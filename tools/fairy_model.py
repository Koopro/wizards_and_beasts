#!/usr/bin/env python3
"""The Fairy: rig, skin and animation.

Canon: a small, decorative, not very intelligent humanoid of one to five inches, with large
insect-like wings that may be transparent or multicoloured; wizards use them as living
decorations (the Hogwarts Christmas trees, Slughorn's party). It glows. FEARFUL,
`bioluminescence`.

The doxy's mirror: two limbs where it has four, a pale body where it has black hair, and two
pairs of big gossamer wings instead of hard cases — and the wings and hair are lit in the
glowmask, because a fairy is something you see by its own light.

Run from the repo root:  python tools/fairy_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from insect import buzz, hover, wings  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "fairy"
HITBOX = (0.45, 0.45)

SKIN_ = hx("#F4E8F0")
HAIR = hx("#F8E48A")
WING_A = hx("#B8E8F8")
WING_B = hx("#F0B8E8")
EYE = hx("#3A6AB8")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 7, 0))
    rig.cube("body", (-1, 4, -1), (2, 3, 2), key="torso")
    rig.cube("body", (-1.5, 3, -1.5), (3, 2, 3), key="skirt")
    rig.bone("head", "body", (0, 7, 0))
    rig.cube("head", (-1.5, 7, -1.5), (3, 3, 3), key="head")
    rig.cube("head", (-2, 9, -1), (4, 2, 3), key="hair", inflate=0.1)
    for side, sign in (("left", 1), ("right", -1)):
        rig.bone(f"arm_{side}", "body", (sign * 1, 7, 0), (0, 0, sign * -30))
        rig.cube(f"arm_{side}", Rig.mirror((1, 4, -0.5), (1, 3, 1), sign), (1, 3, 1))
        rig.bone(f"leg_{side}", "body", (sign * 0.5, 3, 0), (15, 0, 0))
        rig.cube(f"leg_{side}", Rig.mirror((0, 0, -0.5), (1, 3, 1), sign), (1, 3, 1))
    wings(rig, "body", x=0.5, y=6, z=1.5, size=(5, 1, 5), rest=55, yaw=25, name="wing")
    wings(rig, "body", x=0.5, y=5, z=2, size=(4, 1, 3), rest=25, yaw=45, name="wing_low")
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["torso", "head"] + rig.keys("arm_", "leg_"), SKIN_, dither=0.0)
    skin.skin("skirt", WING_B, dither=0.0)
    skin.skin("hair", HAIR, dither=0.0)
    skin.skin(rig.keys("wing_"), WING_A, dither=0.0)
    skin.ramp(rig.keys("wing_"), WING_A, WING_B, faces=("top", "bottom"))
    skin.eyes("head", EYE, row=1, inset=0)
    # The wings and the hair carry the light; the face and body stay lit by the world so the
    # fairy keeps a shape at night instead of flattening into one pastel cut-out (the audit,
    # §3.8: the whole skin used to glow).
    skin.glow(rig.keys("wing_", "hair"))
    return skin


def anims():
    b = 0.12
    idle = {**buzz(b, amp=26), **buzz(b, amp=20, name="wing_low", phase_shift=True), "root": hover(b * 10, 1.0)}
    fly = {**buzz(b, amp=32), **buzz(b, amp=24, name="wing_low", phase_shift=True), "root": hover(b * 5, 0.5),
           "body": {"rotation": kf((0.0, (18, 0, 0)), (b * 5, (18, 0, 0)))}}
    flinch = {**buzz(b, amp=26), "root": {"position": kf((0.0, (0, 0, 0)), (0.08, (0, 1.5, 1)), (0.36, (0, 0, 0)))}}
    song = {**buzz(b, amp=26), "body": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (0, 360, 0)), (1.2, (0, 720, 0)))},
            "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 80)), (1.2, (0, 0, 0)))},
            "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -80)), (1.2, (0, 0, 0)))}}
    return {"idle": clip(b * 10, idle), "fly": clip(b * 5, fly),
            "flinch": clip(b * 3, flinch, loop=False), "song": clip(1.2, song, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "fairy_model.py", build, paint, anims, HITBOX))
