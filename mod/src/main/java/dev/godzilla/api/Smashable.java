package dev.godzilla.api;

import net.minecraft.world.phys.Vec3;

/**
 * Implemented by entities that Godzilla, explosions, falling buildings and the beam can hit: people, vehicles, military units,
 * Mothra. {@link Destruction} calls this (server side) for every Smashable whose bounding box touches a smashed volume, so
 * entities never need to look for Godzilla's hands themselves.
 */
public interface Smashable {
	/**
	 * @param impulse direction and strength of the hit, in blocks per tick (may be zero)
	 * @param power   1 = an ordinary hand swipe or stomp; the beam and big explosions pass more per call
	 */
	void onSmashed(Cause cause, Vec3 impulse, float power);
}
