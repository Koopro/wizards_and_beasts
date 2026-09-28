#!/usr/bin/env python3
"""Block textures for Wizards & Beasts.

33 block textures were flat placeholder fills. Where a correct answer already exists
somewhere, this derives it rather than inventing one:

  - unlit torches/lanterns are the *vanilla* lit texture with the flame removed. The
    flame mask is not hand-painted: vanilla ships `redstone_torch` and
    `redstone_torch_off`, so the pixels that differ between them are exactly the
    flame, and that mask transfers to every torch variant because they share a stick.
  - saplings take their silhouette from `oak_sapling` and their colours from the
    mod's own `<wood>_leaves.png` / `<wood>_log.png`, so each sapling matches the
    tree it grows into instead of guessing a green.
  - mandrake crop stages take vanilla potato-stage silhouettes, recoloured, with the
    root crown surfacing only at the final stage.

The rest (workbench, trunks, props) are drawn from a small primitive set — plank
grain, leather, brass banding — in the palettes the surrounding blocks already use.

Vanilla textures are read from the Gradle-cached client jar and never redistributed:
only derived output is written into this repo.

Run from the repo root:  python tools/block_textures.py [--force] [--only name,name]
"""

import argparse
import io
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import client_jar, is_regenerable, marker, mix, posterise, save, shade  # noqa: E402

BLOCK_DIR = "src/main/resources/assets/wizards_and_beasts/textures/block"
MARKER = marker("block_textures.py")

_vanilla_cache = {}


def vanilla(name):
    """Load a vanilla block texture from the cached client jar."""
    if name in _vanilla_cache:
        return _vanilla_cache[name]
    with client_jar().open("assets/minecraft/textures/block/" + name + ".png") as fh:
        img = Image.open(io.BytesIO(fh.read())).convert("RGBA")
    _vanilla_cache[name] = img
    return img


def mod_texture(name):
    return Image.open(os.path.join(BLOCK_DIR, name + ".png")).convert("RGBA")


# --------------------------------------------------------------------------- helpers

def noise(seed, x, y):
    n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def palette_of(img, count=4):
    """Dominant opaque colours, darkest first — used to reskin vanilla silhouettes."""
    counts = {}
    for px in (img.get_flattened_data() if hasattr(img, "get_flattened_data") else img.getdata()):
        if px[3] < 200:
            continue
        counts[px[:3]] = counts.get(px[:3], 0) + 1
    ranked = sorted(counts.items(), key=lambda kv: -kv[1])[:max(count, 4)]
    return sorted((c for c, _ in ranked), key=lambda c: sum(c))[:count] or [(120, 120, 120)]


def recolour(src, ramp, normalise=False, span=1.0):
    """Map a source texture's luminance onto a colour ramp, keeping its alpha.

    `normalise` stretches the donor's own luminance range across the whole ramp first,
    and `span` then pulls that back toward the middle. Both are needed: stretching alone
    overshoots — full normalisation took the stripped logs from a 9-level range to 122,
    where vanilla sits at 45 — so `span` is how far along the ramp the donor is allowed to
    travel, and it is set per part against measured vanilla numbers rather than by eye.
    Without it the mapping is absolute, and a donor that occupies a narrow band of the
    scale only ever reaches the matching narrow band of the ramp — vanilla
    `stripped_oak_log` spans 45 luminance levels out of 255, so a fifth of the ramp did all
    the work and every stripped wandwood log came out an almost flat wash. Bark donors span
    twice that, which is why the logs looked fine and only the stripped ones did not.
    """
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    w, h = src.size
    lo, hi = 0.0, 1.0
    if normalise:
        lums = [(0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                for r, g, b, a in src.convert("RGBA").getdata() if a >= 8]
        if lums and max(lums) - min(lums) > 0.02:
            lo, hi = min(lums), max(lums)
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a < 8:
                continue
            lum = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0 - lo) / (hi - lo)
            lum = 0.5 + (lum - 0.5) * span
            pos = min(0.999, max(0.0, lum)) * (len(ramp) - 1)
            i = int(pos)
            c = ramp[i] if i >= len(ramp) - 1 else mix(ramp[i], ramp[i + 1], pos - i)
            op[x, y] = (c[0], c[1], c[2], a)
    return out


