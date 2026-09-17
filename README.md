# Wizards & Beasts

A Wizarding World RPG layer for Minecraft, built on NeoForge. Pick a heritage, earn a wand that
chooses you, find and read spellbooks, and grow into a specialised wizard — alongside brooms, Floo,
Apparition, brewing, creatures and a skill web.

| | |
|---|---|
| Mod id | `wizards_and_beasts` (package `at.koopro.wizardsandbeasts`) |
| Version | `0.1.0-alpha.1` — **alpha**, expect balance changes and unfinished content |
| Minecraft | `1.21.11` |
| NeoForge | `21.11.42` or newer |
| Java | 21 |
| License | All Rights Reserved |

---

## Contents

- [Playing](#playing)
- [What is in the mod](#what-is-in-the-mod)
- [Controls](#controls)
- [Commands](#commands)
- [Modules](#modules)
- [Developing](#developing)
- [Documentation map](#documentation-map)

---

## Playing

### Install

1. Install NeoForge `21.11.42`+ for Minecraft `1.21.11`.
2. Drop into `mods/`:
   - the Wizards & Beasts jar (from a CI run's `wizards-and-beasts-mod-artifact`, or `build/libs/`)
   - **[GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) `5.4.5`** — required, not bundled
   - [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — optional, adds recipe viewing
3. The mod must be on **both** client and server.

### First steps

- On first join you pick a **heritage**. The dossier screen previews what each one changes
  (health, speed, armour, size, magic power, whether it can hold a wand at all).
- Get a wand from Ollivander. No wand bonds without a heritage.
- Spells are **found, not bought**: spellbooks turn up in village houses, stronghold libraries and
  other loot. Hold right-click to read one. A wizard who knows a spell can write a copy at a Study
  Lectern.
- Cast to build **proficiency**; spend skill points on the **skill web** (`K`).

Before reporting a bug, check [`KNOWN_ISSUES.md`](documentation/KNOWN_ISSUES.md) — it lists what is
deliberately unfinished in this build. Bug reports use the
[alpha bug report template](.github/ISSUE_TEMPLATE/alpha_bug_report.yml).

---

## What is in the mod

| Pillar | Highlights |
|---|---|
| **Wands & spells** | Datapack-defined woods and cores with visible cast stats in the tooltip; wand allegiance (a stolen wand casts worse); four-slot loadout + spell wheel; Java and JSON spells; per-spell proficiency curve |
| **Character** | Heritages and variants, innate player stats (POWER / PRECISION / REFLEXES / WILLPOWER), skill web, vocations, Animagus and Obscurial forms, character sheet |
| **Getting around** | Tiered brooms with real flight physics, Floo Network (powder → light → speak → arrive), Apparition, pocket dimensions / expanded trunks |
| **Brewing & herbology** | Cauldron block entity with tiers; signature brews (Veritaserum, Draught of Living Death, Amortentia, …) built on a component codec; mod flora as ingredients |
| **Creatures** | GeckoLib-rigged, data-driven creature roster with ability kits; bonding, gifts and breeding on selected species; Bestiary that unlocks on sight |
| **World & economy** | Gringotts currency, wizarding food and sweets, artefacts (Marauder's Map, Sneakoscope, Deluminator, …), wandwood trees, Ministry Handbook guidebook |

---

## Controls

All rebindable under *Options → Controls*.

| Key | Action |
|---|---|
| `X` (hold) | Spell wheel — aim, release to arm the hovered spell |
| Arrow keys | Select spell slot up / right / down / left |
| `G` | Spell menu |
| `K` | Skill web |
| `C` | Character sheet |
| `V` (hold) | Ability wheel |
| `R` | Use selected ability |
| `N` / `M` / `B` | Quick ability slots 1–3 |
| `H` | Toggle hood |
| *unbound* | Toggle stat HUD |

`F6` / `F7` / `F8` belong to the model debug editor and are not meant for play.

---

## Commands

Everything lives under **`/wandb`**, split into eight categories:

```
/wandb player    per-player state (e.g. player stats, player disguise)
/wandb magic     spells and casting
/wandb item      items
/wandb world     the world
/wandb beast     creatures
/wandb ministry  Ministry systems
/wandb admin     operator tools (e.g. admin module set)
/wandb debug     debug panel and dev kits — admin only
```

Tab completion shows the full tree. New commands belong inside an existing category — see
`WandbCommands` and `CommandTreeShapeTest`, which enforces the list.

---

## Modules

Every major system is a **module** with one of four states. A world is seeded from the shipped
defaults (or the common config's override) the first time it loads; after that, change a live world
with `/wandb admin module set`.

| State | Meaning |
|---|---|
| `ENABLED` | On and presented as finished for the alpha |
| `PREVIEW` | Reachable and playable, but content, art or balance is incomplete |
| `DISABLED` | Off; content stays registered, access is refused. Operators can turn it on |
| `COMING_SOON` | Off and out of an operator's reach (nothing ships in this state) |

Shipped **off** by default: `AZKABAN` and `CHAMBER_OF_SECRETS` (placeholder structures),
`MINISTRY` (law enforcement), `DARK_ARTS`. The full table and the reason for each `PREVIEW` is in
[`KNOWN_ISSUES.md` §1–3](documentation/KNOWN_ISSUES.md); the source of truth is
[`ModuleDefaults`](src/main/java/at/koopro/wizardsandbeasts/module/ModuleDefaults.java).

Server settings are in `config/wizards_and_beasts-common.toml`, also editable from the in-game mod
config screen.

---

## Developing

### Requirements

- **JDK 21.** Gradle uses the toolchain, but if your default `java` is older, pin it in your *user*
  Gradle properties (`~/.gradle/gradle.properties`, or `%USERPROFILE%\.gradle\gradle.properties` on
  Windows):
  ```properties
  org.gradle.java.home=C:/path/to/jdk-21
  ```
- Dependencies (GeckoLib, JEI) resolve from their own Maven repositories; nothing to install by hand.

### Tasks

| Command | What it does |
|---|---|
| `./gradlew build` | Compile, test and produce `build/libs/wizards_and_beasts-<version>.jar` |
| `./gradlew test` | JUnit suite, run inside a bootstrapped FML environment |
| `./gradlew runClient` | Dev client |
| `./gradlew runClient2` | Second client in `runs/client2` as `Dev2`, for multiplayer testing |
| `./gradlew runServer` | Dedicated server, no GUI |
| `./gradlew runGameTestServer` | Runs every registered in-world game test, then exits |
| `./gradlew runData` | Data generation into `src/generated/resources` — **read the warning below first** |

IntelliJ and Eclipse run configurations are generated on Gradle sync.

### CI gate

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and PR, in this order, and
all four must pass:

1. `test`
2. `runData`
3. `runGameTestServer`
4. `build`

The jar is uploaded as the `wizards-and-beasts-mod-artifact` build artifact.

### Generated resources and the `runData` hazard

- Datagen output in `src/generated/resources` is **committed**.
- When a path exists in both `src/main/resources` and `src/generated/resources`, the build keeps one
  (`DuplicatesStrategy.EXCLUDE`). The exception is `lang/en_us.json`: the two files are **merged
  key-by-key**, and the hand-authored `src/main/resources` copy wins on a clash. Adding a lang key to
  the main file directly is fine.
- **Do not run `runData` in your working tree to check something.** It has written placeholder
  textures and models into `src/main/resources`, overwriting committed art with solid-colour stubs.
  Let CI run it, or run it in a throwaway worktree:
  ```bash
  git worktree add --detach ../wandb-datagen HEAD
  cd ../wandb-datagen && ./gradlew runData
  ```
  If you did run it locally, never `git add -A` afterwards — stage only what you meant to change.

### Mixins

Prefer NeoForge events, capabilities and attachments. Use a mixin only when vanilla behaviour must be
changed and no event covers it. Mixins live in `at.koopro.wizardsandbeasts.mixin` (client-only in
`.mixin.client`) and **must be listed** in
[`wizards_and_beasts.mixins.json`](src/main/resources/wizards_and_beasts.mixins.json) — an unlisted
mixin silently never loads.

### Project layout

```
src/main/java/at/koopro/wizardsandbeasts/
  <feature>/          one package per feature (wand, spell, broom, floo, brew, creature, heritage, …)
  client/             client-only rendering, screens and input — nothing outside it imports client classes
  registry/           DeferredRegister holders
  network/            payloads (pure data) and server handlers
  module/             module states and gating
  gametest/           in-world game tests
  mixin/              mixins (see above)
src/main/resources/   hand-authored assets and datapack JSON (spells, brews, creatures, skills, wand woods/cores, …)
src/generated/        datagen output, committed
src/test/             JUnit tests
documentation/        design docs, audits, changelog — see below
```

Features are rooted by package; don't introduce a generic `common/` package.

### Branching

`main` is releases only, `dev` is integration, work happens on `feat/`, `fix/`, `chore/`,
`refactor/` or `spike/` branches off `dev` with a PR back into `dev`. Full rules:
[`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md).

### Troubleshooting

| Symptom | Cause |
|---|---|
| `runServer` fails with `Couldn't find Minecraft server thread` | Usually a stale dev JVM still holding `run/world/session.lock` or port 25565. Kill old `java` processes first |
| Dev server stops ticking after a minute with nobody online | Set `pause-when-empty-seconds=0` in `run/server.properties` |
| A texture is suddenly a flat single-colour square | `runData` stubbed it. `git restore` it from `HEAD` |

---

## Documentation map

Everything lives in [`documentation/`](documentation/). Put new docs there — **root-level `.md` files
other than this README are git-ignored** and will silently never be committed
([why](documentation/README.md)).

| Start here | For |
|---|---|
| [`CHANGELOG.md`](documentation/CHANGELOG.md) | What changed, build by build |
| [`KNOWN_ISSUES.md`](documentation/KNOWN_ISSUES.md) | Player-facing limitations of this build |
| [`DEVELOPER_REFERENCE.md`](documentation/DEVELOPER_REFERENCE.md) | Package layout, registries, datapack architecture, networking, key classes, extension guide |
| [`ALPHA_RELEASE_GATE.md`](documentation/ALPHA_RELEASE_GATE.md) | Tagging decision procedure — work top to bottom, ends in "tag" or "fix X first" |
| [`ALPHA_SMOKE.md`](documentation/ALPHA_SMOKE.md) | By-hand in-game checks the automated gate cannot cover |
| [`CURRENT_STATE.md`](documentation/CURRENT_STATE.md) | Design audit: what the mod is, what's strong, what's still thin |

System deep-dives: [`SPELLS.md`](documentation/SPELLS.md),
[`SPELL_EFFECT_COMPONENTS.md`](documentation/SPELL_EFFECT_COMPONENTS.md),
[`SKILL_WEB.md`](documentation/SKILL_WEB.md), [`CREATURES.md`](documentation/CREATURES.md),
[`GUI_DESIGN_SYSTEM.md`](documentation/GUI_DESIGN_SYSTEM.md),
[`PLAYER_POSE_LAYER_SCHEMA.md`](documentation/PLAYER_POSE_LAYER_SCHEMA.md).

Superseded design docs are kept in [`documentation/history/`](documentation/history/).
