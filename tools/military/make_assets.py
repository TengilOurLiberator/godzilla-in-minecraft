"""Military module assets: box models (JSON) and their textures (PNG). Re-run any time:

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\military\\make_assets.py

Writes, under mod/src/client/resources/assets/godzilla/:
    mil/<model>.json          the part tree read by dev.godzilla.client.military.MilModels
    textures/mil/<model>.png  the texture (every box gets its own UV rectangle, packed here)

Model conventions (this file is the single source of truth for geometry AND uv, Java only animates named parts):
  * units are pixels; 16 px = 1 block, ships use 4 px = 1 block ("ppb")
  * x right, y UP, z BACK. The FRONT of every unit is -z. y = 0 is the ground / waterline. Converted to the model's y-down
    convention when writing the JSON.
  * a part has a pivot (relative to its parent's pivot; root children relative to the ground origin), an optional rotation
    in degrees and boxes given as [x, y, z, w, h, d] with (x, y, z) the minimum corner relative to the pivot
  * animated by name in Java: turret* (yaw), barrel* (pitch), rotor (spin about y), tail_rotor (spin about x)
Nothing here is taken from any other game or film: flat colours with hashed noise, panel lines and camouflage blotches.
"""
import json, math, os, struct, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "mod", "src", "client", "resources", "assets", "godzilla")

# ---------------------------------------------------------------------------------------------------------- materials
MATS = {
    # name: (base rgb, pattern)
    "camo": ((84, 94, 56), "camo"),
    "camo_dark": ((56, 64, 40), "camo"),
    "grey": ((138, 146, 152), "panels"),
    "grey_dark": ((92, 98, 104), "panels"),
    "hull": ((120, 128, 134), "seams"),
    "hull_red": ((92, 44, 40), "plain"),
    "deck": ((150, 138, 100), "planks"),
    "funnel": ((86, 90, 96), "panels"),
    "turret": ((104, 112, 118), "panels"),
    "black": ((30, 32, 34), "plain"),
    "track": ((38, 40, 38), "track"),
    "wheel": ((26, 26, 28), "track"),
    "glass": ((70, 104, 130), "glass"),
    "metal": ((170, 174, 178), "plain"),
    "dish": ((226, 230, 232), "dish"),
    "rotor": ((46, 50, 52), "rotor"),
    "missile": ((226, 226, 218), "missile"),
    "red": ((200, 40, 30), "plain"),
    "warn": ((230, 190, 40), "warn"),
    "glow": ((255, 200, 90), "plain"),
}


def hnoise(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, cell, seed):
    """Smooth-ish value noise with the given cell size in pixels."""
    gx, gy = x / cell, y / cell
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = hnoise(x0, y0, seed); b = hnoise(x0 + 1, y0, seed)
    c = hnoise(x0, y0 + 1, seed); d = hnoise(x0 + 1, y0 + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def shade(rgb, k):
    return tuple(max(0, min(255, int(c * k))) for c in rgb)


def pixel(mat, fx, fy, fw, fh, face, gx, gy):
    """Colour of pixel (fx, fy) of a face of size fw x fh; (gx, gy) are atlas coordinates (for noise)."""
    base, pat = MATS[mat]
    k = {"top": 1.12, "bottom": 0.62, "front": 0.95, "back": 0.82, "left": 0.9, "right": 0.9}[face]
    n = 0.92 + 0.16 * hnoise(gx, gy, 7)
    rgb = base
    if pat == "camo":
        v = vnoise(gx, gy, 5, 3)
        if v > 0.62:
            rgb = shade(base, 0.62)
        elif v < 0.3:
            rgb = (min(255, base[0] + 18), min(255, base[1] + 16), base[2] - 6)
        if fx == 0 or fy == 0:
            rgb = shade(rgb, 0.8)
    elif pat == "panels":
        if fx % 8 == 0 or fy % 8 == 0:
            rgb = shade(base, 0.72)
    elif pat == "seams":
        if fy % 5 == 0:
            rgb = shade(base, 0.74)
        if fx % 16 == 0:
            rgb = shade(base, 0.82)
    elif pat == "planks":
        if fy % 3 == 0:
            rgb = shade(base, 0.8)
    elif pat == "track":
        if fx % 3 == 0:
            rgb = shade(base, 1.7)
    elif pat == "glass":
        rgb = shade(base, 0.85 + 0.5 * (1 - fy / max(1, fh)))
    elif pat == "dish":
        r = math.hypot(fx - fw / 2, fy - fh / 2)
        if int(r) % 4 == 0:
            rgb = shade(base, 0.8)
    elif pat == "rotor":
        if fx < 4 or fx > fw - 5:
            rgb = (230, 190, 40)
    elif pat == "missile":
        if 3 <= fy % 12 <= 4:
            rgb = (190, 40, 30)
    elif pat == "warn":
        rgb = base if (fx + fy) // 3 % 2 == 0 else (30, 30, 30)
    return shade(rgb, k * n)


# ---------------------------------------------------------------------------------------------------------- png
def write_png(path, w, h, px):
    rows = b"".join(b"\x00" + b"".join(bytes(px[y][x]) + b"\xff" for x in range(w)) for y in range(h))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)


