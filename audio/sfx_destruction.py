"""sfx_destruction.py - smashing, collapsing, rubble, explosions and fire.  Masonry is Poisson stone ticks and heavy clods,
glass is shards on a falling band plus ringing slivers, steel is plate modes and stick-slip groans, a collapse is all of
them under a swelling rumble."""
from lib import *
from reg import event


def _smash(r, i, size, dur, glass, steel, f0, wet=0.2):
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    s = size
    place(y, deep(min(dur, 1.2 * s + 0.6), f0, 52, 0.05, 0.1 * s + 0.07, 0.4), 0, 1.0)
    cr = N(0.05)
    y[:cr] += 0.9 * hp(white(cr, r), 250, 1) * expo(cr, 0.012 * s, 0.0004)
    crunch = bp(white(n, r), 250, 3800, 2) * expo(n, 0.14 * s + 0.05, 0.002) * np.clip(0.5 + 1.0 * drift(n, r, 45, False), 0, 2) ** 1.5
    y += 0.45 * crunch / (np.std(crunch) + 1e-9) * 0.4
    place(y, pk(stone_rain(dur * 0.9, r, 55 * s, 2, 500, 4800, 1.3, 1.0, 0.25), -9), 0.015)
    for k in range(int(2 + 3 * s)):                                   # slabs and chunks dropping
        tk = r.uniform(0.04, dur * 0.55)
        place(y, deep(0.5, r.uniform(90, 170), r.uniform(45, 70), 0.03, 0.08 + 0.05 * s, 0.4) * r.uniform(0.2, 0.7) * min(1.0, 0.5 + s * 0.3), tk)
    place(y, pk(dust(dur, r, 1.0, 600, 4500, 0.5 * s + 0.2), -26), 0.05)
    if glass:
        place(y, pk(glass_pane(r, min(dur, 1.4), 1.0, s), -7) * glass, r.uniform(0.0, 0.05))
    if steel:
        place(y, pk(metal_crash(min(dur, 1.8), r, s, 7 + int(6 * s), 140, 2200), -9) * steel, 0.01)
        if s >= 2:
            place(y, pk(creak(min(1.4, dur * 0.5), r, 95, 60, 0.8, 2.5, 3.0), -14) * steel, 0.2)
    y = weight(sat(y * 0.8, 1.3), 0.8, 1.0)
    return reverb(y, space("city", stereo=False), wet=wet, dry=1.0, n=n)


@event("smash.small", variants=4, peak=-3.0, atten=70, note="a hand or foot through a wall, a fence or a window: crack, brick scatter")
def smash_small(r, i):
    return _smash(r, i, 0.7, 1.0 + 0.05 * i, 0.25 if i % 2 else 0.0, 0.0, 190 - 12 * i)


@event("smash.medium", variants=4, peak=-2.0, atten=90, note="through a floor of a building: masonry, glass and a bit of steel")
def smash_medium(r, i):
    return _smash(r, i, 1.4, 1.8 + 0.1 * i, 0.6, 0.35, 150 - 8 * i)


@event("smash.large", variants=3, peak=-1.0, atten=130, note="breaking a tower or a bridge: slabs, steel groans, glass, a long tumble")
def smash_large(r, i):
    return _smash(r, i, 2.6, 3.2 + 0.3 * i, 0.9, 0.8, 120 - 8 * i, 0.3)


@event("smash.glass", variants=4, peak=-3.0, atten=70, note="glass breaking: a curtain wall or a shop front")
def smash_glass(r, i):
    n = N(1.8)
    y = np.zeros(n)
    k = [1, 2, 3, 2][i]
    for j in range(k):
        place(y, pk(glass_pane(r, 1.5 - 0.1 * j, 1.0, 1.0 + 0.4 * j), -2.0 - 3 * j), j * r.uniform(0.04, 0.12))
    place(y, 0.25 * deep(0.4, 160, 70, 0.02, 0.05, 0.3), 0.0)
    return reverb(y, space("city", stereo=False), wet=0.15, dry=1.0, n=n)


