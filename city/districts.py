"""Per-zone lot builders: housing, shops, apartments, offices, towers, warehouses, tanks, container yards, parks."""
import numpy as np

import blocks as B
import bldg
from bldg import pick, NEON, THEMES, GLASSES, CARPET
from world import GROUND

AIR = B.AIR


def _inset(rect, i):
    x0, z0, x1, z1 = rect
    return (x0 + i, z0 + i, x1 - i, z1 - i)


def lot_size(rect):
    return rect[2] - rect[0] + 1, rect[3] - rect[1] + 1


def _style(rng, themes, glasses=None, period=4, win_rows=(1, 2), shop=False, glass=None):
    name = pick(rng, themes)
    st = dict(THEMES[name])
    st['theme'] = name
    st['glass'] = glass if glass is not None else pick(rng, glasses or GLASSES)
    st['period'] = period
    st['win_rows'] = set(win_rows)
    st['shop'] = shop
    st['gperiod'] = 5
    return st


# ---------------------------------------------------------------------------------------------------------
def house(world, rect, front, rng, dense=True):
    ins = 0 if dense and rng.random() < 0.6 else 1
    x0, z0, x1, z1 = _inset(rect, ins)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 5 or d < 5:
        return None
    # keep houses modest
    if w > 11:
        x0 += int(rng.integers(0, w - 10))
        x1 = x0 + 10
    if d > 11:
        z0 += int(rng.integers(0, d - 10))
        z1 = z0 + 10
    S = world.new('house')
    st = _style(rng, ['concrete', 'beige', 'quartz', 'mud', 'terracotta', 'brick', 'sand', 'grey', 'tuff'],
                glasses=[B.GLASS, B.GL['light_blue'], B.GL['light_gray'], B.GL['cyan']],
                period=int(rng.choice([3, 4, 5])), win_rows=(2,) if rng.random() < 0.5 else (1, 2))
    n = int(rng.choice([2, 2, 3]))
    gable = rng.random() < 0.55
    roof_y, slabs = bldg.shell_building(S, x0, z0, x1, z1, n, st, rng, fh=3, g0=3, front=front,
                                        parapet=not gable, interior=True, core=False, col_spacing=6)
    if gable:
        bldg.gable_roof(S, x0, z0, x1, z1, roof_y, st['wall'], pick(rng, bldg.ROOFS), rng, overhang=ins)
    else:
        bldg.roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=0.8)
    return S


def shop(world, rect, front, rng, hmax=5):
    x0, z0, x1, z1 = _inset(rect, 1)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 5 or d < 5:
        return None
    S = world.new('shop')
    st = _style(rng, ['concrete', 'grey', 'beige', 'quartz', 'brick', 'ochre', 'terracotta', 'blue', 'dark', 'diorite'],
                glasses=[B.GL['light_blue'], B.GL['cyan'], B.GLASS, B.GL['gray']], period=int(rng.choice([3, 4])),
                win_rows=(1, 2), shop=True)
    st['gglass'] = B.GLASS
    n = int(rng.integers(3, hmax + 1))
    roof_y, slabs = bldg.shell_building(S, x0, z0, x1, z1, n, st, rng, fh=3, g0=4, front=front, parapet=True,
                                        core=False, col_spacing=5, door_w=3)
    # sign band + awning + neon blade sign on the front
    neon = pick(rng, [B.C[c] for c in NEON])
    y_band = slabs[1]
    aw = CARPET[pick(rng, ['red', 'blue', 'green', 'orange', 'yellow', 'magenta', 'cyan', 'white'])]
    if front in ('s', 'n'):
        zz = z1 if front == 's' else z0
        out = zz + (1 if front == 's' else -1)
        S.fill(x0 + 1, y_band, zz, x1 - 1, y_band, zz, neon)
        S.fill(x0 + 1, y_band - 1, out, x1 - 1, y_band - 1, out, aw)
        sx = int(rng.integers(x0 + 1, x1))
        sh = int(rng.integers(3, 6))
        S.fill(sx, y_band + 1, out, sx, y_band + sh, out, neon if rng.random() < 0.5 else pick(rng, [B.C[c] for c in NEON]))
        if rng.random() < 0.25:
            S.set(sx, y_band + sh, out, B.SEA_LANTERN)
    else:
        xx = x1 if front == 'e' else x0
        out = xx + (1 if front == 'e' else -1)
        S.fill(xx, y_band, z0 + 1, xx, y_band, z1 - 1, neon)
        S.fill(out, y_band - 1, z0 + 1, out, y_band - 1, z1 - 1, aw)
        sz = int(rng.integers(z0 + 1, z1))
        sh = int(rng.integers(3, 6))
        S.fill(out, y_band + 1, sz, out, y_band + sh, sz, neon)
    bldg.roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=1.2)
    return S


