# Equipment Architecture (Curios)

Status: implemented 2026-10-07. Curios `14.0.0+1.21.11` is a **required** dependency.

A player wears **real armour and wizarding clothing at the same time**: a netherite chestplate
keeps every point of protection, its enchantments and durability while a robe covers it on screen.
Hiding armour is a **visual** decision made per frame. No code moves equipment to hide it.

```
                         EquipmentAPI  (equipment/)
                               |
         +---------------------+----------------------+
         |                     |                      |
   Vanilla armour         Curios slots           W&B special
   (EquipmentSlot)   (equipment/curios/ only)   (own systems)
   helmet  chest          head   face   body      wand
   legs    feet           back   necklace ring    broom
                          belt   hands  charm     forms
```

## 1. Packages and boundaries

| Concern | Where | Notes |
|---|---|---|
| Semantic types | `equipment/EquipmentType` | `HEADWEAR FACE BODY_CLOTHING CLOAK BACK NECKLACE RING BELT HANDS POUCH TRINKET`. This is the **only** table of Curios slot ids in the mod. |
| Draw order | `equipment/VisualLayer` | Outside-in order (skin, armour, clothing, cloak, headwear, face, neck, hands, accessory, effect). |
| Definitions (data) | `equipment/WearableDefinition`, `WearableDefinitions` | NeoForge **item data map** `wizards_and_beasts:wearables`. It is synced to clients. |
| Gameplay API | `equipment/EquipmentAPI` | Typed queries, `tryEquip`, `equipFromHand`, `stripWearables`, `snapshot`. |
| Curios adapter | `equipment/curios/CuriosEquipment`, `CuriosEquipmentEvents` | The **only** common code that imports Curios. |
| Visibility rule | `equipment/EquipmentVisibility` | A pure `Builder` (unit-tested) plus `resolve(entity)`. |
| Migration | `equipment/EquipmentMigration` | Runs on login and moves legacy armour-slot pieces to their Curios slot. |
| Render-state hiding | `client/equipment/EquipmentRenderStateModifier` | Empties `*Equipment` on the frame's render state. |
| Curios rendering | `client/equipment/curios/WornGeoCurioRenderer`, `CuriosClientRegistration` | The only client code that imports Curios. |
| Pose hand-off | `client/equipment/CapturedBodyPose` + `WizardArmorRenderer#adjustModelBonesForRender` | See section 6. |
| Debug | `equipment/debug/EquipmentFeatureDebug` | Run with `/wandb debug feature equipment`. It also appears in the over-player debug panel. |

Rules:
- Gameplay code asks `EquipmentAPI` in `EquipmentType`s. It never names a slot string and never
  imports Curios.
- Items do **not** implement `ICurioItem`. Stats come from the data map through
  `CurioAttributeModifierEvent`. Slot admission comes from the `wizards_and_beasts:wearable`
  validator. The item classes stay free of Curios types.

## 2. Slots

These are declared in `data/wizards_and_beasts/curios/slots/*.json`. They are assigned to
`minecraft:player` in `curios/entities/player.json`. Curios does **not** give its preset slots to
players on its own.

| Curios slot | Size | Types stored | Content today |
|---|---|---|---|
| `head` | 1 | HEADWEAR | Wizard Hat |
| `face` (new) | 1 | FACE | Death Eater masks (6) |
| `body` | 1 | BODY_CLOTHING | Student, Auror and Death Eater robes |
| `back` | 1 | CLOAK, BACK | Invisibility Cloak, Deathly Hallow |
| `necklace` | 1 | NECKLACE | Time-Turner (worn for display; it is still used from the hand) |
| `ring` | **2** | RING | (none yet) |
| `belt` | 1 | BELT | (none yet) |
| `hands` | 1 | HANDS | (none yet). The wand is **not** hand equipment. |
| `charm` | 1 | POUCH, TRINKET | (none yet) |

Every slot file adds the validator `wizards_and_beasts:wearable`. Curios merges validators as a
union, so other mods' `curios:<slot>` tags keep working. The validator admits a stack only if its
definition's type is stored in that slot. A robe therefore cannot enter a ring slot, and a mask
cannot enter the head slot.

Slot counts are data. To change them, override a slot file in a datapack (`"size": 3`) or use the
Curios config. Gameplay code iterates `EquipmentAPI.getEquippedRings`, so it works with any count.

