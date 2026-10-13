package dev.godzilla.client.player;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;

/**
 * A tiny immediate mesh recorder for the Godzilla model: convex solids and prisms are transformed by a matrix stack and
 * collected per material, then submitted as custom geometry. Texture coordinates are planar projections of the part-local
 * coordinates (tile size {@link #TILE} blocks), so every box has the same texel density without a UV layout; the textures are
 * tileable (the entity sampler repeats).
 */
final class Mesh {
	static final int SKIN = 0;
	static final int BELLY = 1;
	static final int PLATE = 2;
	static final int BONE = 3;
	static final int MOUTH = 4;
	static final int GLOW = 5;
	static final int EYE = 6;
	static final int COUNT = 7;
	static final float TILE = 6.0F;

	static final class Buf {
		float[] f = new float[9 * 512];
		int[] c = new int[512];
		int n;

		void add(float x, float y, float z, float nx, float ny, float nz, float u, float v, int color) {
			if (n == c.length) {
				f = java.util.Arrays.copyOf(f, f.length * 2);
				c = java.util.Arrays.copyOf(c, c.length * 2);
			}
			int i = n * 9;
			f[i] = x;
			f[i + 1] = y;
			f[i + 2] = z;
			f[i + 3] = nx;
			f[i + 4] = ny;
			f[i + 5] = nz;
			f[i + 6] = u;
			f[i + 7] = v;
			c[n++] = color;
		}
	}

	final Buf[] bufs = new Buf[COUNT];
	private final ArrayDeque<Matrix4f> stack = new ArrayDeque<>();
	Matrix4f cur = new Matrix4f();
	private final Vector3f tmp = new Vector3f();
	private final Vector3f tmpN = new Vector3f();

	Mesh() {
		for (int i = 0; i < COUNT; i++) {
			bufs[i] = new Buf();
		}
	}

	void push() {
		stack.push(new Matrix4f(cur));
	}

	void pop() {
		cur = stack.pop();
	}

	void translate(double x, double y, double z) {
		cur.translate((float) x, (float) y, (float) z);
	}

	void rotX(double a) {
		cur.rotateX((float) a);
	}

	void rotY(double a) {
		cur.rotateY((float) a);
	}

	void rotZ(double a) {
		cur.rotateZ((float) a);
	}

	void scale(double s) {
		cur.scale((float) s);
	}

	// ------------------------------------------------------------------ primitives

