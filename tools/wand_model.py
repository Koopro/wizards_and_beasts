#!/usr/bin/env python3
"""The modular wand: geometry, skin and clips, from one description.

Writes `geckolib/models/item/wand.geo.json`, `textures/item/wand.png` and
`geckolib/animations/item/wand.animation.json`. Replaces `wand_variants.py` (which appended to a
frozen rig) and `wand_skin.py` (which painted it): the rig is now built here from nothing, on the
connection contract taken from the V5 modular foundation (2026-09-29).

The contract
------------
Every wand is one handle, one shaft and one tip, picked independently (`WandConfiguration`), so any
handle has to meet any shaft and any shaft any tip. The meeting planes are fixed:

    HANDLE  y  0 -> 11     top face covers a 1.5 x 1.5 section
    SHAFT   y 11 -> 23     starts 1.5 x 1.5 on the axis, ends on the axis no wider than 1.5
    TIP     y 23 -> 27.5   starts 1.0 x 1.0 on the axis
    wand_spell_attachment  (0, 27.5, 0), a child of wand_root and of no variant

so a joint only ever steps inward or runs flush -- never a lip hanging over the part below it, and
never a gap. A variant may do anything it likes between its planes (curl, bulge, twist) and below
the handle's butt or past the tip's end, as long as it returns to the axis at its planes. The spell
attachment does not move with the variant: beams, the clash anchor and anything else that wants
"where the spell leaves the wand" read one bone, whatever the wand looks like.

Blockbench rules (the reason V4 did not import)
-----------------------------------------------
  - Cubes are axis-aligned. No cube carries a rotation; anything that turns (a twist, a curl, a
    hook) is a chain of bones, each rotated about its own pivot.
  - Cube sizes are whole numbers. GeckoLib lays box UV out from `Math.floor(size)` (see
    `beast_preview.uv_rect`), so a 1.44-wide cube samples one texel where a 1.5-wide one samples
    two, and top faces read the island's empty corner. A fractional *width* is an integer size
    plus one uniform negative `inflate` -- which is why a part's width, height and depth must share
    their fraction (`Wand.box` refuses anything else). Parts may overlap where the overlap is
    enclosed by the wider part; that is how a 1.0-wide piece meets a 1.5-wide one.
  - No negative sizes, no cube pivots, no per-face UV: every cube is a plain box-UV cube.

Painting is the old `wand_skin.py` treatment, unchanged: a pale, near-neutral wood the renderer
multiplies by the wand's wood tint, grain streaks on long faces, binding bands on the grip, and
per-variant wood for the shapes named after screen wands.

Run from the repo root:
    python tools/wand_model.py [--force] [--preview DIR]
"""

import argparse
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_skins as bs  # noqa: E402
from artgen_common import hx, is_regenerable, marker, posterise, save  # noqa: E402
from boxuv import Packer  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_PATH = os.path.join(ASSETS, "geckolib", "models", "item", "wand.geo.json")
ANIM_PATH = os.path.join(ASSETS, "geckolib", "animations", "item", "wand.animation.json")
TEX_PATH = os.path.join(ASSETS, "textures", "item", "wand.png")
MARKER = marker("wand_model.py")

TEX_W = TEX_H = 96          # the sheet the wand has always had

HANDLE_TOP = 11.0
SHAFT_TOP = 23.0
TIP_TOP = 27.5
ATTACHMENT = "wand_spell_attachment"

# Bones Java reads by name (WandRenderer, WandItem clips, WandModelContractTest).
SLOTS = ("handle", "shaft", "tip", "core", "ornament")


# --------------------------------------------------------------------------- rig

class Wand:
    """Bones and box-UV cubes, addressed by rendered extents rather than origin + size."""

    def __init__(self):
        self.bones = []
        self.by_name = {}
        self.cubes = []   # (bone, cube dict, material)
        self.bone("wand_root", None, (0, 0, 0))

    def bone(self, name, parent, pivot, rotation=None):
        if name in self.by_name:
            raise ValueError(f"duplicate bone {name}")
        if parent is not None and parent not in self.by_name:
            raise ValueError(f"{name}: unknown parent {parent}")
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation and any(rotation):
            b["rotation"] = [float(v) for v in rotation]
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def box(self, bone, lo, hi, mat="wood"):
        """One cube spanning `lo`..`hi` as rendered.

        Rendered dims share one fraction f; the cube is `ceil`-sized and shrunk back by a uniform
        inflate of -(1 - f) / 2, so GeckoLib's floored box UV and the painter address the same
        whole texels.
        """
        dims = [round(h - l, 4) for l, h in zip(lo, hi)]
        if any(d <= 0 for d in dims):
            raise ValueError(f"{bone}: empty cube {lo}..{hi}")
        fracs = {round(d % 1.0, 4) for d in dims}
        if len(fracs) != 1 or next(iter(fracs)) not in (0.0, 0.25, 0.5, 0.75):
            raise ValueError(f"{bone}: dims {dims} must share one quarter fraction")
        f = fracs.pop()
        inflate = 0.0 if f == 0 else -(1.0 - f) / 2.0
        size = [int(round(d - 2 * inflate)) for d in dims]
        origin = [round(l + inflate, 4) for l in lo]
        cube = {"origin": origin, "size": size}
        if inflate:
            cube["inflate"] = inflate
        self.cubes.append((bone, cube, mat))
        return cube

    def sq(self, bone, y0, y1, w, dx=0.0, dz=0.0, d=None, mat="wood"):
        """A square-section piece centred on (dx, dz) -- the wand's basic unit."""
        d = w if d is None else d
        return self.box(bone, (dx - w / 2, y0, dz - d / 2), (dx + w / 2, y1, dz + d / 2), mat)

    # -- output -----------------------------------------------------------------

    def island_key(self, index):
        """Repeats of one part -- a thread's ribs, a braid's strands, a wire's beads -- are the
        same material at the same size on the same variant, so they share one island. That is
        what keeps 51 variants on the wand's 96 x 96 sheet."""
        bone, cube, mat = self.cubes[index]
        return (variant_of(self, bone), mat, tuple(cube["size"]))

    def pack(self):
        packer = Packer(TEX_W)
        keys = {}
        for i in range(len(self.cubes)):
            keys.setdefault(self.island_key(i), tuple(self.cubes[i][1]["size"]))
        rows = packer.place_sorted(list(keys.items()))
        for i, (_, cube, _) in enumerate(self.cubes):
            cube["uv"] = list(packer.placed[self.island_key(i)][0])
        return rows

    def geo(self):
        bones = []
        for b in self.bones:
            out = dict(b)
            cubes = [c for (bn, c, _) in self.cubes if bn == b["name"]]
            if cubes:
                out["cubes"] = cubes
            bones.append(out)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry.wizards_and_beasts.wand",
                    "texture_width": TEX_W,
                    "texture_height": TEX_H,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 3,
                    "visible_bounds_offset": [0, 1, 0],
                },
                "bones": bones,
            }],
        }


