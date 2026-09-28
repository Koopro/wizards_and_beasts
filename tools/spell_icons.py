#!/usr/bin/env python3
"""Spell icons for the wand HUD.

The HUD wheel is on screen whenever a wand is held, and most of its 92x92 icons were
stand-ins: a flat diamond with a crude scribble, or nothing at all. This redraws them
in the established house style — a coloured diamond with a black keyline and a white
glyph — and takes the diamond's colour straight from each spell's own
`data/wizards_and_beasts/spells/<id>.json` `color` field, so an icon can never drift
away from the beam/particle colour the spell actually casts.

Glyphs are drawn into a mask and then outlined by dilation. Doing it that way rather
than stroking each shape twice means every glyph gets exactly the same keyline weight
no matter which primitives it is built from, which is most of what makes an icon set
look like a set.

Run from the repo root:  python tools/spell_icons.py [--force] [--only id,id]
"""

import argparse
import glob
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import (is_regenerable, marker, mix, pixelate, posterise, save,  # noqa: E402
                           shade)

SPELL_DATA = "src/main/resources/data/wizards_and_beasts/spells"
ICON_DIR = "src/main/resources/assets/wizards_and_beasts/textures/gui/sprites/wand_hud/spells"
MARKER = marker("spell_icons.py")

SIZE = 92
SS = 4          # supersample factor
S = SIZE * SS
C = S // 2      # centre

WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)


# --------------------------------------------------------------------------- glyphs
#
# Every glyph draws white shapes into an L-mask at 4x. Coordinates are in mask space,
# so helpers work in fractions of S to stay resolution-independent.

def u(f):
    """Fraction of the icon's width, in mask pixels."""
    return int(round(S * f))


def ring(d, cx, cy, r, w):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=w)


def disc(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)


def bar(d, x0, y0, x1, y1, w):
    d.line([(x0, y0), (x1, y1)], fill=255, width=w, joint="curve")


def arrow(d, x0, y0, x1, y1, w, head):
    """Line with a solid triangular head at (x1, y1)."""
    bar(d, x0, y0, x1, y1, w)
    ang = math.atan2(y1 - y0, x1 - x0)
    for side in (+1, -1):
        a = ang + side * 2.5
        d.polygon([(x1, y1),
                   (x1 + head * math.cos(ang + 2.5), y1 + head * math.sin(ang + 2.5)),
                   (x1 + head * math.cos(ang - 2.5), y1 + head * math.sin(ang - 2.5))],
                  fill=255)
        break
    d.polygon([(x1, y1),
               (x1 + head * math.cos(ang + 2.5), y1 + head * math.sin(ang + 2.5)),
               (x1 + head * math.cos(ang - 2.5), y1 + head * math.sin(ang - 2.5))],
              fill=255)


def star(d, cx, cy, r_out, r_in, points, rot=0.0):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / points
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=255)


def flame(d, cx, cy, h, w):
    d.polygon([(cx, cy - h), (cx + w, cy - h * 0.1), (cx + w * 0.6, cy + h * 0.55),
               (cx, cy + h * 0.7), (cx - w * 0.6, cy + h * 0.55), (cx - w, cy - h * 0.1)],
              fill=255)


def droplet(d, cx, cy, h, w):
    d.polygon([(cx, cy - h), (cx + w, cy + h * 0.25), (cx, cy + h * 0.85),
               (cx - w, cy + h * 0.25)], fill=255)
    disc(d, cx, cy + int(h * 0.2), int(w * 0.9))


