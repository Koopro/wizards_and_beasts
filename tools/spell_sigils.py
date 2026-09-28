#!/usr/bin/env python3
"""Spell icons for every spell, for Wizards & Beasts -- the wand HUD wheel and the spell menu.

`tools/spell_icons.py` drew 29 of 157 spells as flat diamonds; the rest fell back to the
placeholder. This draws all of them in one richer house style, keeping the 92px diamond the HUD
sockets are cut for:

  - a bevelled metal rim whose metal names the spell's category -- gilt for combat, silver for
    utility, steel for defence, blackened iron for the Dark Arts -- lit from the top-left
  - a field in the spell's own colour (from `data/.../spells/<id>.json`, so an icon can never
    drift from the beam the spell casts), deepening toward the edge, with an engraved ring
  - the glyph in parchment cream, keylined, over a soft glow of the spell colour

Glyphs reuse `spell_icons.py`'s primitives and its 29 finished glyphs; the other 128 are composed
here from a shared motif library, so a family of spells (the -ifors transfigurations, the revelio
family, the repairs) reads as a family.

Run from the repo root:  python tools/spell_sigils.py [--force] [--only id,id] [--sheet PNG]
"""

import argparse
import glob
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import is_regenerable, marker, save  # noqa: E402
import spell_icons as si  # noqa: E402
from spell_icons import (C, S, SIZE, bar, bolt, crescent, disc, droplet, feather, flame,  # noqa: E402
                         hourglass, padlock, ring, shield, snowflake, sparkles, star, u)

SPELL_DATA = si.SPELL_DATA
ICON_DIR = si.ICON_DIR
MARKER = marker("spell_sigils.py")

# Spells defined in Java rather than data, with the colour their effects use.
JAVA_SPELLS = {"protego": 0x5A8FD8, "expecto_patronum": 0xC8E4FF, "avada_kedavra": 0x2EB84A,
               "obscurus_grasp": 0x3A2352, "obscurus_surge": 0x4A2E6A, "imperio": 0x4C6B2F,
               "placeholder": 0x5F5F69}
JAVA_CATEGORY = {"protego": "defense", "expecto_patronum": "defense", "avada_kedavra": "dark_arts",
                 "obscurus_grasp": "dark_arts", "obscurus_surge": "dark_arts", "imperio": "dark_arts",
                 "placeholder": "utility"}

RIM = {  # (light, mid, dark) of each category's metal
    "combat": ((0xF4, 0xD8, 0x86), (0xC9, 0x97, 0x3A), (0x7A, 0x55, 0x1A)),
    "utility": ((0xEE, 0xF0, 0xF4), (0xB8, 0xBE, 0xC8), (0x6A, 0x70, 0x7C)),
    "defense": ((0xD8, 0xE6, 0xF6), (0x8F, 0xA8, 0xC8), (0x46, 0x5A, 0x78)),
    "dark_arts": ((0x6A, 0x7A, 0x5A), (0x3A, 0x34, 0x40), (0x16, 0x12, 0x1A)),
}
CREAM = (0xF6, 0xEC, 0xD0)
KEY = (0x1A, 0x14, 0x12)


# --------------------------------------------------------------------------- motif library
#
# Coordinates are fractions of the icon (0..1). Each motif draws 255 into the glyph mask `d`.

def P(fx, fy):
    return u(fx), u(fy)


def poly(d, pts):
    d.polygon([P(x, y) for x, y in pts], fill=255)


def line(d, pts, w=0.035):
    d.line([P(x, y) for x, y in pts], fill=255, width=u(w), joint="curve")


def dot(d, fx, fy, fr):
    disc(d, u(fx), u(fy), u(fr))


def circ(d, fx, fy, fr, w=0.035):
    ring(d, u(fx), u(fy), u(fr), u(w))


def arc(d, fx, fy, fr, a0, a1, w=0.035):
    d.arc([u(fx - fr), u(fy - fr), u(fx + fr), u(fy + fr)], a0, a1, fill=255, width=u(w))


def ell(d, fx, fy, rx, ry, w=None):
    box = [u(fx - rx), u(fy - ry), u(fx + rx), u(fy + ry)]
    if w:
        d.ellipse(box, outline=255, width=u(w))
    else:
        d.ellipse(box, fill=255)


def rect(d, x0, y0, x1, y1, w=None, r=0.02):
    box = [u(x0), u(y0), u(x1), u(y1)]
    if w:
        d.rounded_rectangle(box, radius=u(r), outline=255, width=u(w))
    else:
        d.rounded_rectangle(box, radius=u(r), fill=255)


def arrow(d, x0, y0, x1, y1, w=0.04, head=0.09):
    si.arrow(d, u(x0), u(y0), u(x1), u(y1), u(w), u(head))


def cut(d, fn):
    """Erase: run a drawing function into the mask with 0 instead of 255."""
    class Eraser:
        def __getattr__(self, name):
            orig = getattr(d, name)

            def call(*a, **k):
                if "fill" in k and k["fill"] == 255:
                    k["fill"] = 0
                if "outline" in k and k["outline"] == 255:
                    k["outline"] = 0
                return orig(*a, **k)
            return call
    fn(Eraser())


def person(d, fx=0.5, fy=0.5, h=0.34):
    dot(d, fx, fy - h * 0.62, h * 0.17)
    poly(d, [(fx - h * 0.22, fy - h * 0.38), (fx + h * 0.22, fy - h * 0.38),
             (fx + h * 0.28, fy + h * 0.2), (fx - h * 0.28, fy + h * 0.2)])
    line(d, [(fx - h * 0.12, fy + h * 0.2), (fx - h * 0.16, fy + h * 0.62)], h * 0.14)
    line(d, [(fx + h * 0.12, fy + h * 0.2), (fx + h * 0.16, fy + h * 0.62)], h * 0.14)


def eye(d, fx=0.5, fy=0.5, w=0.2, closed=False):
    if closed:
        arc(d, fx, fy - w * 0.6, w, 35, 145, 0.035)
        for dx in (-0.6, 0, 0.6):
            line(d, [(fx + dx * w, fy + w * 0.35), (fx + dx * w * 1.1, fy + w * 0.6)], 0.025)
        return
    ell(d, fx, fy, w, w * 0.55, 0.035)
    dot(d, fx, fy, w * 0.28)


def mouth(d, fx=0.5, fy=0.5, w=0.2):
    ell(d, fx, fy, w, w * 0.45, 0.035)
    line(d, [(fx - w, fy), (fx + w, fy)], 0.03)


