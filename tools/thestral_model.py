#!/usr/bin/env python3
"""The Thestral: rig, hide, glowmask and every clip `ThestralEntity` plays.

Replaces the hand-built rig of 2026-08 (110 cubes, 40 bones, fractional sizes, wings that could not fold). Canon
(Order of the Phoenix): black, skeletal horses — "their black coats clung to their skeletons, of which every bone was
visible" — with dragonish faces, white shining pupil-less eyes, a thin black tail, and vast black leathery wings like
a giant bat's. Gentle, clever, drawn to the smell of blood.

Minecraft-first and gaunt: a narrow barrel inside a ribcage whose side plates are cut between the ribs so the dark
body shows through (the skeleton is silhouette plus texture, not a hundred bone cubes), pelvis blades and a spine
ridge, a long thin neck with a ragged crest, a long head with a blunt muzzle and white eyes, thin knobbed legs on
dark hooves, a whip tail with a tuft, and bat wings: arm, forearm and three finger bones over torn membranes. Wings
are authored spread and folded by every ground clip, the hippogriff's scheme (`FOLD`).

Clips: idle, walk, run, fly, glide, graze (movement); takeoff, land, flap, spread, rear, hurt, attack, death (the
entity's action controller).

Run from the repo root:  python tools/thestral_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "thestral"
TOOL = "thestral_model.py"
HITBOX = (1.4, 1.8)
TEX_W = 128

HIDE = hx("#1D1B21")
HIDE_DARK = hx("#121015")
BONE = hx("#5E5964")
BONE_LIT = hx("#85808C")
MEMBRANE = hx("#3B3142")
MEMBRANE_LIT = hx("#54465E")
MANE = hx("#0B0A0D")
HOOF = hx("#0D0C0F")
EYE = hx("#E6EEFF")
MOUTH = hx("#3A2A30")

FINGERS = (8, 24, 40)       # rest yaw of each finger: the open fan
CLIPS = ("idle", "walk", "run", "fly", "glide", "graze", "takeoff", "land", "flap", "spread", "rear", "hurt",
         "attack", "death")


# ----------------------------------------------------------------------------- rig


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 19, 0))
    rig.cube("body", (-2.5, 14, -6), (5, 7, 13), key="barrel")
    rig.cube("body", (-3.5, 20, -7), (7, 3, 6), key="withers")
    rig.cube("body", (-1, 22, -6), (2, 1, 14), key="spine")
    # Ribcage: two side plates, cut between the ribs on the skin so the barrel shows through.
    rig.cube("body", (-4, 14.5, -6), (1, 7, 10), key="ribs_right")
    rig.cube("body", (3, 14.5, -6), (1, 7, 10), key="ribs_left")
    rig.cube("body", (-2, 13, -6), (4, 1, 9), key="keel")

    rig.bone("hips", "body", (0, 20, 7))
    rig.cube("hips", (-3, 15, 6), (6, 7, 6), key="croup")
    rig.cube("hips", (-4, 20, 6), (8, 2, 3), key="pelvis")

    rig.bone("neck", "body", (0, 20, -6), (34, 0, 0))
    rig.cube("neck", (-2, 19, -8.5), (4, 13, 5), key="neck")
    rig.cube("neck", (-0.5, 22, -5), (1, 11, 2), key="mane")

    rig.bone("head", "neck", (0, 31, -6), (-40, 0, 0))
    rig.cube("head", (-2, 29, -12), (4, 5, 7), key="skull")
    rig.cube("head", (-1.5, 29, -17), (3, 3, 5), key="muzzle")
    rig.bone("jaw", "head", (0, 29, -11))
    rig.cube("jaw", (-1.5, 28, -16), (3, 1, 6), key="jaw")

    def ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, 34, -7), (-20, 0, sign * -15))
        rig.cube(f"ear_{side}", Rig.mirror((1, 34, -7.5), (1, 3, 1), sign), (1, 3, 1))

    rig.pair(ear)

    def wing(side, sign):
        root = f"wing_{side}"
        rig.bone(root, "body", (sign * 3, 21.5, -5))
        rig.cube(root, Rig.mirror((3, 21, -5.5), (10, 2, 2), sign), (10, 2, 2), key=f"{root}_arm")
        rig.cube(root, Rig.mirror((3, 21.5, -4), (10, 1, 9), sign), (10, 1, 9), key=f"{root}_arm_membrane")
        rig.bone(f"{root}_fore", root, (sign * 13, 21.5, -5))
        rig.cube(f"{root}_fore", Rig.mirror((13, 21, -5.5), (11, 2, 2), sign), (11, 2, 2), key=f"{root}_forearm")
        rig.cube(f"{root}_fore", Rig.mirror((13, 21.5, -4), (11, 1, 12), sign), (11, 1, 12),
                 key=f"{root}_fore_membrane")
        rig.bone(f"{root}_hand", f"{root}_fore", (sign * 24, 21.5, -5))
        rig.cube(f"{root}_hand", Rig.mirror((24, 21, -6), (2, 2, 2), sign), (2, 2, 2), key=f"{root}_thumb")
        rig.cube(f"{root}_hand", Rig.mirror((24, 21.5, -4), (10, 1, 13), sign), (10, 1, 13),
                 key=f"{root}_hand_membrane")
        for i, fan in enumerate(FINGERS):
            length = 14 - i * 2
            rig.bone(f"{root}_f{i}", f"{root}_hand", (sign * 24, 21.5, -4.5), (0, sign * fan, 0))
            rig.cube(f"{root}_f{i}", Rig.mirror((24, 21, -5), (length, 1, 1), sign), (length, 1, 1),
                     key=f"{root}_finger{i}")

    rig.pair(wing)

    def foreleg(side, sign):
        rigkit.limb(rig, f"foreleg_{side}", "body", x=2, z=-5, sign=sign, joints=[
            ("", 15, 8, 2, 3, (4, 0, 0)),
            ("lower", 7, 6, 2, 2, (-4, 0, 0)),
            ("hoof", 1, 1, 3, 3, None),
        ])

    def hindleg(side, sign):
        rigkit.limb(rig, f"hindleg_{side}", "hips", x=2, z=10, sign=sign, joints=[
            ("", 17, 9, 3, 4, (-10, 0, 0)),
            ("cannon", 8, 7, 2, 2, (14, 0, 0)),
            ("hoof", 1, 1, 3, 3, (-4, 0, 0)),
        ])

    rig.pair(foreleg)
    rig.pair(hindleg)

    rig.bone("tail", "hips", (0, 21, 12), (-38, 0, 0))
    rig.cube("tail", (-0.5, 20.5, 12), (1, 1, 9), key="tail")
    rig.bone("tail_tuft", "tail", (0, 21, 21), (-10, 0, 0))
    rig.cube("tail_tuft", (-1, 20, 20), (2, 2, 6), key="tail_tuft")
    return rig


# ----------------------------------------------------------------------------- skin


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="fleck")
    d = skin.d
    sides = ("east", "west", "north", "south")

    hide = ["barrel", "withers", "croup", "neck", "skull", "muzzle", "jaw", "keel"] + rig.keys("ear_")
    legs = [k for k in rig.keys("foreleg", "hindleg") if not k.endswith("hoof")]
    skin.skin(hide + legs, HIDE, bottom=HIDE_DARK, bevel=0.9, dither=0.0)

    # Bone showing through the hide: knobbed joints, hip points, spine, cheek and brow ridges.
    skin.skin(["spine", "pelvis"], BONE, top=BONE_LIT, bevel=0.85, dither=0.0)
    for key in ("spine",):
        for name in ("east", "west", "top"):
            x0, y0, w, h = rig.faces(key)[name]
            for c in range(1, w, 3):
                d.line([(x0 + c, y0), (x0 + c, y0 + h - 1)], fill=HIDE_DARK)
    for key in legs:
        for name in sides:
            x0, y0, w, h = rig.faces(key)[name]
            d.line([(x0, y0), (x0 + w - 1, y0)], fill=BONE)                     # knee knob at the top
            d.line([(x0 + w // 2, y0 + 1), (x0 + w // 2, y0 + h - 2)], fill=shade(BONE, 0.7))  # shin bone
    for name in ("east", "west"):
        x0, y0, w, h = rig.faces("croup")[name]
        d.rectangle([x0 + 1, y0 + 1, x0 + w - 2, y0 + 2], fill=BONE)            # hip blade
        x0, y0, w, h = rig.faces("skull")[name]
        d.line([(x0, y0 + 1), (x0 + w - 1, y0 + 1)], fill=BONE)                  # brow ridge
        d.line([(x0 + 1, y0 + h - 1), (x0 + w - 2, y0 + h - 1)], fill=BONE)      # cheekbone
        x0, y0, w, h = rig.faces("neck")[name]
        for r in range(1, h, 3):
            d.point((x0 + w // 2, y0 + r), fill=BONE)                            # vertebrae
    x0, y0, w, h = rig.faces("muzzle")["north"]
    d.point((x0, y0 + 1), fill=HIDE_DARK)
    d.point((x0 + w - 1, y0 + 1), fill=HIDE_DARK)
    for name in ("east", "west"):
        x0, y0, w, h = rig.faces("jaw")[name]
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=MOUTH)

    # Ribs: bone bars on the side plates with the hide cut away between them.
    for key in ("ribs_left", "ribs_right"):
        f = rig.faces(key)
        for name in ("east", "west", "north", "south", "top", "bottom"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=BONE)
        for name in ("east", "west"):
            x0, y0, w, h = f[name]
            for c in range(w):
                if c % 3 != 1:
                    for r in range(1, h - 1):
                        d.point((x0 + c, y0 + r), fill=(0, 0, 0, 0))
                else:
                    d.point((x0 + c, y0), fill=BONE_LIT)
    skin.skin("keel", BONE, bevel=0.85, dither=0.0)

    # Mane and tail: black, ragged.
    skin.skin(["mane", "tail", "tail_tuft"], MANE, bevel=0.0, dither=0.0)
    for key in ("mane", "tail_tuft"):
        for name in ("east", "west"):
            x0, y0, w, h = rig.faces(key)[name]
            for c in range(w):
                if rigkit._hash(x0 + c, y0, 5) % 3 == 0:
                    d.point((x0 + c, y0), fill=HIDE)   # ragged strands
                if c % 2 == 0:
                    d.line([(x0 + c, y0 + 1), (x0 + c, y0 + h - 1)], fill=shade(MANE, 1.6))
    skin.skin(rig.keys("foreleg_left_hoof", "foreleg_right_hoof", "hindleg_left_hoof", "hindleg_right_hoof"),
              HOOF, bevel=0.85, dither=0.0)

    # Eyes: large, white, pupil-less, and the only thing on it that glows.
    for name, col in (("east", 1), ("west", -3)):
        x0, y0, w, h = rig.faces("skull")[name]
        ex = x0 + col if col >= 0 else x0 + w + col
        d.rectangle([ex, y0 + 2, ex + 1, y0 + 3], fill=EYE)
        skin.glow_rect((ex, y0 + 2, 2, 2))

    # Wings: bone spars, dark membrane with finger bones and a torn trailing edge.
    for side, sign in (("left", 1), ("right", -1)):
        root = f"wing_{side}"
        skin.skin(rig.keys(f"{root}_arm", f"{root}_forearm", f"{root}_thumb"), HIDE, top=BONE, bevel=0.85,
                  dither=0.0)
        skin.skin(rig.keys(f"{root}_finger"), BONE, top=BONE_LIT, bevel=0.0, dither=0.0)
        for key in (f"{root}_arm_membrane", f"{root}_fore_membrane", f"{root}_hand_membrane"):
            f = rig.faces(key)
            for name in ("top", "bottom"):
                x0, y0, w, h = f[name]
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=MEMBRANE if name == "top" else MEMBRANE_LIT)

                def row(r, name=name, h=h):
                    return r if name == "top" else h - 1 - r

                # One faint vein per panel and a few deep scallops: a bat's wing is a smooth sheet
                # stretched between long fingers, torn at the edge — not a fringe.
                vein = w // 2
                for r in range(h - 2):
                    c = vein + r // 5
                    col = c if sign > 0 else w - 1 - c
                    if 0 <= col < w:
                        d.point((x0 + col, y0 + row(r)), fill=shade(MEMBRANE, 0.8))
                period = 5
                for c in range(w):
                    phase = (c % period) / (period - 1)
                    cut = int(round(3 * (1 - abs(2 * phase - 1))))
                    for r in range(h - cut, h):
                        d.point((x0 + c, y0 + row(r)), fill=(0, 0, 0, 0))
                tear = rigkit._hash(len(key), sign + 2, 13) % max(1, w - 2) + 1
                d.point((x0 + tear, y0 + row(h // 2)), fill=(0, 0, 0, 0))
                if key.endswith("hand_membrane"):
                    for r in range(h):
                        keep = w - int(w * 0.5 * r / max(1, h - 1))
                        for c in range(keep, w):
                            col = c if sign > 0 else w - 1 - c
                            d.point((x0 + col, y0 + row(r)), fill=(0, 0, 0, 0))
            for name in ("east", "west", "north"):
                x0, y0, w, h = f[name]
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=shade(MEMBRANE, 0.7))
            x0, y0, w, h = f["south"]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(0, 0, 0, 0))
    return skin


# ----------------------------------------------------------------------------- animation

FOLD = {"wing_{s}": (0, 88, 96), "wing_{s}_fore": (0, 0, 0), "wing_{s}_hand": (0, 0, 0),
        **{f"wing_{{s}}_f{i}": (0, -fan, 0) for i, fan in enumerate(FINGERS)}}
FOLD_SCALE = (0.6, 1.0, 0.4)
WINGS = tuple(FOLD)
LEGS = ("foreleg_left", "foreleg_right", "hindleg_left", "hindleg_right")


def rot(*frames):
    return {"rotation": kf(*frames)}


def wing_frames(frames):
    """[(t, {pattern: delta}, openness)] -> rotation + fan-scale tracks for both wings (0 folded, 1 spread)."""
    out = {}
    for side, sign in (("left", 1), ("right", -1)):
        for pat in WINGS:
            name = pat.format(s=side)
            track = []
            for t, delta, opn in frames:
                fx, fy, fz = FOLD[pat]
                dx, dy, dz = delta.get(pat, (0, 0, 0))
                k = 1.0 - opn
                track.append((t, (fx * k + dx, sign * (fy * k + dy), sign * (fz * k + dz))))
            out[name] = rot(*track)
        out[f"wing_{side}"]["scale"] = kf(*[(t, tuple(f + (1 - f) * o for f in FOLD_SCALE)) for t, _, o in frames])
    return out


def folded(length):
    return wing_frames([(0, {}, 0.0), (length, {}, 0.0)])


UP = {"wing_{s}": (0, 0, 46), "wing_{s}_fore": (0, 0, 16), "wing_{s}_hand": (0, 0, 12)}
DOWN = {"wing_{s}": (0, 0, -32), "wing_{s}_fore": (0, 0, -10), "wing_{s}_hand": (0, 0, -18)}
RECOVER = {"wing_{s}": (0, 14, 10), "wing_{s}_fore": (0, 0, 24), "wing_{s}_hand": (0, 0, 22)}
HALF = {"wing_{s}": (0, -20, 30), "wing_{s}_fore": (0, 0, 8)}


def gait(length, amp, phases, *, lower=30):
    out = {}
    for leg, phase in phases.items():
        a = [(length * i / 4, amp * [0, 1, 0, -1][(i + int(phase * 4)) % 4]) for i in range(5)]
        out[leg] = rot(*[(t, (v, 0, 0)) for t, v in a])
        low = f"{leg}_lower" if leg.startswith("fore") else f"{leg}_cannon"
        bend = lower if leg.startswith("fore") else -lower
        b = [(length * i / 4, bend * [0, 0, 1, 0][(i + int(phase * 4)) % 4]) for i in range(5)]
        out[low] = rot(*[(t, (v, 0, 0)) for t, v in b])
    return out


def anims():
    clips = {}
    # idle: slow, calm — the eerie stillness is the point; breath, an ear flick, the tail swinging.
    L = 5.0
    idle = {
        "body": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 0.2, 0)), (L, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (L * 0.4, (-3, 6, 0)), (L * 0.75, (2, -5, 0)), (L, (0, 0, 0))),
        "ear_left": rot((0, (0, 0, 0)), (L * 0.6, (0, 0, 0)), (L * 0.64, (-18, 0, 0)), (L * 0.7, (0, 0, 0)), (L, (0, 0, 0))),
        "tail": rot((0, (0, 10, 0)), (L / 2, (0, -10, 0)), (L, (0, 10, 0))),
        "tail_tuft": rot((0, (0, 12, 0)), (L / 2, (0, -12, 0)), (L, (0, 12, 0))),
        "jaw": rot((0, (0, 0, 0)), (L, (0, 0, 0))),
    }
    idle.update(folded(L))
    clips["idle"] = clip(L, idle)

    W = 1.1
    walk = gait(W, 18, {"foreleg_left": 0.0, "hindleg_right": 0.0, "foreleg_right": 0.5, "hindleg_left": 0.5})
    walk.update({"neck": rot((0, (4, 0, 0)), (W / 4, (-2, 0, 0)), (W / 2, (4, 0, 0)), (3 * W / 4, (-2, 0, 0)),
                             (W, (4, 0, 0))),
                 "tail": rot((0, (0, 6, 0)), (W / 2, (0, -6, 0)), (W, (0, 6, 0)))})
    walk.update(folded(W))
    clips["walk"] = clip(W, walk)

    R = 0.6
    run = gait(R, 34, {"foreleg_left": 0.0, "foreleg_right": 0.25, "hindleg_left": 0.5, "hindleg_right": 0.75},
               lower=40)
    run.update({
        "body": {"rotation": kf((0, (-4, 0, 0)), (R / 2, (4, 0, 0)), (R, (-4, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (R / 4, (0, 0.8, 0)), (R / 2, (0, 0, 0)), (3 * R / 4, (0, 0.8, 0)),
                                (R, (0, 0, 0)))},
        "neck": rot((0, (14, 0, 0)), (R / 2, (6, 0, 0)), (R, (14, 0, 0))),
        "tail": rot((0, (40, 0, 0)), (R / 2, (32, 0, 0)), (R, (40, 0, 0))),
    })
    run.update(wing_frames([(0, {"wing_{s}": (0, 0, 8)}, 0.05), (R / 2, {"wing_{s}": (0, 0, 14)}, 0.12),
                            (R, {"wing_{s}": (0, 0, 8)}, 0.05)]))
    clips["run"] = clip(R, run)

    tucked = {"foreleg_left": (55, 0, 0), "foreleg_right": (55, 0, 0),
              "foreleg_left_lower": (-50, 0, 0), "foreleg_right_lower": (-50, 0, 0),
              "hindleg_left": (65, 0, 0), "hindleg_right": (65, 0, 0), "tail": (48, 0, 0)}

    def with_tuck(length, extra):
        out = {k: rot((0, v), (length, v)) for k, v in tucked.items()}
        out.update(extra)
        return out

    # fly: slow deep beats — a thestral is heavy-winged and unhurried.
    F = 1.4
    fly = with_tuck(F, {
        "body": {"rotation": kf((0, (6, 0, 0)), (F, (6, 0, 0))),
                 "position": kf((0, (0, -0.8, 0)), (F * 0.45, (0, 1.0, 0)), (F, (0, -0.8, 0)))},
        "neck": rot((0, (-16, 0, 0)), (F * 0.45, (-20, 0, 0)), (F, (-16, 0, 0))),
        "head": rot((0, (14, 0, 0)), (F, (14, 0, 0))),
    })
    fly.update(wing_frames([(0, UP, 1), (F * 0.45, DOWN, 1), (F * 0.75, RECOVER, 1), (F, UP, 1)]))
    clips["fly"] = clip(F, fly)

    G = 2.8
    glide = with_tuck(G, {
        "body": {"rotation": kf((0, (4, 0, -3)), (G / 2, (4, 0, 3)), (G, (4, 0, -3)))},
        "neck": rot((0, (-16, 0, 0)), (G, (-16, 0, 0))),
        "head": rot((0, (14, 0, 0)), (G, (14, 0, 0))),
    })
    glide.update(wing_frames([(0, {"wing_{s}": (0, 0, 6)}, 1), (G / 2, {"wing_{s}": (0, 0, 11)}, 1),
                              (G, {"wing_{s}": (0, 0, 6)}, 1)]))
    clips["glide"] = clip(G, glide)

    # graze: it picks at the ground — neck right down, a slow tearing motion of the head.
    Gz = 3.0
    graze = {
        "neck": rot((0, (46, 0, 0)), (Gz, (46, 0, 0))),
        "head": rot((0, (26, 0, 0)), (0.8, (32, 5, 0)), (1.6, (26, -3, 0)), (2.3, (32, 0, 0)), (Gz, (26, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.8, (12, 0, 0)), (1.0, (0, 0, 0)), (2.3, (12, 0, 0)), (2.5, (0, 0, 0)),
                   (Gz, (0, 0, 0))),
        "body": rot((0, (4, 0, 0)), (Gz, (4, 0, 0))),
    }
    graze.update(folded(Gz))
    clips["graze"] = clip(Gz, graze)

    T = 1.0
    takeoff = {
        "body": {"position": kf((0, (0, 0, 0)), (0.3, (0, -2, 0)), (0.55, (0, 2.5, 0)), (T, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 0)), (0.3, (6, 0, 0)), (0.55, (-14, 0, 0)), (T, (4, 0, 0)))},
        "hindleg_left": rot((0, (0, 0, 0)), (0.3, (-18, 0, 0)), (0.55, (40, 0, 0)), (T, (60, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (0.3, (-18, 0, 0)), (0.55, (40, 0, 0)), (T, (60, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.35, (-30, 0, 0)), (T, (50, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.35, (-30, 0, 0)), (T, (50, 0, 0))),
    }
    takeoff.update(wing_frames([(0, {}, 0), (0.3, UP, 1), (0.55, DOWN, 1), (0.8, RECOVER, 1), (T, UP, 1)]))
    clips["takeoff"] = clip(T, takeoff, loop=False)

    Ld = 0.9
    brake = {"wing_{s}": (0, -30, 38), "wing_{s}_fore": (0, 0, 12), "wing_{s}_hand": (0, 0, 10)}
    land = {
        "body": {"rotation": kf((0, (-16, 0, 0)), (0.35, (-18, 0, 0)), (0.55, (6, 0, 0)), (Ld, (0, 0, 0))),
                 "position": kf((0, (0, 0.5, 0)), (0.4, (0, 0, 0)), (0.55, (0, -1.5, 0)), (Ld, (0, 0, 0)))},
        "foreleg_left": rot((0, (-40, 0, 0)), (0.4, (-30, 0, 0)), (0.55, (0, 0, 0)), (Ld, (0, 0, 0))),
        "foreleg_right": rot((0, (-40, 0, 0)), (0.4, (-30, 0, 0)), (0.55, (0, 0, 0)), (Ld, (0, 0, 0))),
    }
    land.update(wing_frames([(0, brake, 1), (0.4, brake, 0.8), (0.6, {}, 0.2), (Ld, {}, 0)]))
    clips["land"] = clip(Ld, land, loop=False)

    # flap: two hard beats — a steep climb under a rider.
    Fp = 0.8
    flap = {"body": {"position": kf((0, (0, 0, 0)), (0.2, (0, 0.8, 0)), (Fp, (0, 0, 0)))}}
    flap.update(wing_frames([(0, UP, 1), (0.2, DOWN, 1), (0.4, UP, 1), (0.6, DOWN, 1), (Fp, UP, 1)]))
    clips["flap"] = clip(Fp, flap, loop=False)

    # spread: standing, it opens its wings wide and holds them — the dramatic display — then folds.
    S = 3.0
    spread = {
        "neck": rot((0, (0, 0, 0)), (0.8, (-12, 0, 0)), (2.2, (-12, 0, 0)), (S, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (0.8, (-4, 0, 0)), (2.2, (-4, 0, 0)), (S, (0, 0, 0))),
    }
    spread.update(wing_frames([(0, {}, 0), (0.8, {"wing_{s}": (0, 0, 22)}, 1), (1.5, {"wing_{s}": (0, 0, 30)}, 1),
                               (2.2, {"wing_{s}": (0, 0, 22)}, 1), (S, {}, 0)]))
    clips["spread"] = clip(S, spread, loop=False)

    Rr = 1.3
    rear = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.4, (-36, 0, 0)), (0.9, (-36, 0, 0)), (Rr, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.4, (0, 3, 2)), (0.9, (0, 3, 2)), (Rr, (0, 0, 0)))},
        "hindleg_left": rot((0, (0, 0, 0)), (0.4, (32, 0, 0)), (0.9, (32, 0, 0)), (Rr, (0, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (0.4, (32, 0, 0)), (0.9, (32, 0, 0)), (Rr, (0, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.4, (-50, 0, 0)), (0.6, (-25, 0, 0)), (0.9, (-50, 0, 0)), (Rr, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.4, (-25, 0, 0)), (0.6, (-50, 0, 0)), (0.9, (-25, 0, 0)), (Rr, (0, 0, 0))),
        "foreleg_left_lower": rot((0, (0, 0, 0)), (0.4, (60, 0, 0)), (0.9, (60, 0, 0)), (Rr, (0, 0, 0))),
        "foreleg_right_lower": rot((0, (0, 0, 0)), (0.4, (60, 0, 0)), (0.9, (60, 0, 0)), (Rr, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.4, (20, 0, 0)), (0.9, (20, 0, 0)), (Rr, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.35, (26, 0, 0)), (0.8, (26, 0, 0)), (1.0, (0, 0, 0))),
    }
    rear.update(wing_frames([(0, {}, 0), (0.4, HALF, 0.6), (0.9, HALF, 0.6), (Rr, {}, 0)]))
    clips["rear"] = clip(Rr, rear, loop=False)

    H = 0.45
    hurt = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.1, (-6, 0, 0)), (H, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.1, (0, 0.3, 1.0)), (H, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.12, (-18, 0, 0)), (H, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.1, (28, 0, 0)), (0.35, (0, 0, 0))),
        "tail": rot((0, (0, 0, 0)), (0.12, (24, 0, 0)), (H, (0, 0, 0))),
    }
    hurt.update(wing_frames([(0, {}, 0), (0.12, HALF, 0.35), (H, {}, 0)]))
    clips["hurt"] = clip(H, hurt, loop=False)

    # attack: a kick with both hind legs, weight thrown onto the forelegs — a horse's defence, not a predator's.
    A = 0.7
    attack = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.2, (8, 0, 0)), (0.4, (16, 0, 0)), (A, (0, 0, 0)))},
        "hindleg_left": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.4, (70, 0, 0)), (A, (0, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.42, (70, 0, 0)), (A, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.3, (18, 0, 0)), (A, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.3, (22, 0, 0)), (0.5, (0, 0, 0)), (A, (0, 0, 0))),
    }
    attack.update(wing_frames([(0, {}, 0), (0.3, HALF, 0.3), (A, {}, 0)]))
    clips["attack"] = clip(A, attack, loop=False)

    D = 1.3
    death = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.8, (6, 0, 0)), (D, (6, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.2, (0, 0.8, 0)), (0.8, (0, -4, 0)), (D, (0, -4, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.9, (40, 0, 0)), (D, (40, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.9, (22, 0, 0)), (D, (22, 0, 0))),
    }
    for leg in LEGS:
        fore = leg.startswith("fore")
        death[leg] = rot((0, (0, 0, 0)), (0.6, (-36 if fore else 30, 0, 0)), (D, (-36 if fore else 30, 0, 0)))
        low = f"{leg}_lower" if fore else f"{leg}_cannon"
        death[low] = rot((0, (0, 0, 0)), (0.65, (60 if fore else -50, 0, 0)), (D, (60 if fore else -50, 0, 0)))
    slump = {"wing_{s}": (0, 30, -26)}
    death.update(wing_frames([(0, {}, 0), (0.2, HALF, 0.4), (1.0, slump, 0.8), (D, slump, 0.8)]))
    clips["death"] = dict(clip(D, death, loop=False), loop="hold_on_last_frame")
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool=TOOL, hitbox=HITBOX, tex_h=tex_h,
                       force=a.force, definition=False, shared_clips=False, extra_clips=CLIPS, colours=32)


if __name__ == "__main__":
    sys.exit(main())
