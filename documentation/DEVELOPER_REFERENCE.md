# Wizards & Beasts — Developer Reference

> **Source snapshot:** 2026-07-13  
> **Authority rule:** this document is based on `src/main/java`, `src/main/resources`, and `src/generated/resources`. If an older design document disagrees, implementation wins. Counts are a snapshot and should be regenerated after content work.  
> **Supersedes** `ARCHITECTURE.md` (snapshot 2026-05-19) wherever they disagree — see §19. `ARCHITECTURE.md` is retained only as a historical package-tree reference.

## 1. Project overview

| Property | Current value |
|---|---|
| Mod | **Wizards & Beasts** (`wizards_and_beasts`) |
| Release | `0.1.0-alpha.1` |
| Minecraft | `1.21.11` |
| NeoForge | `21.11.42` |
| Java | 21 |
| Rendering dependency | GeckoLib `5.4.5` |
| License | All Rights Reserved |

Wizards & Beasts is a Wizarding World-inspired NeoForge RPG layer for Minecraft. Its intended experience is not “a few spells added to survival”; it combines a custom magic combat loop, character progression, magical transport, creatures, lore, storage, economy, and world content.

The current alpha’s principal gameplay pillars are:

1. **Wands and spellcasting** — customisable/bonded wands, a four-slot loadout, cooldowns, requirements, beam channels, skill scaling, and both Java- and JSON-defined spells.
2. **Character RPG progression** — heritage, professions, statistics, skills, vocations, spell proficiency, Animagus and Obscurial forms/abilities.
3. **Wizarding-world interaction** — brooms, Floo, Apparition, Marauder’s Map, Gringotts vaults, brewing, magical plants/woods, trunks/pocket spaces, artefacts, and lore systems.
4. **Creatures and discovery** — a data-configured creature roster, bestiary discovery, specialist entities, spawning, loot, and creature abilities.

### Design philosophy inferred from the code

- Prefer **server authority** for game-changing operations. Client input is sent to the server; the server validates, mutates state, and synchronises a narrow result back.
- Prefer **data-driven content** where variants are numerous or balance/lore iteration is likely: spells, brews, creature tuning, skills, vocations, pocket templates, and wand definitions.
- Keep **client-only UI/rendering under `client/**`**. Common/server packages do not depend on client classes.
- Use NeoForge **attachments** for persistent entity/player state and **data components** for per-`ItemStack` state.
- Keep the feature-rooted package layout. Do not introduce a generic `common/` package merely to group unrelated code; this is an explicit refactor decision in `FULL_AUDIT_REPORT.md` Appendix.

The project is a substantial alpha: production systems exist, but balancing, some art, server soak testing, and selected “built but inert” content remain unfinished. Backward-compatibility migration code is present for the old `WizardsAndBeastsMod:*` namespace and legacy attachment IDs.

## 2. Project statistics

| Measure | Snapshot | Method / caveat |
|---|---:|---|
| Production Java files | 930 | `src/main/java` |
| Test Java files | 32 | `src/test/java` |
| Production Java lines | ~67,473 | Includes comments and blank lines |
| Generated asset files | 1,064 | `src/generated/resources/assets` |
| Generated datapack files | 845 | `src/generated/resources/data` |
| Hand-authored datapack files | 489 | `src/main/resources/data` |
| Total datapack files | 1,334 | Generated + hand-authored |
| Entity types | 104 | 86 manifest creatures + 18 fixed/special entity types |
| Creature definitions | 86 | `data/wizards_and_beasts/creatures` |
| Bestiary entries | 107 | `data/wizards_and_beasts/bestiary/entries` |
| JSON spells | 27 | `data/wizards_and_beasts/spells` |
| Java spells | 6 | Bespoke `spell.impl` classes: `AvadaKedavra`, `ExpectoPatronum`, `Imperio`, `ObscurusGrasp`, `ObscurusSurge`, `Protego`. `riddikulus`, `capacious_extremis` and `claustra_reverto` are **JSON**, not Java |
| Total bundled spells | 33 | Java + JSON; addons may add more |
| Skill nodes | 161 | `data/wizards_and_beasts/skill_nodes` |
| Vocations | 5 | `data/wizards_and_beasts/vocations` |
| Custom mob effects | 24 | `effect.ModEffects` |
| Custom attributes | 3 | `registry.ModAttributes` |
| Block entities | 4 | Bench, pocket configurator, Floo fireplace, trunk |
| Menu types | 5 | Bench, Ollivander trial, configurator, Niffler pouch, Hermione’s bag |
| Items / blocks | ~201 item models / ~203 blockstates | Useful **resource-derived lower bounds**, not registry totals; item and block registrations are distributed across helper registries and variants |
| Network surface | 22 feature registrars; 100+ payload-related classes | `network.ModNetwork` fans out by feature; do not infer packet count from a single class |

## 3. Package structure

The root package is `at.koopro.wizardsandbeasts`. The following is the complete package-directory map, compacted by feature; an asterisk means the directory is primarily client-only.

```text
wizardsandbeasts
├── ability (data)                         player abilities and ability state
├── apparition (command)                   apparition rules and commands
├── azkaban (attachment, command, data, structure)
├── bestiary (command, data)               discovery and entry definitions
├── block (deluminator, floo, location, trunk)
├── bloodpact (command)
├── brew (def)                             brews, recipes, reload codecs
├── broom (command)
├── client *
│   ├── ability (state), apparition (state), bestiary (gui, niffler), broom, cloak,
│   ├── currency (gui, state), debug, deluminator, entity, event, floo (gui),
│   ├── form (hud, model, state), gui (character/{tab,widget}, config, util),
│   ├── handbook, heritage (gui, hud, state), hud (stats), item, legilimency (state),
│   ├── map, model, network, owl (screen), particle, skill (gui, state),
│   ├── spell (gui, hud, input, network, render, state, ui), stats,
│   ├── trinket (gui), trunk (gui), ui, wand (gui)
├── command (debug)                        `/wandb` modules and other roots
├── creature (ability, command)            generic creature definition/runtime model
├── currency (command, vault)
├── datagen                                tags, recipes, models, loot, language
├── deluminator
├── diary
├── effect                                 custom `MobEffect` implementations
├── entity
│   ├── azkaban (goal), beast, broom, creature (ai), form, goblin,
│   ├── niffler (ai, command), spell
├── event
│   ├── ability, apparition, bestiary (niffler), bloodpact, brew, broom, cloak,
│   ├── floo, form, heritage, item, memory, skill, spell, stats, trunk, wand
├── floo (call, command)
├── form
├── handbook
├── heritage (command, data, obscurial, profession)
├── item
│   ├── bestiary, bloodpact, brew, broom, cloak, consumable, currency, darkartefact,
│   ├── deluminator, floo, hallow, lore, map, projectile, spell (gamp), trinket,
│   ├── trunk, wand
├── legilimency
├── map
├── memory
├── mirror
├── mixin (client)
├── module (command, condition)            feature gates/conditions
├── network
│   ├── ability, apparition, azkaban, bestiary (niffler), bloodpact, broom,
│   ├── character, currency, debug, floo, form, handbook, heritage, legilimency,
│   ├── map, owl, skill, spell (teacher), stats, trinket, trunk, wand
├── owl (command, data)
├── particle
├── registry                               NeoForge registries and grouped item/block registrars
├── skill (command, data, vocation)
├── spell
│   ├── beam, cast, command, core, data, def, effect, expelliarmus, gamp,
│   ├── imperio, impl, learning, lib, patronus, proficiency, protego, tag, teacher
├── stats
├── sync                                   lifecycle re-synchronisation
├── trunk (command, gui, template)
├── util                                   shared codec/math/NBT/colour helpers
├── wand
│   ├── allegiance, bench, cast, command, corruption, customization, elder,
│   ├── gui, integrity, ollivander, recipe, registry, resonance, stat
└── world (tree)                           configured/placed features and custom trees
```

