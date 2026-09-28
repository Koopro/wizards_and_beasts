#!/usr/bin/env python3
"""Parchment-and-ink GUI chrome for Wizards & Beasts.

Replaces the tooled-leather look of `gui_chrome.py`. Every screen becomes a sheet of aged
parchment with a deckled, lightly scorched edge, a double iron-gall ink rule, gilt furniture
and a wax seal. The eight `WizardsPalette.GuiSkin` materials survive as *variants of the
same paper* -- each changes stock, ink, accent and wax, never the construction -- so the
mod reads as one book with different chapters rather than eight mods.

Why the panels grew from 32x32 to 64x64: paper grain is the whole material, and the old
slicer stretched the centre. A 16px centre stretched to 200px turns grain into blurry
blocks, which is exactly why the leather panels had to be flat gradients. These panels are
meant for a slicer that *tiles* centre and edges, and the grain is generated periodic in 48
(the inner span) so the tile has no seam.

Run from the repo root:
    python tools/gui_parchment.py                      write the kit into textures/gui
    python tools/gui_parchment.py --mockup out.png     style sheet only, writes nothing else
"""

import argparse
import json
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar_texture, hx, is_regenerable, marker, save  # noqa: E402


# Colours here are RGB 3-tuples; alpha is appended where a pixel is written. The shared
# helpers in artgen_common work in RGBA, which does not compose with `+ (255,)`.
def rgb(s):
    return hx(s)[:3]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3))


MARKER = marker("gui_parchment.py")

PANEL = 64      # panel / inset sprite edge
BORDER = 8      # nine-slice border, unchanged from the leather kit
INNER = PANEL - 2 * BORDER   # 48: grain period, so the tiled centre is seamless
BTN = 32        # button cell, same 32/8 cut as before

# --------------------------------------------------------------------------- palette
#
# The default material. Named by role; a skin overrides any of them.
DEFAULT = dict(
    # Paper, dark to light. `paper` is the face; the ramp either side is what grain and
    # edges are drawn from. Warm and slightly green-yellow, like real aged sheepskin --
    # an orange parchment reads as a pizza box at GUI scale.
    paper_dd=rgb("#B99B63"),
    paper_d=rgb("#D5BE8C"),
    paper=rgb("#E4D2A6"),
    paper_l=rgb("#EEE1BC"),
    paper_ll=rgb("#F6ECD0"),
    # The scorched deckle and the stain that creeps in from it.
    burnt=rgb("#6E4E2C"),
    stain=rgb("#B48E57"),
    # Iron-gall ink: brown-black when laid thick, sepia when thin. Never pure black.
    ink=rgb("#2E1F16"),
    ink_2=rgb("#5C4330"),     # secondary text, the thin rule
    ink_3=rgb("#8A6E52"),     # disabled / faint
    # Gilt furniture.
    gilt_d=rgb("#8F6522"),
    gilt=rgb("#C9973A"),
    gilt_l=rgb("#EBC874"),
    gilt_ll=rgb("#FFF0BE"),
    # Sealing wax.
    wax_d=rgb("#4E1010"),
    wax=rgb("#8E2320"),
    wax_l=rgb("#BF4A36"),
    # Ruling on the page stock itself (ledger lines); None = plain sheet.
    ruling=None,
    grain=1.0,               # grain strength multiplier
    seal="crest",
)

# Button tones: (field tint, outline ink). The field is the paper pulled toward the tint,
# so a coloured button is still paper, just paper that has been washed.
TONES = {
    "": (None, None),
    "_confirm": (rgb("#8FA36A"), rgb("#2F4A22")),
    "_danger": (rgb("#C0685A"), rgb("#5E1714")),
    "_select": (rgb("#7F8FB8"), rgb("#1F2A55")),
    "_accent": (rgb("#E0B55A"), rgb("#6E4A12")),
}

