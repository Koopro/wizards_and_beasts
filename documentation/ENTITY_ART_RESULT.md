# Entity Art — Result (2026-09-25)

Implementation of `documentation/ENTITY_ART_AUDIT.md`. Branch `dev`, Minecraft 1.21.11, NeoForge 21.11,
GeckoLib 5.4.5. Every creature was re-photographed in a running client after the work
(`EntityShowcaseCapture`, views front/side/back/far/night plus `hurt` and `death`); the verdicts below
come from those sheets, not from the files.

**Validation:** `./gradlew build` green after every batch — 2044 unit tests, 0 failures.

---

## Two findings that changed every rig

### 1. One-shot clips never played (engine)

GeckoLib applies controllers in registration order and the last non-additive one owns the bones it
keys. The locomotion loop was registered after the one-shot controller, so idle/walk overwrote every
attack, hit, ambient and idle-action clip on every data-driven creature. Fixed in
`GenericBeastEntity.registerControllers`: a new `addMovementController` hook runs first and the four
locomotion classes override the hook instead of the method. No new animation system; the existing
clip gate is unchanged.

### 2. The offline previewer drew Y and Z rotations backwards

GeckoLib mirrors the geo in X and applies `Rz(z)·Ry(−y)·Rx(−x)` in the mirrored space
(`BakedModelFactory.constructBone`; keyframes baked the same way in `BakedAnimationsAdapter`). The
previewer every rig was tuned against applied that formula to unmirrored geometry — X right, **Y and Z
inverted**. Rigs whose shape rests on pitch were unaffected; rigs that carry shape in yaw or roll
rendered differently in the game than in every preview. The Acromantula is the proof: legs arched in
the preview, rose in a vertical bundle in the client.

- `beast_preview.euler_matrix` now reproduces GeckoLib exactly (conjugated rotations + a final X
  mirror); validated by re-rendering the Acromantula and matching the client capture.
- `rigkit.emit` converts on the way out (`to_game_sense`: negate Y and Z on rest, cube and keyframe
  rotations), so every generator keeps authoring in rigkit's documented sense and the game now draws
  what its author saw.
- All 86 rigkit generators were regenerated through it. Textures were byte-identical; only rotations
  changed. The client capture afterwards showed no regressions; the one visible side effect is the
  Matagot's ears, which now splay at their authored angle and read slightly hare-like.

---

## Batches

### Batch 0 — controller order (all 96 data-driven creatures)

`GenericBeastEntity`, `GenericGroundBeastEntity`, `GenericFlyingBeastEntity`,
`GenericAquaticBeastEntity`, `GenericSessileBeastEntity`. See finding 1. Verified in the client: the
`hurt` column of the after-capture shows hit reactions on every creature that has one (heads thrown
up, wings flared, legs braced), which the before-state could not have shown.

### Batch 1 — the ten dragons

