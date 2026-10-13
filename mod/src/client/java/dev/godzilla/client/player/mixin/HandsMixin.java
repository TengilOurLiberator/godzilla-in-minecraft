package dev.godzilla.client.player.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.godzilla.client.player.PlayerClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No vanilla first-person arm or held item for Godzilla; the body model has the arms. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void godzilla$noHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
								  FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (PlayerClient.localIsGodzilla()) {
			ci.cancel();
		}
	}
}