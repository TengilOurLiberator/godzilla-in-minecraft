"""analyze.py - look at and check every rendered file (the decoded .ogg, i.e. what the game will play).

    python analyze.py                    # all events: preview/<file>.png + report.md/json
    python analyze.py godzilla.roar mothra   # just these (event names or prefixes)
    python analyze.py --sheet godzilla music # also write preview/_sheet_<prefix>_N.png contact sheets
    python analyze.py --no-images            # checks only

Each preview: waveform (L blue / R orange), short-term level in dB, a log-frequency spectrogram (30 Hz-20 kHz, 80 dB), and for
loops a zoom of the join.  Checks: clipping, true peak, DC, lead/tail silence, truncated tails, loop length in whole ticks, loop
join click ratio, loop join level jump, mono/stereo as specified, music hit time (rise), crossfade compatibility.
"""
import argparse
import json
import os
import sys

import numpy as np
import scipy.signal as sg
import soundfile as sf
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import dsp
import reg
import make_audio as ma

PREVIEW = os.path.join(HERE, "preview")
STOPS = [(0.0, (0, 0, 4)), (0.14, (31, 12, 72)), (0.29, (85, 15, 109)), (0.43, (136, 34, 106)), (0.57, (186, 54, 85)),
         (0.71, (227, 89, 51)), (0.86, (249, 140, 10)), (1.0, (252, 255, 164))]


def font(size=12):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def cmap(v):
    v = np.clip(v, 0, 1)
    xs = [s[0] for s in STOPS]
    out = np.zeros(v.shape + (3,), dtype=np.uint8)
    for c in range(3):
        out[..., c] = np.interp(v, xs, [s[1][c] for s in STOPS]).astype(np.uint8)
    return out


