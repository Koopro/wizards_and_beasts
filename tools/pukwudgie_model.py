#!/usr/bin/env python3
"""The Pukwudgie: rig, skin and animation.

Canon: a North American relative of the goblin, around two feet tall, grey-skinned, with very
large ears and a prominent nose; it hunts with poisoned arrows and is notoriously hostile, and
Ilvermorny named a house for one. Hostile, `ranged_hex`, `blink_away`.

The ears and the bow. The ears are larger than the head, swept up and back; the nose is long; the
body is wiry and grey; a bow is always in the left hand and a quiver rides the back. Tall and
narrow in its 0.5 x 1.0 box.

Run from the repo root:  python tools/pukwudgie_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "pukwudgie"
HITBOX = (0.5, 1.0)

SKIN_ = hx("#8E9290")
SKIN_DARK = hx("#666A68")
EAR_INNER = hx("#B89A94")
CLOTH = hx("#6A5238")
WOOD = hx("#7A5A34")
STRING = hx("#D8D0B8")
FLETCH = hx("#B83A2A")
EYE = hx("#E8D060")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=7, pelvis=(5, 3, 3), torso=(5, 6, 3), head=(6, 6, 5), hunch=6, neck=1,
              legs=[("", 4, 2, 2, None), ("shin", 2, 2, 2, None), ("foot", 1, 2, 3, None)],
              arms=[("", 4, 2, 2, (0, 0, -6)), ("fore", 3, 2, 2, (-14, 0, 0))],
              arm_x=3.5, leg_x=1.5)
    rig.cube("head", (-1, a.head_y + 1, a.head_front - 3), (2, 3, 3), key="nose")

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 3, a.head_y + 4, a.head_back - 2), (-20, sign * -10, sign * -40))
        rig.cube(f"ear_{side}", Rig.mirror((2.5, a.head_y + 3, a.head_back - 4), (1, 7, 4), sign), (1, 7, 4))

    rig.pair(build_ear)
    rig.cube("body", (-3, a.pelvis_y, -2), (6, 3, 4), key="loincloth", inflate=0.25)
    rig.bone("bow", "arm_left_fore", (3.5, a.shoulder - 8, 0))
    rig.cube("bow", (3, a.shoulder - 14, -1.5), (1, 12, 1), key="bow_stave")
    rig.cube("bow", (3, a.shoulder - 14, 0.5), (1, 12, 1), key="bow_string")
    rig.cube("chest", (-1, a.waist, a.torso_back), (2, 6, 2), key="quiver", rotation=(0, 0, -20),
             pivot=(0, a.waist + 3, a.torso_back + 1))
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.1, dither_colour=SKIN_DARK)
    for key in rig.keys("ear_"):
        skin.rect(skin.face(key, "east"), EAR_INNER)
        skin.rect(skin.face(key, "west"), EAR_INNER)
    skin.skin(["loincloth", "quiver"], CLOTH, dither=0.0)
    skin.tip("quiver", FLETCH, rows=1, faces=("top",))
    skin.skin("bow_stave", WOOD, dither=0.0)
    skin.skin("bow_string", STRING, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 2):
        skin.d.point((ex, y + 2), fill=EYE)
    skin.hline((x, y, w, h), h - 1, SKIN_DARK)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.2, walk_len=0.6, stride=34.0)
    idle["ear_left"] = {"rotation": kf((0.0, (0, 0, 0)), (1.6, (6, 0, -8)), (3.2, (0, 0, 0)))}
    idle["ear_right"] = {"rotation": kf((0.0, (0, 0, 0)), (1.6, (6, 0, 8)), (3.2, (0, 0, 0)))}
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-60, 0, 0)), (0.3, (-100, 0, 0)),
                                     (0.6, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-80, 0, 0)), (0.3, (-90, 0, 0)),
                                    (0.6, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, -20, 0)), (0.6, (0, 0, 0)))},
    }
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-16, 18, 0)), (0.9, (-16, -18, 0)),
                                (1.2, (0, 0, 0)))},
        "ear_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.2, (0, 0, 0)))},
        "ear_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.2, (0, 0, 0)))},
    }
    return {"idle": clip(3.2, idle), "walk": clip(0.6, walk),
            "attack": clip(0.6, attack, loop=False), "call": clip(1.2, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "pukwudgie_model.py", build, paint, anims, HITBOX))
