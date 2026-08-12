# Wizards & Beasts — Architecture Reference

> **STATUS: Historical snapshot 2026-05-19.** Superseded by `DEVELOPER_REFERENCE.md` (snapshot 2026-07-13) wherever they disagree. Retained for original-depth package-tree detail; numbers (NeoForge 21.11.38-beta, 27-spell composition) may lag.
>
> **Doc status (partial):** Sections 3.2 (Spell System), 3.7 (Network Layer), 3.8 (Data / Persistence), 3.12 (Skill System), and 5 (Spell Implementation Status) were resynced to source on the latest pass. Other sections may lag — when in doubt, the source files cited within each section are authoritative.

## 1. Overview

| Property | Value |
|----------|-------|
| **Mod ID** | `wizards_and_beasts` |
| **Mod Name** | Wizards & Beasts |
| **Author** | Koopro |
| **Version** | 0.1.0-alpha.1 |
| **Platform** | NeoForge 21.11.38-beta |
| **Minecraft** | 1.21.11 |
| **Java** | 21 |
| **GeckoLib** | 5.4.5 |

Wizards & Beasts is a Harry Potter–themed magic mod for Minecraft 1.21.11 on the NeoForge platform. It introduces a wand-and-spell system (27 spells total: 24 Java + 3 JSON across 4 categories), a rideable broom with full server-side physics simulation, the Marauder's Map for real-time entity tracking, the Gringotts banking system with five currency types (including self-destructing Leprechaun Gold), two custom NPC entities (Goblin Teller, Niffler with custom AI), and four magical wood types grown via custom world-generation tree features.

---

## 2. Package Tree

### Java Sources

```
src/main/java/at/koopro/wizardsandbeasts/
├── WizardsAndBeastsMod.java                               # @Mod entry point — registers all DeferredRegisters on
│                                          #   the mod event bus; calls SpellEffects.registerAll();
│                                          #   registers Config and entity attributes
├── WizardsAndBeastsClient.java                         # Client-only @EventBusSubscriber — registers renderers,
│                                          #   screens, key bindings via FMLClientSetupEvent
├── Config.java                            # NeoForge ModConfigSpec (logDirtBlock, magicNumber, etc.)
│
├── registry/                              # All DeferredRegister declarations
│   ├── ModItems.java                      # ~51 items: wand, broom, map, currency, wand core
│   │                                      #   materials, block items for 4 wood sets, spawn eggs
│   ├── ModBlocks.java                     # 36 blocks: Elder/Yew/Holly/Rowan wood sets
│   │                                      #   (log, stripped log, wood, stripped wood, planks,
│   │                                      #   slab, stairs, leaves, sapling × 4 wood types)
│   ├── ModEntities.java                   # 4 entity types: BROOM, SPELL_PROJECTILE,
│   │                                      #   GOBLIN_TELLER, NIFFLER
│   ├── ModCreativeTabs.java               # Creative tab groupings
│   ├── ModDataComponents.java             # 5 data components: WAND_WOOD, WAND_CORE,
│   │                                      #   WAND_LENGTH, WAND_FLEXIBILITY, CREATION_TICK
│   ├── ModAttachments.java                # 2 player attachments: SPELL_DATA, VAULT_DATA
│   │                                      #   (both copyOnDeath)
│   ├── ModSounds.java                     # Custom sound events (broom_crash, etc.)
│   ├── ModFeatures.java                   # DeferredRegister for custom tree Feature<?>
│   └── ModDimensions.java                 # Custom dimension registry (future)
│
├── spell/                                 # Spell system
│   ├── Spell.java                         # 20-entry enum: id, displayName, SpellCategory,
│   │                                      #   baseCooldownTicks, baseDamage, color; holds SpellEffect
│   ├── SpellCategory.java                 # Enum: COMBAT, UTILITY, DEFENSE, DARK_ARTS
│   │                                      #   (each with ARGB display color)
│   ├── SpellEffect.java                   # Functional interface:
│   │                                      #   execute(ServerLevel, ServerPlayer, ItemStack)
│   ├── SpellEffects.java                  # Static factory — registers 6 implemented spell
│   │                                      #   effects at startup via Spell.setEffect()
│   └── Proficiency.java                   # Enum placeholder for future spell mastery system
│
├── wand/                                  # Wand attribute enums (all implement StringRepresentable)
│   ├── WandWood.java                      # Enum: ELDER, YEW, HOLLY, ROWAN
│   ├── WandCore.java                      # Enum: PHOENIX_FEATHER, DRAGON_HEARTSTRING,
│   │                                      #   UNICORN_HAIR, THESTRAL_TAIL
│   ├── WandLength.java                    # Enum: SHORT (9"), MEDIUM (11"), STANDARD (13"), LONG (15")
│   └── WandFlexibility.java               # Enum: RIGID, SLIGHTLY_YIELDING, SUPPLE, QUITE_FLEXIBLE
│
├── item/                                  # Item implementations
│   ├── WandItem.java                      # Reads WAND_WOOD/CORE/LENGTH/FLEXIBILITY data
│   │                                      #   components; sends SpellCastC2SPacket on use
│   ├── BroomItem.java                     # Right-click: spawns BroomEntity, mounts the player
│   ├── MaraudersMapItem.java              # Validates trust; registers player with
│   │                                      #   MaraudersMapTracker; sends MapOpenS2CPacket
│   ├── ParchmentItem.java                 # Crafting ingredient for the Marauder's Map
│   └── InkItem.java                       # Crafting ingredient for the Marauder's Map
│
├── entity/                                # Entity implementations
│   ├── BroomEntity.java                   # Rideable broom — server-side physics, tilt angles,
│   │                                      #   crash/collision logic, GeckoLib animations
│   ├── SpellProjectileEntity.java         # Spell bolt — carries spellId, applies hit effect
│   ├── GoblinTellerEntity.java            # Gringotts NPC — opens vault UI on interact
│   ├── NifflerEntity.java                 # Creature attracted to coins; custom AI goals
│   └── ai/
│       ├── NifflerPickupCoinGoal.java     # AI: pick up currency items on the ground
│       └── NifflerStealFromPlayerGoal.java# AI: steal currency from nearby players
│
├── map/                                   # Marauder's Map server logic
│   ├── MaraudersMapTracker.java           # @EventBusSubscriber — tracks active viewers,
│   │                                      #   syncs entity list every 5 ticks, max 200 entities
│   └── TrackedEntityEntry.java            # Record: uuid, x, z, yRot, name, category byte
│
├── network/                               # Network layer
│   ├── ModNetwork.java                    # Registers all 11 packets in
│   │                                      #   RegisterPayloadHandlersEvent (registrar "1")
│   ├── BroomInputPacket.java              # C→S: broom movement input (7 fields)
│   ├── MapOpenS2CPacket.java              # S→C: open MaraudersMapScreen
│   ├── MapSyncPacket.java                 # S→C: stream List<TrackedEntityEntry>
│   ├── MapCloseC2SPacket.java             # C→S: remove player from active viewer set
│   ├── SpellCastC2SPacket.java            # C→S: trigger SpellEffect.execute() server-side
│   ├── SpellSelectC2SPacket.java          # C→S: change active loadout slot
│   ├── SpellAssignC2SPacket.java          # C→S: assign spell ID to loadout slot
│   ├── SpellDataSyncS2CPacket.java        # S→C: push PlayerSpellData snapshot to client
│   ├── GringottsOpenS2CPacket.java        # S→C: open GringottsScreen
│   ├── VaultActionC2SPacket.java          # C→S: deposit or withdraw currency
│   ├── VaultSyncS2CPacket.java            # S→C: push updated vault balances
│   ├── ClientSpellDataHolder.java         # Client-side cache of synced PlayerSpellData
│   └── ClientVaultDataHolder.java         # Client-side cache of synced PlayerVaultData
│
├── data/                                  # Server-side persistent player data (NBT)
│   ├── PlayerSpellData.java               # Known spells, 4-slot loadout, active slot,
│   │                                      #   per-spell cooldowns (expiry tick), cast counts
│   └── PlayerVaultData.java               # Per-denomination currency balances in vault
│
├── client/                                # Client-only code
│   ├── ClientSetup.java                   # FMLClientSetupEvent — registers renderers,
│   │                                      #   key bindings, HUD overlays
│   ├── BroomRenderer.java                 # GeoEntityRenderer<BroomEntity>
│   ├── BroomRiderRenderer.java            # RenderLayer — adjusts rider pose from tilt angles
│   ├── BroomRiderRenderHandler.java       # Event hook: applies BroomRiderRenderer during player render
│   ├── BroomClientInputHandler.java       # Per-tick key capture; sends BroomInputPacket while mounted
│   ├── GoblinTellerRenderer.java          # GeckoLib renderer for GoblinTellerEntity
│   ├── NifflerRenderer.java               # GeckoLib renderer for NifflerEntity
│   ├── SpellProjectileRenderer.java       # Renders spell bolt as colored trail
│   ├── SpellClientInputHandler.java       # Key events for spell menu and slot cycling
│   ├── SpellKeyBindings.java              # Key binding registrations (open menu, cycle slot)
│   ├── gui/
│   │   ├── SpellMenuScreen.java           # Drag-and-drop UI: assign known spells to 4 loadout slots
│   │   └── GringottsScreen.java           # Bank UI: five currency balances, deposit/withdraw buttons
│   ├── hud/
│   │   └── SpellDiamondOverlay.java       # HUD: diamond of 4 spell slot icons, active slot highlighted
│   └── map/
│       ├── MaraudersMapRenderer.java      # Draws entity dots (color by category) on parchment background
│       ├── MaraudersMapScreen.java        # Full-screen parchment map GUI
│       └── MapClientHandler.java          # Handles MapSyncPacket; stores entity list for renderer
│
├── currency/                              # Currency logic
│   ├── LeprechaunGoldItem.java            # Fake coin: auto-destroys after 6000t via CREATION_TICK
│   └── CurrencyHelper.java                # Exchange-rate utilities (Knut/Sickle/Galleon/Dragot)
│
├── command/                               # Server commands and debug tools
│   ├── NeoCommands.java                   # Root /wandb command registration
│   ├── SpellCommands.java                 # /wandb spell learn|forget|reset subcommands
│   ├── DebugWandItem.java                 # Debug wand: prints target block/entity info on use
│   ├── DebugWandState.java                # Holds debug wand interaction state
│   ├── DebugTreeCommand.java              # /wandb tree <type> — place a test tree in-world
│   └── TreeDebugUtil.java                 # Utility for debug tree placement
│
├── world/                                 # World generation
│   ├── ModConfiguredFeatures.java         # ConfiguredFeature definitions for all 4 tree types
│   ├── ModPlacedFeatures.java             # PlacedFeature definitions with placement modifiers
│   └── tree/
│       ├── ElderTreeFeature.java          # Procedural Elder tree generator
│       ├── HollyTreeFeature.java          # Procedural Holly tree generator
│       ├── RowanTreeFeature.java          # Procedural Rowan tree generator
│       └── YewTreeFeature.java            # Procedural Yew tree generator
│
└── datagen/                               # Offline data generators (./gradlew runData)
    ├── ModDataGenerators.java             # GatherDataEvent handler — wires all providers
    ├── ModLanguageProvider.java           # en_us.json translations
    ├── ModModelProvider.java              # Block and item model JSON generation
    ├── ModBlockTagsProvider.java          # Block tag assignments (logs, leaves, etc.)
    ├── ModItemTagsProvider.java           # Item tag assignments
    ├── ModLootTableProvider.java          # Loot table provider orchestrator
    ├── ModBlockLootTableProvider.java     # Per-block drop tables
    └── ModRecipeProvider.java             # Crafting / smelting recipes
```

