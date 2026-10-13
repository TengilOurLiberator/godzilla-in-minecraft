package dev.godzilla.client.game;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The game's music on the client: streams that loop seamlessly (Minecraft can loop a sound instance even though a sound event
 * cannot loop), fade in and out, and sit on the listener (no position, so the 16x scale cannot make them quiet).
 */
public final class MusicPlayer {
	private static final Map<String, Track> TRACKS = new HashMap<>();

	private MusicPlayer() {
	}

	private static SoundEvent event(String key) {
		return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GodzillaMod.MOD_ID, key));
	}

	private static final class Track extends AbstractTickableSoundInstance {
		private float target;
		private float step;
		private boolean stopping;
		final String key;

		Track(String key, boolean loop, float volume, int fadeIn) {
			super(event(key), SoundSource.MUSIC, RandomSource.create());
			this.key = key;
			this.looping = loop;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.target = volume;
			this.volume = fadeIn > 0 ? 0f : volume;
			this.step = fadeIn > 0 ? volume / fadeIn : 1f;
		}

		void fadeOut(int ticks) {
			stopping = true;
			target = 0;
			step = ticks <= 0 ? 1f : Math.max(0.001f, volume / ticks);
		}

		@Override
		public void tick() {
			if (volume < target) {
				volume = Math.min(target, volume + step);
			} else if (volume > target) {
				volume = Math.max(target, volume - step);
			}
			if (stopping && volume <= 0.001f) {
				stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	public static void play(String key, boolean loop, float volume, int fadeIn) {
		Track old = TRACKS.remove(key);
		if (old != null) {
			old.fadeOut(4);
		}
		Track t = new Track(key, loop, volume, fadeIn);
		TRACKS.put(key, t);
		Minecraft.getInstance().getSoundManager().play(t);
	}

	/** Fades out one track, or all of them when key is empty. */
	public static void stop(String key, int fade) {
		if (key.isEmpty()) {
			for (Track t : TRACKS.values()) {
				t.fadeOut(fade);
			}
			TRACKS.clear();
		} else {
			Track t = TRACKS.remove(key);
			if (t != null) {
				t.fadeOut(fade);
			}
		}
	}

	public static void ui(String key, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(key), pitch, volume));
	}

	/** Called every client tick: keeps vanilla music quiet while the game's own music is on. */
	public static void tick() {
		if (!TRACKS.isEmpty()) {
			Minecraft.getInstance().getMusicManager().stopPlaying();
			TRACKS.values().removeIf(t -> t.isStopped());
		}
	}
}
