# Visuals administration and the beam editor

Phase 7 of the Control Center, built on `ADMIN_FRAMEWORK.md` and following the patterns of `ADMIN_WANDS_BROOMS.md`.
It adds the **Visuals** section with seven tabs:

- **Beams** — beam browser, beam editor, live preview, presets.
- **Particles**, **Screen** — each player's own visual preferences (read-only here).
- **Impacts** — every beam's impact intensity side by side.
- **HUD** — the spell HUD toggle and a HUD preview (groundwork for elixir / potion timers).
- **Entities** — the Phase 4 entity viewer, reached from Visuals.
- **Debug** — this client's beam budget, the beam debug editor, a renderer readout.

There is still exactly one beam renderer (`client.beam`). Nothing here draws beams any other way; previews feed the
production renderer a render state.

---

## 1. What already existed

| Piece | Where | Notes |
|---|---|---|
| Beam renderer | `client.beam`: `BeamEntity` (client-only, one per caster), `BeamEntityRenderer`, `Laser` / `Lightning`, `BeamGeometry`, `BeamRenderTypes` | whole-pixel boxes, ≤2 glow shells, sparks, additive or alpha blend |
| Per-spell look | `BeamAppearance` (hard-coded table) | crucio bolt, avada 3px jet, aguamenti laser, leviosa draws nothing |
| Beam channel | `WandBeamChannelLogic` → `BeamChannelS2CPayload` (who channels what, re-announced on an interval) | the client re-traces origin/target every frame |
| Debug editor | `BeamStyleEditor` + `BeamStyleScreen` (`/wandb debug beam edit`) | a global override of every beam on one client |
| Quality preset | `/wandb debug beam preset low|medium|high` → `BeamPresetS2CPayload` | **dead end**: fed `client.wand.BeamSettings`, which the live renderer never reads |
| Legacy cluster | `client.wand.BeamStyle/BeamStyles/BeamSettings`, `BeamDebugScreen` | only the legacy debug screen reads it; `BeamDebugScreen` is opened by nothing |
| Impacts | `SpellImpactBurstS2CPayload` (family, colour, count, spread) → tinted burst + camera kick | carries no spell id |
| HUD | `SpellDiamondOverlay` (+ its `renderPreview`) | no potion/elixir timer HUD exists |
| Entity viewer | `EntityViewerScreen` (Phase 4) | model, variant, clip, speed, zoom, hitbox; light was fixed fullbright |

## 2. Visual configuration is not gameplay configuration

`visual.beam` is a new common package that holds only what a beam **looks** like:

- `BeamVisual` — the supported properties (§3), nothing else.
- `BeamVisualProperty` — id, kind, bounds and the one text parser/formatter. It refuses bad text instead of clamping it.
- `BeamVisualDefaults` — the authored look of each beam spell, moved from `BeamAppearance` with the same numbers
  (`BeamAppearanceDefaultsTest` pins them against the old table, glow curve included).
- `BeamVisuals` — effective look = default + overrides, property by property; an unparsable override is skipped.

No gameplay rule reads any of it. Reach stays the spell's range × wand range; damage, cooldown and timing are the
spell's. `admin_visual_gameplay_unchanged` changes every beam property and checks crucio's damage, cooldown and range.
The one server-read value, `impact_intensity`, scales the **presentation** of an impact burst (particle count and the
camera kick riding on it), never whether or how the beam acts.

## 3. What is editable

Settings `wizards_and_beasts:beam/<spell>/<property>` (section Visuals, capability `admin.visual`), for the four beam
spells (`crucio`, `avada_kedavra`, `aguamenti`, `wingardium_leviosa`):

| Property | Type | Renderer |
|---|---|---|
| `enabled` | bool | beam drawn at all (Leviosa ships off) |
| `shape` | LASER / LIGHTNING | `Laser` / `Lightning` |
| `core_color`, `glow_color` | `#RRGGBB` | rod colour; shells + sparks |
| `core_width`, `core_height` | int 1–12 px | whole pixels, as `BeamGeometry` rounds |
| `core_brightness`, `glow_brightness` | 0–1 | opacities (brightness under additive blend) |
| `glow_shells` | int 0–2 | `BeamGeometry` draws at most two |
| `spark_density` | 0–1 | share of spark candidates lit (was the constant 96/256 = 0.375) |
| `spin` | −20–20 °/tick | rod rotation |
| `additive` | bool | additive vs alpha pipeline |
| `segments`, `jitter`, `crackle_ticks` | 1–32, 0–24 px, 1–40 | lightning only |
| `fade_in_ticks`, `fade_out_ticks` | 0–40 | alpha only; a fading beam's reach is frozen, nothing acts |
| `impact_intensity` | 0–2 | server-side scale of the beam's impact bursts |

