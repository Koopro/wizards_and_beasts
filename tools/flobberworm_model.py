#!/usr/bin/env python3
"""The Flobberworm: rig, skin and animation.

Canon: a ten-inch brown worm that lives in damp ditches, eats lettuce, and does nothing else —
"the most boring creature" that Hagrid's class spent weeks keeping alive. Neither end is its head.
Its mucus thickens potions.

So it is drawn exactly that plainly: a thick segmented brown worm with a slick highlight, no face,
both ends the same. It moves by inching — a hump running along the body — and does nothing else.

Run from the repo root:  python tools/flobberworm_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "flobberworm"
HITBOX = (0.45, 0.45)

BROWN = hx("#8A6A4A")
BROWN_DARK = hx("#64482E")
SLIME = hx("#B8A078")


def build():
    rig = Rig(CID, 64)
    segs = chain(rig, "seg", "root", start=(0, 1.5, -6), direction=1,
                 segments=[(3, 3, 3), (3, 3, 3), (3, 3, 3), (3, 3, 3), (2, 2, 2)],
                 rotations=[(0, 0, 0), (0, 12, 0), (0, -10, 0), (0, -8, 0), (0, 10, 0)])
    ground(rig)
    return rig, segs


def paint(rig, tex_h, segs):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(list(segs), BROWN, dither=0.08, dither_colour=BROWN_DARK)
    for key in segs:
        skin.hline(skin.face(key, "top"), 0, SLIME)
        for face in ("east", "west", "top"):
            skin.vline(skin.face(key, face), 0, BROWN_DARK)
    return skin


def anims(segs):
    idle = undulate(4.0, list(segs), amp=3.0, wave=0.2)
    walk = undulate(1.2, list(segs), amp=14.0, axis="x", wave=0.25)
    flinch = {s: {"rotation": kf((0.0, (0, 0, 0)), (0.15, (0, 25 if i % 2 else -25, 0)), (0.6, (0, 0, 0)))}
              for i, s in enumerate(segs)}
    return {"idle": clip(4.0, idle), "walk": clip(1.2, walk), "flinch": clip(0.6, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "flobberworm_model.py", build, paint, anims, HITBOX))
