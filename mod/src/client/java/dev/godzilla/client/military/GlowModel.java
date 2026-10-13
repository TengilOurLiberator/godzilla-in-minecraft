package dev.godzilla.client.military;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.Vec3;

/**
 * A single glowing 2-pixel cube (0.125 block) drawn emissive and translucent, scaled and rotated into streaks, beam segments and
 * balls of light. Models "glow" (white to orange) and "glow_blue" (white to blue).
 */
public final class GlowModel extends Model<Unit> {
	private static final int FULL_BRIGHT = 15728880;
	private final Identifier texture;

	public GlowModel(ModelPart root, String name) {
		super(root, RenderTypes::entityTranslucentEmissive);
		this.texture = GodzillaMod.id("textures/mil/" + name + ".png");
	}

	@Override
	public void setupAnim(Unit state) {
	}

	/** A ball of light centred at {@code at} (relative to the pose), {@code size} blocks across. */
	public void submitCube(PoseStack pose, SubmitNodeCollector collector, Vec3 at, float size) {
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		float k = size / 0.125F;
		pose.scale(-k, -k, k);
		collector.submitModel(this, Unit.INSTANCE, pose, this.texture, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** A box from a to b, {@code width} blocks thick. */
	public void submitSegment(PoseStack pose, SubmitNodeCollector collector, Vec3 a, Vec3 b, float width) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1e-4) {
			return;
		}
		pose.pushPose();
		pose.translate((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2);
		pose.rotate(Axis.YP, (float) Math.atan2(d.x, d.z));
		pose.rotate(Axis.XP, (float) Math.asin(-d.y / len));
		float kw = width / 0.125F, kl = (float) (len * 1.05) / 0.125F;
		pose.scale(-kw, -kw, kl);
		collector.submitModel(this, Unit.INSTANCE, pose, this.texture, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** Same, given by yaw and pitch of the velocity (MC degrees: pitch positive = down), length and width in blocks. */
	public void submitStreak(PoseStack pose, SubmitNodeCollector collector, float yawDeg, float pitchDeg, float length, float width) {
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, -yawDeg);
		pose.rotateDegrees(Axis.XP, pitchDeg);
		pose.scale(-width / 0.125F, -width / 0.125F, length / 0.125F);
		collector.submitModel(this, Unit.INSTANCE, pose, this.texture, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}
}
