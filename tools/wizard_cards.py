#!/usr/bin/env python3
"""One card design per famous wizard.

Every Chocolate Frog used to hand out the same picture with a different name attached,
which is the one thing a collectible cannot be. This paints a distinct portrait for each
entry in `card/WizardCards.java` and wires it up:

  - `textures/item/card/<id>.png`   128x128: card face, patterned back, edge swatch
  - `models/item/card/<id>.json`    a 10 x 15 x 1 slab with those three regions on it
  - `items/famous_wizard_card.json` a `minecraft:select` over the `wizard_card_id`
                                    component, one case per id, blank card as fallback

The roster here must match `WizardCards.ALL` exactly — ids, order and rarity.
`WizardCardAssetsTest` fails the build if it drifts.

Portraits are built from a trait table rather than from a hash of the id. A random face
per wizard would be different but not *recognisable*, and half the pleasure of the cards
is knowing at a glance which one you pulled: Dumbledore is half-moon glasses over a long
silver beard, Lockhart is teeth and golden waves, Morgana is a black hood.

Run from the repo root:  python tools/wizard_cards.py [--force] [--preview PATH]
"""

import argparse
import json
import os
import sys
import zlib

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, mix, posterise, save  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
TEX_DIR = os.path.join(ASSETS, "textures", "item", "card")
MODEL_DIR = os.path.join(ASSETS, "models", "item", "card")
ITEMS_DIR = os.path.join(ASSETS, "items")
BLANK_TEX = os.path.join(ASSETS, "textures", "item", "famous_wizard_card.png")
BLANK_MODEL = os.path.join(ASSETS, "models", "item", "famous_wizard_card.json")
MARKER = marker("wizard_cards.py")

MODID = "wizards_and_beasts"
COMPONENT = MODID + ":wizard_card_id"

# --------------------------------------------------------------------------- sheet layout
#
# 128x128, with UVs on the model's 0..16 scale (so one texel is 0.125 uv).
#
#   face  px (0,0)-(80,120)    uv (0,0)-(10,15)   the 10x15 front of the card
#   back  px (88,0)-(120,32)   uv (11,0)-(15,4)   stretched over the reverse
#   edge  px (88,40)-(96,48)   uv (11,5)-(12,6)   the four thin sides
SHEET = 128
FACE_W, FACE_H = 80, 120
BACK_BOX = (88, 0, 120, 32)
EDGE_BOX = (88, 40, 96, 48)

INK = hx("141018")
PARCHMENT = hx("d8c9a0")
PARCHMENT_DARK = hx("b3a077")

RARITY_FOIL = {
    "COMMON": hx("8e8778"),
    "UNCOMMON": hx("5f8f52"),
    "RARE": hx("3f86a8"),
    "LEGENDARY": hx("c9992e"),
}
RARITY_PIPS = {"COMMON": 1, "UNCOMMON": 2, "RARE": 3, "LEGENDARY": 4}


def t(id_, rarity, plate, **kw):
    """One roster row. Defaults keep the table to the traits that actually differ."""
    row = {
        "id": id_, "rarity": rarity, "plate": plate,
        "skin": "e8c49a", "hair": "3a2b1a", "hair_style": "short", "beard": "none",
        "hat": "none", "robe": "4a4a5a", "trim": "b0a888", "acc": "none",
        "bg": "2a2438",
    }
    row.update(kw)
    return row


