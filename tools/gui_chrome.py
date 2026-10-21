#!/usr/bin/env python3
"""Per-screen GUI art for Wizards & Beasts, in parchment and ink.

`gui_parchment.py` owns the shared kit (`theme/`, the skin sets, the controls atlas). This file
owns the art that belongs to one screen: the wandmaker's bench, Hermione's bag, the two creative
tabs and the inventory tab icon. Art nothing reads any more is deleted rather than kept current:
on 2026-09-23, and on 2026-10-15 the Bestiary's sheet, wells, rows and placeholder, which the
field-notebook skin replaced.
It used to draw all of that in the wand HUD's tooled leather; since 2026-09-23 it draws the same
aged sheet the kit does, by importing the kit's paper rather than inventing a second one.

Three kinds of asset, picked per file:

  - screen sheets (the bench) are the kit's sheet composed at the size the Java side blits it
  - vanilla-layout containers (the bag, the creative tabs) are recoloured from vanilla's own
    sheet, because the screen places every slot at a hardcoded offset into it
  - small furniture (rows, icons, sockets) is drawn 1:1 from the kit's palette

Run from the repo root:  python tools/gui_chrome.py [--force] [--only name,name]
"""

import argparse
import random
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar_texture, is_regenerable, marker, save  # noqa: E402
from gui_parchment import (BORDER, INNER, PANEL, grain_field, mix, panel,  # noqa: E402
                           paper_colour, shade, skin, slice_tiled)

GUI = "src/main/resources/assets/wizards_and_beasts/textures/gui"
MARKER = marker("gui_chrome.py")

# The default page, and the two materials a screen here wears instead of it.
PAGE = skin()
WORKBENCH = skin("workbench")
# The building-block creative tab is told apart from the spellcasting one by its stock: the blue-
# grey vellum of the star chart reads as the cool masonry it dispenses, and is still paper.
VELLUM = skin("star_chart")


def opaque(c):
    return c + (255,)


def canvas(w, h):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


# --------------------------------------------------------------------------- slots

def slot_colours(s):
    """Vanilla's slot is four greys: shadow, face, lit lip, and the page around it. These are the
    same four roles on paper -- a recess pressed into the sheet, lit from the top-left."""
    return dict(
        shadow=mix(s["paper_dd"], s["ink_2"], 0.55),
        face=mix(s["paper_d"], s["paper_dd"], 0.55),
        lip=s["paper_ll"],
    )


# --------------------------------------------------------------------------- character sheet

def a_icon_tab():
    """The inventory tab's 16x16 icon: a rolled scroll, which is what the character sheet is.

    Drawn a pixel at a time. At 16px a scroll is two rolls and a sheet between them; the three
    ink lines are what make it a document rather than a pillow."""
    img, d = canvas(16, 16)
    s = PAGE
    ink, roll, roll_lit = opaque(s["ink"]), opaque(s["paper_d"]), opaque(s["paper_ll"])
    # The sheet.
    d.rectangle([3, 3, 12, 12], fill=opaque(s["paper_l"]), outline=ink)
    # The two rolls, each a capsule lit on its upper edge.
    for y in (1, 12):
        d.rectangle([2, y, 13, y + 2], fill=roll, outline=ink)
        d.line([(3, y + 1), (12, y + 1)], fill=roll_lit if y == 1 else roll)
        for cx in (2, 13):
            img.putpixel((cx, y), (0, 0, 0, 0))
            img.putpixel((cx, y + 2), (0, 0, 0, 0))
        img.putpixel((1, y + 1), ink)
        img.putpixel((14, y + 1), ink)
    # Writing.
    for y, x1 in ((5, 10), (7, 9), (9, 10)):
        d.line([(5, y), (x1, y)], fill=opaque(s["ink_2"]))
    # A wax dot, so it is this mod's scroll.
    img.putpixel((10, 10), opaque(s["wax"]))
    img.putpixel((11, 10), opaque(s["wax_d"]))
    return img


# --------------------------------------------------------------------------- vanilla containers

