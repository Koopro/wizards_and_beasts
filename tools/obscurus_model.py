#!/usr/bin/env python3
"""The Obscurus: rig, smoke skin, ember glowmask and animations.

`data/creatures/obscurus.json` still carries the tell — "PLACEHOLDER box rig — swap with a
Blockbench model matching these bone/texture/anim slots". It was two cubes, a `body` and a
`head`, on a 64x24 sheet, and `animation.obscurus.fly` was an **empty clip**: a `bones`
object with nothing in it, so the flying half of a flying creature's animation did nothing
at all.

Canon is the Fantastic Beasts obscurus: not a creature with a body but a churning mass of
black smoke — a dense turbulent heart, shells of vapour boiling around it at different
radii, lashing tendrils, and hot cracks of light buried inside. So this rig has no skeleton.
It has a core, three counter-rotating shrouds, a veil, and six whipping tendrils, and the
animation's whole job is to make those layers disagree with each other.

Three things drive the construction:

  - **The skin is painted LIGHT on purpose.** `Tint` (already in the creature JSON, colour
    `#3A2E55`) is multiplied into the render colour by `ScaledBeastRenderer.getRenderColor`.
    Painting the smoke near-black — the obvious choice — multiplies down to a silhouette
    with no readable interior. Mid-grey violet paint lands as deep violet-black in game.
    Offline previews therefore look far too pale; render with `--tint` to see the truth.
  - **Alpha holes make the smoke.** The renderer's cutout render type discards texels below
    the alpha threshold, so punching holes in the shroud plates turns solid boxes into
    ragged vapour for free. The core stays opaque so the thing still has a silhouette.
  - **Cube rotation places the plates, bone rotation animates them.** Each shroud is one
    bone holding plates fanned by static cube yaw. That way a whole shell spins as a rigid
    shell — which is what reads as roiling — instead of needing a bone per plate.

Run from the repo root:  python tools/obscurus_model.py [--force]
"""

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces, gradient, mottle  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/obscurus.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/obscurus.animation.json"
SKIN = f"{ASSETS}/textures/entity/obscurus.png"
GLOW = f"{ASSETS}/textures/entity/obscurus_glowmask.png"
# The player's obscurial form draws from the same palette so the mob and the transformed
# player read as one phenomenon. `FormRegistry` already names this path and already flags
# the form TRANSLUCENT; the file simply never existed, so the form would have rendered a
# missing-texture checker the moment anything drew it.
FORM_SKIN = f"{ASSETS}/textures/entity/form/obscurial_dark.png"
MARKER = marker("obscurus_model.py")

# Pre-tint paint. Everything here is multiplied by #3A2E55 (0.23, 0.18, 0.33) at render, so
# these read roughly three to five times darker in game than they look in the atlas.
SMOKE = hx("#A29CB6")
SMOKE_LT = hx("#D2CCE4")
SMOKE_DK = hx("#6E6880")
SMOKE_XDK = hx("#443F52")
HOLE = (0, 0, 0, 0)          # punched through: cutout alpha is what makes vapour ragged
# The light trapped inside. Painted hot enough to survive the tint multiply.
# Cold and violet, not firelight — an obscurus is not burning.
EMBER = hx("#DCD0FF")
EMBER_HOT = hx("#FFFFFF")
EMBER_DIM = hx("#9B7ACC")
EYE = hx("#FFFFFF")
# The player form's own range. Nothing multiplies this texture at render, so it carries its
# darkness directly — see `paint_form`.
FORM_DK = hx("#0C0A12")
FORM_LT = hx("#4C4460")

ROLES = {}


def cube(key, role, origin, size, rot=None, pivot=None):
    ROLES[key] = role
    return (key, origin, size, rot, pivot)


# --------------------------------------------------------------------------- rig

# Hitbox is 0.95 x 1.25 blocks = 15.2 x 20 units, so the mass is built around y = 10 and
# the tendrils are allowed to reach outside it — smoke has no hard edge.
CENTRE = (0, 10, 0)


def jitter(seed, i, spread):
    """Deterministic -1..1 wobble. Same rig every run, different every plate."""
    n = (i * 2654435761 + seed * 40503) & 0xFFFFFFFF
    return ((n >> 8) % 2000 / 1000.0 - 1.0) * spread


