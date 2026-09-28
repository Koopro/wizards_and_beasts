#!/usr/bin/env python3
"""The Granian: rig, skin and animation.

Canon: the grey winged horse, particularly swift. `tools/winged_horse.py` at 1.0x with a longer,
flatter neck carriage — speed is posture — in dappled grey with a silver mane and pale wings, and
a quicker wing beat than its chestnut cousin.

Run from the repo root:  python tools/granian_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip  # noqa: E402
from winged_horse import horse_clips, paint_horse, winged_horse  # noqa: E402

CID = "granian"
HITBOX = (1.7, 1.9)
COAT = hx("#9CA0A6")
DAPPLE = hx("#7A7E86")


def build():
    rig = Rig(CID, 128)
    a = winged_horse(rig, 1.0, neck_rake=48, tail_len=13)
    ground(rig)
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    paint_horse(skin, rig, a, coat=COAT, coat_dark=DAPPLE, mane=hx("#D6DAE0"),
                mane_dark=hx("#A8AEB8"), feather=hx("#DCE0E6"), feather_dark=hx("#9CA2AE"),
                hoof=hx("#3A3C42"), eye=hx("#1C1E22"))
    for key in ("chest", "barrel", "croup"):
        skin.dither(skin.face(key, "east"), DAPPLE, 0.25, 3, grain="fleck")
        skin.dither(skin.face(key, "west"), DAPPLE, 0.25, 5, grain="fleck")
    return skin


def anims(a):
    idle, fly, strike, call = horse_clips(a, fly_len=0.8)
    return {"idle": clip(4.0, idle), "fly": clip(0.8, fly),
            "strike": clip(1.0, strike, loop=False), "call": clip(1.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "granian_model.py", build, paint, anims, HITBOX))
