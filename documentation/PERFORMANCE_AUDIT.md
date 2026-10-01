# Wizards & Beasts — Performance Audit

`dev`, 2026-09-29. Server-side performance, measured rather than guessed. The client was reviewed but not profiled
(§5). No gameplay was changed: every fix returns the same answers, just computed more cheaply.

## 1. How it was measured

**Load scenario:** `gametest/PerfScenario`. It is registered only when the JVM runs with `-Dwandb.perf=true`, and
then *instead of* the normal suite, so the server does nothing else. The scenario:
- builds a walled 33×33 arena;
- spawns two of every registered creature (192 spawned, about 187 still alive at the end of the run);
- adds three players, ticked through `ServerPlayer.doTick` like connected players;
- warms up for 300 ticks, then samples the server's own tick-time ring (`MinecraftServer.getTickTimesNanos`) every
  100 ticks for 1200 ticks;
- logs `WANDB_PERF … mean_mspt … p95_mspt` (milliseconds per tick).

**Profiler:** Java Flight Recorder on the same run, attached through `JAVA_TOOL_OPTIONS`:

```
JAVA_TOOL_OPTIONS="-Dwandb.perf=true -XX:StartFlightRecording=settings=profile,jdk.ExecutionSample#period=1ms,filename=rec-%p.jfr" ./gradlew runGameTestServer --no-daemon
```

Execution samples on `Server thread` were aggregated by method, both inclusive and as the outermost mod frame.

**Limits:**
- One machine and one scenario: the absolute numbers are this machine's.
- Mean MSPT varies about ±0.3 ms from run to run, so a change worth less than about 10% of a tick has to be judged
  by its profile share, not by MSPT.
- The "before" row was recorded with JFR attached (the profiling settings). JFR costs a few percent, far less than
  the change measured.

## 2. Results

| Run | mean ms/tick | p95 ms/tick |
|---|---|---|
| Before (JFR, 10 ms sampling) | **10.67** | **132.96** |
| After fix 1 only (JFR, 10 ms) | 3.12 | 7.40 |
| After fix 1 only (JFR, 1 ms) | 2.92 | 6.26 |
| After fixes 1–3 (JFR, 1 ms) | 3.21 | 6.31 |
| After fixes 1–3 (no JFR), run 1 | 2.63 | 5.86 |
| After fixes 1–3 (no JFR), run 2 | 2.72 | 6.54 |

| Method (share of server-thread samples) | Before | After |
|---|---|---|
| `PocketDimensionEvents.onLevelTick` | **81.3%** | not sampled |
| `BestiaryDiscoveryHandler.scan` | 3.7% | 2.7% |
| — within it, `addObservedTicks` / `PlayerBestiaryData.copy` | 29% of the scan | gone |
| `GenericBeastEntity.fireImmune` → `hasFireImmuneAbility` | 1.1% | 0.2% |
| `GenericBeastEntity.tick` (all creatures) | 62.8% | 60.4% — of which **97% is vanilla `Mob.tick`**: pathfinding, collision, physics |

## 3. Optimisations made

### Fix 1: the pocket-dimension escape scan searched the whole world, every second, in every dimension

- **Current cost.** Once every 20 ticks in *every* loaded level, it ran
  `level.getEntitiesOfClass(ItemEntity.class, <world-border box>, …)`. A spatial entity query walks the section
  index once per chunk column in range, whether or not anything is there. Across a ±30-million-block border that
  is about 3.75 million sorted-set iterations (fastutil `LongAVLTreeSet.subSet`) per level per second. That was
  81.3% of all server-thread samples, and it caused the 133 ms p95: a 100+ ms stall every second.
- **Why it matters.** `POCKET_DIMENSIONS` ships ENABLED, so every default world paid this, in the overworld, the
  Nether, the End and the extension realm, with or without a single trunk in existence. The cost scales with the
  border, not with what is in the world.
- **Change.** `PocketDimensionEvents` now reads every loaded `ItemEntity` straight off the level's entity lookup
  (`level.getEntities(EntityTypeTest.forClass(ItemEntity.class), …)`). The old box's bounds are kept as a filter
  (y between -64 and 320, inside the world border), next to the unchanged trunk-component checks. That is linear
  in loaded item entities (usually a handful).
- **Expected benefit.** Measured: mean 10.67 → about 3 ms/tick, p95 132.96 → about 6 ms/tick.
- **Risk.** Low. Both paths see the same set of entities: the entity lookup holds the entities in tracked
  sections, the same ones the spatial query can reach. The escape roll and its rate are unchanged. No game test
  covers the escape roll itself (§7).

### Fix 2: the bestiary scan copied the whole bestiary record once per creature watched

- **Current cost.** Every second, for each player, `BestiaryDiscoveryHandler.scan` called
  `BestiaryDataHelper.addObservedTicks` once per bestiary entry being watched. Each call copied the *entire*
  record (three hash maps: tiers, harvest lockouts, watching time) and wrote it back. That is K full-record copies
  per player per second, and was 29% of the scan's cost in the scenario.
- **Why it matters.** It runs for every player, all the time, and grows with both the number of creatures nearby
  and the size of the bestiary record (107 entries).