## 3. The wearables data map

The file is `data/<ns>/data_maps/item/wearables.json`. It uses the standard NeoForge data-map
format, so datapacks can add entries, `replace` them and `remove` them.

```json
{
  "values": {
    "wizards_and_beasts:death_eater_robe_chest": {
      "equipment_type": "body_clothing",
      "visual_layer": "body_clothing",
      "hides": ["chest"],
      "hides_with_hood_up": ["head"],
      "faction": "death_eaters",
      "attributes": [
        { "attribute": "minecraft:armor", "amount": 6, "operation": "add_value" },
        { "attribute": "minecraft:armor_toughness", "amount": 1.0 }
      ]
    }
  }
}
```

| Field | Default | Meaning |
|---|---|---|
| `equipment_type` | required | The semantic type. It also fixes the slot. |
| `visual_layer` | the type's default | The draw layer. It sets the listing order on the sheet and in debug output. |
| `hides` | `[]` | Armour slots (`head`/`chest`/`legs`/`feet`) this piece covers **visually**. |
| `hides_with_hood_up` | `[]` | Extra slots covered while the stack's `hood_up` component is set. |
| `conceals_wearer` | `false` | The Deathly Hallow sets this. It hides all armour, both hands and every W&B piece. |
| `faction` | none | An allegiance id for future uniform-aware systems. Nothing reads it yet. |
| `attributes` | `[]` | Modifiers that apply while the piece is in a **real** (non-cosmetic) slot. They are applied server-side by Curios. The id defaults to `wizards_and_beasts:wearable/<attribute>`, and Curios suffixes each id per slot. |

The robe bodies, the hat and the masks were vanilla armour before this change. Their old armour,
toughness and knockback values now live here. `WearableDataTest` pins them to the
`WizardArmorMaterials` values the items still report. To make a piece purely cosmetic, set its
`attributes` to `[]` in a datapack.

## 4. Armour hiding: exactly how it works

1. The client builds an `AvatarRenderState` for every player it draws. That covers the world, the
   inventory preview and the Character Sheet figure; all three go through
   `EntityRenderer#createRenderState`.
2. `EquipmentRenderStateModifier` is a NeoForge render-state modifier and runs last in the list.
   It calls `EquipmentVisibility.resolve(player)`, which does the following:
   - **Covering:** it walks the stacks Curios will actually *draw*. A stack in a cosmetic slot
     wins; otherwise the real stack is used if that slot's render toggle is on. Each defined piece
     adds its `hides`, plus `hides_with_hood_up` when the hood is up.
   - **Concealment:** it walks the *real* stacks. Any `conceals_wearer` piece conceals.
3. For every hidden slot, the modifier sets `state.headEquipment`, `chestEquipment`,
   `legsEquipment` or `feetEquipment` to `ItemStack.EMPTY`. Vanilla's `HumanoidArmorLayer` and
   GeckoLib's armour mixin both read only those fields, so the piece is not drawn this frame. An
   elytra or other glider is never hidden by clothing, because `WingsLayer` reads the same field.
   Only concealment hides a glider.
4. The real `ItemStack` never leaves `EquipmentSlot.CHEST`. It keeps applying its attributes and
   taking durability damage on the server, and it syncs normally.

Two cases follow from the covering rule. A robe whose render toggle is off covers nothing, because
an undrawn robe over a hidden chestplate would just look like a missing chestplate. A cosmetic
robe *does* cover the real chestplate, because it is what is on screen.

## 5. Cloak migration (gameplay unchanged)

- The `CloakItem` registration drops `.equippable(CHEST)`. Right-click equips the cloak through
  `EquipmentAPI.equipFromHand` into the `back` slot. A cloak already on the back is never
  displaced.
- Cloak *state* stayed where it was. `CloakCharges` holds charges on the stack, plus the Hallow's
  exemption. `CloakEffectsHandler` handles invisibility, charge spend, Deathly equipment-masking
  packets, mob de-targeting, and the login, respawn, dimension and tracking resyncs. Only the
  handler's *location* lookup changed, to `EquipmentAPI.getEquippedCloak`.
- `DemiguiseHairItem` recharges the cloak held in the other hand, or else the worn cloak through
  the API.
