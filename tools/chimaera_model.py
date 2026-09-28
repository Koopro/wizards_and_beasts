#!/usr/bin/env python3
"""The Chimaera: rig, skin and animation.

Canon: a vicious, bloodthirsty Greek monster with a lion's head, a goat's body and a dragon's
tail. Three animals, and each has to be legible on its own or the creature reads as one of
them: the **lion** is a maned head bigger than a goat would carry, the **goat** is the lean
body on thin hooved legs with a shaggy coat, and the **dragon** is a scaled, tapering tail
ending in a spade.

FIRE_BREATH and CHARGE, so the head is carried forward and low and the `bite` clip lunges.

Run from the repo root:  python tools/chimaera_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "chimaera"
HITBOX = (1.7, 1.9)

GOAT = hx("#8C7C62")
GOAT_DARK = hx("#665843")
LION = hx("#C28C48")
MANE = hx("#8E4A1E")
MANE_LIT = hx("#B8672C")
SCALE = hx("#3E5B3C")
SCALE_LIT = hx("#5C7F52")
HOOF = hx("#2F2923")
EYE = hx("#F0B830")
FANG = hx("#E8E0C8")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=14,
        chest=(10, 11, 8), barrel=(10, 10, 12), croup=(9, 10, 7), croup_drop=-5,
        neck=(6, 7, 6), neck_rake=26, head=(9, 8, 8), head_pitch=4, muzzle=(5, 4, 3),
        fore=[("", 8, 3, 4, None), ("cannon", 4, 2, 2, None), ("hoof", 2, 3, 3, None)],
        hind=[("", 8, 4, 5, (12, 0, 0)), ("cannon", 4, 2, 3, (-16, 0, 0)), ("hoof", 2, 3, 3, None)],
        fore_x=3, hind_x=3)

    # The mane rings the lion's face and runs down the neck: one box around the skull, grown
    # with inflate so it frames the face without costing the face its texels.
    rig.cube("head", (-6.5, a.head_y - 2, a.head_front + 3), (13, 12, 6), key="mane",
             inflate=0.5)
    rig.cube("neck", (-4, a.belly + 9, a.front - 1), (8, 8, 5), key="neck_mane", inflate=0.5)
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((5, a.head_y + 8, a.head_back - 4), (2, 3, 2), sign), (2, 3, 2),
        key=f"ear_{side}"))
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((1, a.head_y - 2, a.muzzle_front + 1), (1, 2, 1), sign), (1, 2, 1),
        key=f"fang_{side}"))

    # Goat's beard under the lion's jaw would be a fourth animal; the goat shows in the coat.
    # The dragon tail: four tapering scaled links and a spade.
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 7, a.back - 1), direction=1,
                      segments=[(7, 4, 4), (6, 3, 3), (6, 2, 2), (5, 2, 2)],
                      rotations=[(-10, 0, 0), (-12, 0, 0), (-10, 0, 0), (-8, 0, 0)])
    last = a["tail"][-1]
    rig.cube(last, (-2.5, a.belly + 6.5, a.back + 23), (5, 1, 4), key="spade")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    skin.skin(["chest", "barrel", "croup"], GOAT, dither=0.18, dither_colour=GOAT_DARK)
    skin.skin(legs, GOAT, dither=0.14, dither_colour=GOAT_DARK)
    skin.skin([k for k in legs if "hoof" in k], HOOF, dither=0.0)
    # Shaggy: long strands down the goat flanks.
    for key in ("chest", "barrel", "croup"):
        for face in ("east", "west"):
            x, y, w, h = skin.face(key, face)
            for col in range(1, w, 3):
                skin.d.line([(x + col, y + h // 2), (x + col, y + h - 1)], fill=GOAT_DARK)

    skin.skin(["neck", "head", "muzzle"], LION, dither=0.05, dither_colour=MANE)
    skin.skin(["mane", "neck_mane", "ear_left", "ear_right"], MANE, dither=0.2,
              dither_colour=MANE_LIT)
    skin.bands(["mane", "neck_mane"], MANE_LIT, faces=("east", "west", "top", "south"), step=3)
    skin.skin(["fang_left", "fang_right"], FANG, dither=0.0)
    skin.eyes("head", EYE, row=2, inset=1, pupil=hx("#2A1A08"), width=2, glow=True)
    skin.mark("muzzle", "north", 1, 0, hx("#4A2A18"), w=3)

    tail = rig.keys("tail") + ["spade"]
    skin.skin(tail, SCALE, dither=0.0)
    skin.bands(tail, SCALE_LIT, step=2)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.8, walk_len=0.9, stride=26.0)
    walk.update(knee_tracks(a.rig, legs, 0.9, 16.0))
    # The dragon tail lashes independently of the gait — it is a different animal.
    for i, name in enumerate(a.tail):
        idle[name] = {"rotation": kf((0.0, (0, 6 + 3 * i, 0)), (1.9, (0, -6 - 3 * i, 0)),
                                     (3.8, (0, 6 + 3 * i, 0)))}
    bite = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.45, (24, 0, 0)),
                                (0.8, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-14, 0, 0)), (0.45, (18, 0, 0)),
                                (0.8, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.45, (0, 0.8, -2.5)), (0.8, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-24, 0, 0)), (0.8, (0, 0, 0)))},
    }
    # `howl` is the roar that comes before the fire: head up, jaw wide, mane shaken out.
    howl = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-26, 0, 0)), (1.2, (-20, 0, 0)),
                                (1.6, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-16, 8, 0)), (0.8, (-16, -8, 0)),
                                (1.2, (-12, 0, 0)), (1.6, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-4, 0, 0)), (1.6, (0, 0, 0)))},
    }
    return {"idle": clip(3.8, idle), "walk": clip(0.9, walk),
            "bite": clip(0.8, bite, loop=False), "howl": clip(1.6, howl, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "chimaera_model.py", build, paint, anims, HITBOX))
