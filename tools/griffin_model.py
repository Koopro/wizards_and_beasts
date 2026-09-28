#!/usr/bin/env python3
"""The Griffin: rig, skin and animation.

Eagle in front, lion behind — which makes it the Hippogriff's near neighbour and the one rig in
this wave at real risk of reading as a recolour of it. The separation is deliberate and it is
all in the back half: the Hippogriff is eagle-over-*horse*, so it has hooves, a hanging horse
tail and a long straight barrel. The Griffin is eagle-over-*lion*: padded feline paws, a whip
tail ending in a tuft, a shorter deeper body, and ear tufts off the skull the Hippogriff does
not have.

Flying locomotion, so the movement controller binds `idle` and `fly` — no walk clip, because
nothing would ever play it.

Run from the repo root:  python tools/griffin_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix  # noqa: E402
from rigkit import Rig, Skin, bob, clip, limb, osc  # noqa: E402

CID = "griffin"
HITBOX = (1.7, 1.9)
TEX_W = 128

PLUME = hx("#E9E4D0")
PLUME_LIT = hx("#FAF7EC")
PLUME_DARK = hx("#A9A184")
PELT = hx("#9C6A2C")
PELT_DARK = hx("#6A461A")
BELLY = hx("#BE9151")
BEAK = hx("#E0A431")
BEAK_DARK = hx("#A97318")
TALON = hx("#3A3126")
PAD = hx("#6A4C26")
EYE = hx("#E87A28")
EYE_DARK = hx("#2A1A0C")

# Rest lift of the folded wings' trailing edge (degrees about X): enough that the primaries rise
# over the croup in side view.
WING_MANTLE = 18


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 22, 2))

    # Lion behind, eagle in front, joined at the withers. The chest is deeper and set higher
    # than the barrel, which is what stops the two halves reading as one long tube.
    rig.cube("body", (-5, 14, 0), (10, 10, 11), key="barrel")
    rig.cube("body", (-6, 12, -10), (12, 14, 10), key="breast")
    # A bird's chest, not a horse's: the keel bulges forward past the shoulders and the breast
    # feathers hang below the belly line. Both are in the silhouette from the side, which is
    # where the old rig was a straight-chested horse (silhouette pass 2026-09-27).
    rig.cube("body", (-4.5, 13, -13), (9, 10, 3), key="keel", inflate=0.35)
    rig.cube("body", (-5, 10, -11), (10, 3, 7), key="bib", inflate=0.2)

    rig.bone("croup", "body", (0, 23, 10), (-6, 0, 0))
    rig.cube("croup", (-5, 14, 9), (10, 10, 6))

    # Whip tail with a lion's tuft — the clearest single difference from the Hippogriff's
    # hanging horse tail.
    rig.bone("tail", "croup", (0, 21, 15), (20, 0, 0))
    rig.cube("tail", (-1, 20, 15), (2, 2, 8))
    rig.bone("tail_tuft", "tail", (0, 21, 23), (12, 0, 0))
    rig.cube("tail_tuft", (-2, 19, 23), (4, 4, 5))

    # A short eagle neck with a ruff of hackles round its base, wider than the neck, so the head
    # sits on a collar of feathers instead of on a horse's long column.
    rig.bone("neck", "body", (0, 25, -7), (22, 0, 0))
    rig.cube("neck", (-3.5, 25, -11), (7, 8, 7))
    rig.cube("neck", (-5, 24, -12), (10, 4, 9), key="ruff", inflate=0.3)

    rig.bone("head", "neck", (0, 33, -7), (-30, 0, 0))
    rig.cube("head", (-4, 28, -15), (8, 7, 8))
    # The brow shelf over the eyes: an eagle's scowl, and a step in the head's outline.
    rig.cube("head", (-4.5, 33, -15.5), (9, 1, 3), key="brow")
    # Short, deep and hooked. The old six-long flat bill read as a duck's.
    rig.bone("beak_upper", "head", (0, 32, -14))
    rig.cube("beak_upper", (-2, 29, -19), (4, 4, 5))
    rig.cube("beak_upper", (-1.5, 27, -20), (3, 3, 2), key="beak_hook")
    rig.bone("beak_lower", "head", (0, 30, -14), (-4, 0, 0))
    rig.cube("beak_lower", (-1.5, 28, -18), (3, 1, 4))

    # Ear tufts. Eagles do not have them; griffins in every depiction do, and they are what the
    # eye picks up first at distance.
    def build_tuft(side, sign):
        rig.bone(f"tuft_{side}", "head", (sign * 2.5, 35, -11), (-14, 0, sign * -26))
        rig.cube(f"tuft_{side}", Rig.mirror((1.5, 35, -12), (2, 5, 2), sign), (2, 5, 2))

    rig.pair(build_tuft)

    # Wings: two segments each so the wing has an elbow and can fold. Held mantled, the trailing
    # edge lifted so the primaries cross above the croup: a wing folded flat against the flank
    # reads as a saddle blanket and leaves the outline a horse's.
    def build_wing(side, sign):
        rig.bone(f"wing_{side}", "shoulders", (sign * 5, 27, -5), (WING_MANTLE, 0, sign * -14))
        rig.cube(f"wing_{side}", Rig.mirror((5, 16, -6), (2, 12, 13), sign), (2, 12, 13))
        rig.bone(f"wing_{side}_tip", f"wing_{side}", (sign * 6, 22, 7), (0, sign * -18, sign * -8))
        # Three flight-feather plates stepping 8/10/12 long down the chord: the trailing edge
        # ends in feather tips instead of a square plank (visual style guide, 1.5).
        for i, (y0, h, length) in enumerate(((19, 4, 8), (16, 3, 10), (13, 3, 12))):
            rig.cube(f"wing_{side}_tip", Rig.mirror((5, y0, 7), (1, h, length), sign), (1, h, length),
                     key=f"wing_{side}_tip_p{i}")

    # One shoulder bone carries both the wings and the eagle forelimbs, so a dive folds the
    # whole front of the animal together instead of shearing the wings off the legs.
    rig.bone("shoulders", "body", (0, 25, -5))
    rig.pair(build_wing)

    # Eagle forelimbs, three shapes a horse leg never has: a feathered "trouser" thigh wider than
    # the shank below it, a thin yellow scaled tarsus raked forward, and a splayed foot of three
    # toes forward and one back, each ending in a dark hooked talon.
    def build_foreleg(side, sign):
        limb(rig, f"foreleg_{side}", "shoulders", x=4, z=-5, sign=sign, joints=[
            ("", 16, 6, 6, 6, (4, 0, 0)),
            ("shank", 11, 9, 2, 2, (-12, 0, 0)),
            ("claw", 2, 2, 3, 3, (8, 0, 0)),
        ])
        # The thigh feathers fall over the knee.
        rig.cube(f"foreleg_{side}", Rig.mirror((1.5, 9, -7.5), (5, 2, 5), sign), (5, 2, 5),
                 key=f"foreleg_{side}_fringe", inflate=0.2)
        foot = f"foreleg_{side}_claw"
        pivot = (sign * 4, 0.5, -5)
        for i, (yaw, length) in enumerate(((0, 5), (30, 4), (-30, 4))):
            rot = (0, sign * yaw, 0) if yaw else None
            width = 2 if i == 0 else 1
            rig.cube(foot, Rig.mirror((4 - width / 2.0, 0, -5 - length), (width, 1, length), sign),
                     (width, 1, length), key=f"foreleg_{side}_toe{i}", rotation=rot, pivot=pivot)
            rig.cube(foot, Rig.mirror((3.5, 0, -7 - length), (1, 2, 2), sign), (1, 2, 2),
                     key=f"foreleg_{side}_talon{i}", rotation=rot, pivot=pivot)
        rig.cube(foot, Rig.mirror((3.5, 0, -5), (1, 1, 3), sign), (1, 1, 3), key=f"foreleg_{side}_toe_back")
        rig.cube(foot, Rig.mirror((3.5, 0, -2), (1, 2, 2), sign), (1, 2, 2), key=f"foreleg_{side}_talon_back")

    def build_hindleg(side, sign):
        limb(rig, f"hindleg_{side}", "croup", x=4, z=12, sign=sign, joints=[
            ("", 16, 9, 6, 8, (12, 0, 0)),
            ("shank", 8, 6, 4, 5, (-18, 0, 0)),
            ("paw", 3, 3, 5, 7, None),
        ])

    rig.pair(build_foreleg)
    rig.pair(build_hindleg)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    fore = rig.keys("foreleg")
    thighs = [k for k in fore if k in ("foreleg_left", "foreleg_right") or "fringe" in k]
    talons = [k for k in fore if "talon" in k]
    scaled = [k for k in fore if k not in thighs and k not in talons]
    hind = [k for k in rig.keys("hindleg")]
    wings = rig.keys("wing_")

    skin.skin(["barrel", "croup"], PELT, bottom=BELLY, dither=0.05, dither_colour=PELT_DARK)
    skin.skin(hind, PELT, dither=0.04, dither_colour=PELT_DARK)
    skin.skin([k for k in hind if "paw" in k], PAD, dither=0.0)
    skin.skin(["tail"], PELT, dither=0.04, dither_colour=PELT_DARK)
    skin.skin("tail_tuft", PELT_DARK, dither=0.1, dither_colour=PELT)

    skin.skin(["breast", "keel", "bib", "neck", "ruff", "head", "brow"], PLUME, dither=0.05,
              dither_colour=PLUME_DARK)
    skin.skin(thighs, PLUME, dither=0.04, dither_colour=PLUME_DARK)
    # Layered breast and trouser feathers: sparse offset rows of tips, so the chest reads as
    # plumage. Every-other-texel tips made a regular grid, the lattice the style guide rules out.
    for key in ["keel", "bib", "ruff"] + thighs:
        skin.feathers(key, ("north", "east", "west", "south"), mix(PLUME, PLUME_DARK, 0.6), PLUME_LIT,
                      step=3, rows=3)
    skin.skin(scaled, BEAK, dither=0.0)
    skin.bands([k for k in scaled if "shank" in k], BEAK_DARK, faces=("north", "east", "west", "south"),
               step=2)
    skin.skin(talons, TALON, dither=0.0)
    skin.skin(["tuft_left", "tuft_right"], PLUME_DARK, dither=0.0)

    # Flight feathers: a ramp out to a pale tip plus barring across it, so each wing slab reads
    # as layered quills rather than a painted board.
    skin.skin(wings, PLUME, dither=0.0)
    skin.ramp(wings, PLUME_DARK, PLUME_LIT, faces=("east", "west"))
    skin.bands(wings, PLUME_DARK, faces=("east", "west"), step=3)
    skin.tip(wings, PLUME_DARK, rows=1, faces=("east", "west"))

    # The seam. Plumage frays out over the first rows of the lion barrel instead of stopping
    # dead at the withers, which is the one hard part of drawing a two-animal creature.
    for face in ("east", "west", "top"):
        x, y, w, h = skin.face("barrel", face)
        for col in range(w):
            for row in range(min(h, 1 + (col * 2654435761 >> 5) % 3)):
                skin.d.point((x + col, y + row),
                             fill=PLUME if (col + row) % 2 else PLUME_DARK)

    skin.skin(["beak_upper", "beak_lower"], BEAK, dither=0.0)
    skin.skin("beak_hook", BEAK_DARK, dither=0.0)
    skin.eyes("head", EYE, row=1, inset=1, pupil=EYE_DARK)
    return skin


def build_anim():
    idle = {
        "body": bob(4.0, 0.2, cycles=2),
        "neck": osc(4.0, 3.0, phase=0.1),
        "head": {"rotation": rigkit.kf((0.0, (0, -12, 0)), (1.3, (3, 14, 0)),
                                       (2.6, (-2, -7, 0)), (4.0, (0, -12, 0)))},
        "tuft_left": osc(4.0, 8.0, phase=0.0),
        "tuft_right": osc(4.0, 8.0, phase=0.4),
        "wing_left": osc(4.0, 5.0, axis="z", phase=0.0),
        "wing_right": osc(4.0, 5.0, axis="z", phase=0.5),
        "tail": osc(4.0, 7.0, axis="z", phase=0.2),
        "tail_tuft": osc(4.0, 12.0, axis="z", phase=0.4),
    }

    # One full beat per second. The tip trails the root so the stroke reads as a surface being
    # pulled through air rather than as a rigid plank hinged at the shoulder.
    fly = {
        "wing_left": {"rotation": rigkit.kf((0.0, (0, 0, -48)), (0.35, (0, 0, 20)),
                                            (0.7, (0, 0, -34)), (1.0, (0, 0, -48)))},
        "wing_right": {"rotation": rigkit.kf((0.0, (0, 0, 48)), (0.35, (0, 0, -20)),
                                             (0.7, (0, 0, 34)), (1.0, (0, 0, 48)))},
        "wing_left_tip": {"rotation": rigkit.kf((0.0, (0, 0, -22)), (0.45, (0, 0, 28)),
                                                (0.8, (0, 0, -16)), (1.0, (0, 0, -22)))},
        "wing_right_tip": {"rotation": rigkit.kf((0.0, (0, 0, 22)), (0.45, (0, 0, -28)),
                                                 (0.8, (0, 0, 16)), (1.0, (0, 0, 22)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (54, 0, 0)), (1.0, (54, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (54, 0, 0)), (1.0, (54, 0, 0)))},
        "hindleg_left": {"rotation": rigkit.kf((0.0, (36, 0, 0)), (1.0, (36, 0, 0)))},
        "hindleg_right": {"rotation": rigkit.kf((0.0, (36, 0, 0)), (1.0, (36, 0, 0)))},
        "tail": {"rotation": rigkit.kf((0.0, (-14, 0, 0)), (0.5, (-20, 0, 0)),
                                       (1.0, (-14, 0, 0)))},
        "body": osc(1.0, 4.0, phase=0.1),
    }

    # `strike` is the dive-bomb landing: wings back, talons forward, whole body behind it.
    strike = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-26, 0, 0)),
                                       (0.55, (14, 0, 0)), (0.9, (0, 0, 0)))},
        "foreleg_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-72, 0, -10)),
                                               (0.5, (40, 0, 6)), (0.9, (0, 0, 0)))},
        "foreleg_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-72, 0, 10)),
                                                (0.55, (40, 0, -6)), (0.9, (0, 0, 0)))},
        "wing_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (0, 0, -52)),
                                            (0.9, (0, 0, 0)))},
        "wing_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (0, 0, 52)),
                                             (0.9, (0, 0, 0)))},
        "beak_lower": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (30, 0, 0)),
                                             (0.6, (0, 0, 0)), (0.9, (0, 0, 0)))},
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.25, (-18, 0, 0)), (0.9, (0, 0, 0)))},
    }

    # `call` is the screech, on the ambient beat: head up, beak wide, tufts flat.
    call = {
        "neck": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-28, 0, 0)),
                                       (1.0, (-22, 0, 0)), (1.4, (0, 0, 0)))},
        "head": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-12, 0, 0)),
                                       (1.0, (-8, 0, 0)), (1.4, (0, 0, 0)))},
        "beak_lower": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (34, 0, 0)),
                                             (1.0, (28, 0, 0)), (1.4, (0, 0, 0)))},
        "tuft_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-24, 0, -14)),
                                            (1.4, (0, 0, 0)))},
        "tuft_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.3, (-24, 0, 14)),
                                             (1.4, (0, 0, 0)))},
        "wing_left": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (0, 0, -30)),
                                            (1.4, (0, 0, 0)))},
        "wing_right": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.4, (0, 0, 30)),
                                             (1.4, (0, 0, 0)))},
    }

    return {
        "idle": clip(4.0, idle),
        "fly": clip(1.0, fly),
        "strike": clip(0.9, strike, loop=False),
        "call": clip(1.4, call, loop=False),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rig = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h), build_anim(), cid=CID, tool="griffin_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force)


if __name__ == "__main__":
    sys.exit(main())
