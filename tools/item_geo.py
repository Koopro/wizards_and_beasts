#!/usr/bin/env python3
"""GeckoLib models for held items, for Wizards & Beasts.

An `AnimatedItem` (Java) draws its model in hand and its flat icon in slots. This writes the
model half: `geckolib/models/item/<id>.geo.json`, `geckolib/animations/item/<id>.animation.json`
and `textures/item/model/<id>.png` -- the texture lives apart because `textures/item/<id>.png` is
the icon `tools/item_sprites.py` owns.

The mechanics are `rigkit`'s (whole-number cubes, one packed box-UV island per cube, keyframes as
deltas from the rest pose) and the colours are `item_sprites`' ramps, so a model and its icon are
the same object in the same paint. Each item is one `build_<id>()` returning (rig, skin, clips).

Frame: x and z centred on 0, y up from 0, 16 units to a block. GeckoLib draws that origin at the
*centre* of the item cube -- block-model (8, 8.16, 8), x mirrored -- not at its floor (vanilla's
ItemTransform ends in translate(-0.5), GeoItemRenderer then translates (0.5, 0.51, 0.5)). So the
special model's `base` is written here too, `models/item/<id>_held.json`, transforms only,
computed from the geo's own bounds by `item_display.py`; a borrowed flat or cuboid base put every
geo item 8 units high. The wand's base (`models/item/wand.json`) is written the same way.

Run from the repo root:
    python tools/item_geo.py [--only a,b] [--force] [--preview DIR]
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402
from rigkit import Rig, Skin, clip, kf, sheet_height  # noqa: E402
import item_sprites  # noqa: E402
import item_display  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "item")
ANIM_DIR = os.path.join(ASSETS, "geckolib", "animations", "item")
TEX_DIR = os.path.join(ASSETS, "textures", "item", "model")
MARKER = marker("item_geo.py")

# The item models were painted and approved (2026-09-23/24) with rigkit's original speckle dither.
# rigkit's default became `strand` on 2026-09-27; pinning the legacy grain here, as the protected
# creature redesigns do, keeps a rerun byte-for-byte equal to the shipped textures.
GRAIN = "speckle"


def tones(hex_colour):
    """The icon engine's six-tone ramp as RGBA, keyed outline/dark/shade/base/light/spec."""
    return {k: v + (255,) for k, v in item_sprites.ramp(hex_colour).items()}


def spin(length, axis, turns=1.0, *, steps=4, sign=1):
    """A full, evenly paced rotation about one axis -- keyed in quarters, because GeckoLib
    interpolates linearly between keys and a single 0 -> 360 key pair would not turn at all."""
    index = "xyz".index(axis)
    pairs = []
    for i in range(steps + 1):
        v = [0.0, 0.0, 0.0]
        v[index] = sign * 360.0 * turns * i / steps
        pairs.append((length * i / steps, tuple(v)))
    return {"rotation": kf(*pairs)}


def bob(length, amp, *, axis="y"):
    index = "xyz".index(axis)
    pairs = []
    for i in range(5):
        v = [0.0, 0.0, 0.0]
        v[index] = amp * (0, 1, 0, -1, 0)[i]
        pairs.append((length * i / 4, tuple(v)))
    return {"position": kf(*pairs)}


# --------------------------------------------------------------------------- models


def build_time_turner():
    """An hourglass slung inside two gold rings that turn on different axes, on its chain."""
    rig = Rig("time_turner", tex_w=32)
    rig.bone("chain", "root", (0, 12, 0))
    rig.cube("chain", (-0.5, 12, -0.5), (1, 3, 1), key="chain")
    outer = rig.bone("outer_ring", "root", (0, 7, 0))
    rig.cube(outer, (-3.5, 10.5, -0.5), (7, 1, 1), key="outer_top")
    rig.cube(outer, (-3.5, 2.5, -0.5), (7, 1, 1), key="outer_bottom")
    rig.cube(outer, (-4.5, 3.5, -0.5), (1, 7, 1), key="outer_left")
    rig.cube(outer, (3.5, 3.5, -0.5), (1, 7, 1), key="outer_right")
    inner = rig.bone("inner_ring", "outer_ring", (0, 7, 0))
    rig.cube(inner, (-0.5, 9.5, -2.5), (1, 1, 5), key="inner_top")
    rig.cube(inner, (-0.5, 3.5, -2.5), (1, 1, 5), key="inner_bottom")
    rig.cube(inner, (-0.5, 4.5, -3.5), (1, 5, 1), key="inner_front")
    rig.cube(inner, (-0.5, 4.5, 2.5), (1, 5, 1), key="inner_back")
    glass = rig.bone("hourglass", "root", (0, 7, 0))
    rig.cube(glass, (-1.5, 9, -1.5), (3, 1, 3), key="cap_top")
    rig.cube(glass, (-1, 7, -1), (2, 2, 2), key="bulb_top")
    rig.cube(glass, (-1, 5, -1), (2, 2, 2), key="bulb_bottom")
    rig.cube(glass, (-1.5, 4, -1.5), (3, 1, 3), key="cap_bottom")

    skin = Skin(rig, sheet_height(rig.pack()), grain=GRAIN)
    gold = tones("#E6B43A")
    skin.skin(rig.keys("outer", "inner"), gold["base"], bevel=0.82, dither=0.05,
              dither_colour=gold["light"])
    skin.skin(["cap_top", "cap_bottom"], gold["shade"], bevel=0.8, dither=0)
    skin.skin(["chain"], gold["shade"], bevel=0.8, dither=0.2, dither_colour=gold["dark"])
    glass_t = tones("#BFDCE4")
    sand = tones("#E8C870")
    skin.skin(["bulb_top"], glass_t["light"], bevel=0.9, dither=0)
    skin.skin(["bulb_bottom"], sand["base"], top=glass_t["light"], bevel=0.88, dither=0.1,
              dither_colour=sand["light"])

    clips = {
        "idle": clip(6.0, {
            # Outer ring end over end, inner ring round the hourglass: each sweeps a volume
            # that clears everything inside it, so nothing passes through the glass.
            "outer_ring": spin(6.0, "x"),
            "inner_ring": spin(6.0, "y", turns=2, steps=8, sign=-1),
            "hourglass": bob(3.0, 0.25),
        }),
    }
    return rig, skin, clips


# --------------------------------------------------------------------------- shared shapes


def orb(rig, bone, centre, r, key, squash=(1, 1, 1)):
    """A rough sphere: three overlapping boxes, each one texel short on two axes. Reads round at
    item scale and costs three islands, not a voxel ball's dozens.

    Overlapping boxes share face planes (A's top is C's top), and coplanar faces z-fight -- a
    shimmering hatch across the sphere in game. Each later box is pulled in by a hair more, so
    no two faces ever sit in the same plane."""
    cx, cy, cz = centre
    d = 2 * r
    sx, sy, sz = (max(1, int(d * s)) for s in squash)
    for i, (w, h, l) in enumerate(((sx, sy - 2, sz - 2), (sx - 2, sy, sz - 2), (sx - 2, sy - 2, sz))):
        rig.cube(bone, (cx - w / 2, cy - h / 2, cz - l / 2), (w, h, l), key=f"{key}{i}",
                 inflate=-0.02 * i or None)
    return [f"{key}{i}" for i in range(3)]


