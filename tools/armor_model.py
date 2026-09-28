#!/usr/bin/env python3
"""Wizarding armour: rigs and worn skins, built to the written art direction.

Five sets:

    student_robe        school robe, ankle-length, soft rolled collar, A-line drape
    auror_robe          midnight-blue duelling coat, standing collar, tails, gunmetal
    death_eater_robe    floor-length, deep hood, heavy vertical drape
    wizard_hat          pointed felt, wide waved brim, bent tip
    death_eater_mask    full-face bone mask, six castings on one sculpt

Six things are dictated by GeckoLib, by box geometry or by the wearer rather than chosen,
and each one is a trap that has already cost a rebuild:

  - **Bone names are fixed.** `GeoArmorRenderer#getBoneNameForSegment` resolves a slot to
    `armorBody`, `armorLeftArm`, `armorRightBoot` and five others. An unknown bone is
    dropped *silently*, so a typo is an invisible robe rather than an error.

  - **A detail cube must clear the shell it sits on.** `inflate` grows the torso box on
    every axis, so a collar narrower than `chest + inflate` is not a subtle collar, it is
    *inside the chest* and renders as nothing. An earlier cut put an 8.7-wide collar over a
    10-wide inflated chest on all three robes, and a 0.15-proud lapel on the Death Eater:
    the robes came out as featureless slabs. Every band, lapel, cuff and epaulette below is
    sized from the shell it laps over, and the shells are kept modest (0.55-0.8) so there is
    room to lap over them.

  - **Cube sizes are integers.** GeckoLib lays box UV out from `Math.floor(size)`
    (`BakedModelFactory.buildQuad`) while `boxuv.Packer` rounds up, so a fractional size
    samples a texel off the island and picks up transparent gashes — see `tools/uv_check.py`.
    Shape that needs a fraction gets it from the *origin* or from `inflate`, never the size.

  - **The head is 8x8x8 at y24, and it is not ours.** Anything a chest piece puts above the
    shoulders has to wrap *outside* that box or it is swallowed by it. The old hood sat at
    z 1.4..4.8 — entirely inside the skull — which is why no cut of the Death Eater robe
    ever showed a hood. The hood below is a five-piece shell at |x| >= 4.2 and z >= 4.2 with
    an open front, and tall collars are C-shaped (sides + back) for the same reason.

  - **The A-line drape hangs off `armorBody`, not the legs.** Geometry parented to
    `armorLeftLeg` / `armorRightLeg` rotates with that thigh, so a skirt split down the
    middle scissors open at every step. Hung from the torso it swings as one piece. The legs
    item contributes the trouser seen below the hem; the drape belongs to the chest.

  - **Cloth needs a controller, not a mesh.** There are no edge loops to add — these are
    boxes. Secondary motion is bone rotation, so the drape is built as child bones
    (`robeSkirt` -> `robeHem`) hanging off `armorBody`, and `robeHood` off `armorHead`,
    rather than as loose
    cubes. Nothing animates them yet; a controller can, without re-sculpting.

Cubes are authored in vanilla humanoid space: feet at y=0, head 24..32, facing -Z, because
each armour bone is snapped onto the vanilla `ModelPart` of the same name. That space is
built on the 4-wide default arm; on a slim (Alex) player the sleeve stands proud by half a
pixel, exactly as vanilla armour does.

UVs are packed automatically through `boxuv.Packer`, and the skin is painted through the
same rectangles the packer handed out, so geometry and texture cannot disagree.

Run from the repo root:  python tools/armor_model.py [--force] [--only student_robe,...]
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = f"{ASSETS}/geckolib/models/armor"
ANIM_DIR = f"{ASSETS}/geckolib/animations/armor"
TEX_DIR = f"{ASSETS}/textures/armor"
MARKER = marker("armor_model.py")

# Sheet size is chosen per set, not fixed: the head pieces need a fraction of what a robe
# does, and a 128px sheet for an eight-cube mask is wasted download. `pack_for` walks these
# in order and takes the first that holds the set's islands.
SHEET_SIZES = (32, 64, 128)

# The eight bones GeoArmorRenderer can ask for. HEAD sets use only the first.
ARMOR_BONES = {
    "head": ("armorHead", (0, 24, 0)),
    "body": ("armorBody", (0, 24, 0)),
    "arm_r": ("armorRightArm", (-5, 22, 0)),
    "arm_l": ("armorLeftArm", (5, 22, 0)),
    "leg_r": ("armorRightLeg", (-1.9, 12, 0)),
    "leg_l": ("armorLeftLeg", (1.9, 12, 0)),
    "boot_r": ("armorRightBoot", (-1.9, 12, 0)),
    "boot_l": ("armorLeftBoot", (1.9, 12, 0)),
}

# --------------------------------------------------------------------------- palettes
#
# Same hues as before, pulled a little richer and given one more step of separation between
# `dark` and `light`: the shading below ramps every hanging face from light at the shoulder
# to dark at the hem, and a palette whose ends sit close together ramps into mud.

CLOTH = {
    "student_robe": {
        # Warm black wool. The three robe sets shipped as three near-identical blue-blacks and
        # read as one outfit at ten blocks (visual consistency audit, H7), so each now owns a
        # corner: the school robe is the warm one, with a muted gold crest and buttons.
        "base": hx("#3A3434"), "dark": hx("#1E1A1B"), "light": hx("#554B4A"),
        "trim": hx("#5E5450"), "accent": hx("#A8925A"), "leather": hx("#1C1718"),
    },
    "auror_robe": {
        # A clearly blue duelling coat, a step lighter than the other two. Silver and gunmetal
        # only — the brief rules gold out, so nothing here reaches for brass.
        "base": hx("#26385C"), "dark": hx("#131D33"), "light": hx("#3D5488"),
        "trim": hx("#7A8AA8"), "accent": hx("#C4CEDC"), "leather": hx("#141A28"),
    },
    "death_eater_robe": {
        # Black with a faint cool undertone and no warm tone anywhere. Kept fractionally
        # off zero so the vertical folds read at all.
        # Lifted a step (texture audit 2026-09-27): at #1A1A24 the folds went to black in any
        # light below noon and the robe read as a hole in the player. Still the darkest set.
        "base": hx("#24242F"), "dark": hx("#131319"), "light": hx("#3C3C50"),
        "trim": hx("#3E3E4C"), "accent": hx("#62667C"), "leather": hx("#0E0E14"),
    },
    "wizard_hat": {
        # Felt: matte, a shade warmer than the robes, over a worn leather band.
        "base": hx("#35333F"), "dark": hx("#1C1C24"), "light": hx("#4C4A5A"),
        "trim": hx("#4A4557"), "accent": hx("#8C7A4E"), "leather": hx("#38291B"),
    },
    "death_eater_mask": {
        # Bone rather than silver: "carved, pale, bone-like or porcelain", sickly ivory.
        "base": hx("#D9D0BB"), "dark": hx("#948973"), "light": hx("#F2ECDF"),
        "trim": hx("#786D5B"), "accent": hx("#131319"), "leather": hx("#6C6151"),
    },
}

# How many shades each sheet keeps. Vanilla sits at 4-8 per *texture*; an armour sheet is
# four materials at once, so it gets more — but not so many that the fold ramps stop
# banding, which is the thing that makes generated shading read as painted rather than
# computed.
POSTERISE = {
    "student_robe": 26, "auror_robe": 26, "death_eater_robe": 22,
    "wizard_hat": 22, "death_eater_mask": 20,
}

# --------------------------------------------------------------------------- rigs
#
# Each entry: (bone key, cube name, origin, size, inflate[, rotation, pivot])
#
# `inflate` is how a garment sits proud of the body without re-typing the vanilla box: it
# grows the geometry and leaves the UV alone, which is exactly what a layer of cloth is.
# Anything that is a *shape* rather than a layer — a skirt, a cuff, a hat brim — is a real
# cube instead, because inflate cannot change proportions. Sizes are integers (see the
# module docstring); the half-pixel placement lives in the origin.

TORSO = (-4, 12, -2, 8, 12, 4)
ARM_R = (-8, 12, -2, 4, 12, 4)
ARM_L = (4, 12, -2, 4, 12, 4)
LEG_R = (-3.9, 0, -2, 4, 12, 4)
LEG_L = (-0.1, 0, -2, 4, 12, 4)


def _box(spec, inflate=0.0):
    x, y, z, w, h, d = spec
    return (x, y, z), (w, h, d), inflate


# Bone key may be "<slot>" or "<slot>:<child>" — a child bone hangs off the slot bone and
# inherits its snap, which is what gives the drape something to be animated on later.
RIGS = {
    "student_robe": [
        # Slim through the body. The volume of a robe belongs to the drape, not the torso:
        # a 0.6 shell plus 0.6 sleeves plus shoulder caps made a 18-wide slab with a skirt
        # under it, which is the whole reason the last three cuts read as a bin bag.
        ("body", "chest", *_box(TORSO, 0.4)),
        # Two-layer soft collar. Both clear the 0.4 shell (x +-4.4, z +-2.4).
        ("body", "collar", (-4.8, 21.8, -2.8), (10, 2, 6), 0.0),
        ("body", "collar_lip", (-5.2, 23.4, -3.2), (11, 1, 7), 0.0),
        ("body", "placket", (-1.0, 13.0, -3.0), (2, 10, 1), 0.0),
        # The drape: one slim core that closes the corners, wrapped in four panels that are
        # *rotated* outward from the waist. Stacking wider and wider boxes cannot make an
        # A-line — it makes a wedding cake, with a lit ledge at every step. Eight degrees
        # over twelve units is a real taper, and it costs four cubes.
        ("body:robeSkirt", "skirt", (-4.5, 1.5, -2.5), (9, 12, 5), 0.0),
        ("body:robeSkirt", "skirt_low", (-5.5, 1.4, -3.5), (11, 7, 7), 0.0),
        ("body:robeSkirt/robeHem", "panel_f", (-5.0, 1.2, -3.6), (10, 12, 2), 0.0,
         (8, 0, 0), (0, 13.2, -3.6)),
        ("body:robeSkirt/robeHem", "panel_b", (-5.0, 1.2, 1.6), (10, 12, 2), 0.0,
         (-8, 0, 0), (0, 13.2, 3.6)),
        ("body:robeSkirt/robeHem", "panel_r", (-5.6, 1.2, -3.5), (2, 12, 7), 0.0,
         (0, 0, -8), (-5.6, 13.2, 0)),
        ("body:robeSkirt/robeHem", "panel_l", (3.6, 1.2, -3.5), (2, 12, 7), 0.0,
         (0, 0, 8), (5.6, 13.2, 0)),
        # Sleeve: slim, with a turned cuff. No bell — a 6x5x6 block hung off the forearm
        # reads as a bucket, not as cloth.
        ("arm_r", "sleeve_r", *_box(ARM_R, 0.35)),
        ("arm_l", "sleeve_l", *_box(ARM_L, 0.35)),
        ("arm_r", "cuff_r", (-8.7, 11.4, -2.7), (5, 2, 5), 0.0),
        ("arm_l", "cuff_l", (3.7, 11.4, -2.7), (5, 2, 5), 0.0),
        ("leg_r", "trouser_r", *_box(LEG_R, 0.35)),
        ("leg_l", "trouser_l", *_box(LEG_L, 0.35)),
        ("boot_r", "shoe_r", (-4.3, 0.0, -2.9), (5, 3, 6), 0.0),
        ("boot_l", "shoe_l", (-0.7, 0.0, -2.9), (5, 3, 6), 0.0),
    ],
    "auror_robe": [
        # Tailored: the tightest shell of the three, one closure, one belt, and a coat that
        # falls in three tails. No yoke — it was a third horizontal band across a chest that
        # already carries a collar and a belt, and stacked bands are what made this set
        # read as a stack of trays.
        ("body", "chest", *_box(TORSO, 0.35)),
        ("body", "collar", (-4.8, 22.4, -2.8), (10, 2, 6), 0.0),
        # Back-only standing part: a full ring at this height would be inside the head.
        ("body", "collar_stand", (-3.5, 23.6, 2.8), (7, 2, 3), 0.0),
        ("body", "breast", (-4.4, 13.4, -3.0), (7, 10, 1), 0.0),
        ("body", "belt", (-5.0, 11.4, -3.0), (10, 2, 6), 0.0),
        ("body:robeSkirt", "coat", (-4.6, 7.5, -2.6), (9, 6, 5), 0.0),
        # Four falling panels, each swung out from the hip, with the front pair split
        # down the centre: that gap is the coat opening, and a box filling it (which is
        # what an extra closed skirt cube did) turns the set back into one block.
        ("body:robeSkirt/robeHem", "tail_b", (-5.5, 0.8, 1.8), (11, 9, 2), 0.0,
         (-6, 0, 0), (0, 9.8, 2.8)),
        ("body:robeSkirt/robeHem", "tail_r", (-5.0, 1.5, -3.2), (4, 8, 2), 0.0,
         (6, 0, 0), (0, 9.8, -3.2)),
        ("body:robeSkirt/robeHem", "tail_l", (1.0, 1.5, -3.2), (4, 8, 2), 0.0,
         (6, 0, 0), (0, 9.8, -3.2)),
        ("body:robeSkirt/robeHem", "tail_side_r", (-5.4, 1.2, -3.0), (2, 8, 6), 0.0,
         (0, 0, -6), (-5.4, 9.8, 0)),
        ("body:robeSkirt/robeHem", "tail_side_l", (3.4, 1.2, -3.0), (2, 8, 6), 0.0,
         (0, 0, 6), (5.4, 9.8, 0)),
        ("arm_r", "sleeve_r", *_box(ARM_R, 0.35)),
        ("arm_l", "sleeve_l", *_box(ARM_L, 0.35)),
        ("arm_r", "cuff_r", (-8.7, 11.2, -2.7), (5, 2, 5), 0.0),
        ("arm_l", "cuff_l", (3.7, 11.2, -2.7), (5, 2, 5), 0.0),
        ("leg_r", "trouser_r", *_box(LEG_R, 0.35)),
        ("leg_l", "trouser_l", *_box(LEG_L, 0.35)),
        ("boot_r", "boot_r", (-4.3, 0.0, -2.9), (5, 6, 6), 0.0),
        ("boot_l", "boot_l", (-0.7, 0.0, -2.9), (5, 6, 6), 0.0),
    ],
    "death_eater_robe": [
        ("body", "chest", *_box(TORSO, 0.55)),
        # The one horizontal this set keeps: broad shoulders are the silhouette.
        ("body", "yoke", (-5.2, 20.6, -3.2), (10, 3, 6), 0.0),
        # Collar open at the front, so it stands as high as the jaw without being eaten by
        # the head box.
        ("body", "collar_r", (-5.4, 21.8, -3.2), (2, 4, 7), 0.0),
        ("body", "collar_l", (3.4, 21.8, -3.2), (2, 4, 7), 0.0),
        ("body", "collar_back", (-4.5, 21.8, 2.4), (9, 4, 3), 0.0),
        # Hood: a shell outside the 8x8x8 head, open at the front, with a brow that
        # overhangs the eyes and a peak that falls back off the crown.
        #
        # Hung off **armorHead**, not armorBody, so it turns when the wearer looks around —
        # parented to the torso it slides off the face the moment the head moves. The chest
        # piece still draws it because `HoodedArmorRenderer` adds the HEAD segment to the
        # chest slot; without that override GeckoLib asks for armorHead in the helmet slot
        # only, and a chest-slot hood on that bone is silently absent.
        #
        # Nothing in the shell reaches below y24, which is the head's own pivot: cloth hung
        # lower turns with the head and sweeps out through the shoulders. The gap at the neck
        # is covered by the collar instead, which stays on the body — which is also why
        # `hood_fall`, the old cloth apron from hood to shoulders, is gone.
        ("head:robeHood", "hood_back", (-5.0, 24.0, 4.2), (10, 8, 2), 0.0),
        ("head:robeHood", "hood_side_r", (-5.2, 24.0, -3.8), (1, 8, 10), 0.0),
        ("head:robeHood", "hood_side_l", (4.2, 24.0, -3.8), (1, 8, 10), 0.0),
        ("head:robeHood", "hood_top", (-4.5, 31.4, -4.2), (9, 2, 10), 0.0),
        ("head:robeHood", "hood_peak", (-3.0, 32.6, 1.8), (6, 2, 4), 0.0),
        ("head:robeHood", "hood_brow", (-5.0, 29.0, -4.6), (10, 3, 2), 0.0),
        # The same hood worn down, on its own bone. Both bones are always in the file;
        # `DeathEaterRobeRenderer` shows one and skips the other per frame, because a
        # GeckoLib model cannot be re-cut at runtime — the switch has to be visibility.
        # Down means bunched behind the neck and falling to a point down the back, so it
        # reads as a hood and not as a backpack.
        ("body:robeHoodDown", "hood_down_lip", (-5.0, 23.4, 2.4), (10, 1, 5), 0.0),
        ("body:robeHoodDown", "hood_down_roll", (-5.5, 20.6, 2.6), (11, 3, 4), 0.0),
        ("body:robeHoodDown", "hood_down_fold", (-4.0, 15.8, 3.0), (8, 5, 3), 0.0),
        ("body:robeHoodDown", "hood_down_tip", (-3.0, 13.2, 3.4), (6, 3, 2), 0.0),
        # Wrap-over front: the left panel stands further forward than the right.
        ("body", "lapel_r", (-4.6, 12.8, -3.2), (5, 11, 1), 0.0),
        ("body", "lapel_l", (-0.4, 12.8, -3.45), (5, 11, 1), 0.0),
        # Floor-length, flared harder than the school robe and carrying a train that sweeps
        # back off the hem.
        ("body:robeSkirt", "skirt", (-5.0, 0.8, -3.0), (10, 13, 6), 0.0),
        ("body:robeSkirt", "skirt_low", (-6.0, 0.6, -4.0), (12, 8, 8), 0.0),
        ("body:robeSkirt/robeHem", "panel_f", (-5.5, 0.5, -4.0), (11, 13, 2), 0.0,
         (7, 0, 0), (0, 13.5, -4.0)),
        ("body:robeSkirt/robeHem", "panel_b", (-5.5, 0.5, 2.0), (11, 13, 2), 0.0,
         (-7, 0, 0), (0, 13.5, 4.0)),
        ("body:robeSkirt/robeHem", "panel_r", (-6.1, 0.5, -4.0), (2, 13, 8), 0.0,
         (0, 0, -7), (-6.1, 13.5, 0)),
        ("body:robeSkirt/robeHem", "panel_l", (4.1, 0.5, -4.0), (2, 13, 8), 0.0,
         (0, 0, 7), (6.1, 13.5, 0)),
        ("body:robeSkirt/robeHem", "train", (-5.0, 0.5, 3.4), (10, 4, 2), 0.0,
         (-16, 0, 0), (0, 6.5, 3.4)),
        # Heavy sleeves that hang *below* the hand rather than bulging at the forearm.
        ("arm_r", "sleeve_r", *_box(ARM_R, 0.5)),
        ("arm_l", "sleeve_l", *_box(ARM_L, 0.5)),
        ("arm_r", "bell_r", (-9.2, 8.0, -3.2), (5, 5, 6), 0.0),
        ("arm_l", "bell_l", (4.2, 8.0, -3.2), (5, 5, 6), 0.0),
        ("arm_r", "cuff_r", (-9.0, 12.0, -3.0), (5, 2, 6), 0.0),
        ("arm_l", "cuff_l", (4.0, 12.0, -3.0), (5, 2, 6), 0.0),
        ("leg_r", "trouser_r", *_box(LEG_R, 0.4)),
        ("leg_l", "trouser_l", *_box(LEG_L, 0.4)),
        ("boot_r", "boot_r", (-4.3, 0.0, -2.9), (5, 5, 6), 0.0),
        ("boot_l", "boot_l", (-0.7, 0.0, -2.9), (5, 5, 6), 0.0),
    ],
    "wizard_hat": [
        # Head is 8 tall (24..32). The crown climbs from 32.4 to 46 — fourteen units, about
        # 1.75x head height, inside the brief's 1.5-1.8.
        #
        # The brim is two crossed plates plus a dipped tab front and back rather than one
        # slab: a single box can only ever be a flat disc, and two crossed plates alone read
        # as a plus sign. The tabs are what turn it into a wave.
        ("head", "brim_under", (-7.5, 31.0, -6.5), (15, 1, 13), 0.0),
        ("head", "brim_over", (-6.5, 31.6, -7.5), (13, 1, 15), 0.0),
        ("head", "brim_dip_f", (-4.5, 30.4, -6.9), (9, 1, 2), 0.0),
        ("head", "brim_dip_b", (-4.5, 30.7, 4.9), (9, 1, 2), 0.0),
        ("head", "band", (-5.0, 32.2, -5.0), (10, 2, 10), 0.0),
        ("head", "crown", (-4.5, 32.4, -4.5), (9, 4, 9), 0.0),
        # Five shortening steps instead of three, each overlapping the one below by 1.2 so
        # the bend stays closed and the lean grows smoothly rather than kinking at the tip.
        ("head", "taper1", (-4.0, 35.8, -4.0), (8, 3, 8), 0.0, (-3, 0, 0), (0, 36, 0)),
        ("head", "taper2", (-3.5, 37.6, -3.5), (7, 3, 7), 0.0, (-8, 0, 0), (0, 36, 0)),
        ("head", "taper3", (-3.0, 39.4, -3.0), (6, 3, 6), 0.0, (-14, 0, 0), (0, 36, 0)),
        ("head", "taper4", (-2.0, 41.2, -2.0), (4, 3, 4), 0.0, (-22, 0, 0), (0, 36, 0)),
        # The tip bends rather than continuing straight.
        ("head", "tip", (-1.5, 42.8, -1.5), (3, 3, 3), 0.0, (-26, 0, 0), (0, 36, 0)),
    ],
    "death_eater_mask": [
        # A full-face mask over the head, not a plate hung on the front of it: the sides
        # wrap the temples and the jaw closes under the chin. The plate is half a pixel
        # wider than the skull on each side, so its edge reads as a rim rather than
        # z-fighting the player's own cheek.
        ("head", "face", (-4.5, 24.5, -4.7), (9, 7, 1), 0.0),
        # Sunk into the plate rather than sat on top of it. Proud of the forehead it reads
        # as a headband across the skull, which is what it did twice before.
        ("head", "brow", (-4.5, 29.8, -5.0), (9, 1, 1), 0.0),
        ("head", "nose", (-1.0, 26.5, -5.0), (2, 3, 1), 0.0),
        ("head", "cheek_r", (-4.4, 25.5, -4.85), (2, 3, 1), 0.0),
        ("head", "cheek_l", (2.4, 25.5, -4.85), (2, 3, 1), 0.0),
        ("head", "jaw", (-2.5, 23.4, -4.4), (5, 2, 2), 0.0),
        ("head", "wrap_r", (-4.8, 25.2, -4.5), (1, 5, 4), 0.0),
        ("head", "wrap_l", (3.8, 25.2, -4.5), (1, 5, 4), 0.0),
    ],
}

HEAD_SETS = ("wizard_hat", "death_eater_mask")

# Pieces that hang rather than wrap, and so gather into vertical folds.
HANGING = ("skirt", "skirt_low", "coat", "panel_f", "panel_b", "panel_r", "panel_l",
           "tail_b", "tail_r", "tail_l", "tail_side_r", "tail_side_l",
           "train", "bell_r", "bell_l",
           "lapel_r", "lapel_l", "hood_back", "hood_side_r",
           "hood_side_l", "hood_down_roll", "hood_down_fold", "hood_down_tip")

# Pieces made of something other than the set's cloth. Without this every robe rendered as
# one unbroken column of colour from collar to sole — the boots vanished into the trouser
# and the trouser into the skirt, so the set had no internal edges at all.
LEATHER = ("shoe_r", "shoe_l", "boot_r", "boot_l", "band", "belt")

# Trousers sit a shade under the outer robe, so the leg reads as beneath the garment
# rather than as part of it.
UNDERLAYER = ("trouser_r", "trouser_l")

# Pieces whose bottom edge is a real hem: two dark rows with a lit one above them, which is
# what gives cloth its weight where it stops.
HEMMED = ("panel_f", "panel_b", "panel_r", "panel_l", "tail_b", "tail_r",
          "tail_l", "tail_side_r", "tail_side_l", "train", "bell_r",
          "bell_l", "hood_down_fold", "hood_down_tip")


# Pivot for each child bone, in the same vanilla humanoid space as the cubes. A drape bone
# pivots where it hangs from — the skirt at the waist, the hem at the skirt's lower edge,
# the hood at the shoulder line — so a rotation on it swings the cloth instead of sliding it.
CHILD_PIVOTS = {
    "robeSkirt": (0, 12, 0),
    "robeHem": (0, 6, 0),
    "robeHood": (0, 24, 0),
    "robeHoodDown": (0, 22, 2.6),
}


def build_geo(name, packer, tex):
    """Group the set's cubes under their bones and pack a UV island for each.

    A bone key is either a slot ("body") or a chain of child bones hanging off one
    ("body:robeSkirt/robeHem"). Children exist so the drape has something to be animated on:
    GeckoLib snaps the named armour bone onto its vanilla ModelPart and everything parented
    beneath it comes along, so a controller can rotate `robeSkirt` later and the hem follows.
    """
    by_bone = {}
    order = []
    for entry in RIGS[name]:
        bone_key, cube_name, origin, size, inflate = entry[:5]
        rotation = entry[5] if len(entry) > 5 else None
        pivot = entry[6] if len(entry) > 6 else None
        uv, _ = packer.placed[f"{name}/{cube_name}"]
        cube = {
            "origin": [round(v, 2) for v in origin],
            "size": [round(v, 2) for v in size],
            "uv": list(uv),
        }
        if inflate:
            cube["inflate"] = inflate
        if rotation:
            cube["rotation"] = list(rotation)
            cube["pivot"] = list(pivot)
        if bone_key not in by_bone:
            order.append(bone_key)
        by_bone.setdefault(bone_key, []).append(cube)

    bones = []
    emitted = set()

    def emit_chain(key):
        """Emit the slot bone, then each child in the chain, parented as it goes."""
        slot, _, chain = key.partition(":")
        bone_name, bone_pivot = ARMOR_BONES[slot]
        if bone_name not in emitted:
            bones.append({"name": bone_name, "pivot": list(bone_pivot), "cubes": []})
            emitted.add(bone_name)
        parent = bone_name
        for child in [c for c in chain.split("/") if c]:
            if child not in emitted:
                bones.append({
                    "name": child,
                    "parent": parent,
                    "pivot": list(CHILD_PIVOTS[child]),
                    "cubes": [],
                })
                emitted.add(child)
            parent = child
        return parent

    # Slot bones first, in ARMOR_BONES order, so the file reads head-to-foot.
    for slot in ARMOR_BONES:
        for key in order:
            if key.split(":")[0] != slot:
                continue
            target = emit_chain(key)
            for bone in bones:
                if bone["name"] == target:
                    bone["cubes"].extend(by_bone[key])
                    break

    # A bone with no cubes of its own is a legitimate parent; one with none anywhere is not.
    bones = [b for b in bones if b["cubes"] or any(
        o.get("parent") == b["name"] for o in bones)]

    head_only = name in HEAD_SETS
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + name,
                "texture_width": tex,
                "texture_height": tex,
                "visible_bounds_width": 3,
                "visible_bounds_height": 4 if head_only else 3,
                "visible_bounds_offset": [0, 2.5 if head_only else 1.5, 0],
            },
            "bones": bones,
        }],
    }


def pack_for(name):
    """Pack the set's UV islands onto the smallest sheet that holds them.

    Islands are placed tallest-first. Packing in authoring order leaves each shelf as tall
    as its tallest island and wastes everything under the short ones — the student robe
    overflowed a 64px sheet by 5px that way, on twelve cubes.
    """
    items = [(f"{name}/{e[1]}", e[3]) for e in RIGS[name]]
    for tex in SHEET_SIZES:
        packer = Packer(tex)
        if packer.place_sorted(items) <= tex:
            return packer, tex
    raise SystemExit(f"{name}: UV islands do not fit {SHEET_SIZES[-1]}px")


def build_anim(name):
    """No clips yet — armour is drawn from the wearer's pose, not from its own timeline."""
    return {
        "_comment": [
            "No clips. The wearer's pose drives every armour bone: GeoArmorRenderer snaps "
            "each bone onto the matching vanilla ModelPart every frame, so a robe already "
            "walks and swings without a keyframe.",
            "This file exists because the model declares it. Add a clip here before naming "
            "one from a controller — GeckoLib throws on a clip it cannot find.",
        ],
        "format_version": "1.8.0",
        "animations": {},
    }


