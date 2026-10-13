"""Fills the street grid with buildings, parks, the port and tank farm; street trees and lamps."""
import numpy as np

import blocks as B
import bldg
import districts as D
import plan
from bldg import pick
from world import GROUND, X, Z

TREE_KINDS = {
    'oak': (B.OAK_LOG, B.OAK_LEAVES),
    'birch': (B.OAK_LOG, B.BIRCH_LEAVES),
    'cherry': (B.CHERRY_LOG, B.CHERRY_LEAVES),
    'ginkgo': (B.POPLAR_LOG, B.YELLOW_POPLAR),
    'maple': (B.DARK_OAK_LOG, B.RED_POPLAR),
    'spruce': (B.SPRUCE_LOG, B.SPRUCE_LEAVES),
    'zelkova': (B.DARK_OAK_LOG, B.OAK_LEAVES),
}

BIZ_CENTER = (285.0, 250.0)


def biz(x, z):
    return float(np.exp(-np.hypot(x - BIZ_CENTER[0], z - BIZ_CENTER[1]) / 120.0))


def tree(raw, x, z, kind, rng, h=None):
    log, leaf = TREE_KINDS[kind]
    if h is None:
        h = int(rng.integers(2, 5))
    if kind == 'spruce':
        h = int(rng.integers(3, 6))
        raw.fill(x, GROUND + 1, z, x, GROUND + h + 3, z, log)
        for i, r in enumerate([2, 2, 1, 1, 0]):
            y = GROUND + h - 1 + i
            if r:
                raw.fill(x - r, y, z - r, x + r, y, z + r, leaf)
            else:
                raw.set(x, y, z, leaf)
        return
    raw.fill(x, GROUND + 1, z, x, GROUND + h, z, log)
    r = 1.8 if kind in ('ginkgo', 'maple') else (2.3 if kind != 'cherry' else 2.6)
    if rng.random() < 0.3:
        r += 0.5
    raw.sphere(x, GROUND + h + 1, z, r, leaf, only_air=True)


def paint(world, rect, blk):
    x0, z0, x1, z1 = rect
    world.raw.fill(x0, GROUND, z0, x1, GROUND, z1, blk)


def park(world, T, rect, rng, density=0.02, kinds=None, paths=True, ground=None):
    x0, z0, x1, z1 = rect
    raw = world.raw
    gr = ground if ground is not None else B.GRASS
    # only grass on land cells (keeps ponds)
    land = ~T.pond[z0:z1 + 1, x0:x1 + 1]
    sl = world.vol[GROUND - 28, z0:z1 + 1, x0:x1 + 1]
    sl[land] = gr
    path = np.zeros((z1 - z0 + 1, x1 - x0 + 1), bool)
    if paths:
        cx = (x0 + x1) // 2
        cz = (z0 + z1) // 2
        path[:, cx - x0 - 1:cx - x0 + 1] = True
        path[cz - z0 - 1:cz - z0 + 1, :] = True
        sub = world.vol[GROUND - 28, z0:z1 + 1, x0:x1 + 1]
        sub[path & land] = B.GRAVEL
    kinds = kinds or ['oak', 'oak', 'cherry', 'cherry', 'ginkgo', 'maple', 'birch', 'zelkova', 'spruce']
    n = int((x1 - x0 + 1) * (z1 - z0 + 1) * density)
    for _ in range(n):
        x = int(rng.integers(x0 + 1, x1))
        z = int(rng.integers(z0 + 1, z1))
        if path[z - z0, x - x0] or not land[z - z0, x - x0]:
            continue
        tree(raw, x, z, pick(rng, kinds), rng)


