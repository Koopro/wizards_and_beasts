#!/usr/bin/env python3
"""The small flying insect skeleton shared by the Billywig, Doxy, Fairy, Glumbumble and Swooping Evil.

What they share is only the mechanics of hovering: a body held off the ground, wings mounted on
the back that beat far faster than a bird's, and legs that dangle. What they look like has nothing
in common, so this builds just that — `wings()` for a pair (or two) of flat blades rolled up off
the back, and `buzz()` for the beat — and each creature file builds its own body.

Insect wings are flat and lie *horizontal*, unlike a bird's folded slab, so they beat about Z
from a raised rest roll rather than unfolding from a hanging one.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rigkit import Rig, kf  # noqa: E402


def wings(rig, parent, *, x, y, z, size, rest=20, name="wing", yaw=0):
    """A pair of flat blades `(span, thickness, depth)` rooted at (±x, y, z) and extending outward."""
    def build(side, sign):
        bone = f"{name}_{side}"
        rig.bone(bone, parent, (sign * x, y, z), (0, sign * yaw, sign * rest))
        span, thick, depth = size
        rig.cube(bone, Rig.mirror((x, y, z - depth / 2.0), size, sign), size)
    rig.pair(build)
    return [f"{name}_left", f"{name}_right"]


def buzz(length, *, amp=34.0, name="wing", phase_shift=False):
    """One full up-down stroke per `length` seconds, both sides together."""
    out = {}
    for side, sign in (("left", 1), ("right", -1)):
        a = -amp if phase_shift else amp
        out[f"{name}_{side}"] = {"rotation": kf((0.0, (0, 0, sign * a)), (length / 2, (0, 0, -sign * a)),
                                                (length, (0, 0, sign * a)))}
    return out


def hover(length, amp=0.8):
    return {"position": kf((0.0, (0, 0, 0)), (length / 2, (0, amp, 0)), (length, (0, 0, 0)))}