### Resource Files

```
src/main/resources/
├── assets/wizards_and_beasts/
│   ├── items/                             # Item model JSON files
│   ├── models/                            # Block and entity model JSON files
│   ├── geckolib/                          # GeckoLib animation descriptor JSONs
│   │                                      #   (broom, goblin_teller, niffler, marauders_map)
│   └── sounds.json                        # Sound event definitions (broom_crash, etc.)
├── data/                                  # Loot tables, recipes, advancements
│                                          #   (populated by ./gradlew runData)
├── wizards_and_beasts.mixins.json         # Mixin configuration (currently stub)
├── pack.mcmeta                            # Resource pack metadata
└── META-INF/
    └── neoforge.mods.toml                 # Mod metadata (generated from src/main/templates/)
```

---

## 3. Subsystem Breakdowns

### 3.1 Registry

NeoForge's `DeferredRegister` pattern is used for every game object. Each registry class holds one static `DeferredRegister` field and a set of `DeferredHolder` / `DeferredItem` / `DeferredBlock` statics. All registers are subscribed to the mod event bus in `WizardsAndBeastsMod(IEventBus, ModContainer)`.

| File | Register type | Objects |
|------|---------------|---------|
| `ModItems.java` | `DeferredRegister.Items` | ~51 items |
| `ModBlocks.java` | `DeferredRegister.Blocks` | 36 blocks (4 wood sets × 9 blocks) |
| `ModEntities.java` | `DeferredRegister<EntityType<?>>` | 4 entity types |
| `ModCreativeTabs.java` | `DeferredRegister<CreativeModeTab>` | Creative tab(s) |
| `ModDataComponents.java` | `DeferredRegister<DataComponentType<?>>` | 5 components |
| `ModAttachments.java` | `DeferredRegister<AttachmentType<?>>` | 2 attachments |
| `ModSounds.java` | `DeferredRegister<SoundEvent>` | Custom sound events |
| `ModFeatures.java` | `DeferredRegister<Feature<?>>` | 4 tree features |
| `ModDimensions.java` | `DeferredRegister` (dimension) | Future dimension stubs |

---

### 3.2 Spell System

