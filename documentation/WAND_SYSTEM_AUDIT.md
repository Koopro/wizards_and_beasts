# Wand System Audit — tooltip + core migration scoping

**Date:** 2026-08-19 · **HEAD:** `0e546a33` · **Working tree:** clean (0 dirty)
**Scope:** wand stat resolution, datapack definition → resolver → cast pipeline → player surface.
**Constraint:** read-only. No `src/` file was modified.

---

## 1. Verdict

| Candidate | Verdict | The single fact that drives it |
|---|---|---|
| **A** — surface resolved cast stats in the wand tooltip | **CLEAR** | Both definition registries are datapack registries declared with a **non-null network codec**, so they are already synced to clients; `appendHoverText` already reads them via `context.registries()`. |
| **B** — author wood `cast_modifiers`, migrate cores off the enum switch | **CLEAR WITH CAVEATS** | The `WandCore` enum is **never written** to persistent storage by any live code path — every write site stores an `Identifier`. The migration is a refactor, not a save migration. Caveats are two orphan enum cores needing a ruling. |

Neither stop-condition in §6 of the brief fired. The two "expected possible outcomes" — save-breaking serialization, and definitions absent clientside — are **both false**.

---

## 2. Findings

### Q1 — Wood resolution path

**`applyWood()`** — `src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStatsResolver.java:126-136`

```java
private static void applyWood(WandStats.Builder b, @Nullable Identifier woodId,
                              HolderLookup.@Nullable Provider registries)
```

Body: returns early if either `woodId` or `registries` is null; resolves `WandCastModifiers` via
`castModifiersFor()`; returns early if neutral; then `mulDamage` / `mulCooldown` / `mulRange` /
`addFizzle`, and finally `mods.categoryDamageBonus().forEach(b::addCategoryDamageBonus)`.

**Codec shape.** `cast_modifiers` is **not** declared inline on `WandWoodDefinition` — it is a standalone
shared record, `WandCastModifiers` (`wand/registry/WandCastModifiers.java:21-39`). Every field is
optional and defaults to identity:

| field | type | optional | default |
|---|---|---|---|
| `damage` | float | yes | `1.0` |
| `cooldown` | float | yes | `1.0` |
| `range` | float | yes | `1.0` |
| `fizzle` | float | yes | `0.0` |
| `category_damage_bonus` | `Map<SpellCategory, Float>` | yes | `Map.of()` |

Attached at `WandWoodDefinition.java:33-34`:
`WandCastModifiers.CODEC.optionalFieldOf("cast_modifiers", WandCastModifiers.NEUTRAL)` — optional,
defaulting to `NEUTRAL`.

**Coverage — 4 of 10 woods carry the block.**

- **Has `cast_modifiers`:** `elder.json`, `holly.json`, `rowan.json`, `yew.json`
- **Does not:** `ash.json`, `blackthorn.json`, `hawthorn.json`, `vine.json`, `walnut.json`, `willow.json`

All under `src/main/resources/data/wizards_and_beasts/wizards_and_beasts/wand_woods/`. The four that
have it are exactly the four wandwood species the mod registers as trees/blocks; the six without are
the ones that exist only as wand material.

**Lookup miss.** `castModifiersFor()` — `WandStatsResolver.java:143-155`. Returns
`WandCastModifiers.NEUTRAL` (line 152). The warning is emitted at line 150:

```java
LOGGER.warn("Wand wood '{}' has no definition; it contributes nothing to casts.", woodId);
```

**Logged once per id per JVM run, not per cast** — guarded by `WARNED_MISSING_WOODS`, a
`ConcurrentHashMap.newKeySet()` at line 48. The javadoc at 44-47 states the reason: `resolve` runs once
per cast *and once per beam tick*, so an unguarded warning would flood the log for as long as the wand
is held. The guard is deliberate and correct.

