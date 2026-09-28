#!/usr/bin/env python3
"""The Rougarou: rig, skin and animation.

Canon: a dangerous dog-headed monster of the Louisiana swamps — the creature Newt Scamander was
warned about and the folklore cousin of the werewolf. Hostile, PACK, `camouflage`.

This mod already has a hand-built werewolf, and the rougarou must not be it. The werewolf is a
cursed man mid-change, lean and upright; the rougarou is a swamp animal: heavier in the shoulder,
more stooped, a longer hound's muzzle, drooping rather than pricked ears, a matted wet coat with
moss in it, and a ragged tail.

Run from the repo root:  python tools/rougarou_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, chain, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "rougarou"
HITBOX = (1.2, 1.9)

FUR = hx("#4A4036")
FUR_DARK = hx("#2E2822")
FUR_WET = hx("#5E5446")
MOSS = hx("#4E6A34")
EYE = hx("#E8C23A")
CLAW = hx("#D8CCB0")
NOSE = hx("#141010")


def build():
    rig = Rig(CID, 128)
    a = biped(rig, hip=13, pelvis=(8, 4, 5), torso=(11, 11, 7), head=(7, 7, 7), hunch=26,
              head_z=-2,
              legs=[("", 6, 4, 5, (-24, 0, 0)), ("shin", 5, 3, 3, (46, 0, 0)),
                    ("foot", 2, 4, 6, (-22, 0, 0))],
              arms=[("", 7, 4, 4, (0, 0, -8)), ("fore", 7, 3, 3, (-16, 0, 0)),
                    ("hand", 3, 4, 3, None)],
              arm_x=7, leg_x=2.5)
    rig.cube("head", (-2.5, a.head_y, a.head_front - 6), (5, 4, 6), key="snout")
    rig.cube("chest", (-6, a.shoulder - 5, -4), (12, 6, 8), key="shoulders", inflate=0.5)

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 3, a.head_top - 1, a.head_back - 2), (20, 0, sign * -70))
        rig.cube(f"ear_{side}", Rig.mirror((3, a.head_top - 5, a.head_back - 3), (1, 5, 2), sign), (1, 5, 2))

    rig.pair(build_ear)
    for side, sign in (("left", 1), ("right", -1)):
        rig.cube(f"arm_{side}_hand", Rig.mirror((6, a.shoulder - 21, -2.5), (3, 2, 1), sign), (3, 2, 1),
                 key=f"claws_{side}")
    a["tail"] = chain(rig, "tail", "body", start=(0, a.pelvis_y + 2, 2), direction=1,
                      segments=[(6, 3, 3), (5, 2, 2)], rotations=[(-40, 0, 0), (-10, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), FUR, top=FUR_WET, dither=0.2, dither_colour=FUR_DARK)
    for key in ("shoulders", "torso"):
        for face in ("east", "west", "south"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y + (col % 3)), (x + col, y + h - 1)], fill=FUR_DARK)
        skin.dither(skin.face(key, "top"), MOSS, 0.2, 9)
    skin.skin(rig.keys("claws_"), CLAW, dither=0.0)
    skin.eyes("head", EYE, row=3, inset=1, glow=True)
    skin.mark("snout", "north", 1, 0, NOSE, w=3, h=2)
    x, y, w, h = skin.face("snout", "north")
    skin.hline((x, y, w, h), h - 1, CLAW)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.6, walk_len=0.9, stride=30.0, bob_amp=0.8)
    for i, name in enumerate(a.tail):
        idle[name] = {"rotation": kf((0.0, (0, 8 + 4 * i, 0)), (1.8, (0, -8 - 4 * i, 0)),
                                     (3.6, (0, 8 + 4 * i, 0)))}
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-140, 0, 20)), (0.4, (10, 0, -10)),
                                     (0.7, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-140, 0, -20)), (0.5, (10, 0, 10)),
                                    (0.8, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.45, (12, 0, 0)),
                                 (0.8, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (16, 0, 0)), (0.8, (0, 0, 0)))},
    }
    howl = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-50, 0, 0)), (1.6, (-46, 0, 0)),
                                (2.0, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-14, 0, 0)), (1.6, (-12, 0, 0)),
                                 (2.0, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (0, 0, -20)), (2.0, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (0, 0, 20)), (2.0, (0, 0, 0)))},
    }
    return {"idle": clip(3.6, idle), "walk": clip(0.9, walk),
            "attack": clip(0.8, attack, loop=False), "howl": clip(2.0, howl, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "rougarou_model.py", build, paint, anims, HITBOX))
