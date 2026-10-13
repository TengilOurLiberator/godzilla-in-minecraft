package dev.godzilla.city;

import dev.godzilla.api.City;
import dev.godzilla.api.CityData;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * The real City service. See docs/API-NOTES.md "City stamping" for the findings behind it.
 *
 * <p>Stamping: every chunk column of the volume gets brand-new LevelChunkSections built from the file (parallel, packed straight
 * into the paletted-container storage), sky-light sources and heightmaps are recomputed, scheduled ticks and block entities in the
 * volume are dropped, then the light is made right: the first stamp of a session lets the light engine light the columns from scratch
 * (the way vanilla lights a fresh chunk) and keeps a copy of the resulting light layers; later stamps queue copies of that light
 * back instead. Finally the chunks are resent to the players that track them.
 */
public final class CityImpl implements City {
	private static final String MARKER = "data/godzilla_city_stamp.txt";

	private @Nullable CityData data;
	private CityData.Building @Nullable [][] grid;
	private int gridX, gridZ, gridW, gridD;

	private @Nullable CityFile file;
	private boolean fileTried;
	private @Nullable String fileError;

	private volatile boolean stamped;

	// cached light of the stamped city (sections sy0-1 .. sy1+1 of every column), valid for lightHash
	private DataLayer @Nullable [] skyCache, blockCache;
	private @Nullable String lightHash;
	private int cacheSections;

	// the last stamp, for /godzilla city info
	volatile String lastStampSummary = "never stamped in this session";

	@Override
	public synchronized CityData data() {
		if (data == null) {
			CityData d = CityData.loadFromJar();
			buildGrid(d);
			data = d;
		}
		return data;
	}