def shroud(name, pivot_y, radius, plate, yaws, y0, embers=(), seed=1, vary=True):
    """One shell of vapour: plates fanned around the axis by static cube yaw.

    Fanning with cube rotation rather than one bone per plate is deliberate — the shell has
    to turn as a rigid unit for the layers to visibly slide past each other, and per-plate
    bones would let them drift apart into confetti.

    Every plate is jittered off the nominal radius/height/width. Identical plates on a
    perfect ring built a drum with vertical staves; the whole point of a cloud is that no
    two parts of it agree.
    """
    w, h, d = plate
    pivot = (0, pivot_y, 0)
    cubes = []
    for j, yaw in enumerate(yaws):
        r = radius + (jitter(seed, j, 1.6) if vary else 0)
        ph = h + (jitter(seed, j + 31, 2.6) if vary else 0)
        pw = w + (jitter(seed, j + 61, 0.7) if vary else 0)
        py = y0 + (jitter(seed, j + 97, 1.8) if vary else 0)
        cubes.append(cube(f"{name}.p{j}", "smoke", (-r - pw, py, -d / 2.0),
                          (pw, ph, d), (0, yaw, 0), pivot))
    for j, (yaw, ey, er) in enumerate(embers):
        cubes.append(cube(f"{name}.ember{j}", "ember", (-er - 0.9, ey, -0.45),
                          (0.9, 1.1, 0.9), (0, yaw, 0), pivot))
    return (name, "body", pivot, None, cubes)


