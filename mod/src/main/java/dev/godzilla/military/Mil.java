package dev.godzilla.military;

import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers of the military module: where Godzilla's body is, harmless hits on it, sounds, small per-tick budgets,
 * counters. Everything is relative to Godzilla's scale (16 = 29 blocks tall: torso at about y+14 to y+22 of his feet).
 */
public final class Mil {
	private Mil() {
	}

	// ---- budgets (reset every server tick by the director) --------------------------------------------------------------
	static int puffBudget;
	static int soundBudget;
	/** Small explosions of missed shots; refilled 1 per 5 ticks up to 6. */
	static int blastTokens = 6;

	// ---- counters for /godzilla mil stats ---------------------------------------------------------------------------------
	public static long shotsFired, hitsOnBody, missesOnCity, kills, directorNanos, unitNanos;
	private static final long[] TICK_RING = new long[100];
	private static int ringPos;
	private static long maxNanos;

	static void endTick() {
		long total = directorNanos + unitNanos;
		TICK_RING[ringPos++ % TICK_RING.length] = total;
		maxNanos = Math.max(maxNanos * 99 / 100, total);
		directorNanos = 0;
		unitNanos = 0;
	}

	static double avgTickMs() {
		long sum = 0;
		for (long v : TICK_RING) {
			sum += v;
		}
		return sum / (double) TICK_RING.length / 1e6;
	}

	static double maxTickMs() {
		return maxNanos / 1e6;
	}

	static void resetCounters() {
		shotsFired = hitsOnBody = missesOnCity = kills = 0;
	}

	// ---- game state --------------------------------------------------------------------------------------------------------
	/** True while the military may shoot: RAMPAGE and MOTHRA, and the ships from shortly before Godzilla's head breaks the surface. */
	public static boolean mayFire(boolean ship) {
		Game g = Gz.game();
		Game.Phase p = g.phase();
		if (p.playing()) {
			return true;
		}
		return ship && p == Game.Phase.RISE && g.phaseTicks() >= Game.RISE_SURFACE_TICK - 90;
	}

	/**
	 * Test aid ({@code /godzilla mil focus x y z}): when set, everything shoots at a Godzilla standing here instead of at the
	 * player, so the camera can be moved away to watch the fight from the side. Null normally.
	 */
	static Vec3 focus;

	/** Where Godzilla's feet are (the player's position, or the test focus). */
	public static Vec3 feet(ServerPlayer g) {
		return focus != null ? focus : g.position();
	}

	public static float scaleOf(ServerPlayer g) {
		return Math.max(1.0F, g.getScale());
	}

	/** A point on Godzilla's torso (14..22 blocks above his feet at scale 16) with a little sideways spread. */
	public static Vec3 bodyPoint(ServerPlayer g, RandomSource r) {
		float s = scaleOf(g);
		Vec3 f = Mil.feet(g);
		return new Vec3(f.x + (r.nextFloat() - 0.5) * 0.3 * s, f.y + s * (0.875 + 0.5 * r.nextFloat()), f.z + (r.nextFloat() - 0.5) * 0.3 * s);
	}

	public static Vec3 bodyCenter(ServerPlayer g) {
		return Mil.feet(g).add(0, scaleOf(g) * 1.1, 0);
	}

	/** True when a point is inside the torso: a vertical cylinder of 0.45 x scale radius from 0.3 to 1.65 x scale up. */
	public static boolean insideBody(ServerPlayer g, Vec3 p) {
		float s = scaleOf(g);
		Vec3 f = Mil.feet(g);
		double dx = p.x - f.x, dz = p.z - f.z;
		return dx * dx + dz * dz < (0.45 * s) * (0.45 * s) && p.y > f.y + 0.3 * s && p.y < f.y + 1.65 * s;
	}

	public static double groundY(ServerLevel level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}

	// ---- effects -----------------------------------------------------------------------------------------------------------
	/**
	 * Plays a sound so that everybody within {@code range} blocks hears it. Variable-range sound events reach 16 x volume
	 * blocks (and the client scales the falloff with the same factor), so the volume is range / 16.
	 */
	public static void sound(ServerLevel level, Vec3 at, SoundEvent ev, float range, float pitch) {
		level.playSound(null, at.x, at.y, at.z, ev, SoundSource.HOSTILE, Math.max(1.0F, range / 16.0F), pitch);
	}

