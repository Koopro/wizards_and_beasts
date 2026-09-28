# Wizards & Beasts — Art and Datagen Pipeline

How every shipped asset is produced, how to reproduce it from a fresh clone, and how to prove a
generator still matches the approved art. Written 2026-09-28 on `dev`.

`VISUAL_STYLE_REPORT.md` is the visual source of truth. This file does not change what the art
should look like; it explains how the files come to exist. Where the two meet (protected assets,
§7), the report wins.

---

## 0. Short version

```sh
# once per machine
python -m pip install -r tools/requirements.txt   # Python 3.12+, Pillow + numpy, pinned
./gradlew build                                   # JDK 21; also caches the vanilla client jar

# datagen (models, blockstates, loot, tags, lang, recipes -> src/generated/resources)
./gradlew runData

# prove the art generators still reproduce the shipped art (writes nothing live)
python tools/art_sync_check.py                    # whole pipeline, ~30 min
python tools/art_sync_check.py --only item_geo    # one generator
```

Two pipelines produce the assets, and they never write the same file:

| | Datagen (Java) | Art generators (Python) |
|---|---|---|
| Lives in | `src/main/java/.../datagen/` | `tools/*.py` |
| Writes | `src/generated/resources/` | `src/main/resources/` |
| Produces | block/item models, blockstates, item definitions, loot tables, tags, `en_us` keys, recipes | textures, GeckoLib geo + animation JSON, some item models, a few data files |
| Run by | `./gradlew runData` | `python tools/<name>.py`, from the repo root |
| Checked by | `./gradlew build` (tests read the generated tree) | `python tools/art_sync_check.py`, `./gradlew test` |

---

## 1. Requirements

| Need | Version | Why |
|---|---|---|
| JDK | 21 | Gradle build, datagen, tests. On the original machine `JAVA_HOME` defaults to 1.8: point it at a JDK 21 first. |
| Python | 3.12 or newer (verified on CPython 3.13.14) | The generators. |
| Pillow, numpy | pinned in `tools/requirements.txt` (12.3.0, 2.5.1) | Median-cut posterising and the default bitmap font are Pillow internals; another release can move pixels. Re-run the sync check after changing a pin. |
| Vanilla 1.21.11 client jar | from the Gradle cache | Some generators derive from vanilla textures (unlit torches, spawn-egg template, GUI slot grids, the font sheet, Animagus hawk/beetle layouts). Vanilla art is read, never copied into the repo. |

The vanilla jar is found by `artgen_common.client_jar_path()`, in this order:

1. `WB_MC_CLIENT_JAR` — explicit path to any vanilla client jar of `minecraft_version`;
2. `~/.gradle/caches/neoformruntime/artifacts/minecraft_<version>_client.jar` — filled by
   `./gradlew build` on this project (ModDevGradle);
3. `~/.gradle/caches/forge_gradle/minecraft_repo/versions/<version>/client(-extra).jar` — filled
   by ForgeGradle projects; where the generators used to look.

`<version>` is `minecraft_version` from `gradle.properties`. `GRADLE_USER_HOME` is honoured.

No other machine-specific file is needed. Every generator reads only the repository and that jar.

**Always run generators from the repository root.** Most of them use repo-relative paths
(`src/main/resources/...`).

---

## 2. Datagen

`./gradlew runData` runs the `data` run config (`build.gradle`): all providers in
`ModDataGenerators` write into `src/generated/resources/`, with `src/main/resources/` passed as
`--existing` so models can reference hand-made textures.

| Provider | Output |
|---|---|
| `ModModelProvider` | `assets/.../blockstates`, `models/block`, `models/item`, `items/` |
| `ModLanguageProvider` | `assets/.../lang/en_us.json` (merged with the hand-written one at build) |
| `ModBlockTagsProvider`, `ModItemTagsProvider`, `ModEntityTypeTagsProvider` | `data/.../tags/**` including the `module/*` ownership tags |
| `ModLootTableProvider` (block + entity sub-providers) | `data/.../loot_table/**` |
| `ModRecipeProvider` | `data/.../recipe/**` |

Rules that keep it working:

- **Every registered block needs a generated blockstate, and every block a generated loot table.**
  `ModelProvider` and `BlockLootSubProvider` enumerate the registry and fail on any gap. A
  hand-written JSON in `src/main/resources` does *not* satisfy them: the game would load it, but
  datagen cannot see it.
