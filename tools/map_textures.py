#!/usr/bin/env python3
"""Procedural art for the Marauder's Map.

Writes everything under `assets/wizards_and_beasts/textures/gui/map/`.

Two conventions, both load-bearing, and both borrowed from `skill_chart_textures.py`
because they are what let one sheet serve a whole mod's worth of content:

  - **Everything is authored greyscale and tinted at draw time.** `GuiGraphics.blit`'s
    tinted overload multiplies, so a white pixel becomes the tint exactly and a mid-grey
    becomes a darker shade of it. One forest sprite therefore serves the dark forest, the
    birch wood and a modded biome a resource pack has styled green — the colour lives in
    `assets/.../map_biome_style/*.json`, not here.

  - **Tiles are a wash plus ink, not a fill.** Each terrain cell is a translucent white
    wash (which the tint turns into the biome's colour) with opaque strokes on top (which
    the tint turns into ink). That is what makes the parchment show through the country
    instead of the country covering the parchment, and it is the whole difference between
    a hand-tinted map and a heat map.

Drawn at 1:1 with hard pixels rather than supersampled. This is 16px cartographic
shorthand — six trees standing for a forest — and an antialiased downscale turns a
two-pixel trunk into a grey smudge. The one exception is `compass.png`, which is a single
large sprite with real diagonals in it.

Deterministic: every motif seeds its RNG from (type, variant), so a rerun is a no-op
unless this file changed.

Run from the repo root:  python tools/map_textures.py [--force] [--only name,name]
"""

import argparse
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402
# The page stock and its stains come from the GUI kit, so the map's paper is the same
# paper as the panel it sits in rather than a near relative mixed by hand.
from gui_parchment import mix, paper_colour, periodic_noise  # noqa: E402
from gui_parchment import skin as gp_skin  # noqa: E402

OUT = "src/main/resources/assets/wizards_and_beasts/textures/gui/map"
MARKER = marker("map_textures.py")

# ── Ink values ─────────────────────────────────────────────────────────────
# Greyscale, so these are luminance and alpha only. The names are what they become
# after the biome tint multiplies through.

WASH = (255, 255, 255, 116)        # the biome's colour, laid on thin
WASH_DEEP = (255, 255, 255, 168)   # a second coat, for water and dense canopy
INK = (96, 96, 96, 238)            # the pen
INK_SOFT = (150, 150, 150, 210)    # a lighter stroke: undergrowth, ripples, stipple
INK_FAINT = (190, 190, 190, 150)   # barely there: grain, speckle
CLEAR = (0, 0, 0, 0)

TILE = 16
VARIANTS = 4
TILE_ROWS = 32

MARKER_CELL = 16
MARKER_COLS = 8
MARKER_ROWS = 4

CONTROL_CELL = 12
CONTROL_COLS = 8


# ── Pixel helpers ──────────────────────────────────────────────────────────

def cell(size=TILE, fill=CLEAR):
    img = Image.new("RGBA", (size, size), fill)
    return img, img.load()


def px(buf, x, y, colour, size=TILE):
    """Set a pixel, ignoring anything off the cell. Every motif draws near the edges."""
    if 0 <= x < size and 0 <= y < size:
        buf[x, y] = colour


def blend(buf, x, y, colour, size=TILE):
    """Alpha-composite one pixel over what is already there.

    Motifs overlap -- a trunk under a canopy, a ripple over a wash -- and stamping opaque
    pixels would leave hard holes where a stroke crosses the wash it is meant to sit on.
    """
    if not (0 <= x < size and 0 <= y < size):
        return
    dr, dg, db, da = buf[x, y]
    sr, sg, sb, sa = colour
    a = sa / 255.0
    out_a = sa + int(da * (1 - a))
    if out_a == 0:
        buf[x, y] = CLEAR
        return
    buf[x, y] = (
        int((sr * sa + dr * da * (1 - a)) / out_a),
        int((sg * sa + dg * da * (1 - a)) / out_a),
        int((sb * sa + db * da * (1 - a)) / out_a),
        min(255, out_a),
    )


def wash(buf, rng, colour=WASH, speckle=True, size=TILE):
    """Lay the base coat, with a little mottling so a flat field is not flat."""
    for y in range(size):
        for x in range(size):
            c = colour
            if speckle and rng.random() < 0.16:
                jitter = rng.choice((-16, -8, 10, 18))
                c = (255, 255, 255, max(0, min(255, colour[3] + jitter)))
            buf[x, y] = c


def hline(buf, x0, x1, y, colour, size=TILE, dash=0, rng=None):
    for x in range(x0, x1 + 1):
        if dash and rng and rng.random() < dash:
            continue
        blend(buf, x, y, colour, size)


def vline(buf, x, y0, y1, colour, size=TILE, dash=0, rng=None):
    for y in range(y0, y1 + 1):
        if dash and rng and rng.random() < dash:
            continue
        blend(buf, x, y, colour, size)


def dot(buf, x, y, colour, size=TILE):
    blend(buf, x, y, colour, size)


def blob(buf, cx, cy, r, colour, size=TILE):
    """A filled disc, on the integer grid. Small radii read as rounded squares, which is
    what a 16px tree canopy has to be."""
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r // 2:
                blend(buf, x, y, colour, size)


def ring(buf, cx, cy, r, colour, size=TILE):
    for a in range(0, 360, 8):
        x = cx + int(round(r * math.cos(math.radians(a))))
        y = cy + int(round(r * math.sin(math.radians(a))))
        blend(buf, x, y, colour, size)


