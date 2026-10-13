package dev.godzilla.client.destruction;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thousands of flying pieces as plain arrays: simulated once per client tick (gravity, spin, bounce on the real blocks, resting,
 * shrinking away), drawn each frame by writing the real baked quads of their block into one vertex buffer.
 */
final class Debris {
	static final float GRAVITY = 0.052f;
	static final float DRAG = 0.992f;

	// state
	static int n;
	static int maxLive = 24000;
	static final int HARD_MAX = 120000;
	static boolean adaptive = true;
	static boolean floatMode = false;

	static float[] x = new float[0], y, z, px, py, pz, vx, vy, vz;
	static float[] qx, qy, qz, qw, pqx, pqy, pqz, pqw, wx, wy, wz, ww;
	static float[] size, restYaw;
	static int[] mesh, light;
	static short[] age, ttl;
	static byte[] state, ghost, settle;

	static final RandomSource RND = RandomSource.create();
	static long statSpawned, statDropped, statBounces;

	static final float[] DIR_X = new float[6], DIR_Y = new float[6], DIR_Z = new float[6];

	static {
		for (Direction d : Direction.values()) {
			DIR_X[d.ordinal()] = d.getStepX();
			DIR_Y[d.ordinal()] = d.getStepY();
			DIR_Z[d.ordinal()] = d.getStepZ();
		}
	}

	private Debris() {
	}

	static void trim(int excess) {
		for (int i = n - 1; i >= 0 && excess > 0; i--, excess--) {
			if (ttl[i] - age[i] > 12) {
				ttl[i] = (short) (age[i] + 12);
			}
		}
	}

	static void clear() {
		n = 0;
	}

	private static void ensure(int cap) {
		if (x.length >= cap) {
			return;
		}
		int c = Math.max(cap, Math.min(HARD_MAX, Math.max(4096, x.length * 2)));
		x = java.util.Arrays.copyOf(x, c);
		y = java.util.Arrays.copyOf(y == null ? new float[0] : y, c);
		z = java.util.Arrays.copyOf(z == null ? new float[0] : z, c);
		px = grow(px, c);
		py = grow(py, c);
		pz = grow(pz, c);
		vx = grow(vx, c);
		vy = grow(vy, c);
		vz = grow(vz, c);
		qx = grow(qx, c);
		qy = grow(qy, c);
		qz = grow(qz, c);
		qw = grow(qw, c);
		pqx = grow(pqx, c);
		pqy = grow(pqy, c);
		pqz = grow(pqz, c);
		pqw = grow(pqw, c);
		wx = grow(wx, c);
		wy = grow(wy, c);
		wz = grow(wz, c);
		ww = grow(ww, c);
		size = grow(size, c);
		restYaw = grow(restYaw, c);
		mesh = java.util.Arrays.copyOf(mesh == null ? new int[0] : mesh, c);
		light = java.util.Arrays.copyOf(light == null ? new int[0] : light, c);
		age = java.util.Arrays.copyOf(age == null ? new short[0] : age, c);
		ttl = java.util.Arrays.copyOf(ttl == null ? new short[0] : ttl, c);
		state = java.util.Arrays.copyOf(state == null ? new byte[0] : state, c);
		ghost = java.util.Arrays.copyOf(ghost == null ? new byte[0] : ghost, c);
		settle = java.util.Arrays.copyOf(settle == null ? new byte[0] : settle, c);
	}

	private static float[] grow(float[] a, int c) {
		return java.util.Arrays.copyOf(a == null ? new float[0] : a, c);
	}

