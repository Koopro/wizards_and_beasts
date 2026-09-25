# Item Asset Result — 2026-09-25

Follows `documentation/ITEM_ASSET_AUDIT.md` (baseline `5a9c935c`). Branch `dev`, commits
`4f0c31b2` … `f2e9fa27`. Every visual claim below was checked in a running client with
`client/debug/ItemShowcaseCapture`: 226 items were photographed after the changes, in six views
each, and compared with the 215-item baseline on side-by-side contact sheets.

## Summary

The item art from the 09-23 rework was sound; the numbers that place it were not. Three root
causes explained most of what looked wrong in game, and each is now fixed where it originates
rather than item by item:

1. **One display transform for every 3D prop** (vanilla `block/block`, built for a 16-unit cube).
   Replaced by *hold classes*: transforms are computed from each model's own bounds.
2. **A wrong coordinate frame for GeckoLib items.** Geo `y 0` is the centre of the item cube, not
   its floor, so every animated item drew 8 units high. Each one now gets a transforms-only base
   computed from its geo.
3. **Wand identity was a tint in a Java table**, and every wand made in play had the same shape.
   Colour and silhouette are now data on the wood.

## Commits

| Commit | What |
|---|---|
| `4f0c31b2` | `ItemShowcaseCapture`: dev-only, env-gated client harness that photographs items in slot, ground, frame, first and third person |
| `e4597075` | `ITEM_ASSET_AUDIT.md` |
| `63700d26` | Hold-class transforms for 101 cuboids; `<id>_held` geo bases for 30 AnimatedItems; 11 dead cuboids and 5 `builtin/entity` bases removed |
| `c8ad65ad` | Wood `appearance` (tint + handle/shaft/tip) as datapack data; `WandAppearance` reads the synced registry |
| `d9683c7b` | Wand transforms computed, neutral wood sheet, Elder Wand rebuilt the right way up |
| `32a2d08d` | Harness takes `/give`-style stacks (wands of a given wood) |
| `a0122346` | Books rebuilt: overhanging boards, inset pages, banded spine, cover art taken from each icon |
| `220a8e0e` | 8 unreachable item models and 6 textures only they used, deleted |
| `7d3f1b4b` | A framed wizard card shows its face |
| `f2e9fa27` | 36 hand-written flat models that were identical to datagen output now come from datagen |

## Changed models and why

### Cuboid props (101 models; generator `tools/item_models_3d.py`)
**Before:** every non-rod prop used `block/block`'s display: first person scale 0.4, third person
0.375. A 4-unit vial was drawn at a quarter of a held block, gloves and the chess set were not
visible at all, and books showed their page block to the sky. Rods (quills, telescopes) were
turned edge-on and ran off the top of the screen.

**After:** each builder is tagged with a hold class (`vessel`, `book`, `rod`, `small`, `object`,
`artifact`). A class fixes a rotation, a target visual size and an anchor for each context, and
`tools/item_display.py` solves scale and translation from the model's bounds. Tuned in game
against vanilla's potion, book and stick. Vessels now match the vanilla potion in first person,
and small props sit in the palm.

**Why this is better:** one rule instead of 101 hand-typed blocks. A new prop is correct without
anyone typing numbers, and a size change is one edit that the capture harness can verify.
`--display-only` rewrites only the `display` block (geometry and sheets stay byte-identical, which
was verified on all 101).

### GeckoLib items (30; generator `tools/item_geo.py`)
**Before:** the special model's `base` was whatever sat at `models/item/<id>.json`: a flat
sprite, a leftover cuboid, or a `builtin/entity` stub. None matched GeckoLib's frame: vanilla ends
`ItemTransform.apply` with `translate(-0.5)`, then `GeoItemRenderer` translates `(0.5, 0.51, 0.5)`,
so geo `(0,0,0)` is drawn at block-model `(8, 8.16, 8)` with x mirrored. The Snitch was above the
first-person view, brooms filled the screen, and coins were invisible (scale 0.05 to 0.15).

