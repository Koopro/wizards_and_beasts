#!/usr/bin/env python3
"""Sculpting kit shared by the hand-built creature rigs.

`tools/ghoul_model.py` established the shape of one of these generators — declare bones and
cubes, shelf-pack the box-UV islands, paint through the rectangles the packer hands back,
write geo + animation + skin together — and `hippogriff_model.py`, `thestral_model.py` and the
rest each re-implemented it. That was tolerable for five rigs. It is not tolerable for the
eighty still on bulk-generated boxes, and every re-implementation is another chance to
re-introduce one of the landmines below.

So this module owns the mechanics and a per-creature generator owns only the animal:

    rig = Rig("kneazle", tex_w=64)
    body = rig.bone("body", "root", pivot=(0, 7, 0))
    rig.cube(body, origin=(-3, 5, -5), size=(6, 5, 10))
    ...
    emit(rig, paint(rig), clips, cid="kneazle", tool="kneazle_model.py", hitbox=(0.65, 0.75))

What it enforces, all of it learned the hard way:

  - **Cube sizes are whole numbers.** GeckoLib lays box UV out from `Math.floor(size)`
    (`BakedModelFactory.buildQuad`), while the packer rounds up, so a fractional size makes
    every face on that cube sample a texel off and the top/bottom faces sample the island's
    unfilled corner — transparent, i.e. visible holes on the model. `Rig.cube` refuses one.
    Grow a cube past its texel budget with `inflate`, which moves geometry and leaves UV alone.
  - **One island per cube, assigned by the packer**, so geometry and skin cannot drift.
  - **Keyframes are deltas from the authored rest pose**, because GeckoLib composes
    `baseRot + frameValue`. `osc()` therefore oscillates around zero by default and a clip that
    wants to hold a limb somewhere else says so with `base=`.
  - **Every animated bone exists in the rig.** GeckoLib skips a keyframe track whose bone name
    it cannot find, silently, so a rig can ship fully frozen and look merely lifeless. `emit`
    refuses rather than shipping it.
  - **The sheet is measured, not declared.** `emit` derives the height from the pack and fails
    loudly rather than shipping a declared-vs-shipped mismatch, which `rig_is_renderable`
    rejects and which has bitten this repo twice.

**The rotation sign rule**, derived once here so no generator has to re-derive it wrong.
Bedrock's X rotation runs opposite the usual right-handed sense, and the model faces north
(-Z), so for a **positive** X rotation:

    a bone pointing UP      leans FORWARD (-Z)   — a neck raking over, a head nodding down
    a bone HANGING DOWN     swings BACKWARD (+Z) — a tail lifting, a foreleg reaching back
    a bone pointing BACK    lifts UP             — a tail cocking, a wing rising

Getting it backwards is not subtle and is not visible in the numbers: the first unicorn pass
raked its neck over its own rump and swung its tail under its belly, and read as a headless
pony until it was rendered. Render, always: `python tools/beast_preview.py <id>`.

Every generator built on this stays under two hundred lines, most of which is the animal.
"""

import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, mix as _blend, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces as island_faces  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = f"{ASSETS}/geckolib/models/entity"
ANIM_DIR = f"{ASSETS}/geckolib/animations/entity"
TEX_DIR = f"{ASSETS}/textures/entity"
DEF_DIR = "src/main/resources/data/wizards_and_beasts/creatures"

UNITS_PER_BLOCK = 16.0

_IDENTITY = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))


# --------------------------------------------------------------------------- transforms
#
# Enough linear algebra to measure a posed rig, in Bedrock's convention: euler XYZ with the
# X and Y senses flipped relative to the usual right-handed maths, matching
# `beast_preview.euler_matrix` so the measurement and the picture agree.


def _euler(rot):
    # GeckoLib's own sense, drawn in the unmirrored geo space: X and Z negated, Y as authored.
    # See `beast_preview.euler_matrix` — the previous signs had Y and Z backwards.
    rx, ry, rz = (math.radians(float(v)) for v in rot)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    mx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    my = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    mz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return _matmul(mz, _matmul(my, mx))


def _matmul(a, b):
    return tuple(tuple(sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3))
                 for r in range(3))


def _apply(m, v, off=(0.0, 0.0, 0.0)):
    return tuple(sum(m[r][c] * v[c] for c in range(3)) + off[r] for r in range(3))


def _hash(x, y, seed):
    """Avalanched 32-bit hash of a texel coordinate — noise without a visible lattice."""
    n = (x * 0x9E3779B1) ^ (y * 0x85EBCA77) ^ (seed * 0xC2B2AE3D)
    n &= 0xFFFFFFFF
    n ^= n >> 15
    n = (n * 0x2545F491) & 0xFFFFFFFF
    n ^= n >> 13
    return n


def _cube_transform(cube):
    """A cube's own rotation about its own pivot, applied inside its bone's frame."""
    rot = cube.get("rotation")
    if not rot:
        return _IDENTITY, (0.0, 0.0, 0.0)
    m = _euler(rot)
    pivot = cube.get("pivot", [0.0, 0.0, 0.0])
    return m, tuple(pivot[i] - _apply(m, pivot)[i] for i in range(3))


# --------------------------------------------------------------------------- rig


