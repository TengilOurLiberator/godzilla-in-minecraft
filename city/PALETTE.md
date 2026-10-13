# Tokyo palette (Patrix 26.3 32x basic)

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

### Ground, sea and river

| Block | Patrix colour | Used for |
|---|---|---|
| `stone` | (120,120,119) | Underground fill and the sea floor at y<=28 (must match the flat world so the volume edge has no seam) |
| `sand` | (221,205,162) | Beaches and shallow sea bed |
| `gravel` | (123,116,110) | Deeper sea bed, river bed, park paths, rail ballast |
| `water` | (44,96,190) | Sea, river and ponds (source blocks, level 0) |
| `stone_bricks` | (121,119,117) | Quay walls, river banks, plazas, Tokyo Station forecourt |
| `grass_block` | (96,150,62) | Lawns and parks |
| `polished_andesite` | (123,126,124) | Default yard / courtyard paving between buildings |
| `coarse_dirt` | (108,88,70) | Temple precinct ground |

### Roads

| Block | Patrix colour | Used for |
|---|---|---|
| `gray_concrete` | (55,58,62) | Asphalt (dark slate in Patrix, reads as tarmac) |
| `smooth_stone` | (159,158,158) | Sidewalks and expressway kerbs: the lightest neutral that is still darker than the facades |
| `yellow_concrete` | (228,167,23) | Centre lines |
| `white_concrete` | (196,203,202) | Lane dashes and zebra crossings |
| `iron_bars` | (110,94,86) | Street-lamp poles, railings, balcony rails, tank rails, antenna bases |
| `white_stained_glass` | (254,254,254) | Street-lamp heads (no light emission; the city uses essentially no light blocks) |
| `rail` | (82,69,62) | Elevated rail tracks |

### Facades and structure

| Block | Patrix colour | Used for |
|---|---|---|
| `quartz_block` | (226,219,211) | Pale plaster, pilasters, Diet Building columns |
| `smooth_quartz` | (228,222,213) | Corner pillars and floors of light buildings |
| `calcite` | (220,221,219) | Cornice bands, tower base, bridge towers |
| `white_terracotta` | (181,153,137) | Beige houses and apartments |
| `terracotta` | (132,81,58) | Terracotta-orange houses |
| `sandstone` | (210,191,150) | Sand-coloured pilasters |
| `cut_sandstone` | (213,198,155) | Sand-coloured walls |
| `mud_bricks` | (140,105,82) | Warm brown walls |
| `bricks` | (127,90,77) | Brick apartments, Tokyo Station, brick roofs |
| `tuff_bricks` | (109,109,102) | Grey-green stone walls |
| `polished_diorite` | (204,204,203) | Diet Building walls (pale granite) |
| `light_gray_concrete` | (122,123,115) | Mid-grey concrete, floors, mullions, viaduct girders |
| `gray_concrete` | (55,58,62) | Dark spandrel bands, cores, containers, port paving |
| `iron_block` | (172,167,168) | Metal mullions, AC units, crane parts |
| `light_blue_stained_glass` | (102,152,215) | Window glass (most common office / tower glass) |
| `cyan_stained_glass` | (76,127,152) | Window glass |
| `gray_stained_glass` | (76,76,76) | Dark window glass |
| `blue_stained_glass` | (51,76,177) | Window glass |
| `tinted_glass` | (40,36,40) | Dark curtain wall |
| `glass` | (205,230,240) | House and shop-front glass |

### Roofs

| Block | Patrix colour | Used for |
|---|---|---|
| `deepslate_tile_stairs` | (54,54,55) | Dark kawara roofs (houses, temple, Tokyo Station) |
| `stone_brick_stairs` | (121,119,117) | Grey roofs |
| `brick_stairs` | (127,90,77) | Red-brown roofs |
| `oxidized_cut_copper_stairs` | (98,164,139) | Green copper roofs: pagoda |
| `red_concrete` | (134,34,33) | Temple pillars, Tokyo Tower, crane parts |

### Signs and colour

| Block | Patrix colour | Used for |
|---|---|---|
| `magenta_concrete` | (160,48,150) | Neon signs |
| `cyan_concrete` | (24,114,128) | Neon signs |
| `pink_concrete` | (202,97,135) | Neon signs |
| `lime_concrete` | (89,161,26) | Neon signs |
| `orange_concrete` | (212,93,8) | Neon signs, containers |
| `sea_lantern` | (152,205,198) | A handful of lit sign tips on shop streets (the only light blocks) |
| `red_carpet` | (147,44,41) | Shop awnings (all carpet colours) |

### Landmark specials

