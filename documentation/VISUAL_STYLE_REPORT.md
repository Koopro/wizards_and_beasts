# Wizards & Beasts — Visual Style Report

**The visual source of truth.** Read this before creating, regenerating or "improving" any asset.
Written 2026-09-27 on `dev`, after the visual overhaul (audit → style enforcement → texture
cleanup → visual families). Rules in detail: `VISUAL_STYLE_GUIDE.md`. History and measurements:
`VISUAL_CONSISTENCY_AUDIT.md`. Enforced checks: `src/test/.../resources/VisualStyleConformanceTest.java`.

---

## 0. Verdict

**The mod now reads as one game.** Seen without names, the creatures, blocks, items, armour, effect
icons and particles would pass as one Minecraft expansion. They share:

- box geometry at one texel density;
- the same shading logic;
- one grain and pixel vocabulary;
- one gold, one set of semantic magic colours;
- a scale ladder that ranks correctly against the player.

The one system that still breaks the language is the **spell icon / spell HUD art** (§2, A1).
Everything else outstanding is B-grade: noticeable side by side, not jarring in play.

Evidence behind this verdict:
- **Full roster in-game capture:** 109 creatures × front/side/far/night, 436 shots, 2026-09-27, in `runs/client2/screenshots/final_review`. No model, texture or animation load errors.
- **Metric comparison:** every texture against vanilla 1.21.11, per folder.
- **Contact sheets:** every block, item, effect icon, particle and GUI texture.
- **Offline renders:** worn armour.

The GUI screens themselves have **not** been seen in a running client; there is no screen-capture
harness. Treat GUI verdicts as texture-level only.

---

## 1. Established visual rules

Priority when two rules conflict: **silhouette → proportions → readability → material identity →
palette → animation → detail.** (T) marks rules enforced by a test.

**R1 — Vanilla is the yardstick, measured.** Keep a texture's colour count, noise and luminance
inside vanilla's 5th–95th percentile for its folder. Something far outside that band reads as a
different pack.

**R2 — One texel density in the world.** 1 texel = 1 model unit = 1/16 block for entities, armour,
block entities and held models.
- Size a creature with the definition `scale` (→ `Attributes.SCALE`, which moves model *and*
  hitbox), never a renderer `scale()`.
- GUI art is drawn 1:1 or at an integer multiple. Mob-effect icons are 18×18 (T).

**R3 — Box geometry, integer sizes, silhouette-first.** No sub-unit cubes and no UV holes (`uv_check`).
- Add a cube only if it changes the black silhouette.
- Complexity budgets per class are in STYLE_GUIDE §1.

**R4 — Shade by form, one light.** The engine shades faces. Skins carry a one-texel bevel (lit top,
shaded bottom) and structure, never a painted light direction. Icons are lit top-left.

**R5 — Texture is structure, not noise.**
- Creature grain: `strand` for fur, hair, bark and cloth; `fleck` for skin, hide, scale, feather and
  smoke.
- Ramps are stepped (≤ 4 bands).
- No random 1-px speckle, no smooth gradients, no photographic detail, no anti-aliased edges.
- Block faces must carry material noise: ≥ 0.022 for building surfaces, ≥ 0.012 for any opaque
  face (T).

**R6 — Controlled palette.**
- 4–8 colours per block face, ≤ 48 per creature sheet (T).
- No pure black on lit skins (T); avoid pure white.
- One accent per asset.
- Saturation 0.30–0.50 mean, except vivid-by-canon plumage, banners and emissive.

**R7 — Semantic colour** (`spell/core/MagicColours.java`):
- magic violet;
- dark-magic violet-black;
- healing soft green;
- poison sickly green;
- protection silver-blue;
- fire, light, stun scarlet, ice, water, lightning.

Spells, particles and abilities take their colour from this list. Spell colours are opaque and never
a stand-in (T). Vanilla particles keep their vanilla meaning: `HAPPY_VILLAGER`, `PORTAL` and
`WITCH` are not ours.

**R8 — Emissive is earned.** Only a lore light source, one state organ, or a body of fire/light
glows. Nothing renders full-bright except a glowmask. Forms take the world's light.

**R9 — Scale follows the world**, against the player (1.8) and canon (T hierarchy):
- Giant > Troll > player;
- dragons ≥ Troll;
- Horntail > Welsh Green;
- Thunderbird > Hippogriff;
- Centaur > player.

