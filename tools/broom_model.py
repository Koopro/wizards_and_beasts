#!/usr/bin/env python3
"""The master broom: one rig, six swappable slots, one packed sheet, ten clips.

`broom.geo.json` shipped as a **two-bone, three-cube stub** — a 40-long stick with two
boxes on the end, 64x32, every cube at a hand-picked UV. Seven brooms in the roster and an
eighth generic one all drew that same stick, so Cleansweep Seven and the Firebolt were
literally the same object in different tooltips.

This replaces it with the master-model pattern the wand already uses: every part of the
broom exists in the rig at once, grouped under a container bone per slot, and the renderer
shows exactly one variant per slot from the definition's `model_slots` map. A datapack
assembles a new broom out of parts that already ship — no new art, no new model.

  shaft  -> straight, tapered, streamlined, ribbed, lacquered
  tail_cap -> plain, brass_cap, finial, banded
  binding  -> cord, twine, brass_band, wire
  bristles -> birch, blunt, swept, streamlined, racing
  footrest -> brass, wood, forged        (absent on Cleansweep-tier)
  accent   -> nameplate, lettering, registration   (hero brooms only)

ORIENTATION. Grip end at -Z, bristles trailing at +Z. This is deliberately *not* what the
brief's §2.2 asked for ("shaft along +Z, bristles at -Z"): a Minecraft entity model faces
-Z, so bristles at -Z would fly bristles-first. The stub rig already put its bundle at +Z
and that is what players see today, so the brief's axis would also have silently flipped
every broom in the world. Reported rather than applied.

TEXTURE REGIONS. `shaft_*` and `tail_cap_*` are painted in **greyscale** and tinted at
render time from the definition's `wood_tint` — that is what makes one shaft mesh serve an
ash Nimbus and a mahogany Firebolt. Everything else (`binding_*`, `bristles_*`,
`footrest_*`, `accent_*`) is painted in **final colour and never tinted**: the accent slot
is the baked detail layer carrying lettering and registration marks, so it must not shift
when the wood does. Keep that split when adding variants — a coloured shaft would be
multiplied by the tint twice over.

"32px" is texel density, not canvas size: one shaft width reads as roughly a wand's, and
the sheet is whatever power of two the packed islands need.

Run from the repo root:  python tools/broom_model.py [--force]
"""

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, posterise, save  # noqa: E402
from boxuv import BOTTOM, EAST, NORTH, SOUTH, TOP, WEST, Packer, faces, mottle  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/broom.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/broom.animation.json"
SKIN = f"{ASSETS}/textures/entity/broom.png"
SKIN_DIR = f"{ASSETS}/textures/entity/broom"
GEO_DIR = f"{ASSETS}/geckolib/models/entity"
DEFS = "src/main/resources/data/wizards_and_beasts/broom_definitions"
MARKER = marker("broom_model.py")

# ── palette ──────────────────────────────────────────────────────────────────
# Greyscale ramp for the tintable slots. Mid sits near 0.62 luminance so a tint darkens
# more than it brightens — wood_tint values are wood colours, and multiplying a near-white
# shaft by one washes the grain out entirely.
GREY_DK = hx("#6E6E6E")
GREY_MID = hx("#9E9E9E")
GREY_LT = hx("#C4C4C4")
GREY_GRAIN = hx("#828282")

HEMP = hx("#8A7247")
HEMP_DK = hx("#63512F")
BRASS = hx("#A8813A")
BRASS_LT = hx("#D0A759")
BRASS_DK = hx("#6F5322")
IRON = hx("#6B6F76")
IRON_DK = hx("#484C52")
TWIG = hx("#9A7B4A")
TWIG_DK = hx("#6B5330")
TWIG_LT = hx("#BC9A62")
INK = hx("#2A2118")
BAND_LT = hx("#E8E4D8")      # the metal band: brightest thing on the model
LEATHER = hx("#6B4A2C")
LEATHER_DK = hx("#422C18")

# The band must never take wood_tint — it is the brightest thing on the broom.
TINTABLE = ("shaft_", "tail_cap_")
BAND_BONES = ("binding_",)


# ── per-broom colour schemes ─────────────────────────────────────────────────
#
# One rig, one UV layout, one packing pass — and then the sheet is painted once per broom.
# Every sheet has islands in exactly the same places, so a broom picks its identity by
# swapping the PNG and nothing about the geometry or the UVs moves.
#
# This is what actually makes the seven tell apart in flight. The slot system already gave
# them different *silhouettes*, but all seven drew the one shared sheet, so at any distance
# where the outline blurs they were the same object. Wood colour survives that distance;
# a five-degree difference in shaft sweep does not.
#
# A scheme entry is (base, speckle_palette). Keys:
#   shaft      long faces of the handle (the ones a rider sees)
#   shaft_end  the two end-grain faces, normally a shade lighter
#   grip_wrap  optional — paints `*_grip` cubes as bound leather instead of bare wood
#   band       binding_* rings and wraps
#   twig       bristle slices
#   twig_tip   optional — the last two slices, for tips that differ from the mass
#   strap      footstrap_*
#   accent     accent_* and tail_cap_*
#
# DEFAULT is the odd one out and stays greyscale on purpose: it is the sheet a datapack
# broom falls back to when it ships no art of its own, and its handle is meant to be
# recoloured by `wood_tint` at render time. The seven named schemes are painted in final
# colour and must NOT also carry a wood_tint, or the colour is multiplied in twice.

