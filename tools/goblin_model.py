#!/usr/bin/env python3
"""The goblin: one rig, four Gringotts roles, and the animations for all of them.

Replaces the crudest rig in the mod. `goblin_teller.geo.json` was 7 bones and **6 cubes** — body,
head, two arms, two legs, every one an axis-aligned box, with no ears, no nose, no beard, no hands
and no feet. Its animation file held a single clip whose entire content was a `head` track: there
was **no walk clip at all**, so the goblin slid across the floor with its legs frozen.

And it was green. The shipped skin was olive (`#66745D` family) and `GoblinFormModel.COLOR` is
`0xFF559944` — a Tolkien goblin, not this one. Canon is unusually specific here, so the brief
writes itself. *Philosopher's Stone* ch. 5:

    "...a **swarthy**, clever face, a **pointed beard** and, Harry noticed, **very long fingers
    and feet**."

Griphook in *Deathly Hallows* adds the domed skull, the long nose and the dark slanting eyes. Every
one of those is a shape or a colour decision, and the old rig got all of them wrong. So: swarthy
tan-grey rather than green, a cranium a quarter of the creature's height, a hooked nose, a pointed
beard, big swept-back ears, hands whose fingers are modelled rather than implied, and feet
deliberately longer than a human's would be at this height. If a later art pass makes it green with
neat little hands, it has drawn a different species.

**Two consumers, not one.** Besides the `GoblinTellerEntity` mob, the player heritage form
`goblin_default` draws through this same asset name (`PlayerFormRig`), which is why the base rig
below is prop-free: a player who picked goblin heritage should not be wearing a Gringotts uniform.

**Four variants, one bone table.** The roles are the four goblin professions the mod already
defines in `ProfessionNode` — Vault Clerk, Metalsmith, Bank Steward, Gringotts Director — so the mod
carries one goblin vocabulary instead of two. Each emits its own `.geo.json` and skin under
`goblin/`, differing only by extra cubes hung on the shared bones and by palette. They share
`goblin_teller.animation.json`, which works precisely because the bone names are identical across
all five: `DefaultedGeoModel` resolves model, texture and animation paths independently, and
`DisguisableBeastGeoModel` already demonstrates overriding only the first two.

**LANDMINE: animation rotations are ADDITIVE over the rest rotation.** GeckoLib composes
`bone.baseRotX() + frameSnapshot.getRotX()` (`RenderUtil.translateAndRotateMatrixForBone`), so a
keyframe is a *delta* from the authored pose. Every clip below oscillates around 0.

Run from the repo root:  python tools/goblin_model.py [--force]
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
BASE = "goblin_teller"          # the plain rig + the shared animation file
ANIM = f"{ASSETS}/geckolib/animations/entity/{BASE}.animation.json"
MARKER = marker("goblin_model.py")

TEX_W = 64

# Hitbox from ModEntities: register(..., "goblin_teller", ..., 0.6f, 1.5f). "A head shorter than
# Harry" in the book; 1.5 blocks is where the mod put it, and the rig is fitted to that rather than
# to a number typed here a second time.
BOX_W, BOX_H = 0.6 * 16, 1.5 * 16
TARGET_HEIGHT_UNITS = BOX_H


# --------------------------------------------------------------------------- palette

# Swarthy. Warm grey-tan, weathered — the opposite of the olive-green it replaces. Kept low
# saturation so the variant clothing is what carries colour.
SKIN = hx("#9A8069")
SKIN_LT = hx("#B39880")
SKIN_DK = hx("#6B563F")
SKIN_DEEP = hx("#4E3D2C")
NAIL = hx("#5A4A3A")
EYE = hx("#241A12")
EYE_LT = hx("#C9B48F")
TOOTH = hx("#D8D2BC")
MOUTH = hx("#3A2620")

HAIR = hx("#6E6A63")            # iron grey
HAIR_DK = hx("#474540")
HAIR_WHITE = hx("#C9C4B8")      # the Director has held the post a long time

LINEN = hx("#D8D4C6")
CHARCOAL = hx("#3A3B42")
CHARCOAL_LT = hx("#4E505A")
LEATHER = hx("#6B4A2E")
LEATHER_DK = hx("#4A321E")
SOOT = hx("#2A2724")
BURGUNDY = hx("#5A2230")
BURGUNDY_LT = hx("#7A3242")
BRASS = hx("#B08A3E")
GOLD = hx("#C8A24A")
IRON = hx("#6A6E74")

ROLES = {}
INFLATE = {}
GEOMETRY = {}


def cube(key, role, origin, size, rot=None, pivot=None, inflate=0.0):
    ROLES[key] = role
    if inflate:
        INFLATE[key] = inflate
    GEOMETRY[key] = (origin, size)
    return (key, origin, size, rot, pivot)


def mirror_cube(key, role, origin, size, rot=None, pivot=None, inflate=0.0):
    """The same cube on the other side: negate X, and negate yaw/roll with it."""
    return cube(key, role, (-(origin[0] + size[0]), origin[1], origin[2]), size,
                (rot[0], -rot[1], -rot[2]) if rot else None,
                (-pivot[0], pivot[1], pivot[2]) if pivot else None,
                inflate)


# How far a garment stands off the body it covers, in authored units. Half a texel is the vanilla
# convention for an outer layer and is what the format's `inflate` field exists for.
GARMENT = 0.30


def layer(spec, bone, base_key, key, role, inflate=GARMENT):
    """Clothe a body cube: same origin and size, pushed out by `inflate`.

    **Garments must be `inflate`d, not merely drawn slightly larger.** Authoring a sleeve a fraction
    wider than the arm beneath it survives right up until `snap()` rounds both onto the same whole
    texel — at which point the two are exactly coplanar and z-fight into horizontal stripes, which
    is what the first snapped build did to every sleeve. `inflate` grows the geometry *after* the
    size is fixed and deliberately leaves the UV alone, so a layer can never collide with what it
    covers and never rescales its own texture.
    """
    origin, size = GEOMETRY[base_key]
    spec.setdefault(bone, []).append(cube(f"v.{key}", role, origin, size, inflate=inflate))
    return spec


def sleeves(spec, key, role, stem, inflate=GARMENT):
    """Clothe a mirrored pair of limb cubes in one call.

    Robes with bare arms sticking out of them read as a bib rather than as clothing, and typing the
    right side by hand is how a mirrored pair drifts.
    """
    for side in ("l", "r"):
        bone, cube_name = stem
        layer(spec, f"{bone}_{side}", f"{bone}_{side}.{cube_name}", f"{key}_{side}", role, inflate)
    return spec


def band(spec, bone, base_key, key, role, y, height, inflate=GARMENT * 2):
    """A belt, collar or cuff: the covered part's own footprint, cropped to a height band.

    Sized off the cube underneath rather than typed, because a band typed a fraction wider snaps
    onto the same texel and z-fights — the same trap `layer()` documents. The default inflate is
    twice a garment's so a belt stands proud of the coat it is buckled over, not inside it.
    """
    (ox, _oy, oz), (sx, _sy, sz) = GEOMETRY[base_key]
    spec.setdefault(bone, []).append(
        cube(f"v.{key}", role, (ox, y, oz), (sx, height, sz), inflate=inflate))
    return spec


# --------------------------------------------------------------------------- rig
#
# Design space: 16 units to a block, authored facing -Z with the feet on y=0, then fitted.
#
# Proportions are the point. A goblin is not a short human: the cranium is about a quarter of the
# whole height, the arms hang past the hips, and the legs are short and slightly bowed. Drawing it
# with human proportions at 65% scale is exactly what the placeholder did, and it read as a child.

HIP_Y = 9.4       # where the legs meet the body
SHOULDER_Y = 18.6
HEAD_Y = 21.0     # base of the cranium


def base_bones():
    """The prop-free rig. Every variant is this plus cubes; no variant moves a pivot."""
    B = [("root", None, (0, 0, 0), None, [])]

    # --- torso -------------------------------------------------------------------------
    # Pot-bellied but **narrow across the shoulders**, and that contrast is the whole trick. The
    # first pass gave the chest a 7.8-unit span against an 8.4-unit skull, so head and torso were
    # the same width and the creature rendered as a stack of boxes with the arms swallowed inside
    # the body outline. A goblin is a big head on narrow shoulders over a round gut.
    B.append(("body", "root", (0, HIP_Y, 0), (4, 0, 0), [
        cube("body.belly", "skin", (-3.3, HIP_Y - 0.4, -2.3), (6.6, 5.2, 4.6)),
    ]))
    B.append(("chest", "body", (0, HIP_Y + 4.6, 0), (-3, 0, 0), [
        cube("chest.ribs", "skin", (-2.9, HIP_Y + 4.4, -2.2), (5.8, 5.0, 4.4)),
        cube("chest.collar", "skin", (-2.5, HIP_Y + 8.6, -1.9), (5.0, 1.6, 3.8)),
    ]))
    B.append(("neck", "chest", (0, SHOULDER_Y + 0.4, 0), (6, 0, 0), [
        cube("neck.stem", "skin", (-1.5, SHOULDER_Y + 0.2, -1.5), (3.0, 2.4, 3.0)),
    ]))

    # --- head --------------------------------------------------------------------------
    # The cranium is the silhouette. Wide and domed across the back, narrowing to the brow, with
    # the face hung off the front of it. At 6.6 units of a 24-unit creature it is a quarter of
    # the height, which is what makes a goblin read as a goblin at distance.
    head = [
        cube("head.cranium", "skin", (-4.0, HEAD_Y, -3.6), (8.0, 5.4, 7.2)),
        # The dome. A single box skull is a helmet; the narrower cap, set back off the brow, is
        # what turns it into the high bald cranium Griphook is drawn with.
        cube("head.dome", "skin", (-3.1, HEAD_Y + 5.0, -2.4), (6.2, 2.2, 5.6)),
        cube("head.face", "skin", (-3.2, HEAD_Y + 0.6, -5.6), (6.4, 4.6, 2.2)),
        cube("head.brow", "skin", (-3.5, HEAD_Y + 4.2, -5.2), (7.0, 1.6, 2.0)),
        # The nose: long, hooked, and the second-loudest thing on the head after the ears. Pitched
        # forward so it hangs over the mouth rather than pointing out of the face like a beak.
        cube("head.nose_bridge", "skin", (-1.1, HEAD_Y + 2.4, -6.6), (2.2, 2.6, 1.8)),
        cube("head.nose_hook", "skin", (-1.3, HEAD_Y + 0.8, -6.9), (2.6, 2.2, 2.0),
             (16, 0, 0), (0, HEAD_Y + 2.4, -6.6)),
        # No mouth cube. A 4.4 x 0.9 slab of dark brown across the lower face rendered as an open
        # wound at 64px; the mouth is a painted line on `head.face` instead, which is what every
        # readable vanilla face does.
        cube("head.eye_l", "eye", (1.1, HEAD_Y + 2.9, -5.9), (1.6, 1.2, 0.6)),
        cube("head.eye_r", "eye", (-2.7, HEAD_Y + 2.9, -5.9), (1.6, 1.2, 0.6)),
        # Ears: thin, pointed, raked **back and up** off the top of the skull. Cube rotation
        # rather than bones — they never move independently, and two more bones for a static fan
        # is pure bookkeeping.
        #
        # Both the size and the yaw sign were wrong first time round. Bedrock's +Y rotation
        # carries a +X point toward +Z, i.e. backwards on a model that faces -Z, so the negative
        # yaw raked them *forwards*; combined with 5.6 units of length they rendered as horizontal
        # wings sticking out past the shoulders. Back, up, and short enough to stay inside the
        # head's read.
        cube("head.ear_l", "skin", (3.8, HEAD_Y + 2.8, -0.8), (3.8, 2.4, 0.7),
             (0, 30, 46), (3.8, HEAD_Y + 3.4, -0.5)),
        cube("head.ear_r", "skin", (-7.6, HEAD_Y + 2.8, -0.8), (3.8, 2.4, 0.7),
             (0, -30, -46), (-3.8, HEAD_Y + 3.4, -0.5)),
    ]
    B.append(("head", "neck", (0, HEAD_Y, 0), (-4, 0, 0), head))

    # Beard on its own bone so the Director can wear a longer one without a second head, and so
    # it swings a beat behind the head in the clips.
    #
    # **Pointed**, which the book names, and therefore narrow: at the head's own width it stopped
    # reading as a beard and became a grey bib across the collarbones.
    B.append(("beard", "head", (0, HEAD_Y + 0.4, -4.4), None, [
        cube("beard.jaw", "hair", (-1.5, HEAD_Y - 1.6, -5.5), (3.0, 2.2, 2.2)),
        cube("beard.point", "hair", (-0.7, HEAD_Y - 3.2, -5.1), (1.4, 1.8, 1.6)),
    ]))

    # --- arms --------------------------------------------------------------------------
    # Long. The knuckles hang level with the bottom of the belly, which together with the short
    # legs is what stops this reading as a scaled-down person.
    for side, sx in (("l", 1), ("r", -1)):
        m = cube if side == "l" else mirror_cube
        B.append((f"arm_{side}", "chest", (sx * 3.6, SHOULDER_Y - 0.6, 0), (-4, 0, sx * -4), [
            m(f"arm_{side}.upper", "skin", (2.8, SHOULDER_Y - 5.6, -1.5), (2.2, 5.4, 3.0)),
        ]))
        B.append((f"forearm_{side}", f"arm_{side}", (sx * 4.0, SHOULDER_Y - 5.6, 0), (10, 0, 0), [
            m(f"forearm_{side}.lower", "skin", (3.1, SHOULDER_Y - 10.6, -1.3), (1.9, 5.2, 2.6)),
        ]))
        # THE canon tell. The palm is small and the fingers are individually modelled and long —
        # a single mitten cube here throws away the one detail the book stops to point out.
        hand = [m(f"hand_{side}.palm", "skin", (3.0, SHOULDER_Y - 13.2, -1.4), (2.1, 2.6, 2.8))]
        for i in range(3):
            hand.append(m(f"hand_{side}.finger{i}", "skin",
                          (3.1 + i * 0.7, SHOULDER_Y - 16.6, -1.2 + i * 0.2),
                          (0.7, 3.6, 0.8), (0, 0, -6 + i * 5),
                          (4.0, SHOULDER_Y - 13.2, 0)))
        hand.append(m(f"hand_{side}.thumb", "skin", (2.5, SHOULDER_Y - 15.0, -1.6),
                      (0.8, 2.4, 0.9), (0, 0, 24), (3.2, SHOULDER_Y - 13.0, 0)))
        B.append((f"hand_{side}", f"forearm_{side}", (sx * 4.0, SHOULDER_Y - 13.2, 0), None, hand))

    # --- legs --------------------------------------------------------------------------
    # Short and bowed, and the feet are the other tell: long, flat and splayed, carrying most of
    # this creature's contact with the floor.
    for side, sx in (("l", 1), ("r", -1)):
        m = cube if side == "l" else mirror_cube
        B.append((f"leg_{side}", "root", (sx * 2.0, HIP_Y, 0), (0, 0, sx * -5), [
            m(f"leg_{side}.thigh", "skin", (0.8, HIP_Y - 5.0, -1.6), (2.8, 5.2, 3.2)),
        ]))
        B.append((f"shin_{side}", f"leg_{side}", (sx * 2.2, HIP_Y - 5.0, 0), (0, 0, sx * 4), [
            m(f"shin_{side}.calf", "skin", (1.1, HIP_Y - 9.4, -1.4), (2.4, 4.6, 2.8)),
        ]))
        B.append((f"foot_{side}", f"shin_{side}", (sx * 2.3, HIP_Y - 9.4, 0), None, [
            m(f"foot_{side}.sole", "skin", (1.0, 0, -4.6), (2.8, 1.6, 6.6)),
            m(f"foot_{side}.toes", "nail", (1.1, 0, -6.0), (2.6, 1.1, 1.6)),
        ]))
    return B


# --------------------------------------------------------------------------- variants
#
# A variant is (extra cubes, palette overrides). Never a pivot change: the shared animation file
# is only shared because the skeleton is byte-identical across all five geometries.

def _clerk():
    """Vault Clerk — the rank-1 profession, and what the old `goblin_teller` was trying to be."""
    spec = {
        # The ledger, carried the way a clerk carries one — clamped under the arm, not held out.
        "forearm_l": [
            cube("v.ledger", "book", (5.4, SHOULDER_Y - 10.4, -2.6), (1.6, 4.4, 5.2)),
        ],
        # Quill behind the ear. A couple of pale units that read instantly at mob distance.
        "head": [
            cube("v.quill", "quill", (2.6, HEAD_Y + 4.2, -0.4), (0.8, 4.6, 0.8),
                 (-18, 0, -22), (2.6, HEAD_Y + 4.2, -0.4)),
        ],
    }
    layer(spec, "chest", "chest.ribs", "waistcoat", "cloth")
    layer(spec, "body", "body.belly", "skirt", "cloth")
    sleeves(spec, "sleeve", "cloth", ("arm", "upper"))
    # Shirt showing past the waistcoat sleeve — the pale forearms are what make a clerk read as
    # office staff rather than as a guard.
    sleeves(spec, "cuff", "linen", ("forearm", "lower"))
    band(spec, "chest", "chest.collar", "collar", "linen", HIP_Y + 8.6, 1.6)
    return spec


def _metalsmith():
    """Metalsmith — Ragnuk's trade, the one that forged Gryffindor's sword."""
    spec = {
        "body": [
            # Hammer at the hip: a haft and a head, both readable in silhouette.
            cube("v.haft", "leather_dk", (3.6, HIP_Y - 3.4, -0.6), (1.0, 5.0, 1.0)),
            cube("v.hammer", "iron", (2.8, HIP_Y - 4.8, -1.5), (2.6, 1.8, 2.6)),
        ],
    }
    layer(spec, "chest", "chest.ribs", "apron_bib", "leather")
    layer(spec, "body", "body.belly", "apron_skirt", "leather")
    band(spec, "body", "body.belly", "belt", "leather_dk", HIP_Y + 0.6, 1.4)
    # Arms bare to the shoulder. A smith works with his sleeves off, and it is the one silhouette
    # difference that needs no extra geometry at all.
    return spec


def _banker():
    """Bank Steward — the goblin who decides who may withdraw what."""
    spec = {
        "body": [
            # The keys. Goblins hold the keys; a steward wears them where they can be seen.
            cube("v.keyring", "brass", (3.0, HIP_Y - 3.2, -1.0), (2.0, 2.4, 1.0)),
            cube("v.key", "brass", (3.4, HIP_Y - 5.2, -0.9), (1.0, 2.2, 1.0)),
        ],
        "chest": [
            cube("v.buttons", "brass", (-0.5, HIP_Y + 5.0, -3.4), (1.0, 4.0, 1.0)),
        ],
    }
    layer(spec, "chest", "chest.ribs", "coat", "coat")
    layer(spec, "body", "body.belly", "tails", "coat")
    sleeves(spec, "sleeve", "coat", ("arm", "upper"))
    sleeves(spec, "forearm", "coat", ("forearm", "lower"))
    band(spec, "chest", "chest.collar", "collar", "coat_lt", HIP_Y + 8.8, 2.4)
    # Leather strap, brass only at the buckle. A brass *band* round the whole waist reads as a gold
    # apron at mob distance — the metal has to be a highlight, not a surface.
    band(spec, "body", "body.belly", "belt", "leather_dk", HIP_Y + 0.8, 1.2)
    spec["body"].append(cube("v.buckle", "brass", (-1.0, HIP_Y + 0.6, -3.4), (2.0, 1.6, 1.0)))
    band(spec, "forearm_l", "forearm_l.lower", "cuff_l", "brass",
         SHOULDER_Y - 11.0, 1.3, GARMENT * 3)
    band(spec, "forearm_r", "forearm_r.lower", "cuff_r", "brass",
         SHOULDER_Y - 11.0, 1.3, GARMENT * 3)
    return spec


def _director():
    """Gringotts Director — Ragnok's chair. Chain of office, tall collar, longest beard."""
    spec = {
        "chest": [
            # Chain of office: a yoke over the shoulders and a pendant hanging off it. Set well
            # clear of the robe's own inflate so it reads as jewellery lying on top of the cloth.
            cube("v.chain_l", "gold", (1.2, HIP_Y + 8.2, -3.6), (1.0, 4.0, 1.0),
                 (0, 0, -16), (1.7, HIP_Y + 9.2, 0)),
            cube("v.chain_r", "gold", (-2.2, HIP_Y + 8.2, -3.6), (1.0, 4.0, 1.0),
                 (0, 0, 16), (-1.7, HIP_Y + 9.2, 0)),
            cube("v.pendant", "gold", (-1.0, HIP_Y + 6.2, -3.7), (2.0, 2.0, 1.0)),
        ],
        # Picks up exactly where `beard.point` stops. Authored 3 units lower first time round, it
        # hung detached in mid-air over the robe and read as a white bib rather than a beard.
        "beard": [
            cube("v.beard_long", "hair", (-0.8, HEAD_Y - 5.0, -5.0), (1.6, 2.0, 1.6)),
        ],
        "palette": {"hair": HAIR_WHITE},
    }
    layer(spec, "chest", "chest.ribs", "robe", "robe")
    layer(spec, "body", "body.belly", "skirt", "robe")
    # Wide ceremonial sleeves: a heavier inflate than an office coat, so the Director is visibly
    # bulkier through the arms than the Steward standing next to him.
    sleeves(spec, "sleeve", "robe", ("arm", "upper"), GARMENT * 2)
    sleeves(spec, "forearm", "robe", ("forearm", "lower"), GARMENT * 2)
    band(spec, "chest", "chest.collar", "collar", "robe_lt", HIP_Y + 8.8, 3.0, GARMENT * 3)
    return spec


