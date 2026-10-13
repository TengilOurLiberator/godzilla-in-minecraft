package dev.godzilla.population;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Common (server and client) initialisation of this module: entity types, the simulation, the commands
 * <pre>
 * /godzilla pop stats
 * /godzilla pop spawn people &lt;count&gt; [radius]        civilians on the pavements nearest to the player (default radius 90)
 * /godzilla pop spawn car|taxi|police|bus|truck|parked &lt;count&gt;
 * /godzilla pop spawn train [line]
 * /godzilla pop panic [radius]                        a threat at the player (or Godzilla): everybody within the radius runs
 * /godzilla pop calm | clear
 * /godzilla pop budget &lt;people&gt; &lt;cars&gt;
 * /godzilla pop alarm                                 car alarms near the player
 * </pre>
 */
public final class PopulationModule {
	private PopulationModule() {
	}

	public static void init() {
		PopulationEntities.register();
		Population.init();
		GodzillaCommands.addSubcommands((root, context) -> root.then(popCommands()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> popCommands() {
		return Commands.literal("pop")
				.then(Commands.literal("stats").executes(c -> {
					String s = Population.I.stats(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal(s), false);
					Population.log(s);
					return 1;
				}))
				.then(Commands.literal("spawn")
						.then(Commands.literal("people")
								.then(count().executes(c -> spawnPeople(c.getSource(), IntegerArgumentType.getInteger(c, "count"), 90))
										.then(Commands.argument("radius", DoubleArgumentType.doubleArg(5, 400))
												.executes(c -> spawnPeople(c.getSource(), IntegerArgumentType.getInteger(c, "count"),
														DoubleArgumentType.getDouble(c, "radius"))))))
						.then(vehicleKind("car")).then(vehicleKind("taxi")).then(vehicleKind("police")).then(vehicleKind("bus"))
						.then(vehicleKind("truck")).then(vehicleKind("parked"))
						.then(Commands.literal("train")
								.executes(c -> spawnTrain(c.getSource(), 0))
								.then(Commands.argument("line", IntegerArgumentType.integer(0, 7))
										.executes(c -> spawnTrain(c.getSource(), IntegerArgumentType.getInteger(c, "line"))))))
				.then(Commands.literal("panic")
						.executes(c -> panic(c.getSource(), 200))
						.then(Commands.argument("radius", DoubleArgumentType.doubleArg(5, 600))
								.executes(c -> panic(c.getSource(), DoubleArgumentType.getDouble(c, "radius")))))
				.then(Commands.literal("calm").executes(c -> {
					Population.I.calm();
					c.getSource().sendSuccess(() -> Component.literal("threats cleared"), false);
					return 1;
				}))
				.then(Commands.literal("clear").executes(c -> {
					Population.I.clearAll();
					c.getSource().sendSuccess(() -> Component.literal("all population entities removed"), false);
					return 1;
				}))
				.then(Commands.literal("budget")
						.then(Commands.argument("people", IntegerArgumentType.integer(0, 3000))
								.then(Commands.argument("cars", IntegerArgumentType.integer(0, 500)).executes(c -> {
									Population.I.targetPeople = IntegerArgumentType.getInteger(c, "people");
									Population.I.maxPeople = Population.I.targetPeople + 200;
									Population.I.targetCars = IntegerArgumentType.getInteger(c, "cars");
									c.getSource().sendSuccess(() -> Component.literal("budget: people %d (cap %d), cars %d".formatted(
											Population.I.targetPeople, Population.I.maxPeople, Population.I.targetCars)), false);
									return 1;
								}))))
				.then(Commands.literal("alarm").executes(c -> {
					ServerPlayer p = player(c.getSource());
					if (p == null) {
						return 0;
					}
					Population.I.alarmNear(p.position(), 120);
					c.getSource().sendSuccess(() -> Component.literal("car alarms near " + p.getGameProfile().name()), false);
					return 1;
				}));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, Integer> count() {
		return Commands.argument("count", IntegerArgumentType.integer(1, 2000));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> vehicleKind(String kind) {
		return Commands.literal(kind).then(count().executes(c -> spawnVehicles(c.getSource(), kind, IntegerArgumentType.getInteger(c, "count"))));
	}

	/** The level and the player that commands work around: the executing player, else Godzilla, else the first player. */
	private static ServerPlayer player(CommandSourceStack source) {
		ServerPlayer p = source.getPlayer();
		if (p == null) {
			p = Gz.game().godzilla();
		}
		if (p == null && !source.getServer().getPlayerList().getPlayers().isEmpty()) {
			p = source.getServer().getPlayerList().getPlayers().get(0);
		}
		if (p == null) {
			source.sendFailure(Component.literal("no player online"));
		}
		return p;
	}

	private static int spawnPeople(CommandSourceStack source, int count, double radius) {
		ServerPlayer p = player(source);
		if (p == null) {
			return 0;
		}
		Population pop = Population.I;
		ServerLevel level = p.level();
		prepare(pop, level, p);
		List<BlockPos> spawns = new ArrayList<>(Gz.city().data().sidewalkSpawns());
		Vec3 a = p.position();
		spawns.removeIf(b -> Math.hypot(b.getX() + 0.5 - a.x, b.getZ() + 0.5 - a.z) > radius);
		if (spawns.isEmpty()) {
			source.sendFailure(Component.literal("no pavement within %.0f blocks of %s".formatted(radius, p.getGameProfile().name())));
			return 0;
		}
		spawns.sort(Comparator.comparingDouble(b -> Math.hypot(b.getX() + 0.5 - a.x, b.getZ() + 0.5 - a.z)));
		int made = 0;
		for (int i = 0; i < count; i++) {
			BlockPos b = spawns.get(i % spawns.size());
			double x = b.getX() + 0.5 + (pop.random().nextDouble() - 0.5) * 1.6, z = b.getZ() + 0.5 + (pop.random().nextDouble() - 0.5) * 1.6;
			if (Terrain.stand(level, x, z, b.getY()) == b.getY() && pop.spawnPerson(level, x, b.getY(), z) != null) {
				made++;
			}
		}
		int m = made;
		source.sendSuccess(() -> Component.literal("spawned %d people (%d pavement points in range)".formatted(m, spawns.size())), false);
		return made;
	}

	private static int spawnVehicles(CommandSourceStack source, String kind, int count) {
		ServerPlayer p = player(source);
		if (p == null) {
			return 0;
		}
		Population pop = Population.I;
		ServerLevel level = p.level();
		prepare(pop, level, p);
		boolean parked = kind.equals("parked");
		int made = 0;
		for (int i = 0; i < count * 6 && made < count; i++) {
			if (pop.spawnVehicleAtRandomSpot(level, parked ? "car" : kind, parked, 0, 140)) {
				made++;
			}
		}
		int m = made;
		source.sendSuccess(() -> Component.literal("spawned %d %s".formatted(m, kind)), false);
		return made;
	}

	private static int spawnTrain(CommandSourceStack source, int line) {
		ServerPlayer p = player(source);
		if (p == null) {
			return 0;
		}
		Population pop = Population.I;
		ServerLevel level = p.level();
		prepare(pop, level, p);
		Train t = pop.spawnTrain(level, line, 0.5);
		if (t == null) {
			source.sendFailure(Component.literal("no rail line " + line));
			return 0;
		}
		t.line = line;
		source.sendSuccess(() -> Component.literal("train spawned on line " + line), false);
		return 1;
	}

	private static int panic(CommandSourceStack source, double radius) {
		ServerPlayer p = player(source);
		if (p == null) {
			return 0;
		}
		Population.I.panicAll(p.getX(), p.getZ(), radius, 400);
		source.sendSuccess(() -> Component.literal("panic: threat at %s radius %.0f".formatted(p.getGameProfile().name(), radius)), false);
		return 1;
	}

	/** Commands work in any phase: set up what the tick normally prepares. */
	private static void prepare(Population pop, ServerLevel level, ServerPlayer p) {
		Terrain.ensure();
		pop.prepare(level, p);
	}
}
