#!/usr/bin/env python3
"""The small-crustacean skeleton shared by the Chizpurfle, the Fire Crab and the Mackled Malaclaw.

All three are a shell on three pairs of solved splayed legs with something in front (fangs, a
head, a pair of claws). This builds the shell and legs; the creature files add what is in front
and paint it. The walk is sideways-capable scuttling: alternate legs out of phase.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bodies import splay_leg  # noqa: E402
from rigkit import kf, osc  # noqa: E402


def crab(rig, *, shell, hip_y, leg, pairs=3, spread=5, leg_x=None):
    """`shell` is (w, h, d) sitting on `hip_y`; `leg` is (coxa, femur, tibia, tarsus, thick)."""
    w, h, d = shell
    rig.bone("body", "root", (0, hip_y + h / 2.0, 0))
    rig.cube("body", (-w / 2.0, hip_y - 1, -d / 2.0), shell, key="shell")
    coxa, femur, tibia, tarsus, thick = leg
    legs = []
    for i in range(pairs):
        z = -d / 2.0 + 1 + i * (d - 2) / max(1, pairs - 1)
        yaw = -30 + 60 * i / max(1, pairs - 1)
        for side, sign in (("left", 1), ("right", -1)):
            legs.append(splay_leg(rig, f"leg_{i}_{side}", "body", hip_y=hip_y, x=leg_x or w / 2.0 - 1, z=z,
                                  sign=sign, yaw=yaw, coxa=coxa, femur=femur, tibia=tibia, tarsus=tarsus,
                                  thick=thick))
    return legs


def scuttle(length, legs, amp=24.0):
    return {leg: osc(length, amp, axis="y", phase=(0.0 if i % 2 == (1 if "right" in leg else 0) else 0.5))
            for i, leg in enumerate(legs)}
