package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A maser cannon (a truck with a dish, about 3 blocks): it swings the dish onto Godzilla, charges for two seconds and then
 * holds a wavering blue-white beam on his chest. The beam itself is drawn by the client from the synced target id (the most
 * dramatic harmless hit: strong flash and rumble). Trucks do not move.
 */
public class MaserCannon extends MilitaryUnit {
	private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(MaserCannon.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(MaserCannon.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(MaserCannon.class, EntityDataSerializers.FLOAT);
	private static final int IDLE = 0, CHARGE = 1, BEAM = 2;
	public static final int CHARGE_TICKS = 40, BEAM_TICKS = 56;

	private int state = IDLE;
	private int timer;

	public MaserCannon(EntityType<? extends MaserCannon> type, Level level) {
		super(type, level);
		this.cooldown = 40 + this.random.nextInt(100);
	}

	@Override
	protected float maxHealth() {
		return 3.0F;
	}

	@Override
	protected Game.ScoreKind scoreKind() {
		return Game.ScoreKind.MASER;
	}

	@Override
	protected int points() {
		return 1000;
	}

	@Override
	protected double blastRadius() {
		return 4.0;
	}

	/** The tip of the feed horn, where the beam starts (dimensions from tools/military/make_assets.py: pivot 15+6 px high). */
	@Override
	public Vec3 muzzle() {
		return this.position().add(0, 1.31, 0).add(Mil.dir(this.getYRot(), 0).scale(-0.375)).add(Mil.dir(this.turretYawAbs(), this.turretPitch()).scale(1.5));
	}

	@Override
	protected void serverTick(ServerLevel level, @Nullable ServerPlayer g) {
		if (g == null || !Mil.mayFire(false)) {
			this.endBeam();
			return;
		}
		float s = Mil.scaleOf(g);
		Vec3 c = Mil.bodyCenter(g);
		Vec3 m = this.position().add(0, 1.3, 0);
		double dx = c.x - m.x, dz = c.z - m.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = Mil.yawOf(dx, dz);
		float rel = Mil.approach(this.turretYaw(), Mil.wrap(wantYaw - this.getYRot()), 4.0F);
		float elev = Mth.clamp((float) Math.toDegrees(Math.atan2(c.y - m.y, h)), 0.0F, 75.0F);
		this.setTurret(rel, Mil.approach(this.turretPitch(), elev, 3.0F));
		boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - this.turretYawAbs())) < 6.0F;
		if (this.state != IDLE) {
			this.setBeamEnd(c);
		}
		switch (this.state) {
			case IDLE -> {
				if (this.cooldown > 0) {
					this.cooldown--;
				} else if (aligned && h > 1.2 * s && h < 11.0 * s) {
					this.state = CHARGE;
					this.timer = CHARGE_TICKS;
					this.setBeamEnd(c);
					this.setFlag(F_CHARGE, true);
					Mil.sound(level, this.muzzle(), GodzillaSounds.MIL_MASER_CHARGE, 140, 1.0F);
				}
			}
			case CHARGE -> {
				if (--this.timer <= 0) {
					this.state = BEAM;
					this.timer = BEAM_TICKS;
					this.setFlag(F_CHARGE, false);
					this.setFlag(F_BEAM, true);
				}
			}
			default -> {
				if (this.timer % 30 == BEAM_TICKS % 30) {
					Mil.sound(level, this.muzzle(), GodzillaSounds.MIL_MASER_LOOP, 140, 1.0F);
				}
				if (this.timer % 2 == 0) {
					Vec3 p = Mil.bodyPoint(g, this.random);
					Mil.hitBody(level, g, p, this.muzzle(), 0.8F, false);
					Gz.fx().shake(g, 0.7F, 5);
				}
				if (this.timer % 8 == 0 && Mil.puffBudget > 0) {
					Mil.puffBudget--;
					Vec3 p = Mil.bodyPoint(g, this.random);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, p.x, p.y, p.z, 14, 0.05 * s, 0.05 * s, 0.05 * s, 0.4);
				}
				if (--this.timer <= 0) {
					this.endBeam();
				}
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(END_X, 0.0F);
		builder.define(END_Y, 0.0F);
		builder.define(END_Z, 0.0F);
	}

	private void setBeamEnd(Vec3 p) {
		if (Math.abs(p.x - this.entityData.get(END_X)) > 0.05 || Math.abs(p.y - this.entityData.get(END_Y)) > 0.05 || Math.abs(p.z - this.entityData.get(END_Z)) > 0.05) {
			this.entityData.set(END_X, (float) p.x);
			this.entityData.set(END_Y, (float) p.y);
			this.entityData.set(END_Z, (float) p.z);
		}
	}

	/** World position the beam ends at (Godzilla's chest), valid while charging or beaming. */
	public Vec3 beamEnd() {
		return new Vec3(this.entityData.get(END_X), this.entityData.get(END_Y), this.entityData.get(END_Z));
	}

	private void endBeam() {
		if (this.state != IDLE) {
			this.state = IDLE;
			this.cooldown = 220 + this.random.nextInt(140);
			this.setFlag(F_CHARGE, false);
			this.setFlag(F_BEAM, false);
			this.setBeamEnd(Vec3.ZERO);
		}
	}

	@Override
	protected void clientTick() {
		super.clientTick();
		Level level = this.level();
		if (this.hasFlag(F_CHARGE) || this.hasFlag(F_BEAM)) {
			Vec3 m = this.muzzle();
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, true, m.x + this.random.nextGaussian() * 0.5, m.y + this.random.nextGaussian() * 0.5,
						m.z + this.random.nextGaussian() * 0.5, 0, 0, 0);
			}
		}
		if (this.hasFlag(F_BEAM)) {
			Vec3 e = this.beamEnd();
			float s = 16.0F;
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, true, e.x + this.random.nextGaussian() * 0.15 * s, e.y + this.random.nextGaussian() * 0.2 * s,
						e.z + this.random.nextGaussian() * 0.15 * s, this.random.nextGaussian() * 0.3, this.random.nextGaussian() * 0.3, this.random.nextGaussian() * 0.3);
			}
		}
	}

	@Override
	protected void react(Cause cause, Vec3 impulse, float power) {
		switch (cause) {
			case BEAM, FOOT, BODY, COLLAPSE, MOTHRA -> this.kill();
			case HAND, TAIL -> this.hp -= impulse.length() > 0.3 ? 3.0F : 1.0F * power;
			case EXPLOSION -> this.hp -= 0.5F * power;
			case DEBRIS -> this.hp -= 0.4F;
			default -> {
			}
		}
		if (this.hp <= 0.0F) {
			this.kill();
		}
	}
}
