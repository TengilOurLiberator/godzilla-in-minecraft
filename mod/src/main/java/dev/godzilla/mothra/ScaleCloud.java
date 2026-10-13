package dev.godzilla.mothra;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Gz;
import dev.godzilla.api.Smashable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A drifting cloud of golden scales released by Mothra. It floats to Godzilla's head, hangs there and hurts while he stands in
 * it. Beam and hands tear it apart; while it hangs between him and Mothra it weakens the beam (see Mothra.beamFactor).
 */
public class ScaleCloud extends Entity implements Smashable {
	public static final float RADIUS = 13f;
	public static final int LIFE = 300;
	private static final EntityDataAccessor<Integer> D_LIFE = SynchedEntityData.defineId(ScaleCloud.class, EntityDataSerializers.INT);

	private int delay;
	private Vec3 vel = Vec3.ZERO;
	private float dmgAcc;

	public ScaleCloud(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setDelay(int ticks) {
		this.delay = ticks;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(D_LIFE, LIFE);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 2);
	}

	/** Remaining life as a fraction (1 fresh, 0 gone). */
	public float lifeFraction() {
		return Mth.clamp(entityData.get(D_LIFE) / (float) LIFE, 0f, 1f);
	}

	public void burn(int ticks) {
		entityData.set(D_LIFE, entityData.get(D_LIFE) - ticks);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		int life = entityData.get(D_LIFE) - 1;
		if (life <= 0) {
			discard();
			return;
		}
		entityData.set(D_LIFE, life);
		Player g = Gz.game().godzilla();
		if (g == null) {
			g = level().getNearestPlayer(this, 600);
		}
		if (g == null) {
			return;
		}
		if (delay > 0) {
			delay--;
		}
		Vec3 aim = g.getEyePosition().add(0, -4, 0);
		Vec3 d = aim.subtract(position());
		double dist = d.length();
		double speed = delay > 0 ? 0 : Math.min(0.55, dist * 0.04);
		Vec3 want = dist < 1e-3 ? Vec3.ZERO : d.scale(speed / dist);
		// a slow swirl so it breathes while it hangs
		double t = tickCount * 0.06;
		want = want.add(Math.cos(t) * 0.05, Math.sin(t * 1.3) * 0.03, Math.sin(t) * 0.05);
		vel = vel.lerp(want, 0.08);
		Vec3 p = position().add(vel);
		setPos(p.x, p.y, p.z);

		// damage over time while Godzilla's head or chest is inside
		double inside = Math.min(g.getEyePosition().distanceTo(position()), g.getEyePosition().add(0, -9, 0).distanceTo(position()));
		if (inside < RADIUS + 3) {
			dmgAcc += 0.09f;
			if (dmgAcc >= 1.2f) {
				Gz.game().hurtGodzilla(dmgAcc, position());
				dmgAcc = 0;
			}
		}
	}

	@Override
	public void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (level().isClientSide() || cause == Cause.MOTHRA) {
			return;
		}
		if (cause == Cause.BEAM) {
			burn((int) (7 * Math.max(1f, power)));
		} else if (cause.byGodzilla()) {
			burn(80);
			vel = vel.add(impulse.scale(1.5));
		}
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
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
