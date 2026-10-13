package dev.godzilla.vr;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.server.VRServerAPI;

/**
 * THE server-side class that references Vivecraft. It is only ever loaded by {@link Vr#init()} after FabricLoader reported
 * that "vivecraft" is present, and only used through {@link VrBackend}. Do not reference this class from anywhere else.
 */
public final class VivecraftBridge implements VrBackend {
	@Override
	public boolean isVrPlayer(ServerPlayer player) {
		return VRAPI.instance().isVRPlayer(player);
	}

	@Override
	public @Nullable PoseSnapshot pose(ServerPlayer player) {
		VRPose pose = VRAPI.instance().getVRPose(player);
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
	public void haptic(ServerPlayer player, boolean mainHand, float durationSeconds, float frequency, float amplitude) {
		VRServerAPI.instance().sendHapticPulse(player, mainHand ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND,
				durationSeconds, frequency, amplitude, 0.0F);
	}
}
