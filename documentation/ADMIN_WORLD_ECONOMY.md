# Travel, Ministry, Economy and World administration

Phase 8 of the Control Center, built on `ADMIN_FRAMEWORK.md`. It extends **Travel** and **Ministry** and adds two
sections, **Economy** and **World**. No system was duplicated: every setting binds to the store the game already
reads, at its one read point, and anything without a single read point is shown read-only instead of being faked.

---

## 1. Audit summary (what exists, what does not)

| System | Exists | Does not exist (so not offered) |
|---|---|---|
| Floo | `FLOO_NETWORK` module; 8 Config keys, each with one server reader; powder + flame-charge cost per hop; Knut registration fee; spoken-address misfire | combat restriction (refuses riders, callers, wizards already departing; crosses dimensions) |
| Apparition | `APPARITION` module; tiers, splinch ladder, licence, wards — all hard-coded; its Config keys were deliberately removed at 0.1.0-alpha.1 | random failure (splinching is computed, not rolled), combat lockout (legal in combat by design), money or item cost |
| Brooms | Phase 6 panel | broom combat (a broom cannot be damaged) |
| Ministry | `MINISTRY` module (ships disabled); deterministic Trace; data-driven spell law; fines from the vault; notoriety bands; case ladder + Wizengamot | detection chance, fine expiry, Auror creatures/arrest, imprisonment, a separate enforcement switch |
| Azkaban | one-per-world fortress (new chunks only); Dementors (summoned by command) | a prison: a referral confiscates the wand for 7 days |
| Economy | coinage 29/17/493 (one source); Dragot exchange; 4 priced services; `GRINGOTTS` module | reward multipliers (money enters only as datapack loot) |
| World | Azkaban, Chamber of Secrets (module-gated at generation); trees/plants (biome modifiers); pocket realm; 29 modules | a world-event scheduler (full moon, ageing and owl post are their own clocks) |

**Found and fixed on the way (security):**

- `/wandb player vault deposit|withdraw|clear` and `/wandb player vault <player>` had no permission check, inside a
  branch that is deliberately ungated. Any player could mint Galleons into their own vault or empty another's. They
  now require `WizardsAndBeastsCommandPermissions.ADMIN`; reading your own vault stays open
  (`VaultCommandGateTest`, GameTest `admin_world_unauthorised_refused`).
- The counter's "withdraw all" handed out the whole balance cast from `long` to `int`. It is now capped at the same
  4096 coins per denomination per press as every other verb; the rest stays in the vault.

## 2. Framework addition: apply modes

`admin.config.ApplyMode` is declared per setting by whoever knows its reader, carried on the descriptor (one byte on
the wire), shown as a row badge, on the row's status line, in the tooltip and in command feedback:

| Mode | Badge | Meaning |
|---|---|---|
| `RUNTIME` | — | the next read sees it |
| `RELOAD` | ↻ | after `/reload` |
| `RESTART` | ⟳ | after a server restart (`restartRequired`, unchanged semantics) |
| `NEW_CHUNKS` | ◫ | decided when terrain generates; explored chunks never change; undoing what generated, or getting a one-per-world place whose spot is explored, needs a **new world** |

## 3. Settings by section, with when they take effect

### Travel → Floo

| Setting | Store | Mode |
|---|---|---|
| `module_floo_network` | module state | runtime (datapack reload for recipes runs automatically) |
| `floo_travel_cooldown_ticks`, `floo_misfire_chance_percent`, `floo_departure_windup_ticks`, `floo_lit_timeout_ticks`, `floo_ticks_per_charge`, `floo_speak_typo_tolerance`, `floo_fuzzy_match` | Config | runtime |
| `floo_registration_fee_knuts` (filed under Economy, shown here too) | Config | runtime |

### Travel → Apparition (world data `wizards_and_beasts_apparition_rules`)

| Setting | Default | Mode | Confirms |
|---|---|---|---|
| `module_apparition` | Preview | runtime | when opened |
| `apparition_windup_damage_mode` (Hybrid / Cancel / Lenient) — the combat rule | Hybrid | runtime | — |
| `apparition_blink_cooldown_ticks` | 40 | runtime | — |
| `apparition_anchored_cooldown_ticks` | 1200 | runtime | below half |
| `apparition_splinch_severity_percent` (scales the inflated miss; 0 never splinches) | 100 | runtime | below half |
| `apparition_licence_proficiency_percent` | 25 | runtime | — |

Read only at `ApparitionRules` (published by `ApparitionRulesService` at server start and after each change; reset to
authored on server stop). The pure `SplinchResolver` is untouched; the severity applies at the one call site.

### Travel → Rules

`module_broom_flight` plus the Phase 6 broom rules.

### Ministry → Rules

| Setting | Store | Mode | Confirms |
|---|---|---|---|
| `module_ministry` (Trace, licences, fines, cases — the enforcement switch) | module | runtime | when opened |
| `ministry_fine_scale_percent` (fine multiplier) | Config | runtime | — |
| `ministry_days_per_year` | Config | runtime | always |
| `ministry_new_character_age` (new characters only) | Config | runtime | — |
| `ministry_notoriety_decay_per_second` (new key; `TraceService.decay`) | Config | runtime | — |