- **Every generated blockstate needs a module tag.** `ModuleTagDataTest.everyBlockHasAnOwningModule`
  walks `src/generated/.../blockstates` and demands an owner in `tags/block/module/*`.
- **One source of truth.** `src/generated/resources` is a second resource root and
  `processResources` keeps the `src/main` copy on a clash (`DuplicatesStrategy.EXCLUDE`, main
  first). A file in both places silently shadows the generated one. Generate it or hand-write it,
  never both.
- `runData` touches only `src/generated/resources`. (Older notes say it stubs textures into
  `src/main/resources`; the placeholder-texture provider that did that no longer exists.) Still:
  snapshot before, compare after, and stage only what you meant to change.

### 2.1 The `acromantula_web` failure (fixed 2026-09-28)

`runData` failed with `Missing blockstate definitions for: [wizards_and_beasts:acromantula_web]`
and wrote nothing.

- **Why it was requested:** `ModModelProvider` checks that every block in the registry has a
  blockstate. `ACROMANTULA_WEB` (`registry/ModBlocks.java`, block class `block/AcromantulaWebBlock`)
  is a real, used block: Acromantulas spin it around their colony and as a snare
  (`entity/creature/AcromantulaWebs`, `ai/WebSnareGoal`, game tests in `CreatureWildlifeTests`).
- **Where its resources were:** the Acromantula redesign hand-wrote its blockstate, block model and
  loot table into `src/main/resources`. The name was right and the files were valid; they were just
  invisible to datagen.
- **Fix:** the block is now generated like its neighbours —
  `ModModelProvider.createAcromantulaWeb` (cobweb cross, `cutout`, no item model: the block has no
  item), `ModBlockLootTableProvider` (drops string, lost to explosions), and a
  `module/creatures` block tag. The three hand-written files were removed. The generated model and
  blockstate are identical in content to the files they replace; the loot table is the same table
  in datagen's spelling (`rolls: 1.0`, `bonus_rolls: 0.0`). Nothing changes in play: the module
  tag only gates right-click interaction, which a cobweb does not have.
- The first successful run also refreshed two stale generated entity loot tables (`acromantula`,
  `basilisk`) to what their committed provider already says. Hand-written copies in
  `src/main/resources/data/.../loot_table/entities/` shadow both, with the same drops, so play is
  unchanged.

---

## 3. The art generators

### 3.1 Ownership: the `Generator` marker

Every PNG a generator writes carries a PNG text chunk `Generator = wizards_and_beasts/tools/<tool>.py`
(`artgen_common.save`). That is how a tool tells its own output from someone else's:

- `is_regenerable(path, marker)` — a tool rewrites a file only if it is missing, carries this
  tool's marker, or is a flat placeholder (≤ 8 colours, no marker). Anything else needs `--force`.
- `PROTECTED` — files owned by `gui_parchment.py`, `gui_chrome.py`, `item_sprites.py`,
  `item_geo.py` and `spell_sigils.py` cannot be overwritten by any other tool, even with `--force`.

To find who owns a texture:

```sh
python -c "from PIL import Image; import sys; print(Image.open(sys.argv[1]).info.get('Generator'))" path/to.png
```

`None` means hand-made (see §7 for the list). JSON files carry no marker; their owners are listed
below.

### 3.2 Generators that own shipped assets (class A, tracked)

Every command below is `python tools/<name>.py` from the repo root unless stated. The whole list
with exact arguments is `python tools/art_sync_check.py --list`.

**Creature and form rigs — one generator per creature.** `tools/<id>_model.py` writes, together,
`geckolib/models/entity/<id>.geo.json`, `geckolib/animations/entity/<id>.animation.json`,
`textures/entity/<id>.png`, `<id>_glowmask.png` when the creature glows, and for data-driven
creatures the rig-related fields of `data/.../creatures/<id>.json`. 106 `*_model.py` files are
tracked, from `abraxan_model.py` to `zouwu_model.py`; the ones that need more than a bare run, or
that are not creatures, are:

| Generator | Owns | Notes |
|---|---|---|
| `dragon_model.py [breed…]` | the ten dragons: geo, animation, skin, glowmask; removes the legacy render-only `dragon.scale` and sets the idle profile in their definitions | **The only way to touch a dragon** (report §4). Keeps the definition's top-level `scale`. |
| `ghoul_model.py` → `snap_sizes.py ghoul` → `add_reactions.py ghoul` | Ghoul | Run all three. A bare run leaves fractional cube sizes (UV holes) and no hit/death/attack clips. |
| `obscurus_model.py` → `snap_sizes.py obscurus` → `add_reactions.py obscurus` | Obscurus rig + the player's Obscurial smoke skin | The chain reproduces the shipped rig exactly. `--form-only` rewrites just the form skin. Never a bare full run. |
| `occamy_model.py` → `add_reactions.py occamy`; `werewolf_model.py` → `add_reactions.py werewolf` | Occamy, Werewolf | As above, without the snap step. |
| `puffskein_model.py`, `pygmy_puff_model.py` | Puffskein, Pygmy Puff | One recipe (`puffskein_model.generate`). The Pygmy Puff is pinned to the legacy grain (§8). |
| `broom_model.py` | master broom rig, per-broom sheets, `broom.animation.json` | |
| `armor_model.py` | robes, hat, masks: armour geo + worn textures | |
| `goblin_model.py`, `centaur_model.py`, `merperson_model.py`, `house_elf_model.py`, `veela_harpy_model.py`, `animagus_stag_model.py` | player-form rigs | |
| `bench_model.py`, `cauldron_model.py`, `tent_skin.py` | GeckoLib block rigs / sheets | |

**Textures, icons and GUI.**

