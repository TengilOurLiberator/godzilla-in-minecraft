package dev.godzilla.mothra;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.api.Smashable;
import dev.godzilla.core.GodzillaSounds;
import dev.godzilla.game.GameAudio;
import dev.godzilla.game.GameModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Mothra. A plain entity (no vanilla AI, damage or collision) that flies a scripted, telegraphed fight around Godzilla:
 * circle, gust, scale cloud, dive. See the mode methods for what each one asks of the player. All distances are in blocks and
 * assume the 16x Godzilla (eyes at about 26 blocks above his feet).
 */
public class Mothra extends Entity implements Smashable {
	// ---- tuning --------------------------------------------------------------------------------------------------
	public static final float MAX_HP = 700f;
	static final double CIRCLE_RADIUS = 44;
	static final double CIRCLE_SPEED = 0.95; // blocks per tick along the circle
	static final float BEAM_DAMAGE_PER_POWER = 0.85f;
	static final float HAND_DAMAGE = 30f;
	static final float TAIL_DAMAGE = 45f;
	static final float BODY_DAMAGE = 20f;
	static final float SWAT_DAMAGE = 170f;
	static final float COLLAPSE_DAMAGE = 25f;
	static final float DIVE_HIT_DAMAGE = 24f;
	static final float GUST_DAMAGE_TOTAL = 8f;
	public static final double BODY_HEIGHT = 6;
	/** Test commands set this so a simulated hit counts from any distance. */
	public static boolean debugNoRange;
	/** She never flies lower than this (above the rooftops and the sea) even when Godzilla wades in the bay. */
	static final double MIN_ALT = 76;

	public enum Mode {
		ARRIVE, CIRCLE, GUST_WIND, GUST, SCALES, DIVE_WIND, DIVE, STUNNED, RETREAT, DYING, DEAD
	}

