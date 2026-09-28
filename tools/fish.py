#!/usr/bin/env python3
"""The fish body shared by the Ramora and the Shrake — and the swim every fish-shaped rig uses.

A fish is `bodies.serpent` with fins: a body chain that tapers back from the head, a vertical tail
fin on the last link, a dorsal fin on the first, and a pectoral pair. What makes one fish another
is paint and ornament (the Shrake's spines), so this builds the shape and each creature dresses it.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import ground, serpent, undulate  # noqa: E402
from rigkit import Rig, kf, osc  # noqa: E402


def fish(rig, *, y, head, segments, tail_fin, dorsal, pectoral):
    a = serpent(rig, y=y, segments=segments, head=head)
    segs = a.segments
    z = sum(s[0] for s in segments)
    tw, th, td = tail_fin
    rig.cube(segs[-1], (-tw / 2.0, y - th / 2.0, z - 1), tail_fin, key="tail_fin")
    dw, dh, dd = dorsal
    rig.cube(segs[0], (-dw / 2.0, y + segments[0][2] / 2.0 - 1, 1), dorsal, key="dorsal")
    pw, ph, pd = pectoral
    rig.pair(lambda side, sign: (
        rig.bone(f"fin_{side}", segs[0], (sign * segments[0][1] / 2.0, y - 1, 2), (0, 0, sign * 20)),
        rig.cube(f"fin_{side}", Rig.mirror((segments[0][1] / 2.0, y - 1, 1), pectoral, sign), pectoral),
    ))
    ground(rig)
    a["rig"] = rig
    return a


def fish_loops(a, *, idle_len=3.0, swim_len=0.8):
    segs = a.segments
    idle = {**undulate(idle_len, segs, amp=5.0, wave=0.2),
            "fin_left": osc(idle_len, 14.0, axis="z"), "fin_right": osc(idle_len, 14.0, axis="z", phase=0.5)}
    swim = {**undulate(swim_len, segs, amp=12.0, wave=0.22, grow=3.0),
            "head": osc(swim_len, 4.0, axis="y", phase=0.2),
            "fin_left": osc(swim_len, 20.0, axis="z"), "fin_right": osc(swim_len, 20.0, axis="z")}
    return idle, swim
