# GUI audit — 2026-10-07

Eleven player-facing UIs audited in a running client at 1920×1080, GUI scale 3 and 4 (4 is what
vanilla's "Auto" picks at 1080p, so the tightest a player normally sees), against
`GUI_DESIGN_SYSTEM.md`. Shots: `run/screenshots/ui_before`, `ui_after`, `ui_after2`
(harness: `client/debug/UiScreenCapture`, `WB_UI_CAPTURE=<dir>`).

`GUI_AUDIT.md` is the Phase-0 inventory (2026-08) and is kept as history; this document is the current
state. How to build a screen is `GUI_COMPONENTS.md`.

## Shared fixes (every screen)

| Problem | Fix |
|---|---|
| No shared components above panels/buttons: every screen hand-drew its stat rows, bars, tags, empty states and tooltips | `client/gui/ui/`: `UiSurface`, `UiText`, `Ui` (title, section header, stat row, bar, labelled bar, XP bar, chip, callout, empty/loading/error state, tab, preview card, entity preview, night card, scrim), `UiTooltips`, `UiIcons`, `ConfirmDialog`; widgets `ThemedToggle`, `ThemedCheckbox`, `ThemedDropdown` |
| Colours chosen per screen; light ink on light paper recurred | components take a surface, not colours; `UiSurfaceTest` holds every surface's inks to AA — and caught the field notebook's label ink at 4.1 : 1 on the first run (now lifted) |
| `GuiText.drawFitted` shrank glyphs to 65% to fit | now ellipsises (17 call sites). Text is never scaled on player screens |
| Tooltips were vanilla's purple-black card on parchment screens | `UiTooltipStyleHook`: every non-item tooltip on a mod screen uses the `night` style (`gui/sprites/tooltip/night_*`, from `gui_parchment.py`) |
| A data-named icon with no texture drew the magenta checkerboard | `UiIcons.icon16` draws a gilt monogram instead |
| `ThemedButton` icon-only buttons offset their icon by the label gap | gap only applies when there is a label |

## Screens

Legend: ✅ fixed and seen in the client · ➖ checked, no change needed · ⏳ open

### Character Sheet — `client/gui/character`
Identity: parchment dossier, three columns. Keeps it.
- ✅ "Heritage" printed twice (a block heading over a row with the same label).
- ✅ Identity labels were English typed into the code — now `character_sheet.identity.*` keys.
- ✅ "Special characteristics" ran into the next column — fitted with an ellipsis.
- ✅ Animagus row printed the id of the current form ("Human default") — now the Animagus animal, or "—".
- ✅ Patronus is named by its entity name when the game has one.
- ✅ Tabs were equal-width; "Attributes" was squeezed against its frame — tabs are sized to their labels (hit-testing uses the same edges).
- ⏳ Attributes tab: a stat's derived line ("Spell Damage") sits directly above the next stat's name ("Precision") with no gap, so pairs read wrong. Needs a group gap in `AttributesTab`.
- ⏳ Narrow wax scrollbar (4px) differs from the 8px themed one; consistent with the spell menu, so documented as the compact variant rather than changed.

### Skill Web — `client/skill/gui`
Identity: star chart (`STAR_CHART` skin). Keeps it.
- ✅ Zoom/recenter controls sat inside the chart, covering stars — moved to the header row.
- ✅ At GUI scale 4 the panel lays out at ~0.75 and those controls shrank to 13px around a 12px icon (pre-existing) — floored at icon + 4.
- ➖ Footer, vocation button, node tooltips (now on the night card via the hook).
- ⏳ The constellation name at the chart's top edge ("Monoceros") is clipped by the well when the region sits at the top.

### Spell Menu — `client/spell/gui`
Identity: parchment index + rune-socket plate. Keeps it.
- ✅ Long spell names printed through the "Coming soon" tag ("Alarte Ascen" + "Coming soon") — names fitted to what the status leaves; the tag is a short chip ("Soon").
- ✅ Category headings were enum constants ("COMBAT", "DARK ARTS") — translated names with a hairline.
- ✅ The top socket's label sat on the screen title; socket names were cut without an ellipsis ("Aguame") — plate moved down, top/bottom labels get the plate's width ("Avada Kedavra", "Expelliarmus"), side labels ellipsise, full name and category in a socket tooltip.
- ✅ The detail card was an empty box until a spell was picked — empty state "No spell chosen" with what it will show.

### Ability Wheel — `client/ability/wheel`
Identity: radial wheel over the world. Now on the night surface.
- ✅ **Every ability icon was the missing-texture checkerboard** (all 15 definitions name `textures/ability/placeholder.png`, which does not exist) — gilt monogram sockets via `UiIcons`.
- ✅ A flat grey square behind the wheel — night-card sockets around a night-card hub; hover lifts the socket; selected gets a gilt-light ring; toggled keeps its green ring; quick-slot badges sit on a well.
- ✅ The hub shows the hovered ability's name and description (not just the name); the prompt wraps instead of being cut.
- ⏳ Real ability icons (art): 15 abilities still draw monograms. Data-driven, so painting `textures/ability/<id>.png` and pointing the JSON at it is all that is needed.

### Bestiary — `client/bestiary/gui`
Identity: field notebook (`FIELD_NOTEBOOK` skin), handwritten journal pages. Keeps it.
- ✅ Front page was a column of sentences ("Encountered: 0 / Observed: 0 / …") — section header, tier stat rows, a "Pages begun n / 107" bar, studies completed, the how-to as a caption.
- ➖ Index list, pips, search field, entry pages.
- ⏳ Search field has no placeholder text.

### O.W.L. — `client/owl/screen`
Identity: exam parchment. Keeps it.
- ✅ Results: one sentence per subject ran off the sheet ("Defence Against the Dark Arts — E (Exceeds Expectations)") — a table: subject, grade word, grade letter on a chip (green pass / red fail / muted not examined).
- ✅ Results: "Done" hung 14px off the sheet — both buttons inside, on one row.
- ✅ Profession: "Choose" buttons covered the wage ("1G 2…"), scroll arrows sat on a button — two-line rows, Choose in its own column, "Not qualified" chip for the rest, wheel scrolling with the themed scrollbar.
- ✅ Profession: confirmation was vanilla's grey `ConfirmScreen` — `ConfirmDialog` on the same parchment.
- ➖ Exam intro.

### Ministry — `client/ministry/gui`
Identity: Ministry memo (`MINISTRY_MEMO` skin) with a turning seal. Keeps it.
- ✅ Blank licence: the message was centred across the card and ran through the seal — an empty state beside the seal, with what fills a licence.

### Brewing — no screen (in-world cauldron + action bar + JEI)
- ✅ The start message printed raw ticks ("Started brewing: X (600t)") — "The brew begins: X. Ready in about 30 seconds."
- ⏳ 25 cauldron lang strings carry inline `§` colour codes; the rule now says colour belongs to the code. Converting them means moving each colour to its call site.
- ➖ JEI category.

### Wandmaking — `client/wand/gui/WandmakersBenchScreen` (container)
Identity: workbench skin on vanilla slot layout (container art stays vanilla-positioned by design).
- ➖ Flexibility cycler, slots, hint line.
- ⏳ The workshop-tier bar under the hotbar has no label (its tooltip explains it). Its track is painted into the container art (`gui_chrome.py`), so a caption needs an art change.

### Ollivander — `client/wand/gui/OllivanderTrialScreen`
Identity: workbench skin, trial cards. Keeps it.
- ➖ Cards, resonance bars, "Take this wand". Tooltips now on the night card via the hook.

### Admin UI — `client/admin`
Identity: Ministry purple frame around parchment content, its own widget set (`AdminTheme`, `Admin*`).
- ➖ Already derives every colour from `WizardsPalette` (`AdminTheme`); its dark frame is the same family as `UiSurface.NIGHT`, its paper is `PAGE`. Kept as its own set: it is an operator tool with denser controls (26px rows, inline value editors) and changing it would touch 40+ panels for no player benefit.
- Shared now: tooltips on the night card (hook), `ConfirmDialog` / `ThemedToggle` / `ThemedCheckbox` follow the same state model as `AdminConfirmDialog` / `AdminToggle` / `AdminCheckbox`.

## Developer tools (vanilla controls allowed)
`BeamDebugScreen`, `BeamStyleScreen`, `MorphDebugScreen`, `WandLookPreviewScreen` — vanilla buttons and
English literals are acceptable on tools no player reaches; listed so a future audit does not flag them.

## Open items, by size

1. Ability icon art (15 icons) — art only.
2. `AttributesTab` group spacing.
3. Bestiary search placeholder; skill-web constellation label clipping.
4. Bench tier-bar caption (art change in `gui_chrome.py`).
5. Cauldron lang `§` codes → colour at call sites.


## Finishing pass — 2026-10-15

The first pass reached eleven screens. This one measured all 41 `Screen` classes plus the HUD overlays
(per file: English literals, hex colours, `fill` calls, kit and `GuiScaleHelper` use, vanilla vs themed
buttons, tooltips), checked every `translatable("…")` key the client names against the merged `en_us`,
scanned `textures/gui/**` for files nothing loads, and listed unread constants in the shared GUI classes.

### Fixed

| Screen / area | Was | Now |
|---|---|---|
| Spell wheel | Flat translucent square, flat slots, armed ring a stray `0xFFFFD24A`, ring up to 132px from centre whatever the screen (clipped at GUI 3 on 768p), hub text unbounded over the sockets | Night sockets with hover lift + wash (the ability wheel's language), armed = `GILT_LIGHT` outline, hub card sized to the ring's inside with ellipsised name, radius fitted by `SpellWheelModel.radius` (unit-tested at GUI 1–4, 1080p/768p/720p/480p/ultrawide) |
| Apparition selector | Flat fills, no title rule, no scroll — 16 places × 22px = 352px against 270px at GUI 4 on 1080p, so the last rows were off-screen; mouse only; "m" hardcoded | Night panel, `Ui.title`, `ScrollList` with a night scrollbar, ↑/↓/W/S + Enter/Space, hover only takes the selection when the mouse moves, truncated names get a tooltip, blocked rows in the surface's bad ink, distance via lang |
| Imperio picker | Vanilla buttons labelled with enum constants (`FOLLOW_CASTER`), no panel | Parchment modal, `ThemedButton`s, command names and what each does (tooltip) from lang |
| Diary (write, possession), Two-way mirror (call, view) | English in code; `§4§o` / `§8` / `§5` codes | Lang keys; colour and italics from styles |
| Config board, chapter page, Blood Quill gate | Every string English in code; chapter page did not scroll (Heritage: 20+ rows × 26px, rows after the 12th unreachable at GUI 3 on 1080p); no tooltips although all 91 config values carry a comment; cards had no keyboard focus state; four cards overflowed a 320px-wide screen | Lang keys; NeoForge `wizards_and_beasts.configuration.<key>[.tooltip]` convention with humanised key / spec comment fallback; scrolling page (whole rows, wheel, parchment scrollbar, clipped not hidden so Tab reaches every setting and the page follows focus); tooltips on control and label; focus = hover on cards and the « Return link; cards wrap to the width |
| Skill web node card | `"Level: "`, `"Cost: … SP"`, `"No description."` in code | `screen.wizards_and_beasts.skill_tree.card.*` |
| Obscurial HUD | Every label English in code, tiers drawn as model strings | `hud.wizards_and_beasts.obscurial.*` through `ObscurialHudTheme.text/tier`; quickbar fill is the theme's `PANEL_BG_SOFT` |
| `ScrollList` | Night lists had no scrollbar | `renderScrollbar(g, UiSurface)` — skin, parchment, or a night well with a gilt thumb |
| Capture harness | 1920×1080 at GUI 3/4 only | `WB_UI_CAPTURE_SCALES`, `WB_UI_CAPTURE_WINDOW`; steps for every screen above |

### Tokens and assets

- Removed: `Ui.HEALTH_RED`, `Ui.MAGIC_BLUE` (no readers; `GUI_COMPONENTS.md` updated),
  `WizardsAndBeastsUiTokens.SkillTree.CONTROL_MARGIN` (the controls left the chart on 2026-10-07).
- Kept on purpose: `WizardsPalette.PLATE_2/EDGE_HI/THUMB` (parsed by `gui_mockups.py`),
  `WizardsMetrics.SPACE_XXXL/PANEL_CONTAINER_W` (documented scale entries).
- Deleted art: `gui/bestiary/{screen,left_panel,right_panel,row,header,entry_placeholder}.png` and their
  `gui_chrome.py` entries plus its now-unused `sheet`/`well` helpers. The field-notebook skin replaced them;
  nothing loaded them (AUDIT_PUNCHLIST "seven orphaned Bestiary chrome PNGs" — `scroll_track/thumb` were
  already gone). Only generated art was removed. `art_sync_check --only gui_chrome,gui_parchment,config_textures`: in sync.
- Not orphans although no literal path names them: `sprites/<skin>/button_{hover,off}` (built from
  `ControlState`), `sprites/tooltip/night_*` (vanilla appends `_background`/`_frame`), `skill_tree/chart/region_*`
  and `star_*` (built from tree ids and sizes), the creative-tab backgrounds (`backgroundTexture`).

### Checked, no change

Lang: every literal key the client names resolves (the misses the scan lists are prefixes completed at runtime,
or vanilla's `gui.cancel`/`gui.done`). Remaining hex literals are semantic (coins, blood splatter, mirror glass,
wand-bench tier ramp, toggled/pinned ability rings) or alpha-composed from palette tokens. Gringotts, Floo
network/registration, Marauder's Map, Pensieve, Handbook, Heritage and Vocation selection, Location, Pocket
configurator, Hermione's bag, Niffler pouch and the skill-access notice already use the kit and a scaler (or
clamp to the screen) and draw no English from code. Developer tools (`BeamDebug`, `BeamStyle`, `MorphDebug`,
`WandLookPreview`, the admin `EntityViewer`) keep their own look and English, as before.

### Scaling

Validated arithmetically (the spell-wheel radius in `SpellWheelModelTest` across GUI 1–4 on 1920×1080,
1366×768, 1280×720, 854×480, 2560×1080) and by construction for the list screens (rows = what fits).
**Not yet seen in a running client** — this pass ended before the capture run. To shoot it:

```
WB_UI_CAPTURE=gui_finish WB_UI_CAPTURE_WORLD=wb_gui_finish_capture WB_UI_CAPTURE_SCALES=1,2,3,4 \
WB_UI_CAPTURE_ONLY=spell_wheel,apparition,imperio,mirror_call,mirror_view,diary_write,diary_possession,config,config_heritage,config_gate \
./gradlew runClient
```

then again with `WB_UI_CAPTURE_WINDOW=2560x1080` and `=1280x720`. `run/saves/wb_gui_finish_capture` is a
fresh copy of `wb_admin_capture_visual`.

### Still open

1. In-client screenshots of this pass (above).
2. From 2026-10-07: ability icon art, `AttributesTab` group spacing, bestiary search placeholder, constellation
   label clip, bench tier caption, cauldron `§` codes.
3. Config value names and descriptions are the humanised key and the TOML comment until
   `wizards_and_beasts.configuration.*` keys are written (the screen picks them up with no code change).
4. The Obscurial HUD badges are placed at fixed offsets measured on the English words.
