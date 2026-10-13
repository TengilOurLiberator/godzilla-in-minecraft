package dev.godzilla.destruction;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Destruction;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaSounds;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The real {@link Destruction}: cheap bulk removal, debris bursts to the clients, score, sound, structural bookkeeping. */
public final class DestructionService implements Destruction {
	/** Update clients, but no neighbour updates, no shape updates, no onPlace, no drops (docs/API-NOTES.md section 6). */
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final double SEND_RANGE = 520;

	// tuning, changed with /godzilla destruction set
	static int pieceBudgetPerTick = dev.godzilla.core.GodzillaConfig.debrisPiecesPerTick;
	static int maxPiecesPerBurst = 12000;
	static int soundBudgetPerTick = 5;

	// per tick budgets
	private static int piecesLeft = pieceBudgetPerTick;
	private static int soundsLeft = soundBudgetPerTick;
	private static final Long2LongOpenHashMap SOUND_CELLS = new Long2LongOpenHashMap();
	private static final RandomSource RANDOM = RandomSource.createThreadLocalInstance();

	// statistics
	static long statBlocks, statNanos, statCalls, statPieces, statBursts, statDroppedPieces, statPacketBytes;
	static long tickNanos, maxTickNanos, sumTickNanos, tickCount;
	static long blocksThisTick;
	static long tickStartNs, wholeTickMax, wholeTickSum, wholeTickCount, slowTicks;
	static long[] wholeHist = new long[8];

	interface Shape {
		boolean test(int x, int y, int z);
	}

	static void startTick() {
		tickStartNs = System.nanoTime();
		piecesLeft = pieceBudgetPerTick;
		soundsLeft = soundBudgetPerTick;
		tickNanos = 0;
		blocksThisTick = 0;
	}

	static void endTick(long tick) {
		long whole = System.nanoTime() - tickStartNs;
		wholeTickMax = Math.max(wholeTickMax, whole);
		wholeTickSum += whole;
		wholeTickCount++;
		if (whole > 50_000_000L) slowTicks++;
		wholeHist[whole < 5_000_000L ? 0 : whole < 10_000_000L ? 1 : whole < 20_000_000L ? 2 : whole < 35_000_000L ? 3 : whole < 50_000_000L ? 4 : 5]++;
		maxTickNanos = Math.max(maxTickNanos, tickNanos);
		sumTickNanos += tickNanos;
		tickCount++;
		if ((tick & 255) == 0 && SOUND_CELLS.size() > 4096) {
			SOUND_CELLS.clear();
		}
	}

	static void resetStats() {
		statBlocks = statNanos = statCalls = statPieces = statBursts = statDroppedPieces = statPacketBytes = 0;
		tickNanos = maxTickNanos = sumTickNanos = tickCount = 0;
		wholeTickMax = wholeTickSum = wholeTickCount = slowTicks = 0;
		java.util.Arrays.fill(wholeHist, 0);
		Collapses.resetStats();
	}

	// ------------------------------------------------------------------------------------------------------------------
	// API

	@Override
	public int smashSphere(ServerLevel level, Vec3 center, double radius, Vec3 impulse, Cause cause) {
		return smashCapsule(level, center, center, radius, impulse, cause);
	}

