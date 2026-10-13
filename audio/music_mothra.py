"""music_mothra.py - music.mothra_arrival (the mood turns) and music.mothra_fight (the boss loop).

mothra_arrival: 100 bpm, 5 bars = 12.0 s.  The march stops dead; a high choir and glass bells shimmer in on an E major
pentatonic cloud with a fluttering of wings; a riser and a snare roll lead to the very end, where music.mothra_fight starts.

mothra_fight: 150 bpm, 4/4, E minor with E major / lydian light for Mothra, 48 bars = 76.8 s = 1536 ticks, loops.
    bars  1- 8  A   the gallop: a 16th-note low-brass/saw ostinato over fast taiko, war snare and hats
    bars  9-16  B   + Mothra's choir and a fluttering glass motif on top
    bars 17-24  C   a fast heroic fanfare over C - D - E - B
    bars 25-32  D   breakdown: half-time, dim chords, swelling choir and bells
    bars 33-40  A'  everything again, with the fanfare under the choir
    bars 41-48  E   climax: rising siren-like tone, snare 16ths, high brass, a fill into bar 1
"""
import os
from music_core import *
from reg import event

E1, E2, E3 = 28, 40, 52

# ================================================================== arrival
A_G = Grid(100, 5)


@event("music.mothra_arrival", stereo=True, stream=True, raw=True, q=5, atten=0,
       note="12.0 s, 100 bpm (same tempo as rampage): the march cuts out, a shimmering choir and glass take over, a riser into the end; music.mothra_fight starts on its final beat")