# ---------------------------------------------------------------------------------------------------------- model
class Model:
    def __init__(self, name, ppb=16, ground=24, tex_w=128):
        self.name, self.ppb, self.ground, self.tex_w = name, ppb, ground, tex_w
        self.parts = []

    def part(self, name, boxes, pivot=(0, 0, 0), rot=(0, 0, 0), parent="root"):
        self.parts.append({"name": name, "parent": parent, "pivot": pivot, "rot": rot, "boxes": boxes})

    def emit(self):
        # shelf packing of every box net: width 2d+2w, height d+h
        nets = []
        for p in self.parts:
            for b in p["boxes"]:
                x, y, z, w, h, d = b[:6]
                nets.append((int(2 * d + 2 * w), int(d + h), b))
        order = sorted(range(len(nets)), key=lambda i: -nets[i][1])
        x = y = shelf = 0
        place = {}
        for i in order:
            nw, nh, _ = nets[i]
            if x + nw > self.tex_w:
                x, y, shelf = 0, y + shelf, 0
            place[i] = (x, y)
            x += nw
            shelf = max(shelf, nh)
        tex_h = 1
        while tex_h < y + shelf:
            tex_h *= 2
        px = [[(0, 0, 0)] * self.tex_w for _ in range(tex_h)]
        out_parts, i = [], 0
        for p in self.parts:
            ob = []
            for b in p["boxes"]:
                x0, y0, z0, w, h, d, mat = b
                u, v = place[i]
                self.paint(px, u, v, int(w), int(h), int(d), mat)
                ob.append({"o": [x0, -(y0 + h), z0], "s": [w, h, d], "uv": [u, v]})
                i += 1
            piv = list(p["pivot"])
            piv[1] = (self.ground - piv[1]) if p["parent"] == "root" else -piv[1]
            out_parts.append({"name": p["name"], "parent": p["parent"], "pivot": piv,
                              "rot": [math.radians(r) for r in p["rot"]], "boxes": ob})
        doc = {"texW": self.tex_w, "texH": tex_h, "scale": 16.0 / self.ppb, "ground": self.ground, "parts": out_parts}
        os.makedirs(os.path.join(ASSETS, "mil"), exist_ok=True)
        json.dump(doc, open(os.path.join(ASSETS, "mil", self.name + ".json"), "w"), indent=1)
        write_png(os.path.join(ASSETS, "textures", "mil", self.name + ".png"), self.tex_w, tex_h, px)
        print("%-12s %3d parts %3d boxes texture %dx%d" % (self.name, len(self.parts), len(nets), self.tex_w, tex_h))

    @staticmethod
    def paint(px, u, v, w, h, d, mat):
        # vanilla cube net: top (u+d, v) w*d; bottom (u+d+w, v); left (u, v+d) d*h; front (u+d, v+d) w*h; right (u+d+w, v+d) d*h;
        # back (u+2d+w, v+d) w*h
        faces = [("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d), ("left", u, v + d, d, h),
                 ("front", u + d, v + d, w, h), ("right", u + d + w, v + d, d, h), ("back", u + 2 * d + w, v + d, w, h)]
        for face, fu, fv, fw, fh in faces:
            for yy in range(fh):
                for xx in range(fw):
                    px[fv + yy][fu + xx] = pixel(mat, xx, yy, fw, fh, face, fu + xx, fv + yy)


# ---------------------------------------------------------------------------------------------------------- units
def tank():
    m = Model("tank")
    m.part("hull", [(-7, 2, -15, 14, 5, 30, "camo"), (-6, 6, -17, 12, 3, 4, "camo"), (-6, 3, 15, 12, 4, 2, "camo_dark"),
                    (-5, 7, 3, 10, 1, 10, "camo_dark"), (-11, 0, -16, 4, 6, 32, "track"), (7, 0, -16, 4, 6, 32, "track"),
                    (-11, 5, -15, 4, 2, 30, "camo_dark"), (7, 5, -15, 4, 2, 30, "camo_dark")])
    m.part("turret", [(-5, 0, -7, 10, 4, 13, "camo"), (1, 4, 0, 3, 1, 3, "black"), (-4, 4, 6, 1, 9, 1, "black")], pivot=(0, 7, 2))
    m.part("barrel", [(-1, -1, -15, 2, 2, 15, "black"), (-2, -2, -17, 4, 4, 2, "black")], pivot=(0, 2, -7), parent="turret")
    m.emit()


def heli():
    m = Model("heli", tex_w=256)
    m.part("body", [(-6, 5, -12, 12, 10, 20, "camo"), (-5, 6, -17, 10, 8, 5, "camo"), (-4, 3, -10, 8, 2, 16, "camo_dark"),
                    (-4, 15, -4, 8, 3, 10, "camo_dark"), (-2, 9, 8, 4, 4, 24, "camo"), (-1, 9, 29, 2, 10, 5, "camo"),
                    (-6, 10, 26, 12, 1, 4, "camo"), (-5, 9, -18, 10, 5, 2, "glass"), (-1, 15, -3, 2, 3, 2, "black"),
                    (-9, 0, -9, 2, 1, 18, "black"), (7, 0, -9, 2, 1, 18, "black"), (-8, 1, -5, 1, 4, 1, "black"),
                    (-8, 1, 3, 1, 4, 1, "black"), (7, 1, -5, 1, 4, 1, "black"), (7, 1, 3, 1, 4, 1, "black"),
                    (-14, 7, -4, 28, 1, 5, "camo_dark"), (-15, 5, -6, 3, 3, 9, "camo"), (12, 5, -6, 3, 3, 9, "camo")])
    m.part("rotor", [(-34, 0, -1, 68, 1, 2, "rotor"), (-1, 0, -34, 2, 1, 68, "rotor")], pivot=(0, 18, -2))
    m.part("tail_rotor", [(0, -6, -1, 1, 12, 2, "rotor"), (0, -1, -6, 1, 2, 12, "rotor")], pivot=(3, 14, 31))
    m.emit()


def jet():
    m = Model("jet", tex_w=256)
    m.part("body", [(-3, 2, -22, 6, 6, 40, "grey"), (-2, 3, -29, 4, 4, 7, "grey"), (-1, 4, -33, 2, 2, 4, "black"),
                    (-4, 1, -14, 8, 2, 8, "grey_dark"), (-2, 8, -16, 4, 2, 10, "glass"), (-2, 8, -6, 4, 2, 20, "grey_dark"),
                    (-2, 3, 18, 4, 4, 3, "black"), (-10, 1, -2, 1, 1, 8, "metal"), (9, 1, -2, 1, 1, 8, "metal")])
    m.part("wing_l", [(0, 0, 0, 18, 1, 12, "grey")], pivot=(3, 3, -4), rot=(0, 32, 0))
    m.part("wing_r", [(-18, 0, 0, 18, 1, 12, "grey")], pivot=(-3, 3, -4), rot=(0, -32, 0))
    m.part("tail_l", [(0, 0, 0, 9, 1, 6, "grey")], pivot=(3, 4, 12), rot=(0, 28, 0))
    m.part("tail_r", [(-9, 0, 0, 9, 1, 6, "grey")], pivot=(-3, 4, 12), rot=(0, -28, 0))
    m.part("fin_l", [(0, 0, 0, 1, 9, 8, "grey_dark")], pivot=(2, 7, 10), rot=(0, 0, -12))
    m.part("fin_r", [(-1, 0, 0, 1, 9, 8, "grey_dark")], pivot=(-2, 7, 10), rot=(0, 0, 12))
    m.emit()


def maser():
    m = Model("maser", tex_w=256)
    boxes = [(-8, 3, -22, 16, 4, 44, "camo_dark"), (-7, 7, -22, 14, 8, 11, "camo"), (-6, 10, -23, 12, 3, 1, "glass"),
             (-7, 7, -8, 14, 3, 30, "camo"), (-6, 10, 4, 12, 5, 14, "grey"), (-8, 3, 22, 16, 2, 1, "warn")]
    for z in (-16, -2, 12):
        boxes += [(-10, 0, z, 3, 6, 6, "wheel"), (7, 0, z, 3, 6, 6, "wheel")]
    m.part("body", boxes)
    m.part("turret", [(-4, 0, -4, 8, 5, 8, "grey")], pivot=(0, 15, 6))
    m.part("barrel", [(-1, -1, -14, 2, 2, 14, "metal"), (-12, -12, -16, 24, 24, 2, "dish"), (-12, 11, -19, 24, 1, 3, "metal"),
                      (-12, -12, -19, 24, 1, 3, "metal"), (-12, -11, -19, 1, 22, 3, "metal"), (11, -11, -19, 1, 22, 3, "metal"),
                      (-1, -1, -24, 2, 2, 8, "red")], pivot=(0, 6, 0), parent="turret")
    m.emit()


def ship(name, length_blocks, beam_px, turret_z):
    L = length_blocks * 4
    B = beam_px
    h = B // 2
    m = Model(name, ppb=4, ground=24, tex_w=256)
    bz = -L // 2 + int(0.3 * L)
    boxes = [(-h, -3, -L // 2 + 12, B, 3, L - 12, "hull_red"), (-h, 0, -L // 2 + 12, B, 6, L - 12, "hull"),
             (-h + 1, 6, -L // 2 + 12, B - 2, 1, L - 12, "deck"),
             # bow in three narrowing steps, rising
             (-h + 1, -3, -L // 2 + 7, B - 2, 11, 5, "hull"), (-h + 2, -2, -L // 2 + 3, B - 4, 11, 4, "hull"),
             (-max(1, h - 4), 0, -L // 2, max(2, B - 8), 9, 3, "hull"),
             # bridge, mast, radar, funnels
             (-h + 2, 7, bz, B - 4, 4, 9, "grey"), (-h + 3, 11, bz + 1, B - 6, 3, 6, "grey_dark"), (0, 14, bz + 3, 1, 8, 1, "black"),
             (-3, 22, bz + 2, 6, 1, 3, "black"), (-2, 7, bz + 12, 4, 6, 4, "funnel"), (-2, 13, bz + 12, 4, 1, 4, "black")]
    if L >= 64:
        boxes += [(-2, 7, bz + 18, 4, 5, 4, "funnel"), (-2, 12, bz + 18, 4, 1, 4, "black")]
    m.part("hull", boxes)
    for i, z in enumerate(turret_z(L)):
        m.part("turret_%d" % i, [(-3, 0, -3, 6, 2, 6, "turret")], pivot=(0, 7, z))
        m.part("barrel_%d" % i, [(-2, 0, -10, 1, 1, 10, "black"), (1, 0, -10, 1, 1, 10, "black")], pivot=(0, 1, -3),
               parent="turret_%d" % i)
    m.emit()


def projectiles():
    m = Model("glow", ppb=16, ground=0, tex_w=32)
    m.part("streak", [(-1, -1, -1, 2, 2, 2, "glow")])
    m.emit()
    m = Model("missile", ppb=16, ground=0, tex_w=64)
    m.part("body", [(-1, -1, -10, 3, 3, 20, "missile"), (0, 0, -12, 1, 1, 2, "red"), (-4, 0, 6, 9, 1, 3, "camo_dark"),
                    (0, -4, 6, 1, 9, 3, "camo_dark")])
    m.emit()
    # glow texture: a soft white-hot core to orange edge (used by the emissive streak)
    # (the pngs must keep the model's size 32x4 or the uv scale breaks): a white-hot core fading to the edge colour
    for name, edge in (("glow", (255, 130, 40)), ("glow_blue", (90, 150, 255))):
        if name != "glow":
            mm = Model(name, ppb=16, ground=0, tex_w=32)
            mm.part("streak", [(-1, -1, -1, 2, 2, 2, "glow")])
            mm.emit()
        px = [[(255, 255, 255)] * 32 for _ in range(4)]
        for y in range(4):
            for x in range(32):
                r = min(1.0, (min(1.0, abs(x - 15.5) / 16.0) * 0.5 + abs(y - 1.5) / 3.0) * 0.9)
                px[y][x] = tuple(int(255 + (edge[i] - 255) * r) for i in range(3))
        write_png(os.path.join(ASSETS, "textures", "mil", name + ".png"), 32, 4, px)


if __name__ == "__main__":
    tank(); heli(); jet(); maser()
    ship("ship_s", 12, 7, lambda L: [-L // 2 + 14, L // 2 - 8])
    ship("ship_m", 16, 9, lambda L: [-L // 2 + 14, -L // 2 + 22, L // 2 - 10])
    ship("ship_l", 20, 12, lambda L: [-L // 2 + 14, -L // 2 + 23, L // 2 - 12, L // 2 - 21])
    projectiles()
