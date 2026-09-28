#!/usr/bin/env python3
"""The Kneazle: rig, skin and animation.

A large, intelligent cat with an outsized lion's tuft on the tail, oversized ears and a
flattened, faintly bad-tempered face. The tail and the ears are the whole identity — a cat
body at this size is four boxes and a head, and what stops it reading as the vanilla cat is
that the tail is nearly as long as the animal and ends in a tuft, and that the ears are big
enough to break the head's silhouette.

The Kneazle's kit is `danger_sense` and a `leap`, so on top of the idle and walk its locomotion
class binds it gets a `lunge` — fired on a landed melee hit — and a `hiss`, which rides the
ambient-sound beat. Both names come from the vocabulary `rigkit.TRIGGERABLE` lists; a clip
named anything else has no caller and never plays.

Run from the repo root:  python tools/kneazle_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "kneazle"
HITBOX = (0.65, 0.75)
TEX_W = 64

FUR = hx("#8A6A46")
FUR_DARK = hx("#5E472E")
BELLY = hx("#C7AE8A")
TUFT = hx("#4A3622")
PAW = hx("#6B5236")
EYE = hx("#D8C24A")
NOSE = hx("#3B2A20")
WHISKER = hx("#E6DCC8")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 9, 1))
    rig.cube("body", (-2.5, 6, -3), (5, 5, 7), key="barrel")
    rig.cube("body", (-3, 5, -6), (6, 6, 4), key="chest")

    # Neck and head as one short chain. A cat carries its head level with and slightly in
    # front of the shoulders, which is a small forward offset and no rotation at all.
    rig.bone("head", "body", (0, 9, -6), (-6, 0, 0))
    rig.cube("head", (-2.5, 7, -11), (5, 5, 5))
    rig.cube("head", (-1.5, 7, -13), (3, 3, 2), key="muzzle")

    # Ears on their own bones, splayed outward. Oversized on purpose: this is the one line of
    # the silhouette that says Kneazle rather than cat.
    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, 12, -8), (-10, 0, sign * -18))
        rig.cube(f"ear_{side}", Rig.mirror((0.5, 12, -9), (2, 4, 2), sign), (2, 4, 2))

    rig.pair(build_ear)

    # Tail: three segments so it can curl, ending in the lion tuft. Authored straight back
    # from the rump and lifted by a positive X rotation at each joint.
    rig.bone("tail", "body", (0, 10, 4), (14, 0, 0))
    rig.cube("tail", (-1, 9, 4), (2, 2, 5))
    rig.bone("tail_mid", "tail", (0, 10, 9), (9, 0, 0))
    rig.cube("tail_mid", (-1, 9, 9), (2, 2, 4))
    rig.bone("tail_tuft", "tail_mid", (0, 10, 13), (6, 0, 0))
    rig.cube("tail_tuft", (-1.5, 8, 13), (3, 3, 4))

    legs = {}

    def build_foreleg(side, sign):
        legs[f"foreleg_{side}"] = limb(
            rig, f"foreleg_{side}", "body", x=1.8, z=-4, sign=sign, joints=[
                ("", 7, 5, 2, 2, None),
                ("paw", 2, 2, 2, 3, None),
            ])

    def build_hindleg(side, sign):
        legs[f"hindleg_{side}"] = limb(
            rig, f"hindleg_{side}", "body", x=1.8, z=3, sign=sign, joints=[
                ("", 8, 5, 3, 4, (12, 0, 0)),
                ("shank", 4, 3, 2, 2, (-16, 0, 0)),
                ("paw", 2, 2, 2, 3, (4, 0, 0)),
            ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig, legs


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)

    skin.skin(["barrel", "chest"], FUR, bottom=BELLY, dither=0.05, dither_colour=FUR_DARK)
    skin.skin(["head", "muzzle"], FUR, dither=0.04, dither_colour=FUR_DARK)
    skin.skin(rig.keys("foreleg", "hindleg"), FUR, dither=0.04, dither_colour=FUR_DARK)
    skin.skin([k for k in rig.keys("foreleg", "hindleg") if "paw" in k], PAW, dither=0.05)
    skin.skin(["tail", "tail_mid"], FUR, dither=0.06, dither_colour=FUR_DARK)
    skin.skin("tail_tuft", TUFT, dither=0.08, dither_colour=FUR_DARK)

    # Tabby barring. Across the flanks and rings down the tail, which is what a barred coat
    # actually does — the rings are the part people recognise.
    skin.bands(["barrel", "chest"], FUR_DARK, faces=("east", "west"), step=4)
    skin.bands(["tail", "tail_mid"], TUFT, step=2)
    skin.bands(["ear_left", "ear_right"], FUR_DARK, step=2)
    skin.skin(["ear_left", "ear_right"], FUR, dither=0.0)
    skin.tip(["ear_left", "ear_right"], TUFT, rows=1)

    # A cat's face: forward-set eyes, because it is a predator, and that is the one place a
    # forward pair beats the prey-animal side pair.
    skin.eyes("head", EYE, row=1, inset=1, pupil=NOSE, glow=True)
    skin.mark("muzzle", "north", 1, 0, NOSE)
    for dx in (0, -1):
        skin.mark("muzzle", "north", dx, 1, WHISKER)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}

    idle = {
        "body": bob(3.6, 0.12, cycles=2),
        "head": {"rotation": rigkit.kf((0.0, (0, 12, 0)), (1.2, (-4, -14, 0)),
                                       (2.4, (3, 6, 0)), (3.6, (0, 12, 0)))},
        "ear_left": osc(3.6, 10.0, phase=0.0),
        "ear_right": osc(3.6, 10.0, phase=0.35),
        # The tail never stops. A still cat with a still tail reads as taxidermy.
        "tail": osc(3.6, 7.0, axis="z", phase=0.0),
        "tail_mid": osc(3.6, 11.0, axis="z", phase=0.2),
        "tail_tuft": osc(3.6, 14.0, axis="z", phase=0.4),
    }

    walk = {
        **gait(0.7, legs, amp=30.0),
        **{f"{name}_shank": osc(0.7, 18.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        "body": osc(0.7, 3.0, axis="y"),
        "root": bob(0.7, 0.35, cycles=2),
        "tail": osc(0.7, 6.0, axis="z"),
        "tail_mid": osc(0.7, 9.0, axis="z", phase=0.25),
        "head": osc(0.7, 2.0, axis="y", phase=0.5),
    }

    # `lunge` is the leap ability made visible: gather onto the hindquarters, then extend. The
    # name is not decorative — `GenericBeastEntity.CLIPS_ATTACK` is what fires it on a melee
    # hit, and a clip called `pounce` would simply never play.
    lunge = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (16, 0, 0)),
                                       (0.45, (-22, 0, 0)), (0.9, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.2, (0, -1, 1.5)),
                                       (0.5, (0, 2, -2)), (0.9, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (44, 0, 0)),
                                               (0.5, (-56, 0, 0)), (0.9, (0, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (44, 0, 0)),
                                                (0.5, (-56, 0, 0)), (0.9, (0, 0, 0)))},
        "hindleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-40, 0, 0)),
                                               (0.5, (38, 0, 0)), (0.9, (0, 0, 0)))},
        "hindleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-40, 0, 0)),
                                                (0.5, (38, 0, 0)), (0.9, (0, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-28, 0, 0)), (0.9, (0, 0, 0)))},
    }

    # `hiss`: ears flat back, head low, spine bowed. The Kneazle's danger sense firing is the
    # most useful thing it does, and this is what the player sees when it does.
    hiss = {
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (18, 0, 0)),
                                       (0.9, (14, 0, 0)), (1.2, (0, 0, 0)))},
        "ear_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-46, 0, -22)),
                                           (0.9, (-46, 0, -22)), (1.2, (0, 0, 0)))},
        "ear_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-46, 0, 22)),
                                            (0.9, (-46, 0, 22)), (1.2, (0, 0, 0)))},
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-10, 0, 0)),
                                       (0.9, (-8, 0, 0)), (1.2, (0, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (26, 0, 0)),
                                       (0.9, (22, 0, 0)), (1.2, (0, 0, 0)))},
    }

    return {
        "idle": clip(3.6, idle),
        "walk": clip(0.7, walk),
        "lunge": clip(0.9, lunge, loop=False),
        "hiss": clip(1.2, hiss, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig, _legs = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    skin = paint(rig, tex_h)
    return rigkit.emit(rig, skin, build_anim(), cid=CID, tool="kneazle_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