# --------------------------------------------------------------------------- handles
# y 0 -> 11. Every top face covers the shaft's 1.5 x 1.5 base. The butt below y 0 is free.

def handle_classic(w, b):
    # The base wand's grip, exactly as the author drew it: one 2 x 11 x 2 block.
    w.sq(b, 0, 11, 2)


def handle_smooth(w, b):
    w.sq(b, 0, 11, 2)
    w.sq(b, -0.5, 0, 1.5)                     # a chamfered butt


def handle_gnarled(w, b):
    # Chunky knots on alternating faces: at wand scale small pips read as speckle.
    w.sq(b, 0, 11, 2)
    w.box(b, (1, 1.5, -0.75), (1.5, 3, 0.75))
    w.box(b, (-0.75, 3.5, 1), (0.75, 5, 1.5))
    w.box(b, (-1.5, 5.5, -0.75), (-1, 7, 0.75))
    w.box(b, (1, 8, -0.75), (1.5, 9.5, 0.75))


def handle_carved(w, b):
    # A turned pommel and collar framing four raised panels.
    w.sq(b, 0, 11, 2)
    w.sq(b, 0, 1.5, 2.5)
    w.sq(b, 9.5, 10, 2.5)
    for lo, hi in (((0.75, 3, -0.75), (1.25, 7.5, 0.75)), ((-1.25, 3, -0.75), (-0.75, 7.5, 0.75)),
                   ((-0.75, 3, 0.75), (0.75, 7.5, 1.25)), ((-0.75, 3, -1.25), (0.75, 7.5, -0.75))):
        w.box(b, lo, hi)


def handle_twisted(w, b):
    # The twist is the grip itself: five blocks, each a bone turned 18 degrees past the last.
    for i in range(5):
        seg = w.bone(f"{b}_seg{i}", b, (0, 2 * i + 1, 0), (0, 18 * i, 0))
        w.sq(seg, 2 * i, 2 * i + 2, 2)
    w.sq(b, 10, 11, 2)                        # square collar: the shaft seats on a flat face


def handle_braided(w, b):
    # Strands wound round a slim core, one bar per unit, each turned 36 degrees on.
    w.sq(b, 0, 11, 1)
    for i in range(10):
        bar = w.bone(f"{b}_strand{i}", b, (0, i + 0.5, 0), (0, 36 * i, 0))
        w.box(bar, (-1, i, -0.5), (1, i + 1, 0.5))
    w.sq(b, 10, 11, 2)


def handle_ribbed(w, b):
    w.sq(b, 0, 11, 2)
    for i in range(6):
        w.sq(b, 1 + 1.5 * i, 1.5 + 1.5 * i, 2.5)


def handle_bulbous(w, b):
    # Ron's: a heavy knob under a plain grip.
    w.sq(b, 0, 11, 2)
    w.sq(b, 0.5, 3, 2.5)
    w.sq(b, 3.5, 4, 2.5)
    w.sq(b, -0.5, 0.5, 1)


def handle_fluted(w, b):
    w.sq(b, 0, 11, 2)
    for lo, hi in (((0.75, 0.75, -0.25), (1.25, 10.25, 0.25)), ((-1.25, 0.75, -0.25), (-0.75, 10.25, 0.25)),
                   ((-0.25, 0.75, 0.75), (0.25, 10.25, 1.25)), ((-0.25, 0.75, -1.25), (0.25, 10.25, -0.75))):
        w.box(b, lo, hi)


def handle_wrapped(w, b):
    # A bound grip: a thick leather sleeve round the middle of the handle.
    w.sq(b, 0, 11, 2)
    w.sq(b, 2.5, 8, 2.5, mat="leather")


# Faces of the 2 x 2 grip in climbing order, as (dx, dz) outward normals. A spiral that visits them
# in turn reads as a spiral without a single rotated part, and never buries itself in a corner the
# way a pad turned 45 degrees round a square grip does.
FACES = ((1, 0), (0, 1), (-1, 0), (0, -1))


def on_face(w, bone, face, y0, y1, half_width, out=0.5, radius=1.0, mat="wood"):
    """A pad standing `out` proud of face `face` of a square section of half-size `radius`."""
    nx, nz = FACES[face % 4]
    inner, outer = radius - out / 2, radius + out / 2
    if nx:
        x0, x1 = (inner, outer) if nx > 0 else (-outer, -inner)
        w.box(bone, (x0, y0, -half_width), (x1, y1, half_width), mat)
    else:
        z0, z1 = (inner, outer) if nz > 0 else (-outer, -inner)
        w.box(bone, (-half_width, y0, z0), (half_width, y1, z1), mat)


def handle_vine(w, b):
    """Hermione's: a vine climbing the grip, a leaf where it turns."""
    w.sq(b, 0, 11, 2)
    for i in range(6):
        y = 0.5 + 1.5 * i
        on_face(w, b, i, y, y + 1.5, 0.75)
        if i % 2 == 0:
            nx, nz = FACES[i % 4]
            cx, cz = nx * 1.5, nz * 1.5
            w.box(b, (cx - 0.25 + nz * 0.5, y + 1, cz - 0.25 - nx * 0.5),
                  (cx + 0.25 + nz * 0.5, y + 1.5, cz + 0.25 - nx * 0.5), "leaf")


