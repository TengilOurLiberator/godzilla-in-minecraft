package dev.godzilla.client.military;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.military.MilShot;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Shells, missiles, rockets and tracers: a glowing streak along the velocity (missiles are a real little rocket model). */
public class MilShotRenderer extends EntityRenderer<MilShot, MilModel.State> {
	private static final Identifier MISSILE_TEX = GodzillaMod.id("textures/mil/missile.png");
	private final GlowModel glow;
	private final MilModel missile;

	public MilShotRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.glow = new GlowModel(ctx.bakeLayer(MilitaryClient.layer("glow")), "glow");
		this.missile = new MilModel(ctx.bakeLayer(MilitaryClient.layer("missile")), MilModels.info("missile"));
	}

	@Override
	public MilModel.State createRenderState() {
		return new MilModel.State();
	}

	@Override
	public void extractRenderState(MilShot entity, MilModel.State state, float partial) {
		super.extractRenderState(entity, state, partial);
		state.kind = entity.kind();
		state.yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
		state.pitch = Mth.lerp(partial, entity.xRotO, entity.getXRot());
		state.lightCoords = 15728880;
	}

	@Override
	public void submit(MilModel.State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		switch (s.kind) {
			case MilShot.SHELL -> this.glow.submitStreak(pose, collector, s.yaw, s.pitch, 1.4F, 0.35F);
			case MilShot.SHIP_SHELL -> this.glow.submitStreak(pose, collector, s.yaw, s.pitch, 3.0F, 0.7F);
			case MilShot.ROCKET -> this.glow.submitStreak(pose, collector, s.yaw, s.pitch, 1.3F, 0.3F);
			case MilShot.TRACER -> this.glow.submitStreak(pose, collector, s.yaw, s.pitch, 2.6F, 0.2F);
			default -> {
				pose.pushPose();
				pose.rotateDegrees(Axis.YP, -s.yaw);
				pose.rotateDegrees(Axis.XP, s.pitch);
				pose.rotateDegrees(Axis.YP, 180.0F);
				pose.scale(-1.7F, -1.7F, 1.7F);
				collector.submitModel(this.missile, s, pose, MISSILE_TEX, 15728880, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
				this.glow.submitStreak(pose, collector, s.yaw, s.pitch, 0.9F, 0.4F);
			}
		}
		super.submit(s, pose, collector, camera);
	}
}
