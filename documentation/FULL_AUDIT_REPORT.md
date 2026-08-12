# FULL AUDIT REPORT — Wizards & Beasts

**Date:** 2026-07-06 · **Mode:** read-only diagnostic sweep (Passes A–G) · **Stack:** MC 1.21.11 / NeoForge 21.11.x / Java 21 / GeckoLib 5.4.5
**Scope note:** No production files were modified. Findings are logged, never actioned. Stable IDs `AUD-<pass>-<nnn>` for follow-up fix prompts.

> **Resolution status note (added 2026-07-13 after the smart-merge):** This is the 2026-07-06 snapshot of the `AUD-*` finding registry. For the freshest resolution status of any finding, consult `ALPHA_IMPROVEMENT_AUDIT.md` (2026-07-13) — it treats the carryover findings below as still-open unless explicitly noted: it confirms `AUD-D-001` and `AUD-D-002` as **fixed**, confirms `AUD-D-004` as **now wired** (loader shipped), and confirms the following as **still open**: `AUD-F-001`/`AUD-F-002` (Dark Arts gating), `AUD-E-001` (Protego recast), `AUD-C-001`/`AUD-C-002` (wand-tip cache), `AUD-B-007`/`AUD-B-008` (JSpecify dialect split). Where `AUDIT_PUNCHLIST.md` (folded into `WORKLOG.md`) shows a finding as `[x]` but `ALPHA_IMPROVEMENT_AUDIT.md` still lists it as open, the newer audit wins — the punchlist `[x]` reflects the fix-pass status at the time of writing rather than a verified-still-closed recheck.

---

## Executive summary

**Build:** `./gradlew compileJava` — BUILD SUCCESSFUL (one javac note: deprecated-API usage, no warnings-as-errors). `./gradlew test` — all 33 test classes pass.

**Counts by severity:**

| Severity | Count |
|---|---|
| BLOCKER | 0 (1 fixed 2026-07-13: AUD-D-001) |
| BROKEN | 6 |
| EDGE-CASE | 8 |
| IMPROVEMENT | 6 |
| NICE-TO-HAVE | 4 |

**Known-bug scoreboard (Pass E):** Protego → root cause found (recast ordering, AUD-E-001). Gamp NPE → fix confirmed present. Broom `acceleration` codec → **refuted** (fixed). `niffler_shiny` tag → **fixed**. `floo_powder` recipe → **refuted** (datagen recipe complete). Niffler WIP → structurally sound, one dead TODO hook.

### Top 10 to fix first

> Resolution tags below reflect the 2026-07-13 freshest read from `ALPHA_IMPROVEMENT_AUDIT.md`.

