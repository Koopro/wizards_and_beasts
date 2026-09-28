#!/usr/bin/env python3
"""The ten dragon breeds: one skeleton, ten sets of furniture, ten tempos.

Replaces `tools/dragons/` (the parametric generator older than rigkit) and
`tools/horntail_model.py`. Seen in a running client, the parametric breeds were one shape — a
box horse on block-long stilts with plank wings stuck out sideways and brick-striped scales — and
told apart only by colour; all ten sampled transparent texels. `documentation/ENTITY_ART_AUDIT.md`
§3.2.

The Entity Design Bible fixes the family and the deltas. Family: a heavy wedge head, a long neck
*and* a long tail in balance, wings larger than the body, a low heavy stance. Deltas, all canon:
Horntail — the spiked tail is the silhouette, bronze horn crown; Ironbelly — largest mass, shortest
wings, low slung; Opaleye — slenderest, most upright neck, pupil-less opal eyes; Short-Snout — the
only blunt muzzle; Fireball — face fringe, snub, broad skull, bulging eyes; Vipertooth — smallest,
compact, fanged; Ridgeback — a ridge of plates down the spine; Longhorn — two long golden horns
pointing forward; Hebridean Black — bat wings, ridged spine, arrow tail; Welsh Green — the plain
baseline. Tempo: Vipertooth +20 %, Ironbelly -30 %.

**True size, scale 1.0.** The old breeds were authored small and blown up 1.4-2.2x by the render-
only `dragon.scale`, so their texels were up to twice the size of every other mob's and the two
biggest rendered twice their hitbox height. These are authored at the size they render (the
generator removes `dragon.scale` from each definition), sized so the standing dragon fills its
2.7 x 2.9 box and the Vipertooth its 1.7 x 1.9 one.

**Wings rest spread and every ground clip folds them.** Authoring the fold as the rest pose puts
the membrane geometry in a frame nobody can reason about; authoring it spread keeps the geometry
legible and moves the fold into one constant (`FOLD`) that `idle`, `hit`, `death`, `stretch`,
`shake`, `bite` and `breath` all start from. `fly` is the only clip that works around the rest
pose. Because the dragon authors all of these itself, `reactions.fill` adds nothing — its generic
hit would snap the wings open.

`breath` and `bite` belong to `DragonEntity`'s own `dragon_action` controller, so they ship on disk
but are not declared on `beast_action` (`emit(extra_clips=...)`).

Run from the repo root:  python tools/dragon_model.py [breed ...] [--force]
"""

import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

TOOL = "dragon_model.py"

# ----------------------------------------------------------------------------- breeds
#
# k: overall size. bulk: girth. leg: leg length. neck: neck length. rake: how upright the neck
# stands (degrees above level at the base). wing: span. tempo: clip speed (>1 faster).

# fold: the ground pose of the left wing root as (x, y, z) degrees over the spread rest pose, plus
# the fan scale that closes it, in rigkit's authoring sense (emit flips Y and Z for GeckoLib):
# negative y swings a wing toward the head, positive z lifts it. The
# default is the reference sheet's resting dragon: wings raised off the shoulders and swept back
# over the hips like a half-furled sail — the silhouette that says "dragon" from across a valley.
# Palettes follow the user's reference sheet (2026-09-25), not the older film-still guesses.

FOLD_DEFAULT = ((0, 55, 75), (0, 15, 0), (0, 15, 0))
FOLD_SCALE_DEFAULT = (0.6, 1.0, 0.85)

# A wyvern (the films' dragon, and the Horntail, Ridgeback, Opaleye, Ironbelly and Hebridean on the
# reference sheet) has no forelegs: its wings are its arms and it walks on their clawed wrists like a
# bat. The fold drops the arm straight down and flips the hand back up out of the way; `wyvern_wing`
# sizes the arm so the wrist lands on the ground.
WYVERN_FOLD = ((0, 0, -80), (0, 0, -10), (0, 0, 165))
WYVERN_FOLD_SCALE = (1.0, 1.0, 0.45)