def handle_bone(w, b):
    """Voldemort's: pale and thin, a run of knuckles, a hooked butt."""
    w.sq(b, 0, 5.5, 1.5)
    w.sq(b, 5.5, 11, 1.5)
    for y in (1, 3.5, 6, 8.5):
        w.sq(b, y, y + 1, 2)
    hook = w.bone(f"{b}_hook", b, (0, 0, 0), (0, 0, 35))
    w.sq(hook, -1, 0, 1)
    tip = w.bone(f"{b}_hook_tip", hook, (0, -1, 0), (0, 0, 30))
    w.sq(tip, -2.5, -1, 0.5)


def handle_talon(w, b):
    """A clawed butt: one heavy claw curling down and out, two spurs up the grip."""
    w.sq(b, 0, 11, 2)
    for y, sign in ((3, 1), (6.5, -1)):
        x0 = 0.75 if sign > 0 else -1.25
        w.box(b, (x0, y, -0.75), (x0 + 0.5, y + 1.5, 0.75))
    claw = w.bone(f"{b}_claw", b, (0, 0.5, 0), (0, 0, 25))
    w.sq(claw, -2, 0.5, 1.5)
    point = w.bone(f"{b}_claw_point", claw, (0, -2, 0), (0, 0, 30))
    w.sq(point, -3.5, -2, 0.5)


def handle_flared(w, b):
    """Sirius's: an ornate stepped flare at the butt under a banded grip."""
    w.sq(b, 0, 11, 2)
    w.sq(b, -0.5, 0, 3.5)
    w.sq(b, 0, 1, 3)
    w.sq(b, 1, 1.5, 2.5)
    w.sq(b, 8, 8.5, 2.5)


def handle_briar(w, b):
    """Briar root: a lumpy burl, knotted necks and thorns -- nothing on it quite symmetric."""
    w.box(b, (-1.0, 0, -1.5), (1.5, 2.5, 1.0))            # the burl, off-centre
    w.box(b, (-1.5, 0.5, -0.25), (-1.0, 1.0, 0.25))       # a nub on it
    w.sq(b, 2.5, 5, 1.5)
    w.box(b, (-1.5, 5, -1.0), (1.0, 6.5, 1.5))            # a knot leaning the other way
    w.sq(b, 6.5, 9, 1.5)
    w.sq(b, 9, 11, 2)
    for lo, hi in (((0.75, 3.5, -0.25), (1.25, 4, 0.25)), ((-1.25, 7.5, -0.25), (-0.75, 8, 0.25)),
                   ((-0.25, 4.5, -1.25), (0.25, 5, -0.75)), ((-0.25, 8, 0.75), (0.25, 8.5, 1.25))):
        w.box(b, lo, hi, "thorn")


def handle_bellatrix(w, b):
    """Bellatrix's: one sculpted talon. The grip sits true under the shaft and then bends -- each
    carved segment a bone turned a little further than the one above -- through a swollen knuckle
    and a heavy pommel into a claw that curls back on itself. Ridged like knuckle joints, never a
    plain stick; the curl is continuous, not a hook stuck on a pole."""
    w.sq(b, 9.5, 11, 1.5)                      # the neck the shaft seats on
    w.sq(b, 8.5, 9.5, 2)                       # first carved ridge
    grip = w.bone(f"{b}_grip", b, (0, 8.5, 0), (0, 0, -6))
    w.sq(grip, 5.5, 9, 1.5)
    w.sq(grip, 6, 7, 2)
    swell = w.bone(f"{b}_knuckle", grip, (0, 5.5, 0), (0, 0, -8))
    w.sq(swell, 3, 6, 2)
    w.sq(swell, 4, 4.5, 2.5)
    w.box(swell, (0.75, 4.75, -0.25), (1.25, 5.25, 0.25), "thorn")    # a knuckle spur on the outside
    pommel = w.bone(f"{b}_pommel", swell, (0, 3, 0), (0, 0, -12))
    w.sq(pommel, 0.5, 3, 2.5)
    spur = w.bone(f"{b}_spur", pommel, (0, 0.5, 0), (0, 0, -22))
    w.sq(spur, -1, 0.5, 1.5)
    claw = w.bone(f"{b}_claw", spur, (0, -1, 0), (0, 0, -28))
    w.sq(claw, -2, -1, 1)
    point = w.bone(f"{b}_claw_point", claw, (0, -2, 0), (0, 0, -30))
    w.sq(point, -3.5, -2, 0.5)


HANDLES = {
    "classic": handle_classic, "gnarled": handle_gnarled, "carved": handle_carved,
    "twisted": handle_twisted, "braided": handle_braided, "smooth": handle_smooth,
    "ribbed": handle_ribbed, "bulbous": handle_bulbous, "fluted": handle_fluted,
    "wrapped": handle_wrapped, "vine": handle_vine, "bone": handle_bone, "talon": handle_talon,
    "flared": handle_flared, "briar": handle_briar, "bellatrix": handle_bellatrix,
}


# --------------------------------------------------------------------------- shafts
# y 11 -> 23. Base 1.5 x 1.5 on the axis; top on the axis, 1.0 to 1.5 wide.

def straight_core(w, b, width=1.5):
    w.sq(b, 11, 16.5, width)
    w.sq(b, 16.5, 23, width)


def shaft_straight(w, b):
    # The base wand's stick: 1.5 square, full length, as it always was.
    straight_core(w, b)


def shaft_tapered(w, b):
    w.sq(b, 11, 14.5, 1.5)
    w.sq(b, 14.25, 18.5, 1.25)
    w.sq(b, 18, 23, 1)


def shaft_spiral(w, b):
    # A thread wound up the stick: bars through the core, each a bone turned 30 degrees on.
    straight_core(w, b)
    for i in range(11):
        y = 11.5 + i
        rib = w.bone(f"{b}_rib{i}", b, (0, y + 0.25, 0), (0, 30 * i, 0))
        w.box(rib, (-1.25, y, -0.25), (1.25, y + 0.5, 0.25))


def shaft_knotted(w, b):
    straight_core(w, b)
    w.sq(b, 13, 14, 2)
    w.box(b, (-1.0, 16.5, -1.5), (1.5, 18, 1.0))
    w.sq(b, 20, 21, 2)