VARIANTS = {
    "clerk": _clerk,
    "metalsmith": _metalsmith,
    "banker": _banker,
    "director": _director,
}


def variant_bones(name):
    """The base rig plus one role's cubes, attached to bones that already exist.

    Variant cube keys are re-namespaced under the variant here rather than being typed that way,
    because several roles legitimately want a cube called the same thing in a different material —
    the clerk's collar is linen, the steward's is coat cloth, the Director's is trimmed robe. Left
    unprefixed they share one `ROLES` entry and whichever variant was built last decides what all
    three are painted as.
    """
    # Base first: `layer()`/`band()` size themselves off the body cube they cover, so `GEOMETRY`
    # has to be populated before the variant spec is built.
    bones = base_bones()

    spec = VARIANTS[name]()
    extras = {}
    for bone, cubes in ((k, v) for k, v in spec.items() if k != "palette"):
        out = []
        for key, origin, size, crot, cpivot in cubes:
            keyed = f"{name}.{key.split('.', 1)[-1]}"
            ROLES[keyed] = ROLES[key]
            if key in INFLATE:
                INFLATE[keyed] = INFLATE[key]
            out.append((keyed, origin, size, crot, cpivot))
        extras[bone] = out

    bones = [(bone, parent, pivot, rot, cubes + extras.get(bone, []))
             for bone, parent, pivot, rot, cubes in bones]
    unknown = set(extras) - {b[0] for b in bones}
    if unknown:
        raise SystemExit(f"variant {name} hangs cubes on bones that do not exist: {sorted(unknown)}")
    return bones


