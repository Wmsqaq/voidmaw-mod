#!/usr/bin/env python3
"""Generate Void Maw textures with the stdlib only (no Pillow needed).

Outputs:
  src/main/resources/assets/voidmaw/textures/item/singularity_core.png  (16x16)
  src/main/resources/assets/voidmaw/icon.png                            (128x128)

Re-run from the repo root:  python tools/gen_textures.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_OUT = os.path.join(ROOT, "src/main/resources/assets/voidmaw/textures/item/singularity_core.png")
ICON_OUT = os.path.join(ROOT, "src/main/resources/assets/voidmaw/icon.png")


def write_png(path, width, height, rows):
    """rows: list of height lists of (r, g, b, a) tuples."""
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(struct.pack("4B", *px) for px in row)

    def chunk(tag, data):
        payload = tag + data
        return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", path)


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(v)))


def item_texture():
    size = 16
    cx = cy = 7.5
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - cx, y - cy)
            if d < 5.0:
                # Event horizon: near-black with a faint violet sheen.
                sheen = 10 + 8 * math.sin(x * 1.7 + y * 2.3)
                row.append((clamp(4 + sheen * 0.3), clamp(2 + sheen * 0.2), clamp(10 + sheen), 255))
            elif d < 7.2:
                # Accretion rim, brighter on two arcs to suggest a swirl.
                angle = math.atan2(y - cy, x - cx)
                arc = 0.55 + 0.45 * math.sin(angle * 2.0 + d * 1.5)
                fade = 1.0 - (d - 5.0) / 2.2
                alpha = clamp(255 * fade)
                row.append((clamp(200 * arc), clamp(120 * arc), clamp(255 * arc), alpha))
            else:
                row.append((0, 0, 0, 0))
        rows.append(row)
    write_png(ITEM_OUT, size, size, rows)


def icon_texture():
    size = 128
    cx = cy = size / 2
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            # Ellipse squash fakes a tilted accretion disk.
            de = math.hypot(dx, dy * 2.4)
            r = g = b = a = 0

            # Starfield sparkle far out.
            if de > 46 and ((x * 31 + y * 17) % 97) == 3:
                r, g, b, a = 220, 210, 255, 180

            if de < 44:
                # Event horizon.
                sheen = 12 * math.sin(x * 0.35 + y * 0.21)
                r, g, b, a = clamp(5 + sheen * 0.3), clamp(3 + sheen * 0.2), clamp(12 + sheen), 255
            elif de < 56:
                # Inner violet glow.
                t = (de - 44) / 12.0
                arc = 0.6 + 0.4 * math.sin(math.atan2(dy, dx) * 2.0)
                r, g, b = clamp(210 * arc), clamp(130 * arc), clamp(255 * arc)
                a = clamp(255 * (1.0 - t))
            elif de < 88:
                # Outer accretion disk, cooling from orange to deep violet.
                t = (de - 56) / 32.0
                arc = 0.5 + 0.5 * math.sin(math.atan2(dy, dx) * 2.0 + t * 4.0)
                r = clamp(255 - 170 * t)
                g = clamp(120 - 100 * t + 40 * arc)
                b = clamp(90 + 160 * arc)
                a = clamp(190 * (1.0 - t) ** 1.5)

            if d < 30 and a > 0 and de >= 44:
                a = 255  # keep the core's neighbourhood solid behind the glow
            row.append((clamp(r), clamp(g), clamp(b), clamp(a)))
        rows.append(row)
    write_png(ICON_OUT, size, size, rows)


if __name__ == "__main__":
    item_texture()
    icon_texture()
