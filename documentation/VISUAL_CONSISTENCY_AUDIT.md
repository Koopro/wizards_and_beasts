# Visual Consistency Audit — 2026-09-27

Branch `dev` @ `391aefe8` (plus the uncommitted creature-redesign work in the tree). Minecraft
1.21.11, NeoForge 21.11, GeckoLib 5.4.5.

**This is an audit and an implementation plan. It changes no asset, model, texture, data file or
code.** It is written for the agents that will do the visual overhaul: read §A and §D before
touching anything, then take work from §F in order.

## How this was measured

Earlier passes audited one domain at a time (`ENTITY_ART_AUDIT/RESULT`, `ITEM_ASSET_AUDIT/RESULT`,
`GUI_DESIGN_SYSTEM`, `tasks/todo_block_textures_2026_09_03.md`). Each fixed its own domain against
its own yardstick. This pass compares the domains **against each other and against vanilla**, which
nobody had done.

1. **Metrics on every texture (1,425 mod PNGs, 3,517 vanilla PNGs from the 1.21.11 client jar).**
   Per file: size, opaque colour count, mean/95th-percentile saturation, luminance spread,
   *neighbour noise* (mean |ΔL| between adjacent opaque pixels — how "busy" it is), *jag* (share of
   neighbour steps above 0.08 — how crisp vs soft), edge-vs-interior luminance (outline), the PNG
   `Generator` marker. Vanilla medians per folder are the yardstick.
2. **In-game captures.** `build/entity-art/after/sheet_00..13.png` (109 creatures × front / side /
   back / far / night / hurt / death, villager beside each, 2026-09-25) and the post-redesign
   captures in `runs/client2/screenshots/{hg_after,phoenix_after,th_seen,niffler_showcase,
   kelpie_showcase,bowtruckle_showcase,dementor_showcase}`. Every creature verdict below was read
   off those pictures.
3. **Contact sheets** of all 185 block textures, 210 item sprites, 158 GUI textures, 80 spell
   sigils, the armour sheets, and worn-armour renders from `tools/armor_preview.py`; block and
   creature skins zoomed side by side with vanilla (`stone_bricks`, `cobblestone`, `oak_planks`,
   horse, cow, wolf).
4. **Source reading** for scale (`ModCreatures.Spec`, creature definitions, `Attributes.SCALE`),
   particles (`AbilitySupport.Particle`, `ModParticles`, spell `vfx` blocks), HUD draw sizes and the
   player-form render path.

Measured baselines (median of each folder):

| | colours | saturation | noise | jag | verdict |
|---|---:|---:|---:|---:|---|
| vanilla block | 8 | 0.47 | 0.068 | 0.32 | |
| **mod block** | 8 | **0.32** | **0.049** | **0.21** | flatter and softer than vanilla |
| vanilla item | 10 | 0.50 | 0.132 | 0.53 | |
| mod item (16 px) | 13 | 0.43 | 0.123 | 0.49 | matches |
| vanilla entity | 17 | 0.33 | 0.049 | 0.18 | |
| mod entity | 16 | 0.38 | 0.050 | 0.25 | matches on average; outliers below |
| vanilla mob_effect | 8 | 0.48 | 0.136 | 0.59 | |
| **mod mob_effect** | **4** | 0.40 | **0.071** | **0.27** | under-drawn next to vanilla's icons |
| vanilla particle | 2 | — | 0.024 | 0.11 | crisp, tiny |
| **mod particle** | 1 sprite, soft radial blur | | | | see §B |

**Headline:** the mod is much more consistent than a 1,400-texture project usually is. Items,
the GUI kit and most creatures already share one language. The inconsistencies are concentrated in
five places: **scale** (quantised hitbox tiers), **the base building stones** (soft cloudy noise),
**VFX** (one blurred sprite, ad-hoc vanilla particles, no colour grammar), **a few player-facing
placeholders** (house-elf and veela-harpy forms, Hawk Animagus), and **legacy creature skins**
(flat fill + random speckle).

---

# A. Visual design principles

Rules for every future asset. Each rule is already obeyed by some part of the mod — the rule names
the part to copy.

1. **Vanilla is the yardstick, measured, not remembered.** Before shipping a texture, compare its
   colour count, noise and jag against the vanilla median for its folder (table above). Stay within
   ±30 %. A texture far off the vanilla band reads as "from another pack" no matter how good it is.
2. **One texel = one sixteenth of a block, everywhere in the world.** Entities, armour, block
   entities and held models use box UV at 1 px = 1 unit and are never render-scaled to change size.
   Size changes go through `Attributes.SCALE` (definition `scale`) or real geometry, never a
   renderer `scale()` call. (The dragon rebuild removed `dragon.scale` for exactly this reason.)
3. **GUI art is drawn 1:1 at GUI scale, or at an integer multiple.** No 92 px icon squeezed to 12,
   no 256 px HUD drawn at 96. Author at the size it is shown.
4. **Silhouette first, colour last.** A creature, item or armour set must be nameable from its black
   silhouette at 20 blocks / in the hotbar. (Entity Design Bible, Principle 1 and 4.)
