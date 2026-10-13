"""A flat, silent, offline desktop Minecraft client for automated tests (NO Vivecraft). One per instance (GZ_INSTANCE, see tools\\gz_env.py). It launches through the Fabric loader
with fabric-api + our mod + the Patrix pack, auto-joins the local server, and saves screenshots of the game window.

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\testclient.py start [--port 25567] [--name Tester] [--width 1280 --height 720] [--no-wait]
    ... testclient.py shot <name> [--delay 0]       -> work\\shots\\<name>.png  (prints the path)
    ... testclient.py status                        -> running / joined?
    ... testclient.py log [N]                       -> last N lines of the client log
    ... testclient.py stop

The server must run with online-mode=false:  powershell tools\\server.ps1 -Offline -Background   (RCON: tools\\rcon.py)
Typical script:  server.ps1 -Offline -Background; testclient.py start; rcon.py "gamemode creative Tester" "tp Tester ..." ;
                 testclient.py shot a; testclient.py stop; server.ps1 -Stop

The game runs from its own scratch folder work\\testgame, or work\\inst<N>\\testgame for instance N (options are rewritten on every start: silent, no bobbing/fov effects).
The client process is detached: it keeps running after this script returns, until `stop`.
"""
import argparse, ctypes, ctypes.wintypes as wt, glob, json, os, re, shutil, subprocess, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))     # python -I does not add the script's folder
import gz_env  # noqa: E402

# Instance and checkout (tools\gz_env.py): GZ_INSTANCE=N (or <worktree>\.gz_instance) selects work\inst<N>\{testgame,shots},
# port 25567+N, player Tester<N>; instance 0 is the classic layout. Mod jar = THIS checkout's build; jdk, downloads = main checkout.
INSTANCE = gz_env.INSTANCE
GAME = gz_env.GAME_DIR
SHOTS = gz_env.SHOTS
STATE = os.path.join(gz_env.WORK, "testclient.json")
DOWNLOADS = gz_env.DOWNLOADS
MC = os.path.join(os.environ["APPDATA"], ".minecraft")                       # read-only use of the installed game
JAVA = os.path.join(gz_env.JDK, "bin", "java.exe")
VERSION = "26.3"
FABRIC_PROFILE = "fabric-loader-0.19.5-26.3"
PATRIX = "Patrix_26.3_32x_basic.zip"

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32
for _f in (user32.GetDC, gdi32.CreateCompatibleDC, gdi32.CreateCompatibleBitmap, gdi32.SelectObject):
    _f.restype = ctypes.c_void_p
user32.SetProcessDPIAware()


# ------------------------------------------------------------------------------------------------ launching

def options(width, height):
    o = {
        "version": "5023", "fov": "0.0", "fovEffectScale": "0.0", "screenEffectScale": "0.0", "darknessEffectScale": "0.0",
        "gamma": "0.5", "renderDistance": "16", "simulationDistance": "12", "entityDistanceScaling": "5.0",
        "vignette": "false", "bobView": "false", "damageTiltStrength": "0.0", "enableVsync": "false", "maxFps": os.environ.get("GZ_MAXFPS", "60"),
        "inactivityFpsLimit": '"minimized"', "pauseOnLostFocus": "false", "biomeBlendRadius": "3", "ao": "true",
        "mipmapLevels": "4", "entityShadows": "true", "particles": "0", "guiScale": "2", "showSubtitles": "false",
        "narrator": "0", "chatVisibility": os.environ.get("GZ_CHATVIS", "0"), "soundCategory_master": "0.0", "soundCategory_music": "0.0",
        "soundCategory_record": "0.0", "soundCategory_weather": "0.0", "soundCategory_block": "0.0",
        "soundCategory_hostile": "0.0", "soundCategory_neutral": "0.0", "soundCategory_player": "0.0",
        "soundCategory_ambient": "0.0", "soundCategory_voice": "0.0", "onboardAccessibility": "false",
        "skipMultiplayerWarning": "true", "joinedFirstServer": "true", "tutorialStep": "none", "lang": "en_us",
        "autoJump": "false", "resourcePacks": '["vanilla","file/%s"]' % PATRIX, "incompatibleResourcePacks": "[]",
        "fullscreen": "false", "overrideWidth": str(width), "overrideHeight": str(height),
    }
    return "".join(f"{k}:{v}\n" for k, v in o.items())


