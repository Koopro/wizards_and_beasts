#!/usr/bin/env python3
"""Block textures for the five wandwood species that had no blocks.

ash, blackthorn, hawthorn, walnut, willow — 7 textures each: log, log_top,
stripped_log, stripped_log_top, planks, leaves, sapling.

Same philosophy as `block_textures.py`: where a correct answer already exists, derive it
rather than invent one. Every texture here is a *vanilla* wood texture recoloured to the
species' real bark or foliage, so the grain, the pixel rhythm and the palette depth are
Minecraft's rather than mine. A hand-drawn 16x16 plank grain never sits right beside
vanilla planks; a recoloured one always does.

Donors are chosen by structure, not by colour, since colour is what gets replaced:

  ash         birch      — pale, straight, fine-grained; ash bark reads the same way
  blackthorn  dark_oak   — dense and near-black; blackthorn is the sloe, almost black
  hawthorn    oak        — small, knotty, ordinary hedgerow grain
  walnut      dark_oak   — the prized dark timber, warmed rather than greyed
  willow      oak        — fissured and grey; the tint does the work, not the grain

Leaves need a different treatment from bark, and the reason is worth stating because it is
easy to get wrong. The four wandwoods already shipped carry their green *in the PNG* — elder
is 44,67,43 at its darkest — and their models are a plain `block/leaves` parent with no tint
index, so nothing colours them at render time. But **every vanilla leaf texture is greyscale**
(birch, spruce and oak all measure R=G=B within a point or two); vanilla colours them from the
biome instead. Multiplying a grey donor by a green tint therefore yields grey-green mush that
is neither vanilla nor this mod, and every species comes out looking the same.

So bark and planks are recoloured by multiply — vanilla logs really are coloured, and the
multiply preserves their grain. Leaves are mapped luminance-to-ramp instead, the same way
`block_textures.sapling()` reskins the oak sapling, which is what puts a real green back in.

Vanilla textures are read from the Gradle-cached client jar and never redistributed; only
derived output is written into this repo.

Run from the repo root:  python tools/wandwood_textures.py [--force] [--only ash,willow]
"""

import argparse
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, mix, posterise, save  # noqa: E402
import block_textures as bt  # noqa: E402

BLOCK_DIR = "src/main/resources/assets/wizards_and_beasts/textures/block"
MARKER = marker("wandwood_textures.py")

