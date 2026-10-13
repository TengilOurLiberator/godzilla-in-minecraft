"""reg.py - the event registry.

An *event* is a Minecraft sound event in the namespace `godzilla` (e.g. `godzilla.roar`).  It has one or more
variations (separate ogg files, picked at random by the game).  The renderer function is  fn(r, i) -> audio  where r is a
numpy Generator seeded from the event name and variation index and i is the 0-based variation number.  Audio is (n,)
mono or (n,2) stereo float.  fn may return (audio, {"marker": value}) to put extra facts in the manifest.

    @event("godzilla.roar", variants=3, peak=-1.5, atten=256)
    def roar(r, i): ...

Files go to  out/sounds/<group>/<short>[_<i+1>].ogg  where group is the part of the event name before the first dot.
"""
import zlib

EVENTS = {}

DEFAULTS = dict(
    variants=1,
    stereo=False,        # positional sounds are mono; music and non-positional beds are stereo
    loop=False,          # the file is a seamless loop of a whole number of ticks
    stream=False,        # "stream": true in sounds.json (long music and beds)
    q=6,                 # libvorbis quality
    atten=32,            # attenuation_distance in sounds.json (blocks; Minecraft default is 16)
    limit=True,
    lufs=None, peak=None, fin=None, fout=None, note="", raw=False, ceiling=None,
)


class Event:
    def __init__(self, name, fn, **kw):
        self.name = name
        self.group, self.short = name.split(".", 1)
        self.fn = fn
        d = dict(DEFAULTS)
        d.update(kw)
        self.__dict__.update(d)
        if self.lufs is None and self.peak is None and not self.raw:
            raise ValueError(f"{name}: needs lufs=, peak= or raw=True")

    def file_short(self, i):
        return self.short if self.variants == 1 else f"{self.short}_{i + 1}"

    def seed(self, i):
        return zlib.crc32(f"{self.name}#{i}".encode()) & 0xFFFFFFFF


def event(name, **kw):
    def deco(fn):
        if name in EVENTS:
            raise ValueError("duplicate event " + name)
        EVENTS[name] = Event(name, fn, **kw)
        return fn
    return deco