**`spell_modifiers` — confirmed unread at runtime.** Four references exist in the whole source tree and
none is a read: `WandWoodDefinition.java:12` (javadoc), `:21` (record component), `:29` (codec), and
`WandCastModifiers.java:12` (javadoc cross-reference). Both javadocs state the reason — the schools it
is keyed by (`healing`, `divination`, …) have no `SpellCategory` counterpart and the mapping is an
unmade balance decision.

One thing the brief did not ask and is worth flagging: `spell_modifiers` is declared with
`fieldOf`, i.e. **required**, not optional (`WandWoodDefinition.java:29`). Every wood JSON must carry a
field that nothing reads. Logged to the punchlist.

---

### Q2 — Core resolution path

**The enum switch is `applyCore()`, inside the resolver** — `WandStatsResolver.java:83-122`. It is
symmetric with `applyWood()`, not a separate consulted-from-elsewhere table. The brief's framing that
these might be different shapes does not hold.

| constant | contribution |
|---|---|
| `PHOENIX_FEATHER` | `+0.15` COMBAT category damage; `×0.95` cooldown |
| `DRAGON_HEARTSTRING` | `×1.15` damage; `+0.05` fizzle |
| `UNICORN_HAIR` | `×1.10` range; `+0.10` DEFENSE; `−0.03` fizzle |
| `THESTRAL_TAIL` | `+0.20` DARK_ARTS; `+0.02` fizzle |
| `VEELA_HAIR` | `×0.92` cooldown; `+0.03` fizzle |
| `TROLL_WHISKER` | `×1.10` damage; `×1.08` cooldown; `+0.04` fizzle |
| `WAMPUS_CAT_HAIR` | `+0.12` COMBAT; `×0.97` cooldown |
| `THUNDERBIRD_TAIL_FEATHER` | `×1.15` range; `+0.08` DEFENSE; `+0.02` fizzle |
| `ROUGAROU_HAIR` | `+0.15` DARK_ARTS; `×1.05` damage; `+0.05` fizzle |
| `WHITE_RIVER_MONSTER_SPINE` | `×1.08` damage; `×1.08` range; `×1.05` cooldown |

**Core JSONs — 8 files**, under `data/wizards_and_beasts/wizards_and_beasts/wand_cores/`:
`dragon_heartstring`, `phoenix_feather`, `thestral_tail_hair`, `thunderbird_tail_feather`,
`troll_whisker`, `unicorn_hair`, `veela_hair`, `wampus_cat_hair`.

**`WandCoreDefinition` exists** — `wand/registry/WandCoreDefinition.java`, codec at lines 17-26. All
fields **required** (`fieldOf`, no defaults):

| field | type |
|---|---|
| `display_name` | `Component` |
| `source_key` | String |
| `raw_power` | float |
| `consistency` | float |
| `loyalty` | float |
| `dark_affinity` | float |
| `initiative` | float |
| `allegiance_transfer_resistance` | float |

**No `cast_modifiers` field — it would need adding.** The structure is **directly reusable with no
duplication**: `WandCastModifiers` is already a standalone shared record that `WandWoodDefinition`
merely *uses*. Adding cast stats to cores is one codec line plus one record component, identical in
shape to `WandWoodDefinition.java:33-34`.

**`applyCore()` exists** (see above), so Candidate B replaces a switch body with a registry lookup in a
method that is already there — it does not have to introduce the seam.

**Orphan enum cores — CONFIRMED.** Cross-checking the ten enum serialized names against the eight core
JSONs:

- Enum constants with **no** JSON: `rougarou_hair`, `white_river_monster_spine`, and `thestral_tail`
- JSON with no matching enum **serialized name**: `thestral_tail_hair`

So `ROUGAROU_HAIR` and `WHITE_RIVER_MONSTER_SPINE` are confirmed orphans. Neither is referenced by any
of the 80 wandmaking recipes either.

**`thestral_tail_hair` — REFUTED.** It **does** resolve to `THESTRAL_TAIL`, via an explicit alias at
`wand/stat/WandCore.java:39`:

```java
BY_NAME.put("thestral_tail_hair", THESTRAL_TAIL);
```

