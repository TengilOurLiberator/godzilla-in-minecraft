package dev.godzilla.client.destruction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The real baked quads of every block state that becomes debris, flattened into arrays and centred on the block, so the pieces
 * look exactly like the block with the active resource pack (Patrix) and can be drawn by one tight loop.
 */
final class MeshCache {
	/** Quads are stored opaque/cutout first, then translucent. Per quad: 4 vertices of (x, y, z, u, v). */
	static final class Mesh {
		int quads, opaque;
		float[] v = new float[0];      // 20 floats per quad
		byte[] face = new byte[0];     // Direction ordinal of each quad
		int[] tint = new int[0];       // ARGB multiplier of each quad (tint colour or white)
		int[] emission = new int[0];
		boolean empty() {
			return quads == 0;
		}
	}

	private static final Mesh EMPTY = new Mesh();
	private static Mesh[] meshes = new Mesh[0];
	private static int generation = -1;

	private MeshCache() {
	}

	static void clear() {
		meshes = new Mesh[0];
	}

	static Mesh get(int stateId) {
		if (stateId >= meshes.length) {
			Mesh[] n = new Mesh[Math.max(stateId + 1, Block.BLOCK_STATE_REGISTRY.size() + 16)];
			System.arraycopy(meshes, 0, n, 0, meshes.length);
			meshes = n;
		}
		Mesh m = meshes[stateId];
		if (m == null) {
			m = build(Block.stateById(stateId));
			meshes[stateId] = m;
		}
		return m;
	}

	private static Mesh build(BlockState state) {
		if (state.getRenderShape() != RenderShape.MODEL) {
			return EMPTY;
		}
		Minecraft mc = Minecraft.getInstance();
		BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42), parts);
		List<BakedQuad> opaque = new ArrayList<>(), trans = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (Direction d : Direction.values()) {
				for (BakedQuad q : part.getQuads(d)) {
					(q.materialInfo().layer().translucent() ? trans : opaque).add(q);
				}
			}
			for (BakedQuad q : part.getQuads(null)) {
				(q.materialInfo().layer().translucent() ? trans : opaque).add(q);
			}
		}
		Mesh m = new Mesh();
		m.opaque = opaque.size();
		m.quads = opaque.size() + trans.size();
		if (m.quads == 0) {
			return EMPTY;
		}
		m.v = new float[m.quads * 20];
		m.face = new byte[m.quads];
		m.tint = new int[m.quads];
		m.emission = new int[m.quads];
		int[] tintColors = new int[4];
		java.util.Arrays.fill(tintColors, -1);
		var sources = mc.getBlockColors().getTintSources(state);
		for (int i = 0; i < sources.size() && i < 4; i++) {
			var src = sources.get(i);
			if (src != null) {
				try {
					tintColors[i] = 0xFF000000 | src.color(state);
				} catch (Throwable t) {
					tintColors[i] = -1;
				}
			}
		}
		int k = 0;
		for (List<BakedQuad> list : List.of(opaque, trans)) {
			for (BakedQuad q : list) {
				for (int i = 0; i < 4; i++) {
					Vector3fc p = q.position(i);
					long uv = q.packedUV(i);
					int o = k * 20 + i * 5;
					m.v[o] = p.x() - 0.5f;
					m.v[o + 1] = p.y() - 0.5f;
					m.v[o + 2] = p.z() - 0.5f;
					m.v[o + 3] = UVPair.unpackU(uv);
					m.v[o + 4] = UVPair.unpackV(uv);
				}
				m.face[k] = (byte) q.direction().ordinal();
				int ti = q.materialInfo().tintIndex();
				m.tint[k] = ti >= 0 && ti < 4 ? tintColors[ti] : -1;
				m.emission[k] = q.materialInfo().lightEmission();
				k++;
			}
		}
		return m;
	}
}