# --------------------------------------------------------------------------- painting
#
# Everything below paints *onto* what is already there rather than stamping flat rectangles,
# because what reads as fabric is three passes interacting: a vertical ramp (cloth is lit at
# the shoulder and dark in the hem), evenly spaced pleats (a shadow column with a lit column
# beside it), and a very sparse weave speckle. Flat colour plus heavy noise — the first cut —
# reads as camouflage, not cloth.


def _hash(x, y, seed):
    n = (x * 374761393) ^ (y * 668265263) ^ (seed * 1274126177)
    n = (n ^ (n >> 13)) & 0xFFFFFFFF
    return (n * 1274126177) & 0xFFFFFFFF


def _tint(px, x, y, col, t):
    """Blend `col` into whatever is already at (x, y). Keeps the ramp underneath."""
    px[x, y] = mix(px[x, y], col, t)


def face_base(pal, fname, matte=False):
    """Directional shading. Top catches light, underside falls away, flanks sit between."""
    if fname == "top":
        return mix(pal["base"], pal["light"], 0.30 if matte else 0.55)
    if fname == "bottom":
        return mix(pal["base"], pal["dark"], 0.85)
    if fname in ("east", "west"):
        return mix(pal["base"], pal["dark"], 0.30)
    return pal["base"]


def ramp(d, rect, top_col, bottom_col):
    """Vertical falloff down a face: cloth is lit where it leaves the shoulder."""
    x0, y0, w, h = rect
    span = max(1, h - 1)
    for i in range(h):
        t = i / span
        d.line([(x0, y0 + i), (x0 + w - 1, y0 + i)],
               fill=mix(top_col, bottom_col, t * t * 0.55 + t * 0.45))


