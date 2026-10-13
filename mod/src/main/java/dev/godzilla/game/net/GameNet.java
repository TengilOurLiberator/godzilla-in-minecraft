package dev.godzilla.game.net;

import dev.godzilla.core.GodzillaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Payloads of the game module (all server to client). */
public final class GameNet {
	private GameNet() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(Audio.TYPE, Audio.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Hud.TYPE, Hud.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Cue.TYPE, Cue.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Results.TYPE, Results.CODEC);
	}

	/**
	 * Music and interface sounds, played by the client without position so they are not affected by the 16x scale.
	 * ops: MUSIC_LOOP (looping stream), MUSIC_ONCE, UI (one shot), STOP (sound "" = all music; fade in ticks).
	 */
	public record Audio(int op, String sound, float volume, float pitch, int fade) implements CustomPacketPayload {
		public static final int MUSIC_LOOP = 0, MUSIC_ONCE = 1, UI = 2, STOP = 3;
		public static final Type<Audio> TYPE = new Type<>(GodzillaMod.id("audio"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Audio> CODEC = StreamCodec.ofMember(
				(v, b) -> {
					b.writeVarInt(v.op);
					b.writeUtf(v.sound);
					b.writeFloat(v.volume);
					b.writeFloat(v.pitch);
					b.writeVarInt(v.fade);
				},
				b -> new Audio(b.readVarInt(), b.readUtf(), b.readFloat(), b.readFloat(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The numbers behind the HUD, sent a few times a second. phase is Game.Phase.ordinal(); mothraMax is 0 while there is no
	 * Mothra. comboFrac is the remaining part of the quiet window (1 = just hit, 0 = about to break).
	 */
	public record Hud(int phase, boolean results, long score, int combo, float comboFrac, int runTicks,
					  float health, float healthMax, float mothra, float mothraMax) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(GodzillaMod.id("hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.ofMember(
				(v, b) -> {
					b.writeVarInt(v.phase);
					b.writeBoolean(v.results);
					b.writeVarLong(v.score);
					b.writeVarInt(v.combo);
					b.writeFloat(v.comboFrac);
					b.writeVarInt(v.runTicks);
					b.writeFloat(v.health);
					b.writeFloat(v.healthMax);
					b.writeFloat(v.mothra);
					b.writeFloat(v.mothraMax);
				},
				b -> new Hud(b.readVarInt(), b.readBoolean(), b.readVarLong(), b.readVarInt(), b.readFloat(), b.readVarInt(),
						b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One-off HUD events: TITLE (big title text), FLASH (red edge flash, strength 0..1), BANNER (a line of text, colour 0xRRGGBB). */
	public record Cue(int kind, float strength, String text, int color) implements CustomPacketPayload {
		public static final int TITLE = 0, FLASH = 1, BANNER = 2, COMBO_BREAK = 3;
		public static final Type<Cue> TYPE = new Type<>(GodzillaMod.id("cue"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cue> CODEC = StreamCodec.ofMember(
				(v, b) -> {
					b.writeVarInt(v.kind);
					b.writeFloat(v.strength);
					b.writeUtf(v.text);
					b.writeInt(v.color);
				},
				b -> new Cue(b.readVarInt(), b.readFloat(), b.readUtf(), b.readInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The score breakdown: groups = buildings, landmarks, vehicles, people, military, Mothra, bonuses. */
	public record Results(boolean victory, long score, long best, boolean newBest, String rank, long[] groups, int[] kills,
						  int seconds) implements CustomPacketPayload {
		public static final Type<Results> TYPE = new Type<>(GodzillaMod.id("results"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Results> CODEC = StreamCodec.ofMember(
				(v, b) -> {
					b.writeBoolean(v.victory);
					b.writeVarLong(v.score);
					b.writeVarLong(v.best);
					b.writeBoolean(v.newBest);
					b.writeUtf(v.rank);
					b.writeVarInt(v.groups.length);
					for (long g : v.groups) {
						b.writeVarLong(g);
					}
					b.writeVarInt(v.kills.length);
					for (int k : v.kills) {
						b.writeVarInt(k);
					}
					b.writeVarInt(v.seconds);
				},
				b -> {
					boolean victory = b.readBoolean();
					long score = b.readVarLong();
					long best = b.readVarLong();
					boolean nb = b.readBoolean();
					String rank = b.readUtf();
					long[] groups = new long[b.readVarInt()];
					for (int i = 0; i < groups.length; i++) {
						groups[i] = b.readVarLong();
					}
					int[] kills = new int[b.readVarInt()];
					for (int i = 0; i < kills.length; i++) {
						kills[i] = b.readVarInt();
					}
					return new Results(victory, score, best, nb, rank, groups, kills, b.readVarInt());
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
