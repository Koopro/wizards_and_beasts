#!/usr/bin/env python3
"""Field-journal plates for the Bestiary, drawn from the creatures themselves.

Implements documentation/BESTIARY_ILLUSTRATION_SPEC.md. Every stage is derived from the creature's real rig and
skin through tools/beast_preview.py, so a plate cannot drift from the mob it shows — repaint a skin, re-run this,
and the journal is right again. Hand-painted art may replace any file: give it a different marker (or none) and
this tool leaves it alone unless --force.

Five stages per creature, 96x96 RGBA on transparent (the journal's inset supplies the paper):

  silhouette/<id>.png  the shape in ink, edges roughened — "a shape glimpsed"
  sketch/<id>.png      graphite contour and hatching, no colour — "field sketch"
  portrait/<id>.png    muted colour wash with an ink line — "portrait"
  studied/<id>.png     the portrait with the naturalist's annotation: scale bar, leader ticks, corner marks
  signature/<id>.png   the creature smaller, beside the motif of its signature behaviour (MOTIFS)

Run from the repo root:  python tools/bestiary_journal_art.py [--force] [--only id,id]
"""
import argparse
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_preview as bp  # noqa: E402
from artgen_common import is_regenerable, marker, save  # noqa: E402

OUT = "src/main/resources/assets/wizards_and_beasts/textures/gui/bestiary/journal"
MARKER = marker("bestiary_journal_art.py")
SIZE = 96
INK = (43, 43, 43)          # FIELD_NOTEBOOK ink
GRAPHITE = (85, 83, 80)     # FIELD_NOTEBOOK muted
WASH = (205, 188, 149)      # FIELD_NOTEBOOK base — the paper the wash is laid on
GOLD = (201, 151, 58)       # FIELD_NOTEBOOK accent
SILVER = (196, 201, 210)
PENCIL = (62, 60, 57)       # graphite pressed harder: GRAPHITE alone sits at under 3:1 on the kraft

# The five showcase entries. Adding a creature here is the whole of adding it to the pipeline; its motif says
# what its signature illustration shows (spec §4).
MOTIFS = {
    "niffler": "coins",
    "bowtruckle": "lock",
    "unicorn": "moon_hair",
    "mooncalf": "dance",
    "hippogriff": "bow",
}


def render(cid, size=SIZE, margin=10):
    img = bp.render_image(cid, size=size, margin=margin)
    if img is None:
        raise SystemExit(f"{cid}: no rig or skin to draw from")
    return img.convert("RGBA")


def alpha_of(img):
    return img.split()[3]


def roughen(mask, seed, amount=0.18):
    """Bite a little out of a mask's edge so it reads drawn, not rendered."""
    rnd = random.Random(seed)
    edge = mask.filter(ImageFilter.FIND_EDGES)
    px = mask.load()
    ex = edge.load()
    for y in range(mask.height):
        for x in range(mask.width):
            if ex[x, y] > 0 and rnd.random() < amount:
                px[x, y] = 0
    return mask


def silhouette(cid):
    mask = roughen(alpha_of(render(cid)).point(lambda a: 255 if a > 40 else 0), hash_seed(cid, 1))
    out = Image.new("RGBA", (SIZE, SIZE), INK + (0,))
    out.putalpha(mask.point(lambda a: 220 if a else 0))
    return out


def contour(img):
    """Ink line where the shape ends or the shading breaks."""
    lum = ImageOps.grayscale(img)
    lines = ImageChops.lighter(alpha_of(img).filter(ImageFilter.FIND_EDGES),
                               lum.filter(ImageFilter.FIND_EDGES).point(lambda v: 255 if v > 48 else 0))
    return ImageChops.multiply(lines.point(lambda v: 255 if v > 60 else 0), alpha_of(img).point(lambda a: 255 if a else 0)
                               .filter(ImageFilter.MaxFilter(3)))


def sketch(cid):
    img = render(cid)
    lum = ImageOps.grayscale(img)
    shape = alpha_of(img).point(lambda a: 255 if a > 40 else 0)
    rnd = random.Random(hash_seed(cid, 2))
    out = Image.new("RGBA", (SIZE, SIZE), GRAPHITE + (0,))
    draw = ImageDraw.Draw(out)
    lp, sp = lum.load(), shape.load()
    # Hatching: diagonal strokes where the body is in shadow, sparser in midtone.
    for y in range(SIZE):
        for x in range(SIZE):
            if not sp[x, y]:
                continue
            shade = lp[x, y]
            if (shade < 90 and (x + y) % 3 == 0) or (shade < 150 and (x + y) % 6 == 0):
                if rnd.random() < 0.85:
                    draw.point((x, y), fill=PENCIL + (200,))
    line = contour(img)
    lpx = line.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if lpx[x, y] and rnd.random() < 0.9:
                draw.point((x, y), fill=PENCIL + (255,))
    return out


def portrait(cid):
    img = render(cid)
    rgb = img.convert("RGB")
    grey = ImageOps.grayscale(rgb).convert("RGB")
    muted = Image.blend(rgb, grey, 0.3)
    washed = Image.blend(muted, Image.new("RGB", rgb.size, WASH), 0.18)
    washed = washed.quantize(14, dither=Image.Dither.NONE).convert("RGB")
    out = washed.convert("RGBA")
    out.putalpha(alpha_of(img).point(lambda a: 255 if a > 40 else 0))
    ink = Image.new("RGBA", out.size, INK + (255,))
    out.paste(ink, (0, 0), contour(img).point(lambda v: 210 if v else 0))
    return out


