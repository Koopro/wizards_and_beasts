# Beast Renaissance — creature audit and plan

Revision 2026-11-05 · `dev` @ ef673f65 · audit only, nothing implemented yet.

This page holds seven things:

1. the state of the creature ecosystem, checked in code;
2. a quality matrix for each species;
3. a lore matrix;
4. the proposed shared architecture;
5. a roadmap for each species;
6. milestones;
7. the files expected to change.

It **supersedes the plan sections** of `CREATURE_QUALITY_MATRIX.md` §3 and `CREATURE_IDENTITY_STATUS.md` §5. It does **not** supersede the design rules in `ENTITY_DESIGN_BIBLE.md` and `VISUAL_STYLE_GUIDE.md`, or the canon rulings in `CANON_AUDIT.md` §14. Those stay binding.

**How this audit was done.**
- A per-species fact sheet was measured from data, assets and code: 108 entity ids, of which 107 are creatures and `veela_harpy` is a player form.
- `CREATURE_QUALITY_MATRIX_DATA.md` was regenerated.
- Six read-only code audits covered the shared core, the bespoke species (two groups), world systems, visuals and audio, and the prior docs.
- Critical claims were re-checked by hand and are marked ✓ below.
- Visual verdicts use the VR-0 in-game captures (`C:/tmp/wb-lw-alpha/vr0_entities/screenshots/`) where they exist, and offline `beast_preview.py` renders otherwise.
- Nothing was run in a client for this audit.

**Grades.**
- **Behaviour:**
  - A: several unique mechanics that work.
  - B: real identity, with gaps.
  - C: generic AI plus at least one ability that changes the world or the movement.
  - D: generic AI plus abilities that only apply effects, or core behaviour that is broken.
- **Visual:** A–D as in VR-0; `·` means not judged.
- **Lore:** ✓ faithful; ~ interpretation dominates, or a key canon trait is missing; ✗ contradicts canon.

---

## 0. Summary

**The roster is wide but shallow.** 107 creatures exist:
- 11 are fully bespoke: their own entity class and no creature JSON.
- 96 are data-driven: they run on the four generic classes, configured by `creatures/<id>.json`.
  - 14 of those have a dedicated subclass: Acromantula, Basilisk, Hippogriff, Kelpie, and `DragonEntity` for all ten dragons.
  - The other 82 are purely generic.

**What already works** is a solid base, and nothing in it should be rewritten:
- the ability framework (54 types, each with a real hook);
- the ecology brain (predator, prey, herd, territory, nocturnal rest);
- AI scheduling tiers;
- bonding and companions;
- the bestiary discovery tiers;
- harvest gating;
- the glowmask pipeline.

Nine bespoke creatures (Niffler, Bowtruckle, Phoenix, Dementor, Acromantula, Basilisk, Kelpie, Hippogriff and, partly, the Thestral) have real, tested, species-specific behaviour.

**Ten findings decide the plan:**
1. **Most of the roster can't be met in survival.**
   - 78 species have no natural spawn at all.
   - The default `creatureNaturalSpawns=ALPHA_ONLY` blocks 15 more that do have spawn files.
   - A default world spawns 13 species ✓, plus the Chamber basilisk and command-seeded Dementors.
   - Nine of the ten alpha dragons can't be met.
2. **The day/night spawn rule checks shade, not time** ✓ (`BeastSpawnHandler.java:188`, `getRawBrightness(pos,0)` reads stored sky light). As a result:
   - Night species spawn only in caves or deep shade: mooncalf, niffler, thestral, ghoul, rougarou, werewolf.
   - Day species spawn at night too.
3. **On a dedicated server or as a LAN guest, generic creatures play no one-shot clips** ✓.
   - `CreatureDefinitionRegistry` is filled only by a server reload listener and never synced.
   - The client builds the `beast_action` controller from it (`GenericBeastEntity.java:902-915`).
   - So attack, hit, death, idle, ambient, web and gaze clips do nothing outside singleplayer.
4. **Generic creatures have no daily life.** They have no sleep, eating, drinking, weather response, habitat seeking, perching or shoreline behaviour.
   - "Graze" is a timed clip that can fire mid-walk.
   - Ecology profiles exist for 20 of the 96 data-driven species. The other 76 wander, look and switch on temperament.
5. **Locomotion has two states.**
   - The client guesses idle or moving.
   - Run, takeoff, land, glide, perch, burrow and climb loops exist only on bespoke rigs.
   - Generic fliers (all ten dragons, the winged horses, the griffin) flap while walking. They get no fall-damage protection, so a dragon likely takes fall damage after every wander; to be confirmed in game.
   - Aquatics play swim on land.
   - There are zero sleep clips in the roster.
6. **The roster is silent.**
   - No mod audio file ships; all 103 sound events play vanilla samples.
   - 90 creatures have no voice at all, including every dragon, the unicorn, the thestral, the mooncalf, the occamy and the centaur.
7. **Combat is one vanilla `MeleeAttackGoal`.**
   - HOSTILE targets only players.
   - `CombatProfile` style, windup, recovery, range and retreat are parsed but unused.
   - The most common "identity" ability is a Speed+Strength potion (`enrage`, 33 species).
8. **Lore errors in data:** 33 species carry abilities or looks that contradict canon (§3), for example:
   - Centaur hexes instead of arrows.
   - Doxy has no venom.
   - Sea Serpent constricts, although canon records no attack on a person.
   - Yeti releases a spore cloud.
   - Giant casts hexes instead of throwing boulders.
   - Runespoor heads are wrong.
   - Ridgeback is tan; Hebridean is purple.
9. **The iconic creatures look weakest:** Centaur, Horntail, Hippogriff, Unicorn, and the ten dragons, which share one silhouette.
10. **Dozens of shipped bugs**, listed in §2.3. Examples:
    - The Hidebehind never attacks ✓.
    - Runespoor heads are dead on the client.
    - A Hippogriff or Thestral can be left hanging in the air after a reload.
    - Niffler pocket-carry and Bowtruckle lock-picking can't be triggered by right-click ✓.
    - Dragon breath ignores mobGriefing.

**Recommended order:**
1. Fix what is broken and invisible (BR-0).
2. Give every creature a synced activity state and a locomotion vocabulary (BR-1).
3. Give creatures a daily life through the existing ecology brain (BR-2).
4. Make them reachable in the world (BR-3).
5. Then work on combat, looks, voice and lore species by species, with look approval before each rollout.

---

## 1. Shared architecture: what exists

### 1.1 Data and classes
| Piece | Where | State |
|---|---|---|
| Definition | `creature/CreatureDefinition*.java`, `data/.../creatures/*.json` (96 files) | Applied once in the constructor, server side: health, speed, follow range, flying speed, damage, scale. Read live: temperament, traits, abilities, clips, idle and sound lists, approach speed. **Never synced to clients.** Ignored: `model/texture/animation/locomotion/width/height`, most of `combat`, `sounds.attack/special`. Trait `AMPHIBIOUS` (12 files) has no consumer. |
| Registration | `registry/ModCreatures.java` `MANIFEST` | Chooses the class and hitbox. Every generic is `MobCategory.CREATURE`, including hostile and aquatic ones. Nothing is `notInPeaceful`. Wild beasts are `PathfinderMob`, so they despawn like monsters but respawn only on the 400-tick creature cycle. |
| Generic classes | `entity/creature/Generic{Ground,Flying,Aquatic,Sessile}BeastEntity` + `GenericBeastEntity` | Final `registerGoals`: Float, Panic/Avoid (passive or fearful), EcologyGoal 3, Melee 4, movement 5, follow owner 6, CreatureIdleGoal 8, look 9/10. Temperament split: 29 HOSTILE, 39 NEUTRAL melee, 6 NEUTRAL inert, 22 PASSIVE. |
| Abilities | `creature/ability/` (54 types) | All hooked; none are no-ops. **Real:** dive_bomb, flame_burst, leap, ranged_hex, web_snare, lure_disguise, wary, boggart_dread, hoard_guard, star_reading, fire/water affinity, blink_away, evasion, foresight, blast_propulsion, choranaptyxis, duplication, block_decay, ember_trail, explosive_horn, infighting, forest_keeper, constrict, spell_resist, thorns, life_leech, camouflage, watched_invisibility, lethifold_smother, lycanthropic_bite, slayer_curse, moon_bound, thunderbird_storm, shed, groomable. **Effect only:** enrage, frenzy, pack_tactics, damage_reduction, heal_aura, status_on_hit, dread_aura, fwooper_song, nundu_pestilence, spore_cloud, anchor, danger_sense, death_cry, bioluminescence (particles plus Glowing; no light emitted), tint, jarvey_jinx, sphinx_riddle (no answer mechanic), merfolk_song. |
| Ecology | `creature/ecology/` | `EcologyBrain` decides on the MEDIUM tier or when a stimulus wakes it. Capabilities that behave: predator, prey, herd, territorial, aggressive, nocturnal (rest or shelter by day), tree-dwelling, cowardly, curious, social, scavenger, treasure-seeker (looks only), human-avoidant or friendly, magical-sense, illusion-aware, fear-sensitive, corruption-sensitive. **Labels only:** aquatic, flying, burrowing, climbing. Stimuli actually raised: SPELL, CREATURE_DEATH, DAMAGE. **Never raised:** FIRE, LIGHT, DARKNESS, PLAYER, ENVIRONMENT. State isn't saved, so home re-anchors on every load. Profiles: 27 of 107. Not wired into Augurey, Hidebehind, Runespoor, Streeler or Dementor (whose profile is dead data). |
| Schedule | `creature/schedule/AiCadence`, `AiBackoff`, `AiPursuit` | FAST 1 / MEDIUM 20 / SLOW 100, staggered by entity id. Sound; keep it. Note that goals without `requiresUpdateEveryTick` tick every second tick, which doubles the real cooldowns of DragonBreath, WebSnare, RoosterCrow, DiveBomb, and the niffler and bowtruckle scans. |
| Animation | `AnimHelper.movementController`, `beast_action` | The loop is guessed on the client from `isMoving()` (binary). One-shots are server `triggerAnim` calls, but they only reach clips registered from the unsynced definition (finding 3). No head tracking. Copy-pasted clip bodies: shake ×17, sniff ×16, graze ×26, peek ×12. Graze plays on carnivores and people (nundu, manticore, sphinx, wampus, kneazle, matagot, crup, centaur, chimaera). |
| Rendering | `TintedBeastRenderer` (generic), per-species renderers for the bespoke and dragons | 49 glowmasks auto-attached. Alpha tints are drawn as cutout, so they never fade. Coat variants exist for only hippogriff (4), kelpie (2) and niffler (3). Babies: only `BABY_NIFFLER` as its own type; juveniles of mooncalf and bowtruckle scale through `SCALE`. |
| Sound | `ModSounds`, `sounds.json`, `SoundProfile` | 103 events, all `minecraft:` samples. 6 JSON profiles plus 7 Java overrides. 90 creatures are silent. `merperson.song/screech` and `blast_ended_skrewt.blast` are registered but never played. |
| VFX | vanilla particles plus 4 generic mod particles | No creature-specific particle. Dragon breath shares one FLAME/LAVA/ASH core across breeds. |
| Spawning | `neoforge/biome_modifier/spawn_*.json` (28), `BeastSpawnHandler`, `NifflerSpawnHandler`, `AlphaRoster`, `CreatureRulesService` | See finding 1–2. The admin toggle can only veto, and it reads "true" for species the roster blocks. No test covers natural spawns. |
| Bonding | `creature/bond/`, `creature/companion/`, 9 bond and companion files | Saved on the entity, roster on the player. Breeding only for mooncalf and bowtruckle; no `AgeableMob`, no eggs. The owner's sneak + empty hand is caught by the pat branch before species code runs (`BondableBeast.java:167` ✓). |
| Harvest and loot | `loot/BestiaryHarvestLootModifier`, `bestiary/harvest` (27 rules), 106 loot tables | Kill harvest is study-gated (STUDIED takes 6,000 ticks of calm watching). Hostile targets are almost impossible to "watch calmly". 17 of 26 harvest species can't be met by default. 60 species drop nothing of their own (14 empty tables, 46 vanilla-only). |
| Bestiary | `bestiary/`, `BestiaryDiscoveryHandler` | Per-player scan every 20 ticks (16 blocks, one raycast per creature). The full snapshot is resent on every change. Only 7 entries declare a signature. `veela_harpy` has no entry. |
| Tests | ~45 JUnit classes, 11 GameTest classes | Good on rules (gaze, aura, harvest, bond, schedule). **59 species are never named in any test.** No natural-spawn, persistence-reload, multiplayer-render or flight-landing tests. |

