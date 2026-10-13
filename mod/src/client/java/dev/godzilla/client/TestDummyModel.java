package dev.godzilla.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * A totem made of boxes. Model space: 16 units = 1 block, y points DOWN, 24 is the ground line (the renderer applies the
 * standard -1.501 y offset). Parts are children of the root; the root is passed to {@code super(root)}.
 */
public class TestDummyModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public TestDummyModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -8.0F, -12.0F, 24.0F, 8.0F, 24.0F),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F),
				PartPose.offset(0.0F, 16.0F, 0.0F));
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(32, 32).addBox(-4.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F),
				PartPose.offset(-8.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 32).mirror().addBox(0.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F),
				PartPose.offset(8.0F, 2.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.1F;
		this.head.yRot = (float) Math.sin(t) * 0.6F;
		this.rightArm.zRot = 0.6F + (float) Math.sin(t * 2.0F) * 0.5F;
		this.leftArm.zRot = -0.6F - (float) Math.sin(t * 2.0F) * 0.5F;
	}
}
