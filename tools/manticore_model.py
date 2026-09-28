#!/usr/bin/env python3
"""The Manticore: rig, skin and animation.

Greek by way of the Fantastic Beasts A-Z: a lion's body, a man's head, and a scorpion's tail
whose sting kills instantly. It is the one creature in this wave that is a *composite of three
things*, and the failure mode is that the head ends up a lion's and the whole animal collapses
into another big cat.

So the head is built as a skull, not a muzzle — flat face, brow ridge, a jaw rather than a
snout — and it is ringed by a mane, which is the join. The tail is the other half of the
identity: five tapering segments and a black sting, held arched over the back where the player
can see it, because a sting dragged along the floor communicates nothing.

**GROUND locomotion, not FLYING**, despite the WINGED_QUADRUPED body plan. The movement
controller binds `idle` and `walk`; the wings are folded and decorative and never beat.

Run from the repo root:  python tools/manticore_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "manticore"
HITBOX = (1.7, 1.9)
TEX_W = 128

PELT = hx("#9C5A32")
PELT_DARK = hx("#6B3B1E")
BELLY = hx("#C08A55")
MANE = hx("#4A2413")
MANE_LIT = hx("#6E3A1E")
SKIN_ = hx("#C79A6E")
MEMBRANE = hx("#5E3324")
CHITIN = hx("#2E2A2E")
CHITIN_LIT = hx("#4B454E")
STING = hx("#171419")
VENOM = hx("#9ED84A")
EYE = hx("#E8D24A")
TOOTH = hx("#E4DCC6")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 20, 2))
    rig.cube("body", (-5, 13, -2), (10, 10, 13), key="barrel")
    rig.cube("body", (-6, 12, -11), (12, 12, 9), key="chest")

    rig.bone("croup", "body", (0, 21, 11), (-5, 0, 0))
    rig.cube("croup", (-5, 13, 10), (10, 10, 6))

    # Scorpion tail: five segments, each bone pivoted at the joint before it, arched up and
    # forward over the back. Each carries a positive X rotation, so the arch accumulates
    # segment by segment rather than being one rigid hook.
    rig.bone("tail_1", "croup", (0, 21, 15), (34, 0, 0))
    rig.cube("tail_1", (-2.5, 19, 15), (5, 5, 7))
    rig.bone("tail_2", "tail_1", (0, 21, 22), (26, 0, 0))
    rig.cube("tail_2", (-2, 19, 22), (4, 4, 6))
    rig.bone("tail_3", "tail_2", (0, 21, 28), (24, 0, 0))
    rig.cube("tail_3", (-1.5, 19, 28), (3, 4, 6))
    rig.bone("tail_4", "tail_3", (0, 21, 34), (22, 0, 0))
    rig.cube("tail_4", (-1.5, 19, 34), (3, 3, 5))
    rig.bone("sting", "tail_4", (0, 20, 39), (30, 0, 0))
    rig.cube("sting", (-2, 18, 39), (4, 4, 4), key="sting_bulb")
    rig.cube("sting", (-1, 17, 43), (2, 3, 4), key="sting_barb")

    # A man's head on a mane. The mane is authored as its own box around the neck and grown
    # with `inflate`, so it is wider than the skull without costing extra texels.
    rig.bone("neck", "body", (0, 23, -9), (16, 0, 0))
    rig.cube("neck", (-3, 22, -12), (6, 8, 6))
    rig.cube("neck", (-5, 20, -13), (10, 11, 8), key="mane", inflate=1.0)

    rig.bone("head", "neck", (0, 30, -9), (-18, 0, 0))
    rig.cube("head", (-4, 28, -17), (8, 8, 7))
    rig.cube("head", (-3, 28, -18), (6, 3, 1), key="jaw")
    # The mane runs round the face rather than stopping behind it — this is what makes a human
    # face on a lion body read as one creature and not as a mask.
    rig.cube("head", (-5.5, 26, -16), (11, 12, 5), key="ruff", inflate=0.5)

    # Folded wings: a manticore walks. Two short slabs tucked along the ribs, leathery rather
    # than feathered, so nothing about them promises flight.
    def build_wing(side, sign):
        rig.bone(f"wing_{side}", "body", (sign * 5, 23, -6), (0, 0, sign * -18))
        rig.cube(f"wing_{side}", Rig.mirror((5, 14, -6), (1, 10, 14), sign), (1, 10, 14))

    rig.pair(build_wing)

    def build_foreleg(side, sign):
        limb(rig, f"foreleg_{side}", "body", x=4, z=-7, sign=sign, joints=[
            ("", 14, 8, 5, 6, None),
            ("shank", 7, 5, 4, 4, None),
            ("paw", 3, 3, 5, 7, None),
        ])

    def build_hindleg(side, sign):
        limb(rig, f"hindleg_{side}", "croup", x=4, z=12, sign=sign, joints=[
            ("", 15, 9, 6, 8, (12, 0, 0)),
            ("shank", 7, 5, 4, 5, (-16, 0, 0)),
            ("paw", 3, 3, 5, 7, None),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    tail = ["tail_1", "tail_2", "tail_3", "tail_4"]

    skin.skin(["barrel", "chest", "croup"], PELT, bottom=BELLY, dither=0.05,
              dither_colour=PELT_DARK)
    skin.skin(legs, PELT, dither=0.04, dither_colour=PELT_DARK)
    skin.skin([k for k in legs if "paw" in k], PELT_DARK, dither=0.0)
    skin.skin("neck", PELT, dither=0.04, dither_colour=PELT_DARK)

    skin.skin(["mane", "ruff"], MANE, dither=0.16, dither_colour=MANE_LIT)
    # Combed banding over the mane, which is what stops a big dark box reading as a shadow.
    skin.bands(["mane", "ruff"], MANE_LIT, faces=("east", "west", "top"), step=3)

    # The face: bare skin, forward-set eyes, a mouth of teeth. Painted on the north face only,
    # because the sides of the head are mane.
    skin.skin(["head", "jaw"], MANE, dither=0.0)
    skin.rect(skin.face("head", "north"), SKIN_)
    skin.eyes("head", EYE, row=2, inset=2, pupil=hx("#20160C"), glow=True)
    skin.mark("head", "north", 3, 4, hx("#8E6547"), w=2)
    skin.rect(skin.face("jaw", "north"), TOOTH)
    skin.bands("jaw", MANE, faces=("north",), step=2, offset=0)

    # Wings: leather, with the ribs painted across rather than modelled — at one texel thick
    # there is nowhere to put a rib cube that would not z-fight with the membrane.
    wings = rig.keys("wing_")
    skin.skin(wings, MEMBRANE, dither=0.0)
    skin.ramp(wings, PELT_DARK, MEMBRANE, faces=("east", "west"))
    for key in wings:
        x, y, w, h = skin.face(key, "east")
        for col in range(0, w, 4):
            skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=MANE)
        x, y, w, h = skin.face(key, "west")
        for col in range(0, w, 4):
            skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=MANE)

    # Tail: chitin, banded, tapering to a venom-wet sting. The sting is the darkest thing on
    # the model on purpose — it is the part that kills.
    skin.skin(tail, CHITIN, dither=0.06, dither_colour=CHITIN_LIT)
    skin.bands(tail, CHITIN_LIT, step=3)
    skin.skin(["sting_bulb", "sting_barb"], STING, dither=0.0)
    skin.tip("sting_barb", VENOM, rows=1)
    skin.glow_rect((skin.face("sting_barb", "top")))
    skin.mark("sting_bulb", "east", 1, 1, VENOM, glow=True)
    skin.mark("sting_bulb", "west", 1, 1, VENOM, glow=True)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}
    tail = ["tail_1", "tail_2", "tail_3", "tail_4", "sting"]

    # The tail is never still, and each segment lags the one before it, which is what makes a
    # five-box chain read as one flexible thing.
    idle = {
        "body": bob(4.4, 0.2, cycles=2),
        "neck": osc(4.4, 3.0, phase=0.1),
        "head": {"rotation": rigkit.kf((0.0, (0, -10, 0)), (1.5, (3, 12, 0)),
                                       (3.0, (-2, -6, 0)), (4.4, (0, -10, 0)))},
        **{name: osc(4.4, 5.0 + 2.0 * i, axis="z", phase=0.1 * i)
           for i, name in enumerate(tail)},
        "wing_left": osc(4.4, 4.0, axis="z", phase=0.0),
        "wing_right": osc(4.4, 4.0, axis="z", phase=0.5),
    }

    walk = {
        **gait(1.0, legs, amp=24.0),
        **{f"{name}_shank": osc(1.0, 15.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        "body": osc(1.0, 3.0, axis="y"),
        "root": bob(1.0, 0.5, cycles=2),
        **{name: osc(1.0, 4.0 + 2.0 * i, axis="z", phase=0.12 * i)
           for i, name in enumerate(tail)},
    }

    # `strike` is the sting, and it is the whole animal: the body drops, the tail whips over
    # the head, and the sting arrives ahead of everything else.
    strike = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (12, 0, 0)),
                                       (0.45, (-6, 0, 0)), (0.9, (0, 0, 0)))},
        "tail_1": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (22, 0, 0)),
                                         (0.45, (-64, 0, 0)), (0.9, (0, 0, 0)))},
        "tail_2": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (18, 0, 0)),
                                         (0.5, (-48, 0, 0)), (0.9, (0, 0, 0)))},
        "tail_3": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)),
                                         (0.55, (-40, 0, 0)), (0.9, (0, 0, 0)))},
        "tail_4": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (10, 0, 0)),
                                         (0.55, (-34, 0, 0)), (0.9, (0, 0, 0)))},
        "sting": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (8, 0, 0)),
                                        (0.6, (-30, 0, 0)), (0.9, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-16, 0, 0)),
                                       (0.5, (18, 0, 0)), (0.9, (0, 0, 0)))},
    }

    # `hiss` on the ambient beat: head forward, jaw open, tail cocked. A manticore is supposed
    # to be able to speak; a hiss is the version of that the rig can carry.
    hiss = {
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)),
                                       (1.0, (-16, 0, 0)), (1.4, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (10, 0, 0)),
                                       (1.0, (8, 0, 0)), (1.4, (0, 0, 0)))},
        "tail_1": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (26, 0, 0)), (1.4, (0, 0, 0)))},
        "sting": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (22, 0, 0)), (1.4, (0, 0, 0)))},
        "wing_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (0, 0, -34)),
                                            (1.4, (0, 0, 0)))},
        "wing_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (0, 0, 34)),
                                             (1.4, (0, 0, 0)))},
    }

    return {
        "idle": clip(4.4, idle),
        "walk": clip(1.0, walk),
        "strike": clip(0.9, strike, loop=False),
        "hiss": clip(1.4, hiss, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID,
                       tool="manticore_model.py", hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
