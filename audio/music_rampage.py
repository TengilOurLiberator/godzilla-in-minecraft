"""music_rampage.py - music.rampage and music.rampage_intense: the monster march.

100 bpm, 4/4, E minor (phrygian colour), 40 bars = 96.0 s = exactly 1920 game ticks; both files have the same length, tempo
and key and share every base layer bit-for-bit (the intense one adds layers on top and takes the same gain), so the game can
crossfade between them at any bar line.  They loop: the last bar leads into the downbeat of bar 1.

    bars  1- 8  A   the stomp: taiko on 1 and 3, war snare on 2 and 4, a low-brass riff in E over a sub bass
    bars  9-16  A2  the horn theme over the riff, then the riff moves to C and D
    bars 17-24  B   chord movement C - D - Em - B under a long-note horn melody
    bars 25-32  C   breakdown: heartbeat taiko, a tritone dread, half-time riff, chromatic climb and a snare roll
    bars 33-40  A3  the big return, the theme again, turnaround D - B and a fill into bar 1

The riff and the horn theme are written here for this game; they do not quote any existing piece.
"""
import os
from music_core import *
from reg import event

G = Grid(100, 40)
EMID = 40                                               # E2
ROOTS = [0] * 8 + [0, 0, 0, 0, 8, 8, 10, 10] + [8, 8, 10, 10, 0, 0, 7, 7] + [0, 0, 0, 0, 1, 1, 7, 7] + [0, 0, 0, 0, 8, 8, 10, 7]
MAJOR = {8, 10, 7, 1}
RIFF_A = [(0, 0, 2), (3, 0, 1), (4, 0, 2), (6, 3, 2), (8, 0, 2), (11, 0, 1), (12, 1, 2), (14, 0, 2)]
RIFF_B = [(0, 0, 2), (3, 0, 1), (4, 0, 2), (6, 5, 2), (8, 3, 2), (10, 1, 2), (12, 0, 4)]
TURN = [(0, 0, 2), (3, 0, 1), (4, 0, 2), (6, 0, 2), (8, 0, 2), (10, 0, 2)]
THEME = [[(0, 0, 3), (4, 3, 1), (5, 7, 3), (8, 12, 4), (12, 10, 2), (14, 8, 2)],
         [(0, 7, 3), (4, 8, 1), (5, 7, 2), (7, 5, 1), (8, 3, 4), (12, 1, 2), (14, 0, 2)]]        # semitones above E4
MEL_B = {16: [(0, 67, 8), (8, 76, 8)], 17: [(0, 74, 12), (12, 72, 4)], 18: [(0, 69, 8), (8, 74, 8)], 19: [(0, 72, 12), (12, 71, 4)],
         20: [(0, 71, 6), (6, 76, 2), (8, 79, 8)], 21: [(0, 78, 4), (4, 76, 4), (8, 71, 8)], 22: [(0, 75, 8), (8, 78, 8)], 23: [(0, 76, 12), (12, 71, 4)]}


def sec(b):
    return "A" if b < 8 else "A2" if b < 16 else "B" if b < 24 else "C" if b < 32 else "A3"


def rootm(b):
    r = ROOTS[b]
    return EMID + (r if r <= 5 else r - 12)


def is_major(b):
    return ROOTS[b] in MAJOR


def riff(b):
    pat = TURN if b % 8 == 7 else (RIFF_A if b % 2 == 0 else RIFF_B)
    maj = is_major(b)
    conv = {0: 0, 1: (-1 if maj else 1), 3: (4 if maj else 3), 5: 5}
    return [(s, conv[i], l) for s, i, l in pat]


def at(b, step):
    return G.t(b, step)


# ------------------------------------------------------------------ base layers
def drums(m):
    st = G.step
    for b in range(G.bars):
        s = sec(b)
        last = b % 8 == 7

        def hit(kind, step, vel, pan=0.0, send=0.3):
            m.add(drum(kind, vel), at(b, step), 0.62, pan, send, "taiko")

        if s != "C":
            hit("odaiko", 0, 1.0)
            hit("odaiko", 8, 0.85 if s != "B" else 0.95)
            hit("nagado", 6, 0.5, -0.25)
            hit("nagado", 14, 0.55, 0.25)
            if s == "B":
                hit("nagado", 4, 0.55, -0.2)
                hit("nagado", 12, 0.6, 0.2)
            if not last:
                m.add(war_snare(0.9), at(b, 4), 0.55, 0.0, 0.5, "snare")
                m.add(war_snare(1.0), at(b, 12), 0.55, 0.0, 0.5, "snare")
            else:
                m.add(war_snare(0.9), at(b, 4), 0.55, 0.0, 0.5, "snare")
                for k, kind in enumerate(["tomh", "tom", "tom", "nagado"]):
                    hit(kind, 12 + k, 0.7 + 0.1 * k, -0.4 + 0.27 * k)
        else:
            if b < 28:
                v = 0.45 + 0.08 * (b - 24)
                for q in range(4):
                    hit("odaiko", q * 4, v + 0.05 * (q == 0), 0.0, 0.5)
            elif b < 30:
                hit("odaiko", 0, 0.9)
                hit("nagado", 8, 0.8)
                hit("nagado", 12, 0.6, 0.2)
                m.add(war_snare(0.7), at(b, 4), 0.5, 0.0, 0.5, "snare")
            elif b == 30:
                hit("odaiko", 0, 0.9)
                for k in range(8):
                    m.add(war_snare(0.3 + 0.07 * k), at(b, 8 + k), 0.5, 0.0, 0.5, "snare")
                for k in range(4):
                    hit("nagado", 8 + 2 * k, 0.5 + 0.1 * k, 0.0)
            else:
                hit("odaiko", 0, 1.0)
                for k in range(16):
                    m.add(war_snare(0.4 + 0.04 * k), at(b, k), 0.5, 0.0, 0.45, "snare")
                    if k % 4 == 3:
                        hit("tom" if k < 12 else "tomh", k, 0.8)
        if b in (0, 16, 32):
            m.add(crash_cymbal(1.0), at(b, 0), 0.55, 0.0, 0.5, "crash")
            hit("odaiko", 0, 1.0, 0.0, 0.5)
        elif b == 8:
            m.add(crash_cymbal(0.8), at(b, 0), 0.4, 0.0, 0.5, "crash")
    m.add(reverse_swell(crash_cymbal(1.0), 1.4), G.t(32) - 3.6, 0.35, 0.0, 0.5, "crash")