| Generator | Owns (under `assets/wizards_and_beasts/`) |
|---|---|
| `item_sprites.py --write` | 210 item icons in `textures/item` — the reference style. Without `--write` it only renders a mockup. **Protected owner.** |
| `item_models_3d.py` | cuboid held models (`models/item`) and their box-UV textures (90) |
| `item_geo.py` | GeckoLib held items: `geckolib/{models,animations}/item`, `textures/item/model` (30) + Elder Wand sheet, `models/item/<id>_held.json`. **Protected owner.** |
| `spawn_eggs.py` | 107 spawn eggs (skips the four hand-set ones, §7) |
| `wizard_cards.py` | 24 card sheets `textures/item/card`, the blank card, card models, the card item definition |
| `wand_skin.py` | wand sheet + glowmask |
| `block_textures.py` | 41 block textures (unlit lights derived from vanilla, props) |
| `location_textures.py` | 53 location-set blocks (Hogwarts, Gringotts, Ministry, …) and the one architectural gilt |
| `wandwood_textures.py` | 63 wandwood block textures; heartwood = wand tint |
| `banner_textures.py` | 12 house-banner textures |
| `animate_textures.py` | 6 animated strips + `.png.mcmeta` (see §8 for the three overlays) |
| `particle_sprites.py` | 33 particle sprites and the particle JSON |
| `effect_sprites.py --write`, `effect_icons.py` | 20 + 3 mob-effect icons |
| `gui_parchment.py` | the parchment kit: `gui/theme`, eight skins in `gui/sprites/<skin>`, controls atlas. **Protected owner.** |
| `gui_chrome.py` | per-screen GUI (bestiary, wandmaker's bench, Hermione's bag, creative tabs). **Protected owner.** |
| `config_textures.py`, `pouch_textures.py`, `map_textures.py`, `skill_chart_textures.py`, `stat_icons.py`, `hud_blood_icons.py` | config screens, Niffler pouches, Marauder's Map (9), skill chart (35), stat glyphs, blood meter |
| `spell_sigils.py` | 159 spell icons `gui/sprites/wand_hud/spells`. **Protected owner.** Class A1 in the report: due for a redraw. |
| `bestiary_portraits.py` | 107 bestiary icons + silhouettes, rendered from the real rigs and skins |
| `chocolate_frog.py`, `duelling_dummy.py`, `villager_wandmaker.py`, `protego_shield_skin.py`, `animagus_skins.py`, `omnioculars_scope.py` | one-off entity skins, villager profession + hat, Protego ward, Animagus hawk/beetle, scope overlay |
| `texture_cleanup.py` | the cleaned handbook book/emblem and Obscurial stress meter (idempotent) |
| `player_geo.py` | `geckolib/models/entity/player.geo.json` (from vanilla's player mesh) |
| `skill_web_layout.py` | node coordinates in `data/.../skill_nodes/` |

**Libraries (imported, not run):** `artgen_common` (markers, save, palette helpers, vanilla jar),
`rigkit` (bones, box-UV packing, `Skin` painter, `emit`), `bodies` and the family skeletons
`insect`, `crab`, `fish`, `lizard`, `winged_horse`; `reactions`, `bespoke_reactions` (shared clips),
`boxuv`, `item_display` (held transforms), `beast_preview` (offline rasteriser). Two former writers
are kept only as libraries and refuse to run: `beast_skins.py` (palettes and painters for eggs,
tents, the wand) and `spell_icons.py` (glyph primitives for `spell_sigils.py`).

**Validation and preview tools (write nothing live):**

| Tool | Use |
|---|---|
| `art_sync_check.py` | Reproduction check of the whole pipeline (§6). |
| `uv_check.py --all` or `uv_check.py <id>…` | Texels a rig samples that are transparent (UV holes). |
| `beast_preview.py <id>…`, `pose_preview.py <id> [frames]` | Offline renders of a rig at rest / posed. |
| `entity_contact_sheet.py <shots-dir> <out-dir>` | Contact sheets from an `EntityShowcaseCapture` run. |
| `item_model_preview.py`, `block_texture_sheet.py`, `broom_lineup.py`, `broom_silhouettes.py`, `armor_preview.py --out DIR`, `skill_web_preview.py` | Contact sheets and acceptance renders. |

### 3.3 Order between generators

Each generator reproduces its own files from the repository as it stands, so a single one can be
re-run alone. When you *change* art, refresh what derives from it:

1. a creature skin or rig → its `<id>_model.py` (and the chain in §3.2 if it has one);
2. then `bestiary_portraits.py` and `spawn_eggs.py`;
3. a block texture that another is derived from (e.g. `examination_desk` → `examination_desk_top`)
   → re-run the deriving tool;
4. a spell's colour in `data/.../spells/<id>.json` → `spell_sigils.py`.

Every one of those refreshes changes approved art: judge it in the client (report R13) first.

---

## 4. Audit of `tools/` (2026-09-28)

Method: every generator was run in an isolated copy of the repository and its output compared
with the live files (pixels for PNG, parsed value for JSON), twice, under different hash seeds.
Provenance came from the PNG markers (1,428 marked PNGs; 28 unmarked, all hand-made).

**A — required, actively used: 167 scripts plus `requirements.txt`, tracked in git.** Everything
in §3.2, the libraries, the validation tools and `art_sync_check.py`. `.gitignore` tracks `tools/*.py`
by default and names the retired scripts below.

**B — useful but obsolete** (local only, not tracked):

| Tool | Why not kept |
|---|---|
| `entity_art_inventory.py`, `entity_art_verdicts.py` | 2026-09-25 audit tables; the verdicts are hard-coded from that review. |
| `gui_mockups.py`, `map_mockup.py`, `map_item_preview.py` | Before/after mockups for designs that have since shipped. |
| `tools/audit/*.py` | Static code audits (registry, lang, reachability). Not part of the art pipeline. |

**C — dangerous or superseded** (local only; each would overwrite approved art or data):

| Tool | What a run does |
|---|---|
| `tools/dragons/` (`generate.py` …), `horntail_model.py` | Rewrites the ten reference-sheet dragons with the old parametric breeds (report §4). Superseded by `dragon_model.py`. |
| `creature_gen.py`, `creature_gen_new.py`, `bestiary_placeholders.py` | Placeholder rigs, clips and bestiary shapes; `creature_gen_new` rewrites 232 files, `bestiary_placeholders` 213 portraits. |
| `creature_behavior.py` | Rewrites temperament/damage/traits in 81 creature definitions (since tuned by hand). |
| `canon_items.py` | Rewrites `CanonItemRegistry.java` and `en_us.json`. |
| `wand_variants.py` | Appends variants to the frozen `wand.geo.json`. |
| `rig_repair.py` | One-shot geometry repair; now rewrites 8 finished rigs (dragons, brooms). |
| `map_item_model.py` | Overwrites the Marauder's Map model now owned by `item_geo.py`. |
| `item_icons.py`, `legacy_sprites.py`, `canon_sprites.py`, `armor_item_icons.py`, `spell_source_sprites.py` | Earlier item-icon engines, superseded by `item_sprites.py`; they either write nothing (protected) or resurrect deleted/stale icons. |
| `gen_chamber_nbt.py` | Rewrites the Chamber of Secrets structure with a placeholder room (non-deterministic gzip). |
| `tools/spell_corpus/` | One-shot corpus registration and skill-web pass; the placeholder generator halts on its own count check, the skill pass re-adds a removed node. |
| `tools/wizarding_armor_tools/`, `tools/wizards_beasts_armor_generator/` (+ zip) | Earlier armour generators, superseded by `armor_model.py`. |

**D — dead code** (local only): `dragon_anims_apply.py`, `dragon_traits_apply.py` (one-shot
patches for the retired dragons), `creature_lore.py` (one-shot lang fill), `limb_layout.py`
(one-shot leg layout, a no-op now), `death_eater_masks.py`, `gen_mask_textures.py` (superseded by
`armor_model.py`), `petrify_stone_skin.py` (placeholder; the shipped stone skin is hand-made),
`test_armor.ps1`, and the stray PNG/JPG/TXT outputs, `tools/src/` and `tools/death_eater_masks/`.

---

## 5. Reproducibility fixes made in this pass

None of these changes what a generator produces for the shipped art; each makes a rerun produce
exactly the shipped file where it previously did not.

| Generator | Problem | Fix |
|---|---|---|
| `artgen_common.py`, `block_textures.py`, `spawn_eggs.py`, `animagus_skins.py` | Vanilla jar looked up only in ForgeGradle's cache (another project's) or `build/moddev`; `sorted(glob)[-1]` would pick 1.21.4 over 1.21.11 | One resolver, pinned to `gradle.properties` (§1) |
| `chocolate_frog.py`, `gui_parchment.py` (seals), `wizard_cards.py` (star fields) | Seeded from `hash(str)`, which Python salts per process: every run differed | Fixed seed tables, recovered by search against the shipped PNGs (each a unique match) |
| `item_geo.py` | Painted through `rigkit.Skin`, whose default grain changed on 2026-09-27; all 31 textures drifted | Pinned to the legacy `speckle` grain, as the protected redesigns are |
| `pygmy_puff_model.py` / `puffskein_model.py` | Style pass moved the shared recipe to `fleck`; the Pygmy Puff was never repainted | Pygmy Puff pinned to `speckle` via its spec |
| `dragon_model.py` | Deleted the definitions' top-level `scale` (1.25–1.5, the R9 size ladder) | Removes only the legacy `dragon.scale` |
| `broom_model.py` | Would restore the `root` bob the shipped clips dropped on 2026-09-17 | Clip table matches the shipped file |
| `animate_textures.py` | Read its own output back, so every run grew three strips eight-fold (next run: 16×65536) | Animates frame 0 of a strip; skips already-animated overlays unless `--reanimate` |
| `spawn_eggs.py` | Repainted the four hand-set eggs (they still carry its marker) | Explicit skip list |
| `item_sprites.py` | Recreated six icons deleted as unreachable in `220a8e0e` | Definitions removed |
| `mooncalf_model.py` | Crashed printing its summary (loop variable shadowed the scale) | Renamed |
| `beast_skins.py` | Standalone run dropped eight stray skins | Retired as a writer; library use unchanged |