### Navigation rules

| Need | Start here |
|---|---|
| Bootstrapping / lifecycle | `WizardsAndBeastsMod`, `WizardsAndBeastsClient`, `event/**`, `sync/**` |
| Register vanilla/NeoForge content | `registry/**` |
| Player-facing feature | Its feature root (`spell/**`, `wand/**`, `floo/**`, etc.) plus matching `network/**`, `client/**`, and `event/**` packages |
| Persistent player state | `registry.ModAttachments` and the owning feature’s `*Data` type |
| Stack-specific state | `registry.ModDataComponents` or `wand.WandComponents` |
| Data JSON | `src/main/resources/data/wizards_and_beasts/**` |
| Generated content | `datagen/**` and `src/generated/resources/**` |

## 4. Registry system

`WizardsAndBeastsMod` is the central bootstrap. It attaches each `DeferredRegister` to the mod event bus, registers entity attributes and payloads, and attaches reload listeners. Use this same pattern: declare a static `DeferredRegister`, expose `DeferredHolder`/`DeferredItem` fields, and attach the register once in the mod constructor. This matches the NeoForge 1.21.11 recommended registration model.

| Registry/content | Owner and helpers | Notes |
|---|---|---|
| Items | `ModItems`; `WandItemRegistry`, `BroomItemRegistry`, `ConsumableItemRegistry`, `CurrencyItemRegistry`, `DarkArtefactItemRegistry`, `LoreItemRegistry`, `MiscItemRegistry`, `TrinketItemRegistry` | `ModItems.register(...)` is the common wrapper; grouped registrars prevent one giant item class. |
| Blocks | `ModBlocks`, `WizardingWorldBlockRegistry`, location registries, `WoodSet`, `DeluminatorBlockRegistry` | Uses `DeferredRegister.Blocks`; wood-set helper registers matching block items. |
| Block entities | `ModBlockEntities` | Four types: wandmakers bench, pocket configurator, Floo fireplace, trunks. |
| Entity types | `ModEntities`, `EntityHelper`, `ModCreatures` | Fixed special entities plus a 86-entry creature manifest. `EntityAttributeBindings` queues attributes before the registration event. |
| Sounds / particles | `ModSounds`, `ModParticles` | Register via their own deferred registries; client providers are registered in `client.particle.ModParticleProviders`. |
| Menus / creative tabs | `ModMenuTypes`, `ModCreativeTabs` | Menu constructors use `IMenuTypeExtension.create(...fromNetwork)`. Client screens are bound in `WizardsAndBeastsClient`. |
| Attachments | `ModAttachments`, `WandAttachments` | Persistent entity/player data; attachments that must survive player death declare `copyOnDeath()`. |
| Data components | `ModDataComponents`, `WandComponents` | Per-stack persistent/network-synchronised state. The split is by domain, not by NeoForge registry. |
| Attributes / effects | `ModAttributes`, `effect.ModEffects` | Three synced attributes and 24 custom effects. |
| Structures / placements | `AzkabanStructures` | Registers structure, piece and placement types. |
| Features | `ModFeatures` | Custom tree `Feature<?>` registrations; configured/placed definitions are resource-driven. |
| Villager content | `ModVillager` | POIs and professions. |
| Recipes | `wand.recipe.WandmakingRecipeType`, `WandmakingRecipeSerializer` | Custom wandmaking recipe type/serializer. |
| Spells | `spell.core.Spells` | An in-memory, hybrid registry: Java static registrations, addon event registrations, and reloadable JSON registrations. It is not a vanilla `Registry`. |
| Skills / vocations | `skill.SkillTrees`, `skill.SkillNodeLoader`, `skill.vocation.VocationLoader` | Runtime collections rebuilt from datapacks; skills are not a vanilla registry. |
| Wand modules | `wand.customization.WandModuleRegistry`, `WandModuleLoader` | Programmatic bootstrap plus reloadable definition path. |
| Creature definitions | `CreatureDefinitionLoader` / runtime definition store | Entity IDs must first exist in `ModCreatures.MANIFEST`; JSON supplies runtime tuning. |

### Registration lifecycle

```text
mod constructor
  → attach deferred registries to mod event bus
  → invoke grouped item/creature registration helpers
  → register payload handler listener and entity attribute listener
  → common setup: post RegisterSpellsEvent, initialise Java spells, post RegisterBrewsEvent
  → server resource reload: rebuild JSON-driven feature stores
```

Do not call `DeferredHolder#get()` during static initialisation unless the API explicitly permits it. `ModBlocks` deliberately uses holder references when registering matching block items to avoid init-order problems.

## 5. Gameplay systems

