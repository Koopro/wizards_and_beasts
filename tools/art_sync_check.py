#!/usr/bin/env python3
"""Prove that the art generators still reproduce the shipped assets -- without touching them.

Every generator in `PIPELINE` is run inside a throwaway copy of the repository (`src/`, `tools/`,
`gradle.properties`), and each file it writes is compared with the live one:

  PNG      decoded RGBA pixels (`Image.tobytes()`; never `ImageChops.difference().getbbox()`,
           which only sees alpha on RGBA in this Pillow)
  JSON     parsed value (key order and whitespace do not matter)
  other    bytes

Each job then runs a second time under a different `PYTHONHASHSEED`, so a generator that seeds
from `hash(str)` (salted per process) is caught as non-deterministic.

A job passes when it changes nothing, or changes only what `KNOWN_DRIFT` lists for it. Anything
else is a failure: either the generator no longer describes the approved art, or it writes files
the game does not ship. The live tree is only ever read. See documentation/ART_PIPELINE.md.

Run from the repo root:
    python tools/art_sync_check.py                      # the whole pipeline (~20-40 min)
    python tools/art_sync_check.py --only item_geo,spawn_eggs
    python tools/art_sync_check.py --list               # print the pipeline and exit
Options: --jobs N (parallel sandboxes, default 4), --sandbox DIR (default: system temp),
         --no-determinism (one run per job), --keep (leave the sandboxes for inspection).
Exit status: 0 all in sync, 1 any unexpected difference, 2 bad arguments.
"""

import argparse
import concurrent.futures as cf
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COPY_FILES = ("gradle.properties", "build.gradle", "settings.gradle")
RES = "src/main/resources/assets/wizards_and_beasts/"

# --------------------------------------------------------------------------- the pipeline
#
# name -> list of commands, each a list of arguments to `python`. A chain runs in order in one
# sandbox, the way it has to be run for real. Every creature `tools/<id>_model.py` is added below
# with no arguments unless it appears here.

PIPELINE = {
    # Pre-rigkit rigs whose generator predates two later passes: cube sizes snapped to their
    # painted UV islands, and the shared hit/death/idle clips. A bare run drops both.
    "ghoul_model": [["tools/ghoul_model.py"], ["tools/snap_sizes.py", "ghoul"],
                    ["tools/add_reactions.py", "ghoul"]],
    "obscurus_model": [["tools/obscurus_model.py"], ["tools/snap_sizes.py", "obscurus"],
                       ["tools/add_reactions.py", "obscurus"]],
    "obscurus_model --form-only": [["tools/obscurus_model.py", "--form-only"]],
    "occamy_model": [["tools/occamy_model.py"], ["tools/add_reactions.py", "occamy"]],
    "werewolf_model": [["tools/werewolf_model.py"], ["tools/add_reactions.py", "werewolf"]],
    # Two-mode tools: without --write they only render a mockup.
    "item_sprites": [["tools/item_sprites.py", "--write"]],
    "effect_sprites": [["tools/effect_sprites.py", "--write"]],
}

# Generators run with no arguments (besides the creature rigs, which are discovered).
PLAIN = [
    "animagus_skins", "animate_textures", "banner_textures", "bestiary_portraits", "block_textures",
    "chocolate_frog", "config_textures", "duelling_dummy", "effect_icons", "gui_chrome",
    "gui_parchment", "hud_blood_icons", "item_geo", "item_models_3d", "location_textures",
    "map_textures", "omnioculars_scope", "particle_sprites", "player_geo", "pouch_textures",
    "protego_shield_skin", "skill_chart_textures", "skill_web_layout", "spawn_eggs", "spell_sigils",
    "stat_icons", "tent_skin", "texture_cleanup", "villager_wandmaker", "wand_model",
    "wandwood_textures", "wizard_cards",
]

# Differences that are understood and deliberately not "fixed" by regenerating, because the
# regenerated file would look different from the approved one. Each needs an owner decision;
# the reasons are in documentation/ART_PIPELINE.md ("Known drift").
KNOWN_DRIFT = {
    "spell_sigils": {RES + "textures/gui/sprites/wand_hud/spells/" + n + ".png"
                     for n in ("accio", "capacious_extremis", "diffindo", "episkey")},
    "block_textures": {RES + "textures/block/examination_desk_top.png"},
    "map_textures": {RES + "textures/gui/map/controls.png"},
    "bestiary_portraits": {RES + "textures/bestiary/icons/lethifold.png",
                           RES + "textures/bestiary/icons/obscurus.png",
                           RES + "textures/bestiary/silhouettes/obscurus.png"},
}

