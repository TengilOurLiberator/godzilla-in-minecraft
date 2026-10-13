package dev.godzilla.client.population;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.population.VehicleEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws cars, taxis, police cars, buses, trucks and train carriages. The texture is chosen by style, colour and a flash phase
 * (car alarm hazard lights, police light bar, both drawn full-bright); flying vehicles tumble.
 */
public class VehicleRenderer extends EntityRenderer<VehicleEntity, VehicleModels.State> {
	public static final ModelLayerLocation CAR_LAYER = new ModelLayerLocation(GodzillaMod.id("vehicle_car"), "main");
	public static final ModelLayerLocation BUS_LAYER = new ModelLayerLocation(GodzillaMod.id("vehicle_bus"), "main");
	public static final ModelLayerLocation TRUCK_LAYER = new ModelLayerLocation(GodzillaMod.id("vehicle_truck"), "main");
	public static final ModelLayerLocation TRAIN_LAYER = new ModelLayerLocation(GodzillaMod.id("vehicle_train"), "main");
	private static final int FULL_BRIGHT = 15728880;

	private final EntityModel<VehicleModels.State> car, bus, truck, train;
	private final Map<Integer, Identifier> textures = new HashMap<>();

	public VehicleRenderer(EntityRendererProvider.Context context) {
		super(context);
		car = new VehicleModels.Car(context.bakeLayer(CAR_LAYER));
		bus = new VehicleModels.Bus(context.bakeLayer(BUS_LAYER));
		truck = new VehicleModels.Truck(context.bakeLayer(TRUCK_LAYER));
		train = new VehicleModels.TrainCar(context.bakeLayer(TRAIN_LAYER));
		this.shadowRadius = 0;
	}

	@Override
	public VehicleModels.State createRenderState() {
		return new VehicleModels.State();
	}

	@Override
	public void extractRenderState(VehicleEntity e, VehicleModels.State s, float partialTicks) {
		super.extractRenderState(e, s, partialTicks);
		s.yRot = Mth.rotLerp(partialTicks, e.yRotO, e.getYRot());
		s.style = e.style();
		s.color = e.color();
		s.flags = e.flags();
		s.tickCount = e.tickCount;
	}

	private Identifier texture(int style, int color, int variant) {
		int key = style << 16 | color << 8 | variant;
		return textures.computeIfAbsent(key, k -> {
			String name = switch (style) {
				case VehicleEntity.STYLE_TAXI -> "car_taxi";
				case VehicleEntity.STYLE_POLICE -> "car_police";
				case VehicleEntity.STYLE_BUS -> "bus_" + VehicleEntity.BUS_COLORS[color % VehicleEntity.BUS_COLORS.length];
				case VehicleEntity.STYLE_TRUCK -> "truck_" + VehicleEntity.TRUCK_COLORS[color % VehicleEntity.TRUCK_COLORS.length];
				case VehicleEntity.STYLE_TRAIN -> "train_" + VehicleEntity.TRAIN_COLORS[color % VehicleEntity.TRAIN_COLORS.length];
				default -> "car_" + VehicleEntity.CAR_COLORS[color % VehicleEntity.CAR_COLORS.length];
			};
			return GodzillaMod.id("textures/population/%s_%d.png".formatted(name, variant));
		});
	}

	@Override
	public void submit(VehicleModels.State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int variant = 0;
		boolean lit = false;
		if (s.style == VehicleEntity.STYLE_POLICE && (s.flags & VehicleEntity.FLAG_SIREN) != 0) {
			variant = (int) (s.ageInTicks / 4) & 1;
			lit = true;
		} else if ((s.flags & VehicleEntity.FLAG_ALARM) != 0 && ((int) (s.ageInTicks / 5) & 1) == 0) {
			variant = 1;
			lit = true;
		}
		EntityModel<VehicleModels.State> model = switch (s.style) {
			case VehicleEntity.STYLE_BUS -> bus;
			case VehicleEntity.STYLE_TRUCK -> truck;
			case VehicleEntity.STYLE_TRAIN -> train;
			default -> car;
		};
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - s.yRot);
		float sc = s.style == VehicleEntity.STYLE_TRAIN ? 1.0F : s.style == VehicleEntity.STYLE_BUS || s.style == VehicleEntity.STYLE_TRUCK ? 1.1F : 1.25F;
		poseStack.scale(sc, sc, sc);
		if ((s.flags & VehicleEntity.FLAG_TUMBLE) != 0) {
			poseStack.translate(0.0F, 0.5F, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, s.ageInTicks * 31.0F);
			poseStack.rotateDegrees(Axis.XP, s.ageInTicks * 19.0F);
			poseStack.translate(0.0F, -0.5F, 0.0F);
		}
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, s, poseStack, texture(s.style, s.color, variant), lit ? FULL_BRIGHT : s.lightCoords,
				OverlayTexture.NO_OVERLAY, s.outlineColor);
		poseStack.popPose();
	}
}