def mothra_arrival(r, i):
    G = A_G
    m = Mix(G.dur, loop=False)
    n = m.n
    t = T(n)
    rr = rng_for("arrival")
    # the cut: one last dull blow and a falling sub, then the air changes
    m.add(drum("odaiko", 1.0), 0.0, 0.8, 0.0, 0.5, "hit")
    m.add(sub_boom(3.0, 60, 24, 1.0), 0.0, 0.5, 0.0, 0.2, "hit")
    m.add(brass(mf(E2), 1.8, 0.9, 0.5, 4, 0.04, 1.0), 0.0, 0.4, 0.0, 0.6, "tail")
    # wing flutter: filtered noise chopped at a rising rate
    wn = svf(white(n, rr), 2500 + 1500 * np.sin(TWO_PI * 0.3 * t), 1.2, "bp")
    rate = np.interp(t, [0, 3, 8, 12], [6, 8, 11, 14])
    chop = 0.5 + 0.5 * np.sin(phase_of(rate))
    wn = wn / (np.std(wn) + 1e-9) * chop ** 2 * np.interp(t, [0.2, 3.0, 9.0, 11.5, 12.0], [0.0, 0.25, 0.6, 0.9, 0.0])
    m.full(widen(wn, rr, 0.9) * 0.18, 1.0, 0.6, "flutter")
    low = lp(white(n, rr), 400, 2) * (0.5 + 0.5 * np.sin(phase_of(np.interp(t, [0, 12], [2.5, 2.5])))) * np.interp(t, [0.5, 4, 11.5, 12], [0, 0.4, 0.7, 0])
    m.full(widen(low / (np.std(low) + 1e-9), rr, 0.5) * 0.12, 1.0, 0.4, "wings")
    # the choir: chords of E major added ninth and its relatives, entering from 1.2 s, each lasting a bar, voices fluttering
    chords = [(0.5, 2.4 * 1.0, [64, 71, 78, 80]), (1.5, 2.4, [61, 68, 76, 83]), (3.9, 2.4, [57, 64, 71, 73, 81]), (6.3, 2.4, [59, 66, 71, 75, 83]), (8.7, 3.3, [64, 71, 75, 80, 83])]
    for k, (t0, d, notes) in enumerate(chords):
        d = d + 1.2
        for j, mid in enumerate(notes):
            c = choir_note(mf(mid), d, "ah" if j % 2 else "oh", "oh" if j % 2 else "ah", 3, 0.9, 1.2)
            tt = T(len(c))
            fl = 0.65 + 0.35 * (0.5 + 0.5 * np.sin(TWO_PI * (8 + 2 * k) * tt + j))
            m.add(widen(c * fl, rng_for("arw", k, j), 0.8), t0, 0.4 * (1.0 - 0.06 * j), 0.0, 0.7, "choir")
    # glass bells: a rising pentatonic arpeggio that thickens
    pent = [0, 2, 4, 7, 9]
    cnt = 0
    for k in range(120):
        tk = 1.0 + k * G.step * (2.0 - 1.0 * min(1.0, k / 60.0)) * 0.5
        if tk >= 11.6:
            break
        mid = 76 + pent[cnt % 5] + 12 * ((cnt // 5) % 2) + (7 if (cnt // 10) % 2 else 0)
        m.add(glass(mf(mid), 1.4, 0.45), tk, 0.1 * np.interp(tk, [1, 6, 11.6], [0.5, 1.0, 1.1]), np.sin(cnt * 0.9) * 0.7, 0.6, "glass")
        cnt += 1
    for tb in (0.0, 2.4, 4.8, 7.2, 9.6):
        m.add(bellnote(mf(E3 + 12 + (7 if tb in (4.8, 9.6) else 0)), 4.0, 2.5, 0.6), tb + 0.02, 0.12, 0.0, 0.8, "bell")
    # the build: a low pulse, a riser, accelerating snare, a reverse crash into the end
    for k in range(5):
        m.add(bass(mf(E1 + (0, 0, 9, 11, 11)[k]), G.bar * 0.8), G.t(k), 0.28, 0.0, 0.2, "sub")
    m.add(swell_noise(5.0, 300, 11000, True, 0.22, 2.0, "arr"), 7.0, 1.0, 0.0, 0.4, "riser")
    m.full(np.stack([fit(np.concatenate([np.zeros(N(5.0)), riser_tone(7.0, 220, 2400, 0.06)]), n)] * 2, axis=1), 1.0, 0.5, "riser2")
    tk = 8.4
    k = 0
    while tk < 11.95:
        m.add(war_snare(0.3 + 0.7 * (tk - 8.4) / 3.5), tk, 0.5, 0.0, 0.4, "roll")
        tk += 0.3 * (1 - 0.8 * (tk - 8.4) / 3.55) + 0.04
        k += 1
    m.add(reverse_swell(crash_cymbal(1.2), 1.6), 12.0 - 3.6 + 0.0, 0.4, 0.0, 0.4, "revcrash")
    y = m.render("cathedral", 0.8)
    y = y * np.interp(t, [0, 0.01, 11.88, 12.0], [0, 1, 1, 0.0])[:, None]       # ends cleanly on the fight's first downbeat
    y, _ = finish(y, target_lufs=-19.0, glue=1.2)
    return y, dict(bpm=100, key="E major (pentatonic cloud)", bar_s=G.bar, handoff_s=12.0)


# ================================================================== fight
F = Grid(150, 48)
ROOTS = [0] * 8 + [0, 0, 0, 0, 10, 10, 8, 7] + [8, 8, 10, 10, 0, 0, 7, 7] + [0, 0, 0, 0, 0, 0, 7, 7] + [0, 0, 0, 0, 8, 8, 10, 7] + [0, 0, 8, 8, 10, 10, 7, 7]
assert len(ROOTS) == 48
MAJOR = {8, 10, 7}
EVEN = [0, 0, 12, 0, 0, 0, 12, 0, 0, 0, 12, 0, 0, 3, 5, 3]
ODD = [0, 0, 12, 0, 0, 0, 12, 0, 1, 1, 13, 1, 0, 0, 12, 0]
ACC = {0, 6, 8, 12}
FANFARE = {16: [(0, 72, 2), (2, 72, 2), (4, 76, 4), (8, 79, 4), (12, 76, 4)], 17: [(0, 74, 2), (2, 74, 2), (4, 72, 4), (8, 71, 4), (12, 67, 4)],
           18: [(0, 74, 2), (2, 74, 2), (4, 78, 4), (8, 81, 4), (12, 78, 4)], 19: [(0, 76, 2), (2, 76, 2), (4, 74, 4), (8, 73, 4), (12, 69, 4)],
           20: [(0, 71, 2), (2, 71, 2), (4, 76, 4), (8, 79, 4), (12, 83, 4)], 21: [(0, 81, 2), (2, 79, 2), (4, 76, 4), (8, 74, 4), (12, 71, 4)],
           22: [(0, 75, 4), (4, 78, 4), (8, 83, 4), (12, 81, 4)], 23: [(0, 78, 8), (8, 71, 4), (12, 75, 4)]}


def fsec(b):
    return "A" if b < 8 else "B" if b < 16 else "C" if b < 24 else "D" if b < 32 else "A2" if b < 40 else "E"


def rootm(b):
    r = ROOTS[b]
    return E2 + (r if r <= 5 else r - 12)


def maj(b):
    return ROOTS[b] in MAJOR


def fat(b, step):
    return F.t(b, step)


def f_drums(m):
    st = F.step
    for b in range(F.bars):
        s = fsec(b)
        last = b % 8 == 7

        def hit(kind, step, vel, pan=0.0, send=0.3, g=0.7):
            m.add(drum(kind, vel), fat(b, step), g, pan, send, "taiko")
        if s in ("A", "B", "A2"):
            for k, v in [(0, 1.0), (6, 0.75), (8, 0.9), (11, 0.7), (14, 0.5)]:
                hit("odaiko" if k in (0, 8) else "nagado", k, v, 0.0, 0.3)
            for k in (4, 12):
                m.add(war_snare(1.0), fat(b, k), 0.5, 0.0, 0.4, "snare")
        elif s == "C":
            for q in range(4):
                hit("nagado", q * 4, 0.9 if q % 2 == 0 else 0.7)
                hit("odaiko", q * 4, 0.8 if q == 0 else 0.5, 0.0, 0.3, 0.6)
            for k in (4, 12):
                m.add(war_snare(1.0), fat(b, k), 0.55, 0.0, 0.4, "snare")
            for k in (2, 6, 10, 14):
                m.add(war_snare(0.35), fat(b, k), 0.25, 0.0, 0.4, "snare")
        elif s == "D":
            hit("odaiko", 0, 1.0)
            hit("odaiko", 8, 0.8)
            m.add(war_snare(1.0), fat(b, 8), 0.55, 0.0, 0.5, "snare")
            for q in (3, 5, 7):
                hit("tomh", q * 2 - 1 if q < 7 else 13, 0.4 + 0.05 * q, 0.2)
        else:                                                    # E: climax
            for q in range(4):
                hit("odaiko", q * 4, 1.0 if q == 0 else 0.6, 0.0, 0.3, 0.7)
            for k in range(0, 16, 2):
                hit("nagado", k + 1, 0.6, 0.0, 0.3, 0.5)
            for k in range(16):
                m.add(war_snare(0.35 + 0.6 * (k % 4 == 0) + 0.25 * (b - 40) / 8), fat(b, k), 0.32 if k % 4 else 0.5, 0.0, 0.4, "snare")
        if last and s != "D":
            for k, kind in enumerate(["tomh", "tom", "tomh", "tom", "nagado", "tom", "nagado", "odaiko"]):
                hit(kind, 8 + k, 0.6 + 0.05 * k, -0.4 + 0.1 * k, 0.4, 0.8)
        if b in (0, 8, 16, 24, 32, 40):
            m.add(crash_cymbal(1.0 if b not in (24,) else 0.7), fat(b, 0), 0.5, 0.0, 0.5, "crash")
    m.add(reverse_swell(crash_cymbal(1.0), 1.4), F.t(24) - 3.6, 0.3, 0.0, 0.5, "crash")
    m.add(reverse_swell(crash_cymbal(1.0), 1.4), F.t(48) - 3.6, 0.35, 0.0, 0.5, "crash")


def f_low(m):
    st = F.step
    for b in range(F.bars):
        s = fsec(b)
        r0 = rootm(b)
        if s == "D":
            if b % 2 == 0:
                m.add(brass(mf(r0), 2 * F.bar * 0.99, 0.9, 0.8, 4, 0.8, 0.5), fat(b, 0), 0.5, 0.0, 0.4, "brass")
                m.add(bass(mf(r0 - 12), 2 * F.bar * 0.99), fat(b, 0), 0.5, 0.0, 0.1, "sub")
            continue
        pat = EVEN if b % 2 == 0 else ODD
        for k, iv in enumerate(pat):
            ivm = iv
            if maj(b) and iv in (3, 1) and iv != 12:
                ivm = {3: 4, 1: -1}.get(iv, iv)
            if maj(b) and iv == 13:
                ivm = 11
            acc = k in ACC
            f = mf(r0 + ivm)
            m.add(brass(f, 0.8 * st, 1.0 if acc else 0.75, 0.9, 3, 0.012, 0.03), fat(b, k), 0.44 if acc else 0.3, -0.15, 0.2, "brass")
            m.add(pluck(f * 0.5 if iv != 12 else f * 0.5, 0.9 * st, 0.8, 0.07), fat(b, k), 0.3 if acc else 0.2, 0.15, 0.1, "cello")
            if k % 4 in (0, 2) or iv == 12:
                m.add(bass(mf(r0 - 12 if r0 - 12 >= 27 else r0), 1.2 * st), fat(b, k), 0.38, 0.0, 0.1, "sub")


def f_choir_pads(m):
    for b in range(0, F.bars, 2):
        s = fsec(b)
        r0 = rootm(b)
        third = 4 if maj(b) else 3
        if s in ("B", "A2", "D", "E"):
            for j, iv in enumerate([12 + 7, 24, 24 + third + 0, 24 + 7]):
                mid = r0 + iv
                c = choir_note(mf(mid), 2 * F.bar + 0.6, "ah", "oh", 3, 0.7, 0.9)
                tt = T(len(c))
                fl = 0.8 + 0.2 * np.sin(TWO_PI * 9.0 * tt + j)
                m.add(widen(c * fl, rng_for("fcw", b, j), 0.8), fat(b, 0) - 0.15, 0.42 * (0.9 ** j) * (1.3 if s == "D" else 1.0), 0.0, 0.7, "choir")
        p = pad_voice(mf(r0), 2 * F.bar + 0.8, 0.6, 0.8, 1.8)
        m.add(widen(p, rng_for("fpw", b), 0.5), fat(b, 0) - 0.2, 0.1, 0.0, 0.4, "pad")


def f_motif(m):
    """Mothra's flutter: major-pentatonic glass 16ths over the choir"""
    pent = [0, 2, 4, 7, 9, 12, 14, 16]
    for b in range(F.bars):
        s = fsec(b)
        if s in ("B", "A2", "D"):
            r0 = rootm(b)
            seq = [0, 2, 4, 3, 5, 4, 2, 1] if s != "D" else [0, 1, 2, 3, 4, 3, 2, 1]
            for k in range(16):
                idx = seq[k % 8]
                mid = {0: 76, 8: 79, 10: 78, 7: 83}.get(ROOTS[b], 76) + pent[idx % 8]
                m.add(glass(mf(mid), 1.5 * F.step * 2, 0.35), fat(b, k), 0.07 if k % 2 else 0.1, -0.5 + (k % 8) / 8.0, 0.5, "glass")
        if s == "D" and b % 2 == 0:
            m.add(bellnote(mf(E3 + 24 + (0 if ROOTS[b] == 0 else 7)), 3.0, 2.0, 0.6), fat(b, 0), 0.1, 0.0, 0.7, "bell")


def f_fanfare(m):
    for b in range(F.bars):
        mel = None
        if b in FANFARE:
            mel = FANFARE[b]
        elif b in (32, 33, 34, 35):
            mel = FANFARE[{32: 20, 33: 21, 34: 20, 35: 21}[b]]
        elif fsec(b) == "E":
            k = b - 40
            mel = [(0, 83, 4), (4, 86, 4), (8, 88, 4), (12, 91 if k % 2 else 88, 4)] if k < 7 else [(0, 83, 2), (2, 86, 2), (4, 88, 2), (6, 91, 2), (8, 95, 6), (14, 91, 2)]
        if mel is None:
            continue
        for sp, mid, l in mel:
            m.add(brass(mf(mid), l * F.step * 0.92, 1.0, 1.0, 4, 0.03, 0.08), fat(b, sp), 0.46, 0.1, 0.4, "fanfare")
            m.add(blat(mf(mid + 12), l * F.step * 0.6, 0.8, 1.0), fat(b, sp), 0.1, -0.1, 0.4, "fanfare")


def f_siren(m):
    """climax: a rising alarm tone, one sweep per two bars"""
    n = m.n
    sw = np.zeros(n)
    for b in range(40, 48, 2):
        d = 2 * F.bar
        tt = T(N(d))
        f = 500 * (1900 / 500) ** ((tt / d) ** 0.8)
        ph = phase_of(f)
        s = (np.sin(ph) + 0.5 * np.sin(2 * ph + 0.4) + 0.3 * np.sin(3 * ph)) * np.minimum(1, tt / 0.1) * np.minimum(1, (d - tt) / 0.05)
        m.add(s, fat(b, 0), 0.07, 0.0, 0.4, "siren")
    m.add(swell_noise(2 * F.bar, 300, 11000, True, 0.2, 2.0, "f1"), fat(46, 0), 1.0, 0.0, 0.3, "riser")


def f_extra_hats(m):
    for b in range(F.bars):
        if fsec(b) in ("D",):
            for k in range(0, 16, 2):
                m.add(shaker(0.8), fat(b, k), 0.07, 0.3 if k % 4 else -0.3, 0.1, "hat")
            continue
        for k in range(16):
            v = 0.9 if k % 4 == 2 else 0.5
            m.add(hat(v, open_=(k % 8 == 6)), fat(b, k), 0.06 if k % 2 else 0.09, 0.3 if k % 2 else -0.3, 0.1, "hat")


@event("music.mothra_fight", stereo=True, loop=True, stream=True, raw=True, q=5, atten=0,
       note="the boss loop, 150 bpm E minor, 48 bars = 76.8 s; loops; arrival hands over on its downbeat")
def mothra_fight(r, i):
    m = Mix(F.dur, loop=True)
    f_drums(m)
    f_low(m)
    f_choir_pads(m)
    f_motif(m)
    f_fanfare(m)
    f_siren(m)
    f_extra_hats(m)
    y = m.render("hall", 0.7)
    t = T(len(y))
    y = y * np.interp(t, [F.t(23, 12), F.t(24), F.t(30), F.t(32)], [1.0, 0.72, 0.72, 1.0])[:, None]
    y, g = finish(y, target_lufs=-17.5, glue=1.15)
    if os.environ.get("GZ_STATS"):
        print("fight layers:", m.stats(), "gain", round(g, 2))
    return y, dict(bpm=150, beats_per_bar=4, bars=48, bar_s=F.bar, key="E minor", loop_start_s=0.0, loop_end_s=F.dur,
                   section_s=[F.t(0), F.t(8), F.t(16), F.t(24), F.t(32), F.t(40)],
                   section_names=["A gallop", "B choir", "C fanfare", "D breakdown", "A2 return", "E climax"])
