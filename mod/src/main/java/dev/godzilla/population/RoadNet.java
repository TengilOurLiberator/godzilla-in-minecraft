package dev.godzilla.population;

import dev.godzilla.api.CityData;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** The surface road graph (CityData.roadNodes/roadEdges) as arrays, with lane geometry for left-hand traffic. */
final class RoadNet {
	final int n;
	final double[] x, y, z;
	/** adjacent node indices and the carriageway width of the connecting edge, per node. */
	final int[][] adj;
	final int[][] adjWidth;
	/** the undirected edges (for random placement). */
	final int[] ea, eb, ew;

	private RoadNet(CityData d) {
		n = d.roadNodes().size();
		x = new double[n];
		y = new double[n];
		z = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = d.roadNodes().get(i).getX() + 0.5;
			y[i] = d.roadNodes().get(i).getY();
			z[i] = d.roadNodes().get(i).getZ() + 0.5;
		}
		List<List<int[]>> lists = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			lists.add(new ArrayList<>());
		}
		int m = d.roadEdges().size();
		ea = new int[m];
		eb = new int[m];
		ew = new int[m];
		for (int i = 0; i < m; i++) {
			CityData.RoadEdge e = d.roadEdges().get(i);
			ea[i] = e.a();
			eb[i] = e.b();
			ew[i] = e.width();
			lists.get(e.a()).add(new int[]{e.b(), e.width()});
			lists.get(e.b()).add(new int[]{e.a(), e.width()});
		}
		adj = new int[n][];
		adjWidth = new int[n][];
		for (int i = 0; i < n; i++) {
			adj[i] = lists.get(i).stream().mapToInt(a -> a[0]).toArray();
			adjWidth[i] = lists.get(i).stream().mapToInt(a -> a[1]).toArray();
		}
	}

	static RoadNet of(CityData d) {
		return new RoadNet(d);
	}

	boolean empty() {
		return ea.length == 0;
	}

	double length(int a, int b) {
		return Math.hypot(x[b] - x[a], z[b] - z[a]);
	}

	int width(int a, int b) {
		for (int i = 0; i < adj[a].length; i++) {
			if (adj[a][i] == b) {
				return adjWidth[a][i];
			}
		}
		return 5;
	}

	static double laneOffset(int width) {
		return Math.min(1.15, width / 4.3);
	}

	/**
	 * Picks the node to continue to after arriving at {@code at} from {@code from}. Normally random with a preference for
	 * going straight; when fleeing, the neighbour that leads farthest away from (fx, fz).
	 */
	int pickNext(int from, int at, RandomSource rnd, boolean flee, double fx, double fz) {
		int[] a = adj[at];
		if (a.length == 0) {
			return from;
		}
		if (a.length == 1) {
			return a[0];
		}
		double dxIn = x[at] - x[from], dzIn = z[at] - z[from];
		double lenIn = Math.max(1e-6, Math.hypot(dxIn, dzIn));
		int best = -1;
		double bestScore = -1e18;
		for (int k = 0; k < a.length; k++) {
			int c = a[k];
			if (c == from) {
				continue;
			}
			double dx = x[c] - x[at], dz = z[c] - z[at];
			double l = Math.max(1e-6, Math.hypot(dx, dz));
			double straight = (dx * dxIn + dz * dzIn) / (l * lenIn);
			double score;
			if (flee) {
				// distance from the threat after driving 30 blocks along the edge
				double px = x[at] + dx / l * Math.min(30, l), pz = z[at] + dz / l * Math.min(30, l);
				score = Math.hypot(px - fx, pz - fz) + rnd.nextDouble() * 8;
			} else {
				score = straight * 0.6 + rnd.nextDouble();
			}
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}
		return best < 0 ? from : best;
	}
}
