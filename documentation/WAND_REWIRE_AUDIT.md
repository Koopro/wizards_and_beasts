# WAND_REWIRE_AUDIT.md

**Phase 0 — read-only audit. No code written, nothing staged or committed.**
Branch: `fix/chamber-of-secrets-module-gate`. Date: 2026-08-01.
Prerequisite check: worktree clean except untracked `WORKTREE_TRIAGE_AUDIT.md`; `./gradlew build` **green**. ✅ Proceeded to audit.

---

## ⛔ STOP AND REPORT — four Phase 0 stop-triggers fire

| # | Trigger (§2) | Status |
|---|---|---|
| 1 | `WandWood` persisted in save data or item DataComponents | 🔴 **FIRES** — `wand_wood_legacy` is `.persistent()` **and** `.networkSynchronized()` |
| 2 | Cores exhibit the same dual-system split | 🔴 **FIRES** — and worse: a live silent id-mismatch bug |
| 3 | A wood JSON has a `spell_modifiers` key with no corresponding cast category | 🔴 **FIRES HARD** — **10 of 12** distinct keys unmappable; **all 10 woods** affected |
| 4 | Test suite coverage gap that would let this change break silently | 🔴 **FIRES** — `WandStatsTest` has **zero** wood coverage |

**The decisive one is #3.** §3.3 rules that `applyWood()` is deleted, the registry becomes authoritative, and *"a wood with no modifier for a category contributes exactly 1.0."* Executed literally against the actual data, **no wood has a modifier for any category** — the JSON is keyed by *magical schools* (`healing`, `protection`, `divination`, `hex`…) that do not exist as `SpellCategory` values. Every wood would resolve to neutral 1.0 across the board.

**Net effect of implementing this prompt as written: wood becomes entirely inert.** The 4 woods that currently *do* affect casts (elder, yew, holly, rowan) would stop working. That is a **strict regression** — the opposite of the stated purpose. Making it work requires deciding which school maps to which category, which is explicitly a canon ruling and out of scope (§6).

Halting for a data-model decision. Details below.

---

## 1. Ground-truth inventory

### Wand wood JSON definitions — **10** (prompt's "10" ✅)
Path: `src/main/resources/data/wizards_and_beasts/wizards_and_beasts/wand_woods/` (doubled namespace is correct for datapack registries: `data/<pack_ns>/<registry_ns>/<registry_path>/`).
Registry key: `wizards_and_beasts:wand_woods`, registered via `DataPackRegistryEvent.NewRegistry` (`WandDatapackRegistries:21`).

`ash`, `blackthorn`, `elder`, `hawthorn`, `holly`, `rowan`, `vine`, `walnut`, `willow`, `yew`

### `WandWood` enum — **4 values** (prompt's "4" ✅)
`WandWood.java:13-16` — `ELDER("elder")`, `YEW("yew")`, `HOLLY("holly")`, `ROWAN("rowan")`.
All 4 have a corresponding JSON file. The other 6 woods are enum-invisible → `WandWood.byName()` returns `null` → `applyWood()` early-returns → **zero contribution**. Confirms the prompt's premise for woods.

### Cores — **same split, plus a live bug** (stop-trigger 2)
- `WandCore` enum: **10** values (`WandCore.java:13-22`), consumed by `WandStatsResolver.applyCore()`.
- `WandCoreDefinition` JSON: **8** files. `rougarou_hair` and `white_river_monster_spine` have **no JSON**.
- **Schema is different in kind:** `WandCoreDefinition` has *no* `spell_modifiers` at all — it carries `raw_power`, `consistency`, `loyalty`, `dark_affinity`, `initiative`, `allegiance_transfer_resistance`, consumed by `WandResonanceSystem` and `WandCorruptionSystem` (bonding/corruption), **not** the cast path. So cores are not a duplicated system — they are **two disjoint systems** that happen to share a name.
- 🐞 **Live bug found:** enum id is `thestral_tail`; the JSON file and `ModDataComponents.THESTRAL_TAIL_HAIR_KEY` use `thestral_tail_hair`. `resolveCore()` does `WandCore.byName(id.getPath())` → `"thestral_tail_hair"` has no enum match → `null` → **a Thestral-core wand silently contributes no core modifier to any cast.** Reported, not fixed (§5 forbids touching cores). Recommended for `AUDIT_PUNCHLIST.md` as `BLOCKER` — not written, since this run halted before Phase 5.

