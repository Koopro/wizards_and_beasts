#!/usr/bin/env python3
"""The Blast-Ended Skrewt: rig, skin and animation.

Canon (Goblet of Fire): Hagrid's illegal cross of manticore and fire crab — pale, slimy and
shell-less at first, later armoured, with no visible head, legs sticking out at odd angles, a sting
(males) or sucker (females) on the belly, and a tail end that fires off sparks and propels it
forward several inches with a small bang. FIRE_ATTACK, EXPLODE_ON_DEATH.

The version the book ends on: a grey-armoured segmented body with no head at the front, legs
splayed out in no particular order, a manticore sting curling up over the back, and the rear
segment's end lit orange — the blast.

Run from the repo root:  python tools/blast_ended_skrewt_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, splay_leg, undulate  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "blast_ended_skrewt"
HITBOX = (1.7, 1.9)

# How far each armour plate's back edge tips up (degrees about X, pivoted on its front edge).
PLATE_LIFT = 9

ARMOUR = hx("#8A8480")
ARMOUR_DARK = hx("#5A5450")
SLIME = hx("#C8B4AC")
STING = hx("#2A2626")
BLAST = hx("#F08A2A")
BLAST_HOT = hx("#FCE08A")


def build():
    rig = Rig(CID, 128)
    # Low and wide (silhouette pass 2026-09-27): the old rig carried a crate at knee height on long
    # spider legs. Now the body hugs the ground under overlapping armour plates, the legs are short
    # and crab-bent, and the height is all in the tail.
    rig.bone("body", "root", (0, 6, 0))
    rig.cube("body", (-5, 3, -6), (10, 6, 8), key="front")
    rig.cube("body", (-6, 8, -7), (12, 2, 9), key="front_plate", rotation=(PLATE_LIFT, 0, 0), pivot=(0, 10, -7))
    segments = [(6, 9, 7), (5, 8, 6), (5, 6, 5)]
    segs = chain(rig, "seg", "body", start=(0, 6, 2), direction=1,
                 segments=segments, rotations=[(-3, 0, 0), (-3, 0, 0), (-4, 0, 0)])
    # One plate per segment, wider than the segment and a unit longer so each overlaps the next,
    # each tipped up at its back edge: the serrated top line is what makes it read segmented and
    # shelled at any distance, where flat plates merged back into one crate lid.
    z = 2
    for bone, (length, w, h) in zip(segs, segments):
        top = 6 + h / 2.0 + 1
        rig.cube(bone, (-(w + 2) / 2.0, top - 2, z - 0.5), (w + 2, 2, length + 1),
                 key=f"{bone}_plate", rotation=(PLATE_LIFT, 0, 0), pivot=(0, top, z - 0.5))
        z += length
    # The blast end: a stubby scorched vent standing proud of the last segment, lit inside.
    rig.cube(segs[-1], (-2.5, 4, z), (5, 4, 2), key="vent")
    legs = []
    for i, (lz, yaw) in enumerate([(-6, -30), (-1, 10), (4, 40)]):
        for side, sign in (("left", 1), ("right", -1)):
            skew = 8 if (i + (sign > 0)) % 2 else -8     # odd angles: no two legs agree
            legs.append(splay_leg(rig, f"leg_{i}_{side}", "body", hip_y=4, x=4, z=lz, sign=sign,
                                  yaw=yaw + skew, coxa=2, femur=5, tibia=7, tarsus=3, thick=2))
    # A manticore's sting, thick at the root, curling up off the back and forward over it, ending
    # in a bulb and a barb: a scorpion's tail, where the old one was a thin wire.
    sting = chain(rig, "sting", segs[1], start=(0, 8.5, 12), direction=1,
                  segments=[(5, 4, 4), (5, 3, 3), (4, 3, 3)], rotations=[(62, 0, 0), (52, 0, 0), (52, 0, 0)])
    rig.cube(sting[-1], (-2, 6.5, 26), (4, 4, 4), key="sting_bulb")
    rig.cube(sting[-1], (-0.5, 7, 30), (1, 1, 3), key="sting_barb")
    ground(rig)
    return rig, segs, legs, sting


def paint(rig, tex_h, segs, legs, sting):
    skin = Skin(rig, tex_h, grain="fleck")
    body = ["front"] + list(segs)
    plates = ["front_plate"] + [f"{s}_plate" for s in segs]
    skin.skin(body, ARMOUR, bottom=SLIME, dither=0.12, dither_colour=ARMOUR_DARK)
    skin.skin(plates, ARMOUR, dither=0.12, dither_colour=ARMOUR_DARK)
    skin.bands(plates, ARMOUR_DARK, faces=("top",), step=3)
    skin.tip(plates, ARMOUR_DARK, rows=1, faces=("east", "west", "south"))
    skin.skin(rig.keys("leg_"), SLIME, dither=0.1, dither_colour=ARMOUR_DARK)
    skin.skin(list(sting) + ["sting_bulb"], STING, dither=0.0)
    skin.skin("sting_barb", SLIME, dither=0.0)
    skin.skin("vent", ARMOUR_DARK, dither=0.1, dither_colour=STING)
    x, y, w, h = skin.face("vent", "south")
    skin.rect((x, y, w, h), BLAST)
    skin.rect((x + 1, y + 1, w - 2, h - 2), BLAST_HOT)
    skin.glow_rect((x, y, w, h))
    return skin


def anims(segs, legs, sting):
    phase = {leg: (i * 0.37) % 1.0 for i, leg in enumerate(legs)}      # uncoordinated on purpose
    idle = {**undulate(3.0, list(segs), amp=3.0, wave=0.2), **undulate(3.0, list(sting), amp=6.0, axis="x", wave=0.2)}
    walk = {**{leg: osc(0.7, 16.0, axis="y", phase=p) for leg, p in phase.items()},
            **undulate(0.7, list(segs), amp=6.0, wave=0.2),
            "root": {"position": kf((0.0, (0, 0, 0)), (0.35, (0, 0.6, 0)), (0.7, (0, 0, 0)))}}
    strike = {sting[0]: {"rotation": kf((0.0, (0, 0, 0)), (0.2, (20, 0, 0)), (0.45, (-50, 0, 0)), (0.8, (0, 0, 0)))},
              sting[1]: {"rotation": kf((0.0, (0, 0, 0)), (0.25, (14, 0, 0)), (0.5, (-40, 0, 0)), (0.8, (0, 0, 0)))}}
    # `hiss` is the blast: the rear jolts and the whole skrewt lurches forward a few inches.
    hiss = {segs[-1]: {"scale": kf((0.0, (1, 1, 1)), (0.1, (1.25, 1.25, 1.1)), (0.4, (1, 1, 1)))},
            "root": {"position": kf((0.0, (0, 0, 0)), (0.12, (0, 0.5, -3)), (0.6, (0, 0, -3)), (0.8, (0, 0, 0)))}}
    return {"idle": clip(3.0, idle), "walk": clip(0.7, walk),
            "strike": clip(0.8, strike, loop=False), "hiss": clip(0.8, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "blast_ended_skrewt_model.py", build, paint, anims, HITBOX))
