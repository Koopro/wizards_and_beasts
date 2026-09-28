#!/usr/bin/env python3
"""Mob-effect icons drawn in the item-sprite language, for the effects whose art did not fit.

The texture audit of 2026-09-27 measured the 42 effect icons against vanilla's and found three
kinds of misfit among the hand-made ones:

- flat coloured octagons standing in for an icon (drunk, firewhisky_burn, fizzing, warmth,
  home_comfort, hogwarts_comfort, chocolate_ward, wrackspurt_sight);
- soft blurred discs — gradients, not pixel art (mellow, shadow_form, bubble_float);
- 128x128 paintings squeezed into an 18x18 slot, at seven times the pixel density of every icon
  beside them (dementor_chill, splinched);
- one-pixel line drawings with no outline, three of them the same spiral (confundo, disoriented,
  imperio_euphoria, imperio_resisting), a gradient placeholder (gills), an unoutlined disc
  (peppermint_hop), and yellow sparks for a scarlet spell (stupefy).

Each is redrawn here with `item_sprites`' engine — a hand-placed silhouette, shading computed
from the material (lit from the top-left), a one-pixel outline in the material's own darkest
tone — so the effect row reads like vanilla's: one object per icon, outlined, 16x16 of drawing
centred in the 18x18 canvas vanilla uses. The other 29 icons already sit in that language and
are left alone.

Run from the repo root:
    python tools/effect_sprites.py --mockup out.png
    python tools/effect_sprites.py --write [--force]
"""

import argparse
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import item_sprites as isp  # noqa: E402
from artgen_common import is_regenerable, marker, save  # noqa: E402

EFFECT_DIR = "src/main/resources/assets/wizards_and_beasts/textures/mob_effect"
MARKER = marker("effect_sprites.py")

isp.MATERIALS.update({
    "chocbar": ("#6E4126", "flat"),
    "flame": ("#E0501E", "round"),
    "bubble": ("#9FD8F0", "round"),
    "fizz": ("#F2E29A", "round"),
    "moon": ("#E8D9A0", "round"),
    "mug": ("#A8683A", "flat"),
    "steam": ("#E4E6EE", "flat"),
    "amber": ("#D89A2E", "flat"),
    "shield": ("#B08A3A", "flat"),
    "roof": ("#8E3A2A", "flat"),
    "wall": ("#C8A878", "flat"),
    "lens": ("#E070B0", "round"),
    "shadow": ("#4E3468", "round"),
    "hood": ("#3E4250", "flat"),
    "ice": ("#BFE8FF", "flat"),
    "skin": ("#C9A07C", "flat"),
    "blood": ("#B02A2A", "flat"),
    "violet": ("#9A6BFF", "flat"),
    "star": ("#8CC8FF", "flat"),
    "string": ("#E8D8A8", "flat"),
    "fish": ("#4E9A8A", "round"),
    "mint": ("#F2F0EA", "round"),
    "stun": ("#E8262E", "flat"),
})


class Canvas:
    """A 16x16 character grid with shape helpers; `rows()` hands it to the sprite engine."""

    def __init__(self):
        self.g = [["."] * 16 for _ in range(16)]

    def put(self, x, y, ch):
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = ch

    def rect(self, x0, y0, x1, y1, ch):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, ch)

    def disc(self, cx, cy, r, ch):
        for y in range(16):
            for x in range(16):
                # r*r + r is the pixel-circle threshold with no one-pixel nubs at the four points.
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r:
                    self.put(x, y, ch)

    def rows(self):
        return ["".join(r) for r in self.g]


EFFECTS = {}


def effect(name, legend, canvas):
    EFFECTS[name] = {"name": name, "legend": legend, "grid": canvas.rows(), "outline": True}


