package dev.godzilla.client.destruction;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.destruction.DestructionModule;
import dev.godzilla.destruction.Packets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Client-only initialisation of this module: debris, falling masses, dust, fire and explosions. */
public final class DestructionClient {
	static long frames, lastFrameNanos, frameNanosSum;
	static long simNanos, simTicks;
	static float fps;
	static boolean log = false;
	static long lastLogFrame;

	private DestructionClient() {
	}

	public static void init() {
		ParticleProviderRegistry.getInstance().register(DestructionModule.PUFF, sprites -> {
			FxClient.sprites = sprites;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
		ParticleProviderRegistry.getInstance().register(DestructionModule.SMOKE, sprites -> {
			FxClient.smokeSprites = sprites;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});
		ParticleProviderRegistry.getInstance().register(DestructionModule.BLAST, sprites -> {
			FxClient.blastSprites = sprites;
			return (options, level, x, y, z, xa, ya, za, random) -> null;
		});

		ClientPlayNetworking.registerGlobalReceiver(Packets.DebrisBurst.TYPE, (pk, ctx) -> {
			ClientLevel level = ctx.client().level;
			if (level == null) {
				return;
			}
			Vec3 cam = ctx.client().gameRenderer.mainCamera().position();
			double d = cam.distanceTo(new Vec3(pk.origin().getX(), pk.origin().getY(), pk.origin().getZ()));
			Debris.burst(level, pk.origin(), pk.ix(), pk.iy(), pk.iz(), pk.mode(), pk.power(), pk.rel(), pk.ids(), d);
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.Mass.TYPE, (pk, ctx) -> {
			if (ctx.client().level != null) {
				Masses.add(ctx.client().level, pk);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.Fx.TYPE, (pk, ctx) -> {
			ClientLevel level = ctx.client().level;
			if (level == null) {
				return;
			}
			switch (pk.kind()) {
				case Packets.Fx.EXPLOSION -> FxClient.explosion(level, pk.x(), pk.y(), pk.z(), pk.a(), pk.b() > 0.5f);
				case Packets.Fx.SMOKE_COLUMN -> FxClient.smokeColumn(pk.x(), pk.y(), pk.z(), pk.a(), pk.b());
				case Packets.Fx.DUST -> FxClient.puff(level, pk.x(), pk.y(), pk.z(), pk.a(), Math.max(1, pk.n()), 0.1f, 0.03f, 1f);
				case Packets.Fx.RESET -> resetAll();
				default -> {
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.Fire.TYPE, (pk, ctx) -> {
			switch (pk.op()) {
				case 0 -> FxClient.fireAdd(pk.id(), pk.x(), pk.y(), pk.z(), pk.radius(), pk.ttl());
				case 1 -> FxClient.fireRemove(pk.id());
				default -> FxClient.fireClear();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Packets.ClientCmd.TYPE, (pk, ctx) -> command(ctx.client(), pk.text()));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetAll());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ClientLevel level = mc.level;
			if (level == null || mc.isPaused() || !level.tickRateManager().runsNormally()) {
				return;
			}
			long t0 = System.nanoTime();
			FxClient.tick(level);
			EntityBench.tick();
			Debris.tick(level);
			Vec3 cam = mc.gameRenderer.mainCamera().position();
			Masses.tick(level, 0);
			simNanos += System.nanoTime() - t0;
			simTicks++;
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			long now = System.nanoTime();
			if (lastFrameNanos != 0) {
				long dt = now - lastFrameNanos;
				if (dt < 1_000_000_000L) {
					frameNanosSum += dt;
					frames++;
				}
			}
			lastFrameNanos = now;
			adapt();
			if (Debris.n == 0 && Masses.LIST.isEmpty()) {
				return;
			}
			var cs = ctx.levelState().cameraRenderState;
			Vec3 p = cs.pos;
			Vector3f fwd = new Vector3f(0, 0, -1).rotate(cs.orientation);
			float partial = ctx.levelState().worldPartialTicks;
			Debris.collect(p.x, p.y, p.z, fwd.x, fwd.y, fwd.z, partial);
			PoseStack ps = new PoseStack();
			Masses.drawNanos = 0;
			ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.cutoutMovingBlock(), (pose, buf) -> {
				Debris.emit(buf, false);
				Masses.emit(buf, false, partial);
			});
			if (Debris.nTrans > 0 || hasTranslucentMass()) {
				ctx.submitNodeCollector().submitCustomGeometry(new PoseStack(), RenderTypes.translucentMovingBlock(), (pose, buf) -> {
					Debris.emit(buf, true);
					Masses.emit(buf, true, partial);
				});
			}
		});
		GodzillaMod.LOGGER.info("godzilla: destruction client init done");
	}

	private static boolean hasTranslucentMass() {
		return !Masses.LIST.isEmpty();
	}

	static void resetAll() {
		Debris.clear();
		Masses.clear();
		FxClient.clear();
		MeshCache.clear();
	}

	private static long adaptFrames, adaptNanos;

	/** Keeps the pool at a size the machine draws at a good frame rate. */
	private static void adapt() {
		if (frames - adaptFrames < 45) {
			return;
		}
		double ms = (frameNanosSum - adaptNanos) / 1e6 / (frames - adaptFrames);
		fps = (float) (1000.0 / ms);
		adaptFrames = frames;
		adaptNanos = frameNanosSum;
		if (log && Debris.n + Masses.blocks() > 0) {
			GodzillaMod.LOGGER.info("GZ-DEBRIS {}", statsLine());
		}
		if (Debris.adaptive) {
			// headroom against the frame budget: 90 fps in VR (two eye passes), 75 fps on the desktop
			double budget = dev.godzilla.client.vr.ClientVr.isVrActive() ? 10.5 : 13.0;
			if (ms > budget) {
				Debris.maxLive = Math.max(3000, (int) (Debris.maxLive * 0.88));
				Debris.trim(Debris.n - Debris.maxLive);
			} else if (ms < budget * 0.62 && Debris.n > Debris.maxLive * 0.85) {
				Debris.maxLive = Math.min(Debris.HARD_MAX, (int) (Debris.maxLive * 1.12) + 200);
			}
		}
	}

	static String statsLine() {
		double sim = simTicks == 0 ? 0 : simNanos / 1e6 / simTicks;
		return "pieces=%d (cap %d%s) masses=%d blocks=%d fps=%.1f frame=%.2fms writer=%.2fms (quads %d) sim=%.2fms/tick spawned=%d refused=%d fires=%d puffs=%d"
				.formatted(Debris.n, Debris.maxLive, Debris.adaptive ? " adaptive" : "", Masses.LIST.size(), Masses.blocks(), fps,
						1000f / Math.max(1, fps), (Debris.drawNanos / 1e6) / Math.max(1, frames), Debris.drawnQuads + Masses.quads, sim, Debris.statSpawned,
						Debris.statDropped, FxClient.fireCount(), FxClient.statPuffs);
	}

	// ------------------------------------------------------------------------------------------------------------------
	// test commands sent from the server (/godzilla destruction client ...)

	private static void command(Minecraft mc, String text) {
		String[] t = text.trim().split("\\s+");
		String msg = null;
		try {
			switch (t[0]) {
				case "stats" -> {
					msg = statsLine();
					Debris.drawNanos = 0;
					frames = 0;
					frameNanosSum = 0;
					adaptFrames = 0;
					adaptNanos = 0;
					simNanos = 0;
					simTicks = 0;
				}
				case "max" -> {
					Debris.maxLive = Integer.parseInt(t[1]);
					msg = "max live pieces " + Debris.maxLive;
				}
				case "adaptive" -> {
					Debris.adaptive = t[1].equals("on");
					msg = "adaptive " + Debris.adaptive;
				}
				case "log" -> {
					log = t[1].equals("on");
					msg = "log " + log;
				}
				case "maxfps" -> {
					mc.options.framerateLimit().set(Integer.parseInt(t[1]));
					mc.options.enableVsync().set(false);
					msg = "max fps " + t[1];
				}
				case "benchent" -> {
					EntityBench.start(mc, Integer.parseInt(t[1]));
					msg = "benchent " + EntityBench.count();
				}
				case "clear" -> {
					EntityBench.clear(mc.level);
					resetAll();
					msg = "cleared";
				}
				case "cull" -> {
					Debris.cullDistance = Float.parseFloat(t[1]);
					msg = "cull distance " + Debris.cullDistance;
				}
				case "bench" -> {
					int n = Integer.parseInt(t[1]);
					String kind = t.length > 2 ? t[2] : "fall";
					bench(mc, n, kind);
					msg = "bench " + n + " " + kind;
				}
				default -> msg = "unknown: " + text;
			}
		} catch (Exception e) {
			msg = "bad command: " + text + " (" + e + ")";
		}
		GodzillaMod.LOGGER.info("GZ-DEBRIS-CMD {} -> {}", text, msg);
		if (mc.player != null && msg != null) {
			mc.player.sendSystemMessage(Component.literal(msg));
		}
	}

	/** Fills the air in front of the camera with n pieces: "fall" lets them tumble and bounce, "float" skips the world checks. */
	private static void bench(Minecraft mc, int n, String kind) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		float yaw = mc.gameRenderer.mainCamera().yRot();
		float fx = (float) -Math.sin(Math.toRadians(yaw)), fz = (float) Math.cos(Math.toRadians(yaw));
		int[] ids = new int[]{net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()),
				net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState()),
				net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState()),
				net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState())};
		int oldMax = Debris.maxLive;
		Debris.maxLive = Math.max(Debris.maxLive, Debris.n + n);
		java.util.Random r = new java.util.Random(7);
		for (int i = 0; i < n; i++) {
			float d = 14 + r.nextFloat() * 70;
			float side = (r.nextFloat() - 0.5f) * d * 1.1f;
			float x = (float) cam.x + fx * d - fz * side, z = (float) cam.z + fz * d + fx * side;
			float y = (float) cam.y + r.nextFloat() * 28 - 4;
			Debris.spawn(x, y, z, (r.nextFloat() - 0.5f) * 0.1f, r.nextFloat() * 0.1f, (r.nextFloat() - 0.5f) * 0.1f, ids[r.nextInt(ids.length)],
					0.5f + r.nextFloat() * 0.4f, 15728880, 20000, kind.equals("float") ? 127 : 0);
		}
		Debris.floatMode = kind.equals("float");
		Debris.maxLive = Math.max(oldMax, Debris.n);
	}
}
