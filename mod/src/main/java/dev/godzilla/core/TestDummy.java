package dev.godzilla.core;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Trivial test entity: stands still, never despawns. Exists to prove the entity + render state + model layer pattern.
 * {@code /summon godzilla:test_dummy}
 */
public class TestDummy extends Mob {
	public TestDummy(EntityType<? extends Mob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}
}
