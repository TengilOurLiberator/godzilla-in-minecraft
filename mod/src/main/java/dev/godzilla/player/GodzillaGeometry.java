package dev.godzilla.player;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared body geometry: the server uses it to know where the mouth, feet, tail and (flat) hands are, the client renderer uses the
 * same numbers to draw them. Local frame of the body: x = left, y = up, z = forward, origin = between the feet on the ground.
 * Lengths are blocks at scale 16; multiply by {@code s = scale / 16}.
 */
public final class GodzillaGeometry {
	private GodzillaGeometry() {
	}

	public static final double SHOULDER_X = 5.3;
	public static final double SHOULDER_Y = 21.0;
	public static final double SHOULDER_Z = 0.4;
	public static final double UPPER_ARM = 5.0;
	public static final double FORE_ARM = 4.8;

	public static final double TAIL_ROOT_Y = 10.6;
	public static final double TAIL_ROOT_Z = -3.2;
	public static final double TAIL_SEG = 3.6;
	public static final int TAIL_SEGMENTS = 8;
	/** Downward pitch of each tail segment in radians (the tail droops to the ground and lifts its tip). */
	public static final float[] TAIL_PITCH = {0.35F, 0.45F, 0.50F, 0.45F, 0.35F, 0.20F, 0.05F, -0.10F};
	public static final float[] TAIL_THICK = {4.8F, 4.3F, 3.8F, 3.3F, 2.7F, 2.1F, 1.5F, 1.0F};

	/** Head-local mouth (snout tip, between the jaws): x right..., z forward, y up, relative to the eye. */
	public static final double MOUTH_FORWARD = 6.4;
	public static final double MOUTH_DOWN = 2.5;

	public static double scaleOf(double entityScale) {
		return entityScale / PlayerConfig.SCALE;
	}

	/** Unit vector the body faces for this yaw (Minecraft yaw: 0 = +z, 90 = -x). */
	public static Vec3 forward(double yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new Vec3(-Math.sin(r), 0, Math.cos(r));
	}

	/** Unit vector to the body's left. */
	public static Vec3 left(double yawDeg) {
		double r = Math.toRadians(yawDeg);
		return new Vec3(Math.cos(r), 0, Math.sin(r));
	}

	public static Vec3 toWorld(Vec3 feet, double yawDeg, double s, double lx, double ly, double lz) {
		Vec3 f = forward(yawDeg);
		Vec3 l = left(yawDeg);
		return new Vec3(feet.x + s * (l.x * lx + f.x * lz), feet.y + s * ly, feet.z + s * (l.z * lx + f.z * lz));
	}

	public static Vec3 shoulder(Vec3 feet, double yawDeg, double s, boolean left) {
		return toWorld(feet, yawDeg, s, left ? SHOULDER_X : -SHOULDER_X, SHOULDER_Y, SHOULDER_Z);
	}

	/** The mouth: ahead of and below the eye along the head direction (no roll). */
	public static Vec3 mouth(Vec3 headPos, Vec3 headDir, double s) {
		Vec3 f = headDir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : headDir.normalize();
		Vec3 up = headUp(f);
		return headPos.add(f.scale(MOUTH_FORWARD * s)).add(up.scale(-MOUTH_DOWN * s));
	}

	public static Vec3 headUp(Vec3 f) {
		Vec3 right = f.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1e-8) {
			right = new Vec3(-1, 0, 0);
		}
		return right.cross(f).normalize();
	}

	public static float yawOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	/**
	 * Flat-client hands (no controllers): idle at the sides, or the swipe animation when {@code swipeTicks >= 0}.
	 * Same function on the server (for the capsule sweep) and the client (to draw the arm).
	 *
	 * @param left       which hand
	 * @param swipeTicks ticks since the swipe started (fractional for rendering), negative when idle
	 */
	public static Vec3 flatHand(Vec3 feet, double yawDeg, double s, boolean left, float swipeTicks, float idleTime) {
		double side = left ? 1 : -1;
		double lx;
		double ly;
		double lz;
		// idle: hands hang forward and out, with a slow breathing bob
		double bob = Math.sin(idleTime * 0.09 + (left ? 0 : 1.7)) * 0.35;
		double ix = side * 6.9;
		double iy = 14.2 + bob;
		double iz = 4.4;
		if (swipeTicks < 0) {
			lx = ix;
			ly = iy;
			lz = iz;
		} else {
			float u = Mth.clamp(swipeTicks / PlayerConfig.SWIPE_TICKS, 0, 1);
			double r = 9.0;
			double a0 = Math.toRadians(105);
			double a1 = Math.toRadians(-55);
			double sx = SHOULDER_X;
			double sh = 16.0;
			if (u < 0.25) {
				// wind-up: out and back
				double k = smooth(u / 0.25);
				double wx = side * (sx + r * Math.sin(a0));
				double wz = SHOULDER_Z + r * Math.cos(a0) * 0.6 - 1.0;
				lx = Mth.lerp(k, ix, wx);
				ly = Mth.lerp(k, iy, sh + 1.5);
				lz = Mth.lerp(k, iz, wz);
			} else if (u < 0.6) {
				double k = (u - 0.25) / 0.35;
				double a = Mth.lerp(smooth(k), a0, a1);
				lx = side * (sx + r * Math.sin(a));
				lz = SHOULDER_Z + r * Math.cos(a) * 0.6 - 1.0 + (r * 0.4) * Math.cos(a) * Math.cos(a);
				ly = sh + 1.5 - 3.0 * k;
			} else {
				double k = smooth((u - 0.6) / 0.4);
				double ex = side * (sx + r * Math.sin(a1));
				double ez = SHOULDER_Z + r * Math.cos(a1) * 0.6 - 1.0 + (r * 0.4) * Math.cos(a1) * Math.cos(a1);
				lx = Mth.lerp(k, ex, ix);
				ly = Mth.lerp(k, sh - 1.5, iy);
				lz = Mth.lerp(k, ez, iz);
			}
		}
		return toWorld(feet, yawDeg, s, lx, ly, lz);
	}

	private static double smooth(double k) {
		k = Mth.clamp(k, 0, 1);
		return k * k * (3 - 2 * k);
	}

	/** Two-bone IK: the elbow for a shoulder, a hand and the two bone lengths; the arm stretches when the hand is out of reach. */
	public static Vec3 elbow(Vec3 shoulder, Vec3 hand, double l1, double l2, Vec3 poleHint) {
		Vec3 d = hand.subtract(shoulder);
		double dist = d.length();
		if (dist < 1e-4) {
			return shoulder.add(poleHint.normalize().scale(l1));
		}
		Vec3 dir = d.scale(1 / dist);
		double reach = l1 + l2;
		if (dist >= reach * 0.999) {
			return shoulder.add(dir.scale(dist * l1 / reach));
		}
		double a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
		double h = Math.sqrt(Math.max(0, l1 * l1 - a * a));
		Vec3 pole = poleHint.subtract(dir.scale(poleHint.dot(dir)));
		if (pole.lengthSqr() < 1e-8) {
			pole = new Vec3(0, -1, 0);
		}
		return shoulder.add(dir.scale(a)).add(pole.normalize().scale(h));
	}
}
