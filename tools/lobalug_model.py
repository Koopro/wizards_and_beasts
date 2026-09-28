#!/usr/bin/env python3
"""The Lobalug: rig, skin and animation.

Canon: found at the bottom of the North Sea, a simple creature about ten inches long made of a
rubbery spout and a venom sac; threatened, it contracts the sac and blasts poison at its attacker.
Merpeople use them as weapons, and wizards extract the venom for potions. PASSIVE, `thorns`.

Two parts and nothing else, because canon gives it two parts: a pale translucent sac at the back
and a long rubbery spout in front, with a ring of muscle where they join. The idle is the sac
breathing; the flinch is the squeeze.

Run from the repo root:  python tools/lobalug_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, sphere  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "lobalug"
HITBOX = (0.7, 0.7)

RUBBER = hx("#B89AAE")
RUBBER_DARK = hx("#8A6A82")
SAC = hx("#8ADAB8")
SAC_DARK = hx("#5AAA88")
MUSCLE = hx("#6A4A62")


def build():
    rig = Rig(CID, 64)
    rig.bone("sac", "root", (0, 5, 2))
    ball = sphere(rig, "sac", c=8, y0=0, key="sac")
    rig.bone("spout", "sac", (0, 5, -4))
    rig.cube("spout", (-2.5, 2.5, -6), (5, 5, 2), key="ring")
    rig.cube("spout", (-1.5, 3.5, -12), (3, 3, 6), key="tube")
    rig.cube("spout", (-1, 4, -15), (2, 2, 3), key="nozzle")
    ground(rig)
    return rig, ball


def paint(rig, tex_h, ball):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(ball, SAC, dither=0.2, dither_colour=SAC_DARK, bevel=0)
    skin.skin(["tube", "nozzle"], RUBBER, dither=0.08, dither_colour=RUBBER_DARK)
    skin.bands("tube", RUBBER_DARK, faces=("top", "east", "west", "bottom"), step=2)
    skin.skin("ring", MUSCLE, dither=0.0)
    skin.tip("nozzle", MUSCLE, rows=1, faces=("north",))
    return skin


def anims(ball):
    idle = {"sac": {"scale": kf((0.0, (1, 1, 1)), (1.4, (1.08, 1.08, 1.08)), (2.8, (1, 1, 1)))},
            "spout": osc(2.8, 6.0, axis="y")}
    swim = {"sac": {"scale": kf((0.0, (1, 1, 1)), (0.3, (0.85, 0.85, 1.1)), (0.8, (1.05, 1.05, 0.95)), (1.2, (1, 1, 1)))},
            "spout": osc(1.2, 10.0, axis="y")}
    flinch = {"sac": {"scale": kf((0.0, (1, 1, 1)), (0.1, (0.6, 0.6, 0.6)), (0.25, (1.15, 1.15, 1.15)), (0.6, (1, 1, 1)))},
              "spout": {"scale": kf((0.0, (1, 1, 1)), (0.12, (1.3, 1.3, 1.2)), (0.6, (1, 1, 1)))}}
    return {"idle": clip(2.8, idle), "swim": clip(1.2, swim), "flinch": clip(0.6, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "lobalug_model.py", build, paint, anims, HITBOX))
