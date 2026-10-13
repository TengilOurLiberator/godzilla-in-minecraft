package dev.godzilla.core.net;

import dev.godzilla.core.GodzillaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Payload registration. Payload types must be registered on both sides (this runs in the common initialiser); receivers are
 * registered where the code lives: the server receiver here, the client receiver in GodzillaClient.
 * Round trip: server sends {@link PingS2C}, client logs "GODZILLA-NET client got ping" and answers {@link PongC2S},
 * server logs "GODZILLA-NET server got pong".
 */
public final class GodzillaNetworking {
	private static final AtomicLong NEXT_ID = new AtomicLong(1);

	private GodzillaNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(PingS2C.TYPE, PingS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PongC2S.TYPE, PongC2S.CODEC);

		// handlers run on the server thread
		ServerPlayNetworking.registerGlobalReceiver(PongC2S.TYPE, (payload, context) -> {
			long rttMicros = (System.nanoTime() - payload.serverNanos()) / 1000;
			GodzillaMod.LOGGER.info("GODZILLA-NET server got pong id={} from {} vr={} rtt={}us",
					payload.id(), context.player().getGameProfile().name(), payload.vr(), payload.id() == 0 ? -1 : rttMicros);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendPing(handler.getPlayer()));
	}

	/** Sends a ping to one player and returns its id. */
	public static long sendPing(ServerPlayer player) {
		long id = NEXT_ID.getAndIncrement();
		ServerPlayNetworking.send(player, new PingS2C(id, System.nanoTime()));
		GodzillaMod.LOGGER.info("GODZILLA-NET server sent ping id={} to {}", id, player.getGameProfile().name());
		return id;
	}
}