class Bone:
    """A named joint. Cubes hang off it; children rotate with it."""

    def __init__(self, name, parent, pivot, rotation):
        self.name = name
        self.parent = parent
        self.pivot = tuple(float(v) for v in pivot)
        self.rotation = tuple(rotation) if rotation else None
        self.cubes = []

    def __repr__(self):
        return f"Bone({self.name})"


class Rig:
    """Bones, cubes and their box-UV islands for one creature.

    Built facing north (-Z) with the creature's feet on y=0, which is what the entity
    renderer expects and what makes the offline previewer agree with the game.
    """

    def __init__(self, name, tex_w):
        self.name = name
        self.tex_w = tex_w
        self.bones = []
        self._by_name = {}
        self._cubes = {}  # key -> (cube dict, size)
        self.packer = None
        self.bone("root", None, (0, 0, 0))

    # -- construction -----------------------------------------------------------

    def bone(self, name, parent="root", pivot=(0, 0, 0), rotation=None):
        if name in self._by_name:
            raise ValueError(f"{self.name}: duplicate bone {name!r}")
        if parent is not None and parent not in self._by_name:
            raise ValueError(f"{self.name}: bone {name!r} parents to unknown {parent!r}")
        bone = Bone(name, parent, pivot, rotation)
        self.bones.append(bone)
        self._by_name[name] = bone
        return bone

    def __getitem__(self, name):
        return self._by_name[name]

    def cube(self, bone, origin, size, key=None, inflate=None, rotation=None, pivot=None):
        """Add one box to `bone`, and reserve its UV island.

        `size` must be whole numbers of model units and at least 1 on every axis — see the
        module docstring. `inflate` is the way to make a cube fractionally bigger (a garment
        over a limb, a fur puff over a body) without touching its UV footprint.
        """
        bone = self._bone(bone)
        for axis, value in zip("xyz", size):
            if abs(value - round(value)) > 1e-9:
                raise ValueError(
                    f"{self.name}: cube on {bone.name} has fractional {axis} size {value} — "
                    f"GeckoLib floors it and the faces sample a texel off; use inflate instead")
            if value < 1:
                raise ValueError(
                    f"{self.name}: cube on {bone.name} has {axis} size {value} — "
                    f"anything under 1 collapses to a zero-area UV")
        size = tuple(int(round(v)) for v in size)
        key = key or (bone.name if not bone.cubes else f"{bone.name}#{len(bone.cubes)}")
        if key in self._cubes:
            raise ValueError(f"{self.name}: duplicate cube key {key!r}")
        cube = {"origin": [round(float(v), 2) for v in origin], "size": list(size)}
        if inflate:
            cube["inflate"] = round(float(inflate), 2)
        if rotation:
            cube["rotation"] = [round(float(v), 2) for v in rotation]
            cube["pivot"] = [round(float(v), 2) for v in (pivot if pivot else bone.pivot)]
        bone.cubes.append(cube)
        self._cubes[key] = (cube, size)
        return key

    def _bone(self, bone):
        return bone if isinstance(bone, Bone) else self._by_name[bone]

    # -- mirroring --------------------------------------------------------------

    def pair(self, build):
        """Run `build(side, sign)` for left then right.

        `sign` is +1 on the left (+x) and -1 on the right, so a limb is written once with
        `sign *` on the x terms and `mirror()` on any cube origin. Symmetry by construction:
        the alternative is two blocks of near-identical numbers where one gets edited.
        """
        build("left", 1)
        build("right", -1)

    @staticmethod
    def mirror(origin, size, sign):
        """`origin` reflected across x=0 when `sign` is negative, unchanged when positive.

        A cube's origin is its minimum corner, so reflecting it is `-x - width`, not `-x`.
        """
        x, y, z = origin
        return (x, y, z) if sign > 0 else (-x - size[0], y, z)

    # -- packing ----------------------------------------------------------------

    def pack(self):
        """Assign every cube an island, tallest first, and report the rows used."""
        self.packer = Packer(self.tex_w)
        rows = self.packer.place_sorted([(k, s) for k, (_, s) in self._cubes.items()])
        for key, (cube, _) in self._cubes.items():
            cube["uv"] = list(self.packer.placed[key][0])
        return rows

    def faces(self, key):
        """The six face rects of one cube's island, keyed `top/bottom/east/north/west/south`."""
        uv, size = self.packer.placed[key]
        return island_faces(uv, size)

    def bone_names(self):
        return set(self._by_name)

    def keys(self, *prefixes):
        """Every cube key starting with any of `prefixes` — for painting a whole limb at once."""
        return [k for k in self._cubes if any(k.startswith(p) for p in prefixes)]

    # -- output -----------------------------------------------------------------

    def bounds(self):
        """Axis-aligned extent of the rest pose **as posed**, in model units.

        The rest rotations have to be applied for this number to mean anything. A neck is
        authored standing straight up and then raked forward by its own rotation; measuring
        the authored boxes says the creature is a third taller than it renders, which is
        worse than not measuring at all — it is the number the hitbox gets judged against.
        """
        lo = [math.inf] * 3
        hi = [-math.inf] * 3
        cache = {}
        for bone in self.bones:
            m, off = self._world_of(bone.name, cache)
            for cube in bone.cubes:
                grow = cube.get("inflate", 0.0)
                cm, coff = _cube_transform(cube)
                ox, oy, oz = cube["origin"]
                sx, sy, sz = cube["size"]
                for dx in (-grow, sx + grow):
                    for dy in (-grow, sy + grow):
                        for dz in (-grow, sz + grow):
                            p = _apply(cm, (ox + dx, oy + dy, oz + dz), coff)
                            p = _apply(m, p, off)
                            for i in range(3):
                                lo[i] = min(lo[i], p[i])
                                hi[i] = max(hi[i], p[i])
        return lo, hi

    def _world_of(self, name, cache):
        """The bone's rest transform as a 3x3 matrix plus an offset, in Bedrock's own order."""
        if name in cache:
            return cache[name]
        bone = self._by_name[name]
        m = _euler(bone.rotation) if bone.rotation else _IDENTITY
        if bone.parent:
            pm, poff = self._world_of(bone.parent, cache)
        else:
            pm, poff = _IDENTITY, (0.0, 0.0, 0.0)
        total = _matmul(pm, m)
        pivot = bone.pivot
        local = tuple(pivot[i] - _apply(m, pivot)[i] for i in range(3))
        off = tuple(poff[i] + _apply(pm, local)[i] for i in range(3))
        cache[name] = (total, off)
        return cache[name]

    def geo(self, tex_h):
        lo, hi = self.bounds()
        width = max(hi[0] - lo[0], hi[2] - lo[2]) / UNITS_PER_BLOCK
        height = (hi[1] - lo[1]) / UNITS_PER_BLOCK
        bones = []
        for bone in self.bones:
            out = {"name": bone.name, "pivot": [round(v, 2) for v in bone.pivot]}
            if bone.parent:
                out["parent"] = bone.parent
            if bone.rotation:
                out["rotation"] = [round(float(v), 2) for v in bone.rotation]
            if bone.cubes:
                out["cubes"] = bone.cubes
            bones.append(out)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.name}",
                    "texture_width": self.tex_w,
                    "texture_height": tex_h,
                    # A margin either side: visible bounds are the culling box, and a rig that
                    # animates past its own rest extent pops out of view at the screen edge.
                    "visible_bounds_width": round(width + 1.0, 2),
                    "visible_bounds_height": round(height + 1.0, 2),
                    "visible_bounds_offset": [0, round(height / 2, 2), 0],
                },
                "bones": bones,
            }],
        }

    def cube_count(self):
        return len(self._cubes)


