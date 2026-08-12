# Spell Effect Components (Step 2 — vocabulary)

Composable, data-driven spell effect primitives. A spell's `effects` field is an ordered list of
components; each is one self-contained action (apply an effect, deal damage, ignite, …). The list is
applied in order through `SpellEffectComponent.apply(SpellEffectContext)`.

> **Status: wired.** The runner executes at the `self` cast site, the `projectile`/`cone`/`targeted`
> entity-impact sites (Steps 3/3.5/4), and — as of F2 — the BEAM_CHANNEL per-tick loop with per-entry
> `cadence` (see below). Migrated JSON spells carry `effects` lists; Java spells return an empty list
> and are untouched.

## Files

- `spell/effect/SpellEffectComponent.java` — sealed interface + 9 record variants, mirroring the
  `SkillNodeEffect` discriminated-union pattern (`Type` enum → per-variant `MapCodec`, dispatched by a
  `type` key). Also exposes `MAP_CODEC` (the map form of the dispatch) so the entry wrapper below can
  merge extra top-level fields without touching any variant codec.
- `spell/effect/SpellEffectEntry.java` (F2) — one top-level `effects` entry: a component + an optional
  `cadence` tag (default `tick`). The cadence field rides in the same flat JSON object as the
  component's fields, so pre-cadence JSON parses unchanged.
- `spell/effect/EffectCadence.java` (F2) — `start` / `tick` / `end`.
- `spell/effect/SpellEffectContext.java` — the application envelope (caster / optional target / level /
  position). `subject()` = impact target if present, else caster.
- `spell/def/SpellDefinition.java` — adds the optional, defaulted `effects: List<SpellEffectEntry>`
  field (absent → empty; existing JSON spells deserialize unchanged).

## The 12 components

| `type` | Fields | Subject | Notes |
|---|---|---|---|
| `apply_effect` | `effect` (id), `duration`, `amplifier`=0, `target`=false, `darkArts`=false | caster, or target if `target:true` | `darkArts:true` gates on `DARK_ARTS` instead of `WANDS_AND_SPELLS` |
| `damage` | `amount`, `damageType`? (id) | impact target only | absent `damageType` → magic damage |
| `ignite` | `seconds` | subject | |
| `impulse` | `strength`, `direction`=`push` (`push`/`pull`/`up`) | subject | push = away from caster, pull = toward, up = lift |
| `heal` | `amount` | subject | |
| `dispel` | `scope`=`all` (`all`/`mod_namespaced`/`specific`), `effects`? (ids, for `specific`), `respect_immunity`=true | subject | `mod_namespaced` = only this mod's effects (Finite Incantatem); `respect_immunity` honors `FiniteImmuneEffects` |
| `clear_fire` | — | subject | extinguishes; compose resistances via separate `apply_effect`s |
| `light` | `place`=true | position | place/remove a vanilla light block |
| `repair` | `durability` | caster's held item | offhand-first, else main hand |
| `explosion` | `radius`, `fire`=false, `breakBlocks`=false | position | |
| `aoe_apply` | `radius`, `effects` (nested component list) | each living in range (excl. caster) | runs the nested list per entity, preserving scaling |
| `swap_active_spell` | `spell` (bare id), `learn`=false | caster's spell data | swaps the active loadout slot to `spell` (learning it first when `learn:true`) and syncs to client — paired toggles (lumos↔nox) |

Every component no-ops unless `Module.WANDS_AND_SPELLS` is enabled (dark `apply_effect` requires
`DARK_ARTS`), matching the existing `Spell.canApplyEffect` gate.

### Scaling (F1)

`damage`, `apply_effect`, and `impulse` scale by the cast's multipliers carried on
`SpellEffectContext` — `damageMult`, `durationMult`, `controlMult` (sourced from the
`SpellScalingProfile` / modifier stack at each invocation site; default `1.0`). So a proficient/
wand-buffed cast reproduces the legacy scaled values:
- `apply_effect` → `duration × durationMult` (negative/infinite durations pass through unscaled).
- `impulse` → `strength × controlMult`.
- `damage` → `amount × damageMult`.