def vanilla_map(s):
    """Vanilla's six greys, to paper. The keys are exact: vanilla container sheets use no others."""
    c = slot_colours(s)
    return {
        0: s["ink"],                                   # outer outline
        55: c["shadow"],                               # slot shadow
        85: mix(s["paper_dd"], s["ink_2"], 0.3),       # panel's own shadowed bevel
        139: c["face"],                                # slot face
        255: c["lip"],                                 # highlights
    }


def restyle(src, s, w, h, seed, dark_box=None):
    """Recolour a vanilla panel onto paper without moving a pixel.

    The screen places every slot at a hardcoded offset into this sheet, so the geometry is not ours
    to reinvent -- only the colours are. The flat face (198) takes the kit's grain, so the panel is
    the same sheet as every other screen rather than a flat beige.

    `dark_box` is a rectangle whose slot-grey stays dark: the creative search field. Vanilla draws
    its text white on that grey, and white on paper is unreadable.
    """
    out = src.copy()
    px = out.load()
    table = vanilla_map(s)
    field = grain_field(w + 48, h + 48, seed, s["grain"])
    for y in range(min(h, out.height)):
        for x in range(min(w, out.width)):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            in_box = (dark_box is not None and dark_box[0] <= x <= dark_box[2]
                      and dark_box[1] <= y <= dark_box[3])
            if in_box and r in (85, 139):
                c = shade(s["ink_2"], 0.9) if r == 139 else s["ink"]
            elif r == 198:
                c = paper_colour(s, field[y % 48][x % 48])
            else:
                c = table.get(r)
                if c is None:
                    lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    c = mix(s["ink"], s["paper_ll"], lum)
            px[x, y] = opaque(c)
    return out


# The region CreativeModeInventoryScreen actually blits out of its 256x256 sheet, and the one band
# of flat panel fill inside it that sits below the grid.
PANEL_W = 195
PANEL_H = 136
FOOTER_Y = 129


def search_box(src):
    """The creative search field's rectangle, found rather than typed: the run of 85-grey on row 4
    that is its top border, down to the white lip on row 15."""
    # First run only: the panel's own right-hand bevel is the same grey further along the row.
    x0 = next(x for x in range(70, PANEL_W) if src.getpixel((x, 4))[:3] == (85, 85, 85))
    x1 = x0
    while src.getpixel((x1 + 1, 4))[:3] == (85, 85, 85):
        x1 += 1
    return (x0, 4, x1 + 1, 15)


def a_creative_background(s, seed):
    """A creative-inventory panel on paper.

    Recoloured from vanilla rather than redrawn: the screen blits this sheet once and then places
    all 45 slots, the scroll track and the search field at hardcoded offsets into it. Always from
    the *search* layout, because both mod tabs call withSearchBar() and the search field is part of
    this background, not a separate widget.
    """
    src = client_jar_texture("textures/gui/container/creative_inventory/tab_item_search.png")
    out = restyle(src, s, PANEL_W, PANEL_H, seed, dark_box=search_box(src))
    brand_bands(out, s)
    return out


def brand_bands(out, s):
    """A signature in the two strips vanilla leaves empty.

    Inside the 195x136 the screen blits, only y=0..3 and y=129..131 are flat panel fill: y=4..15 is
    the search field, y=17..128 the slot grid and hotbar, y=132..135 vanilla's bevel and outer edge.
    So: a thin ink rule along the top, and a gilt dotted run along the foot, denser at the ends.
    """
    d = ImageDraw.Draw(out)
    d.line([(4, 2), (PANEL_W - 5, 2)], fill=opaque(s["ink_2"]))
    for x in range(4, PANEL_W - 4, 2):
        edge = min(x - 4, (PANEL_W - 8) - x) / float(PANEL_W / 2)
        if edge < 0.25 or x % 4 == 0:
            d.point((x, FOOTER_Y + 1), fill=opaque(s["gilt_d"]))


# --------------------------------------------------------------------------- Hermione's bag
#
# The beaded evening bag: pink beads set brick-fashion on rose silk with the odd pearl, gilt piping, a
# kiss-lock clasp across the top, and a deep rose satin lining the slots sink into -- reaching *into* the
# bag, not opening a chest. Java: `HermionesBagScreen` (fabric and lining tile on 64/8 like the kit's
# panels, so the beads never stretch; the clasp bar tiles along x).

def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


