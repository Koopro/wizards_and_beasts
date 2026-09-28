#!/usr/bin/env python3
"""The mooncalf: rig, skin, glowmask and animations.

Replaces a `creature_gen.py` box quadruped — 8 bones, 10 cubes — that got the creature's one
defining feature wrong.

**Built to the film, where the film and the book disagree.** *Fantastic Beasts and Where to Find
Them* says "its body is smooth pale grey, it has bulging round eyes on top of its head and four
spindly legs with enormous flat feet". The 2016 film keeps the eyes and the spindly legs and
overrules the rest, and the film is what this rig is matched to:

  - **Not grey.** A pale buttermilk-cream body going warmer down the flanks, with a near-white face.
  - **Blue eyes, not black.** Enormous, glossy, pale-blue irises with big dark pupils, wrapping the
    front and top of a small skull — they take up most of the head and they are the whole animal.
  - **Dark legs.** Slender charcoal legs that read almost black against the cream body, ending in
    small neat feet rather than the book's enormous pads. This is the one place the film is flatly
    contrary to the text; the brief was "like the movies", so the film wins.
  - **A long thin neck.** Nearly as long as the body and carried in a shallow S, which is most of
    what makes the silhouette recognisable at distance.

The rig this replaces had its eyes painted as two white rectangles on the *front* of the face, no
feet at all, and a chunky body on short legs — so it read as a small grey cow.

Three things worth stating:

  - **The eyes are geometry, not paint.** Two cubes bulging out past the sides and front of the
    skull, which is what makes them survive into the silhouette. Painted flat onto the head they
    vanish at any angle where the face is edge-on, and they are the only thing anyone recognises
    this creature by.
  - **Drawn larger than the hitbox.** See `OVERSIZE`. Everything below is authored against the
    rendered size, not the collision box.
  - **Whole-texel cube sizes.** `snap()` rounds every size after the fit scale, because GeckoLib lays
    box UV out from `Math.floor(size)` (`BakedModelFactory.buildQuad`) while `boxuv.Packer`/`faces()`
    round up. Fractional sizes make every face sample a strip of its neighbour, and the top and
    bottom faces sample the island's unfilled corner — which is transparent. The old rig tore on 18
    faces that way. Verify with `python tools/uv_check.py mooncalf`.
  - **LANDMINE: animation rotations are ADDITIVE over the rest rotation.** GeckoLib composes
    `bone.baseRotX() + frameSnapshot.getRotX()` (`RenderUtil.translateAndRotateMatrixForBone`), so a
    keyframe is a *delta* from the authored pose. Every clip below oscillates around 0.

`dance` is the reason this creature exists in the lore — mooncalves come out only at the full moon
and dance on their hind legs, and the patterns they tread are where crop circles come from.
`MooncalfEntity` plays it off a synced flag driven by `WerewolfMoonHandler.moonIsUp`.

Run from the repo root:  python tools/mooncalf_model.py --force
(the shipped texture carries no generator marker, so `--force` is required to replace it)
"""

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces  # noqa: E402
import bespoke_reactions  # noqa: E402

NAME = "mooncalf"
ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/{NAME}.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/{NAME}.animation.json"
SKIN = f"{ASSETS}/textures/entity/{NAME}.png"
GLOW = f"{ASSETS}/textures/entity/{NAME}_glowmask.png"
MARKER = marker("mooncalf_model.py")

TEX_W = 64

# Hitbox from ModEntities: register(..., "mooncalf", ..., 0.7f, 1.2f). Read from here rather than
# typed twice, but the rig is deliberately drawn **larger than its collision box**.
BOX_W, BOX_H = 0.7 * 16, 1.2 * 16

# The model overflows its hitbox by 42%, and that is the whole reason this creature is buildable.
#
# Box UV gives one texel per model unit, so a 1.2-block mob is 19 units tall and its skull is about
# six texels across. The reference needs eyes that are big and bulging, *narrower than the head*, and
# detailed enough to carry a pupil and a catchlight — any two of those fit in six texels and all
# three do not. Six passes proved it: bigger eyes swallowed the head, smaller ones lost the pupil,
# and a skull wide enough to contain them turned the animal into a bobblehead.
#
# Drawing at 1.7 blocks buys ~42% more texels in every direction — a nine-texel skull carrying
# five-texel eyes — which satisfies all three at once. A mob whose model overflows its collision box
# is completely ordinary (vanilla does it for the spider, the ghast and the ender dragon); the box
# stays where gameplay needs it and the art gets the room it needs.
OVERSIZE = 1.42
TARGET_HEIGHT_UNITS = BOX_H * OVERSIZE


