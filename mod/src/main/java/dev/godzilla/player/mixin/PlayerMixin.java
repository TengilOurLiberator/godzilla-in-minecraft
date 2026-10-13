package dev.godzilla.player.mixin;

import dev.godzilla.player.GodzillaState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Godzilla walks on the sea floor: fluids do not carry or float him, he never swims, crouches or jumps. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "isAffectedByFluids", at = @At("HEAD"), cancellable = true)
	private void godzilla$notAffectedByFluids(CallbackInfoReturnable<Boolean> cir) {
		if (GodzillaState.isGodzilla((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void godzilla$alwaysStanding(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (GodzillaState.isGodzilla(self)) {
			self.setPose(Pose.STANDING);
			ci.cancel();
		}
	}
}