| Block | Patrix colour | Used for |
|---|---|---|
| `red_concrete` | (134,34,33) | Tokyo Tower red sections, torii/gate pillars |
| `white_concrete` | (196,203,202) | Tokyo Tower white sections, Rainbow Bridge, Ferris-wheel rim |
| `gold_block` | (203,157,61) | Pagoda finial |
| `lightning_rod` | (204,120,91) | Antenna tips |
| `iron_chain` | (95,83,79) | Cables, hangers, masts |

## Every block state in the volume

`tokyo.gzc` contains 146 distinct states. Counts are cells in the stamped volume (air and water excluded).

| State | Cells | Patrix colour |
|---|---|---|
| `stone` | 6870249 | (120,120,119) |
| `light_gray_concrete` | 130032 | (122,123,115) |
| `sand` | 126423 | (221,205,162) |
| `smooth_stone` | 78645 | (159,158,158) |
| `gray_concrete` | 73001 | (55,58,62) |
| `gravel` | 53055 | (123,116,110) |
| `polished_andesite` | 40741 | (123,126,124) |
| `stone_bricks` | 33684 | (121,119,117) |
| `oak_leaves[distance=1,persistent=true]` | 20852 | (84,146,52) |
| `white_concrete` | 17384 | (196,203,202) |
| `grass_block[snowy=false]` | 16932 | (96,150,62) |
| `cherry_leaves[distance=1,persistent=true]` | 14637 | (216,151,187) |
| `glass` | 13225 | (205,230,240) |
| `light_blue_stained_glass` | 12507 | (102,152,215) |
| `gray_stained_glass` | 10118 | (76,76,76) |
| `black_concrete` | 10046 | (14,16,18) |
| `cyan_stained_glass` | 9138 | (76,127,152) |
| `red_concrete` | 8840 | (134,34,33) |
| `smooth_quartz` | 8654 | (228,222,213) |
| `light_blue_concrete` | 8180 | (36,131,188) |
| `yellow_poplar_leaves[distance=1,persistent=true]` | 7202 | (200,131,28) |
| `tinted_glass` | 6970 | (40,36,40) |
| `iron_block` | 6599 | (172,167,168) |
| `blue_stained_glass` | 6076 | (51,76,177) |
| `quartz_block` | 5984 | (226,219,211) |
| `sandstone` | 5526 | (210,191,150) |
| `smooth_sandstone` | 4976 | (226,211,166) |
| `cut_sandstone` | 4812 | (213,198,155) |
| `yellow_concrete` | 4472 | (228,167,23) |
| `deepslate_tiles` | 4307 | (54,54,55) |
| `spruce_leaves[distance=1,persistent=true]` | 3266 | (70,110,60) |
| `orange_terracotta` | 3219 | (140,72,34) |
| `birch_leaves[distance=1,persistent=true]` | 2981 | (110,160,70) |
| `polished_diorite` | 2949 | (204,204,203) |
| `bricks` | 2775 | (127,90,77) |
| `cyan_concrete` | 2607 | (24,114,128) |
| `green_concrete` | 2434 | (70,87,36) |
| `calcite` | 2195 | (220,221,219) |
| `red_poplar_leaves[distance=1,persistent=true]` | 1885 | (139,44,26) |
| `mud_bricks` | 1862 | (140,105,82) |
| `iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]` | 1830 | (110,94,86) |
| `diorite` | 1795 | (198,197,196) |
| `white_terracotta` | 1703 | (181,153,137) |
| `polished_tuff` | 1621 | (115,114,106) |
| `smooth_stone_slab[type=bottom,waterlogged=false]` | 1404 | (159,158,158) |
| `orange_concrete` | 1275 | (212,93,8) |
| `quartz_bricks` | 1166 | (227,220,212) |
| `tuff_bricks` | 1065 | (109,109,102) |
| `packed_mud` | 1039 | (137,105,82) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 1012 | (54,54,55) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 1012 | (54,54,55) |
| `iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]` | 1001 | (110,94,86) |
| `iron_chain[axis=y]` | 997 | (95,83,79) |
| `blue_concrete` | 910 | (44,47,135) |
| `light_gray_stained_glass` | 849 | (152,152,152) |
| `oxidized_cut_copper` | 772 | (98,164,139) |
| `brown_concrete` | 736 | (91,59,32) |
| `oak_planks` | 715 | (141,117,82) |
| `deepslate_tile_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 681 | (54,54,55) |
| `deepslate_tile_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 681 | (54,54,55) |
| `dark_oak_log[axis=y]` | 658 | (57,46,30) |
| `rail[shape=east_west,waterlogged=false]` | 654 | (82,69,62) |
| `terracotta` | 647 | (132,81,58) |
| `stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 620 | (121,119,117) |
| `stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 620 | (121,119,117) |
| `polished_deepslate` | 608 | (84,85,85) |
| `yellow_terracotta` | 556 | (161,114,31) |
| `poplar_log[axis=y]` | 544 | (79,63,46) |
| `cherry_log[axis=y]` | 470 | (53,38,44) |
| `purple_concrete` | 456 | (95,33,147) |
| `oak_log[axis=y]` | 445 | (106,85,55) |
| `iron_bars[east=true,north=false,south=true,waterlogged=false,west=false]` | 356 | (110,94,86) |
| `iron_bars[east=true,north=true,south=false,waterlogged=false,west=false]` | 353 | (110,94,86) |
| `iron_bars[east=false,north=true,south=false,waterlogged=false,west=true]` | 348 | (110,94,86) |
| `iron_bars[east=false,north=false,south=true,waterlogged=false,west=true]` | 348 | (110,94,86) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 346 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 346 | (98,164,139) |
| `cut_copper` | 328 | (199,115,89) |
| `white_stained_glass` | 313 | (254,254,254) |
| `spruce_planks` | 297 | (102,89,71) |
| `polished_deepslate_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 270 | (84,85,85) |
| `polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 270 | (84,85,85) |
| `mud_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 194 | (140,105,82) |
| `mud_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 194 | (140,105,82) |
| `oxidized_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 163 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 163 | (98,164,139) |
| `stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 147 | (121,119,117) |
| `stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 147 | (121,119,117) |
| `brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 146 | (127,90,77) |
| `brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 146 | (127,90,77) |
| `polished_deepslate_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 140 | (84,85,85) |
| `polished_deepslate_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 140 | (84,85,85) |
| `magenta_concrete` | 137 | (160,48,150) |
| `pink_concrete` | 134 | (202,97,135) |
| `cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 132 | (199,115,89) |
| `cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 132 | (199,115,89) |
| `iron_bars[east=false,north=false,south=false,waterlogged=false,west=true]` | 126 | (110,94,86) |
| `lime_concrete` | 121 | (89,161,26) |
| `quartz_pillar[axis=y]` | 116 | (240,232,222) |
| `iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]` | 114 | (110,94,86) |
| `iron_chain[axis=z]` | 114 | (95,83,79) |
| `red_carpet` | 113 | (147,44,41) |
| `iron_bars[east=true,north=false,south=false,waterlogged=false,west=false]` | 112 | (110,94,86) |
| `tuff_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 91 | (109,109,102) |
| `tuff_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 91 | (109,109,102) |
| `spruce_log[axis=y]` | 91 | (51,39,28) |
| `blue_carpet` | 87 | (57,60,153) |
| `cyan_carpet` | 83 | (31,125,130) |
| `mud_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 80 | (140,105,82) |
| `mud_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 80 | (140,105,82) |
| `iron_chain[axis=x]` | 78 | (95,83,79) |
| `cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 76 | (199,115,89) |
| `cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 76 | (199,115,89) |
| `brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 68 | (127,90,77) |
| `brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 68 | (127,90,77) |
| `lightning_rod[facing=up,powered=false,waterlogged=false]` | 63 | (204,120,91) |
| `iron_bars[east=false,north=true,south=false,waterlogged=false,west=false]` | 61 | (110,94,86) |
| `iron_bars[east=true,north=false,south=true,waterlogged=false,west=true]` | 59 | (110,94,86) |
| `orange_carpet` | 54 | (209,118,42) |
| `iron_bars[east=true,north=true,south=false,waterlogged=false,west=true]` | 53 | (110,94,86) |
| `iron_bars[east=false,north=false,south=true,waterlogged=false,west=false]` | 52 | (110,94,86) |
| `white_carpet` | 46 | (227,227,226) |
| `magenta_carpet` | 39 | (176,76,165) |
| `copper_grate` | 35 | (183,107,79) |
| `green_carpet` | 35 | (84,107,29) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 27 | (54,54,55) |
| `yellow_carpet` | 26 | (210,181,53) |
| `quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 12 | (226,219,211) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 10 | (98,164,139) |
| `deepslate_tile_slab[type=bottom,waterlogged=false]` | 8 | (54,54,55) |
| `iron_bars[east=false,north=true,south=true,waterlogged=false,west=true]` | 5 | (110,94,86) |
| `gold_block` | 4 | (203,157,61) |
| `sea_lantern` | 4 | (152,205,198) |
| `stone_brick_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 3 | (121,119,117) |
| `iron_bars[east=true,north=true,south=true,waterlogged=false,west=false]` | 2 | (110,94,86) |
| `iron_bars[east=true,north=true,south=true,waterlogged=false,west=true]` | 1 | (110,94,86) |