# --------------------------------------------------------------------------- palette

# Dove grey with a blue cast, warming very slightly on the belly. An earlier pass painted this
# creature buttermilk-cream off a reference still — that photograph was shot at golden hour and the
# sunlight was doing the colouring. Under flat light the animal is grey, which is also what the book
# says, so both sources agree once the lighting is discounted.
HIDE = hx("#A8A8B4")
HIDE_LT = hx("#C0C0CA")
HIDE_DK = hx("#7E7E8A")
FACE = hx("#B2B2BC")         # head and throat, a shade lighter than the flanks
BELLY = hx("#BAB6BA")        # faintly warm
LEG = hx("#8A8A96")
LEG_DK = hx("#5E5E6A")       # toes and the soles of the feet
LEG_LT = hx("#A2A2AE")
MUZZLE = hx("#B8B2B6")
NOSTRIL = hx("#2E2A30")
EYE = hx("#6E8CA8")          # blue-grey iris, and only a ring of it — the pupil takes the rest
EYE_DK = hx("#4C6580")
PUPIL = hx("#0C0E14")
EYE_RIM = hx("#8E8E9A")
EAR_IN = hx("#B49A9C")       # pink inner ear
GLINT = hx("#EDF2F8")

ROLES = {}


def cube(key, role, origin, size, rot=None, pivot=None):
    ROLES[key] = role
    return (key, origin, size, rot, pivot)


def mirror_cube(key, role, origin, size, rot=None, pivot=None):
    """The same cube on the other side: negate X, and negate yaw/roll with it."""
    return cube(key, role, (-(origin[0] + size[0]), origin[1], origin[2]), size,
                (rot[0], -rot[1], -rot[2]) if rot else None,
                (-pivot[0], pivot[1], pivot[2]) if pivot else None)


# --------------------------------------------------------------------------- rig
#
# Design space: 16 units to a block, authored facing -Z with the feet on y=0, then fitted.
#
# The proportion is the brief. Legs take up more than half the standing height, the body is a small
# barrel rather than a torso, and the neck carries the head high and upright — that is the film
# silhouette, and it is what a short-legged box quadruped cannot be recoloured into.

FOOT_H = 1.2
LEG_TOP = 7.4           # hip and shoulder height — the legs are the *short* part of this animal
BODY_Y = 6.6
NECK_Y = 10.4
HEAD_Y = 19.2

# Leg positions: (x, z) of each leg's centre. Narrow track; the reference stands with its legs
# almost under the centre line, which is part of why it reads as so delicate.
LEGS = {
    "fl": (1.8, -2.4),
    "fr": (-1.8, -2.4),
    "bl": (2.1, 3.0),
    "br": (-2.1, 3.0),
}


