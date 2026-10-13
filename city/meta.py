"""tokyo.json metadata: building boxes, road graph, spawns, air lanes."""
import numpy as np

import blocks as B
import plan
import terrain
from world import X, Z, Y0, H, GROUND, ORIGIN, to_world

KIND_VALUE = {'house': 1.0, 'shop': 1.2, 'apartment': 1.2, 'office': 1.5, 'tower': 2.0, 'industrial': 1.5,
              'station': 2.0, 'bridge': 1.5, 'landmark': 3.0}


def building_boxes(world):
    """Bounding boxes (local coords, inclusive) and solid block counts from the ownership array."""
    own = world.own
    ys, zs, xs = np.nonzero(own)
    ids = own[ys, zs, xs].astype(np.int64)
    order = np.argsort(ids, kind='stable')
    ids, ys, zs, xs = ids[order], ys[order], zs[order], xs[order]
    uniq, start = np.unique(ids, return_index=True)
    out = {}
    for k, sid in enumerate(uniq):
        a = start[k]
        b = start[k + 1] if k + 1 < len(uniq) else len(ids)
        out[int(sid)] = dict(
            min=(int(xs[a:b].min()), int(ys[a:b].min()) + Y0, int(zs[a:b].min())),
            max=(int(xs[a:b].max()), int(ys[a:b].max()) + Y0, int(zs[a:b].max())),
            blocks=int(b - a))
    return out


def building_list(world):
    boxes = building_boxes(world)
    lst = []
    new_id = {}
    for s in world.structs:
        bx = boxes.get(s.sid)
        if bx is None:
            continue
        nid = len(lst) + 1
        new_id[s.sid] = nid
        wmin = to_world(*bx['min'])
        wmax = to_world(*bx['max'])
        value = s.value if s.value is not None else int(round(bx['blocks'] / 12.0 * KIND_VALUE.get(s.kind, 1.0)))
        d = dict(id=nid, kind=s.kind, min=list(wmin), max=list(wmax), blocks=bx['blocks'], value=int(value))
        if s.kind == 'landmark' or s.name == 'Fuel Tank':
            d['name'] = s.name
        elif s.name:
            d['label'] = s.name
        lst.append(d)
    return lst, boxes, new_id


# ---------------------------------------------------------------------------------------------------------
def road_graph():
    """Nodes at intersections and span ends, edges along each road between consecutive nodes."""
    nodes = {}

    def node(x, z):
        k = (x, z)
        if k not in nodes:
            nodes[k] = len(nodes)
        return nodes[k]

    edges = []
    for r in plan.ROADS:
        width = 2 * r.ch + 1
        for (a, b) in r.spans:
            pts = {a, b}
            for o in plan.ROADS:
                if o.axis == r.axis:
                    continue
                if a <= o.c <= b and any(oa <= r.c <= ob for oa, ob in o.spans):
                    pts.add(o.c)
            pts = sorted(pts)
            ids = []
            for t in pts:
                x, z = (r.c, t) if r.axis == 'v' else (t, r.c)
                ids.append(node(x, z))
            for i0, i1 in zip(ids, ids[1:]):
                edges.append((i0, i1, width))
    inv = sorted(nodes.items(), key=lambda kv: kv[1])
    out_nodes = [list(to_world(x, GROUND + 1, z)) for (x, z), _ in inv]
    return out_nodes, [[a, b, w] for a, b, w in edges], [k for k, _ in inv]


def sidewalk_spawns(world, kind, rng, target=700):
    vol = world.vol
    base = vol[GROUND - Y0]
    cand = []
    side = (kind == 1)
    # sidewalk cells with clear air above, not next to the road intersections
    ok = side & (vol[GROUND - Y0 + 1] == 0) & (vol[GROUND - Y0 + 2] == 0) & (base == plan.SIDEWALK)
    zs, xs = np.nonzero(ok)
    idx = rng.permutation(len(zs))[:target * 3]
    chosen = []
    grid = {}
    for i in idx:
        x, z = int(xs[i]), int(zs[i])
        key = (x // 6, z // 6)
        if key in grid:
            continue
        grid[key] = 1
        chosen.append([*to_world(x, GROUND + 1, z)])
        if len(chosen) >= target:
            break
    chosen.sort()
    return chosen


def road_spawns(world, kind, rng):
    """Tank and maser spawns on clear 5x5 patches of avenue carriageway, found by search near chosen places."""
    from scipy.ndimage import binary_erosion
    vol = world.vol
    asph = np.isin(vol[GROUND - Y0], [plan.ASPHALT, plan.LANE_Y, plan.LANE_W])
    free = np.ones((Z, X), bool)
    for k in range(1, 6):
        free &= (vol[GROUND - Y0 + k] == 0)
    ok5 = binary_erosion(asph & free, np.ones((5, 5), bool))
    # keep away from intersections: the avenues' own carriageway only
    avenue_cells = np.zeros((Z, X), bool)
    for r in plan.ROADS:
        if r.kind != plan.AVE or (r.axis == 'v' and r.c == 400) or (r.axis == 'h' and r.c == 214):
            continue
        if r.axis == 'v':
            avenue_cells[:, r.c - 2:r.c + 3] = True
        else:
            avenue_cells[r.c - 2:r.c + 3, :] = True
    ok5 &= avenue_cells

    def spot(cx, cz, used, rad=14):
        best = None
        for dz in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                x, z = cx + dx, cz + dz
                if 0 <= x < X and 0 <= z < Z and ok5[z, x] and all(abs(x - u) + abs(z - v) > 16 for u, v in used):
                    d = abs(dx) + abs(dz)
                    if best is None or d < best[0]:
                        best = (d, x, z)
        return best

    used = []
    tanks = []
    for (cx, cz) in [(254, 330), (254, 372), (254, 400), (164, 335), (164, 375), (164, 405), (254, 280), (254, 245),
                     (200, 304), (232, 304), (320, 304), (254, 120), (164, 140)]:
        s = spot(cx, cz, used)
        if s:
            used.append((s[1], s[2]))
            tanks.append(list(to_world(s[1], GROUND + 1, s[2])))
    masers = []
    for (cx, cz) in [(254, 200), (164, 240), (254, 160), (110, 304), (270, 304), (164, 190)]:
        s = spot(cx, cz, used)
        if s:
            used.append((s[1], s[2]))
            masers.append(list(to_world(s[1], GROUND + 1, s[2])))
    return tanks, masers

def ship_spawns():
    pts = [(236, 478, 200), (338, 470, 160), (290, 500, 180), (190, 505, 190), (380, 492, 170)]
    out = []
    for (x, z, yaw) in pts:
        wx, _, wz = to_world(x, GROUND, z)
        out.append([wx, GROUND, wz, yaw])
    return out

def air_lanes():
    lanes_local = [
        [(-20, 112, 130), (130, 116, 180), (300, 120, 240), (532, 112, 270)],
        [(-20, 108, 420), (140, 112, 350), (330, 118, 300), (532, 110, 180)],
        [(80, 118, 660), (200, 112, 520), (270, 108, 330), (300, 118, 80), (310, 120, -30)],
        [(532, 104, 560), (400, 110, 420), (330, 116, 250), (200, 120, 120), (100, 118, -30)],
    ]
    return [[list(to_world(x, y, z)) for (x, y, z) in lane] for lane in lanes_local]


def mothra():
    return dict(**{'from': list(to_world(256, 135, -70)), 'to': list(to_world(268, 100, 250))})


