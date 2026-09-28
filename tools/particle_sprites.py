#!/usr/bin/env python3
"""Pixel sprites for every custom particle type, and the particle JSON that lists them.

Until 2026-09-27 all fourteen particle types shared `particle/spell_mote.png`, one 8x8 soft
radial blur, so an ice shard, a water droplet, an electric arc and an ember differed only by
tint and drift — and the blur was the one non-pixel texture in a pixel-art mod (visual
consistency audit, H3). Each type now has its own shapes.

Rules (visual style guide, section 7):
- 8x8 canvas, 3-7 px drawn, hard alpha (0 or 255) — vanilla particles are crisp.
- Greyscale ramp of three steps: 255 core, 216 body, 172 edge. The providers multiply a family
  or style colour over the sprite, so the ramp becomes that colour's light, mid and shadow.
- Two or three variants per type. `FamilyTintParticle`/`BroomTrailParticle` pick one at random
  (`SpriteSet.get(random)`), which is variety without an animation.

Run from the repo root:  python tools/particle_sprites.py [--force]
"""

import argparse
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
TEX = f"{ASSETS}/textures/particle"
DEFS = f"{ASSETS}/particles"
MARK = marker("particle_sprites.py")

# Ramp keys used in the pixel maps below.
RAMP = {"#": 255, "o": 216, "+": 172}

# Each sprite is an 8x8 map; '.' is transparent. Keep shapes small: the quad is ~0.2 block.
SPRITES = {
    "arcane_mote": [
        ["........",
         "...+....",
         "...#....",
         ".+#o#+..",
         "...#....",
         "...+....",
         "........",
         "........"],
        ["........",
         "........",
         "...o....",
         "..o#o...",
         "...o....",
         "........",
         "........",
         "........"],
        ["........",
         "..+...+.",
         "...o.o..",
         "....#...",
         "...o.o..",
         "..+...+.",
         "........",
         "........"],
    ],
    "light_glow": [
        ["........",
         "...+....",
         "...o....",
         ".+o#o+..",
         "...o....",
         "...+....",
         "........",
         "........"],
        ["........",
         "........",
         "..+o+...",
         "..o#o...",
         "..+o+...",
         "........",
         "........",
         "........"],
    ],
    "dark_wisp": [
        ["........",
         "...oo...",
         "..o..+..",
         "..o.....",
         "...oo...",
         ".....o..",
         "..+.o...",
         "...o....",
         ],
        ["........",
         "..+o....",
         "....o...",
         "....o...",
         "...o....",
         "..o.....",
         "..o+....",
         "........"],
        ["........",
         "........",
         "..oo+...",
         ".o......",
         ".o...o..",
         "..ooo...",
         "........",
         "........"],
    ],
    "fire_ember": [
        ["........",
         "........",
         "...+....",
         "..+o+...",
         "..o#o...",
         "...o....",
         "........",
         "........"],
        ["........",
         "........",
         "........",
         "...o+...",
         "...#o...",
         "........",
         "........",
         "........"],
        ["........",
         "....+...",
         "........",
         "..+.....",
         "..o#....",
         "...o....",
         "........",
         "........"],
    ],
    "ice_shard": [
        ["........",
         "......+.",
         ".....o..",
         "....#...",
         "...o....",
         "..+.....",
         "........",
         "........"],
        ["........",
         "...+....",
         "...o....",
         "...#....",
         "...o....",
         "...+....",
         "........",
         "........"],
        ["........",
         "........",
         ".+......",
         "..o.....",
         "...#o...",
         ".....+..",
         "........",
         "........"],
    ],
    "water_droplet": [
        ["........",
         "...+....",
         "...o....",
         "..o#o...",
         "..ooo...",
         "...+....",
         "........",
         "........"],
        ["........",
         "........",
         "...+....",
         "..o#....",
         "..oo....",
         "........",
         "........",
         "........"],
    ],
    "electric_arc": [
        ["........",
         "....+...",
         "...#....",
         "..o.....",
         "...#o...",
         ".....#..",
         "....+...",
         "........"],
        ["........",
         "..+.....",
         "...#....",
         "....o...",
         "...#....",
         "..o.....",
         "..+.....",
         "........"],
        ["........",
         "........",
         ".+#.....",
         "...o.#+.",
         "....#...",
         "........",
         "........",
         "........"],
    ],
    "protego_deflect": [
        ["........",
         "..+o+...",
         ".o...o..",
         ".+...+..",
         "........",
         "........",
         "........",
         "........"],
        ["........",
         "........",
         "..+#+...",
         "..#.#...",
         "..+#+...",
         "........",
         "........",
         "........"],
    ],
    "protego_shatter": [
        ["........",
         "...#....",
         "..#o....",
         "..o++...",
         "........",
         "........",
         "........",
         "........"],
        ["........",
         "........",
         "..+o#...",
         "....o...",
         "........",
         "........",
         "........",
         "........"],
        ["........",
         "....+...",
         "...o#...",
         "...o....",
         "...+....",
         "........",
         "........",
         "........"],
    ],
    "spell_clash": [
        ["........",
         ".+..+..+",
         "..o.o.o.",
         "...o#o..",
         ".+o###o+",
         "...o#o..",
         "..o.o.o.",
         ".+..+..+"],
        ["........",
         "....+...",
         "..+.o.+.",
         "...o#o..",
         "..+o#o+.",
         "....o...",
         "....+...",
         "........"],
    ],
    "ak_bypass_flash": [
        ["...+....",
         "...o....",
         "...#....",
         "+o###o+.",
         "...#....",
         "...o....",
         "...+....",
         "........"],
    ],
    # Smoke, drawn as four dissipation frames in order — `SmokePuffParticle` steps through them by
    # age (`setSpriteFromAge`): a dense puff, a looser cloud, clumps breaking apart, last wisps.
    # Replaces vanilla LARGE_SMOKE for the Dementor, the Werewolf and the Phoenix (tinted by
    # meaning: DARK_MAGIC, DARK_MAGIC, FIRE).
    "smoke_puff": [
        ["........",
         "........",
         "..+oo+..",
         ".+o##o+.",
         ".o####o.",
         ".+o##o+.",
         "..+oo+..",
         "........"],
        ["..+oo...",
         ".+o##o+.",
         "+o#oo##o",
         "o##o+o#o",
         "+o####o+",
         ".+oo#o+.",
         "..+o+...",
         "........"],
        [".+o.....",
         "+o#o..+.",
         ".o+..+o+",
         "....+o#o",
         ".+o..+o.",
         "+o#o....",
         ".+o.....",
         "........"],
        ["........",
         ".+......",
         "......+.",
         "...o....",
         ".+......",
         ".....+..",
         "..+.....",
         "........"],
    ],
    "dust_puff": [
        ["........",
         "........",
         "...++...",
         "..+oo+..",
         "...++...",
         "........",
         "........",
         "........"],
        ["........",
         "........",
         "........",
         "...o+...",
         "...++...",
         "........",
         "........",
         "........"],
    ],
}

