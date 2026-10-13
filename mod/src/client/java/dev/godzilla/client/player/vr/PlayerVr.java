package dev.godzilla.client.player.vr;

import dev.godzilla.core.GodzillaMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame VR access for the Godzilla body (render pose with controller orientation), loaded reflectively only when Vivecraft is
 * installed, like the core bridge. Nothing here is verified in a headset.
 */
public final class PlayerVr {
	/** Head and hands in world space for this render frame; "right/left" are the physical hands (handedness resolved). */
	public record Frame(Vec3 head, Vec3 headDir, Vec3 right, Vec3 rightDir, Vec3 rightUp, Vec3 left, Vec3 leftDir, Vec3 leftUp) {
	}

	public interface Backend {
		boolean active();

		@Nullable Frame renderFrame();

		@Nullable Frame tickFrame();

		void haptic(boolean right, float seconds, float frequency, float amplitude);
	}

	private static @Nullable Backend backend;

	private PlayerVr() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
			return;
		}
		try {
			backend = (Backend) Class.forName("dev.godzilla.client.player.vr.ViveRenderBridge").getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			GodzillaMod.LOGGER.error("godzilla: player VR render bridge failed to load; the body will use flat poses", t);
		}
	}

	public static boolean active() {
		try {
			return backend != null && backend.active();
		} catch (Throwable t) {
			return false;
		}
	}

	/** The pose for drawing this frame, or null when not in VR. */
	public static @Nullable Frame renderFrame() {
		if (!active()) {
			return null;
		}
		try {
			Frame f = backend.renderFrame();
			return f != null ? f : backend.tickFrame();
		} catch (Throwable t) {
			return null;
		}
	}

	public static @Nullable Frame tickFrame() {
		if (!active()) {
			return null;
		}
		try {
			return backend.tickFrame();
		} catch (Throwable t) {
			return null;
		}
	}

	public static void haptic(boolean right, float seconds, float frequency, float amplitude) {
		if (active()) {
			try {
				backend.haptic(right, seconds, frequency, Math.max(0, Math.min(1, amplitude)));
			} catch (Throwable ignored) {
				// a rumble must never break the frame
			}
		}
	}
}
