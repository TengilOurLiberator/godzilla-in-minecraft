package dev.godzilla.population;

import dev.godzilla.api.Cause;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-car train that shuttles back and forth along one polyline of {@code CityData.rails()} (the elevated line). The cars
 * are entities moved by this controller. When any car is smashed, or the deck under a car is gone, the train derails: every car
 * (one after the other) leaves the track as a flying wreck and explodes when it lands.
 */
final class Train {
	static final double SPACING = 3.75;
	static final double VMAX = 0.55;
	/** The rail blocks sit at y-1 of the polyline; the carriage floor is a little above the rail head. */
	static final double RAIL_LIFT = -0.82;

	final List<VehicleEntity> cars = new ArrayList<>();
	final List<Vec3> pts;
	final double[] cum;
	final double length;
	double c; // centre of the train along the line
	int dir = 1;
	int line;
	double speed;
	int wait = 60;
	boolean derailed;
	private long nextPass;
	private int supportCheck;
	private final double half;

	Train(List<Vec3> pts, int carCount, double startFrac, int colorIdx, ServerLevel level, Population p) {
		this.pts = pts;
		cum = new double[pts.size()];
		for (int i = 1; i < pts.size(); i++) {
			cum[i] = cum[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
		}
		length = cum[cum.length - 1];
		half = (carCount - 1) / 2.0 * SPACING + 2.5;
		c = half + (length - 2 * half) * startFrac;
		dir = startFrac > 0.5 ? -1 : 1;
		speed = 0;
		wait = 0;
		for (int i = 0; i < carCount; i++) {
			VehicleEntity v = new VehicleEntity(PopulationEntities.TRAIN_CAR, level);
			v.setLook(VehicleEntity.STYLE_TRAIN, colorIdx);
			v.mode = VehicleEntity.Mode.TRAIN;
			v.train = this;
			cars.add(v);
		}
		place(0);
		for (VehicleEntity v : cars) {
			level.addFreshEntity(v);
		}
	}

	/** Position and unit tangent (x, z) of the line at distance s. */
	private void sample(double s, double[] out) {
		s = Mth.clamp(s, 0, length);
		int i = 1;
		while (i < cum.length - 1 && cum[i] < s) {
			i++;
		}
		Vec3 a = pts.get(i - 1), b = pts.get(i);
		double seg = Math.max(1e-6, cum[i] - cum[i - 1]);
		double t = (s - cum[i - 1]) / seg;
		out[0] = a.x + (b.x - a.x) * t;
		out[1] = a.y + (b.y - a.y) * t;
		out[2] = a.z + (b.z - a.z) * t;
		out[3] = (b.x - a.x) / seg;
		out[4] = (b.z - a.z) / seg;
	}

	private final double[] tmp = new double[5];

	/** True when nothing of this train is on the rails any more (all cars gone or flying). */
	boolean finished() {
		if (cars.isEmpty()) {
			return true;
		}
		for (VehicleEntity v : cars) {
			if (!v.isRemoved() && v.mode == VehicleEntity.Mode.TRAIN) {
				return false;
			}
		}
		return true;
	}

	private void place(double unused) {
		int n = cars.size();
		for (int i = 0; i < n; i++) {
			VehicleEntity v = cars.get(i);
			double s = c + (i - (n - 1) / 2.0) * SPACING;
			sample(s, tmp);
			v.setPos(tmp[0], tmp[1] + RAIL_LIFT, tmp[2]);
			double tx = tmp[3] * dir, tz = tmp[4] * dir;
			v.setYRot((float) (Mth.atan2(-tx, tz) * Mth.RAD_TO_DEG));
		}
	}

	void tick(Population p, ServerLevel level) {
		if (derailed || cars.isEmpty()) {
			return;
		}
		// movement with stations at both ends
		double toEnd = dir > 0 ? length - half - c : c - half;
		if (wait > 0) {
			wait--;
			speed = 0;
		} else {
			double want = Math.min(VMAX, Math.max(0.06, toEnd * 0.02));
			speed += Mth.clamp(want - speed, -0.01, 0.006);
			c += dir * speed;
			if (toEnd <= 0.2) {
				dir = -dir;
				wait = 140;
				speed = 0;
				nextPass = 0;
			}
		}
		place(0);
		// the pass-by sound: played at the point of the line closest to the listener when the train is a few seconds away
		if (speed > 0.15 && p.now >= nextPass && p.anchorPos != null) {
			double bestD = 1e18, bestS = 0;
			for (double s = 0; s <= length; s += 6) {
				sample(s, tmp);
				double d = (tmp[0] - p.anchorPos.x) * (tmp[0] - p.anchorPos.x) + (tmp[2] - p.anchorPos.z) * (tmp[2] - p.anchorPos.z);
				if (d < bestD) {
					bestD = d;
					bestS = s;
				}
			}
			double ahead = (bestS - c) * dir; // blocks until the train passes the listener
			if (bestD < 200 * 200 && ahead > speed * 80 && ahead < speed * 100) {
				sample(bestS, tmp);
				level.playSound(null, tmp[0], tmp[1] + 1, tmp[2], GodzillaSounds.TRAIN_PASS, SoundSource.NEUTRAL, 9.0F, 1.0F);
				nextPass = p.now + 200;
			}
		}
		// the track must still be there
		if (++supportCheck >= 10) {
			supportCheck = 0;
			for (VehicleEntity v : cars) {
				if (!Terrain.solidAt(level, v.getX(), v.getY() - 0.45, v.getZ())) {
					Population.log("no support under a car at " + v.position());
					derail(p, level, Cause.COLLAPSE, Vec3.ZERO);
					return;
				}
			}
		}
	}

	void derail(Population p, ServerLevel level, Cause cause, Vec3 impulse) {
		if (derailed) {
			return;
		}
		derailed = true;
		p.derailments++;
		Population.log("train derails: " + cause + " at c=" + c + " speed=" + speed + " cars=" + cars.size());
		double tx = 0, tz = 0;
		if (!cars.isEmpty()) {
			sample(c, tmp);
			tx = tmp[3] * dir;
			tz = tmp[4] * dir;
		}
		int n = cars.size();
		for (int i = 0; i < n; i++) {
			VehicleEntity v = cars.get(i);
			double side = (v.getRandom().nextDouble() - 0.5) * 0.5;
			Vec3 vel = new Vec3(tx * speed * 0.8 + -tz * side + impulse.x * 0.3, 0.12 + v.getRandom().nextDouble() * 0.15,
					tz * speed * 0.8 + tx * side + impulse.z * 0.3);
			v.launchVel = vel;
			v.launchAt = p.now + 1 + i * 3L;
		}
		// the cars keep their train membership until they launch, so that further hits do nothing but wait for the cascade
	}
}

