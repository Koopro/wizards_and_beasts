#!/usr/bin/env python3
"""The Puffskein: rig, skin and animation — and the recipe the Pygmy Puff is bred from.

Canon: spherical, custard-coloured fur, and a long thin tongue it uses to eat things out of
people's noses. It hums when it is content. That is the brief, and a ball is the hardest shape
there is to build out of boxes.

**A sphere from boxes is a cross, not a cube.** One core cube plus three slabs through it —
wide, deep and tall, each a little narrower than the core on the other two axes — puts a
bevel on all eight corners, and at this size that bevel is the whole difference between a
ball of fur and a crate. The eyes sit on the front slab, so they are proud of the core and
catch light.

**Drawn oversize.** The hitbox is 0.45 blocks, which is 7 texels, and a face needs more than
that: box UV is one texel per model unit, so the budget for eyes is set by geometry, not by
the painter. The ball is 10 units across, the same trick the mooncalf needed.

`tools/pygmy_puff_model.py` calls `generate()` with a smaller ball and a pink coat: a Pygmy
Puff *is* a miniature Puffskein, bred by Fred and George, so it shares this skeleton and its
animation shape by construction.

Run from the repo root:  python tools/puffskein_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, shade  # noqa: E402
from rigkit import Rig, Skin, clip, osc  # noqa: E402

PUFFSKEIN = {
    "cid": "puffskein",
    "tool": "puffskein_model.py",
    "hitbox": (0.45, 0.45),
    "core": 8,          # the ball is core + 2 across on the wide slabs
    "fur": hx("#E6C77A"),
    "fur_dark": hx("#B99A4E"),
    "fur_lit": hx("#F5E3AE"),
    "tongue": hx("#D9677E"),
    "eye": hx("#1E1712"),
}


def build(spec):
    cid, c = spec["cid"], spec["core"]
    half = c / 2.0
    rig = Rig(cid, 64)

    # Everything that squashes lives under `body`, pivoted at the floor, so a scale track on it
    # flattens the ball onto the ground instead of shrinking it toward its own centre.
    rig.bone("body", "root", (0, 0, 0))
    rig.cube("body", (-half, 1, -half), (c, c, c), key="core")
    rig.cube("body", (-half - 1, 2, -half + 1), (c + 2, c - 2, c - 2), key="slab_wide")
    rig.cube("body", (-half, 2, -half - 1), (c, c - 2, c + 2), key="slab_deep")
    rig.cube("body", (-half + 1, 0, -half + 1), (c - 2, c + 2, c - 2), key="slab_tall")

    # The tongue: its own bone so it can shoot out. Rest length is short and tucked under the
    # front slab; the clips scale it along Z.
    rig.bone("tongue", "body", (0, 3, -half - 1))
    rig.cube("tongue", (-0.5, 2.5, -half - 3), (1, 1, 3))

    # Two feet so tiny they are nearly paint, but they are what makes it walk rather than roll.
    rig.pair(lambda side, sign: (
        rig.bone(f"foot_{side}", "body", (sign * (half - 2), 1, 0)),
        rig.cube(f"foot_{side}", Rig.mirror((half - 3, 0, -1.5), (2, 1, 3), sign), (2, 1, 3)),
    ))
    return rig


def paint(rig, tex_h, spec):
    # `grain` "speckle" is the pre-2026-09-27 dither, which the Pygmy Puff still ships with.
    legacy = spec.get("grain") == "speckle"
    skin = Skin(rig, tex_h, grain="speckle") if legacy else Skin(rig, tex_h)
    fur, dark, lit = spec["fur"], spec["fur_dark"], spec["fur_lit"]
    body = ["core", "slab_wide", "slab_deep", "slab_tall"]

    # Fur is the one surface in this wave where a denser dither is right: it is supposed to
    # look soft and irregular, and a flat fill on a ball reads as plastic.
    skin.skin(body, fur, top=lit, dither=0.16, dither_colour=dark)
    skin.dither(skin.face("slab_tall", "top"), lit, 0.2, 7, grain="speckle" if legacy else "fleck")
    skin.skin(["foot_left", "foot_right"], dark, dither=0.0)
    skin.skin("tongue", spec["tongue"], dither=0.0)
    skin.tip("tongue", shade(spec["tongue"], 0.8), rows=1)

    # Eyes on the deep slab's front face: two beads with a glint. Placed from the face size so
    # the Pygmy Puff's smaller ball gets them in proportion without its own numbers.
    x, y, w, h = skin.face("slab_deep", "north")
    ey = y + max(1, h // 3)
    gap = 2 if w >= 8 else 1
    for ex in (x + w // 2 - gap // 2 - 2 - (gap % 2), x + w // 2 + (gap + 1) // 2):
        skin.d.rectangle([ex, ey, ex + 1, ey + 1], fill=spec["eye"])
        skin.d.point((ex + 1, ey), fill=hx("#F4F0E6"))
    return skin


def build_anim(spec):
    squash = {"scale": rigkit.kf((0.0, (1, 1, 1)), (1.5, (1.04, 0.95, 1.04)), (3.0, (1, 1, 1)))}

    idle = {
        "body": squash,
        # The tongue flicks out and back twice per cycle. It is the only thing a puffskein does
        # that anyone remembers, so the idle shows it rather than saving it for a clip.
        "tongue": {"scale": rigkit.kf((0.0, (1, 1, 1)), (0.8, (1, 1, 1)), (0.95, (1, 1, 2.6)),
                                      (1.15, (1, 1, 1)), (2.2, (1, 1, 1)), (2.35, (1, 1, 2.2)),
                                      (2.5, (1, 1, 1)), (3.0, (1, 1, 1)))},
    }

    # Walking is hopping: the ball squashes on landing and stretches at the top of the hop. The
    # feet paddle underneath at twice the rate, which is what sells effort at this size.
    walk = {
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.2, (0, 2.2, 0)),
                                       (0.4, (0, 0, 0)))},
        "body": {"scale": rigkit.kf((0.0, (1.12, 0.86, 1.12)), (0.12, (0.94, 1.08, 0.94)),
                                    (0.3, (1, 1, 1)), (0.4, (1.12, 0.86, 1.12)))},
        "foot_left": osc(0.4, 40.0, phase=0.0),
        "foot_right": osc(0.4, 40.0, phase=0.5),
    }

    # `song` is the hum, on the ambient beat: a fast shiver through the whole ball.
    song = {
        "body": {"rotation": rigkit.kf((0.0, (0, 0, 0)), (0.1, (0, 0, 4)), (0.2, (0, 0, -4)),
                                       (0.3, (0, 0, 4)), (0.4, (0, 0, -4)), (0.5, (0, 0, 3)),
                                       (0.6, (0, 0, -3)), (0.8, (0, 0, 0)), (1.2, (0, 0, 0))),
                 "scale": rigkit.kf((0.0, (1, 1, 1)), (0.6, (1.06, 0.96, 1.06)),
                                    (1.2, (1, 1, 1)))},
    }

    flinch = {
        "body": {"scale": rigkit.kf((0.0, (1, 1, 1)), (0.08, (0.82, 1.16, 0.82)),
                                    (0.4, (1, 1, 1)))},
        "root": {"position": rigkit.kf((0.0, (0, 0, 0)), (0.1, (0, 1.2, 1.4)),
                                       (0.4, (0, 0, 0)))},
    }

    return {
        "idle": clip(3.0, idle),
        "walk": clip(0.4, walk),
        "song": clip(1.2, song, loop=False),
        "flinch": clip(0.4, flinch, loop=False),
    }


def generate(spec, force):
    rig = build(spec)
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h, spec), build_anim(spec), cid=spec["cid"],
                       tool=spec["tool"], hitbox=spec["hitbox"], tex_h=tex_h, force=force)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    return generate(PUFFSKEIN, ap.parse_args().force)


if __name__ == "__main__":
    sys.exit(main())
