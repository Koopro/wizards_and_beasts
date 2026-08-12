# Creatures

## 1. Phase-0 decision (proceed)

The in-progress Niffler and the existing 8 beast entities use a pattern that is **fully compatible** with the data-driven `CreatureDefinition` registry. No stop-and-report required. Niffler is left untouched (it has bespoke AI + its own class; it simply keeps its existing `EntityType`/renderer and is NOT migrated to the generic system — it is one of the 9 entries already having an entity, so it is out of the missing manifest).

## 2. Existing patterns

### Bestiary registry & data (roster source of truth)
- Record: `bestiary/BestiaryEntry.java` — `RecordCodecBuilder` codec. Optional field `entityType` (`Identifier`) marks "has a living entity".
- Registry: `bestiary/BestiaryEntryRegistry.java`. Loader: `bestiary/BestiaryEntryLoader.java` (`SimpleJsonResourceReloadListener`, dir `bestiary/entries`).
- On-disk: `data/wizards_and_beasts/bestiary/entries/*.json` — **63 entries**.

### GeckoLib 5.4.5 usage (exact API in-repo)
- Entity base: `entity/GeoEntityBase` extends `PathfinderMob implements GeoEntity`, holds `GeckoLibUtil.createInstanceCache(this)`.
- Simple renderer factory: `client/GeoRendererHelper.simple(modelName)` → `new GeoEntityRenderer(ctx, new DefaultedEntityGeoModel<>(Identifier(MODID, modelName)))`. **Proven** for niffler/mooncalf/streeler/etc.
- Custom `GeoModel` signature (from `client/wand/WandRenderer.WandModel`):
  - `Identifier getModelResource(GeoRenderState renderState)` → returns sub-path e.g. `"item/wand"` (base adds `geckolib/models/` + `.geo.json`).
  - `Identifier getTextureResource(GeoRenderState renderState)` → full `"textures/item/wand.png"`.
  - `Identifier getAnimationResource(T animatable)` → animatable-keyed.
- Render-state/`DataTicket` pattern (from `client/entity/DementorRenderer`): `renderState.addGeckolibData(TICKET, value)` in `addRenderData`; read via `info.getOrDefaultGeckolibData(...)` in `adjustModelBonesForRender`. No entity access at render time.
- Asset path convention (confirmed on-disk):
  - model: `assets/wizards_and_beasts/geckolib/models/entity/<id>.geo.json`, geometry identifier `geometry.<id>`, format `1.12.0`.
  - animation: `assets/wizards_and_beasts/geckolib/animations/entity/<id>.animation.json`, format `1.8.0`, anims `animation.<id>.idle` / `.walk` / `.fly` / `.swim`.
  - texture: `assets/wizards_and_beasts/textures/entity/<id>.png`.
- Animation controller helper: `util/AnimHelper.movementController(name, transition, idle, walk)`, `.idleController(...)`, `.loop(name, anim)`.

### Registration patterns
- Entity types: `registry/ModEntities.ENTITY_TYPES` (`DeferredRegister<EntityType<?>>`). Helper `registry/EntityHelper.register(reg, name, factory, category, w, h)`.
- Attributes: `registry/EntityAttributeBindings.queue(holder, supplier)` co-located at registration; `registerAll(EntityAttributeCreationEvent)` wired on mod bus in `WizardsAndBeastsMod`.
- Spawn eggs: `registry/MiscItemRegistry` — `ModItems.ITEMS.registerItem("<id>_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(ENTITY.get())))`.
- Reload listeners: `WizardsAndBeastsMod` `AddServerReloadListenersEvent` block. Mirror `BroomDefinitionLoader`/`BroomDefinitionRegistry`.

### ModuleManager
- `module/Module.CREATURES` **already exists** (state `DISABLED`). Per §4 the module gates *access*, not registration. Set to `PREVIEW` so summon/spawn-egg work during verification. Content always registered.

### Command tree
- Root `/wandb` (+ deprecated alias `/wizardsandbeasts`) built in `command/WandbCommands.buildRoot`. Subcommands attach via `.then(XxxCommands.register())`. Permission gate: `WizardsAndBeastsCommandPermissions.GAMEMASTER` (level-2 / gamemaster). New `CreatureCommands.register()` attaches here.

