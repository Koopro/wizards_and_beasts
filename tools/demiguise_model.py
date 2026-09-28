#!/usr/bin/env python3
"""The Demiguise: rig, skin and animation.

Canon: a peaceful, herbivorous beast, "a little like a graceful ape with large, black, doleful
eyes", covered in long silky silver hair — the hair invisibility cloaks are woven from. Its
kit here is `camouflage` and `evasion`; it does not fight.

So the two things the rig has to carry are the **hair** and the **eyes**. The hair is a mantle
that hangs from the shoulders down past the hips, on its own bone so it sways a beat behind
the body; long hair that moves rigidly with the torso reads as a plastic shell. The eyes are
the largest features on the face by a distance, and sad — set low and wide, with no brow.

Drawn oversize for the same reason the Puffskein is: the 0.75-block hitbox is 12 texels tall,
and a face with doleful eyes needs more skull than that.

Run from the repo root:  python tools/demiguise_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, limb, osc  # noqa: E402

CID = "demiguise"
HITBOX = (0.65, 0.75)
TEX_W = 64

HAIR = hx("#D8DDE3")
HAIR_SHADE = hx("#A7AFB9")
HAIR_LIT = hx("#F1F3F6")
FACE = hx("#5E656E")
FACE_DARK = hx("#40464D")
HAND = hx("#6B727B")
EYE = hx("#0E0F12")
GLINT = hx("#E8EEF6")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 6, 0))
    rig.cube("body", (-3.5, 5, -2.5), (7, 6, 5), key="torso")

    # The mantle: a hanging sheet of hair from the shoulders to below the hips, on its own bone
    # so it trails the body in every clip.
    rig.bone("mantle", "body", (0, 11, 2))
    rig.cube("mantle", (-4, 3, 1), (8, 8, 3), inflate=0.25)

    rig.bone("head", "body", (0, 11, -0.5), (6, 0, 0))
    rig.cube("head", (-3.5, 11, -3.5), (7, 5, 5))
    rig.cube("head", (-2, 11, -4.5), (4, 3, 1), key="muzzle")
    # Hair falling over the crown and down the back of the skull.
    rig.cube("head", (-4, 14, -3), (8, 3, 6), key="crown", inflate=0.25)

    # Long arms, as an ape's are: knuckles nearly at the floor.
    def build_arm(side, sign):
        limb(rig, f"arm_{side}", "body", x=4.5, z=0, sign=sign, joints=[
            ("", 11, 5, 2, 2, (0, 0, sign * -6)),
            ("fore", 6, 4, 2, 2, (-10, 0, 0)),
            ("hand", 2, 2, 2, 2, None),
        ])

    def build_leg(side, sign):
        limb(rig, f"leg_{side}", "body", x=1.8, z=0, sign=sign, joints=[
            ("", 6, 4, 3, 3, None),
            ("foot", 2, 2, 3, 4, None),
        ])

    rig.pair(build_arm)
    rig.pair(build_leg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    arms = rig.keys("arm_")
    legs = rig.keys("leg_")

    skin.skin(["torso", "mantle", "crown"], HAIR, top=HAIR_LIT, dither=0.1,
              dither_colour=HAIR_SHADE)
    skin.skin([k for k in arms + legs if "hand" not in k and "foot" not in k], HAIR,
              dither=0.1, dither_colour=HAIR_SHADE)
    skin.skin([k for k in arms + legs if "hand" in k or "foot" in k], HAND, dither=0.0)

    # Silky, not shaggy: long vertical strands down every hanging face, a combed look rather
    # than the mottle a coarse coat would get.
    # Not the crown's front face: strands there, over a face this small, read as a chef's hat.
    for key, faces in (("mantle", skin.SIDES), ("torso", skin.SIDES),
                       ("crown", ("east", "west", "south"))):
        for face in faces:
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=HAIR_SHADE)

    # The face: dark bare skin under the fringe, and eyes that take up most of it.
    skin.skin(["head", "muzzle"], FACE, dither=0.05, dither_colour=FACE_DARK)
    x, y, w, h = skin.face("head", "north")
    for ex in (x + 1, x + w - 3):     # 7 wide: eyes at 1-2 and 4-5, one texel of face between
        skin.d.rectangle([ex, y + 1, ex + 1, y + 2], fill=EYE)
        skin.d.point((ex, y + 1), fill=GLINT)
    skin.hline(skin.face("head", "north"), 0, HAIR)   # the fringe over the brow
    skin.mark("muzzle", "north", 1, 2, FACE_DARK, w=2)
    return skin


def build_anim():
    idle = {
        "body": bob(4.2, 0.12, cycles=2),
        # The mantle lags the body by a quarter cycle — hair settling after the breath.
        "mantle": osc(4.2, 3.0, phase=0.25),
        "head": {"rotation": rigkit.kf((0.0, (0, 10, 0)), (1.4, (4, -12, 0)),
                                       (2.8, (-2, 6, -3)), (4.2, (0, 10, 0)))},
        "arm_left": osc(4.2, 3.0, axis="z", phase=0.0),
        "arm_right": osc(4.2, 3.0, axis="z", phase=0.5),
    }

    walk = {
        "leg_left": osc(0.8, 30.0, phase=0.0),
        "leg_right": osc(0.8, 30.0, phase=0.5),
        # Arms swing opposite the legs and the forearm trails, which is an ape's amble.
        "arm_left": osc(0.8, 24.0, phase=0.5),
        "arm_right": osc(0.8, 24.0, phase=0.0),
        "arm_left_fore": osc(0.8, 12.0, phase=0.65),
        "arm_right_fore": osc(0.8, 12.0, phase=0.15),
        "body": osc(0.8, 4.0, axis="z"),
        "root": bob(0.8, 0.3, cycles=2),
        "mantle": osc(0.8, 6.0, phase=0.3),
    }

    # `flinch`: it does not fight, so it hides — hunches, covers its face with its arms.
    flinch = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (22, 0, 0)),
                                       (0.6, (16, 0, 0)), (0.9, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (20, 0, 0)), (0.9, (0, 0, 0)))},
        "arm_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (-100, 0, 18)),
                                           (0.6, (-96, 0, 18)), (0.9, (0, 0, 0)))},
        "arm_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (-100, 0, -18)),
                                            (0.6, (-96, 0, -18)), (0.9, (0, 0, 0)))},
        "arm_left_fore": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (-40, 0, 0)),
                                                (0.9, (0, 0, 0)))},
        "arm_right_fore": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.15, (-40, 0, 0)),
                                                 (0.9, (0, 0, 0)))},
        "mantle": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.2, (-14, 0, 0)), (0.9, (0, 0, 0)))},
    }

    # `call` on the ambient beat: a slow look over each shoulder. A Demiguise sees the probable
    # future, and a creature that keeps checking behind itself is the closest a rig gets.
    call = {
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.5, (-4, 48, 0)), (1.1, (-4, 48, 0)),
                                       (1.7, (-4, -44, 0)), (2.3, (-4, -44, 0)),
                                       (2.8, (0, 0, 0)))},
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.5, (0, 10, 0)),
                                       (1.7, (0, -10, 0)), (2.8, (0, 0, 0)))},
    }

    return {
        "idle": clip(4.2, idle),
        "walk": clip(0.8, walk),
        "flinch": clip(0.9, flinch, loop=False),
        "call": clip(2.8, call, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID,
                       tool="demiguise_model.py", hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