| System | Purpose and main classes | Data / persistence / client / extension |
|---|---|---|
| Wand | `WandItem`, `WandCast*`, `WandComponents`, `WandAttachments`, allegiance/corruption/integrity/resonance packages | Stack components hold wood, core, flexibility, master, integrity, corruption and config. Player attachments hold bonded-wand/resonance state. Extensible through datapack woods, cores, bench enhancers and `WandModule`. |
| Spells | `spell.core.Spell`, `Spells`, `SpellProperties`, `SpellExecutor`, `spell.cast.*`, `SpellProjectileEntity` | Player loadout/known spells/cooldowns/cast counts live in `PlayerSpellData`. JSON spells reload; Java spells cover bespoke behaviour. C2S cast packets are validated before execution; HUD reads synced state. |
| Skills | `SkillNodeLoader`, `SkillSystemAPI`, `PlayerSkillData`, `SkillEffectCache` | 161 JSON nodes form tree branches. They grant spell learning, damage/cooldown modifiers and abilities. Skill data is attached/synchronised. |
| Heritage/profession | `heritage.*`, `PlayerHeritageData`, `PlayerProfessionData` | Selection, type-specific rules and profession state; synced to client UI. The attachment registry preserves the legacy `type_data` ID for saves. |
| Vocation | `skill.vocation.*`, `VocationLoader`, `PlayerVocationData` | Five JSON vocation definitions; separate committed slots from skills. |
| Forms | `form.*`, `TransitionManager`, `FormMannequinEntity` | Animagus/Obscurial transformations are server state with client render/hand/name-tag suppression and transition overlays. |
| Obscurial | `heritage.obscurial.*`, `ObscurusSurge`, `ObscurusGrasp` | Dark-form-only abilities are deliberately separate from spell slots. Client HUD and dedicated ability packets support them. |
| Brooms | `BroomItem`, `BroomEntity`, `BroomClientInputHandler`, `BroomInputPacket` | Client sends directional input; server simulates acceleration, boost, collision, crash damage and rider state. GeckoLib render/animation is client-only. |
| Creatures | `ModCreatures`, `CreatureDefinitionLoader`, `Generic*BeastEntity`, `DragonEntity` | Java manifest supplies registry-time ID, locomotion and hitbox. JSON supplies runtime stats/traits. Dragons select the shared dragon implementation; special beasts retain bespoke classes. |
| Bestiary | `BestiaryEntryLoader`, `PlayerBestiaryData`, `BestiaryDiscoveryHandler` | 95 JSON entries; server discovery data and sync packets drive client book UI. |
| Brewing | `brew.*`, `BrewReloadListener`, `BrewingRecipeReloadListener`, `CauldronBrewing`, `BrewItem` | Brews and recipes are JSON. Current cauldron flow is event-driven/atomic rather than timed block-entity brewing. |
| Plants / woods | `ModBlocks`, `ModFeatures`, `world.tree.*` | Four magical wood sets and plants such as Mandrake, Mallowsweet and Devil’s Snare; resources define feature placement. |
| Floo | `floo.*`, `FlooFireplaceBlock(Entity)`, `FlooVisitedDestinations` attachment | Fireplace/block entity, discovered destinations and transit UI/packets. |
| Apparition | `apparition.*`, `event.apparition.*`, client controller | Server applies the rule/teleportation result; client renders appropriate effects. |
| Currency / vault | `currency.*`, `PlayerVaultData`, Goblin Teller, `GringottsScreen` | Server-side balances, conversion helpers, vault packets and client cache. Leprechaun gold has expiry behaviour. |
| Storage | `trunk.*`, `TrunkBlock(Entity)`, pocket templates; `HermionesBagMenu`; Niffler pouch | Item components and block entities track packed pockets/locks. Pocket templates reload from JSON. |
| Memory / Legilimency | `memory.*`, `legilimency.*`, `PlayerMemoryData` | Persistent memories and client vision effects, with dedicated events and packets. |
| Blood pacts | `bloodpact.*`, `BloodPactRecord`, `ACTIVE_BLOOD_PACTS` attachment | Persistent pact records, command layer, enforcement events and packets. |
| Dark artefacts / Hallows | `item.darkartefact.*`, `item.hallow.*`, relevant components/events | Per-item components model ownership, usage, possession and state. Many items are feature-gated while alpha hardening continues. |
| Map / handbook / owl | `map.*`, `handbook.*`, `owl.*` | Map streams nearby living-entity markers; handbook and OWL data have dedicated loaders/sync/UI. |
| Azkaban | `azkaban.structure.*`, attachment/data/command, Dementor classes | One-per-world placement, templates/pieces and trespass tracking. |

## 6. Datapack architecture

The mod uses two patterns. **Custom dynamic registries** are registered in `WandDatapackRegistries`; **reload-listener stores** are rebuilt at server resource reload in `WizardsAndBeastsMod`.

| Type | Path beneath `src/main/resources/data/wizards_and_beasts` | Codec / loader / store | Example |
|---|---|---|---|
| Wand woods | `wand_woods/*.json` | `WandWoodDefinition.CODEC`; dynamic registry | `wand_woods/*` |
| Wand cores | `wand_cores/*.json` | `WandCoreDefinition.CODEC`; dynamic registry | `wand_cores/*` |
| Bench enhancers | `bench_enhancers/*.json` | `BenchEnhancerDefinition.CODEC`; dynamic registry | `bench_enhancers/*` |
| Spells | `spells/*.json` | `SpellDefinition.CODEC` → `SpellReloadListener` → `Spells.registerJson` | `spells/accio.json` |
| Brews | `brews/*.json` | `BrewDefinition.CODEC` → `BrewReloadListener` | `brews/wiggenweld_potion.json` |
| Brewing recipes | `brewing_recipes/*.json` | `BrewingRecipeDefinition.CODEC` → `BrewingRecipeReloadListener` | `brewing_recipes/wiggenweld_potion.json` |
| Creature definitions | `creatures/*.json` | `CreatureDefinition.CODEC` → `CreatureDefinitionLoader` | `creatures/abraxan.json` |
| Bestiary entries | `bestiary/entries/*.json` | `BestiaryEntryLoader` | `bestiary/entries/abraxan.json` |
| Skill nodes | `skill_nodes/<tree>/*.json` | skill node codec → `SkillNodeLoader` | `skill_nodes/spell_mastery/basic_casting.json` |
| Vocations | `vocations/*.json` | vocation codec → `VocationLoader` | `vocations/duelist.json` |
| Pocket templates | `pocket_templates/*.json` | template codec → `PocketTemplateLoader` | inspect `pocket_templates/*` |
| Broom definitions | expected reload path owned by `BroomDefinitionLoader` | loader is registered even though no bundled `brooms` JSON directory is present in this snapshot | addon/datapack extension point |
| Handbook chapters | loader owned by `HandbookChapterManager` | reload listener | inspect matching resource path before authoring |
| Wand modules | loader owned by `WandModuleLoader` | reload listener + programmatic bootstrap | inspect matching resource path before authoring |

All codecs should validate their own schema and loaders should log/reject bad definitions without leaving a partial registry. Spells are special: `clearJsonSpells()` runs before reload so a `/reload` is idempotent; Java/addon registrations are retained.

### Bestiary `mmRating`: dragons are uniformly XXXXX

All ten dragon breeds carry `mmRating: 5` by ruling (2026-07-25). Scamander's *Fantastic Beasts* has a single "Dragon" entry graded XXXXX with the breeds described inside it; he assigns no per-breed grades, and `BestiaryScreen` renders `mmRating` as X glyphs, so a lower per-breed value would be a false in-fiction statement. Do not re-introduce a 3/4/5 spread here — the difficulty gradient is canon-supported at the level of *temperament*, not classification, and belongs in a future `threatTier` field.

`mmRating` is also optional: absence means the creature holds no Ministry classification at all, which is correct for ordinary animals with magical uses (`toad`, and the owl/cat/rat still to come). It is not a synonym for `1` — `X` is a real grade held by real magical beasts.

## 7. Networking

`network.ModNetwork` owns protocol registration (`event.registrar("1")`) and delegates to 22 feature-specific `ModNetwork*` classes. Keep payloads inside the owning feature’s network package; only shared codecs belong in `network.PacketCodecUtils`.

