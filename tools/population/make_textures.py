"""Generates the textures of the population module (people, cars, buses, trucks, trains).

Run:  D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\population\\make_textures.py
Writes mod/src/client/resources/assets/godzilla/textures/population/*.png

Everything is painted here (nothing is copied). The box UV layout is Minecraft's: for a box w x h x d at (u, v):
  top (u+d, v, w, d)      bottom (u+d+w, v, w, d)
  right (u, v+d, d, h)    front (u+d, v+d, w, h)    left (u+d+w, v+d, d, h)    back (u+2d+w, v+d, w, h)
The model front is the -Z side. Side faces are painted with k = distance from the front (0 = front end); the top face
has the front at its last row.
The Java models (dev.godzilla.client.population.*Model) use exactly these box sizes and texture offsets.
"""
import os
import sys
from PIL import Image

OUT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "mod", "src", "client", "resources",
                                    "assets", "godzilla", "textures", "population"))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def rgb(r, g, b):
    return (r, g, b, 255)


class Tex:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (255, 0, 255, 255))
        self.px = self.img.load()

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px[xx, yy] = c

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        self.img.save(os.path.join(OUT, name))


class Box:
    """A box w x h x d at texture offset (u, v) with painting helpers in 'front relative' coordinates."""

    def __init__(self, tex, u, v, w, h, d):
        self.t, self.u, self.v, self.w, self.h, self.d = tex, u, v, w, h, d

    def fill(self, c):
        for f in ("top", "bottom", "right", "front", "left", "back"):
            self.face_fill(f, c)

    def rect_of(self, f):
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h),
                "back": (u + 2 * d + w, v + d, w, h)}[f]

    def face_fill(self, f, c):
        x, y, w, h = self.rect_of(f)
        self.t.rect(x, y, w, h, c)

    def put(self, f, i, j, c):
        """Pixel (i, j) of a face in its own texture orientation."""
        x, y, w, h = self.rect_of(f)
        if 0 <= i < w and 0 <= j < h:
            self.t.px[x + i, y + j] = c

    def rectf(self, f, i, j, w, h, c):
        for jj in range(j, j + h):
            for ii in range(i, i + w):
                self.put(f, ii, jj, c)

    def side_rect(self, k, j, w, h, c, sides=("left", "right")):
        """Rectangle on the side faces: k = distance from the front end, j = row from the top."""
        for s in sides:
            for kk in range(k, k + w):
                i = kk if s == "left" else self.d - 1 - kk
                for jj in range(j, j + h):
                    self.put(s, i, jj, c)


# ----------------------------------------------------------------------------------------------------------- people
SHIRTS = [(235, 40, 40), (255, 140, 0), (255, 215, 0), (125, 225, 30), (0, 175, 75), (0, 205, 225), (70, 150, 255),
          (45, 75, 235), (155, 65, 225), (245, 55, 205), (255, 130, 185), (245, 245, 245), (20, 190, 160),
          (255, 90, 60), (40, 45, 80), (60, 60, 65)]
PANTS = [(30, 40, 90), (25, 25, 30), (190, 160, 110), (240, 240, 240), (180, 30, 40), (60, 60, 150), (45, 100, 60),
         (110, 70, 40)]
SKINS = [(255, 214, 178), (240, 190, 150), (205, 150, 110), (150, 100, 70), (255, 224, 196)]
HAIRS = [(15, 15, 20), (60, 35, 20), (230, 200, 90), (200, 80, 30), (200, 200, 205), (30, 90, 220), (20, 20, 20)]


