# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Type system data expansion (heritage / subtype / profession)

**WizType (10)** — Stats, `MagicSource`, `SizeCategory`, `alphaAvailable`, colours, and two-sentence encyclopaedia descriptions aligned to spec: Wizardkind baseline; Werewolf hybrid +1 armour; Obscurial innate, no wand, +2 HP / +0.02 speed; Goblin small innate −2 HP / −0.005 speed / +2 armour; House-Elf small innate −4 HP / +0.015 speed; Veela hybrid speed; Giant large none-magic tank (20 / −0.02 / 6); Centaur large innate; Vampire hybrid (4 / 0.025 / 2); Merpeople innate land-speed penalty (−0.01) with +1 armour. Alpha flag true only for Wizardkind, Werewolf, Obscurial. Giant size category moved from Huge to Large per design.

**WizSubtype (31)** — Lore descriptions, stat tweaks within caps, tag vocabulary (`no_wand`, `transformation`, `sunlight_weakness`, `enhanced_bond`, `rune_affinity`, `vault_access`, `water_breathing`, `nature_speech`, `divination_sight`, `dark_resistance`, `innate_apparition`, `blood_hunger`, `moon_sensitive`, `obscurus_form`), and per-subtype `uiColor` (ARGB) for the heritage confirmation UI. **31 entries:** core 27 from the heritage spec plus `adopted_magical` (Wizardkind), `savage_bite` (Werewolf), `hall` (House-Elf), `clan_warden` (Giant).

**ProfessionNode** — Full trees for all ten types with tier costs (1 / 2 / 3) and prerequisite chains: Wizardkind branching Apprentice → Wandmaker / Seer → Archmage; Werewolf Tracker → Pack Master → Alpha; Obscurial Channeler → Conduit → Harbinger; Goblin Vault Clerk → Metalsmith / Bank Steward → Director; House-Elf Steward → Ward Binder → Liberated Adept; Veela Charmer → Songbird → Flame Dancer; Giant Bruiser → Ravager → Warlord; Centaur Ranger → Star Reader (`centaur_stargazer_path`) → Oracle; Vampire Stalker → Wraith → Nightlord; Merpeople Tidewarden → Deep Caller → Songweaver. Profession tags updated to the requested set (`wand_mastery`, `divination`, `vault`, `smithing`, `healing`, `stealth`, `transformation`, `combat`, `beast_mastery`, `economy`, `astronomy`, `dark_arts`, `song_magic`, `aquatic`, `bond_magic`).

**Integration** — Wand compatibility and resonance heuristics updated for the new subtype tags; spell/HUD/skill routing treats `no_wand` like Squib; type confirmation header draws subtype line using `getUiColor()`.

### Canon correctness pass (2026-07-25)

**Bestiary** — Corrected 56 `mmRating` values to the five-grade Ministry of Magic scale from *Fantastic Beasts and Where to Find Them*. Six were outside the scale entirely (`sea_serpent` 9; `griffin`/`hippocampus`/`snallygaster`/`tebo`/`wampus_cat` 6) and fifty disagreed with Scamander's grade. Most visible: `phoenix` 1 → 4 (difficulty of domestication) and `golden_snidget` 3 → 4 (endangered protected species) — at their old values the scale read both as harmless birds. `BestiaryScreen` now renders the grade as repeated `X` glyphs rather than a bare integer. Dragon breeds untouched pending a design ruling; entries with no canon Ministry grade untouched. `mmRating` has no gameplay reader, so this is display and datapack metadata only. Full before/after table in `MIGRATION_DELTAS.md`.

**Heritage** — `Heritage` and `HeritageVariant` descriptions now resolve through lang keys (`type.WizardsAndBeastsMod.<id>.desc`, `subtype.WizardsAndBeastsMod.<parent>.<id>.desc`), mirroring the existing `ProfessionNode` pattern; 41 keys emitted through `ModLanguageProvider`, generated `en_us.json` 599 → 640. `/wandb heritage list` now follows the client locale. Corrected the Werewolf description: lycanthropy is transmitted by bite and is not heritable, so the "or born with" clause is gone. No heritage gameplay field changed.

**Spells** — Added `SpellCanonTier` (`BOOKS`/`COMPANION`/`FILM`/`POTTERMORE`/`EXPANDED`/`ORIGINAL`) and an optional `canonTier` field on `SpellDefinition`, defaulting to `EXPANDED` so all existing JSON loads unchanged. Backfilled `flipendo`/`glacius`/`depulso` as `expanded` (video-game origin) and `frigora`/`claustra_reverto` as `original` (no canon attestation). Provenance metadata only — nothing gates on it.

