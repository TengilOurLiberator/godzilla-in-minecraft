package dev.godzilla.client.vr;

import dev.godzilla.vr.PoseSnapshot;
import org.jspecify.annotations.Nullable;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * THE client-side class that references Vivecraft. Loaded reflectively by {@link ClientVr#init()} only when Vivecraft is
 * installed; everything else goes through {@link ClientVrBackend}.
 */
public final class VivecraftClientBridge implements ClientVrBackend {
	@Override
	public boolean isVrActive() {
		return VRClientAPI.instance().isVRActive();
	}

	@Override
	public float worldScale() {
		return VRClientAPI.instance().getWorldScale();
	}

	@Override
	public @Nullable PoseSnapshot pose() {
		VRPose pose = VRClientAPI.instance().getPostTickWorldPose();
		if (pose == null) {
			return null;
		}
		VRBodyPartData head = pose.getHead();
		VRBodyPartData main = pose.getMainHand();
		VRBodyPartData off = pose.getOffHand();
		return new PoseSnapshot(true, pose.isSeated(), pose.isLeftHanded(),
				head.getPos(), head.getDir(), main.getPos(), main.getDir(), off.getPos(), off.getDir());
	}

	@Override
	public void haptic(boolean mainHand, float durationSeconds, float frequency, float amplitude) {
		VRClientAPI.instance().triggerHapticPulse(mainHand ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND, durationSeconds, frequency, amplitude, 0.0F);
	}
}
