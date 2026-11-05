# Wardrobe Renaissance — robes, uniforms and faction clothing

Status: **Phase A (audit) and Phase B (design system) written 2026-11-04.** Owner accepted all four
recommendations in §7 the same day (house stamped on the clothing, rank on the stack, canon ids with original
motifs, 16 px). Phase C in progress on `feat/wardrobe`.

Visual reference: the owner's concept sheet "Robe Redesign – Concept Art" (2026-11-04). It sets the
direction: dark wool bodies, metal trim, a readable emblem on the chest, capes with heraldic panels, rank shown
by added pieces rather than by colour alone. The sheet's house crests and skull insignia show the *kind* of
detail we want, not motifs to copy. Every emblem, mask and insignia here is drawn new (§7, D3).

Builds on: `EQUIPMENT_ARCHITECTURE.md` (Curios layer), `VISUAL_STYLE_GUIDE.md`, `VISUAL_RENAISSANCE.md`
(D11, H7), and `tools/armor_model.py` (the generator that owns every worn rig and sheet).

---

## Phase A — Audit of the existing clothing architecture

### A1. What exists (dev @ fad21b04)

| Piece | Items (ids are frozen) | Worn as | Model / texture | Stats (data map) |
|---|---|---|---|---|
| Student robe | `student_robe_chest`, `_legs`, `_boots` | chest = Curios `body`; legs/boots = vanilla armour | `armor/student_robe.geo.json`, `textures/armor/student_robe.png` | chest: armor 3 |
| Auror robe | `auror_robe_chest`, `_legs`, `_boots` | same split | `auror_robe` | chest: armor 6 |
| Death Eater robe | `death_eater_robe_chest`, `_legs`, `_boots` | same split, hood up/down (`hood_up` component, `H` key) | `death_eater_robe` | chest: armor 6, toughness 1, KB 0.05 |
| Wizard hat | `wizard_hat` | Curios `head` | `wizard_hat` | armor 1 |
| Sorting Hat | `sorting_hat` | Curios `head` | `sorting_hat` | armor 1 |
| Masks | `death_eater_mask`, `_1`..`_5` | Curios `face` | one sculpt, 6 alt textures | — |
| Cloaks | `invisibility_cloak`, `deathly_hallow_cloak` | Curios `back` | **no worn model** | — |

The code path for each piece is the same:

```
Item (WizardArmorItem, Wear.CURIO|ARMOR)
  └─ createGeoRenderer → ClientClassBridge → <Set>Renderer extends WizardArmorRenderer / HoodedArmorRenderer
        └─ WizardArmorGeoModel("<set>")  →  geckolib/models/armor/<set>.geo.json + textures/armor/<set>.png
Curios slot ──► WornGeoCurioRenderer (one for all) ──► same renderer, CapturedBodyPose ticket
Armour slot ──► GeckoLib HumanoidArmorLayer mixin   ──► same renderer
Hiding        ► EquipmentRenderStateModifier (wearables.json "hides")
```

Supporting pieces: `wearables.json` data map (type, hides, faction, attributes), `EquipmentVisibility`,
`EquipmentMigration` (legacy armour-slot pieces to Curios on login), `CuriosReloadGuard`, `RobeHood` +
`HoodToggleC2SPayload`. Art: `tools/armor_model.py` (rigs + painter), `armor_preview.py` (offline worn
renders + silhouette), `armor_item_icons.py`, `boxuv.py`, `uv_check.py`. Tests: `ArmorGeoAssetTest`
(root bones, parents, y24 hood floor, icon per item), `ArmorRendererBindingTest`, `WearableDataTest`,
`EquipmentVisibilityTest`, game tests `EquipmentTests`. Capture: `EquipmentShowcaseCapture`
(`WB_EQUIPMENT_SHOWCASE=1|cast`).

The foundation is sound: real armour and clothing coexist, hiding is per frame, rendering is one shared
path, authority is server-side. The redesign keeps all of it.

### A2. Limitations