def person(idx):
    shirt = SHIRTS[idx % len(SHIRTS)]
    pants = PANTS[(idx * 3 + 1) % len(PANTS)]
    skin = SKINS[(idx * 2 + 1) % len(SKINS)]
    hair = HAIRS[(idx * 5 + 2) % len(HAIRS)]
    if idx % 16 in (14, 15):  # salaryman: dark suit, white shirt front, dark trousers
        pants = shirt
    t = Tex(32, 32)
    t.rect(0, 0, 32, 32, rgb(255, 0, 255))
    head = Box(t, 0, 0, 3, 3, 3)
    head.fill(rgb(*skin))
    head.face_fill("top", rgb(*hair))
    head.face_fill("back", rgb(*hair))
    head.rectf("front", 0, 0, 3, 1, rgb(*hair))
    head.put("front", 0, 1, rgb(20, 20, 30))  # eyes
    head.put("front", 2, 1, rgb(20, 20, 30))
    head.put("front", 1, 2, rgb(200, 70, 70))  # mouth
    head.rectf("left", 0, 0, 3, 1, rgb(*hair))
    head.rectf("right", 0, 0, 3, 1, rgb(*hair))
    torso = Box(t, 0, 8, 4, 4, 2)
    torso.fill(rgb(*shirt))
    torso.face_fill("top", shade(shirt, 1.15))
    if idx % 16 in (14, 15):
        torso.rectf("front", 1, 0, 2, 3, rgb(245, 245, 245))
        torso.rectf("front", 1, 0, 1, 4, rgb(245, 245, 245))
        torso.put("front", 2, 1, rgb(200, 30, 30))
        torso.put("front", 2, 2, rgb(200, 30, 30))
    torso.rectf("front", 0, 3, 4, 1, rgb(*pants))  # belt line / trousers top
    leg = Box(t, 0, 16, 2, 3, 2)
    leg.fill(rgb(*pants))
    leg.face_fill("bottom", rgb(30, 30, 35))
    leg.rectf("front", 0, 2, 2, 1, rgb(35, 30, 30))  # shoes
    arm = Box(t, 16, 16, 2, 4, 2)
    arm.fill(rgb(*shirt))
    arm.face_fill("top", shade(shirt, 1.15))
    arm.rectf("front", 0, 3, 2, 1, rgb(*skin))
    arm.rectf("back", 0, 3, 2, 1, rgb(*skin))
    arm.rectf("left", 0, 3, 2, 1, rgb(*skin))
    arm.rectf("right", 0, 3, 2, 1, rgb(*skin))
    arm.face_fill("bottom", rgb(*skin))
    t.save("person_%02d.png" % idx)


# ----------------------------------------------------------------------------------------------------------- cars
WINDOW = rgb(70, 115, 160)
GLASS_LIT = rgb(110, 160, 205)
TIRE = rgb(25, 25, 28)
HUB = rgb(170, 170, 175)

CAR_COLORS = {
    "red": (225, 30, 35), "blue": (40, 90, 240), "green": (35, 190, 70), "orange": (255, 135, 15),
    "white": (240, 240, 240), "purple": (150, 55, 225), "cyan": (30, 210, 235), "pink": (255, 95, 170),
    "lime": (170, 235, 25),
}


def wheels(t, u, v, w, h, d):
    wb = Box(t, u, v, w, h, d)
    wb.fill(TIRE)
    wb.face_fill("left", TIRE)
    wb.face_fill("right", TIRE)
    for f in ("left", "right"):
        x, y, fw, fh = wb.rect_of(f)
        cx, cy = x + fw // 2, y + fh // 2
        t.rect(cx - 1, cy - 1, 2, 2, HUB)