def weave(px, rect, pal, seed, density=5):
    """A couple of percent of tonal variation — a weave, not a pattern.

    The first cut speckled with `dark`, `light` and `trim` straight out of the palette at
    20%. On a 5px face that is not a weave, it is camouflage: the robes rendered as blotchy
    lumps and the hat as scorched rock. Two near-tones at 6% is the whole budget.
    """
    x0, y0, w, h = rect
    lo = mix(pal["base"], pal["dark"], 0.32)
    hi = mix(pal["base"], pal["light"], 0.28)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            r = (_hash(x, y, seed) >> 7) % 100
            if r < density:
                _tint(px, x, y, lo, 0.50)
            elif r < density * 2:
                _tint(px, x, y, hi, 0.32)


def pleats(px, rect, pal, seed, spacing=4, strength=0.38):
    """Evenly spaced folds: a shadow column with a lit column against it.

    Spacing rather than scattering is deliberate. Cloth gathers at a regular pitch, and a
    hashed scatter — what this did before — produces one or two random dark stripes that
    read as a stain. The phase moves per cube so neighbouring panels do not line up.
    """
    x0, y0, w, h = rect
    if w < 5 or h < 3:
        return
    phase = seed % spacing
    inset = 1 if h > 3 else 0
    for i in range(w):
        if (i + phase) % spacing:
            continue
        for y in range(y0 + inset, y0 + h):
            _tint(px, x0 + i, y, pal["dark"], strength)
            if i + 1 < w:
                _tint(px, x0 + i + 1, y, pal["light"], strength * 0.45)