	@Override
	public int smashCapsule(ServerLevel level, Vec3 a, Vec3 b, double radius, Vec3 impulse, Cause cause) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double r2 = radius * radius;
		Shape shape = (x, y, z) -> {
			double px = x + 0.5 - a.x, py = y + 0.5 - a.y, pz = z + 0.5 - a.z;
			double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (px * ab.x + py * ab.y + pz * ab.z) / len2));
			double dx = px - ab.x * t, dy = py - ab.y * t, dz = pz - ab.z * t;
			return dx * dx + dy * dy + dz * dz <= r2;
		};
		AABB box = new AABB(a, b).inflate(radius);
		Vec3 center = a.add(b).scale(0.5);
		int n = smashVolume(level, box, shape, impulse, cause, 0, center, 1f, radius);
		hitEntities(level, box, (e) -> segmentDistance(e, a, ab, len2) <= radius, impulse, cause, 1f + (float) radius * 0.12f);
		return n;
	}

	@Override
	public int smashBox(ServerLevel level, AABB box, Vec3 impulse, Cause cause) {
		Vec3 center = box.getCenter();
		int n = smashVolume(level, box, (x, y, z) -> true, impulse, cause, 0, center, 1f, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) / 2);
		hitEntities(level, box, e -> true, impulse, cause, 1.5f);
		return n;
	}

	@Override
	public int explode(ServerLevel level, Vec3 center, double radius, Cause cause) {
		double r2 = radius * radius;
		RandomSource rnd = RandomSource.createThreadLocalInstance(level.getGameTime() * 31 + (long) (center.x * 7 + center.z * 13));
		Shape shape = (x, y, z) -> {
			double dx = x + 0.5 - center.x, dy = y + 0.5 - center.y, dz = z + 0.5 - center.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r2) {
				return false;
			}
			double f = Math.sqrt(d2) / radius;
			if (f < 0.6) {
				return true;
			}
			return rnd.nextDouble() < 1.0 - (f - 0.6) / 0.4 * 0.85; // fewer toward the edge
		};
		AABB box = new AABB(center, center).inflate(radius);
		int n = smashVolume(level, box, shape, Vec3.ZERO, cause, 1, center, (float) Math.min(1.6, 0.55 + radius * 0.07), radius);
		for (Entity e : level.getEntities((Entity) null, box)) {
			if (e instanceof Smashable s && e.isAlive()) {
				Vec3 out = e.position().subtract(center);
				double d = out.length();
				if (d <= radius + e.getBbWidth()) {
					Vec3 dir = d < 1e-6 ? new Vec3(0, 1, 0) : out.scale(1 / d);
					s.onSmashed(cause, new Vec3(dir.x * 0.9, 0.5 + dir.y * 0.5, dir.z * 0.9), 2f + (float) radius / 3f);
				}
			}
		}
		if (radius >= 1.5) {
			broadcast(level, center, new Packets.Fx(Packets.Fx.EXPLOSION, (float) center.x, (float) center.y, (float) center.z, (float) radius, 0, 0), SEND_RANGE);
			Fires.add(level, center, radius * 0.6, (int) (200 + radius * 40));
		}
		SoundEvent snd = radius >= 7 ? GodzillaSounds.EXPLOSION_LARGE : GodzillaSounds.EXPLOSION_CAR;
		sound(level, center, snd, (float) Math.min(16, 5 + radius * 1.2), 0.95f + RANDOM.nextFloat() * 0.1f, 0, true);
		return n;
	}

	@Override
	public void ignite(ServerLevel level, Vec3 center, double radius) {
		Fires.add(level, center, radius, 900);
	}

	@Override
	public float integrity(int buildingId) {
		return Structures.integrity(buildingId);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// bulk removal

	/** Removes what is in the volume, throws it, scores it. mode 0 = directional, 1 = radial blast. */
	int smashVolume(ServerLevel level, AABB box, Shape shape, Vec3 impulse, Cause cause, int mode, Vec3 center, float power, double size) {
		long t0 = System.nanoTime();
		int floor = Structures.groundY() + 1;
		int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX);
		int minY = Math.max(floor, Math.max(level.getMinY(), (int) Math.floor(box.minY)));
		int maxY = Math.min(level.getMaxY(), (int) Math.floor(box.maxY));
		int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ);
		LongArrayList cells = new LongArrayList();
		IntArrayList ids = new IntArrayList();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int lcx = Integer.MIN_VALUE, lcz = Integer.MIN_VALUE;
		LevelChunk chunk = null;
		for (int z = minZ; z <= maxZ; z++) {
			for (int x = minX; x <= maxX; x++) {
				int cx = x >> 4, cz = z >> 4;
				if (cx != lcx || cz != lcz) {
					chunk = level.getChunkSource().getChunkNow(cx, cz);
					lcx = cx;
					lcz = cz;
				}
				if (chunk == null) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					BlockState st = chunk.getBlockState(p.set(x, y, z));
					if (st.isAir() || !BlockInfo.removable(st) || !shape.test(x, y, z)) {
						continue;
					}
					cells.add(BlockPos.asLong(x, y, z));
					ids.add(Block.BLOCK_STATE_REGISTRY.getId(st));
				}
			}
		}
		int n = cells.size();
		if (n == 0) {
			return 0;
		}
		long tick = level.getGameTime();
		Structures.beforeRemoval(level, cells, cause, tick, center);
		removeCells(level, cells);
		burst(level, cells, ids, center, impulse, mode, power, size);
		finish(level, center, n, ids, cause, size);
		long dt = System.nanoTime() - t0;
		statNanos += dt;
		tickNanos += dt;
		return n;
	}

	/** Sets the cells to air with the cheap flags. */
	static void removeCells(ServerLevel level, LongArrayList cells) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < cells.size(); i++) {
			p.set(cells.getLong(i));
			level.setBlock(p, AIR, FLAGS, 0);
		}
	}

	/** Statistics, events, sound, score for n blocks removed around center. */
	void finish(ServerLevel level, Vec3 center, int n, IntArrayList ids, Cause cause, double size) {
		statBlocks += n;
		statCalls++;
		blocksThisTick += n;
		GzEvents.BLOCKS_SMASHED.fire(new GzEvents.BlocksSmashed(center, n, cause));
		if (cause.byGodzilla()) {
			Gz.game().addScore(Game.ScoreKind.BLOCKS, n, center);
		}
		if (cause == Cause.COLLAPSE) {
			return;
		}
		int glass = 0;
		for (int i = 0; i < ids.size(); i += Math.max(1, ids.size() / 64)) {
			if (BlockInfo.type(Block.stateById(ids.getInt(i))) == BlockInfo.GLASS) {
				glass++;
			}
		}
		int sampled = (ids.size() + Math.max(1, ids.size() / 64) - 1) / Math.max(1, ids.size() / 64);
		boolean glassy = glass * 10 > sampled * 4;
		SoundEvent snd;
		float vol;
		if (glassy) {
			snd = GodzillaSounds.SMASH_GLASS;
			vol = 3.5f + Math.min(2f, n / 100f);
		} else if (n < 14) {
			snd = GodzillaSounds.SMASH_SMALL;
			vol = 3.5f;
		} else if (n < 100) {
			snd = GodzillaSounds.SMASH_MEDIUM;
			vol = 5.5f;
		} else {
			snd = GodzillaSounds.SMASH_LARGE;
			vol = 7.5f + Math.min(3f, n / 600f);
		}
		if (cause == Cause.EXPLOSION) {
			return; // explode() plays its own
		}
		sound(level, center, snd, vol, 0.9f + RANDOM.nextFloat() * 0.2f, cause.ordinal(), false);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// debris

	/** Sends the removed blocks as flying pieces to nearby clients, within the per-tick piece budget. */
	static void burst(ServerLevel level, LongArrayList cells, IntArrayList ids, Vec3 center, Vec3 impulse, int mode, float power, double size) {
		int n = cells.size();
		if (n == 0) {
			return;
		}
		int allowed = Math.min(n, Math.min(piecesLeft, maxPiecesPerBurst));
		if (allowed <= 0) {
			statDroppedPieces += n;
			return;
		}
		piecesLeft -= allowed;
		statDroppedPieces += n - allowed;
		BlockPos origin = BlockPos.containing(center);
		int[] rel = new int[allowed * 3];
		int[] sid = new int[allowed];
		double step = (double) n / allowed;
		double pos = RANDOM.nextDouble() * step;
		for (int k = 0; k < allowed; k++) {
			int i = Math.min(n - 1, (int) pos);
			pos += step;
			long c = cells.getLong(i);
			rel[3 * k] = BlockPos.getX(c) - origin.getX();
			rel[3 * k + 1] = BlockPos.getY(c) - origin.getY();
			rel[3 * k + 2] = BlockPos.getZ(c) - origin.getZ();
			sid[k] = ids.getInt(i);
		}
		statPieces += allowed;
		statBursts++;
		Packets.DebrisBurst pk = new Packets.DebrisBurst(origin, (float) impulse.x, (float) impulse.y, (float) impulse.z, mode, power, rel, sid);
		broadcast(level, center, pk, SEND_RANGE);
	}

	static void broadcast(ServerLevel level, Vec3 near, CustomPacketPayload payload, double range) {
		double r2 = range * range;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(near) <= r2) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------------
	// sound with a rate limit: one per coarse place and kind every few ticks, and a few per tick overall

	static void sound(ServerLevel level, Vec3 at, SoundEvent ev, float volume, float pitch, int kind, boolean force) {
		long tick = level.getGameTime();
		long key = BlockPos.asLong((int) at.x >> 3, ((int) at.y >> 3) + kind * 64, (int) at.z >> 3);
		long last = SOUND_CELLS.getOrDefault(key, -100);
		if (!force && (tick - last < 5 || soundsLeft <= 0)) {
			return;
		}
		if (force && soundsLeft <= -4) {
			return;
		}
		soundsLeft--;
		SOUND_CELLS.put(key, tick);
		level.playSound(null, at.x, at.y, at.z, ev, SoundSource.BLOCKS, volume, pitch);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// entities

	interface EntityTest {
		boolean test(Entity e);
	}

	static double segmentDistance(Entity e, Vec3 a, Vec3 ab, double len2) {
		AABB bb = e.getBoundingBox();
		Vec3 c = bb.getCenter();
		double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
		Vec3 p = a.add(ab.scale(t));
		double dx = Math.max(Math.max(bb.minX - p.x, 0), p.x - bb.maxX);
		double dy = Math.max(Math.max(bb.minY - p.y, 0), p.y - bb.maxY);
		double dz = Math.max(Math.max(bb.minZ - p.z, 0), p.z - bb.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	static void hitEntities(ServerLevel level, AABB box, EntityTest test, Vec3 impulse, Cause cause, float power) {
		List<Entity> list = level.getEntities((Entity) null, box, e -> e instanceof Smashable && e.isAlive());
		for (Entity e : list) {
			if (test.test(e)) {
				((Smashable) e).onSmashed(cause, impulse, power);
			}
		}
	}
}
