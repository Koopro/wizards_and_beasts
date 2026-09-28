#!/usr/bin/env python3
"""The werewolf: rig, skin, glowmask and animations.

Replaces a seven-bone, six-cube box biped that read as a brown crate with sticks for
limbs — no snout, no tail, no silhouette. It was the placeholder the animation file's
`_comment` still advertises, and it is what a transformed player saw.

**Canon is emphatic that a werewolf is not a wolf**, and that is the whole design brief.
Lupin's own DADA lesson in *Prisoner of Azkaban* has the class distinguish the two, and
the tells are specific:

  - a **shorter snout** than a true wolf's;
  - a **tufted tail** rather than a wolf's full brush;
  - **smaller pupils**.

The transformation scene adds the build: bipedal, hunched, gaunt to the point of
emaciation, with long arms and a spine that shows. So this is a hunched biped with a
deliberately stubby muzzle and a thin tail ending in a distinct tuft — three silhouette
cues that a wolf model would fail. If a later art pass makes the snout long and the tail
a brush, it has drawn a wolf, and the canon distinction is gone.

Two things worth stating:

  - **Fitted, not eyeballed.** One rig serves two consumers: the mob, whose
    `data/creatures/werewolf.json` declares a 1.7 x 1.9 hitbox, and the player form, which
    draws it through `SizeProfile werewolf_wolf` and multiplies by that profile's
    `modelScale` of 1.3 into a 2.34-block box. The height that matters is the one *after*
    the hunch rotations, which is not a number you can keep in your head while typing
    pivots — authored by hand this rig came out 36.1 units, i.e. 2.26 blocks against the
    mob's 1.9 and 2.93 against the player form's 2.34. So the generator measures its own
    posed bounding box and scales the whole rig to land on 28.8 units: 1.80 blocks for the
    mob, and exactly 2.34 for the player form. Uniform scale commutes with rotation, so
    every authored angle and proportion survives it untouched.
  - **Whole-texel cube sizes.** `snap()` rounds every size after the fit scale, because GeckoLib
    lays box UV out from `Math.floor(size)` while the packer rounds up — fractional sizes tore this
    rig's texture on 75+ faces before it was added. The sheet is 128 wide with the height following
    the pack; `texture_width/height` in the geometry and the PNG must agree or
    `bestiary_portraits.rig_is_renderable` rejects the rig and the portrait silently keeps its old
    art. Verify with `python tools/uv_check.py werewolf`.

Bone and clip names are load-bearing. `PlayerFormRig` maps the player form to
`animation.werewolf.idle` / `.walk` / `.attack` / `.hit`, and `PlayerFormRigTest` asserts every one
of them exists on disk — asking GeckoLib for a clip its file does not define throws mid-render.

`attack` and `hit` are wired at both ends: the mob fires them from
`GenericBeastEntity.doHurtTarget`/`hurtServer`, gated on `data/creatures/werewolf.json` declaring
`"clips": ["attack", "hit"]`, and the transformed player picks them off its own render state.
`howl` stays authored and unwired, matching the broom `brake`/`summon` precedent.

UVs are packed automatically; the skin is painted through the same coordinates the packer
hands out, so the two cannot disagree.

Run from the repo root:  python tools/werewolf_model.py [--force]
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

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/werewolf.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/werewolf.animation.json"
SKIN = f"{ASSETS}/textures/entity/werewolf.png"
GLOW = f"{ASSETS}/textures/entity/werewolf_glowmask.png"
MARKER = marker("werewolf_model.py")

TEX = 128

# Mangy, ash-grey-brown. Not a healthy wolf's coat — the books describe the transformed
# Lupin as gaunt and wretched, so the palette is dull and patchy rather than rich.
FUR_MID = hx("#6B5B4A")
FUR_LIT = hx("#867460")
FUR_DARK = hx("#4A3E33")
FUR_PATCH = hx("#574A3D")
SKIN_BARE = hx("#7A6A62")   # bald patches over ribs and muzzle
CLAW = hx("#2A2420")
MOUTH = hx("#3A2126")
TOOTH = hx("#E6DFC8")
NOSE = hx("#241D1A")
EYE = hx("#D9A22B")         # amber, and small — the canon tell


# --------------------------------------------------------------------------- rig

# name -> (parent, pivot, cube origin, cube size, rest rotation or None)
#
# Built facing north (-Z), feet on y=0. The hunch lives in the bone rotations, not in the
# cube positions, so the rest pose can be straightened without re-sculpting.
#
# Legs hang off **root, not body** — the same trap the ghoul rig documents. Parenting them
# to the torso means the body's forward rotation folds the whole creature about its ankles
# instead of hunching it at the waist.
BONES = [
    ("root",           None,      (0, 0, 0),          None,                   None,            None),

    # Slim waist, **broad shoulders**, deep hunch. The mass belongs above the ribs, not around
    # them: canon keeps the transformed Lupin emaciated, so a barrel gut would read as a bear,
    # but a narrow shoulder line made the whole thing a flat slab from the front and the hunch
    # had nothing to carry. Waist stays 5.6; the chest goes to 8.0 and the hunch to 28 degrees.
    ("body",           "root",    (0, 16.5, 0),       (-2.8, 16.5, -2.1),     (5.6, 6.5, 4.2), (28, 0, 0)),
    ("chest",          "body",    (0, 22.5, 0),       (-4.6, 22.0, -2.6),     (9.2, 6.4, 5.2), (2, 0, 0)),

    # Neck forward but not buried. At 30 degrees the head sank into the shoulders and the
    # silhouette lost its head entirely; 20 keeps the predator's lead without swallowing it.
    ("neck",           "chest",   (0, 27.6, -1.0),    (-1.7, 27.2, -3.0),     (3.4, 3.4, 3.6), (20, 0, 0)),
    ("head",           "neck",    (0, 30.2, -2.4),    (-2.7, 29.6, -5.8),     (5.4, 5.0, 5.2), (-10, 0, 0)),

    # THE canon tell. Short against a true wolf's long snout. Do not lengthen this.
    ("muzzle",         "head",    (0, 31.6, -5.8),    (-1.6, 30.4, -8.8),     (3.2, 2.6, 3.0), (4, 0, 0)),
    ("jaw",            "head",    (0, 30.4, -5.8),    (-1.45, 29.3, -8.5),    (2.9, 1.2, 2.7), (8, 0, 0)),

    # Fangs. Their own bones purely because this table carries one cube per bone — parenting the
    # upper pair to the muzzle and the lower pair to the jaw is also what makes them separate
    # correctly when the attack clip opens the mouth.
    ("fang_upper",     "muzzle",  (0, 30.4, -8.0),    (-1.3, 29.2, -8.4),     (2.6, 1.4, 1.2), None),
    ("fang_lower",     "jaw",     (0, 30.4, -8.0),    (-1.1, 30.1, -8.2),     (2.2, 1.2, 1.0), None),

    # Swept back and splayed out, and deeper than they are wide — a wolf ear is a fin, not a
    # spike. The first pair were 1.5 x 4.4 x 1.2 uprights raked back only 10 degrees, which in
    # silhouette read as two antennae and took the whole animal with them. Width matters as
    # much as rake: at 1.1 units they were still hairlines seen head-on. Bedrock's +X rotation
    # tips the top of an upright cube *forward*, so the rake is the negative number.
    ("ear_left",       "head",    (2.0, 34.2, -1.6),  (1.4, 34.2, -2.5),      (1.9, 3.4, 2.6), (-34, 0, -26)),
    ("ear_right",      "head",    (-2.0, 34.2, -1.6), (-3.3, 34.2, -2.5),     (1.9, 3.4, 2.6), (-34, 0, 26)),

    # Long, thin arms. These are the shape cue that separates a werewolf from a bear — the
    # knuckles hang near the ankle when it stoops.
    ("arm_left",       "chest",   (4.6, 27.4, -0.4),  (4.1, 18.4, -1.2),      (2.3, 9.2, 2.4), (-14, 0, -4)),
    ("forearm_left",   "arm_left",(5.25, 18.4, 0),    (4.4, 9.6, -1.0),       (1.8, 8.8, 2.0), (20, 0, 0)),
    ("hand_left",      "forearm_left", (5.3, 9.6, 0), (4.2, 6.0, -2.6),       (2.2, 3.4, 3.6), (8, 0, 0)),

    ("arm_right",      "chest",   (-4.6, 27.4, -0.4), (-6.4, 18.4, -1.2),     (2.3, 9.2, 2.4), (-12, 0, 4)),
    ("forearm_right",  "arm_right",(-5.25, 18.4, 0),  (-6.2, 9.6, -1.0),      (1.8, 8.8, 2.0), (18, 0, 0)),
    ("hand_right",     "forearm_right", (-5.3, 9.6, 0), (-6.4, 6.0, -2.6),    (2.2, 3.4, 3.6), (8, 0, 0)),

    # Digitigrade and long: thigh, shin, then a paw that is really the toes. The zig-zag is
    # what stops it walking like a person in a suit.
    ("leg_left",       "root",    (2.1, 16.8, 0),     (0.9, 9.4, -1.7),       (2.6, 7.4, 3.4), (-26, 0, 0)),
    ("shin_left",      "leg_left",(2.1, 9.4, 0),      (1.05, 2.6, -1.4),      (2.2, 6.8, 2.6), (38, 0, 0)),
    ("foot_left",      "shin_left",(2.1, 2.6, 0),     (0.95, 0, -4.4),        (2.5, 2.0, 6.0), (-16, 0, 0)),

    ("leg_right",      "root",    (-2.1, 16.8, 0),    (-3.5, 9.4, -1.7),      (2.6, 7.4, 3.4), (-26, 0, 0)),
    ("shin_right",     "leg_right",(-2.1, 9.4, 0),    (-3.25, 2.6, -1.4),     (2.2, 6.8, 2.6), (38, 0, 0)),
    ("foot_right",     "shin_right",(-2.1, 2.6, 0),   (-3.45, 0, -4.4),       (2.5, 2.0, 6.0), (-16, 0, 0)),

    # The other canon tell: a thin tail ending in a tuft, not a wolf's even brush. Carried out
    # and low rather than tucked, because a tail nobody can see is not a distinguishing mark.
    # The tuft is deliberately wider than the segment before it so the shape survives at
    # distance and in silhouette.
    # It hangs. The tail is parented to `body`, so the 28-degree hunch lifts its root before any
    # of these angles apply — authored at -38/-16/-8 that cancelled out and the tail came off the
    # hips as a horizontal broom handle, which is most of why the silhouette read as a donkey.
    # These carry it down and back in a curve instead. Not too far: at -62 it tucked in behind
    # the legs and vanished from the silhouette altogether, which is the same failure by the
    # opposite route.
    ("tail_base",      "body",    (0, 18.4, 1.9),     (-0.9, 17.6, 1.9),      (1.8, 1.8, 5.0), (-44, 0, 0)),
    ("tail_mid",       "tail_base",(0, 18.4, 6.9),    (-0.7, 17.8, 6.9),      (1.4, 1.4, 4.8), (-20, 0, 0)),
    ("tail_tuft",      "tail_mid",(0, 18.4, 11.7),    (-1.6, 16.9, 11.7),     (3.2, 3.2, 4.0), (-14, 0, 0)),
]


# The rig is authored at whatever scale reads well by hand and then **fitted**, because the
# height that matters is the one after the hunch rotations, and that is not something you can
# hold in your head while typing pivots. Authored as-is it came out 36.1 units — 2.26 blocks
# against a 1.9-block mob box, and 2.93 blocks once the player form's 1.3x size profile was
# applied on top of it, against a 2.34-block box. Both consumers were wrong in the same
# direction and neither was visible from the numbers in the table.
#
# 28.8 units = 1.8 blocks. The mob box is 1.9, leaving a little headroom; the player form
# multiplies by SizeProfile werewolf_wolf's modelScale of 1.3 and lands on 2.34, which is
# that profile's box exactly.
TARGET_HEIGHT_UNITS = 28.8


def _rot_matrix(rx, ry, rz):
    """Bedrock XYZ euler -> matrix, matching `beast_preview.euler_matrix` and GeckoLib exactly.

    GeckoLib composes `mulPose(Z); mulPose(Y); mulPose(X)`, so a point rotates about X first and Z
    last, and Bedrock's X and Y run opposite to the maths convention. The original version here had
    the order reversed and no sign flip, which barely showed while every rotation on this rig was a
    pure X pitch — but the fitted height is measured through this matrix, and the swept ears add
    real Z roll, so it has to agree with what the game draws.
    """
    rx, ry, rz = (math.radians(v) for v in (rx, ry, rz))
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(-ry), math.sin(-ry)
    cz, sz = math.cos(rz), math.sin(rz)

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]],
               mul([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]],
                   [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def _to_world(table, name, point):
    """Walk a point up the bone chain, applying each ancestor's rest rotation."""
    by_name = {row[0]: row for row in table}
    row = by_name[name]
    while True:
        _, parent, pivot, _, _, rot = row
        if rot:
            local = [point[i] - pivot[i] for i in range(3)]
            m = _rot_matrix(*rot)
            local = [sum(m[i][k] * local[k] for k in range(3)) for i in range(3)]
            point = [local[i] + pivot[i] for i in range(3)]
        if not parent:
            return point
        row = by_name[parent]


