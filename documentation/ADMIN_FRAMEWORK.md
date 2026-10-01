# Administration framework and Control Center

Phase 1 (2026-09-30): the reusable framework and the Control Center shell. This page is for developers
extending it. It covers the architecture, how authority flows, how to add a setting or a panel, and what
Phase 2 inherits.

**The one rule:** the server owns every administrable value. The Control Center screen and the
`/wandb admin config` commands are two views of the same server-side API, `AdminSettingService`. Neither
one validates anything itself.

---

## 1. Layers

| Layer | Package | Loads on | Responsibility |
|---|---|---|---|
| Authority | `admin.access` | both | `AdminContext` (who is asking, what they may do), `AdminCapability`, `AdminPolicy` |
| Settings | `admin.config` | both | `AdminSetting` definition, `SettingType` (parse / bounds / format), `SettingBinding` (where the value lives), `AdminSettingRegistry` |
| Catalog | `admin.config.catalog` | server logic | `ConfigSettingCatalog` (which `Config` keys are exposed; pure metadata), `ConfigSettingBinder` (binds them to `Config.SPEC`) |
| History | `admin.history` | server logic | `AdminChangeRecord`, `AdminChangeHistory`, `InMemoryChangeHistory` (bounded), `AdminHistoryData` + `AdminHistoryStore` (persisted per world since Phase 10) |
| Profiles | `admin.profile` | server logic | profile documents, codec, validator, all-or-nothing applier, `ProfileService` (Phase 10, `ADMIN_PROFILES.md`) |
| Service | `admin` | server logic | `AdminSettingService` (the only mutation API), `AdminResult`, `AdminRejection`, `AdminSettings` (the live instance), `AdminCategory` (sections) |
| Commands | `admin.command` | server | `AdminConfigCommands`: `/wandb admin panel`, `/wandb admin config …` |
| Network | `network.admin` | both | 5 C2S requests, 2 S2C payloads, `AdminNetworkService` (the single server handler), wire records |
| Client state | `client.admin` | client | `ClientAdminState` (cached mirror), `AdminClientRequests`, `AdminClientHandlers` |
| Screen | `client.admin.screen` | client | `AdminControlCenterScreen` (the shell), `AdminPanel`s, `AdminEditSession` (drafts) |
| Widgets | `client.admin.widget` | client | the `Admin*` components, `AdminControlFactory` (setting → control), `AdminTheme` |

The packages follow the repo's existing conventions: payloads live under `network/<domain>` and client
code lives under `client/`. That is why the brief's suggested `admin/network` and `admin/screen` became
`network/admin` and `client/admin/screen`.

## 2. Server authority

```
 command ─┐                                   ┌─ binding.set(v) ─► Config.SPEC.set + save()
          ├─► AdminContext.of(source) ─► AdminSettingService ─┤          └─► ModConfigEvent.Reloading ─► Config.onLoad
 packet ──┘   (AdminPolicy ► AdminAccess)     │               ├─ history.record(...)
                                              │               ├─ listeners / observers ─► AdminNetworkService.announce
                                              └─ AdminResult (authoritative value) ─► caller
```

`AdminSettingService.change / reset / resetSection / resetAll / undoLast` judges every request in this
order, whichever way it arrived:

1. **Is the actor an administrator at all?** This is checked *before* the id is looked up, so an outsider
   cannot probe which ids exist. An unknown id and a known id both come back as `UNAUTHORIZED`.
2. **Does the setting exist?** If not: `UNKNOWN_SETTING`.
3. **Does the actor hold the setting's `AdminCapability`?** If not: `UNAUTHORIZED`.
4. **Is the backing store available?** If not: `UNAVAILABLE`.
5. **Is the setting server-authoritative?** If it is `SettingScope.CLIENT`: `CLIENT_ONLY`.
6. **Parse, bounds, rules.** The text must parse as the type (`INVALID_VALUE`), fall inside the type's
   bounds (`OUT_OF_RANGE`), and pass every `SettingValidator` (`CONFLICT`, plus a detail key).
7. **Store.** The service stores the value, reads it back, records it in history, and notifies the
   setting's listeners and the service observers.

Every `AdminResult` carries the **authoritative value after the attempt**, whether the change was
applied, unchanged or refused. The client overwrites what it showed with that value, so a refused edit
snaps back instead of lingering. Refusals sent to someone without read authority carry an empty value.