OAK = hx("#7A5834")
OAK_DK = hx("#543C22")
OAK_LT = hx("#9A7548")
BIRCH = hx("#C3A87A")
BIRCH_DK = hx("#9C8154")
BIRCH_LT = hx("#DCC79C")
EBONY = hx("#2A2320")
EBONY_DK = hx("#171210")
EBONY_LT = hx("#453833")
WALNUT = hx("#4C3623")
WALNUT_DK = hx("#31220F")
WALNUT_LT = hx("#6B4E32")
AGED_OAK = hx("#5A4226")
AGED_OAK_DK = hx("#3A2915")
AGED_OAK_LT = hx("#7C5F3B")
SILVER = hx("#D2D7DC")
SILVER_DK = hx("#9AA1A8")
STRAW = hx("#C6A96A")
STRAW_DK = hx("#9B8148")
GOLD_TWIG = hx("#B08442")
GOLD_TWIG_DK = hx("#7E5C2A")
DARK_TWIG = hx("#5C4527")
DARK_TWIG_DK = hx("#3D2D18")
EMBER = hx("#C0442A")
EMBER_LT = hx("#E8763A")
RED_CORD = hx("#A32620")
RED_CORD_DK = hx("#6E1712")
RUNE_GOLD = hx("#C9A227")

DEFAULT_SCHEME = {
    "shaft": (GREY_MID, (GREY_DK, GREY_GRAIN, GREY_MID)),
    "shaft_end": (GREY_LT, (GREY_MID, GREY_GRAIN)),
    "band": (BAND_LT, (BRASS_LT, BAND_LT)),
    "twig": (TWIG, (TWIG_DK, TWIG, TWIG_LT)),
    "strap": (LEATHER, (LEATHER_DK, BRASS_DK)),
    "accent": (BRASS, (BRASS_DK, BRASS_LT)),
}

SCHEMES = {
    # Warm scuffed oak, dull uneven twigs, grey iron peg. Deliberately the muddiest sheet
    # in the set — "school issue" has to read as the cheap one next to the others.
    "cleansweep_seven": {
        "shaft": (OAK, (OAK_DK, OAK_LT, GREY_GRAIN)),
        "shaft_end": (OAK_LT, (OAK, OAK_DK)),
        "band": (HEMP, (HEMP_DK, TWIG_DK)),
        "twig": (TWIG_DK, (DARK_TWIG_DK, TWIG_DK, TWIG)),
        "strap": (IRON, (IRON_DK, GREY_GRAIN)),
        "accent": (IRON, (IRON_DK, GREY_MID)),
    },
    # Light birch and clean straw: the bright, tidy, unremarkable one.
    "comet_260": {
        "shaft": (BIRCH, (BIRCH_DK, BIRCH_LT)),
        "shaft_end": (BIRCH_LT, (BIRCH, BIRCH_DK)),
        "band": (BRASS, (BRASS_DK, BRASS_LT)),
        "twig": (STRAW, (STRAW_DK, STRAW, TWIG_LT)),
        "strap": (LEATHER, (LEATHER_DK, BRASS_DK)),
        "accent": (BRASS_LT, (BRASS, BRASS_DK)),
    },
    # Polished dark walnut, golden-brown tail, silver collar.
    "nimbus_2000": {
        "shaft": (WALNUT, (WALNUT_DK, WALNUT_LT)),
        "shaft_end": (WALNUT_LT, (WALNUT, WALNUT_DK)),
        "band": (SILVER, (SILVER_DK, BAND_LT)),
        "twig": (GOLD_TWIG, (GOLD_TWIG_DK, GOLD_TWIG, TWIG_LT)),
        "strap": (LEATHER, (LEATHER_DK, WALNUT_DK)),
        "accent": (SILVER, (SILVER_DK, BAND_LT)),
    },
    # The 2000 one generation on: darker stain, brighter chrome, same tail colour so the
    # family reads together and the differences are shape and finish.
    "nimbus_2001": {
        "shaft": (WALNUT_DK, (EBONY, WALNUT)),
        "shaft_end": (WALNUT, (WALNUT_DK, EBONY)),
        "band": (BAND_LT, (SILVER, SILVER_DK)),
        "twig": (GOLD_TWIG, (GOLD_TWIG_DK, GOLD_TWIG, TWIG_LT)),
        "strap": (LEATHER_DK, (EBONY, LEATHER)),
        "accent": (BAND_LT, (SILVER, SILVER_DK)),
    },
    # Ebony and red cord. The darkest handle in the set against the only red on any broom.
    "firebolt": {
        "shaft": (EBONY, (EBONY_DK, EBONY_LT)),
        "shaft_end": (EBONY_LT, (EBONY, EBONY_DK)),
        "band": (RED_CORD, (RED_CORD_DK, EMBER)),
        "twig": (DARK_TWIG, (DARK_TWIG_DK, DARK_TWIG, GOLD_TWIG_DK)),
        "strap": (RED_CORD_DK, (EBONY_DK, RED_CORD)),
        "accent": (SILVER_DK, (EBONY, SILVER)),
    },
    # Firebolt with the heat turned up: gold runes on the band, embers at the tail tips.
    "firebolt_supreme": {
        "shaft": (EBONY, (EBONY_DK, RUNE_GOLD, EBONY_LT)),
        "shaft_end": (EBONY_LT, (EBONY, RUNE_GOLD)),
        "band": (RED_CORD, (RED_CORD_DK, EMBER_LT)),
        "twig": (DARK_TWIG, (DARK_TWIG_DK, DARK_TWIG, GOLD_TWIG_DK)),
        "twig_tip": (EMBER, (EMBER_LT, RED_CORD, EMBER)),
        "strap": (RED_CORD_DK, (EBONY_DK, EMBER)),
        "accent": (RUNE_GOLD, (BRASS_DK, EMBER_LT)),
    },
    # Antique: aged oak with heavy grain, leather at the grip, iron everywhere else, and
    # the darkest bristle mass in the set.
    "oakshaft_79": {
        "shaft": (AGED_OAK, (AGED_OAK_DK, AGED_OAK_LT, WALNUT_DK)),
        "shaft_end": (AGED_OAK_LT, (AGED_OAK, AGED_OAK_DK)),
        "grip_wrap": (LEATHER, (LEATHER_DK, AGED_OAK_DK)),
        "band": (IRON, (IRON_DK, GREY_GRAIN)),
        "twig": (DARK_TWIG_DK, (EBONY, DARK_TWIG_DK, DARK_TWIG)),
        "strap": (LEATHER, (LEATHER_DK, IRON_DK)),
        "accent": (IRON, (IRON_DK, GREY_MID)),
    },
}