# The eight materials. Same keys as `gui_chrome.py`'s SKINS, same enum on the Java side.
SKINS = {
    "field_notebook": dict(  # kraft notebook, graphite pencil, leather strap
        paper_dd=rgb("#9C8B66"), paper_d=rgb("#BCAA82"), paper=rgb("#CDBC95"),
        paper_l=rgb("#D9CAA6"), paper_ll=rgb("#E4D8B8"), burnt=rgb("#4F4634"),
        ink=rgb("#2B2B2B"), ink_2=rgb("#555350"), ink_3=rgb("#85807A"),
        wax=rgb("#6B4630"), wax_d=rgb("#3E2616"), wax_l=rgb("#95684A"), seal="strap"),
    "ministry": dict(  # cream memo stock, the emblem's purple, gold
        paper_dd=rgb("#C4B68F"), paper_d=rgb("#DDD2B2"), paper=rgb("#EAE1C6"),
        paper_l=rgb("#F2EBD6"), paper_ll=rgb("#F9F4E6"), burnt=rgb("#5A4760"),
        ink=rgb("#2A1433"), ink_2=rgb("#4E3558"), ink_3=rgb("#8A7890"),
        wax=rgb("#3E1F47"), wax_d=rgb("#221328"), wax_l=rgb("#6A4474"), seal="stamp"),
    "star_chart": dict(  # an engraved celestial atlas: blue-grey vellum, indigo, silver
        paper_dd=rgb("#9CA2A6"), paper_d=rgb("#BEC3C3"), paper=rgb("#D2D5D0"),
        paper_l=rgb("#DFE1DB"), paper_ll=rgb("#ECEDE7"), burnt=rgb("#2E3450"),
        ink=rgb("#18204A"), ink_2=rgb("#3A4677"), ink_3=rgb("#7C85A6"),
        gilt_d=rgb("#6F7A8C"), gilt=rgb("#A4AEBE"), gilt_l=rgb("#D3DAE4"), gilt_ll=rgb("#F4F7FB"),
        wax=rgb("#232A52"), wax_d=rgb("#12162E"), wax_l=rgb("#46508A"), seal="star"),
    "goblin_ledger": dict(  # ruled ledger paper, oxblood, gold
        ink=rgb("#2A1512"), ink_2=rgb("#5A2A22"), ink_3=rgb("#8E6A60"),
        ruling=rgb("#9FB2C4"), wax=rgb("#6A1E1A"), wax_d=rgb("#3A0E0C"), wax_l=rgb("#9A3A2E"),
        gilt=rgb("#D4AF37"), gilt_l=rgb("#F0D77A"), seal="wax"),
    "workbench": dict(  # Ollivander's pattern paper: pale, shaving-coloured, brass
        paper_dd=rgb("#B7A077"), paper_d=rgb("#D2BE95"), paper=rgb("#E0CFA9"),
        ink=rgb("#2E2012"), ink_2=rgb("#5E4526"), wax=rgb("#7A4A22"), wax_d=rgb("#46280E"),
        wax_l=rgb("#A86E3A"), seal="brand"),
    "marauders_map": dict(  # old, dirty, much-folded, drawn by hand
        paper_dd=rgb("#A98A56"), paper_d=rgb("#C9AE78"), paper=rgb("#DAC291"),
        paper_l=rgb("#E5D2A6"), paper_ll=rgb("#EFE0BC"), burnt=rgb("#5A3A1E"),
        stain=rgb("#A57E44"), ink=rgb("#3A2414"), ink_2=rgb("#6A4A2E"), grain=1.6,
        seal="quill"),
    "pensieve": dict(  # silvered vellum, deep aubergine ink, the light of a memory
        paper_dd=rgb("#A6A2B0"), paper_d=rgb("#C6C3CE"), paper=rgb("#D8D6DE"),
        paper_l=rgb("#E4E3EA"), paper_ll=rgb("#F0F0F5"), burnt=rgb("#3A2F4E"),
        stain=rgb("#A49CB8"), ink=rgb("#261C36"), ink_2=rgb("#4A3F5E"), ink_3=rgb("#8A87A8"),
        gilt_d=rgb("#6E7894"), gilt=rgb("#A8B6D6"), gilt_l=rgb("#D4DEF2"), gilt_ll=rgb("#F4F8FF"),
        wax=rgb("#4A3F5E"), wax_d=rgb("#261C36"), wax_l=rgb("#7A6E96"), seal="ripple"),
    "hearth": dict(  # soot-stained sheet by the grate, green fire
        paper_dd=rgb("#8E8068"), paper_d=rgb("#B0A286"), paper=rgb("#C6B99C"),
        paper_l=rgb("#D3C8AE"), paper_ll=rgb("#DFD6C0"), burnt=rgb("#1E1A18"),
        stain=rgb("#6A5E4C"), ink=rgb("#1E1A18"), ink_2=rgb("#3E3830"), ink_3=rgb("#7A7266"),
        gilt_d=rgb("#146E2A"), gilt=rgb("#21B342"), gilt_l=rgb("#6FE08A"), gilt_ll=rgb("#C8FFD4"),
        wax=rgb("#2A2422"), wax_d=rgb("#141210"), wax_l=rgb("#4E4640"), grain=1.4, seal="grate"),
}


def skin(name=None):
    s = dict(DEFAULT)
    if name:
        s.update(SKINS[name])
    return s


# --------------------------------------------------------------------------- noise


