#!/usr/bin/env python3
"""The Porlock: rig, skin and animation.

Canon: a horse-guardian of Dorset and southern Ireland, about two feet tall, covered in shaggy
hair, with a large head, cloven hooves for feet and very large hands (small arms). It sleeps in
straw and is shy of humans. FEARFUL, `heal_aura`, `blink_away`.

Shaggy is the texture; the silhouette is the big hands and the hooves. The whole animal is one
matted reddish-brown, so the pale, anxious face in the middle of the hair is what the eye finds.

Run from the repo root:  python tools/porlock_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "porlock"
HITBOX = (0.65, 0.75)

HAIR = hx("#8A5A3A")
HAIR_DARK = hx("#5E3C26")
HAIR_LIT = hx("#A87450")
FACE = hx("#D6B494")
EYE = hx("#261A12")
HOOF = hx("#1C1612")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=5, pelvis=(5, 3, 3), torso=(6, 6, 4), head=(7, 6, 6), hunch=8,
              legs=[("", 3, 2, 2, None), ("hoof", 2, 2, 2, None)],
              arms=[("", 3, 2, 2, (0, 0, -10)), ("hand", 3, 3, 3, (-8, 0, 0))],
              arm_x=4, leg_x=1.5)
    rig.cube("chest", (-3.5, a.waist - 2, -2.5), (7, 7, 5), key="shag", inflate=0.4)
    rig.cube("head", (-4, a.head_y + 1, a.head_front + 1), (8, 6, 6), key="mop", inflate=0.4)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), HAIR, top=HAIR_LIT, dither=0.25, dither_colour=HAIR_DARK)
    for key in ("shag", "mop"):
        for face in ("east", "west", "south", "north"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y + (col % 3)), (x + col, y + h - 1)], fill=HAIR_DARK)
    # The face is painted on the head's own front, which stands proud of the mop: the mop starts a
    # unit behind it so the face is the one bare patch in the hair.
    x, y, w, h = skin.face("head", "north")
    skin.rect((x + 1, y + 1, w - 2, h - 2), FACE)
    skin.hline((x, y, w, h), 0, HAIR_DARK)
    skin.d.point((x + 2, y + 2), fill=EYE)
    skin.d.point((x + w - 3, y + 2), fill=EYE)
    skin.hline((x, y, w, h), h - 2, HAIR, inset=2)
    skin.skin([k for k in rig.keys("leg_") if "hoof" in k], HOOF, dither=0.0)
    for key in [k for k in rig.keys("leg_") if "hoof" in k]:
        skin.vline(skin.face(key, "north"), 1, HAIR_DARK)   # the cleft
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.4, walk_len=0.5, stride=34.0)
    flinch = {
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (24, 0, 0)), (0.9, (20, 0, 0)),
                                 (1.2, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-90, 0, 20)), (0.9, (-90, 0, 20)),
                                    (1.2, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-90, 0, -20)), (0.9, (-90, 0, -20)),
                                     (1.2, (0, 0, 0)))},
    }
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 40, 0)), (1.0, (0, -40, 0)),
                                (1.4, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0.8, 0)), (0.4, (0, 0, 0)),
                                (1.4, (0, 0, 0)))},
    }
    return {"idle": clip(3.4, idle), "walk": clip(0.5, walk),
            "flinch": clip(1.2, flinch, loop=False), "call": clip(1.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "porlock_model.py", build, paint, anims, HITBOX))
