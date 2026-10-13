"""music_rise_misc.py - music.rise, music.victory, music.defeat, music.results_loop.  All E-based, all original."""
import os
from music_core import *
from reg import event

E1, E2, E3 = 28, 40, 52          # midi
RISE_BPM = 100
RISE_G = Grid(RISE_BPM, 9)
HIT_S = RISE_G.t(5)              # 12.0 s
HANDOFF_S = RISE_G.t(8)          # 19.2 s: the downbeat on which music.rampage should start


# ================================================================== the rise
@event("music.rise", stereo=True, stream=True, raw=True, q=5, atten=0,
       note="20 s: dark underwater drone, distant heartbeat drums and a rising swell, a held breath, then the HUGE hit at 12.0 s (head breaks the surface); a short ring-out; start music.rampage on the 19.2 s downbeat")
def rise(r, i):
    G = RISE_G
    dur = 20.0
    m = Mix(dur, loop=False)                      # build-up bus (ducked just before the hit)
    h = Mix(dur, loop=False)                      # the hit and after
    n = m.n
    t = T(n)
    rr = rng_for("rise")
    duck = np.interp(t, [0, HIT_S - 0.42, HIT_S - 0.30, HIT_S - 0.03, HIT_S], [1, 1, 0.18, 0.18, 0.0])
    # --- 0..12 s: the dark water
    drone = np.zeros(n)
    for mid, g, ph in [(E1, 1.0, 0.0), (E1 + 12, 0.6, 1.0), (E1 + 19, 0.35, 2.0), (E1 + 24, 0.18, 3.0)]:
        f = mf(mid)
        drone += g * (np.sin(TWO_PI * f * t + ph) + 0.4 * np.sin(TWO_PI * f * 2.003 * t + ph))
    drone *= np.interp(t, [0, 3.5, 9.0, HIT_S], [0.0, 0.5, 0.9, 1.0])
    m.full(widen(drone, rr, 0.5) * duck[:, None] * 0.28, 1.0, 0.2, "drone")
    water = svf(white(n, rr), np.interp(t, [0, 6, HIT_S], [160, 420, 1100]), 0.7, "lp") * np.interp(t, [0, 4, HIT_S], [0.0, 0.5, 1.0]) * np.clip(0.6 + 0.6 * drift(n, rr, 6, False), 0, 2)
    m.full(widen(water / (np.std(water) + 1e-9) * 0.1, rr, 0.8) * duck[:, None], 1.0, 0.4, "water")
    bub = bubbles(HIT_S, rr, 6, 150, 700, 14, 1.0)
    m.full(np.stack([fit(bub, n)] * 2, axis=1) * duck[:, None] * 0.18, 1.0, 0.5, "bubbles")
    # distant heartbeat drums: beats from 2.4 s, doubling at 7.2, then a crescendo roll into the hit
    for k in range(40):
        tk = 2.4 + k * G.beat
        if tk >= HIT_S - 1.3:
            break
        v = np.interp(tk, [2.4, 7.0, 10.6], [0.25, 0.55, 0.85])
        m.add(drum("odaiko", v), tk, 0.7, 0.0, 0.6, "heart")
        if tk > 7.0:
            m.add(drum("nagado", v * 0.8), tk + G.beat / 2, 0.5, 0.0, 0.6, "heart")
    for k in range(10):                                                   # the last roll, accelerating: tom 8ths then 16ths
        tk = HIT_S - 1.3 + k * 0.13 * (1 - 0.04 * k)
        m.add(drum("tom" if k % 2 else "tomh", 0.45 + 0.055 * k), tk, 0.6, -0.3 + 0.07 * k, 0.5, "roll")
    # a swelling brass dread and a rising pitch
    for mid, g in [(E2, 1.0), (E2 + 1, 0.55), (E2 + 7, 0.6)]:
        d = HIT_S - 4.0 - 0.3
        m.add(brass(mf(mid), d, 0.9, 0.7, 4, 5.0, 0.3), 4.0, 0.34 * g, 0.0, 0.5, "brass")
    rt_ = np.concatenate([np.zeros(N(4.0)), riser_tone(HIT_S - 4.0, 90, 1500, 0.07)])
    m.full(np.stack([fit(rt_, n)] * 2, axis=1) * duck[:, None], 1.0, 0.5, "riser")
    m.full(widen(fit(np.concatenate([np.zeros(N(5.0)), swell_noise(HIT_S - 5.0, 300, 9000, True, 0.12, 2.2, "rise")]), n), rr, 0.8) * duck[:, None], 1.0, 0.5, "noise")
    m.add(reverse_swell(crash_cymbal(1.2), 1.3), HIT_S - 4.4, 0.55, 0.0, 0.5, "revcrash")
    # --- the hit at 12.0 s: every drum, the whole brass section, a crash and a sub boom
    T0 = HIT_S
    for kind, v, pan in [("odaiko", 1.0, 0.0), ("nagado", 1.0, -0.25), ("tom", 1.0, 0.25), ("odaiko", 0.9, 0.0)]:
        h.add(drum(kind, v), T0, 0.95, pan, 0.45, "hit")
    h.add(crash_cymbal(1.3), T0, 0.7, 0.0, 0.5, "hit")
    h.add(sub_boom(4.0, 70, 26, 1.6), T0, 0.8, 0.0, 0.2, "hit")
    for mid, g in [(E1, 1.0), (E2, 1.0), (E2 + 7, 0.9), (E2 + 12, 0.9), (E2 + 15, 0.8), (E3 + 7, 0.7), (E3 + 12, 0.5)]:
        h.add(brass(mf(mid), 3.6, 1.0, 1.0, 5, 0.03, 1.4), T0, 0.5 * g, 0.0, 0.5, "hitbrass")
    for mid in (E3 + 7, E3 + 12, E3 + 15, E3 + 19):
        h.add(blat(mf(mid), 0.9, 1.0, 1.0), T0, 0.2, 0.0, 0.5, "hitbrass")
    h.add(bellnote(mf(E3 + 12), 5.0, 3.0, 0.8), T0, 0.14, 0.0, 0.6, "hitbell")
    # --- 12..19.2: the ring-out and a stomp, then a fill that leads into the rampage downbeat
    for k, (b, st) in enumerate([(5, 8), (6, 0), (6, 8), (7, 0), (7, 8)]):
        h.add(drum("odaiko", 0.8 - 0.07 * k), G.t(b, st), 0.8, 0.0, 0.5, "stomp")
    for b, s_, mid, d in [(5, 10, E2, 2), (5, 14, E2, 2), (6, 4, E2 + 3, 2), (6, 6, E2, 2), (6, 12, E2 + 1, 2), (6, 14, E2, 2)]:
        h.add(brass(mf(mid), d * G.step * 0.85, 0.8, 0.8), G.t(b, s_), 0.4, 0.0, 0.4, "stomp")
    for k, kind in enumerate(["tomh", "tom", "tom", "nagado", "tomh", "tom", "nagado", "odaiko"]):
        h.add(drum(kind, 0.55 + 0.06 * k), G.t(7, 8 + k), 0.7, -0.4 + 0.1 * k, 0.5, "fill")
    h.add(swell_noise(2 * G.bar, 400, 10000, True, 0.2, 2.0, "r2"), G.t(6, 0), 1.0, 0.0, 0.3, "fill")
    yb = m.render("hall", 0.9)
    yh = h.render("cathedral", 0.65)
    y = 0.5 * yb + 1.15 * yh
    # fade the ring-out under the handoff
    y = y * np.interp(t, [0, HANDOFF_S - 0.25, HANDOFF_S + 0.8], [1, 1, 0.0])[:, None]
    y, _ = finish(y, target_lufs=-17.0, glue=1.2)
    return y, dict(bpm=RISE_BPM, key="E minor", bar_s=G.bar, hit_s=HIT_S, handoff_s=HANDOFF_S, loop_start_s=None,
                   note2="build 0-12 s; hit exactly at hit_s; start music.rampage at handoff_s (bar 9 of the 100 bpm grid)")


