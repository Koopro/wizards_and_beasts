#!/usr/bin/env python3
"""Renders the armour sets as worn, for judging fit before anything reaches a client.

`beast_preview.py` already rasterises a GeckoLib rig against its own skin, so this does not
re-implement that. What it adds is the *wearer*: an armour model on its own is a hollow
shell with a hole where the head goes, and a robe cannot be judged that way — the whole
question is whether the hem clears the boots and whether the sleeves swallow the arms.

The trick is one sheet. The rasteriser samples a single texture per model, so a mannequin
cannot simply carry its own PNG. Instead the armour's sheet is pasted into the top-left of a
larger preview sheet — leaving every armour UV coordinate valid, unchanged — and the
mannequin's cubes are packed into the empty space below it.

Renders each set from three angles, plus a silhouette pass, because colour flatters a shape:
two quite different outlines both read as "black robe" until it is taken away.

Run from the repo root:  python tools/armor_preview.py --out <dir>
"""

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_preview  # noqa: E402
from artgen_common import hx, mix  # noqa: E402
from boxuv import Packer, faces  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = f"{ASSETS}/geckolib/models/armor"
TEX_DIR = f"{ASSETS}/textures/armor"

SETS = ["student_robe", "auror_robe", "death_eater_robe", "wizard_hat", "death_eater_mask"]
HEAD_SETS = ("wizard_hat", "death_eater_mask")

# Vanilla humanoid boxes, the ones GeoArmorRenderer snaps each armour bone onto.
MANNEQUIN = [
    ("mannequin_head", (0, 24, 0), (-4, 24, -4), (8, 8, 8)),
    ("mannequin_body", (0, 24, 0), (-4, 12, -2), (8, 12, 4)),
    ("mannequin_arm_r", (-5, 22, 0), (-8, 12, -2), (4, 12, 4)),
    ("mannequin_arm_l", (5, 22, 0), (4, 12, -2), (4, 12, 4)),
    ("mannequin_leg_r", (-1.9, 12, 0), (-3.9, 0, -2), (4, 12, 4)),
    ("mannequin_leg_l", (1.9, 12, 0), (-0.1, 0, -2), (4, 12, 4)),
]

SKIN = hx("#B08968")
SKIN_DK = hx("#8A6A50")
SHIRT = hx("#6E6E7A")
SHIRT_DK = hx("#55555F")

# Big enough to hold the armour sheet plus the mannequin packed underneath it. The robes
# moved to 128px sheets when the drape was rebuilt, and at a fixed 128 the mannequin was
# being packed at y=128 — off the bottom edge, sampling nothing, so every preview rendered
# a headless robe. Sized from the sheet it is actually given now.
PREVIEW_SHEET = 256
VIEWS = {"front": (180, 4), "three_quarter": (180 - 34, 18), "side": (180 - 88, 8)}


def build_worn(name, scratch):
    """Merge the armour rig with a mannequin onto one geo + one sheet. Returns the temp id."""
    geo = json.load(open(f"{GEO_DIR}/{name}.geo.json", encoding="utf-8"))["minecraft:geometry"][0]
    armour_tex = Image.open(f"{TEX_DIR}/{name}.png").convert("RGBA")

    sheet = Image.new("RGBA", (PREVIEW_SHEET, PREVIEW_SHEET), (0, 0, 0, 0))
    sheet.paste(armour_tex, (0, 0))
    d = ImageDraw.Draw(sheet)

    # Pack the mannequin below the armour's sheet so no armour UV has to move.
    packer = Packer(PREVIEW_SHEET)
    packer.shelf_y = armour_tex.height
    # The full body even for a hat. Rendering head sets on a half mannequin shrinks their
    # bounding box, and the auto-fit then scales them up — so a hat and a robe photographed
    # side by side came out at two different sizes and could not be compared.
    parts = MANNEQUIN
    for part, _, _, size in parts:
        packer.place(part, size)

    bones = list(geo["bones"])
    for part, pivot, origin, size in parts:
        uv, _ = packer.placed[part]
        bare = part in ("mannequin_head",)
        for fname, rect in faces(uv, size).items():
            x0, y0, w, h = rect
            base = (SKIN if bare else SHIRT) if fname != "bottom" else (SKIN_DK if bare else SHIRT_DK)
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
        if bare:
            # A face, so front and back are told apart at a glance.
            fx, fy, fw, fh = faces(uv, size)["north"]
            for ex in (fx + 2, fx + fw - 3):
                d.point((ex, fy + 3), fill=(30, 30, 36, 255))
            d.line([(fx + 3, fy + 5), (fx + fw - 4, fy + 5)], fill=mix(SKIN, SKIN_DK, 0.8))
        bones.append({
            "name": part,
            "pivot": list(pivot),
            "cubes": [{"origin": list(origin), "size": list(size), "uv": list(uv)}],
        })

    cid = "worn_" + name
    merged = {"format_version": "1.12.0",
              "minecraft:geometry": [{"description": dict(geo["description"],
                                                          texture_width=PREVIEW_SHEET,
                                                          texture_height=PREVIEW_SHEET),
                                      "bones": bones}]}
    with open(os.path.join(scratch, cid + ".geo.json"), "w", encoding="utf-8") as f:
        json.dump(merged, f)
    sheet.save(os.path.join(scratch, cid + ".png"))
    return cid