def ramp_from(colours, steps=5):
    lo, hi = colours[0], colours[-1]
    return [mix(shade(lo + (255,), 0.75)[:3], shade(hi + (255,), 1.18)[:3], i / (steps - 1))
            for i in range(steps)]


def flame_mask():
    """Pixels that differ between vanilla redstone_torch and redstone_torch_off.

    That difference is precisely the lit flame, so it doubles as a cut-out mask for
    every other torch variant — they all share the same stick geometry."""
    lit, off = vanilla("redstone_torch"), vanilla("redstone_torch_off")
    mask = Image.new("L", lit.size, 0)
    lp, op, mp = lit.load(), off.load(), mask.load()
    w, h = lit.size
    for y in range(h):
        for x in range(w):
            a, b = lp[x, y], op[x, y]
            if a != b:
                mp[x, y] = 255
    return mask


def plank_grain(size, base, seed, rows=4):
    img = Image.new("RGBA", (size, size), base + (255,))
    px = img.load()
    row_h = size // rows
    for y in range(size):
        row = y // row_h
        tone = 0.88 + 0.2 * noise(seed, 0, row)
        for x in range(size):
            g = tone * (0.94 + 0.12 * noise(seed + row, x // 2, y))
            if y % row_h == 0:
                g *= 0.7
            px[x, y] = shade(base + (255,), g)
    return img


def brass_edges(draw, size, colour, inset=0):
    draw.rectangle([inset, inset, size - 1 - inset, size - 1 - inset], outline=colour)


# --------------------------------------------------------------------------- assets

def unlit_torch_from(vanilla_name, char_tone):
    """Vanilla torch minus its flame, with the exposed wick charred."""
    src = vanilla(vanilla_name).copy()
    mask = flame_mask()
    sp, mp = src.load(), mask.load()
    w, h = src.size
    for y in range(h):
        for x in range(w):
            if mp[x, y]:
                sp[x, y] = (0, 0, 0, 0)
    # Char the topmost surviving pixel of each column so it reads burnt out, not cut off.
    for x in range(w):
        for y in range(h):
            if sp[x, y][3] > 8:
                sp[x, y] = char_tone
                if y + 1 < h and sp[x, y + 1][3] > 8:
                    sp[x, y + 1] = mix(sp[x, y + 1], char_tone, 0.55)
                break
    return src


def unlit_lantern_from(vanilla_name):
    """Vanilla lantern with the glowing core snuffed: the bright interior goes cold
    and dark while the metal cage keeps its own shading."""
    src = vanilla(vanilla_name).copy()
    px = src.load()
    w, h = src.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            if lum > 0.46:
                # The core is whatever is bright, whatever its hue — soul lanterns
                # glow cyan and copper lanterns glow red, so a warm-hue test misses
                # two of the three. Snuff it to cold ash and strip the saturation.
                grey = int(sum((r, g, b)) / 3)
                cold = mix((grey, grey, grey, a), (38, 38, 46, 255), 0.78)
                px[x, y] = cold
            else:
                px[x, y] = shade((r, g, b, a), 0.74)
    return src


# Elder and yew leaves are close enough in hue that saplings derived from them came out
# indistinguishable in the inventory. A small per-wood shift separates them without
# contradicting the leaf art they are taken from.
SAPLING_SHIFT = {
    "elder": (1.10, 1.06, 0.88),    # warmer, paler
    "yew": (0.82, 0.94, 0.86),      # darker, bluer
    "holly": (0.90, 1.04, 0.92),
    "rowan": (1.06, 0.96, 0.90),
}


def sapling(wood):
    """Vanilla sapling silhouette wearing this tree's own leaf and bark colours."""
    shift = SAPLING_SHIFT.get(wood, (1.0, 1.0, 1.0))
    base = [tuple(max(0, min(255, int(c * k))) for c, k in zip(col, shift))
            for col in palette_of(mod_texture(wood + "_leaves"))]
    leaves = ramp_from(base)
    bark = palette_of(mod_texture(wood + "_log"))
    out = recolour(vanilla("oak_sapling"), leaves)
    # Repaint the stem: the lowest few rows of the vanilla sprite are the stalk.
    op = out.load()
    w, h = out.size
    for y in range(h - 5, h):
        for x in range(w):
            if op[x, y][3] > 8:
                op[x, y] = bark[0] + (255,) if (x + y) % 3 else shade(bark[-1] + (255,), 0.9)
    return out


LEAF_GREEN = [(38, 62, 30), (58, 92, 42), (86, 124, 58), (120, 156, 78)]
MANDRAKE_SKIN = [(96, 78, 50), (150, 124, 78), (196, 172, 118), (222, 204, 156)]


def mandrake_stage(stage):
    out = recolour(vanilla("potatoes_stage%d" % stage), ramp_from(LEAF_GREEN))
    if stage == 3:
        # Only the mature crop shows the root's shoulders above the soil.
        d = ImageDraw.Draw(out)
        d.ellipse([5, 10, 10, 14], fill=MANDRAKE_SKIN[2] + (255,))
        d.point((6, 12), fill=MANDRAKE_SKIN[0] + (255,))
        d.point((9, 12), fill=MANDRAKE_SKIN[0] + (255,))
        d.line([(7, 13), (8, 13)], fill=MANDRAKE_SKIN[0] + (255,))
    return out


BENCH_WOOD = (94, 66, 40)
BENCH_DARK = (62, 43, 26)
BRASS = (168, 138, 72)
BRASS_HI = (214, 186, 112)


def bench_top():
    img = plank_grain(16, BENCH_WOOD, 7, rows=2)
    d = ImageDraw.Draw(img)
    # Wandmaker's bench: a brass measuring rule inlaid along the back, tool notches
    # at the front, and a wand blank resting in a shallow channel.
    d.line([(1, 2), (14, 2)], fill=BRASS + (255,))
    for x in range(2, 15, 3):
        d.point((x, 3), fill=BRASS_HI + (255,))
    d.line([(2, 8), (13, 8)], fill=shade(BENCH_DARK + (255,), 0.8))
    d.line([(2, 9), (13, 9)], fill=mix(BENCH_WOOD + (255,), (222, 200, 160, 255), 0.5))
    for x in (3, 6, 9, 12):
        d.point((x, 13), fill=BENCH_DARK + (255,))
    return img


def bench_front():
    img = plank_grain(16, BENCH_WOOD, 11, rows=3)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 5, 13, 11], outline=BENCH_DARK + (255,))
    d.rectangle([3, 6, 12, 10], fill=shade(BENCH_WOOD + (255,), 0.78))
    d.point((12, 8), fill=BRASS_HI + (255,))   # drawer pull
    d.line([(0, 0), (15, 0)], fill=shade(BENCH_WOOD + (255,), 1.25))
    d.line([(0, 15), (15, 15)], fill=shade(BENCH_WOOD + (255,), 0.6))
    return img