ROSE = dict(
    cloth_dd=rgb("#4E1840"), cloth_d=rgb("#7E2A60"), cloth=rgb("#A63E7E"), cloth_l=rgb("#C2588F"),
    bead_d=rgb("#B0407E"), bead=rgb("#E886BE"), bead_l=rgb("#FFD4EC"),
    pearl_d=rgb("#B7A0B2"), pearl=rgb("#F2E6EE"), pearl_l=rgb("#FFFFFF"),
    lining_dd=rgb("#1E0618"), lining_d=rgb("#2E0C26"), lining=rgb("#3C1232"), lining_l=rgb("#4E1C42"),
)
BEAD_STEP = 4      # one bead every 4px; 48 / 4 = 12 beads per tile, so the tile has no seam
BAG_TILE = PANEL   # 64, cut on BORDER (8), as the kit's panels
CLASP_BAR_W, CLASP_BAR_H = 16, 6
CLASP_BALL = 7


def bead_at(px, x, y, kind):
    """A 3px diamond bead, lit from the top-left."""
    r = ROSE
    lit, body, dark = ((r["pearl_l"], r["pearl"], r["pearl_d"]) if kind == "pearl"
                       else (r["bead_l"], r["bead"], r["bead_d"]))
    for (dx, dy), c in (((1, 0), lit), ((0, 1), lit), ((1, 1), body), ((2, 1), dark), ((1, 2), dark)):
        px[x + dx, y + dy] = opaque(c)


def a_bag_fabric():
    """Rose silk set with beads, 64x64 on an 8px border: outline, two rows of gilt piping, then beads to
    the edge. Every pattern is periodic in INNER (48) from BORDER, so tiled centre and edges meet."""
    r = ROSE
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    weave = grain_field(P + INNER, P + INNER, 61, 0.5)
    for y in range(P):
        for x in range(P):
            v = weave[(y - BORDER) % INNER][(x - BORDER) % INNER]
            px[x, y] = opaque(r["cloth_d"] if v < -0.7 else r["cloth_l"] if v > 0.8 else r["cloth"])
    for y in range(P):
        gy = ((y - BORDER) % INNER) // BEAD_STEP
        if ((y - BORDER) % INNER) % BEAD_STEP != 0:
            continue
        offset = 2 if gy % 2 else 0
        for x in range(P):
            phase = (x - BORDER - offset) % INNER
            if phase % BEAD_STEP != 0 or x + 2 >= P or y + 2 >= P:
                continue
            gx = phase // BEAD_STEP
            kind = "pearl" if (gx * 5 + gy * 7) % 13 == 0 else "bead"
            bead_at(px, x, y, kind)
    d = ImageDraw.Draw(img)
    s = PAGE
    d.rectangle([0, 0, P - 1, P - 1], outline=opaque(r["cloth_dd"]))
    d.line([(1, 1), (P - 2, 1)], fill=opaque(s["gilt_l"]))
    d.line([(1, 1), (1, P - 2)], fill=opaque(s["gilt_l"]))
    d.line([(1, P - 2), (P - 2, P - 2)], fill=opaque(s["gilt_d"]))
    d.line([(P - 2, 1), (P - 2, P - 2)], fill=opaque(s["gilt_d"]))
    d.rectangle([2, 2, P - 3, P - 3], outline=opaque(s["gilt"]))
    d.rectangle([3, 3, P - 4, P - 4], outline=opaque(r["cloth_dd"]))
    for c in ((0, 0), (P - 1, 0), (0, P - 1), (P - 1, P - 1)):
        px[c] = (0, 0, 0, 0)
    return img


def a_bag_lining():
    """Deep rose satin, 64x64 on 8: a quiet two-tone grain (a diagonal sheen showed a seam where the tiled
    centre met the edge strips), shadow along the top-left edge, a lit lip bottom-right. Only the outer 2px
    carry the edge, so items sit on plain satin."""
    r = ROSE
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    grain = grain_field(P + INNER, P + INNER, 67, 0.6)
    for y in range(P):
        for x in range(P):
            v = grain[(y - BORDER) % INNER][(x - BORDER) % INNER]
            px[x, y] = opaque(r["lining_d"] if v < -0.6 else r["lining_l"] if v > 0.9 else r["lining"])
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (P - 1, 0)], fill=opaque(r["lining_dd"]))
    d.line([(0, 0), (0, P - 1)], fill=opaque(r["lining_dd"]))
    d.line([(1, 1), (P - 2, 1)], fill=opaque(r["lining_dd"]))
    d.line([(1, 1), (1, P - 2)], fill=opaque(r["lining_dd"]))
    d.line([(1, P - 1), (P - 1, P - 1)], fill=opaque(r["cloth_d"]))
    d.line([(P - 1, 1), (P - 1, P - 1)], fill=opaque(r["cloth_d"]))
    return img


