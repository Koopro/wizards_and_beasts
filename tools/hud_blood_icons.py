#!/usr/bin/env python3
"""The vampire blood meter's HUD icons, drawn the way vanilla draws hunger.

Ten 9x9 drops in the hunger bar's slot, each one a socket with a drop blitted over it,
and a half sprite for the odd value -- the exact three-sprite contract `Gui.renderFood`
uses, so `BloodBarRenderer` is vanilla's loop with different art.

Read `food_empty.png` and `food_full.png` out of the client jar before changing anything
here. Two conventions in them are load-bearing and neither is obvious:

  - **the socket carries the outline, the drop does not.** `food_empty` is the whole
    silhouette in black-plus-dark-grey; `food_full` is drawn one pixel inside it and has
    no black ring of its own. That is why the bar reads as a row of *sockets* that fill,
    rather than as a row of icons that change colour. Both sprites are blitted, always,
    in that order.
  - **the half sprite is the RIGHT half.** The bar is drawn right to left and empties
    leftward, so a partly-spent icon keeps its right side. Filling the left half instead
    puts the seam on the wrong side of every half-full bar in the game.

Two palettes, mirroring vanilla's plain/`_hunger` pair: fresh arterial red, and a dried
withered maroon for STARVING. The bands between those two are not separate art -- see
`NutritionBarTheme`; four sprite sets would make every threshold change an art change.

Run from the repo root:  python tools/hud_blood_icons.py [--force] [--preview]
"""

import argparse
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, save  # noqa: E402

HUD = "src/main/resources/assets/wizards_and_beasts/textures/gui/sprites/hud"
MARKER = marker("hud_blood_icons.py")

SIZE = 9

# A teardrop: a one-pixel point, a short neck, and a bulb low in the cell. Authored as a
# mask rather than drawn with ellipses because at 9x9 every pixel is a decision, and an
# antialiased circle downsampled to this size comes back as mud.
DROP = [
    "....#....",
    "...###...",
    "...###...",
    "..#####..",
    ".#######.",
    ".#######.",
    ".#######.",
    "..#####..",
    "...###...",
]

# Light comes from the upper left, as it does on every vanilla HUD icon. Placed on the
# bulb rather than at the cell's centre, and measured as a radius rather than as a
# diagonal: a diagonal ramp lights the whole upper-left INCLUDING the tip, and a drop
# whose brightest pixel is its point reads as a garlic bulb with a lit stem.
LIGHT_X, LIGHT_Y = 2.5, 4.0
LIGHT_RADIUS = 1.6
MID_RADIUS = 3.6
SPECULAR = (2, 4)

# The column the half sprite cuts at -- the centre of the drop, so the seam sits on the
# axis rather than a pixel off it.
HALF_CUT = 4

FRESH = {
    "outline": hx("#12040A"),
    "socket": hx("#2A1218"),
    "dark": hx("#7A1226"),
    "mid": hx("#C8324B"),
    "light": hx("#E4536A"),
    "spec": hx("#FFD9DE"),
}

# Dried rather than merely darker. The fresh set is a liquid; this one should read as
# something that stopped being one a while ago, the way food_full_hunger reads as rot.
#
# The mid tone is deliberately NOT as dark as the mood wants. A first pass took it down to
# #5C1220, which is the right colour and the wrong sprite: against its own socket the fill
# was almost invisible, so at the one moment the bar matters most a player could not read
# how much was left. Withered has to stay legible against withered.
WITHERED = {
    "outline": hx("#0B0305"),
    "socket": hx("#1C0D11"),
    "dark": hx("#40101B"),
    "mid": hx("#6E1826"),
    "light": hx("#95323F"),
    "spec": hx("#C7A0A6"),
}

VARIANTS = {"": FRESH, "_withered": WITHERED}


def in_mask(x, y):
    return 0 <= x < SIZE and 0 <= y < SIZE and DROP[y][x] == "#"


def interior(x, y):
    """A mask pixel whose four neighbours are all mask too -- the drop minus its rim.

    The rim is what the socket's outline shows through, so the drop sprite must not
    occupy it. Erosion rather than a second hand-authored mask: two masks that have to
    stay one pixel apart are two masks that will not.
    """
    return in_mask(x, y) and all(in_mask(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def boundary(x, y):
    return in_mask(x, y) and not interior(x, y)


def socket(palette):
    """The empty drop: the whole silhouette, outline over fill. Vanilla's `food_empty`."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if boundary(x, y):
                px[x, y] = palette["outline"]
            elif interior(x, y):
                px[x, y] = palette["socket"]
    return img


def drop(palette, half=False):
    """The filled drop, drawn inside the socket's outline. Vanilla's `food_full`.

    `half` keeps the right half only, including the centre column -- see the module
    docstring for why the seam goes on that side.
    """
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if not interior(x, y):
                continue
            if half and x < HALF_CUT:
                continue
            if (x, y) == SPECULAR:
                px[x, y] = palette["spec"]
                continue
            distance = ((x - LIGHT_X) ** 2 + (y - LIGHT_Y) ** 2) ** 0.5
            if distance < LIGHT_RADIUS:
                px[x, y] = palette["light"]
            elif distance < MID_RADIUS:
                px[x, y] = palette["mid"]
            else:
                px[x, y] = palette["dark"]
    return img


def sprites():
    out = {}
    for suffix, palette in VARIANTS.items():
        out["blood_empty" + suffix] = socket(palette)
        out["blood_full" + suffix] = drop(palette)
        out["blood_half" + suffix] = drop(palette, half=True)
    return out


def preview(images, path):
    """A x16 contact sheet of every sprite, plus the socket-and-drop composite.

    The composite is the only honest view: each sprite on its own looks wrong, because
    neither is ever drawn alone.
    """
    scale = 16
    names = sorted(images)
    cols = len(names) + 2
    sheet = Image.new("RGBA", (cols * SIZE * scale, SIZE * scale), (30, 30, 34, 255))
    for i, name in enumerate(names):
        sheet.paste(images[name].resize((SIZE * scale, SIZE * scale), Image.NEAREST), (i * SIZE * scale, 0))
    for j, suffix in enumerate(("", "_withered")):
        stacked = images["blood_empty" + suffix].copy()
        stacked.alpha_composite(images["blood_full" + suffix])
        sheet.paste(stacked.resize((SIZE * scale, SIZE * scale), Image.NEAREST),
                    ((len(names) + j) * SIZE * scale, 0))
    sheet.save(path)
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true",
                        help="overwrite even art this tool did not write")
    parser.add_argument("--preview", metavar="PATH",
                        help="also write an upscaled contact sheet here")
    args = parser.parse_args()

    images = sprites()
    written = 0
    for name, img in sorted(images.items()):
        path = os.path.join(HUD, name + ".png")
        if not args.force and not is_regenerable(path, MARKER):
            print("skip (not ours):", path)
            continue
        save(img, path, MARKER)
        written += 1
        print("wrote", path)
    if args.preview:
        print("preview", preview(images, args.preview))
    print(f"{written} sprite(s)")


if __name__ == "__main__":
    main()