**The server never trusts client text.** Values travel as text, the same text a command argument carries,
and the setting's `SettingType` parses it on the server. A slider that "cannot" leave its range is a
courtesy. The server re-checks against the definition, never against the metadata it sent.

## 3. Permissions

`AdminContext.of(CommandSourceStack)` is the only constructor server code uses. Packets use
`AdminContext.of(ServerPlayer)`, which goes through the same command source, so a packet can never carry
more authority than the same player would have typing the command.

`AdminPolicy.capabilitiesOf(source)` in Phase 1:

- asks `AdminAccess.allows(source)` and reuses its rules unchanged: the `adminUuids` allow-list when one
  is configured, operator permission when it is not, and the console / command blocks / RCON always;
- returns every capability for an admin and none for anyone else.

`AdminCapability` names seven areas: `admin.config`, `admin.content`, `admin.visual`, `admin.debug`,
`admin.players`, `admin.world` and (Phase 11) `admin.money`, which alone may create or destroy a player's money. Each `AdminCategory` has a default capability, and each setting may name
its own. To introduce roles, replace `AdminPolicy.capabilitiesOf`, for example with one NeoForge
PermissionAPI node per `AdminCapability.node()`. Nothing downstream changes: the service already checks
per-setting capabilities, descriptors already carry `editable` per viewer, and the dashboard already
lists the viewer's authority.

The `/wandb admin` command group keeps its `requires(ADMIN)`. The service checks again anyway, because it
must not depend on having been reached through a gated node.

## 4. Settings

An `AdminSetting<T>` is made of:

- `id`: stable, sent on the wire and typed in commands;
- `category`;
- `SettingType<T>`: kind, parse, bounds, format, and UI metadata (min / max / step / options / maxLength);
- `SettingBinding<T>`: where the value lives;
- `scope`;
- `restartRequired`: set explicitly, and also derived from `ModConfigSpec` `worldRestart` / `gameRestart`;
- `dangerRule` (`dangerous()` = has one): decides per change whether confirmation is needed. The **server**
  enforces it: an unconfirmed dangerous change is answered `CONFIRMATION_REQUIRED` and not applied
  (Phase 2; see `ADMIN_MAGIC.md` §4);
- `capability`;
- `validators`;
- change `listeners`.

Display text is never stored in the setting. The keys are derived from the id by `AdminLangKeys`:

- `admin.wizards_and_beasts.setting.<path>`: the name;
- `….desc`: the description;
- `….warning`: the confirmation text, for dangerous settings only.

The built-in types are `bool`, `integer(min, max)`, `decimal(min, max[, step])`, `enumeration(Class)` and
`text(maxLength, pattern)`, all in `SettingTypes`. To add a custom type, implement `SettingType` and report
the `SettingKind` whose control should draw it. The client falls back to a text field for any kind it
does not know.

### Config.java integration

`Config.java` is **not modified**. `ConfigSpecBinding` wraps an existing `ModConfigSpec.ConfigValue`:

- `set(v)` calls `ConfigValue.set(v)` and then `Config.SPEC.save()`. `save()` writes the file **and** fires
  `ModConfigEvent.Reloading` synchronously (FML `LoadedConfig.save`). `Config.onLoad` then refreshes every
  public static and re-pushes `SpellPower.applyBounds` before `set` returns. Gameplay sees the change on
  its next read, and the value survives a restart.
- **Lost writes (fixed 2026-10-01).** FML's `ConfigWatcher` reloads the file on its own thread, under the mod's
  config lock, by swapping in a freshly read config object; `ConfigValue.set` does not take that lock. A reload
  triggered by an earlier save that lands between `set` and `save` made `save` write the old value back: the
  change was gone, and the service's read-back reported "1 -> 1". It surfaced as a flaky broom GameTest and
  matters most for profiles (many saves in a row). `set` now reads the value back after `save` (which waits on the
  same lock) and writes again until it holds, at most four times (`ConfigSpecBindingWriteTest`).
- `ConfigSettingBinder` derives the setting's type from the `ConfigValue` class:
  - `BooleanValue` becomes a toggle;
  - `IntValue` and `DoubleValue` become numbers whose bounds are the spec's `defineInRange` range;
  - `EnumValue` becomes a choice over its enum.

  No range is restated anywhere.
- The spell-power bounds rule reuses `SpellPower.Bounds`' own constructor as its validator. The ordering
  rule lives in one place.

