#!/usr/bin/env python3
"""The Shrake: rig, skin and animation.

Canon: a fish covered in spines, found in the Atlantic, first created as revenge by a group of
wizards on Muggle fishermen who had insulted them; any nets set near a shrake come up in shreds.
`thorns`, AMPHIBIOUS.

The same fish body as the Ramora, then everything the Ramora is not: murky green-grey instead of
silver, and spines — rows of them down the back and flanks, as geometry, because a spiny fish whose
spines are painted on is a smooth fish.

Run from the repo root:  python tools/shrake_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from fish import fish, fish_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "shrake"
HITBOX = (0.95, 1.25)

HIDE = hx("#6A7A62")
HIDE_DARK = hx("#46543E")
BELLY = hx("#A8B08E")
SPINE = hx("#E0D8C0")
EYE = hx("#E8A030")


def build():
    rig = Rig(CID, 128)
    segs = [(6, 8, 9), (6, 7, 8), (5, 5, 6), (4, 3, 4)]
    a = fish(rig, y=8, head=(7, 8, 6), segments=segs, tail_fin=(1, 11, 5), dorsal=(1, 3, 6),
             pectoral=(4, 1, 3))
    z = 0
    for i, (length, w, h) in enumerate(segs[:3]):
        for j in range(2):
            rig.cube(a.segments[i], (-0.5, 8 + h / 2.0, z + 1 + 3 * j), (1, 3, 1), key=f"spine_top_{i}{j}",
                     rotation=(-30, 0, 0), pivot=(0, 8 + h / 2.0, z + 1.5 + 3 * j))
            for side, sign in (("l", 1), ("r", -1)):
                rig.cube(a.segments[i], Rig.mirror((w / 2.0, 8, z + 1 + 3 * j), (2, 1, 1), sign), (2, 1, 1),
                         key=f"spine_{side}{i}{j}")
        z += length
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["head"] + list(a.segments)
    skin.skin(body, HIDE, bottom=BELLY, dither=0.2, dither_colour=HIDE_DARK)
    skin.skin(["tail_fin", "dorsal"] + rig.keys("fin_"), HIDE_DARK, dither=0.0)
    skin.skin(rig.keys("spine_"), SPINE, dither=0.0)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 2, EYE)
    return skin


def anims(a):
    idle, swim = fish_loops(a, idle_len=3.0, swim_len=0.8)
    attack = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (0, -30, 0)), (0.35, (0, 30, 0)), (0.6, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.25, (0, 0, -2)), (0.6, (0, 0, 0)))}}
    flinch = {"root": {"scale": kf((0.0, (1, 1, 1)), (0.08, (1.15, 1.15, 1.0)), (0.4, (1, 1, 1)))}}
    return {"idle": clip(3.0, idle), "swim": clip(0.8, swim),
            "attack": clip(0.6, attack, loop=False), "flinch": clip(0.4, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "shrake_model.py", build, paint, anims, HITBOX))
