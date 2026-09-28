#!/usr/bin/env python3
"""House banner cloth for Wizards & Beasts.

The four house banners shipped as a flat 16x16 house-colour square with a shapeless blob
in the middle, drawn on `minecraft:block/cross` — the sapling model. Two crossed diagonal
planes read as a plant, not as hanging cloth, and a 16px square cannot carry a banner's
proportions at all.

The shape this targets is the WesterosBlocks one (`banner_block_0`): a banner is two
blocks tall and its silhouette lives in the texture's alpha rather than in geometry, so
the block stays a single thin quad per half. That means everything that makes it read as
cloth has to be painted:

  - a hanging rod with two brackets across the top, so it is suspended rather than floating
  - the cloth inset from the block edges, so you see fabric and not a wall panel
  - vertical fold shading, because a flat fill reads as paper
  - a swallowtail hem cut out of the bottom half
  - a tone-on-tone damask over the field, which is what keeps the large flat area alive

The emblem is the one part drawn as art rather than as a formula: each house beast is
built from primitives at 4x and then `pixelate`d down, the same supersample-and-snap route
the other generators use, because a beast decided at 1px per pixel is a blob (which is
exactly what the old textures were).

Colours are the canon house pairs — field plus metal — not invented ones.

In game the block no longer draws these as flat quads: `HouseBannerRenderer` hangs them on a
waving 3D mesh, with a real rod sampled from the painted one and edge strips along the cloth's
outline. It repeats CLOTH_X0/X1, CLOTH_Y0, HEM_Y and NOTCH as texel constants, so move the
silhouette here and those must follow.

Run from the repo root:  python tools/banner_textures.py [--force] [--only gryffindor,...]
"""

import argparse
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, pixelate, posterise, save, shade  # noqa: E402

BLOCK_DIR = "src/main/resources/assets/wizards_and_beasts/textures/block"
MARKER = marker("banner_textures.py")

W, H = 32, 64                 # one banner, split into two 32x32 halves
CLOTH_X0, CLOTH_X1 = 4, 27    # inclusive: 24px of cloth, 4px of air each side
CLOTH_Y0 = 3                  # first row under the rod
HEM_Y = 56                    # outer corners of the swallowtail
NOTCH = 9                     # how far the V rises at the centre

ROD = hx("#4A3A28")
ROD_LIT = hx("#6B5539")
ROD_DARK = hx("#241C13")

# field, metal, beast
HOUSES = {
    "gryffindor": (hx("#7F0909"), hx("#D3A625"), "lion"),
    "slytherin": (hx("#1A472A"), hx("#AAAAAA"), "snake"),
    # Navy, lifted a step from the film's #0E1A40: at that value the shaded folds and the tails
    # went to black and swallowed the bronze eagle.
    "ravenclaw": (hx("#1A2A5E"), hx("#946B2D"), "eagle"),
    "hufflepuff": (hx("#ECB939"), hx("#372E29"), "badger"),
}


# --------------------------------------------------------------------------- silhouette

def hem_row(x):
    """Bottom-most cloth row for column `x` — the swallowtail.

    The V is linear from each outer corner to the centre, so the two tails stay sharp
    instead of rounding off the way a curve would at this size.
    """
    span = (CLOTH_X1 - CLOTH_X0) / 2.0
    t = 1.0 - abs(x - (CLOTH_X0 + span)) / span
    return int(round(HEM_Y - NOTCH * t))


def in_cloth(x, y):
    return CLOTH_X0 <= x <= CLOTH_X1 and CLOTH_Y0 <= y <= hem_row(x)


# --------------------------------------------------------------------------- field

def fold(x):
    """Drape brightness for a column.

    Three folds across the cloth. The cosine is deliberately not centred on the middle of
    the banner: a fold running straight down the emblem's spine would fight it.
    """
    t = (x - CLOTH_X0) / float(CLOTH_X1 - CLOTH_X0)
    wave = 0.5 + 0.5 * math.cos(2 * math.pi * (t * 3.0 + 0.15))
    # Three flat bands, not a cosine: a continuous ramp posterised into one- and two-pixel
    # stripes, which read as a painted gradient rather than as cloth (texture audit 2026-09-27).
    return 1.02 if wave > 0.66 else (0.93 if wave > 0.33 else 0.85)


def damask(x, y):
    """True on the tone-on-tone motif: a diamond lattice, outline only.

    Kept to a 6x6 cell and to the outline — the first pass filled the cell centres too and
    at this contrast the field read as polka dots rather than as woven pattern.
    """
    cx, cy = (x - CLOTH_X0) % 6 - 2.5, (y - CLOTH_Y0) % 6 - 2.5
    return 2.0 <= abs(cx) + abs(cy) <= 2.8


