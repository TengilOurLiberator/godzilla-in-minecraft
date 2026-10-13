"""sfx_city.py - the citizens and their city: cartoonish screams, panic crowds, squishes, horns, alarms, tyres, trains, sirens."""
from lib import *
from reg import event


# ------------------------------------------------------------------ voices
def human(r, dur, f_keys, vowels, amp_db, fscale=1.0, vib_rate=6.5, vib_depth=0.025, jitter=0.012, breath=0.12, rasp=0.0, nh=38,
          wobble=0.0, wobble_rate=11.0):
    """a synthetic human voice: glottal pulse train, vibrato, formant filters, breath, optional rasp; exaggerated for cartoon effect"""
    n = N(dur)
    t = T(n)
    f = contour(n, f_keys, 14.0)
    f = f * (1 + vib_depth * np.sin(TWO_PI * vib_rate * t + r.uniform(0, 6.28)) * np.minimum(1, t / 0.15 + 0.2))
    if wobble:
        f = f * (1 + wobble * np.sin(TWO_PI * wobble_rate * t))
    amp = env(amp_db, n, "db")
    src = glottal(f, r, jitter, None, nh, 1.0)
    if rasp:
        src = src * (1 - rasp * (0.5 + 0.5 * np.sin(phase_of(f * 0.5 + 0.0)))) + 0.0
    fc = vcurve(n, vowels, fscale)
    voc = formant_bank(src, fc, (9, 11, 13), (1.0, 0.6, 0.35), dry=0.05)
    voc = voc / (np.std(voc) + 1e-9)
    br = formant_bank(white(n, r), fc, (4, 5, 6), (1.0, 0.8, 0.5))
    br = br / (np.std(br) + 1e-9)
    y = (voc + breath * br * (1 + 2 * rasp)) * amp
    return y


def scream_voice(r, i):
    """the ten screams: 0-2 female, 3-5 male, 6-7 short squeaks, 8-9 groups (handled by caller)"""
    if i == 0:     # classic rising shriek that cracks upward
        d = 1.5
        return human(r, d, [(0, 420), (0.12, 900), (0.45, 1050), (0.9, 1180), (1.2, 900), (d, 650)], [(0, "ah"), (0.5, "eh"), (1.0, "ah"), (d, "oh")],
                     [(0, -60), (0.02, -14), (0.15, 0), (1.0, -2), (1.3, -12), (d, -60)], 1.15, 7.0, 0.03, 0.02, 0.1, 0.25)
    if i == 1:     # "aaAAaaah" with a vibrato wobble that is just a bit too fast
        d = 1.7
        return human(r, d, [(0, 520), (0.2, 780), (0.6, 1000), (1.2, 760), (d, 560)], [(0, "aa"), (0.6, "ah"), (d, "oh")],
                     [(0, -60), (0.04, -10), (0.25, 0), (1.2, -3), (1.6, -18), (d, -60)], 1.12, 9.5, 0.06, 0.015, 0.1, 0.2)
    if i == 2:     # a falling cartoon wail from a very high start
        d = 1.9
        return human(r, d, [(0, 1250), (0.25, 1100), (0.9, 820), (d, 380)], [(0, "ee"), (0.4, "eh"), (1.0, "ah"), (d, "oh")],
                     [(0, -60), (0.03, -10), (0.15, 0), (1.2, -4), (d, -60)], 1.12, 6.0, 0.035, 0.02, 0.12, 0.2)
    if i == 3:     # man: gruff yell breaking into a falsetto squeal
        d = 1.6
        return human(r, d, [(0, 150), (0.15, 260), (0.45, 330), (0.62, 700), (1.0, 820), (d, 420)], [(0, "aa"), (0.5, "ah"), (0.7, "ee"), (d, "oh")],
                     [(0, -60), (0.03, -10), (0.12, 0), (0.9, -3), (d, -60)], 0.95, 6.5, 0.03, 0.02, 0.15, 0.35)
    if i == 4:     # man: long, rising "waaaah"
        d = 1.8
        return human(r, d, [(0, 190), (0.3, 300), (1.0, 480), (d, 350)], [(0, "oo"), (0.2, "ow"), (0.8, "ah"), (d, "oh")],
                     [(0, -60), (0.1, -8), (0.4, 0), (1.3, -2), (d, -60)], 0.92, 5.8, 0.03, 0.015, 0.12, 0.2)
    if i == 5:     # man: two panicked shouts
        d = 1.4
        return human(r, d, [(0, 200), (0.1, 330), (0.35, 270), (0.5, 230), (0.6, 380), (1.0, 420), (d, 250)], [(0, "aa"), (0.4, "eh"), (0.6, "ah"), (d, "oh")],
                     [(0, -60), (0.02, -8), (0.1, 0), (0.4, -4), (0.46, -14), (0.52, -3), (0.9, -2), (d, -60)], 0.95, 6.5, 0.03, 0.025, 0.15, 0.4)
    if i == 6:     # short squeak: "eek!"
        d = 0.6
        return human(r, d, [(0, 800), (0.1, 1500), (0.3, 1650), (d, 1100)], [(0, "ee"), (d, "ee")],
                     [(0, -60), (0.015, -6), (0.08, 0), (0.35, -3), (d, -60)], 1.1, 8.0, 0.04, 0.015, 0.05, 0.1)
    return human(r, 0.75, [(0, 380), (0.08, 700), (0.35, 760), (0.75, 450)], [(0, "ah"), (0.4, "eh"), (0.75, "ah")],
                 [(0, -60), (0.02, -6), (0.1, 0), (0.45, -3), (0.75, -60)], 1.0, 9.0, 0.05, 0.02, 0.1, 0.25)


