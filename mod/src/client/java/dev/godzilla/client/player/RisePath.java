package dev.godzilla.client.player;

import dev.godzilla.api.Game;
import dev.godzilla.player.GodzillaGeometry;
import dev.godzilla.player.PlayerConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The scripted rise. The client moves the player each tick along a smooth path (forward only, slowly accelerating), which is
 * the only way to get perfectly smooth motion without a server teleport per tick; the server accepts client positions as always.
 * Height: the feet follow the sea floor, but never lower than a lift curve that brings the eyes to the water surface exactly at
 * {@link Game#RISE_SURFACE_TICK} (on a flat bay floor that means he is carried up; on the real slope the floor does the work).
 */
final class RisePath {
	/** Eye height above the water surface plane at the surface beat (sea surface = top of block y 62 = 63.0, water height ~0.89). */
	static final double SURFACE_EYE_Y = 63.3;

	private RisePath() {
	}

	static double distance(int t) {
		return PlayerConfig.RISE_FORWARD_SPEED * (t - 50.0 * (1.0 - Math.exp(-t / 50.0)));
	}

	static double lift(double y0, double ySurface, int t) {
		if (t <= Game.RISE_SURFACE_TICK) {
			double u = t / (double) Game.RISE_SURFACE_TICK;
			return y0 + (ySurface - y0) * u * u;
		}
		return ySurface + 4.0 * (1.0 - Math.exp(-(t - Game.RISE_SURFACE_TICK) / 60.0));
	}

	static double groundHeight(ClientLevel level, double x, double z, double fromY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int y = (int) Math.floor(fromY); y > level.getMinY(); y--) {
			pos.set(bx, y, bz);
			BlockState st = level.getBlockState(pos);
			if (!st.getCollisionShape(level, pos).isEmpty()) {
				return y + st.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return level.getMinY();
	}

	static void apply(LocalPlayer p, Vec3 start, float yaw, int t) {
		Vec3 fwd = GodzillaGeometry.forward(yaw);
		double d = distance(t);
		double x = start.x + fwd.x * d;
		double z = start.z + fwd.z * d;
		double eye = p.getEyeHeight();
		double ySurface = SURFACE_EYE_Y - eye;
		double y = lift(start.y, ySurface, t);
		// terrain below the path may push him higher after the surface beat, never before (the beat must stay on the music)
		if (t > Game.RISE_SURFACE_TICK) {
			double g = groundHeight((ClientLevel) p.level(), x, z, PlayerConfig.GROUND_Y + 1);
			double k = Math.min(1.0, (t - Game.RISE_SURFACE_TICK) / 50.0);
			k = k * k * (3 - 2 * k);
			y += Math.max(0, g - y) * k;
		}
		p.setPos(x, y, z);
		p.setDeltaMovement(Vec3.ZERO);
		p.fallDistance = 0;
	}
}