The spell system is built around an **abstract `Spell` class hierarchy** with a central in-memory registry (`Spells`) and a generic execution pipeline (`SpellExecutor`). Each spell is a concrete subclass under `at.koopro.wizardsandbeasts.spell.spells.*` (`Lumos`, `Nox`, `Stupefy`, …). There is also a per-spell `SpellRequirement` (prerequisite spell + minimum proficiency) consulted by the UI and — when `Config.enforceSpellRequirements` is true — server-side at cast time.

**Key classes:**

| Class | Role |
|-------|------|
| `Spell` | Abstract base. Constructor sets `id`, `displayName`, `SpellCategory`, `baseCooldownTicks`, `baseDamage`, `color`. Abstract `buildProperties()` and `buildRequirement()` are called by `Spells.init()` after all spells are registered (two-phase init so cross-references are safe). Default `execute(level, caster, wandStack)` delegates to `SpellExecutor.executeGeneric(...)`; subclasses (e.g. `Nox`) may override for custom logic |
| `SpellProperties` | Builder-driven container of all *behavioral* fields: `CastType`, range, knockback, ignite, explode, disarm, repair, pull/levitate, undead bonus, mob-control, target/self `MobEffectInstance` suppliers, cast `SoundEvent` + volume/pitch |
| `CastType` | Enum: `PROJECTILE`, `SELF`, `CONE`, `TARGETED`, `BEAM_CHANNEL`, `BEAM_LETHAL` — drives cast dispatch and held-channel behavior |
| `SpellExecutor` | Static pipeline — for each `CastType`, applies sound + self effects, then projectile/cone/targeted/self-specific behavior; multiplies damage by `SkillSystemAPI.getDamageMultiplier(...)` |
| `SpellRequirement` | Immutable: optional `prerequisiteId` + optional minimum `Proficiency`. `isMet(PlayerSpellData)` returns true when both are satisfied; `getDescription()` returns the chat-ready reason string |
| `Spells` | Central registry (`LinkedHashMap<String, Spell>`). Each spell is a `public static final` field initialized via `register(new YourSpell())`. `Spells.init()` runs `spell.init()` on every entry (two-phase). Lookup via `byId(...)` |
| `SpellCategory` | COMBAT / UTILITY / DEFENSE / DARK_ARTS; each with an ARGB display color |
| `Proficiency` | Three tiers (`NOVICE`, `PROFICIENT` ≥ 50 casts, `MASTERED` ≥ 200 casts) with cooldown and damage multipliers; `fromCastCount(int)` selects the tier |
| `PlayerSpellData` | Per-player server state: known spells (Set), 4-slot loadout (String[4]), active slot, per-spell cooldown expiry ticks, per-spell lifetime cast count |

**Adding a new spell** (three supported pathways):

| Pathway | When to use | How |
|---------|-------------|-----|
| **Java subclass** | Custom logic that doesn't fit `SpellProperties` (e.g. Nox caster-only cleanup, Avada channel kill flow) | Create a `Spell` subclass under `src/main/java/at/koopro/wizardsandbeasts/spell/spells/<YourSpell>.java`. Override `buildProperties()` (and `buildRequirement()`); override `execute(...)` only if generic dispatch is insufficient. Register in [`Spells.java`](src/main/java/at/koopro/wizardsandbeasts/spell/Spells.java). |
| **Datapack JSON** | Combat / utility / status-effect spells with no custom Java logic — covers most spell shapes | Drop a JSON file into `data/<namespace>/wizards_and_beasts/spells/<spell_id>.json` matching [`SpellDefinition.CODEC`](src/main/java/at/koopro/wizardsandbeasts/spell/def/SpellDefinition.java). It is auto-loaded by [`SpellReloadListener`](src/main/java/at/koopro/wizardsandbeasts/spell/def/SpellReloadListener.java) on every `/reload` and registered as a [`JsonSpell`](src/main/java/at/koopro/wizardsandbeasts/spell/JsonSpell.java) under the file's `ResourceLocation`. |
| **Third-party mod (addon)** | Another mod wants to contribute spells | Subscribe to [`RegisterSpellsEvent`](src/main/java/at/koopro/wizardsandbeasts/event/RegisterSpellsEvent.java) (fired on `NeoForge.EVENT_BUS` during `FMLCommonSetupEvent`) and call `Spells.register(yourSpell)` or `Spells.registerJson(yourJsonSpell)`. Java spells must use a fully-qualified id (`yourmod:spell_name`) to avoid collision. |

(Optional regardless of pathway) Add a `SkillEffect.LearnSpell("your_id")` skill in [`SpellMasterySkills.java`](src/main/java/at/koopro/wizardsandbeasts/skill/SpellMasterySkills.java) so survival players can unlock it.

**Spell ID resolution (`Spells.byId`):** A bare id like `"lumos"` resolves under the `wizards_and_beasts:` namespace; a fully-qualified id like `"yourmod:fireball"` is looked up as-is. This is the seam that lets datapack and addon spells coexist with built-in Java spells in the same registry without ambiguity.

**JSON `SpellDefinition` field map:**

| JSON field | Required | Meaning |
|------------|----------|---------|
| `displayName` | yes | UI name |
| `category` | yes | `combat` / `utility` / `defense` / `dark_arts` |
| `cooldownTicks` | yes | Base cooldown before wand/skill multipliers |
| `color` | yes | ARGB int for UI |
| `castType` | yes | `projectile` / `self` / `cone` / `targeted` |
| `baseDamage`, `range`, `knockback`, `igniteSeconds`, `explode {power, breaksBlocks}`, `disarms` | no | Combat knobs |
| `selfEffects[]`, `targetEffects[]` | no | `{ id, duration, amplifier }` mob-effect entries |
| `sound` | no | `{ id, volume, pitch }` cast SFX (vanilla `SoundEvent`) |
| `requirement` | no | `{ type: none\|knows\|proficiency, prerequisiteId, minProficiency }` |

The 16-arg cap on `RecordCodecBuilder.group` was hit by an earlier draft of the schema; some bespoke knobs (Reparo `repairsItem`, Alohomora `opensBlocks`, Accio `pullsTarget`, Wingardium Leviosa `levitatesTarget`, Imperio `controlsMob`) are intentionally **not** exposed in JSON and remain Java-only because they need full per-spell logic anyway. To add more JSON-exposed knobs, introduce a nested sub-record alongside `ExplosionDef` / `SoundDef` rather than adding more top-level fields.

**SpellCategory ARGB colors:**

| Category | ARGB hex |
|----------|----------|
| COMBAT | `0xFFFF4444` (red) |
| UTILITY | `0xFF44FF44` (green) |
| DEFENSE | `0xFF4488FF` (blue) |
| DARK_ARTS | `0xFF8B00FF` (purple) |

---

### 3.3 Wand System

Wands are non-stackable items whose attributes live entirely as NeoForge data components on the `ItemStack` — no raw NBT is used. Each attribute enum implements `StringRepresentable` and provides both a `CODEC` (disk persistence) and a `STREAM_CODEC` (network sync).

