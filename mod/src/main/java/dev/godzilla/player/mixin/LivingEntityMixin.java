package dev.godzilla.player.mixin;

import dev.godzilla.player.GodzillaState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void godzilla$noJump(CallbackInfo ci) {
		if (GodzillaState.isGodzilla((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