---

## 6. Validation

**Reproduction.** `python tools/art_sync_check.py` copies the repo into a temp sandbox per worker,
runs each pipeline job there, compares every written file with the live one, and repeats each job
under another `PYTHONHASHSEED`. It never writes the live tree. Exit status 1 on any unexpected
difference.

Results 2026-09-28 (141 jobs):

- **Developer working tree:** 137 in sync, 4 known drift (§8), 0 failing.
- **Fresh clone** of `HEAD` plus this change set (no uncommitted work): `./gradlew runData` and
  `./gradlew build` green; sync check 127 in sync, 4 known drift, 10 differing. The 10 are the
  generators of the 2026-09-27/28 creature work that was still uncommitted in the shared tree
  (Griffin, Glumbumble, Blast-Ended Skrewt, Knarl, Stag Animagus, the Augurey / Cornish Pixie /
  Mooncalf / Streeler reaction clips, the `smoke_puff` particle): the generators on disk already
  draw the new art, the committed assets are the old art. They agree again once that work is
  committed. The same applies to `runData` in the clone: `HEAD`'s generated `en_us.json` (and one
  trailing newline) came from uncommitted datagen code.

**Tests.** `./gradlew build` (JDK 21) runs, among others: `VisualStyleConformanceTest` (palette,
noise, heartwood, gilt, scale ladder), `CreatureRigGuardTest`, `BroomModelParityTest`,
`ArmorGeoAssetTest`, `WizardCardAssetsTest`, `ControlsAtlasTest`, `CanonItemCatalogTest`,
`SkillNodeJsonTest`, `ModuleTagDataTest`. Never run two Gradle builds at once.

**UV coverage.** `python tools/uv_check.py --all` — known exceptions only: brooms, `player`,
`protego_shield`, Obscurus.

