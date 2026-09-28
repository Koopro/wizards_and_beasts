#!/usr/bin/env python3
"""Shared reaction and idle clips for every rig built on `rigkit`.

`GenericBeastEntity` already asks for these beats: `hit`/`flinch` on taking damage,
`death`/`die`/`collapse` from `die()`, and the idle actions its `IdleProfile` names
(`graze`, `preen`, `coil`, ...) through `CreatureIdleGoal`. Each is gated on the definition's
declared clip list, so until a rig ships the clip the hook does nothing — which is where the whole
roster sat. This module is the art side of those hooks: it reads a rig's skeleton, works out which
bone is the torso, the head, the tail, the legs and the wings from the names the generators
already use, and writes each beat from those roles. A generator that authors its own `hit` or
`graze` keeps it; this only fills what is missing.

Every clip here is a **delta over the rest pose** (GeckoLib adds keyframes to the bone's authored
rotation), keys only the bones it means to move, and is sized to its hook:

  - `hit`   0.4 s. A recoil: torso back, head up, tail lashes, wings flare, forelimbs brace.
  - `death` 1.2 s, held on its last frame. Vanilla already rolls the body onto its side over the
    same second, so the clip's job is the limbs — legs fold, head and neck go slack, the jaw drops,
    wings sag, the tail droops, a serpent loses its line. Held, so the body stays down until the
    entity is removed instead of snapping back to the idle pose mid-fall.
  - idle actions, 1-3 s, one or two per body plan, only the ones this skeleton can perform.

Sign rules are `rigkit`'s: positive X leans an up-pointing bone forward, nods a forward-pointing
head down, swings a hanging limb back and lifts a back-pointing tail; positive Z lifts a left
(+x) wing and lowers a right one.
"""

import re

from rigkit import clip, kf

TORSO = ("body", "body_main", "torso", "chest", "spine", "abdomen", "thorax", "barrel", "trunk",
         "mantle", "cloak", "blob", "shell", "cap")
FORE = ("foreleg", "arm", "forearm", "front_leg", "leg_front", "leg_fl", "leg_fr")
HIND = ("hindleg", "leg_rear", "hind_leg")


