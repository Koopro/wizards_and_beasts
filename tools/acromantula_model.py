#!/usr/bin/env python3
"""The Acromantula: rig, skin and every clip `AcromantulaEntity` plays.

Canon (Chamber of Secrets; Fantastic Beasts): a spider the size of a carthorse, legs spanning up to fifteen feet,
thick black hair, eight black eyes, pincers that click when it is excited or angry, venom in its fangs. It speaks.

The read, in order: the legs — eight of them, each hip → coxa → femur (thick, rising to a knee above the back) →
tibia (thinner, falling) → tarsus (thinnest) → a pointed claw on the floor, graded so no leg is a stick; the pinched
waist between a broad cephalothorax and a big hairy abdomen with spinnerets; the head with its eight eyes and two
heavy chelicerae ending in pale fangs. Black-brown, hair suggested by lit bristle ticks, the abdomen banded in
segments with one dull red-brown chevron. The eyes are black, each with a single cream highlight — the only glow.

Leg chain. Each leg is authored as one straight horizontal chain out from the hip and bent by a roll at each joint;
the yaw lives on a cube-less hip bone so every roll below stays in the leg's own plane (Bedrock composes Rz·Ry on one
bone). The tibia angle is solved so every foot lands on y=0.

Clips — movement: idle, walk, run, climb; mood loop: threaten; one-shots: bite, web, hiss, hit, death (+ the shared
groom / skitter idle actions).

Run from the repo root:  python tools/acromantula_model.py [--force]
"""

import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from rigkit import Rig, Skin, bob, clip, kf, osc  # noqa: E402

CID = "acromantula"
HITBOX = (2.2, 1.6)
TEX_W = 128

CHITIN = hx("#1E1917")
CHITIN_LIT = hx("#3A302A")
BRISTLE = hx("#4A3D33")
HAIR = hx("#0F0C0B")
ABDOMEN = hx("#2A201B")
BAND = hx("#16110F")
MARK = hx("#5A2A1E")          # dull red-brown chevron
JOINT = hx("#0A0808")
PINCER = hx("#2E231D")
FANG = hx("#D8CDB4")
FANG_TIP = hx("#8C8070")
EYE = hx("#08080A")
FACE = hx("#4A3E36")
EYE_SHINE = hx("#D9D2C0")

# Per pair: name, attach z, yaw (negative sweeps forward), femur, tibia, tarsus lengths. Front pair longest and swept
# forward, back pair swept behind; the middle pairs a little shorter.
PAIRS = [
    ("front", -11, -40, 16, 19, 12),
    ("mid_front", -6, -13, 14, 17, 11),
    ("mid_back", -1, 15, 14, 17, 11),
    ("back", 4, 42, 15, 19, 12),
]
# Segment thickness: femur thick, tibia thinner, tarsus thinnest — graded, never eight sticks.
THICK = {"coxa": 4, "femur": 4, "tibia": 3, "tarsus": 2}

HIP_Y = 15
HIP_X = 6
COXA = 4
COXA_LIFT = 12
FEMUR_LIFT = 28
TARSUS_BEND = -26
CLAW = 2


def _solve_tibia(femur, tibia, tarsus):
    """The absolute tibia angle that puts the foot on the floor (bisected over the monotonic range)."""
    knee = HIP_Y + COXA * math.sin(math.radians(COXA_LIFT)) + femur * math.sin(math.radians(COXA_LIFT + FEMUR_LIFT))

    def foot(angle):
        return knee + tibia * math.sin(math.radians(angle)) + tarsus * math.sin(math.radians(angle + TARSUS_BEND))

    lo, hi = -100.0, 0.0
    if not (foot(lo) < 0 < foot(hi)):
        raise ValueError(f"leg cannot reach the floor: knee at {knee:.1f}, reach {tibia + tarsus}")
    for _ in range(60):
        mid = (lo + hi) / 2
        if foot(mid) > 0:
            hi = mid
        else:
            lo = mid
    return (lo + hi) / 2


def leg_names():
    return [f"leg_{n}_{s}" for n, *_ in PAIRS for s in ("left", "right")]


