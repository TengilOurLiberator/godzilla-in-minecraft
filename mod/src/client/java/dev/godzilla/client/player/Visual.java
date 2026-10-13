package dev.godzilla.client.player;

import dev.godzilla.player.BodyMotion;
import dev.godzilla.player.GodzillaGeometry;
import dev.godzilla.player.net.PlayerNet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Client-side animation state of one Godzilla player, advanced once per client tick. */
final class Visual {
	final int entityId;
	final boolean local;
	final BodyMotion motion = new BodyMotion();
	PlayerNet.@Nullable StateS2C state;
	int ticksSinceState;
	float mouth;
	float mouthPrev;
	float glow;
	float glowPrev;
	int swipeLeft = -1;
	int swipeRight = -1;
	long age;
	boolean wasFiring;
	int beamTicks;
	/** Beam length as seen last tick, smoothed. */
	float beamLen;

	Visual(int entityId, boolean local) {
		this.entityId = entityId;
		this.local = local;
	}

	boolean flag(int f) {
		return state != null && state.has(f);
	}

	void tick(AbstractClientPlayer p, float headYaw) {
		age++;
		ticksSinceState++;
		double dx = p.getX() - p.xo;
		double dz = p.getZ() - p.zo;
		boolean ground = p.onGround() || Math.abs(p.getY() - p.yo) < 0.3;
		motion.tick(headYaw, dx, dz, ground, p.getScale());
		mouthPrev = mouth;
		glowPrev = glow;
		float mt = 0;
		float gt = 0;
		if (state != null) {
			if (state.roarTicks() > 0) {
				// open wide at the start of the roar, close slowly at the end
				mt = Mth.clamp(state.roarTicks() / 12.0F, 0, 1) * 1.0F;
			} else if (state.has(PlayerNet.StateS2C.FIRING)) {
				mt = 0.8F;
			} else if (state.has(PlayerNet.StateS2C.CHARGING)) {
				mt = 0.25F + 0.55F * state.charge();
			}
			gt = state.has(PlayerNet.StateS2C.FIRING) ? 1.0F : state.has(PlayerNet.StateS2C.CHARGING) ? state.charge() : 0.0F;
			// plates keep a faint glow while the gauge is not full: they show the energy
			if (gt == 0 && state.gauge() < 0.999F && state.has(PlayerNet.StateS2C.ATTACKS)) {
				gt = state.has(PlayerNet.StateS2C.LOCKOUT) ? 0.0F : 0.1F * state.gauge();
			}
			swipeLeft = state.swipeLeft();
			swipeRight = state.swipeRight();
			wasFiring = state.has(PlayerNet.StateS2C.FIRING);
		}
		mouth += (mt - mouth) * 0.5F;
		glow += (gt - glow) * (gt > glow ? 0.35F : 0.15F);
		float target = state != null && state.has(PlayerNet.StateS2C.FIRING) ? state.beamLength() : 0;
		beamLen += (target - beamLen) * 0.6F;
		if (target > 0) {
			beamTicks++;
		} else {
			beamTicks = 0;
		}
	}

	double scaleRatio(double entityScale) {
		return GodzillaGeometry.scaleOf(entityScale);
	}
}
