#!/usr/bin/env python3
"""The Hidebehind: rig, skin and animation.

Canon: a nocturnal North American forest creature that always hides behind something — no one has
seen one clearly; it preys on humans in the woods. `HidebehindEntity` is a bespoke class (no
creature definition), registered at 0.9 x 1.9, and binds only `idle` and `walk`.

Unseen creatures get drawn as what you would glimpse: something tall and wrong at the edge of the
torchlight. Emaciated and stooped, grey-silver bark-striped skin so it vanishes against a trunk,
arms nearly to the ground with long fingers, a long narrow snout, and two pale eyes — the only
part of it that shows in the dark, which is why they are in the glowmask.

Run from the repo root:  python tools/hidebehind_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "hidebehind"
HITBOX = (0.9, 1.9)

SKIN_ = hx("#8E9298")
STRIPE = hx("#5A5E64")
PALE = hx("#B4B8BC")
EYE = hx("#E8F0D8")
CLAW = hx("#2A2C30")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=14, pelvis=(5, 3, 3), torso=(6, 11, 4), head=(5, 6, 5), hunch=24, neck=2,
              head_z=-1,
              legs=[("", 8, 2, 2, None), ("shin", 4, 2, 2, None), ("foot", 2, 2, 4, None)],
              arms=[("", 9, 2, 2, (0, 0, -4)), ("fore", 9, 2, 2, (-10, 0, 0)),
                    ("hand", 3, 3, 2, None)],
              arm_x=4, leg_x=1.5)
    rig.cube("head", (-1.5, a.head_y, a.head_front - 4), (3, 2, 4), key="snout")
    for side, sign in (("left", 1), ("right", -1)):
        rig.cube(f"arm_{side}_hand", Rig.mirror((3, a.shoulder - 25, -1.5), (3, 3, 1), sign), (3, 3, 1),
                 key=f"fingers_{side}")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(rig.keys(""), SKIN_, top=PALE, dither=0.08, dither_colour=STRIPE)
    # Bark stripes running the length of the body, so it is a trunk when it stands still.
    for key in ("torso", "pelvis") + tuple(rig.keys("arm_", "leg_")):
        for face in ("east", "west", "north", "south"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=STRIPE)
    skin.skin(rig.keys("fingers_"), CLAW, dither=0.0)
    skin.eyes("head", EYE, row=2, inset=1, glow=True)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=5.0, walk_len=1.3, stride=22.0, bob_amp=0.3)
    # It watches: long stillness, then a slow turn of the head.
    idle["head"] = {"rotation": kf((0.0, (0, 0, 0)), (3.0, (0, 0, 0)), (3.8, (0, 34, 6)),
                                   (4.4, (0, 34, 6)), (5.0, (0, 0, 0)))}
    return {"idle": clip(5.0, idle), "walk": clip(1.3, walk)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "hidebehind_model.py", build, paint, anims, HITBOX))
