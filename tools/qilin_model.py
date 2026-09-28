#!/usr/bin/env python3
"""The Qilin: rig, skin and animation.

Canon (Secrets of Dumbledore): a small, graceful, scaled creature — deer-like legs, a dragon-ish
head, a flowing mane — that can see into a person's soul and bows before the pure of heart. It
is passive and fearful here (`heal_aura`, `blink_away`), and a foal-sized animal in a 0.95 x 1.25
box.

Three things say qilin rather than fawn: **scales** instead of fur, painted as overlapping
iridescent rows; a **mane and tail of pale gold** that flow rather than bristle; and small swept
**antlers** where a fawn would have ears.

Run from the repo root:  python tools/qilin_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, pair_horn, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "qilin"
HITBOX = (0.95, 1.25)

SCALE = hx("#6FAE98")
SCALE_DARK = hx("#5E9C86")
SCALE_LIT = hx("#D8C56A")
MANE = hx("#F2E2A6")
MANE_DARK = hx("#CDB46E")
HOOF = hx("#3C3A36")
ANTLER = hx("#E8D69A")
EYE = hx("#1F2A40")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=10,
        chest=(6, 7, 4), barrel=(5, 6, 8), croup=(6, 6, 4), croup_drop=-4,
        neck=(3, 7, 3), neck_rake=22, head=(4, 4, 5), head_pitch=12, muzzle=(3, 2, 3),
        fore=[("", 5, 2, 2, None), ("cannon", 3, 1, 1, None), ("hoof", 2, 2, 2, None)],
        hind=[("", 5, 2, 3, (10, 0, 0)), ("cannon", 3, 1, 1, (-14, 0, 0)), ("hoof", 2, 2, 2, None)],
        fore_x=2, hind_x=2)

    # Mane: a crest down the back of the neck and a forelock, both pale gold.
    rig.cube("neck", (-1, a.belly + 6, a.front + 2.5), (2, 7, 2), key="crest")
    pair_horn(rig, "head", name="antler", base=(1, a.head_y + 4, a.head_back - 2),
              size=(1, 3, 1), tip=(1, 2, 1), rotation=(-48, 0, -22))

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 5, a.back - 1), direction=1,
                      segments=[(3, 2, 2), (5, 3, 2)], rotations=[(-50, 0, 0), (-15, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "neck", "head", "muzzle"]
    skin.skin(body, SCALE, dither=0.0)
    skin.skin(legs, SCALE, dither=0.0)
    skin.skin([k for k in legs if "hoof" in k], HOOF, dither=0.0)

    # Scales: offset rows of a light texel over a dark one, which at this size is exactly what
    # an overlapping scale looks like. The gold glint is sparse on purpose — iridescence, not
    # glitter.
    for key in body + [k for k in legs if "hoof" not in k]:
        for face in ("east", "west", "top", "north", "south"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_DARK)
                    if (col + row + len(key)) % 7 == 0:
                        skin.d.point((x + col, min(y + row + 1, y + h - 1)), fill=SCALE_LIT)

    skin.skin(["crest"] + rig.keys("tail"), MANE, dither=0.12, dither_colour=MANE_DARK)
    skin.skin(rig.keys("antler_"), ANTLER, dither=0.0)
    skin.glow(rig.keys("antler_"))
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.6, walk_len=0.7, stride=26.0, bob_amp=0.3)
    walk.update(knee_tracks(a.rig, legs, 0.7, 16.0))
    # `flinch` is a shy: it has no attack and blinks away rather than fight.
    flinch = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (0, 18, -6)), (0.5, (0, 0, 0)))},
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-20, 0, 0)), (0.5, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.12, (0, 1, 1.5)), (0.5, (0, 0, 0)))},
    }
    # `call` is the bow it gives the worthy: forelegs fold, head low, held.
    call = {
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-60, 0, 0)), (1.8, (-60, 0, 0)),
                                        (2.4, (0, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-60, 0, 0)), (1.8, (-60, 0, 0)),
                                         (2.4, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (18, 0, 0)), (1.8, (18, 0, 0)),
                                (2.4, (0, 0, 0)))},
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (20, 0, 0)), (1.8, (20, 0, 0)),
                                (2.4, (0, 0, 0)))},
    }
    return {"idle": clip(3.6, idle), "walk": clip(0.7, walk),
            "flinch": clip(0.5, flinch, loop=False), "call": clip(2.4, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "qilin_model.py", build, paint, anims, HITBOX))