def ring_xy(rig, bone, centre, half, key, thick=1):
    """A square ring standing in the XY plane, corners notched so it reads round."""
    cx, cy, cz = centre
    span = 2 * half - 2 * thick
    rig.cube(bone, (cx - half + thick, cy + half - thick, cz - thick / 2), (span, thick, thick), key=key + "_t")
    rig.cube(bone, (cx - half + thick, cy - half, cz - thick / 2), (span, thick, thick), key=key + "_b")
    rig.cube(bone, (cx - half, cy - half + thick, cz - thick / 2), (thick, span, thick), key=key + "_l")
    rig.cube(bone, (cx + half - thick, cy - half + thick, cz - thick / 2), (thick, span, thick), key=key + "_r")
    return [key + s for s in ("_t", "_b", "_l", "_r")]


def ring_xz(rig, bone, centre, half, key, thick=1, height=1):
    """The same ring lying flat."""
    cx, cy, cz = centre
    span = 2 * half - 2 * thick
    rig.cube(bone, (cx - half + thick, cy, cz - half), (span, height, thick), key=key + "_n")
    rig.cube(bone, (cx - half + thick, cy, cz + half - thick), (span, height, thick), key=key + "_s")
    rig.cube(bone, (cx - half, cy, cz - half + thick), (thick, height, span), key=key + "_w")
    rig.cube(bone, (cx + half - thick, cy, cz - half + thick), (thick, height, span), key=key + "_e")
    return [key + s for s in ("_n", "_s", "_w", "_e")]


def paint(skin, keys, hex_colour, *, tone="base", bevel=0.84, dither=0.06, speck="light", top=None):
    t = tones(hex_colour)
    skin.skin(keys, t[tone], bevel=bevel, dither=dither, dither_colour=t[speck],
              top=tones(top)["base"] if top else None)
    return t


def osc(length, amp, axis="x", phase=0.0, kind="rotation"):
    """A sine sway in quarters, as a delta track."""
    import math
    index = "xyz".index(axis)
    pairs = []
    for i in range(9):
        v = [0.0, 0.0, 0.0]
        v[index] = amp * math.sin(2 * math.pi * (i / 8 + phase))
        pairs.append((length * i / 8, tuple(v)))
    return {kind: kf(*pairs)}


def merge(*tracks):
    out = {}
    for t in tracks:
        out.update(t)
    return out


def finish(rig):
    return Skin(rig, sheet_height(rig.pack()), grain=GRAIN)


# --------------------------------------------------------------------------- artifacts


def build_sneakoscope():
    """A glass spinning top on a brass point; it turns, and wobbles as it turns."""
    rig = Rig("sneakoscope", tex_w=32)
    rig.bone("wobble", "root", (0, 3, 0))
    rig.bone("top", "wobble", (0, 3, 0))
    rig.cube("top", (-0.5, 3, -0.5), (1, 1, 1), key="tip")
    rig.cube("top", (-1.5, 4, -1.5), (3, 1, 3), key="g1")
    rig.cube("top", (-2.5, 5, -2.5), (5, 1, 5), key="g2")
    rig.cube("top", (-3.5, 6, -3.5), (7, 2, 7), key="g3")
    rig.cube("top", (-3.5, 8, -3.5), (7, 1, 7), key="band", inflate=0.1)
    rig.cube("top", (-2.5, 9, -2.5), (5, 1, 5), key="g4")
    rig.cube("top", (-0.5, 10, -0.5), (1, 3, 1), key="spindle")
    skin = finish(rig)
    paint(skin, ["g1", "g2", "g3", "g4"], "#C8E0E8", bevel=0.9, dither=0.04, speck="spec")
    paint(skin, ["tip", "band", "spindle"], "#B8733A")
    # One clip per SneakoscopeTier -- SneakoscopeItem.clipFor picks by the stack's own reading.
    # Calm is genuinely still: a top that idles with a decorative turn would be lying.
    return rig, skin, {
        "idle": clip(1.0, {"top": {"rotation": kf((0.0, (0, 0, 0)))}}),
        "uneasy": clip(4.0, {"top": spin(4.0, "y", steps=8),
                             "wobble": merge(osc(4.0, 3, "x"), osc(4.0, 3, "z", phase=0.25))}),
        "spin": clip(1.0, {"top": spin(1.0, "y", steps=8),
                           "wobble": merge(osc(1.0, 5, "x"), osc(1.0, 5, "z", phase=0.25))}),
        "shriek": clip(0.4, {"top": spin(0.4, "y", steps=8),
                             "wobble": merge(osc(0.4, 9, "x"), osc(0.4, 9, "z", phase=0.25))}),
    }


def build_remembrall():
    """A glass ball full of white smoke and a red thread of it, on a brass stand."""
    rig = Rig("remembrall", tex_w=32)
    rig.bone("stand", "root", (0, 2, 0))
    rig.cube("stand", (-2.5, 2, -2.5), (5, 1, 5), key="foot")
    rig.cube("stand", (-2, 3, -2), (4, 1, 4), key="collar")
    rig.bone("ball", "root", (0, 7.5, 0))
    ball = orb(rig, "ball", (0, 7.5, 0), 3, "ball")
    skin = finish(rig)
    paint(skin, ["foot", "collar"], "#B8903A")
    paint(skin, ball, "#EEE8EC", bevel=0.92, dither=0.2, speck="shade")
    # The smoke has turned: red rising from the bottom of the glass, thinning as it climbs.
    red = tones("#C8303A")
    for key in ball:
        for face in skin.SIDES:
            x, y, w, h = skin.face(key, face)
            for row in range(h):
                depth = (row + 1) / h
                for col in range(w):
                    if depth > 0.55 or (depth > 0.3 and (col + row) % 3 == 0):
                        skin.d.point((x + col, y + row), fill=red["base" if depth > 0.75 else "light"])
        x, y, w, h = skin.face(key, "bottom")
        skin.rect((x, y, w, h), red["shade"])
    return rig, skin, {"idle": clip(4.0, {
        "ball": merge(spin(4.0, "y", steps=8), osc(4.0, 0.3, "y", kind="position")),
    })}


