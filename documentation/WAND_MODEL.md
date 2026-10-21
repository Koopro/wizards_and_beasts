# The modular wand

One production wand model, `geckolib/models/item/wand.geo.json`, owned by `tools/wand_model.py`
(which also writes `textures/item/wand.png` and `geckolib/animations/item/wand.animation.json`).
Built 2026-09-29 on the V5 "Blockbench-safe modular" foundation. The Elder Wand keeps its own model
(`tools/item_geo.py`) but follows the same length and attachment.

## Contract

```
wand_root
├── handle   ── handle_<variant>   y  0 → 11     top face covers 1.5 × 1.5
├── shaft    ── shaft_<variant>    y 11 → 23     base 1.5 × 1.5 on the axis, top 1.0–1.5 on the axis
│   └── tip  ── tip_<variant>      y 23 → 27.5   base 1.0 × 1.0 on the axis
│       └── wand_spell_attachment  (0, 27.5, 0), no geometry, owned by no variant
├── core     ── core_<variant>     inside the grip, ±0.5 of the axis
└── ornament ── ornament_<variant> on the standard 2 × 2 grip
```

Joints only ever step inward or run flush, so every handle × shaft × tip combination connects.
A variant may curl, bulge or twist between its planes and past the free ends (below the butt,
beyond the point), as long as it is back on the axis at its planes.

`WandModelContractTest` measures all of this on the shipped geo with bone rotations applied, and
`python tools/wand_model.py --check` does the same from Python.

## Blockbench rules

- Cubes are axis-aligned; nothing turns a cube. Twists, curls and hooks are chains of bones, each
  rotated about its own pivot.
- Cube sizes are whole numbers. GeckoLib floors box-UV sizes, so a 1.44-wide cube samples the wrong
  texels. A fractional width is an integer size plus a uniform negative `inflate`; that is why a
  part's width, height and depth share their fraction (`Wand.box` enforces it). Parts may overlap
  where the wider part encloses the overlap.
- Box UV only, packed by the tool; repeated parts of one variant share an island.

Verified 2026-09-29: the geo imports into Blockbench 5.1.6 (111 groups, 299 cubes, no rotated
cubes, no negative sizes) and re-exports identical.

## Selecting a combination

`WandConfiguration` (data component `wizards_and_beasts:wand_configuration`) maps slot → module id.
An explicit configuration wins; otherwise `WandAppearance` lays the wood's
`wand_woods/*.json` `appearance {handle, shaft, tip}` over the default classic / straight / pointed.
`/wandb wand config set|preset …` writes one. `WandRenderer` shows the selected variant bone per slot
and hides the rest (with their child bones).

Module ids are global across slots, so a name used in two slots needs a slot suffix in the id:
the three Bellatrix parts are `bellatrix_handle`, `bellatrix_shaft`, `bellatrix_tip`, all on bones
named `<slot>_bellatrix` (`WandModuleRegistry.register(slot, id, boneVariant)`). Existing ids are
never renamed: saved wands store them.

## Spell attachment

`WandRenderer.preRenderPass` listens on `wand_spell_attachment` and books the drawn point into
`client/beam/WandTipTracker`, which beams and spell clashes read. Because the bone hangs off the
`tip` slot and not a variant, it does not move when the parts change; it does follow a look's
length and bow (2026-10-07, "Looks" below) and the `cast`/`charge` clips, which animate `wand_root`.
The Elder Wand, never reshaped, keeps it on `wand_root`.

## Animations

`animation.wand.charge` (loop) plays while the holder is in a wand hold with this stack,
`animation.wand.cast` once when the hold ends; otherwise the rest pose. Driven by `WandItem`'s
controller from the `HOLDING_CAST` render ticket `WandRenderer` sets for wands drawn in a hand.
Cosmetic only; nothing reads it back.

## Adding a variant

1. Write `def <slot>_<name>(w, b)` in `tools/wand_model.py` with `w.sq` / `w.box` (rendered extents)
   and `w.bone` for anything that turns; add it to that slot's dict.
2. Register it in `WandModuleRegistry.bootstrap()`.
3. `python tools/wand_model.py --preview <dir>` — it refuses to write a rig that breaks the contract
   or overflows the 96 × 96 sheet — then look at the sheets.
4. `./gradlew test --tests "*.WandModelContractTest"`.

## Looks

Built 2026-10-07. Two wands with different makes used to differ only by the wood's tint and
silhouette; core, length, flexibility and bond were invisible and every wood shared one grain. A
wand's **look** is now resolved from the data it already carries and applied at render time. No
component was added to the stack, no stat moved, no id changed.

