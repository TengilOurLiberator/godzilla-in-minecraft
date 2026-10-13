package dev.godzilla.client.player;

import dev.godzilla.player.GodzillaState;
import dev.godzilla.player.net.PlayerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Walking through a wall: the server reports how much mass the body carved this tick (StateS2C.plough, 0..1) and the client
 * slows his horizontal motion by up to 45 percent. Client side only and on the input side of movement, so the server's
 * re-simulation of the reported position (which has no drag) always agrees with what the client did.
 */
public final class PloughDrag {
	private PloughDrag() {
	}

	public static void apply(LocalPlayer p) {
		if (!GodzillaState.isGodzilla(p)) {
			return;
		}
		climb(p);
		Visual v = PlayerClient.visual(p.getId());
		PlayerNet.StateS2C st = v == null ? null : v.state;
		if (st == null || st.plough() < 0.02F) {
			return;
		}
		Vec3 m = p.getDeltaMovement();
		double f = 1.0 - 0.45 * Math.pow(st.plough(), 0.7);
		p.setDeltaMovement(m.x * f, m.y, m.z * f);
	}

	/**
	 * The quays of the bay are vertical sea walls up to 10 blocks high, far more than his step height: heave himself up them. While
	 * he pushes against a wall below the street level he rises at a steady speed (a fast lift, not a snap: no jump of the view).
	 */
	private static void climb(LocalPlayer p) {
		if (PlayerClient.rising() || !p.horizontalCollision || !p.input.hasForwardImpulse()) {
			return;
		}
		if (p.getY() > dev.godzilla.player.PlayerConfig.GROUND_Y + 0.4) {
			return;
		}
		Vec3 m = p.getDeltaMovement();
		p.setDeltaMovement(m.x, Math.max(m.y, 0.5), m.z);
	}
}
