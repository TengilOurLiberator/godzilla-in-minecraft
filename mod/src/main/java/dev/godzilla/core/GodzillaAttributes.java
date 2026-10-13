package dev.godzilla.core;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Custom attributes. Registered in the common initialiser (both sides need them; setSyncable(true) sends the value to the
 * client). Adding one to players goes through FabricDefaultAttributeRegistry.MODIFY because player attributes are not
 * registered one by one.  Check on a server: /attribute Tester godzilla:atomic_power get
 */
public final class GodzillaAttributes {
	public static final Holder<Attribute> ATOMIC_POWER = Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, GodzillaMod.id("atomic_power"),
			new RangedAttribute("attribute.name.godzilla.atomic_power", 1.0, 0.0, 100.0).setSyncable(true));

	private GodzillaAttributes() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.MODIFY.register(context ->
				context.modify(EntityTypes.PLAYER, (type, builder) -> builder.add(ATOMIC_POWER)));
	}
}