def bench_side():
    img = plank_grain(16, BENCH_DARK, 13, rows=3)
    d = ImageDraw.Draw(img)
    d.line([(2, 3), (13, 3)], fill=BRASS + (255,))
    d.line([(2, 12), (13, 12)], fill=shade(BENCH_DARK + (255,), 0.72))
    d.line([(0, 0), (15, 0)], fill=shade(BENCH_DARK + (255,), 1.3))
    return img


def _trunk_field(leather, seed):
    """The scuffed leather ground every trunk face is drawn on."""
    img = Image.new("RGBA", (16, 16), leather + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            g = 0.86 + 0.24 * noise(seed, x // 2, y // 2)
            if y in (0, 15) or x in (0, 15):
                g *= 0.72
            px[x, y] = shade(leather + (255,), g)
    return img


def _trunk_straps(d, leather, straps):
    for sx in ([4, 11] if straps == 2 else [7]):
        d.line([(sx, 0), (sx, 15)], fill=shade(leather + (255,), 0.62))
        d.line([(sx + 1, 0), (sx + 1, 15)], fill=shade(leather + (255,), 1.18))


def _trunk_corners(d, brass):
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        d.rectangle([cx, cy, cx + 1, cy + 1], fill=brass + (255,))


def trunk(leather, seed, brass=BRASS, straps=2):
    """A trunk's flank: leather field, brass corner caps, straps and the lid seam.

    No latch here — the latch belongs on the front, and stamping it on all four
    flanks is what made the old single-sprite trunk read as a crate.
    """
    img = _trunk_field(leather, seed)
    d = ImageDraw.Draw(img)
    _trunk_straps(d, leather, straps)
    # Lid seam: the body/lid split in the model sits at y=9, i.e. 7 rows down.
    d.line([(0, 7), (15, 7)], fill=shade(leather + (255,), 0.55))
    d.line([(0, 8), (15, 8)], fill=shade(leather + (255,), 1.12))
    _trunk_corners(d, brass)
    return img


def trunk_front(leather, seed, brass=BRASS, straps=2):
    """The face you walk up to: same flank, plus a brass lock plate and keyhole."""
    img = trunk(leather, seed + 1, brass=brass, straps=straps)
    d = ImageDraw.Draw(img)
    d.rectangle([6, 6, 9, 10], fill=brass + (255,))
    d.rectangle([6, 6, 9, 10], outline=shade(brass + (255,), 0.72))
    d.point((7, 7), fill=BRASS_HI + (255,))
    # Keyhole — a dark bore with a slot under it, the one high-contrast detail.
    d.point((8, 8), fill=(18, 16, 14, 255))
    d.point((8, 9), fill=(18, 16, 14, 255))
    return img


def trunk_top(leather, seed, brass=BRASS, straps=2):
    """The lid seen from above: boards running front-to-back, hasp at the front edge."""
    img = _trunk_field(leather, seed + 2)
    d = ImageDraw.Draw(img)
    # Board joins across the lid, perpendicular to the straps below.
    for y in (4, 11):
        d.line([(0, y), (15, y)], fill=shade(leather + (255,), 0.66))
        d.line([(0, y + 1), (15, y + 1)], fill=shade(leather + (255,), 1.14))
    _trunk_straps(d, leather, straps)
    _trunk_corners(d, brass)
    # Hasp folding over the front lip (north edge, matching the model's front face).
    d.rectangle([6, 0, 9, 3], fill=brass + (255,))
    d.point((7, 1), fill=BRASS_HI + (255,))
    return img


def _fireplace_brick(seed=5, courses=4):
    """Mortared brickwork — the surround shared by every face of the fireplace."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            g = 0.82 + 0.3 * noise(seed, x // 2, y // 3)
            px[x, y] = shade((78, 74, 72, 255), g)
    d = ImageDraw.Draw(img)
    for y in range(0, 16, courses):
        d.line([(0, y), (15, y)], fill=(52, 48, 46, 255))
    for row, y in enumerate(range(0, 16, courses)):
        for x in range((0 if row % 2 else 4), 16, 8):
            d.line([(x, y), (x, min(15, y + courses - 1))], fill=(52, 48, 46, 255))
    return img


def floo_fireplace(lit, phase=0.0):
    """The hearth face. `phase` in [0, 1) advances the flame; the brickwork stays
    identical across frames so an animation strip only ever shows the fire moving.

    Kept at this signature because `animate_textures.py` drives it per-frame.
    """
    img = _fireplace_brick()
    d = ImageDraw.Draw(img)
    # Hearth opening.
    d.rectangle([4, 8, 11, 15], fill=(22, 20, 22, 255))
    if lit:
        for x in range(5, 11):
            h = 3 + int(2.5 * math.sin(x * 1.1 + phase * 2.0 * math.pi))
            for y in range(15 - h, 16):
                t = (15 - y) / max(1, h)
                d.point((x, y), fill=mix((64, 220, 128, 255), (206, 255, 226, 255), t))
    return img


def floo_fireplace_side():
    """The flanks: unbroken brickwork. The old single sprite put the hearth opening
    on all six faces, so the chimney appeared to be open on every side at once."""
    return _fireplace_brick()


def floo_fireplace_top():
    """Looking down at the chimney: a tighter course with the flue mouth in the middle."""
    img = _fireplace_brick(seed=9, courses=8)
    d = ImageDraw.Draw(img)
    d.rectangle([5, 5, 10, 10], fill=(30, 28, 30, 255))
    d.rectangle([5, 5, 10, 10], outline=(52, 48, 46, 255))
    return img


# The Floo palette: one ramp shared by the hearth's fire and the flames block in front
# of it, so the two never read as two different greens burning side by side.
FLOO_EMBER = (18, 78, 46, 255)
FLOO_GREEN = (64, 220, 128, 255)
FLOO_PALE = (206, 255, 226, 255)


def floo_flames(phase=0.0):
    """A tongue of emerald fire on transparent ground, for the standalone flames block.

    Four copies of this sprite lean inward on the model (see `ModModelProvider.flamesModel`),
    so the silhouette is centre-weighted: tall in the middle, nothing at the edges. Four
    full-width sheets would meet at the corners and read as a green box.

    Alpha is binary because the block renders with the `cutout` render type, which discards
    a fragment outright rather than blending it — a soft-edged tip would come out as a hard
    edge in a different place. The fraying is therefore cut into the silhouette itself, with
    the cut scrolling on `phase` so it does not sit as a fixed hole through the animation.

    `phase` in [0, 1) advances the fire; `animate_textures.py` drives it per-frame. This is
    deliberately not in this module's ASSETS: `floo_flames.png` is an animation strip with a
    single owner, and registering it here as well is how the floating candle got flattened
    from a strip back to one frame (see `artgen_common.is_regenerable`).
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    scroll = int(phase * 16) % 16
    for x in range(16):
        edge = abs(x - 7.5) / 7.5
        envelope = max(0.0, 1.0 - edge * edge)
        lick = (0.55 * math.sin(x * 0.9 + phase * 2 * math.pi)
                + 0.30 * math.sin(x * 2.3 - phase * 4 * math.pi)
                + 0.15 * math.sin(x * 4.1 + phase * 6 * math.pi))
        height = envelope * (12.0 + 3.0 * lick)
        if height < 1.0:
            continue
        for y in range(16 - int(round(height)), 16):
            t = (15 - y) / height          # 0 at the base, 1 at the tip
            if t > 1.0:
                continue
            # Above the halfway mark the tongue breaks up into licks.
            if t > 0.5 and noise(29, x, (y + scroll) % 16) > 1.15 - t * 0.75:
                continue
            if t < 0.35:
                col = mix(FLOO_PALE, FLOO_GREEN, t / 0.35)
            else:
                col = mix(FLOO_GREEN, FLOO_EMBER, (t - 0.35) / 0.65)
            px[x, y] = col
    return img


def spell_teacher():
    img = plank_grain(16, (70, 58, 84), 17, rows=4)
    d = ImageDraw.Draw(img)
    # A lectern face: open book on a stand.
    d.polygon([(2, 9), (7, 7), (7, 13), (2, 14)], fill=(226, 220, 204, 255))
    d.polygon([(13, 9), (8, 7), (8, 13), (13, 14)], fill=(210, 203, 188, 255))
    d.line([(7, 7), (8, 7)], fill=(120, 104, 78, 255))
    for y in (9, 11):
        d.line([(3, y), (6, y)], fill=(150, 146, 136, 255))
        d.line([(9, y), (12, y)], fill=(150, 146, 136, 255))
    d.line([(0, 0), (15, 0)], fill=shade((70, 58, 84, 255), 1.3))
    return img


def spell_teacher_top():
    """The lectern's reading surface from above: the open book, foreshortened."""
    img = plank_grain(16, (70, 58, 84), 19, rows=3)
    d = ImageDraw.Draw(img)
    d.polygon([(2, 3), (7, 2), (7, 13), (2, 12)], fill=(226, 220, 204, 255))
    d.polygon([(13, 3), (8, 2), (8, 13), (13, 12)], fill=(210, 203, 188, 255))
    d.line([(7, 2), (7, 13)], fill=(120, 104, 78, 255))
    d.line([(8, 2), (8, 13)], fill=(120, 104, 78, 255))
    for x in (4, 10):
        for y in range(4, 12, 2):
            d.line([(x - 1, y), (x + 1, y)], fill=(150, 146, 136, 255))
    return img


def examination_desk_top():
    """A worked desk surface: parchment, an inkwell and a quill.

    `examination_desk.png` is hand art with no generator marker, so it is left alone
    and used as the desk's flank. Its own palette drives this top so the two faces
    still read as one piece of furniture.
    """
    wood = palette_of(mod_texture("examination_desk"))
    base, dark = wood[-1], wood[0]
    img = plank_grain(16, base, 23, rows=3)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (15, 0)], fill=shade(base + (255,), 1.28))
    # Parchment sheet, slightly askew so it reads as left mid-exam.
    d.polygon([(3, 4), (11, 3), (12, 12), (4, 13)], fill=(226, 214, 184, 255))
    d.polygon([(3, 4), (11, 3), (12, 12), (4, 13)], outline=(196, 182, 150, 255))
    for y in range(6, 12, 2):
        d.line([(5, y), (10, y)], fill=(150, 140, 118, 255))
    # Inkwell and quill.
    d.rectangle([12, 5, 14, 7], fill=dark + (255,))
    d.point((13, 6), fill=(28, 26, 40, 255))
    d.line([(13, 4), (15, 1)], fill=(232, 228, 216, 255))
    return img


