package dev.godzilla.client.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.godzilla.client.player.vr.PlayerVr;
import dev.godzilla.client.vr.ClientVr;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.GodzillaSounds;
import dev.godzilla.player.GodzillaGeometry;
import dev.godzilla.player.GodzillaState;
import dev.godzilla.player.PlayerConfig;
import dev.godzilla.player.net.PlayerNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client side of being Godzilla: state of every Godzilla body for the renderer, input to the server, camera shake and
 * controller rumble, sound loops, the scripted rise, debug drivers, HUD hiding. The renderer is {@link GodzillaRenderer}.
 */
public final class PlayerClient {
	private static final Map<Integer, Visual> VISUALS = new HashMap<>();
	private static KeyMapping swipeLeftKey;
	private static KeyMapping swipeRightKey;

	// shake and flash
	private static float shakeStrength;
	private static int shakeTicks;
	private static int shakeTotal = 1;
	private static float flashStrength;
	private static int flashTicks;
	private static Vec3 flashFrom = Vec3.ZERO;

	// debug drivers
	private static int walkTicks;
	private static int walkDir = 1;
	private static int turnTicks;

	// rise
	private static boolean riseActive;
	private static Vec3 riseStart = Vec3.ZERO;
	private static float riseYaw;
	private static int riseTick;

	// sounds
	private static @Nullable LoopSound underwater;
	private static @Nullable LoopSound beamLoop;
	private static @Nullable LoopSound impactLoop;
	private static int roarCooldown;
	private static int hapticClock;
	private static int prevRoarTicks;

	private PlayerClient() {
	}

	static @Nullable Visual visual(int entityId) {
		return VISUALS.get(entityId);
	}