# ── rig ──────────────────────────────────────────────────────────────────────
# name -> (parent, pivot, rotation, [(origin, size), ...])
#
# Units are 1/16 block. The broom runs z=-22 (handle tip) to z=+26 (bristle tips); the
# rider sits at the origin, which is why broom_root pivots there and not at a cube corner.

MOUNT = (0, 4, 0)


BAND_Z = 14        # where the bundle is lashed on: shaft ends here, bristles start here
SHAFT_TIP = -22    # free end of the handle

# ── shaft ────────────────────────────────────────────────────────────────────
#
# The sweep comes from BONE ROTATION, not from stepping cube positions. Rev 1 built the
# curve by nudging each successive cube up in Y, which renders as a visible staircase — a
# jagged ramp rather than a swept handle.
#
# Three bones chained root->mid->grip. The root sits AT THE BINDING and does not rotate, so
# the join with the bundle never moves; each child rotates a little more, and because a
# child inherits its parent's transform the rotations accumulate into a smooth curve. Cubes
# inside every bone stay axis-aligned.
#
# Anchoring at the binding is a deviation from the brief's grip->mid->neck ordering, which
# would swing the binding end of the shaft away from the bristles it is supposed to be
# lashed to. Reported rather than applied.

SHAFT_SEGMENTS = (
    # (suffix, z_from, z_to, pivot_z)
    ("",      2,  BAND_Z,  BAND_Z),   # neck: at the binding, anchored, never rotates
    ("_mid", -8,  2,       2),
    ("_grip", SHAFT_TIP, -8, -8),
)


