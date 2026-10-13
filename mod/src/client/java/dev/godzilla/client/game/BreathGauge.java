package dev.godzilla.client.game;

/**
 * The atomic-breath gauge shown by the HUD. The game module does not know the breath: the player module calls
 * {@code BreathGauge.setSource(() -> level)} once in PlayerClient.init() with a function that returns the current charge
 * from 0 (empty) to 1 (full). Until then the gauge shows full.
 */
public final class BreathGauge {
	/** Returns the breath charge, 0..1. */
	public interface Source {
		float level();
	}

	private static volatile Source source = () -> 1f;

	private BreathGauge() {
	}

	public static void setSource(Source s) {
		source = s == null ? () -> 1f : s;
	}

	public static float level() {
		float v = source.level();
		return v < 0 ? 0 : Math.min(v, 1f);
	}
}
