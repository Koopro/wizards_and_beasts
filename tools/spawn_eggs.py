#!/usr/bin/env python3
"""Spawn-egg item sprites for Wizards & Beasts.

107 spawn eggs shipped as identical flat 205-byte blobs. `ModModelProvider` notes that
1.21.10 removed `minecraft:item/template_spawn_egg`, and that is still true in 1.21.11 —
vanilla now ships one fully painted PNG per egg — so every mod egg genuinely needs its
own sprite rather than a tinted template.

Rather than invent 107 sprites, each one is vanilla's egg *shading* wearing that
creature's own colours:

  - the silhouette and light falloff come from `slime_spawn_egg`, which is a clean
    single-hue ramp and therefore reads as pure shading once the hue is replaced
  - the base and spot colours come from `beast_skins.SKINS` — the same palette that
    painted the mob — so an egg always matches the creature that hatches from it
  - creatures with hand-drawn skins are not in that table, so their colours are
    sampled from the entity texture instead

Spot layout is seeded from the creature id, so eggs stay individually recognisable in a
full creative tab without any of them being hand-placed.

Run from the repo root:  python tools/spawn_eggs.py [--force] [--only id,id]
"""

import argparse
import glob
import io
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_skins as bs  # noqa: E402
from artgen_common import client_jar, is_regenerable, marker, mix, save, shade  # noqa: E402

ITEM_DIR = "src/main/resources/assets/wizards_and_beasts/textures/item"
ENTITY_DIR = "src/main/resources/assets/wizards_and_beasts/textures/entity"
MARKER = marker("spawn_eggs.py")

TEMPLATE = "slime_spawn_egg"
_template_cache = {}


def template():
    if TEMPLATE in _template_cache:
        return _template_cache[TEMPLATE]
    with client_jar().open("assets/minecraft/textures/item/%s.png" % TEMPLATE) as fh:
        img = Image.open(io.BytesIO(fh.read())).convert("RGBA")
    _template_cache[TEMPLATE] = img
    return img


def luma(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0


def sample_entity_colours(creature_id):
    """Fallback for hand-drawn beasts: the two most-used opaque colours of the skin."""
    path = os.path.join(ENTITY_DIR, creature_id + ".png")
    if not os.path.exists(path):
        return None
    with Image.open(path) as im:
        rgba = im.convert("RGBA")
        data = rgba.get_flattened_data() if hasattr(rgba, "get_flattened_data") else rgba.getdata()
    counts = {}
    for px in data:
        if px[3] < 200:
            continue
        counts[px[:3]] = counts.get(px[:3], 0) + 1
    if not counts:
        return None
    ranked = [c for c, _ in sorted(counts.items(), key=lambda kv: -kv[1])[:6]]
    base = ranked[0]
    # Prefer a spot colour that actually contrasts with the base.
    spot = max(ranked[1:], key=lambda c: abs(luma(c) - luma(base)), default=shade(base + (255,), 0.6)[:3])
    return base, spot


def egg_colours(creature_id):
    skin = bs.SKINS.get(creature_id)
    if skin is not None:
        base, spot = skin.body, skin.accent
        # An accent too close to the body leaves the egg looking plain; fall back to
        # the horn/eye colour, which is chosen for contrast by construction.
        for candidate in (spot, skin.horn, skin.eye, skin.belly):
            if abs(luma(base) - luma(candidate)) >= 0.20:
                return base, candidate
        # Nothing in the palette contrasts enough: derive one by pushing the base away
        # from its own luminance, so the spots never vanish into the shell.
        return base, (shade(base + (255,), 0.45)[:3] if luma(base) > 0.45
                      else mix(base + (255,), (255, 255, 255, 255), 0.55)[:3])
    sampled = sample_entity_colours(creature_id)
    if sampled:
        return sampled
    return (122, 122, 132), (74, 74, 84)


SPOT_LAYOUTS = [
    [(5, 5), (9, 7), (6, 10), (10, 11)],
    [(6, 4), (10, 6), (5, 9), (9, 11), (7, 7)],
    [(5, 6), (9, 5), (7, 9), (10, 10)],
    [(7, 4), (5, 8), (10, 8), (7, 12)],
    [(6, 6), (10, 7), (6, 11), (9, 9)],
]


def render_egg(creature_id):
    base, spot = egg_colours(creature_id)
    src = template()
    sp = src.load()
    w, h = src.size

    # The template is a single hue, so its luminance is pure shading: map that onto a
    # ramp around the creature's colour and the egg keeps vanilla's exact light falloff.
    #
    # The ramp is pivoted rather than linear. A straight lo->hi mix puts the average
    # pixel halfway to the highlight, which washed every egg out to the same pale
    # beige; pivoting makes the lit face land exactly on the creature's own colour and
    # only the top highlight go brighter.
    lo = shade(base + (255,), 0.42)[:3]
    hi = mix(base + (255,), (255, 255, 255, 255), 0.30)[:3]
    PIVOT = 0.62

    def ramp(t, low, mid, high):
        if t <= PIVOT:
            return mix(low + (255,), mid + (255,), t / PIVOT)
        return mix(mid + (255,), high + (255,), (t - PIVOT) / (1.0 - PIVOT))

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    lums = [luma(sp[x, y]) for y in range(h) for x in range(w) if sp[x, y][3] > 8]
    lmin, lmax = min(lums), max(lums)
    span = max(1e-6, lmax - lmin)

    shades = {}
    for y in range(h):
        for x in range(w):
            px = sp[x, y]
            if px[3] < 8:
                continue
            t = (luma(px) - lmin) / span
            shades[(x, y)] = t
            op[x, y] = ramp(t, lo, base, hi)

    d = ImageDraw.Draw(out)
    seed = sum(ord(c) * (i + 3) for i, c in enumerate(creature_id))
    layout = SPOT_LAYOUTS[seed % len(SPOT_LAYOUTS)]
    spot_lo = shade(spot + (255,), 0.5)[:3]
    spot_hi = mix(spot + (255,), (255, 255, 255, 255), 0.22)[:3]
    for i, (cx, cy) in enumerate(layout):
        r = 1 if (seed + i) % 3 else 2
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
                    continue
                if (x, y) not in shades:
                    continue
                d.point((x, y), fill=ramp(shades[(x, y)], spot_lo, spot, spot_hi))
    return out


# Eggs repainted by hand in their creature's redesign (VISUAL_STYLE_REPORT §4). They still carry
# this tool's marker, so the marker check cannot protect them; only --force repaints them.
HAND_SET = {"bowtruckle", "cornish_pixie", "phoenix", "thestral"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped, sampled = [], [], []
    for path in sorted(glob.glob(os.path.join(ITEM_DIR, "*_spawn_egg.png"))):
        creature_id = os.path.basename(path)[:-len("_spawn_egg.png")]
        if only and creature_id not in only:
            continue
        if not args.force and (creature_id in HAND_SET or not is_regenerable(path, MARKER)):
            skipped.append(creature_id)
            continue
        if creature_id not in bs.SKINS:
            sampled.append(creature_id)
        save(render_egg(creature_id), path, MARKER)
        written.append(creature_id)

    print(f"wrote {len(written)} spawn eggs")
    if sampled:
        print(f"colours sampled from the entity skin for {len(sampled)}: {', '.join(sampled)}")
    if skipped:
        print(f"skipped {len(skipped)} hand-authored: {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
