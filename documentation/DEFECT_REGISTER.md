# DEFECT_REGISTER.md — read-only defect sweep

**Observation only. No fixes proposed, no priority ordering.** Prioritisation is Christian's call.

## Header

| | |
|---|---|
| SHA | `935c79363bbbd3752dba425ecde18a595432ac7f` |
| Branch | `fix/chamber-of-secrets-module-gate` |
| Date | 2026-08-02 |
| Stack asserted | MC 1.21.11 / NeoForge 21.11.x, Java 21, Mojang mappings, GeckoLib 5.4.5, JSpecify |

Pre-flight `git status --porcelain`, verbatim:

```
?? WAND_REWIRE_AUDIT.md
?? WORKTREE_TRIAGE_AUDIT.md
```

### Why this file and not `AUDIT_PUNCHLIST.md`

`AUDIT_PUNCHLIST.md` exists with content — 1163 lines, 108 KB, 60+ sections, 23 open `- [ ]`
items and 51 closed. It is a **chronological work-pass log**, by its own header: *"Everything
after the first dated section is a historical log of individual work passes — those are records
of what was done, not an open-item list."* It has zero markdown tables, an incompatible ID scheme
(`AUD-A-…`→`AUD-G-…`), and no `Size`/`Blocks` columns. Converting it in place would be a rewrite,
not a reconcile. Per decision, this register is a **new file**; the punchlist is untouched and its
23 open items are cross-referenced in §X below rather than restated.

### Deviations from the brief

1. **S10 dropped.** The brief's premise — *"the working tree contains substantial uncommitted work
   (petrify/basilisk, Chamber of Secrets content, wandmaking recipes, new beasts)"* — was stale at
   this SHA. All of it is committed (`550e331b`, `fc2a9085`, `f475f6a4`, `38537441`; 33 tracked
   petrify/basilisk/chamber files, 84 wandmaking). Nothing uncommitted to assess. Confirmed by
   decision.
2. **S1 partial by decision.** `./gradlew build` run; client launch / `/reload` / screen-open not
   attempted. Those parts of S1 are marked NOT ATTEMPTED, not "clean".
3. **S8 NOT RUN in pass 1** — completed in pass 2, see below.
4. **Pass 2 (2026-08-02):** appended S1-R, S8, S11 and corrected the pass-1 summary counts. No
   pass-1 entry was edited or renumbered. **S1-R produced no findings** — see its section.

### Methodology deviations (§7 Upgrade License)

| Sweep | Deviation | Evidence |
|---|---|---|
| S2 | `"_comment"` is a **documentation convention** in this repo, not an incompleteness marker (see `data/abilities/*.json`). Counted, then split from genuine stubs. | 12 `abilities` hits are all design notes |
| S4 | Pairing a data dir to an enum **by name** was replaced with pairing **by member-set overlap** (≥25% of smaller set, ≥2 shared). Name-matching married `data/spells` to `SpellCastGate` (a rejection-reason enum) and **missed the wand woods entirely**. | 11 noise pairs → 6 real |
| S7 | Literal-only key detection reports ~1400 false orphans; this mod composes keys at runtime. Added (a) prefix resolution — a key is referenced if a Java string literal is a proper prefix of it, (b) removal of the 16-prefix whitelist, which had missed all 1036 `bestiary.*` keys, (c) exclusion of `/geckolib/` (clip ids are key-shaped but are not lang keys). | 1424 → 66 orphans |
| S6 | Dangling detection does **not** distinguish namespace, so `minecraft:`-namespace vanilla textures appear as dangling. Flagged inline. | 10 of 37 |

### Reproduction

```bash
python tools/audit/s2_incompleteness.py     # S2
python tools/audit/s3_dead_wiring.py        # S3
python tools/audit/s4_duplicates.py         # S4
python tools/audit/s5_switch.py             # S5
python tools/audit/s6_assets.py             # S6
python tools/audit/s7_lang.py               # S7
python tools/audit/s9_stack.py              # S9
./gradlew build --console=plain             # S1
```

All scripts are pure-read. Each prints JSON; the tables below carry the anchored entries and the
scripts carry the exhaustive lists.

---

## Summary

| Severity | Count |
|---|---:|
| BLOCKER | 25 (2 resolved — `S6-001`, `S7-001`; 23 open) |
| POLISH | 22 |
| NICE-TO-HAVE | 10 |
| **Total entries** | **57** |

