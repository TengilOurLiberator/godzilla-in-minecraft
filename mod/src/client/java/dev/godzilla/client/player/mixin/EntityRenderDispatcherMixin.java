package dev.godzilla.client.player.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.godzilla.client.player.GodzillaRenderer;
import dev.godzilla.client.player.PlayerClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the whole vanilla (or Vivecraft) player drawing, shadow and held items by the Godzilla renderer. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void godzilla$submit(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
								 SubmitNodeCollector collector, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && PlayerClient.isGodzillaId(avatar.id)) {
			GodzillaRenderer.submit(avatar, x, y, z, poseStack, collector);
			ci.cancel();
		}
	}
}