#!/usr/bin/env python3
"""The Troll: rig, skin and animation.

A mountain troll — the one that ends up in a girls' bathroom in the first book: twelve feet of
granite-grey hide, a small bald head, short thick legs, long arms, a wooden club, and a smell.
136 health, 14 damage, KNOCKBACK and CHARGE: the first thing most players will fight that can
kill them in two hits, so the silhouette has to arrive before the damage does.

The proportions are the character, and every one of them is the opposite of a human's:

  - **the head is small and sits low**, in front of the shoulders rather than on top of them, so
    the troll is hunched and its highest point is the hump of its back;
  - **the gut is wider than the chest**, and the legs are short under it;
  - **the arms are long enough that the knuckles reach the knee**, and one of them drags a club.

Built at 2.8 blocks against the 2.7 x 2.9 hitbox.

Run from the repo root:  python tools/troll_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, limb, osc  # noqa: E402

CID = "troll"
HITBOX = (2.7, 2.9)
TEX_W = 128

HIDE = hx("#7F8479")
HIDE_DARK = hx("#5A5E55")
HIDE_LIT = hx("#9A9E92")
WART = hx("#6B6A57")
CLOTH = hx("#5B4A36")
CLOTH_DARK = hx("#3E3224")
WOOD = hx("#6E4E2E")
WOOD_DARK = hx("#4A331D")
TOOTH = hx("#CFC5A2")
EYE = hx("#E0C54C")
NAIL = hx("#3F3A30")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 18, 0))

    # Gut wider than the chest, chest wider than the hump. Read from the side, the torso leans
    # forward from the hips, which is what puts the head in front of the shoulders.
    rig.cube("body", (-10, 14, -10), (20, 13, 18), key="gut")
    rig.cube("body", (-10, 12, -10.5), (20, 7, 19), key="loincloth", inflate=0.5)

    rig.bone("chest", "body", (0, 27, 0), (14, 0, 0))
    rig.cube("chest", (-11, 27, -7), (22, 11, 14))
    rig.cube("chest", (-7, 36, -2), (14, 6, 10), key="hump")

    rig.bone("head", "chest", (0, 37, -7), (-10, 0, 0))
    rig.cube("head", (-5, 33, -16), (10, 9, 10))
    rig.cube("head", (-4, 31, -17), (8, 3, 5), key="jaw")
    rig.cube("head", (-1.5, 35, -18), (3, 3, 2), key="nose")

    def build_ear(side, sign):
        rig.cube("head", Rig.mirror((5, 36, -11), (2, 4, 2), sign), (2, 4, 2), key=f"ear_{side}")

    def build_tusk(side, sign):
        rig.cube("head", Rig.mirror((2, 33, -18), (1, 3, 1), sign), (1, 3, 1), key=f"tusk_{side}")

    rig.pair(build_ear)
    rig.pair(build_tusk)

    # Arms long enough that the knuckles hang at the knee.
    def build_arm(side, sign):
        limb(rig, f"arm_{side}", "chest", x=13, z=-1, sign=sign, joints=[
            ("", 37, 13, 7, 7, (-8, 0, sign * -8)),
            ("fore", 24, 13, 6, 6, (-12, 0, 0)),
            ("hand", 11, 6, 7, 7, None),
        ])

    def build_leg(side, sign):
        limb(rig, f"leg_{side}", "body", x=6, z=0, sign=sign, joints=[
            ("", 16, 8, 8, 8, None),
            ("shin", 8, 6, 7, 7, None),
            ("foot", 2, 2, 9, 11, None),
        ])

    rig.pair(build_arm)
    rig.pair(build_leg)

    # The club hangs from the right hand and drags behind. Its own bone under the hand so the
    # attack clip swings the arm and the club follows without being animated separately.
    rig.bone("club", "arm_right_hand", (-13, 7, -1), (CLUB_DRAG, 0, 0))
    rig.cube("club", (-14.5, 2, -2.5), (3, 6, 3), key="club_grip")
    rig.cube("club", (-16.5, -14, -4.5), (7, 16, 7), key="club_head")
    return rig


def _club_drag():
    """The club's rest angle, solved so its head rests on the floor rather than through it.

    Authored hanging straight down, a 22-unit club from a hand at knee height goes 14 units
    into the ground. A positive X rotation swings a hanging bone backward, so the solve is the
    smallest backward swing that brings the lowest corner of the rig up to the floor — the club
    trailing behind the troll, which is also how a troll carries one.
    """
    global CLUB_DRAG
    lo, hi = 0.0, 90.0
    for _ in range(40):
        CLUB_DRAG = (lo + hi) / 2
        if build().bounds()[0][1] < 0:
            lo = CLUB_DRAG
        else:
            hi = CLUB_DRAG
    CLUB_DRAG = round(hi, 1)
    return CLUB_DRAG


CLUB_DRAG = 0.0


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    limbs = rig.keys("arm_", "leg_")

    skin.skin(["gut", "chest", "hump", "head", "jaw", "nose"], HIDE, top=HIDE_LIT, dither=0.1,
              dither_colour=HIDE_DARK)
    skin.skin(limbs, HIDE, dither=0.1, dither_colour=HIDE_DARK)
    skin.skin(["ear_left", "ear_right"], HIDE_DARK, dither=0.0)
    skin.skin(["tusk_left", "tusk_right"], TOOTH, dither=0.0)

    # Warts and folds: deterministic lumps on the big faces. Stone-grey hide with no texture
    # reads as a statue, which is a different mob.
    for key in ("gut", "chest", "hump", "head"):
        for face in ("north", "east", "west", "south", "top"):
            x, y, w, h = skin.face(key, face)
            for row in range(1, h - 1, 3):
                for col in range(1 + row % 2, w - 1, 4):
                    if (col * 7 + row * 13 + len(key)) % 3 == 0:
                        skin.d.rectangle([x + col, y + row, x + col + 1, y + row], fill=WART)

    skin.skin("loincloth", CLOTH, dither=0.08, dither_colour=CLOTH_DARK)
    skin.bands("loincloth", CLOTH_DARK, faces=("north", "south", "east", "west"), step=3)
    skin.tip([k for k in limbs if "hand" in k or "foot" in k], NAIL, rows=1)

    skin.skin(["club_grip"], WOOD_DARK, dither=0.0)
    skin.bands("club_grip", CLOTH, step=2)
    skin.skin(["club_head"], WOOD, dither=0.12, dither_colour=WOOD_DARK)
    skin.bands("club_head", WOOD_DARK, faces=("north", "south", "east", "west"), step=4)

    # Small, close-set, dim eyes under a heavy brow — the brow is a dark row across the face.
    x, y, w, h = skin.face("head", "north")
    skin.hline((x, y, w, h), 2, HIDE_DARK)
    skin.d.rectangle([x + 2, y + 3, x + 3, y + 3], fill=EYE)
    skin.d.rectangle([x + w - 4, y + 3, x + w - 3, y + 3], fill=EYE)
    skin.hline(skin.face("jaw", "north"), 0, HIDE_DARK)
    return skin


def build_anim():
    # Everything slow and heavy. A troll's weight is its tempo.
    idle = {
        "body": bob(5.0, 0.5, cycles=2),
        "chest": osc(5.0, 2.5, phase=0.1),
        "head": {"rotation": rigkit.kf((0.0, (0, -12, 0)), (1.7, (4, 14, 0)),
                                       (3.4, (-2, -4, 3)), (5.0, (0, -12, 0)))},
        "arm_left": osc(5.0, 4.0, phase=0.0),
        "arm_right": osc(5.0, 3.0, phase=0.5),
        "club": osc(5.0, 3.0, axis="z", phase=0.2),
    }

    walk = {
        "leg_left": osc(1.4, 22.0, phase=0.0),
        "leg_right": osc(1.4, 22.0, phase=0.5),
        "leg_left_shin": osc(1.4, 12.0, phase=0.15),
        "leg_right_shin": osc(1.4, 12.0, phase=0.65),
        "arm_left": osc(1.4, 18.0, phase=0.5),
        "arm_right": osc(1.4, 10.0, phase=0.0),
        # A lurch, side to side, because the legs are short and the gut is not.
        "body": osc(1.4, 5.0, axis="z"),
        "chest": osc(1.4, 3.0, axis="y", phase=0.25),
        "root": bob(1.4, 1.2, cycles=2),
        "club": osc(1.4, 6.0, phase=0.2),
    }

    # `attack` is the overhead slam: club up and back over the shoulder, a beat of hang, then
    # down through the target with the whole torso behind it.
    attack = {
        "arm_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (-160, 0, 12)),
                                            (0.6, (-165, 0, 12)), (0.8, (-20, 0, 0)),
                                            (1.3, (0, 0, 0)))},
        "arm_right_fore": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (-30, 0, 0)),
                                                 (0.8, (0, 0, 0)), (1.3, (0, 0, 0)))},
        "chest": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (-18, -16, 0)),
                                        (0.6, (-20, -18, 0)), (0.8, (22, 8, 0)),
                                        (1.3, (0, 0, 0)))},
        "arm_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (-30, 0, -14)),
                                           (0.8, (24, 0, 0)), (1.3, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.6, (0, 0.8, 1.5)),
                                       (0.8, (0, -1.5, -2.5)), (1.3, (0, 0, 0)))},
    }

    # `groan` on the ambient beat: head back, jaw open, a shrug that runs through the hump.
    groan = {
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.6, (-26, 0, 0)),
                                       (1.6, (-20, 0, 0)), (2.2, (0, 0, 0)))},
        "chest": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.6, (-8, 0, 0)),
                                        (1.6, (-6, 0, 0)), (2.2, (0, 0, 0)))},
        "arm_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.7, (0, 0, -14)),
                                           (2.2, (0, 0, 0)))},
        "arm_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.7, (0, 0, 14)),
                                            (2.2, (0, 0, 0)))},
    }

    return {
        "idle": clip(5.0, idle),
        "walk": clip(1.4, walk),
        "attack": clip(1.3, attack, loop=False),
        "groan": clip(2.2, groan, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    _club_drag()
    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID, tool="troll_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