@event("people.scream", variants=10, peak=-4.0, atten=60, note="cartoon screams: 0-2 women, 3-5 men, 6-7 squeaks, 8-9 groups")
def scream(r, i):
    if i < 8:
        y = scream_voice(r, i)
        y = eq(y, [(100, -12), (250, 0), (3000, 2), (7000, -8), (12000, -20)], circ=False)
        return reverb(y, space("city", stereo=False), wet=0.12, dry=1.0, n=len(y) + N(0.4))
    # group: several overlapping voices of different ages and sizes
    d = 2.4
    y = np.zeros(N(d))
    count = 5 if i == 8 else 7
    for k in range(count):
        v = scream_voice(r, int(r.integers(0, 8)))
        v = v * r.uniform(0.4, 1.0)
        ps = r.uniform(0.85, 1.25)
        v = sg.resample_poly(v, 100, int(100 * ps)) if abs(ps - 1) > 0.02 else v
        place(y, v, r.uniform(0.0, 0.7), 1.0, None)
    y = eq(y, [(100, -12), (250, 0), (3000, 2), (7000, -8), (12000, -20)], circ=False)
    return reverb(y, space("city", stereo=False), wet=0.2, dry=1.0, n=len(y) + N(0.5))


def _syllable(r):
    d = r.uniform(0.18, 0.55)
    f0 = r.choice([r.uniform(180, 300), r.uniform(380, 650), r.uniform(800, 1200)])
    v = human(r, d, [(0, f0 * 0.8), (d * 0.25, f0 * r.uniform(1.0, 1.4)), (d, f0 * r.uniform(0.7, 1.1))], [(0, "ah"), (d * 0.5, r.choice(["eh", "aa", "oh", "ee"])), (d, "ah")],
              [(0, -60), (0.02, -4), (d * 0.3, 0), (d, -60)], 1.0 if f0 < 400 else 1.12, 6.5, 0.04, 0.02, 0.15, 0.25)
    return v


@event("people.crowd_panic_loop", loop=True, lufs=-24.0, atten=70, q=5, note="a crowd running and screaming, 6 s seamless")
def crowd_panic_loop(r, i):
    dur = 6.0
    n = N(dur)
    y = np.zeros(n)
    for k in range(46):
        s = _syllable(r)
        place(y, s * r.uniform(0.2, 1.0), r.uniform(0, dur), 1.0, None, True)
    mur = eq(colored(n, r, -3.0), [(100, -30), (300, 0), (1200, 0), (3500, -12), (8000, -30)]) * (0.6 + 0.4 * swell(n, r, 5, 1.0))
    feet = np.zeros(n)
    for k in range(int(dur * 14)):
        c = N(0.03)
        place(feet, bp(white(c, r), 400, 3000, 1) * expo(c, 0.006) * r.uniform(0.2, 1.0), r.uniform(0, dur), 1.0, None, True)
    rumble = colored(n, r, -6.0, lo=25, hi=200) * (0.6 + 0.4 * swell(n, r, 3, 1.0))
    out = rn(y, -14) + rn(mur, -22) + rn(feet, -30) + rn(rumble, -28)
    return eq(out, [(80, -10), (200, 0), (4000, 0), (9000, -10), (14000, -24)])