BREEDS = {
    "common_welsh_green": dict(
        k=0.9, bulk=1.0, leg=0.95, neck=1.1, rake=48, wing=1.0, tempo=1.0,
        snout="long", horns="pair", dorsal="low", tip="plain", membrane="plain",
        eye_glow=False,
        pal=dict(base="#6DB33A", belly="#C9DE6E", dark="#3F7A22", accent="#E0CF8A",
                 membrane="#5E9E30", eye="#F0C43A", claw="#D8C48A")),
    "antipodean_opaleye": dict(
        k=0.95, bulk=0.82, leg=1.12, neck=1.3, rake=62, wing=1.1, tempo=1.1,
        snout="long", horns="crown_swept", dorsal="frill", tip="plain", membrane="plain",
        eye_glow=True, wyvern=True,
        pal=dict(base="#B8E2E4", belly="#EEF8F2", dark="#7FB0C0", accent="#E6C874",
                 membrane="#D2EEF0", eye="#F4FAFF", claw="#C9A95A", sheen="#EAB8DC",
                 sheen2="#F2E7A0", sheen3="#9FD8B8")),
    "chinese_fireball": dict(
        k=1.0, bulk=1.15, leg=0.85, neck=0.85, rake=22, wing=0.95, tempo=1.05,
        snout="snub", horns="fringe", dorsal="spikes", tip="tuft", membrane="plain",
        eye_glow=False,
        fold=((0, 75, 30), (0, 10, -10), (0, 10, -10)), fold_scale=(0.55, 1, 0.8),
        pal=dict(base="#C42A1E", belly="#E8A840", dark="#7C1610", accent="#E9B23A",
                 membrane="#9E2018", eye="#F4CC1F", claw="#E3B03A")),
    "swedish_short_snout": dict(
        k=1.0, bulk=1.0, leg=1.0, neck=1.15, rake=58, wing=1.0, tempo=1.0,
        snout="blunt", horns="swept", dorsal="low", tip="plain", membrane="plain",
        eye_glow=True, stripes="#E4EEF6",
        fold=((0, 45, 85), (0, 10, 0), (0, 10, 0)), fold_scale=(0.65, 1, 0.9),
        pal=dict(base="#2F78AE", belly="#9CC8E4", dark="#1B4A74", accent="#E4EEF6",
                 membrane="#28689A", eye="#8EE2FF", claw="#D6E2EE")),
    "norwegian_ridgeback": dict(
        k=1.0, bulk=0.9, leg=1.05, neck=1.2, rake=52, wing=1.05, tempo=1.0,
        snout="long", horns="pair", dorsal="quills", tip="plain", membrane="plain",
        eye_glow=True, wyvern=True, fangs=True,
        pal=dict(base="#8C7552", belly="#BDA67C", dark="#5C4A33", accent="#6A563C",
                 membrane="#7E6A4A", eye="#3FC4D2", claw="#4A3C2A")),
    "peruvian_vipertooth": dict(
        k=0.72, bulk=0.85, leg=1.0, neck=1.0, rake=40, wing=1.05, tempo=1.2,
        snout="viper", horns="nubs", dorsal="spikes", tip="plain", membrane="plain",
        eye_glow=False, fangs=True, spine_band=True,
        pal=dict(base="#C8602A", belly="#E6A266", dark="#7A3418", accent="#2B2220", band="#2B2220",
                 membrane="#A84A22", eye="#9EE04A", claw="#2B2220")),
    "romanian_longhorn": dict(
        k=1.05, bulk=1.0, leg=1.0, neck=1.15, rake=56, wing=1.05, tempo=1.0,
        snout="long", horns="long", dorsal="low", tip="plain", membrane="plain",
        eye_glow=False,
        fold=((0, 25, 95), (0, 8, 0), (0, 8, 0)), fold_scale=(0.75, 1, 0.95),
        pal=dict(base="#2E5E2C", belly="#6E9C50", dark="#1A3A18", accent="#D9A83A",
                 membrane="#2A5426", eye="#E6C63E", claw="#D9A83A")),
    "hebridean_black": dict(
        k=1.05, bulk=0.95, leg=1.05, neck=1.25, rake=60, wing=1.3, tempo=1.05,
        snout="long", horns="swept", dorsal="spikes", tip="arrow", membrane="bat",
        eye_glow=True, wyvern=True,
        pal=dict(base="#4A3E5E", belly="#6A5C80", dark="#241C30", accent="#2A2234",
                 membrane="#8A4A62", eye="#C06CFF", claw="#2A2234")),
    "hungarian_horntail": dict(
        k=1.1, bulk=1.05, leg=1.0, neck=1.1, rake=46, wing=1.25, tempo=0.9,
        snout="long", horns="crown", dorsal="spikes", tip="horntail", membrane="plain",
        eye_glow=False, wyvern=True,
        pal=dict(base="#4E4A40", belly="#7A6A50", dark="#2A2822", accent="#B8883C",
                 membrane="#5A5244", eye="#F2A63A", claw="#C9A35A")),
    "ukrainian_ironbelly": dict(
        k=1.22, bulk=1.35, leg=0.82, neck=1.0, rake=44, wing=0.9, tempo=0.7,
        snout="long", horns="ram", dorsal="plates", tip="plain", membrane="plain",
        eye_glow=True, wyvern=True, lattice=True,
        pal=dict(base="#BDBAB3", belly="#E4E1DA", dark="#76736D", accent="#6E6A63",
                 membrane="#A8A49C", eye="#E0281E", claw="#55524C")),
}

HITBOX = {"peruvian_vipertooth": (1.7, 1.9)}
DEFAULT_HITBOX = (2.7, 2.9)
TEX_W = 128


# ----------------------------------------------------------------------------- geometry


class Builder:
    """Rig construction in unscaled units: positions multiply by k, sizes round to whole texels."""

    def __init__(self, cid, b):
        self.b = b
        self.k = b["k"]
        self.rig = Rig(cid, TEX_W)

    def s(self, v):
        return max(1, int(round(v * self.k)))

    def p(self, *v):
        return tuple(round(x * self.k, 2) for x in v)

    def bone(self, name, parent, pivot, rot=None):
        return self.rig.bone(name, parent, self.p(*pivot), rot)

    def box(self, bone, cx, y0, cz, w, h, d, key=None, inflate=None, rot=None, sign=1):
        """A cube centred on (cx, cz) with its base at y0, in unscaled units."""
        W, H, D = self.s(w), self.s(h), self.s(d)
        ox, oy, oz = cx * self.k - W / 2.0, y0 * self.k, cz * self.k - D / 2.0
        pivot = None
        if rot:
            pivot = (cx * self.k, (y0 + h / 2.0) * self.k, cz * self.k)
        return self.rig.cube(bone, (round(ox, 2), round(oy, 2), round(oz, 2)), (W, H, D), key=key,
                             inflate=inflate, rotation=rot, pivot=pivot)


def wyvern_wing(b):
    """Wing scale at which a wyvern's folded arm reaches from the shoulder to the ground.

    The arm is the 15-unit humerus at WYVERN_FOLD's 80 degrees plus the 13-unit forearm hanging
    straight; the shoulder sits one unit under the back, `lift + 1 + 13 * bulk`.
    """
    shoulder = 20 * b["leg"] + 1 + 13 * b["bulk"]
    reach = 15 * math.sin(math.radians(-WYVERN_FOLD[0][2])) + 13
    return shoulder / reach


