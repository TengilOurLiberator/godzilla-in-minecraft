package dev.godzilla.client.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.godzilla.client.player.vr.PlayerVr;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.player.BodyMotion;
import dev.godzilla.player.GodzillaGeometry;
import dev.godzilla.player.PlayerConfig;
import dev.godzilla.player.net.PlayerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

/** Draws the Godzilla body, its shadow and the atomic beam in place of the vanilla player (called from EntityRenderDispatcherMixin). */
public final class GodzillaRenderer {
	private static final Identifier[] TEX = new Identifier[Mesh.COUNT];
	private static final Identifier BEAM_TEX = GodzillaMod.id("textures/player/beam.png");
	private static final Identifier SHADOW_TEX = Identifier.withDefaultNamespace("textures/misc/shadow.png");

	static {
		String[] names = {"skin", "belly", "plate", "bone", "mouth", "glow", "eye"};
		for (int i = 0; i < names.length; i++) {
			TEX[i] = GodzillaMod.id("textures/player/" + names[i] + ".png");
		}
	}

	private GodzillaRenderer() {
	}

	private static RenderType typeOf(int mat) {
		return switch (mat) {
			case Mesh.GLOW, Mesh.EYE -> RenderTypes.entityTranslucentEmissive(TEX[mat]);
			default -> RenderTypes.entityCutout(TEX[mat]);
		};
	}