def build_omnioculars():
    """Brass binoculars; the focus wheel turns and the lenses rack in and out."""
    rig = Rig("omnioculars", tex_w=32)
    rig.bone("body", "root", (0, 8, 0))
    rig.cube("body", (-4.5, 7, -2), (3, 3, 5), key="barrel_l")
    rig.cube("body", (1.5, 7, -2), (3, 3, 5), key="barrel_r")
    rig.cube("body", (-1.5, 8, -0.5), (3, 1, 2), key="bridge")
    rig.cube("body", (-4, 7.5, 3), (2, 2, 1), key="eye_l")
    rig.cube("body", (2, 7.5, 3), (2, 2, 1), key="eye_r")
    rig.bone("lenses", "body", (0, 8, -2))
    rig.cube("lenses", (-4.5, 7, -3), (3, 3, 1), key="lens_l")
    rig.cube("lenses", (1.5, 7, -3), (3, 3, 1), key="lens_r")
    rig.bone("wheel", "body", (0, 9.5, 0.5))
    rig.cube("wheel", (-1, 9, 0), (2, 1, 1), key="wheel", inflate=0.1)
    skin = finish(rig)
    paint(skin, ["barrel_l", "barrel_r", "bridge"], "#B8903A")
    paint(skin, ["eye_l", "eye_r"], "#4A3A2A")
    paint(skin, ["lens_l", "lens_r"], "#6A8AA8", speck="spec", top="#B8903A")
    paint(skin, ["wheel"], "#8A6A2A", dither=0.3)
    return rig, skin, {"idle": clip(3.0, {
        "wheel": spin(3.0, "x", steps=8),
        "lenses": osc(3.0, 0.4, "z", kind="position"),
    })}


def build_foe_glass():
    """A dark mirror in a wooden frame; two shadows drift in it, nearer or farther."""
    rig = Rig("foe_glass", tex_w=32)
    rig.bone("frame", "root", (0, 10, 0))
    frame = ring_xy(rig, "frame", (0, 10, 0), 5, "frame")
    rig.cube("frame", (-4, 6, -0.2), (8, 8, 1), key="glass")
    rig.bone("shadows", "frame", (0, 10, 0))
    rig.cube("shadows", (-3, 7.5, -0.6), (2, 4, 1), key="shade_a", inflate=-0.05)
    rig.cube("shadows", (1, 8, -0.6), (1, 3, 1), key="shade_b", inflate=-0.05)
    skin = finish(rig)
    paint(skin, frame, "#4A3222", dither=0.1)
    paint(skin, ["glass"], "#3A3A4A", dither=0.12, speck="shade")
    paint(skin, ["shade_a", "shade_b"], "#1A1620", dither=0.2, speck="shade")
    return rig, skin, {"idle": clip(5.0, {"shadows": osc(5.0, 1.2, "x", kind="position")})}


def build_mirror_of_erised():
    """The tall gold mirror on clawed feet, and a glint that crosses the glass."""
    rig = Rig("mirror_of_erised", tex_w=48)
    rig.bone("mirror", "root", (0, 8, 0))
    frame = ring_xy(rig, "mirror", (0, 8.5, 0), 5, "frame")
    rig.cube("mirror", (-5, 3.5, -0.5), (10, 1, 1), key="frame_base")
    rig.cube("mirror", (-2, 13.5, -0.5), (4, 1, 1), key="crest")
    rig.cube("mirror", (-4, 4.5, -0.2), (8, 8, 1), key="glass")
    rig.cube("mirror", (-4.5, 2, -1), (2, 2, 2), key="claw_l")
    rig.cube("mirror", (2.5, 2, -1), (2, 2, 2), key="claw_r")
    rig.bone("glint", "mirror", (0, 8, 0))
    rig.cube("glint", (-3, 9, -0.4), (1, 2, 1), key="glint", inflate=-0.1)
    skin = finish(rig)
    paint(skin, frame + ["frame_base", "crest", "claw_l", "claw_r"], "#C8983A")
    paint(skin, ["glass"], "#9AB0C8", dither=0.08, speck="light")
    paint(skin, ["glint"], "#FFFFFF", tone="spec", bevel=0, dither=0)
    return rig, skin, {"idle": clip(4.0, {"glint": {"position": kf(
        (0.0, (0, 0, 0)), (2.0, (5, -4, 0)), (2.01, (0, 0, 0)), (4.0, (0, 0, 0)))}})}


def build_philosophers_stone():
    """A blood-red stone cut to catch the light, turning slowly."""
    rig = Rig("philosophers_stone", tex_w=32)
    rig.bone("stone", "root", (0, 7, 0))
    rig.cube("stone", (-2, 5, -2), (4, 4, 4), key="core", rotation=(0, 45, 0), pivot=(0, 7, 0))
    rig.cube("stone", (-1.5, 9, -1.5), (3, 1, 3), key="crown", rotation=(0, 45, 0), pivot=(0, 7, 0))
    rig.cube("stone", (-1.5, 4, -1.5), (3, 1, 3), key="pavilion", rotation=(0, 45, 0), pivot=(0, 7, 0))
    rig.cube("stone", (-0.5, 3, -0.5), (1, 1, 1), key="culet", rotation=(0, 45, 0), pivot=(0, 7, 0))
    skin = finish(rig)
    paint(skin, ["core", "crown"], "#C0182A", speck="spec", dither=0.1)
    paint(skin, ["pavilion", "culet"], "#C0182A", tone="shade", speck="base")
    return rig, skin, {"idle": clip(5.0, {"stone": merge(spin(5.0, "y", steps=8),
                                                         osc(5.0, 0.4, "y", kind="position"))})}


def build_resurrection_stone():
    """The black stone with the Hallows sign cut into it."""
    rig = Rig("resurrection_stone", tex_w=32)
    rig.bone("stone", "root", (0, 7, 0))
    keys = orb(rig, "stone", (0, 7, 0), 2.5, "stone", squash=(1, 1.2, 0.8))
    skin = finish(rig)
    paint(skin, keys, "#2A2830", dither=0.1, speck="shade")
    silver = tones("#C8CCD4")["light"]
    for key in keys:
        for face in ("north", "south"):
            x, y, w, h = skin.face(key, face)
            if w >= 3 and h >= 3:
                cx = x + w // 2
                skin.d.line([(cx, y + 1), (cx, y + h - 2)], fill=silver)
                skin.d.line([(x + 1, y + h - 2), (x + w - 2, y + h - 2)], fill=silver)
    return rig, skin, {"idle": clip(6.0, {"stone": merge(spin(6.0, "y", steps=8),
                                                         osc(6.0, 0.3, "y", kind="position"))})}