5. **Shade by form, not by noise.** Every face gets a value ramp: light top-left, dark
   bottom-right, a darker underside on creatures. Texture detail is *structure* (strands, scales,
   courses, grain), never random 1 px dots.
6. **Few colours, hue-shifted ramps.** 4–8 colours on a block face, 3–5 per material on an entity,
   ramps that shift warm in the light and cool in the shadow. `tools/item_sprites.py` is the
   reference implementation.
7. **One accent per asset, used where it means something.** (Bible, *Colour*.) The rest of the
   palette stays in the material's natural range.
8. **Materials read the same everywhere.** Fur, feather, scale, hide, bark, cloth, stone, wood,
   metal, glass and magic each have one pixel treatment (§D) shared by blocks, items, armour and
   creatures. A gold rim on a Galleon, a Gringotts trim block and a Horntail crown use the same gilt
   ramp.
9. **No pure black, no pure white** in any lit surface. Darkest ink is `PAGE_INK` `#2E1F16`-class,
   brightest highlight stays below `#F4F0E0`. Only emissive masks and the Dementor's hood void may
   touch the ends.
10. **Outlines belong to icons, not to the world.** 16 px item sprites and GUI icons carry a tinted
    dark outline (never black). Block faces and entity skins never draw an outline on their UV
    edges — geometry makes the edge.
11. **Emissive is earned.** Only a lore light source, one state-signalling organ, or a body made of
    light/fire glows (Bible, *Emissive*). Nothing renders full-bright by accident (§B C2).
12. **Magic is colour-coded by family, not by spell.** Every spell's colour, particle and glow
    comes from its family palette (§D, VFX). Two unrelated spells must not share a colour; hostile
    and benign must never look alike.
13. **Particles are pixel sprites.** 4–8 px, 2–4 colours, crisp, animated by frames or colour over
    life — never a blurred radial gradient.
14. **Canonical identity is one or two features, exaggerated.** The Phoenix is crimson and gold, the
    Hippogriff bows, the Thestral is skeletal and bat-winged, the Niffler has a bill. Find the one or
    two features the book/film uses to *name* the thing and make them the biggest shapes on the
    model. Do not copy film art.
15. **Scale follows the world, not a tier table.** A dragon is bigger than a troll, a giant is bigger
    than both. Size is a per-creature decision (§D, scale rules), not a bucket.
16. **Screens are parchment and ink; overlays are leather.** (`tasks/art_rework_phase2_brief.md`.)
    Full screens use page tokens; anything drawn over the live world uses the leather tokens.
17. **Every asset has a generator, and the generator is under version control.** Hand-edited PNGs
    and untracked scripts drift. (Today `tools/` is git-ignored — §F Phase 0.)
18. **Judge in the client.** An offline preview is a draft. Nothing is done until it has been seen
    through `EntityShowcaseCapture` / `ItemShowcaseCapture` next to a villager or a vanilla item.

---

# B. Current inconsistencies, by severity

### CRITICAL — wrong at a glance, player-facing

| # | Inconsistency | Evidence |
|---|---|---|
| C1 | **Hitbox size is quantised into five tiers (0.45 / 0.75 / 1.25 / 1.9 / 2.9 blocks tall) and every rig is authored to its box.** So all ten dragons, the Troll, the Giant, the Sea Serpent, the Reem, the Nundu, the Zouwu and the Abraxan are the same 2.9-block height; the Thunderbird, Werewolf, Centaur, Hippogriff and Chimaera are all 1.9. A dragon stands no taller than a troll, a giant no taller than a horse. | `ModCreatures.Spec` + `creatures/*.json` `width`/`height`; captures sheets 01, 04, 11, 12. Only the Basilisk uses the definition `scale` field. |
| C2 | **House-elf and Veela (harpy) player forms render as untextured placeholder cubes at full-bright.** `FormModelRenderer`'s legacy path binds `textures/entity/form/house_elf.png` / `veela_harpy.png`, which do not exist (only a *null* path falls back to `placeholder.png`), and draws with light `0xF000F0`, so they glow at night. The Stag Animagus shares the full-bright path. | `FormModelRenderer` L181-205, `FormRegistry` L47-55, L91; `textures/entity/form/` holds only `animagus_stag`, `obscurial_dark`, `placeholder`. Not yet seen in a client — confirm. |
| C3 | **The core building stones are soft, cloudy blobs.** `hogwarts_stone`, `hogwarts_dark_stone`, `hogsmeade_stone`, `diagon_street_stone` (+ their stairs/slabs/walls via `withVariants`) have jag 0.00–0.07 against vanilla stone's 0.32; they look like blurred noise next to the crisp bricks beside them. These are the blocks a castle is built from. | `stone_zoom.png` (scratch), metrics. Cause: `location_textures.rough_stone` builds on low-frequency `fbm` (cells 8/4/2) and posterises it into large soft cells. |

### HIGH — clearly inconsistent, visible in normal play

