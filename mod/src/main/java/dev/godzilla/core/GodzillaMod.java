package dev.godzilla.core;

import dev.godzilla.core.net.GodzillaNetworking;
import dev.godzilla.vr.Vr;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (server and client) initialisation. */
public class GodzillaMod implements ModInitializer {
	public static final String MOD_ID = "godzilla";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Vr.init();
		GodzillaSounds.register();
		GodzillaAttributes.register();
		GodzillaEntities.register();
		GodzillaNetworking.registerCommon();
		CommandRegistrationCallback.EVENT.register(GodzillaCommands::register);

		// Daylight forever, no weather, no mob spawning: the world is a stage.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GameRules rules = server.getGameRules();
			rules.set(GameRules.ADVANCE_TIME, false, server);
			rules.set(GameRules.ADVANCE_WEATHER, false, server);
			rules.set(GameRules.SPAWN_MOBS, false, server);
			rules.set(GameRules.SPAWN_MONSTERS, false, server);
			rules.set(GameRules.SPAWN_PATROLS, false, server);
			rules.set(GameRules.SPAWN_PHANTOMS, false, server);
			rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
			rules.set(GameRules.SPAWN_WARDENS, false, server);
			// time is clock/time-marker based in 26.x; the vanilla command is the simplest verified way to move it
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
			LOGGER.info("godzilla: gamerules set (no time, weather or natural spawning); vivecraft present = {}", Vr.available());
		});
		LOGGER.info("godzilla: common init done");
	}
}
