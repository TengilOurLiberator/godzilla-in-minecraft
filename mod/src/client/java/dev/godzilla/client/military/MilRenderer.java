package dev.godzilla.client.military;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.military.MaserCannon;
import dev.godzilla.military.MilitaryUnit;
import dev.godzilla.military.Warship;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws any of the five unit types: the box model (chosen by variant for ships), and the maser's charge glow and beam. */
public class MilRenderer<T extends MilitaryUnit> extends EntityRenderer<T, MilModel.State> {
	private final MilModel[] models;
	private final Identifier[] textures;
	private final float[] scales;
	private final GlowModel glow;
	private final boolean ship, maser;

	/** @param names model names (one, or three for the ship size classes) */
	public MilRenderer(EntityRendererProvider.Context ctx, boolean ship, boolean maser, String... names) {
		super(ctx);
		this.ship = ship;
		this.maser = maser;
		this.models = new MilModel[names.length];
		this.textures = new Identifier[names.length];
		this.scales = new float[names.length];
		for (int i = 0; i < names.length; i++) {
			MilModels.Info info = MilModels.info(names[i]);
			this.models[i] = new MilModel(ctx.bakeLayer(MilitaryClient.layer(names[i])), info);
			this.textures[i] = GodzillaMod.id("textures/mil/" + names[i] + ".png");
			this.scales[i] = info.scale();
		}
		this.glow = maser ? new GlowModel(ctx.bakeLayer(MilitaryClient.layer("glow_blue")), "glow_blue") : null;
	}

	@Override
	public MilModel.State createRenderState() {
		return new MilModel.State();
	}

	@Override
	public void extractRenderState(T entity, MilModel.State state, float partial) {
		super.extractRenderState(entity, state, partial);
		state.yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
		state.pitch = Mth.lerp(partial, entity.xRotO, entity.getXRot());
		state.roll = entity.rollDeg();
		state.turretYaw = entity.renderTurretYaw(partial);
		state.turretPitch = entity.renderTurretPitch(partial);
		state.spin = (entity.tickCount + partial) * 1.2F;
		state.flags = entity.hasFlag(MilitaryUnit.F_CHARGE) ? MilitaryUnit.F_CHARGE : 0;
		state.flags |= entity.hasFlag(MilitaryUnit.F_BEAM) ? MilitaryUnit.F_BEAM : 0;
		state.sinking = entity.hasFlag(MilitaryUnit.F_SINK);
		state.variant = Mth.clamp(entity.variant(), 0, this.models.length - 1);
		state.muzzle = null;
		state.beamEnd = null;
		if (this.maser && state.flags != 0) {
			Vec3 origin = new Vec3(state.x, state.y, state.z);
			state.muzzle = entity.muzzle().subtract(entity.position());
			if (entity instanceof MaserCannon m) {
				state.beamEnd = m.beamEnd().subtract(origin);
			}
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(T entity, float partial) {
		AABB b = super.getBoundingBoxForCulling(entity, partial);
		return this.maser && (entity.hasFlag(MilitaryUnit.F_BEAM) || entity.hasFlag(MilitaryUnit.F_CHARGE)) ? b.inflate(200) : b;
	}

	@Override
	public void submit(MilModel.State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int v = state.variant;
		float sc = this.scales[v];
		pose.pushPose();
		float roll = state.roll, pitch = state.pitch;
		if (this.ship && !state.sinking) {
			// riding the swell
			roll += (float) Math.sin(state.ageInTicks * 0.07 + state.x * 0.1) * 1.3F;
			pitch += (float) Math.sin(state.ageInTicks * 0.05 + state.z * 0.1) * 0.8F;
		}
		pose.rotateDegrees(Axis.YP, 180.0F - state.yaw);
		pose.rotateDegrees(Axis.ZP, -roll);
		pose.rotateDegrees(Axis.XP, -pitch);
		pose.scale(-sc, -sc, sc);
		pose.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(this.models[v], state, pose, this.textures[v], state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();

		if (this.glow != null && state.muzzle != null) {
			this.submitMaser(state, pose, collector);
		}
		super.submit(state, pose, collector, camera);
	}

	private void submitMaser(MilModel.State state, PoseStack pose, SubmitNodeCollector collector) {
		Vec3 m = state.muzzle;
		boolean beam = (state.flags & MilitaryUnit.F_BEAM) != 0 && state.beamEnd != null;
		float pulse = 0.55F + 0.45F * Mth.sin(state.ageInTicks * 0.9F);
		// the charging ball / muzzle glow
		float ball = beam ? 2.4F + 0.8F * pulse : 0.5F + 1.0F * pulse;
		this.glow.submitCube(pose, collector, m, ball);
		if (!beam) {
			return;
		}
		Vec3 end = state.beamEnd;
		long seed = (long) (state.ageInTicks * 1.5F);
		this.lightning(state, pose, collector, m, end, seed, 1.0F, 2.6F);
		this.lightning(state, pose, collector, m, end, seed + 977, 1.3F, 1.3F);
		this.lightning(state, pose, collector, m, end, seed + 31, 0.5F, 4.2F);
		this.glow.submitCube(pose, collector, end, 6.0F + 2.4F * pulse);
	}

	/** A jagged chain of glowing boxes from a to b; {@code wander} scales how far it strays, {@code width} in blocks. */
	private void lightning(MilModel.State state, PoseStack pose, SubmitNodeCollector collector, Vec3 a, Vec3 b, long seed, float wander, float width) {
		int n = 16;
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 dir = d.scale(1.0 / len);
		Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = dir.cross(side).normalize();
		Vec3 prev = a;
		for (int k = 1; k <= n; k++) {
			double f = k / (double) n;
			double env = Math.sin(Math.PI * f);
			double amp = (1.0 + 1.2 * wander) * env;
			double r1 = hash(seed, k * 2) - 0.5, r2 = hash(seed, k * 2 + 1) - 0.5;
			Vec3 p = a.add(d.scale(f)).add(side.scale(r1 * 2 * amp)).add(up.scale(r2 * 2 * amp));
			if (k == n) {
				p = b;
			}
			this.glow.submitSegment(pose, collector, prev, p, width);
			prev = p;
		}
	}

	private static double hash(long seed, int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
}
