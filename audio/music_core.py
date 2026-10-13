"""music_core.py - a small score engine for the Godzilla music: a tempo grid, a stereo mix bus with a reverb send that can
wrap round the end (seamless loops), cached instruments and the drum kit.

Everything is original and synthesised; nothing quotes any existing piece.
"""
import zlib
from lib import *

_CACHE = {}


def rng_for(*key):
    return np.random.default_rng(zlib.crc32(repr(key).encode()) & 0xFFFFFFFF)


def cached(key, fn):
    if key not in _CACHE:
        _CACHE[key] = fn()
    return _CACHE[key]


class Grid:
    """bpm, 4/4, 16 steps per bar"""

    def __init__(self, bpm, bars):
        self.bpm, self.bars = bpm, bars
        self.beat = 60.0 / bpm
        self.step = self.beat / 4.0
        self.bar = self.beat * 4.0
        self.dur = self.bar * bars

    def t(self, bar, step=0.0):
        return bar * self.bar + step * self.step


class Mix:
    """stereo dry bus + reverb send.  loop=True wraps events and uses a circular reverb"""

    def __init__(self, dur, loop=False):
        self.dur, self.loop = dur, loop
        self.n = N(dur)
        self.dry = np.zeros((self.n, 2))
        self.send = np.zeros((self.n, 2))
        self.energy = {}

    def add(self, sig, t, gain=1.0, pan=0.0, send=0.25, label=None):
        if label is not None:
            self.energy[label] = self.energy.get(label, 0.0) + float(np.sum(np.asarray(sig) ** 2)) * gain * gain
        place(self.dry, sig, t, gain, pan, wrap=self.loop)
        if send:
            place(self.send, sig, t, gain * send, pan, wrap=self.loop)

    def full(self, sig, gain=1.0, send=0.25, label=None):
        sig = fit(sig, self.n)
        if label is not None:
            self.energy[label] = self.energy.get(label, 0.0) + float(np.sum(sig ** 2)) * gain * gain
        if sig.ndim == 1:
            sig = np.stack([sig, sig], axis=1)
        self.dry += gain * sig
        if send:
            self.send += gain * send * sig

    def stats(self):
        tot = sum(self.energy.values()) or 1.0
        return {k: round(10 * np.log10(v / tot + 1e-12), 1) for k, v in sorted(self.energy.items(), key=lambda kv: -kv[1])}

    def render(self, space_name="hall", wet=1.0, send_low=(120, 280)):
        ir = space(space_name)
        send = eq(self.send, [(send_low[0] * 0.5, -40), (send_low[0], -18), (send_low[1], -3), (send_low[1] * 2, 0)], circ=self.loop)   # keep the lows out of the reverb
        w = cconv(send, ir) if self.loop else reverb(send, ir, wet=1.0, dry=0.0, n=self.n)
        return self.dry + wet * w


def finish(y, target_lufs=None, gain_db=None, glue=1.0, eq_pts=None):
    """tone, then a loudness target or a fixed gain, then a memoryless soft clip (glue; keeps loops seamless).
    Returns (audio, gain_db) so a second file can be given exactly the same gain."""
    y = eq(y, eq_pts or [(25, -14), (40, -7), (70, -3), (110, 0), (2000, 1.0), (6000, 0.5), (10000, -4), (15000, -12)])
    if gain_db is None:
        gain_db = (target_lufs - lufs(y)) if target_lufs is not None else 0.0
    y = y * db(gain_db)
    if glue:
        y = np.tanh(y * glue) / glue
    return y, gain_db

# ------------------------------------------------------------------ notes
def mf(m):
    return midi_hz(m)


def gate(x, dur_s, release=0.03):
    n = N(dur_s)
    x = fit(x, n)
    k = min(n, N(release))
    x[-k:] *= np.linspace(1, 0, k)
    return x


# ------------------------------------------------------------------ instruments (cached, deterministic)
DRUMS = {
    "odaiko": (46.0, 1.8, 0.6, 1.6),
    "nagado": (66.0, 1.2, 0.38, 1.2),
    "tom": (92.0, 1.0, 0.32, 1.0),
    "tomh": (135.0, 0.7, 0.22, 0.9),
    "tomhh": (190.0, 0.5, 0.15, 0.8),
}


def drum(kind, vel=1.0):
    q = round(vel * 4) / 4
    def mk():
        p, d, tau, sl = DRUMS[kind]
        return taiko(rng_for("taiko", kind), p, d, tau, sl * (0.5 + 0.5 * q)) * (0.35 + 0.65 * q)
    return cached(("drum", kind, q), mk)


def war_snare(vel=1.0):
    q = round(vel * 4) / 4
    def mk():
        r = rng_for("snare")
        s = snare_drum(r, 170.0, 1.0, 0.2)
        s = s + 0.6 * np.pad(deep(0.2, 150, 90, 0.02, 0.05, 0.3), (0, max(0, len(s) - N(0.2))))[:len(s)]
        return s * (0.4 + 0.6 * q)
    return cached(("snare", q), mk)