def studied(cid):
    out = portrait(cid)
    draw = ImageDraw.Draw(out)
    box = alpha_of(out).getbbox() or (8, 8, SIZE - 8, SIZE - 8)
    # Scale bar under the creature, with end ticks — a naturalist measures.
    y = min(SIZE - 4, box[3] + 2)
    draw.line([(box[0], y), (box[2], y)], fill=INK + (200,))
    for xx in (box[0], (box[0] + box[2]) // 2, box[2]):
        draw.line([(xx, y - 2), (xx, y + 1)], fill=INK + (200,))
    # Leader ticks from three features to the margin, as if labelled.
    for i, (fx, fy) in enumerate(((box[0] + 4, box[1] + 6), (box[2] - 4, (box[1] + box[3]) // 2), (box[0] + 6, box[3] - 6))):
        mx = 3 if fx < SIZE // 2 else SIZE - 4
        draw.line([(fx, fy), (mx, fy - 4 + i * 2)], fill=GRAPHITE + (170,))
        draw.ellipse([fx - 1, fy - 1, fx + 1, fy + 1], fill=INK + (220,))
    # Corner marks of a mounted plate.
    for cx, cy, dx, dy in ((1, 1, 1, 1), (SIZE - 2, 1, -1, 1), (1, SIZE - 2, 1, -1), (SIZE - 2, SIZE - 2, -1, -1)):
        draw.line([(cx, cy), (cx + 5 * dx, cy)], fill=INK + (200,))
        draw.line([(cx, cy), (cx, cy + 5 * dy)], fill=INK + (200,))
    return out


def signature(cid):
    small = portrait(cid).resize((64, 64), Image.Resampling.NEAREST)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(out)
    motif = MOTIFS[cid]
    rnd = random.Random(hash_seed(cid, 3))
    if motif == "coins":
        for _ in range(9):
            x, y = rnd.randint(8, 86), rnd.randint(62, 88)
            draw.ellipse([x - 4, y - 3, x + 4, y + 3], fill=GOLD + (255,), outline=INK + (230,))
        out.alpha_composite(small, (16, 4))
    elif motif == "lock":
        draw.rectangle([64, 52, 82, 70], fill=GRAPHITE + (255,), outline=INK + (255,))
        draw.arc([66, 40, 80, 58], 180, 360, fill=INK + (255,), width=2)
        draw.line([(73, 59), (73, 64)], fill=WASH + (255,), width=2)
        draw.line([(46, 62), (62, 60)], fill=(110, 84, 50, 255), width=2)  # the twig picking it
        out.alpha_composite(small, (0, 16))
    elif motif == "moon_hair":
        draw.ellipse([66, 4, 90, 28], fill=SILVER + (255,), outline=INK + (200,))
        draw.ellipse([72, 2, 96, 26], fill=(0, 0, 0, 0))
        for i in range(5):
            y = 70 + i * 4
            draw.line([(8, y), (40 + i * 6, y - 6)], fill=SILVER + (230,), width=1)
        out.alpha_composite(small, (24, 20))
    elif motif == "dance":
        draw.ellipse([6, 4, 30, 28], fill=SILVER + (255,), outline=INK + (200,))
        for r in (18, 28, 38):
            draw.arc([48 - r, 70 - r // 3, 48 + r, 70 + r // 3], 0, 360, fill=GRAPHITE + (170,))
        out.alpha_composite(small, (16, 22))
    elif motif == "bow":
        # A small figure bowing low, the creature answering: the one courtesy canon insists on.
        fx, fy = 14, 70
        draw.ellipse([fx + 8, fy - 2, fx + 14, fy + 4], outline=INK + (255,))
        draw.line([(fx, fy + 16), (fx + 10, fy + 6)], fill=INK + (255,), width=2)
        draw.line([(fx + 10, fy + 6), (fx + 2, fy + 2)], fill=INK + (255,), width=1)
        draw.line([(fx, fy + 16), (fx - 2, fy + 24)], fill=INK + (255,), width=1)
        draw.line([(fx, fy + 16), (fx + 4, fy + 24)], fill=INK + (255,), width=1)
        out.alpha_composite(small, (30, 12))
    return out


def hash_seed(cid, stage):
    # Stable across runs: Python's hash() is salted per process (tasks/lessons.md, art pipeline).
    return sum((i + 1) * ord(c) for i, c in enumerate(cid)) * 31 + stage


STAGES = {"silhouette": silhouette, "sketch": sketch, "portrait": portrait, "studied": studied, "signature": signature}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s for s in args.only.split(",") if s}
    for cid in MOTIFS:
        if only and cid not in only:
            continue
        for stage, draw in STAGES.items():
            path = os.path.join(OUT, stage, cid + ".png")
            if os.path.exists(path) and not args.force and not is_regenerable(path, MARKER):
                print(f"keeping {path} (not ours)")
                continue
            img = draw(cid)
            assert img.size == (SIZE, SIZE), (cid, stage, img.size)
            save(img, path, MARKER)
            print(f"wrote {path}")


if __name__ == "__main__":
    main()