def xmark(d, fx=0.5, fy=0.5, r=0.12, w=0.045):
    line(d, [(fx - r, fy - r), (fx + r, fy + r)], w)
    line(d, [(fx + r, fy - r), (fx - r, fy + r)], w)


def waves(d, fx, fy, r0, n=3, a0=-45, a1=45, step=0.07):
    for i in range(n):
        arc(d, fx, fy, r0 + i * step, a0, a1, 0.03)


def cloud(d, fx=0.5, fy=0.45, w=0.2):
    for dx, dy, r in ((-0.55, 0.1, 0.45), (0.0, -0.15, 0.6), (0.55, 0.1, 0.45)):
        dot(d, fx + dx * w, fy + dy * w, r * w)
    rect(d, fx - w, fy + 0.05 * w, fx + w, fy + 0.5 * w, r=0.02)


def heart(d, fx=0.5, fy=0.5, r=0.14):
    dot(d, fx - r * 0.5, fy - r * 0.2, r * 0.55)
    dot(d, fx + r * 0.5, fy - r * 0.2, r * 0.55)
    poly(d, [(fx - r * 1.02, fy - r * 0.05), (fx + r * 1.02, fy - r * 0.05), (fx, fy + r)])


def plus(d, fx=0.5, fy=0.5, r=0.14, w=0.07):
    line(d, [(fx - r, fy), (fx + r, fy)], w)
    line(d, [(fx, fy - r), (fx, fy + r)], w)


def flower(d, fx=0.5, fy=0.45, r=0.1, petals=6):
    for i in range(petals):
        a = i * 2 * math.pi / petals
        dot(d, fx + r * math.cos(a), fy + r * math.sin(a), r * 0.55)
    line(d, [(fx, fy + r), (fx, fy + r * 3.2)], 0.03)


def leaf(d, fx, fy, r, ang):
    a = math.radians(ang)
    tip = (fx + r * math.cos(a), fy + r * math.sin(a))
    side = (-math.sin(a) * r * 0.35, math.cos(a) * r * 0.35)
    poly(d, [(fx, fy), ((fx + tip[0]) / 2 + side[0], (fy + tip[1]) / 2 + side[1]), tip,
             ((fx + tip[0]) / 2 - side[0], (fy + tip[1]) / 2 - side[1])])


def sun(d, fx=0.5, fy=0.5, r=0.1, rays=8, ray=0.08):
    dot(d, fx, fy, r)
    for i in range(rays):
        a = i * 2 * math.pi / rays
        line(d, [(fx + (r + 0.03) * math.cos(a), fy + (r + 0.03) * math.sin(a)),
                 (fx + (r + 0.03 + ray) * math.cos(a), fy + (r + 0.03 + ray) * math.sin(a))], 0.03)


def spiral(d, fx=0.5, fy=0.5, r=0.2, turns=2.2, w=0.035):
    pts = []
    for i in range(80):
        t = i / 79
        a = t * turns * 2 * math.pi
        pts.append((fx + r * t * math.cos(a), fy + r * t * math.sin(a)))
    line(d, pts, w)


def snake(d, fx=0.5, fy=0.5, r=0.2, w=0.05):
    pts = [(fx - r + i * r / 20, fy + math.sin(i / 20 * 3 * math.pi) * r * 0.45) for i in range(41)]
    line(d, pts, w)
    dot(d, pts[-1][0] + 0.02, pts[-1][1], w * 0.9)


def bird(d, fx=0.5, fy=0.5, r=0.14):
    line(d, [(fx - r, fy - r * 0.35), (fx - r * 0.4, fy - r * 0.05), (fx, fy + r * 0.2),
             (fx + r * 0.4, fy - r * 0.05), (fx + r, fy - r * 0.35)], 0.04)


def duck(d, fx=0.5, fy=0.55):
    ell(d, fx, fy, 0.17, 0.1)
    dot(d, fx + 0.1, fy - 0.12, 0.07)
    poly(d, [(fx + 0.15, fy - 0.13), (fx + 0.24, fy - 0.1), (fx + 0.15, fy - 0.08)])


def rabbit(d, fx=0.5, fy=0.56):
    ell(d, fx, fy, 0.14, 0.11)
    dot(d, fx + 0.1, fy - 0.1, 0.07)
    ell(d, fx + 0.07, fy - 0.24, 0.025, 0.08)
    ell(d, fx + 0.13, fy - 0.24, 0.025, 0.08)


def pumpkin(d, fx=0.5, fy=0.55):
    for dx in (-0.08, 0, 0.08):
        ell(d, fx + dx, fy, 0.09, 0.12)
    line(d, [(fx, fy - 0.12), (fx + 0.03, fy - 0.2)], 0.035)


def tentacle(d, fx=0.5, fy=0.5):
    pts = [(fx - 0.15 + i * 0.015, fy + 0.2 - i * 0.02 - math.sin(i / 20 * math.pi) * 0.1) for i in range(21)]
    for i, (x, y) in enumerate(pts[:-1]):
        line(d, [(x, y), pts[i + 1]], 0.07 - i * 0.003)
    for i in (4, 9, 14):
        dot(d, pts[i][0] + 0.03, pts[i][1] + 0.02, 0.012)


def insect(d, fx=0.5, fy=0.5):
    ell(d, fx, fy + 0.04, 0.06, 0.1)
    dot(d, fx, fy - 0.1, 0.05)
    for s in (-1, 1):
        for dy in (-0.03, 0.03, 0.09):
            line(d, [(fx, fy + dy), (fx + s * 0.16, fy + dy - 0.05)], 0.025)


def skull(d, fx=0.5, fy=0.47, r=0.16):
    dot(d, fx, fy, r)
    rect(d, fx - r * 0.55, fy + r * 0.5, fx + r * 0.55, fy + r * 1.05, r=0.01)
    cut(d, lambda e: (disc(e, u(fx - r * 0.42), u(fy), u(r * 0.25)),
                      disc(e, u(fx + r * 0.42), u(fy), u(r * 0.25))))


def tooth(d, fx=0.5, fy=0.5, r=0.12):
    poly(d, [(fx - r, fy - r), (fx + r, fy - r), (fx + r * 0.8, fy + r * 0.4), (fx + r * 0.35, fy + r * 1.1),
             (fx, fy + r * 0.45), (fx - r * 0.35, fy + r * 1.1), (fx - r * 0.8, fy + r * 0.4)])


def bone(d, x0, y0, x1, y1, w=0.05):
    line(d, [(x0, y0), (x1, y1)], w)
    for x, y in ((x0, y0), (x1, y1)):
        dot(d, x - 0.02, y, w * 0.7)
        dot(d, x + 0.02, y, w * 0.7)


