#!/usr/bin/env python3
"""The mod configuration screens, cut from the parchment kit in `gui_parchment.py`.

    config/parchment.png   48x48 tile     the page the whole config screen is written on
    config/card.png        84x64          a category card: a lighter slip pinned to the page
    config/card_dark.png   84x64          the Dark Arts card: soot-stained, ruled in wax red
    config/gate_panel.png  64x64 on 8     the Blood Quill gate: the same soot sheet as a page

The page tile is 48, not 64: the kit's grain is periodic in 48 (its inner span), so a 48
tile is the one size that repeats with no seam. `WizardsConfigScreen.PARCHMENT_TILE`
matches it.

The Dark Arts pieces are the kit's HEARTH stock -- the soot-stained sheet by the grate --
with its emerald furniture swapped for wax red. Still paper and ink, like everything else;
the mood comes from the stock and the red, not from switching material.

Run from the repo root:
    python tools/config_textures.py [--force]
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402
from gui_parchment import BORDER, INNER, mix, panel, paper_sheet, skin, slice_tiled  # noqa: E402

MARKER = marker("config_textures.py")
GUI = "src/main/resources/assets/wizards_and_beasts/textures/gui/config"

CARD_W, CARD_H = 84, 64


def card_stock():
    """The default sheet one step lighter: a raised card reads as PAGE_LIGHT on PAGE."""
    s = skin()
    s.update(paper_d=s["paper"], paper=s["paper_l"], paper_l=s["paper_ll"],
             paper_dd=s["paper_d"])
    return s


def dark_stock():
    """HEARTH soot stock, its green-fire furniture replaced by the default kit's wax."""
    s = skin("hearth")
    d = skin()
    s.update(gilt_d=d["wax_d"], gilt=d["wax"], gilt_l=d["wax_l"],
             gilt_ll=mix(d["wax_l"], d["paper_ll"], 0.5),
             wax=d["wax"], wax_d=d["wax_d"], wax_l=d["wax_l"])
    return s


def page_tile():
    # Phase-aligned so a 48px tile starts where the grain period does.
    return paper_sheet(skin(), INNER, INNER, seed=61)


def card(s, seed, rule):
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    slice_tiled(img, panel(s, seed=seed, slip=True), 0, 0, CARD_W, CARD_H, BORDER)
    d = ImageDraw.Draw(img)
    d.rectangle([4, 4, CARD_W - 5, CARD_H - 5], outline=rule)
    return img


def card_light():
    s = card_stock()
    return card(s, 41, s["ink_2"])


def card_dark():
    s = dark_stock()
    return card(s, 43, s["wax"])


def gate_panel():
    return panel(dark_stock(), seed=47)


ASSETS = {
    "parchment.png": page_tile,
    "card.png": card_light,
    "card_dark.png": card_dark,
    "gate_panel.png": gate_panel,
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
