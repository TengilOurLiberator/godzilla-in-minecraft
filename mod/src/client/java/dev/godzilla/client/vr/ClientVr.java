package dev.godzilla.client.vr;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.vr.PoseSnapshot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/** Safe client-side entry point to VR data; works (as "not VR") in a flat client without Vivecraft. */
public final class ClientVr {
	private static @Nullable ClientVrBackend backend;

	private ClientVr() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
			return;
		}
		try {
			backend = (ClientVrBackend) Class.forName("dev.godzilla.client.vr.VivecraftClientBridge").getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			GodzillaMod.LOGGER.error("godzilla: Vivecraft is installed but the client bridge failed to load; continuing flat", t);
		}
	}

	public static boolean isVrActive() {
		return backend != null && backend.isVrActive();
	}

	public static float worldScale() {
		return backend != null ? backend.worldScale() : 1.0F;
	}

	/** VR pose, or a camera-derived one for the local player in a flat client; null when there is no player. */
	public static @Nullable PoseSnapshot pose() {
		if (backend != null) {
			PoseSnapshot p = backend.pose();
			if (p != null) {
				return p;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : PoseSnapshot.fromCamera(mc.player);
	}

	public static void haptic(boolean mainHand, float durationSeconds, float frequency, float amplitude) {
		if (backend != null && backend.isVrActive()) {
			backend.haptic(mainHand, durationSeconds, frequency, amplitude);
		}
	}
}
