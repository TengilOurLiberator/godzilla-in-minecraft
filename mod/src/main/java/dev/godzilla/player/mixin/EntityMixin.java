package dev.godzilla.player.mixin;

import dev.godzilla.player.GodzillaState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
	private void godzilla$noSwimming(CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (GodzillaState.isGodzilla(self)) {
			self.setSwimming(false);
			ci.cancel();
		}
	}
}
