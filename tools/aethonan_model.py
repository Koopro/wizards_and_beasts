#!/usr/bin/env python3
"""The Aethonan: rig, skin and animation.

Canon: the chestnut winged horse common in Britain and Ireland. `tools/winged_horse.py` at 1.0x —
the baseline of the three breeds — in a warm chestnut with a darker mane and brown-barred wings.

Run from the repo root:  python tools/aethonan_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip  # noqa: E402
from winged_horse import horse_clips, paint_horse, winged_horse  # noqa: E402

CID = "aethonan"
HITBOX = (1.7, 1.9)


def build():
    rig = Rig(CID, 128)
    a = winged_horse(rig, 1.0)
    ground(rig)
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    paint_horse(skin, rig, a, coat=hx("#8E4E26"), coat_dark=hx("#6A3818"), mane=hx("#4A2612"),
                mane_dark=hx("#2E170A"), feather=hx("#A8744A"), feather_dark=hx("#6A4428"),
                hoof=hx("#2A201A"), eye=hx("#1A120C"))
    return skin


def anims(a):
    idle, fly, strike, call = horse_clips(a, fly_len=1.0)
    return {"idle": clip(4.0, idle), "fly": clip(1.0, fly),
            "strike": clip(1.0, strike, loop=False), "call": clip(1.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "aethonan_model.py", build, paint, anims, HITBOX))
