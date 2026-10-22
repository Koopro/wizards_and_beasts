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


# --------------------------------------------------------------------------- two-way mirror
#
# Sirius's mirror: a small square of silvered glass in an engraved pewter frame. The call screen is the
# glass itself (you breathe a name onto a patch of fog); the view screen keeps dark glass behind the
# face it shows, with the name on an engraved pewter plaque. Java: `MirrorCallScreen`, `MirrorViewScreen`.

PEWTER = dict(
    void=rgb("#15181C"), d=rgb("#3B4048"), m=rgb("#6F7782"), l=rgb("#A2AAB4"), ll=rgb("#D4DAE1"),
    glass_d=rgb("#8C98A6"), glass=rgb("#A9B4C0"), glass_l=rgb("#C8D1DA"), glass_ll=rgb("#E6ECF2"),
    dark_d=rgb("#171B21"), dark=rgb("#212730"), dark_l=rgb("#2C3440"), dark_ll=rgb("#3C4757"),
)
GLASS_TILE = 64   # drawn with drawTiled: the sheen repeats every 64px and the sprite has no edge strips


def pewter_shape(px, cells, p):
    """A raised pewter casting under the global top-left light."""
    for (x, y) in cells:
        ul = (x - 1, y) not in cells or (x, y - 1) not in cells
        dr = (x + 1, y) not in cells or (x, y + 1) not in cells
        px[x, y] = opaque(p["ll"] if ul and not dr else p["d"] if dr and not ul else p["l"])


def a_mirror_frame():
    """Engraved pewter, 64x64 on 8, transparent inside (the glass is drawn under it). Outside in: outline,
    a bevel lit top-left, an engraved band with a dotted chase every 4px, a groove, the shadowed lip
    the glass sits behind; a rosette cartouche in each corner."""
    p = PEWTER
    P = BAG_TILE
    img = Image.new("RGBA", (P, P), (0, 0, 0, 0))
    px = img.load()
    for y in range(P):
        for x in range(P):
            e = min(x, y, P - 1 - x, P - 1 - y)
            if e > 6:
                continue
            lit = (y == e or x == e) and not (P - 1 - y == e or P - 1 - x == e)
            along = (x if (y == e or P - 1 - y == e) else y) - BORDER
            if e in (0, 6):
                c = p["void"]
            elif e == 1:
                c = p["ll"] if lit else p["d"]
            elif e == 2:
                c = p["l"] if lit else p["m"]
            elif e == 3:
                c = p["d"] if along % 4 == 0 else p["m"]
            elif e == 4:
                c = p["l"] if lit else p["m"]
            else:
                c = p["d"]
            px[x, y] = opaque(c)
    for ox, oy in ((0, 0), (P - 9, 0), (0, P - 9), (P - 9, P - 9)):
        cells = {(ox + i, oy + j) for i in range(9) for j in range(9)
                 if abs(i - 4) + abs(j - 4) <= 4}
        for (x, y) in cells:
            px[x, y] = opaque(p["void"])
        inner = {(x, y) for (x, y) in cells if abs(x - ox - 4) + abs(y - oy - 4) <= 3}
        pewter_shape(px, inner, p)
        px[ox + 4, oy + 4] = opaque(p["void"])
    return img


def _glass(tones, seed):
    """Silvered glass, periodic in GLASS_TILE: a base, a long diagonal sheen and a fainter echo of it,
    the slow shading a mirror has where it catches the room."""
    lo, base, hi, top = tones
    T = GLASS_TILE
    img = Image.new("RGBA", (T, T))
    px = img.load()
    shade = grain_field(T + INNER, T + INNER, seed, 0.5)
    for y in range(T):
        for x in range(T):
            band = (x + y) % T
            if band == 11:
                c = top
            elif band in (10, 12, 40):
                c = hi
            else:
                c = lo if shade[y % INNER][x % INNER] < -0.8 else base
            px[x, y] = opaque(c)
    return img


def a_mirror_glass():
    p = PEWTER
    return _glass((p["glass_d"], p["glass"], p["glass_l"], p["glass_ll"]), 89)


