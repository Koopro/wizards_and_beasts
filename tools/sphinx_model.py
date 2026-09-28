#!/usr/bin/env python3
"""The Sphinx: rig, skin and animation.

Canon: Egyptian, a human head on a lion's body, used for centuries to guard valuables and hidden
places; highly intelligent, fond of riddles, violent only when what it guards is threatened.
`sphinx_riddle`, `dread_aura` and `heal_aura` — it is a guardian, not a predator.

The identity is the headdress. A lion with a human face is the Manticore's problem too; what
makes this one a sphinx at a glance is the striped nemes cloth framing the face and falling to
the shoulders, and a body held upright and composed rather than prowling.

Run from the repo root:  python tools/sphinx_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "sphinx"
HITBOX = (1.7, 1.9)

PELT = hx("#C9A063")
PELT_DARK = hx("#9A7644")
FACE = hx("#B4835A")
FACE_DARK = hx("#8A6040")
GOLD = hx("#D9B44A")
LAPIS = hx("#2F4E8C")
EYE = hx("#1E1A26")
KOHL = hx("#18141C")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=12,
        chest=(12, 13, 9), barrel=(11, 11, 12), croup=(11, 11, 7), croup_drop=-3,
        neck=(6, 6, 6), neck_rake=6, head=(8, 9, 8), head_pitch=0,
        fore=[("", 7, 4, 5, None), ("lower", 3, 3, 4, None), ("paw", 2, 5, 6, None)],
        hind=[("", 7, 5, 6, (12, 0, 0)), ("lower", 3, 3, 4, (-16, 0, 0)), ("paw", 2, 5, 6, None)],
        fore_x=4, hind_x=4)

    # The nemes: a back flap behind the skull, two lappets hanging down either side of the face
    # onto the chest, and a band across the brow. On its own bone under the head so it moves
    # with the head but can settle a beat late.
    hy, hf, hb = a.head_y, a.head_front, a.head_back
    rig.bone("nemes", "head", (0, hy + 9, hb - 2))
    rig.cube("nemes", (-6, hy - 2, hb - 3), (12, 12, 3), key="nemes_back")
    rig.cube("nemes", (-5, hy + 8, hf + 1), (10, 2, 7), key="nemes_crown")
    rig.pair(lambda side, sign: rig.cube(
        "nemes", Rig.mirror((4, hy - 6, hf + 2), (2, 12, 3), sign), (2, 12, 3),
        key=f"lappet_{side}"))
    rig.cube("head", (-1, hy - 3, hf + 2), (2, 3, 2), key="beard")

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 8, a.back - 1), direction=1,
                      segments=[(7, 2, 2), (6, 2, 2), (3, 3, 3)],
                      rotations=[(-30, 0, 0), (-20, 0, 0), (-6, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    skin.skin(["chest", "barrel", "croup", "neck"], PELT, dither=0.06, dither_colour=PELT_DARK)
    skin.skin(legs, PELT, dither=0.05, dither_colour=PELT_DARK)
    skin.skin(rig.keys("tail"), PELT, dither=0.0)
    skin.tip(a.tail[-1], PELT_DARK, rows=3)

    skin.skin("head", FACE, dither=0.03, dither_colour=FACE_DARK)
    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 3):
        skin.d.rectangle([ex, y + 3, ex + 1, y + 3], fill=EYE)
        skin.d.point((ex - 1 if ex == x + 1 else ex + 2, y + 3), fill=KOHL)
    skin.mark("head", "north", 3, 5, FACE_DARK, w=2)
    skin.mark("head", "north", 3, 7, FACE_DARK, w=2)

    # Gold and lapis stripes: the one pattern everybody recognises.
    nemes = rig.keys("nemes_", "lappet_")
    skin.skin(nemes, GOLD, dither=0.0, bevel=0)
    skin.bands(nemes, LAPIS, faces=("east", "west", "north", "south", "top"), step=2)
    skin.skin("beard", GOLD, dither=0.0)
    skin.bands("beard", LAPIS, step=2, offset=1)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=5.0, walk_len=1.0, stride=20.0)
    walk.update(knee_tracks(a.rig, legs, 1.0, 12.0))
    idle["nemes"] = {"rotation": kf((0.0, (0, 0, 0)), (2.5, (3, 0, 0)), (5.0, (0, 0, 0)))}
    strike = {
        "foreleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-70, 0, 12)),
                                         (0.5, (20, 0, 0)), (0.9, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-8, 0, 0)), (0.9, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-8, -10, 0)), (0.9, (0, 0, 0)))},
    }
    # `call` is the riddle: a slow tilt of the head, held, and a knowing return.
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-6, 12, 14)), (1.8, (-6, 12, 14)),
                                (2.4, (0, 0, 0)))},
        "nemes": {"rotation": kf((0.0, (0, 0, 0)), (0.8, (0, 0, -6)), (2.4, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (0, 20, 0)), (1.2, (0, -20, 0)),
                                  (1.8, (0, 20, 0)), (2.4, (0, 0, 0)))},
    }
    return {"idle": clip(5.0, idle), "walk": clip(1.0, walk),
            "strike": clip(0.9, strike, loop=False), "call": clip(2.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "sphinx_model.py", build, paint, anims, HITBOX))
