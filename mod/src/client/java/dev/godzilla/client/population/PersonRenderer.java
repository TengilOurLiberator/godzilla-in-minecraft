package dev.godzilla.client.population;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.population.PersonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws the civilians: no shadow, no name tag, one model shared by all, 16 outfit textures. */
public class PersonRenderer extends EntityRenderer<PersonEntity, PersonModel.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(GodzillaMod.id("person"), "main");
	private static final Identifier[] TEXTURES = new Identifier[PersonEntity.OUTFITS];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = GodzillaMod.id("textures/population/person_%02d.png".formatted(i));
		}
	}

	/** People are drawn bigger than life so that they read from Godzilla's eyes, 26 blocks up. */
	public static final float SCALE = 1.45F;
	private final PersonModel model;

	public PersonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new PersonModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0;
	}

	@Override
	public PersonModel.State createRenderState() {
		return new PersonModel.State();
	}

	@Override
	public void extractRenderState(PersonEntity e, PersonModel.State s, float partialTicks) {
		long t0 = System.nanoTime();
		super.extractRenderState(e, s, partialTicks);
		s.yRot = Mth.rotLerp(partialTicks, e.yRotO, e.getYRot());
		s.walkPhase = Mth.lerp(partialTicks, e.walkPhaseO, e.walkPhase);
		s.walkAmp = Mth.lerp(partialTicks, e.walkAmpO, e.walkAmp);
		s.panic = e.panicking();
		s.flung = e.flung();
		s.outfit = e.outfit();
		PopulationClient.renderNanos += System.nanoTime() - t0;
	}

	@Override
	public void submit(PersonModel.State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		long t0 = System.nanoTime();
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - s.yRot);
		poseStack.scale(SCALE, SCALE, SCALE);
		if (s.flung) {
			poseStack.translate(0.0F, 0.3F, 0.0F);
			poseStack.rotateDegrees(Axis.XP, s.ageInTicks * 37.0F);
			poseStack.rotateDegrees(Axis.ZP, s.ageInTicks * 23.0F);
			poseStack.translate(0.0F, -0.3F, 0.0F);
		}
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, s, poseStack, TEXTURES[s.outfit % TEXTURES.length], s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
		poseStack.popPose();
		PopulationClient.renderNanos += System.nanoTime() - t0;
	}
}
