# Module management

Phase 9 of the Control Center: **Modules** (Modules | Profiles), built on the existing module system. Nothing was
replaced: `ModuleStateService` is still the one write path, `ModuleStateSyncPayload` the one sync, `ModuleManager`
the read cache every gate asks.

---

## 1. Audit

| Piece | Where | Notes |
|---|---|---|
| Modules | `module/Module.java` | 29 enum constants; registration is never gated, only access |
| States | `ModuleState` | DISABLED, ENABLED, PREVIEW (access + "preview" marker), COMING_SOON (roadmap marker, never operator-settable) |
| Authority | `ModuleStateData` (overworld SavedData) | seeded from `ModuleDefaults.configuredDefault` (config `moduleDefaults.*`, else shipped) on first access |
| Write path | `ModuleStateService.setState/setSetting` | persists, refreshes the cache, broadcasts, reloads datapacks when a state changed (recipe conditions) |
| Sync | `ModuleStateSyncPayload` | full snapshot, on join and after every change; client `ClientModuleState` → `ModuleManager.acceptAuthoritative` |
| Commands | `/wandb admin module [list|set|setting]` | through the service |
| Admin | `ModuleStateSettings` (13 modules before this phase) | bindings over the service |
| Settings schema | `module/settings` | framework only; no module registers settings |
| Orphan | `ModuleUpdateRequestPayload` | registered, never sent by any client; still guarded (`AdminAccess`) and now dependency-checked through the service |
| Dependencies | — | **none existed.** Modules lean on each other only through gates in code |

## 2. Dependencies (`module/ModuleDependencies`)

Only edges the game already enforces, each citing its gate:

| Dependent | Dependency | Kind | Enforced by |
|---|---|---|---|
| Apparition | Player abilities | REQUIRES | `ApparitionServerLogic.evaluateStart` refuses unless both are on |
| Ministry | Gringotts | PARTIAL | `MinistryFines.isActive`: no vault to bill, no fines |
| Bestiary | Magizoology | PARTIAL | `BestiaryHarvestLootModifier.isActive`: study-gated drops need both |
| Magizoology | Bestiary | PARTIAL | same |

Found by scanning every co-gated `ModuleManager.isEnabled` and reading the readers. Not claimed: Dark Arts → Spells
(casting is not gated on `WANDS_AND_SPELLS`), and anything else that is a design intention rather than a gate.

Rules, enforced in `ModuleStateService.change` (so commands, the admin service and the orphan packet all obey):

- **Enabling (or previewing) a module whose REQUIRES dependency is off is refused** — `DEPENDENCY_MISSING`; the
  admin setting refuses earlier with the sentence "Cannot enable Apparition because Player abilities is disabled."
- **Disabling a module closes its REQUIRES dependants in the same persisted step** and reports them
  (`Change.alsoDisabled`). Before that: the Modules page says "Disabling Player abilities will also disable
  Apparition."; the admin service asks for confirmation with that sentence; the command needs `confirm`
  (`/wandb admin module set player_abilities disabled confirm`) and prints what else went off.
- **PARTIAL**: allowed both ways, warned (confirmation in the panel, a line in the command reply).

A new edge needs: a line of code that refuses or loses something, an `Edge` citing it, two lang keys
(`module.wizards_and_beasts.dependency.<dependent>.<dependency>.blocked|effect`), and a check in
`admin_module_flags_and_gates`.

## 3. The Modules page

Every module from `Module.values()`; nothing is listed by hand. For each: name (datagen lang), state swatch,
`!` when something it requires is off, `◫` when it is decided at world generation. The detail shows:

| Field | Source |
|---|---|
| Status, default | `ModuleManager` (synced), `ModuleDefaults.shipped` |
| Enable / Preview / Disable | the module's admin setting `module_<id>`, sent at once; the server applies, refuses with the reason, or asks |
| Description | `admin.wizards_and_beasts.setting.module_<id>.desc` |
| Depends on / Depended on by | `ModuleDependencies`, with live state of each side |
| Runs on | `ModuleAdminInfo.side`: server + client (the default: server gates, clients hide content and read it for display) or client presentation only (Character sheet, Player animation) |
| When a change takes effect | `ModuleAdminInfo.applyMode`: runtime for all (recipes and the content index reload by themselves) except **Azkaban** and **Chamber of Secrets**: **NEW CHUNKS ONLY** |
| Where else its switch is | `ModuleAdminInfo.home` (Travel, Ministry, Economy, World, Heritages, Dark Arts, Creatures) |

No module needs a restart to switch: the state is read on every gate call, and the datapack reload that keeps
recipe conditions honest runs automatically after any state change.

## 4. Server authority and sync

- Module state is server-owned (`ModuleStateData`). Every door — admin packet, command, the orphan packet — reaches
  `ModuleStateService`; the admin path checks authority (`AdminCapability.CONFIG`, or the module's home section's
  capability) before the service runs.
- Clients only receive: `ModuleStateSyncPayload` unchanged. The Control Center listens to the same sync
  (`ClientModuleState.whenApplied`), so a cascade that closes a dependant shows on every open panel.
- The admin history records the change that was asked for; a cascade is logged by the service and visible in the
  next sync, not as a separate history entry.

## 5. Profiles (groundwork)

`module/profile`: `ModuleProfile` reserves DEFAULT, RPG, HARDCORE, SANDBOX, MINIMAL, DEVELOPER. Only **Default** has
states (the build's shipped states); the rest are `defined() == false` on purpose — choosing them is a design call.
`ModuleProfilePlanner.plan(current, target)` gives the ordered steps (dependants close before their base; bases open
before their dependants), modules left alone because they are COMING_SOON, and any REQUIRES edge the result would
break (such a profile is refused, not applied). The Profiles tab shows the plan for each defined profile. Applying a
profile later = running its steps through `ModuleStateService.change`.

**Since Phase 10** (`ADMIN_PROFILES.md`) whole configurations — settings and module states — are applied in the
Profiles section. Its applier orders module changes with `ModuleProfilePlanner` and reports `plan.broken()` edges as
validation errors; this tab stays a read-only preview of the module-only profiles.

## 6. Tests

| Test | Proves |
|---|---|
| `ModuleDependenciesTest` | enable refused without a REQUIRES dependency (also for Preview); disable cascades only open dependants; PARTIAL warns, never blocks or cascades; graph has no self/duplicate edges, every edge cites a gate, REQUIRES is acyclic; planner order both ways, broken profiles refused, COMING_SOON locked; Default = shipped and respects every edge; the sync codec carries every module; every module has its name, switch name/desc/warning and apply mode; edge, profile and state text exist |
| GameTest `admin_module_dependencies_enforced` | admin service: Apparition refused with the edge's sentence; opens once its base is on; disabling the base asks with the cascade sentence; confirmed, both close and the dependant is stored closed; a PARTIAL warns and does not cascade |
| GameTest `admin_module_command_dependencies` | the command refuses a missing dependency, refuses a cascade without `confirm`, and closes both with it |
| GameTest `admin_module_sync_and_authority` | a change reaches a player in `ModuleStateSyncPayload` with every module and equal to the server cache; non-admin packet and an admin without the config capability are refused |
| GameTest `admin_module_flags_and_gates` | every module has a switch filed in its home section with the right apply mode, none restart-required; Apparition refuses without abilities; fines stop without Gringotts and resume; study drops stop without Magizoology and resume |

Module changes in GameTests run inside `ModuleStateService.withoutDatapackReload` (scoped to one synchronous call)
and restore the world's states exactly.

Screen check: `WB_ADMIN_CAPTURE=modules WB_ADMIN_CAPTURE_WORLD=<save> WB_ADMIN_CAPTURE_PLAN=modules ./gradlew runClient`.