# ================================================================== victory
V_G = Grid(120, 10)


@event("music.victory", stereo=True, stream=True, raw=True, q=5, atten=0,
       note="20 s, 120 bpm, E major: brass fanfare, a triumphant theme, a huge final chord with crash, bells and choir")
def victory(r, i):
    G = V_G
    m = Mix(G.dur, loop=False)
    # bars 0-1: fanfare calls over a taiko roll
    for k, (mid, st, l) in enumerate([(E3 + 4, 0, 2), (E3 + 4, 2, 2), (E3 + 7, 4, 2), (E3 + 12, 6, 6), (E3 + 11, 12, 2), (E3 + 12, 14, 2)]):
        m.add(blat(mf(mid), l * G.step * 0.9, 1.0, 1.0), G.t(0, st), 0.5, 0.0, 0.4, "fan")
        m.add(brass(mf(mid - 12), l * G.step * 0.9, 0.9, 0.9), G.t(0, st), 0.4, 0.0, 0.4, "fan")
    for k in range(8):
        m.add(drum("tomh" if k % 2 else "tom", 0.4 + 0.07 * k), G.t(0, 0) + k * G.beat / 2, 0.6, 0.0, 0.5, "roll")
    m.add(drum("odaiko", 1.0), G.t(1, 0), 0.9, 0.0, 0.5, "hit")
    m.add(crash_cymbal(1.0), G.t(2, 0), 0.5, 0.0, 0.5, "crash")
    # bars 2-7: the theme over E | B/D# | C#m | A | E/G# | A | B  (a rising major-key tune of my own)
    chords = {2: (E2, [0, 7, 16]), 3: (E2 + 7, [0, 7, 16]), 4: (E2 + 9, [0, 7, 15]), 5: (E2 + 5, [0, 7, 16]), 6: (E2, [0, 7, 16]), 7: (E2 + 7, [0, 7, 16])}
    for b, (root, ivs) in chords.items():
        for iv in ivs:
            m.add(brass(mf(root + iv), G.bar * 0.97, 0.85, 0.7, 4, 0.06, 0.2), G.t(b, 0), 0.22, 0.0, 0.4, "chord")
        m.add(bass(mf(root - 12 if root - 12 >= 27 else root), G.bar * 0.45), G.t(b, 0), 0.4, 0.0, 0.1, "sub")
        m.add(bass(mf(root - 12 if root - 12 >= 27 else root), G.bar * 0.45), G.t(b, 8), 0.4, 0.0, 0.1, "sub")
        for st in (0, 8):
            m.add(drum("odaiko", 0.8), G.t(b, st), 0.7, 0.0, 0.4, "taiko")
        for st in (4, 12):
            m.add(war_snare(0.8), G.t(b, st), 0.5, 0.0, 0.5, "snare")
        for st in (6, 14):
            m.add(drum("nagado", 0.5), G.t(b, st), 0.6, 0.2, 0.4, "taiko")
    theme = {2: [(0, 76, 4), (4, 71, 2), (6, 73, 2), (8, 76, 6), (14, 78, 2)], 3: [(0, 79, 6), (6, 78, 2), (8, 76, 4), (12, 75, 4)],
             4: [(0, 73, 4), (4, 76, 4), (8, 80, 6), (14, 78, 2)], 5: [(0, 81, 6), (6, 80, 2), (8, 78, 4), (12, 76, 4)],
             6: [(0, 76, 2), (2, 78, 2), (4, 80, 4), (8, 83, 6), (14, 81, 2)], 7: [(0, 80, 4), (4, 78, 4), (8, 75, 4), (12, 78, 4)]}
    for b, notes in theme.items():
        for st, mid, l in notes:
            m.add(brass(mf(mid), l * G.step * 0.95, 1.0, 1.0, 4, 0.04, 0.1), G.t(b, st), 0.5, 0.1, 0.45, "theme")
            m.add(mallet(mf(mid + 12), 0.5, 0.18, 1.0), G.t(b, st), 0.12, -0.2, 0.5, "bells")
    # bars 8-9: the final chord (E major, wide) with everything
    T0 = G.t(8)
    for mid, g in [(E1, 1.0), (E2, 1.0), (E2 + 7, 0.8), (E2 + 12, 0.8), (E2 + 16, 0.7), (E3 + 7, 0.7), (E3 + 12, 0.6)]:
        m.add(brass(mf(mid), 6.0, 1.0, 1.0, 5, 0.05, 2.5), T0, 0.4 * g, 0.0, 0.5, "final")
    for mid in (E3 + 16, E3 + 19, E3 + 24):
        m.add(blat(mf(mid), 1.0, 1.0, 1.0), T0, 0.14, 0.0, 0.5, "final")
    m.add(drum("odaiko", 1.0), T0, 0.9, 0.0, 0.5, "hit")
    m.add(drum("nagado", 1.0), T0, 0.8, -0.2, 0.5, "hit")
    m.add(crash_cymbal(1.5), T0, 0.6, 0.0, 0.6, "crash")
    m.add(sub_boom(4.0, 62, 28, 1.4), T0, 0.6, 0.0, 0.2, "hit")
    for k, mid in enumerate([E3 + 28, E3 + 24, E3 + 19, E3 + 16, E3 + 24, E3 + 28, E3 + 31, E3 + 36]):
        m.add(glass(mf(mid), 1.8, 0.5), T0 + 0.4 + 0.22 * k, 0.1, -0.6 + 0.17 * k, 0.6, "spark")
    for mid, g in [(E3 + 4, 1.0), (E3 + 11, 0.8), (E3 + 16, 0.7)]:
        c = choir_note(mf(mid + 12), 6.0, "ah", "oh", 3, 1.0, 3.0)
        m.add(widen(c, rng_for("vc", mid), 0.7), T0 + 0.2, 0.45 * g, 0.0, 0.5, "choir")
    m.add(bellnote(mf(E3 + 12), 7.0, 3.5, 0.8), T0, 0.12, 0.0, 0.7, "bell")
    y = m.render("cathedral", 0.8)
    y = y * np.interp(np.arange(len(y)) / SR, [0, G.dur - 2.5, G.dur], [1, 1, 0])[:, None] ** 1.5
    y, _ = finish(y, target_lufs=-17.0, glue=1.1)
    return y, dict(bpm=120, key="E major", bar_s=G.bar, final_chord_s=T0)