**Wandlore** — `elder.json` no longer carries the `dark` affinity tag. Elder is the rarest and unluckiest wandwood, not an inherently dark one; that association belongs to the Elder Wand as an artefact. Descriptive metadata with no Java reader, so no behaviour change.

**Deferred** — `BestiaryEntry.CODEC` still binds `mmRating` as unbounded `Codec.INT`. Tightening it to `intRange(1, 5)`, plus its regression test, is blocked on `toad.json` shipping `"mmRating": 0` with no canon grade to replace it. See `AUDIT_PUNCHLIST.md`. *(Resolved by the follow-up pass below.)*

### Canon correctness pass, follow-up (2026-07-25)

**Bestiary — `mmRating` is optional.** `BestiaryEntry.mmRating` is now `Optional<Integer>` bound as `Codec.intRange(1, 5).optionalFieldOf("mmRating")`. Absence means *unclassified*, which is the correct state for an ordinary animal with magical uses: Scamander's A–Z lists only creatures that exist exclusively in the magical world, so a toad holds no Ministry grade at all rather than the real grade `X`. `toad.json` drops the field outright (owl, cat and rat will ship the same way). `BestiaryScreen` renders the new `bestiary.wizards_and_beasts.rating.unclassified` placeholder when a grade is absent. New `BestiaryClassificationRangeTest` guards the domain. **Breaking for third-party datapacks:** an `mmRating` outside 1–5 now fails datapack load with a range error.

**Bestiary — dragons are uniformly XXXXX.** All ten breeds are now `5`; seven moved up (`antipodean_opaleye`, `common_welsh_green` from 3; `chinese_fireball`, `hebridean_black`, `norwegian_ridgeback`, `romanian_longhorn`, `swedish_short_snout` from 4). Scamander grades "Dragon" as a single XXXXX species with the breeds described inside it and assigns no per-breed grades; with `mmRating` rendering as X glyphs, a lower per-breed value was a false in-fiction statement. The difficulty gradient is canon-supported at the level of temperament rather than classification and moves to a future `threatTier` field.

**Bestiary — `sortOrder`.** `demiguise` 33 → 94, resolving the only collision in the set (with `cornish_pixie`).

**Spells — `canonTier` is genuinely optional.** The implicit `EXPANDED` default is gone; the field is `Optional<SpellCanonTier>`, so absence now means *untriaged* rather than *expanded*. Backfilled 19 spells as `BOOKS` and 2 as `FILM` (`arresto_momentum`, `bombarda` — both originate in the *Prisoner of Azkaban* film, neither appears in any book). `capacious_extremis` is deliberately left unassigned pending sourcing. **Breaking for third-party datapacks:** a spell without `canonTier` no longer receives an implicit `EXPANDED`.

**Docs** — `DEVELOPER_REFERENCE.md` §2 corrected (107 bestiary entries, 27 JSON spells, 33 total; the 6 Java spells enumerated, since `riddikulus`/`capacious_extremis`/`claustra_reverto` are JSON), and §6 records the dragon ruling. The stale "`AssetModelParityTest` fails at HEAD" punchlist item is marked resolved — it passes, and the uncommitted work tangle does not block the build.

### Added
- Typed skill/type sync packet payloads with bounded decode helpers.
- Packet bounds and codec safety tests for network payload decoding.
- Beam performance presets (`low`, `medium`, `high`) with debug UI and command hooks.
- Timed brewing cauldron BlockEntity scaffold (heat-gated tick progression).
- Legacy namespace migration helper and migration compatibility matrix (`MIGRATION_MATRIX.md`).
- Additional packet bounds regression coverage for skill/type sync payload limits.
- Alpha release candidate gate checklist in `ALPHA_TESTING.md` (tests + fresh server boot + two-client smoke).
- Additional packet bounds regression tests for SpellAssign/SkillUnlock/TypeSelect/FormChange request payloads.
- Pre-beta confidence gate matrix in `ALPHA_TESTING.md` (runData + two-client smoke + soak pass).
- New `time_turner` item with hold-channel-release flow that advances world time on server-side release with cooldown gating.
- Dedicated Obscurial ability-use packet path (`obscurial_ability_use`) and dark-form HUD quickbar/readiness surfacing (`N` Surge, `M` Grasp).
- Configurable server perf profile knobs (`LOW`/`MEDIUM`/`HIGH`) for held-beam scan/effect cadence safety tuning.
- Packet bounds regression coverage for Obscurial ability request payload sizing.
- Lore regression tests for canonical coin selection/ranking and world-placement lore guardrails (tree biome placement + plant biome families).
- Datapack lore regression test coverage for `episkey`, `frigora`, and `levicorpus` spell definitions.

