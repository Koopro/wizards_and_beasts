#!/usr/bin/env python3
"""The Golden Snidget: rig, skin and animation.

Canon: a tiny, perfectly round golden bird with a long thin beak and gleaming jewel-red eyes, whose
wings rotate on joints so it can change direction instantly; hunted nearly to extinction in
Quidditch until the Golden Snitch replaced it. FEARFUL, `evasion`.

The snitch silhouette is the brief: a gold ball with two thin silver wings. The body is the
puffskein's cross-of-slabs sphere at a smaller size, the beak a single long texel line, and the
wings are flat vertical blades that spin rather than flap.

Run from the repo root:  python tools/golden_snidget_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "golden_snidget"
HITBOX = (0.35, 0.35)

GOLD = hx("#E8B830")
GOLD_LIT = hx("#FAE28A")
GOLD_DARK = hx("#B88A1C")
SILVER = hx("#DCE2E8")
SILVER_DARK = hx("#A8B0BA")
EYE = hx("#D81E2A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 3, 0))
    rig.cube("body", (-2, 1, -2), (4, 4, 4), key="core")
    rig.cube("body", (-2.5, 2, -1.5), (5, 2, 3), key="slab_wide")
    rig.cube("body", (-1.5, 2, -2.5), (3, 2, 5), key="slab_deep")
    rig.cube("body", (-1.5, 0.5, -1.5), (3, 5, 3), key="slab_tall")
    rig.cube("body", (-0.5, 2.5, -6), (1, 1, 4), key="beak")
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"wing_{side}", "body", (sign * 2, 3.5, 0), (0, 0, sign * -30))
        rig.cube(f"wing_{side}", Rig.mirror((2, 3, -2), (6, 1, 5), sign), (6, 1, 5))
    rig.bone("legs", "body", (0, 1, 0))
    rig.cube("legs", (-1, 0, 0), (2, 1, 1), key="feet")
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["core", "slab_wide", "slab_deep", "slab_tall"]
    skin.skin(body, GOLD, top=GOLD_LIT, dither=0.1, dither_colour=GOLD_DARK)
    skin.skin(["beak", "feet"], GOLD_DARK, dither=0.0)
    skin.skin(rig.keys("wing_"), SILVER, dither=0.0)
    skin.bands(rig.keys("wing_"), SILVER_DARK, faces=("top", "bottom"), step=2)
    skin.glow(["slab_tall"], faces=("top",))
    x, y, w, h = skin.face("slab_deep", "north")
    skin.d.point((x, y), fill=EYE)
    skin.d.point((x + w - 1, y), fill=EYE)
    skin.glow_rect((x, y, 1, 1))
    skin.glow_rect((x + w - 1, y, 1, 1))
    return skin


def anims():
    # The wings never stop — a snidget hovers even when it is still, blades turning on their joints.
    hover = {
        "wing_left": {"rotation": kf((0.0, (0, 0, 40)), (0.05, (0, 0, -30)), (0.1, (0, 0, 40)))},
        "wing_right": {"rotation": kf((0.0, (0, 0, -40)), (0.05, (0, 0, 30)), (0.1, (0, 0, -40)))},
    }
    idle = {**hover, "root": {"position": kf((0.0, (0, 0, 0)), (0.5, (0, 0.6, 0)), (1.0, (0, 0, 0)))}}
    fly = {**hover, "body": osc(0.4, 10.0, axis="y")}
    flinch = {
        **hover,
        "root": {"position": kf((0.0, (0, 0, 0)), (0.06, (3, 1, 0)), (0.12, (-2, 2, 1)), (0.3, (0, 0, 0)))},
    }
    song = {
        **hover,
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 180, 0)), (0.6, (0, 360, 0)), (0.8, (0, 360, 0)))},
    }
    return {"idle": clip(1.0, idle), "fly": clip(0.4, fly),
            "flinch": clip(0.3, flinch, loop=False), "song": clip(0.8, song, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "golden_snidget_model.py", build, paint, anims, HITBOX))
