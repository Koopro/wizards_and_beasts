# GUI_AUDIT.md — Phase 0, GUI Visual Identity Pass

**Read-only audit. No code written.**

- Repo: `wizards_and_beasts`, MC 1.21.11 / NeoForge 21.11.x
- Branch: `fix/chamber-of-secrets-module-gate`
- HEAD at audit: `1c6697b9c21e03a51e2e6161e6a177e9fafc8cc4`
- Pre-flight `git status --porcelain`: 45 entries, none GUI-related (house-banner work + two untracked audit `.md` files). Snapshot stored in the session scratchpad.

> **STOP-AND-REPORT FIRED.** See §8. Three Phase 0 stop conditions are met, the most
> important being §6: a shared GUI theme layer **already exists and is already partially
> applied**, including a nine-slice helper and a Pillow generator. Phase 1 as briefed would
> build a second one. Do not proceed to Phase 1 without a decision.

---

## 1. Screen inventory

34 screen classes. Every one lives under `at.koopro.wizardsandbeasts.client.**`. Six are
`AbstractContainerScreen` (menu-backed); the rest are plain `Screen`.

Excluded from the count as *not* screens despite the filename: `client/camera/ScreenShakeHandler`,
`client/camera/ScreenShakeMath`, `client/event/InventoryScreenInjector`,
`client/network/ClientScreenHooks`, `client/spell/render/CrucioScreenRenderer`,
`client/skill/gui/SkillScreenRouter` (a router, no `Screen` subclass).

