# Wizards & Beasts — Audit Punchlist

Original sweep: 2026-05-28 (95 findings against 0.1.0-alpha.1).
**Last verified against source: 2026-07-19.**

The original findings block was re-checked claim by claim and **removed**: nearly all of it had been fixed
in the intervening passes while the file kept presenting it as open, which made the document actively
misleading. What survived verification is listed below. Everything after the first dated section is a
historical log of individual work passes — those are records of what was done, not an open-item list.

> Verification rule for this file: a finding stays only if its *substance* was re-confirmed in the current
> source. A missing `TODO` marker is not proof the feature landed — several stub items had their TODO
> removed during refactors and were checked for real behaviour instead.

---

## Open findings (verified 2026-07-19)

- [~] **Cast timing declared and on the wire — PARTIAL 2026-08-12.** The §4 gate had fired on both
  questions: no cast phases anywhere (no `CastManager`; `CastContext` carries no duration), and
  `SpellCastC2SPayload` is an empty record. Christian ruled: add timing to the packet, which is an
  explicit scope grant over `WAND_CAST_POSE_SCHEMA` §9's "do not change the spell pipeline".

  Shipped: `SpellDefinition.CastTiming` (optional; absent = instant, so every existing spell JSON is
  unchanged), `SpellCastAnimationS2CPayload` broadcast to trackers and self on a successful cast, and
  `ClientCastAnimationState` holding running casts keyed by entity id.

  **Still open, and both deliberate:** (a) NO spell JSON declares `castTiming` yet, so nothing
  broadcasts — the field exists and is unused. (b) `KeyframePosePass` does not exist, so nothing
  consumes the state. Casts arrive, are stored, expire, and are never drawn.

  **Not done, and it is a real limitation:** the effect still fires on tick 0. The timing is
  presentation only, so a clip's release window does not land on the effect unless it is authored at
  the very start. Deferring resolution to the release phase is a gameplay change that was not chosen
  (it was option 3 of the ruling) and was not made unilaterally.
- [HIGH] **Broom animation state is never synced, so every observer sees the idle clip.**
  `BroomEntity.registerControllers` gates all three controllers on `currentSpeed` and
  `inputBoosting`. Both are **plain fields**; `defineSynchedData` carries `DEFINITION_ID`,
  `BOOST_TICKS_REMAINING`, `BOOST_COOLDOWN_TICKS` and `CURRENT_DURABILITY` but neither of these.
  GeckoLib controllers run client-side, and `currentSpeed` is produced by `BroomMovement.tickMovement`
  from the input flags, which are written by `setInputFromNetwork` — server-side. On any client that
  is not the rider the inputs are all false, speed decays to zero, and `idleController` wins forever.

- [MEDIUM] **Seven of the ten broom clips have no controller.** `broom.animation.json` ships
  `idle`, `hover`, `fly_forward`, `lean_left`, `lean_right`, `boost`, `brake`, `mount`, `dismount`,
  `summon`. Only `idle`, `fly_forward` and `boost` are referenced from Java. The other seven are
  authored, shipped, and unreachable — `hover` and the two `lean` clips are the ones a flying broom
  most obviously wants.

- [x] **Design docs were invisible to git — FIXED 2026-08-12.** Every root `.md` and the whole of
  `docs/` were gitignored by the 2026-08-10 history purge, so writes to `AUDIT_PUNCHLIST.md`,
  `MIGRATION_DELTAS.md` and every design schema succeeded silently and were never tracked. 27 files,
  ~1.4 MB, moved to `documentation/`, which no ignore rule matches. The ignore rule itself was NOT
  weakened — note that the obvious fix does not work: `docs/` is itself covered
  (`.gitignore:153:/docs/`), so moving there would have changed nothing.

- [MEDIUM] **`tasks/` is still invisible to git.** `todo.md`, `lessons.md` and `ministry_plan.md` are
  covered by `.gitignore:154:/tasks/` and were deliberately NOT moved: those exact paths are prescribed
  by the user's global `CLAUDE.md` workflow ("write plan to `tasks/todo.md`", "update
  `tasks/lessons.md`"), so relocating them would break a convention this migration had no mandate to
  change. They have the same silent-discard failure mode as the docs did. Needs a ruling: relocate and
  update `CLAUDE.md`, or un-ignore `/tasks/`.

- [LOW] **CI references a file that does not exist.** `.github/workflows/ci.yml:42` prints "Update
  KNOWN_ISSUES.md with reproducible failure details before retagging" on failure. There is no
  `KNOWN_ISSUES.md` anywhere in the repository — it was presumably purged and never recreated. Not
  fixed: choosing the replacement document is a call for the maintainer.

