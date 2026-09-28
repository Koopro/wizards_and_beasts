#!/usr/bin/env python3
"""The Tebo: rig, skin and animation.

Canon: an ash-coloured warthog of the Congo and Zaire, able to turn invisible, which makes it
hard to escape or to catch; its hide is prized for protective shields and gloves. FEARFUL but
with `enrage` and `camouflage` — a skittish animal that is dangerous when cornered.

A warthog is a head with a pig attached. The skull is broad and flat and nearly as long as the
body is tall, carried low; warts stand out from the cheeks; tusks curl up from the jaw; a
bristle mane runs down the spine; and the tail is a thin whip carried straight up when it runs.

Run from the repo root:  python tools/tebo_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, pair_horn, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "tebo"
HITBOX = (1.7, 1.9)

ASH = hx("#8C8781")
ASH_DARK = hx("#65615C")
ASH_LIT = hx("#A6A29C")
BRISTLE = hx("#4B4744")
TUSK = hx("#E6DCC2")
SNOUT = hx("#6E5F5A")
EYE = hx("#1C1A18")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=11,
        chest=(15, 14, 10), barrel=(14, 13, 13), croup=(13, 12, 8), croup_drop=-5,
        neck=None, neck_rake=0, head=(12, 10, 12), head_pitch=18, head_y=11, muzzle=(8, 6, 5),
        fore=[("", 6, 5, 5, None), ("lower", 5, 3, 3, None)],
        hind=[("", 6, 5, 6, (8, 0, 0)), ("lower", 5, 3, 3, (-8, 0, 0))],
        fore_x=5, hind_x=5)

    rig.cube("body", (-1, a.top - 1, a.front + 2), (2, 3, 22), key="bristle")
    hy, hf, hb = a.head_y, a.head_front, a.head_back
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((6, hy + 3, hf + 3), (2, 3, 3), sign), (2, 3, 3), key=f"wart_{side}"))
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((5, hy + 8, hb - 4), (3, 4, 2), sign), (3, 4, 2), key=f"ear_{side}"))
    pair_horn(rig, "head", name="tusk", base=(3.5, hy + 1, a.muzzle_front + 2),
              size=(2, 4, 2), tip=(1, 3, 1), rotation=(-30, 0, -34))

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 10, a.back - 1), direction=1,
                      segments=[(7, 1, 1), (3, 2, 2)], rotations=[(-10, 0, 0), (0, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "head", "muzzle"]
    skin.skin(body, ASH, top=ASH_LIT, dither=0.14, dither_colour=ASH_DARK)
    skin.skin(legs, ASH, dither=0.1, dither_colour=ASH_DARK)
    skin.tip([k for k in legs if "lower" in k], BRISTLE, rows=1)
    skin.skin(["bristle"] + rig.keys("ear_", "tail"), BRISTLE, dither=0.0)
    skin.bands("bristle", ASH_DARK, faces=("east", "west"), step=2)
    skin.skin(rig.keys("wart_"), ASH_DARK, dither=0.0)
    skin.skin(rig.keys("tusk_"), TUSK, dither=0.0)

    x, y, w, h = skin.face("muzzle", "north")
    skin.rect((x, y, w, h), SNOUT)
    skin.mark("muzzle", "north", 2, 2, BRISTLE)
    skin.mark("muzzle", "north", -3, 2, BRISTLE)
    for face in ("east", "west"):
        skin.mark("head", face, 3, 2, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.4, walk_len=0.7, stride=26.0, bob_amp=0.5)
    walk.update(knee_tracks(a.rig, legs, 0.7, 14.0))
    walk["tail_1"] = {"rotation": kf((0.0, (-70, 0, 0)), (0.7, (-70, 0, 0)))}  # flagged up
    attack = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (20, 0, 0)), (0.45, (-28, 14, 0)),
                                (0.8, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.45, (0, 0.5, -2.5)), (0.8, (0, 0, 0)))},
    }
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (14, 0, 0)), (0.4, (6, 0, 0)),
                                (0.6, (16, 0, 0)), (0.9, (0, 0, 0)))},
    }
    return {"idle": clip(3.4, idle), "walk": clip(0.7, walk),
            "attack": clip(0.8, attack, loop=False), "groan": clip(0.9, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "tebo_model.py", build, paint, anims, HITBOX))
