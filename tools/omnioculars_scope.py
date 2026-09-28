#!/usr/bin/env python3
"""The Omnioculars scope mask: two overlapping circles, not one.

Vanilla's ``spyglass_scope.png`` is a single aperture, because a spyglass has one tube.
Omnioculars are binoculars, and the whole reason this file exists is that the difference
is the only thing a player ever sees of the item while they are using it.

Drawn analytically at full size rather than supersampled and pixelated. Every other
generator in this folder makes *pixel art* — a 16px sprite where a hard edge is the
point. This is a full-screen GUI mask: at 256px blown up to the height of the window, a
hard circle edge is a visible staircase, so the boundary gets a short alpha ramp instead.

Output: ``assets/wizards_and_beasts/textures/misc/omnioculars_scope.png``
"""

import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artgen_common as art

TOOL = "omnioculars_scope.py"
SIZE = 256

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "wizards_and_beasts",
    "textures", "misc", "omnioculars_scope.png",
)

# Two circles, a quarter of the square across, their centres pushed apart by slightly less
# than a radius. The union spans 0.93 of the square, so the view is not a keyhole, and it
# pinches to 51% of a lens height at the waist.
#
# The waist is the whole design. At the first geometry tried here — radius 0.290, offset
# 0.155 — the circles overlapped so heavily that the union was a single stadium: a wide
# porthole, indistinguishable from a spyglass that had simply been widened, which defeats
# the point of the texture existing. Pushing the centres apart until the notch is visible
# top and bottom is what makes a player read "binoculars" without being told.
RADIUS = 0.250
CENTRE_OFFSET = 0.215

# How far the aperture edge fades, as a fraction of the square. Three pixels at 256, which
# is roughly one screen pixel once the mask is blown up to window height.
FEATHER = 0.012

# An inner shadow just inside the glass, so the aperture has some depth instead of looking
# like a hole cut in card. Reaches this far in from the edge, at this strength.
VIGNETTE = 0.055
VIGNETTE_ALPHA = 0.55


def smoothstep(edge0, edge1, x):
    if edge1 == edge0:
        return 0.0 if x < edge0 else 1.0
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def build():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    pixels = img.load()

    for y in range(SIZE):
        # Normalised to the square, origin at its centre, so every constant above reads as
        # a fraction of the aperture rather than a pixel count.
        ny = (y + 0.5) / SIZE - 0.5
        for x in range(SIZE):
            nx = (x + 0.5) / SIZE - 0.5

            # Distance to whichever lens this pixel is nearest. Taking the minimum is what
            # unions the two circles: a pixel inside either one is inside the field.
            left = math.hypot(nx + CENTRE_OFFSET, ny)
            right = math.hypot(nx - CENTRE_OFFSET, ny)
            distance = min(left, right)

            # 0 well inside the glass, 1 out on the black. The mask is the alpha, so the
            # surround stays fully opaque and the aperture goes fully clear.
            edge = smoothstep(RADIUS - FEATHER, RADIUS + FEATHER, distance)

            inner = (1.0 - smoothstep(RADIUS - FEATHER - VIGNETTE,
                                      RADIUS - FEATHER, distance)) if distance < RADIUS else 0.0
            shadow = (1.0 - inner) * VIGNETTE_ALPHA * (1.0 - edge)

            alpha = edge + shadow * (1.0 - edge)
            pixels[x, y] = (0, 0, 0, int(round(max(0.0, min(1.0, alpha)) * 255)))

    return img


def main():
    marker = art.marker(TOOL)
    if not art.is_regenerable(OUT, marker):
        raise SystemExit("refusing to overwrite hand-drawn art at " + OUT)
    art.save(build(), OUT, marker)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