def prepare(width, height, vivecraft=False):
    os.makedirs(os.path.join(GAME, "natives"), exist_ok=True)
    mods = os.path.join(GAME, "mods")
    shutil.rmtree(mods, ignore_errors=True)
    os.makedirs(mods)
    for f in os.listdir(DOWNLOADS):                           # fabric-api only: NO vivecraft in the flat client (unless --vivecraft)
        if (f.startswith("fabric-api") or (vivecraft and f.startswith("vivecraft"))) and f.endswith(".jar"):
            shutil.copy2(os.path.join(DOWNLOADS, f), mods)
    libs = gz_env.LIBS
    ours = sorted((f for f in os.listdir(libs) if f.startswith("godzilla-") and f.endswith(".jar") and "-sources" not in f),
                  key=lambda f: os.path.getmtime(os.path.join(libs, f)), reverse=True) if os.path.isdir(libs) else []
    if not ours:
        sys.exit(f"no godzilla jar in {libs}: run tools\\build.ps1 first")
    shutil.copy2(os.path.join(libs, ours[0]), mods)
    rp = os.path.join(GAME, "resourcepacks")
    os.makedirs(rp, exist_ok=True)
    shutil.copy2(os.path.join(DOWNLOADS, PATRIX), os.path.join(rp, PATRIX))
    with open(os.path.join(GAME, "options.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(options(width, height))
    for d in ("logs", "screenshots", "crash-reports"):
        shutil.rmtree(os.path.join(GAME, d), ignore_errors=True)
    return ours[0]


def maven_path(name):
    group, art, ver = name.split(":")[:3]
    return os.path.join(MC, "libraries", *group.split("."), art, ver, f"{art}-{ver}.jar")


def command(port, name, width, height, xmx):
    """Same classpath and arguments the launcher builds for the Fabric profile, but with our own game directory."""
    v = json.load(open(os.path.join(MC, "versions", VERSION, VERSION + ".json")))
    fj = json.load(open(os.path.join(MC, "versions", FABRIC_PROFILE, FABRIC_PROFILE + ".json")))
    ok = lambda lib: all(r.get("os", {}).get("name") in (None, "windows") and "arch" not in r.get("os", {})
                         for r in lib.get("rules", [{}]) if r.get("action") == "allow")
    cp = [maven_path(lib["name"]) for lib in fj["libraries"]]
    cp += [os.path.join(MC, "libraries", *lib["downloads"]["artifact"]["path"].split("/"))
           for lib in v["libraries"] if ok(lib) and "artifact" in lib.get("downloads", {})]
    for p in cp:
        if not os.path.exists(p):
            raise FileNotFoundError(p)
    cp.append(os.path.join(MC, "versions", VERSION, VERSION + ".jar"))
    natives = os.path.join(GAME, "natives")
    jvm = []
    for a in v["arguments"]["jvm"]:
        if isinstance(a, str):
            jvm.append(a)
        elif any(r.get("os", {}).get("name") == "windows" for r in a["rules"]):
            jvm += a["value"] if isinstance(a["value"], list) else [a["value"]]
    sub = {"${natives_directory}": natives, "${launcher_name}": "godzilla-testclient", "${launcher_version}": "1",
           "${classpath}": os.pathsep.join(cp)}
    for k, val in sub.items():
        jvm = [a.replace(k, val) for a in jvm]
    jvm += [a for a in fj.get("arguments", {}).get("jvm", []) if isinstance(a, str)]
    return [JAVA, f"-Xmx{xmx}"] + jvm + [
        fj["mainClass"], "--username", name, "--version", FABRIC_PROFILE, "--gameDir", GAME,
        "--assetsDir", os.path.join(MC, "assets"), "--assetIndex", v["assetIndex"]["id"],
        "--accessToken", "0", "--userType", "legacy", "--width", str(width), "--height", str(height),
        "--quickPlayMultiplayer", f"localhost:{port}"]


# ------------------------------------------------------------------------------------------------ state, windows

def load_state():
    try:
        return json.load(open(STATE))
    except (OSError, ValueError):
        return None


def pid_alive(pid):
    """True only if pid is a live process whose command line names THIS instance's game folder (a stale or reused pid is not ours)."""
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          f"(Get-CimInstance Win32_Process -Filter 'ProcessId={int(pid)}' -ErrorAction SilentlyContinue).CommandLine"],
                         capture_output=True, text=True).stdout
    return os.path.normcase(GAME) in os.path.normcase(out)


