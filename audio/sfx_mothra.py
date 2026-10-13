"""sfx_mothra.py - the guardian moth: a high, wavering, choir-like cry (not a song, no film material), the beat of huge wings,
wind, the shimmer of scales, hurt, dive and death."""
from lib import *
from reg import event


def moth_voice(r, dur, f_keys, amp_db, vowels, voices=3, detune=14, vib=0.012, flutter=0.18, flutter_rate=12.5, whistle=0.25, shimmer=0.35,
               room=0.5, tail=1.7, chaos=0.0, scale=1.35):
    """a cry made of several slightly detuned breathy voices through high 'oh/ah' formants, an octave whistle, a flutter and a
    chime-like shimmer riding the top"""
    n = N(dur)
    t = T(n)
    f = contour(n, f_keys, 9.0)
    amp = env(amp_db, n, "db")
    fc = vcurve(n, vowels, scale)
    acc = np.zeros(n)
    for v in range(voices):
        cents = r.uniform(-detune, detune)
        vr = r.uniform(4.8, 6.2)
        vb = 1 + vib * np.sin(TWO_PI * vr * t + r.uniform(0, 6.28)) * np.minimum(1, t / 0.5 + 0.3)
        ff = f * 2 ** (cents / 1200.0) * vb
        if chaos:
            ff = ff * (1 + chaos * drift(n, r, 25, False))
        src = glottal(ff, r, 0.004 + chaos * 0.3, None, 28, 1.3)
        acc += formant_bank(src, fc, (10, 12, 14), (1.0, 0.6, 0.3), dry=0.03)
    acc = acc / (np.std(acc) + 1e-9)
    br = formant_bank(white(n, r), fc, (4, 5, 6), (1.0, 0.8, 0.5))
    br = br / (np.std(br) + 1e-9)
    wh = np.sin(phase_of(f * 2.0 * (1 + 0.5 * vib * np.sin(TWO_PI * 5.5 * t)))) + 0.3 * np.sin(phase_of(f * 3.0))
    shim = np.sin(phase_of(f * 4.0) + 2 * np.sin(phase_of(f * 5.01))) * (0.5 + 0.5 * np.sin(TWO_PI * 7.3 * t)) ** 2
    fl = 1 - flutter + flutter * (0.5 + 0.5 * np.sin(TWO_PI * flutter_rate * t))
    y = (acc + 0.12 * br + whistle * wh + shimmer * shim * 0.4) * amp * fl
    y = eq(y, [(150, -16), (500, -3), (1200, 0), (3500, 2), (8000, -3), (14000, -12)], circ=False)
    if room:
        y = reverb(y, space("hall", stereo=False), wet=room, dry=1.0, n=n + N(tail))
    return y


@event("mothra.cry", variants=4, peak=-2.0, atten=300, note="Mothra's call: a high, wavering, breathy chorus of detuned voices with a flutter and chimes")
def mothra_cry(r, i):
    if i == 0:     # a rising call that opens like a bell
        d = 3.0
        return moth_voice(r, d, [(0, 660), (0.5, 830), (1.2, 1240), (1.9, 1480), (2.5, 1110), (d, 880)], [(0, -60), (0.1, -20), (0.4, -4), (1.3, 0), (2.2, -3), (2.7, -14), (d, -60)],
                          [(0, "oh"), (0.8, "ah"), (1.8, "oh"), (d, "oo")])
    if i == 1:     # two phrases, a fifth apart
        d = 3.2
        return moth_voice(r, d, [(0, 740), (0.35, 990), (0.8, 1110), (1.05, 880), (1.45, 1320), (2.0, 1670), (d, 1100)], [(0, -60), (0.05, -8), (0.3, 0), (0.9, -6), (1.0, -12), (1.2, -3), (2.1, 0), (2.7, -8), (d, -60)],
                          [(0, "ah"), (0.9, "oh"), (1.6, "ah"), (d, "oh")], flutter=0.25)
    if i == 2:     # a long falling lament
        d = 3.4
        return moth_voice(r, d, [(0, 1560), (0.6, 1480), (1.5, 1170), (2.4, 880), (d, 600)], [(0, -60), (0.2, -10), (0.7, 0), (2.0, -2), (2.8, -9), (d, -60)],
                          [(0, "ah"), (1.2, "oh"), (d, "oo")], vib=0.016, flutter=0.12)
    d = 2.6        # a quick shimmering trill upward
    return moth_voice(r, d, [(0, 880), (0.3, 1110), (0.6, 880), (0.9, 1320), (1.2, 1110), (1.6, 1760), (d, 1480)], [(0, -60), (0.03, -8), (0.2, 0), (1.5, -2), (2.0, -9), (d, -60)],
                      [(0, "oh"), (0.8, "ah"), (d, "oh")], flutter=0.35, flutter_rate=17.0, shimmer=0.55)


