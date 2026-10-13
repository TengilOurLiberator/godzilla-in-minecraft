package dev.godzilla.client.destruction;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Comparison for the report: the same flying pieces drawn as client-only BlockDisplay entities (the vanilla way to draw a free
 * block), to measure against the batched drawing in {@link Debris}. Test code, started with the client command "benchent N".
 */
final class EntityBench {
	private static final List<Display.BlockDisplay> LIST = new ArrayList<>();
	private static float[] vx = new float[0], vy = new float[0], vz = new float[0];

	private EntityBench() {
	}

	static int count() {
		return LIST.size();
	}

	static void clear(ClientLevel level) {
		if (level != null) {
			for (Display.BlockDisplay e : LIST) {
				level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		LIST.clear();
	}

	static void start(Minecraft mc, int n) {
		ClientLevel level = mc.level;
		clear(level);
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		float yaw = mc.gameRenderer.mainCamera().yRot();
		float fx = (float) -Math.sin(Math.toRadians(yaw)), fz = (float) Math.cos(Math.toRadians(yaw));
		BlockState[] states = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState()};
		Random r = new Random(7);
		vx = new float[n];
		vy = new float[n];
		vz = new float[n];
		for (int i = 0; i < n; i++) {
			Display.BlockDisplay e = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.LOAD);
			if (e == null) {
				break;
			}
			float d = 14 + r.nextFloat() * 70;
			float side = (r.nextFloat() - 0.5f) * d * 1.1f;
			double x = cam.x + fx * d - fz * side, z = cam.z + fz * d + fx * side, y = cam.y + r.nextFloat() * 28 - 4;
			e.setId(1_000_000 + i);
			e.setBlockState(states[r.nextInt(states.length)]);
			float s = 0.5f + r.nextFloat() * 0.4f;
			e.setTransformation(new Transformation(new Vector3f(-s / 2, -s / 2, -s / 2), new Quaternionf().rotateXYZ(r.nextFloat() * 6, r.nextFloat() * 6, r.nextFloat() * 6), new Vector3f(s, s, s), new Quaternionf()));
			e.setViewRange(4f);
			e.setPos(x, y, z);
			vx[i] = (r.nextFloat() - 0.5f) * 0.1f;
			vy[i] = r.nextFloat() * 0.1f;
			vz[i] = (r.nextFloat() - 0.5f) * 0.1f;
			level.addEntity(e);
			LIST.add(e);
		}
	}

	static void tick() {
		for (int i = 0; i < LIST.size(); i++) {
			Display.BlockDisplay e = LIST.get(i);
			e.setOldPosAndRot();
			e.setPos(e.getX() + vx[i], e.getY() + vy[i], e.getZ() + vz[i]);
		}
	}
}
