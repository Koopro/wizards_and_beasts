#!/usr/bin/env python3
"""The Sea Serpent: rig, skin and animation.

Canon: found in the Atlantic, Pacific and Mediterranean, up to a hundred feet long, with a
horse-like head and a long serpentine body that rises out of the sea in humps; no Muggle has ever
been killed by one despite the hysterical stories. AQUATIC, `constrict`.

The horse-like head is the canon and the humps are the picture everybody has, so the body is
authored rising and falling as well as curving — each link pitched as well as yawed — and a red
fin-crest runs from the poll down the neck.

Run from the repo root:  python tools/sea_serpent_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "sea_serpent"
HITBOX = (2.7, 2.9)

SCALE = hx("#2A5A6A")
SCALE_LIT = hx("#3E7A8A")
BELLY = hx("#C8D8C8")
CREST = hx("#C83A2A")
CREST_DARK = hx("#8A2418")
EYE = hx("#F0E04A")


def build():
    rig = Rig(CID, 128)
    segs = chain(rig, "seg", "root", start=(0, 14, 0), direction=1,
                 segments=[(12, 11, 11), (11, 10, 10), (11, 9, 9), (10, 8, 8), (10, 7, 7), (9, 5, 5), (8, 3, 3)],
                 rotations=[(-30, 0, 0), (40, 20, 0), (-45, -20, 0), (45, 20, 0), (-40, -15, 0), (30, 10, 0), (-20, 0, 0)])
    rig.bone("head", segs[0], (0, 14, 0), (30, 0, 0))
    rig.cube("head", (-5, 10, -12), (10, 9, 12), key="head")
    rig.cube("head", (-3.5, 10, -17), (7, 6, 5), key="muzzle")
    rig.bone("jaw", "head", (0, 10, -4))
    rig.cube("jaw", (-3, 8, -16), (6, 2, 12), key="jaw")
    rig.cube("head", (-0.5, 19, -8), (1, 5, 10), key="crest_head")
    rig.cube(segs[0], (-0.5, 19, 1), (1, 5, 10), key="crest_neck")
    rig.pair(lambda side, sign: rig.cube("head", Rig.mirror((4, 17, -4), (2, 4, 2), sign), (2, 4, 2), key=f"ear_{side}"))
    ground(rig)
    return rig, segs


def paint(rig, tex_h, segs):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["head", "muzzle", "jaw"] + list(segs)
    skin.skin(body, SCALE, bottom=BELLY, dither=0.0)
    for key in body:
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 3):
                for col in range(row // 3 % 2, w, 3):
                    skin.d.point((x + col, y + row), fill=SCALE_LIT)
    skin.skin(["crest_head", "crest_neck"] + rig.keys("ear_"), CREST, dither=0.0)
    skin.bands(["crest_head", "crest_neck"], CREST_DARK, faces=("east", "west"), step=2)
    for face in ("east", "west"):
        skin.mark("head", face, 3, 2, EYE, w=2, h=2)
    return skin


def anims(segs):
    idle = {**undulate(5.0, segs, amp=4.0, axis="x", wave=0.15), "head": {"rotation": kf((0.0, (0, -8, 0)), (2.5, (0, 8, 0)), (5.0, (0, -8, 0)))}}
    swim = {**undulate(2.0, segs, amp=10.0, axis="x", wave=0.2), **{k: v for k, v in undulate(2.0, segs[3:], amp=8.0).items()}}
    bite = {"jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (35, 0, 0)), (0.6, (0, 0, 0)))},
            segs[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.6, (16, 0, 0)), (1.0, (0, 0, 0)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.6, (0, -1, -4)), (1.0, (0, 0, 0)))}}
    hiss = {"jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (28, 0, 0)), (1.4, (24, 0, 0)), (1.8, (0, 0, 0)))},
            "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-20, 0, 0)), (1.8, (0, 0, 0)))}}
    return {"idle": clip(5.0, idle), "swim": clip(2.0, swim),
            "bite": clip(1.0, bite, loop=False), "hiss": clip(1.8, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "sea_serpent_model.py", build, paint, anims, HITBOX))
