#!/usr/bin/env python3
"""Bestiary portraits rendered from the creatures themselves.

`tools/bestiary_placeholders.py` drew a 32px body-plan shape per entry — a blob that said
"quadruped" but not *which* quadruped, so the Bestiary listed 94 near-identical smudges.
Now that every beast has a real skin and `tools/beast_preview.py` can rasterise a rig
offline, the portrait can just be the creature: same model, same texture, same 3/4 angle
the mob is seen from in-game.

That is the whole point of doing it this way rather than drawing 94 icons. A portrait
cannot drift from the mob it depicts, because it *is* the mob — repaint a skin, re-run
this, and the Bestiary is correct again.

Two outputs per entry:

  - `icons/<id>.png`      the lit render, for a discovered entry
  - `silhouettes/<id>.png` the same render's alpha filled with ink, for an undiscovered
                           one. Deriving it from the render rather than drawing it
                           separately is what guarantees the reveal lines up exactly.

Entries whose creature has no rig keep whatever art they already have.

Run from the repo root:  python tools/bestiary_portraits.py [--force] [--only id,id]
"""

import argparse
import glob
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_preview as bp  # noqa: E402
from artgen_common import is_regenerable, marker, pixelate, posterise, save  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
ICON_DIR = os.path.join(ASSETS, "textures", "bestiary", "icons")
SIL_DIR = os.path.join(ASSETS, "textures", "bestiary", "silhouettes")
ENTRY_DIR = "src/main/resources/data/wizards_and_beasts/bestiary/entries"
MARKER = marker("bestiary_portraits.py")

OUT_SIZE = 32
RENDER_SIZE = 320       # rendered large, then downsampled — free antialiasing
PAD = 2                 # texels of breathing room inside the 32px tile

INK = (24, 22, 32)



def pixelate_rect(img, w, h):
    """`artgen_common.pixelate` for a non-square target."""
    src = img.convert("RGBA")
    fx, fy = src.width // w, src.height // h
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    cells = fx * fy
    for y in range(h):
        for x in range(w):
            r = g = b = covered = 0
            for sy in range(y * fy, (y + 1) * fy):
                for sx in range(x * fx, (x + 1) * fx):
                    pr, pg, pb, pa = sp[sx, sy]
                    if pa < 128:
                        continue
                    covered += 1
                    r += pr; g += pg; b += pb
            if covered * 2 < cells:
                continue
            op[x, y] = (r // covered, g // covered, b // covered, 255)
    return out


def rig_is_renderable(creature_id):
    """True when the rig's UVs actually address its texture.

    Two rigs fail this and must keep their existing art rather than be rendered into a
    smear: one whose declared `texture_width/height` disagrees with the PNG it ships (the
    UVs then address a different image than the one being sampled), and one whose cubes
    all sit at the same UV because it was never unwrapped. Both produce a flat slab that
    is a worse portrait than the placeholder it would replace.
    """
    geo_path = os.path.join(bp.GEO_DIR, creature_id + ".geo.json")
    tex_path = os.path.join(bp.TEX_DIR, creature_id + ".png")
    if not (os.path.exists(geo_path) and os.path.exists(tex_path)):
        return False
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    declared = (geo["description"].get("texture_width"), geo["description"].get("texture_height"))
    with Image.open(tex_path) as im:
        if declared != im.size:
            return False
    uvs = [tuple(c["uv"]) for b in geo["bones"] for c in b.get("cubes", [])
           if isinstance(c.get("uv"), list)]
    return bool(uvs) and len(set(uvs)) > 1


def portrait(creature_id):
    """The creature, cropped to its own silhouette and fitted to the icon tile."""
    img = bp.render_image(creature_id, size=RENDER_SIZE, margin=8)
    if img is None:
        return None
    box = img.getbbox()
    if box is None:
        return None
    img = img.crop(box)

    # Fit the longest side, preserving aspect: a serpent stays a serpent rather than
    # being squashed square.
    # Downsample the long side by a whole factor and take hard pixel edges, rather than
    # scaling to fit with a smooth filter — the same softening that made the item sprites
    # read as blurry applies here, and a 32px portrait has no pixels to spare.
    inner = OUT_SIZE - 2 * PAD
    factor = max(1, int(math.ceil(max(img.width, img.height) / inner)))
    padded = Image.new("RGBA", (img.width + (-img.width) % factor,
                                img.height + (-img.height) % factor), (0, 0, 0, 0))
    padded.alpha_composite(img)
    img = pixelate_rect(padded, padded.width // factor, padded.height // factor)
    size = img.size

    tile = Image.new("RGBA", (OUT_SIZE, OUT_SIZE), (0, 0, 0, 0))
    tile.alpha_composite(img, ((OUT_SIZE - size[0]) // 2, (OUT_SIZE - size[1]) // 2))
    return tile


def silhouette_of(icon):
    """Ink in the icon's own alpha, so the undiscovered shape matches the reveal exactly."""
    out = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    ink = Image.new("RGBA", icon.size, INK + (255,))
    out.paste(ink, (0, 0), icon.getchannel("A"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    # The work list is the bestiary *definitions*, not the icons already on disk. Reading it
    # from ICON_DIR meant the tool could only ever regenerate portraits that already existed,
    # so a creature that had never had one could never get one -- 12 of them (bundimun,
    # ghoul, golden_snidget, granian, hidebehind, horned_serpent, lobalug, matagot,
    # pukwudgie, rougarou, toad, yeti) shipped with both files missing while every entry
    # JSON referenced them. The definitions are the authority on what needs art.
    entries = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(ENTRY_DIR, "*.json")))
    written, no_rig, skipped = [], [], []

    for creature_id in entries:
        if only and creature_id not in only:
            continue
        icon_path = os.path.join(ICON_DIR, creature_id + ".png")
        sil_path = os.path.join(SIL_DIR, creature_id + ".png")
        if not args.force and not is_regenerable(icon_path, MARKER):
            skipped.append(creature_id)
            continue
        if not rig_is_renderable(creature_id):
            no_rig.append(creature_id)
            continue
        icon = portrait(creature_id)
        if icon is None:
            no_rig.append(creature_id)
            continue
        save(posterise(icon, 14), icon_path, MARKER)
        save(silhouette_of(icon), sil_path, MARKER)
        written.append(creature_id)

    print(f"rendered {len(written)} portraits (+ matching silhouettes)")
    if no_rig:
        print(f"no usable rig, art left as-is ({len(no_rig)}): {', '.join(no_rig)}")
    if skipped:
        print(f"skipped {len(skipped)} hand-authored")
    return 0


if __name__ == "__main__":
    sys.exit(main())
