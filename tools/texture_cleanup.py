#!/usr/bin/env python3
"""One-off clean-ups for hand-made textures that no generator owns (texture audit 2026-09-27).

Each entry fixes a measured defect without redrawing the art:

- `gui/handbook/book.png`: 706 colours — paper texture made of +/-3-level variation that no
  player can see at GUI scale, i.e. photographic noise. Posterised to 16 colours.
- `gui/obscurial/stress_meter.png`: 512x72 drawn at 256 GUI px — two texels per screen pixel,
  so nearest sampling dropped three of every four, beside 1:1 HUD art — and a 1,739-colour
  painted vortex. Box-reduced to 256x36 (drawn 1:1; `ObscurialStressMeterLayout` halved with it),
  alpha made hard, posterised to 20 colours keeping far-off features.
- `gui/handbook/emblem.png`: 102 alpha levels — an anti-aliased Ministry "M". Pixel art has hard
  edges: alpha is thresholded at half, and every texel snapped to three purples built around the
  dominant one, `#3E1F47`, which `WizardsPalette.MINISTRY` is sampled from and must not move.

Each output is marked, so running this twice is a no-op check rather than a second pass.

Run from the repo root:  python tools/texture_cleanup.py [--force]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, posterise, save  # noqa: E402

TEX = "src/main/resources/assets/wizards_and_beasts/textures"
MARK = marker("texture_cleanup.py")

EMBLEM_PURPLE = (62, 31, 71)
EMBLEM_RAMP = [(44, 20, 51), EMBLEM_PURPLE, (86, 50, 98)]


def _keep_features(src, out, limit=40):
    a, b = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            p, q = a[x, y], b[x, y]
            if p[3] and sum((p[i] - q[i]) ** 2 for i in range(3)) > limit * limit:
                b[x, y] = p
    return out


def stress_meter(img):
    src = img.convert("RGBA")
    if src.size == (256, 36):
        return src  # already cleaned
    small = src.reduce(2)
    px = small.load()
    for y in range(small.height):
        for x in range(small.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
    out = _keep_features(small, posterise(small, 20).convert("RGBA"))
    return _vortex(out)


# The meter's right end: the Obscurus as a pixel spiral. Halving the painted original made mud,
# so it is drawn at size — three arms on a dark disc, flat palette steps, a lit core.
VORTEX_CENTRE, VORTEX_R = (239, 18), 16
VORTEX_RAMP = [(22, 20, 32), (40, 30, 52), (104, 28, 44), (168, 52, 68), (226, 120, 132)]


def _vortex(img):
    import math
    px = img.load()
    cx, cy = VORTEX_CENTRE
    for y in range(img.height):
        for x in range(img.width):
            dx, dy = x - cx + 0.5, y - cy + 0.5
            d = math.hypot(dx, dy)
            if x >= cx - VORTEX_R - 2 and d > VORTEX_R:
                if x > VORTEX_CENTRE[0] - VORTEX_R - 1:
                    px[x, y] = (0, 0, 0, 0)
                continue
            if d > VORTEX_R:
                continue
            arm = 0.5 + 0.5 * math.sin(3 * math.atan2(dy, dx) + d * 0.55)
            if d < 2.5:
                tone = 4
            elif d < 5:
                tone = 3 if arm > 0.4 else 2
            else:
                tone = (2 if arm > 0.72 else 1) if d < VORTEX_R - 2 else (1 if arm > 0.6 else 0)
            px[x, y] = VORTEX_RAMP[tone] + (255,)
    return img


def book(img):
    # Posterise the paper, but keep any texel the palette would move far: the thin gold rule in
    # the frame is a minority colour and median-cut drops it first.
    src = img.convert("RGBA")
    out = posterise(src, 16).convert("RGBA")
    a, b = src.load(), out.load()
    for y in range(src.height):
        for x in range(src.width):
            p, q = a[x, y], b[x, y]
            if sum((p[i] - q[i]) ** 2 for i in range(3)) > 40 * 40:
                b[x, y] = p
    return out


def emblem(img):
    out = img.convert("RGBA").copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a < 128:
                px[x, y] = (0, 0, 0, 0)
                continue
            best = min(EMBLEM_RAMP, key=lambda c: (c[0] - r) ** 2 + (c[1] - g) ** 2 + (c[2] - b) ** 2)
            px[x, y] = best + (255,)
    return out


JOBS = {
    "gui/handbook/book.png": book,
    "gui/handbook/emblem.png": emblem,
    "gui/obscurial/stress_meter.png": stress_meter,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    for rel, fix in JOBS.items():
        path = os.path.join(TEX, rel)
        if is_regenerable(path, MARK) and not args.force:
            print(f"{rel}: already cleaned")
            continue
        if not args.force and not is_regenerable(path, MARK):
            print(f"{rel}: hand-made; --force to clean it once")
            continue
        save(fix(Image.open(path)), path, MARK)
        print("cleaned", rel)
    return 0


if __name__ == "__main__":
    sys.exit(main())