# species -> its own vanilla donor family, tints, leaf ramp, and berry.
#
# Tints are per-channel multipliers applied before posterising. Stripped wood is paler and
# warmer than bark on every real tree, so it gets its own lift rather than reusing the bark
# tint: sharing them made stripped logs read as dirty bark instead of cut timber.
#
# Leaf ramps are darkest-first and aimed at a mean G/R of roughly 1.3-2.5. Anything near 1.0
# reads as grey no matter how dark it is.
SPECIES = {
    # --- the four that shipped hand-drawn, regenerated ---
    "elder": dict(
        donor="mangrove", leaf_donor="oak",
        bark=[(52, 44, 34), (80, 68, 50), (108, 94, 70), (136, 120, 94)],  # grey-brown, fissured
        leaf=[(34, 56, 30), (54, 84, 44), (78, 112, 60), (104, 140, 80)],
        berry=(58, 30, 66), berry_n=9,     # elderberry, near-black purple
    ),
    "holly": dict(
        donor="pale_oak", leaf_donor="spruce",
        bark=[(78, 84, 76), (108, 116, 106), (140, 148, 136), (172, 180, 168)],  # smooth pale grey
        leaf=[(14, 44, 22), (24, 66, 32), (36, 90, 46), (52, 116, 62)],   # very dark, glossy
        berry=(176, 32, 32), berry_n=11,   # the red berry everyone pictures
    ),
    "rowan": dict(
        donor="birch", leaf_donor="birch",
        bark=[(82, 78, 70), (112, 108, 98), (144, 140, 128), (176, 172, 160)],  # smooth grey, faintly warm
        leaf=[(48, 76, 34), (74, 108, 52), (102, 138, 70), (132, 168, 92)],
        berry=(206, 74, 28), berry_n=10,   # orange-red, not the same red as holly
    ),
    "yew": dict(
        donor="cherry", leaf_donor="spruce",
        bark=[(56, 34, 28), (84, 52, 40), (112, 72, 54), (140, 94, 72)],  # muted flaky red-brown
        leaf=[(18, 42, 30), (30, 62, 44), (44, 84, 60), (62, 108, 78)],   # dark blue-green
        berry=(198, 46, 46), berry_n=7,    # aril, sparser than holly
    ),
    # --- the five added with the block families ---
    "ash": dict(
        donor="oak", leaf_donor="birch",
        bark=[(104, 96, 76), (142, 132, 106), (178, 168, 138), (208, 200, 170)],  # pale grey-cream
        leaf=[(44, 60, 30), (66, 92, 44), (92, 122, 60), (120, 150, 80)],
        berry=None, berry_n=0,             # keys/samaras do not read at 16px
    ),
    "blackthorn": dict(
        donor="dark_oak", leaf_donor="oak",
        bark=[(26, 24, 24), (46, 42, 38), (68, 62, 56), (90, 82, 74)],  # near-black
        leaf=[(20, 38, 22), (32, 58, 32), (46, 78, 44), (64, 98, 58)],
        berry=(42, 42, 78), berry_n=9,     # sloe: blue-black, the species' whole name
    ),
    "hawthorn": dict(
        donor="acacia", leaf_donor="jungle",
        bark=[(56, 40, 28), (84, 62, 42), (112, 86, 58), (140, 110, 78)],  # warm hedgerow brown
        leaf=[(46, 74, 34), (70, 106, 50), (98, 136, 68), (128, 166, 90)],
        berry=(168, 38, 34), berry_n=10,   # haws
    ),
    "walnut": dict(
        donor="spruce", leaf_donor="spruce",
        bark=[(42, 28, 18), (66, 44, 28), (92, 64, 42), (118, 86, 58)],  # chocolate
        leaf=[(28, 50, 26), (46, 76, 40), (68, 102, 54), (92, 128, 72)],
        berry=None, berry_n=0,
    ),
    "willow": dict(
        donor="jungle", leaf_donor="birch",
        bark=[(66, 60, 48), (96, 88, 72), (126, 118, 98), (154, 146, 124)],  # grey, deeply fissured
        leaf=[(50, 68, 50), (74, 96, 72), (102, 124, 98), (130, 152, 124)],
        berry=None, berry_n=0,             # catkins, not berries
    ),
}

# Vanilla file names differ from the donor key for stripped variants only.
DONOR_FILES = {
    "log": "{d}_log",
    "log_top": "{d}_log_top",
    "stripped_log": "stripped_{d}_log",
    "stripped_log_top": "stripped_{d}_log_top",
    "planks": "{d}_planks",
    "leaves": "{d}_leaves",
}


SAPWOOD = (232, 214, 184)

# Heartwood: the colour of the cut timber. One value per species, read from the same data the
# wand uses (`wand_woods/<species>.json` -> `appearance.tint`), so a holly wand and a holly plank
# are the same wood (visual families, 2026-09-27). Planks, stripped logs and the rings of a log
# top are heartwood; only the log's side and the outer ring of its top are bark. Before this,
# every part was derived from the bark ramp and stripped wood was mixed toward one shared cream,
# so all nine stripped logs came out the same beige and pale timbers (holly, rowan, willow,
# elder) got grey planks under ivory wands.
WAND_WOODS = "src/main/resources/data/wizards_and_beasts/wizards_and_beasts/wand_woods"


def heartwood(species):
    import json
    with open(os.path.join(WAND_WOODS, species + ".json"), encoding="utf-8") as fh:
        tint = json.load(fh)["appearance"]["tint"].lstrip("#")[-6:]
    return tuple(int(tint[i:i + 2], 16) for i in (0, 2, 4))