def build_slytherins_locket():
    """The heavy gold locket with its emerald S, swaying on its chain."""
    rig = Rig("slytherins_locket", tex_w=32)
    rig.bone("chain", "root", (0, 12, 0))
    rig.cube("chain", (-0.5, 11, -0.5), (1, 2, 1), key="chain")
    rig.bone("locket", "chain", (0, 11, 0))
    rig.cube("locket", (-3, 4, -1), (6, 7, 2), key="case")
    rig.cube("locket", (-2, 5, -1.3), (4, 5, 1), key="face", inflate=-0.1)
    skin = finish(rig)
    paint(skin, ["chain"], "#E0B84A", tone="shade", dither=0.3, speck="dark")
    paint(skin, ["case"], "#D8A83A")
    paint(skin, ["face"], "#1E8A4A", speck="light", dither=0.12)
    gold = tones("#D8A83A")["light"]
    x, y, w, h = skin.face("face", "north")
    for i in range(h):
        skin.d.point((x + (w - 1 - (i % w) if (i // w) % 2 else i % w), y + i), fill=gold)
    return rig, skin, {"idle": clip(3.0, {"locket": osc(3.0, 9, "z")})}


def build_ravenclaws_diadem():
    """A silver circlet with a sapphire and spread eagle wings at the front."""
    rig = Rig("ravenclaws_diadem", tex_w=32)
    rig.bone("diadem", "root", (0, 6, 0))
    band = ring_xz(rig, "diadem", (0, 5, 0), 5, "band")
    rig.cube("diadem", (-4, 6, -5.2), (3, 2, 1), key="wing_l", rotation=(0, 0, 15), pivot=(-1, 6, -5))
    rig.cube("diadem", (1, 6, -5.2), (3, 2, 1), key="wing_r", rotation=(0, 0, -15), pivot=(1, 6, -5))
    rig.cube("diadem", (-1, 6, -5.4), (2, 3, 1), key="sapphire")
    rig.cube("diadem", (-0.5, 9, -5.2), (1, 1, 1), key="point")
    skin = finish(rig)
    paint(skin, band + ["wing_l", "wing_r", "point"], "#D8DCE4", speck="spec")
    paint(skin, ["sapphire"], "#2A5AB8", speck="spec", dither=0.15)
    return rig, skin, {"idle": clip(6.0, {"diadem": merge(spin(6.0, "y", steps=8),
                                                          osc(6.0, 0.3, "y", kind="position"))})}


def build_portkey():
    """An old boot -- what the Ministry uses -- turning as if about to go."""
    rig = Rig("portkey", tex_w=32)
    rig.bone("boot", "root", (0, 6, 0))
    rig.cube("boot", (-1.5, 2, -3.5), (3, 1, 7), key="sole")
    rig.cube("boot", (-1.5, 3, -3.5), (3, 2, 6), key="foot")
    rig.cube("boot", (-1.5, 5, 0), (3, 5, 3), key="shaft")
    rig.cube("boot", (-2, 10, -0.5), (4, 1, 4), key="cuff")
    skin = finish(rig)
    paint(skin, ["sole", "cuff"], "#3A2616")
    paint(skin, ["foot", "shaft"], "#6A4A2A", dither=0.14, speck="shade")
    return rig, skin, {"idle": clip(4.0, {"boot": merge(spin(4.0, "y", steps=8),
                                                        osc(2.0, 0.3, "y", kind="position"))})}


def build_pensieve():
    """A stone basin on a pedestal, silver memory in it, wisps circling the surface."""
    rig = Rig("pensieve", tex_w=64)
    rig.bone("basin", "root", (0, 0, 0))
    rig.cube("basin", (-3, 0.5, -3), (6, 1, 6), key="foot")
    rig.cube("basin", (-1.5, 1.5, -1.5), (3, 3, 3), key="stem")
    rig.cube("basin", (-4, 4.5, -4), (8, 2, 8), key="bowl")
    rim = ring_xz(rig, "basin", (0, 6.5, 0), 5, "rim", height=2)
    rig.cube("basin", (-4, 7.5, -4), (8, 1, 8), key="memory", inflate=-0.1)
    rig.bone("wisps", "basin", (0, 8.5, 0))
    for i, (x, z) in enumerate(((2.5, 0), (-1.5, 2), (-1.5, -2.5))):
        rig.cube("wisps", (x - 0.5, 8.4, z - 0.5), (1, 1, 1), key=f"wisp{i}", inflate=-0.15)
    skin = finish(rig)
    paint(skin, ["foot", "stem", "bowl"] + rim, "#8A8A90", dither=0.18, speck="shade")
    paint(skin, ["memory"], "#DCE4F4", speck="spec", dither=0.2)
    paint(skin, [f"wisp{i}" for i in range(3)], "#FFFFFF", tone="spec", bevel=0, dither=0)
    return rig, skin, {"idle": clip(4.0, {"wisps": spin(4.0, "y", steps=8)})}


def build_golden_snitch():
    """The gold ball and its silver wings, beating too fast to follow."""
    rig = Rig("golden_snitch", tex_w=32)
    rig.bone("body", "root", (0, 8, 0))
    ball = orb(rig, "body", (0, 8, 0), 1.5, "ball")
    rig.bone("wing_l", "body", (-1.5, 8.5, 0))
    rig.cube("wing_l", (-6.5, 8, -0.5), (5, 2, 1), key="wing_l", inflate=-0.1)
    rig.bone("wing_r", "body", (1.5, 8.5, 0))
    rig.cube("wing_r", (1.5, 8, -0.5), (5, 2, 1), key="wing_r", inflate=-0.1)
    skin = finish(rig)
    paint(skin, ball, "#E6B43A", speck="spec")
    paint(skin, ["wing_l", "wing_r"], "#E4E8F0", dither=0.2, speck="shade")
    return rig, skin, {"idle": clip(0.4, {
        "wing_l": osc(0.4, 35, "z"),
        "wing_r": osc(0.4, -35, "z"),
        "body": osc(0.4, 0.2, "y", kind="position"),
    })}


# --------------------------------------------------------------------------- books


def _book(rig, cover_hex, emblem_hex=None, page=False):
    """A book lying closed with its spine to the back; the cover is its own bone, hinged at the
    spine, so a clip can open it."""
    rig.bone("book", "root", (0, 4, 3.5))
    rig.cube("book", (-4, 4, -3), (8, 1, 7), key="back")
    rig.cube("book", (-3.5, 5, -2.5), (7, 2, 6), key="pages")
    rig.cube("book", (-4, 5, 3), (8, 2, 1), key="spine")
    rig.bone("cover", "book", (0, 7, 3.5))
    rig.cube("cover", (-4, 7, -3), (8, 1, 7), key="cover")
    if page:
        # A loose leaf on top of the block, hinged at the spine like the cover.
        rig.bone("page", "book", (0, 7, 3))
        rig.cube("page", (-3.5, 7, -2.5), (7, 1, 5), key="page", inflate=-0.35)
    skin = finish(rig)
    if page:
        paint(skin, ["page"], "#F2EAD0", dither=0.2, speck="shade")
    paint(skin, ["back", "spine"], cover_hex, tone="shade")
    paint(skin, ["cover"], cover_hex, dither=0.08)
    paint(skin, ["pages"], "#EADFBE", dither=0.25, speck="shade")
    if emblem_hex:
        x, y, w, h = skin.face("cover", "top")
        g = tones(emblem_hex)["light"]
        skin.d.rectangle([x + w // 2 - 1, y + h // 2 - 1, x + w // 2, y + h // 2], fill=g)
        skin.d.rectangle([x + 1, y, x + w - 2, y], fill=g)
        skin.d.rectangle([x + 1, y + h - 1, x + w - 2, y + h - 1], fill=g)
    return skin


def build_standard_book_of_spells():
    """The set text, lying open a crack; a page lifts and turns on its own now and then."""
    rig = Rig("standard_book_of_spells", tex_w=48)
    skin = _book(rig, "#5A2A5E", "#E6B43A", page=True)
    return rig, skin, {"idle": clip(4.0, {
        "cover": {"rotation": kf((0.0, (-25, 0, 0)), (4.0, (-25, 0, 0)))},
        "page": {"rotation": kf((0.0, (-5, 0, 0)), (2.5, (-5, 0, 0)), (3.2, (-90, 0, 0)),
                                (3.8, (-22, 0, 0)), (4.0, (-5, 0, 0)))},
    })}


def build_ministry_handbook():
    """The Handbook in Ministry purple and gold, turning its own pages."""
    rig = Rig("ministry_handbook", tex_w=48)
    skin = _book(rig, "#3E1F47", "#E6B43A", page=True)
    return rig, skin, {"idle": clip(3.0, {
        "cover": {"rotation": kf((0.0, (-35, 0, 0)), (3.0, (-35, 0, 0)))},
        "page": {"rotation": kf((0.0, (-5, 0, 0)), (1.6, (-5, 0, 0)), (2.2, (-120, 0, 0)),
                                (2.8, (-30, 0, 0)), (3.0, (-5, 0, 0)))},
    })}


def build_monster_book_of_monsters():
    """Furred, fanged, and snapping -- two quick bites and a shudder."""
    rig = Rig("monster_book_of_monsters", tex_w=48)
    rig.bone("book", "root", (0, 4, 3.5))
    rig.cube("book", (-4, 4, -3), (8, 3, 7), key="jaw", inflate=0.2)
    rig.cube("book", (-3.5, 7, -3.5), (7, 1, 1), key="teeth_low", inflate=-0.1)
    rig.bone("cover", "book", (0, 7, 3.5))
    rig.cube("cover", (-4, 7, -3), (8, 3, 7), key="lid", inflate=0.2)
    rig.cube("cover", (-3.5, 6.5, -3.5), (7, 1, 1), key="teeth_up", inflate=-0.1)
    rig.cube("cover", (-3, 9.5, -3.4), (2, 1, 1), key="eye_l")
    rig.cube("cover", (1, 9.5, -3.4), (2, 1, 1), key="eye_r")
    skin = finish(rig)
    paint(skin, ["jaw", "lid"], "#6A4A2A", dither=0.45, speck="dark")
    paint(skin, ["teeth_low", "teeth_up"], "#EEE6CE", tone="light", dither=0)
    paint(skin, ["eye_l", "eye_r"], "#F2D048", tone="light", dither=0)
    return rig, skin, {"idle": clip(2.0, {
        "cover": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-35, 0, 0)), (0.3, (0, 0, 0)),
                                 (0.45, (-35, 0, 0)), (0.6, (0, 0, 0)), (2.0, (0, 0, 0)))},
        "book": {"rotation": kf((0.0, (0, 0, 0)), (0.7, (0, 0, 4)), (0.8, (0, 0, -4)),
                                (0.9, (0, 0, 3)), (1.0, (0, 0, 0)), (2.0, (0, 0, 0)))},
    })}


def build_howler():
    """The red envelope, flap working like a mouth, shaking as it shouts."""
    rig = Rig("howler", tex_w=32)
    rig.bone("envelope", "root", (0, 7, 0))
    rig.cube("envelope", (-4, 4, -0.5), (8, 6, 1), key="body")
    rig.cube("envelope", (-1, 6, -0.8), (2, 2, 1), key="seal", inflate=-0.1)
    rig.bone("flap", "envelope", (0, 10, 0))
    rig.cube("flap", (-4, 7, -0.7), (8, 3, 1), key="flap", inflate=-0.05)
    skin = finish(rig)
    paint(skin, ["body", "flap"], "#C0392E", dither=0.08)
    paint(skin, ["seal"], "#7A1414", speck="light")
    return rig, skin, {"idle": clip(1.2, {
        "flap": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (60, 0, 0)), (0.4, (10, 0, 0)),
                                (0.6, (70, 0, 0)), (0.9, (5, 0, 0)), (1.2, (0, 0, 0)))},
        "envelope": osc(0.3, 4, "z"),
    })}


