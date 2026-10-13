package dev.godzilla.military;

import dev.godzilla.api.Cause;
import dev.godzilla.api.CityData;
import dev.godzilla.api.Game;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A jet (3 blocks): a fast pass along an air lane, bent toward Godzilla so that it flies by at head height a few tens of blocks
 * to the side, fires two missiles on the approach and banks away along the rest of the lane. Jets come in pairs.
 */
public class Jet extends MilitaryUnit {
	public static final double SPEED = 3.0;
	private static final double X_LIMIT = 248, Z_LIMIT = 308;

	private Vec3[] pts = new Vec3[0];
	private double[] cum = new double[0];
	private double s;
	private double fireS;
	private double soundS = Double.MAX_VALUE;
	private Vec3 soundAt = Vec3.ZERO;
	private boolean soundPlayed;
	private int missilesLeft = 2;
	private int missileCd;
	private float lastYaw;
	private float bank;
	private Vec3 vel = Vec3.ZERO;

	public Jet(EntityType<? extends Jet> type, Level level) {
		super(type, level);
	}

	@Override
	protected float maxHealth() {
		return 1.5F;
	}

	@Override
	protected Game.ScoreKind scoreKind() {
		return Game.ScoreKind.JET;
	}

	@Override
	protected int points() {
		return 900;
	}

	@Override
	protected double blastRadius() {
		return 4.5;
	}

