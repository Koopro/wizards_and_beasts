#!/usr/bin/env python3
"""Skins for the wandmaker villager — Ollivander's profession overlay and his pointed hat.

`ModVillager.WANDMAKER` has been a registered `VillagerProfession` with no art, so
`VillagerProfessionLayer` resolved `wizards_and_beasts:textures/entity/villager/profession/
wandmaker.png`, missed, and drew the villager in the purple-and-black missing texture. Two
sheets close that:

    profession/wandmaker.png   the overlay layer paints onto the villager model itself
    wandmaker_hat.png          the extra pointed-hat geometry (`WandmakerHatModel`)

The overlay is not a free-form sheet: it is sampled by the *vanilla* villager model, so every
rectangle here is a box-UV island at a `texOffs` that `VillagerModel.createBodyModel` chose.
Those islands, and what the layer stack does with each, are:

    hat      (32, 0)  8x10x8   the profession's headwear cube — left empty, the 3D hat covers it
    hat_rim  (30,47) 16x16x1   vanilla's flat brim plate — left empty for the same reason
    jacket   ( 0,38)  8x20x6   the robe: shoulders down to the knees
    arms     (44,22)  4x8x4    one island shared by both arms (the second box is mirrored)
    armbar   (40,38)  8x4x4    the block where the crossed forearms meet

Only `jacket`, `arms` and `armbar` are painted. The head is deliberately left bare: the base
`minecraft:textures/entity/villager/villager.png` already draws the face under every layer, and
the `.mcmeta` written next to the sheet declares `hat: full`, which is what makes the *biome*
layer drop its own head geometry — without it a desert or snow wandmaker wears his region's
head wrap under the pointed hat. The arm islands, by contrast, are painted edge to edge: a
villager has no bare hands, and any row left transparent shows the biome's brown sleeve
through the middle of the robe.

The hat sheet is ours end to end, so its islands are packed here and the same offsets are
restated in `WandmakerHatModel`. Both are listed in HAT_ISLANDS below; changing one without the
other is the drift this layout exists to make obvious.

Run from the repo root:  python tools/villager_wandmaker.py [--force]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, save  # noqa: E402
from boxuv import BOTTOM, EAST, NORTH, SOUTH, TOP, WEST, faces  # noqa: E402

MARKER = marker("villager_wandmaker.py")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTITY_DIR = os.path.join(ROOT, "src/main/resources/assets/wizards_and_beasts/textures/entity/villager")
PROFESSION_PNG = os.path.join(ENTITY_DIR, "profession/wandmaker.png")
PROFESSION_MCMETA = PROFESSION_PNG + ".mcmeta"
HAT_PNG = os.path.join(ENTITY_DIR, "wandmaker_hat.png")

# `hat: full` tells VillagerProfessionLayer this profession supplies the whole head, so the
# biome layer renders its no-hat model and no regional head wrap survives under the hat.
MCMETA = '{\n  "villager": {\n    "hat": "full"\n  }\n}\n'

# ---------------------------------------------------------------------------- palette
# Dusty aubergine — old shop, old man, not a costume purple. Silver for every trim, because
# the wand cores he sells are described by their shine and nothing else in the set is metal.
ROBE_DARK = hx("#372A46")
ROBE = hx("#4A3760")
ROBE_LIT = hx("#5F4A78")
TRIM_DIM = hx("#8C86A0")
TRIM = hx("#B7AFC6")
STAR = hx("#E7E1F0")
SHADOW = hx("#2A1F36")

SIDES = (EAST, NORTH, WEST, SOUTH)
# Left and right of a cube face away from the light, so they sit a shade under front and back.
SIDE_TONE = {EAST: 0.30, NORTH: 0.0, WEST: 0.30, SOUTH: 0.14}

# ------------------------------------------------------------------ villager model islands
JACKET = ((0, 38), (8, 20, 6))
ARMS = ((44, 22), (4, 8, 4))
ARMBAR = ((40, 38), (8, 4, 4))

# ------------------------------------------------------------------------- hat sheet islands
# uv -> (name, (w, h, d)). Restated as `texOffs` in WandmakerHatModel, in this order.
HAT_ISLANDS = {
    "brim": ((0, 0), (14, 1, 14)),
    "band": ((0, 16), (8, 3, 8)),
    "cone1": ((32, 16), (6, 3, 6)),
    "cone2": ((0, 28), (4, 3, 4)),
    "cone3": ((16, 28), (2, 3, 2)),
    "tip": ((24, 28), (2, 2, 2)),
}


def _fill(px, rect, colour):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = colour


def _row(px, rect, row, colour, x0=0, width=None):
    """Paint one texture row of a face, counted from the face's own top edge."""
    fx, fy, fw, _ = rect
    width = fw if width is None else width
    for x in range(fx + x0, fx + x0 + width):
        px[x, fy + row] = colour


def _tone(colour, face):
    return mix(colour, SHADOW, SIDE_TONE[face])


# ------------------------------------------------------------------------ profession sheet

