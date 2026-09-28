#!/usr/bin/env python3
"""Engraved celestial-atlas texture set for the skill web canvas.

Regenerates every PNG under `assets/wizards_and_beasts/textures/gui/skill_tree/chart/`.
Deterministic (fixed seeds), so a rerun is a no-op unless this file changed.

The chart used to be a night sky: a starfield, drifting nebulae, glowing stars and ley
lines. Since the 2026-09-23 parchment pass it is a page out of a seventeenth-century printed
star atlas instead -- pale blue-grey vellum, indigo engraving, silver leaf for the few things
worth gilding. The vocabulary follows the printed charts: a star's magnitude is its ray
count, the uncatalogued background stars are specks of ink, the Milky Way is stipple, the
graticule is a dotted rule, and the survey ring is a graduated circle.

Two conventions, both load-bearing:

  - **Sprites are authored white or greyscale** and tinted at draw time through the tinted
    `blit` overload. One `star_flare_notable.png` serves solid ink when allocated and a faint
    wider echo behind a maxed node. Pieces with a fixed look carry their own colour and are
    drawn untinted (`0xFFFFFFFF` on the Java side): the page layers (vellum, stipple, specks,
    edge toning), the two points-bar pieces and the three control icons.
  - **Anything the screen stretches is uniform along the stretch axis.** The line strip and
    the two bar pieces are rows only, so a stretch in x resamples each row against itself
    and is exact at any length. Get this wrong and a stretched sprite smears. Dashes and
    dots on the constellation lines are laid by the screen, one blit per dash, for the same
    reason: a dash pattern baked into the strip would stretch with the edge.

The page layers reuse `gui_parchment.py`'s `star_chart` material (stock, ink, silver) so the
chart and the chrome around it are one sheet. The vellum is not `paper_sheet`, which wraps
every 48px; this tile is 512 and needs grain that wraps at 512.

Run from the repo root:  python tools/skill_chart_textures.py [--only name,name]
"""

import argparse
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import marker, save  # noqa: E402
from gui_parchment import mix, paper_colour, periodic_noise, skin  # noqa: E402

OUT = "src/main/resources/assets/wizards_and_beasts/textures/gui/skill_tree/chart"
MARKER = marker("skill_chart_textures.py")

SS = 4  # supersample factor for the round sprites

# Star sprite canvas size per node size class (px).
SIZES = {"small": 16, "notable": 24, "keystone": 32}

S = skin("star_chart")
INK = S["ink"]
INK_2 = S["ink_2"]
INK_3 = S["ink_3"]
WHITE = (255, 255, 255, 255)


# --------------------------------------------------------------------------- helpers

def canvas(px, fill=(0, 0, 0, 0), ss=1):
    img = Image.new("RGBA", (px * ss, px * ss), fill)
    return img, ImageDraw.Draw(img)


def down(img, w, h=None):
    return img.resize((w, h if h is not None else w), Image.LANCZOS)