- [x] **`FLIGHT_POSE_CONSTANTS.md` added — RESOLVED 2026-08-12.** `client/pose/FlightPoseConstants.java`
  cited it as the source of its authored values while it existed only in conversation. Now at
  `documentation/FLIGHT_POSE_CONSTANTS.md` and the javadoc citation points at the real path. It carries
  an appended implementation-notes section recording the two deviations already logged here (the `HEAD`
  sign, and §5's Y offset read as blocks) plus the unimplemented §6 continuous blend, so the authored
  source and what shipped no longer disagree silently.
- [x] **Design docs were invisible to git — FIXED 2026-08-12.** Every root `.md` and the whole of
  `docs/` were gitignored by the 2026-08-10 history purge, so writes to `AUDIT_PUNCHLIST.md`,
  `MIGRATION_DELTAS.md` and every design schema succeeded silently and were never tracked. 27 files,
  ~1.4 MB, moved to `documentation/`, which no ignore rule matches. The ignore rule itself was NOT
  weakened — note that the obvious fix does not work: `docs/` is itself covered
  (`.gitignore:153:/docs/`), so moving there would have changed nothing.

- [MEDIUM] **`tasks/` is still invisible to git.** `todo.md`, `lessons.md` and `ministry_plan.md` are
  covered by `.gitignore:154:/tasks/` and were deliberately NOT moved: those exact paths are prescribed
  by the user's global `CLAUDE.md` workflow ("write plan to `tasks/todo.md`", "update
  `tasks/lessons.md`"), so relocating them would break a convention this migration had no mandate to
  change. They have the same silent-discard failure mode as the docs did. Needs a ruling: relocate and
  update `CLAUDE.md`, or un-ignore `/tasks/`.

- [LOW] **CI references a file that does not exist.** `.github/workflows/ci.yml:42` prints "Update
  KNOWN_ISSUES.md with reproducible failure details before retagging" on failure. There is no
  `KNOWN_ISSUES.md` anywhere in the repository — it was presumably purged and never recreated. Not
  fixed: choosing the replacement document is a call for the maintainer.

- [x] **Companion schemas added — RESOLVED 2026-08-12.** `PLAYER_POSE_LAYER_SCHEMA.md` (rev 4) and
  `WAND_CAST_POSE_SCHEMA.md` (rev 2, incl. §8b) existed only in conversation, which is what halted two
  agent sessions with "the companion schema does not exist". Both now at `documentation/`, alongside
  `FLIGHT_POSE_CONSTANTS.md`. Note the ignore rule still covers the repository root, so a schema
  written there in future is silently discarded exactly as before — `documentation/` is the only
  tracked home for these.
- [NICE-TO-HAVE] **The slim-arm variant does not need a second rig.** Reported per the authoring-rig
  brief §3.3 rather than decided. `PlayerModel.createMesh(deformation, true)` changes the arm cube
  width from 4 to 3 and shifts the left arm cube's x offset, but leaves BOTH shoulder pivots at
  (±5, 2, 0) — verified in 1.21.11 source. Animation clips read pivots and rotations, never cube
  dimensions, so a slim rig would animate identically. A second file would only improve Blockbench
  preview fidelity for slim-skinned authors, at the cost of two rigs to keep in sync.

- [NICE-TO-HAVE] **Geo identifier namespacing is inconsistent across the project.** `wand.geo.json`
  uses `geometry.wizards_and_beasts.wand`; every entity rig uses a bare name (`geometry.ashwinder`).
  `player.geo.json` follows the entity convention. Harmless today — the rig is never registered — but
  `geometry.player` is generic enough to collide if it ever is. Not changed: renaming it would touch
  the established entity convention, which is out of scope here.

- [NOTE] **No geo asset in this project had any comment mechanism.** The authoring-rig brief asked for
  the file to declare itself authoring-only "by whatever comment mechanism the project's other geo
  assets use"; a scan of all 120 rigs found zero comment keys of any kind. A top-level `_comment` was
  introduced. GeckoLib's loader is Gson with named fields (`Model.deserializer()`), so unknown keys
  are ignored rather than rejected.

- [x] **Whole-body pitch was computed and thrown away — FIXED 2026-08-12.** `PlayerPoseHandler` documented
  its own gap honestly ("nothing consumes this yet") and the first in-game pass is what made the cost
  obvious: GLIDE and PROPELLED are built around a body pitch of -35/-70 with the head and arms posed to
  match, so without it the player kept an upright torso while the head cranked down and the arms swung
  overhead. Applied at the tail of `AvatarRenderer.setupRotations` via `AvatarRendererMixin` — the one
  frame where the stack is yawed to the player's facing with the origin at their feet, and where vanilla
  puts its own elytra and swimming pitches. Anywhere earlier rotates about the WORLD x axis, so a player
  flying east rolls onto their side instead of leaning forward.

- [x] **Landing snapped the flight pose off — FIXED 2026-08-12.** `FlightPosePass` cleared its state the
  moment the override went absent, and `pose()` returns early on a null state, so the 8-tick fade-out ran
  with nobody reading it. Now the state outlives the deactivation and is cleared only once the fade is
  idle. Pinned by `FlightPosePassTest`.

- [NICE-TO-HAVE] **The broom rider's body tilt is applied in the world frame.** `BroomRiderRenderHandler`
  hooks `RenderLivingEvent.Pre`, which fires before `setupRotations` applies the body yaw, so its
  `Axis.XP` pitch and `Axis.ZP` roll are about world axes rather than the rider's own. It should look
  correct facing north and turn into a roll facing east. Noticed while siting the pose layer's body
  transform; NOT verified in game and NOT touched, since the broom is its own lane.

- [x] **Flight pose values authored — RESOLVED 2026-08-12.** The placeholder set is gone. Values
  transcribed from `FLIGHT_POSE_CONSTANTS.md` into `client/pose/FlightPoseConstants.java`: per-state
  body pitch and pivot, head compensation, per-part rotation triples for chest/arms/legs, first-person
  arm poses with a speed-proportional drop, bank coefficients and the asymmetric fade. Described by its
  author as a tuned starting point rather than final, so expect adjustment — but it is authored, which
  the previous set explicitly was not.

  ONE VALUE CORRECTED FROM THE SOURCE TABLE: `headPitch` is negated. The document gives +2/+22/+58 and
  states the purpose is "so the player still looks where they are going rather than at the ground",
  which in Minecraft requires a negative number — vanilla sets `head.xRot = state.xRot * DEG_TO_RAD`
  and entity pitch is positive looking DOWN. The magnitudes are the author's; only the sign changed,
  and `FlightPoseConstantsTest.headCompensationLiftsTheGazeRatherThanDroppingIt` pins it because it is
  the value most likely to be "fixed" back.

  ONE UNIT INFERRED: §5's first-person `Y offset` (-0.06/-0.14) is read as BLOCKS and converted. As
  ModelPart units those values are invisible, which contradicts the note saying the arm drops to clear
  the crosshair.

- [OPEN] **§6's continuous GLIDE→PROPELLED blend is not implemented.** The source document asks for
  propulsion to drive GLIDE→PROPELLED continuously, treating PROPELLED as the far end of a blend rather
  than a third discrete pose. That needs a continuous value on the wire, and `PoseOverride` carries only
  a discrete `FlightPoseState` plus the manual flag — a schema change, not an implementation detail, so
  it was not made unilaterally. What ships is the six-tick eased cross-fade between the two discrete
  poses, which removes the pop but is not speed-proportional. Needs a ruling: extend the payload with a
  0..1 intensity, or accept the cross-fade.

- [x] **One set of pose timers served every player — FIXED 2026-08-12.** `FlightPosePass` is a singleton
  consulted once per rendered player but held its fade, cross-fade and bank in fields, so with two people
  airborne everyone would have been posed with the LOCAL player's timers. Now keyed by entity id
  (`AvatarRenderState.id`), ticked over `level.players()`, and gated on `onGround()` rather than the
  flying ability — abilities are only synced for the local player, and a pose other people can see has to
  be gated on something other people can see. Single player hid this completely.
- [x] **Whole-body pitch was computed and thrown away — FIXED 2026-08-12.** `PlayerPoseHandler` documented
  its own gap honestly ("nothing consumes this yet") and the first in-game pass is what made the cost
  obvious: GLIDE and PROPELLED are built around a body pitch of -35/-70 with the head and arms posed to
  match, so without it the player kept an upright torso while the head cranked down and the arms swung
  overhead. Applied at the tail of `AvatarRenderer.setupRotations` via `AvatarRendererMixin` — the one
  frame where the stack is yawed to the player's facing with the origin at their feet, and where vanilla
  puts its own elytra and swimming pitches. Anywhere earlier rotates about the WORLD x axis, so a player
  flying east rolls onto their side instead of leaning forward.

- [x] **Landing snapped the flight pose off — FIXED 2026-08-12.** `FlightPosePass` cleared its state the
  moment the override went absent, and `pose()` returns early on a null state, so the 8-tick fade-out ran
  with nobody reading it. Now the state outlives the deactivation and is cleared only once the fade is
  idle. Pinned by `FlightPosePassTest`.

- [NICE-TO-HAVE] **The broom rider's body tilt is applied in the world frame.** `BroomRiderRenderHandler`
  hooks `RenderLivingEvent.Pre`, which fires before `setupRotations` applies the body yaw, so its
  `Axis.XP` pitch and `Axis.ZP` roll are about world axes rather than the rider's own. It should look
  correct facing north and turn into a roll facing east. Noticed while siting the pose layer's body
  transform; NOT verified in game and NOT touched, since the broom is its own lane.

- [x] **Legilimency / non-wizard Apparition unreachable — FIXED 2026-07-19 (`9e46f38`).** Both gates keyed
  on tags no `HeritageVariant` declares. Re-gated on the vocabulary the mod already speaks: Legilimency
  needs a wizard who can work magic (excludes the Squib via the existing `no_casting` tag the skill webs
  gate on); Apparition additionally accepts `innate_apparition`, already carried by all three house-elf
  variants and the concept `elf_apparition` is built around. The original `can_legilimise` / `can_apparate`
  tags still grant, so datapacks can opt in without code changes.

- [x] **Nine beast items missing model + texture — FIXED 2026-07-19 (`9e46f38`).** Models and 16×16 icons
  generated; seven now drop from the beast they are named after (those entities had no loot table at all).
  `AssetModelParityTest` passes and the suite is fully green (182/182) for the first time. The two
  `hidebehind_*` items have no creature to drop from — see below.

- [NICE-TO-HAVE] **`hidebehind_claw` and `hidebehind_shadow_essence` have no source.** There is no
  `hidebehind` creature definition, so both are obtainable only via `/give`. Either add the creature or drop
  the two items.

- [POLISH] **Beam origin unverified.** Endpoint, jitter and glow had identifiable causes in source and are
  fixed; the origin resolves either from the GeckoLib `fx_tip_anchor` bone cache or, when that is stale,
  from hardcoded first/third-person hand offsets in `WandBeamRenderer.wandTipWorldPos`. Which path is wrong
  cannot be told without watching it in game, in both perspectives, with and without the elder-wand skin
  (different tip bone).

- [POLISH] **Heritage form art is placeholder-only.** `textures/entity/form/` contains exactly one file,
  `placeholder.png`. Animagus forms sidestep this by rendering real vanilla models, so the gap is narrower
  than the original finding implied, but any form that does need a bespoke texture has none.

- [NICE-TO-HAVE] **Stale TODOs referencing assets that now exist.** `CharacterSheetTextures` still carries
  "TODO: create …/background.png" and "…/icon_tab.png" comments; both PNGs are present on disk. Comment
  drift only. `client/gui/character/CharacterSheetTextures.java:9,16,23`.

- [NICE-TO-HAVE] **Nine TODO sites remain** across `src/main/java`, all genuine future-work markers rather
  than defects: Dementor Muggle-heritage handling and whisper audio, the two `CharacterSheetTextures` ones
  above, an Occlumency/Dark-Arts-Trace hook, a `floo_fireplace.png` asset note, and a skill-tree damage
  stacking note in `SpellExecutor`.

---

## Removed 2026-07-19 — verified fixed, no longer open

Recorded so the history is not lost. Each was re-checked in source before deletion:

- **Lang keys** claimed absent all exist now: `knut`/`sickle`/`galleon`, `goblin_teller`, base `wand`,
  `goblin_teller_spawn_egg`, and the block keys (`warding_stone`, `floo_fireplace`, `brass_cauldron`,
  `devils_snare`, …).
- **`textures/bestiary/`** exists; **character-sheet `background.png`** exists.
- **`ModNetworkAzkaban.java`** exists — the claimed compile failure is long gone.
- **KNOWLEDGE stat** is derived properly now (`PlayerStatsAPI` computes it; the hardcoded HUD zero is gone).
- **`PatronusFormDeterminer`** no longer appends `_rare` to entity ids, and the questioned
  MERPEOPLE/GOBLIN/HOUSE_ELF mappings are gone.
- **`StatMilestones`** handlers are implemented (real stat grants per milestone), not empty stubs.
- **`sounds.json`** has zero empty `sounds: []` arrays.
- **Stub trinkets** are implemented — e.g. `PensieveItem.use` opens the Pensieve screen via
  `PensieveOpenS2CPayload`. `MoodysTrunkItem` and `NewtsCaseItem` no longer exist at all.
- **Swallowed `catch (Exception ignored)`** blocks in `ExtensionCharmService` are gone.
- **mmRatings** corrected: Niffler 3, Thestral 4, Hippogriff 4 — matching the values the finding demanded.
- **`hidden_wizarding_cache`** no longer carries the `_comment` stub marker.
- **`floo_fireplace`** block model no longer uses `chiseled_stone_bricks`.
- **`test_datapack_tip.json`** has been deleted from the shipped datapack.
- **Templated bestiary lore** ("…is recorded by Magizoologists as a notable magical species") no longer
  appears anywhere in `en_us.json`.
- **STRUCTURES gating** on location blocks is now a documented deliberate choice ("gate, don't delete") in
  `ModBlocks`, not an unaddressed TODO.
- **`mana_cost` / `animation` spell-JSON fields**: dropped as a phantom requirement. Zero of the 26 shipped
  spell JSONs use either field — the mod has no mana system, so their absence was never a defect.
- **Crucio's `WITHER` effect** and **AvadaKedavra's `MASTERED` prerequisite**: the bespoke `Crucio.java` /
  `AvadaKedavra.java` classes no longer exist; those spells ship as data-driven JSON.

---

## Creature Build Pass (2026-06-18) — 53 placeholder creatures

- [POLISH] Generic creature renderers use the proven per-id `GeoRendererHelper.simple(<id>)` factory rather than the prompt's "one renderer per locomotion class". Deliberate: reuses the shipping mooncalf/streeler render path and avoids re-deriving `GeoModel` path prefixes. No per-creature Java classes; only renderer instances differ. `src/main/java/at/koopro/wizardsandbeasts/client/ClientSetup.java`.
- [POLISH] Spawn-egg *use* is not module-gated (only the `/wandb creature summon` command checks `Module.CREATURES`). Gating egg use would require a custom `SpawnEggItem` subclass. `Module.CREATURES` is `PREVIEW` (enabled), so both work today. `src/main/java/at/koopro/wizardsandbeasts/registry/ModCreatures.java`.
- [POLISH] Spawn-egg items have no item model (`models/item/<id>_spawn_egg.json`) and no egg color — they render as the missing-model icon in inventory/creative. Entity world render is unaffected (GeckoLib). Follow-up: emit spawn-egg models via datagen + creative-tab placement.
- [POLISH] `obscurus` is FLYING + BLOB_SPHERE (wingless) → its `…fly` animation clip is empty (idle-only motion). Cosmetic.
- [POLISH] No creative-tab entry for the 53 spawn eggs; obtainable via `/give` or `/wandb creature summon`.
- [NICE-TO-HAVE] Live-client smoke test of `/wandb creature summon <id>` + visible idle render not executed in this environment; render path is structurally identical to shipping GeckoLib mobs.

## Creature Build Batch 2 (2026-06-18)
- [RESOLVED 2026-06-22] Real lore written for all 33 new bestiary entries (tools/creature_lore.py); no placeholder lore strings remain.
- [POLISH] New bestiary icon/silhouette art is solid-color placeholder (32px). Replace with real bestiary art. `textures/bestiary/{icons,silhouettes}/`.

## Dragon Fire/Venom Kit (2026-06-24)
- [POLISH] Dragon breath visual is server-spawned tinted dust + vanilla FLAME particles, not a bespoke breath model/mesh. `fire_color` is also carried into render-state (`DragonRenderer.TICKET_FIRE_COLOR`) but the placeholder rig does not yet tint the model geometry — wired for the real-art swap. `entity/creature/ai/DragonBreathGoal.java`, `client/entity/DragonRenderer.java`.
- [POLISH] `rideable` (Ironbelly = true) is data-only this pass; the mount/flight-control subsystem is a deferred follow-up prompt (per scope §5). Field exists for forward-compat.
- [POLISH] Breath/bite placeholder anim clips are simple head/body keyframes added to the existing box rig; real Blockbench clips drop in by file swap (anim names `animation.<id>.breath` / `.bite`).
- [NICE-TO-HAVE] ASH combustible tag covers vanilla timber (#logs/#planks/wooden families) + bone_block/bookshelf/crafting_table; extend as modded wood/bone blocks are added. `data/wizards_and_beasts/tags/block/dragon_ash_combustible.json`.
- [NICE-TO-HAVE] Live-client smoke test (`/wandb creature summon <breed>` + forced breath) not run in this environment; hit logic is server-authoritative and unit-shaped, render path mirrors shipping GeckoLib mobs.

## CreatureAbility framework + FireAffinity (2026-06-24)
- [NOTE] §4 of the ability prompt says ability dispatch must no-op while the module is `DISABLED` *or* `PREVIEW`. The codebase precedent (`DragonBreathGoal.canUse`, `/wandb creature summon`, every other creature gate) uses `ModuleManager.isEnabled(Module.CREATURES)`, which returns true for PREVIEW. The prompt also mandates "mirror it; do not invent new gating semantics" and the verification gate requires a summoned Salamander to actually exhibit its abilities while `Module.CREATURES` is `PREVIEW` (its default). Resolved by mirroring `isEnabled` (PREVIEW + ENABLED run abilities; DISABLED no-ops) — the §4 "PREVIEW no-op" wording is treated as loose. Toggle-at-runtime verification (disable → no-op, re-enable → resume) holds either way.
- [POLISH] `FireAffinity` "in fire" detection is `isInLava()` + a fire-block check at the entity's feet/feet-above block only (not a full bounding-box sweep). Adequate for the small placeholder hitboxes; widen if a large fire-dweller is added. `creature/ability/FireAffinity.isInFireOrLava`.
- [POLISH] Ember particles spawn at the entity centre (body anchor = `getY()+bbHeight*0.5`), not a Blockbench-defined `mouth`/`vent` locator — the placeholder rigs have no FX-anchor bones yet. Swaps to a real locator when art lands (mirrors the wand/Niffler `*_anchor` pattern). No dynamic block-light (per scope).
- [POLISH] Fire Crab's flame-burst emission and Ashwinder's igniting-egg laying are intentionally NOT implemented (deferred "fire emission" prompt, scope §3). Fire Crab currently ships `fire_immune` only.
- [NICE-TO-HAVE] Live-client smoke test (`/wandb creature summon salamander|ashwinder|fire_crab` + in/out-of-fire behaviour, attacker ignite, module toggle) not run in this environment; logic is server-authoritative and covered by `CreatureDefinitionCodecTest` at the data layer. Render path unchanged (no render-time entity access added; particles are server-spawned).

## Full-roster creature abilities (2026-06-24)
- [RESOLVED 2026-06-24] `ranged_hex` now launches a real travelling `BeastHexProjectile` (`ThrowableItemProjectile`, registered `beast_hex_projectile`, vanilla `ThrownItemRenderer`) carrying damage + effect + trail — dodgeable, no longer hitscan. `web_snare`/`nundu_pestilence`/thunderbird-bolt remain intentional AoE/instant effects (not projectiles by design). `entity/creature/BeastHexProjectile.java`, `entity/creature/ai/RangedHexGoal.java`.
- [RESOLVED 2026-06-24] `leap` now uses `GatedLeapGoal` (a module-gated re-implementation of vanilla `LeapAtTargetGoal`) instead of the vanilla goal, so it self-gates on `Module.CREATURES` like every other ability goal. Disabling the module now stops the leap too. `entity/creature/ai/GatedLeapGoal.java`.
- [NOTE] `blink_away` and `camouflage` share the entity's single `AbilityCooldown` counter, so a creature must not declare both (camouflage's reveal-window and blink's cooldown would interfere). Enforced by assignment: Demiguise/Moke use `camouflage` only (blink dropped). Add a second counter if a future creature needs both.
- [POLISH] `thunderbird_storm` sets *global* server weather (thunder/rain) when the Thunderbird engages a target — intended drama, but world-affecting. Tune `storm_duration_ticks` or scope to local effects if undesirable. `creature/ability/ThunderbirdStorm.java`.
- [RESOLVED 2026-06-24] Canon-gap signatures filled: graphorn `spell_resist`, erumpent `explosive_horn`, jobberknoll `death_cry` (new `onDeath` hook), ramora `anchor`. See MIGRATION_DELTAS.
- [RESOLVED 2026-06-24] occamy choranaptyxic render-scale (`occamy_choranaptyxis` + synced `DATA_RENDER_SCALE` + `ScaledBeastRenderer`), Fire Crab `flame_burst` + Ashwinder `ember_trail` (fire emission), Kneazle `danger_sense`, and real ranged projectiles — all shipped. See MIGRATION_DELTAS.
- [POLISH] `ember_trail` places vanilla fire blocks in the Ashwinder's wake (world-affecting, like the dragon scorch). Gated by a 6%/tick chance + air-on-solid only; tune `chance_per_tick` if too aggressive. `creature/ability/EmberTrail.java`.
- [NICE-TO-HAVE] Still deferred: render-time model tints (art-swap, no logic), Ashwinder igniting-egg ITEM (needs a new item + loot pass), and deeper bespoke uniques beyond the signatures. New canon creatures already exist as dedicated `entity.beast` entities (not the data roster). Niffler THIEF→ability-layer refactor left untouched (prior scope).
- [POLISH] `beast_hex_projectile` has no lang key (`entity.wizards_and_beasts.beast_hex_projectile`); harmless (projectiles aren't named in UI) and renders as a flung magma cream placeholder until beast art lands.
- [NICE-TO-HAVE] Live-client smoke test (summon each beast, observe auras/leaps/ranged/blink/storm + module toggle) not run in this environment; all ability logic is server-authoritative and covered at the data layer by `CreatureDefinitionCodecTest` (parses all 86 creature JSONs + asserts dispatch keys). No render-time entity access added.

---

## Vocation Specialization Framework — AUDIT (2026-06-29) — HALTED per §12

> **RESOLVED — historical record.** These BLOCKERs were pre-implementation *assumption* mismatches, not
> defects. The framework was then built against the real types (see "Vocation Framework — BUILT" below).
> Kept because the corrected type descriptions are still the accurate reference for the skill system.

Audit of the existing skill system before building the Vocation layer. Result: **STOP AND REPORT** — the
prompt's structural assumptions diverge from reality on every load-bearing point. Building as specified
would require touching `Skill`/`SkillNodeEffect`/`PlayerSkillData` and node authoring — all out of scope (§11).

- [BLOCKER] `SkillTree` enum assumption WRONG. Real type is `SkillTreeId` (`skill/SkillTreeId.java`): 7 values
  (SPELL_MASTERY, DARK_ARTS, MAGIZOOLOGY, WANDLORE, HERBOLOGY **+ GOBLIN_CRAFT + ELF_BOND**), and it has **no
  `int maxPoints`**. Fields are id/displayName/color/`Audience`. "Tree max" gating is by heritage `Audience`,
  not a per-tree point cap. → §12 "SkillTree is not a clean enum with the expected values."
- [BLOCKER] `SkillNode` record assumption WRONG. Real type is `Skill` (`skill/Skill.java`) — a builder-built
  **final class, not a record**. `id` is a **String, not Identifier**. It carries `effects: List<SkillEffect>`
  (a *different* type than `SkillNodeEffect`) plus a lazily-derived `nodeEffects: List<SkillNodeEffect>`, and a
  `maxLevel` (nodes are multi-level). `capstoneNodeId: Optional<Identifier>` cannot reference a String-keyed
  node without touching `Skill`. → §12 "would require touching SkillNode."
- [BLOCKER] `PlayerSkillData` assumption WRONG. It is **not a Codec record** — it is an NBT `save()/load()`
  class (`ModAttachments.NbtSerializable`). Fields: `skillPoints` (live remaining counter), `totalPointsEarned`,
  `unlockedSkills: Map<String,Integer>` (**level map, not a `unlockedNodes` set**), no `abilityFlags`
  (abilities derive via `SkillEffectCache`). Prompt's `unlockedNodes/spentPoints/abilityFlags/Codec` do not
  exist. → §11/§12 "any change to PlayerSkillData."
- [BLOCKER] Points accounting differs from `totalPointsEarned − Σ spentPoints`. Real model: `skillPoints` is a
  live counter; spend decrements it, **refund = `skillPoints += pointCost * level`** (see
  `PlayerSkillData.resetSkill/resetAll`). There is no per-spend ledger to sum. → §12 EXACT trigger: "points
  accounting differs … making the §7 refund unsafe (as specified)."
- [BLOCKER] `SkillTreeEffectApplicator` (real name `SkillAttributeApplicator`) cannot apply/remove an arbitrary
  `List<SkillNodeEffect>` for a non-node source. Public API is `applyAll(player)` / `removeAll(player)` only —
  a **full rebuild from `unlockedSkills`**; `applyNode` is private and only handles `AttributeBoost`.
  `removeAll` wipes **all** `skill/`-prefixed modifiers wholesale (not keyed per-source removal). A vocation
  commitment profile would need new public methods + integration into the rebuild loop. → §12 trigger.
- [INFO] Skill nodes are **Java-defined** (`SkillTrees` + `*Skills` builder classes), NOT datapack JSON. There
  is no `SkillNodeRegistry` reload listener to mirror (bestiary/spells are the datapack-driven ones). A
  datapack `VocationRegistry` is still feasible, but `capstoneNodeId` "must reference an existing node" assumes
  datapack-addressable nodes that don't exist as such.
- [OK] Single injection point exists and is clean: `SkillSystemAPI.evaluateUnlock(ServerPlayer, Skill)` already
  early-returns when `Module.SKILL_TREES` disabled. Sync via `SkillDataSyncS2CPayload` + `ClientSkillDataState`,
  registered in `ModNetworkSkills` — a parallel payload is feasible. `/skilltree` command is graftable.
  These four pieces match the prompt; everything structural above does not.

---

## Heritage Selector Redesign — Pre-Build Audit (2026-06-29)

Scope: first-join Heritage selection screen + display/widget layer only. **No BLOCKERs found — clear to build.**

1. [OK] **Current screen** — `at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionScreen extends net.minecraft.client.gui.screens.Screen`. Three-phase wizard (`HERITAGE_LIST` arrow-cycle → `VARIANT_LIST` → `CONFIRMATION`), `Button`-driven, drawing delegated to `HeritageSelectionRenderHelper`. Display strings come from the `Heritage`/`HeritageVariant` enum accessors (`getDisplayName()`, `getDescription()`) — **hardcoded English in the enums**, plus two translation keys (`message./ui.wizards_and_beasts.type_selection.coming_soon`). Scaling via `ScreenLayoutScaler` (not `GuiScaleHelper`).

2. [OK] **Gate-mode** — there is **no flag**: the screen is *always* the hard gate. `shouldCloseOnEsc()→false`, `isPauseScreen()→false`, and `keyPressed` swallows ESC at the root phase (steps back in sub-phases, blocks dismissal at `HERITAGE_LIST`). Open trigger is server-driven: `HeritageDataSyncS2CPayload.openSelector()==true` → `ClientPayloadHandlers:270` → reflection `openHeritageSelectionScreen` → `new HeritageSelectionScreen()`. Preserve this exactly; new screen keeps the same three overrides and parameterless ctor.

3. [OK] **Enums** — `Heritage` (10 values) and `HeritageVariant` (33 values) both live in common pkg `at.koopro.wizardsandbeasts.heritage` → client-accessible. Expose `getDisplayName/getDescription/getColor/isAlphaAvailable/getSubtypes` (Heritage) and `getDisplayName/getDescription/getUiColor/getTags/getParentHeritage/getTotal{Health,Speed,Armor}` (HeritageVariant). **Note (POLISH):** prompt says `WIZARDKIND` has 4 variants (`pure_blood/half_blood/muggle_born/squib`); the enum actually has **5** (adds `adopted_magical`/"Wizard-Raised"). Build renders *all* variants uniformly via `getSubtypes()`, so this is non-blocking — just a count discrepancy vs the prompt.

4. [OK] **Packets** — C2S commit `HeritageSelectC2SPayload(String typeId, String subtypeId)`; S2C open `HeritageDataSyncS2CPayload` (open flag = `openSelector()`). **Server commit handler ALREADY validates availability**: `HeritageSelectC2SPayload.handle` rejects `!heritage.isAlphaAvailable()` (lines 71-75) with `message.wizards_and_beasts.type_selection.coming_soon`, plus locked-check, null-check, and parent-match check. → **Deliverable 5 is a no-op; no server change needed.** Signatures unchanged.

5. [OK] **Availability signal** — authoritative source is the enum flag `Heritage.isAlphaAvailable()`. Exactly 3 are true: `WIZARDKIND`, `WEREWOLF`, `OBSCURIAL`. The other 7 (`GOBLIN, HOUSE_ELF, VEELA, GIANT, CENTAUR, VAMPIRE, MERPEOPLE`) are false → locked-but-browsable. No `ModuleManager` per-heritage state exists; do not invent one. Reuse `isAlphaAvailable()`.

6. [OK] **House style** — `WizardsConfigScreen` (Ministry-memo: parchment fill via `McStylePanel.drawTiled`, letter-spaced shadow-free header, CLASSIFIED wax-style stamp, staggered ink reveal). Reuse: `InkRevealRenderer` (`client.gui.config`, public, widget-stagger + `isRevealed(idx,delayMs)` for manual draws), `McStylePanel` (`drawTexturedPanel/drawTiled/drawNineSlice/drawPanel/drawBorder`), `GuiScaleHelper` (`computeScale/clampedLeft/clampedTop`, downscale-only), parchment palette in `WizardsAndBeastsUiTokens.HeritageSelection.COLOR_*` and `ConfigWidgets.{PARCHMENT,INK,STAMP_RED}`.

7. [OK] **Mechanical descriptor** — `at.koopro.wizardsandbeasts.stats.PowerBandTable` is common pkg, pure static (`getBandMax(variant)`, `getGrowthCap(variant)`), no server-only deps → **client-accessible**. Identity card CAN show a power-band descriptor (band max per variant). Use it.

**Stop-and-report checklist:** none triggered. No enum/data-layer change needed (1 ✗), screen not entangled with gate handler — gate is self-contained in the screen (2 ✗), availability signal exists (3 ✗), commit handler already validates so D5 is zero-line (4 ✗), payload signatures unchanged (5 ✗), no BLOCKER (6 ✗). → **Proceed with build.**

### Vocation Framework — BUILT (Path 1 adaptation, 2026-06-29)

Halt resolved by user-approved Path 1 (adapt to real types; no touch to Skill/SkillNodeEffect/PlayerSkillData/nodes).
Shipped + `./gradlew test` green (incl. new VocationUnlockStateTest, 8 cases). New `skill.vocation` package:
VocationDefinition (Codec; `commitmentEffects` wrapped in `Codec.lazyInitialized` so class-load doesn't pull the
attribute registry — keeps it unit-testable without an FML bootstrap), VocationRegistry (+symmetric areOpposed),
VocationLoader (datapack `vocations/`, wired in WizardsAndBeastsMod), PlayerVocationData (separate Codec
attachment), VocationHelper (Band/UnlockState + pure `unlockState(Optional,Optional,Skill)` core), VocationManager
(commit/clear orchestration + audit block), VocationEffectApplicator (parallel `vocation/`-prefixed keyed apply).
Gate = one line in SkillSystemAPI.evaluateUnlock. Sync = parallel VocationDataSyncS2CPayload + ClientVocationCache
(login + commit/clear). Commands grafted at `/wandb skill vocation {info|set primary|secondary|clear}` (no
`/skilltree` exists). 5 JSONs + en_us keys. Adaptations vs prompt: `/wandb skill` not `/skilltree`; parallel
applicator not SkillTreeEffectApplicator; refund via resetSkill not spent-ledger; grant_ability flags live in a
`grantedAbilities` field (no new SkillNodeEffect variant); attributes limited to existing wand_affinity (others
ship as TODO ability flags). Capstones left Optional.empty (no node authoring). Healer opposition handled absent.

## Handbook Audit

Pre-implementation audit for Ministry Handbook (Part 1: Infrastructure). No stop-and-report triggers hit.

1. **Bestiary pattern**
   - `bestiary/BestiaryEntry.java` — `record` + `Codec<BestiaryEntry> CODEC` (RecordCodecBuilder). Keyed by `Identifier id`. Translatable copy stored as lang keys (e.g. `displayNameKey`), resolved via `Component.translatable`.
   - `bestiary/BestiaryEntryLoader.java` — server `SimpleJsonResourceReloadListener<BestiaryEntry>`, dir `bestiary/entries`, `FileToIdConverter.json(DIRECTORY)`. `apply()` → `BestiaryEntryRegistry.replaceAll(...)`.
   - `bestiary/BestiaryEntryRegistry.java` — static `Map<Identifier,BestiaryEntry>`, `replaceAll/get/getAll`. NOTE: entries themselves are NOT network-synced; the client screen reads the registry directly (works in SP / shared-JVM; on a dedicated server the client relies on the registry being populated). The synced payload `BestiaryDataSyncPayload` carries **player discovery progress**, not entry defs.
   - Screen `client/bestiary/gui/BestiaryScreen.java extends Screen`. Componentized enough (nested `Row` helper, panel/scroll/detail draw split). Opened client-side via `ClientScreenHooks.openBestiaryScreen()` → `Minecraft.getInstance().setScreen(new BestiaryScreen())`. Item invokes the hook reflectively to keep the client class off the server classpath.
2. **Item registration** — `registry/ModItems.java` exposes `DeferredRegister.Items ITEMS`. Custom misc items in `registry/MiscItemRegistry.java` via `ITEMS.registerItem(name, props -> new XItem(props.stacksTo(1)))`. Tab content in `registry/ModCreativeTabs.MAIN`.
3. **Screen registration** — direct `Minecraft.getInstance().setScreen(...)` from `client/network/ClientScreenHooks.java`. No `MenuType`. Confirmed.
4. **Networking** — `CustomPacketPayload` record + `StreamCodec` (`network/bestiary/BestiaryDataSyncPayload.java`). Registered in `network/bestiary/ModNetworkBestiary.register(PayloadRegistrar)`, invoked from `network/ModNetwork.register(RegisterPayloadHandlersEvent)` (registrar version "1"). Client handlers live in `client/network/ClientPayloadHandlers`.
5. **ModuleManager** — `module/Module.java` (hand-maintained enum) + `module/ModuleManager.java` (EnumMap of State). No `HANDBOOK` entry — adding one. Default will be `ENABLED`.
6. **Recipe system** — shaped/shapeless JSON under `data/wizards_and_beasts/recipes/*.json` (e.g. `bestiary.json`). `recipe/` (singular) exists only for one floo file; convention is `recipes/`.

**Dispatch codec reference**: `skill/SkillNodeEffect.java` — sealed interface + `Type` enum (StringRepresentable) + `Type.CODEC.dispatch(::type, Type::codec)` with per-variant `MapCodec`. `HandbookPage` mirrors this exactly.

**Planned deviation from Bestiary**: handbook DOES sync the full chapter list to the client (`SyncHandbookPayload`, `HandbookChapterManager.CLIENT_CHAPTERS`) per spec §1c, whereas Bestiary entry defs are never synced. Sync fires on `OnDatapackSyncEvent` (covers both join and `/reload`) rather than `PlayerLoggedInEvent`, so `/reload` re-pushes updated chapters.

## Handbook Content Audit

Pre-implementation audit for Ministry Handbook Part 2 (content JSON). No Java changes. No stop triggers hit (recipe gaps resolved via text fallback per §3).

1. **Codec field names** (`handbook/HandbookChapter.java`, `handbook/HandbookPage.java`):
   - Chapter: `id`, `title`, `icon` (optional), `sort_index`, `pages`.
   - Page `text`: `heading` (optional), `body`.
   - Page `recipe`: `recipe_id`.
   - Page `image`: `texture`, `caption` (optional).
   - Page `cross_ref`: `target_type`, `entry_id`.
   - All match the §1 assumptions — no corrections needed.
2. **Page type keys**: `text`, `recipe`, `image`, `cross_ref`. Confirmed (HandbookPage.Type enum serialized names).
3. **Data path**: `data/wizards_and_beasts/handbook/chapters/` (FileToIdConverter.json("handbook/chapters")). Confirmed.
4. **Recipe page behaviour**: structural frame + captioned recipe id only (MIGRATION_DELTAS deviation 3). JSON supplies `recipe_id` only — confirmed.
5. **Cross-ref**: renders "See Bestiary →" banner; `target_type` accepted value is `"bestiary"` (HandbookScreen checks `"bestiary".equals(ref.targetType())`). Confirmed.
6. **Item identifiers found** (icons): `wizards_and_beasts:wand`, `wizards_and_beasts:broom`, `wizards_and_beasts:galleon`, `wizards_and_beasts:ministry_handbook`, `wizards_and_beasts:enchanted_trunk` (+ `expanded_trunk`, `masters_trunk`, `moodys_trunk`, `newts_case_item`, `phoenix_feather`, `dragon_heartstring`, `unicorn_hair`, `counterfeit_galleon`, `sickle`, `knut`). All chapter icons resolve to existing ids — **no icon fallbacks used**. (Ch09 uses `wizards_and_beasts:enchanted_trunk`, a real trunk id, instead of the nonexistent `:trunk`/chest fallback.)
7. **Recipe IDs present** under `data/wizards_and_beasts/recipes/`: `bestiary`, `cleansweep_seven`, `comet_260`, `firebolt`, `firebolt_supreme`, `marauders_map`, `ministry_handbook`, `nimbus_2000`, `nimbus_2001`, `pocket_case`, `spell_teacher`.
   - **MISSING `wizards_and_beasts:wand`** → Ch03 wand recipe page replaced with a `text` fallback.
   - **MISSING `wizards_and_beasts:travellers_trunk`** → Ch09 trunk recipe page replaced with a `text` fallback.

---

## Placed Trunk Entry Mechanic — Pre-Implementation Audit (2026-07-01)

Audit-first pass for the Expanded/Master's placed-block descent mechanic. **STOP-AND-REPORT trigger hit (§5): trunks are wired as inventory-only held items; the block path would conflict.** No code written pending a migration decision.

- [OK / no STOP §1.3] Pocket dimension + per-trunk allocation **exists**: `ModDimensions.EXTENSION_REALM`; `ExtensionCharmService.enterPocket/exitPocket/getOrCreatePocket`; per-pocket plot via `computePocketSpawn(caseId)` on a 512-block grid; shell built by `PocketShellGenerator.ensurePocketShell`. Entry anchor = `TrunkRecord.spawnPos()`. Return point stored per-player in `TrunkRegistryData.saveReturnPosition/getReturnPosition/getReturnDimension`. The mechanic can proceed — nothing to build in the dimension layer.

- [BLOCKER / conflict] `TrunkRecord` (`trunk/TrunkRecord.java`) stores pocket identity/config only: `pocketId, owner, pocketName, accessMode, archetype, templateId, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, lockedExternally`. **No return point** (that lives per-player in `TrunkRegistryData`) and **no active-lock index** (that lives on the item component `MOODYS_TRUNK_ACTIVE_LOCK`). For a block path, active-lock + return anchor belong on the `TrunkBlockEntity`, so **TrunkRecord likely needs zero new fields** — confirm before adding (§5).

- [BLOCKER / conflict] All tiers are **held items that teleport on `use()`** — no placed-block entry exists:
  - `enchanted_trunk` = `EnchantedTrunkItem(TIER_1)` — 1 lock (Traveller's tier). **Currently full dimension-enter**, NOT storage-only as the design intent assumes.
  - `expanded_trunk` = `EnchantedTrunkItem(TIER_2)` — 3 locks (Expanded).
  - `masters_trunk` = `EnchantedTrunkItem(TIER_3)` — 7 locks (Master's).
  - `moodys_trunk` = `MoodysTrunkItem` — 7 locks, per-lock sub-pockets, 7th = SAFEHOUSE "pit" (already a decoy-like compartment).
  - `newts_case_item` = `NewtsCaseItem` — Scamander sanctuary (trinket). All registered in `DarkArtefactItemRegistry` / `TrinketItemRegistry`.
  The block path for Expanded/Master's would duplicate `EnchantedTrunkItem.use()`. A migration decision is required (replace vs. add).

- [REFERENCE] BlockEntity pattern to mirror: `ExpansionFocusBlockEntity` (`block/ExpansionFocusBlockEntity.java`) + `PocketConfiguratorBlock` (`BaseEntityBlock`, `saveAdditional/loadAdditional` via ValueInput/Output, `setPlacedBy` binding pocket-at-pos). Registered in `ModBlockEntities.POCKET_CONFIGURATOR`.

- [REFERENCE] Block + BlockItem registration: `ModBlocks.registerBlock(...)` + `ModItems.ITEMS.registerSimpleBlockItem(...)` (see `POCKET_CONFIGURATOR` / `WARDING_STONE`). Directional facing not yet used by any mod block — would add `HorizontalDirectionalBlock` + `FACING` state.

- [REFERENCE] Packed DataComponent to mirror: `WandComponents.WAND_CONFIGURATION` (Codec-backed, item-attached). Existing pocket components (`POCKET_CASE_ID`, `POCKET_ID`, `POCKET_ARCHETYPE`, `MOODYS_TRUNK_ACTIVE_LOCK`, `MOODYS_TRUNK_BASE_ID`, etc.) in `ModDataComponents` already carry the trunk reference on the item — a BlockItem can reuse `POCKET_CASE_ID`/`POCKET_ID` to survive pack-up with zero content loss (case→pocket binding persists in `TrunkRegistryData.caseBindings`).

- [OK §4] Gating constant confirmed: `Module.POCKET_DIMENSIONS` (state ENABLED). Pattern: `if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return PASS/FAIL;` at top of `use()`. Gate entry + lock-cycle; block placement itself can stay ungated.

- [OK §1.8] Traveller's storage-only path: **does not exist as storage-only**. `enchanted_trunk` (TIER_1) currently enters the dimension like the others. Left untouched per §3; flagged here only.

### Resolution (2026-07-01)
STOP trigger addressed under Upgrade License decisions (see MIGRATION_DELTAS.md → "Placed Trunk Entry Mechanic"): Expanded/Master's/Moody's converted to placed `TrunkBlock` + `TrunkBlockEntity` (BlockItems keep ids); Traveller's held item untouched; `TrunkRecord` unchanged (no schema additions needed). `compileJava` green.

### Resolution follow-up (2026-07-01)
Travellers (enchanted_trunk) no longer a held reach-in item — converted to a placed TrunkBlock alongside Newts Case. No held trunk-likes remain. See MIGRATION_DELTAS addendum.

---

## Full Audit 2026-07-06 (read-only sweep — see FULL_AUDIT_REPORT.md for evidence)

### BLOCKER
- [x] **AUD-D-001** Wandmaking recipes never load: move 6 JSONs from `wandmaking_recipes/` → `recipe/` AND add `"type": "wizards_and_beasts:wandmaking"` to each. Wandmaker's Bench currently can never output a wand (`WandmakersBenchMenu.findRecipe` finds nothing).

### BROKEN
- [x] **AUD-E-001** Protego recast self-shatter: `Protego.executeCast` adds the new PROTEGO_SHIELD effect (L57) *before* `shatterExistingIfPresent` (L58); old shield's `beginShatter` removes the caster's effect (ProtegoShieldEntity:229-231) → new shield dies next tick. Reorder (shatter first, then add effect + spawn). Root cause of "shield not spawning".
- [x] **AUD-D-003** Move `tags/items/` (plural, dead in 1.21) → `tags/item/`: `broom_repair_material_elite.json`, `broom_repair_material_racing.json`, `brooms.json`; delete stale `cannot_conjure/` duplicates. Broom repairMaterial tags currently empty.
- [x] **AUD-F-001** Gate `WandBeamSpellHandlers.handleAvada` (and/or `WandBeamChannelLogic` BEAM_LETHAL branch) on `Module.DARK_ARTS` — Avada kills while the module is DISABLED; Crucio/Imperio are gated.
- [x] **AUD-D-002** Brew loaders scan MODID-prefixed dirs (`data/wizards_and_beasts/wizards_and_beasts/brews|brewing_recipes`) but JSONs are un-nested → never load. Fix DIRECTORY constants or move files.
- [x] **AUD-C-001** `WandTipWorldCache.WAND_TIP_BONE="wand_tip"` — bone doesn't exist in master wand.geo (only elder skin). Beam origin always uses fallback math. Point at an existing anchor (e.g. `fx_tip_anchor`) or add the bone.
- [x] **AUD-D-004** `pocket_templates/` had no loader (closed by the 2026-07-06 evening pass: minimal loader shipped, 3 templates boot-verified).
- [x] **AUD-F-002** Spell learning has no DARK_ARTS gate — dark spells learnable while module disabled (Likely; verify UI path first).

### POLISH
- [x] **AUD-C-002** `WandTipWorldCache` never cleared → stale beam anchor after wand switch. Clear per-frame or on item change.
- [ ] **AUD-F-004** Vocation opposition lockout is unlock-time only; skills unlocked pre-commit are grandfathered. Needs design ruling ± commit-time revalidation.
- [x] **AUD-G-004** `SpellCastC2SPayload.IGNORE_RELEASE_UNTIL_GAME_TICK` — add logout cleanup (same family as fixed Imperio leak).
- [x] **AUD-E-002** `ProtegoWardManager.CASTER_TO_ENTITY` — add logout cleanup.
- [x] **AUD-G-005** `ElfAbilityHandler.pullNearbyItems` — throttle (only unthrottled per-tick entity scan left).
- [x] **AUD-F-005** `ModBlocks.java:245` stale TODO — STRUCTURES module exists; apply the gate.
- [x] **AUD-B-007/008** (closed by the 2026-07-06 evening pass: 52 javax imports -> JSpecify, 22 null-returning methods annotated) Nullability: migrate ~30 jetbrains/javax annotation files to JSpecify; annotate the 19 `return null` files with zero `@Nullable` (top: BestiaryScreen, MirrorSessionManager, ColoredGlowRenderer, Brew*Definition).

### NICE-TO-HAVE
- [x] **AUD-C-005** `WandModel.getAnimationResource` returns null — latent NPE if a controller is ever added to WandItem.
- [x] **AUD-G-006** Registry reload swap: adopt volatile immutable-map swap (pattern already in `BestiaryEntryRegistry.CLIENT_ENTRIES`) instead of clear+putAll.
- [x] **AUD-A-003** TODO inventory: down to 9 sites, all future-work markers. Both named wiring gaps closed by the evening pass (HappinessSpellPower hook; protego audio remaps).
- [ ] **AUD-C-007** WandRenderer "flat siblings" comment misdescribes geo (variants are children of container bones).

### Fix pass 2026-07-06 (same day)
All BLOCKER/BROKEN + most POLISH items above fixed and boot-verified (dedicated server: brews 1+1 loaded, 26 spells, 86 creatures, 0 tag failures, wandmaking recipes in RecipeManager — `isSpecial()` added to silence placement warning; MC source confirms recipes stay in RecipeMap).
Deliberately NOT fixed (need design ruling): AUD-D-004 pocket_templates loader-or-delete, AUD-F-004 vocation opposition grandfathering, AUD-G-001 carried_niffler copyOnDeath intent, AUD-B-007/008 nullability sweep (mechanical, large diff).

### Follow-up pass 2026-07-06 (evening)
- [x] **AUD-D-004** pocket_templates: minimal loader shipped (PocketTemplate codec + registry + reload listener, maxRadius caps pocket creation; synthetic trunk_lock_N/trunk_decoy ids pass through). Boot-verified: 3 templates load.
- [x] **AUD-B-007/008** nullability sweep: 52 javax imports → JSpecify; 22 null-returning methods across 18 files annotated.
- [x] Niffler TODO(effects): happiness ≥80 → up to +10% spell damage via ModifierStack hook (HappinessSpellPower).
- [x] Audio: protego_totalum_raise event-name-as-file-path fixed; 4 stale TODO(audio) markers retired (vanilla remaps ship).
Still open (design): AUD-F-004 vocation grandfathering, AUD-G-001 carried_niffler copyOnDeath.

## Addendum (2026-07-09, skill Java→datapack migration): found-but-untouched

- [NICE-TO-HAVE] Skill node `displayName`/`description` are inline English strings, not lang keys — ported verbatim into the new `skill_nodes/` JSONs per fidelity spec. Skill names/descriptions remain non-localizable (pre-existing; `SkillTreeId` display names share the problem).
- [NICE-TO-HAVE] `SkillsTab` class doc says "5 tree bars" but it iterates all 8 `SkillTreeId` values (doc drift, pre-existing). `src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java:20`.
- [NICE-TO-HAVE] `PlayerSkillData.resetAll`/`resetSkill` refund `pointCost × level` only for ids that resolve — an allocation whose definition is missing (now reachable: datapack removed the node) refunds 0 and stays in NBT. Points are stranded until the node returns or an admin `points set`s. Pre-existing arithmetic, newly reachable.

## Addendum (2026-07-09, skill web rework Phase 2): found-but-untouched

- [NICE-TO-HAVE] Goblin (5 nodes, ~7 SP total sink) and elf (5 nodes, ~7 SP) webs have far fewer point sinks than the 60-point cap — goblin/elf players will cap out with nothing to spend on. Pre-existing content gap, now more visible under the cap; Phase 3 filler nodes are the intended fix.
- [NICE-TO-HAVE] `/wandb skill points set` writes unspent points directly without touching `totalPointsEarned`, so an admin can set unspent above earned and spending beyond earned becomes possible. Pre-existing admin override; the earn path (`addSkillPoints`) enforces the cap.
- [NICE-TO-HAVE] `SkillTreeGuiTextures` + the skill-node PNG set are no longer referenced by the canvas (plain shapes per Phase 2 spec). Kept for the Phase 3 star-chart pass; delete then if unused.

## Known-Bug re-verification 2026-07-19 (BUG-1 … BUG-5)

Task: fix 5 named bugs. Pre-fix audit found **all five already resolved at HEAD `00fc42d`** (independently verified against source, not just trusting prior audit docs). These duplicate the Pass-E findings in `FULL_AUDIT_REPORT.md` (AUD-E-001/003/004/005/006). No code changes made — fabricating diffs for non-bugs is out of scope. Diagnoses:

- [x] **BUG-1** Protego shield not spawning — **already fixed** (was AUD-E-001, recast self-shatter). Diagnosis: `Protego` is a registered bespoke Java `Spell` (`Spells.java:55`); every wand cast reaches `SpellExecutor.executeCast` → `spell.executeCast(ctx, level)` (`SpellExecutor.java:97`) → `Protego.executeCast` override. The spawn path IS reachable: `executeCast` (`Protego.java:60-66`) shatters any existing ward FIRST (`shatterExistingIfPresent`, L60), THEN applies the `PROTEGO_SHIELD` MobEffect (L61), THEN spawns `ProtegoShieldEntity` server-side (L62-63) and registers it in `ProtegoWardManager` (L64) so the entity is slaved to the effect's lifetime. The historical symptom was recast-only: the old code added the new effect before shattering, so the old shield's `beginShatter` stripped the fresh effect and the new shield self-died next tick. The reorder fix + explanatory comment (L57-59) are present. All 4 tiers resolve via `ProtegoProjectileHandler.resolveTier`. No routing mismatch. **No change.**
- [x] **BUG-2** GampsLaw.validate NPE — **already fixed** (was AUD-E-003). Diagnosis: `validate` (`GampsLaw.java:28`) null-guards `ctx.definition()` (L30-32) and null/empty-guards `produced` at L41 (`if (produced != null && !produced.isEmpty())`) before any `is(tag)` call, so productless (non-conjuration) spells fall through to the trivial ACCEPT (`return null`, L56). The upstream `SpellDefinition.previewProducedItem` already carries JSpecify `@Nullable` on its return (`SpellDefinition.java:405`) and the base impl returns null. The prompt's "add `@Nullable` to the produced parameter" is already satisfied at the only legitimate site (there is no `produced` *parameter*; it is a local). No caller structurally guarantees null where a value is required. **No change.**
- [x] **BUG-3** Broom `acceleration` codec range too strict — **already fixed** (was AUD-E-004). Diagnosis: range is `[0.005, 0.15]` (`BroomDefinition.java:82`, `readRangedFloat`). Shipped values audited across all broom JSONs: cleansweep_seven 0.050, comet_260 0.065, firebolt 0.100, firebolt_supreme 0.110, nimbus_2000 0.080, nimbus_2001 0.082, oakshaft_79 0.048 — 7 broom files total (the prompt's "7 + Oakshaft = 8" miscounts; oakshaft_79 IS one of the 7). All values inside the range with headroom. No other physics field audited or touched. No `MIGRATION_DELTAS.md` entry needed (no schema change). **No change.**
- [x] **BUG-4** Missing `niffler_shiny` trim tag — **already present / never broken** (was AUD-E-005). Diagnosis: `tags/item/niffler_shiny.json` exists (13 shiny items: gold/diamond/emerald/amethyst/coins tag/copper/iron/netherite/lapis/quartz/redstone) and matches `NifflerSeekShinyItemGoal.java:23` (`wizards_and_beasts:niffler_shiny`). Sibling `tags/block/niffler_shiny_blocks.json` matches `NifflerSeekShinyBlockGoal.java:31`. "trim tag" is a red herring — **zero** references to `minecraft:trimmable_armor_materials` anywhere in src. No Niffler code/asset touched (WIP hard constraint respected). **No change.**
- [x] **BUG-5** Incomplete `floo_powder.json` recipe — **already complete** (was AUD-E-006). Diagnosis: the recipe ships via datagen at `src/generated/resources/data/wizards_and_beasts/recipe/floo_powder.json` — a valid `crafting_shapeless` (blaze_powder + gray_dye + glowstone_dust → 8× floo_powder). No `recipe/floo_powder.json` in `src/main/resources`. No code expects additional floo-powder recipe IDs — the only Java references are the item registration (`MiscItemRegistry.java:39`) and lang keys (`ModLanguageProvider.java:298-299`). The `ingredients` list is complete. **No change.**

### Housekeeping (same session, user-requested)
- Committed Gradle wrapper bump 8.8 → 8.14.5 (was dirty in tree at task start).
- `.gitignore`: ignore all dot-directories (`.*/`) with `!.github/`/`!.vscode/` exceptions (tracked); `.codex/` now ignored.
- [NICE-TO-HAVE] `WizardsAndBeastsUiTokens.SkillTree` still carries grid-era constants (NODE_X_SPACING, TAB_*, GRID_*, PAN_MIN/MAX) now unused by the canvas. Harmless dead tokens; sweep in Phase 3.

## Addendum (2026-07-10, vocation reframe Phase 3): found-but-untouched

- [NICE-TO-HAVE] `VocationDefinition.capstoneNodeId` is orphaned data — its only reader (the capstone gate in the deleted `unlockState`) is gone. No shipped vocation JSON sets it. Keep for the in-region-bonus prompt or delete then.
- [NICE-TO-HAVE] `commitmentEffects` + `grantedAbilities` still confer gameplay on declaration (wandlore +0.1 wand_affinity; ability flags via `VocationAbilityHooks`) — at odds with "identity only, zero gameplay effect" but outside this prompt's deletion checklist. Needs an explicit ruling.
- [NICE-TO-HAVE] The shipped opposition data (now deleted from JSONs) contained exactly one pair: `dark_arts ↔ healer`, and `healer` was never a shipped vocation — the pair was always inert. Recorded as Phase 4 layout design input (dark arts vs. restoration should sit far apart on the web).

## Addendum (2026-07-10, wizard web content Phase 4): found-but-untouched

- [POLISH] spell_mastery (Orion) full clear from Polaris = 59 SP of the 60 cap — technically legal but a knife-edge; one more notable or filler in Orion breaks the cap. Design review before Phase 5 content lands there.
- [NICE-TO-HAVE] Filler/Polaris display names are lang keys rendered raw by the canvas until Phase 5 adds translation at draw time (single Component.translatable call in the tooltip/pips path).
- [NICE-TO-HAVE] unlock_ability notables (e.g. basic_casting, green_thumb, creature_knowledge) have no scalable magnitude — fillers in those regions derive from the region's other stat types instead; if a future region ships ONLY ability unlocks, the §3.3 derivation rule has no input (stop condition documented in the Phase 4 prompt).
- [NICE-TO-HAVE] Goblin/elf webs remain 5 nodes each vs the 60 cap (pre-existing gap, restated).

## Addendum (2026-07-10, star-chart skin Phase 5): found-but-untouched

- [POLISH] Grid-era `WizardsAndBeastsUiTokens.SkillTree` constants (NODE_X_SPACING, TAB_*, GRID_*, PAN_MIN/MAX, VIEWPORT_BG, NODE_*) and the Phase 2 `SkillTreeGuiTextures` class + its node PNG set are now fully orphaned — flag for a deletion pass.
- [NICE-TO-HAVE] Server-side chat feedback (`/wandb skill unlock/info/list`) prints filler display names as raw lang keys — server can't resolve client lang; fix is switching ChatHelper messages to translatable Components (separate pass, touches command text conventions).
- [NICE-TO-HAVE] `SkillAccessDeniedScreen` and `SkillScreenRouter` still use the old plain look — cohesive but not chart-styled; cosmetic only.

## Addendum (2026-07-18, skill audience & access): design flags + found-but-untouched

- [NICE-TO-HAVE, design-decision-pending] **Obscurial `canUseWand=false` deviates from film canon.** A
  wand-wielding Obscurial appears in later *Fantastic Beasts* films, so gating obscurials out of the
  wand region (Sagitta) is a deliberate mechanical choice, not a lore fact. Recorded for the obscurial
  content era. Changed nothing — the audience/access prompt only grants access, it does not touch
  heritage kit or the `canUseWand` flag.
- [NICE-TO-HAVE] **No-heritage crafted-payload access is unchanged (pre-existing).** A player with no
  heritage still resolves to the historical `WIZARD` default in `audienceForHeritage(null, null)`, so a
  crafted `SkillUnlockC2SPayload` could allocate `NONE`-requirement wizard regions before selecting a
  heritage — exactly as before this change (the old default fallthrough did the same; wandlore was and
  is blocked by the `WAND` requirement). Out of scope here (heritage-selected gating in `evaluateUnlock`
  is a separate concern); restated so it isn't mistaken for a regression.
- [NICE-TO-HAVE] **`no_casting` is now a live capability tag** (squib + both obscurial variants). The
  other dead tags (`innate_apparition`, `water_breathing`, etc.) remain punchlisted and untouched per
  the prompt's hard scope.
- [POLISH] **Sealed-region tooltip flavor text is a placeholder.** `skilltree.region.sealed.tooltip`
  renders its raw key (fallback `"Sealed"`) until someone authors the lang entry — intentionally not
  written here (the tangled `en_us.json` is off-limits, and flavor authorship is out of scope).

## Addendum (2026-07-18, ability sources infrastructure): dead tags + found-but-untouched

- [NICE-TO-HAVE] **Dead heritage tags, now exposed but still dead.** `can_apparate`
  (`ApparitionServerLogic:147` — checked, but **no** variant carries it, so it is always false; only
  `WIZARDKIND` apparates) and `water_breathing` (carried by the 3 merpeople variants but consumed by
  nothing — real water-breathing moved to spell `clear_fire`/`apply_effect` JSON components per
  `SpellCastUtilityHandler:45`). The heritage adapter exposes them as `HERITAGE`-source grants trivially
  (the full variant tag set is exposed), but nothing reads them — they **stay dead**, not revived, per
  the prompt's hard scope. Reviving `can_apparate` (wiring a variant to it) is a deliberate future call.
- [NICE-TO-HAVE] **Stored `PlayerAbilityData.abilityFlags` imperative bag — found, untouched.** A
  pre-existing **stored** ability-flag set on the `PLAYER_ABILITY_DATA` attachment, mutated imperatively
  via `PlayerAbilityHelper.add/removeAbilityFlag`. This is the exact stored/revoke-unsafe anti-pattern the
  new derived layer supersedes, but it is a separate, persisted system (alongside apparition/animagus/
  occlumency state) — deriving it would be a migration, out of scope here (§3.3, §5.5). Candidate for a
  future "derive-and-delete" pass once its writers are enumerated.
- [NICE-TO-HAVE] **`expecto_patronum_unlock` is a misnomer.** The node id says "unlock" but its only
  effect is a Patronus cooldown reduction; it gates no spell. If a Patronus *unlock* is intended, it needs
  an actual `grant_ability`/`learn_spell` effect or a spell-requirement edge — currently neither.

---

# §0 audit — Unified Ability Framework (2026-07-19)

Pre-implementation audit for the slots/wheel/keybinds framework. Every §0 stop-condition + §6 halt trigger
fired; halted and got direction (extend existing layer; leave prompted-ability keybinds untouched) before
building. Findings:

- [BLOCKER→RESOLVED] **Pre-existing `ability/` layer overlaps the spec.** `ability/data/PlayerAbilityData`
  (name the prompt wanted, different semantics), `ability/grant/*` (derived source-tracked grant layer =
  the spec's resolver), `network/ability/AbilityDataSyncPayload` + `network/skill/AbilityGrantsSyncS2CPayload`
  + client mirrors already shipped (commit 8190df7). Resolution: reused the grant layer as the resolver;
  new selection state under a non-colliding name. See MIGRATION_DELTAS.md.
- [BLOCKER→RESOLVED] **Prompted abilities already have keybinds.** Apparition(`APPARATE`/R),
  Legilimency(`LEGILIMENCY`/H), Animagus(`ANIMAGUS_TRANSFORM`/`ANIMAGUS_ABILITY`), Obscurial(*) live in
  `SpellKeyBindings` with services. Per §0 "do NOT migrate or delete — report and halt": left untouched;
  the three new keybinds default UNBOUND so they never collide.
- [POLISH] **ModuleManager is 3-state** (`DISABLED/ENABLED/PREVIEW`), not the prompt's 4-state
  (`ENABLED/PREVIEW/COMING_SOON/DISABLED`). `isEnabled()` treats PREVIEW as usable; the resolver's module
  gate matches that. Relevant module `PLAYER_ABILITIES` is `PREVIEW`.
- [POLISH] **Registry sync precedent:** BroomDefinition is server-only (never synced); the wheel needs
  client metadata, so ability definitions ARE client-synced (handbook/skill precedent), a deliberate
  deviation from the Broom pattern.
- [NICE-TO-HAVE] **Wheel usable-set freshness:** the wheel-state sync fires on login/respawn/mutation and
  on the existing grant-sync seam; a grant change with no wheel mutation could be one relog stale. Fine for
  a framework with no real abilities yet; revisit when behaviors land.

# §0 audit — Ability Migration (Apparition / Legilimency / Animagus → framework) (2026-07-19)

> **RESOLVED — historical record.** All four stop conditions were resolved (see "Post-implementation"
> below and the follow-up passes). The one finding that is still live — Legilimency being ungrantable —
> is promoted to "Open findings" at the top of this file.

Pre-implementation audit. **HALTED — four §0 stop-and-report conditions fired.** No code written.

## Stop conditions

- [BLOCKER] **Apparition input model is hold-to-charge + look-target picking — the framework cannot
  express it.** `client/apparition/ApparitionClientController` holds `APPARATE` for `CHARGE_TICKS = 20`,
  re-picks a target every tick after the charge completes (`player.pick(32.0)`, block-hit or 32-block
  look-ray fallback), renders a live 24-particle target ring coloured by ward state + unlock state
  (`onRenderLevel`), and fires `ApparitionRequestPayload(targetBlockPos, targetPos)` **on key release**.
  The framework's use/quick keys are press→`AbilityTriggerHandler.use(player, quick)` with **no payload**:
  no charge, no target, no preview. Framework-triggered Apparition would have nowhere to send a destination.
- [BLOCKER] **Legilimency input model is hold-to-charge + entity targeting.**
  `client/legilimency/LegilimencyClientController` requires `HOLD_TICKS = 30` of held `LEGILIMENCY`, then
  `player.pick(8.0)` for an `EntityHitResult` and sends `LegilimencyRequestPayload(entityId)`, once per hold.
  Same gap: the framework trigger carries no target entity and no hold duration.
- [BLOCKER] **Animagus toggle state would be duplicated.** Truth lives in the persistent
  `PlayerAbilityHelper.isCurrentlyTransformed(player)` (legacy `ability.data.PlayerAbilityData`), and is read
  by `AnimagusEvents` (item-use lock, block-interact lock, tick passives, fall immunity), `FormHitboxHandler`,
  the client renderers, `/wandb animagus`, and `forceRevert` on death. `AbilityTriggerHandler.flipToggle`
  writes `AbilitySelectionState.toggles` **unconditionally before** dispatching `onToggle` and persists it in
  the `ability_selection` attachment — a second, independently-persisted copy that desyncs on every non-wheel
  transform path (death force-revert, `/wandb animagus transform`, transition rejection, `revert()`).
  Single-source-of-truth cannot be preserved without a framework change (see options below).
- [BLOCKER] **Heritage tag→ability mapping is not a working 1:1 id mapping — it currently grants nothing.**
  `HeritageAbilityGrantSource` returns `HeritageVariant.getTags()` verbatim; `AbilityGrants.add` wraps each in
  `AbilityKey.of(raw)` = `trim().toLowerCase()` (no namespacing); `AbilityResolver.keyOf(def)` =
  `AbilityKey.of(def.id().toString())` = the **namespaced** string (`wizards_and_beasts:apparition`). Every
  shipped tag is bare (`enhanced_bond`, `innate_apparition`, `transformation`, `no_wand`, …), so **no heritage
  tag matches any ability id today**. The tag path is inert; there is nothing to migrate off, only to delete.

## Other findings

- [BLOCKER] **Heritage has no codec and no JSONs.** §2.1 assumes a heritage definition codec + shipped
  heritage JSONs. `HeritageVariant` is a hardcoded Java `enum` (31 constants, `Set<String> tags` in the
  constructor); `Heritage` likewise. There is no `data/**/heritage/*.json`. The §2.1 deliverable is not
  executable as written.
- [BLOCKER] **Actual gating for the three is not heritage-tag-shaped**, so it cannot be expressed as an
  `AbilityGrantSource` without either duplicating the checks or moving them:
  - *Apparition* (`ApparitionServerLogic.handleRequest`): `Module.PLAYER_ABILITIES` **and**
    (`SkillSystemAPI.hasAbility("elf_apparition")` **or** (`apparitionUnlocked` ∧ `apparitionLicensed` ∧
    (heritage == `WIZARDKIND` ∨ variant tag `can_apparate`))); plus splinch-severity gate, a bespoke
    per-player cooldown (`PlayerAbilityHelper.getApparitionCooldownTicks`, ticked by `ApparitionEvents`,
    length `Config.apparitionCooldownTicks`), and ward check at destination.
  - *Legilimency* (`LegilimencyServerLogic.canLegilimise`): `Module.PLAYER_ABILITIES` ∧ heritage ==
    `WIZARDKIND` ∧ (variant == null ∨ tag `can_legilimise`); bespoke 600-tick cooldown via
    `PlayerAbilityHelper.getLegilimencyCooldownTicks`.
  - *Animagus* (`AnimagusTransformService`): `Module.PLAYER_ABILITIES` ∧ `animagusUnlocked` (mandrake-leaf
    thunderstorm ritual in `AnimagusEvents`) ∧ a chosen `animagusFormId`; the ritual itself additionally
    requires skill ability `animagus`. No cooldown.
- [BLOCKER] **Neither `can_apparate` nor `can_legilimise` exists on any `HeritageVariant`.** Consequence at
  HEAD: non-WIZARDKIND heritages can never Apparate, and — because every wizard has a non-null variant —
  `canLegilimise` returns `variant.hasTag("can_legilimise")` = **false for every player**. Legilimency is
  dead code at HEAD. Pre-existing; fixing it is a gameplay change and out of this prompt's scope, but any
  grant source mirroring current gating would grant Legilimency to nobody.
- [POLISH] **There is a fourth keybind, not three.** `SpellKeyBindings.ANIMAGUS_ABILITY` fires
  `AnimagusAbilityService.useActive(player, formId)` — the per-form active burst (pounce/bark/charge/flap/
  bound/vanish, own `COOLDOWN` map). The prompt's §2.4 "delete the three dedicated keybinds" does not name
  it. Deleting `ANIMAGUS_TRANSFORM` without a plan for `ANIMAGUS_ABILITY` leaves a dangling bind; migrating
  it needs a fourth ability definition that §3 forbids inventing.
- [POLISH] **Client affordances lost if the binds are deleted as specced**: Apparition's charge-up target
  ring + ward/unlock colour feedback (`ApparitionClientController.onRenderLevel`), and the client-side
  `ClientAbilityCache.get().apparitionUnlocked()` pre-send gate. The framework wheel has no equivalent.
- [NICE-TO-HAVE] **Cooldowns would double up.** Both abilities already own bespoke persisted cooldowns; the
  framework applies `AbilityDefinition.cooldownTicks` on top. §2.2 says "0 if none today; do not invent one"
  — so both definitions must ship `cooldownTicks: 0` and let the legacy cooldown remain authoritative.
- [NICE-TO-HAVE] **Test surface is thin.** Only `src/test/.../ability/grant/AbilityGrantsTest.java` exists;
  there are no existing tests for Apparition/Legilimency/Animagus server logic to keep green (§2.5 bullet 1
  is vacuous), and all three server entry points are `static` on final classes — the §2.5 "mock/verify at the
  adapter seam" test needs the seam to be an injectable indirection, not a direct static call.

## Post-implementation (same day, after direction: all four stops resolved via option (a))

- [POLISH] **`ANIMAGUS_ABILITY` bind still stands alone.** The per-form beast burst
  (`AnimagusAbilityService.useActive`) keeps its keybind and its own C2S payload; §3 forbids inventing a
  fourth ability definition for it. Follow-up: give it an ability definition (ACTIVE, no target) and fold
  the bind in.
- [POLISH] **`AbilityGrantsSyncS2CPayload` has no `STATUS` bucket** and its `HERITAGE` bucket is now always
  empty. Harmless today — `ClientAbilityGrantState` has no consumers beyond the payload handler, and the
  wheel gets its usable set from `AbilitySelectionSyncS2CPayload` instead. Revisit when something reads the
  client grant mirror.
- [POLISH] **Charge duration is client-trusted.** The server cannot observe hold time, so `chargeTicks` is a
  client-side input contract only; a modified client can skip the charge. It bypasses no gameplay check —
  grant, module, cooldown, target kind and range are all re-validated server-side — so this is a feel
  exploit, not a permission one.
- [NICE-TO-HAVE] **Legilimency is still granted to nobody.** `canLegilimise` requires the variant tag
  `can_legilimise`, which no `HeritageVariant` carries (pre-existing, documented above). The migration
  preserves that exactly, so the ability will not appear in the wheel until the tag question is settled —
  a gameplay decision, deliberately out of scope here.
- [RESOLVED 2026-07-25] ~~**`AssetModelParityTest` fails at HEAD** (9 beast item definitions from commit
  `00fc42d` reference models that were never generated).~~ Fixed by `9e46f38` (see the 2026-07-19 entry
  above). Re-verified on a clean checkout 2026-07-25: `AssetModelParityTest` **passes** and the full suite
  is green. The belief that the uncommitted work tangle blocks the build is stale — `./gradlew test`,
  `runData` and `build` all succeed with the tangle in place. Do not plan around it.

## Pass 2 — Obscurial + Animagus beast ability onto the wheel, quick slots (2026-07-19)

- [POLISH→RESOLVED] The `ANIMAGUS_ABILITY` dangling-bind item above is fixed: it is now
  `wizards_and_beasts:animagus_beast_ability` on the wheel.
- [POLISH] **Wheel ergonomics for form-scoped abilities.** Entering obscurus form changes the wheel contents
  (Surge/Grasp appear, stress vent disappears). Quick-slot bindings are kept by id, so a slot holding Surge
  simply goes inert out of form and works again on re-entry — but the wheel gives no hint about *why* an
  entry vanished. Consider showing form-gated entries greyed instead of hidden.
- [POLISH→RESOLVED] **Framework keys ship unbound.** Fixed by giving them defaults drawn from the keys the
  migration freed, each keeping roughly its old meaning: wheel `V` (was the obscurus form toggle), use `R`
  (was Apparate), quick slots `N`/`M`/`B` (were the two instant Obscurial attacks and the stress vent).
  Verified against every other `KeyMapping` in the mod (`SpellKeyBindings`: arrows/`G`/`K`;
  `DebugKeyBindings`: `F6`–`F8`) and against vanilla — no collisions. `DebugInputHandler` polls `R` raw, but
  only while the F6 model editor is active, which already overlaps the arrow spell binds; dev-only, ignored.
- [NICE-TO-HAVE] **Defaults do not reach clients that already ran the previous build.** `ability_wheel` and
  `ability_use` existed there as unbound, so those clients have `key.keyboard.unknown` persisted in
  `options.txt` and keep it — Minecraft only applies a default for a key name the options file has never
  seen. The three `ability_quick_*` names are new and do pick theirs up. Fix is one "Reset" click in
  Controls, or deleting the two lines; not worth an options migration.
- [NICE-TO-HAVE] **`ability_quick_1..3` replaced `ability_quick`.** Anyone who had bound the old key loses
  that binding (different translation key); the in-game Controls screen shows the three new entries unbound.
  Options-file migration was judged not worth it while the keys default unbound anyway.

## Wheel input / beams / Apparition destinations (2026-07-19)

- [POLISH] **Beam origin still unverified.** The other three beam complaints (endpoint, jitter, glow) had
  identifiable causes in source and are fixed; the origin resolves either from the GeckoLib `fx_tip_anchor`
  bone cache or, when that is stale, from hardcoded first/third-person hand offsets in
  `WandBeamRenderer.wandTipWorldPos`. Which path is wrong - and whether the cached bone position is written
  in the right space by `WandRenderer` - cannot be determined without watching it in game. Needs an in-game
  pass in both perspectives, with and without the elder-wand skin (different tip bone).
- [POLISH] **Apparition destination balance is a first guess.** Range is uncapped within a dimension, and
  the only brake is splinch risk scaling to 3x over ~900 blocks past the aimed-hop range, plus the existing
  shared Apparition cooldown. If cross-map hops feel too cheap, the knobs are `DISTANCE_RISK_BLOCKS` /
  `MAX_DISTANCE_RISK` in `ApparitionServerLogic`, or a hard distance cap.
- [NICE-TO-HAVE] **Marking a destination is command-only** (`/wandb apparate mark <name>`). An in-world
  affordance - a "mark this spot" button on the selector, or a placeable marker item - would fit better
  than typing a command.
- [NICE-TO-HAVE] **Selector shows dimension reachability only.** It greys out points in other worlds, which
  is the one rule the client can honestly evaluate. Ward blocks, splinch state and cooldown are only
  discovered on arrival attempt; surfacing them would need extra state on the client.

## Per-player state leak class (2026-07-19, `9e46f38`)

- [x] **`PlayerScopedState` introduced.** ~20 classes held `static Map<UUID, …>` per-player state; only five
  dropped their entry on logout, so the rest retained one entry per player who had ever joined — unbounded
  growth on a long-running server. Previous audits fixed these one class at a time (AUD-G-004 Imperio,
  AUD-E-002 Protego, the niffler handler), which is the signature of a missing abstraction. Instances now
  self-register and one listener clears the leaving player from all of them.
- [x] Migrated: wand beam sessions, form transitions, wand cast hold ticks, floo calls.
- [ ] **Not yet migrated**, deliberately: client-side holders (`WetShakeTracker`, `SizeLerpTracker`,
  `ClientFormDataState`, `DebugWandState`) — `PlayerLoggedOutEvent` never fires client-side, so the util
  would never prune them; an entry per session there is harmless. `LumosFieldEffect` and `DisarmLogState`
  need a check that their key is always a player rather than an arbitrary entity before migrating.
- [ ] **Floo calls now end silently on logout.** Previously the session leaked instead; dropping it is
  strictly better, but the other party is not told the caller vanished. Worth a proper hangup.

# §0 audit — Module State Backend (Prompt A) (2026-07-19)

**Initially HALTED on §4 stop condition #1; resolved on direction ("Go") and implemented.** Findings below
in the prompt's audit order; resolutions marked inline.

## Stop condition

- [BLOCKER→RESOLVED] **`ModuleState` did not exist and the state enum was 3-state, not 4.** There is no
  `ModuleState.java`; states are a nested `ModuleManager.State` with exactly `DISABLED, ENABLED, PREVIEW`.
  **`COMING_SOON` is absent.** The prompt's §0.1 says to stop and report in exactly this case, and it is
  load-bearing for §1.5 (transitions to/from `COMING_SOON` must be rejected even for ops) and §1.6.
  `src/main/java/at/koopro/wizardsandbeasts/module/ModuleManager.java:11–15`.
  **Resolved:** top-level `ModuleState` added with `COMING_SOON`; inert at every gate (`grantsAccess()` false,
  as for `DISABLED`) so no call site moved, and refused by the mutation path in either direction. The old
  nested `ModuleManager.State` is kept `@Deprecated` with a bridge so existing references still compile.

## 1. ModuleManager as it stands

- State lives in a `private static final EnumMap<Module, State> STATES`, populated in a static initialiser —
  hardcoded defaults, no persistence, no per-world scoping, mutable at runtime via `setState`.
- Public API is exactly three methods: `setState(Module, State)`, `isEnabled(Module)`, `isPreview(Module)`.
  `isEnabled` returns true for **both** `ENABLED` and `PREVIEW`.
- Gating call sites: **128 calls across 82 files**, of which **11 are client-side**. All go through
  `isEnabled`/`isPreview`; only one caller mutates (`ModuleCommands:76`).
- [POLISH] `STATES` is not exhaustive over `Module`. Modules absent from the map fall through
  `getOrDefault(module, DISABLED)`. `MINISTRY` (added earlier today) and `CHAMBER_OF_SECRETS` handling
  differ in kind — one is explicit, one implicit. A seed built from this map must decide explicitly.
- [POLISH] The client currently reads the *same static map* as the server. Under this prompt's design the
  client becomes a sync-fed mirror, so those 11 client call sites change from "always correct locally" to
  "correct once the join sync has arrived". Anything reading them before sync would see `DISABLED`.

## 2. World-level persistence — pattern exists, matched

`SavedData` is the established pattern (7 users). Canonical example `FlooNetworkManager`:
`extends SavedData`, `public static final SavedDataType<T> TYPE = new SavedDataType<>(MODID + "_name",
Factory::new, RecordCodecBuilder…)`, accessed via
`level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE)` — already overworld-pinned, exactly
the scoping §1.1 asks for. Mutators call `setDirty()`. No level-scoped `AttachmentType` exists anywhere, so
`SavedData` is unambiguous.

## 3. Sync — pattern exists, matched

`network/bestiary/` (`BestiaryDataSyncPayload`, `SyncBestiaryEntriesPayload`, `ModNetworkBestiary`);
registration through `ModNetwork.register(RegisterPayloadHandlersEvent)` → `event.registrar("1")` →
per-domain `ModNetworkX.register(registrar)`. On-join push is `OnDatapackSyncEvent` (used by the ability
framework) rather than `PlayerLoggedInEvent`; both exist in the codebase.

## 4. Permissions — pattern exists, matched

`WizardsAndBeastsCommandPermissions.GAMEMASTER` = `source.permissions().hasPermission(
Permissions.COMMANDS_GAMEMASTER)`. This is the mod-wide idiom and is what §1.6's "permission level 2"
should bind to. Note the prompt specifies a numeric level; the codebase uses the named NeoForge node.

## 5. Config — exists, but COMMON not SERVER

`Config.java` builds one `ModConfigSpec`, registered as **`ModConfig.Type.COMMON`**
(`WizardsAndBeastsMod.java:170`). §1.4 asks for a *server* config. Adding module seeds to the existing
COMMON spec is the low-friction option; a second SERVER spec would be the first of its kind in the mod.

## Other deltas the prompt should know

- [POLISH] **A module command already exists**: `/wandb module [list | <module> <state>]`, GAMEMASTER-gated,
  registered from `WandbCommands`. §1.6 specifies `/wizardsandbeasts modules …` (plural). `/wizardsandbeasts`
  is a **deprecated alias** of `/wandb` — both roots build the same tree. Shipping `modules` alongside
  `module` would leave two near-identical admin surfaces.
- [POLISH] §1.1 keys state by `Identifier`; modules are a Java `enum Module` with no `Identifier` today.
  A mapping (probably lowercased enum name, as `AbilityResolver` already does for module gating) has to be
  introduced, and it becomes the persisted key — a rename of any enum constant then silently orphans stored
  state.

### Post-implementation notes (Module State Backend)

- [BLOCKER] **The Ministry system has never been active.** `Module.MINISTRY` was absent from the old state
  table and so resolved to `DISABLED`; that default is preserved, which means `TraceService.isActive()` has
  returned false since the Ministry was written earlier today. Nothing it does has ever run in a game.
  Enable with `/wandb module set ministry preview` or via the new `moduleDefaults` config section.
- [POLISH] **Client gates read `DISABLED` until the join sync lands.** The 11 client-side call sites used to
  read a locally-correct static map; they now read a mirror that is empty until `PlayerLoggedInEvent`
  sync arrives. Fails closed, which is the right direction, but anything querying during early client init
  will see a module as off.
- [POLISH] **The legacy positional command form is gone.** `/wandb module <module> <state>` conflicted with
  the new `set`/`setting`/`list` literals and was removed; use `/wandb module set <module> <state>`.
- [NICE-TO-HAVE] **Renaming a `Module` constant orphans its stored state**, because the persisted key is the
  lowercased constant name. Unknown ids are dropped with a warning and the module falls back to its config
  default, so nothing breaks — but a rename silently resets that module in every existing world.
- [NICE-TO-HAVE] **`ModuleSettingsValues` stores encoded JSON rather than typed values.** That is what makes
  the store type-agnostic, but it means a value is only validated when read through its definition; a
  setting whose type changes between versions logs and falls back to the default rather than migrating.

## Config screen + Ministry handbook (2026-07-20)

- [x] **FIXED:** `adminUuids` (today's security allow-list) was rendering on the general Mods-list config
  screen, uncategorized, as a non-editable box — the wrong surface for a security-sensitive setting, visible
  to any player. Excluded from `WizardsConfigScreen`'s scan by key and by the `moduleDefaults` prefix.
- [x] **FIXED:** five config keys were falling into "Gameplay" through the unmapped-key fallback rather than
  a reviewed categorisation. Added explicitly to `CATEGORY_BY_KEY`.
- [x] **FIXED:** no handbook chapter documented the Ministry law-enforcement system despite it existing and
  the book being framed as DMLE-issued. Added `ministry_law.json` (Part XI), describing only what is built —
  the Trace, notoriety-vs-record, wanted bands, pardons, ranks, licensing. Does not claim Aurors or Azkaban
  sentencing exist yet.
- [NICE-TO-HAVE] The config screen's "Dark Arts" category is permanently empty — no config key is mapped to
  it, so its card never renders, even though `openCategory` still special-cases it behind
  `DarkArtsGateScreen`. Pre-existing, unrelated to today, harmless. Either give it a real setting or remove
  the dead special-case.

## Handbook accuracy pass (2026-07-20)

- [x] **FIXED:** `spells.json` claimed Unforgivable use "carries an automatic sentence of life imprisonment
  in Azkaban" — that enforcement mechanic (Ministry Phase 2) does not exist. Rewritten to match Part XI.
- [x] **FIXED:** `heritage.json`'s "recognised Heritage categories" list did not match `Heritage.java` —
  conflated Wizardkind's Variants with top-level categories, omitted four real categories (Obscurial,
  House-Elf, Giant, Merpeople), and listed Metamorphmagus as a category (it is not one). Corrected to the
  real ten.
- [x] **FIXED:** four "Special Magical Abilities" entries (Metamorphmagus, Parseltongue, Wandless Casting,
  Werewolf Condition) claimed a Heritage-Variant grant path, a chat prefix, or a Wolfsbane/transformation
  system that has no code behind it — `setMetamorphmagus`/`setParseltongueSpeaker`/
  `setWandlessCastingLevel`/`setWolfsbaneActive` are called from nowhere in the codebase. Rewritten to state
  the mechanic is not currently enforced, rather than describing a false specific gate.
- [NICE-TO-HAVE] The four flags above (`metamorphmagus`, `parseltongueSpeaker`/`parseltongueSource`,
  `wandlessCastingLevel`, `wolfsbaneActive`) exist on `PlayerAbilityData`, are synced to the client, and are
  presumably meant to be granted by something — a Heritage Variant tag, a skill node, or an admin command —
  none of which exists yet. Genuinely unimplemented features, not doc bugs; the handbook now says so
  honestly rather than promising a specific mechanism.

# §0 audit — Tent Models (Exterior Geometry) (2026-07-21)

No stop conditions fired. Findings in the prompt's order.

1. **No `GeoBlockEntity`/`GeoBlockRenderer` pair anywhere in the mod — this is the first GeckoLib-rendered
   block.** Confirmed by decompiling `geckolib-neoforge-1.21.11-5.4.5.jar`: `animatable/GeoBlockEntity`,
   `renderer/GeoBlockRenderer`, and `model/DefaultedBlockGeoModel` all ship in this exact version, structured
   identically to the entity path already in use (`GeoEntity`/`GeoEntityRenderer`/`DefaultedEntityGeoModel`)
   — same `createRenderState`/`captureDefaultRenderState`/`extractRenderState` shape §0 asks for. Closest
   transferable pattern is **not** an entity but `GeoRendererHelper.simple(modelName)`
   (`client/GeoRendererHelper.java`): a one-line factory returning `new GeoEntityRenderer(context, new
   DefaultedEntityGeoModel<>(id))`, reused across 10+ entities with zero per-entity renderer classes. Mirrored
   directly: added `GeoRendererHelper.simpleBlock(modelName)` returning
   `context -> new GeoBlockRenderer<>(new DefaultedBlockGeoModel<>(id))`.
2. **Trunk blocks are plain vanilla, not GeckoLib.** `TrunkBlock extends BaseEntityBlock`,
   `TrunkBlockEntity extends BlockEntity` (no Geo interfaces), `RenderShape.MODEL`. Datagen comment
   (`ModModelProvider.java:211`) confirms: *"Placed trunks — placeholder cube models + item models until
   bespoke trunk art lands."* No blockstate/block-model JSON exists on disk for any trunk — they're
   datagen-only placeholder cubes. Tents are the first bespoke-modeled block in the mod. Block-registration
   convention reused as-is: `DeferredRegister` block + `BlockBehaviour.Properties`,
   `EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING`, `getStateForPlacement` facing
   the placer, no `VoxelShape` override (inherits vanilla's full single-block shape — satisfies §3 with zero
   code). `ModBlockEntities.java` shows both registration shapes in-repo: one `BlockEntityType` shared across
   many blocks (`TRUNK`, `Set.of(all 5 trunk blocks)`) and one-type-per-block (`WANDMAKERS_BENCH`,
   `FLOO_FIREPLACE`). Tents use the **one-type-per-block** shape (`ModBlockEntities.TENT_CANVAS`/`TENT_GRAND`,
   both backed by the same `TentBlockEntity` Java class) — model selection then lives entirely at the renderer
   factory call (which `DefaultedBlockGeoModel` identifier each factory closes over), mirroring
   `GeoRendererHelper.simple` exactly and needing no per-instance variant field or DataTicket at all.
3. **Module: `Module.POCKET_DIMENSIONS`** (`TrunkBlock.java:149`,
   `ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)`). Confirmed in `Module.java` and defaulted `ENABLED`
   in `ModuleDefaults.java`. Reused for the two preview blocks at `PREVIEW` per §5. Registration is never
   gated (doctrine confirmed by `TrunkBlock`'s own comment: *"Registration is never gated; entry ... is."*);
   there is no interaction to gate in this preview harness (§4 forbids interaction logic), so gating has
   nothing to attach to beyond the module state itself.
4. **Asset layout confirmed by inspecting `wand.geo.json`/`wand.animation.json` and `bowtruckle.*`:**
   `assets/wizards_and_beasts/geckolib/models/<domain>/<name>.geo.json`,
   `assets/wizards_and_beasts/geckolib/animations/<domain>/<name>.animation.json`,
   `assets/wizards_and_beasts/textures/<domain>/<name>.png`, domain so far = `item` | `entity`. No `block`
   domain exists yet — created one (`.../models/block/`, `.../animations/block/`, reusing
   `textures/block/` which trunks already populate with plain PNGs). `format_version: "1.12.0"` for geo,
   `"1.8.0"` for animation (both files read directly). Bone convention mirrors the wand's
   root-bone-at-origin/parent-chain style; the wand's own `handle`/`handle_classic` naming is
 item-specific and not reused verbatim — the prompt's own §2a bone list (`tent_root`, `canvas_body`,
  `entrance_flap`, …) is followed exactly instead, since it is more specific than the wand's.
5. **Oversized-render handling: no precedent anywhere** (`getRenderBoundingBox` has zero overrides in the
   whole tree) — a standard per-instance hook, not shared/global render config, so §6 stop condition 3 does
   not apply. **Correction to first-pass finding**: initially implemented as a `BlockEntity`-side override;
   this does not compile in this MC/NeoForge version — `javap` on the authoritative
   `applyDevTransforms_*_output.jar` shows `getRenderBoundingBox` is absent from vanilla `BlockEntity`
   entirely. The real hook is `IBlockEntityRendererExtension<T>.getRenderBoundingBox(T)`, a default method
   that `BlockEntityRenderer<T,S>` (and so `GeoBlockRenderer`) inherits — i.e. it belongs to the **renderer**,
   not the block entity. Moved accordingly: `GeoRendererHelper.simpleBlock(modelName, renderMargin)` now
   returns an anonymous `GeoBlockRenderer<T, S>` subclass overriding `getRenderBoundingBox(T)` to
   `new AABB(blockEntity.getBlockPos()).inflate(renderMargin)`; `TentBlockEntity` carries no bounds code at
   all. (Anonymous-subclassing the **raw** `GeoBlockRenderer` type to do this fails separately with a
   `createRenderState` erasure clash against `GeoRendererInternals` — the factory keeps its method-level type
   parameters and instantiates `new GeoBlockRenderer<T, S>(...)`, not the raw type, to avoid it.) Bytecode-
   confirmed bonus: `GeoBlockRenderer.getBlockStateDirection` already auto-detects
   `BlockStateProperties.FACING`/`HORIZONTAL_FACING`/`VERTICAL_DIRECTION`/`FACING_HOPPER` and captures it into
   the render state itself (`captureDefaultRenderState` → `addGeckolibData`) — since `AbstractTentBlock.FACING`
   is `HORIZONTAL_FACING` (same property trunks use), rotation is handled by the base renderer with **no**
   per-tent override or custom DataTicket needed for facing.

## Status: implementation complete, unverified in-world

Java compiles clean (`compileJava`), full suite green (220/220, unchanged from baseline). Assets authored:
`tent_canvas.geo.json`/`tent_grand.geo.json` (bone hierarchy per §2a — `tent_root`, `canvas_body`,
`entrance_flap` hinged at its top edge, `guylines_pegs`/`chimney`/`flagpole`/`banner`/4×`turret_*`,
`fx_entrance`/`fx_ridge` anchors), matching `.animation.json` pairs (`idle` wired live via a per-type
`AnimationController` in `TentBlockEntity.registerControllers` — the animation name is read from the
`BlockEntityType`'s own registry path so one Java class serves both without a variant field; `flap_open` is
authored but never triggered, per §4), and checker-pattern placeholder PNGs (magenta corner tag marks them
as WIP). Not yet done: an actual `/setblock` + camera check in a running client — no client was launched
this pass, so scale/culling/facing are confirmed by code and bytecode inspection only, not by eye.

## Deliberate deviation: no `BlockItem`

§2b lists "Two blocks via `DeferredRegister`" without requiring a held/inventory form, and §4 explicitly
excludes "item forms." No `BlockItem`, no creative-tab entry, no item model registered for either tent —
placement for this preview harness is `/setblock` only. `RenderShape.INVISIBLE` used (not `MODEL`): GeckoLib
draws 100% of the visible geometry via the `BlockEntityRenderer`, so there is no vanilla block model to
point a blockstate JSON at, and none was authored — avoids a double-render (a placeholder cube plus the real
Geo tent) and the missing-model log spam a `RenderShape.MODEL` with no model JSON would otherwise produce.

---

# Canon Correctness Pass — findings logged 2026-07-25

## Blockers

- [BLOCKER] **`toad.json` ships `"mmRating": 0`, outside the five-grade Ministry scale.** This is the
  single thing preventing `BestiaryEntry.CODEC` from binding `mmRating` as `Codec.intRange(1, 5)`,
  and with it the `BestiaryClassificationRangeTest` regression guard. `toad` has no Ministry grade in
  any canon source — it is a familiar, not a classified beast — so 0 may well have been a deliberate
  "unclassified" marker. Picking a replacement is a lore call. **Options:** (a) grade it `1` (X,
  "boring"), which is what the scale would say about a toad and is my recommendation; (b) delete the
  entry, if a mundane familiar does not belong in a Ministry bestiary; (c) keep 0 and widen the codec
  to `intRange(0, 5)` with 0 documented as "unclassified", which weakens the guard but is honest
  about the data. Everything else in Phase 1 shipped; only the codec bound and its test are held.

## Nice-to-have

- [NICE-TO-HAVE] **Dragon breed `mmRating` spread is an open design decision.** Scamander classifies
  "Dragon" as a single species entry at XXXXX and assigns no per-breed grades. The mod ships ten
  breeds across 3/4/5, which is plausibly a deliberate difficulty ladder rather than an error, so
  nothing was changed. Current values:

  | Entry | mmRating | Entry | mmRating |
  |---|---|---|---|
  | `antipodean_opaleye` | 3 | `romanian_longhorn` | 4 |
  | `common_welsh_green` | 3 | `swedish_short_snout` | 4 |
  | `chinese_fireball` | 4 | `hungarian_horntail` | 5 |
  | `hebridean_black` | 4 | `peruvian_vipertooth` | 5 |
  | `norwegian_ridgeback` | 4 | `ukrainian_ironbelly` | 5 |

  Recommendation: keep the ladder. It is more useful to a player than ten identical XXXXXs, and the
  bestiary is a gameplay surface as much as a lore one. Needs a ruling either way so it stops being
  re-flagged by every canon audit.

- [NICE-TO-HAVE] **22 of 27 datapack spells have no `canonTier`.** The field exists and defaults to
  `EXPANDED`; only the five whose tier was specified are annotated. The unannotated 22 are
  `accio`, `aguamenti`, `alohomora`, `arresto_momentum`, `bombarda`, `capacious_extremis`,
  `colloportus`, `confringo`, `crucio`, `diffindo`, `episkey`, `expelliarmus`, `finite_incantatem`,
  `incendio`, `levicorpus`, `liberacorpus`, `lumos`, `nox`, `reparo`, `riddikulus`, `stupefy`,
  `wingardium_leviosa`. Canon tier is an authorial call, not an inferable one.

- [NICE-TO-HAVE] **`canonTier` is not surfaced anywhere in-game.** `SpellMenuRenderHelper` draws a
  `Req:` line at a fixed offset; adding a tier line means new layout work, which this pass excluded.
  Worth doing whenever the spell menu is next touched.

- [NICE-TO-HAVE] **13 bestiary entries lack icon textures** (107 entries, 94 icons). Art backlog.

- [NICE-TO-HAVE] **`HeritageVariant.WEREWOLF_BORN` contradicts canon lycanthropy transmission.** The
  `Heritage.WEREWOLF` prose was corrected (lycanthropy is bite-only), but the `"born"` variant still
  ships inherited lycanthropy as a playable path with its own stat mods and tags. Removing or
  reframing it is a mechanics change, deferred to a heritage-mechanics pass along with bite-based
  infection, merperson land movement, and giant vs. half-giant sizing.

## Polish

- [POLISH] **Duplicate `sortOrder` 33: `cornish_pixie` and `demiguise`.** The only collision across
  all 107 entries. Bestiary list ordering between those two is therefore whatever the sort happens to
  produce. Trivial to fix; `sortOrder` was explicitly out of scope for this pass.

- [POLISH] **`Heritage#getTranslationKey()` and `HeritageVariant#getTranslationKey()` have zero call
  sites, and no `type.WizardsAndBeastsMod.*` / `subtype.WizardsAndBeastsMod.*` display-name keys
  exist in either lang file.** Heritage display names are rendered from raw `getDisplayName()`
  strings everywhere. This pass added only the `.desc` keys it needed; the display-name half of the
  pattern is still unwired. Fixing it means emitting 41 more keys and migrating the display-name call
  sites — a small, self-contained follow-up.

- [POLISH] **`ModNetworkTeacher` and `ModNetworkBeamDebug` live under `spell.*`** while the other 23
  network registrars live under `network.*`. Cosmetic package inconsistency.

## Deferred by prompt scope (each needs its own pass)

- `ModuleManager` is a static `EnumMap` with hardcoded defaults, no codec, no persistence, no client
  sync, despite its `package-info` claiming "datapack-driven"; `isEnabled()` returns `true` for
  `PREVIEW`, making the third state a no-op for gating. ~124 call sites.
  → `AGENT_PROMPT_module_state_backend.md`
- ~25 static UUID-keyed maps used as server runtime state, contradicting the `AttachmentType` + Codec
  convention; mixed `HashMap`/`ConcurrentHashMap` discipline; logout cleanup in 13 classes but no
  level-unload cleanup. → future `AGENT_PROMPT_server_state_migration.md`
- `ProtegoShieldHandler.PROTEGO_EXPIRY_TICKS` is a static map although the convention puts Protego
  state in `MobEffect.PROTEGO_SHIELD`; entangled with the Protego-not-spawning bug.
  → `AGENT_PROMPT_known_bugfixes.md`
- 13 independent `PlayerTickEvent` handlers each iterating players separately; should be one
  dispatcher with interval gating. → future `AGENT_PROMPT_tick_consolidation.md`
- 12 root-level Markdown docs totalling ~4175 lines, including five overlapping audit documents.
  → future doc consolidation

---

# Canon Correctness Pass, Follow-Up — 2026-07-25

## Resolved from the previous pass

- [RESOLVED] **`toad.json` `mmRating: 0` blocker.** Christian ruled against grading it `1`.
  `mmRating` is now `Optional<Integer>` bound as `Codec.intRange(1, 5).optionalFieldOf`, `toad` drops
  the field, and `BestiaryClassificationRangeTest` guards the domain. The predecessor's stop-and-report
  was the right call — the recommendation it carried was not.
- [RESOLVED] **Dragon-breed classification decision.** All ten breeds are `mmRating: 5`. Recorded in
  `DEVELOPER_REFERENCE.md` §6 so it stops being re-flagged by every audit.
- [RESOLVED] **21 of the 22 unassigned spell canon tiers.** 19 `BOOKS`, 2 `FILM`; the field is now
  genuinely optional so absence means untriaged rather than defaulted.
- [RESOLVED] **`sortOrder` 33 collision** — `demiguise` moved to 94.

## Open

- [NICE-TO-HAVE] **`threatTier` (or similar) for the dragon difficulty gradient.** Now that all ten
  breeds are XXXXX, the per-breed difficulty distinction has no home. It is canon-supported at the
  level of temperament rather than classification — Scamander calls the Antipodean Opaleye unusually
  mild-tempered for a dragon, and the Peruvian Vipertooth required an ICW cull for its taste for
  humans. A separate field keeps that expressible without lying about the Ministry grade. Deliberately
  not added in this pass; needs its own prompt, since it is a bestiary-gameplay design question rather
  than a lore correction.

- [POLISH] **`capacious_extremis` canon sourcing is unresolved.** The Extension Charm is book canon
  (Hermione's beaded bag, *Deathly Hallows*) but the incantation is never given in the books.
  `capacious_extremis` is the only shipped spell with no `canonTier`. If a source turns up it becomes
  `BOOKS` or `POTTERMORE`; if not, `ORIGINAL` is honest. Christian's call — an agent assigning it would
  be guessing.

- [NICE-TO-HAVE] **`mmRating` for `owl`, `cat` and `rat` when they land.** All three are ordinary
  animals with magical uses, so per the `toad` ruling they ship with **no** `mmRating` field. Noted
  here so nobody helpfully adds a `1`.

- [POLISH] **Historical boot-log lines still cite "26 spells".** `AUDIT_PUNCHLIST.md` (2026-07-06 pass)
  and `WORKLOG.md` both record a dedicated-server boot that reported 26 spells. Those are accurate
  records of a past run and were **left untouched** — rewriting them would falsify the log. The
  current-state count in `DEVELOPER_REFERENCE.md` §2 is corrected to 27.

- [NICE-TO-HAVE] **`canonTier` is still not surfaced in-game.** `SpellMenuRenderHelper` draws its
  `Req:` line at a fixed offset; a tier line means new layout work, excluded from both passes.

## Deferred by prompt scope — additions to the standing list

- **`HeritageVariant.WEREWOLF_BORN`** — ruling recorded: the ID stays (save data), and the reframe is
  **"bitten as a child" vs "bitten as an adult"** rather than inherited lycanthropy. Lupin was bitten at
  four and Greyback targets children deliberately, so lifelong lycanthropy from early childhood is fully
  canon and the mechanical distinction survives intact. Lang key and description only. → heritage-mechanics
  pass.
- **Base `getTranslationKey()` heritage display-name keys are unwired** — carried forward from the
  previous pass. POLISH.
- Everything in the previous pass's deferred list (ModuleManager backend, static server-state maps,
  Protego expiry map, tick consolidation, doc sprawl, network package placement) is unchanged.

---

## Module content gating pass (2026-07-27)

Found while gating module content; each was in reach of the pass and deliberately not taken.

- [x] **BLOCKER — Chamber of Secrets generates while `DISABLED`.** ~~The structure is raw data-driven
  jigsaw generation with no bespoke Java `Structure` subclass to consult the flag, unlike Azkaban's.
  Module state layer 4 (worldgen) therefore does not hold for it.~~ **Fixed 2026-07-27.**
  `ChamberOfSecretsStructure` wraps vanilla jigsaw generation (`JigsawStructure` is `final`, so the
  gate is composition, not a subclass) and returns no generation point unless the module is
  accessible. Verified on a dedicated server across `disabled` / `enabled` / `preview` / `disabled`,
  and an already-generated chamber survives the flag going off — see `MIGRATION_DELTAS.md`.
- [ ] **POLISH — vanilla `/give` still offers gated items.** Mod commands are all admin-gated, but
  `/give`'s suggestion provider enumerates the item registry directly. Filtering it needs a mixin on
  vanilla's provider, which no mechanism in this pass anticipated. Creative menu, recipe book, loot
  and use are all gated, so this is the one remaining acquisition route for a non-admin with command
  access.
- [x] **POLISH — an open creative screen keeps stale contents.** `CreativeTabRefresher` rebuilds the
  tabs on module sync, but a screen already open keeps the list it built with until reopened. Reaching
  into a live `CreativeModeInventoryScreen` was judged not worth it.
- [x] **POLISH — module display names are not used by the module UI.** This pass added
  `module.wizards_and_beasts.<id>.name/.desc` for all 26 modules and the interaction gate renders
  them, but `/wandb module list|info` still prints raw lowercase ids via `Component.literal`.
  Converting those is lang extraction, which is a separate pass.
- [x] **NICE-TO-HAVE — `LocationBlockHelper.stonePressurePlate` is dead.** `DiagonAlleyBlocks`
  registers its pressure plate straight into `ModBlocks` instead, which is why the block needed
  naming by hand in the tag provider. Either route it through the helper or delete the method.
- [x] **NICE-TO-HAVE — mod item tags live in two trees.** The 8 pre-existing gameplay item tags
  (`brooms`, `wands`, `coins`, …) are hand-authored under `src/main/resources`, while the new
  `module/*` tags are generated into `src/generated/resources`. Both work; the split is a trap for
  whoever next looks for "the tag file".

---

## Chamber of Secrets gating pass (2026-07-27)

Found while gating the structure; each is outside that fix's scope.

- [ ] **BLOCKER — `chamber_of_secrets/chamber.nbt` is an empty placeholder.** A 9×6×9 stone-brick box
  (floor, walls, ceiling, air interior) generated by `tools/gen_chamber_nbt.py`, with nothing in it.
  Enabling `CHAMBER_OF_SECRETS` yields a bare room with a basilisk. Building the real chamber is
  content design — the Slytherin statue, the pipe network, the lair layout — and needs canon
  decisions no automated pass may make. This is why the module ships `DISABLED`.
- [ ] **POLISH — `spacing 40 / separation 18` looks dense for an endgame structure.** That is roughly
  one chamber every 640 blocks. Azkaban is one per world; the Chamber of Secrets is arguably a
  one-per-world landmark too, or at least far rarer. Purely a balance decision, so left untouched.
- [x] **POLISH — `AzkabanStructure`'s javadoc contradicts `ModuleDefaults`.** The class comment says
  "AZKABAN = PREVIEW at alpha"; `ModuleDefaults` ships it `DISABLED`, and deliberately so (the
  fortress NBT is a 225-byte placeholder). One of the two is stale documentation.

---

## Client render pass — petrification, broom, cloak (2026-07-28)

Found while closing the three render gaps; each is outside that work's scope.

- [ ] **BLOCKER — the in-game pass for this work has not happened.** `test` (304/304) and `build` are
  clean, and the camera arithmetic has unit coverage, but nothing here can be confirmed from source.
  Needs checking in **both first and third person**, on **your own player and a second player**:
  the petrified statue (texture orientation on the skin sheet, frozen pose, shadow), the broom rider
  pose and torso lean, camera roll and pull-back, the return to neutral on dismount and on death, and
  — because `PlayerModelMixin` is shared with the form system — that **animagus and werewolf forms
  still render correctly**.
- [ ] **NICE-TO-HAVE — the petrification appearance is a placeholder.** `stone_statue.png` is flat
  grey with seeded grain and a few darker fissures, generated by `tools/petrify_stone_skin.py`. Which
  stone, whether the skin should tint rather than be replaced outright, and whether there is a
  transition into stone are art decisions and were left alone deliberately. The mechanism is the
  deliverable; swapping the PNG is all that a real appearance needs.
- [ ] **POLISH — `invisibility_cloak` does not hide held items or armour; `deathly_hallow_cloak`
  does.** Vanilla renders equipment layers regardless of invisibility (`shouldRenderLayers` gates only
  on `isSpectator`), and only the deathly cloak masks the six slots server-side via
  `ClientboundSetEquipmentPacket`. The `deathlyHallow` flag exists precisely to separate the two, so
  this reads as deliberate tiering and was left unchanged — but a floating sword and a full set of
  armour walking around is a conspicuous look for an item called "Invisibility Cloak". A canon call,
  not a bug fix.
- [x] **NICE-TO-HAVE — `FormRenderStateModifier` still uses an `IdentityHashMap` keyed by render
  state.** `BroomRiderRenderer` moved to `EntityRenderState.setRenderData` in this pass; the form
  system's equivalent map is only cleared in `LivingEntityRendererMixin` after a successful non-humanoid
  render, so a HUMANOID form or an early return leaves the entry stranded. Because
  `createRenderState` allocates a fresh state per frame, that map grows for the lifetime of the
  session. Not touched here — the brief forbids refactoring the form system — but it is the same
  defect the broom path just shed.
- [ ] **NICE-TO-HAVE — `PetrifiedState` carries a captured pose the client never sees.** The record
  stores `x/y/z/yRot/xRot` for the server's tick-pinning, but `PetrifiedStateSyncS2CPayload` sends
  only the boolean. That is correct for rendering today (the server pins the player, so the client's
  interpolated position already matches), but if petrification ever needs a *captured mid-stride*
  statue rather than a neutral one, the pose has to go over the wire first.

---

## Wandmaker table repair (2026-07-28)

Found while fixing the blank's wood type and the bench's overlay layout. Full evidence in
`WANDMAKER_AUDIT.md`.

- [x] **BLOCKER — `NifflerPouchScreen` and `HermionesBagScreen` draw their backgrounds with a `blit`
  overload that no longer exists.** Both call
  `graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256)`
  ([NifflerPouchScreen.java:34](src/main/java/at/koopro/wizardsandbeasts/client/bestiary/niffler/NifflerPouchScreen.java#L34),
  [HermionesBagScreen.java:30](src/main/java/at/koopro/wizardsandbeasts/client/item/HermionesBagScreen.java#L30)).
  The 1.21.1-era `blit(Identifier, x, y, u, v, w, h, texW, texH)` is gone; the only pipeline-less
  overload in 1.21.11 is `blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)` with the last four
  parameters `float`. The call compiles because ints widen, and resolves to `x0=x, y0=y, x1=0, y1=0,
  u0=176, u1=166, v0=256, v1=256` — a quad stretched from the panel corner back to the screen origin
  with reversed, out-of-range UVs and a zero-height V span. Both backgrounds are structurally wrong
  in-game. The correct call is the 12-argument pipeline overload the other 32 screens already use.
  Out of scope for the bench-only brief; these two are the whole of the mod's port debt.
- [x] **BLOCKER — five registered wand core materials cannot be placed in the bench or seen in JEI.**
  `WandCoreMaterialItem.isBenchCore` is a hardcoded three-item allowlist
  ([:36-41](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandCoreMaterialItem.java#L36-L41)),
  and `thestral_tail_hair`, `veela_hair`, `troll_whisker`, `wampus_cat_hair` and
  `thunderbird_tail_feather` are registered as plain `Item` with no core key
  ([WandItemRegistry.java:46-55](src/main/java/at/koopro/wizardsandbeasts/registry/WandItemRegistry.java#L46-L55)).
  They render fine and are tagged into `Module.WANDS`, so they read as finished content. Fixing it
  properly means moving wandlore identity into data (a `wand_cores/` registry in the
  `bench_enhancers` pattern) plus a canon core key and recipes for each — a design call, not a
  repair.
- [x] **POLISH — the bench's craft preview is drawn across the three ingredient slots.** The six-line
  preview starts at `y+16` and steps 12/10/10/10/10, so lines land at y 28/38/48 — the artwork's slot
  frames occupy y 35-50 at x 44-149. Item stacks render on top, so it reads as text bleeding out from
  behind the items. Not repaired with the rest of the layout because it cannot be fixed by moving it:
  the panel's free bands are y 16-34, 54-70 and 88-100, and the block needs ~60 px. It needs either
  fewer lines or a different surface (the output slot's own tooltip), which is a redesign.
- [x] **POLISH — `OllivanderTrialScreen` has the same invisible-label defect the bench just lost.** It
  fills a `0xFF1e1a28` panel and does not override `renderLabels`, so vanilla draws both labels in
  `#404040` on top of it.
- [x] **POLISH — the 30 wandmaking recipes gate loading, not access.** Each carries
  `"neoforge:conditions": [{"type": "wizards_and_beasts:module_enabled", "module": "WANDS"}]`, which
  conditions registration on module state, contrary to the standing "gate access, not registration"
  rule. Harmless while `WANDS` ships `ENABLED`. The bench's own access gate
  ([WandmakersBenchBlock.java:48](src/main/java/at/koopro/wizardsandbeasts/wand/bench/WandmakersBenchBlock.java#L48))
  is correct and unaffected.
- [x] **NICE-TO-HAVE — the bench never mentions its tier system until both inputs are seated.**
  `STATUS_BENCH_TOO_PLAIN` is the only surface, and it needs a blank *and* a core in place before it
  can fire. The cheapest wand needs a bench score of 2.0, i.e. four bookshelves in range.
- [x] **NICE-TO-HAVE — the wand blank's tooltip prints a namespaced id.** `Wood:
  wizards_and_beasts:rowan` rather than `Rowan`. Left as-is: the bench screen prints it the same way,
  and changing player-facing wording is a lore call.
- [x] **NICE-TO-HAVE — `WandmakingRecipe` cannot assemble its own result.** `matches` returns `false`
  and `assemble` returns `ItemStack.EMPTY`; `WandmakersBenchMenu` and `WandmakingCategory` each
  rebuild the output wand independently. Two copies of one rule.

---

## Punchlist sweep (2026-07-28, third pass)

Ten of the sixteen open non-blocker entries closed. What is left, and why.

- [ ] **POLISH — vanilla `/give` still offers gated items.** Unchanged, and it is the one item in this
  sweep that genuinely needs a mixin: `/give`'s suggestion provider enumerates the item registry
  directly and nothing hooks it. `/give` is op-only, and creative menu, recipe book, loot and use are
  all gated, so the exposure is a non-admin who has been handed command access. Deliberately not worth
  a mixin on its own; fold it into a mixin pass if one ever happens for another reason.
- [ ] **POLISH — `spacing 40 / separation 18` looks dense for the Chamber of Secrets.** Still a pure
  balance call — roughly one chamber every 640 blocks against Azkaban's one per world. The module
  ships `DISABLED`, so nothing is reachable either way. Left for a decision, not a fix.
- [ ] **NICE-TO-HAVE — the petrification appearance is a placeholder.** Still art: `stone_statue.png`
  is generated grain. Which stone, tint versus replace, and whether there is a transition are choices
  a pass like this may not make.
- [ ] **POLISH — `invisibility_cloak` does not hide held items or armour.** Left alone on purpose. The
  `deathlyHallow` flag exists precisely to tier the two cloaks, so "fix" here would erase a deliberate
  distinction. A canon call.
- [ ] **NICE-TO-HAVE — `PetrifiedState` carries a captured pose the client never sees.** Left alone:
  today's neutral statue is correct, and sending a field nothing reads is waste. It becomes work only
  if petrification ever wants a mid-stride statue.
- [ ] **NICE-TO-HAVE — the wandmaking recipe rule is now load-bearing and undocumented in code.** The
  80 recipes follow `tier += (raw_power - 1.15) * 2.0` and friends (see `MIGRATION_DELTAS.md`), but
  nothing in the repo enforces it — a hand-edited recipe can silently break the pattern. A generator
  script under `tools/`, or a test asserting the relation, would keep it honest.
- [ ] **NICE-TO-HAVE — `ash + unicorn_hair` breaks the recipe rule.** Its `result_integrity` is 0.97
  where every other wood has unicorn and phoenix equal; the rule says 1.00. One of the two ash rows is
  a typo. Not touched, because which one is wrong is a balance call. The 50 new recipes were derived
  from the unicorn column throughout, so they are internally consistent either way.
- [ ] **NICE-TO-HAVE — the three original wand cores still carry a literal debug tooltip.**
  `phoenix_feather`, `dragon_heartstring` and `unicorn_hair` are registered with
  `Component.literal("Source key: fawkes")` and friends, which reaches players as-is. The five cores
  added this pass take no bespoke tooltip and let their `.desc` line speak. The old three should
  follow, but their strings are player-facing copy someone chose.

---

## Deferred from Dead-End Elimination (2026-08-02, `DEAD_END_AUDIT.md`)

Carried forward explicitly. Each was reached, measured, and **stopped on** — none is an oversight.

- [ ] **BLOCKER — the 16 `*_unlock` nodes cannot be gated inside the SP economy.** Reaching all of
  them costs **75 SP** (union of cheapest root→node paths, 45 distinct nodes) against a **60 SP**
  lifetime cap (`SkillSystemAPI.MAX_SKILL_POINTS`). A player cannot buy them all, so gating each
  spell behind its namesake node would make spell access compete with the entire rest of the web.
  That is a progression rebalance, not a bug fix. **Deferred to the skill-web design session.**
  Today the nodes are named `*_unlock` but only modify cooldown/damage; no spell's `requiredSkillId`
  points at any of them. Two extra hazards for whoever takes this: four target spells (Protego,
  Expecto Patronum, Imperio, Avada Kedavra) are bespoke Java — they need the setter added in
  `9e7500bb`, not a JSON edit — and see the `wingardium_unlock` item below.

- [ ] **BLOCKER — `COMING_SOON` is module-granular only; skill nodes have no module field.**
  `ModuleState` (`disabled|enabled|preview|coming_soon`) applies per `Module`; `evaluateUnlock` gates
  only the whole `SKILL_TREES` module, `Skill` carries no module/availability field, and
  `ModuleContentIndex` maps only `EntityType`/`ItemLike` via tags. Marking a single dead node
  "coming soon" therefore needs **new infrastructure** (an availability field on `Skill` + a check in
  `SkillSystemAPI.evaluateUnlock` + client affordance), not a data edit. Until then `wand_mastery`
  stays an inert flag: it is registered, purchasable, and does nothing — a live dead end, and the
  most player-visible open item on this list.

- [ ] **POLISH — Philosopher's Stone is built, functional, and unobtainable.** `PhilosophersStoneItem`
  works (Elixir of Life, cooldown, destroyed-state component) but has no recipe, loot entry, or
  ritual; the `philosophers_stone` skill flag is read by nothing. **Do not add a stopgap acquisition
  path.** Canon note: Flamel's stone is singular and destroyed (*Philosopher's Stone*, ch. 17), so a
  craftable recipe would contradict canon. Acquisition is deferred to a future ritual.

- [ ] **POLISH — `wingardium_unlock` targets `wingardium_leviosa`; node stem ≠ spell id.** Every
  other `*_unlock` node's id is `<spell_id>_unlock`; this one is not. Any future gating pass must not
  derive the spell id by naively stripping `_unlock`, or this node will silently point at a spell
  that does not exist — the same fail-closed shape as the `capacious_extremis` defect.

Also closed in that pass, for the record: `capacious_extremis`'s dangling+namespaced prerequisite
(`9dfc9a2`), `apparition_training`'s unread flag (`8ce80b13`), and the Java/JSON `requiredSkillId`
asymmetry (`9e7500bb`). `SkillReferenceIntegrityTest` now fails the build on any dangling or
namespaced skill reference in data. **None of it has been verified in a live client.**

## Animagus form-system audit (Phase 0 halt)

Three defects found in the same audit were **fixed**, not listed: late-observer form sync, size
modifiers lost on respawn/dimension change, and the module-disable soft-lock. See
`MIGRATION_DELTAS.md`. What remains open:

- [ ] **BLOCKER — none of the Animagus form system has been verified in a live client.** The audit
  read source only. The three fixes above compile and pass 333 unit tests, but the multiplayer
  cases they target (a second player observing a transformed player; a player joining *after*
  someone transformed) are by definition untestable without two clients. Until that runs, treat
  "fixed" as "no longer contradicted by the code".

- [ ] **POLISH — `LivingEntityRendererMixin` holds logic inline.** House rule is that a mixin is
  pure delegation into a handler class. This one computes the lerped form scale and drives the
  PoseStack itself before calling `FormModelRenderer`. Pre-existing; harmless, but it is the
  template anyone adding a second form mixin will copy.

- [ ] **POLISH — form `eye_height` is not independently settable.** `FormHitboxHandler` uses
  `EntityDimensions.scalable(w, h)`, which derives eye height from height. Beast forms whose eyes
  do not sit at 90% of their height (a rat, a beetle) cannot express that. `FormDebugOverlay:75`
  computes a display-only eye height that nothing reads back.

- [ ] **NICE-TO-HAVE — `ModelType` dispatch and `formId` dispatch coexist in `FormModelRenderer`.**
  `renderToCollector` switches on `formId()` for the five vanilla-model Animagus forms, then falls
  through to a `modelType()` switch for everything else. Both are load-bearing; neither is wrong;
  but a form's render path now depends on which switch happens to name it first.

## Animagus v2 — Phase 1 (data layer) open items

- [ ] **BLOCKER — the four form JSONs reference rigs, animations and textures that do not exist.**
  `geckolib/models/entity/form/{rat,cat,dog,falcon}.geo.json` and their `.animation.json` and
  `.png` siblings are Phase 6 deliverables. Nothing reads these paths yet, so nothing breaks today,
  but the datapack is not shippable until the assets land.

- [ ] **BLOCKER — the data layer is inert.** `AnimagusFormRegistry` loads on datapack reload and is
  queried by no production code. State, sync, attribute application and rendering are Phases 2, 3
  and 6. Do not mistake "the JSONs parse" for "forms work".

- [ ] **POLISH — two Animagus form systems now coexist.** The shipped hardcoded roster
  (`AnimagusForms.IDS` → `FormRegistry` → `SizeProfileRegistry`) and the new datapack registry
  describe overlapping concepts with no bridge between them. This is the intended interim state for
  option A′, but until C′ resolves the roster question, anyone adding a form must know which of the
  two to edit.

- [ ] **BLOCKER — Phase 3 (application layer) is not built.** The datapack `hitbox`, `attributes`
  and `capabilities` are loaded, synced and resolvable, and are applied to nobody. Transforming
  still runs entirely through the shipped `SizeProfileRegistry` path. Flight (Phase 4) cannot be
  built on top until this lands.

- [ ] **POLISH — `/wandb animagus form <beast>` and `/wandb animagus set <player> <form>` suggest
  from different rosters.** The former offers `AnimagusForms.BEAST_KEYS` (the six shipped forms),
  the latter the four datapack definitions. Both are correct for what they write today; they
  converge when C′ resolves the roster.

---

## Apparition Reconciliation (Prompt A′) — 2026-08-05

Out-of-scope findings from the Phase 0 discovery audit and the Phase 1 verification pass.

- [ ] **BLOCKER — `runData` mojibakes the generated lang file on this Windows box.** Running
  `./gradlew runData` rewrote 34 lines of
  `src/generated/resources/assets/wizards_and_beasts/lang/en_us.json`, turning every em-dash into
  `â€”` (UTF-8 read as CP1252). Reverted rather than committed. Unrelated to Apparition and it will
  bite the next person who runs datagen and stages the result. The generator needs an explicit
  charset, or the Gradle JVM needs `-Dfile.encoding=UTF-8`.

- [ ] **POLISH — three `Config` keys are now stranded.** `apparitionSplinchBaseChance` is fully dead;
  `apparitionRangeBlocks` and `apparitionCooldownTicks` are superseded by the proficiency range
  formula and per-tier cooldowns. Still registered, read by nothing in the Apparition path. Removing
  them breaks existing config files, so it is a call rather than a cleanup.

- [ ] **POLISH — `PlayerAbilityData.splinchSeverity` and `splinchTicksRemaining` are dead fields.**
  Superseded by the `Splinched` mob effect before this work started; still in the record, still in
  the codec, still in `PlayerAbilityHelper`. Harmless, but they are a second place to look for
  splinch state that no longer holds any.

- [x] ~~**POLISH — Apparition proficiency is farmable by repeated blinking.**~~ **RESOLVED
  2026-08-05.** Blink-spam was reaching mastery roughly 5× faster than anchored travel, inverting the
  intent that the risky action is the primary earn path. A jump shorter than
  `ApparitionServerLogic.PROFICIENCY_MIN_DISTANCE` (15 blocks) now grants nothing; the `0.00005`/block
  rate and `0.01`/jump cap are unchanged. Farming is still *possible* — it just costs a real blink per
  grant rather than a step. Spell proficiency via `SpellProficiencyTracker` has no equivalent floor and
  remains open on its own terms.

- [ ] **POLISH — the shipped default wards point at content that may not exist.**
  `ApparitionWardRegistry.registerDefaults` seeds a Hogwarts ward in dimension
  `wizards_and_beasts:hogwarts` and a Gringotts ward as a 100×80×100 box at overworld origin. If that
  dimension does not exist the ward is inert; the Gringotts box wards a chunk of spawn regardless of
  what is built there. Untouched — §2 ruled the ward model correct — but worth a look.

- [ ] **NICE-TO-HAVE — `PlayerAbilityProficiency` is not synced to the client.** Deliberate: nothing
  client-side reads it today, and derived values are sent by whichever payload needs them. If a
  character sheet or HUD later wants to show practice directly, this needs a sync payload, and the
  codebase has no shared attachment-delta helper — every attachment hand-rolls its own.

- [ ] **NICE-TO-HAVE — no shared attachment sync helper.** Confirmed during the audit: each synced
  attachment writes its own payload and client state holder. Roughly one payload plus one holder per
  attachment. Not this prompt's problem, but it is why the item above has a cost.

- [ ] **NICE-TO-HAVE — the consensual side-along has no prompt UI.**
  `ApparitionSideAlongAcceptC2SPayload` is registered and works; the only way to send it today is
  `/wandb apparate accept`, advertised in a chat line. A prompt with a button is Prompt B's.

---

## GUI theme pass — Phase 1 (2026-08-06)

Noticed and deliberately not fixed. See `MIGRATION_DELTAS.md` for what *was* changed and
`docs/audit/GUI_THEME_AUDIT.md` for the ground truth both rest on.

- [ ] **BLOCKER — Phase 1 in-game verification not performed.** `./gradlew build` is green and
  `runClient` reaches the main menu with a clean atlas, but the Bestiary was never opened and the
  §5 tier A matrix (GUI Scale 1/2/3/Auto × small/large window) was not run. Nine-slice defects
  surface at scale extremes and nowhere else. Specifically needs an eye: entry rows lost their
  per-row plate (`MIGRATION_DELTAS` delta #3) and now rely on the list well plus the category
  buttons for separation — check that at scale 1 before Phase 2.

- [ ] **POLISH — seven orphaned Bestiary chrome PNGs.** `bestiary/screen.png`, `left_panel.png`,
  `right_panel.png`, `row.png`, `header.png`, `scroll_track.png`, `scroll_thumb.png` are now
  referenced by nothing, and `tools/gui_chrome.py` still generates all seven. Left in place: the
  pilot has not been seen in a running client, and deleting the art it replaced before anyone has
  looked at the replacement is the wrong order. Delete once §5 tier A passes.

- [ ] **POLISH — `textures/gui/bestiary/entries/` still does not exist.** Every Bestiary entry
  falls back to `entry_placeholder.png`. Carried from the Phase 0 audit; unchanged by this pass,
  and it makes the pilot's detail pane look emptier than the theming is responsible for.

- [ ] **NICE-TO-HAVE — `textures/mob_effect/README.txt` warns on every client boot.**
  `[resourceLoad/WARN] JarContentsPackResources: Invalid path in datapack:
  wizards_and_beasts:textures/mob_effect/README.txt, ignoring`. Harmless, but it is one line of
  noise in every log anyone will ever read while debugging this mod.

- [ ] **NICE-TO-HAVE — `drawHeader` ships with no caller.** The Bestiary's title sits outside its
  panel and cannot use it without a layout change (§4). It is written for the Phase 2 screens that
  have in-panel titles; if none of them do either, it should be deleted rather than kept as a
  method the mod does not use.

---

## Cleanup Pass 1 — found, not fixed (2026-08-10)

Everything below was surfaced by the cleanup audit and deliberately left alone: each is either a
behaviour change, a canon or balance ruling, or inside the quarantine.

### Behaviour gaps behind the dead constants

Four of the constants removed in `8e7a5967` were not clutter — they were the visible half of a
feature that was never wired. The constant is gone; the gap is recorded here so the signal is not
lost with it.

- [ ] **POLISH — `DementorKissGoal` has no cooldown.** `COOLDOWN_TICKS = 300` was declared and never
  read, so nothing throttles the Kiss between attempts. Whether a dementor should be able to re-Kiss
  immediately is a design call, not a cleanup call.

- [ ] **POLISH — apparition residue never gets its extended lifetime.**
  `ApparitionServerLogic.RESIDUE_LIFETIME_TICKS = 6000` was documented as "extra ticks a torn-loose
  item survives on the ground, so a jump gone wrong is recoverable" and read by nothing. Splinched
  items despawn on the vanilla timer, so a bad jump is less recoverable than intended.

- [ ] **NICE-TO-HAVE — the Marauder's Map never plays its open-idle animation.** `OPEN_IDLE` was
  declared against `animation.marauders_map.open_idle` and never triggered; only `FOLDED_IDLE` and
  `UNFOLDING` are used. The animation ships in the asset and is unreachable.

- [ ] **NICE-TO-HAVE — the two-way mirror has no vertical parallax.**
  `MirrorViewScreen.updatePresence(yaw, pitch)` still takes `pitch`, but the field it fed was never
  read; only `otherYaw` moves the face. The parameter is now unused at its one assignment site.

- [ ] **NICE-TO-HAVE — the Bestiary declared scrollbar sprites it does not draw.**
  `TEX_SCROLL_TRACK` and `TEX_SCROLL_THUMB` pointed at `textures/gui/bestiary/` art and were never
  bound; the screen draws the shared themed scrollbar instead. Worth checking whether those two PNGs
  are now orphaned on disk.

### Quarantine — skipped, not clean

- [ ] **POLISH — `creatures/basilisk.json` still carries the stale PLACEHOLDER rig marker.** The
  shipped rig is 24 bones / 111 cubes / 14 rotations and diverges from the `creature_gen` SERPENTINE
  template, so the marker is false by the same test that cleared the nine dragons. Left in place
  because the basilisk/petrify system is quarantined. One line to delete once the tangle is resolved.

- [ ] **NICE-TO-HAVE — `ClientPetrifyState` imports `java.util.Map` and never uses it.** Quarantined;
  the only unused import left in the mod.

### Deferred by judgement

- [ ] **NICE-TO-HAVE — archetype lang keys still read `trunk.archetype.*`.** After the
  `TrunkArchetype` → `PocketArchetype` rename the translation keys were deliberately not moved to
  `pocket.archetype.*`, because `TrunkTier` and `TrunkAccessMode` keep `trunk.tier.*` and
  `trunk.access.*` and splitting one member out of that family is a net loss. Revisit if and when
  the whole trunk lang namespace is reconsidered — it is four sites: `PocketArchetype
  .getTranslationKey()`, `ModLanguageProvider`, and the main and generated `en_us.json`.

- [ ] **NICE-TO-HAVE — `src/main/graphify-out/` sits inside `src/main`.** A generated knowledge-graph
  cache living beside `java/` and `resources/`. It is not on any source set so it does not reach the
  jar, but it pollutes recursive searches under `src/main` and cost this audit one false positive.
  Belongs outside `src/`, or in `.gitignore` if it is not already.

### Stale by design, confirmed accurate

Not defects — recorded so the next audit does not re-open them:

- `WandWoodDefinition.spellModifiers` is unread on purpose. The javadoc at lines 12–15 states that
  those schools have no `SpellCategory` counterpart and that choosing the mapping is an unmade
  balance decision. Verified present and accurate; the field stays.
- 83 of 96 creature definitions still carry the PLACEHOLDER rig marker and **should**. Their shipped
  rigs are byte-identical in bone structure to `tools/creature_gen.py`'s box-rig template. The
  marker is documentation of a real gap, not stale metadata.
- Re-running `tools/creature_gen.py` will re-emit the marker it writes at line 361 for any creature
  it rebuilds, including the nine dragons whose markers were just removed. The generator has no
  awareness that a rig has since been hand-built. Worth a guard before that script is next run in
  anger.

---

## Cleanup Pass 2 Phase 0 — found, not fixed (2026-08-10)

The structure lane was audited and shelved; see `MIGRATION_DELTAS.md`. Everything below is a real
finding that a moves-only lane cannot address.

- [ ] **POLISH — ~26 `ModNetworkX` classes hold a compile-time reference to a client class.** The
  pattern is `registrar.playToClient(TYPE, CODEC, ClientPayloadHandlers::handleX)` in
  `network.ModNetworkAzkaban`, `network.animagus.ModNetworkAnimagus`, `network.bestiary`,
  `network.brew`, `network.floo`, `network.form`, `network.handbook`, `network.heritage`,
  `network.map`, `network.owl`, `network.petrify`, `network.skill`, `network.spell`, `network.stats`,
  `network.trinket`, `network.trunk`, `network.currency`, `network.bloodpact`, `spell.beam`,
  `spell.teacher` and others. This is the only structural client/server weakness in the tree.
  **It is not a live defect** — a dedicated server boots on this tree with zero ERROR/FATAL — but it
  is one refactor away from being one. The fix is indirection (a handler supplier, or registering
  the client half behind a Dist guard), which is a content change: it belongs in its own small
  prompt, not in a moves-only lane.

- [ ] **POLISH — two different records are both named `BeamStyle`.**
  `client.beam.BeamStyle` and `client.wand.BeamStyle` have different shapes and disjoint consumers:
  `BeamAppearance` and `BeamStyleEditor` bind same-package to `client.beam`'s;
  `client/spell/gui/BeamDebugScreen` and `client/wand/BeamClientPayloadHandlers` use the
  `client.wand` cluster (`BeamStyle` + `BeamStyles` + `BeamSettings`). Both are live. This is
  precisely the shape of the earlier "debugged the wrong beam package for a whole session"
  landmine. Merging them, or renaming one, is a design call.

- [ ] **NICE-TO-HAVE — 7 client classes are reachable only through string-literal FQNs.**
  `ClientScreenHooks`, `CoinRenderer`, `DeluminatorRenderer`, `MaraudersMapRenderer`,
  `MorphWandClientHooks`, `WandRenderer`, `WandCastClient`. The indirection is deliberate and
  correct — it is what keeps those items Dist-safe — but it means no compiler and no IDE rename will
  ever follow them. Any future move of one of these is a silent runtime break. Worth a test that
  asserts each string resolves, so the breakage is caught at build time rather than on first use.

- [ ] **NICE-TO-HAVE — `integration.jei` imports `net.minecraft.client` with no Dist guard.**
  Conventional for a JEI plugin, and safe as long as JEI is never present on a dedicated server.
  Noted rather than changed.

- [ ] **NICE-TO-HAVE — 152 of 283 packages hold one or two files.** Over-fragmentation is this
  tree's real structural smell, not god-packages (only `creature.ability` exceeds 40 files). Moving
  files will not fix it: the missing thing is a convention — feature-first vs layer-first, plus a
  minimum package size — and that is a design decision, not a cleanup. Without it, a merge pass just
  relocates the fragmentation and spends `git log --follow` across ~200 files to do it.

---

## Broom master-model work — Phase 0 findings (2026-08-11)

Defects surfaced by the read-only audit of the broom + wand-customization surface. Logged, not
fixed, except where noted.

- [x] **POLISH — `WandRenderer.applyBoneVisibility` carried a factually wrong comment.** It read
  "Variant bones are flat siblings in the model (not children of container bones)". They are not:
  `wand.geo.json` parents every variant under its slot's container bone (`handle_gnarled` →
  `handle`, `tip_pointed` → `tip`), and a tip variant's `_anchor` is a child of the variant itself,
  not a sibling. The code works either way — it addresses bones by name — but the comment would
  mislead anyone reasoning about why `skipChildrenRender` is enough. **Corrected under narrow
  authorisation; comment text only, no code change.**

- [ ] **POLISH — `BroomDefinitionRegistry.getFallback()` throws if a datapack removes one file.**
  `FALLBACK_ID` is hardcoded to `wizards_and_beasts:cleansweep_seven`, and `getFallback()` raises
  `IllegalStateException("Missing fallback broom definition: …")` when that id is absent. Every
  generic `broom` item resolves through this path, so a datapack that drops `cleansweep_seven.json`
  turns a cosmetic removal into a hard crash on first broom render. A dedicated fallback definition
  that ships with the mod and is not part of the visible roster would make the degradation
  deliberate.

- [ ] **NICE-TO-HAVE — `broom.animation.json` ships `brake` unwired.** Authored as a clip so the
  animation file does not have to be reopened, but no controller references it. Wiring it needs a
  **brake input flag** on `BroomEntity.setInput(...)`, which currently carries only
  `forward, backward, up, down, boosting, yaw, pitch` — `backward` is reverse thrust, not braking —
  plus the matching deceleration behaviour in `BroomMovement`. That is a gameplay feature, not an
  animation binding, and flight-controller changes are out of scope.

- [ ] **NICE-TO-HAVE — `broom.animation.json` ships `summon` unwired.** Same treatment. Wiring it
  needs a **summon trigger** that does not exist anywhere in the mod: no accio-to-hand path, no
  summon state on `BroomEntity`, no synced flag to drive the transition from. Canon-anchored to the
  first flying lesson, so the clip is worth having authored ahead of the system.

- [ ] **NICE-TO-HAVE — `tools/wand_skin.py` docstring is stale.** It opens "`wand.geo.json` is the
  most elaborate rig in the mod — 47 bones … 100 cubes sharing two UV slots". The model now carries
  **77 bones and 206 cubes**. The described defect was fixed by this very script, so the numbers are
  a historical record of the input, but nothing says so and they read as current.

- [ ] **NICE-TO-HAVE — the generic `broom` item has no definition of its own.** Identity comes from
  the `broom_definition` DataComponent, so the eight registered broom items map onto seven
  definition JSONs; the unqualified one borrows Cleansweep Seven's physics, tier, display name and
  lore. Harmless today, but it means "the starter broom" has no authored identity to tune.

## Broom fallback identity — found while implementing (2026-08-11)

- [ ] **POLISH — `BroomEntity:360` uses `Identifier.tryParse()`.** `resolveDefinition()` reads the
  synced definition-id string back with `Identifier.tryParse(getDefinitionId())`. The stack rules for
  this project forbid `tryParse` outright in favour of `Identifier.fromNamespaceAndPath`. Not fixed
  here: the two behave differently on malformed input — `tryParse` returns null and falls through to
  the fallback broom, `fromNamespaceAndPath` throws — so swapping them is a behaviour change on the
  desync path, not a mechanical substitution. Pre-existing; flagged because it is a hard-rule
  violation sitting on a path this work now routes through.

- [ ] **NICE-TO-HAVE — `BroomItem.defaultDefinitionId` and `FALLBACK_ID` overlap confusingly.**
  `RegistryUtils.registerBroom` passes `modId(name)` as each item's `defaultDefinitionId`, which
  `BroomItem` stamps onto the stack at creation (line 47). But `resolveDefinition` and the tooltip
  path both re-read the component with `FALLBACK_ID` as their default, not `defaultDefinitionId`.
  The two agree today, so nothing is broken; they are simply two answers to "what broom is this?"
  living one field apart.

### Resolved by this pass

- **`BroomDefinitionRegistry.getFallback()` no longer throws.** It now degrades to a code-level
  `CODE_DEFAULT` constant when the shipped definition is absent, so deleting a broom JSON from a
  datapack stays cosmetic instead of crashing every generic-broom render and every broom entity
  tick. The earlier logged fragility stands corrected — note that repointing `FALLBACK_ID` alone
  would *not* have fixed it, since a datapack can delete `broom.json` exactly as easily as
  `cleansweep_seven.json`.

- [x] **POLISH — the broom entity had no texture at all.** `textures/entity/broom.png` did not exist,
  while `BroomRenderer`'s `DefaultedEntityGeoModel` resolves exactly that path, so every broom entity
  rendered with a missing texture. Fixed in Phase 2; `tools/broom_model.py` generates it.

- [ ] **NICE-TO-HAVE — `tools/broom_model.py` cannot be committed.** The brief requires generation
  scripts to be committed alongside the assets they produce, but `/tools/` is gitignored by the
  history purge and no tool script in the repo is tracked. The script is on disk beside the others
  and was not force-added; committing it would need a deliberate `.gitignore` carve-out.

- [ ] **POLISH — boosting does not accelerate any faster than normal flight.**
  `BroomMovement:37` reads
  `accelerationRate = boostingNow ? def.acceleration() : def.acceleration();` — both arms of the
  ternary are identical, so the conditional does nothing. Boost raises the *target* speed via
  `boostMultiplier` but the broom closes on it at the ordinary rate, which is why a boost feels like
  a slow drift upward rather than a kick. Left alone: giving the boost arm its own rate is a new
  tuning value and a balance decision, not a typo fix.

## Broom Rev 2 — found, not fixed (2026-08-12)

- [x] **BLOCKER (fixed) — the renderer never resolved `model_slots`.** Rev 1 shipped the data layer
  and the model but not Phase 3, so `BroomRenderer` only applied tilt and every broom in the world
  drew all 24 variants stacked inside each other. The Rev 2 brief recorded this as working "as
  verified by the contact sheet"; the contact sheet was produced by a script that filtered bones in
  Python and proved nothing about the game. Built in `d8ccefd3`.

- [ ] **POLISH — `wood_tint` tints the binding band too.** §3.2 wants the band exempt. GeckoLib's
  render colour is one value per pass, so there is no per-bone tint through this API; exempting the
  band needs a second render layer keyed to the band bones. Accepted as uniform tint by ruling.
  Visible whenever a broom carries a strong `wood_tint` — the band goes the colour of the wood
  instead of staying metal.

- [ ] **POLISH — the broom texture reads as noise, not wood grain.** Known and deferred by §6.
  Deliberately untouched in this pass; the order is silhouette, then tint, then grain.

- [ ] **POLISH — `tail_cap` cannot follow the shaft sweep.** `tail_cap_*` is parented to
  `broom_body`, but the grip end is swept upward by the shaft chain, so a cap sits at the unswept
  height and reads as a ferrule floating near the handle rather than capping it. Fixing it means
  parenting the cap into each shaft variant's chain, which multiplies the cap variants by the shaft
  variants — a design change, not a geometry tweak.

- [ ] **NICE-TO-HAVE — `tools/broom_silhouettes.py` and its sheet cannot be committed.** §5 requires
  the silhouette sheet be committed alongside the geometry, but `/tools/` and `/docs/` are both
  gitignored by the history purge and no tool script in the repo is tracked. Both are on disk. Same
  carve-out question as `tools/broom_model.py`.

- [ ] **NICE-TO-HAVE — no atlas regions were orphaned by the collapse.** §6 asked for orphans to be
  left in place and reported. There are none: the texture is packed from the geometry every run, so
  removing variants re-lays-out the whole sheet rather than leaving holes in it.