def apartment(world, rect, front, rng, nmin=5, nmax=8):
    x0, z0, x1, z1 = _inset(rect, 1)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 8 or d < 8:
        return None
    S = world.new('apartment')
    st = _style(rng, ['concrete', 'beige', 'quartz', 'ochre', 'terracotta', 'brick', 'sand', 'mud', 'tuff', 'blue', 'green'],
                glasses=[B.GL['light_blue'], B.GL['cyan'], B.GL['gray'], B.GLASS],
                period=int(rng.choice([4, 5])), win_rows=(1, 2) if rng.random() < 0.5 else (2,))
    n = int(rng.integers(nmin, nmax + 1))
    roof_y, slabs = bldg.shell_building(S, x0, z0, x1, z1, n, st, rng, fh=3, g0=3, front=front, parapet=True,
                                        col_spacing=5, core=True)
    # balconies on the long faces
    sl = B.SL('smooth_stone')['bottom']
    long_x = w >= d
    for y in slabs[1:-1]:
        if long_x:
            for zz, out in ((z0, z0 - 1), (z1, z1 + 1)):
                for i in range(x0 + 2, x1 - 2, st['period']):
                    S.fill(i, y, out, i + 1, y, out, sl)
                    S.fill(i, y + 1, out, i + 1, y + 1, out, B.IRON_BARS)
        else:
            for xx, out in ((x0, x0 - 1), (x1, x1 + 1)):
                for i in range(z0 + 2, z1 - 2, st['period']):
                    S.fill(out, y, i, out, y, i + 1, sl)
                    S.fill(out, y + 1, i, out, y + 1, i + 1, B.IRON_BARS)
    bldg.roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=1.0)
    return S


def office(world, rect, front, rng, nmin=6, nmax=10, kind='office'):
    x0, z0, x1, z1 = _inset(rect, 1)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 10 or d < 10:
        return None
    if w > 26:
        o = int(rng.integers(0, w - 25))
        x0 += o
        x1 = x0 + 25
    if d > 26:
        o = int(rng.integers(0, d - 25))
        z0 += o
        z1 = z0 + 25
    S = world.new(kind)
    st = _style(rng, ['concrete', 'grey', 'quartz', 'dark', 'diorite', 'blue', 'tuff', 'sand'],
                glasses=[B.GL['light_blue'], B.GL['cyan'], B.GL['gray'], B.GL['blue'], B.TINTED],
                period=int(rng.choice([4, 6, 8])), win_rows=(1, 2))
    if rng.random() < 0.5:
        st['mull'] = pick(rng, [B.IRON_BLOCK, B.C['light_gray'], B.QUARTZ])
        st['mull_period'] = 2
    n = int(rng.integers(nmin, nmax + 1))
    roof_y, slabs = bldg.shell_building(S, x0, z0, x1, z1, n, st, rng, fh=3, g0=4, front=front, parapet=True,
                                        col_spacing=5, core=True, door_w=3)
    if rng.random() < 0.06 and w >= 12 and d >= 12:
        bldg.helipad(S, x0, z0, x1, z1, roof_y)
    else:
        bldg.roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=1.0, tall=True)
    return S


