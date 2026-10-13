package dev.godzilla.game;

import com.mojang.math.Transformation;
import dev.godzilla.api.Cause;
import dev.godzilla.api.Smashable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import dev.godzilla.game.Popups.PopupText;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The big floating "smash me" target that starts a run: shown to a player who has just joined, and after the results. An
 * invisible entity with a large hitbox (so hands, explosions and an ordinary attack click all count) carrying a text display.
 */
public class SmashSign extends Entity implements Smashable {
	public static final float SIZE = 1f;
	private @Nullable PopupText text;
	private float textScale = 6;
	private boolean restart;
	private int age2;

	public SmashSign(EntityType<?> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setup(boolean restart, float scale) {
		this.restart = restart;
		this.textScale = scale;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) {
			return;
		}
		age2++;
		if (text == null || text.isRemoved()) {
			createText();
		}
		if (text != null && age2 % 20 == 1) {
			float k = (age2 / 20) % 2 == 0 ? 1.0f : 1.14f;
			text.setTransformationInterpolationDelay(0);
			text.setTransformationInterpolationDuration(20);
			text.setTransformation(new Transformation(new Vector3f(0, 0, 0), null, new Vector3f(textScale * k), null));
		}
	}

	private void createText() {
		PopupText t = new PopupText(EntityTypes.TEXT_DISPLAY, level());
		Vec3 c = getBoundingBox().getCenter();
		t.setPos(c.x, c.y, c.z);
		Component line1 = Component.literal("SMASH ME").withColor(restart ? 0xFFD23C : 0x66E8FF);
		Component line2 = Component.literal(restart ? "TO PLAY AGAIN" : "TO BEGIN").withColor(0xFFFFFF);
		t.setText(Component.empty().append(line1).append("\n").append(line2));
		t.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		t.setBrightnessOverride(new Brightness(15, 15));
		t.setViewRange(5f);
		t.setLineWidth(1000);
		t.setBackgroundColor(0xC0101828);
		t.setFlags((byte) 3);
		t.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(textScale), null));
		level().addFreshEntity(t);
		text = t;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (text != null) {
			text.discard();
		}
		super.remove(reason);
	}

	@Override
	public void onSmashed(Cause cause, Vec3 impulse, float power) {
		if (cause != Cause.MOTHRA && level() instanceof ServerLevel) {
			GameModule.game().requestStart(null);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof ServerPlayer p) {
			GameModule.game().requestStart(p);
			return true;
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}