def build_bones():
    B = [
        ("root", None, (0, 0, 0), None, []),
        # `body` and `head` keep the names the placeholder used, so anything that ever looks
        # a bone up by those names still finds one.
        ("body", "root", CENTRE, None, [
            cube("body.core", "smoke", (-4.0, 5.5, -3.6), (8.0, 8.5, 7.2)),
            cube("body.lobe_l", "smoke", (-5.2, 7.5, -2.6), (6.4, 6.0, 5.4)),
            cube("body.lobe_r", "smoke", (-1.0, 6.8, -4.6), (5.6, 6.6, 6.0)),
            cube("body.under", "smoke", (-3.4, 3.2, -2.8), (6.0, 4.0, 5.2)),
            cube("body.upper", "smoke", (-2.6, 12.4, -3.0), (5.6, 4.4, 5.0)),
            # Cracks of light, and only on the *outside* of the core. An emissive cube
            # buried in the middle of the mass is wasted: AutoGlowingGeoLayer redraws the
            # same geometry at the same depth, so anything the core occludes is occluded in
            # the glow pass too. Kept to slivers — a first pass at full cube size read as a
            # glowing wound rather than light leaking out of something.
            cube("body.crack_a", "ember", (-4.3, 9.2, -1.2), (0.7, 3.2, 2.2)),
            cube("body.crack_b", "ember", (3.6, 7.4, -0.6), (0.7, 2.4, 1.8)),
            cube("body.crack_c", "ember", (-1.4, 5.4, -4.1), (2.8, 1.8, 0.7)),
        ]),
        # The face rides proud of the shell on purpose. Sunk level with the core it was
        # completely hidden behind the shroud plates, and an obscurus that never shows you
        # anything looking back is just weather.
        ("head", "body", (0, 15.0, 0), None, [
            cube("head.mass", "smoke", (-3.2, 13.6, -5.4), (6.4, 5.2, 6.4)),
            cube("head.brow", "smoke", (-3.6, 17.4, -5.0), (7.2, 2.0, 5.2)),
            cube("head.eye_l", "eye", (-2.9, 15.4, -7.6), (2.3, 1.3, 0.9)),
            cube("head.eye_r", "eye", (0.6, 15.4, -7.6), (2.3, 1.3, 0.9)),
        ]),
    ]

    # Three shells at different radii and heights. Radii stay tight — the first build ran
    # them out to 7.5 and the thing read as a ball of debris rather than the tall churning
    # column it is on screen, and it swallowed the tendrils whole.
    B.append(shroud("shroud_a", 10, 4.4, (3.2, 8.0, 4.0),
                    (0, 62, 124, 186, 248, 310), 6.0, seed=3,
                    embers=((30, 9.5, 5.0),)))
    B.append(shroud("shroud_b", 10, 5.8, (2.8, 6.5, 3.6),
                    (30, 92, 154, 216, 278, 340), 8.5, seed=11,
                    embers=((290, 8.0, 6.2),)))
    B.append(shroud("shroud_c", 7, 4.8, (2.4, 5.0, 3.2),
                    (15, 75, 135, 195, 255, 315), 2.5, seed=19,
                    embers=((160, 4.0, 5.2),)))
    # The plume: what the mass is shedding upward. This is the obscurus silhouette from the
    # films — the body is almost incidental under a column of smoke that keeps rising — and
    # without it the rig is a ball, however well the ball churns.
    # Rings overlap in Y by design: spaced apart they read as separate chimneys rather than
    # one column that keeps going up.
    B.append(shroud("veil", 15, 3.4, (2.6, 6.0, 3.0), (30, 102, 174, 246, 318), 14.5, seed=29,
                    ))
    B.append(shroud("plume", 15, 2.6, (2.2, 5.5, 2.6), (0, 72, 144, 216, 288), 18.5, seed=37))
    B.append(shroud("crown", 15, 1.8, (1.9, 4.5, 2.2), (40, 130, 220, 310), 22.0, seed=43))

    # Six tendrils, each a three-link chain that tapers as it goes.
    #
    # Authored along **-Z**, not -X, and that is load-bearing. Bedrock composes X, then Y,
    # then Z, so on a bone lying along -X the pitch value rotates the tendril about its own
    # length and does nothing at all — the first build aimed six tendrils with a pitch
    # column that was silently inert, and they all came out horizontal. Along -Z the X
    # rotation elevates and the Y rotation then swings that elevation around the compass,
    # which is the spherical aim actually wanted, out of one bone.
    #
    # (elevation, compass yaw). **Positive elevation points DOWN** — authored along -Z, a
    # positive X rotation drops the far end. Half the first set was positive and the rig
    # grew four legs and read as a spider. Nearly everything is negative now, so the wisps
    # sweep up and outward into the plume, with one shallow trailing one.
    TENDRILS = ((-40, 25), (-18, -60), (-8, 115),
                (12, -145), (-30, 70), (-4, -105))
    # Taper hard and curl hard. A wisp that ends at the thickness it started, running
    # straight, is a rod — six of those radiating off a ball is a sea mine. Short segments
    # with a big bend at each joint arc back toward the mass instead.
    SEGS = ((5.0, 2.0, 0.0, None),
            (4.0, 1.2, -5.0, (28, -22, 0)),
            (3.0, 0.6, -9.0, (38, 34, 0)))
    for i, (elev, yaw) in enumerate(TENDRILS):
        for j, (length, thick, z0, bend) in enumerate(SEGS):
            name = f"tendril{i}_{j}"
            parent = "body" if j == 0 else f"tendril{i}_{j - 1}"
            # Segment 0 aims the whole chain; the rest carry a static bend so a tendril
            # curves at rest instead of reading as a straight spike.
            rot = (elev, yaw, 0) if j == 0 else bend
            y = 10.0
            B.append((name, parent, (0, y, z0), rot, [
                cube(f"{name}.seg", "smoke",
                     (-thick / 2.0, y - thick / 2.0, z0 - length), (thick, thick, length))]))
    return B


def build_geo(packer, bones, tex):
    packer.place_sorted([(key, size) for b in bones for key, _, size, _, _ in b[4]])
    out = []
    for name, parent, pivot, rot, cubes in bones:
        b = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rot and any(rot):
            b["rotation"] = [round(v, 2) for v in rot]
        if cubes:
            cs = []
            for key, origin, size, crot, cpivot in cubes:
                c = {"origin": [round(v, 2) for v in origin],
                     "size": [round(v, 2) for v in size],
                     "uv": list(packer.placed[key][0])}
                if crot and any(crot):
                    c["rotation"] = [round(v, 2) for v in crot]
                    c["pivot"] = [round(v, 2) for v in (cpivot or origin)]
                cs.append(c)
            b["cubes"] = cs
        out.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.obscurus",
                "texture_width": tex, "texture_height": tex,
                "visible_bounds_width": 3.5, "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 0.9, 0],
            },
            "bones": out,
        }],
    }


# --------------------------------------------------------------------------- skin

