#!/usr/bin/env python3
"""Hand-authored 16x16 item sprites with automatic shading, for Wizards & Beasts.

Every icon is drawn by hand as a 16x16 text grid -- one character per pixel, each character
naming a *material region*, not a colour. The engine then does the part that is tedious and
error-prone by hand and must be identical on every icon:

  - a five-tone hue-shifted ramp per material (shadows lean blue, lights lean yellow, the way
    vanilla's own item art does, instead of mixing toward grey)
  - shading by form: `flat` regions get a one-pixel bevel lit from the top-left, `round`
    regions are shaded as an ellipsoid under the same light, `metal` the same with harder
    contrast and a specular
  - a selective outline one pixel outside the silhouette, in the darkest tone of the
    material it hugs -- never pure black

So the silhouette and the detail are authored, and the light is computed. Authors leave a
one-pixel margin on every side: the outline grows the shape by one pixel.

Legend entries:  'x': "material"            shaded by the material's kind
                 'x': ("material", "tone")  a fixed tone: outline/dark/shade/base/light/spec
                 'x': ("material", "#RRGGBB") the material with its base recoloured
                 'x': ("material", "#RRGGBB", "tone")

Run from the repo root:
    python tools/item_sprites.py --mockup out.png [--only a,b]   before/after sheet
    python tools/item_sprites.py --write [--force] [--only a,b]  into textures/item
"""

import argparse
import colorsys
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artgen_common import hx, is_regenerable, marker, save  # noqa: E402

MARKER = marker("item_sprites.py")
ITEM = "src/main/resources/assets/wizards_and_beasts/textures/item"
TONES = ("outline", "dark", "shade", "base", "light", "spec")

# --------------------------------------------------------------------------- materials

MATERIALS = {
    # name: (base colour, kind)
    "glass": ("#BFDCE4", "flat"),
    "cork": ("#B0804E", "flat"),
    "liquid": ("#C04070", "round"),
    "wood": ("#9A6334", "flat"),
    "darkwood": ("#6A4226", "flat"),
    "gold": ("#E6B43A", "metal"),
    "silver": ("#C3C9D4", "metal"),
    "bronze": ("#B8733A", "metal"),
    "paper": ("#EADFBE", "flat"),
    "leather": ("#7A4A2A", "flat"),
    "cloth": ("#7A2E2E", "flat"),
    "chocolate": ("#6E4126", "round"),
    "foam": ("#F2E8D0", "round"),
    "pastry": ("#D9A04E", "round"),
    "stone": ("#8C7A5A", "round"),
    "root": ("#C9A27A", "round"),
    "leaf": ("#5E9B3A", "flat"),
    "ivory": ("#EEE6CE", "round"),
    "feather": ("#C8342A", "flat"),
    "wax": ("#8E2320", "round"),
    "smoke": ("#E0A0A0", "round"),
    "weed": ("#5E8A5A", "round"),
    "hair": ("#E8ECF4", "flat"),
    "candy": ("#F2D048", "round"),
    "ink": ("#2E1F16", "flat"),
    # Beaten metal that is a surface, not a volume: masks, plates, badges. Metal shading would
    # read a face-plate as a ball.
    "plate": ("#C8CCD4", "flat"),
}


def _shift_hue(h, target, amount):
    d = (target - h + 0.5) % 1.0 - 0.5
    return (h + max(-amount, min(amount, d))) % 1.0


def ramp(base):
    """Six tones from one base colour, hue-shifted: shadows toward blue-violet, lights toward
    yellow. Low-saturation bases shift less, or a grey would come out tinted."""
    r, g, b = (c / 255 for c in hx(base)[:3])
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    k = min(1.0, s * 2.2)

    def tone(dh_target, dh, ds, dv, vmax=1.0):
        hh = _shift_hue(h, dh_target, dh * k)
        ss = max(0.0, min(1.0, s * ds))
        vv = max(0.0, min(vmax, v * dv))
        rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
        return (round(rr * 255), round(gg * 255), round(bb * 255))

    return {
        "outline": tone(0.70, 0.08, 1.15, 0.36),
        "dark": tone(0.68, 0.06, 1.12, 0.56),
        "shade": tone(0.66, 0.035, 1.06, 0.78),
        "base": tone(h, 0.0, 1.0, 1.0),
        "light": tone(0.15, 0.03, 0.82, 1.18),
        "spec": tone(0.14, 0.02, 0.30, 1.40),
    }


def resolve(entry):
    """Legend entry -> (material name, ramp, kind, fixed tone or None)."""
    if isinstance(entry, str):
        entry = (entry,)
    name = entry[0]
    base, kind = MATERIALS[name]
    fixed = None
    for extra in entry[1:]:
        if extra.startswith("#"):
            base = extra
        else:
            fixed = extra
    return name, ramp(base), kind, fixed


# --------------------------------------------------------------------------- shading

LIGHT = (-0.55, -0.65, 0.52)
_ln = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / _ln for c in LIGHT)


def regions(grid, legend):
    """Connected same-character regions, 4-neighbour."""
    seen = set()
    out = []
    for y in range(16):
        for x in range(16):
            ch = grid[y][x]
            if ch == "." or (x, y) in seen:
                continue
            stack, cells = [(x, y)], []
            seen.add((x, y))
            while stack:
                cx, cy = stack.pop()
                cells.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in seen \
                            and grid[ny][nx] == ch:
                        seen.add((nx, ny))
                        stack.append((nx, ny))
            out.append((ch, cells))
    return out


def shade_region(cells, kind, tones, px):
    cellset = set(cells)
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rx, ry = max(0.5, (x1 - x0 + 1) / 2), max(0.5, (y1 - y0 + 1) / 2)
    for (x, y) in cells:
        lit_edge = (x - 1, y) not in cellset or (x, y - 1) not in cellset
        dark_edge = (x + 1, y) not in cellset or (x, y + 1) not in cellset
        if kind == "flat" or len(cells) < 4:
            t = "light" if lit_edge and not dark_edge else "shade" if dark_edge and not lit_edge \
                else "base"
        else:
            u, v = (x - cx) / rx, (y - cy) / ry
            nz = math.sqrt(max(0.0, 1.0 - min(1.0, u * u + v * v)))
            n = (u * 0.9, v * 0.9, nz + 0.25)
            nl = math.sqrt(sum(c * c for c in n))
            i = sum(a * b for a, b in zip(n, LIGHT)) / nl
            if kind == "metal":
                t = "spec" if i > 0.93 else "light" if i > 0.72 else "base" if i > 0.42 \
                    else "shade" if i > 0.12 else "dark"
            else:
                t = "light" if i > 0.80 else "base" if i > 0.42 else "shade" if i > 0.05 \
                    else "dark"
            if dark_edge and t in ("base", "light"):
                t = "shade"
        px[(x, y)] = tones[t]


