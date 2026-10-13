package dev.godzilla.destruction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.godzilla.api.Cause;
import dev.godzilla.api.CityData;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Registry of everything that can collapse, with the bookkeeping behind integrity(), baselines and the snapshot for the worker. */
final class Structures {
	static final float DESTROYED_BELOW = 0.34f;
	static final int ANALYSIS_DELAY = 6;
	static final int ADHOC_CAP = 60000;
	static final int ADHOC_ID0 = 1_000_000;

	private static final Int2ObjectOpenHashMap<Struct> BY_ID = new Int2ObjectOpenHashMap<>();
	private static final Long2ObjectOpenHashMap<List<Struct>> BY_CHUNK = new Long2ObjectOpenHashMap<>();
	private static final Long2ObjectOpenHashMap<List<Struct>> BUILDINGS_BY_CHUNK = new Long2ObjectOpenHashMap<>();
	static final LongOpenHashSet RUBBLE = new LongOpenHashSet();
	private static List<CityData.Building> indexedFor;
	private static int nextAdhoc = ADHOC_ID0;
	private static Map<Integer, String> labels = new HashMap<>();
	static int adhocCount;

	private Structures() {
	}

	static void reset() {
		BY_ID.clear();
		BY_CHUNK.clear();
		BUILDINGS_BY_CHUNK.clear();
		RUBBLE.clear();
		indexedFor = null;
		nextAdhoc = ADHOC_ID0;
		adhocCount = 0;
	}

	static int groundY() {
		return Gz.city().data().groundY();
	}

	/** Builds the index of the city's buildings when the city data changed (or on first use). */
	static void ensureCity() {
		CityData data = Gz.city().data();
		List<CityData.Building> list = data.buildings();
		if (indexedFor == list) {
			return;
		}
		// keep ad-hoc structures, rebuild the building ones
		List<Struct> keep = new ArrayList<>();
		for (Struct s : BY_ID.values()) {
			if (s.building == null) {
				keep.add(s);
			}
		}
		BY_ID.clear();
		BY_CHUNK.clear();
		BUILDINGS_BY_CHUNK.clear();
		indexedFor = list;
		readLabels();
		for (CityData.Building b : list) {
			Struct s = new Struct(b.id(), b, b.min().getX(), b.min().getY(), b.min().getZ(), b.max().getX(), b.max().getY(), b.max().getZ());
			s.original = b.blocks();
			s.current = b.blocks();
			s.label = labels.getOrDefault(b.id(), b.name() == null ? "" : b.name());
			String l = s.label.toLowerCase(java.util.Locale.ROOT);
			s.fuel = b.kind().equals("industrial") && (l.contains("fuel tank") || l.contains("gas sphere") || l.contains("oil tank"));
			s.bridge = b.kind().equals("bridge");
			add(s);
			index(BUILDINGS_BY_CHUNK, s);
		}
		for (Struct s : keep) {
			add(s);
		}
	}

