package dev.godzilla.destruction;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lasting visual fires. Not fire blocks: the server only keeps a capped list of burning places (centre, radius, time to live)
 * and tells the clients, which run cheap looping flame and smoke particles and the fire.loop sound around them.
 */
final class Fires {
	static final int CAP = 40;

	private static final class Site {
		int id;
		ServerLevel level;
		Vec3 pos;
		double radius;
		long endTick;
	}

	private static final List<Site> SITES = new ArrayList<>();
	private static int nextId = 1;

	private Fires() {
	}

	static int count() {
		return SITES.size();
	}

	static void reset() {
		SITES.clear();
		nextId = 1;
	}

	static void add(ServerLevel level, Vec3 pos, double radius, int ttl) {
		long now = level.getGameTime();
		// merge into an existing site that is close by
		for (Site s : SITES) {
			if (s.level == level && s.pos.distanceToSqr(pos) < Math.pow(Math.max(2, (s.radius + radius) * 0.5), 2)) {
				s.endTick = Math.max(s.endTick, now + ttl);
				if (radius > s.radius) {
					s.radius = radius;
					send(level, new Packets.Fire(0, s.id, (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, (float) s.radius, (int) (s.endTick - now)));
				}
				return;
			}
		}
		if (SITES.size() >= CAP) {
			Site oldest = SITES.get(0);
			for (Site s : SITES) {
				if (s.endTick < oldest.endTick) {
					oldest = s;
				}
			}
			remove(oldest);
		}
		Site s = new Site();
		s.id = nextId++;
		s.level = level;
		s.pos = pos;
		s.radius = radius;
		s.endTick = now + ttl;
		SITES.add(s);
		send(level, new Packets.Fire(0, s.id, (float) pos.x, (float) pos.y, (float) pos.z, (float) radius, ttl));
	}

	private static void remove(Site s) {
		SITES.remove(s);
		send(s.level, new Packets.Fire(1, s.id, 0, 0, 0, 0, 0));
	}

	static void tick(long now) {
		for (int i = SITES.size() - 1; i >= 0; i--) {
			Site s = SITES.get(i);
			if (now >= s.endTick) {
				remove(s);
			}
		}
	}

	/** A player who joins mid-fire gets the current list. */
	static void sendAll(ServerPlayer p) {
		for (Site s : SITES) {
			ServerPlayNetworking.send(p, new Packets.Fire(0, s.id, (float) s.pos.x, (float) s.pos.y, (float) s.pos.z, (float) s.radius,
					(int) Math.max(1, s.endTick - s.level.getGameTime())));
		}
	}

	private static void send(ServerLevel level, Packets.Fire f) {
		for (ServerPlayer p : level.players()) {
			ServerPlayNetworking.send(p, f);
		}
	}

	static void clearClients(ServerLevel level) {
		send(level, new Packets.Fire(2, 0, 0, 0, 0, 0, 0));
	}
}