def car(name, base, kind, variant):
    t = Tex(128, 64)
    t.rect(0, 0, 128, 64, rgb(255, 0, 255))
    lit = variant == 1
    body = Box(t, 0, 0, 13, 6, 24)
    cabin = Box(t, 0, 32, 11, 4, 12)
    b = rgb(*base)
    dark = shade(b, 0.62)
    if kind == "police":
        b = rgb(245, 245, 250)
        dark = rgb(25, 25, 30)
    body.fill(b)
    body.face_fill("top", shade(b, 1.1))
    body.face_fill("bottom", rgb(30, 30, 30))
    for s in ("left", "right"):
        body.side_rect(0, 5, 24, 1, dark)  # skirt
    if kind == "police":
        body.side_rect(0, 3, 24, 3, dark)  # black lower half
        body.side_rect(8, 0, 1, 5, shade(dark, 1.8))
        body.side_rect(15, 0, 1, 5, shade(dark, 1.8))
        body.side_rect(10, 1, 4, 1, rgb(40, 90, 255))  # blue stripe
    elif kind == "taxi":
        for kk in range(0, 24, 2):
            body.side_rect(kk, 3, 1, 1, rgb(20, 20, 20))
            body.side_rect(kk + 1, 4, 1, 1, rgb(20, 20, 20))
        body.side_rect(8, 0, 1, 3, dark)
        body.side_rect(15, 0, 1, 3, dark)
    else:
        body.side_rect(8, 0, 1, 5, dark)  # door seams
        body.side_rect(15, 0, 1, 5, dark)
        body.side_rect(7, 2, 1, 1, shade(b, 1.5))  # door handles
        body.side_rect(14, 2, 1, 1, shade(b, 1.5))
    # front: grille and headlights
    body.rectf("front", 3, 3, 7, 2, rgb(35, 35, 40))
    hl = rgb(255, 255, 255) if lit else rgb(255, 250, 190)
    for i in (0, 10):
        body.rectf("front", i, 1, 3, 2, hl)
    # back: tail lights and plate
    tl = rgb(255, 70, 50) if lit else rgb(200, 15, 15)
    for i in (0, 10):
        body.rectf("back", i, 1, 3, 2, tl)
    body.rectf("back", 5, 3, 3, 1, rgb(240, 240, 240))
    if lit:  # hazard flashers on the sides, front and back
        body.side_rect(0, 1, 2, 2, rgb(255, 170, 0))
        body.side_rect(22, 1, 2, 2, rgb(255, 170, 0))
    # cabin
    cabin.fill(b)
    cabin.face_fill("top", shade(b, 1.2))
    cabin.side_rect(1, 1, 10, 3, GLASS_LIT if lit else WINDOW)
    cabin.side_rect(5, 1, 1, 3, b)  # B pillar
    cabin.rectf("front", 1, 1, 9, 3, GLASS_LIT if lit else WINDOW)
    cabin.rectf("back", 1, 1, 9, 3, GLASS_LIT if lit else WINDOW)
    if kind == "police":
        cabin.face_fill("top", rgb(245, 245, 250))
    wheels(t, 76, 32, 2, 4, 4)
    # roof items: taxi sign (w5 h2 d3 at (0,50)) and light bar (w9 h2 d3 at (0,56))
    sign = Box(t, 0, 50, 5, 2, 3)
    sign.fill(rgb(255, 250, 220))
    sign.face_fill("top", rgb(255, 250, 220))
    sign.rectf("front", 1, 0, 3, 1, rgb(20, 20, 20))
    sign.rectf("back", 1, 0, 3, 1, rgb(20, 20, 20))
    if lit:
        sign.fill(rgb(255, 255, 160))
    bar = Box(t, 0, 56, 9, 2, 3)
    bar.fill(rgb(30, 30, 35))
    red_on, blue_on = rgb(255, 40, 40), rgb(60, 110, 255)
    red_off, blue_off = rgb(120, 15, 15), rgb(20, 40, 130)
    left, right = (red_on, blue_off) if variant == 0 else (red_off, blue_on)
    for f in ("front", "back", "top"):
        x, y, fw, fh = bar.rect_of(f)
        t.rect(x, y, fw // 2 + (fw % 2), fh, left if f != "back" else right)
        t.rect(x + fw // 2 + (fw % 2), y, fw // 2, fh, right if f != "back" else left)
    for s in ("left", "right"):
        bar.face_fill(s, left if s == "left" else right)
    t.save("car_%s_%d.png" % (name, variant))


# ----------------------------------------------------------------------------------------------------------- bus
BUS_COLORS = {"red": (225, 35, 40), "green": (30, 170, 80), "blue": (40, 110, 235), "orange": (255, 140, 20)}


def bus(name, base, variant):
    t = Tex(128, 64)
    t.rect(0, 0, 128, 64, rgb(255, 0, 255))
    lit = variant == 1
    b = rgb(*base)
    body = Box(t, 0, 0, 14, 12, 40)
    body.fill(b)
    body.face_fill("top", shade(b, 1.15))
    body.face_fill("bottom", rgb(35, 35, 38))
    glass = GLASS_LIT if lit else WINDOW
    body.side_rect(0, 8, 40, 1, rgb(245, 245, 245))  # white stripe
    body.side_rect(0, 11, 40, 1, shade(b, 0.6))
    body.side_rect(3, 2, 34, 4, glass)  # window band
    for kk in range(8, 36, 7):
        body.side_rect(kk, 2, 1, 4, b)  # pillars
    body.side_rect(1, 2, 4, 9, shade(glass, 0.85))  # front door
    body.side_rect(3, 2, 1, 9, b)
    body.side_rect(0, 0, 40, 1, shade(b, 1.2))
    # front
    body.rectf("front", 0, 0, 14, 1, rgb(255, 190, 40))  # destination sign
    body.rectf("front", 1, 1, 12, 5, glass)
    body.rectf("front", 6, 1, 2, 5, b)
    for i in (0, 11):
        body.rectf("front", i, 8, 3, 2, rgb(255, 255, 255) if lit else rgb(255, 250, 190))
    body.rectf("front", 4, 9, 6, 2, rgb(30, 30, 35))
    # back
    body.rectf("back", 2, 2, 10, 3, glass)
    for i in (0, 11):
        body.rectf("back", i, 8, 3, 2, rgb(255, 70, 50) if lit else rgb(200, 15, 15))
    # roof AC units
    body.rectf("top", 3, 6, 8, 10, shade(b, 0.8))
    body.rectf("top", 3, 22, 8, 8, shade(b, 0.8))
    wheels(t, 0, 54, 3, 5, 5)
    t.save("bus_%s_%d.png" % (name, variant))


# ----------------------------------------------------------------------------------------------------------- truck
TRUCK_COLORS = {"red": ((225, 40, 35), (245, 245, 245)), "blue": ((40, 100, 235), (255, 215, 20)),
                "green": ((35, 175, 80), (245, 245, 245)), "orange": ((255, 140, 20), (60, 150, 255))}


def truck(name, cab_c, cargo_c, variant):
    t = Tex(128, 64)
    t.rect(0, 0, 128, 64, rgb(255, 0, 255))
    lit = variant == 1
    cab = Box(t, 0, 40, 12, 10, 10)
    cargo = Box(t, 0, 0, 14, 13, 26)
    cb, cg = rgb(*cab_c), rgb(*cargo_c)
    cab.fill(cb)
    cab.face_fill("top", shade(cb, 1.15))
    cab.face_fill("bottom", rgb(35, 35, 38))
    glass = GLASS_LIT if lit else WINDOW
    cab.side_rect(1, 1, 6, 4, glass)
    cab.rectf("front", 1, 1, 10, 4, glass)
    cab.rectf("front", 1, 7, 10, 2, rgb(35, 35, 40))
    for i in (0, 9):
        cab.rectf("front", i, 6, 3, 2, rgb(255, 255, 255) if lit else rgb(255, 250, 190))
    cargo.fill(cg)
    cargo.face_fill("top", shade(cg, 0.92))
    cargo.face_fill("bottom", rgb(35, 35, 38))
    cargo.side_rect(0, 6, 26, 2, cb)  # company stripe
    cargo.side_rect(8, 2, 10, 3, shade(cb, 1.0))  # logo block
    cargo.side_rect(11, 3, 4, 1, cg)
    cargo.rectf("back", 0, 0, 14, 13, shade(cg, 0.9))
    cargo.rectf("back", 6, 0, 2, 13, shade(cg, 0.6))
    for i in (0, 11):
        cargo.rectf("back", i, 10, 3, 2, rgb(255, 70, 50) if lit else rgb(200, 15, 15))
    wheels(t, 84, 40, 3, 5, 5)
    t.save("truck_%s_%d.png" % (name, variant))


# ----------------------------------------------------------------------------------------------------------- train
TRAIN_COLORS = {"green": (40, 190, 80), "orange": (255, 130, 20), "blue": (40, 120, 240)}


def train(name, stripe, variant):
    t = Tex(256, 128)
    t.rect(0, 0, 256, 128, rgb(255, 0, 255))
    lit = variant == 1
    s = rgb(*stripe)
    body = Box(t, 0, 0, 16, 18, 56)
    cream = rgb(235, 238, 240)
    body.fill(cream)
    body.face_fill("top", rgb(190, 195, 200))
    body.face_fill("bottom", rgb(40, 40, 45))
    glass = GLASS_LIT if lit else rgb(60, 95, 140)
    body.side_rect(0, 9, 56, 3, s)  # coloured band
    body.side_rect(0, 14, 56, 2, shade(s, 0.6))
    for k0 in (4, 17, 30, 43):  # windows between doors
        body.side_rect(k0, 2, 6, 5, glass)
        body.side_rect(k0 + 7, 2, 0, 5, glass)
    for k0 in (11, 24, 37, 50):  # doors
        body.side_rect(k0, 2, 2, 12, shade(cream, 0.78))
        body.side_rect(k0, 3, 2, 4, glass)
    # cab front (-Z)
    body.rectf("front", 0, 0, 16, 18, cream)
    body.rectf("front", 2, 2, 12, 6, glass)
    body.rectf("front", 7, 2, 2, 6, cream)
    body.rectf("front", 0, 9, 16, 3, s)
    for i in (1, 12):
        body.rectf("front", i, 13, 3, 2, rgb(255, 255, 255) if lit else rgb(255, 250, 190))
    body.rectf("back", 0, 0, 16, 18, cream)
    body.rectf("back", 2, 2, 12, 6, glass)
    body.rectf("back", 0, 9, 16, 3, s)
    for i in (1, 12):
        body.rectf("back", i, 13, 3, 2, rgb(255, 70, 50) if lit else rgb(200, 15, 15))
    under = Box(t, 0, 76, 12, 3, 40)
    under.fill(rgb(45, 45, 52))
    ac = Box(t, 110, 76, 8, 1, 10)
    ac.fill(rgb(150, 155, 160))
    t.save("train_%s_%d.png" % (name, variant))


def main():
    for i in range(16):
        person(i)
    for n, c in CAR_COLORS.items():
        for v in (0, 1):
            car(n, c, "car", v)
    for v in (0, 1):
        car("taxi", (255, 205, 0), "taxi", v)
        car("police", (245, 245, 250), "police", v)
    for n, c in BUS_COLORS.items():
        for v in (0, 1):
            bus(n, c, v)
    for n, (a, b) in TRUCK_COLORS.items():
        for v in (0, 1):
            truck(n, a, b, v)
    for n, c in TRAIN_COLORS.items():
        for v in (0, 1):
            train(n, c, v)
    print("textures written to", OUT, "(%d files)" % len(os.listdir(OUT)))


if __name__ == "__main__":
    sys.exit(main())
