package dev.godzilla.client.destruction;

import dev.godzilla.core.GodzillaSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Dust clouds, fireballs, smoke columns and lasting fires, all from {@link GzPuff} billows and a few vanilla flames. */
final class FxClient {
	static SpriteSet sprites;
	static int puffsThisTick;
	static final int PUFF_CAP = 260;
	static long clientTick;
	static final RandomSource RND = RandomSource.create();
	static long statPuffs;

	private static final class Column {
		float x, y, z, height, width;
		int left;
	}

	private static final class Site {
		int id;
		float x, y, z, radius;
		long end;
		int soundPhase;
	}

	private static final List<Column> COLUMNS = new ArrayList<>();
	private static final List<Site> SITES = new ArrayList<>();

	private FxClient() {
	}

	static void clear() {
		COLUMNS.clear();
		SITES.clear();
	}

	static int fireCount() {
		return SITES.size();
	}

	static GzPuff make(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float size, int life) {
		if (sprites == null || puffsThisTick >= PUFF_CAP) {
			return null;
		}
		puffsThisTick++;
		statPuffs++;
		GzPuff p = new GzPuff(level, x, y, z, vx, vy, vz, sprites.get(RND), life, size);
		Minecraft.getInstance().particleEngine.add(p);
		return p;
	}

	/** Grey-brown dust. spread = outward speed, rise = upward speed (blocks per tick). */
	static void puff(ClientLevel level, float x, float y, float z, float size, int count, float spread, float rise, float opacity) {
		for (int i = 0; i < count; i++) {
			float a = RND.nextFloat() * 6.2831855f;
			float s = spread * (0.3f + RND.nextFloat() * 0.9f);
			float c = 0.58f + RND.nextFloat() * 0.18f;
			GzPuff p = make(level, x + (RND.nextFloat() - 0.5f) * size * 0.3f, y + RND.nextFloat() * size * 0.15f, z + (RND.nextFloat() - 0.5f) * size * 0.3f,
					Math.cos(a) * s, rise * (0.4f + RND.nextFloat()), Math.sin(a) * s, size * (0.7f + RND.nextFloat() * 0.6f), 55 + RND.nextInt(55));
			if (p != null) {
				p.setCol(c, c * 0.95f, c * 0.86f, c * 0.8f, c * 0.77f, c * 0.7f);
				p.a0 = 0.55f * opacity;
				p.drag = 0.93f;
				p.buoyancy = 0.0012f;
			}
		}
	}

	/** Dust that goes with a burst of flying pieces. */
	static void burstDust(ClientLevel level, float ox, float oy, float oz, int[] rel, int count, int mode, float power, double camDist) {
		if (camDist > 330) {
			return;
		}
		int cnt = (int) Math.max(1, Math.min(14, Math.sqrt(count) * 0.45));
		if (camDist > 150) {
			cnt = Math.max(1, cnt / 2);
		}
		float size = 1.6f + (float) Math.sqrt(count) * 0.09f + (camDist > 100 ? (float) (camDist - 100) * 0.02f : 0);
		for (int i = 0; i < cnt; i++) {
			int k = RND.nextInt(count);
			puff(level, ox + rel[3 * k], oy + rel[3 * k + 1], oz + rel[3 * k + 2], size, 1, mode == 1 ? 0.12f : 0.06f, 0.03f, 1f);
		}
	}

	static void landingFx(ClientLevel level, float x, float y, float z, float dust) {
		// a heavy ring rolling outward along the ground and a slow plume above
		int ring = (int) Math.min(30, 10 + dust * 2);
		for (int i = 0; i < ring; i++) {
			float a = i / (float) ring * 6.2831855f + RND.nextFloat() * 0.3f;
			float r = dust * (0.6f + RND.nextFloat() * 0.8f);
			float s = 0.16f + RND.nextFloat() * 0.1f;
			GzPuff p = make(level, x + Math.cos(a) * r, y + 0.5, z + Math.sin(a) * r, Math.cos(a) * s, 0.01, Math.sin(a) * s, 3.5f + dust * 0.45f, 80 + RND.nextInt(60));
			if (p != null) {
				float c = 0.6f + RND.nextFloat() * 0.15f;
				p.setCol(c, c * 0.95f, c * 0.85f, c * 0.78f, c * 0.76f, c * 0.68f);
				p.a0 = 0.6f;
				p.drag = 0.94f;
				p.buoyancy = 0.001f;
			}
		}
		for (int i = 0; i < 8; i++) {
			GzPuff p = make(level, x + (RND.nextFloat() - 0.5f) * dust, y + 1, z + (RND.nextFloat() - 0.5f) * dust, 0, 0.12 + RND.nextFloat() * 0.1, 0,
					4.5f + dust * 0.6f, 100 + RND.nextInt(60));
			if (p != null) {
				p.setCol(0.68f, 0.64f, 0.58f, 0.5f, 0.48f, 0.45f);
				p.a0 = 0.5f;
				p.drag = 0.95f;
			}
		}
	}

