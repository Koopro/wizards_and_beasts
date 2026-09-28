#!/usr/bin/env python3
"""The Imp: rig, skin and animation.

Canon: found only in Britain and Ireland, often confused with pixies — of similar size, but
unable to fly and not as vividly coloured, usually dark brown to black. Crude practical jokes
are its whole personality. KNOCKBACK, `blink_away`.

At 0.45 blocks it is the smallest humanoid in the mod, so it is drawn at about ten units with a
head that is a third of that — the texel budget for a face that can grin. Swept-back pointed ears
and a mischief-bright pair of eyes are the only detail it can afford, so they get it.

Run from the repo root:  python tools/imp_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "imp"
HITBOX = (0.45, 0.45)

SKIN_ = hx("#4A3428")
SKIN_DARK = hx("#2E2018")
EYE = hx("#F2A43A")
GRIN = hx("#E8DCC8")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=3, pelvis=(3, 2, 2), torso=(4, 3, 3), head=(5, 5, 4),
              legs=[("", 2, 1, 1, None), ("foot", 1, 2, 2, None)],
              arms=[("", 2, 1, 1, (0, 0, -14)), ("hand", 1, 1, 1, None)],
              arm_x=2.5, leg_x=1)

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 2.5, a.head_y + 3, a.head_front + 2), (-20, sign * 30, sign * -30))
        rig.cube(f"ear_{side}", Rig.mirror((2.5, a.head_y + 2, a.head_front + 1.5), (3, 2, 1), sign),
                 (3, 2, 1))

    rig.pair(build_ear)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.1, dither_colour=SKIN_DARK)
    skin.eyes("head", EYE, row=1, inset=1, glow=True)
    skin.mark("head", "north", 1, 3, GRIN, w=3)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=2.0, walk_len=0.35, stride=44.0, bob_amp=0.4)
    idle["root"] = {"position": kf((0.0, (0, 0, 0)), (0.5, (0, 0.6, 0)), (1.0, (0, 0, 0)),
                                   (1.5, (0, 0.6, 0)), (2.0, (0, 0, 0)))}  # can't keep still
    flinch = {
        "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (0, 1.5, 1)), (0.35, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 12)), (0.35, (0, 0, 0)))},
    }
    # `call` is the snigger: doubled over, shaking.
    call = {
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (30, 0, 0)), (0.25, (22, 0, 0)),
                                 (0.35, (30, 0, 0)), (0.45, (22, 0, 0)), (0.7, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-40, 0, 20)), (0.7, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-40, 0, -20)), (0.7, (0, 0, 0)))},
    }
    return {"idle": clip(2.0, idle), "walk": clip(0.35, walk),
            "flinch": clip(0.35, flinch, loop=False), "call": clip(0.7, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "imp_model.py", build, paint, anims, HITBOX))
