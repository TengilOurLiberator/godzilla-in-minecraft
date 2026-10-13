package dev.godzilla.player;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "Is this entity Godzilla right now" for code that runs on both sides (mixins, renderers). It is read from the attribute
 * modifier that {@code GodzillaServer} puts on the scale attribute: attribute modifiers are synced to clients, so client and
 * server always agree, and it cannot drift from the scale itself.
 */
public final class GodzillaState {
	public static final Identifier SCALE_ID = GodzillaMod.id("godzilla_scale");
	public static final Identifier SPEED_ID = GodzillaMod.id("godzilla_speed");
	public static final Identifier STEP_ID = GodzillaMod.id("godzilla_step");
	public static final Identifier JUMP_ID = GodzillaMod.id("godzilla_jump");
	public static final Identifier FALL_ID = GodzillaMod.id("godzilla_fall");
	public static final Identifier CAMERA_ID = GodzillaMod.id("godzilla_camera");

	private GodzillaState() {
	}

	public static boolean isGodzilla(Entity entity) {
		if (entity instanceof LivingEntity living) {
			AttributeInstance scale = living.getAttribute(Attributes.SCALE);
			return scale != null && scale.hasModifier(SCALE_ID);
		}
		return false;
	}
}