### 1.2 Design rules already binding (do not relitigate)
`ENTITY_DESIGN_BIBLE`, `VISUAL_STYLE_GUIDE` and `CANON_AUDIT` §14 already bind this work:
- **Identity:** silhouette in motion first, colour last. Tag every claim `[canon]` or `[design]`.
- **Effects:** emissive must be earned. Size goes through `Attributes.SCALE`, never through the renderer.
- **AI structure:** no second ability or reaction layer, and no general climbing system. Bespoke entities are not forced into `CreatureDefinition`.
- **Clip names** must match the hook lists. A new idle word is agreed first.
- **Art tools:** dragons only through `dragon_model.py`; the do-not-regenerate list in STYLE §4; the silhouette is stated as ASCII and confirmed before any cube is placed.
- **Owner decisions:**
  - Dragons are never bondable.
  - Lore materials never come from a kill (unicorn, phoenix).
  - A kill never pays more than keeping the creature.
  - Audio is license-free and self-made (2026-11-04).
  - The Hippogriff rebuild is approved.
  - Dragons follow the user's reference sheet (5 wyverns, 5 four-legged).
  - The Occamy is a winged snake with a bird head and no legs.
- **Process:** one example with side-by-side screenshots before any look rollout. Captures are taken on the hidden-desktop client.

### 1.3 Stale claims found in prior docs
These are corrected by this page:
- **VR §4.1** says "22 alpha spawn naturally". The real number is 13.
- **MATRIX §1** lags the data: niffler and hippogriff non-combat are ●, and unicorn and demiguise bonding are ●.
- **The matrix script** is missing four species from `BESPOKE` (augurey, streeler, hidebehind, runespoor). It also grades Habitat ● for spawn files that the roster gate makes inert.
- **KNOWN_ISSUES** has three wrong lines: "hippogriff does not bow", "bonding reaches four", and the unicorn kill effects (contradicts C-6).
- **BIBLE** has three stale lines: "no rig has a death clip", "thestral visibility does not exist", and "one dragon rig, ten furniture swaps" (contradicts the reference-sheet ruling).

---

## 2. Species audit

### 2.1 Species quality matrix (all 107)
Columns:
- **Prio:** P1 = alpha roster or progression-critical; P2 = iconic, or has spawn data; P3 = long tail.
- **Spawns:** `yes` means it spawns in a default world. `gated` means a spawn file exists but `ALPHA_ONLY` blocks it. `none` means there is no natural route.
- **Clips:** the number of clips in the animation file.
- **Voice:** what a player actually hears. `remap` means mod events that play vanilla samples.
- **Tested:** whether any JUnit test or GameTest names the species.

| Species | Prio | Class | Spawns (default) | Clips | Behaviour | Visual | Voice | Lore | Tested |
|---|---|---|---|---|---|---|---|---|---|
| `abraxan` | P3 | gen·Flying | none | 9 | D | C | silent | ~ | no |
| `acromantula` | P2 | Acromantula | gated | 11 | A | B | vanilla | ~ | yes |
| `aethonan` | P3 | gen·Flying | none | 9 | D | C | silent | ~ | no |
| `antipodean_opaleye` | P1 | gen·Flying | none | 8 | B | C | silent | ✓ | yes |
| `ashwinder` | P3 | gen·Ground | none | 8 | C | B | silent | ✓ | yes |
| `augurey` | P2 | Augurey | gated | 4 | B | · | cry only | ~ | yes |
| `basilisk` | P1 | Basilisk | Chamber | 14 | A | B | 1 hiss | ~ | yes |
| `billywig` | P3 | gen·Flying | none | 7 | D | B | silent | ✓ | no |
| `blast_ended_skrewt` | P3 | gen·Ground | none | 8 | B | · | unused | ✓ | yes |
| `boggart` | P3 | gen·Ground | none | 8 | B | · | silent | ✓ | yes |
| `bowtruckle` | P1 | Bowtruckle | yes | 12 | A | B | remap | ~ | yes |
| `bundimun` | P2 | gen·Ground | gated | 6 | C | · | silent | ✓ | no |
| `centaur` | P2 | gen·Ground | none | 9 | B | D | silent | ✗ | yes |
| `chimaera` | P3 | gen·Ground | none | 9 | D | B | silent | ✓ | no |
| `chinese_fireball` | P1 | gen·Flying | none | 8 | B | C | silent | ✓ | yes |
| `chizpurfle` | P3 | gen·Ground | none | 7 | C | · | silent | ~ | no |
| `clabbert` | P3 | gen·Ground | none | 6 | C | · | silent | ✗ | no |
| `common_welsh_green` | P1 | gen·Flying | yes | 8 | B | C | silent | ✓ | yes |
| `cornish_pixie` | P2 | CornishPixie | gated | 4 | B | · | remap | ~ | yes |
| `crup` | P3 | gen·Ground | none | 9 | C | B | vanilla | ~ | no |
| `dementor` | P2 | Dementor | cmd | 8 | A | B | remap | ✗ | yes |
| `demiguise` | P1 | gen·Ground | yes | 6 | B | C | silent | ✓ | yes |
| `diricawl` | P3 | gen·Ground | none | 8 | C | · | silent | ✓ | no |
| `doxy` | P3 | gen·Flying | none | 7 | C | C | silent | ✗ | no |
| `dugbog` | P3 | gen·Ground | none | 9 | C | · | silent | ~ | no |
| `erkling` | P3 | gen·Ground | none | 7 | D | · | silent | ✗ | no |
| `erumpent` | P2 | gen·Ground | none | 9 | C | B | silent | ✓ | no |
| `fairy` | P3 | gen·Flying | none | 7 | D | · | silent | ✓ | no |
| `fire_crab` | P3 | gen·Ground | none | 8 | C | B | silent | ✓ | yes |
| `flobberworm` | P3 | gen·Ground | none | 4 | D | D | silent | ✓ | no |
| `fwooper` | P3 | gen·Flying | none | 9 | D | B | vanilla | ✗ | no |
| `ghoul` | P1 | gen·Ground | yes | 7 | D | · | silent | ✓ | yes |
| `giant` | P3 | gen·Ground | none | 8 | C | · | silent | ✗ | yes |
| `glumbumble` | P3 | gen·Flying | none | 7 | D | · | silent | ✗ | no |
| `gnome` | P3 | gen·Ground | none | 6 | C | B | silent | ✗ | no |
| `golden_snidget` | P2 | gen·Flying | gated | 7 | C | · | silent | ✓ | yes |
| `granian` | P2 | gen·Flying | gated | 9 | D | C | silent | ~ | no |
| `graphorn` | P3 | gen·Ground | none | 9 | C | · | silent | ✓ | no |
| `griffin` | P2 | gen·Flying | none | 9 | B | B | 1 screech | ✓ | yes |
| `grindylow` | P2 | gen·Aquatic | none | 7 | B | B | vanilla | ✓ | yes |
| `hebridean_black` | P1 | gen·Flying | none | 8 | B | C | silent | ✗ | yes |
| `hidebehind` | P2 | Hidebehind | gated | 2 | D | · | silent | ~ | no |
| `hippocampus` | P3 | gen·Aquatic | none | 7 | C | D | silent | ✓ | no |
| `hippogriff` | P1 | Hippogriff | yes | 16 | A | C | vanilla | ~ | yes |
| `hodag` | P3 | gen·Ground | none | 9 | C | · | silent | ✗ | no |
| `horklump` | P3 | gen·Sessile | none | 3 | D | B | silent | ✓ | yes |
| `horned_serpent` | P2 | gen·Aquatic | gated | 8 | D | C | silent | ~ | yes |
| `hungarian_horntail` | P1 | gen·Flying | none | 8 | B | D | silent | ~ | yes |
| `imp` | P3 | gen·Ground | none | 6 | C | · | silent | ✗ | no |
| `jarvey` | P3 | gen·Ground | none | 9 | C | B | vanilla | ✓ | no |
| `jobberknoll` | P3 | gen·Flying | none | 7 | D | · | silent | ✗ | no |
| `kappa` | P3 | gen·Aquatic | none | 7 | C | · | silent | ~ | no |
| `kelpie` | P2 | Kelpie | gated | 19 | A | C | remap | ~ | yes |
| `knarl` | P3 | gen·Ground | none | 8 | C | B | silent | ~ | no |
| `kneazle` | P2 | gen·Ground | none | 9 | C | B | silent | ✓ | no |
| `leprechaun` | P3 | gen·Ground | none | 7 | C | · | silent | ✗ | no |
| `lethifold` | P3 | gen·Ground | none | 7 | B | D | silent | ✓ | no |
| `lobalug` | P2 | gen·Aquatic | gated | 6 | C | C | silent | ✗ | no |
| `mackled_malaclaw` | P3 | gen·Ground | none | 7 | D | · | silent | ~ | no |
| `maledictus` | P3 | gen·Ground | none | 7 | C | · | silent | ~ | no |
| `manticore` | P3 | gen·Ground | none | 9 | C | B | silent | ✓ | no |
| `matagot` | P2 | gen·Ground | gated | 9 | B | B | silent | ✓ | yes |
| `merperson` | P2 | gen·Aquatic | none | 8 | C | C | unused | ✗ | yes |
| `moke` | P3 | gen·Ground | none | 8 | C | · | silent | ✗ | yes |
| `mooncalf` | P1 | Mooncalf | yes | 5 | B | C | silent | ~ | yes |
| `murtlap` | P3 | gen·Ground | none | 8 | C | B | silent | ~ | no |
| `niffler` | P1 | Niffler | yes | 10 | A | C | remap | ~ | yes |
| `nogtail` | P3 | gen·Ground | none | 9 | C | · | silent | ✗ | no |
| `norwegian_ridgeback` | P1 | gen·Flying | none | 8 | B | C | silent | ✗ | yes |
| `nundu` | P3 | gen·Ground | none | 9 | D | B | silent | ~ | yes |
| `obscurus` | P1 | gen·Flying | none | 7 | C | C | silent | ~ | yes |
| `occamy` | P1 | gen·Ground | yes | 7 | B | C | silent | ~ | yes |
| `peruvian_vipertooth` | P1 | gen·Flying | none | 8 | B | C | silent | ~ | no |
| `phoenix` | P1 | Phoenix | yes | 11 | A | B | remap | ~ | yes |
| `plimpy` | P3 | gen·Aquatic | none | 7 | C | · | silent | ✗ | no |
| `pogrebin` | P3 | gen·Ground | none | 8 | C | · | silent | ✓ | no |
| `porlock` | P3 | gen·Ground | none | 6 | C | · | silent | ✗ | no |
| `puffskein` | P3 | gen·Ground | none | 7 | D | B | silent | ✗ | no |
| `pukwudgie` | P2 | gen·Ground | gated | 7 | C | · | silent | ✗ | no |
| `pygmy_puff` | P3 | gen·Ground | none | 7 | D | · | silent | ✗ | no |
| `qilin` | P3 | gen·Ground | none | 8 | C | · | silent | ✗ | no |
| `quintaped` | P3 | gen·Ground | none | 8 | C | · | silent | ✓ | no |
| `ramora` | P3 | gen·Aquatic | none | 6 | C | · | silent | ✓ | no |
| `red_cap` | P3 | gen·Ground | none | 7 | D | · | silent | ~ | no |
| `reem` | P3 | gen·Ground | none | 9 | D | · | silent | ✗ | no |
| `romanian_longhorn` | P1 | gen·Flying | none | 8 | B | C | silent | ~ | yes |
| `rougarou` | P1 | gen·Ground | yes | 7 | C | · | silent | ✗ | no |
| `runespoor` | P2 | Runespoor | gated | 2 | D | · | silent | ✗ | yes |
| `salamander` | P3 | gen·Ground | none | 8 | C | · | silent | ✓ | yes |
| `sea_serpent` | P3 | gen·Aquatic | none | 8 | C | C | silent | ✗ | no |
| `shrake` | P3 | gen·Aquatic | none | 7 | C | · | silent | ✓ | no |
| `snallygaster` | P3 | gen·Flying | none | 9 | C | · | silent | ✗ | no |
| `sphinx` | P3 | gen·Ground | none | 9 | D | · | silent | ✗ | no |
| `streeler` | P2 | Streeler | gated | 4 | C | B | silent | ~ | yes |
| `swedish_short_snout` | P1 | gen·Flying | none | 8 | B | C | silent | ✓ | yes |
| `swooping_evil` | P3 | gen·Flying | none | 8 | C | B | silent | ~ | no |
| `tebo` | P3 | gen·Ground | none | 9 | C | · | silent | ✓ | no |
| `thestral` | P1 | Thestral | yes | 14 | B | B | silent | ~ | yes |
| `thunderbird` | P2 | gen·Flying | none | 9 | C | C | silent | ✓ | yes |
| `toad` | P3 | gen·Ground | none | 7 | D | B | silent | ✓ | no |
| `troll` | P3 | gen·Ground | none | 8 | C | · | silent | ~ | yes |
| `ukrainian_ironbelly` | P1 | gen·Flying | none | 8 | B | C | silent | ✗ | yes |
| `unicorn` | P1 | gen·Ground | yes | 8 | B | C | silent | ~ | yes |
| `wampus_cat` | P3 | gen·Ground | none | 9 | C | · | silent | ~ | no |
| `werewolf` | P1 | gen·Ground | yes | 7 | B | C | silent | ~ | yes |
| `yeti` | P2 | gen·Ground | gated | 8 | C | · | silent | ✗ | no |
| `zouwu` | P2 | gen·Ground | none | 9 | C | B | silent | ~ | yes |

