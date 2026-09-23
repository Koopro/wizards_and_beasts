# Wizards & Beasts Entity Bible — Audit

**Scope:** every registered `EntityType` in the repository, audited 2026-09-23 against branch
`dev-2026-09-17-rework` (Minecraft 1.21.11, NeoForge, Java 21).

**This document changed no code, data, model, texture or animation.** It is the inventory that a
later improvement pass is meant to work from. Where something is a design decision rather than an
established fact, it says so.

**Method.** The roster was extracted from both registration paths rather than from one registry
class, then cross-referenced against the GeckoLib rigs, animation clips, entity textures, creature
definitions, loot tables, biome modifiers and Bestiary entries on disk. The raw output is in
`build/entity-audit/roster.json`; the per-entity table below is generated from it. Judgements that
are genuinely per-creature — anatomy, palette, whether a silhouette reads — are called out
individually in the analysis sections and are *not* claimed for all 120 entities, because nobody
looked at 120 silhouettes.

---

## Summary

| | |
|---|---|
| **Total entities** | **120** |
| Bespoke Java entities (`ModEntities`) | 24 |
| Data-driven creatures (`ModCreatures.MANIFEST`) | 96 |
| Magical wildlife | 70 |
| Dangerous creatures | 29 |
| Intelligent magical beings | 8 |
| Projectiles / magical effects | 5 |
| Other / utility (broom, mannequin, dummy, frog) | 4 |
| Magical manifestations (Patronus, Protego shield) | 2 |
| Supernatural entities (Dementor) | 1 |
| NPC (Gringotts teller) | 1 |

Two registration paths, and they matter for everything below:

- **`ModEntities`** — 24 hand-written classes, each with its own AI, renderer and often its own
  sounds and mechanics. The Niffler, Bowtruckle, Mooncalf, Thestral, Phoenix, Dementor and the
  non-creature entities live here.
- **`ModCreatures.MANIFEST`** — 96 creatures sharing `GenericBeastEntity` and its three locomotion
  subclasses, configured entirely from `data/wizards_and_beasts/creatures/<id>.json`.

The single most useful fact in this audit is that **80% of the roster is one class**. Almost every
finding below is therefore a finding about `GenericBeastEntity`, not about ninety-six creatures.

---

## Entity inventory

Generated from `build/entity-audit/roster.json`. Columns: class, category, hitbox, model, texture
resolution, animation clips, habitat, and Bestiary/visual state.