### Conventions

- Names encode direction: `*C2SPacket`/`*C2SPayload` for client requests; `*S2CPacket`/`*S2CPayload` for server results/sync.
- Define a bounded `StreamCodec` and payload `Type`; do not trust arbitrary client strings, collection sizes, slots, or target IDs.
- Register C2S handlers server-side and schedule work onto the correct thread. Validate held items, permissions, range, ownership, cooldowns, requirements and current state **again** on the server.
- Client handlers update a client state holder or open/render UI; they must not become the source of game truth.
- Use targeted sync/deltas after a mutation, and lifecycle sync on login, respawn and dimension change. `sync.PlayerDeathHandler` and feature lifecycle handlers are part of that contract.

### Representative flows

```text
Spell: client select/assign/cast request → server validates wand + data + requirement + cooldown
       → server executes spell and persists data → S2C delta updates HUD/cache.
Broom: client input sample → BroomInput C2S → server BroomEntity physics → normal entity tracking/render state.
Vault: player opens Goblin UI → server state/transaction → Vault sync S2C → client cache/screen.
Form: client request → server transition/state → form/transition sync S2C → client overlay/model modifiers.
```

## 8. Player and entity data

NeoForge attachments are the canonical persistent-state mechanism. `ModAttachments.registerData(...)` adapts older `save()/load()` data classes to `IAttachmentSerializer`; most player attachments use `copyOnDeath()`.

Important player attachments include `SPELL_DATA`, `VAULT_DATA`, `HERITAGE_DATA`, `SKILL_DATA`, `VOCATION_DATA`, `SKILL_BONUS_DATA`, `BESTIARY_DATA`, `PLAYER_ABILITY_DATA`, `OWL_DATA`, `PROFESSION_DATA`, `PLAYER_STATS`, `MEMORIES`, Floo destinations, blood pacts, dark-arts state, Patronus data, mental state and several combat/control states. `WandAttachments` adds bonded-wand and resonance data.

Data components are for a particular stack, not a player. `WandComponents` provides wand wood/core/flexibility/master/allegiance/corruption/integrity/casts/length/configuration. `ModDataComponents` owns cross-feature stack state (brew ID, creation tick, pocket IDs/locks, artefact/Horcrux state, bag inventory, pact references, etc.). Use a `Codec` for persistence and a `StreamCodec` only when clients need the value.

Avoid direct NBT in new item features. Attachment NBT remains appropriate only behind an attachment serializer or existing migration boundary.

## 9. Rendering and UI

`WizardsAndBeastsClient` is the client bootstrap. It registers renderers, model layers, particles, key bindings, GUI overlays and menu screens. The client package contains both presentation and **read-only client caches** populated by S2C packets.

- **HUD/overlays:** spell diamond, Obscurus, effect full-screen overlays, Crucio, transition, Floo and stat HUD; debug overlays are config-gated.
- **Screens:** character sheet and skill UI, spell menus, vault, map, heritage, handbook, Floo, wandmaker bench, Ollivander trial, pocket configuration, Niffler pouch and Hermione’s bag.
- **Entities:** GeckoLib renderers/models cover brooms, Goblin Teller, Niffler and appropriate creatures; standard/custom renderers cover projectiles, Patronus, shields and forms.
- **World effects:** `WandBeamRenderer`, glow and Protego renderers, Apparition and Legilimency render hooks, particles and client event handlers.
- **Mixins:** limited to `mixin/**`; keep side-specific mixins in `mixin/client/**`.

No shader subsystem was identified in this source snapshot. New rendering code belongs under `client/**`, registered from `ClientSetup` or `WizardsAndBeastsClient`, and must not leak into dedicated-server classloading paths.

## 10. World generation

- `ModFeatures` registers custom tree features; `world.tree.{Elder,Yew,Holly,Rowan}TreeFeature` implements generation.
- JSON configured and placed features live under `data/wizards_and_beasts/worldgen/{configured_feature,placed_feature}`; NeoForge biome modifiers install trees, plants and selected spawns.
- `AzkabanStructures` registers the Azkaban structure, template pieces, crag pieces and `OnePerWorldPlacement`. Structure and structure-set JSON live under `worldgen/structure*`.
- `ModDimensions` exists as a registry location, but this snapshot does not establish a completed custom-dimension gameplay pipeline. Do not claim a new dimension without source evidence.

For new worldgen, add the runtime feature/structure registration first, then the configured/placed/biome modifier JSON and datagen where applicable. Test with `runData` and an actual generated world.

## 11. AI and entities

| Entity family | Implementation | Behaviour / rendering / spawning |
|---|---|---|
| Generic roster | `GenericGroundBeastEntity`, `GenericFlyingBeastEntity`, `GenericAquaticBeastEntity`, `GenericSessileBeastEntity` | Chosen from manifest locomotion. JSON supplies runtime traits/attributes; spawn placement is handled by `BeastSpawnHandler`. |
| Dragons | `DragonEntity` | Ten manifest IDs use shared fire/venom behaviour, with tuning from the nested `dragon` definition block. |
| Niffler | `NifflerEntity`, `BabyNifflerEntity`, `entity.niffler.ai.*` | Custom goals, carried-Niffler/happiness attachments, pouch interaction, GeckoLib renderer and dedicated packets. |
| Goblin Teller | `GoblinTellerEntity` | Interaction opens the vault flow; GeckoLib renderer. |
| Broom | `BroomEntity` | Not an AI mob: server receives rider input and applies flight/collision physics. |
| Spell entities | `SpellProjectileEntity`, `ProtegoShieldEntity`, `PatronusEntity`, `BeastHexProjectile`, `WizardingThrownEntity` | Server-combat entities with specialised renderers/effects. |
| Forms | `FormMannequinEntity` and form render state | Provides visual/form transition support; player form rules remain in `form/**`. |
| Dementor / Azkaban | `DementorEntity`, `entity.azkaban.goal.*` | Azkaban-specific hostile/goal layer and soul-drain effects. |

The generic creature model deliberately trades per-creature Java classes for data-defined variants. A new creature normally requires both a `ModCreatures.MANIFEST` entry (ID, locomotion, hitbox) and a matching creature JSON. This is a dual-source contract and must stay aligned.

## 12. Coding conventions

