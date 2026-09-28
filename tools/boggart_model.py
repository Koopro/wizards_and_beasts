#!/usr/bin/env python3
"""The Boggart: rig, skin and animation.

Canon: a shape-shifter that takes the form of whatever most frightens the person looking at it; no
one knows what it looks like alone, because it changes the moment it is seen. It lives in dark
enclosed spaces — wardrobes, under beds, the desk in the staffroom. `boggart_dread`.

So the rig draws the only thing that can be drawn: the not-yet-shape, a slumped mass of shadow
leaking out of the dark, never quite the same outline — stacked irregular swells rotated against
each other, wisps rising off the top, and two pale points where it is looking at you. The idle
never settles, because it never has a shape to settle into.

Run from the repo root:  python tools/boggart_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "boggart"
HITBOX = (0.95, 1.25)

SHADOW = hx("#1E1A26")
SMOKE = hx("#3A3446")
WISP = hx("#5A5068")
EYE = hx("#E8E4F0")


def build():
    rig = Rig(CID, 64)
    rig.bone("mass", "root", (0, 0, 0))
    rig.cube("mass", (-7, 0, -6), (14, 6, 12), key="base", inflate=0.3)
    rig.bone("swell", "mass", (0, 6, 0), (0, 18, 0))
    rig.cube("swell", (-5, 5, -5), (10, 8, 10), key="swell")
    rig.bone("crown", "swell", (0, 13, -1), (0, -24, 6))
    rig.cube("crown", (-4, 12, -4.5), (7, 6, 7), key="crown")
    for i, (x, z, h, rz) in enumerate([(-3, 1, 5, 20), (2, -1, 6, -15), (0, 2, 4, 5)]):
        rig.bone(f"wisp_{i}", "crown", (x, 18, z), (0, 0, rz))
        rig.cube(f"wisp_{i}", (x - 1, 17, z - 1), (2, h, 2), key=f"wisp_{i}")
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SHADOW, dither=0.3, dither_colour=SMOKE, bevel=0)
    skin.skin(rig.keys("wisp_"), SMOKE, dither=0.3, dither_colour=WISP, bevel=0)
    x, y, w, h = skin.face("crown", "north")
    for ex in (x + 1, x + w - 2):
        skin.d.point((ex, y + 2), fill=EYE)
        skin.glow_rect((ex, y + 2, 1, 1))
    return skin


def anims():
    churn = {
        "swell": {"rotation": kf((0.0, (0, 0, 0)), (1.2, (4, 14, -4)), (2.4, (-4, -10, 4)), (3.6, (0, 0, 0))),
                  "scale": kf((0.0, (1, 1, 1)), (1.2, (1.08, 0.94, 1.04)), (2.4, (0.95, 1.08, 0.96)), (3.6, (1, 1, 1)))},
        "crown": {"rotation": kf((0.0, (0, 0, 0)), (0.9, (-6, -16, 6)), (2.1, (6, 12, -6)), (3.6, (0, 0, 0)))},
        **{f"wisp_{i}": {"rotation": kf((0.0, (0, 0, 0)), (1.0 + 0.3 * i, (10, 0, -12)), (3.6, (0, 0, 0)))} for i in range(3)},
    }
    walk = {"mass": {"rotation": kf((0.0, (0, 0, 6)), (0.5, (0, 0, -6)), (1.0, (0, 0, 6)))},
            "swell": {"rotation": kf((0.0, (10, 0, 0)), (0.5, (14, 8, 0)), (1.0, (10, 0, 0)))}}
    # `flinch` is Riddikulus: it recoils and shrinks, briefly the size of nothing at all.
    flinch = {"mass": {"scale": kf((0.0, (1, 1, 1)), (0.12, (0.5, 0.4, 0.5)), (0.8, (0.7, 0.6, 0.7)), (1.2, (1, 1, 1)))}}
    # `call` is the loom: it rises and spreads over whoever is closest.
    call = {"mass": {"scale": kf((0.0, (1, 1, 1)), (0.6, (1.25, 1.4, 1.25)), (1.6, (1.2, 1.35, 1.2)), (2.0, (1, 1, 1)))},
            "crown": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (26, 0, 0)), (2.0, (0, 0, 0)))}}
    return {"idle": clip(3.6, churn), "walk": clip(1.0, walk),
            "flinch": clip(1.2, flinch, loop=False), "call": clip(2.0, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "boggart_model.py", build, paint, anims, HITBOX))
