# Item Asset Audit — 2026-09-25

Scope: the **visual and asset architecture of items** — item definitions, item models (hand-written
and generated), GeckoLib item models, item textures, display transforms, the generators under
`tools/` that write them, and the Java that routes them. Block *placement* models, entity art and
item *behaviour* are out of scope except where they decide how an item looks.

Baseline: `dev` @ `5a9c935c` (the day after the parchment/item art rework, commits `4db88abe` …
`bf9ed960`). Nothing in that rework had been looked at in a running client — its own plan
(`tasks/todo_art_rework_2026_09_23.md`) lists "in-game check: size/position in hand" as open. This
audit is that check.

---

## 1. Method

1. **Inventory by script** — every `items/*.json` in both resource roots resolved with the real
   win order (`src/main` beats `src/generated`: `build.gradle:161` second srcDir + `EXCLUDE`), every
   model's parent chain, element bounds, merged `display` block, texture sizes; every GeckoLib item's
   geo bounds, bones, animations and sheet size; the Java class behind every registration.
2. **In-game capture** — a new dev-only harness, `client/debug/ItemShowcaseCapture`
   (env-gated, inert in production), opens a disposable world, builds a floating stage (floor, wall,
   item frame, dropped item) and photographs every non-block item twice: first person with the
   hotbar (slot icon, first-person hand, ground, item frame in one frame) and third person from the
   front. 206 mod items + 9 vanilla references (sword, stick, book, spyglass, trident, potion,
   shield, clock, bundle) = 430 screenshots, assembled into contact sheets (slot · ground · frame ·
   first-person · third-person per row). Every visual finding below was read off those sheets, not
   off JSON.
3. **Two read-only auditors** cross-checked resource reachability (textures, models, duplicates) and
   the Java rendering paths; their findings are folded in where they concern item visuals.

Reproduce:
```
WB_ITEM_SHOWCASE=<ids.txt> WB_ITEM_SHOWCASE_WORLD=<save copy> WB_ITEM_SHOWCASE_OUT=<dir> ./gradlew runClient2
```
Shots land in `runs/client2/screenshots/<dir>/<id>__fp.png|__tp.png`.

---

## 2. Inventory

538 item definitions (`items/*.json`) — one per registered item, parity verified both ways.

| Group | Count | How it renders |
|---|---:|---|
| Block items (planks, slabs, stairs, logs, walls, leaves, location stone, props, 9 saplings) | 227 | the block's own model / vanilla sapling sprite — **vanilla conventions, out of scope** |
| Flat everywhere (`item/generated` / `item/handheld`) | 188 | vanilla sprite behaviour in all six views (107 of them spawn eggs) |
| Flat icon in slots / **cuboid in hand** (`iconInSlotModelInHand`, + `brew`) | 90 | icon in GUI/ground/frame/shelf; `models/item/<id>.json` (from `tools/item_models_3d.py`) in hand |
| Flat icon in slots / **GeckoLib in hand** (`AnimatedItem`) | 30 | icon in slots; `geckolib:geckolib` special model in hand, `base` supplies the transforms |
| GeckoLib everywhere | 1 | `wand` (own `WandRenderer`, geo also in GUI) |
| Component select / range dispatch | 2 | `famous_wizard_card` (24 cards), `demiguise_hair` (fade frames) |

311 non-block items. The capture covered all of them except 105 of the 107 spawn eggs (two sampled —
the eggs share one flat convention), i.e. **206 items**.

Item textures: 483 PNGs — 323 flat 16×16 icons, 101 cuboid UV sheets (`<id>_3d.png`), 27 GeckoLib
sheets (`item/model/<id>.png`), wand/elder-wand sheets and glowmasks, 25 card faces.

### Classification of the 206 captured items

| Class | Count | Current strategy |
|---|---:|---|
| 1 Vanilla-style flat | 32 | flat (raw ingredients, 2 sampled spawn eggs; +105 eggs not captured) |
| 2 Detailed flat | 13 | flat, richer palette |
| 3 Handheld | 16 | flat handheld sprites, 8 GeckoLib brooms, 9 "rod" cuboids |
| 4 3D inventory item | 0 | **deliberately none** — see §5 |
| 5 3D functional object | 18 | flat icon / cuboid in hand |
| 6 Magical artifact | 28 | flat icon / cuboid or GeckoLib in hand |
| 7 Wand | 4 | `wand` GeckoLib everywhere; debug/morph/blank flat handheld |
| 8 Book/document | 24 | flat icon / cuboid (18 `book()`), 3 GeckoLib books, map, howler |
| 9 Potion/brew | 20 | flat icon / cuboid (vial, bottle, flask) |
| 10 Armour | 25 | flat icons (worn look is a GeckoLib armour renderer, not an item model) |
| 11 Special rendered | 7 | brew tint, sneakoscope spin, card select, hair fade, coins |