### Scope: who reads the value

Every key lives in the one COMMON config, but some are read only on each player's client. For those, a
server write would report success and change nothing on a dedicated server, so they are
`SettingScope.CLIENT`: listed read-only with an explanation, and refused by the service.

| Setting | Scope | Why |
|---|---|---|
| `show_spell_hud_overlay`, `reduce_screen_effects`, `broom_wind_volume`, `broom_fov_effect` | CLIENT | read by client renderers |
| `broom_speed_multiplier`, `broom_gentle_landing` | CLIENT | read by `BroomMovement` / `BroomImpacts`, which run on the rider's client (broom flight is client-authoritative, see `BroomEntity#tick`) |
| everything else in Phase 1 | SERVER | read by server logic |

`perf_profile` and `enable_debug_tools` are read on both sides. The server effect is real (beam cadence,
debug ability grants), so they are SERVER. Their descriptions say that the client-side part follows
each player's own config.

### Settings exposed (Phases 1–6)

Phase 2 moved `enforce_spell_requirements` to Magic, added the spell rules, and added a Dark Arts section;
see `ADMIN_MAGIC.md` for those and for the per-spell values (provider-resolved, not listed here). Phase 3 added
the Heritages section; the per-heritage rules (`heritage/<id>/selectable`, `heritage/<id>/transformation`) are
listed in `ADMIN_HERITAGE.md`. Phase 4 added the Creatures section; the per-creature rules
(`creature/<id>/natural_spawn`, `creature/<id>/variant/<v>/enabled|weight`) are listed in `ADMIN_CREATURES.md`. Phase 5 added Brewing; per-brew and per-recipe values
(`brew/…`, `brew_recipe/…`, provider-resolved) are listed in `ADMIN_BREWING.md`. Phase 6 added Wands and Travel;
the wandmaking withdrawals (`wand_wood/…`, `wand_core/…`, `wand_pair/…`) and per-broom values (`broom/…`) are
provider-resolved and listed in `ADMIN_WANDS_BROOMS.md`.

| Section | Settings |
|---|---|
| Game Rules | `skill_respec_cost_knuts` |
| Wands → Rules | `enable_wand_allegiance` ⚠ (moved from Game Rules in Phase 6), `wand_affinity_strength`, `wand_bond_growth_multiplier` ⚠(>2×), `wand_defeats_to_win`, `wand_neglect_loss_multiplier`, `wand_foreign_backfire` |
| Brewing → Rules | `brewing_enabled` ⚠(off), `brew_speed_multiplier` ⚠(>2×), `brew_failure_multiplier`, `brew_require_heat_source` ⚠(off), `brew_contamination_penalty` |
| Creatures → Rules | `module_creatures` ⚠(opened), `creature_natural_spawns` (moved from Game Rules in Phase 4) |
| Magic → Rules | `enforce_spell_requirements` ⚠(off), `spell_damage_multiplier` ⚠(>2×), `spell_cooldown_multiplier` ⚠(<½), `spell_range_multiplier` ⚠(>2×), `spell_block_damage`, the five spell power bounds ⚠ (sharing the `SpellPower.Bounds` rule) |
| Dark Arts | `allow_unforgivable_curses` ⚠(on), `module_dark_arts` ⚠(opened) |
| Heritages → Rules | `module_heritage` ⚠(opened/closed), `module_player_stats` ⚠(opened/closed), `werewolf_forced_transform`, `werewolf_loss_of_control`, `werewolf_bite_infects` ⚠(on), `werewolf_wolfsbane_suppresses_transform`, `werewolf_pack_betrayal` ⚠(on), `vampire_feed_on_players` ⚠(on) |
| Ministry | `ministry_fine_scale_percent`, `ministry_days_per_year` ⚠ |
| Travel → Rules | `broom_server_speed_scale`, `broom_speed_guard` ⚠(off), `broom_speed_multiplier` (client; may only slow a rider on a remote server), `broom_gentle_landing` (client) |
| Visuals | `show_spell_hud_overlay`, `reduce_screen_effects`, `broom_wind_volume`, `broom_fov_effect`, `broom_speed_particles` (all client); per-beam looks `beam/<spell>/<property>` (server, provider-resolved, `ADMIN_VISUALS.md`) |
| Performance | `perf_profile`, `beam_target_scan_interval_ticks`, `beam_channel_effect_interval_ticks` |
| Debug | `enable_debug_tools` ⚠, `debug_log_spell_gate_reasons` |

