"""sfx_military.py - tanks, shells, jets, missiles, helicopters, machine guns, ship guns and maser cannons."""
from lib import *
from reg import event


@event("mil.tank_fire", variants=3, peak=-1.5, atten=180, note="a tank gun: a flat hard crack, the boom, a shell whistling away, echoes off the buildings")
def tank_fire(r, i):
    dur = 3.0
    n = N(dur)
    y = blast(r, dur, 0.55 + 0.06 * i, sub=(120, 38), crack=1.5, tail=0.6, bright=1.3, drive=1.8, debris_level=0.05)
    place(y, pk(whoosh_pass(0.9, r, 6000, 1200, 0.15, 1.5, 0.3), -14), 0.04)
    place(y, pk(plate(900 - 70 * i, 0.5, r, 0.12, 10, 7), -14), 0.0)                    # the breech ringing
    return reverb(weight(y, 1.0, 1.0), space("city", stereo=False), wet=0.3, dry=1.0, n=n)


@event("mil.tank_engine_loop", loop=True, lufs=-23.0, atten=100, q=5, note="a heavy diesel with track clatter; 3 s seamless")
def tank_engine_loop(r, i):
    dur = 3.0
    n = N(dur)
    eng = engine(dur, 1000.0, r, cyl=8, bright=0.25, rough=0.8, loop=True)
    eng = circ(lambda z: lp(z, 900, 2), eng)
    clank = np.zeros(n)
    for k in range(int(dur * 11)):
        place(clank, plate(r.uniform(600, 2200), 0.15, r, 0.03, 6, 6) * r.uniform(0.3, 1.0), (k + r.uniform(-0.2, 0.2)) / 11.0, 1.0, None, True)
    rl = colored(n, r, -6.0, lo=25, hi=220) * (0.7 + 0.3 * swell(n, r, 4, 1.0))
    gr = eq(colored(n, r, -2.0), [(100, -40), (400, 0), (1500, -4), (5000, -20)]) * (0.5 + 0.5 * swell(n, r, 11, 1.0))
    y = rn(eng, -12) + rn(clank, -24) + rn(rl, -16) + rn(gr, -25)
    return sat(y, 1.2)


@event("mil.shell_hit", variants=4, peak=-3.0, atten=100, note="a shell bursting or glancing off Godzilla's hide: dull thud, a spang and a ricochet whine")
def shell_hit(r, i):
    dur = 1.7
    n = N(dur)
    y = np.zeros(n)
    place(y, 0.9 * deep(0.6, 150 - 10 * i, 70, 0.03, 0.09, 0.4), 0)
    k = N(0.05)
    y[:k] += 0.6 * bp(white(k, r), 200, 2500, 1) * expo(k, 0.008, 0.0003)
    place(y, 0.6 * pk(steel_bar(1100 + 150 * i, 0.9, r, 0.25), -2), 0.01)                    # the casing spangs
    d = 0.7
    t = T(N(d))
    f = 4800 * np.exp(-t / 0.22) + 900 + 150 * i
    wh = np.sin(phase_of(f)) * (0.6 + 0.4 * np.sin(TWO_PI * 13 * t)) * np.exp(-t / 0.25)
    place(y, 0.28 * wh, 0.03)                                                                 # tumbling away
    place(y, 0.35 * pk(burst(0.25, r, 1500, 9000, 0.05, 0.0004), -3), 0.0)
    return reverb(y, space("city", stereo=False), wet=0.2, dry=1.0, n=n)


@event("mil.jet_pass", variants=2, lufs=-19.0, atten=260, note="a jet fighter screaming past low, 6.5 s, Doppler from approach to departure")
def jet_pass(r, i):
    dur = 6.5
    n = N(dur)
    t = T(n)
    tp = 3.0 + 0.2 * i
    near = 1.0 / (1.0 + ((t - tp) / 0.95) ** 2) ** 1.1
    dop = 1 + 0.32 * np.tanh(-(t - tp) / 0.4)
    nz = white(n, r)
    a = svf(nz, 900 * dop, 0.6, "bp")
    b = svf(nz, 3200 * dop, 0.8, "bp")
    c = svf(nz, 230 * dop, 0.7, "lp")
    wh = np.zeros(n)
    for k, g in [(1, 1.0), (2, 0.4), (3, 0.2)]:
        wh += g * np.sin(phase_of(1700 * k * dop * (1 - 0.2 * i)) + k)
    wh *= 0.55 + 0.45 * np.sin(TWO_PI * 5 * t) * 0.0 + 0.0
    y = (0.9 * a / (np.std(a) + 1e-9) + 0.6 * b / (np.std(b) + 1e-9) + 1.0 * c / (np.std(c) + 1e-9)) * near
    y += 0.35 * wh * near ** 1.5
    y *= np.minimum(1, t / 0.4) * np.minimum(1, (dur - t) / 1.2)
    return reverb(y, space("open", stereo=False), wet=0.25, dry=1.0, n=n)


@event("mil.missile", variants=3, peak=-4.0, atten=150, note="a missile launching and streaking away: ignition crack then a hissing rocket motor that recedes")
def missile(r, i):
    dur = 3.5
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    place(y, 0.8 * pk(burst(0.2, r, 500, 8000, 0.03, 0.0004), -2), 0.0)
    place(y, 0.6 * deep(0.5, 180, 60, 0.03, 0.1, 0.3), 0.0)
    f = 3500 * np.exp(-t / 1.1) + 700 + 150 * i
    mo = svf(white(n, r), f, 0.9, "bp") * np.exp(-t / 1.4) * np.minimum(1, t / 0.05)
    mo2 = hp(white(n, r), 3000, 1) * np.exp(-t / 0.9) * 0.4
    y += 1.1 * mo / (np.std(mo) + 1e-9) * 0.3 + mo2 / (np.std(mo2) + 1e-9) * 0.1
    y += 0.4 * lp(white(n, r), 300, 2) * np.exp(-t / 0.9) * 0.8
    return reverb(y, space("city", stereo=False), wet=0.2, dry=1.0, n=n)


