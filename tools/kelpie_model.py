#!/usr/bin/env python3
"""The Kelpie, and the horse it pretends to be: two rigs, one skeleton, one animation file, three coats.

Canon (Fantastic Beasts): a British and Irish water demon that can take various shapes but most often appears as a
horse with bulrushes for a mane; it lures the unwary onto its back, dives to the bottom of its river or loch and
devours them; a Placement Charm puts a bridle on it and makes it docile.

The read: at a glance a dark horse standing by the water (the guise, `kelpie_disguise`); revealed (`kelpie`), the
same horse soaked black with a wet blue-green sheen, a heavy mane of weed hanging off one side of the neck, a long
dripping tail, pale dead eyes, and a jaw that opens far wider than a horse's should.

**The two rigs must share every bone.** `DisguisableBeastGeoModel` swaps only the model and the texture; the
animation resolves from `kelpie`, so the guise is animated by the Kelpie's clips. Both are built by `horse()`, and the
true form only adds cubes (weed, teeth) to bones the guise also has. `emit` validates both against the clips.

**No glowmask on either** (a glowing true form would look for `kelpie_disguise_glowmask.png` while disguised; a
missing emissive texture renders magenta). The pale eyes are paint, not light.

Coats (true form): black (default, `kelpie.png`), blue-black and green-black (`textures/entity/kelpie/<coat>.png`).

Clips — movement: idle, walk, run, swim, float, dive, surface, lure, threaten, drown; one-shots: bite, grab, emerge,
submerge, call, hit, death.

Run from the repo root:  python tools/kelpie_model.py [--force]
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigkit  # noqa: E402
from artgen_common import hx, marker, posterise, save  # noqa: E402
from bodies import chain, ground, knee_tracks, quadruped, quadruped_loops  # noqa: E402
from rigkit import Rig, Skin, clip, kf, osc  # noqa: E402

CID = "kelpie"
DISGUISE = "kelpie_disguise"
TOOL = "kelpie_model.py"
HITBOX = (1.4, 1.7)

COATS = {
    "black": dict(body="#121416", sheen="#1E3236", wet="#4A6E70", mane="#0B110F", weed="#2C4628"),
    "blue_black": dict(body="#11161E", sheen="#1C2C3E", wet="#4A6484", mane="#0A0E14", weed="#26403A"),
    "green_black": dict(body="#121711", sheen="#1E3022", wet="#4A6A4C", mane="#0B100A", weed="#344A22"),
}
DEFAULT_COAT = "black"
MANE_SECTIONS = 3


def horse(rig, *, true_form):
    a = quadruped(
        rig, hip=15,
        chest=(9, 10, 6), barrel=(8, 9, 11), croup=(8, 9, 6), croup_drop=-4,
        neck=(5, 10, 5), neck_rake=34, head=(5, 6, 8), head_pitch=12, muzzle=(4, 3, 3),
        fore=[("", 7, 3, 4, None), ("cannon", 6, 2, 2, None), ("hoof", 2, 3, 3, None)],
        hind=[("", 7, 4, 5, (10, 0, 0)), ("hock", 6, 2, 3, (-14, 0, 0)), ("hoof", 2, 3, 3, None)],
        fore_x=3, hind_x=3)
    hy, hz, hd = a.head_y, a.head_front, a.head_d
    # The jaw: its own bone, so it can open far wider than a horse's does.
    rig.bone("jaw", "head", (0, hy + 1, hz + hd - 2))
    rig.cube("jaw", (-1.5, hy - 1, hz - 3), (3, 2, 9), key="jaw")
    if true_form:
        rig.cube("jaw", (-1.5, hy + 0.5, hz - 3), (3, 1, 1), key="teeth", inflate=0.05)

    def ear(side, sign):
        rig.bone(f"ear_{side}", "head", (sign * 1.5, hy + 6, hz + hd - 2), (-10, 0, sign * 10))
        rig.cube(f"ear_{side}", Rig.mirror((1, hy + 6, hz + hd - 3), (1, 2, 1), sign), (1, 2, 1), key=f"ear_{side}")

    rig.pair(ear)
    rig.cube("head", (-1.5, hy + 5, hz + hd - 3), (3, 2, 1), key="forelock")

    # The mane: heavy sections hanging off one side of the neck, each its own bone so it swings and trails.
    neck_base = a.belly + 10 - 2
    for i in range(MANE_SECTIONS):
        top = neck_base + 10 - i * 3.3
        rig.bone(f"mane_{i + 1}", "neck", (2.5, top, a.front + 3.5))
        rig.cube(f"mane_{i + 1}", (2.5, top - 5, a.front + 2), (1, 5, 3), key=f"mane_{i + 1}")
        if true_form:
            rig.cube(f"mane_{i + 1}", (2.7, top - 7, a.front + 2.5 + (i % 2)), (1, 2, 1), key=f"weed_{i + 1}")

    a["tail"] = chain(rig, "tail", "croup", start=(0, a.belly + 7, a.back - 1), direction=1,
                      segments=[(3, 3, 3), (7, 3, 3), (7, 2, 2)], rotations=[(-55, 0, 0), (-18, 0, 0), (-10, 0, 0)])
    if true_form:
        rig.cube(a.tail[-1], (-1.5, a.belly - 6, a.back + 12), (3, 4, 1), key="weed_tail")
    ground(rig)
    a["rig"] = rig
    return a


def wet_streaks(skin, keys, colour, seed, density=2):
    """Short vertical highlights down the sides: water running off a coat, not noise."""
    d = skin.d
    for key in keys:
        f = skin.rig.faces(key)
        for name in ("east", "west", "north", "south", "top"):
            x0, y0, w, h = f[name]
            for xx in range(0, w, 3):
                n = rigkit._hash(x0 + xx, y0, seed) % 5
                if n < density:
                    start = rigkit._hash(x0 + xx, y0 + 1, seed) % max(1, h // 2)
                    d.line([(x0 + xx, y0 + start), (x0 + xx, y0 + min(h - 1, start + 2))], fill=colour)


def paint_true(rig, tex_h, coat=DEFAULT_COAT):
    c = {k: hx(v) for k, v in COATS[coat].items()}
    skin = Skin(rig, tex_h, grain="speckle")
    d = skin.d
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "neck", "head", "muzzle", "jaw", "ear_left", "ear_right"] + \
        [k for k in legs if "hoof" not in k]
    skin.skin(body, c["body"], top=c["sheen"], bevel=0.9, dither=0.0)
    wet_streaks(skin, body, c["sheen"], 3)
    wet_streaks(skin, ["chest", "barrel", "croup", "neck"], c["wet"], 7, density=1)
    skin.skin([k for k in legs if "hoof" in k], hx("#070909"), dither=0.0)
    mane = rig.keys("mane_", "tail", "forelock")
    skin.skin(mane, c["mane"], bevel=0.9, dither=0.0)
    skin.bands(mane, c["sheen"], step=3)
    skin.skin(rig.keys("weed_"), c["weed"], bevel=0.9, dither=0.0)
    skin.tip(rig.keys("weed_"), c["mane"], rows=1)
    # The mouth: dark red inside, a row of teeth along the jaw.
    x0, y0, w, h = rig.faces("jaw")["top"]
    d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=hx("#3A1C20"))
    skin.skin(["teeth"], hx("#C8C2AC"), bevel=0.0, dither=0.0)
    # Pale, dead eyes.
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, hx("#A7BDB4"))
        skin.mark("head", face, 3, 1, hx("#5E7672"))
    return skin


def paint_guise(rig, tex_h):
    skin = Skin(rig, tex_h, grain="speckle")
    legs = rig.keys("foreleg", "hindleg")
    body = ["chest", "barrel", "croup", "neck", "head", "muzzle", "jaw", "ear_left", "ear_right"] + \
        [k for k in legs if "hoof" not in k]
    skin.skin(body, hx("#241B17"), top=hx("#33271F"), bevel=0.9, dither=0.03, dither_colour=hx("#1A130F"))
    skin.skin([k for k in legs if "hoof" in k], hx("#141010"), dither=0.0)
    mane = rig.keys("mane_", "tail", "forelock")
    skin.skin(mane, hx("#110C0A"), bevel=0.9, dither=0.0)
    skin.bands(mane, hx("#221812"), step=3)
    for face in ("east", "west"):
        skin.mark("head", face, 2, 1, hx("#0E0907"))
    return skin


# ── animation ────────────────────────────────────────────────────────────────


def rot(*frames):
    return {"rotation": kf(*frames)}


def mane_trail(length, amp, lag=0.12, base=0.0):
    out = {}
    for i in range(MANE_SECTIONS):
        out[f"mane_{i + 1}"] = osc(length, amp * (1 + 0.3 * i), axis="x", phase=lag * i, base=base)
    return out


def anims(a):
    rig = a.rig
    tail = a["tail"]
    idle, walk, legs = quadruped_loops(a, idle_len=4.0)
    idle.update(mane_trail(4.0, 3.0))
    walk.update(mane_trail(0.9, 5.0))
    clips = {"idle": clip(4.0, idle), "walk": clip(0.9, walk)}

    # run: a gallop — legs paired, a long stride, the body rocking.
    R = 0.55
    run = {"foreleg_left": osc(R, 50, phase=0.0), "foreleg_right": osc(R, 50, phase=0.08),
           "hindleg_left": osc(R, 46, phase=0.5), "hindleg_right": osc(R, 46, phase=0.58),
           **knee_tracks(rig, legs, R, 34.0),
           "body": osc(R, 5.0, phase=0.25), "neck": osc(R, 8.0, phase=0.4),
           **{name: osc(R, 10.0, axis="x", base=-20 + 8 * i) for i, name in enumerate(tail)}}
    run.update(mane_trail(R, 10.0, base=-12))
    clips["run"] = clip(R, run)

    # swim: the body undulates, the legs stroke long and slow, mane and tail stream behind.
    S = 1.2
    swim = {**{name: osc(S, 38.0, phase=phase) for name, phase in legs.items()},
            **knee_tracks(rig, legs, S, 24.0),
            "body": osc(S, 4.0, phase=0.0), "neck": osc(S, 6.0, phase=0.25, base=16),
            "head": rot((0, (-10, 0, 0)), (S, (-10, 0, 0))),
            **{name: osc(S, 8.0, axis="x", phase=0.1 * i, base=-40 + 6 * i) for i, name in enumerate(tail)}}
    swim.update(mane_trail(S, 6.0, base=-35))
    clips["swim"] = clip(S, swim)

    # float: idle in water — a slow drift, legs treading.
    F = 3.0
    flt = {**{name: osc(F, 14.0, phase=phase) for name, phase in legs.items()},
           "root": {"position": kf((0, (0, 0, 0)), (F / 2, (0, 0.6, 0)), (F, (0, 0, 0)))},
           "neck": osc(F, 3.0, base=10), **{name: osc(F, 6.0, axis="y", phase=0.2 * i) for i, name in enumerate(tail)}}
    flt.update(mane_trail(F, 8.0, base=-20))
    clips["float"] = clip(F, flt)

    # dive / surface: angled down or up through the water, a stronger stroke.
    for name, pitch in (("dive", 28), ("surface", -24)):
        L = 1.0
        body = {**{n: osc(L, 44.0, phase=phase) for n, phase in legs.items()},
                "body": rot((0, (pitch, 0, 0)), (L, (pitch, 0, 0))),
                "neck": rot((0, (pitch * 0.5, 0, 0)), (L, (pitch * 0.5, 0, 0)))}
        body.update(mane_trail(L, 6.0, base=-40))
        clips[name] = clip(L, body)

    # lure: the gentlest horse there is — head low as if grazing, ears soft, tail swishing.
    Lu = 5.0
    lure = {"neck": rot((0, (38, 0, 0)), (Lu / 2, (42, 6, 0)), (Lu, (38, 0, 0))),
            "head": rot((0, (16, 0, 0)), (Lu * 0.4, (18, 0, 0)), (Lu * 0.6, (12, 0, 0)), (Lu, (16, 0, 0))),
            "ear_left": rot((0, (0, 0, 0)), (2.0, (0, 0, 20)), (2.3, (0, 0, 0)), (Lu, (0, 0, 0))),
            **{name: osc(Lu, 10.0, axis="y", phase=0.1 * i) for i, name in enumerate(tail)}}
    lure.update(mane_trail(Lu, 3.0))
    clips["lure"] = clip(Lu, lure)

    # threaten: head low and forward, ears flat, jaw gaping, the body gathered.
    T = 2.0
    threaten = {"neck": rot((0, (20, 0, 0)), (T / 2, (24, 0, 0)), (T, (20, 0, 0))),
                "head": rot((0, (-6, 0, 0)), (T / 2, (-2, 5, 0)), (T, (-6, 0, 0))),
                "jaw": rot((0, (40, 0, 0)), (T / 2, (48, 0, 0)), (T, (40, 0, 0))),
                "ear_left": rot((0, (40, 0, 0)), (T, (40, 0, 0))),
                "ear_right": rot((0, (40, 0, 0)), (T, (40, 0, 0))),
                "body": rot((0, (4, 0, 0)), (T, (4, 0, 0)))}
    threaten.update(mane_trail(T, 4.0))
    clips["threaten"] = clip(T, threaten)

    # drown: the grip — it thrashes, head down, dragging whatever is on it under.
    D = 0.8
    drown = {**{name: osc(D, 40.0, phase=phase) for name, phase in legs.items()},
             "body": rot((0, (18, 0, -6)), (D / 2, (24, 0, 6)), (D, (18, 0, -6))),
             "neck": rot((0, (30, 0, 0)), (D / 2, (36, 10, 0)), (D, (30, 0, 0))),
             "jaw": rot((0, (30, 0, 0)), (D / 2, (10, 0, 0)), (D, (30, 0, 0))),
             **{name: osc(D, 14.0, axis="y", phase=0.1 * i) for i, name in enumerate(tail)}}
    drown.update(mane_trail(D, 12.0, base=-30))
    clips["drown"] = clip(D, drown)

    # bite: the jaw snaps.
    B = 0.5
    clips["bite"] = clip(B, {
        "neck": rot((0, (0, 0, 0)), (0.12, (-10, 0, 0)), (0.22, (16, 0, 0)), (B, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.12, (50, 0, 0)), (0.22, (0, 0, 0)), (B, (0, 0, 0))),
    }, loop=False)

    # grab: a lunge forward, jaw open, the head driving at its victim.
    G = 0.7
    clips["grab"] = clip(G, {
        "body": rot((0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.4, (8, 0, 0)), (G, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.2, (-18, 0, 0)), (0.4, (24, 0, 0)), (G, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.2, (55, 0, 0)), (0.42, (0, 0, 0)), (G, (0, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.2, (-60, 0, 0)), (G, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.25, (-60, 0, 0)), (G, (0, 0, 0))),
    }, loop=False)

    # emerge / submerge: out of the water rearing and shaking, or down into it nose first.
    E = 1.0
    clips["emerge"] = clip(E, {
        "body": rot((0, (0, 0, 0)), (0.3, (-26, 0, 0)), (0.7, (0, 0, 6)), (0.85, (0, 0, -6)), (E, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.7, (0, 14, 0)), (0.85, (0, -14, 0)), (E, (0, 0, 0))),
        "foreleg_left": rot((0, (0, 0, 0)), (0.3, (-70, 0, 0)), (E, (0, 0, 0))),
        "foreleg_right": rot((0, (0, 0, 0)), (0.3, (-60, 0, 0)), (E, (0, 0, 0))),
        **{f"mane_{i + 1}": rot((0, (0, 0, 0)), (0.75, (0, 0, 30)), (0.85, (0, 0, -30)), (E, (0, 0, 0)))
           for i in range(MANE_SECTIONS)},
    }, loop=False)
    clips["submerge"] = clip(E, {
        "body": rot((0, (0, 0, 0)), (0.5, (30, 0, 0)), (E, (34, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.5, (26, 0, 0)), (E, (30, 0, 0))),
        "root": {"position": kf((0, (0, 0, 0)), (E, (0, -6, 0)))},
    }, loop=False)

    # call: an ordinary, inviting whinny — the lure's voice.
    C = 1.4
    clips["call"] = clip(C, {
        "neck": rot((0, (0, 0, 0)), (0.3, (-26, 0, 0)), (1.0, (-20, 0, 0)), (C, (0, 0, 0))),
        "head": rot((0, (0, 0, 0)), (0.3, (14, 0, 0)), (C, (0, 0, 0))),
        "jaw": rot((0, (0, 0, 0)), (0.35, (14, 0, 0)), (0.9, (0, 0, 0)), (C, (0, 0, 0))),
        tail[-1]: rot((0, (0, 0, 0)), (0.4, (0, 30, 0)), (0.9, (0, -30, 0)), (C, (0, 0, 0))),
    }, loop=False)

    Hi = 0.4
    clips["hit"] = clip(Hi, {
        "body": rot((0, (0, 0, 0)), (0.1, (-8, 0, 6)), (Hi, (0, 0, 0))),
        "neck": rot((0, (0, 0, 0)), (0.1, (-16, 12, 0)), (Hi, (0, 0, 0))),
    }, loop=False)

    # death: it collapses onto its side, and the wet mane falls slack.
    Dd = 1.6
    death = {"body": rot((0, (0, 0, 0)), (0.6, (0, 0, 60)), (Dd, (0, 0, 90))),
             "root": {"position": kf((0, (0, 0, 0)), (Dd, (0, -4, 0)))},
             "neck": rot((0, (0, 0, 0)), (Dd, (40, 0, 20))),
             "jaw": rot((0, (0, 0, 0)), (Dd, (24, 0, 0)))}
    for name in legs:
        death[name] = rot((0, (0, 0, 0)), (Dd, (-20, 0, 0)))
    clips["death"] = dict(clip(Dd, death, loop=False), loop="hold_on_last_frame")
    return clips


# Played by KelpieEntity's own controllers; bite, call, hit and death are declared on the shared beast_action layer.
EXTRA = ("run", "float", "dive", "surface", "lure", "threaten", "drown", "grab", "emerge", "submerge")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    force = ap.parse_args().force

    true_rig = Rig(CID, 128)
    a = horse(true_rig, true_form=True)
    clips = anims(a)
    tex_h = rigkit.sheet_height(true_rig.pack())
    status = rigkit.emit(true_rig, paint_true(true_rig, tex_h), clips, cid=CID, tool=TOOL, hitbox=HITBOX,
                         tex_h=tex_h, force=force, colours=28, extra_clips=EXTRA, resize=True)
    if status:
        return status
    for coat in COATS:
        if coat != DEFAULT_COAT:
            save(posterise(paint_true(true_rig, tex_h, coat).img, 28),
                 os.path.join(rigkit.TEX_DIR, CID, coat + ".png"), marker(TOOL))

    guise = Rig(DISGUISE, 128)
    horse(guise, true_form=False)
    tex_h = rigkit.sheet_height(guise.pack())
    return rigkit.emit(guise, paint_guise(guise, tex_h), clips, cid=DISGUISE, tool=TOOL, hitbox=HITBOX,
                       tex_h=tex_h, force=force, colours=28, definition=False, write_animation=False,
                       extra_clips=EXTRA)


if __name__ == "__main__":
    sys.exit(main())