### Logging
- Build history now consolidated in `WORKLOG.md` (audit findings + resolution deltas per build pass).

## 3. Architecture as built (reconciliation with MC constraints)

`EntityType` objects + their attribute suppliers + their hitbox sizes are **frozen registry data** that must be created at mod-init, BEFORE any datapack reload. A pure "add a creature via datapack only" is therefore impossible in Minecraft. Resolution:

- **Java manifest** (`registry/ModCreatures.MANIFEST`) is the registration-time source for `{id, locomotion, width, height}`. It registers one `EntityType` per creature (generic class chosen by locomotion) + one spawn egg + queues a per-locomotion baseline `AttributeSupplier`.
- **`CreatureDefinition` JSON** (`data/wizards_and_beasts/creatures/<id>.json`) is the runtime source of truth for attributes (applied over the baseline on spawn, server-side), body-plan, temperament, and asset ids. Both the Java manifest and the JSON are emitted from one generator so they cannot diverge.
- Generic entity learns its id at runtime via `BuiltInRegistries.ENTITY_TYPE.getKey(getType())` → looks up its `CreatureDefinition`. **No bespoke per-creature classes.**
- Rendering uses the **proven** `GeoRendererHelper.simple(id)` factory per creature (model/texture/anim all keyed by `<id>`). This is a justified deviation from "one renderer per locomotion": it reuses the repo's working renderer path and avoids re-deriving `GeoModel` path prefixes (a §9 render risk). No new renderer classes; renderer *instances* come from the shared factory. Logged as POLISH.

## 4. Body-plan taxonomy applied (per id)

Locomotion classes: `GROUND`, `FLYING`, `AQUATIC`, `SESSILE`. Body-plan is recorded in the def for the real-art swap contract.

## 5. Missing manifest (53 creatures)

All 62 bestiary entries minus the 9 with an `entityType` (augurey, bowtruckle, cornish_pixie, dementor, mooncalf, niffler, phoenix, streeler, thestral). The remaining 54 are built by this run. Per-creature body-plan/locomotion mapping lives in the generator (`tools/creature_gen.py`) and is echoed into each `CreatureDefinition` JSON.

## 6. Roster (table: id / bodyPlan / locomotion / size / HP / status)