def draw_field(img, field):
    px = img.load()
    for y in range(H):
        for x in range(W):
            if not in_cloth(x, y):
                continue
            c = shade(field, fold(x))
            if damask(x, y):
                c = mix(c, shade(field, 1.45), 0.18)
            # The cloth gathers light at the rod and pools shadow in the tails.
            if y < CLOTH_Y0 + 3:
                c = mix(c, (255, 255, 255, 255), 0.10)
            elif y > HEM_Y - 8:
                c = mix(c, (0, 0, 0, 255), 0.14)
            px[x, y] = c


def draw_outline(img, field):
    """1px border wherever cloth meets air. Without it the banner has no edge and the
    alpha cut just looks like a hole."""
    edge = shade(field, 0.42)
    px = img.load()
    border = []
    for y in range(H):
        for x in range(W):
            if not in_cloth(x, y):
                continue
            if any(not in_cloth(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                border.append((x, y))
    for x, y in border:
        px[x, y] = edge


def draw_rod(img):
    px = img.load()
    for x in range(2, W - 2):
        px[x, 0] = ROD_DARK
        px[x, 1] = ROD_LIT
        px[x, 2] = ROD
    # Finials, then the two brackets the cloth hangs from.
    for x, y in ((1, 1), (1, 2), (W - 2, 1), (W - 2, 2)):
        px[x, y] = ROD_DARK
    for bx in (CLOTH_X0 + 2, CLOTH_X1 - 2):
        for y in range(0, 5):
            px[bx, y] = ROD_DARK if y != 1 else ROD_LIT
            px[bx + 1, y] = ROD


# --------------------------------------------------------------------------- emblems
#
# Each beast is drawn at 4x into a square canvas and pixelated down, so curves land on
# pixel boundaries instead of being guessed one pixel at a time. Coordinates below are in
# that 4x space (80x80 for a 20px emblem).

S = 4
EM = 22
CAN = EM * S
C = CAN / 2.0


def _canvas():
    return Image.new("RGBA", (CAN, CAN), (0, 0, 0, 0))


def lion(metal, field):
    """Front-facing maned head.

    The first version drew the mane as a solid disc with tufts all round and read as a sun:
    with no dark centre there was nothing to see as a face. So the mane is an annulus, the
    face fills its whole opening in a much darker tone, and the tufts are few and large —
    fourteen small ones just made a gear.
    """
    img = _canvas()
    d = ImageDraw.Draw(img)
    face, ink = shade(metal, 0.74), shade(metal, 0.20)
    # Wider than tall, and only seven tufts: a symmetric ring of small ones is a sun no
    # matter what sits inside it. The face is only slightly darker than the mane — the
    # first pass made it near-black and the head turned into a hole.
    for i in range(7):
        a = 2 * math.pi * (i + 0.5) / 7
        d.polygon([(C + 32 * math.cos(a - 0.40), C + 27 * math.sin(a - 0.40)),
                   (C + 46 * math.cos(a), C + 40 * math.sin(a)),
                   (C + 32 * math.cos(a + 0.40), C + 27 * math.sin(a + 0.40))], fill=metal)
    d.ellipse([C - 36, C - 31, C + 36, C + 31], fill=metal)
    d.ellipse([C - 23, C - 21, C + 23, C + 25], fill=face)
    for sgn in (-1, 1):                                                    # ears
        d.ellipse([C + sgn * 18 - 8, C - 28, C + sgn * 18 + 8, C - 14], fill=face)
        d.ellipse([C + sgn * 10 - 6, C - 11, C + sgn * 10 + 6, C - 1], fill=ink)  # eyes
    d.polygon([(C - 11, C + 4), (C + 11, C + 4), (C, C + 12)], fill=ink)   # brows into muzzle
    d.ellipse([C - 6, C + 9, C + 6, C + 19], fill=ink)                     # jaw
    return img


def eagle(metal, field):
    """Eagle displayed. Wings have to span nearly the full emblem — the stubby first pass
    read as a cross, because a bird at this size *is* its wingspan."""
    img = _canvas()
    d = ImageDraw.Draw(img)
    ink = shade(metal, 0.55)
    for sgn in (-1, 1):
        d.polygon([(C + sgn * 5, 34), (C + sgn * 22, 12), (C + sgn * 43, 16),
                   (C + sgn * 40, 34), (C + sgn * 22, 44), (C + sgn * 7, 48)], fill=metal)
        for k in range(3):                                                 # feather splits
            d.line([(C + sgn * (20 + 7 * k), 20 + 2 * k), (C + sgn * (14 + 6 * k), 40 - 2 * k)],
                   fill=ink, width=2)
    d.ellipse([C - 9, 30, C + 9, 62], fill=metal)                          # body
    d.ellipse([C - 9, 14, C + 9, 32], fill=metal)                          # head
    d.polygon([(C + 7, 20), (C + 20, 24), (C + 7, 28)], fill=ink)          # beak
    d.ellipse([C - 5, 19, C - 1, 23], fill=ink)                            # eye
    d.polygon([(C - 8, 58), (C + 8, 58), (C + 12, 80), (C, 72), (C - 12, 80)], fill=metal)
    return img


def snake(metal, field):
    """Serpent coiled into an S, tapering head to tail.

    Drawn as a swept disc so the body keeps a rounded outline, with the head oversized
    relative to life: at 22px an anatomically-scaled head disappears and the whole thing
    reads as a squiggle.
    """
    img = _canvas()
    d = ImageDraw.Draw(img)
    ink = shade(metal, 0.5)
    steps = 120
    for i in range(steps):
        t = i / float(steps - 1)
        x = C + 26 * math.sin(2 * math.pi * t)
        y = 22 + 56 * t
        r = 9.0 - 6.5 * t                                                  # taper to a point
        d.ellipse([x - r, y - r, x + r, y + r], fill=metal)
    hx_, hy = C, 20
    d.ellipse([hx_ - 15, hy - 11, hx_ + 15, hy + 11], fill=metal)          # head
    d.polygon([(hx_ - 15, hy - 2), (hx_ - 4, hy - 12), (hx_ - 4, hy + 6)], fill=metal)
    d.ellipse([hx_ - 9, hy - 6, hx_ - 3, hy], fill=ink)                    # eyes
    d.ellipse([hx_ + 3, hy - 6, hx_ + 9, hy], fill=ink)
    d.line([(hx_, hy + 10), (hx_, hy + 19)], fill=ink, width=3)            # forked tongue
    d.line([(hx_, hy + 19), (hx_ - 7, hy + 26)], fill=ink, width=3)
    d.line([(hx_, hy + 19), (hx_ + 7, hy + 26)], fill=ink, width=3)
    return img


def badger(metal, field):
    """Front-facing head.

    The badger is defined by its markings rather than its outline, so the blaze and cheek
    stripes are cut in the *field* colour — they are holes in the beast, not highlights on
    it. Painting them near-white was what turned the first pass into a striped bucket.
    """
    img = _canvas()
    d = ImageDraw.Draw(img)
    stripe, ink = field, shade(metal, 0.6)
    for sgn in (-1, 1):                                                    # ears, clear of the skull
        d.ellipse([C + sgn * 24 - 10, 12, C + sgn * 24 + 10, 32], fill=metal)
    d.ellipse([C - 30, 16, C + 30, 56], fill=metal)                        # skull
    d.polygon([(C - 19, 44), (C + 19, 44), (C + 9, 78), (C - 9, 78)], fill=metal)  # snout
    d.ellipse([C - 10, 68, C + 10, 82], fill=metal)
    d.polygon([(C - 6, 18), (C + 6, 18), (C + 8, 74), (C - 8, 74)], fill=stripe)   # blaze
    for sgn in (-1, 1):
        d.polygon([(C + sgn * 26, 24), (C + sgn * 15, 22),
                   (C + sgn * 12, 52), (C + sgn * 22, 50)], fill=stripe)   # cheek stripes
    d.ellipse([C - 7, 72, C + 7, 82], fill=ink)                            # nose
    return img


BEASTS = {"lion": lion, "eagle": eagle, "snake": snake, "badger": badger}


def draw_emblem(img, beast, metal, field):
    # Posterised on its own: sharing one palette with the field spent every entry on cloth
    # shading and collapsed the beast's interior tones into a single blob.
    art = posterise(pixelate(BEASTS[beast](metal, field), EM), 6)
    ox, oy = (W - EM) // 2, 16
    px, ap = img.load(), art.load()
    for y in range(EM):
        for x in range(EM):
            r, g, b, a = ap[x, y]
            if a and in_cloth(ox + x, oy + y):
                px[ox + x, oy + y] = (r, g, b, 255)


# --------------------------------------------------------------------------- assembly

def build(house):
    field, metal, beast = HOUSES[house]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_field(img, field)
    # Field first and on its own budget: the fold ramp plus the damask is where the shade
    # count actually goes, and posterising the finished banner instead let the cloth eat
    # the palette and flatten the beast.
    img = posterise(img, 8)
    draw_emblem(img, beast, metal, field)
    draw_outline(img, field)
    draw_rod(img)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()

    wanted = [h.strip() for h in args.only.split(",") if h.strip()] or list(HOUSES)
    written = skipped = 0

    for house in wanted:
        if house not in HOUSES:
            raise SystemExit("unknown house: " + house)
        banner = build(house)
        top, bottom = banner.crop((0, 0, W, 32)), banner.crop((0, 32, W, H))

        icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        icon.paste(pixelate(top, 8), (4, 0))
        icon.paste(pixelate(bottom, 8), (4, 8))

        for suffix, art in (("_top", top), ("_bottom", bottom), ("_item", icon)):
            path = os.path.join(BLOCK_DIR, house + "_banner" + suffix + ".png")
            if not (args.force or is_regenerable(path, MARKER)):
                print("skip (not ours):", path)
                skipped += 1
                continue
            save(art, path, MARKER)
            written += 1
            print("wrote", path, art.size)

    print("%d written, %d skipped" % (written, skipped))


if __name__ == "__main__":
    main()