def _shaft_chain(variant, sweep_mid, sweep_grip, butt, tip):
    """Three chained bones for one shaft variant.

    `butt` is the thickness at the binding, `tip` at the grip — thickest where the bundle is
    bound, thinnest in the hand, which is the taper the props actually have. `sweep_*` are
    degrees of X rotation; small values, this is a prop sweep and not a hook.
    """
    bones = []
    widths = (butt, (butt + tip) // 2, tip)
    sweeps = (None, (sweep_mid, 0, 0), (sweep_grip, 0, 0))
    parent = "shaft"
    for i, (suffix, z0, z1, pz) in enumerate(SHAFT_SEGMENTS):
        name = "shaft_" + variant + suffix
        w = max(1, widths[i])
        bones.append((name, parent, (0, 4, pz), sweeps[i],
                      [((-w / 2.0, 4 - w / 2.0, z0), (w, w, z1 - z0))]))
        parent = name
    return bones


# ── bristles ─────────────────────────────────────────────────────────────────
#
# Stacked solid cubes, each smaller than the last, ending in a 1-unit tip. Rev 1's crossed
# alpha planes are gone by ruling: film props are dense tapered volumes, and cubes read
# better at this scale with no alpha-sorting risk at all.
#
# Profiles are authored per variant rather than generated from a curve, because the whole
# point of the collapse is that the four must be told apart in black silhouette. A shared
# parametric profile is what made Rev 1's five bundles the same shape with different numbers.
#
#   (width, height, depth) per slice, front (at the binding) to back (the tip)

BRISTLE_PROFILES = {
    # Training-broom / Weasley: uneven flare, blunt ragged end, never comes to a point.
    "ragged":      [(6, 6, 5), (9, 8, 6), (7, 9, 6), (9, 6, 6), (6, 7, 5)],
    # Nimbus 2000: round in section, swells early, converges to a 1-unit tip.
    "teardrop":    [(5, 5, 5), (9, 9, 6), (7, 7, 6), (4, 4, 6), (1, 1, 5)],
    # Nimbus 2001: much wider than tall — a flattened blade. The width is the whole tell, and
    # it is only visible from above, which is why the silhouette sheet renders two views.
    "blade":       [(7, 3, 5), (11, 4, 6), (9, 3, 6), (5, 2, 6), (1, 1, 5)],
    # Firebolt: narrow, long and clean, barely swelling at all.
    "streamlined": [(4, 4, 6), (5, 5, 7), (4, 4, 7), (3, 3, 7), (1, 1, 6)],
    # Nimbus 2000: tight and aerodynamic — reads as a swept tail rather than a brush. Sits
    # between `teardrop` (which swells to 9 and is unmistakably a brush) and `streamlined`
    # (which never swells at all), because the 2000 has to be told from both of them.
    "swept":       [(5, 5, 5), (6, 6, 6), (5, 5, 6), (3, 3, 6), (1, 1, 5)],
    # Oakshaft 79: a huge unruly mass, wider than the binding at every slice and blunt at
    # the end. The only bundle here that never narrows below the shaft it is lashed to.
    "heavy":       [(9, 9, 5), (12, 11, 7), (11, 12, 7), (12, 9, 7), (10, 10, 6)],
}


def _bristles(profile):
    """A tapered bundle running back from the binding toward +Z."""
    cubes = []
    z = BAND_Z + 1
    for w, h, d in BRISTLE_PROFILES[profile]:
        cubes.append(((-w / 2.0, 4 - h / 2.0, z), (w, h, d)))
        z += d
    return cubes


# ── binding ──────────────────────────────────────────────────────────────────
#
# The band is the highest-contrast element on a film broom and Rev 1 had none: its four
# binding variants were all dull cord at shaft width, so nothing read as a band and the four
# were mutually indistinguishable. Every band here is PROUD of the shaft so it breaks the
# silhouette, and painted bright (see BAND_BONES in paint()).

def _bands(count, width, spacing, thickness=1):
    """`count` rings of `width` (wider than the shaft) centred on the binding."""
    cubes = []
    span = (count - 1) * spacing
    z = BAND_Z - 2 - span / 2.0
    for _ in range(count):
        cubes.append(((-width / 2.0, 4 - width / 2.0, z), (width, width, thickness)))
        z += spacing
    return cubes


def _footstrap():
    """A short leather strap hanging under the binding, ending in a toggle bead.

    Reference shows this on nearly every prop; Rev 1 modelled it as a rigid tier-gated
    stirrup, which is a different object entirely.
    """
    return [((-1.0, 0.0, BAND_Z - 3), (2, 3, 1)),    # strap
            ((-1.5, -2.0, BAND_Z - 4), (3, 2, 3))]   # bead


def snap(bones):
    """Round every cube to whole units, keeping it centred where it was.

    Minecraft box-UV maps one model unit to one texel, and `boxuv.faces()` lays out face
    rects on whole texels. A cube sized 1.15 x 1.15 x 3.41 therefore samples a region that
    does not line up with the rectangle this script painted for it — the texture slides and
    stretches across the geometry. Every cube here was fractional, so nothing was aligned.

    Snapping is not only a fix, it is the house style: integer cubes are what makes a model
    read as Minecraft rather than as a low-poly mesh. Sizes clamp to a minimum of 1 because
    a zero-unit cube has no faces at all.
    """
    out = []
    for name, parent, pivot, rot, cubes in bones:
        if not cubes:
            out.append((name, parent, pivot, rot, cubes))
            continue
        snapped = []
        for cube in cubes:
            origin, size = cube[0], cube[1]
            new_size = tuple(max(1, int(round(v))) for v in size)
            # keep the cube where it was rather than where its corner was
            new_origin = tuple((o + s / 2.0) - n / 2.0
                               for o, s, n in zip(origin, size, new_size))
            snapped.append((new_origin, new_size) + tuple(cube[2:]))
        out.append((name, parent, pivot, rot, snapped))
    return out


def build_bones():
    B = [
        # Containers. `root` and `broom_body` keep their names: the shipped clips animate root,
        # and BroomRenderer reaches for broom_body by name to apply tilt. The six slot
        # containers are what the clips rotate, so one clip covers every variant of a slot.
        ("root", None, (0, 0, 0), None, None),
        ("broom_root", "root", MOUNT, None, None),
        ("broom_body", "broom_root", MOUNT, None, None),
        ("shaft", "broom_body", (0, 4, BAND_Z), None, None),
        ("tail_cap", "broom_body", (0, 4, SHAFT_TIP), None, None),
        ("binding", "broom_body", (0, 4, BAND_Z), None, None),
        ("bristles", "broom_body", (0, 4, BAND_Z), None, None),
        ("footstrap", "broom_body", (0, 4, BAND_Z), None, None),
        ("accent", "broom_body", (0, 4, -10), None, None),
    ]

    # ── SHAFT x5 — thickness, sweep angle and taper severity ───────────────
    #
    # Thickness is the primary tell and sweep the secondary one. Rev 2 shipped `plain` and
    # `swept` with byte-identical cubes differing only by 5 and 9 degrees of rotation, which
    # is not a difference a player can see on a 2-metre prop at flight distance — so the
    # school broom, the Nimbus and the Oakshaft all read as the same stick. Every variant
    # here differs in the width of its cubes, not only in how they are rotated.
    B += _shaft_chain("plain",     0,   0, 3, 2)   # straight, gentle taper — Comet
    B += _shaft_chain("oak",       3,   5, 5, 4)   # chunky, drooping under its own weight
    B += _shaft_chain("swept",    -5,  -9, 3, 2)   # pronounced upward sweep at the grip
    B += _shaft_chain("racing",   -2,  -5, 3, 1)   # slim, severe taper to a needle grip
    B += _shaft_chain("heavy_oak", 1,   2, 7, 5)   # antique log: thickest thing in the rig

    # ── TAIL_CAP x2 — butt-end volume ──────────────────────────────────────
    # The grip end is swept up by the shaft chain, so a cap parented to broom_body cannot
    # follow it. These sit on the axis at the grip's unswept height and read as the ferrule
    # rather than a floating bead — see the punchlist entry.
    B += [
        ("tail_cap_plain", "tail_cap", (0, 4, SHAFT_TIP), None,
         [((-1.0, 3.0, SHAFT_TIP - 1), (2, 2, 2))]),
        ("tail_cap_finial", "tail_cap", (0, 4, SHAFT_TIP), None,
         [((-2.0, 2.0, SHAFT_TIP - 3), (4, 4, 3)),
          ((-1.5, 2.5, SHAFT_TIP), (3, 3, 2))]),
    ]

    # ── BINDING x2 — band width and count ──────────────────────────────────
    B += [
        # One fat wrap: a single wide collar, deep enough to read as bound cord.
        ("binding_cord", "binding", (0, 4, BAND_Z), None, _bands(1, 7, 0, thickness=5)),
        # Three narrow rings spread wide apart, each prouder than the cord collar.
        ("binding_brass_band", "binding", (0, 4, BAND_Z), None, _bands(3, 9, 4, thickness=1)),
        # One narrow proud ring — the collar the racing brooms carry where the school
        # brooms carry a wrap of twine.
        ("binding_collar_ring", "binding", (0, 4, BAND_Z), None, _bands(1, 9, 0, thickness=2)),
        # Two fat rings, wider than any bundle joint in the rig. Antique ironwork.
        ("binding_iron_rings", "binding", (0, 4, BAND_Z), None, _bands(2, 11, 5, thickness=2)),
    ]

    # ── BRISTLES x4 — outline shape and tip convergence ────────────────────
    for name in ("ragged", "teardrop", "blade", "streamlined", "swept", "heavy"):
        B.append(("bristles_" + name, "bristles", (0, 4, BAND_Z), None, _bristles(name)))

    # ── FOOTSTRAP x2 — strap or peg ────────────────────────────────────────
    B.append(("footstrap_leather", "footstrap", (0, 4, BAND_Z), None, _footstrap()))
    # A rigid stirrup bolted under the shaft. This IS the tier-gated platform the rev-1
    # rig modelled for every broom: wrong as a universal, right as the one school-issue
    # broom whose brief calls for an iron foot peg.
    B.append(("footstrap_iron_peg", "footstrap", (0, 4, BAND_Z), None,
              [((-1.0, 0.0, BAND_Z - 4), (2, 3, 2)),        # bracket down off the shaft
               ((-3.0, -2.0, BAND_Z - 5), (6, 2, 4))]))     # the plate itself, stood on

    # ── ACCENT x2 — presence ───────────────────────────────────────────────
    # `none` is a real, cubeless bone rather than an absent map key, so "no nameplate" is a
    # thing a datapack can select explicitly and the renderer's hide-all-but-one loop needs
    # no special case for an unset slot.
    B += [
        ("accent_none", "accent", (0, 4, -10), None, None),
        # Stands proud of the shaft rather than lying flush on it: flush, it added one
        # unit to a 60-unit outline and was invisible in silhouette at any size.
        ("accent_nameplate", "accent", (0, 4, -10), None,
         [((-2.0, 5.0, -16), (4, 3, 11))]),
        # A single stamped disc rather than a plate — "small maker mark", not lettering.
        ("accent_maker_mark", "accent", (0, 4, -10), None,
         [((-2.0, 5.0, -12), (4, 2, 3))]),
        # A collar around the handle itself, so the runes read from every side rather than
        # only from above the way a flush plate does.
        ("accent_runic_band", "accent", (0, 4, -10), None,
         [((-3.0, 1.0, -13), (6, 6, 2)),
          ((-3.0, 1.0, -8), (6, 6, 2))]),
    ]

    # ── FX anchors: empty, for a later particle pass ────────────────────────
    B += [
        ("fx_tip", "broom_root", (0, 4, SHAFT_TIP), None, None),
        ("fx_tail", "broom_root", (0, 4, 40), None, None),
        ("fx_mount", "broom_root", (0, 5, 0), None, None),
    ]
    return B


def pack(bones):
    """Pack every cube onto the smallest power-of-two sheet that holds it.

    Width and height are chosen independently. A broom is a long thin thing, so its islands
    are long and thin too: forcing a square wasted more than half the sheet on empty rows.
    """
    items = []
    for name, _parent, _pivot, _rot, cubes in bones:
        for i, cube in enumerate(cubes or []):
            items.append((f"{name}#{i}", cube[1]))

    # The packer starts a new shelf when a box will not fit, but places it anyway at x=0 —
    # an island wider than the sheet silently runs off the right edge. A 34-long shaft
    # unwraps to a 70px island, so any width below that is unusable no matter how the rows
    # stack. Filter first, then pick the layout that wastes the least sheet.
    widest = max(2 * int(round(s[2] + 0.4999)) + 2 * int(round(s[0] + 0.4999)) for _, s in items)

    best = None
    for width in (64, 128, 256, 512):
        if width < widest:
            continue
        packer = Packer(width)
        used = packer.place_sorted(items)
        height = 1
        while height < used:
            height *= 2
        if height > 512:
            continue
        if best is None or width * height < best[1] * best[2]:
            best = (packer, width, height)
    if best is None:
        raise SystemExit(f"broom rig does not fit: widest island is {widest}px")
    return best


def build_geo(packer, bones, tex_w, tex_h):
    out = []
    for name, parent, pivot, rot, cubes in bones:
        bone = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            bone["parent"] = parent
        if rot:
            bone["rotation"] = [round(v, 2) for v in rot]
        if cubes:
            emitted = []
            for i, cube in enumerate(cubes):
                origin, size = cube[0], cube[1]
                entry = {"origin": [round(v, 2) for v in origin],
                         "size": [round(v, 2) for v in size],
                         "uv": list(packer.placed[f"{name}#{i}"][0])}
                if len(cube) == 4:  # (origin, size, pivot, rotation)
                    entry["pivot"] = [round(v, 2) for v in cube[2]]
                    entry["rotation"] = list(cube[3])
                emitted.append(entry)
            bone["cubes"] = emitted
        out.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.broom",
                "texture_width": tex_w, "texture_height": tex_h,
                "visible_bounds_width": 4, "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.5, 0],
            },
            "bones": out,
        }],
    }