def crisp(img):
    """Steepen a downsampled sprite's alpha ramp: a burin leaves a hard edge, not a blur.

    Without it the LANCZOS fringe reads as a soft glow once the screen magnifies a sprite
    with nearest sampling -- the night-sky look this set replaced.
    """
    r, g, b, a = img.split()
    a = a.point(lambda v: max(0, min(255, (v - 56) * 255 // 144)))
    return Image.merge("RGBA", (r, g, b, a))


def disc(d, cx, cy, r, fill=WHITE):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)


def ring(d, cx, cy, r, width, fill=WHITE):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=fill, width=max(1, int(round(width))))


def ray(d, cx, cy, ang, inner, reach, half_w, fill=WHITE):
    """A tapered engraver's ray: a thin wedge from `inner` out to a point at `reach`."""
    dx, dy = math.cos(ang), math.sin(ang)
    px_, py_ = -dy, dx
    d.polygon([(cx + dx * inner + px_ * half_w, cy + dy * inner + py_ * half_w),
               (cx + dx * reach, cy + dy * reach),
               (cx + dx * inner - px_ * half_w, cy + dy * inner - py_ * half_w)], fill=fill)


def put_ink(px_, size, x, y, colour, alpha):
    """Composite one ink pixel onto an RGBA tile (source-over), wrapping toroidally."""
    x %= size
    y %= size
    r, g, b, a = px_[x, y]
    if a == 0:
        px_[x, y] = colour + (alpha,)
        return
    out_a = alpha + a * (255 - alpha) // 255
    t = alpha / max(1, out_a)
    px_[x, y] = mix((r, g, b), colour, t) + (min(255, out_a),)


# --------------------------------------------------------------------------- page layers

def a_starfield_far():
    """The opaque bottom of the stack: the vellum itself, with a dotted graticule.

    Grain from `gui_parchment`'s own noise and tone ramp, but periodic in 512 rather than 48
    so the big tile has no seam. The graticule is the survey grid every printed chart is
    ruled on, dotted and faint -- a guide for the eye, not a line anyone reads.
    """
    size = 512
    cloud = periodic_noise(size, size, size, 64, 20260923)
    mid = periodic_noise(size, size, size, 16, 20260924)
    fine = periodic_noise(size, size, size, 4, 20260925)
    img = Image.new("RGBA", (size, size))
    px_ = img.load()
    for y in range(size):
        for x in range(size):
            v = ((cloud[y][x] - 0.5) * 0.8 + (mid[y][x] - 0.5) * 0.5
                 + (fine[y][x] - 0.5) * 0.55)
            px_[x, y] = paper_colour(S, v) + (255,)

    rng = random.Random(20260926)
    # Fibres along the grain, and the odd foxing speck, as on the chrome's own stock.
    for _ in range(240):
        fx, fy = rng.randrange(size), rng.randrange(size)
        tone = S["paper_l"] if rng.random() < 0.5 else S["paper_d"]
        for i in range(rng.randint(3, 8)):
            put_ink(px_, size, fx + i, fy, tone, 200)
    for _ in range(26):
        put_ink(px_, size, rng.randrange(size), rng.randrange(size), S["paper_dd"], 150)

    # Graticule: every 64px, one dot in three, so it reads as a ruled guide and not a grid.
    grid = mix(S["paper"], INK_3, 0.55)
    for k in range(0, size, 64):
        for t in range(0, size, 3):
            put_ink(px_, size, k, t, grid, 105)
            put_ink(px_, size, t, k, grid, 105)
    return img


def a_nebula():
    """The Milky Way as the engravers drew it: stipple, dense where the band is, on alpha.

    Density follows a periodic cloud field, so the patches have soft silhouettes and tile
    without a seam. Ink dots, not a colour wash -- a printed chart had one ink.
    """
    size = 256
    band = periodic_noise(size, size, size, 64, 20260927)
    detail = periodic_noise(size, size, size, 16, 20260928)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px_ = img.load()
    rng = random.Random(20260929)
    for y in range(size):
        for x in range(size):
            v = band[y][x] * 0.75 + detail[y][x] * 0.25
            density = max(0.0, (v - 0.58) / 0.42) ** 1.5 * 0.17
            if rng.random() < density:
                put_ink(px_, size, x, y, INK_2, 55 + rng.randrange(35))
    return img


def a_starfield():
    """The uncatalogued stars: specks of ink, a few crosses and the odd nebulous circle.

    Pixel art at 1:1 -- a supersampled speck would downsample to grey fuzz, and the whole
    point of an engraving is that the burin left a hard edge.
    """
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px_ = img.load()
    rng = random.Random(20260930)
    for _ in range(230):
        x, y = rng.randrange(size), rng.randrange(size)
        mag = rng.random()
        if mag < 0.70:
            put_ink(px_, size, x, y, INK_2, 110 + int(mag * 120))
        elif mag < 0.93:
            # A small star: a filled plus.
            put_ink(px_, size, x, y, INK_2, 210)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put_ink(px_, size, x + ox, y + oy, INK_2, 120)
        elif mag < 0.98:
            # A brighter one with four short rays.
            put_ink(px_, size, x, y, INK, 220)
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put_ink(px_, size, x + ox, y + oy, INK_2, 190)
                put_ink(px_, size, x + ox * 2, y + oy * 2, INK_2, 90)
        else:
            # A nebula symbol: a tiny open circle.
            for ox, oy in ((-1, -2), (0, -2), (1, -2), (-2, -1), (2, -1), (-2, 0), (2, 0),
                           (-2, 1), (2, 1), (-1, 2), (0, 2), (1, 2)):
                put_ink(px_, size, x + ox, y + oy, INK_2, 120)
    return img


def a_vignette():
    """Toning at the plate's edge: the vellum browned where hands held it. Near-transparent.

    A box falloff, not a radial one: the thing being framed is a rectangle, and a radial
    vignette stretched to a 2:1 viewport tones the middle of the short edges and misses the
    corners. Smooth and faint in both axes, so the nearest-neighbour stretch never shows steps.
    """
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px_ = img.load()
    reach = 0.22
    tone = mix(S["burnt"], S["paper_dd"], 0.35)
    for y in range(size):
        for x in range(size):
            fx = min(x, size - 1 - x) / (size * reach)
            fy = min(y, size - 1 - y) / (size * reach)
            t = min(1.0, fx) * min(1.0, fy)
            a = int(64 * (1.0 - t) ** 2.0)
            if a > 0:
                px_[x, y] = tone + (a,)
    return img


# --------------------------------------------------------------------------- geometry

def a_survey_ring():
    """A graduated circle, stretched to each survey radius and tinted ink.

    Ticks every 5 degrees, longer every 30: the divided circle every atlas plate carries. 256
    with a one-pixel stroke after the downscale, because the screen stretches this to as much
    as ~1100px across at full zoom and a fatter source would land as a band.
    """
    px = 256
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    r = px * SS * 0.46
    ring(d, c, c, r, SS)
    for i in range(72):
        ang = math.pi * 2 * i / 72
        length = SS * (6 if i % 6 == 0 else 3)
        dx, dy = math.cos(ang), math.sin(ang)
        d.line([(c + dx * r, c + dy * r), (c + dx * (r + length), c + dy * (r + length))],
               fill=WHITE, width=SS)
    return down(img, px)


def a_ley_line():
    """The line strip: 16x8, uniform along x, a crisp engraved stroke across y.

    Crisp, not a glow: rows 1..6 are solid, so a nearest-sampled 1-, 2- or 3-px stroke is a
    hard ink line at every weight, and the two outer rows give the rotated edge a sliver of
    antialiasing. The screen lays dashes and dots out of this; it is never patterned itself.
    """
    w, h = 16, 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px_ = img.load()
    profile = (90, 255, 255, 255, 255, 255, 255, 90)
    for y in range(h):
        for x in range(w):
            px_[x, y] = (255, 255, 255, profile[y])
    return img


# --------------------------------------------------------------------------- nodes

def star_core(px):
    """A plain filled disc, r = 0.36. The screen's knockout and silver roundel.

    Tinted vellum it is the engraver's gap -- lines stop short of a star rather than running
    into it; tinted silver it is the leaf laid under an allocated star.
    """
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    disc(d, c, c, px * SS * 0.36)
    return crisp(down(img, px))


def star_ring(px):
    """An open circle: the allocatable state, tinted to the region's ink.

    Weighted to survive the draw: a 16px sprite lands at 12px, and a one-pixel ring sampled
    down by three quarters breaks into dashes.
    """
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    r = px * SS * 0.30
    ring(d, c, c, r, px * SS / 8.5)
    disc(d, c, c, px * SS * 0.07)  # a centre point, as on a charted but unlit position
    if px >= 32:
        # The keystone's second circle.
        ring(d, c, c, px * SS * 0.43, px * SS / 16)
    return crisp(down(img, px))


def star_flare(px, rays=None, polaris=False):
    """An engraved star of the first rank: a solid disc with tapered rays.

    Ray count is magnitude, as on the printed charts: four for a small node, six for a
    notable, eight (alternating long and short, inside a ring) for a keystone.
    """
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    s = px * SS
    if rays is None:
        rays = {16: 4, 24: 6, 32: 8}.get(px, 6)
    for i in range(rays):
        ang = math.pi * 2 * i / rays - math.pi / 2
        long_ray = rays < 8 or i % 2 == 0
        reach = s * (0.49 if long_ray else 0.34)
        # A keystone's rays spring from outside its ring, so the ring stays an open circle.
        inner = s * (0.25 if rays >= 8 else 0.12)
        ray(d, c, c, ang, inner, reach, s * (0.075 if px <= 16 else 0.06))
    if rays >= 8:
        # The keystone's ornament: a smaller disc inside an open ring, the rays crossing it.
        disc(d, c, c, s * 0.14)
        ring(d, c, c, s * 0.26, s / 26)
    else:
        disc(d, c, c, s * 0.22)
    return crisp(down(img, px))


def a_polaris():
    """Polaris as a compass rose: eight faceted points and a divided ring, 64px.

    Each point is split lengthwise, one half white and one half grey, so under a single tint
    it still reads as a cut, raised star -- the engraver's hatching done in two tones.
    """
    px = 64
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    s = px * SS
    grey = (150, 150, 150, 255)
    ring(d, c, c, s * 0.30, SS * 1.2)
    for i in range(48):
        ang = math.pi * 2 * i / 48
        dx, dy = math.cos(ang), math.sin(ang)
        d.line([(c + dx * s * 0.30, c + dy * s * 0.30), (c + dx * s * 0.33, c + dy * s * 0.33)],
               fill=WHITE, width=SS)
    for i in range(8):
        ang = math.pi * 2 * i / 8 - math.pi / 2
        reach = s * (0.49 if i % 2 == 0 else 0.34)
        half_w = s * (0.07 if i % 2 == 0 else 0.055)
        dx, dy = math.cos(ang), math.sin(ang)
        nx, ny = -dy, dx
        tip = (c + dx * reach, c + dy * reach)
        d.polygon([(c, c), (c + nx * half_w, c + ny * half_w), tip], fill=WHITE)
        d.polygon([(c, c), (c - nx * half_w, c - ny * half_w), tip], fill=grey)
    disc(d, c, c, s * 0.075)
    return crisp(down(img, px))


def star_locked(px):
    """An uncharted position: a thin open circle, drawn faint by the screen.

    Its own shape, not a dim tint of the lit star: "not yet" must be legible without comparing
    two tints. A keystone's is doubled with a dotted outer circle, so its rank shows before
    it is taken.
    """
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    s = px * SS
    ring(d, c, c, s * 0.28, s / 11)
    if px >= 32:
        for i in range(12):
            ang = math.pi * 2 * i / 12
            disc(d, c + math.cos(ang) * s * 0.42, c + math.sin(ang) * s * 0.42, s * 0.03)
    return crisp(down(img, px))


def a_star_halo():
    """A soft wash: hover cue, the affordable pulse, and the region washes.

    A plateau with a soft edge rather than a peaked glow -- on paper this is a watercolour
    wash laid by hand, and a wash is even across its middle.
    """
    px = 64
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    px_ = img.load()
    c = (px - 1) / 2
    for y in range(px):
        for x in range(px):
            r = math.hypot(x - c, y - c) / (px / 2)
            if r >= 1.0:
                continue
            t = min(1.0, max(0.0, (r - 0.45) / 0.55))
            a = int(200 * (1.0 - t * t * (3 - 2 * t)))
            if a > 0:
                px_[x, y] = (255, 255, 255, a)
    return img


# --------------------------------------------------------------------------- furniture

def a_pip(filled):
    """A level pip. Filled is a small four-rayed star; empty is an open circle.

    Shape, not just colour, so the pips under a multi-level node can be counted at 5px.
    """
    px = 8
    img, d = canvas(px, ss=SS)
    c = px * SS / 2
    s = px * SS
    if filled:
        for i in range(4):
            ray(d, c, c, math.pi / 2 * i, 0, s * 0.5, s * 0.14)
        disc(d, c, c, s * 0.22)
    else:
        ring(d, c, c, s * 0.30, SS * 1.2)
    return crisp(down(img, px))


def a_bar(fill):
    """Points-bar track and fill, 8x8, authored in colour and drawn untinted.

    Rows only, so the stretch to the footer's width is exact. Drawn 4px tall, which samples
    rows 1, 3, 5 and 7 -- so the rows come in equal pairs and either half of a pair can land.
    Track: a groove pressed into the vellum. Fill: silver leaf, lit on top.
    """
    px = 8
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    px_ = img.load()
    if fill:
        # Darker than the silver furniture elsewhere: a pale leaf in a pale groove vanishes.
        bands = (S["gilt_l"], S["gilt_d"], mix(S["gilt_d"], INK, 0.35), mix(S["gilt_d"], INK, 0.6))
    else:
        bands = (INK_2, S["paper_dd"], S["paper_d"], S["paper_ll"])
    for y in range(px):
        for x in range(px):
            px_[x, y] = bands[y // 2] + (255,)
    return img


def a_icon(kind):
    """The three viewport controls, 12x12, in the atlas's indigo ink.

    Not tinted at draw: `ThemedButton` blits an icon untinted, so these carry their colour.
    """
    px = 12
    ink = INK + (255,)
    img, d = canvas(px, ss=SS)
    s = px * SS
    if kind == "recenter":
        # A reticle: ring, gap, four ticks, centre dot.
        r = s * 0.28
        c = s / 2
        d.ellipse((c - r, c - r, c + r, c + r), outline=ink, width=SS)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d.line([(c + dx * r * 1.45, c + dy * r * 1.45),
                    (c + dx * s * 0.46, c + dy * s * 0.46)], fill=ink, width=SS)
        d.ellipse((c - SS, c - SS, c + SS, c + SS), fill=ink)
    else:
        # A lens on a handle, with a plus or a minus inside it.
        cx = cy = s * 0.42
        r = s * 0.30
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=ink, width=SS)
        d.line([(cx + r * 0.72, cy + r * 0.72), (s - SS, s - SS)], fill=ink, width=SS * 2)
        d.line([(cx - r * 0.48, cy), (cx + r * 0.48, cy)], fill=ink, width=SS)
        if kind == "zoom_in":
            d.line([(cx, cy - r * 0.48), (cx, cy + r * 0.48)], fill=ink, width=SS)
    return down(img, px)


# --------------------------------------------------------------------------- regions

# One asterism per skill region, in a 16x16 grid. Hand-placed rather than generated: a random
# scatter of dots is not a constellation, and the point of these is that a player learns which
# shape means Herbology the same way they learn which shape means Orion.
#
# Each entry is (points, edges): points are (x, y) in the 16x16 cell, edges index into them.
# The last point in each list is drawn one step brighter — its alpha star.
ASTERISMS = {
    "spell_mastery": ([(3, 12), (7, 8), (11, 11), (13, 5), (8, 2)],
                      [(0, 1), (1, 2), (2, 3), (3, 4), (1, 4)]),
    "dark_arts": ([(3, 3), (8, 6), (13, 3), (8, 10), (8, 14)],
                  [(0, 1), (1, 2), (1, 3), (3, 4)]),
    "magizoology": ([(2, 9), (6, 5), (10, 7), (14, 4), (9, 13)],
                    [(0, 1), (1, 2), (2, 3), (2, 4)]),
    "wandlore": ([(3, 13), (6, 9), (9, 6), (12, 3), (13, 8)],
                 [(0, 1), (1, 2), (2, 3), (2, 4)]),
    "herbology": ([(8, 14), (8, 9), (4, 6), (12, 6), (8, 3)],
                  [(0, 1), (1, 2), (1, 3), (1, 4)]),
    "alchemy": ([(4, 4), (12, 4), (12, 11), (4, 11), (8, 8)],
                [(0, 1), (1, 2), (2, 3), (3, 0), (0, 4)]),
    "goblin_craft": ([(3, 6), (8, 3), (13, 6), (13, 12), (3, 12)],
                     [(0, 1), (1, 2), (2, 3), (3, 4), (4, 0)]),
    "elf_bond": ([(3, 4), (7, 8), (11, 4), (7, 12), (12, 12)],
                 [(0, 1), (1, 2), (1, 3), (3, 4)]),
}


def a_region_glyph(name):
    """A region's asterism, engraved: hairline figure, solid stars, the alpha star rayed."""
    px = 16
    points, edges = ASTERISMS[name]
    img, d = canvas(px, ss=SS)
    scaled = [(x * SS + SS / 2, y * SS + SS / 2) for x, y in points]
    for a, b in edges:
        d.line([scaled[a], scaled[b]], fill=(255, 255, 255, 150), width=SS // 2)
    for i, (x, y) in enumerate(scaled):
        if i == len(scaled) - 1:
            for k in range(4):
                ray(d, x, y, math.pi / 2 * k + math.pi / 4, 0, SS * 2.6, SS * 0.45)
            disc(d, x, y, SS * 1.35)
        else:
            disc(d, x, y, SS * 0.95)
    return crisp(down(img, px))


# --------------------------------------------------------------------------- manifest

ASSETS = {
    # Page layers, back to front. SkillTreeScreen tiles them with the chart.
    "starfield_far.png": a_starfield_far,
    "nebula.png": a_nebula,
    "starfield.png": a_starfield,
    "vignette.png": a_vignette,
    # Geometry.
    "survey_ring.png": a_survey_ring,
    "ley_line.png": a_ley_line,
    # Node states.
    "star_polaris.png": a_polaris,
    "star_halo.png": a_star_halo,
    # Furniture.
    "pip_on.png": lambda: a_pip(True),
    "pip_off.png": lambda: a_pip(False),
    "bar_track.png": lambda: a_bar(False),
    "bar_fill.png": lambda: a_bar(True),
    "icon_zoom_in.png": lambda: a_icon("zoom_in"),
    "icon_zoom_out.png": lambda: a_icon("zoom_out"),
    "icon_recenter.png": lambda: a_icon("recenter"),
}

for _name, _px in SIZES.items():
    ASSETS[f"star_core_{_name}.png"] = (lambda p=_px: star_core(p))
    ASSETS[f"star_ring_{_name}.png"] = (lambda p=_px: star_ring(p))
    ASSETS[f"star_flare_{_name}.png"] = (lambda p=_px: star_flare(p))
    ASSETS[f"star_locked_{_name}.png"] = (lambda p=_px: star_locked(p))

for _region in ASTERISMS:
    ASSETS[f"region_{_region}.png"] = (lambda r=_region: a_region_glyph(r))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="", help="comma-separated file names")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    # No `is_regenerable` gate, unlike gui_chrome.py: this tool owns every file in `chart/`
    # and always has. It does write the marker, so anything hand-authored dropped in here
    # later is at least distinguishable from its output.
    written = 0
    for name, fn in ASSETS.items():
        if only and name not in only:
            continue
        save(fn(), os.path.join(OUT, name), MARKER)
        written += 1
    print(f"wrote {written} chart textures to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