def game_window(pid):
    """The biggest visible top-level window owned by the process pid (found by process id, never by title, so several clients
    on one machine are never mixed up)."""
    found = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    def each(hwnd, _):
        if not user32.IsWindowVisible(hwnd):
            return True
        p = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(p))
        if p.value == pid:
            rc = (ctypes.c_long * 4)()
            user32.GetClientRect(ctypes.c_void_p(hwnd), rc)
            found.append((rc[2] * rc[3], hwnd))
        return True

    user32.EnumWindows(each, 0)
    found = [f for f in found if f[0] > 0]
    return max(found)[1] if found else None


def client_size(hwnd):
    rc = (ctypes.c_long * 4)()
    user32.GetClientRect(ctypes.c_void_p(hwnd), rc)
    return rc[2], rc[3]


def grab(hwnd):
    """The window's own client picture via PrintWindow (the window is not brought to the front, nothing else is captured)."""
    from PIL import Image
    w, h = client_size(hwnd)
    hdc = user32.GetDC(ctypes.c_void_p(hwnd))
    mem = gdi32.CreateCompatibleDC(ctypes.c_void_p(hdc))
    bmp = gdi32.CreateCompatibleBitmap(ctypes.c_void_p(hdc), w, h)
    gdi32.SelectObject(ctypes.c_void_p(mem), ctypes.c_void_p(bmp))
    user32.PrintWindow(ctypes.c_void_p(hwnd), ctypes.c_void_p(mem), 3)            # client area, full content
    header = (ctypes.c_uint32 * 10)(40, w, ctypes.c_uint32(-h).value, 1 | (32 << 16), 0, 0, 0, 0, 0, 0)
    buf = ctypes.create_string_buffer(w * h * 4)
    gdi32.GetDIBits(ctypes.c_void_p(mem), ctypes.c_void_p(bmp), 0, h, buf, header, 0)
    gdi32.DeleteObject(ctypes.c_void_p(bmp))
    gdi32.DeleteDC(ctypes.c_void_p(mem))
    user32.ReleaseDC(ctypes.c_void_p(hwnd), ctypes.c_void_p(hdc))
    return Image.frombuffer("RGBA", (w, h), buf, "raw", "BGRA", 0, 1).convert("RGB")


def uniform(img):
    return all(lo == hi for lo, hi in img.getextrema())


def f2_screenshot(hwnd, timeout=6.0):
    """Fallback when PrintWindow returns a uniform (black) picture: the game's own F2 screenshot. Needs the window in the
    foreground for a moment (SDL/GLFW read real key events)."""
    from PIL import Image
    shots = os.path.join(GAME, "screenshots")
    before = set(glob.glob(os.path.join(shots, "*.png")))
    user32.keybd_event(0x12, 0, 0, 0)                                           # ALT trick so Windows allows the focus change
    user32.SetForegroundWindow(ctypes.c_void_p(hwnd))
    user32.keybd_event(0x12, 0, 2, 0)
    time.sleep(0.5)
    user32.keybd_event(0x71, 0x3C, 0, 0)
    time.sleep(0.05)
    user32.keybd_event(0x71, 0x3C, 2, 0)
    t0 = time.time()
    while time.time() - t0 < timeout:
        new = set(glob.glob(os.path.join(shots, "*.png"))) - before
        if new:
            time.sleep(0.4)
            im = Image.open(sorted(new)[-1])
            im.load()
            return im.convert("RGB")
        time.sleep(0.1)
    return None


# ------------------------------------------------------------------------------------------------ commands

def client_log():
    for p in (os.path.join(GAME, "logs", "latest.log"), os.path.join(GAME, "client.log")):
        if os.path.exists(p):
            try:
                return open(p, encoding="utf-8", errors="replace").read()
            except OSError:
                pass
    return ""


def joined(text):
    return "GODZILLA-NET client got ping" in text


