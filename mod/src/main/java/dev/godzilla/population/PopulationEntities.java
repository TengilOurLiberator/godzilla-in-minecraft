package dev.godzilla.population;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types of the population module. All are plain {@link Entity} subclasses (no AI, no physics, never saved); the
 * simulation is driven by {@link Population} so that hundreds can be alive at once. Bounding boxes are a little generous so
 * that hand swipes and stomps catch them.
 */
public final class PopulationEntities {
	/** A civilian, 0.64 blocks tall. */
	public static final EntityType<PersonEntity> PERSON = register("person", PersonEntity::new, 0.6F, 0.95F, 10, 3);
	/** Cars, taxis, police cars (about 1.5 blocks long). */
	public static final EntityType<VehicleEntity> CAR = register("car", VehicleEntity::new, 1.7F, 1.25F, 12, 2);
	/** Buses and trucks (2 to 2.5 blocks long). */
	public static final EntityType<VehicleEntity> BUS = register("bus", VehicleEntity::new, 2.6F, 1.6F, 12, 2);
	/** One carriage of a train (3.5 blocks long). */
	public static final EntityType<VehicleEntity> TRAIN_CAR = register("train_car", VehicleEntity::new, 3.6F, 1.6F, 24, 1);

	private PopulationEntities() {
	}

	/** Touching this class runs the static initialiser above; call it from init(). */
	public static void register() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.EntityFactory<T> factory, float w, float h,
															 int trackingChunks, int updateInterval) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(factory, MobCategory.MISC).sized(w, h).clientTrackingRange(trackingChunks)
						.updateInterval(updateInterval).noSave().fireImmune().build(key));
	}
}