def a_bag_clasp_bar():
    """16x6, tiled along x: the kiss-lock frame, a gilt tube lit on top."""
    s = PAGE
    img, d = canvas(CLASP_BAR_W, CLASP_BAR_H)
    rows = (shade(s["gilt_d"], 0.55), s["gilt_l"], s["gilt_ll"], s["gilt"], s["gilt_d"], shade(s["gilt_d"], 0.55))
    for y, c in enumerate(rows):
        d.line([(0, y), (CLASP_BAR_W - 1, y)], fill=opaque(c))
    return img


def a_bag_clasp_ball():
    """7x7: one of the two clasp balls that meet at the top of the bag."""
    s = PAGE
    img, d = canvas(CLASP_BALL, CLASP_BALL)
    d.ellipse([0, 0, 6, 6], fill=opaque(s["gilt"]), outline=opaque(shade(s["gilt_d"], 0.55)))
    d.point((2, 2), fill=opaque(s["gilt_ll"]))
    d.point((3, 2), fill=opaque(s["gilt_l"]))
    d.point((2, 3), fill=opaque(s["gilt_l"]))
    d.point((4, 5), fill=opaque(s["gilt_d"]))
    d.point((5, 4), fill=opaque(s["gilt_d"]))
    return img


# --------------------------------------------------------------------------- niffler's pouch
#
# The pouch on a niffler's belly: black velvet fur with a blue-grey sheen around the opening, a few
# stolen coins caught in it, and soft brown suede inside where the slots sink. Java: `NifflerPouchScreen`
# (fur and lining tile on 64/8 like the kit's panels).

NIFFLER = dict(
    fur_dd=rgb("#0B0A0D"), fur_d=rgb("#151318"), fur=rgb("#1E1C23"), fur_l=rgb("#2B2933"),
    sheen=rgb("#4A5566"), sheen_l=rgb("#6E7C90"),
    coin_d=rgb("#8F6522"), coin=rgb("#D4AF37"), coin_l=rgb("#F6DE8A"),
    suede_dd=rgb("#24160C"), suede_d=rgb("#3E2716"), suede=rgb("#55361F"), suede_l=rgb("#6A4630"),
    lip=rgb("#7A5238"),
)