def periodic_noise(w, h, period, cell, seed):
    """Value noise that wraps every `period` pixels in both axes. `cell` must divide it."""
    rng = random.Random(seed)
    n = period // cell
    grid = [[rng.random() for _ in range(n)] for _ in range(n)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        gy, fy = divmod(y / cell, 1)
        gy = int(gy)
        ty = smooth(fy)
        for x in range(w):
            gx, fx = divmod(x / cell, 1)
            gx = int(gx)
            tx = smooth(fx)
            a = grid[gy % n][gx % n]
            b = grid[gy % n][(gx + 1) % n]
            c = grid[(gy + 1) % n][gx % n]
            d = grid[(gy + 1) % n][(gx + 1) % n]
            top = a + (b - a) * tx
            bot = c + (d - c) * tx
            out[y][x] = top + (bot - top) * ty
    return out


def grain_field(w, h, seed, strength=1.0):
    """Paper grain in [-1, 1]-ish, periodic in INNER. Cloudy mottling plus horizontal fibres
    plus the odd foxing speck. Everything wraps, so a tile of it has no seam."""
    cloud = periodic_noise(w, h, INNER, 24, seed)
    mid = periodic_noise(w, h, INNER, 12, seed + 1)
    fine = periodic_noise(w, h, INNER, 3, seed + 2)
    field = [[(cloud[y][x] - 0.5) * 0.6 + (mid[y][x] - 0.5) * 0.45 + (fine[y][x] - 0.5) * 0.5
              for x in range(w)] for y in range(h)]
    rng = random.Random(seed + 3)
    # Fibres: short horizontal streaks, the grain direction of a real skin.
    for _ in range(int(22 * strength)):
        fx, fy = rng.randrange(INNER), rng.randrange(INNER)
        length = rng.randint(3, 7)
        v = rng.choice((-0.55, 0.45))
        for i in range(length):
            for yy in range(fy, h, INNER):
                for xx in range((fx + i) % INNER, w, INNER):
                    field[yy][xx] += v
    # Foxing: rare darker specks.
    for _ in range(int(5 * strength)):
        fx, fy = rng.randrange(INNER), rng.randrange(INNER)
        for yy in range(fy, h, INNER):
            for xx in range(fx, w, INNER):
                field[yy][xx] -= 1.3
    return [[v * strength for v in row] for row in field]


def paper_colour(s, v):
    """Grain value to one of five paper tones. Posterised, so the sheet is pixel art rather
    than a photograph, and the steps are small enough that ink stays readable over them."""
    if v < -0.95:
        return s["paper_d"]
    if v < -0.32:
        return mix(s["paper"], s["paper_d"], 0.45)
    if v < 0.34:
        return s["paper"]
    if v < 0.8:
        return mix(s["paper"], s["paper_l"], 0.55)
    return s["paper_l"]


def paper_sheet(s, w, h, seed, darken=1.0):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    # Offset so the sprite's inner region (BORDER..) starts at grain phase 0.
    field = grain_field(w + INNER, h + INNER, seed, s["grain"])
    for y in range(h):
        for x in range(w):
            v = field[(y - BORDER) % INNER][(x - BORDER) % INNER]
            c = paper_colour(s, v)
            if darken != 1.0:
                c = shade(c, darken)
            px[x, y] = c + (255,)
    if s["ruling"] is not None:
        # Ledger ruling, periodic in INNER: four faint lines per tile, one red margin-ish
        # line would not tile, so only the blue rules.
        for y in range(h):
            if (y - BORDER) % 12 == 11:
                for x in range(w):
                    r = px[x, y]
                    px[x, y] = mix(r[:3], s["ruling"], 0.45) + (255,)
    return img


# --------------------------------------------------------------------------- kit


def periodic_mask(length, seed, density):
    rng = random.Random(seed)
    return [rng.random() < density for _ in range(length)]


def panel(s, seed=11, slip=False):
    """The parchment sheet, 64x64 on an 8px border.

    Border, outside in: deckle (ragged, scorched) / burn / curl shade / paper / ink rule /
    paper / thin ink rule / paper. The two rules are the book-page border every printed
    grimoire has, and they sit wholly inside the border so they never stretch or tile.

    `slip=True` is the same torn paper with one thin rule and no studs: a scrap for cards
    too short for a page border, such as a 32px toast, whose second line of text would
    otherwise run across the bottom rules.
    """
    img = paper_sheet(s, PANEL, PANEL, seed)
    px = img.load()
    P = PANEL
    # One torn profile and one scorch profile per edge, periodic in INNER so a tiled edge
    # strip has no seam. Depth runs 0..2 in *runs*: per-pixel randomness reads as a
    # perforated card, not as torn skin.
    edges = []
    for i in range(4):
        tear = periodic_noise(INNER, 1, INNER, 8, seed + 31 * i)[0]
        jag = periodic_noise(INNER, 1, INNER, 2, seed + 31 * i + 5)[0]
        burn = periodic_noise(INNER, 1, INNER, 12, seed + 31 * i + 9)[0]
        depth = [max(0, min(2, int(round(t * 2.8 + (j - 0.5) * 0.9 - 0.45))))
                 for t, j in zip(tear, jag)]
        edges.append((depth, burn))

    for y in range(P):
        for x in range(P):
            # distance to, and position along, each edge: top, bottom, left, right
            spans = ((y, x), (P - 1 - y, x), (x, y), (P - 1 - x, y))
            best, heat = 99, 0.0
            for (e, pos), (depth, burn) in zip(spans, edges):
                if e > 5:
                    continue
                along = (pos - BORDER) % INNER
                r = e - depth[along]
                if r < best:
                    best, heat = r, burn[along]
            if best > 3:
                continue
            c = px[x, y][:3]
            if best < 0:
                px[x, y] = (0, 0, 0, 0)
            elif best == 0:
                px[x, y] = mix(s["burnt"], c, 0.35 - 0.3 * heat) + (255,)
            elif best == 1:
                px[x, y] = mix(c, s["burnt"], 0.25 + 0.4 * heat) + (255,)
            elif best == 2:
                px[x, y] = mix(c, s["stain"], 0.3 + 0.3 * heat) + (255,)
            else:
                px[x, y] = mix(c, s["stain"], 0.14) + (255,)
    # Round the outer corners: a sheet, not a board.
    for cx, cy in ((0, 0), (P - 1, 0), (0, P - 1), (P - 1, P - 1)):
        sx = 1 if cx == 0 else -1
        sy = 1 if cy == 0 else -1
        for dx, dy in ((0, 0), (1, 0), (0, 1), (2, 0), (0, 2)):
            px[cx + sx * dx, cy + sy * dy] = (0, 0, 0, 0)
    d = ImageDraw.Draw(img)
    if slip:
        d.rectangle([4, 4, P - 5, P - 5], outline=s["ink_3"])
        return img
    # Double rule.
    d.rectangle([4, 4, P - 5, P - 5], outline=s["ink"])
    d.rectangle([6, 6, P - 7, P - 7], outline=s["ink_2"])
    # Corner bosses: a 3x3 gilt stud where the rules meet, lit from the top-left no matter
    # which corner it sits in -- the light is global, not mirrored.
    boss = ((s["gilt_l"], s["gilt_l"], s["gilt"]),
            (s["gilt_l"], s["gilt_ll"], s["gilt_d"]),
            (s["gilt"], s["gilt_d"], s["gilt_d"]))
    for ox, oy in ((3, 3), (P - 6, 3), (3, P - 6), (P - 6, P - 6)):
        for j in range(3):
            for i in range(3):
                px[ox + i, oy + j] = boss[j][i] + (255,)
    return img


def inset(s, seed=23):
    """A recess cut into the sheet: lists, viewports, slots. Darker stock, a thin engraved
    ink line, shadow along the top-left (light is top-left everywhere) and a lit lip on the
    bottom-right. Only the outer 3px carry the edge; the rest of the border is plain stock
    so a content inset of 8 still lands on paper."""
    img = paper_sheet(s, PANEL, PANEL, seed, darken=0.93)
    d = ImageDraw.Draw(img)
    P = PANEL
    d.rectangle([0, 0, P - 1, P - 1], outline=s["ink_2"])
    shadow = mix(s["paper_dd"], s["ink_2"], 0.2)
    d.line([(1, 1), (P - 2, 1)], fill=shadow)
    d.line([(1, 1), (1, P - 2)], fill=shadow)
    d.line([(2, 2), (P - 3, 2)], fill=mix(s["paper_d"], s["paper_dd"], 0.5))
    d.line([(2, 2), (2, P - 3)], fill=mix(s["paper_d"], s["paper_dd"], 0.5))
    d.line([(1, P - 2), (P - 2, P - 2)], fill=s["paper_ll"])
    d.line([(P - 2, 1), (P - 2, P - 2)], fill=s["paper_ll"])
    return img


def button(s, tone="", state="", size=BTN):
    """A paper tab, 32x32 on an 8px border (or `size` square -- the character sheet's
    tabs are 24/3). Rows are uniform across x so the stretched centre stays exact; all
    shading lives in the outer three rows and columns, inside even a 3px border."""
    tint, line = TONES[tone]
    field = s["paper_l"]
    outline = s["ink"]
    if tint is not None:
        field = mix(field, tint, 0.42)
        outline = line
    if state == "_hover":
        field = mix(field, s["paper_ll"], 0.55)
        outline = s["gilt_d"]
    elif state == "_off":
        grey = sum(field) // 3
        field = mix(field, (grey, grey, grey), 0.55)
        field = mix(field, s["paper_d"], 0.3)
        outline = s["ink_3"]
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    B = size
    d.rectangle([0, 0, B - 1, B - 1], fill=field)
    hi = mix(field, (255, 255, 255), 0.45)
    lo = shade(field, 0.78)
    lo2 = shade(field, 0.88)
    if state == "_off":
        hi, lo, lo2 = mix(field, (255, 255, 255), 0.15), shade(field, 0.9), field
    d.line([(1, 1), (B - 2, 1)], fill=hi)
    d.line([(1, 1), (1, B - 3)], fill=hi)
    d.line([(1, B - 2), (B - 2, B - 2)], fill=lo)
    d.line([(1, B - 3), (B - 2, B - 3)], fill=lo2)
    d.line([(B - 2, 2), (B - 2, B - 2)], fill=lo)
    d.rectangle([0, 0, B - 1, B - 1], outline=outline)
    if state == "_hover":
        d.rectangle([1, 1, B - 2, B - 2], outline=s["gilt_l"])
        d.line([(1, B - 2), (B - 2, B - 2)], fill=s["gilt"])
        d.line([(B - 2, 1), (B - 2, B - 2)], fill=s["gilt"])
    # Rounded: knock the four corner pixels out.
    for c in ((0, 0), (B - 1, 0), (0, B - 1), (B - 1, B - 1)):
        img.putpixel(c, (0, 0, 0, 0))
    return img


def divider(s):
    """32x8, stretched in x: a thick-and-thin rule, the typographer's divider."""
    img = Image.new("RGBA", (32, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(0, 3), (31, 3)], fill=s["ink"])
    d.line([(0, 5), (31, 5)], fill=s["ink_2"] + (170,))
    return img


def scroll_track(s):
    """8x32, stretched in y: a groove pressed into the sheet."""
    img = Image.new("RGBA", (8, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cols = ((1, s["ink_2"]), (2, s["paper_dd"]), (3, s["paper_d"]), (4, s["paper_d"]),
            (5, mix(s["paper_d"], s["paper"], 0.5)), (6, s["paper_ll"]))
    for x, c in cols:
        d.line([(x, 0), (x, 31)], fill=c)
    return img


def scroll_thumb(s):
    """8x32, stretched in y: a gilt-edged ribbon marker, the one in every grimoire."""
    img = Image.new("RGBA", (8, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cols = ((1, s["wax_d"]), (2, s["wax_l"]), (3, s["wax"]), (4, s["wax"]),
            (5, shade(s["wax"], 0.8)), (6, s["wax_d"]))
    for x, c in cols:
        d.line([(x, 0), (x, 31)], fill=c)
    return img


# Seed of each seal's wobble. This was `hash(kind) & 0xFFFF`, and Python salts `str` hashes per
# process, so every run drew different seals. These are the seeds the approved 2026-09-23 seals
# were drawn with (recovered by search against the shipped PNGs, 2026-09-28; each is the only
# match in 0..0xFFFF). "strap" draws no wobble.
SEAL_SEED = {"stamp": 4875, "wax": 31425, "quill": 12944, "star": 32724, "brand": 63303,
             "grate": 60349, "ripple": 28646}


def seal(s):
    """The wax seal, 16x16 at native size. Drawn 4x and reduced, then snapped back onto the
    wax ramp so it is still pixel art. The impression differs per material."""
    SS = 4
    big = Image.new("RGBA", (16 * SS, 16 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    kind = s["seal"]
    rng = random.Random(SEAL_SEED.get(kind, 0))
    if kind == "strap":
        d.rounded_rectangle([2 * SS, 3 * SS, 14 * SS, 12 * SS], 2 * SS, fill=s["wax"])
    else:
        # A slightly irregular blob: wax spreads, it is not a circle.
        pts = []
        import math
        for i in range(24):
            a = i / 24 * math.tau
            r = (6.6 + rng.uniform(-0.7, 0.5)) * SS
            pts.append((8 * SS + math.cos(a) * r, 8 * SS + math.sin(a) * r))
        d.polygon(pts, fill=s["wax"])
    small = big.resize((16, 16), Image.LANCZOS)
    px = small.load()
    for y in range(16):
        for x in range(16):
            a = px[x, y][3]
            px[x, y] = (s["wax"] + (255,)) if a > 110 else (0, 0, 0, 0)
    # Rim lighting on the blob: top-left lit, bottom-right shadow.
    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] > 0
    base = small.copy()
    bp = base.load()
    for y in range(16):
        for x in range(16):
            if not bp[x, y][3]:
                continue
            if not solid(x - 1, y) or not solid(x, y - 1):
                px[x, y] = s["wax_l"] + (255,)
            if not solid(x + 1, y) or not solid(x, y + 1):
                px[x, y] = s["wax_d"] + (255,)
    d = ImageDraw.Draw(small)
    deep, lit = s["wax_d"], s["wax_l"]
    g = s["gilt"]
    if kind == "crest":
        # A raised ring with a W struck in it.
        d.ellipse([4, 4, 11, 11], outline=deep)
        d.arc([4, 4, 11, 11], 180, 270, fill=lit)
        for x, y in ((6, 7), (6, 8), (7, 9), (8, 8), (9, 9), (10, 8), (10, 7)):
            d.point((x - 0, y - 0), fill=deep)
    elif kind == "stamp":
        d.ellipse([4, 4, 11, 11], outline=g)
        d.line([(6, 8), (9, 8)], fill=g)
    elif kind == "star":
        for x, y in ((8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 11),
                     (5, 8), (6, 8), (7, 8), (9, 8), (10, 8), (11, 8),
                     (6, 6), (10, 6), (6, 10), (10, 10)):
            d.point((x, y), fill=g)
        d.point((8, 8), fill=s["gilt_ll"])
    elif kind == "wax":
        d.ellipse([4, 4, 11, 11], outline=g)
        d.rectangle([7, 6, 8, 10], fill=g)
        d.line([(6, 7), (9, 7)], fill=s["gilt_l"])
    elif kind == "strap":
        d.rectangle([4, 5, 11, 9], outline=mix(s["wax_l"], s["paper"], 0.4))
        for x in range(4, 12, 2):
            d.point((x, 4), fill=s["paper_d"])
            d.point((x, 10), fill=s["paper_d"])
    elif kind == "brand":
        d.ellipse([4, 4, 11, 11], outline=deep)
        d.ellipse([6, 6, 9, 9], fill=deep)
    elif kind == "quill":
        d.polygon([(8, 3), (10, 7), (9, 12), (7, 12), (6, 7)], fill=s["gilt"],
                  outline=s["gilt_d"])
        d.line([(8, 5), (8, 11)], fill=s["gilt_d"])
    elif kind == "ripple":
        d.ellipse([3, 5, 12, 10], outline=s["gilt_l"])
        d.ellipse([6, 7, 9, 8], outline=s["gilt_ll"])
    elif kind == "grate":
        d.rectangle([4, 5, 11, 11], fill=s["gilt"])
        for x in (6, 9):
            d.line([(x, 5), (x, 11)], fill=deep)
        d.point((5, 6), fill=s["gilt_ll"])
    return small


# --------------------------------------------------------------------------- mockup


class Font:
    """The vanilla ASCII bitmap font, so mock text is the text the game draws."""

    def __init__(self):
        self.sheet = client_jar_texture("textures/font/ascii.png")
        self.widths = {}
        px = self.sheet.load()
        for code in range(256):
            gx, gy = (code % 16) * 8, (code // 16) * 8
            w = 0
            for x in range(8):
                if any(px[gx + x, gy + y][3] for y in range(8)):
                    w = x + 1
            self.widths[code] = w

    def width(self, text):
        return sum((4 if ch == " " else self.widths.get(ord(ch), 5) + 1) for ch in text)

    def draw(self, img, text, x, y, colour, shadow=None):
        if shadow is not None:
            self.draw(img, text, x + 1, y + 1, shadow)
        for ch in text:
            code = ord(ch)
            if ch == " ":
                x += 4
                continue
            gx, gy = (code % 16) * 8, (code // 16) * 8
            glyph = self.sheet.crop((gx, gy, gx + 8, gy + 8))
            solid = Image.new("RGBA", (8, 8), colour + (255,))
            img.paste(solid, (x, y), glyph)
            x += self.widths[code] + 1


def slice_tiled(dst, src, x, y, w, h, b, u=0, v=0, ts=None):
    """What the Java side will do: corners fixed, edges and centre *tiled*."""
    ts = ts or src.width
    inner = ts - 2 * b
    cell = src.crop((u, v, u + ts, v + ts))

    def tile(region, dx, dy, dw, dh):
        for yy in range(0, dh, region.height):
            for xx in range(0, dw, region.width):
                piece = region.crop((0, 0, min(region.width, dw - xx), min(region.height, dh - yy)))
                dst.alpha_composite(piece, (dx + xx, dy + yy))

    c = lambda l, t, r, bt: cell.crop((l, t, r, bt))
    dst.alpha_composite(c(0, 0, b, b), (x, y))
    dst.alpha_composite(c(ts - b, 0, ts, b), (x + w - b, y))
    dst.alpha_composite(c(0, ts - b, b, ts), (x, y + h - b))
    dst.alpha_composite(c(ts - b, ts - b, ts, ts), (x + w - b, y + h - b))
    tile(c(b, 0, ts - b, b), x + b, y, w - 2 * b, b)
    tile(c(b, ts - b, ts - b, ts), x + b, y + h - b, w - 2 * b, b)
    tile(c(0, b, b, ts - b), x, y + b, b, h - 2 * b)
    tile(c(ts - b, b, ts, ts - b), x + w - b, y + b, b, h - 2 * b)
    tile(c(b, b, ts - b, ts - b), x + b, y + b, w - 2 * b, h - 2 * b)


def slice_stretched(dst, src, x, y, w, h, b):
    """Buttons stay stretched: their rows are uniform, so a stretch is exact."""
    ts = src.width
    parts = [(0, b), (b, ts - b), (ts - b, ts)]
    dxs = [(x, b), (x + b, w - 2 * b), (x + w - b, b)]
    dys = [(y, b), (y + b, h - 2 * b), (y + h - b, b)]
    for (sy0, sy1), (dy, dh) in zip(parts, dys):
        for (sx0, sx1), (dx, dw) in zip(parts, dxs):
            if dw <= 0 or dh <= 0:
                continue
            piece = src.crop((sx0, sy0, sx1, sy1)).resize((dw, dh), Image.NEAREST)
            dst.alpha_composite(piece, (dx, dy))


def stretch_bar(dst, src, x, y, w, h):
    dst.alpha_composite(src.resize((w, h), Image.NEAREST), (x, y))


def mock_screen(font, s, w=248, h=176, title="Character Sheet"):
    """A plausible screen in GUI pixels: title, inset viewport, list, scrollbar, buttons."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    slice_tiled(img, panel(s), 0, 0, w, h, BORDER)
    tw = font.width(title)
    font.draw(img, title, (w - tw) // 2, 12, s["ink"])
    stretch_bar(img, divider(s), 12, 21, w - 24, 8)
    # viewport
    slice_tiled(img, inset(s), 12, 32, 80, 104, BORDER)
    figure = [(0, 0, 8, 8), (-2, 8, 12, 20), (-6, 8, -2, 20), (10, 8, 14, 20), (0, 20, 4, 32), (4, 20, 8, 32)]
    fx, fy = 48, 60
    d = ImageDraw.Draw(img)
    for l, t, r, bt in figure:
        d.rectangle([fx + l, fy + t, fx + r - 1, fy + bt - 1], fill=mix(s["paper_d"], s["ink_3"], 0.35))
    font.draw(img, "Pure-blood", 20, 124, s["ink_2"])
    # stats list
    rows = [("Willpower", "12"), ("Knowledge", "7"), ("Spell Power", "18"),
            ("Resistance", "4"), ("Wand Bond", "63%")]
    ly = 36
    slice_tiled(img, inset(s), 100, 32, 136, 104, BORDER)
    for i, (k, v) in enumerate(rows):
        yy = ly + 4 + i * 16
        if i == 2:
            d.rectangle([103, yy - 3, 225, yy + 10], fill=mix(s["paper_d"], s["gilt_l"], 0.45))
        font.draw(img, k, 106, yy, s["ink"])
        font.draw(img, v, 220 - font.width(v), yy, s["ink"])
        if i < len(rows) - 1:
            for xx in range(106, 221, 2):
                img.putpixel((xx, yy + 12), mix(s["paper_d"], s["ink_3"], 0.5) + (255,))
    font.draw(img, "+2 from your wand", 106, ly + 84, s["ink_2"])
    stretch_bar(img, scroll_track(s), 226, 34, 8, 100)
    stretch_bar(img, scroll_thumb(s), 226, 40, 8, 34)
    # buttons: neutral, hovered, confirm, danger, disabled
    specs = [("Close", "", ""), ("Train", "_confirm", ""), ("Reset", "_danger", ""),
             ("Study", "", "_hover"), ("Locked", "", "_off")]
    bx = 12
    for label, tone, state in specs:
        bw = 42
        slice_stretched(img, button(s, tone, state), bx, 144, bw, 20, BORDER)
        colour = s["ink_3"] if state == "_off" else s["ink"]
        font.draw(img, label, bx + (bw - font.width(label)) // 2, 150, colour)
        bx += bw + 3
    img.alpha_composite(seal(s), (w - 22, 3))
    return img


def backdrop(w, h):
    """Stand-in for the darkened world behind a screen."""
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=mix(rgb("#2A2F3A"), rgb("#14161C"), t) + (255,))
    return img


def mockup(path):
    font = Font()
    S = 3   # GUI scale shown
    W, H = 1500, 1140
    sheet = backdrop(W, H)
    d = ImageDraw.Draw(sheet)

    def label(text, x, y, colour=rgb("#E8E2D0")):
        t = Image.new("RGBA", (font.width(text) + 2, 10), (0, 0, 0, 0))
        font.draw(t, text, 0, 0, colour, shadow=rgb("#000000"))
        sheet.alpha_composite(t.resize((t.width * 2, t.height * 2), Image.NEAREST), (x, y))

    def put(img, x, y, scale=S):
        sheet.alpha_composite(img.resize((img.width * scale, img.height * scale), Image.NEAREST), (x, y))

    s = skin()
    label("PARCHMENT + INK  -  default material, GUI scale 3", 24, 18)
    put(mock_screen(font, s), 24, 50)

    # Kit pieces at 4x
    x0 = 24 + 248 * S + 40
    label("KIT  (4x, native sprites)", x0, 18)
    put(panel(s), x0, 50, 4)
    put(inset(s), x0 + 64 * 4 + 16, 50, 4)
    label("panel 64/8", x0, 50 + 256 + 4)
    label("inset 64/8", x0 + 64 * 4 + 16, 50 + 256 + 4)
    y = 50 + 256 + 34
    for i, tone in enumerate(TONES):
        for j, st in enumerate(("", "_hover", "_off")):
            put(button(s, tone, st), x0 + i * 72, y + j * 72, 2)
    label("buttons: neutral confirm danger select accent / hover / off", x0, y + 3 * 72)
    y2 = y + 3 * 72 + 30
    put(divider(s), x0, y2, 4)
    put(scroll_track(s), x0 + 150, y2, 4)
    put(scroll_thumb(s), x0 + 190, y2, 4)
    put(seal(s), x0 + 240, y2, 6)

    # Palette swatches
    py = 50 + 176 * S + 24
    label("PALETTE", 24, py)
    keys = ["paper_dd", "paper_d", "paper", "paper_l", "paper_ll", "burnt", "stain",
            "ink", "ink_2", "ink_3", "gilt_d", "gilt", "gilt_l", "gilt_ll", "wax_d", "wax", "wax_l"]
    for i, k in enumerate(keys):
        d.rectangle([24 + i * 42, py + 26, 24 + i * 42 + 38, py + 60], fill=s[k])

    # The eight skins as small screens
    sy = py + 84
    label("SKINS  -  same sheet, different stock / ink / wax  (GUI scale 2)", 24, sy)
    names = list(SKINS)
    cw, ch = 176, 96
    for i, name in enumerate(names):
        sk = skin(name)
        tile = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        slice_tiled(tile, panel(sk, seed=11 + i), 0, 0, cw, ch, BORDER)
        title = name.replace("_", " ").title()
        font.draw(tile, title, 12, 12, sk["ink"])
        stretch_bar(tile, divider(sk), 12, 21, cw - 40, 8)
        font.draw(tile, "Body text in ink", 12, 32, sk["ink"])
        font.draw(tile, "secondary note", 12, 43, sk["ink_2"])
        slice_stretched(tile, button(sk, "", ""), 12, 64, 50, 20, BORDER)
        font.draw(tile, "Open", 12 + (50 - font.width("Open")) // 2, 70, sk["ink"])
        slice_stretched(tile, button(sk, "", "_hover"), 66, 64, 50, 20, BORDER)
        font.draw(tile, "Hover", 66 + (50 - font.width("Hover")) // 2, 70, sk["ink"])
        slice_tiled(tile, inset(sk), 124, 36, 40, 48, BORDER)
        tile.alpha_composite(seal(sk), (cw - 22, 4))
        col, row = i % 4, i // 4
        put(tile, 24 + col * (cw * 2 + 14), sy + 30 + row * (ch * 2 + 14), 2)
    sheet.save(path)
    print("wrote", path)


# --------------------------------------------------------------------------- controls atlas
#
# Layout mirrored in `McStylePanel` (ATLAS_*). Both spell the numbers out.
#   cols = ButtonTone, rows = ControlState, 32px cells
#   icon band at y=96: randomise 12x12 at (0,96) | arrows right at x=16,28,40 | left at 52,64,76
ATLAS_W, ATLAS_H = 160, 112
ATLAS_ICON_Y = 96
ATLAS_TONES = ["", "_confirm", "_danger", "_select", "_accent"]
ATLAS_STATES = ["", "_hover", "_off"]


def arrow(s, state, right=True):
    """9x13 cycler arrow: a gilt wedge outlined in ink, lit on its upper face."""
    img = Image.new("RGBA", (9, 13), (0, 0, 0, 0))
    px = img.load()
    body, lit, edge = s["gilt"], s["gilt_l"], s["ink"]
    if state == "_hover":
        body, lit, edge = s["gilt_l"], s["gilt_ll"], s["gilt_d"]
    elif state == "_off":
        body, lit, edge = s["paper_d"], s["paper_l"], s["ink_3"]
    solid = set()
    for y in range(13):
        half = 6 - abs(y - 6)
        for x in range(1, 2 + half):
            solid.add((x, y))
    for (x, y) in solid:
        rim = any((x + dx, y + dy) not in solid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c = edge if rim else (lit if y < 6 else body)
        px[x if right else 8 - x, y] = c + (255,)
    return img


def randomise_icon(s):
    """12x12: two crossing arrows, the shuffle glyph players already know."""
    # Two-pixel strokes: at one pixel the crossing diagonals and the heads merge into noise.
    # The strand that finishes on top is drawn last, in ink; the one it crosses is gilt.
    img = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ink, g = s["ink"], s["gilt_d"]

    def strand(y0, y1, c):
        d.line([(0, y0), (2, y0), (6, y1), (8, y1)], fill=c, width=2)
        d.polygon([(8, y1 - 3), (11, y1), (8, y1 + 3)], fill=c)

    strand(8, 3, g)
    strand(3, 8, ink)
    return img


def controls_atlas(s):
    sheet = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    for col, tone in enumerate(ATLAS_TONES):
        for row, state in enumerate(ATLAS_STATES):
            sheet.alpha_composite(button(s, tone, state), (col * BTN, row * BTN))
    sheet.alpha_composite(randomise_icon(s), (0, ATLAS_ICON_Y))
    for i, state in enumerate(ATLAS_STATES):
        sheet.alpha_composite(arrow(s, state, True), (16 + 12 * i, ATLAS_ICON_Y))
        sheet.alpha_composite(arrow(s, state, False), (52 + 12 * i, ATLAS_ICON_Y))
    return sheet


# --------------------------------------------------------------------------- assets

GUI = "src/main/resources/assets/wizards_and_beasts/textures/gui"
NINE_SLICE = {"panel.png", "panel_inset.png"}


def assets():
    """Path under textures/gui -> zero-argument builder."""
    d = skin()
    out = {
        "theme/panel.png": lambda: panel(d),
        "theme/panel_inset.png": lambda: inset(d),
        "theme/slip.png": lambda: panel(d, seed=37, slip=True),
        "theme/divider.png": lambda: divider(d),
        "theme/scrollbar_track.png": lambda: scroll_track(d),
        "theme/scrollbar_thumb.png": lambda: scroll_thumb(d),
        "theme/controls.png": lambda: controls_atlas(d),
        # The character sheet's tab chips, 24/3 -- see CharacterSheetTextures.
        "character_sheet/panel.png": lambda: button(d, "", "", 24),
        "character_sheet/panel_sel.png": lambda: button(d, "", "_hover", 24),
    }
    for i, name in enumerate(SKINS):
        sk = skin(name)
        per = {
            "panel.png": lambda sk=sk, i=i: panel(sk, seed=11 + i),
            "panel_inset.png": lambda sk=sk, i=i: inset(sk, seed=23 + i),
            "divider.png": lambda sk=sk: divider(sk),
            "scrollbar_track.png": lambda sk=sk: scroll_track(sk),
            "scrollbar_thumb.png": lambda sk=sk: scroll_thumb(sk),
            "seal.png": lambda sk=sk: seal(sk),
            "button.png": lambda sk=sk: button(sk, "", ""),
            "button_hover.png": lambda sk=sk: button(sk, "", "_hover"),
            "button_off.png": lambda sk=sk: button(sk, "", "_off"),
        }
        for element, fn in per.items():
            out[f"sprites/{name}/{element}"] = fn
    return out


def write_mcmeta(png_path):
    """Nine-slice metadata beside each panel, so a `blitSprite` consumer cuts it right too.
    The Java side blits by raw path with its own border; the two agree on 64/8."""
    with open(png_path + ".mcmeta", "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": PANEL,
                                       "height": PANEL, "border": BORDER}}}, fh, indent=2)
        fh.write("\n")


def write(force, only):
    written, skipped = [], []
    for rel, fn in assets().items():
        if only and rel not in only and os.path.basename(rel) not in only:
            continue
        path = os.path.join(GUI, rel)
        if not force and not is_regenerable(path, MARKER):
            skipped.append(rel)
            continue
        save(fn(), path, MARKER)
        written.append(rel)
        if os.path.basename(rel) in NINE_SLICE and rel.startswith("sprites/"):
            write_mcmeta(path)
    print(f"wrote {len(written)} gui textures")
    if skipped:
        print(f"skipped {len(skipped)} owned elsewhere (--force claims them): {', '.join(skipped)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mockup", metavar="PNG", help="render the style sheet and stop")
    ap.add_argument("--force", action="store_true",
                    help="overwrite files another generator owns (the leather kit)")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    if args.mockup:
        mockup(args.mockup)
        return
    write(args.force, {x.strip() for x in args.only.split(",") if x.strip()})


if __name__ == "__main__":
    main()
