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
| `McStylePanel` adoption | **11 of 34**; 23 hand-draw with `g.fill()` |
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

## Mockups

`python tools/gui_mockups.py` → `docs/gui_concepts/`.

Honest by construction, not by intention:

- palette values are **parsed out of `WizardsPalette.java`**, metrics out of `WizardsMetrics.java`;
  a missing constant is a hard failure, not a stale colour
- the **real theme sprites** are nine-sliced by a port of `McStylePanel.drawNineSlice`
- text is a measured block at Minecraft's real 6px advance — it claims "this much space is taken"
  rather than pretending to be a font render

A mockup that flatters the design is worse than none.

## Status

Shipped: `WizardsMetrics`, the five themed components, the mockup tool, before/after for the
character sheet and the bestiary.

Not yet applied to any screen — the concepts are proposals. **Nothing here has been seen in a
running client.**
