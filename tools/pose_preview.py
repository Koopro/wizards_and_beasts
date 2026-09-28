#!/usr/bin/env python3
"""Render a rig *posed* — one frame of one clip — from several angles.

`beast_preview.py` renders the rest pose, which is enough for a rig whose rest pose is the pose
the player sees. It is not enough for a dragon whose wings rest spread and are folded by every
ground clip, or for checking that a death clip folds legs the right way: the rotation sign rules
are easy to get backwards and only a picture settles them.

The clip's keyframes are baked into a copy of the geo (rotation added to the bone's rest
rotation, position added to its pivot and cubes, scale ignored), then drawn by `beast_preview`
from the front ¾, the side and above.

    python tools/pose_preview.py <id> [clip[@time] ...] [--out file.png]
    python tools/pose_preview.py chinese_fireball idle fly@0.35 death@1.2
"""

import argparse
import json
import math
import os
import shutil
import sys
import tempfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beast_preview  # noqa: E402

ASSETS = "src/main/resources/assets/wizards_and_beasts"
GEO_DIR = f"{ASSETS}/geckolib/models/entity"
ANIM_DIR = f"{ASSETS}/geckolib/animations/entity"
TEX_DIR = f"{ASSETS}/textures/entity"

VIEWS = (("front", 180 - 32, 20), ("side", 90, 8), ("back", -35, 20), ("top", 180, 70))


def sample(track, t):
    """Linear interpolation over a Bedrock keyframe track at time `t`."""
    if isinstance(track, list):
        return [float(v) for v in track]
    keys = sorted((float(k), v) for k, v in track.items())
    if t <= keys[0][0]:
        return [float(v) for v in _val(keys[0][1])]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            a = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [x + (y - x) * a for x, y in zip(_val(v0), _val(v1))]
    return [float(v) for v in _val(keys[-1][1])]


def _val(v):
    if isinstance(v, dict):
        v = v.get("post", v.get("pre"))
    return v


def posed_geo(cid, clip, t):
    geo = json.load(open(f"{GEO_DIR}/{cid}.geo.json", encoding="utf-8"))
    if not clip:
        return geo
    anims = json.load(open(f"{ANIM_DIR}/{cid}.animation.json", encoding="utf-8"))["animations"]
    key = next(k for k in anims if k.endswith("." + clip))
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    children = {}
    for b in bones.values():
        children.setdefault(b.get("parent"), []).append(b["name"])
    for name, tracks in anims[key]["bones"].items():
        bone = bones.get(name)
        if bone is None:
            continue
        if "rotation" in tracks:
            r = sample(tracks["rotation"], t)
            base = bone.get("rotation", [0, 0, 0])
            bone["rotation"] = [base[i] + r[i] for i in range(3)]
        if "scale" in tracks:
            bone["_scale"] = sample(tracks["scale"], t)
        if "position" in tracks:
            p = sample(tracks["position"], t)
            # GeckoLib mirrors X on translation, as it does on the geo itself.
            d = (-p[0], p[1], p[2])
            stack = [name]
            while stack:
                n = stack.pop()
                bb = bones[n]
                bb["pivot"] = [bb["pivot"][i] + d[i] for i in range(3)]
                for c in bb.get("cubes", []):
                    c["origin"] = [c["origin"][i] + d[i] for i in range(3)]
                    if "pivot" in c:
                        c["pivot"] = [c["pivot"][i] + d[i] for i in range(3)]
                stack.extend(children.get(n, []))
    return geo


def render_frames(cid, frames, size=300):
    tmp = tempfile.mkdtemp()
    old = (beast_preview.GEO_DIR, beast_preview.TEX_DIR)
    beast_preview.GEO_DIR = beast_preview.TEX_DIR = tmp
    beast_preview.SHOW_HIDDEN = any(clip in ("burst", "ashes", "rise") for clip, _ in frames)
    rows = []
    try:
        shutil.copy(f"{TEX_DIR}/{cid}.png", f"{tmp}/{cid}.png")
        for clip, t in frames:
            with open(f"{tmp}/{cid}.geo.json", "w", encoding="utf-8") as fh:
                json.dump(posed_geo(cid, clip, t), fh)
            row = []
            for _, yaw, pitch in VIEWS:
                beast_preview.YAW = math.radians(yaw)
                beast_preview.PITCH = math.radians(pitch)
                row.append(beast_preview.render_image(cid, size=size))
            rows.append((f"{clip or 'rest'}@{t}", row))
    finally:
        beast_preview.GEO_DIR, beast_preview.TEX_DIR = old
        shutil.rmtree(tmp, ignore_errors=True)
    sheet = Image.new("RGB", (size * len(VIEWS), size * len(rows)), beast_preview.BG)
    d = ImageDraw.Draw(sheet)
    for r, (label, row) in enumerate(rows):
        for c, im in enumerate(row):
            if im is not None:
                sheet.paste(im, (c * size, r * size), im)
        d.text((4, r * size + 4), label, fill=(255, 255, 0))
    return sheet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id")
    ap.add_argument("frames", nargs="*")
    ap.add_argument("--out", default=None)
    ap.add_argument("--size", type=int, default=300)
    a = ap.parse_args()
    frames = []
    for f in a.frames or ["rest"]:
        clip, _, t = f.partition("@")
        frames.append((None if clip == "rest" else clip, float(t) if t else 0.0))
    out = a.out or f"build/entity-art/pose/{a.id}.png"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    render_frames(a.id, frames, a.size).save(out)
    print(out)


if __name__ == "__main__":
    main()
