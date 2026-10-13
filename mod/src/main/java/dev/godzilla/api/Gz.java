package dev.godzilla.api;

/**
 * Where modules find each other. Every service is installed by the module that owns it in that module's init() (city, destruction,
 * game, player for Fx); there are no stand-ins any more. Always go through these accessors at the time of use.
 */
public final class Gz {
	private static Destruction destruction;
	private static City city;
	private static Game game;
	private static Fx fx;

	private Gz() {
	}

	private static <T> T need(T service, String name) {
		if (service == null) {
			throw new IllegalStateException("godzilla: the " + name + " service is not installed yet (module init order)");
		}
		return service;
	}

	public static Destruction destruction() {
		return need(destruction, "destruction");
	}

	public static City city() {
		return need(city, "city");
	}

	public static Game game() {
		return need(game, "game");
	}

	public static Fx fx() {
		return need(fx, "fx");
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

	/** One line naming the installed implementations (logged when the server starts). */
	public static String describe() {
		return "destruction=" + name(destruction) + " city=" + name(city) + " game=" + name(game) + " fx=" + name(fx);
	}

	private static String name(Object o) {
		return o == null ? "MISSING" : o.getClass().getName();
	}
}
