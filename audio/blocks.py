"""blocks.py - reusable sound-design pieces (modal bodies, creaks, engines, blasts, birds ...) built on dsp.py.
All return mono float arrays unless stated."""
import numpy as np
from dsp import *

# ------------------------------------------------------------------ modal bodies
BAR_RATIOS = [1.0, 2.756, 5.404, 8.933, 13.34, 18.64]               # free-free steel bar
BELL_RATIOS = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166]   # a minor-third bell: hum, prime, tierce ...
BELL_AMPS = [0.55, 1.0, 0.75, 0.5, 0.55, 0.32, 0.28, 0.22, 0.12]
MEMBRANE = [1.0, 1.593, 2.136, 2.296, 2.653, 2.918, 3.156]            # circular drumhead


def bell(f, dur, r=None, tau=1.2, bright=1.0, strike=0.004):
    """inharmonic bell: modal bank, higher modes die sooner; a hint of FM-ish bite from a tiny noise tick at the strike"""
    freqs = [f * k for k in BELL_RATIOS]
    taus = [tau / (k ** 0.8) for k in BELL_RATIOS]
    amps = [a * (bright if k > 1.5 else 1.0) for a, k in zip(BELL_AMPS, BELL_RATIOS)]
    y = modal(freqs, taus, amps, dur, r, detune=3, beat=0.0012)
    if r is not None:
        n = N(dur)
        y[:N(0.02)] += 0.15 * bright * hp(white(N(0.02), r), 2500, 1) * expo(N(0.02), strike)
    return y


def steel_bar(f, dur, r, tau=0.6, bright=1.0):
    taus = [tau / (1 + 0.55 * i) for i in range(len(BAR_RATIOS))]
    amps = [1.0, 0.6 * bright, 0.35 * bright, 0.2 * bright, 0.1 * bright, 0.06 * bright]
    return modal([f * k for k in BAR_RATIOS], taus, amps, dur, r, detune=4, beat=0.002)


def plate(f, dur, r, tau=0.5, nmodes=14, spread=9.0, bright=1.0):
    """irregular plate/panel: random inharmonic modes spread over `spread` octaves-ish ratio"""
    ratios = np.sort(np.exp(r.uniform(0, np.log(spread), nmodes)))
    ratios[0] = 1.0
    taus = [tau * (1.0 / k) ** 0.45 for k in ratios]
    amps = [(1.0 / k ** 0.5) * (bright if k > 2 else 1.0) * r.uniform(0.5, 1.0) for k in ratios]
    return modal(f * ratios, taus, amps, dur, r, beat=0.0015)


def drum(f, dur, r, tau=0.4, drop=1.25, tau_drop=0.05, click=0.4):
    """membrane with a pitch drop and a stick tick"""
    n = N(dur)
    t = T(n)
    bend = 1 + (drop - 1) * np.exp(-t / tau_drop)
    y = np.zeros(n)
    for k, a in zip(MEMBRANE, [1.0, 0.5, 0.35, 0.3, 0.2, 0.15, 0.1]):
        ph = phase_of(f * k * bend) + r.uniform(0, 6.28)
        y += a * np.sin(ph) * np.exp(-t / (tau / (k ** 0.6)))
    y[:N(0.015)] += click * hp(white(N(0.015), r), 800, 1) * expo(N(0.015), 0.003)
    return endfade(y)


def glass_ring(f, dur, r, tau=0.4):
    ratios = [1.0, 2.32, 4.25, 6.63, 9.38]
    return modal([f * k for k in ratios], [tau / (1 + 0.4 * i) for i in range(5)], [1, .7, .5, .3, .2], dur, r, detune=6, beat=0.003)


