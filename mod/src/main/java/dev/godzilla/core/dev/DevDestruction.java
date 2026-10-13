package dev.godzilla.core.dev;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Destruction;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.FastBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stand-in destruction: blocks simply vanish, entities are told they were smashed. No debris, no collapse, no fire.
 * Replaced by dev.godzilla.destruction.
 */
public final class DevDestruction implements Destruction {
	@Override
	public int smashSphere(ServerLevel level, Vec3 center, double radius, Vec3 impulse, Cause cause) {
		return smashCapsule(level, center, center, radius, impulse, cause);
	}

	@Override
	public int smashCapsule(ServerLevel level, Vec3 a, Vec3 b, double radius, Vec3 impulse, Cause cause) {
		AABB box = new AABB(a, b).inflate(radius);
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		int n = remove(level, box, (x, y, z) -> {
			Vec3 p = new Vec3(x + 0.5, y + 0.5, z + 0.5);
			double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
			return p.distanceToSqr(a.add(ab.scale(t))) <= radius * radius;
		});
		hitEntities(level, box, impulse, cause, 1);
		report(a.add(b).scale(0.5), n, cause);
		return n;
	}

	@Override
	public int smashBox(ServerLevel level, AABB box, Vec3 impulse, Cause cause) {
		int n = remove(level, box, (x, y, z) -> true);
		hitEntities(level, box, impulse, cause, 1);
		report(box.getCenter(), n, cause);
		return n;
	}

	@Override
	public int explode(ServerLevel level, Vec3 center, double radius, Cause cause) {
		AABB box = new AABB(center, center).inflate(radius);
		int n = remove(level, box, (x, y, z) -> center.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) <= radius * radius);
		for (Entity e : level.getEntities((Entity) null, box)) {
			if (e instanceof Smashable s && e.isAlive()) {
				Vec3 out = e.position().subtract(center);
				s.onSmashed(cause, out.lengthSqr() < 1e-6 ? new Vec3(0, 0.5, 0) : out.normalize().scale(0.8), 2);
			}
		}
		report(center, n, cause);
		return n;
	}

	@Override
	public void ignite(ServerLevel level, Vec3 center, double radius) {
	}

	@Override
	public float integrity(int buildingId) {
		return 1;
	}

	private interface Inside {
		boolean test(int x, int y, int z);
	}

	private static int remove(ServerLevel level, AABB box, Inside inside) {
		int floor = Gz.city().data().groundY() + 1; // never dig into the ground or the sea
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int y = Math.max(floor, (int) Math.floor(box.minY)); y <= (int) Math.floor(box.maxY); y++) {
			for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
				for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
					if (!inside.test(x, y, z)) {
						continue;
					}
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK)) {
						level.setBlock(pos, air, FastBlocks.FLAGS_CLIENTS_ONLY, 0);
						n++;
					}
				}
			}
		}
		return n;
	}

	private static void hitEntities(ServerLevel level, AABB box, Vec3 impulse, Cause cause, float power) {
		for (Entity e : level.getEntities((Entity) null, box)) {
			if (e instanceof Smashable s && e.isAlive()) {
				s.onSmashed(cause, impulse, power);
			}
		}
	}

	private static void report(Vec3 center, int n, Cause cause) {
		if (n > 0) {
			GzEvents.BLOCKS_SMASHED.fire(new GzEvents.BlocksSmashed(center, n, cause));
			Gz.game().addScore(Game.ScoreKind.BLOCKS, n, center);
		}
	}
}