def _paint_jacket(px):
    """The robe. 20 rows tall: collar, body, belt at the waist, a shadowed hem."""
    rect = faces(*JACKET)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(ROBE, face))
        _row(px, f, 0, _tone(TRIM_DIM, face))
        _row(px, f, 1, _tone(ROBE_LIT, face))
        # Waist: the belt sits where the jacket cube crosses the hips, not at its midpoint —
        # the cube runs shoulders (row 0) to knees (row 19), so the waist is a third down.
        _row(px, f, 11, _tone(SHADOW, face))
        _row(px, f, 12, _tone(ROBE_DARK, face))
        _row(px, f, 18, _tone(ROBE_DARK, face))
        _row(px, f, 19, _tone(SHADOW, face))

    # Front placket with three fastenings, and a buckle on the belt.
    front = rect[NORTH]
    for row in range(2, 11):
        _row(px, front, row, ROBE_LIT, x0=3, width=2)
    for row in (3, 6, 9):
        _row(px, front, row, TRIM, x0=3, width=1)
    _row(px, front, 11, TRIM, x0=3, width=2)

    _fill(px, rect[TOP], TRIM_DIM)
    _fill(px, rect[BOTTOM], SHADOW)


def _paint_arms(px):
    """Sleeves — every row of both arm islands, cuff included.

    A villager has no bare hands to preserve here: the base sheet paints the whole arm in skin and
    the *biome* layer covers all of it in sleeve, so a partly-painted island leaves the region's
    brown showing past ours rather than a hand.
    """
    rect = faces(*ARMS)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(ROBE, face))
        _row(px, f, 0, _tone(ROBE_LIT, face))
        _row(px, f, 6, _tone(TRIM_DIM, face))
        _row(px, f, 7, _tone(ROBE_DARK, face))
    _fill(px, rect[TOP], ROBE_LIT)
    _fill(px, rect[BOTTOM], SHADOW)

    bar = faces(*ARMBAR)
    for face in SIDES:
        f = bar[face]
        _fill(px, f, _tone(ROBE, face))
        _row(px, f, 3, _tone(TRIM_DIM, face))
    _fill(px, bar[TOP], ROBE_LIT)
    _fill(px, bar[BOTTOM], SHADOW)


def build_profession():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    _paint_jacket(px)
    _paint_arms(px)
    return img


# -------------------------------------------------------------------------------- hat sheet

def _paint_brim(px):
    uv, size = HAT_ISLANDS["brim"]
    rect = faces(uv, size)
    top = rect[TOP]
    _fill(px, top, ROBE)
    # A darker ring one pixel in from the edge reads as the brim curling down.
    x0, y0, w, h = top
    for x in range(x0, x0 + w):
        px[x, y0] = ROBE_DARK
        px[x, y0 + h - 1] = ROBE_DARK
    for y in range(y0, y0 + h):
        px[x0, y] = ROBE_DARK
        px[x0 + w - 1, y] = ROBE_DARK
    _fill(px, rect[BOTTOM], SHADOW)
    for face in SIDES:
        _fill(px, rect[face], _tone(ROBE_DARK, face))


def _paint_band(px):
    """Lower crown: two rows of cloth over a dark hatband, with a buckle at the front."""
    uv, size = HAT_ISLANDS["band"]
    rect = faces(uv, size)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(ROBE, face))
        _row(px, f, 2, _tone(SHADOW, face))
    _row(px, rect[NORTH], 2, TRIM, x0=3, width=2)
    _fill(px, rect[TOP], ROBE)
    _fill(px, rect[BOTTOM], SHADOW)


def _paint_cone(px):
    """The four tapering segments, lightening toward the tip so the point stays readable."""
    for i, name in enumerate(("cone1", "cone2", "cone3", "tip")):
        uv, size = HAT_ISLANDS[name]
        rect = faces(uv, size)
        cloth = mix(ROBE, ROBE_LIT, i / 3)
        for face in SIDES:
            _fill(px, rect[face], _tone(cloth, face))
        _fill(px, rect[TOP], mix(cloth, ROBE_LIT, 0.5))
        _fill(px, rect[BOTTOM], SHADOW)
    # One silver star low on the front of the first segment — the only ornament on the set.
    front = faces(*HAT_ISLANDS["cone1"])[NORTH]
    px[front[0] + 2, front[1] + 1] = STAR
    px[front[0] + 3, front[1] + 1] = TRIM


def build_hat():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    _paint_brim(px)
    _paint_band(px)
    _paint_cone(px)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite art this tool did not write")
    args = ap.parse_args()

    for path, build in ((PROFESSION_PNG, build_profession), (HAT_PNG, build_hat)):
        if not args.force and not is_regenerable(path, MARKER):
            print(f"skipped (hand-drawn) {path}")
            continue
        save(build(), path, MARKER)
        print(f"wrote {path}")

    os.makedirs(os.path.dirname(PROFESSION_MCMETA), exist_ok=True)
    with open(PROFESSION_MCMETA, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(MCMETA)
    print(f"wrote {PROFESSION_MCMETA}")


if __name__ == "__main__":
    main()
