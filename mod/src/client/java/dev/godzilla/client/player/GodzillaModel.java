package dev.godzilla.client.player;

import dev.godzilla.player.GodzillaGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Godzilla body as boxes, limbs and plates, in the body frame (x = left, y = up, z = forward, origin between the feet,
 * blocks at scale 16). Everything dynamic comes in through {@link Params}; arms are two-bone IK from the shoulders to the hand
 * targets, the head sits at the head target and the tail follows the given joints.
 */
final class GodzillaModel {
	private GodzillaModel() {
	}

	static final class Params {
		double phase;
		/** 0..1 how much the legs swing (walking speed). */
		double amp;
		float mouth;
		/** 0..1 glow of the dorsal plates (and the mouth). */
		float glow;
		float flicker = 1.0F;
		Vec3 head = new Vec3(0, 25.9, 0.4);
		double headYawRel;
		double headPitch;
		final Vec3[] hand = new Vec3[2];
		final Vec3[] handDir = new Vec3[2];
		final Vec3[] handUp = new Vec3[2];
		Vec3[] tail;
		boolean firstPerson;
		float age;
	}

	private static final int WHITE = 0xFFFFFFFF;
	private static final double[] UP = {0, 1, 0};

	private static double[] d(double x, double y, double z) {
		return new double[]{x, y, z};
	}

	private static double[] d(Vec3 v) {
		return new double[]{v.x, v.y, v.z};
	}

	private static int tint(int v) {
		return 0xFF000000 | (v << 16) | (v << 8) | v;
	}

	static void build(Mesh m, Params p) {
		double sw = Math.sin(Math.PI * p.phase) * 0.55 * p.amp;
		double cosP = Math.cos(Math.PI * p.phase);
		double bobDy = -10.0 * (1 - Math.cos(Math.abs(sw)));
		m.push();
		m.translate(0, bobDy, 0);
		leg(m, false, -sw, p.amp * 0.9 * Math.max(0, cosP));
		leg(m, true, sw, p.amp * 0.9 * Math.max(0, -cosP));
		pelvisAndTorso(m, p);
		neckAndHead(m, p);
		for (int h = 0; h < 2; h++) {
			arm(m, p, h == 1);
		}
		plates(m, p);
		m.pop();
		tail(m, p, bobDy);
	}

	// ------------------------------------------------------------------ legs

	/** Hip at y 11.6; angle = forward swing in radians (positive forward). */
	private static void leg(Mesh m, boolean left, double swing, double bend) {
		double side = left ? 1 : -1;
		m.push();
		m.translate(side * 2.9, 11.6, -0.3);
		m.rotX(-swing);
		// thigh
		m.box(Mesh.SKIN, tint(215), 0, -2.6, 0.2, 4.9, 6.2, 5.4);
		m.push();
		m.translate(0, -5.6, 0.7);
		m.rotX(bend);
		// shin
		m.box(Mesh.SKIN, tint(200), 0, -2.4, -0.3, 4.1, 5.6, 4.4);
		m.push();
		m.translate(0, -4.8, -0.9);
		m.rotX(swing - bend);
		// foot
		m.box(Mesh.SKIN, tint(190), 0, -0.55, 2.2, 4.6, 1.7, 6.0);
		m.box(Mesh.SKIN, tint(205), 0, 0.2, -0.4, 3.8, 1.8, 3.6);
		for (int i = -1; i <= 1; i++) {
			m.limb(Mesh.BONE, WHITE, d(i * 1.5, -0.9, 5.0), d(i * 1.6, -1.15, 7.3), 1.2, 1.1, 0.25, 0.25, UP);
		}
		m.pop();
		m.pop();
		m.pop();
	}

	// ------------------------------------------------------------------ torso

	private static void pelvisAndTorso(Mesh m, Params p) {
		m.box(Mesh.SKIN, tint(205), 0, 11.4, -0.8, 8.6, 4.4, 6.6);
		m.push();
		m.translate(0, 12.8, -0.6);
		m.rotX(0.12 + 0.05 * p.amp);
		m.box(Mesh.SKIN, tint(225), 0, 4.9, 0.8, 9.4, 9.6, 7.0);
		m.box(Mesh.BELLY, tint(235), 0, 4.2, 4.35, 6.4, 7.4, 1.0);
		m.box(Mesh.SKIN, tint(235), 0, 8.6, 0.6, 12.4, 3.4, 5.6);
		m.pop();
	}

	// ------------------------------------------------------------------ neck and head

