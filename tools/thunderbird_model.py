#!/usr/bin/env python3
"""The Thunderbird: rig, skin and animation.

Canon (Fantastic Beasts): a huge North American bird that senses danger and creates storms as it
flies; Frank, the one Newt releases in Arizona, has three pairs of wings and plumage that shifts
through gold and blue with a lightning shimmer. `thunderbird_storm`, `dive_bomb`, `danger_sense`.

Two pairs of wings rather than three — at this resolution a third pair only thickens the second —
but the *two* is non-negotiable, because it is the thing that makes it not an eagle. The lightning
is painted as bright zig-zags down the flight feathers and lit in the glowmask, so it flashes in a
storm.

Run from the repo root:  python tools/thunderbird_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import bird, bird_loops, ground, wing_beat, wing_pair  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "thunderbird"
HITBOX = (1.7, 1.9)

# Bronze, not yellow: a saturated yellow body with a pale straight bill read as a rubber duck in
# the client. A raptor reads by value — dark hooked beak, frowning brow, long dark primaries.
GOLD = hx("#B8883A")
GOLD_DARK = hx("#7C5A24")
HEAD = hx("#8E6A2E")
BELLY = hx("#E0C48A")
BLUE = hx("#3A6AB8")
BLUE_DARK = hx("#26467E")
BOLT = hx("#EAF6FF")
BEAK = hx("#3E3A3C")
BEAK_TIP = hx("#C8BEA6")
EYE = hx("#F4E24A")
TALON = hx("#2A2620")


def build():
    rig = Rig(CID, 128)
    a = bird(rig, leg_h=8, body=(11, 10, 16), head=(6, 6, 7), beak=(3, 3, 4),
             neck=(6, 5, 6), neck_rake=24, tail=(12, 2, 12), tail_lift=6,
             legs=[("", 4, 3, 3, None), ("shank", 2, 2, 2, None), ("foot", 2, 5, 6, None)],
             leg_x=3, wings=((20, 10, 2), (18, 9, 1)))
    a["wings2"] = wing_pair(rig, "body", x=5, y=a.top - 4, z=a.front + 6,
                            root=(14, 8, 2), tip=(12, 7, 1), name="wing2")
    # Silhouette furniture of a raptor: the hook at the bill tip, the brow ridge that makes the
    # frown, and a crest swept back off the crown instead of the flat cap that sat on it.
    rig.cube("beak", (-1, a.head_y - 1, a.head_front - 4), (2, 2, 1), key="beak_hook")
    rig.cube("head", (-3.5, a.head_top - 3, a.head_front - 1), (7, 1, 3), key="brow")
    rig.cube("head", (-1.5, a.head_top - 1, a.head_back - 3), (3, 2, 7), key="crest")
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), GOLD, bottom=BELLY, dither=0.08, dither_colour=GOLD_DARK)
    skin.skin(["head", "brow"], HEAD, dither=0.06, dither_colour=GOLD_DARK)
    feathers = rig.keys("wing_", "wing2_") + ["tail", "crest"]
    skin.skin(feathers, BLUE, dither=0.0)
    skin.ramp(feathers, GOLD, BLUE_DARK, faces=("east", "west", "top"), steps=3)
    for key in rig.keys("wing_", "wing2_"):
        skin.feathers(key, ("east", "west"), BLUE_DARK, step=3, rows=3)
    # Lightning: a zig-zag of pale texels down each flight-feather face, lit.
    for key in rig.keys("wing_", "wing2_"):
        for face in ("east", "west"):
            x, y, w, h = skin.face(key, face)
            col = w // 3
            for row in range(h):
                col = max(0, min(w - 1, col + (1 if (row // 2) % 2 == 0 else -1)))
                skin.d.point((x + col, y + row), fill=BOLT)
                skin.glow_rect((x + col, y + row, 1, 1))
    skin.skin("beak", BEAK, dither=0.0)
    skin.skin("beak_hook", BEAK_TIP, dither=0.0)
    skin.skin([k for k in rig.keys("leg_") if "foot" in k or "shank" in k], TALON, dither=0.0)
    # Side-set raptor eyes under the brow. Not lit: the storm is its light, not its eyes.
    for face in ("east", "west"):
        skin.mark("head", face, 1, 3, EYE)
        skin.mark("head", face, 2, 3, TALON)
    return skin


def anims(a):
    idle, _walk, fly = bird_loops(a, idle_len=3.4, fly_len=1.2, spread=82.0, amp=44.0)
    fly.update({k.replace("wing", "wing2", 1): v for k, v in wing_beat(1.2, spread=70.0, amp=36.0).items()})
    strike = {
        "body": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (30, 0, 0)), (0.6, (-10, 0, 0)), (0.9, (0, 0, 0)))},
        "leg_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-70, 0, 0)), (0.9, (0, 0, 0)))},
        "leg_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-70, 0, 0)), (0.9, (0, 0, 0)))},
        "wing_left": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (0, 0, 20)), (0.9, (0, 0, 0)))},
        "wing_right": {"rotation": kf((0.0, (0, 0, 0)), (0.25, (0, 0, -20)), (0.9, (0, 0, 0)))},
    }
    call = {
        "neck": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-30, 0, 0)), (1.1, (-24, 0, 0)), (1.5, (0, 0, 0)))},
        "beak": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (20, 0, 0)), (1.1, (16, 0, 0)), (1.5, (0, 0, 0)))},
        **wing_beat(1.5, spread=60.0, amp=10.0),
    }
    return {"idle": clip(3.4, idle), "fly": clip(1.2, fly),
            "strike": clip(0.9, strike, loop=False), "call": clip(1.5, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "thunderbird_model.py", build, paint, anims, HITBOX))
