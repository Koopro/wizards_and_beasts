# Admin UI — the Control Center as the primary management tool

Status 2026-10-07. This is the map from the "complete in-game administrative UI" brief to what the mod ships.
The machinery lives in `ADMIN_FRAMEWORK.md`; each section has its own document (linked below). This page answers
three questions: *where is each thing*, *how is it kept server-authoritative*, and *what is the command for it*.

Open it with `/wandb admin` (bare; the console gets the command list), `/wandb admin panel`, or the keybind. Commands stay supported for automation and debugging; every
mutation the screen can make has a command that goes through the same service (§4).

## 1. Sections

The brief's ten top-level sections, plus the ones the mod already had. Sections with no live panel are hidden from
the sidebar (no "soon" placeholders).

| Brief | Sidebar | What it manages | Document |
|---|---|---|---|
| — | Dashboard | server tiles, recent changes with Undo, viewer's authority | `ADMIN_FRAMEWORK.md` §9 |
| PLAYERS | Players | online players: 8 facets (overview, heritage, skills, spells, effects, ministry, economy, debug) + action log | `ADMIN_PLAYERS.md` |
| MAGIC | Magic (Rules · Spells · **Availability**) and Dark Arts | global spell rules, per-spell values, spell test, bulk enable/disable | `ADMIN_MAGIC.md` |
| — | Heritages | heritage rules, onboarding preview, player inspector | `ADMIN_HERITAGE.md` |
| CREATURES | Creatures (Creatures · **Spawning** · Rules) | roster pages, variants, natural spawning (bulk), test spawns, Entity Viewer | `ADMIN_CREATURES.md` |
| — | Brewing, Wands, Travel | brews/recipes, wand woods/cores/generator, brooms/Floo/Apparition | `ADMIN_BREWING.md`, `ADMIN_WANDS_BROOMS.md`, `ADMIN_WORLD_ECONOMY.md` |
| MINISTRY | Ministry | Trace/law rules | `ADMIN_WORLD_ECONOMY.md` |
| ECONOMY | Economy | Gringotts, prices, dragot rules | `ADMIN_WORLD_ECONOMY.md` |
| WORLD | World | world-generation and world rules | `ADMIN_WORLD_ECONOMY.md` |
| MODULES | Modules | every module as a setting, dependency graph enforced | `ADMIN_MODULES.md` |
| — | Profiles | presets, snapshots, import/export, grouped history | `ADMIN_PROFILES.md` |
| VISUALS | Visuals (Beams · Particles · Impacts · HUD · Screen · Entities · Debug) | beam looks, preset library, HUD/screen previews | `ADMIN_VISUALS.md` |
| PERFORMANCE | Performance | live measured metrics, LOW/MEDIUM/HIGH presets | `ADMIN_PERFORMANCE_DEBUG.md` |
| DEBUG | Debug | leased debug tools, live diagnostics | `ADMIN_PERFORMANCE_DEBUG.md` |

## 2. Server authority

The rules of the brief, and where each is enforced:

| Rule | How |
|---|---|
| Server-authoritative | Settings: `AdminSettingService` is the only mutation API. Player actions: `PlayerAdminService.perform`. Presets, modules, profiles, debug leases, beam presets: their own services. The client holds only drafts. |
| Permission checked server-side | `AdminContext.of(ServerPlayer)` → `AdminPolicy.capabilitiesOf` → one NeoForge permission node per `AdminCapability` (`wizards_and_beasts.admin.config` … `.money`), defaulting to the admin rule. Every service checks again; the command group gate is only a convenience. |
| Client never decides | Descriptors tell the screen what *this viewer* may edit (read-only rows, ⊘ on sections), but that is display only. Dangerous changes are recognised by the server (`CONFIRMATION_REQUIRED`), never by the client. |
| Every mutation has a command | §4. |
| Explicit mutations | One payload per change (`id`, text value, `confirmed` flag, request id). No "save the whole page" payloads. |
| GUI reflects server state | Every result carries the authoritative value read back after the attempt; the row overwrites its draft with it. Bulk lists re-request their rows after applied results arrive. |
| No optimistic permanent state | Drafts are gold-marked until applied; a refused edit snaps back; closing with drafts asks first. |
| Abuse | `AdminRequestLimiter`: per-sender token bucket (burst 80, 30/s) in front of every admin C2S payload. |

## 3. Controls