	private static final EntityDataAccessor<Integer> D_MODE = SynchedEntityData.defineId(Mothra.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> D_HURT_UNTIL = SynchedEntityData.defineId(Mothra.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> D_GLOW = SynchedEntityData.defineId(Mothra.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> D_ROLL = SynchedEntityData.defineId(Mothra.class, EntityDataSerializers.FLOAT);

	// ---- server state ----------------------------------------------------------------------------------------------
	private float hp = MAX_HP;
	private Mode mode = Mode.ARRIVE;
	private int modeTicks;
	private Vec3 vel = Vec3.ZERO;
	private double theta;
	private double dir = 1;
	private int attackIn = 130;
	private int lastAttack = -1;
	private boolean enraged;
	private int meleeCd;
	private int collapseCd;
	private int hurtSoundCd;
	private float roll;
	private float pitch;
	private Vec3 arriveFrom = Vec3.ZERO;
	private Vec3 arriveTo = Vec3.ZERO;
	private boolean hitDone;
	private boolean swatted;
	private double minDive = 1e9;
	private Vec3 stunVel = Vec3.ZERO;
	private Vec3 deathStart = Vec3.ZERO;
	private Vec3 crashPoint = Vec3.ZERO;
	private double deathRadius;
	private double deathAngle;
	private float gustDamageLeft;
	private float damageWindow;
	private int damageWindowAge;

	// ---- client animation state (interpolated in the renderer) -----------------------------------------------------------
	public float animPhase, animPhaseO;
	public float animAmp = 0.7f, animAmpO = 0.7f;
	public float animFold, animFoldO;
	public float animSpeed = 1f;
	public float animGlow, animGlowO;

	public Mothra(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(D_MODE, Mode.ARRIVE.ordinal());
		entityData.define(D_HURT_UNTIL, 0);
		entityData.define(D_GLOW, 0f);
		entityData.define(D_ROLL, 0f);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 2);
	}

	// ---- accessors -----------------------------------------------------------------------------------------------------

	public Mode mode() {
		Mode[] v = Mode.values();
		return v[Mth.clamp(entityData.get(D_MODE), 0, v.length - 1)];
	}

	public int hurtUntil() {
		return entityData.get(D_HURT_UNTIL);
	}

	public float glow() {
		return entityData.get(D_GLOW);
	}

	public float rollRad() {
		return entityData.get(D_ROLL);
	}

	public float hp() {
		return hp;
	}

	public void setHp(float v) {
		hp = Math.min(MAX_HP, v);
		if (hp <= 0 && mode != Mode.DYING && mode != Mode.DEAD) {
			die();
		}
	}

	public Vec3 center() {
		return position().add(0, BODY_HEIGHT / 2, 0);
	}

	public boolean alive() {
		return mode != Mode.DYING && mode != Mode.DEAD;
	}

	// ---- setup -------------------------------------------------------------------------------------------------------------

	/** Places her at the start of the fly-in (clipped to the loaded city region) and plays her cry from the direction she comes from. */
	public void beginArrival(Vec3 from, Vec3 to) {
		// the start point must be inside the forced-loaded city columns or she would not be ticked
		Vec3 start = from;
		Vec3 d = to.subtract(from);
		for (int i = 0; i <= 40 && (Math.abs(start.x) > 235 || start.z < -300 || start.z > 300); i++) {
			start = from.add(d.scale(i / 40.0));
		}
		arriveFrom = start;
		arriveTo = to;
		mode = Mode.ARRIVE;
		modeTicks = 0;
		setPos(start.x, start.y - BODY_HEIGHT / 2, start.z);
		face(to.subtract(start));
		syncMode();
		Player g = target();
		if (g instanceof ServerPlayer sp) {
			Vec3 toward = start.subtract(g.position());
			Vec3 flat = new Vec3(toward.x, 0, toward.z);
			flat = flat.lengthSqr() < 1 ? new Vec3(0, 0, -1) : flat.normalize();
			GameAudio.at(sp, GodzillaSounds.MOTHRA_CRY, SoundSource.HOSTILE, g.getEyePosition().add(flat.scale(170)).add(0, 40, 0), 1f, 1f);
		}
	}

	/** Test helper: start straight in the circling phase around the target. */
	public void beginCircling() {
		Player g = target();
		mode = Mode.CIRCLE;
		modeTicks = 0;
		attackIn = 100;
		if (g != null) {
			theta = Math.atan2(position().z - g.getZ(), position().x - g.getX());
			Vec3 goal = circleGoal(g);
			setPos(goal.x, goal.y - BODY_HEIGHT / 2, goal.z);
		}
		syncMode();
	}

	private void syncMode() {
		entityData.set(D_MODE, mode.ordinal());
	}

	private void enter(Mode m) {
		dev.godzilla.core.GodzillaMod.LOGGER.info("mothra: {} -> {} (hp {}/{}, godzilla {})", mode, m, Math.round(hp), Math.round(MAX_HP),
				Math.round(Gz.game().godzillaHealth()));
		mode = m;
		modeTicks = 0;
		hitDone = false;
		swatted = false;
		syncMode();
	}

	public void forceAttack(String name) {
		Player g = target();
		if (!alive() || g == null) {
			return;
		}
		switch (name) {
			case "gust" -> startGust();
			case "scales" -> startScales();
			case "dive" -> startDive();
			default -> {
			}
		}
	}

	// ---- tick --------------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
		} else {
			serverTick();
		}
	}

	private void clientTick() {
		Mode m = mode();
		float tAmp, tFold, tSpeed;
		switch (m) {
			case GUST_WIND, GUST, SCALES -> {
				tAmp = 0.95f;
				tFold = 0f;
				tSpeed = 1.45f;
			}
			case DIVE_WIND -> {
				tAmp = 1.0f;
				tFold = 0f;
				tSpeed = 1.6f;
			}
			case DIVE -> {
				tAmp = 0.12f;
				tFold = 1.0f;
				tSpeed = 0.5f;
			}
			case STUNNED -> {
				tAmp = 0.5f;
				tFold = 0.3f;
				tSpeed = 2.0f;
			}
			case DYING -> {
				tAmp = 0.35f;
				tFold = 0.6f;
				tSpeed = 0.7f;
			}
			case DEAD -> {
				tAmp = 0.0f;
				tFold = -0.5f;
				tSpeed = 0f;
			}
			case ARRIVE -> {
				tAmp = 0.55f;
				tFold = 0f;
				tSpeed = 0.8f;
			}
			default -> {
				tAmp = 0.62f;
				tFold = 0f;
				tSpeed = 1f;
			}
		}
		animPhaseO = animPhase;
		animAmpO = animAmp;
		animFoldO = animFold;
		animGlowO = animGlow;
		animSpeed += (tSpeed - animSpeed) * 0.12f;
		animAmp += (tAmp - animAmp) * 0.12f;
		animFold += (tFold - animFold) * 0.15f;
		animGlow += (glow() - animGlow) * 0.2f;
		animPhase += animSpeed * 0.785f; // 2.5 beats per second
	}