def a_mirror_glass_dark():
    p = PEWTER
    return _glass((p["dark_d"], p["dark"], p["dark_l"], p["dark_ll"]), 97)


def a_mirror_fog():
    """Breath on glass, 64x64 on 8: a translucent pale field whose alpha falls off over the border, so a
    patch of any size has soft edges."""
    P = BAG_TILE
    img = Image.new("RGBA", (P, P), (0, 0, 0, 0))
    px = img.load()
    for y in range(P):
        for x in range(P):
            e = min(x, y, P - 1 - x, P - 1 - y)
            a = 150 if e >= BORDER else int(150 * (e + 1) / (BORDER + 1))
            px[x, y] = (236, 242, 247, a)
    return img


def a_mirror_plaque():
    """An engraved pewter plate, 64x64 on 8: outline, bevel, an engraved rule two pixels in, a flat face."""
    p = PEWTER
    P = BAG_TILE
    img = Image.new("RGBA", (P, P), opaque(p["l"]))
    px = img.load()
    for y in range(P):
        for x in range(P):
            e = min(x, y, P - 1 - x, P - 1 - y)
            lit = (y == e or x == e) and not (P - 1 - y == e or P - 1 - x == e)
            if e == 0:
                px[x, y] = opaque(p["void"])
            elif e == 1:
                px[x, y] = opaque(p["ll"] if lit else p["m"])
            elif e == 3:
                px[x, y] = opaque(p["m"] if lit else p["ll"])
    for c in ((0, 0), (P - 1, 0), (0, P - 1), (P - 1, P - 1)):
        px[c] = (0, 0, 0, 0)
    return img


def mirror_button(state=""):
    """A pewter button, 32x32 cut on 4, rows uniform across x."""
    p = PEWTER
    face, hi, lo, edge = p["l"], p["ll"], p["m"], p["void"]
    if state == "_hover":
        face, hi, lo = p["ll"], (240, 244, 248), p["l"]
    elif state == "_off":
        face, hi, lo, edge = p["m"], p["m"], p["m"], p["d"]
    B = 32
    img, d = canvas(B, B)
    d.rectangle([0, 0, B - 1, B - 1], fill=opaque(face))
    d.line([(1, 1), (B - 2, 1)], fill=opaque(hi))
    d.line([(1, 1), (1, B - 2)], fill=opaque(hi))
    d.line([(1, B - 2), (B - 2, B - 2)], fill=opaque(lo))
    d.line([(B - 2, 1), (B - 2, B - 2)], fill=opaque(lo))
    d.rectangle([0, 0, B - 1, B - 1], outline=opaque(edge))
    for c in ((0, 0), (B - 1, 0), (0, B - 1), (B - 1, B - 1)):
        img.putpixel(c, (0, 0, 0, 0))
    return img


# --------------------------------------------------------------------------- Riddle's diary
#
# A small black diary from 1943: worn black leather with one gold-tooled line round the cover, an aged
# cream page inside (the ruling is drawn by the screen, under each line of the transcript, so it lines up
# with the text). Java: `DiaryWriteScreen`.

DIARY = dict(
    leather_dd=rgb("#0B0A0A"), leather_d=rgb("#151313"), leather=rgb("#1F1C1B"), leather_l=rgb("#2C2826"),
    scuff=rgb("#3B3532"),
    paper_dd=rgb("#B9A67E"), paper_d=rgb("#CDBF99"), paper=rgb("#DDD1AF"), paper_l=rgb("#E8DEC1"),
    fox=rgb("#B49B6C"),
)