**Totals:**
- Behaviour: 8 A, 25 B, 50 C, 24 D.
- Visual, of 61 judged: 5 D, 27 C, 29 B, 0 A.
- Lore: 36 ✓, 38 ~, 33 ✗.
- 90 silent, 59 untested.

### 2.2 Priority species profiles
Each profile follows the same pattern: what is implemented, what is broken, what is missing for canon, how it looks, its environment and its cost. "Fix" lists the top items in order.

**Niffler** (P1, bespoke, 727 lines)
- **Works:**
  - Seeks treasure items within 12 blocks, best first.
  - Digs ore through two soft blocks, gated on mobGriefing.
  - Keeps a hoard at home; pouch window at bond 50, pocket carry at bond 80.
  - 3 coats; baby with growth.
  - Tested by `CreatureWildlifeTests` and companion tests.
- **Broken:**
  - Bond `feedSound`/`refuseSound` point at ids that don't exist, so feeding is silent ✓.
  - Pocket carry is unreachable by right-click (pat branch).
  - Carried state isn't saved; a stale `CARRIED_NIFFLER` stays on an offline owner.
  - `NifflerPocketLayer` draws nothing.
  - `growIntoAdult` re-rolls babies and loses the name and health.
  - The spawn predicate skips the roster gate.
  - The wild home pulls a bonded niffler back to its burrow.
  - Night spawns happen only in shade or caves.
- **Missing canon:** deep burrows, litters of 6–8, destructiveness indoors.
- **Visual C:** 12 bones; a near-black blob at range.
- **Environment:** no rest or sleep, no rain shelter.
- **Fix:**
  1. Sound ids, carry persistence and right-click order.
  2. Pocket render.
  3. Burrow and rest cycle.
  4. Rig rebuild with a readable bill, paws and belly pouch.

**Bowtruckle** (P1, bespoke)
- **Works:**
  - Home tree prefers wandwood.
  - Defends the tree (blinding hits) and hides at the trunk.
  - Camouflage when still; wall-climbing.
  - Lock-picking at bond 75; breeding.
  - Tested.
- **Broken:**
  - Lock-pick is unreachable by right-click.
  - The lock job never times out.
  - A felled home tree is never re-checked.
  - The home restriction fights follow-owner.
  - `TemptGoal` ignores mallowsweet.
- **Missing canon:** woodlice and fairy eggs as food (no item exists); the "calm with woodlice" interaction.
- **Visual B:** the face is a slot mask.
- **Fix:**
  1. Right-click order, lock timeout, home re-check.
  2. Woodlouse item, used for tempting and calming.
  3. Leaf-rustle VFX when it is camouflaged.

**Phoenix** (P1, bespoke)
- **Works:**
  - Rebirth cycle; tears heal and cure basilisk venom; song gives courage.
  - Flame travel; catches a falling owner; defends its owner.
  - Tested.
- **Broken:**
  - `DefendOwnerGoal` re-paths every tick.
  - Two feather sources.
  - The song buffs any nearby player.
- **Missing canon:**
  - Burning Day from old age (canon; currently only lethal damage).
  - Carrying heavy loads beyond the owner.
- **Visual B:** box-chicken body.
- **Fix:**
  1. Path throttle.
  2. Song limited to owner and allies.
  3. Age-based Burning Day.
  4. Body rig pass.

**Mooncalf** (P1, bespoke)
- **Works:** full-moon emergence, herd dance, dung at dawn, breeding. Tested.
- **Broken:**
  - A "burrowed" mooncalf is an invisible mob standing on the surface with full collision. A priority-0 goal blocks Panic, so it can be hit forever without reacting.
  - Burrow = first tick position.
  - `herdCentre()` runs an entity query on every evaluation, O(n²) on full-moon nights.
  - Spawns only in caves (finding 2).
- **Missing canon:**
  - Patterns left in crops (the canon tell).
  - Dance as the prelude to mating; no sounds.
- **Visual C:** invisible almost every night; no burrow mound.
- **Fix:**
  1. Real burrow (no collision while inside, a visible mound block or decal, flee on dig-out).
  2. Cached herd centre.
  3. Crop-pattern trampling at dance sites (respects mobGriefing).
  4. Voice.

**Thestral** (P1, bespoke)
- **Works:**
  - Visible only to players who witnessed a death (renderer gate plus witness handler).
  - Walk and flight; rideable at bond 15.
  - Scavenger eats meat.
  - Tested.
- **Broken:**
  - Hanging in the air after a reload: `flying` isn't saved but NoGravity is, a shared `WingedWalker` bug.
  - Grudge has no time limit.
  - The graze goal is cosmetic.
- **Missing canon:**
  - Drawn to blood (canon).
  - Flying a rider to a named destination.
- **Visual B:** skeletal silhouette works; the head is a horse box, not reptilian.
- **Silent.**
- **Fix:**
  1. Save the flying state.
  2. Blood attraction (hurt entities raise a stimulus).
  3. Voice.
  4. Reptilian head.

**Unicorn** (P1, generic + 6 abilities)
- **Works:**
  - Wary of the impure; groomed for hair (bond plus purity); sheds.
  - Slayer curse (C-6); heal aura; herd and prey ecology.
  - Bonding.
- **Interpretation:** `blink_away` triggers on any hit (canon unicorns are swift, not teleporters). The heal aura is design.
- **Missing canon:**
  - Foals that age gold → silver (2 yrs) → horned (4) → white (7).
  - Prefers witches.
  - Blood as a cursed half-life for the *drinker*.
- **Visual C:** grey-reading white, floating mane slab, neck gap, no shimmer.
- **Silent.**
- **Fix:**
  1. Replace blink with a gallop flee (needs the BR-1 run loop).
  2. Foal stages with age coats.
  3. Rig and texture rebuild with a luminous coat glowmask.
  4. Voice.

**Hippogriff** (P1, bespoke)
- **Works:**
  - Bow etiquette (saved); stranger warning and grudge.
  - Rideable aerial mount; 4 coats; 16 clips (best in the roster).
  - Tested.
- **Broken:**
  - Hanging in the air after a reload.
  - `dive_bomb` is effectively dead (flight refuses while it has a target).
  - `takeoff` plays on every step-up jump.
  - Grudge and warned maps are never pruned.
- **Lore:** graze clip ✗ (canon diet: insects, birds, small mammals).
- **Visual C:** a horse with a beak (rebuild approved 11-04).
- **Voice:** a parrot.
- **Fix:**
  1. Flight-state persistence.
  2. Takeoff toward a target, so dive bomb fires.
  3. Hunting clip instead of graze.
  4. Rebuild (approved).

**Ghoul** (P1, generic) — behaviour D:
- **Canon:** a harmless attic dweller that moans and bangs pipes, and eats spiders and moths.
- **Implemented:** a forest spawn with no abilities.
- **Fix:** an attic/shelter habitat (seeks roofed spaces); an ambient bang and moan cycle at night; no spawn in open forest.

