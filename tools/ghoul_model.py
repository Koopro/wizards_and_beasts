#!/usr/bin/env python3
"""The Weasley attic ghoul: rig, skin and glowmask.

Replaces the placeholder box rig — six cubes, a body, a head and four sticks — that
`data/creatures/ghoul.json` still carries a `_comment` about. That rig read as a
featureless slab in the Bestiary portrait, which is what prompted this.

Canon is the Deathly Hallows attic ghoul: hunched, gangly, slack-jawed and buck-toothed,
skin a blotchy grey-green, dressed in a set of Ron's cast-off pyjamas. It is a dim,
harmless thing that groans and drops pipes, so the rest pose is a stoop with the arms
hanging past the knees and the head lolling forward — not a soldier's A-pose.

Two things are deliberate and worth stating, because both differ from the rig this
replaces:

  - **True scale.** The old rig was 14.4 units against a 1.9-block hitbox, so it drew at
    roughly 60% of the space it occupied. Across the 96 rigs in the repo the ratio of model
    height to declared height ranges 0.17 to 2.29 with a median of 0.78, i.e. there is no
    convention to match — they were bulk-generated at arbitrary scale. This one is built at
    1.0: 16 units per block, ~30 units tall for a 1.9-block entity.
  - **64x64 skin.** 32x32 cannot carry a face, teeth and cloth. `texture_width/height` in
    the geometry and the PNG must agree or `bestiary_portraits.rig_is_renderable` rejects
    the rig and the portrait silently keeps its old art.

UVs are packed automatically. Bedrock box-UV wants a (2d + 2w) x (d + h) island per cube;
placing ~16 of those by hand is the bookkeeping that makes rigs drift, so a shelf packer
assigns them and the skin is painted through the same coordinates it hands out. The two
therefore cannot disagree.

Run from the repo root:  python tools/ghoul_model.py [--force]
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
GEO = f"{ASSETS}/geckolib/models/entity/ghoul.geo.json"
ANIM = f"{ASSETS}/geckolib/animations/entity/ghoul.animation.json"
SKIN = f"{ASSETS}/textures/entity/ghoul.png"
GLOW = f"{ASSETS}/textures/entity/ghoul_glowmask.png"
MARKER = marker("ghoul_model.py")

TEX = 64

# Blotchy, damp, faintly fungal. Not zombie green — the books call ghouls slimy and
# buck-toothed rather than undead, and the film's is grey with a sickly cast.
SKIN_MID = hx("#7E8A6B")
SKIN_LIT = hx("#97A382")
SKIN_DARK = hx("#5E6A50")
BLOTCH = hx("#6B7A55")
WART = hx("#4E5A42")
MOUTH = hx("#3A2B2E")
TOOTH = hx("#E8E2C4")
NAIL = hx("#C9BE9A")
EYE = hx("#F2E9A8")
# Ron's pyjamas: washed-out maroon stripe, filthy and torn.
RAG = hx("#7A4048")
RAG_DK = hx("#5A2E35")
RAG_LT = hx("#94555E")


# --------------------------------------------------------------------------- rig


# name -> (parent, pivot, cube origin, cube size, rest rotation or None)
#
# Built facing north (-Z), feet on y=0. The stoop lives in the bone rotations rather than
# in the cube positions, so the rest pose can be straightened without re-sculpting.
#
# The legs hang off **root, not body**. Parenting them to the torso -- the obvious reading
# of "body is the trunk" -- means the body's 15-degree stoop rotates the legs with it, and
# the whole creature folds forward about its ankles instead of hunching at the waist. The
# first build did exactly that and read as falling over in side view. `body` therefore
# pivots at the hips and carries only the parts above them.
BONES = [
    ("root",          None,     (0, 0, 0),        None,                 None,          None),
    ("body",          "root",   (0, 13.0, 0),     (-3.5, 13.0, -2.25),  (7, 10, 4.5),  (11, 0, 0)),
    # Head pulled back onto the shoulders. At 7 wide and z=-6 it was as broad as the torso
    # and stood in front of it, so the render was a head with feet.
    ("head",          "body",   (0, 22.5, -0.5),  (-2.75, 22.5, -4.0),  (5.5, 5.5, 6),  (10, 0, 0)),
    ("jaw",           "head",   (0, 23.8, -3.5),  (-2.25, 22.2, -4.0),  (4.5, 1.8, 4),  (13, 0, 0)),
    ("ear_left",      "head",   (2.75, 26.5, -1.0),(2.75, 25.5, -1.5),  (1.8, 2.6, 1),  (0, 0, -30)),
    ("ear_right",     "head",   (-2.75, 26.5, -1.0),(-4.55, 25.5, -1.5),(1.8, 2.6, 1),  (0, 0, 30)),

    ("arm_left",      "body",   (3.6, 21.5, 0),   (3.5, 13.8, -1.15),   (2.3, 7.7, 2.3), (-10, 0, -4)),
    ("forearm_left",  "arm_left", (4.65, 13.8, 0), (3.65, 6.3, -1.0),   (2.0, 7.5, 2.0), (22, 0, 0)),
    ("hand_left",     "forearm_left", (4.65, 6.3, 0), (3.3, 3.2, -0.9), (2.7, 3.1, 1.8), (8, 0, 0)),

    ("arm_right",     "body",   (-3.6, 21.5, 0),  (-5.8, 13.8, -1.15),  (2.3, 7.7, 2.3), (-6, 0, 4)),
    ("forearm_right", "arm_right", (-4.65, 13.8, 0), (-5.65, 6.3, -1.0), (2.0, 7.5, 2.0), (19, 0, 0)),
    ("hand_right",    "forearm_right", (-4.65, 6.3, 0), (-6.0, 3.2, -0.9), (2.7, 3.1, 1.8), (8, 0, 0)),

    ("leg_left",      "root",   (1.9, 13.0, 0),   (0.5, 6.8, -1.4),     (2.8, 6.2, 2.8), (-5, 0, 0)),
    ("shin_left",     "leg_left", (1.9, 6.8, 0),  (0.7, 1.4, -1.2),     (2.4, 5.4, 2.4), (9, 0, 0)),
    ("foot_left",     "shin_left", (1.9, 1.4, 0), (0.4, 0, -3.4),       (3, 1.4, 5.2),  None),

    ("leg_right",     "root",   (-1.9, 13.0, 0),  (-3.3, 6.8, -1.4),    (2.8, 6.2, 2.8), (-5, 0, 0)),
    ("shin_right",    "leg_right", (-1.9, 6.8, 0), (-3.1, 1.4, -1.2),   (2.4, 5.4, 2.4), (9, 0, 0)),
    ("foot_right",    "shin_right", (-1.9, 1.4, 0), (-3.4, 0, -3.4),    (3, 1.4, 5.2),  None),
]


def build_geo(packer):
    bones = []
    for name, parent, pivot, origin, size, rot in BONES:
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rot:
            b["rotation"] = list(rot)
        if size:
            uv = packer.place(name, size)
            b["cubes"] = [{
                "origin": [round(v, 2) for v in origin],
                "size": [round(v, 2) for v in size],
                "uv": list(uv),
            }]
        bones.append(b)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.ghoul",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1.2, 0],
            },
            "bones": bones,
        }],
    }


# --------------------------------------------------------------------------- skin

def paint(packer):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)

    mottle_pal = [BLOTCH, WART, shade(SKIN_MID, 1.08)]

    for name, (uv, size) in packer.placed.items():
        f = faces(uv, size)
        clothed = name == "body"
        seed = sum(ord(c) for c in name)
        for fname, rect in f.items():
            if clothed:
                base = RAG
                pal = [RAG_DK, RAG_LT, shade(RAG, 0.85)]
            else:
                base = SKIN_LIT if fname == "top" else SKIN_DARK if fname == "bottom" else SKIN_MID
                pal = mottle_pal
            mottle(d, rect, base, pal, 26 if not clothed else 34, seed)

        if clothed:
            # Pyjama stripes down the trunk, and a torn hem so it reads as cloth not skin.
            for fname in ("north", "south", "east", "west"):
                x0, y0, w, h = f[fname]
                for sx in range(x0, x0 + w, 3):
                    d.line([(sx, y0), (sx, y0 + h - 1)], fill=RAG_DK)
                for hx_ in range(x0, x0 + w):
                    if (hx_ * 7919) % 3 == 0:
                        d.point((hx_, y0 + h - 1), fill=SKIN_DARK)

        if name == "head":
            x0, y0, w, h = f["north"]
            # Deep-set eyes, high and close: the film's ghoul is all brow and jaw.
            for ex in (x0 + 1, x0 + w - 3):
                d.rectangle([ex, y0 + 2, ex + 1, y0 + 3], fill=MOUTH)
                d.point((ex, y0 + 2), fill=EYE)
                gd.point((ex, y0 + 2), fill=EYE)
            # Brow ridge and a slack upper lip.
            d.line([(x0, y0 + 1), (x0 + w - 1, y0 + 1)], fill=WART)
            d.line([(x0 + 1, y0 + h - 1), (x0 + w - 2, y0 + h - 1)], fill=MOUTH)

        if name == "jaw":
            x0, y0, w, h = f["north"]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=MOUTH)
            # Buck teeth: two long ones proud of the rest.
            for i, tx in enumerate(range(x0 + 1, x0 + w - 1)):
                tall = i in (1, 2)
                d.line([(tx, y0), (tx, y0 + (h - 1 if tall else 0))], fill=TOOTH)
            tx0, ty0, tw, th = f["top"]
            d.rectangle([tx0, ty0, tx0 + tw - 1, ty0 + th - 1], fill=MOUTH)

        if name.startswith("hand"):
            x0, y0, w, h = f["north"]
            for cx in range(x0, x0 + w, 2):
                d.line([(cx, y0 + h - 2), (cx, y0 + h - 1)], fill=NAIL)
        if name.startswith("foot"):
            x0, y0, w, h = f["top"]
            for cx in range(x0, x0 + w, 2):
                d.point((cx, y0), fill=NAIL)

    return img, glow


# --------------------------------------------------------------------------- animation

def build_anim():
    """Idle, walk and a groan. Bone names match the rig; the two clips the entity already
    plays (`idle`, `walk`) keep their identifiers so nothing needs rebinding."""
    def kf(*pairs):
        return {str(t): list(v) for t, v in pairs}

    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.ghoul.idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    # A slow, wet breath: the shoulders lift, the head lolls after it.
                    "body": {"rotation": kf((0.0, (11, 0, 0)), (2.0, (8.5, 0, 0)), (4.0, (11, 0, 0)))},
                    "head": {"rotation": kf((0.0, (10, -3, 2)), (1.3, (12, 4, -2)),
                                            (2.6, (8, -5, 3)), (4.0, (10, -3, 2)))},
                    "jaw": {"rotation": kf((0.0, (14, 0, 0)), (1.6, (20, 0, 0)),
                                           (3.0, (11, 0, 0)), (4.0, (14, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (-10, 0, -4)), (2.0, (-7, 0, -6)), (4.0, (-10, 0, -4)))},
                    "arm_right": {"rotation": kf((0.0, (-6, 0, 4)), (2.0, (-9, 0, 6)), (4.0, (-6, 0, 4)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (2.0, (0, 0.35, 0)), (4.0, (0, 0, 0)))},
                },
            },
            "animation.ghoul.walk": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    # A shamble: short strides, heavy roll, arms swinging loose and late.
                    "leg_left": {"rotation": kf((0.0, (24, 0, 0)), (0.6, (-18, 0, 0)), (1.2, (24, 0, 0)))},
                    "leg_right": {"rotation": kf((0.0, (-18, 0, 0)), (0.6, (24, 0, 0)), (1.2, (-18, 0, 0)))},
                    "shin_left": {"rotation": kf((0.0, (4, 0, 0)), (0.6, (34, 0, 0)), (1.2, (4, 0, 0)))},
                    "shin_right": {"rotation": kf((0.0, (34, 0, 0)), (0.6, (4, 0, 0)), (1.2, (34, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (-26, 0, -6)), (0.6, (2, 0, -9)), (1.2, (-26, 0, -6)))},
                    "arm_right": {"rotation": kf((0.0, (2, 0, 6)), (0.6, (-26, 0, 9)), (1.2, (2, 0, 6)))},
                    "forearm_left": {"rotation": kf((0.0, (30, 0, 0)), (0.6, (18, 0, 0)), (1.2, (30, 0, 0)))},
                    "forearm_right": {"rotation": kf((0.0, (18, 0, 0)), (0.6, (30, 0, 0)), (1.2, (18, 0, 0)))},
                    "body": {"rotation": kf((0.0, (11, 4, 2)), (0.6, (11, -4, -2)), (1.2, (11, 4, 2)))},
                    "root": {"position": kf((0.0, (0, 0, 0)), (0.3, (0, 0.5, 0)),
                                            (0.6, (0, 0, 0)), (0.9, (0, 0.5, 0)), (1.2, (0, 0, 0)))},
                },
            },
            "animation.ghoul.groan": {
                "loop": False,
                "animation_length": 1.6,
                "bones": {
                    # Head back, jaw wide: the noise that gives the attic away.
                    "head": {"rotation": kf((0.0, (10, 0, 0)), (0.4, (-16, 0, 0)),
                                            (1.1, (-12, 0, 0)), (1.6, (10, 0, 0)))},
                    "jaw": {"rotation": kf((0.0, (14, 0, 0)), (0.4, (42, 0, 0)),
                                           (1.1, (38, 0, 0)), (1.6, (14, 0, 0)))},
                    "body": {"rotation": kf((0.0, (11, 0, 0)), (0.5, (5, 0, 0)), (1.6, (11, 0, 0)))},
                    "arm_left": {"rotation": kf((0.0, (-14, 0, -6)), (0.5, (-30, 0, -14)), (1.6, (-14, 0, -6)))},
                    "arm_right": {"rotation": kf((0.0, (-9, 0, 6)), (0.5, (-30, 0, 14)), (1.6, (-9, 0, 6)))},
                },
            },
        },
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    packer = Packer(TEX)
    geo = build_geo(packer)
    used = packer.height_used()
    if used > TEX:
        print(f"ERROR: UV islands need {used}px of a {TEX}px sheet", file=sys.stderr)
        return 1

    if not args.force and not is_regenerable(SKIN, MARKER):
        print("ghoul.png is hand-authored or another tool's; not overwriting. Use --force.")
        return 0

    skin, glow = paint(packer)

    os.makedirs(os.path.dirname(GEO), exist_ok=True)
    with open(GEO, "w", encoding="utf-8") as f:
        json.dump(geo, f, indent=2)
        f.write("\n")
    with open(ANIM, "w", encoding="utf-8") as f:
        json.dump(build_anim(), f, indent=2)
        f.write("\n")
    save(posterise(skin, 18), SKIN, MARKER)
    save(glow, GLOW, MARKER)

    print(f"ghoul rig: {len(geo['minecraft:geometry'][0]['bones'])} bones, "
          f"UV {used}/{TEX}px used, skin {TEX}x{TEX}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
