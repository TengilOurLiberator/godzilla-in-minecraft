"""Average colour of the Patrix 32x texture for each palette block (used by the previews and PALETTE.md)."""
import os
import numpy as np
from PIL import Image

PATRIX = r'D:\godzilla-vr\work\patrix\assets\minecraft'
TEX = os.path.join(PATRIX, 'textures', 'block')
CTM = os.path.join(PATRIX, 'optifine', 'ctm', 'patrix')

_cache = {}


def tex_avg(name, folder=None):
    key = (folder or TEX, name)
    if key in _cache:
        return _cache[key]
    p = os.path.join(folder or TEX, name + '.png')
    if not os.path.exists(p):
        _cache[key] = None
        return None
    im = Image.open(p).convert('RGBA')
    if im.height > im.width:  # animated strip: first frame
        im = im.crop((0, 0, im.width, im.width))
    a = np.asarray(im, dtype=np.float64)
    w = a[..., 3] / 255.0
    tot = w.sum()
    if tot < 1:
        _cache[key] = None
        return None
    rgb = (a[..., :3] * w[..., None]).sum((0, 1)) / tot
    cov = tot / (a.shape[0] * a.shape[1])
    _cache[key] = (rgb, cov)
    return _cache[key]


def ctm_avg(sub):
    """Average over the connected-texture tiles (optifine ctm) of a block family."""
    d = os.path.join(CTM, sub)
    cols = []
    if os.path.isdir(d):
        for f in os.listdir(d):
            if f.endswith('.png') and not f.endswith('_n.png') and not f.endswith('_s.png'):
                r = tex_avg(f[:-4], d)
                if r:
                    cols.append(r[0])
    return np.mean(cols, axis=0) if cols else None


ALIAS = {
    'stone_brick': 'stone_bricks', 'brick': 'bricks', 'nether_brick': 'nether_bricks', 'mud_brick': 'mud_bricks',
    'tuff_brick': 'tuff_bricks', 'deepslate_tile': 'deepslate_tiles', 'deepslate_brick': 'deepslate_bricks',
    'quartz': 'quartz_block_side', 'smooth_quartz': 'quartz_block_bottom', 'quartz_block': 'quartz_block_side',
    'smooth_stone': 'smooth_stone', 'quartz_pillar': 'quartz_pillar_side',
    'sandstone': 'sandstone', 'smooth_sandstone': 'sandstone_top', 'cut_sandstone': 'cut_sandstone',
    'iron_bars': 'iron_bars', 'iron_chain': 'iron_chain', 'grass_block': 'grass_block_top',
    'oak_log': 'oak_log', 'spruce_log': 'spruce_log', 'dark_oak_log': 'dark_oak_log', 'cherry_log': 'cherry_log',
    'poplar_log': 'poplar_log', 'stripped_spruce_log': 'stripped_spruce_log',
    'polished_andesite': 'polished_andesite', 'cinnabar_brick': 'cinnabar_bricks', 'sulfur_brick': 'sulfur_bricks',
    'resin_brick': 'resin_bricks', 'bamboo_mosaic': 'bamboo_mosaic', 'copper': 'copper_block',
    'rail': 'rail', 'scaffolding': 'scaffolding_side', 'lantern': 'lantern', 'ladder': 'ladder',
    'lightning_rod': 'lightning_rod', 'sea_lantern': 'sea_lantern', 'moss_block': 'moss_block',
    'iron_trapdoor': 'iron_trapdoor', 'water': None,
}

SUFFIXES = ('_stairs', '_slab', '_wall', '_fence', '_pane', '_fence_gate', '_pressure_plate')


def block_color(state):
    """Return (r,g,b,alpha) 0..255 for a palette state string."""
    s = state.split(':', 1)[1]
    name = s.split('[', 1)[0]
    if name == 'air':
        return (0, 0, 0, 0)
    if name == 'water':
        return (44, 96, 190, 0.5 * 255)
    if name.endswith('_leaves'):
        tints = {'oak': (84, 146, 52), 'birch': (110, 160, 70), 'spruce': (70, 110, 60),
                 'cherry': None, 'azalea': None, 'flowering_azalea': None, 'mangrove': (100, 150, 60),
                 'yellow_poplar': None, 'red_poplar': None, 'orange_poplar': None}
        k = name[:-7]
        if tints.get(k):
            return tints[k] + (255,)
        r = tex_avg(name)
        if r:
            return tuple(int(v) for v in r[0]) + (255,)
        return (80, 140, 60, 255)
    if name == 'stone':
        c = ctm_avg('stone')
        return tuple(int(v) for v in c) + (255,)
    if name in ('grass_block',):
        return (96, 150, 62, 255)
    base = name
    if base.endswith('_carpet'):
        base = base[:-7] + '_wool'
    for suf in SUFFIXES:
        if base.endswith(suf):
            base = base[:-len(suf)]
            break
    cands = []
    if base in ALIAS and ALIAS[base]:
        cands.append(ALIAS[base])
    cands += [base, base + 's', base + '_side', base + '_top', base + '_front', base + '_bottom']
    for c in cands:
        r = tex_avg(c)
        if r is not None:
            rgb, cov = r
            alpha = 255
            if 'stained_glass' in name or name in ('glass', 'tinted_glass'):
                alpha = 150 if name != 'glass' else 60
                if name == 'glass':
                    rgb = np.array([205, 230, 240])
            elif cov < 0.9:
                alpha = 255 if name not in ('iron_bars', 'iron_chain') else 255
            return tuple(int(v) for v in rgb) + (alpha,)
    raise KeyError('no texture colour for ' + state)


def palette_colors(states):
    out = np.zeros((len(states), 4), np.uint8)
    for i, s in enumerate(states):
        try:
            out[i] = block_color(s)
        except KeyError as e:
            print('WARN', e)
            out[i] = (255, 0, 255, 255)
    return out
