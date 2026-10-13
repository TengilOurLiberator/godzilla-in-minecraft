package dev.godzilla.destruction;

import dev.godzilla.core.GodzillaMod;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client payloads of the destruction module. Everything visual about debris, falling masses, fires and explosions
 * travels in these compact packets; the client simulates it (dev.godzilla.client.destruction).
 */
public final class Packets {
	private Packets() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DebrisBurst.TYPE, DebrisBurst.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Mass.TYPE, Mass.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fire.TYPE, Fire.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClientCmd.TYPE, ClientCmd.CODEC);
	}

	static int zig(int v) {
		return (v << 1) ^ (v >> 31);
	}

	static int unzig(int v) {
		return (v >>> 1) ^ -(v & 1);
	}

	/** Writes the (rel xyz triples, state ids) lists with a palette of distinct state ids. */
	static void writeBlocks(RegistryFriendlyByteBuf buf, int count, int[] rel, int[] ids) {
		Int2IntOpenHashMap index = new Int2IntOpenHashMap();
		index.defaultReturnValue(-1);
		IntArrayList palette = new IntArrayList();
		int[] pi = new int[count];
		for (int i = 0; i < count; i++) {
			int p = index.get(ids[i]);
			if (p < 0) {
				p = palette.size();
				index.put(ids[i], p);
				palette.add(ids[i]);
			}
			pi[i] = p;
		}
		buf.writeVarInt(palette.size());
		for (int i = 0; i < palette.size(); i++) {
			buf.writeVarInt(palette.getInt(i));
		}
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			buf.writeVarInt(zig(rel[3 * i]));
			buf.writeVarInt(zig(rel[3 * i + 1]));
			buf.writeVarInt(zig(rel[3 * i + 2]));
			buf.writeVarInt(pi[i]);
		}
	}

	/** @return {rel, ids} */
	static int[][] readBlocks(RegistryFriendlyByteBuf buf) {
		int pn = buf.readVarInt();
		int[] palette = new int[pn];
		for (int i = 0; i < pn; i++) {
			palette[i] = buf.readVarInt();
		}
		int n = buf.readVarInt();
		int[] rel = new int[3 * n];
		int[] ids = new int[n];
		for (int i = 0; i < n; i++) {
			rel[3 * i] = unzig(buf.readVarInt());
			rel[3 * i + 1] = unzig(buf.readVarInt());
			rel[3 * i + 2] = unzig(buf.readVarInt());
			ids[i] = palette[buf.readVarInt()];
		}
		return new int[][]{rel, ids};
	}

	/**
	 * A burst of flying pieces. rel = x,y,z offsets (blocks) of each removed block from origin; ids = Block.getId(state).
	 * mode: 0 thrown along (ix,iy,iz) in blocks per tick with spread; 1 radial blast from the origin (power = speed);
	 * 2 collapse crumble (outward and down). power scales the spread.
	 */
	public record DebrisBurst(BlockPos origin, float ix, float iy, float iz, int mode, float power, int[] rel, int[] ids)
			implements CustomPacketPayload {
		public static final Type<DebrisBurst> TYPE = new Type<>(GodzillaMod.id("debris_burst"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DebrisBurst> CODEC = StreamCodec.of(
				(buf, v) -> {
					buf.writeBlockPos(v.origin);
					buf.writeFloat(v.ix);
					buf.writeFloat(v.iy);
					buf.writeFloat(v.iz);
					buf.writeByte(v.mode);
					buf.writeFloat(v.power);
					writeBlocks(buf, v.ids.length, v.rel, v.ids);
				},
				buf -> {
					BlockPos o = buf.readBlockPos();
					float ix = buf.readFloat(), iy = buf.readFloat(), iz = buf.readFloat();
					int mode = buf.readByte();
					float power = buf.readFloat();
					int[][] b = readBlocks(buf);
					return new DebrisBurst(o, ix, iy, iz, mode, power, b[0], b[1]);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A mass of blocks that has just come loose and moves as one rigid cluster. Blocks are at origin + rel (their original
	 * world positions, already removed from the world on the server). mode 0: falls straight down (path = drop in 1/32 block
	 * per tick); 1: pancake, same as 0 but crushes what is below; 2: topples about the pivot (path = angle in 1/1000 rad per
	 * tick) toward the horizontal unit direction (ax, az); when it meets the ground first (angle c1 > 0) it carries on turning about the contact point (tx,ty,tz).
	 */
	public record Mass(int id, BlockPos origin, int mode, short[] path, float px, float py, float pz, float ax, float az,
					   float c1, float tx, float ty, float tz, float dust, int[] rel, int[] ids) implements CustomPacketPayload {
		public static final Type<Mass> TYPE = new Type<>(GodzillaMod.id("mass"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Mass> CODEC = StreamCodec.of(
				(buf, v) -> {
					buf.writeVarInt(v.id);
					buf.writeBlockPos(v.origin);
					buf.writeByte(v.mode);
					buf.writeVarInt(v.path.length);
					for (short s : v.path) {
						buf.writeShort(s);
					}
					buf.writeFloat(v.px);
					buf.writeFloat(v.py);
					buf.writeFloat(v.pz);
					buf.writeFloat(v.ax);
					buf.writeFloat(v.az);
					buf.writeFloat(v.c1);
					buf.writeFloat(v.tx);
					buf.writeFloat(v.ty);
					buf.writeFloat(v.tz);
					buf.writeFloat(v.dust);
					writeBlocks(buf, v.ids.length, v.rel, v.ids);
				},
				buf -> {
					int id = buf.readVarInt();
					BlockPos o = buf.readBlockPos();
					int mode = buf.readByte();
					short[] path = new short[buf.readVarInt()];
					for (int i = 0; i < path.length; i++) {
						path[i] = buf.readShort();
					}
					float px = buf.readFloat(), py = buf.readFloat(), pz = buf.readFloat(), ax = buf.readFloat(), az = buf.readFloat();
					float c1 = buf.readFloat(), tx = buf.readFloat(), ty = buf.readFloat(), tz = buf.readFloat();
					float dust = buf.readFloat();
					int[][] b = readBlocks(buf);
					return new Mass(id, o, mode, path, px, py, pz, ax, az, c1, tx, ty, tz, dust, b[0], b[1]);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One-shot visual effects. */
	public record Fx(int kind, float x, float y, float z, float a, float b, int n) implements CustomPacketPayload {
		public static final int EXPLOSION = 1;   // a = radius, b = 1 for a fuel blast
		public static final int SMOKE_COLUMN = 2; // a = height, b = width
		public static final int DUST = 3;         // a = radius, n = amount
		public static final int RESET = 99;       // drop all debris, masses, fires
		public static final Type<Fx> TYPE = new Type<>(GodzillaMod.id("fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, v) -> {
					buf.writeByte(v.kind);
					buf.writeFloat(v.x);
					buf.writeFloat(v.y);
					buf.writeFloat(v.z);
					buf.writeFloat(v.a);
					buf.writeFloat(v.b);
					buf.writeVarInt(v.n);
				},
				buf -> new Fx(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A burning place: op 0 add (id, centre, radius, ticks to live), 1 remove id, 2 remove all. */
	public record Fire(int op, int id, float x, float y, float z, float radius, int ttl) implements CustomPacketPayload {
		public static final Type<Fire> TYPE = new Type<>(GodzillaMod.id("fire"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC = StreamCodec.of(
				(buf, v) -> {
					buf.writeByte(v.op);
					buf.writeVarInt(v.id);
					buf.writeFloat(v.x);
					buf.writeFloat(v.y);
					buf.writeFloat(v.z);
					buf.writeFloat(v.radius);
					buf.writeVarInt(v.ttl);
				},
				buf -> new Fire(buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Test hook: a text command for the client's debris system ("stats", "max 20000", "mode entities", "bench 10000"...). */
	public record ClientCmd(String text) implements CustomPacketPayload {
		public static final Type<ClientCmd> TYPE = new Type<>(GodzillaMod.id("client_cmd"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientCmd> CODEC = StreamCodec.of(
				(buf, v) -> buf.writeUtf(v.text, 256),
				buf -> new ClientCmd(buf.readUtf(256)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
