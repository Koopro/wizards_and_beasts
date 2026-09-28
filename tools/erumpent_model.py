#!/usr/bin/env python3
"""The Erumpent: rig, skin and animation.

Canon: a huge grey African beast, rhinoceros-like, with a thick hide that repels most charms and
curses, a thin rope tail, and a single great horn that can pierce anything from flesh to metal
and holds a fluid that makes whatever it is injected into explode. EXPLODE_ON_DEATH and
`explosive_horn` here, so the horn is the one thing on the animal that is not grey: pale bone
running to a hot orange tip, the colour of the fuse.

Low and heavy — the belly sits at knee height of a player — with the head carried below the
shoulder line, because a charging rhino leads with the horn, not the face.

Run from the repo root:  python tools/erumpent_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "erumpent"
HITBOX = (1.7, 1.9)

HIDE = hx("#7F7B74")
HIDE_DARK = hx("#5B5852")
HIDE_LIT = hx("#99958D")
HORN = hx("#E6DCC4")
FUSE = hx("#E8893A")
FUSE_HOT = hx("#F4C45A")
EYE = hx("#1C1A18")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=11,
        chest=(18, 15, 10), barrel=(20, 16, 14), croup=(18, 14, 9), croup_drop=-3,
        neck=None, neck_rake=0, head=(10, 9, 12), head_pitch=12, head_y=13, muzzle=(8, 6, 5),
        fore=[("", 5, 6, 6, None), ("lower", 4, 5, 5, None), ("foot", 2, 7, 7, None)],
        hind=[("", 5, 7, 7, (8, 0, 0)), ("lower", 4, 5, 5, (-8, 0, 0)), ("foot", 2, 7, 7, None)],
        fore_x=6, hind_x=6, tail=None)

    # The horn: base, shaft and tip on one bone, leaning forward off the end of the muzzle. It
    # is its own bone so the attack clip can toss it without moving the whole skull.
    front = a.muzzle_front
    rig.bone("horn", "head", (0, a.head_y + 6, front + 2), (22, 0, 0))
    rig.cube("horn", (-2, a.head_y + 6, front), (4, 5, 4), key="horn_base")
    rig.cube("horn", (-1.5, a.head_y + 11, front + 0.5), (3, 5, 3), key="horn_shaft")
    rig.cube("horn", (-1, a.head_y + 16, front + 1), (2, 4, 2), key="horn_tip")
    rig.cube("head", (-1.5, a.head_y + 9, a.head_front + 2), (3, 3, 3), key="horn_second")

    rig.pair(lambda side, sign: rig.cube(
        "head", Rig.mirror((4, a.head_y + 8, a.head_back - 3), (2, 3, 2), sign), (2, 3, 2),
        key=f"ear_{side}"))

    # Rope tail with a brush of hair — thin on purpose, against that much body.
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 11, a.back - 1), direction=1,
                      segments=[(7, 2, 2), (6, 1, 1), (3, 3, 3)],
                      rotations=[(-50, 0, 0), (-10, 0, 0), (0, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["chest", "barrel", "croup", "head", "muzzle"]
    skin.skin(body, HIDE, top=HIDE_LIT, dither=0.08, dither_colour=HIDE_DARK)
    skin.skin(rig.keys("foreleg", "hindleg"), HIDE, dither=0.08, dither_colour=HIDE_DARK)
    skin.skin([k for k in rig.keys("foreleg", "hindleg") if "foot" in k], HIDE_DARK, dither=0.0)
    skin.skin(rig.keys("ear_", "tail"), HIDE_DARK, dither=0.0)

    # Hide folds: dark rows across the shoulder and haunch where a rhino's armour creases.
    skin.bands(["chest", "croup"], HIDE_DARK, faces=("east", "west"), step=5, offset=2)
    for face in ("east", "west"):
        x, y, w, h = skin.face("barrel", face)
        skin.vline((x, y, w, h), 1, HIDE_DARK)
        skin.vline((x, y, w, h), w - 2, HIDE_DARK)

    skin.skin(["horn_base", "horn_shaft", "horn_tip", "horn_second"], HORN, dither=0.0)
    skin.ramp(["horn_tip"], FUSE, FUSE_HOT)
    skin.rect(skin.face("horn_tip", "top"), FUSE_HOT)
    skin.glow("horn_tip")   # the fuse: the explosive fluid shows through the horn
    skin.tip("horn_shaft", FUSE, rows=1)

    for face in ("east", "west"):
        skin.mark("head", face, 2, 2, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=5.0, walk_len=1.2, stride=18.0, bob_amp=0.6)
    walk.update(knee_tracks(a.rig, legs, 1.2, 10.0))
    idle["horn"] = {"rotation": kf((0.0, (0, 0, 0)), (2.5, (-4, 0, 0)), (5.0, (0, 0, 0)))}

    # `attack` is the gore: head down, lunge, and the horn tossed up through the target.
    attack = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (22, 0, 0)), (0.55, (-30, 0, 0)),
                                (1.0, (0, 0, 0)))},
        "horn": {"rotation": kf((0.0, (0, 0, 0)), (0.55, (-14, 0, 0)), (1.0, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (6, 0, 0)), (0.55, (-6, 0, 0)),
                                (1.0, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 1.5)), (0.55, (0, 0.5, -3)),
                                (1.0, (0, 0, 0)))},
    }
    # `groan` on the ambient beat: a snort, head shaking side to side.
    groan = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-8, 14, 0)), (0.6, (-8, -14, 0)),
                                (0.9, (-6, 10, 0)), (1.4, (0, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 24)), (0.9, (0, 0, -24)),
                                  (1.4, (0, 0, 0)))},
    }
    return {"idle": clip(5.0, idle), "walk": clip(1.2, walk),
            "attack": clip(1.0, attack, loop=False), "groan": clip(1.4, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "erumpent_model.py", build, paint, anims, HITBOX))