@event("mothra.hurt", variants=3, peak=-2.5, atten=260, note="Mothra struck: a short, thin, dissonant cry")
def mothra_hurt(r, i):
    d = 1.3 + 0.1 * i
    p = [1.0, 1.12, 0.9][i]
    return moth_voice(r, d, [(0, 1500 * p), (0.1, 1650 * p), (0.6, 1050 * p), (d, 800 * p)], [(0, -60), (0.02, -4), (0.1, 0), (0.7, -4), (d, -60)],
                      [(0, "eh"), (0.5, "ah"), (d, "oh")], voices=4, detune=55, vib=0.03, flutter=0.3, flutter_rate=19.0, shimmer=0.1, room=0.3, tail=1.2, chaos=0.01)


@event("mothra.death", peak=-1.5, atten=320, note="Mothra falls: a long falling cry that fails, fading chimes of scales, the body landing in the city")
def mothra_death(r, i):
    d = 7.5
    n = N(d)
    v = moth_voice(r, 5.2, [(0, 1450), (0.8, 1500), (2.0, 1100), (3.4, 700), (5.2, 330)], [(0, -60), (0.15, -8), (0.6, 0), (2.5, -3), (3.5, -9), (4.2, -6), (5.2, -60)],
                   [(0, "ah"), (1.5, "oh"), (3.5, "oo"), (5.2, "oo")], voices=4, detune=25, vib=0.02, flutter=0.2, room=0.5, tail=2.0, chaos=0.004)
    y = np.zeros(n)
    place(y, v, 0.0)
    for k in range(34):
        tk = 0.5 + k * 0.12 + r.uniform(-0.03, 0.03)
        f = r.choice([1760, 2093, 2349, 2637, 3136, 3520]) * (1 - 0.35 * tk / d)
        place(y, shimmer_note(f, 0.9, r, 0.25) * 0.08 * max(0.0, 1 - tk / 5.0), tk)
    tb = 5.0
    place(y, pk(deep(2.5, 75, 26, 0.12, 0.6, 0.6), -2), tb)
    place(y, pk(stone_rain(2.5, r, 80, 2, 500, 4000, 1.6), -17), tb)
    place(y, pk(dust(2.4, r, 1.0, 500, 4500, 1.0), -22), tb)
    place(y, pk(whoosh_pass(1.6, r, 200, 1200, 0.5, 0.8, 0.3), -13), 3.9)
    return y


@event("mothra.wing_loop", loop=True, lufs=-22.0, atten=200, q=5, note="vast wings beating about 2.5 times a second: air pushed down, a papery flutter; 1.6 s seamless")
def mothra_wing_loop(r, i):
    dur = 1.6
    n = N(dur)
    t = T(n)
    flaps = 4
    y = np.zeros(n)
    for k in range(flaps):
        tk = k * dur / flaps
        m = N(0.35)
        tt = T(m)
        sw = svf(white(m, r), 180 + 700 * np.sin(np.pi * tt / 0.35), 0.8, "bp") * np.sin(np.pi * tt / 0.35) ** 1.5
        sw /= (np.std(sw) + 1e-9)
        lo = lp(white(m, r), 110, 2) * np.sin(np.pi * tt / 0.35) ** 1.2
        lo /= (np.std(lo) + 1e-9)
        pa = bp(white(m, r), 1200, 5500, 1) * np.sin(np.pi * tt / 0.35) ** 2.0 * np.clip(0.5 + 0.9 * drift(m, r, 70, False), 0, 2)
        pa /= (np.std(pa) + 1e-9)
        place(y, 0.7 * sw + 0.5 * lo + 0.25 * pa, tk, 1.0, None, True)
        place(y, 0.5 * deep(0.2, 70, 40, 0.03, 0.05, 0.3), tk + 0.12, 1.0, None, True)
    air = eq(colored(n, r, -3.0), [(80, -30), (300, 0), (2000, -5), (6000, -20)]) * (0.6 + 0.4 * swell(n, r, 5, 1.0))
    return rn(y, -14) + rn(air, -26)


