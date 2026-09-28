#!/usr/bin/env python3
"""The Re'em: rig, skin and animation.

Canon: an extremely rare giant oxen with a golden hide; its blood gives immense strength. 80
health, CHARGE and KNOCKBACK, and a 2.7 x 2.9 box — an ox the height of a player standing on
another player.

What reads as *ox* at that size is the mass over the forelegs: a high shoulder hump, a deep
dewlap swinging under the throat, a broad low head carried on no visible neck, and horns that
grow sideways before they turn up. The gold is the rarity; everything else is bull.

Run from the repo root:  python tools/reem_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "reem"
HITBOX = (2.7, 2.9)

GOLD = hx("#D4A63A")
GOLD_DARK = hx("#9C7424")
GOLD_LIT = hx("#EECB6A")
HORN = hx("#EFE4C6")
HORN_DARK = hx("#B9A882")
HOOF = hx("#5A4526")
EYE = hx("#2A1C0E")
NOSE = hx("#6E4E2A")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=18,
        chest=(24, 22, 14), barrel=(22, 20, 18), croup=(20, 18, 11), croup_drop=-4,
        neck=None, neck_rake=0, head=(14, 12, 14), head_pitch=14, head_y=24, muzzle=(10, 8, 4),
        fore=[("", 10, 8, 8, None), ("lower", 6, 7, 7, None), ("hoof", 2, 8, 9, None)],
        hind=[("", 10, 9, 10, (10, 0, 0)), ("lower", 6, 7, 7, (-12, 0, 0)), ("hoof", 2, 8, 9, None)],
        fore_x=8, hind_x=8)

    rig.cube("body", (-9, a.top, a.front + 1), (18, 6, 12), key="hump")
    rig.cube("head", (-5, a.head_y - 6, a.head_front + 5), (10, 6, 9), key="dewlap")

    # Horns: out sideways from the poll, then up. Two bones each so the curve is real geometry.
    def build_horn(side, sign):
        hy, hb = a.head_y + 9, a.head_back - 5
        rig.bone(f"horn_{side}", "head", (sign * 6, hy, hb), (0, 0, sign * -80))
        rig.cube(f"horn_{side}", Rig.mirror((5, hy - 2, hb - 2), (4, 8, 4), sign), (4, 8, 4))
        rig.bone(f"horn_{side}_tip", f"horn_{side}", (sign * 7, hy + 6, hb), (0, 0, sign * 70))
        rig.cube(f"horn_{side}_tip", Rig.mirror((6, hy + 6, hb - 1.5), (3, 8, 3), sign), (3, 8, 3))

    rig.pair(build_horn)

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 16, a.back - 1), direction=1,
                      segments=[(10, 3, 3), (8, 2, 2), (5, 4, 4)],
                      rotations=[(-70, 0, 0), (-10, 0, 0), (0, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "hump", "head", "muzzle", "dewlap"]
    skin.skin(body, GOLD, top=GOLD_LIT, dither=0.07, dither_colour=GOLD_DARK)
    skin.skin(legs, GOLD, dither=0.06, dither_colour=GOLD_DARK)
    skin.skin([k for k in legs if "hoof" in k], HOOF, dither=0.0)
    skin.skin(rig.keys("tail"), GOLD_DARK, dither=0.0)
    skin.bands(["hump", "chest"], GOLD_LIT, faces=("east", "west", "top"), step=4)
    # Darker lower legs, the way a real ox's socks go.
    for key in [k for k in legs if "lower" in k]:
        skin.skin(key, GOLD_DARK, dither=0.05, dither_colour=HOOF)

    horns = rig.keys("horn_")
    skin.skin(horns, HORN, dither=0.0)
    skin.tip([k for k in horns if "tip" in k], HORN_DARK, rows=2)

    skin.eyes("head", EYE, row=3, inset=1, width=2)
    x, y, w, h = skin.face("muzzle", "north")
    skin.rect((x + 1, y + h - 4, w - 2, 3), NOSE)
    skin.mark("muzzle", "north", 2, -3, EYE)
    skin.mark("muzzle", "north", -3, -3, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=5.5, walk_len=1.5, stride=16.0, bob_amp=1.0)
    walk.update(knee_tracks(a.rig, legs, 1.5, 10.0))
    attack = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (26, 0, 0)), (0.7, (-26, 12, 0)),
                                (1.3, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (5, 0, 0)), (0.7, (-5, 0, 0)),
                                (1.3, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.4, (0, -1, 2)), (0.7, (0, 1, -4)),
                                (1.3, (0, 0, 0)))},
    }
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-18, 0, 0)), (1.6, (-14, 0, 0)),
                                (2.2, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (0, 0, 26)), (1.1, (0, 0, -26)),
                                  (1.7, (0, 0, 18)), (2.2, (0, 0, 0)))},
    }
    return {"idle": clip(5.5, idle), "walk": clip(1.5, walk),
            "attack": clip(1.3, attack, loop=False), "groan": clip(2.2, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "reem_model.py", build, paint, anims, HITBOX))
