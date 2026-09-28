#!/usr/bin/env python3
"""Masonry, marble and joinery for the location block sets.

The Hogwarts, Gringotts, Ministry, Diagon Alley, Hogsmeade, Honeydukes and Three
Broomsticks blocks had textures, and they did not read as Minecraft. Three measurable
reasons, all of them fixable:

  - **Palette size.** They shipped 24 to 77 distinct colours. Vanilla, measured:
    `stone` 4, `cobblestone` 6, `oak_planks` 7, `iron_ingot` 8. Nothing else moves a
    texture as far toward looking like Minecraft as spending fewer colours on it, which
    is why every surface here ends at `posterise`.
  - **Uniformity.** Every brick in a wall was the same tone, every mortar line dead
    straight, every tile identical. Vanilla varies each unit and chips the corners; the
    variation *is* the texture. A perfect grid reads as a placeholder.
  - **Regularity in the wrong places.** Marble veining was evenly spaced 45-degree
    dashes, which tiles into visible corduroy across a wall. Veins here are the
    iso-lines of a domain-warped noise field: irregular, branching, and periodic, so
    they wrap without a seam.

Everything is generated to wrap at 16 pixels. The noise lattice is indexed modulo the
tile, and the domain warp is itself periodic, so `f(x + 16) == f(x)` exactly rather than
approximately — a wall of these has no seam and no diagonal banding.

Colour comes from a palette per location, chosen for the films rather than for Minecraft's
existing stone: Hogwarts is warm sandstone-grey, not the neutral grey it had; Gringotts is
ivory and brass; the Ministry is a near-black polished stone with a green cast and gold.

Run from the repo root:
    python tools/location_textures.py [--force] [--only name,name] [--list]
"""

import argparse
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_generated, marker, mix, posterise, save  # noqa: E402
import block_textures as bt  # noqa: E402

BLOCK_DIR = "src/main/resources/assets/wizards_and_beasts/textures/block"
MARKER = marker("location_textures.py")
SIZE = 16


# --------------------------------------------------------------------------- noise
#
# Everything below is built on one wrapped value-noise field. `lattice` indexes its grid
# modulo the number of cells across the tile, so the field is periodic at 16px by
# construction rather than by fading the edges together afterwards.

def lattice(seed, gx, gy, n):
    h = ((gx % n) * 374761393 + (gy % n) * 668265263 + seed * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(seed, x, y, cell):
    """Smoothed value noise on a `cell`-pixel lattice, periodic over the tile."""
    n = max(1, SIZE // cell)
    gx, gy = x / cell, y / cell
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = lattice(seed, x0, y0, n)
    b = lattice(seed, x0 + 1, y0, n)
    c = lattice(seed, x0, y0 + 1, n)
    d = lattice(seed, x0 + 1, y0 + 1, n)
    return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy


def fbm(seed, x, y, cells=(8, 4, 2), weights=(0.55, 0.30, 0.15)):
    """Blotches at several scales. One octave alone is either mush or wallpaper."""
    return sum(w * vnoise(seed + i * 17, x, y, c)
               for i, (c, w) in enumerate(zip(cells, weights)))


def hash01(seed, *parts):
    h = seed * 2654435761
    for p in parts:
        h = ((h ^ (int(p) * 2246822519)) * 2654435761) & 0xFFFFFFFF
    return ((h ^ (h >> 15)) & 0xFFFF) / 65535.0


# --------------------------------------------------------------------------- palette

# One architectural gold for every gilded block, taken from the GUI's gilt tokens
# (`WizardsPalette.GILT_DARK/GILT/GILT_LIGHT`) so a Gringotts trim, a Ministry chevron and the
# gilt on a parchment screen are the same metal (visual families, 2026-09-27). Before, both sets
# named one hex but came out as three golds: posterise folded Gringotts' into its marble (a
# khaki #AF9959) and the veined marble dimmed the Ministry's to #826F31. The three tones are
# drawn exactly and survive posterise (`main` restores them).
GILT_DARK_HEX, GILT_HEX, GILT_LIGHT_HEX = "#8f6522", "#c9973a", "#ebc874"
GILT_TONES = {hx(c)[:3] for c in (GILT_DARK_HEX, GILT_HEX, GILT_LIGHT_HEX)}


class Palette:
    """A surface's tones, darkest to lightest, plus a mortar and an accent.

    Ramps are built by mixing toward black and white from one base rather than by listing
    five hexes per material, so a location's whole set stays in one family and a palette can
    be retuned by moving one colour.
    """

    def __init__(self, base, mortar=None, accent=None, spread=0.30, warm=0.0):
        # spread and warm first: the mortar and accent defaults are derived through
        # shade(), which reads both.
        self.base = hx(base)[:3]
        self.spread = spread
        self.warm = warm
        self.mortar = hx(mortar)[:3] if mortar else self.shade(-0.45)
        self.accent = hx(accent)[:3] if accent else self.shade(0.35)

    def shade(self, t):
        """`t` in -1..1: negative toward shadow, positive toward light.

        Shadows are cooled and highlights warmed slightly. A ramp mixed toward pure grey
        reads as plastic; real stone shifts hue as it turns to the light.
        """
        if t >= 0:
            target = (255, 248, 236) if self.warm >= 0 else (240, 244, 255)
        else:
            target = (18, 16, 22)
        return mix(self.base + (255,), target + (255,), min(1.0, abs(t)))[:3]

    def tone(self, v):
        """Map 0..1 to a point on the ramp, centred on the base."""
        return self.shade((v - 0.5) * 2.0 * self.spread)


# --------------------------------------------------------------------------- surfaces
#
# Each surface fills a 16x16 pixel buffer, and `main` posterises the result.
#
# Which sets the rule every surface here obeys: **detail must be bold before it is
# posterised**. Collapsing to six colours throws away any feature whose contrast is under
# about a sixth of the palette's range, so a subtle vein, a faint chisel mark or a gentle
# moss patch does not come out muted — it comes out *gone*. The first cut of this file lost
# every marble vein exactly that way. Draw it too strong, then let posterise take it down.

def blank():
    return [[(0, 0, 0, 255)] * SIZE for _ in range(SIZE)]


def put(px, x, y, rgb):
    px[y % SIZE][x % SIZE] = (rgb[0], rgb[1], rgb[2], 255)


def rough_stone(pal, seed=1, grit=0.10, contrast=1.45):
    """Quarried stone: small clustered patches, a few chisel marks, no soft cloud.

    Vanilla `stone` clusters into patches two to four pixels across, with short dark dashes
    and single light chips. The first cut here ran its patches on an 8-pixel lattice, so a
    tile held two soft clouds and a castle wall read as blurred plaster beside the crisp
    brick next to it (visual consistency audit, C3). The lattice is 4 and 2 now, with enough
    contrast that posterise keeps four to six steps, and a few faint marks drawn on top.
    """
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            # y is doubled so clusters run along the bed, as vanilla stone's do; the period
            # stays a divisor of 16, so the tile still wraps without a seam.
            v = fbm(seed, x, 2 * y, (4, 2), (0.55, 0.45))
            v = 0.5 + (v - 0.5) * contrast
            v += (grit + 0.10) * (hash01(seed + 5, x, y) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, v))))
    # Chisel marks: three short darker dashes along the bed. Few and faint on purpose — a
    # dash with a lit chip beside it repeats across a wall as a row of little faces.
    for i in range(3):
        x = int(hash01(seed + 31, i, 0) * SIZE)
        y = int(hash01(seed + 37, i, 1) * SIZE)
        for dx in range(2 + int(hash01(seed + 41, i, 2) * 2)):
            put(px, x + dx, y, pal.tone(0.24))
    return px