# ------------------------------------------------------------------ creaks, squeals, scrapes
def creak(dur, r, f0, f1, rough=0.6, bright=3.0, q=4.0, slip=0.6):
    """stick-slip friction: a harmonic-rich buzz with jittery pitch, gated by random slips, through a moving resonance"""
    n = N(dur)
    base = np.exp(np.linspace(np.log(f0), np.log(f1), n))
    f = base * np.exp(0.12 * rough * drift(n, r, 22, False))
    y = hosc(f, nh=24, rolloff=0.9)
    g = np.clip(0.55 + slip * 0.9 * drift(n, r, 16, False), 0.0, 1.6)
    y = y * g
    y = svf(y, base * bright * np.exp(0.2 * drift(n, r, 6, False)), q, "bp") + 0.25 * y
    y += 0.05 * rough * svf(white(n, r), base * 6, 2.0, "bp") * g
    e = np.minimum(1, T(n) / 0.05) * np.minimum(1, (dur - T(n)) / 0.08)
    return y * e


def squeal(dur, r, f0, f1, level=1.0, wobble=0.01):
    """thin high squeak: nearly pure, slightly buzzy, slow wobble"""
    n = N(dur)
    f = np.exp(np.linspace(np.log(f0), np.log(f1), n)) * (1 + wobble * drift(n, r, 9, False))
    ph = phase_of(f)
    y = np.sin(ph) + 0.35 * np.sin(2 * ph + 0.5) + 0.12 * np.sin(3 * ph)
    y *= np.clip(0.8 + 0.5 * drift(n, r, 14, False), 0.1, 1.4)
    return level * y * np.minimum(1, T(n) / 0.02) * np.minimum(1, (dur - T(n)) / 0.04)


def scrape(dur, r, lo=300, hi=3500, grit=1.0):
    """metal dragged over stone: bandpassed noise broken into grains by a fast random gate"""
    n = N(dur)
    x = white(n, r)
    f = np.exp(np.log(lo) + (np.log(hi) - np.log(lo)) * (0.5 + 0.5 * np.tanh(drift(n, r, 8, False))))
    y = svf(x, f, 1.5, "bp")
    gate = np.clip(0.4 + grit * drift(n, r, 60, False), 0, 2) ** 2
    return y * gate


def hiss(dur, r, lo=2500, hi=9000, attack=0.02, tau=None, level=1.0):
    n = N(dur)
    y = bp(white(n, r), lo, hi, 2)
    e = np.minimum(1, T(n) / max(attack, 1e-4))
    e = e * (np.exp(-T(n) / tau) if tau else np.minimum(1, (dur - T(n)) / max(attack, 1e-3)))
    return level * y * e


def click(r, f=3000, tau=0.002, dur=0.03):
    n = N(dur)
    t = T(n)
    return (np.sin(TWO_PI * f * t) * 0.5 + 0.5 * r.standard_normal(n)) * np.exp(-t / tau)


def clunk(f, dur, r, tau=0.06, weight=1.0):
    """heavy relay / bolt / latch: low thump + a short plate + a click"""
    n = N(dur)
    y = weight * thump(dur, f * 0.8, f * 0.45, 0.02, tau * 2.0, 0.001)
    y += 0.7 * plate(f * 2.0, dur, r, tau, 8, 6.0)
    y[:N(0.01)] += 0.8 * hp(white(N(0.01), r), 1500, 1) * expo(N(0.01), 0.0025)
    return y


# ------------------------------------------------------------------ machines
def engine(dur, rpm, r, cyl=4, bright=0.5, rough=0.5, loop=False):
    n = N(dur)
    rpm = np.broadcast_to(np.asarray(rpm, dtype=np.float64), (n,)).copy() if np.ndim(rpm) == 0 else np.asarray(rpm, dtype=np.float64)
    f = rpm / 60.0 * cyl / 2.0
    body = hosc(f, nh=30, rolloff=1.1 + (1 - bright), loop=loop)
    sub = 0.45 * hosc(f * 0.5, nh=8, rolloff=0.8, loop=loop)
    pulse = 0.5 + 0.5 * np.sin(phase_of(f, loop) + 1.0)
    noise = (circ(lambda z: lp(z, 1200 + 3000 * bright, 2), white(n, r)) if loop else lp(white(n, r), 1200 + 3000 * bright, 2)) * (0.35 + 0.65 * pulse ** 2) * 0.6
    y = body + sub + noise
    y *= 1 + rough * 0.12 * drift(n, r, 9, loop)
    return y


