#!/usr/bin/env python3
"""The wandmaker's bench, as a rig that works rather than a cube that sits.

WHAT IT WAS. A table: a slab on four legs, built by `deskModel()` in the datagen. Perfectly
fine as a shape, and completely inert -- the block where wands are made looked exactly like
every other flat surface in the mod.

WHAT THIS IS. The same table silhouette, plus the things that make it a workshop: a small
lathe head at one end with a wand blank held between centres, a treadle underneath, and a
tool rack along the back. Two clips:

  idle     the blank turns barely at all; a shaving hanging off the bench edge sways
  working  the lathe head spins, the treadle pumps, the blank turns under it

WHICH CLIP PLAYS is decided by whether there is a blank or a core in the bench's input slots,
polled server-side and synced. Not by whether a menu is open: a menu is visible to exactly one
player, and animating a block for an audience of one is pointless.

ORIENTATION. Built with the working face at -Z, matching the `wandmakers_bench_front` texture
the JSON model already puts on the north face. GeoBlockRenderer auto-rotates by
HORIZONTAL_FACING when a block has it; this one does not, so the rig faces north always --
which is what the block does today.

Run from the repo root:  python tools/bench_model.py [--force]
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces, mottle  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"

# `wandmakers_bench_rig`, not `wandmakers_bench`. DefaultedBlockGeoModel derives all three paths
# from one id, and the bare name would resolve the texture to textures/block/wandmakers_bench.png
# — the existing flat 16px block texture, which the JSON model and the block item still use. The
# suffix keeps the rig's three files together and leaves the old art alone.
RIG = "wandmakers_bench_rig"
GEO = f"{ASSETS}/geckolib/models/block/{RIG}.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/block/{RIG}.animation.json"
SHEET = f"{ASSETS}/textures/block/{RIG}.png"
MARKER = marker("bench_model.py")

TEX_W, TEX_H = 128, 64

# Sampled to sit beside the existing wandmakers_bench_* block textures rather than replace
# them: the flat model still ships as the inventory icon, and a rig in a different palette
# would read as a different block in the hand than on the ground.
OAK = "#8A6A3F"
OAK_DARK = "#5E4726"
OAK_LIGHT = "#A9834F"
IRON = "#6E7178"
IRON_DARK = "#4A4D53"
BLANK = "#C7A971"     # the unfinished wand on the lathe
SHAVING = "#D9BE8C"


# --------------------------------------------------------------------------- rig

# bone -> (parent, pivot, [(cube name, origin, size), ...])
# Block space: X/Z run -8..8 about the centre, Y runs 0..16 from the block floor.
BONES = [
    ("bench", None, (0, 0, 0), []),
    ("frame", "bench", (0, 0, 0), [
        ("top",      (-8, 12, -8), (16, 3, 16)),
        ("leg_nw",   (-7, 0, -7),  (3, 12, 3)),
        ("leg_ne",   (4, 0, -7),   (3, 12, 3)),
        ("leg_sw",   (-7, 0, 4),   (3, 12, 3)),
        ("leg_se",   (4, 0, 4),    (3, 12, 3)),
        # A stretcher between the back legs, so the underside is not four sticks in the air.
        ("stretcher", (-7, 3, 5),  (14, 2, 1)),
    ]),
    # Tool rack along the back edge: a rail and three hanging tools.
    ("rack", "bench", (0, 15, 6), [
        ("rack_rail", (-6, 15, 6), (12, 1, 1)),
        ("tool_a",    (-5, 16, 6), (1, 3, 1)),
        ("tool_b",    (-1, 16, 6), (1, 4, 1)),
        ("tool_c",    (3, 16, 6),  (1, 3, 1)),
    ]),
    # Lathe: a fixed headstock and tailstock, with the blank turning between them.
    ("lathe", "bench", (0, 15, -3), [
        ("headstock", (-7, 15, -5), (2, 4, 4)),
        ("tailstock", (5, 15, -5),  (2, 4, 4)),
    ]),
    # The blank spins about its own long axis, which runs along X between the two stocks. Its
    # pivot therefore sits on that axis, not at the bench centre — pivot at the centre would
    # swing the blank around the table like a clock hand instead of turning it on the lathe.
    ("blank", "lathe", (0, 16.5, -3), [
        ("wand_blank", (-5, 16, -3.5), (10, 1, 1)),
    ]),
    # Treadle underneath, pumped while the lathe runs. Pivots at its back edge so it hinges.
    ("treadle", "bench", (0, 2, 4), [
        ("treadle_board", (-4, 1.5, -2), (8, 1, 6)),
    ]),
    # A shaving curling off the front edge. The only thing that moves while the bench is idle,
    # which is what stops "idle" reading as "broken".
    ("shaving", "bench", (5, 15, -8), [
        ("curl", (4.5, 13, -8.5), (1, 2, 1)),
    ]),
]


def all_cubes():
    return [(name, size) for _, _, _, cubes in BONES for name, _origin, size in cubes]


def build_geo(packer):
    bones = []
    for name, parent, pivot, cubes in BONES:
        bone = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            bone["parent"] = parent
        if cubes:
            bone["cubes"] = [{
                "origin": [round(v, 2) for v in origin],
                "size": [round(v, 2) for v in size],
                "uv": list(packer.placed[name][0]),
            } for name, origin, size in cubes]
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.wandmakers_bench",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 2,
                "visible_bounds_height": 2,
                "visible_bounds_offset": [0, 0.75, 0],
            },
            "bones": bones,
        }],
    }


# --------------------------------------------------------------------------- sheet

_FACE_LIGHT = {"top": 1.20, "bottom": 0.70, "north": 1.02, "south": 0.90,
               "east": 0.84, "west": 0.98}

# Which palette each cube is painted from. Anything unlisted is oak.
METAL_CUBES = {"headstock", "tailstock", "rack_rail", "tool_a", "tool_b", "tool_c"}


def paint(packer):
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    oak, oak_dark, oak_light = hx(OAK), hx(OAK_DARK), hx(OAK_LIGHT)
    iron, iron_dark = hx(IRON), hx(IRON_DARK)
    blank, shaving = hx(BLANK), hx(SHAVING)

    for name, (uv, size) in packer.placed.items():
        rects = faces(uv, size)
        seed = sum(ord(c) for c in name)

        if name in METAL_CUBES:
            base, palette, density = iron, [iron_dark, shade(iron, 1.15)], 20
        elif name == "wand_blank":
            base, palette, density = blank, [shade(blank, 0.86), shade(blank, 1.12)], 16
        elif name == "curl":
            base, palette, density = shaving, [shade(shaving, 0.8), shade(shaving, 1.1)], 26
        else:
            base, palette, density = oak, [oak_dark, oak_light], 28

        for face_name, rect in rects.items():
            mottle(draw, rect, shade(base, _FACE_LIGHT[face_name]), palette, density, seed)

        # Grain along the length of every oak face, so the top does not read as flat card.
        if name not in METAL_CUBES and name not in ("wand_blank", "curl"):
            for face_name in ("top", "north", "south"):
                x0, y0, w, h = rects[face_name]
                for row in range(y0, y0 + h, 3):
                    draw.line([(x0, row), (x0 + w - 1, row)], fill=shade(oak_dark, 1.0))

    return img


# --------------------------------------------------------------------------- clips

def build_anim():
    def kf(*pairs):
        return {str(t): list(v) for t, v in pairs}

    return {
        "format_version": "1.8.0",
        "animations": {
            # Nothing on the bench. The blank creeps round, the shaving sways. Deliberately slow:
            # this plays on every bench in the world all the time, and a busy idle is noise.
            "animation.wandmakers_bench.idle": {
                "loop": True,
                "animation_length": 8.0,
                "bones": {
                    "blank": {"rotation": kf((0.0, (0, 0, 0)), (8.0, (60, 0, 0)))},
                    "treadle": {"rotation": kf((0.0, (0, 0, 0)))},
                    "shaving": {"rotation": kf((0.0, (0, 0, 4)), (4.0, (0, 0, -5)), (8.0, (0, 0, 4)))},
                    "rack": {"rotation": kf((0.0, (0, 0, 0)))},
                },
            },
            # A blank or a core is on the bench. The treadle drives, the blank spins under it.
            #
            # The blank's rotation runs 0 -> 180 -> 360 over the loop rather than 0 -> 360 in one
            # step. GeckoLib interpolates the shortest arc between keyframes, so a single 360
            # keyframe is the same as no rotation at all and the blank would stand still.
            "animation.wandmakers_bench.working": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    "blank": {"rotation": kf((0.0, (0, 0, 0)), (0.6, (180, 0, 0)), (1.2, (360, 0, 0)))},
                    "treadle": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-14, 0, 0)),
                                               (0.6, (0, 0, 0)), (0.9, (-14, 0, 0)), (1.2, (0, 0, 0)))},
                    "shaving": {
                        "rotation": kf((0.0, (0, 0, 10)), (0.4, (0, 0, -14)),
                                       (0.8, (0, 0, 8)), (1.2, (0, 0, 10))),
                        "position": kf((0.0, (0, 0, 0)), (0.6, (0, -0.3, 0)), (1.2, (0, 0, 0))),
                    },
                    # The whole rack ticks with the treadle: the tools rattle on their rail.
                    "rack": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (1.5, 0, 0)),
                                            (0.6, (0, 0, 0)), (0.9, (1.5, 0, 0)), (1.2, (0, 0, 0)))},
                },
            },
        },
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    packer = Packer(TEX_W)
    packer.place_sorted(all_cubes())
    geo = build_geo(packer)
    used = packer.height_used()
    if used > TEX_H:
        print(f"ERROR: UV islands need {used}px of a {TEX_H}px sheet", file=sys.stderr)
        return 1

    if not args.force and not is_regenerable(SHEET, MARKER):
        print(f"{SHEET} is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    os.makedirs(os.path.dirname(GEO), exist_ok=True)
    os.makedirs(os.path.dirname(ANIM), exist_ok=True)
    with open(GEO, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(geo, fh, indent=2)
        fh.write("\n")
    with open(ANIM, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(build_anim(), fh, indent=2)
        fh.write("\n")
    save(posterise(paint(packer), 14), SHEET, MARKER)

    bones = len(geo["minecraft:geometry"][0]["bones"])
    print(f"bench rig: {bones} bones, 2 clips, UV {used}/{TEX_H}px used, sheet {TEX_W}x{TEX_H}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
