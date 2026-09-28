#!/usr/bin/env python3
"""Skin for the escaped Chocolate Frog entity.

`ChocolateFrogModel` is seven boxes; this paints the box-UV net for each one, in the
same order and at the same `texOffs`. The two files are a pair — move a box in the Java
and its island moves here, or the frog comes out wearing the wrong faces.

Box UV layout, for a box at (u, v) with size (dx, dy, dz):

    up     (u + dz,           v)       dx x dz
    down   (u + dz + dx,      v)       dx x dz
    east   (u,                v + dz)  dz x dy
    north  (u + dz,           v + dz)  dx x dy
    west   (u + dz + dx,      v + dz)  dz x dy
    south  (u + 2*dz + dx,    v + dz)  dx x dy

The frog is chocolate, not an amphibian: no green anywhere, and the read comes from
directional shading plus a moulded sheen on the up faces rather than from markings.

Run from the repo root:  python tools/chocolate_frog.py [--force]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
OUT = os.path.join(ASSETS, "textures", "entity", "chocolate_frog.png")
MARKER = marker("chocolate_frog.py")

SIZE = 32

# Dark couverture, because the mould detail has to read against a light world. The up
# faces are lifted far enough to look glazed rather than merely lit.
CHOC = hx("4a2a16")
CHOC_TOP = hx("6f4223")
CHOC_LOW = hx("2b170b")
MILK = hx("7c4c28")
SHEEN = hx("a9713d")
GOLD = hx("e8c463")
GOLD_LOW = hx("a8843a")
PUPIL = hx("1b1008")

# Same directional key as the item cuboids and the beast skins, so a frog sitting next
# to a dropped frog item is lit from the same place.
FACE_SHADE = {"up": 1.26, "down": 0.62, "north": 1.06, "south": 0.88, "east": 0.82, "west": 1.00}


def shade_rgba(rgba, f):
    """Multiply toward white above 1.0, toward the shadow colour below it."""
    if f >= 1.0:
        return mix(rgba, hx("fff3dd"), min(0.5, (f - 1.0) * 0.9))
    return mix(rgba, hx("120b06"), min(0.6, (1.0 - f) * 0.9))


def speck(seed, x, y):
    n = (x * 73856093 + y * 19349663 + seed * 83492791) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def fill(img, box, colour, seed=0, grain=0.10, sheen_top=False):
    """Flat fill with a little cocoa grain, optionally glazed along its top edge."""
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = colour
            g = (speck(seed, x, y) - 0.5) * 2.0 * grain
            c = mix(c, hx("ffffff") if g > 0 else hx("000000"), abs(g))
            if sheen_top and y - y0 < max(1, (y1 - y0) // 4):
                c = mix(c, SHEEN, 0.45)
            px[x, y] = c


def faces(u, v, dx, dy, dz):
    """The six face rectangles of one box's UV net."""
    return {
        "up": (u + dz, v, u + dz + dx, v + dz),
        "down": (u + dz + dx, v, u + dz + dx + dx, v + dz),
        "east": (u, v + dz, u + dz, v + dz + dy),
        "north": (u + dz, v + dz, u + dz + dx, v + dz + dy),
        "west": (u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy),
        "south": (u + 2 * dz + dx, v + dz, u + 2 * dz + dx + dx, v + dz + dy),
    }


# Per-face grain offsets. These were `hash(name) % 97`, and Python salts `str` hashes per process,
# so every run painted a different grain. The values are the ones the shipped texture was painted
# with (recovered by matching it face by face, 2026-09-28); a rerun now reproduces it exactly.
FACE_SEED = {"up": 14, "down": 18, "east": 73, "north": 35, "west": 73, "south": 5}


def paint_box(img, u, v, dx, dy, dz, base, seed, belly=None, glaze=True):
    for name, box in faces(u, v, dx, dy, dz).items():
        colour = belly if (belly is not None and name == "down") else base
        fill(img, box, shade_rgba(colour, FACE_SHADE[name]), seed=seed + FACE_SEED[name],
             sheen_top=glaze and name in ("north", "east", "west", "south"))


def paint_eye(img, u, v):
    """A gold foil eye with a slit pupil on the outward faces."""
    dx = dy = dz = 2
    for name, box in faces(u, v, dx, dy, dz).items():
        fill(img, box, shade_rgba(GOLD if name != "down" else GOLD_LOW, FACE_SHADE[name]),
             seed=11, grain=0.06)
    px = img.load()
    # Pupil on the four side faces only: an eye that stares out of the top of its own
    # socket looks like a bead, not an eye.
    for name in ("north", "east", "west", "south"):
        x0, y0, x1, y1 = faces(u, v, dx, dy, dz)[name]
        px[x0, y0 + 1] = PUPIL
        px[x1 - 1, y0 + 1] = PUPIL


def build():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # body 5x3x7 @ (0,0) — milk-chocolate underside, the way a moulded frog is poured.
    paint_box(img, 0, 0, 5, 3, 7, CHOC, seed=1, belly=MILK)
    # head 5x3x3 @ (0,12)
    paint_box(img, 0, 12, 5, 3, 3, CHOC, seed=2, belly=MILK)
    # eye 2x2x2 @ (18,12) — one island, both eyes
    paint_eye(img, 18, 12)
    # throat 3x1x2 @ (0,20) — lighter, it is the part that catches the light when it pulses
    paint_box(img, 0, 20, 3, 1, 2, MILK, seed=4, glaze=False)
    # front leg 1x3x1 @ (12,20)
    paint_box(img, 12, 20, 1, 3, 1, CHOC_LOW, seed=5, glaze=False)
    # back leg 2x3x3 @ (18,20)
    paint_box(img, 18, 20, 2, 3, 3, CHOC, seed=6, belly=CHOC_LOW)
    return posterise(img, 14)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite art this tool did not write")
    args = ap.parse_args()

    if not args.force and not is_regenerable(OUT, MARKER):
        print("skip (hand-authored): " + OUT)
        return
    save(build(), OUT, MARKER)
    print("wrote " + OUT)


if __name__ == "__main__":
    main()
