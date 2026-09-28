#!/usr/bin/env python3
"""The Erkling: rig, skin and animation.

Canon: an elfish creature of the Black Forest, larger than a gnome (about three feet), with a
pointed face and a high-pitched cackle that entrances children, whom it lures away to eat.
Hostile here, PACK, `dread_aura`.

It must not read as a gnome, which is round and potato-headed. An erkling is all points: a long
narrow face ending in a sharp nose and chin, ears swept out and back like blades, long fingers,
and a lean stooped frame in a ragged hide tunic. The cackle is the `call` clip.

Run from the repo root:  python tools/erkling_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "erkling"
HITBOX = (0.65, 0.75)

SKIN_ = hx("#98A488")
SKIN_DARK = hx("#6E7A60")
TUNIC = hx("#5A4430")
TUNIC_DARK = hx("#3E2E20")
EYE = hx("#161410")
EYE_SHINE = hx("#D8E0A0")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=6, pelvis=(4, 3, 3), torso=(5, 6, 3), head=(6, 6, 5), hunch=12, neck=1,
              legs=[("", 3, 2, 2, None), ("shin", 2, 1, 1, None), ("foot", 1, 2, 3, None)],
              arms=[("", 4, 1, 1, (0, 0, -6)), ("fore", 3, 1, 1, (-12, 0, 0)),
                    ("hand", 2, 2, 1, None)],
              arm_x=3, leg_x=1)
    rig.cube("chest", (-3, a.waist - 3, -2), (6, 6, 4), key="tunic", inflate=0.25)
    rig.cube("head", (-0.5, a.head_y + 2, a.head_front - 3), (1, 2, 3), key="nose")
    rig.cube("head", (-1, a.head_y - 1, a.head_front + 1), (2, 2, 2), key="chin")

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 3, a.head_y + 4, a.head_front + 3), (0, sign * 30, sign * -20))
        rig.cube(f"ear_{side}", Rig.mirror((3, a.head_y + 3, a.head_front + 2.5), (4, 2, 1), sign),
                 (4, 2, 1))

    rig.pair(build_ear)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.1, dither_colour=SKIN_DARK)
    skin.skin(["tunic", "pelvis"], TUNIC, dither=0.15, dither_colour=TUNIC_DARK)
    skin.tip("tunic", TUNIC_DARK, rows=1)
    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 3):
        skin.d.rectangle([ex, y + 2, ex + 1, y + 2], fill=EYE)
        skin.d.point((ex + 1 if ex == x + 1 else ex, y + 2), fill=EYE_SHINE)
    skin.hline((x, y, w, h), h - 1, SKIN_DARK)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.4, walk_len=0.7, stride=32.0)
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-110, 0, 0)), (0.35, (20, 0, 0)),
                                     (0.6, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-8, 10, 0)), (0.35, (12, -10, 0)),
                                 (0.6, (0, 0, 0)))},
    }
    # `call` is the cackle: head thrown back, shoulders shaking in quick beats.
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (1.0, (-26, 0, 0)),
                                (1.3, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-6, 0, 0)), (0.3, (-2, 0, 0)),
                                 (0.4, (-6, 0, 0)), (0.5, (-2, 0, 0)), (0.6, (-6, 0, 0)),
                                 (0.7, (-2, 0, 0)), (1.3, (0, 0, 0)))},
    }
    return {"idle": clip(3.4, idle), "walk": clip(0.7, walk),
            "attack": clip(0.6, attack, loop=False), "call": clip(1.3, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "erkling_model.py", build, paint, anims, HITBOX))
