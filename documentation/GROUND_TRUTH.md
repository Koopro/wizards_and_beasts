# GROUND_TRUTH.md

Read-only ground-truth inventory. Reports what is in source at `HEAD = 4fa18276`, not
what docs claim or specs describe. Classification per item: **BUILT + WIRED**,
**BUILT + ORPHANED**, **SPECCED ONLY**, **ABSENT**.

## Preconditions (per prompt §6)

- `HEAD` = `4fa18276a083148b60f74c269f3982ef1c9d3dd1` — the commit under review. ✓
- Working tree was **dirty at start**: two untracked files present, neither authored by this
  pass — `WAND_REWIRE_AUDIT.md`, `WORKTREE_TRIAGE_AUDIT.md`. Left untouched, not cleaned.
- No source/data/test file was modified. Only this file is written.

---

## A. Cast categories

### Summary

| Item | Classification | Anchor |
|---|---|---|
| `SpellCategory` enum (the cast-category registry) | BUILT + WIRED | `spell/core/SpellCategory.java:5-9` |
| `SpellCategory.CODEC` | BUILT + WIRED | `SpellCategory.java:12` |
| Categories `WandCastModifiers` can carry | BUILT + WIRED (all 4) | `wand/registry/WandCastModifiers.java:26,36-38` |
| Per-category damage bonus consumed at cast | BUILT + WIRED | `wand/cast/WandStats.java:52`; `SpellExecutor.java:72` |
| `SpellFamily` (visual family, orthogonal) | BUILT + WIRED | `spell/core/SpellFamily.java:10-17` |
| Healing / restorative category | **ABSENT** | — |

### The enum — complete, exact

`SpellCategory` (`spell/core/SpellCategory.java:5-9`) has exactly **four** values, each with an
ARGB tint:

- `COMBAT` `0xFFFF4444`
- `UTILITY` `0xFF44FF44`
- `DEFENSE` `0xFF4488FF`
- `DARK_ARTS` `0xFF8B00FF`

Serialized name = `name().toLowerCase()` (`:23-25`). This is the only cast-category type; there is
no separate registry. `SpellFamily` (`FIRE, ICE, ELECTRIC, ARCANE, DARK, LIGHT, WATER`) is a
**visual particle family**, explicitly documented as orthogonal to gameplay category
(`SpellFamily.java:7-9`) — not a cast category.

### What `WandCastModifiers` can carry

`WandCastModifiers` (`wand/registry/WandCastModifiers.java:21-26`) carries
`Map<SpellCategory, Float> categoryDamageBonus`, keyed by `SpellCategory.CODEC` (`:36`). It can
therefore carry **all four** categories. The record's own javadoc (`:12-16`) states the sibling
field `spellModifiers` is keyed by "magical school (`healing`, `divination`, …)" which "have no
counterpart in `SpellCategory` and no consumer." Consumed at cast via
`WandStats.damageFor` → `categoryDamageBonus.getOrDefault(spell.getCategory(), 0.0f)`
(`wand/cast/WandStats.java:52`), reached from `SpellExecutor.java:72`
(`WandStatsResolver.applyToStack`).

### Spells per category (count)

33 registered spells total: **27 JSON** (`data/wizards_and_beasts/spells/*.json`) + **6 bespoke
Java** (`spell/core/Spells.java:55-63`). JSON and Java id sets are disjoint (verified — no overlap).

| Category | JSON | Java | Total |
|---|---|---|---|
| `COMBAT` | 9 | 0 | **9** |
| `UTILITY` | 15 | 0 | **15** |
| `DEFENSE` | 2 | 2 (`Protego`, `ExpectoPatronum`) | **4** |
| `DARK_ARTS` | 1 (`crucio`) | 4 (`AvadaKedavra`, `Imperio`, `ObscurusSurge`, `ObscurusGrasp`) | **5** |

JSON counts from `"category"` field across the 27 files. Java from the `super(...)` category arg:
`AvadaKedavra.java:12`, `ExpectoPatronum.java:26`, `Imperio.java:12`, `ObscurusGrasp.java:35`,
`ObscurusSurge.java:26`, `Protego.java:29`. (`ObscurusGrasp`/`ObscurusSurge` are Obscurial
abilities, gated out of normal learning by `ObscurialRules.isObscurialAbility`, but still carry a
`SpellCategory`.)

- **Every category is used by ≥1 spell.** No category has zero spells.
- **Every spell carries a category.** No JSON spell omits `"category"` (verified); every Java
  `Spell` requires it via constructor (`spell/core/Spell.java:39`).

### Is there a healing/restorative category? — No.