| # | Inconsistency | Evidence |
|---|---|---|
| H1 | **Two wing languages.** The redesigned Hippogriff/Thestral (2026-09-26, shared `WingedWalker`) have fanned primaries / bat membranes. Abraxan, Aethonan, Granian, Griffin, Snallygaster still carry thick two-stripe plank wings. Same body plan, different construction. | sheets 00, 04, 05, 11 vs `hg_after`, `th_seen` |
| H2 | **Legacy "flat fill + random speckle" skins.** Abraxan, Unicorn, Reem, Nundu, Graphorn, Griffin, Erumpent, Troll, Giant, Knarl, Nogtail, Diricawl, Sphinx, Kappa, Wampus Cat carry one flat colour per face sprinkled with 1 px darker dots. Vanilla horse/cow/wolf shade each face with a value ramp and structured strands. At distance the dots read as disease, not fur. | `skins_zoom.png`, sheets 00, 04, 07, 08, 10, 13 |
| H3 | **VFX has no pixel language.** All 14 custom particle types share one 8×8 soft radial-blur sprite (`particle/spell_mote.png`); `ice_shard`, `water_droplet`, `electric_arc` and `fire_ember` differ only by tint and motion. The beam textures (`effect/wand_beam*.png`) are smooth gradients. Everything else in the mod is pixel art. | `particles/*.json`, `misc_gui.png` |
| H4 | **Ability particles borrow vanilla meanings.** `bioluminescence` / `heal_aura` / bonding / Imperio / Dittany / Felix / chocolate frog all emit `HAPPY_VILLAGER` (vanilla's "trade accepted / bonemeal" sparkle); `blink_away` / `evasion` / `dread_aura` emit `PORTAL` / `WITCH` purple clouds, often 20–40 at a time. In the captures a third of the roster is permanently wrapped in green crosses or purple fog. | `AbilitySupport.Particle`, 40 distinct vanilla `ParticleTypes` across the code; sheets 02, 06–10 |
| H5 | **Spell colours have no grammar.** Red is Stupefy, Diffindo, Crucio *and* Capacious Extremis (a utility charm); Diffindo trails `ice_shard`. 117 of 152 spell files still carry one of two stand-in colours (`0xFFFFAA`, `0xFF5555`, without alpha) while their sigils already got per-id hues — icon and effect disagree the moment one is implemented. | `spells/*.json` `color` + `vfx` |
| H6 | **HUD/spell icons break pixel density.** Spell sigils are 92×92 smooth-gradient diamonds drawn at 12 px in the spell list and ~34 px in the HUD; the HUD frame is a 256 px texture drawn at 96 GUI px (×0.375). Neighbouring vanilla hotbar pixels are 1:1. The sigils are also the only gradient-shaded icon family in a pixel-ramp UI. | `SpellDiamondOverlay` (`HUD_TEX_SIZE 256`, `HUD_ON_SCREEN_SIZE 96`, `ICON_TEX_SIZE 92`), `SpellMenuScreen.LIST_ICON_SIZE 12`, `sigils.png` |
| H7 | **Robes collapse into one colour.** Student robe, Auror robe and Death Eater robe are all dark navy/black in the same value band; at 10 blocks they are one outfit. The student robe carries no house identity at all. | `armor_sheet.png` |
| H8 | **Canon misreads on marquee creatures.** Unicorn (speckled grey-on-white coat, tan horn — should be pure white with a pearl/gold horn), Thunderbird (reads as a rubber duck: yellow body, white bill, player-sized), Hawk Animagus (renders the vanilla *parrot* skin), Fire Crab (spider legs under a jewelled shell; canon is tortoise-like). Details in §C. | sheets 12, 13, 03; `FormModelRenderer` `PARROT_TEXTURE` |

### MEDIUM — noticeable side by side

| # | Inconsistency | Evidence |
|---|---|---|
| M1 | **Blocks run flatter than vanilla** (saturation 0.32 vs 0.47, noise 0.049 vs 0.068). Beyond C3: `honeydukes_pastel_*`, `three_broomsticks_timber`, `diagon_shopfront_wood`, `gringotts_white_marble_pillar`, the stripped wandwood logs (jag 0.00–0.05) are near-flat washes. | metrics, `blocks.png` |
| M2 | **Two paper systems for one game.** Screens are parchment; tooltips are vanilla's purple-bordered dark box. Not wrong (vanilla players expect it), but the two sit on top of each other in every inventory. | no tooltip sprites under `textures/gui/` |
| M3 | **Obscurial stress meter** (`gui/obscurial/stress_meter.png`, 512×72, 1,739 colours, no generator marker) is a smooth-gradient swirl — the one HUD element outside both the parchment and leather kits. | metrics, `misc_gui.png` |
| M4 | **Mob-effect icons under-drawn**: median 4 colours vs vanilla 8, noise half of vanilla. 39 of 42 have no generator marker (legacy). | metrics |
| M5 | **Heavy mottle on large rigs** (Knarl blotches, Graphorn, Giant tunic, Centaur human half, Rougarou). Already listed as debt in `ENTITY_ART_RESULT`. | sheets |
| M6 | **Griffin reads as a beaked horse** (horse neck and legs under an eagle head); the Glumbumble is still a grey box with a V of wings; Blast-Ended Skrewt a grey crate on spider legs. | sheets 01, 04, 05 |
| M7 | **Red Cap renders 2× its hitbox**; long low creatures (Ashwinder, Moke, Salamander, Flobberworm, Runespoor) sit in boxes 3–6× their height. | inventory `fitH` |
| M8 | **Glow is uneven.** Phoenix (made of fire) shows almost no glow at night (3 %); the Fairy's 59 % is the high end. | inventory `glow`, `phoenix_after` night |
| M9 | **Bespoke creatures have no hit/death reactions** (Niffler, Cornish Pixie, Streeler, Bowtruckle, Augurey, Mooncalf): no clip gate, so damage shows only the vanilla red tint while every data-driven creature flinches. | `ENTITY_ART_RESULT` batch 3 |

### LOW

| # | Inconsistency |
|---|---|
| L1 | Augurey: last 32×32 noise skin (195 colours) and 3 UV holes; Bowtruckle 32×16 old skin. |
| L2 | Death Eater mask variants 1–5 are near-identical at 16 px (six skull icons in a row); `death_eater_mask_1` = `death_eater_mask` pixel-for-pixel. |
| L3 | Hair items (unicorn, veela, demiguise ×6 fade frames) are 1 px strands — the noisiest icons in the set (noise 0.44) and weak in a slot. |
| L4 | `ImperioCommandScreen` is the one player-facing screen still drawn in vanilla white-on-dark. (`DiaryPossessionScreen` is dark on purpose.) |
| L5 | Stripped wandwood logs are hard to tell apart (seven pale beige squares). |
| L6 | 97 sound events, 0 own `.ogg` files — all remapped vanilla sounds; five spells share `ender_pearl.throw`, five `blaze.shoot`. Out of visual scope; recorded because it caps how distinct a spell can *feel*. |

---

# C. Asset-by-asset problems

Format: **what is wrong** → why it is inconsistent → target → what changes (M = model, T = texture,
A = animation, D = data, C = code).

### Scale (all C1 creatures) — D (+ M only where noted)
- Wrong: the five-tier box. Why: size is a world rule, and the roster breaks it on every
  comparison a player will make (dragon vs troll, giant vs troll). Target: §D scale table. Change:
  per-creature `scale` in `creatures/*.json` (drives `Attributes.SCALE`, which already moves model
  **and** hitbox together — no renderer scale, principle 2). **Owner sign-off needed**: a bigger box
  changes pathing, doorways, spawn space and melee reach; that is gameplay.

### House-elf and Veela-harpy forms — M + T + C (small)
- Wrong: placeholder cubes, missing texture, full-bright (C2). Target: GeckoLib rigs through
  `PlayerFormRig` like the goblin/merfolk/werewolf forms. House-elf: 0.9-block tall, bat ears wider
  than the head, pillowcase tunic, huge eyes (canon: tennis-ball eyes, pencil nose). Veela harpy:
  the Veela's human proportions with a beaked bird face, scaly wings from the shoulders, fire
  in the hands (GoF ch. 8). Add both to `PlayerFormRig.BY_FORM_ID`; the legacy path then serves
  only the Stag, which should get a rig too so the full-bright path can be deleted.

### Hawk / Beetle Animagus — T (+ M for hawk)
- Wrong: the hawk borrows `ParrotModel` + the parrot skin (a green-red parrot), the beetle a
  silverfish. Target: own skin on the borrowed vanilla model at minimum (brown-barred hawk on the
  parrot model; glossy black beetle on the silverfish), both authored at vanilla density.

### Base building stones (C3) — T
- `hogwarts_stone`, `hogwarts_dark_stone`, `hogsmeade_stone`, `diagon_street_stone`, then the flat
  set in M1. Target: vanilla `stone`/`smooth_stone` behaviour — 4–6 colours, high-frequency 1–3 px
  clusters, jag 0.25–0.35, noise 0.05–0.07, keep the film-derived palette (warm sandstone for the
  castle) that the 2026-09-03 pass chose. Change: replace the low-frequency `fbm` base in
  `rough_stone`/`plaster`/`timber` with a per-pixel hash grain plus small clustered strokes; keep
  `posterise`. Re-check tiled 2×2 with `block_texture_sheet.py`.

### Winged horses and Griffin (H1, M6) — M + T
- Abraxan, Aethonan, Granian, Snallygaster, Griffin: plank wings. Target: the `WingedWalker`
  wing (Hippogriff) for feathered wings, the Thestral membrane for none of these. Abraxan: huge
  palomino, no speckle (canon: elephant-sized, palomino, drinks whisky). Granian: grey, fastest —
  leaner legs. Aethonan: chestnut. Griffin: eagle forequarters with *talons* on the front legs and
  a feathered chest, lion hindquarters — not horse legs. Keep clip names; re-run `reactions.py`.

### Speckle skins (H2) — T only
- Abraxan, Unicorn, Reem, Nundu, Graphorn, Griffin, Erumpent, Troll, Giant, Knarl, Nogtail,
  Diricawl, Sphinx, Kappa, Wampus Cat. Target: per-face value ramp (light back, darker belly, AO in
  creases), strands/scales laid along the body, pattern only where canon puts one (Nundu spots,
  Wampus/Zouwu stripes). Rigs stay; this is a skin repaint through each generator (or a new shared
  `bodies.py` skin painter, preferred — see §F Phase 2).

### Unicorn (H8) — T (+ small M)
- Wrong: grey speckles on white, tan horn tilted like a lance. Target: pure white coat with a
  cool-grey shadow ramp, silver-white mane, pearl horn with a gold base, golden hooves; the horn
  one-third shorter and more upright. Foals (if a baby variant exists) gold. Glow: none (lore gives
  silver blood, not light).

### Thunderbird (H8, C1) — M + T + D
- Wrong: rubber-duck read (short neck, rounded yellow body, white bill), 1.9 blocks. Target:
  raptor silhouette — hooked beak, long primaries, wings that dwarf the body; storm palette
  (slate, white, one electric-blue accent along the wing bars — the existing zig-zag is the right
  idea). Scale ≥ 1.6 (§D).

### Fire Crab — M
- Wrong: long spider legs under the shell. Target: squat tortoise body, jewel-studded carapace
  (keep the gem colours — they are the accent), short stumpy legs, flame vent at the rear.

### Robes (H7) — T (+ D for house variants)
- Target values: Student = black wool with **house-colour hood lining, tie and crest patch** (four
  variants, or one tinted overlay); Auror = dark *plum/charcoal* long coat with a high collar and a
  brass buckle line (distinct hue from student black); Death Eater = true black with a hood and the
  bone mask as the only light value. Each set must separate in the silhouette pass of
  `armor_preview.py` and in value at 10 blocks.

### Particles (H3) — T + C (provider)
- Target: one sprite set per custom particle (§D VFX). `ice_shard` = 3-frame angular shard,
  `water_droplet` = 2-colour drop, `electric_arc` = 2-frame jag, `fire_ember` = 3-frame ember,
  `dark_wisp` = 4-frame curl, `arcane_mote` = 3-frame twinkle, `light_glow` = 2-frame cross. Keep
  `FamilyTintParticle` tinting (sprites greyscale-ramped so tint works).

### Ability particles (H4) — D + C
- Target: the `AbilitySupport.Particle` palette gains mod sprites (`arcane_mote`, `light_glow`,
  `dark_wisp`) and the JSON picks them. `HAPPY_VILLAGER` stays only where vanilla means it (bonding
  "accepted"). Ambient emitters (bioluminescence) cap at 1 particle per 20+ ticks; one-shots
  (blink) at ≤ 12.

### Spell colours (H5) — D
- Target: every spell colour from its family palette (§D). Fix now: Capacious Extremis out of red,
  Diffindo trail → a white-steel `arcane_mote` slash (or a new `cut` sprite). Replace the 117
  stand-ins with the per-id hue the sigil generator already computes, so icon and effect match;
  write them as opaque signed ARGB (the implemented-spell rule).

### Spell sigils + HUD (H6) — T + C
- Target: sigils redrawn as 16×16 pixel icons (or 24×24, drawn at 1× / 2×) in the item-sprite
  ramp language, rim colour by category kept; HUD frame authored at its on-screen size (96 px) or
  drawn at an integer scale. `SpellDiamondOverlay` / `SpellWheelScreen` / `SpellMenuScreen` then draw
  at integer multiples only.

### Obscurial stress meter (M3) — T
- Target: redraw on the leather overlay kit (it is a HUD element) with a pixel swirl.

### Mob-effect icons (M4) — T
- Target: regenerate the 39 unmarked icons through `effect_icons.py` at vanilla density (6–10
  colours, outline, ramp).

### Phoenix glow (M8) — T
- Target: glowmask on the tail streamers, crest and wing tips (a bird of fire); body stays lit.

### Bespoke reactions (M9) — A (+ C gate)
- Niffler, Cornish Pixie, Streeler, Bowtruckle, Augurey, Mooncalf: add `hit`/`death` clips and route
  them through the same one-shot gate the data-driven beasts use.

### Honourable mention, no change needed yet
- **Werewolf** reads as the lanky film werewolf; fine. Its `MoonBound` `LARGE_SMOKE` bursts render as
  big black clouds — move to `dark_wisp` with H4.
- **Giant / Troll**: silhouettes fine; C1 scale and H2 skin only.
- **Centaur**: fine as a body; human half is small against the horse and heavily dithered (M5).
  Target torso ~15 % larger, skin ramp not dither.

---

# D. Visual reference system

### Base texture resolution
| Asset | Resolution | Density |
|---|---|---|
| Block face | 16×16 (animated: 16×16×N frames) | 16 px / block |
| Item sprite | 16×16 | 1 px = 1 GUI px at scale 1 |
| Entity / armour / held model | box UV, sheet size as needed (power-of-two width not required) | 1 px = 1 model unit = 1/16 block |
| Book/cuboid item sheets | box UV, same density as entities | 1 px = 1 unit |
| GUI chrome | authored at on-screen size, 64/8 panels tiled, 32/4 buttons | 1 px = 1 GUI px |
| GUI icon | 16×16 (large feature icons 24×24 or 32×32, drawn 1×) | 1 px = 1 GUI px |
| Particle | 8×8 canvas, 4–8 px drawn | vanilla |
| Mob effect | 18×18 | vanilla |

### Geometry complexity (per rig)
| Class | Bones | Cubes | Notes |
|---|---:|---:|---|
| Tiny (≤ 0.5 block) | 3–12 | 5–15 | one oversize feature carries identity (bill, ears) |
| Small / medium | 10–20 | 10–26 | the rigkit median; most of the roster |
| Large / mount | 20–36 | 20–45 | Hippogriff, Thestral |
| Dragon / boss | 28–36 | 55–80 | the ten dragons today |
| Worn armour | ≤ 12 | ≤ 35 | inflate ≤ 0.55 |
| Held GeckoLib item | ≤ 10 | ≤ 24 | |
Hard rules: integer cube sizes (UV holes otherwise — `uv_check` must report 0), no zero-thickness
cube except a 1-unit plate ≥ 8 long for membranes, no cube smaller than 1 unit.

### Shading philosophy
Light from top-left-front. Every face: 2–3 step ramp, lightest at the top edge; underside one step
darker than the side. AO in creases and joints only. No dithering, no random speckle; texture is
structure (courses, grain, strands, scales). Hue shifts: highlights warmer/yellower, shadows
cooler/bluer (the item-sprite engine's ramps).

### Palette philosophy
Per material, a 3–5 colour ramp; per asset one accent. Saturation band: mean 0.30–0.50 (vanilla
entity/block range); only emissive masks, Fwooper plumage and house banners exceed 0.7. Film
palettes for locations as chosen in the 2026-09-03 block pass (warm sandstone Hogwarts, black-green
Ministry tile with gilt, white Gringotts marble with gold, Diagon brick). No `#000`/`#FFF` on lit
surfaces.

### Outline philosophy
Icons (items, GUI, effects): 1 px tinted outline, a darkened shade of the adjacent fill, never
black. World geometry (blocks, entities, armour): no outline. Panels: the parchment kit's double ink
rule.

### Eye design
- Ground animals: 1×1 or 2×1 dark eye with one lighter pixel above (vanilla cow/wolf idiom).
- Predators / hostile: 2×1 with a coloured iris, no glow (Acromantula excepted — eight red points
  are identity).
- Humanoids: 2-pixel eyes with white sclera, as the player skin.
- Glowing eyes only when lore earns it (Opaleye's pupil-less eyes) — Bible *Emissive*.
- Birds: 1×1 dark eye set in a lighter ring.

### Material rendering
| Material | Treatment |
|---|---|
| Fur | two-tone ramp, warmer underside, 2–3 px strand marks along the body, soft edge |
| Feather | banded rows, hard trailing edge, primaries as separate plates on large wings |
| Scale | tight repeating 2×2 motif, highlight along the spine ridge |
| Hide | low contrast, wrinkle lines at joints only |
| Bark / plant | vertical grain, irregular silhouette |
| Cloth | long vertical folds (1 px darker lines), lighter hem wear |
| Stone | 4–6 colours, small clustered grain, courses for masonry |
| Wood | grain along the board, knot 1 per 2 boards max |
| Metal | 4-step ramp with a 1 px specular, darkest at the lower rim |
| Gilt | `GILT_DARK/GILT/GILT_LIGHT` ramp (same values as the GUI) |
| Glass / liquid | 2 values + a 1–2 px highlight, liquid tinted |
| Magic (smoke, flame, light) | low internal contrast, silhouette carried by the glowmask |

### Animation philosophy
Unchanged from the Entity Design Bible *Animation Language*: loops for locomotion, one-shots on top
(controller order fixed 2026-09-25), idle actions from `IdleProfile`, `hit` 0.4 s, `death` 1.2 s held.
Additions: every creature that can be damaged has `hit` and `death` (bespoke included); wings fold
at rest; rotation authored in rigkit sense and converted by `rigkit.emit`.

### VFX
| Family | Palette (primary / accent) | Sprite |
|---|---|---|
| Combat — stunning / disarming | scarlet / white | `arcane_mote`, `light_glow` |
| Combat — elemental | fire orange, ice cyan, water blue | `fire_ember`, `ice_shard`, `water_droplet` |
| Utility — charms | pale gold / white | `light_glow`, `arcane_mote` |
| Defence — Protego, Patronus | silver-blue / white | `protego_*`, `light_glow` |
| Healing | soft green / white | `light_glow` (not `HAPPY_VILLAGER`) |
| Dark Arts | sickly green (Avada), black-violet (Crucio, curses) | `dark_wisp` |
| Transfiguration / movement | violet | `arcane_mote` |
Density: trail ≤ 2 per tick, impact 8–24, ambient ≤ 1 per 20 ticks. Glow (full-bright) only on the
particle core and the beam, never on the caster or the target model.

### Entity scale rules
| Tier | Height (blocks) | Examples (target) |
|---|---|---|
| PLAYER | 1.8 | player, villager 1.95 |
| SMALL | 0.25–0.9 | Niffler 0.5, Cornish Pixie 0.6, Bowtruckle 0.6, Puffskein 0.45, Kneazle 0.8, House-elf 0.9 |
| MEDIUM | 0.9–1.8 | Crup 1.0, Goblin 1.5, Unicorn 1.6, Acromantula (colony) 1.6 |
| LARGE | 1.8–3.0 | Werewolf 1.9, Hippogriff 1.9, Thestral 1.8, Centaur **2.3**, Dementor 3.0, Abraxan 2.9 |
| GIANT | 3.0–6.0 | Troll **3.6** (12 ft), Giant **5.5–6** (20 ft), Thunderbird **3.0**, Aragog-class 3 |
| DRAGON | 4–6 tall, 10–16 long | Horntail / Ironbelly 5–6, Longhorn / Ridgeback / Welsh / Hebridean 4.5, Fireball / Short-Snout 4, Opaleye 4, Vipertooth 2.5–3 (canon smallest, ~15 ft) |
| BUILDING | door 2 high, storey 4–5, Hogwarts corridor 5+ | a dragon must not fit a village door; a troll should duck under one |
| VEHICLE | broom 0.8×1.0 box, rider on top | brooms only today |
Mechanism: definition `scale` = target height / current hitbox height (e.g. Horntail 5.5 / 2.9 ≈
1.9). Rigs keep authoring at the tier box; `Attributes.SCALE` does the rest. Model-to-hitbox fit
stays within 0.8–1.3 (`entity_art_inventory.py fitH`).

---

# E. Do not change

These already fit the direction. Do not rework them "for consistency" — make the rest match them.

- **16×16 item sprites** (`tools/item_sprites.py`, 210 icons): vanilla-grade density, hue-shifted
  ramps, tinted outlines. This is the mod's reference style.
- **Parchment GUI kit** (`gui_parchment.py`, `WizardsPalette` page tokens, `McStylePanel`, the eight
  skins) and the leather overlay kit. Screens follow the no-shadow-on-paper rule (checked in code).
- **Wand system art**: wood `appearance` data, wand rig (the author's, frozen), Elder Wand.
- **Book cuboids** with overhanging boards and cover art from the icon; hold-class display transforms.
- **Wizard cards** (128 px portraits in rarity frames): a deliberate large-format exception.
- **Spawn eggs** on vanilla's egg shading.
- **Redesigned creatures** (2026-09-25..27): all ten dragons, Dementor, Hippogriff, Thestral, Phoenix
  (except M8 glow), Niffler, Cornish Pixie, Streeler, Acromantula, Basilisk, Kelpie, Bowtruckle rig.
  Scale (C1) is data, not a redesign.
- **Rigkit-era creatures that read cleanly**: Chimaera, Crup, Kneazle, Matagot, Hodag, Horned Serpent,
  Sea Serpent, Runespoor, Occamy, Zouwu, Qilin, Swooping Evil, Golden Snidget, Fwooper, Jobberknoll,
  Leprechaun, Erkling, Red Cap (except M7), Goblin Teller, Ghoul, Troll/Giant silhouettes,
  Manticore, Sphinx silhouette, Maledictus, Merperson, Grindylow, Ramora, Shrake, Horklump, Lobalug.
- **Ministry and Diagon brick block sets**, house banners, floo flames, wandwood leaves/saplings,
  location planks: vanilla-grade.
- **Worn armour geometry** (robe/hat/mask shapes) — only the robe palettes change (H7).
- **Glowmask rule** and its file-presence mechanism.
- **Hurt tint, clip gate, controller order** — engine, correct.

---

# F. Prioritised work plan

Order is by *player-visible damage per hour of work*, with prerequisites first. Each phase ends
with an in-client capture (principle 18) and `./gradlew build` green. No mixin is needed anywhere in
this plan: every change is an asset, a data file, a generator, or ordinary renderer/particle
registration code (`ModParticleProviders`, `FormModelRenderer`, `PlayerFormRig`,
`SpellDiamondOverlay`) — vanilla behaviour is never intercepted.

### Phase 0 — Safety (before any art)
- [ ] Decide `tools/` tracking: the generators every phase edits are git-ignored and exist only on
      this machine (`ITEM_ASSET_RESULT`, *Remaining technical debt*). Track them, or copy to a
      tracked location. Nothing below is reproducible otherwise.
- [ ] Commit the creature-redesign work already in the tree (Dementor, Kelpie, Bowtruckle, Phoenix,
      Thestral, Hippogriff, Niffler, Basilisk, Acromantula) so art work starts from a clean base.
      Use `git commit -- <paths>` (dirty shared tree).
- [ ] Add a `tools/visual_metrics.py` (the measurement from this audit) that prints any texture
      outside the vanilla ±30 % band for its folder. Every later phase runs it.

### Phase 1 — Player-facing placeholders (C2, H8 hawk)
- [ ] House-elf form rig + skin (`PlayerFormRig`), Veela harpy form rig + skin.
- [ ] Stag Animagus to a rig; then delete the full-bright legacy path in `FormModelRenderer`.
- [ ] Hawk and Beetle Animagus own skins on their borrowed vanilla models.
- Verify: form mannequin (`form_mannequin`) at day and night, in first and third person.

### Phase 2 — World scale (C1) — needs owner sign-off first
- [ ] Owner approves the §D scale table (gameplay: pathing, spawn space, reach).
- [ ] Set `scale` on the dragons, Troll, Giant, Thunderbird, Centaur, Sea Serpent (data only).
- [ ] Spawn/placement check for scaled creatures (the duplicate-placement crash memory: test spawning
      paths, not just `/summon`); re-capture with a villager and a 2-high door in frame.
- [ ] Red Cap / long-low rig fit (M7) — only if the owner also accepts hitbox changes.

### Phase 3 — Building blocks (C3, M1)
- [ ] `location_textures.py`: high-frequency grain base for `rough_stone`, `plaster`, `timber`.
- [ ] Regenerate the four base stones and the flat set; tiled 2×2 sheet + in-world wall capture
      beside vanilla stone bricks. Target metrics: noise 0.05–0.07, jag 0.25–0.35.
- [ ] Stripped wandwood logs: one distinguishing trait each (L5).
- Note: `runData` stubs over real textures — never `git add -A` after it.

### Phase 4 — VFX grammar (H3, H4, H5, Werewolf smoke)
- [ ] Draw the 7 custom particle sprite sets (§C) as greyscale-ramped pixel frames; one JSON per type.
- [ ] Beam textures to a pixel core + stepped glow.
- [ ] `AbilitySupport.Particle` gains `ARCANE`, `LIGHT`, `DARK_WISP`; re-point `bioluminescence`,
      `heal_aura`, `blink_away`, `evasion`, `dread_aura`, `MoonBound` smoke; cap ambient rates.
- [ ] Spell colours from the family table; replace the 117 stand-ins with the sigil hue; fix
      Capacious Extremis and Diffindo.
- Verify: a capture of every implemented spell's trail and impact on one sheet, day and night.

### Phase 5 — Creature skins and wings (H1, H2, H8, M5, M6, M8)
- [ ] Shared skin painter in `bodies.py`/`rigkit` (ramp + strands/scales), so fifteen generators do
      not each reinvent fur.
- [ ] Repaint the H2 list through it (skin only, rigs untouched).
- [ ] Winged horses + Griffin onto the `WingedWalker` wing; Griffin forequarters.
- [ ] Unicorn, Thunderbird (model + skin), Fire Crab (model), Phoenix glowmask, Glumbumble, Skrewt.
- [ ] Re-render the Bestiary portraits for every changed rig (`bestiary_portraits.py`).

### Phase 6 — Armour identity (H7)
- [ ] Robe palettes per §C; student robe house variants (data + textures; icons follow from
      `item_sprites.py`).
- [ ] Silhouette + value check in `armor_preview.py` and in client at 10 blocks.

### Phase 7 — GUI density (H6, M3, M4, L4)
- [ ] Spell sigils to 16/24 px pixel icons; HUD diamond authored at on-screen size; integer draw
      scales only.
- [ ] Obscurial stress meter onto the leather kit.
- [ ] Mob-effect icons through `effect_icons.py`.
- [ ] `ImperioCommandScreen` onto parchment (or leather if it is meant as an overlay).
- [ ] Decide tooltips (M2): keep vanilla (recommended) or add a parchment tooltip background.

### Phase 8 — Polish (M9, L1–L3)
- [ ] `hit`/`death` clips for the six bespoke creatures + gate.
- [ ] Augurey rebuild on rigkit; Bowtruckle skin.
- [ ] Death Eater mask icons differentiated; hair icons given more mass.

### Out of scope here, recorded for the owner
- Sound: every event is a remapped vanilla sound (L6).
- `CreatureIdleGoal` never runs in water; flying creatures play `fly` on the ground
  (`ENTITY_ART_RESULT`).

---

## Reproduce

Scratch scripts used for this audit (`texmetrics.py`, `sheet.py`, contact sheets) lived in the
session scratchpad; Phase 0 turns the metric into `tools/visual_metrics.py`. Existing tools:
```
python tools/entity_art_inventory.py
python tools/armor_preview.py --out <dir>
python tools/block_texture_sheet.py
WB_ENTITY_SHOWCASE=<list> WB_ENTITY_SHOWCASE_WORLD=<save copy> WB_ENTITY_SHOWCASE_OUT=<dir> ./gradlew runClient2
WB_ITEM_SHOWCASE=<list>   WB_ITEM_SHOWCASE_WORLD=<save copy>   WB_ITEM_SHOWCASE_OUT=<dir>   ./gradlew runClient2
```
Vanilla reference textures: `build/moddev/artifacts/neoforge-21.11.42-client-extra-aka-minecraft-resources.jar`.
