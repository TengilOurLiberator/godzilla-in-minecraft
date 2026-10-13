package dev.godzilla.client.vr;

import dev.godzilla.vr.PoseSnapshot;
import org.jspecify.annotations.Nullable;

/** Client-side VR access without Vivecraft types in the signatures. Only implementation: {@link VivecraftClientBridge}. */
public interface ClientVrBackend {
	boolean isVrActive();

	/** Vivecraft's current world scale (setting x entity scale), 1 when VR is not active. */
	float worldScale();

	/** Latest world-space pose (after this tick's controller update), or null when VR is not active. */
	@Nullable PoseSnapshot pose();

	void haptic(boolean mainHand, float durationSeconds, float frequency, float amplitude);
}