def posed_bounds(table):
    """World-space AABB of the rig in its rest pose, rotations included."""
    lo = [float("inf")] * 3
    hi = [float("-inf")] * 3
    for name, _parent, _pivot, origin, size, _rot in table:
        if not size:
            continue
        for dx in (0, size[0]):
            for dy in (0, size[1]):
                for dz in (0, size[2]):
                    w = _to_world(table, name, [origin[0] + dx, origin[1] + dy, origin[2] + dz])
                    for i in range(3):
                        lo[i] = min(lo[i], w[i])
                        hi[i] = max(hi[i], w[i])
    return lo, hi


def fit(table, target_height):
    """
    Uniformly scale the rig to {@code target_height} and drop its feet onto y=0.

    Uniform scale commutes with rotation, so scaling pivots, origins and sizes by one factor
    leaves every authored angle and proportion untouched — only the size changes.
    """
    lo, hi = posed_bounds(table)
    k = target_height / (hi[1] - lo[1])
    dy = -lo[1] * k

    out = []
    for name, parent, pivot, origin, size, rot in table:
        pivot = (pivot[0] * k, pivot[1] * k + dy, pivot[2] * k)
        if size:
            scaled = [v * k for v in size]
            snapped = [snap(v) for v in scaled]
            moved = (origin[0] * k, origin[1] * k + dy, origin[2] * k)
            # Keep each cube where it was drawn: absorb the rounding either side of its centre.
            origin = tuple(moved[i] - (snapped[i] - scaled[i]) / 2.0 for i in range(3))
            size = tuple(snapped)
        out.append((name, parent, pivot, origin, size, rot))
    return out, k


