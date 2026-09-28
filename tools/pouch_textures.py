#!/usr/bin/env python3
"""The niffler pouch container sheets, cut from the parchment kit in `gui_parchment.py`.

    niffler_pouch.png       256x256, panel 176x166 at the origin: 3x9 pouch + inventory + hotbar
    baby_niffler_pouch.png  256x256, panel 176x133 at the origin: 1x9 pouch + inventory + hotbar

Both follow the vanilla container sheet: `NifflerPouchScreen` blits the top-left
imageWidth x imageHeight, and `NifflerPouchMenu` places its slots on the vanilla 18px grid
starting at (8, 18). Every well here is drawn from those same numbers, one pixel outside the
16x16 item cell, so the items sit exactly in them.

The sheet is the kit's slip (torn paper, one faint rule at 4px) rather than the full page:
vanilla slots start 7px in, where the page's second rule at 6px would run along their edge.
Each well is PAGE_SHADE with the inset's inverted bevel -- shadow top-left, lit bottom-right,
because light comes from the top-left everywhere in this mod.

Run from the repo root:
    python tools/pouch_textures.py [--force]
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402
from gui_parchment import BORDER, mix, panel, skin, slice_tiled  # noqa: E402

MARKER = marker("pouch_textures.py")
GUI = "src/main/resources/assets/wizards_and_beasts/textures/gui"

SHEET = 256
WIDTH = 176
SLOT = 18


def well(img, s, x, y):
    """One 18x18 slot well whose 16x16 cell starts at (x + 1, y + 1)."""
    d = ImageDraw.Draw(img)
    shadow = mix(s["paper_dd"], s["ink_2"], 0.6)
    lit = s["paper_ll"]
    d.rectangle([x, y, x + SLOT - 1, y + SLOT - 1], fill=s["paper_d"])
    d.line([(x, y), (x + SLOT - 2, y)], fill=shadow)
    d.line([(x, y), (x, y + SLOT - 2)], fill=shadow)
    d.line([(x + 1, y + SLOT - 1), (x + SLOT - 1, y + SLOT - 1)], fill=lit)
    d.line([(x + SLOT - 1, y + 1), (x + SLOT - 1, y + SLOT - 1)], fill=lit)
    # The two corners where shadow meets light take the field between them.
    mid = mix(s["paper_d"], s["paper_dd"], 0.5)
    d.point((x + SLOT - 1, y), fill=mid)
    d.point((x, y + SLOT - 1), fill=mid)


def grid(img, s, top, rows):
    for r in range(rows):
        for c in range(9):
            well(img, s, 7 + c * SLOT, top - 1 + r * SLOT)


def pouch(height, pouch_rows, inventory_top, seed):
    s = skin()
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    slice_tiled(img, panel(s, seed=seed, slip=True), 0, 0, WIDTH, height, BORDER)
    grid(img, s, 18, pouch_rows)
    grid(img, s, inventory_top, 3)
    grid(img, s, inventory_top + 58, 1)
    return img


ASSETS = {
    # Numbers mirror NifflerPouchMenu (slot tops) and NifflerPouchScreen (image heights).
    "niffler_pouch.png": lambda: pouch(166, 3, 84, 71),
    "baby_niffler_pouch.png": lambda: pouch(133, 1, 50, 73),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite files another generator owns")
    args = ap.parse_args()
    written, skipped = [], []
    for name, fn in ASSETS.items():
        path = os.path.join(GUI, name)
        if not args.force and not is_regenerable(path, MARKER):
            skipped.append(name)
            continue
        save(fn(), path, MARKER)
        written.append(name)
    print(f"wrote {len(written)}: {', '.join(written)}")
    if skipped:
        print(f"skipped (no marker or another tool's; --force claims): {', '.join(skipped)}")


if __name__ == "__main__":
    main()
