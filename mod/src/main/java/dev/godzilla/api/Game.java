package dev.godzilla.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The run: who is Godzilla, which phase we are in, the score, Godzilla's health. Server side only. */
public interface Game {
	/** Length of the RISE phase: music.rise is started at its first tick and hands over to the rampage music at 19.2 s. */
	int RISE_TICKS = 384;
	/** Tick of RISE at which Godzilla's eyes break the surface (the hit in music.rise at 12.0 s). */
	int RISE_SURFACE_TICK = 240;
	/** {@link #runTicks()} at which Mothra arrives (three minutes). */
	int MOTHRA_AT_RUN_TICKS = 3600;

	enum Phase {
		/** No run. The city may be standing or wrecked; nothing spawns. */
		IDLE,
		/** The scripted rise out of the bay; the player has no control. */
		RISE,
		/** The player wrecks the city; civilians and the military are active. */
		RAMPAGE,
		/** The boss fight (the rampage continues around it). */
		MOTHRA,
		/** Mothra fell. Results are shown. */
		VICTORY,
		/** Godzilla fell. Results are shown. */
		DEFEAT;

		/** True while the player is in control and the city is alive. */
		public boolean playing() {
			return this == RAMPAGE || this == MOTHRA;
		}
	}

	enum ScoreKind {
		BLOCKS, BUILDING, LANDMARK, FUEL_TANK, CAR, TRAIN, PERSON, TANK, HELICOPTER, JET, SHIP, MASER, MOTHRA_HIT, BONUS
	}

	Phase phase();

	/** Ticks since the current phase began. */
	int phaseTicks();

	/** Ticks since control was given to the player (start of RAMPAGE); 0 before that. Mothra arrives at about 3600. */
	int runTicks();

	/** The player who is Godzilla, or null when nobody is. There is only ever one. */
	@Nullable ServerPlayer godzilla();

	/** The level the run takes place in (the overworld), or null before the server has started. */
	@Nullable ServerLevel level();

	/** Adds points and shows a popup at that place. Only counts while {@link Phase#playing()}. */
	void addScore(ScoreKind kind, int points, Vec3 where);

	long score();

	float godzillaHealth();

	float godzillaMaxHealth();

	/** Only Mothra's attacks should call this. Reaching zero ends the run in DEFEAT. */
	void hurtGodzilla(float amount, Vec3 from);

	/** Called by the Mothra module when her health reaches zero. Ends the run in VICTORY. */
	void mothraDefeated();

	/** Starts a run for this player: fires RESET, restamps the city, then RISE. */
	void start(ServerPlayer player);

	/** Ends any run and returns to IDLE (fires RESET). */
	void stop();
}