	/** True when the local player is Godzilla (used by the mixins). */
	public static boolean localIsGodzilla() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && GodzillaState.isGodzilla(mc.player);
	}

	public static boolean isGodzillaId(int entityId) {
		return VISUALS.containsKey(entityId);
	}

	public static void init() {
		PlayerVr.init();
		KeyMapping.Category cat = KeyMapping.Category.register(GodzillaMod.id("player"));
		swipeLeftKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.godzilla.swipe_left", InputConstants.KEY_Z, cat));
		swipeRightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.godzilla.swipe_right", InputConstants.KEY_X, cat));

		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.StateS2C.TYPE, (payload, ctx) -> onState(ctx.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.FxS2C.TYPE, (payload, ctx) -> {
			if (payload.strength() >= shakeStrength * remaining() || shakeTicks == 0) {
				shakeStrength = payload.strength();
				shakeTicks = payload.ticks();
				shakeTotal = Math.max(1, payload.ticks());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.FlashS2C.TYPE, (payload, ctx) -> {
			flashStrength = payload.strength();
			flashFrom = payload.from();
			flashTicks = 12;
			PlayerVr.haptic(true, 0.12F, 160, payload.strength());
			PlayerVr.haptic(false, 0.12F, 160, payload.strength());
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.HapticS2C.TYPE, (payload, ctx) -> {
			if (payload.hand() != 1) {
				PlayerVr.haptic(true, payload.seconds(), 160, payload.strength());
			}
			if (payload.hand() != 0) {
				PlayerVr.haptic(false, payload.seconds(), 160, payload.strength());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.DebugS2C.TYPE, (payload, ctx) -> {
			Minecraft mc = ctx.client();
			switch (payload.kind()) {
				case 0 -> mc.options.setCameraType(switch (payload.value()) {
					case 1 -> CameraType.THIRD_PERSON_BACK;
					case 2 -> CameraType.THIRD_PERSON_FRONT;
					default -> CameraType.FIRST_PERSON;
				});
				case 1 -> {
					walkTicks = payload.value();
					walkDir = 1;
				}
				case 2 -> {
					walkTicks = payload.value();
					walkDir = -1;
				}
				case 3 -> turnTicks = payload.value();
				case 4 -> GodzillaMod.LOGGER.info("GODZILLA-PLAYER fps={} visuals={} breath={} rendererCpuMs={}", mc.getFps(), VISUALS.size(),
						BreathGauge.state(), "%.3f".formatted(GodzillaRenderer.avgMs));
				default -> {
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PlayerNet.RiseS2C.TYPE, (payload, ctx) -> {
			// the server repeats this every second while rising; only a start or a real drift changes the clock
			if (payload.active() && riseActive && Math.abs(riseTick - payload.tick0()) <= 4) {
				return;
			}
			riseActive = payload.active();
			riseStart = payload.start();
			riseYaw = payload.yaw();
			riseTick = payload.tick0();
			if (!riseActive) {
				stopUnderwater();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(PlayerClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		hideVanillaHud();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, GodzillaMod.id("player_flash"), PlayerClient::drawFlash);
	}

	private static void reset() {
		VISUALS.clear();
		BreathGauge.active = false;
		BreathGauge.state = BreathGauge.State.IDLE;
		BreathGauge.value = 1.0F;
		BreathGauge.charge = 0;
		riseActive = false;
		shakeTicks = 0;
		stopUnderwater();
		stopLoops();
	}

	private static float remaining() {
		return shakeTicks <= 0 ? 0 : (float) shakeTicks / shakeTotal;
	}

	// ------------------------------------------------------------------ state from the server

	private static void onState(Minecraft mc, PlayerNet.StateS2C s) {
		if (s.has(PlayerNet.StateS2C.ENDED)) {
			Visual v = VISUALS.remove(s.entityId());
			if (mc.player != null && s.entityId() == mc.player.getId()) {
				reset();
			}
			return;
		}
		boolean local = mc.player != null && s.entityId() == mc.player.getId();
		Visual v = VISUALS.computeIfAbsent(s.entityId(), id -> new Visual(id, local));
		v.state = s;
		v.ticksSinceState = 0;
		if (local) {
			BreathGauge.active = true;
			BreathGauge.value = s.gauge();
			BreathGauge.charge = s.charge();
			BreathGauge.state = s.has(PlayerNet.StateS2C.FIRING) ? BreathGauge.State.FIRING
					: s.has(PlayerNet.StateS2C.CHARGING) ? BreathGauge.State.CHARGING
					: s.has(PlayerNet.StateS2C.LOCKOUT) ? BreathGauge.State.LOCKOUT : BreathGauge.State.IDLE;
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer me = mc.player;
		if (level == null || me == null) {
			if (!VISUALS.isEmpty()) {
				reset();
			}
			return;
		}
		// advance every Godzilla body
		Iterator<Map.Entry<Integer, Visual>> it = VISUALS.entrySet().iterator();
		while (it.hasNext()) {
			Visual v = it.next().getValue();
			if (!(level.getEntity(v.entityId) instanceof AbstractClientPlayer p) || !GodzillaState.isGodzilla(p)) {
				if (level.getEntity(v.entityId) == null || v.state == null) {
					// not loaded yet: keep waiting for a few ticks only
					if (v.age > 100) {
						it.remove();
					}
					v.age++;
				}
				continue;
			}
			float headYaw;
			PlayerVr.Frame frame = v.local ? PlayerVr.tickFrame() : null;
			if (frame != null) {
				headYaw = GodzillaGeometry.yawOf(frame.headDir());
			} else if (!v.local && v.state != null) {
				headYaw = GodzillaGeometry.yawOf(v.state.headDir());
			} else {
				headYaw = p.getYHeadRot();
			}
			v.tick(p, headYaw, riseActive && v.local);
		}
		Visual mine = VISUALS.get(me.getId());
		boolean godzilla = mine != null && GodzillaState.isGodzilla(me);
		BreathGauge.active = godzilla;
		if (godzilla) {
			localTick(mc, me, mine);
		} else {
			stopUnderwater();
		}
		for (Visual v : VISUALS.values()) {
			sounds(mc, v);
		}
		if (shakeTicks > 0) {
			shakeTicks--;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		if (roarCooldown > 0) {
			roarCooldown--;
		}
	}

	private static void localTick(Minecraft mc, LocalPlayer me, Visual v) {
		// input to the server
		boolean left = false;
		boolean right = false;
		while (swipeLeftKey.consumeClick()) {
			left = true;
		}
		while (swipeRightKey.consumeClick()) {
			right = true;
		}
		boolean attack = mc.options.keyAttack.isDown();
		boolean use = mc.options.keyUse.isDown();
		ClientPlayNetworking.send(new PlayerNet.InputC2S(attack, use, left, right));

		// debug drivers (RCON commands): hold the forward key, turn
		if (walkTicks > 0) {
			walkTicks--;
			(walkDir > 0 ? mc.options.keyUp : mc.options.keyDown).setDown(true);
			if (walkTicks == 0) {
				mc.options.keyUp.setDown(false);
				mc.options.keyDown.setDown(false);
			}
		}
		if (turnTicks > 0) {
			turnTicks--;
			me.setYRot(me.getYRot() + 4.0F);
		}

		if (v.state != null && v.state.roarTicks() > prevRoarTicks) {
			roarCooldown = PlayerConfig.ROAR_COOLDOWN;
		}
		prevRoarTicks = v.state == null ? 0 : v.state.roarTicks();
		BreathGauge.roarReady = roarCooldown == 0;

		if (riseActive) {
			riseTick++;
			RisePath.apply(me, riseStart, riseYaw, riseTick);
		}
		underwaterSound(mc, me);
		rumble(me, v);
	}

	private static void underwaterSound(Minecraft mc, LocalPlayer me) {
		ClientLevel level = mc.level;
		boolean under = level.getFluidState(BlockPos.containing(me.getEyePosition())).is(FluidTags.WATER);
		if (under && underwater == null) {
			underwater = new LoopSound(GodzillaSounds.GODZILLA_UNDERWATER_LOOP, SoundSource.AMBIENT, true, () -> Vec3.ZERO, () -> 1.0F);
			mc.getSoundManager().play(underwater);
		} else if (!under && underwater != null) {
			stopUnderwater();
		}
	}

	private static void stopUnderwater() {
		if (underwater != null) {
			underwater.end();
			underwater = null;
		}
	}

	private static void stopLoops() {
		if (beamLoop != null) {
			beamLoop.end();
			beamLoop = null;
		}
		if (impactLoop != null) {
			impactLoop.end();
			impactLoop = null;
		}
	}

	/** Beam sound loops for whoever is firing (positional; they follow the mouth and the impact point). */
	private static void sounds(Minecraft mc, Visual v) {
		boolean firing = v.state != null && v.state.has(PlayerNet.StateS2C.FIRING);
		if (firing && (v.local || beamLoop == null)) {
			if (beamLoop == null) {
				final Visual vv = v;
				beamLoop = new LoopSound(GodzillaSounds.BREATH_LOOP, SoundSource.HOSTILE, false, () -> mouthOf(vv), () -> 1.0F);
				mc.getSoundManager().play(beamLoop);
				impactLoop = new LoopSound(GodzillaSounds.BREATH_IMPACT_LOOP, SoundSource.HOSTILE, false, () -> impactOf(vv), () -> impactOf(vv) == null ? 0.0F : 1.0F);
				mc.getSoundManager().play(impactLoop);
			}
		} else if (!firing && v.local && beamLoop != null) {
			stopLoops();
		}
		if (firing && v.state != null && mc.level != null) {
			// sparks and smoke at the impact, and a little fire
			Vec3 imp = impactOf(v);
			if (imp != null && v.beamTicks % 2 == 0) {
				var rnd = mc.level.getRandom();
				for (int i = 0; i < 3; i++) {
					mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, imp.x + rnd.nextGaussian() * 1.5, imp.y + rnd.nextDouble() * 1.5,
							imp.z + rnd.nextGaussian() * 1.5, rnd.nextGaussian() * 0.6, 0.5 + rnd.nextDouble(), rnd.nextGaussian() * 0.6);
				}
				mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, imp.x, imp.y + 1, imp.z, 0, 0.3, 0);
			}
		}
	}

	private static @Nullable Vec3 mouthOf(Visual v) {
		if (v.state == null || !v.state.has(PlayerNet.StateS2C.FIRING)) {
			return null;
		}
		return GodzillaGeometry.mouth(v.state.headPos(), v.state.headDir(), 1.0);
	}

	private static @Nullable Vec3 impactOf(Visual v) {
		Vec3 m = mouthOf(v);
		if (m == null || v.state.beamLength() >= PlayerConfig.BREATH_RANGE - 1) {
			return null;
		}
		return m.add(v.state.headDir().normalize().scale(v.state.beamLength()));
	}

	// ------------------------------------------------------------------ rumble (VR) and shake (flat)

	private static void rumble(LocalPlayer me, Visual v) {
		if (!PlayerVr.active()) {
			return;
		}
		hapticClock++;
		if (hapticClock % 2 != 0) {
			return;
		}
		float amp = 0;
		if (v.state != null) {
			if (v.state.has(PlayerNet.StateS2C.FIRING)) {
				amp = Math.max(amp, 0.4F + 0.1F * (float) Math.sin(hapticClock * 0.9));
			} else if (v.state.has(PlayerNet.StateS2C.CHARGING)) {
				amp = Math.max(amp, 0.1F + 0.5F * v.state.charge());
			}
			if (v.state.roarTicks() > 0) {
				amp = Math.max(amp, 0.95F * Math.min(1, v.state.roarTicks() / 20.0F));
			}
		}
		if (shakeTicks > 0) {
			amp = Math.max(amp, Math.min(0.85F, shakeStrength * 0.7F * remaining() + 0.05F));
		}
		if (amp > 0.02F) {
			PlayerVr.haptic(true, 0.09F, 120, amp);
			PlayerVr.haptic(false, 0.09F, 120, amp);
		}
	}

	/** {yaw, pitch, x, y, z} camera offsets for flat clients; zero in VR (the controllers rumble instead). */
	public static float[] shakeOffsets(float partialTick) {
		if (shakeTicks <= 0 || PlayerVr.active() || !localIsGodzilla()) {
			return new float[5];
		}
		float k = shakeStrength * ((shakeTicks - partialTick) / shakeTotal);
		k = Math.max(0, k);
		var rnd = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getRandom();
		if (rnd == null) {
			return new float[5];
		}
		float t = (Minecraft.getInstance().level.getGameTime() + partialTick);
		float amp = k * 1.6F;
		return new float[]{
				(float) Math.sin(t * 2.3) * amp * 0.8F, (float) Math.cos(t * 3.1) * amp, (float) Math.sin(t * 5.1) * k * 0.9F,
				(float) Math.cos(t * 4.7) * k * 1.3F, (float) Math.sin(t * 6.3) * k * 0.9F};
	}

	private static void drawFlash(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		if (flashTicks <= 0 || !BreathGauge.active) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int a = (int) (Mth.clamp(flashStrength * flashTicks / 12.0F, 0, 1) * 150);
		int col = (a << 24) | 0xFFE8C0;
		int edge = Math.max(8, w / 10);
		Minecraft mc = Minecraft.getInstance();
		var cam = mc.getEntityRenderDispatcher().camera;
		if (cam == null) {
			return;
		}
		Vec3 to = flashFrom.subtract(cam.position());
		double yawTo = Math.toDegrees(Math.atan2(-to.x, to.z));
		float rel = Mth.wrapDegrees((float) yawTo - cam.yRot());
		if (Math.abs(rel) > 120) {
			g.fill(0, h - edge, w, h, col);
		} else if (rel > 25) {
			g.fill(0, 0, edge, h, col);
		} else if (rel < -25) {
			g.fill(w - edge, 0, w, h, col);
		} else {
			g.fill(0, 0, w, edge, col);
		}
	}

	private static void hideVanillaHud() {
		Identifier[] ids = {VanillaHudElements.HOTBAR, VanillaHudElements.CROSSHAIR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.AIR_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOUNT_HEALTH};
		for (Identifier id : ids) {
			HudElementRegistry.replaceElement(id, orig -> (g, dt) -> {
				if (!BreathGauge.active) {
					orig.extractRenderState(g, dt);
				}
			});
		}
	}
}