| # | Limitation | Evidence | Consequence for the redesign |
|---|---|---|---|
| L1 | **The three robes read as one outfit.** Student charcoal-navy, Auror midnight blue, Death Eater black sit in one value band; trims are tonal. | VR-0 `player_equipment.jpg`; VISUAL_CONSISTENCY_AUDIT H7 | Identity must come from silhouette and high-contrast trim, not hue. |
| L2 | **No variant axis on a stack.** A new colour, house or rank is a new item, a new renderer class, a new geo and a new sheet (EQUIPMENT_ARCHITECTURE §8). | `StudentRobeRenderer` javadoc: house variants "left undone" | Needs a data-driven livery + rank component (§B3). |
| L3 | **No house, no dark-faction membership.** "The mod has no house system" (MAGICAL_ARTEFACT_STATUS). `wearables.faction` is read by nothing. Career ranks exist (`career_tracks/auror.json`: trainee → auror → senior_auror …) but nothing visual reads them. | grep; `CareerProgression.promote` | Identity must be stamped on the clothing, or a house system built first (D1, D2). |
| L4 | **Cloth never moves.** `robeSkirt`/`robeHem`/panels are child bones "so a controller can animate them later"; nothing does. Legs swing out of a rigid skirt; on a broom the thighs are expected to pierce the front panel (not yet captured). | `armor_model.py` header; VR-0 walk/sneak frames came out black | Procedural cloth from the captured pose (§B5). |
| L5 | **The back slot draws nothing.** Cloaks are worn but invisible on the body; no cape model exists. | `EQUIPMENT_ARCHITECTURE` §8 "only once the cloak has a drawn model" | Cape cut + elytra rule (§B5). |
| L6 | **Belt, hands, ring, charm slots are empty.** No belts, pouches or gloves exist. | slot table §2 | Accessories reuse these slots; no new slots needed. |
| L7 | **Sets are three items with a mixed wear model.** Body = clothing, trousers/boots = armour. | `WizardArmorItem.robeWear` | Keep for the three legacy sets; new cuts ship the body only (§B4). |
| L8 | **Unobtainable in survival.** No recipe, shop or NPC references any robe id; creative and commands only. | grep over `data/` | Acquisition is a Phase E item (§B8). |
| L9 | **First person shows a bare arm.** Same as vanilla armour. | VR-0 capture | Optional sleeve cuff in Phase E. |
| L10 | **Only one piece has a hood**, and only `HoodedArmorRenderer` knows the bone-visibility pattern. | code | Generalise to "toggled bone groups" (rank, hood, emblem). |
| L11 | **Settlement people wear their clothes in their skin.** `SettlementNpcRenderer` is a `HumanoidModel` with one skin per person. | renderer | They could wear wardrobe pieces through the same renderer later (Phase E, optional). |
| L12 | **Canon names and motifs.** Items are called Death Eater / Auror; house banners exist. The brief asks for original designs. | registry, lang | IDs stay; new emblems, insignia and masks are original (D3). |

### A3. Compatibility risks

| Risk | Rule we keep |
|---|---|
| Item ids in saves, Curios inventories, recipes of other packs | No id is renamed or removed. New cuts get new ids. |
| Stacks saved without the new component | Absent component = today's look ("default livery, rank 0"). No migration pass needed. |
| `hood_up` component and `H` toggle | Unchanged; the generalised bone-toggle keeps `robeHood`/`robeHoodDown` names. |
| Stats pinned by `WearableDataTest` | Stats belong to the cut, never to livery or rank. A test asserts that. |
| Bone contract (`ArmorGeoAssetTest`) | Root bones stay the eight GeckoLib names; new bones hang below them; the y24 head floor stays. |
| Curios 14.0.0 `/reload` slot wipe | Nothing new touches slot definitions except, possibly, a necklace size bump (D-later). |
| `runData` overwriting hand art | New items get model/lang lines; stage only real files, reset CRLF noise in `src/generated`. |
| Legacy armour-slot pieces | `EquipmentMigration` already moves them; new cuts are Curios-only. |
| Multiplayer | Livery/rank live on the stack, which Curios syncs to every tracking client. No new packets. |
| Performance | One extra bone pass at most (emblem). Cloth motion is a few float writes per piece per frame. |

---

## Phase B — Design system

### B1. Visual language (applies to every piece)

1. **Silhouette first.** At 10 blocks each faction must be told apart in black on white. Every cut gets a
   silhouette check in `armor_preview.py` before any colour work.
2. **Three tones per cloth, one trim, one metal.** Body cloth (dark, ramped, pleated), lining/trim (the
   identity colour, *high contrast against the body*), metal (gold, silver, bronze or gunmetal) for clasps,
   buttons, insignia only.
3. **Rank adds pieces, it does not repaint.** Trainee → master gains shoulder boards, collar pins, a mantle, a
   longer hem, fur. The base colour never changes with rank.
4. **Density:** 16 texels per block, like the player skin and the rest of the mod. Sheets stay at 64 or 128
   px (`SHEET_SIZES`). No HD: mixing densities on one body reads as a mod clash (D4).
5. **Painting rules already learned** (`armor_model_quality_pass`): A-line by rotated 2-px panels, not stacked
   boxes; drape undercuts are dark, never lit; mottle ≤ 5 %; integer cube sizes; detail cubes clear their shell;
   nothing a chest piece puts above the shoulders may sit inside the 8³ head.
6. **Emblems are 8×8 or 6×8 plates**, drawn by hand in the generator, two-tone plus metal outline, readable
   at 4 blocks. All original.

### B2. Palette

Main fabrics (body cloth, mid tone; the painter ramps ±2 steps):

