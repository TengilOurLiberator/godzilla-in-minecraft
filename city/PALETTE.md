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

`tokyo.gzc` contains 149 distinct states. Counts are cells in the stamped volume (air and water excluded).

| State | Cells | Patrix colour |
|---|---|---|
| `stone` | 6870249 | (120,120,119) |
| `sand` | 126423 | (221,205,162) |
| `light_gray_concrete` | 125175 | (122,123,115) |
| `smooth_stone` | 76408 | (159,158,158) |
| `gray_concrete` | 73557 | (55,58,62) |
| `gravel` | 52497 | (123,116,110) |
| `polished_andesite` | 40221 | (123,126,124) |
| `stone_bricks` | 33076 | (121,119,117) |
| `smooth_quartz` | 20630 | (228,222,213) |
| `white_concrete` | 17756 | (196,203,202) |
| `oak_leaves[distance=1,persistent=true]` | 17531 | (84,146,52) |
| `grass_block[snowy=false]` | 16866 | (96,150,62) |
| `cherry_leaves[distance=1,persistent=true]` | 12447 | (216,151,187) |
| `black_concrete` | 12240 | (14,16,18) |
| `light_blue_stained_glass` | 12000 | (102,152,215) |
| `glass` | 11868 | (205,230,240) |
| `light_blue_concrete` | 11138 | (36,131,188) |
| `gray_stained_glass` | 11079 | (76,76,76) |
| `cyan_stained_glass` | 9741 | (76,127,152) |
| `red_concrete` | 7477 | (134,34,33) |
| `tinted_glass` | 7464 | (40,36,40) |
| `iron_block` | 6820 | (172,167,168) |
| `yellow_poplar_leaves[distance=1,persistent=true]` | 5739 | (200,131,28) |
| `quartz_block` | 5696 | (226,219,211) |
| `sandstone` | 5637 | (210,191,150) |
| `blue_stained_glass` | 5520 | (51,76,177) |
| `smooth_sandstone` | 4722 | (226,211,166) |
| `yellow_concrete` | 4520 | (228,167,23) |
| `polished_diorite` | 4225 | (204,204,203) |
| `cut_sandstone` | 4000 | (213,198,155) |
| `deepslate_tiles` | 3453 | (54,54,55) |
| `calcite` | 3418 | (220,221,219) |
| `birch_leaves[distance=1,persistent=true]` | 2976 | (110,160,70) |
| `diorite` | 2841 | (198,197,196) |
| `spruce_leaves[distance=1,persistent=true]` | 2816 | (70,110,60) |
| `quartz_bricks` | 2614 | (227,220,212) |
| `bricks` | 2600 | (127,90,77) |
| `orange_terracotta` | 2510 | (140,72,34) |
| `green_concrete` | 2196 | (70,87,36) |
| `red_poplar_leaves[distance=1,persistent=true]` | 2037 | (139,44,26) |
| `iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]` | 1926 | (110,94,86) |
| `white_terracotta` | 1775 | (181,153,137) |
| `polished_tuff` | 1519 | (115,114,106) |
| `blue_concrete` | 1414 | (44,47,135) |
| `mud_bricks` | 1351 | (140,105,82) |
| `cyan_concrete` | 1217 | (24,114,128) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 1152 | (54,54,55) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 1152 | (54,54,55) |
| `orange_concrete` | 1124 | (212,93,8) |
| `smooth_stone_slab[type=bottom,waterlogged=false]` | 1116 | (159,158,158) |
| `light_gray_stained_glass` | 1090 | (152,152,152) |
| `iron_chain[axis=y]` | 1040 | (95,83,79) |
| `tuff_bricks` | 1033 | (109,109,102) |
| `iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]` | 937 | (110,94,86) |
| `oak_planks` | 843 | (141,117,82) |
| `weathered_cut_copper` | 770 | (128,142,115) |
| `packed_mud` | 682 | (137,105,82) |
| `brown_concrete` | 681 | (91,59,32) |
| `rail[shape=east_west,waterlogged=false]` | 654 | (82,69,62) |
| `oxidized_cut_copper` | 616 | (98,164,139) |
| `terracotta` | 593 | (132,81,58) |
| `dark_oak_log[axis=y]` | 566 | (57,46,30) |
| `purple_concrete` | 528 | (95,33,147) |
| `deepslate_tile_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 521 | (54,54,55) |
| `deepslate_tile_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 521 | (54,54,55) |
| `poplar_log[axis=y]` | 464 | (79,63,46) |
| `oak_log[axis=y]` | 449 | (106,85,55) |
| `cherry_log[axis=y]` | 405 | (53,38,44) |
| `polished_deepslate` | 386 | (84,85,85) |
| `cut_copper` | 378 | (199,115,89) |
| `stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 362 | (121,119,117) |
| `stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 362 | (121,119,117) |
| `white_stained_glass` | 316 | (254,254,254) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 309 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 309 | (98,164,139) |
| `spruce_planks` | 297 | (102,89,71) |
| `iron_bars[east=true,north=false,south=true,waterlogged=false,west=false]` | 283 | (110,94,86) |
| `tuff_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 281 | (109,109,102) |
| `tuff_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 281 | (109,109,102) |
| `iron_bars[east=true,north=true,south=false,waterlogged=false,west=false]` | 278 | (110,94,86) |
| `iron_bars[east=false,north=true,south=false,waterlogged=false,west=true]` | 273 | (110,94,86) |
| `iron_bars[east=false,north=false,south=true,waterlogged=false,west=true]` | 271 | (110,94,86) |
| `yellow_terracotta` | 198 | (161,114,31) |
| `stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 197 | (121,119,117) |
| `stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 197 | (121,119,117) |
| `mud_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 194 | (140,105,82) |
| `mud_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 194 | (140,105,82) |
| `cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 161 | (199,115,89) |
| `cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 161 | (199,115,89) |
| `lime_concrete` | 158 | (89,161,26) |
| `polished_deepslate_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 156 | (84,85,85) |
| `polished_deepslate_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 156 | (84,85,85) |
| `iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]` | 138 | (110,94,86) |
| `lightning_rod[facing=up,powered=false,waterlogged=false]` | 131 | (204,120,91) |
| `oxidized_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 119 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 119 | (98,164,139) |
| `quartz_pillar[axis=y]` | 116 | (240,232,222) |
| `iron_chain[axis=z]` | 114 | (95,83,79) |
| `brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 111 | (127,90,77) |
| `brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 111 | (127,90,77) |
| `polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 110 | (84,85,85) |
| `polished_deepslate_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 110 | (84,85,85) |
| `magenta_concrete` | 106 | (160,48,150) |
| `red_carpet` | 98 | (147,44,41) |
| `ladder[facing=north,waterlogged=false]` | 93 | (126,102,70) |
| `brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 91 | (127,90,77) |
| `mud_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 91 | (140,105,82) |
| `mud_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 91 | (140,105,82) |
| `brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]` | 91 | (127,90,77) |
| `cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 88 | (199,115,89) |
| `cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 88 | (199,115,89) |
| `iron_bars[east=false,north=false,south=false,waterlogged=false,west=true]` | 86 | (110,94,86) |
| `yellow_carpet` | 85 | (210,181,53) |
| `spruce_log[axis=y]` | 81 | (51,39,28) |
| `pink_concrete` | 81 | (202,97,135) |
| `cyan_carpet` | 81 | (31,125,130) |
| `iron_bars[east=false,north=true,south=false,waterlogged=false,west=false]` | 78 | (110,94,86) |
| `iron_chain[axis=x]` | 78 | (95,83,79) |
| `iron_bars[east=false,north=false,south=true,waterlogged=false,west=false]` | 78 | (110,94,86) |
| `orange_carpet` | 68 | (209,118,42) |
| `iron_bars[east=true,north=false,south=false,waterlogged=false,west=false]` | 66 | (110,94,86) |
| `iron_bars[east=true,north=false,south=true,waterlogged=false,west=true]` | 61 | (110,94,86) |
| `copper_grate` | 60 | (183,107,79) |
| `tuff_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]` | 55 | (109,109,102) |
| `tuff_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]` | 55 | (109,109,102) |
| `iron_bars[east=true,north=true,south=false,waterlogged=false,west=true]` | 53 | (110,94,86) |
| `magenta_carpet` | 52 | (176,76,165) |
| `white_carpet` | 34 | (227,227,226) |
| `blue_carpet` | 30 | (57,60,153) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 27 | (54,54,55) |
| `deepslate_tile_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 27 | (54,54,55) |
| `green_carpet` | 21 | (84,107,29) |
| `quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]` | 12 | (226,219,211) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 10 | (98,164,139) |
| `oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 10 | (98,164,139) |
| `deepslate_tile_slab[type=bottom,waterlogged=false]` | 8 | (54,54,55) |
| `sea_lantern` | 4 | (152,205,198) |
| `gold_block` | 4 | (203,157,61) |
| `stone_brick_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]` | 3 | (121,119,117) |
| `stone_brick_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]` | 3 | (121,119,117) |
| `iron_bars[east=false,north=true,south=true,waterlogged=false,west=true]` | 2 | (110,94,86) |
| `iron_bars[east=true,north=true,south=true,waterlogged=false,west=false]` | 2 | (110,94,86) |
