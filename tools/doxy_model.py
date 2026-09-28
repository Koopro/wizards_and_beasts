#!/usr/bin/env python3
"""The Doxy: rig, skin and animation.

Canon: often mistaken for a fairy, but covered in thick black hair, with an extra pair of arms and
legs, shiny beetle-like wings, and two rows of sharp venomous teeth; it infests curtains (Grimmauld
Place). POISON_ATTACK, `blink_away`, `evasion`.

Every one of those is visible at seven inches because every one is geometry: six limbs, hard wing
cases that catch light, and a mouth that is mostly teeth. The fairy is its mirror image — pale,
two-limbed, glowing, gossamer — and the two are built to be told apart at a glance.

Run from the repo root:  python tools/doxy_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from insect import buzz, hover, wings  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "doxy"
HITBOX = (0.45, 0.45)

HAIR = hx("#1E1A1C")
HAIR_LIT = hx("#3A3438")
WING = hx("#2E4A3A")
WING_SHINE = hx("#7AC8A0")
TEETH = hx("#E8E2D0")
EYE = hx("#E83A2A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 6, 0))
    rig.cube("body", (-1.5, 4, -1), (3, 5, 2), key="torso")
    rig.bone("head", "body", (0, 9, 0))
    rig.cube("head", (-2, 9, -2), (4, 4, 3), key="head")
    rig.cube("head", (-1.5, 9, -2.5), (3, 1, 1), key="teeth")
    for pair, y in (("upper", 8), ("lower", 6)):
        for side, sign in (("left", 1), ("right", -1)):
            rig.bone(f"arm_{pair}_{side}", "body", (sign * 1.5, y, 0), (0, 0, sign * (-30 if pair == "upper" else -60)))
            rig.cube(f"arm_{pair}_{side}", Rig.mirror((1.5, y - 3, -0.5), (1, 3, 1), sign), (1, 3, 1))
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"leg_{side}", "body", (sign * 1, 4, 0), (10, 0, sign * -8))
        rig.cube(f"leg_{side}", Rig.mirror((0.5, 1, -0.5), (1, 3, 1), sign), (1, 3, 1))
    wings(rig, "body", x=0.5, y=8, z=1.5, size=(4, 1, 3), rest=40, yaw=20)
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), HAIR, top=HAIR_LIT, dither=0.25, dither_colour=HAIR_LIT)
    skin.skin(rig.keys("wing_"), WING, dither=0.0)
    for key in rig.keys("wing_"):
        skin.hline(skin.face(key, "top"), 0, WING_SHINE)
        skin.hline(skin.face(key, "bottom"), 0, WING_SHINE)
    skin.skin("teeth", TEETH, dither=0.0)
    skin.bands("teeth", HAIR, faces=("north",), step=2)
    skin.eyes("head", EYE, row=1, inset=0)
    return skin


def anims():
    idle = {**buzz(0.08, amp=30), "root": hover(0.96, 0.8),
            "arm_upper_left": {"rotation": kf((0.0, (0, 0, 0)), (0.48, (-20, 0, 0)), (0.96, (0, 0, 0)))},
            "arm_upper_right": {"rotation": kf((0.0, (0, 0, 0)), (0.48, (-20, 0, 0)), (0.96, (0, 0, 0)))}}
    fly = {**buzz(0.08, amp=36), "body": {"rotation": kf((0.0, (20, 0, 0)), (0.4, (20, 0, 0)))},
           "root": hover(0.4, 0.4)}
    bite = {**buzz(0.08, amp=30),
            "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 0)), (0.25, (20, 0, 0)), (0.4, (0, 0, 0)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0, -1.5)), (0.4, (0, 0, 0)))},
            "arm_upper_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-90, 0, 0)), (0.4, (0, 0, 0)))},
            "arm_upper_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-90, 0, 0)), (0.4, (0, 0, 0)))}}
    flinch = {**buzz(0.08, amp=30),
              "root": {"position": kf((0.0, (0, 0, 0)), (0.06, (-1.5, 1, 0.5)), (0.25, (0, 0, 0)))}}
    # A loop clip must repeat cleanly: the buzz is 0.08s, so the idle's length is a multiple of it.
    return {"idle": clip(0.08 * 12, idle), "fly": clip(0.08 * 5, fly),
            "bite": clip(0.4, bite, loop=False), "flinch": clip(0.25, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "doxy_model.py", build, paint, anims, HITBOX))
