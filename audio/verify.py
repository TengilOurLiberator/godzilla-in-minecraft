"""verify.py - the last gate: every file the game will load exists, decodes, has the right channel count and sample rate, and
sounds.json references exactly those files.

    python verify.py          # exit code 1 if anything is wrong
"""
import json
import os
import re
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_audio as ma
import reg


def main():
    ma.load_modules()
    bad = []
    with open(ma.SOUNDS_JSON, encoding="utf-8") as fh:
        sj = json.load(fh)
    with open(ma.MANIFEST, encoding="utf-8") as fh:
        man = json.load(fh)
    disk = set()
    for root, _, files in os.walk(ma.SOUNDS):
        for f in files:
            disk.add(os.path.relpath(os.path.join(root, f), ma.SOUNDS).replace("\\", "/"))
    referenced = set()
    n_files = 0
    for name, ev in sorted(reg.EVENTS.items()):
        if name not in sj:
            bad.append(f"{name}: not in sounds.json")
            continue
        entries = sj[name]["sounds"]
        if len(entries) != ev.variants:
            bad.append(f"{name}: {len(entries)} sounds in sounds.json, {ev.variants} registered")
        if not re.fullmatch(r"[a-z0-9_.]+", name):
            bad.append(f"{name}: bad event name")
        for e in entries:
            ns, _, path = e["name"].partition(":")
            if ns != "godzilla":
                bad.append(f"{name}: namespace {ns}")
            if not re.fullmatch(r"[a-z0-9_/]+", path):
                bad.append(f"{name}: bad path {path}")
            rel = path + ".ogg"
            referenced.add(rel)
            full = os.path.join(ma.SOUNDS, *rel.split("/"))
            if not os.path.exists(full):
                bad.append(f"{name}: missing file {rel}")
                continue
            try:
                y, sr = sf.read(full, dtype="float64", always_2d=True)
            except Exception as ex:
                bad.append(f"{name}: {rel} does not decode ({ex})")
                continue
            n_files += 1
            ch = y.shape[1]
            if sr != 44100:
                bad.append(f"{rel}: sample rate {sr}")
            if ev.stereo and ch != 2:
                bad.append(f"{rel}: should be stereo, is {ch} ch")
            if not ev.stereo and ch != 1:
                bad.append(f"{rel}: positional/mono event but {ch} ch")
            if not np.all(np.isfinite(y)):
                bad.append(f"{rel}: non-finite samples")
            if np.max(np.abs(y)) < 1e-4:
                bad.append(f"{rel}: silent")
            if bool(e.get("stream")) != bool(ev.stream):
                bad.append(f"{rel}: stream flag mismatch")
            if ev.group == "music" and not e.get("stream"):
                bad.append(f"{rel}: music must be streamed")
            mv = [v for v in man[name]["variants"] if v["file"] == path]
            if not mv or abs(mv[0]["seconds"] - len(y) / sr) > 0.002:
                bad.append(f"{rel}: manifest duration disagrees with the decoded file")
            if ev.loop and abs(len(y) / sr * 20 - round(len(y) / sr * 20)) > 0.01:
                bad.append(f"{rel}: loop is not a whole number of ticks")
    orphans = disk - referenced
    for o in sorted(orphans):
        bad.append(f"orphan file not in sounds.json: {o}")
    extra = set(sj) - set(reg.EVENTS)
    for e in sorted(extra):
        bad.append(f"sounds.json has unknown event {e}")
    print(f"{len(sj)} events, {n_files} files checked")
    for b in bad:
        print("PROBLEM:", b)
    print("OK" if not bad else f"{len(bad)} problems")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
