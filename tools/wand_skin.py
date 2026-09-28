#!/usr/bin/env python3
"""Unwrap and paint the modular wand.

`wand.geo.json` is the most elaborate rig in the mod — 47 bones covering six handle
styles, five shafts, five tips, five cores, four ornaments and ten effect pieces, one
variant shown at a time by the customization system. It shipped with **100 cubes sharing
two UV slots** against a two-colour 32x32 texture, so every one of those variants drew in
the same flat colour. The customization feature was invisible: you could swap a gnarled
handle for a braided one and the wand looked identical.

Same defect as the placeholder creature rigs, so the same fix and the same machinery —
`beast_skins.shelf_pack` lays every cube's box-UV net onto a right-sized atlas and
rewrites the model, then each face is painted from its bone's role.

Roles come from the bone-name prefix the customization system already uses
(`handle_`, `shaft_`, `tip_`, `core_`, `ornament_`, `fx_`), so a new module dropped into
`WandModuleRegistry` picks up sensible material colours here without this tool changing.

Cores and effect pieces also go into `wand_glowmask.png`, so they read as lit rather than
painted on — a wand core is supposed to be the part with something alive in it.

Run from the repo root:  python tools/wand_skin.py [--force]
"""

import argparse
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_skins as bs  # noqa: E402
from artgen_common import hx, is_regenerable, marker, mix, posterise, save  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "item")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
MARKER = marker("wand_skin.py")

# Light, nearly neutral wood. The renderer multiplies the whole sheet by the wand's wood tint
# (WandAppearance, from the wood's datapack entry), so the sheet has to leave room for it: painted
# in warm brown, a pale ash tint and a dark yew tint both multiplied out to the same dark brown and
# every wand looked alike. The hue is the wood's; the sheet carries only grain, light and shape.
WOOD_DARK = hx("#8e877c")[:3]
WOOD_MID = hx("#bdb4a6")[:3]
WOOD_PALE = hx("#ddd4c4")[:3]
BRASS = hx("#a8813a")[:3]
# A set stone, not a jewel: muted and slightly grey so it reads as something inlaid
# into wood rather than as coloured plastic stuck on the end.
GEM = hx("#6b5480")[:3]

# Per-core colours, matching the wand-core items those cores come from.
CORE_COLOURS = {
    "core_phoenix_feather": hx("#e0562b")[:3],
    "core_dragon_heartstring": hx("#b3241d")[:3],
    "core_unicorn_hair": hx("#f2f0ee")[:3],
    "core_thestral_tail": hx("#2b2630")[:3],
    "core_veela_hair": hx("#e8dfa8")[:3],
}

FX_COLOURS = {
    "fx_lumos_orb": hx("#ffe9a8")[:3],
    "fx_charge_sparks": hx("#8fe0f2")[:3],
    "fx_cast_burst": hx("#c9a8f2")[:3],
    "fx_spell_aura": hx("#a88ff2")[:3],
    "fx_casting_glow": hx("#ffd9a0")[:3],
    "fx_channel_runes": hx("#8fe0f2")[:3],
    "fx_grip_pulse": hx("#ffc98f")[:3],
}


def role_of(bone_name):
    for prefix in ("handle", "shaft", "tip", "core", "ornament", "fx"):
        if bone_name.startswith(prefix):
            return prefix
    return "shaft"


def colour_for(bone_name, cube, index, total):
    """Material for one cube. Some bones mix materials — a crystal tip is a wooden stem
    with a stone on the end — so the mounted feature has to be told apart from its stem.

    That distinction is thickness, not height: the stem of an orb tip is nine units tall
    and so reaches higher than the stone it carries, which meant a height test painted the
    whole tip as gemstone. A stem is always thin, a mounted stone always chunky.
    """
    role = role_of(bone_name)
    is_last = index == total - 1

    if role == "core":
        return CORE_COLOURS.get(bone_name, hx("#d6c25a")[:3]), True
    if role == "fx":
        return FX_COLOURS.get(bone_name, hx("#ffe9a8")[:3]), True
    if role == "ornament":
        return (GEM if "gem" in bone_name else BRASS), False
    if role == "tip":
        # Only the crystal tip carries a stone, and only its final cube is that stone —
        # everything before it is the wooden stem the stone is set into. The orb tip is a
        # turned wooden pommel, not a gem, which is why it is not listed here.
        if bone_name == "tip_crystal" and is_last:
            return GEM, True
        return WOOD_PALE, False
    if role == "handle":
        return WOOD_DARK, False
    return WOOD_MID, False


# The rig is now slender enough that most faces are two or three texels across, so a
# coarse bark pattern turns into noise. SKIN is a low-amplitude mottle, which at this
# scale reads as grain rather than as speckle.
# Per-variant wood, for the modules shaped after screen props. On film the wood colour is
# half of what identifies a wand — a bone-white grip and a black clawed one read as
# different characters' wands long before the carving does. Anything not listed here uses
# the default warm brown, so the base wand is unaffected.
VARIANT_WOOD = {
    "handle_bone": (hx("#d8d2c2")[:3], hx("#e6e1d3")[:3]),
    "handle_talon": (hx("#3a2f2a")[:3], hx("#4a3d35")[:3]),
    "handle_vine": (hx("#6b5433")[:3], hx("#856a42")[:3]),
    "handle_flared": (hx("#4a3526")[:3], hx("#5f452f")[:3]),
    "shaft_nodular": (hx("#8a8275")[:3], hx("#a49a8a")[:3]),
    "shaft_barked": (hx("#584030")[:3], hx("#6d5039")[:3]),
    "tip_claw": (hx("#3a2f2a")[:3], hx("#57473c")[:3]),
    "tip_serpent": (hx("#7d8288")[:3], hx("#9aa1a8")[:3]),
    "tip_spiralled": (hx("#6b4527")[:3], hx("#8a5c33")[:3]),
}


