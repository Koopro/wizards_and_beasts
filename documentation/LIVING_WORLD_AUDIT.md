# The Living Wizarding World, Phase 1: audit and blueprint (2026-11-01)

Status: **approved 2026-11-01 with changes, and built on `feat/living-world`.** Sections 1 to 12 are the audit as written,
with corrections marked. Section 13 records the decisions as approved, what was built and measured, and what is unverified.

`J/` = `src/main/java/at/koopro/wizardsandbeasts/`, `D/` = `src/main/resources/data/wizards_and_beasts/`,
`T/` = `src/test/java/at/koopro/wizardsandbeasts/`. Line numbers are from the dirty tree of `feat/chamber-of-secrets`.

Evidence labels: **Existing**, **Partial**, **Placeholder**, **Disabled**, **Missing**. A ✓ marks a claim checked by hand in
source for this audit, on top of the read-only subagent sweep. Vanilla claims were checked in
`neoforge-21.11.42-sources.jar`. Nothing was run: no Gradle task, no server, no client.

---

## Summary

**The mod already has a town in software. It lacks the place and the person.** `location/` and `market/` provide hosts,
standing, contract boards, services, supply-and-demand trade and a server-authoritative screen. Six locations use them
today, hosted by villagers the player has to convert, or by goblins found in caves. So this milestone is smaller than the
brief assumes. It needs:

1. one multi-piece structure;
2. one shared NPC entity, bound to the structure by a small post block;
3. a talk section on the existing location board;
4. a stock limit on the existing market.

No new shop, economy, quest or spell-learning system is needed, and no Mixin. The apothecary NPC hosts the existing
`apothecary` location, with the same prices, standing and board.

**Seven findings change the plan:**

| # | Finding | Consequence |
|---|---|---|
| 1 | The Apprentice's Path pays coin items, and every shop charges the vault only ✓. `GringottsBilling.charge:41` reads the vault; `ApprenticePathService:284-291` gives items. Deposits need a goblin teller, and tellers spawn only in dripstone and lush caves | A new player can reach a shop and be unable to pay. **Each hamlet needs a Gringotts branch** |
| 2 | Vanilla resets a villager's profession when it has 0 XP and no job site ✓ (`ResetProfession`, in the core package). The mod has no trades, so its villagers are always at 0 XP | Villagers are unreliable as generated staff (section 5) |
| 3 | Structure generation reads a module cache that holds shipped defaults until `ServerStartedEvent` ✓, while `prepareLevels` runs earlier ✓ | The first chunks of a world ignore that world's module state. This already affects the Chamber |
| 4 | The repo has no multi-piece jigsaw, processor list, structure datagen or non-`none` terrain adaptation ✓ | Generation is the highest technical risk, so it goes first |
| 5 | `SpellWorldGuard` is not on this branch ✓. It exists only as untracked files in `C:\tmp\wb-spell-world` | There is no area protection, and that work is at risk of loss |
| 6 | The brief's "dark with gold" direction reverses a decision: the dark sheet was built and rejected on 2026-10-21 | NPC screens stay parchment unless you rule otherwise (decision 3) |
| 7 | `feat/apprentice-path` and `feat/chamber-of-secrets` exist nowhere but this disk ✓, and the Chamber is uncommitted ✓ | Secure them before starting (decision 1) |

---

## 1. Repository and branch state

| Item | State (verified 2026-11-01) |
|---|---|
| Current branch | `feat/chamber-of-secrets` @ `a5ed7f9f` |
| Parent | `feat/apprentice-path` @ `17ae7071` (1 commit below HEAD) |
| Grandparent | `feat/heritage-2` @ `9901d76e` (4 below HEAD; pushed) |
| Ancestry | Linear down to `dev` @ `fcfea8bd`: HEAD is 36 ahead, 0 behind. `dev` is 208 ahead of `main` @ `b2b3655f` |
| Remote | `git ls-remote` shows `dev`, `main`, `feat/heritage-2`, `dev-2026-09-17-rework`, `spike/pal-eval`, `testing-feature`. **Neither `apprentice-path` nor `chamber-of-secrets` is on it** |
| Working tree | 66 entries: 38 modified (+438/-56) and 28 untracked. This is the whole Chamber of Secrets slice, its tests and docs. No stash |
| Authorisation | The Chamber's commit was not authorised, per project memory |
| Stack on HEAD only | Phases 3a to 8 plus the Path: cabinet, mirror, sword, wandmaking, brewing, harvest, spell acquisition, Heritage 2.0. `dev` has none of it, so **building on `dev` would drop it** |

**Not on this stack:**
- `feature/ui-overhaul`: 11 commits this branch lacks, forked at `fcfea8bd`. It does not touch `client/location/`, so
  `LocationScreen` work will not collide with it ✓. `ThemedChip`, `SpellMenuLayout`, `Ui.slot` and `Ui.arrow` are absent here.
- `C:\tmp\wb-spell-world`: 29 uncommitted entries, including all of `spell/world/` ✓. Its branch is merged, but the work is
  not on any branch.
- `tools/`: a local repository with no remote. `gen_chamber_nbt.py` lives only here, by your rule that Python stays off GitHub.

**Recent commits:** `a5ed7f9f` tools moved out, `17ae7071` Path docs, `22f2227a` chart fix, `6032c547` Path, `9901d76e` Obscurus.

**Test baseline, last recorded 2026-10-31, not re-run:** unit 2885 with the 6 known wand-model failures; game 461/461.
CI (`.github/workflows/ci.yml`) runs on all branches, so its `test` step should stay red until those six are fixed (inferred).

**Versions:** Minecraft 1.21.11 (`[1.21.11,1.22)`), NeoForge 21.11.42, Java 21, GeckoLib 5.4.5, Curios 14, ModDevGradle 2.0.140.
No Parchment mappings, so vanilla parameters are `p_123` in sources.

---

## 2. Existing architecture

### Registry
**Existing.** All wiring is in `J/WizardsAndBeastsMod.java:79-116`. Blocks use `ModBlocks`, items `ModItems` plus `XItemRegistry`,
entities `ModEntities` through `EntityHelper`. The rest are `ModBlockEntities`, `ModMenuTypes`, `ModSounds`, `ModParticles`,
`ModCreativeTabs`, `ModAttributes`, `ModDataComponents`, `ModAttachments`, `ModVillager`.

There is **no `ModStructures`.** Each feature owns its register (`azkaban/structure/AzkabanStructures`,
`chamber/structure/ChamberOfSecretsStructures`), with one line in the mod class. A settlement follows that pattern.

A new block needs an owning module: `everyBlockHasAnOwningModule` enforces it through the `module/<id>` tags.

### World generation
| Piece | State |
|---|---|
| Chamber | **Existing, on the dirty tree.** Wraps `JigsawStructure` by composition, because it is `final` (`ChamberOfSecretsStructure:42-65`). The JSON is read by vanilla's own codec. `ChamberSite` rejects bad ground. Size 1, rigid, `terrain_adaptation: none`, `random_spread` 40/18 |
| Azkaban | **Disabled, placeholder.** Custom `Structure`, procedural crag, 225-byte NBT, custom `one_per_world` placement |
| Jigsaw connectors, multi-piece pools | **Missing** |
| Processor lists | **Missing.** The only reference is `minecraft:empty` in one pool |
| Structure datagen | **Missing.** JSON is hand-written, NBT comes from `tools/gen_chamber_nbt.py` |
| Vanilla village pools | **Missing.** Nothing is injected, and `data/minecraft` holds tags only |
| `ChamberWorldProbe` / `runChamberProbe` | **Existing, Chamber-specific.** Real noise world, `--nogui`, restart mode. Copy it; do not parameterise it |

Vanilla limits ✓: jigsaw `size` is 0-20. `max_distance_from_center` horizontal plus 12 (for any terrain adaptation) must not
exceed 128, so the ceiling is 116. A structure block saves 48 per axis at most, but the NBT generator is not bound by that.
`Beardifier` handles rigid pool pieces individually ✓.

### Entity
**Existing.** One shared class per family is the house style: `GoblinTellerEntity:47` says "a role is not worth an
`EntityType`", and `GenericBeastEntity` is data-driven. Saving uses `addAdditionalSaveData(ValueOutput)`. No humanoid wizard NPC
exists (section 5).

### Networking
**Existing.** One registrar, `J/network/ModNetwork.java:33`, `registrar("1")`, one `ModNetworkX` per domain; 53 C2S and 138 S2C.
There is no generic guard class. The tools are `PlayerScopedState.throttle:121`, `AdminRequestLimiter`, `PacketCodecUtils`.

The right template is the location loop. `LocationActionC2SPayload` carries an intent, with a bounded string, a 4-tick
throttle and clamped counts. `LocationPresence.isAt` re-checks host, life and reach. `LocationViewS2CPayload` carries a view
built by the server. **An NPC interaction needs no new payload type**, only one more action in that loop.

Known weaknesses, none blocking:
- `abandon:190` and REFRESH skip the presence check.
- `OllivanderTrialMenu.stillValid` returns `true`.
- `LocationView` truncates silently at 64 entries and has no byte budget.

### Persistence
| Store | Use | Convention |
|---|---|---|
| Player attachment (62 in `ModAttachments`, none using `.sync`) | Per-player state | Immutable record with `EMPTY`, `optionalFieldOf` defaults, `CODEC.fieldOf("data")`, `.copyOnDeath()`, plus a sync payload |
| `SavedData` (28 classes; 26 overworld, 2 per-dimension) | World state | `SavedDataType` with a codec, `computeIfAbsent`. `MarketBook`, `TreasureSiteLedger`, `GryffindorSword` are the models |
| Block entity | Local state | `saveAdditional`/`loadAdditional`; never store empty stacks (`WandmakersBenchBlockEntity:168-183`) |
| Entity | Identity and role | `ValueOutput`, codec-based (`DuellingDummyEntity:623`) |

**Migration:** there is no framework. Codec data evolves through `optionalFieldOf` and frozen ids. `DataVersion` exists on four
legacy NBT classes only. `ModuleStateData.ofCodec:78-84` backfills new modules ✓.

### UI
**Existing.** Kit in `client/gui/ui`: `UiSurface` (`PAGE`, `NIGHT`, `of(GuiSkin)`), `Ui`, `UiText`, `ConfirmDialog`, `ThemedButton`,
`ScrollList`. Skins are the enum `WizardsPalette.GuiSkin`. Text is never scaled. `GuiScaleHelper.Layout` handles scaling.

`LocationScreen` (367 lines) is the only board and market screen: one scrolled column with standing, services, market and
contracts. **It bypasses the kit**: hand-drawn buttons, no `ScrollList`, no tooltips.

**Dialogue: Missing.** `AcromantulaSpeech:8` says so outright.

**Theme.** `todo_ui_overhaul_2026_10_21.md:98`: "User saw the night sheet on both screens and preferred the old parchment
theme". `:106`: "All remaining milestones (M4+) stay parchment. Plan's 'dark magical interfaces' sections are void." In code,
`NIGHT` (dark with gilt) is used only by in-world overlays: wheels, Apparition, spell trace. Spell HUD and wheel are off-limits
unless asked.

### Configuration
**Existing.** Module state lives in the world (`ModuleStateData`) and is seeded once from `ModuleConfig`. States are `ENABLED`,
`PREVIEW`, `DISABLED`, `COMING_SOON`; `isEnabled` means ENABLED or PREVIEW. A changed default does not reach an existing world,
but **a new module constant does**: `ofCodec` fills it from the config default ✓.

Tunables are `Config` values plus a `ConfigSettingCatalog` entry and three lang keys, or a world-owned `AdminSettings`
contributor. Worldgen JSON is not config-tunable; the module gate is the switch.

`ModuleContentGate` blocks item use, block use and `FinalizeSpawn`. It never removes anything and does not stop AI.

### Testing
**Existing.** 387 JUnit files, 461 game-test scenarios in 90 files, registered through `RegisterGameTestsEvent`, since 1.21.11 has
no `@GameTest`. `WizardTestSupport` provides `add`, `addAlone`, `placeMockPlayer` (Path off), `forceChunks`, `leaseModule`.
`ChamberWorldProbe` holds the only true server restart. No test runs over a real network: "two clients" are two mock connections.

Limits: game tests are flat, plots are about six blocks wide, and direct template placement proves nothing about natural
generation (`lessons.md:1162`).

---

## 3. Existing feature inventory

