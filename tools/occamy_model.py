#!/usr/bin/env python3
"""The Occamy: rig, plumage and animations.

The Occamy is a **winged serpent with a bird's head, and no legs at all**. It does not stand and it
does not walk: the back two thirds of the body lies along the ground, the front third rears, and it
travels by slithering or by flying. Every earlier cut of this rig got that wrong in the same
direction — the first was an upright barrel-chested bird on long legs, the second the same animal
with shorter ones. The book's phrase is "plumed, two-legged winged creature with a serpentine
body"; read literally it produces a chicken, and the films' animal has no visible feet. There are
none here, and `main()` refuses to write any cube that names one.

    root
      seg_01 (reared forebody) -- neck_01 -- neck_02 -- head -- beak_upper / beak_lower / crest_*
        - wing_{l,r} -- wing_{l,r}_tip -- primary_{l,r}{0..3}
        - seg_02 -- seg_03 -- seg_04 -- seg_05 -- seg_06 -- tail_fan

Bone names are load-bearing and kept: `root`, `head`, `seg_01`..`seg_06` already carry animation
channels, and a clip addressing a bone that no longer exists fails **silently** in GeckoLib.

Two rules this file exists to hold:

**1. Whole-texel cube extents.** GeckoLib lays box UV out from `Math.floor(size)`
(`BakedModelFactory.buildQuad`) while `boxuv.Packer`/`faces()` round *up*, so a cube of extent 6.37
is painted at `u+7` and sampled at `u+6`: every face reads a strip of its neighbour and the top and
bottom faces read the island corner the box layout never fills, which is transparent. Any extent
under 1.0 floors to **zero** and collapses the cube. An earlier cut of this rig had fractional
extents throughout and `tools/uv_check.py` found **253** faults in it. Extents here are integers,
and `main()` refuses to write a rig the checker would reject rather than trusting anyone to run it.
Fractional *positions* are fine — only extents reach the UV.

**2. Sculpt by world position, not by a rotation chain.** Every cube's world interval is written out
below and overlaps its neighbour's in both axes the body curves through: a spine whose segments butt
end to end reads as a staircase with daylight through each joint, and stacking base pitches down a
chain hides its own error (one earlier cut buried the whole lower neck inside the ribs). Only the
plumes and the wings carry base rotations, where a fan is the point. Judge with three orthographic
views — both of those faults are invisible from 3/4.

Facing north (-Z). Wings are modelled SPREAD and folded by the clips, on **roll only** — a fold
mixing yaw and roll is the rotation-order landmine that splits a wing into floating slabs.

The declared body is 1.7 x 1.9, so the box is 27.2 x 30.4 units. A resting serpent does not fill a
1.9-block box standing up, and it is not meant to: the reared head crowns it and the crest sits just
proud, while the length runs well past it, which is correct — a hitbox is the body, not the reach.
The creature is choranaptyxic and is seen from 0.35x to 2.2x, so it has to read at both.

Run from the repo root:  python tools/occamy_model.py [--force]
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces, gradient, mottle  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/occamy.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/occamy.animation.json"
SKIN = f"{ASSETS}/textures/entity/occamy.png"
DEFN = "src/main/resources/data/wizards_and_beasts/creatures/occamy.json"
MARKER = marker("occamy_model.py")

TEX = 128

# Silver-blue plumage into a plum crest, gold beak. The body leads with the blue: an earlier cut led
# with the silver and the whole animal came out white, the blue reading as dirt on it.
SILVER = hx("#C2D3E2")
SILVER_LT = hx("#E9F3FB")
SILVER_DK = hx("#8DA3B8")
BLUE = hx("#6C9FD0")
BLUE_DK = hx("#3D6796")
BLUE_LT = hx("#9CC6EA")
PLUM = hx("#78447A")
PLUM_LT = hx("#A96FA6")
GOLD = hx("#D9A63C")
GOLD_LT = hx("#F2C96A")
GOLD_DK = hx("#B0821F")
EYE_DK = hx("#1B2230")
EYE_LT = hx("#FFE9A8")


def snap(size):
    """Round a cube extent to a whole texel, never below one. See rule 1 in the module docstring."""
    return max(1.0, float(round(size)))


def mirror_x(x0, width):
    """The left-side origin reflected onto the right, for a cube of this width."""
    return -(x0 + width)


# --------------------------------------------------------------------------- the rig

# Wing geometry, shared by the bone pivots and the cubes so they cannot drift apart.
WING_ROOT_X, WING_ARM, WING_OUTER = 3.5, 6.0, 5.0
WING_Y, WING_Z = 12.0, 0.0
PRIMARY_X = WING_ROOT_X + WING_ARM + WING_OUTER - 2.0   # primaries overlap the outer arm
PRIMARIES = ((6.0, 4.0, -20), (7.0, 4.0, -6), (6.0, 4.0, 8), (5.0, 3.0, 22))  # length, chord, yaw


# bone -> (parent, pivot, base rotation or None)
def build_bones():
    B = [
        ("root", None, (0, 0, 0), None),
        ("seg_01", "root", (0, 10.0, 0.0), None),
        ("neck_01", "seg_01", (0, 14.0, -6.0), None),
        ("neck_02", "neck_01", (0, 19.0, -9.0), None),
        ("head", "neck_02", (0, 22.0, -12.0), None),
        ("beak_upper", "head", (0, 23.0, -18.0), None),
        ("beak_lower", "head", (0, 22.0, -18.0), (-2, 0, 0)),
    ]
    # --- the crest: a fan of five plumes sweeping back off the crown -------------------
    # Pitched back on X and splayed on Z. This is the feature that identifies the animal in a
    # screenshot at any of its four sizes, so it is the one place cube count is spent freely.
    B += [
        ("crest_c", "head", (0, 26.0, -13.0), (35, 0, 0)),
        ("crest_l1", "head", (1.5, 26.0, -13.0), (32, 0, -15)),
        ("crest_r1", "head", (-1.5, 26.0, -13.0), (32, 0, 15)),
        ("crest_l2", "head", (2.5, 25.0, -13.0), (27, 0, -31)),
        ("crest_r2", "head", (-2.5, 25.0, -13.0), (27, 0, 31)),
    ]
    # --- wings, set behind the neck on the reared forebody ------------------------------
    for side, sx in (("l", 1), ("r", -1)):
        B.append((f"wing_{side}", "seg_01", (WING_ROOT_X * sx, WING_Y, WING_Z), (0, 0, 8 * sx)))
        B.append((f"wing_{side}_tip", f"wing_{side}",
                  ((WING_ROOT_X + WING_ARM) * sx, WING_Y, WING_Z), (0, 0, 6 * sx)))
        for i, (_length, _chord, yaw) in enumerate(PRIMARIES):
            B.append((f"primary_{side}{i}", f"wing_{side}_tip",
                      (PRIMARY_X * sx, WING_Y, WING_Z), (0, yaw * sx, 0)))
    # --- the serpent: forebody reared, the rest lying along the ground -----------------
    # No legs, so this coil *is* the animal's support: seg_03 back sits at y 0.
    B += [
        ("seg_02", "seg_01", (0, 6.0, 6.0), None),
        ("seg_03", "seg_02", (0, 3.0, 14.0), None),
        ("seg_04", "seg_03", (0, 2.0, 21.0), None),
        ("seg_05", "seg_04", (0, 1.5, 28.0), None),
        ("seg_06", "seg_05", (0, 1.0, 34.0), None),
        ("tail_fan", "seg_06", (0, 1.0, 40.0), None),
    ]
    return B


# (bone, part, origin, size, role) — one entry per cube. Every extent is a whole texel, and the
# world interval each occupies is in the comments so the overlaps can be checked by reading.
def build_cubes():
    C = [
        # --- reared forebody: y 7..14, z -6..5. Thin, not a barrel chest --------------
        ("seg_01", "body", (-3.5, 7, -6), (7, 7, 11), "scale"),
        ("seg_01", "belly", (-2.5, 6, -5), (5, 1, 9), "belly"),
        ("seg_01", "ridge", (-0.5, 13.5, -5), (1, 3, 9), "ridge"),
        ("seg_01", "ruff", (-3.5, 11, -10), (7, 3, 4), "plume"),
        # --- neck: an S clear of the body. y 13..19, then 18..23 ----------------------
        ("neck_01", "column", (-2.5, 13, -10), (5, 6, 5), "scale"),
        ("neck_01", "ruff", (-3, 16, -11), (6, 2, 4), "plume"),
        ("neck_02", "column", (-2, 18, -12), (4, 5, 4), "scale"),
        # --- head: small, and most of its read is beak and crest. y 21..26 -----------
        ("head", "cranium", (-2.5, 21, -18), (5, 5, 7), "head"),
        ("head", "brow", (-2, 26, -17), (4, 1, 4), "plume"),
        ("head", "jowl_l", (2.5, 22, -17), (1, 3, 4), "plume"),
        ("head", "jowl_r", (-3.5, 22, -17), (1, 3, 4), "plume"),
        # --- beak: long, slender and pointed, not a raptor's hook --------------------
        ("beak_upper", "upper", (-1, 23, -27), (2, 1, 9), "beak"),
        ("beak_upper", "base", (-1.5, 23, -20), (3, 2, 3), "beak"),
        ("beak_lower", "lower", (-1, 22, -25), (2, 1, 7), "beak"),
        # --- crest fan ----------------------------------------------------------------
        ("crest_c", "plume", (-0.5, 26, -14), (1, 8, 3), "crest"),
        ("crest_l1", "plume", (1, 26, -14), (1, 7, 3), "crest"),
        ("crest_r1", "plume", (-2, 26, -14), (1, 7, 3), "crest"),
        ("crest_l2", "plume", (2, 25, -14), (1, 6, 2), "crest"),
        ("crest_r2", "plume", (-3, 25, -14), (1, 6, 2), "crest"),
        # --- the descent: y 3..9, then flat on the floor from seg_03 back -------------
        ("seg_02", "body", (-3, 3, 4), (6, 6, 9), "scale"),
        ("seg_02", "belly", (-2, 2, 5), (4, 1, 7), "belly"),
        ("seg_02", "ridge", (-0.5, 8.5, 5), (1, 2, 7), "ridge"),
        ("seg_03", "body", (-2.5, 0, 12), (5, 5, 9), "scale"),
        ("seg_03", "ridge", (-0.5, 4.5, 13), (1, 2, 7), "ridge"),
        ("seg_04", "body", (-2, 0, 20), (4, 4, 8), "scale"),
        ("seg_05", "body", (-1.5, 0, 27), (3, 3, 7), "scale"),
        ("seg_06", "body", (-1, 0, 33), (2, 2, 7), "scale"),
        # --- tail plume fan: a feathered serpent ends in feathers ---------------------
        ("tail_fan", "plume_c", (-1, 0, 39), (2, 2, 9), "primary"),
        ("tail_fan", "plume_l", (0.5, 0, 39), (2, 2, 7), "primary"),
        ("tail_fan", "plume_r", (-2.5, 0, 39), (2, 2, 7), "primary"),
    ]
    # --- wings: modest. Thickness matters more than span — at one or two units deep these
    # read edge-on as pencil lines, which is an aeroplane, not a bird. Each section overlaps
    # the one outboard of it so the arm never shows a gap at the joint.
    for side, sx in (("l", 1), ("r", -1)):
        arm_x, outer_x = WING_ROOT_X, WING_ROOT_X + WING_ARM - 1.0
        C.append((f"wing_{side}", "arm", _x(arm_x, 6, sx) + (10.5, -5), (6, 3, 10), "wing"))
        C.append((f"wing_{side}", "covert", _x(arm_x + 0.5, 5, sx) + (13.5, -4), (5, 1, 8), "plume"))
        C.append((f"wing_{side}_tip", "arm", _x(outer_x, 5, sx) + (10.5, -4.5), (5, 3, 9), "wing"))
        for i, (length, chord, _yaw) in enumerate(PRIMARIES):
            C.append((f"primary_{side}{i}", "feather",
                      _x(PRIMARY_X, length, sx) + (11, -1 - chord / 2),
                      (length, 2, chord), "primary"))
    return C


def _x(x0, width, sx):
    """Left-authored x origin, reflected for the right side. Returns a 1-tuple to append y/z onto."""
    return (x0 if sx > 0 else mirror_x(x0, width),)


def build_geo(packer, bones, cubes):
    by_bone = {}
    for bone, part, origin, size, _role in cubes:
        by_bone.setdefault(bone, []).append((f"{bone}.{part}", origin, size))

    out = []
    for name, parent, pivot, rot in bones:
        b = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = [round(v, 2) for v in rot]
        if name in by_bone:
            b["cubes"] = [{"origin": [round(v, 2) for v in origin],
                           "size": [snap(v) for v in size],
                           "uv": list(packer.placed[key][0])}
                          for key, origin, size in by_bone[name]]
        out.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.occamy",
                "texture_width": TEX, "texture_height": TEX,
                # Sized for the choranaptyxic maximum (2.2x), or a grown Occamy is culled while a
                # player is still looking straight at it.
                "visible_bounds_width": 8, "visible_bounds_height": 6,
                "visible_bounds_offset": [0, 1.4, 0],
            },
            "bones": out,
        }],
    }


# --------------------------------------------------------------------------- the skin

# The fourth number is how far the top face is lifted toward white. It is per-role because a uniform
# lift blew the plumes and the crest out into near-white candles: a 1-unit-thick plume is almost
# *all* top face, so a body's worth of highlight lands on the whole feather.
ROLE_PALETTE = {
    "scale":   (BLUE, [BLUE_DK, SILVER, BLUE_LT], 14, 0.42),
    "head":    (BLUE_LT, [SILVER, BLUE, SILVER_DK], 12, 0.30),
    "plume":   (SILVER, [BLUE_LT, SILVER_DK, BLUE], 16, 0.18),
    "belly":   (SILVER_LT, [SILVER, BLUE_LT], 10, 0.20),
    # The ridge was PLUM and read as a row of purple spikes down the back — a mohawk, not plumage.
    # It is the body colour a shade darker now; the plum belongs to the crest alone, which is what
    # the crest is *for*.
    "ridge":   (BLUE_DK, [BLUE, SILVER_DK], 14, 0.22),
    "wing":    (BLUE, [BLUE_DK, SILVER, BLUE_LT], 14, 0.34),
    "primary": (BLUE_DK, [BLUE, SILVER], 12, 0.16),
    "crest":   (PLUM, [PLUM_LT, BLUE_DK], 16, 0.10),
    "beak":    (GOLD, [GOLD_LT, GOLD_DK], 8, 0.24),
}


def paint(packer, cubes):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    for bone, part, _origin, _size, role in cubes:
        key = f"{bone}.{part}"
        uv, size = packer.placed[key]
        f = faces(uv, size)
        seed = sum(ord(c) for c in key)
        base, pal, density, top_mix = ROLE_PALETTE[role]

        for fname, rect in f.items():
            col = base
            if fname == "top":
                col = mix(base, SILVER_LT, top_mix)
            elif fname == "bottom":
                col = shade(base, 0.64)
            mottle(d, rect, col, pal, density, seed)

        # Feathered parts ramp toward a paler tip — what makes plumage read as plumage rather than a
        # painted box. The crest ramps within its own plum: a plum plume with a white tip is a candle.
        if role in ("primary", "crest"):
            tip = SILVER if role == "primary" else PLUM_LT
            root = BLUE_DK if role == "primary" else shade(PLUM, 0.78)
            for fname in ("top", "bottom"):
                gradient(d, f[fname], root, tip, mix)
            x0, y0, w, h = f["top"]
            if h > 1:
                d.line([(x0 + w // 2, y0), (x0 + w // 2, y0 + h - 1)],
                       fill=PLUM if role == "primary" else PLUM_LT)
        if role == "wing":
            gradient(d, f["top"], BLUE_DK, SILVER, mix)
            gradient(d, f["bottom"], SILVER_DK, SILVER_LT, mix)
        if role == "belly":
            gradient(d, f["bottom"], SILVER_LT, SILVER, mix)

        # Eyes are painted onto the cranium's side faces, the way the phoenix does it — a one-unit
        # eye cube is a slab that z-fights at every size this creature takes.
        if key == "head.cranium":
            for fname in ("east", "west"):
                x0, y0, w, h = f[fname]
                ex, ey = x0 + w // 2, y0 + h // 2 - 1
                d.rectangle([ex - 1, ey - 1, ex, ey], fill=EYE_DK)
                d.point((ex, ey - 1), fill=EYE_LT)

    return img


# --------------------------------------------------------------------------- the clips

def kf(*pairs):
    return {str(t): list(v) for t, v in pairs}


CREST = (("crest_c", 35, 0), ("crest_l1", 32, -15), ("crest_r1", 32, 15),
         ("crest_l2", 27, -31), ("crest_r2", 27, 31))

BODY = ("seg_02", "seg_03", "seg_04", "seg_05", "seg_06", "tail_fan")


def build_anim():
    """`idle`, `walk` and `attack` — the three names the beast controller asks for.

    `walk` is a **slither**, not a gait: there are no legs to step with, so forward motion is a
    lateral wave travelling from the reared forebody down to the tail tip, each segment repeating
    its parent's sway a beat later. That lag is the whole thing — in phase, a segmented body moves
    like one rigid stick. There is also no vertical bob, which a walk cycle would normally carry: a
    body sliding on its belly does not rise and fall on a step it never takes.

    Neck and head values are offsets from zero, because the rest pose carries no base rotation on
    that chain. Wings fold on roll only.
    """
    fold = 82  # + the 8-degree rest roll = tucked flat against the ribs

    idle = {
        "loop": True, "animation_length": 4.0,
        "bones": {
            "root": {"position": kf((0, (0, 0, 0)), (2.0, (0, 0.3, 0)), (4.0, (0, 0, 0)))},
            "seg_01": {"rotation": kf((0, (0, -2, 0)), (2.0, (2, 2, 0)), (4.0, (0, -2, 0)))},
            "neck_01": {"rotation": kf((0, (0, 0, 0)), (1.4, (3, 8, 0)),
                                       (2.8, (-3, -7, 0)), (4.0, (0, 0, 0)))},
            "neck_02": {"rotation": kf((0, (0, 0, 0)), (1.4, (2, 10, 0)),
                                       (2.8, (-3, -9, 0)), (4.0, (0, 0, 0)))},
            "head": {"rotation": kf((0, (0, 0, 0)), (1.4, (-4, 16, 0)),
                                    (2.8, (4, -14, 0)), (4.0, (0, 0, 0)))},
            "beak_lower": {"rotation": kf((0, (-2, 0, 0)), (2.6, (-11, 0, 0)), (4.0, (-2, 0, 0)))},
        },
    }
    walk = {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "seg_01": {"rotation": kf((0, (0, -7, 0)), (0.5, (0, 7, 0)), (1.0, (0, -7, 0)))},
            "neck_01": {"rotation": kf((0, (0, 5, 0)), (0.5, (0, -5, 0)), (1.0, (0, 5, 0)))},
            "head": {"rotation": kf((0, (0, -3, 0)), (0.5, (0, 3, 0)), (1.0, (0, -3, 0)))},
        },
    }
    attack = {
        "loop": False, "animation_length": 0.8,
        "bones": {
            # A heron's strike: coil the neck back, then drive the beak through.
            "neck_01": {"rotation": kf((0, (0, 0, 0)), (0.2, (-26, 0, 0)),
                                       (0.42, (32, 0, 0)), (0.8, (0, 0, 0)))},
            "neck_02": {"rotation": kf((0, (0, 0, 0)), (0.2, (-18, 0, 0)),
                                       (0.42, (26, 0, 0)), (0.8, (0, 0, 0)))},
            "head": {"rotation": kf((0, (0, 0, 0)), (0.2, (16, 0, 0)),
                                    (0.42, (-22, 0, 0)), (0.8, (0, 0, 0)))},
            "beak_lower": {"rotation": kf((0, (-2, 0, 0)), (0.18, (-38, 0, 0)),
                                          (0.45, (-6, 0, 0)), (0.8, (-2, 0, 0)))},
            "seg_01": {"rotation": kf((0, (0, 0, 0)), (0.2, (-9, 0, 0)),
                                      (0.42, (8, 0, 0)), (0.8, (0, 0, 0)))},
        },
    }

    # The crest: flicks on idle, flares wide on the strike. Each plume keeps its own rest pose, so
    # the fan opens rather than collapsing onto one angle.
    for i, (bone, pitch, roll) in enumerate(CREST):
        lag = 0.12 * i
        idle["bones"][bone] = {"rotation": kf(
            (0, (pitch, 0, roll)),
            (round(0.9 + lag, 2), (pitch - 9, 0, roll * 1.3)),
            (round(2.1 + lag, 2), (pitch + 4, 0, roll * 0.85)),
            (4.0, (pitch, 0, roll)))}
        attack["bones"][bone] = {"rotation": kf(
            (0, (pitch, 0, roll)), (0.18, (pitch + 22, 0, roll * 1.9)), (0.8, (pitch, 0, roll)))}
        walk["bones"][bone] = {"rotation": kf(
            (0, (pitch, 0, roll)), (0.5, (pitch - 4, 0, roll)), (1.0, (pitch, 0, roll)))}

    # The body wave. Amplitude grows toward the thin end, and each segment lags the one in front.
    for i, bone in enumerate(BODY):
        amp = 5 + 2 * i
        idle["bones"][bone] = {"rotation": kf(
            (0, (0, -amp * 0.5, 0)), (round(2.0 + 0.16 * i, 2), (0, amp * 0.5, 0)),
            (4.0, (0, -amp * 0.5, 0)))}
        walk["bones"][bone] = {"rotation": kf(
            (0, (0, -amp, 0)), (round(0.5 + 0.07 * i, 2), (0, amp, 0)), (1.0, (0, -amp, 0)))}

    # Wings: tucked at rest, a small settle in idle, snapped open on the lunge.
    for side, s in (("l", 1), ("r", -1)):
        idle["bones"][f"wing_{side}"] = {"rotation": kf(
            (0, (0, 0, fold * s)), (2.0, (0, 0, (fold - 6) * s)), (4.0, (0, 0, fold * s)))}
        idle["bones"][f"wing_{side}_tip"] = {"rotation": kf(
            (0, (0, 0, 56 * s)), (2.0, (0, 0, 50 * s)), (4.0, (0, 0, 56 * s)))}
        walk["bones"][f"wing_{side}"] = {"rotation": kf(
            (0, (0, 0, fold * s)), (0.5, (0, 0, (fold - 4) * s)), (1.0, (0, 0, fold * s)))}
        walk["bones"][f"wing_{side}_tip"] = {"rotation": kf(
            (0, (0, 0, 56 * s)), (1.0, (0, 0, 56 * s)))}
        attack["bones"][f"wing_{side}"] = {"rotation": kf(
            (0, (0, 0, fold * s)), (0.2, (0, 0, 10 * s)),
            (0.45, (0, 0, 24 * s)), (0.8, (0, 0, fold * s)))}
        attack["bones"][f"wing_{side}_tip"] = {"rotation": kf(
            (0, (0, 0, 56 * s)), (0.2, (0, 0, 4 * s)), (0.8, (0, 0, 56 * s)))}
        for i in range(len(PRIMARIES)):
            idle["bones"][f"primary_{side}{i}"] = {"rotation": kf(
                (0, (0, 0, 22 * s)), (4.0, (0, 0, 22 * s)))}
            attack["bones"][f"primary_{side}{i}"] = {"rotation": kf(
                (0, (0, 0, 22 * s)), (0.25, (0, 0, -10 * s)), (0.8, (0, 0, 22 * s)))}

    return {"format_version": "1.8.0", "animations": {
        "animation.occamy.idle": idle,
        "animation.occamy.walk": walk,
        "animation.occamy.attack": attack,
    }}


# --------------------------------------------------------------------------- driver

# Words that would mean this rig had grown feet again. Asserted rather than merely absent, because
# "two-legged" is in the book text and the rig has now been rebuilt away from a chicken twice.
FOOTLESS = ("thigh", "shin", "foot", "leg", "claw", "talon", "toe")


def drop_placeholder_marker(path):
    """The rig is hand-built, so the `PLACEHOLDER box rig` comment has to go.

    `RigMarkerConsistencyTest` asserts marker <-> generated-box-rig-shape in both directions, so
    leaving it on a hand-built rig turns the suite red — which is the point of the test, and the
    reason this is done here rather than remembered by hand.
    """
    if not os.path.exists(path):
        return False
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if "PLACEHOLDER box rig" not in str(data.get("_comment", "")):
        return False
    del data["_comment"]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    bones = build_bones()
    cubes = build_cubes()

    named = {b[0] for b in bones}
    problems = []
    for bone, part, _o, size, _r in cubes:
        if bone not in named:
            problems.append(f"cube {bone}.{part} has no bone")
        for axis, extent in zip("xyz", size):
            # Rule 1. Refuse to write a rig `tools/uv_check.py` would reject, rather than writing it
            # and relying on someone running the checker.
            if abs(extent - round(extent)) > 1e-9 or extent < 1:
                problems.append(f"cube {bone}.{part} has a non-whole {axis} extent: {extent}")
        if any(w in bone or w in part for w in FOOTLESS):
            problems.append(f"an Occamy has no feet; drop {bone}.{part}")
    for name, _p, _pivot, _rot in bones:
        if any(w in name for w in FOOTLESS):
            problems.append(f"an Occamy has no feet; drop the bone {name}")
    if problems:
        for p in problems:
            print("ERROR: " + p, file=sys.stderr)
        return 1

    packer = Packer(TEX)
    used = packer.place_sorted([(f"{b}.{p}", s) for b, p, _o, s, _r in cubes])
    if used > TEX:
        print(f"ERROR: UV islands need {used}px of a {TEX}px sheet", file=sys.stderr)
        return 1
    if not args.force and not is_regenerable(SKIN, MARKER):
        print("occamy.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    with open(GEO, "w", encoding="utf-8") as f:
        json.dump(build_geo(packer, bones, cubes), f, indent=2)
        f.write("\n")
    with open(ANIM, "w", encoding="utf-8") as f:
        json.dump(build_anim(), f, indent=2)
        f.write("\n")
    save(posterise(paint(packer, cubes), 22), SKIN, MARKER)
    dropped = [p for p in (DEFN,) if drop_placeholder_marker(p)]

    print(f"occamy rig: {len(bones)} bones, {len(cubes)} cubes, UV {used}/{TEX}px, skin {TEX}x{TEX}")
    if dropped:
        print("dropped PLACEHOLDER marker from: " + ", ".join(dropped))
    print("now run: python tools/uv_check.py occamy")
    return 0


if __name__ == "__main__":
    sys.exit(main())
