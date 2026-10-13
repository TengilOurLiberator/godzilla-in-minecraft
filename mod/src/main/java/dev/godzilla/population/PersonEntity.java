package dev.godzilla.population;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

/**
 * A civilian. No AI framework and no vanilla physics: {@link Population} steps {@link #simulate} on the server, the client only
 * interpolates the position and derives its walk cycle from the speed. Strolls along the pavements, runs away from threats
 * (Godzilla, roars, collapses, explosions) along the streets with arms up, and pops when smashed.
 */
public class PersonEntity extends Entity implements Smashable {
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(PersonEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_OUTFIT = SynchedEntityData.defineId(PersonEntity.class, EntityDataSerializers.BYTE);
	public static final int OUTFITS = 16;
	public static final int FLAG_PANIC = 1;
	public static final int FLAG_FLUNG = 2;

	/** Outfit shirt colours (RGB), index = outfit; matches tools/population/make_textures.py. */
	private static final int[] SHIRT_RGB = {0xEB2828, 0xFF8C00, 0xFFD700, 0x7DE11E, 0x00AF4B, 0x00CDE1, 0x4696FF, 0x2D4BEB,
			0x9B41E1, 0xF537CD, 0xFF82B9, 0xF5F5F5, 0x14BEA0, 0xFF5A3C, 0x282D50, 0x3C3C41};

	// ---- server simulation state
	private double hx, hz = 1; // heading (unit)
	private double speedTrait = 1;
	private long panicUntil;
	private double fleeX, fleeZ;
	private long nextRepick;
	private long nextScream;
	private int idle;
	private boolean blockedFlag;
	private double vx, vy, vz; // flung velocity
	private int flungTicks;
	private boolean flung;
	private boolean popped;
	private int stuck;

	// ---- client animation state
	public float walkPhase;
	public float walkAmp;
	public float walkPhaseO;
	public float walkAmpO;

	public PersonEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_OUTFIT, (byte) 0);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
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
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	/** Tiny people must still be drawn from Godzilla's eyes; the default would cull them after about 24 blocks. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 150.0 * 150.0;
	}

	public int outfit() {
		return this.entityData.get(DATA_OUTFIT) & 0xFF;
	}

	public void setOutfit(int o) {
		this.entityData.set(DATA_OUTFIT, (byte) (o % OUTFITS));
	}

	public boolean panicking() {
		return (this.entityData.get(DATA_FLAGS) & FLAG_PANIC) != 0;
	}

	public boolean flung() {
		return (this.entityData.get(DATA_FLAGS) & FLAG_FLUNG) != 0;
	}

	private void setFlag(int flag, boolean on) {
		int f = this.entityData.get(DATA_FLAGS);
		int n = on ? f | flag : f & ~flag;
		if (n != f) {
			this.entityData.set(DATA_FLAGS, (byte) n);
		}
	}

	public int shirtRgb() {
		return SHIRT_RGB[outfit() % SHIRT_RGB.length];
	}

	@Override
	public void tick() {
		this.commonTick();
		if (this.level().isClientSide()) {
			// walk cycle from the interpolated movement
			double dx = this.getX() - this.xOld, dz = this.getZ() - this.zOld;
			float speed = (float) Math.sqrt(dx * dx + dz * dz);
			this.walkPhaseO = this.walkPhase;
			this.walkAmpO = this.walkAmp;
			this.walkAmp += (Math.min(1.0F, speed * 14.0F) - this.walkAmp) * 0.4F;
			this.walkPhase += speed * (panicking() ? 26.0F : 34.0F) + 0.02F * this.walkAmp;
		}
	}

	// ============================================================================================== server simulation

	/** Called by {@link Population} once per tick (or every few ticks for far people; dt = ticks since the last call). */
	void simulate(Population p, ServerLevel level, int dt) {
		long now = p.now;
		if (flung) {
			fly(p, level, dt);
			return;
		}
		if (((now + getId()) % 5) == 0) {
			evaluate(p, now);
		}
		boolean panic = now < panicUntil;
		if (panic != panicking()) {
			setFlag(FLAG_PANIC, panic);
			if (panic) {
				p.screamMaybe(level, this);
			}
		}
		RandomSource rnd = this.random;
		double speed;
		if (panic) {
			if (now >= nextRepick || blockedFlag) {
				pickPanicHeading(p, level);
				nextRepick = now + 6 + rnd.nextInt(6);
				blockedFlag = false;
			}
			speed = 0.17 * speedTrait;
			if (now >= nextScream) {
				nextScream = now + 30 + rnd.nextInt(50);
				p.screamMaybe(level, this);
			}
		} else {
			if (idle > 0) {
				idle -= dt;
				faceHeading(dt);
				return;
			}
			if (now >= nextRepick || blockedFlag) {
				pickStrollHeading(p, level, blockedFlag);
				nextRepick = now + 40 + rnd.nextInt(160);
				blockedFlag = false;
				if (rnd.nextInt(5) == 0) {
					idle = 20 + rnd.nextInt(80);
				}
			}
			speed = 0.034 * speedTrait;
		}
		step(level, speed * dt);
		faceHeading(dt);
	}

