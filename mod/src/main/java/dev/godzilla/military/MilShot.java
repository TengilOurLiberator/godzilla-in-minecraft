package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One projectile in flight: a shell, a ship shell, a missile, a heli rocket or a machine-gun tracer. The server moves it along
 * a precomputed arc (no physics, so dozens can be in the air for a few cents of tick time); the client interpolates it,
 * draws a glowing streak and spawns the smoke trail. At the end of the arc the server decides: inside Godzilla's torso is a hit
 * (flash, shake, thud, sparks); otherwise it lands in the sea (splash) or the city (small explosion).
 */
public class MilShot extends Entity {
	public static final byte SHELL = 0, SHIP_SHELL = 1, MISSILE = 2, ROCKET = 3, TRACER = 4;

	private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(MilShot.class, EntityDataSerializers.BYTE);

	private Vec3 from = Vec3.ZERO, to = Vec3.ZERO, side = Vec3.ZERO;
	private int flight = 1;
	private int age;
	private double arc;
	private double weave;
	private Vec3 shooterPos = Vec3.ZERO;

	public MilShot(EntityType<? extends MilShot> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public byte kind() {
		return this.entityData.get(KIND);
	}

	/**
	 * Launches a projectile. {@code to} is where it lands (aimed at the body, or at the ground beside him for a miss).
	 *
	 * @param flight ticks in the air
	 * @param arc    height of the apex above the straight line, in blocks
	 */
	public static void launch(ServerLevel level, byte kind, Vec3 from, Vec3 to, int flight, double arc, double weave) {
		MilShot s = new MilShot(MilEntities.SHOT, level);
		s.entityData.set(KIND, kind);
		s.from = from;
		s.to = to;
		s.shooterPos = from;
		s.flight = Math.max(1, flight);
		s.arc = arc;
		s.weave = weave;
		Vec3 chord = to.subtract(from);
		Vec3 side = chord.cross(new Vec3(0, 1, 0));
		s.side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
		s.setPos(from);
		s.orient(chord);
		Mil.shotsFired++;
		level.addFreshEntity(s);
	}

	private Vec3 at(double t) {
		Vec3 p = from.add(to.subtract(from).scale(t));
		double y = arc * 4.0 * t * (1.0 - t);
		double w = weave * Math.sin(t * Math.PI * 3.0) * (1.0 - t);
		return p.add(side.scale(w)).add(0, y, 0);
	}

	private void orient(Vec3 v) {
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYRot(Mil.yawOf(v.x, v.z));
		this.setXRot((float) -Math.toDegrees(Math.atan2(v.y, h)));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	@Override
	public void tick() {
		if (this.level().isClientSide()) {
			this.clientTrail();
			return;
		}
		long t0 = System.nanoTime();
		this.age++;
		double t = Math.min(1.0, this.age / (double) this.flight);
		Vec3 p = this.at(t);
		Vec3 ahead = this.at(Math.min(1.0, t + 0.04)).subtract(p);
		this.setPos(p);
		if (ahead.lengthSqr() > 1e-6) {
			this.orient(ahead);
		}
		if (this.age >= this.flight) {
			this.impact((ServerLevel) this.level());
			this.discard();
		}
		Mil.unitNanos += System.nanoTime() - t0;
	}

	private void impact(ServerLevel level) {
		Vec3 p = this.position();
		byte kind = this.kind();
		Game.Phase phase = Gz.game().phase();
		ServerPlayer g = Gz.game().godzilla();
		boolean live = phase != Game.Phase.IDLE && phase != Game.Phase.VICTORY && phase != Game.Phase.DEFEAT;
		if (g != null && live && Mil.insideBody(g, p)) {
			switch (kind) {
				case TRACER -> Mil.hitBody(level, g, p, this.shooterPos, 0.1F, false);
				case ROCKET -> Mil.hitBody(level, g, p, this.shooterPos, 0.35F, true);
				case MISSILE -> Mil.hitBody(level, g, p, this.shooterPos, 0.5F, true);
				case SHIP_SHELL -> Mil.hitBody(level, g, p, this.shooterPos, 0.6F, true);
				default -> Mil.hitBody(level, g, p, this.shooterPos, 0.4F, true);
			}
			return;
		}
		if (kind == TRACER) {
			level.sendParticles(ParticleTypes.CRIT, true, true, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.1);
			return;
		}
		double gy = Mil.groundY(level, p.x, p.z);
		if (p.y > gy + 8) {
			level.sendParticles(ParticleTypes.EXPLOSION, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			return;
		}
		boolean water = !level.getBlockState(BlockPos.containing(p.x, gy - 1, p.z)).getFluidState().isEmpty();
		if (water) {
			float size = kind == SHIP_SHELL ? 7.0F : 3.0F;
			Mil.splash(level, p.x, p.z, size);
			Mil.soundLimited(level, new Vec3(p.x, 63, p.z), SoundEvents.GENERIC_SPLASH, kind == SHIP_SHELL ? 140 : 80, 0.6F);
		} else {
			Mil.missesOnCity++;
			Vec3 c = new Vec3(p.x, gy + 0.3, p.z);
			if (Mil.takeBlast()) {
				double radius = switch (kind) {
					case SHIP_SHELL -> 3.5;
					case MISSILE -> 2.6;
					case ROCKET -> 1.8;
					default -> 2.0;
				};
				Gz.destruction().explode(level, c, radius, Cause.PROJECTILE);
			} else {
				level.sendParticles(ParticleTypes.EXPLOSION, true, true, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, c.x, c.y + 1, c.z, 4, 0.5, 0.5, 0.5, 0.02);
			}
		}
	}

	/** Client: smoke and flame behind the projectile, spread between last tick's position and this one. */
	private void clientTrail() {
		byte kind = this.kind();
		if (kind == TRACER) {
			return;
		}
		int n = kind == MISSILE || kind == SHIP_SHELL ? 3 : 2;
		Level level = this.level();
		for (int i = 0; i < n; i++) {
			double f = (i + 1.0) / n;
			double x = this.xOld + (this.getX() - this.xOld) * f;
			double y = this.yOld + (this.getY() - this.yOld) * f;
			double z = this.zOld + (this.getZ() - this.zOld) * f;
			level.addParticle(switch (kind) {
				case MISSILE -> ParticleTypes.CLOUD;
				case ROCKET -> ParticleTypes.CLOUD;
				default -> ParticleTypes.LARGE_SMOKE;
			}, true, true, x, y, z, 0, 0, 0);
		}
		if (kind != SHELL) {
			level.addParticle(ParticleTypes.FLAME, true, true, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 500.0 * 500.0;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, SHELL);
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
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

}

