#!/usr/bin/env python3
"""Art for the duelling dummy: its entity skin and its item sprite.

The dummy renders on vanilla's PLAYER rig (see DuellingDummyRenderer for why), so its skin is an
ordinary 64x64 player skin and the regions below are the vanilla net, nothing bespoke. Overlay
regions - hat, jacket, sleeves, pants - are deliberately left transparent: they sit 0.25px outside
the base cubes and any colour there would z-fight with the armour a dummy is meant to wear.

Materials read at a glance rather than being decorative: sanded post for the limbs, sailcloth for
the torso with a painted target over the heart, and a stitched burlap sack for the head. The target
faces the same way the model's front does, so a dummy planted by the item is already aimed at you.
"""

import os
import random

from PIL import Image

from artgen_common import marker, save

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "wizards_and_beasts")
ENTITY_PNG = os.path.join(ASSETS, "textures", "entity", "duelling_dummy.png")
ITEM_PNG = os.path.join(ASSETS, "textures", "item", "duelling_dummy.png")
MARKER = marker("duelling_dummy.py")

# Sanded ash for the post and limbs, sailcloth for the torso, burlap for the head.
WOOD = (150, 116, 74, 255)
WOOD_DARK = (118, 88, 55, 255)
WOOD_LIGHT = (176, 143, 99, 255)
CLOTH = (206, 190, 155, 255)
CLOTH_DARK = (176, 159, 126, 255)
BURLAP = (188, 163, 118, 255)
BURLAP_DARK = (156, 132, 92, 255)
STITCH = (86, 66, 44, 255)
TARGET_RED = (163, 54, 48, 255)
TARGET_PALE = (222, 213, 190, 255)

# Vanilla 64x64 player-skin regions, as (left, top, right, bottom).
HEAD = (0, 0, 32, 16)
BODY = (16, 16, 40, 32)
RIGHT_ARM = (40, 16, 56, 32)
LEFT_ARM = (32, 48, 48, 64)
RIGHT_LEG = (0, 16, 16, 32)
LEFT_LEG = (16, 48, 32, 64)
# Face rectangles inside those regions that the model shows to the front.
HEAD_FRONT = (8, 8, 16, 16)
BODY_FRONT = (20, 20, 28, 32)


def fill(img, box, colour):
    left, top, right, bottom = box
    for x in range(left, right):
        for y in range(top, bottom):
            img.putpixel((x, y), colour)


def grain(img, box, base, dark, light, rng):
    """Flat colour plus a little vertical streaking, so a limb does not read as plastic."""
    fill(img, box, base)
    left, top, right, bottom = box
    for x in range(left, right):
        if rng.random() < 0.35:
            shade = dark if rng.random() < 0.6 else light
            run_top = rng.randrange(top, bottom)
            run_bottom = min(bottom, run_top + rng.randrange(2, 6))
            for y in range(run_top, run_bottom):
                img.putpixel((x, y), shade)


def weave(img, box, base, dark, rng):
    """Sacking: a base colour speckled on a coarse grid."""
    fill(img, box, base)
    left, top, right, bottom = box
    for x in range(left, right):
        for y in range(top, bottom):
            if (x + y) % 3 == 0 and rng.random() < 0.5:
                img.putpixel((x, y), dark)


def stitched_face(img, rng):
    """Two crossed stitches and a sewn mouth on the head's front face."""
    left, top, _, _ = HEAD_FRONT
    for eye_x in (left + 1, left + 5):
        eye_y = top + 2
        for i in range(2):
            img.putpixel((eye_x + i, eye_y + i), STITCH)
            img.putpixel((eye_x + 1 - i, eye_y + i), STITCH)
    mouth_y = top + 6
    for i in range(4):
        img.putpixel((left + 2 + i, mouth_y), STITCH)
    # One seam up the side of the sack, so the head is clearly sewn rather than carved.
    for y in range(top, top + 8):
        if y % 2 == 0:
            img.putpixel((left + 7, y), BURLAP_DARK)


def painted_target(img):
    """Concentric rings over the heart, on the torso's front face."""
    left, top, right, bottom = BODY_FRONT
    centre_x = left + 3.5
    centre_y = top + 4.0
    for x in range(left, right):
        for y in range(top, bottom):
            radius = max(abs(x - centre_x), abs(y - centre_y))
            if radius < 1.0:
                img.putpixel((x, y), TARGET_RED)
            elif radius < 2.0:
                img.putpixel((x, y), TARGET_PALE)
            elif radius < 3.0:
                img.putpixel((x, y), TARGET_RED)


def build_skin():
    rng = random.Random(0xD00D)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    weave(img, HEAD, BURLAP, BURLAP_DARK, rng)
    stitched_face(img, rng)

    weave(img, BODY, CLOTH, CLOTH_DARK, rng)
    painted_target(img)

    for limb in (RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG):
        grain(img, limb, WOOD, WOOD_DARK, WOOD_LIGHT, rng)

    return img


def build_item():
    """A 16x16 dummy in silhouette: sack head, crossbar arms, post, and a foot brace."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    # Head.
    fill(img, (6, 1, 11, 6), BURLAP)
    fill(img, (6, 1, 11, 2), BURLAP_DARK)
    img.putpixel((7, 3), STITCH)
    img.putpixel((9, 3), STITCH)
    for x in range(7, 10):
        img.putpixel((x, 5), STITCH)

    # Crossbar arms.
    fill(img, (2, 7, 15, 9), WOOD)
    fill(img, (2, 8, 15, 9), WOOD_DARK)

    # Torso over the bar, with the target on it.
    fill(img, (6, 6, 11, 12), CLOTH)
    fill(img, (7, 8, 10, 11), TARGET_PALE)
    fill(img, (8, 9, 9, 10), TARGET_RED)

    # Post and brace.
    fill(img, (7, 12, 10, 15), WOOD)
    fill(img, (5, 14, 12, 15), WOOD_DARK)
    return img


def main():
    save(build_skin(), ENTITY_PNG, MARKER)
    save(build_item(), ITEM_PNG, MARKER)
    print("wrote", ENTITY_PNG)
    print("wrote", ITEM_PNG)


if __name__ == "__main__":
    main()
