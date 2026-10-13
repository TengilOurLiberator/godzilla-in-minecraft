package dev.godzilla.client.player.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.godzilla.client.player.PlayerClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.render.renderstates.VRRenderState;

/** Hides Vivecraft's own controller/hand models while Godzilla (our body has the claws on the controllers). Only active with Vivecraft. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VRArmHelper")
public abstract class VrArmHelperMixin {
	@Inject(method = "renderVRHands", at = @At("HEAD"), cancellable = true, require = 0)
	private static void godzilla$noViveHands(SubmitNodeCollector output, VRRenderState vrState, CameraRenderState cameraState, PlayerRenderState playerState,
											 PoseStack poseStack, boolean renderMain, boolean renderOff, boolean menuHandMain, boolean menuHandOff,
											 int order, CallbackInfoReturnable<Integer> cir) {
		if (PlayerClient.localIsGodzilla() && !menuHandMain && !menuHandOff) {
			cir.setReturnValue(order);
		}
	}
}