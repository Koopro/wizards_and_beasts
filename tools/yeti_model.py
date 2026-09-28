#!/usr/bin/env python3
"""The Yeti: rig, skin and animation.

Canon: a native of Tibet, related to the troll, about fifteen feet tall and covered head to foot
in pure white hair, which lets it vanish against snow; it eats anything that strays near and fears
fire. KNOCKBACK, `camouflage`.

White on white is the brief and the problem: a white box is unreadable. So the fur is shaggy —
hanging strands on every vertical face, a dither of cool grey — and the only non-white on it is
the bare blue-grey face, palms and soles, which are what make it an ape and not a snowdrift.
Stooped, long-armed, jutting head.

Run from the repo root:  python tools/yeti_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "yeti"
HITBOX = (1.7, 2.3)

FUR = hx("#EAEEF0")
FUR_SHADE = hx("#BCC6CE")
SKIN_ = hx("#7C8A98")
SKIN_DARK = hx("#5A6674")
EYE = hx("#9ED8F0")
MOUTH = hx("#3A3E48")


def build():
    rig = Rig(CID, 128)
    a = biped(rig, hip=12, pelvis=(12, 6, 8), torso=(16, 13, 10), head=(9, 9, 9), hunch=22,
              head_z=-2,
              legs=[("", 7, 6, 6, None), ("shin", 3, 5, 5, None), ("foot", 2, 7, 8, None)],
              arms=[("", 10, 5, 5, (0, 0, -8)), ("fore", 9, 5, 5, (-12, 0, 0)),
                    ("hand", 3, 6, 5, None)],
              arm_x=10, leg_x=3.5)
    rig.cube("chest", (-9, a.shoulder - 6, -6), (18, 7, 12), key="mane", inflate=0.6)
    rig.cube("head", (-5, a.head_top - 3, a.head_front + 2), (10, 4, 8), key="crown", inflate=0.4)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), FUR, dither=0.14, dither_colour=FUR_SHADE)
    for key in ("mane", "torso", "pelvis", "crown") + tuple(k for k in rig.keys("arm_", "leg_")
                                                             if "hand" not in k and "foot" not in k):
        for face in ("east", "west", "south", "north"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y + (col * 7) % 3), (x + col, y + h - 1)], fill=FUR_SHADE)
    for key in [k for k in rig.keys("arm_", "leg_") if "hand" in k or "foot" in k]:
        skin.rect(skin.face(key, "bottom"), SKIN_)
        skin.rect(skin.face(key, "north"), SKIN_)
    x, y, w, h = skin.face("head", "north")
    skin.rect((x + 1, y + 2, w - 2, h - 3), SKIN_)
    skin.eyes("head", EYE, row=3, inset=2, width=2, glow=True)
    skin.hline((x, y, w, h), 2, SKIN_DARK, inset=1)
    skin.hline((x, y, w, h), h - 3, MOUTH, inset=3)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=4.4, walk_len=1.2, stride=26.0, bob_amp=1.0)
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-150, 0, 30)), (0.55, (10, 0, -10)),
                                     (0.9, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.35, (-150, 0, -30)), (0.6, (10, 0, 10)),
                                    (0.95, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-16, 0, 0)), (0.55, (14, 0, 0)),
                                 (0.95, (0, 0, 0)))},
    }
    howl = {
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-24, 0, 0)), (1.6, (-20, 0, 0)),
                                 (2.0, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-60, 0, -40)), (0.6, (-40, 0, -30)),
                                    (0.8, (-60, 0, -40)), (1.0, (-40, 0, -30)), (2.0, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-60, 0, 40)), (0.6, (-40, 0, 30)),
                                     (0.8, (-60, 0, 40)), (1.0, (-40, 0, 30)), (2.0, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-20, 0, 0)), (2.0, (0, 0, 0)))},
    }
    return {"idle": clip(4.4, idle), "walk": clip(1.2, walk),
            "attack": clip(0.95, attack, loop=False), "howl": clip(2.0, howl, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "yeti_model.py", build, paint, anims, HITBOX))