**Attribute enums:**

| Enum | Values |
|------|--------|
| `WandWood` | `ELDER`, `YEW`, `HOLLY`, `ROWAN` |
| `WandCore` | `PHOENIX_FEATHER`, `DRAGON_HEARTSTRING`, `UNICORN_HAIR`, `THESTRAL_TAIL` |
| `WandLength` | `SHORT` (9"), `MEDIUM` (11"), `STANDARD` (13"), `LONG` (15") |
| `WandFlexibility` | `RIGID`, `SLIGHTLY_YIELDING`, `SUPPLE`, `QUITE_FLEXIBLE` |

**Data components stored on the wand ItemStack:**

| Component key | Type | Purpose |
|--------------|------|---------|
| `wizards_and_beasts:wand_wood` | `WandWood` | Wood type |
| `wizards_and_beasts:wand_core` | `WandCore` | Core material |
| `wizards_and_beasts:wand_length` | `WandLength` | Physical length |
| `wizards_and_beasts:wand_flexibility` | `WandFlexibility` | Flexibility rating |

A fifth component (`wizards_and_beasts:creation_tick`, type `Long`) is shared with `LeprechaunGoldItem` for its decay timer and is not wand-specific.

---

### 3.4 Marauder's Map

The map subsystem splits server-side tracking from client-side rendering across two packages.

| Class | Side | Role |
|-------|------|------|
| `MaraudersMapItem` | Server | Right-click handler; validates trust; calls `MaraudersMapTracker.addPlayer()`; sends `MapOpenS2CPacket` |
| `MaraudersMapTracker` | Server | `@EventBusSubscriber`; holds `Map<UUID, MapViewData>` of active viewers; fires `onServerTick()` every 5 ticks; AABB scan up to 200 `LivingEntity` per viewer |
| `TrackedEntityEntry` | Shared | `record`: `uuid`, `x`, `z`, `yRot`, `name`, `category` (byte: `PLAYER=0`, `HOSTILE=1`, `PASSIVE=2`) |
| `MapClientHandler` | Client | Handles `MapSyncPacket`; stores entity list in a static field consumed by the screen |
| `MaraudersMapScreen` | Client | Full-screen parchment GUI backed by `MaraudersMapRenderer` |
| `MaraudersMapRenderer` | Client | Draws entity dots color-coded by category on a parchment-textured background |

**Constants (from `MaraudersMapTracker.java`):**

| Constant | Value | Meaning |
|----------|-------|---------|
| `SYNC_INTERVAL_TICKS` | 5 | Entity list refresh rate (every 0.25 s) |
| `MAX_ENTITIES_PER_SYNC` | 200 | Hard cap on tracked entities per viewer per sync |

---

### 3.5 Broom System

The broom is a `GeoEntity` vehicle whose server-side motion is driven by per-tick client input forwarded via packets.

| Class | Role |
|-------|------|
| `BroomItem` | Right-click: spawns `BroomEntity` at player position and mounts the player |
| `BroomEntity` | Server + client physics; stores input state; calculates tilt angles; handles crash and entity collision; GeckoLib animations (idle / fly / boost) |
| `BroomClientInputHandler` | Captures movement keys each tick while player is mounted; sends `BroomInputPacket` |
| `BroomRenderer` | `GeoEntityRenderer<BroomEntity>` — applies GeckoLib model |
| `BroomRiderRenderer` | Render layer: reads `pitchTilt`, `rollTilt`, `forwardLean` from the entity; adjusts rider body transform |
| `BroomRiderRenderHandler` | Event hook that injects `BroomRiderRenderer` during the player's render pass |

**Physics constants (from `BroomEntity.java`):**

| Constant | Value | Notes |
|----------|-------|-------|
| `MAX_SPEED` | 1.2 m/t | Peak forward speed |
| `BOOST_MULTIPLIER` | 1.6× | Multiplied on `MAX_SPEED` during boost input |
| `ACCELERATION` | 0.08 | Lerp factor toward target speed |
| `DECELERATION` | 0.05 | Lerp factor toward zero |
| `WEAK_GRAVITY` | 0.015 m/t² | Downward pull when `speed < 0.1` and Up not pressed |
| `VERTICAL_SPEED` | 0.15 m/t | Speed added per tick for Up/Down input |
| `MAX_PITCH_TILT` | 25° | Max nose-up/down visual tilt |
| `MAX_ROLL_TILT` | 35° | Max bank-left/right visual tilt |
| `MAX_FORWARD_LEAN` | 20° | Rider lean at full speed |
| `TILT_SMOOTHING` | 0.35 | Lerp factor for all tilt transitions |
| `CRASH_SPEED_THRESHOLD` | 0.5 m/t | Minimum speed to trigger crash damage on block collision |
| `MAX_CRASH_DAMAGE` | 20 HP | Damage dealt at full boosted speed |
| `ENTITY_HIT_DAMAGE` | 4 HP | Base damage when hitting a living entity at full speed |
| `KNOCKBACK_FORCE` | 1.5 | Knockback magnitude scaled by speed ratio |
| `COLLISION_SPEED_MIN` | 0.3 m/t | Minimum speed before entity collision scanning runs |
| `COLLISION_COOLDOWN_TICKS` | 10 t | Ticks before the same entity can be hit again |

**GeckoLib animations:** `animation.broom.idle`, `animation.broom.fly`, `animation.broom.boost`

---

### 3.6 Entity System

| Entity | Mob category | Size (W × H) | GeckoLib | Purpose |
|--------|-------------|--------------|---------|---------|
| `BroomEntity` | MISC | 1.5 × 0.6 | Yes | Player-rideable broom vehicle |
| `SpellProjectileEntity` | MISC | 0.25 × 0.25 | No | Spell bolt: carries `spellId`, applies effect on impact |
| `GoblinTellerEntity` | CREATURE | 0.6 × 1.5 | Yes | Gringotts bank NPC; opens vault UI on interact |
| `NifflerEntity` | CREATURE | 0.5 × 0.4 | Yes | Coin-stealing creature with two custom `Goal` classes |

Niffler AI goals:
- `NifflerPickupCoinGoal` — scans ground for dropped currency items and picks them up
- `NifflerStealFromPlayerGoal` — moves toward nearby players and removes currency from their inventory

Entity attribute creation for `GoblinTellerEntity` and `NifflerEntity` is wired in `WizardsAndBeastsMod.registerEntityAttributes()` via `EntityAttributeCreationEvent`.

---

### 3.7 Network Layer

[`ModNetwork.register()`](src/main/java/at/koopro/wizardsandbeasts/network/ModNetwork.java) (listener on `RegisterPayloadHandlersEvent`) opens `PayloadRegistrar` version `"1"` and delegates to one registrar per feature so packet ownership stays close to the subsystem that uses it.

**Sub-registrars (one per file under `at.koopro.wizardsandbeasts.network`):**

