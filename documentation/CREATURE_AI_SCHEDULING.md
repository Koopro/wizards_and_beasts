# Creature AI scheduling

Status 2026-10-07. How creature behaviour is scheduled so creatures can get smarter without the server paying for
it, what every piece costs, and the measurements behind it. Code: `creature/schedule/`. Earlier audit:
`PERFORMANCE_AUDIT.md` (§8 summarises this pass).

---

## 1. The four tiers

Every piece of creature behaviour picks one tier. The tier fixes how often it may run and what it may touch.

| Tier | Runs | May do | Must not do |
|---|---|---|---|
| **FAST** | every tick | steer, set the look target, step a pursuit, count down a timer, read what slower tiers decided | query the world (entities, block scans, raycasts), allocate collections |
| **MEDIUM** | every 20 ticks, staggered | sensing: **one** bounded entity query, classified in one pass | block scans, more than one query |
| **SLOW** | every 100 ticks, staggered, offset from MEDIUM | environment: block searches (shelter, home, hoard), anything touching many block states | run every tick on failure |
| **EVENT** | never on a clock | react to player interaction, damage, harvest, bonding, a spell or a death nearby: set a wake flag; the next goal evaluation looks around (while a higher-priority movement goal such as vanilla's panic holds MOVE, the wake waits for it) | do the reaction's work inside the event handler beyond one bounded query |

`AiCadence.due(entity)` answers "does this tier run for this creature on this tick". It is
`(tickCount + entityId + offset) % period == 0`: creatures spawned or loaded together have consecutive ids, so a
herd spreads its looks evenly over the period instead of all looking on one tick (the old `tickCount % 20` put every
creature in a freshly loaded chunk on the same tick). `AiCadence.every(entity, n)` is the same for any other period
(an ability's 10- or 30-tick beat), half a period out of phase with sensing. Every clocked period and offset is
even, because vanilla evaluates goals only on ticks where `tickCount + id` is even — a tier checked from `canUse`
can never be silently skipped (`AiScheduleTest` proves both).

## 2. Building blocks

For each: tick frequency, world queries, allocation, network traffic, scaling.

| Feature | Frequency | World queries | Allocation | Network | Scaling |
|---|---|---|---|---|---|
| `AiCadence` (`due`, `every`) | per call | none | none | none | O(1) |
| `AiBackoff` — retry pacing for searches that can fail | one check per call; failed search waits base → 2× … → max | none itself; caps the guarded search at one per backoff period | none (two longs, an int) | none | O(1) per guarded search |
| `AiPursuit` — re-path pacing for a chase | one check per FAST tick while chasing | none itself; ≤ 1 path request per 4–20 ticks (+15 after a failed search) | none (four fields) | none | O(pursuers), independent of target speed |
| EVENT wake (`EcologyState.wake()`) | on an event | none itself; ≤ 1 extra look per creature per `EVENT_MIN_GAP` (5) ticks | none (a flag) | none | O(events), capped per creature |
| `AiScratch` — reused query list | per sensing call | — | none after warm-up (one list per thread, dropped if a look grew it past 256) | none | — |
| `EntityTypeCache` — per-type view of a datapack table | per lookup | none | one map entry per entity type, once | none | O(1); rebuilt when the table's map is replaced (`/reload`) |
| `AiMetrics` — work counters | per counted event | none | none (`LongAdder` increment) | none | O(1) |

## 3. The behaviour built on them

| Behaviour | Tier | Frequency | World queries | Allocation | Network | Scaling |
|---|---|---|---|---|---|---|
| Ecology look around (`EcologySensing.perceive`) | MEDIUM (+EVENT) | 1 per creature per 20 ticks, + ≤ 1 per 5 ticks after an event | 1 entity query, radius `sense_radius` (4–32, default 16) | `Perception` + a few small records per look; no list (scratch) | none — ecology state is server-only, never synced | O(creatures × neighbours within radius) per second, spread evenly over the 20 ticks |
| Ecology decision (`EcologyBrain.decide`) | MEDIUM | with each look | none (pure) | one `Decision` | none | O(1) per look |
| Steering, fleeing, hunting, defending (`EcologyGoal.tick`) | FAST | every tick while an intent holds | none | none | none | O(active creatures) |
| Flee position search | FAST, backed off | only when the path ran out; after a failure every 5 → 10 → 20 ticks | `DefaultRandomPos.getPosAway` (≤ 10 block probes) | one `Vec3` | none | O(fleeing creatures) |
| Shelter search | SLOW-capped backoff | only when the path ran out; after a failure 1 s → 2 → 4 → 5 s | ≤ 8 `LandRandomPos` samples + `canSeeSky` | `Vec3`s | none | O(resting creatures) / period |
| Hunt / defend chase, walker | FAST + `AiPursuit` | path ≤ 1 per 4–20 ticks (+15 after a failed search) | vanilla path search per request | vanilla's | entity movement (vanilla) | O(pursuers) |
| Hunt / defend chase, flier that can see its target | FAST steer + `AiPursuit` check | move-control target every tick; sight re-checked per 4–20 ticks | 1 raycast per check; a path only when sight is blocked or it stops gaining | none | entity movement (vanilla) | O(pursuers) |
| Wander, large flier (body ≥ 1.5 wide: dragons, griffin, thunderbird…) | vanilla 1-in-120 roll | per wander start | ≤ 6 candidates × (1 heightmap read + ≤ 24 box collision checks); backs off 1 → 5 s when boxed in | one `Vec3` | entity movement (vanilla) | O(large fliers) |
| Wander, small flier / walker / swimmer | vanilla goals, unchanged | — | vanilla path search | vanilla | vanilla | vanilla |
| Stimulus: spell cast | EVENT | per successful cast | 1 query, radius 24 | the query's list | none | O(casts) |
| Stimulus: death | EVENT | per death **of a creature someone can react to** (has ecology, or is a mod creature) | 1 query, radius 16; **0** for any other death (mob farms) | the query's list | none | O(relevant deaths) |
| Stimulus: damage | EVENT | per hit on a mod creature | 0, or 1 herd query (radius 1.5 × herd radius) for a social species | the query's list | none | O(hits) |
| Player interaction (feed, handle, harvest, bond) | EVENT | per interaction | 0 (wake flag) | none | none | O(interactions) |
| Idle clips (`CreatureIdleGoal`) | SLOW when unplayable | a playable creature: one clip per its idle delay; an unplayable one asks again every 100 ticks | none | none after the first evaluation (playable list cached by identity) | GeckoLib trigger for a played clip (unchanged) | O(creatures) / period |
| Ability beats (aura, heal, pack, gaze…) | `every(n)` | each ability's own period, staggered | the ability's own (players via `level.players()`, see below) | as before | as before | O(creatures with the ability) / period |
| `AbilitySupport.nearbyPlayers` | — | per call | 0 entity queries: walks `level.players()` | none when nobody is near (`List.of()`) | none | O(players) |
| Camouflage | FAST check, refresh ~2/s | per tick: one effect lookup | none | one effect instance per refresh, not per tick | none (effect refresh on a mob is not sent) | O(camouflaged creatures) |
| Bond tick (`tickBond`) | FAST timers, 1 Hz staggered work | — | as before | none (profile from `EntityTypeCache`) | as before | O(bondable creatures) |
| Bestiary watching (`BestiaryDiscoveryHandler.scan`) | per player, 1 Hz | 1 query per player (radius 16); line of sight at most once per creature, only when an entry needs it | as before | tier changes only | O(players × creatures in 16) per second |

## 4. Audit of existing goals (findings and what changed)

| # | Where | Finding | Change | Perceivable? |
|---|---|---|---|---|
| 1 | `EcologyGoal.hunt/defend` | `navigation.moveTo(target)` every tick: a new path search whenever the prey crossed a block. **19.4 % of the server thread** in the baseline profile (mostly 3D flying path searches). | `AiPursuit` (vanilla `MeleeAttackGoal` pacing) | No: the pursuer still closes and bites; every vanilla hostile mob paths this way. All hunting/territory GameTests unchanged. |
| 1b | `EcologyGoal.hunt` (fliers) | even paced, each 3D flying path search cost ~10 ms for a dragon-sized body (`FlyNodeEvaluator` classifies every block of the box around every node) | a flier that can see its target steers at it with its move control, as vanilla's flying hunters do; paths only when sight is blocked or it stops gaining | No: in open air the path was the straight line; it still closes, turns and bites (`creature_ai_flying_predator_chases_by_sight`). |
| 1c | `WaterAvoidingRandomFlyingGoal` on large fliers | every idle wander a 3D path search: **12.6 % of the server thread** after 1/1b | `DirectFlightWanderGoal` (vanilla Ghast pattern: pick, sweep the box along the line, steer) for bodies ≥ 1.5 wide | No: it still takes off, flies somewhere nearby above ground, avoids water and settles (`creature_ai_large_flier_still_wanders`). Small fliers keep planned flight. |
| 2 | `EcologyGoal.seekShelter` | 8 random samples **every tick** while no covered spot exists | `AiBackoff` 20 → 100 ticks | No: it rests where it is either way. |
| 3 | `EcologyGoal.fleeFrom` | failed `getPosAway` retried every tick | `AiBackoff` 5 → 20 ticks | No: a cornered creature holds either way. |
| 4 | Event latency | stimuli and damage were stored but acted on only at the next 1 Hz look (≤ 20 ticks late) | EVENT wake | **Better**: a hurt creature answers within ~5 ticks (`creature_ai_event_wakes_within_a_few_ticks`). |
| 5 | `EcologySensing.perceive` | new list per look, `sqrt` per neighbour, registry lookup per neighbour | scratch list, squared distances, `EntityTypeCache` | No (same classification). |
| 6 | `CreatureIdleGoal` | for a creature with no playable idle clip (most of the roster) a stream + list on every goal evaluation | cached playable list; unplayable → ask again after a SLOW period | No: nothing could play. |
| 7 | `Camouflage` | fresh 40-tick Invisibility every tick | top up at ≤ 20 ticks left | No: invisible on every tick (`creature_ai_camouflage_stays_continuous`). |
| 8 | `AbilitySupport.nearbyPlayers` | section-walking entity query for players, every tick off cooldown (Nundu, Jarvey, Sphinx) | `level.players()` | No: identical set. |
| 9 | `DeathGazeGoal` | player queries via entity sections | `level.players()` + the same box test | No. |
| 10 | `BondableBeast.bondProfile` | registry + map lookup every tick per creature | `EntityTypeCache` | No. |
| 11 | ~20 ability / regen gates | `tickCount % n`: every creature loaded in one tick fires on one tick (p95/p99 spikes) | `AiCadence.every` | No: same frequency, different phase. |
| 12 | `CreatureStimuli.onDeath` | a 16-block query for every death in the world | skipped when nobody can react (proved exact: such a death's only listeners are its own kind, which have no ecology either) | No (`creature_ai_irrelevant_death_costs_no_query`). |
| 13 | Bestiary scan | line-of-sight raycast for every creature before knowing if an entry needs it | lazy, at most once per creature | No: same answers. |

Left alone, on purpose:
- **Vanilla wandering** (`RandomStrollGoal` → walk path search, ~10 % in the crowded arena): it is the wandering
  players see, and its node budget follows `followRange` (16–18 for almost every creature, vanilla-sized).
- **Vanilla physics** of big hitboxes (Basilisk, Acromantula, Hippogriff `LivingEntity.tick`): collision cost scales
  with box size; changing hitboxes changes the game.
- **Dementor aura** (O(Dementors × victims)), out of scope as before (Azkaban only).

## 5. Measurement

`PerfScenario` (`-Dwandb.perf=true`), now with `-Dwandb.perf.copies` / `-Dwandb.perf.half` and a `WANDB_AI` counter
line. Recipe (JDK 21 for every JVM, and a `.jfc` copy of `profile.jfc` with `jdk.ExecutionSample` at 1 ms —
this JDK rejects the `event#setting` shorthand):

```
JAVA_HOME=<jdk21> JAVA_TOOL_OPTIONS="-Dwandb.perf=true -Dwandb.perf.copies=4 -Dwandb.perf.half=24 \
  -XX:StartFlightRecording=settings=<sample1ms.jfc>,filename=<out>-%p.jfr" ./gradlew runGameTestServer --no-daemon
```

**The scenario was measuring the wrong thing.** Before this pass, most of the arena lay outside the test's
entity-ticking chunks: most creatures were never ticked (their `tickCount` was 0 at the end of the run), so the
2026-09-29 figures (~1.5–2.7 ms/tick) covered a fraction of the roster. The arena's chunks are now force-loaded for
the run and the log prints how many creatures actually ticked. All numbers below use the corrected scenario.

### Tick time (corrected scenario, no JFR unless noted; 1200 sampled ticks each)

| Arena | State | Runs: mean ms/tick | p95 | p99 | max | creatures alive at end |
|---|---|---|---|---|---|---|
| 33×33, 192 spawned | before | 6.46 (JFR) · 6.14 · 6.55 | 11.1 · 9.5 · 10.9 | 18.6 · 19.2 · 20.0 | 64 · 39 · 47 | 132 · 147 · 131 |
| 33×33, 192 spawned | after | 6.04 (JFR) · 5.80 · 5.71 | 8.9 · 8.9 · 8.4 | 12.5 · 12.3 · 11.8 | 25 · 25 · 17 | 146 · 143 · 143 |
| 49×49, 384 spawned | before | 12.49 (JFR) · 11.84 | 22.4 · 20.8 | 41.5 · 34.9 | 54 · 58 | 273 · 273 |
| 49×49, 384 spawned | after | 10.68 (JFR) · 10.25 · 10.29 | 14.2 · 13.5 · 14.8 | 18.9 · 18.5 · 18.7 | 41 · 33 · 31 | 255 · 283 · 282 |

Mean **−8 %** (small arena) and **−15 %** (larger), p95 **−17 % / −33 %**, p99 **−37 % / −50 %**, worst tick roughly
halved — with as many or more creatures alive at the end (predators that eat stop hunting; the arena's death count
varies run to run, which is why raw means move ±0.4 ms between identical runs).

### Where the time goes (JFR share of server-thread samples × that run's mean)

| Subsystem | 49×49 before | 49×49 after | 33×33 before | 33×33 after |
|---|---|---|---|---|
| Mod creature AI, all | **6.60 ms** | **1.34 ms** | 1.64 ms | 0.54 ms |
| — ecology goal + sensing | 6.43 | 1.09 | 1.45 | 0.37 |
| — of which path search | 6.12 | 0.70 | 1.30 | 0.19 |
| — abilities | 0.09 | 0.14 | 0.09 | 0.08 |
| — bond | 0.05 | 0.06 | 0.05 | 0.03 |
| — idle goal | 0.002 | 0.003 | 0.005 | 0.006 |
| — bestiary scan | 0.03 | 0.05 | 0.04 | 0.04 |
| Vanilla wander path search | 1.13 | 0.58 | 0.62 | 0.34 |
| Vanilla collision / push | 0.42 | 0.97 | 0.29 | 0.36 |
| Creature tick, total | 11.51 | 9.04 | 5.35 | 4.70 |

Per creature alive, mod creature AI went from about **24 µs to 5 µs per tick** in the larger arena. Collision grew
because creatures that no longer stall on path searches move and crowd more — the arena packs 384 creatures into
49×49 blocks; that is vanilla physics and scales with density, not with this code.

The intermediate step is worth recording: pacing the chase alone (`AiPursuit`) cut ecology path search to half,
but each remaining flying search still cost ~10 ms, and the time freed went straight into fed predators'
**wandering**, also planned by the flying path finder. Only after flying chases steer by sight and large fliers
wander by straight flight did the tick times fall. Raw MSPT would have hidden all of this; compare subsystems.

### Work counters (`WANDB_AI`, per run of 1500 ticks, 49×49)

About 5 400 ecology looks (≈ 1 per living creature per second, as designed), 100–450 event wakes (100–250 of them
deferred by the 5-tick gap during fights), 35–155 paced path requests, 2 700–4 700 skipped hopeless searches (each
of these used to be a search on that tick), 230–390 stimulus queries.

### Guard tests

| Test | Pins |
|---|---|
| `AiScheduleTest` (JUnit) | tier frequencies; one look per tick across a 20-creature herd; goal-parity of clocked tiers; SLOW never with MEDIUM; `every` staggering; backoff doubling/cap/reset; pursuit delays; scratch list never handed out twice |
| `creature_ai_event_wakes_within_a_few_ticks` | a unicorn hurt two ticks after a look runs within `EVENT_MIN_GAP + 3` ticks, not at its next look — by its ecology, or by vanilla's panic when that starts first (it holds MOVE, so the ecology goal waits — the likely cause of one unreproduced failure in six runs) |
| `creature_ai_pursuit_is_paced` | a hippogriff chasing moving prey asks for at most one path per four ticks |
| `creature_ai_flying_predator_chases_by_sight` | a dragon catches a sheep it can see, with almost no path searches |
| `creature_ai_large_flier_still_wanders` | a dragon left alone takes off and flies somewhere (and is on the direct-flight wander) |
| `creature_ai_camouflage_stays_continuous` | a Moke is invisible on every tick with the cheaper refresh |
| `creature_ai_irrelevant_death_costs_no_query` | a zombie's death raises no query; a unicorn's does |
| `ecology_looks_around_only_every_second` (existing) | one look per creature per 20 ticks |
| All `ecology_*` GameTests (existing) | flee, regroup, territory, hunt-until-fed, magic, herd scatter, nocturnal, field notes — unchanged behaviour |

## 6. Adding smarter behaviour without paying for it

1. Pick the tier first. If it needs to know about the world, it is MEDIUM at best — read `EcologyState.perception()`
   (already gathered) before adding a query. If it touches blocks, it is SLOW. If it is a reaction, it is EVENT: wake
   the creature and let the brain decide.
2. One query per look. Classify everything you need in the existing pass in `EcologySensing.classify`; never add a
   second query per creature per second.
3. Never retry a failed search on the next tick: wrap it in an `AiBackoff`.
4. Chasing anything goes through `AiPursuit`; wandering and fleeing go through vanilla navigation and random-pos
   helpers.
5. No collection allocation on FAST: borrow `AiScratch`, return `List.of()` for "none", cache per-definition lists
   by identity.
6. Datapack tables keyed by creature id are read through an `EntityTypeCache`, never `ENTITY_TYPE.getKey` in a loop.
7. Players are found with `level.players()`, never an entity query.
8. Nothing ecological is synced. A new client-visible behaviour rides on what vanilla already sends (movement, look,
   effects, sounds, GeckoLib triggers).
9. Count it: add an `AiMetrics` counter, and pin a budget in a GameTest like `ecology_looks_around_only_every_second`.
