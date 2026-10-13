"""Preview renderers (numpy + Pillow): top-down map, orthographic/isometric view, and a perspective view.

Blocks are coloured by the average colour of their Patrix texture (see texcolors.py)."""
import math
import numpy as np
from PIL import Image, ImageDraw

from world import X, Z, Y0, H
import blocks
import texcolors

SKY_TOP = np.array([96, 150, 226], np.float32)
SKY_HOR = np.array([196, 220, 240], np.float32)
SEA_COL = np.array([40, 92, 170], np.float32)
FOG_COL = np.array([188, 214, 238], np.float32)


def _colors(states):
    c = texcolors.palette_colors(states).astype(np.float32)
    rgb = c[:, :3]
    alpha = c[:, 3] / 255.0
    # per-cell opacity used while marching through translucent blocks
    cell_alpha = alpha.copy()
    for i, s in enumerate(states):
        if s.startswith('minecraft:water'):
            cell_alpha[i] = 0.04
        elif 'stained_glass' in s or s == 'minecraft:tinted_glass':
            cell_alpha[i] = 0.45
        elif s == 'minecraft:glass':
            cell_alpha[i] = 0.18
        elif alpha[i] > 0.99:
            cell_alpha[i] = 1.0
    cell_alpha[0] = 0.0
    return rgb, cell_alpha


def topdown(vol, states, scale=2, grid=True):
    rgb, alpha = _colors(states)
    nz = vol != 0
    top = (H - 1) - np.argmax(nz[::-1], axis=0)               # (Z,X)
    zz, xx = np.meshgrid(np.arange(Z), np.arange(X), indexing='ij')
    blk = vol[top, zz, xx]
    col = rgb[blk].copy()
    ht = (top + Y0).astype(np.float32)
    # water depth shading
    iswater = np.array([s.startswith('minecraft:water') for s in states])[blk]
    wcount = (vol == blocks.WATER).sum(axis=0).astype(np.float32)
    d = np.clip(wcount / 34.0, 0, 1)
    wcol = np.array([120, 190, 225], np.float32) * (1 - d[..., None]) + np.array([20, 55, 130], np.float32) * d[..., None]
    col = np.where(iswater[..., None], wcol, col)
    # height shading (hill-shade from the west/north)
    hs = np.zeros_like(ht)
    hs[1:-1, 1:-1] = (ht[1:-1, 1:-1] - ht[:-2, 1:-1]) * 0.5 + (ht[1:-1, 1:-1] - ht[1:-1, :-2]) * 0.5
    lit = np.clip(1.0 + 0.045 * hs, 0.55, 1.35)
    lit = np.where(iswater, 1.0, lit)
    tall = np.clip((ht - 63) / 70.0, 0, 1)
    lit = lit * (0.85 + 0.25 * (1 - tall) * 0 + 0.15 * tall)
    col = np.clip(col * lit[..., None], 0, 255).astype(np.uint8)
    im = Image.fromarray(col, 'RGB').resize((X * scale, Z * scale), Image.NEAREST)
    if grid:
        d = ImageDraw.Draw(im)
        for g in range(0, X, 50):
            d.line([(g * scale, 0), (g * scale, 6)], fill=(255, 255, 255))
            d.text((g * scale + 2, 6), str(g), fill=(255, 255, 255))
        for g in range(0, Z, 50):
            d.line([(0, g * scale), (6, g * scale)], fill=(255, 255, 255))
            d.text((8, g * scale), str(g), fill=(255, 255, 255))
    return im


def _hash(cx, cy, cz):
    h = (cx.astype(np.int64) * 73856093) ^ (cy.astype(np.int64) * 19349663) ^ (cz.astype(np.int64) * 83492791)
    return ((h >> 3) & 255).astype(np.float32) / 255.0


