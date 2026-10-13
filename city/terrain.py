"""Land, sea floor, river and pond: everything below the street surface."""
import numpy as np
from scipy.ndimage import gaussian_filter, distance_transform_edt, binary_dilation, binary_erosion

import blocks as B
from world import X, Z, Y0, H, GROUND, SEA

# main land (rounded rectangle) and the island across the bay-strait (Odaiba)
LAND = (58, 58, 452, 440)          # x0, z0, x1, z1
ODAIBA = (384, 528, 470, 598)
PIERS = [  # (x0, z0, x1, z1) pier land strips sticking south into the bay
    (66, 436, 84, 494), (100, 436, 118, 486), (134, 436, 152, 476),
]
RIVER_X = 352
RIVER_HALF = 6
RIVER_BED = 56
EXPRESS_X = 400


def river_center(z):
    return RIVER_X + 3.0 * np.sin(np.asarray(z, float) / 38.0)


def smooth_noise(shape, sigma, rng, amp):
    n = rng.standard_normal(shape)
    n = gaussian_filter(n, sigma, mode='reflect')
    n /= n.std() + 1e-9
    return n * amp


def rrect_sdf(gx, gz, x0, z0, x1, z1, r):
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    hx, hz = (x1 - x0) / 2.0, (z1 - z0) / 2.0
    qx = np.abs(gx - cx) - (hx - r)
    qz = np.abs(gz - cz) - (hz - r)
    out = np.hypot(np.maximum(qx, 0), np.maximum(qz, 0)) + np.minimum(np.maximum(qx, qz), 0) - r
    return out


class Terrain:
    pass


def build_terrain(world, rng):
    T = Terrain()
    gz, gx = np.meshgrid(np.arange(Z, dtype=float), np.arange(X, dtype=float), indexing='ij')
    n1 = smooth_noise((Z, X), 16, rng, 3.5)
    d_main = rrect_sdf(gx, gz, *LAND, 40) + n1
    d_odb = rrect_sdf(gx, gz, *ODAIBA, 26) + smooth_noise((Z, X), 10, rng, 2.5)
    land = (d_main < 0) | (d_odb < 0)
    for (x0, z0, x1, z1) in PIERS:
        land[z0:z1 + 1, x0:x1 + 1] = True
    # every street must stand on land
    import plan as _plan
    for r in _plan.ROADS:
        for (a, b) in r.spans:
            if r.axis == 'v':
                land[a - 3:b + 4, r.c - r.hw - 1:r.c + r.hw + 2] = True
            else:
                land[r.c - r.hw - 1:r.c + r.hw + 2, a - 3:b + 4] = True
    # the strip of the main land south of the river mouth stays land; make sure no stray specks
    from scipy.ndimage import label
    lab, n = label(land)
    sizes = np.bincount(lab.ravel())
    for i in range(1, n + 1):
        if sizes[i] < 400:
            land[lab == i] = False
    T.land = land

    # river
    rc = river_center(gz)
    river = (np.abs(gx - rc) <= RIVER_HALF) & land & (gz > 40)
    # round the mouth
    T.river = river
    # pond in the big park (north centre)
    pond = ((gx - 232) / 17.0) ** 2 + ((gz - 106) / 9.0) ** 2 <= 1.0
    T.pond = pond & land

    # distance to land / nearest land cell
    dist, (iz, ix) = distance_transform_edt(~land, return_indices=True)
    T.dist = dist
    # quay (vertical sea-wall) zones: the whole south shore of the main land and the piers
    quay_land = np.zeros((Z, X), bool)
    quay_land[420:, 58:372] = True
    quay_land &= land
    for (x0, z0, x1, z1) in PIERS:
        quay_land[z0 - 2:z1 + 1, x0:x1 + 1] = True
    quay_land &= land
    T.quay_land = quay_land
    nq = gaussian_filter(quay_land[iz, ix].astype(float), 7)
    # slope per nearest land cell: bay-facing coasts are gentle
    s = 0.9 - 0.4 * np.clip((iz - 360) / 100.0, 0, 1)
    s = gaussian_filter(s, 8)
    d = dist
    top_q = 62 - np.minimum(d, 1.5) * 6.0 - s * np.maximum(d - 1.5, 0)
    top_b = 62 - s * np.maximum(d - 0.5, 0)
    top_sea = nq * top_q + (1 - nq) * top_b
    edge = np.minimum(np.minimum(gx, X - 1 - gx), np.minimum(gz, Z - 1 - gz))
    fade = np.clip((edge - 8) / 30.0, 0, 1)
    # flatten smoothly onto the flat world (y=28) toward the volume edge so there is no seam
    sm = fade * fade * (3 - 2 * fade)
    top_sea = Y0 + (top_sea - Y0) * sm
    dune = smooth_noise((Z, X), 5, rng, 1.1)
    mid = (top_sea > 33) & (top_sea < 58)
    top_sea = top_sea + np.where(mid, np.round(dune * fade), 0)
    top_sea = np.clip(np.floor(top_sea + 0.5), Y0, 61).astype(np.int16)
    top_sea = np.where(edge < 10, Y0, top_sea)
    top = np.where(land, GROUND, top_sea).astype(np.int16)
    top = np.where(river, RIVER_BED, top)
    top = np.where(T.pond, 60, top)
    T.top = top
    T.sea_floor = top_sea

    # bank cells beside river / pond / water for quays
    water_here = (top < GROUND)
    T.bank = binary_dilation(river | T.pond, structure=np.ones((3, 3), bool)) & land & ~river & ~T.pond
    near_sea = binary_dilation(~land, structure=np.ones((3, 3), bool)) & land
    T.quay_edge = near_sea & quay_land
    T.beach = binary_dilation(~land, structure=np.ones((3, 3), bool), iterations=3) & land & ~quay_land
    T.coast_dist = distance_transform_edt(land)   # distance from sea for land cells

    # ---- fill the volume ---------------------------------------------------------------------------------
    vol = world.vol
    bedmat = np.where(top >= 38, B.SAND, np.where(top >= 31, B.GRAVEL, B.STONE)).astype(np.uint16)
    is_sea = ~land
    for y in range(Y0, GROUND):
        layer = np.where(top >= y, B.STONE, B.WATER).astype(np.uint16)
        shallow = is_sea & ((top == y) | (top == y + 1)) & (top >= 31)
        layer = np.where(shallow, bedmat, layer)
        rb = (river | T.pond) & ((top == y) | (top == y + 1))
        layer = np.where(rb, B.GRAVEL, layer)
        # sea walls of banks and quays: stone bricks from the bed up to the street
        wall = (T.bank | T.quay_edge) & (y >= 53)
        layer = np.where(wall & (top >= y), B.STONE_BRICKS, layer)
        vol[y - Y0] = layer
    T.under = vol  # alias
    return T
