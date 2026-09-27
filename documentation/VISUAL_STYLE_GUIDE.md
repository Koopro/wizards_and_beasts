# Visual Style Guide — Wizards & Beasts

**Target:** Minecraft, made by a professional wizarding-world expansion team. Every asset must look
like it belongs to one resource pack, and that pack must look like vanilla's sibling.

Not: realistic anatomy, painted fur or feathers, smooth gradients, micro-detail, generic fantasy
kit, AI-art polish.

Evidence and history: `documentation/VISUAL_CONSISTENCY_AUDIT.md`. Measurable rules below are
enforced by `src/test/.../resources/VisualStyleConformanceTest.java` (runs in `./gradlew build`).
Everything else is judged in the client with `EntityShowcaseCapture` / `ItemShowcaseCapture`.

---

## 1. Geometry

Box UV, integer cube sizes, no cube thinner than 1 unit (a 1-unit plate ≥ 8 long is the only
place transparent texels are allowed: wing membranes). `uv_check` must report 0 holes.

| Class | Height | Bones | Cubes | What the extra geometry is *for* |
|---|---|---:|---:|---|
| Tiny | < 0.5 blocks | 3–12 | 5–15 | one oversized identity feature (a bill, ears, a shell); oversize vs hitbox is allowed |
| Small | 0.5–1 | 10–18 | 10–22 | head/body/limb split, face plane readable |
| Humanoid | 1–2 | 14–20 | 14–26 | head, torso, two-segment limbs, one costume/feature cube (beard, hat, ears) |
| Medium beast | 1–2 | 16–26 | 18–30 | two-segment legs, neck, tail chain of 2–3 |
| Large beast / mount | 2–3 | 20–36 | 22–45 | three-mass body (chest, barrel, croup), jointed neck |
| Flying | any | + 4–8 | + 6–14 | two-segment wings; feathered wings end in **stepped primaries** (`wing_pair(primaries=3)`), bat wings in a scalloped membrane plate |
| Dragon | 3.5–4.5 | 28–36 | 55–80 | the ten `dragon_model.py` rigs are the reference |
| Boss | ≥ 3 | ≤ 40 | ≤ 80 | silhouette furniture only (horns, crest, spines) |

**Add a cube only if it changes the silhouette.** A detail that only shows up close is texture,
not geometry. Test: render the rig black (`beast_preview.render_silhouette`) — if the new cube
does not change the outline, delete it.

## 2. Texture

- **Texel density:** 1 texel = 1 model unit = 1/16 block, everywhere in the world. Never resize a
  creature with a renderer `scale()`; use the definition `scale` (→ `Attributes.SCALE`) so model
  and hitbox move together at the same density.
- **Resolution:** blocks and items 16×16; mob-effect icons 18×18 (enforced); particles 8×8 with
  3–7 px drawn; GUI art authored at the size it is shown (the Obscurial meter was 2:1 until
  2026-09-27).
- **Edges:** hard alpha on anything opaque. Anti-aliased edges are not pixel art (the Ministry
  emblem had 102 alpha levels); only deliberately translucent effects keep soft alpha.
- **Palette size:** 4–8 colours per block face, 3–5 per material on a creature, ≤ 48 per creature
  sheet (enforced), posterised through `artgen_common.posterise` / `rigkit.emit`.
- **Noise:** texture is *structure*, not static. Block faces keep a mean neighbour luminance
  step ≥ 0.022 for building surfaces and ≥ 0.012 for any opaque face (enforced) — and in practice
  stay under vanilla andesite (~0.07).
- **Grain (creatures):** `rigkit.Skin(grain=...)` picks the material:
  - `strand` — fur, hair, bark, cloth: two-texel vertical strands on a staggered lattice,
    blended ~55 % toward the shade, every other one with a lit tip.
  - `fleck` — skin, hide, scale, feather, smoke: one-texel low-contrast flecks on the same lattice.
  - `speckle` — frozen legacy look, **only** for the approved redesigns (Dementor, Hippogriff,
    Basilisk, Phoenix, Kelpie). Never for new work.
- **Gradients:** never smooth. `Skin.ramp` is stepped (≤ 4 bands); a per-row blend is a painting.
- **Shadows and highlights:** a one-texel bevel (lit top row, shaded bottom row) and structure;
  Minecraft already shades faces by direction, so do not paint a second directional light.
- **Features survive:** `rigkit.emit` keeps any texel posterise would move by more than 40 RGB —
  eyes, pupils, gems never melt into the coat.

### Material vocabulary

| Material | Treatment |
|---|---|
| Fur / hair | `strand` grain, warmer underside via `bottom=`, mane/tail as banded plates |
| Feather | stepped `ramp` (3 bands) + `feathers()` tip rows; primaries as separate plates |
| Scale | `fleck` grain + a spine highlight; a repeating 2×2 motif on big serpents/dragons |
| Skin / hide | `fleck`, low density; wrinkle lines only at joints |
| Leather | flat fill, 1-texel darker seam lines |
| Metal | 4-step ramp, one 1-texel specular, darkest at the lower rim |
| Cloth | vertical 1-texel folds, lighter hem wear (`armor_model.paint_cloth`) |
| Stone | 4–6 colours, small bedded clusters, a few faint chisel dashes (`location_textures.rough_stone`) |
| Wood | grain along the board, broken every 3–6 texels |
| Magic (smoke, flame, light) | low internal contrast; the silhouette is carried by the glowmask |

