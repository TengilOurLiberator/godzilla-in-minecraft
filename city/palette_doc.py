"""Writes city/PALETTE.md: which block is used for what and why, plus a table generated from the finished volume."""
import numpy as np

import blocks as B
import texcolors

INTRO = """# Tokyo palette (Patrix 26.3 32x basic)

The city is drawn only with block states that exist in Minecraft 26.3 (every palette entry is validated against the
data-generator report `blocks.json` while the generator runs, and an unknown id or property aborts the build).
Colours below are the average colour of the Patrix texture, measured from the extracted pack in
`work/patrix` (the preview images use exactly these averages).

## What Patrix actually looks like (and what that means for the choices)

* **Concrete is not "white / light grey".** Patrix concrete is a muted, slightly blue-grey set:
  `white_concrete` is (196,203,202), `light_gray_concrete` is a mid grey (122,123,115), `gray_concrete` is a
  dark slate (55,58,62). So asphalt is `gray_concrete`, and the brighter "white" look of plaster and sidewalks comes
  from `quartz_block` / `smooth_quartz` (226,219,211), `calcite` (220,221,219), `polished_diorite` and
  `smooth_stone` (159,158,158), not from `white_concrete`.
* **Saturated colours are very saturated** (`red`, `orange`, `yellow`, `lime`, `cyan`, `magenta`, `pink`
  concrete), which is exactly what neon shop signs, container colours and Ferris-wheel cabins need, so those
  use concrete. Terracotta is dark and earthy and gives the beige / ochre / brick-brown facades.
* **Glass is clear with a faint tint; stained glass keeps its hue.** Offices and towers use `light_blue`,
  `cyan`, `blue`, `gray` stained glass and `tinted_glass` for the glass curtain walls, plain `glass` for houses.
* **`bricks` is a muted brown-red** (127,90,77), which is the right colour for Tokyo Station's brick; `cinnabar_bricks`
  (139,53,52) and `resin_bricks` (204,93,10) are the brighter accents.
* **`stone`, `grass_block` and several other natural blocks use OptiFine connected-texture tiles in Patrix**: the
  base texture file is a placeholder that says "ENABLE CONNECT TEXTURE", and the real look is a 4x4 / 6x6 repeating
  set under `optifine/ctm`. The generator therefore never relies on those base images for anything prominent:
  stone only appears as hidden underground fill and as the sea-bed floor (it has to be stone to match the flat world),
  and `grass_block` is used only for lawns. If the client has no CTM support lawns would show the placeholder; swap
  `GRASS` in `blocks.py` for `moss_block` (a proper green texture) in that case.
* Leaves are cut-out textures with a biome tint in-game; poplar leaves (`yellow_poplar`, `red_poplar`, `orange_poplar`,
  new in 26.x) carry their own colour and give the ginkgo / maple street trees.

## Palette by purpose
"""

