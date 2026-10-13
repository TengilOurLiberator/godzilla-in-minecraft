package dev.godzilla.client.mothra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.godzilla.mothra.ScaleCloud;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** The golden scale cloud: a swirl of glitter inside a faint haze, thinning out as its life runs down. */
public class ScaleCloudRenderer extends EntityRenderer<ScaleCloud, ScaleCloudRenderer.State> {
	public static class State extends EntityRenderState {
		float life;
	}

	public ScaleCloudRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(ScaleCloud entity, float partialTicks) {
		return entity.getBoundingBox().inflate(6);
	}

	@Override
	public void extractRenderState(ScaleCloud e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.life = e.lifeFraction();
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 2147483647f;
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
		ps.pushPose();
		ps.translate(0, ScaleCloud.RADIUS * 0.6, 0);
		float fade = Mth.clamp(s.life * 4f, 0f, 1f) * Mth.clamp((1f - s.life) * 8f, 0.25f, 1f);
		c.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(MothraRenderer.GLOW), (pose, vc) -> haze(vc, pose, s, fade));
		c.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(MothraRenderer.SPARKLE), (pose, vc) -> glitter(vc, pose, s, fade));
		ps.popPose();
	}

	private void haze(VertexConsumer vc, PoseStack.Pose pose, State s, float fade) {
		for (int i = 0; i < 6; i++) {
			float a = s.ageInTicks * 0.012f * (i % 2 == 0 ? 1 : -1) + i * 1.9f;
			float x = (float) Math.cos(a) * ScaleCloud.RADIUS * 0.35f;
			float z = (float) Math.sin(a) * ScaleCloud.RADIUS * 0.35f;
			float y = (float) Math.sin(s.ageInTicks * 0.02f + i) * 2f;
			pose = pose; // the quads are placed with an offset instead of a pose push
			float r = ScaleCloud.RADIUS * (0.75f + 0.15f * hash(i));
			int col = ((int) (255 * 0.26f * fade) << 24) | 0xFFC060;
			offsetQuad(vc, pose, x, y, z, r, col);
		}
	}

	private static void offsetQuad(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, int col) {
		MothraRenderer.quad(vc, pose, x - r, y - r, z, x + r, y - r, z, x + r, y + r, z, x - r, y + r, z, col);
		MothraRenderer.quad(vc, pose, x, y - r, z - r, x, y - r, z + r, x, y + r, z + r, x, y + r, z - r, col);
		MothraRenderer.quad(vc, pose, x - r, y, z - r, x + r, y, z - r, x + r, y, z + r, x - r, y, z + r, col);
	}

	private void glitter(VertexConsumer vc, PoseStack.Pose pose, State s, float fade) {
		int n = 150;
		for (int i = 0; i < n; i++) {
			float h1 = hash(i * 31 + 1), h2 = hash(i * 31 + 2), h3 = hash(i * 31 + 3), h4 = hash(i * 31 + 4);
			float rad = ScaleCloud.RADIUS * 0.85f * (float) Math.cbrt(h1);
			float theta = h2 * 6.2832f + s.ageInTicks * (0.015f + 0.03f * (1 - rad / ScaleCloud.RADIUS));
			float y = (h3 * 2f - 1f) * ScaleCloud.RADIUS * 0.55f + (float) Math.sin(s.ageInTicks * 0.05f + h4 * 6f) * 1.4f;
			float x = (float) Math.cos(theta) * rad * 1.1f;
			float z = (float) Math.sin(theta) * rad * 1.1f;
			float tw = 0.5f + 0.5f * (float) Math.sin(s.ageInTicks * (0.5f + h4) + i);
			int a = (int) (255 * Mth.clamp(tw * fade, 0f, 1f));
			MothraRenderer.star(vc, pose, x, y, z, 0.6f + 1.1f * h4, h2 * 6f, a);
		}
	}
}