def a_diary_cover():
    """Worn black leather, 64x64 on 8: a three-tone grain with the odd scuff, an outline, the cover's
    edge rounded at the corners, and a single gold-tooled line three pixels in."""
    d_ = DIARY
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    grain = grain_field(P + INNER, P + INNER, 101, 1.1)
    for y in range(P):
        for x in range(P):
            v = grain[(y - BORDER) % INNER][(x - BORDER) % INNER]
            c = d_["leather_d"] if v < -0.5 else d_["leather_l"] if v > 0.75 else d_["leather"]
            if v > 1.5:
                c = d_["scuff"]
            px[x, y] = opaque(c)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, P - 1, P - 1], outline=opaque(d_["leather_dd"]))
    d.rectangle([3, 3, P - 4, P - 4], outline=opaque(PAGE["gilt_d"]))
    for c in ((0, 0), (P - 1, 0), (0, P - 1), (P - 1, P - 1), (1, 0), (0, 1), (P - 2, 0), (P - 1, 1),
              (0, P - 2), (1, P - 1), (P - 1, P - 2), (P - 2, P - 1)):
        px[c] = (0, 0, 0, 0)
    return img


def a_diary_page():
    """An aged page, 64x64 on 8: cream stock with a slow grain and a few foxing specks, browning toward
    its edges -- the screen rules it."""
    d_ = DIARY
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    grain = grain_field(P + INNER, P + INNER, 103, 0.8)
    for y in range(P):
        for x in range(P):
            v = grain[(y - BORDER) % INNER][(x - BORDER) % INNER]
            c = d_["paper_d"] if v < -0.7 else d_["paper_l"] if v > 0.9 else d_["paper"]
            if v < -1.6:
                c = d_["fox"]
            e = min(x, y, P - 1 - x, P - 1 - y)
            if e < 3:
                c = mix(c, d_["paper_dd"], (3 - e) / 4.0)
            px[x, y] = opaque(c)
    return img


def diary_button(state=""):
    """A leather tab, 32x32 cut on 4: the cover's leather with the tooled gold line as its edge."""
    d_ = DIARY
    face, edge, hi = d_["leather"], PAGE["gilt_d"], d_["leather_l"]
    if state == "_hover":
        face, edge, hi = d_["leather_l"], PAGE["gilt"], d_["scuff"]
    elif state == "_off":
        face, edge, hi = d_["leather_d"], d_["scuff"], d_["leather_d"]
    B = 32
    img, d = canvas(B, B)
    d.rectangle([0, 0, B - 1, B - 1], fill=opaque(face))
    d.line([(1, 1), (B - 2, 1)], fill=opaque(hi))
    d.rectangle([0, 0, B - 1, B - 1], outline=opaque(d_["leather_dd"]))
    d.rectangle([1, 1, B - 2, B - 2], outline=opaque(edge))
    for c in ((0, 0), (B - 1, 0), (0, B - 1), (B - 1, B - 1)):
        img.putpixel(c, (0, 0, 0, 0))
    return img


# --------------------------------------------------------------------------- Pensieve
#
# A shallow stone basin with runes cut round its rim, holding memory: silvery stuff that is neither
# liquid nor gas, turning slowly. Java: `PensieveScreen` (the rim tiles on 64/8; the memory-stuff is
# drawn with drawTiled, periodic in 64, so it has no edge strips to seam).

STONE = dict(
    dd=rgb("#5E5A54"), d=rgb("#7E7A73"), m=rgb("#9C978F"), l=rgb("#B4AFA6"),
    groove=rgb("#4A4640"), lip=rgb("#D2CDC4"),
    mem_d=rgb("#BDC6D8"), mem=rgb("#CBD3E3"), mem_l=rgb("#D6DDEA"), mem_ll=rgb("#E2E8F2"),
)
# Five runes, 3x5, cut into the rim's top and bottom bands. Rows top to bottom, "#" is cut.
RUNES = (
    ("#.#", "##.", "#.#", "#..", "#.."),   # fehu-ish
    ("#..", "##.", "#.#", "#.#", "#.#"),   # uruz-ish
    ("#..", "##.", "#.#", "##.", "#.."),   # thurisaz-ish
    (".#.", "##.", "#.#", ".##", ".#."),   # ingwaz-ish
    ("#.#", "#.#", ".#.", "#.#", "#.#"),   # gebo-ish
)
MEMORY_TILE = 64


