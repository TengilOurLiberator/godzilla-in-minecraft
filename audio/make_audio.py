"""Build all sound and music for Godzilla VR.  Everything is synthesised here (numpy/scipy/numba); nothing is sampled.

    python make_audio.py                         # everything, in parallel
    python make_audio.py godzilla.roar mothra    # just these events, or any name prefix (group, group.name)
    python make_audio.py --list                  # what exists, with level targets
    python make_audio.py --jobs 4 ...            # worker processes (default: cpu count - 2)
    python make_audio.py --table                 # print the manifest as a markdown table
    python make_audio.py --clean                 # delete out/ first (use with a full build)

Outputs (all under audio/out):
    sounds/<group>/<name>[_N].ogg   44.1 kHz Vorbis; sfx are mono, music and beds stereo
    sounds.json                     Minecraft sounds.json for the namespace `godzilla` (one entry per event, "godzilla:<group>/<file>")
    manifest.json                   per event: seconds, ticks, loop, loudness, variants, bpm and loop points for music
"""
import argparse
import glob
import importlib
import json
import multiprocessing as mp
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.join(HERE, "out")
SOUNDS = os.path.join(OUT, "sounds")
SOUNDS_JSON = os.path.join(OUT, "sounds.json")
MANIFEST = os.path.join(OUT, "manifest.json")
NAMESPACE = "godzilla"
MODULES = ["sfx_godzilla", "sfx_destruction", "sfx_city", "sfx_military", "sfx_mothra", "sfx_ui",
           "music_rise_misc", "music_rampage", "music_mothra"]
TP_CEILING = -1.25          # pre-encode true-peak ceiling, dBFS; the decoded ogg is checked against -1.0

import numpy as np
import soundfile as sf
import dsp
from dsp import SR
import reg


def load_modules(verbose=False):
    for m in MODULES:
        try:
            importlib.import_module(m)
        except ModuleNotFoundError as e:
            if e.name != m:
                raise
            if verbose:
                print(f"(module {m} not written yet, skipped)")


def find_tool(name):
    p = shutil.which(name)
    if p:
        return p
    for pat in (rf"D:\malmo-vr\tools\ffmpeg\*\bin\{name}.exe", rf"D:\*\tools\ffmpeg\*\bin\{name}.exe", rf"C:\ffmpeg\bin\{name}.exe"):
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    raise FileNotFoundError(f"{name} not found on PATH or in the known ffmpeg folders")


def out_path(ev, i):
    return os.path.join(SOUNDS, ev.group, ev.file_short(i) + ".ogg")


def encode(x, path, q):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    sf.write(tmp, x.astype(np.float32), SR, subtype="FLOAT")
    try:
        subprocess.run([find_tool("ffmpeg"), "-v", "error", "-y", "-fflags", "+bitexact", "-i", tmp, "-map_metadata", "-1", "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", str(q), path], check=True)
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass


def finalize(ev, x):
    x = np.nan_to_num(np.asarray(x, dtype=np.float64))
    if ev.stereo and x.ndim == 1:
        x = np.stack([x, x], axis=1)
    if not ev.stereo and x.ndim == 2:
        x = x.mean(axis=1)
    if ev.loop:
        x = x - x.mean(axis=0)
    else:
        if np.max(np.abs(x.mean(axis=0))) > 0.0005 * np.max(np.abs(x)):
            x = dsp.hp(x, 12, 1)
    if np.max(np.abs(x)) < 1e-6:
        raise ValueError(f"{ev.name}: silent")
    if not ev.raw:
        gain_db = (ev.lufs - dsp.lufs(x)) if ev.lufs is not None else (ev.peak - dsp.sample_peak_db(x))
        x = x * dsp.db(gain_db)
    fin = ev.fin if ev.fin is not None else (0.0 if ev.loop else 0.0008)
    fout = ev.fout if ev.fout is not None else (0.0 if ev.loop else 0.02)
    x = dsp.fade(x, fin, fout, "lin")
    ceil = ev.ceiling if ev.ceiling is not None else TP_CEILING
    tp = dsp.true_peak_db(x)
    if tp > ceil:
        if ev.limit and not ev.loop:
            x = dsp.limit(x, ceil - 0.15)
        x = x * dsp.db(min(0.0, ceil - dsp.true_peak_db(x)))
    return x