- **Names:** lowercase snake_case for registry/data IDs; UpperCamelCase classes; package names follow feature ownership. Prefer explicit names such as `SpellDataSyncS2CPacket` over vague `SyncPacket`.
- **Immutability:** records and `Codec`/`StreamCodec` are used heavily for definitions and packet values. Prefer immutable definition objects and explicit builders where construction is non-trivial.
- **Registration:** use `DeferredRegister`/holders for NeoForge registries. Keep the registration declaration near the owning feature registry.
- **Events:** event subscribers are feature-scoped under `event/**`; static handlers normally use `@EventBusSubscriber`. Mod-bus lifecycle registration belongs in bootstrap classes.
- **Client separation:** client code belongs in `client/**`; packet client handling is outside shared payload records where possible. Never import client-only distribution APIs into common item/entity code.
- **Validation:** packet/server command paths fail closed. Validate on the server; return a player-facing reason where meaningful; use `SpellRejectCodes`/telemetry patterns as the spell reference.
- **Logging:** `LogUtils.getLogger()` is used by core loading paths. Log bad resource definitions with enough ID/context to fix the JSON; avoid log spam in tick loops.
- **Utilities:** use existing `util` helpers (colour, math, NBT, namespace migration) and `PacketCodecUtils` before creating a duplicate helper.
- **No dependency injection framework:** construction is static registry/event wiring plus explicit service/manager calls. Preserve this style unless a cross-project architectural decision changes it.

## 13. Data generation

`datagen.ModDataGenerators` registers the client data providers. The providers are:

- `ModModelProvider` — block states and item/block models.
- `ModLanguageProvider` — generated language keys.
- `ModBlockTagsProvider` and `ModItemTagsProvider` — tag output.
- `ModLootTableProvider`, `ModBlockLootTableProvider`, `ModEntityLootTableProvider` — loot tables.
- `ModRecipeProvider` — recipes and recipe advancements.

Run `./gradlew runData` after changing a provider. Generated files are committed under `src/generated/resources`; do not hand-edit a generated file as the durable source. `processResources` merges generated and main resources, and explicitly merges `en_us.json` key-by-key with hand-authored values taking precedence.

## 14. Build system

`build.gradle` uses `java-library`, `maven-publish`, `idea`, and `net.neoforged.moddev` `2.0.140`. Java toolchains are pinned to 21. The sole declared runtime library is GeckoLib. Test dependencies are JUnit Jupiter 5.11.4.

Key tasks:

| Task | Purpose |
|---|---|
| `./gradlew test` | JUnit/unit and codec/regression tests |
| `./gradlew runData` | Generate committed resources |
| `./gradlew runGameTestServer` | Headless NeoForge server boot smoke test — **not yet a GameTest coverage gate**; zero `@GameTest`/`test_instance` definitions exist in the repo as of 2026-07-13, so this only proves the server boots, not that any in-world behavior works. See `ALPHA_IMPROVEMENT_AUDIT.md` §12.1. |
| `./gradlew build` | Compile, process resources and package JAR |
| `./gradlew runClient` / `runServer` | Development client/server |

CI's intended gate is test → datagen → GameTest server → build. Current alpha notes flag that `runServer` can fail during local NeoForge bootstrap on some environments; retain the boot-smoke-test run before treating ordinary server launch as verified. Actual GameTest coverage (structure placement, block-entity interaction) is a known gap — not yet implemented.

## 15. Important classes

The following high-leverage classes are the primary map for contributors (full names are intentionally given for code search):

