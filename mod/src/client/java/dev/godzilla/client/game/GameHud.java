package dev.godzilla.client.game;

import dev.godzilla.api.Game;
import dev.godzilla.game.net.GameNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the game draws on the HUD: score and combo, the military alert level (a countdown-free sense of the run's time),
 * the two health bars once Mothra is here, the breath gauge, the title, banners, the red flash, the start prompt and the
 * results. Registered as a Fabric HUD element, so it is part of the normal GUI layer: Vivecraft puts that layer on its floating
 * HUD quad in the headset (scaled by the world scale) and flat players see it in the corners of the screen.
 * Everything important sits in a column at the top centre, close to where the eyes are, not in the screen corners.
 */
public final class GameHud {
	private record Banner(String text, int color, int born, float size) {
	}

	private static GameNet.Hud hud;
	private static GameNet.Results results;
	private static int resultsStart;
	private static int ticks;
	private static String titleText = "";
	private static int titleStart = -1000;
	private static int flashStart = -1000;
	private static float flashStrength;
	private static int breakStart = -1000;
	private static final List<Banner> BANNERS = new ArrayList<>();
	/** Score shown on screen runs up toward the real score. */
	private static float shownScore;

	private GameHud() {
	}

	public static void tick() {
		ticks++;
		if (hud != null) {
			shownScore += (hud.score() - shownScore) * 0.25f;
			if (Math.abs(hud.score() - shownScore) < 1) {
				shownScore = hud.score();
			}
		}
		BANNERS.removeIf(b -> ticks - b.born > (b.size >= 1f ? 80 : 40));
	}

	public static void onHud(GameNet.Hud h) {
		boolean newRun = hud != null && h.runTicks() < hud.runTicks() - 100;
		hud = h;
		if (newRun || h.score() == 0) {
			shownScore = Math.min(shownScore, h.score());
		}
		if (h.phase() < Game.Phase.VICTORY.ordinal() && !h.results()) {
			results = null;
		}
	}

	public static void onCue(GameNet.Cue c) {
		switch (c.kind()) {
			case GameNet.Cue.TITLE -> {
				titleText = c.text();
				titleStart = ticks;
			}
			case GameNet.Cue.FLASH -> {
				flashStart = ticks;
				flashStrength = c.strength();
			}
			case GameNet.Cue.BANNER -> {
				BANNERS.add(new Banner(c.text(), c.color(), ticks, c.strength()));
				while (BANNERS.size() > 3) {
					BANNERS.removeFirst();
				}
			}
			case GameNet.Cue.COMBO_BREAK -> breakStart = ticks;
			default -> {
			}
		}
	}

	public static void onResults(GameNet.Results r) {
		results = r;
		resultsStart = ticks;
	}

	// ------------------------------------------------------------------------------------------------------------------------

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void big(GuiGraphicsExtractor g, Font font, String text, float cx, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		g.centeredText(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int colorA, int colorB) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
		g.fill(x, y, x + w, y + h, 0xFF2A2A33);
		int fw = Math.round(w * Mth.clamp(frac, 0f, 1f));
		if (fw > 0) {
			g.fillGradient(x, y, x + fw, y + h, colorA, colorB);
			g.fill(x, y, x + fw, y + 1, argb(110, 0xFFFFFF));
		}
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || hud == null) {
			return;
		}
		float pt = dt.getGameTimeDeltaPartialTick(false);
		float time = ticks + pt;
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		float cx = w / 2f;
		Game.Phase phase = Game.Phase.values()[Mth.clamp(hud.phase(), 0, Game.Phase.values().length - 1)];

		drawFlash(g, w, h, time);

		if (phase == Game.Phase.IDLE) {
			drawPrompt(g, font, cx, h, time);
			return;
		}
		boolean showResults = hud.results() && results != null;
		if (!showResults && (phase.playing() || phase == Game.Phase.VICTORY || phase == Game.Phase.DEFEAT)) {
			drawScorePanel(g, font, cx, h, time, phase);
			drawBreath(g, font, cx, h, time, phase);
		}
		drawBanners(g, font, cx, h, time);
		drawTitle(g, font, cx, h, time);
		if (showResults) {
			drawResults(g, font, cx, h, time);
		}
	}

	private static void drawFlash(GuiGraphicsExtractor g, int w, int h, float time) {
		float age = time - flashStart;
		if (age < 0 || age > 14) {
			return;
		}
		float a = (1f - age / 14f) * flashStrength;
		int edge = Math.max(14, h / 6);
		int hot = argb((int) (170 * a), 0xFF1010);
		int none = argb(0, 0xFF1010);
		g.fillGradient(0, 0, w, edge, hot, none);
		g.fillGradient(0, h - edge, w, h, none, hot);
		int side = Math.max(6, w / 40);
		g.fill(0, edge, side, h - edge, argb((int) (110 * a), 0xFF1010));
		g.fill(w - side, edge, w, h - edge, argb((int) (110 * a), 0xFF1010));
	}

	private static void drawPrompt(GuiGraphicsExtractor g, Font font, float cx, int h, float time) {
		float bob = (float) Math.sin(time * 0.12) * 2;
		big(g, font, "GODZILLA", cx, 14 + bob, 4.5f, argb(255, 0xFF7A1F));
		big(g, font, "SMASH THE FLOATING SIGN TO BEGIN", cx, 52, 1.4f, argb(255, 0xFFFFFF));
		big(g, font, "(or ROAR)", cx, 66, 1.0f, argb(255, 0xB0B8C8));
	}

	private static void drawScorePanel(GuiGraphicsExtractor g, Font font, float cx, int h, float time, Game.Phase phase) {
		float y = 4;
		big(g, font, "SCORE", cx, y, 0.9f, argb(255, 0xB8C4E0));
		String s = String.format("%,d", Math.round(shownScore));
		big(g, font, s, cx, y + 10, 3.0f, argb(255, 0xFFFFFF));
		y += 36;
		int combo = hud.combo();
		if (combo >= 2) {
			int col = combo >= 6 ? 0xFF4FD8 : combo >= 4 ? 0xFF8A2A : combo >= 3 ? 0xFFC030 : 0xFFE680;
			big(g, font, "COMBO x" + combo, cx, y, 1.8f, argb(255, col));
			int bw = 90;
			bar(g, (int) cx - bw / 2, (int) y + 18, bw, 4, hud.comboFrac(), argb(255, col), argb(255, col & 0xB0B0B0));
			y += 28;
		} else if (time - breakStart < 30) {
			big(g, font, "COMBO LOST", cx, y + 2, 1.2f, argb((int) (255 * (1 - (time - breakStart) / 30f)), 0xFF5050));
			y += 18;
		} else {
			y += 6;
		}
		boolean boss = phase == Game.Phase.MOTHRA || phase == Game.Phase.VICTORY || phase == Game.Phase.DEFEAT;
		if (boss) {
			int bw = 150;
			int lx = (int) cx - bw / 2 - 52;
			float gz = hud.healthMax() <= 0 ? 0 : hud.health() / hud.healthMax();
			g.text(font, "GODZILLA", lx, (int) y, argb(255, 0xFFE0A0), true);
			int c1 = gz > 0.5f ? 0xFF7CE860 : gz > 0.25f ? 0xFFE8C040 : 0xFFE84040;
			bar(g, (int) cx - bw / 2 + 20, (int) y + 1, bw, 7, gz, c1, c1 & 0xFFB0B0B0);
			if (hud.mothraMax() > 0) {
				float m = hud.mothra() / hud.mothraMax();
				g.text(font, "MOTHRA", lx + 12, (int) y + 14, argb(255, 0xE0C0FF), true);
				bar(g, (int) cx - bw / 2 + 20, (int) y + 15, bw, 7, m, 0xFFD060FF, 0xFFFFC040);
			}
		} else if (phase == Game.Phase.RAMPAGE) {
			// military alert: more pips as the run goes on; there is no number to count down
			int pips = Mth.clamp(hud.runTicks() / 640 + 1, 1, 5);
			int pw = 12, gap = 3;
			int x0 = (int) cx - (5 * pw + 4 * gap) / 2;
			g.text(font, "ALERT", (int) cx - font.width("ALERT") / 2, (int) y - 1, argb(255, 0xFF9090), true);
			for (int i = 0; i < 5; i++) {
				boolean on = i < pips;
				int col = on ? (pips >= 4 ? 0xFFFF3030 : 0xFFFFA030) : 0xFF3A3A44;
				g.fill(x0 + i * (pw + gap), (int) y + 9, x0 + i * (pw + gap) + pw, (int) y + 14, col);
			}
		}
	}

	private static void drawBreath(GuiGraphicsExtractor g, Font font, float cx, int h, float time, Game.Phase phase) {
		if (!phase.playing()) {
			return;
		}
		float lvl = BreathGauge.level();
		int bw = 130;
		int y = h - 44;
		bar(g, (int) cx - bw / 2, y, bw, 6, lvl, 0xFF3A7BFF, 0xFFA0F0FF);
		if (lvl >= 0.999f) {
			float glow = 0.5f + 0.5f * (float) Math.sin(time * 0.3);
			g.outline((int) cx - bw / 2 - 2, y - 2, bw + 4, 10, argb((int) (120 + 120 * glow), 0xA0F0FF));
		}
		big(g, font, "ATOMIC BREATH", cx, y - 9, 0.8f, argb(255, 0xA8D8FF));
	}

	private static void drawBanners(GuiGraphicsExtractor g, Font font, float cx, int h, float time) {
		float yy = h * 0.30f;
		for (int i = BANNERS.size() - 1; i >= 0; i--) {
			Banner b = BANNERS.get(i);
			float age = time - b.born;
			float in = Mth.clamp(age / 5f, 0f, 1f);
			float life = b.size >= 1f ? 80f : 40f;
			float out = Mth.clamp((life - age) / 14f, 0f, 1f);
			float scale = (b.size >= 1f ? 2.4f : 1.5f) * (0.7f + 0.3f * in) * (b.size >= 1f && age < 6 ? 1.2f : 1f);
			big(g, font, b.text, cx, yy, scale, argb((int) (255 * Math.min(in, out)), b.color));
			yy += b.size >= 1f ? 26 : 16;
		}
	}

	private static void drawTitle(GuiGraphicsExtractor g, Font font, float cx, int h, float time) {
		float age = time - titleStart;
		if (age < 0 || age > 120) {
			return;
		}
		float in = Mth.clamp(age / 6f, 0f, 1f);
		float out = Mth.clamp((120 - age) / 25f, 0f, 1f);
		float scale = 6.5f + age * 0.02f + (1 - in) * 5f;
		int a = (int) (255 * Math.min(in, out));
		big(g, font, titleText, cx + 2, h * 0.30f + 2, scale, argb(a, 0x000000));
		big(g, font, titleText, cx, h * 0.30f, scale, argb(a, 0xFF6A1A));
	}

	private static final String[] GROUPS = {"Buildings", "Landmarks", "Vehicles", "People", "Military", "Mothra", "Bonuses"};
	private static final String[] KILL_LABELS = {" down", " down", " wrecked", " lost", " destroyed", " hits", ""};

	private static void drawResults(GuiGraphicsExtractor g, Font font, float cx, int h, float time) {
		GameNet.Results r = results;
		float age = time - resultsStart;
		int pw = 250;
		int ph = 188;
		int x0 = (int) cx - pw / 2;
		int y0 = Math.max(4, h / 2 - ph / 2 - 6);
		g.fill(x0 - 2, y0 - 2, x0 + pw + 2, y0 + ph + 2, argb(235, r.victory() ? 0x6A4A00 : 0x4A0A0A));
		g.fill(x0, y0, x0 + pw, y0 + ph, argb(225, 0x0A0E1C));
		big(g, font, r.victory() ? "MOTHRA DEFEATED!" : "GODZILLA FALLS", cx, y0 + 6, 2.2f, argb(255, r.victory() ? 0xFFD23C : 0xFF5050));
		int y = y0 + 30;
		for (int i = 0; i < GROUPS.length; i++) {
			if (age < 6 + i * 6) {
				break;
			}
			long v = i < r.groups().length ? r.groups()[i] : 0;
			String left = GROUPS[i];
			String kills = i < r.kills().length && i < 6 ? "  " + r.kills()[i] + KILL_LABELS[i] : "";
			g.text(font, left, x0 + 12, y, argb(255, 0xE8ECF8), true);
			g.text(font, kills, x0 + 12 + font.width(left), y, argb(255, 0x8890A8), false);
			String vs = String.format("%,d", v);
			g.text(font, vs, x0 + pw - 12 - font.width(vs), y, argb(255, 0xFFE9A0), true);
			y += 12;
		}
		y = y0 + 30 + GROUPS.length * 12 + 4;
		if (age > 6 + GROUPS.length * 6) {
			g.fill(x0 + 10, y - 3, x0 + pw - 10, y - 2, argb(160, 0xFFFFFF));
			float countUp = Mth.clamp((age - 6 - GROUPS.length * 6) / 24f, 0f, 1f);
			big(g, font, "TOTAL  " + String.format("%,d", Math.round(r.score() * countUp)), cx, y + 2, 2.1f, argb(255, 0xFFFFFF));
			if (countUp >= 1f) {
				big(g, font, r.rank().toUpperCase(), cx, y + 24, 1.7f, argb(255, 0xFFB030));
				String best = (r.newBest() ? "NEW BEST!  " : "BEST  ") + String.format("%,d", r.best());
				int bc = r.newBest() && ((int) (time / 6) % 2 == 0) ? 0xFFFFFF : 0x7CFF6A;
				big(g, font, best, cx, y + 40, 1.2f, argb(255, r.newBest() ? bc : 0xA0B0D0));
				big(g, font, "Smash the sign to play again", cx, y + 54, 1.0f, argb(255, 0x66E8FF));
			}
		}
	}
}