| Control | Widget | Where it is used |
|---|---|---|
| Toggle | `AdminToggle` | every boolean setting (rules, modules, per-spell / per-creature switches) |
| Slider | `AdminSlider` | bounded numbers with a small span (multipliers, bounds, broom stats); keyboard steps with arrows |
| Number field | `AdminTextField` (numeric kind) | numbers with wide or open ranges; parsed and bounded on the server |
| Text field | `AdminTextField` | string settings (effect lists, names), preset names |
| Dropdown | `AdminEnumSelector` | enum settings (quality levels, modes) |
| Multi-select | `AdminMultiSelect` (in `BulkTogglePanel`) | Magic → Availability (spell enabled), Creatures → Spawning (natural spawn) |
| Player selector | Players section list with search | Players; global search also lists online players |
| Entity selector | Creatures browser + Entity Viewer | creature pages, variants, test spawns |
| Resource selector | `AdminPicker` | Players → Heritage (lineage), Spells (spell to unlock/grant), Effects (effect to preview) |
| Search | global search (⌕, Ctrl+F, `/`) + per-browser filters + picker / multi-select filters | everywhere |
| Reset | row reset, Reset section (always confirmed), profile revert, Dashboard Undo | settings, profiles |
| Preview | beam preview, Entity Viewer, HUD preview, effect "preview on me", wand generator, brew preview | Visuals, Creatures, Players, Wands, Brewing |
| Confirm | `AdminConfirmDialog` + server-held confirmations | dangerous settings, destructive player actions, bulk applies, preset deletes |