# Particle type -> the sprite set it draws. Broom trails reuse the set that matches their look.
PARTICLES = {
    "arcane_mote": "arcane_mote",
    "light_glow": "light_glow",
    "dark_wisp": "dark_wisp",
    "fire_ember": "fire_ember",
    "ice_shard": "ice_shard",
    "water_droplet": "water_droplet",
    "electric_arc": "electric_arc",
    "protego_deflect": "protego_deflect",
    "protego_shatter": "protego_shatter",
    "spell_clash": "spell_clash",
    "ak_bypass_flash": "ak_bypass_flash",
    "smoke_puff": "smoke_puff",
    "broom_trail_dust": "dust_puff",
    "broom_trail_gold": "arcane_mote",
    "broom_trail_ember": "fire_ember",
}


def render(rows):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(rows[:8]):
        for x, ch in enumerate(row[:8]):
            if ch in RAMP:
                v = RAMP[ch]
                img.putpixel((x, y), (v, v, v, 255))
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    written = 0
    for name, variants in SPRITES.items():
        for i, rows in enumerate(variants):
            path = os.path.join(TEX, f"{name}_{i}.png")
            if not args.force and os.path.exists(path) and not is_regenerable(path, MARK):
                print(f"{path} is not this tool's; skipped")
                continue
            save(render(rows), path, MARK)
            written += 1
    for particle, sprite in PARTICLES.items():
        textures = [f"wizards_and_beasts:{sprite}_{i}" for i in range(len(SPRITES[sprite]))]
        with open(os.path.join(DEFS, f"{particle}.json"), "w", encoding="utf-8") as fh:
            json.dump({"textures": textures}, fh, indent=2)
            fh.write("\n")
    print(f"wrote {written} sprites, {len(PARTICLES)} particle definitions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
