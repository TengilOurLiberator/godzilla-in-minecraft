package dev.godzilla.client.player.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.godzilla.client.player.PlayerClient;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The local player is drawn in first person too while he is Godzilla (the vanilla player is hidden from his own camera). */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
	private boolean godzilla$drawSelf(boolean detached) {
		return detached || PlayerClient.localIsGodzilla();
	}
}