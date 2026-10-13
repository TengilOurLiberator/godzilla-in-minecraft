"""Street grid, blocks, zoning and the road raster."""
import numpy as np

import blocks as B
from world import X, Z, GROUND

SIDEWALK = B.SMOOTH_STONE
ASPHALT = B.C['gray']
LANE_Y = B.C['yellow']
LANE_W = B.C['white']
YARD = B.POL_ANDESITE
PLAZA = B.STONE_BRICKS

AVE = 'A'
STR = 'S'
HALF = {AVE: 6, STR: 3}
CARR_HALF = {AVE: 4, STR: 2}   # carriageway half width (sidewalk is the rest)


class Road:
    def __init__(self, axis, c, kind, spans):
        self.axis = axis    # 'v' runs along z at x=c, 'h' runs along x at z=c
        self.c = c
        self.kind = kind
        self.spans = spans  # list of (a, b) along the axis (centre coordinates)
        self.hw = HALF[kind]
        self.ch = CARR_HALF[kind]


ZMIN, ZMAX = 76, 424     # extent of the N-S roads (centre z)
XMIN, XMAX = 76, 436     # extent of the E-W roads (centre x)

ROADS = [
    # north-south
    Road('v', 76, STR, [(ZMIN, ZMAX)]),
    Road('v', 118, STR, [(168, ZMAX)]),
    Road('v', 164, AVE, [(ZMIN, ZMAX)]),
    Road('v', 208, STR, [(ZMIN, 214), (258, ZMAX)]),
    Road('v', 254, AVE, [(ZMIN, ZMAX)]),
    Road('v', 300, STR, [(ZMIN, ZMAX)]),
    Road('v', 400, AVE, [(100, ZMAX)]),
    Road('v', 436, STR, [(ZMIN, ZMAX)]),
    # east-west
    Road('h', 76, STR, [(XMIN, XMAX)]),
    Road('h', 122, STR, [(164, XMAX)]),
    Road('h', 168, AVE, [(XMIN, XMAX)]),
    Road('h', 214, AVE, [(XMIN, XMAX)]),
    Road('h', 258, STR, [(XMIN, XMAX)]),
    Road('h', 304, AVE, [(XMIN, XMAX)]),
    Road('h', 346, STR, [(XMIN, XMAX)]),
    Road('h', 388, STR, [(XMIN, XMAX)]),
    Road('h', 424, AVE, [(XMIN, XMAX)]),
]

VLINES = [r for r in ROADS if r.axis == 'v']
HLINES = [r for r in ROADS if r.axis == 'h']

# block grid: x intervals between N-S roads, z intervals between E-W roads (inclusive)
XB = [(80, 114), (122, 157), (171, 204), (212, 247), (261, 296), (304, 338), (365, 393), (407, 432)]
ZB = [(80, 118), (126, 161), (175, 207), (221, 254), (262, 297), (311, 342), (350, 384), (392, 417)]

# zone code per block, rows = ZB (R0..R7), cols = XB (B0..B7)
# SJ sensoji, ST tokyo station, DI diet, TT tokyo tower park, FW ferris wheel, TW twin towers,
# PK park, PT port, I industrial, H housing, A apartments, O offices, T towers, SH shops
ZONE_TABLE = [
    ['SJ', 'SJ', 'H',  'PK', 'PK', 'A',  'SH', 'H'],
    ['SJ', 'SJ', 'H',  'PK', 'PK', 'O',  'SH', 'H'],
    ['H',  'A',  'O',  'O',  'TW', 'O',  'A',  'SH'],
    ['H',  'A',  'ST', 'ST', 'SS', 'T',  'A',  'SH'],
    ['I',  'DI', 'A',  'T',  'T',  'T',  'O',  'A'],
    ['I',  'H',  'O',  'O',  'T',  'T',  'SH', 'SH'],
    ['PT', 'PT', 'PT', 'PK', 'O',  'SH', 'SH', 'SH'],
    ['PT', 'PT', 'PT', 'TT', 'FW', 'PK', 'SH', 'PK'],
]


class Block:
    def __init__(self, i, j, rect, code):
        self.i, self.j, self.rect, self.code = i, j, rect, code


def make_blocks():
    out = []
    for j, (z0, z1) in enumerate(ZB):
        for i, (x0, x1) in enumerate(XB):
            out.append(Block(i, j, (x0, z0, x1, z1), ZONE_TABLE[j][i]))
    return out


def precincts(blks):
    """Group blocks that share a special code into one precinct rect (union), keyed by code."""
    special = {'SJ', 'ST'}
    groups = {}
    for b in blks:
        if b.code in special:
            groups.setdefault(b.code, []).append(b)
    out = {}
    for code, bs in groups.items():
        x0 = min(b.rect[0] for b in bs)
        z0 = min(b.rect[1] for b in bs)
        x1 = max(b.rect[2] for b in bs)
        z1 = max(b.rect[3] for b in bs)
        out[code] = (x0, z0, x1, z1)
    return out


class RoadRaster:
    """Raster of the road network: kind per cell and the surface block per cell."""
    NONE, SIDE, CARR, MARK = 0, 1, 2, 3