def build():
    rig = Rig(CID, TEX_W)

    rig.bone("body", "root", (0, HIP_Y, 0))
    rig.cube("body", (-7, 10, -14), (14, 10, 16), key="thorax")
    rig.cube("body", (-4, 20, -12), (8, 1, 11), key="thorax_ridge")

    rig.bone("abdomen", "body", (0, 16, 2), (12, 0, 0))
    rig.cube("abdomen", (-3, 13, 1), (6, 5, 4), key="waist")
    rig.cube("abdomen", (-10, 9, 4), (20, 16, 22), key="abdomen")
    rig.cube("abdomen", (-2, 11, 26), (4, 3, 2), key="spinnerets")

    rig.bone("head", "body", (0, 15, -13), (6, 0, 0))
    rig.cube("head", (-6, 9, -22), (12, 9, 9), key="head")

    # Chelicerae: heavy, two bones so they click; the fangs are their own bones so a bite can flick them.
    def build_pincer(side, sign):
        rig.bone(f"mandible_{side}", "head", (sign * 2.5, 12, -21), (0, sign * 8, 0))
        rig.cube(f"mandible_{side}", Rig.mirror((1, 7, -28), (4, 5, 7), sign), (4, 5, 7), key=f"mandible_{side}")
        rig.bone(f"fang_{side}", f"mandible_{side}", (sign * 3, 8, -27))
        rig.cube(f"fang_{side}", Rig.mirror((2, 3, -29), (2, 5, 2), sign), (2, 5, 2), key=f"fang_{side}")

    rig.pair(build_pincer)

    def build_leg(name, z, yaw, femur, tibia, tarsus):
        tibia_abs = _solve_tibia(femur, tibia, tarsus + CLAW)   # the claw is the part that touches down

        def build(side, sign):
            prefix = f"leg_{name}_{side}"
            x = HIP_X
            rig.bone(f"{prefix}", "body", (sign * x, HIP_Y, z), (0, sign * yaw, 0))
            joints = [
                ("coxa", COXA, (0, 0, sign * COXA_LIFT)),
                ("femur", femur, (0, 0, sign * FEMUR_LIFT)),
                ("tibia", tibia, (0, 0, sign * (tibia_abs - COXA_LIFT - FEMUR_LIFT))),
                ("tarsus", tarsus, (0, 0, sign * TARSUS_BEND)),
            ]
            parent = prefix
            for suffix, length, rot in joints:
                bone = f"{prefix}_{suffix}"
                thick = THICK[suffix]
                rig.bone(bone, parent, (sign * x, HIP_Y, z), rot)
                size = (length, thick, thick)
                rig.cube(bone, Rig.mirror((x, HIP_Y - thick / 2, z - thick / 2), size, sign), size, key=bone)
                parent = bone
                x += length
            # The claw: a one-texel point past the tarsus, so each foot ends in a tip rather than a stump.
            claw = (CLAW, 1, 1)
            rig.cube(parent, Rig.mirror((x, HIP_Y - 0.5, z - 0.5), claw, sign), claw, key=f"{prefix}_claw")

        return build

    for name, z, yaw, femur, tibia, tarsus in PAIRS:
        rig.pair(build_leg(name, z, yaw, femur, tibia, tarsus))
    return rig


