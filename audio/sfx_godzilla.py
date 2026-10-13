"""sfx_godzilla.py - the monster himself (roar, voice, steps, water, swings) and the atomic breath.

The roar is built like an animal call, not like a film recording: a harmonic glottal source with period-doubling and
chaotic jitter, a throat that is far too large (formants scaled down), a rattling growl gate, a screaming overtone
voice on top, breath noise, a saturated sub, then a canyon of city echoes.  Nothing is sampled.
"""
from lib import *
from reg import event


# ------------------------------------------------------------------ the voice
def roar_voice(r, dur, f_keys, vowels, amp_db, scale=0.72, fry=0.55, fry_rate=(26, 44), scream=0.5, breath=0.35,
               jitter=0.025, subh=0.6, room=0.35, tremor=0.03, sub_level=0.25, drive=2.0, scr_ratio=3.0, tail=2.5):
    n = N(dur)
    t = T(n)
    f = contour(n, f_keys, 7.0)
    f = f * (1 + tremor * np.sin(TWO_PI * r.uniform(5.0, 7.0) * t + r.uniform(0, 6.28)) * np.interp(t, [0, 0.5, dur], [0.2, 1.0, 1.4]))
    f = f * (1 + 0.035 * drift(n, r, 9, False))                      # unsteady throat
    for k in range(int(2 + dur // 2)):                               # voice breaks: sudden leaps of a few semitones, like a real animal call
        t0 = r.uniform(0.25 * dur, 0.8 * dur)
        w = r.uniform(0.07, 0.16)
        f = f * 2 ** (r.choice([-1, 1]) * r.uniform(3, 7) / 12.0 * np.exp(-0.5 * ((t - t0) / (w / 2)) ** 2))
    amp = env(amp_db, n, "db")
    amp_s = amp / amp.max()
    subc = np.clip(np.interp(t, [0, dur], [subh, subh * 0.6]) + 0.5 * drift(n, r, 3, False), 0.0, 1.0)
    src = glottal(f, r, jitter, subc, 44, 1.05)
    src = src * fry_gate(n, r, np.interp(t, [0, dur], fry_rate), fry)
    src = src * (1 - 0.3 * (0.5 + 0.5 * np.sin(phase_of(np.full(n, 83.0) * (1 + 0.2 * drift(n, r, 8, False)))))) # buzzy roughness
    fc = vcurve(n, vowels, scale)
    voc = formant_bank(src, fc, (3.5, 4.5, 6.0), (1.0, 0.8, 0.45), dry=0.1)
    voc = voc / (np.std(voc) + 1e-9)
    # the screaming overtone voice: higher, rougher, brightest at the loudest moment
    s2 = glottal(f * scr_ratio, r, jitter * 2.4, None, 30, 0.8)
    f2c = vcurve(n, [(0, (1200, 2200, 3200)), (dur * 0.5, (1700, 2900, 3800)), (dur, (1100, 2000, 3000))])
    scr = formant_bank(s2, f2c, (6, 8, 10), (1.0, 0.8, 0.5))
    scr = scr / (np.std(scr) + 1e-9) * fry_gate(n, r, 37, 0.45)
    # turbulence: breath noise that is gated by the voice itself, so it sounds like gravel in the throat
    sn = src / (np.std(src) + 1e-9)
    br = formant_bank(white(n, r) * (0.35 + np.abs(sn)), [c * 1.1 for c in fc], (3, 4, 5), (1.0, 0.8, 0.5))
    br = br / (np.std(br) + 1e-9)
    subf = np.clip(f * 0.5, 44, 90)
    sb = np.sin(phase_of(subf)) * fry_gate(n, r, 24, 0.3)
    y = voc * amp_s + scream * 0.5 * scr * amp_s ** 3 + breath * 0.9 * br * amp_s ** 0.8 + sub_level * sb * amp_s ** 1.5
    y = sat(y / np.max(np.abs(y)) * 1.1, drive)
    y = eq(y, [(25, -14), (45, -2), (90, 3), (250, 1), (900, 0), (3000, -3), (7000, -10), (12000, -22)], circ=False)
    if room:
        y = reverb(y, space("roar", stereo=False), wet=room, dry=1.0, n=n + N(tail))
    return y


@event("godzilla.roar", variants=3, peak=-1.2, atten=320, note="the signature roar: v1 rising bellow, v2 double bark then scream, v3 low falling growl")
def roar(r, i):
    if i == 0:
        d = 4.6
        return roar_voice(r, d, [(0, 58), (0.45, 92), (1.1, 215), (1.7, 268), (2.5, 205), (3.5, 118), (d, 66)],
                          [(0, "oo"), (0.5, "oh"), (1.2, "ah"), (2.0, "aa"), (3.0, "oh"), (d, "oo")],
                          [(0, -60), (0.04, -22), (0.2, -8), (0.9, -2), (1.7, 0), (2.7, -3), (3.7, -9), (4.3, -26), (d, -60)])
    if i == 1:
        d = 4.4
        return roar_voice(r, d, [(0, 120), (0.15, 205), (0.5, 120), (0.75, 130), (1.0, 250), (1.6, 295), (2.5, 215), (3.4, 120), (d, 62)],
                          [(0, "ah"), (0.4, "oh"), (0.9, "aa"), (1.6, "ee"), (2.4, "ah"), (3.4, "oh"), (d, "oo")],
                          [(0, -60), (0.03, -12), (0.2, 0), (0.5, -9), (0.72, -14), (0.95, -3), (1.6, 0), (2.5, -2), (3.4, -9), (4.0, -26), (d, -60)],
                          fry=0.5, scream=0.7, jitter=0.03, scr_ratio=2.6)
    d = 5.2
    return roar_voice(r, d, [(0, 46), (0.7, 70), (1.8, 140), (2.6, 150), (3.6, 100), (d, 50)],
                      [(0, "oo"), (0.8, "oh"), (1.8, "uh"), (2.8, "ah"), (d, "oo")],
                      [(0, -60), (0.2, -20), (0.8, -6), (1.8, 0), (3.0, -2), (4.2, -10), (4.8, -26), (d, -60)],
                      scale=0.62, fry=0.7, fry_rate=(22, 36), scream=0.3, breath=0.5, subh=0.85, sub_level=0.2, jitter=0.03)


@event("godzilla.roar_short", variants=3, peak=-1.5, atten=256, note="a short sharp bark for combos and hits")
def roar_short(r, i):
    d = [1.3, 1.1, 1.5][i]
    p = [1.0, 1.12, 0.88][i]
    return roar_voice(r, d, [(0, 95 * p), (0.14, 190 * p), (0.5, 235 * p), (1.0, 125 * p), (d, 70 * p)],
                      [(0, "ah"), (0.3, "aa"), (0.7, "oh"), (d, "oo")],
                      [(0, -60), (0.03, -10), (0.15, 0), (0.6, -3), (d - 0.4, -14), (d, -60)],
                      fry=0.5, scream=0.5, room=0.25, tail=1.0)


@event("godzilla.hurt", variants=3, peak=-1.5, atten=200, note="Godzilla is struck: a strained pained grunt-roar")
def hurt(r, i):
    d = [1.2, 1.0, 1.4][i]
    p = [1.0, 1.15, 0.9][i]
    return roar_voice(r, d, [(0, 150 * p), (0.1, 330 * p), (0.45, 280 * p), (d, 150 * p)],
                      [(0, "ay"), (0.25, "ah"), (0.6, "ow"), (d, "oh")],
                      [(0, -60), (0.02, -8), (0.12, 0), (0.55, -3), (d - 0.3, -16), (d, -60)],
                      scale=0.8, fry=0.55, fry_rate=(40, 55), scream=0.8, jitter=0.05, subh=0.3, room=0.2, tremor=0.05, tail=1.0)


@event("godzilla.death", peak=-1.5, atten=320, note="the fall: a long failing roar sinking to a rattling breath, then the enormous body hitting the ground")
def death(r, i):
    d = 7.0
    n = N(d)
    v = roar_voice(r, 5.4, [(0, 240), (0.5, 270), (1.4, 180), (2.5, 105), (3.6, 66), (5.4, 38)],
                   [(0, "ah"), (0.8, "aa"), (1.8, "oh"), (3.2, "uh"), (5.4, "oo")],
                   [(0, -60), (0.04, -8), (0.25, 0), (1.2, -2), (1.9, -9), (2.3, -4), (3.0, -12), (3.5, -9), (4.2, -20), (5.4, -60)],
                   fry=0.75, fry_rate=(34, 18), scream=0.5, breath=0.8, subh=0.8, room=0.4, jitter=0.04, tremor=0.05)
    y = np.zeros(n)
    place(y, v * 0.85, 0.0)
    # the last breath: a hoarse rattle
    br = bp(white(N(1.6), r), 120, 900, 2) * fry_gate(N(1.6), r, 11, 0.9) * np.interp(T(N(1.6)), [0, 0.2, 1.6], [0, 1, 0]) ** 1.5
    place(y, pk(br, -14), 4.6)
    # the body hits the earth
    tb = 4.1
    place(y, pk(thump(3.0, 70, 22, 0.14, 0.9, 0.004, 0.5), -1.0), tb)
    place(y, pk(plate(48, 3.0, r, 1.0, 18, 10), -9), tb)
    place(y, pk(stone_rain(3.2, r, 90, 2, 500, 3500, 1.6), -16), tb)
    place(y, pk(lp(white(N(2.5), r), 160, 2) * expo(N(2.5), 0.8, 0.01), -9), tb)
    return weight(sat(y * 0.8, 1.3), 0.7, 0.8)


@event("godzilla.footstep", variants=4, peak=-0.8, atten=110, note="heavy sub-bass thud with ground rattle")
def footstep(r, i):
    dur = 2.4
    n = N(dur)
    f0 = [66, 61, 72, 56][i]
    y = np.zeros(n)
    place(y, 0.6 * deep(1.6, f0 * 1.15, 36, 0.07, 0.2, 0.7), 0)                  # the sub thump (harmonics so it reads on a headset)
    place(y, 0.9 * deep(1.0, 150 - 8 * i, 78, 0.035, 0.1, 0.3), 0)               # the body of the blow
    sl = N(0.4)
    y[:sl] += 1.7 * bp(white(sl, r), 120, 2800, 1) * expo(sl, 0.05, 0.0008)      # slap of foot on street
    place(y, pk(rattle_modes(r, 85 + 9 * i, 18, 1.9, 0.42, 1.0, 4.5), -3), 0.01)  # the ground and the buildings ring
    place(y, pk(stone_rain(1.9, r, 80, 1.5, 300, 3000, 1.8), -8), 0.03)          # masonry shaken loose
    rl = colored(N(2.0), r, -5.0, lo=25, hi=260) * expo(N(2.0), 0.6, 0.02) * swell(N(2.0), r, 5.0, 0.7, False)
    place(y, pk(rl, -7), 0.02)
    place(y, 0.3 * deep(0.9, 85, 40, 0.05, 0.16, 0.5), 0.34 + 0.02 * i)            # the earth answering
    place(y, 0.14 * deep(0.8, 75, 38, 0.05, 0.14, 0.5), 0.64)
    y = sat(y * 0.55, 1.5)
    return weight(y, 1.0, 1.0)


@event("godzilla.splash_step", variants=4, peak=-1.0, atten=110, note="a foot coming down in shallow water: thump plus a big slap and spray")
def splash_step(r, i):
    dur = 2.4
    n = N(dur)
    y = np.zeros(n)
    place(y, 0.7 * deep(1.2, 80 - 6 * i, 40, 0.06, 0.18, 0.5), 0)
    y += 1.0 * pk(splash(dur, r, 2.2, 1.0), -2)
    sl = N(0.25)
    y[:sl] += 0.6 * pk(lp(white(sl, r), 1200, 2) * expo(sl, 0.03, 0.0008), 0)
    place(y, 0.4 * pk(pour(1.8, r, 1.0, 1.0, 300, 6000, 0.03, 0.6), -12), 0.25)
    return sat(y * 0.7, 1.3)

@event("godzilla.surface", peak=-1.0, atten=200, note="bursting out of the sea: a boil, the breach, then water pouring off for seconds")
def surface(r, i):
    dur = 7.0
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    tb = 1.0
    # build-up: churning water and a rising pressure rumble
    boil = lp(white(n, r), 700, 2) * np.interp(t, [0, tb - 0.1, tb], [0.03, 0.8, 0.0]) * np.clip(0.6 + 0.7 * drift(n, r, 8, False), 0, 2)
    y += 0.8 * boil / (np.std(boil) + 1e-9) * 0.25
    y += 0.7 * bubbles(dur, r, 50, 150, 900, 5, level=1.0) * np.interp(t, [0, tb, tb + 0.6], [0.3, 1, 0])
    rum = colored(n, r, -6.0, lo=20, hi=130) * np.interp(t, [0, tb, tb + 0.4, 4.5], [0.0, 1.0, 1.0, 0.0])
    y += 0.8 * rum / (np.std(rum) + 1e-9) * 0.3
    # the breach
    place(y, pk(splash(3.4, r, 3.0, 1.0), -2), tb)
    place(y, pk(thump(1.6, 70, 28, 0.1, 0.5, 0.004, 0.5), -2), tb)
    place(y, pk(hp(white(N(0.5), r), 200, 1) * expo(N(0.5), 0.1, 0.002), -3), tb)
    # water pouring off the head, shoulders and spines, thinning out
    pr = pour(dur - tb - 0.2, r, 1.0, 1.4, 250, 6500, 0.15, 1.2)
    pr = pr * np.interp(T(len(pr)), [0, 1.5, dur - tb], [1.0, 0.55, 0.05])
    place(y, pk(pr, -8), tb + 0.2)
    place(y, pk(bubbles(3.0, r, 40, 200, 1500, 4), -14), tb + 0.6)
    y = sat(y * 0.9, 1.2)
    return reverb(y, space("bay", stereo=False), wet=0.2, dry=1.0, n=n)


@event("godzilla.underwater_loop", stereo=True, loop=True, stream=True, lufs=-30.0, atten=0, q=4,
       note="what the head hears under the bay: pressure, a deep hum, slow groans and muffled bubbles")
def underwater_loop(r, i):
    dur = 12.0
    n = N(dur)
    t = T(n)
    low = stereo_noise(n, r, -6.0, lo=20, hi=260, corr=0.5)
    low = low * swell(n, r, 0.4, 0.8)[:, None]
    mid = eq(stereo_noise(n, r, -3.0, corr=0.2), [(60, -40), (200, -8), (500, 0), (1100, -10), (2500, -40)]) * swell(n, r, 0.7, 0.9)[:, None]
    hum = np.sin(TWO_PI * lockf(41.2, dur) * t) + 0.4 * np.sin(TWO_PI * lockf(82.5, dur) * t + 1) + 0.3 * np.sin(TWO_PI * lockf(61.8, dur) * t + 2)
    hum = hum * (0.7 + 0.3 * lfo(n, 3, 0.2))
    bub = np.zeros((n, 2))
    for k in range(34):
        b = bubble(r, r.uniform(150, 700), r.uniform(0.03, 0.09))
        place(bub, b * r.uniform(0.2, 1.0), r.uniform(0, dur), 1.0, r.uniform(-0.8, 0.8), wrap=True)
    bub = eq(bub, [(100, -30), (400, 0), (900, -6), (1800, -30)])
    gr = np.zeros(n)
    for k, (t0, f0, f1) in enumerate([(2.5, 70, 52), (8.0, 48, 64)]):
        g = creak(2.4, r, f0, f1, 0.8, 2.0, 3.0)
        place(gr, pk(g, -3), t0, 1.0, None, True)
    gr = eq(gr, [(30, -20), (60, 0), (300, -4), (700, -30)])
    y = rn(low, -12) + rn(mid, -26) + np.stack([rn(hum, -26)] * 2, axis=1) + rn(bub, -34) + np.stack([rn(gr, -33)] * 2, axis=1)
    y = eq(y, [(20, -12), (30, 0), (500, -1), (1200, -14), (3000, -30)])
    return y


@event("godzilla.tail_swoosh", variants=3, peak=-4.0, atten=80, note="the tail sweeping through the air: a heavy low whoomp")
def tail_swoosh(r, i):
    dur = 1.7
    n = N(dur)
    y = whoosh_pass(dur, r, 90, 420 + 80 * i, 0.7, 0.9, 0.22, 0.0, 40)
    y = y / (np.std(y) + 1e-9)
    lo = lp(white(n, r), 160, 2) * np.exp(-0.5 * ((T(n) - 0.72) / 0.28) ** 2)
    y += 1.2 * lo / (np.std(lo) + 1e-9) * 0.6
    place(y, 0.8 * thump(0.6, 50, 30, 0.06, 0.15, 0.01, 0.2), 0.7)
    return sat(y * 0.35, 1.4)


@event("godzilla.hand_swoosh", variants=4, peak=-5.0, atten=64, note="a claw hand swung fast: airy whoosh with a low body")
def hand_swoosh(r, i):
    dur = 0.9 + 0.05 * i
    n = N(dur)
    y = whoosh_pass(dur, r, 300 + 60 * i, 2600, 0.38, 1.1, 0.2, 0.0, 120)
    y = y / (np.std(y) + 1e-9)
    lo = svf(white(n, r), 140, 1.0, "lp") * np.exp(-0.5 * ((T(n) - 0.34) / 0.14) ** 2)
    y += 0.9 * lo / (np.std(lo) + 1e-9) * 0.6
    return y


# ------------------------------------------------------------------ atomic breath
def _pticks(dur, r, rate, lo, hi, tau, gain_lo=0.2):
    """Poisson ticks placed with wrap so the result is periodic"""
    n = N(dur)
    y = np.zeros(n)
    k = r.poisson(rate * dur)
    for _ in range(k):
        f = np.exp(r.uniform(np.log(lo), np.log(hi)))
        ln = N(tau * 6)
        tt = np.arange(ln) / SR
        c = np.sin(TWO_PI * f * tt + r.uniform(0, 6.28)) * np.exp(-tt / (tau * r.uniform(0.5, 1.5)))
        place(y, c * r.uniform(gain_lo, 1.0), r.uniform(0, dur), 1.0, None, True)
    return y


@event("breath.charge", variants=2, peak=-2.0, atten=96, fout=0.004,
       note="about 1 s, rising: the dorsal plates charge, an electric whine climbs and the air starts to roar")
def breath_charge(r, i):
    dur = 1.0 + 0.0 * i
    n = N(dur)
    t = T(n)
    p = t / dur
    f = 55 * (1500 / 55) ** (p ** 1.4)
    ph = phase_of(f)
    idx = 0.5 + 5.0 * p ** 2
    tone = np.sin(ph + idx * np.sin(phase_of(f * 1.5))) + 0.5 * np.sin(2 * ph + 1)
    saw = hosc(f * 0.5, n, nh=30, rolloff=1.0) * p ** 1.5
    nz = svf(white(n, r), 300 + 6000 * p ** 1.5, 1.2, "bp") * p ** 1.2
    tk = np.zeros(n)
    tt = 0.0
    while tt < dur:
        tt += r.exponential(1.0 / (6 + 140 * (tt / dur) ** 2))
        if tt < dur:
            place(tk, click(r, r.uniform(2500, 7000), 0.002) * r.uniform(0.3, 1.0), tt)
    sub = np.sin(phase_of(30 + 25 * p)) * p ** 1.3
    y = 0.6 * tone * (0.2 + 0.8 * p ** 1.2) + 0.5 * saw + 0.9 * nz + 0.35 * tk + 0.8 * sub
    y = sat(y * (0.5 + p), 1.3)
    return y


@event("breath.loop", loop=True, lufs=-17.0, atten=120, q=5,
       note="the beam: a seamless 1.5 s roaring column of energy; replay it every 30 ticks while the trigger is held")
def breath_loop(r, i):
    dur = 1.5
    n = N(dur)
    t = T(n)
    roar_ = colored(n, r, -3.0, lo=60, hi=3500)
    roar_ = eq(roar_, [(40, -20), (100, 0), (500, 2), (1800, -2), (5000, -14)])
    roar_ = roar_ * (0.75 + 0.25 * swell(n, r, 14, 0.8))
    buzz = np.zeros(n)
    for fk, a in [(lockf(96, dur), 1.0), (lockf(193, dur), 0.8), (lockf(290, dur), 0.6), (lockf(387, dur), 0.4), (lockf(485, dur), 0.25), (lockf(774, dur), 0.15)]:
        buzz += a * np.sin(TWO_PI * fk * t + r.uniform(0, 6.28))
    buzz *= 0.75 + 0.25 * lfo(n, 21, 0.1) * lfo(n, 7, 0.3)
    sizz = hp(circ(lambda z: hp(z, 3500, 2), white(n, r)), 3500, 1) * (0.6 + 0.4 * swell(n, r, 40, 1.0))
    sub = np.sin(TWO_PI * lockf(48, dur) * t) + 0.5 * np.sin(TWO_PI * lockf(96, dur) * t + 1)
    sub *= 0.8 + 0.2 * lfo(n, 6)
    y = rn(roar_, -12) + rn(buzz, -15) + rn(sizz, -26) + rn(sub, -17)
    y = sat(y * 1.3, 1.8)
    return y


@event("breath.end", variants=2, peak=-2.5, atten=96, note="the beam cuts out: falling whine, a last gust of steam and a bass thump")
def breath_end(r, i):
    dur = 1.4
    n = N(dur)
    t = T(n)
    p = t / dur
    f = 700 * (50 / 700) ** (p ** 0.6)
    ph = phase_of(f)
    tone = (np.sin(ph + 2.5 * np.sin(phase_of(f * 1.5)) * (1 - p)) + 0.4 * np.sin(2 * ph)) * np.exp(-t / 0.35)
    nz = svf(white(n, r), 4000 * np.exp(-t / 0.3) + 200, 1.0, "bp") * np.exp(-t / 0.28)
    hs = hp(white(n, r), 3000, 1) * np.exp(-t / 0.5) * 0.4
    tk = _pticks(dur, r, 40, 1500, 7000, 0.003) * np.exp(-t / 0.35)
    y = 0.6 * tone + 1.0 * nz + hs + 0.25 * tk
    place(y, 1.0 * thump(0.8, 90, 36, 0.05, 0.2, 0.002, 0.4), 0.0)
    return sat(y * 0.8, 1.2)


@event("breath.impact_loop", loop=True, lufs=-19.0, atten=100, q=5,
       note="where the beam lands: a seamless 1.5 s of crackling fire, hissing steam and low detonation rumble")
def breath_impact_loop(r, i):
    dur = 1.5
    n = N(dur)
    t = T(n)
    cr = _pticks(dur, r, 140, 800, 6500, 0.004, 0.15)
    cr2 = _pticks(dur, r, 14, 200, 900, 0.02, 0.3)
    low = colored(n, r, -6.0, lo=25, hi=190) * (0.7 + 0.3 * swell(n, r, 9, 1.0))
    steam = eq(colored(n, r, 0.0), [(200, -40), (1500, -10), (4000, 0), (9000, -8), (14000, -30)]) * (0.6 + 0.4 * swell(n, r, 20, 1.0))
    thuds = np.zeros(n)
    for k in range(3):
        place(thuds, thump(0.4, r.uniform(110, 170), r.uniform(46, 70), 0.03, 0.09, 0.002, 0.3) * r.uniform(0.5, 1), r.uniform(0, dur), 1.0, None, True)
    y = rn(cr, -15) + rn(cr2, -17) + rn(low, -12) + rn(steam, -25) + rn(thuds, -19)
    return sat(y * 1.1, 1.4)