def build(cid):
    b = BREEDS[cid]
    g = Builder(cid, b)
    bulk, legf, neckf, wingf = b["bulk"], b["leg"], b["neck"], b["wing"]
    lift = 20 * legf  # shoulder height
    if b.get("wyvern"):
        wingf = wyvern_wing(b)

    # --- body: chest, barrel, hips. Deep chest, the barrel slung under it, narrower hips.
    g.bone("body", "root", (0, lift + 2, -2))
    top = lift + 2 + 13 * bulk
    g.box("body", 0, lift - 5, -10, 16 * bulk, 15 * bulk, 13, key="chest")
    g.box("body", 0, lift - 5, 3, 15 * bulk, 13.5 * bulk, 15, key="barrel")
    g.bone("hips", "body", (0, lift + 1, 10))
    g.box("hips", 0, lift - 4, 15, 13 * bulk, 12 * bulk, 11, key="haunch")

    # --- legs: crouched reptile stance, elbows back, knees forward, toes spread flat.
    fx, hx_ = 6.5 * bulk, 6 * bulk

    def foreleg(side, sign):
        rigkit.limb(g.rig, f"foreleg_{side}", "body", x=round(fx * g.k, 2), z=round(-10 * g.k, 2),
                    sign=sign, joints=[
                        ("", round(lift * g.k, 2), g.s(10 * legf), g.s(5.5 * bulk), g.s(6.5), (12, 0, 0)),
                        ("lower", round((lift - 10 * legf) * g.k, 2), g.s(8 * legf), g.s(4.5 * bulk), g.s(5), (-22, 0, 0)),
                        ("paw", round(3 * g.k, 2), g.s(3), g.s(6.5 * bulk), g.s(8), (10, 0, 0)),
                    ])

    def hindleg(side, sign):
        rigkit.limb(g.rig, f"hindleg_{side}", "hips", x=round(hx_ * g.k, 2), z=round(16 * g.k, 2),
                    sign=sign, joints=[
                        ("", round((lift + 2) * g.k, 2), g.s(11 * legf), g.s(7 * bulk), g.s(11), (-16, 0, 0)),
                        ("shank", round((lift - 9 * legf + 2) * g.k, 2), g.s(9 * legf), g.s(5 * bulk), g.s(6), (32, 0, 0)),
                        ("foot", round(3 * g.k, 2), g.s(3), g.s(7 * bulk), g.s(10), (-16, 0, 0)),
                    ])

    if not b.get("wyvern"):
        # A wyvern has no forelegs: it stands on its hind legs and the wrists of its folded wings.
        g.rig.pair(foreleg)
    g.rig.pair(hindleg)

    # --- neck: three segments built straight forward, raked up by rest rotation, the head
    # levelled again at the end so the rake reads as a swan-necked carriage, not a ramp.
    rake = b["rake"]
    ny = lift + 7 * bulk
    seg = [(8.5 * bulk, 8.5 * bulk, 8 * neckf), (7.5 * bulk, 7.5 * bulk, 7.5 * neckf),
           (6.5 * bulk, 6.5 * bulk, 7 * neckf)]
    z = -16.0
    parent = "body"
    rots = (-rake, -rake * 0.25, rake * 0.45)
    for i, ((w, h, d), r) in enumerate(zip(seg, rots), 1):
        name = f"neck_{i}"
        g.bone(name, parent, (0, ny, z + 1), (r, 0, 0))
        g.box(name, 0, ny - h / 2, z - d / 2 + 1, w, h, d + 2)
        parent = name
        z -= d
    # Head: skull, muzzle, jaw, brow. The muzzle decides the breed profile.
    head_rot = rake * 0.8
    g.bone("head", parent, (0, ny, z + 1), (head_rot, 0, 0))
    sk = dict(long=(9.5, 7.5, 9), snub=(11, 8.5, 8), blunt=(10, 9, 8.5), viper=(8, 6.5, 9))[b["snout"]]
    mz = dict(long=(7, 5, 9), snub=(9, 6, 4), blunt=(9, 7, 5), viper=(5.5, 4, 10))[b["snout"]]
    skull_z = z - sk[2] / 2 + 1
    g.box("head", 0, ny - 4, skull_z, sk[0] * bulk ** 0.5, sk[1], sk[2], key="skull")
    muzzle_z = z - sk[2] + 1 - mz[2] / 2
    g.box("head", 0, ny - 4, muzzle_z, mz[0] * bulk ** 0.5, mz[1], mz[2], key="muzzle")
    g.box("head", 0, ny - 4 + sk[1] - 1.5, z - sk[2] + 3.5, sk[0] * bulk ** 0.5 + 1, 2, 4, key="brow")
    g.bone("jaw", "head", (0, ny - 4, z - sk[2] + 3))
    jaw_len = sk[2] * 0.6 + mz[2]
    g.box("jaw", 0, ny - 6, z - sk[2] + 3 - jaw_len / 2, mz[0] * bulk ** 0.5 - 1, 2, jaw_len, key="jaw")
    front = z - sk[2] + 1 - mz[2]

    if b["snout"] == "snub":
        # Fireball: protruding eyes, the one breed with eyes standing proud of the skull.
        for sign in (1, -1):
            g.box("head", sign * (sk[0] * bulk ** 0.5 / 2), ny + 0.5, skull_z - 2, 2, 2, 2,
                  key=f"eye_{'left' if sign > 0 else 'right'}")

    # Horns.
    horns = b["horns"]
    hy = ny - 4 + sk[1]
    hz = skull_z + sk[2] / 2 - 1
    if horns in ("swept", "crown_swept"):
        # Short-Snout, Hebridean, Opaleye: a long pair laid back along the neck line, the way the
        # reference sheet draws them — low, parallel, reading as one sweep from the brow.
        for sign in (1, -1):
            side = "left" if sign > 0 else "right"
            g.bone(f"horn_{side}", "head", (sign * 3, hy, hz), (16, sign * -10, sign * 8))
            g.box(f"horn_{side}", sign * 3, hy - 1, hz + 5, 2, 2, 10)
            g.bone(f"horn_{side}_tip", f"horn_{side}", (sign * 3, hy, hz + 10), (14, 0, 0))
            g.box(f"horn_{side}_tip", sign * 3, hy - 0.5, hz + 10 + 3, 1, 1, 6)
    if horns == "crown_swept":
        # Opaleye: a gold crown — two shorter spikes above and outside the main pair.
        for i, (ang, off) in enumerate(((30, 1.5), (48, 3.5))):
            for sign in (1, -1):
                g.box("head", sign * (3 + off), hy - 1 + i, hz - 1, 1, 1, 6 - 1.5 * i, key=f"crown_{i}_{sign}",
                      rot=(ang, -sign * (18 + 12 * i), 0))
    if horns == "ram":
        # Ironbelly: heavy horns that leave the skull backwards and curl down and forward past
        # the jaw — the reference sheet's goat curl, the thing that makes it read as massive.
        for sign in (1, -1):
            side = "left" if sign > 0 else "right"
            g.bone(f"horn_{side}", "head", (sign * 3.5, hy, hz), (28, sign * -22, 0))
            g.box(f"horn_{side}", sign * 3.5, hy - 1.5, hz + 3.5, 3, 3, 7)
            g.bone(f"horn_{side}_tip", f"horn_{side}", (sign * 3.5, hy, hz + 7), (-75, 0, 0))
            g.box(f"horn_{side}_tip", sign * 3.5, hy - 1, hz + 7 + 3, 2, 2, 6)
            g.bone(f"horn_{side}_curl", f"horn_{side}_tip", (sign * 3.5, hy, hz + 13), (-70, 0, 0))
            g.box(f"horn_{side}_curl", sign * 3.5, hy - 1, hz + 13 + 2, 2, 2, 4)
    if horns in ("pair", "nubs", "crown", "fringe"):
        n = 2
        length = dict(pair=7, nubs=3, crown=9, fringe=4)[horns]
        for sign in (1, -1):
            side = "left" if sign > 0 else "right"
            g.bone(f"horn_{side}", "head", (sign * 3, hy, hz), (38, sign * 14, sign * -18))
            g.box(f"horn_{side}", sign * 3, hy - 1, hz + length / 2, 2, 2, length)
    if horns == "crown":
        # Horntail: a fan of horns swept back off the skull, the crown the films give it.
        for i, (ang, off) in enumerate(((20, 0), (40, 1.5), (60, 3))):
            for sign in (1, -1):
                g.box("head", sign * (2 + off), hy - 1, hz - 1 + off, 2, 2, 9 - 2 * i, key=f"crown_{i}_{sign}",
                      rot=(ang, sign * (22 + 14 * i), 0))
    if horns == "long":
        # Longhorn: two long golden horns sweeping forward over the snout — canon, and the thing
        # the old rig had backwards (one horn, straight up).
        for sign in (1, -1):
            side = "left" if sign > 0 else "right"
            g.bone(f"horn_{side}", "head", (sign * 3.5, hy, hz + 1), (-24, sign * -12, sign * -6))
            g.box(f"horn_{side}", sign * 3.5, hy - 1, hz + 1 - 6, 2, 2, 12)
            g.bone(f"horn_{side}_tip", f"horn_{side}", (sign * 3.5, hy, hz - 10), (-18, 0, 0))
            g.box(f"horn_{side}_tip", sign * 3.5, hy - 0.5, hz - 10 - 4, 1, 1, 8)
    if horns == "fringe":
        # Fireball: a ruff of golden spikes round the face.
        for i, (y, ang) in enumerate(((0, 70), (2.5, 50), (5, 30))):
            for sign in (1, -1):
                g.box("head", sign * (sk[0] * bulk ** 0.5 / 2 + 0.5), ny - 3 + y, skull_z + 2, 1, 1, 5,
                      key=f"fringe_{i}_{sign}", rot=(0, sign * ang, 0))

    # --- tail: six tapering segments in a shallow S, the tip lifted.
    tw = [10, 8.5, 7, 5.5, 4, 3]
    tl = [12, 11, 11, 10, 9, 9]
    trot = [-20, -8, 6, 9, 7, 5]
    if b["tip"] == "horntail":
        tl = [13, 12, 12, 11, 10, 10]
    ty = lift + 3 * bulk
    z = 20.0
    parent = "hips"
    for i in range(6):
        name = f"tail_{i + 1}"
        g.bone(name, parent, (0, ty, z - 1), (trot[i], 0, 0))
        w = tw[i] * bulk
        g.box(name, 0, ty - w * 0.45, z - 1 + tl[i] / 2, w, w * 0.9, tl[i] + 1)
        parent = name
        z += tl[i]
    tip_z = z
    if b["tip"] == "arrow":
        g.box("tail_6", 0, ty - 0.5, tip_z + 1.5, 7, 1, 5, key="barb")
    if b["tip"] == "tuft":
        g.box("tail_6", 0, ty - 2.5, tip_z + 2, 5, 5, 4, key="tuft", inflate=0.3)
    if b["tip"] == "horntail":
        # The tail is the Horntail. Bronze spikes out of both flanks of every segment, a mace
        # at the end.
        zz = 20.0
        for i in range(6):
            w = tw[i] * bulk
            for sign in (1, -1):
                g.box(f"tail_{i + 1}", sign * (w / 2 + 2), ty - 1, zz + tl[i] * 0.55, 5 - i * 0.4, 2, 2,
                      key=f"tspike_{i}_{sign}", rot=(0, sign * -35, sign * 20))
            zz += tl[i]
        g.box("tail_6", 0, ty - 3.5, tip_z + 2, 7, 7, 6, key="mace")
        for sign in (1, -1):
            g.box("tail_6", sign * 4.5, ty - 1, tip_z + 2, 4, 2, 2, key=f"mace_{sign}")
        g.box("tail_6", 0, ty + 3, tip_z + 2, 2, 4, 2, key="mace_top")

    # --- dorsal furniture: cubes on existing bones, never bones of their own.
    dorsal = b["dorsal"]
    if dorsal != "none":
        spots = [("body", -12, top), ("body", -5, top - 0.5), ("body", 2, top - 1), ("hips", 9.5, top - 2),
                 ("hips", 14.5, top - 2.5), ("neck_1", -18, ny + 4 * bulk), ("neck_2", -25, ny + 3.5 * bulk)]
        size = dict(low=(2, 2, 2), spikes=(2, 5, 2), ridge=(1, 6, 4), frill=(1, 3, 4),
                    plates=(3, 2, 4), quills=(1, 8, 2))[dorsal]
        # Ridgeback quills lean back along the spine instead of standing up.
        lean = (-40, 0, 0) if dorsal == "quills" else None
        for i, (bone, zz, yy) in enumerate(spots):
            g.box(bone, 0, yy - (2 if lean else 0.5), zz, *size, key=f"dorsal_{i}", rot=lean)
        zz = 20.0
        for i in range(5):
            h = size[1] * (1 - i * 0.15)
            g.box(f"tail_{i + 1}", 0, ty + tw[i] * bulk * 0.45 - (2 if lean else 0.5), zz + tl[i] / 2, size[0],
                  max(1, h), size[2], key=f"tdorsal_{i}", rot=lean)
            zz += tl[i]

    # --- wings, rest spread: arm, forearm, hand along +x, membranes trailing back (+z).
    def wing(side, sign):
        wy = top - 1
        wz = -9.0
        root = f"wing_{side}"
        x0 = 6 * bulk
        seg = [(15 * wingf, 14 * wingf), (13 * wingf, 19 * wingf), (16 * wingf, 22 * wingf)]
        names = [root, f"{root}_fore", f"{root}_hand"]
        parent = "body"
        x = x0
        for (length, chord), name in zip(seg, names):
            g.bone(name, parent, (sign * x, wy, wz))
            g.box(name, sign * (x + length / 2), wy - 1, wz, length, 2.5 if name == root else 2, 2.5,
                  key=f"{name}_spar")
            g.box(name, sign * (x + length / 2), wy - 0.5, wz + chord / 2, length, 1, chord,
                  key=f"{name}_membrane")
            parent = name
            x += length
        g.box(f"{root}_fore", sign * (x0 + 15 * wingf + 13 * wingf), wy + 0.5, wz - 1.5, 2, 3, 2,
              key=f"{root}_thumb")
        if b.get("wyvern"):
            # The wrist is a wyvern's forefoot: three talons reaching forward off the end of the
            # forearm, flat on the ground once the wing is folded down into a leg.
            wrist = x0 + 15 * wingf + 13 * wingf
            for i, dx in enumerate((-2, 0, 2)):
                g.box(f"{root}_fore", sign * (wrist - 1), wy - 1 + dx, wz - 4, 2, 2, 5,
                      key=f"{root}_talon_{i}")

    g.rig.pair(wing)
    return g.rig


