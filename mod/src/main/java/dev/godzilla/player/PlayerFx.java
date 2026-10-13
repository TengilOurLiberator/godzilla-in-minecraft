package dev.godzilla.player;

import dev.godzilla.api.Fx;
import dev.godzilla.player.net.PlayerNet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** The real {@link Fx}: forwards shake and hit flashes to the Godzilla player's client, which turns them into camera shake (flat) or rumble (VR). */
public final class PlayerFx implements Fx {
	@Override
	public void shake(ServerPlayer player, float strength, int ticks) {
		if (strength <= 0 || ticks <= 0) {
			return;
		}
		ServerPlayNetworking.send(player, new PlayerNet.FxS2C(Math.min(1.0F, strength), Math.min(ticks, 200)));
	}

	@Override
	public void hitFlash(ServerPlayer player, Vec3 from, float strength) {
		ServerPlayNetworking.send(player, new PlayerNet.FlashS2C(from, Math.min(1.0F, strength)));
	}
}
