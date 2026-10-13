package dev.godzilla.core.dev;

import dev.godzilla.api.City;
import dev.godzilla.api.CityData;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Stand-in city: the metadata from the jar, linear building lookup, no stamping. Replaced by dev.godzilla.city. */
public final class DevCity implements City {
	private @Nullable CityData data;

	@Override
	public CityData data() {
		if (data == null) {
			data = CityData.loadFromJar();
		}
		return data;
	}

	@Override
	public CityData.@Nullable Building buildingAt(int x, int y, int z) {
		for (CityData.Building b : data().buildings()) {
			if (b.contains(x, y, z)) {
				return b;
			}
		}
		return null;
	}

	@Override
	public void stamp(ServerLevel level) {
		GodzillaMod.LOGGER.warn("godzilla: stand-in city cannot stamp blocks");
	}

	@Override
	public boolean stamped() {
		return false;
	}
}