def tower(world, rect, front, rng, nmin=11, nmax=15):
    x0, z0, x1, z1 = _inset(rect, 1)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 14 or d < 14:
        return None
    # cap footprint
    W_ = int(min(w, rng.integers(21, 31)))
    D_ = int(min(d, rng.integers(21, 31)))
    x0 += int(rng.integers(0, w - W_ + 1))
    z0 += int(rng.integers(0, d - D_ + 1))
    x1, z1 = x0 + W_ - 1, z0 + D_ - 1
    S = world.new('tower')
    glass = pick(rng, [B.GL['light_blue'], B.GL['cyan'], B.GL['blue'], B.GL['gray'], B.TINTED])
    th = pick(rng, ['concrete', 'grey', 'dark', 'quartz', 'blue'])
    st = dict(THEMES[th])
    st['glass'] = glass
    st['period'] = int(rng.choice([4, 6, 8]))
    st['win_rows'] = {1, 2}
    st['shop'] = False
    st['mull'] = pick(rng, [B.IRON_BLOCK, B.C['light_gray'], B.QUARTZ, B.C['white']])
    st['mull_period'] = 2
    st['span'] = pick(rng, [B.C['gray'], B.C['black'], B.IRON_BLOCK, B.C['light_gray']])
    n = int(rng.integers(nmin, nmax + 1))
    # tiers: base, then up to two setbacks
    tiers = [n]
    if n >= 12 and rng.random() < 0.7:
        a = int(rng.integers(4, 6))
        tiers = [a, n - a]
        if tiers[1] >= 8 and rng.random() < 0.5:
            b = int(rng.integers(3, 5))
            tiers = [a, tiers[1] - b, b]
    cur = (x0, z0, x1, z1)
    base = GROUND
    roof_y = base
    for ti, fl in enumerate(tiers):
        roof_y, slabs = bldg.shell_building(S, *cur, fl, st, rng, fh=3, g0=4 if ti == 0 else 3, base_y=base, front=front,
                                            door=(ti == 0), parapet=True, col_spacing=5,
                                            core=True, foundation=(ti == 0), door_w=3)
        base = roof_y
        sb = int(rng.integers(2, 4))
        cur = (cur[0] + sb, cur[1] + sb, cur[2] - sb, cur[3] - sb)
        if cur[2] - cur[0] < 8 or cur[3] - cur[1] < 8:
            break
    cx0, cz0, cx1, cz1 = _inset((x0, z0, x1, z1), 0)
    r = rng.random()
    ux0, uz0, ux1, uz1 = cur
    if r < 0.12 and len(tiers) == 1:
        bldg.helipad(S, x0, z0, x1, z1, roof_y)
    elif r < 0.3:
        bldg.roof_clutter(S, *cur, roof_y, rng, density=1.0, tall=True)
    elif r < 0.75:
        mx, mz = (ux0 + ux1) // 2, (uz0 + uz1) // 2
        S.fill(mx - 1, roof_y + 1, mz - 1, mx, roof_y + 3, mz, st['pil'])
        S.fill(mx, roof_y + 4, mz, mx, roof_y + 14, mz, B.CHAIN_Y)
        S.fill(mx, roof_y + 10, mz, mx, roof_y + 10, mz, B.C['red'])
    else:
        bldg.roof_clutter(S, *cur, roof_y, rng, density=1.5, tall=True)
    return S


