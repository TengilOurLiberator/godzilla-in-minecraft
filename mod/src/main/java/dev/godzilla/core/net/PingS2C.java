package dev.godzilla.core.net;

import dev.godzilla.core.GodzillaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client. The client answers with {@link PongC2S}. */
public record PingS2C(long id, long serverNanos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PingS2C> TYPE = new CustomPacketPayload.Type<>(GodzillaMod.id("ping"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PingS2C> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PingS2C::id,
			ByteBufCodecs.VAR_LONG, PingS2C::serverNanos,
			PingS2C::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