| | Before | After |
|---|---|---|
| Generator | `tools/dragons/` (parametric) + `horntail_model.py` | `tools/dragon_model.py` on rigkit, one skeleton, per-breed furniture |
| Silhouette | box horse on stilts, plank wings, colour-only breeds | crouched reptile stance, raked neck, long balancing tail, wings folded as a raised sail and spread in flight |
| UV holes | 17–293 per breed | 0 |
| Scale | render-only `dragon.scale` 1.4–2.2 (texels up to 2.2× the roster's) | authored at true size, `dragon.scale` removed from all ten definitions |
| Sheets | 128×112 – 128×200 at 32–36 % use | 128×136 – 256×208 (big breeds on 256-wide sheets instead of 128×424 strips) |
| Clips | idle, fly, breath, bite | + hit, death, stretch, shake (Bible idle profile 300/700, declared); breath/bite stay on `dragon_action` |
| Glow | eyes on 7 breeds | eyes only where lore earns it (Opaleye, Hebridean, Short-Snout, Ridgeback, Ironbelly) |

Per-breed deltas follow the Entity Design Bible: Horntail spiked tail with mace and bronze crown,
Longhorn two forward golden horns (the old rig had one, vertical), Ironbelly massive and low with ram
horns, Opaleye slender with a gold crown and pupil-less glowing eyes, Short-Snout blunt muzzle,
Fireball face fringe and bulging eyes, Vipertooth small and fanged, Ridgeback raked quills, Hebridean
bat wings with deep scallops and an arrow tail, Welsh Green the plain baseline. Wing membranes use
real cutouts (scalloped trailing edge, tapered hand) — `uv_check` now allows transparent texels on a
1-unit-thick plate at least 8 long, and nowhere else.

**Palettes, per-breed folds and the horn/quill/band variants were then restyled by a parallel session
from the user's reference sheet** (see its memory note, *Dragon Reference Restyle 2026-09-25*); the
client capture includes that version.

### Batch 2 — the Dementor

`tools/dementor_model.py`, new. Replaces the one-box placeholder (and its 64-vs-256 sheet mismatch):
hooded, hunched robe with a void under the hood, long sleeves and scabbed grey hands, a hem cut into
rags. 6 bones, 23 cubes, 128×72, no glowmask (a Dementor is an absence of light). Keeps `root` (the
Muggle-view renderer zero-scales it) and re-authors the six clips `DementorEntity` asks for —
`idle_drift`, `pursue`, `kiss_windup`, `kiss_resolve`, `repelled`, `dissipate` — around the new
rest pose; the old ones carried a 35° lean that only made sense for the slab.

### Batch 3 — bespoke placeholders

| Creature | Before | After |
|---|---|---|
| Niffler (+ Baby, same rig at SCALE 0.5) | black box, 32×32 noise, 15 UV holes | pale bill, digging paddles with claws, blue-grey fur sheen, pouch seam; 64×40, 0 holes |
| Cornish Pixie | blue stick with panes, 32×32 noise | big pointed-eared grinning head, dangling limbs, two pairs of veined insect wings; 64×32 |
| Streeler | slug with a flat crate | three-whorl shell with the spiral painted on both sides, banded in its colour cycle; eye stalks; 64×64 |

All three keep exactly the clip names their Java binds (`idle` + `walk`/`fly`); bespoke entities have
no reaction-clip gate, so no hit/death was added to them.

### Batch 4 — silhouette fixes in existing generators

- **Acromantula, Blast-Ended Skrewt, Chizpurfle, Fire Crab, Quintaped, Mackled Malaclaw** — legs
  now arch over and plant (finding 2); death curls them.
- **Horned Serpent** — the alternating yaws cancelled into a straight bar; now a wide S with the
  front reared.
- **Runespoor** — the neck "lift" was a positive X on a forward chain, which lowers it; the three
  necks went into the floor and `ground()` flattened the snake. Now three heads fan up and forward.
- **Glumbumble** — wings now stand in a V (finding 2), so it reads as a flier.
- **Moke, Salamander** — `lizard.py`'s splay sign was backwards all along (tucked the legs under the
  body); the old inversion had hidden it. Legs sprawl again.

### Batch 5 — shared clips

`tools/reactions.py`, new: reads a skeleton's roles from bone names (torso, necks, heads, jaws, tail
and serpent chains, fore/hind/arm/splayed limbs, wings, ears) and builds, only where the generator
has not authored one:

- `hit` (0.4 s) — recoil, head up, tail lash, wings flare, forelimbs brace.
- `death` (1.2 s, held) — legs fold, neck and head slack, jaw drops, wings sag, tail droops, serpents
  lose their line; vanilla's roll-over does the rest.
- `attack` (0.6 s) — only for creatures that deal damage and are not passive (Boggart, Fwooper,
  Leprechaun, Ghoul, Obscurus gained one).