# --------------------------------------------------------------------------- brooms

# (handle wood, twigs, binding, tail widths bottom-up) -- the same identities as the icons.
BROOMS = {
    "broom": ("#B08050", "#D8B870", "#8A6A3A", (5, 4, 3)),
    "oakshaft_79": ("#5E3E22", "#A88A5A", "#B8733A", (5, 4, 3)),
    "cleansweep_seven": ("#9A6A3A", "#C8A870", "#C3C9D4", (4, 4, 3)),
    "comet_260": ("#A0522D", "#C89A6A", "#E6B43A", (4, 3, 3)),
    "nimbus_2000": ("#5A2A1A", "#8A6A4A", "#EBC874", (3, 3, 2)),
    "nimbus_2001": ("#2A2224", "#6A5A4A", "#D8DCE4", (3, 3, 2)),
    "firebolt": ("#3A2A20", "#B89A6A", "#EBC874", (3, 3, 2)),
    "firebolt_supreme": ("#2A1E1A", "#C8A060", "#E0502A", (3, 3, 2)),
}


def _build_broom(name):
    """A long handle over a long twig tail: tail flares in three steps below the binding, the
    handle runs a full block above it. Twigs are streaked in their shade down every side."""
    wood, twigs, band, widths = BROOMS[name]
    rig = Rig(name, tex_w=32)
    rig.bone("broom", "root", (0, 9, 0))
    rig.cube("broom", (-0.5, 10, -0.5), (1, 14, 1), key="handle")
    rig.cube("broom", (-1, 9, -1), (2, 1, 2), key="binding", inflate=0.15)
    rig.bone("tail", "broom", (0, 9, 0))
    y = 9
    for i, w in enumerate(widths[::-1]):
        y -= 3
        rig.cube("tail", (-w / 2, y, -w / 2), (w, 3, w), key=f"tail{i}")
    skin = finish(rig)
    paint(skin, ["handle"], wood, dither=0.05)
    paint(skin, ["binding"], band)
    t = paint(skin, [f"tail{i}" for i in range(3)], twigs, dither=0)
    for i in range(3):
        for face in skin.SIDES:
            x, y0, w, h = skin.face(f"tail{i}", face)
            for col in range(0, w, 2):
                skin.d.line([(x + col, y0), (x + col, y0 + h - 1)], fill=t["shade"])
        x, y0, w, h = skin.face(f"tail{i}", "bottom")
        skin.rect((x, y0, w, h), t["dark"])
    return rig, skin, {"idle": clip(2.5, {
        "tail": merge(osc(2.5, 4, "x"), osc(2.5, 3, "z", phase=0.25)),
        "broom": osc(2.5, 0.3, "y", kind="position"),
    })}


# --------------------------------------------------------------------------- coins, deluminator, map
#
# These three used to have bespoke renderers; they are AnimatedItems now, keeping the hand-tuned
# display transforms of their `models/item/<id>.json` -- so each keeps the old model's frame.