	private @Nullable Player target() {
		ServerPlayer g = Gz.game().godzilla();
		if (g != null) {
			return g;
		}
		return level().getNearestPlayer(this, 600);
	}

	private void serverTick() {
		ServerLevel sl = (ServerLevel) level();
		modeTicks++;
		if (meleeCd > 0) {
			meleeCd--;
		}
		if (collapseCd > 0) {
			collapseCd--;
		}
		if (hurtSoundCd > 0) {
			hurtSoundCd--;
		}
		if (++damageWindowAge > 40) {
			damageWindowAge = 0;
			damageWindow = 0;
		}
		Player g = target();
		Vec3 before = position();
		float glowTarget = 0;
		float pitchTarget = 0;
		float rollTarget = 0;

		switch (mode) {
			case ARRIVE -> {
				// two legs: eased flight to the arrival point, then steer into the circle around Godzilla
				if (modeTicks <= 150) {
					double t = Mth.clamp(modeTicks / 150.0, 0, 1);
					double e = t * t * (3 - 2 * t);
					Vec3 p = arriveFrom.lerp(arriveTo, e).add(Math.sin(t * 6.0) * 7, Math.sin(t * 4.0) * 4, 0);
					setPos(p.x, p.y - BODY_HEIGHT / 2, p.z);
					face(arriveTo.subtract(arriveFrom));
				} else if (g != null) {
					steer(circleGoal(g), 2.1, 0.08);
					faceVel();
					if (modeTicks > 280 || center().distanceTo(circleGoal(g)) < 14) {
						theta = Math.atan2(center().z - g.getZ(), center().x - g.getX());
						attackIn = 150;
						enter(Mode.CIRCLE);
						if (g instanceof ServerPlayer sp) {
							GameAudio.at(sp, GodzillaSounds.MOTHRA_CRY, SoundSource.HOSTILE, center(), 1f, 1.1f);
						}
					}
				}
				rollTarget = (float) (0.2 * Math.sin(modeTicks * 0.05));
			}
			case CIRCLE -> {
				if (g != null) {
					double r = CIRCLE_RADIUS + 4 * Math.sin(tickCount * 0.031);
					double w = CIRCLE_SPEED * (enraged ? 1.3 : 1.0) / r;
					theta += dir * w;
					steer(circleGoal(g), 2.4, 0.2);
					faceVel();
					rollTarget = (float) (-dir * 0.32);
					pitchTarget = (float) Mth.clamp(-vel.y * 6, -12, 12);
					if (--attackIn <= 0) {
						chooseAttack();
					}
				}
			}
			case GUST_WIND -> {
				if (g != null) {
					steer(hoverGoal(g, 34, 9), 1.8, 0.14);
					faceTarget(g);
					glowTarget = modeTicks / 40f;
					pitchTarget = -22;
					if (modeTicks >= 40) {
						doGust(g);
					}
				}
			}
			case GUST -> {
				if (g != null) {
					steer(hoverGoal(g, 34, 9), 0.5, 0.1);
					faceTarget(g);
					glowTarget = 1f - modeTicks / 60f;
					pitchTarget = -14;
					gustTick(sl, g);
					if (modeTicks >= 60) {
						backToCircle(g, 60);
					}
				}
			}
			case SCALES -> {
				if (g != null) {
					steer(hoverGoal(g, 40, 10), 1.4, 0.12);
					faceTarget(g);
					glowTarget = Math.min(1f, modeTicks / 25f);
					pitchTarget = -10;
					if (modeTicks == 28) {
						spawnCloud(sl, g, 0);
						if (enraged) {
							spawnCloud(sl, g, 1);
						}
					}
					if (modeTicks >= 75) {
						backToCircle(g, 70);
					}
				}
			}
			case DIVE_WIND -> {
				if (g != null) {
					steer(diveGoal(g), 2.3, 0.13);
					faceTarget(g);
					glowTarget = Math.min(1f, modeTicks / (enraged ? 28f : 36f));
					pitchTarget = -30;
					if (modeTicks >= (enraged ? 38 : 46)) {
						startSwoop(g);
					}
				}
			}
			case DIVE -> {
				if (g != null) {
					diveTick(sl, g);
					pitchTarget = (float) Math.toDegrees(Math.asin(Mth.clamp(-vel.y / Math.max(0.01, vel.length()), -1, 1)));
					rollTarget = 0;
					glowTarget = 1f;
				}
			}
			case STUNNED -> {
				stunTick(sl, g);
				rollTarget = (float) (Math.sin(modeTicks * 0.35) * 1.1 + 0.8);
				pitchTarget = -15 + (float) Math.sin(modeTicks * 0.27) * 25;
				glowTarget = 0;
			}
			case RETREAT -> {
				if (g != null) {
					steer(circleGoal(g), 2.6, 0.1);
					faceVel();
					pitchTarget = -10;
					if (modeTicks >= 50 || center().distanceTo(circleGoal(g)) < 12) {
						theta = Math.atan2(center().z - g.getZ(), center().x - g.getX());
						enter(Mode.CIRCLE);
					}
				}
			}
			case DYING -> dyingTick(sl);
			case DEAD -> {
				if (modeTicks > 140) {
					discard();
					return;
				}
			}
		}

		// bank and pitch easing
		roll += (rollTarget - roll) * 0.1f;
		pitch += (pitchTarget - pitch) * 0.12f;
		entityData.set(D_ROLL, roll);
		if (Math.abs(glow() - glowTarget) > 0.02f) {
			entityData.set(D_GLOW, glowTarget);
		}
		setXRot(pitch);
		vel = position().subtract(before);
	}