def variant_palette(name):
    return VARIANTS[name]().get("palette", {}) if name else {}


# --------------------------------------------------------------------------- fitting

def _rot(rot):
    """Bedrock XYZ euler -> matrix, matching `beast_preview.euler_matrix` exactly.

    GeckoLib composes `mulPose(Z); mulPose(Y); mulPose(X)`, i.e. a point rotates about X first and
    Z last, and Bedrock's X and Y run opposite to the maths convention.
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
            infl = INFLATE.get(_key, 0.0)
            for dx in (-infl, size[0] + infl):
                for dy in (-infl, size[1] + infl):
                    for dz in (-infl, size[2] + infl):
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

    **This is not cosmetic — a fractional cube size tears its own texture.** GeckoLib lays out box
    UV from `Math.floor(size)` (`BakedModelFactory.buildQuad`), while `boxuv.Packer`/`faces()` — and
    every hand-drawn Blockbench sheet — lay it out from the rounded-up size. On a cube of size 3.2
    the game therefore reads the north face at `u+3` while the painter wrote it at `u+4`: every face
    samples a strip of its neighbour, and the top and bottom faces sample the island's unused corner,
    which is transparent. That is the holes.

    Any dimension under 1.0 is worse still: it floors to **zero**, so the whole cube maps to a
    zero-area UV. Fingers at 0.7 and chain links at 0.9 were doing exactly that.

    Whole sizes make `floor` and the packer's rounding agree by construction, so this cannot come
    back. Fractional *positions* are fine and are left alone — only the extents matter to UV.
    """
    return max(1.0, float(round(size)))


