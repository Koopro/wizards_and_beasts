#!/usr/bin/env python3
"""The Centaur: rig, skin and animation.

The Forbidden Forest's centaurs — Firenze, Bane, Magorian — are proud, star-reading, and armed
with bows. A human torso rising out of a horse's chest, and the join is the only hard part:
the human waist has to come *out of* the front of the barrel at the withers, not sit on top
of the horse's back like a rider, or the creature reads as a man on a pony.

So the torso bone is parented to the horse body at the front of the barrel and its cubes
start below the horse's topline, overlapping into the chest, and the horse has no neck or
head of its own. The bow hangs from the left hand on its own bone, so the `attack` clip —
which is what fires when its `ranged_hex` lands — draws and looses with the arms and the bow
follows.

Built at 2.1 blocks against the 1.7 x 1.9 hitbox; the human head is what rises above it.

Run from the repo root:  python tools/centaur_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "centaur"
HITBOX = (1.7, 1.9)
TEX_W = 128

COAT = hx("#8A5A34")
COAT_DARK = hx("#633F22")
BELLY = hx("#A5764C")
TAIL = hx("#3A2A1E")
HOOF = hx("#2E2721")
SKIN_ = hx("#C8986E")
SKIN_DARK = hx("#A57A54")
HAIR = hx("#3B2B20")
EYE = hx("#2E4A6A")
WOOD = hx("#7A5632")
WOOD_DARK = hx("#51381F")
STRING = hx("#D8D2C0")
QUIVER = hx("#5C3F26")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 17, 4))

    # Horse half: barrel and croup, no neck, no head. The human rises where the neck would be.
    rig.cube("body", (-4.5, 11, -4), (9, 8, 14), key="barrel")
    rig.bone("croup", "body", (0, 18, 10), (-4, 0, 0))
    rig.cube("croup", (-4, 11, 9), (8, 8, 5))

    rig.bone("tail", "croup", (0, 17, 13), (22, 0, 0))
    rig.cube("tail", (-1, 14, 12), (2, 3, 2), key="tail_dock")
    rig.bone("tail_hair", "tail", (0, 15, 13), (8, 0, 0))
    rig.cube("tail_hair", (-1.5, 5, 12), (3, 10, 3))

    # Human half. The waist starts two units *below* the horse's topline and overlaps the front
    # of the barrel, so there is no seam of air or ledge of horse back in front of the torso.
    rig.bone("torso", "body", (0, 17, -2))
    rig.cube("torso", (-4, 15, -4.5), (8, 7, 5), key="waist")
    rig.cube("torso", (-4.5, 22, -4.5), (9, 7, 5), key="chest")
    rig.cube("torso", (-2, 23, 0.5), (4, 7, 2), key="quiver", rotation=(0, 0, 20),
             pivot=(0, 26, 1))

    rig.bone("head", "torso", (0, 29, -2))
    rig.cube("head", (-3, 29, -5), (6, 6, 6))
    rig.cube("head", (-3.5, 31, -4.5), (7, 5, 6), key="hair", inflate=0.25)
    rig.cube("head", (-3, 27, -2), (6, 4, 3), key="hair_back")

    def build_arm(side, sign):
        limb(rig, f"arm_{side}", "torso", x=5.5, z=-2, sign=sign, joints=[
            ("", 29, 6, 2, 3, (0, 0, sign * -8)),
            ("fore", 23, 6, 2, 2, (-18, 0, 0)),
        ])

    rig.pair(build_arm)

    # The bow, in the left hand, held low at rest.
    rig.bone("bow", "arm_left_fore", (5.5, 17, -2), (0, 0, 0))
    rig.cube("bow", (5, 10, -3), (1, 14, 1), key="bow_stave")
    rig.cube("bow", (5, 10, -1), (1, 14, 1), key="bow_string")

    def build_foreleg(side, sign):
        limb(rig, f"foreleg_{side}", "body", x=2.8, z=-1, sign=sign, joints=[
            ("", 13, 6, 3, 4, None),
            ("cannon", 7, 5, 2, 2, None),
            ("hoof", 2, 2, 3, 3, None),
        ])

    def build_hindleg(side, sign):
        limb(rig, f"hindleg_{side}", "croup", x=2.8, z=11, sign=sign, joints=[
            ("", 15, 8, 4, 5, (10, 0, 0)),
            ("hock", 7, 5, 2, 3, (-14, 0, 0)),
            ("hoof", 2, 2, 3, 3, None),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    arms = rig.keys("arm_")

    skin.skin(["barrel", "croup", "tail_dock"], COAT, bottom=BELLY, dither=0.05,
              dither_colour=COAT_DARK)
    skin.skin(legs, COAT, dither=0.04, dither_colour=COAT_DARK)
    skin.skin([k for k in legs if "hoof" in k], HOOF, dither=0.0)
    skin.skin("tail_hair", TAIL, dither=0.0)
    skin.bands("tail_hair", COAT_DARK, step=3)

    # Human skin, with the horse coat running up over the waist so the join is a gradient of
    # hair rather than a line where one texture stops.
    skin.skin(["head"] + arms, SKIN_, dither=0.03, dither_colour=SKIN_DARK)
    # Waist and chest are one continuous torso built from two boxes, so neither gets the house
    # bevel: a lit top row and a shaded bottom row on each box put two seams across the man's
    # stomach, and with a pectoral line on top the torso read as a banded tunic.
    skin.skin(["chest", "waist"], SKIN_, bevel=0, dither=0.03, dither_colour=SKIN_DARK)
    # The coat takes the bottom three rows of the waist, with one dithered row as the fray.
    for face in skin.SIDES:
        x, y, w, h = skin.face("waist", face)
        skin.rect((x, y + h - 3, w, 3), COAT)
        skin.dither((x, y + h - 4, w, 1), COAT, 0.5, 11)

    skin.skin(["hair", "hair_back"], HAIR, dither=0.08, dither_colour=COAT_DARK)
    # The hair box stops just behind the face rather than wrapping it, and the fringe is paint
    # on the face's top row. Wrapping it and cutting the face out as transparent texels would
    # work under a cutout render type, but it ships holes on purpose, and `uv_check` exists
    # precisely so a hole on a rig is always a bug.
    skin.hline(skin.face("head", "north"), 0, HAIR)
    skin.eyes("head", EYE, row=2, inset=1, pupil=hx("#101820"))
    skin.mark("head", "north", 2, 4, SKIN_DARK, w=2)

    skin.skin("quiver", QUIVER, dither=0.0)
    skin.tip("quiver", STRING, rows=1, faces=("top",))
    skin.skin("bow_stave", WOOD, dither=0.0)
    skin.tip("bow_stave", WOOD_DARK, rows=2)
    skin.skin("bow_string", STRING, dither=0.0)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}

    idle = {
        "body": bob(4.2, 0.2, cycles=2),
        "torso": osc(4.2, 2.0, phase=0.1),
        # Looking up. Centaurs read the stars; this one keeps checking.
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (1.2, (-14, 12, 0)),
                                       (2.6, (-18, -6, 0)), (4.2, (0, 0, 0)))},
        "arm_left": osc(4.2, 3.0, phase=0.0),
        "arm_right": osc(4.2, 3.0, phase=0.5),
        "tail": osc(4.2, 6.0, axis="z", phase=0.3),
    }

    walk = {
        **gait(0.9, legs, amp=24.0),
        **{f"{name}_hock": osc(0.9, 16.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        **{f"{name}_cannon": osc(0.9, 14.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("foreleg")},
        "root": bob(0.9, 0.5, cycles=2),
        # The torso counter-sways against the horse's gait, the way a walker's shoulders do.
        "torso": osc(0.9, 3.0, axis="y", phase=0.5),
        "arm_left": osc(0.9, 10.0, phase=0.5),
        "arm_right": osc(0.9, 10.0, phase=0.0),
        "tail_hair": osc(0.9, 6.0, phase=0.4),
    }

    # `attack` is the draw and loose: bow arm straight out, string hand back to the jaw, hold,
    # release. It fires on the hex landing, so the hold is short.
    attack = {
        "torso": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (0, -28, 0)),
                                        (0.8, (0, -30, 0)), (1.2, (0, 0, 0)))},
        "arm_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (-88, 0, 0)),
                                           (0.8, (-90, 0, 0)), (1.2, (0, 0, 0)))},
        "arm_left_fore": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (18, 0, 0)),
                                                (1.2, (0, 0, 0)))},
        "bow": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (0, 0, -90)),
                                      (0.8, (0, 0, -90)), (1.2, (0, 0, 0)))},
        "arm_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (-86, 30, 0)),
                                            (0.8, (-88, 36, 0)), (0.85, (-70, 0, 0)),
                                            (1.2, (0, 0, 0)))},
        "arm_right_fore": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (-60, 0, 0)),
                                                 (0.8, (-64, 0, 0)), (0.85, (0, 0, 0)),
                                                 (1.2, (0, 0, 0)))},
    }

    # `call` is the rear: forelegs up, torso thrown back, arms raised.
    call = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (-26, 0, 0)),
                                       (1.0, (-22, 0, 0)), (1.5, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (-60, 0, 0)),
                                               (0.7, (-30, 0, 0)), (1.0, (-56, 0, 0)),
                                               (1.5, (0, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (-40, 0, 0)),
                                                (0.75, (-62, 0, 0)), (1.05, (-34, 0, 0)),
                                                (1.5, (0, 0, 0)))},
        "hindleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (26, 0, 0)),
                                               (1.5, (0, 0, 0)))},
        "hindleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (26, 0, 0)),
                                                (1.5, (0, 0, 0)))},
        "torso": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (10, 0, 0)), (1.5, (0, 0, 0)))},
        "arm_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.5, (-150, 0, -10)),
                                            (1.1, (-146, 0, -10)), (1.5, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.4, (0, 0, 2)), (1.5, (0, 0, 0)))},
    }

    return {
        "idle": clip(4.2, idle),
        "walk": clip(0.9, walk),
        "attack": clip(1.2, attack, loop=False),
        "call": clip(1.5, call, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID, tool="centaur_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