# Order, ids and rarities mirror WizardCards.ALL.
ROSTER = [
    # --- common ------------------------------------------------------------------
    t("albus_dumbledore", "COMMON", "DUMBLEDORE", skin="f0d0b0", hair="e9e9e9",
      hair_style="long", beard="long", hat="pointed", robe="5b3a8c", trim="d4af37",
      acc="halfmoon", bg="2c2246"),
    t("gilderoy_lockhart", "COMMON", "LOCKHART", skin="f2d3ae", hair="e8c463",
      hair_style="wavy", robe="3aa8a0", trim="f0e6c8", acc="smile", bg="1f3a40"),
    t("hengist_of_woodcroft", "COMMON", "HENGIST", skin="e0b98e", hair="6b4a2b",
      beard="short", hat="wide", robe="6b4a2b", trim="c2a05a", bg="24301f"),
    t("alberic_grunnion", "COMMON", "GRUNNION", skin="e8c49a", hair="8a5a2b",
      beard="forked", hat="cap", robe="5a6b3a", trim="8a9a5a", bg="2b2f1f"),
    t("bertie_bott", "COMMON", "BOTT", skin="f0d0b0", hair="c2a05a", hat="cap",
      robe="c23a4a", trim="f0e6c8", acc="smile", bg="3a1f24"),
    t("glanmore_peakes", "COMMON", "PEAKES", skin="d9a878", hair="2b2b2b",
      beard="short", robe="2b4a6b", trim="b0c4d8", bg="1c2c3a"),
    t("wendelin_the_weird", "COMMON", "WENDELIN", skin="f0d0b0", hair="d64a3a",
      hair_style="long", hat="pointed", robe="c25a2b", trim="f2a03a", acc="smile",
      bg="3a2416"),
    t("uric_the_oddball", "COMMON", "URIC", skin="ead0a8", hair="a8a8a8",
      hair_style="long", beard="long", hat="wide", robe="7a8fa8", trim="c8d8e8",
      bg="24303a"),

    # --- uncommon ----------------------------------------------------------------
    t("paracelsus", "UNCOMMON", "PARACELSUS", skin="d9b48c", hair="4a3a2b",
      beard="long", hat="cap", robe="3a4a3a", trim="a8955a", bg="1f2a20"),
    t("cliodna", "UNCOMMON", "CLIODNA", skin="e8c49a", hair="3a2b1a",
      hair_style="long", hat="hood", robe="2b5a4a", trim="8fd6b0", bg="16302a"),
    t("newt_scamander", "UNCOMMON", "SCAMANDER", skin="ecd0ad", hair="a8703a",
      hair_style="wavy", robe="2b6b8f", trim="c2a05a", bg="1b2f3a"),
    t("bowman_wright", "UNCOMMON", "WRIGHT", skin="c99a6b", hair="3a2b1a",
      beard="short", hat="cap", robe="5a4a3a", trim="e8c463", acc="snitch",
      bg="2e2a1f"),
    t("artemisia_lufkin", "UNCOMMON", "LUFKIN", skin="f0d0b0", hair="6b3a2b",
      hair_style="bun", hat="pointed", robe="6b2b4a", trim="d4af37", bg="331b2a"),
    t("gunhilda_of_gorsemoor", "UNCOMMON", "GUNHILDA", skin="e8c49a", hair="d8d8d8",
      hair_style="bun", robe="8f5a6b", trim="f0d8e0", bg="332430"),
    t("cassandra_vablatsky", "UNCOMMON", "VABLATSKY", skin="d9a878", hair="1a1a2b",
      hair_style="long", hat="hood", robe="4a2b6b", trim="8f6bd6", acc="stars",
      bg="241a3a"),
    t("adalbert_waffling", "UNCOMMON", "WAFFLING", skin="f0d0b0", hair="8a8a8a",
      beard="short", robe="3a4a6b", trim="c2c8d8", acc="glasses", bg="1f2636"),

    # --- rare --------------------------------------------------------------------
    t("merlin", "RARE", "MERLIN", skin="f0d8bc", hair="f2f2f2", hair_style="long",
      beard="long", hat="pointed", robe="2b3a8c", trim="d4af37", acc="stars",
      bg="141d40"),
    t("circe", "RARE", "CIRCE", skin="e8c49a", hair="2b1a0f", hair_style="long",
      hat="crown", robe="8f2b4a", trim="e8c463", bg="3a1626"),
    t("morgana_le_fay", "RARE", "MORGANA", skin="d8c0b0", hair="111118",
      hair_style="long", hat="hood", robe="1f2b1f", trim="6b8f5a", bg="10160f"),
    t("ptolemy", "RARE", "PTOLEMY", skin="c99a6b", hair="2b2b2b", beard="short",
      hat="cap", robe="c8b48f", trim="8f7a4a", acc="stars", bg="2a2a1c"),
    t("cornelius_agrippa", "RARE", "AGRIPPA", skin="e0b98e", hair="3a3a3a",
      hair_style="long", beard="forked", hat="cap", robe="3a2b2b", trim="a8956b",
      bg="20191a"),
    t("andros_the_invincible", "RARE", "ANDROS", skin="c9895a", hair="6b5a3a",
      hair_style="long", beard="long", robe="8f8f8f", trim="e8e8e8", bg="1e2430"),

    # --- legendary ---------------------------------------------------------------
    t("nicolas_flamel", "LEGENDARY", "FLAMEL", skin="ead0a8", hair="e8e8e8",
      hair_style="long", beard="long", hat="cap", robe="6b2b1a", trim="e8c463",
      acc="stars", bg="2e1a12"),
    t("harry_potter", "LEGENDARY", "POTTER", skin="f2d3ae", hair="15100c",
      hair_style="messy", robe="4a2b2b", trim="c23a2b", acc="glasses_scar",
      bg="1b1420"),
]


