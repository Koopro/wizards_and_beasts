#!/usr/bin/env python3
"""The Horklump: rig, skin and animation.

Canon: a Scandinavian creature now spread across northern Europe that looks like a fleshy pinkish
mushroom covered in sparse wiry black bristles; it sends out tendrils into the soil to find
earthworms, and multiplies so fast it can cover a garden in days. SESSILE — `GenericSessileBeastEntity`
binds `idle` only.

A pink mushroom cap on a squat stalk, bristled, with four tendrils spreading along the ground from
its base. The idle is the only locomotion it gets, so the tendrils creep and the cap swells in it.

Run from the repo root:  python tools/horklump_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "horklump"
HITBOX = (0.65, 0.75)

FLESH = hx("#D8A0AC")
FLESH_DARK = hx("#B87884")
GILL = hx("#8A5A64")
BRISTLE = hx("#1E1618")
STALK = hx("#E8C8C8")


def build():
    rig = Rig(CID, 64)
    rig.bone("stalk", "root", (0, 0, 0))
    rig.cube("stalk", (-2, 0, -2), (4, 6, 4), key="stalk")
    rig.bone("cap", "stalk", (0, 6, 0))
    rig.cube("cap", (-5, 6, -5), (10, 4, 10), key="cap")
    rig.cube("cap", (-3.5, 10, -3.5), (7, 2, 7), key="cap_top")
    for i, yaw in enumerate((0, 90, 180, 270)):
        rig.bone(f"tendril_{i}", "stalk", (0, 0.5, 0), (0, yaw + 20, 0))
        rig.cube(f"tendril_{i}", (-0.5, 0, 2), (1, 1, 6), key=f"tendril_{i}")
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["cap", "cap_top"], FLESH, dither=0.08, dither_colour=FLESH_DARK)
    skin.rect(skin.face("cap", "bottom"), GILL)
    for key in ("cap", "cap_top"):
        for face in ("top", "east", "west", "north", "south"):
            skin.dither(skin.face(key, face), BRISTLE, 0.07, 13 + len(face))
    skin.skin("stalk", STALK, dither=0.06, dither_colour=FLESH_DARK)
    skin.skin(rig.keys("tendril_"), FLESH_DARK, dither=0.0)
    return skin


def anims():
    idle = {"cap": {"scale": kf((0.0, (1, 1, 1)), (2.5, (1.06, 1.04, 1.06)), (5.0, (1, 1, 1)))},
            **{f"tendril_{i}": osc(5.0, 10.0, axis="y", phase=0.25 * i) for i in range(4)}}
    flinch = {"cap": {"scale": kf((0.0, (1, 1, 1)), (0.1, (0.85, 0.8, 0.85)), (0.6, (1, 1, 1)))},
              **{f"tendril_{i}": {"scale": kf((0.0, (1, 1, 1)), (0.1, (1, 1, 0.5)), (0.6, (1, 1, 1)))} for i in range(4)}}
    return {"idle": clip(5.0, idle), "flinch": clip(0.6, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "horklump_model.py", build, paint, anims, HITBOX))