	// ---- movement helpers --------------------------------------------------------------------------------------------------

	/** Moves the body centre toward a goal with limited speed and smoothed velocity. */
	private void steer(Vec3 goal, double maxSpeed, double accel) {
		Vec3 d = goal.subtract(center());
		double dist = d.length();
		Vec3 want = dist < 1e-4 ? Vec3.ZERO : d.scale(Math.min(maxSpeed, dist * 0.12) / dist);
		Vec3 v = vel.lerp(want, accel);
		if (v.length() > maxSpeed) {
			v = v.normalize().scale(maxSpeed);
		}
		Vec3 p = position().add(v);
		setPos(p.x, p.y, p.z);
		vel = v;
	}

	private Vec3 circleGoal(Player g) {
		double r = CIRCLE_RADIUS + 4 * Math.sin(tickCount * 0.031);
		double headY = g.getEyeY();
		return new Vec3(g.getX() + r * Math.cos(theta), Math.max(headY + 2, MIN_ALT) + 3 * Math.sin(tickCount * 0.05), g.getZ() + r * Math.sin(theta));
	}

	/** A hover point at a distance from Godzilla, on the side she is on. */
	private Vec3 hoverGoal(Player g, double dist, double above) {
		Vec3 b = center().subtract(g.position());
		Vec3 flat = new Vec3(b.x, 0, b.z);
		flat = flat.lengthSqr() < 1 ? new Vec3(1, 0, 0) : flat.normalize();
		return new Vec3(g.getX() + flat.x * dist, Math.max(g.getEyeY(), MIN_ALT - 2) + above, g.getZ() + flat.z * dist);
	}

	private Vec3 diveGoal(Player g) {
		Vec3 b = center().subtract(g.position());
		Vec3 flat = new Vec3(b.x, 0, b.z);
		flat = flat.lengthSqr() < 1 ? new Vec3(1, 0, 0) : flat.normalize();
		return new Vec3(g.getX() + flat.x * 58, Math.max(g.getEyeY(), MIN_ALT - 2) + 40, g.getZ() + flat.z * 58);
	}

	private void face(Vec3 d) {
		if (d.horizontalDistanceSqr() > 1e-6) {
			float yaw = (float) (Mth.atan2(-d.x, d.z) * 180.0 / Math.PI);
			setYRot(Mth.rotLerp(0.18f, getYRot(), yaw));
		}
	}

	private void faceVel() {
		if (vel.horizontalDistanceSqr() > 0.01) {
			face(vel);
		}
	}

	private void faceTarget(Player g) {
		face(g.position().subtract(position()));
	}

