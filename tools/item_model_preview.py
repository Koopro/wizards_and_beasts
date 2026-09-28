#!/usr/bin/env python3
"""Contact sheet of the cuboid item models, rendered the way the inventory shows them.

`item_models_3d.py` can tell you a model is well-formed — every box in bounds, every UV on
the sheet — and still hand you a goblet that reads as a lump. The only way to know whether
a shape works is to look at it, and opening 138 models in Blockbench one at a time is not
looking at them, it is auditing them.

So this renders each model offline, from the same angle and with the same texture the GUI
uses, and lays them out in a labelled grid. Silhouette problems, a box lost inside another,
a material that came out flat — all of it is visible in one image.

The renderer is deliberately small: an orthographic camera, the display transform out of the
model's own `display.gui` block, and a painter's algorithm over individual *texels* rather
than whole faces. Sorting texels rather than faces is what lets two boxes interpenetrate
without the nearer one vanishing, and a texel quad is small enough that the sort order
within one is never wrong enough to see.

Run from the repo root:
    python tools/item_model_preview.py                       everything, to tools/audit/
    python tools/item_model_preview.py --only golden_snitch  one item, bigger
"""

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ASSETS = "src/main/resources/assets/wizards_and_beasts"
ITEM_MODEL_DIR = os.path.join(ASSETS, "models", "item")
ITEM_TEX_DIR = os.path.join(ASSETS, "textures", "item")
OUT_DIR = os.path.join("tools", "audit")

BACKGROUND = (30, 30, 36, 255)
LABEL = (188, 192, 200, 255)

# Corner and axes for each face's texture net: where its (0,0) texel sits in model space and
# which way u and v run from there. This is the unwrap `item_models_3d.py` paints into, so
# the preview reads the sheet the same way round the generator wrote it.
FACE_FRAME = {
    "up":    (("x0", "y1", "z0"), (1, 0, 0), (0, 0, 1)),
    "down":  (("x0", "y0", "z1"), (1, 0, 0), (0, 0, -1)),
    "north": (("x1", "y1", "z0"), (-1, 0, 0), (0, -1, 0)),
    "south": (("x0", "y1", "z1"), (1, 0, 0), (0, -1, 0)),
    "west":  (("x0", "y1", "z0"), (0, 0, 1), (0, -1, 0)),
    "east":  (("x1", "y1", "z1"), (0, 0, -1), (0, -1, 0)),
}


