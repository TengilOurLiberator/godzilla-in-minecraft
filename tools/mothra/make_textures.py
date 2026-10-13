"""Generate Mothra's textures (original designs, nothing taken from any film or mod).

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\mothra\\make_textures.py

Writes into mod/src/client/resources/assets/godzilla/textures/mothra/:
  body.png     512x512, the model's declared texture size is 128x128 (4x resolution). Fur, eyes, antennae.
  wings.png    1024x1280: fore wing in rows 0..639, hind wing in rows 640..1279. Each is drawn for ONE wing, root at the left
               edge, leading edge at the top; the renderer mirrors the geometry for the other side. Alpha cuts the outline.
  sparkle.png  64x64 four-pointed glitter star.
  glow.png     128x128 soft round glow.

Wing sizes in blocks (must match MothraRenderer): fore wing 19.5 long x 11 wide, hind wing 15 x 9.5.
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "mod", "src", "client", "resources", "assets", "godzilla", "textures", "mothra")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(20261014)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None]
    return a * (1 - t) + b * t


def col(r, g, b):
    return np.array([r, g, b], dtype=np.float32)


def value_noise(h, w, cell, seed):
    r = np.random.default_rng(seed)
    gh, gw = h // cell + 2, w // cell + 2
    g = r.random((gh, gw)).astype(np.float32)
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0 = ys.astype(int)
    x0 = xs.astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def save(arr, name):
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    Image.fromarray(arr, "RGBA").save(os.path.join(OUT, name))
    print("wrote", name, arr.shape)


# ------------------------------------------------------------------------------------------------------------------ wings

def wing(kind):
    W, H = 1024, 640
    if kind == "fore":
        L, C = 19.5, 11.0
    else:
        L, C = 15.0, 9.5
    u = (np.arange(W)[None, :] + 0.5) / W * np.ones((H, 1))
    v = (np.arange(H)[:, None] + 0.5) / H * np.ones((1, W))
    X = u * L  # blocks along the span
    Y = v * C  # blocks across the chord, from the leading edge
    px = L / W  # blocks per pixel

    # silhouette and distance to the edge (in blocks, positive inside)
    if kind == "fore":
        vl = 0.01 + 0.07 * u ** 3
        vt = 1 - 0.80 * u ** 2.3 + 0.012 * np.sin(u * 64) * smooth(0.3, 0.6, u)
        inside_d = np.minimum(v - vl, vt - v) * C
        inside_d = np.minimum(inside_d, (1 - u) * L * 0.9)
    else:
        ang = np.arctan2((v - 0.5) / 0.5, u)
        rad = (u ** 2.8 + (np.abs(v - 0.5) / 0.5) ** 2.8) ** (1 / 2.8)
        scallop = 1 - 0.035 * (0.5 + 0.5 * np.sin(ang * 22)) * smooth(0.2, 0.9, rad)
        inside_d = (scallop - rad) * 7.5
        inside_d = np.minimum(inside_d, (v - 0.01) * C)
        inside_d = np.minimum(inside_d, (0.99 - v) * C)
    alpha = smooth(0.0, 0.07, inside_d)

    # base colour
    if kind == "fore":
        stops = [(0.0, col(255, 244, 218)), (0.22, col(255, 205, 110)), (0.55, col(247, 140, 36)), (1.0, col(205, 78, 22))]
    else:
        stops = [(0.0, col(255, 248, 226)), (0.35, col(255, 226, 140)), (0.75, col(252, 176, 70)), (1.0, col(238, 122, 34))]
    base = np.zeros((H, W, 3), np.float32)
    t = u if kind == "fore" else np.clip(rad, 0, 1)
    base[:] = stops[0][1]
    for (t0, c0), (t1, c1) in zip(stops[:-1], stops[1:]):
        m = smooth(t0, t1, t)
        base = np.where((t >= t0)[..., None], mix(c0, c1, m), base)

    # fine scale speckle
    n1 = value_noise(H, W, 6, 1) - 0.5
    n2 = value_noise(H, W, 18, 2) - 0.5
    base *= (1 + 0.10 * n1 + 0.14 * n2)[..., None]

    # veins radiating from the root
    ox, oy = 0.0, 0.5 * C if kind == "fore" else 0.5 * C
    dx, dy = X - ox, Y - oy
    ang = np.arctan2(dy, dx + 1e-6)
    r = np.hypot(dx, dy)
    nveins = 11 if kind == "fore" else 9
    veins = np.zeros_like(r)
    for k in range(nveins):
        a = -1.15 + 2.3 * k / (nveins - 1)
        if kind == "fore":
            a = -0.95 + 1.9 * k / (nveins - 1)
        veins = np.maximum(veins, smooth(0.075, 0.0, np.abs(ang - a) * r) * smooth(0.5, 2.0, r))
    base = base * (1 - 0.30 * veins[..., None]) + col(120, 60, 20) * (0.30 * veins[..., None])

    # dark leading edge (fore) / dark margin band (both)
    brown = col(34, 18, 12)
    if kind == "fore":
        lead = smooth(0.95, 0.35, (v - vl) * C)
        base = mix(base, brown, lead * 0.85)
    band_w = 1.15 if kind == "fore" else 1.25
    band = smooth(band_w, band_w * 0.55, inside_d)
    # leave the root side free of the band
    band *= smooth(0.12, 0.35, u)
    base = mix(base, brown, band)
    # white dots along the margin
    if kind == "fore":
        s = u
        edge_pos = 1.6
        pitch = 1.3
        k = np.round((np.sqrt(((1 - u) * L) ** 2 + 0) * 0 + (u * L)) / pitch)
        cx = k * pitch
        # dots are placed along the span at fixed spacing, at a fixed depth inside the trailing margin
        depth = (vt - v) * C
        dot = np.hypot((X - cx) , (depth - 0.55)) < 0.26
        dot &= (u > 0.3) & (u < 0.96)
    else:
        a2 = np.arctan2((v - 0.5) / 0.5, u)
        k = np.round(a2 / 0.16)
        ak = k * 0.16
        # points on the margin at angle ak, 0.55 blocks inside
        mx = (np.cos(ak)) * 0.9
        my = 0.5 + np.sin(ak) * 0.5 * 0.9
        dot = (np.hypot((u - mx) * L, (v - my) * C) < 0.30) & (rad > 0.8) & (u > 0.25)
    base = mix(base, col(255, 252, 240), (dot * 1.0).astype(np.float32))

    # eye spots
    def eyespot(cx, cy, R, rings):
        rr = np.hypot(X - cx, (Y - cy))
        out = None
        for rad_frac, color in rings:
            pass
        return rr

    def paint_eye(img, cx, cy, R, palette):
        rr = np.hypot(X - cx, Y - cy) / R
        res = img
        # palette: list of (outer radius fraction, colour), drawn from the outside in
        for frac, color in palette:
            m = smooth(frac, frac - 0.07, rr)
            res = mix(res, color, m)
        # glint
        g = np.hypot(X - (cx - 0.28 * R), Y - (cy - 0.30 * R)) / R
        res = mix(res, col(235, 245, 255), smooth(0.22, 0.10, g) * 0.9)
        return res

    if kind == "fore":
        base = paint_eye(base, 0.60 * L, 0.52 * C, 2.35, [
            (1.00, col(255, 214, 90)), (0.84, col(30, 16, 12)), (0.66, col(255, 246, 225)),
            (0.50, col(70, 100, 245)), (0.28, col(18, 16, 60))])
        base = paint_eye(base, 0.27 * L, 0.64 * C, 1.15, [
            (1.00, col(30, 16, 12)), (0.78, col(250, 120, 30)), (0.45, col(30, 16, 12))])
        base = paint_eye(base, 0.82 * L, 0.34 * C, 0.8, [
            (1.00, col(30, 16, 12)), (0.70, col(255, 240, 215)), (0.38, col(20, 20, 70))])
    else:
        base = paint_eye(base, 0.50 * L, 0.50 * C, 3.1, [
            (1.00, col(255, 190, 70)), (0.88, col(30, 16, 12)), (0.72, col(255, 248, 232)),
            (0.58, col(30, 200, 215)), (0.38, col(10, 110, 150)), (0.20, col(10, 14, 50))])
        base = paint_eye(base, 0.20 * L, 0.5 * C, 0.9, [
            (1.00, col(30, 16, 12)), (0.7, col(255, 150, 50))])

    # fur at the root
    furm = smooth(0.14, 0.0, u) * (0.6 + 0.8 * value_noise(H, W, 3, 7))
    base = mix(base, col(250, 236, 200), np.clip(furm, 0, 1) * 0.8)

    # colour of the transparent texels: the margin colour, so filtering does not bleed black halos
    base = mix(col(60, 28, 14), base, np.clip(alpha * 3, 0, 1))
    out = np.zeros((H, W, 4), np.float32)
    out[..., :3] = base
    out[..., 3] = alpha * 255
    return out


# ------------------------------------------------------------------------------------------------------------------ body

BODY = 512
SCALE = 4  # declared 128 -> 512


def region(img, u0, v0, w, h, d, painter):
    """The cube UV footprint of a box (width w, height h, depth d): 2*(d+w) x (d+h) declared texels."""
    x0, y0 = int(u0 * SCALE), int(v0 * SCALE)
    fw, fh = int(2 * (d + w) * SCALE), int((d + h) * SCALE)
    sub = img[y0:y0 + fh, x0:x0 + fw]
    painter(sub, fw, fh)


def fur_painter(base_a, base_b, stripe=None, seed=0, stripe_col=None, mix_scale=10):
    def paint(sub, w, h):
        n = value_noise(h, w, mix_scale, seed)
        fine = rng.random((h, w)).astype(np.float32)
        c = mix(base_a, base_b, np.clip(n * 1.4 - 0.2, 0, 1))
        c = c * (0.86 + 0.28 * fine[..., None])
        # short hair strokes (vertical streaks)
        streak = value_noise(h, w, 3, seed + 5)
        c = c * (0.92 + 0.16 * streak[..., None])
        if stripe is not None:
            rows = (np.arange(h)[:, None] // stripe) % 2
            c = mix(c, stripe_col, (rows * 0.55) * np.ones((1, w)))
        sub[..., :3] = c
        sub[..., 3] = 255
    return paint


def eye_painter(sub, w, h):
    n = value_noise(h, w, 4, 21)
    facet = ((np.arange(w)[None, :] // 6 + np.arange(h)[:, None] // 6) % 2).astype(np.float32)
    c = mix(col(18, 24, 34), col(40, 62, 80), n)
    c = c * (0.85 + 0.25 * facet[..., None])
    glints = rng.random((h, w)) > 0.985
    c[glints] = col(200, 240, 255)
    sub[..., :3] = c
    sub[..., 3] = 255


def comb_painter(sub, w, h):
    # antenna plume: teeth across the footprint so the cut-out reads as feathery barbs
    tooth = ((np.arange(h)[:, None] // 3) % 2).astype(np.float32) * np.ones((1, w))
    tooth = np.maximum(tooth, smooth(0.3, 0.0, np.abs(np.arange(w)[None, :] - w / 2) / w) * np.ones((h, 1)))
    sub[..., :3] = col(58, 40, 30)
    sub[..., 3] = tooth * 255


def body():
    img = np.zeros((BODY, BODY, 4), np.float32)
    img[..., :3] = col(120, 80, 40)
    img[..., 3] = 255
    cream, orange, white, brown = col(246, 232, 196), col(232, 128, 42), col(255, 250, 236), col(44, 28, 20)
    # (name, u, v, w, h, d) in declared texels; keep in sync with MothraModel
    region(img, 0, 0, 12, 10, 12, fur_painter(cream, orange, seed=3))           # thorax
    region(img, 0, 24, 8, 8, 8, fur_painter(orange, cream, seed=4))              # head
    region(img, 0, 42, 3, 5, 5, eye_painter)                                     # eyes
    region(img, 0, 54, 10, 8, 8, fur_painter(orange, cream, stripe=4, stripe_col=brown, seed=5))   # abdomen 1
    region(img, 0, 72, 8, 6, 8, fur_painter(cream, orange, stripe=4, stripe_col=brown, seed=6))    # abdomen 2
    region(img, 0, 88, 6, 5, 8, fur_painter(orange, cream, stripe=3, stripe_col=brown, seed=7))    # abdomen 3
    region(img, 0, 103, 3, 2, 4, fur_painter(cream, white, seed=8))              # tip
    region(img, 0, 111, 11, 9, 2, fur_painter(white, cream, seed=9, mix_scale=6))  # collar
    region(img, 64, 0, 1, 1, 10, fur_painter(brown, col(120, 90, 60), seed=10, mix_scale=4))      # antenna stalk
    region(img, 64, 12, 3, 1, 9, comb_painter)                                    # antenna plume
    region(img, 64, 24, 2, 8, 2, fur_painter(brown, cream, stripe=3, stripe_col=white, seed=11, mix_scale=4))  # leg
    region(img, 64, 36, 8, 3, 8, fur_painter(orange, brown, seed=12))            # dorsal tuft
    return img


# ------------------------------------------------------------------------------------------------------------------ sparkles

def sparkle():
    n = 64
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (x + 0.5 - n / 2) / (n / 2)
    y = (y + 0.5 - n / 2) / (n / 2)
    r = np.hypot(x, y)
    star = np.exp(-(np.abs(x) * np.abs(y)) * 38) * (1 - np.clip(r, 0, 1)) ** 0.8
    core = np.exp(-r * r * 22)
    a = np.clip(np.maximum(star, core), 0, 1)
    out = np.zeros((n, n, 4), np.float32)
    out[..., 0] = 255
    out[..., 1] = 236 - 30 * (1 - a)
    out[..., 2] = 150 + 90 * a
    out[..., 3] = a * 255
    return out


def glow():
    n = 128
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (x + 0.5 - n / 2) / (n / 2)
    y = (y + 0.5 - n / 2) / (n / 2)
    r = np.clip(np.hypot(x, y), 0, 1)
    a = (1 - r) ** 2.4
    out = np.zeros((n, n, 4), np.float32)
    out[..., 0] = 255
    out[..., 1] = 205
    out[..., 2] = 90
    out[..., 3] = a * 255
    return out


if __name__ == "__main__":
    fore = wing("fore")
    hind = wing("hind")
    sheet = np.concatenate([fore, hind], axis=0)
    save(sheet, "wings.png")
    save(body(), "body.png")
    save(sparkle(), "sparkle.png")
    save(glow(), "glow.png")
