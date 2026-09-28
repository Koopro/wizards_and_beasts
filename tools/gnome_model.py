#!/usr/bin/env python3
"""The Gnome: rig, skin and animation.

Canon (the Weasleys' garden): small and leathery, with a large, knobbly, bald head exactly like a
potato, and hard little feet. De-gnoming is swinging them round by the ankles and throwing them
over the hedge. FEARFUL, `blink_away`.

The potato head is the whole character, so it is nearly as wide as the body is tall and painted
with knobs and eyes like the ones on a potato; the body under it is a small pot-bellied stump on
stubby legs.

Run from the repo root:  python tools/gnome_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "gnome"
HITBOX = (0.65, 0.75)

POTATO = hx("#A38460")
POTATO_DARK = hx("#7A5E40")
KNOB = hx("#8A6C4A")
EYE = hx("#1A140E")
FOOT = hx("#5E4630")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=4, pelvis=(6, 3, 4), torso=(7, 5, 5), head=(8, 7, 7),
              legs=[("", 2, 2, 2, None), ("foot", 2, 3, 3, None)],
              arms=[("", 3, 2, 2, (0, 0, -12)), ("fore", 2, 2, 2, (-10, 0, 0)),
                    ("hand", 1, 2, 2, None)],
              arm_x=4.5, leg_x=1.5)
    rig.cube("head", (-1, a.head_y + 2, a.head_front - 2), (2, 2, 2), key="nose")
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((4, a.head_y + 3, a.head_front + 3), (1, 3, 2), sign), (1, 3, 2),
        key=f"ear_{side}"))
    rig.cube("head", (-3, a.head_top, a.head_front + 2), (4, 1, 3), key="knob_top")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), POTATO_DARK, dither=0.12, dither_colour=FOOT)
    skin.skin(["head", "nose", "knob_top"] + rig.keys("ear_"), POTATO, dither=0.12,
              dither_colour=POTATO_DARK)
    # Potato eyes: small dark pits scattered over the skull, not only on the face.
    for face in ("top", "east", "west", "south"):
        x, y, w, h = skin.face("head", face)
        for i in range(4):
            skin.d.point((x + (i * 5 + 2) % w, y + (i * 3 + 1) % h), fill=KNOB)
    skin.eyes("head", EYE, row=3, inset=2)
    skin.mark("head", "north", 2, -2, POTATO_DARK, w=4)
    skin.skin([k for k in rig.keys("leg_") if "foot" in k], FOOT, dither=0.0)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.0, walk_len=0.45, stride=40.0, bob_amp=0.5)
    # Waddle: the whole gnome rocks side to side on its stubby legs.
    walk["body"] = {"rotation": kf((0.0, (0, 0, 8)), (0.225, (0, 0, -8)), (0.45, (0, 0, 8)))}
    flinch = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (14, 0, 0)), (0.4, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-130, 0, 0)), (0.4, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-130, 0, 0)), (0.4, (0, 0, 0)))},
    }
    # `call`: an indignant little shout, fist shaken.
    call = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-150, 0, 0)), (0.3, (-130, 0, 0)),
                                     (0.4, (-150, 0, 0)), (0.5, (-130, 0, 0)), (0.8, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.8, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.45, walk),
            "flinch": clip(0.4, flinch, loop=False), "call": clip(0.8, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "gnome_model.py", build, paint, anims, HITBOX))
