#!/usr/bin/env python3
"""The Abraxan: rig, skin and animation.

Canon: the immense palomino winged horses that pull the Beauxbatons carriage — elephant-sized,
drinking only single-malt whisky. The largest of the winged-horse breeds, 80 health and a
2.7 x 2.9 box here. `tools/winged_horse.py` at 1.5x: the same animal as the Aethonan and the
Granian, bigger and gold, with pale wings and a white mane.

Run from the repo root:  python tools/abraxan_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip  # noqa: E402
from winged_horse import horse_clips, paint_horse, winged_horse  # noqa: E402

CID = "abraxan"
HITBOX = (2.7, 2.9)


def build():
    rig = Rig(CID, 128)
    a = winged_horse(rig, 1.5)
    ground(rig)
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    paint_horse(skin, rig, a, coat=hx("#D8A850"), coat_dark=hx("#B08434"), mane=hx("#F4EEDC"),
                mane_dark=hx("#CFC6AA"), feather=hx("#F0E6C8"), feather_dark=hx("#C8B684"),
                hoof=hx("#5A4526"), eye=hx("#6A2A1A"))
    return skin


def anims(a):
    idle, fly, strike, call = horse_clips(a, fly_len=1.3, idle_len=5.0)
    return {"idle": clip(5.0, idle), "fly": clip(1.3, fly),
            "strike": clip(1.0, strike, loop=False), "call": clip(1.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "abraxan_model.py", build, paint, anims, HITBOX))