def fit(bones, k, dx, dy):
    """Apply a scale and offset already decided elsewhere, and snap every cube onto whole texels.

    Unlike the basilisk's `fit()`, the factor is **not** derived per-variant: all five geometries
    must share one skeleton for the shared animation file to work, so the plain rig decides the
    scale and every variant is emitted through the same numbers. A robe is allowed to change the
    silhouette; it is not allowed to change how tall the goblin is.

    Snapping happens here rather than at authoring time because the scale factor is what makes the
    sizes fractional: 2.9 authored units become 2.45 after fitting.
    """
    def sc(v):
        return (v[0] * k + dx, v[1] * k + dy, v[2] * k)

    def sized(origin, size):
        scaled = [v * k for v in size]
        snapped = [snap(v) for v in scaled]
        # Keep the cube where it was drawn: absorb the rounding either side of its own centre.
        moved = sc(origin)
        return (tuple(moved[i] - (snapped[i] - scaled[i]) / 2.0 for i in range(3)), tuple(snapped))

    out = []
    for name, parent, pivot, rot, cubes in bones:
        placed = []
        for key, origin, size, crot, cpivot in cubes:
            o, sz = sized(origin, size)
            if key in INFLATE:
                # Inflate is in the same units as the geometry, so it scales with it.
                INFLATE[key] = INFLATE[key] * k
            placed.append((key, o, sz, crot, sc(cpivot) if cpivot else None))
        out.append((name, parent, sc(pivot), rot, placed))
    return out