def build():
    # Bubble Float: one pixel-shaded sphere with a hard specular.
    c = Canvas()
    c.disc(8, 8, 6, "b")
    c.put(5, 5, "*"); c.put(6, 5, "*"); c.put(5, 6, "*")
    effect("bubble_float", {"b": "bubble", "*": ("bubble", "spec")}, c)

    # Fizzing (Whizzbee): three rising bubbles, largest lowest.
    c = Canvas()
    c.disc(6, 10, 4, "f"); c.disc(12, 5, 2, "f"); c.disc(5, 2, 1, "f")
    c.put(4, 8, "*"); c.put(5, 8, "*"); c.put(11, 4, "*")
    effect("fizzing", {"f": "fizz", "*": ("fizz", "spec")}, c)

    # Mellow: a crescent moon — rest, not sleep.
    c = Canvas()
    c.disc(7, 8, 6, "m")
    for y in range(16):
        for x in range(16):
            if (x - 10) ** 2 + (y - 6) ** 2 <= 22:
                c.put(x, y, ".")
    effect("mellow", {"m": "moon"}, c)

    # Warmth: a steaming mug.
    c = Canvas()
    c.rect(3, 7, 10, 14, "u")
    c.rect(3, 6, 10, 6, "o")
    c.rect(11, 8, 12, 8, "u"); c.rect(12, 9, 12, 11, "u"); c.rect(11, 12, 12, 12, "u")
    for x, y in ((5, 1), (5, 2), (6, 3), (5, 4), (8, 2), (9, 3), (8, 4)):
        c.put(x, y, "s")
    effect("warmth", {"u": "mug", "o": "foam", "s": "steam"}, c)

    # Drunk: a dizzy amber spiral.
    c = Canvas()
    for i in range(0, 260):
        t = i / 26.0
        r = 0.55 * t
        x = int(round(8 + r * math.cos(t)))
        y = int(round(8 + r * math.sin(t)))
        c.put(x, y, "a")
    effect("drunk", {"a": "amber"}, c)

    # Firewhisky burn: a flame with a hot core.
    c = Canvas()
    for y, (x0, x1) in enumerate([(7, 7), (7, 8), (6, 8), (6, 9), (5, 9), (5, 10), (4, 10), (4, 11),
                                  (3, 11), (3, 12), (3, 12), (3, 12), (4, 11), (5, 10)]):
        c.rect(x0, y + 1, x1, y + 1, "f")
    c.rect(6, 8, 9, 12, "y"); c.rect(7, 10, 8, 12, "w")
    effect("firewhisky_burn", {"f": "flame", "y": ("flame", "#F2A83A"), "w": ("flame", "#FFF0B0", "light")}, c)

    # Chocolate ward: a segmented bar with a bite out of one corner.
    c = Canvas()
    c.rect(3, 2, 12, 13, "c")
    for x, y in ((11, 2), (12, 2), (12, 3)):
        c.put(x, y, ".")
    for y in (6, 10):
        c.rect(3, y, 12, y, "d")
    c.rect(7, 2, 7, 13, "d")
    effect("chocolate_ward", {"c": "chocbar", "d": ("chocbar", "shade")}, c)

    # Hogwarts comfort: the school shield, quartered in the four house colours.
    c = Canvas()
    shape = [(3, 12)] * 8 + [(4, 11), (4, 11), (5, 10), (6, 9), (7, 8)]
    for i, (x0, x1) in enumerate(shape):
        y = i + 2
        for x in range(x0, x1 + 1):
            left = x < 8
            top = y < 8
            c.put(x, y, {(True, True): "r", (False, True): "g", (True, False): "y", (False, False): "b"}[(left, top)])
    effect("hogwarts_comfort", {"r": ("cloth", "#9A2A2A"), "g": ("cloth", "#2E6A3A"),
                                "y": ("cloth", "#D8AA30"), "b": ("cloth", "#2A4A8E")}, c)

    # Home comfort: a cottage with a lit window.
    c = Canvas()
    for i in range(6):
        c.rect(7 - i, 2 + i, 8 + i, 2 + i, "r")
    c.rect(3, 8, 12, 14, "w")
    c.rect(5, 10, 6, 11, "l"); c.rect(9, 10, 10, 14, "d")
    effect("home_comfort", {"r": "roof", "w": "wall", "l": ("candy", "#F4D060", "light"),
                            "d": ("darkwood", "base")}, c)

    # Wrackspurt sight: Spectrespecs — two lenses on a bridge.
    c = Canvas()
    c.disc(4, 8, 3, "p"); c.disc(11, 8, 3, "q")
    c.rect(7, 7, 8, 7, "k")
    effect("wrackspurt_sight", {"p": "lens", "q": ("lens", "#60A0E0"), "k": ("silver", "base")}, c)

    # Shadow form: a figure dissolving at the hem — head clear of the shoulders so it reads as
    # a person, not a bottle.
    c = Canvas()
    c.disc(8, 3, 1, "s")
    c.rect(5, 6, 11, 7, "s"); c.rect(6, 8, 10, 11, "s")
    c.rect(4, 7, 4, 10, "s"); c.rect(12, 7, 12, 10, "s")
    for x, y in ((6, 12), (8, 12), (10, 12), (7, 13), (9, 13), (8, 14)):
        c.put(x, y, "s")
    effect("shadow_form", {"s": "shadow"}, c)

    # Dementor chill: a pointed hood over a void, and frost.
    c = Canvas()
    for y, (x0, x1) in enumerate([(7, 7), (6, 8), (5, 9), (4, 10), (4, 10), (3, 11), (3, 11),
                                  (3, 11), (2, 12), (2, 12), (2, 12), (2, 12), (2, 12)]):
        c.rect(x0, y + 1, x1, y + 1, "h")
    c.rect(6, 5, 8, 5, "v"); c.rect(5, 6, 9, 10, "v")
    for x, y in ((13, 11), (12, 12), (13, 12), (14, 12), (13, 13)):
        c.put(x, y, "i")
    effect("dementor_chill", {"h": "hood", "v": ("hood", "outline"), "i": "ice"}, c)

    # Splinched: one figure whose arm was left a step behind, torn at the shoulder.
    c = Canvas()
    c.disc(8, 3, 1, "p")
    c.rect(6, 6, 10, 10, "p"); c.rect(11, 6, 12, 10, "p")
    c.rect(6, 11, 7, 14, "p"); c.rect(9, 11, 10, 14, "p")
    c.rect(1, 9, 2, 13, "p")
    for x, y in ((5, 6), (5, 7), (1, 8), (2, 8)):
        c.put(x, y, "x")
    effect("splinched", {"p": "skin", "x": "blood"}, c)