The per-item table (class, strategy, generator, verdict) is the appendix.

---

## 3. How an item gets its look today (pipeline map)

```
registration (ModItems.ITEMS, *ItemRegistry)            Java class decides only one thing visually:
        │                                                 `instanceof AnimatedItem` → GeckoLib in hand
        ▼
datagen/ModModelProvider.registerModels                 writes items/<id>.json (src/generated)
   ├─ generateFlatItem ............................ flat, model generated too
   ├─ declareCustomModelItem / declaredOrGeo ...... model hand-written in src/main
   ├─ iconInSlotModelInHand ....................... vanilla createFlatModelDispatch:
   │                                                   <id>_inventory (flat, generated) | <id> (src/main)
   └─ iconInSlotGeoInHand ......................... icon | geckolib special, base = models/item/<id>
        ▲
src/main/resources/assets/.../items/*.json           5 hand overrides that win (brew, card, hair, sneakoscope, wand)

tools/ (Python, each PNG carries a Generator marker; artgen_common.save refuses cross-writes)
   item_sprites.py   16×16 icons              (protected)
   item_models_3d.py cuboid models/item/<id>.json + <id>_3d.png, ITEMS table, DISPLAY_SOLID / DISPLAY_ROD
   item_geo.py       geckolib/models|animations/item/<id> + item/model/<id>.png (protected)
   wand_skin.py      wand.png / wand_glowmask.png (repaint only; wand shapes frozen)
   item_model_preview.py  offline GUI-angle contact sheet of the cuboids (GUI view only)
```

`AnimatedItem` + one shared `AnimatedItemRenderer` + `AnimatedItemAssetsTest` is sound: one renderer,
id-derived assets, presence tested. The routing is sound. **What is wrong is almost entirely in the
numbers the generators write: display transforms and one coordinate-frame assumption.**

---

## 4. Findings — visual (read off the in-game capture)

Severity: **S1** broken (invisible / off-screen / wrong side), **S2** reads wrong, **S3** polish.

### V1 · S1 · 3D props are microscopic in first person (83 items)
*Evidence:* every vial, bottle, flask, mug, jar, sweet, gem, cup, book in the capture sits as a
thumb-sized shape at the bottom centre of the first-person view; `dragon_hide_gloves`,
`shield_gloves`, `wizards_chess_set` are not visible at all. Vanilla neighbours (stick, book, potion)
fill the lower right quarter.

*Root cause:* `tools/item_models_3d.py:71` `DISPLAY_SOLID` is vanilla `block/block`'s display —
first person `scale 0.4`, third person `0.375` — applied to **every** non-rod prop. Those numbers
assume a full 16-unit cube. A vial is 4 units wide and 12 tall; at 0.4 it is drawn at a quarter of a
held block's width. The transform was lifted for a solid and applied to things that are not blocks.

*Also:* the same preset's rotation shows a book as a box with its page block facing the sky.

### V2 · S1 · GeckoLib items draw 8 px too high (every `AnimatedItem`)
*Evidence:* `golden_snitch` is above the first-person view and floats over the fist in third person;
`sneakoscope`, `remembrall`, `philosophers_stone` are cut off at the top; `portkey`, `howler` sit
high.

*Root cause:* `tools/item_geo.py` documents its frame as "x and z centred on 0, **y up from 0** …
the same space the hand-written cuboid occupies once its x/z are shifted by −8". That is not what
GeckoLib does. `ItemTransform.apply` (vanilla) ends in `translate(-0.5,-0.5,-0.5)`, and
`GeoItemRenderer.adjustRenderPose` then applies `translate(0.5, 0.51, 0.5)` — so geo `(0,0,0)` lands
at block-model **(8, 8.16, 8)**, the centre of the item cube, not its floor. (GeckoLib also mirrors x:
`BakedModelFactory:179`, `-(origin.x + size.x)`.) Every geo item therefore renders 8 units above
where its `base` transforms expect it, and the items whose base is a flat sprite model (no custom
display) show it worst.

### V3 · S1 · Brooms fill the first-person screen
*Evidence:* all 8 brooms: a wall of bristle/handle cubes on the right, cut off by the screen edge;
third person reads fine.
*Root cause:* the brooms' `base` is a plain `item/handheld` flat model (`models/item/<broom>.json`),
so a 24-unit-long 3D rig gets the transforms of a 16-unit sprite, shifted up 8 units by V2.