def build_bones():
    B = [("root", None, (0, 0, 0), None, [])]

    # --- body -------------------------------------------------------------------------
    # Small and pear-shaped: deep haunches tapering to a narrow chest. This animal is mostly neck,
    # and the body is only there to hang it off.
    B.append(("body", "root", (0, BODY_Y, 0), (-6, 0, 0), [
        cube("body.rump", "hide", (-3.0, BODY_Y - 0.4, 0.4), (6.0, 5.6, 4.6)),
        cube("body.chest", "hide", (-2.2, BODY_Y + 0.4, -3.8), (4.4, 4.6, 4.4)),
    ]))

    # A long thin tapering tail, carried down and curled back. The earlier stub was wrong — the
    # reference has a proper rat-like tail nearly as long as the body.
    B.append(("tail", "body", (0, BODY_Y + 3.0, 4.8), (-52, 0, 0), [
        cube("tail.base", "hide", (-0.8, BODY_Y + 2.4, 4.6), (1.6, 1.6, 3.4)),
    ]))
    B.append(("tail_tip", "tail", (0, BODY_Y + 3.2, 8.0), (-18, 0, 0), [
        cube("tail_tip.whip", "hide", (-0.5, BODY_Y + 2.7, 7.8), (1.0, 1.0, 3.6)),
    ]))

    # --- neck: the dominant feature -------------------------------------------------------
    # **Longer than the body**, thick at the shoulder and tapering to the skull, carried close to
    # vertical. In the reference this is more than a third of the standing height and it is the
    # first thing anyone recognises; a short neck turns the creature into a rodent.
    B.append(("neck_lower", "body", (0, NECK_Y - 1.6, -2.2), (-30, 0, 0), [
        cube("neck_lower.stem", "face", (-1.6, NECK_Y - 1.8, -3.4), (3.2, 5.4, 3.2)),
    ]))
    B.append(("neck_upper", "neck_lower", (0, NECK_Y + 3.6, -3.2), (22, 0, 0), [
        cube("neck_upper.stem", "face", (-1.25, NECK_Y + 3.4, -4.2), (2.5, 5.2, 2.6)),
    ]))

    # --- head: small, and dwarfed by its own eyes -----------------------------------------
    head = [
        cube("head.skull", "face", (-4.2, HEAD_Y, -4.8), (8.4, 5.0, 5.2)),
        cube("head.crown", "face", (-3.4, HEAD_Y + 4.6, -4.2), (6.8, 1.4, 4.0)),
        # Barely a snout: a short taper under the eyes with a dark nose on the tip.
        cube("head.muzzle", "muzzle", (-1.3, HEAD_Y - 0.2, -6.6), (2.6, 2.0, 2.0)),
        cube("head.nose", "nostril", (-0.7, HEAD_Y + 0.8, -7.0), (1.4, 0.9, 0.6)),
    ]
    B.append(("head", "neck_upper", (0, HEAD_Y, -4.6), (6, 0, 0), head))

    # THE feature, and it is genuinely enormous: in the reference each eye is about as wide as the
    # whole skull, so the pair is roughly twice the head's width. Two passes tried to talk that down
    # — first to cubes that fit neatly on the head, then to a painted band — and both lost the
    # animal, because a mooncalf that does not look front-heavy with eye is not a mooncalf.
    #
    # Four texels each, on a skull six texels wide. **The skull had to grow, not the eyes.** Four
    # passes shrank and re-shaped eyes that kept rendering as two monitors on a stick, and the real
    # cause was the head behind them: at four texels wide it could not be seen past a pair of
    # four-texel eyes from any angle. The reference's eyes do overhang the skull — by about a texel
    # a side, which is what this is — but there is a visible head between and around them.
    #
    # Slightly wider than tall, so they read as rounded rather than as square panels, and so the
    # crown clears them: an eye the full height of the skull leaves no forehead and no muzzle.
    for side, sx in (("l", 1), ("r", -1)):
        m = cube if side == "l" else mirror_cube
        B.append((f"eye_{side}", "head", (sx * 2.0, HEAD_Y + 2.4, -4.8), (-12, 0, sx * -6), [
            m(f"eye_{side}.dome", "eye", (0.3, HEAD_Y + 0.6, -7.0), (5.0, 4.3, 5.0)),
        ]))

    # Small rounded mouse ears, set high and back behind the eyes, splayed outward. Pink inside —
    # one of the very few warm notes on the animal and worth the two pixels it costs.
    for side, sx in (("l", 1), ("r", -1)):
        m = cube if side == "l" else mirror_cube
        B.append((f"ear_{side}", "head", (sx * 3.6, HEAD_Y + 4.4, -1.6), (-10, 0, sx * -34), [
            m(f"ear_{side}.flap", "ear", (3.2, HEAD_Y + 4.2, -2.2), (2.0, 2.2, 1.1)),
        ]))

    # --- legs -------------------------------------------------------------------------
    # Slender, and the feet carry **long splayed toes** — that is what the book means by "enormous
    # flat feet" and what the reference shows: a foot much longer than the leg is thick, spreading
    # into three visible digits.
    for tag, (lx, lz) in LEGS.items():
        m = cube if lx > 0 else mirror_cube
        ax = abs(lx)
        B.append((f"leg_{tag}", "root", (lx, LEG_TOP, lz), None, [
            m(f"leg_{tag}.thigh", "hide", (ax - 1.0, LEG_TOP - 3.4, lz - 1.0), (2.0, 3.4, 2.0)),
            m(f"leg_{tag}.shin", "leg", (ax - 0.7, FOOT_H, lz - 0.7), (1.4, LEG_TOP - 3.4 - FOOT_H, 1.4)),
        ]))
        foot = [m(f"foot_{tag}.pad", "leg", (ax - 1.1, 0, lz - 1.4), (2.2, FOOT_H, 2.8))]
        for i, tx in enumerate((-1.4, 0.0, 1.4)):
            foot.append(m(f"foot_{tag}.toe{i}", "leg_dk",
                          (ax - 0.4 + tx, 0, lz - 3.6), (0.9, 0.9, 2.4)))
        B.append((f"foot_{tag}", f"leg_{tag}", (lx, FOOT_H, lz), None, foot))
    return B


