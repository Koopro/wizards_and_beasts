#!/usr/bin/env python3
"""The Moke: rig, skin and animation.

Canon: a silver-green lizard up to ten inches long, found across Britain and Ireland, which can
shrink at will — Muggles have never noticed one for that reason. Moke-skin purses shrink too,
so a thief cannot find the opening. FEARFUL, with `camouflage`.

Its reaction to everything is to get smaller, so the `flinch` clip is a scale track on the whole
rig, and the skin is the silvery sheen canon describes: a cool green-grey with a lit ridge down
the spine.

Run from the repo root:  python tools/moke_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import quadruped_loops  # noqa: E402
from lizard import lizard  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "moke"
HITBOX = (0.65, 0.75)

SCALE = hx("#8FA894")
SCALE_DARK = hx("#63806A")
SILVER = hx("#CFDDD2")
BELLY = hx("#B8C6B4")
EYE = hx("#D8B03A")


def build():
    rig = Rig(CID, 64)
    a = lizard(rig, body=(5, 3, 9), head=(4, 3, 4), snout=(3, 2, 2),
               tail=[(5, 2, 2), (5, 1, 1), (4, 1, 1)])
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SCALE, top=SILVER, bottom=BELLY, dither=0.12, dither_colour=SCALE_DARK)
    # Banded scales across the back and a silver ridge down the middle of it.
    for key in ("chest", "barrel", "croup"):
        x, y, w, h = skin.face(key, "top")
        skin.vline((x, y, w, h), w // 2, SILVER)
    skin.bands(a.tail, SCALE_DARK, step=2)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 0, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.0, walk_len=0.5, stride=30.0, bob_amp=0.1)
    # Lizards walk with the spine: the body swings side to side against the legs.
    walk["body"] = {"rotation": kf((0.0, (0, 10, 0)), (0.25, (0, -10, 0)), (0.5, (0, 10, 0)))}
    for i, name in enumerate(a.tail):
        walk[name] = {"rotation": kf((0.0, (0, -8 - 4 * i, 0)), (0.25, (0, 8 + 4 * i, 0)),
                                     (0.5, (0, -8 - 4 * i, 0)))}
    # `flinch` is the shrink. It goes to a third of its size, holds, and cautiously grows back.
    flinch = {
        "root": {"scale": kf((0.0, (1, 1, 1)), (0.15, (0.35, 0.35, 0.35)), (1.6, (0.35, 0.35, 0.35)),
                             (2.2, (1, 1, 1)))},
    }
    hiss = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-18, 0, 0)), (0.7, (-14, 0, 0)),
                                (0.9, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-4, 0, 0)), (0.9, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.5, walk),
            "flinch": clip(2.2, flinch, loop=False), "hiss": clip(0.9, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "moke_model.py", build, paint, anims, HITBOX))
