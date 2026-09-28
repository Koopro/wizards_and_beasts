#!/usr/bin/env python3
"""The Nogtail: rig, skin and animation.

Canon: a demon found in rural Europe, Russia and America, resembling a stunted piglet with long
legs, a thick stubby tail and narrow black eyes. It creeps into a sty and suckles alongside the
piglets; the longer it stays, the worse the farm's blight. Only a pure-white dog can drive one
off.

The wrongness is in the proportions: a piglet's body on legs a foal would have, so the animal
stands too tall for what it is, with the head hung low and the eyes slits. Mottled grey-pink, not
the clean pink of a real piglet.

Run from the repo root:  python tools/nogtail_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "nogtail"
HITBOX = (0.65, 0.75)

HIDE = hx("#B48E88")
BLIGHT = hx("#7C6A66")
DARK = hx("#5A4644")
SNOUT = hx("#C99A94")
EYE = hx("#0E0A0A")
HOOF = hx("#3A2E2C")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=7,
        chest=(6, 5, 4), barrel=(6, 5, 6), croup=(6, 5, 3), croup_drop=-2,
        neck=None, neck_rake=0, head=(5, 5, 4), head_y=6, head_pitch=18, muzzle=(3, 3, 2),
        fore=[("", 4, 2, 2, None), ("lower", 2, 1, 1, None), ("hoof", 1, 2, 2, None)],
        hind=[("", 4, 2, 2, (10, 0, 0)), ("lower", 2, 1, 1, (-10, 0, 0)), ("hoof", 1, 2, 2, None)],
        fore_x=2, hind_x=2)
    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 2, a.head_y + 5, a.head_back - 2), (70, 0, sign * -35))
        rig.cube(f"ear_{side}", Rig.mirror((1, a.head_y + 5, a.head_back - 2.5), (2, 3, 1), sign),
                 (2, 3, 1))
    rig.pair(build_ear)
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 4, a.back - 1), direction=1,
                      segments=[(3, 2, 2)], rotations=[(40, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), HIDE, dither=0.22, dither_colour=BLIGHT)
    skin.skin([k for k in rig.keys("foreleg", "hindleg") if "hoof" in k], HOOF, dither=0.0)
    skin.skin(rig.keys("ear_"), BLIGHT, dither=0.0)
    x, y, w, h = skin.face("muzzle", "north")
    skin.rect((x, y, w, h), SNOUT)
    skin.mark("muzzle", "north", 0, 1, DARK)
    skin.mark("muzzle", "north", -1, 1, DARK)
    # Narrow eyes: one texel tall, two wide, slanted by a texel.
    skin.mark("head", "north", 0, 1, EYE, w=2)
    skin.mark("head", "north", -2, 1, EYE, w=2)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=2.6, walk_len=0.55, stride=30.0, bob_amp=0.3)
    walk.update(knee_tracks(a.rig, legs, 0.55, 18.0))
    bite = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-16, 0, 0)), (0.3, (20, 0, 0)),
                                (0.5, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -1.5)), (0.5, (0, 0, 0)))},
    }
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (10, 10, 0)), (0.4, (6, -10, 0)),
                                (0.6, (10, 8, 0)), (0.9, (0, 0, 0)))},
        "tail": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (0, 30, 0)), (0.4, (0, -30, 0)),
                                (0.6, (0, 30, 0)), (0.9, (0, 0, 0)))},
    }
    return {"idle": clip(2.6, idle), "walk": clip(0.55, walk),
            "bite": clip(0.5, bite, loop=False), "groan": clip(0.9, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "nogtail_model.py", build, paint, anims, HITBOX))
