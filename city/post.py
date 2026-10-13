"""Post passes over the finished volume (connection flags for bars etc.)."""
import numpy as np

import blocks as B
from world import Y0, X, Z, H


def solid_lut(states):
    lut = np.zeros(len(states) + 1, bool)
    bad = ('_stairs', '_slab', '_leaves', '_carpet', 'chain', 'ladder', 'rod', 'lantern', 'rail', 'scaffolding',
           'iron_bars', 'trapdoor')
    for i, s in enumerate(states):
        n = s.split(':', 1)[1].split('[', 1)[0]
        if n in ('air', 'water'):
            continue
        if any(t in n for t in bad):
            continue
        lut[i] = True
    return lut


def connect_bars(world):
    vol = world.vol
    fam = B.CONNECT_FAMILIES['iron_bars']
    idxs = np.array(sorted(fam.values()))
    is_bar = np.isin(vol, idxs)
    ys, zs, xs = np.nonzero(is_bar)
    if len(ys) == 0:
        return 0
    lut = solid_lut(B.PAL.states)
    conn = lut | is_bar_lut(idxs, len(B.PAL.states))

    def nb(dy, dz, dx):
        y2, z2, x2 = ys + dy, zs + dz, xs + dx
        ok = (y2 >= 0) & (y2 < H) & (z2 >= 0) & (z2 < Z) & (x2 >= 0) & (x2 < X)
        out = np.zeros(len(ys), bool)
        out[ok] = conn[vol[y2[ok], z2[ok], x2[ok]]]
        return out

    n = nb(0, -1, 0)
    s = nb(0, 1, 0)
    e = nb(0, 0, 1)
    w = nb(0, 0, -1)
    code = n.astype(int) * 8 + s.astype(int) * 4 + e.astype(int) * 2 + w.astype(int)
    table = np.zeros(16, np.uint16)
    for (nn, ss, ee, ww), i in fam.items():
        table[int(nn) * 8 + int(ss) * 4 + int(ee) * 2 + int(ww)] = i
    vol[ys, zs, xs] = table[code]
    return len(ys)


def is_bar_lut(idxs, n):
    lut = np.zeros(n + 1, bool)
    lut[idxs] = True
    return lut
