#!/usr/bin/env python3
"""The Billywig: rig, skin and animation.

Canon: an Australian insect about half an inch long, vivid sapphire blue, so fast it is rarely
noticed until it stings; its wings are attached to the top of its head and rotate very fast, so it
spins as it flies. Dried stings go into Fizzing Whizzbees. `status_on_hit`, `bioluminescence`.

The rotor is the Billywig. Two blades crossed on a bone at the crown that spins on Y in every clip,
over a slim blue body with a sting — and the body glows, because sapphire in a dark room is the
only way anyone ever sees one.

Run from the repo root:  python tools/billywig_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from insect import hover  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "billywig"
HITBOX = (0.45, 0.45)

BLUE = hx("#1E4ED8")
BLUE_LIT = hx("#5A8CF4")
BLUE_DARK = hx("#12307E")
WING = hx("#C8DCF8")
STING = hx("#E8E0B0")
EYE = hx("#0A0C18")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 4, 0))
    rig.cube("body", (-1.5, 3, -3), (3, 3, 3), key="thorax")
    rig.cube("body", (-1, 3, 0), (2, 2, 4), key="abdomen")
    rig.cube("body", (-0.5, 3.5, 4), (1, 1, 3), key="sting")
    rig.cube("body", (-1.5, 3, -5), (3, 3, 2), key="head")
    for i, z in enumerate((-2, 0, 2)):
        for side, sign in (("l", 1), ("r", -1)):
            rig.cube("body", Rig.mirror((1.5, 1, z - 0.5), (1, 2, 1), sign), (1, 2, 1), key=f"leg_{side}{i}")
    rig.bone("rotor", "body", (0, 6, -4))
    rig.cube("rotor", (-4, 6, -4.5), (8, 1, 1), key="blade_a")
    rig.cube("rotor", (-0.5, 6.5, -8), (1, 1, 7), key="blade_b")
    rig.cube("rotor", (-0.5, 6, -4.5), (1, 1, 1), key="hub")
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["thorax", "abdomen", "head"], BLUE, top=BLUE_LIT, dither=0.1, dither_colour=BLUE_DARK)
    skin.bands("abdomen", BLUE_DARK, faces=("top", "east", "west"), step=2)
    skin.glow(["thorax", "abdomen"], faces=("top", "east", "west"))
    skin.skin("sting", STING, dither=0.0)
    skin.skin(rig.keys("leg_"), BLUE_DARK, dither=0.0)
    skin.skin(["blade_a", "blade_b", "hub"], WING, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    skin.d.point((x, y + 1), fill=EYE)
    skin.d.point((x + w - 1, y + 1), fill=EYE)
    return skin


def anims():
    spin = {"rotation": kf((0.0, (0, 0, 0)), (0.1, (0, 180, 0)), (0.2, (0, 360, 0)))}
    idle = {"rotor": spin, "root": hover(1.0, 0.6)}
    fly = {"rotor": spin, "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 180, 0)), (0.6, (0, 360, 0)))},
           "root": hover(0.6, 0.4)}
    flinch = {"rotor": spin,
              "root": {"position": kf((0.0, (0, 0, 0)), (0.06, (1.5, 1, 0)), (0.2, (0, 0, 0)))}}
    call = {"rotor": {"rotation": kf((0.0, (0, 0, 0)), (0.05, (0, 180, 0)), (0.1, (0, 360, 0)),
                                     (0.15, (0, 540, 0)), (0.2, (0, 720, 0)), (0.6, (0, 1800, 0)))},
            "body": {"rotation": kf((0.0, (-20, 0, 0)), (0.6, (-20, 0, 0)))}}
    return {"idle": clip(0.2, idle), "fly": clip(0.6, fly),
            "flinch": clip(0.2, flinch, loop=False), "call": clip(0.6, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "billywig_model.py", build, paint, anims, HITBOX))