def door(d, fx=0.5, fy=0.5, open_=False):
    rect(d, fx - 0.13, fy - 0.2, fx + 0.13, fy + 0.22, w=0.035, r=0.06)
    if open_:
        poly(d, [(fx - 0.13, fy - 0.18), (fx + 0.02, fy - 0.12), (fx + 0.02, fy + 0.26), (fx - 0.13, fy + 0.2)])
    else:
        dot(d, fx + 0.07, fy + 0.03, 0.022)


def boxes(d, fx, fy, r, w=0.035):
    rect(d, fx - r, fy - r, fx + r, fy + r, w=w, r=0.01)


def footprints(d):
    for i, (x, y) in enumerate(((0.38, 0.66), (0.52, 0.56), (0.42, 0.44), (0.58, 0.34))):
        ell(d, x, y, 0.035, 0.055)
        dot(d, x, y - 0.075, 0.018)


def notes(d, fx=0.5, fy=0.5):
    for dx, dy in ((-0.08, 0.06), (0.08, 0.0)):
        ell(d, fx + dx, fy + dy + 0.1, 0.05, 0.035)
        line(d, [(fx + dx + 0.045, fy + dy + 0.1), (fx + dx + 0.045, fy + dy - 0.12)], 0.03)
    line(d, [(fx - 0.035, fy - 0.08), (fx + 0.125, fy - 0.14)], 0.045)


def speaker(d, fx=0.4, fy=0.5):
    poly(d, [(fx - 0.1, fy - 0.05), (fx - 0.03, fy - 0.05), (fx + 0.06, fy - 0.13),
             (fx + 0.06, fy + 0.13), (fx - 0.03, fy + 0.05), (fx - 0.1, fy + 0.05)])


def ear(d, fx=0.5, fy=0.5):
    arc(d, fx, fy, 0.14, 180, 440 - 360 + 360, 0.045)
    arc(d, fx + 0.02, fy + 0.02, 0.06, 180, 360, 0.035)


def chain(d, x0, y0, x1, y1, links=4):
    for i in range(links):
        t = (i + 0.5) / links
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        if i % 2:
            ell(d, x, y, 0.05, 0.03, 0.025)
        else:
            ell(d, x, y, 0.03, 0.05, 0.025)


def ropes(d):
    for dy in (-0.1, 0.0, 0.1):
        arc(d, 0.5, 0.5 + dy, 0.18, 200, 340, 0.04)


def cage(d):
    rect(d, 0.32, 0.3, 0.68, 0.72, w=0.035, r=0.03)
    for x in (0.41, 0.5, 0.59):
        line(d, [(x, 0.3), (x, 0.72)], 0.025)
    arc(d, 0.5, 0.3, 0.18, 180, 360, 0.035)


def dome(d, fx=0.5, fy=0.64, r=0.22):
    arc(d, fx, fy, r, 180, 360, 0.045)
    line(d, [(fx - r - 0.02, fy), (fx + r + 0.02, fy)], 0.04)


def house(d, fx=0.5, fy=0.5):
    poly(d, [(fx - 0.16, fy - 0.02), (fx, fy - 0.17), (fx + 0.16, fy - 0.02)])
    rect(d, fx - 0.12, fy - 0.02, fx + 0.12, fy + 0.17, r=0.005)
    cut(d, lambda e: e.rectangle([u(fx - 0.03), u(fy + 0.06), u(fx + 0.03), u(fy + 0.17)], fill=255))


def umbrella(d):
    d.pieslice([u(0.28), u(0.3), u(0.72), u(0.66)], 180, 360, fill=255)
    line(d, [(0.5, 0.48), (0.5, 0.68)], 0.035)
    arc(d, 0.46, 0.68, 0.04, 0, 180, 0.035)


def compass(d):
    circ(d, 0.5, 0.5, 0.2, 0.035)
    poly(d, [(0.5, 0.33), (0.54, 0.5), (0.5, 0.67), (0.46, 0.5)])


def boot(d):
    poly(d, [(0.4, 0.3), (0.52, 0.3), (0.52, 0.56), (0.66, 0.6), (0.68, 0.7), (0.38, 0.7)])


def trunk(d):
    rect(d, 0.3, 0.4, 0.7, 0.68, r=0.02)
    cut(d, lambda e: e.rectangle([u(0.3), u(0.48), u(0.7), u(0.5)], fill=255))
    rect(d, 0.46, 0.46, 0.54, 0.54, r=0.01)
    arc(d, 0.5, 0.4, 0.06, 180, 360, 0.03)


def wand(d, x0=0.3, y0=0.7, x1=0.66, y1=0.34):
    line(d, [(x0, y0), (x1, y1)], 0.035)
    line(d, [(x0, y0), (x0 + (x1 - x0) * 0.3, y0 + (y1 - y0) * 0.3)], 0.055)


def burst(d, fx=0.5, fy=0.5, r=0.24, points=10, inner=0.45):
    star(d, u(fx), u(fy), u(r), u(r * inner), points)


def figure_float(d, up=True):
    person(d, 0.5, 0.52 if up else 0.48, 0.3)
    if up:
        arrow(d, 0.3, 0.62, 0.3, 0.3)
        arrow(d, 0.7, 0.62, 0.7, 0.3)
    else:
        arrow(d, 0.3, 0.3, 0.3, 0.62)
        arrow(d, 0.7, 0.3, 0.7, 0.62)


def grow(d, inward):
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        a0, a1 = (0.5 + sx * 0.32, 0.5 + sy * 0.32), (0.5 + sx * 0.14, 0.5 + sy * 0.14)
        if inward:
            arrow(d, a0[0], a0[1], a1[0], a1[1], 0.035, 0.07)
        else:
            arrow(d, a1[0], a1[1], a0[0], a0[1], 0.035, 0.07)


def sparks(d, n=6, r=0.24, seed=1):
    import random
    rng = random.Random(seed)
    for i in range(n):
        a = i * 2 * math.pi / n + rng.random() * 0.4
        rr = r * (0.7 + rng.random() * 0.3)
        star(d, u(0.5 + rr * math.cos(a)), u(0.5 + rr * math.sin(a)), u(0.05), u(0.016), 4, rot=math.pi / 4)


# --------------------------------------------------------------------------- one glyph per spell

G = {}


def glyph(*names):
    def reg(fn):
        for n in names:
            G[n] = fn
        return fn
    return reg


