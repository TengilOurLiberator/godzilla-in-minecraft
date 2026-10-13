package dev.godzilla.military;

import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaCommands;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * The military: tanks, helicopters, jets, warships and maser cannons that fire harmlessly at Godzilla, and the director that
 * escalates them. Entities are in {@code MilEntities}; the client side (models, renderers) is in dev.godzilla.client.military.
 */
public final class MilitaryModule {
	private MilitaryModule() {
	}

	public static void init() {
		MilEntities.register();
		ServerTickEvents.END_SERVER_TICK.register(Director::tick);
		GzEvents.RESET.register(Director::onReset);
		GzEvents.PHASE_CHANGED.register(Director::onPhase);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> Director.reset());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> Director.reset());
		GodzillaCommands.addSubcommands(MilCommands::add);
	}
}