def base_transform():
    """Scale + offset that lands the *plain* rig on the mob's box, shared by every variant."""
    lo, hi = posed_bounds(base_bones())
    k = TARGET_HEIGHT_UNITS / (hi[1] - lo[1])
    return k, -(lo[0] + hi[0]) / 2.0 * k, -lo[1] * k


def build_geo(name, bones, packer, tex_h):
    lo, hi = posed_bounds(bones)
    out = []
    for bone, parent, pivot, rot, cubes in bones:
        b = {"name": bone, "pivot": [round(v, 3) for v in pivot]}
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
                if key in INFLATE:
                    c["inflate"] = round(INFLATE[key], 3)
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
                "identifier": f"geometry.{name}",
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

# Base colour per role. One flat tone each — everything else is derived from it, so a material can
# be re-tinted by changing one entry and the shading stays consistent.
def palette(overrides):
    return {
        "skin": SKIN,
        "nail": NAIL,
        "eye": EYE,
        "mouth": MOUTH,
        "hair": overrides.get("hair", HAIR),
        "cloth": CHARCOAL,
        "linen": LINEN,
        "book": LEATHER_DK,
        "quill": LINEN,
        "leather": LEATHER,
        "leather_dk": LEATHER_DK,
        "iron": IRON,
        "coat": BURGUNDY,
        "coat_lt": BURGUNDY_LT,
        "brass": BRASS,
        "robe": BURGUNDY,
        "robe_lt": BURGUNDY_LT,
        "gold": GOLD,
    }