# -- revealing, seeing, knowing
@glyph("revelio")
def _(d): eye(d, 0.5, 0.5, 0.22); sparkles(d, [(0.28, 0.28, 0.05), (0.73, 0.3, 0.04)])
@glyph("homenum_revelio")
def _(d): eye(d, 0.5, 0.36, 0.17); person(d, 0.5, 0.64, 0.22)
@glyph("specialis_revelio")
def _(d): eye(d, 0.5, 0.5, 0.2); sparkles(d, [(0.3, 0.3, 0.05), (0.7, 0.3, 0.05), (0.5, 0.74, 0.05)])
@glyph("aparecium")
def _(d):
    eye(d, 0.5, 0.36, 0.16)
    for y in (0.58, 0.66, 0.74):
        line(d, [(0.34, y), (0.66, y)], 0.03)
@glyph("appare_vestigium")
def _(d): footprints(d); sparkles(d, [(0.7, 0.68, 0.04)])
@glyph("legilimens")
def _(d): dot(d, 0.5, 0.46, 0.17); cut(d, lambda e: spiral(e, 0.5, 0.46, 0.13, 2.2, 0.03)); eye(d, 0.5, 0.74, 0.1)
@glyph("prior_incantato")
def _(d): wand(d, 0.3, 0.72, 0.56, 0.46); waves(d, 0.56, 0.46, 0.08, 3, -80, 10)
@glyph("point_me")
def _(d): compass(d)
@glyph("periculum")
def _(d): arrow(d, 0.5, 0.74, 0.5, 0.36); burst(d, 0.5, 0.3, 0.12, 8)
@glyph("oculus_reparo")
def _(d):
    circ(d, 0.38, 0.5, 0.1); circ(d, 0.62, 0.5, 0.1); arc(d, 0.5, 0.52, 0.04, 200, 340, 0.03)
    sparkles(d, [(0.5, 0.28, 0.05)])
@glyph("obliviate")
def _(d):
    dot(d, 0.5, 0.46, 0.18)
    cut(d, lambda e: spiral(e, 0.5, 0.46, 0.14, 2.4, 0.035))
    line(d, [(0.3, 0.74), (0.7, 0.74)], 0.03)

# -- movement, lifting, pushing
@glyph("ascendio")
def _(d): figure_float(d, True)
@glyph("descendo")
def _(d): arrow(d, 0.5, 0.26, 0.5, 0.72, 0.05, 0.12); line(d, [(0.3, 0.76), (0.7, 0.76)], 0.035)
@glyph("levioso")
def _(d): feather(d, u(0.5), u(0.46), u(0.2)); arrow(d, 0.72, 0.7, 0.72, 0.34, 0.03, 0.07)
@glyph("locomotor")
def _(d): trunk(d); arrow(d, 0.24, 0.3, 0.44, 0.3, 0.03, 0.06)
@glyph("mobiliarbus")
def _(d):
    dot(d, 0.5, 0.38, 0.14); line(d, [(0.5, 0.5), (0.5, 0.72)], 0.05)
    arrow(d, 0.26, 0.74, 0.74, 0.74, 0.03, 0.06)
@glyph("mobilicorpus")
def _(d): person(d, 0.46, 0.5, 0.3); arrow(d, 0.62, 0.66, 0.78, 0.5, 0.03, 0.06)
@glyph("carpe_retractum")
def _(d): line(d, [(0.26, 0.5), (0.62, 0.5)], 0.035); arrow(d, 0.74, 0.34, 0.32, 0.34, 0.03, 0.07); dot(d, 0.7, 0.5, 0.05)
@glyph("relashio")
def _(d):
    for i in range(5):
        a = math.radians(-150 + i * 30)
        line(d, [(0.5, 0.6), (0.5 + 0.2 * math.cos(a), 0.6 + 0.2 * math.sin(a))], 0.04)
    ell(d, 0.5, 0.64, 0.1, 0.07)
@glyph("expulso", "reducto")
def _(d): burst(d, 0.5, 0.5, 0.26, 12, 0.4)
@glyph("bombarda_maxima")
def _(d): burst(d, 0.5, 0.5, 0.29, 14, 0.35); cut(d, lambda e: star(e, u(0.5), u(0.5), u(0.1), u(0.04), 6))
@glyph("everte_statum")
def _(d): person(d, 0.42, 0.52, 0.26); arc(d, 0.5, 0.5, 0.24, 280, 60, 0.035); arrow(d, 0.7, 0.62, 0.72, 0.66, 0.03, 0.07)
@glyph("alarte_ascendare")
def _(d): person(d, 0.5, 0.6, 0.24); arrow(d, 0.5, 0.3, 0.5, 0.14, 0.04, 0.08)
@glyph("deprimo")
def _(d): arrow(d, 0.5, 0.2, 0.5, 0.54, 0.06, 0.13); line(d, [(0.26, 0.64), (0.74, 0.64)], 0.05); line(d, [(0.34, 0.74), (0.66, 0.74)], 0.03)
@glyph("ventus")
def _(d):
    for dy, l in ((-0.12, 0.34), (0.0, 0.42), (0.12, 0.3)):
        line(d, [(0.28, 0.5 + dy), (0.28 + l, 0.5 + dy)], 0.035)
        arc(d, 0.28 + l, 0.5 + dy - 0.05, 0.05, 270, 450 - 360 + 360, 0.035)
@glyph("glisseo")
def _(d):
    for i in range(4):
        line(d, [(0.3 + i * 0.1, 0.34 + i * 0.1), (0.4 + i * 0.1, 0.34 + i * 0.1)], 0.035)
    line(d, [(0.26, 0.4), (0.62, 0.76)], 0.04)
@glyph("oppugno")
def _(d): bird(d, 0.36, 0.38, 0.1); bird(d, 0.58, 0.48, 0.1); bird(d, 0.4, 0.62, 0.1); arrow(d, 0.62, 0.66, 0.76, 0.56, 0.03, 0.06)
@glyph("piertotum_locomotor")
def _(d):
    person(d, 0.5, 0.52, 0.34)
    line(d, [(0.34, 0.38), (0.66, 0.38)], 0.05)
    line(d, [(0.72, 0.26), (0.72, 0.72)], 0.03)
@glyph("tarantallegra")
def _(d):
    line(d, [(0.44, 0.3), (0.36, 0.52), (0.42, 0.7)], 0.05); line(d, [(0.56, 0.3), (0.66, 0.5), (0.58, 0.7)], 0.05)
    notes(d, 0.66, 0.26) if False else sparkles(d, [(0.3, 0.3, 0.04), (0.72, 0.72, 0.04)])
@glyph("locomotor_wibbly")
def _(d):
    for x in (0.42, 0.58):
        line(d, [(x + 0.05 * math.sin(i) * (1 if x < 0.5 else -1), 0.28 + i * 0.05) for i in range(10)], 0.05)
