"""Which instance and which checkout the tools are working with. Imported by rcon.py, testclient.py, setup_client.py.

Instance number N (0..9), first match wins:  environment GZ_INSTANCE  >  file <worktree>\\.gz_instance  >  0
  N = 0: today's layout (server\\, work\\testgame, work\\shots, ports 25567/25577, player Tester)
  N > 0: work\\inst<N>\\{server,testgame,shots}, ports 25567+N / 25577+N, player Tester<N>

WT   = the checkout the script lives in (a git worktree or the main checkout): mod source, build output.
MAIN = the main checkout D:\\godzilla-vr (override: GZ_MAIN): jdk, downloads, server template, work, game. Always shared.
"""
import os

TOOLS = os.path.dirname(os.path.abspath(__file__))
WT = os.path.dirname(TOOLS)
MAIN = os.environ.get("GZ_MAIN") or r"D:\godzilla-vr"


def _instance():
    v = os.environ.get("GZ_INSTANCE", "").strip()
    if not v:
        f = os.path.join(WT, ".gz_instance")
        if os.path.exists(f):
            v = open(f).read().strip()
    n = int(v) if v else 0
    if not 0 <= n <= 9:
        raise SystemExit(f"GZ_INSTANCE must be 0..9 (got {n}): ports are 25567+N and 25577+N and N>=10 would hit instance 0's RCON port")
    return n


INSTANCE = _instance()
os.environ["GZ_INSTANCE"] = str(INSTANCE)           # children (rcon.py, java launchers) see the same instance

WORK = os.path.join(MAIN, "work") if INSTANCE == 0 else os.path.join(MAIN, "work", f"inst{INSTANCE}")
SERVER_DIR = os.path.join(MAIN, "server") if INSTANCE == 0 else os.path.join(WORK, "server")
GAME_DIR = os.path.join(MAIN, "work", "testgame") if INSTANCE == 0 else os.path.join(WORK, "testgame")   # the flat test client's folder
SHOTS = os.path.join(MAIN, "work", "shots") if INSTANCE == 0 else os.path.join(WORK, "shots")
PORT = 25567 + INSTANCE
RCON_PORT = 25577 + INSTANCE
NAME = "Tester" if INSTANCE == 0 else f"Tester{INSTANCE}"
CLIENT_XMX = "4G" if INSTANCE == 0 else "3G"
JDK = os.path.join(MAIN, "jdk")
DOWNLOADS = os.path.join(MAIN, "downloads")
LIBS = os.path.join(WT, "mod", "build", "libs")      # this checkout's own build output
