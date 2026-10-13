package dev.godzilla.mothra;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaCommands;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Mothra: entity types, the phase hooks that bring her in and clear her away, and the test commands. */
public final class MothraModule {
	public static final ResourceKey<EntityType<?>> MOTHRA_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id("mothra"));
	public static final EntityType<Mothra> MOTHRA = Registry.register(BuiltInRegistries.ENTITY_TYPE, MOTHRA_KEY,
			EntityType.Builder.<Mothra>of(Mothra::new, MobCategory.MISC).sized(20f, (float) Mothra.BODY_HEIGHT)
					.clientTrackingRange(40).updateInterval(1).fireImmune().noSummon().build(MOTHRA_KEY));
	public static final ResourceKey<EntityType<?>> CLOUD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GodzillaMod.id("scale_cloud"));
	public static final EntityType<ScaleCloud> CLOUD = Registry.register(BuiltInRegistries.ENTITY_TYPE, CLOUD_KEY,
			EntityType.Builder.<ScaleCloud>of(ScaleCloud::new, MobCategory.MISC).sized(2f * ScaleCloud.RADIUS, 1.2f * ScaleCloud.RADIUS)
					.clientTrackingRange(32).updateInterval(2).fireImmune().noSummon().build(CLOUD_KEY));

	private static @Nullable Mothra current;

	private MothraModule() {
	}

	/** {hp, max} of the living Mothra, or null when there is none (the HUD shows her bar only then). */
	public static float @Nullable [] bossHealth() {
		Mothra m = current;
		if (m == null || m.isRemoved()) {
			return null;
		}
		return new float[]{Math.max(0, m.hp()), Mothra.MAX_HP};
	}

	static void removed(Mothra m) {
		if (current == m) {
			current = null;
		}
	}

	public static void init() {
		GzEvents.PHASE_CHANGED.register(e -> {
			ServerLevel level = Gz.game().level();
			if (level == null) {
				return;
			}
			if (e.to() == Game.Phase.MOTHRA && (current == null || current.isRemoved())) {
				spawn(level, true);
			} else if (e.to() == Game.Phase.RISE || e.to() == Game.Phase.RAMPAGE || e.to() == Game.Phase.IDLE) {
				clear(level);
			}
		});
		GzEvents.RESET.register(g -> {
			ServerLevel level = Gz.game().level();
			if (level != null) {
				clear(level);
			}
		});
		GodzillaCommands.addSubcommands(MothraModule::commands);
	}

	public static Mothra spawn(ServerLevel level, boolean arrival) {
		clear(level);
		Mothra m = new Mothra(MOTHRA, level);
		var data = Gz.city().data();
		m.setPos(data.mothraFrom().x, data.mothraFrom().y, data.mothraFrom().z);
		level.addFreshEntity(m);
		current = m;
		if (arrival) {
			m.beginArrival(data.mothraFrom(), data.mothraTo());
		} else {
			m.beginCircling();
		}
		return m;
	}

	public static void clear(ServerLevel level) {
		for (Entity e : level.getAllEntities()) {
			if (e instanceof Mothra || e instanceof ScaleCloud) {
				e.discard();
			}
		}
		current = null;
	}

	private static void commands(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext ctx) {
		root.then(Commands.literal("mothra")
				.then(Commands.literal("spawn")
						.executes(c -> spawnCmd(c.getSource(), true))
						.then(Commands.literal("now").executes(c -> spawnCmd(c.getSource(), false))))
				.then(Commands.literal("kill").executes(c -> {
					clear(c.getSource().getLevel());
					c.getSource().sendSuccess(() -> Component.literal("Mothra removed"), false);
					return 1;
				}))
				.then(Commands.literal("attack")
						.then(Commands.argument("kind", StringArgumentType.word()).executes(c -> {
							if (current == null) {
								c.getSource().sendFailure(Component.literal("no Mothra"));
								return 0;
							}
							current.forceAttack(StringArgumentType.getString(c, "kind"));
							return 1;
						})))
				.then(Commands.literal("hp")
						.then(Commands.argument("hp", DoubleArgumentType.doubleArg(0, Mothra.MAX_HP)).executes(c -> {
							if (current == null) {
								c.getSource().sendFailure(Component.literal("no Mothra"));
								return 0;
							}
							current.setHp((float) DoubleArgumentType.getDouble(c, "hp"));
							return 1;
						})))
				.then(Commands.literal("hit")
						.then(Commands.argument("cause", StringArgumentType.word()).executes(c -> {
							if (current == null) {
								c.getSource().sendFailure(Component.literal("no Mothra"));
								return 0;
							}
							Cause cause = Cause.valueOf(StringArgumentType.getString(c, "cause").toUpperCase(java.util.Locale.ROOT));
							Mothra.debugNoRange = true;
							try {
								current.onSmashed(cause, new Vec3(0.6, 0.2, 0), 1.5f);
							} finally {
								Mothra.debugNoRange = false;
							}
							return 1;
						})))
				.then(Commands.literal("status").executes(c -> {
					Mothra m = current;
					c.getSource().sendSuccess(() -> Component.literal(m == null ? "no Mothra"
							: "Mothra %s hp %.0f/%.0f at %s".formatted(m.mode(), m.hp(), Mothra.MAX_HP, m.center())), false);
					return 1;
				})));
	}

	private static int spawnCmd(CommandSourceStack src, boolean arrival) {
		Mothra m = spawn(src.getLevel(), arrival);
		src.sendSuccess(() -> Component.literal("Mothra at " + m.center()), false);
		return 1;
	}
}
