#!/usr/bin/env python3
"""Own skins for the Animagus forms that borrow a vanilla model: the hawk and the beetle.

`FormModelRenderer` draws the hawk with vanilla's `ParrotModel` and the beetle with the
`SilverfishModel` — the right call, a real animated vanilla model beats a placeholder rig — but it
also borrowed their skins, so a hawk Animagus was a red-and-blue macaw and a beetle a grey
silverfish (visual consistency audit, H8). These keep the vanilla UV layout (so the models map
unchanged) and replace the colour: every texel keeps its value and takes the matching step of a
new ramp, then a few features are painted in.

Vanilla textures are read from the cached vanilla client jar (`artgen_common.client_jar`), which
`./gradlew build` downloads; nothing vanilla is copied into the repo other than the layout.

Run from the repo root:  python tools/animagus_skins.py [--force]
"""

import argparse
import io
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar, hx, is_regenerable, marker, posterise, save  # noqa: E402

OUT = "src/main/resources/assets/wizards_and_beasts/textures/entity/form"
MARK = marker("animagus_skins.py")

# Dark to light. Hawk: a red-tailed buzzard's browns; beetle: a glossy stag-beetle black with a
# blue-green sheen at the top of the ramp.
# The third value is a gamma on the source value: the grey parrot is mostly pale, and a pale
# hawk reads as a dove, so its values are pushed down into the browns.
RAMPS = {
    "animagus_hawk": ("minecraft/textures/entity/parrot/parrot_grey.png",
                      ["#2A1C12", "#4E3420", "#7A5634", "#A67C4E", "#D2B489", "#EFE2C4"], 2.4),
    "animagus_beetle": ("minecraft/textures/entity/silverfish.png",
                        ["#0E0C12", "#1C1A26", "#2A2C3C", "#34465A", "#4C6E78", "#7FA6A0"], 1.0),
}


def vanilla(path):
    return Image.open(io.BytesIO(client_jar().read("assets/" + path))).convert("RGBA")


def remap(img, ramp, gamma):
    ramp = [hx(c)[:3] for c in ramp]
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = ((0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0) ** gamma
            step = min(len(ramp) - 1, int(lum * len(ramp)))
            px[x, y] = ramp[step] + (a,)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    for name, (src, ramp, gamma) in RAMPS.items():
        path = os.path.join(OUT, name + ".png")
        if not args.force and os.path.exists(path) and not is_regenerable(path, MARK):
            print(f"{path} is not this tool's; skipped")
            continue
        save(posterise(remap(vanilla(src), ramp, gamma), 8), path, MARK)
        print("wrote", path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
