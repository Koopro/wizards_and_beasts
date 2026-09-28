#!/usr/bin/env python3
"""The Maledictus: rig, skin and animation.

Canon (The Crimes of Grindelwald): a woman carrying a blood curse that turns her, over time, into
a beast — permanently, in the end. Nagini is one; her beast is a great snake. POISON_ATTACK,
`constrict`, `enrage`.

A human model or a snake model would each be only half of that, so it is drawn mid-curse: a
woman's torso, dark hair and dark dress rising out of the coils of a snake's body, the scales
climbing her waist, and a snake's slit yellow eyes already in her face. The `strike` clip is a
snake's strike made with a person's body.

Run from the repo root:  python tools/maledictus_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, hang  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "maledictus"
HITBOX = (0.95, 1.25)

SCALE = hx("#3E4A36")
SCALE_DARK = hx("#29321F")
SCALE_LIT = hx("#6E7A4E")
SKIN_ = hx("#C8A690")
DRESS = hx("#4A1C28")
DRESS_DARK = hx("#2E1018")
HAIR = hx("#161214")
EYE = hx("#E8D23A")


def build():
    rig = Rig(CID, 64)
    rig.bone("coil", "root", (0, 0, 0))
    rig.cube("coil", (-7, 0, -6), (14, 4, 13), key="coil_1")
    rig.cube("coil", (-5.5, 3, -4.5), (11, 4, 10), key="coil_2", rotation=(0, 25, 0), pivot=(0, 5, 0.5))
    rig.cube("coil", (-4, 6, -3), (8, 3, 7), key="coil_3", rotation=(0, -15, 0), pivot=(0, 7.5, 0.5))
    tail = chain(rig, "tail", "coil", start=(0, 1.5, 6), direction=1,
                 segments=[(5, 3, 3), (5, 2, 2), (4, 1, 1)],
                 rotations=[(0, 40, 0), (0, 30, 0), (0, 25, 0)])

    rig.bone("waist", "coil", (0, 9, 0))
    rig.cube("waist", (-2.5, 8, -2), (5, 5, 4))
    rig.bone("chest", "waist", (0, 13, 0))
    rig.cube("chest", (-3, 13, -2), (6, 6, 4), key="torso")
    rig.bone("head", "chest", (0, 19, 0))
    rig.cube("head", (-2.5, 19, -2.5), (5, 5, 5))
    rig.cube("head", (-3, 22, -2.5), (6, 3, 6), key="hair_top", inflate=0.2)
    rig.cube("head", (-3, 15, 1.5), (6, 8, 2), key="hair_back")
    rig.pair(lambda side, sign: hang(rig, f"arm_{side}", "chest", x=4, z=0, sign=sign, top=18,
                                     segments=[("", 4, 2, 2, (0, 0, -8)), ("fore", 4, 2, 2, (-10, 0, 0)),
                                               ("hand", 1, 2, 2, None)]))
    ground(rig)
    return rig, tail


def paint(rig, tex_h, tail):
    skin = Skin(rig, tex_h, grain="fleck")
    coil = ["coil_1", "coil_2", "coil_3"] + list(tail)
    skin.skin(coil, SCALE, top=SCALE_LIT, dither=0.0)
    for key in coil:
        for face in ("top", "east", "west", "north", "south"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_DARK)
    skin.skin(["waist"], SCALE, dither=0.0)
    skin.ramp("waist", DRESS, SCALE)
    skin.skin(["torso"], DRESS, dither=0.08, dither_colour=DRESS_DARK)
    skin.skin(["head"] + rig.keys("arm_"), SKIN_, dither=0.0)
    skin.skin([k for k in rig.keys("arm_") if "hand" not in k], DRESS, dither=0.0)
    skin.skin(["hair_top", "hair_back"], HAIR, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 2):
        skin.d.point((ex, y + 2), fill=EYE)
        skin.glow_rect((ex, y + 2, 1, 1))
    skin.hline((x, y, w, h), 0, HAIR)
    return skin


def anims(tail):
    idle = {
        "waist": osc(4.0, 4.0, axis="z"),
        "chest": osc(4.0, 3.0, axis="z", phase=0.25),
        "head": {"rotation": kf((0.0, (0, -10, 0)), (2.0, (0, 10, 0)), (4.0, (0, -10, 0)))},
        **{name: osc(4.0, 8.0 + 4 * i, axis="y", phase=0.2 * i) for i, name in enumerate(tail)},
    }
    walk = {
        "coil": {"rotation": kf((0.0, (0, 6, 0)), (0.45, (0, -6, 0)), (0.9, (0, 6, 0)))},
        "waist": osc(0.9, 6.0, axis="z", phase=0.25),
        **{name: osc(0.9, 14.0 + 6 * i, axis="y", phase=0.15 * i) for i, name in enumerate(tail)},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.45, (0, 0.3, 0)), (0.9, (0, 0, 0)))},
    }
    strike = {
        "waist": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.4, (34, 0, 0)),
                                 (0.8, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.4, (16, 0, 0)),
                                 (0.8, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-90, 0, 10)), (0.8, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-90, 0, -10)), (0.8, (0, 0, 0)))},
    }
    hiss = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-14, 0, 0)), (1.0, (-10, 0, 0)),
                                (1.3, (0, 0, 0)))},
        "waist": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-8, 0, 0)), (1.3, (0, 0, 0)))},
        tail[-1]: {"rotation": kf((0.0, (0, 0, 0)), (0.2, (0, 30, 0)), (0.4, (0, -30, 0)),
                                  (0.6, (0, 30, 0)), (0.8, (0, -30, 0)), (1.3, (0, 0, 0)))},
    }
    return {"idle": clip(4.0, idle), "walk": clip(0.9, walk),
            "strike": clip(0.8, strike, loop=False), "hiss": clip(1.3, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "maledictus_model.py", build, paint, anims, HITBOX))