def _coin(name, hex_colour, diameter, emblem):
    """A struck coin standing on its edge, x/y face, turning slowly in the hand. The old coins were
    all 12 across; the denominations now step down by size as well as metal."""
    rig = Rig(name, tex_w=64)
    c = diameter / 2
    rig.bone("coin", "root", (0, 6, 0.7))
    d = diameter
    # Three overlapping slabs make the round edge; each is pulled in a hair further than the last
    # so their faces are never coplanar (coplanar faces z-fight into a shimmering hatch).
    rig.cube("coin", (-c, 6 - c + 2, 0.2), (d, d - 4, 1), key="disc_w")
    rig.cube("coin", (-c + 2, 6 - c, 0.2), (d - 4, d, 1), key="disc_h", inflate=-0.02)
    rig.cube("coin", (-c + 1, 6 - c + 1, 0.2), (d - 2, d - 2, 1), key="disc_c", inflate=-0.04)
    b = d - 6
    rig.cube("coin", (-b / 2, 6 - b / 2, -0.3), (b, b, 2), key="boss")
    skin = finish(rig)
    t = paint(skin, ["disc_w", "disc_h", "disc_c"], hex_colour, dither=0.05)
    paint(skin, ["boss"], hex_colour, tone="light", dither=0.08, speck="spec")
    for key in ("disc_w", "disc_h", "disc_c"):
        for face in ("north", "south"):
            x, y, w, h = skin.face(key, face)
            skin.d.rectangle([x, y, x + w - 1, y + h - 1], outline=t["shade"])
    for face in ("north", "south"):
        x, y, w, h = skin.face("boss", face)
        for dx, dy in emblem:
            if 0 <= dx < w and 0 <= dy < h:
                skin.d.point((x + dx, y + dy), fill=t["dark"])
    return rig, skin, {"idle": clip(5.0, {"coin": spin(5.0, "y", steps=8)})}


def build_galleon():
    # a dragon-ish G: the Galleon is Gringotts gold
    return _coin("galleon", "#E6B43A", 12, [(1, 1), (2, 1), (3, 1), (4, 1), (1, 2), (1, 3), (1, 4),
                                             (2, 4), (3, 4), (4, 4), (4, 3), (3, 3)])


def build_sickle():
    # a crescent: the silver Sickle
    return _coin("sickle", "#C3C9D4", 10, [(1, 0), (0, 1), (0, 2), (1, 3), (2, 3)])


def build_knut():
    # a plain struck dot: the bronze Knut
    return _coin("knut", "#B8733A", 8, [(0, 0), (1, 1)])


def build_deluminator():
    """Dumbledore's silver lighter. The lid flips back on its hinge to take a light, and back."""
    rig = Rig("deluminator", tex_w=32)
    rig.bone("base", "root", (0, 0, 0))
    rig.cube("base", (-1.5, 0, -1.5), (3, 9, 3), key="body")
    rig.cube("base", (-1.5, 2, -1.5), (3, 1, 3), key="band_low", inflate=0.1)
    rig.cube("base", (-1.5, 7, -1.5), (3, 1, 3), key="band_high", inflate=0.1)
    rig.bone("lid", "base", (0, 9, 1.5))
    rig.cube("lid", (-1.5, 9, -1.5), (3, 2, 3), key="lid")
    rig.cube("lid", (-0.5, 11, -0.5), (1, 1, 1), key="knob")
    skin = finish(rig)
    paint(skin, ["body", "lid"], "#C3C9D4", dither=0.08, speck="spec")
    paint(skin, ["band_low", "band_high", "knob"], "#8A8E96", tone="shade")
    closed = {"lid": {"rotation": kf((0.0, (0, 0, 0)))}}
    open_ = {"lid": {"rotation": kf((0.0, (-110, 0, 0)))}}
    return rig, skin, {
        "idle_closed": clip(1.0, closed),
        "idle_open": clip(1.0, open_),
        "open_lid": clip(0.3, {"lid": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-110, 0, 0)))}}, loop=False),
        "close_lid": clip(0.3, {"lid": {"rotation": kf((0.0, (-110, 0, 0)), (0.3, (0, 0, 0)))}}, loop=False),
    }


def build_marauders_map():
    """The map as a tri-fold: the left flap folds over the front, the right one behind, so the two
    never meet. Clip names are MaraudersMapItem's own and stay exactly as they were."""
    rig = Rig("marauders_map", tex_w=64)
    rig.bone("sheet", "root", (0, 0, 0))
    rig.bone("panel_centre", "sheet", (0, 0, 0))
    rig.cube("panel_centre", (-3, -5, 0), (6, 10, 1), key="centre")
    rig.bone("flap_left", "sheet", (-3, 0, 1))
    rig.cube("flap_left", (-9, -5, 0), (6, 10, 1), key="left")
    rig.bone("flap_right", "sheet", (3, 0, 0))
    rig.cube("flap_right", (3, -5, 0), (6, 10, 1), key="right")
    skin = finish(rig)
    t = paint(skin, ["centre", "left", "right"], "#DAC291", dither=0.14, speck="shade")
    ink = tones("#3A2414")
    for key in ("centre", "left", "right"):
        for face in ("north", "south"):
            x, y, w, h = skin.face(key, face)
            # corridors and rooms in iron-gall ink, a few footprints in the halls
            skin.d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], outline=ink["light"])
            skin.d.line([(x + 1, y + h // 2), (x + w - 2, y + h // 2)], fill=ink["light"])
            skin.d.line([(x + w // 2, y + 1), (x + w // 2, y + h // 2)], fill=ink["light"])
            skin.d.point((x + 2, y + h - 3), fill=ink["base"])
            skin.d.point((x + 3, y + h - 4), fill=ink["base"])
    fold = {"flap_left": {"rotation": kf((0.0, (0, -180, 0)))},
            "flap_right": {"rotation": kf((0.0, (0, 180, 0)))}}
    flat = {"flap_left": {"rotation": kf((0.0, (0, 0, 0)))},
            "flap_right": {"rotation": kf((0.0, (0, 0, 0)))}}
    return rig, skin, {
        "animation.marauders_map.folded_idle": clip(1.0, fold),
        "animation.marauders_map.open_idle": clip(1.0, flat),
        "animation.marauders_map.unfolding": clip(1.0, {
            "flap_left": {"rotation": kf((0.0, (0, -180, 0)), (1.0, (0, 0, 0)))},
            "flap_right": {"rotation": kf((0.0, (0, 180, 0)), (1.0, (0, 0, 0)))}}, loop=False),
        "animation.marauders_map.folding": clip(1.0, {
            "flap_left": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (0, -180, 0)))},
            "flap_right": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (0, 180, 0)))}}, loop=False),
    }


