"""Infrastructure: expressway, elevated rail and station, river bridges, Rainbow Bridge, port (piers, cranes,
ships), boats, Odaiba."""
import numpy as np

import blocks as B
import bldg
import districts as D
import plan
import populate
import terrain
from bldg import pick, THEMES
from shapes import Frame, hip_roof
from world import GROUND, Y0, X, Z

G = GROUND
AIR = B.AIR
WHITE = B.C['white']
EXP_Y = 71          # expressway / bridge deck level
RAIL_Y = 70         # rail viaduct deck level
EXP_X = 400


# ------------------------------------------------------------------------------------------------------
def expressway(world, T, rng):
    """Elevated 2x2-lane expressway over the x=400 avenue from z=100 to the Rainbow Bridge, with a ramp in the north."""
    S = world.new('bridge', 'Shuto Expressway', value=1200)
    x0, x1 = EXP_X - 6, EXP_X + 6
    z_start, z_end = 100, 423
    # ramp down to the ground in the north
    for z in range(79, z_start):
        y = 64 + ((z - 79) * 7) // 21
        _deck_row(S, x0, x1, z, y, rng, ramp=True)
    for z in range(z_start, z_end + 1):
        _deck_row(S, x0, x1, z, EXP_Y, rng)
    # piers
    hroads = [(r.c - r.hw, r.c + r.hw) for r in plan.HLINES]
    z = 106
    while z < z_end - 4:
        if not any(a - 2 <= z <= b + 1 for a, b in hroads):
            # piers under the outer edges of the deck (on the sidewalks), so the carriageway stays clear
            for px in (x0, x1 - 1):
                S.fill(px, G + 1, z, px + 1, EXP_Y - 2, z + 1, B.C['light_gray'])
        z += 12
    return S


