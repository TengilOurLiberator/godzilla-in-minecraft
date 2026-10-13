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
 * Box models of the vehicles. Units are 1/16 block, y points down, 24 is the ground line, the front is -Z. Texture offsets match
 * tools/population/make_textures.py (car, bus, truck 128x64; train 256x128).
 */
public final class VehicleModels {
	private VehicleModels() {
	}

	public static class State extends EntityRenderState {
		public float yRot;
		public int style;
		public int color;
		public int flags;
		public int tickCount;
	}

	private static void wheel(PartDefinition root, String name, int u, int v, float w, float h, float d, float x, float z) {
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-w / 2, -h, -d / 2, (int) w, (int) h, (int) d),
				PartPose.offset(x, 24, z));
	}

	// ------------------------------------------------------------------------------------------------ car, taxi, police
	public static class Car extends EntityModel<State> {
		private final ModelPart sign;
		private final ModelPart bar;

		public Car(ModelPart root) {
			super(root);
			sign = root.getChild("sign");
			bar = root.getChild("bar");
		}

		public static LayerDefinition layer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, -9, -12, 13, 6, 24), PartPose.offset(0, 24, 0));
			root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 32).addBox(-5.5F, -13, -4, 11, 4, 12), PartPose.offset(0, 24, 0));
			root.addOrReplaceChild("sign", CubeListBuilder.create().texOffs(0, 50).addBox(-2.5F, -15, 0, 5, 2, 3), PartPose.offset(0, 24, 0));
			root.addOrReplaceChild("bar", CubeListBuilder.create().texOffs(0, 56).addBox(-4.5F, -15, 0, 9, 2, 3), PartPose.offset(0, 24, 0));
			for (int i = 0; i < 4; i++) {
				root.addOrReplaceChild("w" + i, CubeListBuilder.create().texOffs(76, 32).addBox(-1, -4, -2, 2, 4, 4),
						PartPose.offset((i & 1) == 0 ? 6.5F : -6.5F, 24, i < 2 ? -7.5F : 7.5F));
			}
			return LayerDefinition.create(mesh, 128, 64);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			sign.visible = s.style == 1;
			bar.visible = s.style == 2;
		}
	}

	// ------------------------------------------------------------------------------------------------ bus
	public static class Bus extends EntityModel<State> {
		public Bus(ModelPart root) {
			super(root);
		}

		public static LayerDefinition layer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -17, -20, 14, 12, 40), PartPose.offset(0, 24, 0));
			for (int i = 0; i < 4; i++) {
				root.addOrReplaceChild("w" + i, CubeListBuilder.create().texOffs(0, 54).addBox(-1.5F, -5, -2.5F, 3, 5, 5),
						PartPose.offset((i & 1) == 0 ? 7F : -7F, 24, i < 2 ? -12F : 12F));
			}
			return LayerDefinition.create(mesh, 128, 64);
		}
	}

	// ------------------------------------------------------------------------------------------------ truck
	public static class Truck extends EntityModel<State> {
		public Truck(ModelPart root) {
			super(root);
		}

		public static LayerDefinition layer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -18, -8, 14, 13, 26), PartPose.offset(0, 24, 0));
			root.addOrReplaceChild("cab", CubeListBuilder.create().texOffs(0, 40).addBox(-6, -15, -18, 12, 10, 10), PartPose.offset(0, 24, 0));
			float[] zs = {-13, 8, 14};
			for (int i = 0; i < 6; i++) {
				root.addOrReplaceChild("w" + i, CubeListBuilder.create().texOffs(84, 40).addBox(-1.5F, -5, -2.5F, 3, 5, 5),
						PartPose.offset((i & 1) == 0 ? 7F : -7F, 24, zs[i / 2]));
			}
			return LayerDefinition.create(mesh, 128, 64);
		}
	}

	// ------------------------------------------------------------------------------------------------ train carriage
	public static class TrainCar extends EntityModel<State> {
		public TrainCar(ModelPart root) {
			super(root);
		}

		public static LayerDefinition layer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -22, -28, 16, 18, 56), PartPose.offset(0, 24, 0));
			root.addOrReplaceChild("under", CubeListBuilder.create().texOffs(0, 76).addBox(-6, -4, -20, 12, 3, 40), PartPose.offset(0, 24, 0));
			for (int i = 0; i < 3; i++) {
				root.addOrReplaceChild("ac" + i, CubeListBuilder.create().texOffs(110, 76).addBox(-4, -23, -5, 8, 1, 10),
						PartPose.offset(0, 24, (i - 1) * 17F));
			}
			return LayerDefinition.create(mesh, 256, 128);
		}
	}
}