def shaft_segmented(w, b):
    # Bamboo: thin joint rings at even intervals.
    straight_core(w, b)
    for y in (14, 17, 20):
        w.sq(b, y, y + 0.5, 2.5)


def shaft_twining(w, b):
    """A creeper twining up the stick, a leaf at every other turn."""
    straight_core(w, b)
    for i in range(8):
        y = 11.5 + 1.25 * i
        on_face(w, b, i, y, y + 1.5, 0.25, radius=1.0, mat="leaf")


def shaft_bowed(w, b):
    # One gentle bow, back on the axis at both planes.
    for i, dx in enumerate((0, 0.25, 0.5, 0.25)):
        w.sq(b, 11 + 2.5 * i, 13.5 + 2.5 * i, 1.5, dx=dx)
    w.sq(b, 20.5, 23, 1.5)


def shaft_reeded(w, b):
    # Parallel reeds standing proud of all four faces, the length of the shaft.
    straight_core(w, b)
    for face in range(4):
        on_face(w, b, face, 11.5, 22, 0.25, radius=1.0)


def shaft_nodular(w, b):
    """The Elder Wand's: evenly spaced nodes along the length."""
    straight_core(w, b)
    for y in (12, 15, 18, 21):
        w.sq(b, y, y + 1.5, 2.5)


def shaft_barked(w, b):
    """Unplaned wood: bark plates on alternating faces at uneven heights."""
    straight_core(w, b)
    for face, y in ((0, 12), (2, 13.5), (1, 15.5), (3, 17), (0, 18.5), (2, 20)):
        on_face(w, b, face, y, y + 2.5, 0.75, radius=0.75)


def shaft_sinuous(w, b):
    """A serpentine S: eight short sections swinging one way and back the other."""
    for i, dx in enumerate((0, 0.25, 0.5, 0.25, -0.25, -0.5, -0.25, 0)):
        w.sq(b, 11 + 1.5 * i, 12.5 + 1.5 * i, 1.5, dx=dx)


def shaft_bellatrix(w, b):
    """Bellatrix's: long, tapering and bowed -- the middle of the talon's arc, so it swings the
    opposite way to the handle's curl and returns to the axis for the tip."""
    w.sq(b, 11, 13.5, 1.5)
    w.sq(b, 13.5, 16, 1.5, dx=-0.25)
    w.sq(b, 15.75, 19, 1.25, dx=-0.5)
    w.sq(b, 19, 21, 1, dx=-0.25)
    w.sq(b, 21, 23, 1)


SHAFTS = {
    "straight": shaft_straight, "spiral": shaft_spiral, "knotted": shaft_knotted,
    "tapered": shaft_tapered, "segmented": shaft_segmented, "twining": shaft_twining,
    "bowed": shaft_bowed, "reeded": shaft_reeded, "nodular": shaft_nodular,
    "barked": shaft_barked, "sinuous": shaft_sinuous, "bellatrix": shaft_bellatrix,
}


# --------------------------------------------------------------------------- tips
# y 23 -> 27.5, starting 1.0 x 1.0 on the axis. The spell leaves at (0, 27.5, 0) whatever is here.

def tip_pointed(w, b):
    # The base wand's end: a short taper to a fine point.
    w.sq(b, 23, 25, 1)
    w.sq(b, 25, 27.5, 0.5)


def tip_orb(w, b):
    w.sq(b, 23, 24, 1)
    w.sq(b, 24, 25.5, 1.5)
    w.sq(b, 25.5, 26, 0.5)


def tip_split(w, b):
    w.sq(b, 23, 24, 1)
    w.sq(b, 24, 27.5, 0.5, dx=-0.5)
    w.sq(b, 24, 27.5, 0.5, dx=0.5)


def tip_crystal(w, b):
    # A stone in a brass setting, not a boulder balanced on the end.
    w.sq(b, 23, 24, 1)
    w.sq(b, 24, 24.5, 1.5, mat="brass")
    w.sq(b, 24.5, 26, 1.5, mat="gem")
    w.sq(b, 26, 27, 1, mat="gem")
    w.sq(b, 27, 27.5, 0.5, mat="gem")


def tip_blunt(w, b):
    w.sq(b, 23, 26, 1)
    w.sq(b, 25, 25.5, 1.5, mat="brass")


def tip_needle(w, b):
    w.sq(b, 23, 24, 1)
    w.sq(b, 24, 27.5, 0.5)


def tip_chisel(w, b):
    w.sq(b, 23, 25, 1)
    w.box(b, (-0.75, 25, -0.25), (0.75, 27.5, 0.25))


def tip_budded(w, b):
    w.sq(b, 23, 26, 1)
    for lo, hi in (((0.5, 24, -0.25), (1, 24.5, 0.25)), ((-1, 25, -0.25), (-0.5, 25.5, 0.25)),
                   ((-0.25, 24.5, 0.5), (0.25, 25, 1))):
        w.box(b, lo, hi, "leaf")
    w.sq(b, 26, 26.5, 0.5, mat="leaf")


def tip_claw(w, b):
    """A short talon point curving to one side."""
    w.sq(b, 23, 24, 1)
    c1 = w.bone(f"{b}_curve", b, (0, 24, 0), (0, 0, 15))
    w.sq(c1, 24, 25, 1)
    w.sq(c1, 25, 25.5, 0.5)
    c2 = w.bone(f"{b}_point", c1, (0, 25.5, 0), (0, 0, 22))
    w.sq(c2, 25.5, 27, 0.5)


def tip_serpent(w, b):
    """Lucius's cane: a serpent's head, snout forward."""
    w.sq(b, 23, 24, 1)
    w.box(b, (-0.75, 24, -1.75), (0.75, 25.5, 0.75))
    w.box(b, (-0.75, 25.5, -1.25), (0.75, 26, 0.25))
    w.box(b, (-1, 25, -1.25), (-0.5, 25.5, -0.75), "eye")
    w.box(b, (0.5, 25, -1.25), (1, 25.5, -0.75), "eye")


def tip_spiralled(w, b):
    """A corkscrew point: a turned blade on each of four bones."""
    for i in range(4):
        seg = w.bone(f"{b}_turn{i}", b, (0, 23.5 + i, 0), (0, 25 * i, 0))
        w.sq(seg, 23 + i, 24 + i, 1)
        w.box(seg, (-0.75, 23.25 + i, -0.25), (0.75, 23.75 + i, 0.25))
    w.sq(b, 27, 27.5, 0.5)


