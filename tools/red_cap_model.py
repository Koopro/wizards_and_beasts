#!/usr/bin/env python3
"""The Red Cap: rig, skin and animation.

Canon: a small dwarf-like creature living in holes on old battlegrounds and anywhere human blood
has been spilled; it bludgeons people who get lost at night. The folklore behind it dips the cap
in its victims' blood, which is where the name and the colour come from. Hostile, `enrage`.

A stout, broad-shouldered dwarf with a grey beard, a club, and the cap: the brightest and most
saturated thing on the model by a distance, a pointed red cone that reads at any range. Its eyes
glow the same red, so in the dark it is two points and a hat.

Run from the repo root:  python tools/red_cap_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "red_cap"
HITBOX = (0.65, 0.75)

SKIN_ = hx("#B08A70")
BEARD = hx("#8C8A84")
BEARD_DARK = hx("#646260")
CAP = hx("#B8201C")
CAP_DARK = hx("#7E1412")
JERKIN = hx("#4E3E2A")
JERKIN_DARK = hx("#34281A")
WOOD = hx("#6A4A2C")
EYE = hx("#F03A2A")
BOOT = hx("#2A2018")


def build():
    rig = Rig(CID, 64)
    a = biped(rig, hip=4, pelvis=(6, 3, 4), torso=(7, 6, 5), head=(6, 6, 5),
              legs=[("", 2, 3, 3, None), ("foot", 2, 3, 4, None)],
              arms=[("", 3, 3, 3, (0, 0, -10)), ("fore", 3, 2, 2, (-10, 0, 0)),
                    ("hand", 1, 3, 3, None)],
              arm_x=5, leg_x=1.5)
    rig.cube("head", (-3, a.head_y - 3, a.head_front - 1), (6, 5, 2), key="beard")
    rig.bone("cap", "head", (0, a.head_top, 0), (-10, 0, 0))
    rig.cube("cap", (-3.5, a.head_top - 1, a.head_front - 0.5), (7, 3, 6), key="cap_band")
    rig.cube("cap", (-2.5, a.head_top + 2, a.head_front + 0.5), (5, 3, 5), key="cap_mid")
    rig.cube("cap", (-1, a.head_top + 5, a.head_front + 2), (2, 3, 3), key="cap_tip")
    # The club is held up over the shoulder, head behind, rather than hanging: hung from a hand
    # at knee height it reached below the feet and `ground()` lifted the whole dwarf off the floor.
    rig.bone("club", "arm_right_hand", (-5, a.shoulder - 6, 0), (0, 0, 0))
    rig.cube("club", (-6, a.shoulder - 6, -1), (2, 8, 2), key="club_shaft")
    rig.cube("club", (-7, a.shoulder + 2, -2), (4, 4, 4), key="club_head")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), JERKIN, dither=0.1, dither_colour=JERKIN_DARK)
    skin.skin(["head"] + [k for k in rig.keys("arm_") if "hand" in k], SKIN_, dither=0.0)
    skin.eyes("head", EYE, row=2, inset=1, glow=True)
    skin.skin("beard", BEARD, dither=0.25, dither_colour=BEARD_DARK)
    skin.skin(["cap_band", "cap_mid", "cap_tip"], CAP, dither=0.12, dither_colour=CAP_DARK)
    skin.tip("cap_band", CAP_DARK, rows=1)
    skin.skin([k for k in rig.keys("leg_") if "foot" in k], BOOT, dither=0.0)
    skin.skin(["club_shaft", "club_head"], WOOD, dither=0.1, dither_colour=JERKIN_DARK)
    skin.dither(skin.face("club_head", "top"), CAP_DARK, 0.3, 4, grain="fleck")
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=3.0, walk_len=0.5, stride=34.0, bob_amp=0.4)
    walk["body"] = {"rotation": kf((0.0, (0, 0, 5)), (0.25, (0, 0, -5)), (0.5, (0, 0, 5)))}
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-160, 0, 0)), (0.4, (20, 0, 0)),
                                     (0.7, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.4, (16, 0, 0)),
                                 (0.7, (0, 0, 0)))},
    }
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (10, 0, 0)), (0.9, (10, 0, 0)),
                                (1.2, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-6, 0, 0)), (1.2, (0, 0, 0)))},
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-30, 0, 0)), (0.8, (-10, 0, 0)),
                                     (1.2, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.5, walk),
            "attack": clip(0.7, attack, loop=False), "groan": clip(1.2, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "red_cap_model.py", build, paint, anims, HITBOX))