	/** Fireball with flash and rising black smoke. */
	static void explosion(ClientLevel level, float x, float y, float z, float r, boolean fuel) {
		float scale = fuel ? 1.5f : 1f;
		// flash
		GzPuff f = make(level, x, y, z, 0, 0, 0, r * 2.4f * scale, 7);
		if (f != null) {
			f.setCol(1f, 0.95f, 0.75f, 1f, 0.7f, 0.3f);
			f.a0 = 1f;
			f.glow = 1f;
			f.grow = 0.4f;
		}
		int fire = (int) Math.min(90, 16 + r * 5);
		for (int i = 0; i < fire; i++) {
			Vec3 d = randomDir();
			float sp = r * (0.03f + RND.nextFloat() * 0.06f) * scale;
			GzPuff p = make(level, x + d.x * r * 0.3, y + d.y * r * 0.3, z + d.z * r * 0.3, d.x * sp, d.y * sp * 0.8 + sp * 0.4, d.z * sp,
					r * (0.5f + RND.nextFloat() * 0.6f) * scale, 20 + RND.nextInt(22));
			if (p != null) {
				p.setCol(1f, 0.82f, 0.4f, 0.28f, 0.12f, 0.06f);
				p.a0 = 0.95f;
				p.glow = 1f;
				p.drag = 0.86f;
				p.buoyancy = 0.012f;
				p.grow = 0.9f;
			}
		}
		int smoke = (int) Math.min(80, 14 + r * 4);
		for (int i = 0; i < smoke; i++) {
			Vec3 d = randomDir();
			float sp = r * (0.012f + RND.nextFloat() * 0.03f) * scale;
			GzPuff p = make(level, x + d.x * r * 0.5, y + d.y * r * 0.4, z + d.z * r * 0.5, d.x * sp, Math.abs(d.y) * sp + 0.05 + r * 0.01, d.z * sp,
					r * (0.7f + RND.nextFloat() * 0.8f) * scale, 70 + RND.nextInt(80));
			if (p != null) {
				float c = 0.12f + RND.nextFloat() * 0.1f;
				p.setCol(c * 1.6f, c * 1.2f, c, c, c, c);
				p.a0 = 0.8f;
				p.drag = 0.95f;
				p.buoyancy = 0.003f;
				p.grow = 1.6f;
			}
		}
		for (int i = 0; i < Math.min(40, 8 + r * 2); i++) {
			Vec3 d = randomDir();
			double sp = 0.2 + RND.nextFloat() * 0.5;
			level.addParticle(ParticleTypes.LAVA, x, y, z, d.x * sp, Math.abs(d.y) * sp + 0.2, d.z * sp);
		}
		if (r >= 4) {
			smokeColumn(x, y, z, r * 5 * scale, r * 0.8f);
		}
	}

	static void smokeColumn(float x, float y, float z, float height, float width) {
		Column c = new Column();
		c.x = x;
		c.y = y;
		c.z = z;
		c.height = height;
		c.width = width;
		c.left = 70;
		COLUMNS.add(c);
	}

	private static Vec3 randomDir() {
		double a = RND.nextDouble() * Math.PI * 2, b = RND.nextDouble() * 2 - 1, r = Math.sqrt(1 - b * b);
		return new Vec3(r * Math.cos(a), b, r * Math.sin(a));
	}

	// ------------------------------------------------------------------------------------------------------------------
	// fires

	static void fireAdd(int id, float x, float y, float z, float radius, int ttl) {
		for (Site s : SITES) {
			if (s.id == id) {
				s.radius = radius;
				s.end = clientTick + ttl;
				return;
			}
		}
		Site s = new Site();
		s.id = id;
		s.x = x;
		s.y = y;
		s.z = z;
		s.radius = radius;
		s.end = clientTick + ttl;
		s.soundPhase = RND.nextInt(60);
		SITES.add(s);
	}

	static void fireRemove(int id) {
		SITES.removeIf(s -> s.id == id);
	}

	static void fireClear() {
		SITES.clear();
	}

