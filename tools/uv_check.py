#!/usr/bin/env python3
"""Verify that a rig's texture actually covers the texels GeckoLib will sample.

This exists because "the texture has holes on the model" is invisible in every other check. The
packer packs, the painter paints the rectangles the packer handed out, the geo declares the same
sizes, `rig_is_renderable` passes, the offline previewer looks fine — and the mob still renders with
transparent gashes, because the *game* addresses the sheet by a different rule than the tools do.

The rule, from `BakedModelFactory.buildQuad` (geckolib 5.4.5):

    Vec3 uvSizeVec = new Vec3(Math.floor(size[0]), Math.floor(size[1]), Math.floor(size[2]));

i.e. box UV is laid out from the **floored** cube size, with these per-face rects (u, v, w, h):

    up     (u + z,         v,     x,  z)
    down   (u + z + x,     v,     x,  z)
    west   (u,             v + z, z,  y)
    north  (u + z,         v + z, x,  y)
    east   (u + z + x,     v + z, z,  y)
    south  (u + z + x + z, v + z, x,  y)

`boxuv.py` rounds sizes *up* instead, so any fractional size shifts every face by a texel and the
top/bottom faces slide into the island corner the box layout never fills — which is transparent.
A size below 1.0 floors to zero and collapses the whole cube to a zero-area UV.

Run from the repo root:
    python tools/uv_check.py goblin_teller goblin/clerk
    python tools/uv_check.py --all
"""

import argparse
import glob
import json
import math
import os
import sys

from PIL import Image

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "entity")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")


def face_rects(uv, size):
    """The six rects GeckoLib samples, in texels, using its own floor()ed sizes."""
    u, v = uv
    x, y, z = (math.floor(s) for s in size)
    return {
        "up": (u + z, v, x, z),
        "down": (u + z + x, v, x, z),
        "west": (u, v + z, z, y),
        "north": (u + z, v + z, x, y),
        "east": (u + z + x, v + z, z, y),
        "south": (u + z + x + z, v + z, x, y),
    }


def check(cid):
    geo_path = os.path.join(GEO_DIR, cid + ".geo.json")
    tex_path = os.path.join(TEX_DIR, cid + ".png")
    if not (os.path.exists(geo_path) and os.path.exists(tex_path)):
        return [f"{cid}: missing geo or texture"]

    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    declared = (geo["description"].get("texture_width"), geo["description"].get("texture_height"))
    problems = []
    with Image.open(tex_path) as im:
        img = im.convert("RGBA")
        if declared != img.size:
            problems.append(f"{cid}: geo declares {declared} but the PNG is {img.size}")
            return problems
        alpha = img.split()[3].load()
        w, h = img.size

        for bone in geo["bones"]:
            for cube in bone.get("cubes", []):
                size = cube["size"]
                uv = cube.get("uv")
                if not isinstance(uv, list):
                    continue
                key = f"{bone['name']}[{','.join(str(round(s, 2)) for s in size)}]"

                degenerate = [ax for ax, s in zip("xyz", size) if math.floor(s) < 1]
                if degenerate:
                    problems.append(
                        f"{cid}: {key} floors to zero on {'/'.join(degenerate)} — "
                        f"GeckoLib maps the whole cube to a zero-area UV")
                    continue
                fractional = [ax for ax, s in zip("xyz", size) if abs(s - round(s)) > 1e-6]
                if fractional:
                    problems.append(
                        f"{cid}: {key} has a fractional size on {'/'.join(fractional)} — "
                        f"the game floors it and samples a texel off from where it was painted")

                # A membrane — one unit thick, eight or more long — is the one shape allowed to
                # carry transparent texels: the scalloped trailing edge of a wing, a ragged fin.
                # GeckoLib draws entities cutout, so alpha 0 there is a deliberate hole, not a tear.
                membrane = min(size) == 1 and max(size) >= 8
                for fname, (fx, fy, fw, fh) in face_rects(uv, size).items():
                    if fx < 0 or fy < 0 or fx + fw > w or fy + fh > h:
                        problems.append(f"{cid}: {key} face {fname} samples outside the sheet")
                        continue
                    holes = sum(1 for yy in range(fy, fy + fh) for xx in range(fx, fx + fw)
                                if alpha[xx, yy] == 0)
                    if holes and not membrane:
                        problems.append(
                            f"{cid}: {key} face {fname} samples {holes} transparent "
                            f"texel(s) at ({fx},{fy}) {fw}x{fh}")
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()

    ids = list(args.ids)
    if args.all:
        for path in sorted(glob.glob(os.path.join(GEO_DIR, "**", "*.geo.json"), recursive=True)):
            ids.append(os.path.relpath(path, GEO_DIR)[:-len(".geo.json")].replace(os.sep, "/"))
    if not ids:
        ap.error("pass rig ids or --all")

    bad = 0
    for cid in ids:
        problems = check(cid)
        if problems:
            bad += 1
            for line in problems[:12]:
                print(line)
            if len(problems) > 12:
                print(f"  … and {len(problems) - 12} more on {cid}")
        else:
            print(f"{cid}: ok")
    print(f"\n{len(ids) - bad}/{len(ids)} rigs clean")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
