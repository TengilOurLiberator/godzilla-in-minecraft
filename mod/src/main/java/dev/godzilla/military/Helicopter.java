package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A helicopter (2.5 blocks): arrives along an air lane, then circles Godzilla at chest to head height just out of easy reach,
 * strafing with a machine gun (tracer lines) and firing rocket pairs. Swatted, it spins down trailing smoke and explodes.
 */
public class Helicopter extends MilitaryUnit {
	private final List<Vec3> waypoints = new ArrayList<>();
	private int wp;
	private boolean orbiting;
	private double theta;
	private double omega;
	private double radius;
	private double heightFrac;
	private Vec3 vel = Vec3.ZERO;

	private int mgLeft, rocketLeft;
	private int fireCd;

	public Helicopter(EntityType<? extends Helicopter> type, Level level) {
		super(type, level);
		this.fireCd = 60 + this.random.nextInt(80);
		this.omega = (this.random.nextBoolean() ? 1 : -1) * (0.011 + this.random.nextDouble() * 0.006);
		this.theta = this.random.nextDouble() * Math.PI * 2;
		this.heightFrac = 0.9 + this.random.nextDouble() * 0.8;
	}

	@Override
	protected float maxHealth() {
		return 2.0F;
	}

	@Override
	protected Game.ScoreKind scoreKind() {
		return Game.ScoreKind.HELICOPTER;
	}

	@Override
	protected int points() {
		return 600;
	}

	@Override
	protected double blastRadius() {
		return 4.0;
	}

	/** The route in: the points of an air lane in order; the orbit follows the last one. */
	public void setRoute(List<Vec3> route) {
		this.waypoints.clear();
		this.waypoints.addAll(route);
		this.wp = 0;
		this.orbiting = route.isEmpty();
	}

	@Override
	public Vec3 muzzle() {
		return this.position().add(Mil.dir(this.getYRot(), 0).scale(1.2)).add(0, 0.25, 0);
	}

	@Override
	protected void serverTick(ServerLevel level, @Nullable ServerPlayer g) {
		if (this.crashing()) {
			this.tickCrash(level);
			return;
		}
		if (g == null) {
			return;
		}
		boolean active = Mil.mayFire(false);
		float s = Mil.scaleOf(g);
		this.radius = this.radius == 0 ? s * (1.9 + this.random.nextDouble() * 0.8) : this.radius;
		Vec3 gp = Mil.feet(g);
		Vec3 target;
		double maxSpeed;
		if (!this.orbiting) {
			boolean near = gp.distanceTo(this.position()) < this.radius * 2.2 + 30;
			if (near || this.wp >= this.waypoints.size()) {
				this.orbiting = true;
				this.theta = Math.atan2(this.getZ() - gp.z, this.getX() - gp.x);
			} else if (this.position().distanceTo(this.waypoints.get(this.wp)) < 12) {
				this.wp++;
			}
		}
		if (this.orbiting) {
			this.theta += this.omega;
			target = new Vec3(gp.x + this.radius * Math.cos(this.theta), gp.y + s * this.heightFrac + Math.sin(this.tickCount * 0.05 + this.getId()) * 1.5,
					gp.z + this.radius * Math.sin(this.theta));
			maxSpeed = 1.2;
		} else {
			target = this.waypoints.get(Math.min(this.wp, this.waypoints.size() - 1));
			maxSpeed = 1.1;
		}
		Vec3 to = target.subtract(this.position());
		double d = to.length();
		Vec3 want = d < 1e-4 ? Vec3.ZERO : to.scale(Math.min(maxSpeed, d * 0.15) / d);
		this.vel = this.vel.add(want.subtract(this.vel).scale(0.07));
		this.setPos(this.position().add(this.vel));
		// the nose points at Godzilla while circling, along the flight path while arriving
		float wantYaw = this.orbiting ? Mil.yawOf(gp.x - this.getX(), gp.z - this.getZ()) : Mil.yawOf(this.vel.x, this.vel.z);
		this.setYRot(Mil.approach(this.getYRot(), wantYaw, 8.0F));
		double sp = this.vel.horizontalDistance();
		this.setXRot(Mth.clamp((float) sp * 14.0F, 0, 14.0F));
		this.setRoll((float) Mth.clamp(this.omega * (this.orbiting ? 900 : 0), -10, 10));
		if (this.voiced && this.tickCount % 40 == 0) {
			Mil.sound(level, this.position(), GodzillaSounds.MIL_HELI_LOOP, 170, 1.0F);
		}
		if (active) {
			this.fire(level, g, s);
		}
	}

	private void fire(ServerLevel level, ServerPlayer g, float s) {
		Vec3 m = this.muzzle();
		if (this.mgLeft > 0) {
			if (this.tickCount % 3 == 0) {
				this.mgLeft--;
				boolean on = this.random.nextFloat() < 0.7F;
				Vec3 to = Mil.bodyPoint(g, this.random);
				if (!on) {
					to = to.add((this.random.nextDouble() - 0.5) * 1.3 * s, (this.random.nextDouble() - 0.5) * 0.8 * s, (this.random.nextDouble() - 0.5) * 1.3 * s);
				}
				double dist = m.distanceTo(to);
				MilShot.launch(level, MilShot.TRACER, m, to, Mth.clamp((int) (dist / 6.5), 3, 30), 0, 0);
			}
			return;
		}
		if (this.rocketLeft > 0) {
			if (this.tickCount % 9 == 0) {
				this.rocketLeft--;
				Vec3 side = Mil.dir(this.getYRot() + 90, 0).scale(this.rocketLeft % 2 == 0 ? 1.3 : -1.3);
				Vec3 from = m.add(side).add(0, -0.3, 0);
				boolean aimed = this.random.nextFloat() < 0.65F;
				Vec3 to = aimed ? Mil.bodyPoint(g, this.random) : Director.missPoint(level, g, this.random);
				double dist = from.distanceTo(to);
				int flight = Mth.clamp((int) (dist / 1.8), 10, 70);
				MilShot.launch(level, MilShot.ROCKET, from, to.add(Director.gVel.scale(flight * 0.7)), flight, dist * 0.02, 1.5);
				Mil.sound(level, from, GodzillaSounds.MIL_MISSILE, 150, 1.25F);
			}
			return;
		}
		if (this.fireCd-- > 0) {
			return;
		}
		double dist = this.position().distanceTo(Mil.feet(g).add(0, s, 0));
		if (dist < 1.2 * s || dist > 8.0 * s || !this.orbiting) {
			return;
		}
		if (this.random.nextFloat() < 0.7F) {
			this.mgLeft = 6 + this.random.nextInt(5);
			Mil.sound(level, m, GodzillaSounds.MIL_MG, 130, 0.95F + this.random.nextFloat() * 0.1F);
			this.fireCd = 90 + this.random.nextInt(90);
		} else {
			this.rocketLeft = 2;
			this.fireCd = 110 + this.random.nextInt(90);
		}
	}

	@Override
	protected void react(Cause cause, Vec3 impulse, float power) {
		switch (cause) {
			case BEAM, COLLAPSE, MOTHRA -> this.kill();
			case EXPLOSION -> {
				this.hp -= 0.9F * power;
				if (this.hp <= 0) {
					this.kill();
				}
			}
			case HAND, FOOT, BODY, TAIL, DEBRIS -> {
				if (!this.crashing()) {
					this.startCrash(this.vel, impulse);
					this.hp = 0;
				}
			}
			default -> {
			}
		}
	}
}

