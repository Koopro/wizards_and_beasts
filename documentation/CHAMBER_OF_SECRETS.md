# Chamber of Secrets — audit and vertical-slice design (2026-10-29)

Status: **built 2026-10-29 on `feat/chamber-of-secrets`; checkpointed 2026-11-01.** The checkpoint preserves the work. It
does not close anything in section 4: "Real-client checks still needed", the open multiplayer items and the survival
balance questions all stand. Section 2 was approved with these decisions:
- ship the module as PREVIEW;
- the Chamber earns the sword and the Hat recalls it;
- the chart comes when the path is walked, and from caches.

Section 3 records what differs from the design and what has been verified.

## 1. What exists (verified in source, 2026-10-29)

| Part | State | Evidence |
|---|---|---|
| Structure type | Registered jigsaw wrapper; `findGenerationPoint` is empty unless `Module.CHAMBER_OF_SECRETS` is on | `chamber/structure/ChamberOfSecretsStructure.java:51-59` |
| Placement | random_spread, spacing 40 / separation 18, every Overworld biome, buried at absolute Y -40 | `worldgen/structure_set/chamber_of_secrets.json`, `worldgen/structure/chamber_of_secrets.json` |
| Template | **Placeholder:** a sealed 9×6×9 stone-brick box with no entrance, chest or decoration | `structure/chamber_of_secrets/chamber.nbt`, written by `tools/gen_chamber_nbt.py` |
| Module | **Ships DISABLED**; admin apply mode NEW_CHUNKS | `module/ModuleDefaults.java:56` |
| Basilisk spawn | Natural spawn override, one per chamber, only while the chamber is not marked slain | `chamber/ChamberBasilisk.java:50-61`, `BeastSpawnHandler.java:77-80` |
| Slain record | SavedData per dimension, keyed by start chunk; set by **any** basilisk death inside a piece | `ChamberBasilisk.Data`, `BasiliskEntity.die:223-228` |
| Basilisk | 136 HP, bite + venom, constrict, territory 24. Gaze: 20-tick windup, then death on direct sight or petrification through water or a spyglass. Counterplay: blindness, Blindfold, Protego, a blinded basilisk, a rooster at dawn | `creatures/basilisk.json`, `basilisk/BasiliskGaze.java`, `DeathGazeGoal.java` |
| Basilisk bugs | **Despawns** (no persistence: vanilla far-away despawn), no boss bar, and a ritual basilisk dying in a chamber marks it slain | `GenericGroundBeastEntity` (no `requiresCustomPersistence`) |
| Sword | One per world (`GryffindorSword` SavedData). Presented by the Sorting Hat to a wearer at ≤30% HP who struck a foe from the `sword_of_gryffindor_foes` tag in the last 10 s. Imbued with venom by a basilisk kill. Freed again when its item entity is destroyed | `artefact/gryffindor/*` |
| Hat source | Hidden wizarding cache, weight 1 of 30; the cache is a 12% roll in five vanilla structures | `loot_table/chests/hidden_wizarding_cache.json:206-219` |
| Spell × block seams | Revelio outlines anything in `revelio_reveals` or with a block entity. Alohomora toggles any block with an `OPEN` property at lock tier 0. Aguamenti fills cauldrons. **There is no generic "spell hits block" hook.** | `RevelioScan.java:93-98`, `SpellCastTargetedHandler.java:356-412`, `AguamentiHelper` |
| Parseltongue | A synced flag that nothing sets or reads | `PlayerAbilityData.java:24` |

**Conclusion:** the basilisk and the sword are real systems. The dungeon is not: there is no way in, nothing to do inside, no designed encounter, and the module is off.

## 2. Proposed vertical slice

### Player experience
1. **Lead.** A *Serpent Chart*: a vanilla explorer map with an X over the nearest Chamber that has not been cleared. The chart search reuses `SiteSearch` (expedition fix, Milestone B).
   - It is handed out when the Apprentice's Path is walked. This is optional: nothing forces the player to go.
   - It also turns up in the hidden wizarding cache, for players who switched the path off.
