#!/usr/bin/env python3
"""The Giant: rig, skin and animation.

Canon: about twenty feet tall, brutal, living in mountain clans that dwindled as they killed one
another; Hagrid's half-brother Grawp is a small one at sixteen. Crude hide clothing, rocks and
tree limbs for weapons. 104 health, KNOCKBACK, PACK, `ranged_hex` (thrown), `spell_resist`.

Box-limited to 2.9 blocks, so the size has to come from proportion rather than height: a small
head on massive shoulders, hands the size of the head, a hide cloak and a rope belt, legs that are
short and thick. Next to the Troll (grey, hunched, club-dragging) the Giant is upright, skin-
coloured and dressed — a person, just an enormous and stupid one.

Run from the repo root:  python tools/giant_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import biped, biped_loops, ground  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "giant"
HITBOX = (2.7, 2.9)

SKIN_ = hx("#B48A6A")
SKIN_DARK = hx("#8A664C")
HIDE = hx("#6E5A44")
HIDE_DARK = hx("#4C3E2E")
FUR = hx("#8C8478")
ROPE = hx("#B8A078")
HAIR = hx("#4A3624")
EYE = hx("#1E1812")


def build():
    rig = Rig(CID, 128)
    a = biped(rig, hip=16, pelvis=(18, 8, 11), torso=(22, 15, 12), head=(11, 11, 11), hunch=6,
              legs=[("", 8, 8, 8, None), ("shin", 6, 7, 7, None), ("foot", 2, 9, 11, None)],
              arms=[("", 11, 7, 7, (0, 0, -6)), ("fore", 10, 6, 6, (-8, 0, 0)),
                    ("hand", 4, 7, 7, None)],
              arm_x=14, leg_x=5)
    rig.cube("chest", (-13, a.shoulder - 5, -7), (26, 6, 14), key="cloak", inflate=0.5)
    rig.cube("body", (-9.5, a.pelvis_y + 2, -6), (19, 2, 12), key="belt", inflate=0.3)
    rig.cube("body", (-9, a.pelvis_y - 5, -6), (18, 7, 12), key="kilt", inflate=0.4)
    rig.cube("head", (-6, a.head_top - 3, a.head_front + 2), (12, 4, 10), key="hair", inflate=0.3)
    rig.cube("head", (-1.5, a.head_y + 3, a.head_front - 2), (3, 3, 2), key="nose")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), SKIN_, dither=0.06, dither_colour=SKIN_DARK)
    skin.skin(["torso", "kilt", "pelvis"], HIDE, dither=0.12, dither_colour=HIDE_DARK)
    skin.bands("kilt", HIDE_DARK, faces=("north", "south", "east", "west"), step=4)
    skin.skin("cloak", FUR, dither=0.3, dither_colour=HIDE_DARK, grain="strand")
    skin.skin("belt", ROPE, dither=0.0)
    skin.bands("belt", HIDE_DARK, faces=("north", "south", "east", "west"), step=2)
    skin.skin("hair", HAIR, dither=0.15, dither_colour=HIDE_DARK)
    skin.eyes("head", EYE, row=4, inset=2, width=2)
    x, y, w, h = skin.face("head", "north")
    skin.hline((x, y, w, h), 3, SKIN_DARK, inset=1)      # heavy brow
    skin.hline((x, y, w, h), h - 3, SKIN_DARK, inset=3)  # mouth
    skin.skin([k for k in rig.keys("leg_") if "foot" in k], HIDE_DARK, dither=0.0)
    return skin


def anims(a):
    idle, walk = biped_loops(a.rig, a, idle_len=5.0, walk_len=1.6, stride=22.0, bob_amp=1.2)
    attack = {
        "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-170, 0, 10)), (0.75, (-170, 0, 10)),
                                     (1.0, (-20, 0, 0)), (1.5, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-170, 0, -10)), (0.75, (-170, 0, -10)),
                                    (1.0, (-20, 0, 0)), (1.5, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-14, 0, 0)), (1.0, (22, 0, 0)),
                                 (1.5, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (1.0, (0, -2, -2)), (1.5, (0, 0, 0)))},
    }
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.7, (-20, 10, 0)), (1.8, (-16, -8, 0)),
                                (2.4, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.8, (-40, 0, -10)), (1.6, (-20, 0, 0)),
                                    (2.4, (0, 0, 0)))},
    }
    return {"idle": clip(5.0, idle), "walk": clip(1.6, walk),
            "attack": clip(1.5, attack, loop=False), "groan": clip(2.4, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "giant_model.py", build, paint, anims, HITBOX))
