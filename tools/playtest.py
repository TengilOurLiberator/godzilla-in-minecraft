"""Scripted full runs of Godzilla VR in the flat test client, driven over RCON. Instance 0 (server\\ and work\\testgame).

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\playtest.py [--runs 2] [--rampage SECONDS] [--passive] [--tag name]

Needs the server and the test client running (tools\\dev.ps1 up; set GZ_MAXFPS=1000 first for an uncapped client).
Per run: /godzilla start, the rise (shots), the rampage (walk through the city by waypoints, swipe, breath, roar; shots every
10 s, tick time and client fps every 5 s), Mothra (aim and fire; or stand still with --passive), results, then the next run starts
by smashing the restart sign. Everything goes to work\\playtest\\<tag>\\ (log.txt, shots\\). Server and client logs are scanned.
"""
import argparse, math, os, random, re, shutil, subprocess, sys, time, json

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gz_env  # noqa: E402
from rcon import Rcon  # noqa: E402

PY = r"D:\voice-assistant\.venv\Scripts\python.exe"
ROOT = os.path.dirname(HERE)
NAME = gz_env.NAME
EYE = 26.0           # eye height above the feet at scale 16
TICK = 0.05

ap = argparse.ArgumentParser()
ap.add_argument("--runs", type=int, default=2)
ap.add_argument("--rampage", type=float, default=0, help="skip to Mothra after this many seconds of rampage (0 = play the full 180 s)")
ap.add_argument("--passive", action="store_true", help="in the Mothra fight do nothing (she must be able to kill)")
ap.add_argument("--tag", default=time.strftime("%H%M%S"))
ap.add_argument("--noresults", action="store_true")
ap.add_argument("--shot-every", type=float, default=10.0)
args = ap.parse_args()

OUT = os.path.join(ROOT, "work", "playtest", args.tag)
SHOTS = os.path.join(OUT, "shots")
os.makedirs(SHOTS, exist_ok=True)
LOG = open(os.path.join(OUT, "log.txt"), "a", encoding="utf-8")
SERVER_LOG = os.path.join(gz_env.SERVER_DIR, "logs", "latest.log")
CLIENT_LOG = os.path.join(gz_env.GAME_DIR, "client.log")

r = Rcon()
random.seed(7)


def log(*a):
    s = time.strftime("%H:%M:%S ") + " ".join(str(x) for x in a)
    print(s, flush=True)
    LOG.write(s + "\n")
    LOG.flush()


def cmd(c):
    global r
    try:
        return r.cmd(c)
    except Exception:
        r = Rcon()
        return r.cmd(c)


def shot(name):
    p = subprocess.run([PY, "-I", os.path.join(HERE, "testclient.py"), "shot", name, "--delay", "0.2"], capture_output=True, text=True)
    src = os.path.join(gz_env.SHOTS, name + ".png")
    if os.path.exists(src):
        shutil.move(src, os.path.join(SHOTS, name + ".png"))
    else:
        log("shot failed", name, p.stdout.strip()[-200:], p.stderr.strip()[-200:])


def floats(s):
    return [float(x) for x in re.findall(r"-?\d+\.?\d*(?:[eE][-+]?\d+)?(?=[df]?[,\]])", s)]


def pos(sel=NAME):
    f = floats(cmd(f"data get entity {sel} Pos"))
    return (f[0], f[1], f[2]) if len(f) >= 3 else None


def status():
    s = cmd("godzilla status")
    m = re.match(r"phase (\w+) \((\d+) ticks\), run (\d+) ticks.*score (\d+), health ([\d.,]+)/", s)
    if not m:
        return None
    return dict(phase=m.group(1), ptick=int(m.group(2)), run=int(m.group(3)), score=int(m.group(4)), health=float(m.group(5).replace(",", ".")))


def tickq():
    s = cmd("tick query")
    m = re.search(r"Average time per tick: ([\d.]+)ms", s)
    p = re.search(r"P99: ([\d.]+)ms", s)
    return (float(m.group(1)) if m else -1, float(p.group(1)) if p else -1)


def client_fps():
    cmd(f"godzilla fps")
    time.sleep(0.15)
    try:
        lines = open(CLIENT_LOG, encoding="utf-8", errors="replace").read().splitlines()
    except OSError:
        return -1, ""
    for ln in reversed(lines[-300:]):
        m = re.search(r"GODZILLA-PLAYER fps=(\d+)", ln)
        if m:
            fr = re.search(r"frame=\[(.*?)\]", ln)
            return int(m.group(1)), (fr.group(1) if fr else "")
    return -1, ""


