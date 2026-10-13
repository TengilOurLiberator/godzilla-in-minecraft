package dev.godzilla.destruction;

import dev.godzilla.api.Cause;
import dev.godzilla.api.Game;
import dev.godzilla.api.Gz;
import dev.godzilla.core.GodzillaSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Industrial fuel tanks (and gas spheres): damage one and it goes up in a chain of blasts, which in turn damage the tanks next
 * to it. A tank is recognised by the building label "Fuel tank" / "Gas sphere" in tokyo.json (the city generator writes
 * "label" for named non-landmark structures), see Structures.ensureCity.
 */
final class FuelTanks {
	private FuelTanks() {
	}

	static void blow(ServerLevel level, Struct s, long tick) {
		if (s.fuelBlown) {
			return;
		}
		s.fuelBlown = true;
		Vec3 c = new Vec3((s.x0 + s.x1 + 1) / 2.0, (s.y0 + s.y1 + 1) / 2.0, (s.z0 + s.z1 + 1) / 2.0);
		double r = Math.max(5, Math.min(12, Math.max(s.sx(), s.sz()) * 0.75));
		int value = s.building != null ? s.building.value() : 100;
		DestructionService svc = (DestructionService) Gz.destruction();
		// ignition at the base, then the big one a few ticks later, then the secondary blasts
		Collapses.schedule(tick + 2, () -> {
			svc.explode(level, new Vec3(c.x, s.y0 + 1, c.z), r * 0.6, Cause.EXPLOSION);
			DestructionService.sound(level, c, GodzillaSounds.EXPLOSION_FUEL, 15f, 1f, 50, true);
		});
		Collapses.schedule(tick + 6, () -> {
			svc.explode(level, c, r, Cause.EXPLOSION);
			DestructionService.broadcast(level, c, new Packets.Fx(Packets.Fx.EXPLOSION, (float) c.x, (float) c.y, (float) c.z, (float) r * 1.6f, 1, 0), 700);
			DestructionService.broadcast(level, c, new Packets.Fx(Packets.Fx.SMOKE_COLUMN, (float) c.x, (float) s.y0, (float) c.z, (float) (r * 5), (float) r, 0), 700);
			Gz.game().addScore(Game.ScoreKind.FUEL_TANK, value, c);
			Collapses.shake(level, c, 0.8f, 60);
			Fires.add(level, c, r * 1.1, 1500);
		});
		for (int i = 0; i < 3; i++) {
			final int k = i;
			Collapses.schedule(tick + 12 + i * 5, () -> {
				double a = k * 2.1 + level.getRandom().nextDouble();
				Vec3 at = new Vec3(c.x + Math.cos(a) * r * 0.8, c.y + k * 2, c.z + Math.sin(a) * r * 0.8);
				svc.explode(level, at, r * 0.5, Cause.EXPLOSION);
			});
		}
		// the neighbours catch it
		for (Struct o : Structures.all()) {
			if (o != s && o.fuel && !o.fuelBlown) {
				double dx = (o.x0 + o.x1 + 1) / 2.0 - c.x, dz = (o.z0 + o.z1 + 1) / 2.0 - c.z;
				if (dx * dx + dz * dz < 34 * 34) {
					Vec3 oc = new Vec3((o.x0 + o.x1 + 1) / 2.0, o.y0 + 3, (o.z0 + o.z1 + 1) / 2.0);
					Vec3 toward = c.subtract(oc).normalize();
					Vec3 edge = oc.add(toward.scale(Math.max(o.sx(), o.sz()) * 0.45));
					Collapses.schedule(tick + 18 + (long) (Math.sqrt(dx * dx + dz * dz) * 0.6), () -> svc.explode(level, edge, 3.5, Cause.EXPLOSION));
				}
			}
		}
	}
}
