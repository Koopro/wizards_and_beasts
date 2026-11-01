# The Living Wizarding World, Phase 1: audit and blueprint (2026-11-01)

Status: **audit and design only, awaiting approval.** Nothing in this document has been built. No gameplay file, registry
or dependency was changed, and no commit was made.

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
