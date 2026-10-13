package dev.godzilla.military;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The six entity types of the military module. None are saved, none are summonable by accident (no mob spawning). */
public final class MilEntities {
	public static final EntityType<Tank> TANK = reg("mil_tank", Tank::new, 2.4F, 1.3F, 14, 2);
	public static final EntityType<Helicopter> HELI = reg("mil_heli", Helicopter::new, 2.8F, 1.8F, 20, 1);
	public static final EntityType<Jet> JET = reg("mil_jet", Jet::new, 3.0F, 1.4F, 24, 1);
	public static final EntityType<Warship> SHIP = reg("mil_ship", Warship::new, 6.0F, 4.0F, 24, 3);
	public static final EntityType<MaserCannon> MASER = reg("mil_maser", MaserCannon::new, 2.8F, 2.4F, 14, 2);
	public static final EntityType<MilShot> SHOT = reg("mil_shot", MilShot::new, 0.5F, 0.5F, 24, 1);

	private MilEntities() {
	}

	private static <T extends Entity> EntityType<T> reg(String name, EntityType.EntityFactory<T> factory, float w, float h, int trackChunks,
														int updateInterval) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(factory, MobCategory.MISC)
				.sized(w, h).clientTrackingRange(trackChunks).updateInterval(updateInterval).noSave().fireImmune().build(key));
	}

	/** Touching this class runs the static initialiser above. */
	public static void register() {
	}
}