def whine(dur, f_curve, r, harm=3, jitter=0.002, level=1.0):
    """electric motor / turbine: fundamental + a few harmonics, tiny jitter"""
    n = N(dur)
    f = np.asarray(f_curve, dtype=np.float64) * (1 + jitter * drift(n, r, 30, False))
    ph = phase_of(f)
    y = np.zeros(n)
    for k in range(1, harm + 1):
        y += np.sin(k * ph + k) / k ** 1.2
    return level * y


def siren_tone(f, n):
    """rotary air-raid siren timbre: saw-ish plus strong 2nd/3rd, chopped by the rotor"""
    ph = phase_of(f, loop=True)
    y = np.sin(ph) + 0.7 * np.sin(2 * ph + 0.3) + 0.55 * np.sin(3 * ph + 1.1) + 0.3 * np.sin(4 * ph) + 0.18 * np.sin(5 * ph)
    return y


# ------------------------------------------------------------------ blasts and debris
def debris(dur, r, rate=40, rate_end=1.0, lo=500, hi=6000, level=1.0, thuds=0.3):
    """falling debris: ringing ticks plus some low clods"""
    n = N(dur)
    y = ticks(dur, r, rate, lo, hi, 0.004, rate_end, shape=1.5, gains=(0.2, 1.0)) * level
    t = 0.0
    while t < dur:
        t += r.exponential(1.0 / max(rate * 0.12 * thuds + 0.2, 0.2) * (1 + 3 * t / dur))
        if t >= dur:
            break
        th = thump(0.25, r.uniform(110, 220), r.uniform(60, 90), 0.02, r.uniform(0.04, 0.12)) * r.uniform(0.2, 0.8) * level * 0.5
        place(y, th, t)
    return y


def blast(r, dur=10.0, size=1.0, sub=(70, 20), crack=1.0, dist=0.0, tail=1.0, bright=1.0, drive=1.6, debris_level=0.5):
    """Layered explosion: pressure crack, fireball body (noise through a falling lowpass), pitch-dropping sub,
    hill echoes, rolling rumble tail, falling debris.  dist (0..1) delays and dulls it."""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    nc = N(0.12)
    cr = hp(white(nc, r), 120, 1) * expo(nc, 0.012 * (1 + dist * 2), 0.0004)
    y[:nc] += crack * pk(cr, -2 - 8 * dist)
    body_n = N(min(dur, 4.0 * size + 1))
    fb = svf(white(body_n, r), 160 + 7000 * bright * np.exp(-T(body_n) / (0.28 * size)), 0.8, "lp") * expo(body_n, 0.45 * size, 0.0015)
    y[:body_n] += pk(fb, -2.5)
    y += 1.0 * fit(thump(min(dur, 3.5 * size + 1), sub[0], sub[1], 0.22 * size, 0.9 * size, 0.004, 0.35), n)
    y += pk(fit(thump(1.5 * size, 140, 55, 0.08, 0.35 * size, 0.002, 0.2), n), -5)
    rl = colored(n, r, -6.0, lo=20, hi=220 * bright + 60) * expo(n, 1.6 * size * tail, 0.05) * swell(n, r, 1.1, 0.8, False)
    y += pk(rl, -7)
    for d, g in [(0.45, .5), (0.95, .4), (1.7, .3), (2.6, .22), (3.6, .15)]:
        if d * size < dur - 1:
            m = N(1.4 * size)
            seg = colored(m, r, -6.0, lo=20, hi=180) * expo(m, 0.5 * size, 0.05) * (1 + 0.5 * drift(m, r, 2, False))
            place(y, pk(seg, -9), d * size, g * tail)
    deb = debris(dur, r, rate=90 * size, rate_end=1.5, level=1.0) * np.minimum(1, t / (0.35 * size))
    y += pk(deb, -16) * debris_level * 2
    y = sat(y * 0.6, drive) if drive else y
    if dist > 0:
        y = svf(y, 9000 * np.exp(-t / (1.5 + 3 * (1 - dist))) * (1 - dist) + 200 + 900 * (1 - dist), 0.6, "lp")
        d = N(dist * 0.9)
        y = np.concatenate([np.zeros(d), y])[:n]
    return y


