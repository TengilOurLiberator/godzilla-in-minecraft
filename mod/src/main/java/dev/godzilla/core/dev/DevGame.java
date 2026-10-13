package dev.godzilla.core.dev;

import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Stand-in game: no timeline. The phase is set by hand with /godzilla dev phase &lt;PHASE&gt; [player], the score is only counted
 * and logged, Godzilla cannot be defeated. Replaced by dev.godzilla.game.
 */
public final class DevGame implements Game {
	private static @Nullable MinecraftServer server;
	private Phase phase = Phase.IDLE;
	private int phaseStart;
	private int runStart = -1;
	private @Nullable UUID godzilla;
	private long score;
	private float health = 100;

	public static void serverStarted(MinecraftServer s) {
		server = s;
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	/** Dev command: jump straight to a phase with this player as Godzilla. */
	public void force(Phase to, @Nullable ServerPlayer player) {
		if (player != null) {
			godzilla = player.getUUID();
		}
		Phase from = phase;
		phase = to;
		phaseStart = now();
		if (to == Phase.RAMPAGE && runStart < 0) {
			runStart = now();
		}
		if (to == Phase.IDLE) {
			runStart = -1;
		}
		GzEvents.PHASE_CHANGED.fire(new GzEvents.PhaseChange(from, to));
	}

	private static int now() {
		return server == null ? 0 : server.getTickCount();
	}

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public int phaseTicks() {
		return now() - phaseStart;
	}

	@Override
	public int runTicks() {
		return runStart < 0 ? 0 : now() - runStart;
	}

	@Override
	public @Nullable ServerPlayer godzilla() {
		return server == null || godzilla == null ? null : server.getPlayerList().getPlayer(godzilla);
	}

	@Override
	public @Nullable ServerLevel level() {
		return server == null ? null : server.overworld();
	}

	@Override
	public void addScore(ScoreKind kind, int points, Vec3 where) {
		score += points;
		if (kind != ScoreKind.BLOCKS) {
			GodzillaMod.LOGGER.info("godzilla dev score: +{} {} (total {})", points, kind, score);
		}
	}

	@Override
	public long score() {
		return score;
	}

	@Override
	public float godzillaHealth() {
		return health;
	}

	@Override
	public float godzillaMaxHealth() {
		return 100;
	}

	@Override
	public void hurtGodzilla(float amount, Vec3 from) {
		health = Math.max(1, health - amount);
		GodzillaMod.LOGGER.info("godzilla dev: Godzilla hurt {} (health {})", amount, health);
	}

	@Override
	public void mothraDefeated() {
		GodzillaMod.LOGGER.info("godzilla dev: Mothra defeated");
		force(Phase.VICTORY, null);
	}

	@Override
	public void start(ServerPlayer player) {
		GzEvents.RESET.fire(this);
		score = 0;
		health = 100;
		runStart = -1;
		ServerLevel level = level();
		if (level != null) {
			Gz.city().stamp(level);
		}
		force(Phase.RAMPAGE, player);
	}

	@Override
	public void stop() {
		GzEvents.RESET.fire(this);
		force(Phase.IDLE, null);
	}
}