| Token | Hex | Used by |
|---|---|---|
| `wool_black` | `#1E1C24` | school, dark faction (desaturated), official |
| `wool_navy` | `#1F2742` | Auror |
| `wool_oxblood` | `#5A1C22` | house trim, master |
| `wool_forest` | `#1F3A2B` | house trim, alchemist |
| `wool_plum` | `#3B2350` | professor, ministry |
| `linen_cream` | `#D9CDB0` | healer, shirts, linings |
| `canvas_umber` | `#5B4330` | traveller, researcher |
| `twill_slate` | `#3E4651` | everyday, merchant |

Accents and trim:

| Token | Hex | Token | Hex |
|---|---|---|---|
| `trim_ochre` | `#C8972F` | `metal_gold` | `#D2A445` |
| `trim_ash` | `#A9A6A0` | `metal_silver` | `#BFC4CC` |
| `trim_scarlet` | `#8E2328` | `metal_bronze` | `#8F5A2C` |
| `trim_emerald` | `#2E6B47` | `metal_gunmetal` | `#4C525A` |
| `trim_sapphire` | `#2D4E8E` | `bone` | `#D8D0BC` |
| `trim_saffron` | `#D9A520` | `leather` | `#6B4528` |

Value bands (the fix for H7): school = black body + bright trim; Auror = mid-dark blue + gunmetal, no gold;
dark faction = near-black, *desaturated*, bone/ash accents only; everyday/traveller = mid values, earth hues;
professor/ministry = jewel tones; healer = light value. Validated by a greyscale contact sheet in Phase D.

### B3. Technical architecture — Cut × Livery × Marks

Three axes, each with one home:

```
CUT     = silhouette   → an item + one .geo.json + one base sheet        (code + generator; few, deliberate)
LIVERY  = identity     → datapack entry: trim tint, lining tint, metal, emblem, optional base colourway
MARKS   = rank/state   → small integers on the stack: rank 0..3, hood up, (later) brooch
```

**Stack component** `wizards_and_beasts:outfit` (network-synchronised, optional):

```json
{ "livery": "wizards_and_beasts:house_lion", "rank": 2 }
```

Absent = the cut's default livery and rank 0, i.e. exactly what an old stack looks like today after the art
refresh. `hood_up` stays its own component.

**Livery registry** — a datapack registry like `wand_woods`, at
`data/<ns>/wizards_and_beasts/livery/<id>.json`:

```json
{
  "cuts": ["wizards_and_beasts:school_robe", "wizards_and_beasts:scholar_gown"],
  "trim": "#8E2328",
  "lining": "#C8972F",
  "metal": "gold",
  "emblem": "wizards_and_beasts:lion",
  "colourway": null,
  "faction": "school_house_lion"
}
```

- `cuts` limits which cuts may carry it (a house livery on an Auror coat is refused).
- `trim`/`lining` are **tints applied per bone**: the generator paints trim and lining cubes as neutral light
  ramps, and the renderer tints only those bones (`DyeableGeoArmorRenderer` pattern in GeckoLib 5.4.5:
  `isBoneDyeable` + `getColorForBone(renderState, bone, …)`). One texture, one draw, any number of liveries.
- `metal` picks one of four pre-painted metal bone groups (tinting metal looks like plastic; it is baked).
- `emblem` names `textures/armor/emblem/<id>.png` (16×16). It is drawn on the cut's `emblem` bone(s) by
  GeckoLib's `CustomBoneTextureGeoLayer` with `getTextureResource` read from the render state. Resource packs can
  add emblems without code.
- `colourway` optionally names an alternative baked base sheet (`<cut>__<colourway>.png`), for the few cuts
  where the body itself changes (healer whites, regional everyday robes). Used sparingly; it is the only
  axis that costs a texture.

**Marks** are bone groups in the cut, shown by count: `rank_1`, `rank_2`, `rank_3` (each cumulative),
`hood_up`/`hood_down`. One generalised `ToggledBoneRenderer` replaces the hood-only logic: every frame it sets
`skipRender`/`skipChildrenRender` on each declared group from the render-state tickets (same mechanism
`HoodedArmorRenderer` proved).

**Where identity comes from (server-side only):** the stack is stamped by an issuing act — a career promotion
(`CareerProgression.promote` re-stitches a worn uniform of that track), a tailor, a recipe, or the admin command
`/wandb outfit <livery> [rank]`. Render code never asks who the wearer is, so it works for every player on
screen and costs no packets (D2).

**Renderer change:** `WizardArmorRenderer` gains the livery/mark tickets in `addRenderData` (read off the
stack, like the hood), the dye hooks, the emblem layer, and cloth motion (§B5). Per-cut renderer classes stay
three-liners. `WornGeoCurioRenderer` is unchanged apart from passing the cloth-motion values.

**Stats:** `wearables.json` stays keyed by item = cut. Livery and rank never change attributes
(`WearableDataTest` gains that assertion), so cosmetic progression carries no gameplay advantage.

