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
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar_texture, is_regenerable, marker, save  # noqa: E402
from gui_parchment import (BORDER, PANEL, grain_field, mix, panel,  # noqa: E402
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


def bare_sheet(s, w, h, seed):
    """The torn sheet with no rule at all, for a vanilla-layout screen whose slots and bars run
    to within a few pixels of the edge.

    The slip's single rule sits at 4px; on the bench the tier bar occupies y 187-192 of a 196px
    sheet, so any rule would be struck through by it. The rule is painted out of the 64px tile
    before composing, from the pixel one step further in -- same grain phase, same edge stain.
    """
    tile = panel(s, seed=seed, slip=True)
    px = tile.load()
    lo, hi = 4, PANEL - 5
    for i in range(lo, hi + 1):
        px[i, lo] = px[i, lo + 1]
        px[i, hi] = px[i, hi - 1]
        px[lo, i] = px[lo + 1, i]
        px[hi, i] = px[hi - 1, i]
    img, _ = canvas(w, h)
    slice_tiled(img, tile, 0, 0, w, h, BORDER)
    return img


# --------------------------------------------------------------------------- slots

def slot_colours(s):
    """Vanilla's slot is four greys: shadow, face, lit lip, and the page around it. These are the
    same four roles on paper -- a recess pressed into the sheet, lit from the top-left."""
    return dict(
        shadow=mix(s["paper_dd"], s["ink_2"], 0.55),
        face=mix(s["paper_d"], s["paper_dd"], 0.55),
        lip=s["paper_ll"],
    )


def slot_well(img, x, y, s):
    """An 18x18 slot recess at the *background* coordinate (one out from the Slot's), in vanilla's
    exact pixel pattern: shadow along the top and left, lit lip along the bottom and right, and
    the two corners where they meet left at the face colour."""
    c = slot_colours(s)
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + 17, y + 17], fill=opaque(c["face"]))
    d.line([(x, y), (x + 16, y)], fill=opaque(c["shadow"]))
    d.line([(x, y), (x, y + 16)], fill=opaque(c["shadow"]))
    d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=opaque(c["lip"]))
    d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=opaque(c["lip"]))


def recess(img, box, s):
    """A shallow rectangular well: darker stock, shadow top-left, lit lip bottom-right."""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=opaque(mix(s["paper"], s["paper_d"], 0.6)))
    shadow = opaque(mix(s["paper_dd"], s["ink_2"], 0.3))
    d.line([(x0, y0), (x1, y0)], fill=shadow)
    d.line([(x0, y0), (x0, y1)], fill=shadow)
    d.line([(x0 + 1, y1), (x1, y1)], fill=opaque(s["paper_ll"]))
    d.line([(x1, y0 + 1), (x1, y1)], fill=opaque(s["paper_ll"]))


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


# --------------------------------------------------------------------------- wandmaker's bench

def a_wandmakers_bench():
    """The bench background, on Ollivander's pattern paper (`workbench`).

    Every coordinate is `WandmakersBenchMenu`'s, not a new one. The bench slots are at (44,35),
    (80,35) and (134,35); the inventory is the vanilla 9x3 at (8, 112) stepping 18, and the hotbar at
    (8, 170). Those are Slot coordinates, so each well is drawn one pixel out from them. Get this
    wrong and every slot renders off its socket, which is the one way this file can break function
    rather than looks.

    The bands the screen draws into are left clear: the flexibility picker at y 66, the status line
    at y 90, and the strip below the hotbar at y 187-193 where the tier bar sits -- which is why the
    sheet carries no frame rule (see `bare_sheet`).
    """
    s = WORKBENCH
    W, H = 176, 196
    img = bare_sheet(s, W, H, 17)
    d = ImageDraw.Draw(img)

    # Title rule, clear of the vanilla title at (8, 6): the kit's thick-and-thin.
    d.line([(8, 16), (W - 9, 16)], fill=opaque(s["ink"]))
    d.line([(8, 18), (W - 9, 18)], fill=opaque(s["ink_2"]))

    for sx in (44, 80, 134):
        slot_well(img, sx - 1, 34, s)
    # The arrow that says which way the work goes: gilt, outlined in ink.
    d.polygon([(108, 43), (116, 43), (116, 40), (122, 45), (116, 50), (116, 47), (108, 47)],
              fill=opaque(s["gilt"]), outline=opaque(s["ink"]))
    d.line([(109, 44), (116, 44)], fill=opaque(s["gilt_l"]))

    # Flexibility picker well: the cycler's 18px row at y 66 with a two-pixel margin. The screen's
    # `FLEX_ROW_Y` is the number that decides it.
    recess(img, (14, 64, W - 15, 85), s)

    for row in range(3):
        for col in range(9):
            slot_well(img, 7 + col * 18, 111 + row * 18, s)
    for col in range(9):
        slot_well(img, 7 + col * 18, 169, s)

    # The tier bar's track, in the 187-193 strip below the hotbar. Java paints only the fill.
    recess(img, (16, 187, W - 17, 192), s)
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


