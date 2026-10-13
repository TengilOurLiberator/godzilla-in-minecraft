"""Generic building generator: floors, outer walls with windows, interior columns and core walls, roofs and
roof clutter.  Everything is written through a world.Struct so the building owns its blocks."""
import numpy as np

import blocks as B
from world import GROUND

AIR = B.AIR
CARPET = {c: B.b(c + '_carpet') for c in B.COLORS}

NEON = ['magenta', 'cyan', 'yellow', 'lime', 'orange', 'pink', 'light_blue', 'red']


def pick(rng, seq):
    return seq[int(rng.integers(0, len(seq)))]


# ---- material themes -------------------------------------------------------------------------------------
def themes():
    C, TC = B.C, B.TC
    return {
        'concrete': dict(wall=C['white'], pil=C['light_gray'], span=C['light_gray'], floor=C['light_gray'], core=C['gray']),
        'grey': dict(wall=C['light_gray'], pil=C['gray'], span=C['gray'], floor=C['light_gray'], core=C['gray']),
        'quartz': dict(wall=B.QUARTZ, pil=B.SMOOTH_QUARTZ, span=B.CALCITE, floor=B.SMOOTH_QUARTZ, core=B.QUARTZ_BRICKS),
        'beige': dict(wall=TC['white'], pil=B.SANDSTONE, span=B.CUT_SANDSTONE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'sand': dict(wall=B.CUT_SANDSTONE, pil=B.SANDSTONE, span=B.SMOOTH_SANDSTONE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'brick': dict(wall=B.BRICKS, pil=B.BRICKS, span=B.STONE_BRICKS, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'mud': dict(wall=B.MUD_BRICKS, pil=B.PACKED_MUD, span=B.MUD_BRICKS, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'ochre': dict(wall=TC['yellow'], pil=TC['orange'], span=B.CUT_SANDSTONE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'terracotta': dict(wall=TC['plain'], pil=TC['orange'], span=TC['white'], floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'dark': dict(wall=C['gray'], pil=C['black'], span=C['black'], floor=C['light_gray'], core=C['gray']),
        'tuff': dict(wall=B.TUFF_BRICKS, pil=B.POLISHED_TUFF, span=B.POL_ANDESITE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'diorite': dict(wall=B.POL_DIORITE, pil=B.DIORITE, span=B.POL_DIORITE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS),
        'blue': dict(wall=C['white'], pil=C['light_blue'], span=C['light_blue'], floor=C['light_gray'], core=C['gray']),
        'green': dict(wall=C['white'], pil=C['green'], span=C['green'], floor=C['light_gray'], core=C['gray']),
        'red': dict(wall=C['white'], pil=C['red'], span=C['red'], floor=C['light_gray'], core=C['gray']),
    }


THEMES = themes()
GLASSES = [B.GL['light_blue'], B.GL['cyan'], B.GL['gray'], B.GL['blue'], B.GLASS, B.TINTED, B.GL['light_gray']]
ROOFS = ['deepslate_tile', 'stone_brick', 'brick', 'mud_brick', 'polished_deepslate', 'tuff_brick',
         'oxidized_cut_copper', 'cut_copper']


# ---- facade ---------------------------------------------------------------------------------------------
def facade(L, rows_y, slab_set, prev_slab, st, g0_top, rng):
    """Pattern (R, L) of palette indices for a wall face of length L over the given y rows.
    rows_y: array of y; slab_set: set of slab y; prev_slab: dict y -> y of slab beneath."""
    cols = np.arange(L)
    p = st['period']
    is_pil = (cols % p == 0) | (cols == 0) | (cols == L - 1)
    mull = st.get('mull')
    is_mull = np.zeros(L, bool)
    if mull is not None:
        is_mull = (cols % st['mull_period'] == 0)
    win = np.where(is_pil, st['pil'], np.where(is_mull, st['mull'] if mull is not None else st['pil'], st['glass']))
    wall = np.where(is_pil, st['pil'], st['wall'])
    span = np.full(L, st['span'])
    # ground floor shopfront: wide glass
    gwin = np.where((cols == 0) | (cols == L - 1) | (cols % st.get('gperiod', 5) == 0), st['pil'], st.get('gglass', st['glass']))
    templ = [win, wall, span, gwin]
    codes = np.zeros(len(rows_y), int)
    for r, y in enumerate(rows_y):
        if y in slab_set:
            codes[r] = 2
            continue
        off = y - prev_slab[y]
        if y < g0_top and st.get('shop'):
            codes[r] = 3
        elif off in st['win_rows']:
            codes[r] = 0
        else:
            codes[r] = 1
    tarr = np.array(templ, dtype=np.int64)
    pat = tarr[codes]                      # (R, L)
    # ground storey with 'solid' ground option
    return pat.astype(np.int64)


def make_style(rng, kind):
    th = dict(pick(rng, list(THEMES.values())))
    st = dict(th)
    st['glass'] = pick(rng, GLASSES)
    st['period'] = 4
    st['win_rows'] = {1, 2}
    st['gperiod'] = 5
    st['shop'] = False
    return st


def shell_building(S, x0, z0, x1, z1, n, st, rng, fh=3, g0=None, base_y=GROUND, front='s', door=True,
                   parapet=True, interior=True, col_spacing=5, core=True, foundation=True, door_w=2):
    """Box building footprint x0..x1 / z0..z1 (inclusive).  Returns (roof_y, slabs)."""
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    g0 = fh if g0 is None else g0
    slabs = [base_y] + [base_y + g0 + k * fh for k in range(n)]
    roof_y = slabs[-1]
    slab_set = set(slabs[1:])
    prev = {}
    last = base_y
    for y in range(base_y + 1, roof_y + 1):
        prev[y] = last
        if y in slab_set:
            last = y
    rows_y = np.arange(base_y + 1, roof_y + 1)
    # foundation and floors
    if foundation:
        S.fill(x0, base_y, z0, x1, base_y, z1, st['floor'])
    for y in slabs[1:-1]:
        S.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, st['floor'])
    S.fill(x0, roof_y, z0, x1, roof_y, z1, st.get('roofb', st['floor']))
    # walls
    g0_top = slabs[1]
    pat_x = facade(w, rows_y, slab_set, prev, st, g0_top, rng)
    pat_z = facade(d, rows_y, slab_set, prev, st, g0_top, rng)
    R = len(rows_y)
    S.put(x0, base_y + 1, z0, pat_x[:, None, :])
    S.put(x0, base_y + 1, z1, pat_x[:, None, :])
    S.put(x0, base_y + 1, z0, pat_z[:, :, None])
    S.put(x1, base_y + 1, z0, pat_z[:, :, None])
    # the slab rows at each floor are continuous with the walls already; re-lay the corners as pillars
    for (cx, cz) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        S.fill(cx, base_y + 1, cz, cx, roof_y, cz, st['pil'])
    if interior and w >= 7 and d >= 7:
        _interior(S, x0, z0, x1, z1, slabs, st, col_spacing, core and w >= 11 and d >= 11, rng)
    if door:
        _door(S, x0, z0, x1, z1, front, base_y, st, rng, door_w)
    if parapet:
        pb = st.get('parapet', st['pil'])
        S.shell(x0, roof_y + 1, z0, x1, roof_y + 1, z1, pb, top=False, bottom=False)
    return roof_y, slabs


def _interior(S, x0, z0, x1, z1, slabs, st, sp, core, rng):
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    top = slabs[-1] - 1
    cx0 = cx1 = cz0 = cz1 = None
    if core:
        cw = 5 if w >= 14 else 4
        cd = 5 if d >= 14 else 4
        cx0 = x0 + (w - cw) // 2
        cz0 = z0 + (d - cd) // 2
        cx1, cz1 = cx0 + cw - 1, cz0 + cd - 1
        S.shell(cx0, slabs[0] + 1, cz0, cx1, top, cz1, st['core'], top=False, bottom=False)
        # doorway to the core on each storey (south wall)
        for y in slabs[:-1]:
            S.fill(cx0 + 1, y + 1, cz1, cx0 + 1, y + 2, cz1, AIR)
            if cw >= 5:
                S.fill(cx1 - 1, y + 1, cz0, cx1 - 1, y + 2, cz0, AIR)
    # pillars on a grid
    xs = list(range(x0 + sp, x1 - 2, sp))
    zs = list(range(z0 + sp, z1 - 2, sp))
    pcol = st['pil']
    for gx in xs:
        for gz in zs:
            if core and cx0 - 1 <= gx <= cx1 + 1 and cz0 - 1 <= gz <= cz1 + 1:
                continue
            S.fill(gx, slabs[0] + 1, gz, gx, top, gz, pcol)


def _door(S, x0, z0, x1, z1, front, base_y, st, rng, door_w=2):
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    if front in ('s', 'n'):
        zz = z1 if front == 's' else z0
        c = x0 + w // 2 - door_w // 2
        S.fill(c, base_y + 1, zz, c + door_w - 1, base_y + 2, zz, AIR)
    else:
        xx = x1 if front == 'e' else x0
        c = z0 + d // 2 - door_w // 2
        S.fill(xx, base_y + 1, c, xx, base_y + 2, c + door_w - 1, AIR)


# ---- roofs ----------------------------------------------------------------------------------------------
def gable_roof(S, x0, z0, x1, z1, roof_y, wall_blk, roof_name, rng, ridge_axis=None, overhang=1):
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    if ridge_axis is None:
        ridge_axis = 'x' if w >= d else 'z'
    ST = B.ST(roof_name)
    full = B.b('minecraft:' + roof_name + 's') if roof_name.endswith('brick') or roof_name.endswith('tile') else None
    if full is None:
        full = {'polished_deepslate': B.POL_DEEPSLATE, 'oxidized_cut_copper': B.OX_CUT_COPPER,
                'cut_copper': B.CUT_COPPER, 'brick': B.BRICKS}.get(roof_name, B.STONE_BRICKS)
    if ridge_axis == 'x':
        a0, a1 = z0 - overhang, z1 + overhang
        i = 0
        while a0 + i <= a1 - i:
            y = roof_y + 1 + i
            za, zb = a0 + i, a1 - i
            if za < zb:
                S.fill(x0, y, za, x1, y, za, ST[('south', 'bottom')])
                S.fill(x0, y, zb, x1, y, zb, ST[('north', 'bottom')])
                if za + 1 < zb:       # filler under the next layer so the roof is face-connected
                    S.fill(x0, y, za + 1, x1, y, za + 1, full)
                    S.fill(x0, y, zb - 1, x1, y, zb - 1, full)
                for z in range(za + 1, zb):
                    if z0 <= z <= z1:
                        S.set(x0, y, z, wall_blk)
                        S.set(x1, y, z, wall_blk)
            else:
                S.fill(x0, y, za, x1, y, za, full)
            i += 1
    else:
        a0, a1 = x0 - overhang, x1 + overhang
        i = 0
        while a0 + i <= a1 - i:
            y = roof_y + 1 + i
            xa, xb = a0 + i, a1 - i
            if xa < xb:
                S.fill(xa, y, z0, xa, y, z1, ST[('east', 'bottom')])
                S.fill(xb, y, z0, xb, y, z1, ST[('west', 'bottom')])
                if xa + 1 < xb:
                    S.fill(xa + 1, y, z0, xa + 1, y, z1, full)
                    S.fill(xb - 1, y, z0, xb - 1, y, z1, full)
                for x in range(xa + 1, xb):
                    if x0 <= x <= x1:
                        S.set(x, y, z0, wall_blk)
                        S.set(x, y, z1, wall_blk)
            else:
                S.fill(xa, y, z0, xa, y, z1, full)
            i += 1
    return roof_y + i


def roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=1.0, tall=False):
    """Water tanks, AC units, antennas, stair bulkhead and signs on a flat roof (parapet already placed)."""
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    if w < 5 or d < 5:
        return
    y = roof_y + 1
    taken = np.zeros((d, w), bool)

    def free(ax0, az0, aw, ad):
        if ax0 < x0 + 1 or az0 < z0 + 1 or ax0 + aw - 1 > x1 - 1 or az0 + ad - 1 > z1 - 1:
            return False
        sub = taken[az0 - z0 - 0: az0 - z0 + ad, ax0 - x0: ax0 - x0 + aw]
        if sub.any():
            return False
        taken[az0 - z0: az0 - z0 + ad, ax0 - x0: ax0 - x0 + aw] = True
        return True

    n_items = int((1 + w * d / 120.0) * density)
    n_items = max(1, min(n_items, 10))
    for _ in range(n_items):
        r = rng.random()
        ax = int(rng.integers(x0 + 1, max(x0 + 2, x1 - 2)))
        az = int(rng.integers(z0 + 1, max(z0 + 2, z1 - 2)))
        if r < 0.28:       # AC units: grey boxes with grille on top
            aw, ad = int(rng.integers(1, 3)), int(rng.integers(1, 3))
            if free(ax, az, aw, ad):
                S.fill(ax, y, az, ax + aw - 1, y, az + ad - 1, B.C['light_gray'])
                S.fill(ax, y + 1, az, ax + aw - 1, y + 1, az + ad - 1, B.COPPER_GRATE if rng.random() < 0.3 else B.IRON_BLOCK)
        elif r < 0.5:      # water tank on legs
            if free(ax, az, 3, 3):
                for lx, lz in ((ax, az), (ax + 2, az), (ax, az + 2), (ax + 2, az + 2)):
                    S.fill(lx, y, lz, lx, y + 1, lz, B.IRON_BARS)
                S.fill(ax, y + 2, az, ax + 2, y + 4, az + 2, pick(rng, [B.C['light_gray'], B.OAK_PLANKS, B.IRON_BLOCK, B.SPRUCE_PLANKS]))
                S.fill(ax + 1, y + 5, az + 1, ax + 1, y + 5, az + 1, B.SMOOTH_STONE)
        elif r < 0.62:     # antenna mast
            if free(ax, az, 1, 1):
                h = int(rng.integers(4, 9 if tall else 7))
                S.fill(ax, y, az, ax, y + h, az, B.CHAIN_Y if rng.random() < 0.5 else B.LIGHTNING_ROD)
                if rng.random() < 0.5:
                    S.set(ax, y + h - 1, az, B.C['red'])
        elif r < 0.78:     # bulkhead (stair exit)
            if free(ax, az, 3, 3):
                S.shell(ax, y, az, ax + 2, y + 2, az + 2, B.C['light_gray'], top=True, bottom=False)
                S.fill(ax + 1, y, az + 2, ax + 1, y + 1, az + 2, AIR)
        elif r < 0.9:      # solar panel row
            aw = int(rng.integers(3, 6))
            if free(ax, az, aw, 2):
                S.fill(ax, y, az, ax + aw - 1, y, az + 1, B.C['blue'])
        else:              # billboard
            aw = int(rng.integers(4, 8))
            if free(ax, az, aw, 1) and w >= 8:
                col = pick(rng, [B.C[c] for c in NEON])
                S.fill(ax, y, az, ax, y + 1, az, B.IRON_BARS)
                S.fill(ax + aw - 1, y, az, ax + aw - 1, y + 1, az, B.IRON_BARS)
                S.fill(ax, y + 2, az, ax + aw - 1, y + 3, az, col)


def helipad(S, x0, z0, x1, z1, roof_y):
    """7x7 helipad with a yellow H on dark ground in the middle of the roof."""
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    y = roof_y + 1
    S.fill(cx - 3, y, cz - 3, cx + 3, y, cz + 3, B.C['gray'])
    S.fill(cx - 2, y, cz - 2, cx - 2, y, cz + 2, B.C['yellow'])
    S.fill(cx + 2, y, cz - 2, cx + 2, y, cz + 2, B.C['yellow'])
    S.fill(cx - 2, y, cz, cx + 2, y, cz, B.C['yellow'])
    S.shell(cx - 4, y, cz - 4, cx + 4, y, cz + 4, B.C['red'], top=False, bottom=False)
