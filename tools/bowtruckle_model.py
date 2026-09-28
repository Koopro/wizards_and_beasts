#!/usr/bin/env python3
"""The Bowtruckle: rig, skin and every clip `BowtruckleEntity` plays.

Canon (Fantastic Beasts; the films' Pickett): a tree-guardian up to eight inches tall, a flat-faced body of bark and
twigs, two small brown eyes, two long sharp fingers on each hand; shy and peaceful until its tree is threatened, when
it goes for the eyes; clever enough with those fingers to pick a lock.

Drawn at true size — about 0.6 blocks — because bonding sets `Attributes.SCALE` back to 1.0 for every adult (the
juvenile rule in `BondableBeast`), so an oversize rig scaled down would be drawn big the first time one grew up.
So it is a handful of one-texel twigs: a thin twig torso with a bark knot, a flat bark face with two brown eyes and
a sprig of leaves on top, twig arms ending in two long claw fingers, twig legs, a small leaf cluster on the back.

`look` is the cube-less bone the renderer turns toward what it watches (GeckoLib sets that bone's rotation outright),
so the head stays free for every clip.

Clips — movement: idle, walk, run, climb, hide, defend, lockpick; one-shots: curious, attack, pickup, hit, death.

Run from the repo root:  python tools/bowtruckle_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "bowtruckle"
HITBOX = (0.3, 0.6)
TEX_W = 32

BARK = hx("#4A3522")
BARK_DARK = hx("#2E2014")
WOOD = hx("#6B4C30")
TWIG = hx("#7E5D3B")
LEAF = hx("#5A7336")
LEAF_LIT = hx("#728C48")
EYE = hx("#3A2412")
EYE_DARK = hx("#150C06")
CLAW = hx("#CDBB94")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 4, 0))
    rig.cube("body", (-1, 4, -0.5), (2, 4, 1), key="torso")
    rig.cube("body", (-1.5, 5, -1), (1, 2, 1), key="knot")          # a bark knot on the chest

    rig.bone("look", "body", (0, 8, 0))
    rig.bone("head", "look", (0, 8, 0))
    rig.cube("head", (-2, 8, -1), (4, 3, 2), key="face")
    rig.bone("sprig", "head", (0, 11, 0), (-10, 0, 0))
    rig.cube("sprig", (-0.5, 11, -0.5), (1, 2, 1), key="sprig_stem")
    rig.cube("sprig", (-1.5, 12, -0.5), (3, 1, 1), key="sprig_leaves")

    def tuft(side, sign):
        rig.bone(f"leaf_{side}", "head", (sign * 1.5, 10, 0), (0, 0, sign * -35))
        rig.cube(f"leaf_{side}", Rig.mirror((1.5, 10, -0.5), (1, 2, 1), sign), (1, 2, 1), key=f"leaf_{side}")

    rig.pair(tuft)

    def arm(side, sign):
        rig.bone(f"arm_{side}", "body", (sign * 1.5, 7.5, 0), (0, 0, sign * 8))
        rig.cube(f"arm_{side}", Rig.mirror((1, 4.5, -0.5), (1, 3, 1), sign), (1, 3, 1), key=f"arm_{side}")
        rig.bone(f"hand_{side}", f"arm_{side}", (sign * 1.5, 4.5, 0))
        # Two long sharp fingers — the lock-picks and the eye-gougers.
        rig.cube(f"hand_{side}", Rig.mirror((1, 2, -1), (1, 3, 1), sign), (1, 3, 1), key=f"finger_{side}_a")
        rig.cube(f"hand_{side}", Rig.mirror((1, 2.5, 0), (1, 2, 1), sign), (1, 2, 1), key=f"finger_{side}_b")

    rig.pair(arm)

    def leg(side, sign):
        rig.bone(f"leg_{side}", "body", (sign * 0.5, 4, 0))
        rig.cube(f"leg_{side}", Rig.mirror((0, 0, -0.5), (1, 4, 1), sign), (1, 4, 1), key=f"leg_{side}")
        rig.cube(f"leg_{side}", Rig.mirror((0, 0, -1.5), (1, 1, 1), sign), (1, 1, 1), key=f"foot_{side}")

    rig.pair(leg)

    rig.bone("leaves_back", "body", (0, 7, 0.5), (20, 0, 0))
    rig.cube("leaves_back", (-1.5, 6, 0.5), (3, 2, 1), key="leaves_back")
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    d = skin.d
    twigs = ["torso", "arm_left", "arm_right", "leg_left", "leg_right", "sprig_stem"]
    skin.skin(twigs, TWIG, bevel=0.85, dither=0.0)
    skin.skin(["knot", "face", "foot_left", "foot_right"], BARK, bevel=0.85, dither=0.0)
    # Bark grain: a darker line down each twig, uneven, so the twigs read as wood and not as sticks of paint.
    for key in twigs + ["face"]:
        f = rig.faces(key)
        for name in ("north", "south", "east", "west"):
            x0, y0, w, h = f[name]
            for yy in range(h):
                if rigkit._hash(x0, y0 + yy, 9) % 3 == 0:
                    d.point((x0 + (yy % max(1, w)), y0 + yy), fill=BARK_DARK if key == "face" else WOOD)
    skin.skin(["leaf_left", "leaf_right", "sprig_leaves", "leaves_back"], LEAF, top=LEAF_LIT, bevel=0.9, dither=0.0)
    for key in ("leaves_back", "sprig_leaves"):
        x0, y0, w, h = rig.faces(key)["north"]
        d.point((x0 + w // 2, y0), fill=LEAF_LIT)
    # Fingers: twig with pale sharp tips.
    fingers = rig.keys("finger_")
    skin.skin(fingers, TWIG, bevel=0.0, dither=0.0)
    skin.tip(fingers, CLAW, rows=1)

    # The face: flat bark with two small brown eyes, a notch of a mouth.
    x0, y0, w, h = rig.faces("face")["north"]
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=WOOD)
    d.line([(x0, y0), (x0 + w - 1, y0)], fill=BARK)          # a bark brow
    d.point((x0 + 1, y0 + 1), fill=EYE)
    d.point((x0 + w - 2, y0 + 1), fill=EYE)
    return skin


# ── animation ────────────────────────────────────────────────────────────────


def rot(*frames):
    return {"rotation": kf(*frames)}


def anims():
    clips = {}
    I = 3.0
    clips["idle"] = clip(I, {
        "body": rot((0, (0, 0, 2)), (I / 2, (0, 0, -2)), (I, (0, 0, 2))),
        "head": rot((0, (0, 0, 0)), (1.0, (0, 14, 4)), (1.6, (0, 14, 4)), (2.2, (0, -8, 0)), (I, (0, 0, 0))),
        "leaf_left": osc(I, 8, axis="z", phase=0.0),
        "leaf_right": osc(I, 8, axis="z", phase=0.3),
        "sprig": osc(I, 6, axis="x", phase=0.1),
        "leaves_back": osc(I, 5, axis="x", phase=0.2),
    })

    W = 0.4
    clips["walk"] = clip(W, {
        "leg_left": osc(W, 28, phase=0.0), "leg_right": osc(W, 28, phase=0.5),
        "arm_left": osc(W, 18, phase=0.5), "arm_right": osc(W, 18, phase=0.0),
        "body": rot((0, (0, 0, 3)), (W / 2, (0, 0, -3)), (W, (0, 0, 3))),
    })

    R = 0.24
    clips["run"] = clip(R, {
        "leg_left": osc(R, 45, phase=0.0), "leg_right": osc(R, 45, phase=0.5),
        "arm_left": osc(R, 30, phase=0.5), "arm_right": osc(R, 30, phase=0.0),
        "body": rot((0, (14, 0, 0)), (R, (14, 0, 0))),
        "head": rot((0, (-10, 0, 0)), (R, (-10, 0, 0))),
        "leaf_left": rot((0, (-20, 0, 0)), (R, (-20, 0, 0))),
        "leaf_right": rot((0, (-20, 0, 0)), (R, (-20, 0, 0))),
    })

    # climb: pressed to the bark, hand over hand, feet pushing.
    C = 0.6
    clips["climb"] = clip(C, {
        "body": rot((0, (-12, 0, 0)), (C, (-12, 0, 0))),
        "arm_left": rot((0, (-160, 0, 10)), (C / 2, (-120, 0, 10)), (C, (-160, 0, 10))),
        "arm_right": rot((0, (-120, 0, -10)), (C / 2, (-160, 0, -10)), (C, (-120, 0, -10))),
        "leg_left": osc(C, 30, phase=0.5), "leg_right": osc(C, 30, phase=0.0),
    })

    # hide: gone still against the bark — limbs in, head turned to the tree, leaves flat.
    H = 4.0
    clips["hide"] = clip(H, {
        "body": rot((0, (6, 0, 0)), (H, (6, 0, 0))),
        "head": rot((0, (10, 70, 0)), (H, (10, 70, 0))),
        "arm_left": rot((0, (-25, 0, -18)), (H, (-25, 0, -18))),
        "arm_right": rot((0, (-25, 0, 18)), (H, (-25, 0, 18))),
        "leg_left": rot((0, (-10, 0, -4)), (H, (-10, 0, -4))),
        "leg_right": rot((0, (-10, 0, 4)), (H, (-10, 0, 4))),
        "leaf_left": rot((0, (0, 0, 30)), (H, (0, 0, 30))),
        "leaf_right": rot((0, (0, 0, -30)), (H, (0, 0, -30))),
    })

    # defend: claws up and out, body stiff, leaves bristling.
    Df = 1.2
    clips["defend"] = clip(Df, {
        "body": rot((0, (-4, 0, 0)), (Df, (-4, 0, 0))),
        "arm_left": rot((0, (-130, 0, -30)), (Df / 2, (-140, 0, -34)), (Df, (-130, 0, -30))),
        "arm_right": rot((0, (-130, 0, 30)), (Df / 2, (-140, 0, 34)), (Df, (-130, 0, 30))),
        "hand_left": rot((0, (-20, 0, 0)), (Df, (-20, 0, 0))),
        "hand_right": rot((0, (-20, 0, 0)), (Df, (-20, 0, 0))),
        "leaf_left": rot((0, (0, 0, -25)), (Df / 2, (0, 0, -32)), (Df, (0, 0, -25))),
        "leaf_right": rot((0, (0, 0, 25)), (Df / 2, (0, 0, 32)), (Df, (0, 0, 25))),
        "sprig": rot((0, (-25, 0, 0)), (Df, (-25, 0, 0))),
    })

    # lockpick: both hands at the lock, fingers working.
    L = 0.8
    clips["lockpick"] = clip(L, {
        "body": rot((0, (12, 0, 0)), (L, (12, 0, 0))),
        "head": rot((0, (18, 0, 0)), (L / 2, (18, 6, 0)), (L, (18, 0, 0))),
        "arm_left": rot((0, (-80, 0, -6)), (L / 2, (-86, 0, -4)), (L, (-80, 0, -6))),
        "arm_right": rot((0, (-86, 0, 6)), (L / 2, (-80, 0, 4)), (L, (-86, 0, 6))),
        "hand_left": rot((0, (0, 0, 0)), (0.2, (-25, 0, 0)), (0.4, (10, 0, 0)), (0.6, (-25, 0, 0)), (L, (0, 0, 0))),
        "hand_right": rot((0, (-25, 0, 0)), (0.2, (10, 0, 0)), (0.4, (-25, 0, 0)), (0.6, (10, 0, 0)), (L, (-25, 0, 0))),
    })

    # curious: leans in and cocks its head at something.
    Cu = 1.2
    clips["curious"] = clip(Cu, {
        "body": rot((0, (0, 0, 0)), (0.3, (20, 0, 0)), (0.9, (20, 0, 0)), (Cu, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.35, (6, 0, 18)), (0.8, (6, 0, -10)), (Cu, (0, 0, 0))),
        "arm_right": rot((0, (0, 0, 0)), (0.4, (-40, 0, 0)), (0.9, (-40, 0, 0)), (Cu, (0, 0, 0))),
    }, loop=False)

    # attack: one quick swipe at the eyes.
    A = 0.35
    clips["attack"] = clip(A, {
        "arm_right": rot((0, (0, 0, 0)), (0.08, (-170, 0, 20)), (0.2, (-40, 0, -20)), (A, (0, 0, 0))),
        "hand_right": rot((0, (0, 0, 0)), (0.12, (-40, 0, 0)), (A, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (0.12, (10, 12, 0)), (A, (0, 0, 0))),
    }, loop=False)

    # pickup: reaches down with both hands for what it was offered.
    P = 0.7
    clips["pickup"] = clip(P, {
        "body": rot((0, (0, 0, 0)), (0.25, (30, 0, 0)), (0.5, (0, 0, 0)), (P, (0, 0, 0))),
        "arm_left": rot((0, (0, 0, 0)), (0.25, (-60, 0, 0)), (0.5, (-30, 0, 10)), (P, (0, 0, 0))),
        "arm_right": rot((0, (0, 0, 0)), (0.25, (-60, 0, 0)), (0.5, (-30, 0, -10)), (P, (0, 0, 0))),
    }, loop=False)

    Hi = 0.3
    clips["hit"] = clip(Hi, {
        "body": rot((0, (0, 0, 0)), (0.07, (-14, 0, 8)), (Hi, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.07, (-12, 10, 0)), (Hi, (0, 0, 0))),
    }, loop=False)

    # death: it comes apart — falls, and the limbs splay like dropped twigs.
    D = 1.0
    clips["death"] = dict(clip(D, {
        "body": rot((0, (0, 0, 0)), (0.5, (80, 0, 10)), (D, (90, 0, 14))),
        "root": {"position": kf((0, (0, 0, 0)), (D, (0, -2, 0)))},
        "arm_left": rot((0, (0, 0, 0)), (D, (-40, 0, -60))),
        "arm_right": rot((0, (0, 0, 0)), (D, (-30, 0, 70))),
        "leg_left": rot((0, (0, 0, 0)), (D, (20, 0, -30))),
        "leg_right": rot((0, (0, 0, 0)), (D, (-15, 0, 35))),
        "head": rot((0, (0, 0, 0)), (D, (20, 0, 40))),
    }, loop=False), loop="hold_on_last_frame")
    return clips


EXTRA = ("run", "climb", "hide", "defend", "lockpick", "curious", "attack", "pickup", "hit", "death")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool="bowtruckle_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, colours=20, definition=False, shared_clips=False, extra_clips=EXTRA)


if __name__ == "__main__":
    sys.exit(main())
