#!/usr/bin/env python3
"""The Wampus Cat: rig, skin and animation.

The Appalachian cat-creature Ilvermorny named a house after: a mountain lion, fast enough to
outrun arrows and hard to kill, whose yellow stare is said to hypnotise. Hostile here, with
`leap`, `frenzy` and `damage_reduction`.

It must not be the Kneazle scaled up. A cougar is long and low, small-headed, with a tail as long
as its body carried in a curve near the ground, and no stripes or rosettes — a plain tawny-grey
coat with a dark saddle. The eyes are the only saturated colour on it, which is the legend.

Run from the repo root:  python tools/wampus_cat_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "wampus_cat"
HITBOX = (1.7, 1.9)

COAT = hx("#9B8668")
COAT_DARK = hx("#6E5D46")
BELLY = hx("#C8B698")
SADDLE = hx("#5A4C3A")
EYE = hx("#F2D23A")
PUPIL = hx("#1A140C")
NOSE = hx("#3E2E24")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=13,
        chest=(10, 10, 9), barrel=(9, 9, 15), croup=(10, 10, 8), croup_drop=-2,
        neck=None, neck_rake=0, head=(8, 7, 8), head_pitch=0, head_y=16, muzzle=(5, 4, 3),
        fore=[("", 8, 4, 5, None), ("lower", 3, 3, 4, None), ("paw", 2, 5, 6, None)],
        hind=[("", 8, 5, 7, (12, 0, 0)), ("lower", 3, 3, 4, (-16, 0, 0)), ("paw", 2, 5, 6, None)],
        fore_x=3.5, hind_x=3.5)

    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((2, a.head_y + 7, a.head_back - 3), (2, 3, 2), sign), (2, 3, 2),
        key=f"ear_{side}"))

    # A cougar's tail: long and heavy, drooping and then curling up at the tip.
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 8, a.back - 1), direction=1,
                      segments=[(9, 3, 3), (9, 3, 3), (8, 3, 3)],
                      rotations=[(-38, 0, 0), (26, 0, 0), (34, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    skin.skin(["chest", "barrel", "croup"], COAT, bottom=BELLY, dither=0.05,
              dither_colour=COAT_DARK)
    skin.skin(["head", "muzzle"] + legs + rig.keys("tail"), COAT, dither=0.04,
              dither_colour=COAT_DARK)
    for key in ("chest", "barrel", "croup"):
        skin.rect(skin.face(key, "top"), SADDLE)
    skin.tip(a.tail[-1], SADDLE, rows=4)
    skin.skin(rig.keys("ear_"), SADDLE, dither=0.0)
    for key in [k for k in legs if "paw" in k]:
        skin.skin(key, COAT_DARK, dither=0.0)

    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 3):
        skin.d.rectangle([ex, y + 2, ex + 1, y + 2], fill=EYE)
        skin.glow_rect((ex, y + 2, 2, 1))   # the hypnotic stare is the legend
        skin.d.point((ex + (1 if ex == x + 1 else 0), y + 2), fill=PUPIL)
    x, y, w, h = skin.face("muzzle", "north")
    skin.rect((x, y + h - 2, w, 2), BELLY)
    skin.mark("muzzle", "north", 1, 0, NOSE, w=3)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=4.0, walk_len=0.8, stride=26.0, bob_amp=0.3)
    walk.update(knee_tracks(a.rig, legs, 0.8, 16.0))
    lunge = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (10, 0, 0)), (0.45, (-20, 0, 0)),
                                (0.9, (0, 0, 0)))},
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (36, 0, 0)), (0.45, (-64, 0, 0)),
                                        (0.9, (0, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (36, 0, 0)), (0.45, (-64, 0, 0)),
                                         (0.9, (0, 0, 0)))},
        "hindleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.45, (40, 0, 0)),
                                        (0.9, (0, 0, 0)))},
        "hindleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.45, (40, 0, 0)),
                                         (0.9, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, -1, 1)), (0.45, (0, 2, -3)),
                                (0.9, (0, 0, 0)))},
    }
    # `hiss`: head low, ears flat, and the stare held.
    hiss = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)), (1.2, (12, 0, 0)),
                                (1.5, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (6, 0, 0)), (1.5, (0, 0, 0)))},
        "tail_3": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 30, 0)), (0.7, (0, -30, 0)),
                                  (1.1, (0, 30, 0)), (1.5, (0, 0, 0)))},
    }
    return {"idle": clip(4.0, idle), "walk": clip(0.8, walk),
            "lunge": clip(0.9, lunge, loop=False), "hiss": clip(1.5, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "wampus_cat_model.py", build, paint, anims, HITBOX))
