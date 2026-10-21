"""Creature quality matrix, measured from the repository rather than remembered.

Writes documentation/CREATURE_QUALITY_MATRIX_DATA.md: one row per creature, twelve columns, each cell an evidence
grade read from data, assets and code. Hand-written judgement lives in documentation/CREATURE_QUALITY_MATRIX.md;
this file is the evidence it is checked against. Re-run after changing a creature:

    python tools/creature_quality_matrix.py

Grades:  ● rich   ◐ basic / default   ○ missing   – not applicable (a creature that never fights has no combat)
"""
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
DATA = RES / "data/wizards_and_beasts"
ASSETS = RES / "assets/wizards_and_beasts"
JAVA = ROOT / "src/main/java/at/koopro/wizardsandbeasts"

BESPOKE = {  # id -> entity class file stem
    "niffler": "NifflerEntity", "bowtruckle": "BowtruckleEntity", "phoenix": "PhoenixEntity",
    "mooncalf": "MooncalfEntity", "thestral": "ThestralEntity", "dementor": "DementorEntity",
    "cornish_pixie": "CornishPixieEntity",
}
# Bespoke classes that run EcologyGoal (the rest of the bespoke roster declares ecology as data only).
ECOLOGY_WIRED_BESPOKE = {"niffler", "bowtruckle", "mooncalf", "thestral", "phoenix", "cornish_pixie"}
MOVEMENT_CLIPS = ("walk", "run", "fly", "swim", "glide", "gallop", "slither", "crawl", "hover")
COMBAT_ABILITIES = {"enrage", "frenzy", "leap", "status_on_hit", "damage_reduction", "thorns", "dive_bomb",
                    "flame_burst", "ranged_hex", "web_snare", "constrict", "pack_tactics", "life_leech",
                    "spell_resist", "explosive_horn", "blast_propulsion", "lethifold_smother", "slayer_curse"}
G_RICH, G_BASIC, G_NONE, G_NA = "●", "◐", "○", "–"
# ENTITY_DESIGN_BIBLE "Idle vocabulary": shipped defaults plus the proposed additions.
IDLE_WORDS = ("preen", "ruffle", "stretch", "hover", "flit", "shake", "graze", "sniff", "scratch", "coil",
              "taste_air", "groom", "skitter", "peek", "grunt", "drift", "roll", "squirm", "settle", "dig",
              "inspect", "bask", "rear", "listen", "fan_wings", "burrow", "bob", "dance", "forage", "look")


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def java_sources():
    out = {}
    for p in JAVA.rglob("*.java"):
        out[p.stem] = p.read_text(encoding="utf-8", errors="ignore")
    return out


def geo_stats(model_path):
    geo = load(model_path)
    if not geo:
        return 0, 0
    bones = cubes = 0
    for g in geo.get("minecraft:geometry", []):
        for b in g.get("bones", []):
            bones += 1
            cubes += len(b.get("cubes", []))
    return bones, cubes


def anim_clips(anim_path):
    a = load(anim_path)
    return set(a.get("animations", {}).keys()) if a else set()


