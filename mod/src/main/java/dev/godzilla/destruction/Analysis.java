package dev.godzilla.destruction;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural analysis of one structure, run on a worker thread over a snapshot of its blocks (never touches the world).
 * <ol>
 * <li>support: everything not connected to the ground (through load-bearing blocks, with glass and bars hanging on to them) falls;</li>
 * <li>storeys: when a layer's remaining load-bearing blocks drop under a share of what the layer had originally, everything from
 * that layer up comes down; a pancake collapse crushes the storeys below one after another, a lopsided one topples.</li>
 * </ol>
 */
final class Analysis {
	static final int T_MASK = 3, RUBBLE = 4, SEED = 8;
	static final float STOREY_RATIO = 0.55f;
	static final int MIN_BASE = 5;
	static final int MIN_MASS = 10;
	static final int CRUMBLE_MAX = 9;           // loose bits up to this size just crumble
	static final float GRAVITY = 0.022f;        // blocks per tick^2 (arcade: about twice real)
	static final float KAPPA = 0.22f;           // crush energy per load-bearing block

	/** What the main thread copies out of the world. Cell index = (y * sz + z) * sx + x, y/z/x relative to the box minimum. */
	static final class Snapshot {
		Struct s;
		int x0, y0, z0, sx, sy, sz;
		byte[] cls;
		int[] baseCount;
		long[] baseSx, baseSz;
		boolean haveBase;
	}

	static final class MassPlan {
		int mode;            // 0 drop, 1 pancake, 2 topple
		long[] cells;
		short[] path;        // pancake: drop in 1/32 block per tick
		float px, py, pz, ax, az;
		int[] crushTicks = new int[0];
		long[][] crushCells = new long[0][];
		int stopDrop;        // pancake: final drop in blocks
	}

	static final class Plan {
		Struct s;
		List<MassPlan> masses = new ArrayList<>();
		long[] crumble = new long[0];
		boolean collapsed;
	}

	static Plan run(Snapshot sn) {
		int sx = sn.sx, sy = sn.sy, sz = sn.sz;
		int n = sx * sy * sz;
		byte[] cls = sn.cls;
		boolean[] grounded = new boolean[n];
		int[] queue = new int[Math.max(16, n / 4)];
		int qh = 0, qt = 0;
		int stepZ = sx, stepY = sx * sz;

		// phase 1: load-bearing blocks connected to a seed
		for (int i = 0; i < n; i++) {
			int c = cls[i];
			if ((c & SEED) != 0 && (c & T_MASK) == BlockInfo.STRONG) {
				grounded[i] = true;
				queue = push(queue, qt++, i);
			}
		}
		while (qh < qt) {
			int i = queue[qh++];
			int x = i % sx, z = (i / sx) % sz, y = i / stepY;
			if (x > 0) qt = visitStrong(cls, grounded, i - 1, queue, qt);
			if (x < sx - 1) qt = visitStrong(cls, grounded, i + 1, queue, qt);
			if (z > 0) qt = visitStrong(cls, grounded, i - stepZ, queue, qt);
			if (z < sz - 1) qt = visitStrong(cls, grounded, i + stepZ, queue, qt);
			if (y > 0) qt = visitStrong(cls, grounded, i - stepY, queue, qt);
			if (y < sy - 1) qt = visitStrong(cls, grounded, i + stepY, queue, qt);
			queue = grow(queue, qt);
		}
		// phase 2: weak blocks hang on grounded ones (or on grounded weak ones), but never link strong blocks
		qh = 0;
		qt = 0;
		for (int i = 0; i < n; i++) {
			if (grounded[i] || ((cls[i] & SEED) != 0 && (cls[i] & T_MASK) != BlockInfo.NONE)) {
				grounded[i] = true;
				queue = push(queue, qt++, i);
			}
		}
		while (qh < qt) {
			int i = queue[qh++];
			int x = i % sx, z = (i / sx) % sz, y = i / stepY;
			if (x > 0) qt = visitWeak(cls, grounded, i - 1, queue, qt);
			if (x < sx - 1) qt = visitWeak(cls, grounded, i + 1, queue, qt);
			if (z > 0) qt = visitWeak(cls, grounded, i - stepZ, queue, qt);
			if (z < sz - 1) qt = visitWeak(cls, grounded, i + stepZ, queue, qt);
			if (y > 0) qt = visitWeak(cls, grounded, i - stepY, queue, qt);
			if (y < sy - 1) qt = visitWeak(cls, grounded, i + stepY, queue, qt);
			queue = grow(queue, qt);
		}

		Plan plan = new Plan();
		plan.s = sn.s;

		// loose components
		boolean[] seen = new boolean[n];
		LongArrayList crumble = new LongArrayList();
		for (int start = 0; start < n; start++) {
			if (grounded[start] || seen[start] || (cls[start] & T_MASK) == BlockInfo.NONE) {
				continue;
			}
			LongArrayList comp = new LongArrayList();
			qh = 0;
			qt = 0;
			seen[start] = true;
			queue = push(queue, qt++, start);
			while (qh < qt) {
				int i = queue[qh++];
				int x = i % sx, z = (i / sx) % sz, y = i / stepY;
				comp.add(BlockPos.asLong(sn.x0 + x, sn.y0 + y, sn.z0 + z));
				if (x > 0) qt = visitLoose(cls, grounded, seen, i - 1, queue, qt);
				if (x < sx - 1) qt = visitLoose(cls, grounded, seen, i + 1, queue, qt);
				if (z > 0) qt = visitLoose(cls, grounded, seen, i - stepZ, queue, qt);
				if (z < sz - 1) qt = visitLoose(cls, grounded, seen, i + stepZ, queue, qt);
				if (y > 0) qt = visitLoose(cls, grounded, seen, i - stepY, queue, qt);
				if (y < sy - 1) qt = visitLoose(cls, grounded, seen, i + stepY, queue, qt);
				queue = grow(queue, qt);
			}
			if (comp.size() <= CRUMBLE_MAX) {
				crumble.addAll(comp);
			} else {
				MassPlan m = new MassPlan();
				m.mode = 0;
				m.cells = comp.toLongArray();
				plan.masses.add(m);
			}
		}
		plan.crumble = crumble.toLongArray();

		if (sn.haveBase) {
			storeys(sn, grounded, plan);
		}
		plan.collapsed = !plan.masses.isEmpty() || plan.crumble.length > 0;
		return plan;
	}