def chevron(buf, cx, cy, w, h, colour, size=TILE):
    """The hachure a hill gets: an inverted V, which is how ink says 'higher here'."""
    for i in range(w + 1):
        t = i / max(1, w)
        y = cy - int(round(h * (1 - abs(2 * t - 1))))
        blend(buf, cx - w // 2 + i, y, colour, size)


def triangle(buf, cx, base_y, half_w, height, colour, fill=None, size=TILE):
    """A peak: two ink flanks, optionally hachured inside."""
    for i in range(height + 1):
        t = i / max(1, height)
        span = int(round(half_w * (1 - t)))
        y = base_y - i
        blend(buf, cx - span, y, colour, size)
        blend(buf, cx + span, y, colour, size)
        if fill and i % 2 == 0:
            for x in range(cx - span + 1, cx + span):
                if (x + y) % 3 == 0:
                    blend(buf, x, y, fill, size)


def fir(buf, cx, base_y, h, colour, size=TILE):
    """A conifer: three stacked tiers over a stub of trunk."""
    tiers = 3
    for tier in range(tiers):
        top = base_y - h + tier * (h // tiers)
        half = 1 + tier
        for x in range(cx - half, cx + half + 1):
            blend(buf, x, top + h // tiers - 1, colour, size)
        blend(buf, cx, top, colour, size)
    blend(buf, cx, base_y, colour, size)


def broadleaf(buf, cx, base_y, r, colour, trunk=INK, size=TILE):
    blob(buf, cx, base_y - r - 1, r, colour, size)
    blend(buf, cx, base_y, trunk, size)
    blend(buf, cx, base_y - 1, trunk, size)


# ── Terrain motifs, one per row of tiles.png ───────────────────────────────
#
# Each takes the pixel buffer and a seeded RNG. The row index each is registered
# under is the `tile` value a biome style writes, and those indices are frozen: a
# resource pack in the wild already refers to them by number.

def t_parchment(buf, rng):
    wash(buf, rng, (255, 255, 255, 74))
    for _ in range(3):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def _waves(buf, rng, rows, colour, dash):
    wash(buf, rng, WASH_DEEP, speckle=False)
    for row in rows:
        y = row + rng.choice((-1, 0, 0, 1))
        x = rng.randrange(0, 3)
        while x < TILE:
            run = rng.randrange(2, 5)
            hline(buf, x, min(TILE - 1, x + run), y, colour)
            # A crest on the longer dashes, so the line reads as water rather than as a rule.
            if run >= 3:
                blend(buf, x + run // 2, y - 1, colour)
            x += run + rng.randrange(2, 5)
        if dash:
            for _ in range(2):
                dot(buf, rng.randrange(TILE), y + 2, INK_FAINT)


def t_deep_water(buf, rng):
    _waves(buf, rng, (3, 8, 13), INK_SOFT, dash=False)


def t_water(buf, rng):
    _waves(buf, rng, (5, 12), INK_SOFT, dash=False)


def t_shore(buf, rng):
    wash(buf, rng)
    # Stipple thinning away from the waterline: the standard cartographic beach.
    for y in range(TILE):
        density = 0.42 * (1.0 - y / TILE)
        for x in range(TILE):
            if rng.random() < density:
                dot(buf, x, y, INK_FAINT)
    for _ in range(3):
        x = rng.randrange(TILE)
        hline(buf, x, min(TILE - 1, x + rng.randrange(1, 3)), TILE - 2 - rng.randrange(2), INK_SOFT)


def t_hill(buf, rng):
    wash(buf, rng)
    for _ in range(2):
        chevron(buf, rng.randrange(4, 12), rng.randrange(7, 13), rng.randrange(5, 8), 2, INK_SOFT)


def t_mountain(buf, rng):
    wash(buf, rng)
    triangle(buf, rng.randrange(5, 8), 13, 4, 7, INK, fill=INK_SOFT)
    triangle(buf, rng.randrange(10, 13), 14, 3, 5, INK, fill=INK_SOFT)


def t_peak(buf, rng):
    wash(buf, rng, (255, 255, 255, 150))
    cx = rng.randrange(6, 10)
    triangle(buf, cx, 14, 5, 9, INK)
    # The snow cap is a hole in the ink, not white paint: white would tint to the biome
    # colour and vanish. Leaving the wash bare is what reads as snow on parchment.
    for i in range(3):
        for x in range(cx - i, cx + i + 1):
            blend(buf, x, 6 + i, (255, 255, 255, 210))


def t_plains(buf, rng):
    wash(buf, rng)
    for _ in range(rng.randrange(4, 7)):
        x, y = rng.randrange(2, 14), rng.randrange(3, 14)
        vline(buf, x, y, y + 2, INK)
        blend(buf, x - 1, y + 1, INK_SOFT)
        blend(buf, x + 1, y + 1, INK_SOFT)


def t_forest(buf, rng):
    wash(buf, rng)
    for _ in range(3):
        broadleaf(buf, rng.randrange(3, 14), rng.randrange(9, 15), 2, INK)


def t_birch(buf, rng):
    wash(buf, rng, (255, 255, 255, 96))
    for _ in range(3):
        cx, base = rng.randrange(3, 14), rng.randrange(10, 15)
        blob(buf, cx, base - 4, 2, INK_SOFT)
        vline(buf, cx, base - 2, base, INK)


def t_dark_forest(buf, rng):
    wash(buf, rng, WASH_DEEP)
    for _ in range(5):
        broadleaf(buf, rng.randrange(2, 15), rng.randrange(8, 16), 2, INK)


def t_conifer(buf, rng):
    wash(buf, rng)
    for _ in range(3):
        fir(buf, rng.randrange(3, 14), rng.randrange(12, 16), 8, INK)


def t_jungle(buf, rng):
    wash(buf, rng, WASH_DEEP)
    for _ in range(4):
        broadleaf(buf, rng.randrange(2, 15), rng.randrange(8, 16), 3, INK)
    for _ in range(6):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_SOFT)


def t_swamp(buf, rng):
    wash(buf, rng, WASH_DEEP, speckle=False)
    for y in (3, 8, 13):
        x = rng.randrange(0, 3)
        while x < TILE:
            hline(buf, x, min(TILE - 1, x + 2), y + rng.choice((-1, 0, 1)), INK_SOFT)
            x += rng.randrange(4, 7)
    for _ in range(3):
        cx, base = rng.randrange(2, 15), rng.randrange(6, 15)
        vline(buf, cx, base - 2, base, INK)
        blend(buf, cx - 1, base - 2, INK_SOFT)
        blend(buf, cx + 1, base - 2, INK_SOFT)


def t_desert(buf, rng):
    wash(buf, rng)
    # Dune arcs: a shallow curve, twice, plus grit.
    for _ in range(2):
        cy = rng.randrange(5, 13)
        x0 = rng.randrange(0, 6)
        for i in range(10):
            blend(buf, x0 + i, cy - int(round(1.6 * math.sin(i / 9 * math.pi))), INK_SOFT)
    for _ in range(6):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def t_badlands(buf, rng):
    wash(buf, rng)
    for y in range(2, 15, 3):
        hline(buf, 0, 15, y + rng.choice((-1, 0)), INK_SOFT, dash=0.35, rng=rng)


def t_savanna(buf, rng):
    wash(buf, rng)
    for _ in range(2):
        cx, base = rng.randrange(3, 13), rng.randrange(11, 15)
        vline(buf, cx, base - 4, base, INK)
        hline(buf, cx - 3, cx + 3, base - 5, INK)          # the flat acacia crown
        hline(buf, cx - 2, cx + 2, base - 6, INK_SOFT)
    for _ in range(4):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def t_snow(buf, rng):
    wash(buf, rng, (255, 255, 255, 92), speckle=False)
    for _ in range(3):
        y = rng.randrange(3, 14)
        x0 = rng.randrange(0, 7)
        for i in range(rng.randrange(4, 8)):
            blend(buf, x0 + i, y - (1 if 1 < i < 4 else 0), INK_SOFT)
    for _ in range(4):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def t_mushroom(buf, rng):
    wash(buf, rng)
    for _ in range(2):
        cx, base = rng.randrange(4, 13), rng.randrange(10, 15)
        for i in range(4):
            hline(buf, cx - 3 + i // 2, cx + 3 - i // 2, base - 3 - i, INK if i == 0 else INK_SOFT)
        vline(buf, cx, base - 2, base, INK)


def t_cherry(buf, rng):
    wash(buf, rng, (255, 255, 255, 104))
    for _ in range(3):
        cx, base = rng.randrange(3, 14), rng.randrange(10, 15)
        for _ in range(6):
            dot(buf, cx + rng.randrange(-2, 3), base - 4 + rng.randrange(-2, 2), INK_SOFT)
        vline(buf, cx, base - 2, base, INK)


def t_nether_waste(buf, rng):
    wash(buf, rng, WASH_DEEP)
    for _ in range(2):
        x, y = rng.randrange(1, 8), rng.randrange(2, 12)
        for i in range(rng.randrange(6, 11)):
            blend(buf, x + i, y + (i // 3), INK)
            if i == 4:
                for j in range(3):
                    blend(buf, x + i + j, y + (i // 3) - j - 1, INK_SOFT)


def t_crimson(buf, rng):
    wash(buf, rng, WASH_DEEP)
    for _ in range(3):
        cx, base = rng.randrange(3, 14), 15
        vline(buf, cx, base - 8, base, INK)
        blob(buf, cx, base - 9, 2, INK_SOFT)


def t_warped(buf, rng):
    wash(buf, rng, WASH_DEEP)
    for _ in range(3):
        cx, base = rng.randrange(3, 14), 15
        vline(buf, cx, base - 9, base, INK)
        hline(buf, cx - 2, cx + 2, base - 10, INK_SOFT)


def t_soul(buf, rng):
    wash(buf, rng)
    for _ in range(4):
        cx, cy = rng.randrange(2, 14), rng.randrange(2, 14)
        ring(buf, cx, cy, 2, INK_SOFT)
    for y in (5, 12):
        hline(buf, 0, 15, y, INK_FAINT, dash=0.55, rng=rng)


def t_basalt(buf, rng):
    wash(buf, rng, WASH_DEEP)
    x = rng.randrange(0, 3)
    while x < TILE:
        top = rng.randrange(2, 8)
        vline(buf, x, top, 15, INK)
        vline(buf, x + 1, top + 1, 15, INK_SOFT)
        x += rng.randrange(3, 5)


def t_end(buf, rng):
    wash(buf, rng, (255, 255, 255, 96))
    for _ in range(3):
        cx, base = rng.randrange(3, 13), rng.randrange(10, 15)
        vline(buf, cx, base - 5, base, INK)
        hline(buf, cx - 1, cx + 1, base - 5, INK_SOFT)
    for _ in range(4):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def t_meadow(buf, rng):
    wash(buf, rng)
    for _ in range(4):
        x, y = rng.randrange(2, 14), rng.randrange(4, 14)
        vline(buf, x, y, y + 2, INK_SOFT)
    for _ in range(5):
        cx, cy = rng.randrange(2, 14), rng.randrange(2, 14)
        blend(buf, cx, cy, INK)
        blend(buf, cx - 1, cy, INK_SOFT)
        blend(buf, cx + 1, cy, INK_SOFT)
        blend(buf, cx, cy - 1, INK_SOFT)


def t_bamboo(buf, rng):
    wash(buf, rng)
    for _ in range(4):
        x, top = rng.randrange(2, 15), rng.randrange(2, 6)
        vline(buf, x, top, 15, INK_SOFT)
        for node in range(top + 2, 15, 4):
            blend(buf, x, node, INK)


def t_mangrove(buf, rng):
    wash(buf, rng, WASH_DEEP, speckle=False)
    for _ in range(3):
        cx, base = rng.randrange(3, 13), rng.randrange(11, 15)
        blob(buf, cx, base - 5, 2, INK_SOFT)
        # Prop roots: the thing that makes a mangrove look like a mangrove.
        for leg in (-2, 0, 2):
            vline(buf, cx + leg, base - 3, base, INK)
    hline(buf, 0, 15, 15, INK_FAINT, dash=0.4, rng=rng)


def t_ice(buf, rng):
    wash(buf, rng, (255, 255, 255, 108), speckle=False)
    for _ in range(3):
        cx, cy = rng.randrange(3, 13), rng.randrange(3, 13)
        for d in (-2, -1, 1, 2):
            blend(buf, cx + d, cy + abs(d) - 2, INK_SOFT)
            blend(buf, cx + d, cy - abs(d) + 2, INK_SOFT)


def t_stony(buf, rng):
    wash(buf, rng)
    for _ in range(4):
        cx, cy = rng.randrange(2, 13), rng.randrange(2, 13)
        w = rng.randrange(2, 4)
        hline(buf, cx, cx + w, cy, INK)
        hline(buf, cx, cx + w - 1, cy + 1, INK_SOFT)
        blend(buf, cx - 1, cy + 1, INK)
    for _ in range(5):
        dot(buf, rng.randrange(TILE), rng.randrange(TILE), INK_FAINT)


def t_arcane(buf, rng):
    wash(buf, rng)
    cx, cy = rng.randrange(6, 11), rng.randrange(6, 11)
    # A six-pointed star: the map's shorthand for "something here is not ordinary".
    for a in range(0, 360, 60):
        for r in range(1, 5):
            blend(buf, cx + int(round(r * math.cos(math.radians(a)))),
                  cy + int(round(r * math.sin(math.radians(a)))), INK_SOFT)
    blend(buf, cx, cy, INK)


TILE_ROWS_TABLE = [
    t_parchment, t_deep_water, t_water, t_shore, t_hill, t_mountain, t_peak,
    t_plains, t_forest, t_birch, t_dark_forest, t_conifer, t_jungle, t_swamp,
    t_desert, t_badlands, t_savanna, t_snow, t_mushroom, t_cherry,
    t_nether_waste, t_crimson, t_warped, t_soul, t_basalt, t_end,
    t_meadow, t_bamboo, t_mangrove, t_ice, t_stony, t_arcane,
]


def build_tiles():
    sheet = Image.new("RGBA", (VARIANTS * TILE, TILE_ROWS * TILE), CLEAR)
    for row in range(TILE_ROWS):
        motif = TILE_ROWS_TABLE[row] if row < len(TILE_ROWS_TABLE) else t_parchment
        for variant in range(VARIANTS):
            img, buf = cell()
            motif(buf, random.Random(row * 977 + variant * 31 + 7))
            sheet.paste(img, (variant * TILE, row * TILE))
    return sheet


# -- Marker symbols ---------------------------------------------------------
#
# Written out pixel by pixel rather than assembled from rectangles.
#
# The first attempt drew each symbol as a solid silhouette with a grey second tone, and it
# failed for a reason worth recording: the tint *multiplies*, so a 66%-grey pixel under the
# map's dark brown ink lands within a few values of a white one. Every icon collapsed into
# a featureless blob. Tone on a tinted sheet has to come from **alpha**, not from
# luminance -- a half-alpha pixel lets the parchment through and reads as a genuinely
# lighter shade of the same ink at any tint.
#
# So the three characters below are opacity, not colour:
#
#   "#"  full ink -- the stroke
#   ":"  half ink -- interior tone, roofs, shadow
#   "."  bare parchment
#
# And the symbols are authored so the *art* carries the size hierarchy: the castle fills
# its whole cell, a pin occupies the middle third of one. Every marker is then drawn at the
# same native 16px, which keeps them all on the pixel grid -- scaling a 9px symbol out of a
# 16px cell is what makes a symbol set look soft.

STROKE = (255, 255, 255, 255)
TONE = (255, 255, 255, 104)

PIXELS = {".": CLEAR, "#": STROKE, ":": TONE}


def m_cell():
    img = Image.new("RGBA", (MARKER_CELL, MARKER_CELL), CLEAR)
    return img, img.load()


def stamp(rows):
    """Turn a pixel map into a cell. Short rows pad with parchment, so a symbol that does
    not reach the bottom edge need not say so."""
    img, buf = m_cell()
    for y, row in enumerate(rows[:MARKER_CELL]):
        for x, ch in enumerate(row[:MARKER_CELL]):
            buf[x, y] = PIXELS.get(ch, CLEAR)
    return img


# The holder's own marks ------------------------------------------------------

WAYPOINT = [
    "................",
    "................",
    ".....######.....",
    "....##::::##....",
    "...##::##::##...",
    "...#::####::#...",
    "...#::#..#::#...",
    "...#::####::#...",
    "...##::##::##...",
    "....##::::##....",
    ".....##::##.....",
    "......#::#......",
    "......#::#......",
    ".......##.......",
    "................",
    "................",
]

HOME = [
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....##::##.....",
    "....##::::##....",
    "...##::::::##...",
    "..##::::::::##..",
    ".####::::::####.",
    "...#::::::::#...",
    "...#::####::#...",
    "...#::#::#::#...",
    "...#::#::#::#...",
    "...#::#::#::#...",
    "...##########...",
    "................",
]

CAMP = [
    "................",
    "................",
    ".......##.......",
    ".......##.......",
    "......####......",
    "......#::#......",
    ".....##::##.....",
    ".....#::::#.....",
    "....##::::##....",
    "....#::::::#....",
    "...##::##::##...",
    "...#::#::#::#...",
    "..##::#::#::##..",
    "..#:::#::#:::#..",
    "..############..",
    "................",
]

SHOP = [
    "................",
    "................",
    "..############..",
    "..#::::::::::#..",
    "..#::::::::::#..",
    "..##.##.##.##...",
    "...#..#..#..#...",
    "..############..",
    "..#::::::::::#..",
    "..#:####:##::#..",
    "..#:#::#:##::#..",
    "..#:#::#:##::#..",
    "..#:####:##::#..",
    "..#::::::##::#..",
    "..############..",
    "................",
]

TREASURE = [
    "................",
    "................",
    "................",
    "....########....",
    "...##::::::##...",
    "..##::::::::##..",
    "..############..",
    "..#::::####::#..",
    "..#::::#..#::#..",
    "..#::::####::#..",
    "..#::::::::::#..",
    "..#::::::::::#..",
    "..############..",
    "................",
    "................",
    "................",
]

DANGER = [
    "................",
    ".......##.......",
    "......####......",
    ".....##::##.....",
    "....##:##:##....",
    "...##::##::##...",
    "..##::.##.::##..",
    ".##:::.##.:::##.",
    ".##::::..::::##.",
    "..##::.##.::##..",
    "...##::##::##...",
    "....##:##:##....",
    ".....##::##.....",
    "......####......",
    ".......##.......",
    "................",
]

CREATURE = [
    "................",
    "................",
    "..##...##...##..",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    "..##...##...##..",
    "................",
    "....########....",
    "..##::::::::##..",
    ".#::::::::::::#.",
    ".#::::::::::::#.",
    ".#::::::::::::#.",
    "..##::::::::##..",
    "....########....",
    "................",
    "................",
]

DEATH = [
    "................",
    "................",
    ".....######.....",
    "....##::::##....",
    "...##::::::##...",
    "...#::::::::#...",
    "...#:::##:::#...",
    "...#:::##:::#...",
    "...#:######:#...",
    "...#:::##:::#...",
    "...#:::##:::#...",
    "...#:::##:::#...",
    "...#::::::::#...",
    "...##########...",
    ".##############.",
    "................",
]

# The wizarding world ---------------------------------------------------------

# Hogwarts. The one symbol that has to be recognised without hovering it, so it fills the
# cell and carries a silhouette nothing else on the page has: a great tower flanked by two
# unequal ones, over a crenellated curtain wall.
CASTLE = [
    ".......##.......",
    "......####......",
    ".....##::##.....",
    "..##.##::##.##..",
    ".####.#::#.####.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    "##.####::####.##",
    "################",
    "#::::::::::::::#",
    "#:::::####:::::#",
    "#:::::#::#:::::#",
    "#######::#######",
]

VILLAGE = [
    "................",
    "................",
    "...##......##...",
    "..####....####..",
    ".##::##..##::##.",
    "##::::##.##::::#",
    "#::::::#.#:::::#",
    "#:####:#.#:###:#",
    "#:#::#:#.#:#:#:#",
    "#:#::#:#.#:#:#:#",
    "#:####:#.#:###:#",
    "#::::::#.#:::::#",
    "########.#######",
    "................",
    "................",
    "................",
]

FORTRESS = [
    "................",
    "................",
    ".##.##.##.##.##.",
    ".##.##.##.##.##.",
    ".##############.",
    ".#::::::::::::#.",
    ".#::##::::##::#.",
    ".#::##::::##::#.",
    ".#::::::::::::#.",
    ".#::::####::::#.",
    ".#::::#::#::::#.",
    ".#::::#::#::::#.",
    ".#::::#::#::::#.",
    ".##############.",
    "................",
    "................",
]

RUIN = [
    "................",
    "................",
    "................",
    ".....####.......",
    "....##::##......",
    "..##.#::#...##..",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    ".#::#.#::#.#::#.",
    "################",
    "................",
    "................",
]

STRUCTURE = [
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....##::##.....",
    "....##::::##....",
    "...##::::::##...",
    "..##::::::::##..",
    "..##::::::::##..",
    "...##::::::##...",
    "....##::::##....",
    ".....##::##.....",
    "......####......",
    ".......##.......",
    "................",
    "................",
]

TOWER = [
    ".......#........",
    "......###.......",
    ".....##:##......",
    "....##:::##.....",
    "...##:::::##....",
    "..###########...",
    "...#:::::::#....",
    "...#:#####:#....",
    "...#:#:::#:#....",
    "...#:#####:#....",
    "...#:::::::#....",
    "...#:#####:#....",
    "...#:#:::#:#....",
    "...#:#:::#:#....",
    "...#########....",
    "................",
]

# Diagon Alley: the brick archway you tap through, which is the image everybody has of it.
ARCH = [
    "................",
    ".##############.",
    ".#::::::::::::#.",
    ".#:##:##:##:#:#.",
    ".#::::::::::::#.",
    ".#:::######:::#.",
    ".#::##::::##::#.",
    ".#::#::::::#::#.",
    ".#:##::::::##:#.",
    ".#::#::::::#::#.",
    ".#:##::::::##:#.",
    ".#::#::::::#::#.",
    ".#:##::::::##:#.",
    ".#::#::::::#::#.",
    ".##############.",
    "................",
]

# Gringotts: a vault door. The lock ring is what stops it reading as a generic building.
VAULT = [
    "................",
    ".##############.",
    ".#::::::::::::#.",
    ".#:::######:::#.",
    ".#::##::::##::#.",
    ".#:#::::::::#:#.",
    ".#:#:::##:::#:#.",
    ".#:#::#::#::#:#.",
    ".#:#::#::#::#:#.",
    ".#:#:::##:::#:#.",
    ".#:#::::::::#:#.",
    ".#::##::::##::#.",
    ".#:::######:::#.",
    ".#::::::::::::#.",
    ".##############.",
    "................",
]

CAULDRON = [
    "................",
    "....#......#....",
    "....:#....#:....",
    ".....:#..#:.....",
    "................",
    "..############..",
    "..#::::::::::#..",
    "..#::::::::::#..",
    "..#::::::::::#..",
    "...#::::::::#...",
    "...#::::::::#...",
    "....########....",
    "....#......#....",
    "...##......##...",
    "................",
    "................",
]

HEARTH = [
    "................",
    ".##############.",
    ".#::::::::::::#.",
    ".##############.",
    ".#::::::::::::#.",
    ".#:##########:#.",
    ".#:#........#:#.",
    ".#:#...##...#:#.",
    ".#:#..#::#..#:#.",
    ".#:#.#::::#.#:#.",
    ".#:#.#::::#.#:#.",
    ".#:#..####..#:#.",
    ".#:#........#:#.",
    ".#:##########:#.",
    ".##############.",
    "................",
]

# Apparition: two halves of one shape pulled apart, which is what the crack looks like.
APPARATE = [
    "................",
    "....#......#....",
    ".....#....#.....",
    "........##......",
    ".......##:#.....",
    "......##::#.....",
    ".....##:::#.....",
    "......##::#.....",
    ".......#::##....",
    "........#::##...",
    ".......##::#....",
    "......##::#.....",
    ".....##:#.......",
    "......#.........",
    "....#......#....",
    "................",
]

STAR = [
    ".......##.......",
    ".......##.......",
    "......#::#......",
    "..#...#::#...#..",
    "..##..#::#..##..",
    "...##.#::#.##...",
    "....##::::##....",
    ".####::::::####.",
    ".####::::::####.",
    "....##::::##....",
    "...##.#::#.##...",
    "..##..#::#..##..",
    "..#...#::#...#..",
    "......#::#......",
    ".......##.......",
    "................",
]

# Azkaban: a barred tower on a sea crag. The waterline is the half of it that says
# "island" -- a tower alone is a tower.
PRISON = [
    "................",
    "....########....",
    "....#::::::#....",
    "...##::::::##...",
    "...#::::::::#...",
    "...#:######:#...",
    "...#:#::::#:#...",
    "...#:######:#...",
    "...#::::::::#...",
    "...#:######:#...",
    "...#:#::::#:#...",
    "...#:######:#...",
    "...#::::::::#...",
    "..##::::::::##..",
    ".##############.",
    "::::::::::::::::",
]

SERPENT = [
    "................",
    "......#####.....",
    "....##:::::##...",
    "...##:::::::#...",
    "...#::#.....#...",
    "...#::#.........",
    "...##::#........",
    "....##::#.......",
    ".....##::#......",
    "......##::#.....",
    ".......#::#.....",
    "...##..#::#.....",
    "..#::###::#.....",
    "..#:::::::#.....",
    "...#######......",
    "................",
]

# The Ministry: a seal. Officialdom is a circle with a device stamped in it.
EMBLEM = [
    "................",
    ".....######.....",
    "...##::::::##...",
    "..##::::::::##..",
    ".##::#::::#::##.",
    ".#:::##::##:::#.",
    ".#:::#:##:#:::#.",
    ".#:::#:##:#:::#.",
    ".#:::#::::#:::#.",
    ".#:::#::::#:::#.",
    ".##::#::::#::##.",
    "..##::::::::##..",
    "...##::::::##...",
    ".....######.....",
    "................",
    "................",
]

# Hogsmeade: a crooked row of gabled shopfronts over cobbles.
ALLEY = [
    "................",
    "................",
    ".......##.......",
    "......####......",
    "..##..#::#..##..",
    ".####.#::#.####.",
    ".#::###::###::#.",
    ".#::#::::::#::#.",
    "################",
    "#::##::##::##::#",
    "#::##::##::##::#",
    "#::::::::::::::#",
    "#:##:::##:::##:#",
    "#:##:::##:::##:#",
    "################",
    "................",
]

MARKER_TABLE = [
    WAYPOINT, HOME, CAMP, SHOP, TREASURE, DANGER, CREATURE, DEATH,
    CASTLE, VILLAGE, FORTRESS, RUIN, STRUCTURE, TOWER, ARCH, VAULT,
    CAULDRON, HEARTH, APPARATE, STAR, PRISON, SERPENT, EMBLEM, ALLEY,
]


def build_markers():
    sheet = Image.new("RGBA", (MARKER_COLS * MARKER_CELL, MARKER_ROWS * MARKER_CELL), CLEAR)
    for index, rows in enumerate(MARKER_TABLE):
        sheet.paste(stamp(rows), ((index % MARKER_COLS) * MARKER_CELL,
                                  (index // MARKER_COLS) * MARKER_CELL))
    return sheet


# ── Single sprites ─────────────────────────────────────────────────────────

SOLID = STROKE
SHADE = TONE


PLAYER_MARK = [
    ".......##.......",
    "......####......",
    "......####......",
    ".....##::##.....",
    ".....##::##.....",
    "....##::::##....",
    "....##::::##....",
    "...##::::::##...",
    "...##::::::##...",
    "..##::::::::##..",
    "..##::::::::##..",
    ".##::::##::::##.",
    ".##:::####:::##.",
    ".#::###..###::#.",
    ".####......####.",
    "................",
]

TRACKED_MARK = [
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    "......####......",
    ".....##::##.....",
    ".....##::##.....",
    "....##::::##....",
    "....##::::##....",
    "....###..###....",
    "................",
    "................",
    "................",
    "................",
    "................",
]

FOOTPRINT = [
    "........",
    "...##...",
    "..####..",
    "..####..",
    "...##...",
    "..####..",
    "..####..",
    "...##...",
]


def build_player_mark():
    """The holder: an ink arrowhead with a notched tail, drawn pointing north so the
    screen can rotate it to a heading. Directional by shape rather than by a nub, because
    a nub on a dot is not read as a heading -- it is read as a speck of dirt."""
    return stamp(PLAYER_MARK)


def build_tracked_mark():
    """Everyone else: the same arrowhead at half the mass, so a crowd of them still
    resolves into individuals instead of into a blot, and so the holder's own mark is
    never mistaken for one of them."""
    return stamp(TRACKED_MARK)


def build_footprint():
    """One step in a trail. Deliberately shapeless at this size -- it is drawn small and
    fading, and detail nobody can see costs the same as detail they can."""
    img = Image.new("RGBA", (8, 8), CLEAR)
    buf = img.load()
    for y, row in enumerate(FOOTPRINT):
        for x, ch in enumerate(row):
            buf[x, y] = PIXELS.get(ch, CLEAR)
    return img


def build_compass():
    """The rose. The one sprite drawn supersampled: it has true diagonals, and a 32px
    star built from integer pixels reads as a lumpy plus sign."""
    ss = 4
    size = 32
    img = Image.new("RGBA", (size * ss, size * ss), CLEAR)
    d = ImageDraw.Draw(img)
    c = size * ss / 2

    d.ellipse([2 * ss, 2 * ss, (size - 2) * ss, (size - 2) * ss], outline=SOLID, width=ss)
    d.ellipse([5 * ss, 5 * ss, (size - 5) * ss, (size - 5) * ss], outline=SHADE, width=ss // 2)

    for angle, length in ((0, 13), (90, 7), (180, 7), (270, 7)):
        rad = math.radians(angle - 90)
        tip = (c + math.cos(rad) * length * ss, c + math.sin(rad) * length * ss)
        left = (c + math.cos(rad + 2.4) * 3.2 * ss, c + math.sin(rad + 2.4) * 3.2 * ss)
        right = (c + math.cos(rad - 2.4) * 3.2 * ss, c + math.sin(rad - 2.4) * 3.2 * ss)
        # North gets the solid point; the other three are shaded, which is how a rose
        # says which way is up without a letter on it.
        d.polygon([tip, left, (c, c), right], fill=SOLID if angle == 0 else SHADE)

    for angle in (45, 135, 225, 315):
        rad = math.radians(angle - 90)
        d.line([(c, c), (c + math.cos(rad) * 8 * ss, c + math.sin(rad) * 8 * ss)],
               fill=SHADE, width=ss)

    out = img.resize((size, size), Image.LANCZOS)
    return out


def _map_paper():
    """The page stock: `gui_parchment.py`'s `marauders_map` skin, one step lighter.

    Same family as the panel it sits in -- same ramp, same scorch, same ink -- but the
    drawing surface is cut from the skin's *light* tones. The terrain is a translucent wash
    composited over this sheet, and on the panel's own face (DAC291) every biome tint came
    out a shade muddier than the styles were tuned for. One ramp step up keeps the country
    reading as colour on paper rather than as stain on stain.
    """
    s = gp_skin("marauders_map")
    s = dict(s, paper_d=mix(s["paper_d"], s["paper"], 0.5), paper=s["paper_l"],
             paper_l=s["paper_ll"])
    return s


def build_parchment():
    """Tileable paper grain, in the kit's posterised pixel-art paper.

    Built from `gui_parchment.periodic_noise` rather than `grain_field`, because this
    sprite tiles at 64 while the kit's grain wraps at its 48px panel span -- a 48-periodic
    field on a 64px tile would put a seam every tile. Same recipe otherwise: cloud, mid and
    fine noise, horizontal fibres, the odd foxing speck, mapped through `paper_colour`.
    Drawn untinted under the terrain, so unlike everything else here its colours are literal.
    """
    size = 64
    s = _map_paper()
    seed = 20260821
    # Smaller clouds than the kit's, and fainter: this tile repeats across a 400px page,
    # and a 32px blotch at full weight turns into a wallpaper figure after three repeats.
    cloud = periodic_noise(size, size, size, 16, seed)
    mid = periodic_noise(size, size, size, 8, seed + 1)
    fine = periodic_noise(size, size, size, 4, seed + 2)
    field = [[(cloud[y][x] - 0.5) * 0.4 + (mid[y][x] - 0.5) * 0.4 + (fine[y][x] - 0.5) * 0.5
              for x in range(size)] for y in range(size)]
    rng = random.Random(seed + 3)
    # Fibres: short horizontal streaks, the grain direction of a real skin. Wrapped, so the
    # tile has no seam.
    for _ in range(40):
        fx, fy = rng.randrange(size), rng.randrange(size)
        v = rng.choice((-0.55, 0.45))
        for i in range(rng.randint(3, 7)):
            field[fy][(fx + i) % size] += v
    for _ in range(8):
        field[rng.randrange(size)][rng.randrange(size)] -= 1.3

    img = Image.new("RGBA", (size, size))
    buf = img.load()
    for y in range(size):
        for x in range(size):
            buf[x, y] = paper_colour(s, field[y][x] * s["grain"]) + (255,)
    return img


def build_creases():
    """Fold lines and edge burn, stretched over the whole page.

    Mostly transparent: this is the layer that makes the terrain look like it is *on*
    something. Drawn above the tiles rather than below, because a crease runs across
    whatever is printed on the paper -- underneath it would read as a river.
    """
    size = 256
    s = _map_paper()
    fold = s["burnt"]           # the valley of a fold, in the skin's own scorch
    ridge = s["paper_ll"]       # the lit ridge beside it
    img = Image.new("RGBA", (size, size), CLEAR)
    d = ImageDraw.Draw(img)

    # Two vertical folds and one horizontal: the map is folded in three the long way,
    # which is how a large sheet is actually folded. Kept faint -- this sheet is
    # stretched over the whole viewport, so a crease that reads correctly at 256px
    # becomes a 40-pixel band of shadow across the terrain at panel size, and the map
    # ends up looking striped rather than folded. The edge burn carries the age instead.
    for x in (size // 3, 2 * size // 3):
        d.line([(x, 0), (x, size)], fill=fold + (26,), width=2)
        d.line([(x + 2, 0), (x + 2, size)], fill=ridge + (30,), width=1)
    d.line([(0, size // 2), (size, size // 2)], fill=fold + (24,), width=2)
    d.line([(0, size // 2 + 2), (size, size // 2 + 2)], fill=ridge + (26,), width=1)

    img = img.filter(ImageFilter.GaussianBlur(0.7))

    # Wear where the folds cross: a much-opened map rubs through first at its crossings.
    rng = random.Random(20260923)
    wear = Image.new("RGBA", (size, size), CLEAR)
    wd = ImageDraw.Draw(wear)
    for x in (size // 3, 2 * size // 3):
        cy = size // 2
        wd.ellipse([x - 9, cy - 7, x + 9, cy + 7], fill=s["stain"] + (34,))
    # Old spills: a few broad, soft tide-marks in the skin's stain colour. Few and large,
    # because the tile sprite already carries the fine foxing and a second layer of small
    # specks at stretch scale would read as noise.
    for _ in range(4):
        x, y = rng.randrange(24, size - 24), rng.randrange(24, size - 24)
        r = rng.randrange(10, 22)
        wd.ellipse([x - r, y - r, x + r, y + r], fill=s["stain"] + (22,))
        wd.ellipse([x - r, y - r, x + r, y + r], outline=s["stain"] + (40,))
    wear = wear.filter(ImageFilter.GaussianBlur(2.2))
    img.alpha_composite(wear)

    # Edge burn, painted after the blur so the corners stay tight against the frame.
    burn = Image.new("RGBA", (size, size), CLEAR)
    bd = ImageDraw.Draw(burn)
    for i in range(18):
        a = int(84 * (1 - i / 18) ** 2)
        bd.rectangle([i, i, size - 1 - i, size - 1 - i], outline=s["burnt"] + (a,))
    img.alpha_composite(burn)
    return img


def build_controls():
    """The icon row. Authored white so the button's own text colour tints them, which is
    what keeps a control legible when the panel skin changes."""
    w, h = CONTROL_COLS * CONTROL_CELL, CONTROL_CELL
    img = Image.new("RGBA", (w, h), CLEAR)
    buf = img.load()

    def rect(cellIndex, x0, y0, x1, y1, colour=SOLID):
        ox = cellIndex * CONTROL_CELL
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if 0 <= ox + x < w and 0 <= y < h:
                    buf[ox + x, y] = colour

    def magnifier(cellIndex):
        for a in range(0, 360, 8):
            x = 5 + int(round(3.2 * math.cos(math.radians(a))))
            y = 5 + int(round(3.2 * math.sin(math.radians(a))))
            rect(cellIndex, x, y, x, y)
        rect(cellIndex, 8, 8, 10, 10)

    magnifier(0)                       # zoom in
    rect(0, 3, 5, 7, 5)
    rect(0, 5, 3, 5, 7)
    magnifier(1)                       # zoom out
    rect(1, 3, 5, 7, 5)

    rect(2, 5, 1, 6, 10)               # centre: a crosshair around a dot
    rect(2, 1, 5, 10, 6)
    rect(2, 4, 4, 7, 7, SHADE)

    rect(3, 5, 1, 6, 3)                # waypoint: a pin
    rect(3, 4, 2, 7, 6)
    rect(3, 5, 7, 6, 10)

    for i, y in enumerate((2, 5, 8)):  # legend: keyed rows
        rect(4, 1, y, 2, y + 1)
        rect(4, 4, y, 10, y + 1, SHADE if i else SOLID)

    for i in range(8):                 # delete: an X
        rect(5, 2 + i, 2 + i, 3 + i, 3 + i)
        rect(5, 9 - i, 2 + i, 10 - i, 3 + i)

    rect(6, 1, 5, 10, 6)               # hide: a closed eye
    rect(6, 3, 7, 4, 8, SHADE)
    rect(6, 7, 7, 8, 8, SHADE)

    for a in range(0, 360, 10):        # show: an open eye
        x = 5 + int(round(4.5 * math.cos(math.radians(a))))
        y = 5 + int(round(2.6 * math.sin(math.radians(a))))
        rect(7, x, y, x, y)
    rect(7, 4, 4, 6, 6, SHADE)
    return img


ASSETS = {
    "tiles.png": build_tiles,
    "markers.png": build_markers,
    "player_mark.png": build_player_mark,
    "tracked_mark.png": build_tracked_mark,
    "footprint.png": build_footprint,
    "compass.png": build_compass,
    "parchment.png": build_parchment,
    "creases.png": build_creases,
    "controls.png": build_controls,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped = [], []
    for name, fn in ASSETS.items():
        if only and name not in only and name.removesuffix(".png") not in only:
            continue
        path = os.path.join(OUT, name)
        if not args.force and not is_regenerable(path, MARKER):
            skipped.append(name)
            continue
        save(fn(), path, MARKER)
        written.append(name)

    print(f"wrote {len(written)} map textures: {', '.join(written)}")
    if skipped:
        print(f"skipped {len(skipped)} hand-authored: {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
