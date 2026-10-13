package dev.godzilla.api;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** The city file (docs/DESIGN.md "City file format"): metadata lookups and stamping the blocks into the world. */
public interface City {
	/** Never null; {@link CityData#EMPTY} when no city file is in the jar. */
	CityData data();

	/** The building whose box contains this block position, or null. */
	CityData.@Nullable Building buildingAt(int x, int y, int z);

	/**
	 * Places the whole city volume into the level exactly as in the file, replacing whatever is there. Used at the start of
	 * every run, so it also repairs a wrecked city. Synchronous; forces the city's chunks to stay loaded.
	 */
	void stamp(ServerLevel level);

	/** True once the city has been stamped into the current world at least once. */
	boolean stamped();
}