def build_one(job):
    name, i = job
    ev = reg.EVENTS[name]
    t0 = time.time()
    r = np.random.default_rng(ev.seed(i))
    res = ev.fn(r, i)
    meta = {}
    if isinstance(res, tuple):
        res, meta = res
    x = finalize(ev, res)
    path = out_path(ev, i)
    for attempt in range(4):
        encode(x, path, ev.q)
        y, sr = sf.read(path, dtype="float64", always_2d=False)
        tp = dsp.true_peak_db(y)
        if tp <= -1.0:
            break
        x = x * dsp.db(-(tp + 1.15))
    n = len(y)
    rec = dict(name=name, index=i, file=f"{ev.group}/{ev.file_short(i)}", path=os.path.relpath(path, HERE).replace("\\", "/"),
               seconds=round(n / sr, 4), samples=n, ticks=round(n / sr * 20, 2),
               channels=1 if y.ndim == 1 else y.shape[1], sample_rate=sr,
               lufs=round(dsp.lufs(y), 2), true_peak=round(tp, 2), sample_peak=round(dsp.sample_peak_db(y), 2),
               bytes=os.path.getsize(path), build_s=round(time.time() - t0, 1))
    rec.update(meta)
    return rec


def matches(name, pats):
    return not pats or any(name == p or name.startswith(p if p.endswith(".") else p) for p in pats)


def _worker(job):
    load_modules()
    try:
        return build_one(job)
    except Exception as e:
        import traceback
        return dict(name=job[0], index=job[1], error=f"{type(e).__name__}: {e}", tb=traceback.format_exc())


def event_entry(ev, files):
    sounds = []
    for f in files:
        e = {"name": f"{NAMESPACE}:{f}"}
        if ev.stream:
            e["stream"] = True
        if ev.atten:
            e["attenuation_distance"] = ev.atten
        sounds.append(e)
    return {"sounds": sounds}


def write_sounds_json():
    """one entry per registered event whose every variation file exists on disk"""
    out = {}
    for name, ev in sorted(reg.EVENTS.items()):
        files = [f"{ev.group}/{ev.file_short(i)}" for i in range(ev.variants)]
        if all(os.path.exists(out_path(ev, i)) for i in range(ev.variants)):
            out[name] = event_entry(ev, files)
    tmp = SOUNDS_JSON + ".tmp"
    os.makedirs(OUT, exist_ok=True)
    with open(tmp, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, indent=1)
    os.replace(tmp, SOUNDS_JSON)
    print(f"sounds.json: {len(out)} events")
    return len(out)


def _clean(x):
    """round floats and drop None so the manifest is tidy"""
    if isinstance(x, float):
        return round(x, 4)
    if isinstance(x, dict):
        return {k: _clean(v) for k, v in x.items() if v is not None}
    if isinstance(x, (list, tuple)):
        return [_clean(v) for v in x]
    return x