def devils_snare():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(6):
        x = 1 + i * 3
        sway = int(1.6 * math.sin(i * 1.7))
        d.line([(x, 0), (x + sway, 15)], fill=mix((26, 54, 30, 255), (66, 104, 54, 255), i / 5.0),
               width=2)
        for k in range(3, 15, 4):
            d.point((x + sway // 2 - 1, k), fill=(88, 128, 66, 255))
            d.point((x + sway // 2 + 1, k + 2), fill=(48, 82, 44, 255))
    return img


def mallowsweet():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(5):
        x = 2 + i * 3
        d.line([(x, 15), (x - 1, 6)], fill=(74, 104, 62, 255))
        d.ellipse([x - 2, 3, x + 1, 7], fill=(214, 182, 216, 255))
        d.point((x - 1, 5), fill=(246, 232, 246, 255))
    return img


def floating_candle(phase=0.0):
    """`phase` in [0, 1) sways and pulses the flame; the wax body never moves."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([6, 6, 9, 15], fill=(232, 226, 206, 255))
    d.line([(6, 6), (6, 15)], fill=(250, 246, 232, 255))
    d.line([(9, 6), (9, 15)], fill=(196, 190, 172, 255))
    for x in (6, 8):
        d.point((x, 7), fill=(206, 198, 178, 255))
    d.line([(7, 3), (7, 5)], fill=(60, 52, 44, 255))
    sway = math.sin(phase * 2.0 * math.pi)
    tip_x = 7 + (1 if sway > 0.55 else (-1 if sway < -0.55 else 0))
    top = 0 if abs(sway) < 0.8 else 1
    d.polygon([(tip_x, top), (tip_x + 2, 3), (7, 4), (6, 3)], fill=(255, 226, 138, 255))
    d.point((tip_x, 2), fill=(255, 252, 226, 255))
    return img



def cauldron_top(metal, brew, seed):
    """Looking down into a full cauldron: a metal rim, then the brew surface inside it.

    The side texture already reads as a metal vessel; what a cube-shaped cauldron lacked
    was any sign there is something *in* it, which is the whole point of a cauldron.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge < 2:
                g = 1.12 if (x < 2 or y < 2) else 0.82
                g *= 0.94 + 0.12 * noise(seed, x, y)
                px[x, y] = shade(metal + (255,), g)
                continue
            depth = min(1.0, (edge - 2) / 5.0)
            g = 0.72 + 0.34 * depth
            g *= 0.96 + 0.08 * noise(seed + 7, x // 2, y // 2)
            px[x, y] = shade(brew + (255,), g)

    d = ImageDraw.Draw(img)
    # Bubbles clustered off-centre — an even scatter reads as a pattern, not a brew.
    for bx, by, r in ((6, 7, 1), (9, 6, 0), (8, 10, 1), (11, 9, 0), (5, 11, 0)):
        d.ellipse([bx - r, by - r, bx + r, by + r],
                  fill=mix(brew + (255,), (255, 255, 255, 255), 0.42))
    return img


# Occamy eggshell: canon calls it "pure, soft silver, thin as paper". The block is a
# broken half-shell resting on the ground, so it wants two faces that disagree — a
# curved silver wall from the side, and a hole from above. It had neither: the model
# borrowed the 16x16 *item* sprite for all six faces, which is a whole egg drawn in an
# inventory frame, so the placed block was a flat egg decal smeared over a cuboid with
# the sprite's transparent corners rendering as opaque black.
SHELL = (201, 205, 214)
SHELL_LIT = (233, 236, 242)
SHELL_DARK = (126, 131, 143)
SHELL_HOLLOW = (48, 51, 59)


def _shell_rim(x, seed, base, amp):
    """Height of the broken edge at column x — jagged, but never a lone spike."""
    n = 0.55 * noise(seed, x, 0) + 0.45 * noise(seed + 3, x // 2, 0)
    return base + int(round(amp * n))


def occamy_eggshell_side():
    """The shell wall seen from outside: silver, curved, cracked open along the top.

    Authored full-frame because the model maps 0-16 onto the face rather than letting
    the auto-UV pick a window — the shell is 6 pixels wide as a block and a cropped
    window of a 16x16 sprite is what made the old one unreadable.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        rim = _shell_rim(x, 11, 2, 3)
        for y in range(16):
            if y < rim:
                continue
            # Curvature: brightest just left of centre, falling off to both edges, so
            # four flat faces still read as one round object.
            curve = 1.0 - abs((x - 6.5) / 8.5)
            g = 0.70 + 0.42 * curve
            # The lip catches the light; the base sits in its own shadow.
            if y <= rim + 1:
                g *= 1.18
            g *= 0.78 + 0.30 * min(1.0, (16 - y) / 9.0)
            g *= 0.95 + 0.10 * noise(29, x, y)
            px[x, y] = shade(SHELL + (255,), g)
    # A hairline crack running down from the broken edge — the thing that says "shell"
    # rather than "silver block" at 6 pixels wide.
    d = ImageDraw.Draw(img)
    crack = mix(SHELL_DARK + (255,), (0, 0, 0, 255), 0.35)
    cx = 10
    for y in range(_shell_rim(cx, 11, 2, 3) + 1, 13):
        d.point((cx + (1 if y % 4 == 3 else 0), y), fill=crack)
    return img


def occamy_eggshell_top():
    """Looking down into the shell: a silver rim, then the hollow inside it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            # Distance from centre, as a fraction of the way to the frame edge.
            r = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            wobble = 0.06 * noise(41, x, y)
            if r > 0.97 + wobble:
                continue
            if r > 0.60 + wobble:
                g = 0.86 + 0.34 * (1.0 - r)
                g *= 0.95 + 0.10 * noise(47, x, y)
                px[x, y] = shade(SHELL + (255,), g)
            else:
                # The hollow darkens toward the middle rather than sitting flat, or the
                # opening reads as a painted dot instead of a hole.
                g = 0.60 + 0.75 * (r / 0.60)
                px[x, y] = shade(SHELL_HOLLOW + (255,), g)
    # Inner lip: one bright pixel ring where the broken edge catches the sky.
    d = ImageDraw.Draw(img)
    d.ellipse([4, 4, 11, 11], outline=mix(SHELL_LIT + (255,), SHELL + (255,), 0.25))
    return img


ASSETS = {
    # Wall torches deliberately absent: vanilla's template_torch_wall takes the *standing*
    # torch sprite, so a separate <name>_wall_torch.png is never sampled by any model.
    "unlit_torch": lambda: unlit_torch_from("torch", (40, 34, 30, 255)),
    "unlit_soul_torch": lambda: unlit_torch_from("soul_torch", (34, 40, 44, 255)),
    "unlit_copper_torch": lambda: unlit_torch_from("copper_torch", (38, 44, 40, 255)),
    "unlit_lantern": lambda: unlit_lantern_from("lantern"),
    "unlit_soul_lantern": lambda: unlit_lantern_from("soul_lantern"),
    "unlit_copper_lantern": lambda: unlit_lantern_from("copper_lantern"),

    "elder_sapling": lambda: sapling("elder"),
    "holly_sapling": lambda: sapling("holly"),
    "rowan_sapling": lambda: sapling("rowan"),
    "yew_sapling": lambda: sapling("yew"),

    "mandrake_crop_stage0": lambda: mandrake_stage(0),
    "mandrake_crop_stage1": lambda: mandrake_stage(1),
    "mandrake_crop_stage2": lambda: mandrake_stage(2),
    "mandrake_crop_stage3": lambda: mandrake_stage(3),

    "occamy_eggshell": occamy_eggshell_side,
    "occamy_eggshell_top": occamy_eggshell_top,

    "wandmakers_bench_top": bench_top,
    "wandmakers_bench_front": bench_front,
    "wandmakers_bench_side": bench_side,

    # Trunks: the bare name is the flank, plus a lock-plate front and a lid top, so
    # the model's FACING property has something to actually orient.
    "enchanted_trunk": lambda: trunk((92, 58, 44), 3),
    "enchanted_trunk_front": lambda: trunk_front((92, 58, 44), 3),
    "enchanted_trunk_top": lambda: trunk_top((92, 58, 44), 3),

    "expanded_trunk": lambda: trunk((66, 52, 96), 5),
    "expanded_trunk_front": lambda: trunk_front((66, 52, 96), 5),
    "expanded_trunk_top": lambda: trunk_top((66, 52, 96), 5),

    "masters_trunk": lambda: trunk((58, 46, 40), 9, brass=(196, 168, 96), straps=1),
    "masters_trunk_front": lambda: trunk_front((58, 46, 40), 9, brass=(196, 168, 96), straps=1),
    "masters_trunk_top": lambda: trunk_top((58, 46, 40), 9, brass=(196, 168, 96), straps=1),

    "moodys_trunk": lambda: trunk((72, 66, 58), 15, brass=(140, 142, 148)),
    "moodys_trunk_front": lambda: trunk_front((72, 66, 58), 15, brass=(140, 142, 148)),
    "moodys_trunk_top": lambda: trunk_top((72, 66, 58), 15, brass=(140, 142, 148)),

    "newts_case_item": lambda: trunk((122, 82, 46), 21, straps=1),
    "newts_case_item_front": lambda: trunk_front((122, 82, 46), 21, straps=1),
    "newts_case_item_top": lambda: trunk_top((122, 82, 46), 21, straps=1),

    "floo_fireplace": lambda: floo_fireplace(False),
    "floo_fireplace_lit": lambda: floo_fireplace(True),
    "floo_fireplace_side": floo_fireplace_side,
    "floo_fireplace_top": floo_fireplace_top,
    "spell_teacher": spell_teacher,
    "spell_teacher_top": spell_teacher_top,
    "examination_desk_top": examination_desk_top,
    "devils_snare": devils_snare,
    "mallowsweet": mallowsweet,
    "floating_candle": floating_candle,

    "brass_cauldron_top": lambda: cauldron_top((154, 125, 58), (95, 143, 107), 11),
    "wizarding_copper_cauldron_top": lambda: cauldron_top((165, 103, 60), (122, 90, 168), 23),
    "pewter_cauldron_top": lambda: cauldron_top((141, 143, 150), (107, 122, 143), 37),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped, resized = [], [], []
    for name, fn in ASSETS.items():
        if only and name not in only:
            continue
        path = os.path.join(BLOCK_DIR, name + ".png")
        if not args.force and not is_regenerable(path, MARKER):
            skipped.append(name)
            continue
        img = fn()
        # Vanilla block textures run 4-9 colours. Generated fills were landing near 50,
        # which is what made them read as rendered rather than drawn.
        img = posterise(img, 8)
        # The derived size wins. A vanilla-derived texture must keep the vanilla
        # layout or its model's UVs sample the wrong region — `unlit_lantern` shipped
        # 16x16 against `template_hanging_lantern`, which expects vanilla's 16x48.
        old = Image.open(path).size if os.path.exists(path) else None
        if old and old != img.size:
            resized.append(f"{name} {old[0]}x{old[1]} -> {img.size[0]}x{img.size[1]}")
        save(img, path, MARKER)
        written.append(name)

    print(f"wrote {len(written)} block textures")
    if resized:
        print("size corrected (model UVs depend on this):")
        for line in resized:
            print("  " + line)
    if skipped:
        print(f"skipped {len(skipped)} hand-authored: {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
