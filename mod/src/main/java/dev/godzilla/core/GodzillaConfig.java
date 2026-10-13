package dev.godzilla.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The knobs the owner may turn up (or down) in one place: {@code config/godzilla.properties} of the game or server folder. The file is
 * written with the defaults on the first start; a missing or unreadable key keeps its default. The server reads its own file (budgets of
 * people, vehicles and pieces), the VR client reads its own (debris on screen). Changes apply after a restart.
 */
public final class GodzillaConfig {
	// ---- client (the game folder's config)
	/** Most flying debris pieces drawn at once. The client also thins this out by itself when the frame rate drops (see debrisAdaptive). */
	public static int debrisMax = 24000;
	/** Let the client shrink the debris budget when frames take too long. Turn off only with a lot of headroom. */
	public static boolean debrisAdaptive = true;

	// ---- server (the server folder's config)
	/** Debris pieces the server may send to the clients per tick (a collapse is spread over ticks beyond this). */
	public static int debrisPiecesPerTick = 5000;
	/** Civilians kept alive around Godzilla, and the hard maximum. */
	public static int peopleTarget = 450;
	public static int peopleMax = 650;
	/** Moving and parked cars, buses and trucks, police cars, trains. */
	public static int carsTarget = 70;
	public static int parkedTarget = 26;
	public static int bigVehiclesTarget = 10;
	public static int policeTarget = 6;
	public static int trainsTarget = 2;
	/** Multiplier on how many military units the director keeps alive (tanks, helicopters, jets, masers; warships stay). */
	public static double militaryScale = 1.0;

	private GodzillaConfig() {
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("godzilla.properties");
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				p.load(r);
			} catch (IOException e) {
				GodzillaMod.LOGGER.warn("godzilla: cannot read {}: {}", file, e.toString());
			}
		}
		debrisMax = getInt(p, "debris.max", debrisMax, 500, 120000);
		debrisAdaptive = Boolean.parseBoolean(p.getProperty("debris.adaptive", Boolean.toString(debrisAdaptive)));
		debrisPiecesPerTick = getInt(p, "debris.piecesPerTick", debrisPiecesPerTick, 200, 100000);
		peopleTarget = getInt(p, "people.target", peopleTarget, 0, 3000);
		peopleMax = Math.max(peopleTarget, getInt(p, "people.max", peopleMax, 0, 4000));
		carsTarget = getInt(p, "vehicles.cars", carsTarget, 0, 600);
		parkedTarget = getInt(p, "vehicles.parked", parkedTarget, 0, 600);
		bigVehiclesTarget = getInt(p, "vehicles.big", bigVehiclesTarget, 0, 200);
		policeTarget = getInt(p, "vehicles.police", policeTarget, 0, 100);
		trainsTarget = getInt(p, "vehicles.trains", trainsTarget, 0, 8);
		militaryScale = Math.max(0.0, Math.min(4.0, getDouble(p, "military.scale", militaryScale)));
		if (!Files.exists(file)) {
			write(file);
		}
		GodzillaMod.LOGGER.info("godzilla config: debris {} (adaptive {}), pieces/tick {}, people {}/{}, cars {} parked {} big {} police {} trains {}, military x{}",
				debrisMax, debrisAdaptive, debrisPiecesPerTick, peopleTarget, peopleMax, carsTarget, parkedTarget, bigVehiclesTarget, policeTarget, trainsTarget, militaryScale);
	}

	private static int getInt(Properties p, String key, int def, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim())));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static double getDouble(Properties p, String key, double def) {
		try {
			return Double.parseDouble(p.getProperty(key, Double.toString(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static void write(Path file) {
		String text = """
				# Godzilla VR: budgets. Restart the game / server after editing. Delete this file to get the defaults back.
				# CLIENT (the game folder): debris
				debris.max=%d
				debris.adaptive=%s
				# SERVER (the server folder): how much is sent and how much lives in the city
				debris.piecesPerTick=%d
				people.target=%d
				people.max=%d
				vehicles.cars=%d
				vehicles.parked=%d
				vehicles.big=%d
				vehicles.police=%d
				vehicles.trains=%d
				military.scale=%s
				""".formatted(debrisMax, debrisAdaptive, debrisPiecesPerTick, peopleTarget, peopleMax, carsTarget, parkedTarget, bigVehiclesTarget,
				policeTarget, trainsTarget, militaryScale);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) {
				w.write(text);
			}
		} catch (IOException e) {
			GodzillaMod.LOGGER.warn("godzilla: cannot write {}: {}", file, e.toString());
		}
	}
}