**R10 — Visual families** (STYLE_GUIDE §9):
- **Shared structure:** each family shares structure and ramp logic.
- **Individual identity:** each member keeps its own colour and one identifying feature.
- **Wood:** heartwood = the wand tint (T); bark on log sides only; 7 parts per species (T).
- **One architectural gold:** `#8F6522 / #C9973A / #EBC874`, equal to the GUI gilt (T).
- **Item icons:** one engine (`item_sprites.py`); icon cloth = worn cloth.

**R11 — Screens are parchment and ink; overlays over the world are leather.** No text shadow on
paper. (`tasks/art_rework_phase2_brief.md`.)

**R12 — Animation matches the geometry.**
- Few keys, clear poses, and a 0.1–0.25 s anticipation before a strike.
- `hit` 0.4 s, `death` 1.2 s held.
- Idle actions come from `IdleProfile`.
- The one-shot controller overrides the loop.

**R13 — Judge in the client.** Nothing is done until seen through `EntityShowcaseCapture` /
`ItemShowcaseCapture` next to a villager or a vanilla item.

---

## 2. Classification of every asset family

**A — must fix · B — should fix · C — acceptable, intentionally different · D — preserve.**

### Creatures and humanoids

| Asset | Class | Note |
|---|---|---|
| Ten dragons | **D** | User reference sheet (5 wyverns, 5 four-legged); rigs and skins never regenerated except through `dragon_model.py`. Scale 1.25–1.5 applied by data. |
| Dementor, Hippogriff (+4 coats), Basilisk, Phoenix, Kelpie (+disguise, +coats) | **D** | Hand-tuned redesigns; generators pinned to `grain="speckle"`. |
| Thestral, Niffler (+baby, coats), Acromantula, Bowtruckle, Cornish Pixie, Streeler | **D** | 2026-09-25/26 rebuilds; read well in client. |
| Unicorn, Thunderbird, Fire Crab, Augurey, Lethifold | **D** | Fixed in this overhaul; canon read confirmed in client. |
| Rigkit roster (~70 data-driven creatures) | **D** | Shared box language, strand/fleck grain, hit/death/idle clips on all 96. |
| Troll, Giant | **D** | Silhouettes read; rescaled 1.25 / 1.8. |
| Winged horses (Abraxan, Aethonan, Granian), Snallygaster | **D** | Feathered primaries added; horse bodies are the family. |
| Griffin | **B** | Stepped wings fixed, but still reads as a beaked horse: the eagle forequarters need talons and a feathered chest instead of horse legs (model). |
| Glumbumble | **B** | Grey box with a V of wings; a furry bumble-body silhouette is missing (model). |
| Blast-Ended Skrewt | **B** | Crate on spider legs; needs a segmented, shell-less, scorpion-tailed body (model). |
| Knarl | **B** | Reads as a brown boulder at distance; quills need to break the silhouette (model: spine cubes, not texture). |
| Red Cap | **B** | Model 2× its 0.75 hitbox; needs a hitbox or `scale` decision (gameplay sign-off). |
| Sea Serpent | **B** | 2.9 blocks for a canonically huge sea creature; candidate for `scale` once aquatic pathing is checked. |
| Long, low creatures (Ashwinder, Moke, Salamander, Flobberworm, Runespoor) | **C** | Tall boxes around low bodies; a hitbox matter, not art. |
| Obscurus | **C** | Painted gaps in its smoke are intended (the only speckle-flagged skin). |
| Invisible / camouflaged creatures (Boggart, Demiguise, Dugbog, Hidebehind, Mooncalf, Moke, Pogrebin, Rougarou, Tebo, Thestral-unseen, Yeti) | **C** | Blank in captures by design; skins judged offline. |
| Fwooper, Chinese Fireball, Salamander saturation | **C** | Vivid by canon / emissive. |
| Bespoke creatures without `hit`/`death` (Niffler, Pixie, Streeler, Bowtruckle, Augurey, Mooncalf) | **B** | Only the vanilla red flash on damage, while every data-driven creature flinches and dies. Animation + clip gate. |

### Player forms

| Asset | Class | Note |
|---|---|---|
| Werewolf, Centaur, Goblin, Merfolk, Obscurial forms (mob rigs) | **D** | |
| House-elf, Veela harpy rigs | **D** | New; asset-tested, not seen in a client. |
| Cat/Dog/Hare Animagus (vanilla model + skin) | **C** | Real vanilla animals are right for Animagi. |
| Hawk/Beetle Animagus (vanilla model, own skin) | **D** | |
| Stag Animagus | **B** | Last legacy `ModelPart` box model; no walk cycle. Needs a rigkit rig. |

### Items, weapons and wands

