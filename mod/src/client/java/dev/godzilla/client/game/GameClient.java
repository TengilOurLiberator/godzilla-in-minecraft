package dev.godzilla.client.game;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.game.GameModule;
import dev.godzilla.game.net.GameNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

/** Client side of the game module: music, HUD, and the (invisible) renderer of the smash sign entity. */
public final class GameClient {
	private GameClient() {
	}

	/** The sign is drawn by the text display it carries; the entity itself only needs a renderer that draws nothing. */
	private static final class NothingRenderer extends EntityRenderer<dev.godzilla.game.SmashSign, EntityRenderState> {
		NothingRenderer(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public EntityRenderState createRenderState() {
			return new EntityRenderState();
		}

		@Override
		public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		}
	}

	public static void init() {
		EntityRenderers.register(GameModule.SIGN, NothingRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(GameNet.Audio.TYPE, (p, ctx) -> {
			switch (p.op()) {
				case GameNet.Audio.MUSIC_LOOP -> MusicPlayer.play(p.sound(), true, p.volume(), p.fade());
				case GameNet.Audio.MUSIC_ONCE -> MusicPlayer.play(p.sound(), false, p.volume(), p.fade());
				case GameNet.Audio.UI -> MusicPlayer.ui(p.sound(), p.volume(), p.pitch());
				case GameNet.Audio.STOP -> MusicPlayer.stop(p.sound(), p.fade());
				default -> {
				}
			}
			GodzillaMod.LOGGER.info("godzilla audio: op {} {} vol {} fade {}", p.op(), p.sound(), p.volume(), p.fade());
		});
		ClientPlayNetworking.registerGlobalReceiver(GameNet.Hud.TYPE, (p, ctx) -> GameHud.onHud(p));
		ClientPlayNetworking.registerGlobalReceiver(GameNet.Cue.TYPE, (p, ctx) -> GameHud.onCue(p));
		ClientPlayNetworking.registerGlobalReceiver(GameNet.Results.TYPE, (p, ctx) -> GameHud.onResults(p));

		HudElementRegistry.addLast(GodzillaMod.id("game_hud"), GameHud::render);
		int[] fpsTick = {0};
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			GameHud.tick();
			MusicPlayer.tick();
			if (++fpsTick[0] % 200 == 0 && mc.level != null) {
				GodzillaMod.LOGGER.info("godzilla fps: {}", mc.getFps());
			}
		});
	}
}
