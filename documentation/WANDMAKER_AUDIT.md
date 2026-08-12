# Wandmaker Table Audit — Phase 0 (read-only)

**Date:** 2026-07-28 · **Branch:** `fix/chamber-of-secrets-module-gate` · **HEAD:** `469ac57`
**Scope:** diagnose Bug A (wand base not recognised) and Bug B (bench screen broken). No production
code was written for this document.

---

## 0. Prompt assumptions that did not hold

**The working tree is clean.** The brief (§1) describes a pre-staged index and a body of uncommitted
work — petrify/basilisk, `chamber_of_secrets`, ~12 beasts, ~24 wandmaking recipes — and forbids
touching it. None of that is uncommitted:

```
$ git status --porcelain=v2 --branch
# branch.oid 469ac575f0d46975ed60bce3dd647f315f890d26
# branch.head fix/chamber-of-secrets-module-gate
$ git diff --cached --name-only     # empty
$ git status --porcelain --untracked-files=all   # empty
```

All of that work is committed (see `3853744`, `fc2a908`, `b985057`, `469ac57`). Consequences:

- §1's "restore the pre-existing staged index" obligation is satisfied by leaving the index empty.
- Hypothesis **A1 becomes directly falsifiable** rather than a git-status question: the recipes are
  either in the repository or they are not. They are (§2 below).
- `./gradlew build` failing at `AssetModelParityTest` was **not** reproduced or investigated; it is
  out of scope either way (§1). No gradle task was run during Phase 0.

