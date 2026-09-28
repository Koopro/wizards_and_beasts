#!/usr/bin/env python3
"""The Pogrebin: rig, skin and animation.

Canon: a Russian demon about a foot tall with a hairy body and a smooth, grey, oversized head; it
crouches, looking like a shiny round rock, and follows travellers, filling them with a sense of
futility until they sit down and despair — at which point it eats them. Hostile, `dread_aura`,
`camouflage`.

The head is a rock: a polished grey dome as wide as the body, sitting on a small crouched hairy
frame whose arms wrap its knees. Crouched, it is a boulder; the idle is that stillness, broken
only by the eyes.

Run from the repo root:  python tools/pogrebin_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "pogrebin"
HITBOX = (0.65, 0.75)

HAIR = hx("#5E4E3E")
HAIR_DARK = hx("#3A3026")
STONE = hx("#9A9CA0")
STONE_LIT = hx("#C8CACE")
STONE_DARK = hx("#6E7074")
EYE = hx("#D8C84A")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=3, pelvis=(5, 2, 4), torso=(6, 4, 5), head=(9, 8, 8), hunch=24,
              legs=[("", 1, 2, 2, (-40, 0, 0)), ("foot", 2, 3, 3, (40, 0, 0))],
              arms=[("", 3, 2, 2, (-30, 0, -10)), ("hand", 2, 2, 2, (-20, 0, 0))],
              arm_x=3.5, leg_x=1.5)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), HAIR, dither=0.35, dither_colour=HAIR_DARK)
    skin.skin("head", STONE, top=STONE_LIT, dither=0.06, dither_colour=STONE_DARK)
    x, y, w, h = skin.face("head", "top")
    skin.rect((x + 2, y + 2, 2, 2), STONE_LIT)            # the polish highlight
    fx, fy, fw, fh = skin.face("head", "north")
    for ex in (fx + 2, fx + fw - 3):
        skin.d.point((ex, fy + fh - 3), fill=EYE)
        skin.glow_rect((ex, fy + fh - 3, 1, 1))
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=6.0, walk_len=0.8, stride=26.0)
    idle["head"] = {"rotation": kf((0.0, (0, 0, 0)), (4.5, (0, 0, 0)), (5.0, (0, 20, 0)), (5.6, (0, 20, 0)), (6.0, (0, 0, 0)))}
    del idle["chest"]
    attack = {"chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.4, (20, 0, 0)), (0.7, (0, 0, 0)))},
              "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-80, 0, -20)), (0.4, (0, 0, 0)))},
              "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-80, 0, 20)), (0.4, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, 2, -2)), (0.7, (0, 0, 0)))}}
    groan = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (14, 0, 0)), (1.8, (10, 0, 0)), (2.4, (0, 0, 0)))},
             "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (10, 0, 0)), (2.4, (0, 0, 0)))}}
    return {"idle": clip(6.0, idle), "walk": clip(0.8, walk),
            "attack": clip(0.7, attack, loop=False), "groan": clip(2.4, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "pogrebin_model.py", build, paint, anims, HITBOX))