def paint_cloth(d, px, rect, pal, fname, seed, hanging, matte=False):
    x0, y0, w, h = rect
    base = face_base(pal, fname, matte)
    if fname in ("top", "bottom"):
        # A ledge of cloth is lit evenly across its width. Ramping it the way a hanging face
        # is ramped draws a light-to-dark gradient over a horizontal surface, and posterise
        # then bands that gradient into stripes — which is what turned the hem's upper
        # surface into a smear of diagonals.
        #
        # The top of a *drape* step is not lit at all: it is the ring of cloth tucked under
        # the wider piece above it. Painting it with the lit top tone drew a bright ledge
        # around every step and turned an A-line into a wedding cake.
        if hanging and fname == "top":
            base = mix(pal["base"], pal["dark"], 0.6)
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    else:
        ramp(d, rect, mix(base, pal["light"], 0.26), mix(base, pal["dark"], 0.42))
    weave(px, rect, pal, seed)
    if hanging and fname in ("north", "south", "east", "west"):
        pleats(px, rect, pal, seed, strength=0.38 if fname in ("north", "south") else 0.22)
    # Form shadow down both outside edges of a front or back face, so a 10-wide panel of
    # cloth turns away from the light instead of ending flat.
    if fname in ("north", "south") and w >= 5:
        for y in range(y0, y0 + h):
            _tint(px, x0, y, pal["dark"], 0.38)
            _tint(px, x0 + w - 1, y, pal["dark"], 0.38)