def snap(size):
    """Round a cube size to a whole texel, never below one.

    **This is not cosmetic — a fractional cube size tears its own texture.** GeckoLib lays box UV out
    from `Math.floor(size)` (`BakedModelFactory.buildQuad`), while `boxuv.Packer`/`faces()` round up.
    On a cube of size 3.2 the game reads the north face at `u+3` while the painter wrote it at `u+4`:
    every face samples a strip of its neighbour, and the top and bottom faces sample the island's
    unused corner, which is transparent. That is holes on the model. Any dimension under 1.0 floors
    to **zero** and collapses the whole cube to a zero-area UV.

    `fit()` is what makes the sizes fractional — the rig is authored in whole units and then scaled —
    so the snap has to happen after the scale, not at authoring time. Verify with
    `python tools/uv_check.py werewolf`.
    """
    return max(1.0, float(round(size)))


def build_geo(packer, table, tex_h):
    """Emit against an already-packed `packer`; `place()` again and every UV shifts."""
    bones = []
    for name, parent, pivot, origin, size, rot in table:
        b = {"name": name, "pivot": [round(v, 3) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = list(rot)
        if size:
            uv = packer.placed[name][0]
            b["cubes"] = [{
                "origin": [round(v, 3) for v in origin],
                "size": [round(v, 3) for v in size],
                "uv": list(uv),
            }]
        bones.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.werewolf",
                "texture_width": TEX,
                "texture_height": tex_h,
                "visible_bounds_width": 4,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.2, 0],
            },
            "bones": bones,
        }],
    }


