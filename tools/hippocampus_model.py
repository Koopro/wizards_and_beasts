#!/usr/bin/env python3
"""The Hippocampus: rig, skin and animation.

Canon: originally from Greece, the front half of a horse and the tail and hindquarters of a giant
fish; it lays large translucent eggs through which the tadpole-foal can be seen. PASSIVE,
AMPHIBIOUS.

A horse's head, neck and forelegs, the forelegs ending in fins instead of hooves, and at the
withers the coat turns to scales and runs back into a long fish tail with a broad fin. The swim
clip is the tail doing the work while the forelegs paddle, which is the only thing a horse half
could do in water.

Run from the repo root:  python tools/hippocampus_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, hang, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "hippocampus"
HITBOX = (1.7, 1.9)

HIDE = hx("#4E7E8E")
HIDE_DARK = hx("#34586A")
SCALE = hx("#3A8A7A")
SCALE_DARK = hx("#256052")
BELLY = hx("#BCD8D0")
FIN = hx("#8ED8C8")
FIN_DARK = hx("#5AA898")
EYE = hx("#10181C")


def build():
    rig = Rig(CID, 128)
    rig.bone("body", "root", (0, 16, 0))
    rig.cube("body", (-4.5, 11, -4), (9, 10, 8), key="chest")
    rig.bone("neck", "body", (0, 19, -2), (30, 0, 0))
    rig.cube("neck", (-2.5, 19, -4.5), (5, 10, 5), key="neck")
    rig.cube("neck", (-0.5, 20, 0), (1, 10, 4), key="crest")
    rig.bone("head", "neck", (0, 29, -2), (-20, 0, 0))
    rig.cube("head", (-2.5, 24, -10), (5, 6, 8), key="head")
    rig.cube("head", (-2, 24, -13), (4, 4, 3), key="muzzle")
    rig.pair(lambda side, sign: rig.cube("head", Rig.mirror((1.5, 30, -4), (1, 3, 2), sign), (1, 3, 2),
                                         key=f"ear_{side}"))
    rig.pair(lambda side, sign: hang(rig, f"foreleg_{side}", "body", x=2.5, z=-1, sign=sign, top=12,
                                     segments=[("", 6, 3, 3, (-20, 0, 0)), ("fin", 4, 4, 5, (30, 0, 0))]))
    tail = chain(rig, "tail", "body", start=(0, 16, 4), direction=1,
                 segments=[(8, 8, 8), (8, 6, 6), (7, 5, 4), (6, 3, 3)],
                 rotations=[(10, 0, 0), (12, 0, 0), (10, 0, 0), (-10, 0, 0)])
    rig.cube(tail[-1], (-0.5, 8, 33), (1, 12, 8), key="fluke")
    ground(rig)
    return rig, tail


def paint(rig, tex_h, tail):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["chest", "neck", "head", "muzzle"] + rig.keys("ear_", "foreleg"), HIDE, bottom=BELLY,
              dither=0.05, dither_colour=HIDE_DARK)
    skin.skin(list(tail), SCALE, bottom=BELLY, dither=0.0)
    for key in tail:
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_DARK)
    skin.skin(["crest", "fluke"] + [k for k in rig.keys("foreleg") if "fin" in k], FIN, dither=0.0)
    skin.bands(["crest", "fluke"], FIN_DARK, faces=("east", "west"), step=2)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, EYE)
    return skin


def anims(tail):
    idle = {**undulate(4.0, list(tail), amp=6.0, wave=0.15),
            "neck": osc(4.0, 4.0), "foreleg_left": osc(4.0, 12.0), "foreleg_right": osc(4.0, 12.0, phase=0.5),
            "root": {"position": kf((0.0, (0, 0, 0)), (2.0, (0, 1, 0)), (4.0, (0, 0, 0)))}}
    swim = {**undulate(1.2, list(tail), amp=16.0, axis="x", wave=0.2, grow=4.0),
            "foreleg_left": osc(1.2, 40.0), "foreleg_right": osc(1.2, 40.0, phase=0.5),
            "neck": osc(1.2, 6.0, phase=0.25)}
    call = {"neck": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-24, 0, 0)), (1.1, (-20, 0, 0)), (1.5, (0, 0, 0)))},
            "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (10, 0, 0)), (1.5, (0, 0, 0)))},
            tail[-1]: {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-30, 0, 0)), (1.0, (20, 0, 0)), (1.5, (0, 0, 0)))}}
    return {"idle": clip(4.0, idle), "swim": clip(1.2, swim), "call": clip(1.5, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "hippocampus_model.py", build, paint, anims, HITBOX))