# ----------------------------------------------------------------------------- skin


def paint(rig, tex_h, cid):
    b = BREEDS[cid]
    pal = {k: hx(v) for k, v in b["pal"].items()}
    base, belly, dark = pal["base"], pal["belly"], pal["dark"]
    skin = Skin(rig, tex_h)
    d = skin.d

    hide = rig.keys("chest", "barrel", "haunch", "neck_", "skull", "muzzle", "brow", "jaw", "tail_",
                    "foreleg", "hindleg")
    skin.skin(hide, base, bottom=belly, bevel=0.9, dither=0.0)

    # Scales: a staggered 3x2 crescent, low contrast, one dark and one light texel per scale.
    lit = shade(base, 1.14)
    low = mix(base, dark, 0.55)
    for key in hide:
        f = rig.faces(key)
        for name in ("top", "east", "west", "north", "south"):
            x0, y0, w, h = f[name]
            for yy in range(h):
                off = 0 if (yy // 2) % 2 == 0 else 1
                for xx in range(w):
                    if (xx + off * 2) % 3 == 2 and yy % 2 == 1:
                        d.point((x0 + xx, y0 + yy), fill=low)
                    elif (xx + off * 2) % 3 == 0 and yy % 2 == 0 and yy > 0:
                        d.point((x0 + xx, y0 + yy), fill=lit)

    # Belly plates: lighter bands across the underside, and the bottom row of each flank.
    plate_line = mix(belly, dark, 0.35)
    for key in rig.keys("chest", "barrel", "haunch", "neck_", "tail_", "jaw"):
        f = rig.faces(key)
        x0, y0, w, h = f["bottom"]
        for yy in range(0, h, 2):
            d.line([(x0, y0 + yy), (x0 + w - 1, y0 + yy)], fill=plate_line)
        for name in ("east", "west", "north", "south"):
            x0, y0, w, h = f[name]
            rows = 2 if h >= 6 else 1
            for yy in range(h - rows, h):
                d.line([(x0, y0 + yy), (x0 + w - 1, y0 + yy)], fill=belly)
    # Spine: a dark stripe along the top.
    for key in rig.keys("chest", "barrel", "haunch", "neck_", "tail_", "skull"):
        x0, y0, w, h = rig.faces(key)["top"]
        cx = x0 + w // 2
        d.line([(cx - 1, y0), (cx - 1, y0 + h - 1)], fill=dark)
        if w > 4:
            d.line([(cx, y0), (cx, y0 + h - 1)], fill=dark)

    if "sheen" in pal:
        # Opaleye: an opal sheen — scattered pale teal and pink flecks on the pearl.
        for key in hide:
            f = rig.faces(key)
            for name in ("top", "east", "west"):
                x0, y0, w, h = f[name]
                for yy in range(h):
                    for xx in range(w):
                        n = rigkit._hash(x0 + xx, y0 + yy, 7) % 100
                        if n < 5:
                            d.point((x0 + xx, y0 + yy), fill=pal["sheen"])
                        elif n < 9:
                            d.point((x0 + xx, y0 + yy), fill=pal["sheen2"])
                        elif n < 13:
                            d.point((x0 + xx, y0 + yy), fill=pal["sheen3"])

    if b.get("stripes"):
        # Short-Snout: pale bands ringing the tail and neck, as on the reference sheet.
        band = hx(b["stripes"])
        for key in rig.keys("tail_", "neck_"):
            f = rig.faces(key)
            for name in ("east", "west"):
                x0, y0, w, h = f[name]
                c = w // 2
                d.rectangle([x0 + c - 1, y0, x0 + c, y0 + h - 1], fill=band)
            x0, y0, w, h = f["top"]
            r = h // 2
            d.rectangle([x0, y0 + r - 1, x0 + w - 1, y0 + r], fill=band)

    if b.get("spine_band"):
        # Vipertooth: a black band down the whole back, the copper only on the flanks.
        for key in rig.keys("chest", "barrel", "haunch", "neck_", "tail_", "skull", "muzzle"):
            f = rig.faces(key)
            x0, y0, w, h = f["top"]
            m = w // 4 if w >= 4 else 0
            d.rectangle([x0 + m, y0, x0 + w - 1 - m, y0 + h - 1], fill=pal["band"])
            for name in ("east", "west"):
                x0, y0, w, h = f[name]
                d.line([(x0, y0), (x0 + w - 1, y0)], fill=pal["band"])

    if b.get("lattice"):
        # Ironbelly: the iron belly — a diamond lattice of dark seams over the pale plates.
        seam = mix(belly, dark, 0.55)
        for key in rig.keys("chest", "barrel", "haunch", "neck_", "tail_"):
            x0, y0, w, h = rig.faces(key)["bottom"]
            for yy in range(h):
                for xx in range(w):
                    if (xx + yy) % 4 == 0 or (xx - yy) % 4 == 0:
                        d.point((x0 + xx, y0 + yy), fill=seam)

    # Claws: toe tips.
    for key in rig.keys("foreleg_left_paw", "foreleg_right_paw", "hindleg_left_foot", "hindleg_right_foot"):
        x0, y0, w, h = rig.faces(key)["north"]
        for xx in range(0, w, 2):
            d.point((x0 + xx, y0 + h - 1), fill=pal["claw"])
            d.point((x0 + xx, y0 + h - 2), fill=pal["claw"])
        x0, y0, w, h = rig.faces(key)["top"]
        for xx in range(0, w, 2):
            d.point((x0 + xx, y0), fill=pal["claw"])

    # Face: nostrils, teeth, eyes, a dark mouth line.
    mz = rig.faces("muzzle")
    x0, y0, w, h = mz["north"]
    d.point((x0 + 1, y0 + 1), fill=dark)
    d.point((x0 + w - 2, y0 + 1), fill=dark)
    tooth = hx("#EDE6D2")
    for name in ("east", "west"):
        x0, y0, w, h = mz[name]
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=dark)
        for xx in range(0, w, 2):
            d.point((x0 + xx, y0 + h - 1), fill=tooth)
        if b.get("fangs"):
            front = x0 + w - 2 if name == "west" else x0 + 1
            d.point((front, y0 + h - 1), fill=tooth)
    x0, y0, w, h = mz["north"]
    d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=tooth)
    jx, jy, jw, jh = rig.faces("jaw")["north"]
    d.line([(jx, jy), (jx + jw - 1, jy)], fill=tooth)

    eye = pal["eye"]
    pupil = hx("#101010")
    sk = rig.faces("skull")
    if "eye_left" in rig._cubes:
        for key in ("eye_left", "eye_right"):
            skin.skin(key, eye, bevel=0, dither=0)
            skin.mark(key, "north", 0, 0, pupil, w=1, h=2)
    for name, front_col in (("east", 1), ("west", -3)):
        x0, y0, w, h = sk[name]
        ex = x0 + front_col if front_col >= 0 else x0 + w + front_col
        ey = y0 + 2
        d.rectangle([ex, ey, ex + 1, ey], fill=eye)
        d.line([(ex - 1, ey - 1), (ex + 2, ey - 1)], fill=dark)  # brow shadow
        if not b["eye_glow"] or cid == "hebridean_black":
            d.point((ex + (1 if name == "east" else 0), ey), fill=pupil)
        if b["eye_glow"]:
            skin.glow_rect((ex, ey, 2, 1))

    # Horns, spikes, fringe, barbs: the accent colour, darker at the tip.
    acc = pal["accent"]
    furniture = rig.keys("horn_", "crown_", "fringe_", "dorsal_", "tdorsal_", "tspike_", "mace", "barb",
                         "tuft", "brow")
    furniture = [k for k in furniture if k != "brow"]
    skin.skin(furniture, acc, bevel=0.85, dither=0.0)
    skin.tip([k for k in furniture if k.startswith(("horn_", "crown_", "tspike_"))], shade(acc, 0.7), rows=1)
    if b["dorsal"] in ("ridge", "frill"):
        skin.skin(rig.keys("dorsal_", "tdorsal_"), mix(acc, base, 0.4), bevel=0.8, dither=0.0)

    # Wings: dark spars, membrane with finger bones and a scalloped trailing edge.
    mem = pal["membrane"]
    mem_lo = shade(mem, 1.18)
    bone_col = mix(mem, dark, 0.6)
    skin.skin(rig.keys("wing_left_spar", "wing_right_spar", "wing_left_fore_spar", "wing_right_fore_spar",
                       "wing_left_hand_spar", "wing_right_hand_spar", "wing_left_thumb",
                       "wing_right_thumb"), mix(base, dark, 0.3), bevel=0.85, dither=0.0)
    skin.tip(rig.keys("wing_left_thumb", "wing_right_thumb"), pal["claw"], rows=1)
    skin.skin(rig.keys("wing_left_talon", "wing_right_talon"), pal["claw"], bevel=0.85, dither=0.0)
    for key in [k for k in rig.keys("wing_") if k.endswith("_membrane")]:
        sign = 1 if "_left" in key else -1
        hand = "_hand" in key
        f = rig.faces(key)
        for name in ("top", "bottom"):
            x0, y0, w, h = f[name]
            fill = mem if name == "top" else mem_lo
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=fill)
            # Rows run leading edge -> trailing edge; on the bottom face the order is reversed.
            def row_of(r, name=name, h=h):
                return r if name == "top" else h - 1 - r
            # Finger bones fanning back from the leading edge.
            fingers = 2 if hand else 1
            for i in range(fingers):
                start = int(w * (0.35 + 0.35 * i))
                for r in range(h):
                    drift = int(r * (0.45 + 0.25 * i))
                    col = start + drift if sign > 0 else w - 1 - start - drift
                    if 0 <= col < w:
                        d.point((x0 + col, y0 + row_of(r)), fill=bone_col)
            # Trailing edge: scallops between the fingers, deep ones on a bat wing.
            depth = 3 if b["membrane"] == "bat" else 2
            period = 7 if b["membrane"] == "bat" else 9
            for c in range(w):
                cut = int(round(depth * abs(math.sin(math.pi * c / period))))
                for r in range(h - cut, h):
                    d.point((x0 + c, y0 + row_of(r)), fill=(0, 0, 0, 0))
            # The hand tapers to a point at its outer end.
            if hand:
                for r in range(h):
                    keep = w - int(w * 0.55 * r / max(1, h - 1))
                    for c in range(w):
                        col = c if sign > 0 else w - 1 - c
                        if c >= keep:
                            d.point((x0 + col, y0 + row_of(r)), fill=(0, 0, 0, 0))
        # The 1-texel rims of a membrane: the trailing rim goes, the others take the edge colour.
        for name in ("east", "west", "north"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=bone_col)
        x0, y0, w, h = f["south"]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(0, 0, 0, 0))
    return skin


