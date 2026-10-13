package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A warship in the bay: a destroyer (12 blocks), cruiser (16) or battleship (20). It lies at its anchor and shells Godzilla
 * during and after the rise. Several hits or one beam sweep sink it with a string of explosions.
 */
public class Warship extends MilitaryUnit {
	public static final float[] LENGTH = {12, 16, 20};
	public static final float[] BEAM = {1.75F, 2.25F, 3.0F};
	private static final float[] HP = {5, 8, 12};
	/** Turret z positions in model pixels (4 px = 1 block, front is -z), the same lists as tools/military/make_assets.py. */
	public static final int[][] TURRET_Z = {{-10, 16}, {-18, -10, 22}, {-26, -17, 28, 19}};

	private int fireCd;
	private int salvoLeft;
	private int turretIdx;
	private int hurtCd;
	private int sinkTicks = -1;
	private float sinkSign = 1;
	private double baseY;

	public Warship(EntityType<? extends Warship> type, Level level) {
		super(type, level);
		this.fireCd = 30 + this.random.nextInt(120);
	}

	/** Server: choose the size class and the heading, then the hit box follows. */
	public void setup(int variant, float yaw, double x, double y, double z) {
		this.entityData.set(VARIANT, variant);
		this.hp = HP[variant];
		this.setYRot(yaw);
		this.yRotO = yaw;
		this.baseY = y;
		this.setPos(x, y, z);
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		int v = this.entityData == null ? 0 : Mth.clamp(this.entityData.get(VARIANT), 0, 2);
		double len = LENGTH[v], w = BEAM[v];
		double yaw = Math.toRadians(this.getYRot());
		double fx = Math.abs(Math.sin(yaw)), fz = Math.abs(Math.cos(yaw));
		double hx = fx * len / 2 + fz * w / 2, hz = fz * len / 2 + fx * w / 2;
		return new AABB(pos.x - hx, pos.y - 1.0, pos.z - hz, pos.x + hx, pos.y + 3.5, pos.z + hz);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (VARIANT.equals(accessor)) {
			this.setPos(this.getX(), this.getY(), this.getZ());
		}
	}

	@Override
	protected float maxHealth() {
		return 8.0F;
	}

	@Override
	protected Game.ScoreKind scoreKind() {
		return Game.ScoreKind.SHIP;
	}

	@Override
	protected int points() {
		return 2500;
	}

	@Override
	protected double blastRadius() {
		return 5.0;
	}

	@Override
	public Vec3 muzzle() {
		int v = Mth.clamp(this.variant(), 0, 2);
		int[] z = TURRET_Z[v];
		double fwd = -z[Mth.clamp(this.turretIdx, 0, z.length - 1)] * 0.25;
		Vec3 base = this.position().add(Mil.dir(this.getYRot(), 0).scale(fwd)).add(0, 2.0, 0);
		float ty = this.turretYawAbs();
		return base.add(Mil.dir(ty, 0).scale(0.75)).add(Mil.dir(ty, this.turretPitch()).scale(2.5));
	}

