#!/usr/bin/env python3
"""The Ramora: rig, skin and animation.

Canon: a silver fish of the Indian Ocean with powerful magical abilities — it can anchor ships and
is the guardian of seafarers; the International Confederation of Wizards protects it from
poaching. PASSIVE, `anchor`.

A clean, heavy, silver fish with a blue sheen along the back — no spines, no teeth, nothing that
threatens, because its whole canon is protection — and a broad sucker-disc under the jaw, the
folklore remora's anchor.

Run from the repo root:  python tools/ramora_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from fish import fish, fish_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "ramora"
HITBOX = (0.95, 1.25)

SILVER = hx("#C4CCD4")
SILVER_DARK = hx("#8C96A2")
SHEEN = hx("#6A8AB8")
BELLY = hx("#EAEEF2")
DISC = hx("#A8A0B0")
EYE = hx("#141820")


def build():
    rig = Rig(CID, 128)
    a = fish(rig, y=8, head=(7, 8, 6), segments=[(6, 8, 9), (6, 7, 8), (5, 5, 6), (4, 3, 4)],
             tail_fin=(1, 12, 6), dorsal=(1, 5, 8), pectoral=(4, 1, 3))
    rig.cube("head", (-2.5, 3.5, -5), (5, 1, 4), key="disc")
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["head"] + list(a.segments)
    skin.skin(body, SILVER, top=SHEEN, bottom=BELLY, dither=0.1, dither_colour=SILVER_DARK)
    for key in body:
        for face in ("east", "west"):
            skin.hline(skin.face(key, face), 1, SHEEN)
    skin.skin(["tail_fin", "dorsal"] + rig.keys("fin_"), SILVER_DARK, dither=0.0)
    skin.bands(["tail_fin", "dorsal"], SILVER, faces=("east", "west"), step=2)
    skin.skin("disc", DISC, dither=0.0)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 2, EYE, w=2, h=2)
    return skin


def anims(a):
    idle, swim = fish_loops(a, idle_len=3.4, swim_len=0.9)
    flinch = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (0, 30, 0)), (0.4, (0, 0, 0)))},
              "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (2, 0, 1)), (0.4, (0, 0, 0)))}}
    return {"idle": clip(3.4, idle), "swim": clip(0.9, swim), "flinch": clip(0.4, flinch, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "ramora_model.py", build, paint, anims, HITBOX))