**"Wand base" is not an item in this mod.** No registered item, lang key, model, or datapack entry
uses that name. The registry holds exactly four wand-family items — `wand`, `wand_blank`,
`debug_wand`, `morph_wand` ([WandItemRegistry.java:19-30](src/main/java/at/koopro/wizardsandbeasts/registry/WandItemRegistry.java#L19-L30)) —
and `WandSlot` has no `BASE` constant, only `HANDLE/SHAFT/TIP/CORE/ORNAMENT`
([WandSlot.java:10-14](src/main/java/at/koopro/wizardsandbeasts/wand/customization/WandSlot.java#L10-L14)).
The audit therefore traced **both** items the bench consumes: the **wand blank** (`wand_blank`,
lang name "Wand Blank") and the **core materials**. See §2.4 — this ambiguity is a stop-and-report
item.

---

## 1. What the wandmaker table actually is

| Piece | File |
|---|---|
| Block | [WandmakersBenchBlock.java](src/main/java/at/koopro/wizardsandbeasts/wand/bench/WandmakersBenchBlock.java) |
| Block entity | [WandmakersBenchBlockEntity.java](src/main/java/at/koopro/wizardsandbeasts/wand/bench/WandmakersBenchBlockEntity.java) |
| Menu | [WandmakersBenchMenu.java](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java) |
| Screen | [WandmakersBenchScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java) |
| Recipe | [WandmakingRecipe.java](src/main/java/at/koopro/wizardsandbeasts/wand/recipe/WandmakingRecipe.java) · [serializer](src/main/java/at/koopro/wizardsandbeasts/wand/recipe/WandmakingRecipeSerializer.java) · [type](src/main/java/at/koopro/wizardsandbeasts/wand/recipe/WandmakingRecipeType.java) |
| Viewer | [WandmakingCategory.java](src/main/java/at/koopro/wizardsandbeasts/integration/jei/WandmakingCategory.java) |

Three slots, backed by `ItemStacksResourceHandler(3)` on the block entity: blank (0) @ 44,35 ·
core (1) @ 80,35 · output (2) @ 134,35, then 36 player slots
([WandmakersBenchMenu.java:85-97](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L85-L97)).

Acceptance is decided by two `mayPlace` predicates, nothing else — no `Ingredient`, no tag, no
`ItemStack.matches`, no datapack option list:

- `WandBlankSlot.mayPlace` → `stack.getItem() instanceof WandBlankItem`
  ([:298-300](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L298-L300))
- `CoreSlot.mayPlace` → `WandCoreMaterialItem.isBenchCore(stack)`
  ([:308-311](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L308-L311))

The recipe is looked up **after** both slots are filled, by `(wood_key, core_key)` equality
([:226-237](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L226-L237)),
and the failure is reported through a synced status int, not by rejecting the item
([:172-224](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L172-L224)).

### Module gate (§7)

The constant is **`Module.WANDS`**, checked once, in
[WandmakersBenchBlock.java:48-50](src/main/java/at/koopro/wizardsandbeasts/wand/bench/WandmakersBenchBlock.java#L48-L50)
(`WandModuleHooks.isWandsEnabled()` → `ModuleManager.isEnabled(Module.WANDS)`, true for `ENABLED`
*or* `PREVIEW`). That gates **access** — the menu does not open — and nothing else. Block, item,
menu type, recipe type and serializer all register unconditionally. `ModuleDefaults` ships
`WANDS = ENABLED` ([ModuleDefaults.java:24](src/main/java/at/koopro/wizardsandbeasts/module/ModuleDefaults.java#L24)),
and `ModuleManager`'s cache is pre-seeded from those defaults, so the gate is open before any world
loads. **Not a cause of either bug.**

One deviation from the standing rule, reported not fixed: all 30 recipe JSONs carry
`"neoforge:conditions": [{"type": "wizards_and_beasts:module_enabled", "module": "WANDS"}]`, which
conditions *recipe loading* on module state rather than access. Harmless today (WANDS is ENABLED),
but it is registration-gating. → punchlist.

---

## 2. Bug A — base not recognised

### 2.1 Hypotheses ruled out

**A1 — recipe missing / uncommitted. RULED OUT.** 30 wandmaking recipes exist, all tracked, all in
the 1.21.11-correct `data/wizards_and_beasts/recipe/` directory (the old `wandmaking_recipes/`
misplacement is AUD-D-001, already closed). They cover 10 woods × 3 cores. The 10 woods are exactly
the 10 `WandBlankItem.wandWoodFromLogBlock` can stamp — rowan, holly, hawthorn, walnut, ash, yew,
willow, blackthorn, elder, vine
([WandBlankItem.java:59-97](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandBlankItem.java#L59-L97)) —
and the three core keys are exactly the three `WandCoreMaterialItem`s. Key strings match verbatim
(`wizards_and_beasts:rowan`, `wizards_and_beasts:phoenix_feather`, …). Codec field names in the JSON
match the serializer ([WandmakingRecipeSerializer.java:24-31](src/main/java/at/koopro/wizardsandbeasts/wand/recipe/WandmakingRecipeSerializer.java#L24-L31)).
Nothing is absent.

**A2 — component-sensitive matching, for the blank. RULED OUT.** `WandBlankSlot.mayPlace` is an
`instanceof` test. It is component-blind by construction and accepts a blank with or without
`WAND_WOOD`. No `ItemStack.matches` / `isSameItemSameComponents` call exists anywhere in the bench
path. (The default `WandConfiguration` component is applied to `wand`, not `wand_blank` —
[WandItemRegistry.java:19-22](src/main/java/at/koopro/wizardsandbeasts/registry/WandItemRegistry.java#L19-L22) —
so the premise does not apply to the blank at all.)

**A3 — missing datapack module registry entry. RULED OUT.** The bench populates no slot options from
a registry. The only datapack registry it consults is `bench_enhancers`, which scores the
surrounding blocks and never touches item eligibility
([BenchMultiblockScanner.java:27-49](src/main/java/at/koopro/wizardsandbeasts/wand/bench/BenchMultiblockScanner.java#L27-L49)).
The `wand_modules/` registry from the customisation system is not read by the bench, and
`data/wizards_and_beasts/wand_modules/` does not exist in this repository.

**A4 — missing tag. RULED OUT.** Neither `mayPlace` predicate, nor `quickMoveStack`, nor the recipe
codec references any item tag. There is no `#wizards_and_beasts:wand_base` and nothing looks for one.

**Module condition. RULED OUT.** See §1 — WANDS ships ENABLED, the pre-seeded cache means the gate
is open even before a world loads.

**Slot-plumbing failure. RULED OUT.** `ResourceHandlerSlot.mayPlace` is a plain virtual method whose
default is `handler.isValid(...)`; both bench overrides fully replace it (NeoForge
21.11.42 sources, `net/neoforged/neoforge/transfer/item/ResourceHandlerSlot.java`). Nothing
downstream re-filters.

### 2.2 Confirmed cause — five registered core materials are refused by the table

`CoreSlot.mayPlace` delegates to a **hardcoded three-item allowlist**:

```java
// WandCoreMaterialItem.java:36-41
public static boolean isBenchCore(ItemStack stack) {
    Item item = stack.getItem();
    return item == WandItemRegistry.PHOENIX_FEATHER.get()
            || item == WandItemRegistry.DRAGON_HEARTSTRING.get()
            || item == WandItemRegistry.UNICORN_HAIR.get();
}
```

Eight core materials are registered. The other five are `registerSimpleItem` — plain `Item`, not
`WandCoreMaterialItem`, therefore no core key
([WandItemRegistry.java:46-55](src/main/java/at/koopro/wizardsandbeasts/registry/WandItemRegistry.java#L46-L55)):

| Item | Class | `getCoreKey` | `isBenchCore` | Slot accepts |
|---|---|---|---|---|
| `phoenix_feather` | `WandCoreMaterialItem` | `…:phoenix_feather` | ✅ | ✅ |
| `dragon_heartstring` | `WandCoreMaterialItem` | `…:dragon_heartstring` | ✅ | ✅ |
| `unicorn_hair` | `WandCoreMaterialItem` | `…:unicorn_hair` | ✅ | ✅ |
| `thestral_tail_hair` | `Item` | `null` | ❌ | ❌ |
| `veela_hair` | `Item` | `null` | ❌ | ❌ |
| `troll_whisker` | `Item` | `null` | ❌ | ❌ |
| `wampus_cat_hair` | `Item` | `null` | ❌ | ❌ |
| `thunderbird_tail_feather` | `Item` | `null` | ❌ | ❌ |

This is Bug A's signature exactly — the item renders correctly in world and in hand (it is a normal
item with a normal model), and the table refuses it outright while other parts work. It also
explains "or list it": `WandmakingCategory.coresFor` filters `BuiltInRegistries.ITEM` for
`WandCoreMaterialItem` instances ([WandmakingCategory.java:96-102](src/main/java/at/koopro/wizardsandbeasts/integration/jei/WandmakingCategory.java#L96-L102)),
so these five are invisible in the JEI viewer as well.

Root cause in one line: **wandlore identity lives in a Java class plus a hardcoded list instead of in
data**, so adding a core item to the registry does not add it to the bench.

### 2.3 The blank's own path — traced end to end, no defect found

Every spawn path was checked as §4 requires:

| Path | Result |
|---|---|
| Creative tab | present — [ModCreativeTabs.java:44](src/main/java/at/koopro/wizardsandbeasts/registry/ModCreativeTabs.java#L44) |
| `/give` | plain item, no required components |
| Crafting | [`data/…/recipe/wand_blank.json`](src/main/resources/data/wizards_and_beasts/recipe/wand_blank.json) exists |
| Villager trade | [ModVillager.java:44](src/main/java/at/koopro/wizardsandbeasts/registry/ModVillager.java#L44) |
| Loot | none |

In all cases the blank arrives **unshaped** (no `WAND_WOOD`); it is shaped by right-clicking a log
([WandBlankItem.java:31-54](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandBlankItem.java#L31-L54)).
The slot accepts it either way. The two ways the bench *appears* to reject a blank are both status
codes, not refusals:

- unshaped blank → `STATUS_BLANK_UNSHAPED` ([menu:190-196](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L190-L196))
- bench score below the recipe's `minimum_bench_tier` → `STATUS_BENCH_TOO_PLAIN` ([menu:204-209](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L204-L209))

The tier requirements run 2.0 → 8.0 across the 30 recipes; enhancer values are 0.5 (bookshelf) to
2.0 (spell teacher), summed over a radius scan, so the cheapest wand needs four bookshelves near the
bench. Reachable, but nothing in the world tells a player that before they build the bench. **Both
status messages are rendered by `renderBg` — which is where Bug B puts them on top of the slots, so
in practice the player may never have read either one.** Bug B is a plausible amplifier of the Bug A
report.

### 2.4 Ambiguity that blocks Phase 1

Which item is "the wand base"? Two candidates, two different fixes, and the brief's §8 forbids the
work the second one implies:

1. **A core material** → §2.2 is the cause. But making the five items placeable is only half a fix:
   with no `core_key` and no recipes naming them, they would move from `STATUS_MISSING_INPUT` to
   `STATUS_NO_RECIPE`. Making them actually craftable needs a canon wandlore key per core plus up to
   50 more recipe JSONs — and §8 forbids authoring recipes beyond the single A1 case, and §9.7
   forbids authoring lore-bearing text.
2. **The wand blank** → no source-level defect found. Either the report is really "the bench never
   produces anything", which lands on tier gating + Bug B hiding the explanation, or there is a
   runtime symptom this audit cannot see from source.

This is **stop-and-report trigger §9.1** — the confirmed cause is outside §2's four hypotheses (a
hardcoded Java allowlist, not a recipe/tag/registry/component problem) and its complete fix is
outside §4's sanctioned shapes.

---

## 3. Bug B — bench screen

### 3.1 API ground truth (from the jar, per §3 Track B)

Decompiled `net/minecraft/client/gui/GuiGraphics.java` and
`net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java` from
`sourcesAndCompiledWithNeoForge_fb332ee0…_output.jar` (NeoForge 21.11.42). Every `blit` overload in
1.21.11:

```java
blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int texW, int texH, int color)
blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int texW, int texH)
blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int uw, int uh, int texW, int texH)
blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int uw, int uh, int texW, int texH, int color)
blit(Identifier, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1)
```

Two facts that matter:

- **The 1.21.1-era `blit(Identifier, x, y, u, v, w, h, texW, texH)` is gone.** The only
  pipeline-less overload takes *corner coordinates and normalised UVs*, with the last four
  parameters `float`. A 1.21.1-shaped call still **compiles** — ints widen to floats — and binds to
  a completely different overload. §2's B1 is real as a class; see Track C.
- `AbstractContainerScreen.render` does **not** call `renderTooltip`; every vanilla subclass calls
  it from its own `render` override (`ContainerScreen:29`, `BrewingStandScreen:34`, 18 others). The
  bench screen doing the same at [:140](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L140)
  is correct, not a double-draw.

### 3.2 Hypotheses ruled out

**B1 — wrong blit overload, in this screen. RULED OUT.** The bench goes through
`McStylePanel.drawTexture` → `g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, srcW, srcH, texW, texH)`
([McStylePanel.java:26-29](src/main/java/at/koopro/wizardsandbeasts/client/gui/McStylePanel.java#L26-L29)),
which is the 12-parameter pipeline overload above — correct for 1.21.11.

**B1 — wrong source/texture dimensions. RULED OUT.** `wandmakers_bench.png` measures **176 × 196**
(PNG IHDR), and the screen passes `imageWidth/imageHeight` (176/196) as *both* the source region and
the sheet size ([screen:31-41](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L31-L41)).
UVs resolve to 0..1 on both axes: an exact 1:1 draw. The background is **not** misplaced, stretched,
or scrambled.

**B2 — raw texture through the sprite path. RULED OUT.** The mod has no
`assets/wizards_and_beasts/textures/gui/sprites/` directory at all, and no `blitSprite` call anywhere
outside vanilla. Nothing is nine-sliced.

**B4 — slot registration order. RULED OUT.** Bench slots are added first (indices 0-2), then the 27
inventory slots (3-29), then the hotbar (30-38)
([menu:85-97](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L85-L97)).
`quickMoveStack`'s ranges — `(3,39)` out of the output, `(0,1)` blank, `(1,2)` core, `(30,39)` and
`(3,30)` between inventory and hotbar — are all consistent with that
([menu:240-284](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L240-L284)).
No shift-click defect.

### 3.3 Confirmed causes — five layout/contrast defects, all in `WandmakersBenchScreen`

Measured against the real texture. Slot artwork was located by decoding the PNG: the three bench
slot frames occupy **y 35-50, x 44-59 / 80-95 / 134-149** (arrow at x 102-126), and the 36
player-inventory cells sit at **x 8,26,…,152** and **y 112 / 130 / 148 / 170** — all of which match
the menu's slot coordinates exactly. The artwork and the slots agree; the *overlays* do not.

| # | Defect | Evidence |
|---|---|---|
| **B-a** | **Title and "Inventory" labels are invisible.** `renderLabels` is not overridden, so vanilla draws both in `-12566464` (#404040). The panel pixels behind them are `(35,31,27)` at the title and `(43,37,32)` at the inventory label — **contrast ratio 1.58 : 1**. | vanilla [`AbstractContainerScreen.renderLabels`]; screen has no override |
| **B-b** | **The preview / status text is drawn across the three slots.** Lines start at `y+16` and step 12/10/10/10/10 → they land at y+28, 38, 48, 58, 68, from `x+28` rightwards. The slot band is y 35-50, x 44-149. Lines 2-4 run straight through blank, arrow and output; item stacks then render on top, so it reads as text bleeding out from behind the items. | [screen:71-77](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L71-L77), [:80-107](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L80-L107) |
| **B-c** | **The tier bar overlaps the hotbar.** Bar occupies `y+168 … y+173`; the hotbar slot frame starts at y 169 (border row `(55,55,55)`) and its interior at y 170. Five rows of overlap. | [screen:48-52](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L48-L52) |
| **B-d** | **The flexibility row is 14 px off-centre.** Drawn with stride 22 and width 20 — 5 buttons span 108 px — but centred as if the span were `values.length * 16` = 80 px. Draw and hit-test share the bug, so clicks still land; it just sits visibly right of centre. | [screen:54-62](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L54-L62) and the identical maths at [:171-176](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L171-L176) |
| **B-e** | **The tier tooltip can clip off the top of the screen** (`tipY = mouseY - 34`, no clamp) and is drawn immediate-mode, so vanilla's deferred item tooltips always cover it. Minor. | [screen:147-159](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L147-L159) |

B-a and B-b together are enough to read as "the UI is broken": no readable headings, and text
smeared through the crafting row.

---

## 4. Track C — screen survey (report-only, fix nothing)

34 custom screens. The mod uses **no** `gui/sprites/` textures anywhere; every texture is a raw PNG
under `textures/gui/…`, drawn through a pipeline `blit`. Only two call sites use the dead 1.21.1
shape.

| Screen | Base | Draw path | Texture convention | Same defect? |
|---|---|---|---|---|
| **NifflerPouchScreen** | `AbstractContainerScreen` | `blit(tex, x, y, 0, 0, w, h, 256, 256)` — **legacy shape** | `textures/gui/*.png` | ❌ **BROKEN — worse than the bench** |
| **HermionesBagScreen** | `AbstractContainerScreen` | `blit(TEXTURE, x, y, 0, 0, w, h, 256, 256)` — **legacy shape** | `textures/gui/*.png` | ❌ **BROKEN — worse than the bench** |
| **WandmakersBenchScreen** | `AbstractContainerScreen` | `McStylePanel.drawTexture` (12-arg pipeline) | `textures/gui/*.png` | background OK; layout defects §3.3 |
| **OllivanderTrialScreen** | `AbstractContainerScreen` | `graphics.fill` only, no texture | — | **shares B-a** (dark `0xFF1e1a28` fill, no `renderLabels` override) |
| **PocketConfiguratorScreen** | `AbstractContainerScreen` | `graphics.fill` only | — | immune — `renderLabels` overridden empty |
| HandbookScreen | `Screen` | 12-arg pipeline blit | `textures/gui/handbook/` | ✅ correct |
| BestiaryScreen | `Screen` | 12-arg pipeline blit (`drawStretched`) | `textures/gui/bestiary/` | ✅ correct |
| SpellMenuScreen | `Screen` | 12-arg pipeline blit ×4 | `textures/gui/spell_menu/` | ✅ correct |
| SkillTreeScreen | `Screen` | 12-arg pipeline blit ×2 | procedural + `skill_tree/` | ✅ correct |
| WizardsConfigScreen | `Screen` | 12-arg pipeline blit ×2 | `textures/gui/config/` | ✅ correct |
| MirrorViewScreen | `Screen` | 12-arg pipeline blit ×2 (skin faces) | player skin | ✅ correct |
| AbilityWheelScreen | `Screen` | 10-arg pipeline blit | ability icons | ✅ correct |
| SpellDiamondOverlay, ObscurialHumanPanelRenderer, ObscurialStressMeterSkinRenderer, HeritageCeremonyRenderer | HUD renderers | 12-arg pipeline blit | `textures/gui/…` | ✅ correct |
| ApparitionSelector, DarkArtsGate, Gringotts, Floo, MorphDebug, CharacterSheet, Heritage, MaraudersMap, OWLExam, OWLResults, ProfessionSelection, SkillAccessDenied, Vocation, BeamDebug, BeamStyle, ImperioCommand, SpellTeacher, DiaryPossession, DiaryWrite, MirrorCall, Pensieve | `Screen` | procedural `fill`/`drawString` only | — | n/a |

**Why the two legacy calls are broken.** `blit(TEXTURE, x, y, 0, 0, 176, 166, 256, 256)` binds to
`blit(Identifier, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1)`, giving
`x0=x, y0=y, x1=0, y1=0, u0=176, u1=166, v0=256, v1=256`: a quad stretched from the panel corner
back to the screen origin, with reversed, out-of-range normalised UVs and a zero-height V span. Not
"slightly offset" — structurally wrong.

**The defect is not mod-wide.** 32 of 34 screens are on the correct API. Two are not, and the
wandmaker bench is not one of them. There is therefore **no conflict under §9.5**: fixing the
bench's layout defects is orthogonal to fixing those two blits, and nothing done here would be
thrown away by a later class-wide pass.

---

## 5. Proposed fixes

**Bug A — core identity (blocked, see §2.4).** The elegant fix is to stop asking "is this item one
of these three classes" and start asking the data. `WandCoreMaterialItem.getCoreKey` becomes a
lookup keyed by item id — either a `DataComponent`/`ItemProperty` on the stack or a small
`wand_cores/` datapack registry in the `bench_enhancers` pattern — and `isBenchCore` becomes
"resolves to a core key". `CoreSlot.mayPlace` stays a one-liner and the five orphaned items become
placeable by adding data, not Java. That is a bigger change than §4 authorises, and it is inert
without a canon core key and recipes for each of the five — both §8/§9.7 territory. The minimal
alternative, if the five are meant to be non-bench decorative drops, is to say so in their tooltips
and leave the allowlist alone; that is a design call, not a repair.

**Bug A — the blank.** Nothing to fix in the accept path. If the report is really "the bench never
outputs anything", the fix is Bug B (make the status line legible) plus, optionally, surfacing the
required bench tier before the player has both inputs in — currently `STATUS_BENCH_TOO_PLAIN` only
appears once blank *and* core are both seated.

**Bug B-a.** Override `renderLabels` on `WandmakersBenchScreen` and draw both labels in the panel's
own palette (the screen already uses `0xFFddccaa` / `0xFFbba07a` for its own text). Two lines, no
shared helper, no other screen touched — which keeps it clear of §9.3.

**Bug B-b.** Move the preview/status block out of the slot band. The panel has clear space at
y+86…y+100 (between the flexibility row and the inventory label) and above at y+16…y+30 for two
lines. Simplest repair that is not a redesign: keep the status message where it is but cap it at the
two lines it already promises and start the *preview* block below the slots rather than above them.

**Bug B-c.** Move the tier bar up to `y + imageHeight - 40` (y+156…161), clear of the hotbar frame at
169, or shorten the panel. Moving the bar is the smaller change.

**Bug B-d.** Centre with the stride actually used: `fx = x + imageWidth/2 - ((values.length - 1) * 22 + 20) / 2`.
Apply to both the draw and the hit-test so they stay identical.

**Bug B-e.** Clamp `tipY` to ≥ 0 and `tipX + tipW` to ≤ `width`.

---

## 6. Punchlist additions (not fixed here)

- **BLOCKER** — `NifflerPouchScreen:34` and `HermionesBagScreen:30` use the removed 1.21.1 `blit`
  shape; it compiles onto the corner/UV overload and draws a structurally wrong quad. Both
  backgrounds are broken in-game. Out of scope per §5/§8.
- **BLOCKER** — five registered wand core materials cannot be placed in the bench or seen in JEI
  (§2.2).
- **POLISH** — `OllivanderTrialScreen` has the same invisible-label defect as B-a.
- **POLISH** — the 30 wandmaking recipes gate *loading* on `Module.WANDS` via `neoforge:conditions`,
  contrary to the "gate access, not registration" rule (§7).
- **NICE-TO-HAVE** — the bench never tells a player the tier system exists until both inputs are
  seated; `STATUS_BENCH_TOO_PLAIN` is the only surface, and B-b makes it unreadable.
- **NICE-TO-HAVE** — `WandmakingRecipe.matches` returns `false` and `assemble` returns
  `ItemStack.EMPTY`; the menu and the JEI category each rebuild the result independently
  ([menu:210-222](src/main/java/at/koopro/wizardsandbeasts/wand/gui/WandmakersBenchMenu.java#L210-L222),
  [category:104-112](src/main/java/at/koopro/wizardsandbeasts/integration/jei/WandmakingCategory.java#L104-L112)).
  Two copies of one rule.

---

## 7. Verification performed

Read-only. No gradle task was run, no client launched, nothing compiled. Findings rest on: the mod
source at `469ac57`; the decompiled 1.21.11 client from the NeoFormRuntime jar; the NeoForge
21.11.42 sources jar; and a pixel decode of `wandmakers_bench.png` (IHDR 176×196, slot-frame and
inventory-cell rectangles located by colour scan, label contrast computed from sampled background
pixels).
Nothing in §3.3 has been confirmed in-game.

---

## 8. Correction — Bug A's root cause (added after the audit, same day)

§2.3 concluded that the wand blank's own path had no source-level defect. That was wrong in one
place, and the place was not in the acceptance path at all.

**[WandBlankItem.java:105](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandBlankItem.java#L105)
passed a raw `Identifier` as a translation argument.**

```java
tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.wand_blank.tooltip.wood", wood));
```

`TranslatableContents` accepts only a `Component`, `Number`, `Boolean` or `String`. Its constructor
in 1.21.11 (NeoForge patch) reads:

```java
var loader = net.neoforged.fml.loading.FMLLoader.getCurrentOrNull();
if (loader != null && !loader.isProduction()) {
    for (Object arg : this.args) {
        if (!(arg instanceof Component) && !isAllowedPrimitiveArgument(arg)) {
            throw new IllegalArgumentException("TranslatableContents' arguments must be either a Component, Number, Boolean, or a String…");
        }
    }
}
```

So the shaping worked and the failure landed one step later, on the only surface that reports it:

1. Right-click a log — `useOn` runs, `WAND_WOOD` is written to the live stack, the sound plays.
2. Hover the shaped blank. `appendHoverText` now takes the second branch and constructs the
   translatable with the `Identifier`. In a **development** runtime that **throws**, out of
   `ItemStack.getTooltipLines`, uncaught, through the tooltip build.
3. JEI reaches the same throw from the other side: `WandmakingCategory.blankFor` builds a shaped
   blank for every wandmaking entry, so the viewer trips it too.

A packaged build never sees it — `isProduction()` is true there and `getArgument` falls back to
`toString()`. That is why the item "renders correctly in-world and in hand" and only the
wood-carrying state misbehaves, and why it presents as *the blank cannot take a wood type*: the
tooltip is the only place the wood is visible, and it is exactly what breaks the moment the wood
exists.

Fixed by passing `wood.toString()`, matching what `WandmakersBenchScreen` already did for the same
value. §2.1's rule-outs (A1-A4, module gating, slot plumbing) all still stand — none of them was the
cause, and none of them needed changing. §2.2's core-material finding is unrelated and remains open
on the punchlist.

A source-wide sweep found no second occurrence of an illegal translatable argument.