There is **no** healing/restorative `SpellCategory`, and none is referenced anywhere. Healing
exists only as a **spell-effect component type**, `HEAL` (`spell/effect/SpellEffectComponent.java:128`),
applied by individual spells. Example: `episkey.json` carries `"category": "defense"` while its
effect list contains `{ "type": "heal", "amount": 4.0 }` — the restorative behaviour is an effect,
not a category.

### Docs disagree

- None found for this section. Note the source itself documents the healing/school gap in two
  places (`WandCastModifiers.java:12-16`, `WandWoodDefinition.java:12-15`); these agree with the
  code.

---

## B. Wand cores

### Summary

| Item | Classification | Anchor |
|---|---|---|
| `WandCore` enum (10 values) | BUILT + WIRED | `wand/stat/WandCore.java:12-21` |
| Core cast contribution (per-enum hardcoded switch, all 10) | BUILT + WIRED (hardcoded) | `wand/cast/WandStatsResolver.java:83-121` |
| `WandCoreDefinition` JSON soul-attributes (8 files) | BUILT (partially wired — see G) | `wand/registry/WandCoreDefinition.java`; `data/.../wand_cores/*.json` |
| 8 bench-legal core material items | BUILT + WIRED | `registry/WandItemRegistry.java:34-64` |
| `rougarou_hair` core | BUILT + ORPHANED (enum + switch only) | see below |
| `white_river_monster_spine` core | BUILT + ORPHANED (enum + switch only) | see below |

### Enum vs JSON, side by side

`WandCore` enum has **10** values (`WandCore.java:12-21`). `data/.../wand_cores/` has **8** JSON
definitions. Cast contribution is a **hardcoded switch over all 10** in `WandStatsResolver.java:83-121`.

| Enum (`WandCore.java`) | serialized id | `wand_cores/*.json` def | `WandCoreMaterialItem`? | Cast switch |
|---|---|---|---|---|
| `PHOENIX_FEATHER` | `phoenix_feather` | ✓ | ✓ `WandItemRegistry.java:34` | ✓ `:86` |
| `DRAGON_HEARTSTRING` | `dragon_heartstring` | ✓ | ✓ `:38` | ✓ `:89` |
| `UNICORN_HAIR` | `unicorn_hair` | ✓ | ✓ `:42` | ✓ `:92` |
| `THESTRAL_TAIL` | `thestral_tail` (aliased `thestral_tail_hair`) | ✓ `thestral_tail_hair.json` | ✓ `:50` | ✓ `:96` |
| `VEELA_HAIR` | `veela_hair` | ✓ | ✓ `:53` | ✓ `:99` |
| `TROLL_WHISKER` | `troll_whisker` | ✓ | ✓ `:56` | ✓ `:102` |
| `WAMPUS_CAT_HAIR` | `wampus_cat_hair` | ✓ | ✓ `:59` | ✓ `:106` |
| `THUNDERBIRD_TAIL_FEATHER` | `thunderbird_tail_feather` | ✓ | ✓ `:62` | ✓ `:109` |
| `ROUGAROU_HAIR` | `rougarou_hair` | **✗ no JSON** | **✗ plain `registerSimpleItem`** | ✓ `:113` |
| `WHITE_RIVER_MONSTER_SPINE` | `white_river_monster_spine` | **✗ no JSON** | **✗ plain `registerSimpleItem`** | ✓ `:117` |

The `THESTRAL_TAIL` alias: the enum shortened the name; `WandCore.java:31-39` aliases
`thestral_tail_hair → THESTRAL_TAIL` in `BY_NAME` because the item, JSON def, recipes, and lang all
spell it `thestral_tail_hair`.

### Do cores have the woods' dual-system split? — No.

Woods carry **two** parallel fields: `spellModifiers` (orphan) + `castModifiers` (wired) —
`WandWoodDefinition.java:21,25`. `WandCoreDefinition` (`wand/registry/WandCoreDefinition.java:8-16`)
has **neither** — no `castModifiers`, no `spellModifiers`. A core's cast contribution is **not**
datapack-driven; it is the hardcoded per-enum switch in `WandStatsResolver.java:83-121`. The core
JSON instead carries six "soul" attributes (`raw_power`, `consistency`, `loyalty`, `dark_affinity`,
`initiative`, `allegiance_transfer_resistance`) + `source_key` (`WandCoreDefinition.java:9-16`) used
by the bonding/resonance math — **4 of which are orphaned** (see §G).

### `rougarou_hair` and `white_river_monster_spine` — exact status

