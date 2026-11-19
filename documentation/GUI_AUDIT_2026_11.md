# GUI audit — 2026-11-18 (UI Renaissance P1)

Baseline: dev `c1949f6c`, hidden real client, `WB_UI_CAPTURE=ui_p1`, 78 screens × GUI 2/3/4 at 1920×1080
(234 shots, no `NoSuchFileException`). Contact sheets: `C:/tmp/wb-ui-run/sheets/base_s3_*.png`.
Earlier audits: `GUI_AUDIT_2026_10.md` (41 screens, 10-15; overhaul 10-21).

Material stays **parchment** (owner: "No navy ui", 2026-11-15). Artefact screens keep their object's look.
Spell HUD diamond and spell wheel are frozen.

## Kit: interaction states (code)

| State | Before | Gap |
|---|---|---|
| Hover | `ControlState.HOVER` face swapped instantly | No transition; hover and keyboard focus look identical |
| Focus | `isHovered() \|\| isFocused()` → same face | Tab focus invisible once the pointer is on another control |
| Pressed | none | No press feedback beyond the click sound |
| Disabled | `_off` face + `PAGE_INK_3` label | OK |
| Selected chip | SELECT face swapped instantly | OK, no transition |
| Confirm | `ConfirmDialog` (kit) in admin + profession only | Skill web start-over used vanilla grey `ConfirmScreen`; skill unlearn is right-click with no confirm (price is on the card — kept) |
| Loading | `Ui.loadingState` exists | **Used by no screen.** Attributes page drew nothing for the stats before the first sync; travel/heritage tabs have their own loading strings |
| Error | `Ui.errorState` exists | Used by no screen |
| Empty | `Ui.emptyState` | 5 screens; bestiary shows rows of `???` instead |
| Motion | none in the kit | — |

## Screens (visual, GUI 3 @ 1080p)

| Screen | Finding | Phase |
|---|---|---|
| Character sheet / Attributes | Five stats in one brass tone: glyphs, names and meters identical in colour; only the word tells them apart. Stats section fills the page; the Attributes block starts at the fold | P3 |
| Character sheet (all pages) | Section headers (rubric) are the same size as body text; long pages (Education, Travel) read as one wall | P5 |
| Spell menu | Learnable list is mostly "Soon" chips; categories readable; detail pane fine | P5 |
| Skill web | Start-over confirm left the chart for a grey stone screen | P2 |
| Config board | Five small cards in a wide empty field; icons 8 px | P5 |
| Bestiary | Empty journal = column of `???` rows with dotted bars; no empty state | P5 |
| Vocation selection | Grey-blue panel with full-width stacked buttons; reads like a different mod beside the parchment sheet | P5 |
| Heritage gate / confirm | OK (10-21 layout) | — |
| Profession / OWL / results / sitting | OK; buttons dense but legible | P5 polish |
| Wand bench / refine / restyle | OK (new 11-17 restyle with live wand) | — |
| Bag, pouch, mirror, Pensieve, map, Gringotts, licence | Artefact identity, keep | — |
| Ability wheel, spell wheel, HUD | Frozen / out of scope | — |

## Missing screens (owner picked all four, P6)

Quest journal · wardrobe · companions · brewing log.

## Capture harness defects found

1. The O.W.L. sitting HUD ("O.W.L. sitting · 8:31 · papers 2/9") stays in every shot after `owl_sitting`:
   the step opens a sitting on the server and nothing closes it.
2. `admin_control_center` shot shows the previous screen: the admin screen opens asynchronously after a
   server round trip, past the settle time.
3. 88 steps planned, 78 written: ten steps produced no file (to trace before P7).

## What was built (2026-11-18/19, branch feat/ui-renaissance)

### Kit additions (one system, used by every new screen)

| Piece | Where | Rule |
|---|---|---|
| `UiMotion` | `client/gui/ui` | The only motion source: wall-clock ease, 110 ms hover, 140 ms open, 90 ms press. Never moves text being read. |
| Eased faces, focus ring, press nudge | `ThemedButton`, `ThemedChip`, `McStylePanel.drawThemedButtonEased`, `Ui.focusRing` | Hover fades the existing hover face in; keyboard focus without the pointer = 1 px gilt ring. No new art. |
| `ConfirmDialog` arrival | `ConfirmDialog` | Scrim fades, card rises 6 px. Destructive = DANGER tone, click only. |
| `StatStyle` | `client/gui/character` | One ink per stat (copper, verdigris, indigo, tarnished gold, plum), all ≥5:1 on PAGE. Used by the sheet, the stats overlay, the glyphs. |
| `StatMeasureChart` | `client/gui/character` | "The Measure": the five stats as an inked pentagon (owner's pick over cards). |
| `FamilyIcons` + `tools/ui_icons.py` | `client/gui/ui`, `textures/gui/icons/` | 16 px engraved one-ink glyphs in the stat-glyph style: spell categories, 8 heritages, 9 equipment slots, 4 rarities, progression. No medallions. |
| `BookChrome` | `client/gui/ui` | An open book: cover nine-slice, two parchment pages, gutter shadow, ribbon. Field journal (leather) and brewing log (green cloth). |
| `Ui.rubric` | `Ui` | Page heading + short ink underline; the sheet pages' bare red headings now use it. |
| `Ui.emptyState` | `Ui` | Hints longer than four lines end in an ellipsis instead of being cut silently. |
| `Ui.entityPreview` | `Ui` | Fits a creature by hitbox width and height (small creatures were drawn as a head only). |

### Screens

| Screen | Key | Material | Data / actions |
|---|---|---|---|
| Attributes page | sheet | parchment, The Measure | read-only |
| Stats overlay | (existing toggle) | parchment slip, stat inks | read-only |
| Wardrobe | Curios' key/button (Shift = Curios' own), "Open wardrobe" unbound | walnut armoire, gilt mirror | `WardrobeMenu` over Curios' API; `WardrobeRenderPayloads` for the eye |
| Field journal | J | brown leather book | read-only (path, career) |
| Companions board | U | cork in walnut | existing `CompanionActionC2SPayload` |
| Brewing log | O | green cloth book | read-only (`KnownRecipesView`, brew defs) |
| Skill web start-over | — | kit dialog over the chart | existing respec payload |

Left alone on purpose: spell HUD and wheel (frozen), the bestiary's `???` rows (the discovery mechanic), the vocation
screen's blue vellum (the skill web's own material), artefact screens (their objects' looks).

### Validation

- Unit: `StatStyleTest` (ink contrast, distinct inks, easing, fade, Measure geometry) + the GUI layout tests: 53 green.
- GameTests: `WardrobeTests` 6/6 (slot admission, shift-click to worn only, look grants no armour, binding curse,
  creative lifts curse, eye flips only its own flag).
- Real clients on a hidden desktop: every new screen at GUI 3/4 on 1080p and GUI 3 on 1280×720.
- Two clients on a dedicated server (`client/debug/WardrobeMpProbe`): the wearer shift-clicked a hat in through the
  wardrobe and clicked the head eye; the watcher's copy of the wearer went empty → `wizard_hat render=true` (t=20 s)
  → `render=false` (t=24 s).
- Pre-existing, not from this work: `equipment_wardrobe_recipes_and_accessory_slots` expects 51 wardrobe recipes, the
  game has 141 (also red on dev).