# ── paint ────────────────────────────────────────────────────────────────────

def _seed(name):
    n = 0
    for ch in name:
        n = (n * 131 + ord(ch)) & 0xFFFFFFFF
    return n


def paint(packer, tex_w, tex_h, scheme=None):
    """Paint one sheet for one colour scheme over the shared UV layout.

    Called once per broom plus once for the fallback. The packing is done before this runs
    and is never re-run, so every sheet this returns is interchangeable with every other —
    swapping a broom's texture cannot move a UV island.
    """
    scheme = scheme or DEFAULT_SCHEME
    img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    for key, (uv, size) in packer.placed.items():
        bone, index = key.split("#", 1)
        index = int(index)
        rects = faces(uv, size)
        seed = _seed(key)

        if bone.startswith(TINTABLE):
            # Grain runs along the length, which for a shaft is the NORTH/SOUTH faces. The
            # grip cubes take `grip_wrap` where a scheme defines one, so a bound handle is
            # a paint decision and not thirteen more cubes in the rig.
            wrap = scheme.get("grip_wrap") if bone.endswith("_grip") else None
            side = wrap or scheme["shaft"]
            end = wrap or scheme["shaft_end"]
            for f in (TOP, BOTTOM, EAST, WEST):
                mottle(d, rects[f], side[0], side[1], 34, seed)
            for f in (NORTH, SOUTH):
                mottle(d, rects[f], end[0], end[1], 26, seed + 1)
        elif bone.startswith(BAND_BONES):
            # The band is the highest-contrast element on a film broom and what makes a
            # stick read as a broom at all. Every scheme paints it away from its own wood.
            base, pal = scheme["band"]
            for r in rects.values():
                mottle(d, r, base, pal, 22, seed)
        elif bone.startswith("bristles_"):
            # Slices 3 and 4 are the tail tips. A scheme with `twig_tip` paints them apart
            # from the mass — that is the Supreme's embers, and it is why the tip colour is
            # keyed off the slice index rather than off cube size.
            tip = scheme.get("twig_tip")
            base, pal = tip if (tip and index >= 3) else scheme["twig"]
            thin = min(size) < 2
            for r in rects.values():
                mottle(d, r, base, pal, 48 if thin else 38, seed)
        elif bone.startswith("footstrap_"):
            base, pal = scheme["strap"]
            for r in rects.values():
                mottle(d, r, base, pal, 34, seed)
        else:  # accent_* — baked detail, never tinted
            base, pal = scheme["accent"]
            for r in rects.values():
                mottle(d, r, base, pal, 22, seed)
            _letter(d, rects[TOP])

    return posterise(img, 24)