- **Enum value**: both exist (`WandCore.java:20-21`).
- **Cast switch**: both handled (`WandStatsResolver.java:113-120`).
- **`wand_cores/*.json` definition**: **neither exists** (dir holds only the other 8).
- **Obtainable item**: both exist, but as **plain `registerSimpleItem`** in
  `registry/ConsumableItemRegistry.java:108-111` — **not** `WandCoreMaterialItem`. They carry **no
  core key**, so `WandCoreMaterialItem.isBenchCore` (`item/wand/WandCoreMaterialItem.java:66-68`)
  returns false → the Wandmaker's Bench refuses them and JEI never lists them as cores. They are
  filed under "Wand Cores" in the creative tab (`registry/ModCreativeTabs.java:105-106`).
- **Recipes / loot**: **zero** wandmaking recipes reference either (the other 8 each appear in 10
  `recipe/wandmaking_<wood>_<core>.json` files); no loot table or loot modifier grants them.

Net: both are **BUILT + ORPHANED** — a live enum constant + cast modifier that no wand can ever
carry, because there is no path to put them in a wand.

### Any other core with no definition or no acquisition path

- The `rougarou_hair` / `white_river_monster_spine` pair above are the only two missing a JSON def.
- **Acquisition gap shared by ALL cores**: no `loot_table`, `loot_modifiers`, or creature JSON
  grants any core material item (verified across those dirs — the only loot modifiers inject bezoar
  + currency, `data/.../loot_modifiers/`). The 8 bench cores appear only as **inputs** to
  wandmaking recipes and in the creative tab. Where the raw core item itself comes from in survival
  was not located — see Unknown.

### Docs disagree

- None located. `WandCoreMaterialItem.java:52-64` documents the prior hardcoded-3-item bench bug as
  already fixed, matching current source.

### Unknown

- The survival acquisition path for the **8 bench core materials** (mob drop / structure loot /
  vendor). Searched `loot_table`, `loot_modifiers`, `recipe`, and `creatures/*.json`; found only
  their use as recipe inputs. Could not confirm whether cores are creative-only or granted by a
  mechanism outside those dirs (e.g., an Ollivander/vendor path).

---

## C. Skill web

### Summary

| Item | Value / Classification | Anchor |
|---|---|---|
| Total nodes | **161** across 8 trees | `data/.../skill_nodes/**/*.json` |
| Node schema | id, displayName, description, tree, pointCost(def 1), maxLevel(def 1), effects[], x, y, edges[] | `skill/Skill.java:58,194` |
| Point cap (total earnable) | **60** (`MAX_SKILL_POINTS`, marked `// TUNE`) | `skill/SkillSystemAPI.java:22` |
| Total points to unlock every node once | **245** (134 declared + 111 default-1) | computed |
| Edges | undirected adjacency; buyable if root or neighbor-bought | `SkillSystemAPI.java:108-117,195-199` |
| `arcane_mastery` (required by `capacious_extremis`) | **ABSENT** node | see below |

### Per-tree breakdown

| Tree id | Nodes | Roots (no incoming edge) | Components | Articulation points (chokepoints) |
|---|---|---|---|---|
| `alchemy` | 21 | 1 | 1 | 5 |
| `dark_arts` | 22 | 3 | 1 | 7 |
| `elf_bond` | 5 | 1 | 1 | 0 |
| `goblin_craft` | 5 | 1 | 1 | 0 |
| `herbology` | 24 | 2 | 1 | 12 |
| `magizoology` | 23 | 3 | 1 | 11 |
| `spell_mastery` | 36 | 9 | 1 | 27 |
| `wandlore` | 25 | 1 | 1 | 13 |
| **Total** | **161** | **21** | 8 (one per tree) | — |

Each tree is a single connected component. Of 161 nodes, **100 are filler** (`*_minor_*`, the
passive grind chain) and **61 are named**. All 161 have an `effects` array and resolvable edges (no
dangling edge targets — verified).

### Effect-type distribution (174 effect entries across 161 nodes; 7 nodes are multi-effect)

| Effect type | Count | Consumer |
|---|---|---|
| `passive_attribute` (attributeId + amountPerLevel) | 55 | `SkillAttributeApplicator` |
| `category_cooldown_reduction` | 42 | `SkillEffectCache` |
| `category_damage_bonus` | 27 | `SkillEffectCache.java:84`; `WandStats` |
| `gameplay_bonus` (GameplayStat) | 18 | `GameplayStat` / `SkillEffectCache` |
| `unlock_ability` (boolean flag) | 15 | `SkillEffectCache.hasAbility` |
| `spell_cooldown_reduction` | 11 | per-spell |
| `spell_damage_bonus` | 6 | per-spell |

Effect magnitudes for all 61 named nodes were extracted (e.g. `combat_focus` =
`category_damage_bonus 0.05/level`, `bombarda_unlock` = `spell_damage_bonus 0.1/level`). Not
reproduced in full here for length; the per-tree named-node dump is deterministic from the JSON.

### Point income — how earned, total earnable

