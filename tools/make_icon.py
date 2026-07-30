"""Draw the Plumbline icon as pixel art and write it out as a PNG.

No image libraries: PNG is just zlib-compressed scanlines in a few chunks.

Composition: a brass plumb bob on a taut, dead-vertical line, with a skewed
wireframe box behind it. The line is the only truly straight thing in the frame --
that is the whole point of the mod.

  python make_icon.py            # writes icon.png (256) + icon_64.png, prints previews
  python make_icon.py --no-box   # bob and line only
"""
import math
import struct
import sys
import zlib

S = 64                      # art is drawn at 64x64
SCALE = 4                   # nearest-neighbour upscale -> 256x256
DRAW_BOX = "--no-box" not in sys.argv

BG        = (0x1B, 0x20, 0x28)
BOX       = (0x3E, 0x2B, 0x33)
BOX_LIT   = (0x53, 0x39, 0x42)
LINE      = (0xE8, 0xE4, 0xDA)
LINE_DIM  = (0x9A, 0x98, 0x92)
BRASS_HI  = (0xF2, 0xC9, 0x77)
BRASS_MID = (0xC9, 0x9A, 0x3B)
BRASS_LO  = (0x8A, 0x64, 0x20)
COLLAR    = (0x6E, 0x5C, 0x3A)

px = [[BG for _ in range(S)] for _ in range(S)]


def put(x, y, c):
    if 0 <= x < S and 0 <= y < S:
        px[y][x] = c


def line(x0, y0, x1, y1, c):
    """Bresenham."""
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        put(x0, y0, c)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


# ---------------------------------------------------------------- skewed box
if DRAW_BOX:
    # front face, rotated ~10 degrees so it sits visibly off-true
    cx, cy, w, h = 31.0, 32.0, 15.0, 12.0
    ang = math.radians(13.0)
    ca, sa = math.cos(ang), math.sin(ang)
    front = []
    for ox, oy in ((-w, -h), (w, -h), (w, h), (-w, h)):
        front.append((cx + ox * ca - oy * sa, cy + ox * sa + oy * ca))
    dx, dy = 6.0, -5.0                       # depth offset
    back = [(x + dx, y + dy) for x, y in front]

    for i in range(4):
        line(*back[i], *back[(i + 1) % 4], BOX)          # back face
    for i in range(4):
        line(*front[i], *back[i], BOX)                    # connecting edges
    for i in range(4):
        line(*front[i], *front[(i + 1) % 4], BOX_LIT)     # front face, brighter

# --------------------------------------------------------------- plumb line
# dead vertical, 2px, from the very top edge down to the bob's collar
for y in range(3, 27):
    put(31, y, LINE)
    put(32, y, LINE_DIM)

# --------------------------------------------------------------- plumb bob
# half-width per row: collar, swelling barrel, long taper to a single point
PROFILE = [
    (27, 3), (28, 3), (29, 3),                                    # collar
    (30, 5), (31, 6), (32, 7), (33, 7), (34, 8), (35, 8),          # shoulder
    (36, 8), (37, 8), (38, 7), (39, 7),                            # barrel
    (40, 6), (41, 6), (42, 5), (43, 5), (44, 4), (45, 4),          # taper
    (46, 3), (47, 3), (48, 2), (49, 2), (50, 1), (51, 1), (52, 0),
]
CENTER = 31

for y, hw in PROFILE:
    if y <= 29:
        for x in range(CENTER - hw, CENTER + hw + 1):
            put(x, y, COLLAR)
        put(CENTER - hw, y, BRASS_LO)
        put(CENTER + hw, y, BRASS_LO)
        continue
    for x in range(CENTER - hw, CENTER + hw + 1):
        rel = x - (CENTER - hw)
        span = (2 * hw) + 1
        if span <= 2:
            c = BRASS_MID
        elif rel <= max(0, span // 4):
            c = BRASS_HI                     # light from upper-left
        elif rel >= span - max(1, span // 3):
            c = BRASS_LO
        else:
            c = BRASS_MID
        put(x, y, c)

# a single specular pixel so the brass reads as metal, not plastic
put(CENTER - 4, 33, (0xFF, 0xEE, 0xC0))
put(CENTER - 4, 34, (0xFF, 0xEE, 0xC0))


# ------------------------------------------------------------------- output
def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)                        # filter type 0
        for (r, g, b) in row:
            raw += bytes((r, g, b, 255))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    return len(png)


def upscale(rows, n):
    out = []
    for row in rows:
        big = []
        for c in row:
            big.extend([c] * n)
        for _ in range(n):
            out.append(list(big))
    return out


def preview(rows, step=1):
    ramp = {BG: ".", BOX: ":", BOX_LIT: "=", LINE: "|", LINE_DIM: "!",
            BRASS_HI: "@", BRASS_MID: "#", BRASS_LO: "%", COLLAR: "+",
            (0xFF, 0xEE, 0xC0): "*"}
    lines = []
    for y in range(0, len(rows), step):
        lines.append("".join(ramp.get(rows[y][x], "?")
                             for x in range(0, len(rows[0]), step)))
    return "\n".join(lines)


n256 = write_png("icon.png", upscale(px, SCALE))
n64 = write_png("icon_64.png", px)

print("icon.png   %dx%d  (%d bytes)" % (S * SCALE, S * SCALE, n256))
print("icon_64.png %dx%d  (%d bytes)" % (S, S, n64))
print("\n--- 64x64 ---")
print(preview(px))
print("\n--- readability check, sampled at 32x32 ---")
print(preview(px, 2))