def heart_ramp(species, lift=0.0):
    """Four tones about the species' heartwood: grain shadow, body, lit grain, highlight.

    Built in HSV so the hue and saturation of the wood survive: only the value steps, with the
    shadows a touch richer and the highlight a touch paler, the way vanilla's plank ramps run.
    Mixing toward white for the highlight (the first cut) greyed the dark timbers — yew and
    walnut came out mauve. The wand multiplies its tint over a light neutral sheet, so on screen
    it sits about a step below the raw tint; the body tone is centred there. `lift` raises the
    value for stripped wood, which is the same timber seen fresh, not a different colour.
    """
    import colorsys
    r, g, b = (v / 255.0 for v in heartwood(species))
    h, sat, val = colorsys.rgb_to_hsv(r, g, b)
    val *= 0.9
    steps = ((0.64, 1.12), (0.82, 1.05), (1.0, 1.0), (1.16, 0.86))
    values = [min(1.0, val * vf + lift * (1.0 - val * vf)) for vf, _ in steps]
    # Relative steps shrink to a few luminance levels on the darkest timbers (blackthorn, yew),
    # which reads as a flat wash; hold every ramp to vanilla's measured spread instead —
    # stripped_oak_log spans ~45 levels, oak_planks ~79 (see STRIPPED_RANGE / SPAN).
    # Stripped wood is held to exactly that spread, both ways: cut timber is calm, and a pale
    # species' natural range made willow and holly busier than their own bark.
    target = (STRIPPED_RANGE if lift else 60.0) / 255.0
    span = values[-1] - values[0]
    if span < target or (lift and span > target):
        mid = sum(values) / len(values)
        k = target / max(span, 1e-3)
        values = [mid + (v - mid) * k for v in values]
        shift = max(0.0, -min(values)) - max(0.0, max(values) - 1.0)
        values = [min(1.0, max(0.0, v + shift)) for v in values]
    return [tuple(int(round(c * 255)) for c in colorsys.hsv_to_rgb(h, min(1.0, sat * sf), v))
            for (_, sf), v in zip(steps, values)]


# Vanilla, measured off the client jar: stripped_oak_log spans 45 luminance levels
# (sd 12.4), stripped_birch_log 40 (sd 10.7). That is the band cut timber sits in — enough
# grain to see, far less than bark.
STRIPPED_RANGE = 45.0


