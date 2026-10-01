# Creature Lab and Entity Viewer

Phase 4 of the Control Center, built on `ADMIN_FRAMEWORK.md` and following the patterns of `ADMIN_MAGIC.md` and
`ADMIN_HERITAGE.md`. It adds the **Creatures** section and a full-screen **Entity Viewer**:

- a browser over the whole roster;
- a page per creature with its real stats, spawns, behaviour, abilities and variants;
- the per-creature rules this mod can actually enforce;
- server-placed test spawns with cleanup.

It adds no second entity registration, no second renderer and no preview models. It reads the stores the game
reads, and the viewer draws each creature with the renderer the game uses.

---

## 1. What already existed, and where admin hooks in

| System | Where | Admin hook |
|---|---|---|
| Roster | `ModCreatures.MANIFEST` (101 generic) + `BESPOKE_IDS` (10 hand-written) = `ROSTER` | The browser lists `ROSTER`. |
| Stats | `CreatureDefinition` datapack (`data/*/creatures/*.json`), applied by `GenericBeastEntity#applyDefinition` on spawn over the locomotion baseline | Shown exactly as applied. Anything the definition does not set (armour, knockback resistance, and every stat of a bespoke creature) comes from the type's registered attribute supplier. |
| Classification, icon | `BestiaryEntry` (category, `mmRating`: absent means no Ministry grade, `iconTexture`) | Category filter, magical/non-magical, icon. |
| Taming, breeding | `BondProfile` datapack (`creature_bonds`), `breeding` block | Tameable / breedable flags. |
| Natural spawns | `neoforge:add_spawns` biome modifiers (weight, group, biomes) baked into biomes at world load, plus `BeastSpawnHandler` placement predicates (module, `creatureNaturalSpawns`, light, ground, per-creature conditions) | Entries read from the server's biome-modifier registry. Conditions are recorded beside each predicate (`SpawnConditionNotes`). There is a per-creature on/off rule. |
| Variants | Coat enums on `HippogriffEntity`, `KelpieEntity`, `NifflerEntity` (synced, saved, rolled in `finalizeSpawn`) | `CreatureVariant` / `VariantHolder` read them, and one weighted roll honours the rules. |
| Rendering | GeckoLib 5.4.5 `GeoEntityRenderer`s per creature | The viewer extracts a render state from the real renderer. |
| Debug spawning | `/wandb creature summon | lineup` | Untouched. The lab adds validated, cleaned-up test spawns. |

## 2. Layers

```
creature/variant/CreatureVariant        common reading of the coat enums (id, texture, authored weight)
creature/variant/VariantHolder          entities that carry one (hippogriff, kelpie, niffler)
creature/variant/CreatureVariants       creature id → its enum values; the one weighted roll
creature/rules/CreatureRule             natural spawn, variant enabled, variant weight (all optional)
creature/rules/CreatureRules            server-only live state
creature/rules/CreatureRulesData        world SavedData
creature/rules/CreatureRulesService     only writer; the natural-spawn gate (MobSpawnEvent.SpawnPlacementCheck)
creature/rules/SpawnConditionNotes      what each placement predicate tests, written beside it

admin/creature/CreatureRuleSettings     creature/<id>/natural_spawn, creature/<id>/variant/<v>/enabled|weight
admin/creature/CreatureAdminService     list, detail, biome-modifier spawn entries
admin/creature/CreatureTestSpawns       server-placed test spawns, cleanup, session discard
admin/command/AdminCreatureCommands     /wandb admin creature …
network/admin/AdminCreaturePayloads + AdminCreatureNetworkService

client/admin/screen/CreaturesPanel      tabs: Creatures | Rules
client/admin/screen/CreatureBrowserPanel
client/admin/viewer/PreviewEntity       client-only instance + GeckoLib control
client/admin/viewer/EntityViewerScreen  full-screen viewer
```

`CreatureRules` has no client layer and no sync payload. Natural spawning and the variant roll both happen on the
server, and the panel sees the rules as ordinary setting values.

## 3. Rules

| Setting id | Default | Enforced at |
|---|---|---|
| `creature/<id>/natural_spawn` (every roster creature) | on | `CreatureRulesService` fails `SpawnPlacementCheck` for `NATURAL` and `CHUNK_GENERATION` only |
| `creature/<id>/variant/<v>/enabled` (every variant) | on | `CreatureVariants.roll` in the entity's `finalizeSpawn` |
| `creature/<id>/variant/<v>/weight` (1–1000) | the authored weight (Niffler 70/12/12/6, others 1) | same |

