#!/usr/bin/env python3
"""The Bundimun: rig, skin and animation.

Canon: found worldwide, infesting houses; it creeps under floorboards and behind skirting, looks
like a patch of greenish fungus with eyes, and scuttles on many thin legs when disturbed. Its
secretions rot the foundations of the house. FEARFUL, `block_decay`.

A patch of mould that is also an animal: a flat, lumpy green mat with a scattering of fungal
bumps, two eyes up on stalks (the only lit part — this is what is looking at you from under the
floor), and a skirt of thin legs that only show when it runs.

Run from the repo root:  python tools/bundimun_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "bundimun"
HITBOX = (0.5, 0.4)

MOULD = hx("#6E8A4A")
MOULD_DARK = hx("#4A5E2E")
SPORE = hx("#A8B86A")
STALK = hx("#8A9A6A")
EYE = hx("#F0E8A0")
LEG = hx("#3A3A2A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 1, 0))
    rig.cube("body", (-4, 1, -4), (8, 2, 8), key="mat")
    for i, (x, z, s) in enumerate([(-2, -1, 3), (1, 1, 3), (-1, 2, 2), (2, -2, 2)]):
        rig.cube("body", (x - s / 2.0, 3, z - s / 2.0), (s, 1 + (i < 2), s), key=f"bump_{i}")
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"stalk_{side}", "body", (sign * 1.5, 3, -2), (-15, 0, sign * -15))
        rig.cube(f"stalk_{side}", Rig.mirror((1, 3, -2.5), (1, 3, 1), sign), (1, 3, 1), key=f"stalk_{side}")
        rig.cube(f"stalk_{side}", Rig.mirror((0.5, 6, -3), (2, 2, 2), sign), (2, 2, 2), key=f"eye_{side}")
    for i in range(3):
        for side, sign in (("l", 1), ("r", -1)):
            rig.bone(f"leg_{side}{i}", "body", (sign * 4, 1, -2 + 2 * i), (0, 0, sign * 20))
            rig.cube(f"leg_{side}{i}", Rig.mirror((4, 0, -2.5 + 2 * i), (2, 1, 1), sign), (2, 1, 1))
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["mat"] + rig.keys("bump_"), MOULD, dither=0.35, dither_colour=MOULD_DARK, bevel=0)
    skin.dither(skin.face("mat", "top"), SPORE, 0.15, 3, grain="fleck")
    skin.skin(rig.keys("stalk_"), STALK, dither=0.0)
    skin.skin(rig.keys("leg_"), LEG, dither=0.0)
    for key in rig.keys("eye_"):
        skin.skin(key, EYE, dither=0.0)
        skin.mark(key, "north", 0, 1, LEG, w=2)
        skin.glow(key, faces=("north", "top", "east", "west"))
    return skin


def anims():
    legs = [f"leg_{s}{i}" for s in "lr" for i in range(3)]
    idle = {"stalk_left": osc(3.0, 12.0, axis="z"), "stalk_right": osc(3.0, 12.0, axis="z", phase=0.4),
            "body": {"scale": kf((0.0, (1, 1, 1)), (1.5, (1.04, 0.9, 1.04)), (3.0, (1, 1, 1)))}}
    walk = {**{leg: osc(0.2, 40.0, axis="y", phase=(i * 0.33) % 1.0) for i, leg in enumerate(legs)},
            "body": {"position": kf((0.0, (0, 0.3, 0)), (0.1, (0, 0, 0)), (0.2, (0, 0.3, 0)))}}
    flinch = {"stalk_left": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (60, 0, 0)), (0.9, (60, 0, 0)), (1.2, (0, 0, 0)))},
              "stalk_right": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (60, 0, 0)), (0.9, (60, 0, 0)), (1.2, (0, 0, 0)))},
              "body": {"scale": kf((0.0, (1, 1, 1)), (0.1, (1.1, 0.6, 1.1)), (1.2, (1, 1, 1)))}}
    return {"idle": clip(3.0, idle), "walk": clip(0.2, walk), "flinch": clip(1.2, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "bundimun_model.py", build, paint, anims, HITBOX))