Awarded via `SkillSystemAPI.awardPoints`, **clamped so total earned never exceeds
`MAX_SKILL_POINTS = 60`** (`SkillSystemAPI.java:22,208-211`). Sources:

- **+1 SP per XP level gained** (`event/skill/SkillEvents.java:82`, `onLevelUp`).
- **+3 SP** on first heritage selection (`SkillEvents.java:94`).
- **Proficiency milestones per spell**: `PROFICIENT` +1, `MASTERED` +2 (`SkillEvents.java:104-110`).

Total earnable is **bounded at 60**. Unlocking every node once costs **245** (sum of `pointCost`,
default 1 — `Skill.java:58,194`). A player can therefore afford ~24% of the web → forced
specialization. Per-audience cap hook exists but all audiences share 60 today
(`SkillSystemAPI.java:25-40`, marked `// TUNE`). Note several nodes have `maxLevel > 1`, so filling
a node to max costs `pointCost × levels` — the 245 figure is the buy-each-once floor.

### Chokepoints / keystones

Edges are **undirected**; a node is buyable when it is a root **or** any edge-neighbor is at level ≥1
(`SkillSystemAPI.java:108-117`, `hasAllocatedNeighbor` `:195-199`). "Prerequisite" therefore means
graph connectivity to a root; true chokepoints are articulation points (counts in the per-tree
table). `spell_mastery` is near-linear (27 of 36 nodes are articulation points). `elf_bond` and
`goblin_craft` have zero — fully redundant small graphs. Sample entry roots: `basic_casting`
(spell_mastery), `wand_study` (wandlore), `goblin_appraisal` (goblin_craft).

### Nodes whose id/lang implies an unlock they do not perform

`unlock_ability` sets a boolean flag read only via `hasAbility`. Cross-referencing the 15 flags
against the actual `hasAbility(...)` call sites (`ApparitionServerLogic`, `ElfAbilityHandler`,
`GoblinAbilityHandler`, `HerbologyAbilityHandler`, `NifflerStealFromPlayerGoal`,
`AnimagusTransformService`) and `getSkillLevel(nodeId)` readers:

- **`apparition_training`** (`wandlore`) — flag `apparition_training` has **zero** Java references;
  nothing reads it (Apparition checks `elf_apparition`, not this). The node's other effect
  (`category_cooldown_reduction`) works, but its namesake unlock is dead. **BUILT + ORPHANED flag.**
- **`philosophers_stone`** (`alchemy`) — flag `philosophers_stone` has no `hasAbility`/`getSkillLevel`
  consumer; the only ref is the unrelated item registration
  (`registry/DarkArtefactItemRegistry.java:36`). Node's `passive_attribute` +
  `category_cooldown_reduction` work; the unlock is dead. **BUILT + ORPHANED flag.**
- **`wand_mastery`** (`wandlore`) — flag `wand_mastery` is not read by `hasAbility`; the string
  `"wand_mastery"` appears only as a **profession-node** id in `ProfessionNode.java:23,26,33` (a
  separate registry). Node's damage/cooldown effects work; the unlock is dead. **BUILT + ORPHANED flag.**
- `potion_potency` and `creature_knowledge` use `unlock_ability` but are actually consumed by
  `getSkillLevel(nodeId)` (`brew/BrewPotency.java:37`; `util/MagizoologyHelper.java:18`) — the effect
  **payload** is redundant, but the nodes function. Not dead.

Separately, the **`*_unlock` naming across `spell_mastery`/`dark_arts` is misleading at scale**:
nodes like `bombarda_unlock`, `avada_kedavra_unlock`, `crucio_unlock`, `imperio_unlock`,
`lumos_unlock`, `protego_unlock` (17 in `spell_mastery`, 3 in `dark_arts`) only grant
cooldown/damage bonuses. **No spell's `requiredSkillId` points at any of them** (verified across all
27 spell JSONs) — they do **not** gate learning or casting the spell they are named for. The single
spell that sets `requiredSkillId` is `capacious_extremis` → `wizards_and_beasts:arcane_mastery`.

### Docs disagree

- The MEMORY index calls `beast_handler/harvest_bounty/natural_remedy/green_thumb/niffler_friend`
  "wired." Source confirms — but via `gameplay_bonus` / `hasAbility`, not via a spell gate. No
  conflict; noted for completeness.

### Unknown

- Whether `capacious_extremis` is grantable by any path **other** than the skill gate. Its
  `requiredSkillId = "wizards_and_beasts:arcane_mastery"` (`spells/capacious_extremis.json:15`)
  resolves against a **nonexistent** skill node, and `hasSkill` keys on the **bare** node id
  (`PlayerSkillData.java:124-126`) so a namespaced key could never match a node id even if the node
  existed. Via the learn path it is unlearnable. Alternate grants (command, advancement, book) were
  not exhaustively traced.

