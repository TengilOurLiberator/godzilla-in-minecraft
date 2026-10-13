package dev.godzilla.game;

import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Floating score numbers in the world: vanilla text display entities (so they also show in VR without any client code),
 * scaled up to be readable from 20 to 100 blocks by a player who is 16 times normal size. Thousands of block hits per second
 * become a few popups: points are collected per place and kind for a few ticks, then shown once.
 */
public final class Popups {
	/** Text display entity that is never written to the world save. */
	public static final class PopupText extends Display.TextDisplay {
		public PopupText(EntityType<?> type, Level level) {
			super(type, level);
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	private static final int COLLECT_TICKS = 9;
	private static final int LIFE_TICKS = 46;
	private static final int MAX_LIVE = 18;
	/** Master size factor of all popups (text display scale 1 is a quarter block per line). */
	public static final float SIZE = 4.0f;

	private record Key(int x, int y, int z, int group) {
	}

	private static final class Pop {
		long points;
		double sx, sy, sz;
		int n;
		int first;
	}

	private static final class Live {
		final PopupText entity;
		final int born;
		final float scale;
		final float rise;
		boolean moved;

		Live(PopupText entity, int born, float scale, float rise) {
			this.entity = entity;
			this.born = born;
			this.scale = scale;
			this.rise = rise;
		}
	}

	private final Map<Key, Pop> pending = new HashMap<>();
	private final List<Live> live = new ArrayList<>();

	public static final int[] GROUP_COLORS = {0xFFE9A0, 0xFFD23C, 0xFF9A3C, 0xFF6FA8, 0x58D6FF, 0xC48BFF, 0xFFE24A};

	/** group: 0 buildings, 1 landmarks, 2 vehicles, 3 people, 4 military, 5 Mothra, 6 bonus. */
	public void add(Vec3 where, int group, long points, int tick) {
		if (points <= 0) {
			return;
		}
		int cell = group == 0 ? 20 : 6;
		Key key = new Key(Mth.floor(where.x / cell), Mth.floor(where.y / cell), Mth.floor(where.z / cell), group);
		Pop p = pending.computeIfAbsent(key, k -> {
			Pop np = new Pop();
			np.first = tick;
			return np;
		});
		p.points += points;
		p.sx += where.x * points;
		p.sy += where.y * points;
		p.sz += where.z * points;
		p.n++;
	}

	/** A big headline popup that is shown at once (bonuses). */
	public void headline(ServerLevel level, Vec3 where, String text, String sub, int color, float size, int tick) {
		Component c = Component.literal(text).withColor(color);
		if (sub != null && !sub.isEmpty()) {
			c = Component.empty().append(c).append(Component.literal("\n" + sub).withColor(0xFFFFFF));
		}
		spawn(level, where.add(0, 8, 0), c, size * 2.2f, 24, tick);
	}

	public void tick(ServerLevel level, int tick) {
		if (!pending.isEmpty()) {
			Iterator<Map.Entry<Key, Pop>> it = pending.entrySet().iterator();
			int budget = 8;
			while (it.hasNext() && budget > 0) {
				Map.Entry<Key, Pop> e = it.next();
				Pop p = e.getValue();
				if (tick - p.first >= COLLECT_TICKS) {
					it.remove();
					budget--;
					if (p.points >= 10) {
						Vec3 at = new Vec3(p.sx / p.points, p.sy / p.points + 2, p.sz / p.points);
						float size = Mth.clamp(3.5f + 1.6f * (float) Math.log10(p.points + 1), 4.5f, 10f) * SIZE;
						spawn(level, at, Component.literal("+" + String.format("%,d", p.points)).withColor(GROUP_COLORS[e.getKey().group]), size, 16, tick);
					}
				}
			}
		}
		Iterator<Live> it = live.iterator();
		while (it.hasNext()) {
			Live l = it.next();
			int age = tick - l.born;
			if (l.entity.isRemoved()) {
				it.remove();
			} else if (age >= LIFE_TICKS) {
				l.entity.discard();
				it.remove();
			} else if (age >= 1 && !l.moved) {
				l.moved = true;
				l.entity.setTransformationInterpolationDelay(0);
				l.entity.setTransformationInterpolationDuration(LIFE_TICKS - 2);
				l.entity.setTransformation(new Transformation(new Vector3f(0, l.rise, 0), null, new Vector3f(l.scale * 0.55f), null));
			}
		}
	}

	private void spawn(ServerLevel level, Vec3 at, Component text, float scale, float rise, int tick) {
		while (live.size() >= MAX_LIVE) {
			live.removeFirst().entity.discard();
		}
		PopupText t = new PopupText(EntityTypes.TEXT_DISPLAY, level);
		t.setPos(at.x, at.y, at.z);
		t.setText(text);
		t.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		t.setBrightnessOverride(new Brightness(15, 15));
		t.setViewRange(5f);
		t.setBackgroundColor(0);
		t.setLineWidth(1000);
		t.setFlags((byte) (1 | 2)); // shadow, see through
		t.setTransformation(new Transformation(new Vector3f(0, 0, 0), null, new Vector3f(scale), null));
		level.addFreshEntity(t);
		live.add(new Live(t, tick, scale, rise));
		// pop in: grow to full size over the first ticks
		t.setTransformationInterpolationDelay(0);
		t.setTransformationInterpolationDuration(2);
	}

	public void clear() {
		pending.clear();
		for (Live l : live) {
			l.entity.discard();
		}
		live.clear();
	}
}

