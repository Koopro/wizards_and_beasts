# Performance and Debug administration

Phase 12 of the Control Center (2026-10-01): **Performance** (Live | Presets | Settings) and **Debug** (Tools | Live |
Settings). Two rules shaped it:

- **No fabricated metrics.** Every number is one the game measured. A metric the game does not measure in the current
  situation is shown as "not measured" or "not measurable", never as a number.
- **Debug tools never outlive the panel.** A tool switched on from the Control Center is a lease, reverted when the
  administrator closes the Control Center, logs out, when the server stops, or when the panel stops renewing it.

---

## 1. Audit

| Area | What exists | Notes |
|---|---|---|
| Performance config | `perfProfile` (LOW/MEDIUM/HIGH), `beamTargetScanIntervalTicks` (1–20, default 2), `beamChannelEffectIntervalTicks` (1–20, default 5) | The profile also shifts both intervals (`WandBeamSpellHandlers`: LOW +2, HIGH −1), scales server-sent particles (Protego feedback, spell clash) and projectile trails |
| Tick intervals | the two beam intervals | Everything else (sync cadences, the debug panel's poll) is a code constant. No sync-interval setting was invented |
| Beam scanning | `WandBeamChannelLogic` channels, `BeamTargetClaims` | added `channelCount()`, `channels()`, `scanIntervalTicks()`, `effectIntervalTicks()` (read-only) |
| Spell effects | `SpellProjectileEntity`, `SpellClashEntity`, mod `MobEffect`s | counted on request |
| Cast sessions | `WandCastSessions` (server-held hold, release consumed once, clash hold) | added `openSessions()` (read-only view) |
| Cast telemetry | `SpellCastTelemetry` (per-player misfire ring), `PlayerSpellData` reject counts, `DebugHooks.logSpellCast` (every cast outcome), `Config.debugLogSpellGateReasons` | no server-wide recent view existed → `CastDiagnostics` |
| Debug mode | `DebugModeService` (per player + all players, memory), in-world inspector (`WorldDebugPanel`) | added set/has-own/set-global for leases |
| Debug commands | `/wandb debug` modules (player, spell, broom, vault, wand, elder_wand, reload), inspect, feature, dev | unchanged |
| Overlays | in-world inspector, `FormDebugOverlay`, `BeamDebugScreen`, `BeamQuality` (client), vanilla hitboxes (`Minecraft.debugEntries`, **saved to the options file**) | `WizardsAndBeastsMod.debugForceBeam` is written by `/wandb debug beam` and read nowhere — left alone, noted |
| Logging | SLF4J → Log4j2; every mod logger is named `at.koopro.wizardsandbeasts.*` | a level can be set on that one logger without touching the root |
| Network | vanilla `Connection.getAverageSentPackets/ReceivedPackets` (per second), `latency()` | bytes are not measured server-side without a bandwidth monitor, so none are shown |

## 2. Performance

### Live (`PerfLivePanel`, server: `admin/perf/PerformanceMetrics`)

| Shown | Source | Unavailable when |
|---|---|---|
| TPS | `min(target, 1000 / average tick ms)`; target from `tickRateManager()` | no tick timed yet |
| Tick time avg / max | `getTickTimesNanos()` (last 100) | same |
| Per dimension: entities, mod entities, spells in flight, loaded chunks, players, tick ms | `getAllEntities()`, `getLoadedChunksCount()`, NeoForge `getTickTime(dim)` | per-dimension times not kept |
| Beams, wand holds, spell entities, mod effects on players, intervals in effect | the systems above | — |
| Packets per second, average latency | vanilla per-connection figures, summed / averaged | nobody connected |
| This client: FPS, particles, entities | `Minecraft.getFps()`, `ParticleEngine.countParticles()`, `ClientLevel.getEntityCount()`, sampled once a second | before the first sample |

Collected only when an open page asks, once a second, and the server drops faster requests per player
(`AdminOpsPayloads.MIN_TICKS_BETWEEN` = 10 ticks): the entity walk is fine on request, wrong per tick.

### Presets (`PerfPresetsPanel`, `admin/perf/PerformancePresets`)

| Preset | perf_profile | scan interval | effect interval | in effect (scan / effect) |
|---|---|---|---|---|
| LOW | LOW | 4 | 8 | 6 / 10 |
| MEDIUM (shipped defaults) | MEDIUM | 2 | 5 | 2 / 5 |
| HIGH | HIGH | 1 | 3 | 1 / 2 |

A preset is only a set of values for existing settings. Applying one goes through `ProfileApplier` and the setting
service: all or nothing, authority checked per setting, one history group `perf_preset:<id>:<millis>` (revertible in
Profiles → History). CUSTOM is shown when the values match no preset. The dialog lists each "from → to".

### Classification

| Setting | Kinds |
|---|---|
| perf_profile | Server, Visual |
| beam_target_scan_interval_ticks | Server, Gameplay |
| beam_channel_effect_interval_ticks | Server, Gameplay |
| enable_debug_tools | Server, Gameplay |
| debug_log_spell_gate_reasons | Server |
| reduce_screen_effects | Client, Visual |
| broom_speed_particles | Client, Visual |
| show_spell_hud_overlay | Client, Visual |
| beam render budget (`BeamQuality`, Visuals → Debug; not a setting) | Client, Visual |

Requested but not offered because nothing supports it: sync intervals (code constants). The Settings tab shows the
classified settings, from whichever section they are filed under.

## 3. Debug

### Tools (`DebugToolsPanel`, server: `admin/debug/DebugLeases`)

| Tool | Scope | Lease ends → |
|---|---|---|
| My debug mode (in-world inspector, my spell events in the log) | the administrator | off |
| Debug mode for every player | server | the value before the first administrator changed it, once the last holder lets go |
| Spell logging for every player (`DebugHooks.logSpellCast` at INFO) | server | same |
| This mod's log level OFF / ERRORS / WARNINGS / INFO / DEBUG (`ModLogLevel`) | the mod's logger only | the exact previous configuration, including removing the logger entry if there was none |
| Hitboxes (vanilla overlay) | this client | the previous status, written back to the options file |

A lease ends when the Control Center closes (`AdminControlCenterScreen.removed()` → `LeaseRequest(release=true)` and
the hitbox restore), on logout, on server stop (`releaseAll`, which matters for the log level: Log4j state survives an
integrated server restart in the same JVM), or after 5 minutes without the open Control Center renewing it (heartbeat
every 60 s). Turning a tool off needs no lease. Debug mode switched on with `/wandb debug toggle` is not the panel's
and is not reverted. The console cannot hold leases (`panel_only`) — it uses the commands.

**Logging is never global.** `ModLogLevel` changes only the `at.koopro.wizardsandbeasts` logger (additive, parented to
the inherited config so output reaches the same appenders). The root logger, Minecraft's and every other mod's loggers
are untouched. Where the lines end up is the server's logging configuration (on NeoForge, DEBUG lines go to
`logs/debug.log`).

