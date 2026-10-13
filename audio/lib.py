"""lib.py - Godzilla-specific building blocks on top of dsp.py / blocks.py: vocal tract, monster voice pieces, water,
masonry, drums and brass for the score, notes.  Everything returns float64 mono unless stated."""
import numpy as np
from dsp import *
from blocks import *

_NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8,
         "A": 9, "A#": 10, "Bb": 10, "B": 11}


def hz(name):
    """'E2' -> Hz"""
    m = 12 * (int(name[-1]) + 1) + _NOTE[name[:-1]]
    return 440.0 * 2 ** ((m - 69) / 12.0)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


# ------------------------------------------------------------------ vocal tract
VOWEL_F = {"ah": (800, 1200, 2800), "oh": (500, 900, 2600), "oo": (320, 800, 2500), "ee": (300, 2300, 3000),
           "eh": (600, 1800, 2600), "aa": (700, 1600, 2600), "uh": (550, 1100, 2500), "ay": (450, 2000, 2800),
           "ow": (650, 1000, 2400)}


def vcurve(n, keys, scale=1.0):
    """keys [(t_s, vowel name or (F1,F2,F3)), ...] -> three formant curves (Hz) over n samples.  scale<1 = bigger throat."""
    ts = [k[0] for k in keys]
    F = [VOWEL_F[k[1]] if isinstance(k[1], str) else k[1] for k in keys]
    t = T(n)
    return [np.interp(t, ts, [f[i] * scale for f in F]) for i in range(3)]


def formant_bank(x, fcs, qs=(5.0, 7.0, 9.0), gains=(1.0, 0.7, 0.35), dry=0.0):
    y = np.zeros(len(x))
    for fc, q, g in zip(fcs, qs, gains):
        y += g * svf(x, fc, q, "bp")
    return y + dry * x


def contour(n, keys, smooth=6.0, kind="exp"):
    """pitch / parameter contour from [(t, value), ...] (geometric interpolation), low-passed at `smooth` Hz"""
    ts = [k[0] for k in keys]
    vs = np.array([k[1] for k in keys], dtype=float)
    t = T(n)
    c = np.exp(np.interp(t, ts, np.log(vs))) if kind == "exp" else np.interp(t, ts, vs)
    if smooth:
        sos = sg.butter(2, smooth / (SR / 2), "low", output="sos")
        c = sg.sosfiltfilt(sos, c, padtype="odd")
    return c


