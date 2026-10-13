package dev.godzilla.client.mothra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.mothra.Mothra;
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
import org.joml.Vector3f;

/**
 * Draws Mothra: the model for the body, bent surfaces for the four wings (so the beat travels out to the tips), a cloud of
 * glitter that falls from the wings and a soft glow while she telegraphs an attack. All in the entity's local frame:
 * +z forward, +y up, x sideways, one unit = one block.
 */
public class MothraRenderer extends EntityRenderer<Mothra, MothraRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(GodzillaMod.id("mothra"), "main");
	static final Identifier BODY = GodzillaMod.id("textures/mothra/body.png");
	static final Identifier WINGS = GodzillaMod.id("textures/mothra/wings.png");
	static final Identifier SPARKLE = GodzillaMod.id("textures/mothra/sparkle.png");
	static final Identifier GLOW = GodzillaMod.id("textures/mothra/glow.png");
	private static final float MODEL_SCALE = 8f;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int NU = 18;
	private static final int NV = 6;

	private final MothraModel model;

	/** One wing: length along the span, chord, where the root sits on the thorax, atlas rows. */
	private record Wing(float length, float chord, float rootX, float rootY, float rootZ, float sweep, float dihedral,
						float lag, float ampScale, float phaseOffset, float v0, boolean hind) {
	}

	private static final Wing FORE = new Wing(19.5f, 11f, 2.4f, 1.4f, 3.4f, 0.10f, 0.14f, 1.15f, 1.0f, 0f, 0f, false);
	private static final Wing HIND = new Wing(15f, 9.5f, 2.2f, 0.4f, -2.0f, 0.16f, 0.04f, 1.55f, 0.85f, 0.7f, 0.5f, true);

	public MothraRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new MothraModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0;
	}

	@Override
	public MothraRenderState createRenderState() {
		return new MothraRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Mothra entity, float partialTicks) {
		return entity.getBoundingBox().inflate(22, 24, 22);
	}

	@Override
	public void extractRenderState(Mothra e, MothraRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		s.yRot = e.getYRot(pt);
		s.xRot = Mth.lerp(pt, e.xRotO, e.getXRot());
		s.roll = e.rollRad();
		s.phase = Mth.lerp(pt, e.animPhaseO, e.animPhase);
		s.amp = Mth.lerp(pt, e.animAmpO, e.animAmp);
		s.fold = Mth.lerp(pt, e.animFoldO, e.animFold);
		s.glow = Mth.lerp(pt, e.animGlowO, e.animGlow);
		s.hurt = e.hurtFlash(pt);
		s.mode = e.mode().ordinal();
		s.dying = e.mode() == Mothra.Mode.DYING;
		s.vx = (float) (e.getX() - e.xo);
		s.vy = (float) (e.getY() - e.yo);
		s.vz = (float) (e.getZ() - e.zo);
		s.lightCoords = FULL_BRIGHT;
	}

	@Override
	public void submit(MothraRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
		ps.pushPose();
		ps.translate(0, Mothra.BODY_HEIGHT / 2, 0);
		ps.rotateAround(Axis.YP.rotationDegrees(-s.yRot), 0, 0, 0);
		ps.rotateAround(Axis.XP.rotationDegrees(s.xRot), 0, 0, 0);
		ps.rotateAround(Axis.ZP.rotation(s.roll), 0, 0, 0);
		int overlay = OverlayTexture.pack(s.hurt * 0.75f, false);

		ps.pushPose();
		ps.scale(MODEL_SCALE, -MODEL_SCALE, -MODEL_SCALE);
		c.submitModel(model, s, ps, RenderTypes.entityCutout(BODY), s.lightCoords, overlay, s.outlineColor);
		ps.popPose();

		c.submitCustomGeometry(ps, RenderTypes.entityTranslucent(WINGS), (pose, vc) -> {
			for (int side = -1; side <= 1; side += 2) {
				wing(vc, pose, side, HIND, s, overlay);
			}
			for (int side = -1; side <= 1; side += 2) {
				wing(vc, pose, side, FORE, s, overlay);
			}
		});
		c.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(SPARKLE), (pose, vc) -> sparkles(vc, pose, s));
		if (s.glow > 0.03f) {
			float a = s.glow * (0.40f + 0.12f * (float) Math.sin(s.ageInTicks * 0.5f));
			c.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(GLOW), (pose, vc) -> glow(vc, pose, 20f, a));
		}
		ps.popPose();
	}

	// ----------------------------------------------------------------------------------------------------- wings

	/** Spine of one wing for this frame: lateral offset, height and leading-edge z at NU+1 stations. */
	private static final class Spine {
		final float[] x = new float[NU + 1];
		final float[] y = new float[NU + 1];
		final float[] z = new float[NU + 1];
	}

	private static Spine spine(Wing w, int side, MothraRenderState s) {
		Spine sp = new Spine();
		float x = w.rootX * side;
		float y = w.rootY;
		float dl = w.length / NU;
		float ampA = 0.78f * s.amp * w.ampScale;
		for (int i = 0; i <= NU; i++) {
			float u = i / (float) NU;
			sp.x[i] = x;
			sp.y[i] = y;
			float swept = s.fold * u * u * 10f;
			sp.z[i] = w.rootZ - w.sweep * u * w.length - swept;
			float ang = w.dihedral + ampA * (float) Math.sin(s.phase - w.lag * u + w.phaseOffset) + s.fold * (0.15f + 1.05f * u);
			x += (float) Math.cos(ang) * dl * side;
			y += (float) Math.sin(ang) * dl;
		}
		return sp;
	}

	/** A point on the wing surface (u along the span, v from the leading edge) in the local frame. */
	private static void point(Wing w, Spine sp, float u, float v, MothraRenderState s, Vector3f out) {
		float f = Mth.clamp(u, 0f, 1f) * NU;
		int i = Math.min(NU - 1, (int) f);
		float t = f - i;
		float x = Mth.lerp(t, sp.x[i], sp.x[i + 1]);
		float y = Mth.lerp(t, sp.y[i], sp.y[i + 1]);
		float zl = Mth.lerp(t, sp.z[i], sp.z[i + 1]);
		// the trailing edge lags the leading edge on the upstroke: a little twist and camber
		float twist = (float) Math.cos(s.phase - w.lag * u + w.phaseOffset) * 0.9f * s.amp * (v - 0.35f) * u;
		float camber = (float) Math.sin(v * Math.PI) * 0.35f;
		out.set(x, y - twist + camber, zl - v * w.chord);
	}

	private static void wing(VertexConsumer vc, PoseStack.Pose pose, int side, Wing w, MothraRenderState s, int overlay) {
		Spine sp = spine(w, side, s);
		Vector3f p = new Vector3f();
		for (int i = 0; i < NU; i++) {
			float u0 = i / (float) NU, u1 = (i + 1) / (float) NU;
			for (int j = 0; j < NV; j++) {
				float v0 = j / (float) NV, v1 = (j + 1) / (float) NV;
				vertex(vc, pose, w, sp, u0, v0, s, overlay, p);
				vertex(vc, pose, w, sp, u0, v1, s, overlay, p);
				vertex(vc, pose, w, sp, u1, v1, s, overlay, p);
				vertex(vc, pose, w, sp, u1, v0, s, overlay, p);
			}
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Wing w, Spine sp, float u, float v, MothraRenderState s, int overlay, Vector3f p) {
		point(w, sp, u, v, s, p);
		vc.addVertex(pose, p.x, p.y, p.z).setColor(-1).setUv(u, w.v0 + v * 0.5f).setOverlay(overlay).setLight(s.lightCoords).setNormal(pose, 0, 1, 0);
	}

	// ----------------------------------------------------------------------------------------------------- glitter

	private static float hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 2147483647f;
	}

	private void sparkles(VertexConsumer vc, PoseStack.Pose pose, MothraRenderState s) {
		int count = s.dying ? 150 : 80;
		float period = 80f;
		Spine[] fore = {spine(FORE, -1, s), spine(FORE, 1, s)};
		Spine[] hind = {spine(HIND, -1, s), spine(HIND, 1, s)};
		Vector3f p = new Vector3f();
		Vector3f vel = new Vector3f(s.vx, s.vy, s.vz).rotateY((float) Math.toRadians(s.yRot));
		for (int i = 0; i < count; i++) {
			float t = s.ageInTicks + i * period / count;
			int cycle = (int) Math.floor(t / period);
			float age = (t - cycle * period) / period;
			int seed = i * 7919 + cycle * 104729;
			boolean useFore = hash(seed) < 0.6f;
			int sideIdx = hash(seed + 1) < 0.5f ? 0 : 1;
			Wing w = useFore ? FORE : HIND;
			Spine sp = useFore ? fore[sideIdx] : hind[sideIdx];
			point(w, sp, 0.25f + 0.75f * hash(seed + 2), 0.1f + 0.8f * hash(seed + 3), s, p);
			float fall = age * (10f + 8f * hash(seed + 4));
			p.y -= fall;
			p.x += (sideIdx == 0 ? -1 : 1) * age * 3f + (float) Math.sin(age * 9f + hash(seed + 5) * 6f) * 1.2f;
			p.z += (float) Math.cos(age * 7f + hash(seed + 6) * 6f) * 1.0f;
			p.sub(vel.x * age * 34f, vel.y * age * 34f, vel.z * age * 34f);
			float fade = (float) Math.pow(Math.sin(Math.PI * age), 0.7);
			float twinkle = 0.65f + 0.35f * (float) Math.sin(s.ageInTicks * 0.9f + i * 1.7f);
			float size = (0.55f + 0.7f * hash(seed + 7)) * (0.6f + 0.4f * fade);
			int a = (int) (255 * Mth.clamp(fade * twinkle, 0f, 1f));
			star(vc, pose, p.x, p.y, p.z, size, hash(seed + 8) * 6.28f, a);
		}
	}

	/** Three crossed quads, so it reads from any direction without needing the camera. */
	static void star(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float rot, int alpha) {
		float c = (float) Math.cos(rot) * r, sn = (float) Math.sin(rot) * r;
		int col = (alpha << 24) | 0xFFF0C0;
		quad(vc, pose, x - c, y - r, z - sn, x + c, y - r, z + sn, x + c, y + r, z + sn, x - c, y + r, z - sn, col);
		quad(vc, pose, x + sn, y - r, z - c, x - sn, y - r, z + c, x - sn, y + r, z + c, x + sn, y + r, z - c, col);
		quad(vc, pose, x - c + sn, y, z - sn - c, x + c + sn, y, z + sn - c, x + c - sn, y, z + sn + c, x - c - sn, y, z - sn + c, col);
	}

	static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
					 float x2, float y2, float z2, float x3, float y3, float z3, int color) {
		vc.addVertex(pose, x0, y0, z0).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
		vc.addVertex(pose, x1, y1, z1).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
		vc.addVertex(pose, x2, y2, z2).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
		vc.addVertex(pose, x3, y3, z3).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
	}

	/** A soft round haze: three crossed quads. */
	static void glow(VertexConsumer vc, PoseStack.Pose pose, float r, float alpha) {
		int col = ((int) (255 * Mth.clamp(alpha, 0f, 1f)) << 24) | 0xFFFFFF;
		quad(vc, pose, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, 0, col);
		quad(vc, pose, 0, -r, -r, 0, -r, r, 0, r, r, 0, r, -r, col);
		quad(vc, pose, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r, col);
	}
}