# --------------------------------------------------------------------------- fitting

def _rot(rot):
    """Bedrock XYZ euler -> matrix, matching `beast_preview.euler_matrix` and GeckoLib exactly.

    GeckoLib composes `mulPose(Z); mulPose(Y); mulPose(X)`, so a point rotates about X first and Z
    last, and Bedrock's X and Y run opposite to the maths convention.
    """
    rx, ry, rz = (math.radians(float(v)) for v in rot)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(-ry), math.sin(-ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(mz, mul(my, mx))


def _apply(m, pivot, p):
    local = [p[i] - pivot[i] for i in range(3)]
    out = [sum(m[i][k] * local[k] for k in range(3)) for i in range(3)]
    return [out[i] + pivot[i] for i in range(3)]


def posed_bounds(bones):
    """World-space AABB at rest, bone *and* cube rotations included."""
    by_name = {b[0]: b for b in bones}
    lo, hi = [float("inf")] * 3, [float("-inf")] * 3
    for name, _parent, _pivot, _brot, cubes in bones:
        for _key, origin, size, crot, cpivot in cubes:
            for dx in (0, size[0]):
                for dy in (0, size[1]):
                    for dz in (0, size[2]):
                        p = [origin[0] + dx, origin[1] + dy, origin[2] + dz]
                        if crot and any(crot):
                            p = _apply(_rot(crot), cpivot or origin, p)
                        row = by_name[name]
                        while True:
                            _, parent, pivot, rot, _ = row
                            if rot and any(rot):
                                p = _apply(_rot(rot), pivot, p)
                            if not parent:
                                break
                            row = by_name[parent]
                        for i in range(3):
                            lo[i] = min(lo[i], p[i])
                            hi[i] = max(hi[i], p[i])
    return lo, hi


def snap(size):
    """Round a cube size to a whole texel, never below one.

    **This is not cosmetic — a fractional cube size tears its own texture.** See the module docstring.
    Fractional *positions* are fine and are left alone; only extents reach the UV.
    """
    return max(1.0, float(round(size)))


def fit(bones, target_height):
    """Scale onto `target_height`, drop onto y=0, centre in X, and snap every cube to whole texels."""
    lo, hi = posed_bounds(bones)
    k = target_height / (hi[1] - lo[1])
    dy = -lo[1] * k
    dx = -(lo[0] + hi[0]) / 2.0 * k

    def sc(v):
        return (v[0] * k + dx, v[1] * k + dy, v[2] * k)

    def sized(origin, size):
        scaled = [v * k for v in size]
        snapped = [snap(v) for v in scaled]
        moved = sc(origin)
        # Keep each cube where it was drawn: absorb the rounding either side of its own centre.
        return (tuple(moved[i] - (snapped[i] - scaled[i]) / 2.0 for i in range(3)), tuple(snapped))

    out = []
    for name, parent, pivot, rot, cubes in bones:
        placed = []
        for key, origin, size, crot, cpivot in cubes:
            o, s = sized(origin, size)
            placed.append((key, o, s, crot, sc(cpivot) if cpivot else None))
        out.append((name, parent, sc(pivot), rot, placed))
    return out, k


def build_geo(bones, packer, tex_h):
    """Emit against an already-packed `packer` — placing again would shift every UV."""
    lo, hi = posed_bounds(bones)
    out = []
    for name, parent, pivot, rot, cubes in bones:
        b = {"name": name, "pivot": [round(v, 3) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rot and any(rot):
            b["rotation"] = [round(v, 2) for v in rot]
        if cubes:
            cs = []
            for key, origin, size, crot, cpivot in cubes:
                c = {"origin": [round(v, 3) for v in origin],
                     "size": [round(v, 3) for v in size],
                     "uv": list(packer.placed[key][0])}
                if crot and any(crot):
                    c["rotation"] = [round(v, 2) for v in crot]
                    c["pivot"] = [round(v, 3) for v in (cpivot or origin)]
                cs.append(c)
            b["cubes"] = cs
        out.append(b)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{NAME}",
                "texture_width": TEX_W,
                "texture_height": tex_h,
                "visible_bounds_width": round(max(hi[0] - lo[0], hi[2] - lo[2]) / 16.0 + 0.5, 2),
                "visible_bounds_height": round((hi[1] - lo[1]) / 16.0 + 0.5, 2),
                "visible_bounds_offset": [0, round((hi[1] + lo[1]) / 32.0, 2), 0],
            },
            "bones": out,
        }],
    }


