#!/usr/bin/env python3
"""Offline previewer for GeckoLib entity rigs — renders a .geo.json + its texture to a PNG.

Verifying `tools/beast_skins.py` by eyeballing texture atlases only proves the atlas
looks tidy; it does not prove the UVs land on the right part of the model. This is a
small textured z-buffer rasteriser that applies each bone's pivot/rotation hierarchy,
samples the actual entity texture per pixel, and renders the creature from a 3/4 view
— the same check you would otherwise need a running client for.

Run from the repo root:
    python tools/beast_preview.py basilisk unicorn hungarian_horntail
    python tools/beast_preview.py --all --out preview/
"""

import argparse
import glob
import json
import math
import os

import numpy as np
from PIL import Image

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "entity")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")

SIZE = 256
BG = (26, 26, 32)

# Silhouette mode. Every face samples flat black instead of the entity texture, so a contact
# sheet shows shape and nothing else.
#
# This exists because a variant that differs only in texture detail is not a variant. Judging
# a rig against its own painted sheet flatters it: two bundles of quite different outline read
# as "both brown and twiggy" until the colour is taken away. Set by render_silhouette().
SILHOUETTE = False

# 3/4 view: yaw right, pitch down. Matches roughly how a mob reads in-game.
#
# The yaw is measured from *behind* the model. A Bedrock/Java entity faces north, i.e. -Z,
# so a small yaw off zero leaves the camera looking at the creature's back: -32 degrees put
# every bestiary portrait tail-on, which is how 107 of them shipped. Facing the model means
# turning it through 180, and keeping the 32 gives the same three-quarter offset from the
# front instead of from the rear.
YAW = math.radians(180 - 32)
PITCH = math.radians(20)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_matrix(rot):
    """Bedrock XYZ euler -> rotation matrix, as GeckoLib actually renders it.

    GeckoLib (`BakedModelFactory.constructBone`, 5.4.5) mirrors the geo in X — pivot `-x`, cube
    origin `-(x + w)` — and then applies `Rz(z) · Ry(-y) · Rx(-x)` in that mirrored space. Drawn
    here in the geo's own (unmirrored) space, that is the conjugate by the mirror:
    `Rz(-z) · Ry(y) · Rx(-x)`, and `MIRROR` flips the finished picture so it is the game's and not
    its reflection.

    Until 2026-09-25 this was `Rz(z) · Ry(-y) · Rx(-x)` in unmirrored space: X right, **Y and Z
    backwards**. Every rig tuned against the preview with a large rest yaw or roll rendered
    differently in the game — the Acromantula's legs arched down here and rose up in a bundle in a
    running client. X was right, which is why most rigs (whose shape is carried by pitch) never
    showed it.
    """
    rx, ry, rz = (math.radians(float(r)) for r in rot)
    return rot_z(-rz) @ rot_y(ry) @ rot_x(-rx)


# Bones a creature's renderer hides outside a particular state (`PhoenixRenderer` zero-scales the ash pile
# unless the bird is burning). Left out of previews and portraits; `pose_preview` sets SHOW_HIDDEN to draw them.
RENDERER_HIDDEN = {"phoenix": {"ash"}}
SHOW_HIDDEN = False

# GeckoLib renders the geo mirrored in X; so does the preview, so its left is the game's left.
MIRROR = np.diag([-1.0, 1.0, 1.0])


def bone_matrix(bone):
    """Local transform for one bone: rotate about its own pivot (Bedrock order XYZ)."""
    pivot = np.array([float(x) for x in bone.get("pivot", [0, 0, 0])])
    rot = bone.get("rotation")
    m = euler_matrix(rot) if rot else np.eye(3)
    # `_scale` is never in a shipped geo: `pose_preview.py` sets it when baking a clip frame.
    # GeckoLib scales in the bone's rotated frame (pivot, rotate, scale, unpivot), children too.
    if "_scale" in bone:
        m = m @ np.diag([float(v) for v in bone["_scale"]])
    return m, pivot, np.zeros(3)


def cube_matrix(cube):
    """A cube's own rotation about its own pivot, applied inside the bone's frame.

    Bedrock lets a *cube* carry `rotation`/`pivot` independently of its bone, and rigs use
    it for detail that never animates on its own — splayed wing fingers, a fan of tail
    hair, angled ribs. Ignoring it (as this previewer did until now) renders those parts
    axis-aligned, so a fan looks like a stack of parallel slabs and the preview disagrees
    with the game for exactly the rigs most worth checking.
    """
    rot = cube.get("rotation")
    if not rot:
        return np.eye(3), np.zeros(3)
    m = euler_matrix(rot)
    pivot = np.array([float(x) for x in cube.get("pivot", [0, 0, 0])])
    return m, pivot - m @ pivot