---

## D. Potions & brewing

### Summary

| Item | Classification | Anchor |
|---|---|---|
| Cauldron tiers (`BRASS/COPPER/PEWTER`) | BUILT + WIRED | `brew/CauldronTier.java:16-19` |
| Tier blocks | BUILT + WIRED | `CauldronBrewing.java` (`tierOf`, `:203-208`) |
| Brewing engine (timed, heat-gated) | BUILT + WIRED | `brew/CauldronBrewing.java` |
| `BrewingRecipe` / `Brew` codecs | BUILT + WIRED | `brew/BrewingRecipe.java`, `brew/Brew.java`, `brew/def/*` |
| Potions defined | 2 (`wiggenweld_potion`, `mandrake_restoration_draught`) | `data/.../brews/*.json` |
| Recipes defined | 2 (same ids) | `data/.../brewing_recipes/*.json` |
| Potency scaling by `potion_potency` skill | BUILT + WIRED | `brew/BrewPotency.java:37`; `Brew.java` `instantiate` |

### Engine shape

Three cauldron tiers, order-significant (`CauldronTier.java`): `BRASS`, `COPPER`, `PEWTER`, with
`isAtLeast`. Bound to three blocks in `CauldronBrewing.tierOf`: `BRASS_CAULDRON`,
`WIZARDING_COPPER_CAULDRON`, `PEWTER_CAULDRON`.

Flow (`brew/CauldronBrewing.java`): sneak + **glass bottle** right-click on a cauldron with a **heat
source below** (fire/soul fire/lava/magma/lit campfire, `hasHeatSource`) → `BrewingRecipes.findMatch`
scans the player inventory for a recipe at that tier → consumes ingredients + one bottle → registers
a timed `ActiveBrew` keyed by position → on `ServerTickEvent.Post` counts down `heatTimeTicks`, then
pops a `BrewItem.of(brew)` above the cauldron and credits the brewer's Potions progress
(`brewPointsFor`: BRASS 1 / COPPER 2 / PEWTER 3). In-memory queue, cap 512 concurrent brews.

Recipe codec (`BrewingRecipe.java:29-34`): `id`, `ingredients[{item, count}]`, `cauldronTier`,
`heatTimeTicks` (≥1), `outputBrewId`. Brew codec (`brew/def/BrewDefinition.java:31-34`, effect entry
`:76-79`): `displayName`, `color`, `effects[{id, duration, amplifier(def 0), ambient(def false)}]`,
`flavorText` (optional). `EffectSpec.instantiate` scales duration by potency multiplier
(`Brew.java`), the multiplier coming from `BrewPotency` × `potion_potency` skill level
(`BrewPotency.java:37`).

### Full example recipe (verbatim — `brewing_recipes/mandrake_restoration_draught.json`)

```json
{
  "ingredients": [
    { "item": "wizards_and_beasts:mandrake", "count": 2 },
    { "item": "minecraft:sugar", "count": 1 }
  ],
  "cauldronTier": "pewter",
  "heatTimeTicks": 400,
  "outputBrewId": "wizards_and_beasts:mandrake_restoration_draught"
}
```

### Every potion + effect

| Brew | Effects | Ingredients (all backed by real items ✓) | Tier |
|---|---|---|---|
| `wiggenweld_potion` | `minecraft:regeneration` 200t/amp1; `minecraft:absorption` 600t/amp0 | glistering_melon_slice, sweet_berries×2, `dittany` | brass |
| `mandrake_restoration_draught` | `wizards_and_beasts:mandrake_restoration` 1t/amp0 | `mandrake`×2, sugar | pewter |

### Ingredients the engine accepts, and backing items

`BrewingRecipe.Ingredient` accepts **any** registered `Item` (id resolved by the codec). Every
ingredient referenced by the two recipes has a real item: `mandrake`
(`ConsumableItemRegistry.java:107`), `dittany` (`ConsumableItemRegistry.java:92`, `DittanyItem`),
plus vanilla `sugar`, `glistering_melon_slice`, `sweet_berries`. The custom effect
`wizards_and_beasts:mandrake_restoration` is registered (`effect/ModEffects.java:18-19`).

### To add one new potion end-to-end (exact list)

1. `data/wizards_and_beasts/brews/<id>.json` — a `BrewDefinition` (displayName, color, effects,
   optional flavorText). Each effect `id` must resolve to a registered `MobEffect`.
2. `data/wizards_and_beasts/brewing_recipes/<id>.json` — a `BrewingRecipeDefinition` (ingredients,
   cauldronTier, heatTimeTicks, `outputBrewId` = the brew id). Each ingredient `item` must resolve.
