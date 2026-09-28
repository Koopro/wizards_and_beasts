#!/usr/bin/env python3
"""Give a rig that is not built on `rigkit` the shared reaction and idle clips.

The hand-built rigs (basilisk, ghoul, hippogriff, obscurus, occamy, werewolf) have their own
generators, older than rigkit, whose outputs this pass leaves alone. They still deserve the beats
`GenericBeastEntity` asks for — `hit`, `death`, an attack if the creature fights, the idle actions
its profile names — so this reads the shipped geo and animation, lets `reactions.fill` add only
what is missing, converts just those new clips to GeckoLib's rotation sense
(`rigkit.to_game_sense`), writes the animation back and declares every one-shot clip on disk.

Idempotent: a second run finds nothing missing and changes nothing.

Run from the repo root:  python tools/add_reactions.py basilisk ghoul ...
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reactions  # noqa: E402
import rigkit  # noqa: E402


class _Clips:
    """Just enough of a Rig for `to_game_sense` to convert clips and nothing else."""
    bones = ()


def process(cid):
    geo_path = os.path.join(rigkit.GEO_DIR, cid + ".geo.json")
    anim_path = os.path.join(rigkit.ANIM_DIR, cid + ".animation.json")
    def_path = os.path.join(rigkit.DEF_DIR, cid + ".json")
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    anim = json.load(open(anim_path, encoding="utf-8"))
    prefix = f"animation.{cid}."
    clips = {k[len(prefix):]: v for k, v in anim["animations"].items() if k.startswith(prefix)}
    bones = [(b["name"], b.get("parent"), b.get("pivot", [0, 0, 0])) for b in geo["bones"]]
    before = set(clips)
    added = reactions.fill(bones, clips, idle_actions=rigkit._idle_actions(cid),
                           fights=rigkit._fights(cid))
    new = {k: clips[k] for k in added}
    rigkit.to_game_sense(_Clips(), new)
    for k, v in new.items():
        anim["animations"][prefix + k] = v
    with open(anim_path, "w", encoding="utf-8") as fh:
        json.dump(anim, fh, indent=2)
        fh.write("\n")
    declared = [k for k in clips if k not in rigkit.MOVEMENT
                and (k in rigkit.TRIGGERABLE or k in rigkit.IDLE_ACTIONS)]
    with open(def_path, encoding="utf-8") as fh:
        data = json.load(fh)
    data["clips"] = declared
    with open(def_path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")
    print(f"{cid}: added {', '.join(added) or 'nothing'}; declares {', '.join(declared)}"
          f"{'' if before <= set(clips) else ' (!)'}")


if __name__ == "__main__":
    for cid in sys.argv[1:]:
        process(cid)