---

## 2. Every consumer of `WandWood`

| File:line | Usage | Migration hazard |
|---|---|---|
| `wand/stat/WandWood.java` | the enum itself + `CODEC` + `STREAM_CODEC` | — |
| `registry/ModDataComponents.java:38-43` | `DataComponentType<WandWood>` registered as **`wand_wood_legacy`**, `.persistent(WandWood.CODEC)`, `.networkSynchronized(WandWood.STREAM_CODEC)` | 🔴 **PERSISTED + NETWORKED** |
| `wand/cast/WandStatsResolver.java:7,153-160` | `resolveWood()` — reads legacy component first, else `WandWood.byName(id.getPath())` | the cast path |
| `wand/cast/Compatibility.java:9,32,54-72,107,127-133` | `subtypeAffinity()` / `fate()` — hardcoded `wood == WandWood.YEW/ELDER/HOLLY/ROWAN` bond scoring | second consumer; bond math, not cast math |
| `spell/command/SpellCommands.java:20,82,316` | suggestion provider over `WandWood.values()`; `WandWood.byName(woodName)` | command surface |
| `item/wand/WandItem.java:9,164-167` | `createWand(WandWood, …)` — converts enum → `Identifier` on write | write path already `Identifier` |

**Not consumers** (already `Identifier`/definition-based, listed only to avoid confusion): `WandLoreNames`, `WandResonanceSystem`, `WandCorruptionSystem`, `WandComponents`, `WandDatapackRegistries` — these use `WandWoodDefinition`.

### 🔴 Migration hazard detail (stop-trigger 1)
Two components coexist:
- `wizards_and_beasts:wand_wood` — `DataComponentType<Identifier>` (`WandComponents.java:23-24`). **This is the live one.** Every write path uses it: `WandmakingRecipe:61`, `OllivanderTrialMenu:113,167`, `WandBlankItem:57`, `WandmakingCategory:87` (JEI), `WandItem.createWand:167`.
- `wizards_and_beasts:wand_wood_legacy` — `DataComponentType<WandWood>`. **Nothing writes it.** Two readers, both as a legacy-first fallback: `WandStatsResolver:154`, `Compatibility:128`.

So the enum component is a read-only back-compat shim for wand stacks written before the `Identifier` migration. It is nonetheless **`.persistent()`** — any such stack in an existing world still carries it, and it takes **priority** over the modern component in both readers. Deleting `WandWood` deletes the component type; those stacks lose the data on load (and vanilla logs/strips unknown components). Whether any world actually holds one is unknowable from source — that is exactly why §2.7 makes it a stop-trigger and §6 makes the migration "a separate decision."

---

## 3. Full cast-path trace

`SpellExecutor.executeGeneric(CastContext, ServerLevel)` — `SpellExecutor.java:56-90`. Order into `ModifierStack`:

| # | Source | Magnitude | Note |
|---|---|---|---|
| 1 | `WandCorruptionSystem` integrity | variable | `multiplyDamage("wand_corruption_integrity")`; gated on `Module.WANDS` + `WandItem` |
| 2 | foreign master | `getAllegianceScore(stack)` | `multiplyDamage("wand_foreign_master")`, only if master ≠ caster |
| 3 | `SkillSystemAPI` damage + cooldown | variable | |
| 4 | **`WandStatsResolver.applyToStack`** | **wood/core/length/flex** | ← **where wood enters, or fails to** |
| 5 | `ObscurialCombatRules` | variable | |
| 6 | `VocationAbilityHooks` | variable | |
| 7 | `HappinessSpellPower` | variable | |
| 8 | dark-corruption light-magic penalty | `×0.7` damage | corruption ≥ 90 and category ∈ {DEFENSE, UTILITY} |
| 9 | misfire roll | `finalMisfireChance()` | fizzle → sound + "Your wand fizzles." |

