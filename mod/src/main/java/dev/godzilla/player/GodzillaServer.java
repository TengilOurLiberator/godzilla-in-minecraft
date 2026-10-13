package dev.godzilla.player;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.GzEvents;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaMod;
import dev.godzilla.core.GodzillaSounds;
import dev.godzilla.player.net.PlayerNet;
import dev.godzilla.vr.PoseSnapshot;
import dev.godzilla.vr.Vr;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of being Godzilla: attributes and abilities, the stride, feet, body, tail, hands, atomic breath, roar and the rise.
 * One {@link Runtime} per Godzilla player; {@link #tick} re-evaluates every tick who is Godzilla (so joining mid-run, phase
 * changes, RESET and disconnects all converge to the right state).
 */
public final class GodzillaServer {
	/** Speed multiplier on the vanilla walking speed, tuned so that full stick crosses 400 blocks in about 40 s. */
	public static double speedFactor = PlayerConfig.WALK_BLOCKS_PER_SECOND / 20.0 / 0.2158 * 1.0;

	private static final Map<UUID, Runtime> RUNTIMES = new HashMap<>();
	private static final Set<UUID> FORCED = new HashSet<>();

	private GodzillaServer() {
	}

	public enum Breath { IDLE, CHARGING, FIRING, LOCKOUT }

	public static final class Runtime {
		final UUID uuid;
		GameType prevGameType = GameType.SURVIVAL;
		final List<ItemStack> savedItems = new ArrayList<>();
		final BodyMotion motion = new BodyMotion();
		Vec3 lastPos = Vec3.ZERO;
		int lastStep;
		boolean attacks;
		// input
		boolean inAttack;
		boolean inUse;
		boolean prevUse;
		int forceAttack;
		int swipeLeft = -1;
		int swipeRight = -1;
		// breath
		Breath breath = Breath.IDLE;
		int chargeTicks;
		float gauge = 1.0F;
		int lockout;
		int rechargeDelay;
		float beamLen;
		int beamTicks;
		Vec3 beamImpact = Vec3.ZERO;
		// roar
		int roarTicks;
		int roarCooldown;
		// hands
		Vec3 prevRight;
		Vec3 prevLeft;
		int swooshCooldown;
		int tailCooldown;
		Vec3[] prevTail;
		Vec3 prevFeet;
		// rise
		boolean riseSent;
		boolean surfaced;
		int idle;
		float plough;
		Pose pose;

		Runtime(UUID uuid) {
			this.uuid = uuid;
		}

		public float gauge() {
			return gauge;
		}

		public Breath breath() {
			return breath;
		}

		public BodyMotion motion() {
			return motion;
		}

		public Pose pose() {
			return pose;
		}
	}

	/** Head and hand poses in world space for one tick. */
	public record Pose(boolean vr, Vec3 head, Vec3 headDir, Vec3 right, Vec3 rightDir, Vec3 left, Vec3 leftDir) {
	}