### V4 · S2 · "Rod" props read as planks (9 items)
*Evidence:* `quill`, `quick_quotes_quill`, `auto_answer_quill`, `probity_probe`, `dark_mark_brand`,
`telescope`, `punching_telescope`: first person shows a flat, face-on column running off the top of
the frame — the plume/lens is never seen.
*Root cause:* `DISPLAY_ROD` (`item_models_3d.py:86`) turns the long axis to face the camera edge-on
at 0.75 and pushes it up 3.5 units; the object's profile is hidden.

### V5 · S1/S2 · The wand, the mod's most important item, reads worst
*Evidence:* slot and item frame show a 1-px dark diagonal; first person is a vertical pole whose tip
is cut off; the ground shows it standing upright. Third person is acceptable.
*Root causes:*
- `models/item/wand.json` gives GUI/fixed `scale 0.5` to a 26-unit-long, 1.5-unit-thin rig: the
  wand occupies ~40 % of the slot diagonal at one pixel wide.
- First person has no rotation: the Y-aligned rig stands straight up.
- `parent: builtin/entity` — removed in 1.21.x; the client log warns `Missing block model:
  minecraft:builtin/entity` (also for `deluminator`, `galleon`, `knut`, `sickle`, `marauders_map`).
- **Wood identity is only a tint.** The wand rig carries 55 authored variant bones (14 handles, 10
  shafts, 11 tips, 5 cores, 9 ornaments; `WandModuleRegistry`), but every wand made in play renders
  `WandConfiguration.DEFAULT` (classic / straight / pointed) — the only writer of
  `WAND_CONFIGURATION` is `/wandb wand config`. Ten woods share one silhouette.
- The tint itself is a hard-coded wood→colour table (`wand/WandAppearance.CANON_WOOD_TINTS`) beside
  a datapack wood registry that is already synced to clients (`WandDatapackRegistries`, network codec
  set) — the one hard-coded wood switch left in the wand system.
- `wand_glowmask.png` / `elder_wand_glowmask.png` are painted by `wand_skin.py` but `WandRenderer`
  adds no glow layer, so cores never glow.

### V6 · S2 · Books are coloured boxes in hand (18 cuboid books)
*Evidence:* first person shows a box of cover colour with a cream top; the icon's emblem, title
panel and bands are absent. Third person: a small brown brick.
*Root cause:* `item_models_3d.book()` builds full-size boards and a page block flush with the fore
edge (no overhang, so no visible "pages between boards"), a spine block, and paints every face with
the plain `leather` material — the cover art that makes each book identifiable exists only in the
16×16 icon. Width/height vary per title; nothing else does.
The three GeckoLib books (`standard_book_of_spells`, `ministry_handbook`, `monster_book_of_monsters`)
read correctly (open book, pages, bands) — they are the reference for what the cuboid books should
reach.

### V7 · S2 · Coins are invisible in hand
`galleon` / `sickle` / `knut`: first-person `scale 0.15 / 0.1 / 0.05`, third person the same →
not visible in third person at all, a speck in first person. (Coin *size difference* was the intent;
the base values were tuned for a pre-GeckoLib renderer.)

### V8 · S2 · Wizard card in an item frame shows its back
`famous_wizard_card` is a cuboid everywhere; its `fixed` transform shows the rear face, so a framed
card is a black rectangle.

### V9 · S3 · Cloaks read as small white/grey boxes
`invisibility_cloak`, `deathly_hallow_cloak` (`cloth_fold`) — the folded-cloth geometry is a
2-box slab; in hand it is a featureless block. Acceptable once V1 sizes it; silhouette polish only.

### Verified OK (seen in game)
- Every flat item behaves like its vanilla counterpart in all six views; icons are clean
  16×16 pixel art on the parchment palette, readable in the hotbar beside vanilla items.
