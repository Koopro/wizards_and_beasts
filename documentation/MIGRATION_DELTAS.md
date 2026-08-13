# Spell Migration Deltas (Step 4 — the 15 non-beam fully-data spells)

Honest record of what "fully-data" cost. Per spell: the migration mechanism, every cosmetic/
convenience behavior shed, and every residual id-keyed Java tail kept. Functional behavior (damage,
applied effect + duration/amplifier, targeting, AoE, gating, cast type) is preserved everywhere; only
cosmetics/conveniences are shed, and a few spells retain Java tails for bits no component expresses.

**Governing decisions:**
- **Primary damage stays on the scaled `baseDamage` props field** (load-bearing policy) — folds in
  skill+wand+proficiency. The `damage` component (flat/adjunct) is used by **none** of the 15.
- **Projectile/cone combat spells migrate via existing scaled props** (`baseDamage`/`igniteSeconds`/
  `explode`/`knockback`/`targetEffects`) rather than components, because (a) the runner is not wired
  to projectile **block** hits (`onHitBlock`), and (b) props preserve exact knockback direction/force
  and block behavior. This is more faithful than the prescribed component for these spells.
- **Components drive** the self/utility spells where the runner site matches and props can't express
  the behavior.
- **Option (b)** (remove targeted early-returns): applied to **liberacorpus** only (lossless).
  `finite_incantatem` and `levicorpus` early-returns were **kept** — removing them changes behavior
  (see below), which §5 forbids.

---

## Combat — migrated via scaled props (no components; full fidelity)

