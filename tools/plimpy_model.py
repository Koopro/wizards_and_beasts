#!/usr/bin/env python3
"""The Plimpy: rig, skin and animation.

Canon: a spherical, mottled fish distinguished by two long legs ending in heavily webbed feet; it
lives in deep lakes, prowls the bottom for water snails, and is not dangerous, though it nibbles
at swimmers' feet and clothes. Tying its legs in a knot sends it off to struggle and float away.

A ball on two legs is the whole silhouette: the cross-of-slabs sphere from the Puffskein, mottled
orange-brown, with long thin legs hanging from its underside and wide webbed paddles for feet, and
a stubby tail fin. It swims by kicking.

Run from the repo root:  python tools/plimpy_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, hang, sphere  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "plimpy"
HITBOX = (0.65, 0.75)

SKIN_ = hx("#C08A4A")
MOTTLE = hx("#7A5228")
BELLY = hx("#E8CC96")
WEB = hx("#8AB090")
EYE = hx("#141010")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 10, 0))
    ball = sphere(rig, "body", c=8, y0=6)
    rig.cube("body", (-0.5, 9, 5), (1, 4, 3), key="tail_fin")
    rig.pair(lambda side, sign: hang(rig, f"leg_{side}", "body", x=2, z=1, sign=sign, top=7,
                                     segments=[("", 5, 1, 1, (10, 0, 0)), ("foot", 2, 3, 4, (-10, 0, 0))]))
    ground(rig)
    return rig, ball


def paint(rig, tex_h, ball):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(ball + ["tail_fin"], SKIN_, bottom=BELLY, dither=0.22, dither_colour=MOTTLE)
    skin.skin(rig.keys("leg_"), MOTTLE, dither=0.0)
    skin.skin([k for k in rig.keys("leg_") if "foot" in k], WEB, dither=0.0)
    x, y, w, h = skin.face(ball[2], "north")
    skin.rect((x + 1, y + 1, 2, 2), EYE)
    skin.rect((x + w - 3, y + 1, 2, 2), EYE)
    skin.hline((x, y, w, h), h - 1, MOTTLE, inset=2)
    return skin


def anims(ball):
    idle = {"body": {"position": kf((0.0, (0, 0, 0)), (1.6, (0, 0.8, 0)), (3.2, (0, 0, 0)))},
            "leg_left": osc(3.2, 10.0), "leg_right": osc(3.2, 10.0, phase=0.5)}
    swim = {"leg_left": osc(0.6, 50.0), "leg_right": osc(0.6, 50.0, phase=0.5),
            "leg_left_foot": osc(0.6, 30.0, phase=0.2), "leg_right_foot": osc(0.6, 30.0, phase=0.7),
            "body": {"rotation": kf((0.0, (10, 0, 0)), (0.6, (10, 0, 0)))}}
    flinch = {"body": {"scale": kf((0.0, (1, 1, 1)), (0.1, (0.85, 1.15, 0.85)), (0.4, (1, 1, 1)))}}
    call = {"body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.6, (0, 0, 0)), (0.9, (-20, 0, 0)),
                                    (1.2, (0, 0, 0)))}}
    return {"idle": clip(3.2, idle), "swim": clip(0.6, swim),
            "flinch": clip(0.4, flinch, loop=False), "call": clip(1.2, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "plimpy_model.py", build, paint, anims, HITBOX))