def yaw_to(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


def count(t):
    m = re.search(r"Count: (\d+)", cmd(f"execute if entity @e[type=godzilla:{t}]"))
    return int(m.group(1)) if m else 0


def entities():
    return {t: count(t) for t in ("person", "car", "bus", "train_car", "mil_tank", "mil_heli", "mil_jet", "mil_ship", "mil_maser", "mil_shot", "mothra", "scale_cloud", "smash_sign")}


# --- waypoints through the city: the most valuable buildings, nearest first -------------------------------------------------------
city = json.load(open(os.path.join(ROOT, "city", "out", "tokyo.json")))
start = city["playerStart"]["pos"]
cand = sorted(city["buildings"], key=lambda b: -b["value"])[:70]
wps = []
cur = (start[0], start[2] - 160)
left = [((b["min"][0] + b["max"][0]) / 2, (b["min"][2] + b["max"][2]) / 2) for b in cand]
while left and len(wps) < 40:
    left.sort(key=lambda p: (p[0] - cur[0]) ** 2 + (p[1] - cur[1]) ** 2)
    cur = left.pop(0)
    wps.append(cur)


def scan_logs(since_server, since_client):
    bad = []
    pat = re.compile(r"Exception|ERROR|\bat dev\.godzilla|Can't keep up|crash", re.I)
    ignore = re.compile(r"POI data mismatch|RCON|Thread RCON", re.I)
    for path, off in ((SERVER_LOG, since_server), (CLIENT_LOG, since_client)):
        try:
            with open(path, "rb") as f:
                f.seek(off)
                text = f.read().decode("utf-8", "replace")
        except OSError:
            continue
        for ln in text.splitlines():
            if pat.search(ln) and not ignore.search(ln):
                bad.append(os.path.basename(os.path.dirname(os.path.dirname(path))) + ": " + ln.strip()[:240])
    return bad


def fsize(p):
    try:
        return os.path.getsize(p)
    except OSError:
        return 0


def wait_phase(ph, timeout):
    t0 = time.time()
    while time.time() - t0 < timeout:
        s = status()
        if s and s["phase"] == ph:
            return s
        time.sleep(0.5)
    return None


def probe(label, rec):
    avg, p99 = tickq()
    fps, fr = client_fps()
    s = status()
    rec.append((label, avg, p99, fps))
    log(f"[{label}] tick avg {avg} ms p99 {p99} ms, client fps {fps} [{fr}], {s}")


def perf_stats():
    log("destruction:", cmd("godzilla destruction stats")[-420:])
    log("pop:", cmd("godzilla pop stats")[:300])
    log("mil:", cmd("godzilla mil stats")[:300])


def run(n):
    srv0, cli0 = fsize(SERVER_LOG), fsize(CLIENT_LOG)
    rec = []
    log(f"===== RUN {n} =====")
    cmd("godzilla destruction resetstats")
    s = status()
    log("before start:", s, entities())
    t_start = time.time()
    if n == 1 or s["phase"] == "IDLE":
        log(cmd(f"godzilla start {NAME}"))
    # --- rise
    t0 = time.time()
    seen = 0
    while True:
        s = status()
        if s and s["phase"] == "RAMPAGE":
            break
        if s and s["phase"] == "RISE":
            k = s["ptick"] // 50
            if k > seen:
                seen = k
                shot(f"r{n}_rise_{s['ptick']:03d}")
        if time.time() - t0 > 40:
            log("RISE did not end", s)
            break
        time.sleep(0.3)
    log("rampage begins", status(), "rise took", round(time.time() - t_start, 1), "s")
    # --- rampage
    wi = 0
    skipped = False
    last_shot = time.time()
    last_probe = 0
    side = 0
    nshot = 0
    tp0 = time.time()
    peak = {}
    while True:
        s = status()
        if not s or s["phase"] != "RAMPAGE":
            break
        el = s["run"] / 20.0
        if args.rampage and el >= args.rampage and not skipped:
            skipped = True
            cmd("godzilla skip")
            continue
        p = pos()
        if p is None:
            time.sleep(0.5)
            continue
        tx, tz = wps[wi % len(wps)]
        dx, dz = tx - p[0], tz - p[2]
        if dx * dx + dz * dz < 30 * 30:
            wi += 1
        yaw = yaw_to(dx, dz)
        pitch = 14 + 10 * math.sin(el * 0.7)
        cmd(f"rotate {NAME} {yaw:.1f} {pitch:.1f}")
        cmd(f"godzilla walk 24")
        cmd(f"godzilla swipe {'left' if side % 2 else 'right'}")
        side += 1
        if int(el * 2) % 14 == 3:
            cmd(f"godzilla breath 55")
        if int(el) % 22 == 21 and int(el * 2) % 2 == 0:
            cmd(f"godzilla roar")
        now = time.time()
        if now - last_probe >= 5:
            last_probe = now
            probe(f"r{n} t={el:.0f}s", rec)
            e = entities()
            for k, v in e.items():
                peak[k] = max(peak.get(k, 0), v)
        if now - last_shot >= args.shot_every:
            last_shot = now
            nshot += 1
            if nshot % 4 == 0:
                cmd(f"godzilla camera third")
                time.sleep(0.6)
                shot(f"r{n}_ramp_{int(el):03d}_3rd")
                cmd(f"godzilla camera first")
            else:
                shot(f"r{n}_ramp_{int(el):03d}")
        time.sleep(0.45)
    log("rampage over", status(), "peak entity counts", peak)
    perf_stats()
    # --- mothra
    fight0 = time.time()
    fp = 0
    mshots = 0
    last_probe = 0
    s = status()
    while s and s["phase"] == "MOTHRA":
        m = pos("@e[type=godzilla:mothra,limit=1]")
        p = pos()
        if m and p and not args.passive:
            ex, ey, ez = p[0], p[1] + EYE, p[2]
            dx, dy, dz = m[0] - ex, m[1] - ey, m[2] - ez
            hd = math.hypot(dx, dz)
            pitch = -math.degrees(math.atan2(dy, hd))
            cmd(f"rotate {NAME} {yaw_to(dx, dz):.1f} {pitch:.1f}")
            dist = math.sqrt(dx * dx + dy * dy + dz * dz)
            if dist < 120:
                cmd("godzilla breath 40")
            if hd > 60:
                cmd("godzilla walk 20")
            if hd < 45:
                cmd(f"godzilla swipe {'left' if fp % 2 else 'right'}")
            fp += 1
        elif p and not args.passive:
            cmd("godzilla walk 10")
        now = time.time()
        if now - last_probe >= 5:
            last_probe = now
            probe(f"r{n} fight {now - fight0:.0f}s", rec)
            log("mothra:", cmd("godzilla mothra status")[:200])
        if mshots < 10 and now - fight0 > 3 + mshots * 9:
            mshots += 1
            shot(f"r{n}_mothra_{mshots:02d}")
        time.sleep(0.4)
        s = status()
    log("fight over", s, "lasted", round(time.time() - fight0, 1), "s")
    # --- results
    s = wait_phase("VICTORY", 1) or wait_phase("DEFEAT", 1) or s
    t0 = time.time()
    shot_done = 0
    while time.time() - t0 < 40 and not args.noresults:
        g = cmd("godzilla game")
        if shot_done == 0 and time.time() - t0 > 3:
            shot(f"r{n}_end_a")
            shot_done = 1
        if "results" in g.lower() or time.time() - t0 > 14:
            pass
        if time.time() - t0 > 14 and shot_done == 1:
            cmd("godzilla results")
            time.sleep(4)
            shot(f"r{n}_results")
            shot_done = 2
            break
        time.sleep(1)
    log("results phase:", status(), cmd("godzilla game"))
    problems = scan_logs(srv0, cli0)
    log(f"RUN {n} wall time {time.time() - t_start:.0f}s; log problems: {len(problems)}")
    for ln in problems[:25]:
        log("  !", ln)
    return rec, problems


def restart_via_sign():
    # wait for the sign, then smash it the way a hand would
    t0 = time.time()
    while time.time() - t0 < 30:
        m = pos("@e[type=godzilla:smash_sign,limit=1]")
        if m:
            break
        time.sleep(0.5)
    if not m:
        log("no restart sign found")
        return False
    log("restart sign at", m)
    cmd(f"execute as {NAME} run godzilla smash {m[0]} {m[1] + 2} {m[2]} 3")
    return wait_phase("RISE", 15) is not None


results = []
for n in range(1, args.runs + 1):
    if n > 1:
        log("--- restart via sign")
        ok = restart_via_sign()
        log("restarted:", ok, status(), "entities:", entities())
        time.sleep(2)
        d = cmd("godzilla destruction stats")
        log("after reset: destruction", d[d.find("structures"):d.find("structures") + 120], "| fires", d[d.find("fires"):d.find("fires") + 10], "| masses/rubble", d[d.find("masses"):d.find("masses") + 40])
        log("after reset: pop", cmd("godzilla pop stats")[:160])
        log("after reset: mil", cmd("godzilla mil stats")[:160])
    rec, problems = run(n)
    results.append((rec, problems))
    log("entities after run:", entities())

log("===== SUMMARY =====")
for i, (rec, problems) in enumerate(results, 1):
    ticks = [x[1] for x in rec if x[1] >= 0]
    p99 = [x[2] for x in rec if x[2] >= 0]
    fps = [x[3] for x in rec if x[3] > 0]
    log(f"run {i}: samples {len(rec)}, tick avg of avgs {sum(ticks) / max(1, len(ticks)):.1f} ms, worst avg {max(ticks, default=0):.1f}, worst p99 {max(p99, default=0):.1f}; "
        f"fps min {min(fps, default=0)} mean {sum(fps) / max(1, len(fps)):.0f}; log problems {len(problems)}")
