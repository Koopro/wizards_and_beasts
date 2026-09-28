#!/usr/bin/env python3
"""The Augurey: rig, skin and animation.

Replaces the last pre-rigkit placeholder rig (texture audit 2026-09-27): eight bones, wings and
tail modelled as zero-thickness planes, the beak sharing its UV island with the left wing, and a
32x32 sheet of 195 colours of noise — the one creature skin still outside the mod's pixel language.

Canon (*Fantastic Beasts*): the Irish phoenix — "a small and underfed-looking vulture,
greenish-black", mournful, whose cry was once thought to foretell death. So the read is a
hunched, scrawny bird with a bare grey head carried low on a forward neck, a hooked beak, a ruff
where the feathers start, and dark wings that end in separate flight feathers. The green is in the
feathers' sheen, not a green bird.

Bespoke (`AugureyEntity`, no creature definition): the entity asks for `idle` and `fly` only.

Run from the repo root:  python tools/augurey_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bespoke_reactions  # noqa: E402
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import bird, bird_loops, ground, wing_pair  # noqa: E402
from rigkit import Rig, Skin, clip, kf  # noqa: E402

CID = "augurey"
TOOL = "augurey_model.py"
HITBOX = (0.5, 0.7)

FEATHER = hx("#2C3A2C")
FEATHER_DARK = hx("#1C261E")
SHEEN = hx("#384C36")
RUFF = hx("#4A5A46")
HEAD = hx("#7C6E6A")
HEAD_DARK = hx("#5E524E")
BEAK = hx("#2E2A26")
BEAK_TIP = hx("#8E887A")
LEG = hx("#4C4642")
EYE = hx("#D8D0A0")


def build():
    rig = Rig(CID, 64)
    a = bird(rig, leg_h=4, body=(5, 5, 7), head=(3, 3, 4), beak=(2, 2, 2), neck=(3, 4, 3), neck_rake=38,
             tail=(3, 1, 6), tail_lift=-8, body_pitch=8,
             legs=[("", 2, 1, 1, None), ("shank", 1, 1, 1, None), ("foot", 1, 2, 3, None)], leg_x=1)
    rig.cube("beak", (-0.5, a.head_y - 1, a.head_front - 2), (1, 2, 1), key="beak_hook")
    rig.cube("neck", (-2, a.top - 3, a.front - 0.5), (4, 2, 4), key="ruff")
    a["wings"] = wing_pair(rig, "body", x=2.5, y=a.top - 1, z=a.front + 1,
                           root=(6, 5, 1), tip=(7, 4, 1), primaries=3, primary_step=2)
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h, grain="fleck")
    skin.skin(rig.keys(""), FEATHER, dither=0.10, dither_colour=SHEEN)
    wings = rig.keys("wing_")
    skin.skin(wings + ["tail"], FEATHER, dither=0.0)
    skin.ramp(wings + ["tail"], SHEEN, FEATHER_DARK, faces=("east", "west", "top"), steps=3)
    skin.skin("ruff", RUFF, dither=0.12, dither_colour=FEATHER)
    skin.skin("head", HEAD, dither=0.08, dither_colour=HEAD_DARK)
    skin.skin("beak", BEAK, dither=0.0)
    skin.skin("beak_hook", BEAK_TIP, dither=0.0)
    skin.skin(rig.keys("leg_"), LEG, dither=0.0)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)
    return skin


def anims(a):
    idle, _walk, fly = bird_loops(a, idle_len=4.0, fly_len=0.7, spread=78.0, amp=40.0)
    # Mournful: the head sinks and lifts slowly rather than darting like a songbird's.
    idle["neck"] = {"rotation": kf((0.0, (0, 0, 0)), (2.0, (10, 0, 0)), (4.0, (0, 0, 0)))}
    return {"idle": clip(4.0, idle), "fly": clip(0.7, fly)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    rig, a = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    clips = anims(a)
    clips.update(bespoke_reactions.clips_for(CID, [(b.name, b.parent, b.pivot) for b in rig.bones]))
    return rigkit.emit(rig, paint(rig, tex_h, a), clips, cid=CID, tool=TOOL, hitbox=HITBOX,
                       tex_h=tex_h, force=args.force, definition=False)


if __name__ == "__main__":
    sys.exit(main())