def _letter(draw, rect):
    """A row of ink ticks standing in for engraved lettering. Placeholder marks, not text."""
    x0, y0, w, h = rect
    if w < 3 or h < 1:
        return
    y = y0 + h // 2
    for i in range(x0 + 1, x0 + w - 1, 2):
        draw.point((i, y), fill=INK)


# ── animation ────────────────────────────────────────────────────────────────

def kf(*pairs):
    return {str(t): list(v) for t, v in pairs}


def build_anim():
    """Ten clips. Eight are wired by the entity's controllers; brake and summon are not.

    brake needs a brake input on BroomEntity.setInput and matching deceleration in
    BroomMovement; summon needs a trigger system that does not exist. Both are authored
    here anyway so the animation file does not have to be reopened when they land.

    Rotation of `broom_body` is deliberately untouched: BroomRenderer overwrites its rotX
    and rotZ from the tilt data tickets every frame, so anything keyed here would be
    clobbered. Body motion goes on `root`; part motion goes on the slot container bones,
    which means it applies to every variant of that slot for free.
    """
    A = {}

    # The looping clips carry no `root` position bob. They did until 2026-09-17 (e90d869f), when
    # the shipped JSON dropped it from idle, hover, fly_forward and boost and gave idle a bristle
    # sway instead; this reproduces that file. The one-shot clips below still move `root`.
    A["animation.broom.idle"] = {
        "loop": True, "animation_length": 2.0,
        "bones": {"bristles": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (1.5, 0, 0)),
                                              (2.0, (0, 0, 0)))}},
    }

    A["animation.broom.hover"] = {
        "loop": True, "animation_length": 3.0,
        "bones": {
            "bristles": {"rotation": kf((0.0, (0, 0, 0)), (1.5, (2.5, 0, 0)), (3.0, (0, 0, 0)))},
        },
    }

    A["animation.broom.fly_forward"] = {
        "loop": True, "animation_length": 0.8,
        "bones": {
            # Bristles drag: the bundle lags the shaft, so it sits nose-up and flutters.
            "bristles": {"rotation": kf((0.0, (-6, 0, 0)), (0.4, (-8, 0, 0)), (0.8, (-6, 0, 0)))},
            "shaft": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (-1.2, 0, 0)), (0.8, (0, 0, 0)))},
        },
    }

    for side, sign in (("left", 1), ("right", -1)):
        A[f"animation.broom.lean_{side}"] = {
            "loop": True, "animation_length": 0.6,
            "bones": {
                "bristles": {"rotation": kf((0.0, (-6, 0, 0)),
                                            (0.6, (-6, 4 * sign, 7 * sign)))},
                "accent": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (0, 0, 2 * sign)))},
            },
        }

    A["animation.broom.boost"] = {
        "loop": True, "animation_length": 0.4,
        "bones": {
            # Splay, not sway: the bundle opens out and the shaft takes a visible bend.
            "bristles": {"rotation": kf((0.0, (-10, 0, 0)), (0.2, (-13, 0, 0)), (0.4, (-10, 0, 0))),
                         "scale": kf((0.0, (1.15, 1.15, 1.0)), (0.2, (1.3, 1.3, 1.0)),
                                     (0.4, (1.15, 1.15, 1.0)))},
            "shaft": {"rotation": kf((0.0, (-2.5, 0, 0)), (0.2, (-3.5, 0, 0)), (0.4, (-2.5, 0, 0)))},
        },
    }

    # UNWIRED — no brake input exists on BroomEntity.
    A["animation.broom.brake"] = {
        "loop": True, "animation_length": 0.5,
        "bones": {
            "root": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (14, 0, 0)), (0.5, (11, 0, 0)))},
            "bristles": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (22, 0, 0)), (0.5, (18, 0, 0))),
                         "scale": kf((0.0, (1.0, 1.0, 1.0)), (0.25, (1.45, 1.45, 0.9)),
                                     (0.5, (1.35, 1.35, 0.92)))},
        },
    }

    A["animation.broom.mount"] = {
        "loop": False, "animation_length": 0.4,
        "bones": {"root": {"position": kf((0.0, (0, -1.5, 0)), (0.2, (0, 0.4, 0)), (0.4, (0, 0, 0))),
                           "rotation": kf((0.0, (0, 0, -6)), (0.4, (0, 0, 0)))}},
    }

    A["animation.broom.dismount"] = {
        "loop": False, "animation_length": 0.35,
        "bones": {"root": {"position": kf((0.0, (0, 0, 0)), (0.35, (0, -1.5, 0))),
                           "rotation": kf((0.0, (0, 0, 0)), (0.35, (0, 0, 5)))}},
    }

    # UNWIRED — nothing in the mod triggers a summon.
    A["animation.broom.summon"] = {
        "loop": False, "animation_length": 1.2,
        "bones": {
            "root": {"position": kf((0.0, (0, -12, 0)), (0.6, (0, 1.2, 0)), (1.2, (0, 0, 0))),
                     "rotation": kf((0.0, (24, 0, 18)), (0.6, (-6, 0, -4)), (1.2, (0, 0, 0)))},
            "bristles": {"rotation": kf((0.0, (14, 0, 0)), (0.6, (-5, 0, 0)), (1.2, (0, 0, 0)))},
        },
    }

    return {"format_version": "1.8.0", "animations": A}