def main():
    sources = java_sources()
    sounds = load(ASSETS / "sounds.json") or {}
    spawn_text = "".join(p.read_text(encoding="utf-8") for p in (DATA / "neoforge/biome_modifier").glob("*.json"))
    harvest_entries = {(load(p) or {}).get("entry", "").split(":")[-1] for p in (DATA / "bestiary/harvest").glob("*.json")}
    ids = sorted({p.stem for p in (DATA / "creatures").glob("*.json")} | set(BESPOKE))
    rows = []
    totals = {}
    for cid in ids:
        definition = load(DATA / "creatures" / f"{cid}.json")
        ecology = load(DATA / "creature_ecology" / f"{cid}.json")
        bond = (DATA / "creature_bonds" / f"{cid}.json").exists()
        entry = load(DATA / "bestiary/entries" / f"{cid}.json")
        profile = (entry or {}).get("profile") or {}
        spawn = f'"wizards_and_beasts:{cid}"' in spawn_text
        cls = sources.get(BESPOKE.get(cid, ""), "")
        mentions = cls + "".join(src for name, src in sources.items() if cid.replace("_", "") in name.lower())

        # visual identity
        model = ASSETS / "geckolib/models/entity" / f"{cid}.geo.json"
        bones, cubes = geo_stats(model)
        texture = (ASSETS / "textures/entity" / f"{cid}.png").exists() or bool(list((ASSETS / "textures/entity").glob(f"{cid}*.png")))
        visual = G_RICH if bones and cubes / max(bones, 1) > 1.4 and texture else G_BASIC if bones and texture else G_NONE
        # movement and idle
        clips = anim_clips(ASSETS / "geckolib/animations/entity" / f"{cid}.animation.json")
        moving = {c for c in clips if any(m in c for m in MOVEMENT_CLIPS)}
        idle_clips = {c for c in clips if any(w in c for w in IDLE_WORDS)}
        movement = G_RICH if len(moving) >= 2 else G_BASIC if moving or cls else G_NONE
        idle_declared = bool(((definition or {}).get("behaviour") or {}).get("idle"))
        # Every creature has at least its body plan's default idle profile, so never ○.
        idle = G_RICH if idle_declared or len(idle_clips) >= 2 else G_BASIC
        # combat
        damage = (definition or {}).get("attackDamage", 0)
        abilities = {a.get("type", "") for a in (definition or {}).get("abilities", [])}
        fights = damage > 0 or "MeleeAttackGoal" in cls or "doHurtTarget" in cls
        combat_profile = bool(((definition or {}).get("behaviour") or {}).get("combat"))
        combat = G_NA if not fights else G_RICH if combat_profile or len(abilities & COMBAT_ABILITIES) >= 2 or (cls and fights) else G_BASIC
        # non-combat behaviour: live ecology plus a signature
        caps = (ecology or {}).get("capabilities", [])
        wired = bool(caps) and (definition is not None or cid in ECOLOGY_WIRED_BESPOKE)
        signature = bool(profile.get("signature"))
        noncombat = G_RICH if wired and signature else G_BASIC if wired or signature or caps else G_NONE
        habitat = G_RICH if spawn else G_BASIC if "SpawnPlacement" in mentions or "spawn" in cls.lower() else G_NONE
        interactions = profile.get("interactions") or []
        interaction = G_RICH if bond or len(interactions) >= 2 else G_BASIC if interactions else G_NONE
        bonding = G_RICH if bond else G_NONE
        harvest = G_RICH if cid in harvest_entries or profile.get("materials") else G_NONE
        bestiary = G_RICH if entry and profile else G_BASIC if entry else G_NONE
        sound_keys = [k for k in sounds if k.startswith(f"entity.{cid}.")]
        sound_declared = bool(((definition or {}).get("behaviour") or {}).get("sounds"))
        sound = G_RICH if len(sound_keys) >= 2 or sound_declared else G_BASIC if sound_keys else G_NONE
        vfx = G_RICH if re.search(r"sendParticles|addParticle", mentions) else G_NONE

        cells = [visual, movement, idle, combat, noncombat, habitat, interaction, bonding, harvest, bestiary, sound, vfx]
        for i, c in enumerate(cells):
            totals.setdefault(i, {}).setdefault(c, 0)
            totals[i][c] += 1
        eco = ", ".join(caps) if caps else ""
        rows.append(f"| `{cid}` | " + " | ".join(cells) + f" | {eco} |")

    cols = ["Visual", "Movement", "Idle", "Combat", "Non-combat", "Habitat", "Interaction", "Bonding", "Harvest",
            "Bestiary", "Sound", "VFX"]
    out = ["# Creature quality matrix — measured evidence", "",
           "Generated by `tools/creature_quality_matrix.py`; do not edit by hand. Judgement and the plan live in",
           "`CREATURE_QUALITY_MATRIX.md`. ● rich · ◐ basic/default · ○ missing · – not applicable.", "",
           "| Creature | " + " | ".join(cols) + " | Ecology |",
           "|---|" + "---|" * len(cols) + "---|"] + rows
    out += ["", "## Totals", "", "| Column | ● | ◐ | ○ | – |", "|---|---|---|---|---|"]
    for i, name in enumerate(cols):
        t = totals.get(i, {})
        out.append(f"| {name} | {t.get(G_RICH, 0)} | {t.get(G_BASIC, 0)} | {t.get(G_NONE, 0)} | {t.get(G_NA, 0)} |")
    out += ["", "### How each column is measured", "",
            "- **Visual** — GeckoLib model + texture; ● when sculpted (> 1.4 cubes per bone).",
            "- **Movement** — movement clips in the animation file (walk/run/fly/swim/glide…); ● for two or more.",
            "- **Idle** — ● for a declared `idle` profile or two clips from the bible's idle vocabulary; ◐ the body-plan default.",
            "- **Combat** — – when the creature never deals damage; ● for a combat profile, two combat abilities, or a bespoke fight.",
            "- **Non-combat** — ● when its ecology is live (data + a goal that runs it) and it has a bestiary signature.",
            "- **Habitat** — ● when a biome spawn modifier names it; ◐ for code placement; ○ summon/structure only.",
            "- **Interaction** — ● for a bond profile or two bestiary interactions.",
            "- **Bonding** — `creature_bonds/<id>.json`.",
            "- **Harvest** — a bestiary harvest rule or declared materials.",
            "- **Bestiary** — ● entry with a creature profile.",
            "- **Sound** — ● two or more `entity.<id>.*` sounds or a declared sound profile.",
            "- **VFX** — ● when code naming the creature emits particles (heuristic: a grep, not a render)."]
    path = ROOT / "documentation/CREATURE_QUALITY_MATRIX_DATA.md"
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {path} ({len(rows)} creatures)")


if __name__ == "__main__":
    main()
