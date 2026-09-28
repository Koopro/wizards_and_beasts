#!/usr/bin/env python3
"""Body archetypes over `rigkit` — the skeleton a whole family of creatures shares.

Wave 1 built twelve creatures one bone at a time and the same forty lines turned up in every
quadruped: a barrel, a chest, a croup, a neck raked forward and a head that cancels the rake,
four jointed legs, a tail. Nothing about those lines was the animal; the animal was the horn,
the mane, the palette and the clip. This module is those forty lines, written once, so a
creature file can be about the creature.

An archetype builds the skeleton and returns an `Anchors` record naming what it made — the
head bone, the top of the skull, the tail tip — so a generator can hang its distinguishing
parts off it without re-deriving any coordinate. It never paints and never animates beyond the
locomotion loops (`idle`, `walk`, `fly`, `swim`) that every member of the family needs; the
reaction clips are the creature's own.

Coordinates follow `rigkit`: facing north (-Z), feet on y=0, one unit = one texel = 1/16 block.
Anchors are in authoring coordinates, so a generator adds its parts first and calls `ground()`
last, which drops the posed rig so its lowest point is the floor — joint rotations swing feet
off the authored heights, and a rig that floats or sinks by a unit reads as either.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rigkit import Rig, bob, gait, osc  # noqa: E402


class Anchors(dict):
    """Named attachment points an archetype hands back. Attribute access for brevity."""

    __getattr__ = dict.__getitem__


# --------------------------------------------------------------------------- shared pieces


def ground(rig):
    """Shift the whole rig vertically so its posed lowest point sits exactly on y=0."""
    lo, _ = rig.bounds()
    dy = -lo[1]
    if abs(dy) < 1e-6:
        return 0.0
    for bone in rig.bones:
        x, y, z = bone.pivot
        bone.pivot = (x, y + dy, z)
        for cube in bone.cubes:
            cube["origin"][1] = round(cube["origin"][1] + dy, 3)
            if "pivot" in cube:
                cube["pivot"][1] = round(cube["pivot"][1] + dy, 3)
    return dy


def leg(rig, name, parent, *, x, z, sign, top, segments):
    """A leg authored top-down whose segment heights must sum to `top`.

    `segments` is `[(suffix, height, width, depth, rotation)]`, rotation written for the left
    side (+x); the right side gets Y and Z negated. Summing to the hip height is
    enforced rather than trusted: a leg a unit short floats the whole animal after `ground()`
    lifts it off its lowest foot, and one a unit long pushes the belly into the floor.
    """
    total = sum(s[1] for s in segments)
    if total != top:
        raise ValueError(f"{rig.name}: {name} segments sum to {total}, hip is at {top}")
    parent_name = parent
    y = top
    for suffix, height, w, d, rot in segments:
        bone = f"{name}_{suffix}" if suffix else name
        # Mirroring across x=0 flips the Y and Z rotation senses, so one segment list serves both
        # sides: a lizard's leg sprawls out on the left and on the right from the same numbers.
        if rot:
            rot = (rot[0], sign * rot[1], sign * rot[2])
        rig.bone(bone, parent_name, (sign * x, y, z), rot)
        size = (w, height, d)
        rig.cube(bone, Rig.mirror((x - w / 2.0, y - height, z - d / 2.0), size, sign), size)
        parent_name = bone
        y -= height
    return parent_name


def chain(rig, name, parent, *, start, direction, segments, rotations=None, key=None):
    """A straight chain of boxes along +Z (back) or -Z (forward) — a tail, a neck, a trunk.

    `segments` is `[(length, width, height)]`, centred on the chain's axis; each segment is a
    bone pivoted at its near end, so a rotation track on any link bends everything after it.
    Returns the bone names in order.
    """
    x, y, z = start
    sgn = 1 if direction > 0 else -1
    parent_name = parent
    names = []
    for i, (length, w, h) in enumerate(segments):
        bone = f"{name}_{i + 1}" if len(segments) > 1 else name
        rot = rotations[i] if rotations and i < len(rotations) else None
        rig.bone(bone, parent_name, (x, y, z), rot)
        oz = z if sgn > 0 else z - length
        rig.cube(bone, (x - w / 2.0, y - h / 2.0, oz), (w, h, length),
                 key=(f"{key}_{i + 1}" if key and len(segments) > 1 else key))
        names.append(bone)
        parent_name = bone
        z += sgn * length
    return names


def pair_horn(rig, parent, *, name, base, size, rotation, tip=None):
    """A mirrored pair of horns, each a base cube plus an optional narrower tip cube."""
    def build(side, sign):
        bx, by, bz = base
        rx, ry, rz = rotation
        bone = f"{name}_{side}"
        rig.bone(bone, parent, (sign * bx, by, bz), (rx, sign * ry, sign * rz))
        w, h, d = size
        rig.cube(bone, Rig.mirror((bx - w / 2.0, by, bz - d / 2.0), size, sign), size)
        if tip:
            tw, th, td = tip
            rig.cube(bone, Rig.mirror((bx - tw / 2.0, by + h, bz - td / 2.0), tip, sign), tip,
                     key=f"{bone}_tip")
    rig.pair(build)


# --------------------------------------------------------------------------- quadruped


def quadruped(rig, *, hip, chest, barrel, croup, neck, neck_rake, head, head_pitch=None,
              muzzle=None, fore, hind, fore_x, hind_x, tail=None, tail_lift=20,
              croup_drop=-4, head_y=None):
    """The four-legged skeleton.

    Sizes are `(width, height, depth)`. The body boxes run chest → barrel → croup back along +Z
    with their undersides at `hip - 2`, so the leg tops tuck two units inside the belly and no
    daylight shows at the shoulder.

    `neck` is `(width, length, depth)` authored straight up and raked forward by `neck_rake`
    degrees; the head cancels the rake (plus `head_pitch`, positive = nose down) so it ends level
    at the top of the neck. `neck=None` sets the head straight onto the chest at `head_y`, which
    is what cats, pigs and lizards want.

    `fore`/`hind` are `leg()` segment lists summing to `hip`. `tail` is a `chain()` segment list
    lifted by `tail_lift` degrees at the root.
    """
    belly = hip - 2
    cw, ch, cd = chest
    bw, bh, bd = barrel
    kw, kh, kd = croup
    length = cd + bd + kd
    z0 = -length / 2.0

    rig.bone("body", "root", (0, belly + bh / 2.0, 0))
    rig.cube("body", (-cw / 2.0, belly, z0), chest, key="chest")
    rig.cube("body", (-bw / 2.0, belly, z0 + cd), barrel, key="barrel")
    croup_z = z0 + cd + bd
    rig.bone("croup", "body", (0, belly + kh, croup_z), (croup_drop, 0, 0))
    rig.cube("croup", (-kw / 2.0, belly, croup_z), croup)

    top = belly + max(ch, bh)
    anchors = Anchors(front=z0, back=croup_z + kd, top=top, belly=belly)

    head_w, head_h, head_d = head
    if neck:
        nw, nl, nd = neck
        neck_z = z0 + nd / 2.0 + 1
        neck_base = belly + ch - 2
        rig.bone("neck", "body", (0, neck_base, neck_z), (neck_rake, 0, 0))
        rig.cube("neck", (-nw / 2.0, neck_base, neck_z - nd / 2.0), (nw, nl, nd))
        pitch = -neck_rake + (head_pitch or 0)
        head_pivot = (0, neck_base + nl, neck_z)
        rig.bone("head", "neck", head_pivot, (pitch, 0, 0))
        hy = neck_base + nl - head_h + 1
        hz = neck_z + nd / 2.0 - head_d
    else:
        hy = head_y if head_y is not None else belly + ch - head_h + 1
        head_pivot = (0, hy + head_h / 2.0, z0 + 1)
        rig.bone("head", "body", head_pivot, ((head_pitch or 0), 0, 0))
        hz = z0 + 1 - head_d
    rig.cube("head", (-head_w / 2.0, hy, hz), head)
    anchors.update(head="head", head_top=hy + head_h, head_front=hz, head_y=hy,
                   head_w=head_w, head_d=head_d, head_back=hz + head_d)
    if muzzle:
        mw, mh, md = muzzle
        rig.cube("head", (-mw / 2.0, hy, hz - md), muzzle, key="muzzle")
        anchors["muzzle_front"] = hz - md

    fore_z = z0 + min(cd, 4) / 2.0 + 1
    hind_z = croup_z + kd / 2.0
    rig.pair(lambda side, sign: leg(rig, f"foreleg_{side}", "body", x=fore_x, z=fore_z,
                                    sign=sign, top=hip, segments=fore))
    rig.pair(lambda side, sign: leg(rig, f"hindleg_{side}", "croup", x=hind_x, z=hind_z,
                                    sign=sign, top=hip, segments=hind))
    anchors.update(fore_z=fore_z, hind_z=hind_z)

    if tail:
        tail_y = belly + kh - 2
        names = chain(rig, "tail", "croup", start=(0, tail_y, croup_z + kd - 1), direction=1,
                      segments=tail, rotations=[(tail_lift, 0, 0)] + [(8, 0, 0)] * (len(tail) - 1))
        anchors["tail"] = names
    return anchors


def quadruped_loops(anchors, *, idle_len=4.0, walk_len=0.9, stride=24.0, knee=14.0, bob_amp=0.4):
    """`idle` and `walk` for anything `quadruped()` built. Returns clip-body dicts, not clips."""
    legs = {"foreleg_left": 0.0, "foreleg_right": 0.5, "hindleg_left": 0.5, "hindleg_right": 0.0}
    tail = anchors.get("tail") or []
    idle = {
        "body": bob(idle_len, 0.15, cycles=2),
        "head": {"rotation": {"0.0": [0, -8, 0], str(round(idle_len / 3, 3)): [2, 9, 0],
                              str(round(2 * idle_len / 3, 3)): [-2, -4, 0],
                              str(round(idle_len, 3)): [0, -8, 0]}},
        **{name: osc(idle_len, 5.0 + 3.0 * i, axis="z", phase=0.15 * i)
           for i, name in enumerate(tail)},
    }
    walk = {
        **gait(walk_len, legs, amp=stride),
        "root": bob(walk_len, bob_amp, cycles=2),
        "body": osc(walk_len, 2.0, axis="y"),
        "head": osc(walk_len, 3.0, phase=0.25),
        **{name: osc(walk_len, 6.0 + 2.0 * i, axis="z", phase=0.2 * i)
           for i, name in enumerate(tail)},
    }
    return idle, walk, legs


def knee_tracks(rig, legs, length, amp):
    """Second-joint swing for every leg that has one, trailing the hip by a sixth of a cycle."""
    out = {}
    for name, phase in legs.items():
        for suffix in ("cannon", "shank", "hock", "shin", "lower"):
            bone = f"{name}_{suffix}"
            if bone in rig.bone_names():
                out[bone] = osc(length, amp, phase=phase + 0.15)
    return out



# --------------------------------------------------------------------------- biped


def hang(rig, name, parent, *, x, z, sign, top, segments, root_rot=None):
    """A limb hanging down from `top` — an arm. Like `leg()` but with no floor to reach.

    `root_rot` is added to the first segment's rotation; `biped()` passes the negated hunch
    through it so arms hang plumb from a torso that leans forward, instead of swinging forward
    with it.
    """
    parent_name = parent
    y = top
    for i, (suffix, height, w, d, rot) in enumerate(segments):
        bone = f"{name}_{suffix}" if suffix else name
        rx, ry, rz = rot or (0, 0, 0)
        if i == 0 and root_rot:
            rx, ry, rz = rx + root_rot[0], ry + root_rot[1], rz + root_rot[2]
        rig.bone(bone, parent_name, (sign * x, y, z), (rx, sign * ry, sign * rz))
        size = (w, height, d)
        rig.cube(bone, Rig.mirror((x - w / 2.0, y - height, z - d / 2.0), size, sign), size)
        parent_name = bone
        y -= height
    return parent_name


def biped(rig, *, hip, pelvis, torso, head, arms, legs, arm_x, leg_x, hunch=0, neck=0,
          head_level=0.85, head_z=0.0):
    """The two-legged skeleton: pelvis on the legs, a torso that can hunch, head, two arms.

    `torso` is `(width, height, depth)` authored upright and leaned forward by `hunch` degrees at
    the waist; the head takes back `head_level` of that lean so a stooped creature still looks
    ahead, and the arms take back all of it so they hang plumb. `neck` lifts the head clear of the
    shoulders by that many units. `head_z` shifts the skull forward (negative) off the spine — a
    jutting head is most of what separates a brute from a man.

    `arms`/`legs` are `[(suffix, length, width, depth, rotation)]`; leg lengths must sum to `hip`.
    """
    pw, ph, pd = pelvis
    tw, th, td = torso
    hw, hh, hd = head
    pelvis_y = hip - 2
    rig.bone("body", "root", (0, hip, 0))
    rig.cube("body", (-pw / 2.0, pelvis_y, -pd / 2.0), pelvis, key="pelvis")

    waist = pelvis_y + ph
    rig.bone("chest", "body", (0, waist - 1, 0), (hunch, 0, 0) if hunch else None)
    rig.cube("chest", (-tw / 2.0, waist - 1, -td / 2.0), torso, key="torso")
    shoulder = waist - 1 + th

    head_bottom = shoulder + neck
    rig.bone("head", "chest", (0, shoulder, head_z), (-hunch * head_level, 0, 0) if hunch else None)
    if neck:
        rig.cube("head", (-max(2, hw // 2) / 2.0, shoulder - 1, head_z - 1), (max(2, hw // 2), neck + 1, 2),
                 key="neck")
    rig.cube("head", (-hw / 2.0, head_bottom, head_z - hd / 2.0), head, key="head")

    rig.pair(lambda side, sign: hang(rig, f"arm_{side}", "chest", x=arm_x, z=0, sign=sign,
                                     top=shoulder - 1, segments=arms,
                                     root_rot=(-hunch, 0, 0) if hunch else None))
    rig.pair(lambda side, sign: leg(rig, f"leg_{side}", "body", x=leg_x, z=0, sign=sign,
                                    top=hip, segments=legs))
    return Anchors(head="head", head_y=head_bottom, head_top=head_bottom + hh,
                   head_front=head_z - hd / 2.0, head_back=head_z + hd / 2.0, head_w=hw,
                   shoulder=shoulder, waist=waist, torso_front=-td / 2.0, torso_back=td / 2.0,
                   pelvis_y=pelvis_y, hip=hip, arm_x=arm_x)


def biped_loops(rig, anchors, *, idle_len=4.0, walk_len=1.0, stride=30.0, arm_swing=None,
                bob_amp=0.4):
    """`idle` and `walk` for anything `biped()` built."""
    arm_swing = stride * 0.7 if arm_swing is None else arm_swing
    idle = {
        "chest": osc(idle_len, 2.0, phase=0.0),
        "head": {"rotation": {"0.0": [0, -10, 0], str(round(idle_len / 3, 3)): [3, 12, 0],
                              str(round(2 * idle_len / 3, 3)): [-2, -4, 2],
                              str(round(idle_len, 3)): [0, -10, 0]}},
        "arm_left": osc(idle_len, 3.0, axis="z", phase=0.0),
        "arm_right": osc(idle_len, 3.0, axis="z", phase=0.5),
    }
    walk = {
        "leg_left": osc(walk_len, stride, phase=0.0),
        "leg_right": osc(walk_len, stride, phase=0.5),
        "arm_left": osc(walk_len, arm_swing, phase=0.5),
        "arm_right": osc(walk_len, arm_swing, phase=0.0),
        "root": bob(walk_len, bob_amp, cycles=2),
        "chest": osc(walk_len, 3.0, axis="y", phase=0.25),
    }
    for side, phase in (("left", 0.0), ("right", 0.5)):
        for suffix in ("shin", "lower", "calf"):
            bone = f"leg_{side}_{suffix}"
            if bone in rig.bone_names():
                walk[bone] = osc(walk_len, stride * 0.6, phase=phase + 0.2)
        for suffix in ("fore", "forearm"):
            bone = f"arm_{side}_{suffix}"
            if bone in rig.bone_names():
                walk[bone] = osc(walk_len, arm_swing * 0.4, phase=(0.5 - phase) + 0.15)
    return idle, walk


# --------------------------------------------------------------------------- wings and birds


def wing_pair(rig, parent, *, x, y, z, root, tip, rest_roll=-10, tip_yaw=-8, name="wing", primaries=0,
              primary_step=3):
    """Two-segment folded wings hanging from the shoulders along the flanks.

    `root`/`tip` are `(length, height, thickness)`: the slab runs back along +Z and hangs down
    from `y`. Folded is the rest pose because it is what a creature on the ground shows; flight
    unfolds it by rolling the root about Z. **Positive Z roll swings the left wing out** (and
    the right wing takes the negated value), so a spread wing sits near +80 on the left.

    `primaries` splits the tip slab into that many flight-feather plates stacked down the chord,
    each `primary_step` longer than the one above, so the trailing edge steps like feather tips
    instead of ending in one square plank. The plates share the tip bone and never overlap, so
    every existing clip still drives them and no two faces are coplanar.
    """
    def build(side, sign):
        bone = f"{name}_{side}"
        rl, rh, rt = root
        rig.bone(bone, parent, (sign * x, y, z), (0, 0, sign * rest_roll))
        rig.cube(bone, Rig.mirror((x, y - rh, z - 1), (rt, rh, rl), sign), (rt, rh, rl))
        tl, th, tt = tip
        tz = z + rl - 3
        rig.bone(f"{bone}_tip", bone, (sign * x, y - 1, tz), (0, sign * tip_yaw, 0))
        if primaries <= 1:
            rig.cube(f"{bone}_tip", Rig.mirror((x, y - 1 - th, tz), (tt, th, tl), sign), (tt, th, tl))
            return
        bands = [th // primaries + (1 if i < th % primaries else 0) for i in range(primaries)]
        top = y - 1
        for i, band in enumerate(bands):
            length = max(2, tl - (primaries - 1 - i) * primary_step)
            rig.cube(f"{bone}_tip", Rig.mirror((x, top - band, tz), (tt, band, length), sign),
                     (tt, band, length), key=f"{bone}_tip_p{i}")
            top -= band
    rig.pair(build)
    return [f"{name}_left", f"{name}_right"]


def wing_beat(length, *, spread=78.0, amp=38.0, tip_lag=0.12, name="wing"):
    """A full flap per `length` seconds around a spread `spread` degrees out from folded."""
    out = {}
    for side, sign in (("left", 1), ("right", -1)):
        track = {}
        tip = {}
        for i in range(5):
            t = length * i / 4
            phase = [0.0, 1.0, 0.0, -1.0, 0.0][i]
            track[str(round(t, 3))] = [0, 0, round(sign * (spread + amp * phase), 2)]
            tip_phase = [0.0, 0.7, 0.0, -0.7, 0.0][(i + 1) % 5] if tip_lag else 0.0
            tip[str(round(t, 3))] = [0, round(sign * 20 * tip_phase, 2), round(sign * 18 * tip_phase, 2)]
        out[f"{name}_{side}"] = {"rotation": track}
        out[f"{name}_{side}_tip"] = {"rotation": tip}
    return out


def bird(rig, *, leg_h, body, head, beak, legs, leg_x, neck=None, neck_rake=0, tail=None,
         tail_lift=10, body_pitch=0, wings=None):
    """A bird: an egg of a body on two jointed legs, neck, head, beak, tail fan, folded wings.

    `legs` sum to `leg_h`. `wings` is `(root, tip)` for `wing_pair`, attached at the top front of
    the body. `body_pitch` tips the body nose-up (negative) or down (positive) about the hips —
    a dodo stands level, a heron stands tall.
    """
    bw, bh, bd = body
    rig.bone("body", "root", (0, leg_h + bh / 2.0, 0), (body_pitch, 0, 0) if body_pitch else None)
    rig.cube("body", (-bw / 2.0, leg_h - 1, -bd / 2.0), body, key="torso")
    top = leg_h - 1 + bh
    front = -bd / 2.0
    hw, hh, hd = head
    if neck:
        nw, nl, nd = neck
        rig.bone("neck", "body", (0, top - 2, front + nd / 2.0), (neck_rake, 0, 0))
        rig.cube("neck", (-nw / 2.0, top - 2, front), neck)
        rig.bone("head", "neck", (0, top - 2 + nl, front + nd / 2.0), (-neck_rake, 0, 0))
        head_y = top - 3 + nl
        head_z = front + nd - hd
    else:
        rig.bone("head", "body", (0, top - 1, front + 1))
        head_y = top - 2
        head_z = front + 2 - hd
    rig.cube("head", (-hw / 2.0, head_y, head_z), head, key="head")
    kw, kh, kd = beak
    rig.bone("beak", "head", (0, head_y + kh / 2.0 + 1, head_z))
    rig.cube("beak", (-kw / 2.0, head_y + 1, head_z - kd), beak, key="beak")
    anchors = Anchors(head="head", head_y=head_y, head_top=head_y + hh, head_front=head_z,
                      head_back=head_z + hd, head_w=hw, top=top, front=front, back=bd / 2.0,
                      leg_h=leg_h, body_w=bw)
    rig.pair(lambda side, sign: leg(rig, f"leg_{side}", "body", x=leg_x, z=1, sign=sign,
                                    top=leg_h, segments=legs))
    if tail:
        tw, th, td = tail
        rig.bone("tail", "body", (0, top - 2, bd / 2.0 - 1), (-tail_lift, 0, 0))
        rig.cube("tail", (-tw / 2.0, top - 2 - th / 2.0, bd / 2.0 - 1), tail, key="tail")
    if wings:
        root, tip = wings
        anchors["wings"] = wing_pair(rig, "body", x=bw / 2.0, y=top - 1, z=front + 1,
                                     root=root, tip=tip)
    return anchors


def bird_loops(anchors, *, idle_len=3.0, walk_len=0.6, fly_len=0.6, stride=34.0, spread=78.0,
               amp=40.0):
    """`idle`, `walk` and `fly` for anything `bird()` built — take the ones the locomotion binds."""
    idle = {
        "head": {"rotation": {"0.0": [0, -14, 0], str(round(idle_len * 0.3, 3)): [6, 10, 0],
                              str(round(idle_len * 0.6, 3)): [-4, 0, 0],
                              str(round(idle_len, 3)): [0, -14, 0]}},
        "body": bob(idle_len, 0.15, cycles=2),
    }
    walk = {
        "leg_left": osc(walk_len, stride, phase=0.0),
        "leg_right": osc(walk_len, stride, phase=0.5),
        "head": {"rotation": {"0.0": [0, 0, 0], str(round(walk_len / 4, 3)): [-10, 0, 0],
                              str(round(walk_len / 2, 3)): [0, 0, 0],
                              str(round(3 * walk_len / 4, 3)): [-10, 0, 0],
                              str(round(walk_len, 3)): [0, 0, 0]}},
        "root": bob(walk_len, 0.4, cycles=2),
    }
    fly = {
        **(wing_beat(fly_len, spread=spread, amp=amp) if anchors.get("wings") else {}),
        "leg_left": {"rotation": {"0.0": [60, 0, 0], str(fly_len): [60, 0, 0]}},
        "leg_right": {"rotation": {"0.0": [60, 0, 0], str(fly_len): [60, 0, 0]}},
        "root": bob(fly_len, 0.8, cycles=1),
    }
    return idle, walk, fly


# --------------------------------------------------------------------------- serpents, fish, arthropods


def serpent(rig, *, y, segments, head, jaw=None, yaws=None, parent="root", name="seg"):
    """A body chain running back from a head at the front, resting in a gentle S.

    `segments` are `chain()` specs `(length, width, height)` from neck to tail tip; `yaws` the rest
    yaw of each link, so the coil is authored rather than straight. The head faces north off the
    front of the first segment on its own bone, with an optional hinged `jaw`.
    """
    names = chain(rig, name, parent, start=(0, y, 0), direction=1, segments=segments,
                  rotations=[(0, yw, 0) for yw in (yaws or [0] * len(segments))])
    hw, hh, hd = head
    rig.bone("head", names[0], (0, y, 0))
    rig.cube("head", (-hw / 2.0, y - hh / 2.0, -hd), head, key="head")
    anchors = Anchors(head="head", head_y=y - hh / 2.0, head_top=y + hh / 2.0, head_front=-hd,
                      head_w=hw, segments=names)
    if jaw:
        jw, jh, jd = jaw
        rig.bone("jaw", "head", (0, y - hh / 2.0, -1))
        rig.cube("jaw", (-jw / 2.0, y - hh / 2.0 - jh, -jd), jaw, key="jaw")
    return anchors


def undulate(length, bones, *, amp=18.0, axis="y", wave=0.18, grow=0.0):
    """A travelling sine wave down a chain: each link lags the one before by `wave` cycles."""
    return {bone: osc(length, amp + grow * i, axis=axis, phase=-wave * i) for i, bone in enumerate(bones)}


def splay_leg(rig, name, parent, *, hip_y, x, z, sign, yaw, coxa, femur, tibia, tarsus, thick=2,
              coxa_lift=12, femur_lift=26, bend=-24):
    """An arthropod leg: out from the body, up to a knee above it, down to the floor.

    The tibia angle is solved so the foot lands on y=0 (see `acromantula_model.py`, where the two
    ways this goes wrong were found): yaw sits on a cube-less hip bone so every roll is in the
    leg's plane, and the bisection is restricted to the range where the foot falls monotonically.
    """
    import math
    knee = hip_y + coxa * math.sin(math.radians(coxa_lift)) \
        + femur * math.sin(math.radians(coxa_lift + femur_lift))

    def foot(angle):
        return knee + tibia * math.sin(math.radians(angle)) + tarsus * math.sin(math.radians(angle + bend))

    lo, hi = -100.0, 0.0
    if not foot(lo) < 0 < foot(hi):
        raise ValueError(f"{rig.name}: {name} cannot reach the floor (knee {knee:.1f}, reach {tibia + tarsus})")
    for _ in range(60):
        mid = (lo + hi) / 2
        lo, hi = (lo, mid) if foot(mid) > 0 else (mid, hi)
    tib = (lo + hi) / 2

    rig.bone(f"{name}_hip", parent, (sign * x, hip_y, z), (0, sign * yaw, 0))
    joints = [("coxa", coxa, (0, 0, sign * coxa_lift)), ("femur", femur, (0, 0, sign * femur_lift)),
              ("tibia", tibia, (0, 0, sign * (tib - coxa_lift - femur_lift))), ("tarsus", tarsus, (0, 0, sign * bend))]
    parent_name = f"{name}_hip"
    px = x
    for suffix, length, rot in joints:
        bone = f"{name}_{suffix}"
        rig.bone(bone, parent_name, (sign * px, hip_y, z), rot)
        t = thick if suffix in ("coxa", "femur") else max(1, thick - 1)
        size = (length, t, t)
        rig.cube(bone, Rig.mirror((px, hip_y - t / 2.0, z - t / 2.0), size, sign), size)
        parent_name = bone
        px += length
    return f"{name}_hip"


def sphere(rig, bone, *, c, y0=0.0, key="ball"):
    """The cross-of-slabs ball from `puffskein_model.py`: a core and three slabs through it."""
    h = c / 2.0
    rig.cube(bone, (-h, y0 + 1, -h), (c, c, c), key=f"{key}_core")
    rig.cube(bone, (-h - 1, y0 + 2, -h + 1), (c + 2, c - 2, c - 2), key=f"{key}_wide")
    rig.cube(bone, (-h, y0 + 2, -h - 1), (c, c - 2, c + 2), key=f"{key}_deep")
    rig.cube(bone, (-h + 1, y0, -h + 1), (c - 2, c + 2, c - 2), key=f"{key}_tall")
    return [f"{key}_core", f"{key}_wide", f"{key}_deep", f"{key}_tall"]
