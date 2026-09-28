#!/usr/bin/env python3
"""The Phoenix: rig, plumage, glowmask and every clip `PhoenixEntity` plays.

Replaces the hand-built rig of 2026-08 (fractional cube sizes, a 128x128 sheet at 11% use, only
`idle` and `fly`). Canon (Fantastic Beasts; Chamber of Secrets): a swan-sized scarlet bird with a
long golden tail; it bursts into flame and rises from its ashes. The user's direction adds a strong
dark beak and dark talons, an expressive eye and a feather crest.

Minecraft-first: a compact body, a neck with a clear step into the torso, a big-eyed head, a crest
of three plumes, wings in three segments (coverts, secondaries, a fan of four separate primaries) so
they fold against the flank and open wide, a tail of three feather chains (a long gold centre and two
shorter scarlet sides) with enough bones to trail, and bird legs with dark talons. An `ash` pile sits
under `root` and is hidden by `PhoenixRenderer` except during rebirth.

Wings are authored spread (legible geometry) and every ground clip folds them from one `FOLD`
constant, as `dragon_model.py` does: yaw back along the flank, roll down over it, close like a fan
with a scale on the root. Values are in rigkit's authoring sense; `emit` converts to GeckoLib's.

Clips (all bound in `PhoenixEntity`): idle, walk, fly, glide (movement); takeoff, land, flap, sing
(one-shots); burst, ashes, rise (rebirth).

Run from the repo root:  python tools/phoenix_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, mix, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "phoenix"
HITBOX = (0.7, 1.0)
TEX_W = 128

CRIMSON = hx("#9A1818")
SCARLET = hx("#C8261C")
RED_LIT = hx("#E24A2A")
ORANGE = hx("#EE7A1E")
GOLD = hx("#F4B42A")
GOLD_LIT = hx("#FFE27A")
DARK = hx("#2A1B17")
DARK_LIT = hx("#4A3226")
EYE = hx("#FFD24A")
PUPIL = hx("#120A08")
ASH = hx("#6A6560")
ASH_DARK = hx("#3E3A36")
ASH_LIT = hx("#9A958E")
EMBER = hx("#FF8A2A")

PRIMARY_FAN = (6, 20, 34, 48)   # rest yaw of each primary: the open fan

EXTRA = ("glide", "takeoff", "land", "flap", "sing", "burst", "ashes", "rise")


# ----------------------------------------------------------------------------- rig


def build():
    rig = Rig(CID, TEX_W)

    rig.bone("body", "root", (0, 9, 1))
    rig.cube("body", (-3.5, 5, -4), (7, 7, 11), key="torso")
    rig.cube("body", (-3, 5, -6), (6, 6, 3), key="breast")

    rig.bone("neck", "body", (0, 10, -4), (14, 0, 0))
    rig.cube("neck", (-2, 10, -6.5), (4, 6, 4))

    rig.bone("head", "neck", (0, 15.5, -4.5), (-14, 0, 0))
    rig.cube("head", (-2.5, 15, -8), (5, 5, 6), key="skull")
    rig.bone("beak", "head", (0, 17, -8))
    rig.cube("beak", (-1, 16, -11), (2, 2, 3), key="beak")
    rig.cube("beak", (-1, 15, -11), (2, 1, 1), key="beak_hook")
    rig.bone("jaw", "head", (0, 16, -8))
    rig.cube("jaw", (-1, 15, -10), (2, 1, 2), key="jaw")

    # Crest: three plumes sweeping back off the crown, the centre tallest.
    rig.bone("crest", "head", (0, 20, -4.5))
    rig.cube("crest", (-0.5, 19.5, -6), (1, 7, 2), key="plume_c", rotation=(-32, 0, 0), pivot=(0, 20, -5))
    rig.cube("crest", (0.5, 19.5, -5.5), (1, 5, 2), key="plume_l", rotation=(-42, 0, 18), pivot=(1, 20, -4.5))
    rig.cube("crest", (-1.5, 19.5, -5.5), (1, 5, 2), key="plume_r", rotation=(-42, 0, -18), pivot=(-1, 20, -4.5))

    # Wings, spread: coverts, secondaries, a fan of four primaries.
    def wing(side, sign):
        root = f"wing_{side}"
        rig.bone(root, "body", (sign * 3.5, 11.5, -2.5))
        rig.cube(root, Rig.mirror((3.5, 10.5, -3), (8, 2, 7), sign), (8, 2, 7), key=f"{root}_coverts")
        rig.bone(f"{root}_mid", root, (sign * 11.5, 11.5, -2.5))
        rig.cube(f"{root}_mid", Rig.mirror((11.5, 11, -2.5), (7, 1, 10), sign), (7, 1, 10),
                 key=f"{root}_secondaries")
        rig.cube(f"{root}_mid", Rig.mirror((11.5, 11, -3), (7, 2, 3), sign), (7, 2, 3), key=f"{root}_mid_coverts")
        rig.bone(f"{root}_tip", f"{root}_mid", (sign * 18.5, 11.5, -2))
        # Each primary is its own bone so the fan can close when the wing folds and open in
        # flight. As fixed cube rotations the fan stood on end over the back when folded.
        for i in range(4):
            length = 11 - i
            z = -1.5 + i * 2.2
            rig.bone(f"{root}_p{i}", f"{root}_tip", (sign * 18.5, 11.5, z), (0, sign * PRIMARY_FAN[i], 0))
            rig.cube(f"{root}_p{i}", Rig.mirror((18.5, 11, z - 1), (length, 1, 2), sign), (length, 1, 2),
                     key=f"{root}_primary{i}")

    rig.pair(wing)

    # Tail: a rump fan, a long gold centre chain and two scarlet side chains.
    rig.bone("tail", "body", (0, 8.5, 6.5), (-16, 0, 0))
    rig.cube("tail", (-2.5, 7, 6), (5, 3, 4), key="rump")
    chains = [("tail_c", 0, 0, [(3, 9), (3, 9), (4, 8)], [-4, 6, 10]),
              ("tail_l", 1.8, 16, [(2, 8), (2, 8)], [-2, 8]),
              ("tail_r", -1.8, -16, [(2, 8), (2, 8)], [-2, 8])]
    for name, x, yaw, segs, pitches in chains:
        parent, z = "tail", 9.5
        for i, ((w, length), pitch) in enumerate(zip(segs, pitches), 1):
            bone = f"{name}{i}"
            rig.bone(bone, parent, (x, 8.5, z), (pitch, yaw if i == 1 else 0, 0))
            rig.cube(bone, (x - w / 2.0, 8, z), (w, 1, length), key=bone)
            parent, z = bone, z + length

    # Legs: a feathered thigh, a bare dark shank, a foot of spread talons.
    def leg(side, sign):
        rigkit.limb(rig, f"leg_{side}", "body", x=1.8, z=1.5, sign=sign, joints=[
            ("", 6, 3, 2, 3, (6, 0, 0)),
            ("shank", 3, 3, 1, 1, (-6, 0, 0)),
            ("foot", 1, 1, 3, 4, None),
        ])

    rig.pair(leg)

    # The ash pile the renderer shows only during rebirth.
    rig.bone("ash", "root", (0, 0, 0))
    rig.cube("ash", (-5, 0, -5), (10, 2, 10), key="ash_low")
    rig.cube("ash", (-3, 2, -3), (6, 2, 6), key="ash_high")
    return rig


# ----------------------------------------------------------------------------- skin


def ramp(skin, key, faces, top, bottom, *, along="rows"):
    d = skin.d
    f = skin.rig.faces(key)
    for name in faces:
        x0, y0, w, h = f[name]
        n = h if along == "rows" else w
        for i in range(n):
            t = i / max(1, n - 1)
            c = mix(top, bottom, t)
            if along == "rows":
                d.line([(x0, y0 + i), (x0 + w - 1, y0 + i)], fill=c)
            else:
                d.line([(x0 + i, y0), (x0 + i, y0 + h - 1)], fill=c)


def paint(rig, tex_h):
    skin = Skin(rig, tex_h, grain="speckle")
    d = skin.d
    sides = ("east", "west", "north", "south")

    # Body: scarlet back, crimson feather tips, a gold-orange breast.
    skin.skin(["torso", "neck"], SCARLET, top=CRIMSON, bottom=ORANGE, bevel=0.9, dither=0.0)
    skin.feathers("torso", ("top", "east", "west", "south"), CRIMSON, RED_LIT)
    skin.feathers("neck", ("east", "west", "south"), CRIMSON, RED_LIT, step=2, rows=2)
    skin.skin("breast", ORANGE, bottom=GOLD, bevel=0.9, dither=0.0)
    ramp(skin, "breast", ("north", "east", "west"), RED_LIT, GOLD)
    skin.feathers("breast", ("north",), mix(ORANGE, CRIMSON, 0.5), GOLD_LIT)
    ramp(skin, "neck", ("north",), SCARLET, ORANGE)

    # Head: scarlet, a gold mask round a big eye, dark beak.
    skin.skin("skull", SCARLET, top=CRIMSON, bevel=0.9, dither=0.0)
    for name, col in (("east", 1), ("west", -3)):
        x0, y0, w, h = rig.faces("skull")[name]
        ex = x0 + col if col >= 0 else x0 + w + col
        d.rectangle([ex - 1, y0 + 1, ex + 2, y0 + 3], fill=GOLD)
        d.rectangle([ex, y0 + 2, ex + 1, y0 + 2], fill=EYE)
        d.point((ex + (1 if name == "east" else 0), y0 + 2), fill=PUPIL)
        d.line([(ex - 1, y0 + 1), (ex + 2, y0 + 1)], fill=CRIMSON)  # brow
    x0, y0, w, h = rig.faces("skull")["north"]
    d.rectangle([x0 + 1, y0 + 2, x0 + w - 2, y0 + h - 1], fill=GOLD)
    skin.skin(["beak", "beak_hook", "jaw"], DARK, top=DARK_LIT, bevel=0.85, dither=0.0)

    # Crest: red at the root, gold, a pale gold tip that glows.
    for key in ("plume_c", "plume_l", "plume_r"):
        skin.skin(key, GOLD, bevel=0, dither=0.0)
        ramp(skin, key, sides, GOLD_LIT, SCARLET)
        x0, y0, w, h = rig.faces(key)["top"]
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=GOLD_LIT)
        skin.glow(key, ("top",))
        for name in sides:
            x0, y0, w, h = rig.faces(key)[name]
            skin.glow_rect((x0, y0, w, 1))

    # Wings. Top faces carry the colour story (red coverts -> orange secondaries -> gold-tipped
    # primaries); undersides are paler gold. Feather separators run across the span.
    for side, sign in (("left", 1), ("right", -1)):
        root = f"wing_{side}"
        cov = f"{root}_coverts"
        skin.skin(cov, CRIMSON, bottom=ORANGE, bevel=0.9, dither=0.0)
        skin.feathers(cov, ("top", "east", "west", "north", "south"), shade(CRIMSON, 0.75), SCARLET)
        skin.skin(f"{root}_mid_coverts", SCARLET, bottom=GOLD, bevel=0.9, dither=0.0)
        skin.feathers(f"{root}_mid_coverts", ("top", "south"), CRIMSON, RED_LIT)
        sec = f"{root}_secondaries"
        skin.skin(sec, SCARLET, bottom=GOLD, bevel=0, dither=0.0)
        f = rig.faces(sec)
        for name, top_c, bot_c in (("top", SCARLET, ORANGE), ("bottom", GOLD, GOLD_LIT)):
            x0, y0, w, h = f[name]
            # Rows run leading edge -> trailing edge on the top face, reversed underneath.
            for r in range(h):
                t = r / max(1, h - 1)
                rr = r if name == "top" else h - 1 - r
                d.line([(x0, y0 + rr), (x0 + w - 1, y0 + rr)], fill=mix(top_c, bot_c, t))
            for c in range(0, w, 2):
                for r in range(h // 3, h):
                    rr = r if name == "top" else h - 1 - r
                    d.point((x0 + c, y0 + rr), fill=mix(CRIMSON, ORANGE, 0.3))
            # Trailing edge: each feather ends in a point.
            for c in range(w):
                if c % 2 == 1:
                    d.point((x0 + c, y0 + (h - 1 if name == "top" else 0)), fill=(0, 0, 0, 0))
        for name in ("east", "west", "north", "south"):
            x0, y0, w, h = f[name]
            d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=CRIMSON)
        for i in range(4):
            key = f"{root}_primary{i}"
            skin.skin(key, SCARLET, bevel=0, dither=0.0)
            for name in ("top", "bottom", "north", "south"):
                x0, y0, w, h = rig.faces(key)[name]
                for c in range(w):
                    along = c if sign > 0 else w - 1 - c   # 0 at the wrist
                    t = along / max(1, w - 1)
                    col = mix(SCARLET, ORANGE, min(1, t * 1.6)) if t < 0.6 else mix(ORANGE, GOLD, (t - 0.6) / 0.4)
                    if name == "bottom":
                        col = mix(col, GOLD_LIT, 0.35)
                    d.line([(x0 + c, y0), (x0 + c, y0 + h - 1)], fill=col)
                # Rounded tip.
                tip = x0 + (w - 1 if sign > 0 else 0)
                if h > 1:
                    d.point((tip, y0), fill=(0, 0, 0, 0))
            for name in ("east", "west"):
                x0, y0, w, h = rig.faces(key)[name]
                d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=CRIMSON)

    # Tail: gold centre streamers with a flame-red band near the tip; scarlet-to-orange sides.
    skin.skin("rump", SCARLET, top=CRIMSON, bottom=ORANGE, bevel=0.9, dither=0.0)
    skin.feathers("rump", ("top", "east", "west", "south"), CRIMSON, RED_LIT)
    centre = ["tail_c1", "tail_c2", "tail_c3"]
    for i, key in enumerate(centre):
        skin.skin(key, GOLD, bevel=0, dither=0.0)
        top, bottom = [(SCARLET, ORANGE), (ORANGE, GOLD), (GOLD, GOLD_LIT)][i]
        ramp(skin, key, ("top", "bottom"), top, bottom)
        for name in ("top", "bottom"):
            x0, y0, w, h = rig.faces(key)[name]
            d.line([(x0 + w // 2, y0), (x0 + w // 2, y0 + h - 1)], fill=mix(top, CRIMSON, 0.4))  # quill
    x0, y0, w, h = rig.faces("tail_c3")["top"]
    d.line([(x0, y0 + h - 3), (x0 + w - 1, y0 + h - 3)], fill=SCARLET)
    skin.glow("tail_c3", ("top", "bottom", "south"))
    for key in ("tail_l1", "tail_l2", "tail_r1", "tail_r2"):
        second = key.endswith("2")
        skin.skin(key, SCARLET, bevel=0, dither=0.0)
        ramp(skin, key, ("top", "bottom"), SCARLET if not second else ORANGE, ORANGE if not second else GOLD)
        if second:
            x0, y0, w, h = rig.faces(key)["top"]
            skin.glow_rect((x0, y0 + h - 2, w, 2))
    # Feather tips at the ends of the tail chains: pointed, not square.
    for key in ("tail_c3", "tail_l2", "tail_r2"):
        for name in ("top", "bottom"):
            x0, y0, w, h = rig.faces(key)[name]
            end = y0 + h - 1 if name == "top" else y0
            d.point((x0, end), fill=(0, 0, 0, 0))
            d.point((x0 + w - 1, end), fill=(0, 0, 0, 0))

    # Legs: feathered red thighs, dark shanks and talons.
    for side in ("left", "right"):
        skin.skin(f"leg_{side}", SCARLET, bottom=CRIMSON, bevel=0.9, dither=0.0)
        skin.skin([f"leg_{side}_shank", f"leg_{side}_foot"], DARK, top=DARK_LIT, bevel=0.9, dither=0.0)
        x0, y0, w, h = rig.faces(f"leg_{side}_foot")["top"]
        for c in range(0, w, 2):
            d.point((x0 + c, y0), fill=DARK_LIT)

    # Ash: grey, mottled, embers that glow.
    skin.skin(["ash_low", "ash_high"], ASH, top=ASH_LIT, bevel=0.9, dither=0.25, dither_colour=ASH_DARK)
    for key in ("ash_low", "ash_high"):
        for name in ("top", "east", "west", "north", "south"):
            x0, y0, w, h = rig.faces(key)[name]
            for yy in range(h):
                for xx in range(w):
                    if rigkit._hash(x0 + xx, y0 + yy, 29) % 100 < 9:
                        d.point((x0 + xx, y0 + yy), fill=EMBER)
                        skin.glow_rect((x0 + xx, y0 + yy, 1, 1))
    return skin


# ----------------------------------------------------------------------------- animation

# The ground pose of each wing bone as a delta over the spread rest pose (left side; the right
# mirrors Y and Z), plus the fan scale on the root. Tuned with `pose_preview.py`.
FOLD = {"wing_{s}": (0, 88, -96), "wing_{s}_mid": (0, 0, -8), "wing_{s}_tip": (0, 0, -6),
        **{f"wing_{{s}}_p{i}": (0, -fan, 0) for i, fan in enumerate(PRIMARY_FAN)}}
FOLD_SCALE = (0.46, 1.0, 0.7)
WINGS = tuple(FOLD)


def rot(*frames):
    return {"rotation": kf(*frames)}


def wing_frames(frames):
    """[(t, {pattern: delta}, openness)] -> rotation + fan-scale tracks for both wings.

    `openness` 0 is the fold, 1 the spread rest pose; the rotation is FOLD*(1-openness)+delta.
    """
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
        scale = [(t, tuple(f + (1 - f) * opn for f in FOLD_SCALE)) for t, _, opn in frames]
        out[f"wing_{side}"]["scale"] = kf(*scale)
    return out


def folded(*times):
    return wing_frames([(t, {}, 0.0) for t in times])


TAIL = ("tail", "tail_c1", "tail_c2", "tail_c3", "tail_l1", "tail_l2", "tail_r1", "tail_r2")


def tail_sway(length, amp, *, axis=1, phase=0.0):
    out = {}
    for i, bone in enumerate(TAIL):
        a = amp * (0.5 + 0.25 * i)
        v = [0, 0, 0]
        v2 = [0, 0, 0]
        v[axis], v2[axis] = a, -a
        out[bone] = rot((0, tuple(v)), (length / 2, tuple(v2)), (length, tuple(v)))
    return out


def anims():
    clips = {}

    L = 4.0
    idle = {
        "body": {"position": kf((0, (0, 0, 0)), (L / 2, (0, 0.25, 0)), (L, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (L * 0.3, (-4, 8, 0)), (L * 0.6, (2, -10, 0)), (L, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (L * 0.3, (4, 14, 0)), (L * 0.45, (4, 14, 6)), (L * 0.6, (-2, -16, 0)),
                    (L, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (L * 0.5, (-8, 0, 0)), (L * 0.55, (4, 0, 0)), (L * 0.6, (-4, 0, 0)),
                     (L, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (L, (0, 0, 0))),
        "leg_left": rot((0, (0, 0, 0)), (L, (0, 0, 0))),
        "leg_right": rot((0, (0, 0, 0)), (L, (0, 0, 0))),
    }
    idle.update(tail_sway(L, 3.0))
    idle.update(wing_frames([(0, {}, 0), (L * 0.7, {}, 0), (L * 0.75, {"wing_{s}": (0, 0, 6)}, 0.05),
                             (L * 0.8, {}, 0), (L, {}, 0)]))
    clips["idle"] = clip(L, idle)

    W = 0.7
    walk = {
        "body": {"position": kf((0, (0, 0, 0)), (W / 4, (0, 0.5, 0)), (W / 2, (0, 0, 0)), (3 * W / 4, (0, 0.5, 0)),
                                (W, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 3)), (W / 2, (0, 0, -3)), (W, (0, 0, 3)))},
        "neck": rot((0, (8, 0, 0)), (W / 4, (-4, 0, 0)), (W / 2, (8, 0, 0)), (3 * W / 4, (-4, 0, 0)), (W, (8, 0, 0))),
        "leg_left": rot((0, (-28, 0, 0)), (W / 2, (26, 0, 0)), (W, (-28, 0, 0))),
        "leg_right": rot((0, (26, 0, 0)), (W / 2, (-28, 0, 0)), (W, (26, 0, 0))),
        "leg_left_foot": rot((0, (20, 0, 0)), (W / 2, (-10, 0, 0)), (W, (20, 0, 0))),
        "leg_right_foot": rot((0, (-10, 0, 0)), (W / 2, (20, 0, 0)), (W, (-10, 0, 0))),
    }
    walk.update(tail_sway(W, 4.0, axis=1))
    walk.update(folded(0, W))
    clips["walk"] = clip(W, walk)

    # fly: one strong beat per cycle — wings high, driven down, the tip lagging; body lifts on
    # the downstroke, tail steadies it, legs tucked.
    F = 0.9
    up = {"wing_{s}": (0, 0, 42), "wing_{s}_mid": (0, 0, 14), "wing_{s}_tip": (0, 0, 10)}
    down = {"wing_{s}": (0, 0, -34), "wing_{s}_mid": (0, 0, -8), "wing_{s}_tip": (0, 0, -16)}
    mid_up = {"wing_{s}": (0, 12, 10), "wing_{s}_mid": (0, 0, 22), "wing_{s}_tip": (0, 0, 20)}
    fly = {
        "body": {"rotation": kf((0, (8, 0, 0)), (F, (8, 0, 0))),
                 "position": kf((0, (0, -0.6, 0)), (F * 0.45, (0, 0.9, 0)), (F, (0, -0.6, 0)))},
        "neck": rot((0, (10, 0, 0)), (F * 0.45, (6, 0, 0)), (F, (10, 0, 0))),
        "head": rot((0, (-6, 0, 0)), (F, (-6, 0, 0))),
        "leg_left": rot((0, (70, 0, 0)), (F, (70, 0, 0))),
        "leg_right": rot((0, (70, 0, 0)), (F, (70, 0, 0))),
        "tail": rot((0, (14, 0, 0)), (F * 0.45, (8, 0, 0)), (F, (14, 0, 0))),
        "tail_c1": rot((0, (4, 0, 0)), (F * 0.55, (-4, 0, 0)), (F, (4, 0, 0))),
        "tail_c2": rot((0, (3, 0, 0)), (F * 0.65, (-5, 0, 0)), (F, (3, 0, 0))),
    }
    fly.update(wing_frames([(0, up, 1), (F * 0.45, down, 1), (F * 0.75, mid_up, 1), (F, up, 1)]))
    clips["fly"] = clip(F, fly)

    # glide: wings held out with a slight dihedral, breathing with the air.
    G = 2.4
    hold = {"wing_{s}": (0, 0, 6), "wing_{s}_tip": (0, 0, 6)}
    lift = {"wing_{s}": (0, 0, 10), "wing_{s}_tip": (0, 0, 12)}
    glide = {
        "body": {"rotation": kf((0, (4, 0, -3)), (G / 2, (4, 0, 3)), (G, (4, 0, -3))),
                 "position": kf((0, (0, 0, 0)), (G / 2, (0, 0.4, 0)), (G, (0, 0, 0)))},
        "leg_left": rot((0, (70, 0, 0)), (G, (70, 0, 0))),
        "leg_right": rot((0, (70, 0, 0)), (G, (70, 0, 0))),
        "tail": rot((0, (12, 0, 0)), (G, (12, 0, 0))),
    }
    glide.update(tail_sway(G, 2.0, axis=1))
    glide["tail"] = rot((0, (12, -3, 0)), (G / 2, (12, 3, 0)), (G, (12, -3, 0)))
    glide.update(wing_frames([(0, hold, 1), (G / 2, lift, 1), (G, hold, 1)]))
    clips["glide"] = clip(G, glide)

    # takeoff: crouch, wings flung up, one powerful downbeat, spring.
    T = 0.8
    takeoff = {
        "body": {"position": kf((0, (0, 0, 0)), (0.2, (0, -1.5, 0)), (0.45, (0, 2.0, 0)), (T, (0, 0, 0))),
                 "rotation": kf((0, (0, 0, 0)), (0.2, (-10, 0, 0)), (0.45, (12, 0, 0)), (T, (6, 0, 0)))},
        "leg_left": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.45, (40, 0, 0)), (T, (60, 0, 0))),
        "leg_right": rot((0, (0, 0, 0)), (0.2, (-20, 0, 0)), (0.45, (40, 0, 0)), (T, (60, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.45, (10, 0, 0)), (T, (8, 0, 0))),
    }
    takeoff.update(wing_frames([(0, {}, 0), (0.2, up, 1), (0.45, down, 1), (0.65, mid_up, 1), (T, up, 1)]))
    clips["takeoff"] = clip(T, takeoff, loop=False)

    # land: wings flared forward as brakes, feet thrust out, a settle, fold.
    Ld = 0.7
    brake = {"wing_{s}": (0, -30, 36), "wing_{s}_mid": (0, 0, 12), "wing_{s}_tip": (0, 0, 10)}
    land = {
        "body": {"rotation": kf((0, (-16, 0, 0)), (0.25, (-18, 0, 0)), (0.45, (6, 0, 0)), (Ld, (0, 0, 0))),
                 "position": kf((0, (0, 0.5, 0)), (0.3, (0, 0, 0)), (0.45, (0, -1, 0)), (Ld, (0, 0, 0)))},
        "leg_left": rot((0, (-40, 0, 0)), (0.3, (-30, 0, 0)), (0.45, (0, 0, 0)), (Ld, (0, 0, 0))),
        "leg_right": rot((0, (-40, 0, 0)), (0.3, (-30, 0, 0)), (0.45, (0, 0, 0)), (Ld, (0, 0, 0))),
        "tail": rot((0, (24, 0, 0)), (0.3, (26, 0, 0)), (Ld, (0, 0, 0))),
    }
    land.update(wing_frames([(0, brake, 1), (0.3, brake, 0.8), (0.5, {}, 0.2), (Ld, {}, 0)]))
    clips["land"] = clip(Ld, land, loop=False)

    # flap: the reusable powerful beat — startle when hurt, beating in place.
    Fp = 0.6
    flap = {
        "body": {"position": kf((0, (0, 0, 0)), (0.15, (0, 0.8, 0.6)), (Fp, (0, 0, 0)))},
        "neck": rot((0, (0, 0, 0)), (0.12, (-16, 0, 0)), (Fp, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.12, (-10, 0, 0)), (Fp, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.1, (24, 0, 0)), (0.3, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (0.12, (-22, 0, 0)), (Fp, (0, 0, 0))),
    }
    flap.update(wing_frames([(0, {}, 0), (0.12, up, 1), (0.3, down, 1), (0.42, up, 0.8), (Fp, {}, 0)]))
    clips["flap"] = clip(Fp, flap, loop=False)

    # sing: head raised, throat working, wings lifted a little from the body, crest up.
    S = 3.0
    half = {"wing_{s}": (0, -10, 14)}
    sing = {
        "neck": rot((0, (0, 0, 0)), (0.4, (-18, 0, 0)), (S - 0.4, (-18, 0, 0)), (S, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.4, (-22, 0, 0)), (1.2, (-26, 8, 0)), (2.0, (-22, -8, 0)), (S - 0.4, (-22, 0, 0)),
                    (S, (0, 0, 0))),
        "jaw": rot(*[(0.4 + i * 0.25, (14 if i % 2 == 0 else 4, 0, 0)) for i in range(10)], (S, (0, 0, 0))),
        "crest": rot((0, (0, 0, 0)), (0.4, (-18, 0, 0)), (S - 0.4, (-18, 0, 0)), (S, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (0.4, (-6, 0, 0)), (S - 0.4, (-6, 0, 0)), (S, (0, 0, 0))),
    }
    sing.update(wing_frames([(0, {}, 0), (0.5, half, 0.15), (S - 0.5, half, 0.15), (S, {}, 0)]))
    clips["sing"] = clip(S, sing, loop=False)

    # burst: rears with wings flung open, then the body is consumed and the ash rises.
    B = 1.0
    gone = (0.02, 0.02, 0.02)
    burst = {
        "body": {"rotation": kf((0, (0, 0, 0)), (0.3, (-24, 0, 0)), (B, (-24, 0, 0))),
                 "position": kf((0, (0, 0, 0)), (0.3, (0, 2, 0)), (0.7, (0, 3, 0)), (B, (0, 3, 0))),
                 "scale": kf((0, (1, 1, 1)), (0.4, (1.1, 1.1, 1.1)), (0.75, gone), (B, gone))},
        "neck": rot((0, (0, 0, 0)), (0.3, (-26, 0, 0)), (B, (-26, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.3, (30, 0, 0)), (B, (30, 0, 0))),
        "ash": {"scale": kf((0, gone), (0.6, gone), (B, (1, 1, 1)))},
    }
    burst.update(wing_frames([(0, {}, 0), (0.3, up, 1), (B, up, 1)]))
    clips["burst"] = dict(clip(B, burst, loop=False), loop="hold_on_last_frame")

    # ashes: a smouldering pile, the embers breathing.
    A = 2.0
    ashes = {
        "body": {"scale": kf((0, gone), (A, gone))},
        "ash": {"scale": kf((0, (1, 1, 1)), (A / 2, (1.04, 1.12, 1.04)), (A, (1, 1, 1)))},
    }
    clips["ashes"] = clip(A, ashes)

    # rise: the pile draws in and the bird unfolds out of it, wings opening then settling.
    R = 1.5
    rise = {
        "ash": {"scale": kf((0, (1, 1, 1)), (0.5, (0.8, 1.4, 0.8)), (0.9, gone), (R, gone))},
        "body": {"scale": kf((0, gone), (0.4, gone), (0.9, (1, 1, 1)), (R, (1, 1, 1))),
                 "position": kf((0, (0, 0, 0)), (0.9, (0, 1.5, 0)), (R, (0, 0, 0)))},
        "neck": rot((0, (-20, 0, 0)), (1.0, (-20, 0, 0)), (R, (0, 0, 0))),
    }
    rise.update(wing_frames([(0, up, 1), (0.9, up, 1), (1.2, mid_up, 0.6), (R, {}, 0)]))
    clips["rise"] = clip(R, rise, loop=False)
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool="phoenix_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, definition=False, shared_clips=False, extra_clips=EXTRA,
                       colours=40)


if __name__ == "__main__":
    sys.exit(main())