**Deliberately not offered** (the renderer has no such thing): length (gameplay), trail, trail length, taper (the
style guide keeps beams on whole-pixel boxes), a beam sound (beam casts play none), impact effect type (the burst's
family comes from the spell). Each would need renderer work first; a property is added only together with its reader.

Renderer changes to support the list, all inside the existing renderer: `BeamStyle.sparkDensity` (+ compat
constructor), `BeamStyle.withOpacityScale`, `BeamEntity` fade windows / frozen stop reach / live-look supplier /
preview TTL, `BeamChannelClient.restyleAll`.

## 4. Defaults, presets, reset

- **Default** = `BeamVisualDefaults`, in code. A setting's default is the authored value; writing it removes the
  override. It cannot be overwritten or deleted by anyone, from the panel or a command
  (`admin_visual_beam_look_syncs_and_resets` proves the default is recovered exactly).
- **Presets** (`BeamPreset`) are templates:
  - built-in: one per beam spell (`builtin:<spell>`), computed, read-only;
  - custom: stored in world data (`BeamVisualData`), max 64, names `[A-Za-z0-9 _'-]{1,32}`, ids never contain `:`.
  - Operations (`BeamVisualService`): create (save as new), save (overwrite a custom preset), duplicate (any, built-ins
    included), rename (id kept), delete. Built-ins answer `BUILTIN` to save / rename / delete.
  - **Load** runs on the client: it copies a preset's values into the spell's rows as drafts. Apply then sends them as
    ordinary setting changes — validated, recorded in history, undoable, broadcast. Deleting a preset never changes a
    spell set from it.
- **Reset**: the row's ↺, "Reset to default" (drafts every row to its default), or "Reset section" on the Impacts tab.
- The browser labels each spell `Default`, `Custom`, or the preset its look matches exactly.

## 5. Server / client classification

| Value | Scope | Travels | Why |
|---|---|---|---|
| `beam/<spell>/*` look | server | `BeamVisualSyncS2CPayload` (overrides only) on join, on `/reload`, after each change | one look per spell for every player |
| `beam/<spell>/impact_intensity` | server | not sent (read where bursts are sent) | the server sends the bursts |
| beam presets | server, admins only | `AdminVisualPayloads.ListReply` on request / after an op | only admins use them |
| beam quality LOW/MEDIUM/HIGH | this client | nothing (the debug command relays it once) | GPU budget |
| `show_spell_hud_overlay`, `reduce_screen_effects`, `broom_fov_effect`, `broom_wind_volume`, `broom_speed_particles` | CLIENT settings | nothing | read by each player's client |

Nothing is sent per tick. A sync is the override table (a few short strings per customised spell); the client
composes it over the default with the spell colour it already has, so a datapack recolour still reaches every
property nobody overrode. `ClientBeamVisuals` is replaced whole per sync and cleared at logout; live beams are
restyled on arrival (a switched-off beam disappears at once).

## 6. The Beams page

- Left: the beam spells (swatch = spell colour, ✕ = not drawn, • = customised).
- Preview: two viewports (Default above Edited; "Compare" toggles) drawn by `BeamPreviewViewport` through
  `BeamEntityRenderer#submit` from a hand-built `BeamRenderState` — updated every frame from the rows **including
  unsaved drafts**, so every change shows at once, no restart, no recast. Pause freezes the preview's own clock
  (crackle, spin, sparks, fades); Replay restarts it; fades loop (`BeamFadeCycle`) so they can be judged.
  "Watch in world" spawns the client-only preview beam on the administrator's wand with the draft look and hides the
  panel for five seconds (`peek`); no packet, no other player sees it.
- Presets: selector, name field, Load / Save as new / Save / Duplicate / Rename / Delete (confirmed), outcome line.
- Rows: Look, Lightning, Fade, Impact; Reset to default; a note naming what is not adjustable and why.

## 7. Preview cleanup

- Viewports keep nothing: a local render state per frame.
- The in-world preview beam (`BeamChannelClient.PREVIEW`) is stopped by `AdminPanel.dispose()`, which the Control
  Center calls on section change, on a Visuals tab change, and from `Screen.removed()` — every way the screen can go
  (Back, Esc, disconnect, another screen opening). `BeamChannelClient.clear()` at logout stops it too.
- Belt and braces: a preview beam discards itself after `PREVIEW_TTL_TICKS` (100) unless an open editor keeps it
  alive each tick. The debug editor (`BeamStyleScreen`) now does the same and stops its preview in `removed()`.
- The capture plan logs `previewRunning` before and after leaving the Beams tab.

