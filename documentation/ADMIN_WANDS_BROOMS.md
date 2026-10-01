# Wand and broom administration

Phase 6 of the Control Center, built on `ADMIN_FRAMEWORK.md` and following the patterns of `ADMIN_BREWING.md`. It
adds two sections:

- **Wands** — Woods, Cores, Generator, Rules.
- **Travel** — Brooms, Rules.

It adds no second wand or broom path. The bench, Ollivander, the cast resolver, the allegiance rules, broom flight
and the broom sync keep reading the stores they read before. What changes is what those stores hold.

---

## 1. What already existed

### Wands

| Part | Where |
|---|---|
| Woods, cores | Datapack registries `wand_woods` / `wand_cores` (`WandDatapackRegistries`), 10 each, synced by vanilla. Each JSON also carries a `_lore` note the codec ignores. |
| Pairings | The 80 `wandmaking` recipes (10 woods × 8 cores; `rougarou_hair` and `white_river_monster_spine` have none). |
| Making a wand | `WandmakersBenchMenu` (recipe → `WandmakingRecipe.createWand`). Ollivander offers trial wands from `ollivander_pool`. |
| Stack | Components: wood, core, flexibility, length, integrity, master, bond, configuration (appearance modules). |
| Appearance | Per-wood `appearance` (tint and modules); named looks in `WandPresetRegistry`. |
| Stats | `WandStatsResolver.resolve`, the one accessor a cast uses. |
| Allegiance | `WandAllegianceService`, which applies the pure rules in `WandAllegianceRules`. Switch: `enableWandAllegiance`. |
| Commands | `/wandb wand …` (bond, config), unchanged. |

### Brooms

| Part | Where |
|---|---|
| Definitions | `broom_definitions/*.json` → `BroomDefinition` → `BroomDefinitionRegistry`, synced by `BroomDefinitionsSyncS2CPayload`. One item per id. |
| Flight | Client-authoritative (the vanilla ridden-vehicle contract). The server only observes positions (`BroomMovement.observeMovement`). |
| Speed | `broomSpeedMultiplier` was a client config value, so a rider could raise their own speed on any server. |
| Wind, FOV, particles | Each player's own preferences (Visuals). |
| Renderer | Per-broom model slots, tint and GeckoLib assets. |

## 2. How admin hooks in

### Wands: rules read at the existing single points

```
WandRulesData (world) ─► WandRules (withdrawn woods/cores/pairs) ─► bench status, Ollivander pool, admin test wand
Config wand keys     ─► WandGlobals (local; synced to remote clients) ─► WandStatsResolver, WandAllegianceRules
```

**Withdrawals** (`WandRules.mayMake`) are checked:
- at the bench, after the recipe lookup — new `STATUS_WITHDRAWN`, "This pairing has been withdrawn on this server";
- in the Ollivander trial pool — withdrawn makes are never offered. The fixed fallback is not filtered, so a new
  wizard always leaves with a wand;
- by the admin generator and the test wand.

Wands that already exist keep working.

**Globals.** Each global is read inside the function that already owned the constant:

| Global | Default | Read by |
|---|---|---|
| Affinity strength (0–2) | 1 | `WandStatsResolver.applyModifiers` scales each wood and core cast modifier around neutral (`WandGlobals.scaled`): damage, cooldown, range, fizzle, category bonus. At 0 every wand is neutral. |
| Bond growth (0–5) | 1 | `WandAllegianceRules.bondAfterSuccessfulCast` |
| Neglect loss (0–5) | 1 | `WandAllegianceRules.bondAfterNeglect` |
| Defeats to win (1–5) | 1 | `WandAllegianceRules.winsToTransfer`. The Elder Wand always stays at 1. |
| Foreign backfire (on/off) | on | `WandAllegianceRules.backfires` |

At the defaults every rule is exactly what it was. The values are synced (`WandGlobalsSyncS2CPayload`, clamped on
decode) so a client's wand tooltips show the server's numbers.

### Brooms: an effective-definition overlay

This is the same shape as brewing:

```
BroomDefinitionLoader → authored → BroomRules.acceptAuthored
                                        │   overrides + withdrawn  (BroomRulesData, world)
                                        │   × broom_server_speed_scale  (Config, server)
                                        ▼
                    BroomDefinitionRegistry ← effective  (existing generation-cached lookups)
                                        │
             BroomDefinitionsSyncS2CPayload → every rider's client → flight
```

- A change republishes and re-sends the existing sync. A broom in the world picks it up at once through the
  registry's generation.