| ID | Class | Category | Hitbox | Model | Texture | Clips | Habitat | Bestiary / State |
|---|---|---|---|---|---|---|---|---|
| `acromantula` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x88 | bite,hiss,idle,walk | — | Y / Underdeveloped |
| `basilisk` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo+glow | 128x160 | gaze,hiss,idle,strike,walk | — | Y / Complete |
| `chimaera` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x64 | bite,howl,idle,walk | — | Y / Underdeveloped |
| `chinese_fireball` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo+glow | 128x144 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `dugbog` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo | 128x48 | bite,hiss,idle,walk | — | Y / Underdeveloped |
| `erkling` | GenericBeastEntity | Dangerous creature | 0.65×0.75 | geo | 64x24 | attack,call,idle,walk | — | Y / Underdeveloped |
| `giant` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo | 128x136 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `graphorn` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo | 128x88 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `hebridean_black` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo+glow | 128x152 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `hungarian_horntail` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo+glow | 128x128 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `kappa` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo+glow | 64x48 | attack,flinch,idle,swim | — | Y / Underdeveloped |
| `lethifold` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo | 64x32 | attack,flinch,idle,walk | — | Y / Underdeveloped |
| `manticore` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x96 | hiss,idle,strike,walk | — | Y / Underdeveloped |
| `norwegian_ridgeback` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo | 128x152 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `nundu` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo+glow | 128x240 | bite,howl,idle,walk | — | Y / Underdeveloped |
| `obscurus` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo+glow | 128x128 | fly,idle | — | Y / Underdeveloped |
| `occamy` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo | 128x128 | attack,idle,walk | — | Y / Underdeveloped |
| `peruvian_vipertooth` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x112 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `pogrebin` | GenericBeastEntity | Dangerous creature | 0.65×0.75 | geo+glow | 64x32 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `pukwudgie` | GenericBeastEntity | Dangerous creature | 0.5×1.0 | geo | 64x32 | attack,call,idle,walk | minecraft:swamp, minecraft:mangr w4 | Y / Underdeveloped |
| `quintaped` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo+glow | 128x64 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `red_cap` | GenericBeastEntity | Dangerous creature | 0.65×0.75 | geo+glow | 64x40 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `rougarou` | GenericBeastEntity | Dangerous creature | 1.2×1.9 | geo+glow | 128x48 | attack,howl,idle,walk | minecraft:swamp, minecraft:mangr w4 | Y / Underdeveloped |
| `swooping_evil` | GenericBeastEntity | Dangerous creature | 0.95×1.25 | geo+glow | 128x32 | fly,hiss,idle,lunge | — | Y / Underdeveloped |
| `troll` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo | 128x152 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `ukrainian_ironbelly` | GenericBeastEntity | Dangerous creature | 2.7×2.9 | geo | 128x200 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `wampus_cat` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x64 | hiss,idle,lunge,walk | — | Y / Underdeveloped |
| `werewolf` | GenericBeastEntity | Dangerous creature | 1.7×1.9 | geo+glow | 128x32 | attack,hit,howl,idle,walk | minecraft:forest, minecraft:dark w2 | Y / Complete |
| `yeti` | GenericBeastEntity | Dangerous creature | 1.7×2.3 | geo+glow | 128x80 | attack,howl,idle,walk | minecraft:snowy_slopes, minecraf w3 | Y / Underdeveloped |
| `clabbert` | GenericBeastEntity | Intelligent magical being | 0.65×0.75 | geo+glow | 64x32 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `demiguise` | GenericBeastEntity | Intelligent magical being | 0.65×0.75 | geo | 64x40 | call,flinch,idle,walk | minecraft:bamboo_jungle, minecra w1 | Y / Underdeveloped |
| `ghoul` | GenericBeastEntity | Intelligent magical being | 0.7×1.9 | geo+glow | 64x64 | groan,idle,walk | #minecraft:is_forest w4 | Y / Underdeveloped |
| `gnome` | GenericBeastEntity | Intelligent magical being | 0.65×0.75 | geo | 64x32 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `imp` | GenericBeastEntity | Intelligent magical being | 0.45×0.45 | geo+glow | 64x16 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `leprechaun` | GenericBeastEntity | Intelligent magical being | 0.65×0.75 | geo+glow | 64x32 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `maledictus` | GenericBeastEntity | Intelligent magical being | 0.95×1.25 | geo+glow | 64x72 | hiss,idle,strike,walk | — | Y / Underdeveloped |
| `porlock` | GenericBeastEntity | Intelligent magical being | 0.65×0.75 | geo | 64x40 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `patronus` | PatronusEntity | Magical manifestation | 0.6×1.2 | — | — | — | — | **no** / Complete |
| `protego_shield` | ProtegoShieldEntity | Magical manifestation | 0.5×0.5 | geo | 64x64 | deflect,fade,horribilis_absorb,idle_disc,idle_dome,shatter | — | **no** / Complete |
| `abraxan` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo | 128x168 | call,fly,idle,strike | — | Y / Underdeveloped |
| `aethonan` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x72 | call,fly,idle,strike | — | Y / Underdeveloped |
| `antipodean_opaleye` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo+glow | 128x128 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `ashwinder` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo+glow | 64x16 | hiss,idle,strike,walk | — | Y / Underdeveloped |
| `augurey` | AugureyEntity | Magical wildlife | 0.5×0.7 | geo | 32x32 | fly,idle | #minecraft:is_forest w5 | Y / Complete |
| `baby_niffler` | BabyNifflerEntity | Magical wildlife | 0.2×0.25 | — | — | — | — | **no** / Missing |
| `billywig` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo+glow | 64x16 | call,flinch,fly,idle | — | Y / Underdeveloped |
| `blast_ended_skrewt` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo+glow | 128x32 | hiss,idle,strike,walk | — | Y / Underdeveloped |
| `boggart` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo+glow | 64x56 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `bowtruckle` | BowtruckleEntity | Magical wildlife | 0.4×0.8 | geo | 32x32 | idle,walk | #minecraft:is_forest w8 | Y / Complete |
| `bundimun` | GenericBeastEntity | Magical wildlife | 0.5×0.4 | geo+glow | 64x16 | flinch,idle,walk | #minecraft:is_forest w3 | Y / Underdeveloped |
| `centaur` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x56 | attack,call,idle,walk | — | Y / Underdeveloped |
| `chizpurfle` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x16 | bite,flinch,idle,walk | — | Y / Underdeveloped |
| `common_welsh_green` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo | 128x144 | bite,breath,fly,idle | minecraft:windswept_hills, minec w1 | Y / Underdeveloped |
| `cornish_pixie` | CornishPixieEntity | Magical wildlife | 0.4×0.6 | geo | 32x32 | fly,idle | #minecraft:is_forest w6 | Y / Complete |
| `crup` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x40 | bite,call,idle,walk | — | Y / Underdeveloped |
| `diricawl` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x40 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `doxy` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x16 | bite,flinch,fly,idle | — | Y / Underdeveloped |
| `erumpent` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo+glow | 128x96 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `fairy` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo+glow | 64x16 | flinch,fly,idle,song | — | Y / Underdeveloped |
| `fire_crab` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo+glow | 64x32 | attack,hiss,idle,walk | — | Y / Underdeveloped |
| `flobberworm` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x8 | flinch,idle,walk | — | Y / Underdeveloped |
| `fwooper` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x40 | flinch,fly,idle,song | — | Y / Underdeveloped |
| `glumbumble` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo+glow | 64x16 | flinch,fly,groan,idle | — | Y / Underdeveloped |
| `golden_snidget` | GenericBeastEntity | Magical wildlife | 0.35×0.35 | geo+glow | 64x24 | flinch,fly,idle,song | minecraft:flower_forest, minecra w1 | Y / Underdeveloped |
| `granian` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x80 | call,fly,idle,strike | minecraft:plains, minecraft:sunf w3 | Y / Underdeveloped |
| `griffin` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x96 | call,fly,idle,strike | — | Y / Underdeveloped |
| `grindylow` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo+glow | 64x24 | attack,flinch,idle,swim | — | Y / Underdeveloped |
| `hidebehind` | HidebehindEntity | Magical wildlife | 0.9×1.9 | geo+glow | 64x40 | idle,walk | minecraft:dark_forest, minecraft w3 | Y / Complete |
| `hippocampus` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x48 | call,idle,swim | — | Y / Underdeveloped |
| `hippogriff` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x64 | attack,fly,idle,walk | — | Y / Underdeveloped |
| `hodag` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo+glow | 64x64 | bite,groan,idle,walk | — | Y / Underdeveloped |
| `horklump` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x32 | flinch,idle | — | Y / Underdeveloped |
| `horned_serpent` | GenericBeastEntity | Magical wildlife | 1.5×1.3 | geo+glow | 128x32 | bite,hiss,idle,swim | minecraft:river, minecraft:swamp w3 | Y / Underdeveloped |
| `jarvey` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x40 | bite,call,idle,walk | — | Y / Underdeveloped |
| `jobberknoll` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x24 | flinch,fly,idle | — | Y / Underdeveloped |
| `kelpie` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x48 | attack,call,idle,swim | — | Y / Underdeveloped |
| `knarl` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x40 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `kneazle` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo+glow | 64x40 | hiss,idle,lunge,walk | — | Y / Underdeveloped |
| `lobalug` | GenericBeastEntity | Magical wildlife | 0.7×0.7 | geo | 64x56 | flinch,idle,swim | #minecraft:is_ocean w3 | Y / Underdeveloped |
| `mackled_malaclaw` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x32 | bite,flinch,idle,walk | — | Y / Underdeveloped |
| `matagot` | GenericBeastEntity | Magical wildlife | 0.6×0.7 | geo+glow | 64x32 | hiss,idle,strike,walk | #minecraft:is_overworld w2 | Y / Underdeveloped |
| `merperson` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo | 64x40 | attack,idle,song,swim | — | Y / Underdeveloped |
| `moke` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x24 | flinch,hiss,idle,walk | — | Y / Underdeveloped |
| `mooncalf` | MooncalfEntity | Magical wildlife | 0.7×1.2 | geo+glow | 64x56 | dance,idle,walk | minecraft:meadow, minecraft:plai w4 | Y / Complete |
| `murtlap` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x32 | bite,flinch,idle,walk | — | Y / Underdeveloped |
| `niffler` | NifflerEntity | Magical wildlife | 0.4×0.5 | geo | 32x32 | idle,walk | #minecraft:is_badlands w3 | Y / Complete |
| `nogtail` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x32 | bite,groan,idle,walk | — | Y / Underdeveloped |
| `phoenix` | PhoenixEntity | Magical wildlife | 0.7×1.0 | geo+glow | 128x128 | fly,idle | #minecraft:is_savanna w1 | Y / Complete |
| `plimpy` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo | 64x56 | call,flinch,idle,swim | — | Y / Underdeveloped |
| `puffskein` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x48 | flinch,idle,song,walk | — | Y / Underdeveloped |
| `pygmy_puff` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x32 | flinch,idle,song,walk | — | Y / Underdeveloped |
| `qilin` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo+glow | 64x48 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `ramora` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo | 128x32 | flinch,idle,swim | — | Y / Underdeveloped |
| `reem` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo | 128x192 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `romanian_longhorn` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo | 128x144 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `runespoor` | RunespoorEntity | Magical wildlife | 0.95×1.25 | geo | 64x32 | idle,walk | #minecraft:is_savanna w2 | Y / Complete |
| `salamander` | GenericBeastEntity | Magical wildlife | 0.65×0.75 | geo+glow | 64x24 | flinch,hiss,idle,walk | — | Y / Underdeveloped |
| `sea_serpent` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo | 128x80 | bite,hiss,idle,swim | — | Y / Underdeveloped |
| `shrake` | GenericBeastEntity | Magical wildlife | 0.95×1.25 | geo | 128x32 | attack,flinch,idle,swim | — | Y / Underdeveloped |
| `snallygaster` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x72 | fly,hiss,idle,strike | — | Y / Underdeveloped |
| `sphinx` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x72 | call,idle,strike,walk | — | Y / Underdeveloped |
| `streeler` | StreelerEntity | Magical wildlife | 0.7×0.7 | geo | 64x64 | idle,walk | minecraft:swamp, minecraft:mangr w5 | Y / Complete |
| `swedish_short_snout` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo+glow | 128x136 | bite,breath,fly,idle | — | Y / Underdeveloped |
| `tebo` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo | 128x88 | attack,groan,idle,walk | — | Y / Underdeveloped |
| `thestral` | ThestralEntity | Magical wildlife | 1.4×1.8 | geo+glow | 128x128 | fly,gallop,graze,idle,screech,walk | #minecraft:is_taiga w4 | Y / Complete |
| `thunderbird` | GenericBeastEntity | Magical wildlife | 1.7×1.9 | geo+glow | 128x96 | call,fly,idle,strike | — | Y / Underdeveloped |
| `toad` | GenericBeastEntity | Magical wildlife | 0.45×0.45 | geo | 64x24 | call,flinch,idle,walk | — | Y / Underdeveloped |
| `unicorn` | GenericBeastEntity | Magical wildlife | 1.4×1.6 | geo+glow | 64x64 | call,flinch,idle,walk | minecraft:old_growth_birch_fores w1 | Y / Underdeveloped |
| `zouwu` | GenericBeastEntity | Magical wildlife | 2.7×2.9 | geo+glow | 128x184 | call,idle,lunge,walk | — | Y / Underdeveloped |
| `goblin_teller` | GoblinTellerEntity | NPC | 0.6×1.5 | geo | 64x40 | bow,idle,walk | minecraft:dripstone_caves, minec w2 | **no** / Complete |
| `broom` | BroomEntity | Other / utility | 0.8×1.0 | geo | 64x512 | boost,brake,dismount,fly_forward,hover,idle,lean_left,lean_right,mount,summon | — | **no** / Complete |
| `chocolate_frog` | ChocolateFrogEntity | Other / utility | 0.3×0.3 | — | 32x32 | — | — | **no** / Complete |
| `duelling_dummy` | DuellingDummyEntity | Other / utility | 0.6×1.95 | — | 64x64 | — | — | **no** / Complete |
| `form_mannequin` | FormMannequinEntity | Other / utility | 0.6×1.8 | — | — | — | — | **no** / Complete |
| `beam` | BeamEntity | Projectile / magical effect | 0.1×0.1 | — | — | — | — | **no** / Complete |
| `beast_hex_projectile` | BeastHexProjectile | Projectile / magical effect | 0.25×0.25 | — | — | — | — | **no** / Complete |
| `spell_clash` | SpellClashEntity | Projectile / magical effect | 0.6×0.6 | — | — | — | — | **no** / Complete |
| `spell_projectile` | SpellProjectileEntity | Projectile / magical effect | 0.25×0.25 | — | — | — | — | **no** / Complete |
| `wizarding_thrown` | WizardingThrownEntity | Projectile / magical effect | 0.25×0.25 | — | — | — | — | **no** / Complete |
| `dementor` | DementorEntity | Supernatural entity | 0.9×3.2 | geo | 256x256 | dissipate,idle_drift,kiss_resolve,kiss_windup,pursue,repelled | — | Y / Complete |