	/** x, y, z: where the entity origin (the feet) is relative to the camera. */
	public static void submit(AvatarRenderState state, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		Entity entity = level == null ? null : level.getEntity(state.id);
		if (entity == null) {
			return;
		}
		Visual v = PlayerClient.visual(state.id);
		if (v == null) {
			return;
		}
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double scale = state.scale > 0 ? state.scale : PlayerConfig.SCALE;
		double s = GodzillaGeometry.scaleOf(scale);
		Vec3 feet = new Vec3(state.x, state.y, state.z);
		Vec3 cam = new Vec3(-x, -y, -z); // camera relative to the feet
		double yawDeg = v.motion.renderYaw(pt);
		double yaw = Math.toRadians(yawDeg);
		boolean local = v.local;

		// head and hands in world space
		Vec3 head;
		Vec3 headDir;
		Vec3[] handPos = new Vec3[2];
		Vec3[] handDir = new Vec3[2];
		Vec3[] handUp = new Vec3[2];
		PlayerVr.Frame frame = local ? PlayerVr.renderFrame() : null;
		PlayerNet.StateS2C st = v.state;
		boolean haveVr = false;
		if (frame != null) {
			head = frame.head();
			headDir = frame.headDir();
			handPos[0] = frame.right();
			handDir[0] = frame.rightDir();
			handUp[0] = frame.rightUp();
			handPos[1] = frame.left();
			handDir[1] = frame.leftDir();
			handUp[1] = frame.leftUp();
			haveVr = true;
		} else if (st != null && st.has(PlayerNet.StateS2C.VR) && !local) {
			head = st.headPos();
			headDir = st.headDir();
			handPos[0] = st.rightHand();
			handDir[0] = st.rightDir();
			handPos[1] = st.leftHand();
			handDir[1] = st.leftDir();
			haveVr = true;
		} else {
			head = entity.getEyePosition(pt);
			headDir = entity.getViewVector(pt);
		}
		if (!haveVr) {
			float idle = v.age + pt;
			for (int h = 0; h < 2; h++) {
				int sw = h == 1 ? v.swipeLeft : v.swipeRight;
				float st2 = sw < 0 ? -1 : sw + Math.min(v.ticksSinceState, 1) * 0 + pt;
				handPos[h] = GodzillaGeometry.flatHand(feet, yawDeg, s, h == 1, st2, idle);
				Vec3 sh = GodzillaGeometry.shoulder(feet, yawDeg, s, h == 1);
				handDir[h] = handPos[h].subtract(sh).normalize();
			}
		}
		for (int h = 0; h < 2; h++) {
			if (handUp[h] == null) {
				handUp[h] = new Vec3(0, 1, 0);
			}
		}

		// build the model in body-local space
		GodzillaModel.Params p = new GodzillaModel.Params();
		double phase = v.motion.renderPhase(pt);
		p.phase = phase;
		p.amp = Mth.clamp(v.motion.speed / 0.22, 0, 1);
		p.mouth = Mth.lerp(pt, v.mouthPrev, v.mouth);
		p.glow = Mth.lerp(pt, v.glowPrev, v.glow);
		p.flicker = 0.82F + 0.18F * (float) Math.sin((v.age + pt) * 1.7) + 0.06F * (float) Math.sin((v.age + pt) * 4.3);
		p.age = v.age + pt;
		Vec3 headLocal = toLocal(head, feet, yaw, s);
		double headYaw = GodzillaGeometry.yawOf(headDir);
		p.head = new Vec3(Mth.clamp(headLocal.x, -8, 8), Mth.clamp(headLocal.y, 18, 34), Mth.clamp(headLocal.z, -8, 9));
		p.headYawRel = Mth.clamp(Math.toRadians(Mth.wrapDegrees(headYaw - yawDeg)), -1.7, 1.7);
		p.headPitch = Math.asin(Mth.clamp(-headDir.normalize().y, -1, 1));
		for (int h = 0; h < 2; h++) {
			p.hand[h] = toLocal(handPos[h], feet, yaw, s);
			p.handDir[h] = rotLocal(handDir[h], yaw).normalize();
			p.handUp[h] = rotLocal(handUp[h], yaw).normalize();
		}
		float[] segYaw = new float[GodzillaGeometry.TAIL_SEGMENTS];
		for (int i = 0; i < segYaw.length; i++) {
			segYaw[i] = Mth.lerp(pt, v.motion.tailYawPrev[i], v.motion.tailYaw[i]);
		}
		Vec3[] tailWorld = BodyMotion.tailJoints(Vec3.ZERO, yawDeg, 1.0, segYaw);
		Vec3[] tailLocal = new Vec3[tailWorld.length];
		for (int i = 0; i < tailWorld.length; i++) {
			// tailJoints is in world axes at scale 1 relative to feet with the root at the body: convert to local axes
			Vec3 w = tailWorld[i];
			tailLocal[i] = new Vec3(w.x * Math.cos(yaw) + w.z * Math.sin(yaw), w.y, -w.x * Math.sin(yaw) + w.z * Math.cos(yaw));
		}
		p.tail = tailLocal;
		double camDist = cam.subtract(head.subtract(feet)).length();
		p.firstPerson = camDist < 3.0 * s;

		Mesh mesh = new Mesh();
		mesh.cur = new Matrix4f().rotateY((float) -yaw).scale((float) s);
		GodzillaModel.build(mesh, p);

		int light = LightCoordsUtil.max(state.lightCoords, LightCoordsUtil.pack(2, 11));
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		for (int mat = 0; mat < Mesh.COUNT; mat++) {
			Mesh.Buf buf = mesh.bufs[mat];
			if (buf.n == 0) {
				continue;
			}
			boolean emissive = mat == Mesh.GLOW || mat == Mesh.EYE;
			int lt = emissive ? LightCoordsUtil.FULL_BRIGHT : light;
			collector.submitCustomGeometry(poseStack, typeOf(mat), (pose, vc) -> {
				for (int i = 0; i < buf.n; i++) {
					int o = i * 9;
					vc.addVertex(pose, buf.f[o], buf.f[o + 1], buf.f[o + 2]).setColor(buf.c[i]).setUv(buf.f[o + 6], buf.f[o + 7])
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(lt).setNormal(pose, buf.f[o + 3], buf.f[o + 4], buf.f[o + 5]);
				}
			});
		}

		shadows(level, collector, poseStack, feet, yawDeg, s, head, handPos, tailWorldScaled(feet, yawDeg, s, segYaw), phase, p.amp, light);
		boolean firing = st != null && st.has(PlayerNet.StateS2C.FIRING);
		boolean charging = st != null && st.has(PlayerNet.StateS2C.CHARGING);
		if (firing || charging) {
			beam(level, collector, poseStack, v, st, feet, cam, head, headDir, s, p.firstPerson, local, firing, charging);
		}
		poseStack.popPose();
	}