def tip_leaf(w, b):
    """A leaf-bladed point: narrow stem, broad shoulders, drawn to a tip."""
    w.sq(b, 23, 24, 1)
    w.box(b, (-0.75, 24, -0.25), (0.75, 25.5, 0.25), "leaf")
    w.box(b, (-1.25, 24.5, -0.25), (-0.75, 25, 0.25), "leaf")
    w.box(b, (0.75, 24.5, -0.25), (1.25, 25, 0.25), "leaf")
    w.box(b, (-0.25, 25.5, -0.25), (0.25, 27, 0.25), "leaf")


def tip_hook(w, b):
    """A crook: the end curls over through a hundred and fifty degrees."""
    w.sq(b, 23, 24, 1)
    c1 = w.bone(f"{b}_bend1", b, (0, 24, 0), (0, 0, 30))
    w.sq(c1, 24, 25, 1)
    c2 = w.bone(f"{b}_bend2", c1, (0, 25, 0), (0, 0, 35))
    w.sq(c2, 25, 26, 1)
    c3 = w.bone(f"{b}_bend3", c2, (0, 26, 0), (0, 0, 45))
    w.sq(c3, 26, 27.5, 0.5)
    c4 = w.bone(f"{b}_bend4", c3, (0, 27.5, 0), (0, 0, 40))
    w.sq(c4, 27.5, 29, 0.5)


def tip_bellatrix(w, b):
    """Bellatrix's: the talon's point. A carved lip at the joint, then a long fine point curving on
    the handle's side, finishing the arc the shaft started."""
    w.sq(b, 23, 23.5, 1.5)                     # carved lip
    w.sq(b, 23, 24, 1)
    c1 = w.bone(f"{b}_curve", b, (0, 24, 0), (0, 0, -10))
    w.sq(c1, 24, 25.75, 0.75)
    c2 = w.bone(f"{b}_point", c1, (0, 25.75, 0), (0, 0, -14))
    w.sq(c2, 25.75, 27.25, 0.5)


TIPS = {
    "pointed": tip_pointed, "orb": tip_orb, "split": tip_split, "crystal": tip_crystal,
    "blunt": tip_blunt, "needle": tip_needle, "chisel": tip_chisel, "budded": tip_budded,
    "claw": tip_claw, "serpent": tip_serpent, "spiralled": tip_spiralled, "leaf": tip_leaf,
    "hook": tip_hook, "bellatrix": tip_bellatrix,
}


# --------------------------------------------------------------------------- cores
# Inside the grip, on the axis (canon: a core is not seen). Kept to +-0.5 so every handle hides it.

def core_phoenix_feather(w, b):
    w.sq(b, 4, 7.5, 0.5, mat="core")
    w.sq(b, 7, 8, 1, mat="core")


def core_dragon_heartstring(w, b):
    for y in (3, 5, 7):
        w.sq(b, y, y + 0.5, 0.5, mat="core")


def core_unicorn_hair(w, b):
    w.sq(b, 2, 8.5, 0.5, mat="core")


def core_thestral_tail(w, b):
    for y in (4, 5, 6, 7):
        w.sq(b, y, y + 0.5, 0.5, mat="core")


def core_veela_hair(w, b):
    for i, dx in enumerate((-0.25, 0.25, -0.25)):
        w.sq(b, 3 + 1.5 * i, 4.5 + 1.5 * i, 0.5, dx=dx, mat="core")


CORES = {
    "phoenix_feather": core_phoenix_feather, "dragon_heartstring": core_dragon_heartstring,
    "unicorn_hair": core_unicorn_hair, "thestral_tail": core_thestral_tail,
    "veela_hair": core_veela_hair,
}


# --------------------------------------------------------------------------- ornaments
# Set on the standard 2 x 2 grip (y 0 -> 11). On a sculpted handle they sit where that grip would be.

def ornament_runes(w, b):
    for y in (2, 4.5, 7):
        w.box(b, (-1.25, y, -0.75), (-0.75, y + 1.5, 0.75), "brass")


def ornament_gem_inlay(w, b):
    for y in (3, 7):
        w.box(b, (-1.25, y, -0.25), (-0.75, y + 0.5, 0.25), "gem")
        w.box(b, (0.75, y, -0.25), (1.25, y + 0.5, 0.25), "gem")


def ornament_metal_band(w, b):
    w.sq(b, 9.5, 10, 2.5, mat="brass")
    w.sq(b, 1, 1.5, 2.5, mat="brass")


def ornament_engraving(w, b):
    for y in (2.5, 5.5, 8.5):
        w.box(b, (-1.25, y, -0.25), (1.25, y + 0.5, 0.25), "groove")
        w.box(b, (-0.25, y, -1.25), (0.25, y + 0.5, 1.25), "groove")


# The eight points round the grip's perimeter, corners included, in climbing order.
PERIMETER = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))


def beads(w, b, count, y0, step, stride, mat):
    for i in range(count):
        px, pz = PERIMETER[(i * stride) % 8]
        y = y0 + step * i
        w.box(b, (px - 0.25, y, pz - 0.25), (px + 0.25, y + 0.5, pz + 0.25), mat)


def ornament_wire_wrap(w, b):
    beads(w, b, 12, 2, 0.5, 1, "brass")


def ornament_initial_plate(w, b):
    w.box(b, (-1.25, 3.5, -0.75), (-0.75, 6, 0.75), "brass")


def ornament_spiral_inlay(w, b):
    beads(w, b, 9, 1, 1.0, 2, "silver")


def ornament_vine_wrap(w, b):
    for i in range(6):
        on_face(w, b, i, 1 + 1.5 * i, 2.5 + 1.5 * i, 0.75, mat="leaf")


def ornament_serpent_coil(w, b):
    """A serpent wound round the grip, one full face per step, head raised at the top."""
    for i in range(10):
        on_face(w, b, i, 1.5 + 0.75 * i, 2 + 0.75 * i, 1.25, mat="silver")
    w.box(b, (0.75, 9, -0.75), (1.75, 10, 0.25), "silver")