2. **Entrance.** A small ruined stone ring with serpent inlay at the surface, then a ladder shaft down to a safe landing (light, a sign, a bed-free alcove).
3. **The pipes.** A flooded, winding corridor that is the navigation section. It is dark, with water underfoot and landmarks along the way.
4. **Challenge 1 — the hidden seal.** The corridor ends in a blank wall.
   - The seal is a new `ChamberSealBlock` hidden among the bricks. **Revelio** outlines it, through the `revelio_reveals` tag.
   - **Alohomora** opens it, through the existing `OPEN` toggle at tier 0.
   - Wandless route: a stone button behind hanging vines in the side alcove powers the same seal.
   - A sign states the rule in localized text.
5. **Challenge 2 — the serpent's basin.** A portcullis made of `ChamberSealBlock` columns, powered by a comparator under a dry basin (a vanilla cauldron).
   - Fill the basin with **Aguamenti**, or with a water bucket taken from the pipes, and the gate lifts.
   - Emptying the basin closes it again, so it never softlocks: water is always available one room back.
6. **The Chamber.** A long pillared hall with serpent statues and side pools.
   - The pools give indirect sight, so a glimpse in water petrifies instead of killing.
   - Entering the hall wakes the Basilisk. It is a designed spawn, not a random one: persistent, homed to the hall, with a boss bar, an intro line ("Do not meet its eyes: look away, or watch it in the water") and the existing gaze windup as the tell.
7. **Victory.** The sword rises from the hall's dais for the player who struck the last blow, if the world's sword is not already out. It is venom-imbued at once, because it was earned by slaying a basilisk.
   - Every participant gets the quest record.
   - The exit is a second ladder up beside the dais, so nobody has to walk back through the pipes.

### Rules
- **Uniqueness:** one sword per world, the user's own ruling of 2026-10-22, kept. A Chamber cleared while the sword is out pays the basilisk fang and the quest record, but no second sword.
- **Sorting Hat migration (proposed):** the Hat stops being the way to *earn* the sword. Being presented the sword in need becomes a **recall**: only for a wearer who has completed the Chamber quest, and only while the world's sword is not out (lost or destroyed). Existing swords and their records are untouched.
- **Encounter state** (per chamber, extending `ChamberBasilisk.Data`): `SLEEPING → AWAKE → SLAIN`.
  - **AWAKE** with nobody in the hall for 2 minutes: the basilisk heals fully and goes back to sleep at home. There is no despawn.
  - **SLAIN** is permanent.
  - Only the chamber's own basilisk can mark it slain.
- **Quest record** (a new small player attachment): `CHARTED → ENTERED → SEAL_OPENED → GATE_OPENED → BASILISK_SLAIN`, plus `SWORD_EARNED`. Each stage gives one toast and is shown on the Identity page once the path is walked.
- **Module:** this needs a decision (see the question).
  - Disabled: no new chambers, no charts handed out, the quest line hidden. Records are kept, and old chambers keep working, as blocks do.
- **Not planned:** Parseltongue, heritage gates, an O.W.L. gate, a puzzle engine.

### Tests planned
- Unit: the encounter state machine, quest stage rules, sword-award rules (one per world, recall eligibility) and the chart search (already exists).
- Game tests in small built scenes:
  - the seal opens by Alohomora and by redstone;
  - the basin opens and closes the gate;
  - the basilisk wakes once, is persistent and resets after the hall empties;
  - one sword with simultaneous killers;
  - no second sword while one is out;
  - recall only for quest-completers;
  - quest records survive death and relog.
- Real client: walk the generated structure in a disposable world, with screenshots.

## 3. As built (2026-10-29)

- **Code:**
  - `chamber/`: `ChamberSealBlock`, `ChamberGateBlock`, `ChamberHeartBlock` and its block entity,
    `ChamberEncounter` (pure rules), `ChamberStage` and `ChamberQuest` with `ChamberQuestService`, `ChamberSword`,
    `ChamberCharts`, `ChamberEvents`;
  - the template comes from `tools/gen_chamber_nbt.py` (41×40×41; the shaft is at the centre for heightmap
    projection; `start_height -36` puts the ring on the ground).
- **Differences from the design:**
  - The knocker is a button set in the wall right above the seal, not behind vines. A powered wall block is the only
    redstone-free way to reach the seal, and Revelio outlines the button too (buttons are in `revelio_reveals`).
  - The hall's exit is a shortcut iron door to the landing, opened by a lever on the hall side, not a second ladder:
    a ladder to the surface could end inside a hill.
  - Stage credit for the seal and the gate goes to every player within 10 blocks (whoever opened it is not known to
    the block).