3. **No Java** for the common case: `BrewItem` is a single generic item carrying brew data
   (`BrewItem.of`), and both defs load via reload listeners (`brew/def/BrewReloadListener.java`,
   `BrewingRecipeReloadListener.java`). Java is needed **only** if the potion introduces a new
   `MobEffect` (register in `effect/ModEffects.java`) or a new ingredient item.

### Docs disagree

- **`BrewingRecipe.java:16-19` javadoc is stale**: it says the "current right-click MVP ignores"
  `heatTimeTicks`. Source contradicts it — `CauldronBrewing` is a **timed** engine that reads
  `recipe.heatTimeTicks()` for the countdown (`CauldronBrewing.java`, `ActiveBrew` +
  `onServerTick`). `heatTimeTicks` is consumed, not ignored.

---

## E. Herbology & crops

### Summary

| Item | Classification | Anchor |
|---|---|---|
| `mandrake_crop` block + `mandrake_seeds` | BUILT + WIRED | `registry/ModBlocks.java:83-95` |
| Any other crop **block** | **ABSENT** (mandrake is the only one) | — |
| Herbology skill tree | BUILT (24 nodes) | `data/.../skill_nodes/herbology/` |
| `green_thumb` (advance nearby crop) | BUILT + WIRED | `event/skill/HerbologyAbilityHandler.java:82` |
| `natural_remedy` (passive regen) | BUILT + WIRED | `HerbologyAbilityHandler.java:76` |
| `harvest_bounty`/`bountiful_harvest` (bonus drops) | BUILT + WIRED | `HerbologyAbilityHandler` (drops handler) |
| `gillyweed`, `dirigible_plum` (plant items) | BUILT + ORPHANED | `ConsumableItemRegistry.java` |

### Crops that exist

- **One** crop block: `MandrakeCropBlock` → `MANDRAKE_CROP` + `MANDRAKE_SEEDS`
  (`ModBlocks.java:83-95`; `block/MandrakeCropBlock.java`). No other `CropBlock`/plant block exists
  (only `MandrakeCropBlock` under `block/`).
- Other herb/plant content exists **only as items** (no farming): `dittany` (`DittanyItem`),
  `gillyweed`, `dirigible_plum`, etc. (`registry/ConsumableItemRegistry.java`).

### Herbology skill nodes (24; named ones)

| Node | Effect |
|---|---|
| `green_thumb` | `unlock_ability green_thumb` → advances one random nearby non-max `CropBlock` (`HerbologyAbilityHandler.java:82-108`) |
| `natural_remedy` | `unlock_ability natural_remedy` → +1 HP regen when fed (`HerbologyAbilityHandler.java:76-79`) |
| `potion_potency` | consumed as `getSkillLevel` by `BrewPotency` (see D) |
| `harvest_bounty`, `bountiful_harvest` | `gameplay_bonus` → duplicate drops on harvest |
| `herbal_vitality` | `passive_attribute` |

`green_thumb` acts on **any** vanilla `CropBlock` (`instanceof CropBlock`), not only mod crops.

### Crops/plants consumed by anything vs nothing

- **Consumed**: `mandrake` → `mandrake_restoration_draught` recipe (1 brewing recipe). `dittany` →
  `wiggenweld_potion` recipe (1) + `DittanyItem` behavior.
- **Consumed by nothing**: `gillyweed`, `dirigible_plum` — registered as plain
  `registerSimpleItem` (no behavior class), referenced by zero recipes/brews. **BUILT + ORPHANED.**
  (Many beast-drop consumables are likewise unreferenced by recipes — see §G.)

### Unknown

- Whether `gillyweed` grants any effect on consumption elsewhere (e.g. an event handler). It is a
  plain item with no dedicated class; no consumer was found, but a generic event listener was not
  exhaustively ruled out.

---

## F. Niffler & beast interaction

### Niffler feature status

| Feature | Classification | Anchor |
|---|---|---|
| Trust meter (0–100) | BUILT + WIRED — **two** 0–100 meters | see below |
| Pouch container + slot count (27 adult / 9 baby) | BUILT + WIRED | `NifflerPouchInventory.java:14-19`; `NifflerPouchMenu` |
| Baby variant + growth timer | BUILT + WIRED | `BabyNifflerEntity.java:24-27,61-62` |
| Pocket-carry attachment | BUILT + WIRED (logic); render placeholder | `CarriedNifflerAttachment.java`; `NifflerEntity.java:230-333` |
| Peek animation via data ticket | BUILT (server logic) + ORPHANED render | see below |
| Bestiary discovery triggers | BUILT + WIRED (4 tiers) | `NifflerEventHandler.java:51-73`; `NifflerEntity.java:375` |
| Magizoology profession bonuses | STUB (posted, no consumer) | `MagizoologyXPEvent.java:9-10` |

