package dev.godzilla.vr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Head and hand poses in world coordinates (already multiplied by the player's world scale: Vivecraft sends world-space data).
 * Directions are unit vectors in the direction the device points (the controller's forward / the head's look direction).
 *
 * @param vr true when the data came from Vivecraft, false when it was derived from the camera
 */
public record PoseSnapshot(boolean vr, boolean seated, boolean leftHanded,
						   Vec3 headPos, Vec3 headDir,
						   Vec3 mainHandPos, Vec3 mainHandDir,
						   Vec3 offHandPos, Vec3 offHandDir) {

	/**
	 * Flat-client fallback: head = eye, both hands hang at shoulder height beside the body, pointing along the look direction.
	 * The sizes scale with the entity scale so that a 16x player has 16x longer arms. This is an approximation only.
	 */
	public static PoseSnapshot fromCamera(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double s = player.getScale();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x); // the player's right-hand side (facing +z, right is -x)
		Vec3 base = eye.add(0, -0.45 * s, 0).add(flat.scale(0.35 * s));
		Vec3 main = base.add(right.scale(0.35 * s));
		Vec3 off = base.subtract(right.scale(0.35 * s));
		return new PoseSnapshot(false, false, false, eye, look, main, look, off, look);
	}

	public String describe() {
		return "%s head=%s dir=%s | main=%s dir=%s | off=%s dir=%s".formatted(
				vr ? "VR" : "flat(camera-derived)", fmt(headPos), fmt(headDir), fmt(mainHandPos), fmt(mainHandDir), fmt(offHandPos), fmt(offHandDir));
	}

	private static String fmt(Vec3 v) {
		return "(%.2f, %.2f, %.2f)".formatted(v.x, v.y, v.z);
	}
}