def bevel(d, rect, base):
    """Flat fill with a one-pixel bevel: lit along the top, shaded along the bottom and right.

    This is the whole readability fix. The first pass filled every face with `mottle()` at 14–40%
    density in two or three tones, which at 64px is a 1–3 pixel blotch every few pixels: the rig
    rendered as camouflage, not as skin, and — because the noise is per-face — no two faces of the
    same cube agreed at the seam. A box model reads as separate volumes because each face carries a
    consistent edge, not because it carries texture.

    Note there is deliberately no top-face-lighter / bottom-face-darker term here. Minecraft already
    applies directional lighting per face; painting it in a second time washes out every top surface.
    """
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    if h > 2:
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=shade(base, 1.09))
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.84))
    if w > 2:
        d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.90))


def speckle(d, rect, base, seed, density=7, spread=0.94):
    """A very light, low-contrast dither — enough to break a flat slab, not enough to read as dirt.

    Deterministic, so the same rig always paints the same way.
    """
    x0, y0, w, h = rect
    lo, hi = shade(base, spread), shade(base, 2.0 - spread)
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
            if (n >> 7) % 100 < density:
                d.point((x, y), fill=lo if (n >> 11) & 1 else hi)


def paint(packer, tex_h, overrides):
    img = Image.new("RGBA", (TEX_W, tex_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pal = palette(overrides)

    for key, (uv, size) in packer.placed.items():
        role = ROLES[key]
        base = pal[role]
        f = faces(uv, size)
        seed = sum(ord(c) for c in key)

        for rect in f.values():
            bevel(d, rect, base)
        # Skin and cloth get a whisper of grain; everything small or hard-surfaced stays flat.
        if role in ("skin", "cloth", "leather", "coat", "robe", "hair"):
            for rect in f.values():
                speckle(d, rect, base, seed, density=8 if role == "skin" else 6)

        # --- hand-placed detail. Features are drawn, never sprinkled ------------------------

        if key == "head.cranium":
            # A fringe at the back of the skull, bald over the crown — the classic pattern on an
            # old bald head. Kept to the **back** face and one ragged pixel row on each side: a
            # third of the side faces filled with flat grey read as ear-muffs bolted to the head.
            x0, y0, w, h = f["south"]
            band = max(1, h // 3)
            d.rectangle([x0, y0 + h - band, x0 + w - 1, y0 + h - 1], fill=HAIR)
            for x in range(x0, x0 + w, 2):
                d.point((x, y0 + h - band - 1), fill=HAIR_DK)
            for fname in ("east", "west"):
                x0, y0, w, h = f[fname]
                for x in range(x0, x0 + w, 3):
                    d.point((x, y0 + h - 2), fill=HAIR)
                    d.point((x, y0 + h - 1), fill=HAIR_DK)

        if key == "head.brow":
            # A hard shadow line under the brow ridge, which is what makes the eyes read as
            # deep-set rather than stuck on.
            x0, y0, w, h = f["north"]
            d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=SKIN_DEEP)

        if key == "head.nose_hook":
            # Nostrils. Two pixels, and they are the difference between "hooked nose" and "extra
            # cube on the face".
            x0, y0, w, h = f["bottom"]
            d.point((x0, y0 + h - 1), fill=SKIN_DEEP)
            d.point((x0 + w - 1, y0 + h - 1), fill=SKIN_DEEP)
            for fname in ("east", "west"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=SKIN_DK)

        if key == "head.face":
            # The mouth: a thin dark line low on the face with a couple of teeth over it, drawn
            # rather than modelled (see the note where the mouth cube used to be).
            x0, y0, w, h = f["north"]
            my = y0 + h - 2
            d.line([(x0 + 1, my), (x0 + w - 2, my)], fill=MOUTH)
            for tx in range(x0 + 2, x0 + w - 2, 2):
                d.point((tx, my - 1), fill=TOOTH)
            # Cheek shadow either side, so the muzzle has some form under the flat lighting.
            d.point((x0, my - 1), fill=SKIN_DK)
            d.point((x0 + w - 1, my - 1), fill=SKIN_DK)

        if role == "eye":
            # Flat, dark, with one pale glint in the corner. "Shrewd" is the word the books use,
            # and at this size a single highlight pixel is the entire expression.
            for fname, rect in f.items():
                x0, y0, w, h = rect
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=EYE)
            for fname in ("north", "east", "west"):
                x0, y0, w, h = f[fname]
                d.point((x0 + w - 1, y0), fill=EYE_LT)

        if role == "mouth":
            for fname, rect in f.items():
                x0, y0, w, h = rect
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=MOUTH)
            # Teeth only along the top edge of the front face — a full grin at 64px is a smear.
            x0, y0, w, h = f["north"]
            for tx in range(x0 + 1, x0 + w - 1, 2):
                d.point((tx, y0), fill=TOOTH)

        if role == "nail":
            # Toenails: solid claw colour down the front face only.
            x0, y0, w, h = f["north"]
            for cx in range(x0, x0 + w, 2):
                d.line([(cx, y0), (cx, y0 + h - 1)], fill=shade(NAIL, 1.15))

        if role == "hair" and key.endswith(("jaw", "point", "beard_long")):
            # Strands down the beard, so it reads as hair rather than a grey wedge.
            for fname in ("north", "east", "west", "south"):
                x0, y0, w, h = f[fname]
                for sx in range(x0, x0 + w, 2):
                    d.line([(sx, y0), (sx, y0 + h - 1)], fill=shade(base, 0.80))

        if role in ("gold", "brass", "iron"):
            # Metal: one bright edge and one dark one. Two lines beat any gradient at this size.
            for fname in ("north", "south", "east", "west", "top"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0), (x0 + w - 1, y0)], fill=shade(base, 1.4))
                if h > 1:
                    d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.62))

        if role == "quill":
            # A feather: pale barbs either side of a dark shaft.
            for fname in ("north", "south", "east", "west"):
                x0, y0, w, h = f[fname]
                d.line([(x0 + w // 2, y0), (x0 + w // 2, y0 + h - 1)], fill=HAIR_DK)

        if role == "book":
            # Page block along one edge of the ledger, spine down the other.
            x0, y0, w, h = f["east"]
            d.rectangle([x0, y0, x0 + max(0, w // 2), y0 + h - 1], fill=LINEN)
            for fname in ("north", "south"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0), (x0, y0 + h - 1)], fill=shade(LEATHER_DK, 0.7))

        if role in ("linen", "coat_lt", "robe_lt"):
            # A darker hem so a collar has an edge instead of blending into the coat under it.
            for fname in ("north", "east", "west", "south"):
                x0, y0, w, h = f[fname]
                d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=shade(base, 0.68))

    return img


# --------------------------------------------------------------------------- animation
#
# Deltas from the rest pose — see the module docstring. All three clips oscillate around 0, and
# every bone named here exists on all five geometries because they share one skeleton.

def kf(*pairs):
    return {str(t): [round(float(v), 2) for v in vec] for t, vec in pairs}


def build_anim():
    # --- idle -----------------------------------------------------------------------------
    # A goblin at a counter is never quite still: it breathes, it shifts its weight off one short
    # leg onto the other, and its long fingers work. The finger flex is the detail that makes the
    # canon hands worth having modelled at all.
    idle = {
        "body": {"rotation": kf((0.0, (0, -2, 0)), (1.6, (0, 2, 0)), (3.2, (0, -2, 0)))},
        "chest": {"rotation": kf((0.0, (-1.5, 1, 0)), (1.6, (0.5, -1, 0)), (3.2, (-1.5, 1, 0)))},
        "neck": {"rotation": kf((0.0, (2, -3, 0)), (1.1, (0, 4, 0)),
                                (2.2, (3, -2, 0)), (3.2, (2, -3, 0)))},
        "head": {"rotation": kf((0.0, (-2, 4, 1)), (1.1, (1, -5, -1)),
                                (2.2, (-3, 3, 2)), (3.2, (-2, 4, 1)))},
        "arm_l": {"rotation": kf((0.0, (2, 0, -2)), (1.6, (-2, 0, -4)), (3.2, (2, 0, -2)))},
        "arm_r": {"rotation": kf((0.0, (-2, 0, 2)), (1.6, (2, 0, 4)), (3.2, (-2, 0, 2)))},
        "forearm_l": {"rotation": kf((0.0, (-4, 0, 0)), (1.6, (4, 0, 0)), (3.2, (-4, 0, 0)))},
        "forearm_r": {"rotation": kf((0.0, (4, 0, 0)), (1.6, (-4, 0, 0)), (3.2, (4, 0, 0)))},
        "hand_l": {"rotation": kf((0.0, (0, 0, 0)), (0.8, (-14, 0, 0)),
                                  (1.4, (2, 0, 0)), (3.2, (0, 0, 0)))},
        "hand_r": {"rotation": kf((0.0, (0, 0, 0)), (1.9, (-12, 0, 0)),
                                  (2.5, (2, 0, 0)), (3.2, (0, 0, 0)))},
        "beard": {"rotation": kf((0.0, (0, 0, 0)), (1.6, (3, 0, 0)), (3.2, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (1.6, (0, 0.25, 0)), (3.2, (0, 0, 0)))},
    }

    # --- walk -----------------------------------------------------------------------------
    # Short legs mean a high cadence: 0.8s per cycle, not the ~1.0 a human-scaled biped gets. The
    # stride is small, the arm swing is long because the arms are long, and the roll on the root
    # is the waddle that comes off a wide, bow-legged stance.
    walk = {
        "leg_l": {"rotation": kf((0.0, (-34, 0, 0)), (0.4, (30, 0, 0)), (0.8, (-34, 0, 0)))},
        "leg_r": {"rotation": kf((0.0, (30, 0, 0)), (0.4, (-34, 0, 0)), (0.8, (30, 0, 0)))},
        "shin_l": {"rotation": kf((0.0, (30, 0, 0)), (0.2, (8, 0, 0)),
                                  (0.4, (2, 0, 0)), (0.8, (30, 0, 0)))},
        "shin_r": {"rotation": kf((0.0, (2, 0, 0)), (0.4, (30, 0, 0)),
                                  (0.6, (8, 0, 0)), (0.8, (2, 0, 0)))},
        "foot_l": {"rotation": kf((0.0, (10, 0, 0)), (0.4, (-14, 0, 0)), (0.8, (10, 0, 0)))},
        "foot_r": {"rotation": kf((0.0, (-14, 0, 0)), (0.4, (10, 0, 0)), (0.8, (-14, 0, 0)))},
        "arm_l": {"rotation": kf((0.0, (26, 0, -3)), (0.4, (-30, 0, -3)), (0.8, (26, 0, -3)))},
        "arm_r": {"rotation": kf((0.0, (-30, 0, 3)), (0.4, (26, 0, 3)), (0.8, (-30, 0, 3)))},
        "forearm_l": {"rotation": kf((0.0, (-14, 0, 0)), (0.4, (-4, 0, 0)), (0.8, (-14, 0, 0)))},
        "forearm_r": {"rotation": kf((0.0, (-4, 0, 0)), (0.4, (-14, 0, 0)), (0.8, (-4, 0, 0)))},
        "body": {"rotation": kf((0.0, (2, 4, 0)), (0.4, (2, -4, 0)), (0.8, (2, 4, 0)))},
        "chest": {"rotation": kf((0.0, (0, -3, 0)), (0.4, (0, 3, 0)), (0.8, (0, -3, 0)))},
        "head": {"rotation": kf((0.0, (0, -3, 0)), (0.4, (0, 3, 0)), (0.8, (0, -3, 0)))},
        "beard": {"rotation": kf((0.0, (-4, 0, 0)), (0.4, (2, 0, 0)), (0.8, (-4, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 0.5, 0)), (0.4, (0, 0, 0)),
                                (0.6, (0, 0.5, 0)), (0.8, (0, 0, 0))),
                 "rotation": kf((0.0, (0, 0, 2.5)), (0.4, (0, 0, -2.5)), (0.8, (0, 0, 2.5)))},
    }

    # --- bow ------------------------------------------------------------------------------
    # "The goblin bowed them through the silver doors." Triggered from `mobInteract`, so it plays
    # exactly as the Gringotts screen opens. Bend from the waist, head down, one long-fingered
    # hand sweeping across the body — a bank clerk's bow, not a courtier's.
    bow = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (24, 0, 0)),
                                (0.95, (23, 0, 0)), (1.6, (0, 0, 0)))},
        "chest": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (10, 0, 0)),
                                 (0.95, (9, 0, 0)), (1.6, (0, 0, 0)))},
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (12, 0, 0)),
                                (0.95, (11, 0, 0)), (1.6, (0, 0, 0)))},
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (8, 0, 0)),
                                (0.95, (7, 0, 0)), (1.6, (0, 0, 0)))},
        "arm_l": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (-38, 0, -22)),
                                 (0.95, (-36, 0, -21)), (1.6, (0, 0, 0)))},
        "forearm_l": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (-30, 0, 0)),
                                     (0.95, (-28, 0, 0)), (1.6, (0, 0, 0)))},
        "arm_r": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (-14, 0, 12)),
                                 (0.95, (-13, 0, 11)), (1.6, (0, 0, 0)))},
        "beard": {"rotation": kf((0.0, (0, 0, 0)), (0.45, (-22, 0, 0)),
                                 (0.95, (-20, 0, 0)), (1.6, (0, 0, 0)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.45, (0, -0.6, 0)),
                                (0.95, (0, -0.6, 0)), (1.6, (0, 0, 0)))},
    }

    return {
        "format_version": "1.8.0",
        "animations": {
            f"animation.{BASE}.idle": {"loop": True, "animation_length": 3.2, "bones": idle},
            f"animation.{BASE}.walk": {"loop": True, "animation_length": 0.8, "bones": walk},
            f"animation.{BASE}.bow": {"loop": False, "animation_length": 1.6, "bones": bow},
        },
    }


