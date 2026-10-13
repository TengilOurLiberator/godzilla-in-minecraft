package dev.godzilla.client.player.mixin;

import dev.godzilla.client.player.PloughDrag;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A little drag while Godzilla ploughs through buildings (client side input only: the server never re-simulates travel). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClientMixin {
	@Inject(method = "travel", at = @At("TAIL"))
	private void godzilla$drag(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer p) {
			PloughDrag.apply(p);
		}
	}
}