Global rules on the Rules tab: `module_creatures` (the Creatures module; warns when opened) and
`creature_natural_spawns`, the existing NONE / ALL / ALPHA_ONLY roster switch, moved here from Game Rules.

Guarantees:

- **The natural-spawn gate is a single listener.** It covers every roster creature, including the Niffler, whose
  predicate lives elsewhere, and creatures with no predicate at all. It only ever answers "fail", and only for this
  mod's creatures. Spawn eggs, commands, structures, breeding and test spawns are untouched. It uses NeoForge's
  documented runtime hook, not a mixin.
- **At least one variant stays enabled.** Disabling the last one is refused as `CONFLICT`
  (`conflict.last_enabled_variant`).
- **No behaviour change without a rule.** With no rules set, the roll is exactly the roll each creature had
  before.
- **Overrides only.** An override equal to the shipped value is removed, not stored.

### Shown, not overridden

Spawn **weight, group size and biomes** are `add_spawns` biome-modifier data. NeoForge applies it to the biomes
when the world loads, so it cannot safely change under a running world. The lab shows these entries as the server
loaded them, including the source file, and says to change them with a datapack. A runtime layer that rewrote
biome spawn lists would be a second spawning system; the per-creature switch is the runtime rule.

**Required conditions** (light, ground, full moon, colony cap, water, stonework, the Chamber) are code
predicates. They are recorded in `SpawnConditionNotes` next to each predicate and listed on the page; they cannot
be edited.

### Not built, because nothing in the mod does it

- **A per-creature "enabled" separate from spawning.** The only switch for the whole roster is the Creatures
  module.
- **Editable attributes.** They are datapack definitions, so change them with a datapack.
- **Data-defined variant sets.** The variant set is the entity's enum. Its ordinal is the saved and synced
  identity, and the renderer picks the texture from it. A creature gains variants by giving its entity a
  `CreatureVariant` enum, a `VariantHolder` implementation, a `CreatureVariants.roll` call in `finalizeSpawn` and an
  entry in `CreatureVariants`. Everything that changes at runtime (which variants are rolled, and their weights) is
  rule data. The lab shows weights as rarity: each variant's share of the roll among the enabled ones.

## 4. The browser and page

- **Filters.** Search by name or id; filter by any bestiary category present; filter by one trait at a time
  (magical, non-magical, hostile, passive, flying, tameable, breedable, spawns naturally, has variants).
- **Row badges.** ✕ means natural spawning is off; ◇n means the creature has n variants.
- **Page.**
  - Header: icon, name and badges.
  - The natural-spawn rule.
  - Viewer and test spawn.
  - Base attributes: health, attack, speed, flying speed, follow range, armour, knockback resistance, scale,
    hitbox, and where the values came from.
  - Natural spawning: each entry with its biomes, weight, group and source, followed by the placement
    conditions.
  - Behaviour: mob category, temperament, locomotion, body plan, traits, behaviour profile, extra clips, and the
    model, texture and animation ids. Bespoke creatures say so.
  - Abilities (`CreatureAbility` types).
  - Variants: name, share of spawns, authored weight, texture, a Preview button, and the enabled and weight rows.

## 5. Test spawns

- **Permission.** Spawning needs the **world** capability; reading the roster needs **content**.
- **The client never names a position.** It sends a creature id, an optional variant and a no-AI switch. The
  server:
  - checks the id is on the roster;
  - checks the variant is one of that creature's variants;
  - caps each admin at 8 live test creatures;
  - chooses a spot 2–6 blocks ahead: in the admin's own level, loaded, on a sturdy top face, with room for the
    hitbox, dry unless the creature is aquatic, and in line of sight. The search stops at the first wall, so it
    never places a creature behind one.