def courses(pal, seed=2, bw=8, bh=4, chip=0.16, crack=0.0, moss=None, soot=0.0):
    """Coursed masonry: staggered units, each its own tone, with chipped corners.

    The stagger period is two rows and both `bw` and `bh` divide 16, so the bond wraps.
    Per-unit tone is the whole point — a wall where every brick is the base colour is a
    grid, and a grid is what the old textures looked like.
    """
    px = blank()
    for y in range(SIZE):
        row = y // bh
        shift = (bw // 2) * (row % 2)
        for x in range(SIZE):
            col = ((x + shift) % SIZE) // bw
            u, v = (x + shift) % bw, y % bh
            edge = u == 0 or v == 0
            if edge:
                m = pal.mortar
                m = mix(m + (255,), pal.shade(-0.6) + (255,),
                        0.5 * hash01(seed + 3, x, y))[:3]
                put(px, x, y, m)
                continue
            t = 0.5 + 0.42 * (hash01(seed, col, row) - 0.5) * 2
            t += 0.12 * (fbm(seed + 9, x, y, (4, 2), (0.6, 0.4)) - 0.5)
            # A chipped corner: knock the tone down where a unit meets the mortar.
            if chip and (u <= 1 or v <= 1 or u >= bw - 1 or v >= bh - 1) \
                    and hash01(seed + 7, x, y) < chip:
                t -= 0.45
            rgb = pal.tone(min(1.0, max(0.0, t)))
            if crack and hash01(seed + 11, col, row) < crack:
                # One split down a unit, seeded per unit so it is the same crack each run.
                cx = 1 + int(hash01(seed + 13, col, row) * (bw - 2))
                if u == cx or (v > bh // 2 and u == cx + 1):
                    rgb = pal.shade(-0.55)
            if moss is not None:
                g = fbm(seed + 21, x, y, (8, 4), (0.7, 0.3))
                if g > 0.50:
                    rgb = mix(rgb + (255,), hx(moss), min(1.0, (g - 0.50) * 5.0))[:3]
            if soot:
                s = fbm(seed + 31, x, y, (8, 4), (0.7, 0.3))
                rgb = mix(rgb + (255,), (26, 22, 20, 255), soot * max(0.0, s - 0.35))[:3]
            put(px, x, y, rgb)
    return px


def marble(pal, seed=3, width=1.0, warp=3.0, vein_colour=None, sheen=True,
           vein_strength=1.0, levels=2):
    """Polished marble: veins as the iso-lines of a domain-warped noise field.

    Evenly spaced diagonal dashes — what these textures used to have — tile into corduroy
    and read as hazard tape. Taking the level set of a warped field instead gives veins that
    wander and branch, and because the warp is built from the same periodic noise the field
    still wraps exactly.

    Two things a naive threshold on that field gets wrong, both of which shipped here first:

      - How *many* veins you get is down to the seed. `gringotts_white_marble` drew badly:
        the field never crossed the level inside the tile and the stone came out blank.
        Folding the field into `levels` bands first guarantees crossings.
      - How *wide* they are is down to the local gradient, so wherever the warped field
        goes flat the vein balloons into a blob — which is exactly what the Ministry's
        black marble turned into. Dividing the distance by the gradient converts it to a
        distance in *pixels*, so `width` means one pixel everywhere.
    """
    px = blank()
    vein = hx(vein_colour)[:3] if vein_colour else pal.shade(-0.42)

    def band(x, y):
        # Warp on a finer lattice than the field, or the vein wanders at exactly the
        # field's own scale and comes out as long straight runs meeting at angles.
        wx = x + warp * (vnoise(seed + 41, x, y, 4) - 0.5) * 2
        wy = y + warp * (vnoise(seed + 43, x, y, 4) - 0.5) * 2
        return (vnoise(seed, wx, wy, 8) * levels) % 1.0

    def wrapped(a, b):
        """Difference across a band, ignoring the jump where it rolls over."""
        d = a - b
        return d - round(d)

    for y in range(SIZE):
        for x in range(SIZE):
            f = band(x, y)
            grad = math.hypot(wrapped(band(x + 1, y), f), wrapped(band(x, y + 1), f))
            pixels = abs(f - 0.5) / max(grad, 1e-4)
            base = 0.5 + 0.16 * (fbm(seed + 51, x, y, (8, 4), (0.7, 0.3)) - 0.5)
            rgb = pal.tone(base)
            if pixels < width:
                rgb = mix(rgb + (255,), vein + (255,),
                          min(1.0, vein_strength * (1.0 - 0.3 * pixels / width)))[:3]
            elif pixels < width * 2.0:
                rgb = mix(rgb + (255,), vein + (255,),
                          0.24 * vein_strength * (1 - pixels / (width * 2.0)))[:3]
            if sheen and y < 2:
                rgb = mix(rgb + (255,), pal.shade(0.5) + (255,), 0.28 - 0.12 * y)[:3]
            put(px, x, y, rgb)
    return px


def flagstone(pal, seed=4, cells=3):
    """Irregular slabs: a jittered lattice, nearest-site, with a mortar joint between.

    A flagged floor is the one masonry that must *not* be on a grid. Sites are jittered
    inside their cell and compared with wrapped offsets so the pattern still tiles.
    """
    px = blank()
    step = SIZE / cells
    sites = []
    for gy in range(cells):
        for gx in range(cells):
            sx = (gx + 0.22 + 0.56 * hash01(seed, gx, gy)) * step
            sy = (gy + 0.22 + 0.56 * hash01(seed + 1, gx, gy)) * step
            sites.append((sx, sy, hash01(seed + 2, gx, gy)))
    for y in range(SIZE):
        for x in range(SIZE):
            best = second = 1e9
            tone = 0.5
            for sx, sy, t in sites:
                dx = abs(x + 0.5 - sx)
                dy = abs(y + 0.5 - sy)
                dx = min(dx, SIZE - dx)
                dy = min(dy, SIZE - dy)
                d = dx * dx + dy * dy
                if d < best:
                    second, best, tone = best, d, t
                elif d < second:
                    second = d
            joint = math.sqrt(second) - math.sqrt(best)
            if joint < 0.9:
                put(px, x, y, pal.mortar)
                continue
            v = 0.5 + 0.40 * (tone - 0.5) * 2
            v += 0.14 * (fbm(seed + 61, x, y, (4, 2), (0.6, 0.4)) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, v))))
    return px


