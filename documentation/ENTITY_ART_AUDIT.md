# Entity Art Audit — 2026-09-25

Scope: the **art** of every creature — GeckoLib rigs, UV layout, skins, glowmasks, animation clips,
drawn scale against the hitbox. Entity architecture, AI and spawning are out of scope except where
they decide whether a clip can play. Brooms, projectiles, beams, the Patronus, the Protego ward, the
duelling dummy, the form mannequin and the chocolate frog are not creatures and are only listed (§5).

Baseline: `dev` @ `44914dc0`. The last creature art work was the five rigkit waves of 2026-09-16
(`7c7151ff` … `975e21b4`, 82 rigs) — **none of which had been looked at in a running client**. This
audit is that look.

---

## 1. Method

1. **In-game capture.** A new dev-only harness, `client/debug/EntityShowcaseCapture` (env-gated, inert
   in production, the entity twin of `ItemShowcaseCapture`), opens a disposable world, builds a plain
   stone stage in open sky and summons each creature alone with its AI off and a villager beside it
   as the yardstick. Five shots per creature: **front ¾** (with villager), **side**, **back ¾**,
   **far** (20 blocks, with villager) and **night** (glow check). Optional `hurt` and `death` views
   damage or kill the creature and shoot mid-clip. 109 creatures × 5 = 545 shots, assembled into
   contact sheets by `tools/entity_contact_sheet.py`. **Every visual verdict below was read off those
   sheets.** The creatures that are invisible or camouflaged in-game by design (dugbog, moke,
   pogrebin, rougarou, tebo, thestral, yeti, mooncalf, hidebehind, boggart) were additionally rendered
   offline with `tools/beast_preview.py` to judge the skin.
2. **Inventory by script.** `tools/entity_art_inventory.py` reads every definition, geo, animation,
   texture and glowmask: bone and cube counts, bones no clip ever moves, posed model extent (rest
   rotations applied), sheet size and use, glow texels, `uv_check` holes, clips on disk vs reachable
   vs expected. Output `build/entity-art/inventory.json`.
3. **Source reading** where a picture and the data disagreed — which is how the controller-order
   defect (§3.1) and the Dementor placeholder (§3.3) were found.

Reproduce:
```
python tools/entity_art_inventory.py
WB_ENTITY_SHOWCASE=build/entity-art/all.txt WB_ENTITY_SHOWCASE_WORLD=wb_entity_showcase \
  WB_ENTITY_SHOWCASE_OUT=entity_before ./gradlew runClient2
python tools/entity_contact_sheet.py runs/client2/screenshots/entity_before build/entity-art/before
```
(`all.txt` holds `id height size` per line; the world is a copy of any disposable save.)

---

## 2. Summary

| | |
|---|---:|
| Creatures audited | **109** (96 data-driven + 13 bespoke living entities) |
| GOOD — reads as its species, leave alone | 56 |
| OK — reads, minor debt | 33 |
| POOR — misreads, or placeholder detail | 10 |
| BROKEN — wrong at a glance | 10 |
| Creatures sampling transparent texels (`uv_check`) | 20, 1,286 holes |
| Rigs with a `death` clip | **0** |
| Data-driven rigs with a `hit`/`flinch` clip | 38 of 96 |
| Rigs covering any idle action their `IdleProfile` names | **3** of 96 |
| Creatures with a glowmask | 50 |

The roster is in better shape than "technically functional": half of it reads cleanly at distance and
most of the rest carries only texture debt. **The problems are concentrated**, and almost all of them
sit outside the 2026-09-16 rigkit waves — in the rigs those waves did not touch: the ten dragons (an
older parametric generator), the Dementor (never rebuilt since the initial upload) and the bespoke
entities built before rigkit existed.

---

## 3. Findings, by severity

### 3.1 Every one-shot clip was overwritten by the idle/walk loop (engine, all 96 data-driven creatures)

GeckoLib 5.4.5 applies controllers in registration order (`AnimatableManager` builds an
`Object2ObjectArrayMap`; `AnimationProcessor.createBoneSnapshots` walks it in order) and a
non-additive controller **overwrites** every bone its current clip keys. `GenericBeastEntity`
registered its one-shot `beast_action` controller first and each locomotion subclass then added the
movement loop **after** it. So while an attack, hit, ambient or idle-action clip played, the idle or
walk cycle rewrote head, legs and tail every frame, and a one-shot moved only the bones the idle
happened not to touch. Every rig's `bite`/`strike`/`hiss`/`call` was largely invisible, and every
clip this pass adds would have been too.

Not art, but no art in this pass can be seen without it. **Fix: loop first, one-shots second** —
`GenericBeastEntity.registerControllers` calls a new `addMovementController` hook before building
`beast_action`; the four locomotion classes override the hook instead of the method. `DragonEntity`
already registered its `dragon_action` last, which is why dragon `bite`/`breath` were the only
one-shots that ever played in full.

### 3.2 The ten dragons are the worst art in the mod (BROKEN ×9, POOR ×1)

Nine come from `tools/dragons/` (a parametric generator older than rigkit); the Horntail is hand-built
in `tools/horntail_model.py`. In-game the nine parametric breeds are one shape: **a box horse on
stilts** — legs a full block long, a short boxy barrel, a head hung off a two-cube neck — with **flat
plank wings** sticking straight out sideways and a sheet painted in horizontal brick stripes. Breed
identity is colour only. The Bible's per-breed silhouette deltas (Horntail's tail, Longhorn's two
forward horns, Short-Snout's snub, Ironbelly's mass, Opaleye's upright neck) are absent or wrong — the
Longhorn has **one** vertical horn.

