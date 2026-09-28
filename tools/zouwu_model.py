#!/usr/bin/env python3
"""The Zouwu: rig, skin and animation.

Canon (The Crimes of Grindelwald): an elephant-sized Chinese cat that can travel a thousand miles
in a day, striped like a tiger over a pale coat, with a huge round face, a vivid mane, and an
extraordinarily long plumed tail it streams behind it. Tamed with a bell-and-feather toy. 136
health here and `blink_away` — it is enormous and very fast, not cruel.

The tail is half the silhouette and gets half the bones: four links and a plume, carried in a
high arc. The mane and plume share the one colour on the animal — blue running to green and
violet — so the eye reads front and back as one creature.

Run from the repo root:  python tools/zouwu_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "zouwu"
HITBOX = (2.7, 2.9)

COAT = hx("#E9DEC2")
COAT_DARK = hx("#C9BC9C")
STRIPE = hx("#3A3344")
MANE_A = hx("#3C74C8")
MANE_B = hx("#3FAE8A")
MANE_C = hx("#8A4FB8")
NOSE = hx("#C45A6A")
EYE = hx("#E8B432")
PAD = hx("#4A4250")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=18,
        chest=(20, 18, 12), barrel=(18, 16, 18), croup=(18, 16, 10), croup_drop=-3,
        neck=(12, 6, 10), neck_rake=18, head=(18, 15, 14), head_pitch=0, muzzle=(10, 6, 4),
        fore=[("", 10, 8, 8, None), ("lower", 6, 7, 7, None), ("paw", 2, 9, 10, None)],
        hind=[("", 10, 9, 10, (12, 0, 0)), ("lower", 6, 7, 7, (-14, 0, 0)), ("paw", 2, 9, 10, None)],
        fore_x=7, hind_x=7)

    # The mane collars the head from behind rather than standing in front of it, so the face
    # sits inside a ring of colour instead of under a painted board.
    rig.cube("neck", (-11, a.head_y - 2, a.head_back - 4), (22, 18, 8), key="mane", inflate=0.5)
    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((6, a.head_y + 14, a.head_back - 5), (4, 5, 3), sign), (4, 5, 3),
        key=f"ear_{side}"))

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 13, a.back - 1), direction=1,
                      segments=[(10, 5, 5), (9, 4, 4), (8, 4, 4), (7, 3, 3)],
                      rotations=[(40, 0, 0), (-18, 0, 0), (-22, 0, 0), (-24, 0, 0)])
    last = a.tail[-1]
    tail_bone = rig[last]
    px, py, pz = tail_bone.pivot
    rig.cube(last, (-4, py - 6, pz + 4), (8, 12, 12), key="plume", inflate=0.5)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "neck", "head", "muzzle"]
    skin.skin(body, COAT, dither=0.06, dither_colour=COAT_DARK)
    skin.skin(legs + rig.keys("tail"), COAT, dither=0.05, dither_colour=COAT_DARK)
    skin.skin([k for k in legs if "paw" in k], PAD, dither=0.0)

    # Tiger stripes: vertical bars down the flanks, rings on the legs and tail.
    for key in ("chest", "barrel", "croup"):
        for face in ("east", "west", "top"):
            x, y, w, h = skin.face(key, face)
            for col in range(2, w, 5):
                length = h - 2 - (col * 7) % 5
                skin.d.line([(x + col, y), (x + col, y + max(2, length))], fill=STRIPE)
    skin.bands([k for k in legs if "paw" not in k] + rig.keys("tail"), STRIPE, step=4, offset=1)
    x, y, w, h = skin.face("head", "top")
    for col in range(2, w, 4):
        skin.d.line([(x + col, y), (x + col, y + h // 2)], fill=STRIPE)

    # Mane and plume: the ramp blue → green → violet, streaked.
    for key in ("mane", "plume"):
        skin.skin(key, MANE_A, dither=0.0, bevel=0)
        skin.ramp(key, MANE_A, MANE_C, faces=("east", "west", "north", "south"))
        skin.dither(skin.face(key, "top"), MANE_B, 0.4, 5)
        for face in ("east", "west", "north", "south"):
            fx, fy, fw, fh = skin.face(key, face)
            for col in range(0, fw, 3):
                skin.d.line([(fx + col, fy), (fx + col, fy + fh - 1)], fill=MANE_B)
    skin.skin(rig.keys("ear_"), MANE_C, dither=0.0)

    skin.eyes("head", EYE, row=4, inset=3, pupil=STRIPE, width=3, glow=True)
    skin.mark("muzzle", "north", 3, 0, NOSE, w=4, h=2)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=4.8, walk_len=1.2, stride=22.0, bob_amp=0.9)
    walk.update(knee_tracks(a.rig, legs, 1.2, 12.0))
    lunge = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.6, (-18, 0, 0)),
                                (1.1, (0, 0, 0)))},
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (30, 0, 0)), (0.6, (-60, 0, 0)),
                                        (1.1, (0, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (30, 0, 0)), (0.6, (-60, 0, 0)),
                                         (1.1, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, -1, 2)), (0.6, (0, 3, -5)),
                                (1.1, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-20, 0, 0)), (1.1, (0, 0, 0)))},
    }
    # `call`: the chirping purr it answers the bell toy with — head cocked, tail curling over.
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-10, 0, 16)), (1.2, (-10, 0, -12)),
                                (1.8, (0, 0, 0)))},
        "tail_2": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-24, 0, 0)), (1.8, (0, 0, 0)))},
        "tail_3": {"rotation": kf((0.0, (0, 0, 0)), (0.7, (-26, 0, 0)), (1.8, (0, 0, 0)))},
    }
    return {"idle": clip(4.8, idle), "walk": clip(1.2, walk),
            "lunge": clip(1.1, lunge, loop=False), "call": clip(1.8, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "zouwu_model.py", build, paint, anims, HITBOX))