class Skeleton:
    """The roles a rig's bones play, read from their names and positions."""

    def __init__(self, bones):
        # bones: iterable of (name, parent, pivot)
        self.parent = {n: p for n, p, _ in bones}
        self.pivot = {n: tuple(v) for n, _, v in bones}
        self.names = [n for n, _, _ in bones]
        names = set(self.names)

        self.torso = next((n for n in TORSO if n in names), None)
        if self.torso is None:
            kids = [n for n in self.names if self.parent.get(n) == "root"]
            self.torso = kids[0] if kids else None
        self.heads = [n for n in self.names if n == "head" or re.match(r"head_", n)
                      and not re.search(r"(horn|crest|fringe|tuft|ear|eye|jaw)", n)]
        self.necks = [n for n in self.names if n.startswith("neck")]
        self.jaws = [n for n in self.names if n in ("jaw", "beak_lower", "lower_jaw", "mandible")
                     or n.startswith("jaw_")]
        self.tails = self._chains(lambda n: n.startswith("tail") and "barb" not in n)
        self.segments = self._chains(lambda n: re.match(r"(seg|coil|body_seg)", n) is not None)
        self.wings = [n for n in self.names if re.match(r"wing_(left|right|l|r)$", n)
                      or re.match(r"wing_(left|right)_(root|arm)$", n)]
        self.wing_tips = [n for n in self.names if n.startswith("wing") and n not in self.wings
                          and self.parent.get(n, "").startswith("wing")]
        self.limbs = self._limbs()
        self.danglers = [n for n in self.names if re.match(r"(ear|tendril|antenna|tentacle|whisker)", n)]

    # -- structure --------------------------------------------------------------

    def _chains(self, test):
        """Members in parent-before-child order, each with its depth in its own chain."""
        out = []
        for n in self.names:
            if not test(n):
                continue
            depth = 0
            p = self.parent.get(n)
            while p and test(p):
                depth += 1
                p = self.parent.get(p)
            out.append((n, depth))
        return out

    def side(self, name):
        if re.search(r"(_left|_l)(_|$)", name):
            return 1
        if re.search(r"(_right|_r)(_|$)", name):
            return -1
        x = self.pivot.get(name, (0, 0, 0))[0]
        return 1 if x > 0.5 else -1 if x < -0.5 else 0

    def _limbs(self):
        """Top segment of every leg or arm, and its child segments, with a role."""
        limbs = []
        for n in self.names:
            if re.match(r"(wing|tail|neck|head|seg|coil|ear|horn|tendril)", n):
                continue
            is_leg = re.match(r"(foreleg|hindleg|leg|arm|forearm|front_leg|hind_leg)", n)
            if not is_leg:
                continue
            parent = self.parent.get(n, "")
            if re.match(r"(foreleg|hindleg|leg|arm|forearm|front_leg|hind_leg)", parent):
                continue  # a lower segment; collected under its top
            chain = []
            cur = [n]
            while cur:
                kids = [k for k in self.names if self.parent.get(k) in cur and k not in chain and k != n]
                kids = [k for k in kids if not re.match(r"(wing|tail)", k)]
                chain.extend(kids)
                cur = kids
            if n.startswith(("arm", "forearm")):
                role = "arm"
            elif n.startswith(FORE):
                role = "fore"
            elif n.startswith(HIND):
                role = "hind"
            elif re.match(r"leg_\d|leg_[a-d]_", n):
                role = "splay"
            else:
                role = "leg"
            limbs.append((n, role, chain))
        return limbs

    def usable(self):
        return self.torso is not None


# ---------------------------------------------------------------------------- helpers


def _rot(*frames):
    return {"rotation": kf(*frames)}


def _pulse(peak_t, end_t, value):
    return ((0.0, (0, 0, 0)), (peak_t, value), (end_t, (0, 0, 0)))


# ---------------------------------------------------------------------------- hit


