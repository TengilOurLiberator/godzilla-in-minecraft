package dev.godzilla.client.population;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.population.PersonEntity;
import dev.godzilla.population.PopulationEntities;
import dev.godzilla.population.VehicleEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/** Client-only initialisation of this module: renderers and model layers of the people and vehicles, and a frame-rate log. */
public final class PopulationClient {
	private static Field fpsField;
	private static int ticks;
	/** Nanoseconds spent in this module's renderers (extract + submit) since the last log line; for the frame-time report. */
	static long renderNanos;

	private PopulationClient() {
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(PersonRenderer.LAYER, PersonModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(VehicleRenderer.CAR_LAYER, VehicleModels.Car::layer);
		ModelLayerRegistry.registerModelLayer(VehicleRenderer.BUS_LAYER, VehicleModels.Bus::layer);
		ModelLayerRegistry.registerModelLayer(VehicleRenderer.TRUCK_LAYER, VehicleModels.Truck::layer);
		ModelLayerRegistry.registerModelLayer(VehicleRenderer.TRAIN_LAYER, VehicleModels.TrainCar::layer);
		EntityRenderers.register(PopulationEntities.PERSON, PersonRenderer::new);
		EntityRenderers.register(PopulationEntities.CAR, VehicleRenderer::new);
		EntityRenderers.register(PopulationEntities.BUS, VehicleRenderer::new);
		EntityRenderers.register(PopulationEntities.TRAIN_CAR, VehicleRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(PopulationClient::tick);
	}

	/** Every 5 seconds, while there is a crowd on screen: the frame rate and the number of entities, for the logs. */
	private static void tick(Minecraft mc) {
		if (mc.level == null || ++ticks % 100 != 0) {
			return;
		}
		int people = 0, vehicles = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof PersonEntity) {
				people++;
			} else if (e instanceof VehicleEntity) {
				vehicles++;
			}
		}
		if (people + vehicles < 20) {
			return;
		}
		int fps = -1;
		try {
			if (fpsField == null) {
				fpsField = Minecraft.class.getDeclaredField("fps");
				fpsField.setAccessible(true);
			}
			fps = fpsField.getInt(null);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// not important
		}
		double ms = renderNanos / 1e6;
		renderNanos = 0;
		GodzillaMod.LOGGER.info("GODZILLA-POP client fps={} people={} vehicles={} renderer CPU {} ms/s (= {} ms per frame at that fps)", fps, people, vehicles,
				String.format("%.1f", ms / 5.0), fps > 0 ? String.format("%.2f", ms / 5.0 / fps) : "?");
	}
}
