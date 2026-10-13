package dev.godzilla.player;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaCommands;
import dev.godzilla.player.net.PlayerNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.function.Function;

/** Common (server and client) initialisation of this module: payloads, the server brain, rules, commands. */
public final class PlayerModule {
	private PlayerModule() {
	}

	public static void init() {
		PlayerNet.register();
		GodzillaServer.init();
		Gz.setFx(new PlayerFx());

		// Godzilla takes no damage of any kind, and cannot hit, use or place anything the vanilla way
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !GodzillaState.isGodzilla(entity));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> GodzillaState.isGodzilla(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> GodzillaState.isGodzilla(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> GodzillaState.isGodzilla(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> GodzillaState.isGodzilla(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> GodzillaState.isGodzilla(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		GodzillaCommands.addSubcommands((root, context) -> addCommands(root));
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> c, boolean explicit) throws CommandSyntaxException {
		if (explicit) {
			return EntityArgument.getPlayer(c, "target");
		}
		ServerPlayer self = c.getSource().getPlayer();
		if (self != null) {
			return self;
		}
		var players = c.getSource().getServer().getPlayerList().getPlayers();
		if (players.isEmpty()) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("no player online")).create();
		}
		return players.get(0);
	}

	private interface Action {
		int run(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException;
	}

	/** Adds literal(name) [args...] [target] with the action; every command accepts an optional trailing player. */
	private static void add(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> node, Action action) {
		node.executes(c -> action.run(c, target(c, false)));
		node.then(Commands.argument("target", EntityArgument.player()).executes(c -> action.run(c, target(c, true))));
		root.then(node);
	}

	private static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		add(root, Commands.literal("become"), (c, p) -> {
			GodzillaServer.force(p, true);
			c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + " is Godzilla (forced until /godzilla unbecome)"), false);
			return 1;
		});
		add(root, Commands.literal("unbecome"), (c, p) -> {
			GodzillaServer.force(p, false);
			c.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + " released"), false);
			return 1;
		});
		for (String side : new String[]{"left", "right"}) {
			LiteralArgumentBuilder<CommandSourceStack> sw = Commands.literal("swipe_" + side);
			add(root, sw, (c, p) -> withRuntime(c, p, rt -> GodzillaServer.swipe(rt, side.equals("left"))));
		}
		root.then(Commands.literal("swipe")
				.then(Commands.argument("side", StringArgumentType.word()).executes(c -> {
					ServerPlayer p = target(c, false);
					String side = StringArgumentType.getString(c, "side");
					return withRuntime(c, p, rt -> GodzillaServer.swipe(rt, side.startsWith("l")));
				})));
		root.then(Commands.literal("breath")
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000)).executes(c -> {
					ServerPlayer p = target(c, false);
					int ticks = IntegerArgumentType.getInteger(c, "ticks");
					return withRuntime(c, p, rt -> GodzillaServer.forceBreath(rt, ticks));
				})));
		add(root, Commands.literal("roar"), (c, p) -> {
			GodzillaServer.Runtime rt = GodzillaServer.runtime(p);
			if (rt == null) {
				c.getSource().sendFailure(Component.literal("not Godzilla; /godzilla become first"));
				return 0;
			}
			boolean ok = GodzillaServer.roar(p, rt);
			c.getSource().sendSuccess(() -> Component.literal(ok ? "roar" : "roar on cooldown"), false);
			return ok ? 1 : 0;
		});
		root.then(Commands.literal("camera")
				.then(Commands.argument("mode", StringArgumentType.word()).executes(c -> {
					ServerPlayer p = target(c, false);
					String m = StringArgumentType.getString(c, "mode");
					int v = m.startsWith("f") && !m.startsWith("fr") ? 0 : m.startsWith("fr") ? 2 : m.startsWith("t") || m.startsWith("b") ? 1 : 0;
					ServerPlayNetworking.send(p, new PlayerNet.DebugS2C(0, v));
					return 1;
				})));
		root.then(Commands.literal("walk")
				.then(Commands.argument("ticks", IntegerArgumentType.integer(-2000, 2000)).executes(c -> {
					ServerPlayer p = target(c, false);
					int t = IntegerArgumentType.getInteger(c, "ticks");
					ServerPlayNetworking.send(p, new PlayerNet.DebugS2C(t >= 0 ? 1 : 2, Math.abs(t)));
					return 1;
				})));
		root.then(Commands.literal("turn")
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000)).executes(c -> {
					ServerPlayer p = target(c, false);
					ServerPlayNetworking.send(p, new PlayerNet.DebugS2C(3, IntegerArgumentType.getInteger(c, "ticks")));
					return 1;
				})));
		root.then(Commands.literal("gauge").executes(c -> {
			ServerPlayer p = target(c, false);
			GodzillaServer.Runtime rt = GodzillaServer.runtime(p);
			c.getSource().sendSuccess(() -> Component.literal(rt == null ? "not Godzilla"
					: "gauge %.2f breath %s".formatted(rt.gauge(), rt.breath())), false);
			return 1;
		}));
		root.then(Commands.literal("speedfactor")
				.then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.1, 20)).executes(c -> {
					GodzillaServer.speedFactor = DoubleArgumentType.getDouble(c, "factor");
					c.getSource().sendSuccess(() -> Component.literal("speed factor " + GodzillaServer.speedFactor + " (applies on next become)"), false);
					return 1;
				})));
	}

	private static int withRuntime(CommandContext<CommandSourceStack> c, ServerPlayer p, java.util.function.Consumer<GodzillaServer.Runtime> f) {
		GodzillaServer.Runtime rt = GodzillaServer.runtime(p);
		if (rt == null) {
			c.getSource().sendFailure(Component.literal("not Godzilla; /godzilla become first"));
			return 0;
		}
		f.accept(rt);
		return 1;
	}
}
