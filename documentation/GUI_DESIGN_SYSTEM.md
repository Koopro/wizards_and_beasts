# GUI_DESIGN_SYSTEM.md

The sizes every screen measures with. Colour is **not** here — that is `WizardsPalette`, which is
already solved and deliberately carries two brands plus a set of excluded semantic colours.

## Why

Measured across all 34 client screens before this landed:

| Symptom | Measured |
|---|---|
| Panel sizes | **25 distinct** — 14 widths, 19 heights |
| Off-grid paddings | **9 of 19** — 3, 6, 7, 9, 10, 13, 19, 22, 26 |
| Scaling approaches | **4** — 5 `fit`, 11 `panel`, 5 raw, **13 none** |
| `McStylePanel` adoption | **11 of 34**; 23 hand-draw with `g.fill()` — 17 of 34 after the screen-redo pass |
| `font.lineHeight` readers | **6 of 34**; 2 screens declare a line-height constant |
| `UiTokens` readers | **7 of 34**; ~283 constants, 27 screens read zero |

`WizardsAndBeastsUiTokens` is misleading rather than merely unused: `SpellDiamond`'s block has no
reader, and `HeritageSelection` declares 48 layout constants of which **zero** are read — including
a `PANEL_WIDTH/HEIGHT = 392×308` the screen contradicts with its own `400×232`. Only its 11 colours
are consumed. It is catalogued, not deleted; `BeamDebug` (78 reads) and `SpellMenu` (85) are live.

## Spacing — 4pt scale

`WizardsMetrics.SPACE_*`

| Token | px | Use |
|---|---:|---|
| `XS` | 2 | bar to its label, pip to pip |
| `S` | 4 | inside a component |
| `M` | 8 | **default** — between components, and clear of a panel frame |
| `L` | 12 | section to section |
| `XL` | 16 | major blocks in a column |
| `XXL` | 24 | column gutters |
| `XXXL` | 32 | rare inside 320px |

`M` replaces the old `FRAME_PAD = 7`. That 7 came from `gui_chrome.dossier_backdrop` painting its
frame out to 6px; 8 still clears it.

## Panel sizes — 4, from 25

| Token | px | Use |
|---|---|---|
| `PANEL_MODAL` | 256 × 144 | one message, confirm/deny |
| `PANEL_STANDARD` | 320 × 240 | **default** — vanilla's own proportion |
| `PANEL_WIDE` | 400 × 256 | two-column, canvas |
| `PANEL_CONTAINER_W` | 176 | vanilla slot grid — **excluded** |

Container screens are out of scope on purpose: their slot offsets are vanilla's, so an invented
frame misaligns every slot. Same reasoning that makes `gui_chrome.restyle` recolour vanilla art in
place rather than redraw it.

## Type

| Token | px | Use |
|---|---:|---|
| `LINE_TIGHT` | 10 | dense lists, stat rows |
| `LINE_BODY` | 12 | **default** |
| `LINE_SECTION` | 16 | heading to content |
| `LINE_TITLE` | 20 | title bars |

Anchored on Minecraft's 9px line height plus leading — not the 11/11 (Gringotts), 12 (MorphDebug) or
12/12/14 (Ollivander) steps currently in use. A screen picks one and stays on it.

## Components

`McStylePanel`, drawing the `gui/theme/` sprites that shipped with zero consumers — which is
precisely why 23 screens hand-rolled their own bevel.

| Call | Sprite | Stretch |
|---|---|---|
| `drawThemedPanel` | `panel.png` 32×32 | nine-slice on 8 |
| `drawThemedInset` | `panel_inset.png` | nine-slice on 8, **bevel inverted** |
| `drawDivider` | `divider.png` 32×8 | x only — exact |
| `drawScrollbar` | `scrollbar_{track,thumb}.png` 8×32 | y only — exact |
| `drawRow` | — | selection tint, alpha composed separately |

The inset inverts its bevel rather than just darkening. Light comes from the top-left throughout;
swap them and an inset reads as a second panel stacked on the first.

## Scaling — one mode

`GuiScaleHelper.Layout.panel` for procedural screens, `Layout.fit` for fixed art. No new code —
both already exist. The 5 raw `computeScale` callers and the 13 unscaled screens adopt `Layout`.

## Materials — `WizardsPalette.GuiSkin`

