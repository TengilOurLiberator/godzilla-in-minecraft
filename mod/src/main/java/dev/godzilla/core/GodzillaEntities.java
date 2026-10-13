package dev.godzilla.core;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

public final class GodzillaEntities {
	public static final ResourceKey<EntityType<?>> TEST_DUMMY_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id("test_dummy"));

	/** createMob also registers the default attributes (Mob.createMobAttributes) for us. */
	public static final EntityType<TestDummy> TEST_DUMMY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, TEST_DUMMY_KEY,
			FabricEntityType.Builder.createMob(TestDummy::new, MobCategory.MISC, mob -> mob.defaultAttributes(Mob::createMobAttributes))
					.sized(1.5F, 3.0F)
					.eyeHeight(2.6F)
					.clientTrackingRange(16)
					.build(TEST_DUMMY_KEY));

	private GodzillaEntities() {
	}

	/** Touching this class runs the static initialiser above; call it from onInitialize. */
	public static void register() {
	}
}