def a_hermiones_bag():
    """Hermione's beaded bag, on paper, from vanilla's `generic_54`.

    Recoloured for the same reason as the creative tabs: the screen assembles itself from two fixed
    regions of this sheet -- the six rows from the top and the player-inventory strip from v=126 --
    with every slot at a hardcoded offset.

    Branding goes in the title band (y=0..16), the one strip vanilla leaves flat, and only from
    x=104 so it clears the title text at x=8. Beads on a drawstring, which is what the bag is.
    """
    s = PAGE
    src = client_jar_texture("textures/gui/container/generic_54.png")
    out = restyle(src, s, 176, 223, 5)
    d = ImageDraw.Draw(out)
    d.line([(104, 11), (168, 11)], fill=opaque(s["ink_2"]))
    beads = (s["wax"], s["gilt"])
    for i, x in enumerate(range(106, 169, 8)):
        bead = beads[i % 2]
        d.ellipse([x - 2, 6, x + 1, 9], fill=opaque(bead), outline=opaque(shade(bead, 0.6)))
        d.point((x - 1, 7), fill=opaque(mix(bead, (255, 255, 255), 0.5)))
    return out


# --------------------------------------------------------------------------- table

# Mirror of Erised: the screen is the mirror itself -- a gilded frame with a crest for the inscription, smoky
# glass for the vision, and the clawed feet it stands on. Layout constants are mirrored by `ErisedScreen`.
ERISED_W, ERISED_H = 192, 232
ERISED_FRAME = (8, 18, 183, 211)      # outer frame box (x0, y0, x1, y1), inclusive
ERISED_GLASS = (20, 38, 171, 199)     # the glass inside it
ERISED_GOLD = [(70, 46, 16), (118, 84, 32), (168, 124, 48), (214, 170, 82), (246, 218, 140)]


def _gold(t):
    """A tone on the gilt ramp, t in [0, 1]."""
    t = max(0.0, min(1.0, t))
    i = min(len(ERISED_GOLD) - 2, int(t * (len(ERISED_GOLD) - 1)))
    f = t * (len(ERISED_GOLD) - 1) - i
    return mix(ERISED_GOLD[i], ERISED_GOLD[i + 1], f)


