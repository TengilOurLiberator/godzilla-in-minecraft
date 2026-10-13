package dev.godzilla.client.player.vr;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/** The only class of the player module's client side that references Vivecraft. Loaded by {@link PlayerVr#init()}. */
public final class ViveRenderBridge implements PlayerVr.Backend {
	@Override
	public boolean active() {
		return VRClientAPI.instance().isVRActive();
	}

	@Override
	public PlayerVr.@Nullable Frame renderFrame() {
		return frame(VRClientAPI.instance().getWorldRenderPose());
	}

	@Override
	public PlayerVr.@Nullable Frame tickFrame() {
		return frame(VRClientAPI.instance().getPostTickWorldPose());
	}

	private static PlayerVr.@Nullable Frame frame(@Nullable VRPose pose) {
		if (pose == null) {
			return null;
		}
		boolean lh = pose.isLeftHanded();
		VRBodyPartData head = pose.getHead();
		VRBodyPartData right = lh ? pose.getOffHand() : pose.getMainHand();
		VRBodyPartData left = lh ? pose.getMainHand() : pose.getOffHand();
		return new PlayerVr.Frame(head.getPos(), head.getDir(), right.getPos(), right.getDir(), up(right), left.getPos(), left.getDir(), up(left));
	}

	private static Vec3 up(VRBodyPartData part) {
		Vector3f v = part.getRotation().transform(new Vector3f(0, 1, 0));
		return new Vec3(v.x, v.y, v.z);
	}

	@Override
	public void haptic(boolean right, float seconds, float frequency, float amplitude) {
		VRClientAPI api = VRClientAPI.instance();
		boolean main = right != api.isLeftHanded();
		api.triggerHapticPulse(main ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND, seconds, frequency, amplitude, 0.0F);
	}
}
