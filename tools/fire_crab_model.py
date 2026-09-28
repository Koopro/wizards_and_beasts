#!/usr/bin/env python3
"""The Fire Crab: rig, skin and animation.

Canon: despite its name, it resembles a large tortoise; its heavily jewelled shell is prized, and
it shoots flames from its rear end when attacked. Native to Fiji, where a stretch of coast is a
reserve. FIRE_IMMUNE, FIRE_ATTACK, `flame_burst`.

A tortoise's domed shell on crab legs, the shell set with gems that catch the light — lit in the
glowmask, since the jewels are why anyone knows the animal exists — a small blunt head at the front,
and a vent at the back that glows where the flame comes out.

Run from the repo root:  python tools/fire_crab_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from crab import crab, scuttle  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "fire_crab"
HITBOX = (0.65, 0.75)

SHELL = hx("#3E3446")
SHELL_LIT = hx("#5A4E64")
SKIN_ = hx("#8A6A4A")
GEMS = (hx("#E83A4A"), hx("#3AC87A"), hx("#3A8AE8"), hx("#E8C83A"))
VENT = hx("#F08A2A")
EYE = hx("#141010")


def build():
    rig = Rig(CID, 64)
    # Canon is "like a large tortoise" with a jewelled shell. The old rig stood the shell on six
    # long spider legs and read as an arachnid in the client. Two pairs of short, thick legs
    # tucked under the rim, a high dome and a head on a short neck make it a tortoise.
    legs = crab(rig, shell=(10, 4, 11), hip_y=3, leg=(1, 2, 4, 2, 3), pairs=2, leg_x=3.5)
    rig.cube("body", (-4, 6, -4.5), (8, 2, 9), key="dome")
    rig.cube("body", (-2.5, 8, -2.5), (5, 1, 5), key="crown")
    rig.bone("head", "body", (0, 4, -5.5))
    rig.cube("head", (-1, 3, -7.5), (2, 2, 2), key="neck")
    rig.cube("head", (-1.5, 3, -10), (3, 3, 3), key="head")
    rig.cube("body", (-1.5, 3, 5.5), (3, 2, 1), key="vent")
    ground(rig)
    return rig, legs


def paint(rig, tex_h, legs):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["shell", "dome", "crown"], SHELL, top=SHELL_LIT, dither=0.0)
    for key in ("shell", "dome", "crown"):
        for face in ("top", "east", "west", "north", "south"):
            x, y, w, h = skin.face(key, face)
            for i in range(0, w * h, 5):
                gx, gy = x + (i * 3) % w, y + (i // w) % h
                skin.d.point((gx, gy), fill=GEMS[i % 4])
                skin.glow_rect((gx, gy, 1, 1))
    skin.skin(["head", "neck"] + rig.keys("leg_"), SKIN_, dither=0.0)
    skin.skin("vent", VENT, dither=0.0)
    skin.glow("vent")
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)
    return skin


def anims(legs):
    idle = {"head": {"rotation": kf((0.0, (0, -10, 0)), (1.6, (4, 10, 0)), (3.2, (0, -10, 0)))},
            "body": {"position": kf((0.0, (0, 0, 0)), (1.6, (0, 0.2, 0)), (3.2, (0, 0, 0)))}}
    walk = {**scuttle(0.6, legs, amp=20.0), "head": osc(0.6, 4.0, axis="y")}
    attack = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-20, 0, 0)), (0.3, (12, 0, 0)), (0.5, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0, -1)), (0.5, (0, 0, 0)))}}
    # `hiss` is the flame: head pulled in, the whole shell tipping forward to aim the rear.
    hiss = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (40, 0, 0)), (0.9, (40, 0, 0)), (1.1, (0, 0, 0)))},
            "body": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (14, 0, 0)), (0.9, (14, 0, 0)), (1.1, (0, 0, 0)))}}
    return {"idle": clip(3.2, idle), "walk": clip(0.6, walk),
            "attack": clip(0.5, attack, loop=False), "hiss": clip(1.1, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "fire_crab_model.py", build, paint, anims, HITBOX))