def hit(sk):
    b = {}
    if sk.torso:
        b[sk.torso] = {"rotation": kf(*_pulse(0.08, 0.4, (-7, 0, 0))),
                       "position": kf((0.0, (0, 0, 0)), (0.08, (0, 0.3, 0.8)), (0.4, (0, 0, 0)))}
    for n in sk.necks[:1]:
        b[n] = _rot(*_pulse(0.1, 0.4, (-8, 0, 0)))
    for n in sk.heads:
        b[n] = _rot((0.0, (0, 0, 0)), (0.1, (-18, 0, 0)), (0.22, (-6, 0, 0)), (0.4, (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot(*_pulse(0.08, 0.36, (22, 0, 0)))
    for n, depth in sk.tails:
        amp = 18 if depth == 0 else 10
        t = 0.1 + 0.05 * depth
        b[n] = _rot((0.0, (0, 0, 0)), (t, (amp, 0, 0)), (t + 0.12, (-amp * 0.4, 0, 0)), (0.4, (0, 0, 0)))
    for n, depth in sk.segments:
        sign = 1 if depth % 2 == 0 else -1
        b[n] = _rot((0.0, (0, 0, 0)), (0.1, (0, 8 * sign, 0)), (0.22, (0, -4 * sign, 0)),
                    (0.4, (0, 0, 0)))
    for n in sk.wings:
        s = sk.side(n)
        b[n] = _rot(*_pulse(0.1, 0.4, (0, 0, 26 * s)))
    for n, role, _ in sk.limbs:
        if role in ("fore", "arm"):
            b[n] = _rot(*_pulse(0.1, 0.4, (-14, 0, 8 * sk.side(n) if role == "arm" else 0)))
        elif role == "splay":
            b[n] = _rot(*_pulse(0.1, 0.4, (0, 0, 10 * sk.side(n))))
    for n in sk.danglers:
        b[n] = _rot(*_pulse(0.1, 0.4, (-14, 0, 0)))
    return clip(0.4, b, loop=False)


# ---------------------------------------------------------------------------- attack


def attack(sk):
    """A lunge: gather back, then the torso drives forward, head and forelimbs leading."""
    b = {}
    if sk.torso:
        b[sk.torso] = {"rotation": kf((0.0, (0, 0, 0)), (0.15, (-6, 0, 0)), (0.3, (8, 0, 0)), (0.6, (0, 0, 0))),
                       "position": kf((0.0, (0, 0, 0)), (0.15, (0, 0, 1.0)), (0.3, (0, 0, -1.8)),
                                      (0.6, (0, 0, 0)))}
    for n in sk.necks[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.15, (-10, 0, 0)), (0.3, (14, 0, 0)), (0.6, (0, 0, 0)))
    for n in sk.heads:
        b[n] = _rot((0.0, (0, 0, 0)), (0.15, (-12, 0, 0)), (0.3, (12, 0, 0)), (0.6, (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot((0.0, (0, 0, 0)), (0.18, (26, 0, 0)), (0.32, (0, 0, 0)), (0.6, (0, 0, 0)))
    for n, role, _ in sk.limbs:
        if role in ("fore", "arm"):
            s = sk.side(n)
            b[n] = _rot((0.0, (0, 0, 0)), (0.15, (-40 if role == "arm" else -24, 0, 6 * s)),
                        (0.3, (-80 if role == "arm" else 10, 0, 0)), (0.6, (0, 0, 0)))
    for n in sk.wings:
        b[n] = _rot(*_pulse(0.2, 0.6, (0, 0, 20 * sk.side(n))))
    return clip(0.6, b, loop=False)


# ---------------------------------------------------------------------------- death


def death(sk):
    b = {}
    hold = 1.2

    def settle(value, *, recoil=None, at=0.6):
        frames = [(0.0, (0, 0, 0))]
        if recoil:
            frames.append((0.15, recoil))
        frames += [(at, value), (hold, value)]
        return _rot(*frames)

    if sk.torso:
        b[sk.torso] = settle((6, 0, 0), recoil=(-10, 0, 0))
        b[sk.torso]["position"] = kf((0.0, (0, 0, 0)), (0.15, (0, 0.4, 0)), (0.6, (0, -1.0, 0)),
                                     (hold, (0, -1.0, 0)))
    for n in sk.necks:
        b[n] = settle((24, 0, 0), recoil=(-10, 0, 0), at=0.7)
    for n in sk.heads:
        b[n] = settle((32, 0, 0), recoil=(-22, 0, 0), at=0.75)
    for n in sk.jaws:
        b[n] = settle((18, 0, 0), recoil=(26, 0, 0), at=0.8)
    for n, depth in sk.tails:
        b[n] = settle((-14, 0, 6 if depth % 2 else -6), recoil=(20 if depth == 0 else 0, 0, 0),
                      at=0.7 + 0.05 * depth)
    for n, depth in sk.segments:
        sign = 1 if depth % 2 == 0 else -1
        b[n] = settle((0, 16 * sign, 0), recoil=(0, -6 * sign, 0), at=0.8)
    for n in sk.wings:
        s = sk.side(n)
        b[n] = settle((0, 0, -34 * s), recoil=(0, 0, 30 * s), at=0.8)
    for n in sk.wing_tips:
        s = sk.side(n)
        b[n] = settle((0, 0, -20 * s), at=0.85)
    for n, role, chain in sk.limbs:
        s = sk.side(n)
        if role == "fore":
            top, low = (-30, 0, 0), (48, 0, 0)
        elif role == "hind":
            top, low = (28, 0, 0), (-36, 0, 0)
        elif role == "arm":
            top, low = (-18, 0, 22 * s), (-28, 0, 0)
        elif role == "splay":
            top, low = (0, 0, 24 * s), (0, 0, 30 * s)
        else:
            top, low = (-16 if s >= 0 else 10, 0, 0), (32, 0, 0)
        b[n] = settle(top, at=0.55)
        for k in chain[:2]:
            b[k] = settle(low, at=0.6)
    for n in sk.danglers:
        b[n] = settle((18, 0, 0), at=0.8)
    c = clip(hold, b, loop=False)
    c["loop"] = "hold_on_last_frame"
    return c


# ---------------------------------------------------------------------------- idle actions


def _graze(sk):
    if not sk.heads:
        return None
    b = {}
    t = (0.0, 0.6, 1.1, 1.5, 1.9, 2.4, 3.0)
    for n in sk.necks[:1]:
        b[n] = _rot((t[0], (0, 0, 0)), (t[1], (38, 0, 0)), (t[5], (38, 0, 0)), (t[6], (0, 0, 0)))
    head_down = 26 if sk.necks else 44
    for n in sk.heads[:1]:
        b[n] = _rot((t[0], (0, 0, 0)), (t[1], (head_down, 0, 0)), (t[2], (head_down + 6, 4, 0)),
                    (t[3], (head_down, -3, 0)), (t[4], (head_down + 6, 0, 0)),
                    (t[5], (head_down, 0, 0)), (t[6], (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot((0.0, (0, 0, 0)), (1.0, (10, 0, 0)), (1.2, (0, 0, 0)), (1.6, (10, 0, 0)),
                    (1.8, (0, 0, 0)))
    if not sk.necks and sk.torso:
        b[sk.torso] = _rot((0.0, (0, 0, 0)), (0.6, (8, 0, 0)), (2.4, (8, 0, 0)), (3.0, (0, 0, 0)))
    return clip(3.0, b, loop=False)


def _sniff(sk):
    if not sk.heads:
        return None
    b = {}
    for n in sk.heads[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.2, (10, -14, 0)), (0.35, (6, -14, 0)), (0.5, (10, -14, 0)),
                    (0.8, (8, 12, 0)), (0.95, (4, 12, 0)), (1.1, (8, 12, 0)), (1.6, (0, 0, 0)))
    for n in sk.necks[:1]:
        b[n] = _rot(*_pulse(0.3, 1.6, (8, 0, 0)))
    return clip(1.6, b, loop=False)


def _shake(sk):
    if not sk.torso:
        return None
    steps = [(0.0, 0)] + [(0.1 * (i + 1), (10 if i % 2 == 0 else -10) * (1 - i / 9)) for i in range(8)]
    steps.append((1.0, 0))
    b = {sk.torso: _rot(*[(t, (0, 0, v)) for t, v in steps])}
    for n in sk.heads[:1]:
        b[n] = _rot(*[(t, (0, v * 1.4, -v)) for t, v in steps])
    for n in sk.danglers:
        b[n] = _rot(*[(t, (0, 0, v * 2)) for t, v in steps])
    for n in sk.wings:
        s = sk.side(n)
        b[n] = _rot((0.0, (0, 0, 0)), (0.15, (0, 0, 14 * s)), (0.8, (0, 0, 14 * s)), (1.0, (0, 0, 0)))
    return clip(1.0, b, loop=False)


def _stretch(sk):
    b = {}
    if sk.wings:
        for n in sk.wings:
            s = sk.side(n)
            b[n] = _rot((0.0, (0, 0, 0)), (0.7, (0, 0, 44 * s)), (1.7, (0, 0, 40 * s)), (2.4, (0, 0, 0)))
        for n in sk.wing_tips:
            s = sk.side(n)
            b[n] = _rot((0.0, (0, 0, 0)), (0.8, (0, 0, 24 * s)), (1.7, (0, 0, 22 * s)), (2.4, (0, 0, 0)))
    arms = [n for n, role, _ in sk.limbs if role == "arm"]
    if arms and not sk.wings:
        for n in arms:
            s = sk.side(n)
            b[n] = _rot((0.0, (0, 0, 0)), (0.8, (-150, 0, -12 * s)), (1.6, (-156, 0, -10 * s)),
                        (2.4, (0, 0, 0)))
    if not b:
        return None
    if sk.torso:
        b[sk.torso] = _rot(*_pulse(0.8, 2.4, (-6, 0, 0)))
    for n in sk.heads[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.8, (-16, 0, 0)), (1.6, (-18, 0, 0)), (2.4, (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot((0.0, (0, 0, 0)), (0.9, (24, 0, 0)), (1.5, (24, 0, 0)), (2.0, (0, 0, 0)))
    return clip(2.4, b, loop=False)


def _preen(sk):
    if not sk.heads:
        return None
    b = {}
    for n in sk.necks[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.5, (10, 36, 0)), (1.9, (10, 36, 0)), (2.4, (0, 0, 0)))
    turn = 60 if sk.necks else 90
    for n in sk.heads[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.5, (24, turn, 0)), (0.8, (32, turn, 0)), (1.1, (22, turn, 0)),
                    (1.4, (32, turn, 0)), (1.9, (24, turn, 0)), (2.4, (0, 0, 0)))
    for n in sk.wings:
        if sk.side(n) > 0:
            b[n] = _rot((0.0, (0, 0, 0)), (0.5, (0, 0, 12)), (1.9, (0, 0, 12)), (2.4, (0, 0, 0)))
    return clip(2.4, b, loop=False)


def _ruffle(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"scale": kf((0.0, (1, 1, 1)), (0.15, (1.08, 1.1, 1.04)), (0.3, (1, 1, 1)),
                                (0.45, (1.05, 1.06, 1.02)), (0.7, (1, 1, 1)))}}
    for n in sk.wings:
        s = sk.side(n)
        b[n] = _rot((0.0, (0, 0, 0)), (0.12, (0, 0, 10 * s)), (0.24, (0, 0, 2 * s)),
                    (0.36, (0, 0, 10 * s)), (0.7, (0, 0, 0)))
    return clip(0.7, b, loop=False)


def _peek(sk):
    if not sk.heads:
        return None
    b = {}
    for n in sk.heads[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.4, (4, 48, 0)), (1.2, (4, 48, 0)), (1.6, (6, -40, 0)),
                    (2.3, (6, -40, 0)), (2.8, (0, 0, 0)))
    if sk.torso:
        b[sk.torso] = _rot((0.0, (0, 0, 0)), (0.4, (0, 10, 4)), (1.2, (0, 10, 4)), (1.6, (0, -8, -4)),
                           (2.3, (0, -8, -4)), (2.8, (0, 0, 0)))
    return clip(2.8, b, loop=False)


def _grunt(sk):
    if not sk.torso:
        return None
    b = {sk.torso: _rot((0.0, (0, 0, 0)), (0.25, (10, 0, 0)), (0.5, (-4, 0, 0)), (1.0, (0, 0, 0)))}
    for n in sk.heads[:1]:
        b[n] = _rot((0.0, (0, 0, 0)), (0.25, (-14, 0, 0)), (0.6, (4, 0, 0)), (1.0, (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot(*_pulse(0.25, 0.8, (18, 0, 0)))
    for n, role, _ in sk.limbs:
        if role == "arm":
            b[n] = _rot(*_pulse(0.25, 1.0, (-10, 0, 14 * sk.side(n))))
    return clip(1.0, b, loop=False)


def _taste_air(sk):
    if not sk.heads:
        return None
    b = {}
    for n in sk.heads:
        b[n] = _rot((0.0, (0, 0, 0)), (0.4, (-22, 0, 0)), (1.4, (-22, 0, 0)), (1.8, (0, 0, 0)))
    for n in sk.jaws:
        b[n] = _rot((0.0, (0, 0, 0)), (0.5, (14, 0, 0)), (0.6, (0, 0, 0)), (0.8, (14, 0, 0)),
                    (0.9, (0, 0, 0)), (1.1, (14, 0, 0)), (1.2, (0, 0, 0)))
    for n in [n for n in sk.names if n.startswith("tongue")]:
        b[n] = {"position": kf((0.0, (0, 0, 0)), (0.5, (0, 0, -1.5)), (0.6, (0, 0, 0)),
                               (0.8, (0, 0, -1.5)), (0.9, (0, 0, 0)))}
    for n, depth in sk.segments[:2]:
        b[n] = _rot(*_pulse(0.4, 1.8, (-8, 0, 0)))
    return clip(1.8, b, loop=False)


def _coil(sk):
    chain = sk.segments or sk.tails
    if len(chain) < 2:
        return None
    b = {}
    for n, depth in chain:
        sign = 1 if depth % 2 == 0 else -1
        b[n] = _rot((0.0, (0, 0, 0)), (1.0, (0, 14 * sign, 0)), (2.0, (0, 14 * sign, 0)),
                    (3.0, (0, 0, 0)))
    return clip(3.0, b, loop=False)


def _groom(sk):
    fronts = [(n, chain) for n, role, chain in sk.limbs if role in ("splay", "fore", "arm")]
    fronts = [f for f in fronts if re.search(r"(_0_|_a_|foreleg|arm)", f[0])][:2]
    if not fronts:
        return None
    b = {}
    for i, (n, chain) in enumerate(fronts):
        s = sk.side(n)
        off = 0.2 * i
        b[n] = _rot((0.0, (0, 0, 0)), (0.3 + off, (-30, 0, -10 * s)), (0.6 + off, (-20, 0, -14 * s)),
                    (0.9 + off, (-30, 0, -10 * s)), (1.6, (0, 0, 0)))
    for n in sk.heads[:1]:
        b[n] = _rot(*_pulse(0.4, 1.6, (12, 0, 0)))
    return clip(1.6, b, loop=False)


def _skitter(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"position": kf((0.0, (0, 0, 0)), (0.15, (1.2, 0, -0.6)), (0.3, (1.2, 0, -0.6)),
                                   (0.45, (-1.0, 0, 0.4)), (0.6, (-1.0, 0, 0.4)), (0.9, (0, 0, 0)))}}
    for n, role, _ in sk.limbs:
        if role == "splay":
            b[n] = _rot((0.0, (0, 0, 0)), (0.1, (0, 12, 0)), (0.2, (0, -12, 0)), (0.3, (0, 12, 0)),
                        (0.45, (0, -12, 0)), (0.6, (0, 12, 0)), (0.9, (0, 0, 0)))
    return clip(0.9, b, loop=False)


def _hover(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"position": kf((0.0, (0, 0, 0)), (0.5, (0, 1.5, 0)), (1.0, (0, 0.5, 0)),
                                   (1.5, (0, 1.8, 0)), (2.0, (0, 0, 0)))}}
    return clip(2.0, b, loop=False)


def _flit(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"position": kf((0.0, (0, 0, 0)), (0.12, (2.5, 1, 0)), (0.3, (2.5, 1, 0)),
                                   (0.42, (-2, 0.5, -1)), (0.6, (-2, 0.5, -1)), (0.8, (0, 0, 0)))}}
    return clip(0.8, b, loop=False)


def _squirm(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"scale": kf((0.0, (1, 1, 1)), (0.25, (1.12, 0.88, 1.12)), (0.5, (0.92, 1.1, 0.92)),
                                (0.75, (1.08, 0.92, 1.08)), (1.0, (0.96, 1.05, 0.96)), (1.3, (1, 1, 1))),
                    "rotation": kf((0.0, (0, 0, 0)), (0.4, (0, 0, 6)), (0.8, (0, 0, -6)), (1.3, (0, 0, 0)))}}
    return clip(1.3, b, loop=False)


def _settle(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"scale": kf((0.0, (1, 1, 1)), (0.4, (1.1, 0.86, 1.1)), (1.6, (1.08, 0.9, 1.08)),
                                (2.0, (1, 1, 1))),
                    "position": kf((0.0, (0, 0, 0)), (0.4, (0, -0.6, 0)), (1.6, (0, -0.5, 0)),
                                   (2.0, (0, 0, 0)))}}
    return clip(2.0, b, loop=False)


def _roll(sk):
    if not sk.torso:
        return None
    b = {sk.torso: _rot((0.0, (0, 0, 0)), (0.8, (0, 0, 28)), (1.6, (0, 0, -24)), (2.4, (0, 0, 0)))}
    return clip(2.4, b, loop=False)


def _drift(sk):
    if not sk.torso:
        return None
    b = {sk.torso: {"position": kf((0.0, (0, 0, 0)), (1.2, (0, 1.2, 0)), (2.4, (0, 0, 0))),
                    "rotation": kf((0.0, (0, 0, 0)), (1.2, (-6, 0, 0)), (2.4, (0, 0, 0)))}}
    for n, depth in sk.tails:
        b[n] = _rot((0.0, (0, 0, 0)), (0.6, (0, 12, 0)), (1.2, (0, -12, 0)), (1.8, (0, 12, 0)),
                    (2.4, (0, 0, 0)))
    return clip(2.4, b, loop=False)


IDLE_BUILDERS = {
    "graze": _graze, "sniff": _sniff, "shake": _shake, "stretch": _stretch, "preen": _preen,
    "ruffle": _ruffle, "peek": _peek, "grunt": _grunt, "taste_air": _taste_air, "coil": _coil,
    "groom": _groom, "skitter": _skitter, "hover": _hover, "flit": _flit, "squirm": _squirm,
    "settle": _settle, "roll": _roll, "drift": _drift,
}

# `IdleProfile.forBodyPlan` — kept in step by `reactions_test` in `entity_art_inventory.py`.
IDLE_DEFAULT = {
    "AVIAN": ["preen", "ruffle", "stretch"],
    "INSECTOID_FLYER": ["hover", "flit"],
    "WINGED_QUADRUPED": ["stretch", "shake", "graze"],
    "QUADRUPED": ["graze", "sniff", "shake", "scratch"],
    "SERPENTINE": ["coil", "taste_air"],
    "ARTHROPOD_MULTILEG": ["groom", "skitter"],
    "BIPED_HUMANOID": ["peek", "scratch"],
    "LARGE_HUMANOID": ["stretch", "grunt"],
    "AQUATIC": ["drift", "roll"],
    "BLOB_SPHERE": ["squirm", "settle"],
}


def fill(bones, clips, *, idle_actions=(), reactions=True, fights=False):
    """Add every missing shared clip this skeleton can perform. Returns the names added.

    `bones` is `(name, parent, pivot)` triples; `clips` is the generator's own clip dict, which
    wins wherever it already has a beat (either name of a synonym pair counts).
    """
    sk = Skeleton(bones)
    if not sk.usable():
        return []
    added = []
    if reactions:
        if not any(k in clips for k in ("hit", "flinch")):
            clips["hit"] = hit(sk)
            added.append("hit")
        if not any(k in clips for k in ("death", "die", "collapse")):
            clips["death"] = death(sk)
            added.append("death")
    if fights and not any(k in clips for k in ("attack", "strike", "bite", "lunge")):
        clips["attack"] = attack(sk)
        added.append("attack")
    for name in idle_actions:
        if name in clips or name not in IDLE_BUILDERS:
            continue
        built = IDLE_BUILDERS[name](sk)
        if built and built["bones"]:
            clips[name] = built
            added.append(name)
    return added
