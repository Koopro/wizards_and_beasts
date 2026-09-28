#!/usr/bin/env python3
"""The Clabbert: rig, skin and animation.

Canon: a tree-dwelling creature from the southern United States, something between a monkey and
a frog — smooth, hairless, mottled green skin, webbed hands and feet, long supple limbs, a wide
frog mouth, and a large pustule in the middle of its forehead that glows scarlet when it senses
danger. Wizards once kept them as intruder alarms. `danger_sense` and `bioluminescence` here.

The pustule is the creature: it is the one glowing texel group on the model and it sits dead
centre of the brow, above a frog's wide lipless mouth, on a monkey's crouched long-armed frame.

Run from the repo root:  python tools/clabbert_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "clabbert"
HITBOX = (0.65, 0.75)

SKIN_ = hx("#6E9A52")
MOTTLE = hx("#4B7438")
BELLY = hx("#BCCB8C")
PUSTULE = hx("#E03A2C")
EYE = hx("#E2C83A")
MOUTH = hx("#2E3A22")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=6, pelvis=(5, 3, 3), torso=(6, 6, 4), head=(7, 5, 6), hunch=18,
              legs=[("", 3, 2, 2, (-20, 0, 0)), ("shin", 2, 2, 2, (30, 0, 0)),
                    ("foot", 1, 3, 3, (-10, 0, 0))],
              arms=[("", 4, 2, 2, (0, 0, -10)), ("fore", 4, 2, 2, (-14, 0, 0)),
                    ("hand", 2, 3, 2, None)],
              arm_x=4, leg_x=1.5)
    rig.cube("head", (-1, a.head_y + 3, a.head_front - 1), (2, 2, 1), key="pustule")
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((2, a.head_top - 1, a.head_front + 1), (2, 2, 2), sign), (2, 2, 2),
        key=f"eye_{side}"))
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, bottom=BELLY, dither=0.2, dither_colour=MOTTLE)
    skin.rect(skin.face("torso", "north"), BELLY)
    skin.skin(rig.keys("eye_"), EYE, dither=0.0)
    for key in rig.keys("eye_"):
        skin.mark(key, "north", 0, 1, MOUTH, w=2)
    skin.skin("pustule", PUSTULE, dither=0.0)
    skin.glow("pustule")
    x, y, w, h = skin.face("head", "north")
    skin.hline((x, y, w, h), h - 2, MOUTH)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.0, walk_len=0.6, stride=34.0, bob_amp=0.5)
    # `flinch`: it springs back and up, arms flung wide, like a startled tree frog.
    flinch = {
        "root": {"position": kf((0.0, (0, 0, 0)), (0.15, (0, 2, 1.5)), (0.5, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (0, 0, -60)), (0.5, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (0, 0, 60)), (0.5, (0, 0, 0)))},
    }
    # `call` is the alarm: head up, crouched still, the pustule presented to whatever it saw.
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-24, 0, 0)), (1.2, (-24, 0, 0)),
                                (1.5, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-10, 0, 0)), (1.5, (0, 0, 0)))},
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.3, (1.0, 1.06, 1.0)), (1.5, (1, 1, 1)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.6, walk),
            "flinch": clip(0.5, flinch, loop=False), "call": clip(1.5, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "clabbert_model.py", build, paint, anims, HITBOX))
