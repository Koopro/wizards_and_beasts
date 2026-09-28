#!/usr/bin/env python3
"""The basilisk: rig, skin, glowmask and every clip `BasiliskEntity` plays.

Canon (Chamber of Secrets; Fantastic Beasts): a giant serpent, up to fifty feet, "thick as an oak trunk"; a vast
wedge head, fangs long as a sword, great yellow eyes that kill whoever meets them. The male wears a scarlet plume;
this one has none — Slytherin's basilisk is never sexed in the books, and the brief asks for the unplumed form.

The read, in order: the head (broad skull, heavy brow, jaws that open wide, ivory fangs, a forked tongue, two eyes
that glow); the thick neck lifting it; the long body lying in an S; the tail tapering to a point. Everything is
chunky — one box per segment, a low dorsal ridge instead of spikes, scales suggested by blocks of colour.

Structure. `body_01` anchors the rig. The neck chain runs forward from it
(`neck_01 → neck_02 → look → head`), the body and tail back from it (`body_02 … tail_tip`). Every segment is authored
straight along Z and bent by its rest rotation, so the slither is a travelling wave of yaw deltas down one chain.
`look` carries no cubes and no clip ever keys it: GeckoLib's head tracking *overwrites* the named bone's rotation, so
it gets a bone of its own and the head stays free to strike, rear and gaze.

Clips — movement: idle, walk (slither); state loops: coiled, threaten; one-shots: coil, uncoil, rear, strike, bite,
gaze, hiss, taste_air, hit, death.

Run from the repo root:  python tools/basilisk_model.py [--force]
"""

import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "basilisk"
HITBOX = (2.7, 2.2)
TEX_W = 128

SCALE_DARK = hx("#26391F")      # deep forest green
SCALE = hx("#34502B")
SCALE_OLIVE = hx("#454F27")     # dark olive
SCALE_LIT = hx("#6B7A3A")       # muted yellow-green
SCALE_GREY = hx("#58644F")      # grey-green, weathered
BELLY = hx("#CFC6A0")
BELLY_LINE = hx("#A89E78")
MOUTH = hx("#5E1B1B")
MOUTH_DARK = hx("#3A1010")
FANG = hx("#E6DEC4")
FANG_TIP = hx("#BDB396")
EYE = hx("#EAC417")
EYE_HOT = hx("#F6EC8A")
PUPIL = hx("#1A1A0E")
TONGUE = hx("#7E2A2E")

OVERLAP = 2      # units each segment reaches into its parent
Z0 = -8          # body_01's front: the joint between neck and body
SEG = 14         # body segment length
# name, width, height, length, rest yaw (authoring sense: + turns toward +x)
BODY = [
    ("body_01", 12, 11, SEG, 0), ("body_02", 12, 11, SEG, 28), ("body_03", 12, 11, SEG, 26),
    ("body_04", 12, 10, SEG, -30), ("body_05", 11, 10, SEG, -34),
    ("tail_01", 10, 9, 13, -26), ("tail_02", 9, 8, 13, 18), ("tail_03", 7, 6, 12, 30),
    ("tail_04", 5, 4, 11, 26), ("tail_tip", 3, 3, 9, 18),
]
CHAIN = [b[0] for b in BODY]
NECK = [("neck_01", 11, 11, 12), ("neck_02", 10, 10, 12)]
NECK_PITCH = {"neck_01": -55, "neck_02": 22}   # +X tips a forward-running bone's front end DOWN
HEAD_PITCH = 26