def a_pouch_fur():
    """Black velvet fur, 64x64 on 8. Strands are short vertical runs, each lit at its tip and dark at its
    root, scattered on a period of INNER so tiled centre and edges meet; the outer edge is tufted rather
    than ruled (fur has no border), and five stolen coins are caught in each 48px tile."""
    n = NIFFLER
    P = BAG_TILE
    img = Image.new("RGBA", (P, P), opaque(n["fur"]))
    px = img.load()
    rng = random.Random(83)
    # Base: two-tone mottling so the coat is never a flat fill.
    mottle = grain_field(P + INNER, P + INNER, 73, 0.9)
    for y in range(P):
        for x in range(P):
            v = mottle[(y - BORDER) % INNER][(x - BORDER) % INNER]
            px[x, y] = opaque(n["fur_d"] if v < -0.3 else n["fur"])
    # Strands: 70 per tile, 3-6px long, tip lit, root dark.
    strands = [(rng.randrange(INNER), rng.randrange(INNER), rng.randint(3, 6), rng.random() < 0.25)
               for _ in range(70)]
    for sx, sy, length, sheen in strands:
        for i in range(length):
            tone = (n["sheen_l"] if sheen else n["fur_l"]) if i == 0 else \
                (n["sheen"] if sheen else n["fur_l"]) if i < length // 2 else n["fur_dd"]
            for yy in range((sy + i - BORDER) % INNER + BORDER - INNER, P, INNER):
                for xx in range(sx + BORDER - INNER, P, INNER):
                    if 0 <= xx < P and 0 <= yy < P:
                        px[xx, yy] = opaque(tone)
    # Coins caught in the fur.
    for gx, gy in ((7, 9), (29, 33), (41, 15), (17, 40), (36, 4)):
        for xx in range(gx + BORDER - INNER, P, INNER):
            for yy in range(gy + BORDER - INNER, P, INNER):
                if 0 <= xx and xx + 3 < P and 0 <= yy and yy + 3 < P:
                    for (dx, dy), c in (((1, 0), "coin_l"), ((2, 0), "coin"), ((0, 1), "coin_l"), ((1, 1), "coin_l"),
                                        ((2, 1), "coin"), ((3, 1), "coin_d"), ((0, 2), "coin"), ((1, 2), "coin"),
                                        ((2, 2), "coin_d"), ((3, 2), "coin_d"), ((1, 3), "coin_d"), ((2, 3), "coin_d")):
                        px[xx + dx, yy + dy] = opaque(n[c])
    # Tufted edge: a ragged silhouette two pixels deep, periodic along each edge.
    tuft = [rng.randint(0, 2) for _ in range(INNER)]
    for i in range(P):
        depth = tuft[(i - BORDER) % INNER]
        for d in range(depth):
            for (x, y) in ((i, d), (i, P - 1 - d), (d, i), (P - 1 - d, i)):
                px[x, y] = (0, 0, 0, 0)
        for (x, y) in ((i, depth), (i, P - 1 - depth), (depth, i), (P - 1 - depth, i)):
            if px[x, y][3]:
                px[x, y] = opaque(n["fur_dd"])
    return img


def a_pouch_lining():
    """Soft brown suede, 64x64 on 8: a nap grain in three tones, shadow top-left, a lit lip bottom-right."""
    n = NIFFLER
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    nap = grain_field(P + INNER, P + INNER, 79, 0.8)
    for y in range(P):
        for x in range(P):
            v = nap[(y - BORDER) % INNER][(x - BORDER) % INNER]
            px[x, y] = opaque(n["suede_d"] if v < -0.6 else n["suede_l"] if v > 0.9 else n["suede"])
    d = ImageDraw.Draw(img)
    for i in (0, 1):
        d.line([(i, i), (P - 1 - i, i)], fill=opaque(n["suede_dd"]))
        d.line([(i, i), (i, P - 1 - i)], fill=opaque(n["suede_dd"]))
    d.line([(1, P - 1), (P - 1, P - 1)], fill=opaque(n["lip"]))
    d.line([(P - 1, 1), (P - 1, P - 1)], fill=opaque(n["lip"]))
    return img


# --------------------------------------------------------------------------- table

ASSETS = {
    "container/creative_inventory/tab_main.png": lambda: a_creative_background(PAGE, 3),
    "container/creative_inventory/tab_decorative.png": lambda: a_creative_background(VELLUM, 7),
    "container/hermiones_bag/fabric.png": a_bag_fabric,
    "container/hermiones_bag/lining.png": a_bag_lining,
    "container/hermiones_bag/clasp_bar.png": a_bag_clasp_bar,
    "container/hermiones_bag/clasp_ball.png": a_bag_clasp_ball,
    "container/niffler_pouch/fur.png": a_pouch_fur,
    "container/niffler_pouch/lining.png": a_pouch_lining,
    "character_sheet/icon_tab.png": a_icon_tab,
}

# The shared chrome (`theme/`), the skin sets (`sprites/<skin>/`), the controls atlas and the
# character sheet's tab chips belong to `gui_parchment.py`. Do not add them here.


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped = [], []
    for rel, fn in ASSETS.items():
        if only and rel not in only and os.path.basename(rel) not in only:
            continue
        path = os.path.join(GUI, rel)
        if not args.force and not is_regenerable(path, MARKER):
            skipped.append(rel)
            continue
        # No posterise pass any more: the paper is already posterised by construction (five tones
        # per material, see `gui_parchment.paper_colour`), and median-cut would merge its steps.
        save(fn(), path, MARKER)
        written.append(rel)

    print(f"wrote {len(written)} gui textures")
    if skipped:
        print(f"skipped {len(skipped)} hand-authored: {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