**Wood entry point (step 4):** `applyToStack` (`WandStatsResolver.java:50-56`) pushes the *already-collapsed* `WandStats` — `multiplyDamage(damageFor(spell))`, `multiplyCooldown(cooldownFor(spell))`, `addMisfireChance(fizzleChance())`, all under the single label `"wand"`. `WandStats` itself is built earlier by `resolve(ItemStack)` (line 37-48): `applyCore` → **`applyWood`** → `applyLength` → `applyFlexibility` into a `WandStats.Builder`.

Current `applyWood` contributions (`WandStatsResolver.java:103-115`) — the entire real effect of wood today:
- `ELDER` → `×1.05` damage, `×0.95` cooldown
- `YEW` → `+0.05` DARK_ARTS category damage
- `HOLLY` → `+0.05` COMBAT category damage
- `ROWAN` → `+0.05` DEFENSE category damage, `−0.02` fizzle
- *(any other wood → `null` → early return → nothing)*

**Cap/floor:** `ModifierStack.HARD_CAP = 3.0f`, `HARD_FLOOR = 0.25f` (`ModifierStack.java:13-14`), applied in `clamp()` at **read time** on `finalDamage()` (:44), `finalCooldown()` (:48), `finalPower()` (:52); `clampUnit()` on `finalMisfireChance()` (:56). Not at push time — so ordering of pushes does not affect the clamp result for multiplicative modifiers.

**🔴 Implementation hazard (Upgrade License note, not a stop-trigger):** `WandStatsResolver.resolve` is `static resolve(@Nullable ItemStack)` with **no `RegistryAccess`**. Datapack-registry lookup requires one. There are **8 call sites** (`SpellCastService:150`, `SpellExecutor:45`, `Spell:125`, `WandBeamChannelLogic:105,273`, `SpellCommands:223`, `WandStatsTest:78`). Phase 3 as briefed cannot be done without threading `RegistryAccess`/`HolderLookup.Provider` through all of them. Precedent exists — `WandCorruptionSystem.getEffectivePowerMultiplier(..., level.registryAccess())` already does this (`SpellExecutor:63`) — so the migration is mechanical, but it is a signature change across 8 sites and must be planned, not discovered mid-phase.

---

## 4. Schema comparison — the core finding

**`SpellCategory` has exactly 4 values** (`SpellCategory.java:6-9`): `COMBAT`, `UTILITY`, `DEFENSE`, `DARK_ARTS`.

**`spell_modifiers` uses 12 distinct keys** across the 10 woods:

| Wood | `spell_modifiers` |
|---|---|
| ash | `combat: 0.12`, `warding: 0.08` |
| blackthorn | `combat: 0.16`, `hex: 0.12` |
| elder | `combat: 0.18`, `transfiguration: 0.10` |
| hawthorn | `combat: 0.14`, `curse_breaking: 0.10` |
| holly | `protection: 0.15`, `healing: 0.12`, `charm: 0.05` |
| rowan | `healing: 0.18`, `protection: 0.14` |
| vine | `divination: 0.20`, `charms: 0.08` |
| walnut | `charms: 0.10`, `divination: 0.12` |
| willow | `healing: 0.14`, `protection: 0.10` |
| yew | `dark_arts: 0.20`, `dueling: 0.12` |

### Keys present in JSON with **no consumer / no cast category** — 10 of 12
`warding`, `hex`, `transfiguration`, `curse_breaking`, `protection`, `healing`, `charm`, `charms`, `divination`, `dueling`

Only `combat` → `COMBAT` and `dark_arts` → `DARK_ARTS` map mechanically.

### Cast categories with **no JSON source**
`UTILITY` and `DEFENSE` — nothing in any wood JSON names either. (`protection`/`warding` *read* like DEFENSE and `healing` like UTILITY, but asserting that is a design mapping, not a fact in the data.)

### 🐞 Data bug
`charm` (holly) vs `charms` (vine, walnut) — same concept, two spellings. Whichever mapping is eventually chosen, one of these silently drops. Punchlist candidate, `POLISH`.