**Obscurus** (P1, generic flying) — behaviour C, visual C:
- **Canon** (FB film): a dark parasitic force bound to an Obscurial. It is not an animal.
- **Implemented:** a hostile flier with dread aura and ranged hex, and no spawn route.
- **Decision needed:** keep it as a free mob, or tie it to the heritage Obscurial condition only (BIBLE open decision #7).
- **Visual:** speckle noise, no form.

**Werewolf** (P1, generic) — behaviour B:
- **Implemented:** full-moon spawn and moon-bound despawn; infecting bite (config); pack tactics; frenzy.
- **Lore:** canon werewolves hunt humans almost exclusively, so pack hunting of animals is interpretation.
- **Broken:** `discard()` with smoke in plain view.
- **Silent:** the howl clip has no sound.
- **Fix:** human-preferring target table; howl voice; fade-out despawn out of view.

**Basilisk** (P1, bespoke)
- **Works:**
  - Mood synced (calm, coiled, threaten).
  - Direct gaze kills; indirect gaze petrifies; Protego, blindness and blindfold give immunity.
  - Rooster crow; venom.
  - Chamber heart binding. Well tested.
- **Broken:**
  - Dead spawn rule in `BeastSpawnHandler:77` (the chamber's `spawn_overrides` is empty).
  - The breeding ritual needs a toad, and toads never spawn.
- **Lore:**
  - The gaze ignores mobs (canon petrified a cat and a ghost).
  - Spiders don't flee it (canon).
  - `constrict` is not canon.
- **Silent** apart from one hiss.
- **Fix:**
  1. Spiders flee it; the gaze reaches mobs.
  2. Hiss and slither voice.
  3. Remove the dead rule; spawn toads in swamps (needed for the ritual).

**Occamy** (P1, generic) — behaviour B:
- **Works:** choranaptyxis resizes the hitbox to the space; territorial predator; jungle spawn; eggshell harvest.
- **Missing canon:** nests with pure-silver eggs; aggressive defence of its eggs.
- **Known issue:** it can suffocate when walled in.
- **Visual C:** stacked blue steps, no plumage. The owner ruling is a winged snake with a bird head and no legs.
- **Fix:** nest block with silver eggs and a guard state; suffocation guard; feathered rig.

**Dragons ×10** (P1, `DragonEntity` on the generic flier) — behaviour B, visual C/D, silent.

What works:
- Breath per breed (STREAM/BURST/JET/ASH, tint, range).
- Hunting livestock through ecology; temper per breed.
- `spell_resist` 0.83 (C-12).
- Heartstring harvest.

Broken or missing:
- **No flight life:** no takeoff, landing, glide, perch or soaring. `FlyingMoveControl` drops to gravity between targets, and there is no fall protection.
- **Combat pathing:** combat uses a full 3D path search for a 2.7–4-wide box (the ~10 ms search the wander goal avoided).
- **Breath** ignores mobGriefing and `SpellWorldGuard`. ASH deletes blocks without drops. About 1,300 particle sends per breath per viewer. Untested.
- **Dead data:** Ironbelly `rideable` is never read. `TICKET_FIRE_COLOR` is never read.
- **No dragon life:** no eggs, nests or rest.

Visual:
- One skeleton with four legs and two wings for all ten. The owner's reference sheet calls for 5 wyverns and 5 four-legged.
- Ridgeback is tan and Hebridean is purple-pink (both ✗).

Spawning: only the Welsh Green can be met.

Per breed, what canon requires that the mod lacks:

| Breed | Canon detail the mod lacks |
|---|---|
| Opaleye | iridescent pearly scales, multicoloured pupil-less eyes |
| Fireball | golden spike fringe; tolerates its own kind, hostile to other dragons |
| Welsh Green | melodious roar, prefers sheep, avoids people ✓ |
| Hebridean Black | black scales, purple eyes, ridges; territorial to the death |
| Horntail | tail spikes as a weapon; most dangerous |
| Ridgeback | **black** with jet ridges; venomous fangs ✓; very aggressive young |
| Vipertooth | smallest and fastest flier (the mod is fast only on ground); copper scales; eats humans |
| Longhorn | glittering golden horns (horn powder is a potion ingredient) |
| Short-Snout | silvery-blue; blue flame turns timber and bone to ash ✓ |
| Ironbelly | **largest** (the mod has it the same size as the Horntail); metal-grey; red eyes |

Fix:
1. Breath gated on mobGriefing and world guard, particles cut to a tenth.
2. Flight framework (§4.6) with perching and landing, no fall damage.
3. Direct-flight combat.
4. Reserves as the spawn route (§4.8).
5. Per-breed silhouette rebuild per the reference sheet.
6. Voice.

**Demiguise** (P1 critical, generic) — behaviour B:
- **Works:** foresight sidestep; invisible while watched; grooming and shedding of hair (cloak material); bonding.
- **Visual C:** reads as a mini yeti. Canon is a slender ape with long silky silver hair and large black eyes.
- **Problems:** invisibility uses vanilla Invisibility (hard pop); jungle and dark-forest spawn (canon: Far East).
- **Fix:** alpha-fade invisibility (needs a translucent render type for alpha tints); rig and coat rebuild; spawn in bamboo and cherry groves.

**Rougarou** (P1 critical, generic) — behaviour C:
- **Canon:** sparse (Wizarding World: a dog-headed monster of the Louisiana swamps; its hair is a wand core).
- **Implemented:** hostile, camouflage (design), swamp spawn, hair harvest.
- **Blocker:** a hostile creature can't be "watched calmly" for 5 minutes, so the progression-critical hair is effectively unreachable.
- **Fix:**
  1. A harvest study route that works for hostile species (§4.9).
  2. Dog-headed silhouette check.
  3. Voice.

**Dementor** (P2, bespoke `Monster`) — behaviour A:
- **Works:** emotion sensing, chill aura, frost, crop wilt, kiss state machine, Patronus repel. Well tested.
- **Problems:**
  - Every Dementor runs a 200-block box query every 600 ticks to spawn more, with no space check.
  - The aura re-queries Dementors per victim.
  - Azkaban ships no Dementor, so `/azkaban` seeds them.
  - The ecology JSON is dead data.
  - It feeds on animals (✗: in PoA ch.19 Sirius says their feelings are too simple for Dementors to sense).
- **Fix:** one spawner per fortress; a cached list per pulse; drop animal feeding; seed Azkaban.

**Acromantula** (P2, bespoke) — behaviour A, visual B:
- **Works:** colony home, webs, speech, threat pose, nocturnal rest, Poison bite, climbing.
- **Problems:**
  - Web keeper radius 12 is less than the territory of 20, so nest silk crumbles.
  - Webs ignore mobGriefing.
  - Gated by default.
- **Missing canon:** egg sacs; fear of the basilisk.
- **Fix:** keeper radius at least the territory; gate on mobGriefing; basilisk avoidance; egg sacs (Class A Non-Tradeable).

**Kelpie** (P2, bespoke) — behaviour A, visual C:
- **Works:**
  - Disguise, mount, grip and drown.
  - Bridle on a subdued kelpie (a saddle stands in for the Placement Charm; documented).
  - Coats; emerge and submerge.
- **Risk:** `FloatGoal` fights the dive. The test never drags a real rider.
- **Missing:**
  - A lure walk to the bank.
  - Bulrush mane.
  - Hair drop (canon wand core).
- **Fix:** suppress Float while gripping and add a real drag test; shore-lure behaviour; weed mane; hair harvest.

**Cornish Pixie** (P2, bespoke)
- **Works:** snatches the held item; a swarm lifts players.
- **Problems:**
  - The carried item isn't rendered.
  - A stolen wand can land in lava or despawn.
  - Creative players shield survival players.
  - `MakeOffGoal` stops immediately.
- **Fix:** carried-item layer; safe drop (never into lava or void, and no despawn timer for wands); target filter; goal fix; room-wrecking interaction (CoS) later.

**Augurey** (P2, bespoke)
- **Works:** cries before rain (canon ✓); flies in rain.
- **Problems:**
  - "Grounded in fine weather" is only a gravity nudge; its navigation is still flying.
  - No ecology.
- **Missing canon:** thorny-thicket nest; ink-repelling feathers.
- **Fix:** ground navigation in dry weather; bramble roost; feather item.

**Hidebehind** (P2, bespoke, 104 lines) — behaviour D:
- **Broken:** it never attacks ✓ (stalk at 1 holds MOVE across the whole follow range).
- **Clips:** only idle and walk.
- **Missing canon:** hiding behind objects, which is its defining trait.
- **Fix:**
  1. Priority fix.
  2. Break-line-of-sight positioning behind trunks.
  3. Attack, hit and death clips plus voice.

**Runespoor** (P2, bespoke) — behaviour D:
- **Broken:** head parts are positioned only on the server and have no `setId` override, so they are dead on the client.
- **Lore ✗:** heads are named plan/judge/critic and the judge poisons. Canon is left planner, middle dreamer, right critic, and the right head carries the venomous fangs.
- **Clips:** only idle and walk. Loot is a spider eye.
- **Fix:**
  1. Parts on both sides.
  2. Canon head roles with venom on the right bite.
  3. Heads that bicker.
  4. Eggs (planner).

**Streeler** (P2, bespoke)
- **Works:** hourly colour change; venom trail kills plants (respects mobGriefing); kills horklumps.
- **Problems:**
  - No `FloatGoal` and not water-avoiding, so it can drown in its swamp spawn.
  - Canon range is Africa.
- **Fix:** float or water avoidance; savanna spawn; shell or venom drop.

**Centaur** (P2, generic) — behaviour B, visual D:
- **Implemented:** star reading, forest keeper (block-break watch), herd.
- **Lore ✗:**
  - Casts hexes (canon: bow and arrows).
  - Grazes (canon: they are Beings).
  - No spawn route.
- **Visual:** mannequin torso, box head.
- **Fix:** bow attack; centaur glade habitat; face and torso rebuild; no graze; voice.

**Merperson / Grindylow** (P2, generic aquatic)
- **Grindylow:** a real ecology (ambush fish, defends its water).
- **Both:**
  - Swim on land.
  - No spawn route.
  - The merfolk song sound is never played.
- **Fix:** lake habitat (deep fresh water); stay-in-water; the song wired to the clip; merfolk spears (canon) instead of hexes.

**Griffin** (P2, generic, bondable) — hoard guard works; **can't be met in survival**. Fix: mountain spawn; voice.

### 2.3 Defect register (found by this audit)

BR-0 (2026-11-05) worked through this table: 23 fixed, 3 fixed in part (D13, D28, D29), 2 turned out not to be defects (D22, D30), 2 moved to BR-1
(D24, the dive-bomb part of D13), 1 waits for an owner decision (D19), and two look-side remainders moved to BR-5 (pixie
carried item, alpha-tint render type). Not verified in a client: D1 (needs a dedicated server and a client) and D5.
**Severity:** S1 breaks a core loop or multiplayer; S2 wrong behaviour; S3 performance or polish. ✓ marks claims verified by hand.

| # | Sev | Defect | Where | Status (BR-0) |
|---|---|---|---|---|
| D1 | S1 | No one-shot clips for generic creatures on a dedicated server or as a LAN guest; definitions are never synced ✓ | `GenericBeastEntity.java:902`, `CreatureDefinitionLoader` | fixed 400241e2 |
| D2 | S1 | Day/night spawn rule reads stored sky light ✓ | `BeastSpawnHandler.java:188`, `NifflerSpawnHandler.java:23` | fixed 9dc0621d |
| D3 | S1 | Hidebehind never attacks ✓ | `HidebehindEntity.java:91-92` | fixed 3224a3b6 |
| D4 | S1 | Runespoor head parts dead on the client | `RunespoorEntity.java:93` | fixed 3224a3b6 |
| D5 | S1 | Hippogriff and Thestral can hang in the air after a reload (NoGravity saved, `flying` not) | `entity/flight/WingedWalkerMoveControl:69` | fixed 3224a3b6 |
| D6 | S1 | Owner sneak + empty hand caught by the pat branch, so pocket carry and lock-pick are unreachable ✓ | `BondableBeast.java:167` | fixed 3a9e62b5 |
| D7 | S2 | Generic fliers have no fall protection and drop between flight targets (likely damage, check in game) ✓ | `GenericFlyingBeastEntity` | fixed 3224a3b6 |
| D8 | S2 | Dragon breath ignores mobGriefing and world guard; ASH deletes blocks | `DragonBreathGoal.java:181-205` | fixed 71116ca5 (mobGriefing / protection mods) |
| D9 | S2 | Niffler bond sounds point at missing ids ✓; carry not saved; stale owner attachment; spawn skips the roster gate | `creature_bonds/niffler.json:18`, `NifflerEntity.java:564,641` | fixed 9dc0621d, 3a9e62b5 |
| D10 | S2 | Burrowed mooncalf is an invisible collidable that never flees | `MooncalfEntity.java:205,306` | fixed 71116ca5 |
| D11 | S2 | Bowtruckle lock job never times out; dead home tree never re-checked; home restriction fights follow | `BowtruckleLockpickGoal:103`, `BowtruckleEntity:179-187` | fixed 3a9e62b5 |
| D12 | S2 | Pixie: carried item not rendered; wand can be lost in lava or to despawn; creative players shield others | `CornishPixieEntity:237` | fixed 71116ca5; carried-item render deferred to BR-5 (look) |
| D13 | S2 | Hippogriff `dive_bomb` dead; takeoff plays on step-up | `WingedWalkerFlightGoal:48`, `FlightBeats:27` | takeoff + pruning fixed 71116ca5; dive bomb → BR-1 flight framework |
| D14 | S2 | Streeler can drown (no float) | `StreelerEntity` goals | fixed 3224a3b6 |
| D15 | S2 | Acromantula nest silk crumbles (keeper 12 < territory 20); webs ignore mobGriefing | `AcromantulaWebBlock`, `AcromantulaWebs.canSpin` | fixed 71116ca5 |
| D16 | S2 | Augurey flies in dry weather | `AugureyEntity:80,124` | fixed 71116ca5 |
| D17 | S2 | Veela harpy toggle reverts within 20 ticks at ≥60% HP | `VeelaHeritageHandler.java:96` | fixed ffa2d138 |
| D18 | S2 | Horned serpent and lobalug use ground placement for aquatic species | biome modifiers, `BeastSpawnHandler` | fixed 9dc0621d |
| D19 | S2 | All beasts are `CREATURE`, none `notInPeaceful`; hostile beasts exist on Peaceful | `ModCreatures.java:183` | open: owner decision §8.3 |
| D20 | S2 | Admin "natural spawn" shows true for roster-blocked species | `CreatureAdminService.java:120` | fixed ffa2d138 |
| D21 | S2 | Saved attribute bases block datapack retuning of existing creatures; `/reload` never rewires goals | `GenericBeastEntity` constructor | fixed ffa2d138 (reload re-applies definition stats; `/reload` still does not rewire goals → BR-1) |
| D22 | S2 | Ecology home and fed state not saved | `ModAttachments.java:350` | not a defect then (transient by design); saved since BR-2 (home, needs, fed-until), `ecology_life_state_survives_a_save` |
| D23 | S3 | Dragon breath ~1,300 particle sends per breath per viewer | `DragonBreathGoal.java:212-227` | fixed 71116ca5 |
| D24 | S3 | Flying melee uses a full 3D path search for 3–4-wide boxes | `GenericBeastEntity.java:595` | open → BR-1 flight framework |
| D25 | S3 | Dementor spawner per entity (200-block box query) and aura re-query per victim | `DementorEntity.java:349,432` | fixed ffa2d138 |
| D26 | S3 | Mooncalf `herdCentre` query on every goal check | `MooncalfEntity.java:233,322` | fixed 71116ca5 |
| D27 | S3 | Goals re-path every tick (phoenix defend, niffler flee, pixie pester, mooncalf burrow) | as listed in §2.2 | fixed 71116ca5, ffa2d138 |
| D28 | S3 | Alpha tints drawn as cutout; `TICKET_FIRE_COLOR` and `DragonTraits.rideable` dead | `TintedBeastRenderer`, `DragonRenderer:52` | dragon render tickets removed ffa2d138; alpha-tint render type → BR-5 (look) |
| D29 | S3 | Dead basilisk spawn rule and stale chamber comment | `BeastSpawnHandler.java:75-80` | comment fixed 9dc0621d; the rule stays as the guard for datapack overrides |
| D30 | S3 | Merperson song/screech and skrewt blast registered, never played | `ModSounds` | not a defect: both merfolk sounds play from `MerfolkSong`, the skrewt blast from `BlastPropulsion` |

---

## 3. Lore accuracy matrix

**Sources:**
- FB: *Fantastic Beasts and Where to Find Them* (2001); FB17: entries added in the 2017 edition.
- HP1–7: the novels.
- F1–F3: the Fantastic Beasts films.
- QTA: *Quidditch Through the Ages*.
- WW: Wizarding World / Pottermore writing.
- MoM: the Ministry classification from FB (— = not classified there).

Canon is what those sources state. Everything else in the "Mod deviation" column is either ✗, a contradiction, or `design`, a gameplay interpretation the mod should label as such.

**Rule for canon gaps:** where canon is silent, keep the design restrained and in character, and tag it `[design]` in the bestiary and bible. Never present it as canon.

| Species | MoM | Source | Canon core | Mod deviation (✗ contradiction · design · missing) | Lore |
|---|---|---|---|---|---|
| abraxan | XXXX | FB, HP4 | giant palomino winged horse; drinks only single-malt whisky | `enrage` design; whisky diet missing | ~ |
| acromantula | XXXXX | FB, HP2 | giant speaking spider, Borneo jungle, colonies, venom, eggs Class A Non-Tradeable; fears basilisk | dark-forest habitat design; eggs and basilisk fear missing | ~ |
| aethonan | XX–XXXX | FB | chestnut winged horse, Britain/Ireland | shares one rig with abraxan/granian; `enrage` design | ~ |
| antipodean_opaleye | XXXXX | FB | pearly iridescent scales, multicoloured pupil-less eyes, scarlet flame; eats sheep, rarely kills people | NEUTRAL ✓, scarlet ✓ | ✓ |
| ashwinder | XXX | FB | grey serpent born from untended magical fire; red eggs that ignite; used in Love Potion | eggs missing (item exists in plan) | ✓ |
| augurey | XX | FB | Irish phoenix, green-black; cry foretells rain (not death); feathers repel ink; thorny nests | rain cry ✓; nest and feathers missing | ~ |
| basilisk | XXXXX | FB, HP2 | serpent king; direct gaze kills, indirect petrifies; flees rooster crow; spiders flee it | gaze ignores mobs; `constrict` ✗ design; spider fear missing | ~ |
| billywig | XXX | FB | sapphire Australian insect; sting gives giddiness and levitation | sting ✓; glow design | ✓ |
| blast_ended_skrewt | — | HP4 | Hagrid's illegal hybrid; blasting rear, sting, suckers; they kill each other | infighting ✓, blast ✓ | ✓ |
| boggart | — | HP3 | shape-shifter that takes the viewer's worst fear; *Riddikulus* | fear form ✓ (creature fears only) | ✓ |
| bowtruckle | XX | FB, F1 | twig-like tree guardian of wand-quality wood; eats woodlice and fairy eggs; pokes out eyes | guardian ✓; woodlice missing; lock-picking F1 ✓ | ~ |
| bundimun | XXX | FB | fungus-like pest that rots house foundations | decay ✓ | ✓ |
| centaur | XXXX | FB, HP1/5 | Beings who chose Beast status; star-reading, forest dwellers; bow and arrow | hexes ✗ (should be arrows); grazing ✗ | ✗ |
| chimaera | XXXXX | FB | lion head, goat body, dragon tail; savage | ✓ | ✓ |
| chizpurfle | XX | FB | crab-like parasite drawn to magic; devours wands and potions | `life_leech` design; magic-eating missing | ~ |
| clabbert | XX | FB | tree-dwelling frog-monkey; forehead pustule flashes scarlet at danger | glow and danger ✓; `blink_away` ✗ | ✗ |
| common_welsh_green | XXXXX | FB, HP4 | green; melodious roar; prefers sheep; avoids people | ✓ (roar missing) | ✓ |
| crup | XXX | FB | Jack-Russell-like with forked tail; loyal to wizards | `leap` design | ~ |
| demiguise | XXXX | FB, F1 | Far-East ape, long silky silver hair, large black eyes; invisible, precognitive; hair woven into cloaks | ✓ abilities; look ✗ (see visual); habitat design | ✓ |
| diricawl | XX | FB | the dodo; vanishes and reappears elsewhere | blink ✓ | ✓ |
| doxy | XXX | FB, HP5 | "biting fairy"; black hair, four limbs plus arms, beetle wings; **venomous bite**; Doxycide | venom ✗ missing; blink and evasion design | ✗ |
| dugbog | XXX | FB | marsh creature like dead wood; bites ankles; loves Mandrake | camouflage ✓; Mandrake raiding missing | ~ |
| erkling | XXXX | FB | elfish, Black Forest; high cackle lures children, whom it eats | `dread_aura` ✗ (should be a lure) | ✗ |
| erumpent | XXXX | FB, F1 | grey African rhino-like; exploding horn; hide repels charms; mating-season charge | ✓ | ✓ |
| fairy | XX | FB | small, vain, ornamental; glows; eggs under leaves | ✓ | ✓ |
| fire_crab | XXX | FB | Fijian tortoise-like with jewelled shell; flames from rear | flame ✓; shell drop missing | ✓ |
| flobberworm | X | FB, HP4 | 10-inch worm, eats lettuce; mucus thickens potions | mucus drop missing | ✓ |
| fwooper | XXX | FB | brightly plumed African bird; song drives listeners mad; sold with a Silencing Charm | song ✓; glow ✗ | ✗ |
| ghoul | XX | FB, HP2 | slimy attic-dweller; moans, bangs pipes; harmless | forest spawn design; attic habitat missing | ✓ |
| giant | — | HP4/5 | about 20 ft; clans; spell-resistant hide; throw boulders | hexes ✗; size ✗ (about 2.5× villager) | ✗ |
| glumbumble | XXX | FB | grey furry flying insect; melancholy treacle; infests hives | `spore_cloud` ✗; glow ✗ | ✗ |
| gnome | XX | FB, HP2 | garden pest, potato-headed; bites; thrown over hedges | `blink_away` ✗; de-gnoming missing | ✗ |
| golden_snidget | XXXX | FB, QTA | tiny golden bird, fast and evasive; protected | ✓ | ✓ |
| granian | XX–XXXX | FB | grey, fast winged horse | `enrage` design | ~ |
| graphorn | XXXX | FB | humped grey-purple beast, two long horns, hide tougher than a dragon's; ridden by mountain trolls | ✓ (C-12 resist) | ✓ |
| griffin | XXXX | FB | eagle front, lion hind; eats raw meat; guards treasure | hoard ✓ | ✓ |
| grindylow | XX | FB, HP3 | horned green water demon; long fingers, strong but brittle grip | ✓ | ✓ |
| hebridean_black | XXXXX | FB | **black**, purple eyes, ridge; territorial | purple-pink skin ✗ | ✗ |
| hidebehind | XXXX | FB17 | nocturnal, silvery, hides behind anything; preys on humans | hiding missing; never attacks (bug) | ~ |
| hippocampus | XXX | FB | horse front, fish tail; lays eggs | ✓ (visual D) | ✓ |
| hippogriff | XXX | FB, HP3 | eagle front, horse rear; bow first; eats insects, birds, small mammals | bow ✓; grazing ✗ | ~ |
| hodag | XXX | FB17 | horned North-American beast drawn to mooncalves | `life_leech` design; mooncalf hunting missing | ✗ |
| horklump | X | FB | pink fleshy mushroom with bristles; gnome food | ✓ | ✓ |
| horned_serpent | XXXX | FB17, WW | great serpent with a forehead jewel; Ilvermorny; horn a wand core | glow design; placement bug | ~ |
| hungarian_horntail | XXXXX | FB, HP4 | spiked tail as weapon; most dangerous | tail weapon missing; look per owner sheet | ~ |
| imp | XX | FB | small riverbank prankster, cannot fly | `blink_away` ✗ | ✗ |
| jarvey | XXX | FB | ferret that shouts short rude phrases; hunts gnomes | insults ✓ (C-11); gnome hunting missing | ✓ |
| jobberknoll | XX | FB | silent blue bird; at death screams every sound it heard, backwards | death cry ✓; glow ✗ | ✗ |
| kappa | XXXX | FB | Japanese river demon; water-filled head; bow and it spills its strength | bow weakness missing | ~ |
| kelpie | XXXX | FB | shape-shifting water horse with a bulrush mane; Placement Charm + bridle | saddle instead of charm (design, documented) | ~ |
| knarl | XXX | FB | hedgehog that treats food as a trap and wrecks the garden | thorns design; garden-wrecking missing | ~ |
| kneazle | XXX | FB | cat-like; senses untrustworthy people; guides owners home | ✓ | ✓ |
| leprechaun | XXX | FB | Irish, green; leprechaun gold that vanishes | glow ✗, blink ✗; vanishing gold missing | ✗ |
| lethifold | XXXXX | FB | living shroud, smothers sleepers at night; only a Patronus repels it | smother ✓; Patronus-only check missing | ✓ |
| lobalug | XXX | FB | North Sea floor; squirts venom from a bladder; merfolk weapon | thorns ✗ (should be a spit) | ✗ |
| mackled_malaclaw | XXX | FB | lobster-like; bite brings a week of bad luck | luck effect partial | ~ |
| maledictus | — | F2 | a woman under a blood curse who becomes a beast (Nagini) | not an animal; design question | ~ |
| manticore | XXXXX | FB | human head, lion body, scorpion tail; croons while eating; sting kills | ✓ (resist ✓) | ✓ |
| matagot | — | F2 | French spirit-cats; attacking one multiplies them | duplication ✓ | ✓ |
| merperson | XXXX | FB, HP4 | Beings; grey skin, green hair (Hogwarts); spears; song only sounds right underwater | hexes ✗; leaves water ✗; song unused | ✗ |
| moke | XXX | FB | silver-green lizard that **shrinks** at will; skin for moneybags | camouflage ✗ | ✗ |
| mooncalf | XX | FB | shy; emerges only at full moon; dances on hind legs, leaving crop patterns; silvery dung | ✓; crop patterns missing | ~ |
| murtlap | XXX | FB | rat with an anemone growth; pickled growth resists curses | heal aura design | ~ |
| niffler | XXX | FB, F1 | burrower drawn to glitter; deep lairs; litters of 6–8; pouch (F1) | ✓ core; litters and burrows design | ~ |
| nogtail | XXX | FB | piglet-demon that blights farms; only a white dog drives it off | `blink_away` design; white-dog remedy missing | ~ |
| norwegian_ridgeback | XXXXX | FB, HP1 | **black**, jet ridges, venomous fangs | tan ✗ | ✗ |
| nundu | XXXXX | FB | giant East African leopard; disease breath; needs a hundred wizards | grazing ✗ (clip) | ~ |
| obscurus | — | F1 | parasitic force of a suppressed Obscurial | free-roaming mob design | ~ |
| occamy | XXXX | FB, F1 | plumed, winged serpent (owner: bird head, no legs); choranaptyxic; pure-silver eggs | resize ✓; eggs missing | ~ |
| peruvian_vipertooth | XXXXX | FB | smallest, fastest flier; copper scales; venom; eats people | fast only on ground | ~ |
| phoenix | XXXX | FB, HP2 | rebirth (old age); healing tears; courage song; carries heavy loads; non-violent | lethal-only rebirth design | ~ |
| plimpy | XXX | FB | spherical lake fish on legs; eats snails | glow ✗ | ✗ |
| pogrebin | XXX | FB | Russian demon that shadows people, brings despair, then attacks | ✓ | ✓ |
| porlock | XX | FB | small horse-guardian, shy of people | heal aura ✗, blink ✗ | ✗ |
| puffskein | XX | FB | custard-coloured, hums; long tongue | glow ✗ | ✗ |
| pukwudgie | — | WW | Ilvermorny; poisoned arrows; contemptuous of people | heal aura ✗; hex instead of arrows | ✗ |
| pygmy_puff | — | HP6 | WWW miniature puffskein, pink or purple | glow ✗ | ✗ |
| qilin | — | F3 | sees into the soul; bows to the worthy leader | heal and blink ✗; bow missing | ✗ |
| quintaped | XXXXX | FB | five-legged human-eater of Drear | ✓ | ✓ |
| ramora | XX | FB | silver fish that anchors ships | ✓ | ✓ |
| red_cap | XXX | FB | dwarf-like, haunts battlefields; repelled by charms | charm weakness missing | ~ |
| reem | XXXX | FB | golden ox; blood gives immense strength | heal aura ✗; blood missing | ✗ |
| romanian_longhorn | XXXXX | FB | dark green, glittering golden horns; horn powder potion ingredient | horn missing | ~ |
| rougarou | — | WW | dog-headed swamp monster; hair a wand core | camouflage design | ✗ (design dominates thin canon) |
| runespoor | XXXX | FB | three heads: left planner, middle dreamer, right critic (venomous) | roles ✗ | ✗ |
| salamander | XXX | FB | lives as long as its fire burns; blood restorative | ✓ | ✓ |
| sea_serpent | XXX | FB | up to 100 ft; no recorded attack on a person | constrict ✗ | ✗ |
| shrake | XXX | FB | spiny Atlantic fish | ✓ | ✓ |
| snallygaster | XXXX | FB17 | Maryland bird-dragon; bulletproof; serrated steel teeth | hex ✗ | ✗ |
| sphinx | XXXX | FB | human head, lion body; riddles; guards | riddles ✓ (C-11); heal and dread ✗ | ✗ |
| streeler | XXX | FB | giant African snail; colour shifts hourly; venomous trail | ✓; swamp habitat design | ~ |
| swedish_short_snout | XXXXX | FB | silvery-blue; blue flame ashes timber and bone | ✓ | ✓ |
| swooping_evil | — | F1 | butterfly-like; venom erases memories | `life_leech` design | ~ |
| tebo | XXXX | FB | ash-grey Congo warthog; invisibility; hide prized for shields | ✓ (hide drop missing) | ✓ |
| thestral | XXXX | FB, HP5 | skeletal winged horse; seen only by those who saw death; carnivore drawn to blood | ✓; blood attraction missing | ~ |
| thunderbird | XXXX | FB17, F1 | senses danger; makes storms | ✓ | ✓ |
| toad | — | HP | ordinary (basilisk ritual) | never spawns | ✓ |
| troll | XXXX | FB, HP1 | up to 12 ft; mountain, forest and river types; strong and stupid | `spell_resist` design; types missing | ~ |
| ukrainian_ironbelly | XXXXX | FB, HP7 | **largest** breed; metal-grey; red eyes | same size as Horntail ✗ | ✗ |
| unicorn | XXXX | FB, HP1 | foals gold → silver → white; blood curses the drinker; prefers witches; swift | blink design; foals missing | ~ |
| wampus_cat | — | WW | Appalachian cat; hair a wand core | ✓ (thin canon) | ~ |
| werewolf | XXXXX | FB, HP3 | transforms at full moon; hunts humans almost exclusively | pack tactics design | ~ |
| yeti | XXXX | FB | Tibetan, 15 ft, white; fears fire | `spore_cloud` ✗; fire fear missing | ✗ |
| zouwu | — | F2 | huge Chinese cat; travels vast distances; tamed by a toy | blink ✓ (design for distance); toy missing | ~ |
| dementor | — | HP3 | blind; feeds on happiness; Patronus; chocolate | feeds on animals ✗ | ✗ |
| chinese_fireball | XXXXX | FB | scarlet, golden spike fringe; mushroom-shaped flame; tolerates its own kind | BURST ✓; orange skin, spikes, rivalry missing | ✓ |
| cornish_pixie | XXX | FB, HP2 | electric blue, shrill; mischievous; lifts people by the ears; wrecks rooms | snatch and lift ✓; room wrecking missing | ~ |

**Corrections the plan makes to data:** BR-7 lists every ✗ with its fix. Two are owner calls, because they remove abilities players may know:
- unicorn `blink_away`;
- bioluminescence on 9 species.

---

## 4. Proposed shared architecture

The principle is **extend the existing layers; add no parallel ones.** Every item below lands in a package that already exists, and there is no second AI layer (BIBLE rule).

### 4.1 Definition sync (fixes D1)
`CreatureDefinitionSyncPayload` (S2C) is sent on login and after a datapack reload, mirroring the broom and spell `*DefinitionSyncEvents`. The client fills `CreatureDefinitionRegistry` before entities load. Payload: id plus the client-relevant fields (clips, idle list, variants, render hints).

Test: a GameTest asserts that a dedicated-side encode → decode round-trip yields the same clip list. Separately, a two-client smoke run checks a hit clip in the hidden-desktop client against a `runServer`.

### 4.2 Activity state (one synced byte)
A `CreatureActivity` enum lives in `creature/profile/`:

`IDLE WALK RUN SNEAK REST SLEEP EAT DRINK GROOM ALERT THREATEN FLEE SWIM FLY GLIDE HOVER TAKEOFF LAND PERCH BURROWED CLIMB`

- **Storage:** one `EntityDataAccessor<Byte>` on `GenericBeastEntity`, and a `HasActivity` interface for the bespoke classes.
- **Who writes it:** the server, from goals.
- **Client side:** `AnimHelper.activityController` replaces the binary `movementController`.
  - It picks a loop through a fallback chain (`run→walk`, `sleep→rest→idle`, `glide→fly`, `perch→idle`, `swim(land)→walk`).
  - Speed decides walk versus run.
- Existing rigs keep working with zero new clips, and new clips light up as they are authored.
- Activity is not saved; it is derived again on load.

### 4.3 Locomotion vocabulary
- **Run:** `CreatureActivity.RUN` when the navigation speed modifier is above 1.2 (panic, chase, charge).
- **Fliers:** see §4.6.
- **Aquatic** (in `GenericAquaticBeastEntity`):
  - preferred depth band from data;
  - surfacing to breathe or bask;
  - `CreatureIdleGoal` allowed in water, using the water idles (drift, roll);
  - the `AMPHIBIOUS` trait gets its consumer: `AmphibiousMoveControl`, generalised from the Kelpie.
  - Merfolk and grindylows never leave water.
- **Burrow and climb:** they become activities with real goals only for species that need them: mooncalf burrow (D10), niffler den, bowtruckle climb (already exists). No general climbing system (BIBLE rule).

### 4.4 Daily life inside the ecology brain
`creature_ecology/<id>.json` gains optional blocks. The new intents are added to `EcologyBrain.decide` in its existing priority order: stimulus > danger > needs > social > idle.

```json
"rhythm":  "diurnal | nocturnal | crepuscular | lunar | cathemeral",
"diet":    { "kind": "grazer|browser|carnivore|insectivore|scavenger|magivore|omnivore",
             "blocks": "#...", "items": "#...", "prey": "#..." },
"needs":   { "hunger_ticks": 24000, "thirst_ticks": 12000 },
"habitat": { "prefers": "#...", "water": "none|shore|shallow|deep", "canopy": true, "sky": "open|covered",
             "shelter_in_rain": true },
"extends": "archetype/<name>"
```

- **New intents:** SLEEP (rhythm-off period, in a sheltered or habitat spot), FORAGE (graze a grass block, browse leaves, pick up a food item, hunt by diet), DRINK (walk to the shore), GROOM (calm, sated), SHELTER (rain or thunder for species that set it), PERCH (fliers), RETURN_TO_HABITAT (out of the preferred band).
- **Needs** are two counters advanced on the SLOW tier: no per-tick cost and no sync.
- **Archetypes** (`creature_ecology/_archetypes/`) give the 80 species without a profile sensible defaults with one line each. Example archetypes: `small_pest`, `grazer_herd`, `solitary_predator`, `aquatic_ambusher`, `winged_grazer`, `dragon`, `arboreal`, `household_creature`, `dark_creature`.
- **Persisted:** home, fed-until and rhythm phase get a serializer (fixes D22).
- **Weather and time** are read in `think()` from the level (`isRaining`, `isThundering`, day time and moon phase). This is cheap and needs no stimulus plumbing.

### 4.5 Stimuli that are missing
- **PLAYER:** raised from the existing `perceive` pass when a player sprints within the hearing radius, or sneaks within the sight radius. No new scan.
- **NOISE** (new kind): a NeoForge `VanillaGameEvent` listener for block destroy, explosion, projectile land, bell, note block, and a player sprinting or jumping. It raises a stimulus to profiled creatures within the event's radius. It is rate-limited per chunk and per second. This gives "reacts to sounds" without a per-tick scan.
- **FIRE, LIGHT and DARKNESS:** sampled at the creature's position on the MEDIUM tier (one light read). FIRE comes from `isOnFire` or lava contact, and from fire blocks seen during `perceive` (cap 1 sample).
- **BLOOD:** a hurt living entity raises it within 24 blocks for species with `blood_drawn`. This covers the thestral (canon), dementors excluded.

### 4.6 One flight framework
`entity/flight/WingedWalker*` (hippogriff and thestral) is generalised so every large generic flier uses it, dragons included:
- walks on the ground;
- takes off on a reason (wander, flee, hunt, target);
- cruises at a data altitude band;
- glides on the way down;
- lands on solid ground and never on treetops unless the species perches;
- perches on high blocks (griffin, thunderbird, augurey).

Rules:
- **Saved:** `flying`.
- **On load,** NoGravity is cleared when not flying (D5).
- **Fliers take no fall damage** (D7).
- **Combat:** flying melee uses the direct-flight sweep instead of 3D path search (D24).
- **Small fliers** (billywig, pixie, snidget, doxy, fairy) keep hover-flight.
- `FlightBeats` triggers takeoff from the goal start, not from vertical speed (D13).

### 4.7 Combat identity
- **Combat goal:** `CreatureCombatGoal` (in `entity/creature/ai/`) reads the already-parsed `CombatProfile` (style, windup with anticipation clip, recovery, preferred range, retreat at health). It replaces `MeleeAttackGoal` only for species that declare a profile; the rest keep vanilla.
- **Targets come from data:** `targets: { players: bool, prey: "#tag", rivals: "#tag" }`. HOSTILE no longer means players only.
- **Ranged attacks** become their own actions:
  - centaur and pukwudgie arrows;
  - giant boulders;
  - lobalug venom spit;
  - merfolk spears.
- **Clean-up:** `enrage` stays, but it stops counting as identity.

### 4.8 Habitat, spawning, reachability
- **Time of day:** use `level.isBrightOutside()` (or the sky-darkened `getMaxLocalRawBrightness`) plus a sky check (D2).
- **Categories:**
  - hostile species → `MONSTER` with `notInPeaceful`;
  - aquatic species → `WATER_CREATURE` / `UNDERGROUND_WATER_CREATURE`.
  - The category belongs to the entity type, not to save data, so existing entities load unchanged.
  - Persistent bonded creatures are unaffected.
- **Generated spawn files:** the ecology `habitat` block generates `biome_modifier/spawn_*.json` through datagen. No more hand drift.
- **Roster gate:** `AlphaRoster` becomes a quality gate. A species leaves `ALPHA_ONLY` only when it passes the §6 QA checklist, so the roster grows wave by wave.
- **Habitat sites** for creatures that shouldn't roam freely, as small structure sets:
  - dragon reserves (one per breed region);
  - merfolk lake village;
  - centaur glade;
  - acromantula hollow;
  - an Azkaban seeded with Dementors.
- **Admin:** the creature panel shows the effective state (roster gate × rule) (D20).

### 4.9 Study and harvest for hostile species
`BestiaryDiscoveryHandler` counts a creature as "watched" while the player stays out of the creature's aggro range, or out of its line of sight (hidden watching), not only while it isn't targeting.

STUDIED also accepts one of these:
- a successful Bestiary *Revelio*-style study act;
- surviving an encounter;
- tracking signs (droppings, shed scales, webs) via `CreatureSign`.

This makes rougarou hair, troll whisker and acromantula venom reachable without breaking C-harvest rulings: a kill still never pays more than a gift.

### 4.10 Young, eggs, variants
- **Young:** a generic `juvenile` stage via the existing `SCALE` mechanism, plus optional age coats (unicorn gold → silver → white), synced as a variant index.
- **Eggs:** egg-layers get an egg block or item with a hatch timer, reusing `CompanionBreeding` where bonding applies:
  - dragons (Class A Non-Tradeable);
  - occamy silver eggs;
  - acromantula sacs;
  - runespoor eggs (from the planner);
  - ashwinder eggs;
  - doxy and fairy eggs;
  - hippocampus eggs.
- **Variants:** a generic `variants` list in the creature JSON (texture suffix + weight). The renderer picks by a synced index, and `CreatureRulesService` weights keep working.

### 4.11 Sound
- **Profiles:** `SoundProfile` is filled for every species.
- **Sound with clips:** voice clips (call, hiss, howl, song) carry their sound through the server action trigger, so an animation never plays mute.
- **Delivery in two tiers:**
  - (a) Tier "remap": curated vanilla samples, pitched and layered, for all species at once.
  - (b) Tier "own": self-made, license-free audio for P1 heroes, per the 11-04 owner decision. The tool is decided in BR-6.
- **Unused events:** merfolk song and skrewt blast get wired.

### 4.12 Performance budget
Rules for all new work:
- one query per look;
- needs on the SLOW tier;
- no per-tick path calls;
- a GameTest metric budget per wave (existing pattern).

Specific fixes are D23–D27.

**Target:** 40 mixed creatures within 48 blocks add under 2 ms per server tick. The `PerformanceMetrics` creature section is the measure.

---

## 5. Species roadmap

### 5.1 Hero and alpha (P1)
| Species | BR-0 fix | Behaviour target | Look target | Voice |
|---|---|---|---|---|
| Niffler | D6, D9, pocket render | den burrow, sleep by day, forage glitter, indoor wrecking | rig rebuild (bill, paws, pouch) | own |
| Bowtruckle | D6, D11 | woodlice diet, calm with woodlice | face | own |
| Phoenix | D27, song allies | age Burning Day | body rig | own |
| Mooncalf | D10, D26 | crop patterns, real burrow | burrow mound | own |
| Thestral | D5 | blood attraction, rider destination | reptilian head | own |
| Unicorn | — | gallop flee instead of blink, foals | full rebuild, shimmer glowmask | own |
| Hippogriff | D5, D13 | hunting diet, dive | approved rebuild | own |
| Ghoul | — | attic and shelter habitat, night noises | — | own |
| Obscurus | — | owner decision | form rebuild | own |
| Werewolf | — | human-preferring, fade despawn | outline | own howl |
| Basilisk | D29 | spiders flee, gaze reaches mobs | fangs, eyes | own |
| Occamy | — | silver-egg nest guarding, suffocation guard | feathered rig | own |
| Dragons ×10 | D7, D8, D23, D24 | flight framework, perch and sleep, reserves, eggs, per-breed traits | reference sheet (5 wyverns, 5 four-legged) | own roars |
| Demiguise | — | alpha-fade invisibility, Far-East habitat | rebuild | remap |
| Rougarou | — | §4.9 study route | silhouette check | remap |

### 5.2 Iconic (P2)
| Species | Work |
|---|---|
| Dementor | D25, seed Azkaban, no animal feeding |
| Acromantula | D15, egg sacs, basilisk fear, release from gate |
| Kelpie | Float fix, shore lure, weed mane, hair |
| Cornish Pixie | D12, room wrecking |
| Augurey | D16, bramble roost, feather |
| Hidebehind | D3, hide behind trunks, clips |
| Runespoor | D4, canon heads, eggs, clips |
| Streeler | D14, savanna spawn, shell drop |
| Centaur | arrows, glade, no graze, rebuild |
| Merperson / Grindylow | lake habitat, stay in water, song, spears |
| Griffin / Thunderbird | mountain perch spawn; thunderbird rig (D8 in VR) |
| Erumpent, Kneazle, Zouwu | habitat spawns; zouwu toy taming; erumpent mating charge |
| Golden Snidget, Granian, Horned Serpent, Lobalug, Matagot, Pukwudgie, Yeti, Bundimun | leave the gate after the QA checklist; fix placement (D18) and lore ✗ |

### 5.3 Long tail (P3) by family
| Family | Members | Shared work |
|---|---|---|
| Household pests | gnome, doxy, bundimun, chizpurfle, knarl, imp, puffskein, pygmy_puff, flobberworm, horklump, fairy | archetype `household_creature`/`small_pest`; garden and house habitat; canon interactions (de-gnoming, Doxycide, chizpurfle eats wand charge, knarl wrecks fed gardens); remove ✗ glows |
| Winged horses | abraxan, aethonan, granian | flight framework; three distinct silhouettes (abraxan huge palomino, granian lean grey); whisky diet; ridable (design) |
| Water | hippocampus, sea_serpent, plimpy, ramora, shrake, lobalug, kappa, horned_serpent | aquatic depth bands, ocean and lake habitats; kappa bow weakness; sea serpent harmless; lobalug spit; serpent rigs lengthened |
| Dark creatures | lethifold, boggart, pogrebin, erkling, red_cap, nogtail, hidebehind | night rhythm; Patronus-only lethifold; erkling child-lure; red cap charm weakness; nogtail white-dog remedy |
| Great beasts | nundu, manticore, chimaera, graphorn, quintaped, reem, tebo, troll, giant, yeti, sphinx | combat profiles; no graze; giant size and boulders; troll types; yeti fire fear; reem blood; tebo hide |
| Small magical | billywig, jobberknoll, fwooper, diricawl, clabbert, mackled_malaclaw, murtlap, moke, salamander, fire_crab, ashwinder, crup, kneazle, jarvey | each gets one canon interaction (moke shrink, malaclaw bad luck, jarvey hunts gnomes, ashwinder eggs, fire crab shell) |
| American (FB17/WW) | hodag, snallygaster, wampus_cat, pukwudgie, rougarou, horned_serpent, thunderbird | hodag hunts mooncalves; snallygaster bulletproof; pukwudgie arrows |
| Film beings | maledictus, qilin, matagot, zouwu, swooping_evil, obscurus | qilin bows to the worthy; swooping-evil venom memory effect; owner decision on non-animals |

---

## 6. Milestones

Each milestone ends with tests green, the matrix script re-run, and a short review in `tasks/todo_beast_renaissance_*.md`. Milestones that change looks follow the owner's rule: one example plus side-by-side screenshots, approval, then rollout.

**QA checklist** (Phase 5), applied per species when its milestone touches it:
- [ ] Goal priorities: a higher goal interrupts a lower one, and nothing holds MOVE forever (GameTest).
- [ ] Navigation: reaches a target 16 blocks off on its terrain type; no stuck timeout.
- [ ] Persistence: save → reload keeps owner, bond, home, flying, variant, age (NBT round-trip GameTest).
- [ ] Multiplayer: clips and activity visible on a client of a dedicated server (smoke on the hidden-desktop client against `runServer`).
- [ ] Animation: each declared clip reachable; a one-shot interrupts its loop and hands back.
- [ ] Players: interactions by owner, stranger and creative; Peaceful behaviour.
- [ ] Other creatures: prey, predator and rival scenarios in `CreatureArena` with `addAlone` and force-loaded reach.
- [ ] Environment: day, night, rain, water and shelter scenario where relevant.
- [ ] Performance: 20 of the species in a metric-budget GameTest.

| Milestone | Scope | Exit |
|---|---|---|
| **BR-0 Correctness** | D1–D30 except looks; tests for each S1/S2 | multiplayer clips work; 13 → same spawn set but by real time; no S1 open |
| **BR-1 Activity + locomotion** | §4.1–4.3, §4.6; dragons, winged horses, griffin and thunderbird on the flight framework | every creature reports an activity; fliers walk, take off, land and perch; no fall deaths; aquatics idle in water |
| **BR-2 Daily life** | §4.4–4.5; archetypes for all 107; sleep, forage, drink and shelter; NOISE, PLAYER, LIGHT and BLOOD stimuli | every species has a profile; a 1-day soak shows sleep, eat, drink and rain behaviour; perf budget held |
| **BR-3 Habitat + reachability** | §4.8–4.9; categories; habitat datagen; gate releases for P2 that pass QA; reserves and lake as structures; study route | every P1 and P2 species can be met in survival; harvest materials reachable |
| **BR-4 Combat identity** | §4.7; profiles for troll, nundu, dugbog, basilisk, acromantula (bible list) plus dragons and centaur; ranged actions | each profiled species fights differently in a duel test |
| **BR-5 Hero looks** | in order: Hippogriff (approved), Centaur, Unicorn, dragons per sheet, Niffler, Occamy, Mooncalf, Kelpie, Demiguise, Thunderbird; new clips: run, sleep, eat, takeoff and land where the rig lacks them | owner approval per creature; VR-0 grade ≥ B |
| **BR-6 Voice** | §4.11 tier remap for all; own audio for P1 | 0 silent P1 and P2 species; no mute voice clip |
| **BR-7 Lore pass** | every ✗ in §3 fixed or ruled; young and eggs (§4.10); canon interactions per family | lore matrix ≤ 5 ✗, each with an owner ruling |
| **BR-8 Long tail** | P3 families to B behaviour and B visual, wave by wave | roster average B |

**Status 2026-11-06 — BR-2 delivered** on `feat/creature-ai` (`tasks/todo_creature_ai_2026_11_05.md`), together with
BR-1 §4.2 (the activity byte) pulled forward; the rest of BR-1 (§4.1 done in BR-0, §4.3, §4.6 flight) is still open.
What landed, by §4.4–4.5: rhythm as capabilities (`diurnal`, `crepuscular`, `nocturnal`), diet as capabilities
(`grazer`, `browser`, `insectivore`, plus `scavenger`/`predator`), needs (seven, declared per species), weather and
climate (`weather-shy`, `cold-adapted`, `warmth-loving`), archetypes with `extends` (13), a profile for all 106 roster
creatures, NOISE and PLAYER stimuli from vanilla game events, persistence (D22). Beyond the plan: commitment and
hysteresis (`EcologyCommitment`), navigation timeouts and failure backoff, individual personality, carrion for
scavengers. Not done from §4.4: `RETURN_TO_HABITAT`, `PERCH` (flight framework), a LIGHT/BLOOD stimulus.
Reference: `CREATURE_ECOLOGY.md` §2–§12.

BR-0 and BR-1 need no owner decision. BR-3, BR-5, BR-6 and BR-7 each start with the questions in §8.

---

## 7. Files expected to change

| Milestone | Files |
|---|---|
| BR-0 | `entity/creature/GenericBeastEntity.java`, `creature/CreatureDefinitionLoader.java` (+ new `creature/CreatureDefinitionSync*.java`, `network/` payload registration), `entity/beast/BeastSpawnHandler.java`, `event/bestiary/niffler/NifflerSpawnHandler.java`, `entity/beast/HidebehindEntity.java`, `entity/beast/RunespoorEntity.java` + `RunespoorHeadPart.java`, `entity/flight/WingedWalkerMoveControl.java` + `WingedWalkerFlightGoal.java` + `FlightBeats.java`, `entity/creature/GenericFlyingBeastEntity.java`, `creature/bond/BondableBeast.java`, `entity/niffler/NifflerEntity.java` + `BabyNifflerEntity.java` + `client/.../NifflerPocketLayer.java`, `data/.../creature_bonds/niffler.json`, `entity/beast/MooncalfEntity.java`, `entity/beast/BowtruckleEntity.java` + `BowtruckleLockpickGoal.java`, `entity/beast/CornishPixieEntity.java` (+ carried-item layer in `client/entity/`), `entity/beast/StreelerEntity.java`, `entity/beast/AugureyEntity.java`, `entity/creature/AcromantulaWebs.java` + `block/AcromantulaWebBlock.java`, `entity/creature/ai/DragonBreathGoal.java`, `entity/azkaban/DementorEntity.java` + `DementorAura.java`, `heritage/.../VeelaHeritageHandler.java`, `registry/ModCreatures.java` (categories), `admin/creature/CreatureAdminService.java`, `entity/beast/PhoenixDefendOwnerGoal.java`, `client/entity/TintedBeastRenderer.java`, `DragonRenderer.java`; tests in `src/test/.../creature/` + `gametest/Creature*Tests.java` |
| BR-1 | new `creature/profile/CreatureActivity.java`, `HasActivity.java`; `entity/AnimHelper` (activity controller); all four `Generic*BeastEntity`; `entity/flight/*` generalised; `DragonEntity.java`, `HippogriffEntity.java`, `ThestralEntity.java`; `entity/creature/ai/CreatureIdleGoal.java`, `DirectFlightWanderGoal.java`; `AmphibiousMoveControl.java`; `creature/Trait.java` (AMPHIBIOUS consumer) |
| BR-2 | `creature/ecology/EcologyBrain.java`, `EcologyGoal.java`, `EcologySensing.java`, `EcologyProfile*.java`, `CreatureStimuli.java` (+ NOISE listener in `event/creature/`), `registry/ModAttachments.java` (ecology serializer); new `data/.../creature_ecology/_archetypes/*.json` + one profile per species (80 new files); `EcologyFieldNotes` |
| BR-3 | `BeastSpawnHandler`, `creature/AlphaRoster.java`, `Config.java`, `creature/rules/*`, `datagen/` (habitat → biome modifiers), `data/.../neoforge/biome_modifier/*`, new reserve/lake/glade structures under `data/.../structure/` + `worldgen/`, `bestiary/BestiaryDiscoveryHandler.java`, `bestiary/EncounterRule.java` |
| BR-4 | new `entity/creature/ai/CreatureCombatGoal.java`; `creature/profile/CombatProfile.java`; `creature/ability/RangedHex.java` → per-species ranged actions (arrow, boulder, spit, spear); creature JSONs |
| BR-5 | `tools/<id>_model.py` (local repo), `tools/dragon_model.py`; `assets/.../geckolib/models|animations/entity/*`, `textures/entity/*`, glowmasks; `tools/bestiary_portraits.py` + `spawn_eggs.py` outputs; renderers for translucency |
| BR-6 | `assets/.../sounds.json`, `assets/.../sounds/entity/**.ogg` (new), `registry/ModSounds.java`, creature JSON `behaviour.sounds` |
| BR-7 | creature JSONs (ability corrections), `creature/ability/*` (new canon interactions), new items/blocks for eggs and parts (`registry/ModItems`, `ModBlocks`), loot tables, harvest rules, bestiary entries, `CANON_AUDIT.md` rulings |
| Docs every wave | this file, `CREATURE_QUALITY_MATRIX.md` (+ regenerated `_DATA.md`), `ENTITY_DESIGN_BIBLE.md` stale lines, `KNOWN_ISSUES.md`, `tools/creature_quality_matrix.py` (`BESPOKE` list, Habitat must respect the roster gate) |

---

## 8. Owner decisions needed

1. **Roster gate.** Release species from `ALPHA_ONLY` wave by wave as they pass QA (recommended)? Or switch the default to ALL now?
2. **Dragons in the world.** Reserves as structures only (recommended: canon keeps dragons in reserves, and it limits breath damage)? Or wild spawns per breed region?
3. **Peaceful.** Should hostile beasts become `MONSTER` and vanish on Peaceful (recommended)?
4. **Removing abilities** that contradict canon: unicorn blink, glows on 9 species, the heal auras, sea serpent constrict. Remove them (recommended), or keep them tagged `[design]`?
5. **Non-animals.** Should Obscurus and Maledictus stay free-roaming mobs, or move to heritage and encounters only?
6. **Visual order** for BR-5: is the proposed order right? The Hippogriff rebuild is already approved.
7. **Audio tool** for self-made sound (BR-6).
