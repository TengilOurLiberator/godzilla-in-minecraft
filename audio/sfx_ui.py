"""sfx_ui.py - the arcade layer: score ticks, combos, bonus, title hit, game over and victory stings (stereo, non-positional)."""
from lib import *
from reg import event

E = {"E3": 164.81, "B3": 246.94, "E4": 329.63, "G4": 392.0, "G#4": 415.3, "B4": 493.88, "E5": 659.25, "G#5": 830.61, "B5": 987.77, "E6": 1318.5, "G6": 1568.0, "B6": 1975.5}


def _verb(x, name="hall", wet=0.25, tail=1.0):
    st = widen(x, np.random.default_rng(5), 0.4) if x.ndim == 1 else x
    ir = space(name)
    return reverb(st, ir, wet=wet, dry=1.0, n=len(x) + N(tail))


@event("ui.score_tick", variants=4, stereo=True, peak=-8.0, atten=0, note="a tiny bright blip for each score increment")
def score_tick(r, i):
    f = [1318.5, 1568.0, 1760.0, 1975.5][i]
    y = mix(shimmer_note(f, 0.16, r, 0.04), 0.4 * shimmer_note(f * 2, 0.1, r, 0.02))
    return np.stack([y, y], axis=1)


@event("ui.combo_up", variants=4, stereo=True, peak=-4.0, atten=0, note="rising arpeggio; variation k is a step higher than k-1, so the game may pick by combo level")
def combo_up(r, i):
    base = 329.63 * 2 ** (i * 2 / 12.0)
    notes = [1.0, 1.25, 1.5, 2.0]
    y = np.zeros(N(0.7))
    for k, ratio in enumerate(notes):
        place(y, shimmer_note(base * 2 * ratio, 0.45, r, 0.13) * (0.7 + 0.1 * k), 0.06 * k)
    place(y, 0.3 * pluck_saw(base, 0.25, 0.7, 0.1), 0.0)
    return _verb(y, "plate", 0.2, 0.5)


@event("ui.combo_break", variants=2, stereo=True, peak=-4.0, atten=0, note="the combo is lost: two falling, deflating notes")
def combo_break(r, i):
    y = np.zeros(N(1.0))
    for k, (f, t0) in enumerate([(392.0, 0.0), (277.2 - 20 * i, 0.16)]):
        d = 0.5
        n = N(d)
        t = T(n)
        ff = f * (1 - 0.12 * (t / d) ** 1.5)
        s = hosc(ff, n, nh=18, rolloff=1.1) * np.exp(-t / 0.22) * np.minimum(1, t / 0.005)
        s = svf(s, 1800 * np.exp(-t / 0.2) + 300, 0.9, "lp")
        place(y, s * 0.6, t0)
    place(y, 0.4 * deep(0.5, 120, 55, 0.04, 0.12, 0.3), 0.16)
    return _verb(y, "room", 0.2, 0.5)


@event("ui.big_bonus", stereo=True, peak=-2.0, atten=0, note="a big bonus: a brass hit, a cascade of coin-like chimes and a bell")
def big_bonus(r, i):
    d = 2.2
    y = np.zeros(N(d))
    for nm, g in [("E3", 1.0), ("B3", 0.8), ("E4", 0.7), ("G#4", 0.5)]:
        place(y, stab(E[nm], 0.55, 1.0) * g * 0.5, 0.0)
    place(y, 0.7 * taiko(r, 70, 1.2, 0.4, 0.8), 0.0)
    for k in range(14):
        f = E["E5"] * 2 ** ([0, 4, 7, 12, 16, 19][k % 6] / 12.0 + (k // 6))
        place(y, shimmer_note(f, 0.6, r, 0.2) * (0.5 - 0.02 * k), 0.08 + 0.065 * k)
    place(y, 0.5 * bell_tone(E["E5"] * 2, 1.8, 1.0, 0.8), 0.08)
    place(y, 0.15 * crash(r, 1.8, 0.6, 1.2), 0.0)
    return _verb(y, "hall", 0.3, 1.0)


@event("ui.title_hit", stereo=True, peak=-1.0, atten=0, note="the title card: a gigantic hit; drums, brass chord, crash and a sub drop")
def title_hit(r, i):
    d = 5.0
    y = np.zeros(N(d))
    for k, (p, g) in enumerate([(48, 1.0), (62, 0.9), (82, 0.6)]):
        place(y, taiko(r, p, 2.2, 0.8, 1.0) * g, 0.012 * k)
    for nm, g in [("E3", 1.0), ("B3", 0.9), ("E4", 0.8)]:
        place(y, lowbrass(E[nm] / 2, 1.8, 1.0, 1.0, 5, 14, 0.04, 0.8) * g * 0.45, 0.02)
    for nm, g in [("B3", 0.7), ("E4", 0.6), ("G4", 0.5), ("B4", 0.4)]:
        place(y, stab(E[nm], 1.2, 1.0) * g * 0.35, 0.02)
    place(y, crash(r, 4.5, 1.4, 0.9) * 0.5, 0.0)
    place(y, deep(3.0, 70, 24, 0.25, 1.0, 0.6) * 0.9, 0.0)
    place(y, 0.3 * bell_tone(E["E4"], 4.0, 2.0, 1.0), 0.0)
    return _verb(y, "cathedral", 0.35, 1.0)


@event("ui.game_over", stereo=True, peak=-2.0, atten=0, note="the end card on a loss: a falling minor brass line, a low drum and a tolling bell")
def game_over(r, i):
    d = 4.0
    y = np.zeros(N(d))
    for k, (nm, t0, dd) in enumerate([("G4", 0.0, 0.5), ("E4", 0.5, 0.5), ("B3", 1.0, 0.5), ("E3", 1.5, 1.9)]):
        place(y, lowbrass(E[nm] * 0.5, dd, 0.9, 0.7, 4, 12, 0.06, 0.2) * 0.7, t0)
    place(y, taiko(r, 55, 2.0, 0.8, 0.8) * 0.9, 1.5)
    place(y, 0.3 * bell_tone(E["E4"], 3.5, 2.0, 0.5), 1.5)
    return _verb(y, "hall", 0.35, 1.5)


@event("ui.victory_sting", stereo=True, peak=-2.0, atten=0, note="a short triumphant fanfare in E major with crash and chimes")
def victory_sting(r, i):
    d = 3.6
    y = np.zeros(N(d))
    seq = [("E4", 0.0, 0.22), ("E4", 0.25, 0.22), ("G#4", 0.5, 0.22), ("B4", 0.75, 0.3)]
    for nm, t0, dd in seq:
        place(y, stab(E[nm], dd, 1.0, 1.0) * 0.5, t0)
    for nm, g in [("E3", 1.0), ("B3", 0.8), ("E4", 0.7), ("G#4", 0.55)]:
        place(y, lowbrass(E[nm], 1.9, 1.0, 1.0, 4, 10, 0.03, 0.7) * g * 0.4, 1.1)
    place(y, 0.8 * taiko(r, 66, 1.8, 0.6, 1.0), 1.1)
    place(y, 0.4 * crash(r, 3.0, 1.0, 1.0), 1.1)
    for k in range(10):
        place(y, shimmer_note(E["E5"] * 2 ** ([0, 4, 7, 12, 16][k % 5] / 12.0 + (k // 5)), 0.7, r, 0.25) * 0.3, 1.2 + 0.07 * k)
    return _verb(y, "hall", 0.3, 1.0)