def limb(rig, name, parent, *, x, z, sign=1, joints):
    """A chain of stacked segments: a leg, an arm, a spider's limb.

    `joints` runs from the body outward as `(suffix, top_y, height, width, depth, rotation)`.
    Each segment is a bone pivoted at its own top and parented to the segment above, which is
    what lets the chain bend at every joint instead of swinging as one rigid pole — the single
    thing that separates a walk cycle from a box rig sliding its planks back and forth.
    """
    parent_name = parent if isinstance(parent, str) else parent.name
    keys = []
    for suffix, top, height, w, d, rot in joints:
        bone_name = f"{name}_{suffix}" if suffix else name
        bone = rig.bone(bone_name, parent_name, (sign * x, top, z), rot)
        origin = Rig.mirror((x - w / 2.0, top - height, z - d / 2.0), (w, height, d), sign)
        keys.append(rig.cube(bone, origin, (w, height, d)))
        parent_name = bone_name
    return keys


# --------------------------------------------------------------------------- skin


class Skin:
    """Painter over the packed sheet. Every draw addresses a cube by key, never by pixel.

    House style, from the goblin pass: flat fill, a one-pixel bevel, hand-placed features,
    and a light dither. Not per-face top-light/bottom-dark shading — Minecraft already lights
    faces directionally and painting it in again double-shades every cube.
    """

    SIDES = ("east", "north", "west", "south")
    ALL = ("top", "bottom", "east", "north", "west", "south")

    def __init__(self, rig, tex_h, *, grain="strand"):
        self.rig = rig
        # The material the house dither draws: "strand" (fur, hair, bark, cloth) or "fleck"
        # (skin, hide, scale, feather, smoke). A single `skin()` call can override it.
        # "speckle" is the legacy look, frozen for the redesigns approved with it.
        self.grain = grain
        self.img = Image.new("RGBA", (rig.tex_w, tex_h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        # Which texels render full-bright. A mask, not colours: `emit` fills it from the final
        # posterised skin, so the glow is always exactly the colour the texel was painted.
        self.glow_mask = Image.new("L", (rig.tex_w, tex_h), 0)
        self._glow = ImageDraw.Draw(self.glow_mask)

    # -- emissive -----------------------------------------------------------------

    def glow_rect(self, r):
        x, y, w, h = r
        if w > 0 and h > 0:
            self._glow.rectangle([x, y, x + w - 1, y + h - 1], fill=255)

    def glow(self, keys, faces=None):
        """Light whole faces of the named cubes — a horn, a sting, a fire vent."""
        for key in ([keys] if isinstance(keys, str) else keys):
            f = self.rig.faces(key)
            for name in (faces or self.ALL):
                self.glow_rect(f[name])

    def glow_mark(self, key, face, dx, dy, *, w=1, h=1):
        """Light a patch placed exactly as `mark` places one — an eye, a gem."""
        x, y, fw, fh = self.rig.faces(key)[face]
        px = x + (dx if dx >= 0 else fw + dx)
        py = y + (dy if dy >= 0 else fh + dy)
        self.glow_rect((px, py, w, h))

    def has_glow(self):
        return self.glow_mask.getbbox() is not None

    # -- primitives -------------------------------------------------------------

    def rect(self, r, colour):
        x, y, w, h = r
        if w > 0 and h > 0:
            self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=colour)

    def hline(self, r, row, colour, inset=0):
        x, y, w, h = r
        if 0 <= row < h:
            self.d.line([(x + inset, y + row), (x + w - 1 - inset, y + row)], fill=colour)

    def vline(self, r, col, colour, inset=0):
        x, y, w, h = r
        if 0 <= col < w:
            self.d.line([(x + col, y + inset), (x + col, y + h - 1 - inset)], fill=colour)

    def face(self, key, name):
        return self.rig.faces(key)[name]

    # -- the house fill ---------------------------------------------------------

    def skin(self, keys, colour, *, top=None, bottom=None, bevel=0.88, dither=0.07,
             dither_colour=None, seed=None, grain=None):
        """Fill each named cube flat, then bevel and dither it.

        `top`/`bottom` override the fill on those two faces — a pale belly is the one place
        per-face colour is right, because it is the animal's markings and not fake lighting.
        """
        for key in ([keys] if isinstance(keys, str) else keys):
            f = self.rig.faces(key)
            s = seed if seed is not None else sum(ord(c) for c in key)
            for name in self.ALL:
                fill = colour
                if name == "top" and top is not None:
                    fill = top
                elif name == "bottom" and bottom is not None:
                    fill = bottom
                self.rect(f[name], fill)
                if bevel:
                    self.hline(f[name], 0, shade(fill, 1.0 / bevel))
                    self.hline(f[name], f[name][3] - 1, shade(fill, bevel))
                if dither:
                    self.dither(f[name], dither_colour or shade(fill, bevel), dither, s,
                                grain=grain or self.grain)

    # Grain lattice: one strand candidate per cell, rows staggered so strands never line up.
    GRAIN_CELL = (3, 4)
    # How far a strand moves from the fill toward the dither colour. Below 1 on purpose: a full
    # step reads as a spot at distance, half a step reads as texture (visual style guide, 2.3).
    GRAIN_STRENGTH = 0.55
    GRAIN_LIGHT = 1.10

    def dither(self, r, colour, density, seed, *, grain="strand"):
        """Deterministic material grain — the same pixels on every run.

        This replaced a random one-texel speckle in the colour of the bevel. Scattered full-step
        dots read as spots and disease at any distance; vanilla fur is structure — strands laid
        one way, a shade apart from the coat. So each strand is two texels tall, sits on a
        jittered, row-staggered lattice (never a visible grid, never a clump), is blended only
        part of the way toward `colour`, and every other strand gets one lit texel on its tip.
        `density` keeps its meaning: roughly the share of texels that change.

        `grain="fleck"` is the same lattice for surfaces that have no hair — skin, hide, scale,
        feather, smoke: a one-texel fleck (every third one two wide) blended the same part-way,
        so the surface has tooth without a single hard dot.

        The hash is avalanched rather than a plain weighted sum of the coordinates, so jitter
        does not fall on a lattice of its own.
        """
        x0, y0, w, h = r
        if grain == "speckle":
            # The pre-2026-09-27 speckle, kept byte-for-byte for the hand-tuned redesigns whose
            # current skins were approved with it (Dementor, Hippogriff, Basilisk, Phoenix,
            # Kelpie). New work uses "strand" or "fleck".
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if _hash(x, y, seed) % 1000 < density * 1000:
                        self.d.point((x, y), fill=colour)
            return
        # A face can run past the sheet edge; the old point-speckle clipped there silently.
        w = min(w, self.img.width - x0)
        h = min(h, self.img.height - y0)
        if density <= 0 or w <= 0 or h <= 0:
            return
        cw, ch = self.GRAIN_CELL
        strand = grain != "fleck"
        chance = min(1.0, density * cw * ch / (2.0 if strand else 1.33))
        for row, cy in enumerate(range(0, h, ch)):
            shift = (row % 2) * (cw // 2 + 1)
            for cx in range(-shift, w, cw):
                n = _hash(x0 + cx, y0 + cy, seed)
                if (n % 1000) >= chance * 1000:
                    continue
                sx = cx + (n >> 10) % cw
                sy = cy + (n >> 14) % max(1, ch - 1)
                if not (0 <= sx < w and sy < h):
                    continue
                if not strand:
                    for dx in ((0, 1) if (n >> 20) % 3 == 0 else (0,)):
                        if sx + dx < w:
                            px, py = x0 + sx + dx, y0 + sy
                            base = self.img.getpixel((px, py))[:3]
                            self.d.point((px, py), fill=_blend(base, colour, self.GRAIN_STRENGTH))
                    continue
                for dy in (0, 1):
                    if sy + dy < h:
                        px, py = x0 + sx, y0 + sy + dy
                        base = self.img.getpixel((px, py))[:3]
                        self.d.point((px, py), fill=_blend(base, colour, self.GRAIN_STRENGTH))
                if (n >> 20) & 1 and sy > 0:
                    px, py = x0 + sx, y0 + sy - 1
                    base = self.img.getpixel((px, py))[:3]
                    self.d.point((px, py), fill=shade(base, self.GRAIN_LIGHT))

    # -- features ---------------------------------------------------------------

    def eyes(self, key, colour, *, face="north", row=1, inset=1, pupil=None, width=1, glow=False):
        """A symmetric pair of eyes on one face, measured in from both edges.

        Guarded rather than clipped: at these sizes an eye that does not fit is a face that is
        too small for eyes at all, and silently painting one texel of eye reads as damage.
        """
        f = self.rig.faces(key)[face]
        x, y, w, h = f
        if w < 2 * (inset + width) + 1 or row >= h:
            raise ValueError(f"{self.rig.name}: {key}.{face} is {w}x{h}, too small for eyes")
        for side in (0, 1):
            ex = x + inset if side == 0 else x + w - inset - width
            self.d.rectangle([ex, y + row, ex + width - 1, y + row], fill=colour)
            if pupil is not None:
                self.d.point((ex if side == 0 else ex + width - 1, y + row), fill=pupil)
            if glow:
                self.glow_rect((ex, y + row, width, 1))

    def mark(self, key, face, dx, dy, colour, *, w=1, h=1, glow=False):
        """A patch measured in from the top-left of one face — a side-set eye, a nostril, a spot.

        Negative `dx`/`dy` measure in from the right/bottom edge, so a feature can be placed
        against either edge without the caller knowing the face's size.
        """
        x, y, fw, fh = self.rig.faces(key)[face]
        px = x + (dx if dx >= 0 else fw + dx)
        py = y + (dy if dy >= 0 else fh + dy)
        if not (x <= px and px + w <= x + fw and y <= py and py + h <= y + fh):
            raise ValueError(f"{self.rig.name}: mark on {key}.{face} ({fw}x{fh}) falls outside it")
        self.d.rectangle([px, py, px + w - 1, py + h - 1], fill=colour)
        if glow:
            self.glow_rect((px, py, w, h))

    def snout(self, key, colour, *, face="north", rows=2, width=2):
        """A muzzle patch centred low on a face."""
        x, y, w, h = self.rig.faces(key)[face]
        cx = x + (w - width) // 2
        self.d.rectangle([cx, y + h - rows, cx + width - 1, y + h - 1], fill=colour)

    def bands(self, keys, colour, *, faces=SIDES, step=3, offset=0):
        """Stripes across the named faces — tabby, tiger, wasp."""
        for key in ([keys] if isinstance(keys, str) else keys):
            f = self.rig.faces(key)
            for name in faces:
                x, y, w, h = f[name]
                for row in range(offset, h, step):
                    self.d.line([(x, y + row), (x + w - 1, y + row)], fill=colour)

    RAMP_STEPS = 4

    def ramp(self, keys, top_colour, bottom_colour, *, faces=SIDES, steps=None):
        """Vertical colour ramp down each named face — plumage, membrane, flame.

        Stepped, never smooth: at most `steps` (default 4) flat bands, the way vanilla shades a
        parrot's wing in two or three tones. A per-row blend over a 14-texel feather is a
        painted gradient, which is the one thing a box-UV skin must not look like.
        """
        smooth = steps is None and self.grain == "speckle"  # the frozen legacy look
        steps = max(2, steps or self.RAMP_STEPS)
        for key in ([keys] if isinstance(keys, str) else keys):
            f = self.rig.faces(key)
            for name in faces:
                x, y, w, h = f[name]
                span = max(1, h - 1)
                for i in range(h):
                    t = i / span if smooth else min(steps - 1, int(i / span * steps)) / (steps - 1)
                    fill = tuple(int(round(a + (b - a) * t))
                                 for a, b in zip(top_colour, bottom_colour))
                    self.d.line([(x, y + i), (x + w - 1, y + i)], fill=fill)

    def feathers(self, key, faces, edge, lit=None, *, step=2, rows=3):
        """Offset rows of feather tips — an edge texel every `step`, rows staggered, an optional lit
        texel above each — which is layered plumage at Minecraft resolution without drawing feathers."""
        f = self.rig.faces(key)
        for name in faces:
            x0, y0, w, h = f[name]
            for yy in range(rows - 1, h, rows):
                off = (yy // rows) % 2
                for xx in range(w):
                    if (xx + off) % step == 0:
                        self.d.point((x0 + xx, y0 + yy), fill=edge)
                    elif lit is not None and yy > 0 and (xx + off) % step == 1:
                        self.d.point((x0 + xx, y0 + yy - 1), fill=lit)

    def tip(self, keys, colour, *, rows=2, faces=ALL):
        """Darken (or recolour) the last rows of each face — a hoof, a claw, a sting."""
        for key in ([keys] if isinstance(keys, str) else keys):
            f = self.rig.faces(key)
            for name in faces:
                x, y, w, h = f[name]
                for row in range(max(0, h - rows), h):
                    self.d.line([(x, y + row), (x + w - 1, y + row)], fill=colour)


# --------------------------------------------------------------------------- animation


def t_key(t):
    return str(round(float(t), 3))


def kf(*pairs):
    """`kf((0.0, (0, 0, 0)), (1.0, (0, 6, 0)))` -> a keyframe track."""
    return {t_key(t): [round(float(v), 2) for v in value] for t, value in pairs}


def osc(length, amp, *, axis="x", phase=0.0, base=0.0, steps=4):
    """A sine oscillation as a rotation track — the gait primitive.

    Keyframes are deltas over the bone's authored rest rotation, so this swings around zero
    unless `base` says otherwise. `phase` is in cycles: a diagonal gait is 0 and 0.5.
    """
    index = "xyz".index(axis)
    track = {}
    for i in range(steps + 1):
        value = [0.0, 0.0, 0.0]
        value[index] = base + amp * math.sin(2 * math.pi * (i / steps + phase))
        track[t_key(length * i / steps)] = [round(v, 2) for v in value]
    return {"rotation": track}


def bob(length, amp, *, cycles=1, axis="y"):
    """A position track that rises and falls `cycles` times — breath, or a footfall lift."""
    index = "xyz".index(axis)
    steps = 4 * cycles
    track = {}
    for i in range(steps + 1):
        value = [0.0, 0.0, 0.0]
        value[index] = amp * math.sin(2 * math.pi * cycles * i / steps)
        track[t_key(length * i / steps)] = [round(v, 3) for v in value]
    return {"position": track}


def gait(length, legs, *, amp=26.0, axis="x"):
    """Rotation tracks for a set of legs. `legs` maps bone name -> phase in cycles.

    A quadruped walks diagonally — front-left with hind-right — which is `{fl: 0, fr: .5,
    hl: .5, hr: 0}`. Per-leg amplitude overrides come as `(phase, amp)`.
    """
    out = {}
    for name, spec in legs.items():
        phase, leg_amp = spec if isinstance(spec, tuple) else (spec, amp)
        out[name] = osc(length, leg_amp, axis=axis, phase=phase)
    return out


def clip(length, bones, *, loop=True):
    return {"loop": bool(loop), "animation_length": round(float(length), 3), "bones": bones}


def animations(name, clips):
    """`{"idle": clip(...)}` -> the file GeckoLib loads, with fully qualified clip names."""
    return {
        "format_version": "1.8.0",
        "animations": {f"animation.{name}.{k}": v for k, v in clips.items()},
    }


# --------------------------------------------------------------------------- emit


# The only one-shot clip names anything ever asks for. `GenericBeastEntity` fires the first
# name a creature declares from each of these lists — on a landed melee hit, on taking damage,
# and on the ambient-noise beat — and `DeathGazeGoal` fires `gaze`. Nothing else has a caller,
# so a clip called `pounce` or `roar` is animation work that will never play once. Name the
# beat from this vocabulary and it is wired for free.
TRIGGERABLE = {
    "attack", "strike", "bite", "lunge",   # GenericBeastEntity.CLIPS_ATTACK
    "hit", "flinch",                       # GenericBeastEntity.CLIPS_HIT
    "death", "die", "collapse",            # GenericBeastEntity.CLIPS_DEATH
    "groan", "hiss", "howl", "call", "song",  # GenericBeastEntity.CLIPS_AMBIENT
    "gaze",                                # DeathGazeGoal
    "web",                                 # WebSnareGoal, AcromantulaEntity nest-webbing
}

# CreatureIdleGoal plays whichever of these the creature's IdleProfile names — its datapack
# `behaviour.idle.actions`, or `IdleProfile.forBodyPlan`. Triggerable only for a creature whose
# profile names it; `_idle_actions` reads which.
IDLE_ACTIONS = {"preen", "ruffle", "stretch", "hover", "flit", "shake", "graze", "sniff",
                "scratch", "coil", "taste_air", "groom", "skitter", "peek", "grunt", "drift",
                "roll", "squirm", "settle"}


def _fights(cid):
    """Whether the creature lands melee hits — the only thing that ever plays an attack clip."""
    path = os.path.join(DEF_DIR, cid + ".json")
    if not os.path.exists(path):
        return False
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    return data.get("attackDamage", 0) > 0 and data.get("temperament") != "PASSIVE" and "dragon" not in data


def _idle_actions(cid):
    """The idle actions this creature's profile asks for, from its definition."""
    import reactions
    path = os.path.join(DEF_DIR, cid + ".json")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    explicit = data.get("behaviour", {}).get("idle", {}).get("actions")
    return list(explicit) if explicit else reactions.IDLE_DEFAULT.get(data.get("bodyPlan"), [])

# Bound by the locomotion class's movement controller rather than declared, so these are not
# one-shot clips and never go in the definition's `clips` list.
MOVEMENT = ("idle", "walk", "fly", "swim")


def _write_glowmask(cid, skin, final, mark, force):
    """Write `<cid>_glowmask.png` from the painted glow, or remove a stale one.

    `GeoRendererHelper.applyGlowIfPresent` attaches the emissive layer whenever that file
    exists, and the mask is addressed by the same UVs as the skin. So a rebuilt rig that keeps
    the old rig's mask glows in texels that now belong to a leg or a flank — every creature
    rebuilt in wave 1 that had shipped a `beast_skins` mask did exactly that. The mask is either
    regenerated from this skin or deleted; there is no third state.
    """
    from artgen_common import is_generated
    path = os.path.join(TEX_DIR, cid + "_glowmask.png")
    if skin.has_glow():
        glow = Image.new("RGBA", final.size, (0, 0, 0, 0))
        glow.paste(final, (0, 0), skin.glow_mask)
        save(glow, path, mark)
        return 0
    if os.path.exists(path):
        if not (force or is_generated(path)):
            print(f"ERROR: {path} is hand-authored and addresses the old UVs; remove it or "
                  f"re-run with --force", file=sys.stderr)
            return 1
        os.remove(path)
        print(f"  removed stale {cid}_glowmask.png (nothing on the new rig glows)")
    return 0


def update_definition(cid, clips, hitbox=None):
    """Drop the placeholder marker from the creature JSON and declare the one-shot clips.

    The marker is what `KNOWN_ISSUES.md` §4.2 and `RigMarkerConsistencyTest` read to decide
    whether this creature is still waiting on art, so leaving it behind a finished rig keeps
    the creature on the backlog for ever. Declaring the clips is what makes them reachable:
    `GenericBeastEntity.triggerDeclared` refuses to fire a clip the definition does not name,
    because GeckoLib throws inside the render pass when asked for a clip that is not there.
    """
    path = os.path.join(DEF_DIR, cid + ".json")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    data.pop("_comment", None)
    if hitbox:
        # The definition mirrors `ModCreatures.Spec`, which is the registration-time copy. Only
        # a generator that deliberately re-sized a creature passes one, and when it does both
        # copies have to move together or the hitbox the player collides with and the hitbox the
        # bestiary prints disagree.
        data["width"], data["height"] = float(hitbox[0]), float(hitbox[1])
    if clips:
        data["clips"] = list(clips)
    else:
        data.pop("clips", None)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def to_game_sense(rig, clips):
    """Convert a rig and its clips from the authoring sense to the one GeckoLib renders.

    Every generator here is written — and every sign rule in this module's docstring was derived —
    in the sense the old `beast_preview` drew: positive Z lifts a left (+x) wing, positive Y yaws
    toward +x. GeckoLib mirrors the geo in X and renders Y and Z the other way round
    (`BakedModelFactory`: rotation `(-x, -y, z)` applied to mirrored geometry; keyframes baked the
    same way in `BakedAnimationsAdapter`). Negating Y and Z on every rest rotation, cube rotation
    and rotation keyframe on the way out makes the game draw exactly what the author drew.
    Found 2026-09-25 when the Acromantula's legs, arched in every preview, rose up in a bundle in a
    running client. Positions and X rotations are unaffected.
    """
    def flip(v):
        return [round(float(v[0]), 2), round(-float(v[1]), 2), round(-float(v[2]), 2)]

    for bone in rig.bones:
        if bone.rotation:
            bone.rotation = tuple(flip(bone.rotation))
        for cube in bone.cubes:
            if cube.get("rotation"):
                cube["rotation"] = flip(cube["rotation"])
    for body in clips.values():
        for tracks in body["bones"].values():
            rot = tracks.get("rotation")
            if isinstance(rot, dict):
                tracks["rotation"] = {t: flip(v) for t, v in rot.items()}
            elif isinstance(rot, list):
                tracks["rotation"] = flip(rot)


def emit(rig, skin, clips, *, cid, tool, hitbox, force=False, colours=20, tex_h=None,
         definition=True, resize=False, write_animation=True, shared_clips=True, extra_clips=()):
    """Write geo, animation and skin together, then report the rig against its hitbox.

    Together is the point: the three files are one artefact, and the way they go wrong here is
    one of them being regenerated without the others.

    `shared_clips` fills the beats the entity asks for and the generator did not author — `hit`,
    `death` and the idle actions of this creature's profile — from `reactions.py`, which reads
    them off the skeleton. A generator's own clip of the same name always wins.

    `extra_clips` are clips a bespoke controller plays (a dragon's `breath` and `bite` belong to
    `DragonEntity`'s own `dragon_action`): allowed on disk, never declared on `beast_action`.
    """
    import copy
    clips = copy.deepcopy(clips)  # callers reuse one dict for a variant rig (kelpie + disguise)
    idle = _idle_actions(cid) if definition else []
    # Only a creature with a definition has the clip gate that plays these; a bespoke entity's
    # Java asks for its own names and would carry them as dead weight.
    if shared_clips and write_animation and definition and os.path.exists(os.path.join(DEF_DIR, cid + ".json")):
        import reactions
        bones = [(b.name, b.parent, b.pivot) for b in rig.bones]
        reactions.fill(bones, clips, idle_actions=idle, fights=_fights(cid))
    to_game_sense(rig, clips)

    dangling = sorted({bone for body in clips.values() for bone in body["bones"]
                       if bone not in rig.bone_names()})
    if dangling:
        print(f"ERROR: {cid} animates bones its rig does not have: {', '.join(dangling)} — "
              f"GeckoLib drops those tracks without a word", file=sys.stderr)
        return 1

    dead = sorted(k for k in clips if write_animation and k not in TRIGGERABLE and k not in MOVEMENT
                  and k not in extra_clips and not (k in IDLE_ACTIONS and k in idle))
    if dead:
        print(f"ERROR: {cid} declares clips nothing can trigger: {', '.join(dead)} — "
              f"rename them from {sorted(TRIGGERABLE)}", file=sys.stderr)
        return 1

    mark = marker(tool)
    tex_path = os.path.join(TEX_DIR, cid + ".png")
    if not force and not is_regenerable(tex_path, mark):
        print(f"{cid}.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 1

    rows = rig.packer.height_used()
    height = tex_h or ((rows + 7) // 8) * 8
    if rows > height:
        print(f"ERROR: {cid} needs {rows} rows, sheet is {height}", file=sys.stderr)
        return 1
    if skin.img.height != height:
        skin.img = skin.img.crop((0, 0, rig.tex_w, height))

    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    with open(os.path.join(GEO_DIR, cid + ".geo.json"), "w", encoding="utf-8") as fh:
        json.dump(rig.geo(height), fh, indent=2)
        fh.write("\n")
    # A variant rig (the Kelpie's disguise) shares its base creature's animation file, so it
    # writes none of its own: `DefaultedGeoModel` resolves animations from the base asset name.
    if write_animation:
        with open(os.path.join(ANIM_DIR, cid + ".animation.json"), "w", encoding="utf-8") as fh:
            json.dump(animations(rig.name, clips), fh, indent=2)
            fh.write("\n")
    final = (posterise(skin.img, colours) if skin.grain == "speckle"
             else _posterise_keeping_features(skin.img, colours))
    save(final, tex_path, mark)
    status = _write_glowmask(cid, skin, final, mark, force)
    if status:
        return status
    if definition:
        update_definition(cid, [k for k in clips if k not in MOVEMENT and k not in extra_clips],
                          hitbox=hitbox if resize else None)

    # GeckoLib's own sampling rule, run on what was just written: a face left unpainted is a
    # transparent hole on the model, and nothing short of this check sees it.
    import uv_check
    holes = uv_check.check(cid)
    if holes:
        for line in holes[:8]:
            print(f"ERROR: {line}", file=sys.stderr)
        return 1

    lo, hi = rig.bounds()
    model_h = (hi[1] - lo[1]) / UNITS_PER_BLOCK
    model_w = max(hi[0] - lo[0], hi[2] - lo[2]) / UNITS_PER_BLOCK
    box_w, box_h = hitbox
    print(f"{cid}: {len(rig.bones)} bones, {rig.cube_count()} cubes, "
          f"sheet {rig.tex_w}x{height} ({rows} rows used)")
    print(f"  model {model_w:.2f} x {model_h:.2f} blocks vs hitbox {box_w} x {box_h} "
          f"({model_w / box_w * 100:.0f}% wide, {model_h / box_h * 100:.0f}% tall)")
    # A unit is a sixteenth of a block, so a fraction of one below the floor is invisible and
    # every jointed leg produces some: `limb` authors each segment's top at an absolute height
    # and the joint rotations above it then swing the foot. Past a unit it is a real gap.
    if lo[1] < -1.0:
        print(f"  WARNING: rig sinks {-lo[1]:.2f} units below the floor")
    return 0


# How far (RGB distance) the palette may move a texel before it is a different colour rather
# than the same colour rounded. An eye is two texels; median-cut spends its budget on the big
# fills and their grain, and folds a dark eye into the nearest brown. Past this, keep the paint.
FEATURE_DISTANCE = 40


def _posterise_keeping_features(img, colours):
    """`posterise`, except texels the palette would move more than FEATURE_DISTANCE keep their
    painted colour — eyes, gems, pupils, the small hand-placed marks that carry identity."""
    final = posterise(img, colours).convert("RGBA")
    src = img.convert("RGBA")
    a, b = src.load(), final.load()
    limit = FEATURE_DISTANCE * FEATURE_DISTANCE
    for y in range(src.height):
        for x in range(src.width):
            p, q = a[x, y], b[x, y]
            if p[3] and sum((p[i] - q[i]) ** 2 for i in range(3)) > limit:
                b[x, y] = p
    return final


def sheet_height(rows, minimum=0):
    return max(minimum, ((rows + 7) // 8) * 8)


def run(cid, tool, build, paint, anims, hitbox, *, resize=False, argv=None):
    """The whole `main()` of a creature generator: build, pack, paint, emit.

    `build()` returns the rig (and anything `paint`/`anims` need, as a tuple whose first item is
    the rig); `paint(rig, tex_h, *extra)` returns a `Skin`; `anims(*extra)` returns the clip dict.
    """
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args(argv)
    built = build()
    rig, extra = (built[0], built[1:]) if isinstance(built, tuple) else (built, ())
    rows = rig.pack()
    tex_h = sheet_height(rows)
    return emit(rig, paint(rig, tex_h, *extra), anims(*extra), cid=cid, tool=tool, hitbox=hitbox,
                tex_h=tex_h, force=args.force, resize=resize)