| Registrar | Owns |
|-----------|------|
| `ModNetworkBroom` | Broom movement / control |
| `ModNetworkMap` | Marauder's Map open / close / sync |
| `ModNetworkSpells` | Spell cast / select / assign / data sync |
| `ModNetworkVault` | Gringotts vault open / action / sync |
| `ModNetworkSkills` | Skill unlock / data sync |
| `ModNetworkWizType` | Wizard type select / data sync |
| `ModNetworkForm` | Form-change request / start / end / sync |
| `ModNetworkBeamDebug` | Wand-beam debug overlay |

**Packets (current roster):**

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `BroomInputPacket` | C → S | Per-tick broom movement input: `forward`, `backward`, `up`, `down`, `boosting`, `yaw`, `pitch` |
| `SizeOverrideC2SPacket` | C → S | Debug: override broom rider size |
| `MapOpenS2CPacket` | S → C | Tells client to open `MaraudersMapScreen` with center / radius / dimension |
| `MapSyncPacket` | S → C | Streams updated `List<TrackedEntityEntry>` to all open map viewers |
| `MapCloseC2SPacket` | C → S | Removes the player from `MaraudersMapTracker`'s active viewer set |
| `SpellCastC2SPacket` | C → S | Triggers cast-release flow through `SpellCastService`. Server validates wand held, spell known, cooldown, requirement gates, and Obscurial rules before execution |
| `SpellSelectC2SPacket` | C → S | Changes the active loadout slot index |
| `SpellAssignC2SPacket` | C → S | Assigns a spell ID to a specific loadout slot |
| `SpellDataSyncS2CPacket` | S → C | Pushes a full typed `PlayerSpellData` snapshot to the client (used for login/full resync paths) |
| `SpellDataDeltaS2CPacket` | S → C | Hot-path post-cast delta sync (`spellId`, cooldown expiry tick, cast count) |
| `AvadaBlastS2CPacket` | S → C | One-shot green Avada blast FX, sent only when server confirms an Avada kill |
| `SkillUnlockC2SPacket` | C → S | Spends a skill point to unlock a skill |
| `SkillDataSyncS2CPacket` | S → C | Pushes a `PlayerSkillData` snapshot to the client |
| `GringottsOpenS2CPacket` | S → C | Tells client to open `GringottsScreen` with current vault data |
| `VaultActionC2SPacket` | C → S | Deposit or withdraw a currency denomination amount |
| `VaultSyncS2CPacket` | S → C | Pushes updated vault balances to the client |
| `TypeSelectC2SPacket` | C → S | Player picks a wizard type / archetype |
| `TypeDataSyncS2CPacket` | S → C | Pushes wizard-type state to the client |
| `FormChangeRequestC2SPacket` | C → S | Request a form change (e.g. Animagus) |
| `TransitionStartS2CPacket` | S → C | Begin form-transition visual sequence |
| `TransitionEndS2CPacket` | S → C | End form-transition visual sequence |
| `FormSyncS2CPacket` | S → C | Pushes current form data to the client |
| `BeamDebugOpenS2CPacket` | S → C | Opens the wand-beam debug overlay |
| `DebugOverlayToggleS2CPacket` | S → C | Toggles a debug HUD layer |

**Client-side caches (not packets):** `ClientSpellDataHolder`, `ClientSkillDataHolder`, `ClientVaultDataHolder`, `ClientTypeDataHolder`, `ClientFormDataHolder`, `ClientTransitionTracker` — hold the last received sync data for use by screens and HUD overlays.

`PacketCodecUtils` provides shared `StreamCodec` helpers used across packet definitions.

---

### 3.8 Data / Persistence

Both player data objects are stored as NeoForge Attachments on the player entity and serialized to NBT via `IAttachmentSerializer`. Both declare `copyOnDeath()` so data survives death.

**`PlayerSpellData` fields:**

| Field | Java type | Description |
|-------|-----------|-------------|
| `knownSpells` | `Set<String>` | Spell IDs the player has learned |
| `loadout` | `String[4]` | Spell IDs assigned to slots 0–3 (null = empty slot) |
| `activeSlot` | `int` | Currently selected loadout slot (0–3) |
| `cooldowns` | `Map<String, Long>` | Spell ID → game-tick at which cooldown expires |
| `castCount` | `Map<String, Integer>` | Spell ID → total lifetime casts (reserved for mastery system) |

NBT keys written by `PlayerSpellData.save()`: `KnownSpells` (ListTag), `Slot0`–`Slot3` (StringTag), `ActiveSlot` (int), `Cooldowns` (ListTag of `{id, tick}`), `CastCount` (ListTag of `{id, count}`).

**`PlayerVaultData`** — stores integer balances per currency denomination. Attachment key: `wizards_and_beasts:vault_data`.

---

### 3.9 Client / UI

| Class | Type | Description |
|-------|------|-------------|
| `SpellMenuScreen` | Screen | Drag-and-drop: assign spells from the player's known list to 4 loadout slots |
| `GringottsScreen` | Screen | Bank UI — five currency balances, deposit/withdraw buttons; reads `ClientVaultDataHolder` |
| `MaraudersMapScreen` | Screen | Full-screen parchment map GUI backed by `MaraudersMapRenderer` |
| `SpellDiamondOverlay` | HUD | Diamond of 4 spell slot icons rendered via `RenderGuiEvent`; highlights active slot |
| `BroomRenderer` | Entity renderer | `GeoEntityRenderer<BroomEntity>` |
| `BroomRiderRenderer` | Render layer | Adjusts the mounted player's body transform using entity tilt values |
| `GoblinTellerRenderer` | Entity renderer | GeckoLib renderer for the Goblin Teller NPC |
| `NifflerRenderer` | Entity renderer | GeckoLib renderer for the Niffler |
| `SpellProjectileRenderer` | Entity renderer | Renders the spell bolt as a colored trail |
| `MaraudersMapRenderer` | Map renderer | Draws entity dots color-coded by PLAYER/HOSTILE/PASSIVE on parchment |

---

### 3.10 Currency System

Five currency types exist. Only `LeprechaunGoldItem` has special behavior.

| Item | Registry key | Notes |
|------|-------------|-------|
| Knut | `wizards_and_beasts:knut` | Base denomination |
| Sickle | `wizards_and_beasts:sickle` | = 29 Knuts |
| Galleon | `wizards_and_beasts:galleon` | = 17 Sickles |
| Dragot | `wizards_and_beasts:dragot` | Wizarding-world alternative denomination |
| Leprechaun Gold | `wizards_and_beasts:leprechaun_gold` | Fake coin; glittering foil effect; self-destructs after **6000 ticks (~5 minutes)** via the `wizards_and_beasts:creation_tick` data component |

`CurrencyHelper` provides denomination exchange utilities. Vault balances are stored server-side in `PlayerVaultData` (copy-on-death attachment) and pushed to the client via `VaultSyncS2CPacket`.

---

### 3.11 World Generation

