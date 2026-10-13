"""Generates the Godzilla body, beam and halo textures (original, procedural; nothing from the films).

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\player\\make_textures.py

All body textures are 64x64 and tile seamlessly: the renderer maps them with world-density planar UVs (6 blocks per tile) and the
entity sampler repeats. Output: mod/src/client/resources/assets/godzilla/textures/player/*.png
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.dirname(HERE)), "mod", "src", "client", "resources", "assets", "godzilla", "textures", "player")
N = 64


def rng(seed):
    return np.random.default_rng(seed)


def periodic_noise(seed, cells, size=N):
    """Smooth value noise that wraps around (so the texture tiles)."""
    r = rng(seed)
    grid = r.random((cells, cells))
    xs = np.linspace(0, cells, size, endpoint=False)
    x0 = np.floor(xs).astype(int)
    f = xs - x0
    f = f * f * (3 - 2 * f)
    x1 = (x0 + 1) % cells
    a = grid[np.ix_(x0, x0)] * (1 - f)[None, :] * (1 - f)[:, None]
    b = grid[np.ix_(x0, x1)] * f[None, :] * (1 - f)[:, None]
    c = grid[np.ix_(x1, x0)] * (1 - f)[None, :] * f[:, None]
    d = grid[np.ix_(x1, x1)] * f[None, :] * f[:, None]
    return a + b + c + d


def fbm(seed, base=4, octaves=4):
    total = np.zeros((N, N))
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total += amp * periodic_noise(seed + o * 17, base * 2 ** o)
        norm += amp
        amp *= 0.5
    return total / norm


def voronoi(seed, cells):
    """Periodic Voronoi: returns (nearest distance, second distance, cell id) in cell units."""
    r = rng(seed)
    px = (np.arange(cells)[None, :] + r.random((cells, cells)))  # x of point in cell (row j, col i)
    py = (np.arange(cells)[:, None] + r.random((cells, cells)))
    ys, xs = np.mgrid[0:N, 0:N] / N * cells
    d1 = np.full((N, N), 9.0)
    d2 = np.full((N, N), 9.0)
    ids = np.zeros((N, N))
    for j in range(cells):
        for i in range(cells):
            dx = np.abs(xs - px[j, i])
            dy = np.abs(ys - py[j, i])
            dx = np.minimum(dx, cells - dx)
            dy = np.minimum(dy, cells - dy)
            d = np.sqrt(dx * dx + dy * dy)
            closer = d < d1
            d2 = np.where(closer, d1, np.minimum(d2, d))
            ids = np.where(closer, j * cells + i, ids)
            d1 = np.where(closer, d, d1)
    return d1, d2, ids


def save(name, arr):
    os.makedirs(OUT, exist_ok=True)
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    Image.fromarray(arr, "RGBA" if arr.shape[2] == 4 else "RGB").save(os.path.join(OUT, name + ".png"))


def rgba(rgb, alpha=255):
    h, w, _ = rgb.shape
    a = np.full((h, w, 1), alpha) if np.isscalar(alpha) else alpha[:, :, None]
    return np.concatenate([rgb, a], axis=2)


def skin():
    d1, d2, ids = voronoi(11, 8)
    edge = np.clip((d2 - d1) * 5.0, 0, 1)         # 0 on cell borders
    bump = np.clip(1 - d1 * 1.6, 0, 1)             # 1 at the cell centre
    n = fbm(5, 4, 4)
    cell_tone = (ids * 0.6180339) % 1.0
    base = 34 + 14 * bump + 10 * n + 7 * cell_tone
    base = base * (0.55 + 0.45 * edge)
    r = base * 0.96
    g = base * 1.0
    b = base * 1.12 + 3
    # sparse paler scars
    scars = (fbm(21, 8, 2) > 0.74).astype(float) * 14
    return rgba(np.stack([r + scars, g + scars, b + scars], axis=2))


def belly():
    d1, d2, ids = voronoi(31, 6)
    edge = np.clip((d2 - d1) * 4.0, 0, 1)
    band = 0.5 + 0.5 * np.sin(np.linspace(0, 2 * np.pi * 4, N, endpoint=False))[:, None]
    n = fbm(9, 4, 3)
    base = 74 + 16 * n + 10 * band + 8 * (1 - d1)
    base = base * (0.7 + 0.3 * edge)
    return rgba(np.stack([base * 1.08, base * 0.97, base * 0.86], axis=2))


def plate():
    ridge = 0.5 + 0.5 * np.sin(np.linspace(0, 2 * np.pi * 6, N, endpoint=False))[None, :]
    n = fbm(41, 4, 4)
    d1, d2, ids = voronoi(43, 5)
    base = 96 + 30 * ridge + 24 * n - 18 * np.clip(1 - (d2 - d1) * 4, 0, 1)
    return rgba(np.stack([base * 1.0, base * 0.98, base * 0.93], axis=2))


def bone():
    streak = fbm(51, 2, 3)
    ring = 0.5 + 0.5 * np.sin(np.linspace(0, 2 * np.pi * 5, N, endpoint=False))[:, None]
    base = 205 + 25 * streak - 18 * ring
    return rgba(np.stack([base, base * 0.95, base * 0.80], axis=2))


def mouth():
    n = fbm(61, 4, 3)
    base = 40 + 40 * n
    return rgba(np.stack([base * 1.7, base * 0.35, base * 0.38], axis=2))


def glow():
    n = fbm(71, 3, 3)
    v = 225 + 30 * n
    return rgba(np.stack([v * 0.82, v * 0.93, np.full((N, N), 255.0)], axis=2))


def eye():
    n = fbm(81, 2, 2)
    v = 235 + 20 * n
    return rgba(np.stack([np.full((N, N), 255.0), v * 0.78, 40 + 30 * n], axis=2))


def beam():
    """u across the beam (bright core, soft edge), v along it (slow streaks that scroll)."""
    u = np.linspace(0, 1, N)[None, :]
    prof = np.clip(1 - np.abs(2 * u - 1), 0, 1) ** 1.35
    streak = 0.82 + 0.18 * fbm(91, 4, 3)
    a = np.clip(prof * streak * 1.15, 0, 1) * np.ones((N, 1))
    rgb = np.stack([np.full((N, N), 235.0), np.full((N, N), 245.0), np.full((N, N), 255.0)], axis=2)
    return rgba(rgb, a * 255)


def halo():
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.sqrt((xs - (N - 1) / 2) ** 2 + (ys - (N - 1) / 2) ** 2) / (N / 2)
    a = np.clip(1 - d, 0, 1) ** 2.2
    core = np.clip(1 - d * 2.2, 0, 1)
    a = np.clip(a + core * 0.6, 0, 1)
    rgb = np.stack([np.full((N, N), 240.0), np.full((N, N), 248.0), np.full((N, N), 255.0)], axis=2)
    return rgba(rgb, a * 255)


if __name__ == "__main__":
    for name, fn in [("skin", skin), ("belly", belly), ("plate", plate), ("bone", bone), ("mouth", mouth),
                     ("glow", glow), ("eye", eye), ("beam", beam), ("halo", halo)]:
        save(name, fn())
        print("wrote", name)