@glyph("locomotor_mortis")
def _(d):
    line(d, [(0.42, 0.28), (0.42, 0.72)], 0.06); line(d, [(0.58, 0.28), (0.58, 0.72)], 0.06)
    line(d, [(0.32, 0.5), (0.68, 0.5)], 0.05)
@glyph("avenseguim")
def _(d): bird(d, 0.4, 0.44, 0.12); arrow(d, 0.3, 0.66, 0.74, 0.5, 0.03, 0.07)
@glyph("portus")
def _(d): boot(d); sparkles(d, [(0.3, 0.3, 0.05), (0.72, 0.36, 0.04)])
@glyph("pack")
def _(d): trunk(d); arrow(d, 0.5, 0.2, 0.5, 0.36, 0.03, 0.06)
@glyph("reverte")
def _(d): arc(d, 0.5, 0.5, 0.2, 40, 330, 0.045); arrow(d, 0.64, 0.66, 0.68, 0.64, 0.04, 0.09)
@glyph("partis_temporus")
def _(d): line(d, [(0.4, 0.26), (0.36, 0.74)], 0.035); line(d, [(0.6, 0.26), (0.64, 0.74)], 0.035); arrow(d, 0.5, 0.74, 0.5, 0.3, 0.03, 0.07)
@glyph("dissendium")
def _(d): door(d, 0.5, 0.5, True)
@glyph("aberto")
def _(d): door(d, 0.5, 0.5, True); sparkles(d, [(0.74, 0.32, 0.04)])
@glyph("finestra")
def _(d):
    rect(d, 0.3, 0.3, 0.7, 0.7, w=0.035, r=0.01)
    line(d, [(0.5, 0.3), (0.5, 0.7)], 0.025); line(d, [(0.3, 0.5), (0.7, 0.5)], 0.025)
    line(d, [(0.36, 0.36), (0.46, 0.46), (0.42, 0.52), (0.64, 0.66)], 0.03)

# -- binding, stopping
@glyph("incarcerous")
def _(d): person(d, 0.5, 0.5, 0.3); cut(d, lambda e: None); ropes(d)
@glyph("brachiabindo")
def _(d): person(d, 0.5, 0.5, 0.32); line(d, [(0.3, 0.46), (0.7, 0.46)], 0.04); line(d, [(0.3, 0.54), (0.7, 0.54)], 0.04)
@glyph("incarcifors")
def _(d): cage(d)
@glyph("immobulus")
def _(d): person(d, 0.5, 0.52, 0.3); snowflake(d, u(0.74), u(0.28), u(0.08))
@glyph("petrificus_totalus")
def _(d): rect(d, 0.36, 0.22, 0.64, 0.78, w=0.04, r=0.03); person(d, 0.5, 0.52, 0.24)
@glyph("impedimenta")
def _(d):
    for x in (0.34, 0.5, 0.66):
        line(d, [(x - 0.06, 0.34), (x + 0.04, 0.5), (x - 0.06, 0.66)], 0.04)
    line(d, [(0.76, 0.3), (0.76, 0.7)], 0.05)
@glyph("langlock")
def _(d): mouth(d, 0.5, 0.44, 0.18); padlock(d, u(0.5), u(0.66), False)
@glyph("mimblewimble")
def _(d): mouth(d, 0.5, 0.4, 0.18); spiral(d, 0.5, 0.64, 0.1, 1.6, 0.035)
@glyph("silencio")
def _(d): mouth(d, 0.5, 0.5, 0.2); xmark(d, 0.5, 0.5, 0.2, 0.04)
@glyph("quietus")
def _(d): speaker(d); line(d, [(0.62, 0.42), (0.74, 0.58)], 0.035); line(d, [(0.74, 0.42), (0.62, 0.58)], 0.035)
@glyph("sonorus")
def _(d): speaker(d); waves(d, 0.48, 0.5, 0.1, 3, -50, 50)
@glyph("muffliato")
def _(d): ear(d, 0.5, 0.48); waves(d, 0.5, 0.48, 0.2, 2, 200, 340, 0.06)

# -- the -ifors transfigurations and other bodily jinxes
@glyph("ducklifors")
def _(d): duck(d)
@glyph("avis")
def _(d): bird(d, 0.36, 0.4, 0.1); bird(d, 0.6, 0.34, 0.12); bird(d, 0.52, 0.6, 0.1)
@glyph("pullus")
def _(d):
    dot(d, 0.5, 0.56, 0.14); dot(d, 0.58, 0.38, 0.08)
    poly(d, [(0.64, 0.38), (0.72, 0.4), (0.64, 0.42)])
    line(d, [(0.46, 0.7), (0.44, 0.78)], 0.025); line(d, [(0.54, 0.7), (0.56, 0.78)], 0.025)
@glyph("flintifors")
def _(d): rect(d, 0.34, 0.5, 0.66, 0.7, r=0.01); flame(d, u(0.5), u(0.38), u(0.13), u(0.07))
@glyph("herbifors")
def _(d): flower(d, 0.5, 0.4, 0.09, 6); leaf(d, 0.5, 0.64, 0.12, -30)
@glyph("lapifors")
def _(d): rabbit(d)
@glyph("melofors")
def _(d): pumpkin(d)
@glyph("tentaclifors")
def _(d): tentacle(d)
@glyph("entomorphis")
def _(d): insect(d)
@glyph("serpensortia")
def _(d): snake(d)
@glyph("vipera_evanesca")
def _(d): snake(d, 0.5, 0.5, 0.18); xmark(d, 0.72, 0.28, 0.07, 0.035)
@glyph("colloshoo")
def _(d): boot(d); line(d, [(0.34, 0.76), (0.72, 0.76)], 0.035); line(d, [(0.4, 0.8), (0.46, 0.76)], 0.02)
@glyph("slugulus_eructo")
def _(d): ell(d, 0.52, 0.6, 0.18, 0.07); dot(d, 0.34, 0.54, 0.06); line(d, [(0.32, 0.5), (0.28, 0.42)], 0.02); mouth(d, 0.5, 0.34, 0.1)
@glyph("furnunculus")
def _(d): dot(d, 0.5, 0.5, 0.2); cut(d, lambda e: None); [dot(d, x, y, 0.045) for x, y in ((0.4, 0.4), (0.6, 0.46), (0.46, 0.6))]
@glyph("ebublio")
def _(d): [circ(d, x, y, r, 0.03) for x, y, r in ((0.42, 0.56, 0.12), (0.6, 0.38, 0.09), (0.64, 0.64, 0.06))]
@glyph("anteoculatia")
def _(d):
    dot(d, 0.5, 0.6, 0.12)
    for s in (-1, 1):
        line(d, [(0.5 + s * 0.08, 0.5), (0.5 + s * 0.2, 0.28)], 0.035)
        line(d, [(0.5 + s * 0.15, 0.38), (0.5 + s * 0.26, 0.36)], 0.03)