The comment at lines 33-38 records that this *was* a live bug — a Thestral wand resolved to `null` and
contributed no core modifier at all — and that aliasing was chosen over renaming because
`getSerializedName()` backs the persistent `wand_core_legacy` codec, so the stored `"thestral_tail"`
form still has to parse. The naming divergence remains (enum says `thestral_tail`, everything else says
`thestral_tail_hair`) but the resolution failure the brief implies is already fixed.

---

### Q3 — Serialization surface — **Candidate B is NOT save-breaking**

**Is the `WandCore` enum name written to persistent storage?** Yes, by a component that *exists* — but
**no live code path writes it.**

`ModDataComponents.java:45-50` registers component id **`wand_core_legacy`**:

```java
DATA_COMPONENTS.register("wand_core_legacy", () -> DataComponentType.<WandCore>builder()
        .persistent(WandCore.CODEC)
        .networkSynchronized(WandCore.STREAM_CODEC).build());
```

The same pattern for `wand_wood_legacy` (`:38-43`), `wand_length_legacy` (`:52-57`),
`wand_flexibility_legacy` (`:59-64`).

**Every reference to these four is a read.** A search for `.set(ModDataComponents.WAND_*)` across
`src/main/java` returns **nothing**. The eight references that exist are all `.get()`:

- `WandStatsResolver.java:185, 200, 208, 223`
- `Compatibility.java:119, 128, 137, 145`

**What a wand actually stores.** `WandComponents.java:29-33`, component id **`wand_core`**:

```java
DATA_COMPONENTS.register("wand_core", () -> DataComponentType.<Identifier>builder()
        .persistent(Identifier.CODEC)
        .networkSynchronized(Identifier.STREAM_CODEC).build());
```

Every write site uses this `Identifier` form:

- `wand/recipe/WandmakingRecipe.java:61-62`
- `wand/gui/OllivanderTrialMenu.java:113-114` and `:167-168`
- `item/wand/WandBlankItem.java:57`
- `item/wand/WandItem.java:166, 169`
- `integration/jei/WandmakingCategory.java:87`

**Upgrade path / precedent.** Not needed for Candidate B — but it exists anyway, and **wood already did
exactly this migration**. `WandStatsResolver.resolveWoodId()` (`:198-205`) reads the legacy enum
component first and converts it to an `Identifier`; the javadoc at `:193-197` states the rule
explicitly — *"The legacy enum component still wins over the modern `Identifier` one … dropping this
branch would silently make them neutral."* `resolveCore()` (`:184-191`) applies the same precedence for
cores today.

**Consequence for Candidate B:** the enum is a *resolution intermediate*, not a stored form. Replacing
the `applyCore()` switch with a datapack lookup changes nothing that reaches disk. `wand_core_legacy`
must be *kept* so pre-migration stacks still resolve, but it needs no new code.

---

### Q4 — Registry & client availability — **Candidate A is a display change**

**Loading mechanism: datapack registry**, not a reload listener.
`wand/registry/WandDatapackRegistries.java:20-24`, from `DataPackRegistryEvent.NewRegistry`:

```java
event.dataPackRegistry(WAND_WOOD_REGISTRY, WandWoodDefinition.CODEC, WandWoodDefinition.CODEC);
event.dataPackRegistry(WAND_CORE_REGISTRY, WandCoreDefinition.CODEC, WandCoreDefinition.CODEC);
```

**Synced to the client: yes, both.** The three-argument overload's third parameter is the network
codec — verified against the NeoForge source rather than assumed:

```java
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec,
                                 @Nullable Codec<T> networkCodec)
```

A non-null `networkCodec` is what makes a datapack registry sync; the two-arg overload passes `null`.
Both wand registries pass their `CODEC`, so both ship to clients. No new packet is required.

**Registry access inside `appendHoverText`:** available and already in use. The `TooltipContext`
parameter exposes `registries()` returning a `HolderLookup.Provider`, called at
`item/wand/WandItem.java:143` and `:145`.