	/** Rate-limited sound for things that happen many times a second. */
	public static void soundLimited(ServerLevel level, Vec3 at, SoundEvent ev, float range, float pitch) {
		if (soundBudget > 0) {
			soundBudget--;
			sound(level, at, ev, range, pitch);
		}
	}

	/**
	 * A harmless hit on Godzilla: flash on that side, shake, the thud, and sparks plus smoke puffs on his body at the point.
	 *
	 * @param strength 0..1 as for {@link dev.godzilla.api.Fx#hitFlash}
	 */
	public static void hitBody(ServerLevel level, ServerPlayer g, Vec3 point, Vec3 from, float strength, boolean heavy) {
		hitsOnBody++;
		Gz.fx().hitFlash(g, from, strength);
		Gz.fx().shake(g, Math.min(1.0F, strength * 0.6F), heavy ? 10 : 4);
		if (heavy) {
			sound(level, point, GodzillaSounds.MIL_SHELL_HIT, 120, 0.85F + level.getRandom().nextFloat() * 0.3F);
		} else {
			soundLimited(level, point, GodzillaSounds.MIL_SHELL_HIT, 100, 1.1F + level.getRandom().nextFloat() * 0.3F);
		}
		if (puffBudget > 0) {
			puffBudget--;
			float s = scaleOf(g);
			double sp = 0.05 * s;
			level.sendParticles(ParticleTypes.FIREWORK, true, true, point.x, point.y, point.z, heavy ? 10 : 4, sp, sp, sp, 0.25);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, point.x, point.y, point.z, heavy ? 4 : 2, sp, sp, sp, 0.03);
			if (heavy) {
				level.sendParticles(ParticleTypes.POOF, true, true, point.x, point.y, point.z, 3, sp * 2, sp * 2, sp * 2, 0.06);
				if (level.getRandom().nextBoolean()) {
					level.sendParticles(ParticleTypes.EXPLOSION, true, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
				}
			}
		}
	}

	/** A shell lands in the sea: a big white column and a spray ring (the splash scales with {@code size} in blocks). */
	public static void splash(ServerLevel level, double x, double z, float size) {
		double y = 63.0;
		level.sendParticles(ParticleTypes.CLOUD, true, true, x, y + size * 0.4, z, (int) (10 + size * 3), size * 0.25, size * 0.5, size * 0.25, 0.12);
		level.sendParticles(ParticleTypes.SPLASH, true, true, x, y + 0.5, z, (int) (40 + size * 10), size * 0.35, size * 0.3, size * 0.35, 0.8);
		level.sendParticles(ParticleTypes.BUBBLE_POP, true, true, x, y + 0.5, z, 12, size * 0.4, 0.1, size * 0.4, 0.05);
	}

	/** Takes one token for a small explosion of a shot that missed and hit the city; false when the budget is used up. */
	public static boolean takeBlast() {
		if (blastTokens > 0) {
			blastTokens--;
			return true;
		}
		return false;
	}

	// ---- geometry ----------------------------------------------------------------------------------------------------------
	/** Minecraft yaw in degrees (0 = +z, 90 = -x) of a horizontal direction. */
	public static float yawOf(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Unit direction for a yaw and an elevation (up positive), both in degrees. */
	public static Vec3 dir(double yawDeg, double elevDeg) {
		double y = Math.toRadians(yawDeg), e = Math.toRadians(elevDeg);
		return new Vec3(-Math.sin(y) * Math.cos(e), Math.sin(e), Math.cos(y) * Math.cos(e));
	}

	public static float wrap(float deg) {
		return Mth.wrapDegrees(deg);
	}

	/** Moves an angle toward a target by at most {@code step} degrees. */
	public static float approach(float cur, float target, float step) {
		float d = Mth.wrapDegrees(target - cur);
		return cur + Mth.clamp(d, -step, step);
	}
}
