#!/usr/bin/env python3
"""The Chizpurfle: rig, skin and animation.

Canon: a small parasite up to a twentieth of an inch long, crab-like with large fangs, attracted
by magic; it lives in the fur and feathers of magical creatures and will get into wizards' houses
to gnaw wands and suck on potion residue. PACK, `life_leech`.

A twentieth of an inch cannot be drawn, so it is drawn at the smallest size a face can carry: a
dark red shell on six hair-thin legs, with two white fangs that are the largest thing about it.

Run from the repo root:  python tools/chizpurfle_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from crab import crab, scuttle  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "chizpurfle"
HITBOX = (0.45, 0.45)

SHELL = hx("#7A2A24")
SHELL_LIT = hx("#A84A3A")
LEG = hx("#4A1A16")
FANG = hx("#F0E8D8")
EYE = hx("#E8D24A")


def build():
    rig = Rig(CID, 64)
    legs = crab(rig, shell=(5, 2, 4), hip_y=2, leg=(1, 2, 3, 1, 1))
    rig.bone("fangs", "body", (0, 2, -2))
    rig.pair(lambda side, sign: rig.cube("fangs", Rig.mirror((0.5, 0, -3), (1, 2, 1), sign), (1, 2, 1), key=f"fang_{side}"))
    ground(rig)
    return rig, legs


def paint(rig, tex_h, legs):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin("shell", SHELL, top=SHELL_LIT, dither=0.0)
    skin.skin(rig.keys("leg_"), LEG, dither=0.0)
    skin.skin(rig.keys("fang_"), FANG, dither=0.0)
    x, y, w, h = skin.face("shell", "north")
    skin.d.point((x + 1, y), fill=EYE)
    skin.d.point((x + w - 2, y), fill=EYE)
    return skin


def anims(legs):
    idle = {"fangs": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (20, 0, 0)), (1.2, (0, 0, 0)))}}
    walk = {**scuttle(0.25, legs, amp=30.0), "body": {"position": kf((0.0, (0, 0, 0)), (0.125, (0, 0.3, 0)), (0.25, (0, 0, 0)))}}
    bite = {"fangs": {"rotation": kf((0.0, (0, 0, 0)), (0.08, (-40, 0, 0)), (0.2, (30, 0, 0)), (0.35, (0, 0, 0)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.15, (0, 0, -1)), (0.35, (0, 0, 0)))}}
    flinch = {"body": {"scale": kf((0.0, (1, 1, 1)), (0.08, (0.8, 0.8, 0.8)), (0.3, (1, 1, 1)))}}
    return {"idle": clip(1.2, idle), "walk": clip(0.25, walk),
            "bite": clip(0.35, bite, loop=False), "flinch": clip(0.3, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "chizpurfle_model.py", build, paint, anims, HITBOX))