@event("people.squish", variants=4, peak=-5.0, atten=50, note="cartoon squish: a wet splat with a bubbly pop")
def squish(r, i):
    dur = 0.55
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    sp = svf(white(n, r), 2200 * np.exp(-t / 0.05) + 250, 1.0, "lp") * expo(n, 0.07, 0.001)
    y += 0.9 * sp / (np.std(sp) + 1e-9) * 0.3
    f = 220 * (1 + 0.3 * i) * np.exp(-t / 0.05) + 70
    y += 0.8 * np.sin(phase_of(f)) * expo(n, 0.09, 0.001)
    y += 0.5 * bubbles(dur, r, 60, 300 + 100 * i, 1400, 8) * expo(n, 0.25)
    pop = shimmer_note(r.uniform(1000, 1500), 0.12, r, 0.03) * 0.25
    place(y, pop, 0.05 + 0.02 * i)
    return y


# ------------------------------------------------------------------ vehicles
def _horn_note(f, dur, rough=0.02):
    n = N(dur)
    t = T(n)
    y = hosc(np.full(n, f), n, nh=16, rolloff=1.2, odd=True) + 0.5 * hosc(np.full(n, f * 1.003), n, nh=16, rolloff=1.2, odd=True)
    y = svf(y, 3200, 0.8, "lp")
    e = np.minimum(1, t / 0.012) * np.minimum(1, np.maximum(0, dur - t) / 0.03)
    return y * e * (1 + rough * np.sin(TWO_PI * 90 * t))


@event("car.horn", variants=4, peak=-6.0, atten=100, note="car horns: two-tone, single, double beep, a long blare")
def car_horn(r, i):
    pairs = [(420, 530), (360, 450), (480, 600), (330, 415)]
    f1, f2 = pairs[i]
    pat = [[(0.0, 0.9)], [(0.0, 0.25), (0.35, 0.25)], [(0.0, 0.45)], [(0.0, 1.3)]][i]
    total = max(a + b for a, b in pat) + 0.15
    n = N(total)
    y = np.zeros(n)
    for t0, dd in pat:
        place(y, _horn_note(f1, dd) + 0.9 * _horn_note(f2, dd), t0)
    y = eq(y, [(150, -10), (400, 0), (1500, 2), (4000, -6), (9000, -18)], circ=False)
    return reverb(y, space("city", stereo=False), wet=0.15, dry=1.0, n=n + N(0.5))