def glottal(f, r, jitter=0.01, subh=None, nh=40, rolloff=1.2, even=1.0):
    """harmonic voice source with random pitch jitter; subh: curve 0..1 of a sub-octave component (period doubling = growl)"""
    n = len(f)
    fj = f * (1 + jitter * drift(n, r, 90, False))
    y = hosc(fj, nh=nh, rolloff=rolloff, even=even)
    if subh is not None:
        y = y + subh * hosc(fj * 0.5, nh=max(8, nh // 2), rolloff=rolloff)
    return y


def fry_gate(n, r, rate, depth=0.6, wander=0.25):
    """irregular amplitude chopping at `rate` Hz (scalar or curve): the rattle of a growl"""
    rate = np.broadcast_to(np.asarray(rate, dtype=float), (n,)) * (1 + wander * drift(n, r, 14, False))
    g = 0.5 + 0.5 * np.sin(phase_of(rate) + 0.8 * drift(n, r, 30, False))
    return 1 - depth * g ** 1.5


def ctl(n, keys, kind="lin"):
    return env(keys, n, kind)


# ------------------------------------------------------------------ water
def bubble(r, f=None, dur=0.05):
    f = f or r.uniform(250, 1800)
    n = N(dur)
    t = T(n)
    fr = f * (1 + 1.6 * t / dur)
    return np.sin(phase_of(fr)) * np.exp(-t / (dur * 0.28)) * np.minimum(1, t / 0.002)


def bubbles(dur, r, rate, lo=250, hi=1800, rate_end=None, level=1.0):
    n = N(dur)
    y = np.zeros(n)
    rate_end = rate if rate_end is None else rate_end
    t = 0.0
    while t < dur:
        t += r.exponential(1.0 / max(np.interp(t, [0, dur], [rate, rate_end]), 0.01))
        if t >= dur:
            break
        place(y, bubble(r, np.exp(r.uniform(np.log(lo), np.log(hi))), r.uniform(0.02, 0.08)) * r.uniform(0.2, 1.0), t)
    return level * y


def splash(dur, r, size=1.0, bright=1.0):
    """water impact: a slapping crack, a bright falling spray and a boil of bubbles"""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    sl = N(0.05)
    y[:sl] += hp(white(sl, r), 300, 1) * expo(sl, 0.012 * size, 0.0005)
    spray = svf(white(n, r), 700 + 7000 * bright * np.exp(-t / (0.35 * size)), 0.9, "bp") * expo(n, 0.5 * size, 0.003)
    y += 0.8 * spray / (np.std(spray) + 1e-9) * 0.3
    body = svf(white(n, r), 160 + 600 * np.exp(-t / (0.2 * size)), 0.8, "lp") * expo(n, 0.35 * size, 0.004)
    y += 1.0 * body / (np.std(body) + 1e-9) * 0.35
    y += 0.5 * bubbles(dur, r, 60 * size, 200, 1200, 4) * np.minimum(1, t / 0.05) * expo(n, 1.2 * size)
    return y


def pour(dur, r, level=1.0, density=1.0, lo=300, hi=7000, fade_in=0.2, fade_out=0.4):
    """water streaming off a body: hissing noise with irregular gushes and trickle plops"""
    n = N(dur)
    t = T(n)
    x = bp(white(n, r), lo, hi, 2)
    gush = np.clip(0.55 + 0.6 * drift(n, r, 9 * density, False), 0.05, 1.8) ** 1.5
    y = x * gush
    y += 0.7 * lp(white(n, r), 260, 2) * np.clip(0.5 + 0.6 * drift(n, r, 3, False), 0.1, 1.6)
    y += 0.6 * bubbles(dur, r, 55 * density, 300, 2400, 15 * density) * 2.5
    e = np.minimum(1, t / fade_in) * np.minimum(1, np.maximum(0, dur - t) / fade_out)
    return level * y * e


# ------------------------------------------------------------------ masonry, glass, steel
def stone_tick(r, f=None, tau=0.012, dur=0.12):
    """a chip of stone or brick hitting something: a few damped partials"""
    f = f or np.exp(r.uniform(np.log(700), np.log(4200)))
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    for k, a in zip([1.0, 1.52, 2.31, 3.1], [1.0, 0.6, 0.4, 0.2]):
        y += a * np.sin(TWO_PI * f * k * t + r.uniform(0, 6.28)) * np.exp(-t / (tau / (k ** 0.5)))
    y[:N(0.003)] += 0.5 * white(N(0.003), r)
    return y


def stone_rain(dur, r, rate, rate_end=1.0, lo=600, hi=4500, shape=1.4, level=1.0, big=0.25):
    """falling masonry: Poisson stone ticks, some heavy clods"""
    n = N(dur)
    y = np.zeros(n)
    t = 0.0
    while True:
        rr = rate_end + (rate - rate_end) * max(0.0, 1 - t / dur) ** shape
        t += r.exponential(1.0 / max(rr, 0.3))
        if t >= dur:
            break
        if r.random() < big:
            place(y, thump(0.25, r.uniform(120, 260), r.uniform(60, 100), 0.02, r.uniform(0.04, 0.14)) * r.uniform(0.3, 0.9), t)
            place(y, stone_tick(r, r.uniform(500, 1500), 0.02, 0.15) * r.uniform(0.2, 0.6), t)
        else:
            place(y, stone_tick(r, np.exp(r.uniform(np.log(lo), np.log(hi)))) * r.uniform(0.1, 1.0), t)
    return level * y


def dust(dur, r, level=1.0, lo=700, hi=5000, tau=1.2):
    n = N(dur)
    return level * bp(white(n, r), lo, hi, 2) * expo(n, tau, 0.05) * np.clip(0.8 + 0.5 * drift(n, r, 6, False), 0.1, 1.5)


def glass_pane(r, dur=1.2, level=1.0, size=1.0):
    """window / curtain-wall glass breaking: sharp crack, bright shards, a long tinkle of falling pieces"""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    c = N(0.06)
    y[:c] += 1.0 * hp(white(c, r), 1800, 2) * expo(c, 0.012, 0.0003)
    sh = svf(white(n, r), 6000 * np.exp(-t / 0.4) + 2500, 1.0, "bp") * expo(n, 0.22 * size, 0.001)
    y += 0.8 * sh / (np.std(sh) + 1e-9) * 0.25
    tt = 0.0
    while tt < dur * 0.9:
        tt += r.exponential(0.012 + 0.09 * (tt / dur) ** 1.2)
        f = r.uniform(2600, 8500)
        g = glass_ring(f, 0.25, r, tau=r.uniform(0.02, 0.08))
        place(y, g * r.uniform(0.15, 0.8) * (1 - 0.7 * tt / dur), tt)
    return level * y


# ------------------------------------------------------------------ percussion and brass for the score and the stings
def taiko(r, pitch=62.0, dur=1.6, tau=0.55, slap=1.0, drop=1.9, shell=0.35):
    """big drum: a pitch-dropping skin note, membrane modes, a stick slap and a little shell ring"""
    n = N(dur)
    t = T(n)
    f = pitch * (1 + (drop - 1) * np.exp(-t / 0.035))
    ph = phase_of(f)
    y = np.sin(ph) * np.exp(-t / tau) + 0.45 * np.sin(2 * ph + 0.5) * np.exp(-t / (tau * 0.5)) + 0.2 * np.sin(3.1 * ph) * np.exp(-t / (tau * 0.3))
    for k, a in zip(MEMBRANE[1:5], [0.35, 0.28, 0.2, 0.14]):
        y += a * np.sin(phase_of(pitch * 1.4 * k * (1 + 0.5 * (drop - 1) * np.exp(-t / 0.03))) + r.uniform(0, 6.28)) * np.exp(-t / (tau * 0.28 / k ** 0.5))
    y = y * np.minimum(1, t / 0.0015)
    k = N(0.03)
    y[:k] += slap * 0.9 * bp(white(k, r), 700, 6000, 1) * expo(k, 0.006, 0.0002)
    y += shell * 0.1 * modal([pitch * 3.7, pitch * 5.9, pitch * 8.4], [0.22, 0.14, 0.1], [1, 0.6, 0.4], dur, r)
    return sat(endfade(y), 1.4)


def snare_drum(r, tune=190.0, snap=1.0, tail=0.16):
    n = N(0.35)
    t = T(n)
    body = np.sin(phase_of(tune * (1 + 0.5 * np.exp(-t / 0.012)))) * np.exp(-t / 0.06) + 0.4 * np.sin(phase_of(tune * 1.8)) * np.exp(-t / 0.04)
    nz = bp(white(n, r), 1800, 9000, 2) * (np.exp(-t / tail) * 0.7 + np.exp(-t / 0.012) * 0.9) * snap
    return endfade(body * 0.8 + nz * 0.9)


def crash(r, dur=3.2, tau=1.0, bright=1.0):
    n = N(dur)
    t = T(n)
    x = hp(white(n, r), 2500 / bright, 2)
    y = x * (np.exp(-t / tau) * 0.8 + np.exp(-t / 0.08) * 0.8) * np.minimum(1, t / 0.002)
    y += 0.5 * modal([420 * k for k in (1, 1.52, 2.1, 2.9, 3.6, 4.3, 5.6)], [tau * 0.7] * 7, [1, .8, .7, .6, .5, .4, .3], dur, r, detune=40) * 0.3
    return endfade(y)


def reverse_swell(x, curve=1.0):
    y = x[::-1].copy()
    return y * np.linspace(0.05, 1.0, len(y)) ** curve


def lowbrass(f, dur, vel=1.0, bright=0.8, voices=4, detune=10, attack=0.05, release=0.12, scoop=0.025, r=None):
    """ensemble low brass (tubas / trombones / horns): a detuned saw stack through a lowpass that opens with the note,
    a bell-like resonance around 600-900 Hz, breath noise and a little pitch scoop.  Deterministic when r is None."""
    n = N(dur)
    t = T(n)
    e = np.minimum(1, t / attack) ** 1.3 * np.minimum(1, np.maximum(0, dur - t) / release)
    sc = 1 - scoop * np.exp(-t / 0.045)
    vib = 1 + 0.004 * np.minimum(1, t / 0.4) * np.sin(TWO_PI * 5.3 * t)
    y = np.zeros(n)
    for v in range(voices):
        cents = (v - (voices - 1) / 2) * detune * 2 / max(voices - 1, 1)
        y += hosc(f * sc * vib * 2 ** (cents / 1200.0), n, nh=34, rolloff=0.95)
    y /= voices
    cut = f * (1.8 + 11.0 * bright * vel * (0.35 + 0.65 * np.minimum(1, t / (attack * 2.0))))
    y = svf(y, cut, 0.8, "lp")
    y = y + 0.5 * svf(y, 750, 2.0, "bp")
    rr = r if r is not None else np.random.default_rng(int(f * 10) % 10007)
    y += 0.03 * svf(white(n, rr), np.minimum(cut, 6000), 1.2, "bp") * np.minimum(1, t / 0.04)
    return y * e * (0.55 + 0.45 * vel)


def stab(f, dur, vel=1.0, bright=1.0):
    """short punchy brass hit (blat)"""
    return lowbrass(f, dur, vel, bright, voices=5, detune=14, attack=0.012, release=0.06, scoop=0.05)


def sub_bass(f, dur, vel=1.0, drive=1.8, attack=0.01, release=0.05):
    n = N(dur)
    t = T(n)
    ph = phase_of(np.full(n, f))
    y = np.sin(ph) + 0.35 * np.sin(2 * ph + 0.3) + 0.12 * np.sin(3 * ph)
    e = np.minimum(1, t / attack) * np.minimum(1, np.maximum(0, dur - t) / release)
    return sat(y * e * vel, drive)


def pluck_saw(f, dur, bright=0.6, tau=0.12):
    n = N(dur)
    t = T(n)
    y = hosc(np.full(n, f), n, nh=22, rolloff=1.0)
    y = svf(y, f * (1.5 + 10 * bright * np.exp(-t / 0.04)), 1.0, "lp")
    return y * np.exp(-t / tau) * np.minimum(1, t / 0.002) * np.minimum(1, np.maximum(0, dur - t) / 0.01)


def bell_tone(f, dur, tau=2.0, bright=1.0, r=None):
    rr = r if r is not None else np.random.default_rng(int(f) % 9973)
    return bell(f, dur, rr, tau=tau, bright=bright)


def shimmer_note(f, dur, r=None, tau=0.5):
    """glassy FM chime used for Mothra's scales and the arcade stings"""
    n = N(dur)
    t = T(n)
    idx = 2.4 * np.exp(-t / 0.08)
    y = np.sin(phase_of(np.full(n, f)) + idx * np.sin(phase_of(np.full(n, f * 3.5))))
    y += 0.4 * np.sin(phase_of(np.full(n, f * 2.0)))
    return y * np.exp(-t / tau) * np.minimum(1, t / 0.001)


def deep(dur, f0, f1, tau_f=0.07, tau_a=0.22, harm=0.6, hpf=30):
    """a sub-bass hit that does not hang about: pitch-dropping thump, harmonics for small speakers, no content below hpf"""
    return hp(thump(dur, f0, f1, tau_f, tau_a, 0.003, harm), hpf, 2)


def weight(y, sub=1.0, mid=1.0):
    """tone for headset speakers: the 20-60 Hz that only a subwoofer reproduces is pulled down and 100-1500 Hz, where the thump
    can actually be heard, is lifted; sub/mid scale those moves"""
    return eq(y, [(20, -18 * sub), (40, -11 * sub), (70, -5 * sub), (120, 0), (250, 1.5 * mid), (800, 2.5 * mid), (3000, 1.0 * mid), (8000, -3), (14000, -9)], circ=False)


# ------------------------------------------------------------------ misc
def trim_to(x, seconds):
    return fit(x, N(seconds))


def whoosh_pass(dur, r, f0, f1, peak_t, q=1.0, width=0.35, floor=0.0, lowcut=60.0):
    """noise through a sweeping resonant band with a bell-shaped level peaking at peak_t (a swoosh)"""
    n = N(dur)
    t = T(n)
    f = np.exp(np.interp(t, [0, peak_t, dur], [np.log(f0), np.log((f0 * f1) ** 0.5), np.log(f1)]))
    y = svf(white(n, r), f, q, "bp")
    e = np.exp(-0.5 * ((t - peak_t) / (width * dur)) ** 2) * (1 - floor) + floor
    return hp(y * e, lowcut, 1)


def rattle_modes(r, base, count, dur, tau=0.5, lo=1.0, hi=4.0):
    """a bank of low modes excited at once: ground / building rattle"""
    fr = base * np.exp(r.uniform(np.log(lo), np.log(hi), count))
    return modal(fr, [tau * r.uniform(0.5, 1.3) for _ in fr], [r.uniform(0.3, 1.0) for _ in fr], dur, r, detune=10)


def stereo_from(x, r, width=0.5):
    return widen(x, r, width)


def loop_fix(y, fade_ms=3.0):
    """for loops built non-periodically: nothing is done here - use periodic constructions instead"""
    return y
