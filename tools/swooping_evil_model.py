#!/usr/bin/env python3
"""The Swooping Evil: rig, skin and animation.

Canon (Fantastic Beasts): a large, spiny, butterfly-like creature that sleeps in a cocoon the size
of a hand and unfurls into a green-and-blue reptilian flier with a skull-like head; it sucks out
the brains of its prey, and its venom, diluted, removes bad memories. Hostile, FIRE_IMMUNE,
`life_leech`, `leap`.

Two pairs of wide wings with spines along the leading edge, iridescent green running to blue; a
long segmented body; and a head that is a bone-white skull with dark sockets. The spines and the
skull are what separate it from a giant butterfly, so both are geometry, and the wing veins glow.

Run from the repo root:  python tools/swooping_evil_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground  # noqa: E402
from insect import buzz, hover, wings  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "swooping_evil"
HITBOX = (0.95, 1.25)

GREEN = hx("#2FA878")
BLUE = hx("#2A5AC8")
DARK = hx("#123A30")
VEIN = hx("#8AF0D8")
BONE = hx("#E6E0CE")
SOCKET = hx("#16161A")
SPINE = hx("#1A2A24")


def build():
    rig = Rig(CID, 128)
    rig.bone("body", "root", (0, 10, 0))
    rig.cube("body", (-2.5, 8, -4), (5, 5, 8), key="thorax")
    seg = chain(rig, "abdomen", "body", start=(0, 10, 4), direction=1,
                segments=[(5, 4, 4), (4, 3, 3), (4, 2, 2)], rotations=[(-10, 0, 0), (-10, 0, 0), (-8, 0, 0)])
    rig.bone("head", "body", (0, 10, -4))
    rig.cube("head", (-2.5, 8, -9), (5, 5, 5), key="skull")
    rig.cube("head", (-1.5, 7, -9.5), (3, 2, 3), key="jaw")
    wings(rig, "body", x=2.5, y=12, z=-1, size=(14, 1, 10), rest=18, yaw=-10, name="wing")
    wings(rig, "body", x=2.5, y=11, z=4, size=(10, 1, 8), rest=8, yaw=25, name="wing_hind")
    for side, sign in (("left", 1), ("right", -1)):
        for i in range(4):
            rig.cube(f"wing_{side}", Rig.mirror((4 + 3 * i, 13, -6), (1, 2, 1), sign), (1, 2, 1),
                     key=f"spine_{side}{i}")
    ground(rig)
    return rig, seg


def paint(rig, tex_h, seg):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(["thorax"] + list(seg), GREEN, dither=0.2, dither_colour=DARK)
    skin.bands(list(seg), DARK, faces=("top", "east", "west"), step=2)
    blades = [k for k in rig.keys("wing_") if not k.startswith("spine")]
    skin.skin(blades, GREEN, dither=0.0)
    skin.ramp(blades, GREEN, BLUE, faces=("top", "bottom"))
    for key in blades:
        for face in ("top", "bottom"):
            x, y, w, h = skin.face(key, face)
            for i in range(1, 4):
                col = w * i // 4
                skin.d.line([(x + col, y), (x + col, y + h - 1)], fill=VEIN)
                skin.glow_rect((x + col, y, 1, h))
    skin.skin(rig.keys("spine_"), SPINE, dither=0.0)
    skin.skin(["skull", "jaw"], BONE, dither=0.08, dither_colour=hx("#B8B0A0"))
    x, y, w, h = skin.face("skull", "north")
    skin.rect((x + 1, y + 1, 1, 2), SOCKET)
    skin.rect((x + w - 2, y + 1, 1, 2), SOCKET)
    skin.hline(skin.face("jaw", "north"), 0, SOCKET)
    return skin


def anims(seg):
    b = 0.5
    idle = {**buzz(b, amp=14), **buzz(b, amp=10, name="wing_hind", phase_shift=True), "root": hover(b * 4, 1.2),
            **{s: {"rotation": kf((0.0, (0, 0, 0)), (b * 2, (-8, 0, 0)), (b * 4, (0, 0, 0)))} for s in seg}}
    fly = {**buzz(b * 0.8, amp=30), **buzz(b * 0.8, amp=22, name="wing_hind", phase_shift=True),
           "root": hover(b * 0.8, 1.0), "body": {"rotation": kf((0.0, (14, 0, 0)), (b * 0.8, (14, 0, 0)))}}
    lunge = {"body": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (-24, 0, 0)), (0.45, (34, 0, 0)), (0.8, (0, 0, 0)))},
             "root": {"position": kf((0.0, (0, 0, 0)), (0.2, (0, 2, 2)), (0.45, (0, -2, -5)), (0.8, (0, 0, 0)))},
             "wing_left": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (0, 0, 60)), (0.45, (0, 0, -20)), (0.8, (0, 0, 0)))},
             "wing_right": {"rotation": kf((0.0, (0, 0, 0)), (0.2, (0, 0, -60)), (0.45, (0, 0, 20)), (0.8, (0, 0, 0)))}}
    hiss = {"head": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (-20, 0, 0)), (1.0, (-16, 0, 0)), (1.3, (0, 0, 0)))},
            "wing_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 40)), (1.3, (0, 0, 0)))},
            "wing_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -40)), (1.3, (0, 0, 0)))},
            "wing_hind_left": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, 30)), (1.3, (0, 0, 0)))},
            "wing_hind_right": {"rotation": kf((0.0, (0, 0, 0)), (0.3, (0, 0, -30)), (1.3, (0, 0, 0)))}}
    return {"idle": clip(b * 4, idle), "fly": clip(b * 0.8, fly),
            "lunge": clip(0.8, lunge, loop=False), "hiss": clip(1.3, hiss, loop=False)}


if __name__ == "__main__":
    sys.exit(rigkit.run(CID, "swooping_evil_model.py", build, paint, anims, HITBOX))
