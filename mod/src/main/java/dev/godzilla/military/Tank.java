package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A tank (2 blocks long) that drives the road graph keeping a firing distance from Godzilla, tracks him with its turret and
 * lobs arcing shells that mostly miss or bounce off. Stomp, kick or burn it.
 */
public class Tank extends MilitaryUnit {
	private int edge = -1;
	private double t;
	private int dir = 1;
	private double speed;
	private double preferred;
	private int reverseCd;
	private int fireCd;

	private boolean flung;
	private int flungTicks;
	private Vec3 flungVel = Vec3.ZERO;
	private float flungRoll;

	public Tank(EntityType<? extends Tank> type, Level level) {
		super(type, level);
		this.speed = 0.18 + this.random.nextDouble() * 0.07;
		this.fireCd = 30 + this.random.nextInt(100);
	}

	@Override
	protected float maxHealth() {
		return 3.0F;
	}

	@Override
	protected Game.ScoreKind scoreKind() {
		return Game.ScoreKind.TANK;
	}

	@Override
	protected int points() {
		return 300;
	}

	@Override
	protected double blastRadius() {
		return 3.2;
	}

	@Override
	public Vec3 muzzle() {
		float tyaw = this.turretYawAbs();
		Vec3 base = this.position().add(0, 0.56, 0).add(Mil.dir(this.getYRot(), 0).scale(-0.125)).add(Mil.dir(tyaw, 0).scale(0.44));
		return base.add(Mil.dir(tyaw, this.turretPitch()).scale(1.06));
	}

	/** Put the tank on the road at the point nearest to {@code where}. */
	public void place(Vec3 where) {
		RoadGraph rg = RoadGraph.of(Gz.city().data());
		if (rg.usable()) {
			RoadGraph.Snap s = rg.snap(where);
			this.edge = s.edge();
			this.t = s.t();
			Vec3 p = rg.point(this.edge, this.t);
			this.setPos(p);
			// start out heading toward Godzilla (the yaws in tokyo.json's tankSpawnYaws are all 0 and the city data does not carry them)
			Vec3 along = rg.nodes[rg.b[this.edge]].subtract(rg.nodes[rg.a[this.edge]]);
			net.minecraft.server.level.ServerPlayer g = Gz.game().godzilla();
			this.dir = g == null ? (this.random.nextBoolean() ? 1 : -1)
					: (along.x * (Mil.feet(g).x - p.x) + along.z * (Mil.feet(g).z - p.z) >= 0 ? 1 : -1);
			Vec3 d = along.scale(this.dir);
			this.setYRot(Mil.yawOf(d.x, d.z));
		} else {
			this.setPos(where);
		}
		this.yRotO = this.getYRot();
	}

	@Override
	protected void serverTick(ServerLevel level, @Nullable ServerPlayer g) {
		if (this.flung) {
			this.tickFlung();
			return;
		}
		RoadGraph rg = RoadGraph.of(Gz.city().data());
		boolean active = g != null && Mil.mayFire(false);
		if (this.edge < 0 && rg.usable()) {
			this.place(this.position());
		}
		if (g == null || !active) {
			return;
		}
		float s = Mil.scaleOf(g);
		this.preferred = (this.preferred == 0 ? (3.0 + this.random.nextDouble() * 2.0) * s : this.preferred);
		if (rg.usable()) {
			this.drive(rg, g, s);
		}
		this.aimAndFire(level, g, s);
		if (this.voiced && this.tickCount % 60 == 0) {
			Mil.sound(level, this.position(), GodzillaSounds.MIL_TANK_ENGINE_LOOP, 100, 1.0F);
		}
	}