ORNAMENTS = {
    "runes": ornament_runes, "gem_inlay": ornament_gem_inlay, "metal_band": ornament_metal_band,
    "engraving": ornament_engraving, "wire_wrap": ornament_wire_wrap,
    "initial_plate": ornament_initial_plate, "spiral_inlay": ornament_spiral_inlay,
    "vine_wrap": ornament_vine_wrap, "serpent_coil": ornament_serpent_coil,
}

VARIANTS = {"handle": HANDLES, "shaft": SHAFTS, "tip": TIPS, "core": CORES, "ornament": ORNAMENTS}
SLOT_PIVOT = {"handle": 0.0, "shaft": HANDLE_TOP, "tip": SHAFT_TOP, "core": 0.0, "ornament": 0.0}


def build():
    w = Wand()
    for slot in SLOTS:
        w.bone(slot, "wand_root", (0, SLOT_PIVOT[slot], 0))
        for variant, builder in VARIANTS[slot].items():
            b = w.bone(f"{slot}_{variant}", slot, (0, SLOT_PIVOT[slot], 0))
            builder(w, b)
    w.bone(ATTACHMENT, "wand_root", (0, TIP_TOP, 0))
    return w


# --------------------------------------------------------------------------- paint
# The wand_skin.py treatment, carried over unchanged so the base wand keeps its look.

WOOD_DARK = hx("#8e877c")[:3]
WOOD_MID = hx("#bdb4a6")[:3]
WOOD_PALE = hx("#ddd4c4")[:3]
MATERIALS = {
    "brass": hx("#a8813a")[:3],
    # A set stone, not a jewel: muted and slightly grey, inlaid rather than stuck on.
    "gem": hx("#6b5480")[:3],
    "silver": hx("#aeb4ba")[:3],
    "leather": hx("#6e5038")[:3],
    "groove": hx("#5c554c")[:3],
    "eye": hx("#c8b45a")[:3],
    "leaf": hx("#5f6b44")[:3],
    "thorn": hx("#6a6258")[:3],
}
CORE_COLOURS = {
    "core_phoenix_feather": hx("#e0562b")[:3],
    "core_dragon_heartstring": hx("#b3241d")[:3],
    "core_unicorn_hair": hx("#f2f0ee")[:3],
    "core_thestral_tail": hx("#2b2630")[:3],
    "core_veela_hair": hx("#e8dfa8")[:3],
}
# Per-variant wood (dark, pale) for the shapes named after screen wands: on film the colour is half
# of what tells two wands apart. The renderer's wood tint still multiplies into these.
VARIANT_WOOD = {
    "handle_bone": (hx("#d8d2c2")[:3], hx("#e6e1d3")[:3]),
    "handle_talon": (hx("#3a2f2a")[:3], hx("#4a3d35")[:3]),
    "handle_vine": (hx("#6b5433")[:3], hx("#856a42")[:3]),
    "handle_flared": (hx("#4a3526")[:3], hx("#5f452f")[:3]),
    "handle_briar": (hx("#5a3b2b")[:3], hx("#744d37")[:3]),
    "handle_bellatrix": (hx("#2c2420")[:3], hx("#3e332d")[:3]),
    "shaft_nodular": (hx("#8a8275")[:3], hx("#a49a8a")[:3]),
    "shaft_barked": (hx("#584030")[:3], hx("#6d5039")[:3]),
    "shaft_bellatrix": (hx("#2c2420")[:3], hx("#3e332d")[:3]),
    "tip_claw": (hx("#3a2f2a")[:3], hx("#57473c")[:3]),
    "tip_serpent": (hx("#7d8288")[:3], hx("#9aa1a8")[:3]),
    "tip_spiralled": (hx("#6b4527")[:3], hx("#8a5c33")[:3]),
    "tip_bellatrix": (hx("#2c2420")[:3], hx("#3e332d")[:3]),
}
ROLE_WOOD = {"handle": WOOD_DARK, "shaft": WOOD_PALE, "tip": WOOD_PALE}
PATTERN = {"handle": "SKIN", "shaft": "SKIN", "tip": "SKIN", "core": "SMOKE", "ornament": "METAL"}


def variant_of(w, bone):
    """The registered variant bone a (possibly nested) bone belongs to."""
    while bone:
        parent = w.by_name[bone].get("parent")
        if parent in SLOTS:
            return bone
        bone = parent
    raise ValueError("bone outside every slot")


