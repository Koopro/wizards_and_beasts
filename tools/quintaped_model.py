#!/usr/bin/env python3
"""The Quintaped: rig, skin and animation.

Canon: the Hairy MacBoon, found only on the Isle of Drear — a highly dangerous carnivore with a low
body covered in thick reddish-brown hair and five legs, each ending in a club foot; the two feuding
clans that once lived on the island were transfigured into them. Hostile, POISON_ATTACK, CHARGE,
`pack_tactics`.

Five legs cannot be mirrored, so they are not: each is a solved splayed leg at its own angle
around a low round shaggy body, with a thick club at the end, and the gait walks them in a
five-beat ripple. A small face peers out of the hair at the front, eyes lit.

Run from the repo root:  python tools/quintaped_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, sphere, splay_leg  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "quintaped"
HITBOX = (0.95, 1.25)

HAIR = hx("#8A4A2A")
HAIR_DARK = hx("#5E2E18")
HAIR_LIT = hx("#AA6A40")
CLUB = hx("#3A2A20")
EYE = hx("#E8C23A")


def build():
    rig = Rig(CID, 128)
    rig.bone("body", "root", (0, 9, 0))
    ball = sphere(rig, "body", c=12, y0=4, key="body")
    legs = []
    for i in range(5):
        angle = -90 + 72 * i            # yaw from straight ahead, around the body
        legs.append(splay_leg(rig, f"leg_{i}", "body", hip_y=8, x=0, z=0, sign=1, yaw=angle,
                              coxa=6, femur=5, tibia=9, tarsus=5, thick=3, coxa_lift=4, femur_lift=30))
    rig.bone("face", "body", (0, 10, -6))
    rig.cube("face", (-2.5, 8, -8.5), (5, 4, 2), key="face")
    ground(rig)
    return rig, ball, legs


def paint(rig, tex_h, ball, legs):
    skin = Skin(rig, tex_h)
    skin.skin(ball + rig.keys("leg_"), HAIR, top=HAIR_LIT, dither=0.35, dither_colour=HAIR_DARK, bevel=0)
    for key in ball:
        for face in ("east", "west", "north", "south"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y + (col % 3)), (x + col, y + h - 1)], fill=HAIR_DARK)
    skin.skin([k for k in rig.keys("leg_") if "tarsus" in k], CLUB, dither=0.0)
    skin.skin("face", HAIR_DARK, dither=0.0)
    x, y, w, h = skin.face("face", "north")
    for ex in (x + 1, x + w - 2):
        skin.d.point((ex, y + 1), fill=EYE)
        skin.glow_rect((ex, y + 1, 1, 1))
    return skin


def anims(ball, legs):
    idle = {"body": {"scale": kf((0.0, (1, 1, 1)), (2.0, (1.03, 0.97, 1.03)), (4.0, (1, 1, 1)))},
            "face": osc(4.0, 10.0, axis="y")}
    walk = {**{leg: osc(1.0, 18.0, axis="y", phase=i / 5.0) for i, leg in enumerate(legs)},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0.5, 0)), (0.4, (0, 0, 0)), (0.6, (0, 0.5, 0)),
                                    (0.8, (0, 0, 0)), (1.0, (0, 0, 0)))},
            "body": osc(1.0, 4.0, axis="z")}
    attack = {"body": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-14, 0, 0)), (0.5, (16, 0, 0)), (0.9, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.25, (0, 1, 1)), (0.5, (0, 0, -3)), (0.9, (0, 0, 0)))}}
    groan = {"body": {"scale": kf((0.0, (1, 1, 1)), (0.6, (1.08, 1.1, 1.08)), (1.6, (1, 1, 1)))},
             "face": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-20, 0, 0)), (1.6, (0, 0, 0)))}}
    return {"idle": clip(4.0, idle), "walk": clip(1.0, walk),
            "attack": clip(0.9, attack, loop=False), "groan": clip(1.6, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "quintaped_model.py", build, paint, anims, HITBOX))
