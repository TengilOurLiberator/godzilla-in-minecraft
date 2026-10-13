package dev.godzilla.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.godzilla.core.GodzillaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The best score, kept in the world folder: {@code <world>/data/godzilla_best.json}. */
final class BestScore {
	private static long best;

	private BestScore() {
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("godzilla_best.json");
	}

	static long best() {
		return best;
	}

	static void load(MinecraftServer server) {
		best = 0;
		try {
			Path f = file(server);
			if (Files.exists(f)) {
				JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
				best = o.get("best").getAsLong();
			}
		} catch (Exception e) {
			GodzillaMod.LOGGER.warn("godzilla: cannot read the best score", e);
		}
	}

	/** Returns true when this score is a new best (and stores it). */
	static boolean update(MinecraftServer server, long score) {
		if (score <= best) {
			return false;
		}
		best = score;
		try {
			Path f = file(server);
			Files.createDirectories(f.getParent());
			Files.writeString(f, "{\"best\": " + score + "}\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			GodzillaMod.LOGGER.warn("godzilla: cannot write the best score", e);
		}
		return true;
	}
}