### Why this blocks §3.3 completely
Every one of the 10 woods carries at least one unmappable key. Under §3.3 ("no fallback table… a wood with no modifier for a category contributes exactly 1.0"):

| Wood | Effect today | Effect after this prompt as written |
|---|---|---|
| elder | ×1.05 dmg, ×0.95 cd | **neutral** (`combat` is a category; but see below) |
| yew | +0.05 DARK_ARTS | +0.20 DARK_ARTS *(magnitude change — §5 forbids)* |
| holly | +0.05 COMBAT | **neutral** (holly has no `combat` key) |
| rowan | +0.05 DEFENSE, −0.02 fizzle | **neutral** (`healing`/`protection` unmappable) |
| ash, blackthorn, hawthorn | nothing | +COMBAT only, rest dropped |
| vine, walnut, willow | nothing | **neutral** (no mappable key at all) |

Note elder: its JSON has `combat: 0.18` but no damage/cooldown multiplier — so wiring it *changes what elder does* (flat multiplier → COMBAT-only bonus) and *changes its magnitude*, both forbidden by §5. And yew's DARK_ARTS goes 0.05 → 0.20, a **4× magnitude change**, which §3.5 explicitly forbids ("Existing JSON values ship as-is" and "Do not tune" are in direct conflict here — the JSON values *are* different from the shipped enum values).

**§3.5 and §3.3 are mutually unsatisfiable against this data.** Shipping JSON values as-is *is* a rebalance, because the JSON magnitudes were never the live ones.

---

## 5. Tooltip audit

`WandItem.appendHoverText` (`WandItem.java:137-162`) — 1.21.11 signature (`TooltipDisplay`, `Consumer<Component>`). Already:
- reads `WandComponents.getWood/getCore/getFlexibility/getMaster/getIntegrity/getCorruption/getLength/getAllegianceScore`;
- resolves display names through `WandLoreNames.wood(context.registries(), wood)` → the **datapack registry**, via `WandWoodDefinition::displayName`.

✅ **`context.registries()` is already available in the tooltip** — Phase 4's registry access is free; no plumbing needed there (unlike the cast path, §3).
❌ It does **not** read `WandConfiguration`.
❌ **No shift-to-expand tooltip pattern exists anywhere in the mod.** All 6 `appendHoverText` files that mention shift use `player.isShiftKeyDown()` in *use/interaction* paths (`BloodPactVialItem:65`, `DeluminatorItem:112,142`, `DarkMarkItem:84`, `PortkeyItem:25`, `TwoWayMirrorItem:52`, `DebugWandItem:63`), not in tooltips. `Screen.hasShiftDown()` — the client-side tooltip idiom — appears **nowhere**. Phase 4 would establish the mod's first shift-detail tooltip; §3.6's "match the existing pattern if one exists" resolves to *none exists*.

Tooltip currently shows 8 lines unconditionally — already dense. Adding a per-category breakdown behind shift is reasonable, but note the base tooltip is not currently gated at all.

---

## 6. Allegiance interaction

`WandCastingAllegianceSystem` pushes into the **same** `ModifierStack`:
- bonded: `multiplyDamage(damageMul, "wand_allegiance")` (:37)
- foreign wand: `multiplyDamage(0.6f, "wand_allegiance_other")` (:43) + `multiplyCooldown(1.5f, "wand_allegiance_other")` (:45)
- unbound: `multiplyDamage(damageMul, "wand_allegiance_unbound")` (:52)

**Composition is pure multiplication into shared accumulators, clamped only at read time** (`ModifierStack.clamp`, cap 3.0 / floor 0.25). Therefore:
- Order of application is **irrelevant to the result** — multiplication commutes, and no clamp happens between pushes.
- Wiring woods **does not change the effective allegiance penalty** as a ratio: 0.6× stays 0.6×. It changes the *absolute* output, and can change whether the product hits the 3.0 cap or 0.25 floor.
- ⚠️ One real interaction: `SpellExecutor:65` **also** applies `multiplyDamage(getAllegianceScore(stack), "wand_foreign_master")` for a foreign master — so a foreign wand can be penalised twice via two different code paths. Pre-existing; not introduced here. Punchlist candidate, `POLISH`.

