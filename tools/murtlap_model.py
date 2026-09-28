#!/usr/bin/env python3
"""The Murtlap: rig, skin and animation.

Canon: a rat-like creature of coastal Britain with a growth on its back that looks like a sea
anemone; pickled and eaten, the growth gives resistance to curses and jinxes, and its essence
soothes cuts (Harry soaks his hand in it after Umbridge's detentions). AMPHIBIOUS, `heal_aura`.

The rat is ordinary on purpose so the anemone carries the identity: a fleshy base on the spine
with a crown of short tentacles fanned out by cube rotation, coral orange against grey-brown fur,
and swaying in its own time in every clip.

Run from the repo root:  python tools/murtlap_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "murtlap"
HITBOX = (0.65, 0.75)

FUR = hx("#857565")
FUR_DARK = hx("#5E5246")
PINK = hx("#D69A92")
ANEMONE = hx("#E2744A")
ANEMONE_TIP = hx("#F6C07A")
ANEMONE_BASE = hx("#B04A3A")
EYE = hx("#140E0C")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=4,
        chest=(5, 5, 4), barrel=(6, 5, 6), croup=(5, 5, 4), croup_drop=-4,
        neck=None, neck_rake=0, head=(4, 4, 4), head_y=4, head_pitch=6, muzzle=(2, 2, 3),
        fore=[("", 2, 2, 2, None), ("paw", 2, 2, 3, None)],
        hind=[("", 2, 2, 3, (10, 0, 0)), ("paw", 2, 2, 3, (-10, 0, 0))],
        fore_x=2, hind_x=2)
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((1, a.head_y + 4, a.head_back - 2), (2, 2, 1), sign), (2, 2, 1),
        key=f"ear_{side}"))
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 2, a.back - 1), direction=1,
                      segments=[(5, 1, 1), (5, 1, 1)], rotations=[(-12, 0, 0), (8, 0, 0)])

    # The anemone: a base on the spine and a crown of tentacles fanned round it.
    top = a.top
    rig.bone("anemone", "body", (0, top, a.front + 7))
    rig.cube("anemone", (-2, top - 1, a.front + 5), (4, 2, 4), key="anemone_base")
    for i, (dx, dz, rx, rz) in enumerate([(-1.5, -1, -24, 22), (0, -1.5, -30, 0), (1.5, -1, -24, -22),
                                          (-1.5, 1, 24, 22), (0, 1.5, 30, 0), (1.5, 1, 24, -22),
                                          (0, 0, 0, 0)]):
        rig.cube("anemone", (dx - 0.5, top + 1, a.front + 7 + dz - 0.5), (1, 4 if i < 6 else 5, 1),
                 key=f"tentacle_{i}", rotation=(rx, 0, rz), pivot=(dx, top + 1, a.front + 7 + dz))
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), FUR, dither=0.1, dither_colour=FUR_DARK)
    skin.skin(rig.keys("ear_", "tail") + ["muzzle"] + [k for k in rig.keys("foreleg", "hindleg")
                                                       if "paw" in k], PINK, dither=0.0)
    skin.skin("anemone_base", ANEMONE_BASE, dither=0.2, dither_colour=ANEMONE)
    skin.skin(rig.keys("tentacle_"), ANEMONE, dither=0.0)
    skin.tip(rig.keys("tentacle_"), ANEMONE_TIP, rows=1)
    skin.rect(skin.face("anemone_base", "top"), ANEMONE)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)
    skin.mark("muzzle", "north", 0, 0, hx("#8A5050"), w=2)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=2.8, walk_len=0.45, stride=34.0, bob_amp=0.2)
    sway = {"rotation": kf((0.0, (6, 0, -8)), (1.4, (-6, 0, 8)), (2.8, (6, 0, -8)))}
    idle["anemone"] = sway
    walk["anemone"] = {"rotation": kf((0.0, (10, 0, 0)), (0.22, (-6, 0, 0)), (0.45, (10, 0, 0)))}
    bite = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-18, 0, 0)), (0.25, (14, 0, 0)),
                                (0.45, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0.4, -1)), (0.45, (0, 0, 0)))},
    }
    # `flinch`: the anemone clamps shut and the rat flattens under it.
    flinch = {
        "anemone": {"scale": kf((0.0, (1, 1, 1)), (0.1, (0.7, 0.5, 0.7)), (0.8, (0.7, 0.5, 0.7)),
                                (1.1, (1, 1, 1)))},
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.1, (1.05, 0.9, 1.0)), (1.1, (1, 1, 1)))},
    }
    return {"idle": clip(2.8, idle), "walk": clip(0.45, walk),
            "bite": clip(0.45, bite, loop=False), "flinch": clip(1.1, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "murtlap_model.py", build, paint, anims, HITBOX))