| # | Class | Path | Base | Title | Menu | Reachable via |
|---|---|---|---|---|---|---|
| 1 | `client.ability.wheel.AbilityWheelScreen` | [AbilityWheelScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java) | `Screen` | `Component.translatable` | — | [AbilityWheelController.java:85](src/main/java/at/koopro/wizardsandbeasts/client/ability/AbilityWheelController.java#L85) — ability-wheel keybind |
| 2 | `client.apparition.gui.ApparitionSelectorScreen` | [ApparitionSelectorScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java) | `Screen` | `Component.translatable` | — | [ClientApparitionPointsState.java:37](src/main/java/at/koopro/wizardsandbeasts/client/apparition/state/ClientApparitionPointsState.java#L37) — on points sync |
| 3 | `client.bestiary.gui.BestiaryScreen` | [BestiaryScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java) | `Screen` | `gui.wizards_and_beasts.bestiary.title` | — | [ClientScreenHooks.java:62](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L62) — S2C |
| 4 | `client.bestiary.niffler.NifflerPouchScreen` | [NifflerPouchScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/niffler/NifflerPouchScreen.java) | `AbstractContainerScreen<NifflerPouchMenu>` | from menu | ✅ `NIFFLER_POUCH` | [WizardsAndBeastsClient.java:88](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L88) |
| 5 | `client.currency.gui.GringottsScreen` | [GringottsScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java) | `Screen` | `Component.literal("Gringotts Wizarding Bank")` ⚠ hardcoded | — | [ClientScreenHooks.java:36](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L36) |
| 6 | `client.floo.gui.FlooNetworkScreen` | [FlooNetworkScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java) | `Screen` | `Component.literal` ⚠ | — | [ClientScreenHooks.java:32](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L32) |
| 7 | `client.gui.MorphDebugScreen` | [MorphDebugScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java) | `Screen` | `Component.literal("Morph Debug")` ⚠ | — | [MorphWandClientHooks.java:11](src/main/java/at/koopro/wizardsandbeasts/client/wand/MorphWandClientHooks.java#L11) — dev item |
| 8 | `client.gui.character.CharacterSheetScreen` | [CharacterSheetScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java) | `Screen` | `gui.wizards_and_beasts.character_sheet.title` | — | inventory button, `C` key, S2C — three sites |
| 9 | `client.gui.config.DarkArtsGateScreen` | [DarkArtsGateScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java) | `Screen` | `Component.literal("Unforgivable Arts — Restricted Access")` ⚠ | — | [WizardsConfigScreen.java:170](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L170) |
| 10 | `client.gui.config.WizardsConfigCategoryScreen` | [WizardsConfigCategoryScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigCategoryScreen.java) | `Screen` | `Component.literal` ⚠ | — | [WizardsConfigScreen.java:173](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L173) |
| 11 | `client.gui.config.WizardsConfigScreen` | [WizardsConfigScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java) | `Screen` | `Component.literal("Wizards & Beasts Configuration")` ⚠ | — | `IConfigScreenFactory` — [WizardsAndBeastsClient.java:47](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L47); mod-list Config button |
| 12 | `client.handbook.HandbookScreen` | [HandbookScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java) | `Screen` | `gui.wizards_and_beasts.handbook.title` | — | [ClientScreenHooks.java:66](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L66) |
| 13 | `client.heritage.gui.HeritageSelectionScreen` | [HeritageSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageSelectionScreen.java) | `Screen` | `gui.wizards_and_beasts.heritage.title` | — | [ClientScreenHooks.java:50](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L50) — first join |
| 14 | `client.item.HermionesBagScreen` | [HermionesBagScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/item/HermionesBagScreen.java) | `AbstractContainerScreen<HermionesBagMenu>` | from menu | ✅ `HERMIONES_BAG` | [WizardsAndBeastsClient.java:89](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L89) |
| 15 | `client.map.MaraudersMapScreen` | [MaraudersMapScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java) | `Screen` | `Component.literal("Marauder's Map")` ⚠ | — | [MapClientHandler.java:13](src/main/java/at/koopro/wizardsandbeasts/client/map/MapClientHandler.java#L13) |
| 16 | `client.owl.screen.OWLExamScreen` | [OWLExamScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java) | `Screen` | `owls.screen.title` | — | [ExaminationDeskClientHandler.java:34](src/main/java/at/koopro/wizardsandbeasts/client/owl/ExaminationDeskClientHandler.java#L34) |
| 17 | `client.owl.screen.OWLResultsReadOnlyScreen` | [OWLResultsReadOnlyScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java) | `Screen` | `owls.screen.results_title` | — | [ExaminationDeskClientHandler.java:30](src/main/java/at/koopro/wizardsandbeasts/client/owl/ExaminationDeskClientHandler.java#L30) |
| 18 | `client.owl.screen.ProfessionSelectionScreen` | [ProfessionSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java) | `Screen` | `owls.screen.profession_title` | — | from OWL exam + results screens |
| 19 | `client.skill.gui.SkillAccessDeniedScreen` | [SkillAccessDeniedScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillAccessDeniedScreen.java) | `Screen` | `screen.…skill_access_denied.title` | — | [SkillScreenRouter.java:32,39](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillScreenRouter.java#L32) |
| 20 | `client.skill.gui.SkillTreeScreen` | [SkillTreeScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java) | `Screen` | `screen.…skill_tree.title` | — | [SkillScreenRouter.java:37](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillScreenRouter.java#L37) |
| 21 | `client.skill.gui.VocationSelectionScreen` | [VocationSelectionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/VocationSelectionScreen.java) | `Screen` | `screen.…vocation.title` | — | [SkillTreeScreen.java:122](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L122) |
| 22 | `client.spell.gui.BeamDebugScreen` | [BeamDebugScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamDebugScreen.java) | `Screen` | `Component.literal("Beam Debug Editor")` ⚠ | — | [ClientScreenHooks.java:40](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L40) — **debug** |
| 23 | `client.spell.gui.BeamStyleScreen` | [BeamStyleScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java) | `Screen` | `Component.literal("Beam Style Editor")` ⚠ | — | [ClientScreenHooks.java:46](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L46) — **debug** |
| 24 | `client.spell.gui.ImperioCommandScreen` | [ImperioCommandScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/ImperioCommandScreen.java) | `Screen` | `Component.literal("Imperio")` ⚠ | — | [WandCastClient.java:51](src/main/java/at/koopro/wizardsandbeasts/client/wand/WandCastClient.java#L51) |
| 25 | `client.spell.gui.SpellMenuScreen` | [SpellMenuScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuScreen.java) | `Screen` | `Component.literal("Spell Menu")` ⚠ | — | [SpellInputController.java:28](src/main/java/at/koopro/wizardsandbeasts/client/spell/input/SpellInputController.java#L28) |
| 26 | `client.spell.gui.SpellTeacherScreen` | [SpellTeacherScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java) | `Screen` | `Component.literal("Spell Teacher")` ⚠ | — | [ClientScreenHooks.java:58](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L58) |
| 27 | `client.trinket.gui.DiaryPossessionScreen` | [DiaryPossessionScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryPossessionScreen.java) | `Screen` | `Component.literal("…")` ⚠ | — | [ClientScreenHooks.java:102](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L102) |
| 28 | `client.trinket.gui.DiaryWriteScreen` | [DiaryWriteScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java) | `Screen` | `Component.literal("Riddle's Diary")` ⚠ | — | [ClientScreenHooks.java:94](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L94) |
| 29 | `client.trinket.gui.MirrorCallScreen` | [MirrorCallScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorCallScreen.java) | `Screen` | `Component.literal("Two-Way Mirror")` ⚠ | — | [ClientScreenHooks.java:78](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L78) |
| 30 | `client.trinket.gui.MirrorViewScreen` | [MirrorViewScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java) | `Screen` | `Component.literal("Two-Way Mirror")` ⚠ | — | [ClientScreenHooks.java:82](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L82) |
| 31 | `client.trinket.gui.PensieveScreen` | [PensieveScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java) | `Screen` | `Component.literal("Pensieve")` ⚠ | — | [ClientScreenHooks.java:74](src/main/java/at/koopro/wizardsandbeasts/client/network/ClientScreenHooks.java#L74) |
| 32 | `client.trunk.gui.PocketConfiguratorScreen` | [PocketConfiguratorScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/trunk/gui/PocketConfiguratorScreen.java) | `AbstractContainerScreen<PocketConfiguratorMenu>` | from menu | ✅ `POCKET_CONFIGURATOR` | [WizardsAndBeastsClient.java:87](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L87) |
| 33 | `client.wand.gui.OllivanderTrialScreen` | [OllivanderTrialScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/OllivanderTrialScreen.java) | `AbstractContainerScreen<OllivanderTrialMenu>` | from menu | ✅ `OLLIVANDER_TRIAL` | [WizardsAndBeastsClient.java:86](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L86) |
| 34 | `client.wand.gui.WandmakersBenchScreen` **← EXCLUDED** | [WandmakersBenchScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java) | `AbstractContainerScreen<WandmakersBenchMenu>` | from menu | ✅ `WANDMAKERS_BENCH` | [WizardsAndBeastsClient.java:85](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsClient.java#L85) |

**Notes**

- The wandmaker screen (#34) **is present**, is confirmed excluded per §4 of the brief
  (`AGENT_PROMPT_wandmaker_table_repair.md` owns it), and is excluded from every count below.
- No screen was found to be unreachable. Every one has at least one open site. Two (#22, #23)
  are debug-only, opened by `BeamDebugOpenS2CPayload` → `ClientScreenHooksInvoker`.
- Not verified by opening in-game — this is a static-reachability audit only. No screen was
  observed to throw.
- `ProfessionSelectionScreen` also constructs a vanilla `ConfirmScreen` at
  [ProfessionSelectionScreen.java:156](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L156).
  That is a vanilla screen, not a mod screen; out of scope.

**Net for scope: 33 themeable screens** (34 minus wandmaker), of which 5 are menu-backed.

---

## 2. Texture inventory

91 PNGs under `assets/wizards_and_beasts/textures/gui/**`.

### 2.1 Referenced

| Path | Dim | Referenced by |
|---|---|---|
| `bestiary/screen.png` | 320×200 | [BestiaryScreen.java:45](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L45) |
| `bestiary/left_panel.png` | 120×168 | BestiaryScreen:47 |
| `bestiary/right_panel.png` | 190×190 | BestiaryScreen:49 |
| `bestiary/row.png` | 112×14 | BestiaryScreen:51 |
| `bestiary/header.png` | 112×14 | BestiaryScreen:53 |
| `bestiary/scroll_track.png` | 3×120 | BestiaryScreen:55 |
| `bestiary/scroll_thumb.png` | 3×24 | BestiaryScreen:57 |
| `bestiary/entry_placeholder.png` | 32×32 | BestiaryScreen:59 |
| `character_sheet/background.png` | 320×240 | [CharacterSheetTextures.java:19](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetTextures.java#L19) |
| `character_sheet/icon_tab.png` | 16×16 | CharacterSheetTextures:26 |
| `character_sheet/panel.png` | 24×24 | CharacterSheetTextures:30 |
| `character_sheet/panel_sel.png` | 24×24 | CharacterSheetTextures:34 |
| `config/parchment.png` | 64×64 | [WizardsConfigScreen.java:35](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L35) |
| `config/card.png` | 84×64 | WizardsConfigScreen:37 |
| `config/card_dark.png` | 84×64 | WizardsConfigScreen:39 |
| `container/hermiones_bag.png` | 256×256 | [HermionesBagScreen.java:22](src/main/java/at/koopro/wizardsandbeasts/client/item/HermionesBagScreen.java#L22) |
| `container/creative_inventory/tab_main.png` | 256×256 | [ModCreativeTabs.java:49](src/main/java/at/koopro/wizardsandbeasts/registry/ModCreativeTabs.java#L49) (prefix build) |
| `container/creative_inventory/tab_decorative.png` | 256×256 | ModCreativeTabs:49 |
| `handbook/book.png` | 340×212 | [HandbookScreen.java:51](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L51) |
| `handbook/emblem.png` | 72×72 | HandbookScreen:53 |
| `heritage/wax_seal.png` | 32×32 | [HeritageCeremonyRenderer.java:45](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L45) |
| `heritage/parchment.png` | 64×64 | HeritageCeremonyRenderer:49 |
| `niffler_pouch.png` | 256×256 | [NifflerPouchScreen.java:14](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/niffler/NifflerPouchScreen.java#L14) |
| `baby_niffler_pouch.png` | 256×256 | NifflerPouchScreen:16 |
| `obscurial/stress_meter.png` | 512×72 | [ObscurialStressMeterLayout.java:12](src/main/java/at/koopro/wizardsandbeasts/client/heritage/hud/ObscurialStressMeterLayout.java#L12) — HUD, out of scope |
| `skill_tree/panel.png` | 64×64 | [SkillTreeRenderHelper.java:25](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java#L25) |
| `skill_tree/chart/*.png` (11) | 16–512 | [SkillTreeChartTextures.java:22-34](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L22) (prefix build) |
| `spell_menu/panel.png` | 64×64 | [SpellMenuRenderHelper.java:27](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java#L27) |
| `spell_menu/slot.png` | 40×40 | [SpellMenuScreen.java:34](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuScreen.java#L34) |
| `spell_menu/slot_active.png` | 40×40 | SpellMenuScreen:36 |
| `sprites/wand_hud/**` (40) | 92×92 / 256×256 | [WandHudSprites.java](src/main/java/at/koopro/wizardsandbeasts/client/hud/WandHudSprites.java) — moved onto vanilla's GUI sprite atlas; drawn with `blitSprite`, no per-frame `ResourceManager` lookup |
| `wandmakers_bench.png` | 176×196 | [WandmakersBenchScreen.java:26](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/WandmakersBenchScreen.java#L26) — excluded screen |

### 2.2 Referenced by zero screens (orphan candidates — **listed, not deleted**)

| Path | Dim | Finding |
|---|---|---|
| `type_icons/centaur.png` | 64×64 | zero refs in Java **and** zero refs in `src/main/resources` / `src/generated` datapack JSON |
| `type_icons/giant.png` | 64×64 | same |
| `type_icons/goblin.png` | 64×64 | same |
| `type_icons/house_elf.png` | 64×64 | same |
| `type_icons/merpeople.png` | 64×64 | same |
| `type_icons/obscurial.png` | 64×64 | same |
| `type_icons/vampire.png` | 64×64 | same |
| `type_icons/veela.png` | 64×64 | same |
| `type_icons/werewolf.png` | 64×64 | same |
| `type_icons/wizardkind.png` | 64×64 | same |

`type_icons/placeholder.png` is **not** orphaned — it is `ModTextures.GENERIC_ICON_PLACEHOLDER`
([ModTextures.java:14](src/main/java/at/koopro/wizardsandbeasts/client/ModTextures.java#L14)),
though its only consumer is the last-resort branch of `resolveWandHudSpellIcon`.

**Root cause of the orphaning** (do not fix here — logged for the punchlist):
`WizardsAndBeastsUiTokens.HeritageSelection` still carries `TYPE_ICON_X`, `TYPE_ICON_Y`,
`TYPE_ICON_SIZE` ([WizardsAndBeastsUiTokens.java:204-206](src/main/java/at/koopro/wizardsandbeasts/client/gui/WizardsAndBeastsUiTokens.java#L204))
but `HeritageSelectionScreen` contains **no `icon` reference at all** — the screen was rewritten
onto `HeritageCeremonyRenderer` and stopped drawing the icons. The tokens and the art are both
dead. `BestiaryEntry` has an `iconTexture` codec field
([BestiaryEntry.java:32](src/main/java/at/koopro/wizardsandbeasts/bestiary/BestiaryEntry.java#L32))
which is a plausible second intended consumer.

### 2.3 Screens referencing zero textures (fully procedural)

24 of the 33 in-scope screens draw entirely with `fill()` / `drawString()` / `McStylePanel`
and reference no texture of their own:

`AbilityWheelScreen`, `ApparitionSelectorScreen`, `GringottsScreen`, `FlooNetworkScreen`,
`MorphDebugScreen`, `DarkArtsGateScreen`, `WizardsConfigCategoryScreen`,
`HeritageSelectionScreen`¹, `MaraudersMapScreen`, `OWLExamScreen`, `OWLResultsReadOnlyScreen`,
`ProfessionSelectionScreen`, `SkillAccessDeniedScreen`, `VocationSelectionScreen`,
`BeamDebugScreen`, `BeamStyleScreen`, `ImperioCommandScreen`, `SpellTeacherScreen`,
`DiaryPossessionScreen`, `DiaryWriteScreen`, `MirrorCallScreen`, `MirrorViewScreen`,
`PensieveScreen`, `PocketConfiguratorScreen`.

¹ delegates its textures to `HeritageCeremonyRenderer`.

### 2.4 Missing directory

`BestiaryScreen.java:201` builds `textures/gui/bestiary/entries/<id>.png`. **That directory does
not exist** — every bestiary entry falls back to `entry_placeholder.png`. Not a theming concern;
logged for the punchlist.

### 2.5 No `theme/` directory exists

There is no `textures/gui/theme/`. The brief's §2.3 target path is unoccupied. The existing
nine-slice sheets are scattered per-feature (`bestiary/`, `character_sheet/`, `config/`,
`skill_tree/`, `spell_menu/`) — see §6.

---

## 3. Draw-call patterns

Counts are raw occurrences per file: `fill(`/`fillGradient(`, `blit*`, `drawString`/
`drawCenteredString`/`drawWordWrap`, `McStylePanel.*`, `WizardsPalette.*`.

Classification: **(a)** blitted custom texture, **(b)** vanilla nine-slice/sprite,
**(c)** procedural `fill`/`drawString` only, **(d)** mixture.

| Screen | fill | blit | text | McPanel | Palette | Class |
|---|---:|---:|---:|---:|---:|---|
| AbilityWheelScreen | 7 | 1 | 3 | 0 | 4 | **d** — palette-aware fills + one icon blit |
| ApparitionSelectorScreen | 2 | 0 | 4 | 0 | 5 | **c** — palette-aware |
| BestiaryScreen | 4 | 1 | 10 | 0 | 11 | **d** — 8 custom textures + palette fills |
| NifflerPouchScreen | 0 | 1 | 0 | 0 | 0 | **a** — pure texture blit |
| GringottsScreen | 3 | 0 | 12 | 1 | 0 | **d** — `McStylePanel` + raw fills |
| FlooNetworkScreen | 4 | 0 | 5 | 1 | 0 | **d** |
| MorphDebugScreen | 1 | 0 | 5 | 1 | 0 | **d** |
| CharacterSheetScreen | 2 | 0 | 3 | 3 | 0 | **d** — `McStylePanel` + `CharacterSheetTextures` |
| DarkArtsGateScreen | 4 | 0 | 0 | 0 | 0 | **c** |
| WizardsConfigCategoryScreen | 2 | 0 | 2 | 0 | 0 | **c** |
| WizardsConfigScreen | 6 | 0 | 4 | 2 | 0 | **d** — 3 custom textures + `McStylePanel` |
| HandbookScreen | 18 | 3 | 4 | 0 | 8 | **d** — heaviest procedural screen; 2 textures |
| HeritageSelectionScreen | 0 | 0 | 1 | 0 | 0 | **d** *via* `HeritageCeremonyRenderer` (fill=21, blit=2) |
| HermionesBagScreen | 0 | 2 | 0 | 0 | 0 | **a** |
| MaraudersMapScreen | 9 | 0 | 13 | 1 | 0 | **d** |
| OWLExamScreen | 1 | 0 | 4 | 0 | 0 | **c** |
| OWLResultsReadOnlyScreen | 1 | 0 | 3 | 0 | 0 | **c** |
| ProfessionSelectionScreen | 1 | 0 | 2 | 0 | 0 | **c** |
| SkillAccessDeniedScreen | 0 | 0 | 3 | 1 | 0 | **b** — `McStylePanel` only |
| SkillTreeScreen | 2 | 2 | 1 | 0 | 0 | **d** *via* `SkillTreeRenderHelper` (fill=16, McPanel=2) + `SkillTreeChartTextures` |
| VocationSelectionScreen | 2 | 0 | 5 | 0 | 0 | **c** |
| BeamDebugScreen | 4 | 0 | 5 | 2 | 0 | **d** — debug |
| BeamStyleScreen | 3 | 0 | 3 | 2 | 0 | **d** — debug |
| ImperioCommandScreen | 0 | 0 | 1 | 0 | 0 | **c** — thinnest screen in the mod |
| SpellMenuScreen | 0 | 4 | 1 | 0 | 0 | **a** *+* `SpellMenuRenderHelper` (fill=2, McPanel=2) |
| SpellTeacherScreen | 0 | 0 | 3 | 1 | 0 | **b** |
| DiaryPossessionScreen | 1 | 0 | 2 | 0 | 0 | **c** |
| DiaryWriteScreen | 1 | 0 | 2 | 0 | 0 | **c** |
| MirrorCallScreen | 0 | 0 | 1 | 0 | 0 | **c** |
| MirrorViewScreen | 1 | 2 | 2 | 0 | 0 | **d** — blits a live player-face render |
| PensieveScreen | 2 | 0 | 5 | 0 | 0 | **c** |
| PocketConfiguratorScreen | 2 | 0 | 5 | 0 | 7 | **c** — palette-aware |
| OllivanderTrialScreen | 3 | 0 | 7 | 3 | 14 | **d** — most fully themed screen in the mod |
| ~~WandmakersBenchScreen~~ | 4 | 0 | 7 | 1 | 6 | *excluded* |

**Line-anchored examples**

- (a) pure blit: [NifflerPouchScreen.java:14-16](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/niffler/NifflerPouchScreen.java#L14) declares two 256×256 sheets and the base class blits them.
- (b) nine-slice via helper: [McStylePanel.java:32-50](src/main/java/at/koopro/wizardsandbeasts/client/gui/McStylePanel.java#L32) is the slicer; [SkillTreeRenderHelper.java:25](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java#L25) is a caller.
- (c) procedural, palette-aware: [ApparitionSelectorScreen.java:36-38](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L36) — gradient fills built from local `0x…` constants alongside 5 `WizardsPalette` reads.
- (d) mixture: [HandbookScreen.java](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java) — 18 fills + 3 blits + 8 palette reads; the book chrome is procedural over `book.png`.

**Application-order candidate for §2.4 step 1** (fewest widgets/draw calls):
`ImperioCommandScreen` (0 fill, 0 blit, 1 text) then `MirrorCallScreen` (0/0/1).

---

## 4. Widget inventory

No screen in this mod subclasses `AbstractWidget` directly for its own layout primitives except
in `config/` and `heritage/`. Everything else is either a vanilla `Button` or a plain static
render helper with no widget identity.

| Class | Kind | Used by |
|---|---|---|
| [ConfigWidgets.ToggleButton](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L158) | `extends AbstractButton` | `WizardsConfigCategoryScreen` |
| [ConfigWidgets.EnumCycleButton](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L191) | `extends AbstractButton` | `WizardsConfigCategoryScreen` |
| [ConfigWidgets.RangedSlider](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L225) | `extends AbstractSliderButton` | `WizardsConfigCategoryScreen` |
| [HeritageRailEntry](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java) | `AbstractWidget` | `HeritageSelectionScreen` |
| [HeritageVariantChip](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java) | `AbstractWidget` | `HeritageSelectionScreen` |
| [HeritageBlockWidget](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/HeritageBlockWidget.java) | render helper (not a `Widget`) | `CharacterSheetScreen` / `AttributesTab` |
| [PlayerModelViewport](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/PlayerModelViewport.java) | render helper | `CharacterSheetScreen` |
| [SkillTreeBarWidget](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java) | render helper | `SkillsTab` |
| [SpellCardWidget](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java) | render helper | `SpellsTab` |
| [VitalsBarWidget](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java) | render helper | `AttributesTab` |
| [AttributesTab](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java) / [SkillsTab](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java) / [SpellsTab](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SpellsTab.java) | `CharacterTab` impls | `CharacterSheetScreen` |
| [SkillTreeRenderHelper](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java) | static renderer | `SkillTreeScreen` |
| [SpellMenuRenderHelper](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java) | static renderer | `SpellMenuScreen` |
| [HeritageCeremonyRenderer](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java) | static renderer | `HeritageSelectionScreen` |
| [InkRevealRenderer](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/InkRevealRenderer.java) | static renderer | `DarkArtsGateScreen` |

---

## 5. Font & colour usage

### 5.1 Fonts

**No screen applies a non-default font or `Style` variant.** Zero `Style.EMPTY.withFont(` calls
and no `Identifier`-typed font references in client screen code. All text is drawn through
`GuiGraphics#drawString` / `drawCenteredString` / `drawWordWrap` with `Minecraft#font`.
`GuiText` ([client/gui/util/GuiText.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/GuiText.java))
and `UiContrast` ([client/gui/util/UiContrast.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java))
are the only text-side shared helpers.

### 5.2 Colour literals — headline numbers

| File | count |
|---|---:|
| `client/gui/WizardsAndBeastsUiTokens.java` | 99 |
| `client/gui/WizardsPalette.java` | 21 |
| `client/heritage/gui/HeritageCeremonyRenderer.java` | 18 |
| `client/gui/character/widget/SpellCardWidget.java` | 16 |
| `client/skill/gui/SkillTreeChartTextures.java` | 15 |
| `client/gui/character/tab/AttributesTab.java` | 15 |
| `client/gui/character/CharacterSheetScreen.java` | 15 |
| `client/heritage/hud/ObscurialHudTheme.java` | 14 |
| `client/heritage/gui/HeritageRailEntry.java` | 13 |
| `client/map/MaraudersMapScreen.java` | 12 |
| `client/floo/gui/FlooNetworkScreen.java` | 11 |
| `client/currency/gui/GringottsScreen.java` | 11 |
| `client/gui/config/ConfigWidgets.java` | 9 |
| `client/trinket/gui/PensieveScreen.java` | 8 |
| `client/gui/util/UiContrast.java` | 8 |
| …tail below | |

**Total in in-scope screen code — excluding `WizardsPalette`, `WizardsAndBeastsUiTokens` and the
excluded `WandmakersBenchScreen`, and excluding HUD/model/beam/entity render files:
`305` literals across `47` files.** (47 > 33 because screens delegate to render helpers, tabs and
widgets that carry literals of their own — e.g. `HeritageCeremonyRenderer`, `SkillTreeRenderHelper`,
the four `character/widget/*` classes.)

Add the 99 in `WizardsAndBeastsUiTokens` and the mod ships **404 hardcoded ARGB literals in GUI
code** against 21 palette tokens. Brief §2.1 requires all 305 to become token references or
be justified in `MIGRATION_DELTAS.md`.

The full `file:line:literal` list is in [§5.3](#53-full-argb-literal-list) below.

### 5.3 Full ARGB literal list

<!-- LITERAL-LIST-BEGIN -->
Scope: client screen code under the 15 GUI packages, excluding `WizardsPalette.java`, `WizardsAndBeastsUiTokens.java` (both reported in §6) and the excluded `WandmakersBenchScreen.java`.

**Total: 305 literals across 47 files.**

**`client/ability/wheel/AbilityWheelScreen.java`** — 10 literals

- [AbilityWheelScreen.java:45](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L45) `0xC0000000`
- [AbilityWheelScreen.java:45](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L45) `0x00FFFFFF`
- [AbilityWheelScreen.java:46](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L46) `0xD0000000`
- [AbilityWheelScreen.java:46](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L46) `0x00FFFFFF`
- [AbilityWheelScreen.java:47](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L47) `0xF0000000`
- [AbilityWheelScreen.java:47](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L47) `0x00FFFFFF`
- [AbilityWheelScreen.java:52](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L52) `0xFF56D364`
- [AbilityWheelScreen.java:53](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L53) `0xFFFFD24A`
- [AbilityWheelScreen.java:54](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L54) `0xFF6AB7FF`
- [AbilityWheelScreen.java:55](src/main/java/at/koopro/wizardsandbeasts/client/ability/wheel/AbilityWheelScreen.java#L55) `0xB0000000`

**`client/apparition/gui/ApparitionSelectorScreen.java`** — 8 literals

- [ApparitionSelectorScreen.java:36](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L36) `0xD0000000`
- [ApparitionSelectorScreen.java:36](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L36) `0x00FFFFFF`
- [ApparitionSelectorScreen.java:37](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L37) `0xC0000000`
- [ApparitionSelectorScreen.java:37](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L37) `0x00FFFFFF`
- [ApparitionSelectorScreen.java:38](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L38) `0xF0000000`
- [ApparitionSelectorScreen.java:38](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L38) `0x00FFFFFF`
- [ApparitionSelectorScreen.java:43](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L43) `0xC0402030`
- [ApparitionSelectorScreen.java:44](src/main/java/at/koopro/wizardsandbeasts/client/apparition/gui/ApparitionSelectorScreen.java#L44) `0xFFE08080`

**`client/bestiary/gui/BestiaryScreen.java`** — 3 literals

- [BestiaryScreen.java:294](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L294) `0x335A84C5`
- [BestiaryScreen.java:365](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L365) `0x664F5B72`
- [BestiaryScreen.java:366](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/gui/BestiaryScreen.java#L366) `0x664F5B72`

**`client/currency/gui/GringottsScreen.java`** — 11 literals

- [GringottsScreen.java:24](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L24) `0xFFD4AF37`
- [GringottsScreen.java:25](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L25) `0xFFC0C0C0`
- [GringottsScreen.java:26](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L26) `0xFFCD7F32`
- [GringottsScreen.java:103](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L103) `0xFF444466`
- [GringottsScreen.java:109](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L109) `0xFFCCCCCC`
- [GringottsScreen.java:116](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L116) `0xFFCCCCCC`
- [GringottsScreen.java:128](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L128) `0xFF444466`
- [GringottsScreen.java:131](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L131) `0xFFCCCCCC`
- [GringottsScreen.java:134](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L134) `0xFF444466`
- [GringottsScreen.java:140](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L140) `0xFF888888`
- [GringottsScreen.java:146](src/main/java/at/koopro/wizardsandbeasts/client/currency/gui/GringottsScreen.java#L146) `0xFF666666`

**`client/floo/FlooTransitOverlay.java`** — 3 literals

- [FlooTransitOverlay.java:52](src/main/java/at/koopro/wizardsandbeasts/client/floo/FlooTransitOverlay.java#L52) `0x0E5A22`
- [FlooTransitOverlay.java:68](src/main/java/at/koopro/wizardsandbeasts/client/floo/FlooTransitOverlay.java#L68) `0x101418`
- [FlooTransitOverlay.java:70](src/main/java/at/koopro/wizardsandbeasts/client/floo/FlooTransitOverlay.java#L70) `0x21B342`

**`client/floo/gui/FlooNetworkScreen.java`** — 11 literals

- [FlooNetworkScreen.java:28](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L28) `0xFFD4AF37`
- [FlooNetworkScreen.java:29](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L29) `0xFFE8E8E8`
- [FlooNetworkScreen.java:30](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L30) `0xFF4CAF50`
- [FlooNetworkScreen.java:31](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L31) `0xFF666666`
- [FlooNetworkScreen.java:32](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L32) `0xFF8BC34A`
- [FlooNetworkScreen.java:33](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L33) `0xFF444466`
- [FlooNetworkScreen.java:111](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L111) `0x00000000`
- [FlooNetworkScreen.java:113](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L113) `0x80204020`
- [FlooNetworkScreen.java:115](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L115) `0x40808080`
- [FlooNetworkScreen.java:151](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L151) `0x40FFFFFF`
- [FlooNetworkScreen.java:152](src/main/java/at/koopro/wizardsandbeasts/client/floo/gui/FlooNetworkScreen.java#L152) `0xAAFFFFFF`

**`client/gui/MorphDebugScreen.java`** — 6 literals

- [MorphDebugScreen.java:161](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L161) `0xFF404040`
- [MorphDebugScreen.java:164](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L164) `0xFF8B8B8B`
- [MorphDebugScreen.java:170](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L170) `0xFF404040`
- [MorphDebugScreen.java:175](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L175) `0xFF606060`
- [MorphDebugScreen.java:177](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L177) `0xFF606060`
- [MorphDebugScreen.java:185](src/main/java/at/koopro/wizardsandbeasts/client/gui/MorphDebugScreen.java#L185) `0xFF404040`

**`client/gui/character/CharacterSheetScreen.java`** — 15 literals

- [CharacterSheetScreen.java:63](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L63) `0xFF2A1E0F`
- [CharacterSheetScreen.java:64](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L64) `0xFF44321A`
- [CharacterSheetScreen.java:65](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L65) `0xFF1A0F00`
- [CharacterSheetScreen.java:66](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L66) `0xFF44321A`
- [CharacterSheetScreen.java:67](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L67) `0xFFFFEECC`
- [CharacterSheetScreen.java:68](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L68) `0xFFAA9977`
- [CharacterSheetScreen.java:69](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L69) `0xFF4A3A1A`
- [CharacterSheetScreen.java:70](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L70) `0xFF251A0A`
- [CharacterSheetScreen.java:71](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L71) `0xFF66501E`
- [CharacterSheetScreen.java:72](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L72) `0xFF150D00`
- [CharacterSheetScreen.java:73](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L73) `0xFFCCBB99`
- [CharacterSheetScreen.java:74](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L74) `0xFF1E1408`
- [CharacterSheetScreen.java:75](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L75) `0xFFCCBB99`
- [CharacterSheetScreen.java:76](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L76) `0xFF887766`
- [CharacterSheetScreen.java:189](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L189) `0xFF1A1005`

**`client/gui/character/tab/AttributesTab.java`** — 15 literals

- [AttributesTab.java:29](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L29) `0xFFDDB97A`
- [AttributesTab.java:30](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L30) `0xFF1E1408`
- [AttributesTab.java:31](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L31) `0xFF3A2A14`
- [AttributesTab.java:32](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L32) `0xFF0A0500`
- [AttributesTab.java:33](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L33) `0xFF887766`
- [AttributesTab.java:34](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L34) `0xFFEEDDBB`
- [AttributesTab.java:35](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L35) `0xFF55FF55`
- [AttributesTab.java:36](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L36) `0xFFFF5555`
- [AttributesTab.java:37](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L37) `0xFFAA0000`
- [AttributesTab.java:38](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L38) `0xFFAAAAAA`
- [AttributesTab.java:39](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L39) `0xFFFFFFFF`
- [AttributesTab.java:44](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L44) `0xFF1A1005`
- [AttributesTab.java:45](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L45) `0xFF886622`
- [AttributesTab.java:155](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L155) `0xFF0D0905`
- [AttributesTab.java:160](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/AttributesTab.java#L160) `0xFF886622`

**`client/gui/character/tab/SkillsTab.java`** — 7 literals

- [SkillsTab.java:23](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L23) `0xFFDDB97A`
- [SkillsTab.java:24](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L24) `0xFF887766`
- [SkillsTab.java:25](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L25) `0xFFEEDDBB`
- [SkillsTab.java:26](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L26) `0xFF2A1E0F`
- [SkillsTab.java:27](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L27) `0xFF44321A`
- [SkillsTab.java:28](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L28) `0xFF160C00`
- [SkillsTab.java:29](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L29) `0xFFCCBB99`

**`client/gui/character/tab/SpellsTab.java`** — 4 literals

- [SpellsTab.java:24](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SpellsTab.java#L24) `0xFF1A1005`
- [SpellsTab.java:25](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SpellsTab.java#L25) `0xFF886622`
- [SpellsTab.java:26](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SpellsTab.java#L26) `0xFFDDB97A`
- [SpellsTab.java:27](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SpellsTab.java#L27) `0xFF776655`

**`client/gui/character/widget/HeritageBlockWidget.java`** — 3 literals

- [HeritageBlockWidget.java:16](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/HeritageBlockWidget.java#L16) `0xFFDDB97A`
- [HeritageBlockWidget.java:17](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/HeritageBlockWidget.java#L17) `0xFFEEDDBB`
- [HeritageBlockWidget.java:18](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/HeritageBlockWidget.java#L18) `0xFF776655`

**`client/gui/character/widget/PlayerModelViewport.java`** — 3 literals

- [PlayerModelViewport.java:21](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/PlayerModelViewport.java#L21) `0xFF0D0905`
- [PlayerModelViewport.java:22](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/PlayerModelViewport.java#L22) `0xFF3A2A14`
- [PlayerModelViewport.java:23](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/PlayerModelViewport.java#L23) `0xFF0A0603`

**`client/gui/character/widget/SkillTreeBarWidget.java`** — 5 literals

- [SkillTreeBarWidget.java:12](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java#L12) `0xFFCCBB99`
- [SkillTreeBarWidget.java:13](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java#L13) `0xFF1A1005`
- [SkillTreeBarWidget.java:14](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java#L14) `0xFF443322`
- [SkillTreeBarWidget.java:15](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java#L15) `0xFF887766`
- [SkillTreeBarWidget.java:55](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SkillTreeBarWidget.java#L55) `0xFF000000`

**`client/gui/character/widget/SpellCardWidget.java`** — 16 literals

- [SpellCardWidget.java:17](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L17) `0xFF1E1408`
- [SpellCardWidget.java:18](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L18) `0xFF3A2A14`
- [SpellCardWidget.java:19](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L19) `0xFF0A0500`
- [SpellCardWidget.java:20](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L20) `0xFFEEDDBB`
- [SpellCardWidget.java:21](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L21) `0xFF998877`
- [SpellCardWidget.java:24](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L24) `0xFF443322`
- [SpellCardWidget.java:25](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L25) `0xFF887766`
- [SpellCardWidget.java:26](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L26) `0xFFCC9933`
- [SpellCardWidget.java:27](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L27) `0xFFCC3322`
- [SpellCardWidget.java:93](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L93) `0xFFCC5544`
- [SpellCardWidget.java:94](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L94) `0xFF55AA55`
- [SpellCardWidget.java:95](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L95) `0xFF4488CC`
- [SpellCardWidget.java:96](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L96) `0xFF883388`
- [SpellCardWidget.java:102](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L102) `0xFF887766`
- [SpellCardWidget.java:103](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L103) `0xFFCC9933`
- [SpellCardWidget.java:104](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/SpellCardWidget.java#L104) `0xFFCC3322`

**`client/gui/character/widget/VitalsBarWidget.java`** — 5 literals

- [VitalsBarWidget.java:11](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java#L11) `0xFFCCBB99`
- [VitalsBarWidget.java:12](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java#L12) `0xFF1A1005`
- [VitalsBarWidget.java:13](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java#L13) `0xFFCC3333`
- [VitalsBarWidget.java:14](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java#L14) `0xFF4488CC`
- [VitalsBarWidget.java:15](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/widget/VitalsBarWidget.java#L15) `0xFF55AA33`

**`client/gui/config/ConfigWidgets.java`** — 9 literals

- [ConfigWidgets.java:28](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L28) `0xFFF5EDD6`
- [ConfigWidgets.java:29](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L29) `0xFF2A2318`
- [ConfigWidgets.java:30](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L30) `0xFF3B2A1A`
- [ConfigWidgets.java:31](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L31) `0xFFC8B89A`
- [ConfigWidgets.java:32](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L32) `0xFF8B0000`
- [ConfigWidgets.java:34](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L34) `0xFFEFE3C4`
- [ConfigWidgets.java:35](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L35) `0xFFE6D6AC`
- [ConfigWidgets.java:36](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L36) `0xFF3F6B3A`
- [ConfigWidgets.java:37](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/ConfigWidgets.java#L37) `0xFFEFFFE9`

**`client/gui/config/DarkArtsGateScreen.java`** — 8 literals

- [DarkArtsGateScreen.java:24](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L24) `0xFF1A0A0A`
- [DarkArtsGateScreen.java:25](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L25) `0xFF8B0000`
- [DarkArtsGateScreen.java:26](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L26) `0xAA000000`
- [DarkArtsGateScreen.java:27](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L27) `0xFFC8A0A0`
- [DarkArtsGateScreen.java:28](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L28) `0xFFFF5555`
- [DarkArtsGateScreen.java:112](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L112) `0x9E3779B9`
- [DarkArtsGateScreen.java:123](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L123) `0xCC8B0000`
- [DarkArtsGateScreen.java:123](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/DarkArtsGateScreen.java#L123) `0xCCB01010`

**`client/gui/config/WizardsConfigScreen.java`** — 3 literals

- [WizardsConfigScreen.java:271](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L271) `0xFFE0B0B0`
- [WizardsConfigScreen.java:280](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L280) `0x18FF8080`
- [WizardsConfigScreen.java:280](src/main/java/at/koopro/wizardsandbeasts/client/gui/config/WizardsConfigScreen.java#L280) `0x22FFFFFF`

**`client/gui/util/UiContrast.java`** — 9 literals

- [UiContrast.java:8](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L8) `0xFFE0F5`
- [UiContrast.java:9](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L9) `0x8B00FF`
- [UiContrast.java:9](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L9) `0x220042`
- [UiContrast.java:37](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L37) `0xFF000000`
- [UiContrast.java:48](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L48) `0x000000`
- [UiContrast.java:65](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L65) `0xFFFFFF`
- [UiContrast.java:68](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L68) `0xFF000000`
- [UiContrast.java:72](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L72) `0xFFFFFF`
- [UiContrast.java:100](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java#L100) `0xFF000000`

**`client/handbook/HandbookScreen.java`** — 8 literals

- [HandbookScreen.java:68](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L68) `0xFF9A8E74`
- [HandbookScreen.java:69](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L69) `0xFFCDBD97`
- [HandbookScreen.java:70](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L70) `0xFF9C8B66`
- [HandbookScreen.java:71](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L71) `0xFF2E1B34`
- [HandbookScreen.java:72](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L72) `0xFFB08E4A`
- [HandbookScreen.java:74](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L74) `0x33000000`
- [HandbookScreen.java:74](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L74) `0x00FFFFFF`
- [HandbookScreen.java:76](src/main/java/at/koopro/wizardsandbeasts/client/handbook/HandbookScreen.java#L76) `0xFFBCB096`

**`client/heritage/gui/HeritageCeremonyRenderer.java`** — 20 literals

- [HeritageCeremonyRenderer.java:53](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L53) `0xE6140C05`
- [HeritageCeremonyRenderer.java:54](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L54) `0x22FFC061`
- [HeritageCeremonyRenderer.java:66](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L66) `0xCC0A0603`
- [HeritageCeremonyRenderer.java:87](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L87) `0x00FFFFFF`
- [HeritageCeremonyRenderer.java:88](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L88) `0x00FFFFFF`
- [HeritageCeremonyRenderer.java:95](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L95) `0x99000000`
- [HeritageCeremonyRenderer.java:95](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L95) `0x00000000`
- [HeritageCeremonyRenderer.java:96](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L96) `0x00000000`
- [HeritageCeremonyRenderer.java:96](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L96) `0x99000000`
- [HeritageCeremonyRenderer.java:104](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L104) `0x66000000`
- [HeritageCeremonyRenderer.java:199](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L199) `0xCC7A1E1E`
- [HeritageCeremonyRenderer.java:200](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L200) `0xFFB85050`
- [HeritageCeremonyRenderer.java:201](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L201) `0xFFF2D7D7`
- [HeritageCeremonyRenderer.java:207](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L207) `0xCC4A3418`
- [HeritageCeremonyRenderer.java:229](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L229) `0xFF6E1212`
- [HeritageCeremonyRenderer.java:230](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L230) `0xFF8B1A1A`
- [HeritageCeremonyRenderer.java:231](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L231) `0xFF5A0E0E`
- [HeritageCeremonyRenderer.java:233](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L233) `0x804A0A0A`
- [HeritageCeremonyRenderer.java:234](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L234) `0x804A0A0A`
- [HeritageCeremonyRenderer.java:236](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageCeremonyRenderer.java#L236) `0x55FFC9A0`

**`client/heritage/gui/HeritageRailEntry.java`** — 15 literals

- [HeritageRailEntry.java:64](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L64) `0x55C69A44`
- [HeritageRailEntry.java:65](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L65) `0xFF000000`
- [HeritageRailEntry.java:67](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L67) `0x22FFE8C0`
- [HeritageRailEntry.java:74](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L74) `0xFF000000`
- [HeritageRailEntry.java:76](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L76) `0xFF000000`
- [HeritageRailEntry.java:80](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L80) `0xFF6B5A40`
- [HeritageRailEntry.java:80](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L80) `0xFF1B130A`
- [HeritageRailEntry.java:103](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L103) `0xFF4A3A22`
- [HeritageRailEntry.java:104](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L104) `0xFF6B5A40`
- [HeritageRailEntry.java:106](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L106) `0xFF4A3A22`
- [HeritageRailEntry.java:107](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L107) `0xFF4A3A22`
- [HeritageRailEntry.java:108](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L108) `0xFF4A3A22`
- [HeritageRailEntry.java:123](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L123) `0xFF000000`
- [HeritageRailEntry.java:123](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L123) `0xFF2A2318`
- [HeritageRailEntry.java:132](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageRailEntry.java#L132) `0xFF000000`

**`client/heritage/gui/HeritageSelectionScreen.java`** — 1 literals

- [HeritageSelectionScreen.java:294](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageSelectionScreen.java#L294) `0xFFF3E2C0`

**`client/heritage/gui/HeritageVariantChip.java`** — 8 literals

- [HeritageVariantChip.java:24](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L24) `0xE83A2B17`
- [HeritageVariantChip.java:25](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L25) `0xC8C6AB74`
- [HeritageVariantChip.java:58](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L58) `0xFF000000`
- [HeritageVariantChip.java:64](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L64) `0xFFE3C88B`
- [HeritageVariantChip.java:64](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L64) `0xFF3C2A14`
- [HeritageVariantChip.java:68](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L68) `0x220042`
- [HeritageVariantChip.java:68](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L68) `0x3F244D`
- [HeritageVariantChip.java:69](src/main/java/at/koopro/wizardsandbeasts/client/heritage/gui/HeritageVariantChip.java#L69) `0xFF2A2013`

**`client/map/MaraudersMapScreen.java`** — 12 literals

- [MaraudersMapScreen.java:22](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L22) `0xFF404040`
- [MaraudersMapScreen.java:23](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L23) `0xFF8B4513`
- [MaraudersMapScreen.java:24](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L24) `0xFFDAA520`
- [MaraudersMapScreen.java:25](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L25) `0xFFB22222`
- [MaraudersMapScreen.java:26](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L26) `0xFF228B22`
- [MaraudersMapScreen.java:27](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L27) `0xFF5C4033`
- [MaraudersMapScreen.java:108](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L108) `0xFF606060`
- [MaraudersMapScreen.java:164](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L164) `0x30000000`
- [MaraudersMapScreen.java:207](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L207) `0xCC2A1E14`
- [MaraudersMapScreen.java:208](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L208) `0xCC4A3520`
- [MaraudersMapScreen.java:209](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L209) `0xFFFFFFFF`
- [MaraudersMapScreen.java:210](src/main/java/at/koopro/wizardsandbeasts/client/map/MaraudersMapScreen.java#L210) `0xFFAAAAAA`

**`client/owl/screen/OWLExamScreen.java`** — 8 literals

- [OWLExamScreen.java:115](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L115) `0xEEF5E6C8`
- [OWLExamScreen.java:130](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L130) `0x5C3317`
- [OWLExamScreen.java:131](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L131) `0x5C3317`
- [OWLExamScreen.java:140](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L140) `0x3A2010`
- [OWLExamScreen.java:148](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L148) `0x5C3317`
- [OWLExamScreen.java:149](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L149) `0x5C3317`
- [OWLExamScreen.java:154](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L154) `0xCC8800`
- [OWLExamScreen.java:154](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLExamScreen.java#L154) `0x666666`

**`client/owl/screen/OWLResultsReadOnlyScreen.java`** — 6 literals

- [OWLResultsReadOnlyScreen.java:103](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L103) `0xEEF5E6C8`
- [OWLResultsReadOnlyScreen.java:105](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L105) `0x5C3317`
- [OWLResultsReadOnlyScreen.java:106](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L106) `0x5C3317`
- [OWLResultsReadOnlyScreen.java:111](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L111) `0xCC8800`
- [OWLResultsReadOnlyScreen.java:111](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L111) `0x666666`
- [OWLResultsReadOnlyScreen.java:124](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/OWLResultsReadOnlyScreen.java#L124) `0xCC8800`

**`client/owl/screen/ProfessionSelectionScreen.java`** — 5 literals

- [ProfessionSelectionScreen.java:182](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L182) `0xEEF5E6C8`
- [ProfessionSelectionScreen.java:184](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L184) `0x5C3317`
- [ProfessionSelectionScreen.java:185](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L185) `0x5C3317`
- [ProfessionSelectionScreen.java:193](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L193) `0xCC8800`
- [ProfessionSelectionScreen.java:193](src/main/java/at/koopro/wizardsandbeasts/client/owl/screen/ProfessionSelectionScreen.java#L193) `0x888888`

**`client/skill/gui/GoblinFormModel.java`** — 1 literals

- [GoblinFormModel.java:18](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/GoblinFormModel.java#L18) `0xFF559944`

**`client/skill/gui/SkillAccessDeniedScreen.java`** — 3 literals

- [SkillAccessDeniedScreen.java:29](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillAccessDeniedScreen.java#L29) `0xFFB02A2A`
- [SkillAccessDeniedScreen.java:30](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillAccessDeniedScreen.java#L30) `0xFF404040`
- [SkillAccessDeniedScreen.java:33](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillAccessDeniedScreen.java#L33) `0xFF606060`

**`client/skill/gui/SkillTreeChartTextures.java`** — 15 literals

- [SkillTreeChartTextures.java:45](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L45) `0xFFE8C76A`
- [SkillTreeChartTextures.java:47](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L47) `0xFF6E6156`
- [SkillTreeChartTextures.java:49](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L49) `0xFF272E44`
- [SkillTreeChartTextures.java:51](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L51) `0xF20A0E1A`
- [SkillTreeChartTextures.java:52](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L52) `0xFF8C93AA`
- [SkillTreeChartTextures.java:57](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L57) `0xFF8FD0A0`
- [SkillTreeChartTextures.java:58](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L58) `0xFF9FC4E8`
- [SkillTreeChartTextures.java:59](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L59) `0xFFF0E2B0`
- [SkillTreeChartTextures.java:60](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L60) `0xFFC96F7F`
- [SkillTreeChartTextures.java:61](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L61) `0xFF8FD8CF`
- [SkillTreeChartTextures.java:62](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L62) `0xFFD9B48F`
- [SkillTreeChartTextures.java:64](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L64) `0xFFC9C2A8`
- [SkillTreeChartTextures.java:65](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L65) `0xFFC0AED6`
- [SkillTreeChartTextures.java:69](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L69) `0xFFB8C0D8`
- [SkillTreeChartTextures.java:74](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeChartTextures.java#L74) `0xFFFFFF`

**`client/skill/gui/SkillTreeRenderHelper.java`** — 3 literals

- [SkillTreeRenderHelper.java:37](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java#L37) `0xFF6A5A90`
- [SkillTreeRenderHelper.java:37](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java#L37) `0xFF1A1626`
- [SkillTreeRenderHelper.java:94](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeRenderHelper.java#L94) `0xFFCED3E4`

**`client/skill/gui/SkillTreeScreen.java`** — 6 literals

- [SkillTreeScreen.java:44](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L44) `0x1E9FB8E8`
- [SkillTreeScreen.java:370](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L370) `0xFFB8C0D8`
- [SkillTreeScreen.java:403](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L403) `0xFFEFF2FF`
- [SkillTreeScreen.java:410](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L410) `0xFFFFF6DC`
- [SkillTreeScreen.java:413](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L413) `0xFFE8ECF8`
- [SkillTreeScreen.java:444](src/main/java/at/koopro/wizardsandbeasts/client/skill/gui/SkillTreeScreen.java#L444) `0xFF3A4258`

**`client/spell/gui/BeamDebugScreen.java`** — 1 literals

- [BeamDebugScreen.java:274](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamDebugScreen.java#L274) `0xFF000000`

**`client/spell/gui/BeamStyleScreen.java`** — 6 literals

- [BeamStyleScreen.java:75](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L75) `0xFFFFD700`
- [BeamStyleScreen.java:76](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L76) `0xFFAAAAAA`
- [BeamStyleScreen.java:77](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L77) `0xFF55FF55`
- [BeamStyleScreen.java:78](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L78) `0xFF555555`
- [BeamStyleScreen.java:79](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L79) `0xFF101014`
- [BeamStyleScreen.java:302](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/BeamStyleScreen.java#L302) `0xFF000000`

**`client/spell/gui/ImperioCommandScreen.java`** — 1 literals

- [ImperioCommandScreen.java:47](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/ImperioCommandScreen.java#L47) `0xFFFFFF`

**`client/spell/gui/SpellMenuRenderHelper.java`** — 4 literals

- [SpellMenuRenderHelper.java:30](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java#L30) `0xFF1E1A32`
- [SpellMenuRenderHelper.java:43](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java#L43) `0xFF6A5A90`
- [SpellMenuRenderHelper.java:43](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java#L43) `0xFF1A1626`
- [SpellMenuRenderHelper.java:83](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuRenderHelper.java#L83) `0x8B00FF`

**`client/spell/gui/SpellMenuScreen.java`** — 1 literals

- [SpellMenuScreen.java:255](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellMenuScreen.java#L255) `0xFFFFFFFF`

**`client/spell/gui/SpellTeacherScreen.java`** — 3 literals

- [SpellTeacherScreen.java:103](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java#L103) `0xFFFFD700`
- [SpellTeacherScreen.java:122](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java#L122) `0xFFCCCCCC`
- [SpellTeacherScreen.java:123](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/SpellTeacherScreen.java#L123) `0xFFAAAAAA`

**`client/trinket/gui/DiaryPossessionScreen.java`** — 3 literals

- [DiaryPossessionScreen.java:49](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryPossessionScreen.java#L49) `0x000000`
- [DiaryPossessionScreen.java:52](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryPossessionScreen.java#L52) `0xFFFFFF`
- [DiaryPossessionScreen.java:53](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryPossessionScreen.java#L53) `0xFFFFFF`

**`client/trinket/gui/DiaryWriteScreen.java`** — 4 literals

- [DiaryWriteScreen.java:106](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java#L106) `0xE6120D08`
- [DiaryWriteScreen.java:107](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java#L107) `0xFF4A3A22`
- [DiaryWriteScreen.java:108](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java#L108) `0xFFFFFF`
- [DiaryWriteScreen.java:117](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/DiaryWriteScreen.java#L117) `0xFFFFFF`

**`client/trinket/gui/MirrorCallScreen.java`** — 1 literals

- [MirrorCallScreen.java:64](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorCallScreen.java#L64) `0xFFFFFF`

**`client/trinket/gui/MirrorViewScreen.java`** — 4 literals

- [MirrorViewScreen.java:86](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java#L86) `0xFF1A1326`
- [MirrorViewScreen.java:87](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java#L87) `0xFF6B4FA0`
- [MirrorViewScreen.java:99](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java#L99) `0xFFFFFF`
- [MirrorViewScreen.java:100](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/MirrorViewScreen.java#L100) `0xFFFFFF`

**`client/trinket/gui/PensieveScreen.java`** — 9 literals

- [PensieveScreen.java:92](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L92) `0xE6100A1A`
- [PensieveScreen.java:93](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L93) `0xFF5B4B8A`
- [PensieveScreen.java:95](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L95) `0xFFFFFF`
- [PensieveScreen.java:99](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L99) `0xFFFFFF`
- [PensieveScreen.java:118](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L118) `0x66B388FF`
- [PensieveScreen.java:118](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L118) `0x33FFFFFF`
- [PensieveScreen.java:122](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L122) `0xFFFFFF`
- [PensieveScreen.java:124](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L124) `0xFFFFFF`
- [PensieveScreen.java:130](src/main/java/at/koopro/wizardsandbeasts/client/trinket/gui/PensieveScreen.java#L130) `0xFFFFFF`

**`client/trunk/gui/PocketConfiguratorScreen.java`** — 2 literals

- [PocketConfiguratorScreen.java:65](src/main/java/at/koopro/wizardsandbeasts/client/trunk/gui/PocketConfiguratorScreen.java#L65) `0xC0000000`
- [PocketConfiguratorScreen.java:65](src/main/java/at/koopro/wizardsandbeasts/client/trunk/gui/PocketConfiguratorScreen.java#L65) `0x00FFFFFF`

**`client/wand/gui/OllivanderTrialScreen.java`** — 1 literals

- [OllivanderTrialScreen.java:41](src/main/java/at/koopro/wizardsandbeasts/client/wand/gui/OllivanderTrialScreen.java#L41) `0xD0000000`
<!-- LITERAL-LIST-END -->

---

## 6. Existing constants — **the extension point already exists**

`at.koopro.wizardsandbeasts.client.gui` is already a shared GUI layer. Six pieces:

### 6.1 `WizardsPalette` — the palette token class

[WizardsPalette.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/WizardsPalette.java),
83 lines, 21 ARGB constants, `final`, private ctor. Its own javadoc says *"The one palette every
screen in this mod draws from"* and *"Import this rather than adding another literal."*

Groups already present, mapped against the brief's §2.1 required groups:

| Brief §2.1 requires | `WizardsPalette` has |
|---|---|
| parchment base | `PARCHMENT` `0xFFEFE7CF` |
| parchment shadow | `PARCHMENT_SHADE` `0xFFE6DCBE` |
| ink (primary text) | `PARCHMENT_INK` `0xFF3A2E24`, `TEXT` `0xFFF3E6D2` |
| ink muted | `TEXT_DIM` `0xFFC2A78F` |
| accent | `BRASS` `0xFFDBA86D`, `BRASS_HI` `0xFFF5E4B0` |
| accent muted | `LINE` `0xFFA4764A`, `EDGE_HI` `0xFFC08A5A` |
| error | ❌ **absent — deliberately.** Javadoc: *"Semantic colours (danger red, Floo green, Avada's green) are deliberately not here."* |
| disabled | `PIP_OFF` `0xFF5E3A2E` (indicator-scoped, not general) |

Plus surfaces (`INK`/`WELL`/`PLATE`/`PLATE_2`/`RAIL`/`SELECT`), `THUMB`, `PIP_ON`, and a
**second deliberate brand** — `MINISTRY` `0xFF3E1F47` / `MINISTRY_DARK` / `MINISTRY_LIGHT`,
sampled off `handbook/emblem.png`. The javadoc explicitly warns that a hue audit will read these
as drift and invite a "correction" — they are correct.

**Adopters (8 files):** `AbilityWheelScreen`, `ApparitionSelectorScreen`, `BestiaryScreen`,
`HandbookScreen`, `PocketConfiguratorScreen`, `OllivanderTrialScreen`, `WandmakersBenchScreen`.
That is **6 of 33 in-scope screens — 18 %.**

### 6.2 `McStylePanel` — the nine-slice helper already exists

[McStylePanel.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/McStylePanel.java), 86 lines.

```java
drawNineSlice(GuiGraphics g, Identifier tex, int x, int y, int w, int h, int ts, int b)
drawTiled     (…, int tile)
drawTexture   (…, int srcW, int srcH)
drawTexturedPanel(GuiGraphics g, int x, int y, int w, int h)   // vanilla demo_background
drawPanel     (…, int fillColor, int highlightTopLeft, int shadowBottomRight)
drawBorder    (…, int highlightTopLeft, int shadowBottomRight)
```

This is the brief's §2.2 deliverable, shipped. It takes the sprite as a parameter rather than
baking in a theme sprite — so there is **no inset variant** and **no themed default**; each caller
supplies its own sheet and its own `ts`/`b`. That is the one real gap against §2.2.

**Adopters: 21 files**, including 14 in-scope screens. Combined with §6.1, **19 of 33 in-scope
screens (58 %) already route through at least one shared helper.**

### 6.3 `tools/gui_chrome.py` — the Pillow generator already exists

[tools/gui_chrome.py](tools/gui_chrome.py). This is the brief's §2.3 deliverable, shipped. It
already:

- carries the same 12 palette constants as `WizardsPalette`, by name (`INK`, `WELL`, `PLATE`,
  `PLATE_2`, `RAIL`, `LINE`, `EDGE_HI`, `BRASS`, `BRASS_HI`, `THUMB`, `TEXT_DIM`, `SELECT`) —
  the two files are documented as having to agree;
- distinguishes supersampled assets (`SS = 4`, LANCZOS down) from 1:1 nine-slice assets, because
  a downscale smears the 3 px border `McStylePanel.drawNineSlice` cuts on;
- honours the repo's `Generator` PNG-marker contract via `artgen_common.marker()` /
  `is_regenerable()` — it regenerates only its own output and never touches hand art;
- supports `--force` and `--only name,name`.

Sibling generators in the same contract: `skill_chart_textures.py`, `bestiary_portraits.py`,
`bestiary_placeholders.py`.

### 6.4 `WizardsAndBeastsUiTokens` — the metrics class, and the problem

[WizardsAndBeastsUiTokens.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/WizardsAndBeastsUiTokens.java),
356 lines, 5 nested classes (`SpellMenu`, `BeamDebug`, `SpellDiamond`, `HeritageSelection`,
`SkillTree`), **99 ARGB literals**.

This is *per-screen* metrics + colour, not shared theme. Four separate colour vocabularies live
here, none of which reference `WizardsPalette`:

- `SpellMenu`: cold blue-violet — `PANEL_FILL 0xCC1A1A2E`, `PANEL_BORDER 0xFF444466`, `TITLE_COLOR 0xFFFFD700`
- `BeamDebug`: neutral grey — `COLOR_BG 0xC8101010`, `COLOR_LABEL 0xFFA0A0A0`
- `SpellDiamond`: purple-gold — `SLOT_BG 0x96130D1F`, `SLOT_ACTIVE 0xBDA86B1A`
- `HeritageSelection`: warm parchment — `COLOR_INNER_BG 0xE8D0BA8A`, `COLOR_TEXT 0xFF2A2013`
- `SkillTree`: warm leather **near-but-not-equal** to `WizardsPalette` — e.g. `BORDER_COLOR 0xFF6D5838` vs `WizardsPalette.LINE 0xFFA4764A`

`SpellMenu` and `SpellDiamond` are the two cold-scheme survivors the `WizardsPalette` javadoc was
written to eliminate. `HeritageSelection` also holds three **dead** tokens (`TYPE_ICON_X/Y/SIZE`,
lines 204-206) whose consumer no longer exists — see §2.2.

The brief's §2.1 "Metrics" group (padding, corner size, divider thickness, row height, scrollbar
width) has **no shared home**. It is spelled out five times, once per nested class, with different
names each time (`TOP_PADDING`/`OUTER_PAD`/`LIST_ROW_SPACING`/`LINE_H`/`ROW_HEIGHT`).

### 6.5 `client.gui.util` — three more shared helpers

- [GuiScaleHelper.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/GuiScaleHelper.java) — the canonical `Layout` (fit=downscale-only art, panel=up+down procedural). **22 adopters**, the single most-adopted GUI helper in the mod. Any theming work must go through it or it will break at 1×/3×.
- [GuiText.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/GuiText.java) — text drawing.
- [UiContrast.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/util/UiContrast.java) — 8 literals; contrast math.

### 6.6 `VanillaGuiTextures`

[VanillaGuiTextures.java](src/main/java/at/koopro/wizardsandbeasts/client/gui/VanillaGuiTextures.java)
— one constant, `minecraft:textures/gui/demo_background.png`, self-described as a placeholder
*"until mod-specific art exists"*. This is the seam where a themed default sprite belongs.

---

## 7. Module gating touchpoints

Only **three** screen-path `ModuleManager` consults exist. All read-only; none is entangled with
render ordering.

| Site | Check | Shape |
|---|---|---|
| [InventoryScreenInjector.java:38](src/main/java/at/koopro/wizardsandbeasts/client/event/InventoryScreenInjector.java#L38) | `!isEnabled(CHARACTER_SHEET)` → early return | Gates the **inventory button**, before `CharacterSheetScreen` is constructed. Outside any render method. |
| [CharacterSheetKeyHandler.java:14](src/main/java/at/koopro/wizardsandbeasts/client/spell/CharacterSheetKeyHandler.java#L14) | `!isEnabled(CHARACTER_SHEET)` → early return | Gates the `C` keybind. Outside render. |
| [CharacterSheetScreen.java:137](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/CharacterSheetScreen.java#L137) | `isPreview(CHARACTER_SHEET) && !previewLogged` | **Logging only**, one-shot. Does not gate rendering. |
| [SkillsTab.java:58](src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java#L58) | `!isEnabled(DARK_ARTS)` | Inside a character-sheet **tab**, decides whether a dark-arts row is drawn. This is the one gate on a render path. |

`SkillScreenRouter` gates `SkillTreeScreen` vs `SkillAccessDeniedScreen`, but does so on skill
state, not `ModuleManager`.

Non-screen client `ModuleManager` consults (HUD/render, **out of scope**): `NifflerPocketLayer`,
`BroomCameraHandler`, `BroomRiderPoseHandler`, `StatHudOverlay`, `PetrifyRenderHandler`,
`SpellDiamondOverlay`, `ClientModuleState`.

**Assessment against brief §5:** theming these screens does not need to touch any of the four
sites. `SkillsTab:58` is the only one worth flagging — it sits inside a render path, so a
migration of `SkillsTab` must preserve the check's position relative to the row draw, not merely
its presence.

---

## 8. Stop-and-report

Three of the five Phase 0 stop conditions fired.

### 8.1 §6 — shared GUI constants class exists **and is already partially themed** 🔴

This is the load-bearing finding. Three of the four Phase 1 deliverables already exist:

| Brief Phase 1 deliverable | Status |
|---|---|
| Theme token class | ✅ `WizardsPalette` — 21 tokens, 6/33 screens adopting |
| Nine-slice panel helper (panel + inset) | ⚠️ `McStylePanel.drawNineSlice` exists; **inset variant + themed default missing** |
| Pillow generator under `tools/` | ✅ `tools/gui_chrome.py` — with the marker contract and the SS/1:1 split already solved |
| Sprite sheets under `textures/gui/theme/` | ❌ sheets exist but live per-feature, not under `theme/` |

Building §2.1–§2.3 as briefed would create a **second** palette next to `WizardsPalette` and a
**second** slicer next to `McStylePanel` — precisely the drift `WizardsPalette`'s javadoc was
written to stop. The brief's own §6 names the existing class as *"the extension point — do not
create a second."*

**The work shape is therefore not "build a theme layer" but "finish rolling out the one that
exists"**, which changes Phase 1 from ~4 new artifacts to ~2 small extensions:

1. add `WizardsPalette.ERROR` + a general `DISABLED` (the two §2.1 groups genuinely absent, noting
   the javadoc's deliberate exclusion of semantic colour — this needs Christian's call, see §8.4);
2. add a `Metrics` group (padding / corner / divider / row height / scrollbar width) — genuinely
   absent, currently spelled five ways in `WizardsAndBeastsUiTokens`;
3. add `McStylePanel.drawThemedPanel` / `drawThemedInset` that bake in a `theme/` sprite, so
   callers stop supplying their own sheet;
4. extend `tools/gui_chrome.py` with a `theme/` target rather than writing a new script.

Then Phase 2 proceeds unchanged.

### 8.2 §"screen inventory count differs" — work shape changed 🟡

The brief is written as if the screen set were small and uniform. It is **34 screens (33 in
scope)**, in 14 packages, with 5 distinct render idioms and 4 mutually incompatible colour
vocabularies in `WizardsAndBeastsUiTokens` alone. At one commit per screen (brief §2.4) this is a
33-commit Phase 2, plus 33 × 2 screenshots × 4 GUI scales × 2 aspect ratios of §7 in-game
verification.

Two sub-populations arguably do not want the parchment/ink theme at all:

- **Debug screens** (`BeamDebugScreen`, `BeamStyleScreen`, `MorphDebugScreen`) — deliberately
  neutral grey so they read as tooling, not as game UI.
- **The Ministry brand** (`HandbookScreen`) — purple-on-parchment by design, per §6.1.

### 8.3 §"wandmaker screen appears" — confirmed excluded ✅

`WandmakersBenchScreen` is present (screen #34), is owned by
`AGENT_PROMPT_wandmaker_table_repair.md`, and is excluded from every count and every table in this
document. Continuing with it excluded, as the brief directs.

Note for the other agent's benefit: it is already one of the two best-themed screens in the mod
(6 `WizardsPalette` reads, 1 `McStylePanel` call).

### 8.4 Decisions needed before Phase 1

1. **Extend `WizardsPalette` or create a new class?** Brief §6 says extend. Recommend extending.
2. **`error` / `disabled` tokens.** The brief's §2.1 requires them; `WizardsPalette`'s javadoc
   deliberately excludes semantic colour. These are in direct conflict. Recommend adding a
   `DISABLED` surface token (it is theme, not semantics) and **not** adding `ERROR` (it is
   semantics), then logging the deviation.
3. **Debug + Ministry screens: theme or exempt?** Recommend exempting all four and logging them
   in `MIGRATION_DELTAS.md` as intentional.
4. **33 screens at one commit each** — confirm that volume is wanted, or agree a batching rule
   (e.g. one commit per package).
5. **Screen titles.** 17 of 33 screens use `Component.literal` with hardcoded English
   (⚠ in §1). Out of scope for this pass — logged for the punchlist, not fixed.

### 8.5 Conditions that did **not** fire ✅

- No screen unreachable or observed throwing (§1).
- No screen's rendering entangled with server-side state — all 5 menu-backed screens read their
  menu through the standard `AbstractContainerScreen` contract; no theming change reaches a
  menu or a packet (§7).

---

## 9. Punchlist candidates found in passing

Logged here, **not fixed**, per brief §4. To be folded into `AUDIT_PUNCHLIST.md` if Phase 1 proceeds.

| Severity | Finding |
|---|---|
| POLISH | 10 orphaned `type_icons/*.png` (§2.2) + 3 dead `HeritageSelection.TYPE_ICON_*` tokens |
| POLISH | `textures/gui/bestiary/entries/` does not exist — every bestiary entry renders the placeholder (§2.4) |
| POLISH | 17 screens ship hardcoded English titles via `Component.literal` (§1) |
| NICE-TO-HAVE | `VanillaGuiTextures` still points at vanilla `demo_background.png` "until mod-specific art exists" (§6.6) |
| NICE-TO-HAVE | `WizardsAndBeastsUiTokens` mixes metrics and colour across 5 unrelated screens in one 356-line file (§6.4) |

---

## 10. Pre/post-flight

No files were created or modified by Phase 0 other than this document. `git status --porcelain`
is unchanged from the pre-flight snapshot apart from the addition of untracked `GUI_AUDIT.md`.
The unrelated house-banner working tree was not touched, staged, or committed.