	private static void neckAndHead(Mesh m, Params p) {
		Vec3 h = p.head;
		if (!p.firstPerson) {
			m.limb(Mesh.SKIN, tint(215), d(0, 21.4, -0.3), d(h.x, h.y - 1.6, h.z - 1.0), 4.8, 4.4, 4.0, 3.8, UP);
		}
		m.push();
		m.translate(h.x, h.y, h.z);
		m.rotY(-p.headYawRel);
		m.rotX(p.headPitch);
		if (!p.firstPerson) {
			// skull, brow, snout
			m.box(Mesh.SKIN, tint(230), 0, -0.5, -1.5, 5.4, 4.2, 5.0);
			m.box(Mesh.SKIN, tint(170), 1.9, 1.1, 0.7, 2.2, 0.8, 1.9);
			m.box(Mesh.SKIN, tint(170), -1.9, 1.1, 0.7, 2.2, 0.8, 1.9);
			m.box(Mesh.EYE, 0xFFFFFFFF, 1.85, 0.2, 1.05, 1.0, 0.55, 0.3);
			m.box(Mesh.EYE, 0xFFFFFFFF, -1.85, 0.2, 1.05, 1.0, 0.55, 0.3);
			m.limb(Mesh.SKIN, tint(225), d(0, -1.1, 0.9), d(0, -1.3, 5.5), 3.9, 2.1, 2.9, 1.6, UP);
			m.box(Mesh.SKIN, tint(150), 0.6, -0.55, 5.3, 0.5, 0.4, 0.4);
			m.box(Mesh.SKIN, tint(150), -0.6, -0.55, 5.3, 0.5, 0.4, 0.4);
			m.box(Mesh.MOUTH, WHITE, 0, -2.0, 3.1, 3.0, 0.3, 4.4);
			for (int i = 0; i < 6; i++) {
				double z = 1.5 + i * 0.75;
				double x = 1.6 - i * 0.07;
				m.limb(Mesh.BONE, WHITE, d(x, -2.0, z), d(x, -2.9, z), 0.55, 0.5, 0.12, 0.12, UP);
				m.limb(Mesh.BONE, WHITE, d(-x, -2.0, z), d(-x, -2.9, z), 0.55, 0.5, 0.12, 0.12, UP);
			}
		}
		// lower jaw (the part you may see when looking down)
		m.push();
		m.translate(0, -2.2, 0.3);
		m.rotX(p.mouth * 0.65);
		m.box(Mesh.SKIN, tint(205), 0, -0.45, 2.5, 3.2, 0.9, 4.8);
		m.box(Mesh.MOUTH, WHITE, 0, 0.05, 2.5, 2.6, 0.2, 4.2);
		for (int i = 0; i < 5; i++) {
			double z = 1.3 + i * 0.8;
			double x = 1.4 - i * 0.06;
			m.limb(Mesh.BONE, WHITE, d(x, 0.0, z), d(x, 0.8, z), 0.5, 0.45, 0.1, 0.1, UP);
			m.limb(Mesh.BONE, WHITE, d(-x, 0.0, z), d(-x, 0.8, z), 0.5, 0.45, 0.1, 0.1, UP);
		}
		m.pop();
		if (p.glow > 0.05F) {
			int a = (int) (Mth.clamp(p.glow * p.flicker, 0, 1) * 255);
			m.box(Mesh.GLOW, (a << 24) | 0x00B8DCFF, 0, -1.65, 3.0, 2.2, 0.3, 3.4);
		}
		m.pop();
	}

	// ------------------------------------------------------------------ arms

	private static void arm(Mesh m, Params p, boolean left) {
		int h = left ? 1 : 0;
		double side = left ? 1 : -1;
		Vec3 sh = new Vec3(side * GodzillaGeometry.SHOULDER_X, GodzillaGeometry.SHOULDER_Y, GodzillaGeometry.SHOULDER_Z);
		Vec3 hand = p.hand[h];
		Vec3 dir = p.handDir[h];
		Vec3 up = p.handUp[h];
		if (hand == null) {
			return;
		}
		Vec3 wrist = hand.subtract(dir.scale(1.5));
		Vec3 elbow = GodzillaGeometry.elbow(sh, wrist, GodzillaGeometry.UPPER_ARM, GodzillaGeometry.FORE_ARM, new Vec3(side * 0.9, -0.6, -0.5));
		m.box(Mesh.SKIN, tint(225), sh.x, sh.y, sh.z, 4.2, 3.8, 4.2);
		m.limb(Mesh.SKIN, tint(215), d(sh), d(elbow), 3.4, 3.4, 2.9, 2.9, d(0, 0, -1));
		m.box(Mesh.SKIN, tint(205), elbow.x, elbow.y, elbow.z, 3.0, 3.0, 3.0);
		m.limb(Mesh.SKIN, tint(205), d(elbow), d(wrist), 2.9, 2.9, 2.3, 2.1, d(0, 0, -1));
		// palm and three claws
		Vec3 palmEnd = wrist.add(dir.scale(2.6));
		Vec3 across = dir.cross(up);
		if (across.lengthSqr() < 1e-6) {
			across = new Vec3(1, 0, 0);
		}
		across = across.normalize();
		Vec3 upn = across.cross(dir).normalize();
		m.limb(Mesh.SKIN, tint(215), d(wrist), d(palmEnd), 2.5, 1.9, 2.9, 1.5, d(upn));
		for (int i = -1; i <= 1; i++) {
			Vec3 base = palmEnd.add(across.scale(i * 0.95)).add(upn.scale(-0.1));
			Vec3 tip = base.add(dir.scale(2.2 - Math.abs(i) * 0.25)).add(upn.scale(-0.9));
			m.limb(Mesh.BONE, WHITE, d(base), d(tip), 0.95, 0.9, 0.12, 0.12, d(upn));
		}
		Vec3 tb = wrist.add(dir.scale(1.2)).add(across.scale(1.2 * (left ? -1 : 1))).add(upn.scale(-0.3));
		m.limb(Mesh.BONE, WHITE, d(tb), d(tb.add(dir.scale(1.5)).add(across.scale((left ? -1 : 1) * 0.6)).add(upn.scale(-0.5))), 0.8, 0.8, 0.12, 0.12, d(upn));
	}

