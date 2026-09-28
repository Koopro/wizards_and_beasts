#!/usr/bin/env python3
"""The Leprechaun: rig, skin and animation.

Canon: Irish, small, green, and sentient enough to speak, though not a "being"; it makes a
realistic gold-like substance that vanishes after a few hours (Ireland's World Cup mascots
showered the crowd with it). THIEF, `bioluminescence`.

Green all over, a red-gold beard, and a hat with a buckle — the one piece of folklore costume
worth keeping because it is what makes a small green man a *leprechaun* at forty blocks. The
leprechaun gold is a coin in its hand, and it glows.

Run from the repo root:  python tools/leprechaun_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "leprechaun"
HITBOX = (0.65, 0.75)

GREEN = hx("#3E8A3A")
GREEN_DARK = hx("#2A6428")
SKIN_ = hx("#9CC08A")
BEARD = hx("#C8702C")
BEARD_DARK = hx("#944E1E")
GOLD = hx("#F2C83A")
BUCKLE = hx("#D8B030")
EYE = hx("#18261A")
SHOE = hx("#2A2218")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=5, pelvis=(5, 3, 3), torso=(6, 5, 4), head=(6, 6, 5),
              legs=[("", 3, 2, 2, None), ("foot", 2, 2, 3, None)],
              arms=[("", 3, 2, 2, (0, 0, -8)), ("fore", 3, 2, 2, (-12, 0, 0)),
                    ("hand", 1, 2, 2, None)],
              arm_x=4, leg_x=1.5)
    rig.cube("head", (-3, a.head_y - 2, a.head_front - 1), (6, 4, 2), key="beard")
    rig.bone("hat", "head", (0, a.head_top, 0), (0, 0, -6))
    rig.cube("hat", (-4, a.head_top, a.head_front - 1), (8, 1, 7), key="hat_brim")
    rig.cube("hat", (-3, a.head_top + 1, a.head_front), (6, 3, 5), key="hat_crown")
    rig.cube("arm_right_hand", (-5, a.shoulder - 9, -1.5), (2, 2, 1), key="coin")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), GREEN, dither=0.08, dither_colour=GREEN_DARK)
    skin.skin("head", SKIN_, dither=0.0)
    skin.eyes("head", EYE, row=2, inset=1)
    skin.skin("beard", BEARD, dither=0.2, dither_colour=BEARD_DARK)
    skin.skin([k for k in rig.keys("leg_") if "foot" in k], SHOE, dither=0.0)
    skin.skin(["hat_brim", "hat_crown"], GREEN_DARK, dither=0.0)
    x, y, w, h = skin.face("hat_crown", "north")
    skin.hline((x, y, w, h), h - 1, SHOE)
    skin.rect((x + w // 2 - 1, y + h - 2, 2, 2), BUCKLE)
    skin.skin("coin", GOLD, dither=0.0)
    skin.glow("coin")
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.0, walk_len=0.5, stride=36.0)
    idle["arm_right_fore"] = {"rotation": kf((0.0, (-40, 0, 0)), (3.0, (-40, 0, 0)))}  # coin shown
    flinch = {
        "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (0, 1.2, 1.5)), (0.4, (0, 0, 0)))},
        "hat": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 20)), (0.4, (0, 0, 0)))},
    }
    # `call` is the jig: hop, kick, hop, hat tipped.
    call = {
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 1.5, 0)), (0.4, (0, 0, 0)),
                                (0.6, (0, 1.5, 0)), (0.8, (0, 0, 0)))},
        "leg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-40, 0, 0)), (0.4, (0, 0, 0)),
                                    (0.8, (0, 0, 0)))},
        "leg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 0)), (0.6, (-40, 0, 0)),
                                     (0.8, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-160, 0, 0)), (0.8, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.5, walk),
            "flinch": clip(0.4, flinch, loop=False), "call": clip(0.8, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "leprechaun_model.py", build, paint, anims, HITBOX))
