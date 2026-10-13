package dev.godzilla.client.player.mixin;

import dev.godzilla.client.player.PlayerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flat-client camera shake (VR gets controller rumble instead; shakeOffsets is zero there). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "update", at = @At("RETURN"))
	private void godzilla$shake(DeltaTracker deltaTracker, CallbackInfo ci) {
		float[] o = PlayerClient.shakeOffsets(deltaTracker.getGameTimeDeltaPartialTick(false));
		if (o[0] != 0 || o[1] != 0 || o[2] != 0 || o[3] != 0 || o[4] != 0) {
			this.setRotation(this.yRot + o[0], this.xRot + o[1]);
			this.setPosition(this.position.add(o[2], o[3], o[4]));
		}
	}
}