### Live (`DebugLivePanel`, server: `admin/debug/LiveDiagnostics`)

Beams being channelled (player, spell, ticks, locked on); open wand holds (session id, spell, age, release consumed,
vanilla release seen, clash hold) — the state the release is matched against; the last 32 cast events and counts by
outcome since start (`CastDiagnostics`, a 64-entry ring fed by `DebugHooks.logSpellCast`, the one hook every cast
outcome passes; counts capped at 64 kinds); this mod's entities by type; every module's state; each connection's
latency and packet rates; which administrator holds which tool. Reading it renews the reader's leases.

### Settings

The persistent debug settings (`enable_debug_tools`, `debug_log_spell_gate_reasons`). These are not leased; the page
says so.

## 4. Cost

- Nothing is collected per tick or per frame. Server reads happen on request (≤ 1/s per admin, throttled server-side);
  client samples once a second; documents are laid out when an answer arrives.
- `CastDiagnostics` costs one small record per cast into a fixed ring.
- The lease expiry check runs every 100 ticks over a map with one entry per administrator holding a tool.

## 5. Tests

| Test | Proves |
|---|---|
| `OpsContractTest` (JUnit, 6) | metrics/diagnostics/lease state round-trip (incl. unavailable values); invented tool/preset ordinals refused; presets name only catalog settings within range, MEDIUM = defaults; classified settings exist; the cast ring is bounded; lang for presets, kinds, levels, refusals |
| `admin_ops_metrics_are_measured` | three spawned mod entities are counted exactly; players, beams and sessions match the owning systems; TPS ≤ target, max ≥ avg |
| `admin_ops_presets_apply_the_settings` | LOW sets its three values (and intervals in effect 6/10); re-applying changes nothing; a hand edit reads CUSTOM; no authority → nothing |
| `admin_ops_debug_tools_are_leased` | refusals (no authority, console, bad value) change nothing; release, logout and expiry turn tools back; a shared tool waits for its last holder; switching off leaves no lease |
| `admin_ops_log_level_stays_in_the_mod` | DEBUG reaches the mod's loggers but not the root or Minecraft's; release restores the level and the logger entry exactly |
| `admin_ops_live_diagnostics_show_casts_and_sessions` | a cast refusal and an open hold appear; refusals are counted; every module listed; non-admins get no diagnostics or metrics |

Client capture `run/screenshots/ops` (13 shots): live metrics, LOW and MEDIUM applied through their dialogs, the tools
switched on, the live view, then the Control Center closed — the server log shows the leases released and the
capture log shows the hitbox overlay back as it was before the panel flipped it.