```
stack components ──► WandLookResolver (shared, pure) ──► WandLook ──► WandRenderer (client)
 wood, core, length,      + wood/core `appearance`          grain, tint,       main pass: grain sheet × tint
 flexibility, explicit      blocks (datapack)                configuration,     bone snapshots: visibility,
 configuration, elder                                        length/girth/bow/  shaft scale/rotation
                                                             twist, core light  overlay layers: trim, glint
 holder vs WAND_MASTER ──► WandBondLook (per draw) ──────────────────────────► glint brightness, pommel mask
```

| Channel | Source | How it is drawn | Cost |
|---|---|---|---|
| Colour + silhouette | wood `appearance.tint/handle/shaft/tip` (unchanged) | tint × sheet, variant bones | — |
| Grain | wood `appearance.grain` → `textures/item/wand/grain_<g>.png` | the main pass's texture; same UV layout as `wand.png`, unknown grain → `wand.png` | 10 small sheets |
| Rare construction | wood `appearance.rare[] {chance, handle?, shaft?, tip?, ornament?}` | modules laid over the wood's when the make's seed falls in a construction's share | — |
| Metal stays metal | `wand_trim.png` (brass, silver, stones, leather, leaves only) | overlay pass, untinted, cutout | 1 pass |
| Core | core `appearance {glint, pattern, strength}` | overlay pass with `wand_glint.png` (point) or `wand_glint_bonded.png` (point + pommel + stones), additive (`eyes`), full-bright, in the core's colour × rhythm | 1 pass |
| Length | `wand_length` inches → shaft Y scale `1 + (in − 12.25) × 0.10`, clamped 0.55–1.45 | `shaft` bone scale; the tip undoes it so only the shaft grows | — |
| Flexibility | girth 1.00/1.00/0.95/0.90/0.85, bow 0/0.75/1.75/3/4.5° | `shaft` XZ scale + Z rotation, `tip` Z rotation | — |
| Bond | `WAND_MASTER`, `WAND_ALLEGIANCE_SCORE` vs the holder (in an inventory: the player it is drawn for) | glint strength/saturation; loyal and mastered light the pommel too | — |
| Hand-turned | seed from wood, core, length bits, flexibility | ±8° shaft turn, ±4 % tint, bow side, rhythm phase | — |
| Elder Wand | `wand_elder_wand` | own model and art (`tools/item_geo.py`): beaded knuckle grip, knotted thinning shaft; never tinted, resized or bent; cold `#D8E4F2` smoke light, knuckles wake when bonded | — |

### The length chain

`wand_root > shaft > tip > wand_spell_attachment`. The tip slot hangs off the shaft slot and the
attachment off the tip slot, so lengthening or bowing the shaft carries the point and the beam
anchor with it (`WandTipTracker` keeps reading the drawn point). Handle, core and ornament stay on
`wand_root`. `WandModelContractTest.theTipRidesOnTheShaft` holds it.

### Display contexts

Held (first and third person) the grip stays in the hand and a long wand reaches further. In a slot,
item frame, shelf or on the ground the wand is re-centred on its drawn length, so the shared display
base (fitted to the 27.5-unit model) frames every length: 15" fits a slot corner to corner. A slot
icon is cached in the GUI item atlas, so its core light shows one fixed moment of the rhythm.

### Authoring

- A wood: `"grain": "wizards_and_beasts:<g>"` (straight, fine, wavy, knotty, figured, banded, flecked,
  stringy, pored, nodal) and optional `"rare": [{"chance": 0.06, "ornament": "wizards_and_beasts:metal_band"}]`
  (chances together ≤ 1; shipped woods keep ≤ 0.2). A new grain is a new `GRAINS` entry and
  `grain_factor` branch in `tools/wand_model.py`.
- A core: `"appearance": {"glint": "#RRGGBB", "pattern": "steady|pulse|heartbeat|ember|shimmer|storm|smoke", "strength": 0..1}`.
  A core without one gets a muted colour derived from its id.
- An explicit `wand_configuration` (command, preset) still wins over the wood and any rare construction.

### Checking a change

- `python tools/wand_model.py --looks DIR` — contact sheets: woods, lengths, flexibility, cores, bond,
  every rare construction (an offline mirror of the resolver; `WandLookAssetTest` pins its tables).
- Control Center → Visuals → Debug → **Wand look preview** (`WandLookPreviewScreen`): cycle wood, core,
  length, flexibility, bond and make on a display wand drawn by the real renderer; *Next rare* searches
  makes for a rare construction; the swatch shows the core light's live rhythm.
- `WandLookResolverTest` (channels), `WandLookAssetTest` (sheets share the UV layout, masks only touch
  used texels, cores distinct, rare modules exist, and the **golden snapshot**
  `src/test/resources/wand/wand_looks.golden` of every wood × core: delete it and rerun to accept an
  intended change).
- In a client: `WB_ITEM_SHOWCASE=<list> ./gradlew runClient2` with `label=wizards_and_beasts:wand[...]` lines.
