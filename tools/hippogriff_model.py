#!/usr/bin/env python3
"""The Hippogriff: one rig, five coats, and every clip `HippogriffEntity` plays.

Replaces the hand-built rig of 2026-08 (small upturned eagle head, two-segment slab wings, only idle/fly/walk
authored and walk unreachable). Canon (Prisoner of Azkaban, Fantastic Beasts): the head, wings and forelegs of a
giant eagle — hooked beak, orange eyes, talons — and the body, hind legs and tail of a horse. Hagrid's herd came in
storm grey (Buckbeak), bronze, pinkish roan, gleaming chestnut and inky black; those are the five coats, one rig.

Minecraft-first: a horse barrel and croup behind a deeper feathered eagle breast, the feathers fraying back over
the withers so the join reads as one animal; a strong neck with a ruff, a big skull with a dark hooked beak and a
crest of feathers swept back; wings in three segments with four separate primaries (they fold along the flank and
spread wide, the phoenix's scheme); eagle forelegs with feathered thighs, scaled shanks and talons; horse hind legs
with a hock and hooves; a long horse tail.

Wings are authored spread and every ground clip folds them from `FOLD` (see `phoenix_model.py`). Rotations are in
rigkit's authoring sense; `emit` converts to GeckoLib's.

Clips: idle, walk, run, fly, glide (movement); takeoff, land, flap, bow, rear (the entity's own controller);
attack, hit, death, graze, stretch, shake (the shared clip gate, declared in the definition).

Run from the repo root:  python tools/hippogriff_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, marker, mix, posterise, save, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "hippogriff"
TOOL = "hippogriff_model.py"
HITBOX = (1.7, 1.9)
TEX_W = 128

# feather: head/neck/breast/wing coverts · primaries: flight feathers · coat: horse body · hair: tail
COATS = {
    "storm_grey": dict(feather="#9AA3AD", feather_dark="#6C7580", feather_lit="#C4CBD2", primaries="#5E6B7A",
                       coat="#8E949A", coat_dark="#666C73", belly="#B3B8BD", hair="#4E545B"),
    "bronze": dict(feather="#B07A3E", feather_dark="#7C5226", feather_lit="#D6A466", primaries="#6A4A2A",
                   coat="#8F6334", coat_dark="#654320", belly="#B08858", hair="#4A3018"),
    "roan": dict(feather="#E0CFC8", feather_dark="#B8A097", feather_lit="#F4E8E2", primaries="#A48478",
                 coat="#C29C92", coat_dark="#946F66", belly="#DCC3BA", hair="#6E4A42"),
    "chestnut": dict(feather="#A0522D", feather_dark="#6E3418", feather_lit="#C8784A", primaries="#5A2E16",
                     coat="#8B4722", coat_dark="#5E2E14", belly="#B06C44", hair="#3C1E0E"),
    "black": dict(feather="#34363C", feather_dark="#1E2024", feather_lit="#50535B", primaries="#24262C",
                  coat="#2C2E33", coat_dark="#18191C", belly="#44464C", hair="#141416"),
}
DEFAULT_COAT = "storm_grey"
BEAK = hx("#3A3430")
BEAK_LIT = hx("#5E5650")
SCALE = hx("#C9A64A")
SCALE_DARK = hx("#957630")
TALON = hx("#24201C")
HOOF = hx("#2E2A26")
EYE = hx("#F29A2A")
PUPIL = hx("#120C08")

PRIMARY_FAN = (6, 18, 30, 42)
EXTRA = ("run", "glide", "takeoff", "land", "flap", "bow", "rear")


# ----------------------------------------------------------------------------- rig


def build():
    rig = Rig(CID, TEX_W)
    rig.bone("body", "root", (0, 19, 2))
    rig.cube("body", (-4.5, 13, -5), (9, 10, 14), key="barrel")
    rig.cube("body", (-5, 12, -12), (10, 12, 8), key="breast")
    rig.cube("body", (-4, 21, -6), (8, 3, 6), key="withers", inflate=0.3)

    rig.bone("croup", "body", (0, 20, 9))
    rig.cube("croup", (-4.5, 13, 8), (9, 10, 7), key="croup")

    rig.bone("neck", "body", (0, 21, -9), (22, 0, 0))
    rig.cube("neck", (-3, 20, -12), (6, 11, 6), key="neck")
    rig.cube("neck", (-3.5, 22, -12.5), (7, 5, 7), key="ruff", inflate=0.25)

    rig.bone("head", "neck", (0, 30.5, -9), (-24, 0, 0))
    rig.cube("head", (-3.5, 29, -16), (7, 7, 8), key="skull")
    rig.bone("beak", "head", (0, 32, -16))
    rig.cube("beak", (-1.5, 30, -21), (3, 3, 5), key="beak")
    rig.cube("beak", (-1.5, 28, -21), (3, 2, 1), key="beak_hook")
    rig.bone("jaw", "head", (0, 30, -16))
    rig.cube("jaw", (-1, 29, -19), (2, 1, 3), key="jaw")
    rig.bone("crest", "head", (0, 35, -9))
    rig.cube("crest", (-1, 33, -9), (2, 4, 4), key="crest_c", rotation=(-36, 0, 0), pivot=(0, 35, -9))
    rig.cube("crest", (1, 32, -9), (1, 3, 4), key="crest_l", rotation=(-44, 0, 16), pivot=(1.5, 34, -9))
    rig.cube("crest", (-2, 32, -9), (1, 3, 4), key="crest_r", rotation=(-44, 0, -16), pivot=(-1.5, 34, -9))

    def wing(side, sign):
        root = f"wing_{side}"
        rig.bone(root, "body", (sign * 5, 22.5, -8))
        rig.cube(root, Rig.mirror((5, 21.5, -9), (10, 2, 9), sign), (10, 2, 9), key=f"{root}_coverts")
        rig.bone(f"{root}_mid", root, (sign * 15, 22.5, -8))
        rig.cube(f"{root}_mid", Rig.mirror((15, 22, -8), (9, 1, 13), sign), (9, 1, 13), key=f"{root}_secondaries")
        rig.cube(f"{root}_mid", Rig.mirror((15, 22, -8.5), (9, 2, 4), sign), (9, 2, 4), key=f"{root}_mid_coverts")
        rig.bone(f"{root}_tip", f"{root}_mid", (sign * 24, 22.5, -7.5))
        for i in range(4):
            length = 13 - i
            z = -7 + i * 2.6
            rig.bone(f"{root}_p{i}", f"{root}_tip", (sign * 24, 22.5, z), (0, sign * PRIMARY_FAN[i], 0))
            rig.cube(f"{root}_p{i}", Rig.mirror((24, 22, z - 1), (length, 1, 3), sign), (length, 1, 3),
                     key=f"{root}_primary{i}")

    rig.pair(wing)

    def foreleg(side, sign):
        rigkit.limb(rig, f"foreleg_{side}", "body", x=3, z=-8, sign=sign, joints=[
            ("", 14, 6, 4, 5, (4, 0, 0)),
            ("shank", 8, 6, 2, 2, (-4, 0, 0)),
            ("talon", 2, 2, 4, 5, None),
        ])
        rig.cube(f"foreleg_{side}_talon", Rig.mirror((1.5, 0, -12.5), (3, 1, 2), sign), (3, 1, 2),
                 key=f"foreleg_{side}_claws")

    def hindleg(side, sign):
        rigkit.limb(rig, f"hindleg_{side}", "croup", x=3, z=12, sign=sign, joints=[
            ("", 16, 8, 4, 6, (-8, 0, 0)),
            ("cannon", 8, 6, 3, 3, (12, 0, 0)),
            ("hoof", 2, 2, 4, 4, (-4, 0, 0)),
        ])

    rig.pair(foreleg)
    rig.pair(hindleg)

    rig.bone("tail", "croup", (0, 21, 15), (-40, 0, 0))
    rig.cube("tail", (-1.5, 19.5, 14), (3, 3, 5), key="tail_dock")
    rig.bone("tail_hair", "tail", (0, 21, 19), (-20, 0, 0))
    rig.cube("tail_hair", (-2, 19, 18), (4, 3, 10), key="tail_hair")
    return rig


# ----------------------------------------------------------------------------- skin


def ramp(skin, key, faces, top, bottom):
    f = skin.rig.faces(key)
    for name in faces:
        x0, y0, w, h = f[name]
        for i in range(h):
            skin.d.line([(x0, y0 + i), (x0 + w - 1, y0 + i)], fill=mix(top, bottom, i / max(1, h - 1)))


def paint(rig, tex_h, coat=DEFAULT_COAT):
    c = {k: hx(v) for k, v in COATS[coat].items()}
    # Feather tips a step off the base, not the full dark: layered plumage, not a checkerboard.
    c["tip"] = mix(c["feather"], c["feather_dark"], 0.55)
    skin = Skin(rig, tex_h, grain="speckle")
    d = skin.d
    sides = ("east", "west", "north", "south")

    # Horse half: flat coat with a soft darker back line and a paler belly; a few dapples on grey and roan.
    horse = ["barrel", "croup"] + rig.keys("hindleg_left", "hindleg_right")
    horse = [k for k in horse if not k.endswith("hoof")]
    skin.skin(horse, c["coat"], bottom=c["belly"], bevel=0.9, dither=0.05, dither_colour=c["coat_dark"])
    if coat in ("storm_grey", "roan"):
        for key in ("barrel", "croup"):
            for name in ("east", "west", "top"):
                x0, y0, w, h = rig.faces(key)[name]
                for yy in range(1, h - 1, 3):
                    for xx in range((yy // 3) % 2 * 2, w - 1, 4):
                        d.rectangle([x0 + xx, y0 + yy, x0 + xx + 1, y0 + yy], fill=shade(c["coat"], 1.12))
    skin.skin(rig.keys("hindleg_left_hoof", "hindleg_right_hoof"), HOOF, bevel=0.85, dither=0.0)
    for key in ("hindleg_left_cannon", "hindleg_right_cannon"):
        x0, y0, w, h = rig.faces(key)["north"]
        d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=c["hair"])  # feathering above the hoof

    # Eagle half: feathered breast, neck and ruff; feathers fray back over the withers and barrel.
    eagle = ["breast", "neck", "ruff", "withers"]
    skin.skin(eagle, c["feather"], bevel=0.9, dither=0.0)
    for key in eagle:
        skin.feathers(key, ("top", "east", "west", "north", "south"), c["tip"], None, step=3)
    ramp(skin, "breast", ("north",), c["feather_lit"], c["feather"])
    skin.feathers("breast", ("north",), c["tip"], None, step=3)
    for name in ("east", "west", "top"):
        x0, y0, w, h = rig.faces("barrel")[name]
        cols = range(w - 1, max(-1, w - 5), -1) if name == "west" else range(min(w, 4))
        if name == "top":
            cols = range(w)
        for i, col in enumerate(cols):
            depth = 3 - i if name != "top" else 2 + (col * 7) % 3
            for row in range(max(0, depth)):
                if name == "top":
                    r = row
                    cx = col
                else:
                    r, cx = row + (col * 5) % 3, col
                if 0 <= r < h:
                    d.point((x0 + cx, y0 + r if name == "top" else y0 + r), fill=c["feather"] if (cx + r) % 2 else c["feather_dark"])

    # Head: feathered, a pale brow, a big orange eye set back behind the beak, a dark hooked beak.
    skin.skin("skull", c["feather"], top=c["feather_dark"], bevel=0.9, dither=0.0)
    skin.feathers("skull", ("top", "south"), c["tip"], None, step=3)
    for name, col in (("east", 1), ("west", -3)):
        x0, y0, w, h = rig.faces("skull")[name]
        ex = x0 + col if col >= 0 else x0 + w + col
        d.line([(ex - 1, y0 + 1), (ex + 2, y0 + 1)], fill=c["feather_lit"])      # brow
        d.rectangle([ex, y0 + 2, ex + 1, y0 + 3], fill=EYE)
        d.point((ex + (1 if name == "east" else 0), y0 + 2), fill=PUPIL)
        d.point((ex + (1 if name == "east" else 0), y0 + 3), fill=PUPIL)
    skin.skin(["beak", "beak_hook", "jaw"], BEAK, top=BEAK_LIT, bevel=0.85, dither=0.0)
    x0, y0, w, h = rig.faces("beak")["north"]
    d.point((x0, y0 + h - 1), fill=TALON)
    d.point((x0 + w - 1, y0 + h - 1), fill=TALON)
    for key in ("crest_c", "crest_l", "crest_r"):
        skin.skin(key, c["feather_dark"], bevel=0.0, dither=0.0)
        ramp(skin, key, sides, c["feather"], c["feather_dark"])

    # Wings: coverts in body feathers, secondaries shading to the flight-feather colour, primaries darkest.
    for side, sign in (("left", 1), ("right", -1)):
        root = f"wing_{side}"
        skin.skin(f"{root}_coverts", c["feather"], bottom=c["feather_lit"], bevel=0.9, dither=0.0)
        skin.feathers(f"{root}_coverts", ("top", "east", "west", "north", "south"), c["tip"], None, step=3)
        skin.skin(f"{root}_mid_coverts", c["feather"], bottom=c["feather_lit"], bevel=0.9, dither=0.0)
        skin.feathers(f"{root}_mid_coverts", ("top", "south"), c["tip"], None, step=3)
        sec = f"{root}_secondaries"
        skin.skin(sec, c["feather"], bevel=0, dither=0.0)
        f = rig.faces(sec)
        for name in ("top", "bottom"):
            x0, y0, w, h = f[name]
            for r in range(h):
                t = r / max(1, h - 1)
                rr = r if name == "top" else h - 1 - r
                col = mix(c["feather"], c["primaries"], t)
                if name == "bottom":
                    col = mix(col, c["feather_lit"], 0.35)
                d.line([(x0, y0 + rr), (x0 + w - 1, y0 + rr)], fill=col)
            for cc in range(0, w, 2):
                for r in range(h // 3, h):
                    rr = r if name == "top" else h - 1 - r
                    d.point((x0 + cc, y0 + rr), fill=shade(c["primaries"], 0.85))
            for cc in range(1, w, 2):
                d.point((x0 + cc, y0 + (h - 1 if name == "top" else 0)), fill=(0, 0, 0, 0))
        for name in ("east", "west", "north", "south"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=c["primaries"])
        for i in range(4):
            key = f"{root}_primary{i}"
            skin.skin(key, c["primaries"], bevel=0, dither=0.0)
            for name in ("top", "bottom"):
                x0, y0, w, h = rig.faces(key)[name]
                for cc in range(w):
                    along = cc if sign > 0 else w - 1 - cc
                    t = along / max(1, w - 1)
                    col = mix(c["feather_dark"], c["primaries"], min(1.0, t * 1.5))
                    if name == "bottom":
                        col = mix(col, c["feather_lit"], 0.3)
                    d.line([(x0 + cc, y0), (x0 + cc, y0 + h - 1)], fill=col)
                d.line([(x0, y0 + h // 2), (x0 + w - 1, y0 + h // 2)], fill=shade(c["primaries"], 0.8))  # quill
                tip = x0 + (w - 1 if sign > 0 else 0)
                d.point((tip, y0), fill=(0, 0, 0, 0))
                d.point((tip, y0 + h - 1), fill=(0, 0, 0, 0))

    # Eagle forelegs: feathered thighs, scaled golden shanks, dark talons.
    for side in ("left", "right"):
        skin.skin(f"foreleg_{side}", c["feather"], bevel=0.9, dither=0.0)
        skin.feathers(f"foreleg_{side}", sides, c["tip"], None, step=3, rows=2)
        skin.skin(f"foreleg_{side}_shank", SCALE, bevel=0.9, dither=0.0)
        for name in sides:
            x0, y0, w, h = rig.faces(f"foreleg_{side}_shank")[name]
            for yy in range(1, h, 3):
                d.line([(x0, y0 + yy), (x0 + w - 1, y0 + yy)], fill=SCALE_DARK)
        skin.skin(f"foreleg_{side}_talon", SCALE, bottom=SCALE_DARK, bevel=0.9, dither=0.0)
        x0, y0, w, h = rig.faces(f"foreleg_{side}_talon")["top"]
        for cc in range(0, w, 2):
            d.line([(x0 + cc, y0), (x0 + cc, y0 + h - 1)], fill=SCALE_DARK)
        skin.skin(f"foreleg_{side}_claws", TALON, top=shade(TALON, 1.5), bevel=0.0, dither=0.0)

    # Tail: long dark horse hair with strands.
    skin.skin(["tail_dock", "tail_hair"], c["hair"], bevel=0.9, dither=0.0)
    for name in ("top", "bottom", "east", "west"):
        x0, y0, w, h = rig.faces("tail_hair")[name]
        for cc in range(0, w, 2):
            d.line([(x0 + cc, y0), (x0 + cc, y0 + h - 1)], fill=shade(c["hair"], 1.3))
    return skin


# ----------------------------------------------------------------------------- animation

# The ground pose of each wing bone over the spread rest pose (left side; the right mirrors Y and Z), plus the
# fan scale that closes it. Found by rendering candidates with `pose_preview.py`: yawed back and rolled down over
# the flank, the wing lies from shoulder to croup, as a folded eagle wing does; a tighter fan collapsed it into a
# stub on the shoulders.
FOLD = {"wing_{s}": (0, 88, 96), "wing_{s}_mid": (0, 0, 0), "wing_{s}_tip": (0, 0, 0),
        **{f"wing_{{s}}_p{i}": (0, -fan, 0) for i, fan in enumerate(PRIMARY_FAN)}}
FOLD_SCALE = (0.75, 1.0, 0.7)
WINGS = tuple(FOLD)


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


def folded(length, extra_times=()):
    return wing_frames([(t, {}, 0.0) for t in (0, *extra_times, length)])


UP = {"wing_{s}": (0, 0, 44), "wing_{s}_mid": (0, 0, 14), "wing_{s}_tip": (0, 0, 10)}
DOWN = {"wing_{s}": (0, 0, -34), "wing_{s}_mid": (0, 0, -8), "wing_{s}_tip": (0, 0, -16)}
RECOVER = {"wing_{s}": (0, 12, 10), "wing_{s}_mid": (0, 0, 22), "wing_{s}_tip": (0, 0, 20)}
HALF = {"wing_{s}": (0, -20, 30), "wing_{s}_mid": (0, 0, 8)}

LEGS = ("foreleg_left", "foreleg_right", "hindleg_left", "hindleg_right")


def gait(length, amp, phases, *, shank=30, cannon=26):
    """Legs swing about the hip; the lower segment folds on the forward swing, like a real stride."""
    out = {}
    for leg, phase in phases.items():
        a = [(length * i / 4, amp * [0, 1, 0, -1][(i + int(phase * 4)) % 4]) for i in range(5)]
        out[leg] = rot(*[(t, (v, 0, 0)) for t, v in a])
        low = f"{leg}_shank" if leg.startswith("fore") else f"{leg}_cannon"
        bend = shank if leg.startswith("fore") else -cannon
        b = [(length * i / 4, bend * [0, 0, 1, 0][(i + int(phase * 4)) % 4]) for i in range(5)]
        out[low] = rot(*[(t, (v, 0, 0)) for t, v in b])
    return out


def anims():
    clips = {}

    L = 4.0
    idle = {
        "body": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 0.25, 0)), (L, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (L * 0.3, (-3, 8, 0)), (L * 0.65, (2, -8, 0)), (L, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (L * 0.3, (4, 12, 4)), (L * 0.65, (-4, -12, -2)), (L, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (L * 0.5, (-10, 0, 0)), (L * 0.56, (4, 0, 0)), (L * 0.62, (-4, 0, 0)),
                     (L, (0, 0, 0))),
        "tail": rot((0, (0, 8, 0)), (L / 2, (0, -8, 0)), (L, (0, 8, 0))),
        "tail_hair": rot((0, (0, 10, 0)), (L / 2, (0, -10, 0)), (L, (0, 10, 0))),
    }
    idle.update(folded(L))
    clips["idle"] = clip(L, idle)

    W = 1.0
    walk = gait(W, 18, {"foreleg_left": 0.0, "hindleg_right": 0.0, "foreleg_right": 0.5, "hindleg_left": 0.5})
    walk.update({
        "body": {"position": kf((0, (0, 0, 0)), (W / 4, (0, 0.3, 0)), (W / 2, (0, 0, 0)), (3 * W / 4, (0, 0.3, 0)),
                                (W, (0, 0, 0)))},
        "neck": rot((0, (4, 0, 0)), (W / 4, (-2, 0, 0)), (W / 2, (4, 0, 0)), (3 * W / 4, (-2, 0, 0)), (W, (4, 0, 0))),
        "tail": rot((0, (0, 6, 0)), (W / 2, (0, -6, 0)), (W, (0, 6, 0))),
    })
    walk.update(folded(W))
    clips["walk"] = clip(W, walk)

    R = 0.6
    run = gait(R, 36, {"foreleg_left": 0.0, "foreleg_right": 0.25, "hindleg_left": 0.5, "hindleg_right": 0.75},
               shank=40, cannon=34)
    run.update({
        "body": {"rotation": kf((0, (-4, 0, 0)), (R / 2, (4, 0, 0)), (R, (-4, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (R / 4, (0, 0.8, 0)), (R / 2, (0, 0, 0)), (3 * R / 4, (0, 0.8, 0)),
                                (R, (0, 0, 0)))},
        "neck": rot((0, (12, 0, 0)), (R / 2, (4, 0, 0)), (R, (12, 0, 0))),
        "tail": rot((0, (30, 0, 0)), (R / 2, (24, 0, 0)), (R, (30, 0, 0))),
    })
    run.update(wing_frames([(0, {"wing_{s}": (0, 0, 6)}, 0), (R / 2, {"wing_{s}": (0, 0, 12)}, 0.1),
                            (R, {"wing_{s}": (0, 0, 6)}, 0)]))
    clips["run"] = clip(R, run)

    tucked = {"foreleg_left": rot((0, (60, 0, 0)), (1, (60, 0, 0))),
              "foreleg_right": rot((0, (60, 0, 0)), (1, (60, 0, 0))),
              "foreleg_left_shank": rot((0, (-40, 0, 0)), (1, (-40, 0, 0))),
              "foreleg_right_shank": rot((0, (-40, 0, 0)), (1, (-40, 0, 0))),
              "hindleg_left": rot((0, (70, 0, 0)), (1, (70, 0, 0))),
              "hindleg_right": rot((0, (70, 0, 0)), (1, (70, 0, 0))),
              "tail": rot((0, (46, 0, 0)), (1, (46, 0, 0)))}

    def with_tuck(length, extra):
        out = {k: rot((0, v["rotation"]["0.0"]), (length, v["rotation"]["0.0"])) for k, v in tucked.items()}
        out.update(extra)
        return out

    F = 1.1
    fly = with_tuck(F, {
        "body": {"rotation": kf((0, (6, 0, 0)), (F, (6, 0, 0))),
                 "position": kf((0, (0, -0.8, 0)), (F * 0.45, (0, 1.0, 0)), (F, (0, -0.8, 0)))},
        "neck": rot((0, (-10, 0, 0)), (F * 0.45, (-14, 0, 0)), (F, (-10, 0, 0))),
        "tail_hair": rot((0, (4, 0, 0)), (F * 0.55, (-6, 0, 0)), (F, (4, 0, 0))),
    })
    fly.update(wing_frames([(0, UP, 1), (F * 0.45, DOWN, 1), (F * 0.75, RECOVER, 1), (F, UP, 1)]))
    clips["fly"] = clip(F, fly)

    G = 2.4
    glide = with_tuck(G, {
        "body": {"rotation": kf((0, (4, 0, -3)), (G / 2, (4, 0, 3)), (G, (4, 0, -3)))},
        "neck": rot((0, (-10, 0, 0)), (G, (-10, 0, 0))),
        "tail_hair": rot((0, (0, -5, 0)), (G / 2, (0, 5, 0)), (G, (0, -5, 0))),
    })
    glide.update(wing_frames([(0, {"wing_{s}": (0, 0, 6)}, 1), (G / 2, {"wing_{s}": (0, 0, 11)}, 1),
                              (G, {"wing_{s}": (0, 0, 6)}, 1)]))
    clips["glide"] = clip(G, glide)

    T = 0.9
    takeoff = {
        "body": {"position": kf((0, (0, 0, 0)), (0.25, (0, -2, 0)), (0.5, (0, 2.5, 0)), (T, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 0)), (0.25, (6, 0, 0)), (0.5, (-14, 0, 0)), (T, (4, 0, 0)))},
        "hindleg_left": rot((0, (0, 0, 0)), (0.25, (-18, 0, 0)), (0.5, (40, 0, 0)), (T, (60, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (0.25, (-18, 0, 0)), (0.5, (40, 0, 0)), (T, (60, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.3, (-30, 0, 0)), (T, (50, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.3, (-30, 0, 0)), (T, (50, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.25, (10, 0, 0)), (0.5, (-14, 0, 0)), (T, (-8, 0, 0))),
    }
    takeoff.update(wing_frames([(0, {}, 0), (0.25, UP, 1), (0.5, DOWN, 1), (0.7, RECOVER, 1), (T, UP, 1)]))
    clips["takeoff"] = clip(T, takeoff, loop=False)

    Ld = 0.8
    brake = {"wing_{s}": (0, -30, 38), "wing_{s}_mid": (0, 0, 12), "wing_{s}_tip": (0, 0, 10)}
    land = {
        "body": {"rotation": kf((0, (-16, 0, 0)), (0.3, (-18, 0, 0)), (0.5, (6, 0, 0)), (Ld, (0, 0, 0))),
                 "position": kf((0, (0, 0.5, 0)), (0.35, (0, 0, 0)), (0.5, (0, -1.5, 0)), (Ld, (0, 0, 0)))},
        "foreleg_left": rot((0, (-40, 0, 0)), (0.35, (-30, 0, 0)), (0.5, (0, 0, 0)), (Ld, (0, 0, 0))),
        "foreleg_right": rot((0, (-40, 0, 0)), (0.35, (-30, 0, 0)), (0.5, (0, 0, 0)), (Ld, (0, 0, 0))),
        "tail": rot((0, (40, 0, 0)), (0.35, (40, 0, 0)), (Ld, (0, 0, 0))),
    }
    land.update(wing_frames([(0, brake, 1), (0.35, brake, 0.8), (0.55, {}, 0.2), (Ld, {}, 0)]))
    clips["land"] = clip(Ld, land, loop=False)

    Fp = 0.6
    flap = {
        "body": {"position": kf((0, (0, 0, 0)), (0.15, (0, 0.6, 0.4)), (Fp, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.12, (-14, 0, 0)), (Fp, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (0.12, (-24, 0, 0)), (Fp, (0, 0, 0))),
    }
    flap.update(wing_frames([(0, {}, 0), (0.12, UP, 1), (0.3, DOWN, 1), (0.42, UP, 0.8), (Fp, {}, 0)]))
    clips["flap"] = clip(Fp, flap, loop=False)

    # bow: the courtesy. Front goes down, one foreleg reaches forward, the other folds, the head lowers,
    # held a beat, then it rises. Wings stay folded — it is a gesture, not a threat.
    B = 2.4
    down = (0.7, B - 0.7)
    bow = {
        "body": {"rotation": kf((0, (0, 0, 0)), (down[0], (14, 0, 0)), (down[1], (14, 0, 0)), (B, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (down[0], (0, -2.5, 0)), (down[1], (0, -2.5, 0)), (B, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (down[0], (42, 0, 0)), (down[1], (42, 0, 0)), (B, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (down[0], (22, 0, 0)), (down[1], (22, 0, 0)), (B, (0, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (down[0], (-38, 0, 0)), (down[1], (-38, 0, 0)), (B, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (down[0], (18, 0, 0)), (down[1], (18, 0, 0)), (B, (0, 0, 0))),
        "foreleg_right_shank": rot((0, (0, 0, 0)), (down[0], (-70, 0, 0)), (down[1], (-70, 0, 0)), (B, (0, 0, 0))),
        "hindleg_left": rot((0, (0, 0, 0)), (down[0], (-10, 0, 0)), (down[1], (-10, 0, 0)), (B, (0, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (down[0], (-10, 0, 0)), (down[1], (-10, 0, 0)), (B, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (down[0], (10, 0, 0)), (down[1], (10, 0, 0)), (B, (0, 0, 0))),
    }
    bow.update(folded(B))
    clips["bow"] = clip(B, bow, loop=False)

    # rear: the warning to someone who has not bowed. Up on the hind legs, talons out, wings half open.
    Rr = 1.2
    rear = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.35, (-34, 0, 0)), (0.8, (-34, 0, 0)), (Rr, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.35, (0, 3, 2)), (0.8, (0, 3, 2)), (Rr, (0, 0, 0)))},
        "hindleg_left": rot((0, (0, 0, 0)), (0.35, (30, 0, 0)), (0.8, (30, 0, 0)), (Rr, (0, 0, 0))),
        "hindleg_right": rot((0, (0, 0, 0)), (0.35, (30, 0, 0)), (0.8, (30, 0, 0)), (Rr, (0, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.35, (-50, 0, -8)), (0.55, (-30, 0, -8)), (0.8, (-50, 0, -8)),
                            (Rr, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.35, (-30, 0, 8)), (0.55, (-50, 0, 8)), (0.8, (-30, 0, 8)),
                             (Rr, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.35, (18, 0, 0)), (0.8, (18, 0, 0)), (Rr, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.3, (26, 0, 0)), (0.7, (26, 0, 0)), (0.9, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (0.3, (-26, 0, 0)), (0.9, (-26, 0, 0)), (Rr, (0, 0, 0))),
    }
    rear.update(wing_frames([(0, {}, 0), (0.35, HALF, 0.6), (0.8, HALF, 0.6), (Rr, {}, 0)]))
    clips["rear"] = clip(Rr, rear, loop=False)

    # attack: a half-rear and a raking talon strike, the beak snapping on the way down.
    A = 0.7
    attack = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.4, (8, 0, 0)), (A, (0, 0, 0)))},
        "foreleg_left": rot((0, (0, 0, 0)), (0.2, (-70, 0, 0)), (0.4, (20, 0, 0)), (A, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.25, (-70, 0, 0)), (0.45, (20, 0, 0)), (A, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.42, (24, 0, 0)), (A, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.3, (30, 0, 0)), (0.45, (0, 0, 0)), (A, (0, 0, 0))),
    }
    attack.update(wing_frames([(0, {}, 0), (0.2, HALF, 0.4), (A, {}, 0)]))
    clips["attack"] = clip(A, attack, loop=False)

    H = 0.45
    hit = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.1, (-6, 0, 0)), (H, (0, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.1, (0, 0.3, 1.0)), (H, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.12, (-16, 0, 0)), (H, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.1, (24, 0, 0)), (0.35, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (0.1, (-24, 0, 0)), (H, (0, 0, 0))),
        "tail": rot((0, (0, 0, 0)), (0.12, (20, 0, 0)), (H, (0, 0, 0))),
    }
    hit.update(wing_frames([(0, {}, 0), (0.12, HALF, 0.4), (H, {}, 0)]))
    clips["hit"] = clip(H, hit, loop=False)

    D = 1.2
    death = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.7, (6, 0, 0)), (D, (6, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.2, (0, 0.8, 0)), (0.7, (0, -4, 0)), (D, (0, -4, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.8, (40, 0, 0)), (D, (40, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.85, (24, 0, 0)), (D, (24, 0, 0))),
        "tail": rot((0, (0, 0, 0)), (0.8, (-10, 10, 0)), (D, (-10, 10, 0))),
    }
    for leg in LEGS:
        fore = leg.startswith("fore")
        death[leg] = rot((0, (0, 0, 0)), (0.6, (-36 if fore else 30, 0, 0)), (D, (-36 if fore else 30, 0, 0)))
        low = f"{leg}_shank" if fore else f"{leg}_cannon"
        death[low] = rot((0, (0, 0, 0)), (0.65, (60 if fore else -50, 0, 0)), (D, (60 if fore else -50, 0, 0)))
    slump = {"wing_{s}": (0, 30, -26)}
    death.update(wing_frames([(0, {}, 0), (0.2, HALF, 0.4), (0.9, slump, 0.8), (D, slump, 0.8)]))
    clips["death"] = dict(clip(D, death, loop=False), loop="hold_on_last_frame")

    # graze: it forages — neck down, pecking at the ground (canon diet: insects, birds, small animals).
    Gz = 3.0
    graze = {
        "neck": rot((0, (0, 0, 0)), (0.6, (50, 0, 0)), (2.4, (50, 0, 0)), (Gz, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.6, (20, 0, 0)), (1.0, (32, 4, 0)), (1.3, (18, 0, 0)), (1.7, (32, -4, 0)),
                    (2.0, (18, 0, 0)), (2.4, (20, 0, 0)), (Gz, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (1.0, (14, 0, 0)), (1.2, (0, 0, 0)), (1.7, (14, 0, 0)), (1.9, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (0.6, (4, 0, 0)), (2.4, (4, 0, 0)), (Gz, (0, 0, 0))),
    }
    graze.update(folded(Gz))
    clips["graze"] = clip(Gz, graze, loop=False)

    S = 2.6
    stretch = {
        "neck": rot((0, (0, 0, 0)), (0.8, (-14, 0, 0)), (1.8, (-14, 0, 0)), (S, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.8, (-12, 0, 0)), (1.8, (-12, 0, 0)), (S, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.9, (22, 0, 0)), (1.5, (22, 0, 0)), (1.9, (0, 0, 0))),
    }
    stretch.update(wing_frames([(0, {}, 0), (0.8, {"wing_{s}": (0, 0, 30)}, 1), (1.8, {"wing_{s}": (0, 0, 26)}, 1),
                                (S, {}, 0)]))
    clips["stretch"] = clip(S, stretch, loop=False)

    K = 1.0
    shake = {"body": rot(*[(K * i / 8, (0, 0, (5 if i % 2 else -5) * (1 - i / 8))) for i in range(8)], (K, (0, 0, 0))),
             "neck": rot(*[(K * i / 8, (0, (10 if i % 2 else -10) * (1 - i / 8), 0)) for i in range(8)],
                         (K, (0, 0, 0)))}
    shake.update(wing_frames([(0, {}, 0), (0.2, {"wing_{s}": (0, 0, 10)}, 0.1), (0.4, {}, 0),
                              (0.6, {"wing_{s}": (0, 0, 8)}, 0.1), (K, {}, 0)]))
    clips["shake"] = clip(K, shake, loop=False)
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    status = rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool=TOOL, hitbox=HITBOX, tex_h=tex_h,
                         force=a.force, shared_clips=False, extra_clips=EXTRA, colours=40)
    if status:
        return status
    # The other coats: same islands, same sheet size, `textures/entity/hippogriff/<coat>.png`.
    for coat in COATS:
        if coat == DEFAULT_COAT:
            continue
        img = posterise(paint(rig, tex_h, coat).img, 40)
        save(img, os.path.join(rigkit.TEX_DIR, CID, coat + ".png"), marker(TOOL))
    return 0


if __name__ == "__main__":
    sys.exit(main())