@glyph("calvorio")
def _(d): dot(d, 0.5, 0.5, 0.18); arc(d, 0.5, 0.5, 0.24, 200, 340, 0.03); sparkles(d, [(0.62, 0.4, 0.04)])
@glyph("densaugeo")
def _(d): tooth(d, 0.5, 0.52, 0.12); arrow(d, 0.72, 0.64, 0.72, 0.3, 0.03, 0.07)
@glyph("engorgio_skullus")
def _(d): skull(d, 0.5, 0.5, 0.13); grow(d, False)
@glyph("redactum_skullus")
def _(d): skull(d, 0.5, 0.5, 0.1); grow(d, True)
@glyph("mutatio_skullus")
def _(d): skull(d, 0.42, 0.5, 0.12); arc(d, 0.66, 0.5, 0.1, 270, 450, 0.035); arrow(d, 0.66, 0.6, 0.6, 0.6, 0.03, 0.06)
@glyph("mucus_ad_nauseam")
def _(d): droplet(d, u(0.5), u(0.46), u(0.18), u(0.1)); droplet(d, u(0.7), u(0.62), u(0.08), u(0.045))
@glyph("steleus")
def _(d): dot(d, 0.4, 0.5, 0.14); waves(d, 0.44, 0.5, 0.16, 3, -40, 40, 0.06)
@glyph("rictusempra", "titillando")
def _(d): feather(d, u(0.46), u(0.5), u(0.22)); arc(d, 0.66, 0.46, 0.08, 20, 160, 0.03)
@glyph("cantis")
def _(d): notes(d)
@glyph("baubillious")
def _(d): bolt(d, u(0.5), u(0.5), u(0.22), u(0.12)); sparkles(d, [(0.3, 0.3, 0.04), (0.72, 0.7, 0.04)])
@glyph("confundo")
def _(d): spiral(d, 0.5, 0.5, 0.2, 2.4, 0.035); sparkles(d, [(0.3, 0.28, 0.04), (0.74, 0.3, 0.035), (0.28, 0.72, 0.035)])
@glyph("obscuro")
def _(d): eye(d, 0.5, 0.5, 0.2, closed=True); line(d, [(0.24, 0.42), (0.76, 0.42)], 0.07)
@glyph("sectumsempra")
def _(d):
    for dy in (-0.12, 0.0, 0.12):
        line(d, [(0.3, 0.5 + dy + 0.08), (0.7, 0.5 + dy - 0.08)], 0.035)
@glyph("morsmordre")
def _(d): skull(d, 0.5, 0.42, 0.13); snake(d, 0.5, 0.72, 0.16, 0.035)
@glyph("protego_diabolica")
def _(d):
    for i in range(8):
        a = i * math.pi / 4
        flame(d, u(0.5 + 0.22 * math.cos(a)), u(0.5 + 0.22 * math.sin(a)), u(0.07), u(0.04))
    circ(d, 0.5, 0.5, 0.1, 0.035)
@glyph("waddiwasi")
def _(d): ell(d, 0.4, 0.46, 0.1, 0.08); arrow(d, 0.5, 0.52, 0.74, 0.68, 0.035, 0.07)
@glyph("flagrate")
def _(d):
    line(d, [(0.3, 0.64), (0.4, 0.44), (0.5, 0.58), (0.6, 0.4), (0.7, 0.56)], 0.04)
    flame(d, u(0.7), u(0.36), u(0.08), u(0.04))
@glyph("verdimillious", "vermillious")
def _(d): sparks(d, 7, 0.24, seed=3); dot(d, 0.5, 0.5, 0.05)
@glyph("fumos", "nebulus")
def _(d): cloud(d, 0.5, 0.46, 0.2); cloud(d, 0.44, 0.66, 0.12)
@glyph("meteolojinx_recanto")
def _(d):
    cloud(d, 0.5, 0.4, 0.18)
    for x in (0.4, 0.5, 0.6):
        line(d, [(x, 0.58), (x - 0.03, 0.68)], 0.03)
    xmark(d, 0.72, 0.7, 0.06, 0.03)
@glyph("peskipiksi_pesternomi")
def _(d):
    person(d, 0.5, 0.54, 0.22)
    for s in (-1, 1):
        ell(d, 0.5 + s * 0.12, 0.44, 0.08, 0.05, 0.03)
    xmark(d, 0.74, 0.26, 0.06, 0.03)
@glyph("steleus_",)  # unused alias guard
def _(d): pass

# -- growth, size, copies, vanishing
@glyph("engorgio")
def _(d): boxes(d, 0.5, 0.5, 0.1); grow(d, False)
@glyph("diminuendo", "reducio")
def _(d): boxes(d, 0.5, 0.5, 0.06); grow(d, True)
@glyph("geminio")
def _(d): boxes(d, 0.44, 0.46, 0.12); boxes(d, 0.58, 0.58, 0.12)
@glyph("evanesco")
def _(d):
    for i in range(12):
        a = i * math.pi / 6
        dot(d, 0.5 + 0.18 * math.cos(a), 0.5 + 0.18 * math.sin(a), 0.025)
    sparkles(d, [(0.5, 0.5, 0.06)])
@glyph("deletrius")
def _(d): rect(d, 0.34, 0.3, 0.66, 0.7, w=0.035, r=0.01); xmark(d, 0.5, 0.5, 0.1, 0.04)
@glyph("herbivicus", "orchideous")
def _(d): flower(d, 0.4, 0.38, 0.07, 5); flower(d, 0.6, 0.44, 0.08, 6)
@glyph("defodio")
def _(d): line(d, [(0.34, 0.7), (0.62, 0.38)], 0.04); arc(d, 0.62, 0.46, 0.14, 200, 340, 0.05)
@glyph("colovaria")
def _(d):
    ell(d, 0.5, 0.5, 0.22, 0.17, 0.04)
    for x, y in ((0.4, 0.42), (0.52, 0.38), (0.62, 0.46), (0.44, 0.56)):
        dot(d, x, y, 0.035)