def tiles(pal, seed=5, n=2, grout=None, polish=0.0, alt=None):
    """Square tiles with a grout joint, each tile its own tone. `alt` chequers a second stone."""
    px = blank()
    step = SIZE // n
    grout_rgb = hx(grout)[:3] if grout else pal.mortar
    for y in range(SIZE):
        for x in range(SIZE):
            tx, ty = x // step, y // step
            u, v = x % step, y % step
            if u == 0 or v == 0:
                put(px, x, y, grout_rgb)
                continue
            t = 0.5 + 0.30 * (hash01(seed, tx, ty) - 0.5) * 2
            t += 0.10 * (fbm(seed + 71, x, y, (4, 2), (0.6, 0.4)) - 0.5)
            stone = alt if alt is not None and (tx + ty) % 2 else pal
            rgb = stone.tone(min(1.0, max(0.0, t)))
            # Kept gentle: at 0.35 toward a light tone this reads on a near-black
            # Ministry tile as a white corner stuck to every unit.
            if u == 1 or v == 1:                       # lit bevel
                rgb = mix(rgb + (255,), pal.shade(0.35) + (255,), 0.20)[:3]
            elif u == step - 1 or v == step - 1:       # shadowed bevel
                rgb = mix(rgb + (255,), pal.shade(-0.35) + (255,), 0.22)[:3]
            if polish and v < step * 0.45 and u + v > step * 0.5:
                rgb = mix(rgb + (255,), pal.shade(0.6) + (255,),
                          polish * (1 - v / (step * 0.45)))[:3]
            put(px, x, y, rgb)
    return px


