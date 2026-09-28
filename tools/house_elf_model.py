#!/usr/bin/env python3
"""The house-elf: rig, skin and animation for the `house_elf_default` player heritage form.

Until 2026-09-27 this form had no rig: `FormModelRenderer` drew the goblin's legacy box model with a
texture path that did not exist, at full brightness, so a house-elf player glowed at night as an
untextured doll (visual consistency audit, C2).

Canon (*Chamber of Secrets* ch. 2): "a little creature with large, bat-like ears and bulging green
eyes the size of tennis balls", a long pencil-shaped nose, dressed in an old pillowcase with holes
for arms and legs. So the read is, in order: ears wider than the head, two huge green eyes, a thin
nose, a pale pillowcase over a thin frame. At 0.99 blocks it is smaller than the goblin and must
not be mistaken for one — the goblin is swarthy, bearded and dressed; the elf is pale and ragged.

Mob-less: nothing spawns a house-elf, so there is no definition and no shared hit/death clips.
`PlayerFormRig` asks for `idle` and `walk`.

Run from the repo root:  python tools/house_elf_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "house_elf"
TOOL = "house_elf_model.py"
HITBOX = (0.33, 0.99)

SKIN_ = hx("#B8B09A")
SKIN_DARK = hx("#8E8672")
EAR_INNER = hx("#C79A92")
CLOTH = hx("#E6E0CE")
CLOTH_DARK = hx("#B6AE98")
EYE = hx("#6FB84A")
EYE_DARK = hx("#2C4A1E")
MOUTH = hx("#5A4038")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=5, pelvis=(4, 2, 3), torso=(4, 5, 3), head=(6, 6, 5), hunch=6, neck=1,
              legs=[("", 3, 1, 1, None), ("foot", 2, 2, 3, None)],
              arms=[("", 4, 1, 1, (0, 0, -4)), ("hand", 2, 1, 1, None)],
              arm_x=3.5, leg_x=1)
    # The pillowcase: one inflated sack over torso and hips, hem hanging below the waist.
    rig.cube("chest", (-2.5, a.waist - 3, -2), (5, 7, 4), key="pillowcase", inflate=0.2)
    # Tennis-ball eyes stand proud of the face; the pencil nose between and below them.
    rig.cube("head", (-3, a.head_y + 2, a.head_front - 1), (2, 2, 1), key="eye_left")
    rig.cube("head", (1, a.head_y + 2, a.head_front - 1), (2, 2, 1), key="eye_right")
    rig.cube("head", (-0.5, a.head_y + 1, a.head_front - 3), (1, 1, 3), key="nose")

    def build_ear(side, sign):
        # Bat ears: broad, flat, set high and swept out almost level — wider than the head.
        rig.bone(f"ear_{side}", "head", (sign * 3, a.head_y + 4, a.head_front + 2.5), (0, sign * 12, sign * -8))
        rig.cube(f"ear_{side}", Rig.mirror((3, a.head_y + 2, a.head_front + 2), (6, 4, 1), sign), (6, 4, 1))

    rig.pair(build_ear)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.06, dither_colour=SKIN_DARK)
    skin.skin("pillowcase", CLOTH, dither=0.10, dither_colour=CLOTH_DARK, grain="strand")
    skin.tip("pillowcase", CLOTH_DARK, rows=1)
    for key in ("ear_left", "ear_right"):
        for face in ("north", "south"):
            x, y, w, h = skin.face(key, face)
            skin.rect((x + 1, y + 1, max(1, w - 2), max(1, h - 2)), EAR_INNER)
    for key in ("eye_left", "eye_right"):
        skin.skin(key, EYE, bevel=0, dither=0.0)
        x, y, w, h = skin.face(key, "north")
        skin.d.point((x + (0 if key == "eye_left" else w - 1), y + h - 1), fill=EYE_DARK)
    skin.skin("nose", SKIN_, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    skin.d.line([(x + 2, y + h - 2), (x + w - 3, y + h - 2)], fill=MOUTH)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.0, walk_len=0.55, stride=34.0)
    # The ears are the elf's whole emotional range: they droop and lift a beat behind the head.
    for side, sign in (("left", 1), ("right", -1)):
        idle[f"ear_{side}"] = {"rotation": kf((0.0, (0, 0, 0)), (1.5, (0, 0, sign * -10)), (3.0, (0, 0, 0)))}
        walk[f"ear_{side}"] = {"rotation": kf((0.0, (0, 0, 0)), (0.275, (0, 0, sign * 8)), (0.55, (0, 0, 0)))}
    attack = {"arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (-100, 0, 0)), (0.3, (10, 0, 0)),
                                           (0.5, (0, 0, 0)))}}
    return {"idle": clip(3.0, idle), "walk": clip(0.55, walk), "attack": clip(0.5, attack, loop=False)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    rig, a = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h, a), anims(a), cid=CID, tool=TOOL, hitbox=HITBOX,
                       tex_h=tex_h, force=args.force, definition=False)


if __name__ == "__main__":
    sys.exit(main())
