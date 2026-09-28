#!/usr/bin/env python3
"""`hit` and `death` for bespoke creatures — the ones with their own entity class and no creature
definition, whose `rigkit.emit` therefore never runs `reactions.fill`.

Animation parity pass 2026-09-27 (visual style report, C): the Cornish Pixie, Streeler, Augurey
and Mooncalf played no reaction at all — a hit landed on a creature still looping its idle, and a
kill tipped over a creature still flying or walking. The Niffler and Bowtruckle already had theirs.

Each clip is the shared `reactions.hit`/`reactions.death` beat (0.4 s; 1.2 s held on its last
frame) with a small species overlay on the bones the shared skeleton reader cannot place:

  - Cornish Pixie: the hit spins it half-round in the air — a pixie is flung, not jolted.
  - Streeler: eye stalks shrink into the head and the head pulls under the shell; in death the
    stalks stay drawn in and the head sinks flat.
  - Augurey: the beak opens on the hit (its cry); in death the head bows under the folded wings.
  - Mooncalf: the huge eyes pop wide on the hit and shut (squashed flat) in death.

Clips are returned in the authoring sense, like every generator's own clips; `rigkit.emit`
converts them. Pass `game_sense=True` for a generator that writes its animation itself.

The `EntityClass.hurtServer`/`die` pair triggers them on a `<id>_action` controller registered
after the movement controller (GeckoLib: the last controller to touch a bone wins).

Run from the repo root to patch the shipped animation files in place (geo and skin untouched):
    python tools/bespoke_reactions.py cornish_pixie streeler augurey mooncalf
Idempotent: clips already on disk are replaced by the same clips.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import reactions  # noqa: E402
import rigkit  # noqa: E402
from rigkit import kf  # noqa: E402


def _pulse(peak, end, value, rest=(0, 0, 0)):
    return kf((0.0, rest), (peak, value), (end, rest))


def _held(at, value, hold=1.2, rest=(0, 0, 0), recoil=None):
    frames = [(0.0, rest)]
    if recoil:
        frames.append((0.15, recoil))
    frames += [(at, value), (hold, value)]
    return kf(*frames)


def _cornish_pixie(hit, death):
    # A half-spin in the air and back: yaw first, the roll trailing it.
    hit["bones"]["body"]["rotation"] = kf((0.0, (0, 0, 0)), (0.1, (-7, 38, -14)), (0.24, (0, -12, 6)),
                                          (0.4, (0, 0, 0)))
    # Dead in the air: the head rolls to one side as it drops.
    death["bones"]["head"]["rotation"] = _held(0.75, (28, 0, 16), recoil=(-22, 0, 0))


def _streeler(hit, death):
    stalks = ("stalk_left", "stalk_right")
    for s in stalks:
        hit["bones"][s] = {"scale": kf((0.0, (1, 1, 1)), (0.08, (1, 0.3, 1)), (0.26, (1, 0.45, 1)),
                                       (0.4, (1, 1, 1))),
                           "rotation": _pulse(0.08, 0.4, (-30, 0, 0))}
        death["bones"][s] = {"scale": _held(0.5, (1, 0.35, 1), rest=(1, 1, 1)),
                             "rotation": _held(0.6, (40, 0, 0))}
    # Head pulls back toward the shell (+z is back), shell rocks with it.
    hit["bones"]["head"]["position"] = kf((0.0, (0, 0, 0)), (0.08, (0, -0.4, 1.6)), (0.26, (0, -0.2, 0.8)),
                                          (0.4, (0, 0, 0)))
    hit["bones"]["shell"] = {"rotation": _pulse(0.1, 0.4, (-6, 0, 0))}
    death["bones"]["head"]["position"] = _held(0.7, (0, -1.2, 0.6))
    death["bones"]["shell"] = {"rotation": _held(0.8, (4, 0, 8), recoil=(-6, 0, 0))}


def _augurey(hit, death):
    hit["bones"]["beak"] = {"rotation": _pulse(0.08, 0.36, (18, 0, 0))}
    # Head bowed deep under the folded wings: the mourner's posture.
    death["bones"]["head"]["rotation"] = _held(0.8, (44, 0, 0), recoil=(-22, 0, 0))
    death["bones"]["beak"] = {"rotation": _held(0.8, (8, 0, 0))}


def _mooncalf(hit, death):
    for e in ("eye_l", "eye_r"):
        hit["bones"][e] = {"scale": kf((0.0, (1, 1, 1)), (0.08, (1.3, 1.3, 1.3)), (0.22, (1.1, 1.1, 1.1)),
                                       (0.4, (1, 1, 1)))}
        death["bones"][e] = {"scale": _held(0.7, (1, 0.25, 1), rest=(1, 1, 1))}


SPECIES = {
    "cornish_pixie": _cornish_pixie,
    "streeler": _streeler,
    "augurey": _augurey,
    "mooncalf": _mooncalf,
}


def clips_for(cid, bones, *, game_sense=False):
    """`{"hit": ..., "death": ...}` for `cid`, from `(name, parent, pivot)` bone triples."""
    sk = reactions.Skeleton(bones)
    clips = {"hit": reactions.hit(sk), "death": reactions.death(sk)}
    SPECIES[cid](clips["hit"], clips["death"])
    names = {b[0] for b in bones}
    dangling = sorted({n for c in clips.values() for n in c["bones"] if n not in names})
    if dangling:
        raise ValueError(f"{cid}: reaction clips animate missing bones {dangling}")
    if game_sense:
        rigkit.to_game_sense(_NoBones(), clips)
    return clips


class _NoBones:
    bones = ()


def patch(cid):
    geo_path = os.path.join(rigkit.GEO_DIR, cid + ".geo.json")
    anim_path = os.path.join(rigkit.ANIM_DIR, cid + ".animation.json")
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    bones = [(b["name"], b.get("parent"), b.get("pivot", [0, 0, 0])) for b in geo["bones"]]
    anim = json.load(open(anim_path, encoding="utf-8"))
    for k, v in clips_for(cid, bones, game_sense=True).items():
        anim["animations"][f"animation.{cid}.{k}"] = v
    with open(anim_path, "w", encoding="utf-8") as fh:
        json.dump(anim, fh, indent=2)
        fh.write("\n")
    print(f"{cid}: hit + death -> {anim_path}")


if __name__ == "__main__":
    for c in sys.argv[1:]:
        patch(c)