| id | bodyPlan | locomotion | size (w×h) | HP | status |
|----|----------|------------|-----------|----|--------|
| abraxan | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| acromantula | ARTHROPOD_MULTILEG | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| aethonan | WINGED_QUADRUPED | FLYING | 1.7x1.9 | 40 | PASS (placeholder) |
| antipodean_opaleye | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| ashwinder | SERPENTINE | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| basilisk | SERPENTINE | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |
| billywig | INSECTOID_FLYER | FLYING | 0.45x0.45 | 4 | PASS (placeholder) |
| blast_ended_skrewt | ARTHROPOD_MULTILEG | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| boggart | BLOB_SPHERE | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| centaur | QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| chimaera | QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| chinese_fireball | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| clabbert | BIPED_HUMANOID | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| crup | QUADRUPED | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| demiguise | BIPED_HUMANOID | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| diricawl | AVIAN | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| doxy | INSECTOID_FLYER | FLYING | 0.45x0.45 | 4 | PASS (placeholder) |
| erumpent | QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| flobberworm | WORM_LARVA | GROUND | 0.45x0.45 | 4 | PASS (placeholder) |
| fwooper | AVIAN | FLYING | 0.65x0.75 | 8 | PASS (placeholder) |
| giant | LARGE_HUMANOID | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |
| graphorn | QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| grindylow | AQUATIC | AQUATIC | 0.65x0.75 | 8 | PASS (placeholder) |
| hebridean_black | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| hippogriff | WINGED_QUADRUPED | FLYING | 1.7x1.9 | 40 | PASS (placeholder) |
| hungarian_horntail | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| jobberknoll | AVIAN | FLYING | 0.45x0.45 | 4 | PASS (placeholder) |
| kelpie | AQUATIC | AQUATIC | 1.7x1.9 | 40 | PASS (placeholder) |
| kneazle | QUADRUPED | GROUND | 0.65x0.75 | 8 | PASS (placeholder) |
| lethifold | BLOB_SPHERE | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| maledictus | BIPED_HUMANOID | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| manticore | WINGED_QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| merperson | AQUATIC | AQUATIC | 0.95x1.25 | 16 | PASS (placeholder) |
| norwegian_ridgeback | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| nundu | QUADRUPED | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |
| obscurus | BLOB_SPHERE | FLYING | 0.95x1.25 | 16 | PASS (placeholder, empty fly clip) |
| occamy | SERPENTINE | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| peruvian_vipertooth | WINGED_QUADRUPED | FLYING | 1.7x1.9 | 40 | PASS (placeholder) |
| plimpy | AQUATIC | AQUATIC | 0.65x0.75 | 8 | PASS (placeholder) |
| qilin | QUADRUPED | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| reem | QUADRUPED | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |
| romanian_longhorn | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| runespoor | SERPENTINE | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| sphinx | QUADRUPED | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| swedish_short_snout | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| swooping_evil | INSECTOID_FLYER | FLYING | 0.95x1.25 | 16 | PASS (placeholder) |
| thunderbird | AVIAN | FLYING | 1.7x1.9 | 40 | PASS (placeholder) |
| troll | LARGE_HUMANOID | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |
| ukrainian_ironbelly | WINGED_QUADRUPED | FLYING | 2.7x2.9 | 90 | PASS (placeholder) |
| unicorn | QUADRUPED | GROUND | 0.95x1.25 | 16 | PASS (placeholder) |
| werewolf | BIPED_HUMANOID | GROUND | 1.7x1.9 | 40 | PASS (placeholder) |
| zouwu | QUADRUPED | GROUND | 2.7x2.9 | 90 | PASS (placeholder) |

## 7. Build deltas (Batch 1 → Batch 2 → AI/Trait → Carry-Theft)

### Creature Build Pass (2026-06-18) — 53 placeholder creatures
**New data-driven creature system.** 53 bestiary entries lacking an entity are now registered, summonable, GeckoLib-rendered placeholder mobs. New: `creature/CreatureDefinition` (codec record), `CreatureDefinitionRegistry`, `CreatureDefinitionLoader` (reload listener, dir `creatures`), `entity/creature/Generic{Ground,Flying,Aquatic,Sessile}BeastEntity` (+ `GenericBeastEntity` base), `registry/ModCreatures` (53-entry MANIFEST → EntityType + baseline attributes + spawn egg per id), `creature/command/CreatureCommands` (`/wandb creature summon|list`). `Module.CREATURES` flipped `DISABLED`→`PREVIEW`.

**Behavioral note:** existing entities/spells/brooms unaffected — purely additive. The 9 entries that already had entities (incl. in-progress Niffler) are untouched. Per-creature runtime attributes load from `data/wizards_and_beasts/creatures/<id>.json` and are applied over a per-locomotion baseline on spawn (server-side); `EntityType` hitbox size is registry-frozen from `ModCreatures.MANIFEST`.

**Verification (Creature Build)**
- `./gradlew compileJava` clean; `./gradlew build -x test` BUILD SUCCESSFUL (processResources/lang/jar ok).
- All 53 creature/geo/animation JSON validate; enum values match `BodyPlan`/`Locomotion`/`Temperament`.
- In-game (not run here — dev client session required): `/wandb creature summon <id>` then observe idle animation. Render path is byte-identical to the shipping `GeoRendererHelper.simple` pipeline.

### Creature Build Batch 2 (2026-06-18) — 33 NEW canonical beasts
33 NEW canonical beasts added as full bestiary entries + generic entities (knarl, griffin, fairy, puffskein, pygmy_puff, chizpufle, horklump, red_cap, erkling, leprechaun, wampus_cat, salamander, murtlap, moke, jarvey, kappa, gnome, hippocampus, sea_serpent, fire_crab, imp, porlock, nogtail, dugbog, mackled_malaclaw, ramora, shrake, tebo, hodag, snallygaster, glumbumble, pogrebin, quintaped).

