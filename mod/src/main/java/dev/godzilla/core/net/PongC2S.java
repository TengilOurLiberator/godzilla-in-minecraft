package dev.godzilla.core.net;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server. Also sent on its own (id 0) when the player presses the test key. */
public record PongC2S(long id, long serverNanos, boolean vr) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PongC2S> TYPE = new CustomPacketPayload.Type<>(GodzillaMod.id("pong"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PongC2S> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PongC2S::id,
			ByteBufCodecs.VAR_LONG, PongC2S::serverNanos,
			ByteBufCodecs.BOOL, PongC2S::vr,
			PongC2S::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