**After:** `models/item/<id>_held.json` holds transforms only, computed from the geo's own bounds,
and `ModModelProvider.iconInSlotGeoInHand` points `base` at it. Brooms have a `staff` class; the
coins keep their size ratio (Galleon > Sickle > Knut) through `relative_to`.

**Removed:** 11 cuboids plus their `_3d.png` sheets that existed only for their `display` block,
and the 5 `builtin/entity` bases (a parent removed in 1.21.x; the client logged
`Missing block model: minecraft:builtin/entity`). The warning is gone from the after-capture log.

### Wand
**Before:** `builtin/entity` parent; a 1-pixel dark diagonal in slots and frames; a vertical pole
cut off in first person. Ten woods shared one silhouette, because nothing wrote the configuration
component except `/wandb wand config`, even though the rig carries 55 authored variant bones. The
sheet was painted warm brown, so every wood tint multiplied out to the same dark brown.

**After:**
- `wand_woods/*.json` gain `appearance: {tint, handle, shaft, tip}`. Each of the ten woods names
  its own combination from the author's existing modules. No wand shape was edited.
- `WandAppearance.configuration(registries, stack)`: an explicit component still wins; otherwise
  the wood's modules are laid over the base wand.
- The wand item no longer carries a default configuration, and `reset` removes the component.
- The hard-coded `CANON_WOOD_TINTS` table is deleted. Unknown woods keep a derived timber colour,
  and a woodless wand keeps the classic brown (`NO_WOOD_TINT`).
- The sheet is repainted as a light neutral ramp, so the wood tint supplies the timber (the geo's
  UVs are unchanged).
- Transforms are computed from the base wand's silhouette: the wand fills the slot diagonally,
  sits in the hand like a tool, and lies flat on the ground. Slot view is lit front-on and its
  cross-section thickened 1.4×.
- The Elder Wand geo ran point-down with the knob on top, so under the shared base it was held by
  its tip. It is rebuilt grip-down, point-up, at the base wand's length, with the same bones and
  `wand_tip` anchor.

**Why this is better:** the design constraint was "no hard-coded wood switches", and now there
are none: a datapack wood defines its own look. `WandAppearanceTest` joins wood JSON → module
registry → `wand.geo.json` bones, so a wood naming a module the rig lacks fails the build instead
of silently rendering an empty slot. Modules only drive rendering, so no stat changes.

### Books (15 cuboid books)
**Before:** boards flush with the page block and one leather colour on every face. In the hand
they read as a coloured brick, and the titles were only visible on the icon.

**After:** boards overhang an inset page block on three sides (the shadow line and pale fore-edge
that make it read as a book), the spine stands proud with two raised bands, and the front board
carries the title's own cover art. It is cropped from the 16×16 icon (page strip removed), scaled
by whole pixels, and stamped after posterising so a gilt emblem is not folded into the leather
palette. Icon and model stay the same object by construction.

### Smaller fixes
- `famous_wizard_card` + 24 card models: `fixed` rotation 0 → 180 about Y, so a framed card shows
  its face (fixed in `tools/wizard_cards.py` too).
