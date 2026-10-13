"""Prepare D:\\godzilla-vr\\game as the game directory of the "Godzilla VR" launcher profile (the real VR client).

    setup_client.py                      prepare game\\ (safe to run again; Genesis's game folder is only read)
    setup_client.py --install-profile    also add the "Godzilla VR" profile to the Minecraft launcher (close the launcher first)

What it takes from the Genesis game folder (D:\\genesis-vr\\game, READ ONLY): config\\ (Vivecraft client config) and openvr\\
(SteamVR manifest and input bindings), each file only where game\\ does not have it yet (local tweaks are kept), and
options.txt as the starting point. Not taken: saves, logs, mods other than the ones listed here, Genesis's resource pack.

What it writes into game\\:
  mods\\            fabric-api + vivecraft (from downloads\\) + the newest godzilla-*.jar from mod\\build\\libs (if built)
  resourcepacks\\   Patrix_26.3_32x_basic.zip (copied from downloads\\), switched on in options.txt
  options.txt       resourcePacks = vanilla + Patrix; Genesis's video settings; view/simulation distance of this server
  servers.dat       one entry, "Godzilla", localhost:<server-port from server\\server.properties> (25567)

launcher_profiles.json (under %APPDATA%\\.minecraft) is changed ONLY with --install-profile. Without the flag the profile
entry that would be added is printed. The first version of the file is kept as launcher_profiles.json.before-godzilla.
"""
import argparse, datetime, json, os, shutil, struct

import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))     # python -I does not add the script's folder
import gz_env  # noqa: E402

ROOT = gz_env.MAIN          # the real VR client folder game\, downloads\ and server\ are shared and live in the main checkout
GAME = os.path.join(ROOT, "game")
DOWNLOADS = os.path.join(ROOT, "downloads")
GENESIS_GAME = r"D:\genesis-vr\game"
MC = os.path.join(os.environ.get("APPDATA", ""), ".minecraft")
PATRIX = "Patrix_26.3_32x_basic.zip"
PROFILE_NAME = "Godzilla VR"
PROFILE_KEY = "godzilla-vr"
SERVER_NAME = "Godzilla"
GENESIS_PROFILE = "Genesis VR"
DEFAULT_VERSION = "fabric-loader-0.19.5-26.3"
DEFAULT_JAVA_ARGS = "-Xmx8G -XX:+UseG1GC"

WANTED = {"onboardAccessibility": "false", "skipMultiplayerWarning": "true", "joinedFirstServer": "true",
          "tutorialStep": "none", "pauseOnLostFocus": "false",
          "renderDistance": "24", "simulationDistance": "16", "entityDistanceScaling": "5.0", "biomeBlendRadius": "3",
          "cloudRange": "128", "particles": "0", "graphicsPreset": "\"custom\"", "mipmapLevels": "4", "entityShadows": "true"}


def need(path, what):
    if not os.path.exists(path):
        raise SystemExit(f"missing {what}: {path}")


def server_port():
    path = os.path.join(ROOT, "server", "server.properties")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.startswith("server-port="):
                return int(line.split("=", 1)[1].strip())
    return 25567


def copy_missing(src_root, game, name):
    """Copy Genesis's folder file by file, but only the files game\\ does not have yet."""
    src = os.path.join(src_root, name)
    need(src, f"Genesis {name} folder")
    dst_root = os.path.join(game, name)
    added = 0
    for dirpath, _dirs, files in os.walk(src):
        rel = os.path.relpath(dirpath, src)
        os.makedirs(os.path.join(dst_root, rel), exist_ok=True)
        for f in files:
            d = os.path.join(dst_root, rel, f)
            if not os.path.exists(d):
                shutil.copy2(os.path.join(dirpath, f), d)
                added += 1
    return added


def install_mods(game):
    mods = os.path.join(game, "mods")
    os.makedirs(mods, exist_ok=True)
    wanted = []
    for f in sorted(os.listdir(DOWNLOADS)):
        if f.startswith(("fabric-api", "vivecraft")) and f.endswith(".jar"):
            wanted.append(os.path.join(DOWNLOADS, f))
    libs = gz_env.LIBS if os.path.isdir(gz_env.LIBS) else os.path.join(ROOT, "mod", "build", "libs")
    ours = sorted((f for f in os.listdir(libs) if f.startswith("godzilla-") and f.endswith(".jar") and "-sources" not in f),
                  key=lambda f: os.path.getmtime(os.path.join(libs, f)), reverse=True) if os.path.isdir(libs) else []
    if ours:
        wanted.append(os.path.join(libs, ours[0]))
    names = {os.path.basename(w) for w in wanted}
    for f in os.listdir(mods):
        if f.lower().endswith(".jar") and f not in names and (f.startswith("godzilla-") or f.startswith(("fabric-api", "vivecraft"))):
            os.remove(os.path.join(mods, f))
    for w in wanted:
        shutil.copy2(w, os.path.join(mods, os.path.basename(w)))
    return sorted(names), bool(ours)


