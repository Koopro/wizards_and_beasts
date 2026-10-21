# Character Sheet — the progression hub

The sheet answers one question: **who is my wizard, and what are they becoming?** It is the one player progression
screen. New progression systems get a page or a card here, never a menu of their own.

Code: `client/gui/character/`. Opened by the `C` keybind, the inventory tab button, `/wandb character` and
`OpenCharacterSheetPayload`. `CharacterSheetScreen.onTab(SheetTab)` opens it on a given page.

## Layout (408 × 234 design space, `Layout.fit`)

| Column | Width | Holds |
|---|---|---|
| Figure | 96 | 3D viewport, health, XP (things about the body you watch change) |
| Rail | 92 | one 18px entry per page: 16px icon (`SheetIcons`), full name, PREVIEW dot, hover = page description |
| Page | 184 | the open page, full column height, scrolls |

The rail replaced the old identity column + two tab rows on 2026-10-15. Nine tabs in two rows clipped every label
to two letters; ten rail entries fit with their names. `↑`/`↓` step through the pages.

## Pages (`SheetTab`, rail order)

| Page | Class | Reads (all server-synced or server-built) |
|---|---|---|
| Identity | `IdentityTab` | heritage data, Ministry record, vault, the cards' sources below, effects, `HeritageProfile` |
| Attributes | `AttributesTab` | `ClientStatsState` (stats + `StatReadout` effect lines), attribute instances |
| Magic | `SpellsTab` | `ClientSpellDataState` via `SheetCaches.spellbook()`; `SpellPowerTooltip` |
| Wand | `WandTab` | the wand stack's components + wood/core registries (`WandEligibility`, `WandCastLines`, `WandAllegianceService`) |
| Skills | `SkillsTab` | `ClientSkillDataState`, `SheetCaches.build()` |
| Creatures | `BeastsTab` | `ClientCompanionState`, `ClientBestiaryCache` |
| Education | `EducationTab` | `ClientEducationState` (server `EducationView`) |
| Profession | `CareerTab` | `ClientCareerState` (server `CareerView`) |
| Reputation | `RecordTab` + `PlacesSection` | `ClientStandingState`, `ClientMinistryRecordState`, `ClientPlaceStandingState`, `ClientCareerState` |
| Travel | `TravelTab` | `ClientTravelProfileState` |

Lang keys keep their original ids (`tab.spells` reads "Magic", `tab.record` "Reputation", `tab.beasts`
"Creatures", `tab.career` "Profession", `tab.education` "Education"), so packs that renamed them keep working.

### Identity

Who (heritage block: heritage, lineage, condition, patronus, animagus, traits), Ministry band, vault; then
**"What you are becoming"** — one `ProgressCard` per road: Build, Magic, Wand, Profession, Education, Reputation,
Creatures. A card = icon, title, headline value, one status line, a bar only where there is a real road with a
length (build nodes, mastery, wand bond with the next state marked, rank XP, coursework toward O, best companion
bond). Clicking opens the page; hovering lists the detail and the next step. A system that is off says "Switched off
on this server"; a view not yet answered says "Asking the server…" — no placeholder numbers. Then active effects and
the heritage profile (the old Heritage tab, refreshed every ~2 s while shown).

Opening the Identity page asks once for every server-built view the cards summarise (career, education,
companions, place standings, heritage profile). Each request is throttled server-side.

### Places (Reputation)

Location standing (Gringotts, Ollivanders, apothecary, Knockturn …) used to reach the client only while a board was
open at the host. `PlaceStandingsC2SPayload` (empty, owner-only, 10-tick throttle) →
`LocationProgression.standings(player)` → `PlaceStandingsS2CPayload(List<PlaceStanding>)` (≤ 64, bounded decode).
Places never dealt with are listed dimmed with how to begin.

## Performance rules

- Derivations keyed on the client state's change counter: `ClientSpellDataState.version()`,
  `ClientSkillDataState.version()` (added 2026-10-15) → `SheetCaches`. The Spells tab sorted and resolved every
  spell, and the Skills tab rebuilt `BuildProfile`, every frame before.
- Identity cards rebuild every 10 frames; Wand page re-derives on a component change or every 20 frames.
- Hover cards are cached per hovered thing (`AttributesTab` stat cards, Magic spell cards).

## Building a page

Extend `ScrollingTab` (scroll, scissor, scrollbar, visible-rect hover, click targets, tooltip hand-off) and lay out
one column with the shared kit (`Ui.sectionHeader`, `Ui.statRow`, `Ui.bar`, `Ui.chip`, `UiText`) on
`UiSurface.PAGE`. Colours drawn as glyphs go through `UiContrast`. No new design tokens. See
`documentation/GUI_COMPONENTS.md`.

## Verify

`WB_UI_CAPTURE=<dir> WB_UI_CAPTURE_WORLD=<save copy> WB_UI_CAPTURE_ONLY=sheet_identity,sheet_wand,… ./gradlew runClient`
shoots each page (`sheet_<page>`) at GUI 3 and 4.
