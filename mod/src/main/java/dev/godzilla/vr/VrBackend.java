package dev.godzilla.vr;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Server-side VR access without any Vivecraft type in its signatures. The only implementation is {@link VivecraftBridge}. */
public interface VrBackend {
	boolean isVrPlayer(ServerPlayer player);

	/** Pose of a VR player, or null if the player is not in VR. */
	@Nullable PoseSnapshot pose(ServerPlayer player);

	/** hand: true = main hand. */
	void haptic(ServerPlayer player, boolean mainHand, float durationSeconds, float frequency, float amplitude);
}
