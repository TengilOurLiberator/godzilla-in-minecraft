package dev.godzilla.military;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.godzilla.api.Cause;
import dev.godzilla.api.Gz;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * /godzilla mil spawn &lt;tank|heli|jet|ship|maser&gt; [count]   spawn units now (jet = a pair); needs a Godzilla (dev phase RAMPAGE)
 * /godzilla mil wave &lt;n&gt;                                     jump the director clock to n x 30 s and fill up to the caps
 * /godzilla mil stats                                         unit counts, shots, tick time of this module, server tick time
 * /godzilla mil smash &lt;type&gt; &lt;cause&gt;                         hit every unit of a type as if Godzilla had (HAND, FOOT, BEAM ...)
 * /godzilla mil clear                                         remove all units and projectiles
 */
final class MilCommands {
	private static final List<String> TYPES = List.of("tank", "heli", "jet", "ship", "maser");

	private MilCommands() {
	}

	static void add(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal("mil")
				.then(Commands.literal("spawn")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
								.executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "type"), 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 60))
										.executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "count"))))))
				.then(Commands.literal("wave")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 20))
								.executes(c -> wave(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
				.then(Commands.literal("stats").executes(c -> stats(c.getSource())))
				.then(Commands.literal("cam")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
								.executes(c -> cam(c.getSource(), StringArgumentType.getString(c, "type"), 30))
								.then(Commands.argument("dist", IntegerArgumentType.integer(3, 300))
										.executes(c -> cam(c.getSource(), StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "dist"))))))
				.then(Commands.literal("focus")
						.then(Commands.literal("off").executes(c -> {
							Mil.focus = null;
							c.getSource().sendSuccess(() -> Component.literal("focus off: units shoot at the player again"), false);
							return 1;
						}))
						.then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
							Mil.focus = Vec3Argument.getVec3(c, "pos");
							c.getSource().sendSuccess(() -> Component.literal("test focus: units shoot at a Godzilla standing at " + Mil.focus), false);
							return 1;
						})))
				.then(Commands.literal("clear").executes(c -> {
					ServerLevel level = c.getSource().getLevel();
					Director.clear(level);
					c.getSource().sendSuccess(() -> Component.literal("military cleared"), false);
					return 1;
				}))
				.then(Commands.literal("smash")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
								.then(Commands.argument("cause", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(
												java.util.Arrays.stream(Cause.values()).map(Enum::name).toList(), b))
										.executes(c -> smash(c.getSource(), StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "cause")))))));
	}

	private static int spawn(CommandSourceStack src, String type, int n) {
		ServerPlayer g = Gz.game().godzilla();
		ServerLevel level = Gz.game().level();
		if (g == null || level == null || !TYPES.contains(type)) {
			src.sendFailure(Component.literal(g == null ? "no Godzilla: /godzilla dev phase RAMPAGE <player> first" : "type: " + TYPES));
			return 0;
		}
		int made = Director.spawn(type, level, g, n);
		src.sendSuccess(() -> Component.literal("spawned " + made + " " + type + " (phase " + Gz.game().phase() + ")"), false);
		return made;
	}

	private static int wave(CommandSourceStack src, int n) {
		ServerPlayer g = Gz.game().godzilla();
		ServerLevel level = Gz.game().level();
		if (g == null || level == null) {
			src.sendFailure(Component.literal("no Godzilla: /godzilla dev phase RAMPAGE <player> first"));
			return 0;
		}
		Director.waveFloor = n * 600;
		Director.fill(level, g, true);
		src.sendSuccess(() -> Component.literal("wave " + n + " (clock " + n * 30 + " s): " + summary()), false);
		return 1;
	}

	private static String summary() {
		int charging = 0, beaming = 0;
		for (MilitaryUnit u : MilitaryUnit.ALL) {
			if (u instanceof MaserCannon) {
				charging += u.hasFlag(MilitaryUnit.F_CHARGE) ? 1 : 0;
				beaming += u.hasFlag(MilitaryUnit.F_BEAM) ? 1 : 0;
			}
		}
		return "tanks %d, helis %d, jets %d, ships %d, masers %d (%d charging, %d beaming)".formatted(Director.count(Tank.class), Director.count(Helicopter.class),
				Director.count(Jet.class), Director.count(Warship.class), Director.count(MaserCannon.class), charging, beaming);
	}

	private static int stats(CommandSourceStack src) {
		ServerLevel level = Gz.game().level();
		int air = level == null ? 0 : Director.shotsInAir(level);
		double mspt = src.getServer().getAverageTickTimeNanos() / 1e6;
		src.sendSuccess(() -> Component.literal(("%s | %d shots in the air | module %.3f ms/tick avg, %.3f max (units + director) | server %.2f ms/tick | "
				+ "fired %d, hit Godzilla %d, hit the city %d, units destroyed %d").formatted(summary(), air, Mil.avgTickMs(), Mil.maxTickMs(), mspt,
				Mil.shotsFired, Mil.hitsOnBody, Mil.missesOnCity, Mil.kills)), false);
		return 1;
	}

	/** Test aid: moves the calling player (a spectator) to a viewpoint {@code dist} blocks from a random unit of a type, facing it. */
	private static int cam(CommandSourceStack src, String type, int dist) {
		Class<? extends MilitaryUnit> cls = switch (type) {
			case "tank" -> Tank.class;
			case "heli" -> Helicopter.class;
			case "jet" -> Jet.class;
			case "ship" -> Warship.class;
			case "maser" -> MaserCannon.class;
			default -> null;
		};
		ServerPlayer p = Gz.game().godzilla();
		if (cls == null || p == null) {
			src.sendFailure(Component.literal("type: " + TYPES + " (and a Godzilla player)"));
			return 0;
		}
		MilitaryUnit best = null;
		for (MilitaryUnit u : MilitaryUnit.ALL) {
			if (cls.isInstance(u) && (best == null || u.getId() % 7 == 0)) {
				best = u;
			}
		}
		if (best == null) {
			src.sendFailure(Component.literal("no " + type));
			return 0;
		}
		Vec3 look = best.position().add(0, best.getBbHeight() * 0.5, 0);
		Vec3 axis = new Vec3(0.6, 0, 0.8);
		if (best instanceof MaserCannon m && m.hasFlag(MilitaryUnit.F_BEAM)) {
			Vec3 mid = m.muzzle().add(m.beamEnd()).scale(0.5);
			Vec3 line = m.beamEnd().subtract(m.muzzle());
			axis = new Vec3(-line.z, 0, line.x).normalize();
			look = mid;
		}
		Vec3 at = look.add(axis.scale(dist)).add(0, Math.max(3, dist * 0.8), 0);
		String cmd = String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f facing %.2f %.2f %.2f", p.getGameProfile().name(), at.x, at.y, at.z, look.x, look.y, look.z);
		src.getServer().getCommands().performPrefixedCommand(src.getServer().createCommandSourceStack(), cmd);
		src.sendSuccess(() -> Component.literal("camera " + dist + " blocks from a " + type), false);
		return 1;
	}

	private static int smash(CommandSourceStack src, String type, String cause) {
		Cause c;
		try {
			c = Cause.valueOf(cause.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			src.sendFailure(Component.literal("cause: " + java.util.Arrays.toString(Cause.values())));
			return 0;
		}
		Class<? extends MilitaryUnit> cls = switch (type) {
			case "tank" -> Tank.class;
			case "heli" -> Helicopter.class;
			case "jet" -> Jet.class;
			case "ship" -> Warship.class;
			case "maser" -> MaserCannon.class;
			default -> null;
		};
		if (cls == null) {
			src.sendFailure(Component.literal("type: " + TYPES));
			return 0;
		}
		int n = 0;
		for (MilitaryUnit u : List.copyOf(MilitaryUnit.ALL)) {
			if (cls.isInstance(u)) {
				u.onSmashed(c, new Vec3(0.5, 0.3, 0.2), 1.0F);
				n++;
			}
		}
		int hit = n;
		src.sendSuccess(() -> Component.literal("smashed " + hit + " " + type + " with " + c), false);
		return n;
	}
}
