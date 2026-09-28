#!/usr/bin/env python3
"""Animated block textures for Wizards & Beasts.

The mod shipped zero `.png.mcmeta` files, so every surface that should read as alive —
Floo fire, candle flame, the Great Hall's enchanted ceiling — was frozen. Minecraft
animates any atlas texture whose PNG is a vertical strip of square frames with a
sibling `.mcmeta` describing the timing, so this is a pure asset change: no model,
blockstate or Java edit is involved.

Frames come from the same generator that drew the still texture (`block_textures.py`)
with a phase argument, so an animated flame cannot drift out of sync with the brickwork
behind it — the static parts are byte-identical in every frame by construction.

Where an animation only pulses brightness, few frames plus `"interpolate": true` beats
many frames: Minecraft blends between them, so an eight-frame glow plays smoothly and
costs an eighth of the atlas space.

Run from the repo root:  python tools/animate_textures.py [--force] [--only name,name]
"""

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import block_textures as bt  # noqa: E402
from artgen_common import is_regenerable, marker, mix, posterise, save  # noqa: E402

BLOCK_DIR = bt.BLOCK_DIR
MARKER = marker("animate_textures.py")


def strip(frames):
    """Stack square frames into the vertical strip Minecraft expects."""
    w, h = frames[0].size
    out = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.alpha_composite(f, (0, i * h))
    return out


def write_meta(path, frametime, interpolate):
    meta = {"animation": {"frametime": frametime}}
    if interpolate:
        meta["animation"]["interpolate"] = True
    with open(path + ".mcmeta", "w", encoding="utf-8", newline="\n") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")


def base_texture(name):
    """The still texture an overlay animates: frame 0 when the file is already a strip.

    Reading the whole file made each run animate the previous run's strip, so the three overlays
    grew eight-fold per run (16x128 -> 16x1024 -> 16x8192 by 2026-07-29).
    """
    img = Image.open(os.path.join(BLOCK_DIR, name + ".png")).convert("RGBA")
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


# --------------------------------------------------------------------------- animations

def floo_fire_frames(count=8):
    return [bt.floo_fireplace(True, phase=i / count) for i in range(count)]


def floo_flames_frames(count=8):
    """The standalone green fire. Never interpolated: blending two frames of a flame
    cross-fades one tongue into another instead of moving it, which reads as a pulsing
    smear rather than fire."""
    return [bt.floo_flames(phase=i / count) for i in range(count)]


def candle_frames(count=8):
    return [bt.floating_candle(phase=i / count) for i in range(count)]


def ceiling_frames(count=8):
    """The enchanted ceiling is a night sky, so its stars should twinkle.

    Star pixels are the existing texture's own brightest points — nothing new is
    painted, each one just breathes on its own offset phase."""
    base = base_texture("enchanted_ceiling_tile")
    px = base.load()
    w, h = base.size
    stars = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 8 and (0.299 * r + 0.587 * g + 0.114 * b) > 120:
                stars.append((x, y, (r, g, b, a), bt.noise(3, x, y)))

    frames = []
    for i in range(count):
        f = base.copy()
        fp = f.load()
        for x, y, col, offset in stars:
            t = 0.5 + 0.5 * math.sin(2 * math.pi * (i / count + offset))
            # Peak brightness has to land on the original colour, not below it —
            # dimming from the source and *then* adding white made every frame
            # darker than the art it replaced.
            fp[x, y] = mix(mix(col, (28, 32, 58, 255), 0.42 * (1.0 - t)),
                           (255, 255, 255, 255), 0.22 * t)
        frames.append(f)
    return frames