@event("mil.heli_loop", loop=True, lufs=-23.0, atten=170, q=5, note="helicopter rotor chop with turbine whine; 2 s seamless")
def heli_loop(r, i):
    dur = 2.0
    n = N(dur)
    t = T(n)
    blades = 22
    sl = np.zeros(n)
    for k in range(blades):
        tt = k * dur / blades
        b = lp(white(N(0.06), r), 700, 2) * expo(N(0.06), 0.012, 0.0015)
        b = b / (np.std(b) + 1e-9)
        place(sl, b * (0.8 + 0.2 * r.random()), tt, 1.0, None, True)
        place(sl, deep(0.12, 110, 70, 0.02, 0.025, 0.3) * 1.2, tt, 1.0, None, True)
    tur = np.zeros(n)
    for fk, a in [(lockf(1250, dur), 1.0), (lockf(2480, dur), 0.4), (lockf(310, dur), 0.5)]:
        tur += a * np.sin(TWO_PI * fk * t + r.uniform(0, 6.28))
    tur *= 0.7 + 0.3 * lfo(n, 22, 0.1)
    wind = eq(colored(n, r, -3.0), [(100, -30), (400, 0), (2000, -4), (6000, -16)]) * (0.6 + 0.4 * swell(n, r, 6, 1.0))
    y = rn(sl, -12) + rn(tur, -30) + rn(wind, -25)
    return sat(y, 1.2)


@event("mil.mg", variants=4, peak=-5.0, atten=130, note="a machine-gun burst: 8-14 rounds at about 13 per second with echoes")
def mg(r, i):
    shots = [10, 14, 8, 12][i]
    dur = shots / 13.0 + 1.2
    n = N(dur)
    y = np.zeros(n)
    t0 = 0.0
    for k in range(shots):
        s = np.zeros(N(0.25))
        c = N(0.08)
        s[:c] += bp(white(c, r), 500, 6500, 2) * expo(c, 0.008, 0.0002)
        place(s, 0.7 * deep(0.2, r.uniform(240, 300), 120, 0.01, 0.025, 0.3), 0)
        place(y, s * r.uniform(0.7, 1.0), t0)
        t0 += (1 / 13.0) * r.uniform(0.9, 1.1)
    return reverb(y, space("city", stereo=False), wet=0.4, dry=1.0, n=n)


@event("mil.ship_cannon", variants=2, peak=-1.0, atten=300, note="a battleship's main gun: a huge flat boom across the water, a shell going over, long echoes")
def ship_cannon(r, i):
    dur = 6.0
    n = N(dur)
    y = blast(r, dur, 1.15 + 0.1 * i, sub=(65, 22), crack=1.5, tail=1.3, bright=0.8, drive=1.8, debris_level=0.0)
    place(y, pk(whoosh_pass(2.0, r, 2500, 400, 0.4, 1.2, 0.35), -17), 0.1)
    place(y, 0.3 * pk(plate(160, 3.0, r, 1.2, 14, 8), -6), 0.0)
    return reverb(weight(y, 1.0, 1.0), space("bay", stereo=False), wet=0.4, dry=1.0, n=n)


@event("mil.maser_charge", variants=2, peak=-3.0, atten=140, fout=0.004, note="a maser cannon spinning up: 2 s of climbing electric whine, arcs and a throbbing hum")
def maser_charge(r, i):
    dur = 2.0
    n = N(dur)
    t = T(n)
    p = t / dur
    f = 110 * (2400 / 110) ** (p ** 1.3)
    ph = phase_of(f)
    y = np.sin(ph + 1.2 * np.sin(phase_of(f * 2.01))) * (0.6 + 0.4 * np.sin(TWO_PI * (14 + 90 * p ** 2) * t))
    y += 0.4 * np.sin(2 * ph + 1)
    hum = np.sin(phase_of(60 + 20 * p)) + 0.5 * np.sin(phase_of(120 + 40 * p))
    z = np.zeros(n)
    tt = 0.0
    while tt < dur:
        tt += r.exponential(1.0 / (4 + 80 * p[min(n - 1, int(tt * SR))] ** 2))
        if tt < dur:
            place(z, click(r, r.uniform(2500, 8000), 0.003) * r.uniform(0.3, 1), tt)
    y = 0.7 * y * (0.1 + 0.9 * p ** 1.2) + 0.5 * hum * p + 0.4 * z
    return sat(y, 1.4)


@event("mil.maser_loop", loop=True, lufs=-21.0, atten=140, q=5, note="the maser beam: a seamless 1.5 s buzzing electric column")
def maser_loop(r, i):
    dur = 1.5
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    for k, a in [(1, 1.0), (2, 0.6), (3, 0.5), (4, 0.3), (5, 0.25), (7, 0.15)]:
        fk = lockf(210 * k, dur)
        y += a * np.sin(TWO_PI * fk * t + 1.5 * np.sin(TWO_PI * 6 * t / dur * 4 + k) + k)
    y *= 0.6 + 0.4 * np.sin(TWO_PI * lockf(60, dur) * t)
    sz = circ(lambda z: hp(z, 5000, 2), white(n, r)) * (0.5 + 0.5 * swell(n, r, 30, 1.0))
    sub = np.sin(TWO_PI * lockf(55, dur) * t)
    out = rn(y, -14) + rn(sz, -26) + rn(sub, -22)
    return sat(out * 1.2, 1.3)
