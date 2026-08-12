# Design Notes

> **STATUS: Historical snapshot 2026-05-19.** Self-described as "Not the canonical source for current architecture/runtime flow." For current spell tuning, see `SPELLS.md` + `SPELL_EFFECT_COMPONENTS.md`. For current creature data, see `CREATURES.md`.

## Spell Baseline (Pre-Overhaul Snapshot)

Historical snapshot before full overhaul tuning pass. Not the canonical source for current architecture/runtime flow.

### Java Spells

| Spell ID | Category | Cast Type | Cooldown (ticks) | Core Function | Outlier Notes |
|---|---|---|---:|---|---|
| `flipendo` | combat | projectile | 38 | Low damage projectile with knockback | Cooldown nudged up from 30 to trim spam |
| `stupefy` | combat | projectile | 40 | Mid damage stun/debuff projectile | Slowness tuned to amp 1 / 40 ticks |
| `expelliarmus` | combat | projectile | 60 | Disarm projectile | Pure utility in combat slot |
| `incendio` | combat | cone | 80 | Cone damage + ignite | Stable baseline |
| `diffindo` | combat | projectile | 50 | High direct projectile damage | Strong for cooldown |
| `bombarda` | combat | targeted | 100 | Targeted explosion + direct damage | Block-breaking explosion is high-impact |
| `confringo` | combat | projectile | 80 | Projectile damage + ignite + explosion | High burst chain potential |
| `glacius` | combat | cone | 60 | Cone slow + light damage | Stable baseline |
| `depulso` | combat | projectile | 40 | Strong knockback projectile | Low damage but strong control |
| `lumos` | utility | self | 20 | Self vision/light buffs | Self-focused; no area fire-sweep side behavior |
| `nox` | utility | self | 20 | Removes Lumos buffs | Caster-scoped cancellation for canon consistency |
| `accio` | utility | cone | 40 | Pulls entities toward caster | Strong positional control |
| `reparo` | utility | self | 80 | Repairs other held item | Stable baseline |
| `wingardium_leviosa` | utility | targeted | 60 | Target levitation | Stable baseline |
| `alohomora` | utility | targeted | 24 | Opens doors/trapdoors/fence gates | Very spammable utility cast |
| `arresto_momentum` | utility | self | 60 | Slow-falling utility | Stable baseline |
| `protego` | defense | self | 100 | Defensive resistance + absorption | Stable baseline |
| `expecto_patronum` | defense | cone | 170 | Defensive cone + anti-dark repel + aura | Repel/protection-first identity; direct damage de-emphasized for non-dark targets |
| `crucio` | dark_arts | beam_channel | 220 | Held damage/control channel | Target effects reapply every 5 ticks in `WandBeamChannelLogic` (was 2) |
| `imperio` | dark_arts | targeted | 260 | Mob control/debuff package | Mob control duration 150 ticks |
| `avada_kedavra` | dark_arts | beam_lethal | 1200 | Lethal channel spell | Extreme lethality by design |

### JSON Spells

| Spell ID | Category | Cast Type | Cooldown (ticks) | Core Function | Outlier Notes |
|---|---|---|---:|---|---|
| `wizards_and_beasts:levicorpus` | utility | targeted | 100 | Target hoist control (levitation-focused) | Uses bespoke targeted handler; JSON effects kept minimal |
| `wizards_and_beasts:frigora` | utility | self | 80 | Cooling/survival utility with nearby freeze interaction | Uses bespoke self handler; JSON effects kept minimal |
| `wizards_and_beasts:episkey` | defense | self | 140 | Minor-injury restoration + cleanse | Uses bespoke self handler; JSON effects kept minimal |

### Correctness Notes

- Beam spell cast behavior must remain gated through `WandBeamChannelLogic` to avoid release-path bypasses.
- High-impact spells (`bombarda`, `crucio`, `imperio`) may need further fairness passes.

### Lore Authenticity Audit (alpha.1 follow-up)

| Spell | Previous placeholder tendency | Current status |
|---|---|---|
| `lumos` | Applied nearby-player glowing in addition to self utility. | Improved — self-only light utility. |
| `expecto_patronum` | Used target `glowing` effect as generic marker instead of Patronus repel/aura. | Improved — aura + repel as primary identity. |
| `episkey` (JSON) | Relied on generic instant-heal/regeneration pairing. | Improved — restorative/cleanse via bespoke Java handler. |
| `frigora` (JSON) | Relied on generic survivability effects only. | Improved — cooling utility and local freeze via bespoke Java handler. |
| `levicorpus` (JSON) | Relied on generic levitation/slowness pairing. | Improved — dedicated target-hoist via bespoke Java handler. |

---

## Lore Accuracy Matrix

Tracks lore-accuracy status and flags intentional gameplay deviations.

### Acceptance Criteria (current pass)

- `episkey`, `frigora`, and `levicorpus` must retain datapack-driven definitions while reducing placeholder-like vanilla approximation feel.
- `expecto_patronum` must prioritize anti-dark repel/aura identity over generic monster-control flavor.
- Obscurial remains on existing architecture, but must deepen risk/reward messaging and cadence without adding new form systems.

### Audit Snapshot

Severity: `High` = contradicts canon / misleads players. `Medium` = feels off-lore but playable. `Low` = flavor polish only.

| Domain | Finding | Severity | Status |
|---|---|---|---|
| Localization | Duplicate `en_us.json` in main + generated resources caused silent key drops. | High | Resolved |
| Localization | `time_turner` had no guaranteed display-name key in generated output. | High | Resolved |
| Economy | Coin denominations didn't follow canonical ratios (29 knuts = 1 sickle, 17 sickles = 1 galleon). | Medium | Resolved |
| Economy | Niffler theft/pickup logic treated all coin-like items equally, including non-canonical currency (`dragot`). | Medium | Resolved |
| Spells | Some datapack spells used strongly non-lore ambient sounds (evoker/levelup/glass break). | Medium | Resolved |
| Spells | `Lumos` and `Expecto Patronum` still had generic reveal-marker behavior. | Medium | Resolved |
| Spells | Datapack approximations (`episkey`, `frigora`, `levicorpus`) used placeholder-like effects. | Medium | Resolved |
| Spells | `nox` affected nearby players; `lumos` doubled as area fire cleanup. | Medium | Resolved |
| Spells | Patronus cone pulse repelled generic monsters instead of dark-aligned targets only. | Medium | Resolved |
| Progression | `Time-Turner` not exposed in creative tab despite being registered. | Low | Resolved |
| Faction/World | House-specific flavor assets were uneven (only Slytherin had common-room portrait). | Low | Resolved |
| World Placement | Wizarding plants lacked natural generation in world placement pipeline. | Medium | Resolved |
| World Placement | Tree placed features lacked explicit biome placement guardrail. | Low | Resolved |
| Obscurial | Dark-form upkeep cadence under high stress lacked explicit escalation cues. | Low | Resolved |

### Remaining Intentional Deviations

- `time_turner` is explicitly a simplified gameplay mechanic (world-time nudge), not canonical constrained time travel.
- `dragot` remains in the mod as optional regional currency, excluded from canonical wizarding coin logic.
- Full canonical timeline rewriting and paradox simulation are intentionally out of scope for alpha.
- House content currently emphasizes flavor assets; house-specific mechanics/progression are out of scope for this pass.