| Spell | JSON props | Residual id-keyed tail (unchanged, fires by JSON id) | Shed |
|---|---|---|---|
| incendio | cone, baseDamage 3.0, igniteSeconds 5 | block-fire-along-look (kept — via `props.ignites()` in cone handler) | — none (igniteSeconds preserves both entity ignite **and** block fire; superior to the `ignite` component) |
| diffindo | projectile, baseDamage 6.0 | diffindo block-cut (`isDiffindo`) | — |
| bombarda | targeted, baseDamage 7.0, explode 2.5/no-break | bombarda block push+impact (`isBombarda`, inside `props.explodes()` branch) | — (used `explode` prop, not `explosion` component, to keep block-targeting + push tail) |
| confringo | projectile, baseDamage 6.0, igniteSeconds 3, explode 2.0/no-break | confringo block-impact (`isConfringo`) | — (props not components: runner isn't wired to `onHitBlock`) |
| flipendo | projectile, baseDamage 2.0, knockback 3.0 | block-miss item push (`isPushSpell`, force 2.4 preserved) | — (knockback prop not `impulse`: exact force + travel-direction) |
| depulso | projectile, baseDamage 1.0, knockback 5.0 | block-miss item push (`isPushSpell`, force 4.0) | — (prop not `impulse`) |
| glacius | cone, baseDamage 2.0, targetEffects [petrificus_totalus 40/0] | glacius block interaction (`isGlacius`) | — (targetEffects prop scaled by cone handler = identical to `apply_effect`; block interaction id-keyed) |

## Utility — migrated via components (+ residual tails where noted)

| Spell | Components (JSON) | Residual Java tail kept | Shed (cosmetic/convenience) |
|---|---|---|---|
| nox | `dispel specific:[lumos_field]` + `swap_active_spell lumos` | — | cast particle burst. ~~lumos↔nox loadout-swap toggle + sync~~ **restored** via the `swap_active_spell` component (lumos: `swap_active_spell nox learn=true`; nox: `swap_active_spell lumos`). |
| reparo | *(none — see tail)* | `handleReparoSelf` (full-repair + wand-aware selection + ElderWand guard + block-repair) | block-repair range no longer wand-scaled (5.0 fixed vs `5.0 × wand.rangeFor`). The `repair` component is unused — it repairs by a fixed amount and can't express full-repair / wand-aware selection / ElderWand guard. |
| liberacorpus | `dispel specific:[levitation]` (early-return removed) | — | "fizzle" failure sound when target has no levitation; bespoke success-burst → generic beam. Functional core (remove levitation) preserved. |
| arresto_momentum | `apply_effect slow_falling 220` (self, scaled) | `applyArrestoAreaStabilize` (AoE velocity dampen — no component) | — (AoE preserved as residual) |
| episkey | `heal 4` + `dispel specific:[poison,wither,blindness,nausea]` | `handleEpiskeySelf` trimmed to **HP-conditional Regeneration** (120 vs 80; missing-HP read pre-heal — the SELF arm runs the tail before the runner) | — (regen exact; heal clamps identically to `min(4,missing)`) |
| frigora | `clear_fire` + `apply_effect fire_resistance 220` + `apply_effect water_breathing 120` | `handleFrigoraSelf` trimmed to Glacius block interaction | — |

## Kept in Java — NOT migrated (functional reasons; option-b exceptions)

| Spell | Why kept |
|---|---|
| finite_incantatem | Targeted early-return **kept**. finite cleanses the **caster** when not aiming at an entity; the generic targeted path applies effects only to a looked-at entity (runner isn't wired to a no-target cast), so removing the handler would break self-cast cleanse — functional, §5. Its `dispel scope:mod_namespaced` logic is exactly what `handleFiniteIncantatem` already does. |
| levicorpus | Targeted early-return **kept**. Its LEVITATION(90/1)+SLOWNESS(60/2) were applied **raw/unscaled** (direct `addEffect`); an `apply_effect` component scales by `durationMult`, which would add proficiency duration-scaling the original lacked — a behavior change, §3/§5. Also keeps fall-distance reset + `setTarget(null)`. |

## State after this run

- **11 Java spell classes deleted** (incendio, diffindo, bombarda, confringo, flipendo, glacius,
  depulso, nox, reparo, liberacorpus, arresto_momentum). 4 JSON spells gained `effects`.
- `SELF_UTILITY_RULES` now holds only: **bespoke** (protego, capacious_extremis, claustra_reverto,
  riddikulus) + **residual tails** (reparo, arresto_momentum, episkey, frigora).
- `Crucio` requirement repointed `Spells.INCENDIO` → `"incendio"` (id-string). Tests repointed
  `Spells.FLIPENDO` → `Spells.EXPELLIARMUS` (FLIPENDO removed).
- **Still Java / deferred:** crucio + wingardium_leviosa (BEAM — F2), the 5 hybrids, the bespoke set.
- `SpellCastSupport.isLiberacorpus` is now unused (left in place; harmless).

---

# Step 5 — the 5 hybrids

**Governing decision (reported per the step-5 prompt's own rule):** **zero new components were
built.** The four proposed (`pull`, `disarm`, `block_state`, `place_fluid`) would all have been dead
code at their spells' actual invocation points:
- `pull` — accio's cone handler (`handleAccio`) early-returns before the runner; its smooth-lerp /
  LOS / item-priority pull is unexpressible by a one-shot impulse. (3.5's `impulse` already has a
  `PULL` direction, so a `pull` component would also have been redundant vocabulary.)
- `disarm` — `disarms` already exists as a `SpellDefinition` prop; the on-hit disarm path is
  props-driven + id-keyed (`ExpelliarmusDisarmHandler` with proficiency scalar).
- `block_state` — alohomora/colloportus cast at *blocks*; the targeted handler only invokes the
  runner for a living target, so the component would never fire on a door.
- `place_fluid` — aguamenti is **BEAM_CHANNEL**; all behavior is per-tick in the beam handlers and
  the runner has no beam wiring (F2, still open).

Faithful mechanism instead: **two new `SpellDefinition` prop fields** — `opensBlocks` (bool) and
`pullStrength` (float) — mapped in `JsonSpell.buildProperties` onto the existing
`SpellProperties.opensBlocks()` / `pullsTarget()` builders, with the rich behavior staying in the
id-keyed handlers (all of which match JSON ids via `SpellIds.matches`).

| Spell | JSON | Residual id-keyed Java tail (unchanged) | Shed |
|---|---|---|---|
| expelliarmus | projectile, disarms, speed 1.6 / spread 0.02, req knows stupefy | on-hit disarm (`ExpelliarmusDisarmHandler` + proficiency scalar + impact sound), block-miss item push (`isPushSpell`), projectile clash (`trySpellClash`), drop-tag pickup handler | Java `getBaseKnockback()=1.2` was **dead** (props knockback was 0, so the on-hit gate never fired) — not carried into JSON; no observable change. Default generic cast sound preserved (no `sound` field). |
| accio | cone 16.0, pullStrength 2.0, req knows wingardium_leviosa | `handleAccio` (smooth-lerp pull, LOS clip, item-priority sort, fall-reset) reads `props.getPullStrength()` — now sourced from JSON | — |
| alohomora | targeted 5.0, opensBlocks | `handleAlohomora` (lock tiers, proficiency gating, Colloportus-lock respect, messages) | — |
| colloportus | targeted 5.0, opensBlocks, req knows alohomora | `handleColloportus` (`ColloportusLockStore`, messages) | — |
| aguamenti | beam_channel 12.0, req knows lumos | **entire behavior**: per-tick beam logic (`WandBeamSpellIds.isAguamenti` → soak/fill/place-after-hold in `WandBeamSpellHandlers`/`AguamentiHelper`) | — (registration-only migration; behavior follows the id. Components can't reach it until F2 beam wiring.) |

`Protego` requirement repointed `Spells.EXPELLIARMUS` → `"expelliarmus"`. Tests repointed
`Spells.EXPELLIARMUS` → `Spells.PROTEGO`. No `SELF_UTILITY_RULES` entries existed for the 5 (none
are SELF spells).

**State after Step 5:** Java spell registrations = `WINGARDIUM_LEVIOSA`, `RIDDIKULUS` (bespoke),
`PROTEGO`, `EXPECTO_PATRONUM`, `AVADA_KEDAVRA`, `CRUCIO`, `IMPERIO`, `OBSCURUS_SURGE`,
`OBSCURUS_GRASP` — i.e. only the bespoke set + the two beam-pending spells (crucio,
wingardium_leviosa). 18 spells now ship as JSON.

## Verification
- `compileJava` clean. Tests green: SpellDefinitionCodecTest (incl. parse-all of every spell JSON +
  migrated-component spot-checks + step-5 hybrid prop asserts), SpellRequirementTest,
  SpellLearningServiceTest, SpellEffectComponentCodecTest.
- In-game cast spot-checks (headless — not run): cast each migrated spell; confirm damage scales,
  effect durations scale, block behaviors fire; disarm strips held item; alohomora/colloportus
  unlock/lock; accio pulls; aguamenti soaks/fills/places. Manual session required.

---

# F2 — Beam-channel runner wiring + cadence; crucio & wingardium_leviosa

**Mechanism.** Per-entry `cadence` tag `{start, tick, end}`, default `tick`, added as a wrapper
(`SpellEffectEntry` = component + cadence, merged into the same flat JSON object via the dispatch's
`MAP_CODEC`) — **no component variant was rewritten** and pre-cadence JSON parses unchanged.
`WandBeamChannelLogic` now runs a channel spell's entries: `start` once on the first channel tick,
`tick` on the **existing** `Config.beamChannelEffectIntervalTicks` interval (only while the beam
holds a living target; subject = beam target), `end` once on release/interruption/spell-switch
(cached-target fallback caster). Scaling multipliers re-resolved from the live `ProficiencyScaler`
profile each invocation. Cadence is **inert** outside BEAM_CHANNEL — the non-channel sites still run
the full list once via the phase-less overload (BEAM_LETHAL/avada never reaches the wiring), so no
already-migrated spell's behavior changes. Nested `aoe_apply` children stay plain components.

| Spell | JSON | Residual id-keyed Java tail (unchanged) | Shed / deltas |
|---|---|---|---|
| crucio | beam_channel 50.0, cooldown 220, baseDamage 2.0, req proficiency incendio:mastered, effects `[apply_effect cruciatus_pain 60 target darkArts cadence:tick]` | `handleCrucioChannel` minus the pain apply: intent feedback payload, corruption accrual (5×intent per interval), WITHER/SLOWNESS cleanup, **ramp damage** (≥40 ticks, every 20: `min(1.5, 0.4+ticks/120)×intent` — time-ramp inexpressible), proficiency hits, pain-strip on end/target-switch (`clearSessionEffects` — pain still lapses immediately at beam stop) | (1) pain refresh no longer intent-scaled: fixed channel interval (5 default, ≤22 LOW) vs `interval/max(0.5,intent)`; duration `60×durationMult` (≥36) vs `max(20, 60/intent)` — invisible in practice (refresh ≪ duration; immediate strip at end unchanged). (2) cast-time "That power is sealed away" message + fizzle sound when DARK_ARTS disabled (Java `executeCast` override) — shed; channel still no-ops (component `darkArts` gate + tail module gate). (3) props targetEffects weakness/nausea/slowness 40t were **dead** (no dispatch path applies targetEffects for BEAM_CHANNEL) — not carried into JSON; no observable change. |
| wingardium_leviosa | beam_channel 16.0, cooldown 60, projectileSpeed 0.0, req knows lumos, **effects empty** | **entire lift behavior**: `WandBeamSpellIds.isLeviosa` → `handleLeviosaChannel` direct spring-motion (hold-distance scroll adjust, grace-miss ticks, no-gravity save/restore) — direct motion manipulation, no component expresses it (registration-only migration, like aguamenti) | — |

**Supporting changes:**
- `ObscurusSurge`/`AvadaKedavra` requirements repointed `Spells.CRUCIO` → `"crucio"` (id-string).
- `JsonSpell.buildRequirement` no longer degrades to NONE when a prerequisite isn't registered yet:
  it resolves eagerly when present (unchanged) and otherwise falls back to the mod-namespaced id
  string, enforced lazily at `isMet` time. Needed because JSON spells init one-by-one during the
  reload sweep and crucio's prerequisite (incendio) is itself JSON — load order is arbitrary.
- `Crucio.java` + `WingardiumLeviosa.java` deleted. `WandBeamChannelLogicTest`'s
  session tests still use the `"crucio"` id string — unaffected.

**State after F2:** Java spell registrations = `RIDDIKULUS`, `PROTEGO`, `EXPECTO_PATRONUM`,
`AVADA_KEDAVRA`, `IMPERIO`, `OBSCURUS_SURGE`, `OBSCURUS_GRASP` — the bespoke set only. 20 spells
ship as JSON. `SELF_UTILITY_RULES` unchanged (neither beam spell is SELF).

## Verification (F2)
- `compileJava` clean; full test suite green (entry-cadence codec tests added: default-tick
  back-compat, start/end parse + round-trip, top-level-only nesting, mixed lists; crucio/wingardium
  JSON spot-checks added to SpellDefinitionCodecTest's parse-all).
- Non-channel inertness: by construction — phase-less runner overload at all non-channel sites runs
  every entry regardless of cadence; existing JSON has no `cadence` field and decodes to the same
  component values.
- In-game (headless — not run, manual session required): crucio with DARK_ARTS temporarily ENABLED →
  continuous pain while channeling, stops on release; wingardium sustained lift, releases on stop;
  `/reload` JSON override changes behavior live; start/tick/end ordering via a test channel spell.

## Creature Build Pass (2026-06-18)

**New data-driven creature system.** 53 bestiary entries lacking an entity are now registered,
summonable, GeckoLib-rendered placeholder mobs. New: `creature/CreatureDefinition` (codec record),
`CreatureDefinitionRegistry`, `CreatureDefinitionLoader` (reload listener, dir `creatures`),
`entity/creature/Generic{Ground,Flying,Aquatic,Sessile}BeastEntity` (+ `GenericBeastEntity` base),
`registry/ModCreatures` (53-entry MANIFEST → EntityType + baseline attributes + spawn egg per id),
`creature/command/CreatureCommands` (`/wandb creature summon|list`). `Module.CREATURES` flipped
`DISABLED`→`PREVIEW`.

**Behavioral note:** existing entities/spells/brooms unaffected — purely additive. The 9 entries that
already had entities (incl. in-progress Niffler) are untouched. Per-creature runtime attributes load
from `data/wizards_and_beasts/creatures/<id>.json` and are applied over a per-locomotion baseline on
spawn (server-side); `EntityType` hitbox size is registry-frozen from `ModCreatures.MANIFEST`.

## Verification (Creature Build)
- `./gradlew compileJava` clean; `./gradlew build -x test` BUILD SUCCESSFUL (processResources/lang/jar ok).
- All 53 creature/geo/animation JSON validate; enum values match `BodyPlan`/`Locomotion`/`Temperament`.
- In-game (not run here — dev client session required): `/wandb creature summon <id>` then observe idle
  animation. Render path is byte-identical to the shipping `GeoRendererHelper.simple` pipeline.

## Creature Build Batch 2 (2026-06-18)
33 NEW canonical beasts added as full bestiary entries + generic entities (knarl, griffin, fairy,
puffskein, pygmy_puff, chizpurfle, horklump, red_cap, erkling, leprechaun, wampus_cat, salamander,
murtlap, moke, jarvey, kappa, gnome, hippocampus, sea_serpent, fire_crab, imp, porlock, nogtail,
dugbog, mackled_malaclaw, ramora, shrake, tebo, hodag, snallygaster, glumbumble, pogrebin, quintaped).
ModCreatures roster 53→86; bestiary entries 62→95. Placeholder lore lang + bestiary icon/silhouette.
Build SUCCESSFUL. First SESSILE-locomotion creature (horklump) now exercises GenericSessileBeastEntity.

## Creature AI + Trait Foundation (2026-06-18)
Data-driven behaviour layer for all 86 generic creatures. New `creature/Trait` enum (FEARFUL, PACK,
FIRE_IMMUNE, FIRE_ATTACK, POISON_ATTACK, PETRIFY, CHARGE, KNOCKBACK, THIEF, AMPHIBIOUS, REGEN,
EXPLODE_ON_DEATH). CreatureDefinition gains `temperament` (already), `attackDamage`, `traits[]`.
GenericBeastEntity now wires goals by temperament (PASSIVE flee / NEUTRAL retaliate / HOSTILE hunt),
applies ATTACK_DAMAGE on spawn, and interprets traits via fireImmune()/doHurtTarget()/tick()/die()
(on-hit ignite, poison, petrify-lite slow+blind, knockback, charge, item-theft; passive regen;
death explosion). Subclasses now supply only movement goals (addMovementGoals); base owns combat.
ModCreatures baseline adds ATTACK_DAMAGE/ATTACK_KNOCKBACK. Behaviour assigned per creature by
tools/creature_behavior.py (category defaults + per-id overrides). Build -x test SUCCESSFUL.
Signature one-offs (true dragon fire-breath, basilisk death-gaze, niffler-grade theft) deferred.

## Creature Carry-Theft + Real Lore (2026-06-22)
THIEF trait upgraded to niffler-grade: GenericBeastEntity now pockets stolen stacks (up to 8) into a
carried list, persists it via ValueOutput/ValueInput (ItemStack.CODEC.listOf, key "CarriedLoot"), and
drops the loot on death (jarvey/leprechaun carry then). Real bestiary lore written for all 33 new
beasts (tools/creature_lore.py) replacing the placeholder strings — 0 placeholder lore left. Build
-x test SUCCESSFUL; gametest server loads 86 defs clean.

## Dragon Fire/Venom Kit — 10 breeds rebound (2026-06-24)
The ten canonical dragon breeds (antipodean_opaleye, chinese_fireball, common_welsh_green,
hebridean_black, hungarian_horntail, norwegian_ridgeback, peruvian_vipertooth, romanian_longhorn,
swedish_short_snout, ukrainian_ironbelly) are now bound to a dedicated `DragonEntity`
(extends `GenericFlyingBeastEntity`) instead of the plain generic flyer — `ModCreatures.factory()`
routes `DRAGON_IDS` to `DragonEntity::new`; `ClientSetup` routes them to `DragonRenderer`.
`CreatureDefinition` gains an optional nested `dragon` block (`DragonTraits`: fire_range, fire_color,
flame_shape STREAM/JET/BURST, block_effect IGNITE/ASH, bite_venom, rideable [data-only], scale).
BEHAVIOURAL DELTA: dragons previously breathed via the generic `BreatheFireGoal` (hardcoded range 9,
no colour/shape/ASH). They now use `DragonBreathGoal` — a server-authoritative cone (half-angle by
flame_shape; IGNITE via vanilla fire, ASH = clean removal of `#wizards_and_beasts:dragon_ash_combustible`
timber/bone blocks), tinted server-spawned particles, GeckoLib `triggerAnim` breath/bite (no packet).
The `FIRE_BREATH` trait was REMOVED from the 10 dragon JSONs (would double-fire alongside the new goal);
FIRE_IMMUNE/FIRE_ATTACK/KNOCKBACK retained. Ridgeback + Vipertooth gain melee poison via `bite_venom`.
Render scale is per-breed (smallest 0.8 → largest 2.2) applied to the `root` bone via render-state
DataTicket — no hitbox change (hitbox stays registry-frozen in MANIFEST). compileJava + build -x test SUCCESSFUL.

### Dragon breath — Ice-and-Fire ground scorch (2026-06-24)
`DragonBreathGoal` block effect reworked: IGNITE no longer gated on `BlockState.isFlammable` (that left
natural terrain untouched — only the victim caught fire). Breath now carpets vanilla fire onto ANY solid
top surface across a splash footprint at the cone impact (`scorchArea`/`igniteColumn`), deduped per breath
via a shared `Set<BlockPos>`. Footprint radius by flame_shape (JET 1.0 / STREAM 1.5 / BURST 2.5); rays 12→18;
ASH mirrors the same footprint (`ashColumn`). Particle jet densified (per-half-block sampling, downrange
spread, periodic LAVA). LIMITATION: placed fire is vanilla, so on non-flammable ground it self-extinguishes
after a few seconds (no lingering char) — a persistent dragon-fire block is deferred (block registration, out
of scope). compileJava SUCCESSFUL.

### CreatureAbility framework + FireAffinity (first beast ability) (2026-06-24)
NEW reusable, datapack-driven ability layer mirroring `SkillNodeEffect`: `creature/ability/CreatureAbility`
is a sealed interface with a dispatch `Codec<CreatureAbility>` keyed by a `Type` serialized-name (the
`SkillNodeEffect` precedent). Server-side hooks: `tick(entity)`, default-no-op `onHurt(entity,source,amount)`,
`onMeleeContact(entity,target)`, and `registerGoals(entity,goalSelector)`. Hooks are typed against the shared
`GenericBeastEntity` base (every data-driven creature, dragons included). `CreatureDefinition` gains a general
top-level `List<CreatureAbility> abilities` (NOT inside the dragon-specific `DragonTraits`), default empty —
so the record now has 17 components, one past `RecordCodecBuilder.group`'s 16-arg ceiling. The codec is
therefore assembled from two `MapCodec` halves (`StatBlock` + `AssetBlock`) merged with `Codec.mapPair`; both
read the SAME flat JSON object, so the on-disk shape is unchanged and all 60 existing creature JSONs parse
untouched (proven by `CreatureDefinitionCodecTest`, 6 tests).

First concrete ability `creature/ability/FireAffinity` (record + MapCodec, all fields defaulted):
`fire_immune`, `requires_fire`, `dry_grace_ticks`, `dry_damage`, `regen_in_fire`, `seek_fire_when_dry`,
`ignite_melee_attackers`, `ember_particles`. Behaviour (server-side, gated on `Module.CREATURES` via
`isEnabled` — the `DragonBreathGoal` precedent): in fire/lava resets the dry timer + heals `regen_in_fire`/sec;
out of fire past the grace window applies `dry_damage`/sec through `DamageSources.dryOut()` (DRY_OUT is not
fire-typed, so it bypasses the creature's own fire immunity); `ember_particles` emits FLAME+ASH at the body
anchor every 6t; `ignite_melee_attackers` ignites a melee attacker via `onHurt`; `seek_fire_when_dry` registers
`SeekFireGoal` (a `MoveToBlockGoal` that self-gates on the module at `canUse` and only seeks once half-way
through the grace window). DELTA on the three creatures: Salamander/Ashwinder/Fire Crab JSONs gain an
`abilities:[{type:fire_affinity,…}]` block (their pre-existing `FIRE_IMMUNE`/`FIRE_ATTACK` traits are retained;
`FireAffinity.fireImmune` is OR-ed into `GenericBeastEntity.fireImmune()`). Per-entity dry-out counter lives on
`GenericBeastEntity` (`FireDryTicks`) persisted via the existing `addAdditionalSaveData`/`read` pattern
(mirrors `CarriedLoot`) — the record stays a shared, stateless definition value. compileJava + targeted test
SUCCESSFUL.

### Full-roster creature abilities — common library + signatures (2026-06-24)
Populated the `CreatureAbility` layer for the whole roster. 19 new variants added to the sealed interface
+ dispatch `Type` enum: 14 common parameterized abilities (`water_affinity`, `status_on_hit`, `enrage`,
`thorns`, `blink_away`, `heal_aura`, `dread_aura`, `bioluminescence`, `life_leech`, `constrict`,
`camouflage`, `leap`, `ranged_hex`, `web_snare`) + 5 bespoke signatures (`nundu_pestilence`,
`lethifold_smother`, `boggart_dread`, `thunderbird_storm`, `fwooper_song`). Shared `AbilitySupport`
(reusable `EffectSpec` status payload w/ codec, registry-safe effect resolution, AoE player/living
gathering, a lazy-`Supplier` `Particle` palette) keeps the records thin. 3 new self-gating AI goals
(`SeekWaterGoal`, `RangedHexGoal`, `WebSnareGoal`) join the existing `SeekFireGoal`.

Ranged attacks (`ranged_hex`, `web_snare`, `nundu_pestilence`) are server-authoritative hitscan/AoE
emitters (particles + effects, the `DragonBreathGoal` pattern) — no new projectile entity-type/renderer.
`GenericBeastEntity` gained two persisted per-entity counters mirroring `FireDryTicks`: `WaterDryTicks`
(WaterAffinity dry-out) and `AbilityCooldown` (shared cooldown for blink/camouflage
reveal/signature bursts, decremented each server tick). The `Particle` enum resolves its `ParticleOptions`
through a `Supplier` so the dispatch codec can be class-loaded and parsed without bootstrapping the particle
registry (pure-codec unit tests stay registry-free).

DELTA: 83 creature JSONs gained an `abilities` block — all 73 generic beasts (canon-grounded common +
signature mixes; see `CREATURE_ABILITIES_PLAN.md` for the per-creature table) plus `enrage` added to the 10
dragons (their `DragonTraits` breath kit untouched). Existing `FIRE_*`/`POISON_ATTACK`/etc. traits are
retained alongside the richer abilities. All ability dispatch + custom goals self-gate on `Module.CREATURES`
via `isEnabled` (PREVIEW+ENABLED run; vanilla `LeapAtTargetGoal` is the one exception — it cannot module-gate,
low impact). Back-compat proven: `CreatureDefinitionCodecTest` parses every shipped creature JSON and asserts
the new dispatch keys; full `:test` BUILD SUCCESSFUL. compileJava SUCCESSFUL.

### Creature ability canon-gap fills + onDeath hook (2026-06-24)
Closed the remaining canon signature gaps. New `onDeath(entity)` hook on `CreatureAbility` (default no-op),
dispatched server-side from `GenericBeastEntity.die()` (module-gated). 4 new variants: `spell_resist`
(Graphorn — heals back a fraction of MAGIC/INDIRECT_MAGIC damage, the reduction matching the mod's
`damageSources().magic()` spells; reusable for any spell-resistant beast), `explosive_horn` (Erumpent —
contained `ExplosionInteraction.NONE` burst on a gored melee victim, distinct from its on-death EXPLODE),
`death_cry` (Jobberknoll — death burst: scream sound + Glowing on nearby living), `anchor` (Ramora — per-sec
heavy Slowness on nearby in-water creatures, pinning them). Assigned: graphorn +spell_resist, erumpent
+explosive_horn, jobberknoll +death_cry, ramora +anchor. CreatureAbility variant count now 24 (20 common-ish
+ 9 signatures across the two passes). compileJava + full :test SUCCESSFUL.

### Cleared the deferred creature-ability backlog (2026-06-24)
Built every remaining deferred item. 4 new variants (28 total) + a real projectile entity + a render-state
size-shift + a new `onDeath`-style render path:

- **occamy_choranaptyxis** (Occamy size-shift, render-only): `GenericBeastEntity` gains a synced
  `DATA_RENDER_SCALE` float (default 1.0, hitbox stays registry-frozen). New `ScaledBeastRenderer` (mirrors
  `DragonRenderer`'s render-state DataTicket → `root` bone scale, no live-entity access) now renders all
  non-dragon creatures (1.0 = identical to the old `GeoRendererHelper.simple`). The ability eases scale
  toward max when roused + roomy, min when calm/confined (ceiling headroom proxy).
- **flame_burst** (Fire Crab, +`FlameBurstGoal`) and **ember_trail** (Ashwinder): the "fire emission"
  follow-up the fire pass deferred. Burst = AoE ignite + small fire damage on cooldown; trail = small
  per-tick chance to lay a vanilla fire block in the Ashwinder's wake (air-on-solid only). No new items.
- **Real ranged projectiles**: `ranged_hex` no longer hitscans. New `BeastHexProjectile`
  (`ThrowableItemProjectile`, registered `beast_hex_projectile`, rendered via vanilla `ThrownItemRenderer`
  as a flung magma cream — the `WizardingThrownEntity` pattern) carries server-side damage + optional effect
  + particle trail; `RangedHexGoal` now launches it (dodgeable, travels, LOS) with a throw sound.
- **danger_sense** (Kneazle): periodically outlines nearby `Enemy` mobs with Glowing (its sixth sense for
  threats).

Assigned: occamy +occamy_choranaptyxis, fire_crab +flame_burst, ashwinder +ember_trail, kneazle +danger_sense.
Codec test extended for the 4 new dispatch keys; the FireAffinity assertions now tolerate multi-ability
creatures. compileJava + full :test SUCCESSFUL.

NOT done (clear rationale): new canon creatures — already exist as dedicated `entity.beast` entities
(augurey/mooncalf/streeler/phoenix/bowtruckle/cornish_pixie/thestral), not the data-driven roster, so nothing
to add there. Niffler THIEF→ability-layer refactor left untouched (prior scope). Render-time model tints are
art-swap work with no logic to write. Ashwinder igniting-egg item deferred to a loot/item pass (no new items
this scope).

---

## Vocation Framework prompt — HALTED at audit (2026-06-29)

No code written. Behavioral deltas the prompt assumed but reality contradicts (see AUDIT_PUNCHLIST §"Vocation
Specialization Framework"): `SkillTree`→`SkillTreeId` (7 values, no maxPoints); `SkillNode`→`Skill` (final
class, String id, `SkillEffect` effects + lazy `nodeEffects`, multi-level); `PlayerSkillData` is NBT not Codec
(`skillPoints` live counter + `unlockedSkills` level-map, no `unlockedNodes`/`spentPoints`/`abilityFlags`);
refund model is `skillPoints += cost*level`, not `totalPointsEarned − Σ spentPoints`; `SkillAttributeApplicator`
is applyAll/removeAll full-rebuild, no per-source keyed apply/remove. Per §12, halted rather than improvise.

---

# Heritage Selector Redesign — Display Layer Deltas (2026-06-29)

Scope: first-join Heritage selection **screen + display/widget layer only**. Gate mechanic, networking, and data layer untouched.

**Behavioral deltas:**
- **Screen UX rewritten** `HeritageSelectionScreen`: the three-phase arrow-cycle wizard (`HERITAGE_LIST`
  → `VARIANT_LIST` → `CONFIRMATION`) is replaced by a single master-detail surface — left rail of all
  10 heritages + right detail panel (lore / identity card / variant chips) + in-screen final-confirm
  overlay. New widgets: `HeritageRailEntry`, `HeritageVariantChip`; new procedural skin
  `HeritageCeremonyRenderer`. `HeritageSelectionRenderHelper` deleted (orphaned by the rewrite).
- **Scaling** now via `GuiScaleHelper` (downscale-only clamp) instead of `ScreenLayoutScaler`.
- **Locked heritages are now browsable.** Previously all 10 were arrow-cycled and the Choose button
  simply disabled for non-alpha heritages. Now the 7 locked heritages render dimmed-with-padlock in the
  rail, are still clickable to read their full detail, but their Confirm button stays disabled and the
  detail panel shows a "Coming Soon" ribbon. The 3 alpha heritages (`WIZARDKIND`, `WEREWOLF`,
  `OBSCURIAL`) remain the only committable ones.
- **Confirm is now an explicit modal** ("This choice is final.") gating the *existing*
  `HeritageSelectC2SPayload`. No new packet.

**Gate-mode:** UNCHANGED contract — `shouldCloseOnEsc()==false`, `isPauseScreen()==false`, ESC swallowed
at root (only steps back out of the confirm overlay). Parameterless ctor + server-driven open via
`HeritageDataSyncS2CPayload.openSelector()` preserved.

**Commit validation (Deliverable 5): NO CHANGE.** Audit confirmed `HeritageSelectC2SPayload.handle`
already rejects `!heritage.isAlphaAvailable()` (server-authoritative, lines 71-75) before writing, so a
crafted commit for a locked heritage is already refused. No server-side edit was made.

**New translation keys** (`en_us.json`): `gui.wizards_and_beasts.heritage.*` (12 UI strings) +
`heritage.wizards_and_beasts.<id>.{lore,trait,flavor}` for all 10 heritages. No new display strings
hardcoded in Java.

**Optional art:** wax-seal motif expects `textures/gui/heritage/wax_seal.png` (32×32, spec in
`HeritageCeremonyRenderer` javadoc); absent → procedural crimson disc fallback. No binaries generated.

**Known non-blocker:** prompt cites 4 Wizardkind variants; enum has 5 (adds `adopted_magical`). The
variant selector renders all of a heritage's variants uniformly via `getSubtypes()`, so the 5th shows
too — faithful to the data, count differs from the prompt only.

## Handbook Infrastructure

Ministry Handbook Part 1 (infrastructure only; no chapter content JSON — that is Part 2).

- **New `Module.HANDBOOK`** enum entry (`module/Module.java`), default `State.ENABLED` in `ModuleManager`. Gates handbook screen open + creative-tab display; item registration is unconditional.
- **New `MINISTRY_HANDBOOK` item** — `item/MinistryHandbookItem` (stacksTo 1), registered in `registry/MiscItemRegistry`. `use()` opens the screen client-side via the reflective `ClientScreenHooks.openHandbookScreen()` pattern (same as `BestiaryItem`), gated on `Module.HANDBOOK`. Added to `ModCreativeTabs.MAIN` behind a `HANDBOOK` check. Lang: `item.wizards_and_beasts.ministry_handbook`.
- **New datapack codecs** — `handbook/HandbookChapter` (record + `Codec`) and `handbook/HandbookPage` (sealed interface + `Type` enum dispatch codec on `"type"`, mirroring `SkillNodeEffect`): variants `text`/`recipe`/`image`/`cross_ref`.
- **New `HandbookChapterManager`** reload listener (`SimpleJsonResourceReloadListener`, dir `handbook/chapters`), registered in `WizardsAndBeastsMod` under `AddServerReloadListenersEvent` id `handbook_chapter_reload_listener`. Sorts by `sort_index`; holds server map + static `CLIENT_CHAPTERS`.
- **New `SyncHandbookPayload`** registration — `network/handbook/SyncHandbookPayload` (`CustomPacketPayload` + `StreamCodec` via `ByteBufCodecs.fromCodec(HandbookChapter.CODEC.listOf())`), registered in `network/handbook/ModNetworkHandbook.register`, wired into `ModNetwork.register`. Client handler `ClientPayloadHandlers.handleSyncHandbook` → `HandbookChapterManager.setClientChapters`.
- **New `HandbookScreen`** (`client/handbook`) — index + chapter modes, reuses Bestiary parchment/dark aesthetic (`BestiaryColors` + shared `textures/gui/bestiary/screen.png`). Reads only `CLIENT_CHAPTERS`.
- **Recipe** `data/wizards_and_beasts/recipes/ministry_handbook.json` (shaped: 8×paper ring + book). Item model `models/item/ministry_handbook.json` + **placeholder** 16×16 texture (marked in model `__comment`).

### Deviations from the Bestiary pattern (and why)
- **Sync carries definitions, not progress.** Bestiary's `BestiaryDataSyncPayload` syncs per-player *discovery progress* and the client reads entry *defs* straight from the server-populated registry. The handbook spec (§1c) requires the *full chapter list* to be synced to the client, so `SyncHandbookPayload` carries `List<HandbookChapter>` and the screen reads `CLIENT_CHAPTERS` exclusively — no reliance on a shared-JVM registry. Makes it correct on dedicated servers.
- **Sync trigger is `OnDatapackSyncEvent`, not `PlayerLoggedInEvent`.** Fires on both join and `/reload`, so editing chapter JSON + `/reload` re-pushes to all connected players (Bestiary's login-only handler would miss `/reload`).
- **Recipe page render is structural for now.** `renderRecipe` draws a 3×3 grid + result frame + captioned recipe id; populating slots from the resolved recipe is deferred to the content pass (Part 2) to avoid coupling infrastructure to the client recipe-manager API surface.

## Handbook Content

Ministry Handbook Part 2 — chapter content JSON only. No Java, no registrations, no asset changes beyond Part 1.

### Chapter files produced (12) — `data/wizards_and_beasts/handbook/chapters/`
1. `welcome.json` (sort 0) — Foreword.
2. `heritage.json` (10) — Magical Heritage & Classification.
3. `wands.json` (20) — Wand Registration & Use.
4. `spells.json` (30) — Spell Licensing & Casting.
5. `brooms.json` (40) — Broom Registration & Flight.
6. `skills.json` (50) — Magical Development & Proficiency.
7. `abilities.json` (60) — Special Magical Abilities (10 pages).
8. `gamps_law.json` (70) — Gamp's Law.
9. `trunks.json` (80) — Extendable Trunk Regulation.
10. `currency.json` (90) — Wizarding Currency & Gringotts.
11. `bestiary_crossref.json` (100) — Magical Creatures cross-reference (incl. `cross_ref` → `wizards_and_beasts:niffler`).
12. `notices.json` (110) — Closing Notices.

All 12 validated as parseable JSON.

### Icon fallbacks used
- None. Every chapter icon resolves to a registered id. Ch09 (trunks) uses `wizards_and_beasts:enchanted_trunk` (a real trunk item) rather than the prompt's `:trunk`/`minecraft:chest` fallback, since no item is registered under the bare id `trunk`.

### Recipe page fallbacks used (recipe not found → text page)
- **Ch03 (wands), page 4** — prompt requested `recipe` page for `wizards_and_beasts:wand`; no such recipe exists under `recipes/`. Replaced with a `text` page ("Acquisition of Wands") noting wands are obtained through established channels.
- **Ch09 (trunks), page 4** — prompt requested `recipe` page for `wizards_and_beasts:travellers_trunk`; no such recipe exists. Replaced with a `text` page ("Acquisition of Trunks").

### Field-name corrections after audit
- None. All codec keys (`id`, `title`, `icon`, `sort_index`, `pages`; `heading`/`body`, `recipe_id`, `texture`/`caption`, `target_type`/`entry_id`) matched the §1 assumptions exactly.

---

# Placed Trunk Entry Mechanic (2026-07-01)

Expanded / Master's / Moody's trunks converted from held reach-in items to **placed directional blocks** you set down and climb into. Traveller's (`enchanted_trunk`, TIER_1) left as a held item. Decision (Upgrade License): "Convert to BlockItem (replace)" + "Both" 7-lock items become placed blocks.

## Behavioral deltas
1. **`expanded_trunk`, `masters_trunk`, `moodys_trunk` are now blocks, not items.** Right-clicking the item no longer teleports; you place the block, then right-click it to descend. Item ids are unchanged (BlockItems keep the same registry ids), so recipes/loot/creative references by id survive.
2. **Directional placement** — `TrunkBlock` has `HORIZONTAL_FACING`; faces the player on placement (mirrors `FlooFireplaceBlock`).
3. **Lock-cycle interaction** — sneak-right-click the placed block cycles the active lock across the tier's count (Expanded 3, Master's/Moody's 7), stored on `TrunkBlockEntity.activeLock`. Non-sneak right-click descends into the selected lock's compartment.
4. **Per-lock compartments** — each lock maps to its own sub-pocket via `deriveLockCase(base, lock)` (same derivation the old Moody's item used). The base id comes from the packed `POCKET_CASE_ID` component; `TrunkRegistryData.caseBindings` persists case→pocket so re-placing re-links every compartment.
5. **Decoy lock (7-lock tiers only)** — one deterministic lock index (`decoyLock(base)`, varies per trunk) opens a small plain `FIELD_CAMP` shell at the smallest radius (TIER_1) with a "The compartment is empty. Wrong lock." message. **No trap, no damage.** Expanded (3 locks) has no decoy.
6. **Pack-up** — breaking the block (survival) drops a `BlockItem` carrying `POCKET_CASE_ID` via `playerWillDestroy` + `popResource`; the block has no loot table so there is no duplicate drop. Re-placing re-binds the same compartments — **zero content loss** across place → enter → leave → break → re-place.
7. **Return anchor** — block entry saves the trunk's own position (`pos.above()`) as the per-player return point via the new `ExtensionCharmService.enterPocket(player, record, returnLevel, returnPos)` overload, so climbing out (existing in-shell trapdoor/door exit in `PocketDimensionEvents.onRightClickBlock`) lands the player back at the trunk. Exit path is item-independent and unchanged.
8. **Gating** — `Module.POCKET_DIMENSIONS` gates *entry and lock-cycle only*; block registration and placement are never gated. When the module is DISABLED, the placed block is inert ("The trunk's latches will not budge.").
9. **Lang** — Expanded/Master's/Moody's now use `block.wizards_and_beasts.*` keys (added to `en_us.json` + `ModLanguageProvider`); the old `item.*` keys for those three are now unused.

## TrunkRecord schema additions
**None.** Confirmed against the §5 trigger: return point is per-player in `TrunkRegistryData` (correct for multiplayer — multiple players may enter one trunk), and the active lock is per-placement on `TrunkBlockEntity`. Storing either on the shared `TrunkRecord` would be wrong. `TrunkRecord` is untouched.

## Decisions taken under the Upgrade License
- **Return point lives per-player in `TrunkRegistryData`, not on the BlockEntity**, despite the prompt listing "return point" as BE state. Rationale: a single BE-stored return would clobber across concurrent enterers. The BE stores only `baseCaseId` + `activeLock`; the block captures its own position as the return anchor at entry time.
- **Both 7-lock trunks (`masters_trunk`, `moodys_trunk`) share one `TrunkBlock`/`TrunkBlockEntity`**, differing only by base archetype (MINISTRY_STANDARD vs SAFEHOUSE). Decoy behavior keys off lock count (≥7), so both get it automatically.
- **`MoodysTrunkItem.java` deleted** (no longer registered). `MOODYS_TRUNK_ACTIVE_LOCK` / `MOODYS_TRUNK_BASE_ID` data components left registered (removing persistent components risks save breakage); the block uses `POCKET_CASE_ID` + BE state instead.

## Out of scope (untouched, as required)
- Pocket-dimension worldgen/archetypes/content, ladder/shed descent visuals, GeckoLib trunk model.
- Traveller's (`enchanted_trunk`) held-item path — still full-enters via `EnchantedTrunkItem.use()`; flagged in AUDIT_PUNCHLIST that it is not storage-only, but left intact per scope.
- Crafting/upgrade recipes; ownership/permission rules (existing `canAccess`/impound/muggle-worthy unchanged).
- Block models are placeholder cubes (datagen `createTrivialBlock`); bespoke trunk art pending. Datagen not re-run (avoids clobbering committed art); block models/blockstates land on next `runData`.

## Addendum (2026-07-01, follow-up): full conversion — Traveller's + Newt's Case

Follow-up decision: convert **all** remaining held trunk-likes to placed blocks (not just Expanded/Master's/Moody's).

- **`enchanted_trunk` (Traveller's, 1 lock)** and **`newts_case_item` (Newt's Case, Scamander)** are now `TrunkBlock`s too. No held reach-in trunk remains. `EnchantedTrunkItem.java` + `NewtsCaseItem.java` deleted; `DarkArtefactItemRegistry.ENCHANTED_TRUNK` and `TrinketItemRegistry.NEWTS_CASE_ITEM` removed; holders moved to `ModBlocks.{ENCHANTED_TRUNK,NEWTS_CASE}` (+ `_ITEM`).
- **`TrunkBlock` ctor decoupled**: now `TrunkBlock(TrunkTier radiusTier, int lockCount, TrunkArchetype)` — lockCount independent of size. Newt's Case = TIER_3 radius (large) + 1 lock + SCAMANDER_SANCTUARY (descends onto the shed roof via the existing SCAMANDER spawn offset).
- **Single-lock trunks (Traveller's, Newt's) get a Muggle-Worthy sneak-toggle** instead of lock-cycling (`TrunkBlockEntity.muggleWorthy`, synced to the bound `TrunkRecord`; blocks non-owner entry; carried on the packed BlockItem via `POCKET_MUGGLE_WORTHY` across pack-up). Multi-lock trunks keep sneak = lock-cycle.
- **Accio pocket-ingress spell** (`SpellCastUtilityHandler.handlePocketIngress`) repointed off the deleted `EnchantedTrunkItem`: it now finds any trunk `BlockItem` in inventory and enters that trunk's **lock-1** compartment (`TrunkBlock.lockCaseId(base, 1)`), so the spell and the placed block share one space per trunk. Honors Muggle-Worthy.
- **Lang**: `block.*` keys for enchanted_trunk + newts_case_item (en_us + provider); old `item.*` names for those now unused. **Loot**: `noDrop()` for both (code hand-drops the component-carrying item). **Models**: placeholder cubes in datagen.
- Newt's Case losses (cosmetic, accepted): enchanted-glint foil, item tooltips, and the two-step "click open then enter" flow — replaced by a straight sneak-toggle + descend. `NEWTS_CASE_MUGGLE_WORTHY` component left registered but unused (block uses `POCKET_MUGGLE_WORTHY`).

`compileJava` BUILD SUCCESSFUL.

## Addendum (2026-07-06, full audit): contract drift observed — recorded only, not corrected

- **Wand bone-model contract vs `WandTipWorldCache` (AUD-C-001):** the render pipeline contract expects a `wand_tip` bone for exact beam-origin capture (`WandTipWorldCache.WAND_TIP_BONE`), but the master `wand.geo.json` (47-bone/100-cube contract — verified intact otherwise: naming `<slot>_<variant>`, pivots root/handle Y=0, shaft/tip Y=11, tip anchors Y=23, 25 module bones 1:1 with `WandModuleRegistry`) ships **without** a `wand_tip` bone. Only the 5-bone `elder_wand.geo.json` skin has one (plus `knob`). Delta: either the geo is missing a contracted bone or the cache constant drifted from the intended `fx_tip_anchor`/`tip_*_anchor` (Y=23) anchors.
- **`WandRenderer` doc comment vs geo (AUD-C-007):** comment says variant bones are "flat siblings … not children of container bones"; geo has them parented under `handle`/`shaft`/`tip`/`core`/`ornament`. Behavior unaffected; documentation drift only.
- **Reload-listener directory contract (AUD-D-002):** `BrewReloadListener`/`BrewingRecipeReloadListener` declare `DIRECTORY = MODID + "/…"` (MODID-prefixed, i.e. `data/<ns>/wizards_and_beasts/…`) while the shipped data uses the un-prefixed convention every *other* listener uses (`bestiary/entries`, `broom_definitions`, `creatures`, `spells`, `vocations`, `handbook/chapters`, `wand_modules`). One of the two conventions is the contract; the brew pair drifted from it and its data never loads.
- **Recipe-data contract (AUD-D-001):** `WandmakingRecipeSerializer`/`Type` are registered as vanilla recipe plumbing (contract: JSONs in `data/<ns>/recipe/` with a `"type"` field), but the shipped JSONs live in `wandmaking_recipes/` without `"type"` — data authored against a loader that doesn't exist.

---

# Skill Definitions: Java → Datapack (Web Rework Phase 1, 2026-07-09)

Behavior-identical representational migration. Same 60 nodes, same string ids, same prerequisites,
levels, costs, trees, audiences, effects. Player attachments untouched (string-id-keyed
`unlockedSkills` map keys unchanged; no refund, no version bump).

- **Definitions moved Java → datapack.** The 8 static `*Skills.java` classes (60 `Skill.Builder`
  nodes) are deleted; nodes now load from `data/wizards_and_beasts/skill_nodes/<tree>/<id>.json`
  via `SkillNodeLoader` (`SimpleJsonResourceReloadListener`, `BroomDefinitionLoader` pattern).
  The JSONs were **generated, not hand-typed**: a one-off extraction test serialized the live Java
  objects through the new `Skill.CODEC` and asserted encode→parse→encode idempotence plus
  field-by-field equality before the Java classes were deleted (extraction test deleted after use).
  The node id is the JSON `id` field (plain string, byte-identical to the old ids), not the file path.
- **Prerequisite semantics (recorded for Phase 2, unchanged here):** ALL prerequisites in the list
  must be satisfied, and each prerequisite must be **maxed** (`PlayerSkillData.isMaxed`), not merely
  allocated (`SkillSystemAPI.evaluateUnlock`).
- **New capability: `/reload` hot-swaps skill definitions.** `SkillTrees` is now a volatile-swapped
  reload-backed registry (server map + client cache). `SyncSkillDefinitionsPayload` pushes the full
  definition list on player login and on `/reload` (`OnDatapackSyncEvent`, bestiary/handbook
  precedent), so changes appear client-side without relog.
- **Client definition source:** client GUI code (`SkillTreeScreen`, `SkillsTab`) now reads the
  synced cache (`SkillTrees.clientById`/`clientGetTree`) instead of the classpath statics; required
  for dedicated servers. Server logic keeps reading the server side (`byId`/`getTree`).
- **Missing-definition tolerance:** if a player attachment references a node id absent from the
  loaded datapack, every consumer already skipped unknown ids (`byId == null → continue`) — that
  behavior is preserved: the allocation stays in NBT (inert, not deleted) and effects simply don't
  apply. The loader logs unknown prerequisites (node becomes unobtainable, load continues) and
  duplicate ids (first definition wins).
- **Validation timing:** the old `SkillTrees.init()` threw at mod-init on an unknown prerequisite;
  a datapack cannot hard-crash the server, so the loader **logs an error instead of throwing**.
  A unit test (`SkillNodeJsonTest`) still fails the build if the *shipped* JSONs have duplicate ids,
  unresolvable prerequisites, or parse errors.
- **Ordering delta (cosmetic):** per-tree node lists are now sorted (tier, column, id) instead of
  Java class-registration order. GUI node positions are tier/column-driven and unchanged; only
  iteration order (e.g. `/wandb skill list <tree>` line order, SkillsTab chip order) can differ.
- **Vocation touch: none.** `VocationHelper`/`VocationManager` signatures consume `Skill` objects,
  which survived as the runtime type; no vocation file was modified.

---

# Skill Web Rework Phase 2: Prerequisite DAG → Adjacency Web (2026-07-09)

Deliberate behavior change (Phase 1 was representational; this is the mechanic swap).

- **Allocation semantics: ALL-prerequisites-MAXED → ANY-neighbor-level≥1.** Phase 1 audit
  recorded the old rule precisely: every listed prerequisite had to be at max level
  (`SkillSystemAPI.evaluateUnlock`, old lines 92–94). New rule: a node's *first* level is
  allocatable iff it is a `root` node OR any edge-neighbor is at level ≥ 1 (`not_adjacent`
  rejection reason replaces `missing_prerequisite:<id>`); further levels of a started node need
  only affordability + `< maxLevel`. Level ≥ 1 opens edges — maxing is never a gate. All other
  `evaluateUnlock` rules preserved verbatim (module gate, maxed, affordability, heritage-audience
  `isTreeAvailable`, vocation mastery-cap/opposition line).
- **Codec/JSON: `prerequisites` and `column` removed; `x`/`y`/`edges`/`size`/`root` added.**
  `tier` is **kept** as an inert vocation band index (VocationHelper:75/:134 consume it; it has
  zero layout/adjacency meaning and is scheduled for deletion in the Vocation reframe prompt).
  Prompt §6(b) said to remove `tier` too — deviation adopted per the agreed Option A after the
  Phase 2 stop-and-report: removing it would have redefined the Foundation/Mastery band mechanic
  (graph-depth substitution flips the secondary-vocation rule on `expecto_patronum_unlock` and
  `keeper_vigor`).
- **Migration: unconditional refund + earned clamp to 60.** `PlayerSkillData` schema v1 → v2 at
  login (player identity available for the required per-player log line): allocation map cleared,
  `earned = min(earned, 60)`, `unspent = earned`, version stamped on the instance and persisted —
  idempotent across relogs. The NBT-level migrator now stamps only structural versions (v1) so an
  intermediate save can't skip the behavioral migration. Entries referencing unknown ids are
  cleared with everything else (no per-node refund math — full refund is by construction).
- **Point cap introduced: `SkillSystemAPI.MAX_SKILL_POINTS = 60` (// TUNE).** Enforced in
  `PlayerSkillData.addSkillPoints` (grant clamps to remaining headroom; no-op at cap), so XP
  level-ups, heritage bonus, proficiency milestones AND admin `points add` all respect it.
  Admin `points set` still bypasses (unspent only — punchlisted).
- **8 trees → 3 per-audience webs** (wizard = 6 trees as regions of one coordinate space;
  goblin and elf standalone). `SkillTreeId` unchanged as region label (Vocation/OWL/audience
  code untouched). Edges are symmetrized at load; unknown/self/cross-audience edges log-and-drop;
  per-web unreachable-from-root nodes warn. Placeholder layout: wizard spokes at i×60°
  (SkillTreeId declaration order), radius 90 + 55·BFS-depth, ±25° sibling spread; goblin/elf
  rings at 70·depth.
- **Center node housing: `spell_mastery`** (§3.3 rule): `SkillTreeId` is a closed enum whose
  extension ripples beyond skill files (SkillsTab bars, OWLGradeCalculator, VocationRegistry
  tree mapping). One PLACEHOLDER node `wizard_core` (root:true, no effects, loud placeholder
  strings) with edges to all 6 former wizard tree roots. Former wizard tree roots lost their
  implicit root status (reachable via the center); goblin/elf former roots are their webs' roots.
- **Command changes:** `/wandb skill list [tree]` keeps its optional tree argument as a region
  filter (unchanged signature). `/wandb skill info <skill>` now prints a "Connected:" neighbor
  list (green = allocated) + a web-root marker instead of the prerequisite list. Unlock rejection
  message for adjacency: "<name> is not connected to your allocated nodes."
- **Iteration-order delta:** registry per-tree lists now sort by id (was tier,column,id in
  Phase 1; column removed, tier no longer a layout key). Affects only text-list ordering
  (`/wandb skill list <tree>`, SkillsTab chips).
- **Compile-forced Vocation touches:** `VocationUnlockStateTest.node()` helper —
  `.position(tier, 0)` → `.tier(tier)` (builder signature change). No production vocation file
  touched; `VocationHelper` reads `getTier()` exactly as before.
- **GUI:** `SkillTreeScreen` grid + per-tree tabs replaced by a single pan/zoom canvas over the
  viewer's audience web (drag-pan, cursor-anchored zoom 0.25×–2.0×, circles by `size`, level
  pips, lit edges when both endpoints ≥ 1, allocated/allocatable/locked states, tooltip, server
  roundtrip on click, earned/spent/cap footer). `SkillScreenRouter` untouched. The per-node
  texture set (`SkillTreeGuiTextures`) is no longer referenced — kept for the Phase 3 skin pass.

---

# Skill Web Rework Phase 3: Vocation Reframe — Identity Only (2026-07-10)

The web's travel cost under the 60-point cap now does the differentiation the Vocation enforcement
layer used to do; the mechanical layer is deleted. Vocation survives as a declared identity.

- **Vocation enforcement deleted.** The vocation step in `SkillSystemAPI.evaluateUnlock` is gone
  (mastery-band gate, secondary-vocation first-tier rule, opposition lockout, capstone gate). The
  remaining chain — module gate → maxed → affordability → adjacency → audience (incl. wandlore
  wand check) — is byte-preserved in order. Allocation outcomes differ from Phase 2 only where the
  vocation step previously rejected (`vocation_locked:*` reasons no longer exist).
- **`tier` removed from the `Skill` codec, builder, and all 61 node JSONs** (the deletion the
  Phase 2 doc block scheduled). One-off generated transform, asserted zero `tier` keys remain;
  transform deleted after use. `VocationHelper`'s band logic (:75/:134) — tier's only reader — died
  with it.
- **`VocationDefinition` schema: `foundationMaxTier` and `oppositions` removed** (codec + all 5
  vocation JSONs, same transform discipline). Opposition's readers were enforcement
  (`unlockState`, declare-time commit check) and the command's "foreclosed" display — all deleted,
  so the field died with them. `capstoneNodeId` is now **orphaned data** (its only reader was the
  deleted capstone gate); kept in the schema for future use, punchlisted.
- **Secondary declarations removed** (storage slot and flow). `PlayerVocationData` is
  primary-only; the codec still *reads* a legacy `secondary` key into a transient marker (never
  re-encoded), and a login migration logs one info line per affected player, strips all vocation
  attribute modifiers (including the old 0.5-scaled secondary profile) and re-applies the primary,
  then persists — the key is gone on next save, so it never re-fires. No version int needed: key
  presence *is* the migration marker.
- **`VocationManager`:** `Slot` enum gone; `commit(player, id)` declares the primary directly.
  Deleted with the mechanics: `SAME_AS_OTHER_SLOT`/`OPPOSED`/`RESPEC_REQUIRED` results, the
  mastery-progress overwrite guard, and `clear()`'s mastery-node refund sweep (clear now just
  strips the profile and the declaration — there are no vocation-locked nodes to refund).
- **Command changes:** `/wandb skill vocation set secondary <id>` removed; `set primary` and
  `info`/`clear` remain. `info` prints only the declaration (mastery/foreclosed lines gone).
  Dead lang keys removed; `info.primary`/`set.primary_ok`/`clear.ok` reworded only to stop
  referencing the deleted two-slot/refund model.
- **`VocationHelper` not collapsed:** it keeps a real surface (declaration queries,
  `hasGrantedAbility`, `vocationOf` home-region hook) so it stays as the query twin of
  `VocationManager`. `getSecondary` removed; `hasGrantedAbility` now reads the primary only.
- **Unchanged on purpose (open question for the future prompts):** `commitmentEffects`
  (attribute profile via `VocationEffectApplicator`) and `grantedAbilities`
  (`VocationAbilityHooks`) still apply on declaration — the prompt's checklist did not delete
  them, but they are gameplay effects, so "vocation = zero gameplay effect" is not fully true yet.
  Flagged in the report for an explicit ruling.
- **`VocationDataSyncS2CPayload`/`ClientVocationCache`:** secondary field dropped from the wire
  format and cache.

---

# Skill Web Rework Phase 4: Wizard Web Content — Geometry, Fillers, Constellations (2026-07-10)

Data + lang only; no code paths changed except the schema version bump. The canvas renders the
new datapack as-is; the star-chart skin is Phase 5.

- **Travel cost is the intended behavior change.** Notables no longer sit edge-to-edge: the
  Phase 2 prereq-derived edges are rewired through filler chains (§3.4 contract — every legacy
  notable–notable/core edge still connected, interiors all fillers, asserted in
  `SkillNodeJsonTest.legacyConnectivityContractHolds`). Reaching and clearing regions costs more
  points than in Phase 3; see the economy table in the phase report (worst: spell_mastery full
  clear = 59 of the 60 cap — knife-edge, flagged for design review).
- **Three cross-region pathways add connectivity that never existed:** wandlore↔herbology
  (arcane_reserve–potion_potency), herbology↔magizoology (harvest_bounty–beast_handler),
  alchemy↔wandlore (transmute_focus–quick_cast), each a 4-filler chain crossing an open border.
  All other borders sealed — Dark Arts (Serpens) reachable only via its own spoke; geometric
  isolation is in addition to Module.DARK_ARTS gating, never instead of it.
- **100 filler nodes added** (size "small", cost 1, maxLevel 1, exactly one effect each):
  14 per region (3-filler spoke trunk from Polaris + 11 woven into rewired edges), 4 per open
  pathway, 4 in the Polaris cluster. Effects derived at ≈25% of the region's typical notable
  magnitude, never invented (per-region stat menu; magnitudes // TUNE in the generator constants,
  now baked in JSON). Ids: `<tree>_minor_<stat>_<n>`.
- **wizard_core is now Polaris:** displayName is the lang key `skill.wizards_and_beasts.node.polaris`,
  gains one universal effect (+0.5 max_health // TUNE), still root, cost 1.
- **New nodes use lang keys in `displayName`** (`skill.wizards_and_beasts.filler.*`, `.node.polaris`)
  while the canvas still draws raw strings — keys render literally until Phase 5 translates them.
  Accepted interim state per the "no rendering changes" constraint; legacy notables keep their
  inline-English strings (punchlisted debt, untouched).
- **Constellation identities ship as data:** `skilltree.region.<tree>.constellation` → Orion
  (spell_mastery), Serpens (dark_arts), Virgo (herbology), Monoceros (magizoology), Fornax
  (alchemy), Sagitta (wandlore). Nothing reads them yet (Phase 5).
- **Schema v3 migration:** `PlayerSkillData.CURRENT_VERSION` 2 → 3; `needsWebMigration()` now
  compares against the constant, so the existing v2 login machinery (clear + full refund + clamp
  to 60 + per-player log + resync) re-fires exactly once for v2 saves. No migrator structural
  change (NBT migrator still stamps only structural v1).
- **Node totals:** 161 (51 wizard notables incl. Polaris + 100 fillers + 5 goblin + 5 elf). The
  prompt estimated ~46 notables/~147 total; the real Phase 1 inventory was 50 wizard notables, so
  totals land higher. Layout is fully deterministic (id-hash jitter); generator deleted after use.

---

# Skill Web Rework Phase 5: Star-Chart Skin (2026-07-10)

Pure client-side rendering pass — zero codec/JSON/server/networking changes. One functional fix,
otherwise cosmetic (the absence of behavioral deltas is the point of this entry).

- **Raw-lang-key rendering fixed** via translate-with-fallback:
  `SkillTreeRenderHelper.resolveDisplayName` (`I18n.exists ? I18n.get : literal`) applied at every
  client draw site — canvas tooltip title and SkillsTab unlocked-node chips. Phase 4 fillers and
  Polaris now show real names; legacy inline-English notables render byte-identically (the
  fallback IS the literal — the display-string debt stays punchlisted, untouched). Server chat
  messages (`/wandb skill` command feedback) still print raw keys for fillers — server-side, out
  of this prompt's scope, punchlisted.
- **Cosmetic reskin (no behavior):** night starfield tile (procedural, seeded, tiled at 1:1 with
  ~0.3× pan parallax), three drawn survey rings at the notable-band radii (120/200/280) centered
  on Polaris, nodes as tinted star sprites (core/ring/flare per size; state = brightness + shape:
  locked dim-ember dot, allocatable white core + region-tinted rim ring, allocated gold
  diffraction flare + hot core; distinct 8-point Polaris sprite), region tint map (locked palette
  incl. neutral tints for goblin/elf webs so they render with zero special-case code), ley-line
  edges (locked hairline / frontier region-tint lift / allocated gold 2px with a slow global
  alpha shimmer), constellation labels at cached region centroids (italic, tinted, fading out as
  zoom passes ~0.9× toward build view), star pips, gold-on-night footer and tooltip.
- **Textures:** 11 PNGs committed under `textures/gui/skill_tree/chart/`, generated
  deterministically by `tools/skill_chart_textures.py` (repo's existing Python tooling convention;
  regenerable). Sprites are white/grayscale, tinted at draw via the color `blit` overload —
  vanilla `GuiGraphics` + `RenderPipelines.GUI_TEXTURED` only, no custom pipelines.
- **Performance:** off-viewport nodes and edges culled before draw (±48px pad); label centroids
  and translated components computed once per graph sync (list-identity check), not per frame;
  no per-frame allocation added to the render loop.

## Addendum (2026-07-18, skill audience & access: tradition rule + capability gating)

Ruling encoded: **audience = tradition of origin; capability tags gate content within a web, never
the web itself.** Access expansion only — no allocation, point, or attachment state changes shape,
so there is no data migration (asserted: `PlayerSkillData` schema untouched, `CURRENT_VERSION`
unchanged). Existing players see zero behavior change on regions they already satisfied.

- **(a) Audience resolution is explicit and variant-aware; the default fallthrough is deleted.**
  `SkillTreeId.audienceForHeritage(Heritage)` (which silently returned `WIZARD` for everything
  non-goblin/elf) is replaced by `audienceForHeritage(@Nullable Heritage, @Nullable HeritageVariant)`
  + `audienceForVariant(HeritageVariant)`, backed by an explicit `EnumMap<HeritageVariant,Audience>`
  (all 30 variants) and an `EnumMap<Heritage,Audience>` null-variant fallback (all 10 heritages).
  Both maps are totality-checked in a static initializer — a missing entry is a **boot-time crash**,
  never a silent misroute. The `Audience` enum gains `VEELA, CENTAUR, MERPEOPLE, GIANT`. The ruling:
  wizardkind (incl. squib), werewolf, obscurial, vampire, half/quarter-veela, and half-giant →
  `WIZARD`; goblin/house-elf/centaur/merpeople, full-veela, and full-giant/clan-warden → their own
  audience. **clan_warden resolves to GIANT** (its data shows no human parentage — a role within
  full-giant clans, not a mixed birth; only `half_giant` is the mixed lineage). The four new
  audiences have no authored web yet; that is expected and inert (guarded below).
- **(b) Obscurial + squib gain partial wizard-web access (access expansion only, no loss).** Both
  resolve to the `WIZARD` audience and open the wizard chart. Within it, Virgo/Monoceros/Fornax
  (magizoology/herbology/alchemy, `Requirement.NONE`) are allocatable end-to-end from Polaris;
  Orion/Serpens (spell_mastery/dark_arts, `CASTING`) and Sagitta (wandlore, `WAND`) render sealed.
  Previously the client router denied these profiles the screen entirely (the `muggle_like` path) —
  that was the live OBSCURIAL bug. No prior access is removed for anyone.
- **(c) The hardcoded WANDLORE wand check is migrated into a general region-requirement mechanism.**
  Region capability is now a `SkillTreeId.Requirement` property (`NONE`/`WAND`/`CASTING`) on the
  closed tree enum (deliberately code, not datapack — trees are a closed set). `wandlore ⇒ WAND`,
  `spell_mastery + dark_arts ⇒ CASTING`, all others `NONE`. `SkillSystemAPI.isTreeAvailable` is now a
  pure audience check; the old `if (tree == WANDLORE) …` special case is deleted and re-expressed as
  `meetsRequirement(WAND, …)` = the identical predicate (`canUseWand && !no_wand`). `evaluateUnlock`
  gains a requirement step ordered strictly **after** the audience check (chain: module → maxed →
  affordability → adjacency → audience → requirements); everything before requirements is byte-
  preserved. Crafted payloads for sealed regions are rejected server-side (new reason
  `requirement_unmet`). New capability tag `no_casting` added to `squib` and both obscurial variants
  (`suppressed`, `unleashed`) via the existing `HeritageVariant` tag set; no other tag data touched.
- **(d) Client denial screen repurposed, not deleted.** `SkillScreenRouter` now routes purely by
  resolved audience: a player with a heritage always opens their chart (capability gating happens
  inside as sealed regions, keyed off the same synced heritage state the server enforces on — no
  client re-derivation from `canUseWand`). The `muggle_like` denial path is removed; the goblin/elf
  special-case branch collapses into the uniform route. `SkillAccessDeniedScreen` is retained solely
  for the no-heritage case (`no_type`) and the generic-error guard. Module-disabled gating stays
  server-authoritative (the router does not re-check `Module.SKILL_TREES`; the server returns
  `module_disabled`). Sealed regions render permanently-locked by **reusing** the existing locked
  ember node style (the seal cue lives in the tooltip line, a placeholder lang key
  `skilltree.region.sealed.tooltip` with a terse `"Sealed"` fallback — no flavor text authored here)
  and are non-interactive client-side.
- **(e) Per-audience point-cap hook added, all `60`, zero behavior change.** `SkillSystemAPI` gains an
  `EnumMap<Audience,Integer>` initialized to `MAX_SKILL_POINTS` for every audience (`// TUNE`),
  read via `pointCapFor(audience)` and branched at the `awardPoints` seam (`addSkillPoints(amount,
  cap)` overload; the old single-arg method delegates with `MAX_SKILL_POINTS`). Because every cap
  equals 60 today, earning is byte-identical — asserted by a unit test over all audiences.
- **Empty-web tripwire (guard for the guarded case).** `SkillNodeLoader.apply` warns loudly if any
  *committable* heritage (`isAlphaAvailable` = wizardkind/werewolf/obscurial, all → `WIZARD`) resolves
  to an audience with zero nodes. Coming-soon heritages mapping to the node-less new audiences are
  expected and stay unreachable (selection is gated on `isAlphaAvailable`); the tripwire is silent for
  them and never crashes.

## Addendum (2026-07-18, ability sources infrastructure: grant layer + refinement scoping)

Builds the ownership layer under player capabilities so the web can become a first-class grantor/refiner
without an unqueryable, revoke-unsafe grant landscape. **Zero content**: no new abilities, no authored
nodes, no refinement consumers. Two grantors re-plumbed behavior-identically as proof; the node-grant and
refinement mechanisms ship unused.

- **(a) Source-tracked grant layer, derived — not stored.** New package `ability.grant`: `AbilityKey`
  (normalized namespaced key), `AbilityGrants` (immutable `key → EnumSet<Source>` snapshot; sources
  `HERITAGE`/`VOCATION`/`SKILL_NODE`, enum with room to grow), `AbilityGrantService` (server query:
  `hasAbility`/`sourcesOf`/`hasFromSource`). Every query **recomputes** from the three already-persistent
  grantors (heritage variant tags, declared vocation `grantedAbilities`, allocated-node grant effects) —
  **no new `AttachmentType`, no schema change, no migration** (§3.1). This is the whole point: a source
  losing its input drops exactly that source's grants on the next recompute; revoke is subtraction by
  omission, so the desync-and-migration bug class never exists. Per the Upgrade License's cache latitude,
  the server keeps **no** stored cache (recompute is a handful of map lookups); the client gets a synced
  mirror (`ClientAbilityGrantState`) via one payload (`AbilityGrantsSyncS2CPayload`), re-pushed on the
  mutation seams (skill alloc/refund, vocation commit/clear, heritage commit, full-login) and repopulated
  on relog. There is no client consumer yet — the sync is the mandated forward hook.
- **(b) Vocation abilities re-plumbed onto the layer (proof #1), behavior-identical.**
  `VocationHelper.hasGrantedAbility` server path now resolves through
  `AbilityGrantService.hasFromSource(player, key, VOCATION)` instead of reading the vocation registry
  directly. The logic is identical (the layer computes VOCATION grants from the same primary vocation's
  `grantedAbilities`), so every reader — `VocationAbilityHooks`, the Magizoology/Herbology handlers, the
  corruption-accrual sites — resolves unchanged, but the grant is now revoke-safe by derivation (clearing
  the vocation drops exactly its grants). The logical/client fallback keeps the old direct read (all live
  readers are server-side). Asserted by the revoke-safety matrix test.
- **(c) Patronus gate NOT migrated (proof #2 → report-only).** Audit finding: `expecto_patronum_unlock`
  is a **cooldown-reduction node** (`spell_cooldown_reduction` on `expecto_patronum`), not an ability gate
  — the "unlock" in its id is a misnomer, and it grants no ability flag to migrate. The spell's actual
  availability is governed by its own learning/requirement config, unrelated to this node. Classified
  entangled/not-applicable; left untouched, punchlisted.
- **(d) Two new `SkillEffect` types (at the §-cap), zero content.** `grant_ability` (`GrantAbility`,
  an `AbilityKey`) is the clean forward path for node ability grants, read by the recompute as a
  `SKILL_NODE` source; `ability_refinement` (`AbilityRefinement` = key + `AbilityModifierAxis` + per-level
  magnitude) is the ability-scoped modifier, read via `AbilityModifiers.get(player, key, axis)`. Category
  scoping did **not** stretch to ability keys (different key space — `SpellCategory` enum vs arbitrary
  `AbilityKey`), so refinement took the second effect type. Both ship with **zero** authored nodes and
  zero consumers. The legacy free-string `unlock_ability` is untouched (still feeds `SkillEffectCache` and
  its 9 existing consumers) and is additionally **bridged** into the grant layer as a `SKILL_NODE` source,
  so existing node abilities are source-tracked without any behavior change.

---

# Unified Ability Framework — slots / wheel / keybinds (2026-07-19)

Framework-only build (no real abilities). Audit (§0) found a pre-existing `ability/` layer, so this
**extends** it rather than building greenfield. Data-shape deltas:

- **`AbilityGrants.Source` gains `DEBUG`.** New command-override + debug-auto-grant source bucket. Additive
  (appended after `SKILL_NODE`, ordinals stable). `AbilityGrants.of(3-arg)` kept verbatim for the existing
  `AbilityGrantsTest` + client mirror; a new `ofSources(Map<Source,Collection<String>>)` builder backs it.
- **`AbilityGrantService.compute` is now source-pluggable.** The three hardcoded readers moved into
  `AbilityGrantSource` implementations (`HeritageAbilityGrantSource`/`VocationAbilityGrantSource`/
  `SkillNodeAbilityGrantSource`) registered in `AbilityGrantSources`; `compute` iterates the registry. This
  is the §2.2 "generic AbilityGrantSource interface" — new grantors register without touching the resolver.
- **Heritage grant list: NO new field.** §2.2 asked for a `List<Identifier> abilities` on the heritage
  definition; the existing `HeritageVariant.getTags()` already feeds the heritage grant source, so the
  mandated "heritage source" is satisfied with **zero** heritage-schema change (audit stop-condition on
  heritage restructuring avoided).
- **New attachment `ability_selection`** (`AbilitySelectionState`: selected/pinned/toggles/cooldowns).
  Deliberately distinct from the legacy `ability.data.PlayerAbilityData` (per-ability Apparition/Legilimency
  fields) — the prompt's requested `PlayerAbilityData` name was already taken with different semantics, so a
  name collision was avoided. NOT `copyOnDeath`: `AbilityFrameworkEvents.onClone` copies selection/pin/
  toggles and drops cooldowns on death (§2.2 "cooldowns not carried across death"); disk persistence via the
  serialize codec is unaffected.
- **New datapack registry `abilities/`** (`AbilityDefinition`, id-from-file like nothing else here — the
  JSON body omits `id`, stamped via `withId` at load). Client-synced on `OnDatapackSyncEvent` (bestiary/
  handbook/skill precedent) so the wheel reads metadata client-side.
- **Command root deviation:** debug commands live under `/wandb ability` (the mod's real root), not the
  prompt's `/wizardsandbeasts ability` — the latter is a deprecated alias of the same dispatcher.
- **Keybind defaults:** `ability_wheel/use/quick` all default UNBOUND (ANIMAGUS_* convention) — no collision
  with the pre-existing Apparition(R)/Legilimency(H)/Animagus/Obscurial binds, which are **left untouched**
  (absorb-into-wheel deferred to a future migration prompt, per user decision).

# Ability Migration (Apparition / Legilimency / Animagus) — §0 audit, HALTED (2026-07-19)

No data-shape change made. Recording the audited shapes so the halt is resumable.

## Interim tag→ability grant rule (§0.1) — as found at HEAD

Exact rule, end to end:

1. `HeritageAbilityGrantSource.grantsFor(player)` → `HeritageAPI.getPlayerHeritageVariant(player).getTags()`
   (a `Set<String>` hardcoded in the `HeritageVariant` enum constructor), returned verbatim.
2. `AbilityGrants.add` → `AbilityKey.of(raw)` → `raw.trim().toLowerCase(Locale.ROOT)`. **No namespacing,
   no prefix stripping, no aliasing.**
3. `AbilityResolver.keyOf(def)` → `AbilityKey.of(def.id().toString())` → the **namespaced** form,
   e.g. `wizards_and_beasts:apparition`.

So a heritage tag grants an ability **iff the tag string is literally equal (case-insensitively) to the
ability's full namespaced id**. It is an identity string match — 1:1 in form, but across two different
string spaces.

**Full list of tags that currently resolve to abilities: none.** Every shipped tag is bare:
`enhanced_bond, dark_resistance, no_wand, no_casting, nature_speech, moon_sensitive, transformation,
obscurus_form, vault_access, rune_affinity, innate_apparition, sunlight_weakness, blood_hunger,
water_breathing, divination_sight`. None contains a `:`; none equals a registered ability id
(`wizards_and_beasts:debug_active|debug_passive|debug_toggle`). The heritage grant path is **inert at HEAD**
— it feeds the key space but grants zero abilities.

Consequence for §2.1: there is no tag-granting behaviour to migrate off, only dead code to delete. And the
replacement design's premise ("heritage JSONs currently granting abilities via tags") does not hold.

## Heritage definition shape (§2.1 blocker)

`HeritageVariant` is a Java `enum` — 31 constants, ctor
`(String id, Heritage parent, String displayName, String description, double healthMod, double speedMod,
double armorMod, int uiColor, Set<String> tags)`. `Heritage` is likewise an enum. **No codec, no
`data/**/heritage/*.json`, no reload listener.** Adding `List<Identifier> abilities` means adding a 10th
enum ctor arg across 31 constants — a Java-source edit, not a datapack edit. Files that would change:
`heritage/HeritageVariant.java` only. **Third-party heritage packs: none possible** — heritage is not
datapack-driven, so there is no external pack surface and no migration note is owed.

## Framework shapes the migration would need to touch (not yet touched)

- `AbilityTriggerHandler.use(ServerPlayer, boolean quick)` — carries **no** target/charge payload.
  Apparition needs `(BlockPos, Vec3)`; Legilimency needs an entity id.
- `AbilityTriggerHandler.flipToggle` — writes `AbilitySelectionState.toggles` before calling
  `AbilityBehavior.onToggle`, so a TOGGLE ability cannot delegate its state to an external owner.
- `AbilityBehavior` — `onActivate(ServerPlayer, AbilityDefinition)` / `onToggle(…, boolean nowOn)`; no
  target parameter, no cancellation of the pre-written toggle.

## Resolution — implemented after direction (2026-07-19)

User picked option (a) on all four stops: extend the framework's trigger path, read-through toggle
ownership, and a status-based grant source with **no** heritage schema change. Data-shape deltas:

- **`AbilityDefinition` gains `input`** (`AbilityInput{targeting, chargeTicks, range}`, optional, defaults to
  `AbilityInput.NONE`). Existing JSONs (`debug_*.json`) are unchanged and keep press-to-fire semantics. New
  enum `AbilityTargeting{none,block,entity}`. `AbilityDefinitionsSyncS2CPayload` grew three trailing fields
  (targeting name, chargeTicks, range) — the wheel client needs them to know what to pick and how long to
  hold. **Wire-incompatible with the previous build**; framework-internal, no external consumers.
- **`AbilityUseC2SPayload` gains `AbilityTarget target`.** New value type `ability.trigger.AbilityTarget`
  (`Kind{NONE,BLOCK,ENTITY}` + BlockPos/Vec3/entityId, own `StreamCodec`). Always sent, `NONE` for untargeted
  abilities, so the wire shape is fixed. Server re-validates kind + distance
  (`AbilityTriggerHandler.validateTarget`, `range + 4.0` slack) and drops mismatches — it never approximates.
- **`AbilityBehavior` gains three defaulted members**: `onActivate(player, def, target)` (defaults to the
  2-arg form, so existing behaviors are untouched), `ownsToggleState()` and `isToggledOn(player, def)`.
- **Toggle single source of truth.** `AbilityTriggerHandler.flipToggle` now skips writing
  `AbilitySelectionState.toggles` entirely when the behavior owns its state, and re-syncs afterwards so a
  refused toggle mirrors correctly. Wheel display reads `AbilityResolver.activeToggles(player, state)` — the
  framework-owned bits **unioned with a live read of each owner**, derived at sync time and never stored.
  Animagus therefore keeps exactly one persisted copy: `PlayerAbilityHelper.isCurrentlyTransformed`.
- **`AbilityGrants.Source` gains `STATUS`** (appended; ordinals stable). New
  `PlayerStatusAbilityGrantSource` grants `wizards_and_beasts:apparition|legilimency|animagus_form` by
  delegating to `ApparitionServerLogic.canApparate` / `LegilimencyServerLogic.canLegilimise` /
  `AnimagusTransformService.canTransform`. It duplicates no rule — each predicate lives with its owner and
  the server logic remains the authority (it re-runs every check, with its per-reason feedback, on trigger).
- **`HeritageAbilityGrantSource` deleted**, unregistered from `AbilityGrantSources`. `Source.HERITAGE` is
  retained (ordinal stability + the pure `AbilityGrants.of(3-arg)` builder the existing test exercises) but
  has no built-in source: the bucket in `AbilityGrantsSyncS2CPayload` is now always empty. **Heritage schema
  unchanged** — no `abilities` field, no enum ctor argument, no JSONs (there are none). Tags are tags again.
  Third-party heritage packs: none possible, heritage is not datapack-driven.
- **Predicates promoted, logic untouched:** `ApparitionServerLogic.canApparate` extracted (elf-Apparition OR
  test+licence+heritage — the same rule `handleRequest` enforces, minus transient checks and per-reason
  messages, which stay in `handleRequest`); `LegilimencyServerLogic.canLegilimise` widened to public.
- **Three C2S payloads deleted** — their only senders were the deleted keybinds:
  `ApparitionRequestPayload`, `LegilimencyRequestPayload`, `AnimagusTransformC2SPayload` (+ their
  registrations in `ModNetworkAbilities` / `ModNetworkForm`). The S2C ward/vision syncs and
  `AnimagusAbilityC2SPayload` (the per-form beast burst, out of scope) stay.
- **New datapack files** `abilities/apparition.json`, `abilities/legilimency.json`,
  `abilities/animagus_form.json` — module `wizards_and_beasts:player_abilities` (mirrors the existing
  `Module.PLAYER_ABILITIES` gate on all three), `cooldownTicks: 0` on all three (each already owns a
  persisted cooldown; none invented), sortOrder 10/11/12, inputs `block/20t/32` and `entity/30t/8` matching
  the deleted controllers' `CHARGE_TICKS`/`HOLD_TICKS`/pick ranges exactly.
- **Lang:** removed `key.wizards_and_beasts.apparate`, `key.wizards_and_beasts.legilimency`,
  `key.wizards_and_beasts.animagus_transform` (orphaned by the keybind deletion); added
  `ability.wizards_and_beasts.{apparition,legilimency,animagus_form}` + `.desc`. No lore authored — the
  descriptions state the input contract only. `key.wizards_and_beasts.animagus_ability` retained (that bind
  survives).
- **Client:** `AbilityWheelController` became the single input driver (press-to-fire for uncharged
  abilities; hold → re-pick each tick after the charge window → fire on release for charged ones). New
  client-only `ClientAbilityChargeState` exposes the in-progress charge so preview renderers do not own
  input. `ApparitionClientController` is now render-only and draws its destination ring off that state
  (ward/licence colouring unchanged). `LegilimencyClientController` deleted (it was input only).

# Ability Migration pass 2 — Obscurial + Animagus beast ability, and quick slots (2026-07-19)

Follow-up to the three-ability migration: the remaining five keybound abilities moved onto the wheel, and
the framework's single pin became three quick slots so instant combat abilities stay one-press.

## Quick slots (framework)

- **`AbilitySelectionState.pinned` → `Map<Integer, Identifier> quickSlots`**, `QUICK_SLOT_COUNT = 3`,
  `SLOT_SELECTED = -1` meaning "the armed selection". New accessors `quickSlot(int)`, `slotOf(id)`,
  `withQuickSlot(int, id)`; an ability occupies at most one slot (re-binding moves it).
- **Save migration:** the codec still *reads* the legacy `pinned` field and folds it into slot 0
  (`mergeQuickSlots`), and never writes it back. Explicit `quickSlots` wins if both are present. Serialized
  as a list of `{slot, ability}` records rather than an int-keyed map, because NBT compound keys are strings.
  Locked by `AbilitySelectionStateTest`.
- **`AbilityUseC2SPayload.quick` (boolean) → `slot` (byte).** Decode clamps anything outside
  `[0, QUICK_SLOT_COUNT)` to `SLOT_SELECTED`, so a bogus slot can never address a quick slot.
- **`AbilitySelectionSyncS2CPayload.pinned` → `quickSlots`**, a fixed-length vector of
  `QUICK_SLOT_COUNT` entries (empty string = unbound slot).
- **Keybinds:** `ABILITY_QUICK` → `QUICK_SLOTS[]` = `ability_quick_1..3`. The framework keys now ship with
  **defaults** instead of unbound, reusing the keys this migration freed so each keeps its old meaning:
  wheel `V`, use `R`, quick slots `N`/`M`/`B`. Clients that already ran the previous build keep their
  persisted unbound state for `ability_wheel`/`ability_use` (see punchlist).
- **Wheel UI:** right-click now *cycles* the hovered ability through the slots (1 → 2 → 3 → unbound) instead
  of toggling one pin; the "P" badge became the slot number. `AbilitySelectionC2SPayload.Action.PIN` →
  `QUICK_SLOT`, `AbilityTriggerHandler.togglePin` → `cycleQuickSlot`.

## Five more abilities migrated

New ids in `AbilityIds`: `animagus_beast_ability`, `obscurial_form`, `obscurial_stress_vent`,
`obscurus_surge`, `obscurus_grasp`. All untargeted, no charge, `cooldownTicks: 0` (each already owns a
cooldown: `AnimagusAbilityService.COOLDOWN`, `ObscurialResourceManager`, `PlayerSpellData`).

- **No `module` field on any of the five** — the deleted binds and their handlers had no `ModuleManager`
  check, and mirroring current gating exactly is the rule. (Note the asymmetry: `animagus_form` *is*
  `player_abilities`-gated because `AnimagusTransformService` checks it, while the beast ability is not.)
- **Server logic extracted verbatim** out of the payload handlers, which were the only place it lived:
  new `ObscurialServerLogic` (`toggleForm` / `stressVent` / `useAbility`, plus `isDarkForm` and the
  `canToggleForm` / `canStressVent` / `canUseAbilities` permission predicates) and
  `AnimagusAbilityService.useActive(player)` / `canUseActive(player)` (the transformed-form guard, lifted
  from `AnimagusAbilityC2SPayload`).
- **`obscurial_form` is a second `ownsToggleState()` behavior** — truth is the active form id in
  `PlayerHeritageData`, read by transitions, renderers, spell policy and the resource manager. Same
  read-through as Animagus; no second persisted copy.
- **Form-scoped visibility:** the beast ability is granted only while transformed, the Obscurial combat
  abilities only in obscurus form, stress vent only outside it. That mirrors the binds, which silently did
  nothing outside their form — now the wheel simply does not offer them.
- **Deleted:** keybinds `OBSCURIAL_TOGGLE` (V), `OBSCURIAL_STRESS_VENT` (B), `OBSCURIAL_ABILITY_PRIMARY`
  (N), `OBSCURIAL_ABILITY_SECONDARY` (M), `ANIMAGUS_ABILITY`; controllers `ObscurialInputController` and
  `ObscurialAbilityInputController`; payloads `ObscurialToggleFormC2SPayload`,
  `ObscurialStressVentC2SPayload`, `ObscurialAbilityUseC2SPayload`, `AnimagusAbilityC2SPayload` (+
  registrations); lang keys for the three binds that had them (the two Obscurial combat binds never had
  translations — a pre-existing gap, now moot). `SpellKeyBindings` is down to menus and spell input.
- **`InputPolicy.canToggleObscurialForm` / `canUseStressVent` deleted** — client-side copies of a decision
  the grant layer now makes server-side.
- **Test delta:** `PacketCodecBoundsTest.obscurialAbilityUse_decode_rejectsOversizedAbilityId` is gone with
  its payload; the client no longer sends an ability-id string at all for these. Replaced by two tests on the
  successor invariant — `AbilityUseC2SPayload` slot clamping.

# Wheel input rework, beam render fixes, Apparition destinations (2026-07-19)

## Wheel input

- **Tap vs hold is now classified by the wheel itself.** `AbilityWheelScreen.tick()` watches the physical
  key for `HOLD_THRESHOLD_TICKS` (4): released sooner = a tap, so the wheel *latches open*; still held =
  a hold, so releasing confirms the hovered entry as before. Previously a tap opened and closed in the same
  frame, which read as "the key does nothing".
- **The wheel opens on a rising edge**, not on `isDown`, tracked with a raw GLFW poll in
  `AbilityWheelController` (`KeyMapping.isDown()` does not report keys held across an open `Screen`).
  Pressing the wheel key again closes a latched wheel; `suppressOpenUntilRelease()` stops the still-held key
  from immediately reopening it.
- **Hover arms the hovered ability** with no click, via a new `AbilitySelectionC2SPayload.Action.SELECT` to
  `AbilityTriggerHandler.arm`. Deliberately *not* `CONFIRM`: confirm **flips** a TOGGLE, so sweeping the
  cursor past the Animagus or obscurus entry would have transformed the player. `SELECT` only moves the
  armed slot. The new enum constant is appended, so ordinals stay stable.

## Beam rendering

- **Endpoint.** The client resolved the beam ray against `BeamSettings.range` (a fixed 50-block client
  slider) and truncated afterwards, while the server resolves against `min(spellRange * wandRangeStat,
  grown reach)`. The visual therefore overshot whatever the server actually hit. `WandBeamRenderer` now
  mirrors `WandBeamChannelLogic` exactly: clamp first, resolve against the clamped reach. The free slider
  is only used by the debug force-beam path, which has no spell.
- **Jitter.** `buildBeamPath` scaled its lateral noise by the *live* beam length, so the whole bolt breathed
  and swam as the beam grew and the aim moved, even though the random walk itself is seed-stable. Amplitude
  is now an absolute width in blocks (`LATERAL_NOISE_BLOCKS`). The perpendicular basis also snapped through
  90 degrees as the aim crossed vertical; it now picks the reference axis the direction is least aligned with.
- **Glow.** `applySpellColor` lerped mid/outer hard toward white (`c * 0.35 + 0.65`), so on an additive
  render type the two widest layers were nearly white for every spell - a pale wash with a thin coloured
  thread inside. They now keep their hue and only lift brightness. It is also applied **only when the spell
  colour changes**, instead of every frame, which had been stomping live tuning from the beam debug screen.
- **Origin is NOT changed.** It resolves from the GeckoLib wand-tip bone cache with a hand-math fallback;
  which of the two is misbehaving cannot be told from source alone. Logged as a punchlist item.

## Apparition destinations

- **New attachment `apparition_points`** (`PlayerApparitionPoints`: a capped list of `ApparitionPoint`
  = name + dimension + position + yaw). `copyOnDeath` - dying does not make a wizard forget where places
  are. Names are trimmed, whitespace-collapsed and truncated to 32 chars at construction; the list caps at
  12 and replaces by name rather than duplicating.
- **New ability `apparition_travel`** (ACTIVE, untargeted, `sortOrder` 9, same `canApparate` grant as
  `apparition`, `cooldownTicks` 0). Its behavior only sends `OpenApparitionSelectorS2CPayload`; the jump
  happens when the player picks a destination. Making it a second wheel entry avoids special-casing an
  ability inside the generic input driver.
- **`ApparitionServerLogic.travelTo`** re-runs every gate `handleRequest` does - module, licence/test/
  heritage, splinch, cooldown, destination wards, side-along pickup - and differs only where a known
  destination genuinely differs from a line-of-sight hop: no range clamp, same-dimension only, saved facing
  restored, and splinch risk scaled by distance (1x at the aimed-hop range, rising per
  `DISTANCE_RISK_BLOCKS` = 900 to a `MAX_DISTANCE_RISK` = 3x ceiling). `performApparition` gained a risk
  multiplier parameter; the existing 3-arg overload keeps aimed Apparition byte-identical at 1.0.
- **`ApparitionTravelC2SPayload` sends an index, never a position** - the server re-reads the destination
  from its own copy of the list, so a crafted packet cannot invent somewhere to teleport to.
- **`/wandb apparate mark|forget|list`** - deliberately *not* permission-gated, unlike the ward/test
  commands next to it: this is ordinary gameplay and each subcommand only touches the caller's own list.
- `ApparitionPointsSyncS2CPayload` rides the existing ability-framework sync seam (login/reload/respawn).

# Module State Backend (Prompt A) — 2026-07-19

Module state becomes server-authoritative, per-world persistent and synced. Gating semantics are unchanged.

## `ModuleState` — new type, fourth state added

`ModuleManager.State` was a nested 3-value enum (`DISABLED/ENABLED/PREVIEW`). There is now a top-level
`ModuleState` with `COMING_SOON` added, plus `grantsAccess()` and `isOperatorSettable()`.

`COMING_SOON` is **inert at every gate** — `grantsAccess()` is false for it exactly as for `DISABLED` — so
introducing it moved no behaviour. It differs only in being refused by the mutation path: a roadmap marker
is a statement about the build, changeable in code or in the config seed, not by an operator at runtime.

`ModuleManager.State` is **retained as `@Deprecated`** with a `toModuleState()` bridge so any reference to
the old nested type still compiles.

## Hardcoded defaults → config seed

The static initialiser's table moved verbatim to `ModuleDefaults.SHIPPED`, and `ModuleConfig` declares one
config entry per module under a `moduleDefaults` section of the **existing COMMON spec** (the mod has no
SERVER spec; standing one up would have been the first of its kind). The config is read **only** when
seeding a world that has no stored state — editing it later does not reach into existing worlds.

**Every default is preserved exactly**, including two that were tempting to change and were not:

- `AZKABAN` and `CHAMBER_OF_SECRETS` stay `DISABLED`. `COMING_SOON` describes them better, but it would take
  them out of an operator's reach — a behaviour change this prompt does not authorise.
- `MINISTRY` was **absent** from the old table, so it resolved through `getOrDefault` to `DISABLED`. Kept
  `DISABLED`. Worth stating plainly: **the Ministry system committed earlier today has therefore never been
  active** — `TraceService.isActive()` has been false since it was written. Turning it on is
  `/wandb module set ministry preview` or a config edit.

A test pins the whole table against the pre-refactor values, so a future edit that moves a gate fails loudly.

## Where state lives

`ModuleStateData extends SavedData`, overworld-pinned via
`level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE)` — matching `FlooNetworkManager` and
the six other `SavedData` users. Per-world by construction: two saves can differ.

Persisted keys are `ModuleIds` identifiers (`wizards_and_beasts:<lowercased enum name>`, the same spelling
`AbilityResolver` already resolves a datapack `module` field by), not ordinals — so reordering the `Module`
enum is safe. **Renaming a constant changes its id and orphans stored state**; unknown ids are dropped with
a warning rather than guessed at, and a module absent from a save falls back to its config default rather
than reading as `DISABLED`.

## `ModuleManager` is now a cache, not the truth

Authority is `ModuleStateData` (server) or the last sync (client). `ModuleManager` remains a static read
cache in front of it because the gate call sites include per-tick paths that must not do a level lookup.
`acceptAuthoritative` is the only way in, called by `ModuleStateService` after persisting and by the client
sync handler.

`isEnabled`/`isPreview` keep their exact signatures. **No gate call site was touched**: a before/after diff
of every `ModuleManager.isEnabled|isPreview` call across the tree is identical except `ModuleCommands`,
which was rewritten deliberately. 126 calls across 81 files, down from 128/82 only because the command's own
two calls became `state()`.

`setState(Module, State)` survives as `@Deprecated` — it writes the cache only and is reverted on the next
refresh. It has no callers left.

## One mutation path, two doors

`ModuleStateService.setState/setSetting` validates, persists, refreshes the cache and broadcasts. The
command tree and the C2S packet both call it, so neither can skip a step the other performs. `COMING_SOON`
is refused in **either direction** at that single point.

## Networking

- `ModuleStateSyncPayload` (S2C) — full snapshot of states + settings. Sent on `ServerStartedEvent`
  broadcast, on `PlayerLoggedInEvent`, and after any accepted change. Full snapshot because the map is one
  short entry per module and a snapshot cannot drift the way a delta stream can.
- `ModuleUpdateRequestPayload` (C2S) — state or setting change. The permission check in its handler is the
  real boundary; a request from a non-operator is dropped **silently** (no probe feedback) and logged at
  WARN with the player name. Bound to `Permissions.COMMANDS_GAMEMASTER`, the same node the command tree
  uses — the prompt says "level 2", the codebase speaks in the named node.
- Registered through `ModNetworkModules` off `ModNetwork.register`, matching every other domain.

## Settings framework — framework only

`SettingDefinition` is a sealed interface over `BoolSetting`, `IntRangeSetting`, `DoubleRangeSetting` and
`EnumSetting<E extends Enum<E> & StringRepresentable>`; each carries its key, translation key, default and
its own value `Codec`. `ModuleSettingsValues` stores values in **encoded** form rather than as `Object`, so
the store round-trips and syncs a value whose type only the definition knows, with no cast anywhere.

On load: unknown keys are dropped with a warning, out-of-range values are clamped and the clamp is logged,
missing keys read as their default. **Every module's schema is empty** — no setting is wired to gameplay,
which is the prompt's scope. The framework is proven by tests that register a schema for the duration of a
test and drop it after.

## Command surface

`/wandb module` gained `set <module> <state>` and `setting <module> <key> <value>`; `list` now shows the
state plus any schema values, and marks `COMING_SOON` entries `(locked)`.

The prompt specified `/wizardsandbeasts modules …` (plural). **Extended the existing `/wandb module` tree
instead**: `/wizardsandbeasts` is a deprecated alias that builds the same tree, so a plural `modules` node
would have shipped two near-identical admin surfaces. The old positional form
`/wandb module <module> <state>` was **removed** in favour of the explicit `set` — it conflicted with the
new `set`/`setting`/`list` literals.

## Mod administration is UUID-gated (2026-07-19/20)

Operator permission alone no longer decides who may administer this mod. `command/AdminAccess` is the single
rule, exposed as `WizardsAndBeastsCommandPermissions.ADMIN`:

- a **non-player source** (console, command block, RCON) always qualifies;
- a player whose UUID is in `adminUuids` qualifies, **operator or not**;
- if the list holds no valid UUID it is treated as unconfigured, and operator permission applies as before.

A list holding a valid entry is a **closed allow-list**: an operator who is not on it is refused exactly
like anyone else. The two escape hatches above exist so a typo cannot permanently lock a server out of its
own settings — the console is the recovery path, and a list of only junk degrades to the old behaviour
rather than to "nobody".

**Scope: all 37 `/wandb` command gates**, across 21 files — skills, creatures, wands, spells, floo, heritage,
azkaban, owls, brooms, trunks, stats, bestiary, proficiency, blood pacts, apparition, animagus, forms, sizes,
wand config, the ability framework and modules. Enforced again server-side on
`ModuleUpdateRequestPayload`, since the module screen will be a client and a screen that refuses to open is
only a courtesy.

Two uses of the raw operator check survive, both deliberately:

- `AdminAccess` itself, as the unconfigured-list fallback;
- `MinistryPermissions.holds`, so **in-world Ministry rank keeps working**. An Auror arresting someone is
  roleplay authority, not mod administration, and a server's own staff should be able to act as the Ministry
  without being a mod admin. The Ministry's op-only debug drivers (`notoriety`, `offence`) *did* move to
  `ADMIN` — driving someone's notoriety directly is authoring, not policing.

`adminUuids` moved from the module-scoped config to the mod-wide `Config`, since it now governs far more
than modules, and ships containing `d3c9ca09-8f15-4e59-96dd-1c0fab339fcd` and
`c9e6dcee-95f6-4d14-a980-5008ff72676e`.

Worth stating plainly, because it is a distribution-visible consequence rather than a local convenience:
**on a server run by anyone else, those two accounts are the only ones able to use any of this mod's admin
commands** until an operator edits the config from the console. That is the intended behaviour; server
owners who do not want it replace the entries with their own or clear the list.

The rule is a pure function (`AdminAccess.decide`) over the UUID, the configured list and the operator flag,
so every branch — including both lock-out guards — is unit-tested without a server.

## Config screen leak + missing Ministry handbook chapter (2026-07-20)

**Config screen.** `WizardsConfigScreen` auto-scans the *entire* `Config.SPEC` — every key appended to the
shared `ModConfigSpec.Builder`, from any file. Today's admin-UUID gating added `adminUuids`
(`ConfigValue<List<...>>`) directly to that builder, with no entry in `CATEGORY_BY_KEY`. It fell into the
"Gameplay" fallback bucket and rendered as a non-editable `[2 entries]` box labelled "Admin Uuids" —
a security allow-list surfaced on the generic Mods-list config screen, reachable by any player, sitting
between spell-cooldown sliders. Excluded by key (`EXCLUDED_KEYS`) and, defensively, by the `moduleDefaults`
prefix the per-world module seed lives under — that block should already be invisible to this screen (it is
a nested config section, not a top-level `ConfigValue`), but the exclusion no longer depends on that being
true. Both admin surfaces already have a real home: `/wandb module` and the config file directly.

While there: five pre-existing keys (`showSpellHudOverlay`, `reduceScreenEffects`, `apparitionRangeBlocks`,
`apparitionCooldownTicks`, `apparitionSplinchBaseChance`) were also missing from `CATEGORY_BY_KEY` and were
silently defaulting into "Gameplay" — a reasonable home, but by accident rather than by review. Added
explicitly.

Not touched: the "Dark Arts" category is permanently empty — no config key maps to it in `CATEGORY_BY_KEY`,
so its card never renders even though `openCategory` still special-cases it behind `DarkArtsGateScreen`.
Pre-existing, unrelated to today's work, and harmless (the empty-bucket guard just skips the card). Logged
in the punchlist rather than fixed, since it wasn't part of what broke.

**Ministry handbook chapter.** The handbook (`item.wizards_and_beasts.ministry_handbook`, styled throughout
as issued by "the Department of Magical Law Enforcement") had ten chapters and a closing-notices page, but
none of them documented the Ministry law-enforcement system itself — the Trace, notoriety vs. the permanent
record, wanted bands, pardons, ranks, or the licence/registration split — despite that system existing and
`notices.json` referencing "the Department of Magical Law Enforcement" by name. New chapter
`ministry_law.json`, sort_index 105 (between Bestiary Cross-Reference at 100 and Closing Notices at 110):
"Part XI — The Trace, Notoriety & the Register". Documents only what is actually built today — the Trace,
the notoriety/record split, wanted bands, pardon semantics, rank authority, and the existing
licence/registration commands. Deliberately does **not** claim Aurors patrol or that Azkaban sentencing
happens: that enforcement half (Phase 2) is not built yet, and the book should not promise a mechanic that
does not exist.

## Handbook accuracy pass (2026-07-20)

Audited the previously-flagged lore items first: bestiary lore/mmRatings (Niffler, Thestral, Hippogriff),
Patronus non-caster heritage mappings, and Avada Kedavra's sound/prerequisite were **already fixed** in
earlier sessions — verified against current source, no action needed. The bestiary's `mmRating` scale now
runs 0–9 (`sea_serpent` is 9, `toad` is 0), so it has deliberately outgrown FB&WTFT's 1–5 X-rating and a
further audit against the book would be comparing against the wrong scale.

Found instead, by reading the handbook chapters against the systems they describe:

- **`spells.json` claimed an unbuilt mechanic.** "Restricted Spells" stated Unforgivable use "carries an
  automatic sentence of life imprisonment in Azkaban" — flatly false: that's Ministry Phase 2 (Aurors,
  arrest, sentencing), which does not exist yet, and it directly contradicted the discipline the new Part XI
  chapter deliberately held. Rewritten to describe what's actually true (the Trace, the permanent Record)
  and cross-reference Part XI, matching the book's existing cross-referencing style.
- **`heritage.json`'s category list didn't match `Heritage.java` at all.** It named "Wizard-born, Muggle-born,
  Half-blood, Pure-blood, Veela, Goblin-kin, Centaur, Werewolf, Vampire, and Metamorphmagus" as the ten
  categories — conflating four `HeritageVariant`s of Wizardkind with top-level categories, omitting Obscurial,
  House-Elf, Giant and Merpeople entirely, and including Metamorphmagus, which is not a `Heritage` constant.
  Replaced with the real ten (`Heritage.values()`) and a line distinguishing Variant from Category.
- **Three "Special Magical Abilities" entries claimed a grant path that has zero callers.**
  `PlayerAbilityHelper.setMetamorphmagus` / `setParseltongueSpeaker` / `setWandlessCastingLevel` /
  `setWolfsbaneActive` are each called from nowhere but their own definitions — no `HeritageVariant` tag,
  skill node, or command sets any of them. The chapter asserted "available only to registrants of the
  appropriate Heritage Variant" (Metamorphmagus), "Heritage-linked" + a `$$` chat prefix (Parseltongue,
  the prefix also unwired), "Heritage-gated" (Wandless Casting), and a forced full-moon transformation gated
  by a Wolfsbane Potion item and a Werewolf Registry (none of which exist — `moon_sensitive` is read exactly
  once, by wand-compatibility scoring, not a transformation system). All four rewritten to state plainly that
  the mechanic is not currently issued/enforced, in the book's own dry bureaucratic voice, rather than
  asserting a specific grant path that isn't real.
- **Left alone, checked and found accurate:** Legilimency & Occlumency (genuinely heritage-gated, fixed
  earlier this session), Vampire/Veela "Heritage-specific passives" (true — `Heritage.java` carries real
  per-heritage `baseHealth`/`baseSpeed`/`baseArmor` deltas), Centaur Divination (the `divination_sight` tag
  is real and heritage-carried), Protego's Unforgivable-bypass claim (`ProtegoShieldEntity` does check
  `Spell.isUnblockable()`).

---

# Canon Correctness Pass — 2026-07-25

Data-correctness + codec-hardening + lore-externalisation pass. No architectural refactor. Every
delta below is behavioural or player-visible.

## Bestiary — Ministry of Magic classification (player-visible)

`mmRating` is the integer form of the five-grade Ministry scale (X…XXXXX) from *Fantastic Beasts and
Where to Find Them*. Valid domain is 1–5. `mmRating` has exactly one reader — `BestiaryScreen` — so
these are display/datapack-metadata changes with no gameplay effect (no spawn weight, damage
scaling, discovery tier, or loot branches on it).

**Out-of-scale values (were impossible):**

| Entry | Was | Now |
|---|---|---|
| `sea_serpent` | 9 | 3 |
| `griffin` | 6 | 4 |
| `hippocampus` | 6 | 3 |
| `snallygaster` | 6 | 4 |
| `tebo` | 6 | 4 |
| `wampus_cat` | 6 | 5 |

**Canon mismatches (50):**

| Entry | Was → Now | Entry | Was → Now |
|---|---|---|---|
| `abraxan` | 3 → 4 | `mackled_malaclaw` | 2 → 3 |
| `ashwinder` | 2 → 3 | `merperson` | 3 → 4 |
| `augurey` | 1 → 2 | `moke` | 2 → 3 |
| `billywig` | 2 → 3 | `mooncalf` | 1 → 2 |
| `bowtruckle` | 1 → 2 | `murtlap` | 2 → 3 |
| `centaur` | 3 → 4 | `nogtail` | 2 → 3 |
| `cornish_pixie` | 2 → 3 | `occamy` | 3 → 4 |
| `crup` | 2 → 3 | `phoenix` | 1 → 4 |
| `demiguise` | 1 → 4 | `plimpy` | 1 → 3 |
| `diricawl` | 1 → 2 | `pogrebin` | 2 → 3 |
| `doxy` | 2 → 3 | `puffskein` | 1 → 2 |
| `dugbog` | 4 → 3 | `quintaped` | 4 → 5 |
| `erkling` | 2 → 4 | `ramora` | 4 → 2 |
| `fairy` | 1 → 2 | `red_cap` | 2 → 3 |
| `fire_crab` | 2 → 3 | `runespoor` | 3 → 4 |
| `fwooper` | 2 → 3 | `salamander` | 2 → 3 |
| `glumbumble` | 1 → 3 | `shrake` | 4 → 3 |
| `golden_snidget` | 3 → 4 | `sphinx` | 3 → 4 |
| `graphorn` | 5 → 4 | `streeler` | 2 → 3 |
| `grindylow` | 3 → 2 | `thunderbird` | 3 → 4 |
| `hippogriff` | 4 → 3 | `unicorn` | 1 → 4 |
| `horklump` | 2 → 1 | `imp` | 1 → 2 |
| `horned_serpent` | 4 → 5 | `jarvey` | 2 → 3 |
| `jobberknoll` | 1 → 2 | `knarl` | 2 → 3 |
| `kneazle` | 2 → 3 | `leprechaun` | 2 → 3 |

The two a player will notice: **phoenix 1 → 4** (XXXX for difficulty of domestication, not
aggression) and **golden_snidget 3 → 4** (endangered protected species; harming one carries severe
penalties). At their old values the scale read both as "harmless bird", inverting its meaning.

**Rendering.** `BestiaryScreen` now prints the grade as repeated `X` glyphs (`X`…`XXXXX`) instead of
a bare integer, which is how every canon source writes it. Values outside 0–5 are clamped rather
than thrown on, so a malformed third-party datapack degrades the tooltip instead of crashing the
screen.

**Not changed.** Dragon breeds (see `AUDIT_PUNCHLIST.md`), and every entry with no Ministry grade in
any higher-tier source (`qilin`, `zouwu`, `swooping_evil`, `matagot`, `maledictus`, `obscurus`,
`pygmy_puff`, `blast_ended_skrewt`, `boggart`, `hodag`, `rougarou`, `pukwudgie`, `toad`).

## Bestiary — codec range NOT applied (deferred)

`BestiaryEntry.CODEC` still binds `mmRating` with unbounded `Codec.INT`. Tightening it to
`Codec.intRange(1, 5)` — and the `BestiaryClassificationRangeTest` that would guard it — is blocked:
`toad.json` ships `"mmRating": 0`, and `toad` has no Ministry grade in any canon source, so picking
a replacement value is a lore decision, not a mechanical one. Applying the range now would make the
shipped datapack fail to load.

> **Breaking change when it lands.** `Codec.intRange(1, 5)` will reject any third-party datapack
> bestiary entry whose `mmRating` falls outside 1–5, at datapack load, with a hard error. This is
> intended — the alternative is silently rendering a nine-X sea serpent — but it must be called out
> in the release notes for the version that ships it.

## Heritage — descriptions now resolve through lang keys

`Heritage` and `HeritageVariant` each gained `getDescriptionTranslationKey()`, returning
`getTranslationKey() + ".desc"` — i.e. `type.WizardsAndBeastsMod.<id>.desc` and
`subtype.WizardsAndBeastsMod.<parent>.<id>.desc`. This mirrors `ProfessionNode`, the existing in-tree
pattern, rather than introducing a second convention.

- `ModLanguageProvider` emits all 41 new keys (10 heritages + 31 variants); generated `en_us.json`
  goes 599 → 640 keys and stays datagen-authoritative.
- `/wandb heritage list` — the single runtime consumer of `HeritageVariant#getDescription()` — now
  resolves through `Component.translatable`. **Delta:** that command's variant descriptions are now
  translatable and will follow the client locale rather than always printing English.
- `Heritage#getDescription()` had no runtime consumer at all.
- **`getDescription()` is retained on both enums, not deleted.** `ModLanguageProvider` needs it as
  the lang *value* source — the exact role it plays on `ProfessionNode`. Deleting it would only move
  the prose from one Java file to another.

## Heritage — Werewolf lycanthropy (lore correction)

`Heritage.WEREWOLF`'s description said werewolves are "afflicted or born with lycanthropy". In canon
the condition is transmitted solely by the bite of a transformed werewolf and does not pass to
offspring; Lupin and Tonks's son Teddy was a Metamorphmagus. The "or born with" clause is dropped.
Text only — no `MagicSource`, stat delta, `SizeCategory`, colour, or committability change.

`HeritageVariant.WEREWOLF_BORN` (`"born"`) still exists and still describes inherited lycanthropy as
a playable path. That is a gameplay variant with its own id, stat mods and tags — removing it is a
mechanics change, out of scope here. Logged.

## Spells — `canonTier` provenance field (additive, no behaviour)

New `SpellCanonTier` enum (`BOOKS`, `COMPANION`, `FILM`, `POTTERMORE`, `EXPANDED`, `ORIGINAL`) and a
new optional `canonTier` field on `SpellDefinition`'s B-half map codec, `optionalFieldOf` with
default `EXPANDED`. All 27 existing spell JSONs load unchanged.

Backfilled: `flipendo`, `glacius`, `depulso` → `expanded` (video-game origin); `frigora`,
`claustra_reverto` → `original` (no attestation in any canon source). The other 22 are deliberately
unannotated pending a ruling.

**Metadata only.** Nothing in the cast pipeline, learning gates, spell UI, or Gamp's Law validation
reads the field. No filtering, no config toggle, no gating.

## Wandlore — elder `affinity_tags`

`wand_woods/elder.json` dropped `"dark"`; `"rare"` and `"combat"` stay. Ollivander's essay makes
elder the rarest and unluckiest wandwood, drawn to the unusual/exceptional/destined owner — it does
not make elder inherently dark. That association belongs to the Elder Wand as an artefact, not to the
wood. `affinity_tags` is descriptive metadata: `WandCorruptionSystem`'s `"healing"` test is its only
Java reader, so **no gameplay behaviour changes**. `spell_modifiers.dark_arts: 0.3` is untouched —
editing it would be a balance change, not a lore fix.

---

# Canon Correctness Pass, Follow-Up — 2026-07-25

Closes the two deliverables the predecessor pass correctly blocked, and applies Christian's rulings
on the four open questions. Two rulings went *against* the predecessor's recommendation.

## Bestiary — `mmRating` is now optional (breaking for third-party datapacks)

`BestiaryEntry.mmRating` changes from `int` to `Optional<Integer>`, bound as
`Codec.intRange(1, 5).optionalFieldOf("mmRating")` — following the `entityType` idiom already in the
record rather than introducing a new one.

> **Breaking change.** An `mmRating` outside 1–5 now fails **datapack load** with a range error
> instead of silently rendering. Absence is legal and means unclassified; `0` and `9` are not.

**Ruling: `toad` does not get `mmRating: 1`.** The predecessor recommended that; Christian ruled
against it. Scamander's A–Z lists only creatures that exist exclusively in the magical world, and
ordinary animals with magical uses are deliberately excluded. A toad therefore holds **no Ministry
classification at all**, not a low one — `X` ("boring") is a real grade held by real magical beasts
such as the Flobberworm and the Horklump, so applying it to a toad asserts a grade canon does not
make. This is a category rather than a special case: owl, cat and rat are coming.

`toad.json` drops the `mmRating` field outright. No sentinel, no null, no zero.

**GUI delta.** `BestiaryScreen` renders X glyphs when a grade is present and the translated
placeholder `bestiary.wizards_and_beasts.rating.unclassified` (`Unclassified`) when it is not. The
defensive clamp is retained: the codec now rejects out-of-range values at load, but a client can
still reach the renderer through a `ClientBestiaryCache` entry synced by an older server.

**Sync.** `SyncBestiaryEntriesPayload` derives its `StreamCodec` from `BestiaryEntry.CODEC` via
`ByteBufCodecs.fromCodec`, so the type change propagates with no payload edit.

**Test.** `BestiaryClassificationRangeTest` covers all three states: every shipped entry parses
within 1–5; absence stays absent rather than acquiring a default; `0`, `6`, `9` and `-1` are rejected
by the codec.

## Bestiary — dragon breeds are uniformly XXXXX (player-visible)

**Ruling reversed.** The predecessor recommended keeping the 3/4/5 difficulty ladder. Christian ruled
against it. Scamander has one entry — "Dragon", XXXXX — with the ten breeds described inside it and
no per-breed grades. Since `mmRating` now renders as X glyphs, i.e. explicitly as a Ministry grade,
"Antipodean Opaleye: XXX" is a false in-fiction statement inside a book that presents itself as the
Ministry register.

| Entry | Was | Now |
|---|---|---|
| `antipodean_opaleye` | 3 | 5 |
| `common_welsh_green` | 3 | 5 |
| `chinese_fireball` | 4 | 5 |
| `hebridean_black` | 4 | 5 |
| `norwegian_ridgeback` | 4 | 5 |
| `romanian_longhorn` | 4 | 5 |
| `swedish_short_snout` | 4 | 5 |
| `hungarian_horntail` | 5 | 5 (unchanged) |
| `peruvian_vipertooth` | 5 | 5 (unchanged) |
| `ukrainian_ironbelly` | 5 | 5 (unchanged) |

The difficulty gradient is **not discarded** — it is canon-supported at the level of *temperament*
rather than classification (the Opaleye is unusually mild-tempered for a dragon; the Vipertooth
required a cull for its taste for humans). That belongs in a separate `threatTier` field in a later
pass. No compensating field, tag or comment was added here.

## Bestiary — `sortOrder` collision resolved

`demiguise` moves from `33` to `94`. `cornish_pixie` keeps `33`. Values 0–93 are contiguously
occupied, so 94 is the next free slot and there is nothing nearer its old position.

Visible effect is limited: `BestiaryScreen` sorts by category first and `demiguise`
(`SMALL_CREATURE`) never shared a group with `cornish_pixie` (`WINGED_BEAST`), so the collision was
only ever a tie in `BestiaryEntryRegistry`'s global ordering, already broken deterministically by id.
Within its own category `demiguise` now sorts last rather than between `niffler` and `mooncalf`.

## Spells — `canonTier` is now genuinely optional (breaking for third-party datapacks)

`SpellDefinition.canonTier` changes from `SpellCanonTier` (defaulted to `EXPANDED`) to
`Optional<SpellCanonTier>` with no default, matching the `mmRating` treatment so both fields read the
same way.

> **Breaking change.** A spell JSON without `canonTier` no longer receives an implicit `EXPANDED`.
> The field is absent, and absence is now meaningful.

The predecessor flagged the collapsed-states flaw itself when the earlier prompt forced the default;
Christian removed the constraint.

**Backfill.** 19 spells to `BOOKS` — `accio`, `aguamenti`, `alohomora`, `colloportus`, `confringo`,
`crucio`, `diffindo`, `episkey`, `expelliarmus`, `finite_incantatem`, `incendio`, `levicorpus`,
`liberacorpus`, `lumos`, `nox`, `reparo`, `riddikulus`, `stupefy`, `wingardium_leviosa`. 2 to `FILM`
— `arresto_momentum` and `bombarda`, neither of which appears in any book; both originate in the
*Prisoner of Azkaban* film. The five carrying explicit values from the predecessor pass keep them
(`flipendo`/`glacius`/`depulso` → `EXPANDED`; `frigora`/`claustra_reverto` → `ORIGINAL`).

**`capacious_extremis` is deliberately unassigned.** The Extension Charm is book canon (Hermione's
beaded bag, *Deathly Hallows*) but the incantation is never given in the books and the source is
unconfirmed. It gets no tier rather than a placeholder — exactly the state the rebind exists to make
expressible. 19 + 2 + 5 + 1 unassigned = 27.

Still metadata only. Nothing in the cast pipeline, learning gates, spell UI or Gamp's Law validation
reads `canonTier`.

---

## 2026-07-27 — Module content gating

Before this pass, switching a module off stopped nothing being *obtained*. Creative tabs exposed 269
entries behind 6 hand-written guards covering 5 modules of 19; 3 modules carried recipe conditions;
nothing rebuilt the creative menu when module state changed.

### Content that is now hidden when its module is off, and was not before

| Module | Newly gated |
|---|---|
| `STRUCTURES` | 130 decorative items / 130 blocks (were only recipe-gated, never hidden from the tab) |
| `CREATURES` | 107 spawn eggs, 108 entity types, and new natural spawns of them |
| `WANDS` | 15 items incl. all 30 wandmaking recipes |
| `DARK_ARTS` | 14 items — previously tab-guarded only, now also recipes, loot and use |
| `BROOM_FLIGHT` | 10 items, previously tab-guarded only |
| `POCKET_DIMENSIONS` | 6 items / 8 blocks, previously tab-guarded only |
| `GRINGOTTS` (new) | 6 currency items, 2 exchange recipes |
| `WANDWOOD` (new) | 36 items / 36 blocks, 32 recipes |
| `MAGIZOOLOGY` (new) | 19 items / 1 block |
| `ARTEFACTS` (new) | 13 items / 11 blocks |
| `WIZARDING_FOOD` (new) | 12 items, 11 recipes |
| `FURNISHINGS` (new) | 12 items / 12 blocks |
| `SCHOLARSHIP` (new) | 5 items |
| `FLOO_NETWORK`, `HANDBOOK`, `BESTIARY`, `OWLS`, `MINISTRY` | 3 / 1 / 1 / 1 / 1 items |

**In a default install nothing disappears.** All seven new modules ship `ENABLED`, and the only
modules shipping `DISABLED` (`DARK_ARTS`, `AZKABAN`, `CHAMBER_OF_SECRETS`, `MINISTRY`) already had
their tab entries guarded. The delta is what an operator can now switch off, not what a player loses.

### Recipe condition coverage

| | Before | After |
|---|---|---|
| Generated | 224 / 267 | **267 / 267** |
| Hand-authored | 10 / 72 | **72 / 72** |
| **Total** | **234 / 339 (69%)** | **339 / 339 (100%)** |

Modules covered went from 3 (`STRUCTURES`, `BROOM_FLIGHT`, `WANDS`) to 15. No recipe is deliberately
left ungated.

### The limit of the guarantee

Ownership is a datapack tag, and content with no tag is reachable in every configuration — the index
fails open so an untagged item stays as reachable as it is today rather than vanishing. Coverage is
**202/202 blocks and 392/393 items**. The single untagged item is `conjured_spoiled_food`, which only
exists because a spell created it and is already gated where that spell is cast.

### What is deliberately *not* gated

- **Block loot.** A loot table rolled with a `BLOCK_STATE` is exempt from the gating loot modifier.
  Filtering it would destroy content already in the world: a wall mined after an operator switched a
  module off would break into nothing. Nothing placed, carried or already spawned is ever removed.
- **Left-click.** A player can always break and collect a block whose module was switched off under
  them.
- **Admin commands.** `/broom give`, `/creature summon` and the rest are behind the UUID allow-list.
  Inspecting a module you have switched off is what those commands are for.
- **Entity AI.** `CREATURES` and `AZKABAN` already go inert through the per-entity guard
  `DementorEntity` and `GenericBeastEntity` use. A blanket tick cancel would freeze already-spawned
  mobs in mid-air.

### Verification

`runData`, `test` (282 passing) and `build` all clean. **No in-game pass was run** — the matrix below
is reasoned from source and guarded by unit tests, not observed in a client.

---

## 2026-07-27 — Chamber of Secrets generation is gated

**The structure no longer generates in a default install.** `Module.CHAMBER_OF_SECRETS` ships
`DISABLED`, and until now the chamber generated anyway: `worldgen/structure/chamber_of_secrets.json`
was a raw `minecraft:jigsaw`, so no code ever consulted the flag. A player exploring underground found
an empty stub chamber with a basilisk in it, belonging to a module the mod believed was switched off.

`ChamberOfSecretsStructure` now owns the structure type and returns no generation point unless the
module is accessible. Same gate as `AzkabanStructure`, same semantics as every other module gate:
`ENABLED` and `PREVIEW` generate, `DISABLED` and `COMING_SOON` do not.

### What changes for an existing world

Nothing already generated is touched. A chamber written into a world's chunks stays there, keeps its
blocks and keeps its `spawn_overrides`, whatever the flag says afterwards — verified by generating
one with the module on, switching it off, restarting the server and locating it again. Only chunks
generated *after* the flag goes off lack a chamber.

The structure's registry id is unchanged (`wizards_and_beasts:chamber_of_secrets`); only the `type`
it dispatches on moved from `minecraft:jigsaw` to `wizards_and_beasts:chamber_of_secrets`. Saved
`StructureStart`s key off the id, and the pieces are still vanilla `minecraft:jigsaw` pool pieces, so
saved chunks load unchanged.

### Implementation note

`JigsawStructure` is `final`, so the gate cannot be an overridden `findGenerationPoint` calling
`super`. `ChamberOfSecretsStructure` holds a `JigsawStructure` instead and its codec is
`JigsawStructure.CODEC` xmapped onto the wrapper — no mirrored field list to drift out of step with a
future Minecraft version, and vanilla's own range validation still applies. The structure JSON is
byte-identical to the one it replaces apart from `type`.

### Build

`neoForge.unitTest` is now enabled, so the JUnit suite runs inside a bootstrapped FML environment —
without it, tests cannot construct Minecraft's registry-backed worldgen types at all. The harness
points the test task at `build/minecraft-junit`; since many existing tests read datapack JSON by
project-relative path, the working directory is pinned back to the project root, and the `config/`
directory FML writes there is gitignored.

### Verification

`runData`, `test` (289 passing) and `build` clean. Verified on a dedicated server, one world, seed
1337, four states in sequence — each `locate` from a fresh far origin so no `StructureCheck` cache
could carry an answer over, with `minecraft:mineshaft` as a control at the same origin:

| Module state | `/locate` result |
|---|---|
| `disabled` (shipped default) | not found — full search radius exhausted (95 s); mineshaft found instantly |
| `enabled` | found at `[800192, ~, 800192]` |
| `preview` | found at `[1399808, ~, 1399792]` |
| `disabled` again | not found — full radius exhausted (128 s); mineshaft found instantly |

The chamber's contents are unchanged and still a placeholder — see `AUDIT_PUNCHLIST.md`.

---

## Client render pass — petrification, broom pose, broom camera (2026-07-28)

Three client-render gaps, grouped because they land on the same path. **Mixin count: 2 before, 2
after.** No new mixin, no new access transformer entry.

### Mixin count, in detail

| | Before | After |
|---|---|---|
| `mixin/client/LivingEntityRendererMixin` | present, untouched | present, untouched |
| `mixin/client/PlayerModelMixin` | one `@Inject` at `setupAnim` TAIL carrying the debug part-transform loop inline | same `@Inject`, now three delegating calls and no logic |
| **total** | **2** | **2** |

The brief authorised one new mixin on the camera's zoom computation if no event covered third-person
distance. **NeoForge 21.11 has `CalculateDetachedCameraDistanceEvent`**, which fires before the
block-collision raycast and exposes `getDistance()`/`setDistance()`. The event was used and the
authorised mixin was not added.

The debug part-transform loop moved out of `PlayerModelMixin` into
`client/debug/ModelDebugPartTransforms`. Behaviour is byte-identical; the point is that the mixin body
is now three delegating calls, which is what survives a Minecraft update.

### Petrification — the brief's premise was wrong

The brief stated "a petrified player currently looks completely normal". It did not:
`client/petrify/PetrifyStoneLayer` existed and was registered in `ClientSetup.registerLayers`. It was
broken four ways, which is a different problem from absence:

1. `textures/entity/petrify/stone_statue.png` did not exist — the statue rendered as the missing-texture
   checkerboard.
2. It drew a second opaque body coincident with the real skin. Vanilla submits the skinned model at
   `LivingEntityRenderer.submit`; the layer submitted another through `RenderTypes.entitySolid`. Two
   coincident opaque surfaces z-fight.
3. It called `mc.level.getEntity(renderState.id)` at render time.
4. It never froze the pose, so the statue kept walking and swinging.

**Delta.** The layer is deleted. `AvatarRenderer.getTextureLocation` reads
`state.skin.body().texturePath()`, so a `RegisterRenderStateModifiersEvent` modifier substitutes
`state.skin` for a stone one and vanilla draws the ordinary player model in stone. No second mesh to
z-fight, the entity shadow survives (an `isInvisible` approach would have lost it, and would have
rendered a 15%-alpha ghost via the `isInvisibleToPlayer` branch), and the overlay flags
(`showHat`/`showJacket`/`showCape`/sleeves/pants) are cleared so the player's own silhouette does not
print through the stone. Cape and elytra are dropped from the substituted skin — a statue wears no
cloth.

The petrified flag reaches the model through `EntityRenderState.setRenderData(ContextKey)`, never by
reading the entity. `EntityRenderer.createRenderState` allocates a **fresh** state every frame, so an
absent entry simply means "not petrified" and there is no teardown path to get wrong.

The frozen pose is applied from `PlayerModelMixin` at `setupAnim` TAIL — `PlayerModel.setupAnim` calls
`super.setupAnim` as its last statement, so TAIL is genuinely after every `HumanoidModel` rotation
including the passenger crouch.

`stone_statue.png` is a deliberate placeholder (flat grey, seeded grain, `tools/petrify_stone_skin.py`).
Appearance is logged NICE-TO-HAVE.

### Broom rider pose

Rider limbs were pure vanilla passenger pose (`HumanoidModel.setupAnim`: legs folded 81° forward at
the hip, both arms +36°) — a boat-sitting pose. Only the whole-body bank was ours. Now: legs
near-vertical and splayed either side of the shaft, arms forward onto the handle, torso taking a
clamped 0.45 share of the broom's forward lean, applied through the same `setupAnim` TAIL injector.

**Delta beyond pose.** The tilt data moved off `BroomRiderRenderer`'s `IdentityHashMap<Object, …>`
keyed by render state and onto the render state itself via `setRenderData`. That map was only ever
cleared in `RenderLivingEvent.Post`, so any render pass that returned early stranded an entry. With
per-frame-fresh render states, an absent entry means "not riding", so dismount, death and a
mid-flight module toggle restore the vanilla pose on the next frame with no teardown at all.

### Broom camera

`ViewportEvent` was unused across the whole tree; broom flight had no camera work. Now:

- **Roll** via `ViewportEvent.ComputeCameraAngles` — 0.35 of the broom's bank, capped at 12° (the
  broom itself banks up to `BroomTuning.MAX_ROLL_TILT` = 50°).
- **Third-person distance** via `CalculateDetachedCameraDistanceEvent` — up to +1.6 blocks at full
  boosted speed. Because the event fires before the collision raycast, the pulled-back camera still
  cannot clip through a wall.

Yaw and pitch are untouched, so nothing here can fight the mouse. Both values ease toward their
target and **snap to exactly zero** once inside `SETTLED_EPSILON`, then stop being applied — which is
what makes the return to vanilla clean rather than a permanent hairline offset. Arithmetic lives in
`BroomCameraMath` with 8 unit tests covering the caps, sign symmetry, overspeed saturation, no
overshoot, and convergence to exactly neutral within ~2 s.

### Cloak — Phase 4 skipped

The audit found no leak an event should close. `RenderNameTagEvent`, `RenderPlayerEvent.Pre` and
`LivingChangeTargetEvent` were all evaluated and none was needed:

| Check | Finding |
|---|---|
| Name tag | Already hidden. `LivingEntityRenderer.shouldShowName` gates on `!isInvisibleTo(localPlayer)`; vanilla suppresses the tag for any invisible entity. |
| Mob targeting | Already handled — `LivingChangeTargetEvent` cancel plus a 64-block `clearMobTargets` sweep on apply. |
| Own first-person view | Already handled by `CloakClientViewRestrictionHandler` (third person + inventory preview); the first-person hand is deliberately kept. |
| Held items + armour | Visible for `invisibility_cloak`, hidden for `deathly_hallow_cloak`. That is the `deathlyHallow` flag doing its job, not a leak. Logged POLISH, unchanged. |

### Module gating

Each handler checks its own module: `CREATURES` for petrification, `BROOM_FLIGHT` for pose and
camera. The mixin checks nothing — it delegates, and the handler decides, so the gate stays in
ordinary testable code.

### Verification

`test` 304/304 passing and `build` clean. **The in-game pass is outstanding** — see
`AUDIT_PUNCHLIST.md`. None of this can be confirmed from source.

---

## Wandmaker table repair (2026-07-28)

Full diagnosis in `WANDMAKER_AUDIT.md`. Two commits, two files, no mixin, no new API.

### What a player will notice differently

| Before | After |
|---|---|
| Shaping a wand blank on a log appeared to do nothing — and in a dev runtime, hovering the shaped blank (or opening JEI's wandmaking entry) threw out of the tooltip build. | The blank takes a wood type and says so on hover: `Wood: wizards_and_beasts:rowan`. |
| The bench's enhancer/tier bar was drawn across the top of the hotbar slots. | The bar sits in the bare strip below the hotbar. Its hover tooltip moved with it. |
| The window title and "Inventory" were near-invisible — vanilla `#404040` on a `(35,31,27)` panel. | Both drawn in the panel's own palette. |
| The flexibility picker sat 14 px right of centre. | Centred. Clicks were always aligned with the drawing, so behaviour is unchanged. |
| The tier tooltip could be drawn off the top or right edge of the window. | Clamped into the window. |

### Behavioural deltas

**`WandBlankItem` tooltip argument.** `Component.translatable("…tooltip.wood", wood)` passed a raw
`Identifier`. `TranslatableContents` accepts only `Component`, `Number`, `Boolean` or `String`, and
its constructor **throws** on anything else when `FMLLoader.isProduction()` is false — a packaged
build silently falls back to `toString()`, a dev client does not. The value is now passed as a
string, matching what `WandmakersBenchScreen` already did for the same id. **Delta:** in a packaged
build the rendered text is unchanged; in dev the tooltip stops throwing. No component, recipe or
gating change — the wood was always being written correctly.

**Tier bar position: `imageHeight - 28` → `imageHeight - 9`.** Chosen from the artwork, not taste:
the texture's last inventory row occupies y 170-186 and the panel's own border begins at y 194, so
187-193 is the only free horizontal strip in the panel. The bar is 6 px tall and now occupies
187-192. Its hover region moved with it — both now read one `barTop()` helper, where previously the
draw and the hit test carried separate copies of the same expression.

**Label colours.** `renderLabels` is now overridden rather than inherited. Colours are the two this
screen already uses for its own text (`0xFFddccaa`, `0xFFbba07a`). Not extracted to a helper on
purpose: a shared fix would be a mod-wide change, which this work is explicitly not.

### API decisions

No texture was moved and no drawing path changed. Ground-truthed against the decompiled 1.21.11
client: the bench already used the current `blit(RenderPipeline, Identifier, x, y, u, v, w, h, uw,
uh, texW, texH)` overload via `McStylePanel`, and `wandmakers_bench.png` is exactly 176 × 196, so the
background was drawing 1:1 all along. The suspected render-API port defect is real but lives in two
other screens — see the punchlist. Neither was touched.

### Verification

`compileJava` clean (two pre-existing JEI deprecation warnings, untouched). `test` 304/304 and
`build` **BUILD SUCCESSFUL** — the pre-existing `AssetModelParityTest` failure the brief describes
does not reproduce on a clean tree, so the before and after failure sets are both empty. **Neither
fix has been confirmed in-game**; the tooltip crash was proven from the decompiled
`TranslatableContents` constructor, and every screen coordinate was measured by decoding the texture,
not by looking at it running.

---

## Screen port debt and the missing wand cores (2026-07-28, second pass)

Four commits, following the wandmaker repair above. Everything on that punchlist that could be closed
without a design ruling is now closed.

### What a player will notice differently

| Before | After |
|---|---|
| The Niffler Pouch and Hermione's Bag opened onto a scrambled background. | Both draw their panels correctly. |
| The Ollivander trial screen's title was invisible, and an "Inventory" heading sat across the middle trial card. | Title readable, phantom heading gone. |
| The bench drew a preview of the finished wand across its own ingredient slots. | Gone. The wand in the output slot carries all of it on its tooltip. |
| Thestral tail hair, veela hair, troll whisker, wampus cat hair and thunderbird tail feather could not be put in the bench and never appeared in JEI. | All five are wand cores. 50 new wood×core combinations, each with a `wand_cores` definition so wands built from them can bond. |

### Behavioural deltas

**The last two 1.21.1 blit call sites are gone.** `NifflerPouchScreen` and `HermionesBagScreen` used
`blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256)`. That overload was removed; the call
bound to `blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)` by int→float widening and drew a quad from
the panel corner back to the screen origin. The pouch sheets were measured (176×166 and 176×133 panels
in the top-left of a 256×256 sheet) and now take one pipeline blit each. Hermione's bag reuses vanilla
`generic_54`, whose player-inventory strip always lives at v=126, so it assembles from two blits exactly
as `ContainerScreen` does. **Delta:** both backgrounds render for the first time. No texture moved.

**`OllivanderTrialScreen` overrides `renderLabels`.** It drew `playerInventoryTitle` at y 106 across the
middle trial card — a heading for an inventory `OllivanderTrialMenu` has no slots for. Title only now.

**The bench's craft preview is deleted, not moved.** Six lines from y+16 through the slot artwork at
y 35-50. The panel has no free band tall enough to hold it, and it duplicated `WandItem`'s own tooltip,
which is a superset. `wandcraft.gui.preview` went with it. **Delta:** a player reads the finished wand's
stats by hovering the output slot rather than off the panel. The status message for an *empty* output
stays — that is the part the item cannot say for itself.

**Wand core identity moved from a hardcoded list into data.** `WandCoreMaterialItem.isBenchCore` was
`item == PHOENIX_FEATHER || item == DRAGON_HEARTSTRING || item == UNICORN_HAIR`; it now asks whether the
stack resolves a core key. The five orphaned materials became `WandCoreMaterialItem`s keyed by their own
id. **Delta for datapacks:** a new core is bench-legal by existing, not by editing Java.

**50 new wandmaking recipes, derived from the shipped 30 rather than invented.** Those 30 are perfectly
regular — `unicorn_hair` is the per-wood base row and every other core is that row plus a fixed offset.
The rule, which reproduces the phoenix and dragon rows exactly:

```
minimum_bench_tier += (raw_power - 1.15) * 2.0
result_length_*    += min(1.0, (raw_power - 1.15) * 2.0)
result_integrity    = base, banded on the core's consistency:
                      >=0.80 -> 0.00   >=0.65 -> -0.03   >=0.50 -> -0.06   else -0.09
                      floored at 0.80
```

The one input that is a design choice is each core's `raw_power`, and those were read off each item's
own `.desc` line in `en_us.json`, which is where this mod already recorded what each core is: troll
whisker crudest (consistency 0.45), veela hair fastest (initiative 1.00), thestral tail hair strongest
and least loyal (raw_power 1.95, transfer resistance 0.40). **Tune `raw_power` and the whole row moves
with it.**

**Five `wand_cores` definitions were mandatory, not cosmetic.** `WandResonanceSystem` returns a flat
`0.0` when the core registry lookup misses, and the bond threshold is 0.65 — a wand built from a core
with no definition could never bond, so it could never cast.

**Eight `wand_core.*` display names added.** All eight definitions, including the three that already
shipped, referenced a `display_name` translate key that did not exist in `en_us.json`.

### Verification

`compileJava` clean, `test` 304/304, `build` **BUILD SUCCESSFUL**. A dedicated server was booted on the
change: `Done (0.555s)`, no parse errors, no mod-side warnings — which is what validates that all 80
wandmaking recipes and all 8 `wand_cores` definitions decode, since a malformed datapack registry entry
aborts the boot. **The screen work is still unverified visually.** Panel geometry was measured by decoding
the PNGs; nobody has looked at these screens running.

---

## Punchlist sweep (2026-07-28, third pass)

Four commits closing ten open non-blocker findings. No mixin added; the two remaining structural
items are noted in `AUDIT_PUNCHLIST.md` with the reason each was left.

### What a player or operator will notice differently

| Before | After |
|---|---|
| `/wandb module set` changed the flag but not the recipes — enabling a module left its recipes uncraftable until `/reload`, disabling one left them craftable. | A state change reloads datapacks, so the crafting table agrees with the viewer immediately. |
| A world whose stored module state differed from the build's defaults loaded its recipes against the defaults. | The same rule covers startup: the reload fires whenever the state that reaches the cache differs from what was there. |
| `/wandb module list` printed raw lowercase ids. | Prints the module's name and its id. |
| Flipping a module with the creative inventory open appeared to do nothing. | The open screen refreshes in place, keeping search text and scroll. |
| Wand tooltips read `Wood: wizards_and_beasts:rowan`. | `Wood: Rowan`, from the wood's own `display_name`. |
| The bench only ever mentioned its tier once a blank *and* a core were seated. | With a shaped blank in, it names the cheapest tier that wood can be worked at. |
| An elder wand looked ordinary in JEI's wandmaking entry. | The viewer builds its stack from the recipe, marker and all. |

### Behavioural deltas

**Datapack reload on module state change.** `ICondition` is evaluated once, while a pack is read, and
the answer is baked into the recipe manager — so a mod that gates recipes with a condition and then
changes the condition's answer at runtime has a half-live gate. 122 recipe JSONs across 12 modules
carry `wizards_and_beasts:module_enabled`, so this was never a wandmaking quirk. The reload is driven
from `refreshAndBroadcast` by an actual before/after comparison of `ModuleManager.snapshot()`, which
covers both the operator command and `ServerStartedEvent`, and costs nothing when a world agrees with
the build. A failed reload is logged and leaves the previous resources in place.

**`ModuleIds.displayName`.** The `module.<ns>.<path>.name` key was being built by hand from
`module.name().toLowerCase(ROOT)` in two places. Derived from the id now, so it cannot drift from
`ModuleIds.of`.

**`LocationBlockHelper.withSlabAndStonePlate` replaces the dead `stonePressurePlate`.** Diagon's
street stone registered straight into `ModBlocks` to avoid the stairs and wall `withVariants` would
have added, which kept it out of `allBlocks()`; every datagen provider then named its three blocks and
three items by hand. The helper now offers that shape and both tag providers lost their copies. **The
generated tag files are unchanged in content** — same members, so `runData` was not re-run.

**`WandmakingRecipe.createWand` / `rollLength`.** One builder for the output wand. `assemble` still
returns empty and now says why: flexibility is bench state and length is rolled, so neither is
recoverable from a `RecipeInput`.

**`WandLoreNames`.** Resolves a wood or core id to its datapack `display_name` through the tooltip
context's registries, falling back to a title-cased path (`rowan` → `Rowan`) rather than a bare
translation key when registry access or the definition is missing. The 10 `wand_wood.*` names those
definitions point at did not exist in either lang file and are added.

**`FormRenderStateModifier` moved to `setRenderData`.** It kept an `IdentityHashMap` keyed by render
state, cleared only after a successful non-humanoid render, so every HUMANOID form and every early
return stranded an entry — and `createRenderState` allocates a fresh state per frame. The map grew for
the life of the session. **Delta:** a long client session no longer leaks; `removeFormData` and both
its call sites are gone.

**Creative screen refreshes in place** via `resize`, which re-runs `init` and re-selects the tab while
keeping search text and scroll — both of which reopening would have lost.

### Verification

`compileJava`, `test` 304/304 and `build` all clean. The module reload was exercised end to end on a
dedicated server over RCON: `wandb module set floo_network disabled` → `Loaded 1852 recipes` (from
1855), `enabled` → back to 1855, and a subsequent boot whose stored state matched the defaults
performed no reload. RCON was enabled temporarily in `run/server.properties` for that test and
restored afterwards. **The startup-with-a-differing-world case was not exercised directly** — it is the
same comparison and the same call, but it was not watched firing. The client-side changes (creative
screen refresh, form render state, tooltips) are **not visually verified**.

---
---

# Wand wood: registry authority (2026-08-01)

Deltas from the wand-wood pass described in `WAND_REWIRE_AUDIT.md` §A.6.

**Design goal was zero gameplay delta.** Every wood number was transcribed from the hardcoded
`WandStatsResolver.applyWood` switch, not chosen. Verified against `git show HEAD:` of the original
switch and pinned by `WandWoodCastModifierTest`.

## Intended: none

| Wood | Before (enum switch) | After (JSON `cast_modifiers`) | Delta |
|---|---|---|---|
| elder | `mulDamage(1.05)`, `mulCooldown(0.95)` | `damage: 1.05`, `cooldown: 0.95` | **none** |
| yew | `DARK_ARTS +0.05` | `category_damage_bonus.dark_arts: 0.05` | **none** |
| holly | `COMBAT +0.05` | `category_damage_bonus.combat: 0.05` | **none** |
| rowan | `DEFENSE +0.05`, `addFizzle(-0.02)` | `defense: 0.05`, `fizzle: -0.02` | **none** |
| ash, blackthorn, hawthorn, vine, walnut, willow | nothing (no enum constant) | absent → neutral | **none** |

`spell_modifiers` (the school-keyed map) is **still unconsumed**, exactly as before. Wiring it needs
the magnitude ruling that has not been given — see audit §A.5.

## Unintended but real — 1 delta, flagged

### D-1: wood id namespace is now respected (was silently ignored)

**File:** `wand/cast/WandStatsResolver.java` · **Severity:** theoretical — no shipped content hits it.

Before, wood resolution was `WandWood.byName(id.getPath())` — it used only the **path** and discarded
the namespace, so `minecraft:elder` or `othermod:elder` resolved to `ELDER` and received elder's
modifiers. After, resolution is a registry lookup on the full `Identifier`, so a foreign-namespace id
misses and contributes neutral (plus a warn-once log line).

Not reverted: the old behaviour was the defect — discarding the namespace means another mod's `elder`
would silently inherit ours. Every id the mod writes is `wizards_and_beasts:*` (`WandmakingRecipe`,
`OllivanderTrialMenu`, `WandBlankItem`, JEI, `WandItem.createWand`), so nothing obtainable in-game
changes. Recorded because a datapack writing a foreign-namespace wood id would see that wand's wood
contribution disappear.

## Explicitly preserved (would have been deltas if missed)

- **P-1 legacy component precedence.** `wand_wood_legacy` (the `.persistent()` enum-typed component)
  still wins over the modern `Identifier` component. `resolveWoodId` converts the legacy enum to
  `wizards_and_beasts:<serializedName>` and looks that up. Dropping this branch would have made every
  pre-migration wand stack silently neutral — the single largest delta risk in this pass.
- **P-2 cap and floor untouched.** `HARD_CAP = 3.0f` / `HARD_FLOOR = 0.25f` and the read-time clamp
  are unmodified, and now covered by `ModifierStackTest` (previously untested).
- **P-3 allegiance composition order.** Unchanged; `applyToStack` pushes at the same point in
  `SpellExecutor.executeGeneric`. Order-independence pinned by
  `ModifierStackTest.multiplicationIsOrderIndependent`.

## Related, previously shipped

### D-0: Thestral core reconnected — `3bc54466`

A genuine gameplay delta, committed before this pass. `WandCore` spelled the id `thestral_tail` while
the item, definition, all 10 wandmaking recipes and the lang key spell it `thestral_tail_hair`, so
`WandCore.byName()` returned null and Thestral wands contributed **no core modifier at all**.

**Before:** no core contribution. **After:** `DARK_ARTS +0.20` damage, `+0.02` fizzle — the values
always intended. A bug fix, not a rebalance: the numbers are unchanged, they simply now apply.

## Not done (still blocked)

- **`WandWood` deletion** — `wand_wood_legacy` is `.persistent()`; removing the enum removes the
  component type and orphans pre-migration stacks. Needs a migration decision (audit §7).
- **`spell_modifiers` consumption** — needs the school→`SpellCategory` mapping + magnitude ruling.
- **Core system** — same dual-vocabulary shape, plus `rougarou_hair` and `white_river_monster_spine`:
  enum constants with no definition and no obtainable item.
- **In-client verification** — compile + test only; not driven in a live client.

---

# Dead-End Elimination — narrow subset (2026-08-02)

Behavioural deltas from the greenlit subset of `DEAD_END_AUDIT.md`. The defect class: a player spends
a **capped, non-refundable** resource (60 lifetime SP against 245 to buy the web) and receives
nothing. Both deltas below turn a purchase that did nothing into a purchase that does what its name
says. No gate was added, no magnitude touched, no node added/removed/re-parented.

## D-1: `capacious_extremis` is learnable — `9dfc9a2`

`spells/capacious_extremis.json` declared `learning.requiredSkillId =
"wizards_and_beasts:arcane_mastery"`. Two independent defects on one gate:
1. the key is **namespaced**, but the reader (`PlayerSkillData.getSkillLevel(String)` via
   `SpellLearningEligibility:51-57`) keys on **bare** node ids — it could never match; and
2. no node `arcane_mastery` exists, in either format.

**Before:** teacher offer always refused with `Requires skill: wizards_and_beasts:arcane_mastery`.
Unlearnable by any player, in any world, permanently — Knuts irrelevant, the offer never resolved.
**After:** the requirement is dropped (not repointed, not replaced by an invented node). The spell is
learnable from a teacher on the normal terms — the Knuts cost is unchanged and still charged.

Reachability of everything else is **unchanged**: `requiredSkillId` is a spell-side learning gate,
not a web edge, so no node's adjacency, cost, or availability moved. Web topology is byte-identical.

**Existing saves:** additive only. A save may contain a player who tried and was refused; they can
now buy it. Nobody loses a spell, because nothing became gated — no spell in the repo carries a
`requiredSkillId` any more, so the "player already learned a now-gated spell" case **does not arise
in this pass**. Had it arisen, `SpellLearningEligibility` gates *learning*, not *casting*, and checks
`knowsSpell` before the skill gate — an already-learned spell would keep working regardless.

## D-2: `apparition_training` is a live unlock — `8ce80b13`

The wandlore node set an `unlock_ability` flag **no code read**. Apparition gated only on
`PlayerAbilityHelper.isApparitionUnlocked`, which no survival path sets — the sole setter is the
debug/admin command (`ApparitionCommands:117`).

**Before:** buying `apparition_training` (5 SP, ~⅑ of the lifetime budget, reachable only deep in
wandlore) changed nothing. Apparition was command-only content.
**After:** `apparition_training` ORs into the "passed your test" gate through one
`hasApparitionUnlock` helper, applied at **all three** read sites — `canApparate` (ability-wheel
visibility) plus the two authority paths `handleRequest` and `travelTo`. Wiring only `canApparate`
would have shown the wheel while the Apparition itself still refused.

**Unchanged and deliberately so:** the command still grants Apparition to a player with no node (the
flag is OR'd first, not replaced). **Licence** (`isApparitionLicensed`) and **heritage**
(`isAllowedHeritage`) remain separate gates — the node satisfies the *test*, not the licence. A
player with the node and no licence still cannot Apparate, and is told so. `elf_apparition` keeps its
existing full bypass. No Apparition persistence, state model or splinch/cooldown/ward logic touched.

**Existing saves — read this one:** a save may already contain a player who bought
`apparition_training` back when it did nothing. On update that player **silently gains the unlock
half** of the Apparition gate (they still need a licence). This is the intended repair — they paid
for it — but it is a real, unannounced capability change on load, not a no-op migration.

## D-3: bespoke Java spells can declare a skill prerequisite — `9e7500bb`

Structural, **zero runtime delta**. `Spell.getRequiredSkillId()` was a hardcoded `null` with no seam,
so a bespoke Java spell could not express a gate a JSON spell expresses freely. Added the field +
`protected setRequiredSkillId`. **No spell calls it**: Protego, Expecto Patronum, Imperio and Avada
Kedavra all still return `null` and remain ungated, and Avada Kedavra's existing Unforgivable-mastery
requirement is untouched. Which spells earn a node gate is deferred to the skill-web design session.

## Guard against recurrence — `9dfc9a2`

`SkillReferenceIntegrityTest` asserts every skill reference in shipped data (spell `requiredSkillId`
+ every node `edges` entry) is a **bare** id that **resolves** to an existing node. Verified **red**
before the D-1 fix (failed on the namespaced `arcane_mastery`) and **green** after. This matters more
than either individual fix: a format mismatch fails *closed* and is invisible in play, which is why
`capacious_extremis` shipped broken.

## Not verified

- **In-client verification not performed.** `./gradlew build` is green at every SHA and the guard
  test proves the data contract, but nothing in this pass was driven in a live client — including the
  D-2 save case, the first/third-person and dedicated-server second-player checks, and the
  "command still works without the node" path. All remain outstanding against the brief's §7.

---

# Mechanical blocker fixes — S6-001, S7-001 (2026-08-02)

Two `DEFECT_REGISTER.md` BLOCKERs, both purely mechanical. `S4-001`, `S4-002` and `S5-001` were
explicitly excluded — they are blocked on rulings.

Commits: `6f6dcda9` (S7-001), `82ab2fa3` (S6-001). `./gradlew build` green at `82ab2fa3`.

## Deltas against the brief

### D-1 — Both defects were materially larger than the register stated

The Phase 0 audit halted on three stop conditions before any code was written.

| | Register said | Reality |
|---|---|---|
| S7-001 | 60 keys | **41** — 10 `type.*` + 31 `subtype.*` |
| S7-001 | rename the keys | keys are **datagen output of two builders**; editing JSON alone would be undone by the next `runData` |
| S6-001 | 2 missing textures, bundimun | **26 missing files across 13 creatures** |

The register's "60" was an arithmetic error made when writing pass 1 — the S7 sweep output was
correct and was miscounted by eye. The register entry is annotated rather than rewritten.

### D-2 — Root causes fixed, not symptoms

Per the brief's "fix the root cause first":

- **S7-001.** `Heritage#getTranslationKey` and `HeritageVariant#getTranslationKey` spelled the
  namespace as a string literal. Both now interpolate `WizardsAndBeastsMod.MODID`, so the key
  namespace and the mod id cannot drift apart again. **Upgrade-license deviation:** the brief said
  rename keys; using the existing constant instead of a corrected literal is strictly stronger and
  costs nothing. One key was hand-authored in `main`'s `en_us.json` rather than generated and was
  renamed in place.
- **S6-001.** `tools/bestiary_portraits.py` built its work list by globbing `ICON_DIR`, so it could
  only regenerate portraits that already existed — a creature that had never had one could never
  get one. The list now comes from the bestiary entry definitions. No art was hand-drawn.

### D-3 — Verification

- **S7-001:** 0 bad keys remain as a lang key; all 41 present under `wizards_and_beasts`; every
  value **byte-identical** before/after, checked by diffing the captured key→value map; diff is
  exactly 41 + 1 renamed lines and nothing else. No value was authored, rewritten or translated.
  The 11 surviving `.WizardsAndBeastsMod.` hits are fully-qualified Java class references.
- **S6-001:** 107 icons + 107 silhouettes against 107 entries, zero missing. Regeneration left the
  93 pre-existing files **byte-identical** (26 additions, 0 modifications), which also demonstrates
  the generator is deterministic.

### D-4 — Found and deliberately not fixed

- **`cornish_pixie` points both texture slots at `textures/gui/bestiary/entry_placeholder.png`.**
  It resolves, so nothing is broken, but it will keep showing the generic placeholder despite now
  having a real portrait. Another creature's asset — out of scope. Needs its own entry.
- **No test covers bestiary texture existence.** `AssetModelParityTest` has one test, checking
  generated *item definitions* against *models*; `BestiaryClassificationRangeTest` covers
  classification ranges. Nothing checks `iconTexture`/`silhouetteTexture` resolve — which is why 26
  missing files shipped unnoticed. The brief forbids extending tests here, so the gap is reported.
- **Several new portraits read as plain boxes** (bundimun, lobalug, toad). That is faithful: those
  creatures ship on placeholder box rigs, which is register entry `S2-001`. The portrait depicts the
  rig it is rendered from.
- **`de_de.json` is an empty object** — 0 keys. Noticed during the lang audit; untouched.

### D-5 — In-game verification NOT performed

The brief's §6 is mandatory and was **not** satisfied. Not done: heritage/variant names rendering as
real text in the ceremony, character screens and handbook; bundimun spawning without a
missing-texture fallback; the dedicated-server second-player pass; screenshots of every affected
screen. `./gradlew build` and the byte-level asset/key verification above are what stand behind
these two commits. Both remain outstanding.

### D-6 — Portraits rendered the back of every creature (found after the fix, corrected)

Spotted on review of the contact sheet: the 13 new portraits — and all 94 that preceded them —
showed the creature's rear. `beast_preview.py` measures yaw from *behind* the model, and a
Bedrock/Java entity faces north (`-Z`), so `YAW = -32°` pointed the camera at the back of the rig.
Verified empirically by rendering unicorn, phoenix, acromantula and yeti at `-32°` and `180-32°`:
only the latter shows eyes, muzzles, beaks and fangs.

`YAW` is now `180 - 32` — the 180 faces the model at the camera, the 32 preserves the same
three-quarter offset, taken from the front. `PITCH` unchanged. Fixed in `beast_preview.py` rather
than overridden in the portrait generator, since both of its consumers want the front.

Regenerated all 105 renderable entries: **182 of 210 files changed**; the rest are symmetric enough
to render identically either way. Commit `<see log>`.

**Scope note:** this reaches all 107 creatures, well beyond the S6-001 prompt's "two missing
textures". Done on explicit direction after the problem was raised, not on initiative.

---

## Animagus form-system prompt — Phase 0 halted, three bugs fixed instead

The "Animagus Form System (rendering + capabilities)" brief assumed greenfield. The mandatory
Phase 0 audit found a complete, shipped Animagus system — six forms with state, sync, hitbox,
per-form render, passives, actives, commands and a ritual — so five of the brief's own
stop-triggers fired at once and no part of its deliverables list was implemented.

The full audit is in the session record; the load-bearing findings, kept here because they
contradict the brief's premises and will mislead the next reader:

- **`HeritageFormBridge` is not a render path.** It is a `Heritage → formId/sizeProfileId` lookup
  table. The form render path is `LivingEntityRendererMixin` (HEAD-cancellable inject on
  `LivingEntityRenderer.submit`) → `FormRenderStateModifier.getFormData` → `FormModelRenderer`,
  and Animagus already uses it.
- **`no_wand` / `no_casting` are not tags and have no `Identifier`.** They are `String`s in a
  `Set<String>` on `HeritageVariant`, read only via `variant.hasTag(...)` at five sites, all of
  which consult the *heritage variant* and never transform state. "Apply `no_wand` to a
  transformed player" is not expressible; Animagus suppresses via events instead.
- **`DisguiseData` / `DisguiseSource` do not exist.** No Polyjuice/Metamorphmagus attachment.
- **No GeckoLib in the player-form render path** — every form is a vanilla `EntityModel`. Adding
  bespoke GeckoLib rigs would create the second render path the brief itself forbids. There is no
  `wand.geo.json`; the beast bone convention is `root → body → {head, foreleg_*, hindleg_*, tail}`,
  not `limb_front_l` / `limb_front_r`.
- **Attribute ids verified against 1.21.11** (extracted `Attributes.java` from the neoformruntime
  `sourcesAndCompiled` jar): `movement_speed`, `scale`, `step_height`, `safe_fall_distance` all
  exist. This was the one stop-trigger that did *not* fire.

### Behavioural deltas shipped

The audit surfaced three defects that are bugs under any roster decision, so they were fixed while
the roster question stays open. Nothing was deleted, renamed or rescoped.

- **Form state never reached late observers.** `FormSyncS2CPayload.syncToTracking` was called only
  on change. A player who started tracking an already-transformed player was never told, and drew a
  human. Added `FormLifecycleHandler` (`StartTracking` / `LoggedIn` / `Respawn` /
  `ChangedDimension`), mirroring `CloakEffectsHandler`'s quartet for the same class of problem, plus
  a targeted `FormSyncS2CPayload.syncTo(observer, target)` so tracking a player does not re-broadcast
  to everyone already tracking them. The brief predicted this defect exactly.
- **Size modifiers were lost on respawn and dimension change.** They are transient, and
  `FormSystemAPI.reapplyCurrentForm` — written for this — was dead code, called from nowhere. The
  new lifecycle handler calls it.
- **Disabling `PLAYER_ABILITIES` soft-locked transformed players.** `toggleTransform` returned
  unconditionally when the module was off, so a beast could not revert. Reverting is now always
  permitted (`revertIfModuleDisabled`), and `AnimagusEvents.onPlayerTick` reverts proactively rather
  than waiting for a key press that is itself gated. `animagusFormId` survives, so re-enabling the
  module restores the player intact — the behaviour the brief specifies.

`compileJava` clean, 333/333 unit tests green. **No in-game verification** — the brief's Phase 5
list has not been run.

---

## Animagus v2 brief — option A′, Phase 1 (data layer)

The v2 brief fires six of its own stop-triggers against this repo (see the audit above; the new one
is #5, the broom controller). Christian directed **A′ then B′ then C′**: build the data layer
non-destructively first, flight second, roster pruning last. Phase 1 is the data layer only.

### Scope decision — additive, nothing removed

The six shipped Animagus forms (`animagus_cat/dog/stag/hawk/hare/beetle`) and their hardcoded
`FormRegistry` / `SizeProfileRegistry` entries are **untouched**. The new
`at.koopro.wizardsandbeasts.animagus` package is a parallel datapack layer that currently owns
nothing at runtime: it loads and can be queried, and no existing code path reads it yet. This is
deliberate — it keeps C′ (pruning to the brief's four-form roster) a separate, reviewable step
rather than smuggling deletions into a data-layer commit.

### Deltas from the brief

- **`animation_map` required-role validation added.** The brief specifies the map and states the
  renderer must never hardcode animation names, but does not say what happens when a role is
  missing. Left unchecked that is a silent T-pose at transform time. The codec now rejects a form
  missing any of `idle`/`walk`/`run`/`jump`/`hurt`, plus `glide`/`flap` for fliers, on the same
  fail-at-load principle the brief applies to rig bones. Taken under the upgrade licence.
- **Cross-field validation via `comapFlatMap`**, not a post-hoc check in the loader. Putting it on
  the codec means a bad form is rejected wherever it is parsed — datapack load, unit test, or any
  future sync path — rather than only on the one route that remembers to call a validator.
- **No display-name or description field.** The brief's field table has none, so form identity is
  the datapack `Identifier`. Per the brief's LORE section no prose was authored; when a name field
  is wanted it should arrive as a placeholder lang key, which is a data edit, not a code change.
- **`AnimagusSounds` defaults to `NONE` rather than being `Optional`.** Absent sounds mean "play
  nothing", never "fall back to the player's" — a cat should not grunt like a wizard. A record with
  three independently-optional fields expresses that without a nested `Optional<Optional<…>>`.

### Verified

`compileJava` + `compileTestJava` clean. **340/340 unit tests green** (333 before, 7 new): all four
form JSONs round-trip through the codec, `FLIGHT` without a `flight` block is rejected, a `flight`
block without `FLIGHT` is rejected, and both missing-animation-role cases are rejected.

**Not verified in-game.** The registry is loaded by the reload listener and read by nothing yet.

### Phase 2 (state & sync) — deviation from the brief's `AnimagusData`

Christian chose to extend the existing state rather than add the brief's `AnimagusData` attachment,
on the grounds of one state store, no migration, and existing commands/ritual/passives continuing to
work. Implementing that revealed the choice could not be taken literally.

The option as sketched widened `PlayerAbilityData.animagusFormId` from `String` to `Identifier`.
That contradicts the reason the option was chosen: the field is read as a `String` by
`AnimagusForms`, `FormRegistry`, `SizeProfileRegistry`, `TransitionManager` and the renderer's form
switch, and it holds values like `"animagus_cat"` in every existing save. Changing the persisted
type means a save migration plus a sweep through all of those — the exact cost the choice was meant
to avoid, for no behaviour a player would notice.

**Resolution: the stored type stays `String`; `AnimagusFormBinding` reconciles the two
vocabularies.** Legacy ids (`animagus_cat`) and datapack ids (`wizards_and_beasts:cat`) both resolve,
so a save written before or after the registry existed reads identically. A legacy id with no
datapack counterpart — `animagus_stag`, `hawk`, `hare`, `beetle` — resolves to a *key* but to no
*definition*, deliberately: naming them keeps `query` honest, while refusing to fabricate a
definition stops a form loading another animal's physics. Those four are the C′ roster question.

Also shipped in Phase 2:

- **`SyncAnimagusFormsPayload`** — registry sync on join and `/reload`, mirroring
  `SyncBestiaryEntriesPayload`. The client cannot read the server registry on a dedicated server, and
  both the renderer (rig, texture, animation map) and the flight predictor (physics) need the whole
  definition, so the full map is sent rather than a digest.
- **`/wandb animagus set|clear|query <player>`** — admin-only, on the existing `ADMIN` predicate that
  every other `/wandb` admin subtree uses. `set` reverts a transformed target first: changing form
  underneath one would leave the old hitbox and attributes applied against the new definition.
  `query` reports the stored id and its datapack definition separately, because "stored but
  undefined" is exactly the state a save lands in when a datapack drops a form from under a player.

The brief's `PlayerEvent.StartTracking` broadcast requirement was already satisfied by the
`FormLifecycleHandler` fix committed earlier on this branch.

**Verified:** `compileJava` clean, **346/346 unit tests green** (340 before, 6 new binding tests).
Not verified in-game.

---

## Apparition Reconciliation (Prompt A′) — 2026-08-05

Modification of shipped code. The discovery prompt that preceded this one assumed a greenfield build;
a full Apparition system already existed, so this replaced three subsystems inside it and left the
anchors, the ward model, the elf bypass, the ability-framework integration and every client class
alone.

### Deltas from the brief

**Phases 3 and 4 were merged into one commit.** The brief ordered the charge machinery before the
splinch ladder, but the charge has nowhere to resolve to without the ladder and the ladder has no
input without the charge. Splitting them would have shipped one commit whose only honest description
is "half a mechanic". `SplinchTier` therefore lands a phase early, with the resolver.

**`ApparitionPhase` has no `DESTINATION` constant.** The destination raycast runs every tick of both
Determination and Deliberation and only *resolves* at release, so modelling it as a period the player
passes through would misdescribe it. What the presentation layer needs is "still holding" versus "the
window is open", which is exactly `DETERMINATION` and `DELIBERATION`.

**No separate charge-start payload.** §3.5 asked for two C2S payloads. The start already exists: the
framework's `AbilityUseC2SPayload` reaches `ApparitionAbilityBehavior`, which now opens the attempt
instead of teleporting. Adding a second payload that says the same thing would have been duplication
with two code paths to keep in agreement. Only the release payload is new.

**`AbilityDefinition` gained a `serverCharge` flag.** §3.5 forbade touching `AbilityInput` or its
contract, and that constraint is honoured — the flag lives on `AbilityDefinition`, defaults to
`false`, and every other ability rides `AbilityInput` unchanged. The client input driver needed
*some* way to know which abilities to drive by press/release rather than by the local timer, and a
datapack flag beats hard-coding two ids in the wheel controller.

**`apparition.json` `chargeTicks` moved 20 → 10, and both definitions gained `serverCharge`.** The
local charge state still runs for Apparition, but only to keep the shipped charge-up ring drawing;
10 matches `BLINK`'s charge duration so the ring completes as the window opens. If the ring and the
server disagree by a tick, the server is right.

**`ApparitionTravelAbilityBehavior` gained a two-line guard.** §2 listed it as untouched, but an
anchored jump started from the destination selector has no way to be *released* otherwise: the press
that ends it would reopen the selector. The guard makes a press during an in-flight attempt a no-op.
`ApparitionChargeManager.begin` refuses to replace an attempt already in flight for the same reason.

**The anchored damage abort resolves at a raw miss of zero.** §3.2 specifies "immediate abort at
MINOR". Resolving at the real release miss would read being shot on tick five of seventy as sixty-five
ticks of panic and land on `CATASTROPHIC`. Zero, plus the hit's own `+4` inflation, lands exactly on
`MINOR` as specified — the rule falls out of the inflation table rather than needing a special case.

**The catastrophic lockout is the cooldown.** `SplinchTier.CATASTROPHIC.lockoutTicks()` is stamped
into the existing persisted `apparitionCooldownTicks` via `max(tierCooldown, lockout)`, rather than
adding a second persisted field that means the same thing.

**First `causeFoodExhaustion` in the codebase.** Flagged by the brief; confirmed by audit. Blink
costs 1.0, anchored 6.0.

**`SplinchedEffect` amplifier 1 is no longer lethal.** Its javadoc previously documented the lethality
as deliberate. Reversed per §3.3: the bleed now runs through a dedicated `SplinchDamageTypes.SPLINCH`
clamped at `1.0F` in `SplinchDamageHandler` before application. A pre-application clamp rather than a
resistance (which would scale wrongly against armour and absorption) or a cancelled event (which would
throw away the hurt animation, sound and knockback). Its own damage type rather than
`damageSources().magic()`, or every magical wound in the mod would have become unkillable too.

**Licence: two refusal gates removed, plus a third the brief did not name.** `canApparate()` — read by
the ability grant layer for wheel visibility — also tested the licence. Leaving it would have hidden
the ability from unlicensed players and reinstated the wall through the back door.

**`MagicalOffence.UNLICENSED_APPARITION` added, notoriety 4.0, non-arrestable.** §3.4 required a
record entry; `TraceService.report` is the documented single seam and takes a `MagicalOffence`, and no
suitable constant existed. Rated below `UNREGISTERED_ANIMAGUS` (6.0). **That figure is a balance
number chosen by analogy, not a canon ruling — change it freely.**

**Anchor capacity became a parameter, not a constant.** `PlayerApparitionPoints.MAX_POINTS` stays as
the record's hard ceiling; `with(point, capacity)` is the new overload and `ApparitionAnchors`
computes the per-wizard cap. `4 + floor(proficiency * 8)` reaches exactly 12 at mastery, so no
existing player wakes up over the limit.

**Config keys stranded, not yet removed.** `apparitionSplinchBaseChance` is now dead (the ladder is
deterministic), and `apparitionRangeBlocks` / `apparitionCooldownTicks` are superseded by the
proficiency formula and the per-tier values. All three are still registered and still read by nothing
in the Apparition path. Removing them is a config-surface break and is left as a decision — see the
punchlist.

**Proficiency earning is farmable.** `0.00005` per block, capped at `0.01` per grant, clean arrival
only (minor pays 25%, major and catastrophic nothing). Repeated blinking earns it. So does spell
proficiency via `SpellProficiencyTracker`; the brief called this out and asked for it to be logged
rather than solved.

**`PlayerAbilityProficiency` is unsynced.** Read/write server-side only. Where a client needs a
derived number it is sent the derived value, so there is no second copy to drift.

### Verified

`compileJava` and `compileTestJava` clean. Full unit suite green at every commit
(`f49e5ae6`, `00c01a7f`, `9dbf5ab8`, `4ffc317d`, `865160c7`, `3c0c1051`), including 4 new test
classes: `PlayerAbilityProficiencyTest`, `SplinchResolverTest`, `ApparitionWindowTest`,
`ApparitionAnchorCapacityTest`. `SkillNodeJsonTest` node census moved 161 → 163.

**Not verified in-game.** §8's twelve-item manual pass has not been run — see the report accompanying
this work.

### Follow-up chore pass — 2026-08-05

**Three Apparition config keys removed.** `apparitionRangeBlocks`, `apparitionCooldownTicks` and
`apparitionSplinchBaseChance` are gone from `Config` and from `WizardsConfigScreen`'s category map.
All three were stranded by the A′ rework: range comes from the proficiency formula, cooldown from the
per-tier values, and the ladder is deterministic so a base splinch *chance* no longer means anything.

**This is a deliberate config-surface break**, accepted at `0.1.0-alpha.1`. An existing
`wizards_and_beasts-common.toml` carrying those three keys will simply have them ignored; NeoForge does
not fail on unknown entries, so no world or config file needs hand-editing. Nothing read the values —
the only remaining references were three `CATEGORY_BY_KEY` entries in the config screen, a
`getOrDefault` catalogue over keys the spec actually publishes, so they were dead lookups rather than
live dependencies.

**`MagicalOffence.UNLICENSED_APPARITION` notoriety 4.0f → 2.0f.** Unregistered Animagus (6.0f) is
deliberate concealment; flying without a licence is a licensing offence — a fine, not Azkaban. Value
only. No enforcement logic and no new offences.

**Apparition proficiency now requires a 15-block minimum.** The `0.00005`/block rate and `0.01`/jump
cap are unchanged, but a jump shorter than 15 blocks grants nothing at all. Blink-spam was reaching
mastery roughly five times faster than anchored travel, which inverted the intent: the risky, slow,
interruptible action should be the primary earn path, not the cheap one. A blink still earns — it just
has to be a real blink.

**Build encoding pinned to UTF-8.** `options.encoding` on every `JavaCompile` and
`defaultCharacterEncoding` on the forked `runData` JVM. The punchlist blamed runData for mangling
`en_us.json`; the actual fault was javac decoding UTF-8 sources with the platform charset, so string
literals were already corrupt in the class file before datagen read them. Affected any non-ASCII
literal in the mod, not just lang.

### Deliberation window rework — 2026-08-05

In-game testing found Apparition unplayable: it discharged while the key was still held, catastrophically,
every time. Not a routing defect — the spec working exactly as written.

**The window was below human reaction time.** At proficiency 0, `windowTicks` was 5 — a quarter of a
second, at or under visual reaction latency. A novice could not hit it by reacting to the ring; only by
having memorised the rhythm. Every honest attempt discharged into `CATASTROPHIC`: no arrival, 14 damage,
40% of inventory dropped, 6000-tick lockout.

Four changes, all authorised after the diagnosis was reported:

1. **`BASE_FLOOR_TICKS` 5 → 12.** 0.6s at novice, 19 ticks at mastery. The skill gradient is unchanged;
   the floor now clears reaction latency. `ApparitionWindowTest` carries a regression guard asserting the
   novice window stays ≥ 10 ticks.

2. **Late release is proportional, not fatal.** `missTicks` is now symmetric — you miss by exactly how
   early *or how late* you were. One tick past the window used to be punished identically to walking away
   mid-cast, which made deliberating the single most dangerous thing you could do in the phase named for it.

3. **Forced discharge moved to a hard cap** (`windowClose + 60`). It is now the backstop for an attempt
   nobody ever releases, rather than the penalty for being slow.

4. **The Determination clock does not run until a destination exists.** The clock and the aim were the same
   timer, so hunting for a landing spot burned the window you were hunting it for. The three Ds are taught
   in order — Destination fixed first, *then* Determination — so this is closer to canon than what it
   replaces, not further. `ApparitionChargeManager.tick` now resolves the raycast before advancing, or the
   player would lose the tick on which they found their spot.

Anchored is unaffected in character: its destination is known at `begin`, so its clock starts immediately.

**Also fixed, and a genuine defect rather than a spec problem:** the client had two paths that dropped a
charge without the player letting go — a screen opening, and the armed ability changing underneath them —
and both cleared local state silently. The server kept an attempt nobody would ever release, which then
discharged. Opening the ability wheel mid-charge therefore splinched you catastrophically.
`ApparitionChargeAbortC2SPayload` now withdraws it; `ApparitionChargeManager.abort` already did the right
thing and simply had no caller.

---

# GUI Theme Pass — Phase 1 (extend + Bestiary pilot)

Ground truth: `docs/audit/GUI_THEME_AUDIT.md`. The brief is v2, which cancelled v1's `WabTheme` /
`WabWidgets` in favour of extending `WizardsPalette` / `WizardsMetrics` / `McStylePanel`.

## Repo-wins divergences (brief §0)

| Brief said | Shipped | Evidence |
|---|---|---|
| Nine-slice cut on **6** (v1) | **8** | v2 already ruled this, and the source confirms it is load-bearing rather than merely shipped: `tools/gui_chrome.py` records that 32×32-on-8 leaves room for "the four-rule frame the wand HUD actually has (dark seat, shadowed gold, filigree, highlight) instead of the two it fits in 3px". Six pixels cannot seat four rules. Nothing was re-cut or redrawn. |
| Ministry frame `#1E3A4C` (v1) | **`#3E1F47`** via `WizardsPalette.MINISTRY` | v2's ruling. The token is sampled byte-exact off `handbook/emblem.png`; the blue was film atrium tiling. |

## Token reuse (brief §2.1, §6.3)

All 25 skin role colours were measured against the 21 shipped tokens. Six landed within a few
points. Five take the token; one keeps its own on Christian's ruling.

| Skin role | Brief hex | Resolved to | Dist |
|---|---|---|---:|
| `MINISTRY.frame` | `#3E1F47` | `WizardsPalette.MINISTRY` | 0.0 |
| `FIELD_NOTEBOOK.accent` | `#8A5A2B` | `WizardsPalette.SELECT` | 9.0 |
| `WORKBENCH.frame` | `#3A2718` | `WizardsPalette.WELL` | 9.1 |
| `FIELD_NOTEBOOK.ink` | `#22201C` | `WizardsPalette.INK` | 9.4 |
| `MINISTRY.base` | `#E4DCC8` | `WizardsPalette.PARCHMENT_SHADE` | 10.2 |
| `GOBLIN_LEDGER.base` | `#E6DFC9` | **kept distinct** | 11.4 |

`GOBLIN_LEDGER.base` is the ruling: goblin ledger stock is not Ministry memo stock, and the vault
is the one screen where sharing `PARCHMENT_SHADE` would be seen as the same paper. Net new base
tokens: 4 of 5.

## Upgrade License deviations (brief §9)

**1. Surfaces are sprites; widgets are tokens.**

The brief's §2.1 lists `drawButton` (4 states) and `drawTab` (2 states) alongside the panel set,
which reads as art. They are not shipped as art.

- *Evidence.* A panel border carries drawn detail that has to nine-slice, and vanilla nine-slices
  only atlas sprites (`blitNineSlicedSprite` is private; `blitSprite` dispatches on `.mcmeta`), so
  surfaces have to be sprites. A button is a filled rect with a one-pixel bevel. As art that is
  4 × 5 = **20 sprites** to express what two `fill` calls and a shade factor express exactly, and
  every one of the 20 would need redrawing to change one colour.
- *Consequence.* States are **derived** — hovered is the face lit, pressed is the same face with
  its bevel inverted — so they cannot drift out of step with each other or with the skin. Same
  reasoning for `drawTab` and `drawSlot`.
- *Cost.* 30 sprites shipped instead of 65.

**2. One state badge, tinted, instead of twenty.**

§2.6 wants four visually distinct module states across five skins. Shipped as a single 8×8
`state/badge` sprite tinted through `blitSprite`'s colour argument.

- *Evidence.* The four states differ by tint (accent when reachable, muted when not) and by what
  happens to the tile beneath them. Twenty near-identical triangles would say nothing the tint does
  not, and the GUI atlas is a shared, finite surface.

**3. `contentTint` returns rather than applies.**

§2.6's `COMING_SOON` silhouette cannot be produced after the fact — a silhouette is the content's
own draw multiplied by a flat colour, so the caller has to pass the tint into its own `blitSprite`.
`McStylePanel.contentTint(skin, state)` returns the multiply colour; `drawStateBadge` draws only the
corner mark. `COMING_SOON` and `DISABLED` stay distinct as the brief requires: opaque skin frame
(*not yet* — shape survives, detail does not) versus dimmed neutral (*switched off*).

**4. `WizardsPalette.withAlpha(token, alpha)`.**

Every screen wanting a translucent tint spelled it `0x33000000 | (SELECT & 0x00FFFFFF)` — two hex
literals per tint, in a codebase whose stated goal is zero colour literals. The masks are
arithmetic, not colour, but nothing reading the file can tell. `McStylePanel.drawRow` moved onto it
at the identical value (`0x33`); **no visual change**.

## Behavioural deltas — Bestiary

Geometry is unchanged throughout: every panel, row, pip and hit region sits at the same coordinate
it did before. What changed is what they are made of.

| # | Delta | Was | Is |
|---|---|---|---|
| 1 | Screen backdrop | `bestiary/screen.png`, a fixed 320×200 blit | `FIELD_NOTEBOOK` nine-slice panel |
| 2 | List and detail panels | `left_panel.png` / `right_panel.png`, fixed blits stretched to fit | skin inset panels, nine-sliced |
| 3 | **Entry rows lost their per-row plate** | `row.png` blitted behind every entry | rows sit directly on the list well; selection is the skin's accent tint via `drawRow` |
| 4 | **Category headers draw as buttons** | `header.png`, a plate that happened to be clickable | `drawButton(NORMAL)` — they collapse a group, so they are buttons |
| 5 | Portrait edge | translucent composed fill, `LINE` at `0x66` | opaque one-pixel `SKIN.frame()` border |
| 6 | Discovery pips | `PIP_ON` / `PIP_OFF` | `SKIN.accent()` / `SKIN.muted()` |
| 7 | Row and detail text | `TEXT` / `TEXT_DIM` | `SKIN.ink()` / `SKIN.muted()` |
| 8 | Scrollbar | shared `theme/` sprites | `FIELD_NOTEBOOK` sprites |
| 9 | Corner seal | — | **new.** See the §2.4 deviation below |

**#3 is the one to look at in-game.** Losing the per-row plate is a real reduction in row-to-row
separation; the design intends the list well plus the category-header buttons to carry it. If it
reads as mushy at GUI scale 1, that is a finding, not something to paper over with a hover tint
this pass did not have licence to add.

### Deviation from §2.4 — seal placement

The brief puts the corner seal at the panel's **top-left**. On the Bestiary that is the search
field (`x+6, y+6`, 108×16), which predates this pass; §4 forbids moving it. Free space at
top-right is 8px wide, and the panel's own top-right is covered by the detail inset.

The seal is drawn at the **foot of the list well** (`x+4, y+H-22`) — the one region on this screen
that is decoration-sized and empty at every scale. Logged rather than silently relocated.

### Not applicable — `drawHeader`

The Bestiary's title is drawn at `y-10`, **above** the panel on the dimmed menu backdrop, not
inside a title bar. `drawHeader` draws a title bar *inside* a panel, so using it here would move
the title — a layout change, which §4 forbids. The title keeps its position and takes
`WizardsPalette.TEXT` (a token, not a literal): the skin's `ink()` is `#1E1A22` and would be
unreadable on that backdrop. `drawHeader` ships for the Phase 2 screens that have in-panel titles.

## Verification status

| Check | Result |
|---|---|
| `./gradlew build` | ✅ BUILD SUCCESSFUL, `:test` and `:check` green |
| Colour literals in `BestiaryScreen` | ✅ **zero** |
| Chrome blits in `BestiaryScreen` | ✅ **zero** — the two remaining `blit` calls are the entry portrait, which is content |
| Generator determinism | ✅ 41 files byte-identical across two `--force` runs |
| Pre-existing art disturbed | ✅ none — `git status` over `textures/gui/` shows only the new `sprites/` tree |
| Sprite contract | ✅ 5 new tests in `GuiSkinSpriteParityTest` (every skin ships every element, badge exists, nine-slice metadata is 32/32/8 and declared, atlas sources `gui/sprites` with an empty prefix, no unreferenced sprite on the atlas) |
| Client boots, atlas stitches, `.mcmeta` parses | ✅ `runClient` reached the main menu; zero atlas, sprite or metadata errors in the log |
| **§5 tier A in-game matrix** | 🔴 **NOT PERFORMED** — see below |

### 🔴 §5 tier A is not done

The client was launched only far enough to prove the resource layer loads. The Bestiary was
**not opened**, and GUI Scale 1 / 2 / 3 / Auto and the small/large window pairs were **not**
exercised. Nine-slice bugs surface at scale extremes and nowhere else, and delta #3 above needs a
human eye at scale 1.

Reported as not done rather than as passed. Phase 2 should not begin on the strength of a build
that compiles.

---

# Cleanup Pass 1 — stale metadata, dead code, safe renames (2026-08-10)

Behaviour-neutral hygiene. No file moves, no package changes, no gameplay changes.
Four commits on `main`: `90ca62e1`, `88eb11f8`, `8e7a5967`, `0e24e01f`.

## Phase 0 — working tree inventory

| Path | State | Logical feature |
|---|---|---|
| `client/gui/util/GuiScaleHelper.java` | modified | heritage GUI restyle |
| `client/heritage/gui/HeritageDossierRenderer.java` | modified | heritage GUI restyle |
| `client/heritage/gui/HeritageSelectionScreen.java` | modified | heritage GUI restyle |
| `test/…/heritage/HeritageNameContrastTest.java` | untracked | heritage GUI restyle |

`git stash list` was empty.

**Deviation from the brief.** The brief expected two uncommitted units. Only one was present: the
innate-stat cast wiring (`StatCastModifiers`, `StatTraining`, `StatEffects`, `StatMilestones`,
`PowerBandTable` and the `SpellExecutor` call site) had already landed in `b2b3655f`, together with
the obscurus/horntail rigs. The quarantined tangle is therefore committed, not dirty, and one commit
was made rather than two.

**Deviation from §1d.** `AssetModelParityTest` was expected to be failing. It is not. A forced
`./gradlew test --rerun-tasks` before any edit gave **432 tests, 0 failures, 0 errors, 0 skipped
across 77 classes**. The baseline is green, which is a stronger position than the brief assumed:
any failure after this pass is unambiguously ours.

## Phase 1 — audit findings

### Stale rig markers

93 of 96 creature definitions under `src/main/resources/data/…/creatures/` carried
`"_comment": "PLACEHOLDER box rig — …"`. `src/generated/resources/` ships no creature definitions at
all (only `advancement`, `loot_table`, `recipe`, `tags`), so there was no generated tree to mirror
and none was hand-edited.

The marker is written by `tools/creature_gen.py` (lines 110 and 361) at the same moment it writes
the box rig, so it is true exactly while the shipped `.geo.json` still *is* that box rig. Rather
than judge by bone count, each shipped rig's bone-name set was compared against
`creature_gen.build_rig(bodyPlan, 1.0)` — the generator was imported and run:

| Class | Count | Marker |
|---|---|---|
| Bone set identical to the generator template | 83 | **accurate — kept** |
| Diverges, marker already cleared (`ghoul`, `hungarian_horntail`, `obscurus`) | 3 | n/a |
| Diverges, marker stale — the dragons | 9 | **removed** |
| Diverges, quarantined (`basilisk`, 24 bones / 111 cubes / 14 rotations) | 1 | **skipped, logged** |

`ghoul` is the calibration point: 18 bones / 17 cubes, built by `tools/ghoul_model.py`, marker
already cleared by hand. The nine dragons carry 22–25 bones and 23–32 cubes with neck, jaw, horn and
foot bones the `WINGED_QUADRUPED` template never emits.

### Dead code

| Symbol | Evidence | Action |
|---|---|---|
| `ModifierStack.finalPower()` | 0 callers; only mention is a javadoc line in `StatCastModifiers` | removed |
| `ModifierStack.multiplyPower()` | 0 callers (contrast: `multiplyDamage` has 25) | removed |
| `ModifierStack.power` | written and read only by the two above | removed |
| 29 unused imports across 25 files | simple name absent from the file outside the import, and absent from any `{@link}` | removed |
| 20 private constants | declared, never mentioned again | removed |
| `MirrorViewScreen.otherPitch` | assigned from the presence packet, never read; only `otherYaw` drives the parallax | removed |
| `WandWoodDefinition.spellModifiers` | unread **by design**; javadoc at lines 12–15 documents why | **kept, verified** |

Rejected after inspection:

- `HidebehindStalkGoal.recalcTicks` — flagged as write-only because it is read through
  `recalcTicks-- <= 0`, where the decrement looks like a write. Live. Left alone.
- 77 "unreferenced package-private types" — every one is a JUnit test class, reached reflectively.
  The rule produced no true positives and its output is discarded.
- 0 private methods without callers. Nothing to do.
- `ClientPetrifyState`'s unused `java.util.Map` import — quarantined. Skipped, logged.

### TrunkArchetype serialization surface

| Site | What is serialized | Coupled to |
|---|---|---|
| `TrunkRecord` codec | `CODEC.optionalFieldOf("archetype", FIELD_CAMP)` | serialized-name literal |
| `ModDataComponents.POCKET_ARCHETYPE` | `.persistent(CODEC)` on item stacks | serialized-name literal |
| `POCKET_ARCHETYPE` stream codec | `valueOf(value.toUpperCase(ROOT))` | **constant identifier** |
| `TrunkBlock.CODEC` | `fieldOf("archetype")` | serialized-name literal |
| lang | `trunk.archetype.wizards_and_beasts.<serializedName>` | serialized-name literal |

The serialized values are explicit lowercase literals held in the constructor —
`SCAMANDER_SANCTUARY("scamander_sanctuary")` — so the **type name reaches no persisted surface**,
and the data component was already registered under the id `pocket_archetype`. Renaming the type is
therefore save-safe; renaming a *constant* would not be, and none were renamed.

## Phase 2 — marker removal (88eb11f8)

Nine files, nine deleted lines, no other change. Removal dropped the single matching line with
newline translation disabled, so line endings, key order, indentation and the em-dash in the marker
text are untouched. All 96 creature definitions still parse.

`antipodean_opaleye`, `chinese_fireball`, `common_welsh_green`, `hebridean_black`,
`norwegian_ridgeback`, `peruvian_vipertooth`, `romanian_longhorn`, `swedish_short_snout`,
`ukrainian_ironbelly`.

## Phase 3 — dead code removal (8e7a5967)

37 files, **68 deletions against 3 insertions** — the three added lines are the rewritten
`StatCastModifiers` javadoc paragraph, which had argued from the symbols being deleted.

The power channel went whole rather than half. Deleting only `finalPower()` as the brief listed it
would have left `power` as a write-only field fed by a `multiplyPower` nobody calls: strictly more
dead code than before, arranged to look deliberate.

Constants removed: `ApparitionServerLogic.RESIDUE_LIFETIME_TICKS`,
`BestiaryScreen.TEX_SCROLL_TRACK` / `TEX_SCROLL_THUMB`, `CharacterSheetScreen.COLOR_BG` /
`COLOR_BG_HI` / `COLOR_BG_SH` / `COLOR_TAB_ACT` / `COLOR_TAB_INACT` / `COLOR_TAB_HI` /
`COLOR_TAB_SH` / `COLOR_EFFECT_BG`, `AttributesTab.CARD_W`, `SpellsTab.COLOR_SECTION`,
`PlayerModelViewport.COLOR_FILL` / `COLOR_HI` / `COLOR_SHADOW`, `SpellCardWidget.COLOR_CATEGORY`,
`VitalsBarWidget.COLOR_BAR_BLUE`, `DementorKissGoal.COOLDOWN_TICKS`, `MaraudersMapItem.OPEN_IDLE`.

Four of those are evidence of unfinished behaviour rather than clutter; the behaviour gaps they
pointed at are logged in `AUDIT_PUNCHLIST.md` so deleting the constant does not delete the signal.

## Phase 4 — rename (0e24e01f)

`TrunkArchetype` → `PocketArchetype`. 9 files, `git mv` for the declaration so history follows, 42
identifiers rewritten. Done as a word-boundary replace of a globally unique identifier rather than a
substring `sed`, verified by `grep` returning zero residual references **and** by a full
`compileJava` + `compileTestJava`.

**Not done: the lang key path.** The brief permits renaming lang keys whose *path* carries the old
term. `trunk.archetype.wizards_and_beasts.*` was left alone: `TrunkTier` and `TrunkAccessMode` keep
`trunk.tier.*` and `trunk.access.*`, and moving one member of that family to `pocket.*` trades a
consistent namespace for an inconsistent one while touching keys a resource pack may override.
Logged as NICE-TO-HAVE rather than done silently.

## Module gating

`git diff b2b3655f..HEAD -- src/main/java/at/koopro/wizardsandbeasts/module` is **empty**. No module
was added, removed or re-gated; every module state is byte-identical before and after.

## Quarantine compliance

No file under the obscurus / horntail / basilisk / petrify tangle was staged, committed, moved,
renamed or edited. Two cleanup operations landed on quarantined files and were skipped:
`creatures/basilisk.json`'s stale rig marker, and the unused `java.util.Map` import in
`client/petrify/state/ClientPetrifyState.java`. Both are logged.

---

# Cleanup Pass 2 — package & file structure reorganisation (2026-08-10)

**Outcome: audited, one move proposed, lane SHELVED. No file was moved. No commit was made.**

Phase 0 was run read-only against `0e24e01f`. Phase 1 was never entered: it requires explicit
approval of a target tree, and the target tree turned out to contain a single file.

## What the tree actually looks like

1,137 main-source files in **283 packages** — an average of 4 files each.

| Metric | Finding |
|---|---|
| Packages > 40 files | **1** — `creature.ability` (42) |
| Packages 21–40 | `effect` (31), `registry` (31), `network.spell` (21) |
| Packages with 1–2 files | **152** |

There are no god-packages. The dominant smell is the opposite — over-fragmentation. `creature.ability`
is a flat list of `CreatureAbility` dispatch-codec implementations and `registry` is a set of registry
holders; flat is the correct shape for both, and any split would be invented taxonomy.

## Client/server split integrity

Only **5** non-`client` files import `net.minecraft.client`, and every one is conventional:
`datagen.ModModelProvider` (datagen never loads at runtime), `integration.jei` ×2 (JEI is itself
client-only), and `mixin.client` ×2 (correctly declared under the `"client"` array of
`wizards_and_beasts.mixins.json`, not `"mixins"`).

46 non-client files reference `at.koopro…client.*`, but the raw count is misleading:

- **4 are javadoc only** — `ConfundoEffect`, `FurnunculusEffect`, `ObscuroEffect`, `BeamRayResolver`
  name client classes inside `{@link}` in comments. No code dependency.
- **7 are deliberately reflective** — `ClientClassBridge.instantiate(...)` or `Class.forName(...)`
  by string literal. Dist-safe by construction.
- **~26 are the `ModNetworkX` pattern** — `registrar.playToClient(TYPE, CODEC,
  ClientPayloadHandlers::handleX)`, a compile-time method reference held by a server-reachable
  registration class. This is the one structural client/server weakness in the tree. It is **not a
  live defect**: a dedicated server booted on this exact tree during pass 1 with zero ERROR/FATAL.
  Fixing it needs indirection, which is a content change and out of scope for a moves-only lane.
- **1 is a genuine structural inversion** — `BeamEntity`, below.

## Move-risk surface — 7 move-blocked classes

Nine string-literal FQNs, invisible to the compiler, all pointing into `client.*`:

| Move-blocked class | Referenced from |
|---|---|
| `client.network.ClientScreenHooks` | `BestiaryItem:24`, `MinistryHandbookItem:35`, `ClientScreenHooksInvoker:9` |
| `client.currency.CoinRenderer` | `CoinItem:31` |
| `client.deluminator.DeluminatorRenderer` | `DeluminatorItem:66` |
| `client.map.MaraudersMapRenderer` | `MaraudersMapItem:197` |
| `client.wand.MorphWandClientHooks` | `MorphWandItem:68` |
| `client.wand.WandRenderer` | `WandItem:54` |
| `client.wand.WandCastClient` | `WandItem:124` |

Other external surfaces: `wizards_and_beasts.mixins.json` names two mixins relative to
`at.koopro.wizardsandbeasts.mixin`; `neoforge.mods.toml` is a **template** under
`src/main/templates/` and carries **no class FQNs**; there is **no `META-INF/services`**. Thirteen
tests read paths, but all read `src/main/resources` data addresses, not Java packages, so package
moves cannot reach them.

## The proposed target tree, in full

```
at.koopro.wizardsandbeasts.client.beam.BeamEntity
  → at.koopro.wizardsandbeasts.entity.spell.BeamEntity
```

Batch A, risk LOW, one file. `BeamEntity` has zero `net.minecraft.client` imports — it uses
`ServerLevel`, `DamageSource`, `ValueInput`/`ValueOutput` — and is registered as a server-side
`EntityType` by `registry.ModEntities:120`, which therefore has to import the client package to do
it. `entity.spell` already holds `PatronusEntity`, `ProtegoShieldEntity`, `SpellProjectileEntity`.
Six referrers, all compile-time, none move-blocked. The registered entity id is the string `"beam"`
and is independent of the class's package, so nothing serialized moves.

**Not executed.** Deferred to ride the client session that still owes pass 1 its §12 in-game
verification, rather than be verified by a green build alone.

## Batches considered and rejected

- **Beam styling consolidation — CANCELLED, name collision.** The intended move was
  `client.wand.{BeamStyle,BeamStyles,BeamSettings}` → `client.beam`. It cannot be done:
  `client.beam.BeamStyle` already exists, as a *different record with a different shape*. The two
  clusters are fully disjoint — `BeamAppearance` and `BeamStyleEditor` resolve `BeamStyle`
  same-package to `client.beam`'s, while the `client.wand` cluster is read only by
  `client/spell/gui/BeamDebugScreen` and `client/wand/BeamClientPayloadHandlers`. Resolving the
  duplication is a merge or a rename, both out of scope here.
- **Merging the 152 one-to-two-file packages.** Cosmetic. ~200 files touched, `git log --follow`
  value spent tree-wide, and §1b is explicit that a merely tidier reorg is not worth doing.
- **Splitting `creature.ability` (42) or `registry` (31).** Flat is correct for both.
- **Moving any move-blocked class.** All 7 are already correctly placed under `client.*`; moving
  them buys nothing and risks a silent runtime break behind a string literal.

## Module gating

No move was executed, so every `ModuleManager` state is trivially byte-identical. Verified by the
absence of any commit in this lane.

---

# Broom master model — Phase 1, data layer (2026-08-11)

## `BroomDefinition` schema change

Two components appended, 19 → 21. Appended rather than inserted: the record has exactly one
construction site (inside its own codec), so position is free, and appending keeps every existing
accessor call unchanged.

| Field | JSON key | Type | Optional | Default when omitted |
|---|---|---|---|---|
| `modelSlots` | `model_slots` | `Map<BroomSlot, Identifier>` | yes | `BroomSlot.defaults()` |
| `woodTint` | `wood_tint` | `int` (packed ARGB) | yes | `BroomDefinition.UNTINTED` = `0xFFFFFFFF` |

### Behaviour for JSON that omits both

All seven shipped broom definitions omit both fields and are **unmodified by this phase**. They
decode to:

- `modelSlots` = `{shaft: straight, tail_cap: plain, binding: cord, bristles: birch}` — the default
  silhouette. Deliberately *not* an empty map: an empty map means "every slot unset", which renders
  a broom with no shaft and no bristles. The default is the humblest thing the model can draw.
- `woodTint` = `0xFFFFFFFF`, which multiplies to no change.

`FOOTREST` and `ACCENT` are absent from the default map on purpose — Cleansweep-tier brooms carry no
footrest, and an accent plate is a hero-broom flourish.

### Encoding

Both fields are written back **only when they differ from the decode-time default**, mirroring how
`loreLines` is already skipped when empty. Re-encoding a broom that authored neither field therefore
produces the same JSON it started with, rather than injecting two keys its author never wrote.

### Colour convention — repo wins

`wood_tint` is authored as a hex string, `"#RRGGBB"` (opaque) or `"#AARRGGBB"` (as written), parsed
to a packed ARGB `int`. This follows the creature `tint` ability (`creature/ability/Tint.java`),
which is the repo's existing convention for a *render* tint. The other convention in the repo — a
raw signed `Codec.INT`, used by brews and spells — was rejected: `-8355840` is unreadable in a file
a datapack author is meant to hand-edit, and this field exists to be hand-edited.

Parsing is `comapFlatMap` returning a `DataResult`, not `xmap`, so a malformed value is a codec
error naming the offending string rather than a thrown exception during datapack load.

### Why the codec was not rewritten

`BroomDefinition.CODEC` is a hand-written `Codec.of(encode, decode)` with a nested-`flatMap` pyramid,
now 21 deep. That is not stylistic: `RecordCodecBuilder`'s `instance.group(...)` caps at 16 fields
and the record has 21.

The repo does have a proven answer to that cap — `SpellDefinition` splits into
`SpellDefinitionFieldsA`/`FieldsB` private sub-records, each with its own `MapCodec` under the limit.
Converting `BroomDefinition` to it was rejected for this phase: the hand-rolled decoder carries
bespoke range validation (`readRangedFloat("maxSpeed", 0.1f, 2.0f)` and friends, thirteen of them)
whose error messages are part of the datapack contract, and a codec rewrite would put that at risk
for no gain this phase needs. Worth doing if a third field ever lands.

## `BroomSlot` — new enum

Six slots mirroring `WandSlot`'s shape (`StringRepresentable`, `bonePrefix`, `slotId`, `required`),
plus a `defaultVariant` that `WandSlot` has no equivalent of — the wand's default lives in
`WandConfiguration.DEFAULT` instead, but a broom's default has to be reachable from the codec, which
runs before any configuration object exists.

| Slot | Bone prefix | Required | Default variant |
|---|---|---|---|
| `SHAFT` | `shaft_` | yes | `straight` |
| `TAIL_CAP` | `tail_cap_` | no | `plain` |
| `BINDING` | `binding_` | no | `cord` |
| `BRISTLES` | `bristles_` | yes | `birch` |
| `FOOTREST` | `footrest_` | no | *(absent)* |
| `ACCENT` | `accent_` | no | *(absent)* |

Declaration order follows the brief's table. `renderOrder()` is computed required-first rather than
returning `List.of(values())` as `WandSlot` does, because the brief's ordering interleaves required
and optional slots and `WandSlot.renderOrder()` documents a required-first contract. Both are
honoured this way.

**Phase 2 must author bones matching these four default variant names**, or an unconfigured broom
falls back to bones that do not exist.

## Verification

`BroomDefinitionCodecTest` — 6 tests, all passing:

- `everyShippedBroomJson_stillParsesUnmodified` — reads all seven files off disk, asserts each
  decodes and lands on the default silhouette and untinted. **This is the gate the brief names.**
- `defaultSilhouette_coversTheRequiredSlotsAndNothingElse`
- `authoredModelSlotsAndWoodTint_decode` — including a slot the JSON omits staying unset
- `woodTint_acceptsExplicitAlpha`
- `unknownSlotKey_isRejected` — error names the offending key
- `malformedWoodTint_isRejected` — wrong length and non-hex

## Broom master model — Phase 2, model & textures (2026-08-11)

`broom.geo.json` went from a **2-bone, 3-cube stub** (a 40-long stick with two boxes, 64x32) to a
36-bone, 72-cube master model. All eight broom identities drew that same stick before this, so the
Firebolt and Cleansweep Seven were the same object with different tooltips.

### Rig

| | |
|---|---|
| Bones | 36 (budget was ~40, ceiling 60) |
| Cubes | 72 |
| Sheet | **128 x 256**, 206 rows used |
| Generator | `tools/broom_model.py` (deterministic, palette-driven, Pillow) |

Hierarchy is `root` → `broom_root` (rider mount) → `broom_body` → six slot container bones → variant
bones, plus three empty FX anchors under `broom_root`. `root` and `broom_body` keep their names on
purpose: the shipped clips animate `root`, and `BroomRenderer` reaches for `broom_body` by literal
name to apply its tilt tickets.

Variants, 24 in all: shaft ×5 (straight, tapered, streamlined, ribbed, lacquered), tail_cap ×4
(plain, brass_cap, finial, banded), binding ×4 (cord, twine, brass_band, wire), bristles ×5 (birch,
blunt, swept, streamlined, racing), footrest ×3 (brass, wood, forged), accent ×3 (nameplate,
lettering, registration).

### Bristles

Per the brief: a solid core of 3–4 cubes at the binding for mass at the root — the logged "twig roots
read spidery" defect — then four crossed planes fanning to the tail. Planes are **0.2 thick, not 0**:
a zero-thickness box has no side faces and vanishes edge-on, which is the defect `phoenix_model.py`
was written to fix on the phoenix wing.

### Texture regions

`shaft_*` and `tail_cap_*` are painted **greyscale** and tinted at render time from `wood_tint`;
everything else is painted in final colour and never tinted. The `accent_*` slot is the baked detail
layer — lettering and registration marks must not shift when the wood colour does. The split is
documented in the generator's module docstring so it survives the next person adding a variant.

Sheet dimensions are chosen by trying each power-of-two width that can hold the widest island, then
taking the least-area result. A 34-long shaft unwraps to a 70px island, so 64 wide is impossible —
the packer does not reject an oversized box, it places it at x=0 and lets it run off the sheet, which
is why the width is filtered before packing rather than after.

### Animations — 10 authored, 3 wired here

`idle`, `hover`, `fly_forward`, `lean_left`, `lean_right`, `boost`, `brake`, `mount`, `dismount`,
`summon`. `brake` and `summon` ship deliberately unwired (see `AUDIT_PUNCHLIST.md`). `hover`,
`lean_*`, `mount` and `dismount` are authored but not yet wired either — controller wiring is Phase
3's deliverable, so this phase leaves the three existing controllers intact.

Clips animate `root` for body motion and the **slot container bones** for part motion, so one clip
covers every variant of a slot. They deliberately do not key `broom_body` rotation: `BroomRenderer`
overwrites its rotX and rotZ from the tilt tickets every frame, so anything keyed there is clobbered.

**One rename:** `animation.broom.fly` → `animation.broom.fly_forward`, with `BroomEntity`'s
`FLY_ANIM` string updated in the same commit so nothing is broken between commits.

### Defect found

`textures/entity/broom.png` **did not exist**. The rig declared a 64x32 sheet and `BroomRenderer`
resolves `textures/entity/broom.png` through `DefaultedEntityGeoModel`, so every broom entity has
been rendering with a missing texture. The generator now produces it.

### Verification

`BroomModelParityTest`, 6 tests — every slot has variant bones and a container bone, every default
variant resolves to a real bone, the contract bones (`broom_body`, `root`, the three FX anchors)
survive, every clip targets bones that exist and every clip `BroomEntity` names exists, and every
box-UV island lies inside the declared sheet with the PNG matching those dimensions. Suite 446/446.

### Deviation from the brief — orientation

§2.2 specifies "Shaft authored along +Z (flight-forward). Bristles at −Z." **Not applied.** The rig
puts the grip at −Z and the bristles trailing at +Z, because a Minecraft entity model faces −Z: with
bristles at −Z the broom flies bristles-first. The stub rig already had its bundle at +Z, so the
brief's axis would also have silently flipped every broom already in the world. Reported per §2
rather than applied silently; one sign flip in `build_bones()` reverses it if the ruling goes the
other way.

---

# Broom Rev 2 — renderer, rename and variant collapse (2026-08-12)

## FOOTREST → FOOTSTRAP

The slot models a hanging leather strap with a toggle bead, not a rigid tier-gated platform, and
reference shows it on training brooms too — so it is also **default-present** now where it used to
default to absent.

| | before | after |
|---|---|---|
| enum constant | `FOOTREST` | `FOOTSTRAP` |
| `model_slots` key | `footrest` | `footstrap` |
| bone prefix | `footrest_` | `footstrap_` |
| variants | `brass`, `wood`, `forged` | `leather` |
| default | absent | `leather` (present) |

**Migration:** `BroomSlot.byId` accepts `footrest` as an alias for `FOOTSTRAP`. The slot codec
rejects unknown ids by design, so without the alias a datapack written against the old key would
fail to decode rather than degrade — the loudest possible break for a rename that changes no
behaviour. Covered by `BroomDefinitionCodecTest.legacyFootrestKey_stillDecodes`.

## Variant collapse

24 → 14. The brief's §4 table totals 14, not the 12 its footer states; the table is implemented as
written.

| slot | before | after |
|---|---|---|
| `SHAFT` | straight, tapered, streamlined, ribbed, lacquered | **plain, swept, racing** |
| `TAIL_CAP` | plain, brass_cap, finial, banded | **plain, finial** |
| `BINDING` | cord, twine, brass_band, wire | **cord, brass_band** |
| `BRISTLES` | birch, blunt, swept, streamlined, racing | **ragged, teardrop, blade, streamlined** |
| `FOOTSTRAP` | brass, wood, forged | **leather** |
| `ACCENT` | nameplate, lettering, registration | **none, nameplate** |

`accent_none` is a real, cubeless bone rather than an absent map key, so "no nameplate" is
selectable and the renderer's hide-all-but-one loop needs no special case.

## model_slots per render identity

Seven of the eight had **no** `model_slots` at all and fell back to `BroomSlot.defaults()`, so every
broom in the game was the same object. All eight are explicit now.

| identity | shaft | tail_cap | binding | bristles | footstrap | accent |
|---|---|---|---|---|---|---|
| `broom` | plain | plain | cord | ragged | leather | none |
| `cleansweep_seven` | plain | plain | cord | ragged | leather | none |
| `comet_260` | plain | plain | cord | teardrop | leather | none |
| `nimbus_2000` | swept | finial | brass_band | teardrop | leather | nameplate |
| `nimbus_2001` | swept | finial | brass_band | blade | leather | nameplate |
| `firebolt` | racing | plain | brass_band | streamlined | leather | nameplate |
| `firebolt_supreme` | racing | finial | brass_band | streamlined | leather | nameplate |
| `oakshaft_79` | plain | finial | cord | ragged | leather | none |

`BroomSlot.defaults()` moves with the roster: shaft `plain`, tail_cap `plain`, binding `cord`,
bristles `ragged`, footstrap `leather`, accent `none`.

## Shaft chain — deviation from §3.1

The brief specified `shaft_grip` → `shaft_mid` → `shaft_neck`, chained from the grip. Two problems:

1. `shaft_grip` parses as *a variant named "grip"* — the renderer resolves variants by the `shaft_`
   prefix, and the slot system would offer three phantom variants and no real ones.
2. Chaining from the grip means rotating the root swings the **binding end** away from the bristles
   it is lashed to.

Built as `shaft_<variant>` (at the binding, unrotated) → `_mid` → `_grip`, so the join is the fixed
end and the free grip is what sweeps. A variant is therefore a *direct child of the slot container*,
which is what `BroomModelParityTest` now asserts.

## Player authoring rig (2026-08-12)

**No behavioural delta.** `player.geo.json` is an authoring asset that nothing loads, registers or
renders, and `PlayerGeoRigParityTest` is a test. Nothing in the running game reads either. Recorded
here explicitly because the deliverable brief asks for a positive statement rather than silence.

---

# Heritage appearance (2026-08-13)

Written against the `HeritageAppearance` override layer. Two of these are canon admissions and two
are honest statements that something is declared but does not run.

## Vampire appearance is under-sourced

Everything the books give on what a vampire looks like is Sanguini in *Half-Blood Prince* ch. 15 —
roughly two lines, gaunt and pale, at a party. There is no canon description of fangs, red eyes, a
cape, or a bat. The `vampire` entry therefore carries **pallor and proportion only**, and its
provenance cites those two lines rather than pretending to more. Anything beyond pallor added later
should be labelled `fanExtrapolation`, not attributed to the citation.

The shipped `vampire_human` form carries a `GLOWING_EYES` render flag. That is invented, and the
appearance entry replaces it with a pallor overlay.

## Human exertion flare is fan-extrapolation, and is deferred

Canon is explicit that wizards are visually indistinguishable from Muggles — the Statute of Secrecy
depends on it. A visible cast aura therefore has **no textual basis at all** and its entry must carry
`fanExtrapolation: true`, never a citation. It is designed to read as "that wizard is powerful", never
"that wizard is of house X"; a bloodline mark would contradict canon outright.

It is also **not wired**, for a mechanical reason rather than a design one. `CastContext` is
server-side. Client-side, `ClientSpellDataState` exposes proficiency for the local player only, and
`SpellCastAnimationS2CPayload` — the one cast signal that reaches other clients — carries
`spellId, ticks, windupEnd, releaseEnd, holdPhase` and **neither a proficiency tier nor a cost
magnitude**. There is nothing to drive the flare with for a remote player. The `overlay` union arm is
built and tested; no flare is declared. Adding the two fields to the payload is a small follow-up.

## Forms declared with no trigger in code

`veela_harpy`, `merfolk_water` and `vampire_bat` are registered forms with size profiles and models,
reachable only through `/wandb` commands. `werewolf_wolf` is in the same position: the mod contains
no moon-phase code whatsoever. These stay declared and unwired — the broom `brake`/`summon`
precedent — rather than having a trigger invented for them, which the brief forbids outright.

## Goblin uses form replacement, not a proportion pass

The brief assigned goblins to the proportion pass on the reasoning that canon treats them as
differently-proportioned humans, citing "part-goblin". **There is no part-goblin variant.** `goblin`
is a whole heritage whose three variants (`common`, `warrior`, `rune`) are all full goblins — a
separate species, not a human lineage. Form replacement is the correct mechanism and is what already
ships. Same reasoning for `house_elf` and `centaur`, both of which are also fully playable heritages
despite the brief asserting they were not.