| System | Reusable seam | State | Limit |
|---|---|---|---|
| **Locations** | `LocationDefinition`, `LocationPresence`, `LocationProgression`, `LocationServices`, `LocationView` | Existing | `presence.structure` works but nothing uses it. `hostedBy` returns the first match, and `keeps` demands `Villager` when `host_profession` is set ✓ |
| **Market** | `MarketTrade`, `MarketPricing`, `MarketBook` | Existing | Stock is infinite; only price moves. Keyed by location id, server-wide |
| **Heritage** | `HeritageAPI.getPlayerHeritage:34`, `getData(p).hasCondition(..)` | Existing | Only Wizardkind ships selectable. No data-driven heritage condition outside `VisionCondition` |
| **Conditions for lines** | `VisionCondition` sealed codec ✓: HERITAGE, CONDITION, LOST_COMPANION, SPELLS_KNOWN, CREATURES_KNOWN, PROFESSION, GOAL, NOT. `ErisedSelector` is weighted | Existing | GOAL reads an empty career record, so activity and contract goals never hold ✓ |
| **Wands** | `OllivanderInteractHandler.openTrial:66`, `WandCare.offer`, `OllivanderPrice`, `wand_evaluation`, `wand_commission` | Existing | The refusal, care, trial logic sits inside the villager event. Player-facing wood and core lore is **Missing** (admin only) |
| **Bench** | `WandmakersBenchBlock` | Existing | Should work in a generated shop; not runtime-tested |
| **Spells** | `SpellLearningService`, `SpellSource.write:97`, loot modifiers, `hidden_wizarding_cache` | Existing | `TraceSource` is sealed. `MarketGood` cannot write a spell |
| **Brewing** | `BrewKnowledge.learn:47`, `lesson_potions`, textbooks in market | Existing | Market cannot write a loose recipe page |
| **Creatures** | `BestiaryDataHelper.getTier:21`, `setTier:25` (pays fees), `creature_treatment`, `magizoologist` track | Existing | No habitat query API. Using `setTier` from an NPC pays a finder's fee |
| **Healing** | Effects and items only: Dittany, Wiggenweld, `PetrifyServerLogic.cure:76`, bezoar | Partial | No player-healing service. `HeritageAPI.cure:189` is "an operator's eraser… not a remedy the world offers" |
| **Economy** | `GringottsBilling`, `MoneyFlow`, `InventoryHandover.giveOrDrop` | Existing | Vault only, or Dragots. Services are free with Gringotts off, but markets close |
| **Contracts** | `CareerTrack`, `CareerRules`, location `standing` block | Existing | Objectives are `activity` or `delivery`. Boards open from an entity host only |
| **Education** | `lesson_<subject>` services ✓, `ExamSittings`, `EducationService.nextSteps:125` | Existing | One lesson a day, O.W.L. only |
| **Expeditions** | `TreasureCharts.nearest:107`, `SiteSearch`, `ChamberCharts` | Existing | `random_spread` placements only |
| **Apprentice's Path** | `offerAdventure:179` (uncommitted), `step.*.hint` lang | Existing | **No optional-step concept** ✓: a new `PathStep` delays the Chamber chart and is effectively mandatory |
| **Discovery** | `map_discovery/*.json`, `TravelRecord.sightedSites`, `FLOO_VISITED_DESTINATIONS` | Partial | Per-player "place discovered" record is **Missing** |
| **Floo** | `FlooNetworkManager.register:142` | Partial | World-seeded hearth is **Missing**. Unowned plus public means `isLandmark`, so listed to everyone. Toll has no keeper |
| **Structures** | Chamber wrapper, probe, seal blocks, anchor pattern | Existing | Anchor conflates "not loaded" with "dead" (`ChamberEncounter:52`): fine for a boss, wrong for a shopkeeper |
| **Trace** | `CastSurvey.isMuggle:93` ✓ | Existing, module off | Not in the `muggles` tag means not a witness. No settlement exemption needed |
| **Griefing** | `spellBlockDamage`, Colloportus | Partial | Area protection **Missing** |

**Lessons with no teacher ✓.** Corrected 2026-11-01 (`OWLGradeRules.teachable`). Arithmancy, Divination and Muggle Studies are not
examined, and Transfiguration is graded by Animagus work, so none of those four has lessons, by design. Of the teachable eight,
Astronomy has no teacher anywhere, and Defence and History sit at the Ministry, which ships disabled. In a default world **three
teachable subjects have no teacher**, and the Scholar covers them with data alone.

---

## 4. Settlement design proposal

### Identity
**A wizarding hamlet built around the Crooked Spire**, a leaning clock tower visible over the trees from about 150 blocks.
Timber-framed, jettied upper floors that overhang the lane, tall twisted chimneys, shopfronts in one painted colour each,
floating candles behind every window. Warm by the shops, wrong and quiet below ground.

Canon allows only one Hogsmeade, so a repeatable structure cannot use that name. Each site rolls one name from a short list
(lang keys), stored in `SettlementData`. The working structure id is `wizarding_hamlet`. See decision 8.

### Why this generation approach

| Option | For | Against | Verdict |
|---|---|---|---|
| Extend vanilla villages (pool injection) | Terrain and roads are free | Wizard shops among Muggle witnesses; cannot have a central square; no precedent here; needs an access transformer; villagers break staff (section 5); not a "place" | Rejected for the reference settlement. A lone wandmaker's hut in villages is a fair later idea |
| One big rigid NBT | Proven by the Chamber; always complete | Every layout change rewrites one file; no variation; hard to grow | Fallback if the spike fails |
| **Hub-and-spoke jigsaw** | Rigid plaza with fixed connectors, each pointing to a one-role pool. Rotation and variants for free; new building = new NBT plus pool | No precedent here; vanilla can silently drop a piece | **Recommended** |
| Custom piece generator | Full control | Rebuilds jigsaw; Azkaban's approach is suited to one fixed site | Rejected |

A normal village pool is random, so a wandmaker might not appear. Here each connector targets a pool holding only that role's
variants, so every role is requested exactly once. `SettlementStructure` then **rejects a stub missing a required piece**,
provided `GenerationStub` exposes its pieces at that point. That is to be proved in the spike, along with
whether one wrapper can serve many JSON structures.

### Layout

```
                     N
    +------------+         +------------+
    |  Reading   |  lane   |  Keeper's  |      Each building: rigid, up to 15 wide,
    |  Room      |   :     |  Post      |      19 deep with its frontage, 16 tall.
    +-----+------+   :     +-----+------+
          |          :           |
 +--------+---+  +---+-----------+---+  +------------+
 | Wandmaker  |--|      PLAZA        |--| Apothecary |
 |            |  |   25 x 25         |  |  + herb    |
 +------------+  |   Crooked Spire   |  |    garden  |
                 |   7 x 7, ~30 tall |  +-----+------+
                 +---+-----------+---+        |
                     |           |         (cellar)
          +----------+-+     +---+-------+
          | Gringotts  |     | open plot |    future general store or healer
          | Branch     |     | (empty)   |
          +------------+     +-----------+
                     S     gate lanes at the two open sides
```

| Property | Value | Reason |
|---|---|---|
| Footprint | About 65 x 65 | 25 plaza + 19 either side + margins |
| `max_distance_from_center` | 48 | Fits; well below the ceiling of 116 |
| `size` | 2 | Plaza to building to annexe; room for lanes at 3 |
| Height | `project_start_to_heightmap: WORLD_SURFACE_WG`, start height 0 | On the ground. All rigid pieces share one Y |
| Adaptation | `beard_thin`; compare `beard_box` in the probe | Fills and shaves slight slopes |
| Site rule | Flat within a set relief over reach 32, surface biome checked, no liquid | `ChamberSite` showed vanilla's check is not enough. Start at 6 blocks and tune from the probe |
| Step | `surface_structures` | List what generates after it first (`lessons.md:1166`). Later trees need dirt, so avoid dirt yards or test them |
| Biomes | Plains, meadow, forest, birch and flower forest, taiga | New tag `has_structure/wizarding_hamlet`. Overworld only by tag |
| Placement | `random_spread`, spacing 72, separation 32, new salt | Rarer than the Chamber. Target about one per 2,500-3,000 blocks; measured in the probe. Compatible with `TreasureCharts.nearest` |

`one_per_world` was rejected: it breaks chart search, needs the access transformer on `biomeSource`, and a single site is a long
way to walk for a reference settlement.

### Buildings

| Place | Purpose | Contents | NPC |
|---|---|---|---|
| **Plaza** | Orientation, identity | Spire, basin, benches, notice board display, Keeper's post nearby | - |
| **Keeper's Post** | Board, courier work, rumours | Desk, pigeonholes, owl perch, board | Keeper |
| **Wandmaker's** | Trial, care, evaluation, commissions, bench | `wandmakers_bench`, boxes on vanilla shelves | Wandmaker |
| **Apothecary** | Ingredients, lessons, creature care | 3 cauldrons, herb garden with `mandrake_crop`, jars, shelves | Apothecary |
| **Reading Room** | Lessons, study, O.W.L. sittings | `examination_desk`, `spell_teacher` (Study Lectern), bookshelves, one spell-source chest | Scholar |
| **Gringotts Branch** | Deposit, withdraw, exchange, expeditions | A small marble front | Goblin teller |
| **The Undercroft** (hidden) | Story, reward, next lead | See below | None |

**The Undercroft** sits under the Apothecary. It reuses `ChamberSealBlock` exactly: hidden among the cellar wall, shown by Revelio,
opened by Alohomora, with a wandless knocker. Inside are a written account of what the hamlet is hiding from, a chest with the
`hidden_wizarding_cache` table (so a Serpent Chart can appear), and a sealed-off tunnel that is a hook for later. It is optional.
Nothing depends on it.

**The healer is folded into the Apothecary for now.** The building goes only when there is a service to put in it.

### NPC placement
One `settlement_post` block per role, behind each counter. NPCs stay within about 10 blocks of their post, indoors at night, and
stroll near the door by day. They do not sleep or commute. Details in section 5.

### Discovery
The experience goes: rumour or chart, the spire over the treeline, the hamlet's name on arrival, then a reason to come back.

| Hook | Mechanism | State |
|---|---|---|
| **Landmark** | The spire, visible at distance | New, in template |
| **Hamlet chart** | Vanilla map with an X, from `TreasureCharts.nearest` over a new structure tag | Reuse |
| **Chart sources** | Hidden wizarding cache (one in eight, like the Serpent Chart); the end-of-Path hand-off `offerAdventure` | Reuse |
| **Rumour** | Hosts at the Keeper's board mention the next lead through talk topics; a `rumour` service gives a treasure or Serpent chart | New service, reused charts |
| **Marauder's Map** | `map_discovery/wizarding_hamlet.json`, `type: structure` | Data only |
| **Arrival** | Posts notice a player within 16 blocks, write `known_places`, show a DISCOVERY toast once | New attachment, no player tick |
| **Hint** | Path `step.wand.hint` may mention wandmakers in hamlets | Lang only |

Not used: a global waypoint, a mandatory Path step, a hidden trail in the first slice. Jigsaw reach cannot carry a trail far.

**Future settlements** need one `worldgen/structure/<id>.json`, its pools and templates, a structure-set entry, the tag, and lang
for its names. New professions need one `npc_roles/<id>.json`, a location file if the place is new, a skin and lang. No Java.

### Visual direction

| Element | Palette |
|---|---|
| Lanes | `diagon_cobblestone`, `diagon_street_stone`, worn with `hogsmeade_worn_stone` |
| Walls, lower | `hogsmeade_stone_bricks`, `hogsmeade_worn_stone` |
| Frame, upper | `three_broomsticks_timber`, `three_broomsticks_planks`, vanilla dark oak |
| Roofs, chimneys | `hogsmeade_roof_tile`, `hogsmeade_chimney_brick` |
| Shopfronts | `diagon_shopfront_wood`, one accent each: `diagon_painted_wood_green`, `diagon_painted_wood_purple` |
| Bank | `gringotts_` marble and gold trim, sparingly |
| Light | `floating_candle`, vanilla lanterns. Warm above ground, `soul_lantern` below |

**New blocks: one, the post.** No shop signs, street lamps, bookshelves, doors or fences exist in the mod's palettes ✓, so use
vanilla hanging signs, lanterns, bookshelves, spruce doors. Chimney smoke and one `arcane_mote` source at the spire are the only
particles. Sound: 103 sound events, 0 `.ogg` ✓, all re-pitched vanilla. Use the vanilla bell at dawn at most.

**Traps:**
- Palette density drives landmark rules. `hogsmeade` fires at 96 blocks ✓, so the Marauder's Map would call this place
  Hogsmeade, and Diagon blocks would add Diagon Alley. Add a structure exclusion to `MapSurveyor` block rules, or keep each
  palette below threshold per chunk. M1 decides by measurement.
- Do not use `ministry_` blocks: `MinistryArea` would fine trespassers.
- A Gringotts palette over the threshold is correct here, and it makes the branch count as a Gringotts landmark.

---

## 5. NPC architecture recommendation

### Options

| Option | For | Against |
|---|---|---|
| **A. Vanilla villagers with mod professions** | Four professions and textures exist. Locations accept them today. Sleep, schedules and doors are free | Loses profession at 0 XP without a job site ✓. A fresh templated villager has no job-site memory, and `ResetProfession` is a core behaviour, so it may reset at once (unverified). No identity or name. Other villagers claim the POI, breed, get zombified, join raids. Looks like a vanilla village, which the brief forbids |
| **B. One `EntityType` per profession** | Simple per role | Against house precedent. Duplicates a class per role |
| **C. GeckoLib humanoid per role** | Goblin and elf rigs exist | A rig and animation per look. Only goblins and elves are ready |
| **D. One shared entity with data-driven roles, on the player rig** | Follows `GoblinTellerEntity:47`. `DuellingDummyRenderer` gives a player model, armour, held wand, with no rigging | Needs a post for identity, simple goals, skins, and a Presence field |

**Recommended: D**, with villagers untouched as existing hosts, so player-made shops keep working.

### Design

```
worldgen template                     server
+-----------------+     spawns,      +---------------------+    hosts     +-----------------+
| settlement_post |---- recovers --->| SettlementNpcEntity |------------->| locations/*.json|
| (block + BE)    |                  | role id (synced)    |   role id    | market, board,  |
+--------+--------+                  +----------+----------+              | services        |
         | reads/writes                         | same attachment         +-----------------+
         v                                      v
+---------------------------+        +-----------------------+
| SettlementData (SavedData)|<-------| settlement_post       |
| site key -> name, posts   |  join, | attachment on entity  |
| post -> role, uuid, state |  death | (NPC or goblin teller)|
+---------------------------+        +-----------------------+
```

