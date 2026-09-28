#!/usr/bin/env python3
"""The Jarvey: rig, skin and animation.

Canon: an overgrown ferret that can talk, although it never says anything worth hearing — short,
rude phrases, fired off in a stream. It hunts gnomes. THIEF and `jarvey_jinx` here.

A ferret is a tube on four short legs: a body three times as long as it is tall, a small
wedge-shaped head that flows straight into the neck, and a tail half the body's length. The
bandit mask across the eyes is the ferret-ness; the size and the open mouth are the Jarvey.

Run from the repo root:  python tools/jarvey_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "jarvey"
HITBOX = (0.65, 0.75)

FUR = hx("#7A5B3C")
FUR_DARK = hx("#553E28")
CREAM = hx("#E3CFA8")
MASK = hx("#3A2A1C")
NOSE = hx("#C47A7A")
EYE = hx("#120C08")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=5,
        chest=(5, 5, 4), barrel=(4, 4, 11), croup=(5, 5, 4), croup_drop=-2,
        neck=None, neck_rake=0, head=(4, 4, 5), head_y=5, muzzle=(3, 2, 2),
        fore=[("", 3, 2, 2, None), ("paw", 2, 2, 3, None)],
        hind=[("", 3, 2, 3, None), ("paw", 2, 2, 3, None)],
        fore_x=1.5, hind_x=1.5)
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((1, a.head_y + 4, a.head_back - 2), (2, 1, 1), sign), (2, 1, 1),
        key=f"ear_{side}"))
    rig.bone("jaw", "head", (0, a.head_y + 1, a.head_front + 1))
    rig.cube("jaw", (-1, a.head_y - 1, a.muzzle_front + 0.5), (2, 1, 3), key="jaw")
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 4, a.back - 1), direction=1,
                      segments=[(5, 2, 2), (4, 2, 2)], rotations=[(-6, 0, 0), (6, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), FUR, dither=0.08, dither_colour=FUR_DARK)
    skin.skin(["head", "muzzle", "jaw"], CREAM, dither=0.0)
    skin.skin(rig.keys("foreleg", "hindleg"), FUR_DARK, dither=0.0)
    skin.tip(a.tail[-1], MASK, rows=3)
    skin.rect(skin.face("chest", "north"), CREAM)
    # The bandit mask: a dark band straight across the eyes and round the sides of the head.
    for face in ("north", "east", "west"):
        x, y, w, h = skin.face("head", face)
        skin.hline((x, y, w, h), 1, MASK)
        skin.hline((x, y, w, h), 2, MASK)
    skin.mark("head", "north", 0, 1, EYE)
    skin.mark("head", "north", -1, 1, EYE)
    skin.mark("muzzle", "north", 1, 0, NOSE)
    skin.skin("jaw", hx("#8A4A4A"), dither=0.0)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=2.4, walk_len=0.45, stride=34.0, bob_amp=0.4)
    # A ferret's run is a bound: the spine flexes, so the body bobs twice per stride.
    walk["body"] = {"rotation": kf((0.0, (0, 0, 0)), (0.11, (6, 0, 0)), (0.22, (0, 0, 0)),
                                   (0.34, (-6, 0, 0)), (0.45, (0, 0, 0)))}
    bite = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 0)), (0.25, (16, 0, 0)),
                                (0.45, (0, 0, 0)))},
        "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (30, 0, 0)), (0.25, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0, -1.5)), (0.45, (0, 0, 0)))},
    }
    # `call` is the insult: a rapid run of jaw snaps with the head bobbing to its own rhythm.
    call = {
        "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (26, 0, 0)), (0.2, (0, 0, 0)),
                               (0.3, (22, 0, 0)), (0.4, (0, 0, 0)), (0.5, (30, 0, 0)),
                               (0.7, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-10, 8, 0)), (0.45, (-6, -8, 0)),
                                (0.7, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-10, 0, 0)), (0.7, (0, 0, 0)))},
    }
    return {"idle": clip(2.4, idle), "walk": clip(0.45, walk),
            "bite": clip(0.45, bite, loop=False), "call": clip(0.7, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "jarvey_model.py", build, paint, anims, HITBOX))