def collect_cubes(geo):
    """Flatten the bone tree into world-space cube corner frames."""
    bones = {b["name"]: b for b in geo["bones"]}
    cache = {}

    def world_of(name):
        if name in cache:
            return cache[name]
        bone = bones[name]
        m, pivot, _ = bone_matrix(bone)
        parent = bone.get("parent")
        if parent and parent in bones:
            pm, poff, _ = world_of(parent)
        else:
            pm, poff = np.eye(3), np.zeros(3)
        # point -> parent(  R*(p - pivot) + pivot )
        total_m = pm @ m
        total_off = poff + pm @ (pivot - m @ pivot)
        cache[name] = (total_m, total_off, None)
        return cache[name]

    out = []
    for name, bone in bones.items():
        m, off, _ = world_of(name)
        for cube in bone.get("cubes", []):
            out.append((name, cube, m, off))
    return out


FACES = {
    #   name: (corner offsets as (x,y,z) multipliers, uv rect key)
    "down":  ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
    "up":    ((0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    "north": ((1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)),
    "south": ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
    "west":  ((1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)),
    "east":  ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)),
}


def uv_rect(fname, u, v, w, h, d):
    """Box-UV rect for one face, using GeckoLib's own rule.

    Two things here are not obvious and both were previewing wrong:

    - **Sizes are floored.** `BakedModelFactory.buildQuad` lays box UV out from
      `Math.floor(size)`, so a cube of size 3.2 addresses three texels, not 3.2 and not four.
      Previewing with the raw float hides exactly the tearing that shows up in game.
    - **`inflate` must not reach here.** It grows the geometry and leaves the UV alone; feeding
      the inflated size in stretches a garment's texture off its own island.
    """
    w, h, d = (math.floor(s) for s in (w, h, d))
    return {
        "up":    (u + d,         v,     w, d),
        "down":  (u + d + w,     v,     w, d),
        "east":  (u,             v + d, d, h),
        "north": (u + d,         v + d, w, h),
        "west":  (u + d + w,     v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }[fname]


def render(cid, out_path):
    """Renders `cid` to `out_path` on the flat preview background."""
    img = render_image(cid)
    if img is None:
        return False
    flat = Image.new("RGB", img.size, BG)
    flat.paste(img, (0, 0), img)
    flat.save(out_path)
    return True


def render_silhouette(cid, size=None, margin=24):
    """Renders `cid` as a flat black shape on transparent, for the silhouette gate."""
    global SILHOUETTE
    SILHOUETTE = True
    try:
        return render_image(cid, size=size, margin=margin)
    finally:
        SILHOUETTE = False


def render_image(cid, size=None, margin=24):
    """Renders `cid` to an RGBA image with a transparent background, or None.

    Split out of `render` so callers that need the creature cut out — the bestiary
    portrait generator wants exactly that — do not have to key a background colour back
    out of a flattened image.
    """
    global SIZE
    previous = SIZE
    if size is not None:
        SIZE = size
    try:
        return _render_rgba(cid, margin)
    finally:
        SIZE = previous


def _render_rgba(cid, margin):
    geo_path = os.path.join(GEO_DIR, cid + ".geo.json")
    tex_path = os.path.join(TEX_DIR, cid + ".png")
    if not (os.path.exists(geo_path) and os.path.exists(tex_path)):
        return None
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    if SILHOUETTE:
        # 1x1 opaque black; every UV lands on the single texel, so all shading vanishes.
        tex = np.zeros((1, 1, 4), dtype=np.float32)
        tex[..., 3] = 1.0
    else:
        tex = np.asarray(Image.open(tex_path).convert("RGBA")).astype(np.float32) / 255.0
    th, tw = tex.shape[0], tex.shape[1]

    view = rot_x(PITCH) @ rot_y(YAW)
    cubes = collect_cubes(geo)
    hidden = set() if SHOW_HIDDEN else RENDERER_HIDDEN.get(cid.split("/")[-1], set())
    if hidden:
        cubes = [c for c in cubes if c[0] not in hidden]

    # ---- gather every face as two textured triangles ----------------------
    tris = []
    pts_all = []
    for _, cube, m, off in cubes:
        ox, oy, oz = (float(c) for c in cube["origin"])
        w, h, d = (float(s) for s in cube["size"])
        inflate = float(cube.get("inflate", 0) or 0)
        uw, uh, ud = w, h, d          # UV uses the *un-inflated* size — see `uv_rect`
        ox -= inflate
        oy -= inflate
        oz -= inflate
        w += 2 * inflate
        h += 2 * inflate
        d += 2 * inflate
        uv = cube.get("uv")
        if not isinstance(uv, list):
            continue
        u0, v0 = float(uv[0]), float(uv[1])

        cm, coff = cube_matrix(cube)

        def corner(mult, cm=cm, coff=coff):
            p = np.array([ox + mult[0] * w, oy + mult[1] * h, oz + mult[2] * d])
            return view @ (MIRROR @ (m @ (cm @ p + coff) + off))

        for fname, mults in FACES.items():
            cs = [corner(mu) for mu in mults]
            ru, rv, rw, rh = uv_rect(fname, u0, v0, uw, uh, ud)
            uvs = [(ru, rv + rh), (ru + rw, rv + rh), (ru + rw, rv), (ru, rv)]
            tris.append((cs, uvs))
            pts_all.extend(cs)

    if not tris:
        return None

    pts = np.array(pts_all)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    centre = (lo + hi) / 2.0
    span = max(hi[0] - lo[0], hi[1] - lo[1]) or 1.0
    scale = (SIZE - margin) / span

    def to_screen(p):
        x = (p[0] - centre[0]) * scale + SIZE / 2.0
        y = SIZE / 2.0 - (p[1] - centre[1]) * scale
        return x, y, p[2]

    frame = np.zeros((SIZE, SIZE, 3), dtype=np.float32)
    zbuf = np.full((SIZE, SIZE), -1e9, dtype=np.float32)

    for cs, uvs in tris:
        s = [to_screen(c) for c in cs]
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            raster(frame, zbuf, [s[a], s[b], s[c]], [uvs[a], uvs[b], uvs[c]], tex, tw, th)

    # Depth buffer doubles as the coverage mask: anything the rasteriser never wrote to
    # is background, so the cut-out is exact rather than a colour-key guess.
    rgba = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    rgba[:, :, :3] = (np.clip(frame, 0, 1) * 255).astype(np.uint8)
    rgba[:, :, 3] = np.where(zbuf > -1e8, 255, 0).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA")


def raster(frame, zbuf, verts, uvs, tex, tw, th):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = verts
    minx = max(0, int(math.floor(min(x0, x1, x2))))
    maxx = min(frame.shape[1] - 1, int(math.ceil(max(x0, x1, x2))))
    miny = max(0, int(math.floor(min(y0, y1, y2))))
    maxy = min(frame.shape[0] - 1, int(math.ceil(max(y0, y1, y2))))
    if minx > maxx or miny > maxy:
        return

    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return

    ys, xs = np.mgrid[miny:maxy + 1, minx:maxx + 1]
    px = xs + 0.5
    py = ys + 0.5
    w0 = ((x1 - x0) * (py - y0) - (px - x0) * (y1 - y0)) / area
    w1 = ((px - x0) * (y2 - y0) - (x2 - x0) * (py - y0)) / area
    w2 = 1.0 - w0 - w1
    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not inside.any():
        return

    z = w2 * z0 + w1 * z1 + w0 * z2
    region_z = zbuf[miny:maxy + 1, minx:maxx + 1]
    visible = inside & (z > region_z)
    if not visible.any():
        return

    u = w2 * uvs[0][0] + w1 * uvs[1][0] + w0 * uvs[2][0]
    v = w2 * uvs[0][1] + w1 * uvs[1][1] + w0 * uvs[2][1]
    ui = np.clip(u.astype(np.int32), 0, tw - 1)
    vi = np.clip(v.astype(np.int32), 0, th - 1)
    texel = tex[vi, ui]
    visible &= texel[..., 3] > 0.5
    if not visible.any():
        return

    region = frame[miny:maxy + 1, minx:maxx + 1]
    region[visible] = texel[..., :3][visible]
    region_z[visible] = z[visible]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--out", default="preview")
    ap.add_argument("--sheet", default="", help="also write a contact sheet here")
    args = ap.parse_args()

    ids = args.ids
    if args.all or not ids:
        ids = sorted(os.path.basename(p)[:-len(".geo.json")]
                     for p in glob.glob(os.path.join(GEO_DIR, "*.geo.json")))
    os.makedirs(args.out, exist_ok=True)

    done = []
    for cid in ids:
        path = os.path.join(args.out, cid + ".png")
        if render(cid, path):
            done.append((cid, path))
    print(f"rendered {len(done)}/{len(ids)}")

    if args.sheet and done:
        cols = 6
        rows = (len(done) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * SIZE, rows * SIZE), BG)
        for i, (_, path) in enumerate(done):
            sheet.paste(Image.open(path), ((i % cols) * SIZE, (i // cols) * SIZE))
        sheet.save(args.sheet)
        print("sheet:", args.sheet)


if __name__ == "__main__":
    main()