- The authored copy is never edited. Resetting a value restores the file's number.
- A withdrawn broom is refused by `BroomItem.use` (deploy and nearby-mount) and `BroomEntity.interact`. The item
  is kept.

## 3. What is editable

### Wands

| Setting | Id | Notes |
|---|---|---|
| Wood allowed | `wand_wood/<ns>/<wood>/enabled` | ⚠ when withdrawn |
| Core allowed | `wand_core/<ns>/<core>/enabled` | ⚠ when withdrawn |
| Pairing allowed | `wand_pair/<wood ns>/<wood>/<core ns>/<core>/enabled` | Exists only for pairings a recipe defines. ⚠ when withdrawn. |
| Rules tab | `enable_wand_allegiance` ⚠, `wand_affinity_strength`, `wand_bond_growth_multiplier` ⚠(>2×), `wand_defeats_to_win`, `wand_neglect_loss_multiplier`, `wand_foreign_backfire` | Config-backed |

**Compatibility is data.** A pairing exists because a `wandmaking` recipe names it. The admin can withdraw a
pairing, never invent one. A datapack adding a recipe adds the pairing and its setting with no code. The validator
refuses any withdrawal that would leave no makeable pairing (`conflict.last_wand_pair`).

**Read-only, shown not edited.** These are datapack data and are displayed as facts on each page:
- wood and core numbers: rarity, affinity tags, spell leanings, cast modifiers, temperament, appearance modules,
  raw power, consistency, loyalty, dark affinity, initiative, transfer resistance;
- canonical lore: the `_lore` note is read verbatim from the part's datapack file and never rewritten.

### Brooms

Every stat is a slider with the stat's own bounds and step (`BroomStat`). The bounds are chosen around the shipped
roster. `BroomRulesTest` proves every shipped broom sits inside them.

| Stat | Min–max | Step | Stat | Min–max | Step |
|---|---|---|---|---|---|
| Top speed ⚠(>2× authored) | 0.1–2.0 | 0.01 | Turn rate | 0.1–2.0 | 0.05 |
| Acceleration | 0.005–0.3 | 0.005 | Climb rate | 0.05–1.0 | 0.01 |
| Braking | 0.005–0.3 | 0.005 | Dive rate (landing) | 0.05–1.0 | 0.01 |
| Boost | 1.0–3.0 | 0.05 | Handling | 0–1 | 0.05 |
| | | | Stability (wind) | 0–1 | 0.05 |

The broom-level switch and rules:
- `broom/<ns>/<id>/enabled` ⚠ when withdrawn;
- the Rules tab: `broom_server_speed_scale` (0.25–2), `broom_speed_guard` ⚠(off), `broom_speed_multiplier`
  (client), `broom_gentle_landing` (client).

Wind sound and the FOV effect are player preferences under Visuals. They are shown as notes, not broom data.

## 4. The panels

**Wands → Woods / Cores** (`WandPartsPanel`, on the shared `AdminBrowserPanel` list-and-page base). Each page shows:
- the part's allowed switch;
- for a wood, the production wand model (4× item render) and the tooltip a wand of it shows, plus a button that
  opens it in the generator;
- every recipe-defined partner, each with its own pairing switch, and the parts no recipe pairs it with;
- the lore, as written;
- the numbers.

The list marks ✕ for withdrawn and • for a withdrawn pairing.