- **Change.** Totals are accumulated during the scan (stored time plus this scan's gain; an entry is watched at
  most once per scan) and written once at the end with the new `BestiaryDataHelper.setObservedTicks`. That write
  copies the *latest* record, so tier changes made during the scan are carried through, not overwritten. Tier
  decisions still use the same total.
- **Expected benefit.** One record copy per player per second instead of K. Measured: scan 3.7% → 2.7% of the
  server thread. What remains is line-of-sight raycasts and the entity query, which are the scan's actual job.
- **Risk.** Low. Watching time is now stored after that scan's tier changes rather than before each one. Nothing
  reads watching time between the two points: tier-advance listeners (deeds, skill points, memories, sync) read
  tiers, never watching time. Bestiary game tests pass.

### Fix 3: `fireImmune()` re-resolved the creature's definition and walked its abilities on every call

- **Current cost.** Vanilla calls `fireImmune()` many times per tick for every mob (fire, lava, damage checks).
  Each call on a mod creature did an entity-type registry lookup, a definitions-map lookup and a walk of its
  ability list. `definition()` was also re-resolved for `abilities()` every tick. Together about 1.1% of the
  server thread.
- **Why it matters.** It is paid per creature, per call, all the time, and adds up with creature count.
- **Change.**
  - `GenericBeastEntity.definition()` caches its answer against the identity of `CreatureDefinitionRegistry`'s map
    (new `snapshot()` accessor). A datapack reload swaps that map, so the cache invalidates exactly when the
    definitions change.
  - The fire-immunity answer is cached against the definition object in the same way.
- **Expected benefit.** Measured: 1.1% → 0.2%, and `definition` / `CreatureDefinitionRegistry.get` no longer
  appear in the profile.
- **Risk.** Very low. The cache is keyed on the exact object it was derived from, so it is never stale, even
  across `/reload`. That is stricter than the existing `cachedTraits`, which was not touched (see §6).

## 4. Audited and left alone

| Area | Finding |
|---|---|
| `EntityTickEvent` handlers (Butterbeer, Whizzbee, Shadow Form) | Run for every entity every tick, but return after one `hasEffect` map lookup. |
| Creature ability ticks | 29 of 32 are interval- or cooldown-gated. The ability layer is about 2% of creature tick time. |
| AI goals with world queries | Niffler seek (item and block), Kelpie lure, death gaze, rooster crow, fire breath and pouch-flee are all interval-gated, mostly by earlier audits. Bowtruckle lock search runs only on a player's request. |
| `ServerTickEvent` handlers (27) | Per-player attachment reads, gated or trivially cheap; no world scans at 20 Hz. |
| Player-tick handlers | Besides the bestiary scan, no handler reached 2.5% of player tick time in the profile. |
| Dementor aura | Runs at 1 Hz, but is O(Dementors × victims) with a nested query per victim. Only Azkaban (disabled by default, and a location) has enough Dementors to matter. Out of scope. |
| Networking from tick paths | Packet sends in tick handlers are change-driven or interval-gated. No per-tick full syncs were found. |
| Client per-frame world queries | Only two: the house banner (a 1×2 box around a visible banner) and the Niffler pocket layer (radius 4, only while carrying one). Both small. |

## 5. Not measured

- **Client rendering.** There is no client in the harness. The review found no per-frame world-wide scans or
  stream chains in render or GUI hooks (§4). A client-side profile (frame time with many creatures, beams and
  HUD overlays) is the next measurement to take.
- **Real multiplayer load.** Mock players on mock connections. Packet serialisation cost on real sockets is not
  in these numbers.

## 6. Candidates not changed (with reasons)

| Candidate | Cost seen | Why not now |
|---|---|---|
| `Camouflage.tick` re-adds a 40-tick Invisibility **every tick** | 0.3% | Refreshing only when under about 20 ticks remain would behave the same, but the gain is inside noise. |
| `NunduPestilence.tick` queries nearby players every tick while off cooldown with nobody near | not visible (two Nundus) | Rare creature. A 10-tick gate would change when the first poisoning can land by up to half a second, which is a (small) behaviour change. |
| `GenericBeastEntity.cachedTraits` is never invalidated on `/reload` | none | A correctness quirk, not a cost. Invalidating it like the new definition cache would change behaviour after a reload, so it is flagged, not fixed. |
| Vanilla `Mob.tick` (pathfinding, collision): 97% of creature time | 60% of the server thread | That is Minecraft's own AI. The lever is creature *count* and spawn density, which is a design decision. The largest per-instance costs are the big-box creatures (Acromantula, Basilisk, about 2.6% each for two instances), all in vanilla collision and pathing. |

## 7. Validation

- `./gradlew build` and `runGameTestServer`: all 169 required tests pass after the changes, including the
  bestiary watching → KNOWN tests and the creature ability tests.
- The trunk-escape roll (fix 1) has no game test of its own. Its entity filter, rate and outcome code are
  unchanged; only how the candidate list is fetched changed.
- The perf scenario is in the repo (`PerfScenario`). It is inert unless `-Dwandb.perf=true` is set, so it adds
  nothing to the normal suite.