| Class | Responsibility | System |
|---|---|---|
| `at.koopro.wizardsandbeasts.WizardsAndBeastsMod` | Common bootstrap, registrations, reload listeners | Core |
| `at.koopro.wizardsandbeasts.WizardsAndBeastsClient` | Client bootstrap | Client |
| `at.koopro.wizardsandbeasts.registry.ModItems` | Item deferred register | Registry |
| `at.koopro.wizardsandbeasts.registry.ModBlocks` | Block deferred register and composition | Registry |
| `at.koopro.wizardsandbeasts.registry.ModEntities` | Fixed entity types | Entity |
| `at.koopro.wizardsandbeasts.registry.ModCreatures` | Creature manifest/entity/egg registration | Creature |
| `at.koopro.wizardsandbeasts.registry.ModAttachments` | Persistent attachments | Data |
| `at.koopro.wizardsandbeasts.registry.ModDataComponents` | Cross-feature item components | Data |
| `at.koopro.wizardsandbeasts.wand.WandComponents` | Wand stack components | Wand |
| `at.koopro.wizardsandbeasts.wand.WandAttachments` | Wand player attachments | Wand |
| `at.koopro.wizardsandbeasts.registry.ModBlockEntities` | Block entities | Registry |
| `at.koopro.wizardsandbeasts.registry.ModMenuTypes` | Menu types | UI |
| `at.koopro.wizardsandbeasts.registry.ModAttributes` | Custom attributes | Stats |
| `at.koopro.wizardsandbeasts.effect.ModEffects` | Custom effects | Combat |
| `at.koopro.wizardsandbeasts.network.ModNetwork` | Payload registration root | Networking |
| `at.koopro.wizardsandbeasts.network.PacketCodecUtils` | Shared packet codecs | Networking |
| `at.koopro.wizardsandbeasts.spell.core.Spells` | Hybrid spell registry | Spells |
| `at.koopro.wizardsandbeasts.spell.core.Spell` | Spell abstraction | Spells |
| `at.koopro.wizardsandbeasts.spell.def.SpellReloadListener` | JSON spell reload | Spells |
| `at.koopro.wizardsandbeasts.spell.cast.SpellCastService` | Cast orchestration | Spells |
| `at.koopro.wizardsandbeasts.spell.cast.SpellExecutor` | Generic execution dispatch | Spells |
| `at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic` | Channelled beam server logic | Spells |
| `at.koopro.wizardsandbeasts.spell.data.PlayerSpellData` | Loadout/known/cooldown state | Spells |
| `at.koopro.wizardsandbeasts.spell.learning.SpellLearningService` | Learning eligibility | Spells |
| `at.koopro.wizardsandbeasts.spell.proficiency.SpellProficiencyTracker` | Cast mastery | Spells |
| `at.koopro.wizardsandbeasts.item.wand.WandItem` | Wand interaction/casting item | Wand |
| `at.koopro.wizardsandbeasts.wand.customization.WandModuleLoader` | Module reload | Wand |
| `at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry` | Module store/bootstrap | Wand |
| `at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries` | Dynamic wand data registries | Wand |
| `at.koopro.wizardsandbeasts.wand.allegiance.AllegianceSystem` | Wand ownership/allegiance | Wand |
| `at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity` | Bench state | Wand |
| `at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipeSerializer` | Custom recipe codec | Wand |
| `at.koopro.wizardsandbeasts.skill.SkillNodeLoader` | JSON skill rebuilding | Skills |
| `at.koopro.wizardsandbeasts.skill.SkillSystemAPI` | Gameplay skill queries | Skills |
| `at.koopro.wizardsandbeasts.skill.data.PlayerSkillData` | Player skill state | Skills |
| `at.koopro.wizardsandbeasts.skill.vocation.VocationLoader` | Vocation reload | Skills |
| `at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData` | Heritage persistence | Heritage |
| `at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialTierRules` | Obscurial rules | Heritage |
| `at.koopro.wizardsandbeasts.form.TransitionManager` | Form transitions | Forms |
| `at.koopro.wizardsandbeasts.ability.AnimagusAbilityService` | Animagus service | Forms |
| `at.koopro.wizardsandbeasts.broom.BroomEntity` | Flight physics | Brooms |
| `at.koopro.wizardsandbeasts.client.broom.BroomClientInputHandler` | Client flight input | Brooms |
| `at.koopro.wizardsandbeasts.bestiary.BestiaryEntryLoader` | Bestiary reload | Bestiary |
| `at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData` | Discovery state | Bestiary |
| `at.koopro.wizardsandbeasts.creature.CreatureDefinitionLoader` | Creature JSON reload | Creatures |
| `at.koopro.wizardsandbeasts.creature.CreatureDefinition` | Creature schema | Creatures |
| `at.koopro.wizardsandbeasts.entity.creature.GenericGroundBeastEntity` | Ground generic creature | Creatures |
| `at.koopro.wizardsandbeasts.entity.creature.GenericFlyingBeastEntity` | Flying generic creature | Creatures |
| `at.koopro.wizardsandbeasts.entity.creature.GenericAquaticBeastEntity` | Aquatic generic creature | Creatures |
| `at.koopro.wizardsandbeasts.entity.creature.DragonEntity` | Dragon specialisation | Creatures |
| `at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity` | Niffler behaviour | Entity |
| `at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity` | Vault NPC | Economy |
| `at.koopro.wizardsandbeasts.brew.Brews` | Brew store | Brewing |
| `at.koopro.wizardsandbeasts.brew.def.BrewReloadListener` | Brew reload | Brewing |
| `at.koopro.wizardsandbeasts.brew.CauldronBrewing` | Cauldron interaction | Brewing |
| `at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData` | Vault balances | Economy |
| `at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper` | Currency conversion | Economy |
| `at.koopro.wizardsandbeasts.floo.FlooNetworkManager` | Floo destinations | Travel |
| `at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic` | Authoritative Apparition mechanics | Travel |
| `at.koopro.wizardsandbeasts.map.MaraudersMapTracker` | Map tracking/sync | Map |
| `at.koopro.wizardsandbeasts.trunk.template.PocketTemplateLoader` | Pocket template reload | Storage |
| `at.koopro.wizardsandbeasts.trunk.TrunkBlockEntity` | Trunk/pocket state | Storage |
| `at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity` | Floo block state | Travel |
| `at.koopro.wizardsandbeasts.memory.PlayerMemoryData` | Memory persistence | Memory |
| `at.koopro.wizardsandbeasts.bloodpact.BloodPactRecord` | Pact schema | Blood pacts |
| `at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructures` | Structure registration | Worldgen |
| `at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructure` | Structure generation | Worldgen |
| `at.koopro.wizardsandbeasts.registry.ModFeatures` | Feature registration | Worldgen |
| `at.koopro.wizardsandbeasts.world.ModConfiguredFeatures` | Feature keys | Worldgen |
| `at.koopro.wizardsandbeasts.world.ModPlacedFeatures` | Placement keys | Worldgen |
| `at.koopro.wizardsandbeasts.client.ClientSetup` | Renderer/layer setup | Client |
| `at.koopro.wizardsandbeasts.client.spell.SpellClientInputHandler` | Spell input | Client |
| `at.koopro.wizardsandbeasts.client.spell.hud.SpellDiamondOverlay` | Spell HUD | Client |
| `at.koopro.wizardsandbeasts.client.wand.WandBeamRenderer` | Wand beam visuals | Client |
| `at.koopro.wizardsandbeasts.client.form.TransitionEffectRenderer` | Form overlays | Client |
| `at.koopro.wizardsandbeasts.client.particle.ModParticleProviders` | Particle factories | Client |
| `at.koopro.wizardsandbeasts.datagen.ModDataGenerators` | Datagen registration | Datagen |
| `at.koopro.wizardsandbeasts.datagen.ModModelProvider` | Models/blockstates | Datagen |
| `at.koopro.wizardsandbeasts.datagen.ModRecipeProvider` | Recipes | Datagen |
| `at.koopro.wizardsandbeasts.module.ModuleManager` | Feature gating | Modules |
| `at.koopro.wizardsandbeasts.util.NamespaceMigration` | Legacy ID migration | Compatibility |
| `at.koopro.wizardsandbeasts.command.WandbCommands` | `/wandb` command root | Commands |

## 16. Extension guide

### Add a spell

1. Decide whether it is data-expressible. Prefer `data/.../spells/<id>.json` plus `SpellDefinition`/effect components. Create Java only for genuinely bespoke state, targeting, entities or timing.
2. For Java, subclass/implement the `Spell` contract in `spell.impl` (or a feature subpackage), register through `Spells.register(...)`, and build `SpellProperties`/requirements.
3. Route through existing cast types/`SpellExecutor` before adding a new dispatcher branch.
4. Add skill unlock/proficiency/teacher data as appropriate, assets/lang, tests for codecs/rules, and a packet only if no existing cast/sync flow covers it.
5. Test normal cast, rejection, cooldown, dedicated-server safety, reload (JSON), death/respawn sync and multiplayer latency.

### Add a creature

1. Add `ModCreatures.Spec(id, locomotion, width, height)`; use `DRAGON_IDS` only for dragon behaviour.
2. Add `data/.../creatures/<id>.json` validated by `CreatureDefinition.CODEC`.
3. Add bestiary entry, loot, model/texture/animation, lang, spawn egg assets and biome modifier/spawn placement if intended.
4. Use a bespoke entity class only when generic locomotion/traits cannot express behaviour; then register it in `ModEntities` and bind attributes/rendering/spawns.

### Add a block or item

1. Register through `ModBlocks`/the relevant block helper or `ModItems`/the relevant grouped item registry.
2. Register a block item with the holder rather than prematurely calling `.get()`.
3. Add datagen hooks for model, blockstate, loot, tags, recipe and language; run `runData` and commit output.
4. Add block entity/menu/client screen only when the block genuinely owns state or interaction UI.

### Add a skill, structure, packet, screen, wand module, or datapack type

- **Skill:** author a `skill_nodes/<tree>/<id>.json`; use the existing effect vocabulary, prerequisite IDs and layout fields. Extend codecs/effects only when the existing model cannot represent the behaviour.
- **Structure:** register type/piece/placement in `AzkabanStructures`-style code, then supply `worldgen/structure`, structure-set/template resources, placement and biome integration.
- **Packet:** place a bounded payload in the owning `network/<feature>` package, register it in that feature’s `ModNetwork*`, validate at the authoritative side, and add a codec-boundary test.
- **Screen:** register `MenuType`, create a server menu with network constructor, bind the screen in `WizardsAndBeastsClient`, and sync only the data it needs.
- **Wand module:** add data/definition using `WandModuleLoader`/registry conventions; ensure its evaluation is server-safe and tooltips/client UI read synced data only.
- **New reloadable type:** define an immutable definition plus codec, add a reload listener/store, register it from the common bootstrap, clear/rebuild atomically, log failures with IDs, document folder/schema and test malformed input.