SLOT_IDS = ("shaft", "tail_cap", "binding", "bristles", "footstrap", "accent")


def flatten(bones, geo, definition):
    """One broom's geometry: the master rig with every unselected variant dropped.

    GENERATED, never hand-edited. A broom naming its own `model` gets a file containing only the
    parts it actually draws, which is what the schema's `model` field is for — but authoring seven
    of those by hand would be seven copies of the same 45 bones, and the renderer addresses slot
    variants by name, so the moment two copies disagree it starts hiding bones that exist in one and
    not the other.

    Emitting them from the master rig removes that risk entirely: there is one place a shaft is
    modelled, and these files are build output like the sheets. `BroomModelParityTest` re-derives the
    expected bone list from the rig and the definition, so a stale file fails the build.

    The slot CONTAINER bones survive even when their slot is unset, because the animations key off
    them — `fly_forward` rotates `bristles`, not `bristles_streamlined`.
    """
    slots = definition.get("model_slots", {})
    keep = set()
    for slot in SLOT_IDS:
        selected = slots.get(slot)
        if selected:
            keep |= descendants_of(bones, slot + "_" + selected.split(":")[-1])

    kept = [b for b in geo["minecraft:geometry"][0]["bones"]
            if not any(b["name"].startswith(slot + "_") for slot in SLOT_IDS)
            or b["name"] in keep]

    out = json.loads(json.dumps(geo))
    out["minecraft:geometry"][0]["bones"] = kept
    return out


