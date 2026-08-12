# TOOLTIP_AUDIT.md — Wand Tooltip Presentation, Phase 0 (read-only)

Date: 2026-08-02
HEAD: `d0313d96` (descendant of `4fa18276`) — prerequisite satisfied.
Baseline: `./gradlew build` exit code **0** (green) before any inspection.
Code written this phase: **none**.

---

## 0. Executive summary

The tooltip is produced by **three independent emitters** that do not know about each
other. Nothing in the mod owns "the wand tooltip" as a single unit — which is the root
cause of every observed defect:

| # | Emitter | Side | Gate | Position |
|---|---------|------|------|----------|
| A | `WandItem.appendHoverText` ([WandItem.java:137-162](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L137-L162)) | **common** | none — unconditional | item-level, first |
| B | `ItemDescriptionTooltipHandler` ([ItemDescriptionTooltipHandler.java:22-36](src/main/java/at/koopro/wizardsandbeasts/client/ItemDescriptionTooltipHandler.java#L22-L36)) | client | `.desc` key exists | `ItemTooltipEvent`, after A |
| C | `WandEligibilityTooltipHandler` ([WandEligibilityTooltipHandler.java:32-75](src/main/java/at/koopro/wizardsandbeasts/client/WandEligibilityTooltipHandler.java#L32-L75)) | client | status always; details on Shift | `ItemTooltipEvent`, after B |
| (D) | `DebugTooltipHandler` ([DebugTooltipHandler.java:22-52](src/main/java/at/koopro/wizardsandbeasts/client/DebugTooltipHandler.java#L22-L52)) | client | F3+H advanced only | last; not in the observation |

Observed line-for-line attribution:

```
Wand                                       ← vanilla item name (item.wizards_and_beasts.wand)
Wood: Yew                                  ← A
Core: Unicorn Hair                         ← A
Flexibility: rigid                         ← A
Master: 380df991-…                         ← A
Integrity: 1.0                             ← A
Corruption: 0.0                            ← A
Length: 11.746094 in                       ← A
Allegiance: 1.0                            ← A
Channel and cast your selected spell.      ← B  (item.wizards_and_beasts.wand.desc)
✓ You can use this wand                    ← C  (wandcraft.eligibility.can_use)
—————— + Allegiance/Bond/Integrity         ← C, Shift-gated
```

---

## 1. Shift-tier mechanics — how it is implemented, and why it appends

**It exists, in exactly one place: emitter C.**

[WandEligibilityTooltipHandler.java:77-81](src/main/java/at/koopro/wizardsandbeasts/client/WandEligibilityTooltipHandler.java#L77-L81):

```java
private static boolean isShiftDown() {
    var window = Minecraft.getInstance().getWindow();
    return InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
}
```

When true it appends a `——————` divider (a `Component.literal`, hardcoded) plus up to
three `Component`s carried on `WandEligibility.Result`: `detailLine1` (allegiance),
`detailLine2` (bond), `detailLine3` (integrity). If all three are null it emits
`wandcraft.eligibility.no_data`. In practice all three are always non-null —
[WandEligibility.java:52-57](src/main/java/at/koopro/wizardsandbeasts/wand/WandEligibility.java#L52-L57) always constructs them — so the `no_data`
branch is dead code today.

**Why it appends rather than gates: yes, the unshifted lines come from a different code
path.** The eight debug-tier lines are emitted by **A**, a common-side `Item` override
that runs inside `ItemStack.getTooltipLines()` before `ItemTooltipEvent` is even fired.
**C** is a client-only event subscriber that can only see `event.getToolTip()` as an
opaque `List<Component>` — it has no handle on which entries A contributed, and no way to
suppress them. A shift gate in C is therefore structurally incapable of hiding A's output.
A never consults shift state at all, and cannot: it is common code, and
`Minecraft.getInstance()` there would crash a dedicated server.

**Consequence for Phase 1:** the re-tier cannot be done inside C. Either A learns the
shift state through `ClientClassBridge` (the existing dist-safety idiom,
[ClientClassBridge.java](src/main/java/at/koopro/wizardsandbeasts/util/ClientClassBridge.java) — already used by `WandItem` itself at lines 54-58 and 124-126), or A's
detail lines move into a client-side emitter. This is an implementation choice, not a
scope question; it is noted here because it is the non-obvious part of Phase 1.

**Ordering constraint:** A's output always precedes C's. A "hold Shift" hint added in A
would render *above* the description and usability lines, not at the bottom where vanilla
puts it. The hint therefore belongs in a client-side emitter.

---

## 2. Duplication source — same value or different?

**Both pairs are pure display duplication. They read the identical component through the
identical accessor. No substantive divergence. No stop-trigger.**

### Allegiance

| Emitter | Call site | Expression |
|---|---|---|
| A | [WandItem.java:160](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L160) | `WandComponents.getAllegianceScore(stack)` → raw float |
| C | [WandEligibility.java:52-53](src/main/java/at/koopro/wizardsandbeasts/wand/WandEligibility.java#L52-L53) | `Math.round(WandComponents.getAllegianceScore(stack) * 100f)` |

### Integrity

| Emitter | Call site | Expression |
|---|---|---|
| A | [WandItem.java:153](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L153) | `WandComponents.getIntegrity(stack)` → raw float |
| C | [WandEligibility.java:54-55](src/main/java/at/koopro/wizardsandbeasts/wand/WandEligibility.java#L54-L55) | `Math.round(WandComponents.getIntegrity(stack) * 100f)` |

Both accessors are single-expression reads of one data component with one default
([WandComponents.java:102-112](src/main/java/at/koopro/wizardsandbeasts/wand/WandComponents.java#L102-L112)):

```java
public static float getAllegianceScore(ItemStack stack) { return stack.getOrDefault(WAND_ALLEGIANCE_SCORE.get(), 1.0f); }
public static float getIntegrity(ItemStack stack)      { return stack.getOrDefault(WAND_INTEGRITY.get(), 1.0f); }
```

There is no second source, no cached copy, and no divergent default. `1.0` and `100%` are
the same number twice. §3.2 (delete A's emitter, keep C's percentage form) is safe.

**Note on C's third consumer.** `WandEligibility.Result` is also read by the character
sheet, [AttributesTab.java:202](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L202). Any change to `Result`'s shape or line contents
lands there too. Deleting A's duplicate lines does not touch `Result`, so Phase 2 as
specified has no character-sheet blast radius. (`AttributesTab` separately renders its own
Wood/Core/Flexibility/Length/Integrity/Allegiance panel at lines 169-187 with hardcoded
English labels and `String.format` — a *fourth* rendering of the same data, out of scope
for this prompt but logged in §9.)

---

## 3. Breakdown absence — confirmed, and what would produce it

**Confirmed absent.** `WandStats`, `WandStatsResolver`, `WandCastModifiers` and
`SpellCategory` appear nowhere in any tooltip path. Grep for `WandStatsResolver` outside
the cast pipeline returns no tooltip or GUI consumer. Yew's `dark_arts: 0.05` is loaded,
resolved and applied on cast, and displayed nowhere.

### The call needed

```java
WandStats stats = WandStatsResolver.resolve(stack, context.registries());
Map<SpellCategory, Float> bonus = stats.categoryDamageBonus();   // additive, e.g. 0.05
float global = stats.damageMultiplier();                          // multiplicative
```

`WandStatsResolver.resolve` is `public static`, in a common package, with **no client-only
and no server-only dependency**. Its only external input is
`HolderLookup.@Nullable Provider registries`, which `Item.TooltipContext.registries()`
supplies — and it already tolerates `null` by contract
([WandStatsResolver.java:56-60](src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStatsResolver.java#L56-L60): *"When `null` the wood contributes nothing"*).

### Is the data client-side?

**Yes.** [WandDatapackRegistries.java:20-24](src/main/java/at/koopro/wizardsandbeasts/wand/registry/WandDatapackRegistries.java#L20-L24) registers the wood registry with the
**two-codec** `dataPackRegistry(key, codec, networkCodec)` overload:

```java
event.dataPackRegistry(WAND_WOOD_REGISTRY, WandWoodDefinition.CODEC, WandWoodDefinition.CODEC);
```

The second argument is the network codec, so `wand_woods` is **synced to the client**.
`WandLoreNames` already relies on this every tooltip frame to print "Yew" instead of
`wizards_and_beasts:yew`, and the live observation confirms it resolves. Core
contributions need no registry at all — they come from a hardcoded enum switch
([WandStatsResolver.java:83-122](src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStatsResolver.java#L83-L122)); `WandCoreDefinition` carries no cast modifiers
whatsoever. Length and flexibility likewise resolve from stack components through
hardcoded switches.

**No stop-condition fires.** No new packet, no new sync, no server round-trip.

### Semantics the display must respect (read, never compute — §3.7)

`categoryDamageBonus` is **additive**, not a multiplier
([WandStats.java](src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStats.java)): `0.05` means +5%. The resolved per-category multiplier the cast
path actually applies is:

```java
public float damageFor(Spell spell) {
    float bonus = categoryDamageBonus.getOrDefault(spell.getCategory(), 0.0f);
    return damageMultiplier * (1.0f + bonus);
}
```

Two consequences the brief's wording does not settle, flagged rather than decided:

1. **`damageMultiplier` is global.** Elder wood contributes `damage: 1.05` with *no*
   category bonus; Dragon Heartstring contributes `mulDamage(1.15f)`. Under `damageFor`,
   such a wand's resolved multiplier is ≠ 1.0 for **all four** categories, so §3.6's
   "omit categories at exactly 1.0" would list four lines rather than none. Reading only
   the additive `categoryDamageBonus` map instead would show *nothing* for an Elder wand
   even though it demonstrably buffs every cast. Both readings are defensible; both are
   "read, not compute". **Needs a ruling** — see §10 Q1.
2. **Only damage is categorised.** `cooldownFor`/`rangeFor` ignore category entirely. A
   per-category breakdown can only ever describe damage.

### Live data (all 10 shipped woods)

| Wood | `cast_modifiers` | Category bonus | Global |
|---|---|---|---|
| yew | yes | `dark_arts: 0.05` | — |
| holly | yes | `combat: 0.05` | — |
| rowan | yes | `defense: 0.05` | `fizzle -0.02` |
| elder | yes | — | `damage 1.05`, `cooldown 0.95` |
| ash, blackthorn, hawthorn, vine, walnut, willow | **no** | — | — |

6 of 10 woods have no `cast_modifiers` block at all → `WandCastModifiers.NEUTRAL`. Walnut
and vine are both in that set, so both serve as the §7 "no affinity" verification case as
the brief expects. Note **rowan** carries a `fizzle` term with no category — under Q1
option (b) it would render as `DEFENSE 1.05` and the fizzle change would be invisible;
fizzle is not categorised at all, so no reading of §3.6 can surface it. **Yew + Unicorn Hair** (the observed wand) resolves to
`DARK_ARTS +0.05` (yew) and `DEFENSE +0.10` (unicorn hair,
[WandStatsResolver.java:92-95](src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStatsResolver.java#L92-L95)) — i.e. the observed wand should show **two** breakdown
lines, not one. The prompt's §7 expectation ("Yew wand shows DARK_ARTS above 1.0") holds
and is a subset.

`spell_modifiers` (`dark_arts: 0.2`, `dueling: 0.12` on yew) is the *other*, unwired
vocabulary — explicitly documented as having no `SpellCategory` counterpart and no
consumer ([WandCastModifiers.java:12-16](src/main/java/at/koopro/wizardsandbeasts/wand/registry/WandCastModifiers.java#L12-L16)). Out of scope per §5; not read by the
breakdown.

---

## 4. Established pattern — one exists

**Exactly one shift-to-expand implementation exists in the mod:**
`WandEligibilityTooltipHandler.isShiftDown()` (§1). Repo-wide grep for `hasShiftDown`
returns **zero** hits; every other `isShiftKeyDown()` hit is a *player-entity* sneak check
in gameplay code (Portkey, Deluminator, Niffler, Broom, …), not a tooltip modifier-key
read. There is no competing convention.

**No conflicting patterns → no stop-trigger.** Phase 1 reuses `isShiftDown()` verbatim as
the reference implementation. Note it reads the raw GLFW key state, not
`Screen.hasShiftDown()`; both work for tooltips, and switching would be an unrequested
change.

There is **no existing "hold Shift" hint** anywhere: grep of `en_us.json` for `shift` /
`Shift` returns only unrelated prose ("allegiance has shifted", "shape-shifter"). §3.1's
hint line needs a new lang key. Vanilla's nearest convention is
`container.shulkerBox.more` — a single italic dark-gray line, last.

---

## 5. Field inventory

**Emitter A — `WandItem.appendHoverText`, all unconditional today:**

| Line | Source | Key | Tier per §3.1 |
|---|---|---|---|
| `Wood: %s` | `WandComponents.getWood` → `WandLoreNames.wood(registries, id)` | `wandcraft.tooltip.wood` | **always** |
| `Core: %s` | `WandComponents.getCore` → `WandLoreNames.core(registries, id)` | `wandcraft.tooltip.core` | **always** |
| `Flexibility: %s` | `WandComponents.getFlexibility` → `getSerializedName()`; `"?"` when null | `wandcraft.tooltip.flexibility` | shift |
| `Master: %s` | `WandComponents.getMaster` → `UUID.toString()` | `wandcraft.tooltip.master` | shift |
| `Integrity: %s` | `WandComponents.getIntegrity` (raw float) | `wandcraft.tooltip.integrity` | shift — **and dedupe** |
| `Corruption: %s` | `WandComponents.getCorruption` (raw float) | `wandcraft.tooltip.corruption` | shift |
| `Length: %s in` | `WandComponents.getLength` (raw float, nullable → `0.0f`) | `wandcraft.tooltip.length_in` | shift — **and reformat** |
| `Allegiance: %s` | `WandComponents.getAllegianceScore` (raw float) | `wandcraft.tooltip.allegiance` | shift — **and dedupe** |

Conditional lines not visible in the observation:

- **`Master`** is emitted only when `master.isPresent()` ([WandItem.java:149](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L149)). An
  unbonded wand shows no Master line at all.
- **`Flexibility: ?`** — literal `"?"` when the component is absent, a hardcoded fallback
  inside an otherwise localized line.
- **`Length: 0.0 in`** — the null length falls back to `0.0f` and prints as a real
  measurement rather than an absence marker. Pre-existing; the quarter-inch rounding in
  §3.5 must not turn this into a plausible-looking `0 in`.
- **Enchantment glint** — `isFoil` returns true iff a master is set ([WandItem.java:132-134](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L132-L134)).
  Visual only, not a tooltip line; noted so it is not mistaken for a duplicate bond signal.

**Emitter B:** one line, `item.wizards_and_beasts.wand.desc` = *"Channel and cast your
selected spell."* — the §3.1 action hint. Gray + italic. Gated on `I18n.exists`.

**Emitter C:**

| Line | Key | Gate |
|---|---|---|
| `✔ You can use this wand` | `wandcraft.eligibility.can_use` | eligible |
| `✗ You cannot use this wand` | `wandcraft.eligibility.cannot_use` | not eligible |
| `  <reason>` | `…reason.not_attuned` / `…reason.wrong_master` | not eligible, reason non-null |
| `? Eligibility unknown` | `wandcraft.eligibility.unknown` | `event.getEntity() == null` (creative menu / JEI) |
| `——————` | **hardcoded literal** | Shift |
| `Allegiance: %s%%` | `wandcraft.eligibility.detail.allegiance` | Shift |
| `Bond: …` (3 variants) | `…detail.bond.you` / `.none` / `.other` | Shift |
| `Integrity: %s%%` | `wandcraft.eligibility.detail.integrity` | Shift |
| `No additional data` | `wandcraft.eligibility.no_data` | Shift + all details null — **dead today** |

Note the lang file ships `✔`/`✗` while the live observation shows `✓` — cosmetic, and the
observation was likely transcribed. Not a defect; flagged so it is not "fixed" blindly.

**Emitter D** (`DebugTooltipHandler`) dumps every data component under F3+H with hardcoded
`[Data Components]` labelling. This is the *legitimate* debug tier and already exists —
which strengthens the case that A's raw floats have no reason to be unconditional.

---

## 6. Master field — storage and client-side name resolution

**Storage:** `WAND_MASTER`, a `DataComponentType<Optional<UUID>>`
([WandComponents.java:41-45](src/main/java/at/koopro/wizardsandbeasts/wand/WandComponents.java#L41-L45)), persistent via `UUIDUtil.CODEC` and
`networkSynchronized(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC))`. **Only the UUID is
stored — no name, ever.**

**Client-side resolution: partially available, with a hard limit.**

The mod contains **no** existing UUID→name resolution: repo-wide grep for `getPlayerInfo`,
`PlayerInfo` and `getProfile()` across `src/main/java` returns **zero** hits. This would be
the first.

The available client-side routes, both without a packet:

1. `Minecraft.getInstance().getConnection().getPlayerInfo(uuid)` → `PlayerInfo` →
   `getProfile().getName()`. Backed by the tab-list roster, so it resolves **only players
   currently connected to the same server**.
2. `Minecraft.getInstance().level.getPlayerByUUID(uuid)` → loaded entities only — strictly
   narrower than (1).

**Therefore: an offline owner's name is not resolvable client-side.** This is not a
transient cache miss — the name genuinely is not present anywhere on the client for a wand
whose master is not logged in. Single-player self-owned wands and same-server owners
resolve; everything else must fall back. That is exactly the case §3.4 anticipates
("If the name cannot be resolved client-side, show a localized 'unknown' placeholder"), so
**no stop-condition fires** — no packet is needed, because the ruling already accepts the
fallback.

**Precedent for the alternative, not taken:** `DeluminatorItem` solves the identical
problem by caching the bound player's *name* at bind time in `CUSTOM_DATA`
([DeluminatorItem.java:93-98](src/main/java/at/koopro/wizardsandbeasts/item/deluminator/DeluminatorItem.java#L93-L98), key `TAG_GUIDED_PLAYER_NAME`). Applying that to wands would
make every owner name resolvable offline — but it writes new stored data at bond time,
which is a value change and out of scope under §5. Logged in §9 for a future ruling, not
proposed for this pass.

**Dist-safety constraint:** `Minecraft.getInstance()` cannot be reached from
`WandItem.appendHoverText` (common code). Same structural constraint as §1 — resolution
happens client-side or through `ClientClassBridge`.

---

## 7. Length field — origin, precision, and numeric consumers

**Origin.** `WAND_LENGTH`, a `DataComponentType<Float>` holding **inches**
([WandComponents.java:71-75](src/main/java/at/koopro/wizardsandbeasts/wand/WandComponents.java#L71-L75)). Full `float` precision — hence the observed
`11.746094`. Writers:

| Writer | Value |
|---|---|
| `WandmakingRecipe.java:66` | `lengthInches` from the recipe — arbitrary float, the source of `11.746094` |
| `OllivanderTrialMenu.java:116,171` | `11.0f`; and a computed `len` |
| `WandItem.createWand` (lines 175-193) | quantised: SHORT 9.0 / MEDIUM 11.0 / LONG 15.0 / STANDARD 13.0 |

### ⚠ STOP-TRIGGER FIRED — the length value **is** consumed numerically by gameplay code

§2's stop-trigger list includes *"The length value is consumed numerically by gameplay code
anywhere."* It is, in **three** places:

1. **[WandStatsResolver.java:207-220](src/main/java/at/koopro/wizardsandbeasts/wand/cast/WandStatsResolver.java#L207-L220)** — `resolveLength` bins the float into a
   `WandLength` enum that drives range/damage/cooldown on every cast:
   ```java
   if (inches <= 10.0f) return WandLength.SHORT;
   if (inches <= 12.0f) return WandLength.MEDIUM;
   if (inches >= 14.0f) return WandLength.LONG;
   return WandLength.STANDARD;
   ```
2. **[Compatibility.java:144-157](src/main/java/at/koopro/wizardsandbeasts/wand/cast/Compatibility.java#L144-L157)** — a byte-identical second copy of the same binning.
3. **[WandResonanceSystem.java:69-71](src/main/java/at/koopro/wizardsandbeasts/wand/resonance/WandResonanceSystem.java#L69-L71)** — feeds the raw float into the resonance
   cache key (`len`, default `11.0f`), i.e. bond scoring.

**Assessment, for the record:** §3.5 is display-only and does not write the component, so a
quarter-inch *rendering* cannot reach any of these three. The trigger is reported because
the prompt requires it, and because it makes one boundary sharp:

- `11.746094` → **11¾ in** displayed, while `resolveLength` bins it **MEDIUM** (≤ 12.0).
- A wand stored at `11.9` would display **12 in** but still bin **MEDIUM**; a wand stored
  at `12.1` displays **12 in** and bins **STANDARD**. **Two wands can display the same
  length and behave differently.** Rounding does not cause this — the underlying binning
  does — but rounding makes it *visible* for the first time.

This is inherent to any rounding of a continuously-stored value against hard thresholds. It
is not a reason not to round, and §3.5 is a fixed ruling. Flagged so the decision is
informed, and logged in §9. Nothing here is proposed to be changed.

**Also noted, not in scope:** `resolveLength` is duplicated verbatim between
`WandStatsResolver` and `Compatibility`. §9.

---

## 8. Lang key coverage

| Line | Localized? |
|---|---|
| A: Wood, Core, Flexibility, Master, Integrity, Corruption, Length, Allegiance | **key** (8 keys, `wandcraft.tooltip.*`) |
| A: `"?"` flexibility fallback | **hardcoded** inside a localized line |
| B: description | **key** |
| C: status / reason / bond / allegiance / integrity / unknown / no_data | **key** (11 keys, `wandcraft.eligibility.*`) |
| C: `——————` divider | **hardcoded literal** |
| C: two-space indent prefixes | **hardcoded** |
| D: `[Data Components]`, `(none)`, `  id = value` | **hardcoded** (debug tier — acceptable) |

All 19 player-facing keys exist in `src/main/resources/.../lang/en_us.json` (lines
645-664). **None** are in `src/generated/.../lang/en_us.json` — they are hand-authored, and
per the merge contract main wins anyway.

### ⚠ Defect found in the existing keys: printf float specifiers

```json
"wandcraft.tooltip.integrity":  "Integrity: %.2f",
"wandcraft.tooltip.corruption": "Corruption: %.2f",
"wandcraft.tooltip.length_in":  "Length: %.1f in",
"wandcraft.tooltip.allegiance": "Allegiance: %.2f",
```

`TranslatableContents` is **not** `String.format`; its format grammar supports `%s`, `%n$s`
and `%%` only. The live observation is decisive: `%.2f` against `1.0f` rendered
**`Integrity: 1.0`**, and `%.1f` against `11.746094f` rendered
**`Length: 11.746094 in`** — the precision directives were silently ignored and the boxed
float was stringified whole. The four `%.Nf` templates have never once done what they
appear to say.

**Implication for Phase 2:** number formatting must happen **in Java**, with the lang keys
using plain `%s`. Formatting cannot be delegated to the lang file. `WandEligibility`
already does this correctly — it passes a pre-rounded `Math.round(...)` int into a `%s%%`
template, which is why the shifted block shows a clean `100%`.

### Test constraint on new keys

[LangParityTest.java](src/test/java/at/koopro/wizardsandbeasts/lang/LangParityTest.java) enforces, at build time:

- `everyReferencedKeyExists` — every literal `translatable("…")` containing the modid must
  exist in merged en_us. **Every new key must be added to `en_us.json` in the same commit.**
- `deDeAddsNoKeyEnUsDoesNotHave` — `de_de` may omit keys (falls back), must not add them.
- `deDeHasNoEmptyValues` — omit rather than blank.

New keys in `en_us.json` only will keep the suite green. No test needs weakening.

---

## 9. Out-of-scope observations (for AUDIT_PUNCHLIST.md, not this pass)

1. **A fourth rendering of the same data.** `AttributesTab.drawWandPanel`
   ([AttributesTab.java:169-187](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L169-L187)) draws Wood/Core/Flexibility/Length/Integrity/Allegiance
   with **hardcoded English labels** (`"Wand"`, `"Wood"`, …) and `String.format("%.1f\"")`.
   It will disagree with the fixed tooltip on length format, and is unlocalized. §5 forbids
   touching it here (tooltip only).
2. **`resolveLength` duplicated verbatim** between `WandStatsResolver` and `Compatibility`.
   Two copies of a gameplay threshold table.
3. **Display-vs-binning boundary** (§7) — displayed length and cast behaviour can diverge
   for stored values near 10.0/12.0/14.0.
4. **`wandcraft.eligibility.no_data` is dead code** — unreachable, since `Result` always
   populates all three detail lines.
5. **Deluminator-style name caching** (§6) would make owner names resolvable offline;
   requires a stored-data change, hence a separate ruling.
6. **`Flexibility: ?`** hardcoded fallback and **`Length: 0.0 in`** null-as-zero, both
   pre-existing.
7. **`spell_modifiers` still unwired** — unchanged, awaiting the magnitude ruling (§5).

---

## 10. Open questions requiring a ruling before Phase 3

**Q1 — which number is "the resolved multiplier" for a category?** (§3, per §3.6/§3.7)

| Option | Yew+Unicorn shows | Elder+Dragon Heartstring shows | Matches `damageFor`? |
|---|---|---|---|
| **(a)** `damageMultiplier × (1 + bonus)` — exactly `damageFor` | `DARK_ARTS 1.05`, `DEFENSE 1.10` | all four at `1.21`, none omitted | yes, exactly |
| **(b)** `1 + bonus` — the category-specific part only | `DARK_ARTS 1.05`, `DEFENSE 1.10` | **"no affinity"**, despite a real +21% | no — hides the global term |

Both are pure reads. (a) is literally what a cast applies and never lies; (b) reads as an
"affinity" list but is silent about a wand that buffs everything. They agree on the
observed wand — the §7 verification cases do not discriminate between them.

**Recommendation: (a)**, because §3.7 is unambiguous that the tooltip must show what the
resolver produces, and (b) would show "no affinity" for an Elder wand that measurably is
not neutral. Under (a), §3.6's "omit at exactly 1.0" is honoured literally: nothing is
omitted for a globally-buffing wand *because nothing about it is 1.0*.

**Q2 — length rounding boundary.** §3.5 says "nearest quarter inch". Confirming: `11.746094`
→ **11¾**, `11.875` → **12** (ties-away-from-zero via `Math.round`), rendering whole
numbers without a fraction glyph (`12 in`, not `12 0/4 in`). Fractions rendered as `¼ ½ ¾`
(U+00BC/BD/BE) in the lang value, not ASCII. No ruling needed unless this reading is wrong.

---

## 11. Stop-trigger status

| Phase 0 trigger | Fired? |
|---|---|
| Duplicated Allegiance/Integrity read different values | **No** — §2, identical accessor, identical component |
| Owner name resolution needs a packet / round-trip | **No** — §6, client roster + accepted fallback |
| Length consumed numerically by gameplay code | **YES** — §7, three call sites. Reported. Display-only change cannot reach them; boundary caveat documented |
| Two conflicting shift-detail patterns already exist | **No** — §4, exactly one |

| §6 absolute stop-condition | Status |
|---|---|
| Tooltip values disagree with what a cast applies | Not yet observable — the breakdown does not exist. Q1 decides which number is shown; option (a) is disagreement-proof by construction |
| Breakdown needs resolution logic not available client-side | **No** — §3, `WandStatsResolver.resolve` is common code over a network-synced registry |
| Authoring flavour text or a canon value | **No** — every new string is a structural label |
| Weakening a test to get green | **No** — §8, adding keys to `en_us.json` keeps `LangParityTest` green |

---

## 12. STOP AND REPORT

Phase 0 complete. No code written. Awaiting:

1. Acknowledgement of the **fired length stop-trigger** (§7) — display-only rounding is
   believed safe; the display-vs-binning boundary is the thing to be aware of.
2. A ruling on **Q1** (§10), which determines what the Phase 3 breakdown prints.
3. Confirmation of **Q2** (§10) if the rounding reading is wrong.

Phases 1-4 are otherwise unblocked: the client-side data is present, the shift pattern is
established and unambiguous, the duplication is safe to collapse, and the lang test imposes
no obstacle.