	private static Vec3[] tailWorldScaled(Vec3 feet, double yawDeg, double s, float[] segYaw) {
		return BodyMotion.tailJoints(feet, yawDeg, s, segYaw);
	}

	private static Vec3 toLocal(Vec3 world, Vec3 feet, double yaw, double s) {
		double dx = world.x - feet.x;
		double dz = world.z - feet.z;
		return new Vec3((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / s, (world.y - feet.y) / s, (-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / s);
	}

	private static Vec3 rotLocal(Vec3 dir, double yaw) {
		return new Vec3(dir.x * Math.cos(yaw) + dir.z * Math.sin(yaw), dir.y, -dir.x * Math.sin(yaw) + dir.z * Math.cos(yaw));
	}

	// ------------------------------------------------------------------ shadow

	private static double groundBelow(ClientLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(y);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = top; by > top - 70 && by > level.getMinY(); by--) {
			pos.set(bx, by, bz);
			BlockState st = level.getBlockState(pos);
			if (!st.getCollisionShape(level, pos).isEmpty()) {
				return by + st.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
			}
			if (!st.getFluidState().isEmpty()) {
				return by + st.getFluidState().getHeight(level, pos);
			}
		}
		return Double.NaN;
	}

	private static void shadows(ClientLevel level, SubmitNodeCollector collector, PoseStack poseStack, Vec3 feet, double yawDeg, double s,
								Vec3 head, Vec3[] hands, Vec3[] tail, double phase, double amp, int light) {
		if (level == null) {
			return;
		}
		java.util.List<double[]> blobs = new java.util.ArrayList<>();
		// x, y(source height), z, radius, alpha
		Vec3 torso = GodzillaGeometry.toWorld(feet, yawDeg, s, 0, 12, 0);
		blobs.add(new double[]{torso.x, torso.y, torso.z, 8.5 * s, 0.55});
		double sw = Math.sin(Math.PI * phase) * 0.55 * amp * 10.0;
		for (int side = -1; side <= 1; side += 2) {
			double fz = (side > 0 ? -sw : sw) + 1.6;
			Vec3 f = GodzillaGeometry.toWorld(feet, yawDeg, s, side * 3.0, 0.5, fz);
			blobs.add(new double[]{f.x, f.y + 1, f.z, 4.2 * s, 0.6});
		}
		blobs.add(new double[]{head.x, head.y, head.z, 3.6 * s, 0.4});
		for (Vec3 h : hands) {
			blobs.add(new double[]{h.x, h.y, h.z, 2.8 * s, 0.4});
		}
		for (int i = 2; i < tail.length; i++) {
			blobs.add(new double[]{tail[i].x, tail[i].y, tail[i].z, GodzillaGeometry.TAIL_THICK[Math.min(i, 7)] * 0.9 * s, 0.45});
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityShadow(SHADOW_TEX), (pose, vc) -> {
			for (double[] b : blobs) {
				double g = groundBelow(level, b[0], b[1], b[2]);
				if (Double.isNaN(g)) {
					continue;
				}
				double h = Math.max(0, b[1] - g);
				float alpha = (float) (b[4] * Mth.clamp(1.0 - h / (70.0 * s), 0.2, 1.0));
				double r = b[3] * (1.0 + h * 0.01);
				float ox = (float) (b[0] - feet.x);
				float oy = (float) (g - feet.y + 0.03);
				float oz = (float) (b[2] - feet.z);
				int c = ((int) (alpha * 255) << 24);
				shadowVertex(vc, pose, ox - (float) r, oy, oz - (float) r, 0, 0, c, light);
				shadowVertex(vc, pose, ox - (float) r, oy, oz + (float) r, 0, 1, c, light);
				shadowVertex(vc, pose, ox + (float) r, oy, oz + (float) r, 1, 1, c, light);
				shadowVertex(vc, pose, ox + (float) r, oy, oz - (float) r, 1, 0, c, light);
			}
		});
	}

	private static void shadowVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
		vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
	}

