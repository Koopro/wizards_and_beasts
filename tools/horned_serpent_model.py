#!/usr/bin/env python3
"""The Horned Serpent: rig, skin and animation.

Canon: the great horned water serpent of North American lore that gave Ilvermorny a house — a
horn on its brow and a jewel set in its forehead, which gives the power of invisibility and flight;
Isolt Sayre's wand cores came from the horn. `bioluminescence`, AQUATIC.

A long, dark serpent with a gold belly, a single swept horn rising from the brow, and the jewel
below it, lit — the one thing on the animal that glows, as in every depiction of it.

Run from the repo root:  python tools/horned_serpent_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, serpent, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "horned_serpent"
HITBOX = (1.5, 1.3)

SCALE = hx("#23382E")
SCALE_LIT = hx("#3E5E4C")
BELLY = hx("#C8A84A")
HORN = hx("#D8CCA8")
JEWEL = hx("#E8283A")
EYE = hx("#E8C84A")


def build():
    rig = Rig(CID, 128)
    a = serpent(rig, y=4, head=(7, 6, 8), jaw=(6, 2, 7),
                segments=[(8, 6, 6), (8, 6, 6), (7, 5, 5), (7, 4, 4), (6, 3, 3), (5, 2, 2)],
                yaws=[0, 28, 26, -34, -38, 30])
    # The front rears off the ground and the rest lies in a wide S. The first pass alternated its
    # yaws link by link (+20/-30/+30...), which cancel, and in a running client the serpent was a
    # straight bar — a serpent reads by its curve (Bible, SERPENTINE).
    first, second = a.segments[0], a.segments[1]
    rig[first].rotation = (-30, 0, 0)
    rig[second].rotation = (30, rig[second].rotation[1], 0)
    rig.bone("horn", "head", (0, a.head_top, -5), (-50, 0, 0))
    rig.cube("horn", (-1, a.head_top, -6), (2, 4, 2), key="horn_base")
    rig.cube("horn", (-0.5, a.head_top + 4, -5.5), (1, 4, 1), key="horn_tip")
    rig.cube("head", (-1, a.head_top - 3, a.head_front - 0.5), (2, 2, 1), key="jewel")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["head", "jaw"] + list(a.segments)
    skin.skin(body, SCALE, bottom=BELLY, dither=0.0)
    for key in body:
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_LIT)
    skin.skin(["horn_base", "horn_tip"], HORN, dither=0.0)
    skin.skin("jewel", JEWEL, dither=0.0)
    skin.glow("jewel")
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, EYE)
    return skin


def anims(a):
    segs = a.segments
    idle = {**undulate(4.0, segs, amp=5.0, wave=0.18), "head": {"rotation": kf((0.0, (0, -8, 0)), (2.0, (-4, 8, 0)), (4.0, (0, -8, 0)))}}
    swim = undulate(1.4, segs, amp=20.0, wave=0.2, grow=2.0)
    bite = {"jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (40, 0, 0)), (0.35, (0, 0, 0)))},
            "head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-20, 0, 0)), (0.35, (14, 0, 0)), (0.6, (0, 0, 0)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -3)), (0.6, (0, 0, 0)))}}
    hiss = {"jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (30, 0, 0)), (1.0, (26, 0, 0)), (1.3, (0, 0, 0)))},
            segs[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-30, 0, 0)), (1.3, (0, 0, 0)))}}
    return {"idle": clip(4.0, idle), "swim": clip(1.4, swim),
            "bite": clip(0.6, bite, loop=False), "hiss": clip(1.3, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "horned_serpent_model.py", build, paint, anims, HITBOX))