def lowend(m):
    """sub bass and the low-brass riff"""
    st = G.step
    for b in range(G.bars):
        s = sec(b)
        r0 = rootm(b)
        if s == "C" and b < 28:
            d = 2 * G.bar * 0.99
            if b % 2 == 0:
                m.add(brass(mf(r0), d, 0.9, 0.9, 4, 1.4, 0.6), at(b, 0), 0.5, 0.0, 0.4, "brass")
                m.add(bass(mf(r0 - 12), d, 1.0), at(b, 0), 0.55, 0.0, 0.1, "sub")
                if b == 26:
                    m.add(brass(mf(r0 + 1), d, 0.8, 0.8, 4, 1.4, 0.6), at(b, 0), 0.35, 0.3, 0.4, "brass")
            continue
        if s == "C" and b < 30:
            for sp, n_, l in [(0, 0, 6), (8, 0, 6), (14, 1, 2)]:
                m.add(brass(mf(r0 + n_), l * st * 0.9, 1.0, 0.8), at(b, sp), 0.6, 0.0, 0.3, "brass")
                m.add(bass(mf(r0 - 12 + n_), l * st * 0.9), at(b, sp), 0.55, 0.0, 0.1, "sub")
            continue
        if s == "C":
            line = [0, 1, 2, 3] if b == 30 else [4, 5, 6, 7]
            for q, off in enumerate(line):
                v = 0.7 + 0.08 * (q + (4 if b == 31 else 0)) / 2
                m.add(brass(mf(EMID + off), 3 * st, min(1.0, v), 0.9), at(b, q * 4), 0.6, 0.0, 0.3, "brass")
                m.add(bass(mf(EMID - 12 + off), 3 * st), at(b, q * 4), 0.5, 0.0, 0.1, "sub")
            continue
        for sp, iv, l in riff(b):
            f = mf(r0 + iv)
            d = l * st * 0.88
            m.add(brass(f, d, 1.0 if sp % 4 == 0 else 0.85, 0.8), at(b, sp), 0.62, -0.1, 0.3, "brass")
            m.add(brass(f * 2, d, 0.7, 0.6, 3), at(b, sp), 0.18, 0.25, 0.3, "horns")
            sf = mf(r0 + iv - 12 if r0 + iv - 12 >= 27 else r0 + iv)
            m.add(bass(sf, d), at(b, sp), 0.5, 0.0, 0.1, "sub")


def pads(m):
    for b in range(0, G.bars, 2):
        r0 = rootm(b)
        third = 4 if is_major(b) else 3
        gain = 0.1 if sec(b) != "C" else 0.16
        for iv, g in [(0, 1.0), (7, 0.8), (12 + third, 0.55)]:
            p = pad_voice(mf(r0 + iv), 2 * G.bar + 1.0, 1.0, 1.6, 1.8)
            m.add(widen(p, rng_for("padw", b, iv), 0.6), at(b, 0) - 0.3, gain * g, 0.0, 0.5, "pad")


def melody(m):
    for b in range(G.bars):
        s = sec(b)
        if s in ("A2", "A3") and b in (8, 9, 10, 11, 32, 33, 34, 35):
            phrase = THEME[b % 2]
            for sp, iv, l in phrase:
                f = mf(64 + iv)
                m.add(brass(f, l * G.step * 0.95, 1.0, 1.0, 4, 0.04, 0.1), at(b, sp), 0.62, 0.15, 0.4, "melody")
        if b in MEL_B:
            for sp, midi, l in MEL_B[b]:
                m.add(brass(mf(midi), l * G.step * 0.97, 0.9, 0.9, 4, 0.1, 0.2), at(b, sp), 0.5, 0.1, 0.45, "melody")


