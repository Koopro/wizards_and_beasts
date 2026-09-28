#!/usr/bin/env python3
"""The Jobberknoll: rig, skin and animation.

Canon: a tiny blue speckled bird that makes no sound at all until the moment of its death, when it
lets out a long scream of every sound it has ever heard, backwards; its feathers go into Truth
Serums and Memory Potions. FEARFUL, `death_cry`.

Silent, so it has no ambient call clip — declaring one would give a bird whose whole canon is
silence a noise beat. A small, neat songbird shape in speckled blue, with the only reaction clip
a startled flinch.

Run from the repo root:  python tools/jobberknoll_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import bird, bird_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "jobberknoll"
HITBOX = (0.45, 0.45)

BLUE = hx("#4C7AC8")
BLUE_DARK = hx("#2E4E8A")
SPECK = hx("#8EAEE0")
BELLY = hx("#9CB8E0")
BEAK = hx("#2A2A30")


def build():
    rig = Rig(CID, 64)
    a = bird(rig, leg_h=2, body=(4, 4, 6), head=(4, 4, 4), beak=(1, 1, 2), tail=(3, 1, 5),
             tail_lift=12, legs=[("", 1, 1, 1, None), ("foot", 1, 2, 2, None)], leg_x=1,
             wings=((5, 3, 1), (4, 3, 1)))
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), BLUE, bottom=BELLY, dither=0.14, dither_colour=SPECK)
    skin.skin(rig.keys("wing_") + ["tail"], BLUE_DARK, dither=0.12, dither_colour=SPECK)
    skin.skin(["beak"] + rig.keys("leg_"), BEAK, dither=0.0)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, BEAK)
    return skin


def anims(a):
    idle, _walk, fly = bird_loops(a, idle_len=2.4, fly_len=0.3, spread=80.0, amp=46.0)
    flinch = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.08, (-20, 0, 0)), (0.3, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.08, (0, 1, 0.8)), (0.3, (0, 0, 0)))},
        "wing_left": {"rotation": kf((0.0, (0, 0, 0)), (0.08, (0, 0, 70)), (0.3, (0, 0, 0)))},
        "wing_right": {"rotation": kf((0.0, (0, 0, 0)), (0.08, (0, 0, -70)), (0.3, (0, 0, 0)))},
    }
    return {"idle": clip(2.4, idle), "fly": clip(0.3, fly), "flinch": clip(0.3, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "jobberknoll_model.py", build, paint, anims, HITBOX))