- **Mobs** have no Curios slots. Using a cloak on a mob still puts it in the mob's chest slot, where
  the old `LivingEquipmentChangeEvent` path makes the mob invisible.
- `CloakClientViewRestrictionHandler` was deleted. It hid only the local player's equipment.
  `conceals_wearer` in the render-state modifier now handles every viewer.
- **Legacy saves:** `EquipmentMigration` runs on login. For each armour slot holding a stack *with
  a wearable definition*, it does the following:
  1. It equips the stack in the stack's Curios slot, with all components intact (charges, hood,
     name, enchantments).
  2. If that slot is occupied, it moves the stack to the main inventory instead.
  3. If the inventory is full too, it leaves the stack where it is, logs a warning and tries again
     on the next login.

  Real armour has no definition and is never touched. The pass is idempotent.

## 6. Rendering clothing from a Curios slot

`WornGeoCurioRenderer` is a single `ICurioRenderer` bound to every `WizardArmorItem` with
`Wear.CURIO`. It asks the item for **its existing `GeoArmorRenderer`**, so the model, texture,
hood bones and mask casting are all unchanged. It then runs a normal GeckoLib pass:

- **Render state:** it uses a fresh `HumanoidRenderState` per piece, holding GeckoLib's data
  tickets. The wearer's own state is never written to, so a hat and a robe on the same player
  cannot clobber each other's tickets.
- **Pose:** in 1.21.11, GeckoLib computes bone transforms *lazily, at flush time*. By then the
  shared `PlayerModel` has been re-posed for another player. The parent model has just been posed
  for this wearer (`LivingEntityRenderer#submit` calls `setupAnim` right before its layers), so the
  six part transforms are copied into `CapturedBodyPose` (36 floats).
  `WizardArmorRenderer#adjustModelBonesForRender` applies them with GeckoLib's own per-segment
  offsets. The parent is the same slim or classic `PlayerModel` type that vanilla fits armour to,
  so slim arms line up exactly as a chestplate's would.
- **Segments:** only the item's `armorType()` bone group is drawn. A robe body draws the torso,
  the sleeves, the skirt hanging from `armorBody`, and the hood, which `HoodedArmorRenderer` adds
  as the head segment. The trousers are the separate `_legs` armour item.
- **First person:** nothing is drawn, matching vanilla armour.
- **Concealed wearer:** the renderer draws nothing.
- **Cost:** per piece per frame, one render-state object and one 36-float array. There are no
  inventory scans; `resolve` walks only the Curios slots, about 10.

## 7. Authority

Every gameplay effect is decided on the server from the real Curios inventory: stats through
Curios, invisibility and charges through `CloakEffectsHandler`, hood toggles through the
`HoodToggleC2SPayload`, which names no slot. The client's synced copy is used only for rendering,
armour hiding, the UI and the debug overlay.

## 8. Recipes

### Add a new robe (no core or renderer change)
1. Model and texture: add a set to `tools/armor_model.py` with the standard bone names, then emit
   `geckolib/models/armor/<set>.geo.json` and `textures/armor/<set>.png`.
2. Renderer: create a three-line subclass of `WizardArmorRenderer` (or of `HoodedArmorRenderer`
   if the robe has a hood) that passes the set name.
3. Item: create a subclass of `WizardArmorItem`. Its constructor passes `robeWear(armorType)`, and
   `rendererClassName()` returns the renderer's FQCN. Register `_chest` (and `_legs`/`_boots` if
   the set has them) in `ArmorItemRegistry`, then add the model and lang datagen lines as for any
   item.
4. Data: add one entry to `wearables.json`, for example
   `{"equipment_type": "body_clothing", "hides": ["chest"], "faction": "ministry"}`.

   That is all. `CuriosClientRegistration` binds the renderer because the item is
   `Wear.CURIO`, the validator admits it to `body`, and the render-state modifier hides the
   chestplate.

### Add a new cloak
Register a `CloakItem` (or a plain item, for a cloak with no charges) and add
`{"equipment_type": "cloak"}`. Add `"conceals_wearer": true` only for Hallow-grade concealment, and
`"hides": ["chest"]` only once the cloak has a drawn model. A worn piece with no visual that hides
armour just makes the armour vanish. The invisibility effect is keyed on `CloakItem` in
`CloakEffectsHandler`.