| Class | Role |
|-------|------|
| `ModFeatures.java` | `DeferredRegister<Feature<?>>` — one entry per magical tree type |
| `ModConfiguredFeatures.java` | `ConfiguredFeature` instances with generation parameters |
| `ModPlacedFeatures.java` | `PlacedFeature` instances with biome placement modifiers |
| `ElderTreeFeature.java` | Procedural generator for Elder wood (tall, branching canopy) |
| `YewTreeFeature.java` | Procedural generator for Yew wood |
| `HollyTreeFeature.java` | Procedural generator for Holly wood |
| `RowanTreeFeature.java` | Procedural generator for Rowan wood |

Each wood type has a full 9-block set: log, stripped log, wood, stripped wood, planks, slab, stairs, leaves, sapling. Block items are registered in `ModItems` via `registerSimpleBlockItem()`.

Datagen output target: `src/generated/resources/` (run `./gradlew runData`).

---

### 3.12 Brewing Pillar

The brewing module is **data-driven from day one** because there are dozens of canon potions, each one really just "drink → apply mob effects". A brew is data; an item slot is the only Java code per potion.

**Packages:**

- `at.koopro.wizardsandbeasts.brew` — runtime types & registries (`Brew`, `Brews`, `BrewingRecipe`, `BrewingRecipes`, `CauldronTier`, `BrewPotency`, `CauldronBrewing`)
- `at.koopro.wizardsandbeasts.brew.def` — JSON codecs & reload listeners (`BrewDefinition`, `BrewingRecipeDefinition`, `BrewReloadListener`, `BrewingRecipeReloadListener`)

**Key types:**

| Class | Role |
|-------|------|
| `Brew` | Record: id, displayName, color, list of `EffectSpec`, optional flavor text. `EffectSpec` carries `Holder<MobEffect>` + base duration/amplifier and produces a `MobEffectInstance` scaled by a potency multiplier at consumption time |
| `BrewingRecipe` | Record: id, list of `Ingredient(Item, count)`, required `CauldronTier`, `heatTimeTicks`, `outputBrewId`. `matches(Inventory, tier)` and `consumeFrom(Inventory)` are the two interaction primitives |
| `Brews`, `BrewingRecipes` | Static registries mirroring `Spells`; cleared and rebuilt on every datapack reload. `Brews.byId(id)` falls back to the `wizards_and_beasts:` namespace for bare ids |
| `CauldronTier` | Enum (`BRASS`, `COPPER`, `PEWTER`); `isAtLeast(tier)` lets recipes demand "pewter or better". Does **not** map to block-class hierarchy — the cauldron blocks themselves are still plain `Block::new`; tier is a recipe-side gate |
| `BrewPotency` | Wires `HerbologySkills.POTION_POTENCY` into the consumption math. `multiplierForLevel(int)` is pure (and tested); `multiplierFor(ServerPlayer)` reads the player's skill level. +10% duration per level, capped at +30% |
| `BrewItem` | The single, generic potion-bottle item (`item.wizards_and_beasts.brew`). Specific brew is identified by the `ModDataComponents.BREW_ID` data component; on use, the brew is resolved and its effects are applied with potency scaling |
| `CauldronBrewing` | `@EventBusSubscriber` on `UseItemOnBlockEvent`. **MVP brewing flow** — see below |
| `RegisterBrewsEvent` | Mod-bus event fired during `FMLCommonSetupEvent`; addons register `Brew`s and `BrewingRecipe`s here |

**Brewing flow (MVP):**

The current cauldron interaction is event-driven and atomic, not BlockEntity-backed. When a player **sneak-right-clicks any Wizards & Beasts cauldron block while holding an empty glass bottle**, `CauldronBrewing` checks (1) a heat source under the cauldron (fire, soul fire, lava, magma block, lit campfire); (2) a registered `BrewingRecipe` whose ingredients are present in the player's inventory and whose required tier ≤ the clicked cauldron's tier. If both pass, ingredients + the bottle are consumed, a `BrewItem` carrying the resulting brew id is added to the inventory, and a brewing-stand sound plays. `heatTimeTicks` from the recipe is **carried but unused** by this MVP.

**Why not a BlockEntity:** the BE-backed timed brewing cauldron (visible floating ingredients, tickable progress, particle pipeline, custom client renderer) is the right destination, but it's significantly more code than the data layer it would gate. The event-driven MVP proves the entire data → recipe → item → consumption loop in ~80 lines and lets us validate brews and recipes end-to-end today. The future BE upgrade will replace `CauldronBrewing` without touching the brewing data layer.

**Adding a new brew:**

1. Drop `data/<namespace>/wizards_and_beasts/brews/<your_brew>.json` matching [`BrewDefinition.CODEC`](src/main/java/at/koopro/wizardsandbeasts/brew/def/BrewDefinition.java) — required fields: `displayName`, `color`, `effects[]`. Each effect is `{ id (mob effect id), duration, amplifier? = 0, ambient? = false }`.
2. Drop `data/<namespace>/wizards_and_beasts/brewing_recipes/<your_recipe>.json` matching [`BrewingRecipeDefinition.CODEC`](src/main/java/at/koopro/wizardsandbeasts/brew/def/BrewingRecipeDefinition.java) — required: `ingredients[]` and `outputBrewId`. Optional: `cauldronTier` (default `brass`), `heatTimeTicks` (default 200).
3. `/reload` and brew it in-game (or restart the server).

Reference example: `wiggenweld_potion` ([brew](src/main/resources/data/wizards_and_beasts/wizards_and_beasts/brews/wiggenweld_potion.json) + [recipe](src/main/resources/data/wizards_and_beasts/wizards_and_beasts/brewing_recipes/wiggenweld_potion.json)). Addon mods register programmatically through `RegisterBrewsEvent` (mirrors `RegisterSpellsEvent`).

---

### 3.13 Skill System

A point-buy skill graph splits across five tree branches and modulates spell math (damage, cooldown) plus unlocks abilities and grants known spells. Skills are *not* required to cast — they layer bonuses on top of the spell pipeline.

**Files (`at.koopro.wizardsandbeasts.skill`):**

| Class | Role |
|-------|------|
| `Skill` | Immutable record-style class built via `Skill.builder(...)`: `id`, `displayName`, `tree` (`SkillTreeId`), `cost` (skill points), `maxLevel`, `description`, `prerequisites`, list of `SkillEffect`, `(tier, column)` UI position |
| `SkillTreeId` | Enum of trees: `SPELL_MASTERY`, `DARK_ARTS`, `WANDLORE`, `MAGIZOOLOGY`, `HERBOLOGY` |
| `SkillEffect` | Sealed interface: `LearnSpell(spellId)`, `SpellDamageBonus(spellId, percent)`, `CategoryDamageBonus(category, percent)`, `CategoryCooldownReduction(category, percent)`, `UnlockAbility(name)`, … |
| `SpellMasterySkills` / `DarkArtsSkills` / `WandloreSkills` / `MagizoologySkills` / `HerbologySkills` | One file per tree; each declares `static final Skill ...` constants registered with `SkillTrees.register(...)` |
| `SkillTrees` | Central registry (mirrors `Spells`). `init()` validates that every prerequisite id exists; `byId(...)`, `getTree(...)`, `all()`, `count()` for lookup |
| `SkillSystemAPI` | Server-side query surface used by spell executor and packets: `getDamageMultiplier(player, spell)`, `getCooldownMultiplier(player, spell)`, `hasAbility(player, name)`, plus `applyImmediateEffects(...)` to honor `LearnSpell` on unlock |
| `SkillEffectCache` | Per-player cached aggregation of unlocked `SkillEffect`s for fast repeated lookups during casting |