	private static void storeys(Snapshot sn, boolean[] grounded, Plan plan) {
		int sx = sn.sx, sy = sn.sy, sz = sn.sz;
		byte[] cls = sn.cls;
		int[] total = new int[sy], strong = new int[sy];
		long[] sumX = new long[sy], sumZ = new long[sy];
		int stepY = sx * sz;
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				int row = y * stepY + z * sx;
				for (int x = 0; x < sx; x++) {
					int i = row + x;
					if (!grounded[i]) continue;
					int c = cls[i];
					if ((c & T_MASK) == BlockInfo.NONE) continue;
					total[y]++;
					if ((c & T_MASK) == BlockInfo.STRONG && (c & RUBBLE) == 0) {
						strong[y]++;
						sumX[y] += sn.x0 + x;
						sumZ[y] += sn.z0 + z;
					}
				}
			}
		}
		int[] above = new int[sy + 1];
		for (int y = sy - 1; y >= 0; y--) {
			above[y] = above[y + 1] + total[y];
		}
		int len = Math.min(sy, sn.baseCount.length);
		int fy = -1;
		for (int y = 0; y < len; y++) {
			int base = sn.baseCount[y];
			if (base < MIN_BASE) continue;
			if (above[y] < MIN_MASS) break;
			if (strong[y] < STOREY_RATIO * base) {
				fy = y;
				break;
			}
		}
		if (fy < 0) {
			return;
		}
		// the mass: every grounded block from this layer up
		LongArrayList cells = new LongArrayList();
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, maxY = fy;
		for (int y = fy; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				int row = y * stepY + z * sx;
				for (int x = 0; x < sx; x++) {
					int i = row + x;
					if (grounded[i] && (cls[i] & T_MASK) != BlockInfo.NONE) {
						cells.add(BlockPos.asLong(sn.x0 + x, sn.y0 + y, sn.z0 + z));
						minX = Math.min(minX, sn.x0 + x);
						maxX = Math.max(maxX, sn.x0 + x);
						minZ = Math.min(minZ, sn.z0 + z);
						maxZ = Math.max(maxZ, sn.z0 + z);
						maxY = Math.max(maxY, y);
					}
				}
			}
		}
		if (cells.isEmpty()) {
			return;
		}
		MassPlan m = new MassPlan();
		m.cells = cells.toLongArray();
		float height = maxY - fy + 1;
		float width = Math.max(maxX - minX + 1, maxZ - minZ + 1);

		// lean: has the remaining cross-section's centre of gravity moved away from where it was?
		boolean topple = false;
		if (strong[fy] > 0 && sn.baseCount[fy] > 0) {
			double bcx = sn.baseSx[fy] / (double) sn.baseCount[fy], bcz = sn.baseSz[fy] / (double) sn.baseCount[fy];
			double ccx = sumX[fy] / (double) strong[fy], ccz = sumZ[fy] / (double) strong[fy];
			double dx = ccx - bcx, dz = ccz - bcz;
			double half = Math.max(sn.sx, sn.sz) / 2.0;
			double lambda = Math.sqrt(dx * dx + dz * dz) / half;
			if (lambda >= 0.12 && height / width >= 1.3) {
				double len2 = Math.sqrt(dx * dx + dz * dz);
				m.ax = (float) (-dx / len2);
				m.az = (float) (-dz / len2);
				// hinge: the remaining supporting block of this layer that sticks out furthest toward the lean
				double best = -1e18;
				int bx = 0, bz = 0;
				for (int z = 0; z < sz; z++) {
					int row = fy * stepY + z * sx;
					for (int x = 0; x < sx; x++) {
						int i = row + x;
						if (grounded[i] && (cls[i] & T_MASK) == BlockInfo.STRONG && (cls[i] & RUBBLE) == 0) {
							double d = (sn.x0 + x + 0.5) * m.ax + (sn.z0 + z + 0.5) * m.az;
							if (d > best) {
								best = d;
								bx = sn.x0 + x;
								bz = sn.z0 + z;
							}
						}
					}
				}
				m.px = (float) (bx + 0.5 + 0.5 * m.ax);
				m.pz = (float) (bz + 0.5 + 0.5 * m.az);
				m.py = sn.y0 + fy;
				topple = true;
			}
		}
		if (topple) {
			m.mode = 2;
			plan.masses.add(m);
			return;
		}

		// pancake: the mass falls through the storeys below, crushing each in turn while it has the energy
		m.mode = 1;
		double mass = above[fy];
		double v = 0, pos = 0;
		IntArrayList ticks = new IntArrayList();
		List<long[]> layers = new ArrayList<>();
		IntArrayList path = new IntArrayList();
		path.add(0);
		int k = fy - 1;              // next layer to hit
		boolean arrested = false;
		int t = 0;
		double groundDrop = fy; // drop when the mass reaches the bottom of the box
		while (t < 400) {
			t++;
			v += GRAVITY;
			pos += v;
			// layers hit during this tick
			while (k >= 0 && pos >= fy - (k + 1)) {
				double cost = KAPPA * (strong[k] + 0.25 * (total[k] - strong[k]));
				double massBefore = mass;
				mass += total[k];
				v = v * massBefore / mass;                       // perfectly inelastic
				double ke = 0.5 * mass * v * v;
				if (ke < cost) {
					arrested = true;
					pos = fy - (k + 1);
					v = 0;
					break;
				}
				v = Math.sqrt(2 * (ke - cost) / mass);
				// the layer gives way now
				LongArrayList l = new LongArrayList();
				for (int z = 0; z < sz; z++) {
					int row = k * stepY + z * sx;
					for (int x = 0; x < sx; x++) {
						int i = row + x;
						if (grounded[i] && (cls[i] & T_MASK) != BlockInfo.NONE) {
							l.add(BlockPos.asLong(sn.x0 + x, sn.y0 + k, sn.z0 + z));
						}
					}
				}
				ticks.add(t);
				layers.add(l.toLongArray());
				k--;
			}
			if (arrested) {
				path.add((int) Math.round(pos * 32));
				break;
			}
			if (k < 0 && pos >= groundDrop) {
				pos = groundDrop;
				path.add((int) Math.round(pos * 32));
				break;
			}
			path.add((int) Math.round(pos * 32));
		}
		m.path = new short[path.size()];
		for (int i = 0; i < path.size(); i++) {
			m.path[i] = (short) Math.min(Short.MAX_VALUE, path.getInt(i));
		}
		m.crushTicks = ticks.toIntArray();
		m.crushCells = layers.toArray(new long[0][]);
		m.stopDrop = (int) Math.round(pos);
		plan.masses.add(m);
	}

	private static int[] push(int[] q, int at, int v) {
		if (at >= q.length) {
			q = java.util.Arrays.copyOf(q, q.length * 2);
		}
		q[at] = v;
		return q;
	}

	private static int[] grow(int[] q, int qt) {
		if (qt + 8 >= q.length) {
			return java.util.Arrays.copyOf(q, q.length * 2);
		}
		return q;
	}

	private static int visitStrong(byte[] cls, boolean[] grounded, int j, int[] queue, int qt) {
		if (!grounded[j] && (cls[j] & T_MASK) == BlockInfo.STRONG) {
			grounded[j] = true;
			queue[qt++] = j;
		}
		return qt;
	}

	private static int visitWeak(byte[] cls, boolean[] grounded, int j, int[] queue, int qt) {
		int t = cls[j] & T_MASK;
		if (!grounded[j] && (t == BlockInfo.WEAK || t == BlockInfo.GLASS)) {
			grounded[j] = true;
			queue[qt++] = j;
		}
		return qt;
	}

	private static int visitLoose(byte[] cls, boolean[] grounded, boolean[] seen, int j, int[] queue, int qt) {
		if (!grounded[j] && !seen[j] && (cls[j] & T_MASK) != BlockInfo.NONE) {
			seen[j] = true;
			queue[qt++] = j;
		}
		return qt;
	}
}