@event("car.alarm", variants=2, peak=-8.0, atten=90, note="car alarm, 4 s: pattern is seamless if replayed")
def car_alarm(r, i):
    dur = 4.0
    n = N(dur)
    t = T(n)
    if i == 0:       # whooping sweeps
        cyc = 0.5
        p = (t % cyc) / cyc
        f = 650 + 900 * p
        y = hosc(f, n, nh=14, rolloff=1.0, odd=True)
        y *= np.minimum(1, (cyc - (t % cyc)) / 0.01)
    else:            # bi-tonal chirps
        f = np.where(((t // 0.12) % 2) == 0, 1300.0, 1750.0)
        y = hosc(f, n, nh=14, rolloff=1.0, odd=True) * (((t % 0.5) < 0.38).astype(float))
    y = svf(y, 5000, 0.7, "lp")
    y = eq(y, [(300, -10), (800, 0), (2500, 2), (6000, -8)], circ=False)
    return reverb(y, space("city", stereo=False), wet=0.2, dry=1.0, n=n)


@event("car.skid", variants=3, peak=-5.0, atten=90, note="tyres screaming on asphalt")
def car_skid(r, i):
    dur = 1.5 + 0.2 * i
    n = N(dur)
    t = T(n)
    f = 1500 + 400 * drift(n, r, 6, False) + 400 * i
    sq = hosc(np.clip(f, 700, 2600), n, nh=8, rolloff=1.1) * np.clip(0.7 + 0.5 * drift(n, r, 25, False), 0.1, 1.4)
    nz = svf(white(n, r), np.clip(f * 1.3, 800, 4000), 2.0, "bp")
    rub = bp(white(n, r), 150, 900, 1) * np.clip(0.6 + 0.6 * drift(n, r, 40, False), 0, 2)
    e = np.minimum(1, t / 0.08) * np.minimum(1, (dur - t) / 0.25)
    y = (0.5 * sq + 0.9 * nz / (np.std(nz) + 1e-9) * 0.3 + 0.4 * rub / (np.std(rub) + 1e-9) * 0.3) * e
    return y


@event("train.pass", variants=2, lufs=-20.0, atten=160, note="an electric commuter train passing, 9 s: motor whine and rail clatter with Doppler, a short horn")
def train_pass(r, i):
    dur = 9.0
    n = N(dur)
    t = T(n)
    tp = 4.3 + 0.2 * i
    near = 1.0 / (1.0 + ((t - tp) / 1.1) ** 2)
    dop = 1.0 + 0.1 * np.tanh(-(t - tp) / 0.5)
    whine = np.zeros(n)
    for k, a in [(1, 1.0), (2, 0.5), (3, 0.3)]:
        whine += a * np.sin(phase_of(520 * k * dop * (1 + 0.04 * i)) + k)
    whine = svf(whine, 3500, 0.7, "lp") * near ** 1.2
    rt = np.zeros(n)
    cars = 8
    tcar = 0.55
    for c in range(cars):
        for ax in range(4):
            tt = tp - 3.0 + c * tcar + (ax // 2) * 0.08 + (ax % 2) * 0.012 * 7
            if 0 < tt < dur:
                nr = near[min(n - 1, int(tt * SR))]
                place(rt, plate(r.uniform(300, 700), 0.2, r, 0.04, 6, 6) * nr, tt)
    roar_ = svf(white(n, r), 700 * dop, 0.8, "bp") * near ** 0.8 + lp(white(n, r), 220, 2) * near
    y = 0.4 * whine / (np.std(whine) + 1e-9) + 0.9 * rt / (np.std(rt) + 1e-9) * 0.35 + 0.8 * roar_ / (np.std(roar_) + 1e-9) * 0.5
    hf = 880 * (1 + 0.1 * np.tanh(-(t - (tp - 1.0)) / 0.5))
    horn = np.sin(phase_of(hf)) + 0.5 * np.sin(2 * phase_of(hf) + 1)
    horn *= ((t > tp - 2.2) & (t < tp - 1.5)).astype(float) * (near ** 0.5)
    y += 0.5 * horn
    y *= np.minimum(1, t / 0.5) * np.minimum(1, (dur - t) / 1.0)
    return reverb(y, space("open", stereo=False), wet=0.2, dry=1.0, n=n)


@event("siren.city_loop", loop=True, lufs=-20.0, atten=220, q=5, note="air-raid siren, 10 s cycle: slow wail rising to a plateau and winding down, with city echoes")
def siren_city_loop(r, i):
    dur = 10.0
    n = N(dur)
    t = T(n)
    p = t / dur
    sh = 0.5 - 0.5 * np.cos(2 * np.pi * np.clip(p * 1.0, 0, 1))
    sh = np.clip(sh * 1.25, 0, 1) ** 0.9
    f = 360 + 280 * sh
    y = siren_tone(f, n)
    y = y * (0.8 + 0.2 * sh)
    y = circ(lambda z: lp(z, 4500, 2), y)
    ir = space("city", stereo=False)
    w = cconv(y, ir)
    return y * 0.8 + 0.7 * w


@event("siren.police", variants=2, peak=-6.0, atten=140, note="police siren: 0 = two-tone 'pii-poo', 1 = rising yelp; 3.2 s, seamless if replayed")
def siren_police(r, i):
    dur = 3.2
    n = N(dur)
    t = T(n)
    if i == 0:
        f = np.where(((t // 0.55) % 2) == 0, 960.0, 770.0)
        f = sg.sosfiltfilt(sg.butter(2, 60 / (SR / 2), "low", output="sos"), f)
    else:
        p = (t % 0.33) / 0.33
        f = 650 + 850 * p ** 1.3
    ph = phase_of(f)
    y = np.sin(ph) + 0.6 * np.sin(2 * ph + 0.5) + 0.4 * np.sin(3 * ph + 1) + 0.2 * np.sin(4 * ph)
    y = svf(y, 5500, 0.7, "lp")
    y = eq(y, [(300, -12), (700, 0), (2500, 1), (7000, -10)], circ=False)
    return reverb(y, space("city", stereo=False), wet=0.2, dry=1.0, n=n)