def build_elder_wand():
    """The Elder Wand: pale bleached elder, knotted along its length with the nodes the books
    describe, a knob at the grip end. Drawn by WandRenderer, not AnimatedItemRenderer, so the
    bone names are its contract: wand_root > shaft/knob/tip, and wand_tip (the beam anchor) at the
    point. Never tinted: it is one wand, not a sample of elder wood.

    Grip at y 0, point up at y 23 -- the base wand's orientation and length, because both share
    one display base (models/item/wand.json). It used to run the other way (point at y -2, knob
    on top), a leftover of the pre-GeckoLib model, which held it by the point."""
    rig = Rig("elder_wand", tex_w=32)
    rig.bone("wand_root", "root", (0, 0, 0))
    rig.bone("knob", "wand_root", (0, 0, 0))
    rig.cube("knob", (-1, 0, -1), (2, 2, 2), key="knob", inflate=0.1)
    rig.cube("knob", (-0.5, -1, -0.5), (1, 1, 1), key="cap")
    rig.bone("shaft", "wand_root", (0, 2, 0))
    rig.cube("shaft", (-0.5, 2, -0.5), (1, 18, 1), key="shaft")
    for i, y in enumerate((4, 8, 12, 16)):
        rig.cube("shaft", (-1, y, -1), (2, 1, 2), key=f"node{i}", inflate=-0.25)
    rig.bone("tip", "wand_root", (0, 20, 0))
    rig.cube("tip", (-0.5, 20, -0.5), (1, 3, 1), key="point", inflate=-0.15)
    rig.bone("wand_tip", "tip", (0, 23, 0))
    skin = finish(rig)
    t = paint(skin, ["shaft", "point"], "#C8BFB0", dither=0.1, speck="shade")
    paint(skin, [f"node{i}" for i in range(4)], "#A89E8C", tone="shade", dither=0.2, speck="dark")
    paint(skin, ["knob", "cap"], "#B8AE9C", dither=0.15, speck="dark")
    # grain: a faint darker fleck stepping down every long face of the shaft
    for face in skin.SIDES:
        x, y, w, h = skin.face("shaft", face)
        for row in range(0, h, 3):
            skin.d.point((x + (row // 3) % max(1, w), y + row), fill=t["shade"])
    return rig, skin, {}


# Models drawn by a bespoke renderer that reads its texture from the default item path.
TEXTURE_PATHS = {"elder_wand": os.path.join(ASSETS, "textures", "item", "elder_wand.png")}


MODELS = {
    "elder_wand": build_elder_wand,
    "galleon": build_galleon,
    "sickle": build_sickle,
    "knut": build_knut,
    "deluminator": build_deluminator,
    "marauders_map": build_marauders_map,
    "time_turner": build_time_turner,
    "sneakoscope": build_sneakoscope,
    "remembrall": build_remembrall,
    "omnioculars": build_omnioculars,
    "foe_glass": build_foe_glass,
    "mirror_of_erised": build_mirror_of_erised,
    "philosophers_stone": build_philosophers_stone,
    "resurrection_stone": build_resurrection_stone,
    "slytherins_locket": build_slytherins_locket,
    "ravenclaws_diadem": build_ravenclaws_diadem,
    "portkey": build_portkey,
    "pensieve": build_pensieve,
    "golden_snitch": build_golden_snitch,
    "standard_book_of_spells": build_standard_book_of_spells,
    "ministry_handbook": build_ministry_handbook,
    "monster_book_of_monsters": build_monster_book_of_monsters,
    "howler": build_howler,
}
MODELS.update({name: (lambda n=name: _build_broom(n)) for name in BROOMS})


# --------------------------------------------------------------------------- in-hand base
#
# The special model's `base` supplies nothing but display transforms (the geo is what is drawn),
# so it is written here, from the geo, rather than borrowed from whatever flat sprite or cuboid
# happened to sit at models/item/<id>.json. Borrowing is how every geo item ended up 8 units high:
# those transforms were tuned for a model whose floor is at y 0, and geo y 0 is the cube centre.

HELD_DIR = os.path.join(ASSETS, "models", "item")
HELD_CREDIT = "generated by tools/item_geo.py"

HOLD = {
    "galleon": "small", "sickle": "small", "knut": "small",
    "deluminator": "small",
    "marauders_map": "book",
    "time_turner": "artifact",
    "sneakoscope": "artifact",
    "remembrall": "small",
    "omnioculars": "object",
    "foe_glass": "object",
    "mirror_of_erised": "artifact",
    "philosophers_stone": "small",
    "resurrection_stone": "small",
    "slytherins_locket": "artifact",
    "ravenclaws_diadem": "artifact",
    "portkey": "object",
    "pensieve": "object",
    "golden_snitch": "small",
    "standard_book_of_spells": "object",
    "ministry_handbook": "object",
    "monster_book_of_monsters": "object",
    "howler": "book",
}
HOLD.update({name: "staff" for name in BROOMS})
# A Galleon is 12 across; the others are drawn smaller on purpose and should stay so in hand.
RELATIVE = {"galleon": 12.0, "sickle": 12.0, "knut": 12.0}


def held_path(name):
    return os.path.join(HELD_DIR, name + "_held.json")


def write_held(name):
    """models/item/<id>_held.json: transforms only, computed from the geo on disk."""
    geo = item_display.load_json(os.path.join(GEO_DIR, name + ".geo.json"))
    bounds = item_display.geo_bounds(geo)
    model = {
        "credit": HELD_CREDIT,
        "textures": {"particle": f"wizards_and_beasts:item/{name}"},
        "display": item_display.display_for(HOLD[name], bounds, relative_to=RELATIVE.get(name)),
    }
    with open(held_path(name), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")


# The wand's base (models/item/wand.json) serves both the wand and the Elder Wand -- one
# renderer switches geo between them. Its transforms are computed from the base wand's own
# silhouette: the classic handle, straight shaft and pointed tip, the bones every wood's shape
# is a variation on. (The wand rig itself is the author's and is never written here.)
WAND_BASE_BONES = ("handle_classic", "shaft_straight", "tip_pointed")
GUI_THICKEN = 1.4


def write_wand_base():
    geo = item_display.load_json(os.path.join(GEO_DIR, "wand.geo.json"))
    geometry = geo["minecraft:geometry"][0]
    base = {"minecraft:geometry": [dict(geometry, bones=[
        b for b in geometry["bones"] if b["name"] in WAND_BASE_BONES])]}
    display = item_display.display_for("wand", item_display.geo_bounds(base))
    # A 1.5-unit wand at slot scale is barely a pixel wide, darker than the slot behind it.
    # Vanilla draws its stick two pixels wide for the same reason; here the cross-section (x, z)
    # is thickened in the slot only, and the length is left true.
    gui = display["gui"]
    gui["scale"] = [round(gui["scale"][0] * GUI_THICKEN, 3), gui["scale"][1], round(gui["scale"][2] * GUI_THICKEN, 3)]
    model = {
        "credit": HELD_CREDIT,
        # Lit face-on in slots, like a sprite: side lighting left most of a thin rod in shadow.
        "gui_light": "front",
        "textures": {"particle": "wizards_and_beasts:item/wand"},
        "display": display,
    }
    with open(os.path.join(HELD_DIR, "wand.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")


# --------------------------------------------------------------------------- emit


def emit(name, force):
    rig, skin, clips = MODELS[name]()
    tex_h = skin.img.height
    for track in clips.values():
        for bone in track["bones"]:
            if bone not in rig.bone_names():
                raise ValueError(f"{name}: animation track for unknown bone {bone!r}")
    paths = {
        "geo": os.path.join(GEO_DIR, name + ".geo.json"),
        "anim": os.path.join(ANIM_DIR, name + ".animation.json"),
        "tex": TEXTURE_PATHS.get(name, os.path.join(TEX_DIR, name + ".png")),
    }
    if not force and not is_regenerable(paths["tex"], MARKER):
        print(f"skip {name}: texture owned elsewhere (--force claims it)")
        return False
    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    with open(paths["geo"], "w", encoding="utf-8", newline="\n") as fh:
        json.dump(rig.geo(tex_h), fh, indent=2)
        fh.write("\n")
    if clips:  # a model with no clips of its own (the Elder Wand plays the wand's) writes none
        with open(paths["anim"], "w", encoding="utf-8", newline="\n") as fh:
            json.dump({"format_version": "1.8.0", "animations": clips}, fh, indent=2)
            fh.write("\n")
    save(skin.img, paths["tex"], MARKER)
    if name in HOLD:
        write_held(name)
    print(f"wrote {name}: {rig.cube_count()} cubes, {rig.tex_w}x{tex_h} sheet, clips {sorted(clips)}")
    return True


# --------------------------------------------------------------------------- animated preview
#
# The entity previewer draws the rest pose. An item model is mostly its motion, so this samples a
# clip's keyframes, poses the bones and rasterises every frame through the previewer's own
# rasteriser, with one camera fixed across the whole clip -- re-framing per frame would make the
# model breathe in and out of the picture and hide the real motion.


def _sample(track, t, length):
    """Linear interpolation of one keyframe track at time t (GeckoLib's default easing)."""
    keys = sorted((float(k), v) for k, v in track.items())
    if len(keys) == 1:
        return keys[0][1]
    t = t % length if length else 0.0
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return keys[-1][1]


def _posed_cubes(bp, geo, anim, t):
    """collect_cubes with the clip applied: rotation keys add to the rest rotation, position keys
    translate the bone in its parent's frame."""
    import numpy as np
    bones = {b["name"]: b for b in geo["bones"]}
    length = anim.get("animation_length", 1.0)
    tracks = anim.get("bones", {})
    cache = {}

    def world_of(name):
        if name in cache:
            return cache[name]
        bone = bones[name]
        track = tracks.get(name, {})
        rot = list(bone.get("rotation", [0, 0, 0]))
        if "rotation" in track:
            rot = [a + b for a, b in zip(rot, _sample(track["rotation"], t, length))]
        m = bp.euler_matrix(rot)
        pivot = np.array([float(x) for x in bone.get("pivot", [0, 0, 0])])
        move = np.array(_sample(track["position"], t, length), dtype=float) if "position" in track \
            else np.zeros(3)
        parent = bone.get("parent")
        pm, poff = world_of(parent) if parent in bones else (np.eye(3), np.zeros(3))
        cache[name] = (pm @ m, poff + pm @ (pivot - m @ pivot + move))
        return cache[name]

    out = []
    for name, bone in bones.items():
        m, off = world_of(name)
        for cube in bone.get("cubes", []):
            out.append((cube, m, off))
    return out


def _frames(name, frames, size, clip_name="idle"):
    import math
    import numpy as np
    from PIL import Image
    import beast_preview as bp
    geo = json.load(open(os.path.join(GEO_DIR, name + ".geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
    anim = json.load(open(os.path.join(ANIM_DIR, name + ".animation.json"), encoding="utf-8"))["animations"][clip_name]
    tex = np.asarray(Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")).astype(np.float32) / 255.0
    th, tw = tex.shape[:2]
    view = bp.rot_x(math.radians(18)) @ bp.rot_y(math.radians(180 - 35))
    length = float(anim.get("animation_length", 1.0))

    posed = []
    for i in range(frames):
        t = length * i / frames
        tris = []
        for cube, m, off in _posed_cubes(bp, geo, anim, t):
            ox, oy, oz = (float(c) for c in cube["origin"])
            w, h, d = (float(s) for s in cube["size"])
            grow = float(cube.get("inflate", 0) or 0)
            u0, v0 = (float(c) for c in cube["uv"])
            cm, coff = bp.cube_matrix(cube)
            for fname, mults in bp.FACES.items():
                cs = [view @ (m @ (cm @ np.array([ox - grow + mu[0] * (w + 2 * grow),
                                                   oy - grow + mu[1] * (h + 2 * grow),
                                                   oz - grow + mu[2] * (d + 2 * grow)]) + coff) + off)
                      for mu in mults]
                ru, rv, rw, rh = bp.uv_rect(fname, u0, v0, w, h, d)
                tris.append((cs, [(ru, rv + rh), (ru + rw, rv + rh), (ru + rw, rv), (ru, rv)]))
        posed.append(tris)

    pts = np.array([c for tris in posed for cs, _ in tris for c in cs])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    centre = (lo + hi) / 2.0
    scale = (size - 24) / (max(hi[0] - lo[0], hi[1] - lo[1]) or 1.0)
    images = []
    for tris in posed:
        frame = np.zeros((size, size, 3), dtype=np.float32)
        frame[:] = np.array(bp.BG, dtype=np.float32) / 255.0
        zbuf = np.full((size, size), -1e9, dtype=np.float32)
        for cs, uvs in tris:
            s = [((c[0] - centre[0]) * scale + size / 2, size / 2 - (c[1] - centre[1]) * scale, c[2])
                 for c in cs]
            for a, b, c in ((0, 1, 2), (0, 2, 3)):
                bp.raster(frame, zbuf, [s[a], s[b], s[c]], [uvs[a], uvs[b], uvs[c]], tex, tw, th)
        images.append(Image.fromarray((np.clip(frame, 0, 1) * 255).astype("uint8"), "RGB"))
    return images, length


def preview(names, out_dir, frames=24, size=192, clip_name="idle"):
    """One animated GIF per model, looping a clip in real time (idle unless `--clip`)."""
    os.makedirs(out_dir, exist_ok=True)
    for name in names:
        anims = json.load(open(os.path.join(ANIM_DIR, name + ".animation.json"), encoding="utf-8"))
        clips = anims["animations"]
        chosen = clip_name if clip_name in clips else ("idle" if "idle" in clips else next(iter(clips)))
        images, length = _frames(name, frames, size, chosen)
        path = os.path.join(out_dir, name + ("" if chosen == "idle" else "_" + chosen.split(".")[-1]) + ".gif")
        images[0].save(path, save_all=True, append_images=images[1:], loop=0,
                       duration=max(20, int(1000 * length / frames)))
        print("preview", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--preview", metavar="DIR")
    ap.add_argument("--clip", default="idle", help="which clip the preview loops")
    ap.add_argument("--held-only", action="store_true",
                    help="rewrite only models/item/<id>_held.json from the geo already on disk")
    args = ap.parse_args()
    only = [x.strip() for x in args.only.split(",") if x.strip()] or list(MODELS)
    if args.held_only:
        for name in only:
            if name in HOLD:
                write_held(name)
        if "elder_wand" in only:
            write_wand_base()
        print(f"held bases written: {sum(1 for n in only if n in HOLD)}")
        return
    for name in only:
        emit(name, args.force)
    if args.preview:
        preview(only, args.preview, clip_name=args.clip)


if __name__ == "__main__":
    main()