| Asset | Class | Note |
|---|---|---|
| All 16×16 item icons (`item_sprites.py`, 210) | **D** | The reference style. |
| Wand (GeckoLib rig, wood appearance data) | **D** | Frozen rig; wood = heartwood tint shared with the blocks. Cores are not shown (canon: inside). |
| Elder Wand | **D** | Own art, untinted. |
| Book cuboids, hold-class transforms, GeckoLib artefacts | **D** | |
| Wizard cards (128 px portraits) | **C** | Deliberate large-format collectible. |
| Spawn eggs | **D** | Vanilla egg shading from skin colours. |
| Hair/strand items (unicorn, veela, demiguise ×6) | **B** | 1-px strands are the thinnest silhouettes in the set; a grouped lock shape would read better in a slot. |
| Death Eater mask variants 1–5 | **B** | Near-identical at 16 px. |

### Blocks

| Asset | Class | Note |
|---|---|---|
| Wood family (9 species × 7 parts) | **D** | Heartwood-linked to wands. |
| Hogwarts, Hogsmeade, Diagon, Gringotts, Ministry, Honeydukes, Three Broomsticks sets | **D** | Film-derived palettes; stone, marble and gilt unified. |
| House banners (block + waving BER) | **D** | Stepped folds. |
| Cauldrons, trunks, benches, Floo, lanterns, crops, warding stone | **D** | |
| Animated textures (enchanted ceiling, Floo flames/grate) | **C** | Metric flags only (close tones); clean pixel art. |

### Armour

| Asset | Class | Note |
|---|---|---|
| Student / Auror / Death Eater robes, wizard hat, Death Eater mask | **D** | Distinct in hue and value; icons match worn. |
| Student robe house variants | **B** | No house identity yet (needs item/data variants). |

### GUI, icons, particles, VFX

| Asset | Class | Note |
|---|---|---|
| **Spell sigils + spell HUD diamond** | **A1** | 92 px smooth-gradient icons drawn at 12 px (list) and ~34 px (HUD); HUD frame is 256 px drawn at 96 (×0.375). The only system still outside the pixel language, and on screen constantly. |
| Parchment screen kit, eight skins, leather overlays | **D** | Approved 2026-09-23. |
| Handbook book/emblem, Obscurial stress meter | **D** | Cleaned to hard-edged 1:1 pixel art. |
| Mob-effect icons (42) | **D** | 20 redrawn; the rest already fit. |
| Particles (14 types, 29 sprites) | **D** | Crisp, greyscale-tinted, one set per type. |
| Creature ability particles (arcane / light / healing / dread / poison) | **D** | |
| Wand beam (`effect/wand_beam*.png`) | **B** | Smooth radial glow; pixel core + stepped glow would match the particles. Touches the custom beam pipeline; test the electrocute look after any change. |
| Protego ward skin | **C** | Translucent magic; soft alpha intentional; pattern already crisp. |
| Werewolf / Dementor / Phoenix `LARGE_SMOKE` bursts | **B** | Big grey vanilla smoke; should become `DREAD` / `dark_wisp`. |
| Magenta vanilla particle clouds seen on some creatures (Fire Crab, Pukwudgie, earlier others) | **B** | Source not found. Not the mod's ability particles. Investigate before shipping. |
| Vanilla tooltips over parchment screens | **C** | Vanilla players expect them; keep. |

---

## 3. Remaining outliers, in fix order

1. **A1 — spell sigils and spell HUD.** Redraw all 159 as pixel icons in the item-sprite language:
   16 or 24 px, rim colour by category, glyph in the spell's own colour. Author the HUD frame at its
   on-screen size. Draw only at integer multiples: `SpellDiamondOverlay`, `SpellWheelScreen`,
   `SpellMenuScreen` (`ICON_TEX_SIZE`, `HUD_TEX_SIZE`). Keep the resource paths.
2. **B — `LARGE_SMOKE` bursts and the magenta clouds** (VFX grammar, R7).
3. **B — Stag Animagus rig**, and hit/death clips for the six bespoke creatures (animation parity).
4. **B — Griffin, Glumbumble, Blast-Ended Skrewt, Knarl** silhouettes (model changes only; keep
   bones and clip names; re-run `reactions.py` / `bestiary_portraits.py`).
5. **B — wand beam** pixel pass.
6. **B — hair items, Death Eater mask variants** (icons).
7. **B — Red Cap / Sea Serpent scale, student house robes** — need gameplay or data sign-off
   first.

---

## 4. Assets that must never be regenerated (or only the stated way)