1. **AUD-D-001 (BLOCKER, FIXED 2026-07-13)** — Wandmaking recipes never load: wrong directory (`wandmaking_recipes/` is not a loaded location) *and* missing `"type"` field. Wandmaker's Bench can never produce a wand. Re-verified 2026-07-13: JSONs now live in `data/wizards_and_beasts/recipe/` with `"type": "wizards_and_beasts:wandmaking"` present; `WandmakersBenchMenu.findRecipe` resolves correctly; `./gradlew compileJava` clean. Recipe coverage also expanded from 6 to the full 30-combo wood×core matrix (10 woods × 3 bench-craftable cores) in the same pass.
2. **AUD-E-001 (BROKEN, still open per 2026-07-13)** — Protego recast ordering: new shield effect is added *before* the old shield is shattered; old shield's shatter removes the caster's effect → new shield self-shatters on its first tick. Explains "shield not spawning" on real casts.
3. **AUD-D-003 (BROKEN)** — `tags/items/` (plural) is dead in 1.21: `broom_repair_material_elite/racing` never load, so all 5 tag-repaired brooms have an always-empty repair ingredient.
4. **AUD-F-001 (BROKEN, still open per 2026-07-13)** — Avada Kedavra's lethal beam path is not gated on `Module.DARK_ARTS` (Crucio and Imperio are). Dark Arts is DISABLED by default, yet Avada still kills.
5. **AUD-D-002 (BROKEN, FIXED 2026-07-13)** — Brew + brewing-recipe reload listeners read `data/wizards_and_beasts/wizards_and_beasts/...` (MODID-prefixed) but the JSONs sit un-nested → wiggenweld datapack defs never load. *(Re-verified 2026-07-13 by ALPHA_IMPROVEMENT_AUDIT §8: confirmed fixed in current source — Wiggenweld Potion loads.)*
6. **AUD-C-001 (BROKEN, still open per 2026-07-13)** — `WandTipWorldCache.WAND_TIP_BONE = "wand_tip"` doesn't exist in the master 47-bone wand geo (elder-wand skin only) → exact beam origin never captured for standard wands.
7. **AUD-C-002 (EDGE-CASE, still open per 2026-07-13)** — `WandTipWorldCache` is never cleared (`clear()` has zero callers) → a stale elder-wand tip position can anchor beams at an old world position.
8. **AUD-D-004 (BROKEN, FIXED 2026-07-13)** — `pocket_templates/` (3 JSONs) has no loader anywhere — dead data or a missing system. *(Re-verified 2026-07-13 by ALPHA_IMPROVEMENT_AUDIT §11: minimal loader shipped — PocketTemplate codec + registry + reload listener; 3 templates boot-verified.)*
9. **AUD-F-002 (BROKEN, Likely, still open per 2026-07-13)** — Spell learning has no DARK_ARTS gate: players can learn dark spells while the module is disabled, then cast the ungated ones (see #4).
10. **AUD-B-007 (IMPROVEMENT, still open per 2026-07-13)** — JSpecify coverage is partial (213/931 files) and mixed with jetbrains/javax annotations in ~30 files; 19 files `return null` with zero `@Nullable` anywhere in the file.

---

## Pass A — Build & compile integrity

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-A-001 | — (pass) | Confirmed | build | `compileJava` succeeds; only note is `Some input files use or override a deprecated API` (javac, not enumerated without `-Xlint:deprecation`) | Baseline is healthy; deprecation debt exists but not itemized by the toolchain |
| AUD-A-002 | — (pass) | Confirmed | src/test (33 classes) | `gradlew test` exit 0 | Codec/data-layer regressions are covered for brews, creatures, spells, packets, migrators |
| AUD-A-003 | NICE-TO-HAVE | Confirmed | 16 sites | TODO inventory (no FIXME/XXX/HACK found): `DementorVoiceClient.java:32`, `DementorRenderer.java:61`, `CharacterSheetTextures.java:9/16/23`, `HappinessTickHandler.java:45` (`TODO(effects): SpellPowerModifier.apply()`), `ApparitionEvents.java:43`, `PatronusDetection.java:18`, `ModBlocks.java:220`, `ModBlocks.java:245` (STRUCTURES gate TODO — module now exists, TODO stale), `ModSounds.java:51/53/55/57` (4 protego audio assets), `VocationManager.java:32`, `SpellExecutor.java:123` | Most are asset/audio placeholders; two are live wiring gaps (HappinessTickHandler effects hook, ModBlocks STRUCTURES gate) |
| AUD-A-004 | — (pass) | Confirmed | registries | Duplicate-registration scan across all `registry/*.java`: every same-name pair is cross-registry (block+item, block+BE+menu, attachment+attribute `dark_corruption`) — no true collisions | DeferredRegister would hard-crash at load on a real dup; none exist |

## Pass B — Tech-stack compliance

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-B-001 | — (pass) | Confirmed | whole tree | `ResourceLocation`: **0** occurrences; `Identifier` + `Identifier.fromNamespaceAndPath()` used throughout | Contract met |
| AUD-B-002 | — (pass) | Confirmed | whole tree | `MinecraftForge.EVENT_BUS`: 0; UUID-keyed `AttributeModifier`: 0 | Contract met |
| AUD-B-003 | — (pass) | Confirmed | registry/* | All registration via `DeferredRegister`; the only `net.minecraft.core.Registry` imports are datapack-registry key plumbing (`WandDatapackRegistries`, debug commands). `ApparitionWardRegistry` is a mod-internal runtime map, not a MC registry | Contract met |
| AUD-B-004 | — (pass) | Confirmed | network/** | 100+ payloads all `implements CustomPacketPayload` with `StreamCodec`; single `RegisterPayloadHandlersEvent` entry (`ModNetwork`) fanning to sub-registrars (`ModNetworkSpells`, `ModNetworkNiffler`, …) | Contract met |
| AUD-B-005 | — (pass) | Confirmed | ModAttachments.java | Player persistence via `AttachmentType` + codec/serializer. `SavedData` used only for world-scoped stores (Azkaban, ElderWand, TrunkRegistry, ColloportusLocks, FlooNetwork) — appropriate use, not player data | Contract met |
| AUD-B-007 | IMPROVEMENT | Confirmed | ~30 files | JSpecify adoption partial: 213/931 files import `org.jspecify`; 291 `@Nullable` across 121 files; **52 lines still import `org.jetbrains.annotations` / `javax.annotation`** (e.g. `AnimagusForms`, `BroomDefinitionRegistry`, `BroomEntity`, `SpellProjectileEntity`, `FormRegistry`, `ObscurialAbility`, `ModDataComponents`) | Two annotation dialects in one codebase defeats tooling; JSpecify was declared the standard |
| AUD-B-008 | IMPROVEMENT | Confirmed | 19 files | Files that `return null` but contain zero `@Nullable`: worst offenders `client/bestiary/gui/BestiaryScreen.java` (4×), `mirror/MirrorSessionManager.java` (3×), `client/spell/ColoredGlowRenderer.java` (3×), `brew/def/BrewingRecipeDefinition.java` (2×), `brew/def/BrewDefinition.java` (2×), plus 14 single-site files (`WandmakingRecipe`, `Compatibility`, `JsonSpell`, `SpellEffectComponent`, `WandRenderer`, `FormModelRenderer`, `CauldronBrewing`, `ApparitionServerLogic`, `StatsCommands`, …) | Unannotated null returns are the NPE seed-bank; fix these before broad annotation sweeps |

## Pass C — GeckoLib 5 render safety

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-C-001 | BROKEN | Confirmed | `client/wand/WandTipWorldCache.java:11` + `geckolib/models/item/wand.geo.json` | `WAND_TIP_BONE = "wand_tip"`, but the master wand geo (47 bones) has **no** `wand_tip` bone — only the 5-bone `elder_wand.geo.json` does. `WandRenderer.preRenderPass:108` registers a bone-position listener that therefore never fires for the standard wand | `WandBeamRenderer.java:94-95` always falls back to the hand-math approximation (`wandTipWorldPos`) — beam origin never matches the actual modelled tip. Master geo has `fx_tip_anchor`/`tip_*_anchor` bones at Y=23 that look like the intended anchor |
| AUD-C-002 | EDGE-CASE | Confirmed | `client/wand/WandTipWorldCache.java:25` | `clear()` has zero callers; `lastWorldTip` persists forever once set (elder wand render) | After switching from elder wand to a standard wand (or any state where the listener stops firing), beams anchor at the *last elder-wand tip world position* — a frozen point in space |
| AUD-C-003 | — (pass) | Confirmed | DragonRenderer / ScaledBeastRenderer / DementorRenderer / ProtegoShieldRenderer / BroomRenderer | All five stateful renderers follow the GL5 contract: state copied in `addRenderData`/`captureDefaultRenderState` via `DataTicket`s, bone passes read only render-state. Interpolation (broom tilt lerp) done at capture time. Simple item renderers (Coin/Deluminator/Map) are stateless `DefaultedItemGeoModel` | The highest-value correctness class is clean |
| AUD-C-004 | — (pass) | Confirmed | WandItem / CoinItem / DeluminatorItem / MaraudersMapItem | All GeoItems have `createGeoRenderer` + `AnimatableInstanceCache` (via `GeoItemBase`) | Required plumbing present |
| AUD-C-005 | EDGE-CASE | Likely | `client/wand/WandRenderer.java:133` | `WandModel.getAnimationResource` returns `null`. Safe only while `WandItem` registers no animation controllers; if a controller is ever added, GeckoLib will NPE resolving animations | Latent trap on the mod's most central item |
| AUD-C-006 | — (pass) | Confirmed | wand.geo.json | **Master model contract verified:** 47 bones / 100 cubes exactly; `wand_root` + all 6 handles pivot Y=0; `shaft`/`tip` + variants Y=11; all 5 `tip_*_anchor` Y=23; bone names `<slot>_<variant>` match all 25 `WandModuleRegistry` built-ins 1:1; `WandConfiguration` drives visibility in `applyBoneVisibility` with `_anchor` pairing | Contract intact |
| AUD-C-007 | NICE-TO-HAVE | Confirmed | `client/wand/WandRenderer.java:70-71` | Comment claims variant bones are "flat siblings … not children of container bones"; the geo has them as *children* of `handle`/`shaft`/`tip`/`core`/`ornament` container bones | Behavior is unaffected (per-variant skipRender works either way) but the comment misdescribes the asset — recorded as doc drift, see `WORKLOG.md` |

## Pass D — Datapack / codec / registry integrity

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-D-001 | — (fixed 2026-07-13) | Confirmed | `data/wizards_and_beasts/recipe/wandmaking_*.json` (30 files) + `wand/gui/WandmakersBenchMenu.java:196-207` | **Originally:** two independent kills — (1) the JSONs sat in `wandmaking_recipes/`, which nothing loads; (2) every JSON lacked the required `"type"` field. **Now:** JSONs live in the correctly-loaded `recipe/` directory with `"type": "wizards_and_beasts:wandmaking"` present on all files; `findRecipe` resolves matches against `getRecipeManager().getRecipes()` correctly. Coverage expanded from the original 6 files to the full 10-wood × 3-core (30-combo) matrix | Wandmaker's Bench now produces a wand for every wood the game recognizes (`WandBlankItem.wandWoodFromLogBlock`) crossed with every bench-craftable core (`WandCoreMaterialItem.isBenchCore`: phoenix feather, dragon heartstring, unicorn hair) |
| AUD-D-002 | — (fixed 2026-07-13) | Confirmed | `brew/def/BrewReloadListener.java:31` + `BrewingRecipeReloadListener.java:29` vs `data/wizards_and_beasts/brews/` + `brewing_recipes/` | **Originally:** Both listeners use `DIRECTORY = MODID + "/brews…"` → they scan `data/<ns>/wizards_and_beasts/brews/…` (MODID-prefixed subdir); the actual JSONs (`wiggenweld_potion.json` ×2) were at the *un-prefixed* `data/wizards_and_beasts/brews/…`. **Now:** Re-verified 2026-07-13 by `ALPHA_IMPROVEMENT_AUDIT.md §8` — confirmed fixed in current source; Wiggenweld Potion does load and is reachable | Datapack brew definitions and brewing recipes now load correctly. The fix scope was the directory contract mismatch, not the recipes themselves (no cauldron-tier crafting recipes ship yet — separate finding in ALPHA_IMPROVEMENT_AUDIT §8) |
| AUD-D-003 | BROKEN | Confirmed | `data/wizards_and_beasts/tags/items/` (plural, 5 files) | 1.21 loads item tags from singular `tags/item/`. Dead files: `brooms.json`, `broom_repair_material_elite.json`, `broom_repair_material_racing.json`, `cannot_conjure/{food,currency}.json` (the last two are stale duplicates — live copies exist in `tags/item/`). All 5 broom definitions reference `#wizards_and_beasts:broom_repair_material_*` as `repairMaterial` | Broom repair-material ingredient resolves to an **empty tag** — tag-based broom repair can never match. `#wizards_and_beasts:brooms` has no consumers (dead either way) |
| AUD-D-004 | — (fixed 2026-07-13) | Confirmed | `data/wizards_and_beasts/pocket_templates/*.json` (3 files) | **Originally:** No loader — zero Java references to `pocket_templates`, `templateId`, or these files' schema. **Now:** Re-verified 2026-07-13 by `ALPHA_IMPROVEMENT_AUDIT.md §11` — minimal loader shipped (PocketTemplate codec + registry + reload listener; `maxRadius` caps pocket creation; synthetic `trunk_lock_N`/`trunk_decoy` ids pass through); 3 templates (`adaptive_utility` / `blank_shell` / `sanctuary_habitat`) boot-verified | The Room-of-Requirement scaffolding is now reachable; fuller gameplay wiring remains future work |
| AUD-D-005 | — (refuted) | Confirmed | `broom/BroomDefinition.java:81-94` | Re-verified the flagged `acceleration` range: codec accepts `[0.005, 0.15]`; scripted validation of all 7 broom JSONs against **all 14** ranged fields + 4 required fields passes cleanly | The "all brooms rejected" bug is fixed (commit 3553a8f). Not reproducible |
| AUD-D-006 | — (refuted) | Confirmed | `tags/item/niffler_shiny.json` + `NifflerSeekShinyItemGoal.java:23` / `NifflerSeekShinyBlockGoal.java:31` | `niffler_shiny` (item) and `niffler_shiny_blocks` (block) both exist in the correct singular dirs and match the goal references. `#minecraft:trimmable_armor_materials`: **zero references** anywhere in src (assets or Java) | Both flagged tag issues are resolved/nonexistent in the current tree |
| AUD-D-007 | — (refuted) | Confirmed | `ModRecipeProvider.java:133` + `src/generated/resources/data/wizards_and_beasts/recipe/floo_powder.json` | Floo powder recipe is datagen-generated: shapeless, 3 ingredients (blaze powder + gray dye + glowstone dust) → 8 powder; generated file present and `src/generated/resources` is on `sourceSets.main.resources` (build.gradle:108) | "Recipe list too short" is refuted — 3-ingredient shapeless is complete and ships |
| AUD-D-008 | — (pass) | Confirmed | spells/ (26 JSONs) vs `SpellDefinition` codec | Union of all keys used by spell JSONs (22 distinct) is fully consumed by the codec; no codec-required field is missing from any JSON (required: displayName/category/cooldownTicks/color/castType — all present); codec round-trip covered by `SpellDefinitionCodecTest` + `SpellEffectComponentCodecTest` (green) | Spell data layer is sound |
| AUD-D-009 | NICE-TO-HAVE | Confirmed | `data/wizards_and_beasts/wizards_and_beasts/{wand_woods,wand_cores,bench_enhancers}` | This *looks* like an accidental double-namespace nest but is the **correct** layout for the custom datapack registries (`Registry` path = `<modid>/<name>`, per `WandDatapackRegistries`) | Recorded so a future cleanup pass doesn't "fix" it and kill wand woods/cores |
| AUD-D-010 | EDGE-CASE | Confirmed | `wand/customization/WandModuleLoader.java:25` | Loader scans `wand_modules/`; no such data dir ships (all 25 modules are Java built-ins) | Fine by design (datapack extension point), but an empty-dir loader with no smoke-test data means path regressions (cf. AUD-D-002) would go unnoticed |
| AUD-D-011 | — (pass) | Confirmed | bestiary/entries (95) vs registrations vs creatures (86) | Every bestiary `entityType` resolves to a registered entity (ModEntities + ModCreatures manifest); all 86 creature defs have bestiary entries; the 9 bestiary-only entries (dementor, niffler, phoenix, thestral, augurey, bowtruckle, cornish_pixie, mooncalf, streeler) are dedicated Java entities — by design | No dangling references in the beast pipeline |

## Pass E — Known-bug verification

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-E-001 | BROKEN | Confirmed (static) | `spell/impl/Protego.java:57-61` + `entity/spell/ProtegoShieldEntity.java:228-232` | **Protego root cause.** Cast order: (57) add new `PROTEGO_SHIELD` effect → (58) `shatterExistingIfPresent` → old shield's `beginShatter()` executes `caster.removeEffect(PROTEGO_SHIELD)` + `ProtegoWardManager.remove(caster)` — **stripping the effect added one line earlier** → (59-61) new shield spawns and registers → next tick `updatePositionFromTier` (tier 0/1) sees `getEffect(PROTEGO_SHIELD) == null` → instant `beginShatter`. Every recast while a previous shield is alive spawns a shield that dies within 1 tick (20-tick shatter anim, no function). Tier-0 lifetime (60–180t) exceeds the 100t cooldown at proficiency, so recast-while-alive is the common case | This is the reported "shield entity not spawning on real cast". **Path note:** Protego is a *bespoke Java spell* (`SpellProperties.self()`, `Spells.PROTEGO` registration, no protego.json) — the break is in the id-keyed Java tail, not pack-rewritable, and not in `SpellEffectRunner`. First-cast chain verified intact end-to-end: payloads registered (`ModNetworkSpells:86-91`), entity registered (tracking range 16), geo/anim/texture assets present, renderer registered |
| AUD-E-002 | EDGE-CASE | Confirmed | `spell/protego/ProtegoWardManager.java:20` | `CASTER_TO_ENTITY` cleaned only on `LevelEvent.Unload`; no logout cleanup. Cross-dimension stale ids self-heal (`instanceof` miss → remove) | Tiny unbounded map on long-running servers; also the stale entry makes the *first* cast after relog trigger the AUD-E-001 path via a dead id (harmless: `getEntity` misses → remove) |
| AUD-E-003 | — (fixed) | Confirmed | `spell/gamp/GampsLaw.java:28-32` | **Gamp NPE fix present and complete**: `validate()` null-guards `ctx.definition()` (bespoke Java spells carry null definitions) before touching `gampDomains()`/`previewProducedItem` (itself `@Nullable`-guarded at :40-41) | Design consequence worth knowing: bespoke Java spells bypass Gamp's Law entirely — currently fine (no bespoke spell conjures food/currency) |
| AUD-E-004 | — (refuted) | Confirmed | see AUD-D-005 | Broom `acceleration` codec range | Fixed |
| AUD-E-005 | — (refuted/fixed) | Confirmed | see AUD-D-006 | `niffler_shiny` tag | Present and loading |
| AUD-E-006 | — (refuted) | Confirmed | see AUD-D-007 | `floo_powder.json` recipe | Complete via datagen |
| AUD-E-007 | — (WIP state) | Confirmed | entity/niffler/** (25+ files) | **Niffler status (read-only, untouched):** entity + baby variant, 6 AI goals, pouch inventory/menu/screen, happiness attachment + tick handler, carried-niffler attachment + login sync (`NifflerCarrySyncS2CPayload` in `syncFullLoginState`), spawn handler, commands, GeckoLib assets, loot tables in the correct singular `loot_table/entities/`. Compiles; both shiny-seek goals reference live tags. One dead hook: `HappinessTickHandler.java:45` `TODO(effects): SpellPowerModifier.apply()` — happiness currently has no spell-power consequence | Healthy WIP; nothing broken found beyond the unwired effects hook |

## Pass F — Gating consistency (ModuleManager)

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-F-001 | BROKEN | Confirmed | `spell/beam/WandBeamSpellHandlers.java:110-146` | `handleAvada` (BEAM_LETHAL kill: `hurt(…, 1_000_000f)`) has **no** `Module.DARK_ARTS` check, and neither does its caller `WandBeamChannelLogic:155`. Contrast: `handleCrucioChannel` gates at :150, `ImperioServerLogic` at :43, cruciatus *effect application* at `Spell.java:217-222` | With DARK_ARTS at its default DISABLED, a player who knows avada_kedavra still one-shots anything. The module gate is inconsistent exactly on the most lethal path |
| AUD-F-002 | BROKEN | Likely | `spell/learning/SpellLearningService.java` (whole file) | No `Module.DARK_ARTS` reference in the learning service or `SpellCastService` — learning/casting gates are proficiency/requirement-based only | Dark spells are learnable while the module is off; combined with AUD-F-001 the DARK_ARTS switch doesn't actually contain the Unforgivables. (Likely not Confirmed: an upstream UI gate may hide the learn path — not found in this sweep) |
| AUD-F-003 | — (pass) | Confirmed | ModuleManager.java + call sites | "Gate, don't delete" holds: registration is never conditional on module state (explicit comments at CREATURES:26-28, AZKABAN:33-35); access points gate instead (17 dark-artefact items, creative tabs at `ModCreativeTabs:140/149`, floo, brooms, pockets, handbook, etc.) | Architecture rule intact |
| AUD-F-004 | EDGE-CASE | Confirmed | `skill/SkillSystemAPI.java:84-107` | Mastery-cap/opposition enforced centrally in `evaluateUnlock` (`VocationHelper.unlockState`, single gate as designed). Slip-throughs: (1) `forceUnlock:155` bypasses everything — admin-only, by design; (2) the gate is *unlock-time only* — skills unlocked **before** committing an opposing vocation are not re-validated at commit time (no revalidation found in `VocationManager.commit` path) | A player can max Dark Arts nodes, then commit Healer: opposition lockout never fires retroactively. Whether that's intended "grandfathering" needs a design ruling |
| AUD-F-005 | IMPROVEMENT | Confirmed | `registry/ModBlocks.java:245` | Stale TODO: "gate behind STRUCTURES module when added" — `Module.STRUCTURES` exists (ENABLED) but this block's access path was never gated | One decorative block family skips its intended gate |
| AUD-F-006 | — (pass) | Confirmed | `skill/vocation/VocationManager.java:97` | Dark-arts vocation commit correctly double-gated (`SKILL_TREES` + `DARK_ARTS`) | — |

## Pass G — Edge cases & robustness

| ID | Severity | Confidence | File:anchor | Problem | Why it matters |
|---|---|---|---|---|---|
| AUD-G-001 | — (pass) | Confirmed | `registry/ModAttachments.java` | All 26 player attachments have `serialize` (codec or NbtSerializable bridge) and `copyOnDeath()` — **except** `carried_niffler` (serialized, not death-copied) and `wand_resonance_cache` (cache, intentionally transient) | Attachments survive death/dimension/relog as intended. Verify `carried_niffler` no-copy is deliberate (carried beast dropped on death?) — flagged, not judged |
| AUD-G-002 | — (pass) | Confirmed | `sync/PlayerStateSyncService.java:39-48` + `WizardsAndBeastsCommands.java:41-75` | Login/respawn/dimension-change all route through `syncFullLoginState` (spells, skills, vocations, abilities, wards, heritage, vault, azkaban, niffler-carry) + stats + OWL; datapack registries sync via `OnDatapackSyncEvent` (bestiary entries, handbook) | Prior audit's login-sync gaps are closed |
| AUD-G-003 | — (fixed) | Confirmed | `spell/imperio/ImperioServerLogic.java:166-181` | Prior Imperio map leak fixed: `onLogout` clears `CASTER_TO_VICTIM` (both directions) + `LAST_RESIST_PACKET_TICK` | — |
| AUD-G-004 | EDGE-CASE | Confirmed | `network/spell/SpellCastC2SPayload.java:30` | `IGNORE_RELEASE_UNTIL_GAME_TICK` entries are only removed when a *later* release from the same player arrives after expiry; no logout cleanup | Slow unbounded growth on long-running servers (one Long per player who ever triggered a server-driven release) — same family as the fixed Imperio leak |
| AUD-G-005 | IMPROVEMENT | Confirmed | `event/skill/ElfAbilityHandler.java:42-51` | `pullNearbyItems` runs **every tick** per player with `elf_nimble_fingers` (AABB item scan, no modulo); `free_elf` branch also calls `removeEffect` ×2 every tick | Small radius keeps it cheap, but it's the only unthrottled per-tick entity scan left; every other scanner is throttled (map tracker adaptive 5-12t + round-robin, pocket-dim 10/20t, obscurial hostile scan, patronus aura) |
| AUD-G-006 | EDGE-CASE | Confirmed | `bestiary/BestiaryEntryRegistry.java:9-18` (pattern also in Broom/Creature registries) | Reload swap is `clear()` + `putAll()` — a non-atomic window where readers see an empty/partial registry; `ENTRIES` in BestiaryEntryRegistry is additionally a plain `HashMap` (Broom/Creature use ConcurrentHashMap) | Benign while apply() runs on the paused server thread, but any async reader (network encode, off-thread chat) during `/reload` could observe empties. A volatile-swap of an immutable map (as `CLIENT_ENTRIES:12` already does!) is the established in-repo pattern |
| AUD-G-007 | EDGE-CASE | Confirmed | `client/entity/DementorRenderer.java:63-67` | Muggle-view derives from `ClientHeritageDataState` (synced attachment mirror) at capture time — correct thread, but if the heritage sync packet hasn't landed yet (first frames after login), `getSelectedHeritage()==null` ⇒ every Dementor renders invisible until sync arrives | Cosmetic-only ordering dependency between login sync and first render; worth knowing when debugging "invisible dementor" reports |
| AUD-G-008 | EDGE-CASE | Confirmed | `spell/cast/SpellCastService.java:133-145` | Cooldown clock invariant (monotonic `getGameTime()`, absolute expiry, cross-dimension) is documented and consistently used — **pass**, recorded because it's the kind of invariant that silently breaks; the comment is the only enforcement | Future edits substituting `getDayTime()` would corrupt persisted cooldowns; consider a test |
| AUD-G-009 | EDGE-CASE | Confirmed | `event/trunk/PocketDimensionEvents.java:53` | `pendingLadderSounds` is a plain `HashMap` mutated from event handlers; safe only under the single-server-thread assumption (holds today) | Fragile if any scheduling ever moves off-thread; ConcurrentHashMap is used for every comparable map elsewhere |
| AUD-G-010 | — (pass) | Confirmed | stat/proficiency boundaries | Boundary logic covered by green tests: `PacketCodecBoundsTest`, migrator tests, `WandStatsTest`, `VocationUnlockStateTest`, `ObscurialTierRulesTest` | Integer/enum boundary risk is test-fenced |

---

## Open questions (surfaced, not assumed)

1. **AUD-D-004** — is `pocket_templates/` a dead remnant or an unbuilt loader for the Room-of-Requirement system? The JSON schema (`templateId`, `maxRadius`, `allowedBuildRadius`, `instabilityCost`, `intentTags`) implies a designed system with no code behind it.
2. **AUD-F-004** — is pre-commit skill "grandfathering" past the vocation opposition lockout intended?
3. **AUD-G-001** — is `carried_niffler` intentionally not `copyOnDeath`?

---

# Appendix: Refactor Decision Log (from REFACTOR_DECISIONS.md)

**Source:** `REFACTOR_DECISIONS.md` (archived; merged here 2026-07-13). Documents the `refactor/architecture-foundation` branch decisions.

## Architecture Decisions

### AD-001 — Keep feature-rooted package layout; do not introduce `common/` super-package
**Problem:** Target layout in the refactor brief nests all non-client code under `common/`. Current layout roots ~30 feature packages directly under `at.koopro.wizardsandbeasts` with client code already isolated under `client/` and data-driven loaders under feature `data/` subpackages.
**Decision:** Keep the existing layout. Moving ~600 files into `common/` would touch every import, the mixin config, and AT entries for zero behavioral or modularity gain — the brief's actual goals (client/common separation, service boundaries) are already met by the current structure or fixed surgically below.
**Rationale:** "Deviate only where the audit reveals a good reason" — churn/risk vastly outweighs benefit; git history readability preserved.
**Files affected:** none (deviation decision).

### AD-002 — Extract client payload handlers out of S2C payload records (dist-safety)
**Problem:** 26 S2C payload classes under `network/**` contain a static `handleClient` whose body imports client-only classes (`net.minecraft.client.Minecraft`, `client/**` caches, screens). Client logic lives in common network classes; brief forbids client imports reachable from common code.
**Decision:** Payload records become pure data (TYPE + STREAM_CODEC + send helpers). Handler bodies move to per-feature `client/network/<Feature>ClientPayloadHandlers` classes. Common `ModNetworkX` registrars reference handlers via method reference — the official NeoForge-documented pattern: the handler class's client-only references live only in method bodies (resolved lazily, never executed on the dedicated server), and its loadable surface (signatures, fields, supertypes) is dist-safe.
**Rationale:** Matches NeoForge networking docs; removes all client classloading risk from payload classes; one consistent home for client packet handling.
**Files affected:** `network/**` payloads (26), `network/**/ModNetwork*` registrars, new `client/network/*ClientPayloadHandlers` classes.

### AD-003 — Move `MuffliatoSoundHandler` to `client/event`
**Problem:** `event/spell/MuffliatoSoundHandler` is a `Dist.CLIENT` subscriber using `Minecraft.getInstance()` but lives in a common package.
**Decision:** Move to `client/event/MuffliatoSoundHandler` unchanged.
**Files affected:** `event/spell/MuffliatoSoundHandler.java` → `client/event/`.

### AD-004 — `WandItem` must not import `ClientPacketDistributor`
**Problem:** `item/wand/WandItem` (common) imports `net.neoforged.neoforge.client.network.ClientPacketDistributor` inside a `level.isClientSide()` branch.
**Decision:** Route the send through the existing `ClientClassBridge` reflection bridge / client hook class so the common item class has zero client imports (consistent with how the same class already bridges `WandRenderer` and `WandCastClient`).
**Files affected:** `item/wand/WandItem.java`, `client/wand/WandCastClient.java`.

### AD-005 — `/wandb debug beam preset` must not mutate client statics from a server command
**Problem:** `command/WandbCommands.setBeamPreset` calls `client.wand.BeamSettings.applyPerformancePreset` directly. Works only because integrated-server shares the JVM; on a dedicated server the command would classload (and uselessly mutate) a client class server-side.
**Decision:** Send the preset to the executing player via the existing beam-debug S2C channel (`spell/beam/ModNetworkBeamDebug`); preset applied client-side. No new gameplay — debug command behavior identical in singleplayer, actually-correct in multiplayer.
**Files affected:** `command/WandbCommands.java`, `spell/beam/ModNetworkBeamDebug.java`, new payload + client handler.

### AD-006 — `ExaminationDeskBlock.onClientInteract` static hook: keep, documented
**Problem:** Common block holds `public static @Nullable Runnable onClientInteract`, set during `FMLClientSetupEvent`. Static mutable state in common code.
**Decision:** Keep. It is the minimal dist-bridge for a client screen open, null-guarded, documented in-source, and write-once at client init. Replacing it with a payload or reflection bridge adds machinery for no behavior gain.
**Files affected:** none.

### AD-007 — God classes
- `trunk/PocketShellGenerator` (588 ln): split Scamander-sanctuary furnishing (~250 ln of zone/floor/shed/feature placement) into package-private `PocketSanctuaryFurnisher`. Generator keeps shell (floor/walls/ceiling/markers/biome paint) orchestration.
- `spell/effect/SpellEffectComponent` (481 ln): sealed vocabulary interface with 13 nested record implementations. **Kept as one file** — each record is <30 lines, the file is a single closed vocabulary (codec registry + variants), and splitting a sealed hierarchy across 14 files hurts readability of the codec dispatch. Re-evaluate if variants grow past ~20.
- `datagen/ModLanguageProvider` (458 ln): translation list; inherently linear, single responsibility. Exempt.
- `entity/niffler/NifflerEntity` (427 ln): cohesive entity logic (bond/pouch/peek are entity state machines over synched data). Marginal overage; splitting would scatter `entityData` access. Exempt.

## Lore Fixes (Autonomous)

*(none required — audit found no canon violations; section retained for completeness)*

## Lore Issues (Flagged — Ambiguous)

*(none found)*

## Performance Changes

### PC-001 — `CloakEffectsHandler`: stop per-tick equipment broadcast + per-tick set allocation
**Problem:** `onServerTick` allocates two `HashSet`s every server tick and, while a Deathly-Hallow cloak is worn, re-sends a full `ClientboundSetEquipmentPacket` to every observer **every tick**.
**Change:** Broadcast only on state transitions (cloak on/off, deathly on/off) plus the existing StartTracking/login/respawn/dimension resyncs, and re-mask on `LivingEquipmentChangeEvent` for ServerPlayers so vanilla equipment updates can't leak through mid-cloak. Tick handler mutates the persistent sets in place instead of rebuilding.
**Expected impact:** Removes O(players²) packet spam per tick in the deathly-cloak case; removes 2 allocations/tick baseline.

### PC-002 — `BestiaryDiscoveryHandler.onPlayerTick`: hoist invariants, fix unbounded cooldown map
**Problem:** Per nearby entity × per bestiary entry: string-concat cooldown key (`uuid + "|" + id`), `PlayerSkillBonusData.forPlayer(player)` re-fetched, registry rescan. `PROX_COOLDOWNS` map keyed by UUID-string never evicted on logout.
**Change:** Hoist skill-bonus lookup and game time per tick invocation; pre-index PROXIMITY entries by entity type id once per scan; evict cooldown entries on `PlayerLoggedOutEvent` (here and in `NifflerEventHandler`).
**Expected impact:** Per-second scan cost drops from O(nearby × allEntries) with allocations to O(nearby) map hits; no unbounded retained memory.

### PC-003 — `PocketDimensionEvents`: world-border-wide ItemEntity scan every tick
**Problem:** `onLevelTick` built a world-border-sized AABB and ran `getEntitiesOfClass(ItemEntity...)` over it **every level tick** to find unsecured trunks (whose escape action is currently a log-only stub). `onPlayerTick` additionally ran a 4-block ItemEntity scan every tick per player for the latch warning. `TrunkRegistryData.get(level)` was fetched per trunk inside the loop.
**Change:** Escape scan gated to every 20 ticks with a compensated 1-in-10 roll (identical expected escape rate to the former 1-in-200 per tick); registry data fetch hoisted out of the loop; latch warning scan gated to every 10 ticks (overlay message persists ~60 ticks, so it remains continuously visible). Constants extracted (`ESCAPE_SCAN_INTERVAL_TICKS`, `ESCAPE_CHANCE_PER_SCAN`, `LATCH_WARNING_SCAN_INTERVAL_TICKS`).
**Expected impact:** ~95% reduction of the most expensive recurring entity scan in the mod.

### AD-008 — Dedicated-server boot exposed two latent dist-load crashes (pre-existing on main)
**Problem:** Booting the dedicated server (`./gradlew runServer`) to validate AD-002 surfaced a `NoClassDefFoundError` during `RegisterPayloadHandlersEvent`. Two payload handlers performed an operation that forces the bytecode verifier to *resolve a client-only class at registration time* (when the registrar resolves the handler method reference, the handler class is loaded and verified server-side):
1. `ClientPayloadHandlers.handleOpenFlooGui` did `new FlooNetworkScreen(...)` + `setScreen(Screen)` inline → verifier loads `net.minecraft.client.gui.screens.Screen`.
2. `SpellClientPayloadHandlers.handleSpellDenied` did `getSoundManager().play(SimpleSoundInstance.forUI(...))` → verifier checks `SimpleSoundInstance <: SoundInstance` → loads `net.minecraft.client...SoundInstance`.
Both were **pre-existing on `main`** (the original `OpenFlooGuiS2CPayload.handleClient` had the same inline `new FlooNetworkScreen`); the STOP-2 client-import debt was never dedicated-server-tested, so the crash shipped latent. Method *bodies* referencing client classes are fine; `new <clientType>` and client→client subtype-merges are not — the verifier resolves those eagerly.
**Decision:** Route both through dist-safe sinks already used elsewhere: Floo screen-open goes through `ClientScreenHooks.openFlooNetworkScreen(List<FlooDestinationDto>)` via a new one-arg `ClientScreenHooksInvoker.invoke(name, paramType, arg)` reflection overload; denied-cast sound goes through `SpellVfxClient.playDeniedFeedback()`. Neither handler class now contains a `new <clientType>` or client-subtype-merge, so both verify and load cleanly server-side.
**Verification:** `./gradlew runServer` reaches `Done (0.436s)` with zero "not present on the dedicated server" errors. This is the proof AD-002 needed — compile-clean did not catch it; only a real server classloader did.
**Files affected:** `client/network/ClientPayloadHandlers.java`, `client/spell/network/SpellClientPayloadHandlers.java`, `client/network/ClientScreenHooks.java`, `client/spell/SpellVfxClient.java`, `network/ClientScreenHooksInvoker.java`.

## Skipped Items

### SK-001 — Hardcoded content (registries built in static initializers)
`FormRegistry`, `SizeProfileRegistry`, `TransformationConfigRegistry`, `Heritage`, `HeritageVariant`, `ProfessionNode`, `PowerBandTable`, `WandCore`, `WandWood` build their content in static initializers. Datapack-extensible candidates, but datapack migration is out of scope per brief. Flagged, unchanged.

### SK-002 — Client-state singletons (static mutable fields under `client/**`)
~25 client classes keep session state in static fields (input controllers, sync caches, HUD state). Acceptable on the client (single instance per JVM); converting to instance singletons is churn without benefit. Unchanged.

### SK-003 — `AzkabanStructures.cachedFortressCenter` volatile static cache
Per-JVM cache of the one-per-world fortress center. Correct for single-world servers; would need per-`ServerLevel` keying for multi-dimension reuse, but the structure is overworld-pinned by design. Unchanged.
