"""Hand-designed landmarks: Tokyo Tower, Ferris wheel, National Diet Building, Tokyo Station, Senso-ji,
Metropolitan Government twin towers."""
import numpy as np

import blocks as B
import bldg
import shapes
from shapes import hip_roof, Frame, circle_pts
from bldg import pick, THEMES
from world import GROUND, Y0
import populate

G = GROUND
AIR = B.AIR
RED = B.C['red']
WHITE = B.C['white']


def _plaza(world, rect, blk):
    x0, z0, x1, z1 = rect
    world.raw.fill(x0, G, z0, x1, G, z1, blk)


# =========================================================================================================
def tokyo_tower(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2 + 2
    populate.park(world, T, rect, rng, density=0.03, kinds=['oak', 'cherry', 'ginkgo', 'maple', 'zelkova'], paths=False)
    # paved plaza around the foot
    _plaza(world, (cx - 13, cz - 13, cx + 13, cz + 13), B.STONE_BRICKS)
    world.raw.fill(cx - 13, G + 1, cz - 13, cx + 13, G + 40, cz + 13, AIR)
    S = world.new('landmark', 'Tokyo Tower', value=2500)
    H1, H2 = 34, 58

    def hw(h):
        return 2.0 + 8.0 * max(0.0, 1 - h / 64.0) ** 2.0

    def colr(h):
        return RED if (h // 8) % 2 == 0 else WHITE

    # four corner legs: 2x2 below the main deck, 1x1 above it
    for h in range(1, H2 + 1):
        wi = int(round(hw(h)))
        col = colr(h)
        t = 2 if h < H1 else 1
        for sx in (-1, 1):
            for sz in (-1, 1):
                xo, zo = cx + sx * wi, cz + sz * wi
                xi, zi = cx + sx * (wi - t + 1), cz + sz * (wi - t + 1)
                S.fill(min(xo, xi), G + h, min(zo, zi), max(xo, xi), G + h, max(zo, zi), col)
    for sx in (-1, 1):
        for sz in (-1, 1):
            S.fill(cx + sx * 10 - 1, G, cz + sz * 10 - 1, cx + sx * 10 + 1, G, cz + sz * 10 + 1, B.C['gray'])
    # lattice: a ring beam at each level, and an X between consecutive levels on every face
    levels = [0, 8, 16, 24, 32, 44, 52, 58]

    def corner(h, sx, sz):
        wi = int(round(hw(h)))
        return (cx + sx * wi, G + h, cz + sz * wi)

    for k, h in enumerate(levels):
        c = [corner(h, -1, -1), corner(h, 1, -1), corner(h, 1, 1), corner(h, -1, 1)]
        if h > 0:
            for a, b in zip(c, c[1:] + c[:1]):
                S.line(a, b, colr(h))
        if k + 1 < len(levels) and levels[k + 1] != H1 + 10:
            h2 = levels[k + 1]
            if h < H1 or h >= 40:
                for (sa, sb) in (((-1, -1), (1, -1)), ((1, -1), (1, 1)), ((1, 1), (-1, 1)), ((-1, 1), (-1, -1))):
                    S.line(corner(h, *sa), corner(h2, *sb), colr(h))
                    S.line(corner(h, *sb), corner(h2, *sa), colr(h))
    # main deck (floor reaches the glass ring so everything is face-connected)
    S.fill(cx - 8, G + H1, cz - 8, cx + 8, G + H1, cz + 8, WHITE)
    S.fill(cx - 8, G + H1 - 1, cz - 8, cx + 8, G + H1 - 1, cz + 8, RED)
    S.shell(cx - 8, G + H1 + 1, cz - 8, cx + 8, G + H1 + 2, cz + 8, B.GL['light_blue'], top=False, bottom=False)
    for sx in (-8, 8):
        for sz in (-8, 8):
            S.fill(cx + sx, G + H1 + 1, cz + sz, cx + sx, G + H1 + 2, cz + sz, RED)
    for t in range(-8, 9, 4):
        for (px, pz) in ((cx + t, cz - 8), (cx + t, cz + 8), (cx - 8, cz + t), (cx + 8, cz + t)):
            S.fill(px, G + H1 + 1, pz, px, G + H1 + 2, pz, WHITE)
    S.fill(cx - 8, G + H1 + 3, cz - 8, cx + 8, G + H1 + 3, cz + 8, RED)
    S.shell(cx - 8, G + H1 + 4, cz - 8, cx + 8, G + H1 + 4, cz + 8, B.IRON_BARS, top=False, bottom=False)
    # top deck
    S.fill(cx - 4, G + H2, cz - 4, cx + 4, G + H2, cz + 4, WHITE)
    S.shell(cx - 4, G + H2 + 1, cz - 4, cx + 4, G + H2 + 2, cz + 4, B.GL['light_blue'], top=False, bottom=False)
    S.fill(cx - 4, G + H2 + 3, cz - 4, cx + 4, G + H2 + 3, cz + 4, RED)
    # antenna
    for k in range(7):
        S.fill(cx - 1, G + H2 + 4 + k, cz - 1, cx + 1, G + H2 + 4 + k, cz + 1, RED if (k // 2) % 2 == 0 else WHITE)
    for k in range(12):
        S.set(cx, G + H2 + 11 + k, cz, RED if (k // 2) % 2 == 0 else WHITE)
    S.set(cx, G + H2 + 23, cz, B.LIGHTNING_ROD)
    # foot-town building under the legs
    st = dict(THEMES['quartz'])
    st.update(glass=B.GL['light_blue'], period=4, win_rows={1, 2}, shop=False)
    bldg.shell_building(S, cx - 5, cz - 5, cx + 5, cz + 5, 2, st, rng, fh=3, g0=4, front='s', parapet=True, core=False)
    return S


# =========================================================================================================
def ferris_wheel(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    cx = (x0 + x1) // 2
    cz = (z0 + z1) // 2 - 2
    _plaza(world, rect, B.STONE_BRICKS)
    # little garden bands on the sides
    populate.park(world, T, (x0, z0, x0 + 3, z1), rng, density=0.1, paths=False)
    populate.park(world, T, (x1 - 3, z0, x1, z1), rng, density=0.1, paths=False)
    S = world.new('landmark', 'Ferris Wheel', value=1500)
    R = 16
    hub_y = G + 19
    for zr in (cz, cz + 4):
        for (px, py) in circle_pts(cx, hub_y, R):
            S.set(int(px), int(py), zr, WHITE)
        for (px, py) in circle_pts(cx, hub_y, R - 4, 70):
            S.set(int(px), int(py), zr, B.C['light_gray'])
    # spokes
    for k in range(16):
        a = k * np.pi / 8
        tx, ty = cx + (R - 0.5) * np.cos(a), hub_y + (R - 0.5) * np.sin(a)
        for zr in (cz, cz + 4):
            S.line((cx, hub_y, zr), (int(round(tx)), int(round(ty)), zr), B.IRON_BLOCK if k % 2 else B.C['light_gray'])
    # hub axle
    S.fill(cx - 1, hub_y - 1, cz, cx + 1, hub_y + 1, cz + 4, B.C['red'])
    # cabins
    cols = ['red', 'yellow', 'light_blue', 'lime', 'orange', 'magenta', 'cyan', 'white']
    for k in range(24):
        a = k * 2 * np.pi / 24
        px = int(round(cx + R * np.cos(a)))
        py = int(round(hub_y + R * np.sin(a)))
        c = B.C[cols[k % len(cols)]]
        S.fill(px - 1, py - 2, cz + 1, px, py - 1, cz + 3, c)
        S.fill(px - 1, py, cz + 1, px, py, cz + 3, B.GL['light_blue'] if False else c)
        S.set(px - 1, py - 1, cz + 2, B.GL['white'])
    # A-frame supports
    for zr in (cz, cz + 4):
        for sx in (-1, 1):
            S.line((cx, hub_y - 1, zr), (cx + sx * 13, G + 1, zr), B.C['light_gray'])
            S.line((cx + sx, hub_y - 1, zr), (cx + sx * 13 + sx, G + 1, zr), B.C['light_gray'])
            S.fill(cx + sx * 13 - 1, G + 1, zr, cx + sx * 13 + 1, G + 2, zr, B.C['gray'])
        S.fill(cx - 13, G + 5, zr, cx + 13, G + 5, zr, B.IRON_BARS)   # cross tie
    # boarding platform and ticket booth
    S.fill(cx - 3, G + 1, cz - 3, cx + 3, G + 1, cz - 1, B.C['gray'])
    S.shell(cx + 6, G + 1, cz - 5, cx + 10, G + 4, cz - 2, B.C['white'], top=True, bottom=False)
    S.fill(cx + 7, G + 2, cz - 2, cx + 9, G + 3, cz - 2, B.GL['light_blue'])
    return S


# =========================================================================================================
def diet_building(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    cx = (x0 + x1) // 2
    _plaza(world, rect, B.STONE_BRICKS)
    # lawns front and sides
    populate.park(world, T, (x0, z1 - 10, x1, z1), rng, density=0.04, paths=False)
    # main axis path
    world.raw.fill(cx - 2, G, z1 - 10, cx + 1, G, z1, B.POL_DIORITE)
    S = world.new('landmark', 'National Diet Building', value=3000)
    th = dict(wall=B.POL_DIORITE, pil=B.QUARTZ, span=B.POL_ANDESITE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS)
    st = dict(th)
    st.update(glass=B.GL['gray'], period=3, win_rows={1, 2}, shop=False, parapet=B.QUARTZ)
    zb0, zb1 = z0 + 1, z0 + 25      # building depth
    xl0, xl1 = cx - 17, cx - 6      # left wing
    xr0, xr1 = cx + 5, cx + 16      # right wing  (cx-5..cx+4 centre)
    # wings (4 storeys) with an open courtyard
    for (a, b) in ((xl0, xl1), (xr0, xr1)):
        roof, slabs = bldg.shell_building(S, a, zb0, b, zb1, 4, st, rng, fh=3, g0=4, front='s', door=False,
                                          core=False, parapet=True, col_spacing=5)
        # courtyard
        S.fill(a + 3, G + 1, zb0 + 8, b - 3, roof + 1, zb1 - 8, AIR)
        S.fill(a + 3, G, zb0 + 8, b - 3, G, zb1 - 8, B.GRAVEL)
        S.shell(a + 2, G + 1, zb0 + 7, b - 2, roof, zb1 - 7, B.POL_DIORITE, top=False, bottom=False)
        for y in range(G + 2, roof, 3):
            S.fill(a + 2, y, zb0 + 8, b - 2, y + 1, zb0 + 8, B.GL['gray']) if False else None
    # centre block
    xc0, xc1 = cx - 5, cx + 4
    roof, slabs = bldg.shell_building(S, xc0, zb0, xc1, zb1, 5, st, rng, fh=3, g0=4, front='s', core=True,
                                      parapet=True, door_w=4)
    # front colonnade: columns across the centre front
    for xx in range(xc0 + 1, xc1, 2):
        S.fill(xx, G + 1, zb1 + 1, xx, G + 4, zb1 + 1, B.QUARTZ_PILLAR)
    S.fill(xc0, G + 5, zb1 + 1, xc1, G + 5, zb1 + 1, B.QUARTZ)
    # steps
    stq = B.ST('quartz')
    S.fill(xc0 - 1, G + 1, zb1 + 2, xc1 + 1, G + 1, zb1 + 2, stq[('north', 'bottom')])
    # tower on the centre block
    tx0, tx1 = cx - 3, cx + 2
    tz0, tz1 = zb0 + 8, zb0 + 15
    troof, _ = bldg.shell_building(S, tx0, tz0, tx1, tz1, 3, st, rng, fh=3, g0=3, base_y=roof, front='s', door=False,
                                   core=False, parapet=True, foundation=False, interior=True, col_spacing=6)
    # stepped pyramid roof
    top = hip_roof(S, tx0 - 1, tz0 - 1, tx1 + 1, tz1 + 1, troof + 1, 'stone_brick', top_blk=B.STONE_BRICKS)
    S.fill(cx, top + 1, tz0 + 3, cx, top + 5, tz0 + 3, B.CHAIN_Y)
    S.set(cx, top + 6, tz0 + 3, B.LIGHTNING_ROD)
    # wing roofs: low hip roofs of dark tile
    return S


# =========================================================================================================
def tokyo_station(world, T, rect, rng):
    x0, z0, x1, z1 = rect
    _plaza(world, rect, B.STONE_BRICKS)
    bx0, bx1 = x0 + 4, x1 - 4
    bz0, bz1 = z0 + 3, z0 + 17
    # forecourt gardens + trees
    populate.park(world, T, (x0, bz1 + 10, x1, z1), rng, density=0.03, kinds=['cherry', 'ginkgo', 'zelkova'], paths=False)
    world.raw.fill(x0 + 10, G, bz1 + 3, x1 - 10, G, bz1 + 9, B.SMOOTH_STONE)
    S = world.new('landmark', 'Tokyo Station', value=3500)
    st = dict(wall=B.BRICKS, pil=B.QUARTZ, span=B.CALCITE, floor=B.SMOOTH_STONE, core=B.STONE_BRICKS)
    st.update(glass=B.GLASS, period=4, win_rows={1, 2, 3}, shop=False, parapet=B.CALCITE)
    # long body, 3 storeys of 4
    roof, slabs = bldg.shell_building(S, bx0, bz0, bx1, bz1, 3, st, rng, fh=4, g0=4, front='s', core=False,
                                      parapet=False, col_spacing=6, door_w=5)
    hip_roof(S, bx0 - 1, bz0 - 1, bx1 + 1, bz1 + 1, roof + 1, 'deepslate_tile', top_blk=B.DEEPSLATE_TILES, max_layers=3,
             cap=False)
    # end pavilions with domes
    for (a, b) in ((bx0, bx0 + 14), (bx1 - 14, bx1)):
        proof, _ = bldg.shell_building(S, a, bz0 - 1, b, bz1 + 1, 1, st, rng, fh=4, g0=4, base_y=roof, front='s',
                                       door=False, core=False, parapet=False, foundation=False, col_spacing=7)
        # raise the pavilion walls of the lower storeys to meet the roof
        pcx, pcz = (a + b) // 2, (bz0 + bz1) // 2
        # octagonal drum
        S.cyl(pcx, pcz, 6.5, proof + 1, proof + 3, B.BRICKS, hollow=True)
        S.disk(pcx, pcz, 6.5, proof + 4, B.WEATH_CUT_COPPER)
        for dy in range(0, 6):
            r = np.sqrt(max(0.0, 6.5 ** 2 - (dy * 1.15) ** 2))
            if r < 1:
                break
            S.disk(pcx, pcz, r, proof + 5 + dy, B.WEATH_CUT_COPPER, hollow=(r > 1.5))
        S.fill(pcx, proof + 11, pcz, pcx, proof + 12, pcz, B.CHAIN_Y)
        S.set(pcx, proof + 13, pcz, B.LIGHTNING_ROD)
    # central pavilion with gable
    cx0, cx1 = (bx0 + bx1) // 2 - 7, (bx0 + bx1) // 2 + 7
    proof, _ = bldg.shell_building(S, cx0, bz0 - 1, cx1, bz1 + 1, 1, st, rng, fh=4, g0=4, base_y=roof, front='s',
                                   door=False, core=False, parapet=False, foundation=False, col_spacing=7)
    bldg.gable_roof(S, cx0, bz0 - 1, cx1, bz1 + 1, proof, B.BRICKS, 'deepslate_tile', rng, ridge_axis='x', overhang=1)
    # arcade along the front
    for xx in range(bx0 + 1, bx1, 3):
        S.fill(xx, G + 1, bz1 + 1, xx, G + 4, bz1 + 1, B.QUARTZ_PILLAR)
    S.fill(bx0, G + 5, bz1 + 1, bx1, G + 5, bz1 + 1, B.CALCITE)
    return S


# =========================================================================================================
def _pagoda(S, cx, cz, base_y):
    """Five-tier pagoda; returns top y."""
    y = base_y
    sizes = [7, 7, 5, 5, 3]
    for k in range(5):
        s = sizes[k]
        h = s // 2
        # body: red pillars at corners, white plaster, a window
        body_h = 3
        S.fill(cx - h, y + 1, cz - h, cx + h, y + body_h, cz + h, B.C['white'])
        S.fill(cx - h + 1, y + 1, cz - h + 1, cx + h - 1, y + body_h, cz + h - 1, AIR)
        for sx in (-h, h):
            for sz in (-h, h):
                S.fill(cx + sx, y + 1, cz + sz, cx + sx, y + body_h, cz + sz, B.C['red'])
        S.fill(cx - h + 1, y + 2, cz - h, cx + h - 1, y + 2, cz - h, B.GL['light_blue'])
        S.fill(cx - h + 1, y + 2, cz + h, cx + h - 1, y + 2, cz + h, B.GL['light_blue'])
        S.fill(cx - h, y + 2, cz - h + 1, cx - h, y + 2, cz + h - 1, B.GL['light_blue'])
        S.fill(cx + h, y + 2, cz - h + 1, cx + h, y + 2, cz + h - 1, B.GL['light_blue'])
        S.fill(cx - h, y + body_h + 1, cz - h, cx + h, y + body_h + 1, cz + h, B.C['red'])   # beam
        top = hip_roof(S, cx - h - 2, cz - h - 2, cx + h + 2, cz + h + 2, y + body_h + 2, 'oxidized_cut_copper',
                       top_blk=B.OX_CUT_COPPER, max_layers=2, soffit=B.C['red'])
        y = y + body_h + 2
    S.fill(cx, y + 1, cz, cx, y + 9, cz, B.CHAIN_Y)
    for k in range(3, 9, 2):
        S.set(cx, y + k, cz, B.b('gold_block'))
    S.set(cx, y + 10, cz, B.LIGHTNING_ROD)
    return y + 10


def _gate(world, x0, z0, x1, z1, name, big_lantern=True, tall=1):
    S = world.new('landmark', name, value=2200)
    w = x1 - x0 + 1
    d = z1 - z0 + 1
    cx = (x0 + x1) // 2
    cz = (z0 + z1) // 2
    # stone base
    S.fill(x0, G + 1, z0, x1, G + 1, z1, B.STONE_BRICKS)
    h1 = 6 * tall
    # pillars: 3 pairs
    px = [x0, x0 + 2, cx - 3, cx + 3, x1 - 2, x1]
    for xx in px:
        for zz in (z0, z1 - 1):
            S.fill(xx, G + 2, zz, xx + (0 if xx in (x0,) else 0), G + h1, zz + 1, B.C['red'])
    # lower walls on both sides of the passage
    S.fill(x0, G + 2, z0, cx - 4, G + h1, z1, B.C['red'])
    S.fill(cx + 4, G + 2, z0, x1, G + h1, z1, B.C['red'])
    S.fill(x0 + 1, G + 2, z0 + 1, cx - 5, G + h1 - 1, z1 - 1, AIR)
    S.fill(cx + 5, G + 2, z0 + 1, x1 - 1, G + h1 - 1, z1 - 1, AIR)
    # lattice windows to read as the Nio guardian niches
    S.fill(x0 + 1, G + 3, z1, cx - 5, G + 4, z1, B.IRON_BARS)
    S.fill(cx + 5, G + 3, z1, x1 - 1, G + 4, z1, B.IRON_BARS)
    # floor beam over the passage
    S.fill(x0, G + h1, z0, x1, G + h1 + 1, z1, B.C['red'])
    # upper storey (hall) with white walls and red frame
    S.fill(x0 + 2, G + h1 + 2, z0 + 1, x1 - 2, G + h1 + 5, z1 - 1, B.C['white'])
    S.fill(x0 + 3, G + h1 + 2, z0 + 2, x1 - 3, G + h1 + 4, z1 - 2, AIR)
    S.fill(x0 + 3, G + h1 + 3, z0 + 1, x1 - 3, G + h1 + 4, z0 + 1, B.GL['light_blue'])
    S.fill(x0 + 3, G + h1 + 3, z1 - 1, x1 - 3, G + h1 + 4, z1 - 1, B.GL['light_blue'])
    for xx in range(x0 + 2, x1 - 1, 4):
        S.fill(xx, G + h1 + 2, z0 + 1, xx, G + h1 + 5, z0 + 1, B.C['red'])
        S.fill(xx, G + h1 + 2, z1 - 1, xx, G + h1 + 5, z1 - 1, B.C['red'])
    S.fill(x0 + 1, G + h1 + 6, z0, x1 - 1, G + h1 + 6, z1, B.C['red'])
    # great roof (tile grey, big overhang)
    hip_roof(S, x0 - 2, z0 - 2, x1 + 2, z1 + 2, G + h1 + 7, 'deepslate_tile', top_blk=B.DEEPSLATE_TILES, max_layers=5,
             soffit=B.C['red'])
    # up-turned eave corners
    for (ex, ez) in ((x0 - 3, z0 - 3), (x1 + 3, z0 - 3), (x0 - 3, z1 + 3), (x1 + 3, z1 + 3)):
        S.set(ex, G + h1 + 8, ez, B.SL('deepslate_tile')['bottom'])
    if big_lantern:
        # the great red lantern hanging in the passage
        S.fill(cx - 1, G + h1 - 5, cz - 1, cx + 1, G + h1 - 1, cz + 1, B.C['red'])
        S.fill(cx - 1, G + h1 - 6, cz, cx + 1, G + h1 - 6, cz, B.C['black'])
        S.fill(cx, G + h1 - 5, cz - 1, cx, G + h1 - 4, cz - 1, B.C['white'])
        S.fill(cx, G + h1, cz, cx, G + h1, cz, B.C['black'])
        S.fill(cx, G + h1 - 1, cz, cx, G + h1 - 1, cz, B.CHAIN_Y)
    # the passage is open: clear the middle
    S.fill(cx - 3, G + 2, z0, cx + 3, G + h1 - 1, z1, AIR)
    if big_lantern:
        S.fill(cx - 1, G + h1 - 5, cz - 1, cx + 1, G + h1 - 2, cz + 1, B.C['red'])
        S.fill(cx - 1, G + h1 - 6, cz - 1, cx + 1, G + h1 - 6, cz + 1, B.C['black'])
        S.fill(cx, G + h1 - 1, cz, cx, G + h1 - 1, cz, B.CHAIN_Y)
    return S


def sensoji(world, T, rect, rng):
    x0, z0, x1, z1 = rect       # 80..157, 80..161
    cx = 120
    # paving and gardens
    _plaza(world, rect, B.COARSE)
    world.raw.fill(x0, G, z0, x1, G, z1, B.GRASS)
    # main axis (Nakamise) and courtyards
    world.raw.fill(cx - 4, G, z0 + 4, cx + 4, G, z1, B.STONE_BRICKS)
    world.raw.fill(cx - 22, G, z0 + 4, cx + 22, G, z0 + 30, B.STONE_BRICKS)   # main hall courtyard
    world.raw.fill(cx - 10, G, 118, cx + 10, G, 126, B.STONE_BRICKS)
    populate.park(world, T, (x0, z0, x1, z1), rng, density=0.012,
                  kinds=['cherry', 'cherry', 'ginkgo', 'maple', 'oak', 'spruce'], paths=False)
    # clear the built-up axis of trees
    world.raw.fill(cx - 22, G + 1, z0 + 4, cx + 22, G + 20, z0 + 30, AIR)
    world.raw.fill(cx - 5, G + 1, z0 + 4, cx + 5, G + 20, z1, AIR)
    world.raw.fill(cx - 12, G + 1, 116, cx + 12, G + 20, 128, AIR)
    # gates
    _gate(world, cx - 8, 150, cx + 8, 157, 'Kaminarimon Gate', True, 1)
    _gate(world, cx - 10, 119, cx + 10, 127, 'Hozomon Gate', False, 1)
    # Nakamise stalls
    for side in (-1, 1):
        for k in range(4):
            z = 131 + k * 4
            xa = cx + 5 if side > 0 else cx - 8
            S = world.new('shop', 'Nakamise stall')
            col = pick(rng, ['red', 'white', 'orange', 'yellow'])
            S.fill(xa, G + 1, z, xa + 3, G + 1, z + 2, B.STONE_BRICKS)
            S.shell(xa, G + 2, z, xa + 3, G + 4, z + 2, B.C['white'], top=False, bottom=False)
            front_x = xa if side > 0 else xa + 3
            S.fill(front_x, G + 2, z + 1, front_x, G + 3, z + 1, AIR)
            S.fill(xa, G + 5, z, xa + 3, G + 5, z + 2, B.C['red'])
            bldg.gable_roof(S, xa, z, xa + 3, z + 2, G + 5, B.C['white'], 'brick' if rng.random() < 0.5 else 'deepslate_tile',
                            rng, ridge_axis='z', overhang=0)
            S.fill(front_x, G + 4, z, front_x, G + 4, z + 2, CARPET_COL(col))
    # main hall (Hondo)
    hx0, hx1, hz0, hz1 = cx - 13, cx + 13, 86, 108
    S = world.new('landmark', 'Senso-ji Main Hall', value=3000)
    S.fill(hx0 - 2, G + 1, hz0 - 2, hx1 + 2, G + 2, hz1 + 2, B.STONE_BRICKS)        # raised platform
    S.fill(hx0 + 1, G + 3, hz0 + 1, hx1 - 1, G + 7, hz1 - 1, B.C['white'])
    S.fill(hx0 + 2, G + 3, hz0 + 2, hx1 - 2, G + 7, hz1 - 2, AIR)
    for xx in range(hx0, hx1 + 1, 4):
        for zz in (hz0, hz1):
            S.fill(xx, G + 3, zz, xx + 1, G + 8, zz + 1, B.C['red'])
    for zz in range(hz0 + 4, hz1 - 2, 4):
        for xx in (hx0, hx1 - 1):
            S.fill(xx, G + 3, zz, xx + 1, G + 8, zz + 1, B.C['red'])
    S.fill(cx - 5, G + 3, hz1 - 1, cx + 5, G + 5, hz1 - 1, B.C['gray'])
    S.fill(cx - 5, G + 3, hz1 - 1, cx + 5, G + 4, hz1 - 1, B.IRON_BARS)
    S.fill(cx - 2, G + 3, hz1 - 1, cx + 2, G + 4, hz1 - 1, AIR)
    # two-tier roof: a red soffit plate carried by the pillars, then the lower hip roof, then a clerestory and the upper roof
    hip_roof(S, hx0 - 3, hz0 - 3, hx1 + 4, hz1 + 4, G + 10, 'deepslate_tile', top_blk=B.DEEPSLATE_TILES, max_layers=4,
             soffit=B.C['red'])
    S.fill(hx0 + 3, G + 14, hz0 + 3, hx1 - 2, G + 15, hz1 - 2, B.C['white'])
    S.fill(hx0 + 4, G + 14, hz0 + 4, hx1 - 3, G + 15, hz1 - 3, AIR)
    S.fill(hx0 + 5, G + 15, hz0 + 3, hx1 - 4, G + 15, hz0 + 3, B.GL['light_blue'])
    S.fill(hx0 + 5, G + 15, hz1 - 2, hx1 - 4, G + 15, hz1 - 2, B.GL['light_blue'])
    top = hip_roof(S, hx0, hz0, hx1 + 1, hz1 + 1, G + 17, 'deepslate_tile', top_blk=B.DEEPSLATE_TILES, soffit=B.C['red'])
    S.set(cx, top + 1, (hz0 + hz1) // 2, B.b('gold_block'))
    # incense burner in the courtyard in front of the hall (part of the hall)
    S.fill(cx - 1, G + 1, 113, cx + 1, G + 2, 115, B.C['gray'])
    S.fill(cx, G + 3, 114, cx, G + 4, 114, B.IRON_BARS)
    # pagoda in the east of the precinct
    S = world.new('landmark', 'Senso-ji Pagoda', value=2800)
    px, pz = 146, 100
    S.fill(px - 6, G + 1, pz - 6, px + 6, G + 1, pz + 6, B.STONE_BRICKS)
    _pagoda(S, px, pz, G + 1)
    return None


def CARPET_COL(c):
    return bldg.CARPET[c]


# =========================================================================================================
def twin_towers(world, T, rect, rng):
    x0, z0, x1, z1 = rect       # 261..296, 175..207
    _plaza(world, rect, B.STONE_BRICKS)
    populate.park(world, T, (x0, z1 - 6, x1, z1), rng, density=0.05, paths=False)
    S = world.new('landmark', 'Metropolitan Government Twin Towers', value=4000)
    st = dict(wall=B.C['light_gray'], pil=B.IRON_BLOCK, span=B.C['gray'], floor=B.C['light_gray'], core=B.C['gray'])
    st.update(glass=B.GL['gray'], period=4, win_rows={1, 2}, shop=False, mull=B.POL_ANDESITE, mull_period=2,
              parapet=B.C['gray'])
    # podium
    px0, px1, pz0, pz1 = x0 + 1, x1 - 1, z0 + 12, z1 - 3
    roof, _ = bldg.shell_building(S, px0, pz0, px1, pz1, 3, st, rng, fh=3, g0=4, front='s', core=False, door_w=4)
    # two towers on the back of the podium
    th, bh = 11, 4
    tz0, tz1 = z0 + 1, z0 + 17
    towers = [(x0 + 2, x0 + 15), (x1 - 15, x1 - 2)]
    for (a, b) in towers:
        r1, _ = bldg.shell_building(S, a, tz0, b, tz1, th, st, rng, fh=3, g0=4, base_y=G, front='s', door=False,
                                    core=True, foundation=False, col_spacing=5)
        # upper: split into two prongs
        pw = 5
        for (pa, pb) in ((a, a + pw - 1), (b - pw + 1, b)):
            bldg.shell_building(S, pa, tz0, pb, tz1, bh, st, rng, fh=3, g0=3, base_y=r1, front='s', door=False,
                                core=False, foundation=False, col_spacing=6, parapet=True)
        # tie the prongs with a thin bridge on the lowest prong storey
        S.fill(a + pw, r1 + 1, tz0, b - pw, r1 + 1, tz1, B.C['gray'])
        S.fill(a + pw, r1, tz0, b - pw, r1, tz1, st['floor'])
    S.fill(x0 + 8, G + 4 + 3 * (th - 1) + 3 * bh + 2, tz0 + 8, x0 + 8, G + 4 + 3 * (th - 1) + 3 * bh + 12, tz0 + 8, B.CHAIN_Y)
    return S
