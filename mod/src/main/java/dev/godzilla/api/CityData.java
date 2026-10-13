package dev.godzilla.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** tokyo.json as Java records. Positions are world block coordinates. See docs/DESIGN.md "City file format". */
public record CityData(int seaLevel, int groundY, BlockPos origin, BlockPos size, Start playerStart,
					   List<Building> buildings, List<BlockPos> roadNodes, List<RoadEdge> roadEdges,
					   List<List<BlockPos>> rails, List<BlockPos> sidewalkSpawns, List<BlockPos> tankSpawns,
					   List<BlockPos> maserSpawns, List<ShipSpawn> shipSpawns, List<List<BlockPos>> airLanes,
					   Vec3 mothraFrom, Vec3 mothraTo) {

	public static final String RESOURCE = "/data/godzilla/city/tokyo.json";

	public static final CityData EMPTY = new CityData(62, 63, BlockPos.ZERO, BlockPos.ZERO, new Start(new Vec3(0.5, 29, 0.5), 0),
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			new Vec3(0, 140, -200), new Vec3(0, 110, 0));

	public record Start(Vec3 pos, float yaw) {
	}

	/** min and max are inclusive block corners. name is null except for landmarks. */
	public record Building(int id, String kind, @Nullable String name, BlockPos min, BlockPos max, int blocks, int value) {
		public boolean contains(int x, int y, int z) {
			return x >= min.getX() && x <= max.getX() && y >= min.getY() && y <= max.getY() && z >= min.getZ() && z <= max.getZ();
		}

		public boolean landmark() {
			return "landmark".equals(kind);
		}

		public Vec3 center() {
			return new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
		}
	}

	/** a and b index {@link #roadNodes}; width is the carriageway width in blocks. */
	public record RoadEdge(int a, int b, int width) {
	}

	public record ShipSpawn(BlockPos pos, float yaw) {
	}

	public boolean isEmpty() {
		return buildings.isEmpty();
	}

	/** Reads the city metadata packed in the mod jar; {@link #EMPTY} when it is not there. */
	public static CityData loadFromJar() {
		try (InputStream in = CityData.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				return EMPTY;
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("cannot read " + RESOURCE, e);
		}
	}

	public static CityData parse(Reader reader) {
		JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
		List<Building> buildings = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("buildings")) {
			JsonObject b = e.getAsJsonObject();
			buildings.add(new Building(b.get("id").getAsInt(), b.get("kind").getAsString(),
					b.has("name") && !b.get("name").isJsonNull() ? b.get("name").getAsString() : null,
					pos(b.get("min")), pos(b.get("max")), b.get("blocks").getAsInt(), b.get("value").getAsInt()));
		}
		JsonObject roads = o.getAsJsonObject("roads");
		List<RoadEdge> edges = new ArrayList<>();
		for (JsonElement e : roads.getAsJsonArray("edges")) {
			JsonArray a = e.getAsJsonArray();
			edges.add(new RoadEdge(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
		}
		List<ShipSpawn> ships = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("shipSpawns")) {
			JsonArray a = e.getAsJsonArray();
			ships.add(new ShipSpawn(pos(a), a.size() > 3 ? a.get(3).getAsFloat() : 0));
		}
		JsonObject start = o.getAsJsonObject("playerStart");
		JsonObject mothra = o.getAsJsonObject("mothraArrival");
		return new CityData(o.get("seaLevel").getAsInt(), o.get("groundY").getAsInt(), pos(o.get("origin")), pos(o.get("size")),
				new Start(vec(start.get("pos")), start.get("yaw").getAsFloat()),
				List.copyOf(buildings), posList(roads.get("nodes")), List.copyOf(edges),
				posLists(o.get("rails")), posList(o.get("sidewalkSpawns")), posList(o.get("tankSpawns")),
				posList(o.get("maserSpawns")), List.copyOf(ships), posLists(o.get("airLanes")),
				vec(mothra.get("from")), vec(mothra.get("to")));
	}

	private static BlockPos pos(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new BlockPos((int) Math.floor(a.get(0).getAsDouble()), (int) Math.floor(a.get(1).getAsDouble()), (int) Math.floor(a.get(2).getAsDouble()));
	}

	private static Vec3 vec(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	private static List<BlockPos> posList(@Nullable JsonElement e) {
		List<BlockPos> out = new ArrayList<>();
		if (e != null) {
			for (JsonElement p : e.getAsJsonArray()) {
				out.add(pos(p));
			}
		}
		return List.copyOf(out);
	}

	private static List<List<BlockPos>> posLists(@Nullable JsonElement e) {
		List<List<BlockPos>> out = new ArrayList<>();
		if (e != null) {
			for (JsonElement l : e.getAsJsonArray()) {
				out.add(posList(l));
			}
		}
		return List.copyOf(out);
	}
}