# --------------------------------------------------------------------------- skin

def bevel(d, rect, base):
    """Flat fill with a one-pixel bevel: lit along the top, shaded along the bottom and right.

    No top-face-lighter / bottom-face-darker term — Minecraft already lights faces directionally and
    painting it in a second time washes out every top surface.
    """
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    if h > 2:
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=shade(base, 1.08))
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.86))
    if w > 2:
        d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.92))


def speckle(d, rect, base, seed, density=5, spread=0.96):
    """A very light dither. "Smooth pale grey" is the book's word, so this hide gets less grain than
    any other rig in the pack — just enough that a large flat flank does not band."""
    x0, y0, w, h = rect
    lo, hi = shade(base, spread), shade(base, 2.0 - spread)
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
            if (n >> 7) % 100 < density:
                d.point((x, y), fill=lo if (n >> 11) & 1 else hi)


def paint(packer, tex_h):
    img = Image.new("RGBA", (TEX_W, tex_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (TEX_W, tex_h), (0, 0, 0, 0))
    d, gd = ImageDraw.Draw(img), ImageDraw.Draw(glow)

    palette = {"hide": HIDE, "face": FACE, "leg": LEG, "leg_dk": LEG_DK,
               "muzzle": MUZZLE, "nostril": NOSTRIL, "eye": EYE, "ear": EAR_IN}

    for key, (uv, size) in packer.placed.items():
        role = ROLES[key]
        base = palette[role]
        f = faces(uv, size)
        seed = sum(ord(c) for c in key)

        for rect in f.values():
            bevel(d, rect, base)
        if role in ("hide", "face", "leg"):
            for rect in f.values():
                speckle(d, rect, base, seed)

        if role == "hide":
            # Countershading: the underside pales off the way it does on any grazing animal, and the
            # flanks warm slightly toward the haunches.
            bevel(d, f["bottom"], BELLY)
            for fname in ("east", "west"):
                x0, y0, w, h = f[fname]
                for ry in range(y0 + h - max(1, h // 3), y0 + h):
                    d.line([(x0, ry), (x0 + w - 1, ry)], fill=shade(HIDE, 1.05))

        if role == "eye":
            # Mostly pupil. The reference's eye is a big dark sphere with only a crescent of
            # blue-grey iris showing where it curves away — an even ring of iris all round makes it
            # a blue frame with a dot in it, which is what two earlier passes produced.
            for rect in f.values():
                x0, y0, w, h = rect
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=PUPIL)
            # The crescent: iris along the top and the outward edge of the faces you actually see.
            for fname in ("north", "top", "east", "west"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0), (x0 + w - 1, y0)], fill=EYE)
                d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=EYE_DK)
                d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=EYE_DK)
            # Where the sphere turns away entirely, no iris at all.
            for fname in ("bottom", "south"):
                bevel(d, f[fname], EYE_DK)
            # One specular catchlight per eye, on the two faces that meet the sky and the viewer,
            # mirrored onto the glowmask. A mooncalf is only ever out at night, and a catchlight
            # that dies with the light leaves a taxidermy stare.
            # Top face only. On the forward face a single pixel is a quarter of a four-texel pupil
            # and reads as a white square punched through the eye.
            x0, y0, w, h = f["top"]
            d.point((x0 + 1, y0 + 1), fill=GLINT)
            gd.point((x0 + 1, y0 + 1), fill=GLINT)

        if role == "leg":
            # A hairline of lift down the outer edge, so a near-black leg still has a readable
            # silhouette against a dark background.
            for fname in ("east", "west", "north", "south"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0), (x0, y0 + h - 1)], fill=LEG_LT)

        if role == "leg_dk":
            # The foot: darker than the leg, with a lit top edge so it separates from the shin.
            for fname in ("north", "south", "east", "west"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0), (x0 + w - 1, y0)], fill=LEG)

        if role == "ear":
            # Pink only on the inward face; the outside is hide-coloured like the rest of the head.
            for fname in ("north", "south", "top", "bottom"):
                bevel(d, f[fname], FACE)

        if key == "head.crown":
            x0, y0, w, h = f["north"]
            d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(FACE, 0.88))

    return img, glow


