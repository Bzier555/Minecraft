"""Compare terrain height across the torus seams vs. ordinary interior chunk borders.
Reads saved region files directly (run `save-all flush` first).
Usage: python -I seam_terrain.py <world/region dir> [half_width_blocks=1024]"""
import sys, os, struct, zlib, statistics

def read_nbt(buf, i=0):
    def payload(t, i):
        if t == 1: return buf[i:i+1][0], i+1
        if t == 2: return struct.unpack_from(">h", buf, i)[0], i+2
        if t == 3: return struct.unpack_from(">i", buf, i)[0], i+4
        if t == 4: return struct.unpack_from(">q", buf, i)[0], i+8
        if t == 5: return struct.unpack_from(">f", buf, i)[0], i+4
        if t == 6: return struct.unpack_from(">d", buf, i)[0], i+8
        if t == 7:
            n = struct.unpack_from(">i", buf, i)[0]; return buf[i+4:i+4+n], i+4+n
        if t == 8:
            n = struct.unpack_from(">H", buf, i)[0]; return buf[i+2:i+2+n].decode("utf-8", "replace"), i+2+n
        if t == 9:
            et = buf[i]; n = struct.unpack_from(">i", buf, i+1)[0]; i += 5; out = []
            for _ in range(n):
                v, i = payload(et, i); out.append(v)
            return out, i
        if t == 10:
            d = {}
            while True:
                tt = buf[i]; i += 1
                if tt == 0: return d, i
                n = struct.unpack_from(">H", buf, i)[0]; name = buf[i+2:i+2+n].decode("utf-8", "replace"); i += 2+n
                d[name], i = payload(tt, i)
        if t == 11:
            n = struct.unpack_from(">i", buf, i)[0]; return list(struct.unpack_from(f">{n}i", buf, i+4)), i+4+4*n
        if t == 12:
            n = struct.unpack_from(">i", buf, i)[0]; return list(struct.unpack_from(f">{n}q", buf, i+4)), i+4+8*n
        raise ValueError(t)
    t = buf[i]; n = struct.unpack_from(">H", buf, i+1)[0]
    v, _ = payload(t, i+3+n)
    return v

_cache = {}
def chunk(region_dir, cx, cz):
    key = (cx >> 5, cz >> 5)
    if key not in _cache:
        p = os.path.join(region_dir, f"r.{key[0]}.{key[1]}.mca")
        _cache[key] = open(p, "rb").read() if os.path.exists(p) else None
    data = _cache[key]
    if data is None: return None
    idx = 4 * ((cx & 31) + (cz & 31) * 32)
    off = int.from_bytes(data[idx:idx+3], "big") * 4096
    if off == 0: return None
    length = struct.unpack_from(">i", data, off)[0]
    comp = data[off+4]
    raw = data[off+5:off+4+length]
    return read_nbt(zlib.decompress(raw) if comp == 2 else raw)

def heights(region_dir, cx, cz, kind="OCEAN_FLOOR_WG", fallback="OCEAN_FLOOR"):
    c = chunk(region_dir, cx, cz)
    hm = c["Heightmaps"]
    longs = hm.get(fallback) or hm.get(kind)
    bits, per = 9, 64 // 9
    vals = []
    for k in range(256):
        l = longs[k // per] & 0xFFFFFFFFFFFFFFFF
        vals.append(((l >> ((k % per) * bits)) & 0x1FF) - 64)
    return vals  # index = x + z*16

def column(region_dir, bx, bz):
    h = heights(region_dir, bx >> 4, bz >> 4)
    return h[(bx & 15) + (bz & 15) * 16]

def compare(region_dir, pairs):
    d = [abs(column(region_dir, *a) - column(region_dir, *b)) for a, b in pairs]
    return statistics.mean(d), statistics.median(d), max(d), sum(1 for x in d if x > 8) / len(d)

if __name__ == "__main__":
    rd = sys.argv[1]; H = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
    zs = range(-H, H, 4)
    rows = {
        "SEAM x: 1023 | -1024": [((H-1, z), (-H, z)) for z in zs],
        "interior x: 511 | 512": [((511, z), (512, z)) for z in zs],
        "interior x: -1 | 0": [((-1, z), (0, z)) for z in zs],
        "SEAM z: 1023 | -1024": [((x, H-1), (x, -H)) for x in zs],
        "interior z: 511 | 512": [((x, 511), (x, 512)) for x in zs],
    }
    print(f"{'boundary':24} mean   median  max   frac>8")
    for name, pairs in rows.items():
        m, med, mx, f = compare(rd, pairs)
        print(f"{name:24} {m:5.2f}  {med:5.1f}  {mx:4d}   {f:.3f}")