def build_zone(world, T, block, rng, stats):
    x0, z0, x1, z1 = block.rect
    code = block.code
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    hs = biz(cx, cz)
    rect = block.rect
    if code == 'PK':
        park(world, T, rect, rng, density=0.05)
        return
    if code == 'H':
        paint(world, rect, plan.YARD)
        lots = plan.split_rect(rect, 8, 11, 8, 13, rng)
        for lot in lots:
            front = plan.front_side(lot, rect, rng)
            D.house(world, lot, front, rng)
        return
    if code == 'A':
        paint(world, rect, plan.YARD)
        lots = plan.split_rect(rect, 13, 21, 13, 21, rng)
        for lot in lots:
            front = plan.front_side(lot, rect, rng)
            w, d = D.lot_size(lot)
            if w >= 14 and d >= 14 and rng.random() < 0.85:
                D.apartment(world, lot, front, rng, 5, 5 + int(2 + 4 * hs))
            elif rng.random() < 0.5:
                D.shop(world, lot, front, rng)
            else:
                D.house(world, lot, front, rng)
        return
    if code == 'SH':
        paint(world, rect, plan.YARD)
        # row of narrow shop lots along the block's long sides, bigger buildings in the middle
        lots = plan.split_rect(rect, 8, 11, 9, 14, rng)
        for lot in lots:
            front = plan.front_side(lot, rect, rng)
            w, d = D.lot_size(lot)
            r = rng.random()
            if w >= 13 and d >= 13 and r < 0.4:
                D.office(world, lot, front, rng, 5, 7)
            elif r < 0.12:
                D.house(world, lot, front, rng)
            else:
                D.shop(world, lot, front, rng, hmax=6)
        return
    if code == 'O':
        paint(world, rect, plan.YARD)
        lots = plan.split_rect(rect, 15, 27, 15, 27, rng)
        for lot in lots:
            front = plan.front_side(lot, rect, rng)
            w, d = D.lot_size(lot)
            if w >= 14 and d >= 14:
                if rng.random() < 0.28 and w >= 16 and d >= 16:
                    D.tower(world, lot, front, rng, 10, 13)
                else:
                    D.office(world, lot, front, rng, 5, 9)
            else:
                D.shop(world, lot, front, rng)
        return
    if code == 'T':
        paint(world, rect, plan.YARD)
        lots = plan.split_rect(rect, 18, 34, 18, 34, rng)
        for lot in lots:
            front = plan.front_side(lot, rect, rng)
            w, d = D.lot_size(lot)
            if w >= 16 and d >= 16:
                D.tower(world, lot, front, rng, 11, 15)
            elif w >= 12 and d >= 12:
                D.office(world, lot, front, rng, 6, 10)
        return
    if code == 'I':
        industrial_block(world, T, rect, rng)
        return
    if code == 'PT':
        port_block(world, T, block, rng)
        return
    # TT, DI, TW, FW, SJ, ST are handled by the landmark builders (ground painted there)


def industrial_block(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    paint(world, rect, B.C['gray'] if False else plan.YARD)
    mid = z0 + 14
    # warehouses in the north strip, a tank farm (two rows of three fuel tanks) in the south
    farm = (x0, mid + 1, x1, z1)
    paint(world, farm, B.GRAVEL)
    for row, cz in enumerate((z0 + 18, z0 + 26)):
        for k, cx in enumerate((x0 + 5, x0 + 15, x0 + 25)):
            D.fuel_tank(world, cx, cz, 3, int(rng.integers(6, 10)), rng)
    wh = (x0, z0, x1, mid - 1)
    for lot in plan.split_rect(wh, 16, 30, 14, 24, rng):
        D.warehouse(world, lot, 's', rng)


def port_block(world, T, block, rng):
    x0, z0, x1, z1 = block.rect
    rect = block.rect
    paint(world, rect, B.C['gray'])
    if block.j == 6:      # inland row: warehouses, a couple of tanks
        for lot in plan.split_rect(rect, 14, 26, 14, 20, rng):
            D.warehouse(world, lot, 's', rng)
    else:                 # waterfront row: container yard
        x = x0 + 1
        while x + 16 <= x1:
            nx = 4
            nz = 7
            D.container_stack(world, x + 1, z0 + 1, nx, nz, rng)
            x += nx * 4 + 3


def street_trees_and_lamps(world, T, kind, rng):
    raw = world.raw
    vol = world.vol
    for r in plan.ROADS:
        if (r.axis == 'v' and r.c == 400) or (r.axis == 'h' and r.c == 214):
            continue
        for (a, b) in r.spans:
            if r.kind == plan.AVE:
                side_offs = (-5, 5)
                step = 9
            else:
                side_offs = ()
                step = 99
            for off in side_offs:
                for t in range(a + 6, b - 5, step):
                    x, z = (r.c + off, t) if r.axis == 'v' else (t, r.c + off)
                    if not (0 <= x < X and 0 <= z < Z):
                        continue
                    # skip intersections, bridges and anything non-sidewalk
                    if kind[z, x] != 1 or T.river[z, x]:
                        continue
                    along = t
                    if any(o.axis != r.axis and abs(along - o.c) <= o.hw + 4 and
                           any(oa <= r.c <= ob for oa, ob in o.spans) for o in plan.ROADS):
                        continue
                    if rng.random() < 0.15:
                        continue
                    k = 'ginkgo' if (r.c // 4) % 3 == 0 else ('zelkova' if (r.c // 4) % 3 == 1 else 'cherry')
                    tree(raw, x, z, k, rng, h=3)
    # street lamps
    lamp_head = B.GL['white']
    for r in plan.ROADS:
        if (r.axis == 'v' and r.c == 400) or (r.axis == 'h' and r.c == 214):
            continue
        for (a, b) in r.spans:
            off = r.hw if r.kind == plan.AVE else r.hw
            step = 17 if r.kind == plan.AVE else 23
            side = 1
            for t in range(a + 11, b - 10, step):
                side = -side
                o = side * off
                x, z = (r.c + o, t) if r.axis == 'v' else (t, r.c + o)
                if not (0 <= x < X and 0 <= z < Z):
                    continue
                if kind[z, x] != 1 or T.river[z, x]:
                    continue
                if vol[GROUND - 28 + 1, z, x] != 0:
                    continue
                raw.fill(x, GROUND + 1, z, x, GROUND + 5, z, B.IRON_BARS)
                raw.set(x, GROUND + 6, z, lamp_head)
