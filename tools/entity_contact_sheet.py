#!/usr/bin/env python3
"""Contact sheets from an EntityShowcaseCapture run: one row per creature, one column per view.

    python tools/entity_contact_sheet.py runs/client2/screenshots/<dir> build/entity-art/sheets \
        [--views front,side,back,far,night] [--per 8] [--cell 360]

Each shot is centre-cropped to a square (the harness frames the creature in the middle) and scaled
to `cell` pixels; the creature id is printed at the left of its row.
"""

import argparse
import glob
import os

from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--views", default="front,side,back,far,night")
    ap.add_argument("--per", type=int, default=8)
    ap.add_argument("--cell", type=int, default=360)
    ap.add_argument("--crop", type=float, default=0.8, help="fraction of the frame height kept")
    ap.add_argument("--far-crop", type=float, default=0.3, help="crop for the far view")
    ap.add_argument("--only", default="", help="comma list of ids")
    a = ap.parse_args()
    views = a.views.split(",")
    ids = sorted({os.path.basename(p).split("__")[0] for p in glob.glob(f"{a.src}/*__*.png")})
    if a.only:
        ids = [i for i in ids if i in a.only.split(",")]
    os.makedirs(a.out, exist_ok=True)
    label_w = 150
    for page in range(0, len(ids), a.per):
        chunk = ids[page:page + a.per]
        sheet = Image.new("RGB", (label_w + a.cell * len(views), a.cell * len(chunk)), (24, 24, 24))
        d = ImageDraw.Draw(sheet)
        for r, cid in enumerate(chunk):
            d.text((6, r * a.cell + 6), cid, fill=(255, 255, 255))
            for c, v in enumerate(views):
                p = f"{a.src}/{cid}__{v}.png"
                if not os.path.exists(p):
                    continue
                im = Image.open(p).convert("RGB")
                w, h = im.size
                side = int(h * (a.far_crop if v == "far" else a.crop))
                box = ((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2)
                im = im.crop(box).resize((a.cell, a.cell), Image.LANCZOS)
                sheet.paste(im, (label_w + c * a.cell, r * a.cell))
                if r == 0:
                    d.text((label_w + c * a.cell + 4, 2), v, fill=(255, 255, 0))
        path = f"{a.out}/sheet_{page // a.per:02d}.png"
        sheet.save(path)
        print(path)


if __name__ == "__main__":
    main()