def rim(vel=1.0):
    return cached(("rim", round(vel * 2) / 2), lambda: 0.7 * pk(modal([420, 1300, 2700], [0.025, 0.015, 0.01], [1, 0.7, 0.5], 0.12, rng_for("rim")), 0) * vel)


def hat(vel=1.0, open_=False):
    return cached(("hat", open_, round(vel * 2) / 2), lambda: pk(hp(white(N(0.25), rng_for("hat", open_)), 7500, 2) * expo(N(0.25), 0.12 if open_ else 0.02, 0.0003), 0) * vel)


def shaker(vel=1.0):
    return cached(("shaker", round(vel * 2) / 2), lambda: pk(bp(white(N(0.12), rng_for("shk")), 5000, 11000, 2) * np.sin(np.pi * np.linspace(0, 1, N(0.12))) ** 1.5, 0) * vel)


def crash_cymbal(size=1.0):
    return cached(("crash", size), lambda: crash(rng_for("crash", size), 3.6 * size, 1.1 * size, 1.0))


def brass(f, dur_s, vel=1.0, bright=0.8, voices=4, attack=0.05, release=0.12):
    key = ("brass", round(f, 1), round(dur_s, 3), round(vel * 2) / 2, bright, voices, attack)
    return cached(key, lambda: lowbrass(f, dur_s, round(vel * 2) / 2, bright, voices, 10, attack, release))


def blat(f, dur_s, vel=1.0, bright=1.0):
    return cached(("blat", round(f, 1), round(dur_s, 3), round(vel * 2) / 2, bright), lambda: stab(f, dur_s, round(vel * 2) / 2, bright))


def bass(f, dur_s, vel=1.0):
    return cached(("bass", round(f, 1), round(dur_s, 3)), lambda: 0.7 * sub_bass(f, dur_s, 1.0, 1.9))


def pluck(f, dur_s, bright=0.6, tau=0.1):
    return cached(("pluck", round(f, 1), round(dur_s, 3), bright, tau), lambda: pluck_saw(f, dur_s, bright, tau))


def bellnote(f, dur_s, tau=2.0, bright=0.7):
    return cached(("bell", round(f, 1), round(dur_s, 2), tau, bright), lambda: bell_tone(f, dur_s, tau, bright))


def glass(f, dur_s, tau=0.5):
    return cached(("glass", round(f, 1), round(dur_s, 2), tau), lambda: shimmer_note(f, dur_s, None, tau))


def mallet(f, dur_s=0.4, tau=0.15, bright=1.0):
    def mk():
        n = N(dur_s)
        t = T(n)
        idx = bright * 2.2 * np.exp(-t / 0.04)
        y = np.sin(phase_of(np.full(n, f)) + idx * np.sin(phase_of(np.full(n, f * 3.0))))
        y = y * np.exp(-t / tau) + 0.3 * np.sin(TWO_PI * f * t) * np.exp(-t / (tau * 1.6))
        return endfade(y)
    return cached(("mallet", round(f, 1), dur_s, tau, bright), mk)


def pad_voice(f, dur_s, attack=1.2, release=1.5, cutoff=1.8, voices=3, detune=7):
    return pad(f, dur_s, rng_for("pad", round(f)), voices=voices, detune=detune, cutoff=cutoff, rolloff=1.3, attack=attack, release=release)


def choir_note(f, dur_s, vowel="ah", vowel2="oh", voices=3, attack=0.6, release=0.8):
    def mk():
        n = N(dur_s)
        t = T(n)
        e = np.minimum(1, t / attack) * np.minimum(1, np.maximum(0, dur_s - t) / release)
        c = choir(f, dur_s, rng_for("choir", round(f)), vowel, voices, 14, 0.005, 5.2, 0.02, None, vowel2, np.interp(t, [0, dur_s], [0.0, 0.7]))
        return c * e
    return cached(("choir", round(f, 1), round(dur_s, 2), vowel, voices), mk)


def swell_noise(dur_s, lo=300, hi=6000, up=True, level=1.0, curve=2.0, seed="sw"):
    r = rng_for("swn", dur_s, lo, hi, seed)
    n = N(dur_s)
    x = svf(white(n, r), np.exp(np.linspace(np.log(lo), np.log(hi), n)) if up else np.exp(np.linspace(np.log(hi), np.log(lo), n)), 1.0, "bp")
    e = np.linspace(0, 1, n) ** curve if up else np.linspace(1, 0, n) ** curve
    return level * x / (np.std(x) + 1e-9) * e


def riser_tone(dur_s, f0, f1, level=1.0):
    n = N(dur_s)
    t = T(n)
    f = np.exp(np.linspace(np.log(f0), np.log(f1), n))
    ph = phase_of(f)
    y = np.sin(ph) + 0.5 * np.sin(2 * ph + 0.3) + 0.25 * np.sin(3 * ph)
    return level * y * np.linspace(0, 1, n) ** 2


def sub_boom(dur_s=3.0, f0=70.0, f1=24.0, tau=1.0):
    return cached(("boom", dur_s, f0, f1, tau), lambda: deep(dur_s, f0, f1, 0.2, tau, 0.6, 24))
