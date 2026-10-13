package dev.godzilla.game;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Gz;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The waiting platform: a small pier on the sea above the spot where the run starts (the bay floor, 35 blocks down). A player who
 * joins stands here at normal size, with the start sign floating in front of him. Starting a run restamps the city, which puts
 * the water back and removes the pier, so it needs no cleanup; {@link #ensure} rebuilds it whenever the game goes idle.
 */
public final class Lobby {
	/** Deck half size: 25 x 25 blocks. */
	private static final int HALF = 12;
	/** The deck's y: the block above the sea surface (water fills up to y=62). */
	public static final int DECK_Y = 63;

	private Lobby() {
	}

	public static Vec3 spot() {
		CityData.Start st = Gz.city().data().playerStart();
		return new Vec3(Math.floor(st.pos().x) + 0.5, DECK_Y + 1, Math.floor(st.pos().z) + 0.5);
	}

	public static float yaw() {
		return Gz.city().data().playerStart().yaw();
	}

	/** Where the start sign hangs: eight blocks ahead of the spot, so its near face is within an arm's reach of a standing player. */
	public static Vec3 signCenter() {
		Vec3 s = spot();
		double yaw = Math.toRadians(yaw());
		return new Vec3(s.x - Math.sin(yaw) * 8, DECK_Y + 3.2, s.z + Math.cos(yaw) * 8);
	}

	public static void ensure(ServerLevel level) {
		Vec3 c = spot();
		int cx = (int) Math.floor(c.x);
		int cz = (int) Math.floor(c.z);
		BlockState deck = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
		BlockState trim = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
		BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState wall = Blocks.STONE_BRICK_WALL.defaultBlockState();
		BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dz = -HALF; dz <= HALF; dz++) {
			for (int dx = -HALF; dx <= HALF; dx++) {
				boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				boolean stripe = Math.abs(dx) <= 1 && dz < 0; // an arrow of dark blocks pointing at the sign and the city
				set(level, p.set(cx + dx, DECK_Y, cz + dz), edge ? trim : stripe ? dark : deck);
				if (edge && (dx + dz) % 2 != 0) {
					set(level, p.set(cx + dx, DECK_Y + 1, cz + dz), wall);
				}
			}
		}
		for (int sx : new int[]{-HALF, HALF}) {
			for (int sz : new int[]{-HALF, HALF}) {
				for (int y = DECK_Y - 1; y > 28; y--) {
					set(level, p.set(cx + sx, y, cz + sz), dark);
				}
				for (int y = DECK_Y + 1; y <= DECK_Y + 5; y++) {
					set(level, p.set(cx + sx, y, cz + sz), y == DECK_Y + 5 ? lamp : dark);
				}
			}
		}
		level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
				net.minecraft.core.GlobalPos.of(level.dimension(), BlockPos.containing(c)), yaw(), 0.0F));
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		if (!level.getBlockState(pos).equals(state)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	public static void enter(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		ensure(level);
		Vec3 s = spot();
		p.teleportTo(level, s.x, s.y, s.z, Set.of(), yaw(), 0.0F, true);
		p.setDeltaMovement(Vec3.ZERO);
		p.fallDistance = 0;
	}
}
