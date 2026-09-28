#!/usr/bin/env python3
"""Contact sheet of the block textures, tiled the way a wall of them actually looks.

A block texture cannot be judged one tile at a time. The two things that decide whether it
reads as Minecraft — whether it seams, and whether its detail survives being repeated across
a wall — only show up when it is tiled, so every entry here is drawn 2x2.

Animated strips (a 16x128 candle, a 16x8192 fire) are sampled at their first frame; anything
that is not square-and-tileable is drawn once at its own size.

Run from the repo root:
    python tools/block_texture_sheet.py                    everything
    python tools/block_texture_sheet.py --filter hogwarts  just the ones whose name matches
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

BLOCK_DIR = "src/main/resources/assets/wizards_and_beasts/textures/block"
OUT_DIR = os.path.join("tools", "audit")

BACKGROUND = (30, 30, 36, 255)
LABEL = (188, 192, 200, 255)


def first_frame(img):
    """Animated strips are N square frames stacked vertically; take the top one."""
    w, h = img.size
    if h > w and h % w == 0:
        return img.crop((0, 0, w, w))
    return img


def tile(img, cell):
    """2x2, so a seam has an edge to fail across."""
    src = first_frame(img.convert("RGBA"))
    if src.width != src.height:
        out = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
        scale = min(cell / src.width, cell / src.height)
        fit = src.resize((max(1, int(src.width * scale)), max(1, int(src.height * scale))),
                         Image.NEAREST)
        out.alpha_composite(fit, ((cell - fit.width) // 2, (cell - fit.height) // 2))
        return out
    half = cell // 2
    one = src.resize((half, half), Image.NEAREST)
    out = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
    for x in (0, half):
        for y in (0, half):
            out.alpha_composite(one, (x, y))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--filter", default="", help="substring the name must contain")
    ap.add_argument("--cell", type=int, default=96)
    ap.add_argument("--cols", type=int, default=10)
    ap.add_argument("--out", default=OUT_DIR)
    args = ap.parse_args()

    names = [n for n in sorted(os.listdir(BLOCK_DIR))
             if n.endswith(".png") and args.filter in n]
    if not names:
        raise SystemExit("nothing matched")

    cell, cols = args.cell, args.cols
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        x, y = (index % cols) * cell, (index // cols) * (cell + 14)
        with Image.open(os.path.join(BLOCK_DIR, name)) as img:
            sheet.alpha_composite(tile(img, cell), (x, y))
        label = name[:-4]
        draw.text((x + 2, y + cell + 2), label if len(label) <= 17 else label[:16] + "…",
                  fill=LABEL)

    os.makedirs(args.out, exist_ok=True)
    suffix = f"_{args.filter}" if args.filter else ""
    path = os.path.join(args.out, f"block_textures{suffix}.png")
    sheet.save(path)
    print(f"{len(names)} textures -> {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