def build():
    rig = Rig(CID, TEX_W)
    # Body and tail: each segment's pivot is its front joint, mid-height; it lies on y=0.
    z = Z0
    parent = "root"
    for name, w, h, length, yaw in BODY:
        rig.bone(name, parent, (0, h / 2, z), (0, yaw, 0) if yaw else None)
        # Each segment reaches OVERLAP back into its parent so a bend never opens a gap at the joint.
        lap = 0 if name == "body_01" else OVERLAP
        rig.cube(name, (-w / 2, 0, z - lap), (w, h, length + lap), key=name)
        if w >= 7:
            rig.cube(name, (-1.5, h, z + 2), (3, 1, length - 4), key=f"{name}_ridge")
        parent = name
        z += length

    # Neck, forward and up from body_01.
    z = Z0
    parent = "body_01"
    for name, w, h, length in NECK:
        rig.bone(name, parent, (0, 6, z), (NECK_PITCH[name], 0, 0))
        rig.cube(name, (-w / 2, 6 - h / 2, z - length), (w, h, length + OVERLAP), key=name)
        rig.cube(name, (-1.5, 6 + h / 2, z - length + 2), (3, 1, length - 4), key=f"{name}_ridge")
        parent = name
        z -= length
    hz = z  # back of the skull

    rig.bone("look", "neck_02", (0, 6, hz))
    rig.bone("head", "look", (0, 6, hz), (HEAD_PITCH, 0, 0))
    rig.cube("head", (-8, 1, hz - 14), (16, 10, 14), key="skull")
    rig.cube("head", (-7, 11, hz - 12), (14, 2, 9), key="crown")

    def brow(side, sign):
        rig.cube("head", Rig.mirror((3, 10, hz - 15), (5, 3, 7), sign), (5, 3, 7), key=f"brow_{side}")
        rig.bone(f"eye_{side}", "head", (sign * 8, 7, hz - 12))
        rig.cube(f"eye_{side}", Rig.mirror((7.5, 6, hz - 14), (2, 3, 4), sign), (2, 3, 4), key=f"eye_{side}")

    rig.pair(brow)

    rig.bone("upper_jaw", "head", (0, 4, hz - 12))
    rig.cube("upper_jaw", (-6, 3, hz - 24), (12, 6, 11), key="snout")
    rig.cube("upper_jaw", (-4, 9, hz - 23), (8, 1, 8), key="snout_ridge")
    rig.bone("fangs", "upper_jaw", (0, 3, hz - 21))

    def fang(side, sign):
        rig.cube("fangs", Rig.mirror((3, -4, hz - 23), (2, 7, 2), sign), (2, 7, 2), key=f"fang_{side}")
        rig.cube("fangs", Rig.mirror((1, 1, hz - 20), (1, 2, 1), sign), (1, 2, 1), key=f"tooth_{side}")

    rig.pair(fang)

    rig.bone("lower_jaw", "head", (0, 3, hz - 12))
    rig.cube("lower_jaw", (-6, -1, hz - 23), (12, 4, 12), key="jaw")
    rig.cube("lower_jaw", (-7, -1, hz - 12), (14, 4, 5), key="jaw_hinge")
    rig.bone("tongue", "lower_jaw", (0, 3, hz - 14))
    rig.cube("tongue", (-1, 2.5, hz - 22), (2, 1, 9), key="tongue")
    rig.cube("tongue", (-1.5, 2.5, hz - 25), (1, 1, 3), key="tongue_fork_l")
    rig.cube("tongue", (0.5, 2.5, hz - 25), (1, 1, 3), key="tongue_fork_r")
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="speckle")
    d = skin.d
    sides = ("east", "west", "north", "south")
    hide = [n for n, *_ in BODY] + [n for n, *_ in NECK]
    skin.skin(hide, SCALE, top=SCALE_DARK, bottom=BELLY, bevel=0.9, dither=0.0)
    for key in hide:
        f = rig.faces(key)
        # Scales as blocks: rows of 2x2 plates, offset each row, a lit corner on some, a weathered grey on a few.
        for name in ("east", "west", "top"):
            x0, y0, w, h = f[name]
            for yy in range(0, h, 2):
                off = (yy // 2) % 2
                for xx in range(-off, w, 3):
                    n = rigkit._hash(xx + x0, yy + y0, 7) % 11
                    colour = SCALE_OLIVE if n < 3 else SCALE_DARK if n < 5 else SCALE_GREY if n == 7 else None
                    if colour is not None:
                        d.rectangle([x0 + max(0, xx), y0 + yy, x0 + min(w - 1, xx + 1), y0 + min(h - 1, yy + 1)],
                                    fill=colour)
                    if n == 9:
                        d.point((x0 + max(0, xx), y0 + yy), fill=SCALE_LIT)
        # The flank's lowest rows are the edge of the belly plates.
        for name in ("east", "west"):
            x0, y0, w, h = f[name]
            d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=BELLY_LINE)
            d.line([(x0, y0 + h - 2), (x0 + w - 1, y0 + h - 2)], fill=SCALE_LIT)
        # Belly: cream plates across the body.
        x0, y0, w, h = f["bottom"]
        for yy in range(1, h, 3):
            d.line([(x0, y0 + yy), (x0 + w - 1, y0 + yy)], fill=BELLY_LINE)
    ridges = rig.keys("body_", "tail_", "neck_")
    ridges = [k for k in ridges if k.endswith("_ridge")]
    skin.skin(ridges, SCALE_DARK, top=SCALE_OLIVE, bevel=0.85, dither=0.0)

    # Head: darker, heavier plates, the brows darkest; the mouth red inside, the jaw's underside cream.
    head = ["skull", "crown", "snout", "snout_ridge", "brow_left", "brow_right", "jaw", "jaw_hinge"]
    skin.skin(head, SCALE, top=SCALE_DARK, bottom=BELLY, bevel=0.88, dither=0.0)
    for key in ("skull", "snout", "jaw", "jaw_hinge"):
        f = rig.faces(key)
        for name in ("east", "west", "top"):
            x0, y0, w, h = f[name]
            for yy in range(0, h, 3):
                for xx in range((yy // 3) % 2 * 2, w, 4):
                    d.rectangle([x0 + xx, y0 + yy, x0 + min(w - 1, xx + 2), y0 + min(h - 1, yy + 1)],
                                fill=SCALE_OLIVE)
    skin.skin(["brow_left", "brow_right", "crown"], SCALE_DARK, top=SCALE_GREY, bevel=0.85, dither=0.0)
    # Mouth: the snout's underside and the jaw's top are the inside of the mouth.
    for key, face in (("snout", "bottom"), ("jaw", "top"), ("jaw_hinge", "top")):
        x0, y0, w, h = rig.faces(key)[face]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=MOUTH)
        d.rectangle([x0 + 2, y0 + 1, x0 + w - 3, y0 + h - 2], fill=MOUTH_DARK)
    # A lipline where the jaws meet, and nostrils.
    for key in ("snout", "jaw"):
        f = rig.faces(key)
        for name in ("east", "west", "north"):
            x0, y0, w, h = f[name]
            row = h - 1 if key == "snout" else 0
            d.line([(x0, y0 + row), (x0 + w - 1, y0 + row)], fill=MOUTH_DARK)
    skin.mark("snout", "north", 3, 1, SCALE_DARK)
    skin.mark("snout", "north", -4, 1, SCALE_DARK)

    skin.skin(rig.keys("fang_", "tooth_"), FANG, bevel=0.9, dither=0.0)
    skin.tip(rig.keys("fang_"), FANG_TIP, rows=2)
    skin.skin(rig.keys("tongue"), TONGUE, bevel=0.9, dither=0.0)

    # Eyes: great yellow eyes with a slit pupil, lit on their outer face. The one thing that should read in the dark.
    for side, face in (("left", "east"), ("right", "west")):
        key = f"eye_{side}"
        skin.skin(key, EYE, bevel=0.0, dither=0.0)
        f = rig.faces(key)
        x0, y0, w, h = f[face]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=EYE)
        d.point((x0 + 1, y0), fill=EYE_HOT)
        d.line([(x0 + w // 2, y0), (x0 + w // 2, y0 + h - 1)], fill=PUPIL)
        skin.glow(key, [face, "north"])
    return skin


# ── animation ────────────────────────────────────────────────────────────────


def rot(*frames):
    return {"rotation": kf(*frames)}


def wave(length, amp, *, cycles=1.0, lag=0.12, axis=1, steps=8, bones=CHAIN, grow=0.08, start=0):
    """A travelling wave down the chain: each joint the same swing, a little later than the one before, a little
    wider toward the tail. Coordinated, never per-bone noise."""
    out = {}
    for i, name in enumerate(bones):
        if i < start:
            continue
        a = amp * (1 + grow * i)
        track = []
        for k in range(steps + 1):
            t = length * k / steps
            v = [0.0, 0.0, 0.0]
            v[axis] = a * math.sin(2 * math.pi * (cycles * k / steps - lag * i))
            track.append((t, tuple(v)))
        out[name] = rot(*track)
    return out


def tongue_flick(t0, dur=0.35):
    return {"position": kf((0, (0, 0, 0)), (t0, (0, 0, 0)), (t0 + dur * 0.3, (0, 0, -8)),
                          (t0 + dur * 0.5, (0, 0, -6)), (t0 + dur * 0.7, (0, 0, -8)), (t0 + dur, (0, 0, 0)))}


def pose(bones, length, frames):
    """{bone: [(t, rot), ...]} → a bone table."""
    return {b: rot(*f) for b, f in frames.items()} | bones


def anims():
    clips = {}
    # idle: it breathes — the body swells and settles in a slow wave, the head sways, the tongue tastes the air.
    L = 4.0
    idle = wave(L, 2.0, lag=0.08, bones=CHAIN[:6], grow=0.0)
    # Breath: the neck lifts a fraction and settles (scaling body_01 would pulse the whole animal, every
    # segment being its child).
    idle["neck_01"] = {"rotation": kf((0, (0, 0, 0)), (L / 2, (3, 0, 0)), (L, (0, 0, 0))),
                       "position": kf((0, (0, 0, 0)), (L / 2, (0, 0.4, 0)), (L, (0, 0, 0)))}
    idle["head"] = rot((0, (0, -6, 0)), (L * 0.4, (2, 6, 0)), (L * 0.6, (2, 6, 0)), (L, (0, -6, 0)))
    idle["tongue"] = tongue_flick(1.4)
    clips["idle"] = clip(L, idle)

    # walk: the slither. A lateral wave runs head to tail; the neck swings against it so the head stays level
    # and steady on its prey.
    W = 1.6
    walk = wave(W, 9.0, lag=0.11)
    walk["neck_01"] = rot(*[(W * k / 8, (0, -6 * math.sin(2 * math.pi * k / 8), 0)) for k in range(9)])
    walk["head"] = rot(*[(W * k / 8, (0, -5 * math.sin(2 * math.pi * (k / 8 - 0.1)), 0)) for k in range(9)])
    walk["tongue"] = tongue_flick(0.9, 0.3)
    clips["walk"] = clip(W, walk)

    # coiled: resting gathered — the body folds in tighter bends, the neck low and the head on the coils.
    coil_bend = {"body_02": 38, "body_03": 40, "body_04": 44, "body_05": 44, "tail_01": 42, "tail_02": 30,
                 "tail_03": 20, "tail_04": 10}
    C = 5.0
    coiled = {b: rot((0, (0, a, 0)), (C / 2, (0, a + 2, 0)), (C, (0, a, 0))) for b, a in coil_bend.items()}
    coiled["neck_01"] = rot((0, (-34, 20, 0)), (C / 2, (-32, 22, 0)), (C, (-34, 20, 0)))
    coiled["neck_02"] = rot((0, (22, 10, 0)), (C, (22, 10, 0)))
    coiled["head"] = rot((0, (8, 0, 0)), (C * 0.5, (6, 4, 0)), (C, (8, 0, 0)))
    coiled["tongue"] = tongue_flick(3.0)
    clips["coiled"] = clip(C, coiled)

    # coil / uncoil: into and out of that rest.
    K = 1.2
    clips["coil"] = clip(K, {**{b: rot((0, (0, 0, 0)), (K, (0, a, 0))) for b, a in coil_bend.items()},
                             "neck_01": rot((0, (0, 0, 0)), (K, (-34, 20, 0))),
                             "neck_02": rot((0, (0, 0, 0)), (K, (22, 10, 0))),
                             "head": rot((0, (0, 0, 0)), (K, (8, 0, 0)))}, loop=False)
    clips["uncoil"] = clip(K, {**{b: rot((0, (0, a, 0)), (K * 0.7, (0, -a * 0.1, 0)), (K, (0, 0, 0)))
                                  for b, a in coil_bend.items()},
                               "neck_01": rot((0, (-34, 20, 0)), (K * 0.6, (8, 0, 0)), (K, (0, 0, 0))),
                               "neck_02": rot((0, (22, 10, 0)), (K, (0, 0, 0))),
                               "head": rot((0, (8, 0, 0)), (K, (0, 0, 0)))}, loop=False)

    # rear: the front third lifts high; threaten holds it there, mouth open, hissing, swaying.
    rear_up = {"neck_01": (26, 0, 0), "neck_02": (14, 0, 0), "head": (-30, 0, 0)}
    R = 0.8
    clips["rear"] = clip(R, {b: rot((0, (0, 0, 0)), (R * 0.6, tuple(v * 1.1 for v in r)), (R, r))
                             for b, r in rear_up.items()}, loop=False)
    T = 2.4
    threaten = {b: rot((0, r), (T / 2, (r[0] - 3, r[1], r[2])), (T, r)) for b, r in rear_up.items()}
    threaten["head"] = rot((0, (-10, -8, 0)), (T / 2, (-13, 8, 0)), (T, (-10, -8, 0)))
    threaten["lower_jaw"] = rot((0, (34, 0, 0)), (T / 2, (40, 0, 0)), (T, (34, 0, 0)))
    threaten["upper_jaw"] = rot((0, (-12, 0, 0)), (T / 2, (-15, 0, 0)), (T, (-12, 0, 0)))
    threaten["tongue"] = tongue_flick(0.8, 0.4)
    threaten.update(wave(T, 3.0, bones=CHAIN[2:], lag=0.1))
    clips["threaten"] = clip(T, threaten)

    # strike: draw back, then the head drives forward with the jaws flung open, fangs first.
    S = 0.7
    clips["strike"] = clip(S, {
        "neck_01": rot((0, (0, 0, 0)), (0.2, (22, 0, 0)), (0.35, (-26, 0, 0)), (S, (0, 0, 0))),
        "neck_02": rot((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.35, (-8, 0, 0)), (S, (0, 0, 0))),
        "head": {"rotation": kf((0, (0, 0, 0)), (0.2, (-14, 0, 0)), (0.35, (14, 0, 0)), (S, (0, 0, 0))),
},
        "lower_jaw": rot((0, (0, 0, 0)), (0.2, (50, 0, 0)), (0.36, (58, 0, 0)), (0.45, (0, 0, 0)), (S, (0, 0, 0))),
        "upper_jaw": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.36, (-24, 0, 0)), (0.45, (0, 0, 0)), (S, (0, 0, 0))),
    }, loop=False)

    # bite: the jaws snap shut and worry the victim.
    B = 0.45
    clips["bite"] = clip(B, {
        "lower_jaw": rot((0, (40, 0, 0)), (0.08, (0, 0, 0)), (0.2, (6, 0, 0)), (0.28, (0, 0, 0)), (B, (0, 0, 0))),
        "upper_jaw": rot((0, (-16, 0, 0)), (0.08, (0, 0, 0)), (B, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.12, (0, 12, 6)), (0.24, (0, -10, -6)), (0.34, (0, 5, 0)), (B, (0, 0, 0))),
        "neck_02": rot((0, (0, 0, 0)), (0.12, (0, 6, 0)), (0.24, (0, -6, 0)), (B, (0, 0, 0))),
    }, loop=False)

    # gaze: it goes still, lifts its head and fixes its stare.
    G = 1.2
    clips["gaze"] = clip(G, {
        "neck_01": rot((0, (0, 0, 0)), (0.3, (14, 0, 0)), (0.9, (14, 0, 0)), (G, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.3, (-8, 0, 0)), (0.9, (-8, 0, 0)), (G, (0, 0, 0))),
        "eye_left": {"scale": kf((0, (1, 1, 1)), (0.3, (1.3, 1.3, 1.3)), (0.9, (1.3, 1.3, 1.3)), (G, (1, 1, 1)))},
        "eye_right": {"scale": kf((0, (1, 1, 1)), (0.3, (1.3, 1.3, 1.3)), (0.9, (1.3, 1.3, 1.3)), (G, (1, 1, 1)))},
    }, loop=False)

    # hiss: the mouth gapes, the head draws back.
    H = 1.0
    clips["hiss"] = clip(H, {
        "lower_jaw": rot((0, (0, 0, 0)), (0.2, (36, 0, 0)), (0.8, (36, 0, 0)), (H, (0, 0, 0))),
        "upper_jaw": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.8, (-12, 0, 0)), (H, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.2, (-8, 0, 0)), (0.8, (-10, 0, 0)), (H, (0, 0, 0))),
        "tongue": {"position": kf((0, (0, 0, 0)), (0.3, (0, 0, -7)), (0.7, (0, 0, -7)), (H, (0, 0, 0)))},
    }, loop=False)

    # taste_air: the head lifts and the tongue flicks twice.
    A = 1.4
    ta = {"head": rot((0, (0, 0, 0)), (0.3, (-8, 4, 0)), (1.1, (-8, -4, 0)), (A, (0, 0, 0))),
          "tongue": {"position": kf((0, (0, 0, 0)), (0.3, (0, 0, -8)), (0.45, (0, 0, -3)), (0.6, (0, 0, -8)),
                                    (0.8, (0, 0, 0)), (A, (0, 0, 0)))}}
    clips["taste_air"] = clip(A, ta, loop=False)

    # hit: the body flinches, the head jerks back.
    Hi = 0.5
    hit = {"head": rot((0, (0, 0, 0)), (0.1, (-14, 10, 0)), (Hi, (0, 0, 0))),
           "neck_01": rot((0, (0, 0, 0)), (0.1, (-8, 0, 0)), (Hi, (0, 0, 0)))}
    hit.update({b: rot((0, (0, 0, 0)), (0.12, (0, (-1) ** i * 6, 0)), (Hi, (0, 0, 0)))
                for i, b in enumerate(CHAIN[1:6])})
    clips["hit"] = clip(Hi, hit, loop=False)

    # death: the head thrashes up, then the whole length collapses and the neck falls flat.
    D = 2.0
    death = {"neck_01": rot((0, (0, 0, 0)), (0.3, (20, 0, 0)), (1.0, (-40, 0, 0)), (D, (-46, 0, 0))),
             "neck_02": rot((0, (0, 0, 0)), (0.3, (-10, 0, 0)), (1.1, (26, 0, 0)), (D, (30, 0, 0))),
             "head": rot((0, (0, 0, 0)), (0.3, (-20, 14, 0)), (1.2, (10, 0, 70)), (D, (12, 0, 80))),
             "lower_jaw": rot((0, (0, 0, 0)), (0.3, (40, 0, 0)), (D, (22, 0, 0)))}
    for i, b in enumerate(CHAIN[1:]):
        death[b] = rot((0, (0, 0, 0)), (0.5 + 0.08 * i, (0, (-1) ** i * 10, 0)), (D, (0, (-1) ** i * 4, (-1) ** i * 6)))
    clips["death"] = dict(clip(D, death, loop=False), loop="hold_on_last_frame")

    # Every clip above is written with +X meaning "lift the front end" on the bones that carry the head up, which
    # reads naturally but is the opposite of the rig's sense (+X tips a forward-running bone's front down). One flip
    # here keeps every number above readable. The jaws are written in the rig's own sense and are not flipped.
    for body in clips.values():
        for bone in LIFT:
            track = body["bones"].get(bone, {}).get("rotation")
            if isinstance(track, dict):
                body["bones"][bone]["rotation"] = {t: [-v[0], v[1], v[2]] for t, v in track.items()}
    return clips


LIFT = ("neck_01", "neck_02", "head")


# Played by BasiliskEntity's own controllers; the rest (strike, bite, gaze, hiss, taste_air, hit, death) are
# declared on the shared beast_action layer.
EXTRA = ("coiled", "threaten", "coil", "uncoil", "rear")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool="basilisk_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, colours=28, shared_clips=False, extra_clips=EXTRA, resize=True)


if __name__ == "__main__":
    sys.exit(main())
