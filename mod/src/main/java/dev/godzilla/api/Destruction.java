package dev.godzilla.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Breaking the world. Server side only. Every smash removes the solid blocks in the volume, throws them as debris along the
 * impulse, calls {@link Smashable#onSmashed} on entities in the volume, queues the touched buildings for a collapse check, and
 * reports score through {@link Game#addScore}. Water, bedrock and the terrain below the city's ground level are never removed.
 * All methods return the number of blocks removed.
 */
public interface Destruction {
	int smashSphere(ServerLevel level, Vec3 center, double radius, Vec3 impulse, Cause cause);

	/** A sphere swept from a to b: a hand moving during one tick, a beam segment, a tail sweep. */
	int smashCapsule(ServerLevel level, Vec3 a, Vec3 b, double radius, Vec3 impulse, Cause cause);

	int smashBox(ServerLevel level, AABB box, Vec3 impulse, Cause cause);

	/**
	 * A blast: blocks within the radius are removed (fewer toward the edge) and thrown outward, entities are smashed with
	 * outward impulse, with fireball, smoke and sound. Size guide: car 2.5, tank shell 2, fuel tank 9.
	 */
	int explode(ServerLevel level, Vec3 center, double radius, Cause cause);

	/** Sets things burning around a point (visual fire and smoke that lasts a while; no block removal). */
	void ignite(ServerLevel level, Vec3 center, double radius);

	/** Fraction (0..1) of a building's original solid blocks still standing; 0 for an unknown id. */
	float integrity(int buildingId);
}