	// ---- attack selection ----------------------------------------------------------------------------------------------

	private void chooseAttack() {
		Game.Phase gp = Gz.game().phase();
		if (gp == Game.Phase.DEFEAT || gp == Game.Phase.VICTORY) {
			attackIn = 100; // the fight is over: she only circles
			return;
		}
		double[] w = {1.0, 1.0, enraged ? 1.9 : 1.3};
		if (lastAttack >= 0) {
			w[lastAttack] *= 0.3;
		}
		double r = random.nextDouble() * (w[0] + w[1] + w[2]);
		int pick = r < w[0] ? 0 : r < w[0] + w[1] ? 1 : 2;
		lastAttack = pick;
		switch (pick) {
			case 0 -> startGust();
			case 1 -> startScales();
			default -> startDive();
		}
	}

	private void backToCircle(Player g, int next) {
		theta = Math.atan2(center().z - g.getZ(), center().x - g.getX());
		dir = random.nextBoolean() ? 1 : -1;
		attackIn = (int) ((90 + next + random.nextInt(60)) * (enraged ? 0.65 : 1.0));
		enter(Mode.RETREAT);
	}

	private void startGust() {
		enter(Mode.GUST_WIND);
	}

	private void doGust(Player g) {
		enter(Mode.GUST);
		gustDamageLeft = GUST_DAMAGE_TOTAL;
		if (g instanceof ServerPlayer sp) {
			GameAudio.at(sp, GodzillaSounds.MOTHRA_GUST, SoundSource.HOSTILE, center(), 1f, 1f);
			Gz.fx().shake(sp, 0.35f, 60);
		}
	}

	private void startScales() {
		enter(Mode.SCALES);
		Player g = target();
		if (g instanceof ServerPlayer sp) {
			GameAudio.at(sp, GodzillaSounds.MOTHRA_SCALES, SoundSource.HOSTILE, center(), 1f, 1f);
		}
	}

	private void startDive() {
		enter(Mode.DIVE_WIND);
		minDive = 1e9;
		Player g = target();
		if (g instanceof ServerPlayer sp) {
			GameAudio.at(sp, GodzillaSounds.MOTHRA_DIVE, SoundSource.HOSTILE, center(), 1f, 1f);
		}
	}

	// ---- gust ------------------------------------------------------------------------------------------------------------

	private void gustTick(ServerLevel sl, Player g) {
		Vec3 d = g.position().subtract(position());
		Vec3 wind = new Vec3(d.x, 0, d.z);
		wind = wind.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : wind.normalize();
		double ramp = Math.min(1, modeTicks / 8.0) * Math.min(1, (60 - modeTicks) / 12.0);
		if (modeTicks % 2 == 0) {
			g.push(wind.x * 0.2 * ramp, 0.02, wind.z * 0.2 * ramp);
			g.syncVelocity = true;
		}
		if (modeTicks % 10 == 5 && gustDamageLeft > 0) {
			float dmg = GUST_DAMAGE_TOTAL / 6f;
			gustDamageLeft -= dmg;
			Gz.game().hurtGodzilla(dmg, center());
		}
		if (g instanceof ServerPlayer sp && modeTicks % 20 == 0) {
			Gz.fx().shake(sp, 0.3f, 24);
		}
		if (modeTicks % 3 == 0) {
			// people, cars and anything else loose are blown along the wind
			AABB box = new AABB(center(), g.position()).inflate(60, 50, 60);
			var gameG = GameModule.game();
				// only civilians are blown about (vehicles and units would explode in chains and be scored as Godzilla's kills)
				gameG.suppressScore(true);
				try {
				for (Entity e : sl.getEntities(this, box, x -> x instanceof dev.godzilla.population.PersonEntity)) {
				Vec3 rel = e.position().subtract(center());
				if (rel.horizontalDistanceSqr() < 100 * 100 && !(e instanceof Player)) {
					((Smashable) e).onSmashed(Cause.MOTHRA, wind.scale(0.9).add(0, 0.25, 0), 1f);
				}
			}
				} finally {
					gameG.suppressScore(false);
				}
		}
		if (modeTicks % 5 == 0) {
			// rooftops are stripped along the wind; this is Mothra's doing, so no points for it
			var game = GameModule.game();
			game.suppressScore(true);
			try {
				Vec3 p = center().add(wind.scale(14 + random.nextInt(60)));
				Gz.destruction().smashSphere(sl, new Vec3(p.x + random.nextInt(21) - 10, 70 + random.nextInt(14), p.z + random.nextInt(21) - 10),
						3.5, wind.scale(0.8), Cause.MOTHRA);
			} finally {
				game.suppressScore(false);
			}
		}
	}

