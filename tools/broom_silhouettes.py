"""The black-silhouette acceptance gate for the broom rig.

Renders every variant of every slot as a flat black shape at 64px, other slots held at the
default silhouette. If two variants in a slot are indistinguishable here, they are not
variants — that is the Rev 1 failure this test exists to catch.
"""
import json
import math
import os
import shutil
import sys

sys.path.insert(0, os.path.abspath("tools"))
import beast_preview as bp  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
WORK = os.path.join(OUT, "_sil")
os.makedirs(WORK, exist_ok=True)

GEO = "src/main/resources/assets/wizards_and_beasts/geckolib/models/entity/broom.geo.json"
TEX = "src/main/resources/assets/wizards_and_beasts/textures/entity/broom.png"

SLOTS = ["shaft", "tail_cap", "binding", "bristles", "footstrap", "accent"]
DEFAULT = {"shaft": "plain", "tail_cap": "plain", "binding": "cord",
           "bristles": "ragged", "footstrap": "leather", "accent": "none"}

doc = json.load(open(GEO, encoding="utf-8"))
BONES = doc["minecraft:geometry"][0]["bones"]


def variants(slot):
    """Chain roots only — a shaft variant's _mid/_grip are its children, not siblings."""
    pre = slot + "_"
    return [b["name"][len(pre):] for b in BONES
            if b["name"].startswith(pre) and b.get("parent") == slot]


def descendants(root):
    keep = {root}
    changed = True
    while changed:
        changed = False
        for b in BONES:
            if b.get("parent") in keep and b["name"] not in keep:
                keep.add(b["name"])
                changed = True
    return keep


def write(cid, selection):
    keep = set()
    for slot in SLOTS:
        keep |= descendants(slot + "_" + selection[slot])
    out = json.loads(json.dumps(doc))
    out["minecraft:geometry"][0]["bones"] = [
        b for b in BONES
        if not any(b["name"].startswith(s + "_") for s in SLOTS) or b["name"] in keep]
    json.dump(out, open(os.path.join(WORK, cid + ".geo.json"), "w", encoding="utf-8"))
    shutil.copyfile(TEX, os.path.join(WORK, cid + ".png"))


bp.GEO_DIR = WORK
bp.TEX_DIR = WORK
# Two views. A flattened blade and a round teardrop have the same side profile and differ
# entirely in plan, so a single side-on sheet cannot tell them apart no matter how far the
# shapes are pushed — the distinction is in the axis that view collapses.
VIEWS = (("side", 270, 0.0), ("top", 270, 89.0))

CELL = 64
rows = []
for slot in SLOTS:
    cells = []
    for v in variants(slot):
        sel = dict(DEFAULT)
        sel[slot] = v
        cid = "sil_%s_%s" % (slot, v)
        write(cid, sel)
        shots = []
        for _, yaw, pitch in VIEWS:
            bp.YAW = math.radians(yaw)
            bp.PITCH = math.radians(pitch)
            shots.append(bp.render_silhouette(cid, size=CELL, margin=2))
        cells.append((v, shots))
    rows.append((slot, cells))

pad = 8
label_h = 12
width = pad + max(len(c) for _, c in rows) * (CELL + pad)
height = pad + len(rows) * (CELL + CELL // 2 + label_h + pad)
sheet = Image.new("RGB", (width + 90, height), (255, 255, 255))
d = ImageDraw.Draw(sheet)

y = pad
for slot, cells in rows:
    d.text((4, y + CELL // 2), slot.upper(), fill=(0, 0, 0))
    x = 88
    for name, shots in cells:
        for k, img in enumerate(shots):
            if img is not None:
                sheet.paste(img, (x, y + k * (CELL // 2)), img)
        d.text((x, y + CELL), name, fill=(90, 90, 90))
        x += CELL + pad
    y += CELL + CELL // 2 + label_h + pad

sheet.save(os.path.join(OUT, "broom_silhouettes.png"))
shutil.rmtree(WORK, ignore_errors=True)
print("wrote broom_silhouettes.png  (%dpx cells)" % CELL)
for slot, cells in rows:
    print("  %-10s %d variants: %s" % (slot, len(cells), [n for n, _ in cells]))
