package dev.godzilla.client.player;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * A looping sound that follows a position and stops when its owner says so (the audio files are seamless loops; Minecraft
 * loops them with the looping flag). {@code where} returns null to fade out and stop.
 */
final class LoopSound extends AbstractTickableSoundInstance {
	private final Supplier<Vec3> where;
	private final Supplier<Float> level;
	private boolean ending;

	LoopSound(SoundEvent event, SoundSource source, boolean global, Supplier<Vec3> where, Supplier<Float> level) {
		super(event, source, RandomSource.create());
		this.looping = true;
		this.where = where;
		this.level = level;
		this.volume = 0.001F;
		if (global) {
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}
		Vec3 p = where.get();
		if (p != null) {
			this.x = p.x;
			this.y = p.y;
			this.z = p.z;
		}
	}

	void end() {
		ending = true;
	}

	@Override
	public void tick() {
		Vec3 p = where.get();
		if (p != null) {
			this.x = p.x;
			this.y = p.y;
			this.z = p.z;
		}
		float target = ending || p == null ? 0.0F : level.get();
		this.volume += (target - this.volume) * 0.35F;
		if ((ending || p == null) && this.volume < 0.02F) {
			stop();
		}
	}
}