def build_bare(name, scratch):
    """The armour model on its own, exactly as the file holds it."""
    cid = "bare_" + name
    geo = json.load(open(f"{GEO_DIR}/{name}.geo.json", encoding="utf-8"))
    with open(os.path.join(scratch, cid + ".geo.json"), "w", encoding="utf-8") as f:
        json.dump(geo, f)
    Image.open(f"{TEX_DIR}/{name}.png").convert("RGBA").save(os.path.join(scratch, cid + ".png"))
    return cid


def render(cid, out_path, view, size=320, silhouette=False):
    import math
    yaw, pitch = VIEWS[view]
    beast_preview.YAW = math.radians(yaw)
    beast_preview.PITCH = math.radians(pitch)
    img = (beast_preview.render_silhouette(cid, size=size) if silhouette
           else beast_preview.render_image(cid, size=size))
    if img is None:
        return False
    img.save(out_path)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=320)
    args = ap.parse_args()

    scratch = os.path.join(args.out, "_geo")
    os.makedirs(scratch, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)
    beast_preview.GEO_DIR = scratch
    beast_preview.TEX_DIR = scratch

    made = 0
    for name in SETS:
        worn = build_worn(name, scratch)
        bare = build_bare(name, scratch)
        for view in VIEWS:
            made += render(worn, os.path.join(args.out, f"{name}_worn_{view}.png"), view, args.size)
        made += render(bare, os.path.join(args.out, f"{name}_bare.png"), "three_quarter", args.size)
        made += render(worn, os.path.join(args.out, f"{name}_silhouette.png"),
                       "side", args.size, silhouette=True)

    # The six mask castings share a model, so they only differ under their own texture.
    for variant in range(1, 6):
        cid = f"mask_{variant}"
        geo = json.load(open(f"{GEO_DIR}/death_eater_mask.geo.json", encoding="utf-8"))
        tex = Image.open(f"{TEX_DIR}/death_eater_mask_{variant}.png").convert("RGBA")
        sheet = Image.new("RGBA", (PREVIEW_SHEET, PREVIEW_SHEET), (0, 0, 0, 0))
        sheet.paste(tex, (0, 0))
        d = ImageDraw.Draw(sheet)
        packer = Packer(PREVIEW_SHEET)
        packer.shelf_y = tex.height
        bones = list(geo["minecraft:geometry"][0]["bones"])
        for part, pivot, origin, size in MANNEQUIN[:1]:
            uv = packer.place(part, size)
            for fname, rect in faces(uv, size).items():
                x0, y0, w, h = rect
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1],
                            fill=SKIN if fname != "bottom" else SKIN_DK)
            bones.append({"name": part, "pivot": list(pivot),
                          "cubes": [{"origin": list(origin), "size": list(size), "uv": list(uv)}]})
        merged = {"format_version": "1.12.0",
                  "minecraft:geometry": [{
                      "description": dict(geo["minecraft:geometry"][0]["description"],
                                          texture_width=PREVIEW_SHEET,
                                          texture_height=PREVIEW_SHEET),
                      "bones": bones}]}
        with open(os.path.join(scratch, cid + ".geo.json"), "w", encoding="utf-8") as f:
            json.dump(merged, f)
        sheet.save(os.path.join(scratch, cid + ".png"))
        made += render(cid, os.path.join(args.out, f"death_eater_mask_{variant}.png"),
                       "three_quarter", args.size)

    print(f"{made} renders -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