# ----------------------------------------------------------------------------- animation

# The ground pose of each wing bone, as a delta over the spread rest pose. Left side; the right
# mirrors Y and Z. Set per breed by `anims()` from `fold` / `fold_scale` (defaults above). Found
# by rendering candidates with `pose_preview.py`: lift the whole wing (negative z), sweep it back
# (negative y) and close it like a fan with a scale on the root. Folding the forearm back on
# itself inside the membrane plane stacks three membranes on one plane and z-fights into a
# jumble; bending the forearm and hand only a little keeps one clean sail.
PATTERNS = ("wing_{s}", "wing_{s}_fore", "wing_{s}_hand")
FOLD = dict(zip(PATTERNS, FOLD_DEFAULT))
FOLD_SCALE = FOLD_SCALE_DEFAULT

# Absolute wing poses the big clips move toward (same frame as FOLD).
SPREAD_UP = {"wing_{s}": (0, 0, 35), "wing_{s}_fore": (0, 0, -12), "wing_{s}_hand": (0, 0, -10)}
MANTLE = {"wing_{s}": (0, 20, 20), "wing_{s}_fore": (0, 5, -10), "wing_{s}_hand": (0, 5, -10)}
SLUMP = {"wing_{s}": (0, 25, -28), "wing_{s}_fore": (0, 0, -4), "wing_{s}_hand": (0, 0, -6)}


