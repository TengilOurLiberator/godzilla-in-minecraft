package dev.godzilla.client.destruction;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/** Writes the quads of one block, rotated and scaled, into a vertex consumer. Shared by flying pieces and falling masses. */
final class Draw {
	private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

	private Draw() {
	}

	/**
	 * @param c    centre of the block relative to the camera
	 * @param r    row-major 3x3 rotation (world = r * local)
	 * @param s    scale (1 = a full block)
	 * @param mask bit per Direction ordinal: faces that may be drawn (-1 = all)
	 * @return quads written
	 */
	static int emitBlock(VertexConsumer out, MeshCache.Mesh m, boolean translucent, float cx, float cy, float cz, float[] r, float s, int light, int mask) {
		int from = translucent ? m.opaque : 0;
		int to = translucent ? m.quads : m.opaque;
		float r0 = r[0] * s, r1 = r[1] * s, r2 = r[2] * s, r3 = r[3] * s, r4 = r[4] * s, r5 = r[5] * s, r6 = r[6] * s, r7 = r[7] * s, r8 = r[8] * s;
		float half = 0.5f * s;
		int written = 0;
		for (int q = from; q < to; q++) {
			int f = m.face[q];
			if (((mask >> f) & 1) == 0) {
				continue;
			}
			float dx = Debris.DIR_X[f], dy = Debris.DIR_Y[f], dz = Debris.DIR_Z[f];
			float nx = r[0] * dx + r[1] * dy + r[2] * dz;
			float ny = r[3] * dx + r[4] * dy + r[5] * dz;
			float nz = r[6] * dx + r[7] * dy + r[8] * dz;
			if (nx * cx + ny * cy + nz * cz + half >= 0) {
				continue;
			}
			float sh = (ny > 0 ? ny * ny : ny * ny * 0.5f) + nx * nx * 0.6f + nz * nz * 0.8f;
			int t = m.tint[q];
			int cr = (int) (((t >> 16) & 255) * sh), cg = (int) (((t >> 8) & 255) * sh), cb = (int) ((t & 255) * sh);
			int color = 0xFF000000 | (cr << 16) | (cg << 8) | cb;
			int lt = m.emission[q] == 0 ? light : LightCoordsUtil.lightCoordsWithEmission(light, m.emission[q]);
			int o = q * 20;
			float[] v = m.v;
			for (int i = 0; i < 4; i++, o += 5) {
				float lx = v[o], ly = v[o + 1], lz = v[o + 2];
				out.addVertex(cx + r0 * lx + r1 * ly + r2 * lz, cy + r3 * lx + r4 * ly + r5 * lz, cz + r6 * lx + r7 * ly + r8 * lz,
						color, v[o + 3], v[o + 4], OVERLAY, lt, nx, ny, nz);
			}
			written++;
		}
		return written;
	}
}