# ================================================================== defeat
@event("music.defeat", stereo=True, stream=True, raw=True, q=5, atten=0,
       note="15 s, 60 bpm, E minor: a heavy blow, a falling low-brass line, a tolling bell and a dying cluster")
def defeat(r, i):
    dur = 15.0
    m = Mix(dur, loop=False)
    rr = rng_for("defeat")
    m.add(drum("odaiko", 1.0), 0.0, 0.9, 0.0, 0.5, "hit")
    m.add(sub_boom(4.0, 62, 26, 1.5), 0.0, 0.7, 0.0, 0.2, "hit")
    m.add(crash_cymbal(1.2), 0.0, 0.3, 0.0, 0.6, "crash")
    for k, (mid, t0, d) in enumerate([(E3 + 7, 0.0, 2.4), (E3 + 5, 2.5, 2.4), (E3 + 3, 5.0, 2.4), (E3 + 1, 7.5, 2.8), (E3, 10.2, 4.8)]):
        m.add(brass(mf(mid), d, 0.85, 0.55, 4, 0.5, 1.0), t0, 0.5, 0.0, 0.6, "line")
        m.add(brass(mf(mid - 12), d, 0.8, 0.5, 4, 0.5, 1.0), t0, 0.4, 0.0, 0.6, "line")
    for k, t0 in enumerate([0.0, 2.5, 5.0, 7.5, 10.2]):
        m.add(bellnote(mf(E3 - 12 + 7) if k % 2 == 0 else mf(E3 - 12), 4.0, 3.0, 0.5), t0 + 0.05, 0.22, 0.0, 0.8, "bell")
    for k in range(5):
        m.add(drum("nagado", 0.6 - 0.08 * k), 2.5 * k + 1.25, 0.5, 0.0, 0.6, "taiko")
    # a dying cluster: E, F, B drifting down a semitone over the piece, closing
    n = m.n
    t = T(n)
    cl = np.zeros(n)
    for mid, g in [(E2, 1.0), (E2 + 1, 0.7), (E2 + 7, 0.6), (E2 + 8, 0.4)]:
        f = mf(mid) * 2 ** (-np.interp(t, [0, dur], [0, 1.2]) / 12.0)
        ph = phase_of(f)
        cl += g * (np.sin(ph) + 0.5 * np.sin(2 * ph + 1) + 0.25 * np.sin(3 * ph + 2))
    cl = svf(cl, np.interp(t, [0, 3, dur], [900, 500, 120]), 0.8, "lp") * np.interp(t, [0, 1.5, 9.0, dur], [0.0, 1.0, 0.7, 0.0])
    m.full(widen(cl, rr, 0.6) * 0.12, 1.0, 0.5, "cluster")
    y = m.render("cathedral", 0.9)
    y = y * np.interp(np.arange(len(y)) / SR, [0, dur - 3.0, dur], [1, 1, 0])[:, None] ** 1.3
    y, _ = finish(y, target_lufs=-20.0, glue=1.1)
    return y, dict(bpm=60, key="E minor", bar_s=4.0)