**Damage policy (F5):** a spell's *primary* projectile/melee damage stays on the legacy scaled
`baseDamage` field (which also folds in skill + wand via `Spell.getDamageForCaster`). The `damage`
component is for *adjunct/secondary* damage and scales by `damageMult` only — do not express primary
damage with it.

### Cadence (F2) — beam channels

Every top-level entry takes an optional `cadence` tag, default `tick`:

```json
{ "type": "apply_effect", "effect": "wizards_and_beasts:cruciatus_pain",
  "duration": 60, "target": true, "darkArts": true, "cadence": "tick" }
```

- **Only BEAM_CHANNEL honors cadence.** `WandBeamChannelLogic` runs the entry list per phase:
  `start` once on the first channel tick, `tick` on the existing channel-effect interval
  (`Config.beamChannelEffectIntervalTicks`, perf-profile adjusted — no new interval), `end` once on
  release/interruption/spell-switch. For `self` / `projectile` / `cone` / `targeted` casts the cadence
  is **inert**: those sites run the whole list once at their existing point, exactly as before.
- `tick` entries fire only while the beam holds a **living target** (subject = beam target), matching
  the legacy per-tick channel handlers. `start`/`end` fall back to the caster as subject when no
  target is held; `end` reuses the session's cached target when it still resolves.
- Scaling multipliers are re-resolved from the live `ProficiencyScaler` profile on every channel
  invocation, so per-tick damage/durations track proficiency like cast-time invocations do.
- **Top-level only:** nested `aoe_apply` children are plain components and fire when their parent
  fires.
- Channeled `apply_effect`s should use a short refresh duration ≥ the worst-case channel interval
  (22 ticks at the LOW perf profile) so the effect persists while channeling and lapses right after
  the beam stops — crucio uses `duration: 60` (36 at the 0.6 durationMult floor).

### Hybrid props (Step 5) — when a behavior is a prop, not a component

`SpellDefinition` also carries behavior **props** that route a spell into the cast pipeline's
existing (id-aware) handlers rather than the component runner: `disarms`, `opensBlocks`,
`pullStrength` (plus the step-4 set: `igniteSeconds`, `explode`, `knockback`, `targetEffects`).
Use a prop when the behavior fires where the runner has no hook (projectile *block* hits, targeted
*block* raycasts, beam channels, early-return handlers); use a component when the runner's
SELF / entity-impact sites cover it. The proposed `pull` / `disarm` / `block_state` / `place_fluid`
components were **deliberately not built** — each would have been dead code at its spell's actual
invocation point (see `WORKLOG.md`, "Step 5 — the 5 hybrids").

## Authoring example

A pack author composes a spell's behavior as a list under `effects`. Example — a defensive "warming
charm" that heals a little, clears poison/wither, and grants fire resistance to the caster:

```json
{
  "displayName": "Warming Charm",
  "category": "defense",
  "cooldownTicks": 120,
  "color": -1118482,
  "castType": "self",
  "sound": { "id": "minecraft:entity.allay.hurt", "volume": 0.4, "pitch": 1.3 },
  "effects": [
    { "type": "heal", "amount": 4.0 },
    { "type": "dispel", "effects": ["minecraft:poison", "minecraft:wither"] },
    { "type": "apply_effect", "effect": "minecraft:fire_resistance", "duration": 220 }
  ]
}
```

A combat example — fire bolt that damages, ignites the target, and explodes (no block damage):

```json
{
  "displayName": "Fire Bolt",
  "category": "combat",
  "cooldownTicks": 80,
  "baseDamage": 0.0,
  "color": -49152,
  "castType": "projectile",
  "effects": [
    { "type": "damage", "amount": 6.0 },
    { "type": "ignite", "seconds": 4 },
    { "type": "explosion", "radius": 2.0, "fire": true, "breakBlocks": false }
  ]
}
```

(The combat example will only take effect once impact-site wiring is approved; `damage`/`ignite`/
`explosion` apply at the projectile's hit point.)