PATTERN = {"handle": "SKIN", "shaft": "SKIN", "tip": "SKIN",
           "core": "SMOKE", "ornament": "METAL", "fx": "SMOKE"}


# The Elder Wand is elder wood: pale, greyish, almost bleached — not the warm brown of
# the ordinary wand. Painting both from one palette lost the one thing that identifies it
# on sight.
ELDER_PALETTE = {"dark": hx("#8a8275")[:3], "mid": hx("#a49a8a")[:3],
                 "pale": hx("#c0b6a4")[:3]}


def paint(model_id, force):
    geo_path = os.path.join(GEO_DIR, model_id + ".geo.json")
    tex_path = os.path.join(TEX_DIR, model_id + ".png")
    glow_path = os.path.join(TEX_DIR, model_id + "_glowmask.png")

    if not force and not is_regenerable(tex_path, MARKER):
        return None

    # Always unwrap, rather than only when UVs collide. The Elder Wand's three cubes have
    # three *distinct* UVs and so pass a collision check, but one of them starts at v=14 on
    # a declared 16x16 sheet and runs off the bottom edge — distinct is not the same as
    # valid. Since this tool owns both textures, laying them out fresh is always correct.
    bs.unwrap_model(geo_path)

    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    width = int(geo["description"]["texture_width"])
    height = int(geo["description"]["texture_height"])

    elder = model_id == "elder_wand"
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()

    for bone in geo["bones"]:
        name = bone["name"]
        role = role_of(name)
        style = PATTERN[role]
        for index, cube in enumerate(bone.get("cubes", [])):
            uv = cube.get("uv")
            if not isinstance(uv, list):
                continue
            base, glowing = colour_for(name, cube, index, len(bone["cubes"]))
            override = VARIANT_WOOD.get(name)
            if override and not glowing:
                dark, pale = override
                base = {WOOD_DARK: dark, WOOD_MID: dark, WOOD_PALE: pale}.get(base, base)
            if elder:
                base = {WOOD_DARK: ELDER_PALETTE["dark"],
                        WOOD_MID: ELDER_PALETTE["mid"],
                        WOOD_PALE: ELDER_PALETTE["pale"]}.get(base, base)
            u, v = float(uv[0]), float(uv[1])
            w, h, d = (float(s) for s in cube["size"])
            seed = (sum(ord(c) for c in name) + index * 37) & 0xFFFF

            for face, (fx0, fy0, fx1, fy1) in bs.box_faces(u, v, w, h, d).items():
                x0, y0 = int(math.floor(fx0)), int(math.floor(fy0))
                x1, y1 = int(math.ceil(fx1)), int(math.ceil(fy1))
                fw, fh = max(1, x1 - x0), max(1, y1 - y0)
                shade = bs.FACE_SHADE[face]
                # Wood on a long face gets turned-wood detail: grain running the length, a lit
                # streak down the near edge so a two-texel face still reads round, binding bands
                # on a grip, and a darker point on a tip. All multipliers on the same base, so the
                # wood tint still colours every one of them.
                wooden = not glowing and role in ("handle", "shaft", "tip")
                long_face = face in ("north", "south", "east", "west") and fh > fw
                for ly in range(fh):
                    for lx in range(fw):
                        f = shade * bs.pattern_factor(style, lx, ly, fw, fh, seed)
                        if wooden and long_face:
                            if fw > 1 and lx == 0:
                                f *= 1.12
                            elif fw > 1 and lx == fw - 1:
                                f *= 0.86
                            if (lx * 5 + ly // 3 + seed) % 7 == 0:
                                f *= 0.9
                            if role == "handle" and ly % 4 == 3:
                                f *= 0.82
                            if role == "tip" and ly < 2:
                                f *= 0.82
                        elif lx == 0 or ly == 0 or lx == fw - 1 or ly == fh - 1:
                            f *= 0.88
                        col = bs.lit(base, f)
                        x, y = x0 + lx, y0 + ly
                        if 0 <= x < width and 0 <= y < height:
                            px[x, y] = (col[0], col[1], col[2], 255)
                            if glowing:
                                gx[x, y] = (col[0], col[1], col[2], 255)

    save(posterise(img, 12), tex_path, MARKER)
    save(posterise(glow, 6), glow_path, MARKER)
    return width, height


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    # The Elder Wand's geometry and skin moved to tools/item_geo.py (2026-09-23); painting it
    # here would re-unwrap and repaint that model with this file's generic grain.
    for model_id in ("wand",):
        result = paint(model_id, args.force)
        if result is None:
            print(f"{model_id}: skipped (hand-authored)")
        else:
            print(f"{model_id}: unwrapped and painted at {result[0]}x{result[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