### Changed
- Lumos now stays self-focused (no nearby-player reveal glow), and Expecto Patronum now relies on repel/aura behavior instead of a generic target glow marker.
- Canonical coin checks now key off item ids with explicit slot-priority helper logic, keeping Niffler coin theft deterministic toward highest-value canonical denominations.
- Repo hygiene guardrails now keep local plan artifacts out of git and CI/docs now consistently document the release gate order (`test` -> `runData` -> `runGameTestServer` -> `build`).
- Spell assignment now reports explicit reject reasons to players.
- Form model renderer now prefers per-form texture paths instead of a forced placeholder texture.
- Alpha docs switched command examples from `/WizardsAndBeastsMod` to `/wandb`.
- Spell slot selection and form/type request rejections now provide immediate action-bar feedback.
- Respawn and dimension-change now trigger full spell/skill/type/form re-sync to reduce stale client state.
- Marauder's Map sync now scales interval/entity cap with active viewers to avoid multiplayer tick spikes.
- Held beam channel logic now throttles target scan/effect cadence to reduce per-tick load.
- Timed cauldron brewing now hardens invalid-state cleanup and active-brew queue guardrails.
- Floo Powder now supports bind-and-travel flow on Floo Grates (sneak bind, use travel).
- Obscurial and spell execution hotspots are split into compatibility-safe helpers (`ObscurialValueCodec`, `ObscurialDamageRules`, `SpellCastHandlers`).
- Incendio-style ignition now supports world block ignition on cone and igniting projectile impacts.
- Aguamenti channel now hydrates farmland, fills vanilla cauldrons while held, and places a source-water block after sustained hold.
- Glacius can now interact with world blocks by extinguishing fire and placing snow layers on valid adjacent faces.
- Diffindo projectiles now cut a strict whitelist of soft/crop blocks on block impact.
- Depulso/Flipendo/Expelliarmus block impacts now push nearby lightweight entities (items/projectiles).
- Reparo now falls back to targeted block repairs for safe vanilla subsets (anvil damage tiers and cracked stone/deepslate variants).
- Channel progression now ramps Crucio hold pressure over time and improves Wingardium Leviosa hold stability/strength scaling.
- Targeted block-hit fallback now gives Imperio a short-range mob-control application around the impacted block when no direct entity target is acquired.
- Stupefy projectiles now apply a short impact pulse debuff around block hits.
- Confringo block impacts now apply fire-first surface interaction before explosion resolution.
- Accio now pulls select non-living physical targets (projectiles/vehicles) with LOS checks.
- Expecto Patronum cone casts now include an immediate repel pulse for nearby hostile mobs.
- Obscurus Grasp now uses fluid-aware LOS and smoother pull motion for collision-safe handling.
- Self-cast utility polish adds Protego cast pulses, Arresto Momentum local stabilization, Lumos nearby fire cleanup, and Nox nearby glow cleanup.
- Avada Kedavra channel execution now enforces a direct line-of-sight check before terminal damage application.
- Global knockback handling now caps extreme force values to reduce cross-spell movement spikes.
- Time Turner now has dimension and threat-gating safeguards to prevent unstable time shifts.
- Expecto Patronum aura duration now scales with caster proficiency.
- Protego now grants a short mastery bonus layer on cast for improved defensive uptime.
- Bombarda targeted impacts now emit deterministic impact feedback and lightweight push utility.
- Obscurus Surge now reduces boost force when a close frontal collision is detected.
- Avada Kedavra now requires a short beam charge window before lethal execution.
- Obscurial-only actions (`obscurus_*`) are treated as abilities instead of spell-slot learn/assign flows.
- CI gate now runs `test`, `runData`, and `runGameTestServer` as pre-beta release checks.
- Datapack spell tuning pass improves `episkey`, `frigora`, and `levicorpus` toward more lore-aligned behavior and reduced placeholder effects.
- Expecto Patronum immediate cone pulse now repels only dark-aligned targets (matching aura intent) and uses a more thematic cast audio profile.
- Obscurial dark-form upkeep now escalates with stress tier and surfaces volatile collapse warnings more clearly during active dark form.
- Expecto Patronum cone casts now skip non-dark targets before generic cone damage/effect application, keeping anti-dark identity consistent.
- `wizards_and_beasts:levicorpus` datapack cast sound now uses a neutral magical cue instead of evoker-coded audio.

## [0.1.0-alpha.1] - 2026-04-19

### Added
- Initial external alpha release process docs (`ALPHA_TESTING.md`, `KNOWN_ISSUES.md`).
- CI workflow for `test` and `build` with uploaded jar artifacts.

### Changed
- Release version moved from snapshot to semantic alpha versioning.
- Mod metadata description updated from development wording to alpha wording.