def padlock(d, cx, cy, open_shackle):
    bw, bh = u(0.16), u(0.14)
    d.rounded_rectangle([cx - bw, cy - bh // 2, cx + bw, cy + bh + bh // 2],
                        radius=u(0.02), fill=255)
    sr = u(0.10)
    if open_shackle:
        d.arc([cx - sr - u(0.09), cy - bh // 2 - sr * 2, cx + sr - u(0.09), cy - bh // 2],
              200, 360, fill=255, width=u(0.035))
    else:
        d.arc([cx - sr, cy - bh // 2 - sr * 2, cx + sr, cy - bh // 2],
              180, 360, fill=255, width=u(0.035))


def snowflake(d, cx, cy, r, arms=6):
    w = u(0.028)
    for i in range(arms):
        a = i * math.pi / (arms / 2)
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        bar(d, cx, cy, x, y, w)
        for t in (0.55, 0.82):
            bx, by = cx + r * t * math.cos(a), cy + r * t * math.sin(a)
            for s in (+0.6, -0.6):
                bar(d, bx, by, bx + r * 0.24 * math.cos(a + s), by + r * 0.24 * math.sin(a + s), w)


def feather(d, cx, cy, h):
    w = u(0.028)
    bar(d, cx + u(0.07), cy - h, cx - u(0.05), cy + h, w)
    for i in range(7):
        t = i / 6.0
        px = cx + u(0.07) - (u(0.12) * t)
        py = cy - h + (2 * h * t)
        span = u(0.11) * (1.0 - abs(t - 0.35))
        bar(d, px, py, px - span, py - span * 0.5, w)
        bar(d, px, py, px + span * 0.8, py - span * 0.4, w)


def shield(d, cx, cy, w, h):
    d.polygon([(cx - w, cy - h), (cx + w, cy - h), (cx + w, cy + h * 0.15),
               (cx, cy + h), (cx - w, cy + h * 0.15)], fill=255)


def bolt(d, cx, cy, h, w):
    d.polygon([(cx - w * 0.1, cy - h), (cx + w, cy - h * 0.15), (cx + w * 0.15, cy - h * 0.05),
               (cx + w * 0.9, cy + h), (cx - w, cy + h * 0.1), (cx - w * 0.2, cy)], fill=255)


def crescent(d, cx, cy, r):
    m = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(m)
    md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    md.ellipse([cx - r + u(0.07), cy - r - u(0.02), cx + r + u(0.07), cy + r - u(0.02)], fill=0)
    return m


def hourglass(d, cx, cy, w, h):
    d.polygon([(cx - w, cy - h), (cx + w, cy - h), (cx - w * 0.15, cy)], fill=255)
    d.polygon([(cx - w, cy + h), (cx + w, cy + h), (cx + w * 0.15, cy)], fill=255)
    bar(d, cx - w, cy - h, cx + w, cy - h, u(0.03))
    bar(d, cx - w, cy + h, cx + w, cy + h, u(0.03))


def sparkles(d, spots):
    for (fx, fy, fr) in spots:
        star(d, u(fx), u(fy), u(fr), u(fr * 0.32), 4, rot=math.pi / 4)


# --- one function per spell -------------------------------------------------

def chevrons(d, inward):
    """Four solid chevrons around the centre. Thin arrows at 92px read as a propeller
    and made Accio and Depulso indistinguishable; solid wedges read as direction."""
    for a in (0, 90, 180, 270):
        r = math.radians(a)
        near, far = (u(0.13), u(0.30)) if inward else (u(0.30), u(0.13))
        tipr, backr = (near, far) if inward else (far, near)
        cos_a, sin_a = math.cos(r), math.sin(r)
        px, py = -sin_a, cos_a
        d.polygon([(C + tipr * cos_a, C + tipr * sin_a),
                   (C + backr * cos_a + u(0.11) * px, C + backr * sin_a + u(0.11) * py),
                   (C + backr * cos_a - u(0.11) * px, C + backr * sin_a - u(0.11) * py)],
                  fill=255)


def g_accio(d):
    chevrons(d, inward=True)
    disc(d, C, C, u(0.055))


def g_depulso(d):
    chevrons(d, inward=False)
    ring(d, C, C, u(0.06), u(0.032))


def g_flipendo(d):
    arrow(d, C - u(0.24), C + u(0.16), C + u(0.24), C - u(0.16), u(0.05), u(0.09))
    bar(d, C - u(0.26), C + u(0.03), C - u(0.10), C - u(0.09), u(0.025))


def g_aguamenti(d):
    droplet(d, C, C - u(0.06), u(0.20), u(0.13))
    for i, y in enumerate((0.70, 0.78)):
        for x0 in (0.26, 0.44, 0.62):
            d.arc([u(x0), u(y) - u(0.05), u(x0 + 0.14), u(y) + u(0.05)],
                  180, 360, fill=255, width=u(0.025))


def g_alohomora(d):
    padlock(d, C, C + u(0.04), True)
    sparkles(d, [(0.70, 0.30, 0.05)])


def g_colloportus(d):
    padlock(d, C, C + u(0.04), False)


def g_arresto_momentum(d):
    hourglass(d, C, C, u(0.17), u(0.21))


def g_capacious_extremis(d):
    d.rectangle([C - u(0.16), C - u(0.16), C + u(0.16), C + u(0.16)], outline=255, width=u(0.035))
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        arrow(d, C + dx * u(0.13), C + dy * u(0.13), C + dx * u(0.30), C + dy * u(0.30),
              u(0.03), u(0.05))


def g_claustra_reverto(d):
    d.arc([C - u(0.20), C - u(0.26), C + u(0.20), C + u(0.14)], 180, 360, fill=255, width=u(0.04))
    bar(d, C - u(0.20), C - u(0.06), C - u(0.20), C + u(0.24), u(0.04))
    bar(d, C + u(0.20), C - u(0.06), C + u(0.20), C + u(0.24), u(0.04))
    arrow(d, C + u(0.06), C + u(0.10), C - u(0.08), C + u(0.10), u(0.03), u(0.05))


def g_confringo(d):
    star(d, C, C, u(0.32), u(0.11), 8, rot=math.pi / 8)
    disc(d, C, C, u(0.07))


def g_bombarda(d):
    star(d, C, C, u(0.34), u(0.13), 6)
    ring(d, C, C, u(0.10), u(0.03))


def g_crucio(d):
    bolt(d, C, C, u(0.30), u(0.16))
    for a in (30, 150, 270):
        r = math.radians(a)
        bar(d, C + u(0.24) * math.cos(r), C + u(0.24) * math.sin(r),
            C + u(0.34) * math.cos(r), C + u(0.34) * math.sin(r), u(0.028))


def g_diffindo(d):
    # Two crossed tapered blades — a single diagonal bar just read as a stripe.
    for sx in (+1, -1):
        d.polygon([(C + sx * u(0.26), C - u(0.28)), (C + sx * u(0.16), C - u(0.28)),
                   (C - sx * u(0.06), C + u(0.16)), (C - sx * u(0.14), C + u(0.16))], fill=255)
        disc(d, C - sx * u(0.13), C + u(0.23), u(0.085))
    ring(d, C, C - u(0.04), u(0.045), u(0.03))


def g_episkey(d):
    d.rectangle([C - u(0.07), C - u(0.22), C + u(0.07), C + u(0.22)], fill=255)
    d.rectangle([C - u(0.22), C - u(0.07), C + u(0.22), C + u(0.07)], fill=255)
    sparkles(d, [(0.74, 0.28, 0.045), (0.26, 0.72, 0.035)])


def g_expelliarmus(d):
    # A wand knocked out of the hand: tapered wand flying up-right, motion arcs behind.
    d.polygon([(C - u(0.20), C + u(0.22)), (C - u(0.13), C + u(0.26)),
               (C + u(0.24), C - u(0.18)), (C + u(0.18), C - u(0.24))], fill=255)
    star(d, C + u(0.24), C - u(0.22), u(0.10), u(0.035), 4, rot=math.pi / 4)
    for r in (0.20, 0.28, 0.36):
        d.arc([C - u(0.34) - u(r) * 0.2, C - u(0.02), C - u(0.34) + u(r), C + u(0.34) + u(r)],
              250, 340, fill=255, width=u(0.028))


def g_finite_incantatem(d):
    ring(d, C, C, u(0.26), u(0.045))
    bar(d, C - u(0.19), C + u(0.19), C + u(0.19), C - u(0.19), u(0.045))


def g_frigora(d):
    snowflake(d, C, C, u(0.30))


def g_glacius(d):
    d.polygon([(C, C - u(0.30)), (C + u(0.15), C - u(0.05)), (C + u(0.09), C + u(0.28)),
               (C - u(0.09), C + u(0.28)), (C - u(0.15), C - u(0.05))], fill=255)
    bar(d, C - u(0.24), C + u(0.10), C - u(0.13), C + u(0.02), u(0.03))
    bar(d, C + u(0.24), C + u(0.10), C + u(0.13), C + u(0.02), u(0.03))


def g_incendio(d):
    flame(d, C, C + u(0.04), u(0.30), u(0.18))


def g_imperio(d):
    ring(d, C, C - u(0.02), u(0.20), u(0.035))
    disc(d, C, C - u(0.02), u(0.08))
    for x in (-0.16, 0.0, 0.16):
        bar(d, C + u(x), C - u(0.30), C + u(x), C - u(0.20), u(0.028))
    d.polygon([(C - u(0.24), C - u(0.26)), (C - u(0.12), C - u(0.34)), (C, C - u(0.26)),
               (C + u(0.12), C - u(0.34)), (C + u(0.24), C - u(0.26))], fill=255)


def g_levicorpus(d):
    arrow(d, C, C + u(0.28), C, C - u(0.24), u(0.045), u(0.08))
    d.arc([C - u(0.16), C - u(0.30), C + u(0.16), C - u(0.02)], 180, 340, fill=255, width=u(0.035))


def g_liberacorpus(d):
    arrow(d, C, C - u(0.28), C, C + u(0.24), u(0.045), u(0.08))
    d.arc([C - u(0.16), C + u(0.02), C + u(0.16), C + u(0.30)], 20, 180, fill=255, width=u(0.035))


def g_lumos(d):
    disc(d, C, C, u(0.13))
    for i in range(8):
        a = i * math.pi / 4
        bar(d, C + u(0.19) * math.cos(a), C + u(0.19) * math.sin(a),
            C + u(0.32) * math.cos(a), C + u(0.32) * math.sin(a), u(0.035))


def g_nox(d):
    return crescent(d, C + u(0.03), C, u(0.28))


def g_protego(d):
    shield(d, C, C - u(0.02), u(0.22), u(0.26))


def g_reparo(d):
    bar(d, C - u(0.26), C - u(0.20), C - u(0.04), C + u(0.02), u(0.05))
    bar(d, C + u(0.26), C + u(0.20), C + u(0.04), C - u(0.02), u(0.05))
    sparkles(d, [(0.50, 0.50, 0.09), (0.70, 0.30, 0.04)])


def g_stupefy(d):
    star(d, C, C, u(0.33), u(0.10), 4, rot=math.pi / 4)
    star(d, C, C, u(0.20), u(0.06), 4)


def g_wingardium_leviosa(d):
    # A lifted crate rather than a feather: at 92px a feather's barbs alias into
    # something closer to a fish skeleton, and a crate also separates this from
    # Levicorpus, which lifts a person.
    d.rectangle([C - u(0.17), C - u(0.28), C + u(0.17), C - u(0.02)], outline=255, width=u(0.04))
    bar(d, C - u(0.17), C - u(0.15), C + u(0.17), C - u(0.15), u(0.03))
    arrow(d, C, C + u(0.30), C, C + u(0.08), u(0.04), u(0.075))
    for x in (-0.22, 0.22):
        bar(d, C + u(x), C + u(0.26), C + u(x), C + u(0.12), u(0.025))


def g_obscurus_grasp(d):
    # A tightening spiral with clawed tips — the Obscurus seizing hold.
    pts = []
    for i in range(90):
        t = i / 89.0
        a = t * 4.4 * math.pi
        r = u(0.06) + u(0.26) * (1.0 - t)
        pts.append((C + r * math.cos(a), C + r * math.sin(a)))
    d.line(pts, fill=255, width=u(0.038), joint="curve")
    for a in (0.4, 2.5, 4.6):
        d.polygon([(C + u(0.34) * math.cos(a), C + u(0.34) * math.sin(a)),
                   (C + u(0.22) * math.cos(a + 0.22), C + u(0.22) * math.sin(a + 0.22)),
                   (C + u(0.22) * math.cos(a - 0.22), C + u(0.22) * math.sin(a - 0.22))], fill=255)


def g_placeholder(d):
    ring(d, C, C, u(0.24), u(0.04))
    d.rectangle([C - u(0.03), C - u(0.14), C + u(0.03), C + u(0.05)], fill=255)
    d.rectangle([C - u(0.03), C + u(0.10), C + u(0.03), C + u(0.16)], fill=255)


GLYPHS = {
    "accio": g_accio, "aguamenti": g_aguamenti, "alohomora": g_alohomora,
    "arresto_momentum": g_arresto_momentum, "bombarda": g_bombarda,
    "capacious_extremis": g_capacious_extremis, "claustra_reverto": g_claustra_reverto,
    "colloportus": g_colloportus, "confringo": g_confringo, "crucio": g_crucio,
    "depulso": g_depulso, "diffindo": g_diffindo, "episkey": g_episkey,
    "expelliarmus": g_expelliarmus, "finite_incantatem": g_finite_incantatem,
    "flipendo": g_flipendo, "frigora": g_frigora, "glacius": g_glacius,
    "imperio": g_imperio, "incendio": g_incendio, "levicorpus": g_levicorpus,
    "liberacorpus": g_liberacorpus, "lumos": g_lumos, "nox": g_nox,
    "obscurus_grasp": g_obscurus_grasp, "protego": g_protego, "reparo": g_reparo,
    "stupefy": g_stupefy, "wingardium_leviosa": g_wingardium_leviosa,
    "placeholder": g_placeholder,
}

# Icons with genuinely hand-drawn art. Listed rather than colour-sniffed because some
# are dark and low-contrast, which a colour-count heuristic reads as a placeholder.
HAND_DRAWN = {"avada_kedavra", "expecto_patronum", "obscurus_surge", "riddikulus"}

# Fallback diamond colours for icons whose spell has no JSON (Java-implemented spells).
FALLBACK_COLOUR = {
    "imperio": 0x4C6B2F, "obscurus_grasp": 0x3A2352, "placeholder": 0x5F5F69,
}


def spell_colour(spell_id):
    path = os.path.join(SPELL_DATA, spell_id + ".json")
    if os.path.exists(path):
        raw = json.load(open(path, encoding="utf-8")).get("color")
        if raw is not None:
            return (int(raw) & 0xFF0000) >> 16, (int(raw) & 0xFF00) >> 8, int(raw) & 0xFF
    rgb = FALLBACK_COLOUR.get(spell_id, 0x5F5F69)
    return (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF


def diamond_points(inset):
    return [(C, inset), (S - inset, C), (C, S - inset), (inset, C)]


def render_icon(spell_id):
    base = spell_colour(spell_id)
    # The stock icons sit around 45% luminance; clamp so a near-white or near-black
    # spell colour still leaves a readable field behind a white glyph.
    lum = (0.299 * base[0] + 0.587 * base[1] + 0.114 * base[2]) / 255.0
    if lum > 0.62:
        base = shade(base + (255,), 0.66)[:3]
    elif lum < 0.10:
        base = mix(base + (255,), (255, 255, 255, 255), 0.18)[:3]

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon(diamond_points(u(0.03)), fill=BLACK)
    d.polygon(diamond_points(u(0.055)), fill=base + (255,))
    # Lit top-left facet, so the plate is not a flat lozenge.
    d.line([diamond_points(u(0.075))[3], diamond_points(u(0.075))[0]],
           fill=mix(base + (255,), (255, 255, 255, 255), 0.28), width=u(0.018))
    d.line([diamond_points(u(0.075))[1], diamond_points(u(0.075))[2]],
           fill=shade(base + (255,), 0.62), width=u(0.018))

    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    produced = GLYPHS[spell_id](md)
    if produced is not None:          # glyphs that need their own mask (crescent)
        mask = produced

    outline = mask.filter(ImageFilter.MaxFilter(2 * (u(0.022) // 2) + 1))
    img.paste(Image.new("RGBA", (S, S), BLACK), (0, 0), outline)
    img.paste(Image.new("RGBA", (S, S), WHITE), (0, 0), mask)

    # Keep the glyph inside the plate.
    clip = Image.new("L", (S, S), 0)
    ImageDraw.Draw(clip).polygon(diamond_points(u(0.03)), fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (S, S), 0), clip))

    # Hard-edged downsample: a smooth filter softens the diamond keyline into a grey
    # halo, which is what made these look blurry against the HUD.
    return posterise(pixelate(img, SIZE), 8)


def main():
    # Retired 2026-09-23: tools/spell_sigils.py draws every spell now, reusing this file's glyph
    # primitives and its 29 glyphs by import. Writing from here would repaint those icons flat.
    raise SystemExit("spell_icons.py is retired as a writer -- run tools/spell_sigils.py")
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    written, skipped, missing = [], [], []
    for spell_id in sorted(GLYPHS):
        if only and spell_id not in only:
            continue
        path = os.path.join(ICON_DIR, spell_id + ".png")
        if spell_id in HAND_DRAWN or (not args.force and not is_regenerable(path, MARKER, 64)):
            skipped.append(spell_id)
            continue
        save(render_icon(spell_id), path, MARKER)
        written.append(spell_id)

    have = {os.path.basename(p)[:-4] for p in glob.glob(os.path.join(ICON_DIR, "*.png"))}
    for spell_path in glob.glob(os.path.join(SPELL_DATA, "*.json")):
        sid = os.path.basename(spell_path)[:-5]
        if sid not in have and sid not in GLYPHS:
            missing.append(sid)

    print(f"wrote {len(written)} spell icons")
    print(f"skipped {len(skipped)}: {', '.join(skipped) or '-'}")
    if missing:
        print(f"spells with no icon and no glyph: {', '.join(sorted(missing))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