# ------------------------------------------------------------------ extra layers for the intense version
def extras(m):
    st = G.step
    for b in range(G.bars):
        s = sec(b)
        r0 = rootm(b)
        third = 4 if is_major(b) else 3
        if s in ("A2", "A3", "B"):
            for k in range(16):
                acc = k in (0, 3, 6, 8, 11, 14)
                f = mf(r0 + 12 + (7 if k % 8 == 6 else 0) + (12 if k == 14 else 0))
                m.add(pluck(f, 2 * st * 0.9, 0.7 if acc else 0.4, 0.09), at(b, k), 0.2 if acc else 0.11, -0.3 if k % 2 else 0.3, 0.2, "pluck")
            for k in range(16):
                if k % 2 == 1 or k % 4 == 2:
                    m.add(hat(0.8 if k % 4 == 2 else 0.5, open_=(k % 8 == 6)), at(b, k), 0.07 if k % 8 != 6 else 0.1, 0.3 if k % 2 else -0.3, 0.15, "hat")
            m.add(drum("odaiko", 0.6), at(b, 2), 0.5, 0.0, 0.3, "taiko")
            m.add(drum("nagado", 0.6), at(b, 10), 0.5, 0.0, 0.3, "taiko")
            m.add(drum("tomh", 0.7), at(b, 13), 0.5, 0.2, 0.3, "taiko")
        if s in ("A2", "A3") and b % 8 < 6:
            for sp in (10, 14):
                for iv in (12, 19, 12 + 12 + third):
                    m.add(blat(mf(r0 + iv), 2 * st * 0.9, 0.9, 1.0), at(b, sp), 0.09, 0.0, 0.3, "stab")
        if b % 2 == 0 and s != "C":
            for iv, g in [(12, 1.0), (19, 0.8), (24 + third - 12, 0.7)]:
                c = choir_note(mf(r0 + iv + 12), 2 * G.bar + 0.5, "ah", "oh", 3, 0.8, 1.0)
                m.add(widen(c, rng_for("chw", b, iv), 0.7), at(b, 0) - 0.2, 0.34 * g, 0.0, 0.5, "choir")
        if b % 4 == 0 and b not in (0, 16, 32):
            m.add(crash_cymbal(0.7), at(b, 0), 0.25, 0.0, 0.4, "crash")
        if b % 8 == 6 and s != "C":
            m.add(swell_noise(2 * G.bar, 400, 9000, True, 0.18, 2.0, b), at(b, 0), 1.0, 0.0, 0.3, "riser")
        if s == "C" and b >= 28:
            for k in range(0, 16, 2):
                m.add(pluck(mf(rootm(b) + 12), 1.5 * st, 0.4, 0.08), at(b, k), 0.1, 0.0, 0.2, "pluck")
        if (s in ("A2", "A3") and b in (10, 11, 34, 35)) or b in MEL_B:
            for sp, iv, l in (THEME[b % 2] if b not in MEL_B else [(a, c - 64, d) for a, c, d in MEL_B[b]]):
                m.add(mallet(mf(76 + iv), 0.45, 0.14, 1.0), at(b, sp), 0.16, -0.2, 0.4, "bells")


def build(extra):
    m = Mix(G.dur, loop=True)
    drums(m)
    lowend(m)
    pads(m)
    melody(m)
    if extra:
        extras(m)
    y = m.render("hall", 0.8)
    t = T(len(y))
    y = y * np.interp(t, [G.t(23, 12), G.t(24), G.t(30), G.t(32)], [1.0, 0.7, 0.7, 1.0])[:, None]     # the breakdown is quieter
    return y, m


TARGET = -18.0
META = dict(bpm=100, beats_per_bar=4, bars=40, bar_s=G.bar, key="E minor", loop_start_s=0.0, loop_end_s=G.dur,
            section_s=[0.0, G.t(8), G.t(16), G.t(24), G.t(32)], section_names=["A stomp", "A2 theme", "B chords", "C breakdown", "A3 return"])


@event("music.rampage", stereo=True, loop=True, stream=True, raw=True, q=5, atten=0,
       note="the monster march, 100 bpm E minor, 40 bars = 96.0 s; loops; same tempo/key/length as rampage_intense")
def rampage(r, i):
    y, m = build(False)
    y, g = finish(y, target_lufs=TARGET, glue=1.15)
    if os.environ.get("GZ_STATS"):
        print("rampage layers (dB of total energy):", m.stats(), "gain", round(g, 2))
    return y, dict(META)


@event("music.rampage_intense", stereo=True, loop=True, stream=True, raw=True, q=5, atten=0,
       note="rampage plus extra layers (ostinato, hats, choir, stabs, bells); same gain as rampage so shared layers match; crossfade-compatible")
def rampage_intense(r, i):
    y0, _ = build(False)
    _, g = finish(y0, target_lufs=TARGET, glue=1.15)
    y, m = build(True)
    y, _ = finish(y, gain_db=g, glue=1.15)
    if os.environ.get("GZ_STATS"):
        print("intense layers (dB of total energy):", m.stats(), "gain", round(g, 2))
    return y, dict(META)