def set_fold(b):
    global FOLD, FOLD_SCALE
    wyvern = b.get("wyvern")
    FOLD = dict(zip(PATTERNS, b.get("fold", WYVERN_FOLD if wyvern else FOLD_DEFAULT)))
    FOLD_SCALE = b.get("fold_scale", WYVERN_FOLD_SCALE if wyvern else FOLD_SCALE_DEFAULT)


def toward(target, w=1.0):
    """The delta over FOLD that lands a fraction `w` of the way to an absolute wing pose."""
    return {p: tuple((t - f) * w for t, f in zip(target[p], FOLD[p])) for p in PATTERNS}


def fold(extra=None, sides=("left", "right")):
    """Rotation of every wing bone in the fold, optionally plus a per-bone delta."""
    out = {}
    for side in sides:
        sign = 1 if side == "left" else -1
        for pat, (x, y, z) in FOLD.items():
            name = pat.format(s=side)
            ex = (extra or {}).get(pat, (0, 0, 0))
            out[name] = (x + ex[0], sign * (y + ex[1]), sign * (z + ex[2]))
    return out


def wings_track(frames):
    """`frames` = [(t, extra-by-pattern[, openness])] -> tracks for every wing bone around the fold.

    `openness` (0 folded .. 1 spread) interpolates the fan scale on the root; the rotations are
    given in full by the caller.
    """
    tracks, scales = {}, []
    for frame in frames:
        t, extra = frame[0], frame[1]
        openness = frame[2] if len(frame) > 2 else 0.0
        for name, r in fold(extra).items():
            tracks.setdefault(name, []).append((t, r))
        scales.append((t, tuple(f + (1 - f) * openness for f in FOLD_SCALE)))
    out = {n: {"rotation": kf(*v)} for n, v in tracks.items()}
    for side in ("left", "right"):
        out[f"wing_{side}"]["scale"] = kf(*scales)
    return out


