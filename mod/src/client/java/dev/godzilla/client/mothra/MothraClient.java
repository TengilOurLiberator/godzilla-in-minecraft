package dev.godzilla.client.mothra;

import dev.godzilla.core.GodzillaMod;
import dev.godzilla.mothra.Mothra;
import dev.godzilla.mothra.MothraModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Client side of Mothra: model layer, renderers, and the wing-beat sound that follows her. */
public final class MothraClient {
	private MothraClient() {
	}

	/** Loops mothra.wing_loop at her position for as long as she exists; louder and faster with the beat. */
	private static final class WingSound extends AbstractTickableSoundInstance {
		private final Mothra mothra;

		WingSound(Mothra mothra) {
			super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(GodzillaMod.MOD_ID, "mothra.wing_loop")),
					SoundSource.HOSTILE, RandomSource.create());
			this.mothra = mothra;
			this.looping = true;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.volume = 1.0f;
			this.x = mothra.getX();
			this.y = mothra.getY();
			this.z = mothra.getZ();
		}

		@Override
		public void tick() {
			if (mothra.isRemoved() || mothra.mode() == Mothra.Mode.DEAD) {
				stop();
				return;
			}
			x = mothra.getX();
			y = mothra.getY() + 3;
			z = mothra.getZ();
			pitch = Math.max(0.5f, 0.75f + 0.3f * mothra.animSpeed);
			volume = mothra.mode() == Mothra.Mode.DIVE ? 0.5f : 1.0f + 0.4f * Math.min(1f, mothra.animSpeed - 1f);
		}
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(MothraRenderer.LAYER, MothraModel::createBodyLayer);
		EntityRenderers.register(MothraModule.MOTHRA, MothraRenderer::new);
		EntityRenderers.register(MothraModule.CLOUD, ScaleCloudRenderer::new);
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mothra m) {
				Minecraft.getInstance().getSoundManager().play(new WingSound(m));
			}
		});
	}
}