### B4. Layers and fit (modular clothing without new slots)

The Curios slots already are the layer system. No new slot is needed:

| Layer (outside → in) | Slot | Pieces | Envelope (px beyond skin) |
|---|---|---|---|
| Cloak / cape | `back` | full cloak, half cape, school cape | 1.6 – 2.2 (behind the robe's back face) |
| Hood or hat | `head` | hats; hoods built into robes | hat brim ≥ 0.9; hood shell ≥ 0.7 |
| Mask | `face` | masks | 0.5 – 0.7 |
| Belt + pouches | `belt` | sash, leather belt, duty belt, bandolier | 1.2 – 1.5 at the waist |
| Gloves | `hands` | gloves, bracers | 0.3 – 0.5 on the forearm |
| Scarf | `necklace` | scarves (later; may need the slot at size 2) | 0.9 – 1.2 at the neck |
| **Robe / coat** | `body` | the cut: torso, sleeves, skirt, collar, shoulders, insignia, emblem | 0.55 – 1.1 |
| Trousers, shoes | vanilla legs/feet | legacy sets' `_legs`/`_boots`, or real armour | vanilla 0.5 / 1.0 |

Collar, shoulder decoration, faction and rank insignia are **bones of the cut**, not equipment. That keeps
them fitted to the silhouette they belong to and prevents the clipping that independent pieces would cause.

Fit rules, asserted by an extended `ArmorGeoAssetTest`:
- every waist cube of a cut ends inside the belt envelope (belts never sink into robes);
- every back cube of a cut ends in front of the cloak's inner face;
- cloak, belt and glove cubes are generated from the same envelope table as the cuts.

Combination rules (render-time, in `EquipmentVisibility`):
- hat worn **and** hood up → the hood is drawn down (the hat wins);
- elytra on the chest → the cloak is not drawn (vanilla does the same with capes); the robe stays;
- a mask hides the helmet (unchanged).

### B5. Rendering and animation

All motion is procedural, read from values vanilla already computes. No physics, no animation files.

| Motion | Source | Bones |
|---|---|---|
| Skirt swing while walking/running | captured leg `xRot` (already in `CapturedBodyPose`) | `clothLegR/L` = 0.95 × own leg (option B, see §C-look) |
| Seated (broom, mount, chair) | both legs pitched forward | `clothHipFront` = 0.8 × the forward pitch both legs share |
| Sneaking | body pitch already applied to `armorBody` | panels counter-rotate to stay vertical |
| Cape/cloak flap | `AvatarRenderState.capeFlap`, `capeLean`, `capeLean2` (vanilla cape physics, per player, free) | cloak root pitch + two hem bones |
| Swimming, elytra, riptide | `swimAmount`, `fallFlyingTimeInTicks` | skirt and cloak collapse along the body; cloak hidden with elytra |
| Hood | head bone (existing) | unchanged |

Cost: one more ticket (about six floats) per piece per frame; no allocation beyond the existing state.

First person: nothing, as today. A sleeve cuff via NeoForge `RenderArmEvent` is a Phase E option.

### B6. Clothing catalogue

Nine cuts. A cut is a silhouette with its own geometry; variety within a cut comes from livery and marks.
Each cut lists what Phase C/E must author.

**Notation:** *Geo* = new or reworked `.geo.json`; *Sheet* = base sheet size; *Bones* = toggled groups.

#### C1 — School Robe (`student_robe_chest`, existing id)
- **Look:** closed-front A-line robe to mid-calf, soft rolled collar, bell sleeves with lining turned back,
  shirt and tie visible at the throat, chest emblem left breast, lining in trim colour at hem and sleeves.
- **Silhouette:** narrow shoulders, gentle flare, sleeves wider at the wrist. The "youngest" outline.
- **Liveries:** `school_plain` (grey trim, no emblem), four original houses — `house_lion` (scarlet/ochre,
  rampant lion), `house_serpent` (emerald/ash, coiled serpent), `house_eagle` (sapphire/bronze, eagle displayed),
  `house_badger` (saffron/black, badger passant). The animals are heraldic commons; the crest drawings are ours.
- **Marks:** rank 0 Year 1–3 (plain); rank 1 Year 4–7 (piped lapels, metal badge pin); rank 2 Graduate (stole
  over the shoulders in trim colour, longer hem); rank 3 Formal (velvet yoke, metal clasp chain).
- **Accessories:** house scarf (necklace), school cape with house panel (back), graduate cap (head, later).
- **Author:** Geo rework, Sheet 128, bones `trim`, `lining`, `emblem`, `rank_1..3`; 5 emblems.

#### C2 — Auror Duelling Coat (`auror_robe_chest`, existing id)
- **Look:** open-front coat over a dark waistcoat, standing collar, double-breasted breast panel, coat tails
  split at the back for movement, reinforced forearms, gunmetal buttons. **No gold anywhere** (art direction 2026-08-26).
- **Silhouette:** squared shoulders, fitted waist, tails — the only "military" outline.
- **Livery:** `auror_office` (midnight + gunmetal + steel-grey piping), emblem: an original Ministry seal
  (eye within a key-ring).
- **Marks:** 0 Trainee (no shoulder boards, grey piping); 1 Auror (shoulder boards, one bar); 2 Senior (two bars,
  silver collar pins); 3 Elite (three bars, leather right pauldron + reinforced half-cape on the left shoulder).
- **Accessories:** duty belt with three vial pouches and a wand holster (belt), dragonhide gloves (hands).
- **Integration:** promotion on `career_tracks/auror` re-stitches a worn coat to the new rank.
- **Author:** Geo rework, Sheet 128, bones `piping`, `rank_1..3`, `emblem`; duty belt cut; gloves cut.

#### C3 — Dark Order Robe (`death_eater_robe_chest`, existing id)
- **Look:** floor-length robe with a deep hood (existing toggle), broad yoke, high collar, heavy vertical folds,
  ragged hem. Bone and ash accents, no colour.
- **Silhouette:** tallest, narrowest at the head (hood), widest at the floor — a spike.
- **Livery:** `dark_order` — an original sigil: a thorned ring broken at the top. Not a skull, not a snake.
- **Marks:** 0 Initiate (plain, hood); 1 Officer (asymmetric shoulder mantle, bone clasp); 2 Elite (tall split
  collar, layered tattered hem, ash embroidery down the front); 3 Ceremonial (trailing back panel, chain of
  bone plates across the chest).
- **Masks:** the existing six castings stay; masks remain separate face pieces.
- **Author:** Geo rework, Sheet 128, bones `rank_1..3`, `emblem` (back of the mantle), hood (existing).

#### C4 — Everyday Robe (new: `wizard_robe_chest`)
- **Look:** knee-to-calf robe worn open over a waistcoat and shirt, cuffs turned back, a cloth sash at the waist.
  Patched, practical, lived in.
- **Silhouette:** relaxed, slightly wider at the hips, open front showing two layers.
- **Liveries / colourways:** slate, moss, plum, rust; regional colourways `northern` (heavier wool, fur-edged
  collar), `coastal` (oilcloth sheen, toggles), `southern` (light linen, short sleeves over shirt).
- **Specialist variants by attachment bone:** `apron` (alchemist, artisan), `ledger_satchel` (merchant).
  These are livery-enabled bones, not separate items.
- **Marks:** none (rank 0 only).
- **Author:** new Geo, Sheet 128, 4 tints + 3 colourway sheets, bones `apron`, `satchel`.

#### C5 — Travelling Coat (new: `travel_coat_chest`)
- **Look:** knee-length duster in canvas, split back vent, short buttoned shoulder cape, leather elbow patches,
  satchel strap across the chest, many pockets.
- **Silhouette:** a shoulder cape over a straight coat — a "Y" outline.
- **Liveries:** traveller (umber/leather), researcher (olive, specimen pockets, field-journal strap), courier.
- **Author:** new Geo, Sheet 128.

#### C6 — Scholar's Gown (new: `scholar_gown_chest`)
- **Look:** full-length open gown with very wide bell sleeves that fall past the hand, a velvet yoke and a
  hood-down cowl lined in faculty colour, waistcoat beneath.
- **Silhouette:** the widest sleeves of any cut — reads as "teacher" at any distance.
- **Liveries:** faculty colours (Potions forest, Charms sapphire, Defence oxblood, Herbology ochre, Astronomy plum).
- **Marks:** 0 Lecturer; 1 Professor (metal chain across the yoke); 2 Head of House (house emblem on the
  cowl); 3 Headmaster (fur-edged yoke, longer train).
- **Accessories:** tall professor's hat with buckle band (head).
- **Author:** new Geo, Sheet 128; new hat cut (64).

#### C7 — Healer's Coat (new: `healer_coat_chest`)
- Light cream long coat with an apron panel, ward-coloured placket and armband, an original emblem (a leaf
  crossed with a wand inside a ring). Marks: trainee / healer / senior healer (collar stripe count).
  Colourway sheet (light body). Phase E.

#### C8 — Ministry Robe (new: `ministry_robe_chest`)
- Tailored closed robe in plum or charcoal, high stand collar, chain of office whose links grow with rank,
  department sash in trim colour. Liveries per department. Phase E.

#### C9 — Master's Mantle (new: `master_mantle_chest`)
- Heavy layered robe with a fur mantle over the shoulders, brocade front panel, long trailing sleeves. Earned,
  not bought: tied to legendary achievements (Mastery capstones, a Hallow, the Chamber). Liveries per
  discipline. Phase E.

#### Accessories (all original, all Phase E unless named in Phase C)

| Piece | Slot | Variants |
|---|---|---|
| Full cloak with hood | back | plain, travelling, dark order |
| Half cape (shoulder) | back | Auror elite, official |
| School cape | back | per house livery (heraldic panel) |
| Scarf | necklace | per house, plain, knitted |
| Leather belt / sash | belt | plain, sash in trim colour |
| Duty belt with vial pouches | belt | Auror |
| Alchemist's bandolier | belt | vial loops |
| Gloves | hands | dragonhide, fingerless, healer |
| Hats | head | wizard hat (existing), professor hat, wide-brim travel hat, fur cap |
| Brooches, armbands | — | a mark on the robe (component), not an item slot |

### B7. Identity integration

| Source | What it changes | How |
|---|---|---|
| School house | school robe/scarf/cape livery | stamped (no house system exists; D1) |
| Career rank (Auror, later others) | rank marks on the matching uniform | `CareerProgression.promote` re-stitches the worn piece whose livery's `faction` matches the track |
| Dark faction | dark order livery + rank | stamped by the faction's own progression when it exists; until then by recipe/admin |
| Profession / vocation | scholar faculty, healer ward, ministry department | livery choice at issue |
| Heritage | nothing on the cloth by default; heritage forms already alter the body | rule: a cut must fit every humanoid heritage scale (asserted by capture, not code) |
| Legendary achievements | master's mantle, rank 3 ceremonial pieces | granted by the achievement's own reward hook |

No piece ever grants more than its cut's stats; no livery or rank is a requirement for any gameplay gate.

### B8. Acquisition (Phase E, listed so the design closes)

Craftable base cuts at a loom-style tailoring recipe; liveries stamped at a tailor (a `tailor` npc role for the
hamlet) for Knuts; uniforms issued by career rank-ups; master pieces as achievement rewards; creative tab
`APPAREL` shows every cut × livery for builders.

### B9. Asset budget

| Phase | Geo | Base sheets | Colourway sheets | Emblems | New items | Java |
|---|---|---|---|---|---|---|
| C | 3 reworked + 2 new | 5 | 0 | 6 (4 houses, ministry seal, dark sigil) | 2 (`wizard_robe_chest`, `scholar_gown_chest`) | outfit component, livery registry, toggled-bone + dye + emblem renderer, cloth motion, `/wandb outfit`, tests |
| E | +4 cuts, +4 accessories cuts, +2 hats | +10 | ~5 | ~8 | ~14 | promotion hook, tailor role, recipes, NPC wearers (optional) |

---

## Phase C — Prototype

### C-look (2026-11-04): offline look sheet, awaiting approval

Generator `tools/wardrobe_model.py` (new; owns every body cut — the three robe rigs left
`armor_model.py`, which keeps hats and masks). Previews `tools/wardrobe_preview.py`. Sheets in
`documentation/wardrobe/`: `c_before_after.jpg`, `c_prototypes.jpg` (front/¾/side/back, stride,
seated), `c_liveries_ranks.jpg`, `c_silhouettes.jpg`, `c_values.jpg`.

**Change against §B3, made while painting:** liveries are **baked sheets, not runtime tints**. One
128 px sheet per livery on the cut's shared UV (`textures/armor/<cut>.png` for the default livery,
`textures/armor/<cut>/<livery>.png` for the rest). Reasons: a tinted grey ramp loses the hue shift
that makes the house colours read as cloth; the emblems are drawn into the same pixels; one draw,
no extra layer. Cost: a new colour needs a new sheet from the generator (a resource-pack change, not
datapack-only). The livery registry still exists — it carries the allowed cuts, the faction for
promotion matching, and the display name — and points at the sheet.