ROLES = [
    ('Ground, sea and river', [
        ('stone', 'Underground fill and the sea floor at y<=28 (must match the flat world so the volume edge has no seam)'),
        ('sand', 'Beaches and shallow sea bed'), ('gravel', 'Deeper sea bed, river bed, park paths, rail ballast'),
        ('water', 'Sea, river and ponds (source blocks, level 0)'),
        ('stone_bricks', 'Quay walls, river banks, plazas, Tokyo Station forecourt'),
        ('grass_block', 'Lawns and parks'), ('polished_andesite', 'Default yard / courtyard paving between buildings'),
        ('coarse_dirt', 'Temple precinct ground')]),
    ('Roads', [
        ('gray_concrete', 'Asphalt (dark slate in Patrix, reads as tarmac)'),
        ('smooth_stone', 'Sidewalks and expressway kerbs: the lightest neutral that is still darker than the facades'),
        ('yellow_concrete', 'Centre lines'), ('white_concrete', 'Lane dashes and zebra crossings'),
        ('iron_bars', 'Street-lamp poles, railings, balcony rails, tank rails, antenna bases'),
        ('white_stained_glass', 'Street-lamp heads (no light emission; the city uses essentially no light blocks)'),
        ('rail', 'Elevated rail tracks')]),
    ('Facades and structure', [
        ('quartz_block', 'Pale plaster, pilasters, Diet Building columns'),
        ('smooth_quartz', 'Corner pillars and floors of light buildings'),
        ('calcite', 'Cornice bands, tower base, bridge towers'),
        ('white_terracotta', 'Beige houses and apartments'), ('terracotta', 'Terracotta-orange houses'),
        ('sandstone', 'Sand-coloured pilasters'), ('cut_sandstone', 'Sand-coloured walls'),
        ('mud_bricks', 'Warm brown walls'), ('bricks', 'Brick apartments, Tokyo Station, brick roofs'),
        ('tuff_bricks', 'Grey-green stone walls'), ('polished_diorite', 'Diet Building walls (pale granite)'),
        ('light_gray_concrete', 'Mid-grey concrete, floors, mullions, viaduct girders'),
        ('gray_concrete', 'Dark spandrel bands, cores, containers, port paving'),
        ('iron_block', 'Metal mullions, AC units, crane parts'),
        ('light_blue_stained_glass', 'Window glass (most common office / tower glass)'),
        ('cyan_stained_glass', 'Window glass'), ('gray_stained_glass', 'Dark window glass'),
        ('blue_stained_glass', 'Window glass'), ('tinted_glass', 'Dark curtain wall'), ('glass', 'House and shop-front glass')]),
    ('Roofs', [
        ('deepslate_tile_stairs', 'Dark kawara roofs (houses, temple, Tokyo Station)'),
        ('stone_brick_stairs', 'Grey roofs'), ('brick_stairs', 'Red-brown roofs'),
        ('oxidized_cut_copper_stairs', 'Green copper roofs: pagoda'),
        ('red_concrete', 'Temple pillars, Tokyo Tower, crane parts')]),
    ('Signs and colour', [
        ('magenta_concrete', 'Neon signs'), ('cyan_concrete', 'Neon signs'), ('pink_concrete', 'Neon signs'),
        ('lime_concrete', 'Neon signs'), ('orange_concrete', 'Neon signs, containers'),
        ('sea_lantern', 'A handful of lit sign tips on shop streets (the only light blocks)'),
        ('red_carpet', 'Shop awnings (all carpet colours)')]),
    ('Landmark specials', [
        ('red_concrete', 'Tokyo Tower red sections, torii/gate pillars'),
        ('white_concrete', 'Tokyo Tower white sections, Rainbow Bridge, Ferris-wheel rim'),
        ('gold_block', 'Pagoda finial'), ('lightning_rod', 'Antenna tips'), ('iron_chain', 'Cables, hangers, masts')]),
]


def write(world, path):
    vol = world.vol
    states = B.PAL.states
    counts = np.bincount(vol.ravel(), minlength=len(states))
    cols = texcolors.palette_colors(states)
    lines = [INTRO]
    for title, items in ROLES:
        lines.append('### ' + title + '\n')
        lines.append('| Block | Patrix colour | Used for |')
        lines.append('|---|---|---|')
        for name, why in items:
            full = 'minecraft:' + name
            idxs = [i for i, s in enumerate(states) if s.startswith(full + '[') or s == full]
            if not idxs:
                continue
            c = cols[idxs[0]]
            lines.append('| `%s` | (%d,%d,%d) | %s |' % (name, c[0], c[1], c[2], why))
        lines.append('')
    lines.append('## Every block state in the volume\n')
    lines.append('`tokyo.gzc` contains %d distinct states. Counts are cells in the stamped volume (air and water excluded).\n'
                 % int((counts[1:] > 0).sum()))
    lines.append('| State | Cells | Patrix colour |')
    lines.append('|---|---|---|')
    order = np.argsort(-counts)
    for i in order:
        if i == 0 or counts[i] == 0 or states[i].startswith('minecraft:water'):
            continue
        c = cols[i]
        lines.append('| `%s` | %d | (%d,%d,%d) |' % (states[i][10:], counts[i], c[0], c[1], c[2]))
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
