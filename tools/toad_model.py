#!/usr/bin/env python3
"""The Toad: rig, skin and animation.

The Hogwarts letter's permitted pet — "an owl OR a cat OR a toad" — and Neville's Trevor. It is a
toad, so it is drawn as one: squat and wide, warty olive-brown, eyes bulging up off the top of the
skull, short forelegs planted, big hind legs folded under it ready to jump, and a throat that
swells when it croaks.

Run from the repo root:  python tools/toad_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "toad"
HITBOX = (0.45, 0.45)

SKIN_ = hx("#7A7040")
WART = hx("#56502A")
BELLY = hx("#C8BC8A")
EYE = hx("#D8A83A")
PUPIL = hx("#141008")
THROAT = hx("#D8C89A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 3, 0), (-12, 0, 0))
    rig.cube("body", (-3.5, 1, -3), (7, 4, 7), key="body")
    rig.bone("head", "body", (0, 3, -3))
    rig.cube("head", (-3, 1, -7), (6, 3, 4), key="head")
    rig.pair(lambda side, sign: rig.cube("head", Rig.mirror((1, 4, -6), (2, 2, 2), sign), (2, 2, 2), key=f"eye_{side}"))
    rig.bone("throat", "head", (0, 1, -5))
    rig.cube("throat", (-2, 0, -6), (4, 1, 3), key="throat")
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"arm_{side}", "body", (sign * 3, 2, -3), (-10, 0, sign * -10))
        rig.cube(f"arm_{side}", Rig.mirror((2.5, 0, -3.5), (1, 2, 1), sign), (1, 2, 1))
        rig.bone(f"leg_{side}", "body", (sign * 3, 2, 3), (0, sign * -20, 0))
        rig.cube(f"leg_{side}", Rig.mirror((3, 0, 0), (2, 2, 5), sign), (2, 2, 5))
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, bottom=BELLY, dither=0.3, dither_colour=WART)
    skin.skin("throat", THROAT, dither=0.0)
    for key in rig.keys("eye_"):
        skin.skin(key, EYE, dither=0.0)
        skin.mark(key, "north", 0, 1, PUPIL, w=2)
    return skin


def anims():
    idle = {"throat": {"scale": kf((0.0, (1, 1, 1)), (0.4, (1.1, 2.0, 1.1)), (0.8, (1, 1, 1)), (3.0, (1, 1, 1)))},
            "body": {"scale": kf((0.0, (1, 1, 1)), (1.5, (1.02, 1.04, 1.02)), (3.0, (1, 1, 1)))}}
    walk = {"root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 2.5, -1.5)), (0.4, (0, 0, -3)), (0.5, (0, 0, -3)))},
            "leg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (60, 0, 0)), (0.4, (0, 0, 0)), (0.5, (0, 0, 0)))},
            "leg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (60, 0, 0)), (0.4, (0, 0, 0)), (0.5, (0, 0, 0)))}}
    call = {"throat": {"scale": kf((0.0, (1, 1, 1)), (0.2, (1.6, 3.5, 1.6)), (0.45, (1, 1, 1)), (0.65, (1.6, 3.5, 1.6)),
                                   (0.9, (1, 1, 1)))},
            "body": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-6, 0, 0)), (0.9, (0, 0, 0)))}}
    flinch = {"root": {"position": kf((0.0, (0, 0, 0)), (0.12, (0, 3, 1)), (0.3, (0, 0, 1.5)), (0.4, (0, 0, 1.5)))}}
    return {"idle": clip(3.0, idle), "walk": clip(0.5, walk),
            "call": clip(0.9, call, loop=False), "flinch": clip(0.4, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "toad_model.py", build, paint, anims, HITBOX))