def paint_roads(land, surf):
    """Paints sidewalks, asphalt, lane markings and crossings into `surf`; returns the kind raster."""
    kind = np.zeros((Z, X), np.uint8)
    carr_v = np.zeros((Z, X), bool)
    carr_h = np.zeros((Z, X), bool)
    side = np.zeros((Z, X), bool)
    for r in ROADS:
        for (a, b) in r.spans:
            a0, a1 = max(a - 3, 0), b + 3
            if r.axis == 'v':
                side[a0:a1 + 1, r.c - r.hw:r.c + r.hw + 1] = True
                carr_v[a0:a1 + 1, r.c - r.ch:r.c + r.ch + 1] = True
            else:
                side[r.c - r.hw:r.c + r.hw + 1, a0:a1 + 1] = True
                carr_h[r.c - r.ch:r.c + r.ch + 1, a0:a1 + 1] = True
    carr = carr_v | carr_h
    surf[side & land] = SIDEWALK
    kind[side] = 1
    surf[carr] = ASPHALT
    kind[carr] = 2
    inter = carr_v & carr_h
    # lane markings (not inside intersections or crosswalk zones)
    mark_block = inter.copy()
    from scipy.ndimage import binary_dilation
    mark_block = binary_dilation(mark_block, structure=np.ones((3, 3), bool), iterations=3)
    for r in ROADS:
        for (a, b) in r.spans:
            a0, a1 = max(a - 3, 0), b + 3
            n = np.arange(a0, a1 + 1)
            if r.kind == AVE:
                lines = [(0, LANE_Y, False), (-2, LANE_W, True), (2, LANE_W, True)]
            else:
                lines = [(0, LANE_Y, True)]
            for off, blk, dashed in lines:
                if r.axis == 'v':
                    cells_z = n[(n % 4 < 2)] if dashed else n
                    surf_x = r.c + off
                    zz = cells_z
                    ok = ~mark_block[zz, surf_x]
                    surf[zz[ok], surf_x] = blk
                    kind[zz[ok], surf_x] = 3
                else:
                    cells_x = n[(n % 4 < 2)] if dashed else n
                    surf_z = r.c + off
                    xx = cells_x
                    ok = ~mark_block[surf_z, xx]
                    surf[surf_z, xx[ok]] = blk
                    kind[surf_z, xx[ok]] = 3
    # crosswalks: at each intersection, on each of the four arms
    for rv in VLINES:
        for rh in HLINES:
            if not (_has(rv, rh.c) and _has(rh, rv.c)):
                continue
            xc, zc = rv.c, rh.c
            # arms of the N-S road (stripes across x) north and south of the E-W road
            for sgn in (-1, 1):
                z_mid = zc + sgn * (rh.ch + 2)
                if _has_seg(rv, zc + sgn * (rh.ch + 2)):
                    for z in (z_mid - 1, z_mid, z_mid + 1)[:3]:
                        pass
                    zs = [zc + sgn * (rh.ch + 1), zc + sgn * (rh.ch + 2), zc + sgn * (rh.ch + 3)]
                    for x in range(xc - rv.ch, xc + rv.ch + 1):
                        if (x - xc) % 2 == 0:
                            for z in zs[:2]:
                                if carr_v[z, x] and not inter[z, x]:
                                    surf[z, x] = LANE_W
                                    kind[z, x] = 3
            for sgn in (-1, 1):
                if _has_seg(rh, xc + sgn * (rv.ch + 2)):
                    xs = [xc + sgn * (rv.ch + 1), xc + sgn * (rv.ch + 2)]
                    for z in range(zc - rh.ch, zc + rh.ch + 1):
                        if (z - zc) % 2 == 0:
                            for x in xs:
                                if carr_h[z, x] and not inter[z, x]:
                                    surf[z, x] = LANE_W
                                    kind[z, x] = 3
    return kind, carr, inter


def _has(r, c):
    return any(a <= c <= b for a, b in r.spans)


def _has_seg(r, c):
    return any(a <= c <= b for a, b in r.spans)


# ---------------------------------------------------------------------------------------------------------
# lots
def split_rect(rect, min_w, max_w, min_d, max_d, rng, gap=1):
    """Recursively subdivide an inclusive rect (x0,z0,x1,z1) into lots separated by `gap` cells."""
    x0, z0, x1, z1 = rect
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    if w <= max_w and d <= max_d:
        return [rect]
    # split the dimension that is relatively larger
    if (w / max_w) >= (d / max_d):
        lo = x0 + max(min_w, int(w * 0.35))
        hi = x1 - max(min_w, int(w * 0.35)) - gap
        if hi < lo:
            return [rect]
        cut = int(rng.integers(lo, hi + 1))
        return (split_rect((x0, z0, cut - 1, z1), min_w, max_w, min_d, max_d, rng, gap) +
                split_rect((cut + gap, z0, x1, z1), min_w, max_w, min_d, max_d, rng, gap))
    else:
        lo = z0 + max(min_d, int(d * 0.35))
        hi = z1 - max(min_d, int(d * 0.35)) - gap
        if hi < lo:
            return [rect]
        cut = int(rng.integers(lo, hi + 1))
        return (split_rect((x0, z0, x1, cut - 1), min_w, max_w, min_d, max_d, rng, gap) +
                split_rect((x0, cut + gap, x1, z1), min_w, max_w, min_d, max_d, rng, gap))


def front_side(lot, block_rect, rng):
    """Which side of the lot faces a road: sides on the block boundary."""
    x0, z0, x1, z1 = lot
    bx0, bz0, bx1, bz1 = block_rect
    sides = []
    if z1 == bz1:
        sides.append('s')
    if z0 == bz0:
        sides.append('n')
    if x1 == bx1:
        sides.append('e')
    if x0 == bx0:
        sides.append('w')
    if not sides:
        return 's'
    return sides[int(rng.integers(0, len(sides)))]

