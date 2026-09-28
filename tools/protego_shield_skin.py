"""Generate the Protego shield-barrier entity texture.

The Protego shield is a GeckoLib entity (``ProtegoShieldEntity``) rendered by
``ProtegoShieldRenderer`` with ``entityTranslucentEmissive`` — so this sheet is a translucent,
full-bright FX surface, NOT opaque pixel art. It is painted onto the two cubes in
``geckolib/models/entity/protego_shield.geo.json``:

    disc_core        16x2x16  @ uv (0, 0)   -> up (16,0)-(32,16), down (32,0)-(48,16)
    dome_hemisphere  16x8x16  @ uv (0,18)   -> cap up (16,18)-(32,34), down (32,18)-(48,34),
                                               sides row v=34 h=8

Because the render type reads vertex alpha, the shimmer is baked as alpha: a faint blue wash,
a brighter cyan hex lattice, and a bright near-white rune ring on the two upward cap faces (the
faces the player sees head-on — the disc is stood upright by the renderer). Cull is on, so the
underside/back faces get the same lattice but no ring.

Unlike the sprite generators this deliberately skips ``posterise``/``pixelate``: those force
alpha to 255 and snap to a 6-colour palette, which would destroy the translucency that makes the
barrier read as energy rather than a solid box. It still carries the shared ``Generator`` marker
so ``is_regenerable`` treats it as ours.

    python tools/protego_shield_skin.py
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

from artgen_common import marker, save

OUT = Path(__file__).resolve().parents[1] / (
    "src/main/resources/assets/wizards_and_beasts/textures/entity/protego_shield.png"
)
TOOL = "protego_shield_skin.py"

SIZE = 64
SS = 8                       # supersample; smooth is desirable for a glow FX
W = SIZE * SS

# Royal-blue barrier that matches the deflect tint (0xFF4169E1) and the spell colour (0xFF4488FF).
WASH = (74, 132, 246)        # faint fill
HEX = (150, 216, 255)        # lattice lines
RING = (206, 240, 255)       # rune ring / glyph — near-white cyan

# The box-UV islands actually sampled by the two cubes, in 64px sheet coords.
ISLANDS = [
    (16, 0, 32, 16),    # disc up  (the face the player sees)
    (32, 0, 48, 16),    # disc down
    (16, 18, 32, 34),   # dome cap up
    (32, 18, 48, 34),   # dome cap down
    (0, 34, 64, 42),    # dome sides row (west|north|east|south, each 16 wide)
]
# The two upward "cap" faces get the rune ring.
CAP_FACES = [(16, 0, 32, 16), (16, 18, 32, 34)]


def _rect_ss(box):
    x0, y0, x1, y1 = box
    return x0 * SS, y0 * SS, x1 * SS, y1 * SS


def _wash(draw):
    """Faint translucent fill over every sampled island (unused sheet stays clear)."""
    for box in ISLANDS:
        draw.rectangle(_rect_ss(box), fill=WASH + (60,))


def _hex_lattice(draw):
    """Flat-top honeycomb across the whole sheet, so adjacent UV faces tile seamlessly."""
    r = 3.4 * SS                     # hex radius in supersample px (~3.4 sheet px)
    dx = 1.5 * r
    dy = math.sqrt(3.0) * r
    verts = [(math.cos(math.radians(60 * k)) * r, math.sin(math.radians(60 * k)) * r)
             for k in range(6)]
    col = 0
    x = -r
    while x < W + r:
        y_off = 0.0 if col % 2 == 0 else dy / 2.0
        y = -r + y_off
        while y < W + r:
            pts = [(x + vx, y + vy) for vx, vy in verts]
            draw.line(pts + [pts[0]], fill=HEX + (140,), width=max(1, SS // 3))
            y += dy
        x += dx
        col += 1


def _rune_ring(draw, box):
    """Concentric rings + tick marks + a small cross glyph, centred on a cap face."""
    x0, y0, x1, y1 = _rect_ss(box)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    R = (x1 - x0) / 2.0
    lw = max(1, SS // 2)
    for rad, a in ((R * 0.92, 210), (R * 0.62, 170), (R * 0.24, 230)):
        draw.ellipse((cx - rad, cy - rad, cx + rad, cy + rad),
                     outline=RING + (a,), width=lw)
    # 12 tick marks between the outer two rings.
    for k in range(12):
        ang = math.radians(k * 30)
        c, s = math.cos(ang), math.sin(ang)
        draw.line((cx + c * R * 0.62, cy + s * R * 0.62,
                   cx + c * R * 0.92, cy + s * R * 0.92),
                  fill=RING + (150,), width=lw)
    # Centre cross glyph.
    g = R * 0.16
    draw.line((cx - g, cy, cx + g, cy), fill=RING + (235,), width=lw)
    draw.line((cx, cy - g, cx, cy + g), fill=RING + (235,), width=lw)


def main():
    hi = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    draw = ImageDraw.Draw(hi)
    _wash(draw)
    _hex_lattice(draw)
    for box in CAP_FACES:
        _rune_ring(draw, box)
    # Clip the lattice/rings back to the sampled islands so stray energy doesn't bleed onto
    # unused sheet regions (which some GeckoLib UV debuggers still render).
    mask = Image.new("L", (W, W), 0)
    mdraw = ImageDraw.Draw(mask)
    for box in ISLANDS:
        mdraw.rectangle(_rect_ss(box), fill=255)
    hi.putalpha(Image.composite(hi.getchannel("A"), Image.new("L", (W, W), 0), mask))

    out = hi.resize((SIZE, SIZE), Image.LANCZOS)
    save(out, str(OUT), marker(TOOL))
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
