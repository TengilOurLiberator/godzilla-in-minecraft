package dev.godzilla.game;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaCommands;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.game.net.GameNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The director: owns the timeline, the score, the HUD data and the results. See {@link GameImpl}. */
public final class GameModule {
	public static final float SIGN_WIDTH = 13f;
	public static final float SIGN_HEIGHT = 7f;
	public static final ResourceKey<EntityType<?>> SIGN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id("smash_sign"));
	public static final EntityType<SmashSign> SIGN = Registry.register(BuiltInRegistries.ENTITY_TYPE, SIGN_KEY,
			EntityType.Builder.<SmashSign>of(SmashSign::new, MobCategory.MISC).sized(SIGN_WIDTH, SIGN_HEIGHT)
					.clientTrackingRange(16).updateInterval(20).noSave().noSummon().build(SIGN_KEY));

	private static GameImpl game;

	private GameModule() {
	}

	public static GameImpl game() {
		return game;
	}

	public static void init() {
		game = new GameImpl();
		Gz.setGame(game);
		GameNet.registerCommon();
		ServerLifecycleEvents.SERVER_STARTED.register(game::serverStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> game.serverStopping());
		ServerTickEvents.END_SERVER_TICK.register(s -> game.tick());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> game.playerJoined(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> game.playerLeft(handler.getPlayer()));
		GzEvents.ROAR.register(game::onRoar);
		GzEvents.BUILDING_DESTROYED.register(game::onBuildingDestroyed);
		GodzillaCommands.addSubcommands(GameModule::commands);
	}

	private static void commands(LiteralArgumentBuilder<CommandSourceStack> root, net.minecraft.commands.CommandBuildContext ctx) {
		root.then(Commands.literal("phase")
				.then(Commands.argument("phase", StringArgumentType.word())
						.executes(c -> phase(c.getSource(), StringArgumentType.getString(c, "phase"), null))
						.then(Commands.argument("targets", EntityArgument.player())
								.executes(c -> phase(c.getSource(), StringArgumentType.getString(c, "phase"), EntityArgument.getPlayer(c, "targets"))))));
		root.then(Commands.literal("skip")
				.executes(c -> skip(c.getSource(), null))
				.then(Commands.argument("targets", EntityArgument.player()).executes(c -> skip(c.getSource(), EntityArgument.getPlayer(c, "targets")))));
		root.then(Commands.literal("score")
				.then(Commands.argument("kind", StringArgumentType.word())
						.then(Commands.argument("points", IntegerArgumentType.integer(1))
								.executes(c -> score(c.getSource(), StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "points"), c.getSource().getPosition()))
								.then(Commands.argument("pos", Vec3Argument.vec3())
										.executes(c -> score(c.getSource(), StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "points"), Vec3Argument.getVec3(c, "pos")))))));
		root.then(Commands.literal("hurt")
				.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1000)).executes(c -> {
					Gz.game().hurtGodzilla((float) DoubleArgumentType.getDouble(c, "amount"), c.getSource().getPosition());
					return 1;
				})));
		root.then(Commands.literal("game").executes(c -> {
			c.getSource().sendSuccess(() -> Component.literal(game.describe()), false);
			return 1;
		}));
		root.then(Commands.literal("sign")
				.executes(c -> sign(c.getSource(), false))
				.then(Commands.literal("restart").executes(c -> sign(c.getSource(), true))));
		root.then(Commands.literal("bonus")
				.then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
					game.bonus("LANDMARK DOWN!", "Tokyo Tower", 1500, Vec3Argument.getVec3(c, "pos"), 0xFFD23C, 22);
					return 1;
				})));
		root.then(Commands.literal("results").executes(c -> {
			game.skipToResults();
			return 1;
		}));
	}

	private static int phase(CommandSourceStack src, String name, ServerPlayer target) {
		Game.Phase phase;
		try {
			phase = Game.Phase.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			src.sendFailure(Component.literal("phase: IDLE | RISE | RAMPAGE | MOTHRA | VICTORY | DEFEAT"));
			return 0;
		}
		ServerPlayer p = target != null ? target : src.getPlayer();
		if (p == null && game.godzilla() == null) {
			src.sendFailure(Component.literal("name the player: /godzilla phase " + name + " <player>"));
			return 0;
		}
		if (phase == Game.Phase.RISE) {
			game.start(p != null ? p : game.godzilla());
		} else {
			game.force(phase, p);
		}
		src.sendSuccess(() -> Component.literal("phase " + game.phase()), false);
		return 1;
	}

	private static int skip(CommandSourceStack src, ServerPlayer target) {
		ServerPlayer p = target != null ? target : src.getPlayer();
		if (p == null && game.godzilla() == null) {
			src.sendFailure(Component.literal("name the player"));
			return 0;
		}
		game.skipToMothra(p);
		src.sendSuccess(() -> Component.literal("run time " + game.runTicks() + " ticks; Mothra arrives at " + Game.MOTHRA_AT_RUN_TICKS), false);
		return 1;
	}

	private static int score(CommandSourceStack src, String kind, int points, Vec3 where) {
		try {
			Gz.game().addScore(Game.ScoreKind.valueOf(kind.toUpperCase(Locale.ROOT)), points, where);
			return 1;
		} catch (IllegalArgumentException e) {
			src.sendFailure(Component.literal("kinds: " + java.util.Arrays.toString(Game.ScoreKind.values())));
			return 0;
		}
	}

	private static int sign(CommandSourceStack src, boolean restart) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		game.spawnSign(src.getPlayerOrException(), restart);
		return 1;
	}
}
