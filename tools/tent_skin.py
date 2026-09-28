#!/usr/bin/env python3
"""Unwrap and paint the tent block rigs.

`tent_canvas` and `tent_grand` are GeckoLib *block* models, so every earlier pass missed
them: `beast_skins.py` globs the entity model folder and `block_textures.py` only handles
flat block sprites. They were still carrying their generator's placeholder — a three-colour
sheet whose marker colour is literal magenta — with all nine and sixteen cubes sharing one
UV slot. Placed in the world, a tent was a magenta box.

Same fix as everywhere else, reusing the same machinery: `beast_skins.shelf_pack` lays each
cube's box-UV net onto a right-sized atlas and rewrites the model, then faces are painted
by their bone's role. Roles come from the bone names the rigs already use, so canvas is
canvas, guylines are rope and the banner is cloth.

Run from the repo root:  python tools/tent_skin.py [--force]
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

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "block")
TEX_DIR = os.path.join(ASSETS, "textures", "block")
MARKER = marker("tent_skin.py")

# A wizarding tent is canvas outside and rather grander inside; the outside is what this
# paints, so it stays honest camping gear — weathered duck canvas, hemp guys, oak pegs.
ROLES = {
    "canvas_body": (hx("#9a8a63")[:3], "CLOTH"),
    "entrance_flap": (hx("#7d6f4e")[:3], "CLOTH"),
    "guylines_pegs": (hx("#6b5a3a")[:3], "BARK"),
    "chimney": (hx("#6b5148")[:3], "STONE"),
    "flagpole": (hx("#7a5a34")[:3], "BARK"),
    "banner": (hx("#8f2f3a")[:3], "CLOTH"),
    "turret": (hx("#8f815c")[:3], "CLOTH"),
    "fx": (hx("#c9b072")[:3], "SMOKE"),
}
DEFAULT = (hx("#8f815c")[:3], "CLOTH")


def role_for(bone_name):
    if bone_name in ROLES:
        return ROLES[bone_name]
    for key, value in ROLES.items():
        if bone_name.startswith(key):
            return value
    return DEFAULT


def paint(model_id, force):
    geo_path = os.path.join(GEO_DIR, model_id + ".geo.json")
    tex_path = os.path.join(TEX_DIR, model_id + ".png")
    if not os.path.exists(geo_path):
        return None
    if not force and not is_regenerable(tex_path, MARKER):
        return None

    bs.unwrap_model(geo_path)
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    width = int(geo["description"]["texture_width"])
    height = int(geo["description"]["texture_height"])

    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()

    for bone in geo["bones"]:
        base, style = role_for(bone["name"])
        for index, cube in enumerate(bone.get("cubes", [])):
            uv = cube.get("uv")
            if not isinstance(uv, list):
                continue
            u, v = float(uv[0]), float(uv[1])
            w, h, d = (float(s) for s in cube["size"])
            seed = (sum(ord(c) for c in bone["name"]) + index * 41) & 0xFFFF
            for face, (fx0, fy0, fx1, fy1) in bs.box_faces(u, v, w, h, d).items():
                x0, y0 = int(math.floor(fx0)), int(math.floor(fy0))
                x1, y1 = int(math.ceil(fx1)), int(math.ceil(fy1))
                fw, fh = max(1, x1 - x0), max(1, y1 - y0)
                shade = bs.FACE_SHADE[face]
                for ly in range(fh):
                    for lx in range(fw):
                        f = shade * bs.pattern_factor(style, lx, ly, fw, fh, seed)
                        if lx == 0 or ly == 0 or lx == fw - 1 or ly == fh - 1:
                            f *= 0.9
                        col = bs.lit(base, f)
                        x, y = x0 + lx, y0 + ly
                        if 0 <= x < width and 0 <= y < height:
                            px[x, y] = (col[0], col[1], col[2], 255)

    save(posterise(img, 14), tex_path, MARKER)
    return width, height


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    for model_id in ("tent_canvas", "tent_grand"):
        result = paint(model_id, args.force)
        print(f"{model_id}: " + (f"unwrapped and painted at {result[0]}x{result[1]}"
                                 if result else "skipped"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
