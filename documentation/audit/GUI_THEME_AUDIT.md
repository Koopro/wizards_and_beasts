# GUI_THEME_AUDIT.md — Phase 0, GUI Theme Pass

**Read-only audit. Zero code written. Zero files modified other than this one.**

- Repo: `wizards_and_beasts`, MC **1.21.11** / NeoForge **21.11.42** / Java 21, Mojang official mappings
- Branch: `feat/animagus-form-data-layer`
- HEAD at audit: `8fe6f4468c36e700ac04b12229effb113bd8f711`
- Pre-flight `git status --porcelain`: **24 entries**, none GUI-related (the uncommitted Obscurial/Horntail rig work). Snapshot in session scratchpad. See §6.

> ## 🔴 STOP-AND-REPORT FIRED — three conditions
>
> **§5.1 — screen inventory mismatch.** 34 screen classes exist. The §1.2 skin table names 6 surfaces. All 6 are found, but **28 screens have no skin assignment** and the brief forbids guessing one.
>
> **§5.9 — scope larger than estimated.** 34 screens against the brief's ceiling of 8. (The 600-line half of that condition did *not* fire — largest screen is 577 lines.)
>
> **§5.10 — conflict between the prompt and repo state.** The `WabTheme` / `WabWidgets` spine in §1.1 duplicates a token layer and a draw layer that **already shipped** (`WizardsPalette`, `WizardsMetrics`, `McStylePanel` themed components, `tools/gui_chrome.py`'s `theme/` target, and five `textures/gui/theme/` sprites). A second, separate `MINISTRY` palette also directly contradicts the repo's documented, byte-sampled Ministry purple brand.
>
> **Phase 1 must not begin until these are resolved.** See §7.
>
> Conditions that did **not** fire: §5.2 (nine-slice API is exactly as the design assumes ✅), §5.4 (`./gradlew build` **BUILD SUCCESSFUL** ✅), §5.7 (no client-type leak found in screen code ✅), §5.8 (no mixin required ✅).

---

## 1. Screen inventory (§0.1)

**34 classes** extending `Screen` or `AbstractContainerScreen`, all under `at.koopro.wizardsandbeasts.client.**`. **6 are container screens** (`AbstractContainerScreen`, `Slot` rendering); 28 are plain `Screen`.

Filename false positives excluded (not `Screen` subclasses): `client/camera/ScreenShakeHandler`, `client/camera/ScreenShakeMath`, `client/event/InventoryScreenInjector`, `client/network/ClientScreenHooks`, `client/spell/render/CrucioScreenRenderer`, `client/skill/gui/SkillScreenRouter`.

Columns: **Cont.** = container screen · **Chrome** = background/frame approach · **Hex** = ARGB literals in the class itself · **Tiles** = renders a list/grid/tile roster of content entities (the §1.5 module-state surfaces) · **Mod** = consults `ModuleManager` · **Skin** = assignment derivable from §1.2.

| # | Class | Lines | Cont. | Chrome | Hex | Tiles | Mod | Skin |
|---|---|---:|:--:|---|---:|:--:|:--:|---|
| 1 | [HandbookScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java) | 577 | — | mix: `book.png`+`emblem.png` blit over 18 `fill()` | 8 | — | — | `MINISTRY` ⚠ |
| 2 | [BestiaryScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java) | 509 | — | 8 fixed-size PNG blits + `fill()` + `McStylePanel.drawScrollbar` | 6 | ✅ entry rows + icon grid | — | `FIELD_NOTEBOOK` |
| 3 | [SkillTreeScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java) | 499 | — | via `SkillTreeRenderHelper` (nine-slice `skill_tree/panel.png`) + `SkillTreeChartTextures` | 6 | ✅ node graph, 7 loops | — | `STAR_CHART` |
| 4 | [CharacterSheetScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java) | 446 | — | `McStylePanel` + `character_sheet/*.png` | 15 | ✅ via `SpellsTab`/`SkillsTab` | ✅ log-only | **NONE** 🔴 |
| 5 | [HeritageSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageSelectionScreen.java) | 406 | — | delegated to `HeritageCeremonyRenderer` (18 literals, 2 blits) | 1 | ✅ heritage rail | — | **NONE** 🔴 |
| 6 | [BeamStyleScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java) | 376 | — | `McStylePanel` + `fill()` | 6 | — | — | **NONE** 🔴 debug |
| 7 | [BeamDebugScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamDebugScreen.java) | 320 | — | `McStylePanel` + `fill()` | 1 | — | — | **NONE** 🔴 debug |
| 8 | [WizardsConfigScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java) | 299 | — | `config/parchment.png`,`card.png`,`card_dark.png` + `McStylePanel` | 3 | ✅ category cards | — | **NONE** 🔴 |
| 9 | [SpellMenuScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuScreen.java) | 296 | — | `spell_menu/slot.png`+`slot_active.png` blits, helper nine-slices `panel.png` | 1 | ✅ spell slots | — | **NONE** 🔴 |
| 10 | [AbilityWheelScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java) | 279 | — | procedural `fill()`, palette-aware | 10 | ✅ wheel segments | — | **NONE** 🔴 |
| 11 | [FlooNetworkScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java) | 243 | — | `McStylePanel` + `fill()` | 11 | ✅ destination rows | — | **NONE** 🔴 |
| 12 | [MaraudersMapScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java) | 236 | — | `McStylePanel` + 9 `fill()` | 12 | ✅ marker list | — | **NONE** 🔴 |
| 13 | [WandmakersBenchScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java) | 212 | ✅ | `wandmakers_bench.png` 176×196 + `McStylePanel` | 9 | — | — | `WORKBENCH` (⛔ §2.1 blocked) |
| 14 | [ProfessionSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java) | 204 | — | procedural `fill()` | 5 | ✅ profession list | — | **NONE** 🔴 |
| 15 | [MorphDebugScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java) | 202 | — | `McStylePanel` + `fill()` | 6 | — | — | **NONE** 🔴 debug |
| 16 | [PensieveScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java) | 186 | — | procedural `fill()` | 9 | ✅ memory list | — | **NONE** 🔴 |
| 17 | [VocationSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/VocationSelectionScreen.java) | 169 | — | procedural `fill()` | 0 | ✅ vocation list | — | **NONE** 🔴 |
| 18 | [OWLExamScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java) | 165 | — | procedural `fill()` | 8 | — | — | **NONE** 🔴 |
| 19 | [GringottsScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java) | 155 | — | `McStylePanel` + raw `fill()` | 11 | — | — | `GOBLIN_LEDGER` |
| 20 | [SpellTeacherScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java) | 140 | — | `McStylePanel` only | 3 | — | — | `MINISTRY` ⚠ ambiguous, see §7.4 |
| 21 | [DarkArtsGateScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java) | 139 | — | procedural `fill()` + `InkRevealRenderer` | 8 | — | — | **NONE** 🔴 |
| 22 | [ApparitionSelectorScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java) | 137 | — | procedural, palette-aware | 8 | ✅ anchor list | — | **NONE** 🔴 |
| 23 | [OWLResultsReadOnlyScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java) | 134 | — | procedural `fill()` | 6 | — | — | **NONE** 🔴 |
| 24 | [OllivanderTrialScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/OllivanderTrialScreen.java) | 131 | ✅ | `McStylePanel` + palette (14 reads — best-themed screen) | 1 | — | — | **NONE** 🔴 |
| 25 | [DiaryWriteScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java) | 129 | — | procedural `fill()` | 4 | — | — | **NONE** 🔴 |
| 26 | [WizardsConfigCategoryScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigCategoryScreen.java) | 129 | — | procedural `fill()` | 0 | ✅ option rows | — | **NONE** 🔴 |
| 27 | [MirrorViewScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java) | 120 | — | `fill()` + live player-face blit | 4 | — | — | **NONE** 🔴 |
| 28 | [PocketConfiguratorScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trunk/gui/PocketConfiguratorScreen.java) | 93 | ✅ | procedural, palette-aware (7 reads) | 2 | — | — | **NONE** 🔴 |
| 29 | [MirrorCallScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorCallScreen.java) | 71 | — | vanilla only | 1 | — | — | **NONE** 🔴 |
| 30 | [HermionesBagScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/item/HermionesBagScreen.java) | 66 | ✅ | `container/hermiones_bag.png` 256×256 blit | 0 | — | — | **NONE** 🔴 |
| 31 | [DiaryPossessionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryPossessionScreen.java) | 60 | — | procedural `fill()` | 3 | — | — | **NONE** 🔴 |
| 32 | [NifflerPouchScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/niffler/NifflerPouchScreen.java) | 57 | ✅ | `niffler_pouch.png` / `baby_niffler_pouch.png` 256×256 blit | 0 | — | — | **NONE** 🔴 |
| 33 | [ImperioCommandScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/ImperioCommandScreen.java) | 55 | — | vanilla only (thinnest screen in mod) | 1 | — | — | **NONE** 🔴 |
| 34 | [SkillAccessDeniedScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillAccessDeniedScreen.java) | 42 | — | `McStylePanel` only | 3 | — | — | **NONE** 🔴 |

### 1.1 Skin-table reconciliation (§5.1)

| §1.2 skin | Named screen | Found? | Class |
|---|---|:--:|---|
| `FIELD_NOTEBOOK` | Bestiary | ✅ | `BestiaryScreen` (#2) |
| `MINISTRY` | Handbook | ✅ | `HandbookScreen` (#1) |
| `MINISTRY` | Spell learning | ⚠ | `SpellTeacherScreen` (#20) — *or* `SpellMenuScreen` (#9). Ambiguous, §7.4 |
| `STAR_CHART` | Skill web | ✅ | `SkillTreeScreen` (#3) |
| `GOBLIN_LEDGER` | Gringotts vault | ✅ | `GringottsScreen` (#19) |
| `WORKBENCH` | Wandmaker table | ✅ | `WandmakersBenchScreen` (#13) |

**Every screen in the table was found.** The failure is the other direction: **28 of 34 screens have no skin.** §1.2 states plainly that any such screen is a stop-and-report condition. That includes several with strong domain identity of their own (`CharacterSheetScreen`, `HeritageSelectionScreen`, `AbilityWheelScreen`, `MaraudersMapScreen`, `PensieveScreen`) and three deliberately-neutral debug screens (`BeamDebugScreen`, `BeamStyleScreen`, `MorphDebugScreen`) that arguably should stay unthemed.

### 1.2 Colour literals

**305 ARGB literals across 47 files** in in-scope screen code, plus **99** in `WizardsAndBeastsUiTokens` and **21** in `WizardsPalette` = **425 GUI colour literals repo-wide** against 21 palette tokens.

47 > 34 because screens delegate chrome to render helpers, tabs and widgets that carry their own literals: `HeritageCeremonyRenderer` (18), `SpellCardWidget` (16), `SkillTreeChartTextures` (15), `AttributesTab` (15), `ObscurialHudTheme` (14), `HeritageRailEntry` (13), `ConfigWidgets` (9), `UiContrast` (8).

**The full `file:line:literal` list is already committed** at [GUI_AUDIT.md §5.3](GUI_AUDIT.md) (lines 305–760) and is re-verified as current at this HEAD. It is not duplicated here.

**Consequence for §1.1's hard rule** ("no screen class may contain a colour literal"): the rule is reachable but it is a ~425-literal migration across 47 files, not 6 — and it collides with `WizardsPalette`'s deliberate exclusion of semantic colour (§7.3).

### 1.3 Texture identifiers

91 PNGs under `assets/wizards_and_beasts/textures/gui/**`. Screen-referenced sheets:

| Screen | Textures |
|---|---|
| `BestiaryScreen` | `bestiary/{screen,left_panel,right_panel,row,header,scroll_track,scroll_thumb,entry_placeholder}.png` |
| `CharacterSheetScreen` | `character_sheet/{background,icon_tab,panel,panel_sel}.png` via `CharacterSheetTextures` |
| `WizardsConfigScreen` | `config/{parchment,card,card_dark}.png` |
| `HandbookScreen` | `handbook/{book,emblem}.png` |
| `HeritageSelectionScreen` | `heritage/{wax_seal,parchment}.png` via `HeritageCeremonyRenderer` |
| `HermionesBagScreen` | `container/hermiones_bag.png` |
| `NifflerPouchScreen` | `niffler_pouch.png`, `baby_niffler_pouch.png` |
| `SkillTreeScreen` | `skill_tree/panel.png` + `skill_tree/chart/*.png` (11) |
| `SpellMenuScreen` | `spell_menu/{panel,slot,slot_active}.png` |
| `WandmakersBenchScreen` | `wandmakers_bench.png` |
| *shared* | `gui/theme/{panel,panel_inset,divider,scrollbar_track,scrollbar_thumb}.png` |
| *vanilla* | `minecraft:textures/gui/demo_background.png` via `VanillaGuiTextures` |

**No `assets/wizards_and_beasts/atlases/` directory exists.** No sprite is currently loaded through the GUI atlas — every mod texture is a direct `blit` of a standalone PNG. Adding `atlases/gui.json` is net-new, not a migration.

---

## 2. Rendering API ground truth (§0.2)

Extracted verbatim from the mapped 1.21.11 sources
(`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_fb332ee0…_output.jar`)
and the stripped client resources jar (`stripClient_5bedf0cb…_resourcesOutput.jar`).

**The design's assumptions hold. §5.2 does not fire.**

### 2.1 Sprite blitting — a `RenderPipeline` argument *is* required

```java
// net.minecraft.client.gui.GuiGraphics
public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height)
public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, float alpha)
public void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int colour)
```

The pipeline is **not** optional — every overload takes it first. Call sites use `RenderPipelines.GUI_TEXTURED`. `Identifier` is the sprite *name* relative to the GUI atlas (e.g. `wizards_and_beasts:field_notebook/panel`), **not** a `textures/…` path.

### 2.2 Nine-slice is data-driven, not an API argument

There is **no** public nine-slice draw method. `blitSprite` dispatches internally on the sprite's own metadata:

```java
private static GuiSpriteScaling getSpriteScaling(TextureAtlasSprite sprite) {
    return sprite.contents().getAdditionalMetadata(GuiMetadataSection.TYPE)
                 .orElse(GuiMetadataSection.DEFAULT).scaling();
}

public void blitSprite(RenderPipeline p, Identifier id, int x, int y, int w, int h, int col) {
    TextureAtlasSprite sprite = this.guiSprites.getSprite(id);
    switch (getSpriteScaling(sprite)) {
        case GuiSpriteScaling.Stretch   s -> this.blitSprite(p, sprite, x, y, w, h, col);
        case GuiSpriteScaling.Tile      t -> this.blitTiledSprite(...);
        case GuiSpriteScaling.NineSlice n -> this.blitNineSlicedSprite(p, sprite, n, x, y, w, h, col);
    }
}
```

`blitNineSlicedSprite` is **private**. Nine-slicing is therefore obtained *only* by putting the sprite on the GUI atlas and shipping a `.mcmeta`. Border sizes are clamped: `Math.min(border.left(), width / 2)` — a 6px border degrades gracefully below 12px width rather than corrupting.

### 2.3 `.mcmeta` schema (verbatim, `minecraft:textures/gui/sprites/widget/button.png.mcmeta`)

```json
{
    "gui": {
        "scaling": {
            "type": "nine_slice",
            "width": 200,
            "height": 20,
            "border": 3
        }
    }
}
```

`border` accepts either a single positive int (uniform) or `{left, top, right, bottom}`. `stretch_inner` defaults to `false` (inner segments **tile**; set `true` to stretch). Codec validation **hard-fails** if `left + right >= width` or `top + bottom >= height`.

> ⚠ Against §1.3: a 32×32 sprite with a 6px border passes (6+6 < 32). A **24×24 inset with a 4px border** also passes. Both are legal. Note however that the repo's shipped `theme/` sprites are 32×32 cut on **8**, not 6 — see §7.2.

### 2.4 Vanilla `assets/minecraft/atlases/gui.json` (verbatim)

```json
{
  "sources": [
    {
      "type": "minecraft:directory",
      "prefix": "",
      "source": "gui/sprites"
    },
    {
      "type": "minecraft:directory",
      "prefix": "mob_effect/",
      "source": "mob_effect"
    }
  ]
}
```

Mirroring this for our namespace means `{"type":"minecraft:directory","prefix":"","source":"gui/sprites"}` — with sprites physically at `assets/wizards_and_beasts/textures/gui/sprites/**` and addressed as `wizards_and_beasts:<subdir>/<name>`. **This matches the §1.7 output path exactly.**

### 2.5 `AbstractContainerScreen` slot drawing — the hook is `renderBg`

```java
public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    super.renderBackground(g, mx, my, pt);
    this.renderBg(g, pt, mx, my);          // ← abstract; subclass draws here
}

public void renderContents(GuiGraphics g, int mx, int my, float pt) {
    ...
    this.renderSlotHighlightBack(g);
    this.renderSlots(g, mx, my);            // ← items drawn here, strictly after renderBg
    this.renderSlotHighlightFront(g);
}

protected abstract void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY);
protected void renderSlots(GuiGraphics g, int mx, int my) { for (Slot s : menu.slots) if (s.isActive()) renderSlot(g, s, mx, my); }
```

**Vanilla draws no slot background at all** — slot wells are painted into the container PNG. So `WabWidgets.drawSlot` belongs in each screen's `renderBg`, iterating `menu.slots` and drawing at `slot.x - 1, slot.y - 1, 18, 18`. Nothing reads or writes slot geometry, so **§1.6's byte-identical requirement is satisfiable** and §5.5 does not fire. Hit detection lives entirely in `Slot`/`AbstractContainerScreen`; a background draw cannot perturb it.

---

## 3. Font ground truth (§0.3)

- **`assets/wizards_and_beasts/font/` does not exist.** No mod font provider of any kind.
- **Zero `Style.EMPTY.withFont(...)` calls** in client code. Every screen draws through `Minecraft#font` with `drawString` / `drawCenteredString` / `drawWordWrap`.
- **Lang inventory:** two files. `en_us.json` = **2484 keys**. `de_de.json` = **`{}`, empty stub, 0 keys.** A third, generated `en_us.json` exists at `src/generated/resources/…` and is union-merged into the shipped file by `processResources`.
- **Scripts present: Latin only.** No Cyrillic, Greek, or CJK. But `en_us.json` carries **6 distinct non-ASCII codepoints on 98 lines** — `§` `·` `—` `…` `✔` `✗`. A headline face covering ASCII alone will tofu `—` and `…` in any header that uses them.
- **Vanilla fallback declaration:** vanilla's own `minecraft:default` font is itself a provider *list*; a mod font gets fallback by listing `{"type":"reference","id":"minecraft:include/default"}` **after** its own providers, so unmatched glyphs fall through. This is the mechanism §1.4 requires; it exists and is declarative.

**§5.6 cannot be fully cleared at Phase 0** — with no non-Latin locale in the repo, the §1.4 verification requires the temporary dev-only lang-key injection the brief itself prescribes. The `—`/`…`/`✔` codepoints above give a real, shipping test case that does not need injection.

---

## 4. Module state ground truth (§0.4)

`ModuleState` ([ModuleState.java](src/main/java/at/koopro/wizardsandbeasts/module/ModuleState.java)) — **four constants, matching §1.5 exactly**:

| Constant | Serialized | `grantsAccess()` | `isOperatorSettable()` | Javadoc semantics |
|---|---|:--:|:--:|---|
| `DISABLED` | `disabled` | false | true | "off. The feature exists but cannot be reached." |
| `ENABLED` | `enabled` | true | true | "on." |
| `PREVIEW` | `preview` | true | true | "on, but flagged as unfinished in player-facing copy." |
| `COMING_SOON` | `coming_soon` | false | **false** | "a roadmap marker… changes in code or in the config seed and nowhere else." |

**Gating controls access, never registration — confirmed.** The class javadoc states it outright ("Registration is never gated — content always registers — so these describe reachability only"), and no conditional-unregistration site was found. ✅

**Current screen-path consults — only four, all read-only:**

| Site | Check | Shape |
|---|---|---|
| [InventoryScreenInjector.java:38](src/main/java/at/koopro/wizardsandbeasts/client/event/InventoryScreenInjector.java#L38) | `!isEnabled(CHARACTER_SHEET)` | Gates the inventory button, before the screen exists. Outside render. |
| [CharacterSheetKeyHandler.java:14](src/main/java/at/koopro/wizardsandbeasts/client/spell/CharacterSheetKeyHandler.java#L14) | `!isEnabled(CHARACTER_SHEET)` | Gates the `C` keybind. Outside render. |
| [CharacterSheetScreen.java:159](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L159) | `isPreview(CHARACTER_SHEET)` | **Logging only**, one-shot. Gates nothing. |
| [SkillsTab.java:58](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L58) | `!isEnabled(DARK_ARTS)` | The **only** gate on a render path — decides whether a dark-arts row draws. |

**No screen renders differently for `PREVIEW` vs `ENABLED`, and none distinguishes `COMING_SOON` from `DISABLED` anywhere in the UI.** Phase 3 is entirely net-new behaviour. `ClientModuleState` is a read-through cache of the server's authoritative map — the correct read point, and it decides nothing itself.

**Content-tile surfaces that would need `drawStateBadge`** (from §1's *Tiles* column) — **13 screens**: Bestiary entry rows + icon grid, Skill web node graph, Character sheet spell cards + skill bars, Heritage rail, Config category cards, Spell menu slots, Ability wheel segments, Floo destination rows, Marauder's map markers, Profession list, Pensieve memory list, Vocation list, Apparition anchor list, Config option rows.

---

## 5. Build baseline (§0.5)

```
BUILD SUCCESSFUL in 51s
9 actionable tasks: 7 executed, 2 up-to-date
```

- `./gradlew build` — **PASSES**. `:test` and `:check` both green. `AssetModelParityTest`, `LangParityTest`, `WandIdParityTest` all pass.
- **§5.4 does not fire.** ✅
- ⚠ **Caveat, stated plainly:** the worktree was **not clean**. 24 uncommitted entries (Obscurial/Horntail rig work — Java, geo/animation JSON, PNGs, `tools/*.py`) were present. The build passed *with* them. None is GUI code. `git status --porcelain` was byte-identical before and after the build.
- Only Gradle deprecation warnings (Gradle 10 incompatibility) — pre-existing, unrelated.

---

## 6. Working-tree hazard (§4)

Per §4, the following uncommitted files are present and **must not be staged** by this pass:

```
 M client/form/FormModelRenderer.java, client/model/ObscurialDarkModel.java, form/FormRegistry.java
 M geckolib/{animations,models}/entity/{hungarian_horntail,obscurus}.*
 M textures/{bestiary/icons,bestiary/silhouettes,entity}/{hungarian_horntail,obscurus}*.png
 M textures/item/parchment.png, textures/mob_effect/{dementor_chill,splinched}.png
 M data/…/creatures/{hungarian_horntail,obscurus}.json, tools/beast_skins.py
?? textures/entity/form/obscurial_dark.png, tools/horntail_model.py, tools/obscurus_model.py
```

This is the Obscurial rig / Horntail rebuild, not the petrify/basilisk or Chamber work named in §4, but the same rule applies: **explicit pathspec commits only.** Note `textures/item/parchment.png` and the two `mob_effect` PNGs are `runData` stubs over real art — the known `runData`-overwrites-art hazard.

---

## 7. Stop-and-report detail

### 7.1 🔴 §5.10 — the theme layer already exists

This is the load-bearing finding, and it is a **repeat**: [GUI_AUDIT.md §8.1](GUI_AUDIT.md) fired the same condition against a near-identical earlier brief, its recommendation was accepted, and the work **shipped**. Building `WabTheme` / `WabWidgets` now would create the second layer that audit was written to prevent.

| §1.1 deliverable | Already exists as | State |
|---|---|---|
| `WabTheme` — palette tokens | [`WizardsPalette`](src/main/java/at/koopro/wizardsandbeasts/client/gui/WizardsPalette.java) | ✅ 21 tokens, 2 brands, documented exclusions. **9 files read it.** |
| `WabTheme` — spacing scale | [`WizardsMetrics.SPACE_*`](src/main/java/at/koopro/wizardsandbeasts/client/gui/WizardsMetrics.java#L35) | ✅ 2/4/8/12/16/24/32 — **identical to §1.3's scale.** 2 screens read it. |
| `WabTheme` — type scale | `WizardsMetrics.LINE_{TIGHT,BODY,SECTION,TITLE}` | ✅ four roles — **1:1 with §1.4's four type roles.** |
| `WabTheme` — header font id | — | ❌ genuinely absent |
| `WabTheme` — sprite lookup, screen→skin map | — | ❌ genuinely absent (single-skin today) |
| `WabWidgets.drawPanel` | `McStylePanel.drawThemedPanel` | ✅ |
| `WabWidgets.drawInsetPanel` | `McStylePanel.drawThemedInset` | ✅ bevel deliberately inverted |
| `WabWidgets.drawDivider` | `McStylePanel.drawDivider` | ✅ |
| `WabWidgets.drawScrollbar` | `McStylePanel.drawScrollbar` | ✅ |
| `WabWidgets.drawHeader` / `drawButton` / `drawTab` / `drawSlot` / `drawStateBadge` / `drawTooltipFrame` | — | ❌ genuinely absent |
| §1.7 Pillow generator | [`tools/gui_chrome.py`](tools/gui_chrome.py) | ✅ already has a `theme/` target (`a_theme_panel`, `a_theme_panel_inset`, `a_theme_divider`, `a_theme_scrollbar_*`) + the `Generator` PNG-marker contract |
| §1.7 sprites | `textures/gui/theme/*.png` (5) | ✅ shipped |
| §1.7 `atlases/gui.json` | — | ❌ genuinely absent |

**Adoption is near zero, which is the real gap.** `McStylePanel.drawThemed*` has exactly **two** call sites in the whole mod (`BestiaryScreen:212,218` scrollbar; `PlayerModelViewport:51` inset). `GUI_DESIGN_SYSTEM.md` says so itself: *"Not yet applied to any screen… Nothing here has been seen in a running client."*

So the honest work shape is **"multi-skin the layer that exists and roll it out"**, not *"build a theme layer"*. That is a scope question, so per §8 it is yours, not mine.

### 7.2 ⚠ Implementation collision — nine-slice mechanism and border size

Two concrete mismatches, both mechanical:

1. **Mechanism.** `McStylePanel.drawNineSlice` hand-slices with nine `g.blit` calls on a standalone PNG. The vanilla path (§2.2) needs the sprite **on the GUI atlas** with a `.mcmeta`. These are not interchangeable — atlas sprites cannot be `blit` by texture path, and standalone PNGs cannot be `blitSprite`. Moving to the atlas means every existing `drawNineSlice` caller changes. The atlas path is better (vanilla-native, correct clamping, one draw call) and I would take it under §8 — but it invalidates the existing `theme/` sprite *location*, which is a §1.7 "paths must not change" concern.
2. **Border size.** §1.3 mandates **32×32 cut on 6** and **24×24 inset cut on 4**. The shipped sprites are **32×32 cut on 8** for *both* panel and inset (`WizardsMetrics.PANEL_SPRITE_BORDER = 8`), and `tools/gui_chrome.py`'s `theme_frame` draws its filigree to that 8px seat. Re-cutting to 6 is a redraw of the existing art, not a parameter change.

### 7.3 🔴 §5.10 — the `MINISTRY` skin contradicts the repo's Ministry brand

§1.2 gives `MINISTRY` a **peacock-blue** frame `#1E3A4C` on cream `#E4DCC8`.

The repo's Ministry brand is **purple**: `WizardsPalette.MINISTRY = 0xFF3E1F47`, and its javadoc records that this is *"sampled straight off `handbook/emblem.png` — it is that file's dominant colour to the byte"*, listed explicitly *"so it reads as a brand rather than as drift: a hue audit that only knows about the leather family flags these as strays and invites someone to 'correct' them."*

Applying the §1.2 `MINISTRY` palette repaints the Handbook blue **while `emblem.png` stays purple**, on the very screen that is the brand's reference. This is exactly the "correction" the javadoc warns against. It is a design decision, not implementation, so I am not substituting — reporting.

Related: §1.1's *"no screen class may contain a colour literal"* collides with `WizardsPalette`'s deliberate exclusion of semantic colour (danger red, Floo green, ability-wheel state rings). Those literals are meaning, not theme. Either they get palette tokens (contradicting the palette's documented scope) or the hard rule needs a stated carve-out.

### 7.4 ⚠ "Spell learning" is ambiguous

§1.2 assigns `MINISTRY` to "spell learning". Two candidates:

- `SpellTeacherScreen` (140 lines) — the NPC teaching interaction. Most literal read.
- `SpellMenuScreen` (296 lines) — the spell **selection** wheel/grid with `slot.png` art.

The brief forbids guessing an assignment. Needs your call.

### 7.5 🔴 §5.9 — scope

34 screens against a stated ceiling of 8, in 14 packages, with 5 render idioms, 425 colour literals across 47 files, and 13 content-tile surfaces for Phase 3. Phase 2 as written ("every remaining screen") plus §6's mandatory in-game verification is **34 screens × 4 GUI scales × 2 window sizes = 272 manual render checks**, before Phase 3's four-module-state world.

The 600-line half of §5.9 did **not** fire: largest is `HandbookScreen` at 577.

### 7.6 Conditions that did not fire ✅

| Cond. | Verdict |
|---|---|
| §5.2 nine-slice/atlas API differs | ✅ **Does not fire.** Exists exactly as designed (§2). |
| §5.3 screen un-themeable without restructuring | ✅ Not determinable pre-implementation; nothing found that obviously blocks. |
| §5.4 build fails | ✅ **BUILD SUCCESSFUL** (§5). |
| §5.5 slot geometry unpreservable | ✅ **Does not fire.** `renderBg` runs strictly before `renderSlots`; nothing touches `Slot` bounds (§2.5). |
| §5.6 font fallback impossible | ✅ Mechanism exists (§3). Full verification deferred to Phase 4 per §1.4. |
| §5.7 client type in common code | ✅ **Does not fire.** All 6 container screens read their menu through the standard contract; no theming change reaches a menu or payload. |
| §5.8 mixin required | ✅ **Does not fire.** `renderBg` and `blitSprite` cover everything the design needs. |

---

## 8. Punchlist candidates (noticed, deliberately not fixed)

| Severity | Finding |
|---|---|
| POLISH | `Identifier.tryParse()` used at **10+ sites** repo-wide (`ApparitionCommands`, `BroomEntity`, `ModuleCommands`, `ModuleIds`, `MapOpenS2CPayload`, `VocationCommitC2SPayload`, `WandConfigCommands`, `ModConfiguredFeatures`) — the §0 stack rules ban it. All non-GUI; §3 forbids adjacent refactoring. |
| POLISH | 17 of 34 screens ship hardcoded English titles via `Component.literal` (carried from `GUI_AUDIT.md §1`). |
| POLISH | `textures/gui/bestiary/entries/` does not exist — every bestiary entry renders `entry_placeholder.png`. Directly affects the Phase 1 pilot's appearance. |
| POLISH | 10 orphaned `type_icons/*.png` + 3 dead `HeritageSelection.TYPE_ICON_*` tokens. |
| NICE-TO-HAVE | `VanillaGuiTextures` still points at vanilla `demo_background.png`. |
| NICE-TO-HAVE | `WizardsAndBeastsUiTokens` (356 lines, 99 literals) mixes metrics and colour across 5 unrelated screens; `HeritageSelection`'s 48 layout constants have **zero** readers and its `PANEL_WIDTH/HEIGHT = 392×308` contradicts the screen's own 400×232. |

---

## 9. Pre/post-flight

- **Pre-flight** `git status --porcelain`: 24 entries (§6). Snapshot: session scratchpad `preflight.txt`.
- **Post-flight**: unchanged apart from this file, untracked at `docs/audit/GUI_THEME_AUDIT.md`.
- No source file was read-modified. No commit was made. The Obscurial/Horntail working tree was not touched, staged, or committed.

---
---

# Phase 0′ Delta

**Read-only re-verification for v2 of the brief.** v2 supersedes v1 entirely; the work shape is now *extend and adopt* the shipped layer across **10** screens, not build a new one across 34.

- HEAD at delta: `bae531402004a632c4b05d55691cd47f39f461ef` — **identical to the audit commit.** `git diff bae5314 HEAD` is empty.
- Build gate (§6.4): `./gradlew build` → **BUILD SUCCESSFUL**, `:test` and `:check` green. **Does not fire.**
- Obscurial/Horntail tangle: **24 entries, 0 staged** (`git diff --cached --name-only` empty). Untouched, unstaged, not read.

## 0′.1 Audit accuracy vs HEAD — one confirmed error, one ambiguity

Zero commits landed between the audit and this delta, so nothing drifted. Re-verification of the audit's own figures against source found:

**Colour-literal counts: exact.** The 10 in-scope screens re-counted at `6, 8, 3, 1, 15, 1, 6, 10, 11, 9` = **70**, matching the §1 table figure for figure. §6.9's 20% tolerance is untouched. **Does not fire.**

**Content-tile classification: one error.**

| Screen | Audit said | Actual | Verdict |
|---|---|---|---|
| `HandbookScreen` | no tiles | **`GRID_COLS`-wide chapter grid**, per-cell hover + `renderItem`, `catCells` hit regions — [HandbookScreen.java:209-221](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L209) | 🔴 **audit was wrong.** Chapters are module-gated content entities. This *is* a §2.6 surface. |
| `SpellTeacherScreen` | no tiles | Iterates `offers` at [:54](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java#L54) but renders each as a vanilla `Button` with a `§a Learn` / `§7 Locked` label — no drawn tile | ⚠ **ambiguous.** Per-entity surface, but a widget not a draw call. `drawStateBadge` cannot decorate it without a custom widget. Flagged, not resolved. |
| `WandmakersBenchScreen` | no tiles | Iterates `WandFlexibility.values()` — a *property* enum, not content entities | ✅ audit correct |

**Revised in-scope tile surfaces: 7, not 6** — Bestiary, **Handbook**, SpellMenu, CharacterSheet, HeritageSelection, SkillTree, AbilityWheel.

**One structural note for Phase 2:** of the 10 in-scope screens, **exactly one is a container screen** — `WandmakersBenchScreen`, which is the §3.1 blocked one. So §2.7's "slot reskin on every container screen, geometry verified unchanged" resolves to a single screen whose verification is blocked by definition. The other 5 container screens (`NifflerPouch`, `HermionesBag`, `OllivanderTrial`, `PocketConfigurator`) are all deferred.

## 0′.2 Existing API surface — verbatim

### `WizardsPalette` — 21 tokens, 2 brands

```java
// Surfaces, dark to light
INK = 0xFF1E1A22;  WELL = 0xFF3A2621;  PLATE = 0xFF663A31;  PLATE_2 = 0xFF71443A;
RAIL = 0xFF8A5240;  SELECT = 0xFF8A5A34;
// Gold filigree
LINE = 0xFFA4764A;  EDGE_HI = 0xFFC08A5A;  BRASS = 0xFFDBA86D;  BRASS_HI = 0xFFF5E4B0;
THUMB = 0xFFC9A06B;
// Text
TEXT = 0xFFF3E6D2;  TEXT_DIM = 0xFFC2A78F;
// Indicators
PIP_ON = 0xFFF5E4B0;  PIP_OFF = 0xFF5E3A2E;
// Ministry of Magic — the mod's second brand
MINISTRY = 0xFF3E1F47;  MINISTRY_DARK = 0xFF221328;  MINISTRY_LIGHT = 0xFF5C3E66;
PARCHMENT = 0xFFEFE7CF;  PARCHMENT_SHADE = 0xFFE6DCBE;  PARCHMENT_INK = 0xFF3A2E24;
```

The anti-drift warning, **verbatim** — this is the rule v2 §2.1 is enforcing:

> *"This exists because it did not. The values lived in `BestiaryColors`, scoped to one screen, so every other screen invented its own approximation of the same leather and drifted: the wand trial had ended up on a cold blue-lavender scheme that shared no hue with anything else in the mod. Import this rather than adding another literal."*

And the Ministry brand note, **verbatim** — the basis for the v2 purple ruling:

> *"`MINISTRY` is sampled straight off `handbook/emblem.png` — it is that file's dominant colour to the byte … Listed here so it reads as a brand rather than as drift: a hue audit that only knows about the leather family flags these as strays and invites someone to 'correct' them."*

Semantic colour (danger red, Floo green, Avada green) is **deliberately excluded** — *"Those carry meaning rather than theme and belong with the feature that means them."*

### `WizardsMetrics` — do not modify

```java
SPACE_XS = 2;  SPACE_S = 4;  SPACE_M = 8;  SPACE_L = 12;
SPACE_XL = 16; SPACE_XXL = 24; SPACE_XXXL = 32;

LINE_TIGHT = 10;  LINE_BODY = 12;  LINE_SECTION = 16;  LINE_TITLE = 20;

PANEL_MODAL_W = 256;  PANEL_MODAL_H = 144;
PANEL_STANDARD_W = 320; PANEL_STANDARD_H = 240;
PANEL_WIDE_W = 400;  PANEL_WIDE_H = 256;  PANEL_CONTAINER_W = 176;

PANEL_SPRITE_SIZE = 32;  PANEL_SPRITE_BORDER = 8;   // the cut. v2 §2.4 ruling matches.
DIVIDER_H = 8;  SCROLLBAR_W = 8;  ROW_H = 16;
```

`LINE_TITLE = 20` already covers the header type role (§2.5). **No new metric needed.**

### `McStylePanel` — full public surface

```java
// Primitives (pre-existing, untouched)
void drawTiled(GuiGraphics, Identifier, int x, int y, int w, int h, int tile)
void drawTexture(GuiGraphics, Identifier, int x, int y, int w, int h, int srcW, int srcH)
void drawNineSlice(GuiGraphics, Identifier, int x, int y, int w, int h, int ts, int b)
void drawTexturedPanel(GuiGraphics, int x, int y, int w, int h)
void drawPanel(GuiGraphics, int x, int y, int w, int h, int fill, int hiTL, int shBR)
void drawBorder(GuiGraphics, int x, int y, int w, int h, int hiTL, int shBR)

// Themed components — the layer being adopted
static final Identifier THEME_PANEL, THEME_PANEL_INSET, THEME_DIVIDER,
                        THEME_SCROLL_TRACK, THEME_SCROLL_THUMB;
void drawThemedPanel(GuiGraphics, int x, int y, int w, int h)
void drawThemedInset(GuiGraphics, int x, int y, int w, int h)
void drawDivider  (GuiGraphics, int x, int y, int w)
void drawScrollbar(GuiGraphics, int x, int y, int trackH, int thumbY, int thumbH)
void drawRow      (GuiGraphics, int x, int y, int w, int h, boolean selected)
```

**Both existing `drawThemed*` call sites** — these are the two that must not break (§6.2):

1. [BestiaryScreen.java:212](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L212) and [:218](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L218) — `McStylePanel.drawScrollbar(...)`
2. [PlayerModelViewport.java:51](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/PlayerModelViewport.java#L51) — `McStylePanel.drawThemedInset(g, x, y, w, h)`

Both are inside in-scope screens (Bestiary, CharacterSheet). Adding skin-aware overloads alongside the current no-skin signatures keeps both compiling untouched. **§6.2 does not fire.**

### `tools/gui_chrome.py` — structure

- **Palette table:** module-level constants at lines 45–58, `NAME = hx("#RRGGBB")`, sampled from `wand_hud_overlay.png` / `wand_hud_bg.png`. Mirrors `WizardsPalette`'s names 1:1 by design — *"the constant names deliberately mirror that file's so the pairing stays obvious."*
- **Emission:** a flat `ASSETS` dict of `"relpath.png" -> zero-arg factory`, driven by `main()` with `--force` / `--only`. Every output goes through `posterise(img, 12)` then `save(..., MARKER)`.
- **Regeneration contract:** `is_regenerable(path, MARKER)` — the generator only overwrites PNGs carrying its own marker, so hand art is never clobbered.
- **Theme targets:** `a_theme_panel`, `a_theme_panel_inset`, `a_theme_divider`, `a_theme_scrollbar_track`, `a_theme_scrollbar_thumb`, sharing one `theme_frame(d, size, seat, rule, filigree, highlight, lowlight)` that draws **four rules inside the 8px border**.
- **Nine-slice cut, as shipped:** `THEME_TS = 32`, `THEME_B = 8`. Reasoning is recorded in the source and is *not* arbitrary:

  > *"32x32 on an 8px border, so the stretched centre is 16x16. The per-screen sheets above are 24x24 on a 3px border; 32/8 is the same shape at a power-of-two size, and the fatter border leaves room for the four-rule frame the wand HUD actually has (dark seat, shadowed gold, filigree, highlight) instead of the two it fits in 3px."*

  **This is why v2's cut-on-8 ruling is correct and v1's 6 was not.** A 6px border cannot seat four rules.

- **Shipped sprite measurements** (verified with Pillow at this HEAD):

  | File | Dim | Colours | `.mcmeta` |
  |---|---|---:|---|
  | `theme/panel.png` | 32×32 | 12 | **none** |
  | `theme/panel_inset.png` | 32×32 | 12 | **none** |
  | `theme/divider.png` | 32×8 | 4 | **none** |
  | `theme/scrollbar_track.png` | 8×32 | 6 | **none** |
  | `theme/scrollbar_thumb.png` | 8×32 | 6 | **none** |

  No `.mcmeta` exists because these are standalone PNGs hand-sliced by `drawNineSlice`, not atlas sprites. Moving to `atlases/gui.json` + `blitSprite` (§2.8) means each gains a `.mcmeta` and moves under `textures/gui/sprites/<skin>/`.

## 0′.3 🔴 Skin palette vs existing tokens — §6.3 collisions

Every one of the 25 skin colours in §2.3 was measured against all 21 shipped tokens (Euclidean RGB distance). **Six land within a few points of an existing token:**

| Skin colour | Hex | Nearest token | Token hex | Dist |
|---|---|---|---|---:|
| `MINISTRY.frame` | `#3E1F47` | `MINISTRY` | `#3E1F47` | **0.0 — byte-identical** |
| `FIELD_NOTEBOOK.accent` | `#8A5A2B` | `SELECT` | `#8A5A34` | 9.0 |
| `WORKBENCH.frame` | `#3A2718` | `WELL` | `#3A2621` | 9.1 |
| `FIELD_NOTEBOOK.ink` | `#22201C` | `INK` | `#1E1A22` | 9.4 |
| `MINISTRY.base` | `#E4DCC8` | `PARCHMENT_SHADE` | `#E6DCBE` | 10.2 |
| `GOBLIN_LEDGER.base` | `#E6DFC9` | `PARCHMENT_SHADE` | `#E6DCBE` | 11.4 |

Near-misses in the 12–25 band (distinct enough to warrant their own token, listed for the record): `MINISTRY.ink` ↔ `INK` (12.4), `STAR_CHART.base` ↔ `INK` (13.8), `GOBLIN_LEDGER.ink` ↔ `INK` (17.2), `GOBLIN_LEDGER.frame` ↔ `WELL` (18.1), `WORKBENCH.ink` ↔ `INK` (19.1), `FIELD_NOTEBOOK.base` ↔ `PARCHMENT_SHADE` (22.8), `WORKBENCH.muted` ↔ `SELECT` (23.3).

§2.1 prescribes the action (**use the token**) and §6.3 requires the report. Five of the six resolve mechanically under §2.1. `MINISTRY.frame` at distance 0.0 is not a collision at all — it is the v2 purple ruling landing exactly on the token, as intended.

**One genuine design consequence, which §2.1 cannot decide:** `MINISTRY.base` and `GOBLIN_LEDGER.base` both collapse onto `PARCHMENT_SHADE`. Reusing the token per §2.1 gives the **Handbook and the Gringotts vault the same page colour**, and those two skins are supposed to read as different materials (Ministry memo stock vs. goblin ruled paper). Needs a ruling: share the token, or keep the 1.2-point-apart pair as two distinct tokens.

## 0′.4 The 20 deferred screens — classified, not themed

Per §2.2. **Permanently exempt (4):** `BeamDebugScreen`, `BeamStyleScreen`, `MorphDebugScreen` (debug should look like debug), `MaraudersMapScreen` (canon artifact with a mandated look).

The remaining **20**, classified against the skin rule. **None themed. Proposals only.**

| Screen | Proposed | Basis |
|---|---|---|
| `DarkArtsGateScreen` | `MINISTRY` | A restriction notice on Unforgivables — Ministry speech act |
| `FlooNetworkScreen` | `MINISTRY` | Floo Network Authority is a Ministry department |
| `ApparitionSelectorScreen` | `MINISTRY` | Apparition licensing is Ministry-administered |
| `OWLExamScreen` | `MINISTRY` | Wizarding Examinations Authority |
| `OWLResultsReadOnlyScreen` | `MINISTRY` | same |
| `ProfessionSelectionScreen` | `MINISTRY` | career placement follows the O.W.L. result |
| `WizardsConfigScreen` | ⚠ exempt-candidate | Mod configuration is tooling, not diegetic — same argument as the debug screens |
| `WizardsConfigCategoryScreen` | ⚠ exempt-candidate | same |
| `SkillAccessDeniedScreen` | `STAR_CHART` | sibling of the skill web; router picks one or the other |
| `VocationSelectionScreen` | `STAR_CHART` | opened from the skill web, specialises its trees |
| `PensieveScreen` | `STAR_CHART` | silver-on-void memory surface; nearest existing material |
| `OllivanderTrialScreen` | `WORKBENCH` | Ollivander's shop; already the mod's best-themed screen (14 palette reads) |
| `PocketConfiguratorScreen` | `WORKBENCH` | enchanted luggage — a made object |
| `HermionesBagScreen` | `FIELD_NOTEBOOK` | field kit under an Extension Charm |
| `NifflerPouchScreen` | `FIELD_NOTEBOOK` | creature containment — Scamander's domain |
| `DiaryWriteScreen` | 🔴 **no skin fits** | Riddle's diary is a Horcrux. None of the five materials is a dark artefact. |
| `DiaryPossessionScreen` | 🔴 **no skin fits** | same |
| `MirrorCallScreen` | 🔴 **no skin fits** | Two-Way Mirror — an artefact with its own look, the `MaraudersMap` argument |
| `MirrorViewScreen` | 🔴 **no skin fits** | same |
| `ImperioCommandScreen` | 🔴 **no skin fits** | an Unforgivable being cast; wearing Ministry livery would be backwards |

**Five screens have no home in the five-skin system.** Two artefact screens (Mirror ×2) plausibly join `MaraudersMapScreen` as permanently exempt. Three dark-arts screens (Diary ×2, Imperio) would need a sixth skin. Both are the follow-up prompt's calls, not this one's — recorded here so that prompt starts from ground truth.

## 0′.5 Stop-and-report status at Phase 0′

| Cond. | Verdict |
|---|---|
| §6.1 screen un-themeable without restructuring | Not determinable pre-implementation; nothing found that obviously blocks |
| §6.2 extending would break public API or a call site | ✅ **Does not fire.** Skin-aware overloads leave both call sites compiling |
| §6.3 skin colour duplicates a token | 🔴 **FIRES — 6 collisions (§0′.3).** Five resolve under §2.1; one needs a ruling |
| §6.4 build fails | ✅ **Does not fire.** BUILD SUCCESSFUL |
| §6.5 slot geometry unpreservable | ✅ `renderBg` before `renderSlots`, per §2.5 of the main audit |
| §6.6 font fallback | Deferred to Phase 4 per §2.5 |
| §6.7 client type in common code | ✅ Does not fire |
| §6.8 mixin required | ✅ Does not fire |
| §6.9 count >10 or literals +20% | ✅ **Does not fire.** 10 screens, 70 literals vs 70 |
| §6.10 unresolved prompt/repo conflict | ✅ Does not fire — §0's repo-wins rule resolves the cut-on-8 question, and the source reasoning (§0′.2) confirms 8 is load-bearing |

## 0′.6 Pre/post-flight

- Pre-flight `git status --porcelain`: 24 entries, 0 staged.
- Post-flight: identical plus the modification to this file. Nothing else read-modified, nothing staged from the Obscurial/Horntail tangle.