**Precedent for a tooltip reading datapack definitions: yes — `wand/WandLoreNames.java`.** This is the
pattern to follow. It resolves both registries from a tooltip context:

```java
public static Component wood(HolderLookup.@Nullable Provider registries, @Nullable Identifier id)
public static Component core(HolderLookup.@Nullable Provider registries, @Nullable Identifier id)
```

Its javadoc (lines 23-28) documents the two fallbacks that matter here: **registry access is nullable on
a tooltip context**, and a definition can be missing outright. It title-cases the id rather than showing
a raw id or a bare translation key. Any cast-stat tooltip must handle the same two cases.

---

### Q5 — Current player-visible surface

**`WandItem.appendHoverText`** — `item/wand/WandItem.java:136-161`. Eight lines, in order:

| # | lang key | value | style |
|---|---|---|---|
| 1 | `wandcraft.tooltip.wood` | `WandLoreNames.wood(context.registries(), wood)` | GOLD |
| 2 | `wandcraft.tooltip.core` | `WandLoreNames.core(context.registries(), core)` | LIGHT_PURPLE |
| 3 | `wandcraft.tooltip.flexibility` | `flexibility.getSerializedName()` or `"?"` | GRAY |
| 4 | `wandcraft.tooltip.master` | master UUID — **only when present** | AQUA |
| 5 | `wandcraft.tooltip.integrity` | `WandComponents.getIntegrity(stack)` | GREEN |
| 6 | `wandcraft.tooltip.corruption` | `WandComponents.getCorruption(stack)` | DARK_RED |
| 7 | `wandcraft.tooltip.length_in` | `getLength(stack)` or `0.0f` | BLUE |
| 8 | `wandcraft.tooltip.allegiance` | `getAllegianceScore(stack)` | DARK_GREEN |

**No cast stats appear anywhere in the tooltip today.** It shows identity and condition, never
contribution — which is precisely the gap Candidate A closes.

**The debug command.** `spell/command/SpellCommands.java`, method `spellInfo` at `:153`; the wand block
is `:209-231`. Node is `/wandb magic spell info <spell>` (`:49`).

**Permission: none.** `.requires(WizardsAndBeastsCommandPermissions.ADMIN)` sits on the sibling nodes
`reset` (`:57`) and `learn_all` (`:60`) — **not** on `info`. It is player-accessible, contrary to the
brief's phrasing ("permission level").

Resolver calls made:

```java
WandStats wand = WandStatsResolver.resolve(wandStack, player.registryAccess());   // :211
float skillDamageMult   = SkillSystemAPI.getDamageMultiplier(player, spell);      // :212
float skillCooldownMult = SkillSystemAPI.getCooldownMultiplier(player, spell);    // :213
```

Values printed: `damageFor(spell)`, `cooldownFor(spell)`, `rangeFor(spell)`, `fizzleChance()` (only when
> 0), plus derived `effectiveDamage` and `effectiveCooldownTicks`.

**Reusable on the client?** The `resolve()` call is — it needs only an `ItemStack` and a
`HolderLookup.Provider`, both available in `appendHoverText`. **The derived numbers are not, and should
not be copied.** `effectiveDamage` at `:214` is

```java
spell.getBaseDamage() * skillDamageMult * wand.damageFor(spell)
```

which omits six modifier sources the real pipeline applies (wand corruption/integrity, foreign master,
Obscurial, vocation, Niffler happiness, player stats) **and bypasses the `ModifierStack` clamp
entirely**. A tooltip repeating that math would print a number the cast pipeline never produces. Logged
to the punchlist as a defect in the command itself, independent of Candidate A.

---

### Q6 — Cast pipeline integration

`spell/cast/SpellExecutor.java`, `executeGeneric(CastContext, ServerLevel)` from `:57`. Call order:

| # | line | modifier |
|---|---|---|
| 1 | `:62` | `wand_corruption_integrity` |
| 2 | `:65` | `wand_foreign_master` (only when the wand has a different master) |
| 3 | `:70` | `SkillSystemAPI.applyDamageModifiers` — **proficiency/skill** |
| 4 | `:71` | `SkillSystemAPI.applyCooldownModifiers` |
| 5 | **`:72`** | **`WandStatsResolver.applyToStack` — wand modifiers** |
| 6 | `:73` | `ObscurialCombatRules.applyCastModifiers` |
| 7 | `:74` | `VocationAbilityHooks.applyCastModifiers` |
| 8 | `:75` | `HappinessSpellPower.applyCastModifiers` |
| 9 | `:76` | `StatCastModifiers.applyCastModifiers` — **player stats** |
| 10 | `:82` | `dark_corruption_light_magic` |

So wand modifiers land **after** skill/proficiency and **before** player-stat modifiers. Since every
entry is a multiply into the same stack, order does not change the product — but it does change which
contribution is visible in a per-source breakdown, which matters if the tooltip ever attributes.

**Wand modifiers are inside the bounded stack.** `WandStatsResolver.applyToStack` (`:73-79`) calls
`stack.multiplyDamage(...)`, `stack.multiplyCooldown(...)` and `stack.addMisfireChance(...)`. Bounds are
`ModifierStack.java:13-14` — `HARD_CAP = 3.0f`, `HARD_FLOOR = 0.25f` — applied in `clamp()` at `:53-55`,
with misfire clamped to the unit range separately.

**`SpellCategory` constants — four** (`spell/core/SpellCategory.java:6-9`): `COMBAT`, `UTILITY`,
`DEFENSE`, `DARK_ARTS`. These are the only valid keys for `category_damage_bonus`.

---

### Q7 — Collision surface with the quarantine

**There is no quarantine. `git status --porcelain --untracked-files=all` returns zero entries.**

The tangle §2 describes was committed earlier in this same session, across seven commits
(`569be832` … `0e546a33`). Nothing is uncommitted, so no file in this audit's scope collides with
anything, and the read-only constraint was never at risk.

**Wandmaking recipes: 80, not ~24.** All committed. They key on full identifiers, not the enum:

```json
"wood_key": "wizards_and_beasts:ash",
"core_key": "wizards_and_beasts:dragon_heartstring"
```

**No dangling ids in either direction:**

- Woods referenced by recipes: 10 · defined: 10 · used-but-undefined: **none** · defined-but-unused: **none**
- Cores referenced by recipes: 8 · defined: 8 · used-but-undefined: **none** · defined-but-unused: **none**

Authoring `cast_modifiers` onto existing core JSONs therefore conflicts with nothing. **Renaming** a
core JSON would break recipes, since `core_key` is matched by identifier — relevant only if decision #2
below resolves toward renaming.

---

## 3. Corrections to the brief

Every point below is a place where the code contradicts §3 as written.

1. **§2 — the uncommitted tangle does not exist.** The tree is clean at `0e546a33`. The obscurus /
   horntail / basilisk files and the wandmaking recipes are all committed.
2. **§2 — "~24 wandmaking recipes" is wrong: there are 80.**
3. **Q2 — "the `WandCore` enum switch" is `WandStatsResolver.applyCore()`**, symmetric with
   `applyWood()` and in the same class. The brief's question "is there an `applyCore()` … or is the enum
   consulted from somewhere else" presents these as alternatives; the answer is the former.
4. **Q1/Q2 — `cast_modifiers` is not a field structure belonging to `WandWoodDefinition`.** It is a
   standalone shared record, `WandCastModifiers`, which `WandWoodDefinition` uses. So the brief's
   "shared record? duplicated?" resolves to *already shared, nothing to extract*.
5. **Q2 — the `thestral_tail_hair` claim is REFUTED.** It resolves correctly via the alias at
   `WandCore.java:39`. The naming divergence is real; the resolution failure is already fixed, and the
   fix is documented in place.
6. **Q5 — the debug command is not permission-gated.** `.requires(ADMIN)` is on its siblings, not on
   `info`.
