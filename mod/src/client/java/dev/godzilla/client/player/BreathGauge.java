package dev.godzilla.client.player;

/**
 * Public client-side read access to the local player's Godzilla state, for the HUD (owned by the game client module).
 *
 * <pre>
 *   if (BreathGauge.active()) {
 *       float g = BreathGauge.value();      // 0..1: how much atomic breath is left (1 = full: about 6 s of beam)
 *       BreathGauge.State s = BreathGauge.state();  // IDLE, CHARGING (0.6 s wind-up), FIRING, LOCKOUT (emptied; cannot fire for ~2 s)
 *       float c = BreathGauge.charge();     // 0..1 progress of the wind-up while CHARGING, 1 while FIRING
 *       float roar = BreathGauge.roarReady(); // 1 = roar available, otherwise 0..1 recharge progress is not exposed: use roarReady() > 0
 *   }
 * </pre>
 *
 * The values are updated once per tick from the server's state payload ({@code PlayerNet.StateS2C}), so they always agree with
 * the server. Everything is 0 / IDLE while the local player is not Godzilla.
 */
public final class BreathGauge {
	public enum State { IDLE, CHARGING, FIRING, LOCKOUT }

	static volatile boolean active;
	static volatile float value = 1.0F;
	static volatile float charge;
	static volatile State state = State.IDLE;
	static volatile boolean roarReady = true;

	private BreathGauge() {
	}

	/** True while the local player is Godzilla (a run is on and he is the one). */
	public static boolean active() {
		return active;
	}

	public static float value() {
		return value;
	}

	public static float charge() {
		return charge;
	}

	public static State state() {
		return state;
	}

	/** 1 when the roar can be used now, 0 while it is on cooldown. */
	public static float roarReady() {
		return roarReady ? 1.0F : 0.0F;
	}
}