**Wands → Generator** (`WandGeneratorPanel`). You select:
- wood and core (from the server's registries);
- length, 8–16 in, step 0.25. This covers every recipe (8.5–15.0) and the canonical 16";
- flexibility (the five `WandFlexibility` values);
- appearance: the wood's own, or a preset.

**The model** is the game's item renderer drawing a stack built by `WandmakingRecipe.createWand`, the bench's own
assembly (`AdminItemPreview`). There is no second renderer.

**The verdict and numbers** come from the server (`AdminWandPayloads.PreviewRequest`, debounced 4 ticks), including
the resolved damage, cooldown, range, fizzle and category bonuses from `WandStatsResolver`, the same resolver a cast
uses. A late reply for an older selection is ignored.

**"Give me this wand"** asks the server to make it.

**Travel → Brooms** (`BroomBrowserPanel`). Each page shows:
- the allowed switch;
- a preview: the broom's own item model at 4×, and a bar per stat scaled to the slider range. The gold bar is as it
  flies now, the black tick is as authored, and the red mark is your unapplied edit;
- the nine sliders;
- the handling profile, boost, landing, FOV punch, drift, durability and model facts.

Harness hooks: `showWand(tab, partId)`, `showWandMake(...)` and `showBroom(id, tab)` on `AdminControlCenterScreen`.

## 5. Server authority

| A client cannot… | Because |
|---|---|
| raise broom speed | Speed lives in the server's synced definitions. The server speed scale is folded in before sync. On a remote server the rider's own `broomSpeedMultiplier` is capped at 1 (`BroomRules.personalSpeedMultiplier`). |
| outfly its broom with a modified client | `BroomSpeedGuard`, in `observeMovement`, compares each tick's observed travel with a ceiling from the server's definition, skill and feather, with tolerance. Strikes build (+2) and decay (−1), so packet jitter never trips it. At 20 strikes the rider is set down and the event is logged. |
| choose wand affinity | Affinity strength and allegiance numbers are server config. The synced copy is for tooltips only; casts resolve on the server. |
| claim wand ownership or allegiance | The test wand is built by the server from the recipe and is masterless (`WAND_MASTER` empty). Allegiance then works on it as on any new wand. No payload carries a master, bond or stat. |
| make a restricted pairing | Preview, give, bench and Ollivander all check `WandRules.mayMake` after the recipe lookup. The request names ids and numbers only. The server rejects unknown woods and cores, pairings with no recipe, withdrawn pairings, lengths outside 8–16 and NaN, unknown flexibilities and unknown looks (`WandAdminService.problem`). |
| read or change anything without rights | Reads need read + CONTENT. The test wand needs WORLD. Every change goes through `AdminSettingService` (authorization, bounds, validators, confirmation). |

## 6. How new content becomes editable

- **A new wood or core JSON** gets its allowed switch and page, with its `_lore` shown if present.
- **A new `wandmaking` recipe** gets its pairing switch.
- **A new broom definition** gets its switch, nine sliders and preview (the item is looked up by the definition id).

None of these need code.

## 7. Commands

| Command | What it does |
|---|---|
| `/wandb admin wand woods` \| `cores` | Each part with its state and number of pairings. |
| `/wandb admin wand preview <wood> <core> <length> <flexibility> [look]` | Server verdict and resolved stats. |
| `/wandb admin wand give <wood> <core> <length> <flexibility> [look]` | Test wand (player only, WORLD capability). |
| `/wandb admin broom list` / `info <broom>` | Each broom's stats as authored and as it flies, and its setting ids. |

Edits go through `/wandb admin config set <id> <value>`.

## 8. Tests

**JUnit**
- `WandRulesTest`: withdrawals, affinity scaling, the remote layer, and allegiance reading the globals. Shipped
  values give canon behaviour.
- `BroomRulesTest`: shipped brooms sit inside the slider bounds; bounds and steps are meaningful; `apply` keeps
  every other field; overrides × scale reach the registry; the personal multiplier is capped on remote servers.
- `AdminWandBroomPayloadCodecTest`: payload round trips, the globals sync, and setting ids with their lang keys.
- `AdminCatalogLangTest` and `CommandTreeShapeTest` are extended.

**GameTests** (`AdminWandBroomTests`)
- `admin_wand_invalid_combinations_rejected`: eight invalid makes, a withdrawn pairing, and an unpaired core that is
  not a setting.
- `admin_wand_withdrawal_reaches_ollivander_and_keeps_one_pair`: 40 draws with holly withdrawn; the last-wood
  conflict.
- `admin_wand_unauthorised_rejected`: wood, broom speed and speed scale changes; the pages; the test wand.
- `admin_wand_test_wand_is_unbonded`
- `admin_broom_values_reach_the_synced_definitions`: override, bounds, scale, reset; a withdrawn school broom is
  refused and deploys once allowed.
- `admin_broom_speed_guard_sets_down_overspeeder`: 4 blocks/tick. The existing
  `broom_packet_jitter_costs_no_durability` still passes with the guard on.

**Unchanged:** the existing allegiance, cast and broom tests pass unchanged. That is the "wand casting and security
untouched" check.

## 9. Limits

- Heritage-based wand affinity (`Compatibility`) and `WandResonanceSystem` stay as they are. The affinity-strength
  rule scales the wood and core modifiers, not heritage bonding.
- Wood and core numbers and lore are read-only here. They are datapack data, edited by datapack.
- Flight remains client-simulated. The server enforces speed with the guard, not by simulating.
- The withdrawn state at the bench is covered through the same check the preview uses (`recipeFor` + `mayMake`). No
  GameTest drives a bench menu.
