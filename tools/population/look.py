"""Test helper: put the spectator camera next to an entity (or a position) and take a screenshot.

  python -I tools\\population\\look.py "@e[type=godzilla:bus,limit=1,sort=nearest]" -5 3.5 -7 shotname [cropx0 cropy0 cropx1 cropy1 scale]
  python -I tools\\population\\look.py "pos:10,72,-104" -20 8 -16 shotname

The selector is evaluated from the player (execute at Tester<N>), so "nearest" means nearest to the camera's current place.
dx dy dz is the camera offset from the target; the camera then faces the target. Optional crop: x0 y0 x1 y1 scale -> <name>_crop.png.
"""
import os
import re
import subprocess
import sys

TOOLS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, TOOLS)
import gz_env  # noqa: E402
from rcon import Rcon  # noqa: E402


def main():
    sel, dx, dy, dz, name = sys.argv[1], *map(float, sys.argv[2:5]), sys.argv[5]
    r = Rcon()
    me = gz_env.NAME
    if sel.startswith("pos:"):
        x, y, z = map(float, sel[4:].split(","))
    else:
        out = r.cmd(f"execute as {me} at @s run data get entity {sel} Pos")
        m = re.search(r"\[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]", out)
        if not m:
            print("no entity:", out)
            return 1
        x, y, z = map(float, m.groups())
    print(r.cmd(f"tp {me} {x + dx} {y + dy} {z + dz} facing {x} {y + 0.5} {z}"))
    r.close()
    import time
    time.sleep(1.3)
    tc = os.path.join(TOOLS, "testclient.py")
    print(subprocess.run([sys.executable, "-I", tc, "shot", name], capture_output=True, text=True).stdout.strip())
    if len(sys.argv) >= 11:
        from PIL import Image
        x0, y0, x1, y1, s = map(int, sys.argv[6:11])
        src = os.path.join(gz_env.SHOTS, name + ".png")
        im = Image.open(src).convert("RGB").crop((x0, y0, x1, y1))
        im = im.resize((im.width * s, im.height * s), Image.NEAREST)
        im.save(os.path.join(gz_env.SHOTS, name + "_crop.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