**Skirt motion — owner's pick, option B (2026-11-04).** Two rounds of "it looks weird when walking":
rev 1 gave each leg its own front panel (one half swung out like a door); rev 2 hinged a front and a
back shell at the waist (the hem lifted into the air and the skirt opened like a tent twice a step —
box geometry cannot make a hinged board read as cloth). Both compared against option B in
`c_motion_options.gif`; the owner chose B:

- a short **hip skirt** from the waist (y 12) to mid-thigh stays with the body (`robeSkirt`); its front
  (`clothHipFront`) lifts only by what *both* legs share forward, so walking leaves it hanging and
  sitting on a broom or chair lifts it into a lap;
- a long **lower half per leg** (`clothLegR`, `clothLegL`) pivots on that leg's hip joint and turns with
  it at 0.95, so the robe parts with each stride and the leg can never leave it. It starts at y 10,
  under the hip skirt, so the joint is always covered; standing, the hip skirt laps over it as a tier.

Accepted costs: a visible tier at mid-thigh; mid-stride the side view reads as wide trousers.

**Overlap fixes (same round):** every cuff ends at or above the hand (y 12, the skirt's top), so a hanging
arm never sits inside the skirt; bell sleeves are wrist bells ending at the hand (hanging sleeve pieces
stuck out like fins mid-swing and were dropped); the Dark Order sigil moved from a cube inside the
lowered hood to the back of the hip skirt; the school stole lost its back strip, which crossed the cowl;
the Everyday sash tail rides the hip front.

Motion is judged from `c_walk.gif` (45° swing), `c_sprint.gif` (70°) and `c_walk_frames.jpg`.

What the look sheet settles:
- **Option B works offline** for all five cuts, walking, sprinting and seated.
- **Open fronts by alpha** (Auror, Everyday, Scholar) read as a coat over a waistcoat.
- **Ranks as bone groups:** school badge → stole → velvet collar and chain; Auror boards → bars and
  collar pins → pauldron and half cape; Dark Order mantle; Scholar chain of office.
- **Values:** Everyday lightest, Auror mid, Dark Order darkest. School and Scholar share a value
  band and are told apart by length, sleeves and hue.

Known weak spots to revisit after the in-game pass: the 3×4 chest crests are colour blocks more than
beasts at this size (the beasts get room on capes and scarves in Phase E); the Dark Order officer
mantle is busy from the front.

### Plan

Five outfits, each a different cut, each with its full mark range so rank can be judged:

1. School Robe, `house_lion` livery, ranks 0–3.
2. Auror Duelling Coat, ranks 0–3.
3. Dark Order Robe, hood up/down, ranks 0–1.
4. Everyday Robe, `slate` and `moss`.
5. Scholar's Gown, Potions faculty, ranks 0–1.

Order: offline look sheet from `armor_preview.py` (front/back/side/silhouette/greyscale) → owner approval →
Java architecture → in-game capture (Phase D). See `tasks/todo_wardrobe_2026_11_04.md`.

## Phase D — Visual validation (2026-11-04)

Phase C's Java is in: `equipment/wardrobe/` (`Outfit` component, `LiveryDefinition` datapack registry,
`Wardrobe#stamp`, `OutfitCommands` = `/wandb item outfit <livery> [rank]`), `client/armor/ClothMotion`,
rank toggles and livery sheets in `WizardArmorRenderer`, items `wizard_robe_chest` and `scholar_gown_chest`.
Unit suite 3,018 tests, 0 failed.