def erode(d, rect, seed, density, edge_only=True):
    """Punch alpha holes so a box face reads as vapour rather than a slab.

    Weighted toward the rim: a face eaten evenly all over just looks like a dirty texture,
    whereas a face whose edges have dissolved reads as something with no boundary.
    """
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            # Distance from the nearest edge of this face, in pixels.
            edge = min(x - x0, x0 + w - 1 - x, y - y0, y0 + h - 1 - y)
            chance = density if not edge_only else density - edge * (density // 3 + 4)
            if chance <= 0:
                continue
            n = (x * 2654435761 + y * 2246822519 + seed * 3266489917) & 0xFFFFFFFF
            if (n >> 9) % 100 < chance:
                d.point((x, y), fill=HOLE)


def paint(packer, tex):
    img = Image.new("RGBA", (tex, tex), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tex, tex), (0, 0, 0, 0))
    d, gd = ImageDraw.Draw(img), ImageDraw.Draw(glow)

    smoke_pal = [SMOKE_LT, SMOKE_DK, SMOKE_XDK]

    for key, (uv, size) in packer.placed.items():
        role = ROLES[key]
        f = faces(uv, size)
        seed = sum(ord(c) for c in key)

        if role == "smoke":
            outer = not key.startswith("body.")
            for fname, rect in f.items():
                col = shade(SMOKE, 1.25) if fname == "top" else \
                    shade(SMOKE, 0.6) if fname == "bottom" else SMOKE
                mottle(d, rect, col, smoke_pal, 40, seed)
                # Vertical streaking: smoke has a direction of travel, and the streaks are
                # what stop the mottle reading as static noise.
                x0, y0, w, h = rect
                for sx in range(x0, x0 + w):
                    if (sx * 7919 + seed) % 3 == 0:
                        gradient(d, (sx, y0, 1, h), SMOKE_LT, SMOKE_XDK, mix)
                if outer:
                    # Only the shells and tendrils dissolve; the core keeps its silhouette.
                    # Thin parts get eaten far more gently — the same density that reads as
                    # vapour on a 4px-wide plate turns a 1px tendril into a dashed line.
                    thin = min(rect[2], rect[3]) <= 4
                    erode(d, rect, seed, 26 if thin else 62)

        elif role == "ember":
            for fname, rect in f.items():
                mottle(d, rect, EMBER, [EMBER_HOT, EMBER_DIM], 45, seed)
                mottle(gd, rect, EMBER, [EMBER_HOT, EMBER_DIM], 45, seed)
                # Ragged edges so an ember is a crack of light, not a lit brick.
                erode(d, rect, seed + 7, 55)
                erode(gd, rect, seed + 7, 55)

        elif role == "eye":
            for rect in f.values():
                mottle(d, rect, EYE, [EMBER, EMBER_DIM], 20, seed)
                mottle(gd, rect, EYE, [EMBER, EMBER_DIM], 20, seed)

    return img, glow