# --------------------------------------------------------------------------- skin

# Bones whose fur is worn through — the gaunt, half-bald look the transformation scene
# describes. Painted as bare hide rather than coat.
BARE = {"muzzle", "jaw", "hand_left", "hand_right", "foot_left", "foot_right"}


def bevel(d, rect, base):
    """Flat fill with a one-pixel bevel: lit along the top, shaded along the bottom and right.

    This replaces `mottle()` at density 34. At 128px that painter puts a one-to-three pixel blotch
    every few pixels, which reads as camouflage rather than as fur, and because the noise is
    generated per face no two faces of a cube agree at the seam. A box model reads as separate
    volumes because each face carries a consistent edge, not because it carries texture.

    There is deliberately no top-face-lighter / bottom-face-darker term. Minecraft already lights
    faces directionally; painting it in a second time washes out every top surface.
    """
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    if h > 2:
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=shade(base, 1.10))
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.82))
    if w > 2:
        d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.88))


def speckle(d, rect, base, seed, density=9, spread=0.92):
    """A light, low-contrast dither — grain, not dirt. Deterministic, so a rig paints the same way
    every run. Fur gets a little more of it than bare hide; neither gets enough to read as noise."""
    x0, y0, w, h = rect
    lo, hi = shade(base, spread), shade(base, 2.0 - spread)
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
            if (n >> 7) % 100 < density:
                d.point((x, y), fill=lo if (n >> 11) & 1 else hi)


