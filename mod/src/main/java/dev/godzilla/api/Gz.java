package dev.godzilla.api;

import dev.godzilla.core.dev.DevCity;
import dev.godzilla.core.dev.DevDestruction;
import dev.godzilla.core.dev.DevFx;
import dev.godzilla.core.dev.DevGame;

/**
 * Where modules find each other. Each service starts as a simple stand-in from dev.godzilla.core.dev, so that any module can
 * be built and tried on its own; the owning module replaces it in its init() with the real thing. Always go through these
 * accessors at the time of use (never cache the result in a static field: the stand-in may not have been replaced yet).
 */
public final class Gz {
	private static Destruction destruction = new DevDestruction();
	private static City city = new DevCity();
	private static Game game = new DevGame();
	private static Fx fx = new DevFx();

	private Gz() {
	}

	public static Destruction destruction() {
		return destruction;
	}

	public static City city() {
		return city;
	}

	public static Game game() {
		return game;
	}

	public static Fx fx() {
		return fx;
	}

	public static void setDestruction(Destruction impl) {
		destruction = impl;
	}

	public static void setCity(City impl) {
		city = impl;
	}

	public static void setGame(Game impl) {
		game = impl;
	}

	public static void setFx(Fx impl) {
		fx = impl;
	}
}