def paint_form(size=64):
    """A tiling smoke field for the player's obscurial form.

    `ObscurialDarkModel` is a vanilla model whose every box uses `texOffs(0, 0)`, so each
    face samples the same corner of the sheet rather than an island of its own. That is
    fine — and much simpler than UV bookkeeping across a hand-written Java model — provided
    the sheet is uniform enough that *any* rectangle cut from it still reads as smoke. So
    this is a field, not a layout: turbulent noise, soft alpha, a few cold flecks.

    Partial alpha is safe here where it is not on the mob: the form is flagged TRANSLUCENT
    in `FormRegistry`, so it draws through `entityTranslucent` rather than a cutout.
    """
    def lattice(gx, gy, salt):
        n = ((gx & 255) * 374761393 + (gy & 255) * 668265263 + salt * 1274126177) & 0xFFFFFFFF
        return ((n >> 11) % 1024) / 1023.0

    def octave(x, y, step, salt):
        """Value noise with smoothstep interpolation, tiling every `size`.

        The first attempt sampled the lattice with integer division and no interpolation,
        which is not noise at all — it is a grid of flat cells, and it printed the sheet as
        horizontal bands. Interpolating between lattice points is the whole difference
        between billows and corduroy.
        """
        fx, fy = x / step, y / step
        gx, gy = int(fx), int(fy)
        tx, ty = fx - gx, fy - gy
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        c00 = lattice(gx, gy, salt)
        c10 = lattice(gx + 1, gy, salt)
        c01 = lattice(gx, gy + 1, salt)
        c11 = lattice(gx + 1, gy + 1, salt)
        return (c00 * (1 - tx) + c10 * tx) * (1 - ty) + (c01 * (1 - tx) + c11 * tx) * ty

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            # Three octaves: billows, curl, grain.
            v = (0.55 * octave(x, y, 16.0, 3)
                 + 0.30 * octave(x, y, 6.0, 17)
                 + 0.15 * octave(x, y, 2.5, 41))
            # Dark on its own terms. The mob's skin is painted pale because `Tint` multiplies
            # it down at render; nothing multiplies this one, so reusing those values gave a
            # pale grey cloud where the form is supposed to be the *dark* form.
            # Stepped, not airbrushed (texture audit 2026-09-27): five colour steps and four
            # alpha levels keep the billows and the holes but read as pixel smoke.
            t = round(min(1.0, max(0.0, v * 1.15)) * 4) / 4.0
            base = mix(FORM_DK, FORM_LT, t)
            # Alpha follows the same field, so the thinnest parts of the cloud are also its
            # darkest — which is what stops it reading as a decal — and the bottom of the
            # range is punched right out so the form has genuine holes in it.
            alpha = 0 if v < 0.34 else min((90, 150, 200, 235), key=lambda a: abs(a - (v - 0.34) * 430))
            px[x, y] = (base[0], base[1], base[2], alpha)

    d = ImageDraw.Draw(img)
    for i in range(10):
        n = (i * 2654435761) & 0xFFFFFFFF
        ex, ey = (n >> 5) % size, (n >> 15) % size
        d.point((ex, ey), fill=(EMBER[0], EMBER[1], EMBER[2], 235))
        d.point(((ex + 1) % size, ey), fill=(EMBER_DIM[0], EMBER_DIM[1], EMBER_DIM[2], 200))
    return img


# --------------------------------------------------------------------------- animation

def kf(*pairs):
    return {str(t): list(v) for t, v in pairs}


def spin(clip, bone, period, degrees, phase=0.0, wobble=0.0):
    """A full revolution about Y over `period`, in four keys so it never eases to a stop.

    Three keys would let the interpolator take the short way round and reverse direction at
    the wrap; quarter turns keep the sign unambiguous.
    """
    step = degrees / 4.0
    clip["bones"][bone] = {"rotation": kf(
        (0.0, (phase, 0, 0)),
        (period * 0.25, (phase + wobble, step, 0)),
        (period * 0.5, (phase, step * 2, 0)),
        (period * 0.75, (phase - wobble, step * 3, 0)),
        (period, (phase, degrees, 0)))}


def tendril_track(clip, i, period, amp, lag):
    """Whip: each segment repeats its parent's swing a beat later, growing as it goes."""
    for j in range(3):
        t0 = lag * j
        a = amp * (0.6 + 0.45 * j)
        clip["bones"][f"tendril{i}_{j}"] = {"rotation": kf(
            (0.0, (0, 0, 0)),
            (round(period * 0.25 + t0, 2), (a * 0.5, a, 0)),
            (round(period * 0.5 + t0, 2), (0, 0, 0)),
            (round(period * 0.75 + t0, 2), (-a * 0.5, -a, 0)),
            (period, (0, 0, 0)))}


