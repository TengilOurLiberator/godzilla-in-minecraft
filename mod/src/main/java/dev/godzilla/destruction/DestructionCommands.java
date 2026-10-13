package dev.godzilla.destruction;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.godzilla.api.Cause;
import dev.godzilla.api.CityData;
import dev.godzilla.api.Gz;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Test and tuning commands of the destruction module. */
final class DestructionCommands {
	private DestructionCommands() {
	}

	static void add(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext ctx) {
		root.then(Commands.literal("destruction")
				.then(Commands.literal("stats").executes(c -> stats(c.getSource())))
				.then(Commands.literal("reset").executes(c -> {
					DestructionModule.reset();
					c.getSource().sendSuccess(() -> Component.literal("destruction state cleared"), false);
					return 1;
				}))
				.then(Commands.literal("resetstats").executes(c -> {
					DestructionService.resetStats();
					return 1;
				}))
				.then(Commands.literal("set")
						.then(Commands.argument("key", StringArgumentType.word())
								.then(Commands.argument("value", IntegerArgumentType.integer(0, 1_000_000))
										.executes(c -> set(c.getSource(), StringArgumentType.getString(c, "key"), IntegerArgumentType.getInteger(c, "value"))))))
				.then(Commands.literal("client")
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
							String text = StringArgumentType.getString(c, "text");
							for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
								ServerPlayNetworking.send(p, new Packets.ClientCmd(text));
							}
							return 1;
						}))));

		// /godzilla collapse <buildingId> [lean]: damage the base of a building so that it comes down
		root.then(Commands.literal("collapse")
				.then(Commands.argument("id", IntegerArgumentType.integer(0))
						.executes(c -> collapse(c.getSource(), IntegerArgumentType.getInteger(c, "id"), false))
						.then(Commands.literal("lean").executes(c -> collapse(c.getSource(), IntegerArgumentType.getInteger(c, "id"), true)))));

		// /godzilla debris <count> <pos> [radial|up|dir]
		root.then(Commands.literal("debris")
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 200000))
						.then(Commands.argument("pos", Vec3Argument.vec3())
								.executes(c -> debris(c.getSource(), IntegerArgumentType.getInteger(c, "count"), Vec3Argument.getVec3(c, "pos"), "radial"))
								.then(Commands.argument("mode", StringArgumentType.word())
										.executes(c -> debris(c.getSource(), IntegerArgumentType.getInteger(c, "count"), Vec3Argument.getVec3(c, "pos"),
												StringArgumentType.getString(c, "mode")))))));

		root.then(Commands.literal("ignite")
				.then(Commands.argument("pos", Vec3Argument.vec3())
						.then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 40)).executes(c -> {
							Gz.destruction().ignite(c.getSource().getLevel(), Vec3Argument.getVec3(c, "pos"), DoubleArgumentType.getDouble(c, "radius"));
							return 1;
						}))));

		// /godzilla build ...: test structures on the flat world
		root.then(Commands.literal("build")
				.then(Commands.literal("ground")
						.then(Commands.argument("from", BlockPosArgument.blockPos())
								.then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> ground(c.getSource(),
										BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"))))))
				.then(Commands.literal("tower")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.then(Commands.argument("w", IntegerArgumentType.integer(3, 60))
										.then(Commands.argument("d", IntegerArgumentType.integer(3, 60))
												.then(Commands.argument("h", IntegerArgumentType.integer(3, 150)).executes(c -> tower(c.getSource(),
														BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "w"),
														IntegerArgumentType.getInteger(c, "d"), IntegerArgumentType.getInteger(c, "h"))))))))
				.then(Commands.literal("lattice")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.then(Commands.argument("w", IntegerArgumentType.integer(4, 40))
										.then(Commands.argument("h", IntegerArgumentType.integer(6, 150)).executes(c -> lattice(c.getSource(),
												BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "w"),
												IntegerArgumentType.getInteger(c, "h"))))))));
	}

	private static int set(CommandSourceStack src, String key, int v) {
		switch (key) {
			case "pieces" -> DestructionService.pieceBudgetPerTick = v;
			case "burst" -> DestructionService.maxPiecesPerBurst = v;
			case "sounds" -> DestructionService.soundBudgetPerTick = v;
			default -> {
				src.sendFailure(Component.literal("keys: pieces (per tick), burst (per packet), sounds (per tick)"));
				return 0;
			}
		}
		src.sendSuccess(() -> Component.literal(key + " = " + v), false);
		return 1;
	}

	private static int stats(CommandSourceStack src) {
		long calls = DestructionService.statCalls;
		double msTotal = DestructionService.statNanos / 1e6;
		String s = "blocks removed %d in %d calls (%.2f ms in smash calls, %.0f blocks/ms); pieces sent %d (%d dropped by budget) in %d bursts; "
				.formatted(DestructionService.statBlocks, calls, msTotal, DestructionService.statBlocks / Math.max(msTotal, 0.001),
						DestructionService.statPieces, DestructionService.statDroppedPieces, DestructionService.statBursts);
		String t = "structures %d (ad-hoc %d), analyses %d (snapshot %.1f ms total, worker %.1f ms total, last %.1f ms), masses %d (%d blocks, largest %d), rubble %d; "
				.formatted(Structures.liveCount(), Structures.adhocCount, Collapses.statAnalyses, Collapses.statSnapshotNanos / 1e6,
						Collapses.statWorkerNanos / 1e6, Collapses.workerNanosLast / 1e6, Collapses.statMasses, Collapses.statMassBlocks,
						Collapses.statMaxMass, Collapses.statRubble);
		String u2 = " worst single steps: mass removal slice %.1f ms, launch %.1f ms, landing %.1f ms; ".formatted(Collapses.maxStepNs / 1e6, Collapses.maxLaunchNs / 1e6, Collapses.maxLandNs / 1e6);
		u2 += "slice cells %d: beforeRemoval %.2f us/block, setBlock %.2f us/block; ".formatted(Collapses.sliceCells, Collapses.beforeNs / 1e3 / Math.max(1, Collapses.sliceCells), Collapses.removeNs / 1e3 / Math.max(1, Collapses.sliceCells));
		String u = u2 + "queues: jobs %d events %d; fires %d; tick time in destruction: avg %.3f ms, max %.3f ms over %d ticks; piece budget %d/tick"
				.formatted(Collapses.jobs(), Collapses.events(), Fires.count(),
						DestructionService.sumTickNanos / 1e6 / Math.max(1, DestructionService.tickCount), DestructionService.maxTickNanos / 1e6,
						DestructionService.tickCount, DestructionService.pieceBudgetPerTick);
		u += "; WHOLE server tick (all work): avg %.2f ms, max %.1f ms over %d ticks, histogram <5/<10/<20/<35/<50/>=50 ms: %d/%d/%d/%d/%d/%d".formatted(
				DestructionService.wholeTickSum / 1e6 / Math.max(1, DestructionService.wholeTickCount), DestructionService.wholeTickMax / 1e6, DestructionService.wholeTickCount,
				DestructionService.wholeHist[0], DestructionService.wholeHist[1], DestructionService.wholeHist[2], DestructionService.wholeHist[3], DestructionService.wholeHist[4], DestructionService.wholeHist[5]);
		String uu = u;
		src.sendSuccess(() -> Component.literal(s + t + uu), false);
		dev.godzilla.core.GodzillaMod.LOGGER.info("DESTRUCTION-STATS {}{}{}", s, t, u);
		return 1;
	}

	private static int collapse(CommandSourceStack src, int id, boolean lean) {
		Structures.ensureCity();
		Struct s = Structures.get(id);
		if (s == null) {
			src.sendFailure(Component.literal("no building " + id));
			return 0;
		}
		ServerLevel level = src.getLevel();
		// knock out the foot of the building: two layers, all round or only one half
		int y0 = s.y0, y1 = Math.min(s.y1, s.y0 + 1);
		AABB box = new AABB(s.x0, y0, s.z0, s.x1 + 1, y1 + 1, s.z1 + 1);
		if (lean) {
			double mid = (s.x0 + s.x1 + 1) / 2.0;
			box = new AABB(s.x0, y0, s.z0, mid, y1 + 1, s.z1 + 1);
		}
		int n = Gz.destruction().smashBox(level, box, new Vec3(0.2, 0.1, 0), Cause.BODY);
		src.sendSuccess(() -> Component.literal("knocked out %d blocks at the foot of building %d (box %d,%d,%d to %d,%d,%d)".formatted(n, id, s.x0, s.y0, s.z0, s.x1, s.y1, s.z1)), false);
		return n;
	}

	private static int debris(CommandSourceStack src, int count, Vec3 at, String mode) {
		ServerLevel level = src.getLevel();
		RandomSource rnd = RandomSource.createThreadLocalInstance(count);
		BlockState[] pal = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
				Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState(), Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(),
				Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()};
		int[] pid = new int[pal.length];
		for (int i = 0; i < pal.length; i++) {
			pid[i] = Block.BLOCK_STATE_REGISTRY.getId(pal[i]);
		}
		BlockPos origin = BlockPos.containing(at);
		int sent = 0;
		while (sent < count) {
			int n = Math.min(count - sent, 12000);
			int[] rel = new int[3 * n];
			int[] ids = new int[n];
			for (int i = 0; i < n; i++) {
				rel[3 * i] = rnd.nextInt(9) - 4;
				rel[3 * i + 1] = rnd.nextInt(7) - 3;
				rel[3 * i + 2] = rnd.nextInt(9) - 4;
				ids[i] = pid[rnd.nextInt(pid.length)];
			}
			int m = mode.equals("radial") ? 1 : 0;
			float iy = mode.equals("up") ? 0.9f : 0.3f;
			Packets.DebrisBurst pk = new Packets.DebrisBurst(origin, mode.equals("dir") ? 0.9f : 0f, iy, 0f, m, 1.2f, rel, ids);
			DestructionService.broadcast(level, at, pk, 1000);
			sent += n;
		}
		final int total = sent;
		src.sendSuccess(() -> Component.literal("sent " + total + " pieces (" + mode + ")"), false);
		return total;
	}

	private static int ground(CommandSourceStack src, BlockPos a, BlockPos b) {
		int gy = Structures.groundY();
		ServerLevel level = src.getLevel();
		BlockState stone = Blocks.STONE.defaultBlockState();
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
			for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
				for (int y = 40; y <= gy; y++) {
					level.setBlock(p.set(x, y, z), stone, DestructionService.FLAGS, 0);
					n++;
				}
			}
		}
		int total = n;
		src.sendSuccess(() -> Component.literal("ground slab of " + total + " blocks, top at y=" + gy), false);
		return total;
	}

	/** A hollow tower: walls of concrete with window bands, a floor every 4 layers, corner columns, a flat roof. */
	private static int tower(CommandSourceStack src, BlockPos o, int w, int d, int h) {
		ServerLevel level = src.getLevel();
		BlockState wall = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
		BlockState col = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
		BlockState glass = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
		BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 0; y < h; y++) {
			boolean floorLayer = y % 4 == 0 || y == h - 1;
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
					BlockState st;
					if (floorLayer) {
						st = edge ? col : floor;
					} else if (!edge) {
						continue;
					} else if (corner || ((x % 4 == 0 || z % 4 == 0) && !(x % 4 == 0 && z % 4 == 0 && false))) {
						st = (corner || (x % 4 == 0 && (z == 0 || z == d - 1)) || (z % 4 == 0 && (x == 0 || x == w - 1))) ? col : wall;
					} else {
						st = glass;
					}
					level.setBlock(p.set(o.getX() + x, o.getY() + y, o.getZ() + z), st, DestructionService.FLAGS, 0);
					n++;
				}
			}
		}
		int total = n;
		src.sendSuccess(() -> Component.literal("tower %dx%dx%d: %d blocks".formatted(w, d, h, total)), false);
		return total;
	}

	/** A lattice tower: four slanted legs with cross braces, narrowing to the top (like a miniature Tokyo Tower). */
	private static int lattice(CommandSourceStack src, BlockPos o, int w, int h) {
		ServerLevel level = src.getLevel();
		BlockState leg = Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState();
		BlockState brace = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int n = 0;
		for (int y = 0; y < h; y++) {
			double f = 1.0 - 0.8 * y / h;
			int half = Math.max(1, (int) Math.round((w - 1) / 2.0 * f));
			int cx = o.getX() + (w - 1) / 2, cz = o.getZ() + (w - 1) / 2;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					level.setBlock(p.set(cx + sx * half, o.getY() + y, cz + sz * half), leg, DestructionService.FLAGS, 0);
					n++;
					if (half > 1) {
						level.setBlock(p.set(cx + sx * (half - 1), o.getY() + y, cz + sz * half), leg, DestructionService.FLAGS, 0);
						n++;
					}
				}
			}
			if (y % 5 == 4) {
				for (int i = -half; i <= half; i++) {
					level.setBlock(p.set(cx + i, o.getY() + y, cz + half), brace, DestructionService.FLAGS, 0);
					level.setBlock(p.set(cx + i, o.getY() + y, cz - half), brace, DestructionService.FLAGS, 0);
					level.setBlock(p.set(cx + half, o.getY() + y, cz + i), brace, DestructionService.FLAGS, 0);
					level.setBlock(p.set(cx - half, o.getY() + y, cz + i), brace, DestructionService.FLAGS, 0);
					n += 4;
				}
			}
		}
		int total = n;
		src.sendSuccess(() -> Component.literal("lattice tower %d wide %d high: %d blocks".formatted(w, h, total)), false);
		return total;
	}
}