**Datagen.** Snapshot `src/main/resources` and `src/generated/resources`, run `./gradlew runData`,
diff. Expected: only `src/generated/resources` changes, and only where a provider changed.

**In the client** (report R13): `WB_ENTITY_SHOWCASE=<list> WB_ENTITY_SHOWCASE_VIEWS=front,side,far,night ./gradlew runClient2`
and `WB_ITEM_SHOWCASE=<list> ./gradlew runClient2`, then compare contact sheets.

---

## 7. Protected assets

From `VISUAL_STYLE_REPORT.md` §4, with how each is now enforced:

| Asset | Rule | Enforced by |
|---|---|---|
| Ten dragons | only `dragon_model.py` | `tools/dragons/` and `horntail_model.py` are not tracked (§4 C) |
| Dementor, Hippogriff (+coats), Basilisk, Phoenix, Kelpie (+disguise, +coats) | generators pinned to `Skin(grain="speckle")` | the pins; sync check |
| Obscurus rig | never a bare full run | the chain in §3.2 (verified exact) or `--form-only` |
| Bowtruckle, Cornish Pixie, Phoenix, Thestral spawn eggs | hand-set | `spawn_eggs.HAND_SET` (only `--force` repaints) |
| Wand rig `wand.geo.json` | frozen | `wand_variants.py` not tracked; `wand_skin.py` paints only |
| Parchment kit, spell sigils, item icons, item geo | only their owner | `artgen_common.PROTECTED` |
| Wizard cards | 128 px exception | — |
| `src/main/resources` after `runData` | never `git add -A` | datagen writes only `src/generated` now; still stage by path |

Hand-made, no generator (keep, or claim into a generator explicitly): 19 mob-effect icons, the
spell-wheel sprites (`gui/sprites/wand_hud/bg.png`, `overlay.png`, `overlay_selected_0..3.png`),
`entity/petrify/stone_statue.png`, `entity/form/placeholder.png`, and `src/main/resources/logo.png`.

---

## 8. Known drift: where a rerun would change approved art

These files are *not* what their generator produces today. Each is left as shipped because
regenerating would visibly change it; `art_sync_check.py` reports them as `KNOWN`, not failures.
Refreshing any of them is an owner decision, judged in the client.

| File(s) | Generator | Cause |
|---|---|---|
| spell icons `accio`, `capacious_extremis`, `diffindo`, `episkey` | `spell_sigils.py` | The spells' colours in `data/.../spells/*.json` changed in the 2026-09-27 semantic-colour pass (R7) after the icons were drawn. The generator follows the data. Folds into the A1 sigil redraw. |
| `textures/block/examination_desk_top.png` | `block_textures.py` | Derived from `examination_desk.png`'s palette; the desk side was repainted on 2026-09-08, the top was not refreshed (differs by ≤ 16 per channel). |
| `textures/gui/map/controls.png` | `map_textures.py` | Last written 2026-09-02; the generator was reworked for the parchment kit on 2026-09-23 and this sheet was not re-emitted. |
| bestiary icon `lethifold`, icon + silhouette `obscurus` | `bestiary_portraits.py` | Portraits predate the latest Lethifold skin and Obscurus rig. |
| `textures/block/{enchanted_ceiling_tile,floo_grate,warding_stone}.png` | `animate_textures.py` | 16×8192 (512 frames) from three compounding runs before the fix in §5; designed as 8 frames. Not reported by the sync check because the default run keeps them; `--reanimate` rebuilds them at 8 frames (visible change: the pulse cycle). |

---

## 9. Changing art safely

1. Find the owner (§3.1) and read §7. If the file is protected, follow its rule.
2. Work on a copy: run the generator in a sandbox (`art_sync_check.py --only <job> --keep --sandbox DIR`
   leaves one in place), or copy `tools/` and `src/` to `C:/tmp/...` yourself.
3. Before editing a generator, confirm it reproduces the live files (sync check). If it does not,
   fix that first; otherwise your change ships someone else's drift with it.
4. Regenerate, compare in the client, refresh derived art (§3.3), run `./gradlew test` and the
   sync check (update `KNOWN_DRIFT` only for a deliberate, documented exception).
5. Stage by path. This working tree is shared; never `git checkout`/`restore` to undo, never
   `git add -A`.

Do not: seed randomness from `hash()` of a string (use `zlib.crc32` or a fixed table); read a
file your tool also writes without guarding against its own output; add a generator that writes
into `src/generated/resources`; commit a retired script from §4 to make a run "work".
