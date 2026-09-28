#!/usr/bin/env python3
"""The Veela in fury: rig, skin and animation for the `veela_harpy` player heritage form.

Until 2026-09-27 this form had no rig: `FormModelRenderer` drew the werewolf's legacy box model
with a texture that did not exist, at full brightness (visual consistency audit, C2).

Canon (*Goblet of Fire* ch. 9): angry, the Veela's faces "were elongating into sharp, cruel-beaked
bird heads, and long, scaly wings were bursting from their shoulders", and they throw balls of
fire. So the read is a tall, slender woman's frame (the human form is 1.8 blocks, the fury 1.98)
with a bird's head and beak, a long mane of silver-blonde hair kept from the human form — the one
thing that says "Veela" rather than "harpy" — and dark scaly wings that fold down the back.

Mob-less: nothing spawns a Veela, so there is no definition and no shared hit/death clips.
`PlayerFormRig` asks for `idle`, `walk`, `attack` and `hit`.

Run from the repo root:  python tools/veela_harpy_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground, wing_beat, wing_pair  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "veela_harpy"
TOOL = "veela_harpy_model.py"
HITBOX = (0.66, 1.98)

SKIN_ = hx("#D9C7B6")
SKIN_DARK = hx("#B09A88")
SCALE = hx("#5C5A66")
SCALE_DARK = hx("#3A3844")
HAIR = hx("#E8E2C8")
HAIR_DARK = hx("#BDB594")
BEAK = hx("#C8A24A")
BEAK_DARK = hx("#8A6A26")
GOWN = hx("#D8DCE6")
GOWN_DARK = hx("#A6AEC2")
EYE = hx("#F2C23A")
TALON = hx("#2E2A28")
WING_PITCH = -62


def build():
    rig = Rig(CID, 128)
    a = biped(rig, hip=14, pelvis=(7, 3, 4), torso=(7, 10, 4), head=(6, 6, 6), neck=2,
              legs=[("", 7, 3, 3, None), ("shin", 6, 2, 2, None), ("foot", 1, 3, 4, None)],
              arms=[("", 6, 2, 2, (0, 0, -6)), ("fore", 5, 2, 2, (-10, 0, 0)),
                    ("hand", 2, 2, 2, None)],
              arm_x=4.5, leg_x=2)
    # Gown from the waist to the shins: the slender human half of her.
    rig.cube("body", (-4, 5, -2.5), (8, 9, 5), key="gown", inflate=0.2)
    # The bird head: a hooked beak off the face and a brow over the eyes.
    rig.cube("head", (-1, a.head_y + 1, a.head_front - 4), (2, 2, 4), key="beak")
    rig.cube("head", (-1, a.head_y - 1, a.head_front - 4), (2, 2, 1), key="beak_hook")
    rig.cube("head", (-3.5, a.head_y + 4, a.head_front - 1), (7, 1, 2), key="brow")
    # Silver-blonde hair: the one feature carried over from the human form.
    rig.cube("head", (-3.5, a.head_y - 6, a.head_back - 1), (7, 12, 2), key="hair")
    a["wings"] = wing_pair(rig, "chest", x=2.5, y=a.shoulder + 3, z=a.torso_back,
                           root=(10, 12, 1), tip=(12, 10, 1), rest_roll=-6, tip_yaw=-6)
    # `wing_pair` is built for a quadruped's flank, where the slab runs back along the body. On
    # an upright body that is a board sticking out behind her, so the roots are pitched down to
    # hang the folded wings along the back instead.
    for bone in rig.bones:
        if bone.name in a["wings"]:
            _, y_rot, z_rot = bone.rotation
            bone.rotation = (WING_PITCH, y_rot, z_rot)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.04, dither_colour=SKIN_DARK)
    skin.skin(["gown", "torso", "pelvis"], GOWN, dither=0.08, dither_colour=GOWN_DARK, grain="strand")
    skin.tip("gown", GOWN_DARK, rows=1)
    skin.skin("hair", HAIR, dither=0.0)
    skin.bands("hair", HAIR_DARK, step=3, faces=("north", "south", "east", "west"))
    wings = rig.keys("wing_")
    skin.skin(wings, SCALE, dither=0.20, dither_colour=SCALE_DARK)
    skin.ramp(wings, SCALE, SCALE_DARK, faces=("east", "west"), steps=3)
    skin.skin(["beak", "beak_hook"], BEAK, dither=0.0)
    skin.tip("beak", BEAK_DARK, rows=1)
    skin.skin("brow", SCALE, dither=0.0)
    skin.skin(rig.keys("arm_left_hand", "arm_right_hand"), TALON, dither=0.0)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 2, EYE)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.6, walk_len=0.9, stride=28.0)
    for side, sign in (("left", 1), ("right", -1)):
        idle[f"wing_{side}"] = {"rotation": kf((0.0, (0, 0, 0)), (1.8, (0, 0, sign * 6)), (3.6, (0, 0, 0)))}
    # Attack is the fireball: wings flare, the right arm hurls.
    attack = {
        **wing_beat(0.6, spread=50.0, amp=12.0),
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-150, 0, 10)), (0.4, (-40, 0, 0)), (0.6, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-15, 0, 0)), (0.6, (0, 0, 0)))},
    }
    hit = {
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-10, 0, 0)), (0.4, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 0)), (0.4, (0, 0, 0)))},
        **{f"wing_{s}": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (0, 0, g * 40)), (0.4, (0, 0, 0)))}
           for s, g in (("left", 1), ("right", -1))},
    }
    return {"idle": clip(3.6, idle), "walk": clip(0.9, walk),
            "attack": clip(0.6, attack, loop=False), "hit": clip(0.4, hit, loop=False)}


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
