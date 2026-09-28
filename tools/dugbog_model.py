#!/usr/bin/env python3
"""The Dugbog: rig, skin and animation.

Canon: a marsh-dweller of Europe and the Americas that looks, when still, like a piece of dead
wood — until you see the finned paws and very sharp teeth. It grazes on small mammals and
Mandrakes. AMPHIBIOUS, `camouflage`, `leap`.

So it is drawn as a log first and an animal second: a long, knotted, bark-grained body lying
low, snag-like branches off the back that break its outline, moss in the grain, and a flat head
that is the same bark until the jaw opens on white teeth. The paws are broad fins.

Run from the repo root:  python tools/dugbog_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "dugbog"
HITBOX = (0.95, 1.25)

BARK = hx("#5D4A36")
BARK_DARK = hx("#3C3024")
BARK_LIT = hx("#7A6650")
MOSS = hx("#5E7A3A")
TEETH = hx("#ECE6D4")
MOUTH = hx("#7A2E2A")
EYE = hx("#D8C44A")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=4,
        chest=(9, 7, 6), barrel=(8, 7, 12), croup=(8, 6, 6), croup_drop=-2,
        neck=None, neck_rake=0, head=(8, 5, 8), head_y=4, muzzle=(6, 3, 5),
        fore=[("", 2, 3, 3, (0, 0, -20)), ("fin", 2, 5, 4, (0, 0, 20))],
        hind=[("", 2, 3, 3, (0, 0, -20)), ("fin", 2, 5, 4, (0, 0, 20))],
        fore_x=4, hind_x=4)
    rig.bone("jaw", "head", (0, a.head_y + 1, a.head_back - 1))
    rig.cube("jaw", (-3, a.head_y - 1, a.muzzle_front + 1), (6, 2, 12), key="jaw")
    for i, (z, rz, rx, h) in enumerate([(3, -30, -10, 6), (11, 25, 8, 5), (18, -15, 20, 4)]):
        rig.cube("body", (-0.5, a.top - 1, a.front + z), (2, h, 2), key=f"snag_{i}",
                 rotation=(rx, 0, rz), pivot=(0, a.top - 1, a.front + z + 1))
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((2, a.head_y + 5, a.head_back - 4), (2, 2, 2), sign), (2, 2, 2),
        key=f"eye_{side}"))
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 3, a.back - 1), direction=1,
                      segments=[(7, 5, 4), (6, 3, 3)], rotations=[(-8, 0, 0), (-4, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), BARK, top=BARK_LIT, dither=0.1, dither_colour=BARK_DARK)
    # Grain: long dark lines running the length of every surface, broken at knots.
    for key in ("chest", "barrel", "croup", "head", "muzzle", "jaw") + tuple(a.tail):
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            horizontal = face != "top"
            for i in range(1, (h if horizontal else w), 2):
                if horizontal:
                    skin.hline((x, y, w, h), i, BARK_DARK)
                else:
                    skin.vline((x, y, w, h), i, BARK_DARK)
    for key in ("chest", "barrel", "croup"):
        skin.dither(skin.face(key, "top"), MOSS, 0.25, 3, grain="fleck")
    skin.skin(rig.keys("snag_"), BARK_DARK, dither=0.0)
    skin.skin("jaw", BARK, dither=0.0)
    x, y, w, h = skin.face("jaw", "top")
    skin.rect((x, y, w, h), MOUTH)
    for col in range(0, w, 2):
        skin.d.point((x + col, y), fill=TEETH)
        skin.d.point((x + col, y + h - 1), fill=TEETH)
    skin.skin(rig.keys("eye_"), EYE, dither=0.0)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=6.0, walk_len=0.7, stride=26.0, bob_amp=0.1)
    idle["body"] = {"rotation": kf((0.0, (0, 0, 0)), (6.0, (0, 0, 0)))}   # it holds still: a log
    idle["head"] = {"rotation": kf((0.0, (0, 0, 0)), (5.0, (0, 0, 0)), (5.4, (0, 12, 0)),
                                   (6.0, (0, 0, 0)))}
    walk["body"] = {"rotation": kf((0.0, (0, 8, 0)), (0.35, (0, -8, 0)), (0.7, (0, 8, 0)))}
    bite = {
        "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (40, 0, 0)), (0.3, (0, 0, 0)),
                               (0.5, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-18, 0, 0)), (0.3, (6, 0, 0)),
                                (0.5, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.12, (0, 1, -1.5)), (0.5, (0, 0, 0)))},
    }
    hiss = {
        "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (30, 0, 0)), (1.0, (26, 0, 0)),
                               (1.3, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.3, (0, 0, 0)))},
    }
    return {"idle": clip(6.0, idle), "walk": clip(0.7, walk),
            "bite": clip(0.5, bite, loop=False), "hiss": clip(1.3, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "dugbog_model.py", build, paint, anims, HITBOX))
