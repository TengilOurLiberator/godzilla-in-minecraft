"""Validation of the generated city: file round trip, building boxes, spawns, road graph, bay depth, edge seam."""
import json
import numpy as np
from scipy.ndimage import label, generate_binary_structure

import blocks as B
import gzc
import plan
from world import X, Z, Y0, H, GROUND, SEA, ORIGIN, to_local

SCENERY_WORDS = ('_leaves', '_log', 'iron_bars', 'stained_glass', 'carpet')


def validate(world, T, kind, gzc_path, json_path, report):
    errs = []

    def err(msg):
        errs.append(msg)
        report.append('FAIL  ' + msg)

    def ok(msg):
        report.append('ok    ' + msg)

    vol = world.vol
    meta = json.load(open(json_path, 'r', encoding='utf-8'))

    # 1. format round trip ---------------------------------------------------------------------------------
    size, origin, pal, cells = gzc.read_gzc(gzc_path)
    if size != (X, H, Z) or origin != ORIGIN:
        err('header size/origin mismatch %s %s' % (size, origin))
    if pal[0] != 'minecraft:air':
        err('palette[0] is not air')
    for s in pal:
        B.validate_state(s)
    lut = np.array([B.PAL.index[s] for s in pal], np.uint16)
    if not np.array_equal(lut[cells], vol):
        err('gzc cells differ from the in-memory volume')
    else:
        ok('gzc round-trips: size %s origin %s palette %d entries, %d cells' % (size, origin, len(pal), cells.size))
    if list(meta['size']) != [X, H, Z] or list(meta['origin']) != list(ORIGIN):
        err('tokyo.json size/origin differ from the gzc header')
    for key in ('seaLevel', 'groundY', 'playerStart', 'buildings', 'roads', 'rails', 'sidewalkSpawns', 'tankSpawns',
                'maserSpawns', 'shipSpawns', 'airLanes', 'mothraArrival'):
        if key not in meta:
            err('tokyo.json lacks %s' % key)

    # 2. building boxes ------------------------------------------------------------------------------------
    bl = meta['buildings']
    mins = np.array([to_local(*b['min']) for b in bl])
    maxs = np.array([to_local(*b['max']) for b in bl])
    n = len(bl)
    overl = []
    for i in range(n):
        o = np.all((mins[i] <= maxs[i + 1:]) & (maxs[i] >= mins[i + 1:]), axis=1)
        for j in np.nonzero(o)[0]:
            overl.append((bl[i]['id'], bl[i + 1 + j]['id']))
    if overl:
        for a, b_ in overl[:20]:
            ba, bb = bl[a - 1], bl[b_ - 1]
            err('boxes overlap: #%d %s %s %s..%s and #%d %s %s %s..%s' % (a, ba['kind'], ba.get('name', ba.get('label', '')), ba['min'], ba['max'],
                                                                         b_, bb['kind'], bb.get('name', bb.get('label', '')), bb['min'], bb['max']))
        report.append('      (%d overlapping pairs in total)' % len(overl))
    else:
        ok('%d building boxes, none overlap' % n)
    # every owned block lies in its box, and every above-ground solid is owned or is scenery
    above = vol[GROUND + 1 - Y0:]
    own_above = world.own[GROUND + 1 - Y0:]
    solid_lut = np.ones(len(B.PAL.states) + 1, bool)
    solid_lut[0] = False
    solid_lut[B.WATER] = False
    unowned = (solid_lut[above]) & (own_above == 0)
    ids = above[unowned]
    names = {}
    for i, c in zip(*np.unique(ids, return_counts=True)):
        names[B.PAL.states[i]] = int(c)
    scen = {k: v for k, v in names.items() if any(w in k for w in SCENERY_WORDS)}
    other = {k: v for k, v in names.items() if k not in scen}
    report.append('      unowned above-ground blocks (scenery: trees, street lamps): %d; others: %s'
                  % (sum(scen.values()), other if other else 'none'))
    if sum(other.values()) > 0:
        err('%d unowned above-ground blocks that are not scenery: %s' % (sum(other.values()), other))
    counts = np.array([b['blocks'] for b in bl])
    if (counts <= 0).any():
        err('building with zero blocks')
    total = int(counts.sum())
    kinds = {}
    for b in bl:
        kinds.setdefault(b['kind'], []).append(b['blocks'])
    ok('buildings by kind (count, blocks min/median/max): ' + '; '.join(
        '%s %d (%d/%d/%d)' % (k, len(v), min(v), int(np.median(v)), max(v)) for k, v in sorted(kinds.items())))
    ok('total owned blocks %d' % total)
    # floating components (blocks that would fall as soon as a collapse pass runs)
    st = generate_binary_structure(3, 1)
    floating = 0
    worst = []
    ownv = world.own
    sid_of = {}
    k = 0
    present = set(np.unique(world.own[GROUND - Y0 + 1:]).tolist()) - {0}
    for s in world.structs:
        if s.sid in present:
            k += 1
            sid_of[k] = s.sid
    for b, lo, hi in zip(bl, mins, maxs):
        sid = sid_of[b['id']]
        sub = ownv[lo[1] - Y0:hi[1] - Y0 + 1, lo[2]:hi[2] + 1, lo[0]:hi[0] + 1] == sid
        lab, nc = label(sub, structure=st)
        sizes = np.bincount(lab.ravel())[1:]
        for ci in range(nc):
            comp = lab == (ci + 1)
            low = lo[1] + int(np.nonzero(comp.any(axis=(1, 2)))[0][0])
            if low != GROUND + 1:
                floating += int(sizes[ci])
                ys_, zs_, xs_ = np.nonzero(comp)
                worst.append((int(sizes[ci]), b['kind'], b.get('name', b.get('label', '')),
                              (int(xs_.min() + lo[0]), int(ys_.min() + lo[1]), int(zs_.min() + lo[2]))))
    worst.sort(reverse=True)
    report.append('      floating (not connected to the ground) blocks: %d in %d fragments; largest: %s'
                  % (floating, len(worst), worst[:8]))
    meta_floating = floating
    # 3. spawns on valid surfaces -------------------------------------------------------------------------
    def local(p):
        return to_local(*p[:3])

    def surface_ok(x, y, z, clear=3, solid_under=True):
        if not (0 <= x < X and 0 <= z < Z and Y0 <= y - 1 and y + clear < Y0 + H):
            return False
        under = vol[y - 1 - Y0, z, x]
        if under == 0 or under == B.WATER:
            return False
        return all(vol[y + k - Y0, z, x] == 0 for k in range(clear))

    bad = [p for p in meta['sidewalkSpawns'] if not surface_ok(*local(p), clear=2)]
    if bad:
        err('%d/%d sidewalk spawns are not on a free surface, e.g. %s' % (len(bad), len(meta['sidewalkSpawns']), bad[:3]))
    else:
        ok('%d sidewalk spawns all on free paving' % len(meta['sidewalkSpawns']))
    for name in ('tankSpawns', 'maserSpawns'):
        bad = []
        for p in meta[name]:
            x, y, z = local(p)
            good = all(surface_ok(x + dx, y, z + dz, clear=4) and vol[y - 1 - Y0, z + dz, x + dx] in
                       (plan.ASPHALT, plan.LANE_Y, plan.LANE_W) for dx in range(-2, 3) for dz in range(-2, 3))
            if not good:
                bad.append(p)
        if bad:
            err('%s: %d/%d not on a clear 5x5 patch of carriageway, e.g. %s' % (name, len(bad), len(meta[name]), bad[:3]))
        else:
            ok('%s: %d, all on clear carriageway' % (name, len(meta[name])))
    bad = []
    for p in meta['shipSpawns']:
        x, y, z = to_local(*p[:3])
        fine = True
        for dx in range(-6, 7, 2):
            for dz in range(-12, 13, 2):
                xx, zz = x + dx, z + dz
                if not (0 <= xx < X and 0 <= zz < Z):
                    fine = False
                    continue
                col = vol[:, zz, xx]
                if col[SEA - Y0] != B.WATER or col[GROUND - Y0] != 0 or col[GROUND + 12 - Y0] != 0:
                    fine = False
                depth = int(np.count_nonzero(col == B.WATER))
                if depth < 10:
                    fine = False
        if not fine:
            bad.append(p)
    if bad:
        err('shipSpawns not on open deep water: %s' % bad)
    else:
        ok('%d ship spawns on open deep water' % len(meta['shipSpawns']))

    # 4. road graph ------------------------------------------------------------------------------------------
    nodes = meta['roads']['nodes']
    edges = meta['roads']['edges']
    parent = list(range(len(nodes)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    for a, b_, w in edges:
        parent[find(a)] = find(b_)
    comps = len({find(i) for i in range(len(nodes))})
    if comps != 1:
        err('road graph has %d components' % comps)
    else:
        ok('road graph connected: %d nodes, %d edges' % (len(nodes), len(edges)))
    bad_cells = 0
    bad_ex = []
    asph = {plan.ASPHALT, plan.LANE_Y, plan.LANE_W}
    for a, b_, w in edges:
        xa, ya, za = to_local(*nodes[a])
        xb, yb, zb = to_local(*nodes[b_])
        n_ = max(abs(xb - xa), abs(zb - za))
        for k in range(n_ + 1):
            x = xa + (xb - xa) * k // max(n_, 1)
            z = za + (zb - za) * k // max(n_, 1)
            if int(vol[GROUND - Y0, z, x]) not in asph or any(vol[GROUND + 1 + j - Y0, z, x] != 0 for j in range(3)):
                bad_cells += 1
                if len(bad_ex) < 6:
                    bad_ex.append((x, z, int(vol[GROUND - Y0, z, x]), [int(vol[GROUND + 1 + j - Y0, z, x]) for j in range(3)]))
    if bad_cells:
        err('%d road-edge cells are not clear asphalt, e.g. %s' % (bad_cells, bad_ex))
    else:
        ok('every road edge runs over clear asphalt (3 blocks of headroom)')

    # 5. bay depth at the player's start -------------------------------------------------------------------
    ps = meta['playerStart']['pos']
    px, py, pz = to_local(*ps)
    col = vol[:, pz, px]
    solid_idx = np.nonzero((col != 0) & (col != B.WATER))[0]
    floor = Y0 + int(solid_idx[solid_idx < (SEA + 1 - Y0)].max())
    water_above = int(np.count_nonzero(col == B.WATER))
    flat = all(vol[28 - Y0, pz + dz, px + dx] == B.STONE and vol[29 - Y0, pz + dz, px + dx] == B.WATER
               for dx in range(-12, 13, 3) for dz in range(-12, 13, 3))
    if floor != 28 or water_above < 34 or not flat:
        err('start column: floor y=%d water=%d flat=%s' % (floor, water_above, flat))
    else:
        ok('player start (%d,%d,%d): floor top y=%d, %d blocks of water above, floor flat within 12 blocks'
           % (ps[0], ps[1], ps[2], floor, water_above))

    # 6. edge seam rule ------------------------------------------------------------------------------------
    seam_bad = 0
    ring = np.zeros((Z, X), bool)
    w_ = 10
    ring[:w_, :] = True
    ring[-w_:, :] = True
    ring[:, :w_] = True
    ring[:, -w_:] = True
    zz, xx = np.nonzero(ring)
    for y in range(Y0, Y0 + H):
        v = vol[y - Y0, zz, xx]
        want = B.STONE if y <= 28 else (B.WATER if y <= SEA else 0)
        seam_bad += int(np.count_nonzero(v != want))
    if seam_bad:
        err('%d cells in the 10-block outer ring differ from stone<=28 / water 29..62 / air' % seam_bad)
    else:
        ok('outer 10-block ring is exactly the flat world (stone to y=28, water 29..62, air above)')
    if not (vol[0] == B.STONE).all():
        err('layer y=28 is not all stone')
    else:
        ok('layer y=28 is all stone')
    return errs, dict(floating=meta_floating)