@event("collapse.rumble", variants=3, lufs=-16.0, atten=160, note="a building coming down: groans, a swelling avalanche, glass and slab impacts, a final thud; 3.6 / 4.8 / 6.0 s")
def collapse_rumble(r, i):
    dur = [3.6, 4.8, 6.0][i]
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    peak_t = dur * [0.42, 0.45, 0.4][i]
    body_env = np.interp(t, [0, 0.25, peak_t * 0.8, peak_t, dur * 0.82, dur], [0.0, 0.25, 0.8, 1.0, 0.45, 0.0])
    # structural failure: steel groans and concrete cracks
    for k in range(2 + i):
        tk = r.uniform(0.0, peak_t * 0.7)
        place(y, pk(creak(r.uniform(0.5, 1.1), r, r.uniform(70, 160), r.uniform(50, 110), 0.8, 2.6, 3.0), -14) * r.uniform(0.4, 0.9), tk)
    for k in range(5 + 2 * i):
        tk = r.uniform(0.0, peak_t * 0.9)
        c = N(0.03)
        place(y, pk(hp(white(c, r), 400, 1) * expo(c, 0.006), -8) * r.uniform(0.3, 1.0), tk)
        place(y, deep(0.4, r.uniform(100, 180), 55, 0.03, 0.07, 0.3) * r.uniform(0.2, 0.6), tk)
    # the avalanche: low rumble, grainy mid roar, and the tick-tick of falling stone
    rl = colored(n, r, -6.0, lo=22, hi=230) * (0.6 + 0.4 * swell(n, r, 7, 0.8, False))
    y += pk(rl * body_env, -4)
    md = bp(white(n, r), 140, 1500, 2) * np.clip(0.5 + 0.9 * drift(n, r, 30, False), 0, 2) ** 1.5 * body_env ** 1.3
    y += pk(md, -8)
    sr_ = pk(stone_rain(dur * 0.95, r, 140, 3, 450, 4500, 0.8, 1.0, 0.35), -10)
    place(y, sr_ * np.interp(T(len(sr_)), [0, peak_t, dur], [0.15, 1.0, 0.25]), 0.1)
    # slabs hitting the ground: heavy, accelerating then thinning
    tt = 0.15
    while tt < dur * 0.9:
        g = np.interp(tt, [0, peak_t, dur], [0.25, 1.0, 0.25])
        place(y, deep(0.9, r.uniform(70, 150), 38, 0.05, 0.18, 0.5) * g ** 1.5 * r.uniform(0.4, 1.0), tt)
        tt += r.exponential(0.3 - 0.12 * np.interp(tt, [0, peak_t, dur], [0, 1, 0]))
    for k in range(2 + i):
        place(y, pk(glass_pane(r, 1.0, 1.0, 1.4), -10) * r.uniform(0.5, 1.0), r.uniform(0.1, peak_t))
    tf = dur * 0.8
    place(y, pk(deep(1.4, 85, 30, 0.08, 0.34, 0.6), 0.0) * 0.85, tf)                  # the last floor hits the ground
    place(y, pk(dust(dur - tf, r, 1.0, 500, 4000, 1.0), -14), tf)
    y = weight(sat(y * 0.7, 1.2), 1.0, 1.2)
    y = y * np.interp(t, [0, 0.4, peak_t, dur * 0.8, dur], [0.3, 0.55, 1.0, 1.0, 0.0]) ** 1.0
    return reverb(y, space("city", stereo=False), wet=0.3, dry=1.0, n=n)


@event("collapse.settle", variants=3, lufs=-22.0, atten=100, note="the rubble settling after a collapse: slumps, trickles, a last ticking")
def collapse_settle(r, i):
    dur = 3.0 + 0.4 * i
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    tt = 0.0
    while tt < dur * 0.8:
        place(y, deep(0.8, r.uniform(70, 120), 40, 0.04, 0.15, 0.4) * r.uniform(0.2, 0.7) * (1 - tt / dur), tt)
        tt += r.exponential(0.45 + 0.7 * tt / dur)
    place(y, pk(stone_rain(dur, r, 28, 0.8, 500, 4500, 1.2), -8), 0.0)
    place(y, pk(dust(dur, r, 1.0, 800, 5500, 1.0), -22), 0.0)
    rl = colored(n, r, -6.0, lo=25, hi=160) * expo(n, 1.0, 0.1)
    y += pk(rl, -16)
    return reverb(y, space("city", stereo=False), wet=0.25, dry=1.0, n=n)


@event("rubble.hit", variants=5, peak=-4.0, atten=60, note="a chunk of masonry landing: a thud, a skitter")
def rubble_hit(r, i):
    dur = 0.8
    n = N(dur)
    y = np.zeros(n)
    place(y, deep(0.5, r.uniform(110, 200), 60, 0.025, 0.07 + 0.01 * i, 0.4), 0)
    k = N(0.03)
    y[:k] += 0.5 * bp(white(k, r), 300, 3000, 1) * expo(k, 0.006, 0.0003)
    place(y, pk(stone_rain(0.7, r, 30, 2, 700, 4500, 1.0, 1.0, 0.1), -12), 0.03 + 0.01 * i)
    return y


# ------------------------------------------------------------------ explosions and fire
def _blast(r, dur, size, bright=1.0, sub=(80, 24)):
    y = blast(r, dur, size, sub=sub, crack=1.0, tail=1.0, bright=bright, drive=1.8, debris_level=0.6)
    return weight(y, 1.0, 1.1)


