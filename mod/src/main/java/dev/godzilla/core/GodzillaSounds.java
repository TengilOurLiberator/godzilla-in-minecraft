package dev.godzilla.core;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events. The event id must match a key in assets/godzilla/sounds.json (written by audio/ into the jar), e.g. the
 * key "godzilla.roar" is the event godzilla:godzilla.roar. Server code plays them with level.playSound(null, pos, EVENT, SoundSource.X, vol, pitch);
 * on a test client: /playsound godzilla:godzilla.roar master @s.
 */
public final class GodzillaSounds {
	public static final SoundEvent ROAR = register("godzilla.roar");

	private GodzillaSounds() {
	}

	private static SoundEvent register(String id) {
		// createVariableRangeEvent: audible range depends on the volume argument. createFixedRangeEvent(id, blocks) pins it.
		return Registry.register(BuiltInRegistries.SOUND_EVENT, GodzillaMod.id(id), SoundEvent.createVariableRangeEvent(GodzillaMod.id(id)));
	}

	public static void register() {
	}
}