	// ---- scales ------------------------------------------------------------------------------------------------------------

	private void spawnCloud(ServerLevel sl, Player g, int index) {
		ScaleCloud c = new ScaleCloud(MothraModule.CLOUD, sl);
		Vec3 p = center().add(0, -2, 0);
		c.setPos(p.x, p.y, p.z);
		c.setDelay(index * 40);
		sl.addFreshEntity(c);
	}

	// ---- dive ------------------------------------------------------------------------------------------------------------

	private void startSwoop(Player g) {
		enter(Mode.DIVE);
		minDive = 1e9;
		Vec3 target = g.getEyePosition().add(g.getDeltaMovement().scale(5));
		vel = target.subtract(center()).normalize().scale(2.4);
	}

	private void diveTick(ServerLevel sl, Player g) {
		Vec3 head = g.getEyePosition();
		Vec3 to = head.subtract(center());
		double dist = to.length();
		minDive = Math.min(minDive, dist);
		// home in with a limited turn rate: a hard sidestep escapes, a swat does not need to
		Vec3 dirNow = vel.lengthSqr() < 1e-6 ? to.normalize() : vel.normalize();
		Vec3 newDir = dirNow.lerp(to.scale(1 / Math.max(dist, 1e-3)), enraged ? 0.09 : 0.06).normalize();
		double speed = enraged ? 2.9 : 2.6;
		Vec3 p = position().add(newDir.scale(speed));
		setPos(p.x, p.y, p.z);
		vel = newDir.scale(speed);
		face(vel);
		if (modeTicks % 3 == 0 && center().y < 98) {
			// she tears through the upper storeys on the way down: the damage is hers
			var game = GameModule.game();
			game.suppressScore(true);
			try {
				Gz.destruction().smashSphere(sl, center(), 5, vel.scale(0.6), Cause.MOTHRA);
			} finally {
				game.suppressScore(false);
			}
		}
		if (!hitDone && dist < 9.5) {
			hitDone = true;
			Gz.game().hurtGodzilla(DIVE_HIT_DAMAGE, center());
			if (g instanceof ServerPlayer sp) {
				Gz.fx().shake(sp, 1f, 30);
			}
		}
		if (hitDone || (dist > minDive + 3 && minDive < 40) || modeTicks > 90) {
			backToCircle(g, 70);
		}
	}

	/** She was swatted out of the air: knocked away, stunned, badly hurt. */
	private void swat(Vec3 impulse) {
		if (swatted || mode != Mode.DIVE) {
			return;
		}
		Player g = target();
		Vec3 away = g == null ? vel : center().subtract(g.position());
		Vec3 flat = new Vec3(away.x, 0, away.z);
		Vec3 push = impulse.horizontalDistanceSqr() > 0.01 ? new Vec3(impulse.x, 0, impulse.z).normalize().add(flat.normalize().scale(0.8)) : flat;
		stunVel = push.normalize().scale(2.6).add(0, 0.55, 0);
		enter(Mode.STUNNED);
		swatted = true;
		vel = stunVel;
		damage(SWAT_DAMAGE, Cause.HAND, true);
		if (!alive()) {
			return;
		}
		if (g instanceof ServerPlayer sp) {
			Gz.fx().shake(sp, 0.7f, 24);
			GameModule.game().bonus("SWAT!", "", 2500, center(), 0xFFE24A, 20);
		}
	}

	private void stunTick(ServerLevel sl, @Nullable Player g) {
		stunVel = stunVel.scale(0.94).add(0, -0.012, 0);
		Vec3 p = position().add(stunVel);
		setPos(p.x, Math.max(p.y, 72), p.z);
		vel = stunVel;
		if (modeTicks % 3 == 0 && center().y < 100) {
			// knocked through the skyline: this one counts for Godzilla
			Gz.destruction().smashSphere(sl, center(), 6, stunVel.scale(0.5), Cause.COLLAPSE);
		}
		if (modeTicks >= 80 && g != null) {
			backToCircle(g, 50);
		}
	}