## 17. Roadmap and status

### Complete or materially implemented

Wands, spellcasting, skills, heritage/profession selection, forms, brooms, vaults, generic creatures/bestiary, tree/plants/worldgen, brewing MVP, Floo, storage/trunks, core artefacts, client HUD/screens and datagen are present.

### Active / in progress

- Skill-tree chart UI/assets are currently uncommitted workspace work.
- Alpha hardening: multiplayer spell/channel behaviour, lifecycle sync, balance, debug polish and high-player-count map/beam tuning.
- Asset coverage and localisation continue alongside systems.

### Planned or explicitly incomplete

- A timed/block-entity cauldron brewing experience replaces the current atomic event-driven MVP.
- House-specific progression/mechanics are intentionally not part of this alpha.
- Pre-beta large-server soak, further UI/admin-flow polish and more content are noted in §21.9.
- Some module-gated dark-arts/artefact content is present but intentionally disabled or not fully wired.

## 18. Known issues and technical constraints

- Rapid channel release can desynchronise in multiplayer edge cases.
- `runServer` can fail in some local environments during NeoForge bootstrap; this is not the same as a confirmed code failure, but must be reproduced/resolved before release confidence.
- Balance values are intentionally provisional.
- Debug logs/visuals and a small number of TODOs remain; source scan currently finds nine `TODO`/`FIXME` markers, including an unresolved Floo fireplace asset.
- The mod has legacy namespace and attachment compatibility paths. Do not remove them without a migration/version policy and save tests.
- Creature registry facts are duplicated between Java manifest and JSON definitions; mismatches are a release risk.
- Generated resources are committed. A stale datagen run can produce source/resource drift even when Java compiles.

## 19. Architecture review

### Documentation drift

`ARCHITECTURE.md` identifies itself as partial and is demonstrably stale in places: it describes a 27-spell composition and an older NeoForge version, while current source has 32 bundled spells and `gradle.properties` specifies NeoForge `21.11.42`. Preserve it as historical context, but update it from this reference/source before relying on it for implementation decisions.

### High-value refactor opportunities

1. **Unify content validation:** validate `ModCreatures.MANIFEST` against creature JSON, bestiary entries, assets and spawn/loot declarations in one test/datagen validation pass.
2. **Clarify component ownership:** `ModDataComponents` and `WandComponents` both own deferred data-component registers. The domain split is reasonable, but document it and avoid third component registries.
3. **Reduce implicit class-loading registration:** `ModBlocks` calls location-registry `init()` methods in a static block. It works, but explicit constructor-time registration is easier to audit and less fragile around init order.
4. **Codify feature readiness:** `ModuleManager` gating, TODO tracker, alpha status and audit documents should feed one maintained feature-status matrix so “registered”, “craftable”, “enabled”, and “production-ready” cannot be confused.
5. **Keep client caches bounded:** client static state holders are appropriate caches but need lifecycle clears and should never be treated as authoritative. The existing refactor log already calls this out.
6. **Add integration tests for reload and multiplayer:** codec/unit tests are strong; add GameTests or dedicated-server harness coverage for `/reload`, reconnect, dimension change, active channels and repeated UI transactions.

## 20. Final technical summary

Wizards & Beasts is a feature-rooted NeoForge 1.21.11 RPG mod. `WizardsAndBeastsMod` wires standard NeoForge registries, attachments, components, packets, entity attributes and reload listeners; feature packages then own their server logic, data models, events, packets and client presentation. The authoritative gameplay path is server-side, while clients provide input, cached synchronised state and rendering.

For a contributor, the governing pattern is: **put code in the owning feature package; use NeoForge deferred registries for game objects; use attachments for persistent player/entity state; use data components for per-stack state; make variant-heavy content reloadable JSON; validate every client request server-side; keep client classes out of shared logic; generate standard resources through datagen; and test both codecs and lifecycle/multiplayer behaviour.**

Before implementing a feature, inspect its nearest existing analogue, update its data/assets/tests/sync path as a single change, run `test` and `runData`, then use `runGameTestServer` or a real multiplayer smoke test where the feature crosses the client/server boundary.

## 21. Alpha Release Gates & Smoke Checks

> Merged from `ALPHA_STATUS.md` (2026-07-18). Detailed bug tracking with file:line references lives in `FULL_AUDIT_REPORT.md` (stable `AUD-*` IDs) and `ALPHA_IMPROVEMENT_AUDIT.md` (broader design/gameplay findings). Open audit-tracked items include: Dark Arts gating gap on Avada/learning (`AUD-F-001/002`), Protego recast self-shatter (`AUD-E-001`), wand-tip cache (`AUD-C-001/002`).

### 21.1 Known Issues (0.1.0-alpha.1)
- Multiplayer desync edge cases may still occur during rapid spell channel release sequences.
- Obscurial ability input (`N`/`M`) is dark-form only by design; players expecting spell-slot behavior may need onboarding guidance.
- Balance values are provisional and may change quickly across alpha builds.
- Some debugging-focused visuals and logs may still be present while alpha hardening continues.
- Legacy-world namespace migration is complete; canonical IDs are `wizards_and_beasts:*`, while `WizardsAndBeastsMod:*` remains legacy-compatibility input during migration paths.
- `runServer` can fail on some local environments with `FatalStartupException: Couldn't find Minecraft server thread` during NeoForge launch bootstrap; `compileJava/test` and `runGameTestServer` remain the current CI-style verification path while this startup issue is investigated. Note: `runGameTestServer` is currently a boot-only smoke test — no `@GameTest`/`test_instance` definitions exist yet, so it does not verify any in-world behavior (see `ALPHA_IMPROVEMENT_AUDIT.md` §12.1).
- `time_turner` intentionally uses a simplified gameplay-safe world-time advance mechanic and is not a full canonical time-travel simulation.
- House assets now have flavor parity coverage, but house-specific mechanics/progression are still intentionally not implemented in this alpha.
- Datapack spells (`episkey`, `frigora`, `levicorpus`) now keep JSON definitions for tuning, while bespoke Java handlers provide lore-focused runtime behavior.
- Regional/non-canonical currency handling (for example `dragot`) remains a gameplay extension and is intentionally separated from canonical UK coin conversion logic.

### 21.2 Test Environment
- Minecraft: `1.21.11`
- NeoForge: `21.11.42`
- Java: `21`
- Mod jar: build artifact from CI or local `build/libs`

### 21.3 Installation
**Client (singleplayer or multiplayer):** Install Java 21 → Install NeoForge for Minecraft 1.21.11 → Place jar in `mods` → Launch and verify mod list.

**Dedicated Server:** Install Java 21 → Create NeoForge server for 1.21.11 → Place jar in server `mods` → Start and confirm no startup errors.

### 21.4 Recommended Alpha Scenarios
1. Fresh world wizarding progression start.
2. Spell slot assign/select/cast loop for all four slots.
3. Channel spell hold and release behavior under normal latency and poor latency.
4. Save/reload and reconnect after active spell usage.
5. Dedicated server session with at least two clients.