def paint(packer, tex_h):
    img = Image.new("RGBA", (TEX, tex_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glow = Image.new("RGBA", (TEX, tex_h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)

    for name, (uv, size) in packer.placed.items():
        f = faces(uv, size)
        bare = name in BARE
        base = SKIN_BARE if bare else FUR_MID
        seed = sum(ord(c) for c in name)

        for rect in f.values():
            bevel(d, rect, base)
            speckle(d, rect, base, seed, density=6 if bare else 10)

        # Fur direction: short dark strokes running down the coat. This is what separates fur
        # from painted leather now that the blotches are gone, and it is directional on purpose —
        # a random scatter reads as dirt, a combed one reads as a pelt.
        if not bare:
            for fname in ("north", "south", "east", "west"):
                x0, y0, w, h = f[fname]
                for sx in range(x0 + 1, x0 + w - 1, 3):
                    off = (sx + seed) % 3
                    d.line([(sx, y0 + off), (sx, min(y0 + h - 1, y0 + off + 2))], fill=FUR_DARK)

        # Ribs: dark bands across the chest so the starved build reads on the skin as well as in
        # the silhouette. Canon has the transformed Lupin emaciated and the broad shoulders would
        # otherwise read as healthy bulk.
        if name == "chest":
            for fname in ("east", "west", "north", "south"):
                x0, y0, w, h = f[fname]
                for i in range(1, 4):
                    ry = y0 + int(h * i / 4.0)
                    d.line([(x0 + 1, ry), (x0 + w - 2, ry)], fill=FUR_DARK)
                    if ry + 1 < y0 + h:
                        d.line([(x0 + 1, ry + 1), (x0 + w - 2, ry + 1)], fill=shade(FUR_MID, 1.12))

        # Muzzle: a black nose pad on the top of the snout, not the front — a pad painted across
        # the whole face reads as a mask.
        if name == "muzzle":
            x0, y0, w, h = f["top"]
            d.rectangle([x0 + w // 4, y0, x0 + w - w // 4 - 1, y0 + max(1, h // 3)], fill=NOSE)
            x0, y0, w, h = f["north"]
            d.rectangle([x0 + w // 3, y0, x0 + w - w // 3 - 1, y0 + max(1, h // 3)], fill=NOSE)

        if name == "jaw":
            for rect in f.values():
                bevel(d, rect, SKIN_BARE)
            x0, y0, w, h = f["top"]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=MOUTH)

        # Fangs: solid ivory, a dark root line so they read as set into the gum.
        if name.startswith("fang"):
            for rect in f.values():
                x0, y0, w, h = rect
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=TOOTH)
            for fname in ("north", "east", "west", "south"):
                x0, y0, w, h = f[fname]
                edge = y0 + h - 1 if name == "fang_upper" else y0
                d.line([(x0, edge), (x0 + w - 1, edge)], fill=MOUTH)

        # Claws on the leading edge of every hand and paw.
        if name in ("hand_left", "hand_right", "foot_left", "foot_right"):
            x0, y0, w, h = f["north"]
            for cx in range(x0, x0 + w, 2):
                d.line([(cx, y0 + h - 2), (cx, y0 + h - 1)], fill=CLAW)

        # Eyes. Small on purpose — "smaller pupils" is one of the three things Lupin's lesson uses
        # to tell a werewolf from a wolf, so this is a couple of amber pixels each, not a lantern.
        if name == "head":
            x0, y0, w, h = f["north"]
            ey = y0 + h // 3
            for ex in (x0 + w // 3 - 1, x0 + w - w // 3):
                d.point((ex, ey), fill=EYE)
                gd.point((ex, ey), fill=EYE)

    return img, glow


# --------------------------------------------------------------------------- animation

def build_anim():
    def kf(*pairs):
        return {str(t): list(v) for t, v in pairs}

    return {
        "format_version": "1.8.0",
        "animations": {
            # Standing, breathing hard, tail low. A werewolf at rest is still a coiled
            # thing, so the idle is shallow and quick rather than a slow sway.
            "animation.werewolf.idle": {
                "loop": True,
                "animation_length": 3.0,
                "bones": {
                    "body": {"rotation": kf((0.0, (18, 0, 0)), (1.5, (16, 0, 0)), (3.0, (18, 0, 0)))},
                    "chest": {"rotation": kf((0.0, (6, 0, 0)), (1.5, (8.5, 0, 0)), (3.0, (6, 0, 0)))},
                    "neck": {"rotation": kf((0.0, (30, -2, 0)), (1.0, (28, 3, 0)),
                                            (2.1, (32, -3, 0)), (3.0, (30, -2, 0)))},
                    "head": {"rotation": kf((0.0, (-16, 2, 0)), (1.0, (-14, -3, 1)),
                                            (2.1, (-18, 3, -1)), (3.0, (-16, 2, 0)))},
                    "jaw": {"rotation": kf((0.0, (7, 0, 0)), (1.2, (13, 0, 0)), (3.0, (7, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (-10, 0, -5)), (1.5, (-7, 0, -7)), (3.0, (-10, 0, -5)))},
                    "arm_right": {"rotation": kf((0.0, (-8, 0, 5)), (1.5, (-11, 0, 7)), (3.0, (-8, 0, 5)))},
                    "tail_base": {"rotation": kf((0.0, (-20, -4, 0)), (1.5, (-17, 5, 0)), (3.0, (-20, -4, 0)))},
                    "tail_mid": {"rotation": kf((0.0, (-12, -6, 0)), (1.5, (-9, 7, 0)), (3.0, (-12, -6, 0)))},
                    "tail_tuft": {"rotation": kf((0.0, (-6, -7, 0)), (1.5, (-4, 8, 0)), (3.0, (-6, -7, 0)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (1.5, (0, 0.3, 0)), (3.0, (0, 0, 0)))},
                },
            },
            # A lope, not a walk: long strides, deep knee flex, the whole body driving
            # forward over the hips.
            "animation.werewolf.walk": {
                "loop": True,
                "animation_length": 0.9,
                "bones": {
                    "leg_left": {"rotation": kf((0.0, (-52, 0, 0)), (0.45, (10, 0, 0)), (0.9, (-52, 0, 0)))},
                    "leg_right": {"rotation": kf((0.0, (10, 0, 0)), (0.45, (-52, 0, 0)), (0.9, (10, 0, 0)))},
                    "shin_left": {"rotation": kf((0.0, (54, 0, 0)), (0.45, (22, 0, 0)), (0.9, (54, 0, 0)))},
                    "shin_right": {"rotation": kf((0.0, (22, 0, 0)), (0.45, (54, 0, 0)), (0.9, (22, 0, 0)))},
                    "foot_left": {"rotation": kf((0.0, (-8, 0, 0)), (0.45, (-26, 0, 0)), (0.9, (-8, 0, 0)))},
                    "foot_right": {"rotation": kf((0.0, (-26, 0, 0)), (0.45, (-8, 0, 0)), (0.9, (-26, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (26, 0, -5)), (0.45, (-34, 0, -7)), (0.9, (26, 0, -5)))},
                    "arm_right": {"rotation": kf((0.0, (-34, 0, 5)), (0.45, (26, 0, 7)), (0.9, (-34, 0, 5)))},
                    "forearm_left": {"rotation": kf((0.0, (12, 0, 0)), (0.45, (30, 0, 0)), (0.9, (12, 0, 0)))},
                    "forearm_right": {"rotation": kf((0.0, (30, 0, 0)), (0.45, (12, 0, 0)), (0.9, (30, 0, 0)))},
                    "body": {"rotation": kf((0.0, (23, 3, 0)), (0.45, (23, -3, 0)), (0.9, (23, 3, 0)))},
                    "neck": {"rotation": kf((0.0, (34, 0, 0)), (0.45, (31, 0, 0)), (0.9, (34, 0, 0)))},
                    "tail_base": {"rotation": kf((0.0, (-26, -9, 0)), (0.45, (-26, 9, 0)), (0.9, (-26, -9, 0)))},
                    "tail_mid": {"rotation": kf((0.0, (-14, -12, 0)), (0.45, (-14, 12, 0)), (0.9, (-14, -12, 0)))},
                    "tail_tuft": {"rotation": kf((0.0, (-6, -14, 0)), (0.45, (-6, 14, 0)), (0.9, (-6, -14, 0)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (0.22, (0, 0.9, 0)),
                                            (0.45, (0, 0, 0)), (0.68, (0, 0.9, 0)), (0.9, (0, 0, 0)))},
                },
            },
            # A maul, not a punch. The weight drops onto the back leg, the whole trunk coils and
            # then falls forward through both arms, and the jaw snaps shut a beat *after* the arms
            # land — a wolf closes on what its claws have already stopped.
            #
            # Triggered from `GenericBeastEntity.doHurtTarget` for the mob and from the player's
            # own swing progress for the transformed-player form.
            "animation.werewolf.attack": {
                "loop": False,
                "animation_length": 0.7,
                "bones": {
                    "body": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (12, 0, 0)),
                                            (0.38, (-18, 0, 0)), (0.7, (0, 0, 0)))},
                    "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (8, 0, 0)),
                                             (0.38, (-12, 0, 0)), (0.7, (0, 0, 0)))},
                    "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-14, 0, 0)),
                                            (0.38, (16, 0, 0)), (0.7, (0, 0, 0)))},
                    "head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-10, 0, 0)),
                                            (0.38, (14, 0, 0)), (0.7, (0, 0, 0)))},
                    "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (34, 0, 0)),
                                           (0.42, (44, 0, 0)), (0.5, (0, 0, 0)),
                                           (0.7, (0, 0, 0)))},
                    # The two arms are deliberately a frame apart. Landing them together reads as
                    # a shove; landing them staggered reads as a mauling.
                    "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-58, 0, -18)),
                                                (0.4, (48, 0, 14)), (0.7, (0, 0, 0)))},
                    "forearm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-26, 0, 0)),
                                                    (0.4, (22, 0, 0)), (0.7, (0, 0, 0)))},
                    "hand_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-18, 0, 0)),
                                                 (0.4, (26, 0, 0)), (0.7, (0, 0, 0)))},
                    "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.22, (-52, 0, 18)),
                                                 (0.47, (44, 0, -14)), (0.7, (0, 0, 0)))},
                    "forearm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.22, (-24, 0, 0)),
                                                     (0.47, (20, 0, 0)), (0.7, (0, 0, 0)))},
                    "hand_right": {"rotation": kf((0.0, (0, 0, 0)), (0.22, (-16, 0, 0)),
                                                  (0.47, (24, 0, 0)), (0.7, (0, 0, 0)))},
                    # Back leg takes the weight, front leg braces.
                    "leg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (18, 0, 0)),
                                                (0.38, (-12, 0, 0)), (0.7, (0, 0, 0)))},
                    "leg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (10, 0, 0)),
                                                 (0.38, (-16, 0, 0)), (0.7, (0, 0, 0)))},
                    "tail_base": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (16, 0, 0)),
                                                 (0.4, (-14, 0, 0)), (0.7, (0, 0, 0)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (0.15, (0, 0, 0.8)),
                                            (0.38, (0, 0, -1.6)), (0.7, (0, 0, 0)))},
                },
            },
            # Struck. Short and sharp — a long recoil on a 0.4s window reads as a stumble, and this
            # has to survive being retriggered while the mob is being hit repeatedly.
            "animation.werewolf.hit": {
                "loop": False,
                "animation_length": 0.4,
                "bones": {
                    "body": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-14, 4, 0)),
                                            (0.4, (0, 0, 0)))},
                    "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-8, -4, 0)),
                                             (0.4, (0, 0, 0)))},
                    "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-18, 0, 0)),
                                            (0.4, (0, 0, 0)))},
                    "head": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-16, 6, 0)),
                                            (0.4, (0, 0, 0)))},
                    "jaw": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (24, 0, 0)),
                                           (0.4, (0, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-22, 0, -20)),
                                                (0.4, (0, 0, 0)))},
                    "arm_right": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (-20, 0, 20)),
                                                 (0.4, (0, 0, 0)))},
                    "leg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.12, (14, 0, 0)),
                                                (0.4, (0, 0, 0)))},
                    "tail_base": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (22, 0, 0)),
                                                 (0.4, (0, 0, 0)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (0.1, (0, -0.4, 1.1)),
                                            (0.4, (0, 0, 0)))},
                },
            },
            # Authored and left unwired — no trigger exists for it, matching the broom
            # brake/summon precedent. Head back, jaw wide, spine straightening.
            "animation.werewolf.howl": {
                "loop": False,
                "animation_length": 2.4,
                "bones": {
                    "body": {"rotation": kf((0.0, (18, 0, 0)), (0.6, (4, 0, 0)),
                                            (1.8, (4, 0, 0)), (2.4, (18, 0, 0)))},
                    "neck": {"rotation": kf((0.0, (30, 0, 0)), (0.6, (-18, 0, 0)),
                                            (1.8, (-20, 0, 0)), (2.4, (30, 0, 0)))},
                    "head": {"rotation": kf((0.0, (-16, 0, 0)), (0.6, (-34, 0, 0)),
                                            (1.8, (-32, 0, 0)), (2.4, (-16, 0, 0)))},
                    "jaw": {"rotation": kf((0.0, (7, 0, 0)), (0.6, (40, 0, 0)),
                                           (1.8, (36, 0, 0)), (2.4, (7, 0, 0)))},
                    "tail_base": {"rotation": kf((0.0, (-20, 0, 0)), (0.8, (-34, 0, 0)), (2.4, (-20, 0, 0)))},
                },
            },
        },
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    fitted, k = fit(BONES, TARGET_HEIGHT_UNITS)
    packer = Packer(TEX)
    for name, _parent, _pivot, _origin, size, _rot in fitted:
        if size:
            packer.place(name, size)
    used = packer.height_used()
    # Sheet width is fixed and the height follows. Snapping the cubes onto whole texels shrank the
    # islands enough that a square 128 sheet would ship four fifths of the PNG empty.
    tex_h = 8 * int(math.ceil(used / 8.0))
    geo = build_geo(packer, fitted, tex_h)

    if not args.force and not is_regenerable(SKIN, MARKER):
        print("werewolf.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    skin, glow = paint(packer, tex_h)

    os.makedirs(os.path.dirname(GEO), exist_ok=True)
    with open(GEO, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
        f.write("\n")
    with open(ANIM, "w", encoding="utf-8") as f:
        json.dump(build_anim(), f, indent=2)
        f.write("\n")
    save(posterise(skin, 20), SKIN, MARKER)
    save(glow, GLOW, MARKER)

    lo, hi = posed_bounds(fitted)
    h = hi[1] - lo[1]
    print(f"werewolf rig: {len(geo['minecraft:geometry'][0]['bones'])} bones, "
          f"UV {used}/{tex_h} rows used, skin {TEX}x{tex_h}")
    print(f"  fitted x{k:.3f} -> {h:.1f}u = {h / 16:.2f} blocks "
          f"(mob box 1.9; player form x1.3 = {h / 16 * 1.3:.2f} vs profile box 2.34)")
    print(f"  width {hi[0] - lo[0]:.1f}u = {(hi[0] - lo[0]) / 16:.2f} blocks (mob box 1.7)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
