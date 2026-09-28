#!/usr/bin/env python3
"""The winged horse shared by the Abraxan, the Aethonan and the Granian.

Canon lists the breeds side by side and separates them by size and colour only: the Abraxan is
the immense palomino of the Beauxbatons carriage, the Aethonan the chestnut of Britain and
Ireland, the Granian a grey and exceptionally fast. So there is one skeleton here, built at a
scale factor, and the three generators differ in scale, palette and what they carry.

Scaling a rig is where the whole-number landmine lives: every size is rounded after scaling,
never before, and leg segments are re-summed so the hip is wherever the scaled legs put it.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import chain, ground, quadruped, quadruped_loops, knee_tracks, wing_beat, wing_pair  # noqa: E402
from rigkit import Skin, kf  # noqa: E402


def _s(v, s):
    return max(1, int(round(v * s)))


def winged_horse(rig, s, *, neck_rake=34, tail_len=11):
    fore = [("", _s(8, s), _s(3, s), _s(4, s), None), ("cannon", _s(6, s), _s(2, s), _s(2, s), None),
            ("hoof", _s(2, s), _s(3, s), _s(3, s), None)]
    hind = [("", _s(8, s), _s(4, s), _s(5, s), (10, 0, 0)), ("hock", _s(6, s), _s(2, s), _s(3, s), (-14, 0, 0)),
            ("hoof", _s(2, s), _s(3, s), _s(3, s), None)]
    hip = sum(seg[1] for seg in fore)
    hind[1] = ("hock", hind[1][1] + hip - sum(seg[1] for seg in hind), *hind[1][2:])
    a = quadruped(
        rig, hip=hip,
        chest=(_s(9, s), _s(10, s), _s(6, s)), barrel=(_s(8, s), _s(9, s), _s(11, s)),
        croup=(_s(8, s), _s(9, s), _s(6, s)), croup_drop=-4,
        neck=(_s(5, s), _s(10, s), _s(5, s)), neck_rake=neck_rake,
        head=(_s(5, s), _s(6, s), _s(8, s)), head_pitch=12, muzzle=(_s(4, s), _s(4, s), _s(3, s)),
        fore=fore, hind=hind, fore_x=_s(3, s), hind_x=_s(3, s))
    rig.cube("neck", (-_s(1, s) / 2.0 - 0.5, a.belly + _s(8, s), a.front + _s(4, s)),
             (_s(2, s), _s(10, s), _s(2, s)), key="mane")
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + _s(8, s), a.back - 1), direction=1,
                      segments=[(_s(3, s), _s(3, s), _s(3, s)), (_s(tail_len, s), _s(4, s), _s(3, s))],
                      rotations=[(-50, 0, 0), (-20, 0, 0)])
    a["wings"] = wing_pair(rig, "body", x=_s(4.5, s), y=a.top + 1, z=a.front + _s(4, s),
                           root=(_s(13, s), _s(10, s), _s(2, s)), tip=(_s(12, s), _s(9, s), 1),
                           primaries=3, primary_step=_s(3, s))
    a["rig"] = rig
    return a


def paint_horse(skin, rig, a, *, coat, coat_dark, mane, mane_dark, feather, feather_dark, hoof, eye,
                eye_glow=False):
    legs = rig.keys("foreleg", "hindleg")
    skin.skin(["chest", "barrel", "croup", "neck", "head", "muzzle"] + legs, coat, dither=0.05,
              dither_colour=coat_dark)
    skin.skin([k for k in legs if "hoof" in k], hoof, dither=0.0)
    skin.skin(["mane"] + rig.keys("tail"), mane, dither=0.0)
    skin.bands(["mane"] + rig.keys("tail"), mane_dark, step=3)
    wings = rig.keys("wing_")
    skin.skin(wings, feather, dither=0.0)
    skin.ramp(wings, feather, feather_dark, faces=("east", "west"))
    skin.bands(wings, feather_dark, faces=("east", "west"), step=3)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, eye, glow=eye_glow)
    skin.mark("muzzle", "north", 0, -2, coat_dark)
    skin.mark("muzzle", "north", -1, -2, coat_dark)


def horse_clips(a, *, fly_len=1.0, idle_len=4.0):
    idle, _walk, _legs = quadruped_loops(a, idle_len=idle_len)
    idle.update({w: {"rotation": kf((0.0, (0, 0, 0)), (idle_len / 2, (0, 0, 4 if w.endswith("left") else -4)),
                                    (idle_len, (0, 0, 0)))} for w in a.wings})
    fly = {
        **wing_beat(fly_len, spread=80.0, amp=40.0),
        "foreleg_left": {"rotation": kf((0.0, (-50, 0, 0)), (fly_len, (-50, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (-40, 0, 0)), (fly_len, (-40, 0, 0)))},
        "hindleg_left": {"rotation": kf((0.0, (40, 0, 0)), (fly_len, (40, 0, 0)))},
        "hindleg_right": {"rotation": kf((0.0, (46, 0, 0)), (fly_len, (46, 0, 0)))},
        "neck": {"rotation": kf((0.0, (10, 0, 0)), (fly_len / 2, (6, 0, 0)), (fly_len, (10, 0, 0)))},
        "tail_1": {"rotation": kf((0.0, (30, 0, 0)), (fly_len, (30, 0, 0)))},
    }
    strike = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-30, 0, 0)), (0.6, (-10, 0, 0)), (1.0, (0, 0, 0)))},
        "foreleg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-80, 0, 0)), (0.55, (20, 0, 0)),
                                        (1.0, (0, 0, 0)))},
        "foreleg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.35, (-80, 0, 0)), (0.6, (20, 0, 0)),
                                         (1.0, (0, 0, 0)))},
        **{w: {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 60 if w.endswith("left") else -60)),
                              (1.0, (0, 0, 0)))} for w in a.wings},
    }
    call = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-24, 0, 0)), (1.0, (-18, 0, 0)), (1.4, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (12, 0, 0)), (1.4, (0, 0, 0)))},
        **{w: {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 40 if w.endswith("left") else -40)),
                              (1.4, (0, 0, 0)))} for w in a.wings},
    }
    return idle, fly, strike, call
