package dev.godzilla.military;

import dev.godzilla.api.CityData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The surface road graph of tokyo.json as arrays, with a snap-to-road helper. Built once per city data object. */
public final class RoadGraph {
	public final Vec3[] nodes;
	/** Edge i connects a[i] and b[i]. */
	public final int[] a, b;
	public final double[] len;
	/** For each node, the indexes of its edges. */
	public final List<int[]> nodeEdges = new ArrayList<>();

	private static RoadGraph cached;
	private static CityData cachedFor;

	public static synchronized RoadGraph of(CityData data) {
		if (cachedFor != data) {
			cached = new RoadGraph(data);
			cachedFor = data;
		}
		return cached;
	}

	private RoadGraph(CityData data) {
		List<BlockPos> n = data.roadNodes();
		this.nodes = new Vec3[n.size()];
		for (int i = 0; i < nodes.length; i++) {
			BlockPos p = n.get(i);
			this.nodes[i] = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		}
		int e = data.roadEdges().size();
		this.a = new int[e];
		this.b = new int[e];
		this.len = new double[e];
		List<List<Integer>> adj = new ArrayList<>();
		for (int i = 0; i < nodes.length; i++) {
			adj.add(new ArrayList<>());
		}
		for (int i = 0; i < e; i++) {
			CityData.RoadEdge r = data.roadEdges().get(i);
			a[i] = r.a();
			b[i] = r.b();
			len[i] = nodes[r.a()].distanceTo(nodes[r.b()]);
			adj.get(r.a()).add(i);
			adj.get(r.b()).add(i);
		}
		for (List<Integer> l : adj) {
			this.nodeEdges.add(l.stream().mapToInt(Integer::intValue).toArray());
		}
	}

	public boolean usable() {
		return a.length > 0;
	}

	/** Position on edge {@code e} at fraction t from node a to node b. */
	public Vec3 point(int e, double t) {
		return nodes[a[e]].lerp(nodes[b[e]], t);
	}

	/** Nearest point on any edge: {edge, t} packed in a record. */
	public record Snap(int edge, double t, double dist) {
	}

	public Snap snap(Vec3 p) {
		int best = 0;
		double bt = 0, bd = Double.MAX_VALUE;
		for (int i = 0; i < a.length; i++) {
			Vec3 pa = nodes[a[i]], pb = nodes[b[i]];
			Vec3 ab = pb.subtract(pa);
			double l2 = ab.lengthSqr();
			double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(pa).dot(ab) / l2));
			double d = pa.add(ab.scale(t)).distanceToSqr(p);
			if (d < bd) {
				bd = d;
				best = i;
				bt = t;
			}
		}
		return new Snap(best, bt, Math.sqrt(bd));
	}
}