	// ------------------------------------------------------------------ atomic beam

	private static void beam(ClientLevel level, SubmitNodeCollector collector, PoseStack poseStack, Visual v, PlayerNet.StateS2C st,
							 Vec3 feet, Vec3 cam, Vec3 head, Vec3 headDir, double s, boolean firstPerson, boolean local, boolean firing, boolean charging) {
		Vec3 dir = headDir.normalize();
		Vec3 mouthW = GodzillaGeometry.mouth(head, dir, s);
		Vec3 mouth = mouthW.subtract(feet);
		float age = v.age + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double R = 1.9 * Math.max(0.35, s);
		if (charging && !firing) {
			float ch = st.charge();
			double r = R * (0.5 + 2.2 * ch);
			disc(collector, poseStack, mouth, cam, r, 0.9F * ch, 0.55F, 0.8F, 1.0F);
			disc(collector, poseStack, mouth, cam, r * 0.5, ch, 1.0F, 1.0F, 1.0F);
			return;
		}
		double range = PlayerConfig.BREATH_RANGE * s;
		double len = v.beamLen > 0 ? v.beamLen : st.beamLength();
		if (local) {
			BlockHitResult hit = level.clip(new ClipContext(mouthW, mouthW.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			len = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().subtract(mouthW).length();
			len = Math.min(len, Math.max(len, 1));
		}
		if (len < 1) {
			return;
		}
		Vec3 end = mouth.add(dir.scale(len));
		final int n = 18;
		double[] ds = new double[n + 1];
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			ds[i] = len * t * t * 0.55 + len * t * 0.45; // denser near the mouth
		}
		float fl = 0.85F + 0.15F * (float) Math.sin(age * 2.1);
		final double Rr = R;
		final Vec3 mouthF = mouth;
		final double lenF = len;
		layer(collector, poseStack, mouthF, dir, cam, ds, Rr * 1.0, 0.0, 0.45F * fl, 0.45F, 0.7F, 1.0F, firstPerson, age);
		layer(collector, poseStack, mouthF, dir, cam, ds, Rr * 0.62, 0.0, 0.75F, 0.65F, 0.88F, 1.0F, firstPerson, age);
		layer(collector, poseStack, mouthF, dir, cam, ds, Rr * 0.3, 0.0, 1.0F, 1.0F, 1.0F, 1.0F, firstPerson, age);
		disc(collector, poseStack, mouthF, cam, Rr * 1.9, 0.8F, 0.6F, 0.85F, 1.0F);
		disc(collector, poseStack, mouthF, cam, Rr * 0.9, 1.0F, 1.0F, 1.0F, 1.0F);
		if (lenF < range - 1) {
			// impact flash and sparks
			double fr = Rr * (3.0 + 0.8 * Math.sin(age * 1.9));
			disc(collector, poseStack, end, cam, fr, 0.7F * fl, 0.55F, 0.8F, 1.0F);
			disc(collector, poseStack, end, cam, fr * 0.5, 0.95F, 1.0F, 1.0F, 1.0F);
			sparks(collector, poseStack, end, dir, cam, Rr, v.beamTicks);
		}
	}

	private static void layer(SubmitNodeCollector collector, PoseStack poseStack, Vec3 mouth, Vec3 dir, Vec3 cam, double[] ds,
							  double radius, double unused, float alpha, float r, float g, float b, boolean firstPerson, float age) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(BEAM_TEX), (pose, vc) -> {
			int n = ds.length;
			Vec3[] side = new Vec3[n];
			double[] w = new double[n];
			float[] a = new float[n];
			Vec3[] pts = new Vec3[n];
			for (int i = 0; i < n; i++) {
				Vec3 pt = mouth.add(dir.scale(ds[i]));
				pts[i] = pt;
				Vec3 toCam = cam.subtract(pt);
				Vec3 sd = dir.cross(toCam);
				if (sd.lengthSqr() < 1e-6) {
					sd = dir.cross(new Vec3(0, 1, 0));
				}
				side[i] = sd.normalize();
				double grow = Mth.clamp(ds[i] / (radius * 7.0 + 6.0), 0, 1);
				w[i] = radius * (0.3 + 0.7 * grow) * (1.0 + 0.08 * Math.sin(age * 1.3 + ds[i] * 0.35));
				double near = firstPerson ? Mth.clamp((ds[i] - 2.0) / 14.0, 0.12, 1.0) : 1.0;
				a[i] = (float) (alpha * near);
			}
			for (int i = 0; i + 1 < n; i++) {
				double v0 = ds[i] * 0.05 - age * 0.9;
				double v1 = ds[i + 1] * 0.05 - age * 0.9;
				beamVertex(vc, pose, pts[i].add(side[i].scale(-w[i])), 0, (float) v0, r, g, b, a[i]);
				beamVertex(vc, pose, pts[i].add(side[i].scale(w[i])), 1, (float) v0, r, g, b, a[i]);
				beamVertex(vc, pose, pts[i + 1].add(side[i + 1].scale(w[i + 1])), 1, (float) v1, r, g, b, a[i + 1]);
				beamVertex(vc, pose, pts[i + 1].add(side[i + 1].scale(-w[i + 1])), 0, (float) v1, r, g, b, a[i + 1]);
			}
		});
	}

	private static void beamVertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, float r, float g, float b, float a) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
	}

	/** A camera-facing glowing disc. */
	private static void disc(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, Vec3 cam, double radius, float alpha, float r, float g, float b) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(SHADOW_TEX_GLOW), (pose, vc) -> {
			Vec3 f = cam.subtract(at).normalize();
			Vec3 right = f.cross(new Vec3(0, 1, 0));
			if (right.lengthSqr() < 1e-6) {
				right = new Vec3(1, 0, 0);
			}
			right = right.normalize().scale(radius);
			Vec3 up = f.cross(right).normalize().scale(radius);
			Vec3 c0 = at.subtract(right).subtract(up);
			Vec3 c1 = at.add(right).subtract(up);
			Vec3 c2 = at.add(right).add(up);
			Vec3 c3 = at.subtract(right).add(up);
			beamVertex(vc, pose, c0, 0, 0, r, g, b, alpha);
			beamVertex(vc, pose, c1, 1, 0, r, g, b, alpha);
			beamVertex(vc, pose, c2, 1, 1, r, g, b, alpha);
			beamVertex(vc, pose, c3, 0, 1, r, g, b, alpha);
		});
	}

	private static final Identifier SHADOW_TEX_GLOW = GodzillaMod.id("textures/player/halo.png");

	private static void sparks(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, Vec3 dir, Vec3 cam, double R, int tick) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(SHADOW_TEX_GLOW), (pose, vc) -> {
			java.util.Random rnd = new java.util.Random(tick * 7919L);
			for (int i = 0; i < 26; i++) {
				double life = rnd.nextDouble();
				Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.8 + 0.3, rnd.nextGaussian()).normalize();
				d = d.add(dir.scale(-0.9)).normalize();
				Vec3 p = at.add(d.scale(R * (1.0 + life * 6.0)));
				p = p.add(0, -life * life * R * 2.0, 0);
				double size = R * (0.22 + 0.2 * rnd.nextDouble()) * (1.0 - life * 0.7);
				Vec3 f = cam.subtract(p).normalize();
				Vec3 right = f.cross(new Vec3(0, 1, 0));
				if (right.lengthSqr() < 1e-6) {
					continue;
				}
				right = right.normalize().scale(size);
				Vec3 up = f.cross(right).normalize().scale(size);
				float a = (float) (1.0 - life);
				beamVertex(vc, pose, p.subtract(right).subtract(up), 0, 0, 1.0F, 0.95F, 0.7F, a);
				beamVertex(vc, pose, p.add(right).subtract(up), 1, 0, 1.0F, 0.95F, 0.7F, a);
				beamVertex(vc, pose, p.add(right).add(up), 1, 1, 1.0F, 0.95F, 0.7F, a);
				beamVertex(vc, pose, p.subtract(right).add(up), 0, 1, 1.0F, 0.95F, 0.7F, a);
			}
		});
	}
}
