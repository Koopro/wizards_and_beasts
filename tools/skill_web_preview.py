#!/usr/bin/env python3
"""Render the skill web to a PNG, so a layout change can be judged without the client.

Draws what SkillTreeScreen draws, in design space: an edge per adjacency pair and a disc per
node at the sprite size the screen actually uses (SkillTreeScreen.baseSpritePx -- 34 Polaris,
26 keystone, 18 notable, 12 small). It is a geometry check, not a mock-up: no parallax, no
textures, no palette. If two nodes overlap here they overlap in game.

One image per audience, because the three audiences never share a screen -- which is also why
the elf and goblin webs may legally sit on the same coordinates.

Run from the repo root:  python tools/skill_web_preview.py [--out DIR]
"""

import argparse
import collections
import glob
import json
import math
import os

from PIL import Image, ImageDraw

NODE_GLOB = "src/main/resources/data/wizards_and_beasts/skill_nodes/*/*.json"

AUDIENCE = {"elf_bond": "house_elf", "goblin_craft": "goblin"}

# SkillTreeScreen.baseSpritePx
SPRITE = {"keystone": 26, "notable": 18, "small": 12}
POLARIS_SPRITE = 34

TREE_COLOUR = {
    "spell_mastery": (170, 136, 255),
    "dark_arts": (139, 0, 255),
    "magizoology": (68, 204, 136),
    "wandlore": (187, 136, 51),
    "herbology": (68, 187, 68),
    "alchemy": (102, 204, 204),
    "goblin_craft": (212, 175, 55),
    "elf_bond": (181, 126, 220),
}

MARGIN = 40


def load():
    nodes = {}
    for path in sorted(glob.glob(NODE_GLOB)):
        with open(path, encoding="utf-8") as handle:
            nodes[json.load(open(path, encoding="utf-8"))["id"]] = json.load(
                open(path, encoding="utf-8"))
    return nodes


def sprite_px(node):
    if node["id"] == "wizard_core":
        return POLARIS_SPRITE
    return SPRITE.get(node.get("size", "notable"), 18)


def render(nodes, audience, out_path):
    web = [d for d in nodes.values() if AUDIENCE.get(d["tree"], "wizard") == audience]
    if not web:
        return None
    xs = [d.get("x", 0.0) for d in web]
    ys = [d.get("y", 0.0) for d in web]
    pad = max(sprite_px(d) for d in web)
    min_x, max_x = min(xs) - pad, max(xs) + pad
    min_y, max_y = min(ys) - pad, max(ys) + pad
    width = int(max_x - min_x) + 2 * MARGIN
    height = int(max_y - min_y) + 2 * MARGIN

    img = Image.new("RGB", (width, height), (14, 12, 20))
    draw = ImageDraw.Draw(img)

    def px(d):
        return (d.get("x", 0.0) - min_x + MARGIN, d.get("y", 0.0) - min_y + MARGIN)

    ids = {d["id"] for d in web}
    drawn = set()
    for d in web:
        for edge in d.get("edges", []):
            if edge not in ids:
                continue
            key = tuple(sorted((d["id"], edge)))
            if key in drawn:
                continue
            drawn.add(key)
            draw.line([px(d), px(nodes[edge])], fill=(70, 62, 48), width=1)

    for d in sorted(web, key=lambda n: sprite_px(n)):
        cx, cy = px(d)
        r = sprite_px(d) / 2.0
        colour = TREE_COLOUR.get(d["tree"], (200, 200, 200))
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour, outline=(20, 18, 24))

    img.save(out_path)
    return out_path, width, height, len(web), len(drawn)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="build/skill-web-preview")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    nodes = load()
    for audience in ("wizard", "house_elf", "goblin"):
        result = render(nodes, audience, os.path.join(args.out, "skill-web-%s.png" % audience))
        if result:
            path, w, h, n, e = result
            print("%-10s %3d nodes %3d edges  %dx%d  %s" % (audience, n, e, w, h, path))

    # Closest pair per audience, as a number to read beside the picture.
    by_aud = collections.defaultdict(list)
    for d in nodes.values():
        by_aud[AUDIENCE.get(d["tree"], "wizard")].append(d)
    for audience, web in sorted(by_aud.items()):
        worst = min(
            (math.hypot(a.get("x", 0) - b.get("x", 0), a.get("y", 0) - b.get("y", 0)),
             a["id"], b["id"])
            for i, a in enumerate(web) for b in web[i + 1:]
        )
        print("%-10s closest pair: %.1f  (%s / %s)" % (audience, worst[0], worst[1], worst[2]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