def cmd_start(a):
    st = load_state()
    if st and pid_alive(st["pid"]):
        sys.exit(f"test client already running (pid {st['pid']}); `stop` it first")
    os.makedirs(SHOTS, exist_ok=True)
    jar = prepare(a.width, a.height, a.vivecraft)
    log = open(os.path.join(GAME, "client.log"), "w")
    cmd = command(a.port, a.name, a.width, a.height, a.xmx)
    # detached: survives this script; no console window
    proc = subprocess.Popen(cmd, cwd=GAME, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                            creationflags=0x08000000 | 0x00000008 | 0x00000200)   # NO_WINDOW | DETACHED | NEW_PROCESS_GROUP
    json.dump({"pid": proc.pid, "port": a.port, "name": a.name, "jar": jar, "started": time.time()}, open(STATE, "w"))
    print(f"test client started (pid {proc.pid}, mod jar {jar}, auto-join localhost:{a.port} as {a.name})", flush=True)
    if a.no_wait:
        return
    deadline = time.time() + a.timeout
    while time.time() < deadline:
        if proc.poll() is not None:
            sys.exit(f"client exited with code {proc.returncode}; see {os.path.join(GAME, 'client.log')}")
        if joined(client_log()):
            print("joined the server (payload round trip seen in the client log)")
            time.sleep(a.settle)
            return
        time.sleep(1.0)
    sys.exit(f"did not join within {a.timeout}s; is the server running with online-mode=false (server.ps1 -Offline)? "
             f"see {os.path.join(GAME, 'client.log')}")


def cmd_shot(a):
    st = load_state()
    if not st or not pid_alive(st["pid"]):
        sys.exit("test client is not running (testclient.py start)")
    hwnd = game_window(st["pid"])
    if not hwnd:
        sys.exit("no game window yet")
    time.sleep(a.delay)
    os.makedirs(SHOTS, exist_ok=True)
    img = grab(hwnd)
    how = "PrintWindow"
    if uniform(img):
        alt = f2_screenshot(hwnd)
        if alt is not None:
            img, how = alt, "F2 (PrintWindow was uniform)"
    out = os.path.join(SHOTS, a.name + ".png")
    img.save(out)
    print(f"{out}  {img.size[0]}x{img.size[1]}  via {how}  (instance {INSTANCE}, client pid {st['pid']}, window {hwnd:#x})" + ("  WARNING: picture is uniform" if uniform(img) else ""))


def cmd_status(a):
    st = load_state()
    if not st or not pid_alive(st["pid"]):
        print("not running")
        return
    print(f"instance {INSTANCE}: running pid {st['pid']}, joined={joined(client_log())}, window={'yes' if game_window(st['pid']) else 'no'}")


def cmd_log(a):
    print("\n".join(client_log().splitlines()[-a.n:]))


def cmd_stop(a):
    st = load_state()
    if st and pid_alive(st["pid"]):
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(st["pid"])], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(f"test client of instance {INSTANCE} stopped")
    else:
        print(f"test client of instance {INSTANCE} was not running")
    try:
        os.remove(STATE)
    except OSError:
        pass


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("start")
    s.add_argument("--port", type=int, default=gz_env.PORT)
    s.add_argument("--name", default=gz_env.NAME)
    s.add_argument("--width", type=int, default=1280)
    s.add_argument("--height", type=int, default=720)
    s.add_argument("--xmx", default=gz_env.CLIENT_XMX)
    s.add_argument("--timeout", type=int, default=240)
    s.add_argument("--settle", type=float, default=8.0, help="seconds to wait after joining so chunks and entities arrive")
    s.add_argument("--no-wait", action="store_true")
    s.add_argument("--vivecraft", action="store_true", help="also install Vivecraft (still a flat client: vrEnabled is false in a fresh config); tests the optional-dependency bridge")
    s.set_defaults(f=cmd_start)
    s = sub.add_parser("shot")
    s.add_argument("name")
    s.add_argument("--delay", type=float, default=0.0)
    s.set_defaults(f=cmd_shot)
    sub.add_parser("status").set_defaults(f=cmd_status)
    s = sub.add_parser("log")
    s.add_argument("n", nargs="?", type=int, default=40)
    s.set_defaults(f=cmd_log)
    sub.add_parser("stop").set_defaults(f=cmd_stop)
    a = ap.parse_args()
    a.f(a)


if __name__ == "__main__":
    main()