	/** Threat check: starts or extends a panic. */
	private void evaluate(Population p, long now) {
		double[] out = p.tmp;
		if (p.scaredAt(getX(), getZ(), out)) {
			if (now >= panicUntil) {
				// reaction delay so the crowd does not start in perfect unison
				nextRepick = 0;
				panicUntil = now + 40 + this.random.nextInt(40);
			} else {
				panicUntil = Math.max(panicUntil, now + 50 + this.random.nextInt(40));
			}
			fleeX = out[0];
			fleeZ = out[1];
		}
	}

	private void faceHeading(int dt) {
		float target = (float) (Mth.atan2(-hx, hz) * Mth.RAD_TO_DEG);
		float yr = Mth.approachDegrees(getYRot(), target, 28.0F * dt);
		setYRot(yr);
		setYHeadRot(yr);
	}

	private void step(ServerLevel level, double dist) {
		double nx = getX() + hx * dist;
		double nz = getZ() + hz * dist;
		int y = (int) Math.floor(getY() + 0.01);
		int g = Terrain.stand(level, nx, nz, y);
		if (g == Terrain.NONE) {
			// slide along the axes (walls along the street)
			int gx = Terrain.stand(level, nx, getZ(), y);
			int gz = Terrain.stand(level, getX(), nz, y);
			if (gx != Terrain.NONE && Math.abs(hx) >= Math.abs(hz) * 0.5) {
				setPos(nx, gx, getZ());
			} else if (gz != Terrain.NONE) {
				setPos(getX(), gz, nz);
			} else {
				blockedFlag = true;
				stuck++;
			}
			return;
		}
		stuck = 0;
		setPos(nx, g, nz);
	}

	private static final double[] DIRS8X = {1, 0.7071, 0, -0.7071, -1, -0.7071, 0, 0.7071};
	private static final double[] DIRS8Z = {0, 0.7071, 1, 0.7071, 0, -0.7071, -1, -0.7071};

	private void pickPanicHeading(Population p, ServerLevel level) {
		double ax = getX() - fleeX, az = getZ() - fleeZ;
		double l = Math.hypot(ax, az);
		if (l < 0.01) {
			double a = this.random.nextDouble() * Math.PI * 2;
			ax = Math.cos(a);
			az = Math.sin(a);
		} else {
			ax /= l;
			az /= l;
		}
		int y = (int) Math.floor(getY() + 0.01);
		int best = -1;
		double bestScore = -1e9;
		for (int k = 0; k < 8; k++) {
			double dx = DIRS8X[k], dz = DIRS8Z[k];
			double score = dx * ax + dz * az + 0.3 * (dx * hx + dz * hz) + this.random.nextDouble() * 0.25;
			if (score <= bestScore) {
				continue;
			}
			// the way must stay open for a few blocks
			if (Terrain.stand(level, getX() + dx * 1.2, getZ() + dz * 1.2, y) == Terrain.NONE
					|| Terrain.stand(level, getX() + dx * 2.6, getZ() + dz * 2.6, y) == Terrain.NONE) {
				continue;
			}
			bestScore = score;
			best = k;
		}
		if (best < 0) {
			double a = this.random.nextDouble() * Math.PI * 2;
			hx = Math.cos(a);
			hz = Math.sin(a);
		} else {
			hx = DIRS8X[best];
			hz = DIRS8Z[best];
		}
	}

