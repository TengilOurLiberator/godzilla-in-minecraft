package dev.godzilla.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Server-side events between modules. Register listeners in a module's init(); they are called on the server thread. */
public final class GzEvents {
	public static final class Event<T> {
		private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

		public void register(Consumer<T> listener) {
			listeners.add(listener);
		}

		public void fire(T value) {
			for (Consumer<T> l : listeners) {
				l.accept(value);
			}
		}
	}

	public record PhaseChange(Game.Phase from, Game.Phase to) {
	}

	public record BlocksSmashed(Vec3 center, int count, Cause cause) {
	}

	/** remaining: the building's integrity when it was declared destroyed. */
	public record BuildingDestroyed(CityData.Building building, Cause lastCause) {
	}

	/** A mass of blocks came down: centre of where it lands, how many blocks. */
	public record Collapse(Vec3 center, int blocks, CityData.Building building) {
	}

	/**
	 * A new run is about to start, or the run was stopped: every module discards its entities, timers and per-run state.
	 * Fired before the city is restamped.
	 */
	public static final Event<Game> RESET = new Event<>();
	public static final Event<PhaseChange> PHASE_CHANGED = new Event<>();
	public static final Event<BlocksSmashed> BLOCKS_SMASHED = new Event<>();
	public static final Event<Collapse> COLLAPSE = new Event<>();
	/** Fired once per building, when its integrity first drops below the destroyed threshold. */
	public static final Event<BuildingDestroyed> BUILDING_DESTROYED = new Event<>();
	/** The player roared (people panic, units flinch). */
	public static final Event<ServerPlayer> ROAR = new Event<>();

	private GzEvents() {
	}
}
