#!/usr/bin/env python3
"""The Hodag: rig, skin and animation.

Canon: a horned, frog-like creature of North America with glowing eyes, which feeds on
Mooncalves — the MACUSA Muggle-worthy excuse for the Wisconsin folk legend. `leap`, `enrage`,
`life_leech` here.

The legend's shape, filtered through "frog-like": a wide flat head with a grinning mouth, bulging
eyes on top of the skull, a bull's horns off the back of it, a crest of blunt spikes down the
spine, and a heavy spiked tail. Squat, broad and low — a toad built like a bull.

Run from the repo root:  python tools/hodag_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, pair_horn, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "hodag"
HITBOX = (0.95, 1.25)

HIDE = hx("#4F5A42")
HIDE_DARK = hx("#353D2C")
BELLY = hx("#9C9A68")
HORN = hx("#D8CBAE")
SPIKE = hx("#2A2E22")
EYE = hx("#E8D23C")
MOUTH = hx("#E8E0CC")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=6,
        chest=(9, 8, 5), barrel=(9, 8, 8), croup=(8, 7, 5), croup_drop=-4,
        neck=None, neck_rake=0, head=(10, 5, 7), head_y=8, muzzle=(8, 3, 2),
        fore=[("", 3, 3, 3, (0, 0, -18)), ("foot", 3, 4, 4, (0, 0, 18))],
        hind=[("", 3, 4, 4, (10, 0, -14)), ("foot", 3, 4, 4, (-10, 0, 14))],
        fore_x=4, hind_x=4)
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((2.5, a.head_y + 5, a.head_front + 1), (3, 2, 3), sign), (3, 2, 3),
        key=f"eye_{side}"))
    pair_horn(rig, "head", name="horn", base=(4, a.head_y + 4, a.head_back - 2),
              size=(2, 3, 2), tip=(1, 3, 1), rotation=(-20, 0, -55))
    for i in range(4):
        rig.cube("body", (-1, a.top - 1, a.front + 2 + 5 * i), (2, 3 - (i == 3), 3),
                 key=f"spike_{i}")
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 4, a.back - 1), direction=1,
                      segments=[(6, 4, 3), (5, 3, 2)], rotations=[(-14, 0, 0), (-8, 0, 0)])
    rig.cube(a.tail[-1], (-0.5, a.belly + 5, a.back + 8), (1, 3, 2), key="tail_spike")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), HIDE, bottom=BELLY, dither=0.16, dither_colour=HIDE_DARK)
    skin.skin(rig.keys("spike_", "tail_spike"), SPIKE, dither=0.0)
    skin.skin(rig.keys("horn_"), HORN, dither=0.0)
    skin.tip([k for k in rig.keys("horn_") if "tip" in k], SPIKE, rows=1)
    skin.skin(rig.keys("eye_"), HIDE, dither=0.0)
    for key in rig.keys("eye_"):
        skin.rect(skin.face(key, "north"), EYE)
        skin.glow(key, faces=("north",))
        skin.mark(key, "north", 1, 0, SPIKE, h=2)
    # The grin: a row of teeth all the way across the wide muzzle.
    x, y, w, h = skin.face("muzzle", "north")
    skin.hline((x, y, w, h), h - 1, SPIKE)
    for col in range(0, w, 2):
        skin.d.point((x + col, y + h - 2), fill=MOUTH)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.2, walk_len=0.7, stride=24.0, bob_amp=0.4)
    walk["body"] = {"rotation": kf((0.0, (0, 6, 3)), (0.35, (0, -6, -3)), (0.7, (0, 6, 3)))}
    bite = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-24, 0, 0)), (0.35, (16, 0, 0)),
                                (0.6, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.15, (0, 2, -1)), (0.35, (0, 0, -2)),
                                (0.6, (0, 0, 0)))},
    }
    groan = {
        "body": {"scale": kf((0.0, (1, 1, 1)), (0.4, (1.08, 1.1, 1.0)), (1.0, (1, 1, 1)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-12, 0, 0)), (1.0, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 24, 0)), (0.6, (0, -24, 0)),
                                  (1.0, (0, 0, 0)))},
    }
    return {"idle": clip(3.2, idle), "walk": clip(0.7, walk),
            "bite": clip(0.6, bite, loop=False), "groan": clip(1.0, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "hodag_model.py", build, paint, anims, HITBOX))
