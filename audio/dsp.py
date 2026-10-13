"""dsp.py - the synthesis toolkit shared by every sound.

Everything is float64 numpy at SR = 44100.  Conventions:
  * a "loop" sound is built from periodic ingredients (see tile3/mid, lockf, drift, phase_loop, eq, cconv) so its
    last sample runs straight into its first.
  * eq() is a zero-phase FFT shaper (the main tone-shaping tool); the numba state-variable filter handles
    anything whose cutoff moves.
"""
import numpy as np
import scipy.signal as sg
from scipy.fft import rfft, irfft, rfftfreq, next_fast_len
from scipy.interpolate import CubicSpline
from scipy.ndimage import minimum_filter1d, uniform_filter1d
from numba import njit
import pyloudnorm as pyln

SR = 44100
TWO_PI = 2.0 * np.pi


def N(s):
    return int(round(s * SR))


def T(n):
    return np.arange(n) / SR


def db(x):
    return 10.0 ** (np.asarray(x) / 20.0)


def todb(x):
    return 20.0 * np.log10(np.maximum(x, 1e-12))


def fit(x, n):
    """pad with zeros / trim to n samples (axis 0)"""
    if len(x) >= n:
        return x[:n].copy()
    pad = [(0, n - len(x))] + [(0, 0)] * (x.ndim - 1)
    return np.pad(x, pad)


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=1)


def endfade(y, ms=8.0):
    """short linear fade at the very end so a truncated tail never clicks"""
    k = min(len(y), max(2, int(ms / 1000.0 * SR)))
    y[-k:] *= np.linspace(1.0, 0.0, k)
    return y


def lockf(f, dur):
    """frequency nudged so a steady oscillator completes an integer number of cycles in dur seconds"""
    return np.maximum(1.0, np.round(np.asarray(f) * dur)) / dur


# ------------------------------------------------------------------ noise
def colored(n, r, slope=-3.0, lo=20.0, hi=None):
    """periodic noise with a power slope in dB/octave (pink -3, brown -6, blue +3), rms 1"""
    X = rfft(r.standard_normal(n))
    f = rfftfreq(n, 1 / SR)
    g = (np.maximum(f, lo) / 1000.0) ** (slope / 6.0206)
    if hi:
        g = g * (1.0 / (1.0 + (f / hi) ** 4))
    g[0] = 0
    y = irfft(X * g, n)
    return y / (np.sqrt(np.mean(y ** 2)) + 1e-12)


def white(n, r):
    return r.standard_normal(n)


def pink(n, r):
    return colored(n, r, -3.0)


def brown(n, r):
    return colored(n, r, -6.0)


