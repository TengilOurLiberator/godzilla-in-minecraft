"""Block palette for the Tokyo generator.

Every block state used by the city is registered here through `b()`, which validates the block id and every state
property against the vanilla data-generator report (blocks.json) and fails loudly on anything unknown.
Palette indices are assigned in registration order (index 0 is always minecraft:air) and compacted when written.
"""
import json
import os

BLOCKS_JSON = r'D:\genesis-vr\work\mcdata\out\reports\blocks.json'

_DB = None


def _db():
    global _DB
    if _DB is None:
        with open(BLOCKS_JSON, 'r', encoding='utf-8') as f:
            _DB = json.load(f)
    return _DB


class PaletteError(Exception):
    pass


def parse_state(s):
    if '[' in s:
        bid, rest = s.split('[', 1)
        assert rest.endswith(']'), s
        props = {}
        for kv in rest[:-1].split(','):
            k, v = kv.split('=')
            props[k] = v
    else:
        bid, props = s, {}
    return bid, props


def validate_state(s):
    bid, props = parse_state(s)
    db = _db()
    if bid not in db:
        raise PaletteError('unknown block id %r in %r' % (bid, s))
    allowed = db[bid].get('properties') or {}
    for k, v in props.items():
        if k not in allowed:
            raise PaletteError('block %s has no property %r (state %r); has %s' % (bid, k, s, list(allowed)))
        if v not in allowed[k]:
            raise PaletteError('block %s property %s has no value %r (state %r); has %s' % (bid, k, v, s, allowed[k]))


class Palette:
    def __init__(self):
        self.states = ['minecraft:air']
        self.index = {'minecraft:air': 0}

    def idx(self, s):
        if not s.startswith('minecraft:'):
            s = 'minecraft:' + s
        i = self.index.get(s)
        if i is None:
            validate_state(s)
            i = len(self.states)
            self.states.append(s)
            self.index[s] = i
        return i


PAL = Palette()


def b(name, **props):
    """Register 'name' with optional properties and return its palette index."""
    if props:
        s = '%s[%s]' % (name, ','.join('%s=%s' % (k, str(v).lower() if isinstance(v, bool) else v)
                                       for k, v in sorted(props.items())))
    else:
        s = name
    return PAL.idx(s)


COLORS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow', 'lime', 'green', 'cyan',
          'light_blue', 'blue', 'purple', 'magenta', 'pink']

AIR = 0

# ---- terrain ---------------------------------------------------------------------------------------------
WATER = b('water', level=0)
STONE = b('stone')
DIRT = b('dirt')
COARSE = b('coarse_dirt')
SAND = b('sand')
GRAVEL = b('gravel')
GRASS = b('grass_block', snowy=False)
MOSS = b('moss_block')
PACKED_MUD = b('packed_mud')
ANDESITE = b('andesite')
CALCITE = b('calcite')

# ---- concretes / terracottas / glass ----------------------------------------------------------------------
C = {c: b(c + '_concrete') for c in COLORS}
TC = {c: b(c + '_terracotta') for c in COLORS}
TC['plain'] = b('terracotta')
GL = {c: b(c + '_stained_glass') for c in COLORS}
GLASS = b('glass')
TINTED = b('tinted_glass')
WOOL = {c: b(c + '_wool') for c in ('white', 'light_gray', 'gray', 'black', 'red', 'blue', 'yellow')}

# ---- masonry --------------------------------------------------------------------------------------------
QUARTZ = b('quartz_block')
SMOOTH_QUARTZ = b('smooth_quartz')
QUARTZ_BRICKS = b('quartz_bricks')
QUARTZ_PILLAR = b('quartz_pillar', axis='y')
CHISELED_QUARTZ = b('chiseled_quartz_block')
BRICKS = b('bricks')
STONE_BRICKS = b('stone_bricks')
CRACKED_STONE_BRICKS = b('cracked_stone_bricks')
MUD_BRICKS = b('mud_bricks')
TUFF_BRICKS = b('tuff_bricks')
POLISHED_TUFF = b('polished_tuff')
POL_ANDESITE = b('polished_andesite')
POL_DIORITE = b('polished_diorite')
DIORITE = b('diorite')
SMOOTH_STONE = b('smooth_stone')
SANDSTONE = b('sandstone')
CUT_SANDSTONE = b('cut_sandstone')
SMOOTH_SANDSTONE = b('smooth_sandstone')
DEEPSLATE_TILES = b('deepslate_tiles')
POL_DEEPSLATE = b('polished_deepslate')
DEEPSLATE_BRICKS = b('deepslate_bricks')
BLACKSTONE = b('blackstone')
IRON_BLOCK = b('iron_block')
COPPER = b('copper_block')
CUT_COPPER = b('cut_copper')
OX_CUT_COPPER = b('oxidized_cut_copper')
WEATH_CUT_COPPER = b('weathered_cut_copper')
OX_COPPER = b('oxidized_copper')
COPPER_GRATE = b('copper_grate')
RESIN_BRICKS = b('resin_bricks')
CINNABAR_BRICKS = b('cinnabar_bricks')
SULFUR_BRICKS = b('sulfur_bricks')
COAL_BLOCK = b('coal_block')
SEA_LANTERN = b('sea_lantern')
GLOWSTONE = b('glowstone')
SHROOMLIGHT = b('shroomlight')

