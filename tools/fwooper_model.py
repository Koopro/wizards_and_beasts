#!/usr/bin/env python3
"""The Fwooper: rig, skin and animation.

Canon: an African bird with extremely brightly coloured plumage — orange, pink, lime green or
yellow — whose feathers make fancy quills; its song is beautiful at first and eventually drives
the listener insane, so sold birds must carry a Silencing Charm. `fwooper_song` here.

A round songbird, drawn as loud as its canon: every plumage colour at once, banded, with a plume
of tail feathers carried high. The `song` clip is the whole point of the creature and rides the
ambient beat.

Run from the repo root:  python tools/fwooper_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import bird, bird_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "fwooper"
HITBOX = (0.65, 0.75)

ORANGE = hx("#F28A2A")
PINK = hx("#EE5A9A")
LIME = hx("#8ED83A")
YELLOW = hx("#F6D83A")
BEAK = hx("#3A3028")
EYE = hx("#141010")


def build():
    rig = Rig(CID, 64)
    a = bird(rig, leg_h=3, body=(7, 7, 8), head=(5, 5, 5), beak=(2, 2, 2), tail=(6, 1, 7),
             tail_lift=40, legs=[("", 2, 1, 1, None), ("foot", 1, 2, 3, None)], leg_x=1.5,
             wings=((7, 6, 1), (5, 5, 1)))
    rig.cube("head", (-1, a.head_top, a.head_front + 1), (2, 3, 3), key="tuft")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), ORANGE, dither=0.0)
    skin.bands(["torso"], PINK, faces=("east", "west", "north", "south", "top"), step=3)
    skin.skin(["head"], YELLOW, dither=0.0)
    skin.skin(rig.keys("wing_"), LIME, dither=0.0)
    skin.bands(rig.keys("wing_"), YELLOW, faces=("east", "west"), step=2)
    skin.skin(["tail", "tuft"], PINK, dither=0.0)
    skin.bands(["tail"], LIME, faces=("top", "bottom"), step=2)
    skin.skin("beak", BEAK, dither=0.0)
    skin.skin(rig.keys("leg_"), BEAK, dither=0.0)
    skin.eyes("head", EYE, row=1, inset=1)
    return skin


def anims(a):
    idle, _walk, fly = bird_loops(a, idle_len=2.6, fly_len=0.35, spread=80.0, amp=45.0)
    flinch = {
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.08, (1.2, 1.2, 1.2)), (0.4, (1, 1, 1)))},
        "tail": {"rotation": kf((0.0, (0, 0, 0)), (0.08, (-30, 0, 0)), (0.4, (0, 0, 0)))},
    }
    # `song`: head back, beak working, the whole bird swelling with each phrase.
    song = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-24, 0, 0)), (1.6, (-20, 0, 0)), (1.8, (0, 0, 0)))},
        "beak": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.45, (0, 0, 0)), (0.6, (14, 0, 0)),
                                (0.75, (0, 0, 0)), (0.9, (14, 0, 0)), (1.05, (0, 0, 0)), (1.2, (14, 0, 0)),
                                (1.35, (0, 0, 0)), (1.8, (0, 0, 0)))},
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.45, (1.08, 1.08, 1.08)), (0.9, (1, 1, 1)),
                             (1.35, (1.08, 1.08, 1.08)), (1.8, (1, 1, 1)))},
    }
    return {"idle": clip(2.6, idle), "fly": clip(0.35, fly),
            "flinch": clip(0.4, flinch, loop=False), "song": clip(1.8, song, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "fwooper_model.py", build, paint, anims, HITBOX))