@glyph("duro")
def _(d): poly(d, [(0.34, 0.66), (0.4, 0.36), (0.56, 0.3), (0.68, 0.44), (0.64, 0.68)]); cut(d, lambda e: e.line([P(0.42, 0.5), P(0.56, 0.44)], fill=255, width=u(0.025)))
@glyph("spongify", "molliare")
def _(d):
    rect(d, 0.3, 0.38, 0.7, 0.66, r=0.05)
    cut(d, lambda e: [disc(e, u(x), u(y), u(0.03)) for x, y in ((0.4, 0.48), (0.54, 0.44), (0.6, 0.58), (0.46, 0.58))])
@glyph("impervius")
def _(d): umbrella(d); [line(d, [(x, 0.2), (x - 0.02, 0.26)], 0.02) for x in (0.32, 0.5, 0.68)]
@glyph("epoximise")
def _(d): boxes(d, 0.4, 0.5, 0.09); boxes(d, 0.6, 0.5, 0.09); droplet(d, u(0.5), u(0.3), u(0.07), u(0.04))
@glyph("erecto")
def _(d): poly(d, [(0.28, 0.72), (0.5, 0.3), (0.72, 0.72)]); cut(d, lambda e: e.polygon([P(0.44, 0.72), P(0.5, 0.56), P(0.56, 0.72)], fill=255))
@glyph("scourgify", "tergeo")
def _(d): [circ(d, x, y, r, 0.03) for x, y, r in ((0.4, 0.4, 0.08), (0.6, 0.46, 0.1), (0.46, 0.64, 0.07))]; sparkles(d, [(0.7, 0.28, 0.04)])

# -- repairing, healing
@glyph("reparifarge")
def _(d): spiral(d, 0.5, 0.5, 0.18, 1.8, 0.035); arrow(d, 0.36, 0.72, 0.28, 0.62, 0.03, 0.07)
@glyph("reparifors")
def _(d): heart(d, 0.5, 0.52, 0.14); plus(d, 0.72, 0.3, 0.06, 0.03)
@glyph("papyrus_reparo")
def _(d):
    rect(d, 0.34, 0.26, 0.66, 0.74, w=0.035, r=0.01)
    line(d, [(0.34, 0.5), (0.46, 0.46), (0.56, 0.54), (0.66, 0.5)], 0.03)
@glyph("brackium_emendo")
def _(d): bone(d, 0.32, 0.66, 0.68, 0.34, 0.05); sparkles(d, [(0.7, 0.66, 0.05)])
@glyph("ferula")
def _(d): bone(d, 0.32, 0.5, 0.68, 0.5, 0.05); [line(d, [(x, 0.4), (x, 0.6)], 0.03) for x in (0.42, 0.5, 0.58)]
@glyph("vulnera_sanentur")
def _(d): heart(d, 0.5, 0.52, 0.18); cut(d, lambda e: [e.line([P(x - 0.03, 0.46), P(x + 0.03, 0.52)], fill=255, width=u(0.02)) for x in (0.42, 0.5, 0.58)])
@glyph("rennervate")
def _(d): heart(d, 0.5, 0.56, 0.13); bolt(d, u(0.5), u(0.28), u(0.1), u(0.06))
@glyph("anapneo")
def _(d): ell(d, 0.42, 0.56, 0.08, 0.14); ell(d, 0.58, 0.56, 0.08, 0.14); line(d, [(0.5, 0.26), (0.5, 0.44)], 0.035)

# -- light, fire, water, weather
@glyph("lumos_maxima")
def _(d): sun(d, 0.5, 0.5, 0.12, 12, 0.1)

# -- wards and guards
@glyph("cave_inimicum")
def _(d): dome(d); eye(d, 0.5, 0.54, 0.08)
@glyph("repello_inimicum")
def _(d): dome(d); arrow(d, 0.5, 0.34, 0.5, 0.18, 0.03, 0.07)
@glyph("repello_muggletum")
def _(d): house(d, 0.5, 0.56); dome(d, 0.5, 0.74, 0.28)
@glyph("salvio_hexia")
def _(d): shield(d, u(0.5), u(0.48), u(0.17), u(0.22)); cut(d, lambda e: star(e, u(0.5), u(0.46), u(0.08), u(0.035), 5, -math.pi / 2))
@glyph("fianto_duri")
def _(d): shield(d, u(0.5), u(0.48), u(0.17), u(0.22)); cut(d, lambda e: e.line([P(0.5, 0.34), P(0.5, 0.62)], fill=255, width=u(0.04)))
@glyph("surgito")
def _(d): person(d, 0.5, 0.54, 0.26); arc(d, 0.5, 0.54, 0.24, 180, 360, 0.03); sparkles(d, [(0.3, 0.3, 0.04), (0.7, 0.3, 0.04)])

# -- the Java-defined spells, redrawn in the house style
@glyph("avada_kedavra")
def _(d): skull(d, 0.5, 0.46, 0.17)
@glyph("expecto_patronum")
def _(d):
    ell(d, 0.5, 0.58, 0.16, 0.09)
    dot(d, 0.64, 0.44, 0.07)
    for s in (0.6, 0.7):
        line(d, [(s, 0.4), (s + 0.04, 0.24)], 0.03)
        line(d, [(s + 0.02, 0.32), (s + 0.08, 0.28)], 0.025)
    for x in (0.38, 0.46, 0.56, 0.62):
        line(d, [(x, 0.64), (x, 0.76)], 0.03)
@glyph("riddikulus")
def _(d):
    dot(d, 0.5, 0.5, 0.2)
    cut(d, lambda e: (disc(e, u(0.42), u(0.44), u(0.03)), disc(e, u(0.58), u(0.44), u(0.03)),
                      e.pieslice([u(0.36), u(0.44), u(0.64), u(0.66)], 0, 180, fill=255)))
@glyph("obscurus_surge")
def _(d): spiral(d, 0.5, 0.5, 0.24, 2.6, 0.05)


# The 29 glyphs spell_icons.py already drew, reused as they are.
for _name, _fn in si.GLYPHS.items():
    G.setdefault(_name, _fn)
G.pop("steleus_", None)


# --------------------------------------------------------------------------- frame


# The corpus registration gave every not-yet-built spell one of two stand-in colours, so 117 icons
# would share a field. Those two values are not a spell's colour; for them the icon takes a hue of
# its own inside its category's band, stable per id. A spell that gets a real colour keeps it.
PLACEHOLDER_COLOURS = {0xFFFFAA, 0xFF5555}
HUE_BANDS = {"utility": (70, 230), "combat": (-30, 45), "defense": (190, 235), "dark_arts": (260, 320)}