---

## Visual audit

Texture sheets are **64 or 128 pixels wide** with heights from 8 to 512 — 31 distinct sizes. That is
not inconsistency for its own sake: these are box-UV atlases, so a sheet is as tall as its rig needs.
The two widths are a real split, though, and nothing records which one a new creature should use.

**Glowmasks: 50 of 110 rigs.** `GeoRendererHelper` attaches an `AutoGlowingGeoLayer` purely on the
presence of `textures/entity/<model>_glowmask.png`, so emissive is opt-in per creature with no code
change. That is a good seam. One mask is deliberately the whole creature — the Fairy, which carries
`"glow_self": true` and lore reading "a vain, faintly glowing winged sprite".

**Visual identity, descriptive:** STRONG 24, MODERATE 34, WEAK 52, N/A 10.

WEAK here is a structural reading, not an aesthetic verdict: it marks a creature that shares a body
plan with ten or more others *and* has only the standard four animation clips. Those two together
mean it is very likely to read as "generic creature of type X" in motion. It is a list of places to
look, not a list of bad art. Whether a given silhouette actually reads was not judged for all 120 —
that needs eyes on each one, and nobody has looked at 120 silhouettes.

**Body plans:** QUADRUPED 23, WINGED_QUADRUPED 17, BIPED_HUMANOID 13, BLOB_SPHERE 9, AQUATIC 8,
ARTHROPOD_MULTILEG 6, SERPENTINE 5, INSECTOID_FLYER 5, AVIAN 5, LARGE_HUMANOID 3, WORM_LARVA 1,
SESSILE 1.

