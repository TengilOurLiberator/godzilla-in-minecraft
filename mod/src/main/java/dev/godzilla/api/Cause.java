package dev.godzilla.api;

/** What broke a block or hit an entity. */
public enum Cause {
	HAND, FOOT, BODY, TAIL, BEAM, EXPLOSION, COLLAPSE, DEBRIS, PROJECTILE, MOTHRA;

	/** True when Godzilla did it directly (counts toward the player's score and combo). */
	public boolean byGodzilla() {
		return this == HAND || this == FOOT || this == BODY || this == TAIL || this == BEAM;
	}
}