def a_pensieve_rim():
    """Carved stone, 64x64 on 8: a granite grain, an outline, a bevel lit top-left, runes cut into the
    top and bottom bands every 12px (four per 48px span, so the tiled edge repeats cleanly), a groove
    down the sides, chamfered corners."""
    st = STONE
    P = BAG_TILE
    img = Image.new("RGBA", (P, P))
    px = img.load()
    grain = grain_field(P + INNER, P + INNER, 107, 1.0)
    for y in range(P):
        for x in range(P):
            v = grain[(y - BORDER) % INNER][(x - BORDER) % INNER]
            px[x, y] = opaque(st["d"] if v < -0.6 else st["l"] if v > 0.8 else st["m"])
    for y in range(P):
        for x in range(P):
            e = min(x, y, P - 1 - x, P - 1 - y)
            lit = (y == e or x == e) and not (P - 1 - y == e or P - 1 - x == e)
            if e == 0:
                px[x, y] = opaque(st["dd"])
            elif e == 1:
                px[x, y] = opaque(st["lip"] if lit else st["d"])
            elif e == 7:
                px[x, y] = opaque(st["d"] if lit else st["lip"])
    # Runes on the top and bottom bands, and a groove down the sides.
    for band_y in (2, P - 7):
        for x in range(BORDER - INNER, P, 12):
            along = (x - BORDER) % INNER
            glyph = RUNES[(along // 12 + (0 if band_y == 2 else 2)) % len(RUNES)]
            gx = x + 4
            for j, row in enumerate(glyph):
                for i, ch in enumerate(row):
                    xx, yy = gx + i, band_y + j
                    if ch == "#" and 2 <= xx < P - 2:
                        px[xx, yy] = opaque(st["groove"])
                        if xx + 1 < P - 2 and row[i + 1:i + 2] != "#":
                            px[xx + 1, yy] = opaque(st["lip"])
    for gx in (3, P - 4):
        for y in range(9, P - 9):
            px[gx, y] = opaque(st["groove"])
            px[gx + 1, y] = opaque(st["lip"])
    for (cx, cy) in ((0, 0), (P - 1, 0), (0, P - 1), (P - 1, P - 1)):
        sx, sy = (1 if cx == 0 else -1), (1 if cy == 0 else -1)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            px[cx + sx * dx, cy + sy * dy] = (0, 0, 0, 0)
    return img


def a_pensieve_memory():
    """Memory-stuff, periodic in 64: slow wavy currents of light drifting across a silver-blue field,
    every 16px, each a bright core between softer edges, with a fainter shadow current between them.
    The waves are sines of x with a 64px period, so the tile wraps without a seam."""
    import math
    st = STONE
    T = MEMORY_TILE
    img = Image.new("RGBA", (T, T))
    px = img.load()
    for y in range(T):
        for x in range(T):
            w = y + 5 * math.sin(2 * math.pi * x / T) + 2 * math.sin(2 * math.pi * 2 * x / T + 1.0)
            k = w % 16
            if 0 <= k < 1:
                c = st["mem_ll"]
            elif k < 2.2 or k > 15.2:
                c = st["mem_l"]
            elif 8.5 <= k < 9.5:
                c = st["mem_d"]
            else:
                c = st["mem"]
            px[x, y] = opaque(c)
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
    "artefact/mirror/frame.png": a_mirror_frame,
    "artefact/mirror/glass.png": a_mirror_glass,
    "artefact/mirror/glass_dark.png": a_mirror_glass_dark,
    "artefact/mirror/fog.png": a_mirror_fog,
    "artefact/mirror/plaque.png": a_mirror_plaque,
    "sprites/mirror/button.png": lambda: mirror_button(),
    "sprites/mirror/button_hover.png": lambda: mirror_button("_hover"),
    "sprites/mirror/button_off.png": lambda: mirror_button("_off"),
    "artefact/diary/cover.png": a_diary_cover,
    "artefact/diary/page.png": a_diary_page,
    "sprites/diary/button.png": lambda: diary_button(),
    "sprites/diary/button_hover.png": lambda: diary_button("_hover"),
    "sprites/diary/button_off.png": lambda: diary_button("_off"),
    "artefact/pensieve/rim.png": a_pensieve_rim,
    "artefact/pensieve/memory.png": a_pensieve_memory,
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