- **Tagging.** Each test creature gets `wb_admin_test`, an owner tag, a session tag and a "Test …" name.
- **Cleanup.** Test creatures are removed:
  - by Clean up (this admin's, in every dimension);
  - when their owner logs out;
  - by `EntityJoinLevelEvent`, which refuses entry to a test creature from any other server session. A crash or
    stop cannot leave them in the world.

## 6. Entity Viewer

Open it from a creature page, or from a variant's Preview button, which opens it on that variant.

- **Production renderer.** `PreviewEntity` creates the creature with `EntityType#create` on the client, the same
  path the client uses for every entity a server announces, and never adds it to a level. The viewer asks
  `EntityRenderDispatcher#getRenderer` for its renderer and calls `createRenderState`, then
  `GuiGraphics#submitEntityRenderState`, the way the inventory draws the player. The renderer's class name is shown
  in the panel and in the debug readout.
- **Camera.** Drag to orbit (yaw wraps, pitch stays within ±60°), scroll to zoom (0.3–4×), and Reset to return.
- **Variant.** The selector sets the instance's variant through `VariantHolder`, so the renderer's own texture
  switch shows it.
- **Animation.** Any clip in the creature's GeckoLib animation file, or "Its own animation", which is the
  creature's real controllers at rest. The instance stands on the ground, so it shows its idle rather than the
  airborne loop. A chosen clip plays on one extra controller, appended to this instance's `AnimatableManager` only
  (the last controller owns the bones its clip keys). Loop or play once, Restart, Pause (Space), and speed 0.25–2×
  (`setAnimationSpeed` on every controller).
- **Overlays.**
  - Hitbox: the entity's box, projected through the same body turn and view rotation as the model.
  - Name.
  - Bones: the baked model's bone tree, with the bones the selected clip moves in gold. GeckoLib exposes the tree
    and each clip's bone tracks, but not posed bone positions to the GUI, so bones are listed rather than drawn.
  - Debug readout (for this preview instance only): box, eye height, variant, health, age, each controller's
    current clip, and the renderer class. Live AI, target and goal state for spawned test creatures are in the
    existing in-world `CreatureDebugInspector`.
- **Cost and cleanup.** One instance. It is advanced one tick per client tick only while playing, and the clip
  list and bone tree are read once. The instance is dropped on close, on `removed()` (any screen change, including
  a disconnect), and when the level it was made for goes away. Its GeckoLib state lives in the instance's own cache
  and goes with it. No global render state is touched.

## 7. How to add a creature without touching the admin UI

1. Register it as the game requires: add a `Spec` to `ModCreatures.MANIFEST` (or put a bespoke id in
   `BESPOKE_IDS`), plus its `CreatureDefinition` JSON and renderer.
2. Optional: a bestiary entry (category, grade, icon), a bond profile, a `neoforge:add_spawns` biome modifier, and
   placement conditions noted with `SpawnConditionNotes.note` beside its predicate.
3. Nothing else. It appears in the browser, gets a `creature/<id>/natural_spawn` rule, can be spawned and cleaned
   up, and opens in the viewer. The shared `creature_property.*` texts need no new lang keys.

To add a variant set, see §3. A new placement condition needs one `admin.wizards_and_beasts.spawn_condition.<key>`
lang key.

## 8. Commands

```
/wandb admin creature list [filter]
/wandb admin creature info <creature>
/wandb admin creature spawn <creature> [variant] [no_ai]      (world capability; the server places it)
/wandb admin creature cleanup
/wandb admin config set wizards_and_beasts:creature/troll/natural_spawn false
/wandb admin config set wizards_and_beasts:creature/niffler/variant/pale/weight 20
```

## 9. Tests

- **JUnit**
  - `CreatureRulesTest`: defaults; roll proportions without rules (60/30/10); a disabled variant is never rolled
    and weights apply; all-zero fallback; per-creature natural-spawn rule; codec round trip; the shipped variant
    sets and Niffler weights.
  - `AdminCreaturePayloadCodecTest`: list and detail round trip; the spawn request carries no position; rule ids
    and their lang keys.
  - `AdminCatalogLangTest`, `CommandTreeShapeTest`.
- **GameTest** (`AdminCreatureTests`, 6 scenarios)
  - With the rule off, the real `SpawnPlacements.checkSpawnRules` fails natural and world-generation spawns, while
    eggs, commands and breeding are untouched and vanilla mobs are ignored.
  - Disabled coats are never rolled; the last one cannot be disabled; weight 0 is out of range.
  - A non-admin cannot spawn, read the roster or change a rule.
  - Test spawns:
    - an unknown creature, an unknown variant and another creature's variant are all refused;
    - a valid spawn lands within reach with the requested coat and its tags;
    - nothing is placed behind a wall;
    - cleanup removes the creature.
  - A test creature from another session never enters a level.
  - The page reads the acromantula's biome-modifier entry, its colony-cap condition, its definition health, and the
    Niffler's coats and weights.
- **Client capture** (`WB_ADMIN_CAPTURE_PLAN=creature`, 12 shots):
  - the browser and pages;
  - the viewer with overlays, variant switching and a clip from the appended controller;
  - a test spawn placed in plain sight, then cleaned up.