---

## 7. Migration risk

**Yes — `WandWood` is persisted.** See §2. `wizards_and_beasts:wand_wood_legacy` is a `.persistent()` + `.networkSynchronized()` `DataComponentType<WandWood>`, read with **priority over** the modern `Identifier` component by both `WandStatsResolver:154` and `Compatibility:128`.

**What breaks if the enum is deleted as briefed (§3.2):** the component type disappears from the registry. Any wand stack in an existing world still carrying it has an unknown component on load — the data is dropped, and for a stack that carries *only* the legacy component (pre-migration wand, never re-written), the wand loses its wood identity entirely rather than falling back cleanly.

**Per §6 — stopping.** A save-data migration is required and is explicitly "a separate decision." A safe path exists (keep the component type registered, read it once, rewrite as `Identifier`, then retire in a later version) but that is a migration design, which this prompt does not authorise.

---

## 8. Stack-rule violation sweep

Swept `WandStatsResolver`, `WandWood`, `ModDataComponents`, `Compatibility`, `SpellCommands`, `WandItem`, `WandWoodDefinition`, `WandDatapackRegistries`, `WandComponents` for `ResourceLocation`, `Identifier.tryParse`, `MinecraftForge.EVENT_BUS`:

**Zero violations.** All use `Identifier` / `Identifier.fromNamespaceAndPath`. No UUID-keyed `AttributeModifier` in these files (the `UUID` in `WandComponents.getMaster` is player identity, not attribute keying — allowed). JSpecify `@Nullable` present on `WandWood.byName` and `WandStatsResolver.resolve`.

---

## 9. Upgrade-License evidence (implementation only — no scope change)

Two briefed details are wrong against the code. Recording, not acting:

1. **§3.7 / Phase 1 "reload listener + volatile-swapped immutable `Map<Identifier, WandWoodDefinition>`" is inapplicable.** Woods are **not** loaded by a custom reload listener — they are a NeoForge **datapack registry** (`event.dataPackRegistry(WAND_WOOD_REGISTRY, …)`, `WandDatapackRegistries:21`). `RegistryAccess` already gives atomic swap-on-reload *and* client sync for free. Building a parallel volatile map would duplicate the registry, add a second source of truth, and reintroduce exactly the dual-system problem this prompt exists to remove. The correct implementation is `registryAccess.lookupOrThrow(WAND_WOOD_REGISTRY).get(id)` — the idiom already used by `WandResonanceSystem:80,231` and `WandCorruptionSystem:64`.

2. **Phase 3's resolution point requires a signature change the brief does not mention.** `WandStatsResolver.resolve(ItemStack)` has no `RegistryAccess`; 8 call sites must be updated. Precedent: `SpellExecutor:63`. Mechanical but must be scoped up front.

---

## 10. Recommendation (decision needed — not acted on)

The blocker is a **data-model question**, not an implementation one: *`spell_modifiers` is keyed by magical school; the cast path is keyed by `SpellCategory`. There are 4 categories and 12 schools.*

Three ways forward, all requiring your ruling:

- **A. Map schools → categories.** Author a school→`SpellCategory` mapping (`protection`+`warding` → DEFENSE, `healing` → UTILITY, `hex` → DARK_ARTS, …). Smallest change, keeps 4 categories. **Requires a canon ruling** and accepts the magnitude jumps in §4 (yew 0.05 → 0.20).
- **B. Make schools first-class.** Introduce a school dimension alongside `SpellCategory` and tag spells with it. Truest to the JSON's intent and to the wandlore fantasy; much larger, touches spell definitions (out of scope here).
- **C. Rewrite the wood JSONs to be category-keyed.** Makes §3.3 work verbatim. But it means **authoring modifier values**, which §5 forbids outright.

Also needs a separate ruling: the `wand_wood_legacy` component retirement (§7), and whether the Thestral core id-mismatch bug (§1) gets its own fix prompt.

**Nothing from the wand rewire was implemented. `./gradlew build` remains green.**
(Separately committed afterwards: `3bc54466` — the Thestral core id fix from §1 plus `WandIdParityTest`. That needed no ruling; see the final section.)