# ================================================================== results loop
R_G = Grid(128, 16)


@event("music.results_loop", stereo=True, loop=True, stream=True, raw=True, q=5, atten=0,
       note="the score screen: a light arcade loop, 128 bpm, E major, 16 bars = 30.0 s")
def results_loop(r, i):
    G = R_G
    m = Mix(G.dur, loop=True)
    prog = [(40, 4), (37, 3), (45, 4), (47, 4)]                    # E2, C#2.., A2, B2 roots
    st = G.step
    for b in range(G.bars):
        root, third = prog[b % 4][0], prog[b % 4][1]
        T0 = G.t(b)
        for k in range(8):                                          # bass: bouncing 8ths
            mid = root - 12 + (12 if k % 4 == 3 else 0) + (7 if k == 6 else 0)
            m.add(pluck(mf(mid + 12), 1.5 * st, 0.5, 0.12), T0 + k * 2 * st, 0.35, 0.0, 0.1, "bass")
        arp = [0, third, 7, 12, 7, third, 12, 7 + 12]
        for k in range(16):                                         # square-ish arpeggio, 16ths
            mid = root + 12 + arp[k % 8]
            m.add(pluck(mf(mid + 12), 1.2 * st, 0.9, 0.06), T0 + k * st, 0.13 if k % 4 else 0.17, -0.4 if k % 2 else 0.4, 0.25, "arp")
        m.add(drum("tom", 0.5), T0, 0.5, 0.0, 0.1, "kick")
        m.add(drum("tom", 0.45), T0 + 8 * st, 0.5, 0.0, 0.1, "kick")
        m.add(drum("tomh", 0.35), T0 + 11 * st, 0.4, 0.0, 0.1, "kick")
        for k in (4, 12):
            m.add(rim(1.0), T0 + k * st, 0.45, 0.0, 0.2, "snare")
        for k in range(0, 16, 2):
            m.add(hat(0.7, open_=(k == 14)), T0 + k * st, 0.08, 0.3, 0.1, "hat")
        if b >= 8:
            mel = [[0, 4, 7, 12, 11, 7, 4, 7], [4, 7, 9, 12, 11, 9, 7, 4], [0, 4, 9, 12, 9, 4, 12, 9], [7, 11, 14, 11, 7, 11, 14, 19]][b % 4]
            for k, off in enumerate(mel):
                mid = 64 + off - (0 if b % 4 != 1 else 3)
                m.add(mallet(mf(mid + 12), 0.45, 0.16, 1.0), T0 + k * 2 * st, 0.2, (-0.3 if k % 2 else 0.3), 0.45, "mallet")
        if b % 4 == 0:
            for iv in (0, 7, 16):
                p = pad_voice(mf(root + iv), 4 * G.bar * 0.5 + 0.8, 0.3, 0.6, 2.2)
                m.add(widen(p, rng_for("rp", b, iv), 0.5), T0 - 0.05, 0.05, 0.0, 0.4, "pad")
    for k in range(12):
        m.add(glass(mf(88 + [0, 4, 7, 11][k % 4]), 1.2, 0.3), G.t(k + 2, 15) if k < 14 else 0, 0.04, (-0.7 + 0.12 * k), 0.5, "spark")
    y = m.render("room", 0.6)
    y, _ = finish(y, target_lufs=-22.0, glue=1.2, eq_pts=[(25, -20), (60, -2), (120, 0), (6000, 0), (12000, -4), (16000, -12)])
    return y, dict(bpm=128, key="E major", bars=16, bar_s=G.bar, loop_start_s=0.0, loop_end_s=G.dur)
