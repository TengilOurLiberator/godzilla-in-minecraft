package dev.godzilla.player.mixin;

import dev.godzilla.player.GodzillaState;
import dev.godzilla.player.PlayerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Godzilla only collides with terrain (blocks at or below the city's ground level). Everything above is ignored, on client and
 * server alike, so client prediction and the server's movement check agree and no wall can stop or stutter him; the server
 * carves whatever his body overlaps (GodzillaServer.feetAndBody).
 */
@Mixin(EntityCollisionContext.class)
public abstract class CollisionContextMixin {
	@Shadow
	@Final
	private @Nullable Entity entity;

	@Unique
	private byte godzilla$state;

	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void godzilla$ignoreBuildings(BlockState state, CollisionGetter getter, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		if (pos.getY() <= PlayerConfig.GROUND_Y || this.entity == null) {
			return;
		}
		if (this.godzilla$state == 0) {
			this.godzilla$state = (byte) (GodzillaState.isGodzilla(this.entity) ? 1 : 2);
		}
		if (this.godzilla$state == 1) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
