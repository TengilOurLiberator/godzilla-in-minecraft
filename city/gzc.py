"""Reader and writer for the .gzc city volume (see docs/DESIGN.md, 'City file format')."""
import gzip
import struct

import numpy as np

MAGIC = b'GZC1'


def write_gzc(path, vol, states, origin):
    """vol is (sizeY, sizeZ, sizeX) of palette indices into `states`; the palette is compacted (air stays 0)."""
    used = np.unique(vol)
    assert used[0] == 0 and states[0] == 'minecraft:air', 'palette index 0 must be air and present'
    remap = np.zeros(len(states), np.uint16)
    pal = []
    for new, old in enumerate(used):
        remap[old] = new
        pal.append(states[old])
    cells = remap[vol]
    sy, sz, sx = vol.shape
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, compresslevel=6, mtime=0) as f:
            f.write(MAGIC)
            f.write(struct.pack('<3i', sx, sy, sz))
            f.write(struct.pack('<3i', *origin))
            f.write(struct.pack('<H', len(pal)))
            for s in pal:
                bs = s.encode('utf-8')
                f.write(struct.pack('<H', len(bs)))
                f.write(bs)
            f.write(cells.astype('<u2').tobytes())
    return pal, cells


def read_gzc(path):
    """Independent reader used by the validation step.  Returns (size, origin, palette, cells[y,z,x])."""
    with gzip.open(path, 'rb') as f:
        data = f.read()
    assert data[:4] == MAGIC, 'bad magic'
    sx, sy, sz = struct.unpack_from('<3i', data, 4)
    ox, oy, oz = struct.unpack_from('<3i', data, 16)
    n = struct.unpack_from('<H', data, 28)[0]
    pos = 30
    pal = []
    for _ in range(n):
        ln = struct.unpack_from('<H', data, pos)[0]
        pos += 2
        pal.append(data[pos:pos + ln].decode('utf-8'))
        pos += ln
    cnt = sx * sy * sz
    cells = np.frombuffer(data, dtype='<u2', count=cnt, offset=pos)
    assert pos + cnt * 2 == len(data), 'trailing or missing data'
    return (sx, sy, sz), (ox, oy, oz), pal, cells.reshape(sy, sz, sx)