def _deck_row(S, x0, x1, z, y, rng, ramp=False):
    S.fill(x0, y - 1, z, x1, y - 1, z, B.C['light_gray'])            # structure
    S.fill(x0 + 2, y, z, x1 - 2, y, z, B.C['gray'])                  # asphalt
    S.fill(x0, y, z, x0 + 1, y, z, B.POL_ANDESITE)
    S.fill(x1 - 1, y, z, x1, y, z, B.POL_ANDESITE)
    S.set((x0 + x1) // 2, y, z, B.C['yellow'])
    if z % 4 < 2:
        S.set((x0 + x1) // 2 - 3, y, z, WHITE)
        S.set((x0 + x1) // 2 + 3, y, z, WHITE)
    S.set(x0, y + 1, z, WHITE)
    S.set(x1, y + 1, z, WHITE)
    if z % 12 == 0:
        S.fill(x0, y + 2, z, x0, y + 5, z, B.IRON_BARS)
        S.fill(x1, y + 2, z, x1, y + 5, z, B.IRON_BARS)
        S.set(x0, y + 6, z, B.GL['white'])
        S.set(x1, y + 6, z, B.GL['white'])


# ------------------------------------------------------------------------------------------------------
def rail_line(world, T, rng, station_rect):
    """Elevated rail viaduct along z=214 from x=62 to x=388 with a station in the middle. Returns the rail polylines."""
    sx0, sx1 = station_rect
    zc = 214
    pieces = [(62, sx0 - 1, 'Elevated rail West', 150), (sx1 + 1, 388, 'Elevated rail East', 306)]
    structs = {}
    vroads = [(r.c - r.hw, r.c + r.hw) for r in plan.VLINES]
    for (a, b, name, tx) in pieces:
        S = world.new('bridge', name, value=800)
        structs[name] = S
        for x in range(a, b + 1):
            _viaduct_col(S, x, zc, rng)
        # piers
        x = a + 8
        while x < b - 4:
            in_river = (terrain.RIVER_X - 16 <= x <= terrain.RIVER_X + 16)
            in_road = any(c0 - 1 <= x <= c1 + 1 for c0, c1 in vroads)
            if not in_river and not in_road:
                # piers on the avenue's sidewalks, with a cap beam carrying the deck over the carriageway
                for pz in (zc - 6, zc + 5):
                    S.fill(x, G + 1, pz, x + 1, RAIL_Y - 3, pz + 1, B.C['light_gray'])
                S.fill(x, RAIL_Y - 2, zc - 6, x + 1, RAIL_Y - 2, zc + 6, B.C['light_gray'])
                S.fill(x, RAIL_Y - 1, zc - 4, x + 1, RAIL_Y - 1, zc + 4, B.C['light_gray'])
            x += 10
        # catenary masts
        for x in range(a + 12, b - 6, 24):
            S.fill(x, RAIL_Y + 2, zc - 4, x, RAIL_Y + 7, zc - 4, B.IRON_BARS)
            S.fill(x, RAIL_Y + 2, zc + 4, x, RAIL_Y + 7, zc + 4, B.IRON_BARS)
            S.fill(x, RAIL_Y + 7, zc - 4, x, RAIL_Y + 7, zc + 4, B.IRON_BARS)
        _train(S, tx, zc - 2 if tx < 200 else zc + 2, rng)
    rails = [[[62, RAIL_Y + 2, zc - 2], [sx0 - 1, RAIL_Y + 2, zc - 2], [sx1 + 1, RAIL_Y + 2, zc - 2], [388, RAIL_Y + 2, zc - 2]],
             [[62, RAIL_Y + 2, zc + 2], [sx0 - 1, RAIL_Y + 2, zc + 2], [sx1 + 1, RAIL_Y + 2, zc + 2], [388, RAIL_Y + 2, zc + 2]]]
    return rails, structs


def _viaduct_col(S, x, zc, rng, platform=False):
    y = RAIL_Y
    S.fill(x, y - 1, zc - 4, x, y, zc + 4, B.C['light_gray'])         # girder + deck
    S.fill(x, y + 1, zc - 3, x, y + 1, zc + 3, B.GRAVEL)
    S.set(x, y + 1, zc - 2, B.RAIL_EW)
    S.set(x, y + 1, zc + 2, B.RAIL_EW)
    S.set(x, y + 1, zc - 4, WHITE)
    S.set(x, y + 1, zc + 4, WHITE)
    S.set(x, y + 2, zc - 4, B.IRON_BARS)
    S.set(x, y + 2, zc + 4, B.IRON_BARS)


def _train(S, x0, z, rng):
    """A 4-car electric train standing on the rails."""
    col = pick(rng, ['green', 'light_blue', 'orange', 'blue'])
    y = RAIL_Y + 2
    for car in range(4):
        a = x0 + car * 11
        S.fill(a, y, z - 1, a + 9, y, z + 1, B.C['gray'])
        S.fill(a, y + 1, z - 1, a + 9, y + 3, z + 1, B.C['white'])
        S.fill(a + 1, y + 1, z, a + 8, y + 3, z, AIR)
        S.fill(a, y + 2, z - 1, a + 9, y + 2, z - 1, B.GL['light_blue'])
        S.fill(a, y + 2, z + 1, a + 9, y + 2, z + 1, B.GL['light_blue'])
        S.fill(a, y + 1, z - 1, a + 9, y + 1, z - 1, B.C[col])
        S.fill(a, y + 1, z + 1, a + 9, y + 1, z + 1, B.C[col])
        S.fill(a + 1, y + 4, z, a + 8, y + 4, z, B.C['light_gray'])
    return S


def rail_station(world, T, rect, rng):
    """Elevated station (platforms + canopy) over the avenue plus a concourse building on the block's north edge."""
    x0, z0, x1, z1 = rect       # block rect 261..296, 221..254
    zc = 214
    S = world.new('station', 'Yurakucho Station', value=2000)
    a, b = x0 + 1, x1 - 1
    for x in range(a, b + 1):
        _viaduct_col(S, x, zc, rng)
        # platforms on both sides (13 wide deck over the avenue)
        S.fill(x, RAIL_Y, zc - 6, x, RAIL_Y, zc - 4, B.C['light_gray'])
        S.fill(x, RAIL_Y, zc + 4, x, RAIL_Y, zc + 6, B.C['light_gray'])
        S.set(x, RAIL_Y + 1, zc - 5, B.SMOOTH_STONE)
        S.set(x, RAIL_Y + 1, zc + 5, B.SMOOTH_STONE)
        S.set(x, RAIL_Y + 1, zc - 4, B.C['yellow'])
        S.set(x, RAIL_Y + 1, zc + 4, B.C['yellow'])
        S.set(x, RAIL_Y + 1, zc - 6, WHITE)
        S.set(x, RAIL_Y + 1, zc + 6, WHITE)
        S.set(x, RAIL_Y + 2, zc - 6, B.IRON_BARS)
        S.set(x, RAIL_Y + 2, zc + 6, B.IRON_BARS)
        # canopy
        S.fill(x, RAIL_Y + 7, zc - 6, x, RAIL_Y + 7, zc + 6, B.C['light_gray'])
        if (x - a) % 3 == 1:
            S.fill(x, RAIL_Y + 8, zc - 6, x, RAIL_Y + 8, zc + 6, B.GL['light_blue'])
    for x in range(a + 2, b - 1, 8):
        for zz in (zc - 6, zc + 6):
            S.fill(x, RAIL_Y + 1, zz, x, RAIL_Y + 6, zz, B.C['light_gray'])
        S.fill(x, RAIL_Y + 2, zc, x, RAIL_Y + 6, zc, B.IRON_BARS) if False else None
    # supports under the platform
    for x in range(a + 4, b - 3, 10):
        for pz in (zc - 6, zc + 5):
            S.fill(x, G + 1, pz, x + 1, RAIL_Y - 3, pz + 1, B.C['light_gray'])
        S.fill(x, RAIL_Y - 2, zc - 6, x + 1, RAIL_Y - 2, zc + 6, B.C['light_gray'])
        S.fill(x, RAIL_Y - 1, zc - 4, x + 1, RAIL_Y - 1, zc + 4, B.C['light_gray'])
    # concourse building (2 floors; roof level = platform level)
    st = dict(THEMES['concrete'])
    st.update(glass=B.GL['light_blue'], period=4, win_rows={1, 2, 3}, shop=False, gglass=B.GLASS)
    bz0, bz1 = z0, z0 + 14
    roof, slabs = bldg.shell_building(S, a, bz0, b, bz1, 2, st, rng, fh=3, g0=4, front='s', door_w=5, core=False,
                                      parapet=False, col_spacing=6)
    # stair block linking concourse roof to platform (a covered passage)
    S.fill(a + 14, RAIL_Y, bz0 - 2, a + 18, RAIL_Y, bz0 - 1, B.C['light_gray'])
    # entrance canopy and signage
    S.fill(a + 10, G + 5, bz1 + 1, a + 22, G + 5, bz1 + 3, B.C['red'])
    for xx in (a + 10, a + 22):
        S.fill(xx, G + 1, bz1 + 3, xx, G + 4, bz1 + 3, B.QUARTZ_PILLAR)
    # surrounding plaza
    return S, (zc, a, b)


def station_block_ground(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    world.raw.fill(x0, G, z0, x1, G, z1, B.STONE_BRICKS)
    populate.park(world, T, (x0, z0 + 20, x1, z1), rng, density=0.03, kinds=['cherry', 'ginkgo', 'zelkova'], paths=True)


# ------------------------------------------------------------------------------------------------------
def river_bridges(world, T, rng, shared=None):
    out = []
    shared = shared or {}
    for r in plan.HLINES:
        z0, z1 = r.c - r.hw, r.c + r.hw
        # river columns at this latitude
        xs = np.nonzero(T.river[r.c])[0]
        if len(xs) == 0:
            continue
        xa, xb = int(xs.min()) - 1, int(xs.max()) + 1
        S = shared.get(r.c) or world.new('bridge', 'River bridge', value=400)
        for z in (z0, z1):
            S.fill(xa, G + 1, z, xb, G + 1, z, WHITE)
            S.fill(xa, G + 2, z, xb, G + 2, z, B.IRON_BARS)
            for x in range(xa, xb + 1, 6):
                S.fill(x, G + 1, z, x, G + 3, z, B.C['light_gray'])
        # cars go over: parapets only at the sides; lamp heads
        out.append(S)
    # the E-W road z=214 also carries the rail, fine; the N-S roads do not cross the river
    return out


# ------------------------------------------------------------------------------------------------------
def rainbow_bridge(world, T, rng):
    S = world.new('landmark', 'Rainbow Bridge', value=4500)
    x0, x1 = EXP_X - 6, EXP_X + 6
    z_a, z_b = 424, 538
    t1, t2 = 462, 508
    # deck
    for z in range(z_a, z_b + 1):
        _deck_row(S, x0, x1, z, EXP_Y, rng)
        S.fill(x0, EXP_Y - 3, z, x1, EXP_Y - 2, z, WHITE) if False else None
    # under-deck truss girders (white) along the edges
    S.fill(x0, EXP_Y - 3, z_a, x0 + 1, EXP_Y - 2, z_b, WHITE)
    S.fill(x1 - 1, EXP_Y - 3, z_a, x1, EXP_Y - 2, z_b, WHITE)
    for z in range(z_a, z_b, 3):
        S.fill(x0 + 2, EXP_Y - 2, z, x1 - 2, EXP_Y - 2, z, B.C['light_gray'])
    # ramp down on the Odaiba side
    for z in range(z_b + 1, z_b + 25):
        y = EXP_Y - (z - z_b) // 3
        _deck_row(S, x0, x1, z, y, rng)
        S.fill(x0 + 2, G + 1, z, x1 - 2, y - 2, z, B.C['light_gray']) if y - 2 > G else None
    # approach piers over water
    for z in (444, 478, 494, 522):
        floor = int(T.top[min(z, Z - 1), EXP_X]) if 0 <= z < Z else 40
        if floor < G:
            S.fill(EXP_X - 2, max(floor, 30), z, EXP_X + 2, EXP_Y - 3, z + 1, B.C['light_gray'])
            S.fill(EXP_X - 5, EXP_Y - 3, z, EXP_X + 5, EXP_Y - 3, z + 1, B.C['light_gray'])
    # towers
    TH = 105
    for tz in (t1, t2):
        floor = int(T.top[tz, EXP_X])
        for lx in (EXP_X - 9, EXP_X + 7):
            # pier foundation in the water
            S.fill(lx - 1, max(floor - 1, 30), tz - 1, lx + 3, G + 2, tz + 3, B.STONE_BRICKS)
            S.fill(lx, G + 3, tz, lx + 2, TH, tz + 2, WHITE)
            # taper cap
            S.fill(lx, EXP_Y - 2, tz, lx + 2, EXP_Y - 1, tz + 2, B.CALCITE)
        # portal cross beams and X bracing
        for yb, th in ((EXP_Y + 12, 2), (EXP_Y + 24, 2), (TH - 1, 3)):
            S.fill(EXP_X - 9, yb, tz, EXP_X + 9, yb + th - 1, tz + 2, WHITE)
        for (ya, yb) in ((EXP_Y + 14, EXP_Y + 24), (EXP_Y + 26, TH - 2)):
            S.line((EXP_X - 7, ya, tz + 1), (EXP_X + 7, yb, tz + 1), B.C['light_gray'])
            S.line((EXP_X + 7, ya, tz + 1), (EXP_X - 7, yb, tz + 1), B.C['light_gray'])
        # saddle blocks
        S.fill(EXP_X - 6, TH + 1, tz, EXP_X - 6, TH + 1, tz + 2, B.IRON_BLOCK)
        S.fill(EXP_X + 6, TH + 1, tz, EXP_X + 6, TH + 1, tz + 2, B.IRON_BLOCK)
    # main cables
    for cx in (EXP_X - 6, EXP_X + 6):
        zc = (t1 + t2) / 2.0
        half = (t2 - t1) / 2.0
        ymin = EXP_Y + 6
        prev = None
        for z in range(t1 + 1, t2 + 1):
            y = ymin + (TH + 1 - ymin) * ((z - zc) / half) ** 2
            S.set(cx, int(round(y)), z + 1, B.IRON_BLOCK)
            if prev is not None:
                for yy in range(min(prev, int(round(y))), max(prev, int(round(y))) + 1):
                    S.set(cx, yy, z + 1, B.IRON_BLOCK)
            prev = int(round(y))
            if (z - t1) % 4 == 0 and z + 1 < t2 + 1:
                for yy in range(EXP_Y + 2, int(round(y))):
                    S.set(cx, yy, z + 1, B.CHAIN_Y)
        # side spans down to the anchorages
        for (tz, za, sgn) in ((t1, z_a + 2, -1), (t2 + 2, z_b - 2, 1)):
            ya = EXP_Y + 2
            n = abs(tz - za)
            for k in range(n + 1):
                z = tz + sgn * k
                y = TH + 1 - (TH + 1 - ya) * (k / n) ** 1.3
                S.set(cx, int(round(y)), z, B.IRON_BLOCK)
                S.set(cx, int(round(y)) - 1, z, B.IRON_BLOCK) if k < n else None
            for k in range(2, n, 4):
                z = tz + sgn * k
                y = TH + 1 - (TH + 1 - ya) * (k / n) ** 1.3
                for yy in range(EXP_Y + 2, int(round(y))):
                    S.set(cx, yy, z, B.CHAIN_Y)
    return S


# ------------------------------------------------------------------------------------------------------
def gantry_crane(world, mode, ox, oz, u, vq, rng, reach=26, name='Port crane'):
    S = world.new('industrial', name, value=700)
    F = Frame(S, mode, ox, oz)
    col = pick(rng, [B.C['light_blue'], B.C['red'], B.C['orange'], B.C['white']])
    dark = B.C['gray']
    y0 = G
    top = y0 + 17
    gap = 8
    for du in (0, 8):
        for vv in (vq, vq - gap):
            F.fill(u + du, y0 + 1, vv, u + du + 1, top, vv + 1, col)
        F.fill(u + du, top - 1, vq - gap, u + du + 1, top, vq + 1, col)
        F.line((u + du, y0 + 2, vq), (u + du, top - 2, vq - gap // 2), dark)
        F.line((u + du + 1, y0 + 2, vq - gap), (u + du + 1, top - 2, vq - gap // 2), dark)
    F.fill(u, top, vq - gap, u + 9, top, vq - gap + 1, dark)
    F.fill(u, top, vq, u + 9, top, vq + 1, dark)
    for du in (0, 9):
        F.line((u + du, top + 1, vq + 1), (u + du, top + 4, vq + reach), col)
        F.line((u + du, top + 3, vq + 1), (u + du, top + 6, vq + reach), col)
        for k in range(vq + 1, vq + reach, 4):
            F.line((u + du, top + 1 + (k - vq) // 8, k), (u + du, top + 4 + (k - vq) // 8, k + 2), dark)
    F.fill(u, top + 4, vq + reach, u + 9, top + 5, vq + reach, dark)
    F.fill(u, top + 1, vq - gap - 5, u, top + 1, vq - gap, col)
    F.fill(u + 9, top + 1, vq - gap - 5, u + 9, top + 1, vq - gap, col)
    F.fill(u, top + 2, vq - gap - 5, u + 9, top + 3, vq - gap - 3, dark)
    F.fill(u + 4, top + 1, vq - 2, u + 5, top + 9, vq - 1, col)
    F.line((u + 4, top + 9, vq - 2), (u + 4, top + 5, vq + reach), B.CHAIN_Z if mode in ('x', 'xm') else B.CHAIN_X)
    F.line((u + 5, top + 9, vq - 1), (u + 5, top + 5, vq + reach), B.CHAIN_Z if mode in ('x', 'xm') else B.CHAIN_X)
    F.fill(u + 4, top - 7, vq + reach - 6, u + 5, top - 6, vq + reach - 4, dark)
    return S


def cargo_ship(world, mode, ox, oz, L, beam, rng, name='Cargo ship', hull_col=None, deck_col=None):
    S = world.new('industrial', name, value=1500)
    F = Frame(S, mode, ox, oz)
    hull = hull_col if hull_col is not None else pick(rng, [B.C['red'], B.C['blue'], B.C['black'], B.C['gray'], B.C['green']])
    waterline = G - 1
    deck_y = G + 3
    half = beam // 2
    for u in range(L + 1):
        t = u / L
        if t < 0.08:
            hw = half - 1
        elif t > 0.72:
            hw = max(0, int(round(half * (1 - ((t - 0.72) / 0.28) ** 1.6))))
        else:
            hw = half
        draft = G - 5
        for v in range(-hw, hw + 1):
            edge = abs(v) == hw
            F.fill(u, draft, v, u, deck_y - 1, v, hull if (edge or draft == deck_y - 1) else B.C['gray'])
            if not edge:
                F.fill(u, draft + 1, v, u, deck_y - 2, v, AIR)
            F.set(u, deck_y, v, B.C['gray'])
            F.set(u, draft, v, B.C['black'])
        # bulwark
        if hw > 0 and t < 0.95:
            F.set(u, deck_y + 1, -hw, B.C['light_gray'])
            F.set(u, deck_y + 1, hw, B.C['light_gray'])
        # waterline stripe
        F.set(u, waterline, -hw, B.C['red'])
        F.set(u, waterline, hw, B.C['red'])
    # superstructure at the stern
    su0, su1 = 3, 9
    F.fill(su0, deck_y + 1, -half + 1, su1, deck_y + 7, half - 1, B.C['white'])
    F.fill(su0 + 1, deck_y + 1, -half + 2, su1 - 1, deck_y + 6, half - 2, AIR)
    for yy in (deck_y + 3, deck_y + 5):
        F.fill(su0 - 0, yy, -half + 1, su1, yy, half - 1, B.GL['light_blue'])
        F.fill(su0 + 1, yy, -half + 1, su1 - 1, yy, -half + 1, B.GL['light_blue'])
    F.fill(su0 + 1, deck_y + 8, -half + 2, su1 - 1, deck_y + 8, half - 2, B.C['white'])
    F.fill(su0 + 1, deck_y + 9, -1, su0 + 2, deck_y + 12, 1, B.C['red'])   # funnel
    F.fill(su0 + 1, deck_y + 13, -1, su0 + 2, deck_y + 13, 1, B.C['black'])
    # container stacks on deck
    cols = ['red', 'blue', 'orange', 'light_blue', 'green', 'yellow', 'light_gray', 'brown']
    u = su1 + 3
    while u + 4 < int(L * 0.7):
        for v in range(-half + 1, half - 1, 2):
            h = int(rng.integers(1, 4))
            c = pick(rng, cols)
            for k in range(h):
                F.fill(u, deck_y + 1 + k * 2, v, u + 3, deck_y + 2 + k * 2, v + 1, B.C[c])
        u += 5
    F.fill(int(L * 0.9), deck_y + 1, 0, int(L * 0.9), deck_y + 4, 0, B.IRON_BARS)
    return S


def small_boat(world, mode, ox, oz, L, rng, kind='yacht'):
    S = world.new('industrial', 'Boat', value=100)
    F = Frame(S, mode, ox, oz)
    col = pick(rng, [B.C['white'], B.C['white'], B.C['light_blue'], B.C['red'], B.C['yellow']])
    half = 2 if L < 14 else 3
    for u in range(L + 1):
        t = u / L
        hw = half if t < 0.7 else max(0, int(round(half * (1 - (t - 0.7) / 0.3))))
        for v in range(-hw, hw + 1):
            F.fill(u, G - 2, v, u, G, v, col if abs(v) == hw else B.C['gray'])
            F.set(u, G + 1, v, B.C['light_gray'] if abs(v) == hw else B.OAK_PLANKS)
    F.fill(2, G + 2, -half + 1, max(4, L // 2), G + 3, half - 1, B.C['white'])
    F.fill(3, G + 2, -half + 2, max(3, L // 2 - 1), G + 3, half - 2, AIR)
    F.fill(2, G + 3, -half + 1, max(4, L // 2), G + 3, half - 1, B.GL['light_blue'])
    F.fill(2, G + 4, -half + 1, max(4, L // 2), G + 4, half - 1, B.C['white'])
    if L >= 10:
        F.fill(L // 3, G + 5, 0, L // 3, G + 8, 0, B.IRON_BARS)
    return S


def port_piers(world, T, rng):
    """Container stacks on the three piers, cranes at the pier roots reaching over the berths, ships in the berths."""
    for (px0, pz0, px1, pz1) in terrain.PIERS:
        world.raw.fill(px0, G, pz0, px1, G, pz1, B.C['gray'])
        z = pz0 + 15           # leave the crane zone at the root of the pier clear
        while z + 12 < pz1 - 4:
            D.container_stack(world, px0 + 1, z, 4, 6, rng)
            z += 15
        for zz in range(pz0 + 4, pz1, 8):
            world.raw.set(px0, G + 1, zz, B.IRON_BARS)
            world.raw.set(px1, G + 1, zz, B.IRON_BARS)
    # cranes: P1's east edge reaches over berth 1, P3's west edge over berth 2
    gantry_crane(world, 'z', 84, 438, 0, -1, rng, reach=13)
    gantry_crane(world, 'zm', 134, 438, 0, -1, rng, reach=13)
    # two quay cranes east of the piers
    gantry_crane(world, 'x', 0, 0, 166, 437, rng, reach=22)
    gantry_crane(world, 'x', 0, 0, 192, 437, rng, reach=22)
    # ships in the berths between the piers (bow south), starting beyond the cranes
    cargo_ship(world, 'z', 92, 450, 38, 9, rng, 'Container ship')
    cargo_ship(world, 'z', 126, 450, 30, 8, rng, 'Cargo ship')
    # harbour craft along the central quay
    for (x, z, L) in ((222, 446, 14), (244, 447, 10), (262, 450, 12), (292, 448, 16), (318, 446, 9), (340, 450, 11)):
        small_boat(world, 'x', x, z, L, rng)


# ------------------------------------------------------------------------------------------------------
def odaiba(world, T, rng):
    x0, z0, x1, z1 = terrain.ODAIBA
    land = T.land
    # beach ring and central lawn/plaza
    sub = world.vol[G - Y0, z0 - 8:z1 + 8, x0 - 8:x1 + 8]
    cd = T.coast_dist[z0 - 8:z1 + 8, x0 - 8:x1 + 8]
    ld = land[z0 - 8:z1 + 8, x0 - 8:x1 + 8]
    sub[ld & (cd <= 4)] = B.SAND
    sub[ld & (cd > 4)] = B.STONE_BRICKS
    sub[ld & (cd > 14)] = B.GRASS
    # plaza core
    lots = plan.split_rect((x0 + 28, z0 + 14, x1 - 12, z0 + 40), 12, 20, 12, 18, rng)       # east of the bridge ramp
    lots += plan.split_rect((x0 + 12, z0 + 43, x1 - 12, z1 - 8), 12, 20, 12, 18, rng)       # south of the ramp
    for lot in lots:
        w, d = D.lot_size(lot)
        front = 's'
        if w >= 14 and d >= 12 and rng.random() < 0.5:
            D.office(world, lot, front, rng, 3, 6)
        elif w >= 9:
            D.shop(world, lot, front, rng, hmax=4)
    populate.park(world, T, (x0 + 2, z1 - 10, x1 - 2, z1 - 2), rng, density=0.03, paths=False)