def render(sprite):
    if "from" in sprite:
        # A derived frame: the source sprite with its alpha scaled, e.g. a fade sequence.
        img = render(SPRITES[sprite["from"]])
        px = img.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[x, y]
                if a:
                    px[x, y] = (r, g, b, a * sprite["alpha"] // 255)
        return img
    grid, legend = sprite["grid"], sprite["legend"]
    bad = [f"row {i}: {len(r)}" for i, r in enumerate(grid) if len(r) != 16]
    assert len(grid) == 16 and not bad, f"{sprite.get('name')}: {len(grid)} rows; {bad}"
    px = {}
    owner = {}
    for ch, cells in regions(grid, legend):
        assert ch in legend, f"{sprite.get('name')}: '{ch}' not in legend"
        if legend[ch] is None:
            continue  # authored hole: another layer draws here
        name, tones, kind, fixed = resolve(legend[ch])
        for c in cells:
            owner[c] = tones
        if fixed:
            for c in cells:
                px[c] = tones[fixed]
        else:
            shade_region(cells, kind, tones, px)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items():
        img.putpixel((x, y), c + (255,))
    if not sprite.get("outline", True):
        return img
    # Outline: one pixel outside the silhouette, in the hugged material's own darkest tone.
    # Pixels on the lit side take the next tone up, so the rim reads as light, not as a cage.
    for y in range(16):
        for x in range(16):
            if (x, y) in owner:
                continue
            best = None
            for dx, dy, lit in ((1, 0, True), (0, 1, True), (-1, 0, False), (0, -1, False)):
                n = (x + dx, y + dy)
                if n in owner:
                    tone = owner[n]["dark"] if lit else owner[n]["outline"]
                    if best is None or sum(tone) < sum(best):
                        best = tone
            if best is not None:
                img.putpixel((x, y), best + (255,))
    return img


# --------------------------------------------------------------------------- sprites

SPRITES = {}

# The wand keeps its bespoke GeckoLib renderer, whose `textures/item/<name>.png` is the model's UV
# sheet, not an icon -- overwriting it breaks the model. (AnimatedItems read their sheet from
# `textures/item/model/`, so their `textures/item/<name>.png` is an icon and is drawn here.)
GECKOLIB_ITEMS = {"wand", "elder_wand"}


def sprite(name, legend, *rows, outline=True):
    """`outline=False` for an overlay layer, e.g. a tinted liquid drawn over its bottle."""
    assert name not in GECKOLIB_ITEMS, f"{name} is a GeckoLib model texture, not an icon"
    SPRITES[name] = {"name": name, "legend": legend, "grid": list(rows), "outline": outline}


def faded(name, source, alpha):
    """A copy of `source` at `alpha` (0..255), for frame sequences driven by an item property."""
    SPRITES[name] = {"name": name, "from": source, "alpha": alpha}


GLASS = {"g": "glass", "*": ("glass", "spec"), "c": "cork", "C": ("cork", "shade")}


def sym(half, spec_fill=None):
    """A symmetric row from its left 8 columns. Highlights are lit from the left, so they must not
    mirror: every '*' on the right half becomes `spec_fill` (the region it sits in)."""
    assert len(half) == 8, half
    right = half[::-1]
    if spec_fill:
        right = right.replace("*", spec_fill)
    return half + right

sprite("amortentia", dict(GLASS, l=("liquid", "#E0609A"), s=("liquid", "#F4B8D4", "light")),
       "................",
       "......cccc......",
       "......cCCc......",
       ".....gggggg.....",
       "......gssg......",
       "......gllg......",
       ".....gllllg.....",
       "...gllllllllg...",
       "..gl*lllllllg...",
       "..gl*llllllllg..",
       "..gllllllllllg..",
       "..gllllllllllg..",
       "...gllllllllg...",
       "....gllllllg....",
       "......gggg......",
       "................")

sprite("felix_felicis", dict(GLASS, l=("liquid", "#F2C230"), s=("liquid", "#FFF0A0", "spec")),
       "................",
       ".......cc.......",
       "......cccc......",
       "......cCCc......",
       ".....gggggg.....",
       ".....g*lllg.....",
       ".....g*llsg.....",
       ".....gllllg.....",
       ".....gslllg.....",
       ".....gllllg.....",
       ".....glllsg.....",
       ".....gllllg.....",
       ".....gllllg.....",
       ".....gllllg.....",
       "......gggg......",
       "................")

sprite("polyjuice_potion", dict(GLASS, l=("liquid", "#6E7A3A"), b=("liquid", "#4A5226", "shade"),
                                a=("glass", "shade")),
       "................",
       "......cccc......",
       "......cCCc......",
       "......gggg......",
       ".......gg.......",
       "....gggggggg....",
       "...gaaaaaaaag...",
       "...g*llllbllg...",
       "...g*lllllllg...",
       "...gllblllllg...",
       "...gllllllblg...",
       "...glllllllllg..",
       "...gllbllllllg..",
       "...gllllllllg...",
       "....gggggggg....",
       "................")

sprite("butterbeer", {"w": "wood", "W": ("wood", "shade"), "f": "foam",
                      "b": ("liquid", "#C8862E"), "h": "darkwood", "r": ("bronze", "#9A7A4A")},
       "................",
       "................",
       "...ffff.ff......",
       "..ffffffffff....",
       "..fffffffffff...",
       "..rrrrrrrrrr....",
       "..wWwwWwwWww.hh.",
       "..wWwwWwwWww..h.",
       "..wWwwWwwWww..h.",
       "..rrrrrrrrrr..h.",
       "..wWwwWwwWww.hh.",
       "..wWwwWwwWww....",
       "..wWwwWwwWww....",
       "..rrrrrrrrrr....",
       "................",
       "................")

sprite("chocolate_frog", {"c": "chocolate", "e": ("chocolate", "#8A5634", "light"),
                          "E": ("ink", "dark")},
       "................",
       "................",
       "....ee....ee....",
       "...eEe....eEe...",
       "...cccccccccc...",
       "..cccccccccccc..",
       "..cccccccccccc..",
       ".cc.cccccccc.cc.",
       ".c..cccccccc..c.",
       "....cccccccc....",
       "...cccccccccc...",
       "..ccc.cccc.ccc..",
       ".ccc..cccc..ccc.",
       ".cc..........cc.",
       "................",
       "................")

sprite("bertie_botts_every_flavour_beans",
       {"p": ("paper", "#E8D8A8"), "s": ("cloth", "#B83A2E"), "P": ("paper", "#E8D8A8", "shade"),
        "1": ("candy", "#E85A5A"), "2": ("candy", "#7AC24A"), "3": ("candy", "#F2D048"),
        "4": ("candy", "#8A5ACB"), "5": ("candy", "#F29A3A")},
       "................",
       "....PpPpPpPp....",
       "....pppppppp....",
       "....ssssssss....",
       "....pppppppp....",
       "...pppppppppp...",
       "...ssssssssss...",
       "...pppppppppp...",
       "...pppppppppp...",
       "...ssssssssss...",
       "...pppppppppp...",
       "...pppppppppp...",
       "....pppppppp.11.",
       ".22.........33..",
       ".22..44.55......",
       "................")

sprite("fizzing_whizzbee", {"y": ("candy", "#F2C83A"), "k": ("ink", "#3A2A1A", "base"),
                            "w": ("hair", "#EAF2FA"), "*": ("candy", "#F2C83A", "spec")},
       "................",
       "................",
       "...ww......ww...",
       "..wwww....wwww..",
       "..wwwww..wwwww..",
       "...wwwyyyywww...",
       ".....yyyyyy.....",
       "....y*yyyyyy....",
       "....kkkkkkkk....",
       "....yyyyyyyy....",
       "....kkkkkkkk....",
       "....yyyyyyyy....",
       ".....yyyyyy.....",
       "......yyyy......",
       "................",
       "................")

sprite("pumpkin_pasty", {"p": "pastry", "c": ("pastry", "light"), "o": ("pastry", "#C06A2A")},
       "................",
       "................",
       "................",
       "................",
       "......pppp......",
       "....pppppppp....",
       "...pppoppoppp...",
       "..ppppppppppppp.",
       "..ppppppppppppp.",
       ".pppppppppppppp.",
       ".cpcpcpcpcpcpcp.",
       "..c.c.c.c.c.c...",
       "................",
       "................",
       "................",
       "................")

BOOK = {"l": "leather", "L": ("leather", "shade"), "p": "paper", "P": ("paper", "shade"),
        "g": ("gold", "light"), "G": ("gold", "base")}

sprite("a_history_of_magic", dict(BOOK, l=("leather", "#6E4A2A"), L=("leather", "#6E4A2A", "shade")),
       "................",
       "................",
       "...Lllllllllll..",
       "...Llllllllllpl.",
       "...LGGGGGGGGlpl.",
       "...Llllllllllpl.",
       "...Lll.gggg.lpl.",
       "...Llll.gg.llpl.",
       "...Lll.gggg.lpl.",
       "...Llllllllllpl.",
       "...LGGGGGGGGlpl.",
       "...Llllllllllpl.",
       "...LllllllllllP.",
       "...LLLLLLLLLLL..",
       "................",
       "................")

sprite("standard_book_of_spells",
       dict(BOOK, l=("leather", "#5A2A5E"), L=("leather", "#5A2A5E", "shade")),
       "................",
       "................",
       "...Lllllllllll..",
       "...Llllllllllpl.",
       "...Lllllllllllpl",
       "...Lllllgllllpl.",
       "...Llllgggllllpl",
       "...Llggggggglpl.",
       "...Llllgggllllpl",
       "...Lllllglllllpl",
       "...Lllllllllllpl",
       "...Llllllllllpl.",
       "...LllllllllllP.",
       "...LLLLLLLLLLL..",
       "................",
       "................")

sprite("bezoar", {"s": "stone", "d": ("stone", "dark"), "*": ("stone", "light")},
       "................",
       "................",
       "................",
       "......ssss......",
       "....ssssssss....",
       "...ss*sssdsss...",
       "...s*ssssssss...",
       "..ssssdsssssss..",
       "..sssssssdssss..",
       "..ssdssssssss...",
       "...sssssdssss...",
       "....ssssssss....",
       ".....ssssss.....",
       "................",
       "................",
       "................")

sprite("baby_mandrake", {"r": "root", "k": ("ink", "#3A2414", "base"), "l": "leaf",
                         "L": ("leaf", "#3E7A2A")},
       "................",
       "....L..l..L.....",
       ".....LlllL......",
       "......lLl.......",
       "......rrr.......",
       ".....rrrrr......",
       "....rkrrkrr.....",
       "....rrrrrrr.....",
       "....rrkkkrr.....",
       "..rr.rrrrr.rr...",
       "...rrrrrrrrr....",
       ".....rrrrr......",
       ".....rr.rr......",
       "....rr...rr.....",
       "................",
       "................")

sprite("basilisk_fang", {"i": "ivory", "v": ("liquid", "#4E8A3A"), "*": ("ivory", "spec")},
       "................",
       "..ii............",
       "..iiii..........",
       "...iiiii........",
       "...i*iiii.......",
       "....i*iiiii.....",
       ".....iiiiiii....",
       "......iiiiiii...",
       ".......iiiiii...",
       "........iiiii...",
       ".........iiii...",
       "..........iii...",
       "..........iv....",
       "...........v....",
       "................",
       "................")

sprite("acromantula_venom", dict(GLASS, l=("liquid", "#5A2A6A"), k=("ink", "#1A1020", "base")),
       "................",
       "......cccc......",
       "......cCCc......",
       ".....gggggg.....",
       "....gllllllg....",
       "...gllllllllg...",
       "...g*kllllklg...",
       "...g*lkllkllg...",
       "...gkkkkkkkkg...",
       "...gllkkkklllg..",
       "...gkllkkllklg..",
       "...gllklklllg...",
       "...gllllllllg...",
       "....gllllllg....",
       ".....gggggg.....",
       "................")

sprite("phoenix_feather", {"r": "feather", "o": ("feather", "#F2A030"), "s": ("ivory", "#F4E4B0")},
       "................",
       "...........oo...",
       "..........oroo..",
       ".........orrroo.",
       "........orrrrro.",
       ".......orrrsrro.",
       "......orrrsrro..",
       ".....orrrsrro...",
       "....orrrsrro....",
       "...orrrsrro.....",
       "...orrsrro......",
       "....ossoo.......",
       "....so..........",
       "...s............",
       "..s.............",
       "................")

sprite("auto_answer_quill", {"w": ("hair", "#EEE8DA"), "s": ("ivory", "#D8C89A"),
                             "n": ("gold", "base"), "k": ("ink", "base")},
       "................",
       "............ww..",
       "...........wwww.",
       "..........wwwww.",
       ".........wwwsww.",
       "........wwwsww..",
       ".......wwwsww...",
       "......wwwsww....",
       ".....wwwsww.....",
       ".....wwsww......",
       "......sww.......",
       ".....s..........",
       "....n...........",
       "...n............",
       "..k.............",
       "................")

sprite("beaters_bat", {"w": "wood", "d": "darkwood", "b": ("leather", "#5A3A22")},
       "................",
       "...........www..",
       "..........wwwww.",
       ".........wwwwww.",
       "........wwwwwww.",
       ".......wwwwwww..",
       "......wwwwwww...",
       ".....wwwwww.....",
       "....wwwww.......",
       "...dwww.........",
       "..bbd...........",
       ".bbb............",
       ".bb.............",
       "................",
       "................",
       "................")

sprite("remembrall", {"g": "glass", "*": ("glass", "spec"), "m": ("smoke", "#F0E8E8"),
                      "r": ("smoke", "#D8383A"), "b": ("gold", "#B8903A")},
       "................",
       "................",
       ".....gggggg.....",
       "....g*mmmmmg....",
       "...g*mmmmmmmg...",
       "...gmmmrrmmmg...",
       "...gmmrrrrmmg...",
       "...gmmrrrrrmg...",
       "...gmmmrrmmmg...",
       "...gmmmmmmmmg...",
       "....gmmmmmmg....",
       ".....gggggg.....",
       "....bbbbbbbb....",
       "...bbbbbbbbbb...",
       "................",
       "................")

sprite("sneakoscope", {"g": ("glass", "#C8E0E8"), "*": ("glass", "spec"), "b": "bronze"},
       "................",
       ".......bb.......",
       ".......bb.......",
       "....gggggggg....",
       "...g*ggggggggg..",
       "..g*gggggggggg..",
       "..gggggggggggg..",
       "..bbbbbbbbbbbb..",
       "...gggggggggg...",
       "....gggggggg....",
       ".....gggggg.....",
       "......gggg......",
       ".......gg.......",
       ".......bb.......",
       "................",
       "................")

sprite("time_turner", {"o": "gold", "s": ("glass", "#E8DCB0"), "a": ("candy", "#E8C870", "base"),
                       "c": ("gold", "shade")},
       "......cccc......",
       ".......cc.......",
       "....oooooooo....",
       "...o........o...",
       "..o...oooo...o..",
       "..o...ssss...o..",
       ".o.....ss.....o.",
       ".o......a.....o.",
       ".o.....aa.....o.",
       "..o...aaaa...o..",
       "..o...oooo...o..",
       "...o........o...",
       "....oooooooo....",
       "................",
       "................",
       "................")

sprite("golden_snitch", {"o": "gold", "w": ("silver", "#E4E8F0")},
       "................",
       "................",
       "................",
       ".www........www.",
       "wwwww......wwwww",
       ".wwwww....wwwww.",
       "...wwwoooowww...",
       ".....oooooo.....",
       ".....oooooo.....",
       ".....oooooo.....",
       ".....oooooo.....",
       "......oooo......",
       "................",
       "................",
       "................",
       "................")

sprite("gillyweed", {"w": "weed", "d": ("weed", "#3E6A44")},
       "................",
       "................",
       "......w..d......",
       ".....ww.dd..w...",
       "....www.dd.ww...",
       "....ww.ddd.ww...",
       "...www.dd.www...",
       "...wwwddddww....",
       "....wwwddwww....",
       ".....wwwwww.....",
       "......wwww......",
       ".....dwwwwd.....",
       "....dd.ww.dd....",
       "...dd..ww..dd...",
       "................",
       "................")

sprite("howler", {"r": ("paper", "#C0392E"), "R": ("paper", "#C0392E", "shade"),
                  "f": ("paper", "#C0392E", "dark"), "w": "wax"},
       "................",
       "................",
       "................",
       "..rrrrrrrrrrrr..",
       "..rfrrrrrrrrfr..",
       "..rrffrrrrffrr..",
       "..rrrrffffrrrr..",
       "..rrrrrwwrrrrr..",
       "..rrrrwwwwrrrr..",
       "..rrrrrwwrrrrr..",
       "..rrrrrrrrrrrr..",
       "..RRRRRRRRRRRR..",
       "................",
       "................",
       "................",
       "................")

sprite("unicorn_hair", {"h": "hair", "*": ("hair", "spec")},
       "................",
       "................",
       "..........hh....",
       ".........h..h...",
       "........h...h...",
       ".......h...h....",
       "......h...h.....",
       ".....h...h......",
       "....h...h.......",
       "...h...h........",
       "...h..h.........",
       "....hh..........",
       "................",
       "................",
       "................",
       "................")


# --------------------------------------------------------------------------- batch 1: potions, vials, powders

E = "................"
S = sym

sprite("blood_pact_vial", {"n": ("silver", "shade"), "s": "silver", "S": ("silver", "dark"),
                           "g": "glass", "r": ("liquid", "#A0142A"), "*": ("glass", "spec")},
       E,
       S("......n."),
       S(".....n.."),
       S(".....n.."),
       S("......n."),
       S(".......s"),
       S("......sS"),
       S(".....sg*", "r"),
       S(".....sg*", "r"),
       S(".....sgr"),
       S(".....sgr"),
       S(".....sgr"),
       S("......sg"),
       S(".......s"),
       E, E)

sprite("blood_replenishing_potion", dict(GLASS, l=("liquid", "#B01830"), a=("glass", "shade")),
       E,
       S("......cc"),
       S("......cC"),
       S(".....ggg"),
       S("......ga"),
       S("......ga"),
       S(".....gll"),
       S(".....g*l", "l"),
       S("....g*ll", "l"),
       S("....glll"),
       S("...gllll"),
       S("...gllll"),
       S("..glllll"),
       S("..gggggg"),
       E, E)

# The brew is two layers: glass and cork untinted, the liquid a greyscale mask the brew tint
# source paints (see items/brew.json). The glass's highlight stays on the glass layer, so the
# liquid layer leaves those pixels open.
BREW_ROWS = (
    E,
    S("......cc"),
    S("......cC"),
    S(".......g"),
    S(".......g"),
    S("......ga"),
    S(".....gll"),
    S("....g*ll", "l"),
    S("...g*lll", "l"),
    S("...gllll"),
    S("...gllll"),
    S("...gllll"),
    S("....glll"),
    S(".....ggg"),
    E, E,
)
sprite("brew", dict(GLASS, a=("glass", "shade"), l=None), *BREW_ROWS)
sprite("brew_liquid", {"l": ("liquid", "#D8D8D8"), "g": None, "a": None, "c": None, "C": None,
                       "*": None}, *BREW_ROWS, outline=False)

sprite("broom_polish", {"t": "silver", "T": ("silver", "light"), "k": ("silver", "dark"),
                        "b": ("silver", "#A8AEB8"), "R": ("cloth", "#A82A22"),
                        "y": ("gold", "light")},
       E, E, E,
       S("....tttt"),
       S("...tTTTT"),
       S("...kkkkk"),
       S("...bbbbb"),
       S("...bRRRR"),
       S("...bRyyy"),
       S("...bRRRR"),
       S("...bbbbb"),
       S("....bbbb"),
       E, E, E, E)

sprite("draught_of_living_death", dict(GLASS, l=("liquid", "#D6CCEA"), a=("glass", "shade")),
       E,
       S("......cc"),
       S("......cC"),
       S(".......g"),
       S(".....ggg"),
       S("....gaaa"),
       S("....g*ll", "l"),
       S("....g*ll", "l"),
       S("....glll"),
       S("....glll"),
       S("....glll"),
       S("....glll"),
       S("....glll"),
       S("....gggg"),
       E, E)

sprite("draught_of_peace", dict(GLASS, l=("liquid", "#9CD6D0"), v=("smoke", "#E4F2F2", "light")),
       "........v.......",
       ".......v........",
       S("......cc"),
       S("......cC"),
       S(".......g"),
       S("......gl"),
       S(".....gll"),
       S("....g*ll", "l"),
       S("...g*lll", "l"),
       S("...gllll"),
       S("...gllll"),
       S("....glll"),
       S(".....gll"),
       S("......gg"),
       E, E)

sprite("elixir_of_life", {"o": "gold", "O": ("gold", "shade"), "g": "glass",
                          "l": ("liquid", "#E0482A"), "*": ("glass", "spec"),
                          "s": ("candy", "#FFE08A", "spec")},
       E,
       S(".......o"),
       S("......oO"),
       S(".......g"),
       S("......gl"),
       S(".....g*l", "l"),
       S("....g*sl", "l"),
       S("...gllll"),
       S("..glllsl"),
       S("...gllll"),
       S("....glll"),
       S(".....gll"),
       S("......gl"),
       S(".......o"),
       S("......oo"),
       E)

sprite("essence_of_dittany", {"k": ("ink", "#2A2226"), "s": "silver",
                              "b": ("glass", "#7A4420"), "*": ("glass", "#7A4420", "spec"),
                              "p": "paper", "t": ("ink", "base")},
       E, E,
       S(".......k"),
       S("......kk"),
       S("......kk"),
       S(".......s"),
       S(".....bbb"),
       S("....b*bb", "b"),
       S("....bppp"),
       S("....bptt"),
       S("....bppp"),
       S("....bbbb"),
       S("....bbbb"),
       E, E, E)

sprite("firewhisky", {"c": "cork", "g": ("glass", "#C8B89A"), "*": ("glass", "spec"),
                      "a": ("liquid", "#C8741A"), "p": "paper", "f": ("candy", "#E0501A", "base"),
                      "F": ("candy", "#F2B030", "base")},
       E,
       S("......cc"),
       S(".......g"),
       S(".......g"),
       S("......gg"),
       S("....gggg"),
       S("...g*aaa", "a"),
       S("...gappp"),
       S("...gappf"),
       S("...gapfF"),
       S("...gappp"),
       S("...gaaaa"),
       S("...gaaaa"),
       S("...ggggg"),
       E, E)

sprite("flesh_eating_slug_repellent", dict(GLASS, k=("silver", "#6A6E76"), K=("silver", "#6A6E76", "dark"),
                                           l=("liquid", "#6A9A2A"), p="paper",
                                           s=("root", "#8A6A3A")),
       E, E,
       S("....kkkk"),
       S("....KKKK"),
       S("...ggggg"),
       S("...g*lll", "l"),
       S("...g*lll", "l"),
       S("...gpppp"),
       "...gpppsssppg...",
       "...gppssssppg...",
       S("...gpppp"),
       S("...gllll"),
       S("...gllll"),
       S("....gggg"),
       E, E)

sprite("gubraithian_fire", {"k": ("silver", "#4A4E56"), "g": "glass", "a": ("glass", "light"),
                            "f": ("candy", "#E0581A", "base"), "F": ("candy", "#F2A030", "base"),
                            "Y": ("candy", "#FFF0A0", "base")},
       E,
       S("......kk"),
       S(".....k.."),
       S(".....kkk"),
       S("....gggg"),
       S("....gaaf"),
       S("....gafF"),
       S("....gfFF"),
       S("....gfFY"),
       S("....gfFY"),
       S("....gffF"),
       S("....gaff"),
       S("....kkkk"),
       E, E, E)

sprite("ink_bottle", {"g": "glass", "*": ("glass", "spec"), "a": ("glass", "shade"),
                      "k": ("ink", "#1C1A2E")},
       E, E,
       S("......gg"),
       S("......ga"),
       S("....gggg"),
       S("...g*aaa", "a"),
       S("..g*kkkk", "k"),
       S("..g*kkkk", "k"),
       S("..gkkkkk"),
       S("..gkkkkk"),
       S("..gkkkkk"),
       S("...ggggg"),
       E, E, E, E)

sprite("matagot_essence", dict(GLASS, l=("liquid", "#2A2034"), m=("smoke", "#4A3A5A"),
                               e=("candy", "#8AE05A", "spec")),
       E,
       S("......cc"),
       S("......cC"),
       S(".....ggg"),
       S("......gm"),
       S("......gl"),
       S(".....gll"),
       S("...glmll"),
       "..gl*lllllllg...",
       "..gl*mlellellg..",
       "..gllllmllllmg..",
       "..g" + "llmllllllm" + "g..",
       S("...glllm"),
       S("....glll"),
       S("......gg"),
       E)

sprite("memory_vial", dict(GLASS, l=("liquid", "#B8C6DE"), w=("hair", "#F4F8FF", "spec")),
       E,
       S(".......c"),
       S("......cc"),
       S("......cC"),
       S(".....ggg"),
       S(".....g*w", "l"),
       S(".....g*l", "w"),
       S(".....glw"),
       S(".....gwl"),
       S(".....glw"),
       S(".....gll"),
       S(".....gwl"),
       S(".....gll"),
       S("......gg"),
       E, E)

sprite("mrs_skowers_mess_remover", {"k": ("silver", "#5A5E66"), "b": ("liquid", "#E8D86A"),
                                    "*": ("liquid", "#E8D86A", "spec"), "p": "paper",
                                    "u": ("cloth", "#2E5AA8")},
       E,
       ".....kkkk.......",
       S(".......k"),
       S("......kk"),
       S(".....bbb"),
       S("....bbbb"),
       S("....b*bb", "b"),
       S("....b*bb", "b"),
       S("....bppp"),
       S("....bpuu"),
       S("....bppp"),
       S("....bbbb"),
       S("....bbbb"),
       S("....bbbb"),
       E, E)

sprite("murtlap_essence", dict(GLASS, l=("liquid", "#E0C040"), t=("root", "#D88A8A")),
       E, E, E,
       S(".....ccc"),
       S(".....cCC"),
       S("....gggg"),
       S("...g*lll", "l"),
       "...g*lltllllg...",
       "...glllttlllg...",
       "...gllllltllg...",
       S("...gllll"),
       S("....gggg"),
       E, E, E, E)

sprite("pepperup_potion", dict(GLASS, l=("liquid", "#D8401E"), v=("smoke", "#ECE6E0", "light"),
                               a=("glass", "shade")),
       "......v..v......",
       ".....v..v.......",
       S("......cc"),
       S("......cC"),
       S(".......g"),
       S("......ga"),
       S(".....gll"),
       S("....g*ll", "l"),
       S("...g*lll", "l"),
       S("...gllll"),
       S("...gllll"),
       S("....glll"),
       S(".....ggg"),
       E, E, E)

sprite("pumpkin_juice", dict(GLASS, l=("liquid", "#E8862A")),
       E, E,
       "...gggggggggg...",
       "...g*lllllllg...",
       "...g*lllllllggg.",
       "...gllllllllg.g.",
       "...gllllllllg.g.",
       "...gllllllllg.g.",
       "...gllllllllggg.",
       "...gllllllllg...",
       "...gllllllllg...",
       S("....glll"),
       S(".....ggg"),
       E, E, E)

sprite("skele_gro", dict(GLASS, l=("liquid", "#7A8A6A"), p="paper", i=("ivory", "light"),
                         k=("ink", "base")),
       E,
       S("......cc"),
       S("......cC"),
       S(".....ggg"),
       S("....glll"),
       S("...g*lll", "l"),
       S("...gpppp"),
       S("...gppii"),
       S("...gppik"),
       S("...gppii"),
       S("...gpipp"),
       S("...gllll"),
       S("....glll"),
       S(".....ggg"),
       E, E)

sprite("veritaserum", dict(GLASS, l=("liquid", "#DCEEF4"), d=("liquid", "#DCEEF4", "light")),
       E, E, E,
       S("......cc"),
       S("......cC"),
       S("......gg"),
       S("......g*", "l"),
       S("......g*", "l"),
       S("......gl"),
       S("......gl"),
       S("......gl"),
       S("......gg"),
       E,
       ".......d........",
       E, E)

sprite("wolfsbane_potion", {"s": ("silver", "#9AA0A8"), "l": ("liquid", "#5A7AA0"),
                            "v": ("smoke", "#D8DCE6", "light")},
       ".....v...v......",
       "......v...v.....",
       ".....v...v......",
       S("...sssss"),
       S("...sllll"),
       S("...sllll"),
       S("....slll"),
       S(".....sss"),
       S(".......s"),
       S(".......s"),
       S(".......s"),
       S(".....sss"),
       S("....ssss"),
       E, E, E)

sprite("ghoul_slime", {"s": ("weed", "#7AA84A"), "*": ("weed", "#7AA84A", "spec")},
       E, E, E, E, E,
       S("......ss"),
       S("....ssss"),
       S("...s*sss", "s"),
       S("..s*ssss", "s"),
       S("..ssssss"),
       S(".sssssss"),
       S(".sssssss"),
       "..sss..sss...s..",
       "...s....s.......",
       E, E)

sprite("floo_powder", {"p": ("root", "#B8643A"), "P": ("root", "#B8643A", "shade"),
                       "r": ("root", "#9A4E2A", "dark"), "d": ("leaf", "#3ACA5A"),
                       "*": ("leaf", "#B8FFC8", "base")},
       E, E, E, E,
       S("......d*", "d"),
       S(".....ddd"),
       S("...rrrrr"),
       S("...ppppp"),
       S("...PPPPP"),
       S("....pppp"),
       S("....pppp"),
       S(".....ppp"),
       S(".....ppp"),
       E, E, E)

sprite("peruvian_instant_darkness_powder", {"b": ("leather", "#3A3040"), "t": ("cloth", "#8A7A5A"),
                                            "d": ("smoke", "#1A1620", "base")},
       E,
       "......d..d......",
       ".....d.dd.d.....",
       S("......dd"),
       S(".......t"),
       S("......tt"),
       S(".....bbb"),
       S("....bbbb"),
       S("...bbbbb"),
       S("...bbbbb"),
       S("...bbbbb"),
       S("....bbbb"),
       S(".....bbb"),
       E, E, E)

sprite("empty_butterbeer_mug", {"w": "wood", "W": ("wood", "shade"), "d": ("darkwood", "dark"),
                                "h": "darkwood", "r": ("bronze", "#9A7A4A")},
       E, E, E, E,
       "..rrrrrrrrrr....",
       "..rddddddddr.hh.",
       "..wWwwWwwWww..h.",
       "..wWwwWwwWww..h.",
       "..rrrrrrrrrr..h.",
       "..wWwwWwwWww.hh.",
       "..wWwwWwwWww....",
       "..wWwwWwwWww....",
       "..rrrrrrrrrr....",
       E, E, E)

sprite("dittany", {"s": ("leaf", "#6A8A4A"), "l": ("leaf", "#9AB07A"), "f": ("candy", "#D87AA8")},
       E,
       ".......f........",
       "......fff.......",
       ".......s........",
       "....ll.s.ll.....",
       "...llll.sllll...",
       "...lll..s.lll...",
       ".......s........",
       "..ll...s...ll...",
       ".llll..s..llll..",
       ".lll...s...lll..",
       ".......s........",
       "......s.........",
       "......s.........",
       E, E)


# --------------------------------------------------------------------------- batch 2: books and paper
#
# Three bodies, each drawn once; what tells two books apart in a chest is the cover colour and
# the tooled emblem, so those are per book. An emblem is up to 6x6, '.' leaves the cover showing.


def _emblem(rows, emblem, top, left):
    rows = list(rows)
    for j, line in enumerate(emblem):
        r = list(rows[top + j])
        for i, ch in enumerate(line):
            if ch != ".":
                r[left + i] = ch
        rows[top + j] = "".join(r)
    return rows


VOLUME = (   # a standing volume, spine left, page block showing on the right
    E, E,
    "...Lllllllllll..",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...Llllllllllpl.",
    "...LllllllllllP.",
    "...LLLLLLLLLLL..",
    E, E,
)

TOME = (     # thick, banded spine, a clasp across the fore-edge
    E,
    "..Lllllllllll...",
    "..Lllllllllllp..",
    "..Gllllllllllpp.",
    "..Lllllllllllpp.",
    "..Lllllllllllpp.",
    "..Lllllllllllkk.",
    "..Lllllllllllkk.",
    "..Lllllllllllpp.",
    "..Lllllllllllpp.",
    "..Lllllllllllpp.",
    "..Gllllllllllpp.",
    "..LlllllllllllP.",
    "..LLLLLLLLLLLL..",
    E, E,
)

BOOKLET = (  # slim, small, for primers and pamphlets
    E, E, E,
    "....Lllllllll...",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....Llllllllpl..",
    "....LlllllllllP.",
    "....LLLLLLLLL...",
    E, E, E,
)


def book(name, body, cover, emblem=(), at=(5, 5), extra=None, clasp="#B08A3A"):
    legend = {"l": ("leather", cover), "L": ("leather", cover, "shade"),
              "p": "paper", "P": ("paper", "shade"), "g": ("gold", "light"),
              "G": ("gold", "base"), "k": ("gold", clasp, "base"), "s": ("silver", "light")}
    legend.update(extra or {})
    top, left = at
    sprite(name, legend, *_emblem(body, emblem, top, left))


CAULDRON = (".g..g.", "gggggg", ".gggg.", ".gggg.", "..gg..")
book("advanced_potion_making", VOLUME, "#2E5A3A", CAULDRON, at=(5, 5))
book("beginners_guide_to_transfiguration", BOOKLET, "#3A5A8A",
     ("..s...", ".sss..", "s...s.", ".sss..", "..s..."), at=(5, 6))
book("bestiary", TOME, "#6A4424", ("g....g", ".g..g.", "gggggg", ".g..g.", "g....g"), at=(5, 4))
book("fantastic_beasts_and_where_to_find_them", VOLUME, "#7A5A2A",
     ("..gg..", ".gggg.", ".gggg.", "..gg.."), at=(6, 5))
book("hogwarts_a_history", TOME, "#6A1E24",
     ("gggggg", "g.gg.g", "gggggg", "g.gg.g", ".gggg.", "..gg.."), at=(4, 4))
book("magical_draughts_and_potions", VOLUME, "#4A2A6A",
     ("..gg..", "..gg..", ".gggg.", "gggggg", ".gggg."), at=(5, 5))
book("ministry_handbook", BOOKLET, "#3E1F47",
     ("g...g.", "gg.gg.", "g.g.g.", "g...g."), at=(6, 6))
book("moste_potente_potions", TOME, "#3A4A2A",
     ("..gg..", ".g..g.", "gggggg", ".gggg."), at=(5, 4),
     extra={"t": ("paper", "#C8B888", "dark")})
book("one_thousand_magical_herbs_and_fungi", VOLUME, "#4A7A2A",
     ("...g..", "..gg..", ".ggg.g", "gggggg", "..g..."), at=(5, 5))
book("quidditch_through_the_ages", VOLUME, "#8A2A1E",
     ("s....s", "ss..ss", ".sggs.", "..gg.."), at=(6, 5))
book("riddles_diary", BOOKLET, "#1E1C22", ("g", "g"), at=(4, 5),
     extra={"p": ("paper", "#D8CCA8")})
book("rise_and_fall_of_the_dark_arts", TOME, "#1E1C1E",
     (".vvvv.", "v.vv.v", "vvvvvv", ".v.v..", "..vv.."), at=(5, 4),
     extra={"v": ("leaf", "#3A8A4A", "light")})
book("secrets_of_the_darkest_art", TOME, "#241418",
     (".rrrr.", "r.rr.r", ".rrrr."), at=(6, 4),
     extra={"r": ("candy", "#C0201E", "base")}, clasp="#6A6E76")
book("tales_of_beedle_the_bard", BOOKLET, "#6A4A2A",
     ("..g...", ".ggg..", ".ggg..", "g.g.g.", "ggggg."), at=(5, 6))
book("unfogging_the_future", VOLUME, "#4A6A8A",
     ("..ss..", ".ssss.", ".ssss.", "..ss..", ".gggg."), at=(5, 5))

sprite("monster_book_of_monsters", {"f": ("leather", "#6A4A2A"), "F": ("leather", "#4A3018", "shade"),
                                    "t": ("ivory", "light"), "e": ("candy", "#F2D048", "base"),
                                    "k": ("ink", "base"), "p": "paper"},
       E,
       "..F.FF.F.F......",
       "..ffffffffff.F..",
       ".Fffeffffeff....",
       "..ffkffffkfff...",
       ".Fffffffffffpp..",
       "..tftftftftfpp..",
       "..kkkkkkkkkkpp..",
       "..tftftftftfpp..",
       ".Fffffffffffpp..",
       "..ffffffffffff..",
       ".Fffffffffffff..",
       "..ffffffffffF...",
       "..F.F.FF.F......",
       E, E)

sprite("daily_prophet", {"p": ("paper", "#E4DECA"), "P": ("paper", "#E4DECA", "shade"),
                         "k": ("ink", "base"), "c": ("ink", "#6A6660", "base")},
       E, E,
       "..pppppppppppp..",
       "..pkkkkkkkkkkp..",
       "..pppppppppppp..",
       "..pcccc.pcccpp..".replace(".", "p"),
       "..pcpp.cpcpcpp..".replace(".", "p"),
       "..pcccc.pcccpp..".replace(".", "p"),
       "..pppppppppppp..",
       "..pcccpcccpccp..",
       "..pppppppppppp..",
       "..pcccpcccpccp..",
       "..PPPPPPPPPPPP..",
       E, E, E)

sprite("the_quibbler", {"y": ("paper", "#F2D84A"), "Y": ("paper", "#F2D84A", "shade"),
                        "m": ("candy", "#D84A8A", "base"), "b": ("candy", "#3A6AC8", "base"),
                        "k": ("ink", "base")},
       E, E,
       "...yyyyyyyyyy...",
       "...ymmmmmmmmy...",
       "...yyyyyyyyyy...",
       "...yybbbbbbyy...",
       "...ybbkbbkbby...",
       "...ybbbbbbbby...",
       "...yybbbbbbyy...",
       "...yyyyyyyyyy...",
       "...ykkkkyyyyy...",
       "...ykkkyyyyyy...",
       "...YYYYYYYYYY...",
       E, E, E)

sprite("parchment", {"p": "paper", "r": ("paper", "#D8C49A"), "R": ("paper", "#D8C49A", "shade"),
                     "k": ("paper", "#B8A070", "shade")},
       E, E,
       S("...rrrrr"),
       S("..rRRRRR"),
       S("...ppppp"),
       S("...ppppp"),
       S("...pkkkk"),
       S("...ppppp"),
       S("...pkkkk"),
       S("...ppppp"),
       S("...pkkkp"),
       S("...ppppp"),
       S("...rrrrr"),
       S("..rRRRRR"),
       E, E)

sprite("torn_spell_page", {"p": "paper", "k": ("ink", "#3A2A5A", "base")},
       E, E,
       "...pppp.ppp.....",
       "...pppppppppp...",
       "...pkkpkkpkpp...",
       "...pppppppppp...",
       "...pkpkkkpkkp...",
       "...pppppppppp...",
       "...pkkkpkkpp....",
       "...pppppppppp...",
       "...pkkpkkkp.....",
       "...ppppppppp....",
       "....pp.pppp.....",
       ".....p...p......",
       E, E)

sprite("ministry_license_scroll", {"p": "paper", "P": ("paper", "shade"),
                                   "w": ("wax", "#3E1F47"), "r": ("cloth", "#6A2E7A")},
       E, E, E,
       "..PPPPPPPPPPP...",
       ".Pppppppppppp...",
       ".Pppppppppppp...",
       ".Pppppppppppp...",
       ".Ppppppppwwpp...",
       ".Pppppppwwwwp...",
       ".Ppppppppwwpp...",
       "..PPPPPPPPrPPP..",
       "..........r.r...",
       ".........r...r..",
       E, E, E)

QUILL = (
    E,
    "............ww..",
    "...........wwww.",
    "..........wwwww.",
    ".........wwwsww.",
    "........wwwsww..",
    ".......wwwsww...",
    "......wwwsww....",
    ".....wwwsww.....",
    ".....wwsww......",
    "......sww.......",
    ".....s..........",
    "....s...........",
    "...n............",
    "..n.............",
    E,
)
sprite("quill", {"w": ("hair", "#8A7A6A"), "s": ("ivory", "#E8DCC0"), "n": ("ink", "base")}, *QUILL)
sprite("quick_quotes_quill", {"w": ("leaf", "#6AD83A"), "s": ("ivory", "#E8F0C0"),
                              "n": ("gold", "base")}, *QUILL)

sprite("spellotape", {"t": ("glass", "#E8DCA8"), "T": ("glass", "#E8DCA8", "shade"),
                      "c": ("paper", "#B89A6A")},
       E, E, E,
       S(".....ttt"),
       S("....tttt"),
       S("...ttTcc"),
       S("...ttc.."),
       S("...ttc.."),
       S("...ttTcc"),
       S("....tttt"),
       S(".....ttt"),
       "..........ttt...",
       "...........tt...",
       E, E, E)


# --------------------------------------------------------------------------- batch 3: sweets, food, joke shop

sprite("canary_cream", {"b": ("pastry", "#E0B870"), "c": ("foam", "#FFF4D8"),
                        "y": ("feather", "#F2D030")},
       E, E, E,
       "...........yy...",
       "..........yyy...",
       S("..bbbbbb"),
       S(".bbbbbbb"),
       S(".ccccccc"),
       S(".bbbbbbb"),
       S("..bbbbbb"),
       E, E, E, E, E, E)

sprite("chocolate_bar", {"w": ("cloth", "#5A2A6A"), "W": ("cloth", "#5A2A6A", "shade"),
                         "f": ("gold", "light"), "c": "chocolate", "C": ("chocolate", "dark")},
       E, E, E,
       "..wwwwwwwffffff.",
       "..wWWWWWWfcCcCf.",
       "..wwffwwwfcccCf.",
       "..wwffwwwfCcCcf.",
       "..wWWWWWWfcccCf.",
       "..wwwwwwwffffff.",
       E, E, E, E, E, E, E)

sprite("conjured_spoiled_food", {"p": ("silver", "#C8C4B8"), "m": ("weed", "#7A8A3A"),
                                 "M": ("weed", "#5A6A2A", "dark"), "f": ("ink", "base")},
       E, E,
       "......f.....f...",
       "....f...........",
       "........f.......",
       S("....mmmm"),
       S("...mmMmm"),
       S("..mmmmmM"),
       S(".ppppppp"),
       S("pppppppp"),
       S(".ppppppp"),
       E, E, E, E, E)

sprite("droobles_best_blowing_gum", {"b": ("candy", "#6AA8E8"), "*": ("candy", "#6AA8E8", "spec"),
                                     "w": ("paper", "#F0E8F4"), "s": ("candy", "#E85AA8")},
       E,
       S(".....bbb"),
       S("...bbbbb"),
       S("..b*bbbb", "b"),
       S("..b*bbbb", "b"),
       S("..bbbbbb"),
       S("...bbbbb"),
       S(".....bbb"),
       S(".......w"),
       S(".....www"),
       S("....wsws"),
       S("....wwww"),
       S("....wsws"),
       S(".....www"),
       E, E)

# Skiving Snackbox sweets: two-tone chews, one end makes you ill, the other cures it.
def _chew(name, a, b):
    sprite(name, {"a": ("candy", a), "b": ("candy", b), "w": ("paper", "#F4ECE0")},
           E, E, E, E,
           "..w..........w..",
           "..ww.aaabbb.ww..",
           "..wwaaaabbbbww..",
           "..wwaaaabbbbww..",
           "..ww.aaabbb.ww..",
           "..w..........w..",
           E, E, E, E, E, E)


_chew("fainting_fancies", "#E8783A", "#8A4ACB")
_chew("puking_pastilles", "#8AC84A", "#E8783A")

sprite("fever_fudge", {"f": ("chocolate", "#A86A3A"), "r": ("candy", "#D8302A", "base"),
                       "*": ("chocolate", "#A86A3A", "light")},
       E, E, E, E,
       S("...fffff"),
       S("..f*ffff", "f"),
       S("..fffrff"),
       S("..frffff"),
       S("..ffffrf"),
       S("..ffffff"),
       S("...fffff"),
       E, E, E, E, E)

sprite("nosebleed_nougat", {"n": ("foam", "#F4E4D4"), "p": ("candy", "#E8A0B0"),
                            "d": ("liquid", "#B81E2A")},
       E, E, E, E,
       "...nnnnnnnnnn...",
       "..nnpnnpnnpnnn..",
       "..nnnnnnnnnnnn..",
       "..npnnpnnpnnpn..",
       "..nnnnnnnnnnnn..",
       "...nnnnnnnnnnd..",
       ".............d..",
       "............ddd.",
       ".............d..",
       E, E, E)

sprite("peppermint_toad", {"t": ("candy", "#8AD8A8"), "e": ("candy", "#F4FFF8", "spec"),
                           "k": ("ink", "base"), "s": ("candy", "#E8F8EE", "light")},
       E, E, E, E,
       "....ee....ee....",
       "...ekt....tke...",
       S("...ttttt"),
       S("..tttttt"),
       S(".ttsstst"),
       S(".ttttttt"),
       "..tt.tttttt.tt..",
       ".tt..........tt.",
       E, E, E, E)

sprite("skiving_snackbox", {"o": ("cloth", "#E0782A"), "O": ("cloth", "#E0782A", "shade"),
                            "p": ("cloth", "#7A2A8A"), "y": ("gold", "light")},
       E, E, E,
       S("..oooooo"),
       S(".OOOOOOO"),
       S(".ooooooo"),
       S(".oppppoo").replace("oo", "oo"),
       S(".opyyyyp"),
       S(".oppppoo"),
       S(".ooooooo"),
       S(".ooooooo"),
       S(".OOOOOOO"),
       E, E, E, E)

sprite("ton_tongue_toffee", {"t": ("candy", "#C8782A"), "w": ("paper", "#E8C8A0"),
                             "r": ("candy", "#E0587A")},
       E, E, E, E, E,
       ".w............w.",
       ".ww..tttttt..ww.",
       ".wwwttttttttwww.",
       ".wwwttttttttwww.",
       ".ww..tttttt..ww.",
       ".w......rr....w.",
       ".......rrr......",
       "........r.......",
       E, E, E)

sprite("treacle_tart", {"c": ("pastry", "#D8A050"), "t": ("liquid", "#C07A1E"),
                        "l": ("pastry", "#E8C078", "light")},
       E, E, E, E, E,
       S(".......c"),
       S(".....ccc"),
       S("...ccttt"),
       S(".cctltlt"),
       S("cctttttt"),
       S("cltltltl"),
       S("cccccccc"),
       E, E, E, E)

sprite("u_no_poo", {"b": ("cloth", "#6A3A8A"), "B": ("cloth", "#6A3A8A", "shade"),
                    "l": ("paper", "#F0E4C8"), "p": ("candy", "#8AC84A")},
       E, E, E,
       S("....bbbb"),
       S("...BBBBB"),
       S("...lllll"),
       S("...lpplp"),
       S("...lllll"),
       S("...bbbbb"),
       S("...bbbbb"),
       S("...BBBBB"),
       E, E, E, E, E)

sprite("dungbomb", {"d": ("stone", "#6A4A2A"), "f": ("cloth", "#C8B888"), "s": ("candy", "#F2B030", "base"),
                    "v": ("smoke", "#8A8A5A", "base")},
       ".........v..v...",
       "..........vv....",
       "........s.......",
       ".......f........",
       ".......f........",
       S(".....ddd"),
       S("...ddddd"),
       S("..dddddd"),
       S("..dddddd"),
       S("..dddddd"),
       S("...ddddd"),
       S(".....ddd"),
       E, E, E, E)

sprite("decoy_detonator", {"k": ("ink", "#24202A"), "*": ("ink", "#24202A", "light"),
                           "h": ("gold", "#B8903A"), "l": ("ink", "#24202A", "dark")},
       E, E, E,
       "............hh..",
       "...........hh...",
       S("...kkkkk"),
       S("..k*kkkk", "k"),
       S(".kkkkkkk"),
       S(".kkkkkkk"),
       S("..kkkkkk"),
       "..l.l.l..l.l.l..",
       ".l..l..ll..l..l.",
       E, E, E, E)

sprite("extendable_ears", {"s": ("root", "#E0B090"), "e": ("root", "#E0A088")},
       E,
       "..eee...........",
       ".eeeee..........",
       ".ee.ee..........",
       ".eee.s..........",
       "..ee..s.........",
       "......s.........",
       ".......s........",
       "........ss......",
       "..........ss....",
       "............s...",
       "............s...",
       "...........ss...",
       "..........ss....",
       E, E)

sprite("filibusters_fireworks", {"r": ("cloth", "#C0302A"), "w": ("paper", "#F0E8D8"),
                                 "c": ("cloth", "#E8C83A"), "s": ("wood", "#9A7A4A")},
       E,
       "............c...",
       "...........ccc..",
       "..........rrc...",
       ".........rrrr...",
       "........wrrr....",
       ".......rwwr.....",
       "......rrrw......",
       ".....rrrr.......",
       "......rr........",
       ".....s..........",
       "....s...........",
       "...s............",
       "..s.............",
       E, E)

sprite("wildfire_whiz_bangs", {"b": ("cloth", "#2A3A8A"), "B": ("cloth", "#2A3A8A", "shade"),
                               "o": ("cloth", "#E8782A"), "y": ("candy", "#FFE070", "base"),
                               "r": ("cloth", "#C0302A")},
       "....y......y....",
       ".y....y..y...y..",
       "....ro..or......",
       "....ro..or......",
       "...rrooooorr....",
       "..bbbbbbbbbbb...",
       "..BBBBBBBBBBB...",
       "..byyyybbyyyb...",
       "..bbbbbbbbbbb...",
       "..byybbyyybbb...",
       "..bbbbbbbbbbb...",
       "..BBBBBBBBBBB...",
       E, E, E, E)

sprite("exploding_snap", {"w": ("paper", "#F0E8D8"), "W": ("paper", "#F0E8D8", "shade"),
                          "r": ("cloth", "#B83A2A"), "s": ("candy", "#FFD040", "base")},
       "...........s....",
       ".........s.s.s..",
       "..........sss...",
       ".........s.s.s..",
       "..wwwwwww.......",
       "..wrrrrrwww.....",
       "..wrwwwrwWw.....",
       "..wrwrwrwWw.....",
       "..wrwwwrwWw.....",
       "..wrrrrrwWw.....",
       "..wwwwwwwWw.....",
       "...WWWWWWWw.....",
       E, E, E, E)

sprite("portable_swamp", {"j": ("glass", "#BCD4C0"), "m": ("weed", "#5A6A2A"),
                          "M": ("weed", "#3A4A1E", "dark"), "r": ("leaf", "#6A8A3A"),
                          "c": "cork"},
       E,
       "......r..r......",
       ".....r..r.......",
       S(".....ccc"),
       S("....jjjj"),
       S("...jmmmm"),
       S("...jmMmm"),
       S("...jmmmm"),
       S("...jmmMm"),
       S("...jmmmm"),
       S("...jmMmm"),
       S("....jjjj"),
       E, E, E, E)

sprite("gobstones", {"a": ("stone", "#6A5A8A"), "b": ("stone", "#8A4A3A"), "c": ("stone", "#4A6A6A"),
                     "*": ("stone", "#E8E0F0", "spec")},
       E, E, E, E,
       "......aaaa......",
       ".....a*aaaa.....",
       ".....aaaaaa.....",
       ".....aaaaaa.....",
       "..bbbbaaaacccc..",
       ".bb*bbb.cc*cccc.",
       ".bbbbbb..cccccc.",
       ".bbbbbb..cccccc.",
       "..bbbb....cccc..",
       E, E, E)

sprite("wizards_chess_set", {"d": ("darkwood", "base"), "l": ("wood", "#D8B888", "base"),
                             "k": ("ink", "#2A2226"), "w": ("ivory", "light"), "e": "darkwood"},
       E,
       ".......k........",
       "......kkk.......",
       ".......k........",
       "......kkk.......",
       ".....kkkkk......",
       ".eeeeeeeeeeeeee.",
       ".edldldldldldle.",
       ".eldldldldldlde.",
       ".edldldldldldle.",
       ".eldldldldldlde.",
       ".eeeeeeeeeeeeee.",
       E, E, E, E)

sprite("dirigible_plum", {"o": ("candy", "#E8883A"), "*": ("candy", "#E8883A", "spec"),
                          "l": ("leaf", "#5A8A3A"), "s": ("wood", "#6A4A2A")},
       E,
       S("......oo"),
       S(".....ooo"),
       S("....o*oo", "o"),
       S("....o*oo", "o"),
       S("....oooo"),
       S("....oooo"),
       S(".....ooo"),
       S("......oo"),
       S(".......s"),
       S(".....lls"),
       S("....ll.s"),
       S(".......s"),
       E, E, E)


# --------------------------------------------------------------------------- batch 4: creature materials, ingredients

# Hair comes in three cuts so a chest of wand cores does not read as eight copies of one sprite:
# a hank bound at the top, a loose flowing lock, and a single coiled strand.
def _strands(count, spacing, slope, bound):
    """Wavy strands of hair, each a 4-connected pixel path so it shades as one region, spaced so
    the outline pass lays a dark line between neighbours -- which is what makes it read as hair
    rather than as a solid blade. `bound` ties them together at the top in a band ('b')."""
    import math
    grid = [["."] * 16 for _ in range(16)]
    top = 3 if bound else 1
    for i in range(count):
        prev = None
        for y in range(top, 15 - (i % 2)):
            xf = 11.5 - (y - top) * slope + 1.1 * math.sin(y / 2.1 + i * 1.3) + (i - (count - 1) / 2) * spacing
            x = max(1, min(14, int(round(xf))))
            lo, hi = (x, x) if prev is None else (min(x, prev), max(x, prev))
            for xx in range(lo, hi + 1):
                grid[y][xx] = "h"
            prev = x
    if bound:
        for y in (2, 3):
            for x in range(16):
                if grid[3][x] == "h" or grid[4][x] == "h":
                    grid[y][x] = "b" if y == 3 else ("B" if grid[3][x] == "h" else ".")
    # One highlight run down the middle strand, lit side.
    return ["".join(r) for r in grid]


HANK = tuple(_strands(4, 2, 0.35, True))
LOCK = tuple(_strands(3, 2, 0.72, False))

STRAND = (
    E, E,
    ".......hhhh.....",
    "......h....h....",
    ".....h..hh..h...",
    ".....h.h..h.h...",
    ".....h.h.h..h...",
    ".....h..h..h....",
    "......h...h.....",
    ".......hhh......",
    ".........h......",
    "..........h.....",
    "...........h....",
    "............h...",
    E, E,
)


def hair(name, rows, colour, band="#8A6A3A"):
    sprite(name, {"h": ("hair", colour), "*": ("hair", colour, "spec"),
                  "b": ("cloth", band), "B": ("cloth", band, "shade")}, *rows)


hair("demiguise_hair", LOCK, "#C8D8E8")
for i, a in enumerate((255, 204, 153, 102, 50)):
    faded(f"demiguise_hair_fade_{i}", "demiguise_hair", a)
hair("granian_hair", HANK, "#A8A4A0", band="#5A3A6A")
hair("rougarou_hair", HANK, "#3A2E28", band="#8A2A22")
hair("thestral_tail_hair", STRAND, "#2A2630")
hair("veela_hair", LOCK, "#F2E0A0")
hair("wampus_cat_hair", HANK, "#5A5A62", band="#2A6A5A")

sprite("dragon_heartstring", {"h": ("liquid", "#B8202A"), "*": ("liquid", "#FF8A60", "spec")},
       E, E,
       ".......hhhh.....",
       "......h*...h....",
       ".....h..hh..h...",
       ".....h.h..h.h...",
       ".....h.h.h..h...",
       ".....h..h..h....",
       "......h...h.....",
       ".......hhh......",
       "........h.......",
       ".........h......",
       "..........hh....",
       "............h...",
       E, E)

sprite("golden_snidget_feather", {"f": ("gold", "#F2C040"), "s": ("ivory", "#FFF0C0")},
       E, E, E,
       "..........ff....",
       ".........ffff...",
       "........ffsff...",
       ".......ffsff....",
       "......ffsff.....",
       ".....ffsff......",
       ".....fsff.......",
       "......s.........",
       ".....s..........",
       "....s...........",
       E, E, E)

sprite("thunderbird_tail_feather", {"f": ("feather", "#3A5A8A"), "l": ("candy", "#F4F0A0", "base"),
                                    "s": ("ivory", "#D8E0F0")},
       E,
       "...........ff...",
       "..........ffff..",
       ".........fflff..",
       "........fflfff..",
       ".......ffflff...",
       "......fflfsf....",
       ".....fflfsf.....",
       "....fffsff......",
       "....ffsff.......",
       ".....sf.........",
       "....s...........",
       "...s............",
       "..s.............",
       E, E)

sprite("erumpent_horn", {"i": ("ivory", "#D8CCB0"), "r": ("ivory", "#A89878", "shade")},
       E,
       "............ii..",
       "...........iii..",
       "..........iiri..",
       ".........iiii...",
       "........irii....",
       ".......iiiii....",
       "......iiriii....",
       ".....iiiiii.....",
       "....iiriiii.....",
       "...iiiiiii......",
       "...iriiiii......",
       "...iiiiii.......",
       "....iiii........",
       E, E)

sprite("hidebehind_claw", {"c": ("stone", "#4A4448"), "*": ("stone", "#4A4448", "light")},
       E, E,
       "....cc..........",
       "...c*cc.........",
       "...cccc.........",
       "....cccc........",
       ".....cccc.......",
       "......cccc......",
       ".......cccc.....",
       "........ccc.....",
       ".........cc.....",
       ".........c......",
       "........c.......",
       E, E, E)

sprite("hidebehind_shadow_essence", dict(GLASS, l=("liquid", "#1E1A26"), m=("smoke", "#5A4A7A")),
       E,
       S("......cc"),
       S("......cC"),
       S(".....ggg"),
       S("....gmll"),
       S("...g*llm", "l"),
       S("...gllml"),
       S("...glmll"),
       S("...gmlll"),
       S("...gllll"),
       S("....glll"),
       S(".....ggg"),
       E, E, E, E)

sprite("horned_serpent_gem", {"a": ("glass", "#3AB8A8"), "d": ("glass", "#1E7A7A", "dark"),
                              "*": ("glass", "#E8FFF8", "spec"), "o": ("gold", "base")},
       E, E, E,
       S(".....aaa"),
       S("....a*aa", "a"),
       S("...a*aaa", "a"),
       S("..aaaaaa"),
       S("..dddddd"),
       S("...ddddd"),
       S("....dddd"),
       S(".....ddd"),
       S("......dd"),
       E, E, E, E)

sprite("mooncalf_dung", {"d": ("silver", "#B8C4D8"), "*": ("silver", "#F4F8FF", "spec")},
       E, E, E, E, E,
       "......dd........",
       ".....d*dd.......",
       ".....dddd.dd....",
       "...dd.dd.d*dd...",
       "..d*dd...dddd...",
       "..dddd.dd.dd....",
       "...dd.d*dd......",
       "......ddd.......",
       E, E, E)

sprite("occamy_eggshell", {"s": ("silver", "#D8DEE8"), "i": ("silver", "#8A92A0", "shade")},
       E, E, E, E,
       "....s.s..s.s....",
       "...sisisisisi...",
       "..siiiiiiiiiis..",
       "..ssiiiiiiiiss..",
       "..sssssssssss...",
       "..ssssssssssss..",
       "...ssssssssss...",
       "....ssssssss....",
       ".....ssssss.....",
       E, E, E)

sprite("pukwudgie_venom_sac", {"v": ("weed", "#7A5A9A"), "*": ("weed", "#C8A8E8", "spec"),
                               "t": ("root", "#8A6A5A")},
       E, E,
       S(".......t"),
       S(".......t"),
       S("......vv"),
       S("....vvvv"),
       S("...v*vvv", "v"),
       S("..v*vvvv", "v"),
       S("..vvvvvv"),
       S("..vvvvvv"),
       S("...vvvvv"),
       S("....vvvv"),
       S("......vv"),
       E, E, E)

sprite("troll_whisker", {"w": ("stone", "#7A7A5A"), "r": ("root", "#8A6A4A")},
       E,
       "...........r....",
       "..........rw....",
       ".........ww.....",
       "........ww......",
       ".......ww.......",
       "......ww........",
       "......w.........",
       ".....ww.........",
       "....ww..........",
       "...ww...........",
       "..ww............",
       "..w.............",
       E, E, E)

sprite("white_river_monster_spine", {"i": ("ivory", "#EEE8D8"), "s": ("ivory", "#B8B0A0", "shade")},
       E, E,
       ".......i........",
       "......iii.......",
       "......iii.......",
       "......isi.......",
       "...iiiiiiiii....",
       "..iisiiiiisii...",
       "...iiiiiiiii....",
       "......isi.......",
       "......iii.......",
       ".....iiiii......",
       "......iii.......",
       E, E, E)

sprite("yeti_fur", {"f": ("hair", "#F0F2F6"), "s": ("hair", "#B8C0CC", "shade")},
       E, E, E,
       "......f.f.......",
       "....f.ffff.f....",
       "...fffffffff....",
       "..ffffsffffff...",
       "..fffffffsfff...",
       ".ffsffffffffff..",
       "..ffffffsffff...",
       "..fffffffffff...",
       "...f.ff.ff.f....",
       E, E, E, E)

sprite("mandrake", {"r": ("root", "#B8906A"), "k": ("ink", "#3A2414", "base"), "l": "leaf",
                    "L": ("leaf", "#3E7A2A"), "m": ("liquid", "#6A2A1E", "dark")},
       "...L..l..L..l...",
       "....LlLlLlL.....",
       ".....LlllL......",
       "......rrrr......",
       ".....rrrrrr.....",
       "....rkrrrrkr....",
       "....rrrrrrrr....",
       "....rrmmmmrr....",
       "..rrrrmmmmrrrr..",
       ".rr.rrrrrrrr.rr.",
       "....rrrrrrrr....",
       ".....rrrrrr.....",
       ".....rr..rr.....",
       "....rr....rr....",
       "...r........r...",
       E)

sprite("mandrake_seeds", {"p": ("paper", "#C8A878"), "P": ("paper", "#C8A878", "shade"),
                          "s": ("root", "#6A4A2A"), "t": ("cloth", "#3E7A2A")},
       E, E, E,
       S(".......t"),
       S("......tt"),
       S(".....ppp"),
       S("....pppp"),
       S("...ppppp"),
       S("...ppPPp"),
       S("...ppppp"),
       S("....pppp"),
       "............s...",
       "..s......s....s.",
       ".....s..........",
       E, E)

sprite("shrunken_head", {"f": ("root", "#6A5A3A"), "h": ("hair", "#2A2420"), "k": ("ink", "base"),
                         "s": ("cloth", "#C8B888")},
       E,
       S(".....hhh"),
       S("....hhhh"),
       S("...hhfff"),
       S("...hffff"),
       S("...fkfff"),
       S("...fffff"),
       S("...ffkff"),
       S("....fsss"),
       S(".....fff"),
       S("......hh"),
       S(".....h.h"),
       S("....h..h"),
       E, E, E)

sprite("hand_of_glory", {"h": ("root", "#8A7A5A"), "c": ("foam", "#F0E8C8"),
                         "f": ("candy", "#F2A030", "base"), "y": ("candy", "#FFF0A0", "base")},
       "........y.......",
       ".......yfy......",
       "........f.......",
       ".......ccc......",
       ".......ccc......",
       "...h.h.ccch.h...",
       "...h.hhccchh....",
       "...hhhhhhhhh....",
       "....hhhhhhh.....",
       "....hhhhhhh.....",
       ".....hhhhh......",
       ".....hhhhh......",
       "......hhh.......",
       "......hhh.......",
       E, E)


# --------------------------------------------------------------------------- batch 5: robes, masks, hats, gloves
#
# Three robe sets share cut but not cloth: the Auror in navy with Ministry gold, the Death Eater
# in black with silver clasps and a torn hem, the student in black lined with house red.

ROBE_CHEST = (
    E, E,
    S("...rrl.."),
    S(".rrrrl.."),
    S("rrrrrltt"),
    S("rrrrrrtc"),
    S("rr.rrrtt"),
    S("rr.rrrtc"),
    S("...rrrtt"),
    S("...rrrtc"),
    S("...rrrtt"),
    S("...rrrtt"),
    S("...hhhhh"),
    E, E, E,
)
ROBE_LEGS = (
    E, E,
    S("...ttttt"),
    S("...rrrrr"),
    S("...rrrrr"),
    S("...rrrr."),
    S("...rrrr."),
    S("...rrrr."),
    S("...rrrr."),
    S("...rrrr."),
    S("...rrrr."),
    S("...hhhh."),
    E, E, E, E,
)
ROBE_BOOTS = (
    E, E, E, E, E, E,
    S("..rrr..."),
    S("..rrr..."),
    S("..rrr..."),
    S("..rrr..."),
    S("..rrrrr."),
    S(".rrrrrr."),
    S(".hhhhhh."),
    E, E, E,
)


def robe_set(prefix, cloth, trim, clasp, hem, lining=None):
    legend = {"r": ("cloth", cloth), "t": trim, "c": clasp, "h": hem,
              "l": lining or ("cloth", cloth, "light")}
    sprite(prefix + "_chest", legend, *ROBE_CHEST)
    sprite(prefix + "_legs", legend, *ROBE_LEGS)
    sprite(prefix + "_boots", dict(legend, r=("leather", cloth)), *ROBE_BOOTS)


# Cloths match the worn sets in `armor_model.py` (texture audit 2026-09-27): the Auror's coat is
# blue with silver — its brief rules gold out — the school robe warm black, the Death Eater's the
# coolest and darkest. The icon and the worn robe are the same object.
robe_set("auror_robe", "#26385C", ("silver", "shade"), ("silver", "light"), ("silver", "dark"))
robe_set("death_eater_robe", "#24242F", ("cloth", "#24242F", "shade"), ("silver", "light"),
         ("cloth", "#24242F", "dark"))
robe_set("student_robe", "#3A3434", ("cloth", "#3A3434", "shade"), ("gold", "#B8903A", "base"),
         ("cloth", "#3A3434", "dark"), lining=("cloth", "#9A2A26"))

sprite("quidditch_robes", {"r": ("cloth", "#A8231E"), "g": ("gold", "light"), "w": ("paper", "#F4ECD8")},
       E, E,
       S("...rrr.."),
       S(".rrrrrgg"),
       S("rrrrrrrr"),
       S("rrrrwwrr"),
       S("rr.rwrrr"),
       S("rr.rwwrr"),
       S("...rrwrr"),
       S("...rwwrr"),
       S("...rrrrr"),
       S("...ggggg"),
       E, E, E, E)

# The masks are beaten silver. Each variant is the same face with its own motif; the base item
# shows the classic.
MASK = (
    E,
    S("....mmmm"),
    S("...mmmmm"),
    S("..mmmmmm"),
    S("..m*mmmm", "m"),
    S("..mmkkmm"),
    S("..mmkkmm"),
    S("..mmmmmm"),
    S("..mmmmmm"),
    S("...mmmmm"),
    S("...mmmmm"),
    S("....mmmm"),
    S(".....mmm"),
    S("......mm"),
    E, E,
)


def mask(name, motif=(), extra=None):
    legend = {"m": "plate", "*": ("plate", "spec"), "k": ("ink", "base"),
              "d": ("plate", "dark"), "o": ("ink", "#2A2426", "base")}
    legend.update(extra or {})
    rows = list(MASK)
    for (y, x, ch) in motif:
        r = list(rows[y])
        r[x] = ch
        rows[y] = "".join(r)
    sprite(name, legend, *rows)


def _mirror(points):
    return points + [(y, 15 - x, ch) for (y, x, ch) in points]


CLASSIC = _mirror([(2, 6, "d"), (3, 5, "d"), (8, 4, "d"), (9, 5, "d"), (10, 6, "d")])
mask("death_eater_mask", CLASSIC)
mask("death_eater_mask_1", CLASSIC)
mask("death_eater_mask_2", [(2, 5, "d"), (3, 6, "d"), (4, 7, "d"), (8, 8, "d"), (9, 7, "d"),
                            (10, 8, "d"), (11, 9, "d"), (12, 8, "d"), (3, 10, "d"), (4, 9, "d")])
mask("death_eater_mask_3", _mirror([(10, 6, "o"), (10, 7, "o"), (11, 6, "o"), (11, 7, "o"),
                                    (12, 7, "o")]))
mask("death_eater_mask_4", _mirror([(1, 4, "m"), (0, 4, "m"), (1, 5, "d"), (3, 4, "d")]))
mask("death_eater_mask_5", [(1, 9, "d"), (2, 9, "d"), (3, 8, "d"), (4, 9, "d"), (7, 9, "d"),
                            (8, 10, "d"), (9, 10, "d"), (10, 11, "d"), (5, 4, "d"), (6, 3, "d")])

HAT = (
    ".........kk.....",
    "........kkk.....",
    ".......kkkk.....",
    ".......kkkk.....",
    "......kkkkk.....",
    "......kkkkkk....",
    ".....kkkkkkk....",
    ".....kkkkkkk....",
    "....kkkkkkkkk...",
    "....bbbbbbbbb...",
    "..kkkkkkkkkkkkk.",
    ".kkkkkkkkkkkkkkk",
    "..kkkkkkkkkkkkk.",
    E, E, E,
)


def hat(name, cloth, band, extra=None, marks=()):
    legend = {"k": ("cloth", cloth), "b": band, "s": ("candy", "#F2E07A", "base")}
    legend.update(extra or {})
    rows = [r.replace("kkkkkkkkkkkkkkk", "kkkkkkkkkkkkkk.") if i == 11 else r for i, r in enumerate(HAT)]
    for (y, x, ch) in marks:
        r = list(rows[y])
        r[x] = ch
        rows[y] = "".join(r)
    sprite(name, legend, *rows)


hat("wizard_hat", "#35333F", ("cloth", "#6A2A7A"))
hat("headless_hat", "#7A3A8A", ("gold", "base"), marks=[(4, 8, "s"), (6, 9, "s"), (7, 6, "s")])

sprite("sorting_hat", {"h": ("leather", "#7A5A3A"), "H": ("leather", "#5A3E24", "shade"),
                       "k": ("ink", "#2A1E14", "base"), "p": ("leather", "#9A7A4A")},
       "..........hh....",
       ".........hhh....",
       "........hhhh....",
       "........hhh.....",
       ".......hhhhh....",
       "......hHhhhh....",
       "......hkhhkh....",
       ".....hhhhhhhh...",
       ".....hHkkkkhh...",
       "....hhhhphhhhh..",
       "..hhhhhhhhhhhhh.",
       ".hhhHhhhhhHhhhh.",
       "..hhhhhhhhhhhh..",
       E, E, E)

sprite("shield_hat", {"b": ("cloth", "#2A4A8A"), "B": ("cloth", "#2A4A8A", "shade"),
                      "s": ("silver", "light"), "S": ("silver", "shade")},
       E, E, E, E,
       S(".....bbb"),
       S("....bbbb"),
       S("...bbbbs"),
       S("...bbbsS"),
       S("...bbbbs"),
       S("...BBBBB"),
       S(".bbbbbbb"),
       S("..bbbbbb"),
       E, E, E, E)

sprite("shield_cloak", {"b": ("cloth", "#2A4A8A"), "l": ("cloth", "#8AA8D8"), "s": ("silver", "light"),
                        "c": ("silver", "base")},
       E,
       S("...cbbbb"),
       S("..bbbbbb"),
       S("..bbbbbb"),
       S(".bbbbbbs"),
       S(".bbbbbss"),
       S(".bbbbbbs"),
       S("bbbbbbbb"),
       S("bbbbbbbb"),
       S("bbbbbbbb"),
       S("bbbbbbbb"),
       S("bblbblbb"),
       S("b..b..b."),
       E, E, E)

GLOVE = (
    E, E,
    ".....g.g........",
    "....gg.gg.g.....",
    "....gg.gg.gg....",
    "..g.gg.gg.gg....",
    "..gggggggggg....",
    "..gggggggggg....",
    "...ggggggggg....",
    "...ggggggggg....",
    "....ccccccc.....",
    "....ccccccc.....",
    E, E, E, E,
)
sprite("shield_gloves", {"g": ("cloth", "#2A4A8A"), "c": ("silver", "base")}, *GLOVE)
sprite("dragon_hide_gloves", {"g": ("leather", "#5A6A3A"), "c": ("leather", "#3A4424")}, *GLOVE)

sprite("earmuffs", {"p": ("hair", "#F4A8C8"), "b": ("silver", "#8A8E96")},
       E, E,
       S(".....bbb"),
       S("...bb..."),
       S("..b....."),
       S("..b....."),
       S(".ppp...."),
       S("ppppp..."),
       S("ppppp..."),
       S("ppppp..."),
       S(".ppp...."),
       E, E, E, E, E)

sprite("blindfold", {"c": ("cloth", "#2A2430"), "C": ("cloth", "#2A2430", "shade")},
       E, E, E, E, E,
       ".cccccccccccc...",
       ".CCCCCCCCCCCcc..",
       ".cccccccccccc.c.",
       "...........c..c.",
       "..........c....c",
       "..........c.....",
       E, E, E, E, E)


# --------------------------------------------------------------------------- batch 6: brooms, Quidditch, transport


def _broom_rows(tail, mark):
    """A broom on the diagonal, handle top-right to tail bottom-left. The tail is individual twig
    streaks fanning out from the binding -- a solid wedge reads as a paddle. `tail` sets the fan
    (0 a fat household splay .. 2 a racing broom's tight birch); `mark` puts a gold registration
    fleck on the handle. Alternate streaks take the darker tone so the twigs separate."""
    grid = [["."] * 16 for _ in range(16)]
    for i in range(7):
        grid[1 + i][13 - i] = grid[1 + i][14 - i] = "h"
    if mark:
        grid[4][10] = grid[4][11] = "g"
    spread, count, length = ((0.42, 7, 7), (0.30, 6, 7), (0.18, 5, 7))[tail]
    streaks = {}
    for k in range(count):
        i = k - (count - 1) / 2
        for t in range(length - (k % 2)):
            x = int(round(7 - t + i * spread * t))
            y = int(round(8 + t + i * spread * t))
            if 1 <= x <= 14 and 1 <= y <= 14:
                streaks[(x, y)] = k
    # Fill each row between its outermost twigs so the tail is one solid region (gaps between
    # streaks would each get an outline and turn the tail into a net), then lay every other
    # streak in the darker tone. The last row stays ragged: only the twig ends themselves.
    last = max(y for _, y in streaks)
    for y in range(16):
        xs = [x for (x, yy) in streaks if yy == y]
        if not xs:
            continue
        span = range(min(xs), max(xs) + 1) if y < last else xs
        for x in span:
            grid[y][x] = "b"
    for (x, y), k in streaks.items():
        if k % 2:
            grid[y][x] = "B"
    grid[7][7] = grid[7][8] = grid[6][8] = "n"
    return ["".join(r) for r in grid]


def broom(name, wood, twigs, band, tail, mark=False):
    sprite(name, {"h": ("wood", wood), "b": ("wood", twigs), "B": ("wood", twigs, "shade"),
                  "n": band, "g": ("gold", "light")}, *_broom_rows(tail, mark))


broom("broom", "#B08050", "#D8B870", ("cloth", "#8A6A3A"), 0)
broom("oakshaft_79", "#5E3E22", "#A88A5A", ("bronze", "base"), 0)
broom("cleansweep_seven", "#9A6A3A", "#C8A870", ("silver", "base"), 1)
broom("comet_260", "#A0522D", "#C89A6A", ("gold", "base"), 1, mark=True)
broom("nimbus_2000", "#5A2A1A", "#8A6A4A", ("gold", "light"), 2, mark=True)
broom("nimbus_2001", "#2A2224", "#6A5A4A", ("silver", "light"), 2, mark=True)
broom("firebolt", "#3A2A20", "#B89A6A", ("gold", "light"), 2, mark=True)
broom("firebolt_supreme", "#2A1E1A", "#C8A060", ("liquid", "#E0502A", "light"), 2, mark=True)

sprite("enchanted_twig_bundle", {"t": ("wood", "#B89A6A"), "T": ("wood", "#B89A6A", "shade"),
                                 "c": ("cloth", "#8A2A22")},
       E, E,
       "...t.t..t.t.t...",
       "...tt.tT.tt.t...",
       "....ttTttTtt....",
       "....tTttTttt....",
       "....ttTttTtt....",
       "....cccccccc....",
       "....tTttTttt....",
       "....ttTttTtt....",
       "...tTtt.tTttt...",
       "...t.tt.t.t.t...",
       "..t..t...t...t..",
       E, E, E)

sprite("broomstick_servicing_kit", {"w": ("leather", "#6A3A22"), "W": ("leather", "#6A3A22", "shade"),
                                    "k": ("gold", "base"), "h": ("leather", "#3A2214")},
       E, E, E,
       S("......hh"),
       S(".....h.."),
       S("..wwwwww"),
       S(".wwwwwww"),
       S(".WWWWWkk"),
       S(".wwwwwwk"),
       S(".wwwwwww"),
       S(".wwwwwww"),
       S(".WWWWWWW"),
       E, E, E, E)

sprite("quaffle", {"r": ("leather", "#A8281E"), "s": ("leather", "#6A1410", "dark")},
       E, E,
       S(".....rrr"),
       S("...rrrrr"),
       S("..rrsrrr"),
       S("..rrrrrr"),
       S(".rsrrrrs"),
       S(".rrrrrrr"),
       S(".rrrrrrr"),
       S(".rsrrrrs"),
       S("..rrrrrr"),
       S("..rrsrrr"),
       S("...rrrrr"),
       S(".....rrr"),
       E, E)

sprite("bludger", {"b": ("silver", "#4A4A52"), "*": ("silver", "#4A4A52", "spec"),
                   "r": ("silver", "#4A4A52", "dark")},
       E, E,
       S(".....bbb"),
       S("...bbbbb"),
       S("..b*bbbb", "b"),
       S("..b*brbb", "b"),
       S(".bbbbbbb"),
       S(".brbbbbr"),
       S(".bbbbbbb"),
       S(".bbbbbbb"),
       S("..bbbrbb"),
       S("..bbbbbb"),
       S("...bbbbb"),
       S(".....bbb"),
       E, E)

sprite("flying_ford_anglia", {"c": ("cloth", "#4AA8B8"), "C": ("cloth", "#4AA8B8", "shade"),
                              "g": ("glass", "#C8E4EC"), "w": ("ink", "#1E1E24"),
                              "s": ("silver", "light")},
       E, E, E, E,
       "......cccccc....",
       ".....cggcggcc...",
       "....cgggcgggcc..",
       ".scccccccccccccs",
       ".cCCCCCCCCCCCCc.",
       ".ccccccccccccccs",
       "..www......www..",
       "..www......www..",
       E, E, E, E)

sprite("knight_bus", {"p": ("cloth", "#5A2A8A"), "P": ("cloth", "#5A2A8A", "shade"),
                      "g": ("glass", "#F2E0A0"), "w": ("ink", "#1E1E24"), "y": ("gold", "light")},
       E,
       ".ppppppppppppp..",
       ".pgpgpgpgpgpgp..",
       ".ppppppppppppp..",
       ".PPPPPPPPPPPPP..",
       ".pgpgpgpgpgpgp..",
       ".ppppppppppppp..",
       ".PPPPPPPPPPPPP..",
       ".pgpgpgpgpgpgpp.",
       ".ppppppppppppppy",
       ".ppyyyyyyyppppp.",
       "..ww.......ww...",
       "..ww.......ww...",
       E, E, E)

sprite("thestral_carriage", {"k": ("darkwood", "#241E22"), "K": ("darkwood", "#241E22", "shade"),
                             "g": ("glass", "#8A8EA0"), "w": ("darkwood", "#3A2E26"),
                             "l": ("candy", "#F2C060", "base")},
       E, E,
       "....kkkkkkkk....",
       "...kkkkkkkkkk...",
       "...kggkkkggkk...",
       "...kggkkkggkk.l.",
       "...kkkkkkkkkkk..",
       "...KKKKKKKKKK...",
       "..kkkkkkkkkkkk..",
       "..kkkkkkkkkkkk..",
       "..ww......www...",
       ".w..w....w...w..",
       ".w..w....w...w..",
       "..ww......www...",
       E, E)


# --------------------------------------------------------------------------- batch 7: artifacts, instruments, coins

# Loose coins (not the GeckoLib galleon/sickle/knut, which are models). A struck disc on a true
# circle, shaded flat with a raised rim: metal's ellipsoid shading turned them into beans.
COIN = (
    E, E,
    S("......cc"),
    S("....cccc"),
    S("...ccmmm"),
    S("...cmccc"),
    S("..cmcccc"),
    S("..cmcccc"),
    S("..cmcccc"),
    S("..cmcccc"),
    S("...cmccc"),
    S("...ccmmm"),
    S("....cccc"),
    S("......cc"),
    E, E,
)


def coin(name, colour, rows=COIN, mark=None):
    legend = {"c": ("plate", colour), "m": ("plate", colour, "dark"),
              "x": mark or ("plate", colour, "light"), "o": None}
    sprite(name, legend, *rows)


def _with(rows, changes):
    rows = list(rows)
    for y, row in changes.items():
        rows[y] = row
    return rows


coin("counterfeit_galleon", "#C0A85A")
# The D.A.'s galleons: the date on the rim glows when Harry changes it.
coin("da_galleon", "#E6B43A", mark=("candy", "#FFF4B0", "base"),
     rows=_with(COIN, {7: S("..cmcxcx"), 8: S("..cmcxcx")}))
# The dragot: foreign, copper, struck with a square hole.
coin("dragot", "#C0703A", rows=_with(COIN, {7: S("..cmccmo"), 8: S("..cmccmo")}))

sprite("leprechaun_gold", {"c": ("gold", "#F2C84A"), "m": ("gold", "#F2C84A", "shade"),
                           "s": ("candy", "#FFFBE0", "base")},
       E, E,
       "...........s....",
       "..s.............",
       ".......cccc.....",
       "......cmmmmc....",
       "......cmccmc..s.",
       "...cccccccc.....",
       "..cmmmmc.ccccc..",
       "..cmccmcccmmmmc.",
       ".cccccccc.cmccmc",
       "cmmmmmmmcccccccc",
       "cccccccccmmmmmmc",
       ".ccccccc.cccccc.",
       E, E)

sprite("brass_scales", {"b": ("gold", "#C8A04A"), "d": ("gold", "#C8A04A", "shade")},
       E,
       S(".......b"),
       S("bbbbbbbb"),
       S("b......b"),
       S("b......b"),
       "b.............b.",
       "dbbd......dbbd..",
       ".dd........dd...",
       S(".......b"),
       S(".......b"),
       S(".......b"),
       S(".......b"),
       S(".....bbb"),
       S("...bbbbb"),
       E, E)

CAULDRON_BODY = (
    S(".ppppppp"),
    S("pllllll."),
    S("pppppppp"),
    S("pppppppp"),
    S(".ppppppp"),
    S(".ppppppp"),
    S("..pppppp"),
    S("...ppppp"),
    S("...p...p"),
)

sprite("collapsible_cauldron", {"p": ("silver", "#8A8C90"), "l": ("liquid", "#3A3E44", "dark"),
                                "s": ("silver", "#8A8C90", "dark")},
       E, E, E, E,
       *[r if i != 3 else S("ssssssss") for i, r in enumerate(CAULDRON_BODY)],
       E, E, E)

sprite("self_stirring_cauldron", {"p": ("silver", "#5A5A60"), "l": ("liquid", "#4AB85A", "base"),
                                  "w": ("wood", "#9A6A3A"), "o": ("liquid", "#8AF09A", "light")},
       ".........w......",
       ".........w......",
       "........w..o....",
       "....o...w.......",
       "........w.o.....",
       "..o....w........",
       *CAULDRON_BODY,
       E)

sprite("foe_glass", {"f": ("darkwood", "#3A2A22"), "g": ("glass", "#4A4A5A"), "s": ("smoke", "#8A7A9A"),
                     "k": ("ink", "#1A1620", "base")},
       E,
       S("..ffffff"),
       S(".fgggggg"),
       S(".fggsggg"),
       S(".fgsssgg"),
       S(".fgskkgg"),
       S(".fgsssgg"),
       S(".fgggsgg"),
       S(".fggsssg"),
       S(".fggskkg"),
       S(".fggsssg"),
       S(".fgggggg"),
       S("..ffffff"),
       S("....ff.."),
       E, E)

sprite("goblet_of_fire", {"w": ("darkwood", "#6A4A2A"), "f": ("candy", "#6AA8F2", "base"),
                          "F": ("candy", "#E0F0FF", "base")},
       ".....f..f.f.....",
       "....ff.fF.ff....",
       "....fFffFfFf....",
       "...ffFFFFFFff...",
       S("...wwwww"),
       S("...wwwww"),
       S("....wwww"),
       S(".....www"),
       S("......ww"),
       S("......ww"),
       S("......ww"),
       S(".....www"),
       S("....wwww"),
       S("...wwwww"),
       E, E)

sprite("golden_egg", {"o": "gold", "l": ("gold", "shade")},
       E, E,
       S("......oo"),
       S(".....ooo"),
       S("....oooo"),
       S("...ooooo"),
       S("...lllll"),
       S("..oooooo"),
       S("..oooooo"),
       S("..llllll"),
       S("..oooooo"),
       S("...ooooo"),
       S("....oooo"),
       S("......oo"),
       E, E)

sprite("hufflepuffs_cup", {"o": "gold", "b": ("candy", "#2A2A2A", "base")},
       E, E, E,
       S("...ooooo"),
       S(".o.ooooo"),
       S("o..oobbo"),
       S("o..oooob"),
       S(".o.ooooo"),
       S("....oooo"),
       S(".....ooo"),
       S("......oo"),
       S(".....ooo"),
       S("....oooo"),
       E, E, E)

sprite("marvolo_gaunts_ring", {"o": ("gold", "#D8A83A"), "k": ("stone", "#1E1A22"),
                               "*": ("stone", "#8A8A9A", "spec")},
       E, E, E, E,
       S(".....kkk"),
       S("....k*kk", "k"),
       S("....kkkk"),
       S("...ooooo"),
       S("..oo...."),
       S("..o....."),
       S("..oo...."),
       S("...ooooo"),
       E, E, E, E)

sprite("mirror_of_erised", {"o": ("gold", "#C8983A"), "g": ("glass", "#9AB0C8"),
                            "*": ("glass", "spec"), "s": ("smoke", "#E8C8A8", "light")},
       S(".....ooo"),
       S("...ooooo"),
       S("..oggggg"),
       S("..og*ggg", "g"),
       S("..og*ggg", "g"),
       S("..ogggss"),
       S("..oggsss"),
       S("..oggsss"),
       S("..ogggss"),
       S("..oggggg"),
       S("..oggggg"),
       S("..oooooo"),
       S("..o....."),
       S(".oo....."),
       E, E)

sprite("omnioculars", {"b": ("gold", "#B8903A"), "g": ("glass", "#6A8AA8"), "k": ("gold", "#B8903A", "dark")},
       E, E, E, E,
       S("..bbb..b"),
       S(".bbbbbbb"),
       S(".bgggbkk"),
       S(".bgggbkk"),
       S(".bbbbbbb"),
       S("..bbb..."),
       E, E, E, E, E, E)

sprite("opal_necklace", {"c": ("gold", "light"), "o": ("glass", "#E8D8F4"), "p": ("candy", "#F2A8C8", "base"),
                         "b": ("candy", "#8AD8E8", "base")},
       E,
       S("..cc...."),
       S(".c......"),
       S(".c......"),
       S("..c....."),
       S("..c....."),
       S("...c...."),
       S("....c..."),
       S(".....ccc"),
       S(".....ooo"),
       S("....oopb"),
       S("....obpo"),
       S(".....ooo"),
       S("......oo"),
       E, E)

sprite("pensieve", {"s": ("stone", "#8A8A90"), "m": ("liquid", "#DCE4F4", "light"),
                    "w": ("hair", "#FFFFFF", "base")},
       E, E, E, E,
       S("..ssssss"),
       S(".smmmwmm"),
       S(".smwmmmm"),
       S("..ssssss"),
       S("...sssss"),
       S(".....sss"),
       S(".....sss"),
       S("....ssss"),
       S("..ssssss"),
       E, E, E)

sprite("philosophers_stone", {"r": ("glass", "#C0182A"), "*": ("glass", "#FF8A8A", "spec"),
                              "d": ("glass", "#C0182A", "dark")},
       E, E, E, E,
       S(".....rrr"),
       S("....r*rr", "r"),
       S("...r*rrr", "r"),
       S("...rrrrr"),
       S("...rrrrd"),
       S("....rrdd"),
       S(".....ddd"),
       E, E, E, E, E)

sprite("resurrection_stone", {"k": ("stone", "#2A2830"), "g": ("silver", "#8A8E9A", "light")},
       E, E, E, E,
       S(".....kkk"),
       S("....kkkg"),
       S("...kkkgk"),
       S("...kkgkg"),
       S("...kgkkg"),
       S("....gggg"),
       S(".....kkk"),
       E, E, E, E, E)

sprite("portkey", {"l": ("leather", "#6A4A2A"), "s": ("leather", "#3A2616"), "h": ("paper", "#C8B888")},
       E, E, E, E,
       ".......lll......",
       ".......lll......",
       ".......lhl......",
       ".......lll......",
       ".......llll.....",
       "..llllllllll....",
       ".lllllllllll....",
       ".lllllllllll....",
       ".ssssssssssss...",
       E, E, E)

sprite("probity_probe", {"o": ("gold", "light"), "h": ("leather", "#3A2A22")},
       E, E,
       S(".......o"),
       S(".......o"),
       S("......o."),
       S(".......o"),
       S("......o."),
       S(".......o"),
       S("......oo"),
       S("......hh"),
       S("......hh"),
       S("......hh"),
       S(".....ooo"),
       E, E, E)

sprite("secrecy_sensor", {"o": ("gold", "base"), "h": ("darkwood", "base")},
       E,
       "........o.......",
       ".......o.o......",
       "......o...o.....",
       ".......o.o......",
       "........o.......",
       ".......o.o......",
       "......o...o.....",
       ".......o.o......",
       "........o.......",
       ".......hhh......",
       ".......hhh......",
       ".......hhh......",
       "......ooooo.....",
       E, E)

TELESCOPE = (
    E, E,
    "............bb..",
    "...........bbbb.",
    "..........bbbbb.",
    ".........bbbbb..",
    "........kbbbb...",
    ".......bbbkk....",
    "......bbbbb.....",
    ".....bbbk.......",
    "....bbbb........",
    "...bbbb.........",
    "..gbbb..........",
    "..gg............",
    E, E,
)
sprite("telescope", {"b": ("gold", "#C8A04A"), "k": ("gold", "#C8A04A", "dark"),
                     "g": ("glass", "#8AA8C8")}, *TELESCOPE)
# The joke version: a boxing glove waiting in the eyepiece end.
_PUNCH = list(TELESCOPE)
_PUNCH[0:4] = [E, "............rrr.", "...........rrrrr", "...........rrrr."]
sprite("punching_telescope", {"b": ("gold", "#C8A04A"), "k": ("gold", "#C8A04A", "dark"),
                              "g": ("glass", "#8AA8C8"), "r": ("leather", "#C0302A")}, *_PUNCH)

sprite("ravenclaws_diadem", {"s": ("silver", "#D8DCE4"), "b": ("glass", "#2A5AB8"), "*": ("glass", "spec")},
       E, E, E, E,
       S(".......s"),
       S("......sb"),
       S(".s...sb*", "b"),
       S(".ss.ssbb"),
       S("..sssssb"),
       S("..ssssss"),
       S("...s...."),
       E, E, E, E, E)

sprite("revealer", {"r": ("candy", "#D8404A"), "R": ("candy", "#D8404A", "shade"),
                    "w": ("paper", "#F0E8E0")},
       E, E, E, E, E,
       S("...rrrrr"),
       S("..rrrrrr"),
       S("..rrwwww"),
       S("..rrrrrr"),
       S("..RRRRRR"),
       E, E, E, E, E, E)

sprite("slytherins_locket", {"o": ("gold", "#D8A83A"), "g": ("glass", "#1E8A4A"), "c": ("gold", "light")},
       S("...cc..."),
       S("..c..c.."),
       S("..c...c."),
       S("...c..c."),
       S(".....c.."),
       S(".....ooo"),
       S("...ooooo"),
       S("..oogggg"),
       S("..oggooo"),
       S("..oooggg"),
       S("..ooooog"),
       S("..oggggg"),
       S("...ooooo"),
       S(".....ooo"),
       E, E)

sprite("sword_of_gryffindor", {"s": ("silver", "#D8DCE4"), "h": ("gold", "base"), "r": ("glass", "#C0182A"),
                               "g": ("leather", "#6A2A1E")},
       "..............s.",
       ".............ss.",
       "............ss..",
       "...........ss...",
       "..........ss....",
       ".........ss.....",
       "........ss......",
       ".......ss.......",
       "...h..ss........",
       "....hrh.........",
       "....hhh.........",
       "...gh..h........",
       "..gg............",
       ".rg.............",
       E, E)

sprite("triwizard_cup", {"c": ("glass", "#A8D8F0"), "*": ("glass", "spec"), "b": ("candy", "#E0F4FF", "base")},
       E, E,
       S("..cccccc"),
       S(".c.cc*cc", "c"),
       S("c..ccbcc"),
       S("c..ccccc"),
       S(".c.ccccc"),
       S("...ccccc"),
       S(".....ccc"),
       S("......cc"),
       S("......cc"),
       S(".....ccc"),
       S("...ccccc"),
       E, E, E)

sprite("two_way_mirror", {"f": ("silver", "#8A8E96"), "g": ("glass", "#A8C0D8"), "*": ("glass", "spec")},
       E, E, E,
       S("..ffffff"),
       S("..fggggg"),
       S("..fg*ggg", "g"),
       S("..fg*ggg", "g"),
       S("..fggggg"),
       S("..fggggg"),
       S("..fggggg"),
       S("..ffffff"),
       E, E, E, E, E)

sprite("vanishing_cabinet", {"k": ("darkwood", "#2A1E1A"), "K": ("darkwood", "#2A1E1A", "shade"),
                             "o": ("gold", "base")},
       E,
       S("...kkkkk"),
       S("..kkkkkk"),
       S("..kKKKKk"),
       S("..kKkkKk"),
       S("..kKkkKk"),
       S("..kKkkKo"),
       S("..kKkkKo"),
       S("..kKkkKk"),
       S("..kKkkKk"),
       S("..kKKKKk"),
       S("..kkkkkk"),
       S("..k....k"),
       E, E, E)

sprite("wizarding_wireless", {"w": ("wood", "#8A5A2A"), "c": ("cloth", "#C8A878"), "o": ("gold", "base")},
       E, E, E,
       S("....wwww"),
       S("...wwwww"),
       S("..wwwwww"),
       S("..wccccc"),
       S("..wcwcwc"),
       S("..wccccc"),
       S("..wwwwww"),
       S("..wwowwo"),
       S("..wwwwww"),
       E, E, E, E)

sprite("dark_mark_brand", {"i": ("silver", "#6A6E76"), "h": ("darkwood", "base"), "k": ("ink", "#1E1A1E"),
                           "g": ("liquid", "#3AB84A", "light")},
       "........kkk.....",
       ".......kgkgk....",
       ".......kkkkk....",
       "........kgk.....",
       ".........k......",
       "........i.......",
       ".......i........",
       "......i.........",
       ".....i..........",
       "....hh..........",
       "...hh...........",
       "..hh............",
       ".hh.............",
       E, E, E)


# --------------------------------------------------------------------------- batch 8: wands, trunks, cases, cloaks

WAND = (
    E,
    "............tt..",
    "...........tww..",
    "..........www...",
    ".........www....",
    "........www.....",
    ".......www......",
    "......www.......",
    ".....hhh........",
    "....hhh.........",
    "...hhh..........",
    "..hhh...........",
    ".hhh............",
    ".hh.............",
    E, E,
)
sprite("wand_blank", {"w": ("wood", "#D8B888"), "h": ("wood", "#D8B888"), "t": ("wood", "#D8B888", "shade")}, *WAND)
sprite("debug_wand", {"w": ("paper", "#F0F0F0"), "h": ("cloth", "#C0302A"), "t": ("candy", "#FF4A4A", "base")}, *WAND)
sprite("morph_wand", {"w": ("darkwood", "#4A2A5A"), "h": ("darkwood", "#2A1A30"),
                      "t": ("candy", "#C88AF2", "base")}, *WAND)

sprite("duelling_dummy", {"s": ("wood", "#D8B870"), "S": ("wood", "#D8B870", "shade"),
                          "p": ("wood", "#7A5A3A"), "r": ("cloth", "#B8302A"), "w": ("paper", "#F0E8D8")},
       E,
       S(".....sss"),
       S("....ssss"),
       S(".....sss"),
       S("......pp"),
       S("ssssssss"),
       S(".sssswww"),
       S("....swrr"),
       S("....swrw"),
       S("....sSSS"),
       S("....ssss"),
       S("......pp"),
       S("......pp"),
       S("....pppp"),
       E, E)

# The four trunks, Newt's case and the pocket case had icons here until 2026-09-25 (220a8e0e):
# the trunks and the case draw their block model in the inventory, and `pocket_case` is not an
# item, so those textures were deleted as unreachable. Drawing them again would recreate orphans.

sprite("hermiones_beaded_bag", {"p": ("cloth", "#7A3A8A"), "b": ("candy", "#E8C8F4", "base"),
                                "c": ("cloth", "#C8A0D8")},
       E, E,
       S(".....c.."),
       S("......cc"),
       S(".....ppp"),
       S("....pppp"),
       S("...pbpbp"),
       S("..pppppp"),
       S("..pbpbpb"),
       S("..pppppp"),
       S("..pbpbpb"),
       S("..pppppp"),
       S("...ppppp"),
       S(".....ppp"),
       E, E)

CLOAK = (   # hood points, shoulders, a flare to the hem, and fold lines down it; without the
            # folds a cloak at 16px reads as a bell, a ghost or a heap
    E,
    ".....cc..cc.....",
    "....cccKKccc....",
    "....cccccccc....",
    "....cfcccfcc....",
    "....cfcccfcc....",
    "....cfc*cfcc....",
    "...ccfcccfccc...",
    "...cfccfccfcc...",
    "...cfccfccfcc...",
    "..ccfccfccfccc..",
    "..cfccfccfccfc..",
    "..cfccfccfccfc..",
    ".ccfccfccfccfcc.",
    "..ffffffffffff..",
    E,
)
sprite("invisibility_cloak", {"c": ("hair", "#C8D4E4"), "f": ("hair", "#C8D4E4", "shade"),
                              "*": ("hair", "#FFFFFF", "base"), "K": ("silver", "light")}, *CLOAK)
# The Hallow: the same cloak, older and darker, the sign stitched at the breast.
_HALLOW = list(CLOAK)
_HALLOW[5:9] = ["....cfcgcfcc....", "....cfgGgfcc....", "...ccfgGgfccc...", "...cfgggggfcc..."]
sprite("deathly_hallow_cloak", {"c": ("cloth", "#3E4258"), "f": ("cloth", "#3E4258", "shade"),
                                "g": ("silver", "light"), "G": ("silver", "dark"),
                                "*": ("cloth", "#3E4258", "light"), "K": ("silver", "light")}, *_HALLOW)


# --------------------------------------------------------------------------- former bespoke GeckoLib items
#
# The coins, the Deluminator and the map are AnimatedItems now: a model in hand, and these in slots.

_GALLEON = COIN
_SICKLE = _with(COIN, {2: E, 13: E, 3: S(".....ccc"), 12: S(".....ccc")})
_KNUT = [E, E, E, E] + [S("......cc"), S(".....ccc"), S("....cmmm"), S("....cmcc"), S("....cmcc"),
                        S("....cmmm"), S(".....ccc"), S("......cc")] + [E, E, E, E]
coin("galleon", "#E6B43A", rows=_with(_GALLEON, {6: S("..cmcxxx"), 9: S("..cmcxcc"), 8: S("..cmcccx")}),
     mark=("plate", "#E6B43A", "dark"))
coin("sickle", "#C3C9D4", rows=_with(_SICKLE, {6: S("..cmcxcc"), 7: S("..cmxccc"), 8: S("..cmcxcc")}),
     mark=("plate", "#C3C9D4", "dark"))
coin("knut", "#B8733A", rows=_KNUT)

sprite("deluminator", {"s": "silver", "b": ("silver", "#8A8E96", "shade"), "k": ("silver", "dark")},
       E,
       S(".......k"),
       S("......ss"),
       S("......ss"),
       S("......bb"),
       S("......ss"),
       S("......ss"),
       S("......ss"),
       S("......ss"),
       S("......ss"),
       S("......bb"),
       S("......ss"),
       S("......ss"),
       E, E, E)

sprite("marauders_map", {"p": ("paper", "#DAC291"), "P": ("paper", "#DAC291", "shade"),
                         "k": ("ink", "#5A3A1E", "base"), "f": ("ink", "#2A1A0E", "base")},
       E, E,
       "...pppppppppp...",
       "...pkkkkpkkkp...",
       "...pkpppPpppp...",
       "...pkpfpPpkkp...",
       "...pkkkkPpkpp...",
       "...ppppfPpkpp...",
       "...pkkkpPpppp...",
       "...pkpppPfpkp...",
       "...pkkkkPpkkp...",
       "...pppppPpppp...",
       "...PPPPPPPPPP...",
       E, E, E)


# --------------------------------------------------------------------------- mockup


def mockup(path, names=None):
    """Old icon left, new right, for every sprite (or just `names`)."""
    names = [n for n in (names or SPRITES) if n in SPRITES]
    cols = 9
    cell = 16 * 5
    pad = 12
    rows = (len(names) + cols - 1) // cols
    W = cols * (cell * 2 + pad * 3)
    H = rows * (cell + pad * 2) + 40
    sheet = Image.new("RGBA", (W, H), (139, 139, 139, 255))
    for i, name in enumerate(names):
        col, row = i % cols, i // cols
        x = col * (cell * 2 + pad * 3) + pad
        y = row * (cell + pad * 2) + pad + 30
        old_path = os.path.join(ITEM, name + ".png")
        if os.path.exists(old_path):
            old = Image.open(old_path).convert("RGBA").resize((cell, cell), Image.NEAREST)
            sheet.alpha_composite(old, (x, y))
        new = render(SPRITES[name]).resize((cell, cell), Image.NEAREST)
        sheet.alpha_composite(new, (x + cell + pad, y))
    sheet.save(path)
    print("wrote", path)


def write(force, only):
    """Every sprite to textures/item/<name>.png. Refuses files another generator owns unless
    --force, which is how the canon/legacy icons are claimed once."""
    written, skipped = [], []
    for name, spec in SPRITES.items():
        if only and name not in only:
            continue
        path = os.path.join(ITEM, name + ".png")
        if not force and not is_regenerable(path, MARKER):
            skipped.append(name)
            continue
        save(render(spec), path, MARKER)
        written.append(name)
    print(f"wrote {len(written)} item icons")
    if skipped:
        print(f"skipped {len(skipped)} owned elsewhere (--force claims them): {', '.join(skipped)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mockup", metavar="PNG")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default="", help="comma-separated sprite names")
    args = ap.parse_args()
    only = {x.strip() for x in args.only.split(",") if x.strip()}
    if args.mockup:
        mockup(args.mockup, sorted(only) if only else None)
    if args.write:
        write(args.force, only)
    if not (args.mockup or args.write):
        ap.error("pass --mockup PNG and/or --write")


if __name__ == "__main__":
    main()