def build_anim():
    """`idle` and `fly` — the only two clips `GenericFlyingBeastEntity` plays.

    Both are built the same way and differ only in violence: shells counter-rotating at
    different rates so no two layers ever agree, a core that breathes, and tendrils that
    lash a beat behind whatever is dragging them.
    """
    idle = {"loop": True, "animation_length": 6.0, "bones": {}}
    spin(idle, "shroud_a", 6.0, 360, wobble=3)
    spin(idle, "shroud_b", 6.0, -360, wobble=-4)
    spin(idle, "shroud_c", 6.0, 240, wobble=5)
    spin(idle, "veil", 6.0, -200, wobble=6)
    idle["bones"]["body"] = {
        "rotation": kf((0.0, (0, 0, 0)), (2.0, (3, 12, -2)),
                       (4.0, (-3, -10, 2)), (6.0, (0, 0, 0))),
        # Scale is the whole "breathing mass" read — a smoke cloud with a fixed volume
        # looks like a rock.
        "scale": kf((0.0, (1.0, 1.0, 1.0)), (1.5, (1.06, 0.95, 1.06)),
                    (3.0, (0.96, 1.07, 0.96)), (4.5, (1.04, 0.98, 1.04)),
                    (6.0, (1.0, 1.0, 1.0))),
    }
    idle["bones"]["head"] = {
        "rotation": kf((0.0, (0, 0, 0)), (1.7, (-4, -14, 3)),
                       (3.6, (5, 16, -3)), (6.0, (0, 0, 0)))}
    idle["bones"]["root"] = {
        "position": kf((0.0, (0, 0, 0)), (3.0, (0, 1.2, 0)), (6.0, (0, 0, 0)))}
    for i in range(6):
        tendril_track(idle, i, 6.0, 14, 0.22)

    fly = {"loop": True, "animation_length": 1.6, "bones": {}}
    spin(fly, "shroud_a", 1.6, 360, wobble=8)
    spin(fly, "shroud_b", 1.6, -400, wobble=-10)
    spin(fly, "shroud_c", 1.6, 300, wobble=12)
    spin(fly, "veil", 1.6, -280, wobble=14)
    fly["bones"]["body"] = {
        # Pitched into the direction of travel and stretched along it: the mass elongates
        # when it moves and gathers back up when it stops.
        "rotation": kf((0.0, (14, 0, 0)), (0.8, (18, 6, -4)), (1.6, (14, 0, 0))),
        "scale": kf((0.0, (0.9, 1.15, 0.9)), (0.8, (0.86, 1.22, 0.86)),
                    (1.6, (0.9, 1.15, 0.9))),
    }
    fly["bones"]["head"] = {
        "rotation": kf((0.0, (-8, 0, 0)), (0.8, (-12, -8, 0)), (1.6, (-8, 0, 0)))}
    fly["bones"]["root"] = {
        "position": kf((0.0, (0, 0, 0)), (0.4, (0, 0.8, 0)),
                       (1.2, (0, -0.6, 0)), (1.6, (0, 0, 0)))}
    for i in range(6):
        tendril_track(fly, i, 1.6, 34, 0.09)

    return {"format_version": "1.8.0",
            "animations": {"animation.obscurus.idle": idle,
                           "animation.obscurus.fly": fly}}


# --------------------------------------------------------------------------- driver

def pack(tex):
    ROLES.clear()
    bones = build_bones()
    packer = Packer(tex)
    geo = build_geo(packer, bones, tex)
    return bones, packer, geo, packer.height_used()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    # The shipped rig has been post-processed since this generator last wrote it (`add_reactions.py`
    # added hit/death/idle clips, later fixes touched the geo). A full run discards that, so run
    # `add_reactions.py` after it — or use --form-only to repaint just the player-form smoke.
    ap.add_argument("--form-only", action="store_true")
    args = ap.parse_args()

    if args.form_only:
        save(paint_form(), FORM_SKIN, MARKER)
        print("wrote", FORM_SKIN)
        return 0

    for tex in (64, 128, 256):
        bones, packer, geo, used = pack(tex)
        if used <= tex:
            break
    else:
        print(f"ERROR: UV islands need {used}px, more than a 256px sheet", file=sys.stderr)
        return 1

    if not args.force and not is_regenerable(SKIN, MARKER):
        print("obscurus.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    skin, glow = paint(packer, tex)
    for path, doc in ((GEO, geo), (ANIM, build_anim())):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=2)
            f.write("\n")
    # Posterising the smoke would flatten exactly the tonal range the tint then compresses
    # further, so this one keeps a wider palette than the other rigs.
    save(posterise(skin, 28), SKIN, MARKER)
    save(glow, GLOW, MARKER)
    if args.force or is_regenerable(FORM_SKIN, MARKER):
        # Not posterised: the form is translucent, and median-cut collapses the alpha ramp with
        # the palette. `paint_form` steps colour and alpha itself instead.
        save(paint_form(), FORM_SKIN, MARKER)

    cubes = sum(len(b[4]) for b in bones)
    print(f"obscurus rig: {len(bones)} bones, {cubes} cubes, "
          f"UV {used}/{tex}px used, skin {tex}x{tex}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