	// ------------------------------------------------------------------ plates

	private static void plates(Mesh m, Params p) {
		// the lean of the chest tips the back slightly; the plates are drawn in the unleaned frame, which is close enough
		int glowA = (int) (Mth.clamp(p.glow * p.flicker, 0, 1) * 255);
		double[][] rows = {
				{0, 14.6, -3.9, 3.0, 4.6, 1.0},
				{0, 18.0, -4.0, 3.4, 5.2, 1.1},
				{0, 21.4, -3.6, 3.0, 4.4, 1.0},
				{2.7, 16.4, -3.6, 2.4, 3.2, 0.9},
				{-2.7, 16.4, -3.6, 2.4, 3.2, 0.9},
				{3.0, 20.0, -3.2, 2.1, 2.9, 0.9},
				{-3.0, 20.0, -3.2, 2.1, 2.9, 0.9},
		};
		double[] out = d(0, 0.3, -1);
		double[] along = d(0, 1, 0);
		for (double[] r : rows) {
			m.plate(Mesh.PLATE, tint(235), d(r[0], r[1], r[2]), norm(out), along, r[3], r[4], r[5]);
			if (glowA > 4) {
				m.plate(Mesh.GLOW, (glowA << 24) | 0x00B8DCFF, d(r[0], r[1], r[2] - 0.05), norm(out), along, r[3] * 1.06, r[4] * 1.06, r[5] * 1.35);
			}
		}
		// neck plates follow the neck
		Vec3 neckB = new Vec3(0, 21.4, -0.3);
		Vec3 neckT = p.head.add(0, -1.6, -1.0);
		for (int i = 0; i < 2; i++) {
			Vec3 q = neckB.lerp(neckT, 0.35 + 0.3 * i).add(0, 0.2, -2.0);
			m.plate(Mesh.PLATE, tint(235), d(q), d(0, 0.5, -1), d(0, 1, 0), 2.2, 3.0, 0.9);
		}
	}

	private static double[] norm(double[] v) {
		double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[]{v[0] / l, v[1] / l, v[2] / l};
	}

	// ------------------------------------------------------------------ tail

	private static void tail(Mesh m, Params p, double bobDy) {
		Vec3[] j = p.tail;
		if (j == null) {
			return;
		}
		int glowA = (int) (Mth.clamp(p.glow * p.flicker, 0, 1) * 255);
		for (int i = 0; i < GodzillaGeometry.TAIL_SEGMENTS; i++) {
			double w0 = i == 0 ? 5.6 : GodzillaGeometry.TAIL_THICK[i];
			double w1 = i + 1 < GodzillaGeometry.TAIL_SEGMENTS ? GodzillaGeometry.TAIL_THICK[i + 1] : 0.3;
			Vec3 a = j[i].add(0, bobDy, 0);
			Vec3 b = j[i + 1].add(0, bobDy, 0);
			m.limb(Mesh.SKIN, tint(200 + (i % 2) * 15), d(a), d(b), w0, w0 * 0.95, w1, w1 * 0.95, UP);
			if (i >= 1) {
				Vec3 seg = b.subtract(a).normalize();
				Vec3 mid = a.lerp(b, 0.5).add(0, w0 * 0.45, 0);
				double h = 1.6 + (7 - i) * 0.38;
				m.plate(Mesh.PLATE, tint(235), d(mid), d(0, 1, 0), d(seg.scale(-1)), 3.0, h, 0.9);
				if (glowA > 4) {
					m.plate(Mesh.GLOW, (glowA << 24) | 0x00B8DCFF, d(mid.add(0, 0.05, 0)), d(0, 1, 0), d(seg.scale(-1)), 3.18, h * 1.06, 1.2);
				}
			}
		}
	}
}