---

# Appendix A — proposed school→category mapping (AWAITING RULING, not implemented)

Drafted for approval. **No values are authored here** — magnitudes are quoted verbatim from the
existing JSON. The only proposal is which `SpellCategory` each school key routes to.

## A.1 The table

| JSON school key | → `SpellCategory` | Confidence |
|---|---|---|
| `combat` | COMBAT | certain |
| `dueling` | COMBAT | certain |
| `dark_arts` | DARK_ARTS | certain |
| `hex` | DARK_ARTS | high — a hex is minor dark magic |
| `protection` | DEFENSE | certain |
| `warding` | DEFENSE | certain |
| `curse_breaking` | DEFENSE | **debatable** — counter-magic; could be UTILITY |
| `healing` | UTILITY | forced — no better home |
| `charm` + `charms` | UTILITY | **debatable** — Charms is broad; some charms are combat |
| `transfiguration` | UTILITY | forced |
| `divination` | UTILITY | forced |

`charm` (holly) and `charms` (vine, walnut) are the same concept; the mapping dedupes them. Which
spelling wins in the JSON is a separate trivial fix.

## A.2 🔴 What the mapping reveals — read before ruling

Working the table through against real data surfaced two problems that A does **not** solve.

### Problem 1 — the JSON schema is strictly less expressive than the enum table it replaces

`spell_modifiers` is `Map<String, Float>` consumed as *category damage bonus only*. It has **no
vocabulary** for the other four things woods currently do:

| Mechanic | Enum table today | Expressible in JSON? |
|---|---|---|
| category damage bonus | yes | ✅ yes |
| flat damage multiplier | elder `×1.05` | ❌ **no** |
| cooldown multiplier | elder `×0.95` | ❌ **no** |
| fizzle modifier | rowan `−0.02` | ❌ **no** |
| range multiplier | (cores use it) | ❌ **no** |

So elder's cooldown break and rowan's fizzle reduction **cannot survive** the migration. They are
deleted, not moved. Any mapping has this problem — it is a schema gap, not a mapping choice.

### Problem 2 — UTILITY is a damage-bonus dead end

The mechanic is `addCategoryDamageBonus`. Utility spells overwhelmingly deal no damage, so a UTILITY
bonus multiplies nothing. Four of the eleven schools route to UTILITY, which means the woods defined
purely by those schools stay **effectively inert even after wiring** — the exact defect this work
exists to fix.

### A.3 Per-wood before/after under the proposed mapping

| Wood | Today | After | Verdict |
|---|---|---|---|
| ash | nothing | COMBAT +0.12, DEFENSE +0.08 | ✅ real gain |
| blackthorn | nothing | COMBAT +0.16, DARK_ARTS +0.12 | ✅ real gain |
| hawthorn | nothing | COMBAT +0.14, DEFENSE +0.10 | ✅ real gain |
| willow | nothing | DEFENSE +0.10, UTILITY +0.14 | ✅ partial gain |
| yew | DARK_ARTS +0.05 | DARK_ARTS +0.20, COMBAT +0.12 | ⚠️ **4× magnitude jump** |
| elder | ×1.05 dmg, ×0.95 cd | COMBAT +0.18, UTILITY +0.10 | ⚠️ **shape change; cooldown break lost** |
| holly | COMBAT +0.05 | DEFENSE +0.15, UTILITY +0.17 | ⚠️ **loses COMBAT entirely** |
| rowan | DEFENSE +0.05, fizzle −0.02 | DEFENSE +0.14, UTILITY +0.18 | ⚠️ **2.8× jump; fizzle lost** |
| vine | nothing | UTILITY +0.28 | ❌ **still inert in practice** |
| walnut | nothing | UTILITY +0.22 | ❌ **still inert in practice** |

Scorecard: 4 real gains, 4 changed-in-shape-or-magnitude (all forbidden by §3.5/§5), 2 still inert.

Note holly especially — canonically Harry's duelling wand — would **lose** its COMBAT bonus and
become a defensive/utility wood. That is a canon-facing outcome, not just a number.

