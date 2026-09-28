#!/usr/bin/env python3
"""The Dementor: rig, skin and every clip `DementorEntity` plays.

Canon (Prisoner of Azkaban): a towering cloaked figure whose face is hidden under its hood, gliding rather than
walking, with a hand that is "glistening, greyish, slimy-looking, and scabbed, like something dead that had decayed
in water". Blind; it drains peace, hope and happiness; the Kiss takes the soul.

The read is the silhouette: a hood deeper and wider than the shoulders with a void where a face would be, a gaunt
narrow body, arms too long for it ending in pale bony hands, and no legs — the robe hangs into four torn panels and
a fringe of rags that stream behind it. Each panel and rag strip is its own bone so the cloth can lag and ripple;
nothing else needs to move. No glow: a Dementor is an absence of light.

Clips (all bound in `DementorEntity`): idle, float (movement); drain, kiss_windup, kiss_resolve, repelled, dissipate
(state); hurt (trigger).

Run from the repo root:  python tools/dementor_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "dementor"
HITBOX = (0.9, 3.2)
TEX_W = 128

CLOTH = hx("#17171B")
CLOTH_LIT = hx("#272830")
CLOTH_BLUE = hx("#232733")
CLOTH_FOLD = hx("#0C0C0F")
RAG = hx("#202228")
SKIN = hx("#8A9187")
SKIN_DARK = hx("#5C6259")
SCAB = hx("#4A4238")
VOID = hx("#030304")

CLIPS = ("idle", "float", "drain", "kiss_windup", "kiss_resolve", "repelled", "dissipate", "hurt")
PANELS = (("robe_front", 0, -1), ("robe_back", 0, 1), ("robe_left", 1, 0), ("robe_right", -1, 0))


def build():
    rig = Rig(CID, TEX_W)
    # The whole figure floats: `ground()` is never called, and the lowest rag tips sit at y=0 only so the hitbox
    # has something to measure from. The body hangs off `body`; robe panels hang off `robe`.
    rig.bone("body", "root", (0, 38, 0), (10, 0, 0))
    rig.cube("body", (-4, 30, -3), (8, 12, 6), key="torso")
    rig.cube("body", (-6, 40, -3.5), (12, 3, 7), key="shoulders", inflate=0.25)

    rig.bone("hood", "body", (0, 43, -1), (12, 0, 0))
    rig.cube("hood", (-5, 42, -7), (10, 10, 11), key="hood")
    rig.cube("hood", (-3, 51, -2), (6, 2, 7), key="hood_peak")
    rig.cube("hood", (-5.5, 50, -8), (11, 2, 3), key="hood_brim")
    rig.bone("head", "hood", (0, 45, -1))          # the dark inside the hood; animated for the kiss
    rig.cube("head", (-3, 43, -6), (6, 6, 3), key="void")

    def arm(side, sign):
        name = f"arm_{side}"
        rig.bone(name, "body", (sign * 6, 41, -1), (-6, 0, sign * 6))
        rig.cube(name, Rig.mirror((5, 31, -3), (4, 10, 4), sign), (4, 10, 4), key=f"{name}_sleeve")
        rig.bone(f"{name}_fore", name, (sign * 7, 31, -1), (-10, 0, 0))
        rig.cube(f"{name}_fore", Rig.mirror((4.5, 21, -3.5), (5, 10, 5), sign), (5, 10, 5), key=f"{name}_cuff")
        rig.bone(f"{name}_hand", f"{name}_fore", (sign * 7, 21, -1))
        rig.cube(f"{name}_hand", Rig.mirror((5.5, 17, -2.5), (3, 4, 3), sign), (3, 4, 3), key=f"{name}_palm")
        for i, dz in enumerate((-2.5, -1, 0.5)):
            length = 6 - (i % 2)
            rig.cube(f"{name}_hand", Rig.mirror((5.5 + (i % 2), 17 - length, dz), (1, length, 1), sign),
                     (1, length, 1), key=f"{name}_finger{i}")

    rig.pair(arm)

    # The robe: one bone at the waist, four panels hanging from it, torn rags at the hem of each.
    rig.bone("robe", "root", (0, 30, 0))
    rig.cube("robe", (-6, 22, -4.5), (12, 9, 9), key="robe_skirt")
    for name, sx, sz in PANELS:
        wide = sx == 0
        pivot = (sx * 5.5, 23, sz * 4)
        rig.bone(name, "robe", pivot)
        if wide:
            rig.cube(name, (-6, 8, sz * 4.5 - 0.5), (12, 15, 1), key=f"{name}_cloth")
        else:
            rig.cube(name, (sx * 6 - 0.5, 8, -4.5), (1, 15, 9), key=f"{name}_cloth")
    for i, (x, z) in enumerate(((-4, -4), (3, -4), (-5, 4), (0, 5), (4, 4))):
        rig.bone(f"rag_{i}", "robe", (x, 9, z), (0, 0, 0))
        rig.cube(f"rag_{i}", (x - 1, 0, z - 0.5), (2, 9, 1), key=f"rag_{i}")
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="speckle")
    d = skin.d
    sides = ("east", "west", "north", "south")
    cloth = ["torso", "shoulders", "hood", "hood_peak", "hood_brim", "robe_skirt"] + rig.keys("arm_left_sleeve",
            "arm_right_sleeve", "arm_left_cuff", "arm_right_cuff")
    skin.skin(cloth, CLOTH, top=CLOTH_LIT, bevel=0.85, dither=0.0)
    # Long hanging folds: a dark crease and a faint blue-grey highlight, uneven so nothing tiles.
    for key in ["torso", "robe_skirt", "arm_left_sleeve", "arm_right_sleeve", "arm_left_cuff", "arm_right_cuff",
                "hood"]:
        for name in sides:
            x0, y0, w, h = rig.faces(key)[name]
            for c in range(2, w - 1, 4):
                top = 1 + (c * 7 + len(key)) % 3
                d.line([(x0 + c, y0 + top), (x0 + c, y0 + h - 1)], fill=CLOTH_FOLD)
                d.line([(x0 + c + 1, y0 + top + 2), (x0 + c + 1, y0 + h - 1)], fill=CLOTH_BLUE)
    # The hood's opening: nothing inside, rimmed by the brim's shadow.
    x0, y0, w, h = rig.faces("hood")["north"]
    d.rectangle([x0 + 2, y0 + 2, x0 + w - 3, y0 + h - 1], fill=VOID)
    d.line([(x0 + 2, y0 + 2), (x0 + w - 3, y0 + 2)], fill=CLOTH_FOLD)
    skin.skin("void", VOID, bevel=0.0, dither=0.0)
    x0, y0, w, h = rig.faces("hood_brim")["bottom"]
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=VOID)

    # Robe panels and rags: cloth sheets with the hem torn into uneven strips.
    for key in rig.keys("robe_front_cloth", "robe_back_cloth", "robe_left_cloth", "robe_right_cloth", "rag_"):
        f = rig.faces(key)
        big = [n for n in sides if f[n][2] > 2]
        for name in ("north", "south", "east", "west", "top", "bottom"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=RAG if name in big else CLOTH_FOLD)
        for name in big:
            x0, y0, w, h = f[name]
            for c in range(1, w, 3):
                d.line([(x0 + c, y0), (x0 + c, y0 + h - 1)], fill=CLOTH_FOLD)
            for c in range(w):
                n = rigkit._hash(c // 2, sum(map(ord, key + name)), 11)
                cut = 2 + n % max(1, h // 2)
                if (c // 2) % 3 == 0:
                    cut = max(1, cut - 3)
                for r in range(h - cut, h):
                    d.point((x0 + c, y0 + r), fill=(0, 0, 0, 0))
        x0, y0, w, h = f["bottom"]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(0, 0, 0, 0))

    # Hands: grey, wet, scabbed, bony.
    hands = rig.keys("arm_left_palm", "arm_right_palm", "arm_left_finger", "arm_right_finger")
    skin.skin(hands, SKIN, bevel=0.85, dither=0.15, dither_colour=SKIN_DARK)
    for key in rig.keys("arm_left_palm", "arm_right_palm"):
        for name in ("north", "east", "west"):
            x0, y0, w, h = rig.faces(key)[name]
            d.point((x0 + (w - 1) // 2, y0 + 1), fill=SCAB)
            d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=SKIN_DARK)   # knuckles
    for key in rig.keys("arm_left_finger", "arm_right_finger"):
        for name in sides:
            x0, y0, w, h = rig.faces(key)[name]
            for r in range(1, h, 2):
                d.point((x0, y0 + r), fill=SKIN_DARK)                                  # finger joints
    skin.tip(rig.keys("arm_left_finger", "arm_right_finger"), SCAB, rows=1)
    return skin


def rot(*frames):
    return {"rotation": kf(*frames)}


def cloth_ripple(length, amp, speed=1.0):
    """The robe panels and rags lag and ripple, each a little out of step with the last."""
    out = {}
    names = [p[0] for p in PANELS] + [f"rag_{i}" for i in range(5)]
    for i, name in enumerate(names):
        a = amp * (1.2 if name.startswith("rag") else 1.0)
        ph = (i * 0.17) % 1.0
        axis = 2 if name in ("robe_left", "robe_right") else 0
        frames = []
        for k in range(5):
            t = length * k / 4
            v = [0, 0, 0]
            import math
            v[axis] = a * math.sin(2 * math.pi * (k / 4 * speed + ph))
            frames.append((t, tuple(v)))
        out[name] = rot(*frames)
    return out


def anims():
    clips = {}
    # idle: hanging in the air, rising and sinking, the cloth barely stirring, the hood turning slowly.
    L = 4.0
    idle = {
        "root": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 1.2, 0)), (L, (0, 0, 0)))},
        "body": rot((0, (0, 0, 0)), (L / 2, (3, 0, 0)), (L, (0, 0, 0))),
        "hood": rot((0, (0, -8, 0)), (L * 0.45, (4, 10, 0)), (L * 0.55, (4, 10, 0)), (L, (0, -8, 0))),
        "arm_left": rot((0, (0, 0, 0)), (L / 2, (-4, 0, 2)), (L, (0, 0, 0))),
        "arm_right": rot((0, (0, 0, 0)), (L / 2, (-4, 0, -2)), (L, (0, 0, 0))),
    }
    idle.update(cloth_ripple(L, 4.0))
    clips["idle"] = clip(L, idle)

    # float: leaning into the glide, the robe streaming back.
    F = 1.6
    float_ = {
        "root": {"position": kf((0, (0, 0, 0)), (F / 2, (0, 0.6, 0)), (F, (0, 0, 0)))},
        "body": rot((0, (14, 0, 0)), (F, (14, 0, 0))),
        "hood": rot((0, (-6, 0, 0)), (F, (-6, 0, 0))),
        "robe": rot((0, (-24, 0, 0)), (F / 2, (-28, 0, 0)), (F, (-24, 0, 0))),
        "arm_left": rot((0, (-10, 0, 0)), (F, (-10, 0, 0))),
        "arm_right": rot((0, (-10, 0, 0)), (F, (-10, 0, 0))),
    }
    float_.update(cloth_ripple(F, 9.0, speed=1.0))
    clips["float"] = clip(F, float_)

    # drain: it hangs close and reaches, both hands out toward the victim, the hood tilted to them. Looped while
    # the state lasts.
    Dr = 2.0
    drain = {
        "body": rot((0, (18, 0, 0)), (Dr / 2, (22, 0, 0)), (Dr, (18, 0, 0))),
        "hood": rot((0, (8, 0, 0)), (Dr / 2, (14, 0, 0)), (Dr, (8, 0, 0))),
        "arm_left": rot((0, (-70, -8, 10)), (Dr / 2, (-76, -10, 12)), (Dr, (-70, -8, 10))),
        "arm_right": rot((0, (-70, 8, -10)), (Dr / 2, (-76, 10, -12)), (Dr, (-70, 8, -10))),
        "arm_left_hand": rot((0, (0, 0, 0)), (Dr / 2, (-12, 0, 0)), (Dr, (0, 0, 0))),
        "arm_right_hand": rot((0, (0, 0, 0)), (Dr / 2, (-12, 0, 0)), (Dr, (0, 0, 0))),
    }
    drain.update(cloth_ripple(Dr, 12.0, speed=2.0))
    clips["drain"] = clip(Dr, drain)

    # kiss_windup: the hood lowers toward the victim, the hands close in on either side of their head.
    W = 1.5
    windup = {
        "body": rot((0, (0, 0, 0)), (W, (28, 0, 0))),
        "hood": rot((0, (0, 0, 0)), (W, (22, 0, 0))),
        "head": {"position": kf((0, (0, 0, 0)), (W, (0, 0, -1.5)))},
        "arm_left": rot((0, (0, 0, 0)), (W * 0.6, (-86, -12, 20)), (W, (-80, -24, 8))),
        "arm_right": rot((0, (0, 0, 0)), (W * 0.6, (-86, 12, -20)), (W, (-80, 24, -8))),
        "arm_left_fore": rot((0, (0, 0, 0)), (W, (-18, 0, 0))),
        "arm_right_fore": rot((0, (0, 0, 0)), (W, (-18, 0, 0))),
    }
    windup.update(cloth_ripple(W, 14.0, speed=2.0))
    clips["kiss_windup"] = dict(clip(W, windup, loop=False), loop="hold_on_last_frame")

    # kiss_resolve: the last inches — the hood closes on the face and holds.
    R = 0.75
    resolve = {
        "body": rot((0, (28, 0, 0)), (0.3, (36, 0, 0)), (R, (32, 0, 0))),
        "hood": rot((0, (22, 0, 0)), (0.3, (36, 0, 0)), (R, (30, 0, 0))),
        "head": {"position": kf((0, (0, 0, -1.5)), (0.3, (0, 0, -3)), (R, (0, 0, -2.5)))},
        "arm_left": rot((0, (-80, -24, 8)), (R, (-72, -30, 2))),
        "arm_right": rot((0, (-80, 24, -8)), (R, (-72, 30, -2))),
    }
    clips["kiss_resolve"] = dict(clip(R, resolve, loop=False), loop="hold_on_last_frame")

    # repelled: flung back and up from the silver light, arms thrown across the hood.
    Rp = 1.0
    repelled = {
        "root": {"position": kf((0, (0, 0, 0)), (0.25, (0, 3, 4)), (Rp, (0, 1.5, 2.5)))},
        "body": rot((0, (0, 0, 0)), (0.25, (-30, 0, 0)), (Rp, (-14, 0, 0))),
        "hood": rot((0, (0, 0, 0)), (0.25, (-16, 0, 0)), (Rp, (10, 0, 0))),
        "robe": rot((0, (0, 0, 0)), (0.3, (28, 0, 0)), (Rp, (12, 0, 0))),
        "arm_left": rot((0, (0, 0, 0)), (0.3, (-150, 0, -24)), (Rp, (-120, 0, -12))),
        "arm_right": rot((0, (0, 0, 0)), (0.3, (-150, 0, 24)), (Rp, (-120, 0, 12))),
    }
    repelled.update(cloth_ripple(Rp, 16.0, speed=3.0))
    clips["repelled"] = dict(clip(Rp, repelled, loop=False), loop="hold_on_last_frame")

    # hurt: a blow does nothing to it; it recoils, and the cloth shudders.
    H = 0.5
    hurt = {
        "body": rot((0, (0, 0, 0)), (0.1, (-8, 0, 0)), (H, (0, 0, 0))),
        "hood": rot((0, (0, 0, 0)), (0.12, (-10, 6, 0)), (H, (0, 0, 0))),
        "root": {"position": kf((0, (0, 0, 0)), (0.1, (0, 0.3, 0.8)), (H, (0, 0, 0)))},
    }
    hurt.update(cloth_ripple(H, 10.0, speed=2.0))
    clips["hurt"] = clip(H, hurt, loop=False)

    # dissipate: it unravels upward — the robe and rags spread and thin, and the figure is gone.
    Dz = 1.0
    gone = (0.02, 0.02, 0.02)
    dissipate = {
        "root": {"scale": kf((0, (1, 1, 1)), (0.6, (0.8, 1.3, 0.8)), (Dz, gone)),
                 "position": kf((0, (0, 0, 0)), (Dz, (0, 8, 0)))},
        "robe": rot((0, (0, 0, 0)), (Dz, (0, 120, 0))),
    }
    for i in range(5):
        dissipate[f"rag_{i}"] = rot((0, (0, 0, 0)), (Dz, ((-1) ** i * 50, 0, (-1) ** (i + 1) * 30)))
    clips["dissipate"] = dict(clip(Dz, dissipate, loop=False), loop="hold_on_last_frame")
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool="dementor_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, definition=False, shared_clips=False, extra_clips=CLIPS)


if __name__ == "__main__":
    sys.exit(main())
