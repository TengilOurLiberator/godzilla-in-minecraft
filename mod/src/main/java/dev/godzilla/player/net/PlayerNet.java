package dev.godzilla.player.net;

import dev.godzilla.core.GodzillaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Payloads of the player module. All registered on both sides by {@link #register()}. */
public final class PlayerNet {
	private PlayerNet() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String name) {
		return new CustomPacketPayload.Type<>(GodzillaMod.id("player/" + name));
	}

	private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) {
		b.writeDouble(v.x);
		b.writeDouble(v.y);
		b.writeDouble(v.z);
	}

	private static Vec3 readVec(RegistryFriendlyByteBuf b) {
		return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
	}

	/** Client to server, every client tick while Godzilla: trigger and A held, and swipe presses (flat clients). */
	public record InputC2S(boolean attack, boolean use, boolean swipeLeft, boolean swipeRight) implements CustomPacketPayload {
		public static final Type<InputC2S> TYPE = mk("input");
		public static final StreamCodec<RegistryFriendlyByteBuf, InputC2S> CODEC = StreamCodec.of(
				(b, v) -> b.writeByte((v.attack ? 1 : 0) | (v.use ? 2 : 0) | (v.swipeLeft ? 4 : 0) | (v.swipeRight ? 8 : 0)),
				b -> {
					int f = b.readUnsignedByte();
					return new InputC2S((f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to every client, every tick for each Godzilla: what the body is doing. */
	public record StateS2C(int entityId, int flags, float gauge, float charge, float beamLength, int roarTicks,
						   int swipeLeft, int swipeRight, float plough, Vec3 headPos, Vec3 headDir,
						   Vec3 rightHand, Vec3 rightDir, Vec3 leftHand, Vec3 leftDir) implements CustomPacketPayload {
		public static final int ACTIVE = 1;
		public static final int CHARGING = 2;
		public static final int FIRING = 4;
		public static final int LOCKOUT = 8;
		public static final int VR = 16;
		public static final int RISE = 32;
		public static final int ATTACKS = 64;
		public static final int ENDED = 128;
			/** Defeat: the body topples over. */
			public static final int FALLEN = 256;

		public static final Type<StateS2C> TYPE = mk("state");
		public static final StreamCodec<RegistryFriendlyByteBuf, StateS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entityId);
					b.writeVarInt(v.flags);
					b.writeFloat(v.gauge);
					b.writeFloat(v.charge);
					b.writeFloat(v.beamLength);
					b.writeVarInt(v.roarTicks);
					b.writeVarInt(v.swipeLeft + 1);
					b.writeVarInt(v.swipeRight + 1);
					b.writeFloat(v.plough);
					writeVec(b, v.headPos);
					writeVec(b, v.headDir);
					writeVec(b, v.rightHand);
					writeVec(b, v.rightDir);
					writeVec(b, v.leftHand);
					writeVec(b, v.leftDir);
				},
				b -> new StateS2C(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(),
						b.readVarInt() - 1, b.readVarInt() - 1, b.readFloat(), readVec(b), readVec(b), readVec(b), readVec(b), readVec(b), readVec(b)));

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Camera shake and controller rumble for the Godzilla player. */
	public record FxS2C(float strength, int ticks) implements CustomPacketPayload {
		public static final Type<FxS2C> TYPE = mk("fx");
		public static final StreamCodec<RegistryFriendlyByteBuf, FxS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeFloat(v.strength);
					b.writeVarInt(v.ticks);
				},
				b -> new FxS2C(b.readFloat(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A harmless hit from a world position: flash on that side and a rumble. */
	public record FlashS2C(Vec3 from, float strength) implements CustomPacketPayload {
		public static final Type<FlashS2C> TYPE = mk("flash");
		public static final StreamCodec<RegistryFriendlyByteBuf, FlashS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					writeVec(b, v.from);
					b.writeFloat(v.strength);
				},
				b -> new FlashS2C(readVec(b), b.readFloat()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Controller pulse after a hand hit: hand 0 = right, 1 = left, 2 = both. */
	public record HapticS2C(int hand, float strength, float seconds) implements CustomPacketPayload {
		public static final Type<HapticS2C> TYPE = mk("haptic");
		public static final StreamCodec<RegistryFriendlyByteBuf, HapticS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeByte(v.hand);
					b.writeFloat(v.strength);
					b.writeFloat(v.seconds);
				},
				b -> new HapticS2C(b.readUnsignedByte(), b.readFloat(), b.readFloat()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Debug: kind 0 = camera (value 0 first, 1 third back, 2 third front), 1 = walk forward for value ticks, 2 = walk backward, 3 = turn right for value ticks. */
	public record DebugS2C(int kind, int value) implements CustomPacketPayload {
		public static final Type<DebugS2C> TYPE = mk("debug");
		public static final StreamCodec<RegistryFriendlyByteBuf, DebugS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeByte(v.kind);
					b.writeVarInt(v.value);
				},
				b -> new DebugS2C(b.readUnsignedByte(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The rise out of the sea: the client moves the player along the scripted path (smooth, no server teleport per tick). */
	public record RiseS2C(boolean active, Vec3 start, float yaw, int tick0) implements CustomPacketPayload {
		public static final Type<RiseS2C> TYPE = mk("rise");
		public static final StreamCodec<RegistryFriendlyByteBuf, RiseS2C> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeBoolean(v.active);
					writeVec(b, v.start);
					b.writeFloat(v.yaw);
					b.writeVarInt(v.tick0);
				},
				b -> new RiseS2C(b.readBoolean(), readVec(b), b.readFloat(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(InputC2S.TYPE, InputC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StateS2C.TYPE, StateS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FxS2C.TYPE, FxS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FlashS2C.TYPE, FlashS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HapticS2C.TYPE, HapticS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DebugS2C.TYPE, DebugS2C.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RiseS2C.TYPE, RiseS2C.CODEC);
	}
}

