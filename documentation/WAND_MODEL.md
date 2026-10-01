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
├── tip      ── tip_<variant>      y 23 → 27.5   base 1.0 × 1.0 on the axis
├── core     ── core_<variant>     inside the grip, ±0.5 of the axis
├── ornament ── ornament_<variant> on the standard 2 × 2 grip
└── wand_spell_attachment          (0, 27.5, 0), no geometry, owned by no variant
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
`client/beam/WandTipTracker`, which beams and spell clashes read. Because the bone hangs off
`wand_root` and not a variant, it does not move when the parts change; it does follow the
`cast`/`charge` clips, which animate `wand_root`.

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