	/** A quad v0..v3 given in local space; the winding is fixed so that the front faces the outward hint. */
	void quad(int mat, int color, double[] a, double[] b, double[] c, double[] d, double hx, double hy, double hz) {
		double e1x = b[0] - a[0];
		double e1y = b[1] - a[1];
		double e1z = b[2] - a[2];
		double e2x = d[0] - a[0];
		double e2y = d[1] - a[1];
		double e2z = d[2] - a[2];
		double nx = e1y * e2z - e1z * e2y;
		double ny = e1z * e2x - e1x * e2z;
		double nz = e1x * e2y - e1y * e2x;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-9) {
			// degenerate (triangle given as a quad with a repeated vertex): use the other pair of edges
			e2x = c[0] - a[0];
			e2y = c[1] - a[1];
			e2z = c[2] - a[2];
			nx = e1y * e2z - e1z * e2y;
			ny = e1z * e2x - e1x * e2z;
			nz = e1x * e2y - e1y * e2x;
			len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-9) {
				return;
			}
		}
		nx /= len;
		ny /= len;
		nz /= len;
		if (nx * hx + ny * hy + nz * hz < 0) {
			double[] t = b;
			b = d;
			d = t;
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		double ax = Math.abs(nx);
		double ay = Math.abs(ny);
		double az = Math.abs(nz);
		Buf buf = bufs[mat];
		tmpN.set((float) nx, (float) ny, (float) nz);
		cur.transformDirection(tmpN).normalize();
		float wx = tmpN.x;
		float wy = tmpN.y;
		float wz = tmpN.z;
		double[][] vs = {a, b, c, d};
		for (double[] v : vs) {
			float u;
			float w;
			if (ax >= ay && ax >= az) {
				u = (float) v[2] / TILE;
				w = (float) v[1] / TILE;
			} else if (ay >= az) {
				u = (float) v[0] / TILE;
				w = (float) v[2] / TILE;
			} else {
				u = (float) v[0] / TILE;
				w = (float) v[1] / TILE;
			}
			tmp.set((float) v[0], (float) v[1], (float) v[2]);
			cur.transformPosition(tmp);
			buf.add(tmp.x, tmp.y, tmp.z, wx, wy, wz, u, w, color);
		}
	}

	/** Convex solid from 8 corners indexed x + 2y + 4z (0 = min side). */
	void solid(int mat, int color, double[][] c) {
		double cx = 0;
		double cy = 0;
		double cz = 0;
		for (double[] p : c) {
			cx += p[0] / 8;
			cy += p[1] / 8;
			cz += p[2] / 8;
		}
		int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
		for (int[] f : faces) {
			double[] a = c[f[0]];
			double[] b = c[f[1]];
			double[] cc = c[f[2]];
			double[] d = c[f[3]];
			double mx = (a[0] + b[0] + cc[0] + d[0]) / 4 - cx;
			double my = (a[1] + b[1] + cc[1] + d[1]) / 4 - cy;
			double mz = (a[2] + b[2] + cc[2] + d[2]) / 4 - cz;
			quad(mat, color, a, b, cc, d, mx, my, mz);
		}
	}

	void box(int mat, int color, double cx, double cy, double cz, double sx, double sy, double sz) {
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++) {
			c[i] = new double[]{cx + ((i & 1) == 0 ? -sx : sx) / 2, cy + ((i & 2) == 0 ? -sy : sy) / 2, cz + ((i & 4) == 0 ? -sz : sz) / 2};
		}
		solid(mat, color, c);
	}

	/** A tapered limb between two points: widths w0, d0 at the start and w1, d1 at the end, with a hint for the side axis. */
	void limb(int mat, int color, double[] from, double[] to, double w0, double d0, double w1, double d1, double[] upHint) {
		double yx = to[0] - from[0];
		double yy = to[1] - from[1];
		double yz = to[2] - from[2];
		double yl = Math.sqrt(yx * yx + yy * yy + yz * yz);
		if (yl < 1e-6) {
			return;
		}
		yx /= yl;
		yy /= yl;
		yz /= yl;
		// x axis = up x y
		double xx = upHint[1] * yz - upHint[2] * yy;
		double xy = upHint[2] * yx - upHint[0] * yz;
		double xz = upHint[0] * yy - upHint[1] * yx;
		double xl = Math.sqrt(xx * xx + xy * xy + xz * xz);
		if (xl < 1e-6) {
			xx = 1;
			xy = 0;
			xz = 0;
			xl = 1;
		}
		xx /= xl;
		xy /= xl;
		xz /= xl;
		// z axis = x x y
		double zx = xy * yz - xz * yy;
		double zy = xz * yx - xx * yz;
		double zz = xx * yy - xy * yx;
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++) {
			boolean top = (i & 2) != 0;
			double w = (top ? w1 : w0) / 2 * ((i & 1) == 0 ? -1 : 1);
			double d = (top ? d1 : d0) / 2 * ((i & 4) == 0 ? -1 : 1);
			double[] base = top ? to : from;
			c[i] = new double[]{base[0] + xx * w + zx * d, base[1] + xy * w + zy * d, base[2] + xz * w + zz * d};
		}
		solid(mat, color, c);
	}

	/**
	 * A flat plate standing on a surface: polygon in (along, out) coordinates, extruded sideways by thickness. The profile is a
	 * leaf shape of the given length (along the spine) and height (out of the surface).
	 */
	void plate(int mat, int color, double[] p, double[] out, double[] along, double length, double height, double thick) {
		double sx = out[1] * along[2] - out[2] * along[1];
		double sy = out[2] * along[0] - out[0] * along[2];
		double sz = out[0] * along[1] - out[1] * along[0];
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sl < 1e-6) {
			return;
		}
		sx /= sl;
		sy /= sl;
		sz /= sl;
		double[][] prof = {{-0.5, 0}, {0.5, 0}, {0.44, 0.45}, {0.1, 1.0}, {-0.3, 0.55}};
		int n = prof.length;
		double[][][] side = new double[2][n][];
		for (int k = 0; k < 2; k++) {
			double t = (k == 0 ? -0.5 : 0.5) * thick;
			for (int i = 0; i < n; i++) {
				double a = prof[i][0] * length;
				double o = prof[i][1] * height;
				side[k][i] = new double[]{p[0] + along[0] * a + out[0] * o + sx * t, p[1] + along[1] * a + out[1] * o + sy * t, p[2] + along[2] * a + out[2] * o + sz * t};
			}
		}
		double cx = p[0] + out[0] * height * 0.4;
		double cy = p[1] + out[1] * height * 0.4;
		double cz = p[2] + out[2] * height * 0.4;
		for (int k = 0; k < 2; k++) {
			double hx = (k == 0 ? -sx : sx);
			double hy = (k == 0 ? -sy : sy);
			double hz = (k == 0 ? -sz : sz);
			for (int i = 1; i < n - 1; i++) {
				quad(mat, color, side[k][0], side[k][i], side[k][i + 1], side[k][i + 1], hx, hy, hz);
			}
		}
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double mx = (side[0][i][0] + side[0][j][0] + side[1][i][0] + side[1][j][0]) / 4 - cx;
			double my = (side[0][i][1] + side[0][j][1] + side[1][i][1] + side[1][j][1]) / 4 - cy;
			double mz = (side[0][i][2] + side[0][j][2] + side[1][i][2] + side[1][j][2]) / 4 - cz;
			quad(mat, color, side[0][i], side[0][j], side[1][j], side[1][i], mx, my, mz);
		}
	}
}
