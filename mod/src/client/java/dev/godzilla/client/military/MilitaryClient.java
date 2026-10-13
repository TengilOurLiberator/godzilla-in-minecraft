package dev.godzilla.client.military;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.military.MilEntities;
import dev.godzilla.military.MilShot;
import dev.godzilla.military.MilitaryUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Client side of the military: model layers read from the generated json (tools/military/make_assets.py), renderers for the
 * five unit types and the projectiles, and a once-per-5-seconds log line with the frame rate while units are around.
 */
public final class MilitaryClient {
	private static final List<String> MODELS = List.of("tank", "heli", "jet", "maser", "ship_s", "ship_m", "ship_l", "glow", "glow_blue", "missile");

	private MilitaryClient() {
	}

	public static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(GodzillaMod.id("mil_" + name), "main");
	}

	public static void init() {
		for (String m : MODELS) {
			ModelLayerRegistry.registerModelLayer(layer(m), () -> MilModels.layer(m));
		}
		EntityRenderers.register(MilEntities.TANK, c -> new MilRenderer<>(c, false, false, "tank"));
		EntityRenderers.register(MilEntities.HELI, c -> new MilRenderer<>(c, false, false, "heli"));
		EntityRenderers.register(MilEntities.JET, c -> new MilRenderer<>(c, false, false, "jet"));
		EntityRenderers.register(MilEntities.MASER, c -> new MilRenderer<>(c, false, true, "maser"));
		EntityRenderers.register(MilEntities.SHIP, c -> new MilRenderer<>(c, true, false, "ship_s", "ship_m", "ship_l"));
		EntityRenderers.register(MilEntities.SHOT, MilShotRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.level.getGameTime() % 100 != 0) {
				return;
			}
			int units = 0, shots = 0;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof MilitaryUnit) {
					units++;
				} else if (e instanceof MilShot) {
					shots++;
				}
			}
			// measuring aid: the test client is capped at 60 fps by tools/testclient.py; a file named mil_uncap in its game folder lifts that
			if (mc.gameDirectory.toPath().resolve("mil_uncap").toFile().exists() && mc.options.framerateLimit().get() < 260) {
				mc.options.framerateLimit().set(260);
			}
			if (units + shots > 0) {
				GodzillaMod.LOGGER.info("GODZILLA-MIL client fps={} units={} shots={}", mc.getFps(), units, shots);
			}
		});
	}
}