- idle actions named by the creature's `IdleProfile` — `graze sniff shake stretch preen ruffle peek
  grunt taste_air coil groom skitter hover flit squirm settle roll drift`.

`rigkit.emit` runs it for every creature with a definition and declares the clips;
`tools/add_reactions.py` does the same for the six hand-built rigs (Basilisk, Ghoul, Hippogriff,
Obscurus, Occamy, Werewolf) without regenerating them, and declared the Occamy's `attack`, which was
on disk and never reachable.

| | Before | After |
|---|---:|---:|
| Data-driven creatures with `hit`/`flinch` | 38 / 96 | **96 / 96** |
| with `death` | 0 / 96 | **96 / 96** |
| with any idle action | 3 / 96 | **94 / 96** (Flobberworm, Horklump: none by design) |
| damage-dealers with no attack clip | 6 | 0 |

### Batch 6 — Fairy glow

Glowmask restricted to wings and hair (452 → 268 texels); at night the fairy keeps a body and face
instead of flattening into one pastel cut-out.

### Batch 7 — UV holes on hand-built rigs

`tools/snap_sizes.py`: each fractional cube size snapped up to the island `boxuv` painted for,
re-centred (growth under one unit). Thestral 258 → 0, Hippogriff 78 → 0, Ghoul 55 → 0, Phoenix 42 → 0.
Silhouettes unchanged (previewed). Obscurus keeps 320 transparent texels — they are painted gaps in
its smoke, not addressing errors. Augurey left as is: its 32×32 sheet cannot hold the snapped tail.

### Bestiary portraits

The 30 creatures whose rigs changed visibly had their Bestiary icon and silhouette re-rendered with
`tools/bestiary_portraits.py` (portraits are rendered from the rig, so they had gone stale).

---

## Files

- **Java:** `entity/creature/GenericBeastEntity.java` + the four locomotion subclasses (controller
  order); `client/debug/EntityShowcaseCapture.java` (new, dev-only, inert unless
  `WB_ENTITY_SHOWCASE` is set).
- **Assets:** 98 geo and 102 animation files; textures for the 10 dragons, Dementor, Niffler,
  Cornish Pixie, Streeler, Fairy glowmask; glowmasks removed for Chinese Fireball, Hungarian
  Horntail, Peruvian Vipertooth, added for Ridgeback and Ironbelly; 29 Bestiary icons + silhouettes changed (30 re-rendered).
- **Data:** `clips` declared across 96 creature definitions; `dragon.scale` removed and a Bible idle
  profile added on the ten dragons.
- **Tools (git-ignored `tools/`):** `entity_art_inventory.py`, `entity_contact_sheet.py`,
  `entity_art_verdicts.py`, `pose_preview.py`, `reactions.py`, `add_reactions.py`, `snap_sizes.py`,
  `dragon_model.py`, `dementor_model.py`, `niffler_model.py`, `cornish_pixie_model.py`,
  `streeler_model.py`; `rigkit.py`, `beast_preview.py`, `uv_check.py`, `lizard.py` and five generators
  edited. **Do not run `tools/dragons/generate.py` or `tools/horntail_model.py` again** — they would
  overwrite the rebuilt dragons.

## Intentional exceptions

- Hidebehind is invisible to whoever faces it, and Moke, Dugbog, Pogrebin, Rougarou, Tebo, Yeti,
  Mooncalf, Boggart and Thestral render translucent or hidden in the capture by design; their skins
  were judged offline.
- `run` clips were not authored: the movement controllers only ask for `idle` + `walk`/`fly`/`swim`.
- Hitboxes were not changed (gameplay, registered twice).
- Hand-built rigs outside rigkit were not re-oriented to the new rotation sense — their in-game look
  was already judged acceptable and changing it is a redesign, not a fix.

## Remaining art debt

- Heavy mottle/dither on Abraxan, Graphorn, Kappa, Knarl, Rougarou, Centaur's human half, Giant.
- Red Cap renders 2× its hitbox height; long low creatures (Ashwinder, Moke, Salamander, Flobberworm,
  Runespoor) sit in boxes several times their height.
- Augurey (3 UV holes) and the eight brooms, `player`, `protego_shield` (non-creatures, 1–3 each).
- Grindylow declares an ambient sound with no voice clip.
- Bowtruckle and Augurey still wear the old noise skins (silhouettes read).
- Code, for the owner: `CreatureIdleGoal` never runs in water, so aquatic `drift`/`roll` cannot fire
  for a creature in its habitat; flying creatures play `fly` when walking.
- Nothing here has been committed; the tree is shared with the parallel dragon session.