def floo_grate_frames(count=8):
    """Embers behind the grate: the darkest gaps glow Floo-green and fade."""
    base = base_texture("floo_grate")
    px = base.load()
    w, h = base.size
    gaps = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 8 and (0.299 * r + 0.587 * g + 0.114 * b) < 70:
                gaps.append((x, y, (r, g, b, a), bt.noise(11, x // 2, y // 2)))

    frames = []
    for i in range(count):
        f = base.copy()
        fp = f.load()
        for x, y, col, offset in gaps:
            t = 0.5 + 0.5 * math.sin(2 * math.pi * (i / count + offset))
            # Only the deepest few gaps glow. Lighting every dark pixel turned the
            # grate into green speckle instead of embers seen through ironwork.
            if offset > 0.78:
                fp[x, y] = mix(col, (72, 214, 130, 255), 0.62 * t)
        frames.append(f)
    return frames


def warding_stone_frames(count=8):
    """A warding stone that looks like plain rock reads as scenery.

    This keeps the existing stone and cuts a rune ring into it, pulsing between a
    carved shadow and a lit glyph so the block announces what it does."""
    base = base_texture("warding_stone")
    ring = [(8, 3), (11, 4), (13, 7), (13, 10), (11, 13), (8, 14), (5, 13), (3, 10),
            (3, 7), (5, 4)]
    spokes = [(8, 6), (8, 8), (8, 10), (6, 8), (10, 8)]

    frames = []
    for i in range(count):
        f = base.copy()
        d = ImageDraw.Draw(f)
        t = 0.5 + 0.5 * math.sin(2 * math.pi * i / count)
        carved = (44, 46, 54, 255)
        lit = mix(carved, (126, 214, 236, 255), t)
        for p in ring:
            d.point(p, fill=lit)
        for p in spokes:
            d.point(p, fill=mix(carved, (168, 232, 248, 255), t * 0.85))
        frames.append(f)
    return frames


# name -> (frame builder, frametime, interpolate)
ASSETS = {
    "floo_fireplace_lit": (floo_fire_frames, 3, False),
    "floating_candle": (candle_frames, 4, False),
    "enchanted_ceiling_tile": (ceiling_frames, 6, True),
    "floo_flames": (floo_flames_frames, 2, False),
    "floo_grate": (floo_grate_frames, 5, True),
    "warding_stone": (warding_stone_frames, 5, True),
}

# These two already carry our generator marker; the other three are hand-authored art
# that this only overlays, so they need --force and are called out in the run summary.
OVERLAYS_HAND_ART = {"enchanted_ceiling_tile", "floo_grate", "warding_stone"}


def is_strip(path):
    if not os.path.exists(path):
        return False
    with Image.open(path) as im:
        return im.height > im.width


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true",
                    help="also animate the hand-authored textures this overlays")
    ap.add_argument("--only", default="")
    ap.add_argument("--reanimate", action="store_true",
                    help="rebuild overlays that are already animated strips from their first frame")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped, kept = [], [], []
    for name, (builder, frametime, interpolate) in ASSETS.items():
        if only and name not in only:
            continue
        path = os.path.join(BLOCK_DIR, name + ".png")
        if not args.force and not is_regenerable(path, MARKER):
            skipped.append(name)
            continue
        if name in OVERLAYS_HAND_ART and not args.reanimate and is_strip(path):
            # The shipped overlays are 512-frame strips (three compounding runs, see
            # base_texture). Rebuilding them gives the designed 8 frames, which is a visible
            # change to approved art -- so it takes an explicit flag, never a plain rerun.
            kept.append(name)
            continue
        frames = builder()
        # Posterise the assembled strip, not each frame: quantising frames separately picks
        # a slightly different palette per frame, and the texture then shimmers as it plays.
        save(posterise(strip(frames), 10), path, MARKER)
        write_meta(path, frametime, interpolate)
        written.append(f"{name} ({len(frames)}f)")

    print(f"animated {len(written)}: {', '.join(written) or '-'}")
    if skipped:
        print(f"skipped (needs --force, overlays hand art): {', '.join(skipped)}")
    if kept:
        print(f"kept (already animated; --reanimate rebuilds, a visible change): {', '.join(kept)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