def paint(rig, tex_h):
    skin = Skin(rig, tex_h)
    d = skin.d
    legs = [k for k in rig.keys("leg_") if not k.endswith("_claw")]

    skin.skin(["thorax", "thorax_ridge", "waist", "head"], CHITIN, top=CHITIN_LIT, bevel=0.85, dither=0.0)
    skin.skin(["abdomen", "spinnerets"], ABDOMEN, bevel=0.88, dither=0.0)
    skin.skin(legs, CHITIN, bevel=0.85, dither=0.0)
    skin.skin(rig.keys("mandible_"), PINCER, bevel=0.85, dither=0.0)
    skin.skin(rig.keys("fang_"), FANG, bevel=0.9, dither=0.0)
    skin.tip(rig.keys("fang_"), FANG_TIP, rows=1)
    skin.skin([k for k in rig.keys("leg_") if k.endswith("_claw")], JOINT, bevel=0.0, dither=0.0)

    # Hair: short lit bristle ticks, offset each row — thick black hair at Minecraft resolution.
    def bristles(keys, faces, step=4):
        for key in keys:
            f = rig.faces(key)
            for name in faces:
                x0, y0, w, h = f[name]
                for yy in range(1, h, 3):
                    for xx in range(((yy // 3) * 2) % step, w, step):
                        if rigkit._hash(x0 + xx, y0 + yy, 5) % 3 == 0:
                            d.line([(x0 + xx, y0 + yy), (x0 + xx, y0 + min(h - 1, yy + 1))], fill=BRISTLE)

    bristles(["thorax", "head", "abdomen"], ("top", "east", "west", "north", "south"))
    bristles(legs, ("top", "north", "south", "east", "west"), step=3)

    # Legs: the joints dark — the end of every segment — so the chain reads as jointed.
    for key in legs:
        f = rig.faces(key)
        for name in ("north", "south", "top", "bottom"):
            x0, y0, w, h = f[name]
            d.line([(x0 + w - 1, y0), (x0 + w - 1, y0 + h - 1)], fill=JOINT)
            d.line([(x0, y0), (x0, y0 + h - 1)], fill=JOINT)

    # Abdomen: segment bands across the top and sides, and one dull red-brown chevron down the back.
    f = rig.faces("abdomen")
    for name in ("top", "east", "west"):
        x0, y0, w, h = f[name]
        if name == "top":
            for yy in range(3, h, 4):
                d.line([(x0 + 1, y0 + yy), (x0 + w - 2, y0 + yy)], fill=BAND)
        else:
            for xx in range(3, w, 4):
                d.line([(x0 + xx, y0 + 1), (x0 + xx, y0 + h - 2)], fill=BAND)
    x0, y0, w, h = f["top"]
    cx = x0 + w // 2
    for i, yy in enumerate(range(3, h - 3, 4)):
        span = max(1, 5 - i)
        d.line([(cx - span, y0 + yy + 1), (cx - 1, y0 + yy + 2)], fill=MARK)
        d.line([(cx + span - 1, y0 + yy + 1), (cx, y0 + yy + 2)], fill=MARK)

    # Eight black eyes on the head's front: a big pair low in the middle, a smaller pair above, two on each side.
    # Each is black with one cream highlight — the only thing that glows, and only just.
    x0, y0, w, h = rig.faces("head")["north"]
    # A face plate a shade lighter than the chitin, so black eyes read against it.
    d.rectangle([x0 + 1, y0, x0 + w - 2, y0 + h - 3], fill=FACE)
    cx = x0 + w // 2
    eyes = [(-3, 4, 2, 2), (1, 4, 2, 2), (-2, 1, 1, 1), (1, 1, 1, 1),
            (-6, 2, 1, 1), (-5, 5, 1, 1), (5, 2, 1, 1), (4, 5, 1, 1)]
    for dx, dy, ew, eh in eyes:
        ex, ey = cx + dx, y0 + dy
        d.rectangle([ex, ey, ex + ew - 1, ey + eh - 1], fill=EYE)
        if ew > 1:
            d.point((ex, ey), fill=EYE_SHINE)
            skin.glow_rect((ex, ey, 1, 1))
    return skin


# ── animation ────────────────────────────────────────────────────────────────


def rot(*frames):
    return {"rotation": kf(*frames)}


def phases():
    """Alternating tetrapod: front-left, mid-front-right, mid-back-left, back-right together, then the other four."""
    return {f"leg_{n}_{s}": (0.0 if (i % 2 == 0) == (s == "left") else 0.5)
            for i, (n, *_) in enumerate(PAIRS) for s in ("left", "right")}


def gait(length, hip, femur, tibia):
    ph = phases()
    out = {}
    for name in leg_names():
        out[name] = osc(length, hip, axis="y", phase=ph[name])
        out[f"{name}_femur"] = osc(length, femur, axis="z", phase=ph[name] + 0.15)
        out[f"{name}_tibia"] = osc(length, tibia, axis="z", phase=ph[name] + 0.3)
    return out


def side(name):
    return 1 if name.endswith("left") else -1


def anims():
    ph = phases()
    clips = {}

    # idle: it breathes; the pincers work; a leg shifts now and then; the head turns a little.
    I = 4.0
    idle = {
        "body": bob(I, 0.25, cycles=2),
        "abdomen": osc(I, 3.0, phase=0.2),
        "head": osc(I, 5.0, axis="y", phase=0.1),
        "mandible_left": osc(I, 6.0, axis="y", phase=0.0),
        "mandible_right": osc(I, 6.0, axis="y", phase=0.5),
    }
    for i, name in enumerate(leg_names()):
        idle[f"{name}_femur"] = osc(I, 2.0, axis="z", phase=ph[name] + 0.13 * i)
    clips["idle"] = clip(I, idle)

    # walk: the tetrapod gait, deliberate — a heavy animal carried under its knees.
    W = 1.0
    walk = gait(W, 14.0, 10.0, 8.0)
    walk.update({"root": bob(W, 0.35, cycles=2), "body": osc(W, 2.0, axis="y"), "abdomen": osc(W, 3.0, phase=0.25)})
    clips["walk"] = clip(W, walk)

    # run: the same gait, faster and wider, the body pressed low and the abdomen swinging.
    R = 0.5
    run = gait(R, 22.0, 16.0, 12.0)
    run.update({"root": {"position": kf((0, (0, -1.0, 0)), (R / 4, (0, -0.4, 0)), (R / 2, (0, -1.0, 0)),
                                        (R * 3 / 4, (0, -0.4, 0)), (R, (0, -1.0, 0)))},
                "body": osc(R, 3.5, axis="y"), "abdomen": osc(R, 6.0, phase=0.25),
                "head": rot((0, (4, 0, 0)), (R, (4, 0, 0)))})
    clips["run"] = clip(R, run)

    # climb: body tipped nose-up against the wall, the front legs reaching up over each other, the rest gripping.
    C = 0.8
    climb = gait(C, 8.0, 18.0, 10.0)
    climb.update({"body": rot((0, (-35, 0, 0)), (C, (-35, 0, 0))),
                  "root": {"position": kf((0, (0, 2, 0)), (C, (0, 2, 0)))}})
    clips["climb"] = clip(C, climb)

    # threaten: reared — the front of the body up, the front pair lifted high and swaying, pincers wide.
    T = 1.6
    threaten = {
        "root": {"position": kf((0, (0, 2.5, 0)), (T / 2, (0, 3.0, 0)), (T, (0, 2.5, 0)))},
        "body": rot((0, (-18, 0, 0)), (T / 2, (-21, 0, 0)), (T, (-18, 0, 0))),
        "abdomen": rot((0, (10, 0, 0)), (T, (10, 0, 0))),
        "mandible_left": rot((0, (0, -26, 0)), (T / 2, (0, -18, 0)), (T, (0, -26, 0))),
        "mandible_right": rot((0, (0, 26, 0)), (T / 2, (0, 18, 0)), (T, (0, 26, 0))),
    }
    for s in ("left", "right"):
        sg = 1 if s == "left" else -1
        threaten[f"leg_front_{s}_femur"] = rot((0, (0, 0, sg * 40)), (T / 2, (0, 0, sg * 52)), (T, (0, 0, sg * 40)))
        threaten[f"leg_front_{s}_tibia"] = rot((0, (0, 0, sg * 30)), (T / 2, (0, 0, sg * 20)), (T, (0, 0, sg * 30)))
        threaten[f"leg_front_{s}"] = rot((0, (0, sg * -10, 0)), (T / 2, (0, sg * 5, 0)), (T, (0, sg * -10, 0)))
        threaten[f"leg_mid_front_{s}_femur"] = rot((0, (0, 0, sg * 12)), (T, (0, 0, sg * 12)))
    clips["threaten"] = clip(T, threaten)

    # bite: a snap forward — body lunges, pincers flare then close, the fangs flick down.
    B = 0.6
    bite = {
        "root": {"position": kf((0, (0, 0, 0)), (0.12, (0, 1.5, 1.5)), (0.25, (0, 0.5, -3)), (B, (0, 0, 0)))},
        "body": rot((0, (0, 0, 0)), (0.12, (-16, 0, 0)), (0.25, (8, 0, 0)), (B, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.12, (-10, 0, 0)), (0.25, (10, 0, 0)), (B, (0, 0, 0))),
        "mandible_left": rot((0, (0, 0, 0)), (0.12, (0, -34, 0)), (0.25, (0, 12, 0)), (B, (0, 0, 0))),
        "mandible_right": rot((0, (0, 0, 0)), (0.12, (0, 34, 0)), (0.25, (0, -12, 0)), (B, (0, 0, 0))),
        "fang_left": rot((0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.3, (20, 0, 0)), (B, (0, 0, 0))),
        "fang_right": rot((0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.3, (20, 0, 0)), (B, (0, 0, 0))),
    }
    for s in ("left", "right"):
        sg = 1 if s == "left" else -1
        bite[f"leg_front_{s}_femur"] = rot((0, (0, 0, 0)), (0.12, (0, 0, sg * 30)), (0.3, (0, 0, sg * -6)),
                                            (B, (0, 0, 0)))
    clips["bite"] = clip(B, bite, loop=False)

    # web: the abdomen tips under and sweeps side to side as the silk comes, legs braced.
    Wb = 1.0
    clips["web"] = clip(Wb, {
        "abdomen": rot((0, (0, 0, 0)), (0.25, (-28, -12, 0)), (0.5, (-30, 12, 0)), (0.75, (-28, -8, 0)), (Wb, (0, 0, 0))),
        "body": rot((0, (0, 0, 0)), (0.25, (6, 0, 0)), (0.75, (6, 0, 0)), (Wb, (0, 0, 0))),
        "root": {"position": kf((0, (0, 0, 0)), (0.3, (0, -0.8, 0)), (0.8, (0, -0.8, 0)), (Wb, (0, 0, 0)))},
    }, loop=False)

    # hiss: the clicking — pincers chatter, body low and still. It announces itself before it moves.
    H = 0.9
    clips["hiss"] = clip(H, {
        "mandible_left": rot((0, (0, 0, 0)), (0.15, (0, -22, 0)), (0.3, (0, 6, 0)), (0.45, (0, -22, 0)),
                             (0.6, (0, 6, 0)), (H, (0, 0, 0))),
        "mandible_right": rot((0, (0, 0, 0)), (0.15, (0, 22, 0)), (0.3, (0, -6, 0)), (0.45, (0, 22, 0)),
                              (0.6, (0, -6, 0)), (H, (0, 0, 0))),
        "abdomen": rot((0, (0, 0, 0)), (0.3, (-10, 0, 0)), (H, (0, 0, 0))),
        "root": {"position": kf((0, (0, 0, 0)), (0.3, (0, -0.8, 0)), (H, (0, 0, 0)))},
    }, loop=False)

    # hit: a short recoil — body back, legs flinch in.
    Hi = 0.4
    hit = {"root": {"position": kf((0, (0, 0, 0)), (0.1, (0, 0.5, 2)), (Hi, (0, 0, 0)))},
           "body": rot((0, (0, 0, 0)), (0.1, (-8, 0, 0)), (Hi, (0, 0, 0)))}
    for name in leg_names():
        hit[f"{name}_femur"] = rot((0, (0, 0, 0)), (0.1, (0, 0, side(name) * 10)), (Hi, (0, 0, 0)))
    clips["hit"] = clip(Hi, hit, loop=False)

    # death: the body drops and every leg curls in underneath — the dead spider's clench.
    D = 1.6
    death = {"root": {"position": kf((0, (0, 0, 0)), (0.3, (0, 1, 0)), (1.0, (0, -8, 0)), (D, (0, -8, 0)))},
             "body": rot((0, (0, 0, 0)), (0.3, (-10, 0, 8)), (D, (4, 0, 12))),
             "abdomen": rot((0, (0, 0, 0)), (D, (-8, 0, 0)))}
    for i, name in enumerate(leg_names()):
        sg = side(name)
        t0 = 0.3 + 0.05 * i
        death[f"{name}_femur"] = rot((0, (0, 0, 0)), (t0, (0, 0, sg * 10)), (D, (0, 0, sg * 50)))
        death[f"{name}_tibia"] = rot((0, (0, 0, 0)), (t0, (0, 0, 0)), (D, (0, 0, sg * -70)))
        death[f"{name}_tarsus"] = rot((0, (0, 0, 0)), (D, (0, 0, sg * -40)))
    clips["death"] = dict(clip(D, death, loop=False), loop="hold_on_last_frame")
    return clips


EXTRA = ("run", "climb", "threaten")   # AcromantulaEntity's movement loops; `web` is declared (WebSnareGoal)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    return rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool="acromantula_model.py", hitbox=HITBOX,
                       tex_h=tex_h, force=a.force, colours=24, extra_clips=EXTRA, resize=True)


if __name__ == "__main__":
    sys.exit(main())