	static void tick(ClientLevel level) {
		clientTick++;
		puffsThisTick = 0;
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		for (int i = COLUMNS.size() - 1; i >= 0; i--) {
			Column c = COLUMNS.get(i);
			if (--c.left < 0) {
				COLUMNS.remove(i);
				continue;
			}
			for (int k = 0; k < 3; k++) {
				float rise = c.height / 75f;
				GzPuff p = make(level, c.x + (RND.nextFloat() - 0.5f) * c.width * 0.5f, c.y + 1, c.z + (RND.nextFloat() - 0.5f) * c.width * 0.5f,
						(RND.nextFloat() - 0.5f) * 0.03, rise * (0.7f + RND.nextFloat() * 0.5f), (RND.nextFloat() - 0.5f) * 0.03, c.width * (0.6f + RND.nextFloat() * 0.5f), 90 + RND.nextInt(60));
				if (p != null) {
					float g = 0.1f + RND.nextFloat() * 0.08f;
					p.setCol(g * 1.4f, g * 1.1f, g, g * 1.2f, g * 1.2f, g * 1.2f);
					p.a0 = 0.85f;
					p.drag = 0.985f;
					p.grow = 1.8f;
				}
			}
		}
		for (int i = SITES.size() - 1; i >= 0; i--) {
			Site s = SITES.get(i);
			if (clientTick >= s.end) {
				SITES.remove(i);
				continue;
			}
			double dx = s.x - cam.x, dy = s.y - cam.y, dz = s.z - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > 400) {
				continue;
			}
			// flames and smoke on the surfaces of what is still standing
			int tries = dist > 200 ? 1 : (int) Math.min(5, 2 + s.radius * 0.4);
			for (int k = 0; k < tries; k++) {
				float a = RND.nextFloat() * 6.2831855f, rr = (float) Math.sqrt(RND.nextFloat()) * s.radius;
				int bx = (int) Math.floor(s.x + Math.cos(a) * rr), bz = (int) Math.floor(s.z + Math.sin(a) * rr);
				int top = (int) Math.floor(s.y + s.radius * 0.6f + 2);
				int by = top;
				BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
				int lim = (int) (s.radius * 1.6f + 6);
				while (lim-- > 0) {
					BlockState st = level.getBlockState(mp.set(bx, by, bz));
					if (!st.isAir()) {
						break;
					}
					by--;
				}
				if (lim <= 0) {
					continue;
				}
				double fx = bx + 0.5 + (RND.nextFloat() - 0.5f) * 0.6, fy = by + 1.05, fz = bz + 0.5 + (RND.nextFloat() - 0.5f) * 0.6;
				if (level.getBlockState(mp.set(bx, by + 1, bz)).isAir()) {
					var fl = mc.particleEngine.createParticle(ParticleTypes.FLAME, fx, fy, fz, 0, 0.02, 0);
					if (fl != null) {
						fl.scale(1.6f + RND.nextFloat() * 1.6f);
					}
					if (RND.nextInt(2) == 0) {
						GzPuff p = make(level, fx, fy + 0.5, fz, (RND.nextFloat() - 0.5f) * 0.02, 0.06 + RND.nextFloat() * 0.04, (RND.nextFloat() - 0.5f) * 0.02,
								1.4f + RND.nextFloat() * 1.2f, 50 + RND.nextInt(50));
						if (p != null) {
							float g = 0.15f + RND.nextFloat() * 0.1f;
							p.setCol(g, g, g, g * 1.3f, g * 1.3f, g * 1.3f);
							p.a0 = 0.7f;
							p.drag = 0.97f;
							p.grow = 2.2f;
						}
					}
				}
			}
			// the crackle, only for the few nearest and only when close enough to hear it
			if (dist < 110 && ((clientTick + s.soundPhase) % 60) == 0 && nearestRank(s, cam) < 3) {
				level.playLocalSound(s.x, s.y, s.z, GodzillaSounds.FIRE_LOOP, SoundSource.BLOCKS, 2.5f + s.radius * 0.1f, 1f, false);
			}
		}
	}

	private static int nearestRank(Site s, Vec3 cam) {
		double d = s.x * 0;
		double mine = (s.x - cam.x) * (s.x - cam.x) + (s.z - cam.z) * (s.z - cam.z);
		int rank = 0;
		for (Site o : SITES) {
			if (o != s && (o.x - cam.x) * (o.x - cam.x) + (o.z - cam.z) * (o.z - cam.z) < mine) {
				rank++;
			}
		}
		return rank;
	}
}