# ------------------------------------------------------------------ spectral shaping
def eq(x, pts, circ=True):
    """Zero-phase EQ.  pts = [(Hz, dB), ...] interpolated linearly in log-frequency, held flat outside.
    circ=True treats x as periodic (loops).  circ=False pads to avoid wrap-around."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    m = n if circ else next_fast_len(n + SR // 2)
    f = rfftfreq(m, 1 / SR)
    fp = np.log10(np.maximum([p[0] for p in pts], 1.0))
    gp = [p[1] for p in pts]
    g = db(np.interp(np.log10(np.maximum(f, 1.0)), fp, gp))
    X = rfft(x, m, axis=0)
    X = X * (g[:, None] if x.ndim == 2 else g)
    return irfft(X, m, axis=0)[:n]


def lp(x, f, order=2):
    sos = sg.butter(order, min(f, 0.49 * SR) / (SR / 2), "low", output="sos")
    return sg.sosfilt(sos, x, axis=0)


def hp(x, f, order=2):
    sos = sg.butter(order, max(f, 1.0) / (SR / 2), "high", output="sos")
    return sg.sosfilt(sos, x, axis=0)


def bp(x, lo_f, hi_f, order=2):
    sos = sg.butter(order, [max(lo_f, 1.0) / (SR / 2), min(hi_f, 0.49 * SR) / (SR / 2)], "band", output="sos")
    return sg.sosfilt(sos, x, axis=0)


def notch(x, f, q=10.0):
    b, a = sg.iirnotch(f / (SR / 2), q)
    return sg.lfilter(b, a, x, axis=0)


def tile3(x):
    return np.concatenate([x, x, x])


def circ(fn, x):
    """run a causal filter function on a periodic signal so the result is periodic too"""
    n = len(x)
    return fn(np.concatenate([x, x, x]))[n:2 * n]


@njit(cache=True, fastmath=True)
def _svf(x, f, q, mode, sr):
    n = len(x)
    y = np.empty(n)
    ic1 = 0.0
    ic2 = 0.0
    for i in range(n):
        fc = f[i]
        if fc > 0.48 * sr:
            fc = 0.48 * sr
        if fc < 5.0:
            fc = 5.0
        g = np.tan(np.pi * fc / sr)
        k = 1.0 / q[i]
        a1 = 1.0 / (1.0 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        v3 = x[i] - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        if mode == 0:
            y[i] = v2
        elif mode == 1:
            y[i] = k * v1
        elif mode == 2:
            y[i] = x[i] - k * v1 - v2
        else:
            y[i] = x[i] - k * v1
    return y


def _curve(v, n):
    if np.isscalar(v):
        return np.full(n, float(v))
    return np.asarray(v, dtype=np.float64)


def svf(x, f, q=0.707, mode="lp"):
    """state-variable filter with per-sample cutoff f (Hz) and q.  mode lp / bp (unity peak) / hp / notch"""
    x = np.ascontiguousarray(x, dtype=np.float64)
    if x.ndim == 2:
        return np.stack([svf(x[:, c], f, q, mode) for c in range(x.shape[1])], axis=1)
    n = len(x)
    return _svf(x, _curve(f, n), _curve(q, n), {"lp": 0, "bp": 1, "hp": 2, "notch": 3}[mode], float(SR))


@njit(cache=True, fastmath=True)
def _delayloop(x, d, fb, damp):
    n = len(x)
    L = int(np.max(d)) + 4
    buf = np.zeros(L)
    y = np.empty(n)
    w = 0
    lpz = 0.0
    for i in range(n):
        rp = w - d[i]
        while rp < 0:
            rp += L
        i0 = int(rp)
        fr = rp - i0
        i1 = i0 + 1
        if i1 >= L:
            i1 -= L
        v = buf[i0] * (1.0 - fr) + buf[i1] * fr
        lpz = (1.0 - damp) * v + damp * lpz
        o = x[i] + fb * lpz
        buf[w] = o
        y[i] = o
        w += 1
        if w >= L:
            w = 0
    return y


def delayloop(x, delay, fb=0.98, damp=0.3):
    """feedback delay line with one-pole damping in the loop and (optionally moving) fractional delay in samples.
    Fed with a short burst it is Karplus-Strong; fed with noise it is a comb filter."""
    x = np.ascontiguousarray(x, dtype=np.float64)
    return _delayloop(x, _curve(delay, len(x)), float(fb), float(damp))


def ks(exc, freq, dur, fb=0.996, damp=0.35):
    """Karplus-Strong string/bar: exc is the excitation (burst), freq may be a curve (Hz)."""
    n = N(dur)
    x = fit(exc, n)
    f = _curve(freq, n)
    return endfade(delayloop(x, SR / f - 0.5, fb, damp))


# ------------------------------------------------------------------ envelopes and modulators
def env(pts, n, kind="lin"):
    """piecewise envelope from [(t_seconds, value), ...] over n samples. kind 'lin', 'db' (values in dB) or 'exp' (geometric)."""
    t = np.array([p[0] for p in pts], dtype=float)
    v = np.array([p[1] for p in pts], dtype=float)
    ts = T(n)
    if kind == "db":
        return db(np.interp(ts, t, v))
    if kind == "exp":
        return np.exp(np.interp(ts, t, np.log(np.maximum(v, 1e-9))))
    return np.interp(ts, t, v)


def expo(n, tau, attack=0.0):
    """exp(-t/tau) with an optional linear attack (s)"""
    t = T(n)
    e = np.exp(-t / tau)
    if attack > 0:
        e = e * np.minimum(1.0, t / attack)
    return e


def fade(x, fin=0.0, fout=0.0, shape="cos"):
    x = x.copy()
    n = len(x)
    a, b = N(fin), N(fout)
    if a > 0:
        w = np.linspace(0, 1, a)
        w = 0.5 - 0.5 * np.cos(np.pi * w) if shape == "cos" else w
        x[:a] *= w[:, None] if x.ndim == 2 else w
    if b > 0:
        w = np.linspace(1, 0, b)
        w = 0.5 - 0.5 * np.cos(np.pi * w) if shape == "cos" else w
        x[n - b:] *= w[:, None] if x.ndim == 2 else w
    return x


def drift(n, r, rate, periodic=True):
    """smooth random modulation, unit std, about `rate` new random targets per second (cubic, periodic over n samples)"""
    k = max(2, int(round(rate * n / SR)))
    xs = np.linspace(0, n, k + 1)
    ys = r.standard_normal(k + 1)
    if periodic:
        ys[-1] = ys[0]
        cs = CubicSpline(xs, ys, bc_type="periodic")
    else:
        cs = CubicSpline(xs, ys)
    y = cs(np.arange(n))
    return y / (np.std(y) + 1e-9)


def swell(n, r, rate, depth=1.0, periodic=True):
    """positive slow gain modulation (geometric), mean about 1"""
    return np.exp(depth * 0.5 * drift(n, r, rate, periodic) - 0.0)


def lfo(n, cycles, phase=0.0, shape="sin"):
    """integer-cycle periodic LFO in [-1, 1]"""
    p = (np.arange(n) / n * cycles + phase) % 1.0
    if shape == "tri":
        return 4 * np.abs(p - 0.5) - 1
    if shape == "saw":
        return 2 * p - 1
    return np.sin(TWO_PI * p)


def phase_of(f, loop=False):
    """phase (radians) of an oscillator with frequency curve f (Hz).  loop=True nudges f so the phase wraps exactly."""
    f = np.asarray(f, dtype=np.float64)
    ph = np.cumsum(f) / SR
    if loop:
        tot = ph[-1] + f[-1] / SR
        k = max(1.0, np.round(tot))
        ph = ph * (k / tot)
    return TWO_PI * ph


# ------------------------------------------------------------------ oscillators
def sine(f, n=None, loop=False, phase0=0.0):
    f = _curve(f, n) if n else np.asarray(f, dtype=np.float64)
    return np.sin(phase_of(f, loop) + phase0)


@njit(cache=True, fastmath=True)
def _hosc(ph, f, amps, sr):
    n = len(ph)
    nh = len(amps)
    y = np.zeros(n)
    for i in range(n):
        lim = int(0.45 * sr / max(f[i], 1.0))
        if lim > nh:
            lim = nh
        s = 0.0
        for k in range(1, lim + 1):
            s += amps[k - 1] * np.sin(k * ph[i])
        y[i] = s
    return y


def hosc(f, n=None, nh=40, rolloff=1.0, odd=False, loop=False, phase0=0.0, even=1.0):
    """band-limited harmonic oscillator (additive).  rolloff 1 = sawtooth, odd=True with rolloff 1 ~ square,
    even<1 attenuates even harmonics."""
    f = _curve(f, n) if n else np.asarray(f, dtype=np.float64)
    k = np.arange(1, nh + 1, dtype=np.float64)
    amps = k ** (-rolloff)
    if odd:
        amps = amps * (k % 2)
    else:
        amps = amps * np.where(k % 2 == 0, even, 1.0)
    ph = phase_of(f, loop) + phase0
    y = _hosc(ph, f, amps, float(SR))
    return y / (np.max(np.abs(amps)) * 1.0 + 1e-9)


def fm(fc, ratio, index, n=None, loop=False):
    """2-operator FM: carrier fc (Hz or curve), modulator = fc*ratio, index (curve or scalar)"""
    fc = _curve(fc, n) if n else np.asarray(fc, dtype=np.float64)
    ix = _curve(index, len(fc))
    pm = phase_of(fc * ratio, loop)
    pc = phase_of(fc, loop)
    return np.sin(pc + ix * np.sin(pm))


def additive(freqs, amps, n, phases=None, r=None):
    """sum of steady sinusoids"""
    t = T(n)
    y = np.zeros(n)
    for i, (f, a) in enumerate(zip(freqs, amps)):
        ph = (phases[i] if phases is not None else (r.uniform(0, TWO_PI) if r is not None else 0.0))
        y += a * np.sin(TWO_PI * f * t + ph)
    return y


def modal(freqs, decays, amps, dur, r=None, detune=0.0, beat=0.0, attack=0.0005):
    """Struck body: bank of damped sinusoids (the modal-resonator model of an impact).
    decays are time constants (s); detune = random +-cents per mode; beat adds a second near-degenerate partner of
    each mode (ratio 1+beat) for the slow beating of real plates and bells."""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    for i, (f, d, a) in enumerate(zip(freqs, decays, amps)):
        if f >= 0.47 * SR:
            continue
        if r is not None and detune:
            f = f * 2 ** (r.uniform(-detune, detune) / 1200.0)
        ph = r.uniform(0, TWO_PI) if r is not None else 0.0
        e = np.exp(-t / d) * np.minimum(1.0, t / attack) if attack else np.exp(-t / d)
        y += a * e * np.sin(TWO_PI * f * t + ph)
        if beat:
            y += 0.7 * a * e * np.sin(TWO_PI * f * (1 + beat) * t + ph + 1.3)
    return endfade(y)


# ------------------------------------------------------------------ placing, mixing, stereo
def place(buf, sig, t, gain=1.0, pan=None, wrap=False):
    """add sig into buf at time t seconds. buf (n,) or (n,2); sig (m,) or (m,2). pan -1..1 for mono -> stereo.
    wrap=True wraps the tail around the end (for loops)."""
    n = len(buf)
    i0 = int(round(t * SR))
    sig = np.asarray(sig, dtype=np.float64)
    if buf.ndim == 2 and sig.ndim == 1:
        p = 0.0 if pan is None else pan
        a = (p + 1) * np.pi / 4
        sig = np.stack([sig * np.cos(a), sig * np.sin(a)], axis=1)
    elif buf.ndim == 1 and sig.ndim == 2:
        sig = sig.mean(axis=1)
    m = len(sig)
    if wrap:
        i0 %= n
        pos = 0
        while pos < m:
            take = min(m - pos, n - i0)
            buf[i0:i0 + take] += gain * sig[pos:pos + take]
            pos += take
            i0 = 0
    else:
        if i0 >= n or i0 + m <= 0:
            return buf
        s0 = max(0, -i0)
        e0 = min(m, n - i0)
        seg = sig[s0:e0]
        if m > 800 and e0 == m and i0 + m < n - 16:        # a segment that ends inside the buffer: never end on a click
            seg = seg.copy()
            k = int(0.006 * SR)
            seg[-k:] *= (np.linspace(1.0, 0.0, k)[:, None] if seg.ndim == 2 else np.linspace(1.0, 0.0, k))
        buf[i0 + s0:i0 + e0] += gain * seg
    return buf


def pan(x, p):
    a = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def decor(x, r, amount=1.0, spacing=120.0):
    """phase-decorrelated copy of x: same magnitude spectrum, smoothly randomised phase across frequency"""
    n = len(x)
    X = rfft(x)
    f = rfftfreq(n, 1 / SR)
    k = max(4, int(f[-1] / spacing))
    xs = np.linspace(0, f[-1], k)
    ph = CubicSpline(xs, r.uniform(-np.pi, np.pi, k))(f) * amount
    return irfft(X * np.exp(1j * ph), n)


def widen(x, r, width=0.6):
    """mono -> stereo: centre plus a decorrelated side component"""
    d = decor(x, r)
    return np.stack([x + width * d, x - width * d], axis=1) / np.sqrt(1 + width ** 2)


def stereo_noise(n, r, slope=-3.0, lo=20.0, hi=None, corr=0.0):
    a = colored(n, r, slope, lo, hi)
    b = colored(n, r, slope, lo, hi)
    if corr:
        b = corr * a + np.sqrt(1 - corr ** 2) * b
    return np.stack([a, b], axis=1)


def haas(x, ms=12.0, side=1):
    d = N(ms / 1000.0)
    y = np.roll(x, d)
    y[:d] = 0
    return np.stack([x, y], axis=1) if side == 1 else np.stack([y, x], axis=1)


def ms_width(x, w):
    """scale stereo width (1 = unchanged)"""
    m = (x[:, 0] + x[:, 1]) / 2
    s = (x[:, 0] - x[:, 1]) / 2 * w
    return np.stack([m + s, m - s], axis=1)


# ------------------------------------------------------------------ spaces
_IR_CACHE = {}


def make_ir(rt_lo, rt_hi, predelay=0.0, build=0.03, early=8, early_span=0.06, stereo=True, seed=1, echoes=None,
            lp_hz=None, length=None, low_cut=30.0, density=1.0):
    """Synthetic room impulse response.
    rt_lo/rt_hi: RT60 (s) below 250 Hz / above 4 kHz (log-interpolated in between)
    echoes: list of (delay_s, gain, lowpass_hz) discrete reflections (open city, canyon)
    returns (m,2) unit-energy IR (or (m,) if stereo False)."""
    key = (rt_lo, rt_hi, predelay, build, early, early_span, stereo, seed, tuple(map(tuple, echoes or [])), lp_hz, length, low_cut, density)
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    r = np.random.default_rng(seed)
    rt_max = max(rt_lo, rt_hi)
    m = N(length if length else rt_max * 1.15 + predelay + 0.2)
    ch = 2 if stereo else 1
    t = T(m)
    centres = np.array([63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000], dtype=float)
    rts = np.interp(np.log10(centres), np.log10([250, 4000]), [rt_lo, rt_hi])
    out = np.zeros((m, ch))
    f = rfftfreq(m, 1 / SR)
    for c in range(ch):
        wn = r.standard_normal(m)
        if density < 1.0:                       # sparser, grainier tails
            wn = wn * (r.random(m) < density) / np.sqrt(density)
        W = rfft(wn)
        acc = np.zeros(m)
        for fc, rt in zip(centres, rts):
            w = np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / fc) / 0.75) ** 2)
            if fc == centres[0]:
                w = np.where(f < fc, 1.0, w)
            if fc == centres[-1]:
                w = np.where(f > fc, 1.0, w)
            band = irfft(W * w, m)
            acc += band * np.exp(-6.9078 * t / rt)
        out[:, c] = acc
    bd = np.minimum(1.0, 1 - np.exp(-t / max(build, 1e-3)))
    out *= bd[:, None]
    # early reflections
    for c in range(ch):
        for i in range(early):
            td = r.uniform(0.003, early_span) * (0.4 + i / max(early, 1))
            k = int(td * SR)
            if k < m:
                out[k, c] += r.choice([-1, 1]) * r.uniform(0.5, 1.0) * np.exp(-td / (early_span * 1.5)) * np.sqrt(np.mean(out[:, c] ** 2)) * 6
    if echoes:
        for d, g, lf in echoes:
            for c in range(ch):
                k = int((d + r.uniform(-0.02, 0.02) * (c)) * SR)
                ln = min(m - k, N(0.25))
                if ln > 100:
                    burst = r.standard_normal(ln) * np.exp(-np.arange(ln) / (0.05 * SR))
                    burst = lp(burst, lf, 2) if lf else burst
                    out[k:k + ln, c] += g * burst / (np.sqrt(np.mean(burst ** 2)) + 1e-9) * np.sqrt(np.mean(out[:, c] ** 2)) * 8
    if lp_hz:
        out = lp(out, lp_hz, 2)
    out = hp(out, low_cut, 1)
    if predelay:
        out = np.concatenate([np.zeros((N(predelay), ch)), out])
    out /= np.sqrt(np.sum(out ** 2, axis=0, keepdims=True).mean()) + 1e-12
    if not stereo:
        out = out[:, 0]
    _IR_CACHE[key] = out
    return out


def conv(x, ir):
    """full convolution; mono x with stereo ir gives stereo.  Output length len(x)+len(ir)-1."""
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1 and ir.ndim == 1:
        return sg.fftconvolve(x, ir)
    if x.ndim == 1 and ir.ndim == 2:
        return np.stack([sg.fftconvolve(x, ir[:, c]) for c in range(ir.shape[1])], axis=1)
    if x.ndim == 2 and ir.ndim == 2:
        return np.stack([sg.fftconvolve(x[:, c], ir[:, c]) for c in range(2)], axis=1)
    return np.stack([sg.fftconvolve(x[:, c], ir) for c in range(2)], axis=1)


def reverb(x, ir, wet=0.3, dry=1.0, n=None):
    """dry + wet*convolution, padded/trimmed to n samples (default: len(x) + tail)"""
    x = np.asarray(x, dtype=np.float64)
    y = conv(x, ir)
    n = n or len(y)
    out_ch = y.ndim
    xd = fit(x, n)
    if out_ch == 2 and xd.ndim == 1:
        xd = np.stack([xd, xd], axis=1)
    return dry * xd + wet * fit(y, n)


def cconv(x, ir):
    """circular convolution (loops): the IR tail wraps around to the start.  x mono/stereo, ir mono/stereo."""
    n = len(x)
    if len(ir) > n:                                   # fold a too-long IR
        k = int(np.ceil(len(ir) / n))
        irp = np.zeros((k * n,) + ir.shape[1:])
        irp[:len(ir)] = ir
        ir = irp.reshape((k, n) + ir.shape[1:]).sum(axis=0)
    else:
        irp = np.zeros((n,) + ir.shape[1:])
        irp[:len(ir)] = ir
        ir = irp
    if x.ndim == 1 and ir.ndim == 1:
        return irfft(rfft(x) * rfft(ir), n)
    if x.ndim == 1:
        X = rfft(x)
        return np.stack([irfft(X * rfft(ir[:, c]), n) for c in range(ir.shape[1])], axis=1)
    if ir.ndim == 1:
        return np.stack([irfft(rfft(x[:, c]) * rfft(ir), n) for c in range(x.shape[1])], axis=1)
    return np.stack([irfft(rfft(x[:, c]) * rfft(ir[:, c]), n) for c in range(2)], axis=1)


SPACES = {
    # name: kwargs for make_ir
    "capsule": dict(rt_lo=0.16, rt_hi=0.05, early=10, early_span=0.008, seed=11, lp_hz=5000),
    "booth": dict(rt_lo=0.35, rt_hi=0.2, early=10, early_span=0.02, seed=12),
    "room": dict(rt_lo=0.55, rt_hi=0.35, early=12, early_span=0.03, seed=13),
    "corridor": dict(rt_lo=1.5, rt_hi=0.9, early=14, early_span=0.05, predelay=0.004, seed=14),
    "tunnel": dict(rt_lo=3.0, rt_hi=1.4, early=16, early_span=0.07, seed=15),
    "flooded": dict(rt_lo=4.2, rt_hi=0.8, build=0.06, early=6, early_span=0.05, seed=16, lp_hz=2500),
    "hangar": dict(rt_lo=7.5, rt_hi=3.2, build=0.08, predelay=0.03, early=18, early_span=0.12, seed=17),
    "cavern": dict(rt_lo=9.0, rt_hi=4.5, build=0.12, predelay=0.06, early=18, early_span=0.2, seed=18, lp_hz=9000),
    "hall": dict(rt_lo=3.6, rt_hi=2.4, build=0.05, predelay=0.02, early=14, early_span=0.08, seed=19),
    "cathedral": dict(rt_lo=6.5, rt_hi=3.8, build=0.09, predelay=0.04, early=16, early_span=0.15, seed=20, lp_hz=11000),
    "city": dict(rt_lo=1.3, rt_hi=0.6, build=0.1, predelay=0.02, early=4, early_span=0.1, seed=21,
                 echoes=[(0.16, 0.5, 2500), (0.34, 0.4, 1800), (0.61, 0.28, 1200), (1.0, 0.16, 900)], density=0.5, low_cut=110),
    "open": dict(rt_lo=0.7, rt_hi=0.3, build=0.15, predelay=0.01, early=2, early_span=0.05, seed=22,
                 echoes=[(0.3, 0.35, 1500), (0.9, 0.2, 900), (1.6, 0.1, 700)], density=0.4, low_cut=100),
    "plate": dict(rt_lo=2.2, rt_hi=1.8, build=0.02, early=6, early_span=0.02, seed=23),
    # a giant's voice over a city and a bay: dark, long, with discrete far echoes off the skyscrapers and the hills
    "roar": dict(rt_lo=2.6, rt_hi=0.9, build=0.07, predelay=0.03, early=6, early_span=0.1, seed=31,
                 echoes=[(0.22, 0.55, 2600), (0.52, 0.42, 1800), (0.93, 0.3, 1300), (1.5, 0.2, 900), (2.3, 0.12, 700)], density=0.5, low_cut=90),
    "bay": dict(rt_lo=1.6, rt_hi=0.5, build=0.1, predelay=0.015, early=4, early_span=0.08, seed=32,
                echoes=[(0.35, 0.3, 1800), (0.8, 0.2, 1100), (1.4, 0.12, 800)], density=0.5, low_cut=100),
    # under the sea: everything dull and slow
    "deep": dict(rt_lo=3.5, rt_hi=0.5, build=0.2, early=4, early_span=0.1, seed=33, lp_hz=900),
}


def space(name, stereo=True):
    kw = dict(SPACES[name])
    kw["stereo"] = stereo
    return make_ir(**kw)


# ------------------------------------------------------------------ dynamics and loudness
@njit(cache=True)
def _release(g, c):
    out = g.copy()
    for i in range(1, len(g)):
        rec = out[i - 1] + (1.0 - out[i - 1]) * c
        out[i] = min(g[i], rec)
    return out


def limit(x, ceiling_db=-1.2, look_ms=2.5, release_ms=120.0):
    """look-ahead peak limiter (oversample-free; keep ceiling a bit under the true-peak target)"""
    c = float(db(ceiling_db))
    a = np.max(np.abs(x), axis=1) if x.ndim == 2 else np.abs(x)
    need = np.minimum(1.0, c / np.maximum(a, 1e-9))
    w = max(3, N(look_ms / 1000.0))
    g = minimum_filter1d(need, size=2 * w + 1, mode="nearest")
    g = uniform_filter1d(g, size=2 * w + 1, mode="nearest")
    g = _release(g, 1.0 / (release_ms / 1000.0 * SR))
    g = np.minimum(g, need)           # never exceed the instantaneous requirement
    return x * (g[:, None] if x.ndim == 2 else g)


def softclip(x, drive=1.0):
    return np.tanh(x * drive) / np.tanh(drive)


def sat(x, amount=2.0):
    """asymmetric-ish saturation for weight (adds low harmonics so subs read on small speakers)"""
    return np.tanh(x * amount + 0.05 * amount * x * x) / np.tanh(amount)


def lufs(x):
    x = np.asarray(x, dtype=np.float64)
    if len(x) < N(0.5):
        x = fit(x, N(0.5))
    meter = pyln.Meter(SR)
    try:
        v = meter.integrated_loudness(x)
    except Exception:
        return -120.0
    return float(v) if np.isfinite(v) else -120.0


def true_peak_db(x):
    y = sg.resample_poly(np.asarray(x, dtype=np.float64), 4, 1, axis=0)
    return float(todb(np.max(np.abs(y))))


def sample_peak_db(x):
    return float(todb(np.max(np.abs(x))))


def rms_db(x):
    return float(todb(np.sqrt(np.mean(np.asarray(x) ** 2))))


# ------------------------------------------------------------------ small building blocks used by many sounds
def burst(dur, r, lo=200, hi=8000, tau=0.05, attack=0.0005, order=2):
    """band-limited noise burst with exponential decay"""
    n = N(dur)
    x = white(n, r)
    x = bp(x, lo, hi, order) if lo > 20 else lp(x, hi, order)
    return endfade(x * expo(n, tau, attack), 5.0)


def thump(dur, f0, f1, tau_f=0.08, tau_a=0.3, attack=0.002, harm=0.15):
    """pitch-dropping sine (kick / sub hit); a touch of 2nd and 3rd harmonic so it carries on small speakers"""
    n = N(dur)
    t = T(n)
    f = f1 + (f0 - f1) * np.exp(-t / tau_f)
    ph = phase_of(f)
    y = np.sin(ph) + harm * np.sin(2 * ph + 0.4) + harm * 0.5 * np.sin(3 * ph)
    return endfade(y * expo(n, tau_a, attack), 5.0)


def ticks(dur, r, rate, lo=1500, hi=7000, tau=0.004, rate_end=None, shape=1.0, gains=(0.3, 1.0)):
    """Poisson clicks (debris, dust, crackle) each a short ringing burst; rate may fall to rate_end over the duration"""
    n = N(dur)
    out = np.zeros(n)
    rate_end = rate if rate_end is None else rate_end
    t = 0.0
    while t < dur:
        rr = rate_end + (rate - rate_end) * (1 - t / dur) ** shape
        t += r.exponential(1.0 / max(rr, 1e-3))
        if t >= dur:
            break
        f = np.exp(r.uniform(np.log(lo), np.log(hi)))
        ln = N(tau * 6)
        tt = np.arange(ln) / SR
        click = np.sin(TWO_PI * f * tt + r.uniform(0, 6.28)) * np.exp(-tt / (tau * r.uniform(0.5, 1.5)))
        i = int(t * SR)
        m = min(ln, n - i)
        out[i:i + m] += r.uniform(*gains) * click[:m]
    return endfade(out, 10.0)


def whoosh(dur, r, f0, f1, q=1.2, shape=None, amp=None, mode="bp"):
    """noise through a moving resonant filter; f0->f1 geometric unless a curve is given"""
    n = N(dur)
    x = white(n, r)
    if shape is None:
        f = np.exp(np.linspace(np.log(f0), np.log(f1), n))
    else:
        f = shape
    y = svf(x, f, q, mode)
    if amp is not None:
        y = y * amp
    return y


def rumble(dur, r, hi=120, slope=-6.0, mod_rate=0.7, mod_depth=0.6, lo=18):
    n = N(dur)
    x = colored(n, r, slope, lo=lo, hi=hi)
    return x * swell(n, r, mod_rate, mod_depth, periodic=False)


def air_absorb(x, distance_s, r=None):
    """time-varying lowpass: sound gets duller as the tail progresses (distance / air absorption)"""
    n = len(x)
    t = T(n)
    f = 14000.0 * np.exp(-t / max(distance_s, 1e-3)) + 300.0
    return svf(x, f, 0.6, "lp")


# ------------------------------------------------------------------ loop-safe helpers and level helpers
def csvf(x, f, q=0.707, mode="lp"):
    """svf on a periodic signal (moving cutoff must itself be periodic): result is periodic"""
    n = len(x)
    ff = f if np.isscalar(f) else tile3(np.asarray(f))
    qq = q if np.isscalar(q) else tile3(np.asarray(q))
    return svf(tile3(x) if x.ndim == 1 else np.concatenate([x, x, x]), ff, qq, mode)[n:2 * n]


def fold(y, n):
    """wrap anything past n back onto the start (event tails in a loop)"""
    out = y[:n].copy()
    k = 1
    while k * n < len(y):
        seg = y[k * n:(k + 1) * n]
        out[:len(seg)] += seg
        k += 1
    return out


def rn(x, level_db):
    """scale so the rms is level_db (dBFS)"""
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12) * db(level_db)


def pk(x, level_db):
    """scale so the sample peak is level_db"""
    return x / (np.max(np.abs(x)) + 1e-12) * db(level_db)


def mix(*sigs):
    """sum signals of different lengths (zero-padded to the longest)"""
    m = max(len(s) for s in sigs)
    out = np.zeros((m,) + sigs[0].shape[1:])
    for s in sigs:
        out[:len(s)] += s
    return out