	private static void readLabels() {
		labels = new HashMap<>();
		try (InputStream in = CityData.class.getResourceAsStream(CityData.RESOURCE)) {
			if (in == null) {
				return;
			}
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : o.getAsJsonArray("buildings")) {
				JsonObject b = e.getAsJsonObject();
				if (b.has("label") && !b.get("label").isJsonNull()) {
					labels.put(b.get("id").getAsInt(), b.get("label").getAsString());
				}
			}
		} catch (Exception e) {
			GodzillaMod.LOGGER.warn("destruction: cannot read building labels: {}", e.toString());
		}
	}

	private static void add(Struct s) {
		BY_ID.put(s.id, s);
		index(BY_CHUNK, s);
	}

	private static void index(Long2ObjectOpenHashMap<List<Struct>> map, Struct s) {
		for (int cx = s.x0 >> 4; cx <= s.x1 >> 4; cx++) {
			for (int cz = s.z0 >> 4; cz <= s.z1 >> 4; cz++) {
				map.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>(2)).add(s);
			}
		}
	}

	private static void unindex(Struct s) {
		for (int cx = s.x0 >> 4; cx <= s.x1 >> 4; cx++) {
			for (int cz = s.z0 >> 4; cz <= s.z1 >> 4; cz++) {
				List<Struct> l = BY_CHUNK.get(ChunkPos.asLong(cx, cz));
				if (l != null) {
					l.remove(s);
				}
			}
		}
	}

	static Struct get(int id) {
		return BY_ID.get(id);
	}

	static Iterable<Struct> all() {
		return BY_ID.values();
	}

	static Struct find(int x, int y, int z) {
		List<Struct> l = BY_CHUNK.get(ChunkPos.asLong(x >> 4, z >> 4));
		if (l != null) {
			for (int i = 0; i < l.size(); i++) {
				Struct s = l.get(i);
				if (s.contains(x, y, z)) {
					return s;
				}
			}
		}
		return null;
	}

	static Struct findBuilding(int x, int y, int z) {
		List<Struct> l = BUILDINGS_BY_CHUNK.get(ChunkPos.asLong(x >> 4, z >> 4));
		if (l != null) {
			for (int i = 0; i < l.size(); i++) {
				Struct s = l.get(i);
				if (s.contains(x, y, z)) {
					return s;
				}
			}
		}
		return null;
	}

	static float integrity(int id) {
		ensureCity();
		Struct s = BY_ID.get(id);
		return s == null ? 0f : s.integrity();
	}

	// ------------------------------------------------------------------------------------------------------------------
	// damage accounting

	/**
	 * Called with the blocks about to be removed (world still intact): makes sure every touched structure has its baseline,
	 * counts the loss, marks them for a structural check, declares buildings destroyed and fuel tanks blown.
	 */
	static void beforeRemoval(ServerLevel level, LongArrayList cells, Cause cause, long tick, Vec3 where) {
		ensureCity();
		int groundY = groundY();
		Struct last = null;
		List<Struct> touched = null;
		for (int i = 0; i < cells.size(); i++) {
			long c = cells.getLong(i);
			int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
			Struct s = last != null && last.contains(x, y, z) ? last : find(x, y, z);
			if (s == null) {
				if (y <= groundY) {
					continue;
				}
				s = createAdhoc(level, x, y, z);
				if (s == null) {
					continue;
				}
			}
			if (!s.baselined) {
				captureBaseline(level, s);
			}
			if (s != last) {
				if (touched == null) {
					touched = new ArrayList<>(4);
				}
				if (!touched.contains(s)) {
					touched.add(s);
				}
				last = s;
			}
			if (!RUBBLE.remove(c)) {
				s.current--;
			}
		}
		if (touched != null) {
			for (Struct s : touched) {
				s.lastCause = cause;
				s.lastDamageTick = tick;
				if (!s.dirty) {
					s.dirty = true;
					s.dirtyAt = tick + ANALYSIS_DELAY;
				}
				if (s.current < 0) {
					s.current = 0;
				}
				if (!s.destroyed && s.building != null && s.integrity() < DESTROYED_BELOW) {
					s.destroyed = true;
					GzEvents.BUILDING_DESTROYED.fire(new GzEvents.BuildingDestroyed(s.building, cause));
					Gz.game().addScore(s.building.landmark() ? Game.ScoreKind.LANDMARK : Game.ScoreKind.BUILDING, s.building.value(), s.asBuilding().center());
				}
				if (s.fuel && !s.fuelBlown && s.integrity() < 0.93f) {
					FuelTanks.blow(level, s, tick);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------------
	// ad-hoc structures

	private static Struct createAdhoc(ServerLevel level, int sx, int sy, int sz) {
		// flood fill the connected solid lump (outside every building box and above the ground)
		int groundY = groundY();
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayList queue = new LongArrayList();
		long start = BlockPos.asLong(sx, sy, sz);
		queue.add(start);
		seen.add(start);
		int minX = sx, minY = sy, minZ = sz, maxX = sx, maxY = sy, maxZ = sz;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int head = 0;
		while (head < queue.size() && queue.size() < ADHOC_CAP) {
			long c = queue.getLong(head++);
			int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
			for (int d = 0; d < 6; d++) {
				int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
				if (ny <= groundY) {
					continue;
				}
				long nc = BlockPos.asLong(nx, ny, nz);
				if (seen.contains(nc)) {
					continue;
				}
				if (findBuilding(nx, ny, nz) != null) {
					continue;
				}
				LevelChunk ch = level.getChunkSource().getChunkNow(nx >> 4, nz >> 4);
				if (ch == null) {
					continue;
				}
				BlockState st = ch.getBlockState(p.set(nx, ny, nz));
				if (!BlockInfo.removable(st)) {
					continue;
				}
				seen.add(nc);
				queue.add(nc);
				minX = Math.min(minX, nx);
				maxX = Math.max(maxX, nx);
				minY = Math.min(minY, ny);
				maxY = Math.max(maxY, ny);
				minZ = Math.min(minZ, nz);
				maxZ = Math.max(maxZ, nz);
			}
		}
		// does it join an existing ad-hoc structure?
		Struct join = null;
		for (int cx = minX >> 4; cx <= maxX >> 4 && join == null; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4 && join == null; cz++) {
				List<Struct> l = BY_CHUNK.get(ChunkPos.asLong(cx, cz));
				if (l != null) {
					for (Struct o : l) {
						if (o.building == null && o.x0 <= maxX && o.x1 >= minX && o.y0 <= maxY && o.y1 >= minY && o.z0 <= maxZ && o.z1 >= minZ) {
							join = o;
							break;
						}
					}
				}
			}
		}
		if (join != null) {
			unindex(join);
			join.x0 = Math.min(join.x0, minX);
			join.y0 = Math.min(join.y0, minY);
			join.z0 = Math.min(join.z0, minZ);
			join.x1 = Math.max(join.x1, maxX);
			join.y1 = Math.max(join.y1, maxY);
			join.z1 = Math.max(join.z1, maxZ);
			index(BY_CHUNK, join);
			join.original += queue.size();
			join.current += queue.size();
			join.baselined = false;
			return join;
		}
		Struct s = new Struct(nextAdhoc++, null, minX, minY, minZ, maxX, maxY, maxZ);
		s.original = queue.size();
		s.current = queue.size();
		s.label = "lump";
		add(s);
		adhocCount++;
		return s;
	}

	/** Records, per layer, how many load-bearing blocks the structure has now (its undamaged state, as far as we know). */
	static void captureBaseline(ServerLevel level, Struct s) {
		int sy = s.sy();
		s.baseCount = new int[sy];
		s.baseSx = new long[sy];
		s.baseSz = new long[sy];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		boolean foreign = s.building == null;
		for (int cx = s.x0 >> 4; cx <= s.x1 >> 4; cx++) {
			for (int cz = s.z0 >> 4; cz <= s.z1 >> 4; cz++) {
				LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
				if (ch == null) {
					continue;
				}
				int xa = Math.max(s.x0, cx << 4), xb = Math.min(s.x1, (cx << 4) + 15);
				int za = Math.max(s.z0, cz << 4), zb = Math.min(s.z1, (cz << 4) + 15);
				for (int sectionY = s.y0 >> 4; sectionY <= s.y1 >> 4; sectionY++) {
					int idx = ch.getSectionIndexFromSectionY(sectionY);
					if (idx < 0 || idx >= ch.getSectionsCount()) {
						continue;
					}
					LevelChunkSection sec = ch.getSection(idx);
					if (sec.hasOnlyAir()) {
						continue;
					}
					int ya = Math.max(s.y0, sectionY << 4), yb = Math.min(s.y1, (sectionY << 4) + 15);
					for (int y = ya; y <= yb; y++) {
						int layer = y - s.y0;
						for (int z = za; z <= zb; z++) {
							for (int x = xa; x <= xb; x++) {
								BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
								if (BlockInfo.type(st) != BlockInfo.STRONG) {
									continue;
								}
								if (foreign && findBuilding(x, y, z) != null) {
									continue;
								}
								if (!RUBBLE.isEmpty() && RUBBLE.contains(BlockPos.asLong(x, y, z))) {
									continue;
								}
								s.baseCount[layer]++;
								s.baseSx[layer] += x;
								s.baseSz[layer] += z;
							}
						}
					}
				}
			}
		}
		s.baselined = true;
	}

	// ------------------------------------------------------------------------------------------------------------------
	// snapshot for the worker

	static Analysis.Snapshot snapshot(ServerLevel level, Struct s) {
		int sx = s.sx(), sy = s.sy(), sz = s.sz();
		long cells = (long) sx * sy * sz;
		if (cells > 4_000_000L) {
			return null;
		}
		byte[] cls = new byte[(int) cells];
		int groundY = groundY();
		boolean foreign = s.building == null;
		boolean anyRubble = !RUBBLE.isEmpty();
		int stepY = sx * sz;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int cx = s.x0 >> 4; cx <= s.x1 >> 4; cx++) {
			for (int cz = s.z0 >> 4; cz <= s.z1 >> 4; cz++) {
				LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
				if (ch == null) {
					return null;
				}
				int xa = Math.max(s.x0, cx << 4), xb = Math.min(s.x1, (cx << 4) + 15);
				int za = Math.max(s.z0, cz << 4), zb = Math.min(s.z1, (cz << 4) + 15);
				for (int sectionY = s.y0 >> 4; sectionY <= s.y1 >> 4; sectionY++) {
					int idx = ch.getSectionIndexFromSectionY(sectionY);
					if (idx < 0 || idx >= ch.getSectionsCount()) {
						continue;
					}
					LevelChunkSection sec = ch.getSection(idx);
					if (sec.hasOnlyAir()) {
						continue;
					}
					int ya = Math.max(s.y0, sectionY << 4), yb = Math.min(s.y1, (sectionY << 4) + 15);
					for (int y = ya; y <= yb; y++) {
						for (int z = za; z <= zb; z++) {
							int row = (y - s.y0) * stepY + (z - s.z0) * sx - s.x0;
							for (int x = xa; x <= xb; x++) {
								BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
								int t = BlockInfo.type(st);
								if (t == BlockInfo.NONE) {
									continue;
								}
								if (foreign && findBuilding(x, y, z) != null) {
									continue;
								}
								if (anyRubble && RUBBLE.contains(BlockPos.asLong(x, y, z))) {
									t |= Analysis.RUBBLE;
								}
								if (y == groundY + 1) {
									t |= Analysis.SEED;
								}
								cls[row + x] = (byte) t;
							}
						}
					}
				}
			}
		}
		// supported from below by whatever the box stands on
		for (int z = s.z0; z <= s.z1; z++) {
			for (int x = s.x0; x <= s.x1; x++) {
				int i = (z - s.z0) * sx + (x - s.x0);
				if ((cls[i] & Analysis.T_MASK) != BlockInfo.NONE && solidAt(level, x, s.y0 - 1, z, p)) {
					cls[i] |= Analysis.SEED;
				}
			}
		}
		if (s.bridge) {
			for (int y = s.y0; y <= s.y1; y++) {
				for (int z = s.z0; z <= s.z1; z++) {
					anchor(level, s, cls, s.x0, y, z, s.x0 - 1, z, p);
					anchor(level, s, cls, s.x1, y, z, s.x1 + 1, z, p);
				}
				for (int x = s.x0; x <= s.x1; x++) {
					anchor(level, s, cls, x, y, s.z0, x, s.z0 - 1, p);
					anchor(level, s, cls, x, y, s.z1, x, s.z1 + 1, p);
				}
			}
		}
		Analysis.Snapshot sn = new Analysis.Snapshot();
		sn.s = s;
		sn.x0 = s.x0;
		sn.y0 = s.y0;
		sn.z0 = s.z0;
		sn.sx = sx;
		sn.sy = sy;
		sn.sz = sz;
		sn.cls = cls;
		sn.haveBase = s.baselined;
		sn.baseCount = s.baseCount.clone();
		sn.baseSx = s.baseSx.clone();
		sn.baseSz = s.baseSz.clone();
		return sn;
	}

	private static void anchor(ServerLevel level, Struct s, byte[] cls, int x, int y, int z, int ox, int oz, BlockPos.MutableBlockPos p) {
		int i = ((y - s.y0) * s.sz() + (z - s.z0)) * s.sx() + (x - s.x0);
		if ((cls[i] & Analysis.T_MASK) != BlockInfo.NONE && solidAt(level, ox, y, oz, p)) {
			cls[i] |= Analysis.SEED;
		}
	}

	private static boolean solidAt(ServerLevel level, int x, int y, int z, BlockPos.MutableBlockPos p) {
		LevelChunk ch = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
		if (ch == null) {
			return false;
		}
		BlockState st = ch.getBlockState(p.set(x, y, z));
		return BlockInfo.type(st) != BlockInfo.NONE;
	}

	static int liveCount() {
		return BY_ID.size();
	}

	static IntArrayList dirtyIds(long tick) {
		IntArrayList l = new IntArrayList();
		for (Struct s : BY_ID.values()) {
			if (s.dirty && !s.inFlight && s.outstanding == 0 && tick >= s.dirtyAt) {
				l.add(s.id);
			}
		}
		return l;
	}
}