	private void buildGrid(CityData d) {
		gridX = d.origin().getX();
		gridZ = d.origin().getZ();
		gridW = d.size().getX();
		gridD = d.size().getZ();
		CityData.Building[][] g = new CityData.Building[Math.max(0, gridW * gridD)][];
		for (CityData.Building b : d.buildings()) {
			int x0 = Math.max(b.min().getX() - gridX, 0), x1 = Math.min(b.max().getX() - gridX, gridW - 1);
			int z0 = Math.max(b.min().getZ() - gridZ, 0), z1 = Math.min(b.max().getZ() - gridZ, gridD - 1);
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = z * gridW + x;
					CityData.Building[] old = g[i];
					if (old == null) {
						g[i] = new CityData.Building[]{b};
					} else {
						old = Arrays.copyOf(old, old.length + 1);
						old[old.length - 1] = b;
						g[i] = old;
					}
				}
			}
		}
		grid = g;
	}

	@Override
	public CityData.@Nullable Building buildingAt(int x, int y, int z) {
		data(); // makes sure the grid exists (synchronized once, then plain reads)
		CityData.Building[][] g = grid;
		int gx = x - gridX, gz = z - gridZ;
		if (g == null || gx < 0 || gz < 0 || gx >= gridW || gz >= gridD) {
			return null;
		}
		CityData.Building[] list = g[gz * gridW + gx];
		if (list != null) {
			for (CityData.Building b : list) {
				if (b.contains(x, y, z)) {
					return b;
				}
			}
		}
		return null;
	}

	@Override
	public boolean stamped() {
		return stamped;
	}

	synchronized @Nullable CityFile file() {
		if (!fileTried) {
			fileTried = true;
			try {
				long t0 = System.nanoTime();
				file = CityFile.load();
				if (file == null) {
					fileError = "no tokyo.gzc in the mod jar";
				} else {
					GodzillaMod.LOGGER.info("godzilla city: tokyo.gzc decoded in {} ms: {}x{}x{} cells at ({}, {}, {}), palette {}, {} MB in memory, md5 {}",
							(System.nanoTime() - t0) / 1_000_000, file.sizeX, file.sizeY, file.sizeZ, file.originX, file.originY, file.originZ,
							file.palette.length, file.memoryBytes() / (1 << 20), file.hash);
					if (!file.usable()) {
						fileError = "tokyo.gzc has " + file.errors.size() + " problem(s): " + String.join("; ", file.errors);
						GodzillaMod.LOGGER.error("godzilla city: {}. The city will NOT be stamped until the city file is fixed.", fileError);
					} else {
						CityData d = data();
						if (!d.origin().equals(new BlockPos(file.originX, file.originY, file.originZ))
								|| !d.size().equals(new BlockPos(file.sizeX, file.sizeY, file.sizeZ))) {
							GodzillaMod.LOGGER.warn("godzilla city: tokyo.json (origin {}, size {}) disagrees with the tokyo.gzc header; the gzc header is used for stamping",
									d.origin(), d.size());
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				fileError = "cannot read tokyo.gzc: " + e;
				GodzillaMod.LOGGER.error("godzilla city: {}", fileError, e);
			}
		}
		return file;
	}

	@Nullable String fileError() {
		return fileError;
	}

	// ---------------------------------------------------------------------------------------------------- marker

	private static Path markerPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
	}

	/** True when this world was already stamped with exactly this city file. */
	boolean markerMatches(MinecraftServer server) {
		CityFile f = file();
		if (f == null || !f.usable()) {
			return false;
		}
		try {
			Path p = markerPath(server);
			return Files.exists(p) && Files.readString(p, StandardCharsets.UTF_8).trim().startsWith(f.hash);
		} catch (IOException e) {
			return false;
		}
	}

	/** Called at server start: remember that the world already holds the city (without stamping). */
	void adoptMarker() {
		stamped = true;
	}

	private void writeMarker(MinecraftServer server, CityFile f) {
		try {
			Path p = markerPath(server);
			Files.createDirectories(p.getParent());
			Files.writeString(p, f.hash + " stamped " + java.time.Instant.now() + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			GodzillaMod.LOGGER.warn("godzilla city: cannot write the stamp marker: {}", e.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------- stamping

	@Override
	public synchronized void stamp(ServerLevel level) {
		CityFile f = file();
		if (f == null || !f.usable()) {
			GodzillaMod.LOGGER.error("godzilla city: cannot stamp: {}", fileError == null ? "no city file" : fileError);
			return;
		}
		long tStart = System.nanoTime();
		MinecraftServer server = level.getServer();
		int cx0 = Math.floorDiv(f.originX, 16), cx1 = Math.floorDiv(f.originX + f.sizeX - 1, 16);
		int cz0 = Math.floorDiv(f.originZ, 16), cz1 = Math.floorDiv(f.originZ + f.sizeZ - 1, 16);
		int sy0 = Math.floorDiv(f.originY, 16), sy1 = Math.floorDiv(f.originY + f.sizeY - 1, 16);
		int nx = cx1 - cx0 + 1, nz = cz1 - cz0 + 1, n = nx * nz;

		// 1. the chunks must exist and stay loaded
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		LevelChunk[] chunks = new LevelChunk[n];
		for (int i = 0; i < n; i++) {
			chunks[i] = level.getChunk(cx0 + i % nx, cz0 + i / nx);
		}
		long t1 = System.nanoTime();

		ThreadedLevelLightEngine le = (ThreadedLevelLightEngine) level.getChunkSource().getLightEngine();
		// let any light work left over from the wrecked city finish before the blocks change under it
		awaitIdle(server, le, cx0, cz0, nx, n);

		// 2. scheduled ticks and block entities of the old blocks must not act on the new ones
		BoundingBox box = new BoundingBox(f.originX, f.originY, f.originZ, f.originX + f.sizeX - 1, f.originY + f.sizeY - 1, f.originZ + f.sizeZ - 1);
		level.getBlockTicks().clearArea(box);
		level.getFluidTicks().clearArea(box);
		for (LevelChunk c : chunks) {
			if (!c.getBlockEntities().isEmpty()) {
				for (BlockPos p : new ArrayList<>(c.getBlockEntities().keySet())) {
					if (box.isInside(p)) {
						c.removeBlockEntity(p);
					}
				}
			}
		}

		// 3. new sections, in parallel
		Strategy<BlockState> strategy = level.palettedContainerFactory().blockStatesStrategy();
		long[] changed = new long[n];
		AtomicReference<Throwable> failure = new AtomicReference<>();
		IntStream.range(0, n).parallel().forEach(i -> {
			try {
				changed[i] = rebuildColumn(f, chunks[i], cx0 + i % nx, cz0 + i / nx, sy0, sy1, strategy);
			} catch (Throwable t) {
				failure.compareAndSet(null, t);
			}
		});
		if (failure.get() != null) {
			throw new IllegalStateException("city stamp failed while building sections", failure.get());
		}
		// markUnsaved touches the chunk map's unsaved set, which is not thread-safe: do it here, on the server thread
		for (LevelChunk chunk : chunks) {
			chunk.markUnsaved();
		}
		long t3 = System.nanoTime();

		// 4. light
		boolean fromCache = lightHash != null && lightHash.equals(f.hash) && skyCache != null && cacheSections == sy1 - sy0 + 3;
		if (fromCache) {
			for (int i = 0; i < n; i++) {
				int cx = cx0 + i % nx, cz = cz0 + i / nx;
				for (int sy = sy0; sy <= sy1; sy++) {
					if ((changed[i] >> (sy - sy0) & 1) != 0) {
						le.updateSectionStatus(SectionPos.of(cx, sy, cz), chunks[i].getSection(chunks[i].getSectionIndexFromSectionY(sy)).hasOnlyAir());
					}
				}
				for (int k = 0; k < cacheSections; k++) {
					SectionPos sp = SectionPos.of(cx, sy0 - 1 + k, cz);
					DataLayer s = skyCache[i * cacheSections + k];
					DataLayer b = blockCache[i * cacheSections + k];
					if (s != null) {
						le.queueSectionData(LightLayer.SKY, sp, s.copy());
					}
					if (b != null) {
						le.queueSectionData(LightLayer.BLOCK, sp, b.copy());
					}
				}
			}
			awaitIdle(server, le, cx0, cz0, nx, n);
		} else {
			relight(server, le, chunks, cx0, cz0, nx, n);
			captureLight(le, f, cx0, cz0, nx, n, sy0, sy1);
		}
		long t4 = System.nanoTime();

		// 5. resend to the players that see them
		var chunkMap = level.getChunkSource().chunkMap;
		IntStream.range(0, n).parallel().forEach(i -> {
			List<ServerPlayer> players = chunkMap.getPlayers(chunks[i].getPos(), false);
			if (!players.isEmpty()) {
				var packet = new ClientboundLevelChunkWithLightPacket(chunks[i], le, null, null);
				for (ServerPlayer p : players) {
					p.connection.send(packet);
				}
			}
		});
		long t5 = System.nanoTime();

		// 6. spawn, marker
		CityData d = data();
		var start = d.playerStart();
		level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(level.dimension(), BlockPos.containing(start.pos())), Mth.wrapDegrees(start.yaw()), 0.0F));
		writeMarker(server, f);
		stamped = true;
		long tEnd = System.nanoTime();
		lastStampSummary = "%d chunks, %.0f ms total (load chunks %.0f, build sections %.0f, light %.0f [%s], resend %.0f), at game time %d".formatted(
				n, (tEnd - tStart) / 1e6, (t1 - tStart) / 1e6, (t3 - t1) / 1e6, (t4 - t3) / 1e6, fromCache ? "restored from cache" : "relit and cached",
				(t5 - t4) / 1e6, level.getGameTime());
		GodzillaMod.LOGGER.info("godzilla city: stamped {}", lastStampSummary);
	}

	private static long rebuildColumn(CityFile f, LevelChunk chunk, int cx, int cz, int sy0, int sy1, Strategy<BlockState> strategy) {
		LevelChunkSection[] sections = chunk.getSections();
		long changed = 0;
		for (int sy = sy0; sy <= sy1; sy++) {
			int idx = chunk.getSectionIndexFromSectionY(sy);
			LevelChunkSection old = sections[idx];
			LevelChunkSection fresh = buildSection(f, cx, cz, sy, old, strategy);
			if (old.hasOnlyAir() != fresh.hasOnlyAir()) {
				changed |= 1L << (sy - sy0);
			}
			sections[idx] = fresh;
		}
		chunk.getSkyLightSources().fillFrom(chunk);
		Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
		return changed;
	}

	private static int intern(List<BlockState> entries, IdentityHashMap<BlockState, Integer> ids, BlockState s) {
		Integer id = ids.get(s);
		if (id == null) {
			id = entries.size();
			entries.add(s);
			ids.put(s, id);
		}
		return id;
	}

	private static LevelChunkSection buildSection(CityFile f, int cx, int cz, int sy, LevelChunkSection old, Strategy<BlockState> strategy) {
		int x0 = cx * 16 - f.originX, z0 = cz * 16 - f.originZ, y0 = sy * 16 - f.originY;
		boolean inside = x0 >= 0 && x0 + 16 <= f.sizeX && z0 >= 0 && z0 + 16 <= f.sizeZ && y0 >= 0 && y0 + 16 <= f.sizeY;
		int[] local = new int[4096];
		int[] map = new int[f.palette.length];
		Arrays.fill(map, -1);
		List<BlockState> entries = new ArrayList<>();
		IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
		for (int ly = 0; ly < 16; ly++) {
			for (int lz = 0; lz < 16; lz++) {
				int i = ly << 8 | lz << 4;
				int wy = y0 + ly, wz = z0 + lz;
				if (inside) {
					int base = (wy * f.sizeZ + wz) * f.sizeX + x0;
					for (int lx = 0; lx < 16; lx++) {
						int ci = f.get(base + lx);
						int m = map[ci];
						if (m < 0) {
							m = map[ci] = intern(entries, ids, f.palette[ci]);
						}
						local[i + lx] = m;
					}
				} else {
					boolean rowIn = wy >= 0 && wy < f.sizeY && wz >= 0 && wz < f.sizeZ;
					for (int lx = 0; lx < 16; lx++) {
						int wx = x0 + lx;
						if (rowIn && wx >= 0 && wx < f.sizeX) {
							int ci = f.get((wy * f.sizeZ + wz) * f.sizeX + wx);
							int m = map[ci];
							if (m < 0) {
								m = map[ci] = intern(entries, ids, f.palette[ci]);
							}
							local[i + lx] = m;
						} else {
							// outside the file: keep what the world has
							local[i + lx] = intern(entries, ids, old.getBlockState(lx, ly, lz));
						}
					}
				}
			}
		}
		int n = entries.size();
		PalettedContainer<BlockState> container;
		if (n > 256) {
			container = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy);
			for (int i = 0; i < 4096; i++) {
				container.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, entries.get(local[i]));
			}
		} else {
			Optional<LongStream> storage;
			if (n == 1) {
				storage = Optional.empty();
			} else {
				int bits = n <= 16 ? 4 : 32 - Integer.numberOfLeadingZeros(n - 1);
				int perLong = 64 / bits;
				long[] data = new long[(4096 + perLong - 1) / perLong];
				int i = 0;
				for (int w = 0; w < data.length; w++) {
					long v = 0;
					for (int k = 0; k < perLong && i < 4096; k++, i++) {
						v |= (long) local[i] << (k * bits);
					}
					data[w] = v;
				}
				storage = Optional.of(LongStream.of(data));
			}
			container = PalettedContainer.unpack(strategy, new PalettedContainerRO.PackedData<>(entries, storage)).result().orElseThrow();
		}
		return new LevelChunkSection(container, old.getBiomes());
	}

	// ---------------------------------------------------------------------------------------------------- light

	/** Blocks (running the server's own tasks meanwhile) until all light tasks queued so far, for every column, are done. */
	private static void awaitIdle(MinecraftServer server, ThreadedLevelLightEngine le, int cx0, int cz0, int nx, int n) {
		List<CompletableFuture<?>> fs = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			fs.add(le.waitForPendingTasks(cx0 + i % nx, cz0 + i / nx));
		}
		await(server, le, fs);
	}

	private static void await(MinecraftServer server, ThreadedLevelLightEngine le, List<? extends CompletableFuture<?>> fs) {
		CompletableFuture<Void> all = CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]));
		server.managedBlock(() -> {
			le.tryScheduleUpdate();
			return all.isDone();
		});
	}

	/** Lights every column from scratch, like vanilla does for a chunk that was just generated (reset, initialise all, then light all). */
	private void relight(MinecraftServer server, ThreadedLevelLightEngine le, LevelChunk[] chunks, int cx0, int cz0, int nx, int n) {
		int minSec = chunks[0].getMinSectionY(), maxSec = chunks[0].getMaxSectionY();
		for (int i = 0; i < n; i++) {
			ChunkPos pos = chunks[i].getPos();
			le.retainData(pos, false);
			le.setLightEnabled(pos, false);
			for (int sy = le.getMinLightSection(); sy < le.getMaxLightSection(); sy++) {
				le.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, sy), null);
				le.queueSectionData(LightLayer.SKY, SectionPos.of(pos, sy), null);
			}
			for (int sy = minSec; sy <= maxSec; sy++) {
				le.updateSectionStatus(SectionPos.of(pos, sy), true);
			}
		}
		// The old layers are only dropped when the engine runs an update; marking a section non-empty again before that would
		// keep the stale layer (see LayerLightSectionStorage.initializeSection), so let the engine process the reset first.
		awaitIdle(server, le, cx0, cz0, nx, n);
		List<CompletableFuture<?>> init = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			init.add(le.initializeLight(chunks[i], false));
		}
		await(server, le, init);
		List<CompletableFuture<?>> lit = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			lit.add(le.lightChunk(chunks[i], false));
		}
		await(server, le, lit);
		awaitIdle(server, le, cx0, cz0, nx, n);
	}

	private void captureLight(ThreadedLevelLightEngine le, CityFile f, int cx0, int cz0, int nx, int n, int sy0, int sy1) {
		int secs = sy1 - sy0 + 3;
		DataLayer[] sky = new DataLayer[n * secs];
		DataLayer[] block = new DataLayer[n * secs];
		var skyL = le.getLayerListener(LightLayer.SKY);
		var blockL = le.getLayerListener(LightLayer.BLOCK);
		for (int i = 0; i < n; i++) {
			for (int k = 0; k < secs; k++) {
				SectionPos sp = SectionPos.of(cx0 + i % nx, sy0 - 1 + k, cz0 + i / nx);
				DataLayer s = skyL.getDataLayerData(sp);
				DataLayer b = blockL.getDataLayerData(sp);
				sky[i * secs + k] = s == null ? null : s.copy();
				block[i * secs + k] = b == null ? null : b.copy();
			}
		}
		skyCache = sky;
		blockCache = block;
		cacheSections = secs;
		lightHash = f.hash;
	}

	/** Forgets the cached light, so the next stamp lights the city from scratch again. */
	synchronized void dropLightCache() {
		skyCache = null;
		blockCache = null;
		lightHash = null;
	}

	/** CRC of every sky and block light layer the engine holds for the volume (debug: equal after equal stamps means equal light). */
	synchronized String lightChecksum(ServerLevel level) {
		CityFile f = file();
		if (f == null) {
			return "no city file";
		}
		var le = level.getChunkSource().getLightEngine();
		int cx0 = Math.floorDiv(f.originX, 16), cx1 = Math.floorDiv(f.originX + f.sizeX - 1, 16);
		int cz0 = Math.floorDiv(f.originZ, 16), cz1 = Math.floorDiv(f.originZ + f.sizeZ - 1, 16);
		int sy0 = Math.floorDiv(f.originY, 16) - 1, sy1 = Math.floorDiv(f.originY + f.sizeY - 1, 16) + 1;
		java.util.zip.CRC32 sky = new java.util.zip.CRC32(), block = new java.util.zip.CRC32();
		long skySum = 0, blockSum = 0, layers = 0;
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				for (int sy = sy0; sy <= sy1; sy++) {
					SectionPos sp = SectionPos.of(cx, sy, cz);
					DataLayer s = le.getLayerListener(LightLayer.SKY).getDataLayerData(sp);
					DataLayer b = le.getLayerListener(LightLayer.BLOCK).getDataLayerData(sp);
					sky.update(s == null ? new byte[]{-1} : s.getData());
					block.update(b == null ? new byte[]{-1} : b.getData());
					if (s != null) {
						layers++;
						for (byte v : s.getData()) {
							skySum += (v & 15) + (v >> 4 & 15);
						}
					}
					if (b != null) {
						layers++;
						for (byte v : b.getData()) {
							blockSum += (v & 15) + (v >> 4 & 15);
						}
					}
				}
			}
		}
		return "sky crc %08x (level sum %d), block crc %08x (level sum %d), %d layers".formatted(sky.getValue(), skySum, block.getValue(), blockSum, layers);
	}

	/** Bytes held by the light cache (DataLayers are 2 KB each). */
	long lightCacheBytes() {
		long count = 0;
		for (DataLayer[] a : new DataLayer[][]{skyCache, blockCache}) {
			if (a != null) {
				for (DataLayer l : a) {
					if (l != null) {
						count++;
					}
				}
			}
		}
		return count * 2048;
	}

	boolean lightCached() {
		return lightHash != null;
	}
}
