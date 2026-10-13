"""Generate the 64x64 texture of the test entity (no image library needed).

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\make_test_texture.py

Bright orange/teal checkerboard with a darker border per 8 px cell so box faces and UV mistakes are obvious in screenshots.
"""
import os, struct, zlib

W = H = 64
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "mod", "src", "client", "resources", "assets", "godzilla", "textures", "entity", "test_dummy.png")


def pixel(x, y):
    cell = ((x // 4) + (y // 4)) % 2
    base = (255, 140, 30) if cell else (30, 180, 170)
    # darker edge on each 16 px tile so face boundaries are visible
    if x % 16 in (0, 15) or y % 16 in (0, 15):
        base = tuple(int(c * 0.45) for c in base)
    # hue shift per 16 px tile row so the parts differ
    shift = (y // 16) * 25
    return (min(255, base[0] + shift), base[1], max(0, base[2] - shift), 255)


rows = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(W)) for y in range(H))


def chunk(tag, data):
    c = struct.pack(">I", len(data)) + tag + data
    return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "wb").write(png)
print("wrote", OUT, len(png), "bytes")