**Per-player state:** `PlayerSkillData` (attachment registered in [`ModAttachments`](src/main/java/at/koopro/wizardsandbeasts/registry/ModAttachments.java)) stores unlocked skill ids + level counts and skill-point balance. Sync via `SkillDataSyncS2CPacket`; client cache lives in client state holders.

**Adding a skill:** add a `static final Skill` field in the appropriate `*Skills.java` file using `Skill.builder(...)` and `SkillTrees.register(...)`, then expose it from [`SkillTrees.java`](src/main/java/at/koopro/wizardsandbeasts/skill/SkillTrees.java). Reference its `id` from any prerequisite. `SkillTrees.init()` will fail-fast at startup if the prerequisite graph is broken.

---

## 4. Key Data Flows

### A. Spell Cast Flow

```
[Client] Spell selection / loadout
       |
       ├── SpellSelectC2SPacket (slot index) ───────────────► [Server] update PlayerSpellData.activeSlot
       └── SpellAssignC2SPacket (slot + spell id) ──────────► [Server] validate + update loadout

─────────────────────────────────────────────────────────────────────────────────
[Client] Player holds wand right-click (WandItem#onUseTick)
       |
       └── server-side held loop: WandBeamChannelLogic.tick(...)
               ├── only for cast types BEAM_CHANNEL / BEAM_LETHAL
               ├── strict allowlist: Crucio + Avada spell ids
               ├── Crucio: applies target effects each held tick, strips effects on channel end
               └── Avada: attempts lethal hit; on confirmed kill:
                      ├── send AvadaBlastS2CPacket (one-shot visual to tracking players)
                      ├── completeWandCastRelease(...) (cooldown + cast count + sync)
                      └── releaseUsingItem() to end hold state

─────────────────────────────────────────────────────────────────────────────────
[Client] Player releases wand right-click (WandItem#releaseUsing)
       |
       |  SpellCastC2SPacket (no payload) ─────────────────────────► [Server]
       |
[Server] SpellCastC2SPacket#handle()
       |
       ├── verify player holds a WandItem (main or off-hand)
       ├── player.getData(SPELL_DATA) ──► PlayerSpellData
       ├── PlayerSpellData.getActiveSpellId() ──► spellId (skip if null)
       ├── Spells.byId(spellId) ──► Spell
       ├── PlayerSpellData.knowsSpell(spellId) ──► skip if false
       ├── if Config.enforceSpellRequirements:
       │       SpellRequirement.isMet(data) ──► skip + chat reason if false
       ├── PlayerSpellData.isOnCooldown(spellId, gameTime) ──► skip + chat if true
       ├── Spell#execute(serverLevel, serverPlayer, wandStack)
       │       │
       │       ├── (default) SpellExecutor.executeGeneric(...)
       │       │       ├── play cast sound
       │       │       ├── apply self-effects
       │       │       └── branch on CastType:
       │       │               PROJECTILE → spawn SpellProjectileEntity (carries spellId)
       │       │               SELF       → optional repair (e.g. Reparo)
       │       │               CONE       → AABB scan, push/damage/effect entities
       │       │               TARGETED   → ray-trace, apply effect to single target
       │       │               BEAM_*     → no immediate damage here; handled by WandBeamChannelLogic
       │       │
       │       └── (overrides) e.g. Nox#execute → playSound + removeEffect(GLOWING/NIGHT_VISION)
       │
       ├── apply SkillSystemAPI.getCooldownMultiplier ──► setCooldown(spellId, gameTime + cd)
       ├── PlayerSpellData.incrementCastCount(spellId)
       ├── SkillEvents.checkProficiencyMilestone (chat + effects on tier-up)
       └── SpellDataDeltaS2CPacket.sendTo(player, spellId, expiryTick, newCastCount)
```

### B. Marauder's Map Flow

```
[Client] Right-click with MaraudersMapItem
       |
[Server] MaraudersMapItem#use()
       |
       ├── isTrusted(player) == false ──► send chat insult, return
       |
       └── isTrusted == true
               └── MaraudersMapTracker.addPlayer(player, center, radius, dimension)
                       |
                       └── send MapOpenS2CPacket ──────────────────────► [Client]
                                                            open MaraudersMapScreen

─────────────────────────────────────────────────────────────────────────────────
Every 5 server ticks — MaraudersMapTracker.onServerTick()
       |
       └── for each active viewer UUID:
               |
               ├── look up ServerPlayer ──► null? remove from map, continue
               |
               ├── gatherEntities(level, center, radius)
               |       └── AABB scan LivingEntities, up to MAX_ENTITIES_PER_SYNC=200
               |           classify: Player → PLAYER, Monster → HOSTILE, Mob → PASSIVE
               |
               └── PacketDistributor.sendToPlayer(MapSyncPacket(entities)) ──► [Client]
                                                            MapClientHandler updates entity list
                                                            MaraudersMapScreen re-renders dots
```

### C. Broom Flight Flow

```
[Server] BroomItem#use() ──► spawn BroomEntity, player.startRiding(broomEntity)

─────────────────────────────────────────────────────────────────────────────────
Per tick while player is mounted:

[Client] BroomClientInputHandler captures key state (W/S/Space/Shift/Sprint)
       |
       |  BroomInputPacket(fwd, back, up, down, boost, yaw, pitch) ────► [Server]
       |
[Server] BroomInputPacket#handle()
       └── broomEntity.setInput(forward, backward, up, down, boosting, yaw, pitch)

[Server] BroomEntity#tick()
       |
       ├── tickMovement()
       |       ├── setYRot(inputYaw), setXRot(clamp(inputPitch, -90, 90))
       |       ├── targetSpeed = MAX_SPEED [* BOOST_MULTIPLIER if boosting]
       |       ├── currentSpeed = lerp(ACCELERATION | DECELERATION, currentSpeed, targetSpeed)
       |       ├── compute motion vector from yaw + pitch angles
       |       ├── add vertical component (Up/Down keys + WEAK_GRAVITY)
       |       ├── move(SELF, deltaMovement)
       |       └── if (horizontalCollision || verticalCollision) && speed > CRASH_SPEED_THRESHOLD
       |               └── handleCrash(impactSpeed)
       |                       ├── eject rider, deal scaled damage [0..MAX_CRASH_DAMAGE=20]
       |                       ├── apply CRASH_KNOCKBACK=0.6 to rider
       |                       ├── spawn CLOUD + POOF particles
       |                       ├── play broom_crash sound
       |                       └── dropBroomItem(), discard()
       |
       ├── updateTilt()
       |       ├── pitchTilt  ← clamp(accelDelta * 200, ±MAX_PITCH_TILT=25°), lerp 0.35
       |       ├── rollTilt   ← clamp(-turnRate * 4, ±MAX_ROLL_TILT=35°), lerp 0.35
       |       └── forwardLean ← speedRatio * MAX_FORWARD_LEAN=20°, lerp 0.35
       |
       ├── scanEntityCollisions()
       |       └── for nearby LivingEntities at speed > COLLISION_SPEED_MIN=0.3:
       |               ├── BroomEntity: halve both speeds, push apart
       |               └── LivingEntity: knockback + scaled ENTITY_HIT_DAMAGE=4, cooldown 10t
       |
       └── tickCollisionCooldowns() ──► decrement per-entity cooldown counters

─────────────────────────────────────────────────────────────────────────────────
[Client Renderer]
       BroomRenderer      ──► plays idle / fly / boost GeckoLib animation by speed + boost state
       BroomRiderRenderer ──► reads getPitchTilt(), getRollTilt(), getForwardLean()
                              adjusts rider body / arm transforms each frame
```

