#!/usr/bin/env python3
"""How a 3D item is held: display transforms computed from the model, not typed per item.

A display transform is three numbers per context -- rotation, translation, scale -- and the
game applies them as

    pose = translate(t) * rotate(r) * scale(s) * translate(-8, -8, -8)      (pixels)

so a model point p lands at  t + R * s * (p - 8).  Hand-typing those numbers per item is how the
props ended up the size of a thumbnail: the first cut lifted vanilla's `block/block` display for
every prop, and block/block assumes a full 16-unit cube. A 4-unit vial held at a block's 0.4 is a
quarter of a held block wide.

So a transform here is derived, not written. Each *hold class* fixes, per context:

  - the rotation (how the object is presented: a bottle upright, a book cover-on, a rod as a tool),
  - the *visual size* its longest side should have after scaling (in model units), and
  - the *anchor*: where its visual centre should land (vanilla's own anchor for that context).

Scale is visual size / the model's longest side, and translation is solved so the centre of the
model's bounding box lands on the anchor. A 4-unit vial and a 14-unit cauldron then both read at
arm's length, and a new item is correct without anyone typing numbers.

Reference points, from vanilla's `item/generated` and `item/handheld` (a 16 px sprite): first
person scale 0.68 at (1.13, 3.2, 1.13), i.e. about 11 units of visual size; third person 0.55 at
(0, 3, 1) for a generated sprite, 0.85 at (0, 4, 0.5) for a tool.

Only right-hand contexts are written: vanilla mirrors them for the left hand (ItemTransform.apply
negates x translation and the y/z rotations when `leftHand`), which is exactly the symmetry wanted.

GeckoLib frame. A geo model is not in block-model space: after the display transform,
`GeoItemRenderer.adjustRenderPose` translates by (0.5, 0.51, 0.5) *after* vanilla's
(-0.5, -0.5, -0.5), so geo (0, 0, 0) sits at block-model (8, 8.16, 8) -- the centre of the item
cube, not its floor -- and GeckoLib bakes x mirrored (BakedModelFactory: -(origin.x + size.x)).
`geo_bounds` converts, which is what makes a geo model's centre land on the same anchor as a
cuboid's.
"""

import json
import math

GEO_ORIGIN = (8.0, 8.16, 8.0)

# Anchors (pixels, right hand). First-person anchor is vanilla's handheld/generated one; third
# person is vanilla generated's, raised a unit so an upright object sits in the fist, not through it.
FP_ANCHOR = (1.13, 3.2, 1.13)
TP_ANCHOR = (0.0, 3.0, 1.0)

# rotation (deg, XYZ), visual size (units), anchor (px) per context.
# gui/ground/fixed only matter for models shown there (the slot branch of a dispatch item is the
# flat icon); they are still written so a model is sane wherever it ends up.
HOLD_CLASSES = {
    # Upright containers: bottles, vials, flasks, mugs, jars. Stand in the fist, a quarter turn
    # so the belly and the label side show.
    "vessel": {
        "firstperson_righthand": ((0, -25, 0), 7.0, (1.13, 2.6, 1.13)),
        "thirdperson_righthand": ((0, 0, 0), 7.0, (0.0, 3.5, 1.0)),
        "gui": ((20, -30, 0), 14.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 7.0, (0, 3, 0)),
        "fixed": ((0, 180, 0), 12.0, (0, 0, 0)),
    },
    # A closed book held cover-on, spine in the palm, tipped back a little so the head edge shows.
    "book": {
        "firstperson_righthand": ((-10, -30, -8), 6.5, (1.13, 2.6, 1.13)),
        "thirdperson_righthand": ((-15, 90, 0), 7.5, (0.0, 3.5, 1.5)),
        "gui": ((20, -35, 0), 14.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 7.0, (0, 3, 0)),
        "fixed": ((0, 180, 0), 13.0, (0, 0, 0)),
    },
    # Long things held by one end (quills, probes, telescopes, bats, swords): the vanilla tool
    # pose, with the model's Y-aligned length turned onto the sprite diagonal a tool is drawn on
    # (handheld's z 25 / 55, minus the 45 degrees between "up" and "diagonal").
    "rod": {
        "firstperson_righthand": ((0, -90, -20), 10.0, FP_ANCHOR),
        "thirdperson_righthand": ((0, -90, 10), 12.0, (0.0, 4.0, 0.5)),
        "gui": ((0, 0, -45), 15.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 8.0, (0, 3, 0)),
        "fixed": ((0, 180, -45), 14.0, (0, 0, 0)),
    },
    # Brooms: a rod too long to show whole. Held as a staff, grip low, the head trailing out of
    # frame the way vanilla's trident does; third person carries it like a tool.
    "staff": {
        "firstperson_righthand": ((0, -90, -20), 14.0, (1.13, 4.0, 1.13)),
        "thirdperson_righthand": ((0, -90, 10), 16.0, (0.0, 5.0, 0.5)),
        "gui": ((0, 0, -45), 15.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 9.0, (0, 3, 0)),
        "fixed": ((0, 180, -45), 14.0, (0, 0, 0)),
    },
    # The wand: the one item that stays 3D in the slot, because its identity is its shape and
    # wood. Diagonal and filling the slot like a vanilla tool sprite; a tool in the hand, tip
    # forward; lying across an item frame.
    "wand": {
        "firstperson_righthand": ((0, -90, -20), 12.0, FP_ANCHOR),
        "thirdperson_righthand": ((0, -90, 10), 11.0, (0.0, 4.0, 0.5)),
        "gui": ((0, 0, -45), 19.0, (0, 0, 0)),
        "ground": ((0, 0, -90), 10.0, (0, 2, 0)),
        "fixed": ((0, 0, -45), 17.0, (0, 0, 0)),
        "on_shelf": ((0, 0, -45), 14.0, (0, 0, 0)),
    },
    # Things that fit in the palm: sweets, gems, stones, coins, eggs, trinkets.
    "small": {
        "firstperson_righthand": ((10, -30, 0), 5.0, (1.13, 2.6, 1.13)),
        "thirdperson_righthand": ((10, 0, 0), 5.0, (0.0, 3.0, 1.0)),
        "gui": ((20, -30, 0), 13.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 6.0, (0, 3, 0)),
        "fixed": ((0, 180, 0), 11.0, (0, 0, 0)),
    },
    # Objects that are carried rather than wielded: instruments, boxes, cauldrons, gloves, hats,
    # folded cloth. Presented three-quarter, a little bigger than a held block.
    "object": {
        "firstperson_righthand": ((0, -35, 0), 5.5, (1.13, 2.4, 1.13)),
        "thirdperson_righthand": ((10, -30, 0), 6.0, (0.0, 3.0, 1.0)),
        "gui": ((25, -135, 0), 14.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 7.0, (0, 3, 0)),
        "fixed": ((0, 180, 0), 12.0, (0, 0, 0)),
    },
    # Relics shown off rather than carried: a touch larger and turned to face the viewer.
    "artifact": {
        "firstperson_righthand": ((0, -20, 0), 6.0, (1.13, 2.8, 1.13)),
        "thirdperson_righthand": ((0, 0, 0), 6.5, (0.0, 3.5, 1.0)),
        "gui": ((20, -30, 0), 14.0, (0, 0, 0)),
        "ground": ((0, 0, 0), 7.0, (0, 3, 0)),
        "fixed": ((0, 180, 0), 12.0, (0, 0, 0)),
    },
}