def write_options(game):
    base = None
    for cand in (os.path.join(game, "options.txt"), os.path.join(GENESIS_GAME, "options.txt"), os.path.join(MC, "options.txt")):
        if os.path.exists(cand):
            base = cand
            break
    lines = list(open(base, encoding="utf-8")) if base else []
    packs = '["vanilla","file/%s"]' % PATRIX
    out, seen = [], set()
    for line in lines:
        key = line.split(":", 1)[0]
        if key == "resourcePacks":
            line = f"resourcePacks:{packs}\n"
            seen.add(key)
        elif key == "incompatibleResourcePacks":
            line = "incompatibleResourcePacks:[]\n"
            seen.add(key)
        elif key in WANTED:
            line = f"{key}:{WANTED[key]}\n"
            seen.add(key)
        out.append(line)
    if "resourcePacks" not in seen:
        out.append(f"resourcePacks:{packs}\n")
    out += [f"{k}:{v}\n" for k, v in WANTED.items() if k not in seen]
    path = os.path.join(game, "options.txt")
    open(path, "w", encoding="utf-8", newline="\n").writelines(out)
    return path, base


def nbt_string(text):
    b = text.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def write_servers(game, port):
    entry = (b"\x08" + nbt_string("name") + nbt_string(SERVER_NAME)
             + b"\x08" + nbt_string("ip") + nbt_string(f"localhost:{port}") + b"\x00")
    nbt = b"\x0a" + nbt_string("") + b"\x09" + nbt_string("servers") + b"\x0a" + struct.pack(">i", 1) + entry + b"\x00"
    path = os.path.join(game, "servers.dat")
    with open(path, "wb") as f:
        f.write(nbt)
    return path


def profile_entry():
    """The profile for the game folder; version and JVM args follow the Genesis VR profile when it exists."""
    version, java_args = DEFAULT_VERSION, DEFAULT_JAVA_ARGS
    lp = os.path.join(MC, "launcher_profiles.json")
    if os.path.exists(lp):
        for p in json.load(open(lp, encoding="utf-8")).get("profiles", {}).values():
            if p.get("name") == GENESIS_PROFILE:
                version, java_args = p.get("lastVersionId", version), p.get("javaArgs", java_args)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    return {"created": now, "gameDir": GAME, "icon": "Grass", "javaArgs": java_args, "lastUsed": now,
            "lastVersionId": version, "name": PROFILE_NAME, "type": "custom"}


def install_profile(entry):
    lp = os.path.join(MC, "launcher_profiles.json")
    need(lp, "launcher profile list (open the Minecraft launcher once first)")
    data = json.load(open(lp, encoding="utf-8"))
    profiles = data.setdefault("profiles", {})
    key = PROFILE_KEY
    for k, p in profiles.items():
        if p.get("name") == PROFILE_NAME:
            key = k
            entry["created"] = p.get("created", entry["created"])
    backup = lp + ".before-godzilla"
    if not os.path.exists(backup):
        shutil.copy2(lp, backup)
    profiles[key] = entry
    with open(lp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    return lp, backup, key


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("game_dir", nargs="?", default=GAME)
    ap.add_argument("--install-profile", action="store_true", help="add the 'Godzilla VR' profile to the Minecraft launcher")
    args = ap.parse_args()
    game = args.game_dir
    need(GENESIS_GAME, "Genesis game folder")
    need(os.path.join(DOWNLOADS, PATRIX), "Patrix resource pack")
    os.makedirs(game, exist_ok=True)

    os.makedirs(os.path.join(game, "resourcepacks"), exist_ok=True)
    shutil.copy2(os.path.join(DOWNLOADS, PATRIX), os.path.join(game, "resourcepacks", PATRIX))
    jars, have_ours = install_mods(game)
    added = {n: copy_missing(GENESIS_GAME, game, n) for n in ("config", "openvr")}
    opts, base = write_options(game)
    servers = write_servers(game, server_port())

    print("game folder:", game)
    print("  mods:", ", ".join(jars), "" if have_ours else "(godzilla jar not built yet: run tools\\build.ps1, it deploys into game\\mods)")
    print("  resourcepacks:", PATRIX, "(enabled)")
    print("  config/openvr files added from Genesis (existing kept):", added)
    print("  options.txt started from:", base or "nothing (defaults)")
    print("  servers.dat: one entry, %r at localhost:%d" % (SERVER_NAME, server_port()))

    entry = profile_entry()
    if args.install_profile:
        lp, backup, key = install_profile(entry)
        print(f"launcher profile written: {lp} (key {key}; the first version of the file is in {backup})")
    else:
        print("\nLauncher profile NOT changed. With --install-profile this entry would be added to")
        print(os.path.join(MC, "launcher_profiles.json"), "under the key", repr(PROFILE_KEY) + ":")
        print(json.dumps({PROFILE_KEY: entry}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