**Pass-1 summary correction.** The pass-1 header read BLOCKER 6 / POLISH 19 / NICE-TO-HAVE 12.
Recounted from the table rows, pass 1 actually contained **BLOCKER 5 / POLISH 22 /
NICE-TO-HAVE 10** — total 37 was right, all three severity lines were wrong. There was no
unnamed sixth BLOCKER; the five named in the pass-1 report (`S4-001`, `S4-002`, `S5-001`,
`S6-001`, `S7-001`) were the complete set. Figures above are pass 1 corrected plus pass 2.

| Sweep | Status | Raw hits | Register entries |
|---|---|---:|---:|
| S1 build & runtime | PARTIAL — build only | 0 failures | 1 |
| S2 self-declared incompleteness | complete | 390 | 6 |
| S3 dead wiring | complete (see confidence note) | 149 | 8 |
| S4 divergent duplicates | complete | 6 | 4 |
| S5 switch fall-through | complete (1 spot-verified) | 10 | 4 |
| S6 asset integrity | complete | 37 / 334 / 184 | 6 |
| S7 lang coverage | complete | 12 / 66 | 5 |
| S8 unreachable content | complete (pass 2) | 20 | 20 |
| S9 stack violations | complete | 16 | 2 |
| S10 uncommitted work | **DROPPED** (premise stale) | — | 1 |
| S1-R runtime | see §S1-R | — | 0 |
| S11 config surface | complete (pass 2) | 18 / 255 / 24 / 40 | not a defect sweep |

⚖️ = needs a canon or design ruling. Never resolved here.

---

## S1 — Build & runtime

`./gradlew build --console=plain` → **BUILD SUCCESSFUL in 38s**, 9 tasks, `:test` and `:check`
both ran and passed. Zero compile errors. Two Gradle deprecation notices, neither from mod code.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S1-001 | NICE-TO-HAVE | build | `gradle.properties` | Build uses Gradle features incompatible with Gradle 10; `--warning-mode all` not captured, so the specific deprecations are unidentified. | S | — |

**NOT ATTEMPTED** (by decision, not by finding): client launch, `/reload` datapack parse errors,
screen-open throws, entity-spawn throws, item-use throws. S1 is not clean on those axes — it is
unmeasured.

---

## S2 — Self-declared incompleteness

