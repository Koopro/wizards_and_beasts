#!/usr/bin/env python3
"""The Streeler: a giant snail with a shell that changes colour and a trail that kills grass.

The shipped rig was a slug with a flat teal crate on its back, over a 64x64 sheet of 228-colour
noise (`documentation/ENTITY_ART_AUDIT.md` §3.5). A snail's whole read is the coiled shell, so
the shell here is a stack of whorls — a big body whorl, two smaller turns stepping forward and
up — with the spiral groove painted on both sides, banded in the colours it cycles through
(teal to violet to rose), since the rig has one static skin. Eye stalks up front, the foot long
and low with a paler sole.

`StreelerEntity` binds only `idle` and `walk`.

Run from the repo root:  python tools/streeler_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bespoke_reactions  # noqa: E402
import rigkit  # noqa: E402
from artgen_common import hx, mix  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "streeler"
HITBOX = (0.7, 0.7)
TEX_W = 64

FOOT = hx("#9FA895")
FOOT_DARK = hx("#6F7A68")
SOLE = hx("#C4C9B6")
EYE = hx("#1A1C18")
BANDS = [hx("#2BA59A"), hx("#3B7FC4"), hx("#7456C2"), hx("#B0539A")]
GROOVE = hx("#1C2B33")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 2, 0))
    rig.cube("body", (-3.5, 0, -7), (7, 3, 16), key="foot")
    rig.bone("head", "body", (0, 3, -6), (-24, 0, 0))
    rig.cube("head", (-2.5, 1.5, -11), (5, 4, 5), key="head")

    def stalk(side, sign):
        rig.bone(f"stalk_{side}", "head", (sign * 1.5, 5, -9), (-10, 0, sign * -18))
        rig.cube(f"stalk_{side}", Rig.mirror((1, 5, -9.5), (1, 5, 1), sign), (1, 5, 1))
        rig.cube(f"stalk_{side}", Rig.mirror((0.5, 9.5, -10), (2, 2, 2), sign), (2, 2, 2), key=f"eye_{side}")

    rig.pair(stalk)
    # The shell: three whorls, largest over the back, each smaller turn forward and higher.
    rig.bone("shell", "body", (0, 3, 1), (-8, 0, 0))
    rig.cube("shell", (-3.5, 3, -4), (7, 11, 11), key="whorl_1")
    rig.cube("shell", (-3, 8, -5), (6, 7, 7), key="whorl_2", inflate=0.3)
    rig.cube("shell", (-2, 12, -4), (4, 4, 4), key="whorl_3", inflate=0.3)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    d = skin.d
    skin.skin(["foot", "head"], FOOT, bottom=SOLE, bevel=0.88, dither=0.12, dither_colour=FOOT_DARK)
    skin.skin(rig.keys("stalk_left", "stalk_right"), FOOT, bevel=0, dither=0)
    skin.skin(["eye_left", "eye_right"], EYE, bevel=0.8, dither=0)
    # Slime sheen along the foot's flanks.
    for name in ("east", "west"):
        x0, y0, w, h = rig.faces("foot")[name]
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=SOLE)
    # Shell: concentric bands on the side faces (the spiral), stripes round the rim.
    for i, key in enumerate(("whorl_1", "whorl_2", "whorl_3")):
        f = rig.faces(key)
        for name in ("top", "bottom", "north", "south"):
            x0, y0, w, h = f[name]
            for yy in range(h):
                for xx in range(w):
                    band = BANDS[(yy // 2 + i) % len(BANDS)] if name in ("north", "south") else BANDS[(xx // 2 + i) % len(BANDS)]
                    d.point((x0 + xx, y0 + yy), fill=band)
        for name in ("east", "west"):
            x0, y0, w, h = f[name]
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            for yy in range(h):
                for xx in range(w):
                    r = max(abs(xx - cx), abs(yy - cy))
                    band = BANDS[(int(r) + i) % len(BANDS)]
                    d.point((x0 + xx, y0 + yy), fill=band)
                    if abs(r - round(r)) < 0.01 and int(r) % 2 == 1:
                        d.point((x0 + xx, y0 + yy), fill=mix(band, GROOVE, 0.55))
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], outline=GROOVE)
    return skin


def anims():
    L = 3.0
    idle = {
        "head": {"rotation": kf((0, (0, 0, 0)), (1.2, (-6, 10, 0)), (2.2, (4, -8, 0)), (L, (0, 0, 0)))},
        "stalk_left": {"rotation": kf((0, (0, 0, 0)), (0.9, (-14, 0, 8)), (2.0, (10, 0, -6)), (L, (0, 0, 0)))},
        "stalk_right": {"rotation": kf((0, (0, 0, 0)), (1.1, (10, 0, -8)), (2.3, (-12, 0, 6)), (L, (0, 0, 0)))},
        "shell": {"rotation": kf((0, (0, 0, 0)), (L / 2, (0, 0, 2)), (L, (0, 0, 0)))},
    }
    W = 2.0
    walk = {
        "body": {"scale": kf((0, (1, 1, 1)), (W / 2, (0.96, 1, 1.08)), (W, (1, 1, 1)))},
        "head": {"rotation": kf((0, (0, 0, 0)), (W / 2, (-6, 0, 0)), (W, (0, 0, 0)))},
        "shell": {"position": kf((0, (0, 0, 0)), (W / 2, (0, 0.2, -0.6)), (W, (0, 0, 0)))},
        "stalk_left": {"rotation": kf((0, (6, 0, 0)), (W / 2, (-6, 0, 0)), (W, (6, 0, 0)))},
        "stalk_right": {"rotation": kf((0, (-6, 0, 0)), (W / 2, (6, 0, 0)), (W, (-6, 0, 0)))},
    }
    return {"idle": clip(L, idle), "walk": clip(W, walk)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    clips = anims()
    clips.update(bespoke_reactions.clips_for(CID, [(b.name, b.parent, b.pivot) for b in rig.bones]))
    return rigkit.emit(rig, paint(rig, tex_h), clips, cid=CID, tool="streeler_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, definition=False, shared_clips=False)


if __name__ == "__main__":
    sys.exit(main())
