# Hamlet Architecture — Wizards & Beasts

How a wizarding hamlet's buildings look, and why. Source of truth: `tools/gen_hamlet_nbt.py` (local only, never
pushed); its output, `data/wizards_and_beasts/structure/wizarding_hamlet/<role>/a.nbt`, is committed. Re-run the
generator after any change; it writes gzip without a timestamp, so an untouched template stays byte-identical.

Only hamlets generated after a change get it. Placed hamlets keep their blocks: a template change never reaches a
chunk that already exists.

## 1. Inventory (2026-11-03)

| Template | Size | Building | Pool variants |
|---|---|---|---|
| plaza | 25x34x25 | Crooked Spire, cobble rings, 4 lamp posts, 2 benches, basin, notice board | 1 |
| keeper | 11x15x18 | General store | 1 |
| wandmaker | 11x18x18 | Wand shop | 1 |
| apothecary | 11x21x18 | Apothecary over a cellar and the Undercroft | 1 |
| reading_room | 11x15x18 | The Scholar's reading room | 1 |
| bank | 11x15x18 | Gringotts branch counter | 1 |
| open_plot | — | Empty pool element | 1 |

Each house is one shell, `house()`: a stone ground storey, a 1-block jetty, a roof, a chimney, the counter and the role's
`settlement_post`. The street is template south; the building connector at (5, g+1, 17) turns it to face the plaza.

**Fixed points other code reads** (do not move): the `spruce_door` lower half at (5, g+1, 12) (`SettlementWorldProbe`
finds entrances by it; the customer stands 5 north); the post at (5, g, 4); the counter at z 6; the 11x18 footprint;
the interior x1..9 z2..11 (checked for intrusions); 32 sign lang lines (`SettlementLayoutTest`). `SettlementRooms`
clears vegetation from any template air with template above it, so overhangs are safe.

## 2. What was weak

From the alpha-pass shots (`C:\tmp\wb-lw-alpha\evidence\lake-run2`) and a same-site "before" run:

1. One silhouette five times; the two north houses read as twin roofs side by side.
2. Flat fronts: wall, two 2x2 glass blocks, a door. No depth, porch or shopfront.
3. Upper storey and gable a plain plank triangle with two 1x1 panes.
4. A 1-block spruce sign off-centre beside the door, unreadable from the plaza.
5. Function unreadable: only a coloured plank skirt; keeper and apothecary were both green.
6. Fronts unlit at night; only the plaza lamps.
7. The bank, the most important building, was the same timber house in marble.

The plaza and the Spire already had a strong identity and were left alone.

## 3. The shared language

Hogsmeade family (style guide §9): cold grey stone, slate roof, red chimney brick, dark timber. Every shop front is
built from the same pieces:

| Piece | Function | Blocks |
|---|---|---|
| Shopfront | posts at x 0/4/6/10 under the jetty; a 3-pane display bay each side on a riser; the old wall behind opens into a display shelf | `three_broomsticks_timber`, riser per building, `glass_pane` |
| Porch | door set back one block, lantern hung from the beam | `lantern` (hanging) |
| Gilt sign | the role's sign, centred on the beam over the porch; dark board, glowing gilt text | `dark_oak_wall_sign` |
| Jetty windows | two 1x2 casements, open shutters, a sill | `glass_pane`, `spruce_trapdoor` |
| Gable front | king post, collar beam, loft panes; roof carried one block past the wall (eave) | timber, `hogsmeade_roof_tile(_stairs)` |

Panes are stored joined (`T.connect_panes`), since placement keeps a template's states.

## 4. Building identity

| Building | Silhouette | Front |
|---|---|---|
| Keeper (general store) | 45° front gable | green riser; a bit of everything in the windows (pots, candle, lantern, azalea); barrels, a pumpkin and a tulip at the ends of the front |
| Wandmaker | steep front gable (pitch 2), the tallest house | purple riser; chiseled bookshelves stacked as wand boxes in the windows, purple and white candles; a dark-oak bench to wait on |
| Apothecary | 45° front gable over its cellar | brown riser, bottle-green glass; brewing stand, herbs and candles in the windows; potted herbs at the door; a lit campfire tops the chimney, so smoke rises day and night |
| Reading Room | the only ridge along the street, with a dormer | shopfront-plank riser; lecterns, candles and flowers in the windows |
| Bank | marble temple front | four marble columns and antae under one gilt band; marble upper storey; marble-tile pediment with a marble cornice; a podium to the street |

## 5. New assets

None. No block, texture or registration was added. New uses of existing blocks:

- Mod: `gringotts_white_marble_tiles`, `gringotts_white_marble_stairs` (bank), `diagon_shopfront_wood` (apothecary riser).
- Vanilla: `glass_pane`, `green_stained_glass_pane`, `dark_oak_wall_sign`, `spruce_trapdoor`, `spruce_slab`, `dark_oak_stairs`,
  `lantern`, `campfire`, `chiseled_bookshelf`, `lectern`, `brewing_stand`, `decorated_pot`, coloured `candle`s, potted plants,
  `barrel`, `pumpkin`.

Block entities added per hamlet: one sign moved (none new), 2 decorated pots (keeper), 4 chiseled bookshelves
(wandmaker), 1 brewing stand + 1 decorated pot + 1 campfire (apothecary), 2 lecterns (reading room). Of these only the
campfire ticks. No entities.

## 6. Validation (2026-11-03)

- **Unit tests:** `settlement.*`, 71 run, 0 failed, on the final templates.
- **World probe** (`WB_SETTLEMENT_PROBE=survey`, seed 1234567, 10 sites, all four rotations and all six biomes the
  hamlet uses): 10 of 10 clean. Per site: every template block placed (0 changed), no floor or cellar over nothing, 5
  doors walked through, 5 counters reached, all 4 sides left, 5 entrances, 5 roles. Positive control: the probe counted
  6,554 template blocks, the new templates' total (the old was 6,086).
  Run before the bank's last change (one gilt course swapped back to marble, same positions).
- **Real client** (hidden desktop, seed 1234567, site 858, -1070): same site before (all old) and after, day and night;
  all five people at their posts each run. The tour (`HamletPlaytest`, part `tour`) now also shoots
  `night_exterior_<role>`.

Screenshots (local, not committed): `C:\tmp\wb-visual-shots\` — `v2` before, `v3` Keeper prototype, `v5` final.

## 7. Remaining debt

- One variant per role. Pools take more; two shells per role would end the repeat between hamlets.
- The open plot is empty. A small garden or market stall would close the south side.
- Backs and sides are the old shell; only fronts were worked. From outside the hamlet the backs are seen first.
- The display windows hold blocks, not wares: no item frames (entities) were used. Real stock behind glass would need a
  block-entity renderer.
- The apothecary's green glass hides most of its display.
- Signs are vanilla wall signs; a hanging sign would need shorter lang lines (hanging signs fit about 10 characters).
- Doors stay `spruce_door`: `SettlementWorldProbe` finds entrances by that block. Varying doors means teaching it the
  door tag first.
- `three_broomsticks_timber` reads as dark brick at a distance (Visual Consistency Audit M1), which flattens the framing.