ModCreatures roster 53→86; bestiary entries 62→95. Placeholder lore lang + bestiary icon/silhouette. Build SUCCESSFUL. First SESSILE-locomotion creature (horklump) now exercises `GenericSessileBeastEntity`.

### Creature AI + Trait Foundation (2026-06-18)
Data-driven behaviour layer for all 86 generic creatures. New `creature/Trait` enum (FEARFUL, PACK, FIRE_IMMUNE, FIRE_ATTACK, POISON_ATTACK, PETRIFY, CHARGE, KNOCKBACK, THIEF, AMPHIBIOUS, REGEN, EXPLODE_ON_DEATH). CreatureDefinition gains `temperament` (already), `attackDamage`, `traits[]`.

GenericBeastEntity now wires goals by temperament (PASSIVE flee / NEUTRAL retaliate / HOSTILE hunt), applies ATTACK_DAMAGE on spawn, and interprets traits via fireImmune()/doHurtTarget()/tick()/die() (on-hit ignite, poison, petrify-lite slow+blind, knockback, charge, item-theft; passive regen; death explosion). Subclasses now supply only movement goals (addMovementGoals); base owns combat.

ModCreatures baseline adds ATTACK_DAMAGE/ATTACK_KNOCKBACK. Behaviour assigned per creature by tools/creature_behavior.py (category defaults + per-id overrides). Build -x test SUCCESSFUL. Signature one-offs (true dragon fire-breath, basilisk death-gaze, niffler-grade theft) deferred.

### Creature Carry-Theft + Real Lore (2026-06-22)
THIEF trait upgraded to niffler-grade: GenericBeastEntity now pockets stolen stacks (up to 8) into a carried list, persists it via ValueOutput/ValueInput (ItemStack.CODEC.listOf, key "CarriedLoot"), and drops the loot on death (jarvey/leprechaun carry then). Real bestiary lore written for all 33 new beasts (tools/creature_lore.py) replacing the placeholder strings — 0 placeholder lore left. Build -x test SUCCESSFUL; gametest server loads 86 defs clean.

## 8. Dragon Fire/Venom Kit + Ice-and-Fire ground scorch

The ten dragon breeds graduate from generic flyers to a dedicated breed-parameterised `DragonEntity` (one class, ten `CreatureDefinition` variants — canonical "one entity, not ten classes").

New code:
- `creature/DragonTraits.java` — nested optional `dragon` sub-codec on `CreatureDefinition` (fire_range, fire_color, flame_shape, block_effect, bite_venom, rideable [data-only], scale).
- `entity/creature/DragonEntity.java` — extends `GenericFlyingBeastEntity`; injects breath goal via `addMovementGoals()` (the only hook off the `final registerGoals`), melee venom via `doHurtTarget`, GeckoLib triggerable `breath`/`bite` controller.
- `entity/creature/ai/DragonBreathGoal.java` — server-authoritative cone (entities via cone-sweep, blocks via sampled rays), IGNITE/ASH, module-gated on `Module.CREATURES`, tinted particles + anim trigger.
- `client/entity/DragonRenderer.java` — render-state scale + fire_color DataTickets, no live-entity access in bone methods.
- `data/.../tags/block/dragon_ash_combustible.json` — ASH target tag (data, no block registration).

