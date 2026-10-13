package dev.godzilla.player;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Body yaw, stride phase and tail lag. Pure state machine run once per tick; the server runs one per Godzilla to place feet and
 * tail for the destruction, every client runs one per Godzilla to animate the model, from the same inputs (head yaw, position).
 */
public final class BodyMotion {
	/** Degrees per tick the body may turn. */
	public static final float TURN_RATE = 9.0F;
	/** How far (degrees) the body may point away from the head. */
	public static final float MAX_HEAD_OFFSET = 70.0F;
	public static final int HISTORY = 64;
	/** Ticks of lag between successive tail segments. */
	public static final float TAIL_LAG = 2.4F;

	public float yaw;
	public float yawPrev;
	/** Steps taken (continuous: one unit = one footfall). */
	public double phase;
	public double phasePrev;
	/** Horizontal speed in blocks per tick (smoothed). */
	public float speed;
	public boolean initialised;
	private final float[] history = new float[HISTORY];
	private int hpos;
	public int age;
	/** Tail segment yaws in degrees (now and previous tick), body-relative offsets are derived by the renderer. */
	public final float[] tailYaw = new float[GodzillaGeometry.TAIL_SEGMENTS];
	public final float[] tailYawPrev = new float[GodzillaGeometry.TAIL_SEGMENTS];

	public void reset(float yaw) {
		this.yaw = yaw;
		this.yawPrev = yaw;
		java.util.Arrays.fill(history, yaw);
		for (int i = 0; i < tailYaw.length; i++) {
			tailYaw[i] = yaw;
			tailYawPrev[i] = yaw;
		}
		phase = 0;
		phasePrev = 0;
		speed = 0;
		initialised = true;
	}

	/**
	 * @param headYaw   yaw the head looks along (degrees)
	 * @param vx        horizontal movement this tick (blocks)
	 * @param onGround  strides only advance while walking on something
	 * @param scale     entity scale (stride length scales with it)
	 */
	public void tick(float headYaw, double vx, double vz, boolean onGround, double scale) {
		if (!initialised) {
			reset(headYaw);
		}
		yawPrev = yaw;
		phasePrev = phase;
		double sp = Math.hypot(vx, vz);
		speed += (float) ((sp - speed) * 0.4);
		float target = yaw;
		if (sp > 0.05) {
			float moveYaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
			if (Math.abs(Mth.wrapDegrees(moveYaw - headYaw)) > 110) {
				moveYaw += 180.0F; // walking backwards: keep facing the head
			}
			target = moveYaw;
		}
		float off = Mth.wrapDegrees(target - headYaw);
		if (Math.abs(off) > MAX_HEAD_OFFSET) {
			target = headYaw + Math.signum(off) * MAX_HEAD_OFFSET;
		}
		yaw += Mth.clamp(Mth.wrapDegrees(target - yaw), -TURN_RATE, TURN_RATE);
		yaw = Mth.wrapDegrees(yaw);

		if (onGround) {
			phase += sp / (PlayerConfig.STEP_LENGTH * (scale / PlayerConfig.SCALE));
		}
		age++;
		hpos = (hpos + 1) % HISTORY;
		history[hpos] = yaw;
		for (int i = 0; i < tailYaw.length; i++) {
			tailYawPrev[i] = tailYaw[i];
			float lagged = at(Math.round(TAIL_LAG * (i + 1)));
			float sway = (float) (Math.sin(age * 0.11 - i * 0.7) * (2.0 + i * 0.7));
			tailYaw[i] = lagged + sway;
		}
	}

	/** Body yaw {@code back} ticks ago, unwrapped relative to the current yaw. */
	public float at(int back) {
		back = Mth.clamp(back, 0, HISTORY - 1);
		float y = history[((hpos - back) % HISTORY + HISTORY) % HISTORY];
		return yaw + Mth.wrapDegrees(y - yaw);
	}

	public float renderYaw(float pt) {
		return yawPrev + Mth.wrapDegrees(yaw - yawPrev) * pt;
	}

	public double renderPhase(float pt) {
		return Mth.lerp(pt, phasePrev, phase);
	}

	/** Joints of the tail (TAIL_SEGMENTS + 1 points, root first) in world space for a given set of segment yaws. */
	public static Vec3[] tailJoints(Vec3 feet, double bodyYaw, double s, float[] segYaw) {
		Vec3[] out = new Vec3[GodzillaGeometry.TAIL_SEGMENTS + 1];
		Vec3 p = GodzillaGeometry.toWorld(feet, bodyYaw, s, 0, GodzillaGeometry.TAIL_ROOT_Y, GodzillaGeometry.TAIL_ROOT_Z);
		out[0] = p;
		for (int i = 0; i < GodzillaGeometry.TAIL_SEGMENTS; i++) {
			double yaw = Math.toRadians(segYaw[i]);
			float pitch = GodzillaGeometry.TAIL_PITCH[i];
			double h = Math.cos(pitch);
			Vec3 dir = new Vec3(Math.sin(yaw) * h, -Math.sin(pitch), -Math.cos(yaw) * h);
			p = p.add(dir.scale(GodzillaGeometry.TAIL_SEG * s));
			out[i + 1] = p;
		}
		return out;
	}

	/** Which foot lands at which step: the footfall happens when the phase crosses n + 0.5; foot = n % 2 (0 = right). */
	public static int stepIndex(double phase) {
		return (int) Math.floor(phase + 0.5);
	}
}