# --------------------------------------------------------------------------- emit

def geo_path(asset):
    return f"{ASSETS}/geckolib/models/entity/{asset}.geo.json"


def skin_path(asset):
    return f"{ASSETS}/textures/entity/{asset}.png"


def emit(asset, identifier, bones, overrides):
    """Pack, paint and write one geometry + skin pair. Returns (bones, cubes, rows, tex_h)."""
    packer = Packer(TEX_W)
    used = packer.place_sorted([(key, size) for b in bones for key, _, size, _, _ in b[4]])
    tex_h = 8 * int(math.ceil(used / 8.0))
    geo = build_geo(identifier, bones, packer, tex_h)
    skin = paint(packer, tex_h, overrides)

    path = geo_path(asset)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
        f.write("\n")
    os.makedirs(os.path.dirname(skin_path(asset)), exist_ok=True)
    save(posterise(skin, 40), skin_path(asset), MARKER)
    return len(bones), sum(len(b[4]) for b in bones), used, tex_h


def check_anim_bones(all_bones, anim):
    """Every animated bone must exist on every geometry — the premise of one shared clip set."""
    used = set()
    for clip in anim["animations"].values():
        used |= set(clip["bones"])
    missing = {}
    for asset, bones in all_bones.items():
        gap = sorted(used - {b[0] for b in bones})
        if gap:
            missing[asset] = gap
    return missing


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    if not args.force and not is_regenerable(skin_path(BASE), MARKER):
        print(f"{BASE}.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    k, dx, dy = base_transform()

    ROLES.clear()
    fitted = {BASE: fit(base_bones(), k, dx, dy)}
    for name in VARIANTS:
        fitted[f"goblin/{name}"] = fit(variant_bones(name), k, dx, dy)

    anim = build_anim()
    missing = check_anim_bones(fitted, anim)
    if missing:
        print(f"ERROR: animation targets bones some rigs lack: {missing}", file=sys.stderr)
        return 1

    with open(ANIM, "w", encoding="utf-8") as f:
        json.dump(anim, f, indent=2)
        f.write("\n")

    lo, hi = posed_bounds(fitted[BASE])
    print(f"goblin rig fitted x{k:.3f} -> {hi[1] - lo[1]:.1f}u tall = {(hi[1] - lo[1]) / 16:.2f} "
          f"blocks (box {BOX_H / 16:.2f}); {hi[0] - lo[0]:.1f}u wide = "
          f"{(hi[0] - lo[0]) / 16:.2f} blocks (box {BOX_W / 16:.2f})")
    for asset, bones in fitted.items():
        name = asset.split("/")[-1]
        identifier = BASE if asset == BASE else f"goblin_{name}"
        nb, nc, rows, tex_h = emit(asset, identifier, bones,
                                   variant_palette(None if asset == BASE else name))
        print(f"  {asset:<20} {nb} bones, {nc} cubes, UV {rows}/{tex_h} rows, skin {TEX_W}x{tex_h}")
    print(f"  clips: {', '.join(sorted(anim['animations']))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
