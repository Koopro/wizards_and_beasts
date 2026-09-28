#!/usr/bin/env python3
"""The Cornish Pixie.

The shipped rig was a blue stick with two panes over a 32x32 noise sheet: no face, no ears, and
two transparent texels (`documentation/ENTITY_ART_AUDIT.md` §3.5).

Canon (Chamber of Secrets): electric blue, about eight inches high, pointed faces, shrill voices,
and pure mischief. The read at distance is the head — big, pointed-eared, grinning — on a tiny
body under a blur of insect wings, so the head is drawn large (oversize, like every small mob that
has to carry a face) and the ears swept up and out. Wings are membrane plates with cut outlines
and painted veins.

`CornishPixieEntity` binds only `idle` and `fly`.

Run from the repo root:  python tools/cornish_pixie_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bespoke_reactions  # noqa: E402
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "cornish_pixie"
HITBOX = (0.4, 0.6)
TEX_W = 64

BLUE = hx("#2F6FE8")
BLUE_LIT = hx("#5C95FF")
BLUE_DARK = hx("#1C43A6")
HAIR = hx("#16307A")
EYE = hx("#F4F07A")
PUPIL = hx("#101018")
MOUTH = hx("#0E1640")
TOOTH = hx("#F2F2F2")
WING = hx("#CFE4F7")
VEIN = hx("#7FA6CC")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 5, 0))
    rig.cube("body", (-2, 3, -1.5), (4, 5, 3), key="torso")

    rig.bone("head", "body", (0, 8, 0))
    rig.cube("head", (-3, 8, -3), (6, 6, 5), key="skull")
    rig.cube("head", (-1, 7, -3.5), (2, 2, 2), key="chin")  # the pointed face
    rig.cube("head", (-2.5, 13, -1.5), (5, 2, 4), key="hair", inflate=0.25)

    def ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 3, 11, -0.5), (0, sign * -20, sign * -35))
        rig.cube(f"ear_{side}", Rig.mirror((3, 10.5, -1), (4, 2, 1), sign), (4, 2, 1))

    rig.pair(ear)

    def limb(side, sign):
        rig.bone(f"arm_{side}", "body", (sign * 2.5, 7.5, 0), (0, 0, sign * -12))
        rig.cube(f"arm_{side}", Rig.mirror((2, 3, -0.5), (1, 5, 1), sign), (1, 5, 1))
        rig.bone(f"leg_{side}", "body", (sign * 1, 3, 0), (10, 0, 0))
        rig.cube(f"leg_{side}", Rig.mirror((0.5, -2, -0.5), (1, 5, 1), sign), (1, 5, 1))

    rig.pair(limb)

    def wing(side, sign):
        rig.bone(f"wing_{side}", "body", (sign * 1, 7, 1.5), (0, sign * -35, 0))
        rig.cube(f"wing_{side}", Rig.mirror((1, 6, 1.5), (1, 4, 8), sign), (1, 4, 8), key=f"wing_{side}_up")
        rig.cube(f"wing_{side}", Rig.mirror((1, 3, 1.5), (1, 3, 8), sign), (1, 3, 8), key=f"wing_{side}_low")

    rig.pair(wing)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    d = skin.d
    blue = rig.keys("torso", "skull", "chin", "ear_", "arm_", "leg_")
    skin.skin(blue, BLUE, bevel=0.85, dither=0.05, dither_colour=BLUE_LIT)
    skin.skin("hair", HAIR, bevel=0.85, dither=0.1, dither_colour=BLUE_DARK)
    skin.tip(rig.keys("ear_"), BLUE_DARK, rows=1)
    # Face: huge eyes, a wide grin with teeth.
    x0, y0, w, h = rig.faces("skull")["north"]
    for ex in (x0 + 1, x0 + w - 3):
        d.rectangle([ex, y0 + 2, ex + 1, y0 + 2], fill=EYE)
        d.point((ex + (1 if ex > x0 + 2 else 0), y0 + 2), fill=PUPIL)
        d.line([(ex, y0 + 1), (ex + 1, y0 + 1)], fill=BLUE_DARK)
    d.line([(x0 + 1, y0 + 4), (x0 + w - 2, y0 + 4)], fill=MOUTH)
    for xx in range(x0 + 2, x0 + w - 2, 2):
        d.point((xx, y0 + 4), fill=TOOTH)
    d.point((x0, y0 + 3), fill=MOUTH)
    d.point((x0 + w - 1, y0 + 3), fill=MOUTH)
    # Wings: pale, veined, outline cut so they read as insect wings and not panes.
    for key in rig.keys("wing_left_", "wing_right_"):
        f = rig.faces(key)
        for name in ("east", "west", "north", "south", "top", "bottom"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=WING)
        for name in ("east", "west"):
            x0, y0, w, h = f[name]
            for c in range(1, w - 1):
                d.point((x0 + c, y0 + h // 2), fill=VEIN)
            # Rounded ends: the far corners and the root corners go, nothing in between.
            for c in range(w):
                along = c if name == "west" else w - 1 - c   # 0 at the root
                if along >= w - 2 or along == 0:
                    for r in (0, h - 1):
                        d.point((x0 + c, y0 + r), fill=(0, 0, 0, 0))
                if along == w - 1 and h > 3:
                    for r in (1, h - 2):
                        d.point((x0 + c, y0 + r), fill=(0, 0, 0, 0))
        x0, y0, w, h = f["north"]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(0, 0, 0, 0))
        x0, y0, w, h = f["south"]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(0, 0, 0, 0))
    return skin


def anims():
    L = 1.6
    buzz = [(i * 0.05, (0, 0, 22 if i % 2 else -6)) for i in range(int(L / 0.05) + 1)]
    idle = {
        "body": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 1.2, 0)), (L, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 0)), (L / 2, (0, 0, 4)), (L, (0, 0, 0)))},
        "head": {"rotation": kf((0, (0, 0, 0)), (0.4, (0, 20, 8)), (0.9, (-6, -18, -6)), (L, (0, 0, 0)))},
        "arm_left": {"rotation": kf((0, (-20, 0, 0)), (L / 2, (-50, 0, -10)), (L, (-20, 0, 0)))},
        "arm_right": {"rotation": kf((0, (-50, 0, 0)), (L / 2, (-20, 0, 10)), (L, (-50, 0, 0)))},
        "leg_left": {"rotation": kf((0, (10, 0, 0)), (L / 2, (-10, 0, 0)), (L, (10, 0, 0)))},
        "leg_right": {"rotation": kf((0, (-10, 0, 0)), (L / 2, (10, 0, 0)), (L, (-10, 0, 0)))},
        "wing_left": {"rotation": kf(*buzz)},
        "wing_right": {"rotation": kf(*[(t, (0, 0, -v[2])) for t, v in buzz])},
    }
    F = 0.8
    fbuzz = [(i * 0.04, (0, 0, 26 if i % 2 else -8)) for i in range(int(F / 0.04) + 1)]
    fly = {
        "body": {"rotation": kf((0, (28, 0, 0)), (F, (28, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (F / 2, (0, 0.8, 0)), (F, (0, 0, 0)))},
        "head": {"rotation": kf((0, (-22, 0, 0)), (F, (-22, 0, 0)))},
        "arm_left": {"rotation": kf((0, (40, 0, -20)), (F, (40, 0, -20)))},
        "arm_right": {"rotation": kf((0, (40, 0, 20)), (F, (40, 0, 20)))},
        "leg_left": {"rotation": kf((0, (30, 0, 0)), (F / 2, (45, 0, 0)), (F, (30, 0, 0)))},
        "leg_right": {"rotation": kf((0, (45, 0, 0)), (F / 2, (30, 0, 0)), (F, (45, 0, 0)))},
        "wing_left": {"rotation": kf(*fbuzz)},
        "wing_right": {"rotation": kf(*[(t, (0, 0, -v[2])) for t, v in fbuzz])},
    }
    return {"idle": clip(L, idle), "fly": clip(F, fly)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    clips = anims()
    clips.update(bespoke_reactions.clips_for(CID, [(b.name, b.parent, b.pivot) for b in rig.bones]))
    return rigkit.emit(rig, paint(rig, tex_h), clips, cid=CID, tool="cornish_pixie_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=a.force, definition=False, shared_clips=False)


if __name__ == "__main__":
    sys.exit(main())