def descendants_of(bones, root):
    """A variant plus its chain children — a shaft variant is three bones, not one."""
    parents = {name: parent for name, parent, _pivot, _rot, _cubes in bones}
    keep = {root}
    changed = True
    while changed:
        changed = False
        for name, parent in parents.items():
            if parent in keep and name not in keep:
                keep.add(name)
                changed = True
    return keep


def emit_per_broom_geos(bones, geo):
    """Writes one geometry per broom that names a `model`, and reports what it wrote."""
    written = []
    for name in sorted(os.listdir(DEFS)):
        if not name.endswith(".json"):
            continue
        definition = json.load(open(os.path.join(DEFS, name), encoding="utf-8"))
        model = definition.get("model")
        if not model:
            continue

        asset = model.split(":")[-1]
        flattened = flatten(bones, geo, definition)
        # Each file needs its own identifier or GeckoLib caches one under the other's name.
        flattened["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + asset
        with open(f"{GEO_DIR}/{asset}.geo.json", "w", encoding="utf-8") as fh:
            json.dump(flattened, fh, indent=2)
            fh.write("\n")
        written.append((asset, len(flattened["minecraft:geometry"][0]["bones"])))
    return written


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    if not args.force and not is_regenerable(SKIN, MARKER):
        print(f"skip: {SKIN} is hand-authored or from another generator (use --force)")
        return

    bones = snap(build_bones())
    packer, tex_w, tex_h = pack(bones)

    with open(GEO, "w", encoding="utf-8") as fh:
        json.dump(build_geo(packer, bones, tex_w, tex_h), fh, indent=2)
        fh.write("\n")
    with open(ANIM, "w", encoding="utf-8") as fh:
        json.dump(build_anim(), fh, indent=2)
        fh.write("\n")

    # The fallback sheet, greyscale-handled and meant to be recoloured by `wood_tint`.
    save(paint(packer, tex_w, tex_h), SKIN, MARKER)

    # One final-colour sheet per shipped broom, all sharing the layout just packed.
    os.makedirs(SKIN_DIR, exist_ok=True)
    for broom_id, scheme in SCHEMES.items():
        save(paint(packer, tex_w, tex_h, scheme), f"{SKIN_DIR}/{broom_id}.png", MARKER)

    per_broom = emit_per_broom_geos(bones, build_geo(packer, bones, tex_w, tex_h))
    for asset, count in per_broom:
        print(f"  {asset}.geo.json: {count} bones")

    cubes = sum(len(c or []) for *_, c in bones)
    print(f"broom: {len(bones)} bones, {cubes} cubes, {tex_w}x{tex_h} sheet "
          f"({packer.height_used()} rows used), {len(SCHEMES) + 1} skins")


if __name__ == "__main__":
    main()