	private void pickStrollHeading(Population p, ServerLevel level, boolean wasBlocked) {
		int y = (int) Math.floor(getY() + 0.01);
		int start = this.random.nextInt(4);
		for (int i = 0; i < 4; i++) {
			int k = ((start + i) & 3) * 2;
			double dx = DIRS8X[k], dz = DIRS8Z[k];
			if (wasBlocked && dx * hx + dz * hz > 0.5) {
				continue; // do not pick the blocked way again
			}
			if (!wasBlocked && dx * hx + dz * hz < -0.5 && this.random.nextInt(3) != 0) {
				continue; // mostly no sudden u-turns
			}
			if (Terrain.stand(level, getX() + dx * 1.2, getZ() + dz * 1.2, y) != Terrain.NONE) {
				hx = dx;
				hz = dz;
				return;
			}
		}
		idle = 10 + this.random.nextInt(30);
	}

	private void fly(Population p, ServerLevel level, int dt) {
		flungTicks += dt;
		vy -= 0.05 * dt;
		double nx = getX() + vx * dt, ny = getY() + vy * dt, nz = getZ() + vz * dt;
		setPos(nx, ny, nz);
		setYRot(getYRot() + 40);
		boolean hit = vy < 0 && Terrain.solidAt(level, nx, ny - 0.05, nz) || Terrain.solidAt(level, nx, ny + 0.3, nz);
		if (hit || ny < Gz.city().data().groundY() || flungTicks > 120) {
			pop(p, level, Cause.HAND, false);
		}
	}

	@Override
	public void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (this.level().isClientSide() || popped || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		Population p = Population.I;
		if (cause == Cause.HAND && impulse.lengthSqr() > 0.02 && !flung) {
			Vec3 v = impulse.scale(0.9);
			double l = v.length();
			if (l > 1.6) {
				v = v.scale(1.6 / l);
			}
			vx = v.x;
			vy = Math.max(0.12, v.y) + 0.2;
			vz = v.z;
			flung = true;
			setFlag(FLAG_FLUNG, true);
			setFlag(FLAG_PANIC, true);
			setPos(getX(), getY() + 0.2, getZ());
			p.screamMaybe(level, this);
			return;
		}
		pop(p, level, cause, true);
	}

	/** The cartoon death: a poof of smoke and coloured confetti, a squish, a few points. */
	void pop(Population p, ServerLevel level, Cause cause, boolean score) {
		if (popped) {
			return;
		}
		popped = true;
		double x = getX(), y = getY() + 0.3, z = getZ();
		if (p.takeParticleBudget()) {
			level.sendParticles(ParticleTypes.POOF, x, y, z, 3, 0.12, 0.12, 0.12, 0.02);
			level.sendParticles(new DustParticleOptions(shirtRgb(), 1.6F), x, y, z, 7, 0.22, 0.25, 0.22, 0.04);
			level.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 3, 0.2, 0.2, 0.2, 0.25);
		}
		p.squish(level, x, y, z);
		Game game = Gz.game();
		game.addScore(Game.ScoreKind.PERSON, 10, new Vec3(x, y, z));
		p.deaths++;
		discard();
	}

	/** Fresh, calm person at a standing position. */
	void init(RandomSource rnd) {
		setOutfit(rnd.nextInt(OUTFITS));
		speedTrait = 0.8 + rnd.nextDouble() * 0.45;
		double a = rnd.nextInt(4) * Math.PI / 2;
		hx = Math.cos(a);
		hz = Math.sin(a);
		setYRot((float) (Mth.atan2(-hx, hz) * Mth.RAD_TO_DEG));
		nextRepick = 0;
		idle = rnd.nextInt(30);
	}

	/** Starts running away from a point (a person coming out of a building, for example). */
	void panicFrom(Population p, double fx, double fz, int ticks) {
		fleeX = fx;
		fleeZ = fz;
		panicUntil = p.now + ticks;
		nextRepick = 0;
		setFlag(FLAG_PANIC, true);
	}

	boolean isFlung() {
		return flung;
	}
}
