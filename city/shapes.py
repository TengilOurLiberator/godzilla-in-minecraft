"""Reusable shape helpers: hip roofs with correct stair corners, a rotating/mirroring writer frame, circles."""
import numpy as np
import blocks as B


FULL = {
    'stone_brick': B.STONE_BRICKS, 'deepslate_tile': B.DEEPSLATE_TILES, 'brick': B.BRICKS, 'mud_brick': B.MUD_BRICKS,
    'polished_deepslate': B.POL_DEEPSLATE, 'oxidized_cut_copper': B.OX_CUT_COPPER, 'cut_copper': B.CUT_COPPER,
    'tuff_brick': B.TUFF_BRICKS, 'quartz': B.QUARTZ, 'sandstone': B.SANDSTONE, 'cinnabar_brick': B.CINNABAR_BRICKS,
    'weathered_cut_copper': B.WEATH_CUT_COPPER,
}


def hip_roof(S, x0, z0, x1, z1, y, name, top_blk=None, max_layers=99, cap=True, soffit=None):
    """Hip roof of stairs (with proper outer-corner shapes), first layer at height y over the inclusive rect.
    `soffit` (a block) lays a plate under the first layer across the whole rect so the roof is carried by the walls;
    when the roof is stopped by max_layers the opening is closed with a plate (`cap`).  Returns the top layer's y."""
    ST = B.ST(name)
    full = top_blk if top_blk is not None else FULL[name]
    if soffit is not None:
        S.fill(x0, y - 1, z0, x1, y - 1, z1, soffit)
    i = 0
    last = None
    while True:
        a0, b0, a1, b1 = x0 + i, z0 + i, x1 - i, z1 - i
        if a0 > a1 or b0 > b1:
            break
        if i >= max_layers:
            if cap and last is not None:
                la0, lb0, la1, lb1, lyy = last
                if la0 + 1 <= la1 - 1 and lb0 + 1 <= lb1 - 1:
                    S.fill(la0 + 1, lyy, lb0 + 1, la1 - 1, lyy, lb1 - 1, full)
            break
        yy = y + i
        w = a1 - a0 + 1
        d = b1 - b0 + 1
        if w <= 2 or d <= 2:
            S.fill(a0, yy, b0, a1, yy, b1, full)
            break
        S.fill(a0 + 1, yy, b0, a1 - 1, yy, b0, ST[('south', 'bottom')])      # north edge, high side south
        S.fill(a0 + 1, yy, b1, a1 - 1, yy, b1, ST[('north', 'bottom')])      # south edge
        S.fill(a0, yy, b0 + 1, a0, yy, b1 - 1, ST[('east', 'bottom')])       # west edge
        S.fill(a1, yy, b0 + 1, a1, yy, b1 - 1, ST[('west', 'bottom')])       # east edge
        # filler ring just inside, under the next layer's stairs, so the layers are face-connected
        S.fill(a0 + 1, yy, b0 + 1, a1 - 1, yy, b0 + 1, full)
        S.fill(a0 + 1, yy, b1 - 1, a1 - 1, yy, b1 - 1, full)
        S.fill(a0 + 1, yy, b0 + 1, a0 + 1, yy, b1 - 1, full)
        S.fill(a1 - 1, yy, b0 + 1, a1 - 1, yy, b1 - 1, full)
        S.set(a0, yy, b0, B.b(name + '_stairs', facing='south', half='bottom', shape='outer_left', waterlogged=False))
        S.set(a1, yy, b0, B.b(name + '_stairs', facing='south', half='bottom', shape='outer_right', waterlogged=False))
        S.set(a0, yy, b1, B.b(name + '_stairs', facing='north', half='bottom', shape='outer_right', waterlogged=False))
        S.set(a1, yy, b1, B.b(name + '_stairs', facing='north', half='bottom', shape='outer_left', waterlogged=False))
        last = (a0, b0, a1, b1, yy)
        i += 1
    return y + i


class Frame:
    """Draw in local (u, y, v) coordinates and map to the world.
    mode 'x': u -> +x, v -> +z      mode 'xm': u -> +x, v -> -z
    mode 'z': u -> +z, v -> +x      mode 'zm': u -> +z, v -> -x"""

    def __init__(self, S, mode, ox, oz):
        self.S, self.mode, self.ox, self.oz = S, mode, ox, oz

    def m(self, u, v):
        if self.mode == 'x':
            return self.ox + u, self.oz + v
        if self.mode == 'xm':
            return self.ox + u, self.oz - v
        if self.mode == 'z':
            return self.ox + v, self.oz + u
        return self.ox - v, self.oz + u

    def set(self, u, y, v, blk):
        x, z = self.m(u, v)
        self.S.set(x, y, z, blk)

    def fill(self, u0, y0, v0, u1, y1, v1, blk):
        xa, za = self.m(u0, v0)
        xb, zb = self.m(u1, v1)
        self.S.fill(min(xa, xb), y0, min(za, zb), max(xa, xb), y1, max(za, zb), blk)

    def line(self, p0, p1, blk):
        xa, za = self.m(p0[0], p0[2])
        xb, zb = self.m(p1[0], p1[2])
        self.S.line((xa, p0[1], za), (xb, p1[1], zb), blk)


def circle_pts(cx, cy, r, n=None):
    n = n or int(r * 14)
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    pts = np.unique(np.rint(np.stack([cx + r * np.cos(t), cy + r * np.sin(t)], axis=1)).astype(int), axis=0)
    return pts