## Model audit

**Hitboxes are tiered, not per-creature.** 86 of the 96 generic creatures share five sizes:

| Hitbox | Creatures |
|---|---|
| 0.65 × 0.75 | 24 |
| 1.7 × 1.9 | 21 |
| 2.7 × 2.9 | 17 |
| 0.95 × 1.25 | 13 |
| 0.45 × 0.45 | 11 |

Only ten creatures have a bespoke box. The consequence is concrete: a Basilisk, an Abraxan winged
horse and an Antipodean Opaleye dragon all occupy exactly 2.7 × 2.9. A serpent canon describes as
fifty feet long has the same footprint as a horse.

**This is a design decision, not a defect.** Tiering keeps spawning and pathfinding predictable, and
`Attributes.SCALE` is already used elsewhere in the mod (the Occamy's choranaptyxis) to move hitbox
and model together. But it is the single largest structural reason distinct creatures feel alike at
range, and canon sets no Minecraft scale for any of them, so any change here is a design call rather
than a correction.

## Animation audit

110 rigs carry 385 clips. The distribution is the story:

| Clip | Rigs | Clip | Rigs |
|---|---|---|---|
| idle | 108 | hiss | 17 |
| walk | 67 | strike | 13 |
| flinch | 36 | groan | 13 |
| fly | 30 | swim | 11 |
| call | 28 | breath | 10 |
| bite | 24 | song | 6 |
| attack | 24 | howl | 5 |

**84 of 110 rigs have exactly four clips** — idle, a locomotion clip, an attack clip and a voice
clip. That is a deliberate, consistent standard and it is why the roster animates at all.

Two gaps follow from it:

- **74 of 110 rigs have no hurt or death clip.** Only 36 carry `flinch`.
- **No rig anywhere has a `death` clip.** Every creature in the mod dies with vanilla's rotate-and-
  sink. For a mod whose beasts are the headline content, the moment of killing one is unanimated.

Missing by design rather than omission: spawn, transformation and interaction clips exist only on the
bespoke entities that need them — Protego's four held shapes, the Dementor's kiss windup and resolve,
the broom's mount/dismount/boost/brake.

## Idle behaviour

The brief calls this out as extremely important, so this section has the clearest answer.

`GenericBeastEntity` gives every one of its 96 creatures the same idle: a locomotion-appropriate
wander at priority 5, `LookAtPlayerGoal` at 9, `RandomLookAroundGoal` at 10. That is competent, and
it is identical for all of them. **No generic creature has a species-specific idle behaviour.**

The bespoke entities do, and they are the proof the mod knows how:

| Creature | Species idle actually implemented |
|---|---|
| Bowtruckle | `TemptGoal`, `MoveTowardsRestrictionGoal` (stays near its tree) |
| Mooncalf | `GatherToDanceGoal`, `StayInBurrowGoal` |
| Thestral | `ThestralGrazeGoal` |
| Phoenix | flying wander, avoid, look — **no preening, no wing-spread** |
| Niffler | digging and carrying, in its own handler outside the goal list |

Against the brief's own examples: the Niffler does dig and investigate; the Bowtruckle stays with its
tree but does not climb, hide or peek; the Phoenix does none of preen, spread wings, or anything but
fly and look around.

## Movement

Better than expected, and worth stating as a correction to the brief's worry: **movement is properly
specialised.** `addMovementGoals()` is overridden per locomotion —

- `GenericGroundBeastEntity` → `WaterAvoidingRandomStrollGoal`, ground navigation
- `GenericFlyingBeastEntity` → `WaterAvoidingRandomFlyingGoal`, `FlyingMoveControl`, flying navigation
- `GenericAquaticBeastEntity` → `RandomSwimmingGoal`, `SmoothSwimmingMoveControl`,
  `WaterBoundPathNavigation`, `canBreatheUnderwater`

Locomotion across the roster: GROUND 58, FLYING 26, AQUATIC 11, SESSILE 1. **No aquatic creature
behaves like a zombie and no flier paths along the ground.**

What is genuinely missing is narrower: no climbing anywhere — the Bowtruckle is a tree creature that
cannot climb a tree — and the Dementor, a thing that should drift through architecture, uses ordinary
ground navigation with `SpawnPlacementTypes.ON_GROUND`.

## AI personality

Temperament is a three-value field: NEUTRAL 45, HOSTILE 29, PASSIVE 22. It decides whether a creature
gets panic and avoid goals, a melee goal, and a retaliation target.

The richer behavioural vocabulary lives in the ability layer, and it is substantial — **48 distinct
ability types across 91 of 96 creatures**: `enrage` 33, `status_on_hit` 18, `blink_away` 13, `leap`
12, `bioluminescence` 11, `water_affinity` 10, `ranged_hex` 7, `heal_aura` 7, `camouflage` 6,
`constrict` 6, `dread_aura` 6, `thorns` 6, down to one-offs like `sphinx_riddle`, `jarvey_jinx`,
`lethifold_smother`, `occamy_choranaptyxis` and `thunderbird_storm`. Fifteen traits sit alongside
(`KNOCKBACK` 22, `FIRE_IMMUNE` 15, `FEARFUL` 12, `AMPHIBIOUS` 12, `PACK` 10 …).

So the archetypes the brief asks for are expressible — **Skittish** (FEARFUL), **Pack-based** (PACK,
`pack_tactics`), **Territorial** (`enrage`), **Predatory** (HOSTILE + `leap`), **Supernatural**
(`dread_aura`, `blink_away`) — but only as combat and reaction abilities. **Curious**,
**Mischievous** and **Social** have no vocabulary outside the bespoke classes.

## Player interaction

`study` methods on Bestiary profiles: `watch` dominates, with `feed`, `bond`, `handle` and `harvest`
on a minority. Bonding exists (`BondableBeast`, `creature_bonds/`), feeding exists, harvesting is
gated on knowledge tier through `HarvestGate`, and observation drives discovery tiers.

**43 of 120 entities have a loot table; 64 of the 96 generic creatures have none.** A creature with no
loot table and no `harvest` study method is, materially, something you can only look at.

## Reaction system

Reactions that exist: damage retaliation, FEARFUL panic and avoid, `danger_sense`, `wary`,
`watched_invisibility` (the Demiguise), `moon_bound`, purity-sensitivity to Dark corruption, and
creature trust through the Magizoology skill line.

Reactions that exist nowhere in the roster: to **specific spells**, to **fire**, **water**, **light or
darkness** as such, to **another creature's death**, or to **weather**. `fire_affinity` and
`water_affinity` are movement and damage traits, not reactions to the element appearing.

## Combat

Combat for a generic creature is `MeleeAttackGoal` at 1.2× speed, or 1.45× with CHARGE, plus whatever
ability the definition attaches. Bespoke goals exist for `BreatheFireGoal`, `DeathGazeGoal` and
`RoosterCrowWeaknessGoal`.

There is **no telegraphing, no retreat behaviour, no target switching and no attack-range variation**
in the shared path. A Nundu and a Crup approach and bite in the same rhythm; what differs is the
damage number and which ability fires. The dragons are the exception, with their own breath goal.

## Spawn and habitat

25 of 120 entities spawn naturally. Placement is gated in `BeastSpawnHandler` on module, roster
setting, solid ground and a day/night light check, with two special cases: the werewolf only under a
full moon, and the Matagot only on wizarding stonework.

**83 Bestiary entries describe creatures that cannot currently appear.** That is a roadmap state
rather than a bug — but nothing in the Bestiary UI distinguishes "planned" from "encounterable", so
the book teaches a world four times larger than the one that exists.

## Sound

**Nine entity sounds exist, and all nine are the Niffler's** (`ambient`, `death`, `dig`, `eat`,
`happy`, `hiss`, `hurt`, `squirm`, plus the baby's ambient). Of 78 registered sounds, every other one
belongs to brooms, Apparition, Floo, butterbeer or another system.

**119 of 120 entities are silent** — no ambient, hurt, death or attack sound. Meanwhile 28 rigs have a
`call` clip, 17 a `hiss`, 13 a `groan`, 6 a `song`, 5 a `howl`: the animations for vocalising already
exist and play against silence. This is the largest single gap in the roster and among the cheapest
to close, because the animation half is already done.

## Particles and magical FX

Present and behaviour-carrying: `bioluminescence` (11 creatures) with a glow particle and
`glow_self`, `tint` (4), `ember_trail`, `spore_cloud`, `dread_aura`, the Dementor's crop-withering
and client-side memory voices, and the 50 glowmasks. These communicate rather than decorate, which is
the right bar.

## Bestiary and lore

107 of 120 entities have a Bestiary entry. The 13 without are five projectiles, two manifestations,
four utility entities, the Gringotts teller and the baby Niffler — none of which belongs in a book
about beasts.

Profiles carry classification, `basis`, diet, behaviour, society, interactions, materials and study
methods. **`basis` is the field that keeps lore honest**: it records per entry whether the content is
`canon` or a mod interpretation, which is exactly the distinction the brief asks never to blur.

Contradictions of the "nocturnal in lore, awake at noon" kind: **none found** — no Bestiary entry
claims a diel cycle at all. The real contradiction is the one above: the book describes 107 creatures
and the world contains 24.

## Visual priority groups

Descriptive states, not scores.

| State | Count | What it means here |
|---|---|---|
| **Missing** | 1 | `chocolate_frog` — an entity with a texture but no rig and no animation |
| **Broken** | 0 | no entity has a rig whose texture is absent |
| **Inconsistent** | — | see cross-entity section; this is a roster-level property, not a per-entity one |
| **Underdeveloped** | 94 | has a working rig and texture, and stops at four clips, no sound, generic idle |
| **Complete** | 25 | nothing obviously absent was found at this level of inspection |

"Complete" means only that this audit found no missing component. It is not a statement that the art
or behaviour is good — that judgement needs the game running.

## Cross-entity consistency

Genuine inconsistencies, where two similar creatures are built differently without a technical
reason:

1. **Sound.** The Niffler has nine sounds. Every other creature has none. Same infrastructure
   available to all.
2. **Idle behaviour.** Bowtruckle, Mooncalf and Thestral have species idle goals; the 96 generic
   creatures share one. The Phoenix is bespoke and *still* has a generic idle, which is the clearest
   case of similar things built differently for no technical reason.
3. **Hitbox granularity.** Ten creatures have bespoke boxes; 86 share five tiers.
4. **Texture sheet width.** 64 versus 128 with no recorded rule.
5. **Loot.** 43 entities have a table, 64 generic creatures have none.

Consistent and worth preserving: the four-clip animation standard, the glowmask-by-presence
convention, the per-locomotion navigation split, the data-driven `CreatureDefinition` pipeline, and
the `basis` lore-provenance field.

---

# Final report

### 1. Total entity count

**120** — 24 bespoke Java entities, 96 data-driven creatures.

### 2. Entities missing models

**10**, and 9 of them correctly: `spell_projectile`, `beast_hex_projectile`, `wizarding_thrown`,
`beam`, `spell_clash`, `patronus`, `form_mannequin`, `duelling_dummy` (vanilla player rig by design)
and `baby_niffler` (renders from the adult).

The one real gap is **`chocolate_frog`** — it has a texture and no rig or animation.

### 3. Entities missing textures

**8**, all of them projectiles, manifestations or mannequins that draw from code rather than a sheet.
No creature is missing a texture.

### 4. Entities missing animations

Not a list of entities so much as a list of clips:

- **74 of 110 rigs** have no hurt or flinch clip.
- **110 of 110 rigs** have no death clip. Nothing in the mod has a death animation.
- `chocolate_frog` has no animation file at all.

### 5. Entities using generic AI

**96** — every creature on `GenericBeastEntity`. The AI is not *bad* (temperament, 48 abilities, 15
traits, correct per-locomotion navigation) but it is one behaviour tree configured 96 ways.

### 6. Entities with weak visual identity

**52**, by the structural reading defined above — shares a body plan with 10+ others *and* has only
the four standard clips. The 17 `WINGED_QUADRUPED` creatures sharing a 2.7 × 2.9 hitbox are the
densest cluster.

### 7. Entities with missing Bestiary entries

**13**, and all 13 correctly: five projectiles, two manifestations, four utility entities, the
Gringotts teller, the baby Niffler. **No creature is missing an entry.** The inverse is the real
issue: 83 entries describe creatures that cannot appear.

### 8. Entities with lore/implementation contradictions

No nocturnal-versus-daytime class of contradiction exists, because no entry claims a diel cycle.
What does contradict:

- **83 Bestiary entries for creatures that cannot spawn**, with no UI distinction between planned and
  encounterable.
- **Hitbox tiers versus described size** — a fifty-foot Basilisk with a horse's footprint.
- **Vocal animations against total silence** — 69 rigs carry a voice clip; 119 entities have no sound.

### 9. Entities requiring specialised movement

Few, because the locomotion split already covers most of it:

- **Bowtruckle** — a tree creature with no climbing.
- **Dementor** — should drift through walls; uses ground navigation and `ON_GROUND` placement.
- **Lethifold** (`lethifold_smother`) and the `SESSILE` creature — neither fits ground, flying or
  swimming cleanly.

### 10. Entities requiring specialised idle behaviour

All **96** generic creatures, plus the **Phoenix**, which is bespoke and still idles generically. The
brief's own examples are the right starting set: Niffler (has it), Bowtruckle (partial), Phoenix
(none).

### 11. Entities requiring unique combat behaviour

The **29 HOSTILE** creatures, since all of them currently approach-and-bite on one rhythm. Most
valuable where the creature's identity *is* its attack: Basilisk (death gaze exists), Nundu, Manticore,
Lethifold, Acromantula (`web_snare` exists but no distinct combat rhythm), Blast-Ended Skrewt.

### 12. Shared infrastructure worth improving instead of fixing entities individually

This is where the leverage is. 80% of the roster is one class, so each of these is one change that
reaches dozens of creatures. Recommended only where multiple entities genuinely need them:

**A. Creature sound set — highest value, lowest cost.**
119 entities are silent while 69 rigs already have voice clips authored. A `CreatureSounds` layer
reading an optional `sounds` block on `CreatureDefinition`, defaulting per `bodyPlan`, would give the
whole roster ambient/hurt/death in one pass. The animation work is already done; only the audio and
the wiring are missing. *Needed by: 119 entities.*

**B. `CreatureIdleController` — the brief's own priority.**
A small idle-action vocabulary driven from `CreatureDefinition` (`sniff`, `graze`, `preen`, `perch`,
`dig`, `groom`, `hide`, `bask`), chosen by body plan with per-creature override. The bespoke goals
already written — `ThestralGrazeGoal`, `GatherToDanceGoal`, `StayInBurrowGoal` — are the proof of
concept and the first implementations to generalise. *Needed by: 96 generic creatures + Phoenix.*

**C. Hurt and death animation hooks.**
Two clip names added to the rig standard, plus one shared listener mapping damage and death onto
them. Cheaper than it sounds because `flinch` already exists on 36 rigs and the playback path is
established. *Needed by: 74 rigs for hurt, all 110 for death.*

**D. `CreatureReactions` — environmental and magical stimulus.**
There is no path today by which a creature responds to a spell, to fire, to light, or to another
creature dying. A small reaction table on `CreatureDefinition` keyed to stimulus would give Nifflers
a reason to react to Lumos and herd animals a reason to scatter when one of them falls.
*Needed by: most of the roster; most valuable on the 22 PASSIVE and 45 NEUTRAL creatures.*

**E. Combat rhythm profiles.**
Windup, recovery, preferred range and retreat threshold as a named profile on the definition
(`ambusher`, `charger`, `harrier`, `brute`), replacing the single `MeleeAttackGoal` for all.
*Needed by: 29 HOSTILE creatures.*

**F. Scale as an expressed property.**
`Attributes.SCALE` already moves hitbox and model together (the Occamy proves it). Letting
`CreatureDefinition` carry a scale would let the five hitbox tiers stay — keeping spawning
predictable — while a Basilisk still reads as enormous. *Needed by: the 86 tier-shared creatures.*

Explicitly **not** recommended:

- A `MagicalAuraRenderer`. The glowmask-by-presence convention and `bioluminescence` already cover
  this, and a second path would compete with a working one.
- An `EmissiveCreatureRenderer`. `GeoRendererHelper.applyGlowIfPresent` is that system and it works.
- An `AquaticNavigation` or `FlyingNavigation` abstraction. Both already exist as
  `GenericAquaticBeastEntity` and `GenericFlyingBeastEntity` and are correctly wired.
- A `CreatureInteractionSystem`. Bonding, feeding, handling and harvesting are already unified
  through `BondableBeast`, `HarvestGate` and the Bestiary study methods.

---

## Open design decisions

Things this audit deliberately did not settle, because canon does not:

1. **Minecraft scale for any creature.** No canon source gives one. The tier system is a design
   decision and should be recorded as one.
2. **Whether the Bestiary should show roadmap state.** 83 planned entries currently read as real. The
   UI has no field for it; adding one is a feature, not a fix.
3. **Whether 96 creatures should stay on one behaviour class.** It is why the roster exists at all at
   this size. Diverging them is a cost decision, not a correctness one.
4. **Which 64- or 128-wide texture standard applies to new creatures.**

## Reproducing this audit

Raw data is in `build/entity-audit/`:

- `roster.json` — all 120 entities with class, category, hitbox, model/texture/animation presence,
  texture resolution, glowmask, abilities, traits, temperament, body plan, spawn rule, loot table,
  Bestiary entry and derived state.
- `clips.json` — animation clip names per rig.
- `table.md` — the generated inventory table above.
