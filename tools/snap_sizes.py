#!/usr/bin/env python3
"""Close the UV holes on the hand-built rigs by snapping cube sizes to their painted islands.

The pre-rigkit generators (thestral, phoenix, ghoul, obscurus, hippogriff, augurey, ...) authored
fractional cube sizes. `boxuv` laid each island out from the size rounded **up**; GeckoLib samples
it from the size **floored** (`BakedModelFactory.buildQuad`), so every face of such a cube samples
a texel off and the top and bottom faces reach into the island's empty corner — transparent, a hole
on the model (`documentation/ENTITY_ART_AUDIT.md` §3.4).

The fix that keeps the skin: make the geometry match the island. Each fractional size becomes the
integer `boxuv` painted for, and the cube grows about its centre by the difference (under one
unit, i.e. under a sixteenth of a block on each side), so nothing moves and the rig keeps its
silhouette. The UVs, the texture and the animation are untouched.

Run from the repo root:  python tools/snap_sizes.py thestral phoenix ...
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import uv_check  # noqa: E402

GEO_DIR = "src/main/resources/assets/wizards_and_beasts/geckolib/models/entity"


def painted(size):
    """The island size `boxuv` painted for: `int(round(x + 0.4999))`, i.e. ceil for fractions."""
    return max(1, int(round(size + 0.4999)))


def snap(cid):
    path = os.path.join(GEO_DIR, cid + ".geo.json")
    with open(path, encoding="utf-8") as fh:
        geo = json.load(fh)
    changed = 0
    for bone in geo["minecraft:geometry"][0]["bones"]:
        for cube in bone.get("cubes", []):
            size = cube["size"]
            new = [painted(s) for s in size]
            if new == [round(s, 6) for s in size]:
                continue
            cube["origin"] = [round(o - (n - s) / 2.0, 3) for o, s, n in zip(cube["origin"], size, new)]
            cube["size"] = new
            changed += 1
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(geo, fh, indent=2)
        fh.write("\n")
    holes = uv_check.check(cid)
    print(f"{cid}: {changed} cubes snapped, {len(holes)} problems left")
    for line in holes[:6]:
        print("   ", line)


if __name__ == "__main__":
    for cid in sys.argv[1:]:
        snap(cid)