Breed values (per agent prompt §4) injected by `tools/dragon_traits_apply.py`; breath/bite anim clips by `tools/dragon_anims_apply.py`. Canon flame colour only for Opaleye/Short-Snout/Fireball; every other colour and all fire_range figures are design-extrapolation (flagged in each JSON's `_dragon_note`).

Verification: `./gradlew compileJava` and `./gradlew build -x test` both BUILD SUCCESSFUL. Live summon + forced-breath smoke test on a dev client is the single open item (env has no client).

### Dragon breath — Ice-and-Fire ground scorch (2026-06-24)
`DragonBreathGoal` block effect reworked: IGNITE no longer gated on `BlockState.isFlammable` (that left natural terrain untouched — only the victim caught fire). Breath now carpets vanilla fire onto ANY solid top surface across a splash footprint at the cone impact (`scorchArea`/`igniteColumn`), deduped per breath via a shared `Set<BlockPos>`. Footprint radius by flame_shape (JET 1.0 / STREAM 1.5 / BURST 2.5); rays 12→18; ASH mirrors the same footprint (`ashColumn`). Particle jet densified (per-half-block sampling, downrange spread, periodic LAVA). LIMITATION: placed fire is vanilla, so on non-flammable ground it self-extinguishes after a few seconds (no lingering char) — a persistent dragon-fire block is deferred (block registration, out of scope). compileJava SUCCESSFUL.

## 9. Real-art swap contract (per body-plan)

A real Blockbench model replaces a placeholder by **changing only asset files** — no code. For creature `<id>`:
- Model: `assets/wizards_and_beasts/geckolib/models/entity/<id>.geo.json`, geometry identifier **`geometry.<id>`**, texture size 64×64.
- Texture: `assets/wizards_and_beasts/textures/entity/<id>.png`.
- Animation: `assets/wizards_and_beasts/geckolib/animations/entity/<id>.animation.json` with anims named **`animation.<id>.idle`** plus the locomotion clip: `…walk` (GROUND), `…fly` (FLYING), `…swim` (AQUATIC); SESSILE = idle only.

Bone-name contract (snake_case, root bone `root` at Y=0; keep these names so the controllers/anim keep working):
- QUADRUPED / WINGED_QUADRUPED: `body`, `head`, `foreleg_left`, `foreleg_right`, `hindleg_left`, `hindleg_right`, `tail`; winged adds `wing_left`, `wing_right` (pivot at shoulder).
- BIPED_HUMANOID / LARGE_HUMANOID: `body`, `head`, `arm_left`, `arm_right`, `leg_left`, `leg_right`.
- AVIAN: `body`, `head`, `wing_left`, `wing_right`, `leg_left`, `leg_right`, `tail`.
- INSECTOID_FLYER: `body`, `head`, `wing_left`, `wing_right`, `leg_left`, `leg_right`.
- SERPENTINE: `head`, `seg_01`…`seg_06`. WORM_LARVA: `seg_01`…`seg_06`.
- BLOB_SPHERE: `body`, `head`.
- ARTHROPOD_MULTILEG: `body`, `head`, `leg_left_1..3`, `leg_right_1..3`.
- AQUATIC: `body`, `head`, `fin_left`, `fin_right`, `tail`.
- SESSILE: `body`, `bristle_01`…`bristle_04`.

The runtime attribute/size values live in `data/wizards_and_beasts/creatures/<id>.json` (`CreatureDefinition`); editing health/speed there is hot-reloadable (`/reload`). Hitbox size is registry-frozen in `registry/ModCreatures.MANIFEST` (Java) — changing it requires a restart.

## 10. Abilities framework

### 10.1 Common abilities (parameterized, reusable)

| id | hooks | key params |
|----|-------|-----------|
| `fire_affinity` (shipped) | tick/onHurt/goal | fire dwell |
| `water_affinity` | tick + SeekWaterGoal | breatheUnderwater, regenInWater, requiresWater, dryGraceTicks, dryDamage, seekWaterWhenDry, slowOnLand, bubbleParticles |
| `status_on_hit` | onMeleeContact | effects[] (effect/amp/duration) |
| `enrage` | onHurt + tick | healthFraction, speedAmplifier, strengthAmplifier, durationTicks |
| `thorns` | onHurt | reflectFraction, attackerEffect? |
| `blink_away` | onHurt | triggerHealthFraction, range, cooldownTicks |
| `heal_aura` | tick | healPerSecond, radius, healAllies, cleanseSelf |
| `dread_aura` | tick | radius, effects[] |
| `bioluminescence` | tick | particle, interval, glowSelf |
| `life_leech` | onMeleeContact | healOnHit |
| `constrict` | onMeleeContact | holdTicks, pullStrength |
| `camouflage` | tick | revealOnAttackTicks (invisible until it attacks/hurt) |
| `leap` | LeapAtTargetGoal | leapVelocity |
| `ranged_hex` | RangedHexGoal | range, damage, effect?, cone, cooldownTicks, particle |
| `web_snare` | WebSnareGoal | range, slowAmplifier, durationTicks, cooldownTicks |

### 10.2 Signature abilities (bespoke)

- `nundu_pestilence` — AoE disease cloud (poison+wither+hunger) ahead, lingering particles, cooldown. (nundu)
- `lethifold_smother` — close-range engulf at low light: darkness/blindness/slow + drains air & heals self. (lethifold)
- `boggart_dread` — invisible until a player looks within range, then reveals + strong fear bundle. (boggart)
- `thunderbird_storm` — when threatened: thunderstorm + lightning near attackers, cooldown. (thunderbird)
- `fwooper_song` — AoE escalating madness (nausea→weakness→mining-fatigue→damage). (fwooper)

### 10.3 Per-creature assignment (73 generic)

abraxan: enrage · acromantula: web_snare, leap · aethonan: enrage · basilisk: constrict (DEATH_GAZE trait kept) ·
billywig: status_on_hit(levitation+nausea), bioluminescence · blast_ended_skrewt: ranged_hex(fire), thorns ·
boggart: boggart_dread · centaur: ranged_hex(arrow) · chimaera: enrage, status_on_hit(wither) ·
chizpurfle: life_leech, status_on_hit(weakness) · clabbert: bioluminescence(glow), blink_away · crup: leap ·
demiguise: camouflage, blink_away · diricawl: blink_away · doxy: blink_away · dugbog: water_affinity, leap, status_on_hit ·
erkling: dread_aura, status_on_hit(nausea) · erumpent: enrage · fairy: bioluminescence(glow) · flobberworm: thorns(slow) ·
fwooper: fwooper_song, bioluminescence · giant: enrage, ranged_hex(rock) · glumbumble: dread_aura, bioluminescence ·
gnome: blink_away · graphorn: enrage, thorns · griffin: enrage · grindylow: water_affinity, constrict ·
hippocampus: water_affinity · hippogriff: enrage · hodag: leap, enrage · horklump: thorns ·
imp: status_on_hit(slow), blink_away · jarvey: blink_away (THIEF trait kept) · jobberknoll: bioluminescence ·
kappa: water_affinity, life_leech, status_on_hit(weakness) · kelpie: water_affinity, constrict, status_on_hit ·
knarl: thorns · kneazle: leap, status_on_hit(weakness) · leprechaun: blink_away, bioluminescence (THIEF trait) ·
lethifold: lethifold_smother · mackled_malaclaw: status_on_hit(unluck+weakness) · maledictus: constrict, enrage ·
manticore: status_on_hit(wither), leap, enrage · merperson: water_affinity, ranged_hex(spear) · moke: camouflage, blink_away ·
murtlap: heal_aura(self), thorns · nogtail: blink_away, status_on_hit · nundu: nundu_pestilence, enrage, dread_aura ·
obscurus: dread_aura, ranged_hex(dark), enrage · occamy: constrict, enrage · plimpy: water_affinity, bioluminescence ·
pogrebin: dread_aura, camouflage · porlock: blink_away, heal_aura(allies) · puffskein: heal_aura(self), bioluminescence ·
pygmy_puff: bioluminescence · qilin: heal_aura(allies+cleanse), blink_away · quintaped: leap, enrage ·
ramora: water_affinity · red_cap: status_on_hit(wither), enrage · reem: enrage, heal_aura(self) ·
runespoor: constrict, ranged_hex(venom) · sea_serpent: water_affinity, constrict, enrage · shrake: water_affinity, thorns ·
snallygaster: ranged_hex(spit), leap · sphinx: status_on_hit(blindness), dread_aura, heal_aura(self) ·
swooping_evil: leap, life_leech, status_on_hit(weakness+nausea) · tebo: camouflage, enrage · thunderbird: thunderbird_storm, enrage ·
troll: heal_aura(self), enray · unicorn: heal_aura(allies+cleanse), blink_away, thorns(bad-omen attacker) ·
wampus_cat: leap, status_on_hit(weakness), enrage · werewolf: leap, status_on_hit(hunger), enrage ·
zouwu: leap, blink_away, enrage

10 dragons: each gains `enrage` (additive; DragonTraits breath kit untouched).

## 11. Canon-gap fills + onDeath hook (2026-06-24)

Closed the remaining canon signature gaps. New `onDeath(entity)` hook on `CreatureAbility` (default no-op), dispatched server-side from `GenericBeastEntity.die()` (module-gated). 4 new variants: `spell_resist` (Graphorn — heals back a fraction of MAGIC/INDIRECT_MAGIC damage, the reduction matching the mod's `damageSources().magic()` spells; reusable for any spell-resistant beast), `explosive_horn` (Erumpent — contained `ExplosionInteraction.NONE` burst on a gored melee victim, distinct from its on-death EXPLODE), `death_cry` (Jobberknoll — death burst: scream sound + Glowing on nearby living), `anchor` (Ramora — per-sec heavy Slowness on nearby in-water creatures, pinning them). Assigned: graphorn +spell_resist, erumpent +explosive_horn, jobberknoll +death_cry, ramora +anchor. CreatureAbility variant count now 24 (20 common-ish + 9 signatures across the two passes). compileJava + full :test SUCCESSFUL.

## 12. Cleared the deferred creature-ability backlog (2026-06-24)

Built every remaining deferred item. 4 new variants (28 total) + a real projectile entity + a render-state size-shift + a new `onDeath`-style render path:

- **occamy_choranaptyxis** (Occamy size-shift, render-only): `GenericBeastEntity` gains a synced `DATA_RENDER_SCALE` float (default 1.0, hitbox stays registry-frozen). New `ScaledBeastRenderer` (mirrors `DragonRenderer`'s render-state DataTicket → `root` bone scale, no live-entity access) now renders all non-dragon creatures (1.0 = identical to the old `GeoRendererHelper.simple`). The ability eases scale toward max when roused + roomy, min when calm/confined (ceiling headroom proxy).
- **flame_burst** (Fire Crab, +`FlameBurstGoal`) and **ember_trail** (Ashwinder): the "fire emission" follow-up the fire pass deferred. Burst = AoE ignite + small fire damage on cooldown; trail = small per-tick chance to lay a vanilla fire block in the Ashwinder's wake (air-on-solid only). No new items.
- **Real ranged projectiles**: `ranged_hex` no longer hitscans. New `BeastHexProjectile` (`ThrowableItemProjectile`, registered `beast_hex_projectile`, rendered via vanilla `ThrownItemRenderer` as a flung magma cream — the `WizardingThrownEntity` pattern) carries server-side damage + optional effect + particle trail; `RangedHexGoal` now launches it (dodgeable, travels, LOS) with a throw sound.
- **danger_sense** (Kneazle): periodically outlines nearby `Enemy` mobs with Glowing (its sixth sense for threats).

Assigned: occamy +occamy_choranaptyxis, fire_crab +flame_burst, ashwinder +ember_trail, kneazle +danger_sense. Codec test extended for the 4 new dispatch keys; the FireAffinity assertions now tolerate multi-ability creatures. compileJava + full :test SUCCESSFUL.

NOT done (clear rationale): new canon creatures — already exist as dedicated `entity.beast` entities (augurey/mooncalf/streeler/phoenix/bowtruckle/cornish_pixie/thestral), not the data-driven roster, so nothing to add there. Niffler THIEF→ability-layer refactor left untouched (prior scope). Render-time model tints are art-swap work with no logic to write. Ashwinder igniting-egg item deferred to a loot/item pass (no new items this scope).

## 13. Lessons carried forward

- FLYING + a wingless body-plan (BLOB_SPHERE: obscurus) produces an empty `…fly` clip — valid, renders idle only. Acceptable for placeholder.
- `Level.isClientSide` is private; use `level.isClientSide()`.
- Generic entity learns its id via `BuiltInRegistries.ENTITY_TYPE.getKey(getType())` — no per-creature class needed.