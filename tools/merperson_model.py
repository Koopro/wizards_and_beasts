#!/usr/bin/env python3
"""The Merperson: rig, skin and animation — and the rig the `merfolk_water` player form draws.

Canon (Goblet of Fire, the Black Lake): grey-skinned, long wild dark-green hair, yellow eyes and
broken yellow teeth, with thick silver fish tails; they carry spears and sing, and their song is
only intelligible under water. `ranged_hex`, AMPHIBIOUS.

The lake merpeople are not the pretty sort, and the rig follows the book: a wiry grey torso, hair
hanging in ropes, a spear, and a heavy tail curling down and back to a broad fluke that the body
rests on. The `song` clip rides the ambient beat.

**Second consumer.** `PlayerFormRig` draws this rig for the `merfolk_water` form, scaled by the
`merpeople_water` size profile's `modelScale`, which must equal hitbox height over this rig's
authored height (`FormRigScaleTest`). This generator prints the value to set.

Run from the repo root:  python tools/merperson_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, hang  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "merperson"
HITBOX = (0.95, 1.25)
FORM_HITBOX_HEIGHT = 1.62

SKIN_ = hx("#8A968E")
SKIN_DARK = hx("#646E68")
HAIR = hx("#2A4A30")
HAIR_DARK = hx("#18301E")
TAIL = hx("#9AAAA8")
TAIL_DARK = hx("#6A7A7A")
FIN = hx("#C8D4D0")
WOOD = hx("#6A5A3A")
TIP = hx("#B0B8BC")
EYE = hx("#E8D23A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 12, 0))
    rig.cube("body", (-3, 12, -2), (6, 7, 4), key="torso")
    rig.bone("head", "body", (0, 19, 0))
    rig.cube("head", (-2.5, 19, -2.5), (5, 5, 5), key="head")
    rig.cube("head", (-3, 22, -2), (6, 3, 5), key="hair_top", inflate=0.2)
    rig.cube("head", (-3, 14, 2), (6, 9, 2), key="hair_back")
    rig.pair(lambda side, sign: hang(rig, f"arm_{side}", "body", x=4, z=0, sign=sign, top=18,
                                     segments=[("", 4, 2, 2, (0, 0, -10)), ("fore", 4, 2, 2, (-20, 0, 0)),
                                               ("hand", 1, 2, 2, None)]))
    rig.bone("spear", "arm_right_hand", (-4, 9, 0), (-20, 0, 0))
    rig.cube("spear", (-4.5, 2, -0.5), (1, 16, 1), key="spear_shaft")
    rig.cube("spear", (-5, 18, -1), (2, 3, 2), key="spear_tip")
    hang(rig, "tail", "body", x=0, z=0, sign=1, top=12,
         segments=[("", 5, 5, 4, (18, 0, 0)), ("mid", 4, 4, 3, (26, 0, 0)), ("low", 3, 3, 2, (30, 0, 0)),
                   ("fin", 1, 9, 6, (10, 0, 0))])
    ground(rig)
    lo = min(c["origin"][1] for b in rig.bones for c in b.cubes)
    hi = max(c["origin"][1] + c["size"][1] for b in rig.bones for c in b.cubes)
    print(f"  merpeople_water modelScale should be {FORM_HITBOX_HEIGHT / ((hi - lo) / 16.0):.3f} "
          f"(authored height {(hi - lo) / 16.0:.3f} blocks)")
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["torso", "head"] + rig.keys("arm_"), SKIN_, dither=0.08, dither_colour=SKIN_DARK)
    skin.skin(["hair_top", "hair_back"], HAIR, dither=0.0)
    for key in ("hair_back", "hair_top"):
        for face in ("east", "west", "south", "north"):
            x, y, w, h = skin.face(key, face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=HAIR_DARK)
    tail = [k for k in rig.keys("tail") if k != "tail_fin"]
    skin.skin(tail, TAIL, dither=0.0)
    for key in tail:
        for face in ("north", "south", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=TAIL_DARK)
    skin.skin("tail_fin", FIN, dither=0.0)
    skin.bands("tail_fin", TAIL_DARK, faces=("top", "bottom"), step=2)
    skin.skin("spear_shaft", WOOD, dither=0.0)
    skin.skin("spear_tip", TIP, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    skin.d.point((x + 1, y + 2), fill=EYE)
    skin.d.point((x + w - 2, y + 2), fill=EYE)
    skin.hline((x, y, w, h), h - 1, hx("#C8B458"), inset=1)
    return skin


def anims():
    idle = {"tail": osc(3.6, 6.0), "tail_mid": osc(3.6, 8.0, phase=0.15), "tail_low": osc(3.6, 10.0, phase=0.3),
            "body": osc(3.6, 3.0, axis="z"), "head": {"rotation": kf((0.0, (0, -12, 0)), (1.8, (0, 12, 0)), (3.6, (0, -12, 0)))},
            "arm_left": osc(3.6, 6.0, axis="z")}
    swim = {"body": {"rotation": kf((0.0, (60, 0, 0)), (1.0, (60, 0, 0)))},
            "tail": osc(1.0, 18.0), "tail_mid": osc(1.0, 22.0, phase=0.15), "tail_low": osc(1.0, 26.0, phase=0.3),
            "tail_fin": osc(1.0, 20.0, phase=0.45), "arm_left": osc(1.0, 20.0, axis="z"),
            "head": {"rotation": kf((0.0, (-50, 0, 0)), (1.0, (-50, 0, 0)))}}
    attack = {"arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-60, 0, 0)), (0.45, (-100, 0, 0)), (0.8, (0, 0, 0)))},
              "spear": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (60, 0, 0)), (0.45, (80, 0, 0)), (0.8, (0, 0, 0)))},
              "body": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (-10, 20, 0)), (0.45, (14, -10, 0)), (0.8, (0, 0, 0)))}}
    song = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-24, 0, 0)), (2.0, (-20, 0, 0)), (2.4, (0, 0, 0)))},
            "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-60, 0, -40)), (1.8, (-50, 0, -30)), (2.4, (0, 0, 0)))},
            "body": osc(2.4, 6.0, axis="z")}
    return {"idle": clip(3.6, idle), "swim": clip(1.0, swim),
            "attack": clip(0.8, attack, loop=False), "song": clip(2.4, song, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "merperson_model.py", build, paint, anims, HITBOX))
