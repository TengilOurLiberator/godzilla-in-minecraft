package dev.godzilla.player;

/** Tunables of the player module. Lengths are blocks at scale 16 (Godzilla is 28.8 blocks tall); they are multiplied by scale/16. */
public final class PlayerConfig {
	private PlayerConfig() {
	}

	/** The vanilla scale attribute while being Godzilla (the attribute maximum). */
	public static final double SCALE = 16.0;
	/** Blocks at or below this y are terrain that Godzilla walks on; everything above is ignored by his collision (the server carves it). */
	public static final int GROUND_Y = 63;
	/** Target full-stick ground speed in blocks per second (400 blocks in 40 s). */
	public static final double WALK_BLOCKS_PER_SECOND = 10.0;
	/** Vanilla player movement_speed base (0.1 = 4.317 blocks/s). */
	public static final double VANILLA_BASE_SPEED = 0.1;
	public static final double STEP_HEIGHT = 3.0;
	/** Third person camera: vanilla distance 4 x scale; Godzilla uses less so the whole body fits. */
	public static final double CAMERA_DISTANCE = 2.2;

	// ---- stride and feet
	public static final double STEP_LENGTH = 9.6;
	public static final double FOOT_HALF_WIDTH = 3.0;
	public static final double FOOT_REACH = 4.6;

	// ---- breath
	public static final int BREATH_CHARGE_TICKS = 12;
	public static final int BREATH_MAX_TICKS = 120;
	public static final int BREATH_RECHARGE_TICKS = 200;
	public static final int BREATH_RECHARGE_DELAY = 24;
	public static final int BREATH_LOCKOUT_TICKS = 40;
	public static final double BREATH_RANGE = 150.0;
	public static final double BREATH_RADIUS = 1.7;
	/** Gauge needed to start charging. */
	public static final float BREATH_MIN_START = 0.12F;

	// ---- roar
	public static final int ROAR_TICKS = 60;
	public static final int ROAR_COOLDOWN = 90;

	// ---- hands
	public static final double HAND_RADIUS = 2.25;
	/** Hand speeds in blocks per tick: below SLOW nothing happens, from FAST on a swing throws at full strength. */
	public static final double HAND_SLOW = 0.30;
	public static final double HAND_FAST = 1.6;
	public static final int SWIPE_TICKS = 16;

	// ---- rise
	public static final double RISE_FORWARD_SPEED = 0.2;
	public static final double SEA_LEVEL = 62.0;
}