	// ------------------------------------------------------------------ public API

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GodzillaServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			Runtime rt = RUNTIMES.get(p.getUUID());
			if (rt != null) {
				unbecome(p, rt, false);
			}
			FORCED.remove(p.getUUID());
		});
		GzEvents.RESET.register(g -> {
			FORCED.clear();
			MinecraftServer server = dev.godzilla.core.dev.DevGame.server();
			if (server != null) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					Runtime rt = RUNTIMES.get(p.getUUID());
					if (rt != null) {
						unbecome(p, rt, true);
					}
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PlayerNet.InputC2S.TYPE, (payload, ctx) -> {
			Runtime rt = RUNTIMES.get(ctx.player().getUUID());
			if (rt != null) {
				rt.inAttack = payload.attack();
				rt.inUse = payload.use();
				if (payload.swipeLeft()) {
					swipe(rt, true);
				}
				if (payload.swipeRight()) {
					swipe(rt, false);
				}
			}
		});
	}

	public static @Nullable Runtime runtime(ServerPlayer p) {
		return RUNTIMES.get(p.getUUID());
	}

	public static void force(ServerPlayer p, boolean on) {
		if (on) {
			FORCED.add(p.getUUID());
		} else {
			FORCED.remove(p.getUUID());
			Runtime rt = RUNTIMES.get(p.getUUID());
			if (rt != null && !shouldBe(p)) {
				unbecome(p, rt, true);
			}
		}
	}

	public static void swipe(Runtime rt, boolean left) {
		if (left) {
			rt.swipeLeft = 0;
		} else {
			rt.swipeRight = 0;
		}
	}

	/** Dev: holds the trigger for this many ticks without a client. */
	public static void forceBreath(Runtime rt, int ticks) {
		rt.forceAttack = ticks;
	}

	public static boolean roar(ServerPlayer p, Runtime rt) {
		if (rt.roarCooldown > 0) {
			return false;
		}
		rt.roarTicks = PlayerConfig.ROAR_TICKS;
		rt.roarCooldown = PlayerConfig.ROAR_COOLDOWN;
		Pose pose = rt.pose != null ? rt.pose : computePose(p, rt);
		double s = GodzillaGeometry.scaleOf(p.getScale());
		Vec3 mouth = GodzillaGeometry.mouth(pose.head(), pose.headDir(), s);
		ServerLevel level = p.level();
		play(level, mouth, GodzillaSounds.GODZILLA_ROAR, 320, 1.0F);
		Gz.fx().shake(p, 0.9F, PlayerConfig.ROAR_TICKS);
		ServerPlayNetworking.send(p, new PlayerNet.HapticS2C(2, 1.0F, 1.2F));
		GzEvents.ROAR.fire(p);
		return true;
	}

	public static boolean shouldBe(ServerPlayer p) {
		if (FORCED.contains(p.getUUID())) {
			return true;
		}
		Game g = Gz.game();
		return g.phase() != Game.Phase.IDLE && g.godzilla() == p;
	}

	// ------------------------------------------------------------------ become / unbecome

	private static void addMod(ServerPlayer p, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = p.getAttribute(attr);
		if (a != null) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void removeMod(ServerPlayer p, Holder<Attribute> attr, Identifier id) {
		AttributeInstance a = p.getAttribute(attr);
		if (a != null) {
			a.removeModifier(id);
		}
	}

	private static Runtime become(ServerPlayer p) {
		Runtime rt = new Runtime(p.getUUID());
		RUNTIMES.put(p.getUUID(), rt);
		rt.prevGameType = p.gameMode.getGameModeForPlayer();
		// empty hands: stash the inventory
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			rt.savedItems.add(p.getInventory().getItem(i).copy());
		}
		p.getInventory().clearContent();
		p.setGameMode(GameType.ADVENTURE);
		p.getAbilities().invulnerable = true;
		p.getAbilities().mayfly = false;
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		addMod(p, Attributes.SCALE, GodzillaState.SCALE_ID, PlayerConfig.SCALE - 1.0, AttributeModifier.Operation.ADD_VALUE);
		addMod(p, Attributes.MOVEMENT_SPEED, GodzillaState.SPEED_ID, speedFactor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addMod(p, Attributes.STEP_HEIGHT, GodzillaState.STEP_ID, PlayerConfig.STEP_HEIGHT - 0.6, AttributeModifier.Operation.ADD_VALUE);
		addMod(p, Attributes.JUMP_STRENGTH, GodzillaState.JUMP_ID, -1.0, AttributeModifier.Operation.ADD_VALUE);
		addMod(p, Attributes.SAFE_FALL_DISTANCE, GodzillaState.FALL_ID, 1000.0, AttributeModifier.Operation.ADD_VALUE);
		addMod(p, Attributes.CAMERA_DISTANCE, GodzillaState.CAMERA_ID, PlayerConfig.CAMERA_DISTANCE - 4.0, AttributeModifier.Operation.ADD_VALUE);
		p.refreshDimensions();
		GodzillaMod.LOGGER.info("godzilla: {} becomes Godzilla (scale {})", p.getGameProfile().name(), PlayerConfig.SCALE);
		return rt;
	}

	private static void unbecome(ServerPlayer p, Runtime rt, boolean notify) {
		RUNTIMES.remove(p.getUUID());
		removeMod(p, Attributes.SCALE, GodzillaState.SCALE_ID);
		removeMod(p, Attributes.MOVEMENT_SPEED, GodzillaState.SPEED_ID);
		removeMod(p, Attributes.STEP_HEIGHT, GodzillaState.STEP_ID);
		removeMod(p, Attributes.JUMP_STRENGTH, GodzillaState.JUMP_ID);
		removeMod(p, Attributes.SAFE_FALL_DISTANCE, GodzillaState.FALL_ID);
		removeMod(p, Attributes.CAMERA_DISTANCE, GodzillaState.CAMERA_ID);
		p.setGameMode(rt.prevGameType);
		p.onUpdateAbilities();
		p.getInventory().clearContent();
		for (int i = 0; i < rt.savedItems.size() && i < p.getInventory().getContainerSize(); i++) {
			p.getInventory().setItem(i, rt.savedItems.get(i));
		}
		p.refreshDimensions();
		if (notify) {
			ServerPlayNetworking.send(p, new PlayerNet.RiseS2C(false, Vec3.ZERO, 0, 0));
			broadcast(p, new PlayerNet.StateS2C(p.getId(), PlayerNet.StateS2C.ENDED, 0, 0, 0, 0, -1, -1, 0.0F, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
		}
		GodzillaMod.LOGGER.info("godzilla: {} is no longer Godzilla", p.getGameProfile().name());
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean should = shouldBe(p);
			Runtime rt = RUNTIMES.get(p.getUUID());
			if (should && rt == null) {
				rt = become(p);
			} else if (!should && rt != null) {
				unbecome(p, rt, true);
				continue;
			}
			if (rt != null) {
				try {
					tickOne(p, rt);
				} catch (RuntimeException e) {
					GodzillaMod.LOGGER.error("godzilla: player module tick failed", e);
				}
			}
		}
	}

	private static void tickOne(ServerPlayer p, Runtime rt) {
		ServerLevel level = p.level();
		Game game = Gz.game();
		Game.Phase phase = game.phase();
		boolean forced = FORCED.contains(p.getUUID());
		rt.attacks = forced || phase.playing();
		boolean rise = phase == Game.Phase.RISE && !forced;
		double scale = p.getScale();
		double s = GodzillaGeometry.scaleOf(scale);

		// vitals: nothing hurts, nothing is needed
		p.setAirSupply(p.getMaxAirSupply());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(20.0F);
		p.clearFire();
		p.fallDistance = 0;
		if (p.getAbilities().flying || p.getAbilities().mayfly) {
			p.getAbilities().flying = false;
			p.getAbilities().mayfly = false;
			p.onUpdateAbilities();
		}
		if (p.getHealth() < p.getMaxHealth()) {
			p.setHealth(p.getMaxHealth());
		}

		if (rt.forceAttack > 0) {
			rt.forceAttack--;
		}
		if (rt.swipeLeft >= 0 && ++rt.swipeLeft >= PlayerConfig.SWIPE_TICKS) {
			rt.swipeLeft = -1;
		}
		if (rt.swipeRight >= 0 && ++rt.swipeRight >= PlayerConfig.SWIPE_TICKS) {
			rt.swipeRight = -1;
		}
		if (rt.roarTicks > 0) {
			rt.roarTicks--;
		}
		if (rt.roarCooldown > 0) {
			rt.roarCooldown--;
		}
		if (rt.swooshCooldown > 0) {
			rt.swooshCooldown--;
		}
		if (rt.tailCooldown > 0) {
			rt.tailCooldown--;
		}

		// motion and pose
		Vec3 feet = p.position();
		Vec3 move = rt.lastPos == Vec3.ZERO ? Vec3.ZERO : feet.subtract(rt.lastPos);
		if (move.lengthSqr() > 40 * 40) {
			move = Vec3.ZERO; // teleport
			rt.motion.initialised = false;
			rt.lastStep = 0;
		}
		rt.lastPos = feet;
		Pose pose = computePose(p, rt);
		rt.pose = pose;
		float headYaw = GodzillaGeometry.yawOf(pose.headDir());
		if (!rt.motion.initialised) {
			rt.motion.reset(headYaw);
			rt.lastStep = BodyMotion.stepIndex(rt.motion.phase);
		}
		rt.motion.tick(headYaw, move.x, move.z, p.onGround() || p.isInWater() || level.getBlockState(p.blockPosition().below()).isSolid(), scale);
		if (rise) {
			riseTick(p, rt, level, game, feet, s);
		} else if (rt.riseSent) {
			rt.riseSent = false;
			rt.surfaced = false;
			ServerPlayNetworking.send(p, new PlayerNet.RiseS2C(false, Vec3.ZERO, 0, 0));
		}

		if (rt.attacks) {
			feetAndBody(p, rt, level, feet, move, s);
			tail(p, rt, level, feet, s);
			hands(p, rt, level, pose, s);
			breath(p, rt, level, pose, s);
			if (rt.inUse && !rt.prevUse) {
				roar(p, rt);
			}
		} else {
			rt.breath = Breath.IDLE;
			rt.chargeTicks = 0;
			rt.prevRight = null;
			rt.prevLeft = null;
			rt.prevTail = null;
			if (!rise) {
				rt.beamLen = 0;
			}
			rt.gauge = Math.min(1.0F, rt.gauge + 1.0F / PlayerConfig.BREATH_RECHARGE_TICKS);
		}
		rt.prevUse = rt.inUse;
		rt.prevFeet = feet;
		sendState(p, rt, pose, rise);
	}

	// ------------------------------------------------------------------ pose

	private static Pose computePose(ServerPlayer p, Runtime rt) {
		PoseSnapshot snap = Vr.pose(p);
		double s = GodzillaGeometry.scaleOf(p.getScale());
		if (snap.vr()) {
			boolean lh = snap.leftHanded();
			Vec3 rp = lh ? snap.offHandPos() : snap.mainHandPos();
			Vec3 rd = lh ? snap.offHandDir() : snap.mainHandDir();
			Vec3 lp = lh ? snap.mainHandPos() : snap.offHandPos();
			Vec3 ld = lh ? snap.mainHandDir() : snap.offHandDir();
			return new Pose(true, snap.headPos(), snap.headDir(), rp, rd, lp, ld);
		}
		Vec3 feet = p.position();
		double yaw = rt.motion.initialised ? rt.motion.yaw : p.getYRot();
		Vec3 head = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		float sl = rt.swipeLeft < 0 ? -1 : rt.swipeLeft;
		float sr = rt.swipeRight < 0 ? -1 : rt.swipeRight;
		float idle = p.tickCount;
		Vec3 rh = GodzillaGeometry.flatHand(feet, yaw, s, false, sr, idle);
		Vec3 lh = GodzillaGeometry.flatHand(feet, yaw, s, true, sl, idle);
		Vec3 rs = GodzillaGeometry.shoulder(feet, yaw, s, false);
		Vec3 ls = GodzillaGeometry.shoulder(feet, yaw, s, true);
		return new Pose(false, head, dir, rh, rh.subtract(rs).normalize(), lh, lh.subtract(ls).normalize());
	}

	// ------------------------------------------------------------------ feet and body

	private static void feetAndBody(ServerPlayer p, Runtime rt, ServerLevel level, Vec3 feet, Vec3 move, double s) {
		BodyMotion m = rt.motion;
		int idx = BodyMotion.stepIndex(m.phase);
		if (idx > rt.lastStep && idx - rt.lastStep < 4) {
			footfall(p, rt, level, feet, idx, s);
		}
		rt.lastStep = idx;

		double sp = Math.hypot(move.x, move.z);
		if (sp > 0.03 || p.tickCount % 10 == 0) {
			Vec3 fwd = sp > 0.03 ? new Vec3(move.x, 0, move.z).normalize() : GodzillaGeometry.forward(m.yaw);
			Vec3 ahead = fwd.scale(Math.min(2.5, 0.8 + sp * 4) * s);
			double half = 3.6 * s;
			Vec3 impulse = fwd.scale(Math.max(0.15, sp * 1.8)).add(0, 0.05, 0);
			// legs, then torso
			AABB legs = new AABB(feet.x - half, feet.y + 0.05, feet.z - half, feet.x + half, feet.y + 12.5 * s, feet.z + half).move(ahead);
			AABB torso = new AABB(feet.x - half - 0.8 * s, feet.y + 12.5 * s, feet.z - half - 0.8 * s,
					feet.x + half + 0.8 * s, feet.y + 24.5 * s, feet.z + half + 0.8 * s).move(ahead);
			int removed = Gz.destruction().smashBox(level, legs, impulse, Cause.BODY) + Gz.destruction().smashBox(level, torso, impulse, Cause.BODY);
			// how much mass he is ploughing through right now (0..1); the client turns it into a little drag
			rt.plough += (Math.min(1.0F, removed / 300.0F) - rt.plough) * 0.5F;
		} else {
			rt.plough *= 0.5F;
		}
	}

	private static void footfall(ServerPlayer p, Runtime rt, ServerLevel level, Vec3 feet, int idx, double s) {
		boolean right = idx % 2 == 1;
		BodyMotion m = rt.motion;
		Vec3 fp = GodzillaGeometry.toWorld(feet, m.yaw, s, right ? -PlayerConfig.FOOT_HALF_WIDTH : PlayerConfig.FOOT_HALF_WIDTH, 0, PlayerConfig.FOOT_REACH);
		double hx = (Math.abs(Math.cos(Math.toRadians(m.yaw))) * 2.4 + Math.abs(Math.sin(Math.toRadians(m.yaw))) * 3.4) * s;
		double hz = (Math.abs(Math.sin(Math.toRadians(m.yaw))) * 2.4 + Math.abs(Math.cos(Math.toRadians(m.yaw))) * 3.4) * s;
		AABB box = new AABB(fp.x - hx, fp.y - 0.1, fp.z - hz, fp.x + hx, fp.y + 3.2 * s, fp.z + hz);
		Gz.destruction().smashBox(level, box, new Vec3(0, -0.15, 0).add(GodzillaGeometry.forward(m.yaw).scale(0.15)), Cause.FOOT);
		boolean water = level.getFluidState(net.minecraft.core.BlockPos.containing(fp.x, fp.y + 0.5, fp.z)).is(net.minecraft.tags.FluidTags.WATER);
		play(level, fp, water ? GodzillaSounds.GODZILLA_SPLASH_STEP : GodzillaSounds.GODZILLA_FOOTSTEP, 110, 0.9F + p.getRandom().nextFloat() * 0.2F);
		if (water) {
			level.sendParticles(ParticleTypes.SPLASH, fp.x, fp.y + 1.0, fp.z, 60, 2.4, 0.8, 2.4, 0.4);
			level.sendParticles(ParticleTypes.BUBBLE_POP, fp.x, fp.y + 1.0, fp.z, 20, 2.4, 0.8, 2.4, 0.1);
		}
		Gz.fx().shake(p, 0.18F + Math.min(0.12F, rt.motion.speed * 0.3F), 8);
	}

	// ------------------------------------------------------------------ tail

	private static void tail(ServerPlayer p, Runtime rt, ServerLevel level, Vec3 feet, double s) {
		BodyMotion m = rt.motion;
		Vec3[] now = BodyMotion.tailJoints(feet, m.yaw, s, m.tailYaw);
		Vec3[] prev = rt.prevTail;
		Vec3 prevFeet = rt.prevFeet;
		rt.prevTail = now;
		if (prev == null || prevFeet == null) {
			return;
		}
		double tipRel = 0;
		for (int i = 2; i < GodzillaGeometry.TAIL_SEGMENTS; i++) {
			Vec3 b = now[i + 1];
			Vec3 a = feet.add(prev[i + 1].subtract(prevFeet));
			Vec3 d = b.subtract(a);
			double len = d.length();
			if (i == GodzillaGeometry.TAIL_SEGMENTS - 1) {
				tipRel = len;
			}
			if (len > 0.3) {
				double r = GodzillaGeometry.TAIL_THICK[i] * 0.5 * s + 0.6;
				Gz.destruction().smashCapsule(level, a, b, r, d.scale(0.6), Cause.TAIL);
			}
		}
		if (tipRel > 0.6 && rt.tailCooldown == 0) {
			rt.tailCooldown = 22;
			play(level, now[GodzillaGeometry.TAIL_SEGMENTS - 2], GodzillaSounds.GODZILLA_TAIL_SWOOSH, 80, 0.9F);
		}
	}

	// ------------------------------------------------------------------ hands

	private static void hands(ServerPlayer p, Runtime rt, ServerLevel level, Pose pose, double s) {
		for (int h = 0; h < 2; h++) {
			boolean left = h == 1;
			Vec3 cur = left ? pose.left() : pose.right();
			Vec3 prev = left ? rt.prevLeft : rt.prevRight;
			if (left) {
				rt.prevLeft = cur;
			} else {
				rt.prevRight = cur;
			}
			if (prev == null) {
				continue;
			}
			Vec3 v = cur.subtract(prev);
			double sp = v.length();
			double slow = PlayerConfig.HAND_SLOW * s;
			double fast = PlayerConfig.HAND_FAST * s;
			if (sp < slow || sp > 60) {
				continue;
			}
			double k = Mth.clamp((sp - slow) / (fast - slow), 0, 1);
			Vec3 impulse = v.normalize().scale(sp * (0.35 + 0.65 * k));
			int n = Gz.destruction().smashCapsule(level, prev, cur, PlayerConfig.HAND_RADIUS * s, impulse, Cause.HAND);
			if (sp > fast * 0.6 && rt.swooshCooldown == 0) {
				rt.swooshCooldown = 8;
				play(level, cur, GodzillaSounds.GODZILLA_HAND_SWOOSH, 64, (float) (0.85 + 0.3 * k));
			}
			if (n > 0 && pose.vr()) {
				float str = Mth.clamp(0.25F + n / 90.0F, 0.25F, 1.0F);
				ServerPlayNetworking.send(p, new PlayerNet.HapticS2C(left ? 1 : 0, str, 0.08F + Math.min(0.25F, n / 500.0F)));
			}
		}
	}

	// ------------------------------------------------------------------ atomic breath

	private static void breath(ServerPlayer p, Runtime rt, ServerLevel level, Pose pose, double s) {
		boolean trigger = rt.inAttack || rt.forceAttack > 0;
		Vec3 mouth = GodzillaGeometry.mouth(pose.head(), pose.headDir(), s);
		Vec3 dir = pose.headDir().normalize();
		if (rt.lockout > 0) {
			rt.lockout--;
		}
		switch (rt.breath) {
			case IDLE, LOCKOUT -> {
				rt.beamLen = 0;
				if (rt.lockout > 0) {
					rt.breath = Breath.LOCKOUT;
				} else {
					rt.breath = Breath.IDLE;
				}
				if (trigger && rt.lockout == 0 && rt.gauge >= PlayerConfig.BREATH_MIN_START && rt.roarTicks == 0) {
					rt.breath = Breath.CHARGING;
					rt.chargeTicks = 0;
					play(level, mouth, GodzillaSounds.BREATH_CHARGE, 96, 1.0F);
				} else if (rt.rechargeDelay > 0) {
					rt.rechargeDelay--;
				} else {
					rt.gauge = Math.min(1.0F, rt.gauge + 1.0F / PlayerConfig.BREATH_RECHARGE_TICKS);
				}
			}
			case CHARGING -> {
				if (!trigger) {
					rt.breath = Breath.IDLE;
					rt.chargeTicks = 0;
				} else if (++rt.chargeTicks >= PlayerConfig.BREATH_CHARGE_TICKS) {
					rt.breath = Breath.FIRING;
					rt.beamTicks = 0;
				}
			}
			case FIRING -> {
				if (!trigger || rt.gauge <= 0) {
					play(level, mouth, GodzillaSounds.BREATH_END, 96, 1.0F);
					rt.breath = Breath.IDLE;
					rt.rechargeDelay = PlayerConfig.BREATH_RECHARGE_DELAY;
					rt.chargeTicks = 0;
					if (rt.gauge <= 0) {
						rt.lockout = PlayerConfig.BREATH_LOCKOUT_TICKS;
						rt.breath = Breath.LOCKOUT;
					}
					rt.beamLen = 0;
				} else {
					rt.gauge = Math.max(0, rt.gauge - 1.0F / PlayerConfig.BREATH_MAX_TICKS);
					fireBeam(p, rt, level, mouth, dir, s);
					rt.beamTicks++;
				}
			}
		}
	}

	private static void fireBeam(ServerPlayer p, Runtime rt, ServerLevel level, Vec3 mouth, Vec3 dir, double s) {
		double range = PlayerConfig.BREATH_RANGE * s;
		Vec3 end = mouth.add(dir.scale(range));
		BlockHitResult hit = level.clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		boolean hitBlock = hit.getType() != HitResult.Type.MISS;
		Vec3 stop = hitBlock ? hit.getLocation() : end;
		rt.beamLen = (float) stop.subtract(mouth).length();
		rt.beamImpact = stop;

		// everything smashable along the whole ray, with higher power
		AABB span = new AABB(mouth, stop).inflate(2.5);
		for (Entity e : level.getEntities((Entity) null, span, ent -> ent instanceof Smashable && ent != p)) {
			AABB bb = e.getBoundingBox().inflate(0.8 + 0.3 * s);
			if (bb.clip(mouth, stop).isPresent() || bb.contains(mouth)) {
				((Smashable) e).onSmashed(Cause.BEAM, dir.scale(0.6), 3.0F);
			}
		}
		if (hitBlock) {
			Vec3 a = stop.subtract(dir.scale(0.6));
			Vec3 b = stop.add(dir.scale(2.6));
			Gz.destruction().smashCapsule(level, a, b, PlayerConfig.BREATH_RADIUS * Math.max(0.5, s), dir.scale(0.8), Cause.BEAM);
		}
		if (rt.beamTicks % 3 == 0) {
			Gz.destruction().ignite(level, stop, 3.2 * Math.max(0.5, s));
		}
		if (rt.beamTicks % 4 == 0) {
			Gz.fx().shake(p, 0.12F, 5);
		}
	}

	// ------------------------------------------------------------------ the rise

	private static void riseTick(ServerPlayer p, Runtime rt, ServerLevel level, Game game, Vec3 feet, double s) {
		int t = game.phaseTicks();
		if (!rt.riseSent || t % 20 == 0) {
			dev.godzilla.api.CityData.Start start = Gz.city().data().playerStart();
			Vec3 pos = start.pos();
			if (!rt.riseSent && t < 5) {
				p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), start.yaw(), 8.0F, true);
			}
			rt.riseSent = true;
			ServerPlayNetworking.send(p, new PlayerNet.RiseS2C(true, pos, start.yaw(), t));
		}
		double eyeY = feet.y + p.getEyeHeight();
		if (!rt.surfaced && t >= Game.RISE_SURFACE_TICK) {
			rt.surfaced = true;
			play(level, new Vec3(feet.x, PlayerConfig.SEA_LEVEL + 1, feet.z), GodzillaSounds.GODZILLA_SURFACE, 200, 1.0F);
			Gz.fx().shake(p, 0.8F, 50);
			ServerPlayNetworking.send(p, new PlayerNet.HapticS2C(2, 0.9F, 0.6F));
		}
		double surface = PlayerConfig.SEA_LEVEL + 0.9;
		if (t >= Game.RISE_SURFACE_TICK - 6 && t < Game.RISE_SURFACE_TICK + 70) {
			// the wave ring on the sea
			double age = Math.max(0, t - (Game.RISE_SURFACE_TICK - 6));
			double r = 3 + age * 0.75;
			int n = (int) (20 + r * 2.5);
			for (int i = 0; i < n; i++) {
				double a = i / (double) n * Math.PI * 2 + age * 0.13;
				double px = feet.x + Math.cos(a) * r;
				double pz = feet.z + Math.sin(a) * r;
				level.sendParticles(p, ParticleTypes.SPLASH, true, true, px, surface + 0.1, pz, 2, 0.4, 0.2, 0.4, 0.15);
				if (i % 3 == 0) {
					level.sendParticles(p, ParticleTypes.CLOUD, true, true, px, surface + 0.3, pz, 1, 0.3, 0.1, 0.3, 0.02);
				}
			}
		}
		if (eyeY > surface - 1.0 && t < Game.RISE_TICKS) {
			// water pouring off the head and shoulders: everything of the body that is above the surface
			double top = Math.min(eyeY + 1.0 * s, feet.y + 28.0 * s);
			double h = top - surface;
			if (h > 0.5) {
				double cy = surface + h * 0.5;
				level.sendParticles(p, ParticleTypes.FALLING_WATER, true, true, feet.x, cy, feet.z, 45, 3.6 * s, h * 0.45, 3.6 * s, 0.0);
				level.sendParticles(p, ParticleTypes.SPLASH, true, true, feet.x, surface + 0.2, feet.z, 25, 5.5 * s, 0.4, 5.5 * s, 0.3);
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Plays a sound at full range: volume is the attenuation distance divided by 16 so that the server broadcasts far enough. */
	static void play(ServerLevel level, Vec3 at, SoundEvent sound, int range, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, range / 16.0F, pitch);
	}

	private static void sendState(ServerPlayer p, Runtime rt, Pose pose, boolean rise) {
		int flags = PlayerNet.StateS2C.ACTIVE;
		if (rt.breath == Breath.CHARGING) {
			flags |= PlayerNet.StateS2C.CHARGING;
		}
		if (rt.breath == Breath.FIRING) {
			flags |= PlayerNet.StateS2C.FIRING;
		}
		if (rt.breath == Breath.LOCKOUT) {
			flags |= PlayerNet.StateS2C.LOCKOUT;
		}
		if (pose.vr()) {
			flags |= PlayerNet.StateS2C.VR;
		}
		if (rise) {
			flags |= PlayerNet.StateS2C.RISE;
		}
		if (rt.attacks) {
			flags |= PlayerNet.StateS2C.ATTACKS;
		}
		float charge = rt.breath == Breath.CHARGING ? rt.chargeTicks / (float) PlayerConfig.BREATH_CHARGE_TICKS
				: rt.breath == Breath.FIRING ? 1.0F : 0.0F;
		broadcast(p, new PlayerNet.StateS2C(p.getId(), flags, rt.gauge, charge, rt.beamLen, rt.roarTicks, rt.swipeLeft, rt.swipeRight,
				rt.plough, pose.head(), pose.headDir(), pose.right(), pose.rightDir(), pose.left(), pose.leftDir()));
	}

	private static void broadcast(ServerPlayer source, PlayerNet.StateS2C payload) {
		MinecraftServer server = source.level().getServer();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(other, payload);
		}
	}
}
