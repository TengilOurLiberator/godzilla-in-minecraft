package dev.godzilla.destruction;

import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaCommands;
import dev.godzilla.core.GodzillaMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Common (server and client) initialisation of this module: smashing, debris, collapse, fire. */
public final class DestructionModule {
	private DestructionModule() {
	}

	public static void init() {
		Packets.register();
		Gz.setDestruction(new DestructionService());
		ServerTickEvents.START_SERVER_TICK.register(server -> DestructionService.startTick());
		ServerTickEvents.END_SERVER_TICK.register(DestructionModule::endTick);
		GzEvents.RESET.register(game -> reset());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Fires.sendAll(handler.getPlayer()));
		GodzillaCommands.addSubcommands(DestructionCommands::add);
		GodzillaMod.LOGGER.info("godzilla: destruction module installed");
	}

	private static void endTick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		long tick = level.getGameTime();
		Collapses.tick(server, tick);
		Fires.tick(tick);
		DestructionService.endTick(tick);
	}

	/** Forget everything of this run and tell the clients to drop all debris, masses and fires. */
	public static void reset() {
		Structures.reset();
		Collapses.reset();
		Fires.reset();
		DestructionService.resetStats();
		MinecraftServer server = Gz.game().level() != null ? Gz.game().level().getServer() : null;
		if (server != null) {
			Packets.Fx fx = new Packets.Fx(Packets.Fx.RESET, 0, 0, 0, 0, 0, 0);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(p, fx);
			}
		}
	}
}