def paint_leather(d, px, rect, pal, fname, seed):
    """Hide, not cloth: horizontal grain, a lit top edge and a heavy bottom edge."""
    x0, y0, w, h = rect
    base = face_base(pal, fname)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    for i in range(h):
        if i % 3 == 1:
            for x in range(x0, x0 + w):
                _tint(px, x, y0 + i, pal["dark"], 0.30)
    weave(px, rect, pal, seed, density=4)
    if h >= 2:
        for x in range(x0, x0 + w):
            _tint(px, x, y0, pal["light"], 0.40)
            _tint(px, x, y0 + h - 1, pal["dark"], 0.55)


def paint_bone(d, px, rect, pal, fname, cube, seed):
    """Carved bone: one flat tone, a lit top edge, a shaded underside, faint grain.

    A per-row ramp corrugates a 7px face into visible stripes, and the brow is a 1px-tall
    ridge whose lit top edge *is* its whole face — which turned it into a white sweatband
    across the forehead in three earlier cuts. It gets shade only.
    """
    x0, y0, w, h = rect
    base = face_base(pal, fname, matte=True)
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            r = (_hash(x, y, seed) >> 9) % 100
            if r < 7:
                _tint(px, x, y, pal["dark"], 0.22)
            elif r < 12:
                _tint(px, x, y, pal["light"], 0.28)
    if cube != "brow":
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=mix(base, pal["light"], 0.75))
    if h > 2 or cube == "brow":
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=mix(base, pal["dark"], 0.6))


