#!/usr/bin/env python3
"""The Lethifold: rig, skin and animation.

Canon: a rare tropical creature that resembles a black cloak perhaps half an inch thick (thicker
if it has recently eaten), gliding along the ground at night; it smothers its sleeping victims and
digests them where they lie, leaving no trace. Only the Patronus Charm repels it. DEATH_GAZE,
`lethifold_smother`.

Half an inch thick is a single texel, so it is drawn as a cloak: a sheet lying on the ground that
rises at the front into a curling, rippling hood, four hinged panels that bend in a travelling
wave. Pure black, edged a shade lighter so the silhouette survives a dark room. It has no face.

Run from the repo root:  python tools/lethifold_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "lethifold"
HITBOX = (0.95, 1.25)

# A step off true black (texture audit 2026-09-27): at #0C0A0E the shroud had three colours
# and no readable fold — darker than the Dementor, which reads. Still the darkest thing in a room.
BLACK = hx("#1A1720")
EDGE = hx("#3A3444")
LINING = hx("#2C2434")


def build():
    rig = Rig(CID, 64)
    rig.bone("train", "root", (0, 0, 4))
    rig.cube("train", (-7, 0, 0), (14, 1, 10), key="train")
    parent, y = "train", 0.5
    panels = []
    for i in range(4):
        bone = f"panel_{i}"
        rig.bone(bone, parent, (0, y, 0), (-(20 + 8 * i) if i else -70, 0, 0))
        rig.cube(bone, (-7 + i * 0.5, y, -1), (14 - i, 6, 1), key=bone)
        panels.append(bone)
        parent, y = bone, y + 6
    ground(rig)
    return rig, panels


def paint(rig, tex_h, panels):
    skin = Skin(rig, tex_h, grain="strand")
    skin.skin(rig.keys(""), BLACK, dither=0.12, dither_colour=LINING)
    for key in ["train"] + list(panels):
        for face in ("north", "south", "top"):
            x, y, w, h = skin.face(key, face)
            skin.vline((x, y, w, h), 0, EDGE)
            skin.vline((x, y, w, h), w - 1, EDGE)
    skin.hline(skin.face(panels[-1], "north"), 0, EDGE)
    return skin


def anims(panels):
    ripple = {p: osc(3.0, 6.0 + 2 * i, phase=-0.18 * i) for i, p in enumerate(panels)}
    idle = {**ripple, "train": {"scale": kf((0.0, (1, 1, 1)), (1.5, (1.02, 1, 1.04)), (3.0, (1, 1, 1)))}}
    walk = {**{p: osc(1.0, 10.0 + 3 * i, phase=-0.2 * i) for i, p in enumerate(panels)},
            "train": osc(1.0, 4.0, axis="z")}
    # `attack` is the smother: the hood rears and folds over the full length of the victim.
    attack = {panels[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.7, (40, 0, 0)), (1.2, (0, 0, 0)))},
              **{p: {"rotation": kf((0.0, (0, 0, 0)), (0.3 + 0.05 * i, (-10, 0, 0)), (0.7 + 0.05 * i, (30, 0, 0)), (1.2, (0, 0, 0)))}
                 for i, p in enumerate(panels[1:], start=1)}}
    flinch = {p: {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 0)), (0.5, (0, 0, 0)))} for p in panels}
    return {"idle": clip(3.0, idle), "walk": clip(1.0, walk),
            "attack": clip(1.2, attack, loop=False), "flinch": clip(0.5, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "lethifold_model.py", build, paint, anims, HITBOX))