390 marker hits (27 asset-name `placeholder` references excluded as S6's concern). **The count is
misleading: three systemic clusters account for 286 of them.** Reported as one entry each, per §5's
shape-before-dump rule, rather than 286 rows.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S2-001 | POLISH | creatures | `src/main/resources/data/wizards_and_beasts/creatures/abraxan.json:2` | 96 creature definitions carry the identical `_comment`: *"PLACEHOLDER box rig — swap with a Blockbench model matching these bone/texture/anim slots."* — 96 of ~110 creatures ship on box rigs. | XL | S6-003 |
| S2-002 | POLISH | geckolib | `src/main/resources/assets/wizards_and_beasts/geckolib/animations/entity/abraxan.animation.json:3` | 90 animation files declare themselves preview rigs with stubbed clips (`flap_open is stubbed for a future trigger`). | XL | S2-001 |
| S2-003 | POLISH | skill_nodes | `src/main/resources/data/wizards_and_beasts/skill_nodes/alchemy/alchemy_minor_efficiency_1.json:3` | 100 skill nodes use `displayName` keys under the literal namespace `skill.wizards_and_beasts.filler.*` — the node set is padded with acknowledged filler. | XL ⚖️ | — |
| S2-004 | NICE-TO-HAVE | lang | `src/main/resources/assets/wizards_and_beasts/lang/en_us.json:62` | 12 `filler` lang values ship as player-visible strings ("Minor Combat Power", "Minor Focus"). | S | S2-003 |
| S2-005 | NICE-TO-HAVE | tools | `tools/` | 36 marker hits in the art-generation scripts. | S | — |
| S2-006 | NICE-TO-HAVE | client | `src/main/java/at/koopro/wizardsandbeasts/client/` | 16 marker hits in client code, none clustered. | S | — |

Full anchored list: `python tools/audit/s2_incompleteness.py`.

---

## S3 — Dead wiring

**Confidence note.** The reader search excludes the declaring file. That produces false positives
where a codec field is read by a same-file wrapper which is itself read externally —
`BestiaryEntry.habitatKey` is read by `BestiaryEntry.habitat()` at
[BestiaryEntry.java:56](src/main/java/at/koopro/wizardsandbeasts/bestiary/BestiaryEntry.java#L56),
which *is* consumed. The seven `BestiaryEntry` rows below are therefore **suspect, not confirmed**.
Entries are marked accordingly. The registered-object and enum-constant results do not have this
weakness.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S3-001 | POLISH | bestiary | `src/main/java/at/koopro/wizardsandbeasts/bestiary/BestiaryEntry.java:50` | `iconTexture` is parsed from every bestiary entry JSON; no reader found outside the record. Corroborated independently by GUI_AUDIT.md §2.2, which found all 10 non-placeholder `textures/gui/type_icons/*.png` orphaned. | M | — |
| S3-002 | POLISH | creatures | `src/main/java/at/koopro/wizardsandbeasts/creature/CreatureDefinition.java:58` | `bodyPlan` parsed from creature JSON; readers: none. Corroborated by S3-007 — every `BodyPlan` constant is also unread. | M | S3-007 |
| S3-003 | POLISH | creatures | `src/main/java/at/koopro/wizardsandbeasts/creature/CreatureDefinition.java:59` | `locomotion` parsed from creature JSON; readers: none. | M | — |
| S3-004 | POLISH | wand | `src/main/java/at/koopro/wizardsandbeasts/wand/recipe/WandmakingRecipeSerializer.java:28-29` | `result_length_max` and `result_integrity` parsed from wandmaking recipe JSON; readers: none — two recipe outputs are declared in data and dropped. | M | — |
| S3-005 | POLISH | wand | `src/main/java/at/koopro/wizardsandbeasts/wand/WandAttachments.java:30` | `allegiance_score` is persisted and parsed; readers: none. | M | — |
| S3-006 | POLISH | registry | `src/main/java/at/koopro/wizardsandbeasts/registry/ModFeatures.java:20-29` | All four wand-wood tree features (`ELDER_TREE`, `YEW_TREE`, `HOLLY_TREE`, `ROWAN_TREE`) are registered with zero call sites — no biome modifier or placement references them. | M | S4-001 |
| S3-007 | POLISH | creatures | `src/main/java/at/koopro/wizardsandbeasts/creature/BodyPlan.java:10-18` | 9 of the `BodyPlan` constants have no reader anywhere. The enum is parsed and never branched on. | M | S3-002 |
| S3-008 | NICE-TO-HAVE | registry | `src/main/java/at/koopro/wizardsandbeasts/registry/ModDataComponents.java:139` | 8 registered DataComponents have zero readers (`POCKET_ARCHETYPE`, `POCKET_ACCESS_MODE`, `POCKET_TEMPLATE_ID`, `WAND_ELDER_WAND`, `POCKET_LOCKED_EXTERNALLY`, `MOODYS_TRUNK_ACTIVE_LOCK`, `MOODYS_TRUNK_BASE_ID`, `NEWTS_CASE_MUGGLE_WORTHY`). | M | — |

Totals: **30 codec fields, 18 registered objects, 101 enum constants** with no external reader.
Full anchored list: `python tools/audit/s3_dead_wiring.py`.

---

## S4 — Divergent duplicates

6 real divergences after switching to overlap-based pairing. The brief's named instance is
**confirmed**.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S4-001 | **BLOCKER** | wand | `src/main/java/at/koopro/wizardsandbeasts/wand/stat/WandWood.java:12` | 10 wand woods exist as datapack JSON (`ash, blackthorn, elder, hawthorn, holly, rowan, vine, walnut, willow, yew`); the `WandWood` enum declares 4 (`ELDER, YEW, HOLLY, ROWAN`). The casting path reads the enum — [Compatibility.java:58-72](src/main/java/at/koopro/wizardsandbeasts/wand/cast/Compatibility.java#L58) compares `wood == WandWood.YEW` etc., and [Compatibility.java:133](src/main/java/at/koopro/wizardsandbeasts/wand/cast/Compatibility.java#L133) resolves via `WandWood.byName(id.getPath())`, which returns null for the other 6. | L | — |
| S4-002 | **BLOCKER** | wand | `src/main/java/at/koopro/wizardsandbeasts/wand/stat/WandCore.java:12` | Same pattern on cores: 8 JSON definitions vs 10 enum constants, diverging in **both** directions. `ROUGAROU_HAIR`, `THESTRAL_TAIL`, `WHITE_RIVER_MONSTER_SPINE` exist only in the enum; the JSON spells the third one `thestral_tail_hair`, so even the shared concept does not match by name. | L | S4-001 |
| S4-003 | POLISH | module | `src/main/java/at/koopro/wizardsandbeasts/module/Module.java:6` | `data/wizards_and_beasts/tags` and the `Module` enum overlap 20/26 but diverge both ways: 11 tag dirs have no module, and 6 modules (`CHARACTER_SHEET`, `PLAYER_ABILITIES`, `PLAYER_STATS`, `PROFICIENCY`, `SKILL_TREES`, `WANDS_AND_SPELLS`) have no tag. | M ⚖️ | — |
| S4-004 | POLISH | skill | `src/main/java/at/koopro/wizardsandbeasts/skill/SkillTreeId.java:11` | 5 vocation JSONs vs 8 `SkillTreeId` constants vs 14 `Profession` constants — three representations of overlapping concepts, pairwise divergent. `duelist`, `herbologist`, `magizoologist`, `dark_arts`, `wandlore` exist in JSON with no matching constant. | L ⚖️ | — |

Full output: `python tools/audit/s4_duplicates.py`.

---

## S5 — Switch fall-through

10 switches over a mod enum lack both full case coverage and a default that does real work.
**One spot-verified by reading the source; the rest are script output and unverified.**

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S5-001 | **BLOCKER** | spell | `src/main/java/at/koopro/wizardsandbeasts/spell/patronus/PatronusFormDeterminer.java:42` | ✅ **Verified by reading source.** `switch (heritage)` cases 5 of 10 `Heritage` constants and ends `default -> null`. `GOBLIN`, `HOUSE_ELF`, `GIANT`, `CENTAUR`, `MERPEOPLE` resolve to a null patronus form. | M ⚖️ | — |
| S5-002 | POLISH | owl | `src/main/java/at/koopro/wizardsandbeasts/owl/ProfessionEligibility.java:21` | Switch cases 13 of 14 `Profession` constants with no default; `JOURNALIST` falls through. | S | — |
| S5-003 | POLISH | owl | `src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java:114` | Switch cases 12 of 14 `Profession` constants, `default -> false`; `QUIDDITCH_PLAYER` and `JOURNALIST` fall through to ineligible. | S | S5-002 |
| S5-004 | POLISH | skill | `src/main/java/at/koopro/wizardsandbeasts/skill/SkillTreeId.java:202` | Switch over `MagicSource` cases 3 of 4 with no default; `INNATE` and `HYBRID` fall through. | S | — |

Remaining 6 (unverified): `FormModelRenderer.java:198` (`ModelType.HUMANOID`),
`SkillEvents.java:104` and `ExpectoPatronum.java:84` (`Proficiency.NOVICE`, both `default → 0`),
`WantedLevel.java:49` (`OF_INTEREST`), `SpellExecutor.java:122` (`CastType.BEAM_CHANNEL` — likely a
parser truncation false positive, the switch body exceeds the 240-line scan window),
`TrunkArchetype.java:80` (`FIELD_CAMP`).

---

## S6 — Asset integrity

2307 asset files, 1689 texture/model/geckolib, 452 distinct references.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S6-001 | ~~**BLOCKER**~~ **RESOLVED `82ab2fa3`** | bestiary | `src/main/resources/data/wizards_and_beasts/bestiary/entries/bundimun.json:20-21` | `bundimun` declares `textures/bestiary/icons/bundimun.png` and `textures/bestiary/silhouettes/bundimun.png`; neither file exists. **Scope correction on fix: 26 files across 13 creatures were missing, not 2 across 1. Root cause was `tools/bestiary_portraits.py` globbing `ICON_DIR` for its work list, so it could only regenerate portraits that already existed.** | S | — |
| S6-002 | POLISH | bestiary | `src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java:201` | Screen composes `textures/gui/bestiary/entries/<id>.png`; the directory does not exist, so every one of ~107 entries renders `entry_placeholder.png`. Also recorded in GUI_AUDIT.md §2.4. | M | S6-001 |
| S6-003 | POLISH | assets | `src/main/resources/assets/wizards_and_beasts/textures/` | 334 texture/geckolib files are referenced by nothing: 120 under `models/`, 67 `entity/`, 65 `gui/`, 26 `mob_effect/`, 22 `item/`, 19 `animations/`, 12 `block/`. | L | S2-001 |
| S6-004 | POLISH | ability | `src/main/resources/data/wizards_and_beasts/abilities/animagus_beast_ability.json:4` | Ability definitions point at `textures/ability/placeholder.png`, which does not exist. | S | — |
| S6-005 | NICE-TO-HAVE | assets | `src/main/resources/assets/wizards_and_beasts/textures/block/tent_canvas.png` | 184 textures are non-power-of-two (e.g. `160x168`, `256x160`, `16x48`). ⚖️ Minecraft tolerates NPOT for non-atlas entity/block textures, so this is a conformance observation, not necessarily a defect — needs a ruling on whether the repo wants a POT rule. | M ⚖️ | — |
| S6-006 | NICE-TO-HAVE | client | `src/main/java/at/koopro/wizardsandbeasts/client/gui/VanillaGuiTextures.java:14` | 10 of the 37 "dangling" references are `minecraft:`-namespace vanilla textures (`demo_background.png`, `entity/cat/tabby.png`, `entity/wolf/wolf.png`, …) resolved from the client jar. **These are false positives of the sweep**, recorded so a later pass does not re-flag them. | S | — |

Full output: `python tools/audit/s6_assets.py`.

---

## S7 — Lang coverage

2329 lang keys; 1367 literal references; 467 further keys resolved through a composing prefix.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S7-001 | ~~**BLOCKER**~~ **RESOLVED `6f6dcda9`** | heritage | `src/main/resources/assets/wizards_and_beasts/lang/en_us.json` | ~~60~~ **41** keys are namespaced `WizardsAndBeastsMod` instead of the mod id `wizards_and_beasts` — 10 `type.…` and ~~50~~ **31** `subtype.…`. Every heritage and variant description is therefore unreachable, and the live path shows a raw key. **Count correction: the original 60 was an arithmetic error in this register, not in the sweep. Root cause was two key builders (`Heritage:121`, `HeritageVariant:240`), not the lang JSON.** | M | — |
| S7-002 | POLISH | sounds | `src/main/resources/assets/wizards_and_beasts/sounds.json:216` | 9 niffler sound subtitles are referenced from `sounds.json` with no value in `en_us.json` (`entity.niffler.{ambient,hurt,death,eat,happy,hiss,squirm,dig}`, `entity.baby_niffler.ambient`). | S | — |
| S7-003 | POLISH | spell | `src/main/resources/assets/wizards_and_beasts/lang/en_us.json` | 4 spells have name + description values that nothing references: `colloportus`, `finite_incantatem`, `liberacorpus`, `riddikulus` (plus `finite_incantatem.cast_fizzle`). | M | — |
| S7-004 | POLISH | wand | `src/main/resources/assets/wizards_and_beasts/lang/en_us.json` | 6 `wandcraft.cast.reject.*` keys are unreferenced (`cooldown`, `langlocked`, `no_active_spell`, `no_wand`, `not_known`, `unknown_spell`) — the cast-rejection feedback strings exist but nothing reads them. | M | — |
| S7-005 | NICE-TO-HAVE | gringotts / ministry / hud | `src/main/resources/assets/wizards_and_beasts/lang/en_us.json` | 9 further unreferenced values across `gringotts.*.reject.*` (3), `ministry.*.notice.licence.*` (2), `hud.*.stats.*` (2), `command.*.stats.*` (2). | S | — |

Full lists both directions: `python tools/audit/s7_lang.py`.

---

## S8 — Unreachable content

**NOT RUN.** No acquisition-path analysis was performed. This section has zero entries because the
sweep did not execute, **not** because nothing was found. It remains outstanding.

---

## S9 — Stack violations

Clean on six of seven patterns: **zero** `ResourceLocation`, zero `MinecraftForge.EVENT_BUS`, zero
`net.minecraftforge` imports, zero UUID-keyed `AttributeModifier`, zero `ForgeRegistries`, zero
jetbrains/javax `@Nullable` imports.

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S9-001 | POLISH | mixed | `src/main/java/at/koopro/wizardsandbeasts/module/ModuleIds.java:82` | 16 uses of `Identifier.tryParse`, which the project standard forbids in favour of `Identifier.fromNamespaceAndPath`. Other sites: `ApparitionCommands.java:58,89`, `BroomEntity.java:358`, +12. | M | — |
| S9-002 | NICE-TO-HAVE | audit | — | JSpecify coverage on public signatures was **not** measured — it needs a type-aware pass, not a grep, and no AST tooling was stood up. Unmeasured, not clean. | M | — |

Full output: `python tools/audit/s9_stack.py`.

---

## S10 — Uncommitted-work assessment

| ID | Sev | System | Anchor | Defect | Size | Blocks |
|---|---|---|---|---|---|---|
| S10-001 | NICE-TO-HAVE | process | — | Sweep dropped: its premise was stale. At `935c7936` the tree holds only two untracked `.md` files; the petrify/basilisk, Chamber, wandmaking and beast work named by the brief is all committed. | S | — |

---

## S1-R — Runtime failures (completes partial S1)

**Steps 2 and 6 NOT ATTEMPTED**, by decision recorded at the top of pass 2: opening every screen
and running a two-client desync check both require a human driving a client. Claiming them would be
fabrication.

**Steps 1, 3, 4, 5 NOT COMPLETED in this pass either.** `./gradlew runServer` was launched and had
not finished booting when the pass ended, so no `/reload` log, no `/summon` sweep, and no
`/give`+`/setblock` sweep were captured. **S1-R produced no findings and no entries — it is
unmeasured, not clean.** The `S1` section above still stands as build-only.

---

## S8 — Unreachable content

Complete. 108 items, 36 blocks, 19 entity types examined against every acquisition path in the
repo.

| Outcome | item | block | entity |
|---|---:|---:|---:|
| reachable (a path exists) | 76 | 35 | 19 |
| **UNREACHABLE — zero paths** | **19** | **1** | **0** |
| creative/debug by design | 13 | 0 | 0 |
| code-spawned by design | — | — | 6 |

**Cross-check against `AUDIT_PUNCHLIST.md`: CONFIRMED.** The punchlist's standing note —
*"Philosopher's Stone is built, functional, and unobtainable"* — is independently reproduced here as
`S8-012`. That entry was in fact used as the sweep's correctness oracle: three successive
methodology errors each showed up as `philosophers_stone` reading "reachable", and each was traced
and corrected (see the header's methodology table).

**Confidence.** The `reachable` column is deliberately generous — a code site constructing the
item's `ItemStack` counts as a path, because the wand comes from the Ollivander bench menu and a
brew from the cauldron, neither of which has a recipe JSON. So the 20 below are items with **no
path of any kind**, and the false-negative risk sits on the reachable side, not here.

**Module gating (category b) could not be separated.** Nearly every registered id appears in some
`module/*` tag, so "in a module tag" does not discriminate. Gating is recorded per-row in the script
output as `in_module_tag` but is not used to excuse any row: a tag says which module owns a thing,
never how a player gets it.

| S8-001 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:48` | `blood_pact_vial` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-002 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/ConsumableItemRegistry.java:37` | `conjured_spoiled_food` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-003 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:38` | `dark_mark_brand` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-004 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:47` | `deathly_hallow_cloak` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-005 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/CurrencyItemRegistry.java:22` | `dragot` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-006 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:46` | `hermiones_beaded_bag` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-007 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:28` | `hufflepuffs_cup` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-008 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:44` | `invisibility_cloak` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-009 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:22` | `marvolo_gaunts_ring` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-010 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/ModBlocks.java:218` | `newts_case_item` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-011 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:44` | `pensieve` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-012 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:36` | `philosophers_stone` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-013 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:30` | `portkey` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-014 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:31` | `ravenclaws_diadem` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-015 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:41` | `resurrection_stone` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-016 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:19` | `riddles_diary` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-017 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/DarkArtefactItemRegistry.java:25` | `slytherins_locket` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-018 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/TrinketItemRegistry.java:26` | `time_turner` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-019 | **BLOCKER** | item | `src/main/java/at/koopro/wizardsandbeasts/registry/ConsumableItemRegistry.java:111` | `white_river_monster_spine` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |
| S8-020 | **BLOCKER** | block | `src/main/java/at/koopro/wizardsandbeasts/registry/ModBlocks.java:213` | `newts_case_item` is registered with zero survival acquisition path — no recipe output, loot entry, block drop, spawn entry, trade, structure placement, or code grant outside `datagen`/`client`/`command`. | M | — |

Full output including per-row paths and gating flags: `python tools/audit/s8_reachability.py`.

---

## S11 — Configuration surface inventory

Not a defect sweep. No severity column. **Ownership is stated as current fact; where a knob
*should* live is not proposed here.**

### S11.1 — `ModConfigSpec` entries

18 entries across `Config.java` and `ModuleConfig.java`. **Zero have no readers.**

| Name | Scope | Default / range | Anchor | Readers | Owner |
|---|---|---|---|---:|---|
| `enforceSpellRequirements` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:19` | 4 | config |
| `debugLogSpellGateReasons` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:22` | 3 | config |
| `enableDebugTools` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:25` | 4 | config |
| `spellTeacherRequirePayment` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:28` | 2 | config |
| `spellTeacherLearnCostKnuts` | common | `0, 0, Integer.MAX_VALUE` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:31` | 2 | config |
| `debugLogCloakVisibility` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:34` | 2 | config |
| `enableCloakSelfViewRestrictions` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:37` | 1 | config |
| `cloakSelfViewRestrictionsDeathlyOnly` | common | `true` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:40` | 1 | config |
| `perfProfile` | common | `PerfProfile.MEDIUM` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:43` | 3 | config |
| `beamTargetScanIntervalTicks` | common | `2, 1, 20` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:46` | 2 | config |
| `beamChannelEffectIntervalTicks` | common | `5, 1, 20` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:49` | 2 | config |
| `enableWandAllegiance` | common | `true` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:52` | 2 | config |
| `showSpellHudOverlay` | common | `true` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:55` | 2 | config |
| `reduceScreenEffects` | common | `false` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:59` | 4 | config |
| `apparitionRangeBlocks` | common | `32, 4, 256` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:62` | 2 | config |
| `apparitionCooldownTicks` | common | `60, 0, 20 * 300` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:65` | 4 | config |
| `apparitionSplinchBaseChance` | common | `0.35, 0.0, 1.0` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:68` | 2 | config |
| `adminUuids` | common | `DEFAULT_ADMIN_UUIDS, () -> "", entry -> entry instance` | `src/main/java/at/koopro/wizardsandbeasts/Config.java:97` | 3 | config |

### S11.2 — De facto knobs (hardcoded balance dials)

**255 found.** Over the shape-reporting threshold in spirit, so the full list stays in the script
rather than the register. The two the brief names explicitly:

| Knob | Value | Anchor | Controls | Owner |
|---|---|---|---|---|
| `ModifierStack.HARD_CAP` | `3.0f` | `src/main/java/at/koopro/wizardsandbeasts/spell/cast/ModifierStack.java:13` | Upper clamp on every cast modifier (damage, cooldown, power). | java-constant |
| `ModifierStack.HARD_FLOOR` | `0.25f` | `src/main/java/at/koopro/wizardsandbeasts/spell/cast/ModifierStack.java:14` | Lower clamp on the same three. `clampUnit` separately pins misfire chance to 0..1. | java-constant |
| `ProficiencyScaler` damage | `0.65f + 0.85f * t` | `src/main/java/at/koopro/wizardsandbeasts/spell/cast/ProficiencyScaler.java:54` | Damage multiplier curve across proficiency. | java-constant |
| `ProficiencyScaler` cooldown | `1.40f - t * 0.70f` | `…/ProficiencyScaler.java:58` | Cooldown multiplier curve. | java-constant |
| `ProficiencyScaler` duration | `0.75f + p * 0.65f` | `…/ProficiencyScaler.java:62` | Effect-duration multiplier curve. | java-constant |
| `ProficiencyScaler` control | `0.82f + p * 0.48f` | `…/ProficiencyScaler.java:65` | Control/precision multiplier curve. | java-constant |
| `ProficiencyScaler` accuracy | `1.00f - p * 0.20f` | `…/ProficiencyScaler.java:68` | Accuracy multiplier curve (inverted — lower is better). | java-constant |

Representative sample of the other 248, all owner `java-constant`:
`AnimagusAbilityService.PASSIVE_DURATION = 40`,
`AbilityTriggerHandler.TARGET_RANGE_SLACK = 4.0`,
`ApparitionServerLogic.MAX_DISTANCE_RISK = 3.0`,
`PlayerApparitionPoints.MAX_POINTS = 12`,
`OnePerWorldPlacement.BIOME_SNAP_RADIUS = 256`,
`FlooFireplaceBlockEntity.LIT_TIMEOUT_TICKS = 600`,
`TrunkBlock.DECOY_MIN_LOCKS = 7`,
`BrewPotency.BONUS_PER_LEVEL = 0.10f`,
`BasiliskBreedingRitual.TOAD_RADIUS = 2.0` / `MAX_Y = 40`.

Full list with anchors and values: `python tools/audit/s11_config_surface.py`.

### S11.3 — Datapack registries

24 directories. Reload-safety is uniform: all are datapack registries or reload-listener backed, so
they re-read on `/reload`. **Cross-reference S3** — a registry appearing here is not proof its
fields are consumed; `creatures` parses `bodyPlan` and `locomotion` that nothing reads (`S3-002`,
`S3-003`), and `bestiary` parses `iconTexture` that nothing reads (`S3-001`).

| Directory | Entries | Owner |
|---|---:|---|
| `data/wizards_and_beasts/abilities` | 12 | datapack |
| `data/wizards_and_beasts/advancement` | 264 | datapack |
| `data/wizards_and_beasts/bestiary` | 107 | datapack |
| `data/wizards_and_beasts/brewing_recipes` | 2 | datapack |
| `data/wizards_and_beasts/brews` | 2 | datapack |
| `data/wizards_and_beasts/broom_definitions` | 7 | datapack |
| `data/wizards_and_beasts/creatures` | 96 | datapack |
| `data/wizards_and_beasts/damage_type` | 2 | datapack |
| `data/wizards_and_beasts/dimension` | 1 | datapack |
| `data/wizards_and_beasts/dimension_type` | 1 | datapack |
| `data/wizards_and_beasts/handbook` | 13 | datapack |
| `data/wizards_and_beasts/loot_modifiers` | 5 | datapack |
| `data/wizards_and_beasts/loot_table` | 308 | datapack |
| `data/wizards_and_beasts/neoforge` | 30 | datapack |
| `data/wizards_and_beasts/ollivander_pool` | 1 | datapack |
| `data/wizards_and_beasts/pocket_templates` | 3 | datapack |
| `data/wizards_and_beasts/recipe` | 373 | datapack |
| `data/wizards_and_beasts/skill_nodes` | 161 | datapack |
| `data/wizards_and_beasts/spells` | 27 | datapack |
| `data/wizards_and_beasts/tags` | 44 | datapack |
| `data/wizards_and_beasts/vocations` | 5 | datapack |
| `data/wizards_and_beasts/wizards_and_beasts` | 23 | datapack |
| `data/wizards_and_beasts/worldgen` | 19 | datapack |

### S11.4 — State-mutating commands

**40 files register commands.** Permission levels are declared per-command via
`requires(src -> src.hasPermission(n))`. Persistence varies: attachment-backed mutations survive a
restart, client-only debug toggles do not. Per-command permission and persistence were **not**
enumerated individually — that is the one part of S11 left incomplete.

Full list of command roots per file: `python tools/audit/s11_config_surface.py`.

---

## X — Cross-reference to `AUDIT_PUNCHLIST.md`

The punchlist's 23 open `- [ ]` items are **not restated here** and are **not superseded**. Two of
them overlap findings above and are noted for reconciliation:

| Punchlist item | Overlaps |
|---|---|
| *"`wand_mastery` stays an inert flag… registered, purchasable, and does nothing"* | S3 (registered-with-no-readers class of defect) |
| *"Philosopher's Stone is built, functional, and unobtainable"* | S8 — which did not run, so this is the only unreachability datapoint currently on file |

A full reconciliation pass over all 23 was not performed.

---

## Post-flight

`git status --porcelain` after the sweep:

```
?? DEFECT_REGISTER.md
?? WAND_REWIRE_AUDIT.md
?? WORKTREE_TRIAGE_AUDIT.md
?? tools/audit/
```

Diffed against pre-flight: the only additions are `DEFECT_REGISTER.md` and `tools/audit/`. No
tracked file was modified. No git mutation was performed.