# ---- wood -----------------------------------------------------------------------------------------------
OAK_PLANKS = b('oak_planks')
SPRUCE_PLANKS = b('spruce_planks')
DARK_OAK_PLANKS = b('dark_oak_planks')
BAMBOO_PLANKS = b('bamboo_planks')
BAMBOO_MOSAIC = b('bamboo_mosaic')
CHERRY_PLANKS = b('cherry_planks')
PALE_OAK_PLANKS = b('pale_oak_planks')
OAK_LOG = b('oak_log', axis='y')
SPRUCE_LOG = b('spruce_log', axis='y')
DARK_OAK_LOG = b('dark_oak_log', axis='y')
CHERRY_LOG = b('cherry_log', axis='y')
POPLAR_LOG = b('poplar_log', axis='y')
STRIPPED_SPRUCE_LOG = b('stripped_spruce_log', axis='y')
LOG_X = b('dark_oak_log', axis='x')
LOG_Z = b('dark_oak_log', axis='z')
SPRUCE_LOG_X = b('spruce_log', axis='x')
SPRUCE_LOG_Z = b('spruce_log', axis='z')


def leaves(kind):
    return b(kind + '_leaves', distance=1, persistent=True)


OAK_LEAVES = leaves('oak')
CHERRY_LEAVES = leaves('cherry')
BIRCH_LEAVES = leaves('birch')
SPRUCE_LEAVES = leaves('spruce')
AZALEA_LEAVES = leaves('azalea')
FLOWER_LEAVES = leaves('flowering_azalea')
YELLOW_POPLAR = leaves('yellow_poplar')
RED_POPLAR = leaves('red_poplar')
ORANGE_POPLAR = leaves('orange_poplar')
MANGROVE_LEAVES = leaves('mangrove')

# ---- shapes ---------------------------------------------------------------------------------------------
FACINGS = ('north', 'south', 'east', 'west')


def stairs(base, facing, half='bottom'):
    return b(base + '_stairs', facing=facing, half=half, shape='straight', waterlogged=False)


def slab(base, typ='bottom'):
    return b(base + '_slab', type=typ, waterlogged=False)


def wall(base):
    return b(base + '_wall', up=True, north='none', south='none', east='none', west='none', waterlogged=False)


# stair / slab caches keyed by base name
_ST = {}


def ST(base):
    """dict (facing, half) -> index"""
    if base not in _ST:
        _ST[base] = {(f, h): stairs(base, f, h) for f in FACINGS for h in ('bottom', 'top')}
    return _ST[base]


_SL = {}


def SL(base):
    """dict type -> index"""
    if base not in _SL:
        _SL[base] = {t: slab(base, t) for t in ('bottom', 'top', 'double')}
    return _SL[base]


# ---- thin things -------------------------------------------------------------------------------------------
def bars(n=False, s=False, e=False, w=False):
    return b('iron_bars', north=n, south=s, east=e, west=w, waterlogged=False)


BARS = {(n, s, e, w): bars(n, s, e, w) for n in (False, True) for s in (False, True)
        for e in (False, True) for w in (False, True)}
IRON_BARS = BARS[(False, False, False, False)]
CHAIN_Y = b('iron_chain', axis='y')
CHAIN_X = b('iron_chain', axis='x')
CHAIN_Z = b('iron_chain', axis='z')
LIGHTNING_ROD = b('lightning_rod', facing='up', powered=False, waterlogged=False)
LADDER = {f: b('ladder', facing=f, waterlogged=False) for f in FACINGS}
RAIL_EW = b('rail', shape='east_west', waterlogged=False)
RAIL_NS = b('rail', shape='north_south', waterlogged=False)
LANTERN = b('lantern', hanging=False, waterlogged=False)
LANTERN_H = b('lantern', hanging=True, waterlogged=False)
SCAFFOLD = b('scaffolding', bottom=False, distance=0, waterlogged=False)
IRON_TRAPDOOR_FLAT = b('iron_trapdoor', facing='north', half='bottom', open=False, powered=False, waterlogged=False)
STONE_PRESSURE = b('stone_pressure_plate', powered=False)
KELP_PLANT = None

# ---- families that need their connection flags fixed after construction -----------------------------------
# (family name -> function (n,s,e,w) -> palette index, plus the index set)
CONNECT_FAMILIES = {
    'iron_bars': {(n, s, e, w): i for (n, s, e, w), i in BARS.items()},
}
