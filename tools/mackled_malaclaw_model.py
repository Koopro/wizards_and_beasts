#!/usr/bin/env python3
"""The Mackled Malaclaw: rig, skin and animation.

Canon: a land creature of rocky European coastlines that looks like a lobster, a foot long at
most, light grey with deep green spots; eating its flesh causes a fever and a green rash, and its
bite brings a week of bad luck. `status_on_hit`.

A lobster: long tail segments ending in a fan, a pair of big claws on arms, long antennae, and
small walking legs — in pale grey with dark green spots, the one colour scheme no real lobster has.

Run from the repo root:  python tools/mackled_malaclaw_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground  # noqa: E402
from crab import crab, scuttle  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "mackled_malaclaw"
HITBOX = (0.65, 0.75)

GREY = hx("#B8BCC0")
GREY_DARK = hx("#8A9096")
SPOT = hx("#2E5A3A")
EYE = hx("#141414")


def build():
    rig = Rig(CID, 64)
    legs = crab(rig, shell=(5, 4, 6), hip_y=3, leg=(1, 2, 4, 2, 1), leg_x=2)
    tail = chain(rig, "tail", "body", start=(0, 4, 3), direction=1,
                 segments=[(3, 4, 3), (3, 3, 2), (3, 3, 2)], rotations=[(-6, 0, 0), (-6, 0, 0), (-8, 0, 0)])
    rig.cube(tail[-1], (-2.5, 3.5, 12), (5, 1, 3), key="fan")
    claws = []
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"arm_{side}", "body", (sign * 2.5, 4, -3), (0, sign * -30, 0))
        rig.cube(f"arm_{side}", Rig.mirror((2, 3.5, -6), (1, 1, 3), sign), (1, 1, 3), key=f"arm_{side}")
        rig.bone(f"claw_{side}", f"arm_{side}", (sign * 2.5, 4, -6), (0, sign * 20, 0))
        rig.cube(f"claw_{side}", Rig.mirror((1.5, 3, -10), (2, 2, 4), sign), (2, 2, 4), key=f"claw_{side}")
        rig.cube(f"claw_{side}", Rig.mirror((2.5, 5, -9), (1, 1, 3), sign), (1, 1, 3), key=f"pincer_{side}")
        claws.append(f"claw_{side}")
        rig.bone(f"antenna_{side}", "body", (sign * 1, 5, -3), (-20, sign * -20, 0))
        rig.cube(f"antenna_{side}", Rig.mirror((0.5, 5, -10), (1, 1, 7), sign), (1, 1, 7), key=f"antenna_{side}")
    ground(rig)
    return rig, legs, tail, claws


def paint(rig, tex_h, legs, tail, claws):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), GREY, dither=0.0)
    for key in ["shell"] + list(tail) + claws:
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for i in range(0, max(1, w * h), 4):
                skin.d.point((x + (i * 5) % w, y + (i // max(1, w)) % h), fill=SPOT)
    skin.skin(rig.keys("leg_", "antenna_"), GREY_DARK, dither=0.0)
    x, y, w, h = skin.face("shell", "north")
    skin.d.point((x + 1, y), fill=EYE)
    skin.d.point((x + w - 2, y), fill=EYE)
    return skin


def anims(legs, tail, claws):
    idle = {"claw_left": osc(2.4, 8.0, axis="y"), "claw_right": osc(2.4, 8.0, axis="y", phase=0.5),
            "antenna_left": osc(2.4, 14.0, axis="y"), "antenna_right": osc(2.4, 14.0, axis="y", phase=0.3)}
    walk = {**scuttle(0.5, legs, amp=22.0), "arm_left": osc(0.5, 6.0, axis="y"), "arm_right": osc(0.5, 6.0, axis="y")}
    bite = {"claw_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (0, 30, 0)), (0.3, (0, -20, 0)), (0.5, (0, 0, 0)))},
            "claw_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (0, -30, 0)), (0.35, (0, 20, 0)), (0.5, (0, 0, 0)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0, -1)), (0.5, (0, 0, 0)))}}
    flinch = {tail[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.1, (40, 0, 0)), (0.4, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (0, 0, 2)), (0.4, (0, 0, 0)))}}
    return {"idle": clip(2.4, idle), "walk": clip(0.5, walk),
            "bite": clip(0.5, bite, loop=False), "flinch": clip(0.4, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "mackled_malaclaw_model.py", build, paint, anims, HITBOX))
