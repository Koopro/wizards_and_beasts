#!/usr/bin/env python3
"""The Runespoor: rig, skin and animation.

Canon: a three-headed African snake, six to seven feet long, vivid orange with black stripes. The
left head (from the wizard's view) plans, the middle dreams, and the right criticises — and only
the right has venomous fangs; the heads often attack one another, so a runespoor is rarely seen
with all three. Parselmouths favour it. `RunespoorEntity` is bespoke and binds only `idle` and
`walk`.

The three heads are the animal, each on its own two-bone neck forking off the front of one body,
and they move independently in the idle — the planner watching, the dreamer drifting, the critic
turned on the other two — because a runespoor whose heads move in unison is a hydra.

Run from the repo root:  python tools/runespoor_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "runespoor"
HITBOX = (0.95, 1.25)

ORANGE = hx("#E07A26")
ORANGE_LIT = hx("#F4A04A")
STRIPE = hx("#1C1614")
BELLY = hx("#F2D09A")
FANG = hx("#ECE6D2")
EYE = hx("#F0E04A")


def build():
    rig = Rig(CID, 64)
    body = chain(rig, "seg", "root", start=(0, 2, 0), direction=1,
                 segments=[(5, 4, 3), (5, 4, 3), (5, 3, 3), (5, 3, 2), (4, 2, 2), (4, 1, 1)],
                 rotations=[(0, 0, 0), (0, 30, 0), (0, -40, 0), (0, 40, 0), (0, -35, 0), (0, 25, 0)])
    heads = []
    for name, yaw, lift in (("planner", 34, 45), ("dreamer", 0, 65), ("critic", -34, 45)):
        necks = chain(rig, f"neck_{name}", body[0], start=(0, 2.5, 0), direction=-1,
                      segments=[(4, 2, 2), (4, 2, 2)], rotations=[(-lift, yaw, 0), (lift * 0.6, 0, 0)])
        # Negative X raises a forward-pointing chain. The first pass used +lift, which drove all
        # three necks into the floor; `ground()` then lifted the snake to meet them and it lay
        # flat, the three heads indistinguishable — the one thing a Runespoor must not be.
        rig.bone(f"head_{name}", necks[-1], (0, 2.5, -8))
        rig.cube(f"head_{name}", (-1.5, 1.5, -11), (3, 2, 3), key=f"head_{name}")
        heads.append(f"head_{name}")
    rig.pair(lambda side, sign: rig.cube("head_critic", Rig.mirror((0.5, 0.5, -11), (1, 1, 1), sign), (1, 1, 1),
                                         key=f"fang_{side}"))
    ground(rig)
    return rig, body, heads


def paint(rig, tex_h, body, heads):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), ORANGE, top=ORANGE_LIT, bottom=BELLY, dither=0.0)
    skin.bands(list(body) + rig.keys("neck_"), STRIPE, faces=("top", "east", "west"), step=3)
    skin.skin(rig.keys("fang_"), FANG, dither=0.0)
    for key in heads:
        for face in ("east", "west"):
            skin.mark(key, face, 0, 0, EYE)
    return skin


def anims(body, heads):
    idle = {
        **undulate(4.0, list(body), amp=4.0, wave=0.2),
        "neck_planner_1": {"rotation": kf((0.0, (0, 0, 0)), (2.0, (-6, 10, 0)), (4.0, (0, 0, 0)))},
        "neck_dreamer_1": {"rotation": kf((0.0, (0, 0, 0)), (1.3, (6, -8, 4)), (2.7, (-4, 6, -4)), (4.0, (0, 0, 0)))},
        "neck_critic_1": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (0, 24, 0)), (1.4, (0, 30, 0)), (2.4, (0, 10, 0)), (4.0, (0, 0, 0)))},
        "head_critic": {"rotation": kf((0.0, (0, 0, 0)), (1.0, (0, 20, 0)), (2.4, (0, 0, 0)), (4.0, (0, 0, 0)))},
    }
    walk = {**undulate(1.0, list(body), amp=20.0, wave=0.2),
            **{f"neck_{n}_1": {"rotation": kf((0.0, (0, 6, 0)), (0.5, (0, -6, 0)), (1.0, (0, 6, 0)))}
               for n in ("planner", "dreamer", "critic")}}
    return {"idle": clip(4.0, idle), "walk": clip(1.0, walk)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "runespoor_model.py", build, paint, anims, HITBOX))