Measured: 17–27 transparent UV holes per breed (fractional sizes everywhere), 12–14 of ~24 bones never
animated, sheets 128×112 – 128×200 at 32–36 % use, Ironbelly and Longhorn rendering 2.0× / 1.8× their
hitbox height (render-only `dragon.scale` over a registry-frozen box). The Horntail is the one dragon
with a silhouette (horn crown, spiked tail) but its folded wings collapse into a jumble at the flank,
its skin is camouflage noise, and it samples **293** transparent texels.

Dragons are the Bible's hardest identity case and the creatures a player most expects to be good.
**Rebuild all ten** from one breed-parametrised rigkit generator.

### 3.3 The Dementor is still the placeholder (BROKEN)

`dementor.geo.json` is one 12×46×8 cube on a `cloak` bone; `head`, `armR`, `armL` and `body` are
empty bones that its six animations drive. The geo declares a 64×64 sheet, the PNG is 256×256, so the
cube samples a corner of an unrelated painting and renders as a **tilted rainbow-striped slab**. One of
the mod's most important threats has no hood, no hands and no read. **Rebuild**, keeping the `root`
bone (the Muggle-view renderer scales it to zero) and the six clip names `DementorEntity` asks for.

### 3.4 Transparent UV holes on 20 creatures

`uv_check` reproduces GeckoLib's box-UV sampling (floored sizes). Holes render as see-through gashes
and, at 1–2 px, as speckle. Everything the rigkit waves rebuilt is clean; the holes are all in
pre-rigkit rigs:

| Rig | Holes | Source |
|---|---:|---|
| obscurus | 325 | `obscurus_model.py` (hand-built, fractional sizes) |
| hungarian_horntail | 293 | `horntail_model.py` |
| thestral | 258 | `thestral_model.py` |
| hippogriff | 78 | `hippogriff_model.py` |
| ghoul | 55 | `ghoul_model.py` |
| phoenix | 42 | `phoenix_model.py` |
| 9 parametric dragons | 17–27 each | `tools/dragons/` |
| niffler (+ baby) | 15 | bulk generator |
| augurey, cornish_pixie, streeler | 2–3 each | bulk generator |

The dragons, niffler, pixie and streeler are rebuilt anyway (§3.2, §3.5). The rest keep their
silhouettes and need **size snapping only**.

### 3.5 Bespoke creatures still on placeholder-grade art

Built before rigkit, on the "placeholder-grade, not pixel art" noise painter: 32×32 sheets carrying
116–228 colours of noise.

- **Niffler** (and the baby, which shares the rig) — POOR. One of the most recognisable creatures in
  the canon renders as a featureless black box: no duck bill, no pale muzzle, no belly pouch.
- **Cornish Pixie** — POOR. A blue stick with two panes; no face, no pointed ears.
- **Streeler** — POOR. The giant snail's shell is a flat crate.
- Bowtruckle and Augurey — OK: noisy skins, silhouettes read. Not rebuilt.

### 3.6 Silhouette failures among the rigkit rigs

- **Acromantula** — POOR. All eight legs rise from the body in a vertical bundle and read as a hand;
  a spider reads by the knee arch above the body and the feet planted wide.
- **Horned Serpent** — POOR. A dead-straight plank. The Bible: a serpent's read is "a long unbroken
  curve".
- **Runespoor** — POOR. A flat line on the floor; the three heads, its whole identity, cannot be told
  apart at any distance.
- **Glumbumble** — POOR. A grey box on legs; no wing read, reads as a mammal.

### 3.7 Clip coverage

- **Death:** no rig declares `death`/`die`/`collapse`; the hook in `GenericBeastEntity.die` has fired
  into nothing since it landed.
- **Hit:** 58 of 96 data-driven creatures have no `hit`/`flinch`.
- **Idle actions:** `IdleProfile` names 19 actions across ten body plans; 93 of 96 rigs cover none.
- **Attack:** six damage-dealing non-dragons have no playable attack clip — `occamy` has an `attack`
  clip on disk its definition never declares; `boggart`, `fwooper`, `ghoul`, `leprechaun`, `obscurus`
  have none. (Dragons attack through `dragon_action`, correctly.)
- `rigkit.TRIGGERABLE` still held only the pre-foundation vocabulary, so a generator could not emit a
  `death` or idle-action clip at all — `emit` refused them as untriggerable.