	/** @return false when the pool is full */
	static boolean spawn(float sx, float sy, float sz, float svx, float svy, float svz, int meshId, float sSize, int lightCoords, int life, int ghostTicks) {
		if (n >= maxLive || n >= HARD_MAX) {
			statDropped++;
			return false;
		}
		ensure(n + 1);
		int i = n++;
		x[i] = px[i] = sx;
		y[i] = py[i] = sy;
		z[i] = pz[i] = sz;
		vx[i] = svx;
		vy[i] = svy;
		vz[i] = svz;
		// random orientation and tumble
		float a = RND.nextFloat() * 6.2831855f, b = RND.nextFloat() * 2 - 1, r = (float) Math.sqrt(1 - b * b);
		float ax = r * (float) Math.cos(a), ay = b, az = r * (float) Math.sin(a);
		float ang = RND.nextFloat() * 6.2831855f;
		float s = (float) Math.sin(ang / 2);
		qx[i] = pqx[i] = ax * s;
		qy[i] = pqy[i] = ay * s;
		qz[i] = pqz[i] = az * s;
		qw[i] = pqw[i] = (float) Math.cos(ang / 2);
		float a2 = RND.nextFloat() * 6.2831855f, b2 = RND.nextFloat() * 2 - 1, r2 = (float) Math.sqrt(1 - b2 * b2);
		float spin = (0.04f + RND.nextFloat() * 0.34f) * 0.5f;
		float ss = (float) Math.sin(spin);
		wx[i] = r2 * (float) Math.cos(a2) * ss;
		wy[i] = b2 * ss;
		wz[i] = r2 * (float) Math.sin(a2) * ss;
		ww[i] = (float) Math.cos(spin);
		size[i] = sSize;
		mesh[i] = meshId;
		light[i] = lightCoords;
		age[i] = 0;
		ttl[i] = (short) life;
		state[i] = 0;
		ghost[i] = (byte) ghostTicks;
		settle[i] = 0;
		restYaw[i] = RND.nextFloat() * 6.2831855f;
		statSpawned++;
		return true;
	}

	private static void remove(int i) {
		int l = --n;
		if (i != l) {
			x[i] = x[l];
			y[i] = y[l];
			z[i] = z[l];
			px[i] = px[l];
			py[i] = py[l];
			pz[i] = pz[l];
			vx[i] = vx[l];
			vy[i] = vy[l];
			vz[i] = vz[l];
			qx[i] = qx[l];
			qy[i] = qy[l];
			qz[i] = qz[l];
			qw[i] = qw[l];
			pqx[i] = pqx[l];
			pqy[i] = pqy[l];
			pqz[i] = pqz[l];
			pqw[i] = pqw[l];
			wx[i] = wx[l];
			wy[i] = wy[l];
			wz[i] = wz[l];
			ww[i] = ww[l];
			size[i] = size[l];
			restYaw[i] = restYaw[l];
			mesh[i] = mesh[l];
			light[i] = light[l];
			age[i] = age[l];
			ttl[i] = ttl[l];
			state[i] = state[l];
			ghost[i] = ghost[l];
			settle[i] = settle[l];
		}
	}

	// ------------------------------------------------------------------------------------------------------------------
	// simulation, once per client tick

	private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

	/** 0 air, 1 solid (stops pieces), 2 liquid. */
	private static int cell(ClientLevel level, int ix, int iy, int iz) {
		BlockState st = level.getBlockState(MP.set(ix, iy, iz));
		if (st.isAir()) {
			return 0;
		}
		if (!st.getFluidState().isEmpty()) {
			return 2;
		}
		return st.getCollisionShape(level, MP).isEmpty() ? 0 : 1;
	}

