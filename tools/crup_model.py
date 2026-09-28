#!/usr/bin/env python3
"""The Crup: rig, skin and animation.

A wizarding terrier — canon is "a Jack Russell with a forked tail", which is the whole brief.
The fork is the only thing separating it from a dog anyone could draw, so it is two cubes on
two bones splayed either side of the dock rather than a notch painted on one slab: it has to
survive being seen from the side, where a painted fork is invisible.

Blocky muzzle, dropped ears, short legs, broad chest. PACK trait, so it is usually met in
threes and the silhouette has to read at distance.

Run from the repo root:  python tools/crup_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "crup"
HITBOX = (0.65, 0.75)
TEX_W = 64

COAT = hx("#EFE8DC")
COAT_SHADE = hx("#CFC5B4")
PATCH = hx("#9A6A3C")
PATCH_DARK = hx("#6E4A28")
PAW = hx("#D9CFBD")
NOSE = hx("#2C2320")
EYE = hx("#3A2A1E")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 8, 1))
    rig.cube("body", (-3, 5, -3), (6, 5, 7), key="barrel")
    rig.cube("body", (-3.5, 4, -6), (7, 6, 4), key="chest")

    rig.bone("head", "body", (0, 9, -6), (-4, 0, 0))
    rig.cube("head", (-3, 7, -10), (6, 5, 4))
    # A terrier's muzzle is a distinct square block off the front of the skull, not a taper.
    rig.cube("head", (-2, 7, -13), (4, 3, 3), key="muzzle")

    # Dropped ears. They hang from the top corners of the skull, so the bone is above the cube
    # and a *negative* X rotation swings the hanging flap forward — the sign that makes a
    # terrier's ear fold over instead of standing up like a horn.
    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 3, 11, -9), (-18, 0, sign * -26))
        rig.cube(f"ear_{side}", Rig.mirror((2, 7, -10), (2, 4, 2), sign), (2, 4, 2))

    rig.pair(build_ear)

    # The fork. One dock, then two tines on their own bones splayed outward and up, so the
    # split is geometry and reads from every angle including straight on.
    rig.bone("tail", "body", (0, 9, 4), (34, 0, 0))
    rig.cube("tail", (-1, 8, 4), (2, 2, 3))

    def build_tine(side, sign):
        rig.bone(f"tail_{side}", "tail", (sign * 0.5, 9, 7), (8, sign * 34, sign * 14))
        rig.cube(f"tail_{side}", Rig.mirror((0, 8, 7), (1, 2, 5), sign), (1, 2, 5))

    rig.pair(build_tine)

    def build_foreleg(side, sign):
        limb(rig, f"foreleg_{side}", "body", x=2.2, z=-4, sign=sign, joints=[
            ("", 6, 4, 2, 2, None),
            ("paw", 2, 2, 2, 3, None),
        ])

    def build_hindleg(side, sign):
        limb(rig, f"hindleg_{side}", "body", x=2.2, z=3, sign=sign, joints=[
            ("", 7, 4, 3, 4, (14, 0, 0)),
            ("shank", 4, 2, 2, 2, (-18, 0, 0)),
            ("paw", 2, 2, 2, 3, (4, 0, 0)),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")

    skin.skin(["barrel", "chest", "head", "muzzle"], COAT, dither=0.05,
              dither_colour=COAT_SHADE)
    skin.skin(legs, COAT, dither=0.04, dither_colour=COAT_SHADE)
    skin.skin([k for k in legs if "paw" in k], PAW, dither=0.0)
    skin.skin(["tail", "tail_left", "tail_right"], COAT, dither=0.04,
              dither_colour=COAT_SHADE)

    # Tan saddle and a tan mask over one side of the face — the asymmetry is deliberate, it
    # is what makes a white dog read as a specific white dog rather than a blank.
    skin.rect(skin.face("barrel", "top"), PATCH)
    skin.rect(skin.face("barrel", "east"), PATCH)
    skin.bands("barrel", PATCH_DARK, faces=("top",), step=4)
    skin.rect(skin.face("head", "east"), PATCH)
    skin.rect(skin.face("ear_left", "east"), PATCH)
    skin.skin(["ear_left", "ear_right"], PATCH, dither=0.06, dither_colour=PATCH_DARK)
    skin.tip(["tail_left", "tail_right"], PATCH, rows=2)

    skin.eyes("head", EYE, row=1, inset=1)
    skin.mark("muzzle", "north", 1, 0, NOSE, w=2)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}

    idle = {
        "body": bob(3.0, 0.1, cycles=2),
        "head": {"rotation": rigkit.kf((0.0, (0, 14, 0)), (1.0, (6, -16, 0)),
                                       (2.0, (-4, 8, 0)), (3.0, (0, 14, 0)))},
        "ear_left": osc(3.0, 8.0, phase=0.0),
        "ear_right": osc(3.0, 8.0, phase=0.3),
        # The fork wags as one, and the tines trail it — a dog wags from the dock.
        "tail": osc(3.0, 16.0, axis="y", phase=0.0),
        "tail_left": osc(3.0, 10.0, axis="y", phase=0.15),
        "tail_right": osc(3.0, 10.0, axis="y", phase=0.15),
    }

    walk = {
        **gait(0.6, legs, amp=34.0),
        **{f"{name}_shank": osc(0.6, 20.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        "body": osc(0.6, 3.0, axis="y"),
        "root": bob(0.6, 0.3, cycles=2),
        "tail": osc(0.6, 22.0, axis="y"),
        "ear_left": osc(0.6, 9.0, phase=0.25),
        "ear_right": osc(0.6, 9.0, phase=0.25),
    }

    bite = {
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (-24, 0, 0)),
                                       (0.35, (26, 0, 0)), (0.6, (0, 0, 0)))},
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-14, 0, 0)), (0.6, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.2, (0, 0.6, -1.2)),
                                       (0.6, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-34, 0, 0)),
                                               (0.6, (0, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-34, 0, 0)),
                                                (0.6, (0, 0, 0)))},
    }

    call = {
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-30, 0, 0)),
                                       (0.5, (-18, 0, 0)), (0.8, (0, 0, 0)))},
        "ear_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.8, (0, 0, 0)))},
        "ear_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.8, (0, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (0, 26, 0)),
                                       (0.5, (0, -26, 0)), (0.8, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.2, (0, 0.8, 0)), (0.8, (0, 0, 0)))},
    }

    return {
        "idle": clip(3.0, idle),
        "walk": clip(0.6, walk),
        "bite": clip(0.6, bite, loop=False),
        "call": clip(0.8, call, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID, tool="crup_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
