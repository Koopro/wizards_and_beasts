#!/usr/bin/env python3
"""The Knarl: rig, skin and animation.

Canon: indistinguishable from a hedgehog, except by its temper — leave food out and a hedgehog
eats it; a knarl assumes it is a trap and wrecks your garden. FEARFUL, with `thorns`.

So it *is* a hedgehog, and the job is to make a hedgehog out of boxes: a spined dome that hides
the legs almost entirely, a pointed snout poking out of the front, and a pale face. The spines
are a separate shell over the body so the `flinch` clip can curl the animal into a ball by
pulling the head and legs in under them.

Run from the repo root:  python tools/knarl_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import ground, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "knarl"
HITBOX = (0.65, 0.75)

SPINE = hx("#6E5A48")
SPINE_TIP = hx("#DCCCAE")
SPINE_DARK = hx("#46382C")
FACE = hx("#C9AE88")
FACE_DARK = hx("#9C8262")
NOSE = hx("#1E1814")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=3,
        chest=(6, 5, 3), barrel=(7, 6, 5), croup=(6, 5, 3), croup_drop=0,
        neck=None, neck_rake=0, head=(4, 3, 3), head_y=1, muzzle=(2, 2, 3),
        fore=[("", 1, 2, 2, None), ("paw", 2, 2, 2, None)],
        hind=[("", 1, 2, 2, None), ("paw", 2, 2, 2, None)],
        fore_x=2, hind_x=2)
    # A pointed snout: the black nose tip standing proud of the muzzle, and two small round ears,
    # so the face reads as an animal's at the front of the ball.
    rig.cube("head", (-0.5, 2, a.muzzle_front - 1), (1, 1, 1), key="nose")
    for side, sign in (("l", 1), ("r", -1)):
        rig.cube("head", Rig.mirror((1, a.head_top, a.head_back - 1.5), (1, 1, 1), sign), (1, 1, 1),
                 key=f"ear_{side}")

    # The spined shell: a core with a slab through it across and along, then two steps on top, so
    # the dome's outline rounds off from the front and from the side instead of standing square.
    rig.bone("spines", "body", (0, a.belly + 3, 0))
    rig.cube("spines", (-4.5, a.belly + 1, a.front + 0.5), (9, 5, 10), key="shell", inflate=0.25)
    rig.cube("spines", (-5.5, a.belly + 2, a.front + 1.5), (11, 3, 8), key="shell_wide")
    rig.cube("spines", (-3.5, a.belly + 2, a.front - 0.5), (7, 3, 12), key="shell_deep")
    rig.cube("spines", (-4, a.belly + 6, a.front + 1), (8, 2, 9), key="shell_top")
    rig.cube("spines", (-3, a.belly + 8, a.front + 2), (6, 1, 7), key="shell_crown")

    # Quills as geometry (silhouette pass 2026-09-27). Painted spines on a dome left it a brown
    # boulder at any distance; these stand off the dome, swept back and out, so the outline is a
    # fringe of points from every side. Rows: crown, upper flank, lower flank, rump.
    quills = []
    y = a.belly

    def quill(x, qy, z, length, pitch, yaw, roll):
        key = f"quill_{len(quills)}"
        rig.cube("spines", (x - 0.5, qy - 0.5, z), (1, 1, length), key=key,
                 rotation=(pitch, yaw, roll), pivot=(x, qy, z))
        quills.append(key)

    for x in (-2, 0, 2):
        for z in (a.front + 2, a.front + 5, a.front + 8):
            quill(x, y + 9, z, 4, 26, x * 6, 0)
    for sign in (1, -1):
        for z in (a.front + 2, a.front + 5, a.front + 8):
            quill(sign * 4, y + 7, z, 4, 20, sign * 30, sign * -28)
        for z in (a.front + 3, a.front + 7):
            quill(sign * 5.5, y + 4, z, 3, 6, sign * 40, sign * -30)
    for x in (-2.5, 0, 2.5):
        quill(x, y + 6, a.back - 1, 3, 4, x * 8, 0)
    a["quills"] = quills
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    skin.skin(["chest", "barrel", "croup", "head", "muzzle"] + rig.keys("foreleg", "hindleg", "ear_"),
              FACE, dither=0.05, dither_colour=FACE_DARK)
    shell = ["shell", "shell_wide", "shell_deep", "shell_top", "shell_crown"]
    skin.skin(shell, SPINE, dither=0.0, bevel=0)
    # Spines: dark roots and pale tips scattered, not a lattice. A regular every-other-texel
    # pattern reads as a checkerboard crate from any distance; a hedgehog is salt-and-pepper.
    for key in shell:
        for face in skin.ALL:
            r = skin.face(key, face)
            skin.dither(r, SPINE_DARK, 0.35, 11 + len(face))
            skin.dither(r, SPINE_TIP, 0.22, 29 + len(face))
    # The standing quills alternate dark and mid shafts, every one with a pale tip.
    for i, key in enumerate(a["quills"]):
        skin.skin(key, SPINE_DARK if i % 2 else SPINE, dither=0.0, bevel=0)
        x, y, w, h = skin.face(key, "south")
        skin.rect((x, y, w, h), SPINE_TIP)
    skin.skin("nose", NOSE, dither=0.0)
    skin.mark("muzzle", "north", 0, 0, NOSE, w=2)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, NOSE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.0, walk_len=0.5, stride=40.0, bob_amp=0.2)
    idle["head"] = {"rotation": kf((0.0, (0, -12, 0)), (0.8, (4, 10, 0)), (1.6, (-2, -4, 0)),
                                   (3.0, (0, -12, 0)))}
    # `flinch` is the curl: head and legs pull in, the shell rolls down over them, and it holds.
    flinch = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (60, 0, 0)), (1.2, (60, 0, 0)),
                                (1.5, (0, 0, 0)))},
        "spines": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (10, 0, 0)), (1.2, (10, 0, 0)),
                                  (1.5, (0, 0, 0))),
                   "scale": kf((0.0, (1, 1, 1)), (0.15, (1.08, 1.12, 1.0)), (1.2, (1.08, 1.12, 1.0)),
                               (1.5, (1, 1, 1)))},
        "root": {"position": kf((0.0, (0, 0, 0)), (0.15, (0, -1, 0)), (1.2, (0, -1, 0)),
                                (1.5, (0, 0, 0)))},
    }
    # `call` is a sniff: snout up, quick nods.
    call = {
        "head": {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-20, 0, 0)), (0.3, (-10, 0, 0)),
                                (0.45, (-22, 0, 0)), (0.6, (-10, 0, 0)), (0.9, (0, 0, 0)))},
    }
    return {"idle": clip(3.0, idle), "walk": clip(0.5, walk),
            "flinch": clip(1.5, flinch, loop=False), "call": clip(0.9, call, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "knarl_model.py", build, paint, anims, HITBOX))
