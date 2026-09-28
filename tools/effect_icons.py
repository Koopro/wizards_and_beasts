#!/usr/bin/env python3
"""The three mob effects that ship with no icon.

`basilisk_gaze_lock`, `mandrake_restoration` and `sundered` are registered and applied in
play, but have no sprite — so they render as the missing-texture checkerboard in the
inventory and in the HUD status bar. 25 of the mod's 28 effects have art; these are the gap.

Derived from vanilla mob-effect sprites rather than drawn, for the same reason the wandwood
textures are: at 18x18 the vanilla icons have a particular weight and outline, and a
hand-drawn one sits badly in a row of them. Donors are chosen for silhouette, since colour is
what gets replaced:

  basilisk_gaze_lock   blindness     the eye. Recoloured to a venomous yellow-green, which is
                                     the one effect in the mod that means "look away".
  mandrake_restoration regeneration  the upward curl reads as a shoot; earthed to root colours
                                     rather than the vanilla pink.
  sundered             resistance    the shield. Recoloured to the effect's own declared
                                     0xA83232 so a broken shield reads as broken armour.

Vanilla textures are read from the Gradle-cached client jar and never redistributed; only
derived output is written into this repo.

Run from the repo root:  python tools/effect_icons.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar_texture, is_regenerable, marker, posterise, save  # noqa: E402
import block_textures as bt  # noqa: E402

EFFECT_DIR = "src/main/resources/assets/wizards_and_beasts/textures/mob_effect"
MARKER = marker("effect_icons.py")

# effect -> (vanilla donor, ramp darkest-first)
EFFECTS = {
    "basilisk_gaze_lock": (
        "blindness",
        [(28, 34, 10), (74, 88, 20), (132, 152, 34), (196, 214, 74)],   # venom yellow-green
    ),
    "mandrake_restoration": (
        "regeneration",
        [(48, 38, 22), (92, 74, 40), (140, 118, 68), (196, 180, 128)],  # root and earth
    ),
    "sundered": (
        "resistance",
        [(46, 12, 12), (96, 26, 26), (168, 50, 50), (214, 96, 96)],     # the effect's own 0xA83232
    ),
}


def icon(donor, ramp):
    """Vanilla silhouette wearing this effect's colours, posterised back to a small palette."""
    src = client_jar_texture("textures/mob_effect/" + donor + ".png")
    return posterise(bt.recolour(src, bt.ramp_from(ramp)), 8)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args(argv[1:])

    written = skipped = 0
    for name, (donor, ramp) in EFFECTS.items():
        path = os.path.join(EFFECT_DIR, name + ".png")
        if not args.force and not is_regenerable(path, MARKER):
            print("  skip (not ours) %s" % name)
            skipped += 1
            continue
        save(icon(donor, ramp), path, MARKER)
        written += 1
        print("  wrote %s (from vanilla %s)" % (name, donor))

    print("\n%d written, %d skipped" % (written, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
