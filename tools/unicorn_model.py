#!/usr/bin/env python3
"""The Unicorn: rig, skin and animation.

The most-seen placeholder in the mod. Every other box-rigged creature is behind the
`ALPHA_ONLY` spawn gate; the Unicorn is deliberately exempt from it, because unicorn hair is
one of the three wand cores and gating it would dead-end wandmaking. So a player who has
never touched a config has met exactly one bulk-generated box rig, and it was this one.

Canon is a slender horse rather than a draft animal — pure white when fully grown, silver-blue
in the shadows, with a straight tapered horn.

**Its hitbox moved with it, to 1.4 x 1.6 — a vanilla horse's box, because it is a horse.** The
0.95 x 1.25 it had was one of five bulk buckets every generic creature was assigned from, and a
horse does not fit in it: the animal drawn at true proportion is 2.2 blocks nose to tail, so the
box would have ended somewhere around its shoulder and half the creature would have been
unhittable. Six creatures already carry bespoke boxes for the same reason (ghoul, yeti,
rougarou, matagot, pukwudgie, horned_serpent); this is the seventh. Nothing else changes —
`ModCreatures.MANIFEST` and the creature JSON are the only two places the number lives.

Run from the repo root:  python tools/unicorn_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, gait, limb, osc  # noqa: E402

CID = "unicorn"
HITBOX = (1.4, 1.6)
TEX_W = 64

COAT = hx("#F2F1EE")          # white, but never pure — pure white flares against snow
COAT_SHADE = hx("#C9CBD6")    # cool shadow, which is what reads as "unicorn" and not "horse"
BELLY = hx("#FBFBFA")
MANE = hx("#DBE0F0")
MANE_DARK = hx("#A5AEC8")
# Pearl, not ivory: the old cream glowed tan in daylight and read as a bone lance. The spiral is
# drawn in pale gold — the one warm note on a cool white animal (visual style guide, 3.2).
HORN = hx("#F5F3EC")
HORN_DARK = hx("#D6BE78")
HOOF = hx("#9AA2B8")
EYE = hx("#2B3A63")
MUZZLE = hx("#E7DEDB")


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 19, 4))

    # Barrel, chest and croup as three boxes rather than one: a horse is deepest through the
    # girth and narrows to the loin, and one long box is exactly the crate the old rig was.
    rig.cube("body", (-3, 12, -1), (6, 8, 9), key="barrel")
    rig.cube("body", (-3.5, 11, -4), (7, 9, 4), key="chest")

    rig.bone("croup", "body", (0, 19, 8), (-4, 0, 0))
    rig.cube("croup", (-3, 12, 7), (6, 8, 5))

    # Tail authored hanging straight down, then swept back — see the sign rule in `rigkit`:
    # a positive X rotation swings a hanging bone toward +Z, which is how a unicorn carries it.
    rig.bone("tail", "croup", (0, 18, 11), (10, 0, 0))
    rig.cube("tail", (-1, 15, 10), (2, 3, 2), key="tail_dock")
    rig.bone("tail_hair", "tail", (0, 16, 11), (6, 0, 0))
    rig.cube("tail_hair", (-1.5, 7, 10), (3, 9, 3))

    # Neck authored straight up and then raked forward, and the head cancels the rake so it
    # ends level at the top of a raised neck instead of pointing at the sky. Authoring the
    # chain straight and bending it at the joints is the whole trick: every cube stays a plain
    # axis-aligned box and only the two rotations have to be right.
    rig.bone("neck", "body", (0, 19, -1), (40, 0, 0))
    rig.cube("neck", (-2, 19, -3), (4, 9, 4))
    rig.cube("neck", (-1, 20, 0.5), (2, 9, 2), key="crest")

    rig.bone("head", "neck", (0, 28, -1), (-40, 0, 0))
    rig.cube("head", (-2, 23, -7), (4, 5, 6))
    rig.cube("head", (-1.5, 23, -10), (3, 3, 3), key="muzzle")
    # Forelock and ears get their own bones because they move: the forelock trails the head
    # toss a beat late, and an ear that swivels is most of what makes a horse look alive.
    rig.bone("forelock", "head", (0, 28, 0))
    rig.cube("forelock", (-1, 27, 0), (2, 4, 2))

    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, 28, -2.5), (0, 0, sign * -8))
        rig.cube(f"ear_{side}", Rig.mirror((1, 28, -3), (1, 2, 1), sign), (1, 2, 1))

    rig.pair(build_ear)

    # The horn: a two-cube taper on its own bone, raked off the brow. One cube reads as a peg;
    # the step from 2 wide to 1 is what makes it a spiral horn at this resolution.
    # Raked 26 degrees, not 40: at 40 the head pitch carried it level and it read as a lance.
    rig.bone("horn", "head", (0, 28, -6), (26, 0, 0))
    rig.cube("horn", (-1, 28, -7), (2, 3, 2), key="horn_base")
    rig.cube("horn", (-0.5, 31, -6.5), (1, 5, 1), key="horn_tip")

    forelegs = {}
    hindlegs = {}

    def build_foreleg(side, sign):
        forelegs[side] = limb(rig, f"foreleg_{side}", "body", x=2.5, z=-2, sign=sign, joints=[
            ("", 14, 7, 3, 3, None),
            ("cannon", 7, 5, 2, 2, None),
            ("hoof", 2, 2, 3, 3, None),
        ])

    def build_hindleg(side, sign):
        hindlegs[side] = limb(rig, f"hindleg_{side}", "croup", x=2.5, z=9, sign=sign, joints=[
            ("", 16, 8, 4, 5, (10, 0, 0)),
            ("hock", 8, 6, 2, 3, (-14, 0, 0)),
            ("hoof", 2, 2, 3, 3, (4, 0, 0)),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig, forelegs, hindlegs


def paint(rig, tex_h, forelegs, hindlegs):
    skin = Skin(rig, tex_h)

    body = ["barrel", "chest", "croup"]
    skin.skin(body, COAT, bottom=BELLY, dither=0.05, dither_colour=COAT_SHADE)
    skin.skin(["neck", "head", "tail_dock"], COAT, dither=0.05, dither_colour=COAT_SHADE)

    for keys in list(forelegs.values()) + list(hindlegs.values()):
        skin.skin(keys, COAT, dither=0.04, dither_colour=COAT_SHADE)
    # Hooves and fetlocks: the darkest thing on the animal, and the only place the silhouette
    # touches the ground, so it is worth the two texels.
    skin.skin([k for k in rig.keys("foreleg", "hindleg") if "hoof" in k], HOOF, dither=0.0)

    # Mane and tail read as hair because they are a different, cooler colour with combed
    # banding — not because they are a different shape.
    skin.skin(["crest", "forelock", "tail_hair"], MANE, dither=0.0)
    skin.bands(["crest", "forelock", "tail_hair"], MANE_DARK, step=3)

    skin.skin(["horn_base", "horn_tip"], HORN, dither=0.0)
    skin.glow(["horn_base", "horn_tip"])   # the one magical thing about a horse
    # The spiral: a dark texel stepping one column along per row up the horn.
    for key, rows in (("horn_base", 4), ("horn_tip", 6)):
        for face in ("east", "north", "west", "south"):
            x, y, w, h = skin.face(key, face)
            # Every other row: on the one-wide tip a texel per row would gild it solid.
            for row in range(0, h, 2):
                skin.d.point((x + ((row // 2) % max(1, w)), y + row), fill=HORN_DARK)

    skin.skin("muzzle", MUZZLE, dither=0.06, dither_colour=COAT_SHADE)
    for dx in (0, -1):
        skin.mark("muzzle", "north", dx, -1, hx("#7E6A68"))

    # Eyes sit on the sides of the skull, as they do on any prey animal. A forward-facing pair
    # on a 4-wide head would read as a predator, and would not fit the face besides.
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)

    skin.skin(["ear_left", "ear_right"], COAT, dither=0.0)
    skin.tip(["ear_left", "ear_right"], MANE_DARK, rows=1)
    return skin


def build_anim():
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5,
            "hindleg_left": 0.5, "hindleg_right": 0.0}

    idle = {
        "body": bob(4.0, 0.15, cycles=2),            # breath, carried by the whole barrel
        "neck": osc(4.0, 3.0, phase=0.1),
        "head": {"rotation": rigkit.kf((0.0, (0, -8, 0)), (1.3, (2, 9, 0)),
                                       (2.6, (-2, -5, 0)), (4.0, (0, -8, 0)))},
        "tail": osc(4.0, 5.0, axis="z", phase=0.25),
        "ear_left": osc(4.0, 7.0, phase=0.0),
        "ear_right": osc(4.0, 7.0, phase=0.4),
    }

    walk = {
        **gait(0.9, legs, amp=24.0),
        **{f"{name}_cannon": osc(0.9, 14.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("foreleg")},
        **{f"{name}_hock": osc(0.9, 16.0, phase=phase + 0.15)
           for name, phase in legs.items() if name.startswith("hindleg")},
        "body": osc(0.9, 2.0, axis="y", phase=0.0),
        "root": bob(0.9, 0.5, cycles=2),
        "neck": osc(0.9, 3.0, phase=0.25),
        "tail_hair": osc(0.9, 6.0, phase=0.5),
    }

    # `call` rides the ambient-sound beat (`GenericBeastEntity.playAmbientSound` fires the
    # first of groan/hiss/howl/call/song a creature declares). The unicorn's is a head toss —
    # the horn sweeps up and the forelock follows it a beat late.
    call = {
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-26, 0, 0)),
                                       (0.9, (-14, 0, 0)), (1.4, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (14, 0, 0)),
                                       (0.9, (4, 0, 0)), (1.4, (0, 0, 0)))},
        "forelock": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.45, (22, 0, 0)),
                                           (1.0, (-6, 0, 0)), (1.4, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.35, (-38, 0, 0)),
                                               (0.7, (12, 0, 0)), (1.4, (0, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.7, (-12, 0, 0)), (1.4, (0, 0, 0)))},
    }

    # `flinch` is the damage reaction. The unicorn is FEARFUL and blinks away rather than
    # fighting, so the recoil is a shy away from the hit, not a wind-up.
    flinch = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.12, (6, 0, -8)), (0.5, (0, 0, 0)))},
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.12, (16, -14, 0)), (0.5, (0, 0, 0)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.12, (0.6, 0, 0.8)), (0.5, (0, 0, 0)))},
    }

    return {
        "idle": clip(4.0, idle),
        "walk": clip(0.9, walk),
        "call": clip(1.4, call, loop=False),
        "flinch": clip(0.5, flinch, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig, forelegs, hindlegs = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    skin = paint(rig, tex_h, forelegs, hindlegs)
    return rigkit.emit(rig, skin, build_anim(), cid=CID, tool="unicorn_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force, resize=True)


if __name__ == "__main__":
    sys.exit(main())
