#!/usr/bin/env python3
"""The Graphorn: rig, skin and animation.

Canon: a large, humpbacked beast with greyish-purple skin tougher than a dragon's, two very long
sharp golden horns, and huge four-thumbed feet. Mountain trolls ride them, and they are
aggressive to everything else — CHARGE, KNOCKBACK, `spell_resist`.

The hump is what separates it from every other big grey quadruped: the shoulders stand a head
higher than the hips and the head hangs forward below them on a short thick neck, so the horns
are carried low and level, pointed at whatever it is about to hit.

Run from the repo root:  python tools/graphorn_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, pair_horn, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "graphorn"
HITBOX = (1.7, 1.9)

HIDE = hx("#6E6680")
HIDE_DARK = hx("#4C465A")
HIDE_LIT = hx("#8A829C")
HORN = hx("#D9B24A")
HORN_DARK = hx("#A07E28")
TOE = hx("#3A3440")
EYE = hx("#D8C07A")


def build():
    rig = Rig(CID, 128)
    a = quadruped(
        rig, hip=12,
        chest=(14, 15, 10), barrel=(13, 13, 11), croup=(12, 11, 7), croup_drop=-8,
        neck=(8, 6, 7), neck_rake=62, head=(9, 8, 10), head_pitch=24, muzzle=(7, 5, 3),
        fore=[("", 6, 5, 5, None), ("lower", 4, 4, 4, None), ("foot", 2, 7, 8, None)],
        hind=[("", 6, 5, 6, (10, 0, 0)), ("lower", 4, 4, 4, (-10, 0, 0)), ("foot", 2, 7, 8, None)],
        fore_x=5, hind_x=5)

    # The hump: a raised block over the withers, deeper than the chest is tall at its peak.
    rig.cube("body", (-6, a.top, a.front + 1), (12, 7, 11), key="hump")
    rig.cube("body", (-4, a.top + 7, a.front + 3), (8, 3, 7), key="hump_crest")

    # Horns off the top of the skull, swept forward and slightly out: long, level, golden.
    pair_horn(rig, "head", name="horn", base=(3, a.head_y + 6, a.head_back - 3),
              size=(2, 7, 2), tip=(1, 7, 1), rotation=(78, 0, -12))

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 9, a.back - 1), direction=1,
                      segments=[(5, 3, 3), (4, 2, 2)], rotations=[(-40, 0, 0), (-10, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["chest", "barrel", "croup", "neck", "head", "muzzle", "hump", "hump_crest"],
              HIDE, top=HIDE_LIT, dither=0.1, dither_colour=HIDE_DARK)
    legs = rig.keys("foreleg", "hindleg")
    skin.skin(legs, HIDE, dither=0.08, dither_colour=HIDE_DARK)
    # Four-thumbed feet: the toes are dark bars across the front of the foot box.
    for key in [k for k in legs if "foot" in k]:
        skin.skin(key, HIDE_DARK, dither=0.0)
        x, y, w, h = skin.face(key, "north")
        for col in range(0, w, 2):
            skin.d.point((x + col, y + h - 1), fill=TOE)
    skin.skin(rig.keys("tail"), HIDE_DARK, dither=0.0)

    # Plated hide over the hump: a dragon's scales are thinner than this.
    skin.bands(["hump", "hump_crest"], HIDE_DARK, faces=("top",), step=3)
    skin.bands(["hump"], HIDE_DARK, faces=("east", "west"), step=4, offset=1)

    horns = rig.keys("horn_")
    skin.skin(horns, HORN, dither=0.0)
    skin.bands(horns, HORN_DARK, step=3)

    skin.eyes("head", EYE, row=2, inset=1, pupil=HIDE_DARK)
    skin.mark("muzzle", "north", 1, -2, HIDE_DARK)
    skin.mark("muzzle", "north", -2, -2, HIDE_DARK)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=4.6, walk_len=1.1, stride=20.0, bob_amp=0.6)
    walk.update(knee_tracks(a.rig, legs, 1.1, 10.0))
    attack = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.55, (-26, 0, 0)),
                                (1.0, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (10, 0, 0)), (0.55, (-20, 0, 0)),
                                (1.0, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, -0.5, 1)), (0.55, (0, 0.5, -3)),
                                (1.0, (0, 0, 0)))},
    }
    groan = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-18, 0, 0)), (1.3, (-14, 0, 0)),
                                (1.8, (0, 0, 0)))},
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.5, (-3, 0, 0)), (1.8, (0, 0, 0)))},
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-22, 0, 0)), (0.7, (8, 0, 0)),
                                        (1.0, (-18, 0, 0)), (1.3, (0, 0, 0)))},
    }
    return {"idle": clip(4.6, idle), "walk": clip(1.1, walk),
            "attack": clip(1.0, attack, loop=False), "groan": clip(1.8, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "graphorn_model.py", build, paint, anims, HITBOX))
