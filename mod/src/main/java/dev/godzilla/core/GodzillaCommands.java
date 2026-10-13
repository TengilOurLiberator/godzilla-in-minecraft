package dev.godzilla.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.godzilla.core.net.GodzillaNetworking;
import dev.godzilla.vr.PoseSnapshot;
import dev.godzilla.vr.Vr;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /godzilla scale &lt;value&gt; [targets]   set the vanilla scale attribute (clamped by the game to 0.0625..16)
 * /godzilla pose [targets]              print head and hand positions (Vivecraft data if the player is in VR)
 * /godzilla ping [targets]              server to client payload; the client answers with a C2S payload
 */
public final class GodzillaCommands {
	/** Range of the vanilla Attributes.SCALE (RangedAttribute 1.0, 0.0625, 16.0). */
	public static final double SCALE_MIN = 0.0625;
	public static final double SCALE_MAX = 16.0;

	/** A module's own subcommands of /godzilla. Call {@link #addSubcommands} from the module's init(). */
	public interface Sub {
		void add(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context);
	}

	private static final List<Sub> SUBS = new ArrayList<>();

	public static void addSubcommands(Sub sub) {
		SUBS.add(sub);
	}

	private GodzillaCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("godzilla")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("scale")
						.then(Commands.argument("value", DoubleArgumentType.doubleArg())
								.executes(c -> scale(c.getSource(), self(c.getSource()), DoubleArgumentType.getDouble(c, "value")))
								.then(Commands.argument("targets", EntityArgument.players())
										.executes(c -> scale(c.getSource(), EntityArgument.getPlayers(c, "targets"),
												DoubleArgumentType.getDouble(c, "value"))))))
				.then(Commands.literal("pose")
						.executes(c -> pose(c.getSource(), self(c.getSource())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> pose(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
				// dev tool: /godzilla fill <setblock|sections|sections_relight> <from> <to> <block>   (prints blocks and milliseconds)
				.then(Commands.literal("fill")
						.then(Commands.argument("mode", StringArgumentType.word())
								.then(Commands.argument("from", BlockPosArgument.blockPos())
										.then(Commands.argument("to", BlockPosArgument.blockPos())
												.then(Commands.argument("block", BlockStateArgument.block(context))
														.executes(c -> fill(c.getSource(), StringArgumentType.getString(c, "mode"),
																BlockPosArgument.getLoadedBlockPos(c, "from"), BlockPosArgument.getLoadedBlockPos(c, "to"),
																BlockStateArgument.getBlock(c, "block").getState())))))))
				.then(Commands.literal("ping")
						.executes(c -> ping(c.getSource(), self(c.getSource())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> ping(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
				// the run: /godzilla start [player], /godzilla stop, /godzilla stamp (place or repair the city)
				.then(Commands.literal("start")
						.executes(c -> start(c.getSource(), self(c.getSource())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(c -> start(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
				.then(Commands.literal("stop").executes(c -> {
					Gz.game().stop();
					return 1;
				}))
				.then(Commands.literal("stamp").executes(c -> {
					long t0 = System.nanoTime();
					Gz.city().stamp(c.getSource().getLevel());
					c.getSource().sendSuccess(() -> Component.literal("city stamped in %.0f ms".formatted((System.nanoTime() - t0) / 1e6)), false);
					return 1;
				}))
				.then(Commands.literal("status").executes(c -> {
					Game g = Gz.game();
					ServerPlayer p = g.godzilla();
					c.getSource().sendSuccess(() -> Component.literal("phase %s (%d ticks), run %d ticks, godzilla %s, score %d, health %.0f/%.0f, city %s (%d buildings)".formatted(
							g.phase(), g.phaseTicks(), g.runTicks(), p == null ? "nobody" : p.getGameProfile().name(), g.score(),
							g.godzillaHealth(), g.godzillaMaxHealth(), Gz.city().stamped() ? "stamped" : "not stamped", Gz.city().data().buildings().size())), false);
					return 1;
				}))
				// dev tools: /godzilla smash <pos> <radius>, /godzilla explode <pos> <radius>
				.then(Commands.literal("smash")
						.then(Commands.argument("pos", Vec3Argument.vec3())
								.then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 64))
										.executes(c -> {
											int n = Gz.destruction().smashSphere(c.getSource().getLevel(), Vec3Argument.getVec3(c, "pos"),
													DoubleArgumentType.getDouble(c, "radius"), new Vec3(0.6, 0.4, 0), Cause.HAND);
											c.getSource().sendSuccess(() -> Component.literal("smashed " + n + " blocks"), false);
											return n;
										}))))
				.then(Commands.literal("explode")
						.then(Commands.argument("pos", Vec3Argument.vec3())
								.then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.5, 64))
										.executes(c -> {
											int n = Gz.destruction().explode(c.getSource().getLevel(), Vec3Argument.getVec3(c, "pos"),
													DoubleArgumentType.getDouble(c, "radius"), Cause.EXPLOSION);
											c.getSource().sendSuccess(() -> Component.literal("exploded " + n + " blocks"), false);
											return n;
										}))));
		for (Sub sub : SUBS) {
			sub.add(root, context);
		}
		dispatcher.register(root);
	}

	private static int start(CommandSourceStack source, Collection<ServerPlayer> targets) {
		ServerPlayer p = targets.iterator().next();
		Gz.game().start(p);
		source.sendSuccess(() -> Component.literal("run started for " + p.getGameProfile().name()), false);
		return 1;
	}

	private static Collection<ServerPlayer> self(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return List.of(source.getPlayerOrException());
	}

	private static int scale(CommandSourceStack source, Collection<ServerPlayer> targets, double value) {
		for (ServerPlayer p : targets) {
			AttributeInstance scale = p.getAttribute(Attributes.SCALE);
			// setBaseValue does NOT clamp the stored base value (getValue() does), so clamp here to the attribute's range
			double clamped = Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
			scale.setBaseValue(clamped);
			String note = clamped != value ? " (requested %s; the scale attribute range is %s..%s)".formatted(value, SCALE_MIN, SCALE_MAX) : "";
			source.sendSuccess(() -> Component.literal("%s scale = %s (effective %.4f), expected height %.2f, eye height %.2f%s".formatted(
					p.getGameProfile().name(), clamped, p.getScale(), 1.8 * p.getScale(), 1.62 * p.getScale(), note)), false);
		}
		return targets.size();
	}

	private static int pose(CommandSourceStack source, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			PoseSnapshot snap = Vr.pose(p);
			source.sendSuccess(() -> Component.literal(p.getGameProfile().name() + ": " + snap.describe()), false);
		}
		return targets.size();
	}

	private static int fill(CommandSourceStack source, String mode, BlockPos from, BlockPos to, BlockState state) {
		long t0 = System.nanoTime();
		long n;
		switch (mode) {
			case "setblock" -> n = FastBlocks.fillBoxSetBlock(source.getLevel(), from, to, state, FastBlocks.FLAGS_CLIENTS_ONLY);
			case "setblock_all" -> n = FastBlocks.fillBoxSetBlock(source.getLevel(), from, to, state, Block.UPDATE_ALL);
			case "sections" -> n = FastBlocks.fillBoxSections(source.getLevel(), from, to, state, false);
			case "sections_relight" -> n = FastBlocks.fillBoxSections(source.getLevel(), from, to, state, true);
			default -> {
				source.sendFailure(Component.literal("mode: setblock | setblock_all | sections | sections_relight"));
				return 0;
			}
		}
		double ms = (System.nanoTime() - t0) / 1e6;
		source.sendSuccess(() -> Component.literal("%s: %d blocks in %.1f ms (%.0f ns/block)".formatted(mode, n, ms, ms * 1e6 / Math.max(1, n))), false);
		return (int) Math.min(n, Integer.MAX_VALUE);
	}

	private static int ping(CommandSourceStack source, Collection<ServerPlayer> targets) {
		for (ServerPlayer p : targets) {
			long id = GodzillaNetworking.sendPing(p);
			source.sendSuccess(() -> Component.literal("ping #" + id + " sent to " + p.getGameProfile().name() + "; see the logs"), false);
		}
		return targets.size();
	}
}