# --------------------------------------------------------------------------- animation
#
# Deltas from the rest pose — see the module docstring. Clips oscillate around 0.

FEET = [f"foot_{t}" for t in LEGS]
LEGS_ALL = [f"leg_{t}" for t in LEGS]


def kf(*pairs):
    return {str(t): [round(float(v), 2) for v in vec] for t, vec in pairs}


def build_anim():
    # --- idle: a shy animal listening -------------------------------------------------
    idle = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (1.5, (1.5, 0, 0)), (3.0, (0, 0, 0)))},
        "neck_lower": {"rotation": kf((0.0, (0, -2, 0)), (1.0, (-1.5, 2, 0)),
                                      (2.1, (1.5, -1.5, 0)), (3.0, (0, -2, 0)))},
        "neck_upper": {"rotation": kf((0.0, (0, -3, 0)), (1.0, (2, 3, 0)),
                                      (2.1, (-2, -2, 0)), (3.0, (0, -3, 0)))},
        "head": {"rotation": kf((0.0, (0, 4, 1)), (1.0, (3, -4, -1)),
                                (2.1, (-2, 3, 1)), (3.0, (0, 4, 1)))},
        # The ears do the listening; the eyes barely move because they are fixed on the sky.
        "ear_l": {"rotation": kf((0.0, (0, 0, 0)), (0.7, (-14, 0, -8)),
                                 (1.1, (0, 0, 0)), (3.0, (0, 0, 0)))},
        "ear_r": {"rotation": kf((0.0, (0, 0, 0)), (1.9, (-14, 0, 8)),
                                 (2.3, (0, 0, 0)), (3.0, (0, 0, 0)))},
        "tail": {"rotation": kf((0.0, (0, -6, 0)), (1.5, (0, 6, 0)), (3.0, (0, -6, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (1.5, (0, 0.2, 0)), (3.0, (0, 0, 0)))},
    }

    # --- walk: diagonal pairs, long legs ------------------------------------------------
    # A quadruped's diagonal gait: front-left swings with back-right. On legs this long the swing
    # is wide and the feet counter-rotate so the enormous pads stay flat to the ground instead of
    # scything through it.
    walk = {}
    swing = {"fl": 1, "br": 1, "fr": -1, "bl": -1}
    for tag, phase in swing.items():
        a = 26 * phase
        walk[f"leg_{tag}"] = {"rotation": kf((0.0, (a, 0, 0)), (0.45, (-a, 0, 0)), (0.9, (a, 0, 0)))}
        walk[f"foot_{tag}"] = {"rotation": kf((0.0, (-a * 0.55, 0, 0)), (0.45, (a * 0.55, 0, 0)),
                                              (0.9, (-a * 0.55, 0, 0)))}
    walk["body"] = {"rotation": kf((0.0, (0, 2.5, 0)), (0.45, (0, -2.5, 0)), (0.9, (0, 2.5, 0)))}
    walk["neck_lower"] = {"rotation": kf((0.0, (-3, 0, 0)), (0.45, (2, 0, 0)), (0.9, (-3, 0, 0)))}
    walk["neck_upper"] = {"rotation": kf((0.0, (4, 0, 0)), (0.45, (-3, 0, 0)), (0.9, (4, 0, 0)))}
    walk["head"] = {"rotation": kf((0.0, (5, 0, 0)), (0.45, (-4, 0, 0)), (0.9, (5, 0, 0)))}
    walk["tail"] = {"rotation": kf((0.0, (0, -10, 0)), (0.45, (0, 10, 0)), (0.9, (0, -10, 0)))}
    walk["root"] = {"position": kf((0.0, (0, 0, 0)), (0.22, (0, 0.5, 0)), (0.45, (0, 0, 0)),
                                   (0.68, (0, 0.5, 0)), (0.9, (0, 0, 0)))}

    # --- dance: the whole point of the animal -------------------------------------------
    # It rears onto the hind legs and sways, forelegs pawing the air, head rolling as it watches
    # the moon. Deliberately slow and slightly absurd — this is a shy herbivore's private ritual,
    # not a threat display, and the crop circles it treads are the joke the lore is built on.
    #
    # The rear is carried on `root` rotation so the hind feet stay planted while everything above
    # them tips back; rotating `body` alone would swing the legs out from under it.
    dance = {
        "root": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-34, 0, 0)), (1.4, (-30, 0, 0)),
                                (2.6, (-34, 0, 0)), (3.4, (-30, 0, 0)), (4.0, (0, 0, 0))),
                 "position": kf((0.0, (0, 0, 0)), (0.6, (0, 1.2, 1.4)), (2.0, (0, 1.6, 1.4)),
                                (3.4, (0, 1.2, 1.4)), (4.0, (0, 0, 0)))},
        # Sway. The whole body rocks side to side twice over the loop, which is what treads a ring.
        "body": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (0, 14, -6)), (2.0, (0, 0, 0)),
                                (3.0, (0, -14, 6)), (4.0, (0, 0, 0)))},
        # The neck straightens as it rears — the S opens out and the head goes up, which is the
        # whole gesture. Split across both segments so the curve unwinds rather than hinging.
        "neck_lower": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (18, 0, 0)), (2.0, (21, 0, 0)),
                                      (3.4, (18, 0, 0)), (4.0, (0, 0, 0)))},
        "neck_upper": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (-12, 0, 0)), (2.0, (-15, 0, 0)),
                                      (3.4, (-12, 0, 0)), (4.0, (0, 0, 0)))},
        # Head rolls a slow circle — it is watching the moon go round, not looking for anything.
        "head": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (-8, 12, 5)), (2.0, (-14, 0, 0)),
                                (3.0, (-8, -12, -5)), (4.0, (0, 0, 0)))},
        "ear_l": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (-18, 0, -12)), (3.0, (6, 0, 4)),
                                 (4.0, (0, 0, 0)))},
        "ear_r": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (6, 0, 12)), (3.0, (-18, 0, -4)),
                                 (4.0, (0, 0, 0)))},
        "tail": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (12, 16, 0)), (2.0, (16, 0, 0)),
                                (3.0, (12, -16, 0)), (4.0, (0, 0, 0)))},
    }
    # Forelegs paw the air, a beat apart so it looks like treading rather than a salute.
    for tag, off in (("fl", 0.0), ("fr", 0.5)):
        dance[f"leg_{tag}"] = {"rotation": kf(
            (0.0, (0, 0, 0)), (0.6, (-58, 0, 0)),
            (round(1.2 + off, 2), (-38, 0, 0)), (round(1.7 + off, 2), (-66, 0, 0)),
            (round(2.4 + off, 2), (-40, 0, 0)), (round(2.9 + off, 2), (-64, 0, 0)),
            (3.6, (-52, 0, 0)), (4.0, (0, 0, 0)))}
        dance[f"foot_{tag}"] = {"rotation": kf(
            (0.0, (0, 0, 0)), (0.6, (34, 0, 0)),
            (round(1.7 + off, 2), (22, 0, 0)), (round(2.9 + off, 2), (34, 0, 0)),
            (4.0, (0, 0, 0)))}
    # Hind legs take the weight and flex with the sway.
    for tag, sign in (("bl", 1), ("br", -1)):
        dance[f"leg_{tag}"] = {"rotation": kf(
            (0.0, (0, 0, 0)), (0.6, (16, 0, 0)), (1.0, (12, 0, 4 * sign)),
            (2.0, (16, 0, 0)), (3.0, (12, 0, -4 * sign)), (4.0, (0, 0, 0)))}
        dance[f"foot_{tag}"] = {"rotation": kf(
            (0.0, (0, 0, 0)), (0.6, (-14, 0, 0)), (2.0, (-12, 0, 0)), (4.0, (0, 0, 0)))}

    return {
        "format_version": "1.8.0",
        "animations": {
            f"animation.{NAME}.idle": {"loop": True, "animation_length": 3.0, "bones": idle},
            f"animation.{NAME}.walk": {"loop": True, "animation_length": 0.9, "bones": walk},
            f"animation.{NAME}.dance": {"loop": True, "animation_length": 4.0, "bones": dance},
        },
    }


