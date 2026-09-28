#!/usr/bin/env python3
"""The Nundu: rig, skin and animation.

Canon calls it a gigantic leopard that moves silently and whose breath carries disease — "the
most dangerous beast in the world", which no other creature in this mod claims. 136 health,
14 damage and `nundu_pestilence`, so the silhouette has to sell that before the health bar
does.

Three things carry it, and none of them is size on its own:

  - a **ruff** of matted hide around the neck, wider than the skull, so the head reads as the
    front of something heavy rather than as a cat's head on a big body;
  - a **distended throat**, which is where the breath comes from, slung under the jaw;
  - **weight distribution** — shoulders higher than hips, and forelegs thicker than hind. A
    big cat drawn with even proportions reads as an inflated house cat.

Built at 2.75 blocks to the shoulder against the declared 2.7 x 2.9 hitbox.

Run from the repo root:  python tools/nundu_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "nundu"
HITBOX = (2.7, 2.9)
TEX_W = 128

HIDE = hx("#A8894F")
HIDE_DARK = hx("#6E5730")
BELLY = hx("#CBB483")
ROSETTE = hx("#463620")
RUFF = hx("#7A6238")
THROAT = hx("#9E8F63")       # sickly, paler than the hide — this is the diseased part
THROAT_SICK = hx("#8C9457")
FANG = hx("#E8E2CE")
EYE = hx("#C9E04B")
NOSE = hx("#3A2E22")
PAD = hx("#5A4A32")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 30, 6))

    # Shoulders higher than hips: the chest box is both taller and deeper than the barrel, and
    # sits a little forward of it, which is the entire difference between a big cat and a
    # scaled-up small one.
    rig.cube("body", (-10, 24, -2), (20, 17, 20), key="barrel")
    rig.cube("body", (-11, 23, -16), (22, 20, 14), key="chest")

    rig.bone("croup", "body", (0, 40, 16), (-5, 0, 0))
    rig.cube("croup", (-9, 24, 16), (18, 16, 8))

    # Ruff: one box around the base of the neck, grown with `inflate` rather than authored
    # bigger, so its UV island stays the size of the geometry underneath it.
    rig.bone("neck", "body", (0, 40, -15), (6, 0, 0))
    rig.cube("neck", (-8, 30, -24), (16, 14, 9))
    rig.cube("neck", (-9, 29, -23), (18, 16, 8), key="ruff", inflate=1.5)

    rig.bone("head", "neck", (0, 42, -23), (-8, 0, 0))
    rig.cube("head", (-8, 32, -35), (16, 14, 12))
    rig.cube("head", (-5, 31, -40), (10, 8, 5), key="muzzle")
    # Throat sac, slung under the jaw and painted sick — the ability made visible. Its own
    # bone, because it swells: a cube cannot carry a scale track, only a bone can.
    rig.bone("throat", "head", (0, 33, -34))
    rig.cube("throat", (-6, 26, -36), (12, 7, 10))

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 5, 46, -30), (-8, 0, sign * -22))
        rig.cube(f"ear_{side}", Rig.mirror((3, 46, -31), (4, 5, 3), sign), (4, 5, 3))

    rig.pair(build_ear)

    def build_fang(side, sign):
        rig.cube("head", Rig.mirror((2, 28, -39), (2, 4, 2), sign), (2, 4, 2),
                 key=f"fang_{side}")

    rig.pair(build_fang)

    rig.bone("tail", "croup", (0, 38, 24), (18, 0, 0))
    rig.cube("tail", (-3, 34, 24), (6, 6, 11))
    rig.bone("tail_tip", "tail", (0, 37, 35), (10, 0, 0))
    rig.cube("tail_tip", (-2, 34, 35), (4, 5, 9))

    def build_foreleg(side, sign):
        limb(rig, f"foreleg_{side}", "body", x=7, z=-11, sign=sign, joints=[
            ("", 28, 16, 10, 12, None),
            ("cannon", 13, 9, 8, 9, (6, 0, 0)),
            ("paw", 5, 5, 10, 13, (-6, 0, 0)),
        ])

    def build_hindleg(side, sign):
        limb(rig, f"hindleg_{side}", "croup", x=7, z=18, sign=sign, joints=[
            ("", 26, 15, 10, 14, (14, 0, 0)),
            ("shank", 12, 8, 7, 8, (-20, 0, 0)),
            ("paw", 5, 5, 9, 12, (6, 0, 0)),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")

    skin.skin(["barrel", "chest", "croup"], HIDE, bottom=BELLY, dither=0.05,
              dither_colour=HIDE_DARK)
    skin.skin(["head", "muzzle", "neck", "tail", "tail_tip"], HIDE, dither=0.04,
              dither_colour=HIDE_DARK)
    skin.skin(legs, HIDE, dither=0.04, dither_colour=HIDE_DARK)
    skin.skin([k for k in legs if "paw" in k], PAD, dither=0.03)
    skin.skin("ruff", RUFF, dither=0.14, dither_colour=HIDE_DARK)
    skin.skin(["ear_left", "ear_right"], RUFF, dither=0.0)
    skin.skin(["fang_left", "fang_right"], FANG, dither=0.0)

    # Rosettes: a leopard's coat is rings, not spots, and at this size there is room to draw
    # them as rings. Deterministic placement — a random scatter re-rolls on every run and the
    # coat stops being the same animal twice.
    for key in ("barrel", "chest", "croup"):
        for face in ("east", "west", "top"):
            x, y, w, h = skin.face(key, face)
            seed = sum(ord(c) for c in key + face)
            for row in range(2, h - 2, 5):
                for col in range(2 + (row // 5 % 2) * 3, w - 2, 6):
                    n = (col * 374761393 + row * 668265263 + seed) & 0xFFFFFFFF
                    if n % 5 == 0:
                        continue
                    skin.d.rectangle([x + col, y + row, x + col + 2, y + row + 1],
                                     outline=ROSETTE)

    # The throat is the one part that is not leopard: paler, blotched, and sickly green where
    # the breath collects.
    skin.skin("throat", THROAT, dither=0.0)
    skin.bands("throat", THROAT_SICK, faces=("east", "west", "bottom", "north"), step=3)

    skin.eyes("head", EYE, row=2, inset=3, pupil=NOSE, width=2, glow=True)
    skin.mark("muzzle", "north", 3, 0, NOSE, w=4, h=2)
    skin.tip(["fang_left", "fang_right"], hx("#CFC6AC"), rows=1)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}

    # Slow everything down. A 2.9-block predator that idles at a house cat's tempo reads as a
    # toy; weight is communicated almost entirely by clip length.
    idle = {
        "body": bob(5.0, 0.4, cycles=2),
        "neck": osc(5.0, 3.0, phase=0.1),
        "head": {"rotation": rigkit.kf((0.0, (0, -10, 0)), (1.7, (4, 11, 0)),
                                       (3.4, (-3, -6, 0)), (5.0, (0, -10, 0)))},
        "throat": {"scale": rigkit.kf((0.0, (1, 1, 1)), (2.5, (1.08, 1.12, 1.08)),
                                      (5.0, (1, 1, 1)))},
        "ear_left": osc(5.0, 9.0, phase=0.0),
        "ear_right": osc(5.0, 9.0, phase=0.45),
        "tail": osc(5.0, 6.0, axis="z", phase=0.0),
        "tail_tip": osc(5.0, 12.0, axis="z", phase=0.25),
    }

    walk = {
        **gait(1.3, legs, amp=20.0),
        **{f"{name}_shank": osc(1.3, 13.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        **{f"{name}_cannon": osc(1.3, 11.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("foreleg")},
        "body": osc(1.3, 2.5, axis="y"),
        "root": bob(1.3, 0.8, cycles=2),
        "neck": osc(1.3, 2.5, phase=0.25),
        "tail": osc(1.3, 7.0, axis="z"),
    }

    bite = {
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-26, 0, 0)),
                                       (0.5, (22, 0, 0)), (1.0, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-18, 0, 0)),
                                       (0.5, (30, 0, 0)), (1.0, (0, 0, 0)))},
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.0, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-40, 0, 0)),
                                               (0.6, (20, 0, 0)), (1.0, (0, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (-40, 0, 0)),
                                                (0.65, (20, 0, 0)), (1.0, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.35, (0, 1.5, -3)),
                                       (1.0, (0, 0, 0)))},
    }

    # `howl` is the roar, on the ambient beat. The throat sac swells first and empties into the
    # sound, which is the closest the rig gets to showing where the pestilence comes from.
    howl = {
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (-30, 0, 0)),
                                       (1.4, (-24, 0, 0)), (2.0, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (-14, 0, 0)),
                                       (1.4, (-10, 0, 0)), (2.0, (0, 0, 0)))},
        "throat": {"scale": rigkit.kf((0.0, (1, 1, 1)), (0.5, (1.3, 1.35, 1.3)),
                                      (1.5, (0.95, 0.9, 0.95)), (2.0, (1, 1, 1)))},
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.5, (-6, 0, 0)), (2.0, (0, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.6, (-20, 0, 0)), (2.0, (0, 0, 0)))},
    }

    return {
        "idle": clip(5.0, idle),
        "walk": clip(1.3, walk),
        "bite": clip(1.0, bite, loop=False),
        "howl": clip(2.0, howl, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID, tool="nundu_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