	/** Plans the pass: lane index, whether to fly it backwards, and a sideways offset (for the wingman) in blocks. */
	public void plan(CityData data, ServerPlayer g, RandomSource r, int lane, boolean reverse, double lateral) {
		List<BlockPos> lp = data.airLanes().get(lane);
		List<Vec3> pts = new ArrayList<>();
		for (BlockPos p : lp) {
			pts.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
		}
		if (reverse) {
			java.util.Collections.reverse(pts);
		}
		for (int it = 0; it < 2; it++) { // Chaikin corner cutting: smooth banked turns instead of kinks
			List<Vec3> n = new ArrayList<>();
			n.add(pts.get(0));
			for (int i = 0; i < pts.size() - 1; i++) {
				n.add(pts.get(i).scale(0.75).add(pts.get(i + 1).scale(0.25)));
				n.add(pts.get(i).scale(0.25).add(pts.get(i + 1).scale(0.75)));
			}
			n.add(pts.get(pts.size() - 1));
			pts = n;
		}
		float sc = Mil.scaleOf(g);
		Vec3 gp = Mil.feet(g);
		// closest vertex to Godzilla (horizontal), then slide the whole lane so it passes at 1..2.4 x scale from him
		int ci = 0;
		double cd = Double.MAX_VALUE;
		Vec3 prev = pts.get(0);
		for (int i = 0; i < pts.size(); i++) {
			double d = Math.hypot(pts.get(i).x - gp.x, pts.get(i).z - gp.z);
			if (d < cd) {
				cd = d;
				ci = i;
			}
		}
		Vec3 c = pts.get(ci);
		Vec3 tan = pts.get(Math.min(pts.size() - 1, ci + 1)).subtract(pts.get(Math.max(0, ci - 1)));
		double tl = Math.hypot(tan.x, tan.z);
		Vec3 perp = tl < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-tan.z / tl, 0, tan.x / tl);
		double want = sc * (1.0 + r.nextDouble() * 1.4);
		Vec3 toG = new Vec3(gp.x - c.x, 0, gp.z - c.z);
		double dg = toG.length();
		Vec3 shift = dg < 1e-6 ? Vec3.ZERO : toG.scale(1.0 - want / dg);
		shift = shift.add(perp.scale(lateral));
		double yT = gp.y + sc * (1.5 + r.nextDouble() * 0.5);
		List<Vec3> shifted = new ArrayList<>();
		for (Vec3 p : pts) {
			double dh = Math.hypot(p.x - c.x, p.z - c.z);
			double w = 1.0 - Mth.clamp((dh - 80.0) / 220.0, 0.0, 1.0);
			double y = Mth.lerp(w * w * (3 - 2 * w), p.y, yT);
			shifted.add(new Vec3(Mth.clamp(p.x + shift.x, -X_LIMIT, X_LIMIT), y, Mth.clamp(p.z + shift.z, -Z_LIMIT, Z_LIMIT)));
		}
		// resample every 5 blocks
		List<Vec3> out = new ArrayList<>();
		List<Double> oc = new ArrayList<>();
		double acc = 0;
		out.add(shifted.get(0));
		oc.add(0.0);
		Vec3 last = shifted.get(0);
		for (int i = 1; i < shifted.size(); i++) {
			Vec3 b = shifted.get(i);
			double seg = last.distanceTo(b);
			if (seg < 1e-6) {
				continue;
			}
			int n = Math.max(1, (int) Math.ceil(seg / 5.0));
			for (int k = 1; k <= n; k++) {
				Vec3 p = last.lerp(b, k / (double) n);
				acc += out.get(out.size() - 1).distanceTo(p);
				out.add(p);
				oc.add(acc);
			}
			last = b;
		}
		this.pts = out.toArray(new Vec3[0]);
		this.cum = oc.stream().mapToDouble(Double::doubleValue).toArray();
		// fire at 9.5 x scale away on the approach; the sound peaks at the closest point
		this.fireS = 0;
		int cpa = 0;
		double best = Double.MAX_VALUE;
		boolean found = false;
		for (int i = 0; i < this.pts.length; i++) {
			double d = Math.hypot(this.pts[i].x - gp.x, this.pts[i].z - gp.z);
			if (!found && d <= 9.5 * sc) {
				this.fireS = this.cum[i];
				found = true;
			}
			if (d < best) {
				best = d;
				cpa = i;
			}
		}
		this.soundS = this.cum[cpa] - 3.2 * 20 * SPEED;
		this.soundAt = this.pts[cpa];
		this.s = 0;
		this.setPos(this.pts[0]);
	}

	private Vec3 sample(double dist) {
		if (this.pts.length == 0) {
			return this.position();
		}
		double d = Mth.clamp(dist, 0, this.cum[this.cum.length - 1]);
		int lo = 0, hi = this.cum.length - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (this.cum[mid] <= d) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		double span = this.cum[hi] - this.cum[lo];
		return this.pts[lo].lerp(this.pts[hi], span < 1e-9 ? 0 : (d - this.cum[lo]) / span);
	}

	@Override
	public Vec3 muzzle() {
		return this.position().add(Mil.dir(this.getYRot(), 0).scale(1.0)).add(0, -0.1, 0);
	}

	@Override
	protected void serverTick(ServerLevel level, @Nullable ServerPlayer g) {
		if (this.crashing()) {
			this.tickCrash(level);
			return;
		}
		if (this.pts.length == 0) {
			this.discard();
			return;
		}
		this.s += SPEED;
		if (this.s >= this.cum[this.cum.length - 1]) {
			this.discard();
			return;
		}
		Vec3 p = this.sample(this.s);
		Vec3 ahead = this.sample(this.s + 10).subtract(p);
		this.vel = p.subtract(this.position());
		this.setPos(p);
		float yaw = Mil.yawOf(ahead.x, ahead.z);
		double h = Math.hypot(ahead.x, ahead.z);
		this.setYRot(yaw);
		this.setXRot((float) -Math.toDegrees(Math.atan2(ahead.y, h)));
		float rate = Mth.wrapDegrees(yaw - this.lastYaw);
		this.lastYaw = yaw;
		this.bank = Mth.lerp(0.15F, this.bank, Mth.clamp(rate * 14.0F, -70.0F, 70.0F));
		this.setRoll(this.bank);
		if (!this.soundPlayed && this.s >= this.soundS) {
			this.soundPlayed = true;
			Mil.sound(level, this.soundAt, GodzillaSounds.MIL_JET_PASS, 260, 1.0F);
		}
		if (g != null && Mil.mayFire(false) && this.missilesLeft > 0 && this.s >= this.fireS && this.missileCd-- <= 0) {
			this.missilesLeft--;
			this.missileCd = 7;
			Vec3 side = Mil.dir(this.getYRot() + 90, 0).scale(this.missilesLeft % 2 == 0 ? 1.3 : -1.3);
			Vec3 from = p.add(side).add(0, -0.4, 0);
			boolean aimed = this.random.nextFloat() < 0.7F;
			Vec3 to = aimed ? Mil.bodyPoint(g, this.random) : Director.missPoint(level, g, this.random);
			double dist = from.distanceTo(to);
			int flight = Mth.clamp((int) (dist / 2.4), 18, 80);
			MilShot.launch(level, MilShot.MISSILE, from, to.add(Director.gVel.scale(flight * 0.8)), flight, dist * 0.015, 2.5);
			Mil.sound(level, from, GodzillaSounds.MIL_MISSILE, 150, 0.95F + this.random.nextFloat() * 0.1F);
		}
	}

	@Override
	protected void react(Cause cause, Vec3 impulse, float power) {
		switch (cause) {
			case BEAM, COLLAPSE, MOTHRA -> this.kill();
			case EXPLOSION -> {
				this.hp -= 0.8F * power;
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
