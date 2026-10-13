package dev.godzilla.game;

import dev.godzilla.api.CityData;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.GodzillaSounds;
import dev.godzilla.game.net.GameNet;
import dev.godzilla.mothra.MothraModule;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The director: the run's timeline (RISE, RAMPAGE, MOTHRA, VICTORY or DEFEAT, results), music, score and combos, bonuses,
 * Godzilla's health. Runs from one server tick handler; everything here is on the server thread.
 */
public final class GameImpl implements Game {
	public static final float MAX_HEALTH = 100f;
	/** A combo breaks after this many ticks without destruction. */
	public static final int COMBO_QUIET_TICKS = 50;
	/** Chain value needed for multiplier 1, 2, 3 ... */
	private static final int[] COMBO_LEVELS = {0, 25, 80, 180, 340, 600, 950, 1400};
	/** Rampage music runs this long before the intense version takes over (the last bar of the 96 s loop is the crossfade). */
	private static final int INTENSE_AT = 1872;
	private static final int CROSSFADE = 48;
	private static final int MOTHRA_ARRIVAL_MUSIC_TICKS = 240;

	private static final String[] RANKS = {"Clumsy Lizard", "Neighbourhood Nuisance", "City Stomper", "Skyline Wrecker", "Scourge of Tokyo", "Monarch of Monsters"};
	private static final long[] RANK_SCORES = {0, 10_000, 25_000, 50_000, 90_000, 150_000};

	private @Nullable MinecraftServer server;
	private Phase phase = Phase.IDLE;
	private int phaseTicks;
	private int runTicks;
	private @Nullable UUID godzilla;
	private @Nullable UUID pendingStart;
	private boolean pendingStartSet;
	private int ticks;

	private long score;
	private final long[] byKind = new long[ScoreKind.values().length];
	private final int[] countByKind = new int[ScoreKind.values().length];
	private int buildingsDown;
	private int landmarksDown;
	private float health = MAX_HEALTH;
	private boolean suppress;

	private float chain;
	private int comboLevel = 1;
	private int comboTimer;
	private boolean intense;
	private boolean results;
	private int resultsAt;
	private int loopAt;
	private boolean victory;

	private final ArrayDeque<Integer> killTicks = new ArrayDeque<>();
	private int lastMultiKill;
	private final List<int[]> downedBuildings = new ArrayList<>(); // tick, x, z
	private int lastLevelled = -10000;
	private final Popups popups = new Popups();
	private @Nullable SmashSign sign;

	// ---------------------------------------------------------------- wiring

	public void serverStarted(MinecraftServer s) {
		server = s;
		BestScore.load(s);
		// sweep entities of ours that an earlier crash may have written to the world
		for (net.minecraft.world.entity.Entity e : s.overworld().getAllEntities()) {
			if (e instanceof SmashSign || e instanceof Popups.PopupText) {
				e.discard();
			}
		}
	}

	public void serverStopping() {
		popups.clear();
		server = null;
	}

	public void playerJoined(ServerPlayer p) {
		if (phase == Phase.IDLE) {
			godzilla = p.getUUID();
			schedulePrompt = 30;
		}
	}

	private int schedulePrompt = -1;

	public void playerLeft(ServerPlayer p) {
		if (p.getUUID().equals(godzilla)) {
			if (phase != Phase.IDLE) {
				stop();
			}
			discardSign();
			godzilla = null;
		}
	}

	/** A smash on the sign, an attack click, or a roar after the results. */
	public void requestStart(@Nullable ServerPlayer who) {
		pendingStartSet = true;
		pendingStart = who == null ? null : who.getUUID();
	}

	public void onRoar(ServerPlayer p) {
		if (phase == Phase.IDLE || (results && phaseTicks > resultsAt + 80)) {
			requestStart(p);
		}
	}

	// ---------------------------------------------------------------- Game

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public int phaseTicks() {
		return phaseTicks;
	}