### Add a new ring
Create a plain `Item`, then add
`{"equipment_type": "ring", "attributes": [{"attribute": "minecraft:max_health", "amount": 2}]}`.
Both ring slots accept it. If the ring needs a visual, make it a `WizardArmorItem` with
`Wear.CURIO` and a renderer, or register a separate `ICurioRenderer` in
`client/equipment/curios/`.

### Add a new hat
Follow the `WizardHatItem` pattern: a `WizardArmorItem` with `ArmorType.HELMET` and `Wear.CURIO`,
and a renderer drawing `armorHead`. Then add
`{"equipment_type": "headwear", "hides": ["head"]}`.

### Add a new necklace
Use a plain item with `{"equipment_type": "necklace"}`. Gameplay reads it with
`EquipmentAPI.getEquippedNecklace(player)`, server-side only.

### Add a new mask
Follow the `DeathEaterMaskItem` pattern. A per-item skin is `withAltTexture` on the shared model.
Then add `{"equipment_type": "face", "hides": ["head"]}`. A face mask hides the helmet because a
helmet (inflated by one pixel) would otherwise swallow the mask.

## 9. Interactions with other systems

| System | Effect |
|---|---|
| Werewolf change | `WerewolfEquipment` also strips W&B wearables through `EquipmentAPI.stripWearables`, respecting the whitelist. Without this, a cloak worn on the back would keep a wolf invisible. |
| Hood keybind | `RobeHood.toggleTarget` reads the worn body clothing. |
| Animagus and other non-humanoid forms | `LivingEntityRendererMixin` cancels the player render, so Curios pieces are not drawn. This is the same as armour. |
| Deathly Hallow | Observers receive masked vanilla equipment packets from the server. Their copy of the Curios inventory still holds the cloak, so the render-state modifier conceals the W&B pieces. |
| Death | Curios applies its default drop rule, the same as armour; `keepInventory` is respected. |
| Respawn, dimension change, relog | Curios persists the inventory and the mod adds no copies. See the GameTests. |
| Duelling Dummy | Uses vanilla armour slots only, so robe bodies, hats and masks can no longer be put on it. |

## 10. Tests

- JUnit `EquipmentVisibilityTest` covers the hiding and concealment rule.
- JUnit `WearableDataTest` checks that every data-map entry decodes, names a real item and maps to
  a declared, player-assigned and validated slot, that rings have at least 2 slots, that clothing
  stats equal the materials, and that only the Hallow conceals.
- GameTests in `gametest/EquipmentTests` (14 scenarios) cover equip and remove, armour stats under
  a robe, visual-only hiding, the hood, slot validation, a full outfit, cloak effects and charges,
  the Hallow, legacy migration through a real save and reload, the never-overwrite rule, relog,
  dimension change, death drops and the werewolf strip.
- Visual: `client/debug/EquipmentShowcaseCapture`. Run it with `WB_EQUIPMENT_SHOWCASE=1 ./gradlew runClient`
  against a disposable save named `wb_equipment_capture`. It photographs 7 outfits from the
  quarter, front, side and back, sneaking, walking, in first person, in the inventory preview and
  on the Character Sheet. Use `runClient2` (Dev2) for the classic arm model; `Dev` is slim.

## 11. Known limits

- **Clothing under an ordinary Invisibility Cloak stays visible.** The body is invisible but the
  robe, hat and mask still draw. This matches vanilla, which also draws armour on invisible
  entities, and the old behaviour for the hat and mask. Only the Hallow (`conceals_wearer`) hides
  the outfit.
- **Cloaks have no worn model.** Before this change they drew nothing either. When a cloak model
  exists, give the cloak an `ICurioRenderer` and `"hides": ["chest"]` if it should cover armour.
- **Character Sheet:** the data is ready, because `EquipmentAPI.snapshot(mc.player)` works
  client-side from Curios' synced copy. There is no dedicated equipment panel yet; the sheet's
  figure and its Armour attribute already reflect clothing.
- **Curios 14.0.0 empties slots on `/reload`.** `CuriosReloadGuard` repopulates the registry; delete
  it once Curios fixes this upstream.
- **Duelling Dummy** uses vanilla armour slots only, so robe bodies, hats and masks cannot be put
  on it.