def _rot_matrix(rx, ry, rz):
    """JOML rotationXYZ(rx, ry, rz) as used by ItemTransform: M = Rx * Ry * Rz."""
    ax, ay, az = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rxm = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    rym = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rzm = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return _mul(_mul(rxm, rym), rzm)


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def _round(v, step=0.01):
    return round(round(v / step) * step, 4)


def transform(bounds, rotation, size, anchor):
    """One display entry: scale the longest side to `size`, land the box centre on `anchor`."""
    lo, hi = bounds
    extent = max(h - l for l, h in zip(lo, hi)) or 16.0
    s = size / extent
    centre = [(l + h) / 2.0 - 8.0 for l, h in zip(lo, hi)]
    rotated = _apply(_rot_matrix(*rotation), [c * s for c in centre])
    t = [a - r for a, r in zip(anchor, rotated)]
    # ItemTransform clamps translation to +-80 px and scale to 4; stay well inside.
    t = [max(-79.0, min(79.0, v)) for v in t]
    s = min(s, 3.9)
    return {
        "rotation": [_round(v, 0.5) for v in rotation],
        "translation": [_round(v) for v in t],
        "scale": [_round(s, 0.001)] * 3,
    }


def display_for(hold, bounds, overrides=None, relative_to=None):
    """The full display block for a model with these block-space bounds.

    `relative_to` keeps deliberate size differences inside one class: sizes are then read as
    "for a model whose longest side is `relative_to`", and a smaller model stays smaller. The
    coins use it -- a Knut is meant to be the small one.
    """
    spec = dict(HOLD_CLASSES[hold])
    if overrides:
        spec.update(overrides)
    extent = max(h - l for l, h in zip(*bounds))
    factor = extent / relative_to if relative_to else 1.0
    return {ctx: transform(bounds, rot, size * factor, anchor) for ctx, (rot, size, anchor) in spec.items()}


def cuboid_bounds(elements):
    """Bounds of block-model elements, element rotations included (one axis, about `origin`)."""
    xs, ys, zs = [], [], []
    for e in elements:
        f, t = e["from"], e["to"]
        corners = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
        rot = e.get("rotation")
        if rot and rot.get("angle"):
            axis, angle, origin = rot["axis"], math.radians(rot["angle"]), rot["origin"]
            c, s = math.cos(angle), math.sin(angle)
            i, j = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
            for p in corners:
                a, b = p[i] - origin[i], p[j] - origin[j]
                p[i], p[j] = origin[i] + a * c - b * s, origin[j] + a * s + b * c
        for p in corners:
            xs.append(p[0]); ys.append(p[1]); zs.append(p[2])
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def geo_bounds(geo):
    """Bounds of a GeckoLib item model in block-model space (rest pose, cube inflate included).

    Bone rest rotations are ignored: in the item rigs only the Philosopher's Stone and the diadem
    carry any, and theirs are a few degrees on small parts -- far below what moves a bounding box.
    """
    geometry = geo["minecraft:geometry"][0]
    xs, ys, zs = [], [], []
    for bone in geometry.get("bones", []):
        for cube in bone.get("cubes", []):
            o, sz, inf = cube["origin"], cube["size"], cube.get("inflate", 0.0)
            for x in (o[0] - inf, o[0] + sz[0] + inf):
                xs.append(GEO_ORIGIN[0] - x)
            for y in (o[1] - inf, o[1] + sz[1] + inf):
                ys.append(GEO_ORIGIN[1] + y)
            for z in (o[2] - inf, o[2] + sz[2] + inf):
                zs.append(GEO_ORIGIN[2] + z)
    if not xs:
        return (0, 0, 0), (16, 16, 16)
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def load_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)