## 3. Colour

Rich but controlled: mean saturation 0.30–0.50 (vanilla's range). Above 0.7 only for an
emissive accent, plumage the lore calls vivid (Fwooper, Phoenix), and house banners.
One accent per asset, where it means something. No `#000000` on a lit surface (enforced for
entity skins); avoid `#FFFFFF`.

**Semantic colours** — `spell/core/MagicColours.java` is the one list; particles, beams and spell
data take their colour from it, so a colour always means the same thing:

| Meaning | Constant | Hex |
|---|---|---|
| Magic (charms, transfiguration, movement, teleport) | `MAGIC` | `#9A6BFF` |
| Dark magic (curses, dark creatures) | `DARK_MAGIC` | `#5A1E6E` |
| Killing Curse | `DEATH` | `#3CFF6A` |
| Healing | `HEALING` | `#8FE3A0` |
| Fire | `FIRE` | `#FF6A1E` |
| Poison, venom, disease | `POISON` | `#8AA02A` |
| Protection (shields, wards, Patronus) | `PROTECTION` | `#9CCBFF` |
| Corruption (Obscurus, soul damage) | `CORRUPTION` | `#4E2440` |
| Light (Lumos, bioluminescence) | `LIGHT` | `#FFE89A` |
| Stun / disarm | `STUN` | `#E8262E` |
| Ice · Water · Lightning | `ICE` · `WATER` · `ELECTRIC` | `#9ADCFF` · `#3A8CFF` · `#FFF07A` |

Vanilla particles keep their vanilla meaning and must not stand in for ours: `HAPPY_VILLAGER`
is "trade accepted / bonemeal", `PORTAL` is the Enderman, `WITCH` the witch. Creature abilities
use `AbilitySupport.Particle.ARCANE / LIGHT / HEALING / DREAD / POISON` (mod sprites, tinted).

Every spell's `color` is opaque signed ARGB and never a corpus stand-in (enforced). A spell with no
designed colour takes the per-id hue its sigil already uses (`spell_sigils._stand_in`).

## 4. Lighting

One rule for the whole mod: **the world lights the model.** Light comes from the engine's face
shading plus the block light at the entity; skins carry no painted light direction beyond the
one-texel bevel (top lit, bottom shaded — same on every cube, every asset).

- Nothing renders full-bright except a glowmask. Form models and borrowed vanilla models take the
  player's `lightCoords` (`FormModelRenderer.lightOf`).
- Emissive is earned: a lore light source, one state organ, or a body of fire/light
  (Entity Design Bible, *Emissive*). Eyes glow only when lore says so (Opaleye).
- GUI art and icons: light from the top-left.

## 5. Eyes

| Kind | Eye |
|---|---|
| Mammal (prey) | 1×1 dark eye on the **side** faces of the head (`mark(head, east/west)`), never forward |
| Mammal (predator) | 2×1 forward pair, coloured iris, dark pupil texel |
| Bird | 1×1 dark eye in a lighter ring, side-set; raptors get a brow cube above it |
| Magical beast | as its body plan, plus one lore colour (the Unicorn's deep blue) |
| Undead / dark | no whites: a dark socket, or nothing (Dementor: a void under the hood) |
| Dragon | slit pupil on a coloured iris, set under a brow ridge; glowing only where the breed earns it |
| Humanoid | player-skin eyes: white sclera + 1-texel iris; goblins and elves oversized |

Eyes are protected colours: `rigkit.emit` never lets posterise merge them into the skin.

## 6. Silhouette

A creature must be nameable from its black silhouette **front, side, at 20 blocks, and at night**.
Order of work: silhouette → value pattern → colour → texture. Each body plan has one dominant read
(Entity Design Bible, *Silhouette rules*); the creature varies it, never replaces it. Canon identity
is one or two features, exaggerated: the Unicorn's upright pearl horn, the Thunderbird's hooked
beak and two pairs of wings, the Fire Crab's tortoise dome, the house-elf's ears.

Verify with `tools/beast_preview.py --sheet` (colour and silhouette) and the in-client capture
(`far` and `night` columns).

## 7. Animation

Readable Minecraft-scale motion, matched to box geometry: few keys, clear poses, no secondary
jiggle. Rotations are additive over the rest pose; author in rigkit sense (`rigkit.emit` converts).

| Beat | Rule |
|---|---|
| Anticipation | 0.1–0.25 s wind-up before any strike, opposite to the blow |
| Attack | 0.4–0.9 s, one big pose change; the one-shot controller wins over the loop |
| Idle | 3–5 s loop, one small motion + `IdleProfile` actions |
| Walk | 0.5–1.0 s gait; legs 24–34°; bob ≤ 0.5 units |
| Fly | 0.6–1.3 s beat; wings ±40° around ~80° spread |
| Hit | 0.4 s recoil (`reactions.py`) |
| Death | 1.2 s held collapse (`reactions.py`); vanilla's roll finishes it |
| Special | named clip gated by the definition (`clips`); never loop a special |

## 8. Scale

Normalised against the player (1.8) and canon, through the definition `scale`, which moves the
model and the hitbox together. Do not make a creature huge because it is magical.

| Tier | Height (blocks) | Examples |
|---|---|---|
| Small | 0.25–0.9 | Niffler, Cornish Pixie, Bowtruckle, Puffskein, House-elf (form) |
| Medium | 0.9–1.8 | Crup, Goblin, Unicorn |
| Large | 1.8–3 | Hippogriff, Thestral, Centaur 2.3, Thunderbird 2.7, Abraxan 2.9, Dementor 3.2 |
| Giant | 3–6 | Troll 3.6, Giant 5.2 |
| Dragon | 3.6–4.4 (breed order) | Horntail/Ironbelly 4.35 › Longhorn/Ridgeback/Hebridean 3.9 › Fireball/Short-Snout/Opaleye 3.8 › Welsh Green 3.6; Vipertooth (smallest) 1.9 |

Enforced hierarchy: giant > troll > player; centaur > player; Thunderbird > Hippogriff; every
large dragon ≥ troll; Horntail > Welsh Green. Changing `scale` changes the hitbox — check
pathing and spawn space for anything that spawns naturally.

---

## 9. Visual families (items and blocks)

One design system, individual materials: members of a family share structure, ramp logic and
furniture; each keeps its own colour and one identifying feature. Enforced parts are marked (T).

| Family | Shared | Individual |
|---|---|---|
| **Wood** (9 wandwood species) | vanilla-donor grain per part; 7 parts per species (T); bark on log sides and the outer ring of log tops; stripped wood held to vanilla's 45-level spread | **heartwood** = the wand tint in `wand_woods/<species>.json` — planks, stripped logs and ring centres read it directly (T: planks within 32 of tint x 0.86); bark and leaf ramps; berries |
| **Wand** | one rig, neutral light sheet, silhouette modules per wood (`appearance.handle/shaft/tip`) | wood tint = the species' heartwood (same number the blocks use); the Elder Wand keeps its own art |
| **Gilt / metals** | one architectural gold: `GILT_DARK #8F6522 · GILT #C9973A · GILT_LIGHT #EBC874` (= GUI tokens) on every gilded block (T) | Gringotts pairs it with ivory marble, the Ministry with green-black marble; held items use the item engine's brighter `gold` metal kind (same hue family, more contrast at 16px) |
| **Hogwarts** | warm sandstone ramp (`#8b8478`), bedded rough stone, coursed bricks, moss/crack variants | dark stone, flagstone floor, fluted pillars |
| **Hogsmeade** | cold grey stone, slate roof, red chimney brick, dark timber | Honeydukes pastel stripes, Three Broomsticks timber |
| **Diagon Alley** | red-brown brick, grey cobbles, shopfront timber | painted boards (green, purple) that show joints and wood through chipped paint |
| **Gringotts** | ivory/pale marble + gilt + dark iron | banking-hall chequer floor, goblin stonework, vault bricks |
| **Ministry** | near-black green-cast marble + gilt + dark wood | gilded chevron trim, gold-veined marble, dark tile |
| **Items** (one engine, `item_sprites.py`) | hand-drawn silhouette, computed shading lit top-left, tinted outline; 16x16 | families by object: books (overhanging boards, banded spine), potions (one glass, tinted liquid), coins (Galleon gold > Sickle silver > Knut bronze), creature drops, sweets, robes (cloth = the worn set's cloth), brooms |
| **Effect icons** (18x18, T) | same engine, 16px drawing centred | one object per effect |

---

## Tools

| Asset | Generator (`tools/`, git-ignored) |
|---|---|
| Creature rigs + skins | `*_model.py` on `rigkit.py` + `bodies.py` (`augurey_model.py` since 2026-09-27) |
| Bestiary portraits, spawn eggs | `bestiary_portraits.py`, `spawn_eggs.py` — re-run after any skin change |
| Player-form rigs | `house_elf_model.py`, `veela_harpy_model.py`, `goblin_model.py` |
| Animagus skins on vanilla models | `animagus_skins.py` |
| Location blocks | `location_textures.py` (gilt ramp `GILT_*`, `painted_boards`, chequer `tiles(alt=)`) |
| Wood blocks | `wandwood_textures.py` (heartwood read from `wand_woods/*.json`) |
| Armour | `armor_model.py` |
| Particles | `particle_sprites.py` |
| Mob-effect icons | `effect_sprites.py` (item-sprite engine, 18x18), `effect_icons.py` (vanilla-derived) |
| Hand-made textures, one-off fixes | `texture_cleanup.py` (handbook book/emblem, Obscurial stress meter) |
| Items | `item_sprites.py`, `item_models_3d.py`, `item_geo.py` |
| GUI | `gui_parchment.py`, `gui_chrome.py`, `spell_sigils.py` |