	private void drive(RoadGraph rg, ServerPlayer g, float s) {
		Vec3 pos = this.position();
		Vec3 toG = Mil.feet(g).subtract(pos);
		double dist = Math.sqrt(toG.x * toG.x + toG.z * toG.z);
		Vec3 along = rg.nodes[rg.b[this.edge]].subtract(rg.nodes[rg.a[this.edge]]).scale(this.dir);
		if (this.reverseCd > 0) {
			this.reverseCd--;
		} else {
			double dot = along.x * toG.x + along.z * toG.z;
			if ((dist < 1.5 * s && dot > 0) || (dist > 8.0 * s && dot < 0)) {
				this.dir = -this.dir;
				this.reverseCd = 80;
			}
		}
		double step = this.speed / Math.max(1.0, rg.len[this.edge]);
		this.t += step * this.dir;
		if (this.t >= 1.0 || this.t <= 0.0) {
			int node = this.t >= 1.0 ? rg.b[this.edge] : rg.a[this.edge];
			this.t = Mth.clamp(this.t, 0.0, 1.0);
			int[] opts = rg.nodeEdges.get(node);
			int best = this.edge;
			double bestScore = Double.MAX_VALUE;
			for (int e : opts) {
				if (e == this.edge && opts.length > 1) {
					continue;
				}
				int other = rg.a[e] == node ? rg.b[e] : rg.a[e];
				Vec3 np = rg.nodes[other];
				double d = Math.sqrt((np.x - Mil.feet(g).x) * (np.x - Mil.feet(g).x) + (np.z - Mil.feet(g).z) * (np.z - Mil.feet(g).z));
				double score = Math.abs(d - this.preferred) + this.random.nextDouble() * 14.0;
				if (score < bestScore) {
					bestScore = score;
					best = e;
				}
			}
			this.edge = best;
			this.dir = rg.a[best] == node ? 1 : -1;
			this.t = this.dir == 1 ? 0.0 : 1.0;
		}
		Vec3 p = rg.point(this.edge, this.t);
		Vec3 d = rg.nodes[rg.b[this.edge]].subtract(rg.nodes[rg.a[this.edge]]).scale(this.dir);
		this.setPos(p);
		this.setYRot(Mil.approach(this.getYRot(), Mil.yawOf(d.x, d.z), 9.0F));
	}

	private void aimAndFire(ServerLevel level, ServerPlayer g, float s) {
		Vec3 c = Mil.bodyCenter(g);
		Vec3 m = this.position().add(0, 0.8, 0);
		double dx = c.x - m.x, dz = c.z - m.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = Mil.yawOf(dx, dz);
		float rel = Mil.approach(this.turretYaw(), Mil.wrap(wantYaw - this.getYRot()), 6.0F);
		float elev = Mth.clamp((float) Math.toDegrees(Math.atan2(c.y - m.y, h)) + 18.0F, 2.0F, 60.0F);
		this.setTurret(rel, Mil.approach(this.turretPitch(), elev, 3.0F));
		if (this.fireCd > 0) {
			this.fireCd--;
			return;
		}
		boolean inRange = h > 1.0 * s && h < 10.0 * s;
		boolean aligned = Math.abs(Mth.wrapDegrees(wantYaw - this.turretYawAbs())) < 7.0F;
		if (!inRange || !aligned) {
			return;
		}
		Vec3 from = this.muzzle();
		boolean aimed = this.random.nextFloat() < 0.6F;
		Vec3 to = aimed ? Mil.bodyPoint(g, this.random) : Director.missPoint(level, g, this.random);
		double dist = from.distanceTo(to);
		int flight = Mth.clamp((int) (dist / 3.0), 14, 60);
		to = to.add(Director.gVel.scale(flight * 0.8));
		MilShot.launch(level, MilShot.SHELL, from, to, flight, dist * 0.1, 0);
		this.flashMuzzle();
		Mil.sound(level, from, GodzillaSounds.MIL_TANK_FIRE, 180, 0.9F + this.random.nextFloat() * 0.2F);
		this.fireCd = 100 + this.random.nextInt(90);
	}

	private void tickFlung() {
		this.flungTicks++;
		this.flungVel = new Vec3(this.flungVel.x * 0.99, this.flungVel.y - 0.08, this.flungVel.z * 0.99);
		this.move(MoverType.SELF, this.flungVel);
		this.flungRoll += 22.0F;
		this.setRoll(Mth.wrapDegrees(this.flungRoll));
		this.setXRot(Mth.wrapDegrees(this.getXRot() + 9.0F));
		if ((this.flungTicks > 4 && (this.onGround() || this.horizontalCollision)) || this.flungTicks > 90) {
			this.destroy((ServerLevel) this.level());
		}
	}

	@Override
	protected void react(Cause cause, Vec3 impulse, float power) {
		switch (cause) {
			case BEAM, FOOT, BODY, COLLAPSE, MOTHRA -> this.kill();
			case HAND, TAIL -> {
				double speed = impulse.length();
				if (speed > 0.3) {
					this.flung = true;
					this.flungVel = impulse.scale(1.1).add(0, 0.35, 0);
					this.setOnGround(false);
				} else {
					this.hp -= 1.0F * power;
				}
			}
			case EXPLOSION -> this.hp -= 0.6F * power;
			case DEBRIS -> this.hp -= 0.5F;
			default -> {
			}
		}
		if (this.hp <= 0.0F) {
			this.kill();
		}
	}
}