def _stand_in(spell_id, category):
    import colorsys
    import zlib
    h = zlib.crc32(spell_id.encode()) / 0xFFFFFFFF
    lo, hi = HUE_BANDS.get(category, (0, 360))
    hue = ((lo + (hi - lo) * h) % 360) / 360
    sat = 0.45 + 0.25 * ((zlib.crc32((spell_id + "s").encode()) & 0xFF) / 255)
    r, g, b = colorsys.hsv_to_rgb(hue, sat, 0.68)
    return int(r * 255), int(g * 255), int(b * 255)


def spell_meta(spell_id):
    path = os.path.join(SPELL_DATA, spell_id + ".json")
    if os.path.exists(path):
        data = json.load(open(path, encoding="utf-8"))
        raw = int(data.get("color", 0x5F5F69))
        category = data.get("category", "utility")
        if raw & 0xFFFFFF in PLACEHOLDER_COLOURS:
            return _stand_in(spell_id, category), category
        return ((raw >> 16) & 0xFF, (raw >> 8) & 0xFF, raw & 0xFF), category
    rgb = JAVA_SPELLS.get(spell_id, 0x5F5F69)
    return ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF), JAVA_CATEGORY.get(spell_id, "utility")


def _mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def diamond(inset):
    return [(C, inset), (S - inset, C), (C, S - inset), (inset, C)]


def render(spell_id):
    colour, category = spell_meta(spell_id)
    lum = (0.299 * colour[0] + 0.587 * colour[1] + 0.114 * colour[2]) / 255
    if lum > 0.62:        # a white glyph needs a field it can stand on
        colour = _mixc(colour, (0, 0, 0), 0.35)
    elif lum < 0.08:
        colour = _mixc(colour, (255, 255, 255), 0.2)
    light, mid, dark = RIM[category]

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # rim: dark keyline, then the metal band lit on its top-left faces and shadowed bottom-right
    d.polygon(diamond(u(0.02)), fill=KEY + (255,))
    band = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    bd.polygon(diamond(u(0.04)), fill=mid + (255,))
    bd.polygon([(C, u(0.04)), (u(0.04), C), (C, C)], fill=light + (255,))
    bd.polygon([(S - u(0.04), C), (C, S - u(0.04)), (C, C)], fill=dark + (255,))
    img.alpha_composite(band)
    d.polygon(diamond(u(0.1)), fill=KEY + (255,))

    # field: the spell's colour, deepening from the centre to the rim
    field = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fpx = field.load()
    inner = diamond(u(0.115))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(inner, fill=255)
    centre_c = _mixc(colour, (255, 255, 255), 0.22)
    edge_c = _mixc(colour, (0, 0, 0), 0.5)
    rmax = C - u(0.115)
    for y in range(0, S, 2):
        for x in range(0, S, 2):
            t = min(1.0, (abs(x - C) + abs(y - C)) / rmax)
            c = _mixc(centre_c, edge_c, t ** 1.4)
            for yy in (y, y + 1):
                for xx in (x, x + 1):
                    fpx[xx, yy] = c + (255,)
    img.paste(field, (0, 0), mask)

    # engraved ring and ticks
    engrave = _mixc(colour, (0, 0, 0), 0.35) + (255,)
    er = u(0.31)
    d.ellipse([C - er, C - er, C + er, C + er], outline=engrave, width=u(0.012))
    for i in range(16):
        a = i * math.pi / 8
        r0, r1 = er - u(0.02), er + u(0.02)
        d.line([(C + r0 * math.cos(a), C + r0 * math.sin(a)), (C + r1 * math.cos(a), C + r1 * math.sin(a))],
               fill=engrave, width=u(0.01))

    # glyph: glow of the spell colour, keyline, cream fill
    gmask = Image.new("L", (S, S), 0)
    produced = G[spell_id](ImageDraw.Draw(gmask))
    if isinstance(produced, Image.Image):
        gmask = produced
    glow = gmask.filter(ImageFilter.MaxFilter(u(0.05) | 1)).filter(ImageFilter.GaussianBlur(u(0.03)))
    glow_c = _mixc(colour, (255, 255, 255), 0.55)
    img.paste(Image.new("RGBA", (S, S), glow_c + (255,)), (0, 0),
              Image.eval(glow, lambda v: int(v * 0.85)))
    keyline = gmask.filter(ImageFilter.MaxFilter(2 * (u(0.018) // 2) + 1))
    img.paste(Image.new("RGBA", (S, S), KEY + (255,)), (0, 0), keyline)
    cream = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cream)
    for y in range(S):
        cd.line([(0, y), (S, y)], fill=_mixc(CREAM, (0xE0, 0xCC, 0x9E), y / S) + (255,))
    img.paste(cream, (0, 0), gmask)

    # gloss on the top-left facet of the field
    gloss = Image.new("L", (S, S), 0)
    ImageDraw.Draw(gloss).polygon([(C, u(0.13)), (u(0.13), C), (C - u(0.08), C - u(0.08))], fill=38)
    img.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0), gloss)

    # keep everything inside the diamond, then reduce with a hard alpha edge (a soft one halos
    # grey against the HUD)
    clip = Image.new("L", (S, S), 0)
    ImageDraw.Draw(clip).polygon(diamond(u(0.02)), fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (S, S), 0), clip))
    small = img.resize((SIZE, SIZE), Image.LANCZOS)
    alpha = small.getchannel("A").point(lambda v: 255 if v > 110 else 0)
    small.putalpha(alpha)
    return small


# --------------------------------------------------------------------------- run


def all_spells():
    ids = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(SPELL_DATA, "*.json"))}
    return sorted(ids | set(JAVA_SPELLS))


def sheet(path, ids):
    cols = 12
    cell = 96
    rows = (len(ids) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cell, rows * cell), (40, 34, 30, 255))
    for i, sid in enumerate(ids):
        out.alpha_composite(render(sid), ((i % cols) * cell + 2, (i // cols) * cell + 2))
    out.save(path)
    print("wrote", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="")
    ap.add_argument("--sheet", metavar="PNG", help="contact sheet only, writes no icons")
    args = ap.parse_args()
    only = [s.strip() for s in args.only.split(",") if s.strip()]
    ids = only or all_spells()
    missing = [sid for sid in ids if sid not in G]
    if missing:
        raise SystemExit(f"no glyph for: {', '.join(missing)}")
    if args.sheet:
        sheet(args.sheet, ids)
        return 0
    written, skipped = [], []
    for sid in ids:
        path = os.path.join(ICON_DIR, sid + ".png")
        if not args.force and not is_regenerable(path, MARKER, 64):
            skipped.append(sid)
            continue
        save(render(sid), path, MARKER)
        written.append(sid)
    print(f"wrote {len(written)} spell icons")
    if skipped:
        print(f"skipped {len(skipped)} owned elsewhere (--force claims them)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
