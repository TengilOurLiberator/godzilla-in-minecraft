"""Deterministic generator for the miniature Tokyo.  Run:  D:\\voice-assistant\\.venv\\Scripts\\python.exe -I city\\build_city.py

Outputs city/out/tokyo.gzc, city/out/tokyo.json, city/out/validation.txt and city/out/preview/*.png
Arguments (optional): preview names to render: top iso start shore ave tower ... (default: top iso start shore ave);
'none' renders no previews, 'fast' = top only.
"""
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np

import blocks as B
import world as W
import terrain
import plan
import populate
import post
import landmarks
import infra
import meta
import gzc
import validate
import palette_doc

SEED = 20261013
OUT = os.path.join(HERE, 'out')
_T0 = time.time()

START_LOCAL = (300, 565)       # x, z of the player's start (local coordinates)
START_YAW = 170.0


def stage(msg):
    print('[%6.1fs] %s' % (time.time() - _T0, msg), flush=True)


def build():
    rng = np.random.default_rng(SEED)
    w = W.World()
    stage('terrain')
    T = terrain.build_terrain(w, rng)
    surf = np.full((W.Z, W.X), plan.YARD, np.uint16)
    stage('roads')
    kind, carr, inter = plan.paint_roads(T.land, surf)
    top_layer = np.where(T.land & ~T.river & ~T.pond, surf, 0).astype(np.uint16)
    deck = T.river & (kind > 0)
    top_layer = np.where(deck, surf, top_layer)
    w.vol[W.GROUND - W.Y0] = top_layer
    blks = plan.make_blocks()
    prec = plan.precincts(blks)
    stage('districts')
    for b in blks:
        populate.build_zone(w, T, b, rng, None)
    stage('street furniture')
    populate.street_trees_and_lamps(w, T, kind, rng)
    stage('landmarks')
    byc = {b.code: b for b in blks}
    landmarks.tokyo_tower(w, T, byc['TT'].rect, rng)
    landmarks.ferris_wheel(w, T, byc['FW'].rect, rng)
    landmarks.diet_building(w, T, byc['DI'].rect, rng)
    landmarks.tokyo_station(w, T, prec['ST'], rng)
    landmarks.sensoji(w, T, prec['SJ'], rng)
    landmarks.twin_towers(w, T, byc['TW'].rect, rng)
    stage('infrastructure')
    infra.station_block_ground(w, T, byc['SS'].rect, rng)
    rails, rstructs = infra.rail_line(w, T, rng, (byc['SS'].rect[0] + 1, byc['SS'].rect[2] - 1))
    infra.rail_station(w, T, byc['SS'].rect, rng)
    infra.expressway(w, T, rng)
    infra.river_bridges(w, T, rng, shared={214: rstructs['Elevated rail East']})
    infra.rainbow_bridge(w, T, rng)
    infra.port_piers(w, T, rng)
    infra.odaiba(w, T, rng)
    stage('post')
    n = post.connect_bars(w)
    stage('connected %d bars' % n)
    return w, T, kind, rails, rng