⚠ = dangerous: the server holds the change until it is confirmed (in brackets: which direction asks).

## 5. How to add a setting

**A key that already exists in `Config`:**

1. Add an `Entry` to `ConfigSettingCatalog.ENTRIES`. Choose the section, decide the scope from **who reads
   the value** (grep the readers of `Config.<field>`), and set the dangerous flag if it changes gameplay
   for everyone in a way worth a confirmation.
2. Add the lang keys `admin.wizards_and_beasts.setting.<path>`, `.desc`, and `.warning` if dangerous.
   Insert them textually into `en_us.json`; never re-serialise the file. `AdminCatalogLangTest` fails
   until the keys exist.
3. That's all. The panel, commands, tooltips, history and network sync pick it up.

**A value that does not live in `Config`** (a module setting, a datapack override, world data):

1. Implement `SettingBinding<T>` over the existing store. Do not copy the value.
2. Register it from a contributor added at mod setup:
   `AdminSettings.addContributor(registry -> registry.register(AdminSetting.builder(id, type, binding).category(...).build()))`.
   The registry freezes on first use; contributing later throws.
3. Add the lang keys as above.

## 6. How to add a panel

Sections come from `AdminCategory`. For a section's panel:

- no settings: `ComingSoonPanel`, automatically;
- settings: the generic `SettingsPanel` draws one `AdminValueRow` per setting, with the control chosen by
  `AdminControlFactory`.

Most sections never need panel code.

For a bespoke page (a creature preview, a history browser, a module board):

1. Implement `AdminPanel`. `init` adds widgets through `AdminPanelHost.addPanelWidget`; `render` draws
   beneath the widgets; `renderOverlay` draws tooltips.
2. Hold view state only, such as scroll and filters. Values live in `ClientAdminState`, and drafts live
   in the host's `AdminEditSession`.
3. Return it from `AdminPanels.create` for its section, and update `AdminPanels.fits` to match.
4. Draw with `AdminTheme` and the `Admin*` widgets, not new colour literals.
5. A section with tabs **extends `TabbedPanel`** (keep a `static` tab field so reopening lands on the same tab) —
   never hand-roll a tab bar. Tabs size to the width, cycle with Ctrl+Tab and become the breadcrumb.
6. A list + detail page **extends `AdminBrowserPanel`**: it brings the search box (`AdminTextField.search`), the
   list, scrolling, tooltips, the breadcrumb (`crumb()` = the shown entry) and the empty states. Override
   `loaded()` (has the server answered?) and `emptyMessage()` so an empty list never says "waiting" forever.
7. Optional hooks: `crumb()` (where inside the section), `cycleTab`, `reveal(settingId)` (search led here:
   scroll the row into view and `AdminValueRow.highlight()` it).
8. Notices use `AdminWarning` with a severity — INFO (ℹ, purple), WARNING (⚠, gilt), DANGER (✖, wax) — and
   dialogs take the same severity. Glyph and colour always go together.

A new **section** is a new `AdminCategory` constant, plus its `.name` and `.summary` lang keys and a
sidebar glyph in `AdminControlCenterScreen.glyph`.

## 7. Network flow

| Direction | Payload | When | Server handling |
|---|---|---|---|
| C2S | `AdminOpenRequestC2SPayload` | client asks to open the panel | `AdminNetworkService.openFor`: non-admin → dropped + WARN, no reply |
| C2S | `AdminSectionRequestC2SPayload(sectionId)` | on switching section | `refreshFor(player, section)`: same gate |
| C2S | `AdminRefreshRequestC2SPayload` | the ⟳ button, and on visiting the dashboard | `refreshFor(player, null)` |
| C2S | `AdminChangeSettingC2SPayload(requestId, id, text, confirmed)` | Apply | `AdminSettingService.change`, then reply |
| C2S | `AdminResetSettingC2SPayload(requestId, id, confirmed)` | Reset section | `AdminSettingService.reset`, then reply |
| S2C | `AdminSnapshotS2CPayload(open, section?, info, descriptors)` | answer to open / section / refresh | — |
| S2C | `AdminSettingResultS2CPayload(requestId, AdminResult)` | answer to change / reset (`requestId` echoed), **and** broadcast (`requestId = -1`) of every applied change to every *other* online admin | — |