def build_more():
    # Confundo: a question mark — confusion, not dizziness (drunk keeps the spiral).
    c = Canvas()
    c.rect(5, 2, 10, 3, "v"); c.rect(10, 3, 11, 7, "v"); c.rect(8, 7, 10, 8, "v")
    c.rect(4, 3, 5, 5, "v"); c.rect(7, 8, 8, 10, "v"); c.rect(7, 12, 8, 13, "v")
    effect("confundo", {"v": "violet"}, c)

    # Disoriented: three stars circling a point.
    c = Canvas()
    for sx, sy in ((7, 2), (2, 10), (12, 11)):
        c.put(sx + 1, sy, "s"); c.rect(sx, sy + 1, sx + 2, sy + 1, "s"); c.put(sx + 1, sy + 2, "s")
    for x, y in ((4, 5), (5, 4), (11, 5), (12, 7), (7, 13), (9, 13), (4, 12)):
        c.put(x, y, "t")
    effect("disoriented", {"s": ("star", "#EAF6FF", "light"), "t": "star"}, c)

    # Imperius: the marionette's control bar and its strings — euphoria whole, resisting cut.
    def marionette(cut):
        c = Canvas()
        c.rect(2, 3, 13, 4, "w"); c.rect(7, 1, 8, 7, "w")
        for x in (3, 7, 12):
            for y in range(5, 14):
                if cut and x != 7 and 8 <= y <= 9:
                    c.put(x, y, "r" if y == 8 else ".")
                    continue
                c.put(x, y, "g")
        return c
    effect("imperio_euphoria", {"w": ("darkwood", "base"), "g": ("gold", "light")}, marionette(False))
    effect("imperio_resisting", {"w": ("darkwood", "base"), "g": "string", "r": "blood"}, marionette(True))

    # Gills (Gillyweed): a fish with gill slits.
    c = Canvas()
    c.disc(7, 8, 4, "f"); c.rect(11, 6, 11, 10, "f"); c.rect(12, 5, 13, 11, "f")
    c.put(5, 7, "e")
    for y in (7, 9):
        c.put(8, y, "g")
    effect("gills", {"f": "fish", "e": ("ink", "base"), "g": ("fish", "outline")}, c)

    # Peppermint hop: a mint humbug, red swirl on white.
    c = Canvas()
    c.disc(8, 8, 5, "m")
    for x, y in ((8, 4), (9, 4), (10, 5), (11, 6), (12, 8), (11, 10), (8, 12), (7, 12), (5, 11),
                 (4, 9), (4, 8), (5, 6), (8, 7), (9, 8), (8, 9), (7, 8)):
        c.put(x, y, "r")
    effect("peppermint_hop", {"m": "mint", "r": ("wax", "#D0303A", "base")}, c)

    # Stupefy: a scarlet stunning burst — the duel's jet of red light.
    c = Canvas()
    c.rect(7, 2, 8, 13, "s"); c.rect(2, 7, 13, 8, "s")
    for i in range(3, 13):
        c.put(i, i, "s"); c.put(15 - i, i, "s")
    c.rect(6, 6, 9, 9, "w")
    effect("stupefy", {"s": "stun", "w": ("stun", "#FFD0C8", "light")}, c)


def render(name):
    icon = isp.render(EFFECTS[name])
    canvas = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    canvas.alpha_composite(icon, (1, 1))
    return canvas


def mockup(path):
    names = sorted(EFFECTS)
    cell, pad = 18 * 4, 10
    sheet = Image.new("RGBA", (len(names) * (cell * 2 + pad * 3), cell + 2 * pad), (139, 139, 139, 255))
    for i, name in enumerate(names):
        x = i * (cell * 2 + pad * 3) + pad
        old = os.path.join(EFFECT_DIR, name + ".png")
        if os.path.exists(old):
            sheet.alpha_composite(Image.open(old).convert("RGBA").resize((cell, cell), Image.NEAREST), (x, pad))
        sheet.alpha_composite(render(name).resize((cell, cell), Image.NEAREST), (x + cell + pad, pad))
    sheet.save(path)
    print("wrote", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mockup", metavar="PNG")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    build()
    build_more()
    if args.mockup:
        mockup(args.mockup)
    if args.write:
        for name in EFFECTS:
            path = os.path.join(EFFECT_DIR, name + ".png")
            if not args.force and os.path.exists(path) and not is_regenerable(path, MARKER):
                print(f"{name}: not this tool's; --force claims it")
                continue
            save(render(name), path, MARKER)
        print(f"wrote {len(EFFECTS)} effect icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
