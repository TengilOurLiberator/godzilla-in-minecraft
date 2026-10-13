package dev.godzilla.client.destruction;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;

/**
 * One billow of dust, smoke or fire: a big soft quad that expands, slows down, rises or sinks, changes colour from a hot start
 * to a cool end and fades out. Dust clouds, the fireball and the smoke columns are all made of these.
 */
final class GzPuff extends SingleQuadParticle {
	float baseSize, grow, buoyancy, drag;
	float r0, g0, b0, r1, g1, b1, a0;
	float glow;

	GzPuff(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TextureAtlasSprite sprite, int life, float size) {
		super(level, x, y, z, sprite);
		this.hasPhysics = false;
		this.lifetime = life;
		this.baseSize = size;
		this.quadSize = size;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.grow = 1.2f;
		this.drag = 0.92f;
		this.roll = this.oRoll = random.nextFloat() * 6.2831855f;
		this.a0 = 0.6f;
		setCol(0.7f, 0.66f, 0.6f, 0.7f, 0.66f, 0.6f);
	}

	void setCol(float ra, float ga, float ba, float rb, float gb, float bb) {
		r0 = ra;
		g0 = ga;
		b0 = ba;
		r1 = rb;
		g1 = gb;
		b1 = bb;
		this.rCol = ra;
		this.gCol = ga;
		this.bCol = ba;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		this.yd += buoyancy;
		this.xd *= drag;
		this.yd *= drag;
		this.zd *= drag;
		this.move(this.xd, this.yd, this.zd);
		this.roll += (this.xd + this.zd) * 0.2f;
		float f = (float) age / lifetime;
		this.rCol = r0 + (r1 - r0) * Math.min(1f, f * 1.6f);
		this.gCol = g0 + (g1 - g0) * Math.min(1f, f * 1.6f);
		this.bCol = b0 + (b1 - b0) * Math.min(1f, f * 1.6f);
		// quick fade-in, long fade-out
		float in = Math.min(1f, age / 3f);
		float out = f < 0.45f ? 1f : 1f - (f - 0.45f) / 0.55f;
		this.alpha = a0 * in * Math.max(0f, out);
	}

	@Override
	public float getQuadSize(float a) {
		float f = (age + a) / lifetime;
		float growth = 1f + grow * (1f - (1f - f) * (1f - f));
		return baseSize * growth;
	}

	@Override
	public int getLightCoords(float a) {
		if (glow > 0) {
			float f = (age + a) / lifetime;
			float g = Math.max(0f, glow * (1f - f * 1.8f));
			if (g > 0) {
				int base = super.getLightCoords(a);
				return LightCoordsUtil.addSmoothBlockEmission(base, g);
			}
		}
		return super.getLightCoords(a);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}
