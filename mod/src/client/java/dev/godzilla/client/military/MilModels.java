package dev.godzilla.client.military;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the box models written by tools/military/make_assets.py (assets/godzilla/mil/&lt;name&gt;.json): a part tree with boxes and
 * their uv rectangles. Java only animates parts by name (see {@link MilModel}).
 */
public final class MilModels {
	public record PartInfo(String name, String parent) {
	}

	/** scale: 16 / pixels-per-block, applied by the renderer; parts: in file order (parents first). */
	public record Info(String name, float scale, int texW, int texH, List<PartInfo> parts) {
	}

	private static final Map<String, Info> INFO = new HashMap<>();

	private MilModels() {
	}

	public static synchronized Info info(String name) {
		Info i = INFO.get(name);
		if (i == null) {
			load(name);
			i = INFO.get(name);
		}
		return i;
	}

	/** The baked-model recipe; also fills {@link #info}. */
	public static LayerDefinition layer(String name) {
		return load(name);
	}

	private static synchronized LayerDefinition load(String name) {
		String path = "/assets/godzilla/mil/" + name + ".json";
		try (InputStream in = MilModels.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("missing " + path + " (run tools/military/make_assets.py)");
			}
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			int tw = o.get("texW").getAsInt(), th = o.get("texH").getAsInt();
			MeshDefinition mesh = new MeshDefinition();
			Map<String, PartDefinition> defs = new HashMap<>();
			defs.put("root", mesh.getRoot());
			List<PartInfo> parts = new ArrayList<>();
			for (JsonElement pe : o.getAsJsonArray("parts")) {
				JsonObject p = pe.getAsJsonObject();
				String pn = p.get("name").getAsString(), parent = p.get("parent").getAsString();
				CubeListBuilder cubes = CubeListBuilder.create();
				for (JsonElement be : p.getAsJsonArray("boxes")) {
					JsonObject b = be.getAsJsonObject();
					float[] ob = floats(b.getAsJsonArray("o")), sz = floats(b.getAsJsonArray("s"));
					int[] uv = ints(b.getAsJsonArray("uv"));
					cubes.texOffs(uv[0], uv[1]).addBox(ob[0], ob[1], ob[2], sz[0], sz[1], sz[2]);
				}
				float[] piv = floats(p.getAsJsonArray("pivot")), rot = floats(p.getAsJsonArray("rot"));
				PartDefinition pd = defs.get(parent).addOrReplaceChild(pn, cubes, PartPose.offsetAndRotation(piv[0], piv[1], piv[2], rot[0], rot[1], rot[2]));
				defs.put(pn, pd);
				parts.add(new PartInfo(pn, parent));
			}
			INFO.put(name, new Info(name, o.get("scale").getAsFloat(), tw, th, List.copyOf(parts)));
			return LayerDefinition.create(mesh, tw, th);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + path, e);
		}
	}

	private static float[] floats(JsonArray a) {
		float[] f = new float[a.size()];
		for (int i = 0; i < f.length; i++) {
			f[i] = a.get(i).getAsFloat();
		}
		return f;
	}

	private static int[] ints(JsonArray a) {
		int[] f = new int[a.size()];
		for (int i = 0; i < f.length; i++) {
			f[i] = a.get(i).getAsInt();
		}
		return f;
	}
}
