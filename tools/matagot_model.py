#!/usr/bin/env python3
"""The Matagot: rig, skin and animation.

Canon (Fantastic Beasts): the French Ministry's guardians — spectral, hairless black cats with
glowing blue eyes, which multiply when attacked. `duplication` and `damage_reduction` here.

Hairless is the whole brief: no fur texture, no tuft, no stripes. Bare wrinkled skin over a gaunt
frame, oversized bat-like ears, a thin rat's tail, and eyes that are the only light on it. A
matagot that reads as a black house cat has failed.

Run from the repo root:  python tools/matagot_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "matagot"
HITBOX = (0.6, 0.7)

SKIN_ = hx("#24212A")
WRINKLE = hx("#17151C")
SHEEN = hx("#3A3644")
EYE = hx("#8FE6F2")
EYE_CORE = hx("#E6FBFF")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=7,
        chest=(5, 5, 4), barrel=(4, 4, 7), croup=(5, 5, 4), croup_drop=-3,
        neck=None, neck_rake=0, head=(5, 4, 5), head_y=8, muzzle=(3, 2, 2),
        fore=[("", 5, 2, 2, None), ("paw", 2, 2, 3, None)],
        hind=[("", 5, 2, 3, (14, 0, 0)), ("paw", 2, 2, 3, (-10, 0, 0))],
        fore_x=1.5, hind_x=1.5)

    # Bat ears: taller than the skull and flared out.
    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, a.head_y + 4, a.head_back - 3),
                 (-8, 0, sign * -20))
        rig.cube(f"ear_{side}", Rig.mirror((0.5, a.head_y + 4, a.head_back - 3.5), (2, 5, 1), sign),
                 (2, 5, 1))

    rig.pair(build_ear)
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 4, a.back - 1), direction=1,
                      segments=[(5, 1, 1), (5, 1, 1)], rotations=[(30, 0, 0), (-20, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    everything = rig.keys("")
    skin.skin(everything, SKIN_, top=SHEEN, dither=0.0)
    # Wrinkles, not fur: short horizontal folds, sparse, over the shoulders and haunch.
    skin.bands(["chest", "croup"], WRINKLE, faces=("east", "west"), step=2, offset=1)
    x, y, w, h = skin.face("head", "north")
    for ex in (x, x + w - 2):
        skin.d.rectangle([ex, y + 1, ex + 1, y + 2], fill=EYE)
        skin.d.point((ex + (1 if ex == x else 0), y + 1), fill=EYE_CORE)
        skin.glow_rect((ex, y + 1, 2, 2))
    skin.skin(rig.keys("ear_"), SHEEN, dither=0.0)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.0, walk_len=0.6, stride=30.0, bob_amp=0.2)
    walk.update(knee_tracks(a.rig, legs, 0.6, 16.0))
    idle["ear_left"] = {"rotation": kf((0.0, (0, 0, 0)), (1.5, (0, 0, -10)), (3.0, (0, 0, 0)))}
    idle["ear_right"] = {"rotation": kf((0.0, (0, 0, 0)), (1.5, (0, 0, 10)), (3.0, (0, 0, 0)))}
    strike = {
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-80, 0, 0)), (0.35, (20, 0, 0)),
                                        (0.6, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-12, 0, 0)), (0.6, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (10, 0, 0)), (0.6, (0, 0, 0)))},
    }
    hiss = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-8, 0, 0)), (0.9, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.25, (0, 1, 0)), (0.9, (0, 0, 0)))},
        "ear_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-40, 0, -30)), (0.9, (0, 0, 0)))},
        "ear_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-40, 0, 30)), (0.9, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (40, 0, 0)), (0.9, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.6, walk),
            "strike": clip(0.6, strike, loop=False), "hiss": clip(0.9, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "matagot_model.py", build, paint, anims, HITBOX))