## 8. Other tabs

- **Impacts** — `impact_intensity` of each beam as rows; projectile / targeted impacts are not adjustable (their burst
  has no spell to look up).
- **HUD** — the spell HUD toggle, and a preview on a dark backdrop with sample numbers on its own clock: the real
  `SpellDiamondOverlay.renderPreview` with its sweep, and the new shared pieces `HudDurationBar` (label, draining bar,
  `HudTime` readout: `45s`, `3:07`, `23:59:12`) and `HudStatusChip`. They are the groundwork for the Philosopher's Stone
  / Elixir of Life timer and later timed statuses; nothing live uses them yet, on purpose.
- **Entities** — the creature roster; a click opens `EntityViewerScreen`, which gained a light selector (full,
  torchlit, dim, dark — block light only, so it does not depend on the time of day).
- **Debug** — `BeamQuality` for this client (LOW: no sparks, one shell, bolts capped at 4 segments; MEDIUM = HIGH = full
  look), the beam debug editor, a link to the Debug section, and a readout (beams drawn, preview beam, overridden
  spells, quality). `/wandb debug beam preset` now reaches `BeamQuality` as well as the legacy `BeamSettings`.

## 9. How a new beam spell becomes editable

1. Add it to `BeamVisualDefaults.SPELLS` and its `forSpell` case (and to the channel's `WandBeamSpellIds`;
   `BeamSpellCoverageTest` fails until both agree).
2. That's all: settings, sync, browser, presets, impacts and lang keys (shared per property) follow.

A new **property**: a `BeamVisual` field, a `BeamVisualProperty` constant, the renderer code that reads it
(`BeamAppearance.of` / `BeamGeometry`), two lang keys `admin.wizards_and_beasts.beam_property.<id>[.desc]`
(`AdminVisualPayloadCodecTest` checks them).

## 10. Network

| Direction | Payload | Handling |
|---|---|---|
| C2S | `AdminVisualPayloads.ListRequest` | `sendList`: non-admin / no visual capability → dropped + WARN |
| C2S | `AdminChangeSettingC2SPayload` / reset with a `beam/…` id | `AdminSettingService` like every setting |
| C2S | `AdminVisualPayloads.PresetRequest(op, id, name, values)` | `runPreset`: refused → `ActionReply(unauthorized)`; else outcome, and a fresh listing to every authorised admin |
| S2C | `AdminVisualPayloads.ListReply` / `ActionReply` | client state + screen refresh |
| S2C | `BeamVisualSyncS2CPayload` | every player: join, `/reload`, each change |

## 11. Tests

| Test | Proves |
|---|---|
| `BeamVisualPropertyTest` | round trip, canonical text, 20 malformed / out-of-range values refused, unknown ids (length, trail, sound) absent, apply skips bad entries |
| `BeamAppearanceDefaultsTest` | the four default looks draw exactly as before (old table copied in), Leviosa off, opacity scale |
| `BeamPresetAndFadeTest` | built-ins complete, custom ids never collide, name bounds, fade loop, HUD timer text |
| `BeamSpellCoverageTest` | look table = beam channel spells |
| `AdminVisualPayloadCodecTest` | list / preset / reply / sync round trips, bad op and hostile counts refused, setting id parsing, lang keys |
| GameTest `admin_visual_beam_look_syncs_and_resets` | stored, canonical, one sync per change to a player, writing the default removes the override, reset recovers the default |
| GameTest `admin_visual_invalid_values_rejected` | 11 invalid values refused, unknown properties/spells unknown, no-visual-capability admin and non-admin packet refused, nothing stored |
| GameTest `admin_visual_presets_and_builtins` | create / name clash / bad name / incomplete look / duplicate built-in / rename / save / delete; built-ins refuse all three changes and stay intact; preset label; delete leaves spells alone; non-admin packet refused |
| GameTest `admin_visual_gameplay_unchanged` | every property changed, spell damage / cooldown / range unchanged; impact 0 → none, 2 → double, non-beam spells untouched |

Screen check: `WB_ADMIN_CAPTURE=<dir> WB_ADMIN_CAPTURE_WORLD=<save> WB_ADMIN_CAPTURE_PLAN=visual ./gradlew runClient`.

## 12. Limits

- The legacy `client.wand` beam cluster and the orphaned `BeamDebugScreen` are still in the tree (merging or deleting
  them is a separate call; the live renderer does not read them).
- Projectile / targeted-spell impacts and trail particles are not per-spell adjustable: their payloads carry no spell.
- The HUD timer pieces are previewed only; no live elixir HUD yet.
- The in-world preview is local to the administrator. Other players see a look only after Apply.
