package dev.godzilla.client.population;

import dev.godzilla.population.PopulationEntities;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only initialisation of this module: renderers and model layers of the people and vehicles. */
public final class PopulationClient {
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
	}
}
