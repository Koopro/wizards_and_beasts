#!/usr/bin/env python3
"""The small-lizard skeleton shared by the Moke and the Salamander.

Both are canonically a lizard of a hand's length or two, and the two creatures differ only in
what they do: the moke shrinks, the salamander burns. So the body is one builder — low slung,
four sprawled legs whose knees stick out sideways, and a tail as long as the body that drags —
and each creature file supplies proportions, colour and clips.

A lizard's legs do not go under it. `bodies.leg` mirrors Z rotations per side, so the upper
segment rolls out (positive Z, rigkit's sense) and the lower rolls back down, and the elbow ends
up level with the spine. The sign was negative until 2026-09-25, which tucked the legs under the
body; the old preview's inverted roll hid it and so did the game's, until `emit` learned
GeckoLib's real sense.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import chain, ground, quadruped  # noqa: E402


def lizard(rig, *, body, head, snout, tail, hip=3, splay=55):
    """`body` is (width, height, length); `tail` a list of chain segments."""
    w, h, length = body
    a = quadruped(
        rig, hip=hip,
        chest=(w, h, length // 3), barrel=(w, h, length - 2 * (length // 3)),
        croup=(w, h, length // 3), croup_drop=0,
        neck=None, neck_rake=0, head=head, head_y=hip - 2, muzzle=snout,
        fore=[("", 2, 2, 2, (0, 0, splay)), ("foot", hip - 2, 2, 3, (0, 0, -splay))],
        hind=[("", 2, 2, 2, (0, 0, splay)), ("foot", hip - 2, 2, 3, (0, 0, -splay))],
        fore_x=w / 2.0, hind_x=w / 2.0)
    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + h / 2.0, a.back - 1), direction=1,
                      segments=tail, rotations=[(-6, 0, 0)] + [(0, 0, 0)] * (len(tail) - 1))
    ground(rig)
    a["rig"] = rig
    return a
