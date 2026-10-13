package dev.godzilla.game;

import dev.godzilla.game.net.GameNet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Sound helpers. Music and interface sounds go to the client as {@link GameNet.Audio} (no position, so the 16x scale cannot
 * make them quiet, and the client can loop and fade them). World sounds are sent straight to the player as a sound packet:
 * the usual level.playSound only reaches players within 16 x volume blocks, which is far less than the 200 to 300 blocks the
 * sound definitions allow (the client then applies the attenuation_distance of sounds.json).
 */
public final class GameAudio {
	private GameAudio() {
	}

	public static void musicLoop(ServerPlayer p, String sound, float volume, int fadeIn) {
		ServerPlayNetworking.send(p, new GameNet.Audio(GameNet.Audio.MUSIC_LOOP, sound, volume, 1, fadeIn));
	}

	public static void musicOnce(ServerPlayer p, String sound, float volume, int fadeIn) {
		ServerPlayNetworking.send(p, new GameNet.Audio(GameNet.Audio.MUSIC_ONCE, sound, volume, 1, fadeIn));
	}

	/** Fades out one named track ("" = every music track). */
	public static void stopMusic(ServerPlayer p, String sound, int fadeTicks) {
		ServerPlayNetworking.send(p, new GameNet.Audio(GameNet.Audio.STOP, sound, 0, 1, fadeTicks));
	}

	public static void ui(ServerPlayer p, String sound, float volume, float pitch) {
		ServerPlayNetworking.send(p, new GameNet.Audio(GameNet.Audio.UI, sound, volume, pitch, 0));
	}

	/** A sound at a world position, heard by this player with the sound's own attenuation distance. */
	public static void at(ServerPlayer p, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source,
				pos.x, pos.y, pos.z, volume, pitch, p.level().getRandom().nextLong()));
	}
}