7. **Q3/Q4 — both blocking questions resolve favourably.** The brief anticipates that either could turn
   Candidate A into a networking task or Candidate B into a save migration. Neither does.

**Methodology deviation (Upgrade License).** For Q4 I did not rely on the codebase or on documentation
to establish sync semantics — I extracted `DataPackRegistryEvent.java` from the NeoForge sources jar and
read the overload signatures directly, because "does a non-null third argument mean synced" is exactly
the kind of fact that is asserted confidently and wrongly. Same approach for confirming the legacy
components have no write sites: a negative grep across all of `src/main/java`, not an inspection of the
classes I expected to be involved.

---

## 4. Open design decisions — for Christian

Not answered here.

1. **`ROUGAROU_HAIR` and `WHITE_RIVER_MONSTER_SPINE`** — enum constants with no JSON, no recipe, and no
   way to obtain a wand carrying them. Author definitions, or retire them? Note retirement is not free:
   `WandCore.STREAM_CODEC` is ordinal-based, so removing a constant shifts every later constant's wire
   id and misreads legacy stacks.
2. **`thestral_tail` naming divergence.** Keep the alias indefinitely, or align the enum's serialized
   name to `thestral_tail_hair`? Aligning changes what `getSerializedName()` writes, which is the
   persistent `wand_core_legacy` form — so it needs a read-side alias regardless, i.e. the current state
   may already be the end state.
3. **Tooltip content — raw multipliers or derived values?** `1.15× damage, 0.95× cooldown` is honest and
   composable. "Effective damage: 7.4" is more useful and currently impossible to compute correctly in a
   tooltip (see Q5) without duplicating the whole pipeline.
4. **Tooltip visibility.** Always shown, only on Shift, or only under `TooltipFlag.isAdvanced()`? The
   tooltip is already eight lines before any cast stats are added.
5. **The six woods with no `cast_modifiers`.** Author them, or is neutral the intended balance for woods
   that exist only as wand material? The four that have the block are exactly the four that also exist
   as trees, which may be coincidence or may be the rule.
6. **`spell_modifiers`.** Required by the codec, read by nothing, keyed by schools with no
   `SpellCategory` counterpart. Map it, drop it, or leave it authored and unread?
7. **If cores migrate, what happens to `applyCore()`?** Delete the switch outright, or keep it as a
   fallback for cores with no definition — which is currently the only thing making the two orphan
   constants do anything at all.

---

## 5. Effort estimate

### Candidate A — tooltip cast stats · **CLEAR**

| | |
|---|---|
| Files touched | ~2 — `WandItem.appendHoverText`, `en_us.json`. Optionally a small `WandStatsLines` helper beside `WandLoreNames`. |
| New networking | **None.** Both registries already sync. |
| New codec | **None.** |
| Migration | **None.** |
| Pattern to follow | `wand/WandLoreNames.java` — including its nullable-registries and missing-definition fallbacks. |
| Risk | Low. The only trap is the derived-value math in `SpellCommands` (Q5), which must not be copied. |

Blocked only on decisions #3 and #4.

### Candidate B — core migration + wood authoring · **CLEAR WITH CAVEATS**

| | |
|---|---|
| Files touched | `WandCoreDefinition` (+1 record component, +1 codec line); 8 core JSONs; `WandStatsResolver.applyCore`; 6 wood JSONs if #5 says author them. |
| New networking | **None.** `WAND_CORE_REGISTRY` already syncs. |
| New codec | One field, reusing `WandCastModifiers.CODEC` verbatim — no new type. |
| Migration | **None.** The enum is never persisted by live code; `wand_core_legacy` stays for reading old stacks and needs no change. |
| Risk | Low mechanically. The caveat is entirely decisions #1 and #7 — the two orphan constants are the only cores whose behaviour lives *solely* in the switch, so deleting the switch silently drops them unless they are authored first. |

Blocked on decisions #1, #5 and #7.

---

*Read-only audit. No `src/` file was modified; no game was launched. Findings outside this scope were
appended to `AUDIT_PUNCHLIST.md` rather than fixed.*