	static void tick(ClientLevel level) {
		int minY = level.getMinY() - 8;
		for (int i = n - 1; i >= 0; i--) {
			int a = ++age[i];
			if (a >= ttl[i]) {
				remove(i);
				continue;
			}
			pqx[i] = qx[i];
			pqy[i] = qy[i];
			pqz[i] = qz[i];
			pqw[i] = qw[i];
			px[i] = x[i];
			py[i] = y[i];
			pz[i] = z[i];
			float half = size[i] * 0.5f;
			if (state[i] == 1) {
				// at rest: lie flat, check now and then that the floor is still there
				if (settle[i] > 0) {
					settle[i]--;
					float h = restYaw[i] * 0.5f;
					nlerp(i, 0, (float) Math.sin(h), 0, (float) Math.cos(h), 0.4f);
				}
				if (((a + i) & 7) == 0 && cell(level, (int) Math.floor(x[i]), (int) Math.floor(y[i] - half - 0.1f), (int) Math.floor(z[i])) != 1) {
					state[i] = 0;
					ww[i] = 1;
					wx[i] = wy[i] = wz[i] = 0;
				}
				continue;
			}
			float vxi = vx[i] * DRAG, vyi = (vy[i] - (floatMode ? 0f : GRAVITY)) * DRAG, vzi = vz[i] * DRAG;
			float nx = x[i] + vxi, ny = y[i] + vyi, nz = z[i] + vzi;
			if (ghost[i] > 0) {
				if (!floatMode) ghost[i]--;
			} else {
				int ix = (int) Math.floor(x[i]), iy = (int) Math.floor(y[i]), iz = (int) Math.floor(z[i]);
				int c = cell(level, (int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz));
				if (c == 2) {
					vxi *= 0.5f;
					vzi *= 0.5f;
					vyi *= 0.5f;
					if (ttl[i] - a > 10) {
						ttl[i] = (short) (a + 10);
					}
				} else if (c == 1) {
					statBounces++;
					// find which faces we hit: test the axes one after the other
					boolean hitX = cell(level, (int) Math.floor(nx), iy, iz) == 1;
					boolean hitZ = cell(level, ix, iy, (int) Math.floor(nz)) == 1;
					boolean hitY = cell(level, ix, (int) Math.floor(ny), iz) == 1;
					if (!hitX && !hitY && !hitZ) {
						hitY = true; // a corner: treat as a floor/ceiling hit
					}
					if (hitX) {
						vxi = -vxi * 0.45f;
						nx = x[i];
					}
					if (hitZ) {
						vzi = -vzi * 0.45f;
						nz = z[i];
					}
					if (hitY) {
						if (vyi < 0) {
							ny = (float) Math.floor(ny) + 1 + half;
							if (cell(level, (int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz)) == 1) {
								ny = y[i];
							}
							float impact = -vyi;
							vyi = impact * 0.32f;
							vxi *= 0.72f;
							vzi *= 0.72f;
							wx[i] *= 0.6f;
							wy[i] *= 0.6f;
							wz[i] *= 0.6f;
							ww[i] = (float) Math.sqrt(Math.max(0, 1 - wx[i] * wx[i] - wy[i] * wy[i] - wz[i] * wz[i]));
							if (impact < 0.16f && vxi * vxi + vzi * vzi < 0.012f) {
								state[i] = 1;
								vxi = vyi = vzi = 0;
								settle[i] = 5;
								ttl[i] = (short) Math.min(ttl[i], a + 50 + RND.nextInt(130));
								if (ttl[i] <= a + 20) {
									ttl[i] = (short) (a + 30);
								}
							}
						} else {
							ny = y[i];
							vyi = -vyi * 0.2f;
						}
					}
				}
			}
			vx[i] = vxi;
			vy[i] = vyi;
			vz[i] = vzi;
			x[i] = nx;
			y[i] = ny;
			z[i] = nz;
			if (ny < minY) {
				remove(i);
				continue;
			}
			// spin
			if (state[i] == 0) {
				float ox = qx[i], oy = qy[i], oz = qz[i], ow = qw[i];
				float wxi = wx[i], wyi = wy[i], wzi = wz[i], wwi = ww[i];
				float rx = wwi * ox + wxi * ow + wyi * oz - wzi * oy;
				float ry = wwi * oy - wxi * oz + wyi * ow + wzi * ox;
				float rz = wwi * oz + wxi * oy - wyi * ox + wzi * ow;
				float rw = wwi * ow - wxi * ox - wyi * oy - wzi * oz;
				float inv = 1f / (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
				qx[i] = rx * inv;
				qy[i] = ry * inv;
				qz[i] = rz * inv;
				qw[i] = rw * inv;
			}
			if (((a + i) % 12) == 0) {
				light[i] = sampleLight(level, nx, ny, nz);
			}
		}
	}

	private static void nlerp(int i, float tx, float ty, float tz, float tw, float t) {
		float d = qx[i] * tx + qy[i] * ty + qz[i] * tz + qw[i] * tw;
		if (d < 0) {
			tx = -tx;
			ty = -ty;
			tz = -tz;
			tw = -tw;
		}
		float rx = qx[i] + (tx - qx[i]) * t, ry = qy[i] + (ty - qy[i]) * t, rz = qz[i] + (tz - qz[i]) * t, rw = qw[i] + (tw - qw[i]) * t;
		float inv = 1f / (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
		qx[i] = rx * inv;
		qy[i] = ry * inv;
		qz[i] = rz * inv;
		qw[i] = rw * inv;
	}

	static int sampleLight(ClientLevel level, float fx, float fy, float fz) {
		MP.set((int) Math.floor(fx), (int) Math.floor(fy), (int) Math.floor(fz));
		int b = level.getBrightness(LightLayer.BLOCK, MP);
		int s = level.getBrightness(LightLayer.SKY, MP);
		return LightCoordsUtil.pack(b, Math.max(s, 9));
	}

	// ------------------------------------------------------------------------------------------------------------------
	// drawing

	static int[] opaqueList = new int[0], transList = new int[0];
	static int nOpaque, nTrans;
	static float camX, camY, camZ, partial;
	static float fwdX, fwdY, fwdZ;
	static long drawNanos;
	static int drawnPieces, drawnQuads;
	static float cullDistance = 280f;

	/** Culls and lists the pieces to draw this frame. */
	static void collect(double cx, double cy, double cz, float fx, float fy, float fz, float partialTicks) {
		camX = (float) cx;
		camY = (float) cy;
		camZ = (float) cz;
		fwdX = fx;
		fwdY = fy;
		fwdZ = fz;
		partial = partialTicks;
		if (opaqueList.length < n) {
			opaqueList = new int[Math.max(n, opaqueList.length * 2)];
			transList = new int[opaqueList.length];
		}
		int no = 0, nt = 0;
		float maxD2 = cullDistance * cullDistance;
		for (int i = 0; i < n; i++) {
			float rx = px[i] + (x[i] - px[i]) * partialTicks - camX;
			float ry = py[i] + (y[i] - py[i]) * partialTicks - camY;
			float rz = pz[i] + (z[i] - pz[i]) * partialTicks - camZ;
			float d2 = rx * rx + ry * ry + rz * rz;
			if (d2 > maxD2) {
				continue;
			}
			float dot = rx * fx + ry * fy + rz * fz;
			if (dot < 0.18f * (float) Math.sqrt(d2) - 2.5f) {
				continue;
			}
			MeshCache.Mesh m = MeshCache.get(mesh[i]);
			if (m.quads == 0) {
				continue;
			}
			if (m.opaque > 0) {
				opaqueList[no++] = i;
			}
			if (m.quads > m.opaque) {
				transList[nt++] = i;
			}
		}
		nOpaque = no;
		nTrans = nt;
	}

	private static final float[] R = new float[9];

	/** Writes the quads of every listed piece. translucentPass selects which quads of each mesh. */
	static void emit(VertexConsumer out, boolean translucentPass) {
		long t0 = System.nanoTime();
		int[] list = translucentPass ? transList : opaqueList;
		int count = translucentPass ? nTrans : nOpaque;
		int quadsDrawn = 0;
		for (int k = 0; k < count; k++) {
			int i = list[k];
			float rx = px[i] + (x[i] - px[i]) * partial - camX;
			float ry = py[i] + (y[i] - py[i]) * partial - camY;
			float rz = pz[i] + (z[i] - pz[i]) * partial - camZ;
			// nlerp of the orientation
			float ax = pqx[i], ay = pqy[i], az = pqz[i], aw = pqw[i];
			float bx = qx[i], by = qy[i], bz = qz[i], bw = qw[i];
			if (ax * bx + ay * by + az * bz + aw * bw < 0) {
				ax = -ax;
				ay = -ay;
				az = -az;
				aw = -aw;
			}
			float qx0 = ax + (bx - ax) * partial, qy0 = ay + (by - ay) * partial, qz0 = az + (bz - az) * partial, qw0 = aw + (bw - aw) * partial;
			float inv = 1f / (float) Math.sqrt(qx0 * qx0 + qy0 * qy0 + qz0 * qz0 + qw0 * qw0);
			quatToMatrix(qx0 * inv, qy0 * inv, qz0 * inv, qw0 * inv, R);
			float fade = Math.min(1f, (ttl[i] - age[i] - partial) / 14f);
			if (fade <= 0.02f) {
				continue;
			}
			float s = size[i] * (fade < 1f ? fade : 1f);
			quadsDrawn += Draw.emitBlock(out, MeshCache.get(mesh[i]), translucentPass, rx, ry, rz, R, s, light[i], -1);
		}
		drawnQuads = quadsDrawn;
		drawNanos += System.nanoTime() - t0;
	}

	static void quatToMatrix(float x, float y, float z, float w, float[] m) {
		float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
		m[0] = 1 - 2 * (yy + zz);
		m[1] = 2 * (xy - wz);
		m[2] = 2 * (xz + wy);
		m[3] = 2 * (xy + wz);
		m[4] = 1 - 2 * (xx + zz);
		m[5] = 2 * (yz - wx);
		m[6] = 2 * (xz - wy);
		m[7] = 2 * (yz + wx);
		m[8] = 1 - 2 * (xx + yy);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// spawning from the server's bursts

	static void burst(ClientLevel level, net.minecraft.core.BlockPos origin, float ix, float iy, float iz, int mode, float power, int[] rel, int[] ids, double camDist) {
		int count = ids.length;
		if (count == 0) {
			return;
		}
		boolean far = camDist > 170;
		int free = Math.max(0, maxLive - n);
		int take = Math.min(count, free);
		if (far) {
			take = Math.min(take, Math.max(8, count / 8));
		}
		double step = take > 0 ? (double) count / take : 1;
		double pos = RND.nextDouble() * step;
		float ox = origin.getX() + 0.5f, oy = origin.getY() + 0.5f, oz = origin.getZ() + 0.5f;
		float[] sparkle = null;
		int sparkles = 0;
		for (int k = 0; k < take; k++) {
			int i = Math.min(count - 1, (int) pos);
			pos += step;
			float bx = ox + rel[3 * i], by = oy + rel[3 * i + 1], bz = oz + rel[3 * i + 2];
			MeshCache.Mesh m = MeshCache.get(ids[i]);
			if (m.quads == 0) {
				continue;
			}
			boolean glass = m.quads > m.opaque;
			float dx = rel[3 * i], dy = rel[3 * i + 1], dz = rel[3 * i + 2];
			float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.001f;
			float ux = dx / dl, uy = dy / dl, uz = dz / dl;
			float svx, svy, svz;
			float rnd1 = RND.nextFloat() - 0.5f, rnd2 = RND.nextFloat() - 0.5f, rnd3 = RND.nextFloat() - 0.5f;
			float sp = 0.9f + RND.nextFloat() * 0.5f;
			if (mode == 1) { // blast
				float spd = power * (0.35f + RND.nextFloat() * 0.9f) / (1f + dl * 0.05f);
				svx = (ux + rnd1 * 0.5f) * spd;
				svy = (uy * 0.8f + 0.35f + rnd2 * 0.4f) * spd;
				svz = (uz + rnd3 * 0.5f) * spd;
			} else if (mode == 2) { // crumble
				svx = ux * 0.18f * sp + rnd1 * 0.3f;
				svy = rnd2 * 0.18f + 0.05f;
				svz = uz * 0.18f * sp + rnd3 * 0.3f;
			} else { // thrown
				float spread = 0.12f + 0.16f * power;
				svx = ix * sp + ux * 0.06f + rnd1 * spread * 2;
				svy = iy * sp + 0.06f + rnd2 * spread * 2 + uy * 0.05f;
				svz = iz * sp + uz * 0.06f + rnd3 * spread * 2;
			}
			float spd2 = svx * svx + svy * svy + svz * svz;
			if (spd2 > 6.25f) {
				float f = 2.5f / (float) Math.sqrt(spd2);
				svx *= f;
				svy *= f;
				svz *= f;
			}
			float sz = glass ? 0.3f + RND.nextFloat() * 0.3f : 0.4f + RND.nextFloat() * 0.45f;
			spawn(bx + (RND.nextFloat() - 0.5f) * 0.5f, by + (RND.nextFloat() - 0.5f) * 0.5f, bz + (RND.nextFloat() - 0.5f) * 0.5f, svx, svy, svz,
					ids[i], sz, sampleLight(level, bx, by, bz), 90 + RND.nextInt(180), 0);
			if (glass && sparkles < 16 && RND.nextInt(4) == 0 && camDist < 140) {
				sparkles++;
				level.addParticle(ParticleTypes.END_ROD, bx, by, bz, svx * 0.4, svy * 0.4 + 0.02, svz * 0.4);
			}
		}
		FxClient.burstDust(level, ox, oy, oz, rel, count, mode, power, camDist);
	}
}