	// ---- damage ------------------------------------------------------------------------------------------------------------

	@Override
	public void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (!alive() || level().isClientSide() || cause == Cause.MOTHRA) {
			return;
		}
		Player g = target();
		if (cause == Cause.BEAM) {
			float f = BEAM_DAMAGE_PER_POWER * Math.min(3f, Math.max(1f, power)) * beamFactor(g);
			damage(f, cause, false);
			return;
		}
		if (cause == Cause.BODY || cause == Cause.FOOT) {
			return; // standing in her way is not an attack: only hands, tail and the beam hurt her
		}
		if (cause.byGodzilla()) {
			if (!debugNoRange && g != null && g.position().distanceTo(center()) > 70) {
				return; // "only when she is close"
			}
			if (mode == Mode.DIVE && (cause == Cause.HAND || cause == Cause.TAIL || cause == Cause.BODY)) {
				swat(impulse);
				return;
			}
			if (meleeCd > 0) {
				return;
			}
			meleeCd = 10;
			float dmg = cause == Cause.TAIL ? TAIL_DAMAGE : cause == Cause.HAND ? HAND_DAMAGE : BODY_DAMAGE;
			damage(dmg * Math.min(2f, Math.max(1f, power)), cause, false);
			vel = vel.add(impulse.scale(0.4));
			return;
		}
		if (cause == Cause.COLLAPSE || cause == Cause.EXPLOSION || cause == Cause.DEBRIS) {
			if (collapseCd == 0) {
				collapseCd = 15;
				damage(COLLAPSE_DAMAGE, cause, false);
			}
		}
	}

	/** A scale cloud hanging between her and Godzilla weakens the beam to a quarter. */
	private float beamFactor(@Nullable Player g) {
		if (g == null) {
			return 1f;
		}
		Vec3 a = g.getEyePosition();
		Vec3 b = center();
		AABB box = new AABB(a, b).inflate(16);
		float f = 1f;
		for (ScaleCloud c : level().getEntitiesOfClass(ScaleCloud.class, box)) {
			Vec3 ab = b.subtract(a);
			double t = Mth.clamp(c.position().subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
			if (a.add(ab.scale(t)).distanceTo(c.position()) < 14) {
				f = 0.25f;
				c.burn(6);
			}
		}
		return f;
	}

	private void damage(float amount, Cause cause, boolean bypass) {
		if (!alive() || amount <= 0) {
			return;
		}
		float mult = switch (mode) {
			case STUNNED -> 1.5f;
			case GUST -> 0.6f;
			case DIVE -> bypass ? 1f : 0.5f;
			case ARRIVE -> 0.4f;
			default -> 1f;
		};
		float dealt = amount * mult;
		hp -= dealt;
		entityData.set(D_HURT_UNTIL, tickCount + 6);
		Game game = Gz.game();
		game.addScore(Game.ScoreKind.MOTHRA_HIT, Math.max(1, Math.round(dealt * 4)), center());
		// a heavy burst of damage while she winds up for a dive knocks her out of it
		damageWindow += dealt;
		if (mode == Mode.DIVE_WIND && damageWindow > 110) {
			damageWindow = 0;
			Player g = target();
			stunVel = new Vec3(random.nextDouble() - 0.5, 0.3, random.nextDouble() - 0.5).normalize().scale(1.6);
			vel = stunVel;
			enter(Mode.STUNNED);
			if (g instanceof ServerPlayer sp) {
				GameModule.game().bonus("DIVE STOPPED!", "", 1000, center(), 0x66E8FF, 16);
			}
		}
		if (dealt >= 8 && hurtSoundCd == 0 && target() instanceof ServerPlayer sp) {
			hurtSoundCd = 30;
			GameAudio.at(sp, GodzillaSounds.MOTHRA_HURT, SoundSource.HOSTILE, center(), 1f, 0.95f + random.nextFloat() * 0.1f);
		}
		if (!enraged && hp < MAX_HP * 0.5f) {
			enraged = true;
			if (target() instanceof ServerPlayer sp) {
				GameAudio.at(sp, GodzillaSounds.MOTHRA_CRY, SoundSource.HOSTILE, center(), 1f, 0.8f);
				Gz.fx().shake(sp, 0.5f, 30);
			}
		}
		if (hp <= 0) {
			die();
		}
	}

	// ---- death ---------------------------------------------------------------------------------------------------------------

	private void die() {
		hp = 0;
		Player g = target();
		deathStart = center();
		Vec3 away = g == null ? new Vec3(0, 0, -1) : center().subtract(g.position());
		Vec3 flat = new Vec3(away.x, 0, away.z);
		flat = flat.lengthSqr() < 1 ? new Vec3(0, 0, -1) : flat.normalize();
		Vec3 base = g == null ? position() : g.position();
		crashPoint = new Vec3(base.x + flat.x * 42, Gz.city().data().groundY() + 1, base.z + flat.z * 42);
		deathRadius = Mth.clamp(new Vec3(deathStart.x - crashPoint.x, 0, deathStart.z - crashPoint.z).length(), 18, 46);
		deathAngle = Math.atan2(deathStart.z - crashPoint.z, deathStart.x - crashPoint.x);
		enter(Mode.DYING);
		if (g instanceof ServerPlayer sp) {
			GameAudio.at(sp, GodzillaSounds.MOTHRA_DEATH, SoundSource.HOSTILE, center(), 1f, 1f);
		}
	}

	private void dyingTick(ServerLevel sl) {
		final int total = 140;
		double s = Mth.clamp(modeTicks / (double) total, 0, 1);
		double rad = deathRadius * Math.pow(1 - s, 1.1);
		double ang = deathAngle + 3.6 * Math.PI * (s * (2 - s));
		double y = Mth.lerp(Math.pow(s, 1.7), deathStart.y, crashPoint.y + 4);
		setPos(crashPoint.x + rad * Math.cos(ang), y - BODY_HEIGHT / 2, crashPoint.z + rad * Math.sin(ang));
		face(new Vec3(-Math.sin(ang), 0, Math.cos(ang)));
		setYRot(getYRot() + (float) (s * 400));
		roll += (float) (0.25 + s * 0.7);
		roll = Mth.wrapDegrees((float) Math.toDegrees(roll)) * (float) (Math.PI / 180.0);
		pitch += (38f - pitch) * 0.05f;
		entityData.set(D_ROLL, roll);
		if (modeTicks % 6 == 0 && center().y < 100 && modeTicks > 40) {
			var game = GameModule.game();
			game.suppressScore(true);
			try {
				Gz.destruction().smashSphere(sl, center(), 8, new Vec3(0, -0.3, 0), Cause.MOTHRA);
			} finally {
				game.suppressScore(false);
			}
		}
		if (modeTicks >= total) {
			crash(sl);
		}
	}

	private void crash(ServerLevel sl) {
		var game = GameModule.game();
		setPos(crashPoint.x, crashPoint.y, crashPoint.z);
		setXRot(0);
		pitch = 0;
		roll = 0;
		entityData.set(D_ROLL, 0f);
		game.suppressScore(true);
		try {
			Gz.destruction().explode(sl, crashPoint.add(0, 5, 0), 16, Cause.MOTHRA);
			Gz.destruction().smashBox(sl, new AABB(crashPoint.x - 18, crashPoint.y + 1, crashPoint.z - 10, crashPoint.x + 18, crashPoint.y + 7, crashPoint.z + 10), Vec3.ZERO, Cause.MOTHRA);
			Gz.destruction().ignite(sl, crashPoint, 14);
		} finally {
			game.suppressScore(false);
		}
		Player g = target();
		if (g instanceof ServerPlayer sp) {
			Gz.fx().shake(sp, 1f, 60);
		}
		if (Gz.game().phase() == Game.Phase.MOTHRA) {
			game.bonus("MOTHRA DOWN!", "", 10000, crashPoint.add(0, 8, 0), 0xFFD23C, 26);
		}
		enter(Mode.DEAD);
		Gz.game().mothraDefeated();
	}

	// ---- misc ---------------------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
		return true;
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		MothraModule.removed(this);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	/** Used by the renderer: 0..1 of a hit flash still showing. */
	public float hurtFlash(float partialTicks) {
		float left = hurtUntil() - (tickCount + partialTicks);
		return Mth.clamp(left / 6f, 0f, 1f);
	}
}
