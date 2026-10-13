package dev.godzilla.destruction;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.GodzillaSounds;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs structural collapses: hands snapshots to the worker, turns its plans into falling masses (removed from the world in time
 * slices, animated on the clients), crushes the storeys below, and finally lands the mass: crushes what is under it and leaves
 * a rubble mound of real blocks.
 */
final class Collapses {
	static final float RUBBLE_FRACTION = 0.40f;
	static final int SLICE_CELLS = 3500;
	static final long TICK_BUDGET_NANOS = 6_000_000L;

	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "godzilla-collapse");
		t.setDaemon(true);
		return t;
	});
	private static final ConcurrentLinkedQueue<Analysis.Plan> RESULTS = new ConcurrentLinkedQueue<>();
	private static volatile int epoch = 0;

	private static final class Event implements Comparable<Event> {
		final long at, seq;
		final Runnable run;

		Event(long at, long seq, Runnable run) {
			this.at = at;
			this.seq = seq;
			this.run = run;
		}

		@Override
		public int compareTo(Event o) {
			return at != o.at ? Long.compare(at, o.at) : Long.compare(seq, o.seq);
		}
	}

	private static final PriorityQueue<Event> EVENTS = new PriorityQueue<>();
	private static final ArrayDeque<MassJob> JOBS = new ArrayDeque<>();
	private static long seq;
	private static int nextMassId = 1;

	// statistics
	static long statMasses, statMassBlocks, statAnalyses, statWorkerNanos, statSnapshotNanos, statRubble, statMaxMass;
	static volatile long workerNanosLast;

	private Collapses() {
	}

	static void reset() {
		epoch++;
		RESULTS.clear();
		EVENTS.clear();
		JOBS.clear();
		seq = 0;
	}

	static int queued() {
		return JOBS.size() + EVENTS.size() + RESULTS.size();
	}

	static int jobs() {
		return JOBS.size();
	}

	static int events() {
		return EVENTS.size();
	}

	static void schedule(long at, Runnable r) {
		EVENTS.add(new Event(at, seq++, r));
	}

	// ------------------------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server, long tick) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		long t0 = System.nanoTime();
		// 1. finished analyses
		Analysis.Plan plan;
		while ((plan = RESULTS.poll()) != null) {
			apply(level, plan, tick);
		}
		// 2. timed events (crushing storeys, landings, fuel chains)
		while (!EVENTS.isEmpty() && EVENTS.peek().at <= tick) {
			EVENTS.poll().run.run();
		}
		// 3. removal of masses, a slice per job per tick
		int n = JOBS.size();
		for (int i = 0; i < n; i++) {
			MassJob j = JOBS.poll();
			if (!j.step(level, tick)) {
				JOBS.add(j);
			}
			if (System.nanoTime() - t0 > TICK_BUDGET_NANOS) {
				break;
			}
		}
		// 4. start the next structural check
		if (System.nanoTime() - t0 < TICK_BUDGET_NANOS / 2) {
			Struct best = null;
			for (Struct s : Structures.all()) {
				if (s.dirty && !s.inFlight && s.outstanding == 0 && tick >= s.dirtyAt && (best == null || s.dirtyAt < best.dirtyAt)) {
					best = s;
				}
			}
			if (best != null) {
				startAnalysis(level, best);
			}
		}
		DestructionService.tickNanos += System.nanoTime() - t0;
	}

	private static void startAnalysis(ServerLevel level, Struct s) {
		long t0 = System.nanoTime();
		Analysis.Snapshot sn = Structures.snapshot(level, s);
		s.dirty = false;
		if (sn == null) {
			return;
		}
		s.inFlight = true;
		statSnapshotNanos += System.nanoTime() - t0;
		statAnalyses++;
		final int myEpoch = epoch;
		POOL.execute(() -> {
			long w0 = System.nanoTime();
			try {
				Analysis.Plan p = Analysis.run(sn);
				if (myEpoch == epoch) {
					RESULTS.add(p);
				}
			} catch (Throwable t) {
				GodzillaMod.LOGGER.error("destruction: structural analysis failed", t);
				sn.s.inFlight = false;
			}
			workerNanosLast = System.nanoTime() - w0;
			statWorkerNanos += workerNanosLast;
		});
	}

	private static void apply(ServerLevel level, Analysis.Plan plan, long tick) {
		Struct s = plan.s;
		s.inFlight = false;
		if (!plan.collapsed) {
			return;
		}
		if (plan.crumble.length > 0) {
			s.outstanding++;
			JOBS.add(new MassJob(s, null, plan.crumble));
		}
		for (Analysis.MassPlan m : plan.masses) {
			s.outstanding++;
			JOBS.add(new MassJob(s, m, m.cells));
		}
	}

	// ------------------------------------------------------------------------------------------------------------------

	/** Takes one mass out of the world over a few ticks, then sets it going. */
	private static final class MassJob {
		final Struct s;
		final Analysis.MassPlan m; // null: loose crumbs that just burst apart
		final long[] src;
		int pos;
		final LongArrayList cells = new LongArrayList();
		final IntArrayList ids = new IntArrayList();

		MassJob(Struct s, Analysis.MassPlan m, long[] src) {
			this.s = s;
			this.m = m;
			this.src = src;
		}

		/** @return true when finished */
		boolean step(ServerLevel level, long tick) {
			LongArrayList slice = new LongArrayList();
			IntArrayList sliceIds = new IntArrayList();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			int end = Math.min(src.length, pos + SLICE_CELLS);
			for (; pos < end; pos++) {
				long c = src[pos];
				p.set(c);
				LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
				if (ch == null) {
					continue;
				}
				BlockState st = ch.getBlockState(p);
				if (st.isAir() || !BlockInfo.removable(st)) {
					continue;
				}
				slice.add(c);
				sliceIds.add(Block.BLOCK_STATE_REGISTRY.getId(st));
			}
			if (!slice.isEmpty()) {
				Structures.beforeRemoval(level, slice, Cause.COLLAPSE, tick, Vec3.ZERO);
				DestructionService.removeCells(level, slice);
				DestructionService.statBlocks += slice.size();
				if (m == null) {
					Vec3 c = centroid(slice);
					DestructionService.burst(level, slice, sliceIds, c, Vec3.ZERO, 2, 1f, 3);
				} else {
					cells.addAll(slice);
					ids.addAll(sliceIds);
				}
			}
			if (pos < src.length) {
				return false;
			}
			if (m == null) {
				s.outstanding--;
				return true;
			}
			launch(level, tick);
			return true;
		}

		private void launch(ServerLevel level, long tick) {
			if (cells.isEmpty()) {
				s.outstanding--;
				return;
			}
			int n = cells.size();
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (int i = 0; i < n; i++) {
				long c = cells.getLong(i);
				int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
			Vec3 center = new Vec3((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
			short[] path;
			int mode = m.mode;
			float px = m.px, py = m.py, pz = m.pz, ax = m.ax, az = m.az;
			float dropOrAngle;
			if (mode == 2) {
				float[] theta = topple(level, cells, px, py, pz, ax, az, maxY - (int) py + 1);
				path = toShorts(theta, 1000f);
				dropOrAngle = theta.length == 0 ? 0 : theta[theta.length - 1];
			} else if (mode == 1) {
				path = ensureLength(m.path, 16);
				dropOrAngle = path[path.length - 1] / 32f;
			} else {
				double d = dropDistance(level, cells);
				if (d < 0.75) {
					// nothing to fall: it just crumbles where it is
					DestructionService.burst(level, cells, ids, center, Vec3.ZERO, 2, 1f, 3);
					s.outstanding--;
					return;
				}
				int ticks = Math.max(6, (int) Math.ceil(Math.sqrt(2 * d / Analysis.GRAVITY)));
				path = new short[ticks + 1];
				for (int t = 0; t <= ticks; t++) {
					path[t] = (short) Math.round(Math.min(d, 0.5 * Analysis.GRAVITY * t * t) * 32);
				}
				path[ticks] = (short) Math.round(d * 32);
				dropOrAngle = (float) d;
			}
			int duration = path.length - 1;
			BlockPos origin = new BlockPos(minX, minY, minZ);
			int[] rel = new int[3 * n];
			int[] idArr = new int[n];
			for (int i = 0; i < n; i++) {
				long c = cells.getLong(i);
				rel[3 * i] = BlockPos.getX(c) - minX;
				rel[3 * i + 1] = BlockPos.getY(c) - minY;
				rel[3 * i + 2] = BlockPos.getZ(c) - minZ;
				idArr[i] = ids.getInt(i);
			}
			int massId = nextMassId++;
			float dust = (float) Math.sqrt(n) / 5f + 2f;
			Packets.Mass pk = new Packets.Mass(massId, origin, mode, path, px, py, pz, ax, az, dust, rel, idArr);
			DestructionService.broadcast(level, center, pk, 700);
			statMasses++;
			statMassBlocks += n;
			statMaxMass = Math.max(statMaxMass, n);
			GzEvents.COLLAPSE.fire(new GzEvents.Collapse(center, n, s.asBuilding()));
			DestructionService.sound(level, center, GodzillaSounds.COLLAPSE_RUMBLE, 10f, 0.95f + level.getRandom().nextFloat() * 0.1f, 40, true);
			shake(level, center, 0.25f + Math.min(0.5f, n / 4000f), 40 + Math.min(80, n / 100));

			// the landing and, for a pancake, the storeys giving way on the way down
			final LongArrayList landCells = new LongArrayList(cells);
			final IntArrayList landIds = new IntArrayList(ids);
			final int fMode = mode;
			final float fAmount = dropOrAngle;
			final float fpx = px, fpy = py, fpz = pz, fax = ax, faz = az;
			final int extraCrushed[] = {0};
			final IntArrayList crushedIds = new IntArrayList();
			if (mode == 1) {
				for (int i = 0; i < m.crushCells.length; i++) {
					final long[] layer = m.crushCells[i];
					s.outstanding++;
					schedule(tick + m.crushTicks[i], () -> {
						crushLayer(level, s, layer, center, crushedIds);
						extraCrushed[0] += layer.length;
						s.outstanding--;
					});
				}
			}
			schedule(tick + duration, () -> {
				land(level, s, fMode, landCells, landIds, crushedIds, fAmount, fpx, fpy, fpz, fax, faz, center);
				s.outstanding--;
			});
		}
	}

	private static void crushLayer(ServerLevel level, Struct s, long[] layer, Vec3 center, IntArrayList crushedIds) {
		LongArrayList cells = new LongArrayList();
		IntArrayList ids = new IntArrayList();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (long c : layer) {
			p.set(c);
			LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
			if (ch == null) {
				continue;
			}
			BlockState st = ch.getBlockState(p);
			if (st.isAir() || !BlockInfo.removable(st)) {
				continue;
			}
			cells.add(c);
			ids.add(Block.BLOCK_STATE_REGISTRY.getId(st));
		}
		if (cells.isEmpty()) {
			return;
		}
		Structures.beforeRemoval(level, cells, Cause.COLLAPSE, level.getGameTime(), center);
		DestructionService.removeCells(level, cells);
		DestructionService.statBlocks += cells.size();
		crushedIds.addAll(ids);
		Vec3 c = centroid(cells);
		DestructionService.burst(level, cells, ids, c, Vec3.ZERO, 2, 1.3f, 4);
		DestructionService.sound(level, c, GodzillaSounds.SMASH_MEDIUM, 6f, 0.8f + level.getRandom().nextFloat() * 0.2f, 41, false);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// motion

	private static short[] ensureLength(short[] path, int min) {
		if (path.length >= min) {
			return path;
		}
		int end = Math.max(path[path.length - 1], 40);
		short[] p = new short[min];
		for (int t = 0; t < min; t++) {
			double f = t / (double) (min - 1);
			p[t] = (short) Math.round(end * f * f);
		}
		return p;
	}

	private static short[] toShorts(float[] a, float scale) {
		short[] p = new short[a.length];
		for (int i = 0; i < a.length; i++) {
			p[i] = (short) Math.round(a[i] * scale);
		}
		return p;
	}

	/** How far the whole lump can fall straight down before something is under it. */
	static double dropDistance(ServerLevel level, LongArrayList cells) {
		java.util.HashMap<Long, Integer> lowest = new java.util.HashMap<>();
		for (int i = 0; i < cells.size(); i++) {
			long c = cells.getLong(i);
			long key = (((long) BlockPos.getX(c)) << 32) ^ (BlockPos.getZ(c) & 0xffffffffL);
			int y = BlockPos.getY(c);
			Integer old = lowest.get(key);
			if (old == null || y < old) {
				lowest.put(key, y);
			}
		}
		double best = 400;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		for (var e : lowest.entrySet()) {
			int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
			int y0 = e.getValue();
			LevelChunk ch = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			if (ch == null) {
				continue;
			}
			int y = y0 - 1;
			while (y >= minY && ch.getBlockState(p.set(x, y, z)).isAir()) {
				y--;
			}
			best = Math.min(best, y0 - (y + 1));
		}
		return best;
	}

	/** Angle (radians) per tick for a lump tipping over about its hinge until it meets the ground or something standing. */
	static float[] topple(ServerLevel level, LongArrayList cells, float px, float py, float pz, float ax, float az, float height) {
		int n = cells.size();
		int samples = Math.min(n, 700);
		float[] sx = new float[samples], sy = new float[samples], sz = new float[samples];
		RandomSource rnd = RandomSource.createThreadLocalInstance(n);
		for (int i = 0; i < samples; i++) {
			long c = cells.getLong(n <= samples ? i : rnd.nextInt(n));
			sx[i] = BlockPos.getX(c) + 0.5f;
			sy[i] = BlockPos.getY(c) + 0.5f;
			sz[i] = BlockPos.getZ(c) + 0.5f;
		}
		float end = 1.52f;
		float[] o = new float[3];
		outer:
		for (float th = 0.03f; th < 1.52f; th += 0.02f) {
			float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
			for (int i = 0; i < samples; i++) {
				rotate(sx[i], sy[i], sz[i], px, py, pz, ax, az, cs, sn, o);
				int ix = (int) Math.floor(o[0]), iz = (int) Math.floor(o[2]);
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ix, iz);
				if (o[1] < h) {
					end = th - 0.02f;
					break outer;
				}
			}
		}
		end = Math.max(0.12f, end);
		// inverted pendulum: slow to start, then it goes
		float k = 0.07f / Math.max(8f, height);
		float th = 0.035f, w = 0.01f;
		IntArrayList ticks = new IntArrayList();
		java.util.ArrayList<Float> list = new java.util.ArrayList<>();
		list.add(0f);
		for (int t = 0; t < 400 && th < end; t++) {
			w += k * (float) Math.sin(th + 0.15f);
			th += w;
			list.add(Math.min(th, end));
		}
		float[] out = new float[list.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = list.get(i);
		}
		return out;
	}

	/** Rotates a point about the horizontal hinge through (px,py,pz) so that the top goes toward (ax,az). */
	static void rotate(float x, float y, float z, float px, float py, float pz, float ax, float az, float cs, float sn, float[] out) {
		float rx = x - px, ry = y - py, rz = z - pz;
		float r = rx * ax + rz * az;
		float lx = rx - r * ax, lz = rz - r * az;
		float r2 = r * cs + ry * sn;
		float h2 = -r * sn + ry * cs;
		out[0] = px + ax * r2 + lx;
		out[1] = py + h2;
		out[2] = pz + az * r2 + lz;
	}

	// ------------------------------------------------------------------------------------------------------------------
	// landing

	private static void land(ServerLevel level, Struct s, int mode, LongArrayList cells, IntArrayList ids, IntArrayList crushedIds,
							 float amount, float px, float py, float pz, float ax, float az, Vec3 center) {
		int n = cells.size();
		LongOpenHashSet footprint = new LongOpenHashSet();
		LongArrayList finals = new LongArrayList(n);
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		float cs = (float) Math.cos(amount), sn = (float) Math.sin(amount);
		float[] o = new float[3];
		int drop = Math.round(amount);
		for (int i = 0; i < n; i++) {
			long c = cells.getLong(i);
			int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
			if (mode == 2) {
				rotate(x + 0.5f, y + 0.5f, z + 0.5f, px, py, pz, ax, az, cs, sn, o);
				x = (int) Math.floor(o[0]);
				y = (int) Math.floor(o[1]);
				z = (int) Math.floor(o[2]);
			} else {
				y -= drop;
			}
			finals.add(BlockPos.asLong(x, y, z));
			footprint.add(BlockPos.asLong(x, 0, z));
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		Vec3 land = new Vec3((minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);

		// whatever is in the way is crushed
		LongArrayList crush = new LongArrayList();
		IntArrayList crushIds = new IntArrayList();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int floor = Structures.groundY() + 1;
		for (int i = 0; i < n; i++) {
			long c = finals.getLong(i);
			if (BlockPos.getY(c) < floor) {
				continue;
			}
			p.set(c);
			LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
			if (ch == null) {
				continue;
			}
			BlockState st = ch.getBlockState(p);
			if (!st.isAir() && BlockInfo.removable(st)) {
				crush.add(c);
				crushIds.add(Block.BLOCK_STATE_REGISTRY.getId(st));
			}
		}
		if (!crush.isEmpty()) {
			Structures.beforeRemoval(level, crush, Cause.COLLAPSE, level.getGameTime(), land);
			DestructionService.removeCells(level, crush);
			DestructionService.burst(level, crush, crushIds, centroid(crush), Vec3.ZERO, 2, 1.4f, 4);
			DestructionService.statBlocks += crush.size();
		}

		// whoever is under it
		AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1.0, 0.5, 1.0);
		float power = Math.min(8f, 1.5f + n / 300f);
		List<Entity> victims = level.getEntities((Entity) null, box, e -> e instanceof Smashable && e.isAlive());
		for (Entity e : victims) {
			int ex = (int) Math.floor(e.getX()), ez = (int) Math.floor(e.getZ());
			boolean inside = false;
			for (int dx = -1; dx <= 1 && !inside; dx++) {
				for (int dz = -1; dz <= 1 && !inside; dz++) {
					inside = footprint.contains(BlockPos.asLong(ex + dx, 0, ez + dz));
				}
			}
			if (inside) {
				Vec3 out = e.position().subtract(land);
				Vec3 dir = out.lengthSqr() < 1e-6 ? new Vec3(0, 0, 0) : out.normalize();
				((Smashable) e).onSmashed(Cause.COLLAPSE, new Vec3(dir.x * 0.6, 0.15, dir.z * 0.6), power);
			}
		}

		// the mound
		IntArrayList palette = new IntArrayList();
		for (int i = 0; i < ids.size(); i++) {
			if (BlockInfo.type(Block.stateById(ids.getInt(i))) == BlockInfo.STRONG) {
				palette.add(ids.getInt(i));
			}
		}
		for (int i = 0; i < crushedIds.size(); i++) {
			if (BlockInfo.type(Block.stateById(crushedIds.getInt(i))) == BlockInfo.STRONG) {
				palette.add(crushedIds.getInt(i));
			}
		}
		if (palette.isEmpty()) {
			palette.addAll(ids);
		}
		int total = n + crushedIds.size();
		int rubble = Math.min(7000, Math.max(3, Math.round(total * RUBBLE_FRACTION)));
		if (!palette.isEmpty()) {
			placeRubble(level, footprint, rubble, palette, mode == 2 ? 0.8f : 1f);
		}
		statRubble += rubble;

		DestructionService.sound(level, land, GodzillaSounds.COLLAPSE_SETTLE, 7f, 0.95f + level.getRandom().nextFloat() * 0.1f, 42, true);
		if (n > 60) {
			shake(level, land, 0.2f + Math.min(0.6f, n / 3500f), 30 + Math.min(60, n / 80));
		}
	}

	/** Heaps count blocks around and on top of the footprint: a low wide mound, not a copy of the building. */
	static void placeRubble(ServerLevel level, LongOpenHashSet footprint, int count, IntArrayList palette, float spreadScale) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (long c : footprint) {
			int x = BlockPos.getX(c), z = BlockPos.getZ(c);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		int m = (int) Math.max(2, Math.min(12, (2 + Math.sqrt(count) / 2.5) * spreadScale));
		int w = maxX - minX + 1 + 2 * m, d = maxZ - minZ + 1 + 2 * m;
		if ((long) w * d > 250_000L) {
			return;
		}
		int ox = minX - m, oz = minZ - m;
		boolean[] fp = new boolean[w * d];
		for (long c : footprint) {
			fp[(BlockPos.getZ(c) - oz) * w + (BlockPos.getX(c) - ox)] = true;
		}
		// distance outside (to the footprint) and inside (to its edge), chessboard metric, by sweeping
		float[] out = distance(fp, w, d, false);
		float[] in = distance(fp, w, d, true);
		float emax = 1;
		for (int i = 0; i < in.length; i++) {
			emax = Math.max(emax, in[i]);
		}
		float[] prof = new float[w * d];
		RandomSource rnd = RandomSource.createThreadLocalInstance(count * 7919L + minX * 31L + minZ);
		for (int i = 0; i < prof.length; i++) {
			float sd = fp[i] ? in[i] : -out[i];
			float t = (sd + 0.55f * m) / (emax + 0.55f * m);
			if (t <= 0) {
				continue;
			}
			prof[i] = (float) Math.pow(Math.min(1f, t), 1.5) * (0.7f + 0.6f * rnd.nextFloat());
		}
		// scale the height so that the columns add up to the count
		float lo = 0.1f, hi = 80f;
		for (int it = 0; it < 24; it++) {
			float mid = (lo + hi) / 2;
			long sum = 0;
			for (float v : prof) {
				sum += (long) (v * mid + 0.5f);
			}
			if (sum > count) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		float hmax = lo;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int ht = (int) (prof[z * w + x] * hmax + 0.5f);
				if (ht <= 0) {
					continue;
				}
				int wx = ox + x, wz = oz + z;
				LevelChunk ch = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
				if (ch == null) {
					continue;
				}
				int ys = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
				BlockState below = ch.getBlockState(p.set(wx, ys - 1, wz));
				if (below.isAir() || !below.getFluidState().isEmpty()) {
					continue;
				}
				for (int k = 0; k < ht; k++) {
					p.set(wx, ys + k, wz);
					if (!ch.getBlockState(p).isAir()) {
						continue;
					}
					BlockState st = Block.stateById(palette.getInt(rnd.nextInt(palette.size())));
					level.setBlock(p, st, DestructionService.FLAGS, 0);
					Structures.RUBBLE.add(p.asLong());
					placed++;
				}
			}
		}
	}

	private static float[] distance(boolean[] fp, int w, int d, boolean inside) {
		float INF = 1e9f;
		float[] dist = new float[w * d];
		for (int i = 0; i < dist.length; i++) {
			dist[i] = (fp[i] == inside) ? INF : 0;
		}
		// two-pass chamfer (chessboard-ish with diagonal cost 1.4)
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int i = z * w + x;
				float v = dist[i];
				if (x > 0) v = Math.min(v, dist[i - 1] + 1);
				if (z > 0) {
					v = Math.min(v, dist[i - w] + 1);
					if (x > 0) v = Math.min(v, dist[i - w - 1] + 1.4f);
					if (x < w - 1) v = Math.min(v, dist[i - w + 1] + 1.4f);
				}
				dist[i] = v;
			}
		}
		for (int z = d - 1; z >= 0; z--) {
			for (int x = w - 1; x >= 0; x--) {
				int i = z * w + x;
				float v = dist[i];
				if (x < w - 1) v = Math.min(v, dist[i + 1] + 1);
				if (z < d - 1) {
					v = Math.min(v, dist[i + w] + 1);
					if (x < w - 1) v = Math.min(v, dist[i + w + 1] + 1.4f);
					if (x > 0) v = Math.min(v, dist[i + w - 1] + 1.4f);
				}
				dist[i] = v;
			}
		}
		for (int i = 0; i < dist.length; i++) {
			if (dist[i] >= INF) dist[i] = 0;
		}
		return dist;
	}

	// ------------------------------------------------------------------------------------------------------------------

	static Vec3 centroid(LongArrayList cells) {
		double x = 0, y = 0, z = 0;
		int n = cells.size();
		for (int i = 0; i < n; i++) {
			long c = cells.getLong(i);
			x += BlockPos.getX(c);
			y += BlockPos.getY(c);
			z += BlockPos.getZ(c);
		}
		return n == 0 ? Vec3.ZERO : new Vec3(x / n + 0.5, y / n + 0.5, z / n + 0.5);
	}

	static void shake(ServerLevel level, Vec3 at, float strength, int ticks) {
		ServerPlayer g = Gz.game().godzilla();
		if (g == null) {
			return;
		}
		double d = g.position().distanceTo(at);
		float k = (float) (strength / (1.0 + d / 70.0));
		if (k > 0.02f) {
			Gz.fx().shake(g, Math.min(1f, k), ticks);
		}
	}
}