`WB_EQUIPMENT_SHOWCASE=wardrobe` (`EquipmentShowcaseCapture`) photographs the five prototypes standing
(¾/front/side/back), walking (6 frames), sprinting (3), sneaking (2) and seated in a boat (2). Run on a
hidden desktop with both player models — `Dev` (slim) and `Dev2` (wide) — 85 shots each, every robe
stamped through `Wardrobe#stamp` (the log says `STAMPED`, so the livery registry loaded in game).
Sheets: `d_ingame_slim_still.jpg`, `d_ingame_slim_motion.jpg`, `d_ingame_classic.jpg`.

Verified in game: each lower half follows its own leg in the right direction (the signs only provable
here); the hip skirt and halves sit over the thighs in the boat; crouching keeps the halves with the
legs; rank pieces (stole, boards and pauldron, mantle, chain) and liveries (lion, moss, potions) draw;
slim and wide arms both sit inside the sleeves. The walk and sneak frames VR-0 found black render.

Not yet captured: swimming, elytra, broom riding. Body-only cuts show the wearer's own skin trousers in
the opening and below the hem, by design.

## §7 — Decisions needed from the owner

| # | Decision | Recommendation |
|---|---|---|
| D1 | House identity: stamp it on the clothing (cosmetic choice), or build a house affiliation system first? | Stamp now; a later Sorting can stamp the same livery. |
| D2 | Rank: printed on the stack at promotion/issue, or read live from the wearer's career rank? | Stack. Works for every player on screen with no new sync; a stolen coat shows its owner's rank, which fits disguise play. |
| D3 | Names and motifs: keep canon ids and item names, draw every emblem, sigil and mask new? | Yes. Lang names may be renamed later without touching ids. |
| D4 | Texture density: 16 px per block (current) or HD? | 16 px. The skin and every other mod piece are 16 px. |

