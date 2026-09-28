#!/usr/bin/env python3
"""A cauldron you can see into, and a liquid that shows what is in it.

WHAT WAS WRONG. The cauldron was a solid JSON box, `[2,3,2] -> [14,12,14]`, with the block's
top texture stretched across its opening. It could not read as empty, because there was no
inside: the "opening" was a lid. `CauldronVisual` already tracked six states and drove a
`tintindex` on that one top face, so the state machine was right and the geometry simply had
nowhere to put the answer -- and the one tinted face is invisible unless you stand over the
block and look down.

WHAT THIS IS. The pot is hollow here: a floor, four walls and a rim, so the inside is a real
volume. The liquid is not painted on the pot at all. It is five separate bones stacked at the
same height, one per look, and the animation for each state scales the four it does not want
to zero. That is what finally makes EMPTY mean empty -- the empty clip zeroes all five and
there is nothing in the pot but its own floor.

WHY ANIMATION AND NOT CODE. GeoBone in GeckoLib 5.4.5 has no `setHidden`; bone state comes
from the animation. Every clip sets all five scales explicitly rather than relying on what the
previous clip left behind, so switching states cannot leave two surfaces fighting for the same
plane.

ONE RIG, THREE METALS. The three cauldrons share a BlockEntityType and therefore share this
geometry; only the sheet differs. The renderer picks the sheet from the blockstate, the same
way GoblinRenderer picks a texture from a synced role.

Run from the repo root:  python tools/cauldron_model.py [--force]
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save, shade  # noqa: E402
from boxuv import Packer, faces, mottle  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/block/wizarding_cauldron.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/block/wizarding_cauldron.animation.json"
TEX_DIR = f"{ASSETS}/textures/block"
MARKER = marker("cauldron_model.py")

TEX_W, TEX_H = 128, 64

# The three metals. Keys are the texture suffix; the renderer maps a block to one of these.
# Liquid colours are deliberately NOT per-metal: a Pepperup Potion is the same colour in a
# brass pot as in a pewter one, and making the brew depend on the pot would be a lie.
METALS = {
    "pewter": {"base": "#7C818A", "rim": "#8E949E", "dark": "#5A5F68", "foot": "#4E535B"},
    "brass": {"base": "#A98A3C", "rim": "#C6A45A", "dark": "#7D6428", "foot": "#6B5522"},
    "wizarding_copper": {"base": "#A96E42", "rim": "#C4855A", "dark": "#7C4F2E", "foot": "#6A4227"},
}

# The five things a pot can have in it. EMPTY is absent on purpose: it is the state with no
# liquid bone showing at all, which is the entire point of the rebuild.
LIQUIDS = {
    "water": "#3E6FBE",        # plain water, before anything is dropped in
    "ingredients": "#6C8E46",  # murky: something is steeping
    "brewing": "#C89F27",      # hot, working
    "done": "#D8C46B",         # settled and pale, ready to bottle
    "spoiled": "#6A6156",      # grey-brown sludge, left off the heat
}

STEAM = "#DCE4EB"


# --------------------------------------------------------------------------- rig

# bone -> (parent, pivot, [(cube name, origin, size), ...])
#
# Block model space: X and Z run -8..8 about the block centre, Y runs 0..16 from its floor.
# The pot keeps the old model's outer footprint (-6..6, y 3..12) so it sits exactly where
# players are used to; everything inside that is new.
# The plane every liquid surface sits on. NOT halfway down the pot: a cauldron seen from a
# standing player's eye is looked at over its own rim, and geometry that low is hidden behind
# it from every angle except directly overhead. That is the same mistake the tinted `#top` face
# made. At 10.5 the surface sits just under the rim's underside and reads from across a room.
LID_Y = 10.5
BONES = [
    ("cauldron", None, (0, 0, 0), []),
    ("pot", "cauldron", (0, 0, 0), [
        ("floor",      (-6, 3, -6),   (12, 2, 12)),
        ("wall_north", (-6, 5, -6),   (12, 7, 2)),
        ("wall_south", (-6, 5, 4),    (12, 7, 2)),
        ("wall_west",  (-6, 5, -4),   (2, 7, 8)),
        ("wall_east",  (4, 5, -4),    (2, 7, 8)),
    ]),
    # FOUR WALLS, NOT A SLAB. A single 13x1x13 band across the top is a lid: it caps the pot and
    # hides everything in it, which is precisely the failure the old JSON model had with its `#top`
    # face and precisely what this rig exists to fix. The datagen model's own comment says so —
    # "a slab would cap the pot and hide the brew" — and this rig reintroduced it anyway. The
    # opening (x and z within -4..4) must stay clear.
    #
    # Sizes are whole texels on every axis: box UV rounds them up, so a 2.5-thick rim would be
    # painted as 3 and the geometry and its island would disagree.
    ("rim", "pot", (0, 12, 0), [
        ("rim_north", (-7, 12, -7), (14, 1, 3)),
        ("rim_south", (-7, 12, 4),  (14, 1, 3)),
        ("rim_west",  (-7, 12, -4), (3, 1, 8)),
        ("rim_east",  (4, 12, -4),  (3, 1, 8)),
    ]),
    ("legs", "cauldron", (0, 0, 0), [
        ("foot_nw", (-5, 0, -5), (2, 3, 2)),
        ("foot_ne", (3, 0, -5),  (2, 3, 2)),
        ("foot_sw", (-5, 0, 3),  (2, 3, 2)),
        ("foot_se", (3, 0, 3),   (2, 3, 2)),
    ]),
    # Steam rides above the rim and is zeroed by every clip that is not actually hot.
    #
    # One bone per puff, NOT three cubes in one bone. An animation can only address bones, so
    # three cubes under a single `steam` bone would leave the clips driving names that do not
    # exist — the puffs would sit at full size in every state, including an empty pot, and
    # nothing would report an error.
    # Above the rim's top (y 13), not through it.
    ("steam", "cauldron", (0, 13, 0), []),
    ("steam_low",  "steam", (0, 13.4, 0), [("steam_low",  (-3, 13.4, -3), (6, 1, 6))]),
    ("steam_mid",  "steam", (0, 14.8, 0), [("steam_mid",  (-2, 14.8, -2), (4, 1, 4))]),
    ("steam_high", "steam", (0, 16.0, 0), [("steam_high", (-1, 16.0, -1), (2, 1, 2))]),
]

# One bone per liquid look, each pivoting at the centre of the surface so a scale keyframe
# shrinks it towards the middle of the pot rather than towards a corner.
for _name in LIQUIDS:
    BONES.append((f"liquid_{_name}", "cauldron", (0, LID_Y, 0), [
        (f"liquid_{_name}", (-4, LID_Y, -4), (8, 1, 8)),
    ]))


def all_cubes():
    return [(cube_name, size)
            for _, _, _, cubes in BONES
            for cube_name, _origin, size in cubes]


def build_geo(packer):
    """Read the placements `packer` already made, so UVs cannot drift from the painter.

    The packer is filled by `place_sorted` (tallest island first) before this runs; placing
    here instead would re-place in authoring order and hand the painter different rectangles
    than the geometry claims.
    """
    bones = []
    for name, parent, pivot, cubes in BONES:
        bone = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            bone["parent"] = parent
        if cubes:
            bone["cubes"] = [{
                "origin": [round(v, 2) for v in origin],
                "size": [round(v, 2) for v in size],
                "uv": list(packer.placed[cube_name][0]),
            } for cube_name, origin, size in cubes]
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.wizarding_cauldron",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                # The steam reaches a block above the rim, so the bounds have to clear it or
                # the whole pot pops out of view when the anchor block leaves the frustum.
                "visible_bounds_width": 2,
                "visible_bounds_height": 2.5,
                "visible_bounds_offset": [0, 0.9, 0],
            },
            "bones": bones,
        }],
    }


# --------------------------------------------------------------------------- sheet

def paint(packer, metal):
    """One sheet for one metal. Liquid and steam islands are identical across metals."""
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    base = hx(metal["base"])
    rim = hx(metal["rim"])
    dark = hx(metal["dark"])
    foot = hx(metal["foot"])

    for cube_name, (uv, size) in packer.placed.items():
        rects = faces(uv, size)
        seed = sum(ord(c) for c in cube_name)

        if cube_name.startswith("liquid_"):
            colour = hx(LIQUIDS[cube_name[len("liquid_"):]])
            _paint_surface(draw, rects, colour, cube_name, seed)
            continue

        if cube_name.startswith("steam_"):
            _paint_steam(draw, rects, hx(STEAM), seed)
            continue

        if cube_name.startswith("foot_"):
            palette = [shade(foot, 0.88), shade(foot, 1.1)]
            for face_name, rect in rects.items():
                mottle(draw, rect, shade(foot, _lighting(face_name)), palette, 18, seed)
            continue

        if cube_name.startswith("rim_"):
            palette = [shade(rim, 0.9), shade(rim, 1.12), dark]
            for face_name, rect in rects.items():
                mottle(draw, rect, shade(rim, _lighting(face_name)), palette, 22, seed)
            continue

        # Pot body. The floor's top face is the one you see through an empty pot, so it is
        # painted darker and grubbier than the outside — it is the inside of a cooking pot.
        palette = [shade(base, 0.86), shade(base, 1.08), dark]
        for face_name, rect in rects.items():
            colour = shade(base, _lighting(face_name))
            if cube_name == "floor" and face_name == "top":
                colour = shade(dark, 0.92)
            mottle(draw, rect, colour, palette, 24, seed)

    return img


# Directional light baked in, matching the item models: top lit, underside dark. A block
# entity renderer gets one light value for the whole model, so without this the pot reads as
# a flat silhouette from every angle.
_FACE_LIGHT = {"top": 1.20, "bottom": 0.70, "north": 1.02, "south": 0.90,
               "east": 0.84, "west": 0.98}


def _lighting(face_name):
    return _FACE_LIGHT[face_name]


def _paint_surface(draw, rects, colour, cube_name, seed):
    """A liquid surface: only the top face really matters, the sides are the meniscus."""
    kind = cube_name[len("liquid_"):]
    for face_name, rect in rects.items():
        lit = shade(colour, 1.16 if face_name == "top" else 0.82)
        if kind == "ingredients":
            # Flecks of whatever is steeping, so it reads as loaded rather than just green.
            mottle(draw, rect, lit, [shade(colour, 0.6), hx("#8E7A45"), hx("#4A6B36")], 34, seed)
        elif kind == "brewing":
            mottle(draw, rect, lit, [shade(colour, 1.35), shade(colour, 0.7)], 30, seed)
        elif kind == "spoiled":
            mottle(draw, rect, lit, [shade(colour, 0.72), hx("#4A443C"), hx("#7E7466")], 40, seed)
        elif kind == "done":
            mottle(draw, rect, lit, [shade(colour, 1.25), shade(colour, 0.92)], 16, seed)
        else:
            mottle(draw, rect, lit, [shade(colour, 1.18), shade(colour, 0.86)], 14, seed)

    # A highlight streak across the top face sells it as a liquid rather than a slab.
    x0, y0, w, h = rects["top"]
    if w >= 4 and h >= 4:
        draw.line([(x0 + 1, y0 + 1), (x0 + w - 3, y0 + 1)],
                  fill=mix(colour, (255, 255, 255, 255), 0.45))


def _paint_steam(draw, rects, colour, seed):
    for face_name, rect in rects.items():
        mottle(draw, rect, shade(colour, _lighting(face_name)), [colour, shade(colour, 0.86)], 44, seed)


# --------------------------------------------------------------------------- clips

def build_anim():
    """One clip per CauldronVisual. Every clip sets every liquid bone, deliberately.

    Setting only the bone a clip wants would leave the previous clip's surface at full scale
    when the state changed, so two liquids would fight for the same plane. Being exhaustive
    costs a few lines and makes each clip a complete description of the pot.
    """
    def kf(*pairs):
        return {str(t): list(v) for t, v in pairs}

    def liquids(active, motion=None):
        """Scale every liquid bone: `active` gets 1 (or `motion`), the rest get flat zero."""
        out = {}
        for name in LIQUIDS:
            bone = f"liquid_{name}"
            if name == active:
                out[bone] = motion if motion else {"scale": kf((0.0, (1, 1, 1)))}
            else:
                out[bone] = {"scale": kf((0.0, (0, 0, 0)))}
        return out

    def steam(*keyframes):
        return {b: {"scale": kf(*keyframes)} for b in ("steam_low", "steam_mid", "steam_high")}

    no_steam = steam((0.0, (0, 0, 0)))

    clips = {}

    # EMPTY: nothing in the pot at all. Every liquid bone off, no steam. This is the state the
    # old solid model could not express.
    clips["empty"] = {"loop": True, "animation_length": 1.0,
                      "bones": {**liquids(None), **no_steam}}

    # WATER: still, with a slow swell you only notice if you watch.
    clips["water"] = {"loop": True, "animation_length": 5.0, "bones": {
        **liquids("water", {
            "scale": kf((0.0, (1, 1, 1)), (2.5, (1, 1.35, 1)), (5.0, (1, 1, 1))),
            "position": kf((0.0, (0, 0, 0)), (2.5, (0, 0.12, 0)), (5.0, (0, 0, 0))),
        }),
        **no_steam,
    }}

    # INGREDIENTS: something is steeping. Slightly livelier than water, still cold.
    clips["ingredients"] = {"loop": True, "animation_length": 3.6, "bones": {
        **liquids("ingredients", {
            "scale": kf((0.0, (1, 1, 1)), (1.2, (1, 1.5, 1)), (2.4, (1, 1.15, 1)), (3.6, (1, 1, 1))),
            "position": kf((0.0, (0, 0, 0)), (1.2, (0, 0.2, 0)), (2.4, (0, 0.05, 0)), (3.6, (0, 0, 0))),
        }),
        **no_steam,
    }}

    # BREWING: a rolling boil, and the steam column that goes with it.
    clips["brewing"] = {"loop": True, "animation_length": 1.6, "bones": {
        **liquids("brewing", {
            "scale": kf((0.0, (1, 1, 1)), (0.4, (1, 2.4, 1)), (0.8, (1, 1.3, 1)),
                        (1.2, (1, 2.0, 1)), (1.6, (1, 1, 1))),
            "position": kf((0.0, (0, 0, 0)), (0.4, (0, 0.5, 0)), (0.8, (0, 0.15, 0)),
                           (1.2, (0, 0.4, 0)), (1.6, (0, 0, 0))),
        }),
        "steam_low": {
            "scale": kf((0.0, (0.2, 0.2, 0.2)), (0.8, (1.0, 1.0, 1.0)), (1.6, (0.2, 0.2, 0.2))),
            "position": kf((0.0, (0, 0, 0)), (1.6, (0, 1.6, 0))),
        },
        "steam_mid": {
            "scale": kf((0.0, (0.9, 0.9, 0.9)), (0.8, (0.5, 0.5, 0.5)), (1.6, (0.9, 0.9, 0.9))),
            "position": kf((0.0, (0, 0, 0)), (0.8, (0.4, 1.0, -0.3)), (1.6, (0, 0, 0))),
        },
        "steam_high": {
            "scale": kf((0.0, (0.6, 0.6, 0.6)), (0.8, (0.15, 0.15, 0.15)), (1.6, (0.6, 0.6, 0.6))),
            "position": kf((0.0, (0, 0, 0)), (0.8, (-0.5, 1.4, 0.4)), (1.6, (0, 0, 0))),
        },
    }}

    # DONE: off the boil, settled, with a slow shimmer that says "come and bottle me".
    clips["done"] = {"loop": True, "animation_length": 4.0, "bones": {
        **liquids("done", {
            "scale": kf((0.0, (1, 1, 1)), (2.0, (1, 1.25, 1)), (4.0, (1, 1, 1))),
            "position": kf((0.0, (0, 0.1, 0)), (2.0, (0, 0.3, 0)), (4.0, (0, 0.1, 0))),
        }),
        "steam_low": {
            "scale": kf((0.0, (0.35, 0.35, 0.35)), (2.0, (0.5, 0.5, 0.5)), (4.0, (0.35, 0.35, 0.35))),
            "position": kf((0.0, (0, 0, 0)), (2.0, (0, 0.6, 0)), (4.0, (0, 0, 0))),
        },
        "steam_mid": {"scale": kf((0.0, (0, 0, 0)))},
        "steam_high": {"scale": kf((0.0, (0, 0, 0)))},
    }}

    # SPOILED: sunk below where it should sit, barely moving.
    clips["spoiled"] = {"loop": True, "animation_length": 6.0, "bones": {
        **liquids("spoiled", {
            "scale": kf((0.0, (1, 0.6, 1)), (3.0, (1, 0.75, 1)), (6.0, (1, 0.6, 1))),
            "position": kf((0.0, (0, -0.6, 0)), (3.0, (0, -0.45, 0)), (6.0, (0, -0.6, 0))),
        }),
        **no_steam,
    }}

    return {
        "format_version": "1.8.0",
        "animations": {f"animation.wizarding_cauldron.{name}": clip
                       for name, clip in clips.items()},
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

    targets = {name: os.path.join(TEX_DIR, f"wizarding_cauldron_{name}.png") for name in METALS}
    if not args.force:
        blocked = [p for p in targets.values() if not is_regenerable(p, MARKER)]
        if blocked:
            print(f"hand-authored, not overwriting: {', '.join(blocked)}. Use --force.")
            return 0

    os.makedirs(os.path.dirname(GEO), exist_ok=True)
    os.makedirs(os.path.dirname(ANIM), exist_ok=True)
    with open(GEO, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(geo, fh, indent=2)
        fh.write("\n")
    with open(ANIM, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(build_anim(), fh, indent=2)
        fh.write("\n")

    for name, metal in METALS.items():
        save(posterise(paint(packer, metal), 14), targets[name], MARKER)

    bones = len(geo["minecraft:geometry"][0]["bones"])
    clips = len(build_anim()["animations"])
    print(f"cauldron rig: {bones} bones, {clips} clips, "
          f"UV {used}/{TEX_H}px used, {len(METALS)} sheets at {TEX_W}x{TEX_H}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