### 21.5 Bug Report Format
Include: alpha version, environment, MC/NeoForge/Java versions, reproduction steps, expected/actual result, `latest.log` and crash report. For Obscurial issues also include: active form, ability key used, HUD cooldown/readiness state, approx. latency.

### 21.6 Save Safety
- Alpha builds may contain compatibility-breaking changes.
- Back up worlds before upgrading between alpha versions.

### 21.7 Systems Checklist (multi-feature smoke pass)
- **Heritage (type) + professions:** On first join without a chosen type, the selector opens. After choosing, `/wandb type info` shows current type and profession state. `/wandb type list` lists all types. Profession commands under `/wandb type profession ...` (`list`, `info`, `unlock`, `select`, `points`). Changing another player's type requires operator (`type set` / `type reset`).
- **Vault (Gringotts):** Talk to Goblin Teller NPC to open vault UI (deposit/withdraw/sync). Verify balances update and no disconnects under multiplayer lag.
- **Skills:** Earn skill points from XP level-ups, choosing heritage, and spell proficiency milestones (`SkillEvents`). Spend with `/wandb skill unlock <skillId>` (survival). `/wandb skill points` shows balance. `/wandb skill info` and `/wandb skill list` are reference commands. Giving points, force-unlocking, or resets require operator permission.
- **Spells:** Learn via unlocked skills (`LearnSpell` effects) or `/wandb spell learn <spellId>`. Cast with wand and slot HUD. `/wandb spell reset` and `/wandb spell learnall` restricted to operators.
- **Obscurial abilities (dark form only):** Separate from spell slots; do not appear in spell teacher offers or slot assignment. Enter dark form, then `N` = `Obscurus Surge`, `M` = `Obscurus Grasp`. Verify cooldown/readiness in Obscurus HUD panel and action-bar feedback for lockout/cooldown/invalid-form.
- **Forms and size (admin-heavy):** `/wandb wizform list` informational for anyone. Applying forms/size to players is operator-only (`wizform set|reset`, `/wandb wizsize` subtree).
- **Morph debug tools:** Under `/wandb debug wizmorph ...` (operators only).

### 21.8 Release Candidate Gate (alpha.2+)
For every release candidate, run all checks before tagging:
- `./gradlew test` must pass (packet codec bounds + sync regression tests included).
- `./gradlew runData` must pass (generated resources up to date for this commit).
- Fresh server boot (`./gradlew runGameTestServer`) must start cleanly with no startup errors.
- Two-client dedicated smoke pass: Client A and B join same server → validate heritage selection, spell select/assign/cast, skill unlock, form sync → validate reconnect after dimension change and death/respawn → validate lifecycle re-sync after login, respawn, dimension change.
- Record any failures under Known Issues with reproduction steps.
- RC policy: if any gate fails, do not tag/release until Known Issues updated with environment + reproduction + logs.

### 21.9 Pre-Beta Confidence Gate
Before pre-beta signoff, run this full gate twice on fresh server boots:
- `./gradlew test` passes.
- `./gradlew runData` passes and generated resources updated.
- Fresh server boot (`./gradlew runGameTestServer`) starts cleanly.
- Two-client dedicated smoke: heritage/type select, spell assign/select/cast, skill unlock, form transitions; Floo grate destination bind + travel with Floo Powder; timed brewing completion with valid heat and pause behavior with missing heat.
- 30-minute dedicated soak: sustained Marauder's Map use, beam channeling, form transitions; no server tick collapse, no stuck transitions, no runaway sync spam.

### 21.10 Lore Accuracy Smoke (major-pass addendum)
Add this short pass before release tagging:
- **Currency economy:** Verify denomination crafting both directions: `29 Knut -> 1 Sickle`, `17 Sickle -> 1 Galleon`, reverse conversions preserve totals.
- **Niffler behavior:** Verify dropped `Knut/Sickle/Galleon` prioritized over non-canonical currency. Verify Niffler steals single highest-value canonical coin first when multiple denominations exist.
- **Spells datapack tuning:** Verify `Levicorpus`, `Frigora`, `Episkey` load from datapack definitions and cast with updated sounds/effect windows.
- **Localization source-of-truth:** Run `./gradlew runData` and verify generated `en_us.json` contains both names and `.desc` lore tooltip entries.
- **House flavor parity:** Verify all four houses expose portrait flavor entries (`portrait_gryffindor_common`, `portrait_slytherin_common`, `portrait_ravenclaw_common`, `portrait_hufflepuff_common`) with localized title/author text.
- **World placement lore pass:** Verify custom tree features still generate in intended biome families after `minecraft:biome` guardrail addition. Verify wizarding plant patches generate naturally: `mandrake_patch` in forest-family biomes, `mallowsweet_patch` in meadow/plains/flower-forest biomes, `devils_snare_patch` in dark/swamp-family biomes.

### 21.11 Coming Soon (post-0.1.0-alpha.1)
- Typed full/delta sync rollout for additional systems.
- Automated gameplay integration scenarios (cast/select/channel/cooldown).
- Beam renderer performance profile presets for low-end clients.
- Improved HUD/menu feedback when the server rejects a cast or assignment.

## Geo space vs Java model space (verified 2026-08-12)

Needed by every keyframe clip authored against `player.geo.json`. Verified from implementation
bytecode and asserted by `PlayerGeoRigParityTest`, not recalled.

**Positions.** Geo space has its origin at the feet with Y up. Java model space has its origin at the
neck with Y down. So `y_model = 24 - y_geo`; X and Z pass through unchanged. Geo pivots and cube
origins are absolute; `addBox` coordinates are relative to the owning part's pivot.

**Rotations.** The map above is the reflection `diag(1, -1, 1)`. Conjugating a rotation by it gives
`R_x(-t)`, `R_y(t)`, `R_z(-t)` — **X and Z negate, Y is unchanged**. Y survives because a reflection
in the plane perpendicular to an axis commutes with rotation about that axis.

**Do not confuse this with vanilla's `scale(-1, -1, 1)`** in `LivingEntityRenderer.submit`. That is a
180 degree rotation about Z, not a reflection, and it negates X and Y while leaving Z alone. Mixing
the two up is what put the flight pose's head counter-rotation in backwards.

**Transform order is identical on both sides.** GeckoLib's
`RenderUtil.translateAndRotateMatrixForBone` is translate-to-pivot, Z, Y, X, translate-away;
`ModelPart.translateAndRotate` is translate, Z, Y, X. Both divide pivots by 16.
`BakedModelFactory.Builtin.constructBone` applies no negation of its own — GeoBone pivots and
rotations are the raw JSON values.

**`CubeDeformation` is not in the cube bounds.** `ModelPart.Cube.minX/maxX` are the un-grown bounds;
the deformation is applied to vertex positions only. An overlay child's bounds therefore equal its
base part's, with inflate carried separately — same as the geo file states it.
