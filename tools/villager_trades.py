#!/usr/bin/env python3
"""Profession overlays for the trading villagers — the apothecary, the Ministry clerk and the broommaker.

Both professions (`ModVillager.APOTHECARY`, `ModVillager.MINISTRY_CLERK`) keep a location board
(`locations/apothecary.json`, `locations/ministry.json`). Without a sheet, `VillagerProfessionLayer`
resolves `wizards_and_beasts:textures/entity/villager/profession/<id>.png`, misses, and draws the
villager in the purple-and-black missing texture.

The islands are the vanilla villager model's, exactly as `villager_wandmaker.py` documents them; this
tool imports them from there rather than restating them. Only `jacket`, `arms` and `armbar` are
painted. Neither profession declares `hat: full`, so the biome's own headwear stays — an apothecary
in the desert keeps his head wrap.

    apothecary      a dark coat under a long canvas apron, stained at the hem, a row of corked vials
                    across the bib, and rolled sleeves
    ministry_clerk  Ministry navy robes, a white collar, and the gold "M" of the Ministry on the chest
    broommaker      a russet waistcoat over a cream shirt, a scuffed leather work apron with a pocket
                    of birch twigs, and leather cuffs (`ModVillager.BROOMMAKER`, `locations/broom_shop.json`)

Run from the repo root:  python tools/villager_trades.py [--force]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, save  # noqa: E402
from boxuv import BOTTOM, EAST, NORTH, SOUTH, TOP, WEST, faces  # noqa: E402
from villager_wandmaker import ARMBAR, ARMS, JACKET, _fill, _row  # noqa: E402

MARKER = marker("villager_trades.py")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROFESSION_DIR = os.path.join(ROOT, "src/main/resources/assets/wizards_and_beasts/textures/entity/villager/profession")

SIDES = (EAST, NORTH, WEST, SOUTH)
SIDE_TONE = {EAST: 0.30, NORTH: 0.0, WEST: 0.30, SOUTH: 0.14}


def _tone(colour, face, shadow):
    return mix(colour, shadow, SIDE_TONE[face])


# ------------------------------------------------------------------------------ apothecary
# Slug & Jiggers: a working shop. Bottle-green coat, an undyed canvas apron that has seen spills.
COAT_DARK = hx("#1F3328")
COAT = hx("#2C4A39")
COAT_LIT = hx("#3D6250")
APRON = hx("#B8A788")
APRON_DIM = hx("#988769")
STAIN = hx("#6E7A4A")
GLASS = hx("#7FC8B4")
GLASS_DARK = hx("#3F7F6E")
CORK = hx("#8A5A35")
GREEN_SHADOW = hx("#142219")


def build_apothecary():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rect = faces(*JACKET)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(COAT, face, GREEN_SHADOW))
        _row(px, f, 0, _tone(COAT_LIT, face, GREEN_SHADOW))
        _row(px, f, 19, _tone(GREEN_SHADOW, face, GREEN_SHADOW))
    front = rect[NORTH]
    # The apron: bib from row 2, skirt to the hem, two columns of coat showing at each side.
    for row in range(2, 19):
        _row(px, front, row, APRON if row % 5 else APRON_DIM, x0=1, width=6)
    _row(px, front, 2, APRON_DIM, x0=1, width=6)
    # Ties at the waist, wrapping round the sides.
    for face in (EAST, WEST, SOUTH):
        _row(px, rect[face], 11, _tone(APRON_DIM, face, GREEN_SHADOW))
    # Spills near the hem.
    for x, row in ((2, 16), (3, 17), (5, 15), (4, 18)):
        px[front[0] + x, front[1] + row] = STAIN
    # A row of corked vials across the bib.
    for x in (2, 4, 6):
        px[front[0] + x - 1, front[1] + 4] = CORK
        px[front[0] + x - 1, front[1] + 5] = GLASS
        px[front[0] + x - 1, front[1] + 6] = GLASS_DARK
    _fill(px, rect[TOP], COAT_LIT)
    _fill(px, rect[BOTTOM], GREEN_SHADOW)

    arms = faces(*ARMS)
    for face in SIDES:
        f = arms[face]
        _fill(px, f, _tone(COAT, face, GREEN_SHADOW))
        # Sleeves rolled to the elbow: a cuff of folded cloth.
        _row(px, f, 4, _tone(COAT_LIT, face, GREEN_SHADOW))
        _row(px, f, 5, _tone(COAT_LIT, face, GREEN_SHADOW))
        _row(px, f, 7, _tone(COAT_DARK, face, GREEN_SHADOW))
    _fill(px, arms[TOP], COAT_LIT)
    _fill(px, arms[BOTTOM], GREEN_SHADOW)
    bar = faces(*ARMBAR)
    for face in SIDES:
        _fill(px, bar[face], _tone(COAT, face, GREEN_SHADOW))
        _row(px, bar[face], 3, _tone(COAT_DARK, face, GREEN_SHADOW))
    _fill(px, bar[TOP], COAT_LIT)
    _fill(px, bar[BOTTOM], GREEN_SHADOW)
    return img


# --------------------------------------------------------------------------- ministry clerk
# Ministry navy, a starched white collar, and the gold M every Ministry document carries.
NAVY_DARK = hx("#141C33")
NAVY = hx("#1E2A4A")
NAVY_LIT = hx("#2E3D66")
COLLAR = hx("#E8E4D8")
GOLD = hx("#D4AF37")
GOLD_DIM = hx("#9C7E22")
NAVY_SHADOW = hx("#0B1020")


def build_ministry_clerk():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rect = faces(*JACKET)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(NAVY, face, NAVY_SHADOW))
        _row(px, f, 0, _tone(COLLAR, face, NAVY_SHADOW))
        _row(px, f, 1, _tone(NAVY_LIT, face, NAVY_SHADOW))
        _row(px, f, 11, _tone(NAVY_DARK, face, NAVY_SHADOW))
        _row(px, f, 19, _tone(NAVY_SHADOW, face, NAVY_SHADOW))
    front = rect[NORTH]
    # A white collar opening to a V, and a dark placket down the robe.
    _row(px, front, 1, COLLAR, x0=2, width=4)
    _row(px, front, 2, COLLAR, x0=3, width=2)
    for row in range(3, 19):
        _row(px, front, row, NAVY_DARK, x0=4, width=1)
    # The Ministry M, gold, over the heart (the wearer's left = the face's right half).
    m = ((5, 4), (5, 5), (5, 6), (6, 5), (7, 4), (7, 5), (7, 6))
    for x, row in m:
        if x < front[2]:
            px[front[0] + x, front[1] + row] = GOLD
    px[front[0] + 6, front[1] + 6] = GOLD_DIM
    _row(px, front, 11, GOLD_DIM, x0=3, width=2)
    _fill(px, rect[TOP], COLLAR)
    _fill(px, rect[BOTTOM], NAVY_SHADOW)

    arms = faces(*ARMS)
    for face in SIDES:
        f = arms[face]
        _fill(px, f, _tone(NAVY, face, NAVY_SHADOW))
        _row(px, f, 0, _tone(NAVY_LIT, face, NAVY_SHADOW))
        _row(px, f, 6, _tone(COLLAR, face, NAVY_SHADOW))
        _row(px, f, 7, _tone(NAVY_DARK, face, NAVY_SHADOW))
    _fill(px, arms[TOP], NAVY_LIT)
    _fill(px, arms[BOTTOM], NAVY_SHADOW)
    bar = faces(*ARMBAR)
    for face in SIDES:
        _fill(px, bar[face], _tone(NAVY, face, NAVY_SHADOW))
        _row(px, bar[face], 3, _tone(COLLAR, face, NAVY_SHADOW))
    _fill(px, bar[TOP], NAVY_LIT)
    _fill(px, bar[BOTTOM], NAVY_SHADOW)
    return img


# ------------------------------------------------------------------------------ broommaker
# A workshop trade: twig and varnish. Russet waistcoat, cream shirt sleeves, a scuffed leather apron.
RUSSET_DARK = hx("#4A2416")
RUSSET = hx("#6E3622")
RUSSET_LIT = hx("#8C4A30")
SHIRT = hx("#D9CDB0")
SHIRT_DIM = hx("#B5A88A")
LEATHER = hx("#7A5534")
LEATHER_DIM = hx("#5C3F25")
SCUFF = hx("#9A7450")
TWIG = hx("#C8A86A")
TWIG_DARK = hx("#8E7442")
BROWN_SHADOW = hx("#24120A")


def build_broommaker():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    rect = faces(*JACKET)
    for face in SIDES:
        f = rect[face]
        _fill(px, f, _tone(RUSSET, face, BROWN_SHADOW))
        _row(px, f, 0, _tone(SHIRT, face, BROWN_SHADOW))
        _row(px, f, 1, _tone(RUSSET_LIT, face, BROWN_SHADOW))
        _row(px, f, 19, _tone(BROWN_SHADOW, face, BROWN_SHADOW))
    front = rect[NORTH]
    # Shirt showing at the open neck of the waistcoat.
    _row(px, front, 1, SHIRT, x0=3, width=2)
    _row(px, front, 2, SHIRT_DIM, x0=3, width=2)
    # The leather apron: bib from row 4, skirt to the hem, scuffed where the knee meets the work.
    for row in range(4, 19):
        _row(px, front, row, LEATHER if row % 6 else LEATHER_DIM, x0=1, width=6)
    _row(px, front, 4, LEATHER_DIM, x0=1, width=6)
    for x, row in ((2, 15), (5, 16), (3, 17), (4, 14)):
        px[front[0] + x, front[1] + row] = SCUFF
    # Neck strap up each side of the bib, ties round the waist.
    px[front[0] + 1, front[1] + 3] = LEATHER_DIM
    px[front[0] + 6, front[1] + 3] = LEATHER_DIM
    for face in (EAST, WEST, SOUTH):
        _row(px, rect[face], 11, _tone(LEATHER_DIM, face, BROWN_SHADOW))
    # A pocket across the apron with a bundle of birch twigs standing out of it.
    _row(px, front, 10, LEATHER_DIM, x0=2, width=4)
    for x in (2, 3, 4, 5):
        px[front[0] + x, front[1] + 8] = TWIG if x % 2 else TWIG_DARK
        px[front[0] + x, front[1] + 9] = TWIG_DARK if x % 2 else TWIG
    _fill(px, rect[TOP], SHIRT)
    _fill(px, rect[BOTTOM], BROWN_SHADOW)

    arms = faces(*ARMS)
    for face in SIDES:
        f = arms[face]
        _fill(px, f, _tone(SHIRT, face, BROWN_SHADOW))
        # Leather cuffs over rolled shirt sleeves.
        _row(px, f, 0, _tone(SHIRT_DIM, face, BROWN_SHADOW))
        _row(px, f, 5, _tone(LEATHER, face, BROWN_SHADOW))
        _row(px, f, 6, _tone(LEATHER_DIM, face, BROWN_SHADOW))
    _fill(px, arms[TOP], SHIRT)
    _fill(px, arms[BOTTOM], BROWN_SHADOW)
    bar = faces(*ARMBAR)
    for face in SIDES:
        _fill(px, bar[face], _tone(SHIRT, face, BROWN_SHADOW))
        _row(px, bar[face], 3, _tone(LEATHER_DIM, face, BROWN_SHADOW))
    _fill(px, bar[TOP], SHIRT)
    _fill(px, bar[BOTTOM], BROWN_SHADOW)
    return img


SHEETS = {
    "apothecary": build_apothecary,
    "ministry_clerk": build_ministry_clerk,
    "broommaker": build_broommaker,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite art this tool did not write")
    args = ap.parse_args()
    for name, build in SHEETS.items():
        path = os.path.join(PROFESSION_DIR, name + ".png")
        if not args.force and not is_regenerable(path, MARKER):
            print(f"skipped (hand-drawn) {path}")
            continue
        save(build(), path, MARKER)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