## A.4 Honest recommendation

**Option A alone does not achieve the prompt's stated purpose.** It fixes 4 woods, breaks 4, and
leaves 2 inert — while deleting elder's cooldown break and rowan's fizzle reduction outright.

The minimum change that actually works is **A + a schema extension**: give `WandWoodDefinition` the
missing vocabulary (a flat `damage` / `cooldown` / `fizzle` / `range` block alongside the per-school
map) so the existing enum behaviour has somewhere to land. That is a codec change, not a balance
change, and it lets every current effect migrate without authoring a single new number.

Three things still need Christian's ruling, and none can be inferred from the code:
1. The `curse_breaking` and `charms` rows above.
2. Accepting the magnitude jumps (yew 4×, rowan 2.8×) — or supplying different numbers.
3. Whether holly may stop being a combat wood.

### A.5 Rulings received 2026-08-01

| # | Ruling | Status |
|---|---|---|
| 1 | `curse_breaking` → **UTILITY**, `charms` → **UTILITY** | ✅ answered |
| 2 | magnitude jumps (yew 4×, rowan 2.8×) | ❌ **"Idk" — unanswered** |
| 3 | holly may stop being a combat wood | ✅ answered (yes) |

Ruling 1 moves `curse_breaking` out of DEFENSE, so DEFENSE is fed only by `protection` + `warding`,
and UTILITY now absorbs **five** of eleven schools — worsening Problem 2 (§A.2), not improving it.
Under it, hawthorn resolves to COMBAT +0.14 / UTILITY +0.10 and is the fifth wood whose non-combat
half lands on a damage bonus that multiplies nothing.

**Ruling 2 is load-bearing and remains open, so the school-wiring path stays blocked.** Wiring
`spell_modifiers` as-is is a rebalance (§3.5 and §5 both forbid it); wiring it with different numbers
means authoring values (§5 forbids that too). Neither is available without ruling 2.

Rewire via the school map: **still blocked.** See §A.6 for a path that needs no ruling at all.

## A.6 Zero-ruling alternative (recommended)

The brief's own §3.4 already says the six unwired woods **ship neutral** and their modifier values
must not be authored. Taking that literally dissolves the blocker:

> Migrate the **four wired woods' existing enum values verbatim into JSON**, leave the six unwired
> woods neutral, and leave `spell_modifiers` unconsumed until ruling 2 lands.

This satisfies every fixed ruling in §3 without a single canon decision:

- registry becomes authoritative for cast contributions ✅
- **zero** behavioural delta — every number is transcribed, not chosen ✅
- no magnitude change (§3.5) ✅
- six woods ship neutral (§3.4) ✅
- no authored values (§5) ✅

It requires the §A.4 schema extension (`damage` / `cooldown` / `fizzle` / `range` alongside the
per-school map), because elder's `×0.95` cooldown and rowan's `−0.02` fizzle have nowhere else to
land. That is a codec change, not a balance change.

**Known cost:** `spell_modifiers` stays dead data until ruling 2 lands. If it never does, the mod
carries an unconsumed field indefinitely — a smaller problem than today's (two disagreeing systems),
but a real one worth naming.

**Known limit:** this does **not** delete `WandWood`. The §7 persistence stop-trigger
(`wand_wood_legacy` is a `.persistent()` component) is untouched and still needs its own migration
decision. So this is Phases 1+3 of the brief, not Phase 2.

Nothing from §A.6 has been implemented — awaiting go/no-go.

---

# Committed alongside this audit

`3bc54466` — `fix(wand): let a Thestral core reach the cast it was cut out of`

Independent of every open question above. `WandCore` said `thestral_tail`; the item, the definition,
all 10 wandmaking recipes and the lang key say `thestral_tail_hair`. Wands store the item's id, so
`WandCore.byName()` returned null and Thestral wands silently lost `+0.20` dark-arts damage and
`+0.02` fizzle. Fixed by aliasing the id (not renaming the constant — `getSerializedName()` backs the
persistent `wand_core_legacy` codec). Added `WandIdParityTest`, which was verified to fail without
the fix and pass with it. `./gradlew build` green at that SHA.

