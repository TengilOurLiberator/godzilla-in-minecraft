package dev.godzilla.client;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.TestDummy;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Pattern in 26.3: entity -> (extractRenderState copies what the model needs into a RenderState) -> model.setupAnim(state)
 * -> submit. The renderer never touches the entity while drawing. Add fields to a RenderState subclass for custom data and
 * fill them in {@code extractRenderState}.
 */
public class TestDummyRenderer extends MobRenderer<TestDummy, LivingEntityRenderState, TestDummyModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(GodzillaMod.id("test_dummy"), "main");
	private static final Identifier TEXTURE = GodzillaMod.id("textures/entity/test_dummy.png");

	public TestDummyRenderer(EntityRendererProvider.Context context) {
		super(context, new TestDummyModel(context.bakeLayer(LAYER)), 0.9F);
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}
}
