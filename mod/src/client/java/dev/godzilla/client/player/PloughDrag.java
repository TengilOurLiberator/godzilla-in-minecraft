package dev.godzilla.client.player;

import dev.godzilla.player.GodzillaGeometry;
import dev.godzilla.player.GodzillaState;
import dev.godzilla.player.PlayerConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Walking through a wall: the client samples the (not yet removed) building blocks around the legs and torso ahead and slows
 * his horizontal motion a little. Client only and applied to the input side of movement, so the server's re-simulation of the
 * reported position (which has no drag) always agrees with the client.
 */
public final class PloughDrag {
	private static final double[] HEIGHTS = {2, 6, 10, 15, 20};
	private static final double[] SIDES = {-3.0, 0.0, 3.0};

	/** Fraction of the maximum slowdown applied last tick (0..1), for debugging. */
	public static volatile float lastMass;

	private PloughDrag() {
	}

	public static void apply(LocalPlayer p) {
		if (!GodzillaState.isGodzilla(p) || !(p.level() instanceof ClientLevel level)) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		double hs = Math.hypot(v.x, v.z);
		if (hs < 0.02) {
			lastMass = 0;
			return;
		}
		double s = GodzillaGeometry.scaleOf(p.getScale());
		double yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
		Vec3 fwd = GodzillaGeometry.forward(yaw);
		Vec3 left = GodzillaGeometry.left(yaw);
		int solid = 0;
		int total = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (double h : HEIGHTS) {
			for (double side : SIDES) {
				total++;
				double x = p.getX() + (fwd.x * 3.0 + left.x * side) * s;
				double y = p.getY() + h * s;
				double z = p.getZ() + (fwd.z * 3.0 + left.z * side) * s;
				pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
				if (pos.getY() <= PlayerConfig.GROUND_Y) {
					continue;
				}
				BlockState st = level.getBlockState(pos);
				if (!st.isAir() && st.getFluidState().isEmpty()) {
					solid++;
				}
			}
		}
		float mass = solid / (float) total;
		lastMass = mass;
		if (mass > 0) {
			double f = 1.0 - 0.5 * Math.pow(mass, 0.7);
			p.setDeltaMovement(v.x * f, p.getDeltaMovement().y, v.z * f);
		}
	}
}