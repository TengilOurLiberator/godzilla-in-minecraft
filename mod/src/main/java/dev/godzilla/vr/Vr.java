package dev.godzilla.vr;

import dev.godzilla.core.GodzillaMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Safe entry point to server-side VR data. Works with or without Vivecraft installed: without it every query answers "not VR".
 * No Vivecraft type appears here; the Vivecraft-referencing class is loaded reflectively, and only when the mod is present.
 */
public final class Vr {
	private static @Nullable VrBackend backend;

	private Vr() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
			GodzillaMod.LOGGER.info("godzilla: Vivecraft not installed; VR features answer 'not VR'");
			return;
		}
		try {
			backend = (VrBackend) Class.forName("dev.godzilla.vr.VivecraftBridge").getDeclaredConstructor().newInstance();
			GodzillaMod.LOGGER.info("godzilla: Vivecraft bridge active");
		} catch (Throwable t) {
			GodzillaMod.LOGGER.error("godzilla: Vivecraft is installed but the bridge failed to load; continuing without VR", t);
		}
	}

	public static boolean available() {
		return backend != null;
	}

	public static boolean isVrPlayer(ServerPlayer player) {
		return backend != null && backend.isVrPlayer(player);
	}

	/** VR pose if the player is in VR, otherwise a camera-derived pose. Never null. */
	public static PoseSnapshot pose(ServerPlayer player) {
		if (backend != null) {
			PoseSnapshot p = backend.pose(player);
			if (p != null) {
				return p;
			}
		}
		return PoseSnapshot.fromCamera(player);
	}

	/** Haptic pulse on a VR player's controller; does nothing for flat players. */
	public static void haptic(ServerPlayer player, boolean mainHand, float durationSeconds, float frequency, float amplitude) {
		if (backend != null) {
			backend.haptic(player, mainHand, durationSeconds, frequency, amplitude);
		}
	}
}