@event("mothra.gust", variants=3, peak=-3.0, atten=240, note="a gale thrown off Mothra's wings: a heavy rushing blast with grit")
def mothra_gust(r, i):
    dur = 2.4
    n = N(dur)
    t = T(n)
    y = whoosh_pass(dur, r, 220, 1200 + 200 * i, 0.9, 0.7, 0.28, 0.0, 50)
    y = y / (np.std(y) + 1e-9)
    lo = lp(white(n, r), 200, 2) * np.exp(-0.5 * ((t - 0.9) / 0.4) ** 2)
    y += 0.9 * lo / (np.std(lo) + 1e-9) * 0.6
    gr = hp(white(n, r), 2000, 1) * np.exp(-0.5 * ((t - 0.9) / 0.5) ** 2) * np.clip(0.4 + 1.0 * drift(n, r, 90, False), 0, 2)
    y += 0.3 * gr / (np.std(gr) + 1e-9) * 0.5
    return sat(y * 0.4, 1.2)


@event("mothra.scales", variants=3, peak=-8.0, atten=200, note="shimmering scale-dust: a glittering veil of tiny chimes over a breathy hiss")
def mothra_scales(r, i):
    dur = 2.8
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    sw = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.3
    veil = bp(white(n, r), 4500, 12000, 2) * sw * np.clip(0.4 + 1.2 * drift(n, r, 38, False), 0, 2) ** 1.5
    y += 0.5 * veil / (np.std(veil) + 1e-9) * 0.2
    scale = [0, 2, 4, 7, 9, 12, 14, 16, 19]
    tk = 0.0
    while tk < dur - 0.4:
        dens = 12 + 55 * np.sin(np.pi * tk / dur) ** 2
        tk += r.exponential(1.0 / dens)
        f = 880 * 2 ** ((r.choice(scale) + 12 * r.integers(0, 2)) / 12.0 + [0, 0.0, 0.0][i])
        place(y, shimmer_note(f * (1 + 0.5 * i * 0.0), 0.5, r, r.uniform(0.06, 0.22)) * r.uniform(0.1, 0.6) * np.sin(np.pi * min(1.0, tk / dur)) ** 0.5, tk)
    return reverb(y, space("hall", stereo=False), wet=0.4, dry=1.0, n=n + N(1.0))


@event("mothra.dive", variants=2, peak=-2.5, atten=280, note="Mothra stooping on Godzilla: a falling wail, a rising wall of wind, a heavy whoomp at the bottom")
def mothra_dive(r, i):
    dur = 3.0
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    near = (t / 1.9).clip(0, 1) ** 2.2 * np.exp(-np.clip(t - 1.9, 0, None) / 0.5)
    f = 2300 * np.exp(-t / 0.9) + 650 + 120 * i
    wl = np.sin(phase_of(f) + 0.8 * np.sin(phase_of(f * 1.5))) + 0.3 * np.sin(phase_of(f * 2))
    y += 0.55 * wl * near ** 0.7
    wd = svf(white(n, r), 700 + 1500 * np.exp(-((t - 1.9) / 0.7) ** 2), 0.8, "bp") * near
    y += 0.9 * wd / (np.std(wd) + 1e-9) * 0.5
    y += 0.4 * lp(white(n, r), 160, 2) / 1.0 * near
    place(y, pk(deep(1.2, 90, 36, 0.06, 0.3, 0.5), -3), 1.9)
    return reverb(y, space("open", stereo=False), wet=0.2, dry=1.0, n=n)
