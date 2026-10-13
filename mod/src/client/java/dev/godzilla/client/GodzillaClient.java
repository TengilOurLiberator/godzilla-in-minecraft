package dev.godzilla.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.godzilla.client.vr.ClientVr;
import dev.godzilla.core.GodzillaEntities;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.net.PingS2C;
import dev.godzilla.core.net.PongC2S;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only initialisation: renderers, model layers, key mappings, client payload receivers, client tick. */
public class GodzillaClient implements ClientModInitializer {
	public static KeyMapping pingKey;

	@Override
	public void onInitializeClient() {
		ClientVr.init();

		// entity rendering: model layer (geometry) + renderer
		ModelLayerRegistry.registerModelLayer(TestDummyRenderer.LAYER, TestDummyModel::createBodyLayer);
		EntityRenderers.register(GodzillaEntities.TEST_DUMMY, TestDummyRenderer::new);

		// key mapping (shown under "Godzilla" in the controls screen; Vivecraft lists every registered KeyMapping for binding)
		KeyMapping.Category category = KeyMapping.Category.register(GodzillaMod.id("main"));
		pingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.godzilla.ping", InputConstants.KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (pingKey.consumeClick()) {
				GodzillaMod.LOGGER.info("GODZILLA-NET client: ping key pressed, sending pong id=0");
				ClientPlayNetworking.send(new PongC2S(0, System.nanoTime(), ClientVr.isVrActive()));
			}
		});

		// payload receiver (runs on the client thread)
		ClientPlayNetworking.registerGlobalReceiver(PingS2C.TYPE, (payload, context) -> {
			GodzillaMod.LOGGER.info("GODZILLA-NET client got ping id={} vr={} worldScale={}", payload.id(), ClientVr.isVrActive(), ClientVr.worldScale());
			ClientPlayNetworking.send(new PongC2S(payload.id(), payload.serverNanos(), ClientVr.isVrActive()));
		});
		GodzillaMod.LOGGER.info("godzilla: client init done");
	}
}