## Phase E1 — Capes and cloaks (2026-11-04)

Items `school_cape` (liveries `school_plain` + the four houses; the crest at 8x9 with the beast on the
back) and `travel_cloak` (`umber`, `slate`; hood worn down). One `CapeItem` class and one `CapeRenderer`
for every cape cut; back slot (`equipment_type: cloak`, no stats, no charges).

Motion (`ClothMotion`): `capeRoot` takes vanilla's own cape angle, `6 + capeLean/2 + capeFlap`
(`PlayerCapeModel#setupAnim`), from the avatar render state; `capeHem` folds further by what half the
stride's amplitude (`1.4 * walkAnimationSpeed`, vanilla's leg swing) asks beyond that, capped at 18°.
The first in-game capture cleared the full 80° walk swing and stood the cape out like a flag; vanilla's
own cape does not clear a full swing either, so a brief brush at the peak of a step is accepted.

Rules: no cape is drawn with a glider on the chest (vanilla's rule); a robe's cowl or lowered hood is
hidden while a cape is drawn (the cape's collar sits there). Sideways lean (`capeLean2`) is not used yet.

Preview trap found here: `beast_preview` swings a bone the opposite way to GeckoLib's snapshots. The
robe halves never showed it (they follow the legs either way); `wardrobe_preview.to_preview` converts.

Sheets: `e1_capes.jpg`, `e1_capes_walk.gif`, `e1_capes_sprint.gif` (offline); `e1_ingame_capes_motion.jpg`,
`e1_ingame_capes_poses.jpg` (game, wide arms).

## Phase E2 — Accessories (2026-11-04)

Worn through Curios slots that already existed, no new slots: `leather_belt` and `duty_belt` (belt;
pouches at the hips, phial loops and a wand holster on the duty belt), `scarf` (necklace; knitted
stripes per house livery), `gloves` (hands; gauntlets wider than any cuff), `professor_hat` and
`travel_hat` (head, `hides: [head]`). Purely cosmetic, no attributes. One `AccessoryItem` class and one
`AccessoryRenderer`; the model, default livery and bone group come from the registry.

Fit: each accessory lies outside every robe cut's envelope (belt outside the widest sash, scarf ring
outside the tallest collar, gloves over the cuffs, hat brims clear of the skull), and nothing hangs
where a robe half swings (pouches sit beside the hip flank, below the hand).

Rule: a hat over a raised hood draws the hood down (`WizardArmorRenderer.HAT_WORN`), checked in game
with a Dark Order robe whose stack says hood up. The scarf shares the necklace slot with the
Time-Turner (size 1); raising the slot to 2 is a data change if both should be worn.

Sheets: `e2_accessories.jpg` (offline), `e2_ingame_accessories.jpg` (game, slim arms).

## Phase E3 — Remaining cuts (2026-11-04)

`travel_coat_chest` (buttoned canvas duster under a short shoulder cape, satchel, patched elbows;
`canvas_umber`, `field_olive`), `healer_coat_chest` (light coat, ward colour down the placket, apron,
an original leaf-and-wand emblem; ranks 1-2 are armbands; `ward_green`, `ward_crimson`),
`ministry_robe_chest` (tailored, standing collar, department sash; ranks 1-3 grow the chain of office;
`ministry_plum`, `ministry_charcoal`), `master_mantle_chest` (fur mantle and standing fur collar,
brocade panel, wrist bells; `master_crimson`, `master_midnight`). All body slot, all the school robe's
stats (armor 3). One `RobeCutItem` + `RobeCutRenderer` serve every cut added since the wardrobe; the
first five cuts keep their own classes.

Shoulder pieces (capelet, fur mantle) span x +-8.5 and z +-3.1: a hanging sleeve stays inside them and
a cape (front face z 3.2) still hangs clear behind; a swinging arm comes out from under them.

Weak spot: the travelling coat reads muddy and banded in game light (many horizontal tiers on one
canvas colour); a lighter canvas and fewer bands would help.

Sheets: `e3_cuts.jpg` (offline), `e3_ingame_cuts.jpg` (game, wide arms).

## Phase E4 — Identity and acquisition (2026-11-04)

**Issued uniforms.** Career ranks already hand items over on promotion (`rewards` in
`career_tracks/*.json`); the uniforms ride on that, as data, already stitched: Auror → Auror coat at
rank 1 with the duty belt, Senior Auror → rank 2, Head Auror → rank 3; Field Naturalist → travelling
coat in field olive with gloves; Curse-Breaker → travelling coat and hat; the two master ranks → the
master's mantle (crimson for the magizoologist, midnight for the curse-breaker). Rank on the stack
(§7 D2) means a promotion hands over a new coat rather than re-stitching the worn one.

**Recipes** (`recipe/wardrobe/`, 51): every cut a player makes has a shaped recipe from wool, leather,
string and nuggets; the Auror coat, duty belt and master's mantle are issued only. Liveries come by
dye: a cut plus its colours (the four houses for the school robe, scarf and cape; one dye each for the
other colourways), white dye back to the default. Dyeing yields a fresh stack, so a dyed robe is
rank 0; ranks come only from careers.

**Tailor:** deferred. A tailor belongs in the hamlet, whose buildings another session owns; recipes
and dyes already cover survival access.

Tests: `WardrobeRecipeTest` (results real, stitched results admitted by their livery, career uniforms
admitted, no two recipes with the same inputs, no single-material recipe in a vanilla armour shape —
the gloves first took the leather-boots shape); game tests prove a promotion issues the olive coat and
the server holds all 51 wardrobe recipes.
