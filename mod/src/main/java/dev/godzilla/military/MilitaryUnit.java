package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the five unit types. Plain entities (no mob AI, no physics unless flung): the subclass moves itself with setPos each
 * server tick, the vanilla tracker sends the moves and the client interpolates. Units are harmless to Godzilla; they are
 * {@link Smashable} and die in a fireball when hit.
 *
 * <p>Synced to the client: turret yaw (relative to the hull) and elevation, a roll angle, a bit set of flags, a target entity id
 * (the maser beam). Entity event 60 = muzzle flash.
 */
public abstract class MilitaryUnit extends Entity implements Smashable {
	protected static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.FLOAT);
	protected static final EntityDataAccessor<Float> TURRET_PITCH = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.FLOAT);
	protected static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.FLOAT);
	protected static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(MilitaryUnit.class, EntityDataSerializers.INT);

	public static final int F_CRASH = 1, F_CHARGE = 2, F_BEAM = 4, F_SINK = 8;
	public static final byte EVENT_MUZZLE = 60;

	/** Every live unit on the server (director, stats, reset). */
	static final List<MilitaryUnit> ALL = new ArrayList<>();

	protected float hp;
	protected boolean dying;
	private boolean registered;
	/** Set by the director for the few units nearest to Godzilla: only those play their looping engine sounds. */
	public boolean voiced;
	protected int cooldown = 40;

	// client-side smoothed turret
	private float tYaw, tYawO, tPitch, tPitchO;

	protected MilitaryUnit(EntityType<? extends MilitaryUnit> type, Level level) {
		super(type, level);
		this.hp = this.maxHealth();
		if (!level.isClientSide()) {
			// counted at once (the director spawns several per tick), not at the first tick
			this.registered = true;
			ALL.add(this);
		}
	}

	// ---- what a subclass provides ------------------------------------------------------------------------------------------
	protected abstract float maxHealth();

	protected abstract Game.ScoreKind scoreKind();

	protected abstract int points();

	/** Radius of the fireball when it dies, in blocks (a car is 2.5, a fuel tank 9). */
	protected abstract double blastRadius();

	protected abstract void serverTick(ServerLevel level, @Nullable ServerPlayer godzilla);

	/** Damage and reaction to a hit from Godzilla, a blast or falling rubble. Server side, not while dying. */
	protected abstract void react(Cause cause, Vec3 impulse, float power);

	/** Where a shot leaves the barrel, in the world. */
	public Vec3 muzzle() {
		return this.position().add(0, this.getBbHeight() * 0.6, 0);
	}

	// ---- ticking -----------------------------------------------------------------------------------------------------------
	@Override
	public final void tick() {
		if (this.level().isClientSide()) {
			this.clientTick();
			return;
		}
		long t0 = System.nanoTime();
		ServerLevel level = (ServerLevel) this.level();
		if (!this.registered) {
			this.registered = true;
			ALL.add(this);
		}
		if (this.dying) {
			this.destroy(level);
		} else {
			this.serverTick(level, Gz.game().godzilla());
		}
		Mil.unitNanos += System.nanoTime() - t0;
	}

	protected void clientTick() {
		this.tYawO = this.tYaw;
		this.tPitchO = this.tPitch;
		this.tYaw = Mil.approach(this.tYaw, this.entityData.get(TURRET_YAW), 14.0F);
		this.tPitch = Mil.approach(this.tPitch, this.entityData.get(TURRET_PITCH), 8.0F);
		if (this.hasFlag(F_CRASH)) {
			Level level = this.level();
			double r = this.getBbWidth() * 0.3;
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, true, true, this.getX() + (this.random.nextDouble() - 0.5) * r,
						this.getY() + this.getBbHeight() * 0.5, this.getZ() + (this.random.nextDouble() - 0.5) * r, 0, 0.02, 0);
			}
			level.addParticle(ParticleTypes.FLAME, true, true, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 0, 0, 0);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_MUZZLE) {
			this.muzzleFlash();
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Client: a flash and a puff at the barrel. */
	protected void muzzleFlash() {
		Vec3 m = this.muzzle();
		Level level = this.level();
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.FLAME, true, true, m.x, m.y, m.z, this.random.nextGaussian() * 0.03, this.random.nextGaussian() * 0.03,
					this.random.nextGaussian() * 0.03);
		}
		level.addParticle(ParticleTypes.LARGE_SMOKE, true, true, m.x, m.y, m.z, 0, 0.02, 0);
		level.addParticle(ParticleTypes.EXPLOSION, true, true, m.x, m.y, m.z, 0, 0, 0);
	}

	protected final void flashMuzzle() {
		this.level().broadcastEntityEvent(this, EVENT_MUZZLE);
	}

	// ---- being hit and dying -----------------------------------------------------------------------------------------------
	@Override
	public final void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (this.level().isClientSide() || this.dying || this.isRemoved() || cause == Cause.PROJECTILE) {
			return;
		}
		this.react(cause, impulse, power);
	}

	/** Dies next tick (never inside the Destruction callback that told us about the hit). */
	protected void kill() {
		this.dying = true;
	}

	protected void destroy(ServerLevel level) {
		Vec3 c = this.position().add(0, this.getBbHeight() * 0.5, 0);
		this.discard();
		Mil.kills++;
		Gz.game().addScore(this.scoreKind(), this.points(), c);
		Mil.sound(level, c, GodzillaSounds.EXPLOSION_LARGE, 220, 0.95F + this.random.nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.EXPLOSION, true, true, c.x, c.y, c.z, 2, 0.5, 0.3, 0.5, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, c.x, c.y, c.z, 12, 0.6, 0.5, 0.6, 0.05);
		level.sendParticles(ParticleTypes.FLAME, true, true, c.x, c.y, c.z, 14, 0.5, 0.4, 0.5, 0.12);
		Gz.destruction().explode(level, c, this.blastRadius(), Cause.EXPLOSION);
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		ALL.remove(this);
		super.onRemoval(reason);
	}

	// ---- falling out of the sky (helicopters and jets) -----------------------------------------------------------------------
	private boolean crashing;
	private Vec3 crashVel = Vec3.ZERO;
	private int crashAge;
	private float spinYaw, spinRoll, rollAcc;

	protected final boolean crashing() {
		return this.crashing;
	}

	/** Starts the spin-down: it keeps part of its speed, falls, trails smoke, and explodes where it lands. */
	protected void startCrash(Vec3 velocity, Vec3 impulse) {
		this.crashing = true;
		this.crashVel = velocity.scale(0.8).add(impulse.scale(0.6));
		this.spinYaw = (this.random.nextBoolean() ? 1 : -1) * (14.0F + this.random.nextFloat() * 14.0F);
		this.spinRoll = (this.random.nextBoolean() ? 1 : -1) * (10.0F + this.random.nextFloat() * 20.0F);
		this.rollAcc = this.rollDeg();
		this.setFlag(F_CRASH, true);
	}

	protected void tickCrash(ServerLevel level) {
		this.crashAge++;
		this.crashVel = new Vec3(this.crashVel.x * 0.985, this.crashVel.y - 0.055, this.crashVel.z * 0.985);
		Vec3 np = this.position().add(this.crashVel);
		this.setPos(np);
		this.setYRot(this.getYRot() + this.spinYaw);
		this.setXRot(Mth.clamp(this.getXRot() + 2.5F, -10.0F, 55.0F));
		this.rollAcc += this.spinRoll;
		this.setRoll(Mth.wrapDegrees(this.rollAcc));
		boolean ground = !level.getBlockState(net.minecraft.core.BlockPos.containing(np.x, np.y - 0.3, np.z)).isAir() || np.y <= 64.0;
		if (ground || this.crashAge > 140) {
			this.destroy(level);
		}
	}

	// ---- synced state helpers ----------------------------------------------------------------------------------------------
	public boolean hasFlag(int f) {
		return (this.entityData.get(FLAGS) & f) != 0;
	}

	protected void setFlag(int f, boolean on) {
		int cur = this.entityData.get(FLAGS);
		int next = on ? cur | f : cur & ~f;
		if (next != cur) {
			this.entityData.set(FLAGS, next);
		}
	}

	protected void setTurret(float yawRel, float elev) {
		if (Math.abs(Mth.wrapDegrees(yawRel - this.entityData.get(TURRET_YAW))) > 0.7F) {
			this.entityData.set(TURRET_YAW, Mth.wrapDegrees(yawRel));
		}
		if (Math.abs(elev - this.entityData.get(TURRET_PITCH)) > 0.7F) {
			this.entityData.set(TURRET_PITCH, elev);
		}
	}

	protected void setRoll(float roll) {
		if (Math.abs(roll - this.entityData.get(ROLL)) > 0.5F) {
			this.entityData.set(ROLL, roll);
		}
	}

	public float turretYaw() {
		return this.entityData.get(TURRET_YAW);
	}

	public float turretPitch() {
		return this.entityData.get(TURRET_PITCH);
	}

	public float rollDeg() {
		return this.entityData.get(ROLL);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public int targetId() {
		return this.entityData.get(TARGET);
	}

	/** Smoothed turret angles for the renderer. */
	public float renderTurretYaw(float partial) {
		return Mth.rotLerp(partial, this.tYawO, this.tYaw);
	}

	public float renderTurretPitch(float partial) {
		return Mth.lerp(partial, this.tPitchO, this.tPitch);
	}

	/** Absolute yaw of the turret (hull yaw plus the relative turret yaw), degrees. */
	protected float turretYawAbs() {
		return this.getYRot() + this.entityData.get(TURRET_YAW);
	}

	// ---- boilerplate -------------------------------------------------------------------------------------------------------
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TURRET_YAW, 0.0F);
		builder.define(TURRET_PITCH, 0.0F);
		builder.define(ROLL, 0.0F);
		builder.define(FLAGS, 0);
		builder.define(TARGET, 0);
		builder.define(VARIANT, 0);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	/** They must be visible from 150 blocks and more (the vanilla rule is 64 x the box size). */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 450.0 * 450.0;
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
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
