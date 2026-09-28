#!/usr/bin/env python3
"""The Niffler (and the Baby Niffler, which renders the same rig at half scale): one rig, four coats.

Canon (Fantastic Beasts; the films): small, fluffy, black, with a long snout; a burrower that lives up to twenty feet
down; drawn to anything that glitters; a belly pouch that holds far more than it should. Curious, mischievous,
harmless.

The read, in order: the long tan snout against black fur; bright eyes with a glint; two small round ears; a round,
hunched little body with a fluffed back; short legs, the front ones broad digging paddles with pale claws; a short
fluffy tail. The fur is never flat black — a blue-grey sheen in short strokes keeps the silhouette readable.

`look` is a cube-less bone between the body and the head: GeckoLib's head tracking *sets* the rotation of the bone
it turns, so it gets one no clip keys, and the head stays free to sniff, dig and celebrate.

Coats: classic black (the default, `niffler.png`), dark brown, grey, and a rare pale one
(`textures/entity/niffler/<coat>.png`, same islands).

Clips — movement: idle, walk, run, hoard (loop, sitting over its treasure); one-shots: sniff, dig, pickup, celebrate,
hit, death.

Run from the repo root:  python tools/niffler_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, marker, posterise, save, shade  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "niffler"
TOOL = "niffler_model.py"
HITBOX = (0.4, 0.5)
TEX_W = 64

COATS = {
    "classic": dict(fur="#1D1C24", sheen="#30313D", dark="#0E0E13", belly="#2A2933"),
    "dark_brown": dict(fur="#2E211A", sheen="#433226", dark="#1A120D", belly="#3A2B22"),
    "grey": dict(fur="#4A4A52", sheen="#5E5F69", dark="#2F2F35", belly="#5A5A63"),
    "pale": dict(fur="#CFC8BC", sheen="#E2DCD0", dark="#A69E91", belly="#DAD3C7"),
}
DEFAULT_COAT = "classic"

SNOUT = hx("#8A6A55")
SNOUT_DARK = hx("#5E4536")
SNOUT_TIP = hx("#3E2C22")
CLAW = hx("#D8CDB6")
EYE = hx("#0A0A0C")
EYE_RIM = hx("#2A1D14")
GLINT = hx("#F2EEE2")
EAR_INNER = hx("#6E5448")
GOLD = hx("#E8B93A")


def build():
    rig = Rig(CID, TEX_W)
    # A round, hunched body: the main block plus a fluffed back riding a little higher at the rear.
    rig.bone("body", "root", (0, 5, 1), (-6, 0, 0))
    rig.cube("body", (-4, 1, -4), (8, 7, 10), key="body")
    rig.cube("body", (-3, 7.5, -2.5), (6, 2, 8), key="back", inflate=0.25)

    rig.bone("look", "body", (0, 6, -4))
    rig.bone("head", "look", (0, 6, -4), (6, 0, 0))
    rig.cube("head", (-3, 3.5, -9), (6, 5, 5), key="skull")
    rig.cube("head", (-2.5, 8, -8), (5, 1, 3), key="crown", inflate=0.15)

    def ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 2.5, 8.5, -6), (0, 0, sign * -15))
        rig.cube(f"ear_{side}", Rig.mirror((1.5, 8.5, -6.5), (2, 2, 1), sign), (2, 2, 1), key=f"ear_{side}")

    rig.pair(ear)

    # The snout: long, flat, tan — the Niffler's whole face.
    rig.bone("snout", "head", (0, 5, -9), (10, 0, 0))
    rig.cube("snout", (-1.5, 3.5, -15), (3, 2, 6), key="snout")
    rig.cube("snout", (-1, 3.2, -16), (2, 2, 1), key="snout_tip")

    def leg(side, sign, name, z, front):
        rig.bone(name, "body", (sign * 3, 3.5, z))
        rig.cube(name, Rig.mirror((2, 1, z - 1.5), (2, 3, 3), sign), (2, 3, 3), key=f"{name}_leg")
        # Front paws are broad digging paddles; the hind feet are small.
        size = (3, 1, 4) if front else (2, 1, 3)
        rig.cube(name, Rig.mirror((1.5, 0, z - size[2] + 1), size, sign), size, key=f"{name}_paw")

    rig.pair(lambda s, g: leg(s, g, f"front_{s}_leg", -2.5, True))
    rig.pair(lambda s, g: leg(s, g, f"back_{s}_leg", 4, False))

    rig.bone("tail", "body", (0, 4, 6), (-25, 0, 0))
    rig.cube("tail", (-1.5, 2.5, 5.5), (3, 2, 3), key="tail", inflate=0.2)
    return rig


def paint(rig, tex_h, coat=DEFAULT_COAT):
    c = {k: hx(v) for k, v in COATS[coat].items()}
    skin = Skin(rig, tex_h)
    d = skin.d
    fur = ["body", "back", "skull", "crown", "tail", "ear_left", "ear_right"] + rig.keys("front_", "back_")
    fur = [k for k in fur if not k.endswith("_paw")]
    skin.skin(fur, c["fur"], bottom=c["belly"], bevel=0.0, dither=0.0)

    # Fur: short sheen strokes, two texels long, staggered — fluffy at Minecraft resolution, never noise.
    for key in fur:
        f = rig.faces(key)
        for name in ("top", "east", "west", "north", "south"):
            x0, y0, w, h = f[name]
            for yy in range(0, h, 2):
                for xx in range((yy // 2) % 3, w, 3):
                    if rigkit._hash(x0 + xx, y0 + yy, 3) % 3 == 0:
                        d.line([(x0 + xx, y0 + yy), (x0 + xx, y0 + min(h - 1, yy + 1))], fill=c["sheen"])
            if name in ("east", "west", "north", "south") and h > 2:
                d.line([(x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)], fill=c["dark"])
    # The pouch: a seam across the belly with something shiny peeking out of it.
    x0, y0, w, h = rig.faces("body")["bottom"]
    d.line([(x0 + 1, y0 + h // 2), (x0 + w - 2, y0 + h // 2)], fill=c["dark"])
    d.point((x0 + w // 2, y0 + h // 2 + 1), fill=GOLD)

    # Ears: a warm inside on the front face.
    for side in ("left", "right"):
        x0, y0, w, h = rig.faces(f"ear_{side}")["north"]
        d.rectangle([x0, y0 + 1, x0 + w - 1, y0 + h - 1], fill=EAR_INNER)

    # Snout: tan, darker underneath, a dark nose tip with nostrils.
    skin.skin(["snout"], SNOUT, bottom=SNOUT_DARK, bevel=0.85, dither=0.0)
    skin.skin(["snout_tip"], SNOUT_TIP, bevel=0.0, dither=0.0)
    x0, y0, w, h = rig.faces("snout")["top"]
    for yy in range(1, h, 2):
        d.point((x0 + (yy % w), y0 + yy), fill=SNOUT_DARK)

    # Paws: dark, with pale claws along the front edge and the top.
    for key in rig.keys("front_left_leg_paw", "front_right_leg_paw", "back_left_leg_paw", "back_right_leg_paw"):
        skin.skin(key, c["dark"], bevel=0, dither=0)
        x0, y0, w, h = rig.faces(key)["north"]
        for xx in range(0, w, 1 if "front" in key else 2):
            d.point((x0 + xx, y0), fill=CLAW)
        x0, y0, w, h = rig.faces(key)["top"]
        d.line([(x0, y0), (x0 + w - 1, y0)], fill=CLAW)

    # Eyes: big and bright for a small face — a dark eye with a warm rim and a glint, set on the skull's sides near
    # the front, and visible from the front as well.
    for name, col in (("east", 1), ("west", -3)):
        x0, y0, w, h = rig.faces("skull")[name]
        ex = x0 + col if col >= 0 else x0 + w + col
        d.rectangle([ex, y0 + 1, ex + 1, y0 + 2], fill=EYE)
        d.point((ex, y0 + 1), fill=GLINT)
        d.point((ex + (1 if name == "east" else 0), y0 + 3), fill=EYE_RIM)
    x0, y0, w, h = rig.faces("skull")["north"]
    d.point((x0, y0 + 1), fill=EYE)
    d.point((x0 + w - 1, y0 + 1), fill=EYE)
    d.point((x0, y0), fill=GLINT)
    d.point((x0 + w - 1, y0), fill=GLINT)
    return skin


# ── animation ────────────────────────────────────────────────────────────────


def rot(*frames):
    return {"rotation": kf(*frames)}


LEGS = {"front_left_leg": 0.0, "front_right_leg": 0.5, "back_left_leg": 0.5, "back_right_leg": 0.0}


def anims():
    clips = {}
    # idle: breathes, twitches an ear, turns its head, and gives the air a sniff.
    I = 3.2
    clips["idle"] = clip(I, {
        "head": rot((0, (0, 0, 0)), (0.9, (4, 16, 0)), (1.8, (-2, -12, 0)), (I, (0, 0, 0))),
        "snout": rot((0, (0, 0, 0)), (0.3, (-6, 0, 0)), (0.42, (0, 0, 0)), (0.55, (-6, 0, 0)), (0.7, (0, 0, 0)),
                     (I, (0, 0, 0))),
        "ear_left": rot((0, (0, 0, 0)), (2.2, (0, 0, 0)), (2.3, (0, 0, -18)), (2.45, (0, 0, 0)), (I, (0, 0, 0))),
        "body": {"scale": kf((0, (1, 1, 1)), (I / 2, (1.03, 1.04, 1.0)), (I, (1, 1, 1)))},
        "tail": osc(I, 8, axis="y"),
    })

    # walk: a busy little waddle.
    W = 0.5
    walk = {
        "body": {"rotation": kf((0, (0, 0, 5)), (W / 2, (0, 0, -5)), (W, (0, 0, 5))),
                 "position": kf((0, (0, 0, 0)), (W / 4, (0, 0.4, 0)), (W / 2, (0, 0, 0)),
                                (3 * W / 4, (0, 0.4, 0)), (W, (0, 0, 0)))},
        "head": rot((0, (0, 0, -4)), (W / 2, (0, 0, 4)), (W, (0, 0, -4))),
        "tail": osc(W, 16, axis="y"),
    }
    walk.update(rigkit.gait(W, LEGS, amp=34))
    clips["walk"] = clip(W, walk)

    # run: a scurry — faster, lower, ears back, snout forward.
    R = 0.3
    run = {
        "body": {"rotation": kf((0, (6, 0, 4)), (R / 2, (4, 0, -4)), (R, (6, 0, 4))),
                 "position": kf((0, (0, 0, 0)), (R / 4, (0, 0.6, 0)), (R / 2, (0, 0, 0)),
                                (3 * R / 4, (0, 0.6, 0)), (R, (0, 0, 0)))},
        "head": rot((0, (-6, 0, 0)), (R, (-6, 0, 0))),
        "ear_left": rot((0, (-30, 0, 0)), (R, (-30, 0, 0))),
        "ear_right": rot((0, (-30, 0, 0)), (R, (-30, 0, 0))),
        "tail": osc(R, 22, axis="y"),
    }
    run.update(rigkit.gait(R, LEGS, amp=48))
    clips["run"] = clip(R, run)

    # hoard: sat back over its treasure, turning it over in its paws, peering in, content.
    H = 2.8
    clips["hoard"] = clip(H, {
        "body": rot((0, (-28, 0, 0)), (H, (-28, 0, 0))),
        "root": {"position": kf((0, (0, -0.5, 0)), (H, (0, -0.5, 0)))},
        "head": rot((0, (30, 0, 0)), (0.8, (34, 10, 0)), (1.6, (30, -8, 0)), (H, (30, 0, 0))),
        "front_left_leg": rot((0, (-50, 0, 10)), (0.7, (-60, 0, 14)), (1.4, (-50, 0, 10)), (H, (-50, 0, 10))),
        "front_right_leg": rot((0, (-50, 0, -10)), (0.7, (-40, 0, -6)), (1.4, (-50, 0, -10)), (H, (-50, 0, -10))),
        "back_left_leg": rot((0, (28, 0, 0)), (H, (28, 0, 0))),
        "back_right_leg": rot((0, (28, 0, 0)), (H, (28, 0, 0))),
        "tail": osc(H, 6, axis="y"),
    })

    # sniff: head low, snout working, a scan left and right.
    S = 1.2
    clips["sniff"] = clip(S, {
        "head": rot((0, (0, 0, 0)), (0.2, (18, 0, 0)), (0.5, (18, 20, 0)), (0.8, (18, -20, 0)), (S, (0, 0, 0))),
        "snout": rot((0, (0, 0, 0)), (0.25, (-8, 0, 0)), (0.35, (0, 0, 0)), (0.45, (-8, 0, 0)), (0.55, (0, 0, 0)),
                     (0.75, (-8, 0, 0)), (0.85, (0, 0, 0)), (S, (0, 0, 0))),
        "ear_left": rot((0, (0, 0, 0)), (0.3, (-15, 0, 0)), (S, (0, 0, 0))),
        "ear_right": rot((0, (0, 0, 0)), (0.3, (-15, 0, 0)), (S, (0, 0, 0))),
    }, loop=False)

    # dig: nose down, the front paddles going like mad, rear up.
    D = 1.0
    dig = {
        "body": rot((0, (0, 0, 0)), (0.15, (22, 0, 0)), (0.85, (22, 0, 0)), (D, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.15, (28, 0, 0)), (0.85, (28, 0, 0)), (D, (0, 0, 0))),
        "tail": osc(D, 18, axis="y"),
    }
    for side, phase in (("left", 0.0), ("right", 0.5)):
        track = [(0, (0, 0, 0))]
        for k in range(1, 8):
            t = 0.1 + 0.1 * k
            track.append((t, (-70 if (k + (phase > 0)) % 2 else -10, 0, 0)))
        track.append((D, (0, 0, 0)))
        dig[f"front_{side}_leg"] = rot(*track)
    clips["dig"] = clip(D, dig, loop=False)

    # pickup: a quick dip and snatch, the prize tucked into the pouch.
    P = 0.6
    clips["pickup"] = clip(P, {
        "head": rot((0, (0, 0, 0)), (0.15, (30, 0, 0)), (0.3, (-6, 0, 0)), (P, (0, 0, 0))),
        "front_left_leg": rot((0, (0, 0, 0)), (0.2, (-40, 0, 0)), (0.4, (20, 0, 0)), (P, (0, 0, 0))),
        "front_right_leg": rot((0, (0, 0, 0)), (0.2, (-40, 0, 0)), (0.4, (20, 0, 0)), (P, (0, 0, 0))),
    }, loop=False)

    # celebrate: something really good — a little hop, a wiggle, ears up.
    C = 0.9
    clips["celebrate"] = clip(C, {
        "root": {"position": kf((0, (0, 0, 0)), (0.15, (0, 2.5, 0)), (0.3, (0, 0, 0)), (0.45, (0, 1.5, 0)),
                                (0.6, (0, 0, 0)), (C, (0, 0, 0)))},
        "body": rot((0, (0, 0, 0)), (0.15, (-12, 0, 8)), (0.45, (-8, 0, -8)), (C, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.2, (-16, 0, 0)), (0.6, (-8, 12, 0)), (C, (0, 0, 0))),
        "ear_left": rot((0, (0, 0, 0)), (0.15, (0, 0, 20)), (C, (0, 0, 0))),
        "ear_right": rot((0, (0, 0, 0)), (0.15, (0, 0, -20)), (C, (0, 0, 0))),
        "tail": osc(C, 30, axis="y"),
    }, loop=False)

    # hit: a flinch.
    Hi = 0.35
    clips["hit"] = clip(Hi, {
        "body": rot((0, (0, 0, 0)), (0.08, (-10, 0, 6)), (Hi, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.08, (-14, 10, 0)), (Hi, (0, 0, 0))),
    }, loop=False)

    # death: tips onto its side, paws in.
    Dd = 1.0
    death = {"body": rot((0, (0, 0, 0)), (0.5, (0, 0, 80)), (Dd, (0, 0, 90))),
             "root": {"position": kf((0, (0, 0, 0)), (Dd, (0, -1, 0)))},
             "head": rot((0, (0, 0, 0)), (Dd, (10, 0, 0)))}
    for name in LEGS:
        death[name] = rot((0, (0, 0, 0)), (Dd, (-30, 0, 0)))
    clips["death"] = dict(clip(Dd, death, loop=False), loop="hold_on_last_frame")
    return clips


EXTRA = ("run", "hoard", "sniff", "dig", "pickup", "celebrate")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    rig = build()
    tex_h = rigkit.sheet_height(rig.pack())
    status = rigkit.emit(rig, paint(rig, tex_h), anims(), cid=CID, tool=TOOL, hitbox=HITBOX, tex_h=tex_h,
                         force=a.force, colours=28, definition=False, shared_clips=False, extra_clips=EXTRA)
    if status:
        return status
    # The other coats: same islands, same sheet, `textures/entity/niffler/<coat>.png`.
    for coat in COATS:
        if coat != DEFAULT_COAT:
            save(posterise(paint(rig, tex_h, coat).img, 28), os.path.join(rigkit.TEX_DIR, CID, coat + ".png"),
                 marker(TOOL))
    return 0


if __name__ == "__main__":
    sys.exit(main())