**Ministry → Law** (read-only): every offence with heat, remedy and fine at the live scale; wanted bands; Trace
channels and delays; the case ladder; what Azkaban referral does; fine collection. It states plainly what the game
does not do.

### Economy → Gringotts / Rules

| Setting | Store | Mode | Confirms |
|---|---|---|---|
| `module_gringotts` (all charges on/off; vaults untouched) | module | runtime | both directions |
| `ollivander_wand_price_knuts` | Config | runtime | — |
| `floo_registration_fee_knuts` | Config | runtime | — |
| `skill_respec_cost_knuts` (moved from Game Rules) | Config | runtime | — |
| `dragot_galleon_rate` | Config | runtime | — |

Read-only on the Gringotts tab: coinage, Dragot fee (5%), spread (±3%), quote lifetime, forgery rates — fixed in code
and read in several places (one of them a client tooltip), so not settings — plus money sources and the integrity rule.
**No Economy setting moves money** (`AdminWorldEconomyTest` pins the list; `admin_economy_integrity` changes every one
and checks a vault is untouched).

### World → Overview / Rules

| Setting | Store | Mode | Confirms |
|---|---|---|---|
| `module_azkaban` | module | **NEW CHUNKS ONLY** | when opened |
| `module_chamber_of_secrets` | module | **NEW CHUNKS ONLY** | when opened |
| `module_pocket_dimensions` | module | runtime | both directions |
| `module_structures` (decorative location blocks, not worldgen) | module | runtime | when opened |
| `dummy_scarecrow`, `dummy_scare_radius` (hostile-spawn suppression) | Config | runtime | — |

`creature_natural_spawns` stays in Creatures (runtime). The Overview lists every module with its state and where it is
edited.

## 4. Things that are not settings, and why

| Thing | Mode if changed | Why not in the panel |
|---|---|---|
| Spell law (`data/*/spell_law/*.json`) | **reload** | data; edit the datapack and `/reload` |
| Standing gates (`data/*/standing_gates`) | **reload** | data |
| Biome modifiers: creature spawns, wandwood trees, plant patches | **restart** + new chunks | read once at server start |
| Structure placement (spacing, radius, biomes) | **restart** + new chunks | data; a salt change moves positions → **new world** |
| `moduleDefaults.*` in the config | **new world only** | a seed read when a world is created; live state is the module switches |
| Coinage, Dragot fee/spread | — | fixed; scattered readers including a client tooltip |
| Fine amounts per offence, wanted thresholds | — | enum constants used throughout (refactor, not a setting) |

## 5. Server / client

Every Phase 8 setting is server-read. Apparition rules are server-only state (never synced; no client decides a jump).
Module states reach clients through the existing `ModuleStateSyncPayload`. Nothing new is sent per tick.

## 6. Tests

| Test | Proves |
|---|---|
| `AdminWorldEconomyTest` | lang for every new switch/rule/mode/section; Economy holds prices only (pinned); 7 Floo settings under Travel; Apparition defaults = authored constants, runtime, danger only where it can warn; rule arithmetic incl. severity 0 and the forced-discharge sentinel |
| `AdminPayloadCodecTest` | apply mode survives the wire for every mode; unknown ordinal reads as runtime |
| `VaultCommandGateTest` | deposit, withdraw, clear and reading another's vault are administrator-only; own vault open |
| GameTest `admin_world_unauthorised_refused` | non-admin packets refused for 7 new settings; admin without the world capability refused; non-admin `vault deposit` / `clear` refused and the vault unchanged; own vault readable |
| GameTest `admin_world_runtime_changes_reach_readers` | Floo cooldown/misfire, Apparition cooldown/licence, notoriety cooling reach their readers at once; Apparition rule stored in world data; under-half anchored cooldown asks first; reset restores authored rules |
| GameTest `admin_world_apply_mode_flags` | Azkaban/Chamber are NEW_CHUNKS on setting and descriptor, others runtime; Economy/World have settings |
| GameTest `admin_economy_integrity` | every Economy setting changed, vault untouched; console vault deposit/withdraw still work |
| GameTest `admin_ministry_rules` | opening the Ministry asks to confirm; fine scale reaches `FineSchedule.assess`; module drives `TraceService.isActive`; cooling 20 → 15 over 10 s at 0.5/s; nothing cools while closed |
| GameTest `admin_travel_restrictions` | Apparition switch gates `evaluateStart`; Lenient vs Hybrid changes a hit's outcome; severity 0 never splinches, 100 is the authored ladder |

Screen check: `WB_ADMIN_CAPTURE=world WB_ADMIN_CAPTURE_WORLD=<save> WB_ADMIN_CAPTURE_PLAN=world ./gradlew runClient`.

## 7. Limits and follow-ups

- Game Rules is now empty (its one setting is a price) and shows as "soon".
- An admin money tool in the panel is deliberately not built; the command is the tool, gated and logged to operators.
- Changing a module switch reloads datapacks (existing `ModuleStateService` behaviour): safe, but heavy on big packs.
- Possible refactors to make more values settings: per-offence fines/heat, wanted thresholds, the Dragot fee (needs a
  synced accessor because a client tooltip reads it).