def luminance(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


# How much of the ramp each part is allowed to travel, tuned so the output lands on the
# vanilla numbers measured off the client jar: oak_log spans 80 luminance levels,
# oak_planks 79, stripped_oak_log 45.
SPAN = {
    "log": 0.70,
    "log_top": 0.70,
    "planks": 0.68,
    "stripped_log": 0.38,
    "stripped_log_top": 0.38,
}


def lighten(ramp, amount=0.46, target=STRIPPED_RANGE):
    """A stripped-wood ramp from a bark ramp: paler and warmer, but still grained.

    Cut timber is not bark with the brightness turned up. The first version multiplied and
    added a lift, which clips: on a pale species — holly especially — most of the ramp
    saturated at 255 and the log came out as a flat white wash with no grain at all.

    Mixing toward sapwood cannot clip, but on its own it flattens, because every entry moves
    toward the same colour. So the ramp is re-expanded about its own mean — and by whatever
    factor actually reaches vanilla's spread for this species rather than by a fixed guess,
    since a dark narrow bark ramp and a pale wide one need very different pushes. Measured
    against the target instead of eyeballed, the nine species come out consistent.
    """
    mixed = [mix(c + (255,), SAPWOOD + (255,), amount)[:3] for c in ramp]
    lum = [luminance(c) for c in mixed]
    span = max(lum) - min(lum)
    factor = 1.0 if span < 1.0 else max(1.0, min(5.0, target / span))
    mean = [sum(c[i] for c in mixed) / len(mixed) for i in range(3)]
    out = [[mean[i] + (c[i] - mean[i]) * factor for i in range(3)] for c in mixed]
    # Expanding about the mean can push a pale species past white; slide the whole ramp
    # back inside the gamut rather than clipping it, which would flatten it again.
    high = max(max(c) for c in out)
    low = min(min(c) for c in out)
    shift = (255 - high) if high > 255 else ((0 - low) if low < 0 else 0)
    return [tuple(max(0, min(255, int(round(v + shift)))) for v in c) for c in out]


def ramped(img, ramp, colours=10, span=1.0):
    """Map the donor's luminance onto a colour ramp, then posterise.

    Not a per-channel multiply, which is what the first pass used. Multiplying can only push a
    donor's existing hue around — it cannot set one — so yew came out neon salmon off cherry,
    ash came out gold off oak, and willow stayed jungle-green no matter what it was multiplied
    by. Mapping luminance keeps the donor's *grain* while the ramp fixes the colour outright.
    """
    # Normalised: the donors are vanilla textures whose luminance sits in a narrow
    # band, and mapping that band absolutely onto the ramp wastes most of it.
    return posterise(bt.recolour(img, bt.ramp_from(ramp), normalise=True, span=span),
                     colours)


def tinted(img, tint, colours=10):
    """Retained for anything still wanting a multiply. Unused by the wood path."""
    out = img.convert("RGBA").copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (
                max(0, min(255, int(r * tint[0]))),
                max(0, min(255, int(g * tint[1]))),
                max(0, min(255, int(b * tint[2]))),
                a,
            )
    return posterise(out, colours)


def berries(img, colour, count, seed):
    """Stamp small fruit clusters onto opaque leaf pixels.

    Deliberately not random per run: the seed is derived from the species name, so
    regenerating produces the identical texture and a rebuild never shows up as a diff.

    Each berry is one pixel plus a lighter highlight above-left, which is how vanilla draws
    small round things at this size — sweet berries and glow berries both do it. A flat
    single-colour dot reads as a hole in the canopy instead of a berry.
    """
    if not colour or count <= 0:
        return img
    rng = random.Random(seed)
    out = img.copy()
    px = out.load()
    w, h = out.size
    hi = tuple(min(255, int(c * 1.45)) for c in colour)
    placed = []
    attempts = 0
    while len(placed) < count and attempts < count * 40:
        attempts += 1
        x, y = rng.randrange(1, w - 1), rng.randrange(1, h - 1)
        if px[x, y][3] < 200:
            continue
        # Keep them apart; clumped berries turn into an unreadable blob.
        if any(abs(x - ox) < 3 and abs(y - oy) < 3 for ox, oy in placed):
            continue
        placed.append((x, y))
        px[x, y] = colour + (255,)
        if px[x - 1, y - 1][3] >= 200:
            px[x - 1, y - 1] = hi + (255,)
    return out


def build(species, spec):
    """Every texture for one species, keyed by the file name it will be written to."""
    bark = spec["donor"]
    out = {}
    for part, pattern in DONOR_FILES.items():
        donor = pattern.format(d=spec["leaf_donor"] if part == "leaves" else bark)
        src = bt.vanilla(donor)
        if part == "leaves":
            # Greyscale donor -> species ramp. See the module docstring.
            leaves = posterise(bt.recolour(src, bt.ramp_from(spec["leaf"])), 10)
            # Berries after posterising: quantising them alongside the foliage merges the
            # fruit into the nearest green and it disappears.
            leaves = berries(leaves, spec.get("berry"), spec.get("berry_n", 0),
                             sum(ord(c) for c in species))
            out[species + "_leaves"] = leaves
            continue
        if part == "log":
            ramp = spec["bark"]
        elif part.startswith("stripped"):
            ramp = heart_ramp(species, lift=0.12)
        else:
            ramp = heart_ramp(species)
        if part.startswith("stripped"):
            name = part.replace("log", species + "_log", 1)
        else:
            name = species + "_" + part
        # The heartwood ramp is already held to vanilla's spread, so the stripped parts use most
        # of it; the 0.38 in SPAN was tuned for the old bark-derived ramp and flattens it.
        span = 0.85 if part.startswith("stripped") else SPAN.get(part, 0.7)
        img = ramped(src, ramp, span=span)
        if part == "log_top":
            img = bark_rim(img, ramped(src, spec["bark"], span=SPAN["log"]))
        out[name] = img
    return out


def bark_rim(top, bark):
    """The outer ring of a log's end is bark, the inside is heartwood — as on every vanilla log."""
    out = top.copy()
    px, bx = out.load(), bark.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                px[x, y] = bx[x, y]
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite even hand-authored art")
    ap.add_argument("--only", help="comma-separated species subset")
    args = ap.parse_args(argv[1:])

    wanted = set(args.only.split(",")) if args.only else set(SPECIES)
    written = skipped = 0

    for species, spec in SPECIES.items():
        if species not in wanted:
            continue
        for name, img in build(species, spec).items():
            path = os.path.join(BLOCK_DIR, name + ".png")
            if not args.force and not is_regenerable(path, MARKER):
                print("  skip (not ours) %s" % name)
                skipped += 1
                continue
            save(img, path, MARKER)
            written += 1
            print("  wrote %s" % name)

    # Saplings last: block_textures.sapling() reads this mod's own <wood>_leaves and
    # <wood>_log off disk, so they have to exist before it runs.
    for species in sorted(wanted):
        path = os.path.join(BLOCK_DIR, species + "_sapling.png")
        if not args.force and not is_regenerable(path, MARKER):
            print("  skip (not ours) %s_sapling" % species)
            skipped += 1
            continue
        save(bt.sapling(species), path, MARKER)
        written += 1
        print("  wrote %s_sapling" % species)

    print("\n%d written, %d skipped" % (written, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
