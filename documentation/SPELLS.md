# Spells

## 1. Foundation re-spec (why the 17 stalled) — historical preamble

> **Source:** Spell Migration Foundation Re-Spec (Step 4 → blocked, pre-work required), 2026-06-11  
> **Status:** This document preserves the planning artifact that explained *why* Step 4 migration was halted. The actual execution lives in the delta logs below.  
> **Decision:** Step 4 (migrate the remaining 17 fully-data spells) is **on hold**. A full audit shows the step-2 component vocabulary + step-3 runner cannot reproduce these spells' **functional** behavior yet. Per the step-4 §5 rule ("functional behavior must be preserved, or you STOP"), the responsible move is to build foundation first, then migrate. This doc is that foundation spec + the per-spell audit so the migration step can be re-issued precisely.

### Why the 17 can't migrate as the step-4 mapping prescribes

#### Blocker A — components are scaling-blind (functional)
The components apply **flat** values; the legacy apply paths scale by proficiency / wand / skill:

| Behavior | Legacy (scaled) | Component (flat) |
|----------|-----------------|------------------|
| Damage | `baseDamage × skill × wand × proficiency × scalingProfile.damageMult()` ([Spell.getDamageForCaster:124](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spell.java#L124), [SpellProjectileEntity:91](src/main/java/at/koopro/wizardsandbeasts/entity/spell/SpellProjectileEntity.java#L91)) | `damage` → `target.hurt(magic, amount)` |
| Effect duration | `duration × scalingProfile.durationMult()` (`applyTargetEffects`/`applySelfEffects`) | `apply_effect` → fixed `duration` |
| Knockback | `baseKnockback × scalingProfile.controlMult()` | `impulse` → flat `strength` |

`PROFICIENCY` is `PREVIEW` and `SKILL_TREES` is `ENABLED` (live), so the scaling is real gameplay. Mapping damage/effects/knockback onto flat components = a stealth nerf. **Affects:** incendio, diffindo, flipendo, glacius, depulso, wingardium, crucio, frigora, levicorpus, arresto.  
(Note: this delta also silently applies to **stupefy** as shipped in step 3 — its `STUPEFY` effect duration no longer scales. Small at amplifier 0, but it's the same class of bug; fold the fix in here.)

#### Blocker B — runner not wired to BEAM_CHANNEL
`crucio` (`beamChannel(50)`) and `wingardium_leviosa` (`beamChannel(16)`) apply effects **per-tick in `WandBeamChannelLogic`**, not at cast/impact. The step-3 runner wires SELF + projectile/cone/targeted only. `apply_effect` at cast can't reproduce a channel. `crucio` additionally matches step-4 §5's "if it's a channel, STOP, leave in Java."

#### Blocker C — multi-part / mechanism-mismatch spells
Single-component mapping sheds functional parts:

| Spell | Mapping | Reality (functional gap) |
|-------|---------|--------------------------|
| `nox` | light(remove) | removes the **LUMOS_FIELD mob effect** + loadout-swap, not a light *block*. `light` component is wrong mechanism. Also: **nox obtainability** — step 3 deleted lumos→nox auto-learn; needs a restored learn path. |
| `episkey` | heal | heal `min(4,missing)` + cleanse {poison,wither,blindness,nausea} + **conditional** REGEN (120 vs 80 ticks) ([SpellCastUtilityHandler:34](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastUtilityHandler.java#L34)) |
| `frigora` | apply_effect | clearFire + FIRE_RESISTANCE(220) + WATER_BREATHING(120) + Glacius block interaction |
| `finite_incantatem` | dispel(all) | removes only **mod-namespaced, non-immune** effects ([handler:193](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastTargetedHandler.java#L193)); `dispel(all)` would strip vanilla buffs = behavior change |
| `levicorpus` | apply_effect | LEVITATION(90,1) + SLOWNESS(60,2) + fallDistance reset + `setTarget(null)` |
| `arresto_momentum` | apply_effect | self SLOW_FALLING(220) **+ `applyArrestoAreaStabilize` AoE** (dampens nearby entities) — AoE is functional |

## 2. Foundation work F1–F5 (now all DONE — corrected status)

**F1 — Scaling-aware effect application.** Extend `SpellEffectContext` to carry the cast's scaling (`damageMult`, `durationMult`, `controlMult`, and the finalized `ModifierStack` output) sourced from the `CastContext`/`SpellScalingProfile` already built in `SpellCastService`. Update `damage`, `apply_effect`, and `impulse` `apply()` to multiply by the relevant factor. This is the single most important fix — without it every combat/effect migration is a nerf. *(Modifies step-2 components + step-3 context; that's the point of doing it as foundation, not mid-migration.)*

**F2 — Beam-channel runner wiring.** Add a per-tick invocation hook in `WandBeamChannelLogic` that runs a beam spell's `effects` against each ticked target (subject = beam target). Required for `crucio` and `wingardium_leviosa`. Decide tick cadence to match current beam damage/effect cadence.

**F3 — Components for the multi-part gaps** (v1.5; step-4 forbade new components, so they belong to the foundation step):
- `cleanse` / extend `dispel` with a namespace filter + immune-list awareness → covers `finite`, `episkey` cleanse.
- conditional / scaled `heal` (heal up-to-missing, optional follow-up effect) → `episkey`.
- `clear_fire` (or a flag on an effect component) → `frigora`.
- `aoe` wrapper (apply a component list to entities in a radius) → `arresto` stabilize, and future AoE spells.
- a `learn_spell` / `set_loadout` convenience (or accept these as bespoke Java) → `nox` toggle, lumos↔nox. **Decide:** are toggles in-scope for components at all, or do they stay Java hooks?

**F4 — Residual id-keyed tails policy.** Several block-interaction tails are id-keyed and already survive a Java→JSON swap (they key on spell id via `SpellIds.matches`, not the Java class): diffindo block-cut, depulso/flipendo push, confringo block-impact, bombarda push, stupefy block pulse, glacius block interaction. **Policy:** keep these as logged residual tails (they work for JSON ids) until components exist; document each in `WORKLOG.md`.

**F5 — Damage policy decision.** Two ways to keep scaled damage: (a) leave damage in the scaled `baseDamage` props field and use components only for non-damage behavior, or (b) make the `damage` component scaling-aware (F1) and zero `baseDamage`. Pick one and apply uniformly. (a) is lower-risk and means combat spells barely use the `damage` component; (b) is "more data-driven" but rides on F1.

### Status update (2026-07-13)
- **F1, F3, F4, F5 — DONE** (Step 3.5 "Component Parity Pass"). Components are scaling-aware; `dispel` is parameterized (scope + immunity); `clear_fire` and `aoe_apply` added. No spell migrated. Also fixes the shipped-stupefy duration-scaling delta (its `apply_effect` now scales).
- **F2 (BEAM_CHANNEL wiring) — STILL OPEN.** `crucio` + `wingardium_leviosa` remain Java.
- **Revised Step 4** (actual migration) now unblocked for everything except the two beam spells.

## 3. Step 4 deltas (Combat → scaled props; Utility → components; kept in Java)

> **Source:** Spell Migration Deltas (Step 4 — the 15 non-beam fully-data spells), 2026-06-13  
> **Governing decisions:**
> - **Primary damage stays on the scaled `baseDamage` props field** (load-bearing policy) — folds in skill+wand+proficiency. The `damage` component (flat/adjunct) is used by **none** of the 15.
> - **Projectile/cone combat spells migrate via existing scaled props** (`baseDamage`/`igniteSeconds`/`explode`/`knockback`/`targetEffects`) rather than components, because (a) the runner is not wired to projectile **block** hits (`onHitBlock`), and (b) props preserve exact knockback direction/force and block behavior. This is more faithful than the prescribed component for these spells.
> - **Components drive** the self/utility spells where the runner site matches and props can't express the behavior.
> - **Option (b)** (remove targeted early-returns): applied to **liberacorpus** only (lossless). `finite_incantatem` and `levicorpus` early-returns were **kept** — removing them changes behavior (see below), which §5 forbids.

### Combat — migrated via scaled props (no components; full fidelity)

| Spell | JSON props | Residual id-keyed tail (unchanged, fires by JSON id) | Shed |
|-------|------------|------------------------------------------------------|------|
| incendio | cone, baseDamage 3.0, igniteSeconds 5 | block-fire-along-look (kept — via `props.ignites()` in cone handler) | — none (igniteSeconds preserves both entity ignite **and** block fire; superior to the `ignite` component) |
| diffindo | projectile, baseDamage 6.0 | diffindo block-cut (`isDiffindo`) | — |
| bombarda | targeted, baseDamage 7.0, explode 2.5/no-break | bombarda block push+impact (`isBombarda`, inside `props.explodes()` branch) | — (used `explode` prop, not `explosion` component, to keep block-targeting + push tail) |
| confringo | projectile, baseDamage 6.0, igniteSeconds 3, explode 2.0/no-break | confringo block-impact (`isConfringo`) | — (props not components: runner isn't wired to `onHitBlock`) |
| flipendo | projectile, baseDamage 2.0, knockback 3.0 | block-miss item push (`isPushSpell`, force 2.4 preserved) | — (knockback prop not `impulse`: exact force + travel-direction) |
| depulso | projectile, baseDamage 1.0, knockback 5.0 | block-miss item push (`isPushSpell`, force 4.0) | — (prop not `impulse`) |
| glacius | cone, baseDamage 2.0, targetEffects [petrificus_totalus 40/0] | glacius block interaction (`isGlacius`) | — (targetEffects prop scaled by cone handler = identical to `apply_effect`; block interaction id-keyed) |

### Utility — migrated via components (+ residual tails where noted)

| Spell | Components (JSON) | Residual Java tail kept | Shed (cosmetic/convenience) |
|-------|-------------------|-------------------------|-----------------------------|
| nox | `dispel specific:[lumos_field]` + `swap_active_spell lumos` | — | cast particle burst. ~~lumos↔nox loadout-swap toggle + sync~~ **restored** via the `swap_active_spell` component (lumos: `swap_active_spell nox learn=true`; nox: `swap_active_spell lumos`). |
| reparo | *(none — see tail)* | `handleReparoSelf` (full-repair + wand-aware selection + ElderWand guard + block-repair) | block-repair range no longer wand-scaled (5.0 fixed vs `5.0 × wand.rangeFor`). The `repair` component is unused — it repairs by a fixed amount and can't express full-repair / wand-aware selection / ElderWand guard. |
| liberacorpus | `dispel specific:[levitation]` (early-return removed) | — | "fizzle" failure sound when target has no levitation; bespoke success-burst → generic beam. Functional core (remove levitation) preserved. |
| arresto_momentum | `apply_effect slow_falling 220` (self, scaled) | `applyArrestoAreaStabilize` (AoE velocity dampen — no component) | — (AoE preserved as residual) |
| episkey | `heal 4` + `dispel specific:[poison,wither,blindness,nausea]` | `handleEpiskeySelf` trimmed to **HP-conditional Regeneration** (120 vs 80; missing-HP read pre-heal — the SELF arm runs the tail before the runner) | — (regen exact; heal clamps identically to `min(4,missing)`) |
| frigora | `clear_fire` + `apply_effect fire_resistance 220` + `apply_effect water_breathing 120` | `handleFrigoraSelf` trimmed to Glacius block interaction | — |

### Kept in Java — NOT migrated (functional reasons; option-b exceptions)

| Spell | Why kept |
|-------|----------|
| finite_incantatem | Targeted early-return **kept**. finite cleanses the **caster** when not aiming at an entity; the generic targeted path applies effects only to a looked-at entity (runner isn't wired to a no-target cast), so removing the handler would break self-cast cleanse — functional, §5. Its `dispel scope:mod_namespaced` logic is exactly what `handleFiniteIncantatem` already does. |
| levicorpus | Targeted early-return **kept**. Its LEVITATION(90/1)+SLOWNESS(60/2) were applied **raw/unscaled** (direct `addEffect`); an `apply_effect` component scales by `durationMult`, which would add proficiency duration-scaling the original lacked — a behavior change, §3/§5. Also keeps fall-distance reset + `setTarget(null)`. |

### State after this run
- **11 Java spell classes deleted** (incendio, diffindo, bombarda, confringo, flipendo, glacius, depulso, nox, reparo, liberacorpus, arresto_momentum). 4 JSON spells gained `effects`.
- `SELF_UTILITY_RULES` now holds only: **bespoke** (protego, capacious_extremis, claustra_reverto, riddikulus) + **residual tails** (reparo, arresto_momentum, episkey, frigora).
- `Crucio` requirement repointed `Spells.INCENDIO` → `"incendio"` (id-string). Tests repointed `Spells.FLIPENDO` → `Spells.EXPELLIARMUS` (FLIPENDO removed).
- **Still Java / deferred:** crucio + wingardium_leviosa (BEAM — F2), the 5 hybrids, the bespoke set.
- `SpellCastSupport.isLiberacorpus` is now unused (left in place; harmless).

## 4. Step 5 — the 5 hybrids

> **Source:** Spell Migration Deltas (Step 5 — the 5 hybrids), 2026-06-13  
> **Governing decision (reported per the step-5 prompt's own rule):** **zero new components were built.** The four proposed (`pull`, `disarm`, `block_state`, `place_fluid`) would all have been dead code at their spells' actual invocation points:
> - `pull` — accio's cone handler (`handleAccio`) early-returns before the runner; its smooth-lerp / LOS / item-priority pull is unexpressible by a one-shot impulse. (3.5's `impulse` already has a `PULL` direction, so a `pull` component would also have been redundant vocabulary.)
> - `disarm` — `disarms` already exists as a `SpellDefinition` prop; the on-hit disarm path is props-driven + id-keyed (`ExpelliarmusDisarmHandler` + proficiency scalar).
> - `block_state` — alohomora/colloportus cast at *blocks*; the targeted handler only invokes the runner for a living target, so the component would never fire on a door.
> - `place_fluid` — aguamenti is **BEAM_CHANNEL**; all behavior is per-tick in the beam handlers and the runner has no beam wiring (F2, still open).

Faithful mechanism instead: **two new `SpellDefinition` prop fields** — `opensBlocks` (bool) and `pullStrength` (float) — mapped in `JsonSpell.buildProperties` onto the existing `SpellProperties.opensBlocks()` / `pullsTarget()` builders, with the rich behavior staying in the id-keyed handlers (all of which match JSON ids via `SpellIds.matches`).

| Spell | JSON | Residual id-keyed Java tail (unchanged) | Shed |
|-------|------|-----------------------------------------|------|
| expelliarmus | projectile, disarms, speed 1.6 / spread 0.02, req knows stupefy | on-hit disarm (`ExpelliarmusDisarmHandler` + proficiency scalar + impact sound), block-miss item push (`isPushSpell`), projectile clash (`trySpellClash`), drop-tag pickup handler | Java `getBaseKnockback()=1.2` was **dead** (props knockback was 0, so the on-hit gate never fired) — not carried into JSON; no observable change. Default generic cast sound preserved (no `sound` field). |
| accio | cone 16.0, pullStrength 2.0, req knows wingardium_leviosa | `handleAccio` (smooth-lerp pull, LOS clip, item-priority sort, fall-reset) reads `props.getPullStrength()` — now sourced from JSON | — |
| alohomora | targeted 5.0, opensBlocks | `handleAlohomora` (lock tiers, proficiency gating, Colloportus-lock respect, messages) | — |
| colloportus | targeted 5.0, opensBlocks, req knows alohomora | `handleColloportus` (`ColloportusLockStore`, messages) | — |
| aguamenti | beam_channel 12.0, req knows lumos | **entire behavior**: per-tick beam logic (`WandBeamSpellIds.isAguamenti` → soak/fill/place-after-hold in `WandBeamSpellHandlers`/`AguamentiHelper`) | — (registration-only migration; behavior follows the id. Components can't reach it until F2 beam wiring.) |

`Protego` requirement repointed `Spells.EXPELLIARMUS` → `"expelliarmus"`. Tests repointed `Spells.EXPELLIARMUS` → `Spells.PROTEGO`. No `SELF_UTILITY_RULES` entries existed for the 5 (none are SELF spells).

### State after Step 5
Java spell registrations = `WINGARDIUM_LEVIOSA`, `RIDDIKULUS` (bespoke), `PROTEGO`, `EXPECTO_PATRONUM`, `AVADA_KEDAVRA`, `CRUCIO`, `IMPERIO`, `OBSCURUS_SURGE`, `OBSCURUS_GRASP` — i.e. only the bespoke set + the two beam-pending spells (crucio, wingardium_leviosa). 18 spells now ship as JSON.

## 5. F2 — Beam-channel runner wiring + cadence; crucio & wingardium_leviosa

> **Source:** Spell Migration Deltas (F2 — Beam-channel runner wiring + cadence; crucio & wingardium_leviosa), 2026-06-13  
> **Mechanism.** Per-entry `cadence` tag `{start, tick, end}`, default `tick`, added as a wrapper (`SpellEffectEntry` = component + cadence, merged into the same flat JSON object via the dispatch's `MAP_CODEC`) — **no component variant was rewritten** and pre-cadence JSON parses unchanged. `WandBeamChannelLogic` now runs a channel spell's entries: `start` once on the first channel tick, `tick` on the **existing** `Config.beamChannelEffectIntervalTicks` interval (only while the beam holds a living target; subject = beam target), `end` once on release/interruption/spell-switch (cached-target fallback caster). Scaling multipliers re-resolved from the live `ProficiencyScaler` profile each invocation. Cadence is **inert** outside BEAM_CHANNEL — the non-channel sites still run the full list once via the phase-less overload (BEAM_LETHAL/avada never reaches the wiring), so no already-migrated spell's behavior changes. Nested `aoe_apply` children stay plain components.

| Spell | JSON | Residual id-keyed Java tail (unchanged) | Shed / deltas |
|-------|------|-----------------------------------------|---------------|
| crucio | beam_channel 50.0, cooldown 220, baseDamage 2.0, req proficiency incendio:mastered, effects `[apply_effect cruciatus_pain 60 target darkArts cadence:tick]` | `handleCrucioChannel` minus the pain apply: intent feedback payload, corruption accrual (5×intent per interval), WITHER/SLOWNESS cleanup, **ramp damage** (≥40 ticks, every 20: `min(1.5, 0.4+ticks/120)×intent` — time-ramp inexpressible), proficiency hits, pain-strip on end/target-switch (`clearSessionEffects` — pain still lapses immediately at beam stop) | (1) pain refresh no longer intent-scaled: fixed channel interval (5 default, ≤22 LOW) vs `interval/max(0.5,intent)`; duration `60×durationMult` (≥36) vs `max(20, 60/intent)` — invisible in practice (refresh ≪ duration; immediate strip at end unchanged). (2) cast-time "That power is sealed away" message + fizzle sound when DARK_ARTS disabled (Java `executeCast` override) — shed; channel still no-ops (component `darkArts` gate + tail module gate). (3) props targetEffects weakness/nausea/slowness 40t were **dead** (no dispatch path applies targetEffects for BEAM_CHANNEL) — not carried into JSON; no observable change. |
| wingardium_leviosa | beam_channel 16.0, cooldown 60, projectileSpeed 0.0, req knows lumos, **effects empty** | **entire lift behavior**: `WandBeamSpellIds.isLeviosa` → `handleLeviosaChannel` direct spring-motion (hold-distance scroll adjust, grace-miss ticks, no-gravity save/restore) — direct motion manipulation, no component expresses it (registration-only migration, like aguamenti) | — |

### Supporting changes:
- `ObscurusSurge`/`AvadaKedavra` requirements repointed `Spells.CRUCIO` → `"crucio"` (id-string).
- `JsonSpell.buildRequirement` no longer degrades to NONE when a prerequisite isn't registered yet: it resolves eagerly when present (unchanged) and otherwise falls back to the mod-namespaced id string, enforced lazily at `isMet` time. Needed because JSON spells init one-by-one during the reload sweep and crucio's prerequisite (incendio) is itself JSON — load order is arbitrary.
- `Crucio.java` + `WingardiumLeviosa.java` deleted. `WandBeamChannelLogicTest`'s session tests still use the `"crucio"` id string — unaffected.

### State after F2
Java spell registrations = `RIDDIKULUS`, `PROTEGO`, `EXPECTO_PATRONUM`, `AVADA_KEDAVRA`, `IMPERIO`, `OBSCURUS_SURGE`, `OBSCURUS_GRASP` — the bespoke set only. 20 spells now ship as JSON. `SELF_UTILITY_RULES` unchanged (neither beam spell is SELF).

## 6. Per-spell migration plan (once F1–F5 land)

> **Source:** Spell Migration Foundation Re-Spec (Step 4 → blocked, pre-work required), §6  
> **Exact current numbers captured for the re-issue.** CT = cast type. "Tail" = residual id-keyed Java hook.

| Spell | CT | cd | Components (post-foundation) | Scaled? | Tail / notes |
|-------|----|----|------------------------------|---------|--------------|
| incendio | cone | 80 | damage 3.0 + ignite 5 | dmg | block-ignite-along-look (props.ignites today); decide keep |
| diffindo | proj | 50 | damage 6.0 | dmg | diffindo block-cut tail |
| bombarda | targeted | 140 | (baseDamage 7.0 scaled) + explosion 2.5/no-break | dmg via props | bombarda push+impact tail |
| confringo | proj | 80 | explosion 2.0/no-break + ignite 3 | none | confringo block-impact tail |
| flipendo | proj | 38 | damage 2.0 + impulse 3.0 push | dmg+kb | push tail |
| glacius | cone | 70 | damage 2.0 + apply_effect petrificus_totalus 40/0 | dmg+dur | glacius block interaction tail |
| depulso | proj | 40 | damage 1.0 + impulse 5.0 push | dmg+kb | push tail |
| nox | self | 20 | dispel lumos_field (+ loadout-swap?) | — | **obtainability**; toggle = F3 decision |
| reparo | self | 80 | repair 100 | — | block-repair raycast tail |
| wingardium_leviosa | **beam** | 60 | apply_effect levitation (per-tick) | dur | needs F2 |
| liberacorpus | targeted | 35 | dispel levitation (conditional) | — | success only if had levitation |
| arresto_momentum | self | 80 | apply_effect slow_falling 220 + aoe stabilize | dur | AoE needs F3 |
| crucio | **beam** | 220 | apply_effect weakness40/1 + nausea40/0 + slowness40/1, darkArts | dur | needs F2; §5 channel |
| episkey | self | 140 | heal 4 (≤missing) + cleanse{poison,wither,blindness,nausea} + apply_effect regen (120/80 conditional) | — | needs F3 heal+cleanse |
| finite_incantatem | targeted | 40 | dispel (mod-namespaced, non-immune) | — | needs F3 filtered dispel |
| frigora | self | 80 | clear_fire + apply_effect fire_resistance 220 + water_breathing 120 | dur | + block interaction tail; needs F3 |
| levicorpus | targeted | 100 | apply_effect levitation 90/1 + slowness 60/2 (+ fallreset, setTarget) | dur | fallreset/setTarget bespoke |

### Clean enough to migrate first (smallest foundation dependency)
`bombarda`, `confringo`, `reparo` — functional parity with only residual tails, no scaling dependency for their mapped components (F5(a): keep bombarda's damage in `baseDamage`). Everything else waits on F1 (scaling), F2 (beam), or F3 (multi-part components).

### Suggested ordering:
F1 → re-migrate stupefy effect to scaled apply_effect + migrate the scaling-dependent combat spells → F2 → crucio/wingardium → F3 → episkey/frigora/finite/arresto/nox.

## 7. See also: SPELL_EFFECT_COMPONENTS.md (canonical 12-component vocabulary)

The current, wired component vocabulary lives in `SPELL_EFFECT_COMPONENTS.md` (Step 2 — vocabulary). It defines the 12 reusable, data-driven spell effect primitives that power the component-driven migrations above.

**Components:** `apply_effect`, `damage`, `ignite`, `impulse`, `heal`, `dispel`, `clear_fire`, `light`, `repair`, `explosion`, `aoe_apply`, `swap_active_spell`  
**Key mechanics:** scaling (F1) via `SpellEffectContext`, cadence (F2) for beam channels, hybrid props (Step 5) for behavior that fires where the runner has no hook.

## 8. VFX / SFX plug points (per-spell look and sound)

Where a spell's presentation comes from. All four are data-driven already — a new spell that wants
its own look and sound almost never needs Java, and checking here first is cheaper than discovering
it after writing a renderer.

| What | Author it in | Read by |
|---|---|---|
| **Cast sound** | `"sound": { "id", "volume", "pitch" }` in the spell JSON (`SpellDefinition.SoundDef`). Java spells: `SpellProperties.Builder.sound(...)` | `Spell.playSound`, called from `SpellExecutor.dispatchGeneric` |
| **Trail particles** | `spellFamily` + `color` in the spell JSON | `SpellProjectileEntity.tick`, tinting `ModParticles.tinted(family, argb)` |
| **Impact burst** | the same `spellFamily` + `color` | server: `SpellImpactBurstS2CPayload.sendToTracking(...)`; client: `SpellVfxClient.spawnTintBurst` |
| **Beam look** | `BeamStyles` / `BeamSettings` under `client.wand` | `client.beam` — the only beam renderer |

`SpellFamilies.of(spell)` resolves the family: JSON spells return their declared `spellFamily`
(defaulting to `ARCANE`), and the six bespoke Java spells fall through a hardcoded id table. If a
spell renders in the wrong colour, that table is the first place to look.

### Adding a genuinely bespoke effect

1. Put the client code in `client.spell.SpellVfxClient` (or `client.beam`), **not** at the payload
   handler. `SpellClientPayloadHandlers` is loaded *server*-side when the payload registrar resolves
   its method references, so a client-only type in its signatures fails verification on a dedicated
   server. Call a static method on `SpellVfxClient` from a handler lambda instead — this is why
   `playDeniedFeedback` and `spawnTintBurst` are shaped the way they are.
2. If it needs a new packet, put a bounded payload in `network/spell` and register it in
   `ModNetworkSpells`.
3. Keep the decision on the server. The client is told what happened; it never decides that anything
   did. Sending a *code* rather than a rendered sentence is the same rule applied to text —
   `SpellDeniedS2CPayload` carries a reject code and `ClientSpellRejectFeedback` decides what the
   player reads and where.

### Cast-failure feedback

Two different things, on purpose:

- **Reject** — the cast was refused before it happened. Routed through `SpellCastService.debugReject`,
  which ticks the reject counter and sends `SpellDeniedS2CPayload` with the code. The client resolves
  the code to a lang key via `SpellRejectCodes.castRejectMessageKey` and draws it on the spell HUD, or
  the action bar when the HUD is hidden — one channel, never both. Codes whose text is composed from
  live state (`REQUIREMENTS_UNMET`, `GAMP_HARD_REJECT`) resolve to `null` and the site's own richer
  sentence stands alone.
- **Misfire** — the cast was legal and the wand failed anyway (mental instability, obscurus
  backlash, the modifier-driven misfire chance). No reject code, no denial packet: there is no reason
  to report. It plays `SPELL_FIZZLE` and says so.
