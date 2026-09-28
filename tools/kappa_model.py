#!/usr/bin/env python3
"""The Kappa: rig, skin and animation.

Canon: a Japanese water demon that lives in shallow ponds and rivers, often said to look like a
monkey with fish scales instead of fur, with a hollow in the top of its head in which it carries
water; trick it into bowing and the water spills out and it loses its strength. Hostile,
AMPHIBIOUS, POISON_ATTACK, `life_leech`.

The hollow is the thing everyone knows, so the skull is open at the top with a lit pool of water
in it. The body is a crouched, long-armed monkey scaled in yellow-green, with a shell across the
back from the folklore that the wizarding description leaves out and every picture keeps.

Run from the repo root:  python tools/kappa_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "kappa"
HITBOX = (0.95, 1.25)

SCALE = hx("#8A9A4A")
SCALE_DARK = hx("#5E6E2E")
BELLY = hx("#C8C88A")
SHELL = hx("#5A5234")
SHELL_LIT = hx("#7E7450")
WATER = hx("#7ACCEA")
BEAK = hx("#D8B44A")
EYE = hx("#1A1810")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=8, pelvis=(6, 4, 4), torso=(8, 8, 5), head=(7, 6, 6), hunch=24,
              legs=[("", 4, 3, 3, (-15, 0, 0)), ("shin", 2, 2, 2, (25, 0, 0)), ("foot", 2, 4, 4, (-10, 0, 0))],
              arms=[("", 5, 2, 2, (0, 0, -8)), ("fore", 4, 2, 2, (-14, 0, 0)), ("hand", 2, 3, 3, None)],
              arm_x=5, leg_x=2)
    rig.cube("chest", (-4.5, a.waist, a.torso_back - 1), (9, 8, 3), key="shell")
    rig.cube("head", (-3.5, a.head_top, a.head_front), (7, 1, 6), key="rim")
    rig.cube("head", (-2.5, a.head_top - 0.5, a.head_front + 1), (5, 1, 4), key="pool")
    rig.cube("head", (-1.5, a.head_y + 1, a.head_front - 2), (3, 2, 2), key="beak")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SCALE, bottom=BELLY, dither=0.0)
    for key in rig.keys(""):
        for face in ("east", "west", "top", "north"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_DARK)
    skin.rect(skin.face("torso", "north"), BELLY)
    skin.skin("shell", SHELL, dither=0.0)
    for face in ("south", "top"):
        x, y, w, h = skin.face("shell", face)
        for col in range(0, w, 3):
            skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=SHELL_LIT)
    skin.skin("rim", SCALE_DARK, dither=0.0)
    skin.skin("pool", WATER, dither=0.0)
    skin.glow("pool", faces=("top",))
    skin.skin("beak", BEAK, dither=0.0)
    skin.eyes("head", EYE, row=1, inset=1)
    return skin


def anims(a):
    idle, _walk = biped_loops(a.rig, a, idle_len=3.4)   # AQUATIC binds idle and swim, never walk
    swim = {"chest": {"rotation": kf((0.0, (50, 0, 0)), (0.8, (50, 0, 0)))},
            "arm_left": osc(0.8, 60.0, base=-60), "arm_right": osc(0.8, 60.0, base=-60, phase=0.5),
            "leg_left": osc(0.8, 30.0, phase=0.25), "leg_right": osc(0.8, 30.0, phase=0.75)}
    attack = {"arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-120, 0, -20)), (0.4, (-30, 0, 0)), (0.7, (0, 0, 0)))},
              "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-120, 0, 20)), (0.4, (-30, 0, 0)), (0.7, (0, 0, 0)))},
              "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.45, (14, 0, 0)), (0.7, (0, 0, 0)))}}
    # `flinch` is the bow: the one reaction canon gives it, and the water very nearly spills.
    flinch = {"chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (30, 0, 0)), (0.6, (26, 0, 0)), (0.9, (0, 0, 0)))},
              "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (20, 0, 0)), (0.9, (0, 0, 0)))}}
    return {"idle": clip(3.4, idle), "swim": clip(0.8, swim),
            "attack": clip(0.7, attack, loop=False), "flinch": clip(0.9, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "kappa_model.py", build, paint, anims, HITBOX))