def rot_axis(v, axis, degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == "x":
        return (x, y * c - z * s, y * s + z * c)
    if axis == "y":
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def display_transform(v, disp):
    """Vanilla applies the GUI transform as X then Y then Z about the model's centre."""
    rx, ry, rz = disp.get("rotation", [0, 0, 0])
    sx, sy, sz = disp.get("scale", [1, 1, 1])
    tx, ty, tz = disp.get("translation", [0, 0, 0])
    x, y, z = v[0] - 8.0, v[1] - 8.0, v[2] - 8.0
    x, y, z = rot_axis((x, y, z), "z", rz)
    x, y, z = rot_axis((x, y, z), "y", ry)
    x, y, z = rot_axis((x, y, z), "x", rx)
    return (x * sx + tx, y * sy + ty, z * sz + tz)


def element_points(el):
    f, t = el["from"], el["to"]
    named = {"x0": f[0], "x1": t[0], "y0": f[1], "y1": t[1], "z0": f[2], "z1": t[2]}
    rot = el.get("rotation")

    def place(corner, du, dv, u, v):
        p = [named[corner[0]], named[corner[1]], named[corner[2]]]
        for k in range(3):
            p[k] += du[k] * u + dv[k] * v
        if rot:
            o = rot["origin"]
            local = rot_axis((p[0] - o[0], p[1] - o[1], p[2] - o[2]), rot["axis"], rot["angle"])
            p = [local[k] + o[k] for k in range(3)]
        return p

    return named, place


def auto_uv(el, face):
    """The UVs Minecraft derives when a face omits them, per BlockElement#uvsByFace.

    Model builders leave a face's UVs out whenever they match this, so a model read back off
    disk is not the model that was written unless you can reproduce the defaults.
    """
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    return {
        "down":  [x0, 16 - z1, x1, 16 - z0],
        "up":    [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west":  [z0, 16 - y1, z1, 16 - y0],
        "east":  [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


# What `minecraft:block/block` gives a block item in the inventory. Block models inherit their
# display block from a parent this tool does not resolve, so a model with none gets these.
BLOCK_GUI = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}


def render(model, texture, size=192, supersample=3):
    disp = model.get("display", {}).get("gui") or BLOCK_GUI
    canvas = size * supersample
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    tex = texture.convert("RGBA")
    tw, th = tex.size
    px = tex.load()

    quads = []
    for el in model["elements"]:
        named, place = element_points(el)
        for face, spec in el["faces"].items():
            corner, du, dv = FACE_FRAME[face]
            u0, v0, u1, v1 = spec.get("uv") or auto_uv(el, face)
            # UVs are on a 0..16 scale whatever the sheet's resolution; back to texels.
            tu0, tv0 = u0 * tw / 16.0, v0 * th / 16.0
            cols = max(1, int(round((u1 - u0) * tw / 16.0)))
            rows = max(1, int(round((v1 - v0) * th / 16.0)))
            # How far the face runs along its own u and v, in model units.
            ext = {"x": named["x1"] - named["x0"], "y": named["y1"] - named["y0"],
                   "z": named["z1"] - named["z0"]}
            len_u = sum(abs(du[k]) * ext["xyz"[k]] for k in range(3))
            len_v = sum(abs(dv[k]) * ext["xyz"[k]] for k in range(3))
            for r in range(rows):
                for c in range(cols):
                    colour = px[min(tw - 1, int(tu0 + c)), min(th - 1, int(tv0 + r))]
                    if colour[3] == 0:
                        continue
                    pts, depth = [], 0.0
                    for uu, vv in ((c, r), (c + 1, r), (c + 1, r + 1), (c, r + 1)):
                        p = place(corner, du, dv, uu * len_u / cols, vv * len_v / rows)
                        sx, sy, sz = display_transform(p, disp)
                        pts.append((canvas / 2 + sx * canvas / 14.0,
                                    canvas / 2 - sy * canvas / 14.0))
                        depth += sz
                    quads.append((depth / 4.0, pts, colour))

    for _, pts, colour in sorted(quads, key=lambda q: q[0]):
        draw.polygon(pts, fill=colour)
    return img.resize((size, size), Image.LANCZOS)


def sheet(entries, cols=10, cell=112):
    rows = (len(entries) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cell, rows * (cell + 14)), BACKGROUND)
    draw = ImageDraw.Draw(out)
    for index, (name, tile) in enumerate(entries):
        x, y = (index % cols) * cell, (index // cols) * (cell + 14)
        out.alpha_composite(tile.resize((cell, cell), Image.LANCZOS), (x, y))
        label = name if len(name) <= 19 else name[:18] + "…"
        draw.text((x + 3, y + cell + 2), label, fill=LABEL)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--out", default=OUT_DIR)
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    entries = []
    for name in sorted(os.listdir(ITEM_MODEL_DIR)):
        if not name.endswith(".json"):
            continue
        item = name[:-5]
        if only and item not in only:
            continue
        with open(os.path.join(ITEM_MODEL_DIR, name), encoding="utf-8") as fh:
            model = json.load(fh)
        if model.get("credit") != "generated by tools/item_models_3d.py":
            continue
        tex_id = model["textures"]["0"].split(":", 1)[1].split("/")[-1]
        tex_path = os.path.join(ITEM_TEX_DIR, tex_id + ".png")
        with Image.open(tex_path) as tex:
            entries.append((item, render(model, tex)))

    os.makedirs(args.out, exist_ok=True)
    if only and len(entries) == 1:
        path = os.path.join(args.out, f"item_model_{entries[0][0]}.png")
        entries[0][1].resize((384, 384), Image.NEAREST).save(path)
    else:
        path = os.path.join(args.out, "item_models_3d.png")
        sheet(entries).save(path)
    print(f"rendered {len(entries)} models -> {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