- Slot/ground/frame branch of all 119 dispatch items: flat icon, vanilla transforms — correct.
- Brew tint: correct colour in slot and hand.
- `ministry_handbook`, `standard_book_of_spells`, `monster_book_of_monsters`, `mirror_of_erised`,
  `marauders_map`, `ravenclaws_diadem`, `deluminator`, `time_turner` read as themselves in hand
  (sizes need V2's correction, shapes are fine).
- Armour icons: consistent, readable; worn look is the GeckoLib armour renderer (out of scope).
- Spawn eggs: vanilla egg shading — consistent with vanilla.

---

## 5. Target model strategy per class

The art target is *Minecraft first*: slots show 16×16 pixel art (vanilla's own rule for the
spyglass, trident and shield), and depth appears where the player looks at an object at arm's
length. Hence **class 4 stays empty on purpose**: a tilted 3D model in a 16 px slot is smaller and
less legible than a drawn icon (the 2026-09-23 rework removed 101 of them for that reason). The
wand is the one exception because its identity *is* its shape and wood — it keeps a 3D slot view,
but framed to read (V5).

| Class | Slot / ground / frame | In hand | Hold rule |
|---|---|---|---|
| 1–2 flat | flat icon | vanilla `item/generated` | vanilla |
| 3 handheld rod | flat icon | cuboid or geo, held like a tool: grip in the fist, long axis forward-up (vanilla `handheld` feel) | `rod` |
| 5 functional object | flat icon | cuboid/geo, held upright in front of the palm, scaled so the largest side reads like a held block | `object` |
| 6 artifact | flat icon | geo/cuboid, presented at eye-line, slightly larger than a prop | `artifact` |
| 7 wand | 3D, diagonal, full slot | geo, grip in the fist, tip forward and up | `wand` |
| 8 book | flat icon | cover towards the viewer, spine in the palm, readable cover art | `book` |
| 9 potion | flat icon | upright vessel, liquid visible | `vessel` |
| 10 armour | flat icon | flat (wearable, vanilla armour convention) | vanilla |
| small props (sweets, gems, coins) | flat icon | in the palm, scaled up to be legible | `small` |

**One rule for transforms, not 90 hand-tuned blocks:** each hold class fixes a rotation and a target
*visual size* (the longest side, in model units, after scaling) and an anchor point; scale and
translation are then computed from each model's own bounds so its visual centre lands on the anchor.
This is what makes a 4-unit vial and a 14-unit cauldron both read at arm's length, and what makes a
new item correct without anyone typing numbers.

---

## 6. Findings — structural / hygiene

| # | Finding | Files | Action |
|---|---|---|---|
| S1 | Two sources of hand transforms for the same GeckoLib items: 11 AnimatedItems use a *cuboid* `models/item/<id>.json` as `base` purely for its `display` block — the cuboid geometry and its `<id>_3d.png` are never drawn | `models/item/{foe_glass,mirror_of_erised,monster_book_of_monsters,omnioculars,pensieve,ravenclaws_diadem,resurrection_stone,slytherins_locket,standard_book_of_spells,time_turner,ministry_handbook}.json` + `_3d.png` | replace by a transforms-only `base` computed from the geo (V2 fix), delete the dead cuboids and sheets |
| S2 | 6 models parent `builtin/entity` (removed; warns at load) | `models/item/{wand,deluminator,galleon,knut,sickle,marauders_map}.json` | transforms-only models, no parent |
| S3 | 8 unreachable item models + the 6 textures only they name | `models/item/{enchanted,expanded,masters,moodys}_trunk`, `newts_case_item`, `pocket_case`, `spell_teacher`, `wandmakers_bench`; `textures/item/{enchanted,expanded,masters,moodys}_trunk`, `newts_case_item`, `pocket_case` | delete (items render their block model; `pocket_case` is not an item) |
| S4 | 43 hand-written item models are byte-identical to what `generateFlatItem` emits | 8 broom bases, `wand_blank`, 8 wand cores, `chocolate_frog`, `demiguise_hair(_fade_*)`, … | move to datagen (one source of truth); brooms' bases become geo `_held` models (V3) |
| S5 | `HAND_MODELLED_CANON` in `ModModelProvider` must be kept in sync with `item_models_3d.ITEMS` by a check in the tool | `ModModelProvider.java:62`, `item_models_3d.py:466` | keep (it is guarded); shrink when S1 removes geo items from it |
| S6 | Pixel-identical duplicates | `death_eater_mask_1.png`=`death_eater_mask.png`, `demiguise_hair_fade_0.png`=`demiguise_hair.png` | fade_0 model → `item/demiguise_hair`; mask_1: owner decision (it is a registered variant) |
| S7 | 8 per-potion sprites painted 2026-09-23, never drawn (every brew is the one tinted `brew` item) | `textures/item/{amortentia,…,veritaserum}.png` | **owner decision**: wire via a brew-id `select` or delete. Left untouched here |
| S8 | Wand glowmasks painted, never rendered | `textures/item/wand_glowmask.png`, `elder_wand_glowmask.png` | add the glow layer to `WandRenderer` (V5) |
| S9 | Two naming conventions for 3D sheets: `item/<id>_3d.png` vs `item/model/<id>.png` | 101 + 27 files | note only; renaming 101 files buys nothing a player sees |
| S10 | `item_geo.py` docstring states the wrong frame (V2); `ModModelProvider` comments cite stale `build.gradle` line numbers (136/139 → 161/164) | — | fix with V2 |

### Asset pipeline
- Generation is already centralised per asset kind (icons / cuboids / geo / wand skin) with a
  shared marker contract; outputs are deterministic (seeded noise, no clock/random state).
- The **display transform** is the one concept duplicated across generators (a preset table in
  `item_models_3d.py`, hand-written blocks in `models/item/*.json` for geo bases). It gets one owner:
  a small shared module (`tools/item_display.py`) used by both `item_models_3d.py` and `item_geo.py`.
- `item_model_preview.py` only previews the GUI angle — which since the slot/hand split is the one
  view these models no longer appear in. The in-game harness replaces it as the check that matters;
  it stays as an offline silhouette check.
- Retired writers still on disk (`item_icons.py`, `canon_sprites.py`, `legacy_sprites.py`,
  `spell_icons.py`) are fenced by the protected-marker guard; deleting them is optional hygiene.

---

## 7. Plan (in order)

1. **Hold-class display system** — `tools/item_display.py`: hold classes, bounds-normalised
   transforms, geo frame corrected (V1, V2, V4). `item_models_3d.py` writes it for every cuboid
   (display-only rewrite mode, geometry and sheets untouched).
2. **GeckoLib in-hand bases** — `item_geo.py` writes `models/item/<id>_held.json` (transforms only,
   from geo bounds); `ModModelProvider.iconInSlotGeoInHand` points `base` at it. Removes S1, S2 (geo
   items), fixes V2, V3, V7. Delete the 11 dead cuboids/sheets.
3. **Wand** — framed GUI/frame/ground transforms, angled first person, no `builtin/entity` (V5);
   wood silhouettes as **data**: optional `appearance` on `wand_woods/*.json` (tint + handle/shaft/tip
   module ids), read on the client through the synced registry; `WandAppearance`'s hard-coded table
   moves into those files; an explicit `WAND_CONFIGURATION` on a stack still wins. Glow layer for the
   core (S8). Wand shapes themselves stay untouched (frozen by the author).
4. **Books** — rebuilt `book()`: boards overhang an inset page block (fore edge, head, tail),
   rounded spine with raised bands, cover art projected from the item's own 16×16 icon onto the
   front board so the 3D book and its icon are the same object (V6).
5. **Card, cloaks, small fixes** (V8, V9, S6 fade frame).
6. **Hygiene** — S3 deletions, S4 flat duplicates to datagen, doc fixes (S10).
7. **Validation** — re-run the capture on all 206 items; before/after sheets; `./gradlew build`.

Out of scope, recorded: per-potion icons (S7, owner decision), the `_3d`/`model/` naming (S9), worn
armour models, entity textures.

---

## Appendix — every captured item

Views: FP = first person, TP = third person. "OK" = reads correctly in all six views.

| Class | Item | Strategy (today) | Generator / builder | Verdict (in-game) |
|---|---|---|---|---|
| 3D functional object | `bertie_botts_every_flavour_beans` | flat icon / cuboid in hand | beanbox | FP microscopic, low centre (V1) |
| 3D functional object | `brass_scales` | flat icon / cuboid in hand | scales | FP microscopic, low centre (V1) |
| 3D functional object | `broomstick_servicing_kit` | flat icon / cuboid in hand | crate | FP microscopic, low centre (V1) |
| 3D functional object | `collapsible_cauldron` | flat icon / cuboid in hand | cauldron | FP microscopic, low centre (V1) |
| 3D functional object | `dungbomb` | flat icon / cuboid in hand | sac | FP microscopic, low centre (V1) |
| 3D functional object | `exploding_snap` | flat icon / cuboid in hand | cards | FP microscopic, low centre (V1) |
| 3D functional object | `filibusters_fireworks` | flat icon / cuboid in hand | firework | FP microscopic, low centre (V1) |
| 3D functional object | `hermiones_beaded_bag` | flat icon / cuboid in hand | pouch | FP microscopic, low centre (V1) |
| 3D functional object | `portable_swamp` | flat (generated[vanilla]) | sac | vanilla behaviour, OK |
| 3D functional object | `pukwudgie_venom_sac` | flat icon / cuboid in hand | sac | FP microscopic, low centre (V1) |
| 3D functional object | `revealer` | flat icon / cuboid in hand | bar | FP microscopic, low centre (V1) |
| 3D functional object | `secrecy_sensor` | flat icon / cuboid in hand | device | FP microscopic, low centre (V1) |
| 3D functional object | `self_stirring_cauldron` | flat icon / cuboid in hand | cauldron | FP microscopic, low centre (V1) |
| 3D functional object | `skiving_snackbox` | flat icon / cuboid in hand | crate | FP microscopic, low centre (V1) |
| 3D functional object | `spellotape` | flat icon / cuboid in hand | tape | FP microscopic, low centre (V1) |
| 3D functional object | `wildfire_whiz_bangs` | flat icon / cuboid in hand | firework | FP microscopic, low centre (V1) |
| 3D functional object | `wizarding_wireless` | flat icon / cuboid in hand | radio | FP microscopic, low centre (V1) |
| 3D functional object | `wizards_chess_set` | flat icon / cuboid in hand | chess | FP invisible |
| 3D inventory item | `bludger` | flat (generated[vanilla]) | ball | vanilla behaviour, OK |
| 3D inventory item | `canary_cream` | flat (generated[vanilla]) | sweet | vanilla behaviour, OK |
| 3D inventory item | `chocolate_bar` | flat icon / cuboid in hand | bar | FP microscopic, low centre (V1) |
| 3D inventory item | `da_galleon` | flat icon / cuboid in hand | coin | FP microscopic, low centre (V1) |
| 3D inventory item | `dirigible_plum` | flat (generated[vanilla]) | plum | vanilla behaviour, OK |
| 3D inventory item | `droobles_best_blowing_gum` | flat icon / cuboid in hand | gumpack | FP microscopic, low centre (V1) |
| 3D inventory item | `fainting_fancies` | flat icon / cuboid in hand | pastille | FP microscopic, low centre (V1) |
| 3D inventory item | `fever_fudge` | flat icon / cuboid in hand | fudge | FP microscopic, low centre (V1) |
| 3D inventory item | `fizzing_whizzbee` | flat (generated[vanilla]) | whizzbee | vanilla behaviour, OK |
| 3D inventory item | `gobstones` | flat (generated[vanilla]) | ball | vanilla behaviour, OK |
| 3D inventory item | `gubraithian_fire` | flat icon / cuboid in hand | flame | FP microscopic, low centre (V1) |
| 3D inventory item | `nosebleed_nougat` | flat icon / cuboid in hand | nougat | FP microscopic, low centre (V1) |
| 3D inventory item | `peppermint_toad` | flat (generated[vanilla]) | toad | vanilla behaviour, OK |
| 3D inventory item | `puking_pastilles` | flat icon / cuboid in hand | pastille | FP microscopic, low centre (V1) |
| 3D inventory item | `pumpkin_pasty` | flat icon / cuboid in hand | pasty | FP microscopic, low centre (V1) |
| 3D inventory item | `quaffle` | flat (generated[vanilla]) | ball | vanilla behaviour, OK |
| 3D inventory item | `ton_tongue_toffee` | flat icon / cuboid in hand | toffee | FP microscopic, low centre (V1) |
| 3D inventory item | `treacle_tart` | flat icon / cuboid in hand | tart | FP microscopic, low centre (V1) |
| 3D inventory item | `u_no_poo` | flat icon / cuboid in hand | capsule | FP microscopic, low centre (V1) |
| Armour | `auror_robe_boots` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `auror_robe_chest` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `auror_robe_legs` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `blindfold` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask_1` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask_2` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask_3` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask_4` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_mask_5` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_robe_boots` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_robe_chest` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `death_eater_robe_legs` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `dragon_hide_gloves` | flat icon / cuboid in hand | glove | FP invisible (below view) |
| Armour | `earmuffs` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `headless_hat` | flat icon / cuboid in hand | hat | FP microscopic, low centre (V1) |
| Armour | `quidditch_robes` | flat icon / cuboid in hand | cloth_fold | FP microscopic, low centre (V1) |
| Armour | `shield_cloak` | flat icon / cuboid in hand | cloth_fold | FP microscopic, low centre (V1) |
| Armour | `shield_gloves` | flat icon / cuboid in hand | glove | FP invisible |
| Armour | `shield_hat` | flat icon / cuboid in hand | hat | FP microscopic, low centre (V1) |
| Armour | `sorting_hat` | flat icon / cuboid in hand | hat | FP microscopic, low centre (V1) |
| Armour | `student_robe_boots` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `student_robe_chest` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `student_robe_legs` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Armour | `wizard_hat` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Book/document | `a_history_of_magic` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `advanced_potion_making` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `beginners_guide_to_transfiguration` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `bestiary` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `daily_prophet` | flat icon / cuboid in hand | newspaper | FP microscopic, low centre (V1) |
| Book/document | `fantastic_beasts_and_where_to_find_them` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `hogwarts_a_history` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `howler` | flat icon / GeckoLib in hand | envelope | FP too high (V2) |
| Book/document | `magical_draughts_and_potions` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `marauders_map` | flat icon / GeckoLib in hand | item_geo | FP big parchment near camera, acceptable |
| Book/document | `ministry_handbook` | flat icon / GeckoLib in hand | book | reads as an open book - good |
| Book/document | `ministry_license_scroll` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Book/document | `monster_book_of_monsters` | flat icon / GeckoLib in hand | book | chunky box, acceptable |
| Book/document | `moste_potente_potions` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `one_thousand_magical_herbs_and_fungi` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `parchment` | flat icon / cuboid in hand | scroll | FP microscopic, low centre (V1) |
| Book/document | `quidditch_through_the_ages` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `rise_and_fall_of_the_dark_arts` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `secrets_of_the_darkest_art` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `standard_book_of_spells` | flat icon / GeckoLib in hand | book | reads as an open book - good |
| Book/document | `tales_of_beedle_the_bard` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Book/document | `the_quibbler` | flat icon / cuboid in hand | newspaper | FP microscopic, low centre (V1) |
| Book/document | `torn_spell_page` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Book/document | `unfogging_the_future` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Detailed flat | `baby_mandrake` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `conjured_spoiled_food` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `duelling_dummy` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `floo_powder` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `flying_ford_anglia` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `gryffindor_banner` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `hufflepuff_banner` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `knight_bus` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `mandrake` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `mandrake_seeds` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `ravenclaw_banner` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `slytherin_banner` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Detailed flat | `thestral_carriage` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Handheld | `auto_answer_quill` | flat icon / cuboid in hand | quill | FP: flat gold plank, no plume visible (ROD) |
| Handheld | `beaters_bat` | flat icon / cuboid in hand | bat | FP oversized plank (V4) |
| Handheld | `broom` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `cleansweep_seven` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `comet_260` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `firebolt` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `firebolt_supreme` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `nimbus_2000` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `nimbus_2001` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `oakshaft_79` | flat icon / GeckoLib in hand | item_geo | FP fills the right of the screen, bristles cut off (V3) |
| Handheld | `probity_probe` | flat icon / cuboid in hand | rod | FP oversized plank (V4) |
| Handheld | `punching_telescope` | flat icon / cuboid in hand | telescope | FP oversized plank (V4) |
| Handheld | `quick_quotes_quill` | flat icon / cuboid in hand | quill | FP: flat green plank (ROD) |
| Handheld | `quill` | flat icon / cuboid in hand | quill | FP: flat plank (ROD) |
| Handheld | `sword_of_gryffindor` | flat icon / cuboid in hand | sword | FP oversized plank (V4) |
| Handheld | `telescope` | flat icon / cuboid in hand | telescope | FP oversized plank (V4) |
| Magical artifact | `dark_mark_brand` | flat icon / cuboid in hand | rod | FP oversized plank (V4) |
| Magical artifact | `deathly_hallow_cloak` | flat icon / cuboid in hand | cloth_fold | reads as a small white box |
| Magical artifact | `deluminator` | flat icon / GeckoLib in hand | item_geo | FP tall white column, acceptable |
| Magical artifact | `foe_glass` | flat icon / GeckoLib in hand | mirror | FP: thin dark slab |
| Magical artifact | `goblet_of_fire` | flat (generated[vanilla]) | goblet | vanilla behaviour, OK |
| Magical artifact | `golden_egg` | flat (generated[vanilla]) | egg | vanilla behaviour, OK |
| Magical artifact | `golden_snitch` | flat icon / GeckoLib in hand | snitch | FP above the view, TP floats over the fist (geo +8px, V2) |
| Magical artifact | `hand_of_glory` | flat icon / cuboid in hand | hand_of_glory | FP microscopic, low centre (V1) |
| Magical artifact | `horned_serpent_gem` | flat icon / cuboid in hand | gem | FP microscopic, low centre (V1) |
| Magical artifact | `hufflepuffs_cup` | flat icon / cuboid in hand | trophy | FP microscopic, low centre (V1) |
| Magical artifact | `invisibility_cloak` | flat icon / cuboid in hand | cloth_fold | reads as a small white box |
| Magical artifact | `marvolo_gaunts_ring` | flat (generated[vanilla]) | ring | vanilla behaviour, OK |
| Magical artifact | `mirror_of_erised` | flat icon / GeckoLib in hand | mirror | vanilla behaviour, OK |
| Magical artifact | `omnioculars` | flat icon / GeckoLib in hand | binoculars | vanilla behaviour, OK |
| Magical artifact | `opal_necklace` | flat (generated[vanilla]) | necklace | vanilla behaviour, OK |
| Magical artifact | `pensieve` | flat icon / GeckoLib in hand | basin | vanilla behaviour, OK |
| Magical artifact | `philosophers_stone` | flat icon / GeckoLib in hand | gem | FP cut off at top, TP floating (V2) |
| Magical artifact | `portkey` | flat icon / GeckoLib in hand | item_geo | FP too high/large (V2) |
| Magical artifact | `ravenclaws_diadem` | flat icon / GeckoLib in hand | diadem | vanilla behaviour, OK |
| Magical artifact | `remembrall` | flat icon / GeckoLib in hand | orb | FP cut off at top (V2) |
| Magical artifact | `resurrection_stone` | flat icon / GeckoLib in hand | gem | vanilla behaviour, OK |
| Magical artifact | `riddles_diary` | flat icon / cuboid in hand | book | FP microscopic, low centre (V1) - plain boxes, no cover art (V6) |
| Magical artifact | `shrunken_head` | flat icon / cuboid in hand | shrunken_head | FP microscopic, low centre (V1) |
| Magical artifact | `slytherins_locket` | flat icon / GeckoLib in hand | locket | FP: tall yellow column (chain edge-on) |
| Magical artifact | `time_turner` | flat icon / GeckoLib in hand | hourglass | vanilla behaviour, OK |
| Magical artifact | `triwizard_cup` | flat icon / cuboid in hand | trophy | FP microscopic, low centre (V1) |
| Magical artifact | `two_way_mirror` | flat icon / cuboid in hand | mirror | FP microscopic, low centre (V1) |
| Magical artifact | `vanishing_cabinet` | flat icon / cuboid in hand | cabinet | FP microscopic, low centre (V1) |
| Potion/brew | `acromantula_venom` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `blood_pact_vial` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `blood_replenishing_potion` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `broom_polish` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `butterbeer` | flat icon / cuboid in hand | bottle | FP microscopic, low centre (V1) |
| Potion/brew | `dittany` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `draught_of_peace` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `elixir_of_life` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `empty_butterbeer_mug` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Potion/brew | `essence_of_dittany` | flat icon / cuboid in hand | flask | FP microscopic, low centre (V1) |
| Potion/brew | `firewhisky` | flat icon / cuboid in hand | bottle | FP microscopic, low centre (V1) |
| Potion/brew | `flesh_eating_slug_repellent` | flat icon / cuboid in hand | bottle | FP microscopic, low centre (V1) |
| Potion/brew | `ghoul_slime` | flat (generated[vanilla]) | vial | vanilla behaviour, OK |
| Potion/brew | `hidebehind_shadow_essence` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `ink_bottle` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `matagot_essence` | flat (generated[vanilla]) | vial | vanilla behaviour, OK |
| Potion/brew | `memory_vial` | flat icon / cuboid in hand | vial | FP microscopic, low centre (V1) |
| Potion/brew | `mrs_skowers_mess_remover` | flat icon / cuboid in hand | bottle | FP microscopic, low centre (V1) |
| Potion/brew | `murtlap_essence` | flat icon / cuboid in hand | flask | FP microscopic, low centre (V1) |
| Potion/brew | `pumpkin_juice` | flat icon / cuboid in hand | bottle | FP microscopic, low centre (V1) |
| Special rendered | `brew` | flat icon / cuboid in hand | vial | tint works in all views; FP microscopic (V1) |
| Special rendered | `demiguise_hair` | range dispatch | - | OK |
| Special rendered | `famous_wizard_card` | component select | - | item frame shows the card back (fixed rot); GUI shows bare base card without component |
| Special rendered | `galleon` | flat icon / GeckoLib in hand | item_geo | FP/TP near-invisible (scale 0.15, builtin/entity base) |
| Special rendered | `knut` | flat icon / GeckoLib in hand | item_geo | FP/TP invisible (scale 0.05) |
| Special rendered | `sickle` | flat icon / GeckoLib in hand | item_geo | FP/TP invisible (scale 0.1) |
| Special rendered | `sneakoscope` | flat icon / GeckoLib in hand | item_geo | FP cut off at top, TP floating (V2) |
| Vanilla-style flat | `abraxan_spawn_egg` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `acromantula_spawn_egg` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `basilisk_fang` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `bezoar` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `chocolate_frog` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `counterfeit_galleon` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `decoy_detonator` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `devils_snare` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `dragon_heartstring` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `dragot` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `enchanted_twig_bundle` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `erumpent_horn` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `extendable_ears` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `gillyweed` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `golden_snidget_feather` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `granian_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `hidebehind_claw` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `leprechaun_gold` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `mallowsweet` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `mooncalf_dung` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `occamy_eggshell` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `peruvian_instant_darkness_powder` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `phoenix_feather` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `rougarou_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `thestral_tail_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `thunderbird_tail_feather` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `troll_whisker` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `unicorn_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `veela_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `wampus_cat_hair` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `white_river_monster_spine` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Vanilla-style flat | `yeti_fur` | flat (generated[vanilla]) | - | vanilla behaviour, OK |
| Wand | `debug_wand` | flat (generated[vanilla]) | - | dev item; flat handheld, fine |
| Wand | `morph_wand` | flat (generated[vanilla]) | - | dev item; flat handheld, fine |
| Wand | `wand` | GeckoLib everywhere | item_geo | GUI/frame: thin 1-px diagonal; FP: vertical pole, top cut off; every wood same shape (V5) |
| Wand | `wand_blank` | flat (handheld[vanilla]) | - | flat handheld, reads as a stick (fine) |