# Rigs a bare run must not be used for (see PIPELINE), and rig tools that are not generators.
NOT_PLAIN_MODELS = {"ghoul_model", "obscurus_model", "occamy_model", "werewolf_model"}


def tool_files():
    """tools/*.py that belong to the repository: tracked, or new and not ignored. Retired
    generators kept on a developer's disk are git-ignored and so never picked up."""
    try:
        out = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "tools"],
                             cwd=REPO, capture_output=True, text=True, check=True).stdout
        return sorted(os.path.basename(p) for p in out.split() if p.count("/") == 1 and p.endswith(".py"))
    except (OSError, subprocess.CalledProcessError):  # not a git checkout
        return sorted(f for f in os.listdir(os.path.join(REPO, "tools")) if f.endswith(".py"))


def pipeline():
    jobs = dict(PIPELINE)
    for name in PLAIN:
        jobs[name] = [["tools/%s.py" % name]]
    for f in tool_files():
        name = f[:-3]
        if f.endswith("_model.py") and name not in NOT_PLAIN_MODELS and name not in jobs:
            jobs[name] = [["tools/" + f]]
    return dict(sorted(jobs.items()))


# --------------------------------------------------------------------------- comparison

def digest(path):
    with open(path, "rb") as fh:
        raw = fh.read()
    ext = os.path.splitext(path)[1].lower()
    try:
        if ext == ".png":
            with Image.open(io.BytesIO(raw)) as im:
                im = im.convert("RGBA")
                return "png:%dx%d:" % im.size + hashlib.sha1(im.tobytes()).hexdigest()
        if ext in (".json", ".mcmeta"):
            value = json.loads(raw.decode("utf-8"))
            return "json:" + hashlib.sha1(json.dumps(value, sort_keys=True).encode()).hexdigest()
    except Exception:  # unreadable as its type: fall back to bytes
        pass
    return "raw:" + hashlib.sha1(raw).hexdigest()


def stat_tree(root):
    """rel path -> (mtime, size) for every file under root/src."""
    out = {}
    base = os.path.join(root, "src")
    for d, dirs, files in os.walk(base):
        dirs[:] = [x for x in dirs if x not in (".cache", "__pycache__")]
        for f in files:
            p = os.path.join(d, f)
            st = os.stat(p)
            out[os.path.relpath(p, root).replace("\\", "/")] = (st.st_mtime_ns, st.st_size)
    return out


def written(box, before):
    """Files the job created, changed (by stat) or removed, relative to `before`."""
    after = stat_tree(box)
    touched = {k for k, v in after.items() if before.get(k) != v}
    removed = set(before) - set(after)
    return touched, removed


def differences(box, touched, removed):
    """Of the touched files, those that differ from the live repository."""
    changed, added = [], []
    for rel in sorted(touched):
        live = os.path.join(REPO, rel)
        if not os.path.exists(live):
            added.append(rel)
        elif digest(live) != digest(os.path.join(box, rel)):
            changed.append(rel)
    return changed, added, sorted(removed)


# --------------------------------------------------------------------------- sandbox

def prepare(box):
    if os.path.exists(box):
        shutil.rmtree(box)
    os.makedirs(box)
    shutil.copytree(os.path.join(REPO, "src"), os.path.join(box, "src"),
                    ignore=shutil.ignore_patterns(".cache"))
    shutil.copytree(os.path.join(REPO, "tools"), os.path.join(box, "tools"),
                    ignore=shutil.ignore_patterns("__pycache__"))
    for f in COPY_FILES:
        if os.path.exists(os.path.join(REPO, f)):
            shutil.copy2(os.path.join(REPO, f), box)


def restore(box, base, touched, removed):
    """Undo a job: copy every touched or removed file back from the live tree, drop new ones."""
    for rel in touched | removed:
        live, dst = os.path.join(REPO, rel), os.path.join(box, rel)
        if os.path.exists(live):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(live, dst)
        elif os.path.exists(dst):
            os.remove(dst)
    shutil.rmtree(os.path.join(box, "tools"))
    shutil.copytree(os.path.join(REPO, "tools"), os.path.join(box, "tools"),
                    ignore=shutil.ignore_patterns("__pycache__"))
    now = stat_tree(box)
    assert set(now) == set(base), "sandbox restore left stray files"


