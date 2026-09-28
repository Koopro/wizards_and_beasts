#!/usr/bin/env python3
"""The Ashwinder: rig, skin and animation.

Canon: born when a magical fire is left to burn unchecked for too long — a thin, pale-grey serpent
with glowing red eyes that rises from the embers and slithers into the shadows, leaving a trail of
ash, and lays scorching red eggs within an hour that will set the house alight if not frozen.
FIRE_IMMUNE, FIRE_ATTACK, `ember_trail`.

Thin and ash-pale, with embers still showing through the scales along its back — lit in the
glowmask with the eyes, because a creature made of a dying fire should look like one in the dark.

Run from the repo root:  python tools/ashwinder_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, serpent, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "ashwinder"
HITBOX = (0.65, 0.75)

ASH = hx("#B8B2AC")
ASH_DARK = hx("#86807A")
EMBER = hx("#F07A2A")
EMBER_HOT = hx("#FCC85A")
EYE = hx("#F02A1A")


def build():
    rig = Rig(CID, 64)
    a = serpent(rig, y=1.5, head=(3, 2, 4),
                segments=[(4, 2, 2), (4, 2, 2), (4, 2, 2), (4, 2, 2), (4, 1, 1), (3, 1, 1)],
                yaws=[0, 25, -35, 35, -30, 20])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), ASH, dither=0.2, dither_colour=ASH_DARK)
    for key in a.segments[:4]:
        x, y, w, h = skin.face(key, "top")
        for col in range(0, w, 2):
            skin.d.point((x + col, y + (col // 2) % h), fill=EMBER if col % 4 else EMBER_HOT)
            skin.glow_rect((x + col, y + (col // 2) % h, 1, 1))
    for face in ("east", "west"):
        skin.mark("head", face, 1, 0, EYE, glow=True)
    return skin


def anims(a):
    segs = a.segments
    idle = {**undulate(3.0, segs, amp=4.0, wave=0.2), "head": {"rotation": kf((0.0, (0, -10, 0)), (1.5, (0, 10, 0)), (3.0, (0, -10, 0)))}}
    walk = undulate(0.8, segs, amp=18.0, wave=0.2)
    strike = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-30, 0, 0)), (0.35, (20, 0, 0)), (0.6, (0, 0, 0)))},
              segs[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-20, 0, 0)), (0.35, (10, 0, 0)), (0.6, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.35, (0, 0, -2)), (0.6, (0, 0, 0)))}}
    hiss = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (1.0, (-26, 0, 0)), (1.2, (0, 0, 0)))},
            segs[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (1.2, (0, 0, 0)))}}
    return {"idle": clip(3.0, idle), "walk": clip(0.8, walk),
            "strike": clip(0.6, strike, loop=False), "hiss": clip(1.2, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "ashwinder_model.py", build, paint, anims, HITBOX))