def fluted(pal, seed=6, flutes=2):
    """A pillar shaft: a few broad flutes with a dark groove between them.

    Four flutes across sixteen pixels is a four-pixel repeat, and a four-pixel repeat of
    light-dark is not a column, it is a barcode. Two broad flutes read as a turned shaft and
    leave room for the groove to be an actual groove.
    """
    px = blank()
    width = SIZE / flutes
    for y in range(SIZE):
        for x in range(SIZE):
            u = (x % width) / width
            t = 0.26 + 0.62 * math.sin(math.pi * u) ** 0.7
            if u < 0.06 or u > 0.94:
                t -= 0.42
            t += 0.09 * (fbm(seed, x, y, (8, 4), (0.6, 0.4)) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def pillar_top(pal, seed=7):
    """The cap: a turned ring around a core, chamfered, not a bullseye of flat squares."""
    px = blank()
    c = (SIZE - 1) / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            r = max(abs(x - c), abs(y - c)) / c
            if r > 0.94:
                t = 0.22
            elif r > 0.74:
                t = 0.78
            elif r > 0.58:
                t = 0.34
            else:
                t = 0.55 + 0.18 * (fbm(seed, x, y, (8, 4), (0.6, 0.4)) - 0.5)
            t += 0.08 * (hash01(seed + 3, x, y) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def cobbles(pal, seed=8, cells=4):
    """Rounded setts, each domed and separated by a dark joint."""
    px = blank()
    step = SIZE / cells
    sites = []
    for gy in range(cells):
        for gx in range(cells):
            sx = (gx + 0.28 + 0.44 * hash01(seed, gx, gy)) * step
            sy = (gy + 0.28 + 0.44 * hash01(seed + 1, gx, gy)) * step
            sites.append((sx, sy, hash01(seed + 2, gx, gy)))
    for y in range(SIZE):
        for x in range(SIZE):
            best = 1e9
            tone = 0.5
            near = (0.0, 0.0)
            for sx, sy, t in sites:
                dx = x + 0.5 - sx
                dy = y + 0.5 - sy
                dx -= SIZE * round(dx / SIZE)
                dy -= SIZE * round(dy / SIZE)
                d = dx * dx + dy * dy
                if d < best:
                    best, tone, near = d, t, (dx, dy)
            radius = step * 0.62
            dist = math.sqrt(best)
            if dist > radius:
                put(px, x, y, pal.mortar)
                continue
            dome = 1.0 - (dist / radius) ** 2
            t = 0.22 + 0.58 * dome + 0.44 * (tone - 0.5)
            t -= 0.14 * (near[0] + near[1]) / max(1e-6, radius) * 0.5
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def planks(pal, seed=9, rows=4, vertical=False, nails=None):
    """Sawn boards with grain running along them, and optional nail heads."""
    px = blank()
    h = SIZE // rows
    for y in range(SIZE):
        for x in range(SIZE):
            a, b = (x, y) if not vertical else (y, x)
            board = b // h
            v = b % h
            if v == 0:
                put(px, x, y, pal.mortar)
                continue
            t = 0.5 + 0.30 * (hash01(seed, board) - 0.5) * 2
            grain = vnoise(seed + 11 + board * 3, a, board * 7, 4)
            t += 0.26 * (grain - 0.5)
            if grain > 0.80:
                t -= 0.22
            rgb = pal.tone(min(1.0, max(0.0, t)))
            if nails and (a % 8 == 2) and v == h // 2:
                rgb = hx(nails)[:3]
            put(px, x, y, rgb)
    return px


def painted_boards(paint, wood, seed=9, rows=4):
    """Vertical boards under paint. The paint hides the grain, so the material has to read
    through wear instead: staggered board-end joints, as on vanilla planks, and a few chips
    where the timber shows through (visual families, 2026-09-27 — the first cut was flat
    stripes that could have been any painted material)."""
    px = planks(paint, seed=seed, rows=rows, vertical=True)
    w = SIZE // rows
    for board in range(rows):
        cut = int(hash01(seed + 5, board) * SIZE)
        for u in range(1, w):
            put(px, board * w + u, cut, paint.mortar)
        for k in range(2):
            x = board * w + 1 + int(hash01(seed + 9, board, k) * (w - 1))
            y = (cut + 1 + int(hash01(seed + 13, board, k) * 5)) % SIZE
            put(px, x, y, wood.tone(0.6))
            put(px, x, (y + 1) % SIZE, wood.tone(0.4))
    return px


def panelling(pal, seed=10, panels=2):
    """Wainscot: raised panels with a bevelled surround."""
    px = blank()
    step = SIZE // panels
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x % step, y % step
            inset = min(u, v, step - 1 - u, step - 1 - v)
            if inset == 0:
                t = 0.30
            elif inset == 1:
                t = 0.80 if (u < step / 2 and v < step / 2) else 0.36
            else:
                t = 0.55 + 0.22 * (vnoise(seed, x, y * 3, 4) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def roof_tiles(pal, seed=11, rows=4):
    """Overlapping slates: staggered courses with a shadow under each lip."""
    px = blank()
    h = SIZE // rows
    w = SIZE // 4
    for y in range(SIZE):
        row = y // h
        shift = (w // 2) * (row % 2)
        for x in range(SIZE):
            u, v = (x + shift) % w, y % h
            col = ((x + shift) % SIZE) // w
            if v == 0:
                put(px, x, y, pal.shade(-0.62))
                continue
            t = 0.5 + 0.34 * (hash01(seed, col, row) - 0.5) * 2
            t += 0.30 * (1.0 - v / h) - 0.12
            if u == 0:
                t -= 0.28
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def riveted(pal, seed=12, plates=2):
    """Iron plate: bolted panels, for a vault door."""
    px = blank()
    step = SIZE // plates
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x % step, y % step
            t = 0.5 + 0.16 * (vnoise(seed, x, y, 4) - 0.5)
            if u == 0 or v == 0:
                t -= 0.34
            elif u == 1 or v == 1:
                t += 0.18
            if (u in (2, step - 3)) and (v in (2, step - 3)):
                t += 0.42                                    # rivet head
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def hewn(pal, seed=13, cells=4):
    """Hand-worked stone: broad blocks carrying diagonal chisel strokes."""
    px = blank()
    step = SIZE // cells
    for y in range(SIZE):
        for x in range(SIZE):
            bx, by = x // step, y // step
            u, v = x % step, y % step
            if u == 0 or v == 0:
                put(px, x, y, pal.mortar)
                continue
            t = 0.5 + 0.34 * (hash01(seed, bx, by) - 0.5) * 2
            stroke = (x + y * 2 + int(hash01(seed + 5, bx, by) * 6)) % 4
            t += 0.30 if stroke == 0 else (-0.24 if stroke == 2 else 0.0)
            t += 0.10 * (vnoise(seed + 9, x, y, 4) - 0.5)
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def inlay(base_pal, gold, seed=14, motif="panel"):
    """A gilded surface. A motif, because scattered gold pixels read as dirt, not as gold.

    `panel` frames the whole tile, so a wall of them becomes a lattice of gilded panels —
    which tiles by construction, unlike a meander, whose corners have to be got exactly right
    or the wall grows visible breaks. `chevron` is a running zig-zag for a narrower trim.
    """
    # A quiet base: the frame is the subject, and loud veining fights it.
    px = marble(base_pal, seed=seed + 3, width=0.5, sheen=False)
    g = hx(gold)[:3]
    if gold == GILT_HEX:
        g_lo, g_hi = hx(GILT_DARK_HEX)[:3], hx(GILT_LIGHT_HEX)[:3]
    else:
        g_lo = mix(g + (255,), (40, 28, 8, 255), 0.45)[:3]
        g_hi = mix(g + (255,), (255, 246, 200, 255), 0.5)[:3]

    def gild(x, y, lit):
        put(px, x, y, g_hi if lit else (g_lo if (x + y) % 4 == 0 else g))

    if motif == "chevron":
        for x in range(SIZE):
            phase = x % 8
            rise = phase if phase < 4 else 7 - phase
            for y in range(SIZE):
                if y % 8 in (2 + rise, 3 + rise):
                    gild(x, y, y % 8 == 2 + rise)
    else:
        for i in range(2, SIZE - 2):
            for edge, lit in ((2, True), (SIZE - 3, False)):
                gild(i, edge, lit)
                gild(edge, i, lit and i < SIZE // 2)
        for dx, dy in ((0, -2), (0, 2), (-2, 0), (2, 0), (0, -1), (0, 1), (-1, 0), (1, 0)):
            gild(8 + dx, 8 + dy, dx + dy < 0)
    return px


def veined_gold(pal, gold, seed=15):
    """Black marble whose veins run gold instead of grey.

    The vein is blended into the stone, so its core lands near the gold but never on it; the
    texels close to a gilt tone are snapped onto it, which is what keeps this block's gold the
    same metal as every other gilded block.
    """
    px = marble(pal, seed=seed, width=0.9, warp=4.5, vein_colour=gold, sheen=True,
                vein_strength=0.9)
    if gold == GILT_HEX:
        tones = sorted(GILT_TONES, key=sum)
        for row in px:
            for i, c in enumerate(row):
                best = min(tones, key=lambda t: sum((t[k] - c[k]) ** 2 for k in range(3)))
                if sum((best[k] - c[k]) ** 2 for k in range(3)) < 70 * 70:
                    row[i] = best + (255,)
    return px


def plaster(pal, seed=16, stripe=None):
    """Painted render with a trowel texture, and an optional painted stripe for a sweetshop.

    The render has tooth: a two-pixel grain plus short lighter trowel strokes. The first cut
    was a smooth wash at 0.22 contrast, which posterised to two flat colours.
    """
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            t = 0.5 + 0.40 * (fbm(seed, x, y, (4, 2), (0.6, 0.4)) - 0.5)
            t += 0.12 * (hash01(seed + 3, x, y) - 0.5)
            rgb = pal.tone(min(1.0, max(0.0, t)))
            if stripe and (x % 8) < 4:
                rgb = mix(rgb + (255,), hx(stripe), 0.5)[:3]
            put(px, x, y, rgb)
    for i in range(5):
        x = int(hash01(seed + 11, i, 0) * SIZE)
        y = int(hash01(seed + 13, i, 1) * SIZE)
        for dx in range(3):
            r, g, b, _ = px[y % SIZE][(x + dx) % SIZE]
            put(px, x + dx, y, mix((r, g, b, 255), (255, 250, 240, 255), 0.18)[:3])
    return px


def timber(pal, seed=17):
    """A dark structural beam: grain running the length of it, broken, with an adze line.

    Grain is drawn as rows that change tone every few pixels — the way `oak_log`'s side
    reads — rather than the smooth stretched noise of the first cut, which posterised flat.
    """
    px = blank()
    for y in range(SIZE):
        seg = 3 + int(hash01(seed + 7, y, 0) * 4)
        for x in range(SIZE):
            t = 0.5 + 0.55 * (hash01(seed, y, (x + y * 3) // seg) - 0.5)
            if hash01(seed + 19, y, x // 5) > 0.82:
                t -= 0.30
            if y % 8 == 0:
                t -= 0.26
            put(px, x, y, pal.tone(min(1.0, max(0.0, t))))
    return px


def metal_vessel(pal, band=None, seed=18, rivets=True):
    """The side of a cast vessel: a rolled rim, a waist band, and rivets.

    Cauldron sides were the odd face out — `block_textures.py` already generates each pot's
    *top* at seven colours, while the side sat unmarked at forty-five, so a single cauldron
    was drawn in two different styles depending on which way you looked at it.
    """
    px = blank()
    band_rgb = hx(band)[:3] if band else pal.shade(-0.5)
    for y in range(SIZE):
        for x in range(SIZE):
            t_ = 0.5 + 0.20 * (vnoise(seed, x, y * 2, 4) - 0.5)
            t_ += 0.26 * (1.0 - y / (SIZE - 1)) - 0.10        # lit from above
            rgb = pal.tone(min(1.0, max(0.0, t_)))
            if y in (0, 1):                                    # rim
                rgb = pal.tone(0.86 if y == 0 else 0.30)
            elif y in (7, 8):                                  # waist band
                rgb = mix(rgb + (255,), band_rgb + (255,), 0.75 if y == 7 else 0.5)[:3]
            elif y == SIZE - 1:
                rgb = pal.tone(0.18)
            if rivets and y in (3, 12) and x % 5 == 2:
                rgb = pal.tone(0.92)
            put(px, x, y, rgb)
    return px


def dimmed(name, pal, seed=19, keep=0.30):
    """A vanilla texture with the light taken out of it, kept as its own shape.

    The same trick `block_textures.py` uses for unlit torches: derive the dark state from
    the lit one rather than drawing a second texture, so the two are unmistakably the same
    block. Luminance is preserved and remapped onto a cold ramp, so glowstone keeps its
    lumpy clusters and loses only the glow.
    """
    src = bt.vanilla(name).convert("RGBA")
    px = blank()
    sp = src.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, _ = sp[x % src.width, y % src.height]
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            t_ = keep + (1.0 - keep) * lum
            put(px, x, y, pal.tone(min(1.0, max(0.0, t_))))
    return px


def device_panel(pal, seed=20, lamp=None):
    """A cased instrument: a bezelled plate with a recessed window."""
    px = blank()
    lamp_rgb = hx(lamp)[:3] if lamp else pal.shade(0.55)
    for y in range(SIZE):
        for x in range(SIZE):
            inset = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if inset == 0:
                t_ = 0.24
            elif inset == 1:
                t_ = 0.78 if (x < SIZE / 2 and y < SIZE / 2) else 0.34
            else:
                t_ = 0.52 + 0.18 * (vnoise(seed, x, y, 4) - 0.5)
            rgb = pal.tone(min(1.0, max(0.0, t_)))
            if 4 <= x <= 11 and 4 <= y <= 9:
                edge = x in (4, 11) or y in (4, 9)
                rgb = pal.tone(0.20) if edge else mix(pal.tone(0.30) + (255,),
                                                      lamp_rgb + (255,), 0.55)[:3]
            put(px, x, y, rgb)
    return px


# --------------------------------------------------------------------------- palettes
#
# Chosen for the films rather than for Minecraft's existing stone. The castle is warm
# sandstone-grey — the old set was a neutral cold grey, which is most of why it read as
# generic dungeon rather than as Hogwarts. Gringotts is ivory and brass. The Ministry is a
# near-black polished stone with a green cast, gold, and dark wood.

# The unmarked half of prop sets whose other faces block_textures.py already generates.
# Palettes are sampled from those siblings so the faces of one block agree.
BRASS_POT = Palette("#8a7440", mortar="#4e4020", warm=1.0, spread=0.34)
PEWTER_POT = Palette("#7d838d", mortar="#4a505c", spread=0.32)
COPPER_POT = Palette("#a4653c", mortar="#5f3a22", warm=1.0, spread=0.34)
DESK_WOOD = Palette("#7f5f3a", mortar="#4e3b25", warm=1.0, spread=0.34)
BENCH_WOOD = Palette("#4a3320", mortar="#2b1d12", warm=1.0, spread=0.36)
CONFIGURATOR = Palette("#6a6f78", mortar="#3c4048", spread=0.34)
DEAD_GLOWSTONE = Palette("#5d5344", mortar="#3a332a", warm=1.0, spread=0.36)


HOGWARTS = Palette("#8b8478", mortar="#5d574d", warm=1.0, spread=0.34)
HOGWARTS_DARK = Palette("#4a463f", mortar="#2f2c27", warm=1.0, spread=0.36)
HOGWARTS_FLOOR = Palette("#7d766a", mortar="#4f4a42", warm=1.0, spread=0.32)

GRINGOTTS_MARBLE = Palette("#ddd3bd", mortar="#a89d86", warm=1.0, spread=0.24)
GRINGOTTS_PALE = Palette("#cfc3a8", mortar="#9b8f76", warm=1.0, spread=0.26)
GRINGOTTS_STONE = Palette("#7b7266", mortar="#4e483f", warm=1.0, spread=0.32)
GRINGOTTS_IRON = Palette("#6a6a72", mortar="#3d3d45", spread=0.34)
BRASS = GILT_HEX  # Gringotts gilding is the same gold as the Ministry's (visual families)

MINISTRY = Palette("#26282b", mortar="#14161a", spread=0.42)
MINISTRY_TILE = Palette("#2c3033", mortar="#15181b", spread=0.40)
MINISTRY_WOOD = Palette("#3a2a20", mortar="#241a13", warm=1.0, spread=0.36)
GOLD = GILT_HEX

DIAGON_BRICK = Palette("#8a4a38", mortar="#6b5c4e", warm=1.0, spread=0.34)
DIAGON_STONE = Palette("#7a7168", mortar="#4d463f", warm=1.0, spread=0.34)
DIAGON_GREEN = Palette("#2f5540", mortar="#1d3628", spread=0.34)
DIAGON_PURPLE = Palette("#4a3060", mortar="#2c1c3a", spread=0.34)
DIAGON_WOOD = Palette("#6b4a30", mortar="#432c1c", warm=1.0, spread=0.34)

HOGSMEADE = Palette("#7f8288", mortar="#4f5257", spread=0.32)
HOGSMEADE_SLATE = Palette("#4a5058", mortar="#2b3036", spread=0.36)
HOGSMEADE_BRICK = Palette("#7a4a3c", mortar="#5a4a40", warm=1.0, spread=0.34)

HONEYDUKES_PINK = Palette("#d99bb0", mortar="#b3768c", warm=1.0, spread=0.22)
HONEYDUKES_YELLOW = Palette("#e0c874", mortar="#b39c52", warm=1.0, spread=0.22)

BROOMSTICKS_PLANK = Palette("#7a5636", mortar="#4c3520", warm=1.0, spread=0.34)
BROOMSTICKS_TIMBER = Palette("#4a3324", mortar="#2c1d13", warm=1.0, spread=0.36)


# --------------------------------------------------------------------------- catalogue
#
# Filenames are exactly the ones already on disk, so nothing in the models or the block
# registry has to change: same names, different content.
#
# `posterise` count per entry. Vanilla, measured: stone 4, cobblestone 6, oak_planks 7,
# iron_ingot 8. Marble earns a couple more because a vein needs a mid-tone to not look
# like a scratch; flat masonry gets fewer.

TEXTURES = {
    # --- Hogwarts: warm castle stone ---------------------------------------
    "hogwarts_stone": (lambda: rough_stone(HOGWARTS, seed=101, contrast=1.7), 6),
    "hogwarts_dark_stone": (lambda: rough_stone(HOGWARTS_DARK, seed=102, grit=0.14), 6),
    "hogwarts_stone_bricks": (lambda: courses(HOGWARTS, seed=103), 7),
    "hogwarts_cracked_stone_bricks": (
        lambda: courses(HOGWARTS, seed=104, chip=0.26, crack=0.45), 7),
    "hogwarts_mossy_stone_bricks": (
        lambda: courses(HOGWARTS, seed=105, moss="#5c7038"), 8),
    "hogwarts_flagstone": (lambda: flagstone(HOGWARTS_FLOOR, seed=106), 7),
    "hogwarts_floor_tile": (lambda: tiles(HOGWARTS_FLOOR, seed=107, n=2), 7),
    "hogwarts_stone_pillar": (lambda: fluted(HOGWARTS, seed=108), 6),
    "hogwarts_stone_pillar_top": (lambda: pillar_top(HOGWARTS, seed=109), 6),

    # --- Gringotts: ivory marble and brass ---------------------------------
    "gringotts_white_marble": (lambda: marble(GRINGOTTS_MARBLE, seed=201,
                                              vein_colour="#a3947a", width=0.6, warp=3.2), 8),
    "gringotts_white_marble_tiles": (
        lambda: tiles(GRINGOTTS_MARBLE, seed=202, n=2, polish=0.5), 8),
    "gringotts_white_marble_pillar": (lambda: fluted(GRINGOTTS_MARBLE, seed=203), 7),
    "gringotts_white_marble_pillar_top": (
        lambda: pillar_top(GRINGOTTS_MARBLE, seed=204), 7),
    "gringotts_pale_marble": (lambda: marble(GRINGOTTS_PALE, seed=205, warp=5.0,
                                             vein_colour="#8d7f64", width=0.7), 8),
    "gringotts_gold_trim": (lambda: inlay(GRINGOTTS_MARBLE, BRASS, seed=206), 8),
    "gringotts_vault_bricks": (lambda: courses(GRINGOTTS_STONE, seed=207, bh=8, chip=0.2), 7),
    "gringotts_iron_vault_stone": (lambda: riveted(GRINGOTTS_IRON, seed=208), 7),
    "gringotts_goblin_stonework": (lambda: hewn(GRINGOTTS_STONE, seed=209), 7),
    "gringotts_counting_floor": (
        # A banking-hall chequer of pale and dark marble; the 4px tiles it replaced were a
        # busy grid that read as a floor pattern only up close.
        lambda: tiles(GRINGOTTS_PALE, seed=210, n=2, grout="#5b5245", alt=GRINGOTTS_STONE), 7),

    # --- Ministry: polished black, gold, dark wood -------------------------
    "ministry_black_marble": (lambda: marble(MINISTRY, seed=301, width=0.85, warp=4.5,
                                             vein_colour="#6b7a75"), 7),
    "ministry_black_marble_tiles": (lambda: tiles(MINISTRY, seed=302, n=2, polish=0.6), 7),
    "ministry_black_marble_pillar": (lambda: fluted(MINISTRY, seed=303), 6),
    "ministry_black_marble_pillar_top": (lambda: pillar_top(MINISTRY, seed=304), 6),
    "ministry_gilded_black_marble": (lambda: veined_gold(MINISTRY, GOLD, seed=305), 8),
    "ministry_gilded_trim": (lambda: inlay(MINISTRY, GOLD, seed=306, motif="chevron"), 8),
    "ministry_dark_tile": (lambda: tiles(MINISTRY_TILE, seed=307, n=4), 6),
    "ministry_floor_tile": (lambda: tiles(MINISTRY_TILE, seed=308, n=2, polish=0.7), 7),
    "ministry_wall_panel": (lambda: panelling(MINISTRY_WOOD, seed=309), 7),

    # --- Diagon Alley: warm brick and painted shopfronts -------------------
    "diagon_brick": (lambda: courses(DIAGON_BRICK, seed=401, bh=4), 7),
    "diagon_worn_brick": (
        lambda: courses(DIAGON_BRICK, seed=402, chip=0.30, crack=0.35, soot=0.5), 8),
    "diagon_brick_tiles": (lambda: tiles(DIAGON_BRICK, seed=403, n=4), 7),
    "diagon_cobblestone": (lambda: cobbles(DIAGON_STONE, seed=404), 7),
    "diagon_street_stone": (lambda: cobbles(DIAGON_STONE, seed=405, cells=4), 7),
    "diagon_painted_wood_green": (
        lambda: painted_boards(DIAGON_GREEN, DIAGON_WOOD, seed=406), 7),
    "diagon_painted_wood_purple": (
        lambda: painted_boards(DIAGON_PURPLE, DIAGON_WOOD, seed=407), 7),
    "diagon_shopfront_planks": (lambda: planks(DIAGON_WOOD, seed=408, rows=4), 7),
    "diagon_shopfront_wood": (lambda: timber(DIAGON_WOOD, seed=409), 6),

    # --- Hogsmeade: cold stone and slate -----------------------------------
    "hogsmeade_stone": (lambda: rough_stone(HOGSMEADE, seed=501), 6),
    "hogsmeade_stone_bricks": (lambda: courses(HOGSMEADE, seed=502), 7),
    "hogsmeade_worn_stone": (
        lambda: courses(HOGSMEADE, seed=503, chip=0.32, crack=0.4), 7),
    "hogsmeade_chimney_brick": (
        lambda: courses(HOGSMEADE_BRICK, seed=504, soot=0.7), 7),
    "hogsmeade_roof_tile": (lambda: roof_tiles(HOGSMEADE_SLATE, seed=505), 7),

    # --- Honeydukes and the Three Broomsticks ------------------------------
    "honeydukes_pastel_pink": (
        lambda: plaster(HONEYDUKES_PINK, seed=601, stripe="#f2c9d6"), 6),
    "honeydukes_pastel_yellow": (
        lambda: plaster(HONEYDUKES_YELLOW, seed=602, stripe="#f4e4a8"), 6),
    "three_broomsticks_planks": (lambda: planks(BROOMSTICKS_PLANK, seed=701, rows=4,
                                                nails="#3a3128"), 7),
    "three_broomsticks_timber": (lambda: timber(BROOMSTICKS_TIMBER, seed=702), 6),

    # --- prop faces left unmarked when their siblings were generated -------
    "brass_cauldron_side": (lambda: metal_vessel(BRASS_POT, band="#4e7558"), 7),
    "pewter_cauldron_side": (lambda: metal_vessel(PEWTER_POT, band="#566273"), 7),
    "wizarding_copper_cauldron_side": (
        lambda: metal_vessel(COPPER_POT, band="#674c8f"), 7),
    "examination_desk": (lambda: planks(DESK_WOOD, seed=801, rows=4), 7),
    "wandmakers_bench": (lambda: planks(BENCH_WOOD, seed=802, rows=4, nails="#2b2118"), 7),
    "pocket_configurator": (lambda: device_panel(CONFIGURATOR, seed=803, lamp="#8fd6c2"), 7),
    "unlit_glowstone": (lambda: dimmed("glowstone", DEAD_GLOWSTONE, seed=804), 6),
}



# --------------------------------------------------------------------------- driver
#
# These files were committed without a generator marker, so `is_regenerable` reads them as
# hand art and refuses. They are not hand art — they are the uncomposed first cut this tool
# replaces — so the adoption is stated here rather than worked around silently. Every one is
# tracked in git, so `git restore` brings the old set back. After the first run they carry
# our marker and the ordinary rule applies again.

def may_write(path, force):
    if force or not os.path.exists(path):
        return True
    if is_generated(path, MARKER):
        return True
    if is_generated(path):
        return False            # another tool's output is never ours to take
    return os.path.basename(path)[:-4] in TEXTURES


def vein_pixels(img, pal):
    """How many pixels sit far enough from the stone's own tone to read as a vein."""
    base = pal.base
    n = 0
    for r, g, b, _ in img.convert("RGBA").get_flattened_data():
        if abs(r - base[0]) + abs(g - base[1]) + abs(b - base[2]) > 48:
            n += 1
    return n


def to_image(px):
    img = Image.new("RGBA", (SIZE, SIZE))
    img.putdata([px[y][x] for y in range(SIZE) for x in range(SIZE)])
    return img


# Marbles, and the palette each is drawn on, so the run can prove its veins came through.
VEINED = {
    "gringotts_white_marble": GRINGOTTS_MARBLE,
    "gringotts_pale_marble": GRINGOTTS_PALE,
    "ministry_black_marble": MINISTRY,
    "ministry_gilded_black_marble": MINISTRY,
}


def keep_gilt(raw, img):
    """Put back every texel drawn in an exact gilt tone that posterise moved."""
    a, b = raw.convert("RGBA").load(), img.load()
    for y in range(raw.height):
        for x in range(raw.width):
            if a[x, y][:3] in GILT_TONES:
                b[x, y] = a[x, y]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    if args.list:
        for name in sorted(TEXTURES):
            print(name)
        print(f"\n{len(TEXTURES)} textures")
        return 0

    unknown = only - set(TEXTURES)
    if unknown:
        raise SystemExit(f"not in the catalogue: {', '.join(sorted(unknown))}")

    written, skipped = [], []
    for name, (surface, colours) in sorted(TEXTURES.items()):
        if only and name not in only:
            continue
        path = os.path.join(BLOCK_DIR, name + ".png")
        if not may_write(path, args.force):
            skipped.append(name)
            continue
        raw = to_image(surface())
        img = posterise(raw, colours)
        keep_gilt(raw, img)
        pal = VEINED.get(name)
        if pal is not None:
            seen = vein_pixels(img, pal)
            if seen < 12:
                raise SystemExit(
                    f"{name}: only {seen} vein pixels survived posterise — the veining is "
                    f"invisible. Raise `veins`, `vein_strength`, or the contrast of its "
                    f"vein colour against the base.")
        save(img, path, MARKER)
        written.append(name)

    print(f"wrote {len(written)} location block textures")
    if skipped:
        print(f"skipped {len(skipped)} not ours: {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
