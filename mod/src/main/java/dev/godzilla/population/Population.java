package dev.godzilla.population;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.GodzillaSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The city's life: keeps the budget of civilians, cars and trains alive around Godzilla, steps all of them once per server
 * tick (people with a level of detail by distance), turns the game events (roar, smashed blocks, collapses, explosions) into
 * fear, and rate-limits the sounds. One instance per JVM; everything is dropped on {@link GzEvents#RESET} and at server stop.
 */
public final class Population {
	public static final Population I = new Population();

	// ---- tunables (see /godzilla pop budget)
	public int targetPeople = 450;
	public int maxPeople = 650;
	public int targetCars = 70;
	public int targetParked = 26;
	public int targetBigVehicles = 10;
	public int targetPolice = 6;
	public int targetTrains = 2;
	/** Godzilla scares people within this many blocks (horizontal) of his feet. */
	public double scareRadius = 55;

	// ---- per-tick state (read by the entities)
	long now;
	@Nullable ServerLevel level;
	@Nullable RoadNet roads;
	@Nullable Vec3 anchorPos;
	@Nullable AABB gzBox;
	@Nullable Vec3 gzPos;
	boolean alert;
	/** Debug: when set nobody moves (for screenshots). */
	public boolean frozen;
	final double[] tmp = new double[2];

	final List<PersonEntity> people = new ArrayList<>();
	final List<VehicleEntity> vehicles = new ArrayList<>();
	final List<Train> trains = new ArrayList<>();
	private final Map<Long, ArrayList<VehicleEntity>> grid = new HashMap<>();
	private final List<Threat> threats = new ArrayList<>();
	private final Map<Integer, Long> burstCooldown = new HashMap<>();
	private final Map<Long, Long> crowdLast = new HashMap<>();
	private final long[] trainRespawn = new long[8];
	private final RandomSource rnd = RandomSource.create();
	private @Nullable CityData roadsFor;
	private long activeSince = -1;
	private long nextSirenAt;
	private long nextRiseTrainCheck;

	// ---- stats
	long deaths, explosions, derailments, screams, honks, skids, crowdLoops, alarms, sirens;
	double msEma;
	double msMax;
	int nearPeople;
	private int prevTick = -1;

	// ---- rate limiters (tokens)
	private double screamTok = 4, squishTok = 6, honkTok = 3, particleTok = 60, alarmTok = 4, sirenTok = 3;

	private static final class Threat {
		double x, z, radius;
		long expires;
	}

	private Population() {
	}

	// ================================================================================================== wiring

	static void init() {
		GzEvents.RESET.register(g -> I.reset());
		GzEvents.PHASE_CHANGED.register(I::onPhase);
		GzEvents.ROAR.register(p -> I.addThreat(p.getX(), p.getZ(), 150, 220));
		GzEvents.BLOCKS_SMASHED.register(I::onSmashed);
		GzEvents.COLLAPSE.register(I::onCollapse);
		GzEvents.BUILDING_DESTROYED.register(e -> {
			if (I.level != null) {
				I.emerge(I.level, e.building(), 18);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(I::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> I.reset());
	}

	/** Drops every entity and all state of this module. */
	void reset() {
		for (PersonEntity e : people) {
			e.discard();
		}
		for (VehicleEntity e : vehicles) {
			e.discard();
		}
		people.clear();
		vehicles.clear();
		trains.clear();
		threats.clear();
		burstCooldown.clear();
		crowdLast.clear();
		grid.clear();
		java.util.Arrays.fill(trainRespawn, 0);
		activeSince = -1;
		deaths = explosions = derailments = screams = honks = skids = crowdLoops = alarms = sirens = 0;
		msEma = msMax = 0;
		// leftovers in the world (an entity added behind our back, or surviving a lost reference)
		try {
			ServerLevel lvl = Gz.game().level();
			if (lvl != null && lvl.getServer().isRunning()) {
				for (net.minecraft.world.entity.Entity e : lvl.getAllEntities()) {
					if (e instanceof PersonEntity || e instanceof VehicleEntity) {
						e.discard();
					}
				}
			}
		} catch (RuntimeException e) {
			Population.log("reset: could not sweep leftovers: " + e);
		}
	}

	boolean active() {
		Game.Phase ph = Gz.game().phase();
		return ph == Game.Phase.RISE || ph == Game.Phase.RAMPAGE || ph == Game.Phase.MOTHRA;
	}

	private void onPhase(GzEvents.PhaseChange c) {
		if (c.to() == Game.Phase.RISE || (c.from() == Game.Phase.IDLE && c.to().playing())) {
			activeSince = now;
		}
	}

	// ================================================================================================== events -> fear

	void addThreat(double x, double z, double radius, int ttl) {
		for (Threat t : threats) {
			if (Math.abs(t.x - x) < 8 && Math.abs(t.z - z) < 8) {
				t.radius = Math.max(t.radius, radius);
				t.expires = Math.max(t.expires, now + ttl);
				t.x = x;
				t.z = z;
				return;
			}
		}
		if (threats.size() >= 24) {
			threats.remove(0);
		}
		Threat t = new Threat();
		t.x = x;
		t.z = z;
		t.radius = radius;
		t.expires = now + ttl;
		threats.add(t);
	}

	/** Fills out[0..1] with the position to flee from and returns true when (x, z) is within reach of a threat. */
	boolean scaredAt(double x, double z, double[] out) {
		double best = Double.MAX_VALUE;
		for (int i = 0, n = threats.size(); i < n; i++) {
			Threat t = threats.get(i);
			double dx = x - t.x, dz = z - t.z;
			double d2 = dx * dx + dz * dz;
			if (d2 < t.radius * t.radius && d2 < best) {
				best = d2;
				out[0] = t.x;
				out[1] = t.z;
			}
		}
		if (gzPos != null) {
			double dx = x - gzPos.x, dz = z - gzPos.z;
			double d2 = dx * dx + dz * dz;
			if (d2 < scareRadius * scareRadius && d2 < best) {
				best = d2;
				out[0] = gzPos.x;
				out[1] = gzPos.z;
			}
		}
		return best != Double.MAX_VALUE;
	}

	private void onSmashed(GzEvents.BlocksSmashed e) {
		ServerLevel lvl = level;
		if (lvl == null || e.count() < 3) {
			return;
		}
		double r = 22 + Math.min(40, Math.sqrt(e.count()) * 3);
		addThreat(e.center().x, e.center().z, r, 100);
		if (e.count() >= 6) {
			alarmNear(e.center(), 28 + Math.sqrt(e.count()));
			CityData.Building b = buildingNear(e.center());
			if (b != null && !b.landmark() || b != null && e.count() > 30) {
				Long last = burstCooldown.get(b.id());
				if (last == null || now - last > 40) {
					burstCooldown.put(b.id(), now);
					emerge(lvl, b, 2 + (int) Math.min(10, Math.sqrt(e.count()) * 0.6));
				}
			}
		}
	}

	private void onCollapse(GzEvents.Collapse e) {
		ServerLevel lvl = level;
		if (lvl == null) {
			return;
		}
		addThreat(e.center().x, e.center().z, 70, 240);
		alarmNear(e.center(), 60);
		if (e.building() != null) {
			Long last = burstCooldown.get(e.building().id());
			if (last == null || now - last > 60) {
				burstCooldown.put(e.building().id(), now);
				emerge(lvl, e.building(), 8 + Math.min(16, e.blocks() / 150));
			}
		}
	}

	private CityData.@Nullable Building buildingNear(Vec3 c) {
		CityData.Building b = Gz.city().buildingAt((int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z));
		if (b != null) {
			return b;
		}
		CityData.Building best = null;
		double bd = 4 * 4;
		for (CityData.Building cand : Gz.city().data().buildings()) {
			double dx = Math.max(Math.max(cand.min().getX() - c.x, 0), c.x - (cand.max().getX() + 1));
			double dy = Math.max(Math.max(cand.min().getY() - c.y, 0), c.y - (cand.max().getY() + 1));
			double dz = Math.max(Math.max(cand.min().getZ() - c.z, 0), c.z - (cand.max().getZ() + 1));
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 < bd) {
				bd = d2;
				best = cand;
			}
		}
		return best;
	}

	/** People pour out of a damaged building and run. */
	void emerge(ServerLevel lvl, CityData.Building b, int n) {
		if (people.size() >= maxPeople + 120) {
			return;
		}
		int gy = Gz.city().data().groundY() + 1;
		double cx = (b.min().getX() + b.max().getX() + 1) / 2.0, cz = (b.min().getZ() + b.max().getZ() + 1) / 2.0;
		int spawned = 0;
		for (int tries = 0; tries < n * 4 && spawned < n; tries++) {
			double x, z;
			int side = rnd.nextInt(4);
			double ax = b.min().getX() + rnd.nextDouble() * (b.max().getX() - b.min().getX() + 1);
			double az = b.min().getZ() + rnd.nextDouble() * (b.max().getZ() - b.min().getZ() + 1);
			switch (side) {
				case 0 -> {
					x = ax;
					z = b.min().getZ() - 0.5 - rnd.nextDouble();
				}
				case 1 -> {
					x = ax;
					z = b.max().getZ() + 1.5 + rnd.nextDouble();
				}
				case 2 -> {
					x = b.min().getX() - 0.5 - rnd.nextDouble();
					z = az;
				}
				default -> {
					x = b.max().getX() + 1.5 + rnd.nextDouble();
					z = az;
				}
			}
			if (Terrain.stand(lvl, x, z, gy) != gy) {
				continue;
			}
			PersonEntity pe = spawnPerson(lvl, x, gy, z);
			if (pe != null) {
				pe.panicFrom(this, cx, cz, 160);
				spawned++;
			}
		}
	}

	/** Cars with an alarm: parked cars near an impact start theirs. */
	void alarmNear(Vec3 c, double radius) {
		double r2 = radius * radius;
		for (int i = 0, n = vehicles.size(); i < n; i++) {
			VehicleEntity v = vehicles.get(i);
			if (v.mode == VehicleEntity.Mode.PARKED && v.distanceToSqr(c.x, v.getY(), c.z) < r2) {
				v.alarmUntil = Math.max(v.alarmUntil, now + 160 + rnd.nextInt(140));
			}
		}
	}

	void onExplosion(ServerLevel lvl, Vec3 c, VehicleEntity source, double radius) {
		addThreat(c.x, c.z, 35, 120);
		alarmNear(c, 32);
		double r2 = (radius + 1.6) * (radius + 1.6);
		for (int i = 0, n = vehicles.size(); i < n; i++) {
			VehicleEntity v = vehicles.get(i);
			if (v == source || v.isRemoved() || v.distanceToSqr(c.x, c.y, c.z) > r2) {
				continue;
			}
			if (v.mode == VehicleEntity.Mode.TRAIN) {
				if (v.train != null) {
					v.train.derail(this, lvl, dev.godzilla.api.Cause.EXPLOSION, Vec3.ZERO);
				}
			} else if (v.chainAt == 0) {
				v.chainAt = now + 4 + rnd.nextInt(8);
			}
		}
	}

	// ================================================================================================== sounds

	private boolean near(double x, double z, double maxDist) {
		Vec3 a = anchorPos;
		return a == null || (x - a.x) * (x - a.x) + (z - a.z) * (z - a.z) < maxDist * maxDist;
	}

	void screamMaybe(ServerLevel lvl, PersonEntity pe) {
		if (screamTok < 1 || !near(pe.getX(), pe.getZ(), 150)) {
			return;
		}
		screamTok -= 1;
		screams++;
		lvl.playSound(null, pe.getX(), pe.getY() + 0.4, pe.getZ(), GodzillaSounds.PEOPLE_SCREAM, SoundSource.NEUTRAL,
				2.6F, 0.8F + rnd.nextFloat() * 0.85F);
	}

	void squish(ServerLevel lvl, double x, double y, double z) {
		if (squishTok < 1 || !near(x, z, 150)) {
			return;
		}
		squishTok -= 1;
		lvl.playSound(null, x, y, z, GodzillaSounds.PEOPLE_SQUISH, SoundSource.NEUTRAL, 2.2F, 0.85F + rnd.nextFloat() * 0.5F);
	}

	void honk(ServerLevel lvl, VehicleEntity v) {
		if (honkTok < 1 || !near(v.getX(), v.getZ(), 200)) {
			return;
		}
		honkTok -= 1;
		honks++;
		lvl.playSound(null, v.getX(), v.getY() + 0.5, v.getZ(), GodzillaSounds.CAR_HORN, SoundSource.NEUTRAL, 3.2F,
				v.style() == VehicleEntity.STYLE_BUS || v.style() == VehicleEntity.STYLE_TRUCK ? 0.7F : 0.9F + rnd.nextFloat() * 0.4F);
	}

	void skid(ServerLevel lvl, VehicleEntity v) {
		if (honkTok < 1 || !near(v.getX(), v.getZ(), 200)) {
			return;
		}
		honkTok -= 1;
		skids++;
		lvl.playSound(null, v.getX(), v.getY() + 0.3, v.getZ(), GodzillaSounds.CAR_SKID, SoundSource.NEUTRAL, 3.0F, 0.9F + rnd.nextFloat() * 0.3F);
	}

	boolean takeParticleBudget() {
		if (particleTok < 1) {
			return false;
		}
		particleTok -= 1;
		return true;
	}

	boolean takeAlarmSlot(VehicleEntity v) {
		if (alarmTok < 1 || !near(v.getX(), v.getZ(), 220)) {
			return false;
		}
		alarmTok -= 1;
		alarms++;
		return true;
	}

	boolean takeSirenSlot(VehicleEntity v) {
		if (sirenTok < 1 || !near(v.getX(), v.getZ(), 220)) {
			return false;
		}
		sirenTok -= 1;
		sirens++;
		return true;
	}

	// ================================================================================================== vehicle queries

	private static long cell(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xffffffffL);
	}

	private void buildGrid() {
		grid.clear();
		for (int i = 0, n = vehicles.size(); i < n; i++) {
			VehicleEntity v = vehicles.get(i);
			if (v.mode == VehicleEntity.Mode.FLY) {
				continue;
			}
			grid.computeIfAbsent(cell(Mth.floor(v.getX() / 8), Mth.floor(v.getZ() / 8)), k -> new ArrayList<>(4)).add(v);
		}
	}

	/** Free distance (between the car bodies) to the nearest vehicle in front, or a large number. */
	double gapAhead(VehicleEntity v, double fx, double fz, double maxDist, boolean yieldToCross) {
		double best = Double.MAX_VALUE;
		int x0 = Mth.floor((v.getX() - maxDist) / 8), x1 = Mth.floor((v.getX() + maxDist) / 8);
		int z0 = Mth.floor((v.getZ() - maxDist) / 8), z1 = Mth.floor((v.getZ() + maxDist) / 8);
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				List<VehicleEntity> l = grid.get(cell(cx, cz));
				if (l == null) {
					continue;
				}
				for (int i = 0, n = l.size(); i < n; i++) {
					VehicleEntity o = l.get(i);
					if (o == v || o.mode == VehicleEntity.Mode.TRAIN) {
						continue;
					}
					double rx = o.getX() - v.getX(), rz = o.getZ() - v.getZ();
					double fwd = rx * fx + rz * fz;
					if (fwd < 0.2 || fwd > maxDist + 2) {
						continue;
					}
					double lat = Math.abs(rx * fz - rz * fx);
					double limit = o.mode == VehicleEntity.Mode.PARKED ? 0.75 : 0.5 + (v.bodyLength() + o.bodyLength()) * 0.12;
					if (lat > limit) {
						continue;
					}
					if (o.mode == VehicleEntity.Mode.DRIVE) {
						double yaw = o.getYRot() * Mth.DEG_TO_RAD;
						double cosang = fx * -Math.sin(yaw) + fz * Math.cos(yaw);
						if (cosang < -0.5 && lat > 0.4) {
							continue; // oncoming traffic in the other lane
						}
						if (cosang >= -0.5 && cosang < 0.5) { // crossing traffic: the lower entity id has priority
							if (!yieldToCross || v.getId() < o.getId()) {
								continue;
							}
						}
					}
					double gap = fwd - (v.bodyLength() + o.bodyLength()) * 0.5;
					if (gap < best) {
						best = gap;
					}
				}
			}
		}
		return best;
	}

	/** Distance along the heading until Godzilla's (inflated) footprint, or Double.MAX_VALUE. */
	double godzillaAhead(VehicleEntity v, double fx, double fz, double maxDist) {
		AABB b = gzBox;
		if (b == null || v.getY() > b.maxY || v.getY() < b.minY - 3) {
			return Double.MAX_VALUE;
		}
		for (double d = 0.5; d <= maxDist; d += 0.6) {
			double px = v.getX() + fx * d, pz = v.getZ() + fz * d;
			if (px >= b.minX && px <= b.maxX && pz >= b.minZ && pz <= b.maxZ) {
				return d;
			}
		}
		return Double.MAX_VALUE;
	}

	// ================================================================================================== spawning

	@Nullable PersonEntity spawnPerson(ServerLevel lvl, double x, double y, double z) {
		PersonEntity e = new PersonEntity(PopulationEntities.PERSON, lvl);
		e.setPos(x, y, z);
		e.init(rnd);
		if (!lvl.addFreshEntity(e)) {
			return null;
		}
		people.add(e);
		return e;
	}

	/** kind: "car", "taxi", "police", "bus", "truck". Drives from d blocks along the road edge a->b. */
	@Nullable VehicleEntity spawnVehicle(ServerLevel lvl, String kind, int a, int b, double d, boolean parked) {
		RoadNet r = roads;
		if (r == null || r.empty()) {
			return null;
		}
		boolean big = kind.equals("bus") || kind.equals("truck");
		VehicleEntity v = new VehicleEntity(big ? PopulationEntities.BUS : PopulationEntities.CAR, lvl);
		switch (kind) {
			case "taxi" -> v.setLook(VehicleEntity.STYLE_TAXI, 0);
			case "police" -> v.setLook(VehicleEntity.STYLE_POLICE, 0);
			case "bus" -> v.setLook(VehicleEntity.STYLE_BUS, rnd.nextInt(VehicleEntity.BUS_COLORS.length));
			case "truck" -> v.setLook(VehicleEntity.STYLE_TRUCK, rnd.nextInt(VehicleEntity.TRUCK_COLORS.length));
			default -> v.setLook(VehicleEntity.STYLE_CAR, rnd.nextInt(VehicleEntity.CAR_COLORS.length));
		}
		v.cruise = (big ? 0.17 : 0.2) + rnd.nextDouble() * 0.07;
		v.placeOnRoad(r, a, b, d);
		if (parked) {
			double len = Math.max(0.1, r.length(a, b));
			double dx = (r.x[b] - r.x[a]) / len, dz = (r.z[b] - r.z[a]) / len;
			double w = r.width(a, b);
			double side = rnd.nextBoolean() ? 1 : -1;
			double off = w / 2.0 - (big ? 1.3 : 0.55);
			v.setPos(r.x[a] + dx * d + dz * off * side, r.y[a], r.z[a] + dz * d - dx * off * side);
			v.setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) + (rnd.nextBoolean() ? 0 : 180));
			v.mode = VehicleEntity.Mode.PARKED;
			v.speed = 0;
		}
		if (!lvl.addFreshEntity(v)) {
			return null;
		}
		vehicles.add(v);
		return v;
	}

	/** Random point on a random road edge between minD and maxD from the anchor; returns {edge, d} or null. */
	private double @Nullable [] randomRoadSpot(double minD, double maxD, double margin) {
		RoadNet r = roads;
		if (r == null || r.empty()) {
			return null;
		}
		for (int t = 0; t < 10; t++) {
			int e = rnd.nextInt(r.ea.length);
			int a = r.ea[e], b = r.eb[e];
			double len = r.length(a, b);
			if (len < 2 * margin + 2) {
				continue;
			}
			double d = margin + rnd.nextDouble() * (len - 2 * margin);
			double px = r.x[a] + (r.x[b] - r.x[a]) * d / len, pz = r.z[a] + (r.z[b] - r.z[a]) * d / len;
			if (anchorPos != null) {
				double dist = Math.hypot(px - anchorPos.x, pz - anchorPos.z);
				if (dist < minD || dist > maxD) {
					continue;
				}
			}
			return new double[]{e, d, rnd.nextBoolean() ? 1 : 0};
		}
		return null;
	}

	private boolean roadSpotFree(double px, double pz) {
		List<VehicleEntity> l = grid.get(cell(Mth.floor(px / 8), Mth.floor(pz / 8)));
		if (l != null) {
			for (VehicleEntity o : l) {
				if (Math.abs(o.getX() - px) < 4 && Math.abs(o.getZ() - pz) < 4) {
					return false;
				}
			}
		}
		return true;
	}

	boolean spawnVehicleAtRandomSpot(ServerLevel lvl, String kind, boolean parked, double minD, double maxD) {
		RoadNet r = roads;
		double[] spot = randomRoadSpot(minD, maxD, parked ? 10 : 4);
		if (r == null || spot == null) {
			return false;
		}
		int e = (int) spot[0];
		boolean flip = spot[2] > 0;
		int a = flip ? r.eb[e] : r.ea[e], b = flip ? r.ea[e] : r.eb[e];
		double len = r.length(a, b);
		double px = r.x[a] + (r.x[b] - r.x[a]) * spot[1] / len, pz = r.z[a] + (r.z[b] - r.z[a]) * spot[1] / len;
		if (!roadSpotFree(px, pz)) {
			return false;
		}
		VehicleEntity v = spawnVehicle(lvl, kind, a, b, spot[1], parked);
		if (v != null) {
			grid.computeIfAbsent(cell(Mth.floor(v.getX() / 8), Mth.floor(v.getZ() / 8)), k -> new ArrayList<>(4)).add(v);
		}
		return v != null;
	}

	/** Spawns a train on rail line {@code line} (0-based), at a fraction of its length. */
	@Nullable Train spawnTrain(ServerLevel lvl, int line, double frac) {
		List<List<BlockPos>> rails = Gz.city().data().rails();
		if (line >= rails.size() || rails.get(line).size() < 2) {
			return null;
		}
		List<Vec3> pts = new ArrayList<>();
		for (BlockPos bp : rails.get(line)) {
			pts.add(new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5));
		}
		Train t = new Train(pts, 5, frac, rnd.nextInt(VehicleEntity.TRAIN_COLORS.length), lvl, this);
		trains.add(t);
		vehicles.addAll(t.cars);
		return t;
	}

	// ================================================================================================== the tick

	private void tick(MinecraftServer server) {
		long t0 = System.nanoTime();
		now = server.getTickCount();
		ServerLevel lvl = Gz.game().level();
		level = lvl;
		if (lvl == null) {
			return;
		}
		boolean active = active();
		Game.Phase ph = Gz.game().phase();
		boolean wantAlert = ph == Game.Phase.RAMPAGE || ph == Game.Phase.MOTHRA;
		if (wantAlert && !alert) {
			nextSirenAt = now; // the city siren starts when the rampage begins
		}
		alert = wantAlert;
		if (!active && people.isEmpty() && vehicles.isEmpty()) {
			return;
		}
		Terrain.ensure();
		ensureRoads();
		refreshAnchor(server, lvl);
		// token buckets
		screamTok = Math.min(5, screamTok + 0.32);
		squishTok = Math.min(8, squishTok + 0.5);
		honkTok = Math.min(3, honkTok + 0.1);
		particleTok = Math.min(80, particleTok + 40);
		alarmTok = Math.min(4, alarmTok + 4.0 / 80);
		sirenTok = Math.min(3, sirenTok + 3.0 / 64);

		for (Iterator<Threat> it = threats.iterator(); it.hasNext(); ) {
			if (it.next().expires < now) {
				it.remove();
			}
		}
		people.removeIf(e -> e.isRemoved());
		vehicles.removeIf(e -> e.isRemoved());
		trains.removeIf(t -> t.finished());

		if (active) {
			maintain(lvl);
		}
		buildGrid();
		if (!frozen) {
			stepPeople(lvl);
			for (int i = 0; i < vehicles.size(); i++) {
				VehicleEntity v = vehicles.get(i);
				if (!v.isRemoved()) {
					v.simulate(this, lvl);
				}
			}
			for (int i = 0; i < trains.size(); i++) {
				trains.get(i).tick(this, lvl);
			}
		}
		ambience(lvl);

		double ms = (System.nanoTime() - t0) / 1e6;
		msEma += (ms - msEma) * 0.05;
		msMax = Math.max(msMax * 0.995, ms);
	}

	private void ensureRoads() {
		CityData d = Gz.city().data();
		if (d != roadsFor) {
			roadsFor = d;
			roads = RoadNet.of(d);
		}
	}

	private void refreshAnchor(MinecraftServer server, ServerLevel lvl) {
		ServerPlayer gz = Gz.game().godzilla();
		ServerPlayer anchor = gz;
		if (anchor == null && !lvl.players().isEmpty()) {
			anchor = lvl.players().get(0);
		}
		anchorPos = anchor == null ? null : anchor.position();
		if (gz != null && gz.level() == lvl && Gz.game().phase() != Game.Phase.IDLE) {
			gzPos = gz.position();
			gzBox = gz.getBoundingBox().inflate(1.6, 0, 1.6);
		} else {
			gzPos = null;
			gzBox = null;
		}
	}

	private void stepPeople(ServerLevel lvl) {
		Vec3 a = anchorPos;
		int near = 0;
		for (int i = 0, n = people.size(); i < n; i++) {
			PersonEntity p = people.get(i);
			if (p.isRemoved()) {
				continue;
			}
			int dt = 1;
			if (a != null) {
				double dx = p.getX() - a.x, dz = p.getZ() - a.z;
				double d2 = dx * dx + dz * dz;
				if (d2 < 160 * 160) {
					near++;
				}
				if (d2 > 330 * 330 && people.size() > targetPeople * 0.85 && now % 4 == 0) {
					p.discard();
					continue;
				}
				if (d2 > 260 * 260) {
					dt = 4;
				} else if (d2 > 140 * 140) {
					dt = 2;
				}
			}
			if (dt > 1 && ((now + p.getId()) % dt) != 0) {
				continue;
			}
			p.simulate(this, lvl, dt);
		}
		nearPeople = near;
	}

	/** Keeps the budgets: spawns what is missing around the anchor, removes what is far away. */
	private void maintain(ServerLevel lvl) {
		Vec3 a = anchorPos;
		if (a == null) {
			return;
		}
		// ---- people
		boolean surge = activeSince >= 0 && now - activeSince < 120;
		int quota = surge ? 20 : 8;
		List<BlockPos> spawns = Gz.city().data().sidewalkSpawns();
		if (!spawns.isEmpty() && nearPeople < targetPeople && people.size() < maxPeople) {
			for (int tries = 0; tries < 60 && quota > 0; tries++) {
				BlockPos sp = spawns.get(rnd.nextInt(spawns.size()));
				double dx = sp.getX() + 0.5 - a.x, dz = sp.getZ() + 0.5 - a.z;
				double d2 = dx * dx + dz * dz;
				if (d2 < 32 * 32 || d2 > 160 * 160) {
					continue; // never right at Godzilla's feet, and not beyond the action
				}
				double x = sp.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.8, z = sp.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.8;
				if (Terrain.stand(lvl, x, z, sp.getY()) != sp.getY()) {
					continue;
				}
				if (spawnPerson(lvl, x, sp.getY(), z) != null) {
					quota--;
					nearPeople++;
				}
			}
		}
		// ---- vehicles
		int cars = 0, parked = 0, big = 0, police = 0;
		for (int i = 0, n = vehicles.size(); i < n; i++) {
			VehicleEntity v = vehicles.get(i);
			if (v.isRemoved() || v.mode == VehicleEntity.Mode.TRAIN) {
				continue;
			}
			double dx = v.getX() - a.x, dz = v.getZ() - a.z;
			boolean far = dx * dx + dz * dz > 320 * 320;
			if (far && v.mode != VehicleEntity.Mode.FLY && now % 8 == 0) {
				v.discard();
				continue;
			}
			switch (v.style()) {
				case VehicleEntity.STYLE_BUS, VehicleEntity.STYLE_TRUCK -> big++;
				case VehicleEntity.STYLE_POLICE -> police++;
				default -> {
					if (v.mode == VehicleEntity.Mode.PARKED) {
						parked++;
					} else {
						cars++;
					}
				}
			}
		}
		int vq = surge ? 12 : 2;
		for (int tries = 0; tries < 8 && vq > 0; tries++) {
			boolean ok;
			if (cars < targetCars) {
				ok = spawnVehicleAtRandomSpot(lvl, rnd.nextInt(8) == 0 ? "taxi" : "car", false, 30, 230);
				cars += ok ? 1 : 0;
			} else if (parked < targetParked) {
				ok = spawnVehicleAtRandomSpot(lvl, "car", true, 25, 230);
				parked += ok ? 1 : 0;
			} else if (big < targetBigVehicles) {
				ok = spawnVehicleAtRandomSpot(lvl, rnd.nextInt(3) == 0 ? "truck" : "bus", false, 30, 230);
				big += ok ? 1 : 0;
			} else if (police < targetPolice) {
				ok = spawnVehicleAtRandomSpot(lvl, "police", false, 30, 230);
				police += ok ? 1 : 0;
			} else {
				break;
			}
			if (ok) {
				vq--;
			}
		}
		// ---- trains: one per rail line up to the budget, replaced some time after a wreck
		int lines = Math.min(targetTrains, Math.min(trainRespawn.length, Gz.city().data().rails().size()));
		if (now >= nextRiseTrainCheck) {
			nextRiseTrainCheck = now + 20;
			for (int line = 0; line < lines; line++) {
				boolean has = false;
				for (Train t : trains) {
					if (t.line == line) {
						has = true;
					}
				}
				if (!has && now >= trainRespawn[line]) {
					Train t = spawnTrain(lvl, line, rnd.nextDouble() * 0.8 + 0.1);
					if (t != null) {
						t.line = line;
						trainRespawn[line] = now + 1200;
					}
				}
			}
		}
	}

	/** Siren over the city, the panicking crowd, and other loops: replayed on their exact length. */
	private void ambience(ServerLevel lvl) {
		Vec3 a = anchorPos;
		if (a == null) {
			return;
		}
		if (alert && now >= nextSirenAt) {
			nextSirenAt = now + 200;
			lvl.playSound(null, a.x, a.y + 4, a.z, GodzillaSounds.SIREN_CITY_LOOP, SoundSource.AMBIENT, 4.0F, 1.0F);
		}
		if (now % 20 != 0) {
			return;
		}
		// dense crowds that are running: a loop of the crowd noise at the densest spots
		Map<Long, int[]> counts = new HashMap<>();
		for (int i = 0, n = people.size(); i < n; i++) {
			PersonEntity p = people.get(i);
			if (p.isRemoved() || !p.panicking()) {
				continue;
			}
			int[] c = counts.computeIfAbsent(cell(Mth.floor(p.getX() / 12), Mth.floor(p.getZ() / 12)), k -> new int[3]);
			c[0]++;
			c[1] += (int) (p.getX() * 8);
			c[2] += (int) (p.getZ() * 8);
		}
		int played = 0;
		for (Map.Entry<Long, int[]> e : counts.entrySet()) {
			int[] c = e.getValue();
			if (c[0] < 10 || played >= 2) {
				continue;
			}
			double x = c[1] / 8.0 / c[0], z = c[2] / 8.0 / c[0];
			if (!near(x, z, 150)) {
				continue;
			}
			Long last = crowdLast.get(e.getKey());
			if (last == null || now - last >= 118) {
				crowdLast.put(e.getKey(), now);
				crowdLoops++;
				lvl.playSound(null, x, Gz.city().data().groundY() + 1.5, z, GodzillaSounds.PEOPLE_CROWD_PANIC_LOOP, SoundSource.NEUTRAL,
						Math.min(4.0F, 2.0F + c[0] / 20.0F), 1.0F);
			}
			played++;
		}
		crowdLast.values().removeIf(t -> now - t > 400);
	}

	// ================================================================================================== commands support

	String stats(MinecraftServer server) {
		int panic = 0, flung = 0;
		for (PersonEntity p : people) {
			if (p.panicking()) {
				panic++;
			}
			if (p.flung()) {
				flung++;
			}
		}
		int driving = 0, parked = 0, flying = 0, alarm = 0, trainCars = 0, stopped = 0;
		for (VehicleEntity v : vehicles) {
			switch (v.mode) {
				case DRIVE -> {
					driving++;
					if (v.speed < 0.02) {
						stopped++;
					}
				}
				case PARKED -> {
					parked++;
					if ((v.flags() & VehicleEntity.FLAG_ALARM) != 0) {
						alarm++;
					}
				}
				case FLY -> flying++;
				case TRAIN -> trainCars++;
			}
		}
		return "people %d (near %d, panicking %d, flung %d) | vehicles driving %d (stopped %d), parked %d (alarm %d), flying %d | trains %d (%d cars) | threats %d | deaths %d, explosions %d, derailments %d | sounds: screams %d, honks %d, skids %d, alarms %d, police sirens %d, crowd loops %d | population %.3f ms/tick avg, %.2f max | server %.2f ms/tick | phase %s"
				.formatted(people.size(), nearPeople, panic, flung, driving, stopped, parked, alarm, flying, trains.size(), trainCars, threats.size(),
						deaths, explosions, derailments, screams, honks, skids, alarms, sirens, crowdLoops, msEma, msMax, server.getAverageTickTimeNanos() / 1e6, Gz.game().phase());
	}

	void panicAll(double x, double z, double radius, int ttl) {
		addThreat(x, z, radius, ttl);
	}

	void clearAll() {
		reset();
	}

	void calm() {
		threats.clear();
	}

	/** Sets up the per-tick context for commands that run while the tick is idle (phase IDLE, nothing alive yet). */
	void prepare(ServerLevel lvl, ServerPlayer player) {
		level = lvl;
		now = lvl.getServer().getTickCount();
		ensureRoads();
		anchorPos = player.position();
		buildGrid();
	}

	RandomSource random() {
		return rnd;
	}

	int threatCount() {
		return threats.size();
	}

	static void log(String s) {
		GodzillaMod.LOGGER.info("godzilla population: {}", s);
	}
}