	@Override
	public int runTicks() {
		return runTicks;
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
	public long score() {
		return score;
	}

	@Override
	public float godzillaHealth() {
		return health;
	}

	@Override
	public float godzillaMaxHealth() {
		return MAX_HEALTH;
	}

	public boolean resultsShown() {
		return results;
	}

	public int comboMultiplier() {
		return comboLevel;
	}

	/** While set, addScore is ignored: Mothra's wind and crash are not Godzilla's doing. */
	public void suppressScore(boolean on) {
		suppress = on;
	}

	@Override
	public void addScore(ScoreKind kind, int points, Vec3 where) {
		if (!phase.playing() || points <= 0 || suppress) {
			return;
		}
		long gained = (long) points * comboLevel;
		score += gained;
		byKind[kind.ordinal()] += gained;
		countByKind[kind.ordinal()]++;
		if (kind != ScoreKind.BONUS) {
			feedCombo(kind, points);
		}
		popups.add(where, groupOf(kind), gained, ticks);
		if (isKill(kind)) {
			noteKill(where);
		}
	}

	@Override
	public void hurtGodzilla(float amount, Vec3 from) {
		if (phase != Phase.MOTHRA || amount <= 0) {
			return;
		}
		ServerPlayer p = godzilla();
		health = Math.max(0, health - amount);
		if (p != null) {
			Gz.fx().hitFlash(p, from, Mth.clamp(amount / 18f, 0.2f, 1f));
			sendAll(new GameNet.Cue(GameNet.Cue.FLASH, Mth.clamp(amount / 14f, 0.25f, 1f), "", 0));
			if (health > 0 && amount >= 1.5f) {
				GameAudio.at(p, GodzillaSounds.GODZILLA_HURT, SoundSource.PLAYERS, p.getEyePosition(), 1f, 0.9f + p.getRandom().nextFloat() * 0.2f);
			}
		}
		if (health <= 0) {
			setPhase(Phase.DEFEAT);
		}
	}

	@Override
	public void mothraDefeated() {
		if (phase == Phase.MOTHRA) {
			setPhase(Phase.VICTORY);
		}
	}

	@Override
	public void start(ServerPlayer player) {
		ServerLevel level = level();
		if (server == null || level == null) {
			return;
		}
		discardSign();
		resetRunState();
		godzilla = player.getUUID();
		Phase from = phase;
		phase = Phase.IDLE; // the other modules see RESET in IDLE
		GzEvents.RESET.fire(this);
		popups.clear();
		Gz.city().stamp(level);
		CityData.Start st = Gz.city().data().playerStart();
		player.teleportTo(level, st.pos().x, st.pos().y, st.pos().z, Set.<Relative>of(), st.yaw(), 0f, true);
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0;
		player.setHealth(player.getMaxHealth());
		phase = from;
		setPhase(Phase.RISE);
		GodzillaMod.LOGGER.info("godzilla game: run started for {}", player.getGameProfile().name());
	}

	@Override
	public void stop() {
		ServerPlayer p = godzilla();
		if (p != null) {
			GameAudio.stopMusic(p, "", 10);
		}
		Phase from = phase;
		resetRunState();
		phase = Phase.IDLE;
		phaseTicks = 0;
		GzEvents.RESET.fire(this);
		popups.clear();
		if (from != Phase.IDLE) {
			GzEvents.PHASE_CHANGED.fire(new GzEvents.PhaseChange(from, Phase.IDLE));
		}
		schedulePrompt = 20;
	}

	private void resetRunState() {
		score = 0;
		java.util.Arrays.fill(byKind, 0);
		java.util.Arrays.fill(countByKind, 0);
		buildingsDown = 0;
		landmarksDown = 0;
		health = MAX_HEALTH;
		chain = 0;
		comboLevel = 1;
		comboTimer = 0;
		runTicks = 0;
		intense = false;
		results = false;
		killTicks.clear();
		downedBuildings.clear();
		lastMultiKill = 0;
		lastLevelled = -10000;
		suppress = false;
	}

	// ---------------------------------------------------------------- phases

	/** Test command: jump to a phase with this player as Godzilla; starts a run without restamping if none is on. */
	public void force(Phase to, @Nullable ServerPlayer player) {
		if (player != null) {
			godzilla = player.getUUID();
		}
		ServerPlayer p = godzilla();
		if (p == null) {
			return;
		}
		discardSign();
		if (to == Phase.IDLE) {
			stop();
			return;
		}
		if (phase == Phase.IDLE || phase == Phase.VICTORY || phase == Phase.DEFEAT) {
			resetRunState();
		}
		results = false;
		if (to == Phase.RAMPAGE || to == Phase.MOTHRA) {
			runTicks = to == Phase.MOTHRA ? MOTHRA_AT_RUN_TICKS : Math.max(runTicks, 1);
		}
		setPhase(to);
	}

	/** Test command: jump to just before Mothra (the rampage music is already in its late form). */
	public void skipToMothra(@Nullable ServerPlayer player) {
		if (phase != Phase.RAMPAGE) {
			force(Phase.RAMPAGE, player);
		}
		runTicks = MOTHRA_AT_RUN_TICKS - 100;
	}

	private void setPhase(Phase to) {
		Phase from = phase;
		phase = to;
		phaseTicks = 0;
		results = false;
		ServerPlayer p = godzilla();
		switch (to) {
			case RISE -> {
				runTicks = 0;
				health = MAX_HEALTH;
				if (p != null) {
					GameAudio.stopMusic(p, "", 0);
					GameAudio.musicOnce(p, "music.rise", 1f, 0);
				}
			}
			case RAMPAGE -> {
				if (from == Phase.RISE) {
					runTicks = 0;
				}
				intense = false;
				if (p != null) {
					if (from == Phase.RISE) {
						GameAudio.stopMusic(p, "music.rise", 16);
					} else {
						GameAudio.stopMusic(p, "", 20);
					}
					boolean late = runTicks >= INTENSE_AT;
					GameAudio.musicLoop(p, late ? "music.rampage_intense" : "music.rampage", 1f, from == Phase.RISE ? 0 : 20);
					intense = late;
				}
			}
			case MOTHRA -> {
				if (p != null) {
					GameAudio.stopMusic(p, "", 30);
					GameAudio.musicOnce(p, "music.mothra_arrival", 1f, 10);
				}
			}
			case VICTORY -> {
				victory = true;
				resultsAt = 330;
				loopAt = 400;
				if (p != null) {
					GameAudio.stopMusic(p, "", 12);
					GameAudio.ui(p, "ui.victory_sting", 1f, 1f);
					sendAll(new GameNet.Cue(GameNet.Cue.BANNER, 1f, "MOTHRA IS DEFEATED!", 0xFFD23C));
				}
			}
			case DEFEAT -> {
				victory = false;
				resultsAt = 260;
				loopAt = 300;
				if (p != null) {
					GameAudio.stopMusic(p, "", 10);
					GameAudio.at(p, GodzillaSounds.GODZILLA_DEATH, SoundSource.PLAYERS, p.getEyePosition(), 1f, 1f);
					sendAll(new GameNet.Cue(GameNet.Cue.BANNER, 1f, "GODZILLA HAS FALLEN", 0xFF5050));
				}
			}
			default -> {
			}
		}
		GzEvents.PHASE_CHANGED.fire(new GzEvents.PhaseChange(from, to));
		sendHud();
	}

	// ---------------------------------------------------------------- tick

	public void tick() {
		if (server == null) {
			return;
		}
		ticks++;
		if (pendingStartSet) {
			pendingStartSet = false;
			ServerPlayer who = pendingStart != null ? server.getPlayerList().getPlayer(pendingStart) : godzilla();
			pendingStart = null;
			if (who == null && !server.getPlayerList().getPlayers().isEmpty()) {
				who = server.getPlayerList().getPlayers().getFirst();
			}
			if (who != null && (phase == Phase.IDLE || results)) {
				start(who);
			}
		}
		ServerLevel level = level();
		if (level == null) {
			return;
		}
		if (schedulePrompt >= 0 && --schedulePrompt < 0 && phase == Phase.IDLE) {
			ServerPlayer p = godzilla();
			if (p == null && !server.getPlayerList().getPlayers().isEmpty()) {
				p = server.getPlayerList().getPlayers().getFirst();
				godzilla = p.getUUID();
			}
			if (p != null) {
				spawnSign(p, false);
			}
		}
		if (phase != Phase.IDLE) {
			if (godzilla() == null) {
				stop();
				return;
			}
			phaseTicks++;
			ServerPlayer p = godzilla();
			switch (phase) {
				case RISE -> {
					if (phaseTicks == RISE_SURFACE_TICK) {
						sendAll(new GameNet.Cue(GameNet.Cue.TITLE, 1f, "GODZILLA", 0xFF6A1A));
						GameAudio.ui(p, "ui.title_hit", 1f, 1f);
					}
					if (phaseTicks >= RISE_TICKS) {
						setPhase(Phase.RAMPAGE);
					}
				}
				case RAMPAGE -> {
					runTicks++;
					if (!intense && runTicks >= INTENSE_AT) {
						intense = true;
						GameAudio.musicLoop(p, "music.rampage_intense", 1f, CROSSFADE);
						GameAudio.stopMusic(p, "music.rampage", CROSSFADE);
					}
					if (runTicks >= MOTHRA_AT_RUN_TICKS) {
						setPhase(Phase.MOTHRA);
					}
				}
				case MOTHRA -> {
					runTicks++;
					if (phaseTicks == MOTHRA_ARRIVAL_MUSIC_TICKS) {
						GameAudio.musicLoop(p, "music.mothra_fight", 1f, 0);
					}
				}
				case VICTORY -> {
					if (phaseTicks == 10) {
						GameAudio.musicOnce(p, "music.victory", 1f, 0);
					}
					endScreens(p);
				}
				case DEFEAT -> {
					if (phaseTicks == 20) {
						GameAudio.musicOnce(p, "music.defeat", 1f, 0);
					}
					if (phaseTicks == 100) {
						GameAudio.ui(p, "ui.game_over", 1f, 1f);
					}
					endScreens(p);
				}
				default -> {
				}
			}
			tickCombo();
			popups.tick(level, ticks);
		}
		if (sign != null && sign.isRemoved()) {
			sign = null;
		}
		if (ticks % 5 == 0) {
			sendHud();
		}
	}

	private void endScreens(ServerPlayer p) {
		if (!results && phaseTicks >= resultsAt) {
			results = true;
			showResults(p);
		}
		if (results && phaseTicks == loopAt) {
			GameAudio.musicLoop(p, "music.results_loop", 0.9f, 20);
		}
	}

	// ---------------------------------------------------------------- combo and bonuses

	private static int groupOf(ScoreKind k) {
		return switch (k) {
			case BLOCKS, BUILDING -> 0;
			case LANDMARK -> 1;
			case FUEL_TANK, CAR, TRAIN -> 2;
			case PERSON -> 3;
			case TANK, HELICOPTER, JET, SHIP, MASER -> 4;
			case MOTHRA_HIT -> 5;
			case BONUS -> 6;
		};
	}

	private static boolean isKill(ScoreKind k) {
		return switch (k) {
			case PERSON, CAR, TANK, HELICOPTER, JET, SHIP, MASER, FUEL_TANK, TRAIN -> true;
			default -> false;
		};
	}

	private void feedCombo(ScoreKind kind, int points) {
		float w = kind == ScoreKind.BLOCKS ? Math.min(10f, 0.4f + points * 0.12f) : 8f + Math.min(40f, points / 40f);
		chain += w;
		comboTimer = COMBO_QUIET_TICKS;
		int lvl = 1;
		for (int i = COMBO_LEVELS.length - 1; i >= 0; i--) {
			if (chain >= COMBO_LEVELS[i]) {
				lvl = i + 1;
				break;
			}
		}
		if (lvl > comboLevel) {
			comboLevel = lvl;
			ServerPlayer p = godzilla();
			if (p != null) {
				GameAudio.ui(p, "ui.combo_up", 0.85f, 0.85f + 0.06f * lvl);
				sendAll(new GameNet.Cue(GameNet.Cue.BANNER, 0.6f, "COMBO x" + lvl, lvl >= 5 ? 0xFF5AD2 : lvl >= 3 ? 0xFFB030 : 0xFFE680));
			}
		}
	}

	private void tickCombo() {
		if (comboTimer > 0 && --comboTimer == 0) {
			if (comboLevel >= 2) {
				ServerPlayer p = godzilla();
				if (p != null) {
					GameAudio.ui(p, "ui.combo_break", 0.8f, 1f);
				}
				sendAll(new GameNet.Cue(GameNet.Cue.COMBO_BREAK, 1f, "", 0));
			}
			chain = 0;
			comboLevel = 1;
		}
	}

	private void noteKill(Vec3 where) {
		killTicks.addLast(ticks);
		while (!killTicks.isEmpty() && ticks - killTicks.peekFirst() > 24) {
			killTicks.removeFirst();
		}
		int n = killTicks.size();
		if (n < 4) {
			lastMultiKill = 0;
		} else if (n >= 5 && n >= Math.max(5, lastMultiKill * 2)) {
			lastMultiKill = n;
			bonus("MULTI-KILL x" + n, "", 60L * n, where, 0xFF8040, 14);
		}
	}

	public void onBuildingDestroyed(GzEvents.BuildingDestroyed e) {
		if (!phase.playing()) {
			return;
		}
		CityData.Building b = e.building();
		Vec3 c = b.center();
		buildingsDown++;
		if (b.landmark()) {
			landmarksDown++;
			bonus("LANDMARK DOWN!", b.name() == null ? "" : b.name(), 1500, c, 0xFFD23C, 22);
			return;
		}
		downedBuildings.add(new int[]{ticks, (int) c.x, (int) c.z});
		downedBuildings.removeIf(a -> ticks - a[0] > 240);
		int near = 0;
		for (int[] a : downedBuildings) {
			double dx = a[1] - c.x, dz = a[2] - c.z;
			if (dx * dx + dz * dz < 90 * 90) {
				near++;
			}
		}
		if (near >= 4 && ticks - lastLevelled > 300) {
			lastLevelled = ticks;
			bonus("BLOCK LEVELLED!", near + " buildings down", 600L * near / 2, c, 0x7CFF6A, 20);
		}
	}

	/** A bonus that does not feed or use the combo: score, big popup, banner and fanfare. */
	public void bonus(String text, String sub, long points, Vec3 where, int color, float size) {
		if (!phase.playing()) {
			return;
		}
		score += points;
		byKind[ScoreKind.BONUS.ordinal()] += points;
		countByKind[ScoreKind.BONUS.ordinal()]++;
		ServerLevel level = level();
		if (level != null) {
			popups.headline(level, where, text, "+" + String.format("%,d", points), color, size, ticks);
		}
		ServerPlayer p = godzilla();
		if (p != null) {
			GameAudio.ui(p, "ui.big_bonus", 1f, 1f);
		}
		sendAll(new GameNet.Cue(GameNet.Cue.BANNER, 1f, sub.isEmpty() ? text : text + "  " + sub, color));
	}

	// ---------------------------------------------------------------- results and HUD

	private static int rankIndex(long score) {
		int r = 0;
		for (int i = 0; i < RANK_SCORES.length; i++) {
			if (score >= RANK_SCORES[i]) {
				r = i;
			}
		}
		return r;
	}

	private void showResults(ServerPlayer p) {
		if (server == null) {
			return;
		}
		long[] groups = new long[7];
		for (ScoreKind k : ScoreKind.values()) {
			groups[groupOf(k)] += byKind[k.ordinal()];
		}
		int[] kills = {buildingsDown, landmarksDown,
				countByKind[ScoreKind.CAR.ordinal()] + countByKind[ScoreKind.TRAIN.ordinal()] + countByKind[ScoreKind.FUEL_TANK.ordinal()],
				countByKind[ScoreKind.PERSON.ordinal()],
				countByKind[ScoreKind.TANK.ordinal()] + countByKind[ScoreKind.HELICOPTER.ordinal()] + countByKind[ScoreKind.JET.ordinal()]
						+ countByKind[ScoreKind.SHIP.ordinal()] + countByKind[ScoreKind.MASER.ordinal()],
				countByKind[ScoreKind.MOTHRA_HIT.ordinal()]};
		long previous = BestScore.best();
		boolean newBest = BestScore.update(server, score);
		String rank = RANKS[rankIndex(score)];
		sendAll(new GameNet.Results(victory, score, BestScore.best(), newBest && score > 0, rank, groups, kills, runTicks / 20));
		GodzillaMod.LOGGER.info("godzilla game: results score {} rank {} best {} (was {})", score, rank, BestScore.best(), previous);
		spawnSign(p, true);
	}

	private void sendHud() {
		if (server == null) {
			return;
		}
		float[] m = MothraModule.bossHealth();
		GameNet.Hud h = new GameNet.Hud(phase.ordinal(), results, score, comboLevel, comboTimer / (float) COMBO_QUIET_TICKS,
				runTicks, health, MAX_HEALTH, m == null ? 0 : m[0], m == null ? 0 : m[1]);
		sendAll(h);
	}

	private void sendAll(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (server == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	// ---------------------------------------------------------------- the sign

	private void discardSign() {
		if (sign != null) {
			sign.discard();
			sign = null;
		}
	}

	/** Puts the "smash me" target in front of the player at a distance their hands reach. */
	public void spawnSign(ServerPlayer p, boolean restart) {
		discardSign();
		ServerLevel level = level();
		if (level == null) {
			return;
		}
		SmashSign s = new SmashSign(GameModule.SIGN, level);
		float scale = (float) p.getScale();
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		double dist = Math.max(8, 0.95 * scale);
		Vec3 at = p.getEyePosition().add(flat.scale(dist)).add(0, -0.1 * scale, 0);
		s.setPos(at.x, at.y - GameModule.SIGN_HEIGHT / 2.0, at.z);
		s.setup(restart, Math.max(2.5f, 0.55f * scale + 0.5f));
		level.addFreshEntity(s);
		sign = s;
	}

	// ---------------------------------------------------------------- misc for the commands

	public void skipToResults() {
		if ((phase == Phase.VICTORY || phase == Phase.DEFEAT) && !results) {
			phaseTicks = Math.max(phaseTicks, resultsAt - 1);
		}
	}

	public String describe() {
		return "phase %s (%d), run %d, score %d, combo x%d (chain %.0f, timer %d), health %.0f, kills %s, best %d".formatted(
				phase, phaseTicks, runTicks, score, comboLevel, chain, comboTimer, health, java.util.Arrays.toString(countByKind), BestScore.best());
	}
}
