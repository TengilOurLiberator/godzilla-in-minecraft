package dev.godzilla.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Feedback felt by the Godzilla player. Server side calls; the implementation forwards them to that player's client. */
public interface Fx {
	/**
	 * Camera shake (kept gentle in VR) plus controller rumble.
	 *
	 * @param strength 0..1: 0.15 a footstep, 0.3 a shell bouncing off, 0.6 a building coming down nearby, 1 Mothra's dive
	 */
	void shake(ServerPlayer player, float strength, int ticks);

	/** A harmless hit landed on Godzilla from this world position: a flash on that side, a rumble, a grunt now and then. */
	void hitFlash(ServerPlayer player, Vec3 from, float strength);
}