**Trust meter** — there are **two** distinct 0–100 meters:

- **Bond**: `DATA_BOND_LEVEL` (synced `EntityDataAccessor`), `bondLevel` 0–100, milestones
  `{20,50,80,100}`, `ownerUUID`, proximity accrual (`NifflerEntity.java:58,74-85,108,136`). Drives
  `NifflerFollowBondedPlayerGoal`.
- **Happiness**: `HappinessAttachment` (`DEFAULT 75, MIN 0, MAX 100` — `HappinessAttachment.java:4-6`),
  feeds `HappinessSpellPower` cast modifiers (`entity/niffler/HappinessSpellPower.java`).

The prompt's "trust meter (0–100)" most closely maps to **bond** (0–100 with named milestones); both
meters are built and wired.

**Pouch**: `NifflerPouchInventory(int slots)` — **adults 27, babies 9** (`NifflerPouchInventory.java:14`),
opened via `NifflerPouchMenu` / `NifflerPouchScreen`.

**Baby variant + growth timer**: `BabyNifflerEntity` — 9-slot pouch, half-scale, **cannot** be
pocket-carried, **grows to adult after 72000 ticks (3 in-game days) with `feedCount >= 3`**
(`BabyNifflerEntity.java:24-27,61-62`); `growthTicks`/feed count persisted (`:100-107`).

**Pocket-carry**: `CarriedNifflerAttachment(UUID)` (`entity/niffler/CarriedNifflerAttachment.java`).
Toggled server-side by **sneak + empty hand** (`NifflerEntity.java:230-283`, `tryToggleCarry` /
`setCarried`), synced via `NifflerCarrySyncS2CPayload` → `ClientPayloadHandlers.java:145-146`. The
**logic is fully wired**; only the on-player **render** is a placeholder (below).

**Peek animation**: server logic is BUILT — `DATA_PEEK_TICK` `EntityDataAccessor<Integer>` plus a
`peekPhase` state machine (0 idle / 1 rising / 2 held / 3 falling) driven each tick
(`NifflerEntity.java:64,86-88,156-`). NB: it uses a vanilla `EntityDataAccessor` (`DATA_PEEK_TICK`) +
GeckoLib `RawAnimation`, **not** a GeckoLib `SerializableDataTicket`. The **client layer that would
render the peeking niffler is an explicit placeholder** — `NifflerPocketLayer.java:18` "renders
nothing until niffler_peek.geo.json is created"; `:48` "full GeckoLib peek model render goes here
once assets exist." So: peek **logic BUILT + WIRED**, peek **visual ORPHANED / SPECCED** (no model).

**Bestiary discovery**: fully wired, 4 tiers `SIGHTED→ENCOUNTERED→STUDIED→MASTERED`
(`bestiary/DiscoveryTier.java:7-10`). Niffler triggers: `SIGHTED`/`ENCOUNTERED`/`STUDIED` in
`NifflerEventHandler.java:51,66,73`, `MASTERED` in `NifflerEntity.java:375`.

**Magizoology profession bonuses**: partial.
- `MagizoologyXPEvent` **is posted** on bond milestones (`NifflerEntity.java:373`) — but it is a
  **stub**: "Full XP integration is out of scope; listeners may subscribe for future systems"
  (`MagizoologyXPEvent.java:9-10`). **No listener consumes it** → posted into the void.
- What *is* wired: `MagizoologyHelper.isMagizoologist` (checks `creature_knowledge` skill,
  `MagizoologyHelper.java:16-18`) gives a **faster bond threshold** (30 vs 50) and another bonus in
  `NifflerEntity.java:360,388`. So the *skill* affects bonding; a *profession* XP/bonus loop does
  not exist yet.

### TODO / stub / unreachable in the Niffler path

- `MagizoologyXPEvent` — declared stub, fires with no consumer (`MagizoologyXPEvent.java:9-10`).
- `NifflerPocketLayer` — placeholder render, draws nothing (`:18,48`).
- No unregistered class or unreachable branch was found in the Niffler entity/AI/network/menu set
  (all AI goals are added in `NifflerEntity`, payloads registered in `ModNetworkNiffler`).

### Across all creatures

- **96 creature definitions** (`data/wizards_and_beasts/creatures/*.json`).
- **All 96 carry the placeholder box-rig marker** — `_comment: "PLACEHOLDER box rig — swap with a
  Blockbench model…"` and a `bodyPlan` field (verified: 96/96). The box-rig contract is
  `creature/BodyPlan.java:5` ("placeholder rig so a real Blockbench model can be swapped in"). No
  creature ships a real Blockbench model via this data path.
