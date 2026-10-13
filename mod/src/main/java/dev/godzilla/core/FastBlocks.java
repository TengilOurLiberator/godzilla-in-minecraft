package dev.godzilla.core;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Reference implementations of bulk block writes on the server, with the cost of each (measured, see docs/API-NOTES.md).
 * Both fill an inclusive box with one state. The city stamper should use {@link #fillBoxSections} style writes.
 */
public final class FastBlocks {
	/** Update clients, but no neighbour updates, no shape updates, no onPlace, no block-entity side effects, no drops. */
	public static final int FLAGS_CLIENTS_ONLY = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

	private FastBlocks() {
	}

	/** Per-block Level.setBlock with explicit flags. Simple, light engine and chunk tracking stay correct. Returns blocks changed. */
	public static long fillBoxSetBlock(ServerLevel level, BlockPos a, BlockPos b, BlockState state, int flags) {
		long changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Math.min(a.getY(), b.getY()); y <= Math.max(a.getY(), b.getY()); y++) {
			for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
				for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
					if (level.setBlock(pos.set(x, y, z), state, flags, 0)) {
						changed++;
					}
				}
			}
		}
		return changed;
	}

	/**
	 * Writes straight into the chunk sections, then resends each touched chunk (blocks + light as it is) to the players that
	 * track it. Does NOT touch the light engine unless {@code relight} is set (then every changed block is queued with
	 * LightEngine.checkBlock, which is the expensive part), does not run any block logic, does not notify clients block by block.
	 * Chunks must be loaded. Returns blocks written.
	 */
	public static long fillBoxSections(ServerLevel level, BlockPos a, BlockPos b, BlockState state, boolean relight) {
		int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
		long written = 0;
		var lightEngine = level.getChunkSource().getLightEngine();
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunk(cx, cz);
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
					int x0 = Math.max(minX, cx << 4) & 15, x1 = Math.min(maxX, (cx << 4) + 15) & 15;
					int z0 = Math.max(minZ, cz << 4) & 15, z1 = Math.min(maxZ, (cz << 4) + 15) & 15;
					int y0 = Math.max(minY, sy << 4) & 15, y1 = Math.min(maxY, (sy << 4) + 15) & 15;
					section.acquire();
					try {
						for (int y = y0; y <= y1; y++) {
							for (int z = z0; z <= z1; z++) {
								for (int x = x0; x <= x1; x++) {
									section.setBlockState(x, y, z, state, false);
									written++;
									if (relight) {
										lightEngine.checkBlock(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
									}
								}
							}
						}
					} finally {
						section.release();
					}
				}
				Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
				chunk.markUnsaved();
				resend(level, chunk);
			}
		}
		return written;
	}

	/** Sends the chunk (blocks and current light data) to every player that tracks it. */
	public static void resend(ServerLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		var packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getChunkSource().getLightEngine(), null, null);
		for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
			player.connection.send(packet);
		}
	}
}
