package dev.godzilla.client.military;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One model for every unit: the geometry comes from the json, the animation is by part name. Parts named turret* turn with the
 * synced turret yaw, barrel* tilt with the elevation, "rotor" spins about y, "tail_rotor" about x.
 */
public class MilModel extends EntityModel<MilModel.State> {
	/** What the renderer extracts from the entity. Angles in degrees unless noted. */
	public static class State extends EntityRenderState {
		public float yaw, pitch, roll, turretYaw, turretPitch, spin;
		public int flags, variant;
		public @Nullable Vec3 muzzle, beamEnd;
		public byte kind;
		public boolean sinking;
	}

	private final List<ModelPart> turrets = new ArrayList<>();
	private final List<ModelPart> barrels = new ArrayList<>();
	private ModelPart rotor, tailRotor;

	public MilModel(ModelPart root, MilModels.Info info, Function<Identifier, RenderType> renderType) {
		super(root, renderType);
		Map<String, String> parent = new HashMap<>();
		for (MilModels.PartInfo p : info.parts()) {
			parent.put(p.name(), p.parent());
		}
		for (MilModels.PartInfo p : info.parts()) {
			ModelPart part = find(root, parent, p.name());
			if (p.name().startsWith("turret")) {
				this.turrets.add(part);
			} else if (p.name().startsWith("barrel")) {
				this.barrels.add(part);
			} else if (p.name().equals("rotor")) {
				this.rotor = part;
			} else if (p.name().equals("tail_rotor")) {
				this.tailRotor = part;
			}
		}
	}

	public MilModel(ModelPart root, MilModels.Info info) {
		this(root, info, RenderTypes::entityCutout);
	}

	private static ModelPart find(ModelPart root, Map<String, String> parent, String name) {
		List<String> chain = new ArrayList<>();
		for (String n = name; !n.equals("root"); n = parent.get(n)) {
			chain.add(0, n);
		}
		ModelPart p = root;
		for (String n : chain) {
			p = p.getChild(n);
		}
		return p;
	}

	@Override
	public void setupAnim(State state) {
		super.setupAnim(state);
		float ty = (float) Math.toRadians(state.turretYaw);
		float tp = (float) Math.toRadians(state.turretPitch);
		for (ModelPart t : this.turrets) {
			t.yRot = ty;
		}
		for (ModelPart b : this.barrels) {
			b.xRot = -tp;
		}
		if (this.rotor != null) {
			this.rotor.yRot = state.spin;
		}
		if (this.tailRotor != null) {
			this.tailRotor.xRot = state.spin * 1.3F;
		}
	}
}