**Entity.** `SettlementNpcEntity extends PathfinderMob implements Npc` (the interface exists ✓). Synced role id. Persistent
(`setPersistenceRequired`), not pushable or leashable, no natural spawn. 20 HP. `Npc` means `isPerson` holds, so the Unforgivables
count against it as they should.

**Role data**, `D/npc_roles/<id>.json`, loaded like `Locations`:

```json
{
  "location": "wizards_and_beasts:apothecary",
  "skin": "wizards_and_beasts:apothecary",
  "names": ["npc.wizards_and_beasts.apothecary.name.0"],
  "greetings": [
    { "text": "npc.wizards_and_beasts.apothecary.greet.vampire", "weight": 3,
      "when": [{ "type": "condition", "condition": "vampirism" }] },
    { "text": "npc.wizards_and_beasts.apothecary.greet.default" }
  ],
  "topics": [
    { "id": "mandrakes", "label": "...", "reply": "...",
      "when": [{ "type": "spells_known", "min": 1 }] }
  ]
}
```

**Hosting.** `Presence` gains an optional `host_role`. `keeps` checks the profession for a `Villager` and the role for an NPC, so
`apothecary.json` lists both hosts. `hostedBy` then stays a first match, with no ambiguity.

**Identity and persistence.** Keep identity in `SavedData`, not in a block entity. `ChamberEncounter:52` treats "AWAKE and not loaded"
as forget, which would rewrite a shopkeeper every chunk load. Death is therefore recorded by the entity, never inferred.