- **Taming / breeding code**: no general vanilla `Animal` breeding (`setInLove`/`BreedGoal`) for
  beasts. What exists:
  - `BasiliskBreedingRitual` — a bespoke dark-magic ritual (chicken egg + thunderstorm + toad),
    not vanilla breeding (`basilisk/ritual/BasiliskBreedingRitual.java:28-39`).
  - Niffler **bonding** (not vanilla taming) — see above.
  - `TemptGoal` on some beasts (e.g. `BowtruckleEntity.java:48`) — lure only, no tame/breed.
  - Kelpie "tame-horse guise" is a cosmetic disguise, not taming (`KelpieLureGoal.java:16`).

### Report: unresolved decisions in source

- `creature/Temperament.java:3` — "Placeholder build uses passive wander behaviour only; this is
  metadata for follow-up AI work." Temperament is parsed but drives no distinct AI yet.
- `MagizoologyXPEvent` — hardcoded stub with a deferred-integration comment; a real profession-XP
  branch is unbuilt.
- `NifflerPocketLayer` — render deferred pending an asset that does not exist.

### Unknown

- Whether any consumer of `MagizoologyXPEvent` is registered dynamically (addon/event-bus scan);
  none found statically.

---

## G. Orphan sweep (fields parsed but never read by gameplay)

| Defined in | Field / key | Read by | Status |
|---|---|---|---|
| `wand/registry/WandWoodDefinition.java:21,29` | `spell_modifiers` (`Map<String,Float>`) | nothing (javadoc `:12-15` confirms) | **BUILT + ORPHANED** (canonical) |
| `wand/registry/WandCoreDefinition.java:11` | `consistency` | nothing (`.consistency()` = 0 refs) | **BUILT + ORPHANED** |
| `WandCoreDefinition.java:12` | `loyalty` | nothing | **BUILT + ORPHANED** |
| `WandCoreDefinition.java:13` | `dark_affinity` | nothing | **BUILT + ORPHANED** |
| `WandCoreDefinition.java:14` | `initiative` | nothing | **BUILT + ORPHANED** |
| `WandCoreDefinition.java:10` | `source_key` | nothing (`.sourceKey()` = 0 refs) | **BUILT + ORPHANED** |
| `WandCoreDefinition.java:11` | `raw_power` | `WandResonanceSystem.java:167` | WIRED |
| `WandCoreDefinition.java:16` | `allegiance_transfer_resistance` | `WandDisarmAllegianceSystem.java:95` | WIRED |
| `WandCore.java:20-21` | `ROUGAROU_HAIR`, `WHITE_RIVER_MONSTER_SPINE` | cast switch only; no wand can carry them | **BUILT + ORPHANED** (see B) |
| skill flag `apparition_training` | — | no reader | **BUILT + ORPHANED** (see C) |
| skill flag `philosophers_stone` | — | no reader (item is unrelated) | **BUILT + ORPHANED** (see C) |
| skill flag `wand_mastery` | — | no skill reader (only a profession id) | **BUILT + ORPHANED** (see C) |
| `event/.../MagizoologyXPEvent` | whole event | no listener | **BUILT + ORPHANED** (posted, unconsumed; see F) |
| items `gillyweed`, `dirigible_plum` | — | no recipe/behavior | **BUILT + ORPHANED** (see E) |
| spell `capacious_extremis` `requiredSkillId` | `arcane_mastery` | node absent + key-shape mismatch | soft-broken gate (see C) |

Wired-and-confirmed (checked, **not** orphans): `WandCastModifiers.categoryDamageBonus`
(`WandStats.java:52`), `WandWoodDefinition.castModifiers` (`WandStatsResolver.java:129-135`),
`refuseThreshold` (3 readers), `personalityAffinity`/`affinityTags` (1 reader each),
`heatTimeTicks` (consumed by `CauldronBrewing` — the javadoc claiming otherwise is the stale side).

### Additional load-bearing system named (per prompt §7)

- **`WandStatsResolver`** (`wand/cast/WandStatsResolver.java`) is load-bearing on both A and B: it is
  the single seam where cast categories, wood `castModifiers`, and the hardcoded core switch combine
  into `WandStats`. Included read-only above because the "healing school has no consumer" and
  "cores are hardcoded, not datapack" findings both resolve here.

---

## Method notes / limits

- Counts (`spells`, `skill_nodes`, `creatures`, recipes) are from the on-disk JSON under
  `src/main/resources/data/wizards_and_beasts/` at `HEAD`.
- "Read by nothing" claims are from accessor greps over `src/main/java` (both `.field()` form and
  loose form); a value consumed purely by reflection or by a not-yet-scanned dynamic path would be
  missed — such cases are flagged as Unknown in their section rather than asserted absent.
- No build was run (the tree is dirty and the last commit is unverified per the task); findings are
  static-source only.
