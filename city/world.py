"""The voxel volume, and writers that paint into it.

Local coordinates: x in [0,512), z in [0,640), y is the real world y (28..155).
Array layout is vol[y - Y0, z, x] which is exactly the order of the .gzc file.
World position = local + ORIGIN (x and z shifted, y unchanged).
"""
import numpy as np
from blocks import AIR, WATER

X, Z = 512, 640
Y0 = 28
H = 128
YMAX = Y0 + H - 1          # 155
ORIGIN = (-256, 28, -320)  # world position of cell (0,0,0)
GROUND = 63
SEA = 62
OWN_MIN_Y = 64             # only blocks above the ground belong to buildings


def to_world(x, y, z):
    return (x + ORIGIN[0], y, z + ORIGIN[2])


def to_local(x, y, z):
    return (x - ORIGIN[0], y, z - ORIGIN[2])


class Writer:
    """Paints blocks. When `sid` is non-zero, every non-air block written above ground is owned by that structure."""

    def __init__(self, world, sid=0):
        self.w = world
        self.sid = sid

    # -- primitives --------------------------------------------------------------------------------------
    def set(self, x, y, z, blk):
        if 0 <= x < X and 0 <= z < Z and Y0 <= y <= YMAX:
            w = self.w
            w.vol[y - Y0, z, x] = blk
            if self.sid and y >= OWN_MIN_Y:
                w.own[y - Y0, z, x] = self.sid if blk != AIR else 0

    def get(self, x, y, z):
        if 0 <= x < X and 0 <= z < Z and Y0 <= y <= YMAX:
            return int(self.w.vol[y - Y0, z, x])
        return -1

    def fill(self, x0, y0, z0, x1, y1, z1, blk):
        if x0 > x1:
            x0, x1 = x1, x0
        if y0 > y1:
            y0, y1 = y1, y0
        if z0 > z1:
            z0, z1 = z1, z0
        x0 = max(x0, 0)
        z0 = max(z0, 0)
        y0 = max(y0, Y0)
        x1 = min(x1, X - 1)
        z1 = min(z1, Z - 1)
        y1 = min(y1, YMAX)
        if x0 > x1 or y0 > y1 or z0 > z1:
            return
        w = self.w
        w.vol[y0 - Y0:y1 - Y0 + 1, z0:z1 + 1, x0:x1 + 1] = blk
        if self.sid:
            oy = max(y0, OWN_MIN_Y)
            if oy <= y1:
                w.own[oy - Y0:y1 - Y0 + 1, z0:z1 + 1, x0:x1 + 1] = self.sid if blk != AIR else 0

    def put(self, x0, y0, z0, arr):
        """arr is (dy, dz, dx) of palette indices; negative values are skipped."""
        arr = np.asarray(arr)
        dy, dz, dx = arr.shape
        x1, y1, z1 = x0 + dx, y0 + dy, z0 + dz
        ax0, az0, ay0 = max(x0, 0), max(z0, 0), max(y0, Y0)
        ax1, az1, ay1 = min(x1, X), min(z1, Z), min(y1, YMAX + 1)
        if ax0 >= ax1 or az0 >= az1 or ay0 >= ay1:
            return
        sub = arr[ay0 - y0:ay1 - y0, az0 - z0:az1 - z0, ax0 - x0:ax1 - x0]
        dst = self.w.vol[ay0 - Y0:ay1 - Y0, az0:az1, ax0:ax1]
        m = sub >= 0
        dst[m] = sub[m]
        if self.sid:
            oy = max(ay0, OWN_MIN_Y)
            if oy < ay1:
                msub = m[oy - ay0:]
                o = self.w.own[oy - Y0:ay1 - Y0, az0:az1, ax0:ax1]
                vals = np.where(sub[oy - ay0:] != AIR, self.sid, 0).astype(np.uint16)
                o[msub] = vals[msub]

    def pts(self, xs, ys, zs, blk):
        """Set many cells given index arrays."""
        xs = np.asarray(xs)
        ys = np.asarray(ys)
        zs = np.asarray(zs)
        ok = (xs >= 0) & (xs < X) & (zs >= 0) & (zs < Z) & (ys >= Y0) & (ys <= YMAX)
        xs, ys, zs = xs[ok], ys[ok], zs[ok]
        self.w.vol[ys - Y0, zs, xs] = blk
        if self.sid:
            k = ys >= OWN_MIN_Y
            self.w.own[ys[k] - Y0, zs[k], xs[k]] = self.sid if blk != AIR else 0

    # -- shapes ---------------------------------------------------------------------------------------
    def shell(self, x0, y0, z0, x1, y1, z1, blk, top=True, bottom=True):
        """Hollow box: walls (and optionally floor and ceiling)."""
        self.fill(x0, y0, z0, x1, y1, z0, blk)
        self.fill(x0, y0, z1, x1, y1, z1, blk)
        self.fill(x0, y0, z0, x0, y1, z1, blk)
        self.fill(x1, y0, z0, x1, y1, z1, blk)
        if top:
            self.fill(x0, y1, z0, x1, y1, z1, blk)
        if bottom:
            self.fill(x0, y0, z0, x1, y0, z1, blk)

    def disk(self, cx, cz, r, y, blk, hollow=False):
        x0, x1 = int(cx - r - 1), int(cx + r + 1)
        z0, z1 = int(cz - r - 1), int(cz + r + 1)
        gx, gz = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1))
        d = np.hypot(gx - cx, gz - cz)
        m = d <= r + 0.5
        if hollow:
            m &= d > r - 0.7
        self.pts(gx[m], np.full(m.sum(), y), gz[m], blk)

    def cyl(self, cx, cz, r, y0, y1, blk, hollow=False):
        x0, x1 = int(cx - r - 1), int(cx + r + 1)
        z0, z1 = int(cz - r - 1), int(cz + r + 1)
        gx, gz = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1))
        d = np.hypot(gx - cx, gz - cz)
        m = d <= r + 0.5
        if hollow:
            m &= d > r - 0.7
        px, pz = gx[m], gz[m]
        for y in range(y0, y1 + 1):
            self.pts(px, np.full(px.shape, y), pz, blk)

    def sphere(self, cx, cy, cz, r, blk, only_air=False):
        x0, x1 = int(cx - r - 1), int(cx + r + 1)
        z0, z1 = int(cz - r - 1), int(cz + r + 1)
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            gx, gz = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1))
            d = np.sqrt((gx - cx) ** 2 + (gz - cz) ** 2 + (y - cy) ** 2)
            m = d <= r + 0.3
            if only_air:
                ok = (gx >= 0) & (gx < X) & (gz >= 0) & (gz < Z) & (Y0 <= y <= YMAX)
                if not ok.any():
                    continue
                cur = np.zeros(gx.shape, np.uint16)
                cur[ok] = self.w.vol[y - Y0, gz[ok], gx[ok]]
                m &= (cur == AIR)
            self.pts(gx[m], np.full(m.sum(), y), gz[m], blk)

    def line(self, p0, p1, blk, step=0.5):
        """3D voxel line between two (x,y,z) points (inclusive)."""
        p0 = np.asarray(p0, float)
        p1 = np.asarray(p1, float)
        n = int(max(np.abs(p1 - p0)) / step) + 1
        t = np.linspace(0, 1, n + 1)[:, None]
        q = np.rint(p0 + (p1 - p0) * t).astype(int)
        out = [tuple(int(v) for v in q[0])]
        for r in q[1:]:
            r = tuple(int(v) for v in r)
            cur = list(out[-1])
            if r == tuple(cur):
                continue
            # walk axis by axis so the line stays face-connected (6-connected)
            for ax in sorted(range(3), key=lambda a: -abs(r[a] - cur[a])):
                while cur[ax] != r[ax]:
                    cur[ax] += 1 if r[ax] > cur[ax] else -1
                    out.append(tuple(cur))
        arr = np.array(out)
        self.pts(arr[:, 0], arr[:, 1], arr[:, 2], blk)

    def thick_line(self, p0, p1, blk, r=1):
        """Line with a (2r+1) square cross-section in x/z."""
        for dx in range(-r + 1, r + 1) if r > 0 else [0]:
            for dz in range(-r + 1, r + 1) if r > 0 else [0]:
                self.line((p0[0] + dx, p0[1], p0[2] + dz), (p1[0] + dx, p1[1], p1[2] + dz), blk)


class Struct(Writer):
    def __init__(self, world, sid, kind, name=None, value=None):
        super().__init__(world, sid)
        self.kind = kind
        self.name = name
        self.value = value
        self.tags = {}


class World:
    def __init__(self):
        self.vol = np.zeros((H, Z, X), np.uint16)
        self.own = np.zeros((H, Z, X), np.uint16)
        self.structs = []
        self.raw = Writer(self)

    def new(self, kind, name=None, value=None):
        sid = len(self.structs) + 1
        assert sid < 65535
        s = Struct(self, sid, kind, name, value)
        self.structs.append(s)
        return s

    def column_top(self, x, z, ymin=GROUND):
        """Highest non-air, non-water cell at/above ymin in this column, or ymin-1."""
        col = self.vol[ymin - Y0:, z, x]
        nz = np.nonzero((col != AIR) & (col != WATER))[0]
        return ymin + int(nz[-1]) if len(nz) else ymin - 1