def paint(w):
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    painted = set()
    for index, (bone, cube, mat) in enumerate(w.cubes):
        if tuple(cube["uv"]) in painted:
            continue
        painted.add(tuple(cube["uv"]))
        variant = variant_of(w, bone)
        role = variant.split("_", 1)[0]
        if mat == "wood":
            base = ROLE_WOOD[role]
            override = VARIANT_WOOD.get(variant)
            if override:
                base = override[1] if base == WOOD_PALE else override[0]
        elif mat == "core":
            base = CORE_COLOURS[variant]
        else:
            base = MATERIALS[mat]
        wooden = mat == "wood" and role in ("handle", "shaft", "tip")
        style = "METAL" if mat in ("brass", "silver") else PATTERN[role]
        u, v = cube["uv"]
        cw, ch, cd = cube["size"]
        seed = (sum(ord(c) for c in variant) + index * 37) & 0xFFFF
        for face, (fx0, fy0, fx1, fy1) in bs.box_faces(u, v, cw, ch, cd).items():
            x0, y0 = int(fx0), int(fy0)
            fw, fh = int(fx1 - fx0), int(fy1 - fy0)
            shade = bs.FACE_SHADE[face]
            long_face = face in ("north", "south", "east", "west") and fh > fw
            for ly in range(fh):
                for lx in range(fw):
                    f = shade * bs.pattern_factor(style, lx, ly, fw, fh, seed)
                    if wooden and long_face:
                        # Turned wood: grain down the length, a lit streak on the near edge so a
                        # two-texel face still reads round, binding bands on a grip.
                        if fw > 1 and lx == 0:
                            f *= 1.12
                        elif fw > 1 and lx == fw - 1:
                            f *= 0.86
                        if (lx * 5 + ly // 3 + seed) % 7 == 0:
                            f *= 0.9
                        if role == "handle" and ly % 4 == 3:
                            f *= 0.82
                    elif lx == 0 or ly == 0 or lx == fw - 1 or ly == fh - 1:
                        f *= 0.88
                    col = bs.lit(base, f)
                    x, y = x0 + lx, y0 + ly
                    if 0 <= x < TEX_W and 0 <= y < TEX_H:
                        px[x, y] = (col[0], col[1], col[2], 255)
    return posterise(img, 12)


# --------------------------------------------------------------------------- clips
# Deltas on wand_root, played by WandItem's controller: `charge` loops while the holder is in a wand
# hold, `cast` plays once when the hold ends. The rest pose is the idle -- a wand in a slot or a
# resting hand does not move. Values from the V5 foundation; the charge is a tremor of the whole
# wand (V5 scaled the empty attachment bone, which draws nothing).

def clips():
    return {
        "animation.wand.cast": {
            "loop": False,
            "animation_length": 0.32,
            "bones": {"wand_root": {"rotation": {
                "0.0": [0, 0, 0], "0.08": [-8, 0, 0], "0.18": [5, 0, 0], "0.32": [0, 0, 0]}}},
        },
        "animation.wand.charge": {
            "loop": True,
            "animation_length": 0.4,
            "bones": {"wand_root": {"rotation": {
                "0.0": [0, 0, 0], "0.1": [0.6, 0, -0.8], "0.2": [-0.5, 0, 0.6],
                "0.3": [0.4, 0, -0.4], "0.4": [0, 0, 0]}}},
        },
    }


# --------------------------------------------------------------------------- checks

def world_boxes(geo, keep=None):
    """Every cube's rendered corners in model space, bone rest rotations applied (GeckoLib sense)."""
    import numpy as np
    import beast_preview as bp
    out = []
    for name, cube, m, off in bp.collect_cubes(geo):
        if keep is not None and name not in keep:
            continue
        ox, oy, oz = cube["origin"]
        sx, sy, sz = cube["size"]
        g = cube.get("inflate", 0.0)
        pts = [m @ np.array([x, y, z]) + off
               for x in (ox - g, ox + sx + g) for y in (oy - g, oy + sy + g) for z in (oz - g, oz + sz + g)]
        out.append((name, cube, pts))
    return out


def subtree(geo, root):
    kids = {}
    for b in geo["bones"]:
        kids.setdefault(b.get("parent"), []).append(b["name"])
    names, stack = set(), [root]
    while stack:
        n = stack.pop()
        names.add(n)
        stack.extend(kids.get(n, []))
    return names


def check(geo):
    """The contract, measured on the rig as posed. Returns a list of failures."""
    problems = []
    g = geo["minecraft:geometry"][0]
    for b in g["bones"]:
        for c in b.get("cubes", []):
            if "rotation" in c or "pivot" in c:
                problems.append(f"{b['name']}: rotated cube")
            if any(int(s) != s or s < 1 for s in c["size"]):
                problems.append(f"{b['name']}: size {c['size']}")
            u, v = c["uv"]
            sw, sh, sd = c["size"]
            if u + 2 * sd + 2 * sw > TEX_W or v + sd + sh > TEX_H:
                problems.append(f"{b['name']}: uv island off the sheet")
    eps = 1e-6
    for slot, lo_plane, hi_plane in (("handle", None, HANDLE_TOP), ("shaft", HANDLE_TOP, SHAFT_TOP),
                                     ("tip", SHAFT_TOP, None)):
        for variant in VARIANTS[slot]:
            name = f"{slot}_{variant}"
            boxes = world_boxes(g, subtree(g, name))
            ys = [p[1] for _, _, pts in boxes for p in pts]
            if hi_plane is not None and max(ys) > hi_plane + eps:
                problems.append(f"{name}: reaches y {max(ys):.2f} past its top plane {hi_plane}")
            if lo_plane is not None and min(ys) < lo_plane - eps:
                problems.append(f"{name}: reaches y {min(ys):.2f} below its base plane {lo_plane}")
            # Joint sections: what sits on each plane must be centred and inside the contract.
            for plane, want in ((hi_plane, "top"), (lo_plane, "base")):
                if plane is None:
                    continue
                touching = [pts for _, _, pts in boxes
                            if abs((max(p[1] for p in pts) if want == "top" else min(p[1] for p in pts)) - plane) < eps]
                if not touching:
                    problems.append(f"{name}: nothing meets the {want} plane y={plane}")
                    continue
                xs = [p[0] for pts in touching for p in pts]
                zs = [p[2] for pts in touching for p in pts]
                span_x, span_z = (min(xs), max(xs)), (min(zs), max(zs))
                if slot == "handle" and not (span_x[0] <= -0.75 + eps and span_x[1] >= 0.75 - eps
                                             and span_z[0] <= -0.75 + eps and span_z[1] >= 0.75 - eps):
                    problems.append(f"{name}: top face {span_x}x{span_z} does not cover the 1.5 shaft base")
                if slot == "shaft" and want == "base" and max(abs(span_x[0] + 0.75), abs(span_x[1] - 0.75),
                                                              abs(span_z[0] + 0.75), abs(span_z[1] - 0.75)) > eps:
                    problems.append(f"{name}: base {span_x}x{span_z} is not the 1.5 section")
                if slot == "shaft" and want == "top" and not (
                        -0.75 - eps <= span_x[0] <= -0.5 + eps and 0.5 - eps <= span_x[1] <= 0.75 + eps
                        and -0.75 - eps <= span_z[0] <= -0.5 + eps and 0.5 - eps <= span_z[1] <= 0.75 + eps):
                    problems.append(f"{name}: top {span_x}x{span_z} is off the axis or out of 1.0..1.5")
                if slot == "tip" and want == "base":
                    core = [pts for pts in touching
                            if abs(min(p[0] for p in pts) + 0.5) < eps and abs(max(p[0] for p in pts) - 0.5) < eps]
                    if not core:
                        problems.append(f"{name}: base has no 1.0 neck on the axis")
    att = next((b for b in g["bones"] if b["name"] == ATTACHMENT), None)
    if att is None or att.get("parent") != "wand_root" or att["pivot"] != [0.0, TIP_TOP, 0.0] or "rotation" in att:
        problems.append(f"{ATTACHMENT} must be an unrotated child of wand_root at (0, {TIP_TOP}, 0)")
    return problems


# --------------------------------------------------------------------------- preview

def render_combo(geo, tex, handle, shaft, tip, size=192, side=False, extra=()):
    """One assembled wand, drawn as GeckoLib draws it (mirrored X, bone rotations applied)."""
    import numpy as np
    import beast_preview as bp
    g = geo["minecraft:geometry"][0]
    keep = set()
    for name in (f"handle_{handle}", f"shaft_{shaft}", f"tip_{tip}") + tuple(extra):
        keep |= subtree(g, name)
    view = bp.rot_x(math.radians(0 if side else 15)) @ bp.rot_y(math.radians(0 if side else 35))
    th, tw = tex.shape[:2]
    tris = []
    for name, cube, m, off in bp.collect_cubes(g):
        if name not in keep:
            continue
        ox, oy, oz = (float(c) for c in cube["origin"])
        w, h, d = (float(s) for s in cube["size"])
        inf = float(cube.get("inflate", 0.0))
        u0, v0 = cube["uv"]
        for fname, mults in bp.FACES.items():
            cs = [view @ (bp.MIRROR @ (m @ np.array([ox - inf + mu[0] * (w + 2 * inf),
                                                     oy - inf + mu[1] * (h + 2 * inf),
                                                     oz - inf + mu[2] * (d + 2 * inf)]) + off))
                  for mu in mults]
            ru, rv, rw, rh = bp.uv_rect(fname, u0, v0, w, h, d)
            tris.append((cs, [(ru, rv + rh), (ru + rw, rv + rh), (ru + rw, rv), (ru, rv)]))
    # Fixed frame so every wand in a sheet is drawn at one scale: y -5..30 plus the width.
    scale = (size - 16) / 36.0
    frame = np.zeros((size, size, 3), dtype=np.float32)
    frame[:] = np.array(bp.BG, dtype=np.float32) / 255.0
    zbuf = np.full((size, size), -1e9, dtype=np.float32)
    for cs, uvs in tris:
        s = [(c[0] * scale + size / 2, size - 8 - (c[1] + 5.5) * scale, c[2]) for c in cs]
        for a, bb, c in ((0, 1, 2), (0, 2, 3)):
            bp.raster(frame, zbuf, [s[a], s[bb], s[c]], [uvs[a], uvs[bb], uvs[c]], tex, tw, th)
    # plane guides: 11, 23 and the attachment
    for y, col in ((HANDLE_TOP, (70, 70, 90)), (SHAFT_TOP, (70, 70, 90)), (TIP_TOP, (200, 80, 80))):
        row = int(size - 8 - (y + 5.5) * scale)
        if 0 <= row < size:
            for x in range(0, size, 4):
                if zbuf[row, x] < -1e8:
                    frame[row, x] = np.array(col) / 255.0
    return Image.fromarray((np.clip(frame, 0, 1) * 255).astype("uint8"), "RGB")


def preview(out_dir, geo):
    import numpy as np
    from PIL import ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    tex = np.asarray(Image.open(TEX_PATH).convert("RGBA")).astype(np.float32) / 255.0
    sheets = {
        "combos": [("classic", "straight", "pointed"), ("bellatrix", "straight", "pointed"),
                   ("classic", "bellatrix", "crystal"), ("briar", "sinuous", "hook"),
                   ("twisted", "reeded", "leaf"), ("bellatrix", "bellatrix", "bellatrix"),
                   ("bone", "tapered", "claw"), ("flared", "spiral", "spiralled")],
        "handles": [(h, "straight", "pointed") for h in HANDLES],
        "shafts": [("classic", s, "pointed") for s in SHAFTS],
        "tips": [("classic", "straight", t) for t in TIPS],
    }
    for sheet, combos in sheets.items():
        for side in (False, True):
            tiles = [render_combo(geo, tex, *c, side=side) for c in combos]
            cols = 8
            rows = (len(tiles) + cols - 1) // cols
            img = Image.new("RGB", (cols * 192, rows * 212), (20, 20, 26))
            d = ImageDraw.Draw(img)
            for i, (t, c) in enumerate(zip(tiles, combos)):
                x, y = (i % cols) * 192, (i // cols) * 212
                img.paste(t, (x, y))
                d.text((x + 4, y + 194), "+".join(c), fill=(220, 220, 220))
            path = os.path.join(out_dir, f"wand_{sheet}{'_side' if side else ''}.png")
            img.save(path)
            print("preview", path)


# --------------------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="claim a wand.png another tool wrote")
    ap.add_argument("--preview", metavar="DIR", help="also render combination sheets here")
    ap.add_argument("--check", action="store_true", help="only validate the geo on disk")
    args = ap.parse_args()

    if args.check:
        geo = json.load(open(GEO_PATH, encoding="utf-8"))
        problems = check(geo)
        print("\n".join(problems) or "wand.geo.json: contract holds")
        return 1 if problems else 0

    if not args.force and not is_regenerable(TEX_PATH, MARKER):
        print("wand.png is another tool's; rerun with --force to claim it")
        return 1
    w = build()
    rows = w.pack()
    if rows > TEX_H:
        print(f"ERROR: the islands need {rows} rows; the sheet is {TEX_H}", file=sys.stderr)
        return 1
    geo = w.geo()
    problems = check(geo)
    if problems:
        print("\n".join("ERROR: " + p for p in problems), file=sys.stderr)
        return 1
    with open(GEO_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(geo, fh, indent=2)
        fh.write("\n")
    with open(ANIM_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"format_version": "1.8.0", "animations": clips()}, fh, indent=2)
        fh.write("\n")
    save(paint(w), TEX_PATH, MARKER)
    print(f"wand: {len(w.bones)} bones, {len(w.cubes)} cubes, {rows}/{TEX_H} sheet rows; "
          f"{len(HANDLES)} handles x {len(SHAFTS)} shafts x {len(TIPS)} tips")
    if args.preview:
        preview(args.preview, geo)
    return 0


if __name__ == "__main__":
    sys.exit(main())
