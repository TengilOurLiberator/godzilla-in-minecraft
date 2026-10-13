package dev.godzilla.city;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaCommands;
import dev.godzilla.core.GodzillaMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelData;

/**
 * Common (server and client) initialisation of this module: the real City service (see {@link CityImpl}), stamping the city once
 * when a world has not got it yet, and /godzilla city info.
 */
public final class CityModule {
	private static final CityImpl CITY = new CityImpl();

	private CityModule() {
	}

	public static void init() {
		Gz.setCity(CITY);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			CityFile f = CITY.file();
			if (f == null || !f.usable()) {
				GodzillaMod.LOGGER.error("godzilla city: no usable city file ({}); the world is left as it is", CITY.fileError());
				return;
			}
			if (CITY.markerMatches(server)) {
				// same world, same city file: it is already in the region files. Only make sure the chunks stay loaded and the spawn is set.
				CITY.adoptMarker();
				CityData d = CITY.data();
				int cx0 = Math.floorDiv(f.originX, 16), cx1 = Math.floorDiv(f.originX + f.sizeX - 1, 16);
				int cz0 = Math.floorDiv(f.originZ, 16), cz1 = Math.floorDiv(f.originZ + f.sizeZ - 1, 16);
				for (int cz = cz0; cz <= cz1; cz++) {
					for (int cx = cx0; cx <= cx1; cx++) {
						level.setChunkForced(cx, cz, true);
					}
				}
				level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(level.dimension(), BlockPos.containing(d.playerStart().pos())),
						Mth.wrapDegrees(d.playerStart().yaw()), 0.0F));
				GodzillaMod.LOGGER.info("godzilla city: this world already holds city {}; not restamping (use /godzilla stamp)", f.hash);
			} else {
				GodzillaMod.LOGGER.info("godzilla city: this world does not hold city {} yet; stamping", f.hash);
				CITY.stamp(level);
			}
		});

		GodzillaCommands.addSubcommands((root, context) -> root.then(Commands.literal("city")
				.then(Commands.literal("lightsum").executes(c -> {
					String s = CITY.lightChecksum(c.getSource().getLevel());
					c.getSource().sendSuccess(() -> Component.literal(s), false);
					return 1;
				}))
				// debug: forget the cached light and stamp again (lights the whole city from scratch)
				.then(Commands.literal("relight").executes(c -> {
					CITY.dropLightCache();
					CITY.stamp(c.getSource().getLevel());
					c.getSource().sendSuccess(() -> Component.literal(CITY.lastStampSummary), false);
					return 1;
				}))
				// debug: /godzilla city light <pos>   sky and block light the server has there (and the blockstate)
				.then(Commands.literal("light").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
					ServerLevel level = c.getSource().getLevel();
					BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
					var le = level.getChunkSource().getLightEngine();
					var layer = le.getLayerListener(LightLayer.SKY).getDataLayerData(net.minecraft.core.SectionPos.of(p));
					String raw = layer == null ? "no sky layer" : "raw sky " + layer.get(p.getX() & 15, p.getY() & 15, p.getZ() & 15);
					c.getSource().sendSuccess(() -> Component.literal("%s: sky %d, block %d, %s, %s".formatted(p.toShortString(),
							level.getBrightness(LightLayer.SKY, p), level.getBrightness(LightLayer.BLOCK, p), level.getBlockState(p), raw)), false);
					return 1;
				})))
				.then(Commands.literal("info").executes(c -> {
					CityData d = CITY.data();
					CityFile f = CITY.file();
					Runtime rt = Runtime.getRuntime();
					String lines = "city: %d x %d x %d blocks at origin (%d, %d, %d), %d buildings (%d landmarks), seaLevel %d, groundY %d%n"
							.formatted(d.size().getX(), d.size().getY(), d.size().getZ(), d.origin().getX(), d.origin().getY(), d.origin().getZ(),
									d.buildings().size(), d.buildings().stream().filter(CityData.Building::landmark).count(), d.seaLevel(), d.groundY())
							+ (f == null ? "gzc: " + CITY.fileError() + "%n".formatted()
							: "gzc: palette %d entries, %d cells, %d MB in memory, md5 %s, %s%n".formatted(f.palette.length, f.cellCount(), f.memoryBytes() >> 20,
							f.hash, f.usable() ? "usable" : "NOT usable: " + String.join("; ", f.errors)))
							+ "stamped: %s; last stamp: %s%n".formatted(CITY.stamped(), CITY.lastStampSummary)
							+ "light cache: %s (%d MB); heap %d / %d MB".formatted(CITY.lightCached() ? "yes" : "no", CITY.lightCacheBytes() >> 20,
							(rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
					c.getSource().sendSuccess(() -> Component.literal(lines), false);
					return 1;
				}))));
	}
}