def a_erised_mirror():
    """The Mirror of Erised as a screen. The frame is a rounded gilt moulding (lit along its crown, shadowed into
    the glass and out at the rim) with a bead run, rosettes at the corners and a medallion on the crest; the glass
    is near-black at the rim, misted and faintly lit toward the centre, with one diagonal sheen."""
    import math
    img = Image.new("RGBA", (ERISED_W, ERISED_H), (0, 0, 0, 0))
    px = img.load()
    grain = grain_field(ERISED_W, ERISED_H, 41)
    fx0, fy0, fx1, fy1 = ERISED_FRAME
    gx0, gy0, gx1, gy1 = ERISED_GLASS

    def gilt(x, y, base):
        px[x, y] = opaque(_gold(base + grain[y][x] * 0.12))

    def moulding(t):
        """Tone across a rail, t in [0, 1] from the rim to the glass: dark rim, lit crown, shadowed groove."""
        return 0.25 + 0.65 * math.sin(math.pi * min(1.0, max(0.0, t))) ** 0.8

    cx = ERISED_W // 2
    # Crest: an arch over the top rail, moulded along its height, with a medallion at the crown.
    for x in range(34, ERISED_W - 34):
        rel = (x - cx) / (ERISED_W / 2 - 34)
        top = int(round(3 + 15 * rel * rel))
        for y in range(top, fy0):
            t = (y - top) / max(1, fy0 - top)
            tone = moulding(t * 0.9 + 0.05)
            if y == top:
                tone = 0.1
            elif x in (34, ERISED_W - 35):
                tone = 0.15
            gilt(x, y, tone)
    for y in range(0, 16):
        for x in range(cx - 8, cx + 8):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - 9)
            if d <= 7.5:
                gilt(x, y, 0.1 if d > 6.6 else (0.95 if d < 2.5 else 0.6 - 0.04 * (y - 9)))
    # Frame rails.
    side = gx0 - fx0
    for y in range(fy0, fy1 + 1):
        for x in range(fx0, fx1 + 1):
            if gx0 <= x <= gx1 and gy0 <= y <= gy1:
                continue
            ts = [(x - fx0) / side if x < gx0 else None,
                  (fx1 - x) / side if x > gx1 else None,
                  (y - fy0) / (gy0 - fy0) if y < gy0 else None,
                  (fy1 - y) / (fy1 - gy1) if y > gy1 else None]
            t = min(v for v in ts if v is not None)
            tone = moulding(t)
            if x in (fx0, fx1) or y in (fy0, fy1):
                tone = 0.08
            elif x == fx0 + 1 or y == fy0 + 1:
                tone = 0.97
            if gx0 - 1 <= x <= gx1 + 1 and gy0 - 1 <= y <= gy1 + 1:
                tone = 0.12  # the groove where gilt meets glass
            gilt(x, y, tone)
    # The inscription, engraved along the crown of the top rail: glyph-like cuts, not letters -- the words are
    # mirror-written in canon anyway, and the screen shows the reading as a tooltip.
    rng = __import__("random").Random(7)
    ry0 = (fy0 + gy0) // 2 - 2
    x = gx0 + 6
    while x < gx1 - 6:
        width = rng.choice((2, 3, 3, 4))
        for gx in range(x, min(x + width, gx1 - 6)):
            for gy in range(ry0, ry0 + 5):
                if rng.random() < 0.55 or gy in (ry0, ry0 + 4) and rng.random() < 0.4:
                    px[gx, gy] = opaque(_gold(0.12))
        x += width + (3 if rng.random() < 0.2 else 1)
    # Corner rosettes.
    for rx, ry in (((fx0 + gx0) // 2, (fy0 + gy0) // 2), ((fx1 + gx1) // 2 + 1, (fy0 + gy0) // 2),
                   ((fx0 + gx0) // 2, (fy1 + gy1) // 2 + 1), ((fx1 + gx1) // 2 + 1, (fy1 + gy1) // 2 + 1)):
        for y in range(ry - 5, ry + 6):
            for x in range(rx - 5, rx + 6):
                d = math.hypot(x - rx, y - ry)
                if d <= 5.2:
                    gilt(x, y, 0.1 if d > 4.3 else (0.98 if d < 1.5 else 0.45 + 0.08 * ((x + y) % 2)))
    # Bead runs, between the rosettes.
    for y in range(gy0 + 8, gy1 - 6, 6):
        for x in ((fx0 + gx0) // 2, (fx1 + gx1) // 2 + 1):
            px[x, y] = opaque(_gold(1.0))
            px[x, y + 1] = opaque(_gold(0.2))
    for x in range(gx0 + 8, gx1 - 6, 6):
        y = (gy1 + fy1) // 2 + 1
        px[x, y] = opaque(_gold(1.0))
        px[x + 1, y] = opaque(_gold(0.2))
    # Glass.
    gw, gh = gx1 - gx0, gy1 - gy0
    for y in range(gy0, gy1 + 1):
        for x in range(gx0, gx1 + 1):
            u, v = (x - gx0) / gw - 0.5, (y - gy0) / gh - 0.42
            centre = max(0.0, 1.0 - (u * u * 2.4 + v * v * 2.2))
            mist = 0.55 + grain[y][x] * 0.6
            sheen = 1.0 if abs((x - gx0) - 0.6 * (y - gy0) - 14) < 2.5 else 0.0
            t = 0.06 + 0.62 * centre * centre * mist + 0.07 * sheen
            px[x, y] = opaque(mix((8, 8, 14), (74, 78, 102), max(0.0, min(1.0, t))))
    # Clawed feet under the bottom rail: a tapering leg and three toes.
    for x0 in (gx0 + 2, gx1 - 26):
        for y in range(fy1 + 1, ERISED_H - 4):
            half = 10 - (y - fy1) // 2
            for x in range(x0 + 12 - half, x0 + 12 + half + 1):
                gilt(x, y, 0.08 if abs(x - (x0 + 12)) == half else moulding((x - (x0 + 12 - half)) / (2 * half)))
        for toe in (x0 + 4, x0 + 12, x0 + 20):
            for y in range(ERISED_H - 4, ERISED_H):
                for x in range(toe - 3, toe + 4):
                    edge = abs(x - toe) == 3 or y == ERISED_H - 1
                    gilt(x, y, 0.08 if edge else (0.95 if y == ERISED_H - 4 else 0.5))
    return img


ASSETS = {
    "erised/mirror.png": a_erised_mirror,
    "container/creative_inventory/tab_main.png": lambda: a_creative_background(PAGE, 3),
    "container/creative_inventory/tab_decorative.png": lambda: a_creative_background(VELLUM, 7),
    "container/hermiones_bag.png": a_hermiones_bag,
    "wandmakers_bench.png": a_wandmakers_bench,
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