def write_outputs(w, T, kind, rails, rng):
    os.makedirs(OUT, exist_ok=True)
    states = B.PAL.states
    gz_path = os.path.join(OUT, 'tokyo.gzc')
    js_path = os.path.join(OUT, 'tokyo.json')
    stage('writing gzc')
    pal, _ = gzc.write_gzc(gz_path, w.vol, states, W.ORIGIN)
    stage('metadata')
    blist, boxes, new_id = meta.building_list(w)
    nodes, edges, node_local = meta.road_graph()
    tanks, masers = meta.road_spawns(w, kind, rng)
    sx, sz = START_LOCAL
    spawn = meta.sidewalk_spawns(w, kind, rng)
    start = list(W.to_world(sx, 29, sz))
    data = {
        'seaLevel': W.SEA, 'groundY': W.GROUND,
        'origin': list(W.ORIGIN), 'size': [W.X, W.H, W.Z],
        'playerStart': {'pos': start, 'yaw': START_YAW},
        'buildings': blist,
        'roads': {'nodes': nodes, 'edges': edges},
        'rails': [[list(W.to_world(*p)) for p in line] for line in rails],
        'sidewalkSpawns': spawn,
        'tankSpawns': tanks, 'maserSpawns': masers,
        'shipSpawns': meta.ship_spawns(),
        'airLanes': meta.air_lanes(),
        'mothraArrival': meta.mothra(),
        # extras (not part of the contract, ignorable)
        'tankSpawnYaws': [0.0] * len(tanks), 'maserSpawnYaws': [0.0] * len(masers),
        'volumeTopY': W.YMAX,
    }
    with open(js_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=1)
    stage('validating')
    report = []
    errs, info = validate.validate(w, T, kind, gz_path, js_path, report)
    with open(os.path.join(OUT, 'validation.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(report) + '\n')
    print('\n'.join(report))
    palette_doc.write(w, os.path.join(HERE, 'PALETTE.md'))
    return data, errs


def previews(w, which):
    import preview
    os.makedirs(os.path.join(OUT, 'preview'), exist_ok=True)
    pv = os.path.join(OUT, 'preview')
    states = B.PAL.states
    sx, sz = START_LOCAL
    # unit forward vector for the start yaw (Minecraft: forward = (-sin yaw, cos yaw))
    import math
    yaw = math.radians(START_YAW)
    fwd = np.array([-math.sin(yaw), 0.0, math.cos(yaw)])
    views = {
        'start': dict(origin=(sx, 64.6, sz), f=fwd + np.array([0, 0.04, 0]), fov=100, size=(1280, 640)),
        'under': dict(origin=(sx, 54.5, sz), f=fwd, fov=100, size=(960, 480)),
        'shore': dict(origin=(300, 90, 446), f=np.array([-0.1, -0.12, -1.0]), fov=100, size=(1280, 640)),
        'ave': dict(origin=(254, 90, 410), f=np.array([0, -0.12, -1.0]), fov=100, size=(1280, 640)),
        'tower': dict(origin=(230, 92, 470), f=np.array([0, 0.12, -1.0]), fov=80, size=(1000, 760)),
        'port': dict(origin=(120, 90, 520), f=np.array([0, -0.12, -1.0]), fov=100, size=(1280, 640)),
        'diet': dict(origin=(140, 90, 330), f=np.array([0, -0.1, -1.0]), fov=90, size=(1100, 600)),
        'temple': dict(origin=(120, 90, 200), f=np.array([0, -0.15, -1.0]), fov=90, size=(1100, 600)),
        'station': dict(origin=(210, 90, 290), f=np.array([0, -0.1, -1.0]), fov=90, size=(1100, 600)),
        'bridge': dict(origin=(330, 100, 400), f=np.array([0.5, -0.1, 0.85]), fov=90, size=(1100, 600)),
        'twin': dict(origin=(280, 100, 262), f=np.array([0, -0.05, -1.0]), fov=80, size=(1000, 640)),
    }
    if 'top' in which:
        preview.topdown(w.vol, states).save(os.path.join(pv, 'topdown.png'))
        stage('top')
    if 'iso' in which:
        f = np.array([0.30, -0.62, -0.72])
        o = np.array([256, 70, 300]) - f * 900
        preview.render(w.vol, states, o, f, size=(1400, 880), ortho=720).save(os.path.join(pv, 'iso_south.png'))
        stage('iso')
    if 'iso2' in which:
        f = np.array([-0.5, -0.5, -0.7])
        o = np.array([256, 70, 300]) - f * 900
        preview.render(w.vol, states, o, f, size=(1400, 880), ortho=720).save(os.path.join(pv, 'iso_southeast.png'))
        stage('iso2')
    for name, v in views.items():
        if name in which:
            preview.render(w.vol, states, v['origin'], v['f'], size=v['size'], fov=v['fov']).save(
                os.path.join(pv, 'view_%s.png' % name))
            stage('view ' + name)


if __name__ == '__main__':
    w, T, kind, rails, rng = build()
    data, errs = write_outputs(w, T, kind, rails, rng)
    args = sys.argv[1:]
    which = ['top', 'iso', 'start', 'shore', 'ave'] if not args else args
    if which == ['none']:
        which = []
    if which == ['fast']:
        which = ['top']
    previews(w, which)
    stage('done' + ('' if not errs else '  (VALIDATION FAILED: %d errors)' % len(errs)))
    sys.exit(1 if errs else 0)

