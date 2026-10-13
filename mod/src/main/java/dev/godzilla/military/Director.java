package dev.godzilla.military;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides how much military is on the screen. Escalation by {@link Game#runTicks()} (seconds, "t"):
 * <pre>
 *   ships    3 from the rise, 4 from 1:00, 5 from 2:00           (replaced 30 s after sinking)
 *   tanks    none until 0:20, then 4 + (t-20)/5, at most 20      (replaced after 2 s)
 *   helis    none until 0:20, then 1 + (t-20)/15, at most 8      (replaced after 4 s)
 *   jets     none until 1:15, then 2 + (t-75)/25, at most 6 alive, a pair every 22 s shrinking to 8 s
 *   masers   none until 2:00, then 1 + (t-120)/30, at most 4     (replaced after 10 s)
 * </pre>
 * That is about 40 units on screen by minute two. The same schedule keeps going in MOTHRA. {@code /godzilla mil wave n}
 * jumps the clock to n x 30 s.
 */
public final class Director {
	/** Godzilla's velocity in blocks per tick (smoothed), so shooters can lead him. */
	static Vec3 gVel = Vec3.ZERO;
	private static Vec3 lastG;
	/** Minimum clock set by /godzilla mil wave. */
	static int waveFloor;

	private static final long[] NEXT = new long[5];
	private static final int TANK = 0, HELI = 1, JET = 2, MASER = 3, SHIP = 4;
	private static final List<Pending> PENDING = new ArrayList<>();
	private static long tick;

	private record Pending(long at, int lane, boolean reverse) {
	}

	private Director() {
	}

	public static void reset() {
		MilitaryUnit.ALL.clear();
		PENDING.clear();
		java.util.Arrays.fill(NEXT, 0);
		waveFloor = 0;
		gVel = Vec3.ZERO;
		lastG = null;
		Mil.resetCounters();
	}

	/** Removes every unit and projectile of this module from the level. */
	public static void clear(ServerLevel level) {
		List<Entity> kill = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof MilitaryUnit || e instanceof MilShot) {
				kill.add(e);
			}
		}
		for (Entity e : kill) {
			e.discard();
		}
		MilitaryUnit.ALL.clear();
		PENDING.clear();
	}

	public static void onReset(Game game) {
		ServerLevel level = game.level();
		if (level != null) {
			clear(level);
		}
		reset();
	}

	public static void onPhase(dev.godzilla.api.GzEvents.PhaseChange c) {
		if (c.to() == Game.Phase.IDLE) {
			onReset(Gz.game());
		}
	}

	// ---- per tick ----------------------------------------------------------------------------------------------------------
	public static void tick(MinecraftServer server) {
		long t0 = System.nanoTime();
		tick++;
		Mil.puffBudget = 8;
		Mil.soundBudget = 4;
		if (tick % 5 == 0 && Mil.blastTokens < 6) {
			Mil.blastTokens++;
		}
		Game game = Gz.game();
		ServerLevel level = game.level();
		ServerPlayer g = game.godzilla();
		if (g != null) {
			Vec3 p = Mil.feet(g);
			if (lastG != null) {
				gVel = gVel.scale(0.8).add(p.subtract(lastG).scale(0.2));
			}
			lastG = p;
		} else {
			lastG = null;
		}
		Game.Phase phase = game.phase();
		if (level != null && g != null && (phase == Game.Phase.RISE || phase.playing())) {
			if (tick % 10 == 0) {
				fill(level, g, false);
			}
			if (!PENDING.isEmpty()) {
				for (int i = PENDING.size() - 1; i >= 0; i--) {
					Pending p = PENDING.get(i);
					if (tick >= p.at) {
						PENDING.remove(i);
						spawnJet(level, g, p.lane, p.reverse, 14.0);
					}
				}
			}
			if (tick % 20 == 0) {
				assignVoices(g);
			}
		}
		Mil.directorNanos += System.nanoTime() - t0;
		Mil.endTick();
	}

	private static double clock(Game game) {
		return Math.max(game.runTicks(), waveFloor) / 20.0;
	}

	static int count(Class<? extends MilitaryUnit> type) {
		int n = 0;
		for (MilitaryUnit u : MilitaryUnit.ALL) {
			if (type.isInstance(u) && !u.dying && !u.isRemoved() && !u.hasFlag(MilitaryUnit.F_SINK)) {
				n++;
			}
		}
		return n;
	}

	static int shotsInAir(ServerLevel level) {
		int n = 0;
		for (Entity e : level.getAllEntities()) {
			if (e instanceof MilShot) {
				n++;
			}
		}
		return n;
	}

	/** Brings every type up to its cap for the current clock. {@code force} ignores the per-type cooldowns (the wave command). */
	static void fill(ServerLevel level, ServerPlayer g, boolean force) {
		Game game = Gz.game();
		boolean rise = game.phase() == Game.Phase.RISE;
		double t = rise ? -1 : clock(game);
		RandomSource r = level.getRandom();
		int guard = force ? 80 : 1;
		while (guard-- > 0) {
			boolean any = false;
			int ships = rise ? 3 : t < 60 ? 3 : t < 120 ? 4 : 5;
			if (count(Warship.class) < ships && (force || tick >= NEXT[SHIP]) && spawnShip(level, g)) {
				NEXT[SHIP] = tick + (rise ? 20 : 600);
				any = true;
			}
			if (!rise) {
				int tanks = t < 20 ? 0 : (int) Math.min(20, 4 + (t - 20) / 5);
				if (count(Tank.class) < tanks && (force || tick >= NEXT[TANK]) && spawnTank(level, g)) {
					NEXT[TANK] = tick + 40;
					any = true;
				}
				int helis = t < 20 ? 0 : (int) Math.min(8, 1 + (t - 20) / 15);
				if (count(Helicopter.class) < helis && (force || tick >= NEXT[HELI]) && spawnHeli(level, g)) {
					NEXT[HELI] = tick + 80;
					any = true;
				}
				int jets = t < 75 ? 0 : (int) Math.min(6, 2 + 2 * ((t - 75) / 50));
				if (count(Jet.class) + PENDING.size() <= jets - 2 && (force || tick >= NEXT[JET])) {
					spawnJetPair(level, g);
					NEXT[JET] = tick + (long) Math.max(160, 440 - (t - 75) * 3);
					any = true;
				}
				int masers = t < 120 ? 0 : (int) Math.min(4, 1 + (t - 120) / 30);
				if (count(MaserCannon.class) < masers && (force || tick >= NEXT[MASER]) && spawnMaser(level, g)) {
					NEXT[MASER] = tick + 200;
					any = true;
				}
			}
			if (!any) {
				break;
			}
		}
	}

	private static void assignVoices(ServerPlayer g) {
		Vec3 gp = Mil.feet(g);
		for (Class<? extends MilitaryUnit> c : List.of(Tank.class, Helicopter.class)) {
			int max = c == Tank.class ? 4 : 3;
			List<MilitaryUnit> l = new ArrayList<>();
			for (MilitaryUnit u : MilitaryUnit.ALL) {
				if (c.isInstance(u)) {
					l.add(u);
				}
			}
			l.sort(Comparator.comparingDouble(u -> u.distanceToSqr(gp)));
			for (int i = 0; i < l.size(); i++) {
				l.get(i).voiced = i < max;
			}
		}
	}

	// ---- spawning ----------------------------------------------------------------------------------------------------------
	/** Spawns {@code n} of a type by name for the command (no caps). Returns how many were created. */
	static int spawn(String type, ServerLevel level, ServerPlayer g, int n) {
		int made = 0;
		for (int i = 0; i < n; i++) {
			boolean ok = switch (type) {
				case "tank" -> spawnTank(level, g);
				case "heli", "helicopter" -> spawnHeli(level, g);
				case "jet" -> {
					spawnJetPair(level, g);
					yield true;
				}
				case "ship" -> spawnShip(level, g);
				case "maser" -> spawnMaser(level, g);
				default -> false;
			};
			if (ok) {
				made++;
			}
		}
		return made;
	}

	private static boolean occupied(Class<? extends MilitaryUnit> c, Vec3 at, double radius) {
		for (MilitaryUnit u : MilitaryUnit.ALL) {
			if (c.isInstance(u) && u.position().distanceToSqr(at) < radius * radius) {
				return true;
			}
		}
		return false;
	}

	/** Picks a spawn point: free, at least 3 x scale from Godzilla, preferring about 7 x scale away. */
	private static BlockPos pickSpawn(List<BlockPos> list, ServerPlayer g, RandomSource r, Class<? extends MilitaryUnit> c, double prefer) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		float s = Mil.scaleOf(g);
		for (BlockPos p : list) {
			Vec3 v = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			if (occupied(c, v, 4.5)) {
				continue;
			}
			double d = Math.hypot(v.x - Mil.feet(g).x, v.z - Mil.feet(g).z);
			double score = Math.abs(d - prefer * s) + r.nextDouble() * 40 + (d < 3 * s ? 1000 : 0);
			if (score < bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	static boolean spawnTank(ServerLevel level, ServerPlayer g) {
		CityData d = Gz.city().data();
		BlockPos p = pickSpawn(d.tankSpawns(), g, level.getRandom(), Tank.class, 7.5);
		if (p == null) {
			return false;
		}
		Tank t = new Tank(MilEntities.TANK, level);
		t.place(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
		level.addFreshEntity(t);
		return true;
	}

	static boolean spawnMaser(ServerLevel level, ServerPlayer g) {
		CityData d = Gz.city().data();
		BlockPos p = pickSpawn(d.maserSpawns(), g, level.getRandom(), MaserCannon.class, 6);
		if (p == null) {
			return false;
		}
		MaserCannon m = new MaserCannon(MilEntities.MASER, level);
		m.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		m.setYRot(Mil.yawOf(Mil.feet(g).x - p.getX(), Mil.feet(g).z - p.getZ()));
		level.addFreshEntity(m);
		return true;
	}

	static boolean spawnShip(ServerLevel level, ServerPlayer g) {
		CityData d = Gz.city().data();
		List<CityData.ShipSpawn> list = d.shipSpawns();
		int n = list.size();
		if (n == 0) {
			return false;
		}
		int start = level.getRandom().nextInt(n);
		for (int k = 0; k < n; k++) {
			int i = (start + k) % n;
			CityData.ShipSpawn s = list.get(i);
			Vec3 v = new Vec3(s.pos().getX() + 0.5, 62.85, s.pos().getZ() + 0.5);
			if (occupied(Warship.class, v, 22)) {
				continue;
			}
			Warship w = new Warship(MilEntities.SHIP, level);
			w.setup(new int[]{1, 2, 0, 1, 0}[i % 5], s.yaw(), v.x, v.y, v.z);
			level.addFreshEntity(w);
			return true;
		}
		return false;
	}

	private static List<Vec3> lane(CityData d, int idx, boolean reverse) {
		List<Vec3> pts = new ArrayList<>();
		for (BlockPos p : d.airLanes().get(idx)) {
			pts.add(new Vec3(Mth.clamp(p.getX() + 0.5, -248, 248), p.getY(), Mth.clamp(p.getZ() + 0.5, -308, 308)));
		}
		if (reverse) {
			java.util.Collections.reverse(pts);
		}
		return pts;
	}

	static boolean spawnHeli(ServerLevel level, ServerPlayer g) {
		CityData d = Gz.city().data();
		if (d.airLanes().isEmpty()) {
			return false;
		}
		RandomSource r = level.getRandom();
		// prefer the lane end farthest from Godzilla so the helicopter flies in
		int idx = r.nextInt(d.airLanes().size());
		List<Vec3> route = lane(d, idx, r.nextBoolean());
		Helicopter h = new Helicopter(MilEntities.HELI, level);
		h.setPos(route.get(0));
		h.setRoute(route.subList(1, route.size()));
		level.addFreshEntity(h);
		return true;
	}

	static void spawnJetPair(ServerLevel level, ServerPlayer g) {
		CityData d = Gz.city().data();
		if (d.airLanes().isEmpty()) {
			return;
		}
		int idx = level.getRandom().nextInt(d.airLanes().size());
		boolean rev = level.getRandom().nextBoolean();
		spawnJet(level, g, idx, rev, 0.0);
		PENDING.add(new Pending(tick + 14, idx, rev));
	}

	private static void spawnJet(ServerLevel level, ServerPlayer g, int lane, boolean reverse, double lateral) {
		Jet j = new Jet(MilEntities.JET, level);
		j.plan(Gz.city().data(), g, level.getRandom(), lane, reverse, lateral);
		level.addFreshEntity(j);
	}

	// ---- helpers used by the units -----------------------------------------------------------------------------------------
	/** A landing point near Godzilla's feet for a shot that misses: the ground (or the sea) 0.7 to 2.2 x scale away. */
	static Vec3 missPoint(ServerLevel level, ServerPlayer g, RandomSource r) {
		float s = Mil.scaleOf(g);
		double a = r.nextDouble() * Math.PI * 2;
		double dist = s * (0.7 + 1.5 * r.nextDouble());
		double x = Mil.feet(g).x + Math.cos(a) * dist, z = Mil.feet(g).z + Math.sin(a) * dist;
		return new Vec3(x, Mil.groundY(level, x, z) + 0.2, z);
	}
}