def spectrogram(x, width, height=260, fmin=30, fmax=20000, rng=80.0):
    m = dsp.mono(x)
    nfft = 4096
    hop = max(256, (len(m) - nfft) // max(width, 1))
    win = np.hanning(nfft)
    if len(m) < nfft:
        m = np.pad(m, (0, nfft - len(m)))
    nfr = (len(m) - nfft) // hop + 1
    idx = np.arange(nfft)[None, :] + hop * np.arange(nfr)[:, None]
    S = np.abs(np.fft.rfft(m[idx] * win, axis=1)) ** 2 / (win ** 2).sum()
    f = np.fft.rfftfreq(nfft, 1 / dsp.SR)
    rows = np.exp(np.linspace(np.log(fmax), np.log(fmin), height))
    pos = np.interp(rows, f, np.arange(len(f)))
    lo = np.floor(pos).astype(int)
    hi = np.minimum(lo + 1, len(f) - 1)
    w = pos - lo
    img = S[:, lo] * (1 - w) + S[:, hi] * w
    d = 10 * np.log10(img + 1e-14)
    ref = np.percentile(d, 99.9)
    d = (d - (ref - rng)) / rng
    d = d.T
    cols = np.minimum((np.arange(width) * d.shape[1] / width).astype(int), d.shape[1] - 1)
    return cmap(d[:, cols])


def waveform(x, width, height=110):
    img = Image.new("RGB", (width, height), (14, 14, 18))
    dr = ImageDraw.Draw(img)
    chans = [x] if x.ndim == 1 else [x[:, 0], x[:, 1]]
    n = len(chans[0])
    cols = [(90, 160, 255), (255, 170, 70)]
    dr.line([(0, height // 2), (width, height // 2)], fill=(40, 40, 50))
    for c, ch in enumerate(chans):
        edges = np.linspace(0, n, width + 1).astype(int)
        for i in range(width):
            seg = ch[edges[i]:max(edges[i] + 1, edges[i + 1])]
            if len(seg) == 0:
                continue
            a, b = seg.min(), seg.max()
            dr.line([(i, height / 2 - b * height / 2 * 0.95), (i, height / 2 - a * height / 2 * 0.95)], fill=cols[c])
    return img


def level_plot(x, width, height=60):
    img = Image.new("RGB", (width, height), (14, 14, 18))
    dr = ImageDraw.Draw(img)
    m = dsp.mono(x)
    win = dsp.N(0.1)
    nb = max(1, len(m) // win)
    e = 10 * np.log10(np.mean(m[:nb * win].reshape(nb, win) ** 2, axis=1) + 1e-12)
    for g in (-60, -40, -20):
        y = height * (-g) / 80.0
        dr.line([(0, y), (width, y)], fill=(40, 40, 55))
    pts = [(i * width / max(nb - 1, 1), min(height - 1, max(0, height * (-v) / 80.0))) for i, v in enumerate(e)]
    if len(pts) > 1:
        dr.line(pts, fill=(120, 230, 140))
    return img


def join_plot(x, width, height=70):
    img = Image.new("RGB", (width, height), (14, 14, 18))
    dr = ImageDraw.Draw(img)
    k = dsp.N(0.025)
    m = x if x.ndim == 1 else x[:, 0]
    seg = np.concatenate([m[-k:], m[:k]])
    sc = max(np.max(np.abs(seg)), 1e-6)
    pts = [(i * width / len(seg), height / 2 - seg[i] / sc * height / 2 * 0.9) for i in range(len(seg))]
    dr.line(pts, fill=(250, 220, 90))
    dr.line([(width / 2, 0), (width / 2, height)], fill=(255, 60, 60))
    return img


def render(label, y, loop, path, width=900):
    parts = [waveform(y, width), level_plot(y, width), Image.fromarray(spectrogram(y, width))]
    if loop:
        parts.append(join_plot(y, width))
    H = 24 + sum(p.height for p in parts) + 4 * len(parts)
    img = Image.new("RGB", (width, H), (0, 0, 0))
    ImageDraw.Draw(img).text((6, 4), label, fill=(235, 235, 235), font=font(13))
    yy = 24
    for p in parts:
        img.paste(p, (0, yy))
        yy += p.height + 4
    img.save(path)
    return img


def db_(v):
    return 10 ** (v / 20.0)


def frames_db(m, win_s=0.05):
    w = dsp.N(win_s)
    nb = len(m) // w
    return 10 * np.log10(np.mean(m[:nb * w].reshape(nb, w) ** 2, axis=1) + 1e-14)


def checks(ev, var, y, rec, man_ev):
    sr = rec["sample_rate"]
    m = dsp.mono(y)
    res, flags = {}, []
    res["peak_db"] = round(dsp.sample_peak_db(y), 2)
    res["true_peak_db"] = round(dsp.true_peak_db(y), 2)
    res["lufs"] = round(dsp.lufs(y), 2)
    res["dc"] = round(float(np.max(np.abs(np.mean(y, axis=0)))), 6)
    res["clipped_samples"] = int(np.sum(np.abs(y) >= 0.999))
    ch = 1 if y.ndim == 1 else y.shape[1]
    res["channels"] = ch
    if res["true_peak_db"] > -1.0:
        flags.append("true peak above -1 dBFS")
    if res["clipped_samples"]:
        flags.append("clipping")
    if res["dc"] > 0.003:
        flags.append("DC offset")
    if ev.stereo != (ch == 2):
        flags.append(f"channels {ch} but stereo={ev.stereo}")
    if ev.group == "music" and not ev.stream:
        flags.append("music not streamed")
    ab = np.abs(m)
    nz = np.where(ab > db_(-60))[0]
    if len(nz) == 0:
        flags.append("SILENT")
    else:
        res["lead_silence_s"] = round(nz[0] / sr, 3)
        res["tail_silence_s"] = round((len(m) - 1 - nz[-1]) / sr, 3)
        if res["lead_silence_s"] > 0.15 and not (ev.group == "music"):
            flags.append(f"leading silence {res['lead_silence_s']} s")
    if ev.loop:
        hpf = sg.butter(4, 2000.0 / (dsp.SR / 2), "high", output="sos")
        k = 8192
        seg = np.concatenate([m[-k:], m[:k]])
        hs = np.abs(sg.sosfiltfilt(hpf, seg))
        ref = max(float(np.percentile(np.abs(sg.sosfilt(hpf, m)), 99.9)), db_(-80))
        jp = float(hs[k - dsp.N(0.0005):k + dsp.N(0.0005)].max())
        res["join_hf_ratio"] = round(jp / ref, 2)
        d_all = np.abs(np.diff(m))
        res["join_step_ratio"] = round(float(abs(m[0] - m[-1]) / (np.percentile(d_all, 99.9) + 1e-9)), 2)
        tk_ = rec["seconds"] * 20.0
        res["loop_ticks"] = round(tk_, 3)
        if abs(tk_ - round(tk_)) > 1e-2:
            flags.append(f"loop length {rec['seconds']} s is not a whole number of ticks ({tk_:.2f})")
        w = dsp.N(0.1)
        yy = y if y.ndim == 2 else y[:, None]
        a_ = 10 * np.log10(np.mean(yy[-w:] ** 2) + 1e-14)
        b_ = 10 * np.log10(np.mean(yy[:w] ** 2) + 1e-14)
        res["join_level_diff_db"] = round(float(a_ - b_), 2)
        e20 = dsp.N(0.02)
        l_first = 10 * np.log10(np.mean(yy[:e20] ** 2) + 1e-14)
        l_last = 10 * np.log10(np.mean(yy[-e20:] ** 2) + 1e-14)
        onset = (l_first - l_last) > 12.0
        res["join_onset_db"] = round(float(l_first - l_last), 1)
        res["join_note"] = "downbeat/onset at the join (by design)" if onset else "ok"
        if res["join_hf_ratio"] > 1.3 and res["join_step_ratio"] > 1.0 and not onset:
            flags.append(f"loop join click (hf ratio {res['join_hf_ratio']})")
            res["join_note"] = "check"
        if abs(res["join_level_diff_db"]) > 9.0 and not onset:
            flags.append("loop join level jump")
    else:
        from scipy.ndimage import maximum_filter1d
        a_ = np.abs(m)
        pre = maximum_filter1d(a_, size=101, origin=50)
        post = maximum_filter1d(a_, size=101, origin=-50)
        d_ = np.abs(np.diff(m, append=m[-1]))
        cand = np.where((d_ > 0.3 * pre) & (post < 0.1 * pre) & (pre > db_(-50)))[0]
        cand = [int(i) for i in cand if i < len(m) - dsp.N(0.03)]
        if cand:
            ts = sorted(set(round(i / sr, 2) for i in cand))
            res["cut_times_s"] = ts[:8]
            flags.append("abrupt cut at " + ", ".join(str(x) for x in ts[:4]) + " s")
        if res.get("tail_silence_s", 0) > 1.0:
            flags.append(f"long tail silence {res['tail_silence_s']} s")
    if ev.name == "music.rise":
        e = frames_db(m, 0.02)
        de = np.diff(e)
        i0, i1 = int(9.0 / 0.02), int(15.0 / 0.02)
        k = int(np.argmax(de[i0:i1])) + i0
        res["hit_steepest_rise_s"] = round((k + 1) * 0.02, 2)
        res["hit_expected_s"] = man_ev.get("hit_s")
        res["hit_rise_db"] = round(float(e[min(len(e) - 1, k + 15)] - np.mean(e[max(0, k - 25):k - 2])), 1)
        if abs(res["hit_steepest_rise_s"] - (man_ev.get("hit_s") or 0)) > 0.12:
            flags.append("rise hit not at hit_s")
    res["flags"] = flags
    return res


def cross_checks(man):
    """music pairs that must be crossfade-compatible: same length, tempo and key"""
    out = []
    pairs = [("music.rampage", "music.rampage_intense")]
    for a, b in pairs:
        if a in man and b in man:
            ok = abs(man[a]["seconds"] - man[b]["seconds"]) < 0.001 and man[a].get("bpm") == man[b].get("bpm") and man[a].get("key") == man[b].get("key")
            out.append(f"{a} / {b}: same length {man[a]['seconds']} vs {man[b]['seconds']}, bpm {man[a].get('bpm')} vs {man[b].get('bpm')}, key {man[a].get('key')} vs {man[b].get('key')} -> {'OK' if ok else 'MISMATCH'}")
            try:
                ya = sf.read(os.path.join(ma.SOUNDS, "music", "rampage.ogg"))[0]
                yb = sf.read(os.path.join(ma.SOUNDS, "music", "rampage_intense.ogg"))[0]
                n = min(len(ya), len(yb))
                ca = np.corrcoef(dsp.mono(ya[:n]), dsp.mono(yb[:n]))[0, 1]
                out.append(f"  waveform correlation rampage vs intense: {ca:.3f} (shared layers are bit-identical before the codec)")
            except Exception as e:
                out.append(f"  (correlation skipped: {e})")
    return out


def rhythm(man, name):
    """onset strength (spectral flux, 60 Hz-8 kHz) folded onto the 16th-note grid of one bar: shows where the accents are"""
    ev = man[name]
    y, sr = sf.read(os.path.join(ma.SOUNDS, *ev["variants"][0]["file"].split("/")) + ".ogg")
    m = dsp.mono(y)
    nfft, hop = 2048, 441
    win = np.hanning(nfft)
    nfr = (len(m) - nfft) // hop
    idx = np.arange(nfft)[None, :] + hop * np.arange(nfr)[:, None]
    S = np.abs(np.fft.rfft(m[idx] * win, axis=1))
    f = np.fft.rfftfreq(nfft, 1 / sr)
    S = np.log1p(30 * S[:, (f > 60) & (f < 8000)])
    flux = np.maximum(0, np.diff(S, axis=0)).sum(axis=1)
    step_s = 60.0 / ev["bpm"] / 4
    t = (np.arange(len(flux)) + 1) * hop / sr
    bar_s = step_s * 16
    pos = ((t % bar_s) / step_s)
    bins = np.zeros(16)
    for k in range(16):
        sel = np.abs(pos - k) < 0.5 if k else ((pos < 0.5) | (pos > 15.5))
        bins[k] = flux[sel].mean() if sel.any() else 0
    bins = bins / bins.max()
    ac = np.correlate(flux - flux.mean(), flux - flux.mean(), "full")[len(flux) - 1:]
    lags = np.arange(len(ac)) * hop / sr
    sel = (lags > 0.3) & (lags < 3.0)
    best = lags[sel][np.argmax(ac[sel])]
    print(f"{name}: strongest periodicity {best:.3f} s (beat = {60.0 / ev['bpm']:.3f} s, bar = {bar_s:.3f} s)")
    print("   accent profile per 16th (1 = strongest): " + " ".join(f"{b:.2f}" for b in bins))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("names", nargs="*")
    ap.add_argument("--sheet", nargs="*")
    ap.add_argument("--no-images", action="store_true")
    ap.add_argument("--rhythm", action="store_true", help="print the accent profile of music events")
    a = ap.parse_args(argv)
    ma.load_modules()
    with open(ma.MANIFEST, encoding="utf-8") as fh:
        man = json.load(fh)
    os.makedirs(PREVIEW, exist_ok=True)
    names = [n for n in sorted(man) if ma.matches(n, a.names)]
    report, sheets = {}, {}
    for n in names:
        ev = reg.EVENTS[n]
        for v in man[n]["variants"]:
            path = os.path.join(ma.SOUNDS, *v["file"].split("/")) + ".ogg"
            if not os.path.exists(path):
                continue
            y, sr = sf.read(path, dtype="float64")
            rec = dict(v, sample_rate=sr)
            key = v["file"]
            report[key] = checks(ev, v, y, rec, man[n])
            if not a.no_images:
                label = f"{key}  {rec['seconds']:.2f}s  {'stereo' if y.ndim == 2 else 'mono'}  {report[key]['lufs']:.1f} LUFS  tp {report[key]['true_peak_db']:.1f}  {'LOOP' if ev.loop else ''}"
                sheets[key] = render(label, y, ev.loop, os.path.join(PREVIEW, key.replace("/", "_") + ".png"))
    for pref in (a.sheet or []):
        items = [k for k in sheets if k.replace("/", ".").startswith(pref) or k.startswith(pref)]
        for pg in range(0, len(items), 6):
            chunk = items[pg:pg + 6]
            ims = [sheets[k].resize((600, int(sheets[k].height * 600 / 900))) for k in chunk]
            rows = (len(ims) + 1) // 2
            hh = max(i.height for i in ims)
            sheet = Image.new("RGB", (1200, hh * rows), (0, 0, 0))
            for i, im in enumerate(ims):
                sheet.paste(im, ((i % 2) * 600, (i // 2) * hh))
            sheet.save(os.path.join(PREVIEW, f"_sheet_{pref.replace('.', '_')}_{pg // 6}.png"))
    rp = os.path.join(PREVIEW, "report.json")
    full = {}
    if os.path.exists(rp) and a.names:
        with open(rp, encoding="utf-8") as fh:
            full = json.load(fh)
    full.update(report)
    with open(rp, "w", encoding="utf-8") as fh:
        json.dump(full, fh, indent=1)
    lines = ["| file | s | ch | lufs | tp | loop join (hf ratio, level dB, onset dB) | flags |", "|---|---|---|---|---|---|---|"]
    for k, v in report.items():
        j = f"{v.get('join_hf_ratio')}, step {v.get('join_step_ratio')}, {v.get('join_level_diff_db')}, {v.get('join_onset_db')}" if "join_hf_ratio" in v else ""
        sec = [x for n in names for x in man[n]["variants"] if x["file"] == k][0]["seconds"]
        lines.append(f"| {k} | {sec:.2f} | {v['channels']} | {v['lufs']} | {v['true_peak_db']} | {j} | {'; '.join(v['flags'])} |")
    with open(os.path.join(PREVIEW, "report.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    bad = {k: v for k, v in report.items() if v["flags"]}
    for k, v in bad.items():
        print(f"FLAG {k}: {'; '.join(v['flags'])}")
    if a.rhythm:
        for n in names:
            if man[n].get("bpm"):
                rhythm(man, n)
    for line in cross_checks(man):
        print(line)
    print(f"\n{len(report)} files analysed, {len(bad)} with flags")


if __name__ == "__main__":
    main()