def cube_palette(pal, cube):
    """The set palette, restated for whatever this particular cube is made of."""
    if cube in LEATHER:
        hide = pal["leather"]
        return {"base": hide, "dark": shade(hide, 0.62), "light": shade(hide, 1.45),
                "trim": shade(hide, 0.5), "accent": pal["accent"], "leather": hide}
    if cube in UNDERLAYER:
        return dict(pal, base=mix(pal["base"], pal["dark"], 0.45),
                    light=pal["base"], dark=shade(pal["dark"], 0.85))
    return pal


def paint_set(name, packer, tex, variant=None):
    """One skin for one set. `variant` recolours the mask castings off a shared layout."""
    set_pal = CLOTH[name]
    img = Image.new("RGBA", (tex, tex), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    px = img.load()
    bone_set = name == "death_eater_mask"

    for key, (uv, size) in sorted(packer.placed.items()):
        if not key.startswith(name + "/"):
            continue
        cube = key.split("/", 1)[1]
        pal = cube_palette(set_pal, cube)
        f = faces(uv, size)
        seed = sum(ord(c) * (i + 7) for i, c in enumerate(cube)) + (variant or 0) * 31

        for fname, rect in f.items():
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            if bone_set:
                paint_bone(d, px, rect, pal, fname, cube, seed)
            elif cube in LEATHER:
                paint_leather(d, px, rect, pal, fname, seed)
            else:
                paint_cloth(d, px, rect, pal, fname, seed,
                            hanging=cube in HANGING, matte=(name == "wizard_hat"))

        _detail(d, px, name, cube, f, pal, variant)

    return posterise(img, POSTERISE[name])


def _hem_band(d, px, f, pal):
    """Two dark rows and a lit one above them — the weight at the bottom of a fall."""
    for fname in ("north", "south", "east", "west"):
        x0, y0, w, h = f[fname]
        if w <= 0 or h <= 0:
            continue
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=pal["dark"])
        if h > 2:
            for x in range(x0, x0 + w):
                _tint(px, x, y0 + h - 2, pal["dark"], 0.55)
        if h > 3:
            for x in range(x0, x0 + w):
                _tint(px, x, y0 + h - 3, pal["light"], 0.22)


