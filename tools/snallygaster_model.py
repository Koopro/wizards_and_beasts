#!/usr/bin/env python3
"""The Snallygaster: rig, skin and animation.

Canon: a part-bird, part-serpent native to Maryland, which MACUSA has spent a great deal of effort
keeping out of the Muggle newspapers; its beak is lined with serrated steel-like teeth and its
hide deflects bullets. FIRE_ATTACK, CHARGE, `leap`.

Bird in front, serpent behind: a beaked, crested head on a long neck, feathered wings, and a
scaled body that runs back into a long tapering tail. The teeth are the one detail that must read
up close — a row of metal-grey points along the beak's edge.

Run from the repo root:  python tools/snallygaster_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, quadruped, quadruped_loops, wing_beat, wing_pair  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "snallygaster"
HITBOX = (1.7, 1.9)

SCALE = hx("#4E6A5A")
SCALE_DARK = hx("#34483C")
BELLY = hx("#A8B08A")
FEATHER = hx("#6A4E7A")
FEATHER_DARK = hx("#46325A")
BEAK = hx("#C8B478")
STEEL = hx("#B8C0C8")
EYE = hx("#F0A030")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=9,
        chest=(10, 9, 7), barrel=(9, 8, 12), croup=(8, 7, 6), croup_drop=-6,
        neck=(5, 12, 5), neck_rake=30, head=(6, 5, 8), head_pitch=10, muzzle=None,
        fore=[("", 5, 3, 3, None), ("claw", 4, 3, 4, None)],
        hind=[("", 5, 4, 4, (10, 0, 0)), ("claw", 4, 3, 4, (-10, 0, 0))],
        fore_x=4, hind_x=4)
    rig.bone("beak", "head", (0, a.head_y + 3, a.head_front))
    rig.cube("beak", (-2, a.head_y + 2, a.head_front - 6), (4, 3, 6), key="beak_top")
    rig.cube("beak", (-1.5, a.head_y, a.head_front - 5), (3, 2, 5), key="beak_jaw")
    rig.cube("head", (-1, a.head_top, a.head_front + 2), (2, 4, 6), key="crest")
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 4, a.back - 1), direction=1,
                      segments=[(8, 5, 5), (8, 4, 3), (7, 3, 2), (6, 2, 1)],
                      rotations=[(-12, 0, 0), (-6, 0, 0), (4, 0, 0), (6, 0, 0)])
    a["wings"] = wing_pair(rig, "body", x=4.5, y=a.top + 1, z=a.front + 3,
                           root=(12, 9, 2), tip=(11, 8, 1), primaries=3)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["chest", "barrel", "croup", "neck", "head"] + rig.keys("foreleg", "hindleg", "tail")
    skin.skin(body, SCALE, bottom=BELLY, dither=0.0)
    for key in body:
        for face in ("top", "east", "west"):
            x, y, w, h = skin.face(key, face)
            for row in range(0, h, 2):
                for col in range(row // 2 % 2, w, 2):
                    skin.d.point((x + col, y + row), fill=SCALE_DARK)
    wings = rig.keys("wing_")
    skin.skin(wings + ["crest"], FEATHER, dither=0.0)
    skin.bands(wings + ["crest"], FEATHER_DARK, faces=("east", "west"), step=3)
    skin.skin(["beak_top", "beak_jaw"], BEAK, dither=0.0)
    for key in ("beak_top", "beak_jaw"):
        for face in ("east", "west"):
            x, y, w, h = skin.face(key, face)
            row = h - 1 if key == "beak_top" else 0
            for col in range(0, w, 2):
                skin.d.point((x + col, y + row), fill=STEEL)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, EYE)
    return skin


def anims(a):
    idle, _walk, _legs = quadruped_loops(a, idle_len=3.6)
    fly = {
        **wing_beat(0.9, spread=80.0, amp=42.0),
        "foreleg_left": {"rotation": kf((0.0, (50, 0, 0)), (0.9, (50, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (50, 0, 0)), (0.9, (50, 0, 0)))},
        "hindleg_left": {"rotation": kf((0.0, (40, 0, 0)), (0.9, (40, 0, 0)))},
        "hindleg_right": {"rotation": kf((0.0, (40, 0, 0)), (0.9, (40, 0, 0)))},
        **{name: {"rotation": kf((0.0, (0, 8 + 4 * i, 0)), (0.45, (0, -8 - 4 * i, 0)),
                                 (0.9, (0, 8 + 4 * i, 0)))} for i, name in enumerate(a.tail)},
    }
    strike = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-24, 0, 0)), (0.45, (30, 0, 0)), (0.8, (0, 0, 0)))},
        "beak": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.45, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.45, (0, 1, -3)), (0.8, (0, 0, 0)))},
    }
    hiss = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.0, (-16, 0, 0)), (1.3, (0, 0, 0)))},
        **{w: {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 50 if w.endswith("left") else -50)),
                              (1.3, (0, 0, 0)))} for w in a.wings},
    }
    return {"idle": clip(3.6, idle), "fly": clip(0.9, fly),
            "strike": clip(0.8, strike, loop=False), "hiss": clip(1.3, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "snallygaster_model.py", build, paint, anims, HITBOX))
