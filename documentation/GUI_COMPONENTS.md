# GUI components — usage guide

How to build a screen in Wizards & Beasts so it looks like the rest of the game. Read this before
writing a `Screen`. The rules behind it (sizes, type, colour) are in `GUI_DESIGN_SYSTEM.md`; the
screen-by-screen state of the mod is in `GUI_AUDIT_2026_10.md`.

**The one rule:** never type a colour or a pixel offset into a screen. Pick a *surface*, draw with
the components below, and step down a column with the `y` each call returns.

---

## 1. Pick a surface

Every component takes a `UiSurface` (`client/gui/ui/UiSurface`). The surface decides the material and
every ink on it, including whether text gets a shadow.

| Surface | Looks like | Use it for |
|---|---|---|
| `UiSurface.PAGE` | the parchment sheet (`gui/theme/`) | any player screen without its own material |
| `UiSurface.NIGHT` | soot-violet card, gilt edge, cream ink | things floating over the world: radial wheels, tooltip cards, dropdown lists, HUD-adjacent overlays |
| `UiSurface.of(GuiSkin.X)` | one of the eight materials (star chart, workbench, ledger, field notebook, ministry memo, Marauder's map, Pensieve, hearth) | a screen with a strong identity of its own |

A screen picks **one** surface for its body. A NIGHT card may float over a PAGE screen (a tooltip, a
dropdown, a confirmation scrim); two paper materials side by side may not.

```java
private static final UiSurface S = UiSurface.of(GuiSkin.WORKBENCH);
```

## 2. Lay out a column

Every draw call returns the `y` below what it drew. A column is a chain:

```java
int y = Ui.title(g, font, S, title, x, top, w, playerName);      // title + divider
y = Ui.sectionHeader(g, font, S, x, y, w, heading, caption);      // heading + hairline
y = Ui.statRow(g, font, S, x, y, w, label, value);                // label ......... value
y = Ui.statRow(g, font, S, x, y, w, label, value, S.good());      // value in a meaning colour
y = Ui.labelledBar(g, font, S, x, y, w, label, valueText, frac, S.good());
y += WizardsMetrics.SPACE_L;                                       // gap between sections
y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, hint, x, y, w, 3);
```

Gaps come from `WizardsMetrics.SPACE_*` (2, 4, **8**, 12, 16, 24). Never a literal.

## 3. Component catalogue

### Containers

| Need | Call |
|---|---|
| Raised panel | `Ui.panel(g, S, x, y, w, h)` |
| Recessed well (list, viewport, track) | `Ui.inset(g, S, x, y, w, h)` |
| Dark magical card | `Ui.nightCard(g, x, y, w, h)` (same as `Ui.panel` with `NIGHT`) |
| Section break | `Ui.divider(g, S, x, y, w)` — 8px sprite on paper, advance by its return |
| Row separator | `Ui.hairline(g, S, x, y, w)` |
| Modal dim | `Ui.scrim(g, screenW, screenH)` |

Panel sizes: `PANEL_MODAL` 256×144, `PANEL_STANDARD` 320×240, `PANEL_WIDE` 400×256. Pick the smallest
that fits; scale it with `GuiScaleHelper.Layout.panel(...)`.

### Text

`UiText` — five roles, one font, **never scaled**:

| Role | Colour | Step | For |
|---|---|---|---|
| `TITLE` | ink | 20 | one per screen |
| `HEADING` | heading (rubric on paper, gilt at night) | 16 | a section |
| `BODY` | ink | 12 | values, prose, list entries |
| `LABEL` | ink2 | 12 | the name half of name/value |
| `CAPTION` | ink3 | 10 | hints, placeholders, footnotes |

- `UiText.drawFit(...)` — shortens with "…" to a width. Returns whether it cut; if it did, show the full
  text with `UiTooltips.ifTruncated(...)`.
- `UiText.drawRight(...)`, `drawCentered(...)`, `drawWrapped(..., maxLines)`.
- `GuiText.drawFitted*` now also ellipsises (it used to shrink glyphs to 65%, unreadable at GUI scale 4).

### Values and progress

| Need | Call |
|---|---|
| Name/value row | `Ui.statRow(...)` — label ellipsised so it never meets the value |
| Progress bar | `Ui.bar(g, S, x, y, w, h, fraction, fill, marker)` — `marker < 0` for none |
| Bar with label/value above | `Ui.labelledBar(...)` |
| Experience | `Ui.xpBar(...)` (vanilla XP green) |
| Status tag ("Coming soon", a grade letter, "Not qualified") | `Ui.chip(g, font, S, x, y, text, Ui.Tone.X)` → returns width |

Fill colours with meaning: `Ui.XP_GREEN` for experience; otherwise a surface ink (`S.good()`, `S.warn()`, `S.accent()`). A feature colour that means something (a spell family, a coin) goes through `UiContrast.readableOn`.

`Ui.Tone`: `NEUTRAL` (ink2), `GOOD`, `BAD`, `WARN`, `ACCENT`, `MUTED` (disabled / not applicable).

### Messages and states

| State | Call | Write it as |
|---|---|---|
| Nothing yet | `Ui.emptyState(g, font, S, rect, title, hint)` | title says what is missing, hint says how to fill it ("Meet a creature to begin its page") — never just "Empty" |
| Waiting on the server | `Ui.loadingState(g, font, S, rect, text)` | "Asking the Ministry" + animated dots |
| Failed | `Ui.errorState(...)` | what failed, then what to try |
| Warning / error / info / success inline | `Ui.callout(g, font, S, x, y, w, Ui.Callout.WARNING, text)` → returns height | one sentence |

### Tabs

```java
for (int i = 0; i < tabs.length; i++) {
    Ui.Rect r = Ui.tabRect(x, y, w, tabs.length, i);           // or size tabs to their labels
    Ui.tab(g, font, S, r, label(i), i == active, r.contains(mouseX, mouseY), enabled(i));
}
```

Active = `SELECT` tone; hit-test with the same `Rect` you drew (compute geometry once). Tab height
`Ui.TAB_H` = 18 (clears the nine-slice floor at every layout scale). If labels differ a lot in length,
size tabs to their labels and share the spare width (the character sheet does this).

### Previews

| Need | Call |
|---|---|
| Picture / item / model in a well with a caption | `Ui.Rect inner = Ui.previewCard(g, font, S, rect, caption)` then draw into `inner` |
| A living entity that follows the mouse | `Ui.entityPreview(g, font, S, rect, entity, caption, mouseX, mouseY)` |
| An icon named by data | `UiIcons.icon16(g, font, texture, name, x, y)` — draws a gilt monogram if the texture is missing, never the magenta checkerboard |

### Controls (widgets — `client/gui/widget`)

| Control | Class | When |
|---|---|---|
| Button | `ThemedButton` (+ `.tone(ButtonTone.X)`, `ThemedButton.skinned(...)` for a material) | any action |
| On/off that applies at once | `ThemedToggle` | "Show hints" |
| Tick in a form applied later | `ThemedCheckbox` | "Include spells" before Export |
| 2–5 options, value always visible | `CyclerWidget` (◀ value ▶) | flexibility, heritage |
| 6+ options | `ThemedDropdown` — register it before widgets its list may cover | woods, cores |
| Number in a range | `ThemedSlider` | volumes, tuning |
| Text entry | `ThemedTextField` | search, names |
| Long list | `ScrollList` | anything that scrolls |
| Yes/no before something irreversible | `ConfirmDialog` (owned by the screen) | see below |

Button tones (`McStylePanel.ButtonTone`): `NEUTRAL` default · `CONFIRM` commits something the player
cannot take back · `DANGER` destroys or refuses · `SELECT` the current choice in a group · `ACCENT` a live
but non-committing action (reroll, reset).

**Vanilla `Button`, `Checkbox`, `EditBox`, `ConfirmScreen` are not used on player screens.** They are
fine on developer tools (`BeamDebugScreen`, `MorphDebugScreen`, `WandLookPreviewScreen`).

### Confirmation

```java
private final ConfirmDialog confirm = new ConfirmDialog();

confirm.open(title, body, Component.translatable("…forget"), Component.translatable("gui.cancel"),
        McStylePanel.ButtonTone.DANGER, () -> send(...));

// render(): after everything else
confirm.render(g, font, width, height, mouseX, mouseY);
// mouseClicked / keyPressed: first
if (confirm.mouseClicked(event.x(), event.y())) return true;
if (confirm.keyPressed(event.key())) return true;
```

The confirm label is the verb ("Choose", "Forget spell"), never "Yes". `DANGER` is never confirmed by
Enter. Escape always cancels.

### Tooltips

- Every plain tooltip on a mod screen is drawn on the night card automatically
  (`UiTooltipStyleHook`). Item tooltips keep the item's own card.
- Draw one yourself with `UiTooltips.show(g, font, title, bodyLines, mouseX, mouseY)`.
- Order: **name** (gilt, first) → what it is → numbers (green good / red bad) → why it is disabled
  (`UiTooltips.disabledReason`) → lore last (`UiTooltips.lore`). Lines wrap at 180px.
- Tooltips appear on hover over the thing itself. No "?" icons.

### Toasts

`WizardsToasts` (`client/gui/toast`) for things that happened while the player was doing something
else (a spell learned, a page written). Short title, one line of body, drawn on the paper slip. Do not
send chat for these, and do not toast what the screen the player is looking at already shows.

## 4. States every control has

| State | How it shows |
|---|---|
| Normal | its face |
| Hover / focus | the lighter row of the controls atlas; list rows get `S.hoverWash()`; a radial socket lifts 2px |
| Active / selected | `SELECT` tone for buttons and tabs; `S.selectWash()` plus a gilt edge for rows |
| Disabled | the `_off` row of the atlas and `S.disabled()` text — and a tooltip that says **why** |
| Pressed | vanilla click sound; no extra art |

Never show disabled by hiding: a greyed "Choose" says "you can't yet", a missing one says nothing.

## 5. Things players must never see

- Raw ids (`wizards_and_beasts:human_default`), enum constants (`DARK_ARTS`), translation keys.
- Raw engine units: ticks (`600t`), unrounded floats (`0.43999997`), internal scores.
- English typed into code — every string goes through `Component.translatable`.
- `§` colour codes in new lang strings: colour belongs to the component drawing the text.
- Glyphs scaled below 1.0 to make text fit.
- The magenta checkerboard (use `UiIcons`).

## 6. Before you open a PR

1. `./gradlew test --tests "*UiSurfaceTest" --tests "*GuiSkinTest"` — contrast of every surface.
2. Photograph the screen at GUI scale 3 and 4 (1080p "Auto" is 4):
   `WB_UI_CAPTURE=ui_check WB_UI_CAPTURE_WORLD=<save copy> ./gradlew runClient` (add your screen to
   `client/debug/UiScreenCapture.plan()`). Look for text meeting text, text leaving its panel, and
   controls sitting on content.
3. Check the empty state: open the screen on a fresh character.