@event("explosion.car", variants=4, peak=-1.5, atten=100, note="a car going up: a sharp whump, metal and glass flying, tyres popping")
def explosion_car(r, i):
    dur = 3.0
    n = N(dur)
    y = _blast(r, dur, 0.55, 1.1, (95, 30))
    place(y, pk(metal_crash(1.8, r, 0.7, 12, 200, 3000), -8), 0.05)
    place(y, pk(glass_pane(r, 1.0, 1.0, 0.8), -12), 0.02)
    for k in range(r.integers(1, 4)):
        place(y, pk(deep(0.3, r.uniform(180, 300), 100, 0.01, 0.04, 0.4), -10), r.uniform(0.3, 1.2))   # tyre bursts
    place(y, pk(hiss(1.2, r, 400, 6000, 0.05, 0.6), -18), 0.15)                                    # the fireball's breath
    return y


@event("explosion.large", variants=3, peak=-1.0, atten=190, note="a big blast (fuel tank, building, ship): the whole street shakes")
def explosion_large(r, i):
    dur = 6.0
    y = _blast(r, dur, 1.35, 1.0, (70, 22))
    place(y, pk(metal_crash(2.5, r, 1.2, 20, 100, 2000), -10), 0.1)
    place(y, pk(stone_rain(3.5, r, 120, 3, 400, 4000, 1.4), -16), 0.2)
    return y


@event("explosion.fuel", variants=2, peak=-1.0, atten=240, note="a huge fireball: ignition whump, a long roaring bloom, a rolling thunder, crackle")
def explosion_fuel(r, i):
    dur = 8.0
    n = N(dur)
    t = T(n)
    y = _blast(r, dur, 1.7, 0.9, (60, 20))
    # the fireball bloom: a roar that swells for a second and a half, falling in pitch
    fb = svf(white(n, r), 140 + 1800 * np.exp(-((t - 0.9) / 1.2) ** 2) + 120 * np.exp(-t / 3), 0.8, "lp")
    bl = np.interp(t, [0, 0.06, 0.8, 1.5, 3.5, 6.0], [0, 0.6, 1.0, 0.9, 0.35, 0.0])
    y += pk(fb * bl * np.clip(0.7 + 0.5 * drift(n, r, 12, False), 0.1, 1.6), -5)
    place(y, pk(_pcrackle(r, 5.0, 90, 2.0), -16), 0.8)
    place(y, 0.5 * pk(deep(3.0, 62, 30, 0.2, 0.9, 0.5), -2), 0.0)
    return weight(y, 0.8, 1.0)


def _pcrackle(r, dur, rate, rate_end):
    n = N(dur)
    y = np.zeros(n)
    t = 0.0
    while t < dur:
        t += r.exponential(1.0 / max(np.interp(t, [0, dur], [rate, rate_end]), 0.1))
        if t >= dur:
            break
        f = np.exp(r.uniform(np.log(600), np.log(6500)))
        c = N(0.02)
        tt = T(c)
        place(y, (np.sin(TWO_PI * f * tt + r.uniform(0, 6.28)) * 0.5 + white(c, r) * 0.5) * np.exp(-tt / r.uniform(0.002, 0.006)) * r.uniform(0.15, 1.0), t)
    return y


@event("fire.loop", loop=True, lufs=-26.0, atten=60, q=5, note="a burning building: crackle, a low flame roar, hiss and pops; 3 s seamless")
def fire_loop(r, i):
    dur = 3.0
    n = N(dur)
    cr = np.zeros(n)
    for _ in range(r.poisson(110 * dur)):
        f = np.exp(r.uniform(np.log(500), np.log(6000)))
        c = N(0.015)
        tt = T(c)
        place(cr, (np.sin(TWO_PI * f * tt + r.uniform(0, 6.28)) + 0.6 * white(c, r)) * np.exp(-tt / r.uniform(0.0015, 0.005)) * r.uniform(0.1, 1.0), r.uniform(0, dur), 1.0, None, True)
    pops = np.zeros(n)
    for _ in range(r.poisson(6 * dur)):
        place(pops, deep(0.15, r.uniform(140, 260), 90, 0.01, 0.03, 0.2) * r.uniform(0.3, 1.0), r.uniform(0, dur), 1.0, None, True)
    roar_ = eq(colored(n, r, -4.0), [(30, -30), (90, 0), (300, 2), (900, -5), (3000, -14), (9000, -30)]) * (0.6 + 0.4 * swell(n, r, 5, 1.0))
    hs = eq(colored(n, r, 0.0), [(500, -40), (3000, -4), (7000, 0), (12000, -10)]) * (0.5 + 0.5 * swell(n, r, 25, 1.0))
    y = rn(cr, -17) + rn(pops, -22) + rn(roar_, -14) + rn(hs, -28)
    return y
