package dev.godzilla.population;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cars, taxis, police cars, buses, trucks and train carriages. Driving cars follow the road graph in the left-hand lane with
 * a pure-pursuit steer; they queue behind each other, stop and honk when Godzilla stands in the way, flee when panicked and
 * blow up when smashed (a hand swipe bats them through the air first). {@link Population} steps {@link #simulate}.
 */
public class VehicleEntity extends Entity implements Smashable {
	private static final EntityDataAccessor<Byte> DATA_STYLE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_COLOR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);

	public static final int STYLE_CAR = 0, STYLE_TAXI = 1, STYLE_POLICE = 2, STYLE_BUS = 3, STYLE_TRUCK = 4, STYLE_TRAIN = 5;
	/** Names of the colour variants per style, matching tools/population/make_textures.py. */
	public static final String[] CAR_COLORS = {"red", "blue", "green", "orange", "white", "purple", "cyan", "pink", "lime"};
	public static final String[] BUS_COLORS = {"red", "green", "blue", "orange"};
	public static final String[] TRUCK_COLORS = {"red", "blue", "green", "orange"};
	public static final String[] TRAIN_COLORS = {"green", "orange", "blue"};

	public static final int FLAG_TUMBLE = 1, FLAG_ALARM = 2, FLAG_SIREN = 4, FLAG_BRAKE = 8;

	enum Mode {DRIVE, PARKED, FLY, TRAIN}

	Mode mode = Mode.DRIVE;
	Train train; // when mode == TRAIN
	// ---- driving state (server)
	int ea, eb, en = -1;
	double s;
	double speed;
	double cruise = 0.22;
	private long panicUntil;
	private double fleeX, fleeZ;
	private int blocked;
	private int ignoreCross;
	private int uturnCooldown;
	private long nextHonk;
	private long nextSiren;
	private long nextAlarm;
	long alarmUntil;
	long chainAt;
	private Vec3 vel = Vec3.ZERO;
	private int flyTicks;
	private double spin;
	long launchAt;
	Vec3 launchVel = Vec3.ZERO;
	private boolean dead;
	private boolean wasGzBlocked;

	public VehicleEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STYLE, (byte) 0);
		builder.define(DATA_COLOR, (byte) 0);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, getType() == PopulationEntities.TRAIN_CAR ? 1 : 2);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 260.0 * 260.0;
	}

	public int style() {
		return this.entityData.get(DATA_STYLE);
	}

	public int color() {
		return this.entityData.get(DATA_COLOR);
	}

	public int flags() {
		return this.entityData.get(DATA_FLAGS);
	}

	void setFlag(int flag, boolean on) {
		int f = this.entityData.get(DATA_FLAGS);
		int n = on ? f | flag : f & ~flag;
		if (n != f) {
			this.entityData.set(DATA_FLAGS, (byte) n);
		}
	}

	void setLook(int style, int color) {
		this.entityData.set(DATA_STYLE, (byte) style);
		this.entityData.set(DATA_COLOR, (byte) color);
	}

	double bodyLength() {
		return switch (style()) {
			case STYLE_BUS -> 2.5;
			case STYLE_TRUCK -> 2.25;
			case STYLE_TRAIN -> 3.5;
			default -> 1.5;
		};
	}

	@Override
	public void tick() {
		this.commonTick();
	}

	// ================================================================================================== simulation

	void simulate(Population p, ServerLevel level) {
		if (dead) {
			return;
		}
		if (chainAt != 0 && p.now >= chainAt) {
			explode(p, level);
			return;
		}
		switch (mode) {
			case DRIVE -> drive(p, level);
			case PARKED -> parked(p, level);
			case FLY -> fly(p, level);
			case TRAIN -> {
				if (launchAt != 0 && p.now >= launchAt) {
					launchAt = 0;
					launch(launchVel);
				}
			}
		}
	}

	private void parked(Population p, ServerLevel level) {
		boolean alarm = p.now < alarmUntil;
		if (alarm != ((flags() & FLAG_ALARM) != 0)) {
			setFlag(FLAG_ALARM, alarm);
		}
		if (alarm && p.now >= nextAlarm && p.takeAlarmSlot(this)) {
			nextAlarm = p.now + 80;
			level.playSound(null, getX(), getY() + 0.5, getZ(), GodzillaSounds.CAR_ALARM, SoundSource.NEUTRAL, 3.0F, 1.0F);
		}
	}

	private void drive(Population p, ServerLevel level) {
		RoadNet r = p.roads;
		if (r == null || ea >= r.n || eb >= r.n) {
			return;
		}
		long now = p.now;
		RandomSource rnd = this.random;
		double ax = r.x[ea], az = r.z[ea], bx = r.x[eb], bz = r.z[eb];
		double len = Math.max(0.1, Math.hypot(bx - ax, bz - az));
		double dx = (bx - ax) / len, dz = (bz - az) / len;
		double lane = RoadNet.laneOffset(r.width(ea, eb));
		double nx = dz, nz = -dx; // left of the direction of travel: Japan drives on the left

		// fear
		if (((now + getId()) % 6) == 0) {
			double[] out = p.tmp;
			if (p.scaredAt(getX(), getZ(), out)) {
				panicUntil = now + 100;
				fleeX = out[0];
				fleeZ = out[1];
			}
		}
		boolean panic = now < panicUntil;
		double target = cruise * (panic ? 1.9 : 1.0);

		// where to steer
		double look = 2.4 + speed * 7;
		double sT = s + look;
		double tx, tz;
		if (sT <= len) {
			tx = ax + dx * sT + nx * lane;
			tz = az + dz * sT + nz * lane;
		} else {
			if (en < 0) {
				en = r.pickNext(ea, eb, rnd, panic, fleeX, fleeZ);
			}
			double l2 = Math.max(0.1, r.length(eb, en));
			double d2x = (r.x[en] - bx) / l2, d2z = (r.z[en] - bz) / l2;
			double lane2 = RoadNet.laneOffset(r.width(eb, en));
			double rem = Math.min(sT - len, l2);
			tx = bx + d2x * rem + d2z * lane2;
			tz = bz + d2z * rem - d2x * lane2;
		}
		float want = (float) (Mth.atan2(-(tx - getX()), tz - getZ()) * Mth.RAD_TO_DEG);
		float yaw = Mth.approachDegrees(getYRot(), want, (float) (5 + speed * 45));
		setYRot(yaw);
		double fx = -Math.sin(yaw * Mth.DEG_TO_RAD), fz = Math.cos(yaw * Mth.DEG_TO_RAD);

		// obstacles: other vehicles, Godzilla, rubble
		double stopLook = 3.0 + speed * 16;
		double allowed = target;
		double gap = p.gapAhead(this, fx, fz, stopLook, ignoreCross <= 0);
		boolean hardBrake = false;
		if (gap < stopLook) {
			allowed = Math.min(allowed, Math.max(0, gap - 0.35) * 0.14);
		}
		double gz = p.godzillaAhead(this, fx, fz, stopLook + 1.5);
		boolean gzBlocked = gz < Double.MAX_VALUE;
		if (gzBlocked) {
			allowed = Math.min(allowed, Math.max(0, gz - 1.5) * 0.12);
			if (!wasGzBlocked && speed > 0.15) {
				p.skid(level, this);
				hardBrake = true;
			}
			if (now >= nextHonk) {
				nextHonk = now + 25 + rnd.nextInt(30);
				p.honk(level, this);
			}
		}
		wasGzBlocked = gzBlocked;
		if (Terrain.solidAt(level, getX() + fx * 1.5, getY() + 0.4, getZ() + fz * 1.5)) {
			allowed = 0;
		}
		if (gap < 1.2 && panic && now >= nextHonk) {
			nextHonk = now + 30 + rnd.nextInt(30);
			p.honk(level, this);
		}
		if (allowed < 0.02 && speed < 0.03) {
			blocked++;
		} else {
			blocked = Math.max(0, blocked - 2);
		}
		if (ignoreCross > 0) {
			ignoreCross--;
		}
		if (uturnCooldown > 0) {
			uturnCooldown--;
		}
		if (blocked > 90 && gap >= 1.5 * stopLook) {
			// nothing but a wall or rubble in the way: turn round
			uturn(r);
			blocked = 0;
		} else if (blocked > 120 && gzBlocked && uturnCooldown <= 0) {
			uturn(r);
			blocked = 0;
		} else if (blocked > 70 && gap < stopLook && !gzBlocked) {
			ignoreCross = 40; // break a crossing deadlock
			blocked = 40;
		}
		if (allowed < speed) {
			speed = Math.max(allowed, speed - (hardBrake ? 0.12 : 0.05));
		} else {
			speed = Math.min(allowed, speed + 0.012);
		}
		boolean braking = allowed < target - 0.03 && speed > 0.01 || speed < 0.01;
		if (braking != ((flags() & FLAG_BRAKE) != 0)) {
			setFlag(FLAG_BRAKE, braking);
		}

		setPos(getX() + fx * speed, r.y[ea], getZ() + fz * speed);

		// along-edge progress, edge change at the node
		s = (getX() - ax) * dx + (getZ() - az) * dz;
		if (s > len) {
			if (en < 0) {
				en = r.pickNext(ea, eb, rnd, panic, fleeX, fleeZ);
			}
			ea = eb;
			eb = en;
			en = -1;
			double nax = r.x[ea], naz = r.z[ea];
			double nl = Math.max(0.1, r.length(ea, eb));
			s = (getX() - nax) * (r.x[eb] - nax) / nl + (getZ() - naz) * (r.z[eb] - naz) / nl;
		}

		if (style() == STYLE_POLICE) {
			boolean siren = p.alert;
			if (siren != ((flags() & FLAG_SIREN) != 0)) {
				setFlag(FLAG_SIREN, siren);
			}
			if (siren && now >= nextSiren && p.takeSirenSlot(this)) {
				nextSiren = now + 64;
				level.playSound(null, getX(), getY() + 0.5, getZ(), GodzillaSounds.SIREN_POLICE, SoundSource.NEUTRAL, 3.0F,
						0.95F + rnd.nextFloat() * 0.1F);
			}
		}
	}

	private void uturn(RoadNet r) {
		int t = ea;
		ea = eb;
		eb = t;
		en = -1;
		double len = Math.max(0.1, r.length(ea, eb));
		s = len - s;
		uturnCooldown = 120;
		speed = 0;
	}

	/** Places the car on a road edge, in the lane, at distance d from the start node. */
	void placeOnRoad(RoadNet r, int a, int b, double d) {
		ea = a;
		eb = b;
		en = -1;
		s = d;
		double len = Math.max(0.1, r.length(a, b));
		double dx = (r.x[b] - r.x[a]) / len, dz = (r.z[b] - r.z[a]) / len;
		double lane = RoadNet.laneOffset(r.width(a, b));
		setPos(r.x[a] + dx * d + dz * lane, r.y[a], r.z[a] + dz * d - dx * lane);
		setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
		mode = Mode.DRIVE;
		speed = cruise * 0.8;
	}

	// ================================================================================================== smashing

	@Override
	public void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (this.level().isClientSide() || dead || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		Population p = Population.I;
		if (mode == Mode.TRAIN) {
			if (train != null) {
				train.derail(p, level, cause, impulse);
			}
			return;
		}
		switch (cause) {
			case HAND -> {
				if (mode != Mode.FLY) {
					Vec3 v = impulse.scale(0.8);
					double l = v.length();
					if (l > 1.5) {
						v = v.scale(1.5 / l);
					}
					launch(new Vec3(v.x, Math.max(0.1, v.y) + 0.22, v.z));
				}
			}
			case EXPLOSION -> {
				if (chainAt == 0) {
					chainAt = p.now + 3 + this.random.nextInt(8);
				}
			}
			case BEAM -> {
				if (chainAt == 0) {
					chainAt = p.now + this.random.nextInt(5);
				}
			}
			default -> explode(p, level);
		}
	}

	void launch(Vec3 v) {
		mode = Mode.FLY;
		train = null;
		vel = v;
		flyTicks = 0;
		spin = (this.random.nextBoolean() ? 1 : -1) * (14 + this.random.nextInt(18));
		setFlag(FLAG_TUMBLE, true);
		setFlag(FLAG_ALARM, false);
	}

	private void fly(Population p, ServerLevel level) {
		flyTicks++;
		vel = new Vec3(vel.x * 0.995, vel.y - 0.045, vel.z * 0.995);
		double nx = getX() + vel.x, ny = getY() + vel.y, nz = getZ() + vel.z;
		setPos(nx, ny, nz);
		setYRot(getYRot() + (float) spin);
		boolean landed = vel.y < 0 && Terrain.solidAt(level, nx, ny - 0.05, nz);
		boolean wall = flyTicks > 2 && vel.horizontalDistanceSqr() > 0.01 && Terrain.solidAt(level, nx + vel.x * 2, ny + 0.5, nz + vel.z * 2);
		if (landed || wall || flyTicks > 140 || ny < Gz.city().data().groundY() - 1) {
			explode(p, level);
		}
	}

	/** Boom: the explosion, chain reaction through the neighbours, score, fire, panic and car alarms around. */
	void explode(Population p, ServerLevel level) {
		if (dead) {
			return;
		}
		dead = true;
		int st = style();
		boolean train = st == STYLE_TRAIN;
		double radius = train ? 4.0 : st == STYLE_BUS || st == STYLE_TRUCK ? 3.3 : 2.5;
		Vec3 c = new Vec3(getX(), getY() + 0.5, getZ());
		if (this.train != null) {
			this.train.cars.remove(this);
		}
		discard();
		p.explosions++;
		level.playSound(null, c.x, c.y, c.z, GodzillaSounds.EXPLOSION_CAR, SoundSource.NEUTRAL, 6.0F, 0.9F + this.random.nextFloat() * 0.25F);
		Gz.destruction().explode(level, c, radius, Cause.EXPLOSION);
		Gz.destruction().ignite(level, c, radius * 0.7);
		Gz.game().addScore(train ? Game.ScoreKind.TRAIN : Game.ScoreKind.CAR, train ? 300 : st == STYLE_BUS || st == STYLE_TRUCK ? 150 : 100, c);
		p.onExplosion(level, c, this, radius);
	}

	AABB blockBox() {
		return getBoundingBox();
	}
}
