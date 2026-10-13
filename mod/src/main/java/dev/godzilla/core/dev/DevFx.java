package dev.godzilla.core.dev;

import dev.godzilla.api.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Stand-in feedback: nothing. Replaced by dev.godzilla.player. */
public final class DevFx implements Fx {
	@Override
	public void shake(ServerPlayer player, float strength, int ticks) {
	}

	@Override
	public void hitFlash(ServerPlayer player, Vec3 from, float strength) {
	}
}