- **Verified:**
  - Unit tests: `ChamberRulesTest`, and `ChamberTemplateTest`, which checks the template against the heart's
    geometry.
  - Game tests: `ChamberTests` (6) and the Hat-recall checks in `GryffindorTests`.
  - A mutation check: without persistence, the wake test failed.
  - A real-client walkthrough (`ChamberShowcaseCapture`, screenshots in `run/screenshots/chamber_showcase`) showed:
    the ring, the landing, the seal wall and the seal opening, the dry and full basin with the gate, the hall with
    the woken basilisk and its boss bar, and the sword over the dais.
- **Not verified:** see section 4.

## 4. Headless validation (2026-10-31)

No graphical client was used. Everything here ran under Gradle, the game-test server or a `--nogui` dedicated server.

### Natural generation: verified on real terrain

The game-test world is flat and direct template placement proves nothing about generation, so `ChamberWorldProbe` (dev
only, inert without `WB_CHAMBER_PROBE`) runs a real dedicated server on a noise world, in `build/chamber-probe`:

```
WB_CHAMBER_PROBE=generate ./gradlew runChamberProbe
WB_CHAMBER_PROBE=restart  ./gradlew runChamberProbe   # same directory, after generate
```

Final result, seeds 4815162342, 271828182 and 8675309, ten Chambers each:

| Check | Result |
|---|---|
| Complete: heart with its block entity, 2 seal, 12 gate, cauldron, comparator, chest with loot, 5 worded signs, 29 ladders, door, lever, trapdoor | 30/30 |
| Rotations seen | all four |
| Wall, floor and ceiling blocks unchanged after generation | 2752/2752 at all 30 |
| Walk from the landing with the doors shut | exactly the template's 426 cells at all 30; basin room and hall unreachable |
| Hall roof exposed | 0 columns at all 30 |
| Seal group opens; comparator-held gate rises (one Chamber per seed) | yes |
| Basilisk killed: recorded against the Chamber's start, chart search passes it over | yes |
| Chart search from spawn | 26-116 ms |
| Grid cells yielding a Chamber | 26.6-27.3% of 640×640-block cells |

Worlds created with the shipped config report the module as PREVIEW.

The probe found three real defects, all fixed:
- **Mineshafts cut through walls.** A mineshaft breached one of 20 Chambers (146 wall blocks gone, 6,427 cells walkable,
  the hall reachable past both puzzles). The cause was the step moved to `underground_structures` when the dungeon was
  built, the same step as mineshafts and earlier than ore veins. Restored to `underground_decoration`.
- **Biome read underground.** Vanilla checks a jigsaw's biome at its start height, 36 blocks down. One ring came up in a
  river. `ChamberSite` now checks the surface biome and rejects a liquid surface.
- **Halls out of hillsides.** Of 20 sites, two had 37% and 72% of the hall's roof uncovered, and a third 2%.
  `ChamberSite` rejects ground falling more than 16 blocks within 18 of the ring (the roof is 20 down). With the river
  check, this cost about a quarter of the sites (35% of grid cells to 27%).

Dimensions: the structure set is limited by biome, and the tag lists Overworld biomes only. Salts are unique. A Chamber
overlapped a village once in 30 sites, and an ancient city and trial chambers once each, all harmless: the Chamber
generates after them. No exclusion zone is needed on this evidence.

Existing worlds: module state lives in the world (`ModuleStateData`) and the config only seeds a new world, so an
existing world keeps DISABLED until an operator changes it. Chambers then appear only in chunks not yet generated. A
placeholder Chamber in a world that had the module on is left as it was, with no heart.

### Multiplayer

The Chamber has no client-to-server packets. Every decision is the server's, in one thread, and the client is only
told stage ids. Fixed and pinned by a test:
- **Spell kills named nobody (the serious one).** Bolt and cone spells deal `magic()` damage, with no attacker. The caster
  was not a participant, "the last blow" fell to the nearest player, and the fang did not drop. `ChamberEvents.spellStruck`
  now names the caster for a Chamber's basilisk only.
  - **Mod-wide, not fixed:** every other creature has the same gap, so a spell kill drops no `killed_by_player` loot.
    Naming the caster everywhere would change aggro, companion protection and poaching, which needs its own decision.