def rot(*frames):
    return {"rotation": kf(*frames)}


def anims(cid):
    b = BREEDS[cid]
    set_fold(b)
    T = 1.0 / b["tempo"]

    def t(v):
        return round(v * T, 3)

    legs = ("foreleg_left", "foreleg_right", "hindleg_left", "hindleg_right")
    tails = [f"tail_{i}" for i in range(1, 7)]

    # idle: slow breath, a head that looks about, the tail swinging low, wings folded.
    L = t(4.0)
    idle = {
        "body": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 0.5, 0)), (L, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 0)), (L / 2, (-1.5, 0, 0)), (L, (0, 0, 0)))},
        "neck_1": rot((0, (0, 0, 0)), (L * 0.3, (-3, 6, 0)), (L * 0.7, (2, -6, 0)), (L, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (L * 0.35, (4, 12, 0)), (L * 0.75, (-4, -10, 0)), (L, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (L * 0.5, (3, 0, 0)), (L, (0, 0, 0))),
    }
    for i, name in enumerate(tails):
        a = 4 + i * 2.5
        idle[name] = rot((0, (0, a, 0)), (L / 2, (0, -a, 0)), (L, (0, a, 0)))
    idle.update(wings_track([(0, {}), (L * 0.5, {"wing_{s}": (0, 0, -3)}), (L, {})]))

    # fly: one stroke per cycle, tip lagging root; legs tucked, neck and tail streaming level.
    F = t(1.3)
    fly = {
        "body": {"rotation": kf((0, (6, 0, 0)), (F / 2, (3, 0, 0)), (F, (6, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (F * 0.3, (0, 1.2, 0)), (F * 0.8, (0, -0.6, 0)), (F, (0, 0, 0)))},
        "neck_1": rot((0, (22, 0, 0)), (F, (22, 0, 0))),
        "head": rot((0, (-8, 0, 0)), (F / 2, (-4, 0, 0)), (F, (-8, 0, 0))),
        "foreleg_left": rot((0, (55, 0, 0)), (F, (55, 0, 0))),
        "foreleg_right": rot((0, (55, 0, 0)), (F, (55, 0, 0))),
        "hindleg_left": rot((0, (62, 0, 0)), (F, (62, 0, 0))),
        "hindleg_right": rot((0, (62, 0, 0)), (F, (62, 0, 0))),
        "foreleg_left_lower": rot((0, (30, 0, 0)), (F, (30, 0, 0))),
        "foreleg_right_lower": rot((0, (30, 0, 0)), (F, (30, 0, 0))),
    }
    for i, name in enumerate(tails):
        a = 3 + i * 1.5
        ph = i * 0.08
        fly[name] = rot((0, (a + (12 if i == 0 else 0), 0, 0)), (F * (0.5 + ph) % F, (-a + (12 if i == 0 else 0), 0, 0)),
                        (F, (a + (12 if i == 0 else 0), 0, 0)))
    for side, sign in (("left", 1), ("right", -1)):
        fly[f"wing_{side}"] = rot((0, (0, 0, sign * 38)), (F * 0.45, (0, 0, sign * -30)), (F, (0, 0, sign * 38)))
        fly[f"wing_{side}_fore"] = rot((0, (0, 0, sign * 12)), (F * 0.55, (0, 0, sign * -22)), (F, (0, 0, sign * 12)))
        fly[f"wing_{side}_hand"] = rot((0, (0, sign * 6, sign * 18)), (F * 0.6, (0, sign * -4, sign * -26)),
                                       (F, (0, sign * 6, sign * 18)))
    for k in list(fly):
        if k in ("foreleg_left", "foreleg_right", "hindleg_left", "hindleg_right") or k.startswith("tail"):
            pass

    # breath: rear back, draw in, then thrust the head out with the jaw wide and hold.
    B = t(1.6)
    breath = {
        "neck_1": rot((0, (0, 0, 0)), (B * 0.25, (-18, 0, 0)), (B * 0.4, (9, 0, 0)), (B * 0.85, (8, 0, 0)), (B, (0, 0, 0))),
        "neck_2": rot((0, (0, 0, 0)), (B * 0.25, (-8, 0, 0)), (B * 0.4, (10, 0, 0)), (B * 0.85, (8, 0, 0)), (B, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (B * 0.25, (-16, 0, 0)), (B * 0.4, (-6, 0, 0)), (B * 0.85, (-6, 0, 0)), (B, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (B * 0.25, (8, 0, 0)), (B * 0.4, (38, 0, 0)), (B * 0.85, (38, 0, 0)), (B, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (B * 0.25, (-5, 0, 0)), (B * 0.4, (3, 0, 0)), (B, (0, 0, 0))),
    }
    breath.update(wings_track([(0, {}), (B * 0.3, toward(MANTLE, 0.6), 0.4), (B * 0.85, toward(MANTLE, 0.6), 0.4),
                               (B, {})]))

    # bite: a lunge — head and neck strike forward and down, jaw snaps.
    Bt = t(0.6)
    bite = {
        "neck_1": rot((0, (0, 0, 0)), (Bt * 0.3, (-10, 0, 0)), (Bt * 0.5, (26, 0, 0)), (Bt, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (Bt * 0.3, (-12, 0, 0)), (Bt * 0.5, (14, 0, 0)), (Bt, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (Bt * 0.3, (34, 0, 0)), (Bt * 0.5, (0, 0, 0)), (Bt, (0, 0, 0))),
        "body": {"position": kf((0, (0, 0, 0)), (Bt * 0.5, (0, 0, -2)), (Bt, (0, 0, 0)))},
    }
    bite.update(wings_track([(0, {}), (Bt * 0.5, toward(MANTLE, 0.35), 0.2), (Bt, {})]))

    # hit: recoil, head flung up, wings half-open.
    H = 0.45
    hit = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.1, (-6, 0, 0)), (H, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.1, (0, 0.4, 1.2)), (H, (0, 0, 0)))},
        "neck_1": rot((0, (0, 0, 0)), (0.1, (-12, 0, 0)), (H, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.12, (-20, 0, 0)), (H, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.1, (28, 0, 0)), (0.35, (0, 0, 0))),
        "tail_1": rot((0, (0, 0, 0)), (0.12, (16, 0, 0)), (0.28, (-6, 0, 0)), (H, (0, 0, 0))),
        "tail_3": rot((0, (0, 0, 0)), (0.18, (12, 0, 0)), (H, (0, 0, 0))),
    }
    hit.update(wings_track([(0, {}), (0.12, toward(SPREAD_UP, 0.5), 0.5), (H, {})]))

    # death: rear, then the legs go and the wings slump open; held until the body is removed.
    D = 1.2
    death = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.7, (6, 0, 0)), (D, (6, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.2, (0, 1, 0)), (0.7, (0, -3, 0)), (D, (0, -3, 0)))},
        "neck_1": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.8, (34, 0, 0)), (D, (34, 0, 0))),
        "neck_2": rot((0, (0, 0, 0)), (0.8, (14, 0, 0)), (D, (14, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.85, (24, 0, 0)), (D, (24, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.2, (30, 0, 0)), (0.9, (16, 0, 0)), (D, (16, 0, 0))),
    }
    for leg in legs:
        fore = leg.startswith("fore")
        death[leg] = rot((0, (0, 0, 0)), (0.6, (-40 if fore else 30, 0, 0)), (D, (-40 if fore else 30, 0, 0)))
        low = f"{leg}_lower" if fore else f"{leg}_shank"
        death[low] = rot((0, (0, 0, 0)), (0.65, (60 if fore else -45, 0, 0)), (D, (60 if fore else -45, 0, 0)))
    for i, name in enumerate(tails):
        death[name] = rot((0, (0, 0, 0)), (0.7 + 0.04 * i, (-10, (-1) ** i * 8, 0)), (D, (-10, (-1) ** i * 8, 0)))
    slump = toward(SLUMP)
    death.update(wings_track([(0, {}), (0.2, toward(SPREAD_UP, 0.5), 0.5), (0.9, slump, 1.0),
                              (D, slump, 1.0)]))

    # stretch: unfold the wings high, neck up, a yawn; then fold again.
    S = t(3.0)
    open_ = toward(SPREAD_UP)
    stretch = {
        "neck_1": rot((0, (0, 0, 0)), (S * 0.3, (-14, 0, 0)), (S * 0.7, (-14, 0, 0)), (S, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (S * 0.3, (-18, 0, 0)), (S * 0.7, (-18, 0, 0)), (S, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (S * 0.35, (34, 0, 0)), (S * 0.55, (34, 0, 0)), (S * 0.7, (0, 0, 0)), (S, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (S * 0.3, (-4, 0, 0)), (S * 0.7, (-4, 0, 0)), (S, (0, 0, 0))),
    }
    stretch.update(wings_track([(0, {}), (S * 0.3, open_, 1.0), (S * 0.7, open_, 1.0), (S, {})]))

    # shake: a heavy shudder along the body, head shaking, wings rattling in the fold.
    K = t(1.2)
    shake = {"body": rot(*[(K * i / 8, (0, 0, (6 if i % 2 else -6) * (1 - i / 8))) for i in range(8)], (K, (0, 0, 0)))}
    shake["head"] = rot(*[(K * i / 8, (0, (14 if i % 2 else -14) * (1 - i / 8), 0)) for i in range(8)], (K, (0, 0, 0)))
    shake.update(wings_track([(0, {}), (K * 0.2, {"wing_{s}": (0, 0, -8)}), (K * 0.4, {"wing_{s}": (0, 0, 4)}),
                              (K * 0.6, {"wing_{s}": (0, 0, -6)}), (K, {})]))

    return {
        "idle": clip(L, idle),
        "fly": clip(F, fly),
        "breath": clip(B, breath, loop=False),
        "bite": clip(Bt, bite, loop=False),
        "hit": clip(H, hit, loop=False),
        "death": dict(clip(D, death, loop=False), loop="hold_on_last_frame"),
        "stretch": clip(S, stretch, loop=False),
        "shake": clip(K, shake, loop=False),
    }


# ----------------------------------------------------------------------------- definition


def update_definition(cid):
    """Drop the render-only scale and give the dragon its Bible idle profile.

    Only `dragon.scale` (render-only, blew the texels up) is removed. The top-level `scale` is
    `Attributes.SCALE` — model *and* hitbox, set per breed by the 2026-09-27 style pass so the
    dragons rank above the Troll (VISUAL_STYLE_REPORT R9). It is data, not this rig's business,
    and a rerun must keep it.
    """
    path = os.path.join(rigkit.DEF_DIR, cid + ".json")
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    data.get("dragon", {}).pop("scale", None)
    data.setdefault("behaviour", {})["idle"] = {"actions": ["stretch", "shake"],
                                              "minDelay": 300, "maxDelay": 700}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def fit_clips(rig, clips):
    """Drop tracks for bones this breed does not have (a wyvern's forelegs)."""
    bones = rig.bone_names()
    for body in clips.values():
        body["bones"] = {n: t for n, t in body["bones"].items() if n in bones}
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("breeds", nargs="*")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    status = 0
    for cid in a.breeds or BREEDS:
        update_definition(cid)
        rig = build(cid)
        rows = rig.pack()
        if rows > 256:
            # Sheet shape follows the islands: a tall 128-wide strip packs the big breeds worse
            # than a square-ish 256-wide sheet.
            global TEX_W
            TEX_W = 256
            rig = build(cid)
            rows = rig.pack()
            TEX_W = 128
        tex_h = rigkit.sheet_height(rows)
        status |= rigkit.emit(rig, paint(rig, tex_h, cid), fit_clips(rig, anims(cid)), cid=cid, tool=TOOL,
                              hitbox=HITBOX.get(cid, DEFAULT_HITBOX), tex_h=tex_h, force=a.force,
                              extra_clips=("breath", "bite"), colours=32)
    return status


if __name__ == "__main__":
    sys.exit(main())
