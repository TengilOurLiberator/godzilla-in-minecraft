package dev.godzilla.destruction;

import dev.godzilla.api.Cause;
import dev.godzilla.api.CityData;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** One structure the collapse logic tracks: a city building, or an ad-hoc connected lump of blocks outside any building box. */
final class Struct {
	final int id;
	final CityData.@Nullable Building building;
	int x0, y0, z0, x1, y1, z1; // inclusive world box
	int original;
	int current;
	boolean baselined;
	int[] baseCount = new int[0];   // per layer (y - y0): strong blocks originally
	long[] baseSx = new long[0], baseSz = new long[0];
	boolean destroyed;
	boolean dirty;
	long dirtyAt;      // server tick at which the analysis may run
	int outstanding;   // scheduled collapse events not finished yet
	boolean inFlight;  // analysis running on the worker
	boolean fuel;
	boolean fuelBlown;
	boolean bridge;
	String label = "";
	Cause lastCause = Cause.HAND;
	long lastDamageTick;

	Struct(int id, CityData.@Nullable Building b, int x0, int y0, int z0, int x1, int y1, int z1) {
		this.id = id;
		this.building = b;
		this.x0 = x0;
		this.y0 = y0;
		this.z0 = z0;
		this.x1 = x1;
		this.y1 = y1;
		this.z1 = z1;
	}

	int sx() {
		return x1 - x0 + 1;
	}

	int sy() {
		return y1 - y0 + 1;
	}

	int sz() {
		return z1 - z0 + 1;
	}

	boolean contains(int x, int y, int z) {
		return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
	}

	float integrity() {
		if (original <= 0) {
			return 0;
		}
		return Math.max(0f, Math.min(1f, current / (float) original));
	}

	CityData.Building asBuilding() {
		if (building != null) {
			return building;
		}
		return new CityData.Building(id, "adhoc", label.isEmpty() ? null : label, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), original, 0);
	}
}