**Multi-select** edits nothing new: each tick is the existing per-entity boolean setting
(`spell/<id>/enabled`, the creature's natural-spawn setting). Ticked rows that differ from the server carry a gold
draft mark; *Tick shown* / *Untick shown* act on the filtered rows; *Apply N* opens a dialog naming every change
(the first twelve by name, the rest counted), then sends one ordinary change per row. Any the server holds as
dangerous come back through the normal confirmation dialog. The list then shows what the server stored.

**Picker**: click (or Enter/Space when focused) opens a list under it; typing filters, ↑↓ move, Enter picks, Esc
closes. The list is drawn by the screen after every widget, so it is never under the buttons below it, and those
buttons do not light up beneath it. Options come from the server; the picker only chooses.

## 4. Mutations and their commands

Each row's screen action and command call the same server entry point.

| Mutation | Screen | Command | Service |
|---|---|---|---|
| Change / reset a setting | any settings row, Reset section, Undo | `/wandb admin config set|reset|reset_section|reset_all confirm|undo|confirm` | `AdminSettingService` |
| Spell values, disable, test | Magic → Spells | `/wandb admin spell set|disable|test` | `AdminSettingService`, spell test service |
| Bulk spell / spawn switches | Magic → Availability, Creatures → Spawning | `config set` per id | `AdminSettingService` |
| Module state | Modules | `/wandb admin module …` (needs `admin.config`) or `config set module_<id>` | `ModuleStateService` |
| Player action (any) | Players facets | `/wandb admin players act <player> <action> [argument|amount] [confirm]` | `PlayerAdminService.perform` |
| Heritage assign / reset onboarding | Heritages inspector | `/wandb admin heritage assign|reset_onboarding` | `HeritageAssignment` |
| Creature test spawn / cleanup | Creatures | `/wandb admin creature spawn|cleanup` | creature lab service |
| Test wand | Wands generator | `/wandb admin wand give` | wand generator service |
| Profiles | Profiles | `/wandb admin profile apply|save|snapshot|import|revert_group …` | `ProfileService` |
| Performance preset | Performance → Presets | `/wandb admin performance preset <low|medium|high> [confirm]` | `PerformancePresets.apply` |
| Beam preset library | Visuals → Beams | `/wandb admin visual preset list|duplicate|rename|delete [confirm]` | `BeamVisualAdminService.perform` |
| Debug tools | Debug → Tools (leased) | `/wandb debug …` | `DebugLeases` (panel) / debug modules (console) |

Debug tools are deliberately different: the panel *leases* a tool and it is released when the screen closes; the
console keeps its own, persistent `/wandb debug` commands.

## 5. Dangerous operations

| Requirement | How |
|---|---|
| Warning dialog | `AdminConfirmDialog` (warning or danger severity), opened by the screen for destructive actions and re-opened for every change the server held as `CONFIRMATION_REQUIRED` |
| Exact affected object | dialogs name the setting with old → new, the player and action, the preset, or every spell / creature in a bulk apply |
| Confirmation | enforced on the server (`confirmed` flag; commands repeat with `confirm`); an unconfirmed dangerous request changes nothing |
| Permission requirement | capability per setting / action (`admin.money` for money, `admin.world` for positions, …); refusals carry `UNAUTHORIZED` and no value |
| Audit / debug logging | setting changes: persistent `AdminHistoryData` + INFO log; player actions: persistent `PlayerActionLog` (held, refused and applied attempts; unauthorised ones only in the server log, so outsiders cannot flood the world's log); beam presets: INFO log per operation; debug leases: logged on grant and release |

## 6. Live previews

| Brief | Where |
|---|---|
| Beam style, width, colour | Visuals → Beams: live preview on the production beam renderer, follows drafts |
| Particles, visual effects | the beam preview draws the look's sparks, shells and bolts at the chosen `BeamQuality`; Impacts edits `impact_intensity` per beam (no separate preview: impact bursts carry no spell). Particles / Screen are each player's own preferences, shown read-only |
| Spell presentation | Visuals → Beams per spell; Magic → Spells test cast |
| Creature variant | Creatures page variant selector + Entity Viewer |
| Entity scale | Entity Viewer scale selector (×0.5 … ×3, preview only: sets `Attributes.SCALE` on the preview entity) |
| HUD | Visuals → HUD preview panel |

Previews stop when the panel changes or the screen closes (`AdminPanel.dispose`).

## 7. Testing

- GameTests: `AdminControlTests` (permission nodes, `players act`, performance/beam preset commands) plus the earlier
  admin suites; JUnit: `AdminRequestLimiterTest`, `CommandTreeShapeTest` and the framework tests.
- Screenshots: `WB_ADMIN_CAPTURE=<dir> WB_ADMIN_CAPTURE_PLAN=controls WB_ADMIN_CAPTURE_WORLD=<copy of a capture world>
  ./gradlew runClient` shoots the sidebar, Availability (unedited, filtered, drafts, confirm), Spawning, and the
  Players picker open and filtered.

## 8. Limits

- Bulk lists cover the two per-entity switches that matter most (spell enabled, natural spawning). Another one is a
  `BulkTogglePanel` subclass of about forty lines: rows, setting id per row, lang keys.
- The picker is single-choice. Multi-choice of resources outside a bulk list (e.g. an allow-list setting) would be a
  new `SettingKind`; nothing in the mod needs one yet.
- Rate limits are fixed constants, not settings.

## 9. Reports, operations and the audit timeline (2026-10-15)

Two reusable channels so a new read-only page or one-shot action needs **no payload, no command and no bespoke
panel** — the old pattern (one request/reply pair, rate limiter, client cache and chat printer per page) stops here.

| Piece | Where | What |
|---|---|---|
| Report | `admin/report/AdminReport` + `AdminReports` registry | id, section, capability, `build(request, ReportBuilder)`; reads owning systems now, keeps nothing |
| View | `ReportView` / `ReportBuilder` | sections of label/value lines with a tone, optional subjects (picker), actions (buttons); bounded (600 lines, text clipped) |
| Read door | `AdminReportService.read` | admin check before id lookup (no probing) → exists → capability → build (exception = `FAILED`, logged) |
| Operation | `admin/operation/AdminOperation` + `AdminOperations` | id, section, capability, destructive, `validate`, `execute` |
| Act door | `AdminOperationService.perform` | admin → exists → capability → bounded input → validate → confirm if destructive → execute → audit |
| Audit | `AdminAuditLog` (world SavedData, 2000) | every operation attempt by an authorised admin, plus test spawns/cleanup, test wands, beam presets, heritage assign/reset |
| Wire | `AdminReportPayloads` | one report request/reply, one operation request/reply; text bounded on read; reports ≤1 per 10 ticks per page |
| Page | `client/admin/screen/AdminReportPanel` | subject picker, filter, refresh, op buttons (confirm dialog for destructive or server-held) |
| Commands | `AdminReportCommands` | `/wandb admin report list|<id> [subject]`, `/wandb admin op list|<id> <subject> [argument] [confirm]` |

Shipped reports: `creature_live` (Creatures → Live: body/scale, variant, bond, ecology intent, running AI goals),
`world_generation`, `world_locations`, `world_floo` (World), `ministry_licences`, `ministry_standing`,
`ministry_debug` (Ministry), `economy_markets` (Economy → Market tools), `diag_network`, `diag_registry`,
`diag_data`, `diag_modules` and `audit` (new **Diagnostics** section, capability `admin.debug`; audit also on
Economy → Audit filtered to money).

Shipped operations: `creature_scale|variant|freeze|heal|discard` (need `admin.world`; discard is destructive and
refused for a bonded companion), `economy_market_reset` (destructive; `/wandb admin economy market reset` now runs it).

Players gained four facets: **Conditions** (lycanthropy/obscurus live state), **Wand** (own wand identity, bond,
wear), **Profession** (career rank/XP/wage/goals, vocation, build pathways, exam grades), **Creatures** (companion
roster incl. unloaded/remembered). The tab bar wraps onto a second row when tabs no longer fit.

**Add a report:** class + `BuiltinReports` line + `report.<id>.title|summary` and its row keys + an
`AdminReportPanel` tab. **Add an operation:** class + `BuiltinOperations` line + `operation.<id>`, `.desc`
(+ `.warning` if destructive) + `ReportBuilder.action(...)` on the page that offers it.
`AdminReportFrameworkTest` fails on a missing title/summary/operation key; GameTest `admin_reports_build_on_a_live_server`
builds every registered report against a real world.
