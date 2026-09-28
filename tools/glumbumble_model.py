#!/usr/bin/env python3
"""The Glumbumble: rig, skin and animation.

Canon: a grey, furry flying insect that makes melancholy-inducing treacle, used as an antidote to
the hysteria caused by eating Alihotsy leaves; it nests in dark secluded places and infests
beehives. `dread_aura`, `spore_cloud`.

A bumblebee with all the colour taken out of it: fat banded abdomen in two greys, a fuzzy thorax,
small smoky wings that look too small to lift it. The only warmth is the treacle-amber of the
eyes, which are the lit part.

Run from the repo root:  python tools/glumbumble_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground  # noqa: E402
from insect import buzz, hover  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "glumbumble"
HITBOX = (0.45, 0.45)

FUR = hx("#8A8C90")
FUR_DARK = hx("#4A4C50")
FUR_LIT = hx("#B0B2B6")
WING = hx("#6E747C")
EYE = hx("#C8882A")


def build():
    rig = Rig(CID, 64)
    rig.bone("body", "root", (0, 4, 0))
    # Rounded, not boxed (silhouette pass 2026-09-27): the thorax is a core with a slab through it
    # on every axis, so its outline steps round instead of reading as one cube, and the fur is
    # the step.
    rig.cube("body", (-2, 3, -2), (4, 4, 4), key="thorax", inflate=0.2)
    rig.cube("body", (-3, 4, -1), (6, 2, 2), key="thorax_wide", inflate=0.3)
    rig.cube("body", (-1, 2, -1), (2, 6, 2), key="thorax_tall", inflate=0.3)
    rig.cube("body", (-1, 4, -3), (2, 2, 6), key="thorax_deep", inflate=0.3)
    # A round head hung low at the front, and two feelers: at this size the feelers are what says
    # "insect" before anything else does.
    rig.cube("body", (-1.5, 3, -5), (3, 3, 3), key="head", inflate=0.2)
    for side, sign in (("l", 1), ("r", -1)):
        rig.cube("body", Rig.mirror((0.5, 6, -5), (1, 3, 1), sign), (1, 3, 1), key=f"feeler_{side}",
                 rotation=(-35, 0, sign * -20), pivot=(sign * 1, 6, -4.5))
    # The fat abdomen that makes it a bumblebee: rounded the same way, drooping behind, and
    # tapering to a point instead of ending square.
    rig.bone("abdomen", "body", (0, 5, 2), (-18, 0, 0))
    rig.cube("abdomen", (-2.5, 2, 2), (5, 5, 6), key="abdomen")
    rig.cube("abdomen", (-3.5, 3, 3), (7, 3, 4), key="abdomen_wide", inflate=0.2)
    rig.cube("abdomen", (-1.5, 1, 3), (3, 7, 4), key="abdomen_tall", inflate=0.2)
    rig.cube("abdomen", (-1.5, 3, 8), (3, 3, 2), key="abdomen_tip")
    for i, z in enumerate((-1, 1)):
        for side, sign in (("l", 1), ("r", -1)):
            rig.cube("body", Rig.mirror((1.5, 0, z - 0.5), (1, 3, 1), sign), (1, 3, 1), key=f"leg_{side}{i}")

    # Wings set on top of the thorax and swept back over the abdomen, the way a bee holds them,
    # with a smaller hind wing behind the fore wing: a wing shape, where the old flat blades
    # standing out sideways made a V over a box.
    def build_wing(side, sign):
        bone = f"wing_{side}"
        rig.bone(bone, "body", (sign * 1, 7.5, -0.5), (10, sign * 42, sign * 22))
        rig.cube(bone, Rig.mirror((1, 7.5, -1), (4, 1, 6), sign), (4, 1, 6), key=f"wing_{side}")
        rig.cube(bone, Rig.mirror((1, 7.5, 5), (3, 1, 3), sign), (3, 1, 3), key=f"wing_{side}_hind")

    rig.pair(build_wing)
    ground(rig)
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    thorax = rig.keys("thorax")
    skin.skin(thorax + ["head"], FUR, top=FUR_LIT, dither=0.3, dither_colour=FUR_DARK)
    abdomen = rig.keys("abdomen")
    skin.skin(abdomen, FUR, dither=0.15, dither_colour=FUR_LIT)
    skin.bands(abdomen, FUR_DARK, faces=("top", "east", "west", "bottom"), step=2)
    skin.skin("abdomen_tip", FUR_DARK, dither=0.0)
    skin.skin(rig.keys("leg_", "feeler_"), FUR_DARK, dither=0.0)
    wings = rig.keys("wing_")
    skin.skin(wings, WING, dither=0.0)
    x, y, w, h = skin.face("head", "north")
    for ex in (x, x + w - 1):
        skin.d.point((ex, y + 1), fill=EYE)
        skin.glow_rect((ex, y + 1, 1, 1))
    return skin


def anims():
    b = 0.1
    idle = {**buzz(b, amp=30), "root": hover(b * 16, 0.6),
            "abdomen": {"rotation": kf((0.0, (0, 0, 0)), (b * 8, (6, 0, 0)), (b * 16, (0, 0, 0)))}}
    fly = {**buzz(b, amp=36), "root": hover(b * 6, 0.5), "body": {"rotation": kf((0.0, (12, 0, 0)), (b * 6, (12, 0, 0)))}}
    flinch = {**buzz(b, amp=30), "abdomen": {"rotation": kf((0.0, (0, 0, 0)), (0.1, (30, 0, 0)), (0.4, (0, 0, 0)))}}
    # `groan`: the drone that sets the mood — the whole insect sinks and sways.
    groan = {**buzz(b * 2, amp=16),
             "root": {"position": kf((0.0, (0, 0, 0)), (0.8, (0, -1.5, 0)), (1.6, (0, 0, 0)))},
             "body": {"rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 10)), (1.2, (0, 0, -10)), (1.6, (0, 0, 0)))}}
    return {"idle": clip(b * 16, idle), "fly": clip(b * 6, fly),
            "flinch": clip(0.4, flinch, loop=False), "groan": clip(1.6, groan, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "glumbumble_model.py", build, paint, anims, HITBOX))