def glass_shatter(r, dur=1.5, level=1.0):
    n = N(dur)
    y = np.zeros(n)
    y[:N(0.08)] += pk(hp(white(N(0.08), r), 1500, 1) * expo(N(0.08), 0.015), -3)
    y += 0.5 * ticks(dur, r, 400, 2500, 10000, 0.002, 10, 2.0, (0.2, 1.0)) / 3
    y[:N(0.4)] += pk(burst(0.4, r, 3000, 12000, 0.12), -9)
    return level * y


def metal_crash(dur, r, size=1.0, hits=18, f_lo=120, f_hi=1500):
    """a pile of metal falling over itself: many plate hits, thickest at the start"""
    n = N(dur)
    y = np.zeros(n)
    for i in range(hits):
        t0 = min(dur * 0.8, r.exponential(dur * 0.12 * size))
        f = np.exp(r.uniform(np.log(f_lo), np.log(f_hi)))
        p = plate(f, 1.2, r, tau=r.uniform(0.12, 0.45), nmodes=10, spread=12.0)
        place(y, pk(p, -3 * (1 + t0 / dur * 4)), t0, r.uniform(0.3, 1.0))
    return y

# ------------------------------------------------------------------ birds
def bird_chirp(r, f0, f1, dur, vib=0.0, harm=0.25):
    n = N(dur)
    t = T(n)
    f = f0 * (f1 / f0) ** (t / dur)
    if vib:
        f = f * (1 + vib * np.sin(TWO_PI * r.uniform(40, 90) * t))
    ph = phase_of(f)
    y = np.sin(ph) + harm * np.sin(2 * ph + 0.7)
    return y * np.sin(np.pi * t / dur) ** 1.4


def bird_phrase(r, kind=None):
    k = int(r.integers(0, 4)) if kind is None else kind
    parts = []
    if k == 0:                                                   # two-note whistle
        a = r.uniform(2400, 3600)
        parts = [(0.0, bird_chirp(r, a, a * 1.25, 0.16)), (0.22, bird_chirp(r, a * 1.3, a * 0.95, 0.2))]
    elif k == 1:                                                 # fast trill
        a = r.uniform(3500, 5200)
        parts = [(i * 0.045, bird_chirp(r, a, a * 1.12, 0.035, 0.0) * (0.7 + 0.3 * r.random())) for i in range(r.integers(5, 10))]
    elif k == 2:                                                 # rising sweep and a drop
        a = r.uniform(2000, 3000)
        parts = [(0.0, bird_chirp(r, a, a * 2.0, 0.12)), (0.18, bird_chirp(r, a * 1.8, a * 1.1, 0.1))]
    else:                                                        # tweet-tweet-tweet
        a = r.uniform(3000, 4200)
        parts = [(i * 0.13, bird_chirp(r, a * (1 + 0.05 * i), a * 1.35, 0.07, 0.01)) for i in range(r.integers(3, 6))]
    ln = max(t + len(s) / SR for t, s in parts)
    out = np.zeros(N(ln) + 8)
    for t0, s in parts:
        place(out, s, t0)
    return out


# ------------------------------------------------------------------ voices / pads (used by music and a few sfx)
VOWELS = {"ah": [(800, 1, 80), (1150, 0.5, 90), (2900, 0.2, 120)],
          "oh": [(500, 1, 70), (900, 0.4, 80), (2800, 0.1, 120)],
          "oo": [(320, 1, 60), (800, 0.15, 80), (2600, 0.05, 120)],
          "eh": [(600, 1, 80), (1800, 0.4, 90), (2700, 0.15, 120)]}


