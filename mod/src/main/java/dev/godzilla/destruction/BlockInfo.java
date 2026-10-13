package dev.godzilla.destruction;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** Classification of block states for smashing and for structural analysis, cached per state id. */
public final class BlockInfo {
	public static final byte NONE = 0;    // air, fluid, bedrock, barrier: never removed, never structure
	public static final byte STRONG = 1;  // load bearing
	public static final byte WEAK = 2;    // glass, bars, ladders, leaves...: hangs on, bears nothing
	public static final byte GLASS = 3;   // weak and sounds like glass

	private static byte[] table = new byte[0];

	private BlockInfo() {
	}

	public static byte type(BlockState s) {
		int id = Block.BLOCK_STATE_REGISTRY.getId(s);
		if (id >= table.length) {
			int n = Block.BLOCK_STATE_REGISTRY.size() + 64;
			byte[] t = Arrays.copyOf(table, n);
			Arrays.fill(t, table.length, n, (byte) -1);
			table = t;
		}
		byte v = table[id];
		if (v < 0) {
			v = compute(s);
			table[id] = v;
		}
		return v;
	}

	public static boolean removable(BlockState s) {
		return type(s) != NONE;
	}

	private static byte compute(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK) || s.is(Blocks.BARRIER) || s.is(Blocks.LIGHT)
				|| s.is(Blocks.STRUCTURE_VOID) || s.is(Blocks.MOVING_PISTON) || s.is(Blocks.END_PORTAL) || s.is(Blocks.NETHER_PORTAL)) {
			return NONE;
		}
		if (s.getSoundType() == SoundType.GLASS) {
			return GLASS;
		}
		if (!s.canOcclude()) {
			return WEAK;
		}
		return STRONG;
	}

	public static boolean solid(byte t) {
		return t != NONE;
	}
}
