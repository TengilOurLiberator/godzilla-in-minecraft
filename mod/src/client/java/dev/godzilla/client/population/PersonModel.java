package dev.godzilla.client.population;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * A civilian made of six boxes, 10 model units (0.625 blocks) tall, with a slightly big head. Texture 32x32, box offsets as in
 * tools/population/make_textures.py: head (0,0) 3x3x3, body (0,8) 4x4x2, leg (0,16) 2x3x2, arm (16,16) 2x4x2.
 */
public class PersonModel extends EntityModel<PersonModel.State> {
	public static class State extends EntityRenderState {
		public float yRot;
		public float walkPhase;
		public float walkAmp;
		public boolean panic;
		public boolean flung;
		public int outfit;
	}

	private final ModelPart head;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public PersonModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		leftArm = root.getChild("left_arm");
		rightArm = root.getChild("right_arm");
		leftLeg = root.getChild("left_leg");
		rightLeg = root.getChild("right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.0F, -1.5F, 3, 3, 3), PartPose.offset(0, 17, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 8).addBox(-2.0F, -4.0F, -1.0F, 4, 4, 2), PartPose.offset(0, 21, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2), PartPose.offset(1, 21, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2), PartPose.offset(-1, 21, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, 0.0F, -1.0F, 2, 4, 2), PartPose.offset(3, 17.5F, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, 0.0F, -1.0F, 2, 4, 2), PartPose.offset(-3, 17.5F, 0));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		float swing = (float) Math.sin(s.walkPhase) * s.walkAmp;
		if (s.flung) {
			float t = s.ageInTicks * 0.9F;
			leftArm.xRot = -2.6F + (float) Math.sin(t) * 0.5F;
			rightArm.xRot = -2.6F + (float) Math.sin(t + 1.5F) * 0.5F;
			leftArm.zRot = -0.5F;
			rightArm.zRot = 0.5F;
			leftLeg.xRot = (float) Math.sin(t) * 1.2F;
			rightLeg.xRot = (float) Math.sin(t + 2.0F) * 1.2F;
		} else if (s.panic) {
			float wave = (float) Math.sin(s.ageInTicks * 0.9F + s.outfit) * 0.5F;
			leftLeg.xRot = swing * 1.5F;
			rightLeg.xRot = -swing * 1.5F;
			// both arms thrown up over the head, flapping
			leftArm.xRot = -2.75F + wave * 0.5F;
			rightArm.xRot = -2.75F - wave * 0.5F;
			leftArm.zRot = -0.35F - wave * 0.5F;
			rightArm.zRot = 0.35F - wave * 0.5F;
			head.xRot = -0.15F;
		} else {
			leftLeg.xRot = swing * 1.1F;
			rightLeg.xRot = -swing * 1.1F;
			leftArm.xRot = -swing * 0.9F;
			rightArm.xRot = swing * 0.9F;
		}
	}
}