Not art, recorded for the owner: `CreatureIdleGoal.canUse` refuses while `isInWater()`, so the aquatic
body plan's `drift`/`roll` never fire for a creature that lives in water; and a `FLYING` creature
plays `fly` while walking on the ground (the Hippogriff's `walk` clip is unreachable).

### 3.8 Glow

50 creatures ship a glowmask and 49 use it the Bible's way — eyes, one organ, a flame. **Fairy**
does not: 100 % of its painted texels glow, so at night it is a flat pastel cut-out and its wings
vanish into the body. Salamander (32 %), Swooping Evil (12 %) and Billywig (18 %) are high but read
correctly — flame, iridescent wing, sting.

### 3.9 Scale against hitbox (posed model extent)

Deliberate oversize is normal for small mobs (the mooncalf lesson: a 1-block mob cannot carry a face at
1:1), so this lists only what hurts play:

- **Ukrainian Ironbelly / Romanian Longhorn** render 2.0× / 1.8× their hitbox height — player hits
  pass through the upper body. Fixed by the dragon rebuild authoring to the box.
- **Red Cap** stands 2.0× its 0.75-block box; its head cannot be hit.
- Low, long creatures (ashwinder, moke, salamander, flobberworm, runespoor) sit in boxes 3–6× taller
  than the animal. Hitboxes are registered twice (definition JSON and `ModCreatures.Spec`) and are
  gameplay, so they are recorded as debt rather than changed.

### 3.10 Texture debt not worth a rebuild

Heavy mottle/dither reads as a coat pattern on Abraxan, Graphorn, Kappa, Knarl (blotches too large),
Rougarou, Centaur's human half and Giant. Every one of those silhouettes reads; per the brief ("never
replace a good asset merely because it is old") they stay, listed as debt.

---

## 4. Priority and batches

Ordered by the brief's priority list (silhouette → texture → UV → essential animation → scale → lore):

| Batch | Creatures | Work |
|---|---|---|
| **0** | all data-driven | controller order (§3.1) — prerequisite for every clip below |
| **1** | 10 dragons | full rebuild on rigkit: silhouette, per-breed furniture, UV, skin, glow, clips, fit to hitbox |
| **2** | dementor | full rebuild: hooded cloak, skeletal hands, tattered hem; keep its six clips and `root` |
| **3** | niffler (+baby), cornish pixie, streeler | rebuild the three POOR bespoke creatures |
| **4** | acromantula, horned serpent, runespoor, glumbumble | silhouette fixes in their existing generators |
| **5** | every rigkit creature | `hit`, `death` and body-plan idle actions from shared `tools/reactions.py`; declare `occamy` attack |
| **6** | fairy | glow restricted to wings and aura |
| **7** | thestral, phoenix, ghoul, obscurus, hippogriff | UV size snapping, same silhouette |

Deliberately **not** in scope: the 56 GOOD creatures' art, hitbox changes (gameplay), the aquatic idle
gate (code), and anything spawning-related.

---

## 5. Non-creature entities (not audited as creatures)

`broom` (8 GeckoLib variants; one fractional-size UV hole each), `spell_projectile`,
`beast_hex_projectile`, `spell_clash`, `beam`, `wizarding_thrown`, `patronus` (renders borrowed creature
models), `protego_shield` (3 UV holes), `duelling_dummy`, `form_mannequin`, `chocolate_frog`.

---

## 6. Per-creature table

Columns: model id; sheet size; bone count; clips on disk; clips the entity asks for that the rig or
definition lacks (`idle-action(…)` lists the profile's names); glow texels; render scale; hitbox; posed
rest-pose extent in blocks (x × y × z, before scale); `uv_check` holes; verdict from the capture.
Paths: `geckolib/models/entity/<model>.geo.json`, `textures/entity/<model>.png`,
`geckolib/animations/entity/<model>.animation.json`. `missing` counts `attack` for dragons, which attack
through `dragon_action` — read those as satisfied. Kneazle already shows the batch-5 clips (it was the
test subject for `reactions.py`).

| ID | Model / texture | Tex | Bones | Clips | Missing | Glow | Scale | Hitbox | Model (posed, blocks) | UV holes | Verdict | Notes |
|---|---|---|---:|---|---|---|---:|---|---|---:|---|---|
| `abraxan` | `abraxan` | 128x168 | 23 | call, fly, idle, strike | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 2.7x2.9 | 1.28x3.13x3.51 | 0 | **OK** | palomino winged horse reads; spotted dither everywhere reads as a coat pattern it should not have |
| `acromantula` | `acromantula` | 128x88 | 46 | bite, hiss, idle, walk | hit, death, idle-action(groom/skitter) | 8 tx | 1.0 | 1.7x1.9 | 4.05x1.6x3.28 | 0 | **POOR** | eight legs rise in a vertical bundle like fingers; no arch-and-plant spider stance, reads as a hand |
| `aethonan` | `aethonan` | 128x72 | 23 | call, fly, idle, strike | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 1.7x1.9 | 0.81x2.0x2.4 | 0 | **OK** | chestnut winged horse; wing slabs banded like barrel staves |
| `antipodean_opaleye` | `antipodean_opaleye` | 128x128 | 22 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 4 tx | 1.4 | 2.7x2.9 | 3.69x2.1x3.9 | 17 | **BROKEN** | old parametric dragon: horse on stilts, plank wings, brick-striped sheet, 17 UV holes |
| `ashwinder` | `ashwinder` | 64x16 | 8 | hiss, idle, strike, walk | hit, death, idle-action(coil/taste_air) | 6 tx | 1.0 | 0.65x0.75 | 0.32x0.12x1.64 | 0 | **OK** | thin ash serpent with ember glow; tiny as canon asks |
| `augurey` | `augurey` | 32x32 | 8 | fly, idle | - | - | 1.0 | 0.5x0.7 | 1.18x0.87x0.92 | 3 | **OK** | hunched dark-green bird, reads; old 32x32 noise skin, 3 UV holes |
| `baby_niffler` | `niffler` | 32x32 | 8 | idle, walk | - | - | 0.5 | 0.2x0.25 | 0.34x0.39x0.81 | 15 | **POOR** | shares niffler rig: featureless black box |
| `basilisk` | `basilisk` | 128x160 | 23 | gaze, hiss, idle, strike, walk | hit, death, idle-action(coil/taste_air) | 44 tx | 1.4 | 2.7x2.9 | 2.65x2.88x4.31 | 0 | **GOOD** | great S-curve, crest, yellow eyes; heavy mottle |
| `billywig` | `billywig` | 64x16 | 3 | call, flinch, fly, idle | death, idle-action(hover/flit) | 51 tx | 1.0 | 0.45x0.45 | 0.5x0.41x0.94 | 0 | **OK** | blue beetle with rotor wings |
| `blast_ended_skrewt` | `blast_ended_skrewt` | 128x32 | 38 | hiss, idle, strike, walk | hit, death, idle-action(groom/skitter) | 30 tx | 1.0 | 1.7x1.9 | 2.45x1.41x2.41 | 0 | **OK** | pale legs splay upward, blast end glows; reads as crustacean |
| `boggart` | `boggart` | 64x56 | 7 | call, flinch, idle, walk | attack, death, idle-action(squirm/settle) | 2 tx | 1.0 | 0.95x1.25 | 0.91x1.47x0.79 | 0 | **OK** | translucent shapeless by design |
| `bowtruckle` | `bowtruckle` | 32x32 | 6 | idle, walk | - | - | 1.0 | 0.4x0.8 | 0.25x0.81x0.12 | 0 | **OK** | twig figure with leaf head; old 32x32 noise skin (116 colours) but reads |
| `bundimun` | `bundimun` | 64x16 | 10 | flinch, idle, walk | death, idle-action(squirm/settle) | 32 tx | 1.0 | 0.5x0.4 | 0.78x0.51x0.5 | 0 | **GOOD** | mossy blob with fungal crest |
| `centaur` | `centaur` | 128x56 | 24 | attack, call, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 1.7x1.9 | 0.81x2.28x1.67 | 0 | **OK** | horse body reads; human half blotchy and faceless at distance |
| `chimaera` | `chimaera` | 128x64 | 21 | bite, howl, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 4 tx | 1.0 | 1.7x1.9 | 0.88x1.98x3.51 | 0 | **GOOD** | lion head, goat body, serpent tail: the composite reads |
| `chinese_fireball` | `chinese_fireball` | 128x144 | 23 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 5 tx | 1.8 | 2.7x2.9 | 3.69x2.1x3.65 | 19 | **BROKEN** | old parametric dragon; face fringe lost in brick stripes, 19 UV holes |
| `chizpurfle` | `chizpurfle` | 64x16 | 33 | bite, flinch, idle, walk | death, idle-action(groom/skitter) | - | 1.0 | 0.45x0.45 | 0.81x0.25x0.49 | 0 | **OK** | tiny red crab-mite |
| `clabbert` | `clabbert` | 64x32 | 16 | call, flinch, idle, walk | death, idle-action(peek/scratch) | 16 tx | 1.0 | 0.65x0.75 | 0.63x1.09x0.5 | 0 | **GOOD** | green frog-monkey, pustule glows at night |
| `common_welsh_green` | `common_welsh_green` | 128x144 | 24 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | - | 1.4 | 2.7x2.9 | 3.56x2.21x3.87 | 27 | **BROKEN** | old parametric dragon, 27 UV holes |
| `cornish_pixie` | `cornish_pixie` | 32x32 | 6 | fly, idle | - | - | 1.0 | 0.4x0.6 | 0.75x0.62x0.19 | 2 | **POOR** | blue stick figure with two pale panes; no face, no ears, 32x32 noise skin |
| `crup` | `crup` | 64x40 | 18 | bite, call, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.5x0.87x1.49 | 0 | **GOOD** | Jack Russell with forked tail |
| `dementor` | `dementor` | 256x256 | 6 | dissipate, idle_drift, kiss_resolve, kiss_windup, pursue, repelled | idle, fly | - | 1.0 | 0.9x3.2 | 0.75x2.88x0.5 | 1 | **BROKEN** | placeholder: one 12x46x8 box, empty head/arm bones, geo says 64x64 while the PNG is 256x256 so the sheet smears into rainbow stripes |
| `demiguise` | `demiguise` | 64x40 | 14 | call, flinch, idle, walk | death, idle-action(peek/scratch) | - | 1.0 | 0.65x0.75 | 0.69x1.11x0.57 | 0 | **OK** | silver ape, dark face; hair striping heavy |
| `diricawl` | `diricawl` | 64x40 | 14 | call, flinch, idle, walk | death, idle-action(preen/ruffle/stretch) | - | 1.0 | 0.65x0.75 | 0.65x1.0x1.06 | 0 | **GOOD** | dodo silhouette |
| `doxy` | `doxy` | 64x16 | 11 | bite, flinch, fly, idle | death, idle-action(hover/flit) | - | 1.0 | 0.45x0.45 | 0.47x0.76x0.42 | 0 | **GOOD** | black biter with red eyes and beetle wings |
| `dugbog` | `dugbog` | 128x48 | 15 | bite, hiss, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.95x1.25 | 0.73x0.85x3.0 | 0 | **GOOD** | log-backed croc (camouflaged in-game by design) |
| `erkling` | `erkling` | 64x24 | 18 | attack, call, idle, walk | hit, death, idle-action(peek/scratch) | - | 1.0 | 0.65x0.75 | 0.85x1.18x0.6 | 0 | **GOOD** | elfin, big nose, tunic |
| `erumpent` | `erumpent` | 128x96 | 20 | attack, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 40 tx | 1.0 | 1.7x1.9 | 1.25x1.77x4.01 | 0 | **GOOD** | rhino with glowing horn |
| `fairy` | `fairy` | 64x16 | 11 | flinch, fly, idle, song | death, idle-action(hover/flit) | 452 tx | 1.0 | 0.45x0.45 | 0.5x0.7x0.46 | 0 | **POOR** | 100% of the skin glows: flat pastel shape at night, wings lost |
| `fire_crab` | `fire_crab` | 64x32 | 33 | attack, hiss, idle, walk | hit, death, idle-action(groom/skitter) | 93 tx | 1.0 | 0.65x0.75 | 1.49x0.51x1.02 | 0 | **GOOD** | jewelled shell crab |
| `flobberworm` | `flobberworm` | 64x8 | 6 | flinch, idle, walk | death | - | 1.0 | 0.45x0.45 | 0.23x0.19x0.87 | 0 | **GOOD** | segmented tube, as the Bible says |
| `fwooper` | `fwooper` | 64x40 | 13 | flinch, fly, idle, song | attack, death, idle-action(preen/ruffle/stretch) | - | 1.0 | 0.65x0.75 | 0.62x0.94x1.11 | 0 | **OK** | bright plumage reads; wings are striped crates |
| `ghoul` | `ghoul` | 64x64 | 18 | groan, idle, walk | attack, hit, death, idle-action(peek/scratch) | 2 tx | 1.0 | 0.7x1.9 | 0.73x1.78x0.75 | 55 | **OK** | stooped green attic ghoul, glowing eyes; 55 UV holes |
| `giant` | `giant` | 128x136 | 16 | attack, groan, idle, walk | hit, death, idle-action(stretch/grunt) | - | 1.0 | 2.7x2.9 | 2.19x3.02x0.98 | 0 | **OK** | huge, reads; skin mottle |
| `glumbumble` | `glumbumble` | 64x16 | 5 | flinch, fly, groan, idle | death, idle-action(hover/flit) | 2 tx | 1.0 | 0.45x0.45 | 0.56x0.59x0.8 | 0 | **POOR** | grey box on legs; no wing read, looks like a raccoon not a flying insect |
| `gnome` | `gnome` | 64x32 | 14 | call, flinch, idle, walk | death, idle-action(peek/scratch) | - | 1.0 | 0.65x0.75 | 0.68x1.06x0.56 | 0 | **GOOD** | potato-headed garden gnome |
| `goblin_teller` | `goblin_teller` | 64x40 | 18 | bow, idle, walk | - | - | 1.0 | 0.6x1.5 | 0.7x1.51x0.58 | 0 | **GOOD** | bespoke detailed goblin |
| `golden_snidget` | `golden_snidget` | 64x24 | 5 | flinch, fly, idle, song | death, idle-action(preen/ruffle/stretch) | 11 tx | 1.0 | 0.35x0.35 | 0.93x0.34x0.56 | 0 | **GOOD** | gold ball with silver wings |
| `granian` | `granian` | 128x80 | 23 | call, fly, idle, strike | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 1.7x1.9 | 0.81x1.93x2.55 | 0 | **OK** | grey winged horse; wing slabs |
| `graphorn` | `graphorn` | 128x88 | 21 | attack, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 1.7x1.9 | 1.06x2.24x2.92 | 0 | **OK** | humped purple bull with gold horns; heavy dither |
| `griffin` | `griffin` | 128x96 | 28 | call, fly, idle, strike | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 1.7x1.9 | 1.07x2.65x3.25 | 0 | **OK** | eagle front / lion rear reads; head long like a llama |
| `grindylow` | `grindylow` | 64x24 | 16 | attack, flinch, idle, swim | death, ambient, idle-action(drift/roll) | 2 tx | 1.0 | 0.65x0.75 | 0.47x1.22x0.37 | 0 | **GOOD** | thin green horned water demon |
| `hebridean_black` | `hebridean_black` | 128x152 | 24 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 4 tx | 1.8 | 2.7x2.9 | 3.75x2.34x4.18 | 25 | **BROKEN** | old parametric dragon, 25 UV holes |
| `hidebehind` | `hidebehind` | 64x40 | 16 | idle, walk | - | 2 tx | 1.0 | 0.9x1.9 | 0.63x2.03x0.88 | 0 | **OK** | invisible when looked at by design; skin judged offline |
| `hippocampus` | `hippocampus` | 128x48 | 12 | call, idle, swim | hit, death, idle-action(drift/roll) | - | 1.0 | 1.7x1.9 | 0.56x1.94x3.68 | 0 | **GOOD** | horse front, fish tail |
| `hippogriff` | `hippogriff` | 128x64 | 29 | attack, fly, idle, walk | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 1.7x1.9 | 0.92x2.36x2.27 | 78 | **OK** | reads; small upturned eagle head, 78 UV holes |
| `hodag` | `hodag` | 64x64 | 16 | bite, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 12 tx | 1.0 | 0.95x1.25 | 1.13x1.0x2.3 | 0 | **GOOD** | horned lizard-bull with glowing eyes |
| `horklump` | `horklump` | 64x32 | 7 | flinch, idle | death | - | 1.0 | 0.65x0.75 | 0.96x0.75x0.96 | 0 | **GOOD** | pink mushroom with tendrils |
| `horned_serpent` | `horned_serpent` | 128x32 | 10 | bite, hiss, idle, swim | hit, death, idle-action(coil/taste_air) | 16 tx | 1.0 | 1.5x1.3 | 0.58x0.85x3.03 | 0 | **POOR** | dead-straight plank; a serpent's read is the curve |
| `hungarian_horntail` | `hungarian_horntail` | 128x128 | 40 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 33 tx | 1.8 | 2.7x2.9 | 3.51x2.24x4.0 | 293 | **POOR** | most distinctive dragon but wings collapse into a jumble at the flank, camo-noise skin, 293 UV holes |
| `imp` | `imp` | 64x16 | 14 | call, flinch, idle, walk | death, idle-action(peek/scratch) | 2 tx | 1.0 | 0.45x0.45 | 0.67x0.62x0.26 | 0 | **OK** | brown imp with glowing eyes |
| `jarvey` | `jarvey` | 64x40 | 15 | bite, call, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.38x0.63x2.06 | 0 | **GOOD** | ferret |
| `jobberknoll` | `jobberknoll` | 64x24 | 13 | flinch, fly, idle | death, idle-action(preen/ruffle/stretch) | - | 1.0 | 0.45x0.45 | 0.42x0.45x0.87 | 0 | **GOOD** | small blue bird |
| `kappa` | `kappa` | 64x48 | 16 | attack, flinch, idle, swim | death, idle-action(drift/roll) | 20 tx | 1.0 | 0.95x1.25 | 0.76x1.46x0.76 | 0 | **OK** | turtle-backed water demon; spot dither heavy |
| `kelpie` | `kelpie` | 128x48 | 19 | attack, call, idle, swim | hit, death, idle-action(drift/roll) | - | 1.0 | 1.7x1.9 | 0.62x2.0x2.4 | 0 | **GOOD** | horse guise reads |
| `knarl` | `knarl` | 64x40 | 13 | call, flinch, idle, walk | death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.66x0.62x1.02 | 0 | **OK** | hedgehog; camouflage blotches too large |
| `kneazle` | `kneazle` | 64x40 | 18 | death, graze, hiss, hit, idle, lunge, shake, sniff, walk | - | 2 tx | 1.0 | 0.65x0.75 | 0.47x1.03x1.89 | 0 | **GOOD** | lean cat with lion tail |
| `leprechaun` | `leprechaun` | 64x32 | 15 | call, flinch, idle, walk | attack, death, idle-action(peek/scratch) | 16 tx | 1.0 | 0.65x0.75 | 0.62x1.27x0.44 | 0 | **GOOD** | green coat, hat, beard |
| `lethifold` | `lethifold` | 64x32 | 6 | attack, flinch, idle, walk | death, idle-action(squirm/settle) | - | 1.0 | 0.95x1.25 | 0.88x0.75x1.09 | 0 | **GOOD** | black cloak |
| `lobalug` | `lobalug` | 64x56 | 3 | flinch, idle, swim | death, idle-action(squirm/settle) | - | 1.0 | 0.7x0.7 | 0.62x0.62x1.25 | 0 | **GOOD** | sphere with proboscis |
| `mackled_malaclaw` | `mackled_malaclaw` | 64x32 | 41 | bite, flinch, idle, walk | death, idle-action(groom/skitter) | - | 1.0 | 0.65x0.75 | 1.11x0.53x1.53 | 0 | **OK** | grey lobster, legs flat |
| `maledictus` | `maledictus` | 64x72 | 14 | hiss, idle, strike, walk | hit, death, idle-action(peek/scratch) | 2 tx | 1.0 | 0.95x1.25 | 1.19x1.57x1.18 | 0 | **GOOD** | woman on a serpent coil |
| `manticore` | `manticore` | 128x96 | 24 | hiss, idle, strike, walk | hit, death, idle-action(stretch/shake/graze) | 12 tx | 1.0 | 1.7x1.9 | 0.88x3.03x2.98 | 0 | **GOOD** | lion, human face, scorpion tail |
| `matagot` | `matagot` | 64x32 | 16 | hiss, idle, strike, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 8 tx | 1.0 | 0.6x0.7 | 0.52x1.07x1.84 | 0 | **GOOD** | black cat, glowing eyes |
| `merperson` | `merperson` | 64x40 | 14 | attack, idle, song, swim | hit, death, idle-action(drift/roll) | - | 1.0 | 0.95x1.25 | 0.64x1.55x0.94 | 0 | **GOOD** | grey merperson with spear |
| `moke` | `moke` | 64x24 | 15 | flinch, hiss, idle, walk | death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.38x0.22x1.69 | 0 | **GOOD** | silver-green lizard (camouflaged in-game by design) |
| `mooncalf` | `mooncalf` | 64x56 | 19 | dance, idle, walk | - | 2 tx | 1.0 | 0.7x1.2 | 0.8x1.72x0.98 | 0 | **GOOD** | goggle-eyed; hides by day by design |
| `murtlap` | `murtlap` | 64x32 | 15 | bite, flinch, idle, walk | death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.43x0.83x1.79 | 0 | **GOOD** | rat with anemone back |
| `niffler` | `niffler` | 32x32 | 8 | idle, walk | - | - | 1.0 | 0.4x0.5 | 0.34x0.39x0.81 | 15 | **POOR** | iconic creature as a featureless black box: no bill, no pale muzzle, no pouch, 15 UV holes, 32x32 sheet |
| `nogtail` | `nogtail` | 64x32 | 19 | bite, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 0.65x0.75 | 0.46x0.73x1.24 | 0 | **GOOD** | piglet |
| `norwegian_ridgeback` | `norwegian_ridgeback` | 128x152 | 24 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | - | 1.4 | 2.7x2.9 | 3.62x2.34x4.05 | 19 | **BROKEN** | old parametric dragon, 19 UV holes |
| `nundu` | `nundu` | 128x240 | 22 | bite, howl, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 4 tx | 1.0 | 2.7x2.9 | 1.5x3.2x5.3 | 0 | **GOOD** | huge leopard |
| `obscurus` | `obscurus` | 128x128 | 27 | fly, idle | attack, hit, death, idle-action(squirm/settle) | 97 tx | 1.0 | 0.95x1.25 | 1.23x1.66x1.18 | 325 | **OK** | black swirling mass; 325 UV holes (reads as holes in smoke, so survives) |
| `occamy` | `occamy` | 128x128 | 30 | attack, idle, walk | attack, hit, death, idle-action(coil/taste_air) | - | 1.0 | 1.7x1.9 | 2.43x2.11x4.69 | 0 | **GOOD** | blue plumed serpent-bird |
| `peruvian_vipertooth` | `peruvian_vipertooth` | 128x112 | 23 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 4 tx | 0.8 | 1.7x1.9 | 3.06x2.0x3.61 | 20 | **BROKEN** | old parametric dragon, 20 UV holes |
| `phoenix` | `phoenix` | 128x128 | 25 | fly, idle | - | 92 tx | 1.0 | 0.7x1.0 | 2.6x1.3x1.97 | 42 | **OK** | red bird, gold tail; 42 UV holes |
| `plimpy` | `plimpy` | 64x56 | 6 | call, flinch, idle, swim | death, idle-action(drift/roll) | - | 1.0 | 0.65x0.75 | 0.62x1.0x0.81 | 0 | **GOOD** | round fish with legs |
| `pogrebin` | `pogrebin` | 64x32 | 12 | attack, groan, idle, walk | hit, death, idle-action(squirm/settle) | 2 tx | 1.0 | 0.65x0.75 | 0.58x0.87x0.53 | 0 | **GOOD** | stone-headed (camouflaged in-game by design) |
| `porlock` | `porlock` | 64x40 | 12 | call, flinch, idle, walk | death, idle-action(peek/scratch) | - | 1.0 | 0.65x0.75 | 0.63x1.15x0.47 | 0 | **GOOD** | shaggy little guardian |
| `puffskein` | `puffskein` | 64x48 | 5 | flinch, idle, song, walk | death, idle-action(squirm/settle) | - | 1.0 | 0.45x0.45 | 0.62x0.62x0.75 | 0 | **GOOD** | custard puff with tongue |
| `pukwudgie` | `pukwudgie` | 64x32 | 17 | attack, call, idle, walk | hit, death, idle-action(peek/scratch) | - | 1.0 | 0.5x1.0 | 0.93x1.51x0.62 | 0 | **GOOD** | grey, big ears, bow |
| `pygmy_puff` | `pygmy_puff` | 64x32 | 5 | flinch, idle, song, walk | death, idle-action(squirm/settle) | - | 1.0 | 0.45x0.45 | 0.5x0.5x0.62 | 0 | **GOOD** | pink puff |
| `qilin` | `qilin` | 64x48 | 21 | call, flinch, idle, walk | death, idle-action(graze/sniff/shake/scratch) | 48 tx | 1.0 | 0.95x1.25 | 0.38x1.55x1.61 | 0 | **GOOD** | scaled horse with horn |
| `quintaped` | `quintaped` | 128x64 | 28 | attack, groan, idle, walk | hit, death, idle-action(groom/skitter) | 2 tx | 1.0 | 0.95x1.25 | 2.29x1.15x2.18 | 0 | **OK** | five hairy legs; reads |
| `ramora` | `ramora` | 128x32 | 8 | flinch, idle, swim | death, idle-action(drift/roll) | - | 1.0 | 0.95x1.25 | 0.97x0.91x2.0 | 0 | **GOOD** | silver fish |
| `red_cap` | `red_cap` | 64x40 | 16 | attack, groan, idle, walk | hit, death, idle-action(peek/scratch) | 2 tx | 1.0 | 0.65x0.75 | 0.92x1.5x0.46 | 0 | **OK** | tall red cap; model twice its hitbox height |
| `reem` | `reem` | 128x192 | 23 | attack, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 2.7x2.9 | 1.93x2.79x4.06 | 0 | **GOOD** | golden ox |
| `romanian_longhorn` | `romanian_longhorn` | 128x144 | 24 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | - | 1.8 | 2.7x2.9 | 3.75x2.83x4.03 | 23 | **BROKEN** | old parametric dragon: one vertical horn where canon has two forward horns, 23 UV holes |
| `rougarou` | `rougarou` | 128x48 | 20 | attack, howl, idle, walk | hit, death, idle-action(peek/scratch) | 2 tx | 1.0 | 1.2x1.9 | 1.14x1.83x1.68 | 0 | **OK** | dog-headed (camouflaged in-game); mottle heavy |
| `runespoor` | `runespoor` | 64x32 | 16 | idle, walk | - | - | 1.0 | 0.95x1.25 | 0.83x0.39x2.24 | 0 | **POOR** | flat line on the floor; three heads not distinguishable |
| `salamander` | `salamander` | 64x24 | 15 | flinch, hiss, idle, walk | death, idle-action(graze/sniff/shake/scratch) | 197 tx | 1.0 | 0.65x0.75 | 0.38x0.21x1.62 | 0 | **GOOD** | fiery lizard with flame glow |
| `sea_serpent` | `sea_serpent` | 128x80 | 10 | bite, hiss, idle, swim | hit, death, idle-action(coil/taste_air) | - | 1.0 | 2.7x2.9 | 1.22x1.72x5.1 | 0 | **GOOD** | undulating serpent with crest |
| `shrake` | `shrake` | 128x32 | 8 | attack, flinch, idle, swim | death, idle-action(drift/roll) | - | 1.0 | 0.95x1.25 | 0.97x0.96x1.94 | 0 | **GOOD** | spined fish |
| `snallygaster` | `snallygaster` | 128x72 | 22 | fly, hiss, idle, strike | hit, death, idle-action(stretch/shake/graze) | - | 1.0 | 1.7x1.9 | 0.85x1.89x4.07 | 0 | **OK** | long neck, purple wings |
| `sphinx` | `sphinx` | 128x72 | 21 | call, idle, strike, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 1.7x1.9 | 0.81x1.84x2.55 | 0 | **GOOD** | lion with pharaoh head |
| `streeler` | `streeler` | 64x64 | 5 | idle, walk | - | - | 1.0 | 0.7x0.7 | 0.44x0.69x0.99 | 2 | **POOR** | shell is a flat teal crate on a slug; 228-colour noise skin |
| `swedish_short_snout` | `swedish_short_snout` | 128x136 | 24 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | 4 tx | 1.4 | 2.7x2.9 | 3.56x2.14x3.7 | 27 | **BROKEN** | old parametric dragon, 27 UV holes |
| `swooping_evil` | `swooping_evil` | 128x32 | 10 | fly, hiss, idle, lunge | hit, death, idle-action(hover/flit) | 216 tx | 1.0 | 0.95x1.25 | 2.05x0.82x1.64 | 0 | **GOOD** | skull head, iridescent wings |
| `tebo` | `tebo` | 128x88 | 16 | attack, groan, idle, walk | hit, death, idle-action(graze/sniff/shake/scratch) | - | 1.0 | 1.7x1.9 | 1.0x1.59x3.45 | 0 | **GOOD** | warthog (camouflaged in-game by design) |
| `thestral` | `thestral` | 128x128 | 40 | fly, gallop, graze, idle, screech, walk | - | 32 tx | 1.0 | 1.4x1.8 | 4.43x2.44x4.06 | 258 | **OK** | skeletal winged horse (visible only to those who saw death); 258 UV holes |
| `thunderbird` | `thunderbird` | 128x96 | 20 | call, fly, idle, strike | hit, death, idle-action(preen/ruffle/stretch) | 166 tx | 1.0 | 1.7x1.9 | 1.09x1.92x2.29 | 0 | **OK** | big ochre bird; X-strap markings read as harness |
| `toad` | `toad` | 64x24 | 8 | call, flinch, idle, walk | death, idle-action(squirm/settle) | - | 1.0 | 0.45x0.45 | 0.7x0.51x0.74 | 0 | **GOOD** | toad |
| `troll` | `troll` | 128x152 | 17 | attack, groan, idle, walk | hit, death, idle-action(stretch/grunt) | - | 1.0 | 2.7x2.9 | 2.07x2.72x2.36 | 0 | **GOOD** | hulking grey troll with club |
| `ukrainian_ironbelly` | `ukrainian_ironbelly` | 128x200 | 25 | bite, breath, fly, idle | attack, hit, death, idle-action(stretch/shake/graze) | - | 2.2 | 2.7x2.9 | 4.44x2.59x4.83 | 20 | **BROKEN** | old parametric dragon, 20 UV holes, renders twice its hitbox height |
| `unicorn` | `unicorn` | 64x64 | 23 | call, flinch, idle, walk | death, idle-action(graze/sniff/shake/scratch) | 54 tx | 1.0 | 1.4x1.6 | 0.56x2.03x2.02 | 0 | **GOOD** | white horse, glowing gold horn |
| `wampus_cat` | `wampus_cat` | 128x64 | 19 | hiss, idle, lunge, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 4 tx | 1.0 | 1.7x1.9 | 0.75x1.64x4.04 | 0 | **GOOD** | big spotted cat |
| `werewolf` | `werewolf` | 128x32 | 26 | attack, hit, howl, idle, walk | death, idle-action(peek/scratch) | 2 tx | 1.0 | 1.7x1.9 | 0.7x1.81x1.62 | 0 | **OK** | stooped wolf-man; reads |
| `yeti` | `yeti` | 128x80 | 16 | attack, howl, idle, walk | hit, death, idle-action(stretch/grunt) | 4 tx | 1.0 | 1.7x2.3 | 1.58x2.31x1.02 | 0 | **GOOD** | white yeti (camouflaged in snow by design) |
| `zouwu` | `zouwu` | 128x184 | 21 | call, idle, lunge, walk | hit, death, idle-action(graze/sniff/shake/scratch) | 6 tx | 1.0 | 2.7x2.9 | 1.44x2.72x5.61 | 0 | **OK** | striped cat; rainbow tail is one oversized cube |