How the client stays in sync:

- **No periodic sync.** There is a snapshot on open, a section snapshot on navigation, and deltas after
  each mutation.
- **Descriptors are cached.** The client keeps them by id and replaces only `value` on a result.
  Metadata crosses the wire once per visit.
- **Other admins' changes arrive automatically.** The live service registers
  `AdminNetworkService::announce` as an observer. An applied change from **any** door, commands
  included, reaches every other open panel.
- **Lost replies don't leave rows stuck.** `ClientAdminState.expireStale()` expires a request after 6 s.
  The row shows "the server did not answer" and falls back to the last known server value.

A write request from a non-admin **does** get a `REJECTED/UNAUTHORIZED` reply with an empty value. An
administrator whose access is revoked mid-session sees the refusal instead of a row stuck on "saving",
and the reply discloses nothing.

## 8. Commands

`/wandb admin` (group, `requires(ADMIN)`):

- `panel`: opens the Control Center for the calling player (`AdminNetworkService.openFor`).
- `config [list [section]]`
- `config get <setting>`
- `config set <setting> <value…>`: `AdminSettingService.change`
- `config reset <setting>`: `…reset`
- `config reset_section <section>`: `…resetSection`
- `config reset_all confirm`: `…resetAll` (bare `reset_all` only explains itself)
- `config undo`: `…undoLast` (server-wide, the most recent applied change; refuses when the setting has
  changed since)
- `config history [count]`

**Rule for future admin commands:** build `AdminContext.of(source)` and call the service. Do not parse,
bound or store a value in the command.

`/wandb admin profile …` (Phase 10) lists, previews, applies, saves, snapshots, exports, imports and reverts
profiles and history through `ProfileService`; see `ADMIN_PROFILES.md` §9.

The existing `/wandb admin module …` tree is unchanged. It still calls `ModuleStateService`, the
module system's own "one door".

## 9. Control Center screen

- **Layout.** Title bar with server state and ⟳ on top; section sidebar on the left (it collapses to
  glyphs under 400 px); panel in the centre; Back / Revert / Reset section / Apply at the bottom.
- **Visual language.** Ministry purple framing, parchment page, gilt accents, rubric headings, all from
  `WizardsPalette` through `AdminTheme`.
- **Drafts.** Edits are drafts until Apply. Apply sends them unconfirmed; any the server holds back as
  dangerous come back with their warnings and are listed in one dialog, then resent confirmed. The client
  never decides what is dangerous. Reset section always confirms. Closing with drafts asks before
  discarding them.
- **Row status line.** Shows, in priority order: saving → ✖ refused (with the reason) → edited → ✔ saved
  → changed by another admin → client setting / locked / default.
- **Not a pause screen.** In singleplayer a pause screen stops the integrated server, which would leave
  every request unanswered.
- **Navigation.** The title bar is a breadcrumb (`Control Center › Magic › Spells › Stupefy`). Back walks the
  sections visited this session and only then becomes Close. The sidebar marks sections this administrator
  may view but not change with ⊘ (tooltip names the permission), and the footer repeats it on that section.
