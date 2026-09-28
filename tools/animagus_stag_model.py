#!/usr/bin/env python3
"""The Stag Animagus: rig, skin and animation for the player form.

Until 2026-09-28 the stag was the last form drawn by a hand-written `ModelPart` box model
(`PatronusStagModel`, shared with the Patronus): a flat part hierarchy, every box sampling
`texOffs(0, 0)` of a uniform brown noise sheet, no `setupAnim`, and so no walk cycle at all
(visual style report, B). This is the same animal rebuilt in the rig language every other drawn
form uses, and it keeps what made it a stag:

  - a slim barrel with a haunch cap, on long thin legs;
  - a neck raked well forward (the old part pose was 40 degrees) into a tapered head and muzzle;
  - ears swept back off the crown;
  - antlers of the same four pieces per side: a main beam rising up and back, a rear fork off
    its top, an outer tine, and an upper prong off that tine;
  - a short upturned tail;
  - the same brown coat, now with the markings a red deer actually has: a pale belly, a cream
    rump patch, darker legs and muzzle, pale antler tips, dark hooves.

It is authored at true proportions, about as tall as the form's 1.6-block box, so it renders at
roughly one texel per model unit like every creature rig; `SizeProfileRegistry` sets `modelScale`
to hitbox height / rig height (`FormRigScaleTest` holds it) and the old aspect stretch goes.

Clips are the ones `PlayerFormRig` binds for this form: `idle`, `walk`, `run` (sprinting),
`leap` (off the ground) and `hit` (damage flash). There is no creature definition, so `hit` comes
from `reactions.py` here rather than from `rigkit.emit`. There is no `death`: the transformation
ends at the moment of death (`AnimagusEvents.onDeath`), so a stag is never drawn dying.

Run from the repo root:  python tools/animagus_stag_model.py [--force]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reactions  # noqa: E402
import rigkit  # noqa: E402
from artgen_common import hx  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, gait, kf, osc  # noqa: E402

CID = "animagus_stag"
HITBOX = (0.9, 1.6)

# The old sheet's own brown, sampled from its most common texels.
COAT = hx("#71502F")
COAT_DARK = hx("#5A3D22")
COAT_LIT = hx("#8A6642")
BELLY = hx("#A88460")
RUMP = hx("#D9C9A6")
MUZZLE = hx("#4E3522")
NOSE = hx("#1E1611")
EYE = hx("#1A120C")
HOOF = hx("#2E2620")
ANTLER = hx("#CDBB94")
ANTLER_DARK = hx("#7E6848")
ANTLER_TIP = hx("#EEE4CC")


def build():
    rig = Rig(CID, 64)
    a = quadruped(
        rig, hip=12,
        chest=(6, 7, 5), barrel=(5, 6, 9), croup=(6, 7, 5), croup_drop=-2,
        neck=(3, 8, 4), neck_rake=35, head=(4, 4, 5), head_pitch=8, muzzle=(3, 2, 3),
        fore=[("", 6, 2, 3, None), ("cannon", 4, 2, 2, None), ("hoof", 2, 2, 3, None)],
        hind=[("", 6, 3, 4, (10, 0, 0)), ("cannon", 4, 2, 2, (-14, 0, 0)), ("hoof", 2, 2, 3, None)],
        fore_x=2, hind_x=2)

    # The haunch cap: the rump rounds off behind the croup instead of ending square.
    rig.cube("croup", (-2, a.belly + 1, a.back), (4, 5, 1), key="haunch")

    # Ears, swept back and out off the crown.
    def build_ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, a.head_top - 0.5, a.head_back - 1.5), (-24, 0, sign * -38))
        rig.cube(f"ear_{side}", Rig.mirror((1, a.head_top - 0.5, a.head_back - 2.5), (1, 3, 2), sign), (1, 3, 2))

    rig.pair(build_ear)

    # Antlers: the old model's four pieces a side, on a bone leaning back and out so the beam sweeps
    # the way a stag carries it.
    def build_antler(side, sign):
        top, back = a.head_top, a.head_back - 2
        bone = f"antler_{side}"
        rig.bone(bone, "head", (sign * 1.5, top, back), (-16, 0, sign * -16))
        rig.cube(bone, Rig.mirror((1, top, back - 0.5), (1, 6, 1), sign), (1, 6, 1), key=f"{bone}_beam")
        rig.cube(bone, Rig.mirror((1, top + 5, back + 0.5), (1, 1, 3), sign), (1, 1, 3), key=f"{bone}_fork")
        rig.cube(bone, Rig.mirror((2, top + 3, back - 0.5), (3, 1, 1), sign), (3, 1, 1), key=f"{bone}_tine")
        rig.cube(bone, Rig.mirror((4, top + 4, back - 0.5), (1, 2, 1), sign), (1, 2, 1), key=f"{bone}_prong")

    rig.pair(build_antler)

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 5, a.back + 0.5), direction=1,
                      segments=[(2, 2, 3)], rotations=[(30, 0, 0)])
    ground(rig)
    a["rig"] = rig
    return rig, a


def paint(rig, tex_h, a):
    skin = Skin(rig, tex_h)
    legs = rig.keys("foreleg", "hindleg")
    lower = [k for k in legs if "cannon" in k]
    hooves = [k for k in legs if "hoof" in k]
    uppers = [k for k in legs if k not in lower and k not in hooves]

    skin.skin(["chest", "barrel", "croup", "haunch"], COAT, top=COAT_LIT, bottom=BELLY, dither=0.08,
              dither_colour=COAT_DARK)
    # A stag's neck is darker and shaggier than its flank.
    skin.skin("neck", COAT_DARK, dither=0.12, dither_colour=COAT)
    skin.skin("head", COAT, top=COAT_LIT, dither=0.06, dither_colour=COAT_DARK)
    skin.skin("muzzle", MUZZLE, dither=0.0)
    skin.mark("muzzle", "north", 0, 0, NOSE, w=3)
    skin.skin(uppers, COAT, dither=0.08, dither_colour=COAT_DARK)
    skin.skin(lower, COAT_DARK, dither=0.06, dither_colour=MUZZLE)
    skin.skin(hooves, HOOF, dither=0.0)
    skin.skin(rig.keys("ear_"), COAT, dither=0.05, dither_colour=COAT_DARK)
    for side in ("left", "right"):
        skin.mark(f"ear_{side}", "north", 0, 1, BELLY, h=2)

    # The cream rump patch round the tail, which is what a deer shows you as it runs.
    skin.skin("haunch", RUMP, dither=0.05, dither_colour=BELLY)
    x, y, w, h = skin.face("croup", "south")
    skin.rect((x, y + 1, w, h - 2), RUMP)
    skin.skin(rig.keys("tail"), COAT_DARK, bottom=RUMP, dither=0.0)
    skin.d.rectangle([skin.face("tail", "south")[0], skin.face("tail", "south")[1],
                      skin.face("tail", "south")[0] + skin.face("tail", "south")[2] - 1,
                      skin.face("tail", "south")[1] + skin.face("tail", "south")[3] - 1], fill=RUMP)

    antlers = rig.keys("antler_")
    skin.skin(antlers, ANTLER, dither=0.08, dither_colour=ANTLER_DARK)
    # Dark burr at the base of each beam, pale polished points.
    skin.tip([k for k in antlers if k.endswith("_beam")], ANTLER_DARK, rows=1,
             faces=("north", "east", "west", "south"))
    for key in [k for k in antlers if k.endswith(("_fork", "_prong"))]:
        x, y, w, h = skin.face(key, "top")
        skin.rect((x, y, w, h), ANTLER_TIP)
    for face in ("east", "west"):
        skin.mark("head", face, 1, 1, EYE)
    return skin


def anims(a):
    idle, walk, legs = quadruped_loops(a, idle_len=3.0, walk_len=0.9, stride=24.0, bob_amp=0.3)
    walk.update(knee_tracks(a.rig, legs, 0.9, 16.0))
    # Ears never still: a flick each, out of step, and a tail flick between them.
    idle["ear_left"] = {"rotation": kf((0.0, (0, 0, 0)), (0.9, (0, 0, 0)), (1.0, (-18, 0, -10)),
                                       (1.2, (0, 0, 0)), (3.0, (0, 0, 0)))}
    idle["ear_right"] = {"rotation": kf((0.0, (0, 0, 0)), (2.1, (0, 0, 0)), (2.2, (-18, 0, 10)),
                                        (2.4, (0, 0, 0)), (3.0, (0, 0, 0)))}
    idle["tail"] = {"rotation": kf((0.0, (0, 0, 0)), (1.5, (0, 0, 0)), (1.6, (16, 0, 0)),
                                   (1.8, (0, 0, 0)), (3.0, (0, 0, 0)))}
    walk["tail"] = osc(0.9, 6.0, axis="z")

    # `run` is a bounding gallop for the sprint: fore pair together, hind pair together, half a beat
    # apart, and the body rocking over them with the neck countering it.
    run_len = 0.5
    gallop = {"foreleg_left": 0.0, "foreleg_right": 0.08, "hindleg_left": 0.5, "hindleg_right": 0.58}
    run = {
        **gait(run_len, gallop, amp=44.0),
        **knee_tracks(a.rig, gallop, run_len, 30.0),
        "body": osc(run_len, 5.0, axis="x"),
        "neck": osc(run_len, 6.0, axis="x", phase=0.5),
        "root": {"position": kf((0.0, (0, 0, 0)), (run_len / 4, (0, 1.2, 0)), (run_len / 2, (0, 0, 0)),
                                (3 * run_len / 4, (0, 1.2, 0)), (run_len, (0, 0, 0)))},
        "tail": {"rotation": kf((0.0, (20, 0, 0)), (run_len, (20, 0, 0)))},
        "ear_left": {"rotation": kf((0.0, (-20, 0, 0)), (run_len, (-20, 0, 0)))},
        "ear_right": {"rotation": kf((0.0, (-20, 0, 0)), (run_len, (-20, 0, 0)))},
    }

    # `leap` is held while off the ground: forelegs folded up under the chest, hind legs trailing,
    # nose lifted.
    leap_len = 1.0
    held = lambda v: {"rotation": kf((0.0, v), (leap_len, v))}  # noqa: E731
    leap = {
        "body": held((-8, 0, 0)),
        "neck": held((-8, 0, 0)),
        "foreleg_left": held((-50, 0, 0)),
        "foreleg_right": held((-44, 0, 0)),
        "foreleg_left_cannon": held((80, 0, 0)),
        "foreleg_right_cannon": held((74, 0, 0)),
        "hindleg_left": held((40, 0, 0)),
        "hindleg_right": held((34, 0, 0)),
        "hindleg_left_cannon": held((-10, 0, 0)),
        "hindleg_right_cannon": held((-10, 0, 0)),
        "tail": held((24, 0, 0)),
        "ear_left": held((-24, 0, 0)),
        "ear_right": held((-24, 0, 0)),
    }
    clips = {"idle": clip(3.0, idle), "walk": clip(0.9, walk), "run": clip(run_len, run),
             "leap": clip(leap_len, leap)}
    # The shared hit beat, read off this skeleton: the same `hit` every creature rig carries.
    clips["hit"] = reactions.hit(reactions.Skeleton([(b.name, b.parent, b.pivot) for b in a.rig.bones]))
    return clips


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    rig, a = build()
    rows = rig.pack()
    tex_h = rigkit.sheet_height(rows)
    return rigkit.emit(rig, paint(rig, tex_h, a), anims(a), cid=CID, tool="animagus_stag_model.py",
                       hitbox=HITBOX, tex_h=tex_h, force=args.force, definition=False,
                       extra_clips=("run", "leap"))


if __name__ == "__main__":
    sys.exit(main())