`tools/gui_chrome.py` generates eleven sprites per skin into `gui/sprites/<skin>/`. It has always
claimed `WizardsPalette.GuiSkin` mirrors its `SKINS` table; that class did not exist until the
screen-redo pass, so Java knew only a folder name and the two screens that had adopted a skin each
hand-copied its colours (`SkillTreeChartTextures.CHART_INK` is `star_chart`'s ink, retyped).

| Skin | Material | Screen |
|---|---|---|
| `star_chart` | night void, indigo, silver leaf | skill web, vocation select |
| `marauders_map` | pocket-worn parchment and brass | Marauder's Map |
| `workbench` | worn wood, shellac, brass calipers | Ollivander's trial, wandmaker's bench |
| `goblin_ledger` | oxblood leather, ruled paper, gold | Gringotts |
| `pensieve` | dark wet stone, silver memory light | Pensieve |
| `hearth` | soot stone, warm soot, Floo green | Floo network |
| `ministry` | pale violet memo, emblem purple | — |
| `field_notebook` | kraft paper, canvas board, pencil | Bestiary (Field Journal) |

**A screen that takes a skin must take that skin's `ink()` with it.** Four materials are light and
every colour above the skin table was picked against the dark leather HUD:

| On `workbench` `#D9C49A` | | On `goblin_ledger` `#E6DFC9` | |
|---|---:|---|---:|
| `TEXT` | 1.39 | Galleon gold `#D4AF37` | 1.58 |
| `TEXT_DIM` | 1.34 | Sickle silver `#C0C0C0` | 1.37 |
| `BRASS_HI` | 1.35 | `#CCCCCC` labels | 1.21 |
| *its own `ink`* | *9.44* | *its own `ink`* | *13.59* |

For a colour that carries meaning and so cannot be replaced — a coin, a spell family, a good/bad
cast contribution — use `UiContrast.readableOn(fg, skin.base())`, which keeps the hue and moves only
the luminance. `GuiSkinTest` pins ink-on-base at AA and that every material has a legible edge.

Note the edge invariant is a disjunction, not `accent` alone: sampling the generated `divider.png`,
a light skin's rule is carried by its dark seat row at 9.4–11.9 : 1 while the accent row beside it
sits at 1.6–2.3. The accent is the highlight *on* the edge. `star_chart` is the inverse case.

## Scaling — the ladder rule

Skinned screens pre-multiply every coordinate through `layout.s()` at layout time and push no pose,
so mouse coordinates need no unmapping. **Do not mix scaled and unscaled offsets in one column.**
Gringotts' first cut kept a fixed 32px header above scaled rows; at `Layout.panel`'s 0.72 floor that
put its buttons at y 170 and its totals at y 148. A ladder whose every rung scales cannot overflow
the panel it fits at 1.0 — and it is worth checking arithmetically across 0.72…1.35 rather than by
eye at 1.0.

Two corollaries:

- A nine-sliced control needs **≥18px** (`2 * 8 + 2`). Do not reach for `Math.max(18, s(h))`: a
  floored button is taller than the rung that reserved space for it. Pick a design height whose
  smallest scaled value already clears 18 — 26 works, since `26 × 0.72 = 19`.
- `drawDivider` is an **8px sprite, not a 1px rule**. Advance by `DIVIDER_H`.

## Lists

`client/gui/widget/ScrollList` — row-index scrolling plus the scrollbar that goes with it. Extracted
from the Bestiary, which was the copy that was already right. Before it there were four hand-rolled
lists behind three different scrollbars, and two of the four (the Marauder's Map waypoints, the
Pensieve) drew no bar at all while scrolling perfectly.

## Mockups

`python tools/gui_mockups.py` → `docs/gui_concepts/`.

Honest by construction, not by intention:

- palette values are **parsed out of `WizardsPalette.java`**, metrics out of `WizardsMetrics.java`;
  a missing constant is a hard failure, not a stale colour
- the **real theme sprites** are nine-sliced by a port of `McStylePanel.drawNineSlice`
- text is a measured block at Minecraft's real 6px advance — it claims "this much space is taken"
  rather than pretending to be a font render

A mockup that flatters the design is worse than none.

## Visual language (2026-10-07)

The rules every screen follows so the mod reads as one game. Components that implement them live in
`client/gui/ui/` (`UiSurface`, `UiText`, `Ui`, `UiTooltips`, `UiIcons`, `ConfirmDialog`) and
`client/gui/widget/`; how to use them is `GUI_COMPONENTS.md`.

### Surfaces

| Surface | Field | Ink / label / caption | Heading | Shadow |
|---|---|---|---|---|
| `PAGE` | parchment `#E4D2A6` | `#2E1F16` / `#5C4330` / `#8A6E52` | rubric `#8E2320` | no |
| `NIGHT` | soot-violet `#1C1722` @ 94% | `#F3E6D2` / `#C2A78F` / `#9A8878` | gilt `#EBC874` | yes |
| `of(GuiSkin)` | the skin's paper | the skin's ink; label and meaning colours lifted to AA on its paper | skin ink | no |

`UiSurfaceTest` pins every surface: ink, label and heading at 4.5 : 1, caption / good / bad / warn /
disabled at 3 : 1, shadow exactly on the dark surfaces. A screen has one body surface; NIGHT cards may
float over it. Each screen keeps its own identity through its material, its art and its layout — the
skill web stays a star chart, the bestiary a field notebook, the wand trial a workbench — while the type,
spacing, controls, states and tooltips are shared.

### Typography hierarchy

One font at one size, so hierarchy is colour, position and space. Five roles (`UiText.Role`):
**Title** (ink, step 20, one per screen, followed by the divider) · **Heading** (heading colour, step
16) · **Body** (ink, 12) · **Label** (ink2, 12) · **Caption** (ink3, 10). No text is scaled below 1.0
on a player screen; what does not fit is ellipsised and the full text goes in a tooltip.
Sentence case for headings and buttons; no all-caps except where a material is lettered (the Ministry
certificate heading).

### Spacing

The 4-pt scale above. In practice: **8** from a panel's frame and between components, **12** between
sections, **4** inside a component (a chip's text from its edge), **2** between a bar and its label.
Rows step by the type role's step; a list row is `ROW_H` 16.

### Corners and borders

- Paper panels carry their deckle and gilt corner studs in the sprite; nothing draws a second frame
  inside them.
- Procedural pieces (bars, chips, night cards, callouts) are square with the **corner texel cut** —
  at GUI scale that is the same soft corner the sprites have. Nothing is rounded more than one texel.
- Every piece has a **1px edge**: the surface's edge colour on paper, gilt at night (lit top-left,
  shaded bottom-right). Insets invert the bevel. Light always comes from the top-left.

### Icons

Native size only: 16px in rows and sockets, 32px in large previews. Never 1.5× or fractional scales
(pixel art shimmers). An icon named by data that has no texture draws a gilt monogram (`UiIcons`),
never the missing-texture checkerboard. A spell family, an ability kind or a coin keeps its hue; the
frame around it carries the state.

### Interaction states

| State | Buttons / tabs | Rows | Radial sockets | Text |
|---|---|---|---|---|
| Normal | atlas row 0 | — | night card | ink / label |
| Hover / focus | atlas row 1 | `hoverWash` | lift 2px toward the hub + wash | ink |
| Active / selected | `SELECT` tone | `selectWash` + gilt edge | gilt-light outline | heading or ink |
| Disabled | atlas row 2 (`_off`) | ink3 text | — | `disabled`, tooltip says why |
| Confirm / danger | `CONFIRM` / `DANGER` tone | — | — | — |

### Tooltip rules

Plain tooltips on any mod screen use the night card (`UiTooltipStyleHook`; sprites
`gui/sprites/tooltip/night_*`, written by `gui_parchment.py`). Name first in gilt, then what it is,
then numbers (good green / bad red), then why it is unavailable, lore last in grey italic. Wrap at
180px. Item tooltips keep the item's own card.

### Player-facing text

No raw ids, enum constants, translation keys, tick counts or unrounded floats; no English in code;
no new `§` codes in lang strings (colour is the component's job). The 2026-10-07 audit removed the
ones it found (`GUI_AUDIT_2026_10.md`).

## Status

2026-10-15: finishing pass over all 41 screens and the HUD — spell wheel, Apparition, Imperio, trinkets, config and
Obscurial HUD brought onto the kit and lang; orphaned Bestiary art and dead tokens removed. Details and what is
still open: `GUI_AUDIT_2026_10.md`, "Finishing pass".

2026-10-07: the visual language above is implemented and applied to the eleven audited screens; the
fixes were checked in a running client at GUI scale 3 and 4 (`client/debug/UiScreenCapture`,
`WB_UI_CAPTURE`). Before/after and the open items are in `GUI_AUDIT_2026_10.md`.

Earlier: `WizardsMetrics`, the five themed components, the mockup tool, before/after for the character
sheet and the bestiary.
