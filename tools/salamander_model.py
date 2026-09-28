#!/usr/bin/env python3
"""The Salamander: rig, skin and animation.

Canon: a small fire-dwelling lizard, born from and fed by flame, which appears brilliant white,
blue or scarlet depending on the heat of the fire; it lives only as long as the fire burns
unless fed pepper. FIRE_IMMUNE, FIRE_ATTACK and `fire_affinity` here.

Same skeleton as the Moke (`tools/lizard.py`), a different animal in every other respect: scarlet
running to white-hot along the spine, with the whole upper body lit in the glowmask so it reads
as burning in a dark nether corridor, and a flickering idle rather than a still one.

Run from the repo root:  python tools/salamander_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import quadruped_loops  # noqa: E402
from lizard import lizard  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "salamander"
HITBOX = (0.65, 0.75)

EMBER = hx("#C8341E")
FLAME = hx("#F07A24")
HOT = hx("#FCE6A0")
CHAR = hx("#5A1A10")
EYE = hx("#6AD8F0")


def build():
    rig = Rig(CID, 64)
    a = lizard(rig, body=(5, 3, 9), head=(4, 3, 4), snout=(3, 2, 2),
               tail=[(5, 2, 2), (4, 2, 1), (4, 1, 1)])
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), EMBER, top=FLAME, bottom=CHAR, dither=0.18, dither_colour=FLAME)
    for key in ("chest", "barrel", "croup", "head") + tuple(a.tail):
        x, y, w, h = skin.face(key, "top")
        skin.vline((x, y, w, h), w // 2, HOT)
        skin.glow(key, faces=("top", "east", "west"))
    skin.bands(a.tail, FLAME, step=2)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 0, EYE, glow=True)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=1.2, walk_len=0.45, stride=30.0, bob_amp=0.1)
    # Flicker: a fast small shimmer in scale, like heat haze, instead of a slow breath.
    idle["body"] = {"scale": kf((0.0, (1, 1, 1)), (0.3, (1.04, 1.08, 1.0)), (0.6, (0.98, 0.96, 1.0)),
                                (0.9, (1.03, 1.06, 1.0)), (1.2, (1, 1, 1)))}
    walk["body"] = {"rotation": kf((0.0, (0, 12, 0)), (0.22, (0, -12, 0)), (0.45, (0, 12, 0)))}
    flinch = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (0, 30, 0)), (0.4, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (1, 0.8, 0)), (0.4, (0, 0, 0)))},
    }
    hiss = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-24, 0, 0)), (0.6, (-20, 0, 0)),
                                (0.8, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (20, 0, 0)), (0.8, (0, 0, 0)))},
    }
    return {"idle": clip(1.2, idle), "walk": clip(0.45, walk),
            "flinch": clip(0.4, flinch, loop=False), "hiss": clip(0.8, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "salamander_model.py", build, paint, anims, HITBOX))
