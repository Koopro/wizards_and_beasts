#!/usr/bin/env python3
"""Bedrock box-UV packing and painting, shared by the hand-built creature rigs.

Bedrock's box UV gives a cube one island of (2d + 2w) x (d + h) laid out as

      . top    bottom .
    east north  west  south

and the geometry stores only that island's top-left corner. Placing a couple of dozen of
those by hand is the bookkeeping that makes a rig and its skin drift apart, so the packer
below assigns them and `faces()` hands back the same rectangles for the painter. A
generator that packs through here cannot paint a face into the wrong place.

Extracted from `tools/ghoul_model.py`, which was the first rig built this way.
"""

TOP, BOTTOM, EAST, NORTH, WEST, SOUTH = "top", "bottom", "east", "north", "west", "south"


def _island(size):
    """Island footprint (width, height) for a cube of this size."""
    w, h, d = (int(round(v + 0.4999)) for v in size)
    return 2 * d + 2 * w, d + h


class Packer:
    """Shelf packer for box-UV islands. Call `place()` per cube, then `height_used()`."""

    def __init__(self, width):
        self.w = width
        self.shelf_y = 0
        self.shelf_h = 0
        self.x = 0
        self.placed = {}

    def place(self, name, size):
        iw, ih = _island(size)
        if self.x + iw > self.w:
            self.shelf_y += self.shelf_h
            self.shelf_h = 0
            self.x = 0
        uv = (self.x, self.shelf_y)
        self.x += iw
        self.shelf_h = max(self.shelf_h, ih)
        self.placed[name] = (uv, size)
        return uv

    def place_sorted(self, items):
        """Place `(name, size)` pairs tallest-island-first, then report the height used.

        Shelf packing in authoring order leaves a shelf as tall as its tallest island and
        wastes everything under the short ones: the thestral's 110 cubes filled only 70%
        of a 128px sheet that way and spilled onto a 256. Sorting by island height first
        is the whole fix — same packer, same `faces()` rectangles, roughly a third less
        sheet. Callers that place incrementally keep using `place()`, so rigs already
        shipped against that order keep their UVs.
        """
        for name, size in sorted(items, key=lambda it: -_island(it[1])[1]):
            self.place(name, size)
        return self.height_used()

    def height_used(self):
        return self.shelf_y + self.shelf_h


def faces(uv, size):
    """The six face rects of one island, in the layout `beast_preview.py` samples."""
    u, v = uv
    w, h, d = (int(round(x + 0.4999)) for x in size)
    return {
        TOP:    (u + d, v, w, d),
        BOTTOM: (u + d + w, v, w, d),
        EAST:   (u, v + d, d, h),
        NORTH:  (u + d, v + d, w, h),
        WEST:   (u + d + w, v + d, d, h),
        SOUTH:  (u + d + w + d, v + d, w, h),
    }


def mottle(draw, rect, base, palette, density, seed):
    """Deterministic speckle over a face rect — texture that is the same every run."""
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
            if (n >> 7) % 100 < density:
                draw.point((x, y), fill=palette[(n >> 11) % len(palette)])


def gradient(draw, rect, top_col, bottom_col, mix_fn):
    """Vertical ramp down a face rect, for plumage that lightens toward the tip."""
    x0, y0, w, h = rect
    span = max(1, h - 1)
    for i in range(h):
        draw.line([(x0, y0 + i), (x0 + w - 1, y0 + i)],
                  fill=mix_fn(top_col, bottom_col, i / span))
