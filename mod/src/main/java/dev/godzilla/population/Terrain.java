package dev.godzilla.population;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Gz;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cheap walkability queries for people. A cell is walkable when it is inside the city rectangle, outside the ground floor
 * footprint of every building box (a static mask built from the city metadata, so people never wander into buildings even
 * when they are hollow), and the blocks say so (floor below, free cell, no fluid).
 */
final class Terrain {
	static final int NONE = Integer.MIN_VALUE;

	private static CityData built;
	private static byte[] blocked;
	private static int ox, oz, sx, sz;
	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

	private Terrain() {
	}

	/** (Re)builds the footprint mask when the city data object changed. */
	static void ensure() {
		CityData d = Gz.city().data();
		if (d == built) {
			return;
		}
		built = d;
		ox = d.origin().getX();
		oz = d.origin().getZ();
		sx = Math.max(1, d.size().getX());
		sz = Math.max(1, d.size().getZ());
		blocked = new byte[sx * sz];
		for (CityData.Building b : d.buildings()) {
			if (b.min().getY() > d.groundY() + 2 || b.max().getY() < d.groundY() + 1) {
				continue; // elevated structures (bridges) do not block the street below
			}
			for (int z = b.min().getZ(); z <= b.max().getZ(); z++) {
				for (int x = b.min().getX(); x <= b.max().getX(); x++) {
					int i = (z - oz) * sx + (x - ox);
					if (x >= ox && z >= oz && x < ox + sx && z < oz + sz) {
						blocked[i] = 1;
					}
				}
			}
		}
	}

	static boolean inCity(int x, int z) {
		return x >= ox + 1 && z >= oz + 1 && x < ox + sx - 1 && z < oz + sz - 1;
	}

	static boolean footprint(int x, int z) {
		return !inCity(x, z) || blocked[(z - oz) * sx + (x - ox)] != 0;
	}

	static boolean solid(ServerLevel level, int x, int y, int z) {
		BlockState s = level.getBlockState(POS.set(x, y, z));
		return !s.isAir() && !s.getCollisionShape(level, POS).isEmpty();
	}

	static boolean passable(ServerLevel level, int x, int y, int z) {
		BlockState s = level.getBlockState(POS.set(x, y, z));
		return s.isAir() || s.getFluidState().isEmpty() && s.getCollisionShape(level, POS).isEmpty();
	}

	/** True when a collision shape is present at this world position (used for flying things). */
	static boolean solidAt(ServerLevel level, double x, double y, double z) {
		return solid(level, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	/**
	 * The floor level (feet y) a person at (x, z) would stand on when currently at integer level y: one block up, level,
	 * or up to two blocks down; {@link #NONE} when the cell is a wall, a building, a pit or water.
	 */
	static int stand(ServerLevel level, double px, double pz, int y) {
		int x = (int) Math.floor(px);
		int z = (int) Math.floor(pz);
		if (footprint(x, z)) {
			return NONE;
		}
		if (!level.hasChunkAt(POS.set(x, y, z))) {
			return NONE;
		}
		for (int yy = y + 1; yy >= y - 2; yy--) {
			if (passable(level, x, yy, z) && solid(level, x, yy - 1, z)) {
				return yy;
			}
		}
		return NONE;
	}
}