| Post state | On look (1 Hz, only when the 3x3 chunks' entities are loaded, `ServerLevel.areEntitiesLoaded` ✓) |
|---|---|
| `VACANT` | Write the record, then spawn |
| `PRESENT`, found | If over 24 blocks away and unobserved, return it. Otherwise nothing |
| `PRESENT`, not found | After 30 consecutive looks, treat as lost: new UUID, respawn |
| `AWAY` | At the recorded dawn, spawn the same identity |

A copy that loads later with the wrong UUID is discarded on `EntityJoinLevelEvent`, as `ChamberEvents.onJoin:78` does, except that
`SavedData` is always readable, so the load-order gap the Chamber has does not exist. The rule lives in a pure `PostRules`, tested
like `ChamberEncounter`. NPCs are **never** stored in templates, which removes spawn duplication and `finalizeSpawn` concerns.

**Death** (decision 2). At lethal damage, `LivingDeathEvent` is cancelled, the NPC Apparates away with a crack and smoke, no drop, no
XP, and returns next dawn. The same handler covers the goblin teller through the attachment, with no edit to its class. Griefers
get a closed shop for at most a day, and there is no corpse, respawn cost or duplication route.

**Behaviour.** Goals only: look at player, look around, restricted stroll, `MoveTowardsRestrictionGoal` (the pattern in
`Augurey:99-122`), `OpenDoorGoal`, and stand still while being spoken to. Use `AiCadence` for anything repeated. No schedules or work
animations in the slice. They stay in their shop at night and stroll near the door by day. No sleeping, so no bed logic.

**Interaction.** `mobInteract` on the server: gate on module, then `LocationPresence.admit`, then `openAtHost`. Plain use opens it, and
sneaking is unneeded here. All later actions go through the existing packet and re-check `isAt` with `host_reach`.

**Dialogue.** A talk section on `LocationView`: one greeting, up to four topic buttons. A topic is label plus reply. There is no graph
and no script language. The client sends `TOPIC` with an id; the server re-evaluates `when`, then replies with a lang key. An unknown or
ineligible topic refreshes the board. Conditions are `VisionCondition` read through `Gazer`, so a heritage line is one JSON entry.
Talk does not need the location to be open, so a closed shop still speaks. Move `VisionCondition` only when a third user appears.

Heritage lines stay flavour. `HERITAGE_IDENTITY_STATUS.md:241` says reactions were "deliberately not added as a discount or reputation
number", and `:287` says "only once the world has people who would react". Basic services are never gated by heritage.

**Rendering.** A copy of `DuellingDummyRenderer` choosing the texture from the role. Skins are 64x64 from a new local generator with
the `Generator` marker. Robes are baked in, since mobs have no Curios (`EQUIPMENT_ARCHITECTURE.md:142`). The teller keeps its GeckoLib model.

**Classification.** Not in `muggles`, so not a Trace witness. Not in `ministry_officials`. Not in Dragot tags, so it refuses Dragots like
the apothecary villager.

**Multiplayer.** Everything runs on the server thread. Two players can hold boards open at once; `MarketTrade.buy` is sequential, so
the second sees the first's pressure ✓. The host slot is per player, dropped at logout, and re-admitted at next use.

**Mixin.** None needed. Everything is an entity subclass, a structure wrapper, events, or data.

---

## 6. Profession design

**Minimum set: Keeper, Wandmaker, Apothecary, Scholar, plus the existing goblin teller.** Marginal cost per role is low, since each is
one JSON, one skin, lang and at most one service. The General Merchant is a stretch goal. The Healer is deferred.

| Profession | Reuses | Services | UI, data | Difficulty | Testing |
|---|---|---|---|---|---|
| **Goblin teller** | Entity, `gringotts.json`, vault | All existing | None; post binding only | Low | Bound teller recovers; deposit then shop buy |
| **Wandmaker** | `ollivanders.json`, `openTrial`, `WandCare`, evaluation, commission, bench | Existing, plus `wand_trial`, which wraps the logic moved out of `OllivanderInteractHandler` | Role JSON, wood and core topics (new lang) | Medium | Trial from NPC; first wand free once; wandless heritage refusal; villager path unchanged |
| **Apothecary** | `apothecary.json` entirely | 5 existing, 23 buys, 18 sells | Role JSON | Low | Hosts same market as villager; prices shared |
| **Scholar** | `lesson_<subject>`, lectern, exam desk, loot | Lessons for untaught subjects. New `reading_room.json` | Role JSON, location, lang | Low | Lesson once a day; desk sits in template; no spell sold |
| **Keeper** | `courier_dispatch`, contract engine, charts | Board, courier, `rumour` | New `hamlet.json` with standing; 4-6 contracts from existing activity types; new `rumour` service | Medium | Board seeded per player; contracts pay once; rumour once per day |
| **General Merchant** (stretch) | Market, sell-only | None | New location, sell-only | Low | Sell-only has no faucet effect |
| **Healer** (deferred) | Dittany, Wiggenweld, Wolfsbane exist as items | **None exist for players** | Would need a new service | High for little | - |

**Doctrine.** The Scholar teaches subjects, never spells. `SYSTEM_INTERACTION_MAP.md:137`: "Money never buys magic".
`WIZARD_LIFE_AUDIT.md:234`: "Do not... sell spells". `SpellLearningService:47` allows buying "a book someone else wrote", but not
the contents of your head, and the old teacher was retired as "generic RPG trainer behaviour". So the Reading Room offers a found chest
and a Study Lectern, not a catalogue.

**Healer.** `HeritageAPI.cure` must not be called, and the conditions have no cure on purpose. Selling Dittany and Wiggenweld already
happens at the Apothecary, whose lore text can point the way.

---

## 7. Economy proposal

**Currency.** Only Knuts, Sickles and Galleons, through `GringottsBilling`; Dragots by existing policy. 29 K = 1 S, 17 S = 1 G, 493 K = 1 G.
No new `MoneyFlow` is expected except for rumours, if they carry a fee.

**Bank.** Keep vault-only purchase. Pocket coins are deliberately fine-proof, so taking them at shops would change a settled rule. The
branch teller fixes the gap: coins from the Path or loot go in at the counter, across the plaza from the shops. Selling goods also credits
the vault, so that route exists too. I found no starting vault balance. The Path pays 696 K in coin. Fix the claim at
`ApprenticePathService:52`, which says two Sickles "buy blank spellbooks at the wandmaker's", either in text (mention the counter) or by
paying those rewards into the vault (decision 4).

**Why existing locations.** A second apothecary file would add a second market ceiling. Currently the lawful faucet is 7,383 K/day against a
pinned ceiling of 25,000 (`MarketDataTest:204-221`), computed across every file in `D/locations`. Reusing ids leaves it unchanged, and
gives no cross-hamlet arbitrage. `MarketDataTest` also scans new files, so a sell-only store adds nothing.

**Limited stock.** Add one optional field, `stock`, to a good, on the existing pressure:

```
available = stock - ceil(pressure)     (buy side only)
```

There is no new timer or inventory. Pressure already decays on read with `half_life_days`, is stored in overworld `SavedData` under
`location|side|good`, and survives unloads, relogs and restarts. After a full sell-out, half returns in one half-life. `buy` clamps `count`
before pricing, at about line 343. Sold-out goods say so on the board. Ordinary ingredients stay unlimited, with price as the brake: at
depth 16, buying sixteen units doubles the price. That is the clear reason to keep them unlimited. Limited stock goes on rare goods only.

**Purchase flow (existing, unchanged).** Admit (module, presence, tier), count, weekly limit, item exists, law, price, charge, give, write pressure
and record. A full inventory drops at the feet, never destroys. Sale: `canCredit`, allowance, remove goods, credit, restore on failure.

**Rules.**
- Selling is allowed only for what a location's `buys` lists.
- No per-settlement price scaling, no reputation discount beyond existing tiers.
- Merchants do not sell what only crafting or exploration should give; this is enforced by review of the data, and `MarketDataTest`'s no-arbitrage
  and no-profitable-contract pins.
- Not a replacement for crafting: wands stay commissions and trials, brews are not newly sold.

**Exploits considered.**

| Attempt | Why it fails |
|---|---|
| Unload or relog to restock | Stock comes from `MarketBook`, which is decayed by time, not by load |
| Two players buy the last unit | One server thread; the second call sees the new pressure and refuses |
| Packet spam | 4-tick throttle, `MAX_BUY`, clamps |
| Buy from one hamlet, sell at another | One key per location id, so no spread exists |
| Kill and respawn NPC for reset | Prices live in `MarketBook`; boards and standing in the player |
| Contract twice on two hamlets | One location id, one `TrackRecord` |
| Sell to a shop while the vault is full | `canCredit` before removal |

**Known consequence (decision 5).** All hamlets share one price, one board and one standing per shop. That reads as a chain, which is fine for one
settlement. An optional market scope could separate them later, if ever needed.

---

## 8. Technical risks

| Risk | Level | Mitigation |
|---|---|---|
| **Multi-piece jigsaw has no precedent here.** Piece dropped, overlapping, or floating | High | First task of M1 is a greybox spike. Fallback is one rigid NBT |
| **Natural generation differs from direct placement** (the Chamber's mineshaft breach, river ring, exposed halls) | High | `SettlementWorldProbe` on three seeds, block-by-block, flood fill, before any art |
| **Terrain.** Adaptation is unproven on rigid wide pieces | High | Site rule, compare `beard_thin` and `beard_box` in the probe |
| **Module gate timing** ✓ | Medium | Load world state before spawn generation. `LevelEvent.Load` on the overworld is the candidate; verify order. Also reset between worlds, as no stop hook exists (inferred) |
| **Existing worlds.** A new site at the edge of generated chunks is cut off, losing buildings and posts, because old chunks are not rewritten | Medium | The hamlet still works, with fewer shops. State it in the changelog. Decision 10 |
| **Duplicate or lost NPCs** | Medium | Single `SavedData` truth, UUID check on join, no entities in templates, pure rules, restart test |
| **Landmark rules mislabel the hamlet** | Medium | Exclusion or per-chunk counts; test in probe |
| **Griefing.** A hamlet is breakable and Alohomora has no claim check | Medium | Accept for survival, as vanilla villages. Post unbreakable. Decision 11 |
| **Art.** No generator for buildings or skins, and looks must be shown first | Medium | Greybox first; one building and one NPC shown side by side before rollout |
| **Unobserved dark rooms**, as with the Chamber's dais | Medium | Real-client walkthrough is part of done |
| **Combat AI of creatures near NPCs** | Low | Monsters do not target this type. Check creature ecology hostility in tests |
| **Entity counts** | Low | 5 NPCs per site, loaded only near players |
| **NPC ticking** | Low | No brain, vanilla goals, stop at simulation distance |
| **Pathfinding** | Low | Radius 10, doors only. Stray return when unobserved |
| **Post ticking** | Low | 1 Hz while loaded, 6 posts per site, cheap checks first |
| **Chart search cost** | Low | Chamber: 26-116 ms. Site height sampling is dearer, so check biome first. A world without a site must not stall (known Chamber limit): run the search off the login path, or cap it |
| **Generation cost** | Low | About 9-25 height samples per candidate. Measure in the probe |
| **Save growth** | Low | About 100 bytes per post, bounded by sites found |
| **Payload size** | Low | Talk is under 5 lang keys; `LocationView` caps at 64 |
| **Merchant sync** | Low | Server view refreshes in place after each action |
| **Client rendering** | Low | Reused player model and a few 64x64 textures |
| **Global state** | Low | `PlayerScopedState` for the host slot only |
| **Save compatibility** | Low | `optionalFieldOf` on all new fields. Removing the mod loses NPCs and posts only |
| **Stacked-branch loss** | **High, now** | Decision 1 |

---

## 9. Test strategy

Behaviour only. Each test should fail if the behaviour breaks; mutation-check the ones that guard money or duplication.

### Unit (JUnit)

| Test | Covers |
|---|---|
| `SettlementSiteTest` | Ground rule edges: relief, liquid, biome |
| `SettlementTemplateTest` | Parses every NBT: required post, role, connectors match pools, entry reachable, palette under landmark limits |
| `PostRulesTest` | All state transitions: vacant, present, lost after 30 looks, away until dawn, no spawn while entities unloaded |
| `NpcRoleDataTest` | Roles name real locations; lang keys exist; conditions parse; no `goal` that reads an empty record; default greeting always eligible |
| `MarketStockTest` | `available`, clamp before price, restock with half-life |
| Extended `LocationDataTest`, `MarketDataTest` | Host roles valid; faucet below the pin; no arbitrage |
| `ModuleStateTest`, `AdminCatalogLangTest`, `everyBlockHasAnOwningModule` | New module and block are owned and worded |

### Game tests

Use `addAlone` for all market and "who is nearby" scenarios. `forceChunks` and `checkChunksTick` for moved entities. Clear barrier walls first.

| Area | Scenarios |
|---|---|
| Post, NPC | Spawns once; second look does not duplicate; saved and reloaded entity keeps role and name; wrong-UUID copy discarded; lost NPC replaced; lethal blow gives away state and no drop; returns at dawn; stray returned; teller bound the same way |
| Interaction | Out of reach refused; reach re-checked per action; dead host refused; module off answers nothing; closed location still talks |
| Dialogue | Conditional line appears only when it holds; heritage alternative; ineligible `TOPIC` refused; progress-gated topic; two players see their own lines |
| Merchants | Purchase; short of funds; full inventory; weekly limit; last unit, two players; restock; reconnect; same pressure at villager and NPC |
| Integration | Teller deposit then shop buy; wand trial from NPC; first wand free once; lesson counts toward O.W.L.; `BrewKnowledge` on recipe lesson; `creature_treatment`; courier delivery at another NPC; contract pays once |
| Discovery | First arrival pays once; second player gets own record; survives reconnect; chart once; chart skips nothing it should not |
| Trace | Cast near NPC gives no Muggle witness; vanilla villager still does |

Spell-kill note: `damageSources().magic()` names no attacker (`lessons.md:1175`), so test NPC death through a real spell as well.

### World generation (`SettlementWorldProbe`, `--nogui`, seeds 4815162342, 271828182, 8675309, ten sites each)

| Check | Pass |
|---|---|
| All required pieces and posts present | 30/30 |
| Template blocks unchanged | Every wall, floor, roof block |
| Ground contact | No floating corners over the set limit; entrances not buried |
| Walk from gate to each counter | Flood fill succeeds |
| Rotations | All four seen |
| Site frequency and biome | Within target; no site in an excluded biome or liquid |
| Dimensions | None in Nether or End |
| Duplicates | None within separation |
| Module off | None generated, including in spawn chunks |
| Existing world | Generate with module off, enable, explore: new sites only, cut sites harmless |
| Overlap with vanilla | Listed; none blocks a shop |
| Restart | NPCs, states, stock survive |
| Performance | Chart search time, tick time at a site |

### Real client (M6 only, with your consent; not during this audit)

Walk a natural hamlet from a distance: spire visible, each room legible, signs at all rotations, NPC reads as a person, doors, no dark counters.
Two clients through `runClient2`: same shop, last unit, relog. Capture with a `WB_SETTLEMENT_SHOWCASE` harness in the style of the Chamber's.

---

## 10. Detailed implementation roadmap

Order changed from the brief in two ways. A short **M0.5** handles preconditions. World generation is proved in **greybox** before art, and final
architecture moves to M5, because every Chamber failure came from terrain and none from content.

### M0.5 Preconditions
- **Objectives:** secure the stack; correct what the settlement would otherwise inherit.
- **New:** nothing. **Changes:** `ModuleLifecycleEvents` gate timing; Path money wording.
- **Dependencies:** decisions 1 and 4.
- **Risks:** Chamber test baseline must be re-run on commit.
- **Testing:** unit and game suites; module-off probe with a world that disagrees with shipped defaults.
- **Done when:** the stack is committed and backed up, and the gate holds at first chunk.
- **Not included:** any settlement code.

### M1 World generation foundation
- **Objectives:** a greybox hamlet that generates correctly on real terrain.
- **Existing:** Chamber wrapper pattern, `ChamberSite`, probe, module system.
- **New:** `Module.SETTLEMENTS`; `SettlementStructure`, `SettlementStructures`, `SettlementSite`; pools, set, tags; greybox NBT from a new local generator; `SettlementWorldProbe`.
- **Files:** `J/settlement/structure/`, `module/`, `admin/module/ModuleAdminInfo`, mod class, `build.gradle` (run config), `D/worldgen/`, `D/structure/wizarding_hamlet/`.
- **Dependencies:** M0.5, decisions 7, 8, 10.
- **Risks:** piece drops, terrain, `GenerationStub` access.
- **Testing:** template and site unit tests, all probe checks.
- **Done when:** 30/30 sites complete on three seeds, module off places none, frequency within target.
- **Not included:** NPCs, art, posts doing anything, loot.

### M2 NPC and interaction foundation
- **Objectives:** a persistent, recoverable person who talks.
- **Existing:** `LocationPresence`, `LocationView`, dummy renderer, `VisionCondition`, `AiCadence`.
- **New:** `SettlementNpcEntity`, `NpcRole(s)`, `SettlementData`, `SettlementPostBlock` + BE, `PostRules`, `SettlementEvents`, talk section, `TOPIC` action, `host_role`.
- **Files:** `J/settlement/`, `J/location/`, `J/network/location/`, `J/client/location/LocationScreen`, `J/client/settlement/`, registries, lang.
- **Dependencies:** M1 for placement only; can be tested alone. Decisions 2, 3, 6.
- **Risks:** duplicates, chunk order, `LocationScreen` being outside the kit.
- **Testing:** `PostRulesTest`, `NpcRoleDataTest`, Post/NPC, Interaction and Dialogue game tests; restart probe.
- **Done when:** one placeholder role lives through death, restart and two players, and a **one-NPC look is shown beside the alternatives**.
- **Not included:** shops, schedules, skins beyond one.

### M3 First functional professions
- **Objectives:** the working core: bank, wands, apothecary, in the generated hamlet.
- **Existing:** `gringotts`, `ollivanders`, `apothecary` locations; teller; trial.
- **New:** three role files; `wand_trial`; teller binding; two skins.
- **Files:** `D/npc_roles/`, `D/locations/` (hosts), `J/wand/` (extract), `J/location/`.
- **Dependencies:** M2, decision 5.
- **Risks:** villager path regressing, `OllivanderTrialMenu.stillValid`.
- **Testing:** Integration game tests; existing `LocationTests` stay green.
- **Done when:** a new player can walk in with Path coins, deposit, buy an ingredient and take a first wand.
- **Not included:** stock, Keeper, Scholar.

### M4 Merchant and economy integration
- **Objectives:** limited stock; Keeper and Scholar; contracts.
- **Existing:** `MarketBook`, `CareerTrack`, `lesson_<subject>`, courier.
- **New:** `stock` field; board note; `hamlet.json`; `reading_room.json`; two roles; two skins; contract set.
- **Files:** `J/market/`, `D/locations/`, `D/npc_roles/`, lang.
- **Dependencies:** M3, decision 9.
- **Risks:** faucet pin, contract rewards.
- **Testing:** `MarketStockTest`, Merchant game tests, `MarketDataTest`.
- **Done when:** faucet below pin, all exploit rows in section 7 covered by a test.
- **Not included:** healer, per-settlement prices, spell sales. General Merchant only if time allows.

### M5 Architecture, discovery and hidden content
- **Objectives:** the place players see and find.
- **Existing:** charts, `map_discovery`, `offerAdventure`, seal blocks, cache loot.
- **New:** final building NBT; Undercroft; `known_places`; `HamletCharts`; `rumour`; map marker art; arrival toast; site names.
- **Files:** `tools/` (local), `D/structure/`, `D/loot_table/`, `J/settlement/`, `J/journey/` (hand-off only), lang.
- **Dependencies:** M4, decision 12. One finished building approved first.
- **Risks:** re-run the whole probe after final templates; dark rooms.
- **Testing:** Discovery game tests; full probe.
- **Done when:** a player with no wiki can go from chart to Undercroft.
- **Not included:** Floo hearth (needs a listing rule), trail, second settlement, new blocks except the post.

### M6 Real-client validation and polish
- **Objectives:** see it, and fix what only eyes catch.
- **New:** showcase harness; two-client session; `ALPHA_SMOKE`, `CHANGELOG`, `KNOWN_ISSUES`, `LOCATIONS` updates.
- **Dependencies:** all; your time at the machine.
- **Risks:** late rework of lighting and scale.
- **Testing:** section 9 real-client list.
- **Done when:** walkthrough accepted, smoke steps written, module default confirmed.
- **Not included:** new features.

---

## 11. Proposed file and package architecture

`J/settlement/` is new and unclaimed ✓. It matches feature packages such as `chamber/` and `location/`. `J/network/location/` stays the only network
home, so there is no `network/settlement/`.

```
J/settlement/
  SettlementData.java           SavedData (overworld): site key -> name, post records
  SettlementEvents.java         entity join, death; module checks
  KnownPlaces.java              player attachment: sites found
  HamletCharts.java             chart issue, as ChamberCharts
  SettlementWorldProbe.java     dev only, inert without WB_SETTLEMENT_PROBE
  structure/
    SettlementStructure.java    gated jigsaw wrapper
    SettlementStructures.java   DeferredRegister
    SettlementSite.java         pure ground rule
  post/
    SettlementPostBlock.java
    SettlementPostBlockEntity.java
    PostRules.java              pure transitions
  npc/
    SettlementNpcEntity.java
    NpcRole.java                record + codec
    NpcRoles.java               datapack loader
    NpcTalk.java                greeting and topic choice

J/client/settlement/
  SettlementNpcRenderer.java

Changed:
  J/location/LocationDefinition.java    Presence.host_role
  J/location/LocationPresence.java      keeps()
  J/location/LocationView.java          talk section
  J/location/LocationProgression.java   topic action
  J/location/LocationServices.java      wand_trial, rumour
  J/network/location/LocationActionC2SPayload.java    TOPIC
  J/client/location/LocationScreen.java talk section
  J/market/MarketGood.java              stock
  J/market/MarketTrade.java             clamp
  J/wand/...OllivanderInteractHandler   extract visit()
  J/module/Module.java, ModuleDefaults.java; J/admin/module/ModuleAdminInfo.java
  J/registry/ModEntities, ModBlocks, ModBlockEntities, ModAttachments
  J/WizardsAndBeastsMod.java            SettlementStructures.register

D/worldgen/structure/wizarding_hamlet.json
D/worldgen/structure_set/wizarding_hamlet.json
D/worldgen/template_pool/wizarding_hamlet/{plaza,keeper,wandmaker,apothecary,reading_room,bank,open_plot}.json
D/structure/wizarding_hamlet/*.nbt
D/tags/worldgen/biome/has_structure/wizarding_hamlet.json
D/tags/worldgen/structure/settlement.json
D/npc_roles/{keeper,wandmaker,apothecary,scholar}.json
D/locations/{hamlet,reading_room}.json      (hosts added to apothecary, ollivanders, gringotts)
D/map_discovery/wizarding_hamlet.json
D/loot_table/chests/{reading_room,undercroft}.json

T/settlement/...                      unit tests
J/gametest/SettlementTests.java       one contribute() line in WizardsAndBeastsGameTests
```

Generators stay in `tools/` and are never pushed. Their `.nbt` and `.png` output is committed.

---

## 12. Approval decisions

### Needed before implementation

| # | Decision | Recommendation |
|---|---|---|
| 1 | **Base and backup.** Commit the Chamber, then branch? The stack is on this disk only | Commit the Chamber on its branch, push the stack to a backup, branch `feat/living-world` from it. Rescue `C:\tmp\wb-spell-world` onto a branch |
| 2 | **NPC death.** Mortal, or unkillable? | Lethal damage makes them Apparate away; back next dawn; no drops or XP |
| 3 | **UI.** The brief says dark and gold; you rejected the dark sheet on 2026-10-21 | Shops and talk stay on parchment `LocationScreen`, with skins. If you want dark, I build one screen and show it side by side first. Also: when does `feature/ui-overhaul` merge? |
| 4 | **Money.** Keep vault-only shops with a teller in every hamlet, and fix the Path? | Yes. Keep vault-only. Teller in each hamlet. Path text explains the counter |
| 5 | **Shared shops.** Every hamlet shares one apothecary's prices, board and standing | Yes for this phase |
| 6 | **NPC model.** Shared entity on the player rig with robes baked into skins | Yes. Villagers remain for player-built shops |
| 7 | **Generation.** Hub-and-spoke jigsaw; about one per 2,500-3,000 blocks | Yes. Fallback is one rigid NBT. Fix the number after the probe |
| 8 | **Identity.** Hamlets with rolled names, built around the Crooked Spire; not Hogsmeade | Yes. Tell me if you prefer one named, unique place |
| 9 | **Scholar and spells.** Lessons in the untaught subjects, never spells | Yes. Subjects, corrected: Astronomy, Defence Against the Dark Arts, History of Magic |
| 10 | **Module.** New `SETTLEMENTS`, shipped PREVIEW. It reaches existing worlds on upgrade through backfill, in new chunks only, and sites at the frontier may be cut | PREVIEW, with a changelog line. Ship DISABLED if you would rather keep existing worlds untouched |
| 11 | **Protection.** Hamlets breakable like vanilla villages, or wait for `SpellWorldGuard`? | Breakable in the slice; posts unbreakable. Land the guard as separate work |
| 12 | **Path hand-off.** Hamlet chart offered at the end of the Path, beside the Serpent Chart, with no new step | Yes |

### Details I can decide during implementation
Names of classes and lang keys; exact relief limit; `beard_thin` or `beard_box`; the 24-block stray limit and 30-look grace; stroll rate; piece
sizes; contract list and rewards inside existing pins; stock numbers on rare goods; topic text; site name list; how the landmark clash is resolved;
which particles and whether the bell rings.

### Stale documents found, left unchanged
- `Module.java:66-71` and `CHAMBER_OF_SECRETS.md` section 1 say the Chamber ships DISABLED; `ModuleDefaults:57` says PREVIEW.
- `KNOWN_ISSUES.md` section 6 says the Chamber places nothing, and section 8 says no block entity, structure or brewing is tested.
- `LOCATIONS.md` section 1 calls the Chamber a placeholder.
- `ECONOMY.md` section 8 says the apothecary sells 8 goods (it sells 18), and section 9 says no Floo toll (`FlooTolls` exists).
- `GUI_DESIGN_SYSTEM.md:96-101` describes dark skins; all skins are paper in code.
- `ApprenticePathService:52` claims Sickles buy books at the wandmaker's; see section 7.

---

## 13. Results (2026-11-01)

Written on `feat/living-world`. Nothing here was pushed or merged. **No graphical client was used:** everything below ran under
Gradle, the game-test server or a `--nogui` dedicated server.

### Where the build differs from the blueprint

| Blueprint | As approved and built |
|---|---|
| Stock derived from `MarketBook` pressure, shared across hamlets | Prices global; stock per site in `SettlementData`, with assortment and shelf size seeded per site |
| Back next dawn | Same, and never sooner than two minutes |
| `rumour` service | Dropped. Rumours are talk topics; charts come from the Path and caches |
| Scholar teaches five untaught subjects | Corrected: Astronomy, Defence, History. The other subjects cannot have lessons (section 3) |
| Flat stock numbers | `shop` block on the role file |
| `surface_structures`, `beard_thin`, spacing 72/32, relief 6 | `surface_structures`, `beard_box`, spacing 40/20, relief 10, exclusion 8 chunks from villages |

### Data boundaries (requirement D)

| Level | Holds | Where |
|---|---|---|
| Global | Prices, price pressure, economic rules, location and role files | `MarketBook`, `locations/*.json`, `npc_roles/*.json` |
| Settlement | Who keeps each post, whether they are present or away, the site's seed, its shelves | `SettlementData`, overworld `SavedData` |
| Player | Standing, boards, weekly limits, unlocks, places found, whether charted | `location_standing`, `market_record`, `known_places` attachments |

### Module initialisation (requirement A)

World state now reaches the cache at the overworld's `LevelEvent.Load`, which vanilla fires before the spawn search
(`MinecraftServer.createLevels`). The old comparison that decides on a recipe reload is kept, because an early load
would otherwise have silenced it.

Proved on a fresh world configured with `settlements`, `chamber_of_secrets` and `structures` disabled:

| Run | First chunk saw | World stores | Gated recipe after start |
|---|---|---|---|
| Fix in | DISABLED | DISABLED | absent, correct |
| Fix removed | **PREVIEW**, the defect | DISABLED | absent |

**Chamber implication:** before this fix a world configured with the Chamber off could get one in its first chunks, and
Azkaban was the reverse. Existing worlds keep what generated. The fix is on `feat/living-world`, not on the Chamber checkpoint.

### Generation (requirement B)

**Superseded by section 14.** The figures below came from a weaker probe. Its monster-room, floor and counter rows understated the defects.

Hub-and-spoke jigsaw is confirmed; the rigid NBT fallback was not needed. Seeds 4815162342, 271828182, 8675309, ten sites each.

| Check | Result |
|---|---|
| All six pieces and all five posts present | 30/30 |
| Rotations | All four, 30 of each |
| Refused for a missing building | 0 |
| Every counter reachable on foot | 30/30 |
| Footprint can be left on all four sides | 30/30 |
| Village overlap | 0 |
| Template blocks changed | 39 of about 147,000, all at one site: a monster room in the Undercroft's outer wall |
| Trunks in the hamlet's open air | 2 sites, 3 cells or fewer |
| Floor cells over nothing | 0 at 16 sites, up to 68 at 13, and 311 at one, by a ravine; all still walkable |
| Strict "clean" sites | 26/30 |
| Grid yield | 3.8-6.9% of cells |
| Distribution | One hamlet per 2,440-3,266 blocks. Nearest to spawn 975-2,296. Nearest neighbour median 969-1,547, least 419 |
| Biomes | Forest and plains mostly, then snowy plains, birch, taiga |
| Module off | 0 of 625 cells, no people |

What the probe changed:
- Yield was 0.6-1.6% at first. Water rejected three quarters of flat cells, and sampling a square rejected land for ponds at
  corners nothing stands on. Sampling the cross-shaped footprint, relief 10 and a denser grid fixed it.
- `top_layer_modification` left about 1,000 floating tree blocks, so it was abandoned.
- `beard_box` supported floors better than `beard_thin`.
- It found the bank's floor overwriting its post, and two wrong role names. `SettlementLayoutTest` now pins both.

Chart search is 0.2-0.8 s if run at once, so `HamletCharts` runs it at 3 ms a tick. `SiteSearchWalkTest` proves the walk finds what
the whole search finds. The tick-budgeted path itself has not run on real terrain with a real player.

### NPC architecture (decision 6)

Confirmed: one `SettlementNpcEntity` plays every role from data, and the goblin teller is bound by attachment with no change to
its class. On all three seeds five people spawned on real terrain, and the same five, by UUID and name, came back through a real
server restart, with no duplicates.

### Tests

Unit 2940 run, 6 failed: the known wand-model tests, unchanged. Game 473/473, 12 of them new.

### Spell-world work (requirement E)

Inspected, archived in `C:\tmp\wb-backups\2026-11-01`, and **not copied or changed**.
- `C:\tmp\wb-spell-world` is at `cd45b5bb`, 63 commits behind the Chamber tip. Uncommitted: 21 modified files; new `spell/world/`
  (1,340 lines), its tests, data maps, tags and `SPELL_WORLD_INTERACTIONS.md`; two crash reports from 2026-10-14, unexplained.
- `git apply --check` of its 21 modified files against this stack failed at `WizardsAndBeastsGameTests.java`. A clean text apply
  elsewhere would prove little: five more of those files changed since its base, and it rewrites `SpellHelper`.
- It would need a planned port, with its own tests. It is not a dependency of anything here.

### Still unverified (requirement F and this work)

Nothing below should be read as done.

**Hamlets:**
- Nothing has been seen in a client: buildings, Spire, skins, talk section, sign text at each rotation, lighting, darkness at night.
  The rule from the UI work stands: one building and one person are shown before the look is accepted.
- No two-client run. "Two players" in the game tests are mock connections on one server.
- Whether NPC strolling looks right, or a person gets stuck. No game test runs their AI at length.
- ~~A hamlet at the edge of explored land in an existing world.~~ Measured in section 14: it does not come out cut short.
- Whether a player actually finds one by chart; `HamletCharts` end to end on real terrain.
- Whether Wandmaker's Bench, cauldrons, desk and lectern work in a generated building. Each is placed, and none was used.
- Undercroft: seal rotation, the ladder, the button as knocker, Revelio. Only placement was checked.
- Economy balance: prices are the existing ones, and the Keeper's contract rewards have never been played.
- Marker art: the hamlet uses the existing village marker.

**The Chamber, unchanged from `CHAMBER_OF_SECRETS.md` section 4:** no real-client walk of a natural Chamber, no survival fight,
no two-client run, signs at each rotation unread, hall visibility, plus the open decisions on sword ownership and a gaze grace.

**Parked:** Dark-theme comparison; `feature/ui-overhaul` merge; `SpellWorldGuard`.

## 14. Terrain integrity and generation hardening (2026-11-02)

Branch `feat/living-world`, HEAD `33506296`, all of it uncommitted. Backup of the prior work: `C:\tmp\wb-backups\2026-11-02-generation-wip`.
No building, profession, quest, UI or economy change. The jigsaw is unchanged.

This section supersedes the generation figures in section 13. Three of them were wrong, because the old probe could not see the defect:

| Section 13 said | Measured with a stricter probe, same three seeds, fixes off |
|---|---|
| Monster room breach at one site in 30 | Reproduced, and the wider sample found other structures doing the same |
| Unsupported floor "up to 311, at one site" | 45 of 110 sites, 2,803 cells, most 409. Cellars, never measured: 19 sites, 358 cells |
| Every counter reachable, 30/30 | That check used a window of 4 and counted doors as blocked. Replaced by a door-aware walk |
| Strict clean 26/30 | 52/110 |

### Root causes

**A. Monster room in the Undercroft.** A vanilla feature, not a layout fault: 0 piece collisions in 110 sites. `MonsterRoomFeature` runs in an earlier step than the hamlet, but a room begun in a neighbouring chunk reaches 4 blocks into this one, and that chunk may decorate later, so it writes over pieces already placed. Its edge cells call `setBlock` with no `features_cannot_replace` check. Reproduced on seed 8675309 at chunk -110,211, plaza CLOCKWISE_90: 47 blocks changed below the floor, including a soul lantern and a sign.

**B. Unsupported floors.** The site check runs at `STRUCTURE_STARTS`, before carvers, so it cannot see carver caves or ravines. `beard_box` works from the noise stage and cannot fill them either. Nothing placed under the floor afterwards. Reproduced at 409 cells, and the 311 from the audit on seed 4815162342. Deepest void 41.

**C. Other structures through a hamlet (new).** The first twelve-seed sample found seven sites: ruined portals (5), a pillager outpost, trail ruins. On seed 987654321, chunk 177,164, a portal wrote 17 blocks of netherrack and magma through a floor and took a post, leaving four people. The Chamber overlaps at five control sites were the same fault: only a placement's exclusion zone keeps two structures apart, and most have none.

**D. Vegetation in rooms (new).** A tree from the neighbouring chunk's pass grew through a room: 17 control sites, 122 cells. Same route as A: a neighbour's feature arriving late.

### The fix

| File | Change |
|---|---|
| `mixin/MonsterRoomFeatureMixin` (new, in `"mixins"`) | Cancels `MonsterRoomFeature.place` when the origin's chunk references a settlement. |
| `settlement/structure/SettlementGround` (new) | Footing after placement: a 2-block plate under each floor column, stone piers on a 4-block grid, only if rock lies within 24. Bounded scan per column, and a floor over a canyon ends in a plate rather than a tower. |
| `settlement/structure/SettlementRooms` (new) | On `ChunkEvent.Load` of a new chunk, clears non-air from a building's rooms. Open ground is left alone. Runs when all neighbours have finished features. |
| `settlement/structure/SettlementStructure` | `assess()` returns a verdict as well as the stub. New `OCCUPIED` verdict: refuses a site if any other spread-placed structure would start within 12 chunks and reach the hamlet's box plus 12 blocks. A cheap biome read rejects most candidates first. |
| `settlement/structure/SettlementSite` | Named verdicts, so the probe can report why each site was refused. |
| `settlement/HamletCharts` | The settlement tag now has one home, in `SettlementGround`. |
| Templates, six `.nbt` | Re-generated from `tools/gen_hamlet_nbt.py` (local only). |
| `settlement/SettlementWorldProbe`, `SettlementLifeProbe` | Whole-template compare, piece collisions, cellar floors, door-aware walk, verdict tally, TSV per site, restart re-inspection, gate and existing-world modes. |
| `gametest/SettlementTests`, `SettlementGroundTest`, `SettlementLayoutTest` | Regression tests below. |

**Why a mixin:** NeoForge has no feature-placement event. A biome modifier would remove dungeons from every biome a hamlet can stand in. Overriding the placed-feature file would fight other mods. The room must be stopped before it starts, because it writes into placed pieces from a neighbouring chunk. The `OCCUPIED` rule and the footing use no mixin.

**Why the footing is bounded:** the brief ruled out blindly filling ravines. Masonry is 21,792 blocks across 371 sites, 845 at most, deepest pier 23. 6,398 columns hang over a gap by design, on a plate.

### Regression tests

| Test | Covers | Proof it can fail |
|---|---|---|
| `settlement_assembly_holds_at_every_turn` | Every piece and connector, four rotations, overlap | Moved connector: "0 of reading_room" |
| `settlement_stands_on_*`, 5 terrains × 4 rotations | Ravine, bottomless, flooded, uneven, cavern | `PIER_REACH` 400 alone: all four `bottomless` fail, "48 piers begun over a gap too deep" |
| `settlement_no_monster_room_breaches_the_undercroft_*` ×4 | Monster room, real feature code | Fixes off: exactly these fail, "14 rooms started under a settlement" |
| `settlement_floor_is_footed_over_a_gap_*` ×4 | Plate and piers per rotation | Fixes off |
| `settlement_rooms_are_cleared_of_vegetation_*` ×4 | Plants in rooms, Undercroft boundary | Fixes off |
| `settlement_gives_way_to_other_structures` | Eight sites that really collided, two free | `occupied` off: "seed 1234567 chunk -145, 123 came out STANDS" |
| `SettlementGroundTest` | Pier rules, including a gap too deep | Unit |
| `SettlementLayoutTest` | Overlap, clearance, posts, doors | Unit |

Mutation 1 (mixin, footing and room clearing off) failed exactly 24 tests, each with its right message.

### Expanded real terrain

Twelve seeds, up to 60 sites each, `--nogui` server: 8675309, 271828182, 4815162342, 1234567, 20261101, 42, 1, 987654321, 3141592653, 1618033988, 7777777, -5577006791947779410.

| Verdict over 7,500 grid cells | Cells |
|---|---|
| Stands | 371 |
| Biome | 3,918 |
| Inside a village's exclusion zone | 1,938 |
| Relief | 866 |
| Liquid | 344 |
| Occupied | 63 |
| Incomplete | 0 |

| Check on 371 sites | Control (110 sites, 3 seeds) | Final |
|---|---|---|
| Template blocks changed | Present | 0 |
| Cellar blocks changed | Present | 0 |
| Monster rooms at the Undercroft | Present | 0 |
| Floor cells over nothing | 2,803 | 0 |
| Cellar cells over nothing | 358 | 0 |
| Piece collisions | 0 | 0 |
| Other structures clashing | 5 sites | 0 |
| Plants in rooms | 122 cells | 0 |
| Doors or counters unreachable | Not measured | 0 |
| Site left on foot from every side | Not measured | 370 of 371 |
| Clean | 52 of 110 | 370 of 371 |

All four rotations appear, and results after a real server restart are identical.

| Plaza rotation | Clean / sites |
|---|---|
| NONE | 94/94 |
| CLOCKWISE_90 | 94/94 |
| CLOCKWISE_180 | 91/92 |
| COUNTERCLOCKWISE_90 | 91/91 |

The one site not clean is seed 1234567, chunk 409,-389: its west side is a lake 14 blocks below the floor. All five entrances work. The walker cannot swim, so the site stays marked rather than reclassified.

### Module checks (seed 271828182)

| Check | Result |
|---|---|
| `SETTLEMENTS` disabled | 0 sites, no chart, no people |
| Preview and enabled | 35 sites, identical start lists |
| Restart | Identical |
| Timing | First chunk saw the world's state with everything off and as shipped |
| Chamber off | No hamlet refused for it: `OCCUPIED` 3 against 8 |
| Existing world, explored land | 0 marks, 0 hamlets backdated across 16 sites |
| Existing world, new land | 19 of 19 sites clear of the explored land grew a hamlet, all clean |
| Azkaban with its module on | Started at its chunk, 2 pieces |
| Azkaban with its module off | Not started, as designed: it ships disabled |

**Correction to section 13 and the changelog:** a hamlet at the edge of explored land does not come out cut short. A chunk cannot reach the feature step until every start within 8 chunks is settled, so a start near old land was decided while the module was off and never appears. That costs a few hamlets near old land, not their shape. The first version of this run printed "none" everywhere and proved nothing, so it was repeated with new land as a positive control.

**Chamber:** no regression from this work, and one older defect found. On seed 8675309, chunk -590,-547, an amethyst geode from a neighbouring chunk overwrites the Chamber's wall in some runs: 18 blocks in one of 5 runs with fixes off, 204 in two of 5 with them, 0 in the rest, with a walk of 384 cells instead of 426 in the worst case. It varies between runs of one tree, because it depends on which chunk decorates last. Five sites is too few to say whether the rate differs. It is the same route as defect A, and is **not fixed**, as the Chamber is outside this milestone. Hamlets are out of its reach: the lowest floor in the sample is y 59 and geodes begin at y 30 or below.

### Performance

| Measure | Control | Final |
|---|---|---|
| Generating a whole site | 8,965 ms | 8,562 ms, no measurable overhead |
| Assess, per grid cell | | 82 ms mean (57-117 by seed) |
| Cells reaching the other-structure check | | About 6%: median 550 ms, p95 2.5 s, max 7.2 s, hamlet's own terrain and jigsaw included |
| `occupied()` alone | | 316 ms mean, 2.2 s max. 90% is asking other structures' own checks |
| Chart search from spawn | 0.7-1.3 s | 2.0 s mean, 5.5 s max |

Loops are bounded (25×25 chunks, one call per candidate, once), and nothing runs per tick. **Risk:** a chart search walks on the server thread, and one step cannot be interrupted, so a search can stall the server for up to a few seconds. It was kept because it is rare and exact. The yield cost is about 15% of candidates, wider than a strict collision by the 12-block margin.

### Tests

| Suite | Result |
|---|---|
| Unit, final code | 2,947 run, 6 failed: the same six wand-model tests as before the work |
| Game, final code | 510/510 |
| Game, 510 tests, seven runs in all | 6 clean, 1 with one failure |
| Game, clean HEAD `33506296`, six runs | 4 clean, 2 with one failure each |

**The baseline of 473/473 is not stable.** Single unrelated tests fail now and then, on clean HEAD too:

| Tree | Test | Message |
|---|---|---|
| HEAD | `settlement_lethal_blow_apparates_them_away` | "something dropped" |
| HEAD | `protego_horribilis_shrugs_off_dark` | "neither ward answered the blow" |
| Fixes off | `creature_ai_camouflage_stays_continuous` | "the moke was visible on tick 4" |
| Fixes off | `placement_dummy_faces_the_player_who_planted_it` | "found 0" |
| Final | `poaching_is_filed_when_an_official_sees_it` | "an official from elsewhere is watching" |
| Earlier, 509 tests | `ecology_looks_around_only_every_second` | "looked around 0 times" |
| Earlier, 509 tests | `ministry_trace_muggles_see_magic` | "never started ticking entities" |

None has repeated. The cause is unproven. Tests share one world, tellers left by other tests are a likely source for the poaching one, and `forceChunks` never releases, which the new tests add to. Nothing was reclassified or retried away. New failures from this work: none established.

### Remaining limitations

- Nothing has been seen in a client.
- Strongholds and Azkaban are outside the `OCCUPIED` rule: neither uses spread placement. A stronghold through a hamlet was not seen in the sample and cannot be ruled out.
- The `OCCUPIED` biome read samples, so a rare miss stays possible. It matched the exact check on all twelve seeds.
- Neighbour-feature races depend on chunk order, and a site is generated once per sample. Zero in 371 is a measurement, not a proof. Other features than monster rooms and plants are not blocked, only measured.
- The Chamber's geode race is open.
- Chart search can stall the server for a few seconds.
- Game tests fail singly at random, and the cause is open.
- Modded biomes and other world types were not sampled.

**Readiness:** READY for in-game visual alpha testing of hamlets. Everything in "Still unverified" in section 13 is still unverified, except the edge-of-explored-land item, answered above.

## 15. Visual and gameplay alpha in a real client (2026-11-03)

Branch `feat/living-world`, HEAD `33506296`, uncommitted, on top of the section 14 work. Backup first:
`C:\tmp\wb-backups\2026-11-02-alpha-start`. No commit, push, merge, reset or branch switch. No building, profession, quest
or system was added. Evidence: `C:\tmp\wb-lw-alpha\evidence` (screenshots and reports per run); chart measurements in
`C:\tmp\wb-lw-alpha\srv-before`, `srv-after`, `srv-after3`, `srv-after4`; tests in `C:\tmp\wb-lw-alpha\tests`.

### What kind of testing this was

| Kind | Used for | What it is not |
|---|---|---|
| **Real client, scripted** | Three natural hamlets: tour, walkthrough, UI | Not a person. Keys and clicks come from `client/debug/HamletPlaytest` through `KeyMapping`s and the open screen's own `mouseClicked`. It shows what the client draws and does, and cannot say how a place feels |
| **Two real clients, real dedicated server** | Multiplayer | Not a mock connection: two game processes, each joined over TCP. Scripted, through `settlement/SettlementMpProbe` |
| **Headless server probe** | Chart search before and after (`settlement/ChartSearchProbe`) | No client |
| **Game and unit tests** | Regressions, flakes | Mock players |

No person has played a hamlet by hand. Nothing below is a judgement of fun.

### The graphical environment

The physical display was in use throughout: an unrelated Minecraft 26.3 window, an IDE, a browser, and a video encode at
100% CPU until 06:20. No virtual display, VM or remote session existed, and installing one was ruled out. A **hidden Win32
desktop** worked without installing or changing anything: `CreateDesktop`, then `CreateProcess` with `lpDesktop`. The client
got the real GPU (`NVIDIA GeForce RTX 3060 ... GL 3.3.0`), screenshots are real frames, and a watchdog confirmed 0 windows on
the visible desktop and an unmoved foreground every five seconds of every run. Clients were muted, ran below normal priority
in their own directories under `C:\tmp\wb-lw-alpha`, and were started with `java` directly, never through Gradle, whose
daemon would have forked them onto the visible desktop. Tools: `C:\tmp\wb-lw-alpha\tools`.

Cost of the loaded machine: a client took 6 minutes to start, a fresh world 8 to 12, and the integrated server ran up to
30 s behind. Several scripted steps timed out on that alone; they are marked as harness below, not as game defects.

### Sites (naturally generated, fresh worlds, nothing placed by hand)

| Site | Seed, chunk | Ground | Reached on foot from the plaza, 64 blocks out (cells at the W, E, N, S edges) |
|---|---|---|---|
| Lake | 1234567, 409,-389 | Forest on a terraced hill, lake 14 below on the west | 0, 528, 371, 304 |
| Spawn | 271828182, the one nearest spawn, found by chart | Plains | 628, 634, 621, 660 |
| Ravine | 271828182, 375,-227 | Savanna plateau, the most footing of the 371 sites | 409, 480, 557, 0 |

### Visual findings

- **Composition works.** From the air and from the plaza the hamlet reads as one place: a paved star around the stone Crooked
  Spire, five buildings on its arms, lamp posts. The Spire is the landmark from every approach.
- **Night works outside.** Lamps and lit windows mark the plaza. Interiors are dim, one candle each, but readable, and the
  people can be seen.
- **Sparse on open ground.** On plains it is five small buildings on bare grass: no paths, fences or gardens past the plaza.
- **Repetition.** The Wandmaker's and Keeper's fronts are the same house. Only the bank (sandstone, iron doors) stands apart.
- **Bare interiors.** About 70% empty floor behind one long counter. The Wandmaker and the Reading Room show the same
  bookshelf wall, so the Wandmaker has no wand identity indoors. The bank (chiselled counter, checker floor, goblin) and the
  Apothecary (cauldrons over coals) have one.
- **Footing shows.** Where the land fell away, a building stands on a visible stone-and-dirt plinth.
- **Names.** The hamlet's shops are titled "Ollivanders", kept by "Old Bartle Quince", and "Slug & Jiggers Apothecary":
  Diagon Alley firms. Left for a decision.
- **Screens** (parchment) are readable at GUI scales 1 to 4 (`scale_*.png`). The Heritage, Handbook, shop, Bench, O.W.L. and
  Gringotts screens all drew correctly. Purchase and refusal feedback are toasts ("Bought 1 x ...", "Not enough in your
  vault"). Signs above doors were not read at a readable distance.

### Building by building (real client)

| | Lake | Spawn | Ravine | Notes |
|---|---|---|---|---|
| Walk in on foot from outside the site | 84 cells | 100 cells | stuck on a 1-block terrace | Real keys over natural ground |
| Keeper: board, 4 topics, 3 offers, contract accepted | not reached | no screen | **pass** | See the door below |
| Wandmaker: board, 4 topics, buy, Bench screen | pass | pass | pass | 29 Knuts, shelf 9 to 8, each time |
| Apothecary: board, 3 topics, buy, refused when broke | buy failed* | pass | pass | *the script ran out of scrolling: defect 2 |
| Reading Room: board, 4 topics, lesson taken, O.W.L. desk, chest | aim failed | pass | pass | The Study Lectern opens nothing without a blank book and ink, as designed |
| Teller: Gringotts counter | aim failed | pass | pass | Clicks dropped: defect 4 |
| Undercroft: down through the trapdoor and the ladder | no route (harness) | no route (harness) | **67 cells, real trapdoor click** | Seal: defect 7 |

### Defects found, and what was done

| # | Defect | Evidence | Done |
|---|---|---|---|
| 1 | **Flooded cellar.** At the ravine site the cellar and Undercroft lay in an aquifer. Jigsaw's default `apply_waterlogging` placed the bottom ladder, a soul lantern and a sign *holding* the water, so they became springs, and the cellar flooded once a player was near and fluids ticked. The 371-site probe compared blocks before any fluid tick, so it could not see this. Dry at the other two sites | Region read of the saved world: `ladder[waterlogged=true]`, `soul_lantern[waterlogged=true]`; screenshots | **Fixed:** `"liquid_settings": "ignore_waterlogging"` in `worldgen/structure/wizarding_hamlet.json`. On a fresh dedicated-server world at the same site: 0 waterlogged blocks after 30 s of ticking near players, against 5 in the control run. The FLOODED terrain test now fails any template block that holds water (mutation below) |
| 2 | **Shop lists bury what is for sale.** A board drew everything the keeper buys first: about 30 grey "Requires standing" rows above the Apothecary's one item for sale | Screenshots | **Fixed:** `LocationScreen` draws "For sale" first. The purchase that failed at the lake passed at both later sites |
| 3 | **Back at dawn is not dawn.** `PostRules.backAt` is game time. Sleeping or `/time set` moves day time only, so after a skipped night the record still read "AWAY, back in 16,451 game ticks". Once that game time passed, they returned with the same name | Two real clients, dedicated server | **Not fixed:** the record's saved format would change. Proposed: keep the 2,400-tick floor, and also return once the day count has passed the dawn after the blow |
| 4 | **Gringotts drops quick clicks without a word.** `VaultActionC2SPayload` allows one action per 2 server ticks and returns silently. On a lagging server honest clicks arrive together: 2 of 7, 3 of 7 and 9 of 10 deposits counted. No coin was ever lost or made | All three sites, both clients | **Not fixed:** shared economy code. Proposed: queue within the throttle, or tell the player |
| 5 | **Stale board for the other player.** After both bought from one shelf, one open board still read 19, the other 18 | Two clients | Not fixed. The server re-prices each purchase, so a stale line can only be refused, never undercharged |
| 6 | **The Keeper's door.** At two sites the scripted player could not open it: the crosshair did not register the door and three right clicks failed. The shot shows the door face with two people's names directly behind it. People are not pushable and close doors behind them | `stuck_to_the_keeper.png` | **Unproven.** Not shown that a person would be blocked |
| 7 | **Knocker.** Right-clicked by the script in the flooded cellar, the Undercroft's button did not open the seal | Ravine run | **Unproven.** Retest now that the cellar stays dry |
| 8 | Broom sound events name `minecraft:sounds/block/wood/break1.ogg` and others that 1.21.11 does not have | Client log | Unrelated to hamlets; recorded only |

Harness faults that first read as defects, each disproved by its screenshot: a "no route" that was the player's death from a
40-block drop; a door "stuck" that was the Ministry Handbook, which a new character holds, opened by a click that missed; the
bank's flanking iron doors, which by design do not open by hand; a purchase that "failed" because the script stopped scrolling.

### The Wayfarer's Chart

The Path was finished through the real `ApprenticePathService.evaluate()`: every other step was recorded as setup, so the last
was met for real. The chart arrived with "Tucked into your things you find a Wayfarer's Chart. Someone thinks you should
meet your neighbours", 71 and 83 s later on the loaded machine, with the Serpent Chart beside it. The hamlet nearest spawn
stood where the search said. Two Handbooks were in the pack; the setup recorded the first step last, so that may be the setup,
and it is not called a defect.

### Accessibility (Phase D)

Seed 1234567, chunk 409,-389: the west is a lake 14 blocks down; east, north and south are open on foot 64 blocks out (528,
371 and 304 cells reach the edge). All five doors face the plaza. **Decision: acceptable variation, no new rule.** What matters,
reaching it at all, holds from three sides, and the ravine site showed the same picture (closed south, open three ways). A
rule demanding four open sides would refuse every lakeside and clifftop hamlet for something no player needs. Section 14's
"370 of 371" looked only 4 blocks out; this walk (part `approach` of the harness) is the better measure for the next probe run.

### Chart search (Phase F)

| Same seed, 16 origins 12,000 blocks out, same machine load | Before | First attempt | Final | No search (control) |
|---|---|---|---|---|
| Worst server pause | **43.2 s** | 15.9 s | **3.7 s** | 1.2 s, 2.9 s |
| Ticks longer than 100 ms | 29% | 57% | **10.7%** | 9% to 10.5% |
| Mean search | 24.0 s | 13.1 s | 15.7 s | |
| Sites found | 16 | 16 | the same 16 | |

Why it stalled: `HamletCharts` budgeted 3 ms a tick between `walk.step()` calls, but one step ran `checkStructurePresence`,
which runs all of `SettlementStructure.assess()` (jigsaw and the other-structure check) on the server thread, and a standing
site ran it a second time inside `getChunk(STRUCTURE_STARTS)`. The audit's 5.5 s was `nearest()` run once, not the walk.

The fix leaves the question where vanilla already answers it, in the chunk generators. `TreasureCharts.AskedCell` turns a
cell away by exclusion zone and biome on the server thread, asks for the chunk to `STRUCTURE_STARTS`, and answers
`SiteSearch.PENDING` until it is done; the walk asks the same cell again next tick. The first attempt called
`ServerChunkCache.getChunkFuture` from the tick, and sampled stacks showed it waiting inside (`managedBlock`): that is what it
does on the server thread. It is now asked from a worker, where vanilla only queues the request on the server thread, and the
one-tick ticket is renewed each turn. No world access moves off the server thread. Also fixed: a chart finished after the
player changed dimension is drawn of the Overworld.

Tests: `SiteSearchWalkTest` (a waiting cell is asked again and never passed; waiting never changes what is found, 200 worlds),
`HamletChartsTest` (a waiting cell ends the tick's share; a spent budget asks nothing), and the game tests
`settlement_chart_is_given_once_however_often_it_is_offered`, `settlement_chart_search_waits_on_the_world_a_cell_a_tick` and
`settlement_chart_search_ends_with_a_logout`.

Left: a search still costs wall time (16 s mean here), and the Chamber's Serpent Chart, handed over at the same moment, still
searches synchronously (`ChamberCharts.offer`, Chamber scope).

### Two clients (Phase E)

Two client processes, one dedicated server, TCP, the ravine site.

| Check | Result |
|---|---|
| Both join; five people present | Pass |
| Both open the same person's board at once | Pass |
| Six purchases at once from one shelf | Shelf 24 to 18 by the fresher reading; each paid only for what they got (101 and 103 Knuts) |
| Two players at one teller | Pass, conserved; the throttle dropped 1 deposit (defect 4) |
| A contract is one player's own | Pass, weakly: the other player's board was not open when asked |
| Lethal blow | Gone from both clients, 0 items within 6 blocks |
| Back at dawn after a skipped night | **Fail**, defect 3 |
| Back once the record's game time passed | Pass, same name |
| Disconnect and reconnect | Vault and pocket unchanged |
| Chunks dropped and reloaded | **Inconclusive:** the probe's teleport back did not move one player, and the hamlet's chunk was not entity-ticking. It says nothing either way about people's persistence, which section 13 proved across a real restart |

The first attempt timed both clients out: the probe generated 225 chunks under them. The site is now generated before they join.

### Tests

| Suite | Result |
|---|---|
| Unit, final code | 2,952 run, 6 failed: the same six wand-model tests as before. The five new chart tests pass |
| Game, final code, runs 4 to 8 | **513/513, five runs of five** |
| Game, runs 1 to 3 (before the two test corrections below) | 5, 12 and 6 failures; causes below |
| Mutation: liquid fix removed from the built resources only | Exactly the four `settlement_stands_on_flooded_*` failed, "apothecary chest holds water" |

Two corrections to Living World tests, neither loosening a check:
- `settlement_stands_on_*` assembled its pieces with vanilla's default liquid setting, so it tested vanilla rather than the
  structure file. It now reads `liquid_settings` from the registered structure through its own codec.
- `settlement_gives_way_to_other_structures` failed on every run in this tree and never in `C:\tmp\wb-lw-fix`: one of its
  taken sites is a Chamber, the Chamber only starts with its module on, and this tree's `run/config` still says
  `chamber_of_secrets = "disabled"` while the fix tree's says `"preview"`. The section 14 figure of 510/510 came from the fix
  tree. The test now leases the module for its check instead of reading the run's config. The config was not changed.

### Random game-test failures

Messages were sharpened, not loosened, on the three named tests: `settlement_lethal_blow_apparates_them_away` lists the items
before and after; `poaching_is_filed_when_an_official_sees_it` names each official, its age and place; and
`protego_horribilis_shrugs_off_dark` adds each caster's state and where the ward, the caster and the attacker stood. **None of
the three recurred in eight runs**, so their causes remain unproven. What the runs did show:

| Failure | Runs | Cause |
|---|---|---|
| `location_gringotts_loads_and_opens_at_the_teller`, `location_board_needs_presence`, `location_expedition_loop_end_to_end`, `location_goblins_break_curses_for_a_fee`, `location_tier_gates_high_security_vault`, together | 2 | **Shared global state, shown.** `AdminModuleTests` switches `GRINGOTTS` and `PLAYER_ABILITIES` off and on (logged: "GRINGOTTS enabled -> disabled") and is registered with `tests.add`, so it runs in the same batch as every other test. Module state is global. Fix, not made here (outside this milestone): register the admin module tests with `addAlone` |
| `ministry_trace_child_is_warned_then_heard`, `ministry_casework_players_alone_cannot_name_anyone`, `ministry_trace_wand_examination_finds_an_unforgivable` | 2, 3 | "The chunks under the scenario never started ticking entities on tick 402": chunk lifetime under load. Not reproduced in runs 4 to 8 |

Likely, and unproven: every `addAlone` scenario runs at the same spot, because each batch reuses the structure grid's first
slot, so an entity or item a previous lone test left outside its bounds (a goblin teller is a Ministry official) is still
there for the next. The poaching message now prints what it finds.

The Chamber's geode race (section 14) was not touched; its evidence stays in `C:\tmp\wb-lw-results\chamber`. The Chamber
is not validated.

### Remaining risks

- No person has played it. Scripted play cannot judge feel, pacing, or whether the place is found without being told.
- Defects 3 to 7 are open.
- The Undercroft's knocker and seal are not shown working in a real client.
- The 371-site survey cannot see anything that needs ticking (fluids, falling blocks, leaf decay). The flood was one.
- Existing worlds keep cellars generated before the fix, flooded or not.
- Modded biomes and other world types were not sampled.

## 16. Alpha stabilization (2026-11-03)

Branch `feat/living-world`, HEAD `33506296`, uncommitted, on top of section 15. Backup first:
`C:\tmp\wb-backups\2026-11-03-stabilization-start` (tracked patch, untracked tar, HEAD, status). No commit, push, merge,
reset or branch switch. Nothing added: no hamlet, building, profession, quest or system. Earlier evidence and comparison
trees are untouched. New evidence: `C:\tmp\wb-lw-stab` (client runs `k-*`, two-client run `mp`, `s-mp`, `c-one`, `c-two`,
life probe `s-life`, test logs `tests`). Harness: `C:\tmp\wb-lw-stab\tools`, copies of section 15's pointed at that
directory.

### Repository state at the start

As reported: `feat/living-world` at `33506296`, 32 entries (23 modified, 9 untracked). 16 come from the generation
hardening (section 14), 5 more were there when section 15 began (three documents, `SettlementSite`, `SettlementRooms`),
and 11 come from section 15 (`LocationScreen`, `PoachingTests`, `ProtegoShieldTests`, `SiteSearch`, `TreasureCharts`,
`wizarding_hamlet.json`, `SiteSearchWalkTest`, `HamletPlaytest`, `ChartSearchProbe`, `SettlementMpProbe`,
`HamletChartsTest`). The attribution comes from the earlier backups' file lists.

### P0: back at dawn

**Root cause.** `PostRules.backAt` turned "the next dawn" into a game time at the moment of the blow, and the post compared
game time. A bed sleep sets day time to the next multiple of 24,000 (`ServerLevel.tick`, through `onSleepFinished`) and
leaves game time alone; so does `/time add`; `/time set` writes day time outright; and `/neoforge day` changes how much day
time a game tick is worth. None of these moved the record, so the person came back a day of game time later, whatever the
clock showed.

**Fix.** The record keeps two values: `dawn`, the day time of the dawn they return at, and `back_at`, a game-time backstop
for the same moment at the day speed of the blow. They are back when either has passed (`PostRules.due`). Day time covers
sleep, `/time add` and fast days; the backstop covers a clock turned back or stopped, and is scaled by
`getDayTimePerTick` so a slow day does not bring them back at night. The 2,400-tick minimum is unchanged. `dawn` is a new
optional saved field: a record written before it reads `NO_DAWN` and keeps the game time it was written with, so a world
saved with someone away loads unchanged. Name index, UUID handling, the stale-copy refusal and the shelves are untouched.

| Kind | What | Result |
|---|---|---|
| Unit, `PostRulesTest` (16) | a night slept through, ordinary time to the tick, `/time add`, a clock turned back, a stopped clock, half and 20x day speed, an old record | pass |
| Unit, `SettlementDataTest` (7) | two people away survive two save round trips with the same dawn; an old-format record decodes with `NO_DAWN` | pass |
| Game `settlement_back_at_dawn_as_the_day_runs` | blow at the vanilla rate, so game time is 3,000 ticks off; the day then runs 25x faster; the post's own tick brings them back at dawn | pass |
| Game `settlement_back_at_the_dawn_slept_to` | a real bed, `startSleepInBed`, vanilla's wake; back with under 200 game ticks passed, against a backstop 10,000 away | pass |
| Game `settlement_two_away_return_once_each` | two posts, both struck, both back at dawn as themselves after three looks each; the shelf unchanged | pass |
| Game `settlement_away_while_the_hamlet_is_unloaded` | a post on a chunk of its own, unloaded (checked) while away, dawn passes, reloaded: nobody meanwhile, then one person | pass |
| Game `settlement_blow_and_return_three_times` | three blows, three dawns: same name, new body each time, nothing dropped, the shelf unchanged | pass |
| Mutation: `due` set back to the old game-time rule | the five scenarios above | **all five fail**, each at its own check |

The unload scenario first failed: it looked 45 ticks after the chunk ticked, before the nine chunks' entities were back
from disk, and a post judges nothing until they are, by design. It now waits for them. That was the test's timing, not the
game.

### P0: the Undercroft knocker

**The alpha's failure was the harness.** Section 15's saved ravine world (`C:\tmp\wb-lw-alpha\c1\saves\playtest_ravine`)
has the knocker intact: `polished_blackstone_button[facing=west]` on the brick above the upper seal block, both seal
blocks `open=false, powered=false`. That is the geometry `chamber_seal_opens` already tests. The alpha's step aimed at the
middle of the button's cell and clicked without checking the crosshair; its after-shot looks at the ceiling. The module
gate stops right clicks on the Chamber's own blocks while `CHAMBER_OF_SECRETS` is off, but the knocker is a vanilla button
and the seal opens from its redstone, so the gate does not apply.

The new playtest part `knock` walks from the plaza on foot, empties the hand, aims at the button's own shape, does not
click until the crosshair is on it, checks that the server saw the button pressed, and checks the seal.

| Site (fresh world, natural generation) | Reached on foot | Crosshair | Server press | Seal | Walked through | Saved open |
|---|---|---|---|---|---|---|
| Ravine, 271828182 chunk 375,-227 (`k-ravine3`) | **56 cells**, trapdoor and ladder | button, 2.5 blocks | yes, empty hand | `open=true` | yes | yes (region file) |
| Ravine again (`k-ravine2`) | set down (harness, below) | button, 2.6 blocks | yes | `open=true` | yes | yes |
| Plains nearest spawn, 271828182 (`k-spawn`) | set down (harness, below) | button, 2.6 blocks | yes | `open=true` | yes | yes |

The in-client reopen never started a world on this machine (no load line in ten minutes, three times), so persistence was
read from each saved world after a clean save-and-quit. Harness faults found and fixed on the way: the walker took every
trapdoor for a way through and opened the Apothecary's counter top (a row of top-half trapdoors) to climb over it, where
the Apothecary stood at his counter; and a nested client call deadlocked the first ravine run. The spawn run's walker
fell off a terrace outside the hamlet, with nobody near.

The ravine cellar is dry on natural generation after twenty minutes with a player nearby: 0 of the cellar's 264 template
room cells hold water in `k-ravine`, `k-ravine2` and `k-ravine3`, against **79** in section 15's pre-fix world at the same
site, whose bottom ladder is still `waterlogged=true`. (A box of 9 blocks round the seal counts about 240 water blocks in
every world: the aquifer outside the cellar walls.)

### P1: the Keeper's door

**Not reproduced; no game change.** The new part `door` traces the door from the server every tick (each swing, and who
stood within four blocks, where, with which goal running), and walks up from the plaza, aims at the door at chest height,
right-clicks, steps in and comes back out.

| Site | Tries | Opened | Crosshair on a person | Shut on the player | Got through |
|---|---|---|---|---|---|
| Ravine (`k-ravine`) | 8 natural | 8 | 0 | 0 | 8 |
| Plains (`k-spawn`) | 6 natural | 6 | 0 | 0 | 6 |
| Plains, the keeper held still right behind the door (setup) | 1 | 1 | 0 | 0 | 1 |
| Plains, the keeper held still in the doorway cell itself (setup) | 1 | 1 | 0 | 0 | 1, past the keeper |

The crosshair landed on the door's upper half every time, as it does at eye height. A person behind the door or in the
doorway did not stop it opening or the player passing, and the door log shows only the player's own swings: no inhabitant
shut a door on the player in 16 tries. Inhabitants are not pushable, so a player pressing into one is pushed back gently
and cannot move them; here that slowed the player and did not stop them. With no cause shown, there is no regression test
for one; the harness now lists who stood within two blocks of any cell it is stuck at. Possible and not seen: an
inhabitant standing in the one-block gap at the end of the Apothecary's counter, the only way to the Undercroft's hatch.
A player can still get past a person there, slowly.

### P1: Gringotts and the board under load

**Cause.** `VaultActionC2SPayload` admitted one action per 2 server ticks and dropped the rest without a word.
`enqueueWork` runs every packet that piled up during lag in one tick, at one game time, so a lagging server kept the
first click of a burst and lost the rest. The board's `LocationActionC2SPayload` had the same gap throttle (4 ticks);
section 15's two-client run shows it: six purchases, shelf 24 to 19.

**Fix.** A per-tick budget (`util/TickBudget`) in place of the gap: up to 64 vault actions and 16 board actions per player
per server tick are handled at once, in arrival order. Past the budget the first refusal of the tick is told on the action
bar ("Too many requests at once: the goblin took the first 64 and nothing moved for the rest"). A click from someone who
has left the counter is now told so; it was silent. A malformed packet stays unanswered, since no honest client sends one.

Why not a queue or request ids: each action is decided on the vault and the pocket as they stand when it runs, so a burst
can only move what is there; TCP delivers each packet once and in order and the client never resends, so an id would guard
against a duplicate that cannot happen; and a queue would have to outlive a disconnect, which a budget does not. The budget
keeps the bound, and the server stays authoritative.

| Game test | Result |
|---|---|
| `settlement_teller_takes_every_click_of_a_burst`: 7 deposits and 29 withdrawals in one tick (the alpha's run) | 36 of 36, coin conserved |
| `settlement_teller_refuses_past_its_budget_and_says_so`: 74 in one tick | 64 handled, 10 refused, one message; the retry next tick handled |
| `settlement_teller_serves_two_players_at_once` | both whole, each conserved |
| `settlement_teller_answers_what_it_cannot_do` | insufficient funds and coins not carried both answered, nothing moved |
| `settlement_teller_across_a_disconnect_and_a_retry` | ten deposits count once across a reconnect; a retry off the counter is answered and moves nothing; at the counter it works |
| `settlement_board_takes_a_burst_and_answers_the_rest` | 16 handled, the rest refused with one message |

Also fixed: `gringotts.wizards_and_beasts.reject.full` was used and had no text.

### P1: the shop board for the other player

**Cause.** After a trade, `MarketTrade` refreshed only the trader's board. Prices are the whole market's and a settlement
shelf is shared, so everyone else's open board went stale.

**Fix.** `LocationProgression.refreshOthers` sends a fresh board after a purchase or a sale to every other player standing
at one of that place's hosts, each built from its own player's standing, so unlocks stay private. A client without that
board open ignores it (`LocationScreen.receive`). Prices stay global, shelves stay per site, and the server still re-prices
and re-counts every purchase.

`settlement_a_trade_refreshes_every_board_at_the_counter` reads what each mock client was actually sent: the watcher gets
the new shelf and price without asking, still locked out of the buyer's unlocked good; the buyer keeps theirs; a player at
no counter is sent nothing.

### P2: flooded cellars in existing worlds

- **Generated and saved cellars keep their water.** The waterlogged ladder, lantern and sign are blocks in saved chunks
  (section 15's world still has 79 wet cells). Draining them automatically is not safe: the mod cannot tell water the
  aquifer left from water or blocks a player placed since. Nothing rewrites them.
- **A gap in the section 15 fix, closed.** A `PoolElementStructurePiece` saves its liquid setting only when it is not the
  default and reads back the default (`apply_waterlogging`) when it is missing. A hamlet whose structure start was saved
  before the fix, with its cellar chunk not yet generated, would still have flooded. `SettlementRooms`, which already runs
  once on every newly generated hamlet chunk, now also sets back to dry every waterloggable block the template keeps dry.
  Only chunks generating now are touched, so no player change is lost. `settlement_an_old_start_dries_its_cellar_*` (four
  rotations) assembles the hamlet with vanilla's default on flooded ground, requires that it floods (the positive control),
  runs the pass, and requires no template block holding water.
- **For a cellar that has flooded already**, without tools: fill the water or use sponges, then break the waterlogged
  ladder, lantern and sign and put them back. An operator command that dries only the template's own blocks in a named
  hamlet would be safe (set `waterlogged` back on blocks the template placed, remove water only from template air cells),
  but it is new work and was not made.

### Visual proposal (nothing changed)

Measured from the templates (`wizarding_hamlet/<role>/a.nbt`):

- **One shell, five times.** Keeper, Wandmaker, Reading Room and Apothecary share walls, windows, roof and door. Their
  fronts are identical cell for cell except an 8-block plank band at floor level (Keeper green painted wood, Wandmaker
  purple, Reading Room plain shopfront planks); the Apothecary matches the Keeper in every front cell compared. The bank
  differs (marble, vault bricks, gold trim). The four interiors are 86 to 93% identical to each other.
- **Empty floor.** At floor level 20% (Keeper), 30% (Wandmaker), 39% (Reading Room), 26% (bank) and 27% (Apothecary) of
  the 99 interior cells hold anything. At head height the Keeper's room is empty from the row behind the back counter
  (z 3) to the door (z 11).
- **Same wall indoors.** The Wandmaker's identity is 27 bookshelves and 9 chiselled bookshelves along the back and one
  side; the Reading Room's is 57 bookshelves on the same back wall. From the door they read as one room.

Proposals in the existing language (Hogsmeade stone, Three Broomsticks timber, painted wood):

1. **Fronts:** one feature each, not a new shell. Wandmaker: a bay window with wand boxes behind it and a sign bracket.
   Keeper: a porch roof on two posts with a lantern. Reading Room: two-block windows and a bench by the door. Apothecary:
   herb boxes under the windows and a hanging cauldron sign.
2. **Wandmaker indoors:** the side wall of bookshelves becomes shelves of wand boxes (barrels or decorated pots) and a
   measuring stool by the Bench; bookshelves only behind the counter.
3. **Floors:** furniture of the trade in about a third of the empty cells: the Keeper's notice board and map table, two
   reading desks with candles, the Apothecary's drying racks and ingredient barrels, the bank's queue rail.
4. **Plains:** a gravel path from each door to the plaza's paving, and a low fence or hedge round two back yards.
5. **Footing:** face the top row of a plinth over falling ground with the timber or stone of the wall above, so it reads
   as a foundation.

None was built: each is a template edit that needs a look in a real client before rollout.

### Two real clients on a dedicated server

Seed 271828182, the ravine site, fresh world generated before the clients joined, two client processes over TCP
(`C:\tmp\wb-lw-stab\mp\run1\mp_report_run1.txt`, and `mp\mp_report.txt` for the boards rerun).

| Check | Section 15 | Now |
|---|---|---|
| Two players' deposits (10 Sickle clicks each) | 290 / **261** | 290 / 290 |
| Two players' withdrawals (29 Knut clicks each) | 29 / **9** | 29 / 29 |
| 10 Sickle clicks inside one client tick, and 29 Knut clicks inside one client tick | not tried | all land, worth conserved (30,160 and 29,870 before and after) |
| One buys, the other only watches: the watcher's open board | not tried | follows, 24 to 23 on both |
| Six purchases at once from one shelf | 24 to **19**, boards 19 / 18 | 23 to 17, boards 17 / 17, each paid only for what they got |
| A real bed sleep by both clients after a blow | not tried (day time was set) | both asleep, woke at day time 24,036; back as themselves 210 game ticks after the blow, with the backstop 13,905 away; both clients see them |
| Disconnect and reconnect | vault and pocket kept | vault and pocket kept |
| Chunks dropped and reloaded | inconclusive | inconclusive again: the probe's teleport back did not move the players, as in section 15. The game test `settlement_away_while_the_hamlet_is_unloaded` covers it |

The first run's board steps were void: the probe stood the two players a block apart in front of the Apothecary and each
clicked the other (AlphaOne's shot is AlphaTwo's head). The probe now stands them apart; the boards were rerun alone.

### A restart while two are away (dedicated server, life probe)

Seed 271828182, the hamlet nearest spawn, `C:\tmp\wb-lw-stab\s-life`. First start (`life`): 77 checks, 0 failed, among them
the wandmaker struck and back at their time under ordinary (sprinted) time, then the keeper and the scholar struck at day
time 24,200, both recorded away for dawn 48,000, and the server saved and stopped. Second start on the same world
(`return`): 15 checks, 0 failed. After the restart both were still away waiting for the same dawn and not back early
(day time 24,500); the other three, the seal (open), the Bench slot and the shelf came through; then ordinary time ran to
dawn and each came back once (day time 48,200), and the hamlet held its five.

### Tests on the final code

| Suite | Result |
|---|---|
| Game, five full runs (`C:\tmp\wb-lw-stab\tests\gt_full_1..5.log`) | **529 / 529** in runs 1, 2, 4 and 5; **528 / 529** in run 3 |
| Run 3's failure | `creature_ai_camouflage_stays_continuous`, "the moke was visible on tick 4": a **known random failure**, seen with the same message on the section 14 control tree with every fix off (`C:\tmp\wb-lw-results\gametest_1.log`). Nothing in this work touches creatures or that scenario |
| New game scenarios | 16 (dawn 5, counter 5, board 2, old start 4), 529 in all, up from 513 |
| Settlement scenarios alone, during the work | 68 / 68 after setup fixes to the new scenarios (chunks forced a tick too early, a bed used before the sky darkened, plot-dependent positions, async entity load) |
| Unit (`unit.log`) | 2,962 run, **6 failed**: the same six wand-model tests as the historical baseline. The 10 new unit tests pass |

No new deterministic failure; one known random failure in one run; harness failures (the walker, the in-client reopen,
the probe's teleport back) are named where they happened above and were not counted as game results.

### Remaining issues

- **No person has played a hamlet.** Every result here is scripted. `documentation/HAMLET_PLAYTEST.md` is the checklist for
  the first human playtest.
- Cellars generated and saved before section 15's fix keep their water (P2). New worlds, and new chunks of old hamlets,
  are dry.
- Inhabitants cannot be pushed. One in a one-block gap slows a player; not seen to stop one.
- The two-client chunk-reload check is still inconclusive in a real client (the probe cannot move the players back); the
  game test covers it.
- The in-client reopen of a world never started on this machine; persistence was read from saved region files.
- Buildings look alike and interiors are bare (proposal above, unbuilt). Shop names are Diagon Alley firms.
- The Chamber's geode race (section 14) and the archived `SpellWorldGuard` work are untouched, as instructed.