# --------------------------------------------------------------------------- drawing

# Star-field seeds of the shipped cards. These were `hash(id) & 0xFFFF`, and Python salts `str`
# hashes per process, so every run scattered different stars. The values are the ones the shipped
# textures were painted with (recovered by search against them, 2026-09-28). A card added later
# gets a stable CRC instead.
STAR_SEED = {"cassandra_vablatsky": 42466, "merlin": 38422, "ptolemy": 16700,
             "nicolas_flamel": 45504, "harry_potter": 27141}


def star_seed(card_id):
    return STAR_SEED.get(card_id, zlib.crc32(card_id.encode()) & 0xFFFF)


def sprinkle(draw, box, colour, seed, count):
    """Star specks in a panel. Deterministic, so a rerun paints the same sky."""
    x0, y0, x1, y1 = box
    n = seed * 2654435761 & 0xFFFFFFFF
    for _ in range(count):
        n = (n * 1103515245 + 12345) & 0xFFFFFFFF
        x = x0 + (n >> 8) % max(1, x1 - x0)
        n = (n * 1103515245 + 12345) & 0xFFFFFFFF
        y = y0 + (n >> 8) % max(1, y1 - y0)
        draw.point((x, y), fill=colour)


def portrait(draw, box, row):
    """The wizard, inside the framed panel."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    cx = x0 + w // 2

    skin, hair = hx(row["skin"]), hx(row["hair"])
    robe, trim = hx(row["robe"]), hx(row["trim"])
    bg = hx(row["bg"])

    # Sky: a vertical wash, lighter behind the head so the silhouette separates.
    for y in range(y0, y1):
        f = (y - y0) / max(1, h - 1)
        draw.line([(x0, y), (x1 - 1, y)], fill=mix(mix(bg, hx("ffffff"), 0.22), bg, f))
    if row["acc"] in ("stars",) or row["rarity"] == "LEGENDARY":
        sprinkle(draw, (x0 + 2, y0 + 2, x1 - 2, y0 + h // 2), trim, star_seed(row["id"]), 22)

    # Shoulders, then a V of trim for the collar.
    sh_top = y0 + int(h * 0.70)
    draw.polygon([(x0 + 3, y1), (x0 + 12, sh_top), (x1 - 13, sh_top), (x1 - 4, y1)], fill=robe)
    draw.polygon([(cx - 9, sh_top), (cx, sh_top + 12), (cx + 9, sh_top)], fill=trim)

    # Neck and head.
    draw.rectangle([cx - 5, sh_top - 9, cx + 5, sh_top + 2], fill=mix(skin, INK, 0.22))
    head = [cx - 11, y0 + int(h * 0.18), cx + 11, sh_top - 3]
    hy0, hy1 = head[1], head[3]

    # A hood is drawn *under* the head, not over it. Cut out of the finished portrait it
    # left a hole where the face should be; behind it, it reads as cloth the wizard is
    # looking out of, which is the whole point of a hood.
    if row["hat"] == "hood":
        draw.chord([cx - 17, hy0 - 8, cx + 17, hy1 + 10], 180, 360, fill=robe)
        draw.rectangle([cx - 17, (hy0 + hy1) // 2, cx + 17, y1], fill=robe)
        draw.chord([cx - 14, hy0 - 4, cx + 14, hy1 + 6], 180, 360, fill=mix(robe, INK, 0.45))

    draw.ellipse(head, fill=skin, outline=mix(skin, INK, 0.4))

    # Hair.
    style = row["hair_style"]
    if style != "bald":
        draw.chord([head[0] - 1, hy0 - 2, head[2] + 1, hy0 + 22], 180, 360, fill=hair)
    if style in ("long", "wavy", "bun"):
        draw.rectangle([head[0] - 2, hy0 + 8, head[0] + 3, hy1 - 4], fill=hair)
        draw.rectangle([head[2] - 3, hy0 + 8, head[2] + 2, hy1 - 4], fill=hair)
    if style == "wavy":
        draw.ellipse([head[0] - 4, hy0 + 14, head[0] + 4, hy1 - 1], fill=hair)
        draw.ellipse([head[2] - 4, hy0 + 14, head[2] + 4, hy1 - 1], fill=hair)
    if style == "bun":
        draw.ellipse([cx - 6, hy0 - 9, cx + 6, hy0 + 2], fill=hair)
    if style == "messy":
        for i in range(-10, 11, 3):
            draw.line([(cx + i, hy0 + 2), (cx + i + 1, hy0 - 4)], fill=hair)

    # Face.
    eye_y = hy0 + int((hy1 - hy0) * 0.46)
    draw.rectangle([cx - 6, eye_y, cx - 4, eye_y + 1], fill=INK)
    draw.rectangle([cx + 4, eye_y, cx + 6, eye_y + 1], fill=INK)
    mouth_y = hy0 + int((hy1 - hy0) * 0.72)
    if row["acc"] == "smile":
        draw.arc([cx - 6, mouth_y - 4, cx + 6, mouth_y + 3], 0, 180, fill=hx("f2f2f2"))
        draw.line([(cx - 5, mouth_y), (cx + 5, mouth_y)], fill=hx("fffaf0"))
    else:
        draw.line([(cx - 4, mouth_y), (cx + 4, mouth_y)], fill=mix(skin, INK, 0.55))

    # Beard, drawn after the mouth so a long one covers it.
    beard = row["beard"]
    if beard == "short":
        draw.chord([cx - 10, mouth_y - 8, cx + 10, hy1 + 4], 0, 180, fill=hair)
    elif beard == "long":
        draw.polygon([(cx - 10, mouth_y - 2), (cx + 10, mouth_y - 2),
                      (cx + 5, y1 - 4), (cx - 5, y1 - 4)], fill=hair)
    elif beard == "forked":
        draw.polygon([(cx - 10, mouth_y - 2), (cx + 10, mouth_y - 2),
                      (cx + 6, y1 - 12), (cx, mouth_y + 8), (cx - 6, y1 - 12)], fill=hair)

    # Eyewear and the rest of the props, over everything else on the face.
    acc = row["acc"]
    if acc in ("glasses", "glasses_scar", "halfmoon"):
        rim = hx("2b2b2b") if acc != "halfmoon" else hx("d4af37")
        if acc == "halfmoon":
            draw.arc([cx - 9, eye_y - 3, cx - 1, eye_y + 4], 0, 180, fill=rim)
            draw.arc([cx + 1, eye_y - 3, cx + 9, eye_y + 4], 0, 180, fill=rim)
        else:
            draw.ellipse([cx - 9, eye_y - 3, cx - 1, eye_y + 4], outline=rim)
            draw.ellipse([cx + 1, eye_y - 3, cx + 9, eye_y + 4], outline=rim)
        draw.line([(cx - 1, eye_y + 1), (cx + 1, eye_y + 1)], fill=rim)
    if acc == "glasses_scar":
        draw.line([(cx - 4, hy0 + 6), (cx - 2, hy0 + 9)], fill=hx("b03a2b"))
        draw.line([(cx - 2, hy0 + 9), (cx - 4, hy0 + 12)], fill=hx("b03a2b"))
    if acc == "snitch":
        draw.ellipse([x1 - 16, y0 + 6, x1 - 8, y0 + 14], fill=hx("e8c463"))
        draw.line([(x1 - 17, y0 + 8), (x1 - 22, y0 + 5)], fill=hx("f2f2f2"))
        draw.line([(x1 - 7, y0 + 8), (x1 - 2, y0 + 5)], fill=hx("f2f2f2"))

    # Hat, last: it sits over the hair and the top of the frame line.
    hat = row["hat"]
    if hat == "pointed":
        draw.polygon([(cx - 16, hy0 + 3), (cx + 16, hy0 + 3), (cx + 3, y0 + 1)], fill=robe)
        draw.rectangle([cx - 18, hy0 + 2, cx + 18, hy0 + 5], fill=mix(robe, INK, 0.35))
        draw.rectangle([cx - 12, hy0 - 1, cx + 12, hy0 + 2], fill=trim)
    elif hat == "wide":
        draw.rectangle([cx - 20, hy0 + 1, cx + 20, hy0 + 4], fill=mix(robe, INK, 0.4))
        draw.chord([cx - 11, hy0 - 9, cx + 11, hy0 + 9], 180, 360, fill=robe)
    elif hat == "cap":
        draw.chord([cx - 12, hy0 - 3, cx + 12, hy0 + 15], 180, 360, fill=robe)
        draw.rectangle([cx - 12, hy0 + 5, cx + 12, hy0 + 7], fill=trim)
    elif hat == "hood":
        # Only the front edge is drawn here: the cowl itself is already behind the head.
        # The brow band shades the eyes, which is what makes a hood read as a hood.
        draw.chord([cx - 14, hy0 - 4, cx + 14, hy0 + 16], 180, 360, fill=robe)
        draw.arc([cx - 14, hy0 - 4, cx + 14, hy0 + 16], 180, 360, fill=trim)
        draw.rectangle([cx - 15, hy0 + 8, cx - 10, hy1 - 2], fill=robe)
        draw.rectangle([cx + 10, hy0 + 8, cx + 15, hy1 - 2], fill=robe)
    elif hat == "crown":
        draw.rectangle([cx - 11, hy0 - 1, cx + 11, hy0 + 3], fill=trim)
        for i in (-9, -3, 3, 9):
            draw.polygon([(cx + i - 2, hy0), (cx + i + 2, hy0), (cx + i, hy0 - 6)], fill=trim)


def nameplate(draw, box, text, font):
    x0, y0, x1, y1 = box
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=PARCHMENT)
    draw.rectangle([x0, y0, x1 - 1, y0], fill=mix(PARCHMENT, hx("ffffff"), 0.5))
    draw.rectangle([x0, y1 - 1, x1 - 1, y1 - 1], fill=PARCHMENT_DARK)
    w = draw.textlength(text, font=font)
    draw.text((x0 + ((x1 - x0) - w) / 2, y0 + 2), text, font=font, fill=INK)


def card_face(row, font):
    img = Image.new("RGBA", (FACE_W, FACE_H), INK)
    draw = ImageDraw.Draw(img)
    foil = RARITY_FOIL[row["rarity"]]

    # Foil border, then an ink mount inside it.
    draw.rectangle([0, 0, FACE_W - 1, FACE_H - 1], fill=foil)
    draw.rectangle([1, 1, FACE_W - 2, FACE_H - 2], outline=mix(foil, hx("ffffff"), 0.45))
    draw.rectangle([4, 4, FACE_W - 5, FACE_H - 5], fill=INK)

    portrait(draw, (7, 7, FACE_W - 7, 86), row)
    draw.rectangle([6, 6, FACE_W - 7, 86], outline=mix(foil, INK, 0.35))
    nameplate(draw, (7, 90, FACE_W - 7, 106), row["plate"], font)

    # Rarity pips: how many, not just what colour, so the tier survives a colourblind eye.
    for i in range(RARITY_PIPS[row["rarity"]]):
        x = 8 + i * 6
        draw.rectangle([x, 110, x + 3, 113], fill=foil)
    return img


def card_back(row):
    """The reverse: a foil lattice on ink, tinted by tier. Stretched over the back face."""
    w, h = BACK_BOX[2] - BACK_BOX[0], BACK_BOX[3] - BACK_BOX[1]
    img = Image.new("RGBA", (w, h), INK)
    draw = ImageDraw.Draw(img)
    foil = RARITY_FOIL[row["rarity"]]
    for i in range(-h, w + h, 6):
        draw.line([(i, 0), (i + h, h)], fill=mix(INK, foil, 0.55))
        draw.line([(i + h, 0), (i, h)], fill=mix(INK, foil, 0.30))
    draw.rectangle([0, 0, w - 1, h - 1], outline=foil)
    draw.ellipse([w // 2 - 5, h // 2 - 5, w // 2 + 5, h // 2 + 5], fill=foil)
    return img


def sheet(row, font):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    img.paste(card_face(row, font), (0, 0))
    img.paste(card_back(row), (BACK_BOX[0], BACK_BOX[1]))
    edge = Image.new("RGBA", (EDGE_BOX[2] - EDGE_BOX[0], EDGE_BOX[3] - EDGE_BOX[1]),
                     mix(PARCHMENT, INK, 0.35))
    img.paste(edge, (EDGE_BOX[0], EDGE_BOX[1]))
    return posterise(img, 24)


# --------------------------------------------------------------------------- models

# 10 wide, 15 tall, 1 deep, centred on the model's middle so the display transforms and
# the first-person map pose both rotate about the card rather than about its corner.
DISPLAY = {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    # -90, not 90: a +90 turn about X points the portrait at the floor, and a dropped card
    # lands face down.
    "ground": {"rotation": [-90, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    # 180 about Y, as vanilla's item/generated does: an item frame shows a model's north face,
    # and at 0 a framed card showed its back.
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 0], "scale": [1.0, 1.0, 1.0]},
    "thirdperson_righthand": {"rotation": [0, -90, 25], "translation": [0, 4, 2],
                              "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [0, 90, -25], "translation": [0, 4, 2],
                             "scale": [0.5, 0.5, 0.5]},
    # Only used when the other hand is full; a free off-hand gets the two-handed map pose
    # from WizardCardHandRenderer instead.
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13],
                              "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13],
                             "scale": [0.68, 0.68, 0.68]},
}


def card_model(texture):
    face = [0.0, 0.0, 10.0, 15.0]
    back = [15.0, 0.0, 11.0, 4.0]   # mirrored, so the lattice runs the same way round
    edge_h = [11.0, 5.0, 12.0, 6.0]
    return {
        "credit": "generated by tools/wizard_cards.py",
        "gui_light": "front",
        "textures": {"0": texture, "particle": texture},
        "elements": [{
            "from": [3.0, 0.5, 7.5],
            "to": [13.0, 15.5, 8.5],
            "faces": {
                # South is the face the GUI and an item frame show, so the portrait goes there.
                "south": {"uv": face, "texture": "#0"},
                "north": {"uv": back, "texture": "#0"},
                "east": {"uv": edge_h, "texture": "#0"},
                "west": {"uv": edge_h, "texture": "#0"},
                "up": {"uv": edge_h, "texture": "#0"},
                "down": {"uv": edge_h, "texture": "#0"},
            },
        }],
        "display": DISPLAY,
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def select_definition():
    return {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:component",
            "component": COMPONENT,
            "cases": [
                {"when": row["id"],
                 "model": {"type": "minecraft:model",
                           "model": "%s:item/card/%s" % (MODID, row["id"])}}
                for row in ROSTER
            ],
            # A card with no id printed on it: the crafted blank.
            "fallback": {"type": "minecraft:model",
                         "model": "%s:item/famous_wizard_card" % MODID},
        }
    }


def blank_row():
    return t("blank", "COMMON", "", hair_style="bald", robe="6b6152", trim="9a8f78",
             skin="c9bda0", bg="1d1a24")


def blank_face(font):
    """The unprinted card: frame, empty mount, empty nameplate."""
    img = Image.new("RGBA", (FACE_W, FACE_H), INK)
    draw = ImageDraw.Draw(img)
    foil = RARITY_FOIL["COMMON"]
    draw.rectangle([0, 0, FACE_W - 1, FACE_H - 1], fill=foil)
    draw.rectangle([4, 4, FACE_W - 5, FACE_H - 5], fill=INK)
    draw.rectangle([7, 7, FACE_W - 8, 85], fill=mix(PARCHMENT, INK, 0.25))
    for y in range(9, 84, 5):
        draw.line([(11, y), (FACE_W - 12, y)], fill=mix(PARCHMENT, INK, 0.45))
    nameplate(draw, (7, 90, FACE_W - 7, 106), "", font)
    return img


def build_blank(font):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    img.paste(blank_face(font), (0, 0))
    img.paste(card_back(blank_row()), (BACK_BOX[0], BACK_BOX[1]))
    edge = Image.new("RGBA", (EDGE_BOX[2] - EDGE_BOX[0], EDGE_BOX[3] - EDGE_BOX[1]),
                     mix(PARCHMENT, INK, 0.35))
    img.paste(edge, (EDGE_BOX[0], EDGE_BOX[1]))
    return posterise(img, 16)


def load_font():
    """PIL's built-in bitmap face. No font file to ship, and it is already pixel-aligned."""
    try:
        return ImageFont.load_default(9)
    except TypeError:  # older Pillow: load_default takes no size
        return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite art this tool did not write")
    ap.add_argument("--preview", default="", help="also write a contact sheet here")
    args = ap.parse_args()

    font = load_font()
    written = 0
    for row in ROSTER:
        tex = os.path.join(TEX_DIR, row["id"] + ".png")
        if args.force or is_regenerable(tex, MARKER):
            save(sheet(row, font), tex, MARKER)
            written += 1
        else:
            print("skip (hand-authored): " + tex)
        write_json(os.path.join(MODEL_DIR, row["id"] + ".json"),
                   card_model("%s:item/card/%s" % (MODID, row["id"])))

    if args.force or is_regenerable(BLANK_TEX, MARKER):
        save(build_blank(font), BLANK_TEX, MARKER)
        written += 1
    else:
        print("skip (hand-authored): " + BLANK_TEX)
    write_json(BLANK_MODEL, card_model("%s:item/famous_wizard_card" % MODID))
    write_json(os.path.join(ITEMS_DIR, "famous_wizard_card.json"), select_definition())

    print("wrote %d textures, %d models, 1 item definition" % (written, len(ROSTER) + 1))

    if args.preview:
        cols = 8
        rows = (len(ROSTER) + cols - 1) // cols
        pad = 6
        sheet_img = Image.new("RGBA", (cols * (FACE_W + pad) + pad,
                                       rows * (FACE_H + pad) + pad), (24, 22, 30, 255))
        for i, row in enumerate(ROSTER):
            sheet_img.paste(posterise(card_face(row, font), 24),
                            (pad + (i % cols) * (FACE_W + pad), pad + (i // cols) * (FACE_H + pad)))
        os.makedirs(os.path.dirname(os.path.abspath(args.preview)), exist_ok=True)
        sheet_img.save(args.preview)
        print("preview: " + args.preview)


if __name__ == "__main__":
    main()