def choir(f, dur, r, vowel="ah", voices=5, detune=14, vib=0.0045, vib_rate=5.2, breath=0.02, lockdur=None, vowel2=None, mix=None):
    """Synthetic choir-like pad: several detuned, slightly vibrato'd harmonic voices through vowel formants.
    f: Hz (scalar or curve). vowel2 + mix (curve 0..1) morphs between two vowels. lockdur: make periodic."""
    n = N(dur)
    fc = np.broadcast_to(np.asarray(f, dtype=np.float64), (n,)).copy()
    outs = []
    for vname in ([vowel] + ([vowel2] if vowel2 else [])):
        acc = np.zeros(n)
        for v in range(voices):
            cents = r.uniform(-detune, detune)
            vr = lockf(vib_rate * r.uniform(0.85, 1.15), lockdur) if lockdur else vib_rate * r.uniform(0.85, 1.15)
            vibr = 1 + vib * np.sin(TWO_PI * vr * T(n) + r.uniform(0, 6.28))
            osc = hosc(fc * 2 ** (cents / 1200) * vibr, nh=40, rolloff=1.2, loop=bool(lockdur))
            fmt = np.zeros(n)
            for ff, g, bw in VOWELS[vname]:
                fmt += g * (csvf(osc, ff, ff / bw, "bp") if lockdur else svf(osc, ff, ff / bw, "bp"))
            acc += fmt
        acc += breath * (csvf(white(n, r), 1500, 1.0, "bp") if lockdur else svf(white(n, r), 1500, 1.0, "bp")) * 3
        outs.append(acc / voices)
    if vowel2:
        m = np.asarray(mix, dtype=np.float64)
        return outs[0] * (1 - m) + outs[1] * m
    return outs[0]


def brass(f, dur, r, bright=0.7, voices=3, detune=7, swell_t=0.25, scoop=0.03, release=0.1, vib=0.0):
    """Low-brass-like tone: detuned sawtooth stack through a lowpass that opens as the note swells, a pitch scoop and breath noise"""
    n = N(dur)
    t = T(n)
    fc = np.broadcast_to(np.asarray(f, dtype=np.float64), (n,)).copy()
    e = np.minimum(1, t / swell_t) ** 1.5 * np.minimum(1, np.maximum(0, (dur - t)) / release)
    sc = 1 - scoop * np.exp(-t / 0.05)
    y = np.zeros(n)
    for v in range(voices):
        cents = (v - (voices - 1) / 2) * detune * 2 / max(voices - 1, 1) if voices > 1 else 0.0
        y += hosc(fc * sc * 2 ** (cents / 1200) * (1 + vib * np.sin(TWO_PI * 5.5 * t)), nh=36, rolloff=1.0)
    y /= voices
    cut = fc * (1.5 + 9.0 * bright * e)
    y = svf(y, cut, 0.9, "lp")
    y += 0.03 * svf(white(n, r), cut, 1.2, "bp") * np.minimum(1, t / 0.05)
    return y * e


def pad(f, dur, r, voices=4, detune=9, cutoff=1.8, rolloff=1.2, attack=2.0, release=3.0, lockdur=None):
    """Plain warm/cold pad: detuned saw stack, lowpassed at cutoff * f, slow swell in and out"""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    for v in range(voices):
        cents = r.uniform(-detune, detune)
        ff = lockf(f * 2 ** (cents / 1200), lockdur) if lockdur else f * 2 ** (cents / 1200)
        y += hosc(np.full(n, ff), nh=30, rolloff=rolloff, loop=bool(lockdur))
    y = (csvf(y / voices, f * cutoff, 0.7, "lp") if lockdur else svf(y / voices, np.full(n, f * cutoff), 0.7, "lp"))
    if lockdur is None:
        y *= np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release)
    return y