# --------------------------------------------------------------------------- emit

def check_anim_bones(bones, anim):
    """Every animated bone must exist. GeckoLib skips missing ones silently, so a whole clip can
    ship doing nothing at all — which is how the basilisk stayed frozen for months."""
    names = {b[0] for b in bones}
    used = set()
    for clip in anim["animations"].values():
        used |= set(clip["bones"])
    return sorted(used - names)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    ROLES.clear()
    fitted, k = fit(build_bones(), TARGET_HEIGHT_UNITS)

    anim = build_anim()
    for clip_name, v in bespoke_reactions.clips_for(
            NAME, [(b[0], b[1], b[2]) for b in fitted], game_sense=True).items():
        anim["animations"][f"animation.{NAME}.{clip_name}"] = v
    dangling = check_anim_bones(fitted, anim)
    if dangling:
        print(f"ERROR: animation targets bones the rig does not have: {dangling}", file=sys.stderr)
        return 1

    packer = Packer(TEX_W)
    used = packer.place_sorted([(key, size) for b in fitted for key, _, size, _, _ in b[4]])
    tex_h = 8 * int(math.ceil(used / 8.0))
    geo = build_geo(fitted, packer, tex_h)

    if not args.force and not is_regenerable(SKIN, MARKER):
        print(f"{NAME}.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    skin, glow = paint(packer, tex_h)
    for path, doc in ((GEO, geo), (ANIM, anim)):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=2)
            f.write("\n")
    save(posterise(skin, 32), SKIN, MARKER)
    save(glow, GLOW, MARKER)

    lo, hi = posed_bounds(fitted)
    cubes = sum(len(b[4]) for b in fitted)
    print(f"{NAME} rig: {len(fitted)} bones, {cubes} cubes, "
          f"UV {used}/{tex_h} rows used, skin {TEX_W}x{tex_h}")
    print(f"  fitted x{k:.3f} -> {hi[1] - lo[1]:.1f}u tall = {(hi[1] - lo[1]) / 16:.2f} blocks "
          f"drawn vs {BOX_H / 16:.2f} box (oversize x{OVERSIZE}); "
          f"{hi[0] - lo[0]:.1f}u wide = {(hi[0] - lo[0]) / 16:.2f} blocks vs {BOX_W / 16:.2f} box")
    skull = next(sz for b in fitted for key, _, sz, _, _ in b[4] if key == "head.skull")
    eye = next(sz for b in fitted for key, _, sz, _, _ in b[4] if key == "eye_l.dome")
    print(f"  skull {skull[0]:.0f} texels wide, eyes {eye[0]:.0f} texels "
          f"-> pupil {max(1, int(eye[0]) - 2)}x{max(1, int(eye[1]) - 2)}")
    print(f"  clips: {', '.join(sorted(anim['animations']))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