- 36 flat models (wand cores, ingredients, sweets, `wand_blank`, the slot icons of 8 brooms, the
  Philosopher's Stone and the Remembrall) are now generated by `ModModelProvider`; `flatOrGeo`
  takes a template so the brooms stay handheld. The generated output compared equal to each
  deleted file.

## Converted to data
- Wand wood colour and silhouette: Java table → `wand_woods/*.json` `appearance`.
- Display transforms: 131 hand-maintained blocks → derived from model bounds by hold class.

## Deleted
- 11 cuboid models + 11 `_3d.png` (foe_glass, ministry_handbook, mirror_of_erised,
  monster_book_of_monsters, omnioculars, pensieve, ravenclaws_diadem, resurrection_stone,
  slytherins_locket, standard_book_of_spells, time_turner).
- 5 `builtin/entity` bases (deluminator, galleon, knut, sickle, marauders_map).
- 8 unreachable models (4 trunks, newts_case_item, pocket_case, spell_teacher, wandmakers_bench)
  and 6 textures only they named.
- `elder_wand_glowmask.png` (no renderer layer read it).
- 36 hand-written flat models (now generated).
- The `WandRenderer.WOOD_TINT` data ticket (written, never read) and `WandAppearance.CANON_WOOD_TINTS`.

## Created
- `client/debug/ItemShowcaseCapture.java`, `wand/registry/WandWoodAppearance.java`
- 30 `models/item/<id>_held.json`, 36 generated flat models
- `tools/item_display.py` (see the note on `tools/` below)

## Tests
- `WandAppearanceTest` rewritten: every shipped wood has its own colour *and* silhouette; every
  module a wood names exists in the registry, in the right slot, and as a bone in `wand.geo.json`;
  a partial appearance is laid over the base wand; derived colours stay in the timber band; a
  woodless wand is brown, not bare or black; both hex forms parse.
- `BroomItemModelTest` resolves models in the game's win order (main, then generated).
- `./gradlew build`: **BUILD SUCCESSFUL**, 2,044 unit tests, 0 failures (baseline 2,042), after every step. Gametests are
  not part of `build` and were not run.

## Intentionally left alone
- **Wand shapes.** The rig is the author's (frozen). Only the choice of modules per wood changed.
- **Wand glowmask layer (S8).** The painted glow covers core bones that sit inside the handle and
  `fx` bones the renderer hides, so adding the layer would light nothing visible. It is recorded
  here rather than wired in for no effect.
- **8 per-potion sprites (S7).** Painted on 09-23 and never drawn, because every brew is one tinted
  item. Wiring them in or deleting them is an owner decision.
- `death_eater_mask_1` = `death_eater_mask` and `demiguise_hair_fade_0` = `demiguise_hair`
  (pixel-identical): the first is a registered variant, the second is owned by the hair generator.
- `_3d.png` vs `item/model/` sheet naming (S9): renaming 90 files buys nothing a player sees.
- Worn armour models, block items and entity art are out of scope.

## Remaining art debt (seen in the after-capture)
- `standard_book_of_spells`, `ministry_handbook`, `monster_book_of_monsters` (geo, `object` class)
  are large in first person. They need their own class or a smaller size.
- `slytherins_locket` in first person shows the chain edge-on as a yellow slab.
- Cloaks (`invisibility_cloak`, `deathly_hallow_cloak`, `shield_cloak`) now read at the right size
  but as folded slabs. The silhouette is weak (`cloth_fold`, V9).
- The wand shows only its base-shape modules in the slot at 16 px. Wood differences read mainly
  through colour there, and through shape in the hand, frame and on the ground.
- Nothing here was checked on a dedicated server. Wood appearance depends on the synced wood
  registry, which is synced by design (`WandDatapackRegistries`, network codec set).

## Remaining technical debt
- **`tools/` is git-ignored** (repo history purge, 2026-08-10). The generator changes —
  `item_display.py` (new), `item_models_3d.py`, `item_geo.py`, `wand_skin.py`, `wizard_cards.py` —
  exist only on this machine. The committed JSON/PNG outputs are complete without them, but
  regenerating from a fresh clone would lose the hold-class system. Owner decision: track `tools/`
  again, or keep a copy elsewhere.
- `HAND_MODELLED_CANON` in `ModModelProvider` is still a hand list kept in sync by a check in
  `item_models_3d.py`.
- 5 hand-written item definitions in `src/main` still shadow trivial generated ones (brew, card,
  hair, sneakoscope, wand). This is deliberate; see the audit, R11.

## How to re-check
```
WB_ITEM_SHOWCASE=<list> WB_ITEM_SHOWCASE_WORLD=<save copy> WB_ITEM_SHOWCASE_OUT=<dir> ./gradlew runClient2
```
A list line is an id, or `label=id[components]` in `/give` syntax (e.g.
`wand_vine=wizards_and_beasts:wand[wizards_and_beasts:wand_wood="wizards_and_beasts:vine"]`).
Shots land in `runs/client2/screenshots/<dir>/`. Run it against a disposable save copy, because
the harness builds its stage into the world.
