#!/usr/bin/env python3
"""The Grindylow: rig, skin and animation.

Canon: a small, horned, pale-green water demon found in British and Irish lakes, feeding on small
fish and aggressive to wizards and Muggles alike; its long fingers grip hard but are easily
broken. The Hogwarts lake is full of them. AMPHIBIOUS, `constrict`.

Pale green, sharp little horns, and fingers: long spindly hands are the grab that `constrict` is,
so the hands are the biggest detail on the body. The swim clip is a frog-kick with a long arm
stroke; the idle is a float.

Run from the repo root:  python tools/grindylow_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, ground, pair_horn  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "grindylow"
HITBOX = (0.65, 0.75)

SKIN_ = hx("#8FB894")
SKIN_DARK = hx("#5E8466")
BELLY = hx("#C4DCC0")
HORN = hx("#E0DCC4")
EYE = hx("#D8F04A")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=5, pelvis=(4, 3, 3), torso=(5, 5, 3), head=(6, 5, 5), hunch=20,
              legs=[("", 3, 2, 2, None), ("foot", 2, 2, 3, None)],
              arms=[("", 4, 1, 1, (0, 0, -10)), ("fore", 4, 1, 1, (-20, 0, 0)), ("hand", 3, 3, 1, None)],
              arm_x=3, leg_x=1)
    pair_horn(rig, "head", name="horn", base=(2, a.head_top - 1, a.head_back - 2), size=(1, 2, 1),
              tip=(1, 2, 1), rotation=(-30, 0, -20))
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, bottom=BELLY, dither=0.12, dither_colour=SKIN_DARK)
    skin.rect(skin.face("torso", "north"), BELLY)
    skin.skin(rig.keys("horn_"), HORN, dither=0.0)
    for key in [k for k in rig.keys("arm_") if "hand" in k]:
        skin.bands(key, SKIN_DARK, faces=("north", "south"), step=2)   # the long fingers
    skin.eyes("head", EYE, row=1, inset=1, glow=True)
    x, y, w, h = skin.face("head", "north")
    skin.hline((x, y, w, h), h - 1, SKIN_DARK, inset=1)
    return skin


def anims(a):
    idle = {
        "root": {"position": kf((0.0, (0, 0, 0)), (1.5, (0, 0.8, 0)), (3.0, (0, 0, 0)))},
        "arm_left": osc(3.0, 10.0, axis="z"),
        "arm_right": osc(3.0, 10.0, axis="z", phase=0.5),
        "leg_left": osc(3.0, 8.0, phase=0.25),
        "leg_right": osc(3.0, 8.0, phase=0.75),
    }
    swim = {
        "chest": {"rotation": kf((0.0, (40, 0, 0)), (0.9, (40, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (-150, 0, 0)), (0.45, (20, 0, -30)), (0.9, (-150, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (-150, 0, 0)), (0.45, (20, 0, 30)), (0.9, (-150, 0, 0)))},
        "leg_left": {"rotation": kf((0.0, (30, 0, -20)), (0.45, (-10, 0, 0)), (0.9, (30, 0, -20)))},
        "leg_right": {"rotation": kf((0.0, (30, 0, 20)), (0.45, (-10, 0, 0)), (0.9, (30, 0, 20)))},
    }
    attack = {
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-100, 0, -30)), (0.35, (-60, 0, 10)), (0.6, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-100, 0, 30)), (0.35, (-60, 0, -10)), (0.6, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0.5, -1.5)), (0.6, (0, 0, 0)))},
    }
    flinch = {"chest": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 0)), (0.4, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (0, 0, 1.5)), (0.4, (0, 0, 0)))}}
    return {"idle": clip(3.0, idle), "swim": clip(0.9, swim),
            "attack": clip(0.6, attack, loop=False), "flinch": clip(0.4, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "grindylow_model.py", build, paint, anims, HITBOX))