- **Global search** (⌕ in the title bar, Ctrl+F, or `/`). `AdminSearchIndex` (pure, unit-tested) holds one entry
  per section, setting, creature variant, spell, creature, brew (effects are keywords), heritage, wand wood/core,
  broom, beam look and preset, module, profile and online player. `AdminSearchSources` builds it from what the
  client already holds, and rebuilds only when a source signature changes (`ClientAdminState.version()` plus the
  identity of each section's last list); on opening it fetches each list once that has not arrived yet. Every
  query word must match; title matches outrank keyword matches. Choosing a setting opens its tab or entity page
  and highlights the row.
- **Save state in the footer**, one at a time with a glyph: ⟳ saving · ● N unsaved · ✖ N refused · ✖ N edits
  dropped (a setting became read-only — never silent) · ✔ saved · up to date.
- **Tooltips** of a setting: name, what it does, *Affects*, *Takes effect*, the confirmation/read-only line,
  *Default · Range*, and *Current* when it differs. Lines are cached per descriptor.
- **Dashboard.** Tiles (server, players, average tick, modules, spells, brews, creatures, heritages, debug tools
  on, settings changed) each open their section; recent changes show the setting's name, old → new as the row
  would show it, who, how long ago, and Undo (a confirmed history revert) while the change is still revertible.
- **Never crashes on a page.** A page that throws in init/render/tick is replaced by an error page with the cause
  and Retry; the cause is logged.
- **Keyboard.** Tab moves focus (a gilt ring shows it when the last input was a key); arrows / Shift+arrows /
  Home / End adjust a focused slider; Ctrl+F or `/` search, ↑↓ Enter in results; Ctrl+Tab / Ctrl+Shift+Tab
  switch tabs; PageUp / PageDown switch sections; Ctrl+S applies; Alt+← goes back; Esc closes the topmost thing
  (search, dialog, then the Control Center).
- **Capture.** `WB_ADMIN_CAPTURE_PLAN=polish` (world copy `wb_admin_capture_polish`) shoots search, the save
  states, Undo, focus, and every section at GUI scale 2, 3 and 4.

## 10. Tests

| Test | What it proves |
|---|---|
| `AdminSettingServiceTest` (JUnit) | unauthorised refused and value hidden; unknown ids indistinguishable to outsiders; per-setting capability; applied + recorded + announced; result carries the stored (read-back) value; no-op not recorded; out-of-range / malformed / NaN / hex / unknown refused; client-only refused; cross-setting rule with detail key; unavailable store; failing listener; reset, reset section, reset all; undo once-only, stale-undo refused, rejected never undone; bounded history; frozen registry |
| `SettingTypesTest` | text round-trip, step derivation, overflow |
| `AdminPayloadCodecTest` | round-trips; mangled id cannot alias a mod setting; oversized value refused at decode; unknown status reads as REJECTED |
| `AdminSearchIndexTest` | "phoenix" finds creature (first), variant, setting and wand core; every word must match; limit keeps the total; rebuild only on a new signature |
| `AdminSessionInfoCodecTest` | dashboard header round-trips with Undo data and counts; only the newest recent changes are sent |
| `AdminCatalogLangTest` | every catalog / section / rejection lang key exists; paths unique and typeable |
| `CommandTreeShapeTest` | `admin panel`, `admin config set`, `admin config reset_all confirm`, `admin config undo` resolve |
| GameTest `admin_packet_from_a_non_admin_is_refused` | the packet entry points against the live server: no change, no disclosure, no snapshot, no screen |
| GameTest `admin_packet_from_an_admin_is_applied_and_echoed` | applied → `Config` static updated synchronously; result echoed with `requestId`; out-of-range / malformed / unknown / client-only refused; reset restores the default; the original value is restored in `finally` |
| GameTest `admin_catalog_binds_to_config` | every catalog entry binds; ranges come from the spec; enums become choices |

## 11. Phase 2 and later

**Done in Phase 2** (`ADMIN_MAGIC.md`): setting providers, server-enforced confirmation (`confirmed` flag on
the change/reset payloads, `AdminConfirmations` + `/wandb admin config confirm` for commands, `BATCH` result
request id), the Magic and Dark Arts sections, module state as a setting (`ModuleStateSettings`, Dark Arts).

**Done in Phase 3** (`ADMIN_HERITAGE.md`): the Heritages section (browser, per-heritage rules, onboarding
preview, player inspector with assign/reset), entity-scoped setting ids generalised in `AdminLangKeys`
(`<family>/<entity>/<property>` → `<family>_property.<property>`), Heritage and Player Stats modules as settings.

**Done in Phase 4** (`ADMIN_CREATURES.md`): the Creatures section (roster browser, pages, variant rules, natural-spawn
rule via `SpawnPlacementCheck`, server-placed test spawns with cleanup) and the full-screen Entity Viewer on the
production renderers.

**Done in Phase 5** (`ADMIN_BREWING.md`): the Brewing section — effective-registry overlay over `Brews` /
`BrewingRecipes`, brew enable/effect-list and recipe settings via a provider, the reusable effect editor, preview, and
five global brewing rules. `AdminLangKeys`: a property segment's text is looked up up to its first dot.

**Done in Phase 6** (`ADMIN_WANDS_BROOMS.md`): the Wands section (wood/core browsers with recipe-defined pairings,
canonical lore read from the datapack files, a generator on the production item renderer with server-side
validation and test wands, allegiance/affinity rules) and the Travel section (broom browser with nine bounded
sliders, preview bars, an effective-definition overlay synced to riders, a server speed scale and speed guard).
`ClientAdminState` gained per-page setting maps (`acceptPageSettings`) for sections whose descriptors arrive with
their own reply; `AdminBrowserPanel` is the shared list-and-page base for new browsers.

**Done in Phase 12** (`ADMIN_PERFORMANCE_DEBUG.md`): Performance (live metrics measured by the game, LOW/MEDIUM/HIGH
presets as plain setting values applied through `ProfileApplier`, Gameplay/Server/Client/Visual classification) and
Debug (leased tools: own/all-player debug mode, spell logging, the mod-only log level, hitboxes; live diagnostics of
beams, cast sessions, recent casts and refusals, entities, modules, network). Debug tools are released when the Control
Center closes (`AdminControlCenterScreen.removed()`), on logout, server stop, or after 5 minutes without renewal.

**Done in Phase 11** (`ADMIN_PLAYERS.md`): the Players section. Search online players; eight facets read live from
each owning system; a closed list of player actions through one pipeline (permission → target → operation →
confirmation → snapshot → execute → rollback → log); a persistent `PlayerActionLog`; a separate `MONEY` capability;
positions only with `WORLD`; `/wandb admin players list|inspect|log`.

**Done in Phase 10** (`ADMIN_PROFILES.md`): configuration profiles. One versioned export schema for presets, saved
profiles, snapshots and imports; a validator that checks every value, module dependency and authority before anything
changes and flags RESTART / NEW CHUNKS; an all-or-nothing applier (module-first order, retry on order-dependent rule
conflicts, rollback, one datapack reload); persistent grouped history (`AdminHistoryData`, 1000 records) with revert
one / revert group; the Profiles section and `/wandb admin profile …`. Framework additions:
`AdminSettingProvider.enumerate`, `AdminSettingRegistry.everything`, `AdminSettingService.withGroup` / `changeAs` /
`revert`, `AdminChangeRecord.group`.

**Done in Phase 9** (`ADMIN_MODULES.md`): the Modules section. Every module (from the enum) is a `module_<id>` setting
over `ModuleStateService`; `ModuleDependencies` (only gate-backed edges) is enforced in the service — refused enables,
announced and confirmed cascades; the existing `ModuleStateSyncPayload` also refreshes open panels; profile groundwork
(`ModuleProfile`, `ModuleProfilePlanner`).

**Done in Phase 8** (`ADMIN_WORLD_ECONOMY.md`): Travel gains Floo and Apparition tabs (Apparition rules are world data,
honouring the earlier removal of its config keys); Ministry gains Rules/Law tabs; new **Economy** and **World**
sections. Framework: `ApplyMode` (runtime / reload / restart / new chunks) on every setting and descriptor, `TabbedPanel`,
`AdminInfoPanel` for read-only fact pages, `SettingsPanel.note/anySection`. Security: the vault money commands are now
administrator-only.

**Done in Phase 7** (`ADMIN_VISUALS.md`): the Visuals section (Beams | Particles | Impacts | HUD | Screen | Entities |
Debug). Beam looks are provider settings over `visual.beam` (visual config kept apart from gameplay config), synced to
every player on change only; presets are templates with protected built-in defaults; live preview on the production
beam renderer. Framework additions: `AdminPanel.dispose()` (called on section/tab change and from `Screen.removed()`,
so a panel's previews always stop), `SettingsPanel` slices (a filter plus a heading) for tabbed sections, `PlacedRows`
for a few rows beside a preview.

- **Setting replication for CLIENT-scope gameplay values.** Broom speed is solved (Phase 6: the server scale
  travels inside the synced definitions and the client multiplier is capped at 1 on remote servers).
  `broom_gentle_landing` is still read from each rider's own config.
- ~~Modules panel~~ — done in Phase 9 (`ADMIN_MODULES.md`).
- **Content sections** (spells, heritages, creatures, brewing, wands, Floo, Apparition, Gringotts): each
  exposes its existing store through a binding. Allowed-lists become a new `SettingKind` (set of ids)
  with its own control.
- **Roles.** Replace `AdminPolicy.capabilitiesOf` with per-capability permission nodes.
- ~~History UI and persistence~~ and ~~Presets~~ — done in Phase 10 (`ADMIN_PROFILES.md`).
- ~~Visual / beam settings and entity/model preview tools~~ — done in Phase 7 (`ADMIN_VISUALS.md`).
- **Rate limiting** of admin C2S requests. Today they are bounded (packet size, history ring) but not
  throttled.
