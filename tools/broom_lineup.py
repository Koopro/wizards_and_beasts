"""Renders the seven shipped brooms side by side, each in its own colours.

The acceptance question for the per-broom pass is not "does each broom have art" but "can a
player tell a Cleansweep from a Firebolt across a pitch". That is answered by seeing them
next to each other at the size they actually read at, which is what this draws.

`broom_silhouettes.py` is the companion gate and answers a different question: whether two
variants of one *slot* differ in outline. Both are needed — outline survives distance and
colour survives motion blur, and the rev-2 brooms passed the first while failing the second.

Reads the shipped broom definitions, so a broom added to the datapack shows up here with no
change to this file.

Run from the repo root:  python tools/broom_lineup.py [outdir]
"""
import json
import math
import os
import shutil
import sys

sys.path.insert(0, os.path.abspath("tools"))
import beast_preview as bp  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO = f"{ASSETS}/geckolib/models/entity/broom.geo.json"
TEX_DIR = f"{ASSETS}/textures/entity"
DEFS = "src/main/resources/data/wizards_and_beasts/broom_definitions"

OUT = sys.argv[1] if len(sys.argv) > 1 else "docs/broom"
WORK = os.path.join(OUT, "_lineup")

SLOTS = ["shaft", "tail_cap", "binding", "bristles", "footstrap", "accent"]

doc = json.load(open(GEO, encoding="utf-8"))
BONES = doc["minecraft:geometry"][0]["bones"]


def descendants(root):
    """A variant plus its chain children — a shaft variant is three bones, not one."""
    keep = {root}
    changed = True
    while changed:
        changed = False
        for bone in BONES:
            if bone.get("parent") in keep and bone["name"] not in keep:
                keep.add(bone["name"])
                changed = True
    return keep


def assemble(broom_id, definition):
    """Write a one-broom geo and its sheet into the work dir, the way the renderer would.

    The renderer hides the unselected variants at draw time; here they are dropped from the
    file, which is the same picture by a cheaper route.
    """
    # A broom that names its own model is previewed from THAT FILE, not from a fresh filter of the
    # master rig. Re-deriving it here would make the preview agree with itself while disagreeing
    # with what actually ships -- which is the one thing a preview must never do.
    model = definition.get("model")
    if model:
        src_geo = f"{ASSETS}/geckolib/models/entity/{model.split(':')[-1]}.geo.json"
        out = json.load(open(src_geo, encoding="utf-8"))
    else:
        slots = definition.get("model_slots", {})
        keep = set()
        for slot in SLOTS:
            selected = slots.get(slot)
            if selected:
                keep |= descendants(slot + "_" + selected.split(":")[-1])
        out = json.loads(json.dumps(doc))
        out["minecraft:geometry"][0]["bones"] = [
            b for b in BONES
            if not any(b["name"].startswith(s + "_") for s in SLOTS) or b["name"] in keep]
    json.dump(out, open(os.path.join(WORK, broom_id + ".geo.json"), "w", encoding="utf-8"))

    # `texture` takes either form: a full path rooted at textures/, or a GeckoLib subpath under
    # textures/entity/. Same two forms BroomAssets accepts, for the same reason.
    sheet = definition.get("texture") or definition.get("entity_texture")
    if not sheet:
        src = f"{TEX_DIR}/broom.png"
    else:
        path = sheet.split(":")[-1]
        src = f"{ASSETS}/{path}" if path.startswith("textures/") else f"{TEX_DIR}/{path}.png"
    shutil.copyfile(src, os.path.join(WORK, broom_id + ".png"))


def tinted(image, wood_tint):
    """Multiply in a broom's wood_tint, the way BroomRenderer.getRenderColor does.

    Without this the generic broom previews grey, because its sheet is painted greyscale on
    purpose and the colour only arrives at render time — the preview would be showing a
    broom nobody ever sees.
    """
    if image is None or not wood_tint:
        return image
    body = wood_tint.lstrip("#")
    body = body[2:] if len(body) == 8 else body
    r, g, b = (int(body[i:i + 2], 16) for i in (0, 2, 4))
    image = image.copy()  # render_image hands back a read-only view
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            cr, cg, cb, ca = px[x, y]
            if ca:
                px[x, y] = (cr * r // 255, cg * g // 255, cb * b // 255, ca)
    return image


def main():
    os.makedirs(WORK, exist_ok=True)
    bp.GEO_DIR = WORK
    bp.TEX_DIR = WORK
    # Near side-on. A broom is a long thin thing and its whole identity is a length
    # profile; a three-quarter view foreshortens the shaft into the bundle and hides it.
    bp.YAW = math.radians(272)
    bp.PITCH = math.radians(8)

    brooms = []
    for name in sorted(os.listdir(DEFS)):
        if not name.endswith(".json"):
            continue
        broom_id = name[:-5]
        definition = json.load(open(os.path.join(DEFS, name), encoding="utf-8"))
        assemble(broom_id, definition)
        brooms.append((broom_id,
                       tinted(bp.render_image(broom_id, size=260, margin=6),
                              definition.get("wood_tint"))))

    cell_w, cell_h, pad, label = 260, 150, 10, 14
    sheet = Image.new("RGB", (pad + len(brooms) * (cell_w + pad),
                              pad + cell_h + label + pad), (232, 232, 232))
    draw = ImageDraw.Draw(sheet)
    x = pad
    for broom_id, image in brooms:
        if image is not None:
            sheet.paste(image, (x, pad + (cell_h - image.height) // 2), image)
        draw.text((x, pad + cell_h), broom_id, fill=(40, 40, 40))
        x += cell_w + pad

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "broom_lineup.png")
    sheet.save(path)
    shutil.rmtree(WORK, ignore_errors=True)
    print("wrote", path, "-", len(brooms), "brooms")


if __name__ == "__main__":
    main()