def run(box, commands, seed):
    env = dict(os.environ, PYTHONHASHSEED=str(seed), PYTHONIOENCODING="utf-8")
    log, rc = [], 0
    for cmd in commands:
        p = subprocess.run([sys.executable] + cmd, cwd=box, env=env, capture_output=True,
                           text=True, encoding="utf-8", errors="replace")
        log.append((p.stdout + p.stderr).strip()[-600:])
        if p.returncode:
            rc = p.returncode
            break
    return rc, "\n".join(log)


def worker(index, jobs, sandbox_root, determinism):
    box = os.path.join(sandbox_root, "w%d" % index)
    prepare(box)
    base = stat_tree(box)
    results = []
    for name, commands in jobs:
        t0 = time.time()
        rc, log = run(box, commands, 1)
        touched, removed = written(box, base)
        changed, added, gone = differences(box, touched, removed)
        first = {rel: digest(os.path.join(box, rel)) for rel in touched if os.path.exists(os.path.join(box, rel))}
        restore(box, base, touched, removed)
        base = stat_tree(box)
        nondet = []
        if determinism and rc == 0:
            run(box, commands, 2718281)
            touched2, removed2 = written(box, base)
            second = {rel: digest(os.path.join(box, rel)) for rel in touched2 if os.path.exists(os.path.join(box, rel))}
            nondet = sorted(k for k in set(first) | set(second) if first.get(k) != second.get(k))
            restore(box, base, touched2, removed2)
            base = stat_tree(box)
        results.append(dict(name=name, rc=rc, log=log, changed=changed, added=added, removed=gone,
                            nondeterministic=nondet, seconds=round(time.time() - t0, 1)))
        print(".", end="", flush=True)
    return results


# --------------------------------------------------------------------------- report

def verdict(r):
    known = KNOWN_DRIFT.get(r["name"], set())
    unexpected = [p for p in r["changed"] if p not in known] + r["added"] + r["removed"]
    drift = [p for p in r["changed"] if p in known]
    if r["rc"]:
        return "ERROR", unexpected, drift
    if unexpected or r["nondeterministic"]:
        return "DIFF", unexpected, drift
    return ("KNOWN" if drift else "OK"), unexpected, drift


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--only", default="", help="comma-separated job names (see --list)")
    ap.add_argument("--jobs", type=int, default=4)
    ap.add_argument("--sandbox", default="")
    ap.add_argument("--no-determinism", action="store_true")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    jobs = pipeline()
    if args.list:
        for name, commands in jobs.items():
            print("%-28s %s" % (name, "  &&  ".join("python " + " ".join(c) for c in commands)))
        return 0
    if args.only:
        wanted = [s.strip() for s in args.only.split(",") if s.strip()]
        missing = [w for w in wanted if w not in jobs]
        if missing:
            print("unknown job(s): %s (see --list)" % ", ".join(missing), file=sys.stderr)
            return 2
        jobs = {w: jobs[w] for w in wanted}

    sandbox_root = args.sandbox or tempfile.mkdtemp(prefix="wb_art_sync_")
    n = max(1, min(args.jobs, len(jobs)))
    buckets = [[] for _ in range(n)]
    for i, item in enumerate(jobs.items()):
        buckets[i % n].append(item)
    print("checking %d job(s) in %d sandbox(es) under %s" % (len(jobs), n, sandbox_root), flush=True)
    results = []
    try:
        with cf.ProcessPoolExecutor(n) as pool:
            for fut in [pool.submit(worker, i, b, sandbox_root, not args.no_determinism)
                        for i, b in enumerate(buckets)]:
                results.extend(fut.result())
    finally:
        if not args.keep:
            shutil.rmtree(sandbox_root, ignore_errors=True)
    print()

    failed = 0
    for r in sorted(results, key=lambda r: r["name"]):
        status, unexpected, drift = verdict(r)
        print("%-6s %-28s %6.1fs" % (status, r["name"], r["seconds"]))
        if status == "KNOWN":
            print("         known drift: %d file(s)" % len(drift))
        if status in ("DIFF", "ERROR"):
            failed += 1
            for p in unexpected[:12]:
                print("         differs: " + p)
            for p in r["nondeterministic"][:12]:
                print("         changes between runs: " + p)
            if status == "ERROR":
                print("         " + r["log"].replace("\n", "\n         ")[-600:])
    print("\n%d job(s), %d in sync, %d with known drift, %d failing" % (
        len(results), sum(verdict(r)[0] == "OK" for r in results),
        sum(verdict(r)[0] == "KNOWN" for r in results), failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