def render(vol, states, origin, forward, size=(960, 540), fov=100.0, ortho=None, max_steps=1400,
           fog_k=0.0016, world_fog=True, sun=(0.55, 0.8, 0.35), roll_up=(0, 1, 0)):
    """Voxel ray marcher. `origin` is (x, y_world, z) local; `forward` a direction vector.
    ortho: None for perspective, or the width in blocks of the view for an orthographic camera."""
    rgb, alpha = _colors(states)
    W, Hh = size
    f = np.asarray(forward, np.float64)
    f /= np.linalg.norm(f)
    up0 = np.asarray(roll_up, np.float64)
    r = np.cross(f, up0)
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    px = (np.arange(W) + 0.5) / W * 2 - 1
    py = 1 - (np.arange(Hh) + 0.5) / Hh * 2
    PX, PY = np.meshgrid(px, py)
    PX = PX.ravel()
    PY = PY.ravel()
    o = np.asarray(origin, np.float64).copy()
    o[1] -= Y0
    if ortho is None:
        t = math.tan(math.radians(fov) / 2)
        d = f[None, :] + (PX * t * W / Hh)[:, None] * r[None, :] + (PY * t)[:, None] * u[None, :]
        d /= np.linalg.norm(d, axis=1)[:, None]
        org = np.tile(o, (len(PX), 1))
    else:
        d = np.tile(f, (len(PX), 1))
        org = o[None, :] + (PX * ortho / 2)[:, None] * r[None, :] + (PY * ortho / 2 * Hh / W)[:, None] * u[None, :]
    N = len(PX)
    dirs = d.astype(np.float64)
    org = org.astype(np.float64)
    box_lo = np.array([0.0, 0.0, 0.0])
    box_hi = np.array([X, H, Z], float)
    # slab intersection to move origins that are outside to the box
    with np.errstate(divide='ignore', invalid='ignore'):
        inv = 1.0 / np.where(np.abs(dirs) < 1e-9, 1e-9, dirs)
        t0 = (box_lo - org) * inv
        t1 = (box_hi - org) * inv
    tn = np.minimum(t0, t1).max(axis=1)
    tf = np.maximum(t0, t1).min(axis=1)
    inside = np.all((org >= box_lo) & (org <= box_hi), axis=1)
    hit_box = inside | ((tn <= tf) & (tf > 0))
    tstart = np.where(inside, 0.0, np.maximum(tn, 0) + 1e-3)
    pos = org + dirs * tstart[:, None]
    # outputs
    acc = np.zeros((N, 3), np.float32)
    T = np.ones(N, np.float32)
    tdist = np.zeros(N, np.float32)
    exit_y = np.zeros(N, np.float32)
    # active set
    act = np.nonzero(hit_box)[0]
    p = pos[act]
    dr = dirs[act]
    cell = np.floor(p).astype(np.int32)
    cell = np.clip(cell, [0, 0, 0], [X - 1, H - 1, Z - 1])
    stp = np.where(dr > 0, 1, -1).astype(np.int32)
    invd = 1.0 / np.where(np.abs(dr) < 1e-9, 1e-9, dr)
    tmax = ((cell + (stp > 0)) - p) * invd
    tdel = np.abs(invd)
    tcur = tstart[act].copy()
    sun = np.asarray(sun, np.float32)
    sun /= np.linalg.norm(sun)
    flat = vol.reshape(-1)
    sx, sy = Z * X, X  # strides for (y, z, x) -> index = y*Z*X + z*X + x
    ids = act
    lastaxis = np.full(len(ids), 1, np.int8)
    shade_ax = np.array([0.78, 1.0, 0.62], np.float32)
    # first cell is examined before stepping
    first = True
    for it in range(max_steps):
        if len(ids) == 0:
            break
        if not first:
            tx = tmax[:, 0]
            ty = tmax[:, 1]
            tz = tmax[:, 2]
            ax = np.where((tx <= ty) & (tx <= tz), 0, np.where(ty <= tz, 1, 2))
            ar = np.arange(len(ids))
            tcur = tmax[ar, ax]
            cell[ar, ax] += stp[ar, ax]
            tmax[ar, ax] += tdel[ar, ax]
            lastaxis = ax.astype(np.int8)
            oob = (cell[:, 0] < 0) | (cell[:, 0] >= X) | (cell[:, 1] < 0) | (cell[:, 1] >= H) | \
                  (cell[:, 2] < 0) | (cell[:, 2] >= Z)
            if oob.any():
                # finalise exits
                ex = ids[oob]
                tdist[ex] = tcur[oob]
                exit_y[ex] = (org[ex, 1] + dirs[ex, 1] * tcur[oob]) + Y0
                keep = ~oob
                ids, cell, stp, tmax, tdel, tcur, lastaxis = ids[keep], cell[keep], stp[keep], tmax[keep], tdel[keep], tcur[keep], lastaxis[keep]
                if len(ids) == 0:
                    break
        first = False
        c = flat[cell[:, 1] * sx + cell[:, 2] * sy + cell[:, 0]]
        a = alpha[c]
        vis = a > 0
        if vis.any():
            vi = np.nonzero(vis)[0]
            cv = c[vi]
            ax_v = lastaxis[vi]
            sgn = stp[vi, ax_v]
            sh = shade_ax[ax_v].copy()
            top = (ax_v == 1)
            sh = np.where(top & (sgn < 0), 1.0, np.where(top, 0.5, sh))
            cl = cell[vi]
            noise = 0.93 + 0.14 * _hash(cl[:, 0], cl[:, 1], cl[:, 2])
            col = rgb[cv] * (sh * noise)[:, None]
            av = a[vi]
            idv = ids[vi]
            acc[idv] += (T[idv] * av)[:, None] * col
            T[idv] *= (1 - av)
            tdist[idv] = tcur[vi]
            done = np.zeros(len(ids), bool)
            done[vi] = T[idv] < 0.04
            if done.any():
                keep = ~done
                ids, cell, stp, tmax, tdel, tcur, lastaxis = ids[keep], cell[keep], stp[keep], tmax[keep], tdel[keep], tcur[keep], lastaxis[keep]
    # background for rays that still have transmittance
    rem = T > 0.04
    dy = dirs[:, 1]
    sky_t = np.clip(dy * 1.6, 0, 1)[:, None]
    sky = SKY_HOR[None, :] * (1 - sky_t) + SKY_TOP[None, :] * sky_t
    # downward rays leaving the volume above sea level continue to the sea plane
    sea = (dy < -1e-4)
    tsea = np.where(sea, (63.0 - (org[:, 1] + Y0)) / np.where(sea, dy, -1), 1e9)
    sea_col = SEA_COL[None, :] * 0.9
    back = np.where(sea[:, None], sea_col + (FOG_COL[None, :] - sea_col) * np.clip(1 - np.exp(-np.maximum(tsea, 0) * 0.0045), 0, 1)[:, None], sky)
    far = np.where(sea, tsea, 2000.0)
    # fog on contribution already accumulated
    fog = 1 - np.exp(-tdist * fog_k) if world_fog else np.zeros(N, np.float32)
    colr = acc * (1 - fog[:, None]) + (1 - T)[:, None] * FOG_COL[None, :] * fog[:, None]
    colr = colr + T[:, None] * np.where(rem[:, None], back, 0)
    img = np.clip(colr, 0, 255).astype(np.uint8).reshape(Hh, W, 3)
    return Image.fromarray(img, 'RGB')