def update_manifest(recs):
    man = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as fh:
            man = json.load(fh)
    by = {}
    for r in recs:
        by.setdefault(r["name"], []).append(r)
    for name, rs in by.items():
        ev = reg.EVENTS[name]
        old = man.get(name, {}).get("variants", [])
        have = {v["index"]: v for v in old}
        for r in rs:
            have[r["index"]] = r
        vs = [have[k] for k in sorted(have) if k < ev.variants]
        first = vs[0]
        meta = {k: v for k, v in first.items() if k not in ("name", "index", "file", "path", "seconds", "samples", "ticks", "channels",
                                                           "sample_rate", "lufs", "true_peak", "sample_peak", "bytes", "build_s")}
        ent = dict(event=f"{NAMESPACE}:{name}", seconds=max(v["seconds"] for v in vs), ticks=max(v["ticks"] for v in vs),
                   loop=bool(ev.loop), stream=bool(ev.stream), channels=first["channels"], variants_count=ev.variants,
                   lufs=round(float(np.mean([v["lufs"] for v in vs])), 2), true_peak=max(v["true_peak"] for v in vs),
                   atten=ev.atten, note=ev.note)
        ent.update(meta)
        ent["variants"] = [dict(file=v["file"], seconds=v["seconds"], ticks=v["ticks"], lufs=v["lufs"], true_peak=v["true_peak"],
                                bytes=v["bytes"], index=v["index"]) for v in vs]
        man[name] = _clean(ent)
    man = {k: man[k] for k in sorted(man) if k in reg.EVENTS}
    os.makedirs(OUT, exist_ok=True)
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(man, fh, indent=1)
    return man


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("names", nargs="*")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 4) - 2))
    ap.add_argument("--table", action="store_true")
    ap.add_argument("--clean", action="store_true")
    ap.add_argument("--variant", type=int, default=None, help="only this 1-based variation (when building a single event)")
    a = ap.parse_args(argv)
    if a.table:
        with open(MANIFEST, encoding="utf-8") as fh:
            man = json.load(fh)
        print("| event | files | seconds | ticks | ch | LUFS | true peak | loop | notes |")
        print("|---|---|---|---|---|---|---|---|---|")
        for k, v in man.items():
            extra = ", ".join(f"{x}={v[x]}" for x in v if x in ("bpm", "key", "bars", "bar_s", "loop_start_s", "loop_end_s", "hit_s", "section_s"))
            print(f"| `{k}` | {v['variants_count']} | {v['seconds']:.2f} | {v['ticks']:.0f} | {v['channels']} | {v['lufs']:.1f} | {v['true_peak']:.1f} | {'loop' if v['loop'] else ''} | {extra} |")
        return
    load_modules()
    todo = [n for n in sorted(reg.EVENTS) if matches(n, a.names)]
    if a.names and not todo:
        sys.exit("nothing matches " + " ".join(a.names))
    if a.list:
        for n in todo:
            e = reg.EVENTS[n]
            tgt = f"{e.lufs} LUFS" if e.lufs is not None else (f"peak {e.peak}" if e.peak is not None else "raw")
            print(f"{n:28s} x{e.variants} {'loop ' if e.loop else '     '}{'stereo' if e.stereo else 'mono  '} {tgt}")
        print(len(todo), "events,", sum(reg.EVENTS[n].variants for n in todo), "files")
        return
    if a.clean and os.path.isdir(OUT) and not a.names:
        shutil.rmtree(OUT)
    jobs = []
    for n in todo:
        e = reg.EVENTS[n]
        for i in range(e.variants):
            if a.variant is None or a.variant - 1 == i:
                jobs.append((n, i))
    t0 = time.time()
    print(f"building {len(jobs)} files for {len(todo)} events with {a.jobs} workers")
    dsp.svf(np.zeros(64), 1000.0)                      # compile numba kernels once so the workers load the cache
    dsp.delayloop(np.zeros(64), 20.0)
    dsp.hosc(np.full(64, 100.0))
    dsp.limit(np.zeros(64))
    jobs.sort(key=lambda j: -(reg.EVENTS[j[0]].group == "music"))     # long jobs first
    recs, failed = [], []
    if a.jobs <= 1 or len(jobs) == 1:
        results = map(_worker, jobs)
    else:
        pool = mp.get_context("spawn").Pool(a.jobs)
        results = pool.imap_unordered(_worker, jobs)
    for rec in results:
        if "error" in rec:
            failed.append(rec)
            print(f"FAILED {rec['name']}#{rec['index']}: {rec['error']}\n{rec['tb']}", flush=True)
        else:
            recs.append(rec)
            print(f"{rec['file']:30s} {rec['seconds']:7.2f}s {rec['channels']}ch {rec['lufs']:6.1f} LUFS tp {rec['true_peak']:6.2f} ({rec['build_s']}s)", flush=True)
    update_manifest(recs)
    write_sounds_json()
    total = sum(r["bytes"] for r in recs)
    print(f"{len(recs)} built, {len(failed)} failed, {total / 1e6:.1f} MB, {time.time() - t0:.0f}s")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    mp.freeze_support()
    main()