def warehouse(world, rect, front, rng):
    x0, z0, x1, z1 = _inset(rect, 1)
    w, d = x1 - x0 + 1, z1 - z0 + 1
    if w < 12 or d < 12:
        return None
    S = world.new('industrial')
    wall = pick(rng, [B.C['light_gray'], B.C['white'], B.C['light_blue'], B.IRON_BLOCK, B.C['gray'], B.C['cyan']])
    st = dict(wall=wall, pil=B.C['gray'], span=B.C['gray'], floor=B.C['light_gray'], core=B.C['gray'],
              glass=B.GL['light_blue'], period=6, win_rows={3}, shop=False)
    h = int(rng.integers(7, 11))
    # a single tall storey: slabs at base and roof only
    roof_y = GROUND + h
    S.fill(x0, GROUND, z0, x1, GROUND, z1, B.C['light_gray'])
    S.shell(x0, GROUND + 1, z0, x1, roof_y - 1, z1, wall, top=False, bottom=False)
    # window strip near the top and big doors
    S.fill(x0 + 1, roof_y - 3, z0, x1 - 1, roof_y - 2, z0, B.GL['light_blue'])
    S.fill(x0 + 1, roof_y - 3, z1, x1 - 1, roof_y - 2, z1, B.GL['light_blue'])
    S.fill(x0, GROUND + 1, z0, x0, roof_y - 1, z0, B.C['gray'])
    S.fill(x1, GROUND + 1, z0, x1, roof_y - 1, z0, B.C['gray'])
    S.fill(x0, GROUND + 1, z1, x0, roof_y - 1, z1, B.C['gray'])
    S.fill(x1, GROUND + 1, z1, x1, roof_y - 1, z1, B.C['gray'])
    for xs in range(x0 + 6, x1 - 3, 6):     # frame columns visible in the wall and inside
        S.fill(xs, GROUND + 1, z0, xs, roof_y - 1, z0, B.C['gray'])
        S.fill(xs, GROUND + 1, z1, xs, roof_y - 1, z1, B.C['gray'])
        S.fill(xs, GROUND + 1, (z0 + z1) // 2, xs, roof_y - 1, (z0 + z1) // 2, B.C['gray'])
    # big door
    dz = z1 if front == 's' else z0
    dc = (x0 + x1) // 2
    S.fill(dc - 2, GROUND + 1, dz, dc + 2, GROUND + 5, dz, AIR)
    # low gabled / flat roof
    roof_name = pick(rng, ['stone_brick', 'deepslate_tile', 'polished_deepslate'])
    S.fill(x0, roof_y, z0, x1, roof_y, z1, B.C['gray'])
    if rng.random() < 0.6:
        bldg.gable_roof(S, x0, z0, x1, z1, roof_y, wall, roof_name, rng, overhang=0,
                        ridge_axis='x' if w >= d else 'z')
    else:
        S.shell(x0, roof_y + 1, z0, x1, roof_y + 1, z1, wall, top=False, bottom=False)
        bldg.roof_clutter(S, x0, z0, x1, z1, roof_y, rng, density=1.0)
    # interior pallet racks (sparse)
    for _ in range(int((w * d) // 60)):
        rx = int(rng.integers(x0 + 2, x1 - 3))
        rz = int(rng.integers(z0 + 2, z1 - 3))
        S.fill(rx, GROUND + 1, rz, rx + 2, GROUND + int(rng.integers(1, 4)), rz + 1, pick(rng, [B.OAK_PLANKS, B.C['orange'], B.C['brown']]))
    return S


def fuel_tank(world, cx, cz, r, h, rng, name='Fuel tank'):
    """A recognisable cylindrical storage tank: white shell, red band, floating-roof rim, ladder, pipe."""
    S = world.new('industrial', name)
    y0 = GROUND
    S.disk(cx, cz, r + 1, y0, B.C['gray'])
    S.cyl(cx, cz, r, y0 + 1, y0 + h, B.C['white'], hollow=True)
    S.disk(cx, cz, r, y0 + h, B.C['light_gray'])            # roof
    S.disk(cx, cz, r - 1, y0 + h + 1, B.C['white']) if r > 4 else None
    S.cyl(cx, cz, r, y0 + h - 1, y0 + h - 1, B.C['red'], hollow=True)   # red band under the rim
    S.cyl(cx, cz, r, y0 + 2, y0 + 2, B.C['red'], hollow=True)
    S.cyl(cx, cz, r, y0 + h + 1, y0 + h + 1, B.IRON_BARS, hollow=True)  # rail around the roof
    # ladder on the south side
    for y in range(y0 + 1, y0 + h + 1):
        S.set(cx, y, cz + r + 1, B.LADDER['north'])
    # pipe
    S.fill(cx + r + 1, y0 + 1, cz - 1, cx + r + 3, y0 + 1, cz - 1, B.IRON_BLOCK)
    return S


def sphere_tank(world, cx, cz, r, rng):
    S = world.new('industrial', 'Gas sphere')
    y0 = GROUND
    cy = y0 + r + 3
    for lx, lz in ((-r // 2, -r // 2), (r // 2, -r // 2), (-r // 2, r // 2), (r // 2, r // 2)):
        S.fill(cx + lx, y0 + 1, cz + lz, cx + lx, cy - 1, cz + lz, B.IRON_BARS)
    S.disk(cx, cz, r // 2 + 2, y0, B.C['gray'])
    # hollow sphere shell
    for y in range(int(cy - r), int(cy + r) + 1):
        for rr, blk in ((r, B.C['white']),):
            h = np.sqrt(max(0.0, rr * rr - (y - cy) ** 2))
            if h < 0.7:
                S.set(cx, y, cz, blk)
                continue
            rin = np.sqrt(max(0.0, (rr - 1.3) ** 2 - (y - cy) ** 2))
            gx, gz = np.meshgrid(np.arange(int(cx - h - 1), int(cx + h + 2)), np.arange(int(cz - h - 1), int(cz + h + 2)))
            dd = np.hypot(gx - cx, gz - cz)
            m = (dd <= h + 0.3) & (dd >= rin)
            S.pts(gx[m], np.full(m.sum(), y), gz[m], blk)
    S.fill(cx, int(cy + r), cz, cx, int(cy + r) + 2, cz, B.IRON_BARS)
    return S


def container_stack(world, x0, z0, nx, nz, rng, heights=None, name='Container stack', rot=False):
    """A block of container stacks: each container is 4 long (x) x 2 wide (z) x 2 high."""
    S = world.new('industrial', name)
    cols = [B.C['red'], B.C['blue'], B.C['orange'], B.C['light_blue'], B.C['green'], B.C['yellow'],
            B.C['light_gray'], B.C['brown'], B.C['cyan'], B.C['white'], B.C['purple']]
    for i in range(nx):
        for j in range(nz):
            h = int(rng.integers(1, 5)) if heights is None else heights
            col = pick(rng, cols)
            for k in range(h):
                c = col if rng.random() < 0.6 else pick(rng, cols)
                bx = x0 + i * 4
                bz = z0 + j * 2
                S.fill(bx, GROUND + 1 + k * 2, bz, bx + 3, GROUND + 2 + k * 2, bz + 1, c)
                # dark door end to read as a container
                S.fill(bx, GROUND + 1 + k * 2, bz, bx, GROUND + 2 + k * 2, bz + 1, B.C['gray'])
    return S


def gantry_crane(world, x, zq, rng, reach=26):
    """Ship-to-shore crane: two portal frames straddling the quay (along x), boom reaching south over the water."""
    S = world.new('industrial', 'Port crane')
    col = pick(rng, [B.C['light_blue'], B.C['red'], B.C['white'], B.C['orange']])
    dark = B.C['gray']
    y0 = GROUND
    top = y0 + 17
    gap = 12     # landward spacing between front and rear legs
    for dx in (0, 8):
        for zz in (zq, zq - gap):
            S.fill(x + dx, y0 + 1, zz, x + dx + 1, top, zz + 1, col)
        # portal beam between front and rear legs
        S.fill(x + dx, top - 1, zq - gap, x + dx + 1, top, zq + 1, col)
        # diagonal brace in the portal
        S.line((x + dx, y0 + 2, zq), (x + dx, top - 2, zq - gap // 2), dark)
        S.line((x + dx + 1, y0 + 2, zq - gap), (x + dx + 1, top - 2, zq - gap // 2), dark)
    # longitudinal beams tying the frames
    S.fill(x, top, zq - gap, x + 9, top, zq - gap + 1, dark)
    S.fill(x, top, zq, x + 9, top, zq + 1, dark)
    # boom: two girders, rising slightly toward the tip
    for dx in (0, 9):
        S.line((x + dx, top + 1, zq + 1), (x + dx, top + 4, zq + reach), col)
        S.line((x + dx, top + 3, zq + 1), (x + dx, top + 6, zq + reach), col)
        for k in range(zq + 1, zq + reach, 4):
            S.line((x + dx, top + 1 + (k - zq) // 8, k), (x + dx, top + 3 + (k - zq) // 8 + 1, k + 2), dark)
    S.fill(x, top + 4, zq + reach, x + 9, top + 5, zq + reach, dark)       # tip beam
    # back boom with counterweight
    S.fill(x, top + 1, zq - gap - 6, x, top + 1, zq - gap, col)
    S.fill(x + 9, top + 1, zq - gap - 6, x + 9, top + 1, zq - gap, col)
    S.fill(x, top + 2, zq - gap - 6, x + 9, top + 3, zq - gap - 4, dark)
    # A-frame mast with stays
    S.fill(x + 4, top + 1, zq - 2, x + 5, top + 9, zq - 1, col)
    S.line((x + 4, top + 9, zq - 2), (x + 4, top + 5, zq + reach), B.CHAIN_Z)
    S.line((x + 5, top + 9, zq - 1), (x + 5, top + 5, zq + reach), B.CHAIN_Z)
    S.line((x + 4, top + 9, zq - 2), (x + 4, top + 3, zq - gap - 6), B.CHAIN_Z)
    # spreader hanging
    S.fill(x + 4, top - 6, zq + reach - 6, x + 5, top - 5, zq + reach - 4, dark)
    return S