	@Override
	protected void serverTick(ServerLevel level, @Nullable ServerPlayer g) {
		if (this.hurtCd > 0) {
			this.hurtCd--;
		}
		if (this.sinkTicks >= 0) {
			this.tickSink(level);
			return;
		}
		if (g == null) {
			return;
		}
		Vec3 c = Mil.bodyCenter(g);
		Vec3 m = this.position().add(0, 2.0, 0);
		double dx = c.x - m.x, dz = c.z - m.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = Mil.yawOf(dx, dz);
		float rel = Mil.approach(this.turretYaw(), Mil.wrap(wantYaw - this.getYRot()), 3.0F);
		float elev = Mth.clamp((float) Math.toDegrees(Math.atan2(c.y - m.y, h)) + 14.0F, 3.0F, 50.0F);
		this.setTurret(rel, Mil.approach(this.turretPitch(), elev, 1.5F));
		if (!Mil.mayFire(true)) {
			return;
		}
		if (this.salvoLeft > 0) {
			if (this.tickCount % 9 == 0) {
				this.salvoLeft--;
				this.shoot(level, g);
			}
			return;
		}
		if (this.fireCd-- > 0) {
			return;
		}
		boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - this.turretYawAbs())) < 8.0F;
		if (aligned && h > 20) {
			this.salvoLeft = 2;
			this.fireCd = 70 + this.random.nextInt(60);
		}
	}

	private void shoot(ServerLevel level, ServerPlayer g) {
		int[] z = TURRET_Z[Mth.clamp(this.variant(), 0, 2)];
		this.turretIdx = (this.turretIdx + 1) % z.length;
		Vec3 from = this.muzzle();
		boolean aimed = this.random.nextFloat() < 0.5F;
		Vec3 body = Mil.bodyPoint(g, this.random);
		// below the waves a hit is impossible: the shell raises a column of water above him instead
		Vec3 to = aimed && body.y > 63 ? body : Director.missPoint(level, g, this.random);
		double dist = from.distanceTo(to);
		int flight = Mth.clamp((int) (dist / 2.6), 25, 110);
		MilShot.launch(level, MilShot.SHIP_SHELL, from, to.add(Director.gVel.scale(flight * 0.8)), flight, dist * 0.2, 0);
		this.flashMuzzle();
		Mil.sound(level, from, GodzillaSounds.MIL_SHIP_CANNON, 300, 0.9F + this.random.nextFloat() * 0.15F);
	}

	@Override
	protected void muzzleFlash() {
		super.muzzleFlash();
		Vec3 m = this.muzzle();
		for (int i = 0; i < 6; i++) {
			this.level().addParticle(ParticleTypes.LARGE_SMOKE, true, true, m.x, m.y, m.z, this.random.nextGaussian() * 0.08, 0.05,
					this.random.nextGaussian() * 0.08);
		}
	}

	// ---- sinking -----------------------------------------------------------------------------------------------------------
	private void tickSink(ServerLevel level) {
		int t = this.sinkTicks++;
		Vec3 p = this.position();
		float len = LENGTH[Mth.clamp(this.variant(), 0, 2)];
		if (t == 0 || t == 12 || t == 30 || t == 52 || t == 80) {
			Vec3 f = Mil.dir(this.getYRot(), 0);
			Vec3 at = p.add(f.scale((this.random.nextDouble() - 0.5) * len * 0.7)).add(0, 1.5, 0);
			if (t == 0) {
				Mil.kills++;
				Gz.game().addScore(this.scoreKind(), this.points(), at);
			}
			Mil.sound(level, at, GodzillaSounds.EXPLOSION_LARGE, 240, 0.8F + this.random.nextFloat() * 0.3F);
			level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y, at.z, 2, 1.0, 0.5, 1.0, 0);
			level.sendParticles(ParticleTypes.FLAME, true, true, at.x, at.y, at.z, 24, 1.0, 0.6, 1.0, 0.15);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 16, 1.2, 0.8, 1.2, 0.06);
			Gz.destruction().explode(level, at, 4.0, Cause.EXPLOSION);
		}
		if (t % 3 == 0 && t < 130) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, p.x, p.y + 3, p.z, 3, len * 0.15, 0.5, len * 0.15, 0.03);
		}
		float k = Mth.clamp(t / 120.0F, 0, 1);
		this.setRoll(this.sinkSign * 55.0F * k * k);
		this.setXRot(9.0F * k);
		this.setPos(p.x, this.baseY - (t < 30 ? 0 : (t - 30.0) * (t - 30.0) * 0.0007), p.z);
		if (t > 150) {
			this.discard();
		}
	}

	@Override
	protected void react(Cause cause, Vec3 impulse, float power) {
		if (this.sinkTicks >= 0) {
			return;
		}
		switch (cause) {
			case BEAM -> this.hp -= 0.8F * power;
			case HAND, FOOT, BODY, TAIL -> {
				if (this.hurtCd == 0) {
					this.hp -= 2.5F * power;
					this.hurtCd = 8;
				}
			}
			case EXPLOSION -> this.hp -= 0.6F * power;
			case COLLAPSE, MOTHRA -> this.hp -= 4.0F;
			case DEBRIS -> this.hp -= 0.15F;
			default -> {
			}
		}
		if (this.hp <= 0.0F) {
			this.sinkTicks = 0;
			this.sinkSign = this.random.nextBoolean() ? 1 : -1;
			this.setFlag(F_SINK, true);
		}
	}
}