---

## 5. Spell Implementation Status

Java spells are concrete `Spell` subclasses under `at.koopro.wizardsandbeasts.spell.spells.*` and are registered in [`Spells.java`](src/main/java/at/koopro/wizardsandbeasts/spell/Spells.java). JSON spells are loaded on reload from `data/<namespace>/wizards_and_beasts/spells/*.json` via `SpellReloadListener`.

**Combat:** `Stupefy`, `Expelliarmus`, `Incendio`, `Diffindo`, `Bombarda`, `Confringo`, `Flipendo`, `Glacius`, `Depulso`.
**Utility:** `Lumos`, `Nox`, `Accio`, `Reparo`, `WingardiumLeviosa`, `Alohomora`, `ArrestoMomentum`.
**Defense:** `Protego`, `ExpectoPatronum`.
**Dark Arts:** `AvadaKedavra`, `Crucio`, `Imperio`.

Each subclass declares its `id`, `displayName`, `SpellCategory`, `baseCooldownTicks`, `baseDamage`, and `color` via its constructor; behavior is built in `buildProperties()` (returns a `SpellProperties`) and gated by `buildRequirement()` (returns a `SpellRequirement`). `Nox` is the canonical example of a spell that overrides `execute(...)` directly because the generic dispatcher cannot express "remove an effect."

### 5.1 What Each Spell Does (Runtime Behavior)

| Spell | Primary behavior path |
|-------|------------------------|
| `Stupefy` | Projectile bolt; applies configured on-hit damage/effects in `SpellProjectileEntity` |
| `Expelliarmus` | Projectile bolt; disarms target on hit (`SpellProjectileEntity`) |
| `Incendio` | Cone cast; applies cone damage/effects and ignite logic (`SpellExecutor.handleCone`) |
| `Diffindo` | Projectile damage bolt (`SpellProjectileEntity`) |
| `Bombarda` | Targeted explosion at target/block (`SpellExecutor.handleTargeted`) |
| `Confringo` | Projectile with ignition/explosion behavior on impact (`SpellProjectileEntity`) |
| `Flipendo` | Projectile knockback (`SpellProjectileEntity` + `SpellHelper.applyKnockback`) |
| `Glacius` | Cone cast with slowing effects (`SpellExecutor.handleCone`) |
| `Depulso` | Projectile with stronger knockback (`SpellProjectileEntity`) |
| `Lumos` | Self cast applying light-related buffs (`Spell.applySelfEffects`) |
| `Nox` | Custom execute override removing Lumos-related effects immediately |
| `Accio` | Cone cast that pulls entities toward caster (`SpellExecutor.handleCone`) |
| `Reparo` | Self cast that repairs the opposite-hand item (`SpellExecutor.handleRepair`) |
| `WingardiumLeviosa` | Targeted levitation on hit target (`SpellExecutor.handleTargeted`) |
| `Alohomora` | Targeted block interaction opening doors/trapdoors/gates (`SpellExecutor.handleAlohomora`) |
| `ArrestoMomentum` | Self cast with slow-falling style defensive utility |
| `Protego` | Self defensive buffs (resistance/absorption style effects) |
| `ExpectoPatronum` | Cone cast with damage/effects (plus undead bonus behavior) |
| `Crucio` | Held channel (`WandBeamChannelLogic`): applies target effects every tick while maintaining target lock |
| `Imperio` | Targeted cast that applies mob-control effects (`SpellExecutor.handleTargeted`) |
| `AvadaKedavra` | Held lethal channel (`WandBeamChannelLogic.handleAvada`): executes kill attempt and triggers release flow on confirmed death |

**JSON spells (`data/wizards_and_beasts/wizards_and_beasts/spells`):**
- `wizards_and_beasts:levicorpus` (targeted levitation utility)
- `wizards_and_beasts:frigora` (self mobility/survival utility)
- `wizards_and_beasts:episkey` (self defensive/healing utility)

### 5.1.1 Overhaul Tuning Snapshot

- `Bombarda`: lower direct burst and non-griefing explosion profile (higher cooldown, lower power, no block breaking).
- `Crucio`: weaker tick-level oppression (higher cooldown, reduced debuff intensity/duration).
- `Imperio`: shorter control window and slightly tighter range, with higher cooldown.
- `AvadaKedavra`: stricter mastery gate (`Crucio` must be `MASTERED`).
- `Alohomora`: reduced spam by raising cooldown.
- `Stupefy`: reduced debuff amplifier to improve PvP fairness.
- `Expelliarmus` and `ExpectoPatronum`: small pacing buffs to keep utility/defense competitive.
- JSON balance alignment: `levicorpus`/`frigora` cooldown adjustments and `episkey` category fixed to valid enum.

### 5.2 Avada Blast Timing Rule

- The persistent held-beam renderer is intentionally **not** used for Avada.
- Avada visual blast is emitted only via `AvadaBlastS2CPacket`, sent from `WandBeamChannelLogic.handleAvada` after the server confirms the target died.
- Result: no Avada blast while simply holding or missing; one blast on confirmed lethal hit.

**Adding a new spell:** see the Spell System section (§3.2) for the full pattern.

Per-spell tunables (cooldown, base damage, color, prerequisite) live in the source — refer to each `spells/*.java` file rather than maintaining a duplicate table here.

---

## 6. Dependencies

| Dependency | Version | Role |
|-----------|---------|------|
| **NeoForge** | 21.11.38-beta | Mod loader, event bus, `DeferredRegister`, data components, attachments, `PayloadRegistrar` networking, `ModConfigSpec` |
| **GeckoLib** | 5.4.5 | Animated entity and item rendering; used by `BroomEntity`, `GoblinTellerEntity`, `NifflerEntity`, and the Marauder's Map item model |
| **Minecraft** | 1.21.11 | Base game |
| **Java** | 21 | Language runtime (toolchain via Eclipse Adoptium) |
