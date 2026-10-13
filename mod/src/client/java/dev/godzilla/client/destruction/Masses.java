package dev.godzilla.client.destruction;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.godzilla.destruction.Packets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Falling masses: a chunk of building that came loose moves as one rigid cluster (the server already took the blocks out of the
 * world). Draws only the faces on the outside of the cluster, and turns it into flying pieces and dust when it lands.
 */
final class Masses {
	static final class Mass {
		int id, mode, n;
		float[] bx, by, bz;
		int[] meshId;
		byte[] mask;
		short[] path;
		float px, py, pz, ax, az, dust;
		float cx, cy, cz;       // centroid
		int age;
		float wobble, phase;
		int baseLight;
		float minY;
	}

	static final List<Mass> LIST = new ArrayList<>();
	private static final RandomSource RND = RandomSource.create();
	static long drawNanos;
	static int quads;

	private Masses() {
	}

	static void clear() {
		LIST.clear();
	}

	static int blocks() {
		int t = 0;
		for (Mass m : LIST) {
			t += m.n;
		}
		return t;
	}

	static void add(ClientLevel level, Packets.Mass pk) {
		Mass m = new Mass();
		m.id = pk.id();
		m.mode = pk.mode();
		m.path = pk.path();
		m.px = pk.px();
		m.py = pk.py();
		m.pz = pk.pz();
		m.ax = pk.ax();
		m.az = pk.az();
		m.dust = pk.dust();
		int n = pk.ids().length;
		m.n = n;
		m.bx = new float[n];
		m.by = new float[n];
		m.bz = new float[n];
		m.meshId = pk.ids();
		m.mask = new byte[n];
		BlockPos o = pk.origin();
		int[] rel = pk.rel();
		int maxX = 0, maxY = 0, maxZ = 0;
		for (int i = 0; i < n; i++) {
			maxX = Math.max(maxX, rel[3 * i]);
			maxY = Math.max(maxY, rel[3 * i + 1]);
			maxZ = Math.max(maxZ, rel[3 * i + 2]);
		}
		int w = maxX + 3, h = maxY + 3, d = maxZ + 3;
		long vol = (long) w * h * d;
		java.util.BitSet occ = null;
		it.unimi.dsi.fastutil.longs.LongOpenHashSet occHash = null;
		if (vol < 40_000_000L) {
			occ = new java.util.BitSet((int) vol);
			for (int i = 0; i < n; i++) {
				occ.set(((rel[3 * i + 1] + 1) * d + (rel[3 * i + 2] + 1)) * w + rel[3 * i] + 1);
			}
		} else {
			occHash = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
			for (int i = 0; i < n; i++) {
				occHash.add(BlockPos.asLong(rel[3 * i], rel[3 * i + 1], rel[3 * i + 2]));
			}
		}
		double sx = 0, sy = 0, sz = 0;
		float minY = Float.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			int x = rel[3 * i], y = rel[3 * i + 1], z = rel[3 * i + 2];
			m.bx[i] = o.getX() + x + 0.5f;
			m.by[i] = o.getY() + y + 0.5f;
			m.bz[i] = o.getZ() + z + 0.5f;
			sx += m.bx[i];
			sy += m.by[i];
			sz += m.bz[i];
			minY = Math.min(minY, m.by[i]);
			int mask = 0;
			for (Direction dir : Direction.values()) {
				int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
				boolean solid = occ != null ? occ.get(((ny + 1) * d + (nz + 1)) * w + nx + 1) : occHash.contains(BlockPos.asLong(nx, ny, nz));
				if (!solid) {
					mask |= 1 << dir.ordinal();
				}
			}
			m.mask[i] = (byte) mask;
		}
		m.minY = minY;
		m.cx = (float) (sx / n);
		m.cy = (float) (sy / n);
		m.cz = (float) (sz / n);
		m.wobble = 0.012f + RND.nextFloat() * 0.02f;
		m.phase = RND.nextFloat() * 6.28f;
		m.baseLight = LightCoordsUtil.pack(0, 15);
		LIST.add(m);
	}

	private static float sample(short[] p, float t, float scale) {
		if (p.length == 0) {
			return 0;
		}
		int i = (int) Math.floor(t);
		if (i >= p.length - 1) {
			return p[p.length - 1] / scale;
		}
		if (i < 0) {
			return 0;
		}
		float f = t - i;
		return (p[i] + (p[i + 1] - p[i]) * f) / scale;
	}

	/** Rotation matrix, pivot and offset of the cluster at time t (ticks since it started). */
	private static void pose(Mass m, float t, float[] R, float[] pivot, float[] off) {
		Debris.quatToMatrix(0, 0, 0, 1, R);
		if (m.mode == 2) {
			float th = sample(m.path, t, 1000f);
			float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
			// rotate about the horizontal axis perpendicular to (ax, az) through the hinge: top moves toward (ax, az)
			float ax = m.ax, az = m.az;
			// local basis: u = lean direction (ax,0,az), up = (0,1,0), lateral = (-az,0,ax) stays
			// world = P + u*(r*cs + h*sn) + up*(-r*sn + h*cs) + lat*l, with r = rel.u, h = rel.y, l = rel.lat
			// as a matrix on rel = (rx, ry, rz):
			float uxx = ax * ax, uxz = ax * az, uzz = az * az;
			// r*u contribution: rotation of the u component
			R[0] = 1 - uxx + uxx * cs;
			R[1] = ax * sn;
			R[2] = -uxz + uxz * cs;
			R[3] = -ax * sn;
			R[4] = cs;
			R[5] = -az * sn;
			R[6] = -uxz + uxz * cs;
			R[7] = az * sn;
			R[8] = 1 - uzz + uzz * cs;
			pivot[0] = m.px;
			pivot[1] = m.py;
			pivot[2] = m.pz;
			off[0] = off[1] = off[2] = 0;
		} else {
			float drop = sample(m.path, t, 32f);
			float amp = m.wobble * Math.min(1f, t / 12f) * (m.mode == 1 ? 1.0f : 1.6f);
			float a = amp * (float) Math.sin(t * 0.21f + m.phase);
			float b = amp * 0.7f * (float) Math.sin(t * 0.17f + m.phase * 1.7f);
			// small tilt about x and z
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
			// R = Rz(b) * Rx(a)
			R[0] = cb;
			R[1] = -sb * ca;
			R[2] = sb * sa;
			R[3] = sb;
			R[4] = cb * ca;
			R[5] = -cb * sa;
			R[6] = 0;
			R[7] = sa;
			R[8] = ca;
			pivot[0] = m.cx;
			pivot[1] = m.minY;
			pivot[2] = m.cz;
			off[0] = 0;
			off[1] = -drop;
			off[2] = 0;
			if (m.mode == 1) { // a pancake grinds: tremble
				off[0] = (RND.nextFloat() - 0.5f) * 0.1f;
				off[2] = (RND.nextFloat() - 0.5f) * 0.1f;
			}
		}
	}

	static void tick(ClientLevel level, double camDist) {
		for (int k = LIST.size() - 1; k >= 0; k--) {
			Mass m = LIST.get(k);
			m.age++;
			// trailing dust from the lower edge
			if (m.age < m.path.length - 2 && (m.age & 1) == 0) {
				float t = m.age;
				float[] R = new float[9], pv = new float[3], of = new float[3];
				pose(m, t, R, pv, of);
				int puffs = Math.min(5, 1 + m.n / 900);
				for (int p = 0; p < puffs; p++) {
					int i = RND.nextInt(m.n);
					if ((m.mask[i] & 0x3d) == 0 && RND.nextInt(3) != 0) {
						continue;
					}
					float[] w = world(m, i, R, pv, of);
					FxClient.puff(level, w[0], w[1] - 0.5f, w[2], 2.2f + m.dust * 0.25f, 1, 0.12f, 0.02f, 0.6f);
				}
			}
			if (m.age >= m.path.length - 1) {
				land(level, m);
				LIST.remove(k);
			}
		}
	}

	private static final float[] TMP = new float[3];

	private static float[] world(Mass m, int i, float[] R, float[] pv, float[] of) {
		float rx = m.bx[i] - pv[0], ry = m.by[i] - pv[1], rz = m.bz[i] - pv[2];
		TMP[0] = pv[0] + R[0] * rx + R[1] * ry + R[2] * rz + of[0];
		TMP[1] = pv[1] + R[3] * rx + R[4] * ry + R[5] * rz + of[1];
		TMP[2] = pv[2] + R[6] * rx + R[7] * ry + R[8] * rz + of[2];
		return TMP;
	}

	private static void land(ClientLevel level, Mass m) {
		float[] R = new float[9], pv = new float[3], of = new float[3];
		float tEnd = m.path.length - 1;
		pose(m, tEnd, R, pv, of);
		float[] R2 = new float[9], pv2 = new float[3], of2 = new float[3];
		pose(m, Math.max(0, tEnd - 1), R2, pv2, of2);
		int free = Math.max(0, Debris.maxLive - Debris.n);
		int take = Math.min(m.n, free);
		double step = take > 0 ? (double) m.n / take : 1;
		double pos = RND.nextDouble() * step;
		float lowest = Float.MAX_VALUE;
		float sx = 0, sz = 0;
		float[] wlast = new float[3];
		for (int k = 0; k < take; k++) {
			int i = Math.min(m.n - 1, (int) pos);
			pos += step;
			float[] w = world(m, i, R, pv, of);
			float wx = w[0], wy = w[1], wz = w[2];
			float[] w2 = world(m, i, R2, pv2, of2);
			float vx = (wx - w2[0]) * 0.55f, vy = (wy - w2[1]) * 0.55f, vz = (wz - w2[2]) * 0.55f;
			float dx = wx - m.cx, dz = wz - m.cz;
			float dl = (float) Math.sqrt(dx * dx + dz * dz) + 0.01f;
			float out = 0.05f + RND.nextFloat() * 0.35f;
			vx += dx / dl * out + (RND.nextFloat() - 0.5f) * 0.1f;
			vz += dz / dl * out + (RND.nextFloat() - 0.5f) * 0.1f;
			vy += RND.nextFloat() * 0.3f - 0.05f;
			vy = Math.max(vy, -0.9f);
			Debris.spawn(wx, wy, wz, vx, vy, vz, m.meshId[i], 0.5f + RND.nextFloat() * 0.45f, Debris.sampleLight(level, wx, wy + 1, wz),
					80 + RND.nextInt(160), 6);
			if (wy < lowest) {
				lowest = wy;
			}
		}
		// the dust cloud: a ring and a heap, scaled by the size of the mass
		float size = 3f + m.dust * 0.8f;
		int puffs = Math.min(70, 14 + (int) (m.dust * 4));
		float[] w0 = world(m, 0, R, pv, of);
		float ground = lowest == Float.MAX_VALUE ? w0[1] : lowest;
		for (int p = 0; p < puffs; p++) {
			int i = RND.nextInt(m.n);
			float[] w = world(m, i, R, pv, of);
			FxClient.puff(level, w[0], ground, w[2], size, 1, 0.35f, 0.08f, 1.0f);
		}
		FxClient.landingFx(level, m.cx, ground, m.cz, m.dust);
	}

	// ------------------------------------------------------------------------------------------------------------------

	static int[] listIdx = new int[0];
	static float camX, camY, camZ, partial, fwdX, fwdY, fwdZ;
	static final float[] RM = new float[9];

	static void emit(VertexConsumer out, boolean translucent, float partialTicks) {
		long t0 = System.nanoTime();
		int q = 0;
		float[] R = new float[9], pv = new float[3], of = new float[3];
		for (Mass m : LIST) {
			float t = m.age + partialTicks;
			pose(m, t, R, pv, of);
			float maxD2 = Debris.cullDistance * Debris.cullDistance * 2.0f;
			for (int i = 0; i < m.n; i++) {
				MeshCache.Mesh mesh = MeshCache.get(m.meshId[i]);
				if (mesh.quads == 0 || (translucent && mesh.quads == mesh.opaque)) {
					continue;
				}
				int mask = m.mask[i];
				if (mask == 0) {
					continue;
				}
				float rx = m.bx[i] - pv[0], ry = m.by[i] - pv[1], rz = m.bz[i] - pv[2];
				float cx = pv[0] + R[0] * rx + R[1] * ry + R[2] * rz + of[0] - Debris.camX;
				float cy = pv[1] + R[3] * rx + R[4] * ry + R[5] * rz + of[1] - Debris.camY;
				float cz = pv[2] + R[6] * rx + R[7] * ry + R[8] * rz + of[2] - Debris.camZ;
				float d2 = cx * cx + cy * cy + cz * cz;
				if (d2 > maxD2) {
					continue;
				}
				float dot = cx * Debris.fwdX + cy * Debris.fwdY + cz * Debris.fwdZ;
				if (dot < 0.18f * (float) Math.sqrt(d2) - 2.5f) {
					continue;
				}
				q += Draw.emitBlock(out, mesh, translucent, cx, cy, cz, R, 1.0f, m.baseLight, mask);
			}
		}
		quads = q;
		drawNanos += System.nanoTime() - t0;
	}
}