- **A second knock closed the seal** on anyone in the doorway, or walled them in past it. The seal is now one-way.
- **The gate fell on a player standing in it** when someone else emptied the basin. It now waits for them.
- **A stale basilisk could stay** if its chunk loaded before its heart's. The heart now removes it.

Covered by new scenarios, no bug found: two players in one hall share the boss bar and it follows them out; a player who
fought and left is still credited; only the sword's owner can pick it up; quest stages survive reconnect and death.

Open:
- **An offline participant gets no credit.** A player who fought and disconnected before the kill gets no quest stage.
- **A player whose chart leads to a Chamber someone else cleared** has a stale hint and no new chart. Nothing is
  blocked: other Chambers exist, and caches hold charts.
- **Griefing:** the basin's comparator and cauldron can be mined. The gate cannot then rise, but the stone walls beside
  it can also be mined, so it is not a hard lock.

### The basilisk's gaze

Sound:
- The dot products are correct: facing within 60° of its head, the victim looking within 45° of its eyes, 16-block range.
- Line of sight is a server-side collider ray; solid blocks stop it.
- Each player has their own lock, re-measured every 5 ticks; looking away, a wall, blindness, a Blindfold or Protego
  breaks it, and the outcome is decided from fresh geometry.
- It is not lethal to creative or spectating players, nor to anyone already petrified.
- The confinement teleport, no despawn, empty-hall reset and boss-bar tracking are tested.

Problems found:
- **The hall's pools could not do what the sign said.** The water is at floor level, and a player's eyes are 1.6 blocks
  above the floor, so a line between their eyes and the basilisk's never crosses it. The sign and warning now say what
  works (look away, spyglass, blindness). Sight through real water still petrifies: it needs water at eye height,
  such as an Aguamenti cast between them.
- **The windup is 15-20 ticks, not 20.** The first measurement already counts 5.

Needs real survival testing; nothing was changed:
- **First contact may be lethal before it can be read.** The basilisk wakes about 11 blocks from the gate, facing it. A
  player walking in looking down the hall meets its eyes at once, and the warning toast arrives in the same second as a
  0.75-1 s death. A 3-5 second grace after waking is the likely fix.
- **Whether melee is viable:** a 45° cone means looking near the head at all.
- **Whether a Blindfold, Protego or a dawn chicken trivialise the fight:** each removes the gaze or the basilisk outright.
- 136 HP and 14 a bite against a player who has just finished the Apprentice's Path.
- About one Chamber every 1,200 blocks is more common than "a major discovery" suggests. It can be thinned with
  `spacing`.

### Persistence

Verified across a real server restart on two seeds: seal open, gate raised, heart AWAKE, one bound persistent basilisk
with the heart's own id, cleared Chamber still SLAIN. The bound basilisk was not yet loaded at server start, and was
present by the check 100 ticks later.

Known limits:
- Module off and back on: nothing is lost. Waking stops while it is off, and a woken basilisk stays. A heart also does
  not wake with creatures off.
- If a heart looks before its basilisk's chunk has loaded, it forgets it and the next visitor meets a whole basilisk. No
  duplicate results; the old copy is removed.
- The empty-hall timer runs only while the hall is loaded. Leave far enough for the chunks to unload and the damage
  stays when you return.
- The heart, the sword's item entity and the `GryffindorSword` record are saved separately. A crash between them could
  lose the sword while the record says it is out, and no command clears that record.
- A basilisk dying with no credited player online (burning after a logout) clears the Chamber with no sword given.
- If no Chamber lies within reach, the chart's search reruns at each login; on an ordinary world it finds one in about
  0.1 s, but a world with none could stall the server for seconds.

No duplication route was found: a Chamber accepts one death, the world accepts one sword, and a relog pays nothing.

### Real-client checks still needed

- Read the signs up close, at every rotation.
- A survival fight: gaze telegraph (heartbeat, overlay), reaction time, melee, first contact.
- Two real clients: boss bar, toasts, sword pickup.
- A naturally generated Chamber from the surface down, in a hilly biome.
- Hall visibility during the fight; `hall_awake` was dim.