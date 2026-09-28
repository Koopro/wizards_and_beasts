#!/usr/bin/env python3
"""The Diricawl: rig, skin and animation.

Canon: a plump, fluffy-feathered, flightless bird of Mauritius that escapes danger by vanishing in
a puff of feathers and reappearing elsewhere — the phoenix of the Dodo; Muggles think it extinct
because they never saw where it went. FEARFUL, `blink_away`, and GROUND locomotion.

It is a dodo: a heavy round body on short yellow legs, a large hooked beak nearly as big as the
head, stub wings that cannot lift it, and a curled tuft for a tail. The `flinch` clip is the
vanish — a sudden puff outward, then back.

Run from the repo root:  python tools/diricawl_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import bird, bird_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "diricawl"
HITBOX = (0.65, 0.75)

DOWN = hx("#8C8A84")
DOWN_DARK = hx("#6A6862")
DOWN_LIT = hx("#B4B2AC")
BEAK = hx("#D8C47A")
BEAK_TIP = hx("#5A4A2A")
LEG = hx("#E0C04A")
TUFT = hx("#F0EEE6")
EYE = hx("#E8D84A")


def build():
    rig = Rig(CID, 64)
    a = bird(rig, leg_h=4, body=(8, 8, 10), head=(5, 5, 5), beak=(3, 3, 4),
             neck=(3, 3, 3), neck_rake=-10, tail=(4, 3, 2), tail_lift=40,
             legs=[("", 2, 2, 2, None), ("foot", 2, 3, 3, None)], leg_x=2,
             wings=((5, 4, 1), (3, 3, 1)))
    rig.cube("beak", (-1.5, a.head_y - 1, a.head_front - 4), (3, 2, 2), key="hook")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), DOWN, top=DOWN_LIT, dither=0.25, dither_colour=DOWN_DARK)
    skin.skin(["beak", "hook"], BEAK, dither=0.0)
    skin.skin("hook", BEAK_TIP, dither=0.0)
    skin.skin(rig.keys("leg_"), LEG, dither=0.0)
    skin.skin("tail", TUFT, dither=0.1, dither_colour=DOWN_LIT)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, EYE)
    return skin


def anims(a):
    idle, walk, _fly = bird_loops(a, idle_len=3.4, walk_len=0.6, stride=34.0)
    walk["body"] = {"rotation": kf((0.0, (0, 0, 8)), (0.3, (0, 0, -8)), (0.6, (0, 0, 8)))}
    walk["wing_left"] = {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 20)), (0.6, (0, 0, 0)))}
    walk["wing_right"] = {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -20)), (0.6, (0, 0, 0)))}
    # `flinch` is the vanish: every feather puffs out at once and the bird collapses into it.
    flinch = {
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.08, (1.4, 1.4, 1.4)), (0.2, (0.1, 0.1, 0.1)),
                             (0.5, (0.1, 0.1, 0.1)), (0.7, (1, 1, 1)))},
    }
    call = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.9, (-16, 0, 0)), (1.2, (0, 0, 0)))},
        "beak": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (12, 0, 0)), (0.6, (0, 0, 0)), (0.8, (12, 0, 0)),
                                (1.2, (0, 0, 0)))},
    }
    return {"idle": clip(3.4, idle), "walk": clip(0.6, walk),
            "flinch": clip(0.7, flinch, loop=False), "call": clip(1.2, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "diricawl_model.py", build, paint, anims, HITBOX))
