#!/usr/bin/env python3
"""The seven glyphs the Character Sheet's stat rows are labelled with.

One 112x16 strip of 16x16 cells: the five `PlayerStat` constants in declaration order,
then the two state marks a row can carry.

    0 power       a lightning bolt
    1 precision   a ranging reticle
    2 willpower   a kite shield
    3 reflexes    a double chevron
    4 knowledge   an open book
    5 capped      a padlock, drawn on POWER when heritage stops it growing
    6 prodigy     an eight-point starburst, the sheet's prodigy mark

Drawn at 1:1 rather than supersampled and pixelated. At 16px these are seven or eight
pixels of actual silhouette; a downscale spends them on anti-aliasing and what arrives is
a grey smudge that reads as none of the seven. Every pixel here is placed.

Palette is `WizardsPalette`'s brass family plus the two accents the sheet already uses for
these states, so a row of icons sits in the same metal as the filigree around it. Each
glyph is a dark seat, a mid body and a single highlight -- the same three-tone treatment
`gui_chrome.py` gives its furniture, which is what stops sixteen-pixel art looking flat.

Run from the repo root:  python tools/stat_icons.py [--force]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, save  # noqa: E402

OUT = ("src/main/resources/assets/wizards_and_beasts/textures/gui"
       "/character_sheet/stat_icons.png")
MARKER = marker("stat_icons.py")

CELL = 16
COUNT = 7

# WizardsPalette.BRASS / BRASS_HI / LINE, and the two accents the stat rows already use.
SEAT = hx("#5E3A2E")     # WizardsPalette.PIP_OFF -- the dark every glyph is seated on
BODY = hx("#A4764A")     # WizardsPalette.LINE
LIT = hx("#DBA86D")      # WizardsPalette.BRASS
HI = hx("#F5E4B0")       # WizardsPalette.BRASS_HI
GOLD = hx("#FFD700")     # the sheet's prodigy gold
GOLD_D = hx("#8A6A00")

# Each glyph is written as 16 rows of 16 characters. Legend:
#   '.' transparent   'o' seat   'x' body   'X' lit   '*' highlight
LEGEND = {".": None, "o": SEAT, "x": BODY, "X": LIT, "*": HI}

POWER = [
    "................",
    "..........oo....",
    ".........o*Xo...",
    "........o**Xo...",
    ".......o**Xo....",
    "......o**Xo.....",
    ".....o**XXoooo..",
    "....o**XXXXXXo..",
    "..oooXXXXXX**o..",
    "..oXXXXXXX**o...",
    "....oXXXX**o....",
    ".....oXXX*o.....",
    "......oXX*o.....",
    ".......oX*o.....",
    "........oo......",
    "................",
]

PRECISION = [
    "................",
    ".......oo.......",
    ".......X*.......",
    ".....ooXXoo.....",
    "....oX*..*Xo....",
    "...oX......Xo...",
    "..oX...oo...Xo..",
    ".oXX..o**o..XXo.",
    ".o*X..o**o..X*o.",
    "..oX...oo...Xo..",
    "...oX......Xo...",
    "....oX*..*Xo....",
    ".....ooXXoo.....",
    ".......X*.......",
    ".......oo.......",
    "................",
]

WILLPOWER = [
    "................",
    "...oooooooooo...",
    "..oXXXXXXXXXXo..",
    "..oX*XXXXXX*Xo..",
    "..oXXXXooXXXXo..",
    "..oXXXo**oXXXo..",
    "..oXXXo**oXXXo..",
    "..oXXXo**oXXXo..",
    "...oXXo**oXXo...",
    "...oXXo**oXXo...",
    "....oXo**oXo....",
    ".....oXooXo.....",
    "......oXXo......",
    ".......oo.......",
    "................",
    "................",
]

REFLEXES = [
    "................",
    "XXo..oXXo.......",
    "oXXo..oXXo......",
    ".oXXo..oXXo.....",
    "..oXXo..oXXo....",
    "...oXXo..oXXo...",
    "....oX*o..oX*o..",
    ".....oX*o..oX*o.",
    "....oX*o..oX*o..",
    "...oXXo..oXXo...",
    "..oXXo..oXXo....",
    ".oXXo..oXXo.....",
    "oXXo..oXXo......",
    "XXo..oXXo.......",
    "................",
    "................",
]

KNOWLEDGE = [
    "................",
    "................",
    "..ooo......ooo..",
    ".oX**Xoooox**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oX**XX*XXx**Xo.",
    ".oXXXXX*XXXXXXo.",
    ".oxxxxx*xxxxxxo.",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]

CAPPED = [
    "................",
    "................",
    ".....oooooo.....",
    "....oXXXXXXo....",
    "....oXo..oXo....",
    "....oXo..oXo....",
    "..oooooooooooo..",
    "..oXXXXXXXXXXo..",
    "..oXX*XXXX*XXo..",
    "..oXXXX**XXXXo..",
    "..oXXXX**XXXXo..",
    "..oXXXXXXXXXXo..",
    "..oxxxxxxxxxxo..",
    "..oooooooooooo..",
    "................",
    "................",
]

PRODIGY = [
    "................",
    ".......oo.......",
    "......o**o......",
    "......oXXo......",
    "...o..oXXo..o...",
    "...oX.oXXo.Xo...",
    "....oXoXXoXo....",
    ".oooooX**Xooooo.",
    ".oXXXX****XXXXo.",
    "....oXoXXoXo....",
    "...oX.oXXo.Xo...",
    "...o..oXXo..o...",
    "......oXXo......",
    "......o**o......",
    ".......oo.......",
    "................",
]

GLYPHS = [POWER, PRECISION, WILLPOWER, REFLEXES, KNOWLEDGE, CAPPED, PRODIGY]
# The two state marks wear their own colour: the padlock is the same gold the sheet's
# "heritage limit" text uses, and the prodigy star is the gold already on that chip.
OVERRIDES = {5: {"x": GOLD_D, "X": GOLD, "*": HI}, 6: {"x": GOLD_D, "X": GOLD, "*": GOLD}}


def draw(cell, rows, overrides):
    palette = dict(LEGEND)
    palette.update(overrides)
    for y, row in enumerate(rows):
        if len(row) != CELL:
            raise SystemExit("glyph row %d is %d wide, expected %d" % (y, len(row), CELL))
        for x, ch in enumerate(row):
            colour = palette.get(ch, LEGEND.get(ch))
            if colour is not None:
                cell.putpixel((x, y), colour)


def build():
    if len(GLYPHS) != COUNT:
        raise SystemExit("expected %d glyphs, found %d" % (COUNT, len(GLYPHS)))
    strip = Image.new("RGBA", (CELL * COUNT, CELL), (0, 0, 0, 0))
    for index, rows in enumerate(GLYPHS):
        cell = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
        draw(cell, rows, OVERRIDES.get(index, {}))
        strip.paste(cell, (index * CELL, 0))
    return strip


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true",
                        help="overwrite even art this tool did not write")
    args = parser.parse_args(argv)

    if not args.force and not is_regenerable(OUT, MARKER):
        print("skip (hand-authored): " + OUT)
        return 0
    save(build(), OUT, MARKER)
    print("wrote " + OUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