def _edge_light(d, px, f, pal, strength=0.55, sides=("north", "south", "east", "west")):
    """Catch light along the top row of each named face — how a fold announces itself."""
    for fname in sides:
        x0, y0, w, h = f[fname]
        if w <= 0 or h <= 0:
            continue
        for x in range(x0, x0 + w):
            _tint(px, x, y0, pal["light"], strength)


def _detail(d, px, name, cube, f, pal, variant):
    """Per-cube marks: hems, seams, closures, buckles, mask chasing."""
    if cube in HEMMED:
        _hem_band(d, px, f, pal)

    if cube.startswith("collar"):
        # A collar is read from its top edge: lit along the fold, dark where it meets the
        # neck. Without both it is just another band of the same cloth.
        _edge_light(d, px, f, pal, 0.6)
        if cube in ("collar", "collar_lip"):
            # Notch the throat. A 10-wide band with a lit edge all the way across reads
            # as a shelf bolted to the shoulders; two dark columns at the centre turn it
            # into a collar that opens.
            x0, y0, w, h = f["north"]
            for cx in (x0 + w // 2 - 1, x0 + w // 2):
                for y in range(y0, y0 + h):
                    _tint(px, cx, y, pal["dark"], 0.7)
        for fname in ("north", "south", "east", "west"):
            x0, y0, w, h = f[fname]
            if h > 1:
                for x in range(x0, x0 + w):
                    _tint(px, x, y0 + h - 1, pal["dark"], 0.5)

    if cube == "yoke":
        # Structured panel across the shoulders: one step lighter than the chest, closed by
        # a seam along its lower edge.
        for fname in ("north", "south", "east", "west"):
            x0, y0, w, h = f[fname]
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    _tint(px, x, y, pal["light"], 0.16)
            if h > 1:
                d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)],
                       fill=mix(pal["base"], pal["dark"], 0.7))

    if cube == "breast" or cube.startswith("lapel"):
        # Same cloth as the coat, one step lighter, with a lit leading edge — that edge is
        # the whole read: it is what says "this panel is lapped over that one".
        for fname in ("north", "east", "west"):
            x0, y0, w, h = f[fname]
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    _tint(px, x, y, pal["light"], 0.22)
        x0, y0, w, h = f["north"]
        d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=pal["trim"])
        d.line([(x0, y0), (x0, y0 + h - 1)], fill=mix(pal["base"], pal["dark"], 0.6))
        if cube == "breast":
            # Gunmetal clasps down the closure. No gold anywhere on this set.
            for cy in range(y0 + 1, y0 + h - 1, 3):
                d.point((x0 + w - 2, cy), fill=pal["accent"])

    if cube.startswith("epaulette") or cube.startswith("shoulder"):
        _edge_light(d, px, f, pal, 0.5, sides=("north", "south", "top"))
        x0, y0, w, h = f["top"]
        if w > 0 and h > 0:
            d.line([(x0, y0), (x0 + w - 1, y0)], fill=pal["trim"])

    if cube in ("belt", "band"):
        x0, y0, w, h = f["north"]
        bx = x0 + w // 2 - 1
        d.rectangle([bx, y0 + max(0, h // 2 - 1), bx + 1, y0 + max(0, h - 1)],
                    fill=pal["accent"])

    if cube == "placket":
        x0, y0, w, h = f["north"]
        for by in range(y0 + 1, y0 + h - 1, 3):
            d.point((x0 + w // 2, by), fill=pal["accent"])

    if cube.startswith("cuff") or cube.startswith("bell"):
        # A cuff reads as a band only if it is edged: lit where it turns over, dark
        # under. The bell is six pixels wide, so it gets half the rim a two-pixel cuff
        # can carry — at full strength it drew a white hoop around the sleeve.
        _edge_light(d, px, f, pal, 0.25 if cube.startswith("bell") else 0.45)
        for fname in ("north", "south", "east", "west"):
            x0, y0, w, h = f[fname]
            if h > 1:
                for x in range(x0, x0 + w):
                    _tint(px, x, y0 + h - 1, pal["dark"], 0.6)

    if cube.startswith("hood"):
        # The inside of a hood is a hole. The brow overhang gets pushed right down so the
        # face sits in shadow, and the crown catches the only light on the piece.
        if cube == "hood_brow":
            for fname in ("south", "bottom"):
                x0, y0, w, h = f[fname]
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        _tint(px, x, y, pal["dark"], 0.75)
            _edge_light(d, px, f, pal, 0.4, sides=("north",))
        if cube == "hood_top":
            _edge_light(d, px, f, pal, 0.45, sides=("north", "east", "west"))
        if cube in ("hood_down_lip", "hood_down_roll"):
            # The rim of a hood worn down is the fold you actually see from behind.
            _edge_light(d, px, f, pal, 0.5)
            for fname in ("north", "south", "east", "west"):
                x0, y0, w, h = f[fname]
                if h > 1:
                    for x in range(x0, x0 + w):
                        _tint(px, x, y0 + h - 1, pal["dark"], 0.55)

    if cube.startswith("brim"):
        # Rim highlight so the brim edge separates from the crown above it, and a dark
        # underside so the wave is legible from below.
        _edge_light(d, px, f, pal, 0.5)
        x0, y0, w, h = f["bottom"]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                _tint(px, x, y, pal["dark"], 0.5)

    if cube.startswith("taper") or cube in ("crown", "tip"):
        # Felt creases: one soft vertical on the front and back of every step, so the cone
        # is not five flat rings.
        for fname in ("north", "south"):
            x0, y0, w, h = f[fname]
            if w >= 4:
                for y in range(y0, y0 + h):
                    _tint(px, x0 + w // 3, y, pal["dark"], 0.30)
                    _tint(px, x0 + w // 3 + 1, y, pal["light"], 0.14)

    if name == "death_eater_mask":
        if cube == "face":
            _mask_face(d, px, f["north"], pal, variant or 0)
        if cube == "nose":
            _edge_light(d, px, f, pal, 0.6, sides=("north", "east", "west"))
        if cube.startswith("cheek"):
            # Shade, not shine. A lit rim on a 2x3 plate turns the cheekbone into a
            # white tile; the plane reads from its shadowed lower edge instead.
            x0, y0, w, h = f["north"]
            for x in range(x0, x0 + w):
                _tint(px, x, y0 + h - 1, pal["dark"], 0.45)


def _mask_face(d, px, rect, pal, variant):
    """One carved bone face: sockets, a shadowed nose line, then the casting's own marks.

    The plate's north face is 9x7, which is nine pixels to say "mask" in. Everything here is
    laid out from that: 2px sockets on rows 2-3, a lit brow ridge above them and a cheekbone
    shadow below, because a socket on its own reads as two dots.
    """
    x0, y0, w, h = rect
    eye_y = y0 + 2
    socket = pal["accent"]
    lit = pal["light"]
    soft = mix(pal["base"], pal["dark"], 0.55)
    shadow = mix(pal["base"], pal["dark"], 0.85)
    left, right = x0 + 1, x0 + w - 3
    mx = x0 + w // 2

    for ex in (left, right):
        d.rectangle([ex, eye_y, ex + 1, eye_y + 1], fill=socket)
        d.line([(ex, eye_y - 1), (ex + 1, eye_y - 1)], fill=mix(pal["base"], lit, 0.55))
        d.line([(ex, eye_y + 2), (ex + 1, eye_y + 2)], fill=soft)

    # Nose line and a sealed mouth: the two marks that turn a plate into a face.
    d.line([(mx, eye_y + 1), (mx, eye_y + 2)], fill=soft)
    d.line([(mx - 1, y0 + h - 2), (mx + 1, y0 + h - 2)], fill=soft)

    if variant == 0:
        return

    if variant == 1:  # Veil: tears drawn down from both sockets
        for ex in (left, right):
            for y in range(eye_y + 2, y0 + h - 1):
                _tint(px, ex + 1, y, pal["dark"], 0.55)
        for y in range(eye_y + 3, y0 + h - 1):
            d.point((mx, y), fill=shadow)

    elif variant == 2:  # Serpent: scales chased across the brow and along the jaw
        for y in list(range(y0, eye_y - 1)) + [y0 + h - 1]:
            for x in range(x0 + 1, x0 + w - 1):
                if (x + y) % 2 == 0:
                    _tint(px, x, y, pal["trim"], 0.35)
        # A coil down the centre. Scaling the whole plate greyed the casting out and
        # cost it the one thing a 9x7 face has: contrast between socket and bone.
        for i, y in enumerate(range(eye_y + 1, y0 + h - 1)):
            _tint(px, mx + (i % 2), y, pal["trim"], 0.45)
        d.line([(mx, y0), (mx, eye_y - 2)], fill=shadow)

    elif variant == 3:  # Howl: sockets torn open, mouth agape
        for ex in (left, right):
            d.rectangle([ex, eye_y - 1, ex + 1, eye_y + 2], fill=socket)
        d.rectangle([mx - 1, y0 + h - 3, mx + 1, y0 + h - 2], fill=socket)
        d.point((mx, y0 + h - 1), fill=mix(socket, pal["base"], 0.45))

    elif variant == 4:  # Ridge: horned crest along the brow
        for i in range(3):
            d.point((x0 + 1 + i, y0), fill=lit)
            d.point((x0 + w - 2 - i, y0), fill=lit)
        d.line([(x0 + 1, y0 + 1), (x0 + w - 2, y0 + 1)], fill=mix(pal["base"], lit, 0.45))
        d.point((x0 + 1, eye_y + 3), fill=lit)
        d.point((x0 + w - 2, eye_y + 3), fill=lit)

    elif variant == 5:  # Fracture: one crack running through the left socket
        for x, y in ((x0 + 2, y0), (x0 + 2, y0 + 1), (left, eye_y - 1),
                     (left + 1, eye_y + 2), (left + 1, eye_y + 3),
                     (x0 + 3, y0 + h - 2), (x0 + w - 3, eye_y + 2),
                     (x0 + w - 3, eye_y + 3), (x0 + w - 4, y0 + h - 2)):
            d.point((x, y), fill=shadow)
        d.point((x0 + 2, y0 + 2), fill=mix(pal["base"], lit, 0.5))


# --------------------------------------------------------------------------- output


def texture_paths(name):
    """Every PNG this set owns. The mask ships six castings off one model."""
    if name == "death_eater_mask":
        return [(f"{TEX_DIR}/death_eater_mask.png", 0)] + \
               [(f"{TEX_DIR}/death_eater_mask_{v}.png", v) for v in range(1, 6)]
    return [(f"{TEX_DIR}/{name}.png", None)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="", help="comma-separated set names")
    args = ap.parse_args()

    wanted = [s for s in RIGS if not args.only or s in args.only.split(",")]
    written = []

    for name in wanted:
        packer, tex = pack_for(name)
        geo = build_geo(name, packer, tex)
        used = packer.height_used()

        blocked = [p for p, _ in texture_paths(name)
                   if not args.force and not is_regenerable(p, MARKER)]
        if blocked:
            print(f"{name}: {blocked[0]} is hand-authored; not overwriting. Use --force.")
            continue

        os.makedirs(GEO_DIR, exist_ok=True)
        os.makedirs(ANIM_DIR, exist_ok=True)
        with open(f"{GEO_DIR}/{name}.geo.json", "w", encoding="utf-8") as f:
            json.dump(geo, f, indent="\t")
            f.write("\n")
        with open(f"{ANIM_DIR}/{name}.animation.json", "w", encoding="utf-8") as f:
            json.dump(build_anim(name), f, indent="\t")
            f.write("\n")

        for path, variant in texture_paths(name):
            save(paint_set(name, packer, tex, variant), path, MARKER)

        cubes = sum(len(b["cubes"]) for b in geo["minecraft:geometry"][0]["bones"])
        bones = len(geo["minecraft:geometry"][0]["bones"])
        skins = len(texture_paths(name))
        written.append((name, bones, cubes, used, tex, skins))

    print(f"{'set':<20} {'bones':>5} {'cubes':>6} {'sheet':>10} {'skins':>6}")
    for name, bones, cubes, used, tex, skins in written:
        print(f"{name:<20} {bones:>5} {cubes:>6} {used:>4}/{tex:<4}px {skins:>6}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