| Asset | Rule |
|---|---|
| Ten dragons | Only via `tools/dragon_model.py`. **Never run `tools/dragons/generate.py` or `tools/horntail_model.py`**: they overwrite the reference-sheet dragons. |
| Dementor, Hippogriff (+coats), Basilisk, Phoenix, Kelpie (+disguise, +coats) | Generators pinned to `Skin(grain="speckle")`, which reproduces the shipped files byte-for-byte. Never switch them to `strand`/`fleck`, and never hand-edit them to "match" the roster. |
| Obscurus rig (`obscurus.geo.json` / `.animation.json`) | Never a full `obscurus_model.py` run: it drops the `add_reactions.py` clips and later geo fixes. Use `--form-only` for the player-form smoke. |
| Spawn eggs of Bowtruckle, Cornish Pixie, Phoenix, Thestral | Hand-set by their redesigns; `spawn_eggs.py` would overwrite them. |
| Wand rig (`wand.geo.json`) | The author's frozen model. Wood looks come from `wand_woods/*.json` `appearance`. |
| Parchment GUI kit, spell sigils, item icons, item geo | Owned by `gui_parchment.py`, `gui_chrome.py`, `spell_sigils.py`, `item_sprites.py`, `item_geo.py`. `artgen_common.save` refuses other writers even with `--force`. Redraw through the owner. |
| Wizard cards | Deliberate 128 px exception. |
| `src/main/resources` after `runData` | `runData` stubs placeholder textures over real art. Never `git add -A` after it. Snapshot and compare instead. |

Hand-made textures with no generator (keep them as they are, or claim them into a generator
explicitly):
- 19 mob-effect icons;
- the spell-wheel overlay sprites (`gui/sprites/*/bg.png`, `overlay*.png`);
- `effect/wand_beam*.png`;
- `entity/petrify/stone_statue.png`;
- `entity/form/placeholder.png`.

---

## 5. Future redesign queue

The A/B list of §3, plus:
- **Wand core detail:** needs a rig change or per-bone tinting. Cores are canonically hidden, so
  this is optional.
- ~~**`tools/` is git-ignored.**~~ Fixed 2026-09-28: the generators that own shipped assets are
  tracked, retired ones stay local, and `tools/art_sync_check.py` proves they reproduce the live
  files. See `ART_PIPELINE.md`.

---

## 6. Standards for every future agent

**Before you draw**
1. Find the asset's family (STYLE_GUIDE §9) and its generator (the PNG `Generator` text chunk).
2. Check §4. If the asset is listed there, stop and follow that rule.
3. Look at its neighbours in-game, not just the file.

**How to change art safely** (this tree is shared and dirty; peers may be working):
1. Copy `tools/` and `src/main/resources` to a sandbox under `C:/tmp`.
2. Sync-check the generator there. It must reproduce the live files byte-for-byte; compare
   `Image.tobytes()`, **not** `ImageChops.difference().getbbox()`, which only sees alpha in this Pillow.
3. Edit and regenerate in the sandbox. Copy back only the changed files, skipping the protected
   list, with a backup of each.
4. Never `git checkout` / `restore` to undo: it destroys peers' uncommitted work.

**What "done" means**
- `./gradlew test` is green, including `VisualStyleConformanceTest`. If you add a rule, prove it
  fails on the old art (mutation check).
- `python tools/uv_check.py --all` reports only the known exceptions: brooms, `player`,
  `protego_shield`, Obscurus.
- In-client capture next to a villager or vanilla item, with no asset errors in the log:
  - `WB_ENTITY_SHOWCASE=<list> WB_ENTITY_SHOWCASE_VIEWS=front,side,far,night ./gradlew runClient2`
  - `WB_ITEM_SHOWCASE=<list> ./gradlew runClient2`
- Derived art refreshed after any skin change: `bestiary_portraits.py`, and `spawn_eggs.py` (not
  the four hand-set eggs).
- JDK 21 for gradle (`JAVA_HOME` defaults to 1.8 on this machine). Never run two gradle builds at
  once.

**What not to do**
- No realism, no extra cubes or texture detail "for quality".
- No new colour meanings.
- No full-bright.
- No vanilla particles as the mod's magic.
- No render-scaling creatures.
- No smooth gradients or anti-aliased edges.
- No redesign of a D-class asset because it is old.

---

## 7. Known environment blockers

- ~~`runData` fails on `Missing blockstate definitions for: [wizards_and_beasts:acromantula_web]`.~~
  Fixed 2026-09-28: the web's blockstate, model, loot table and module tag are generated
  (`ART_PIPELINE.md` §2.1).
