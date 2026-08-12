# Worklog

> **Reverse-chronological build log** — each build pass has an "Audit" subsection (findings) followed by "Resolution" subsection (deltas). Sources: `MIGRATION_DELTAS.md` (resolution deltas) + `AUDIT_PUNCHLIST.md` (audit findings + fix passes). Merged 2026-07-13.

---

## 2026-07-18 — Skill audience & access: tradition rule + capability gating

### Audit
- Obscurial `canUseWand=false` deviates from film canon (wand-wielding Obscurial appears in later *Fantastic Beasts* films) — deliberate mechanical choice, not lore fact.
- No-heritage crafted-payload access unchanged (pre-existing): player with no heritage resolves to historical `WIZARD` default in `audienceForHeritage(null, null)`, so crafted `SkillUnlockC2SPayload` could allocate `NONE`-requirement wizard regions before selecting heritage.
- `no_casting` is now a live capability tag (squib + both obscurial variants). Other dead tags (`innate_apparition`, `water_breathing`, etc.) remain punchlisted.
- Sealed-region tooltip flavor text is a placeholder (`skilltree.region.sealed.tooltip` renders raw key fallback `"Sealed"`).

### Resolution
- **Audience resolution made explicit and variant-aware; default fallthrough deleted.** `SkillTreeId.audienceForHeritage(Heritage)` (silently returned `WIZARD` for everything non-goblin/elf) replaced by `audienceForHeritage(@Nullable Heritage, @Nullable HeritageVariant)` + `audienceForVariant(HeritageVariant)`, backed by explicit `EnumMap<HeritageVariant,Audience>` (all 30 variants) and `EnumMap<Heritage,Audience>` null-variant fallback (all 10 heritages). Both maps totality-checked in static initializer — missing entry = **boot-time crash**, never silent misroute.
- **`Audience` enum gains `VEELA, CENTAUR, MERPEOPLE, GIANT`.** Ruling: wizardkind (incl. squib), werewolf, obscurial, vampire, half/quarter-veela, and half-giant → `WIZARD`; goblin/house-elf/centaur/merpeople, full-veela, and full-giant/clan-warden → their own audience. **clan_warden resolves to GIANT** (its data shows no human parentage — a role within full-giant clans, not a mixed birth; only `half_giant` is the mixed lineage).
- **Obscurial + squib gain partial wizard-web access (access expansion only, no loss).** Both resolve to `WIZARD` audience and open the wizard chart. Within it, Virgo/Monoceros/Fornax (magizoology/herbology/alchemy, `Requirement.NONE`) allocatable end-to-end from Polaris; Orion/Serpens (spell_mastery/dark_arts, `CASTING`) and Sagitta (wandlore, `WAND`) render sealed. Previously the client router denied these profiles the screen entirely (`muggle_like` path) — that was the live OBSCURIAL bug. No prior access removed for anyone.
- **Hardcoded WANDLORE wand check migrated into general region-requirement mechanism.** Region capability now a `SkillTreeId.Requirement` property (`NONE`/`WAND`/`CASTING`) on the closed tree enum (deliberately code, not datapack — trees are a closed set). `wandlore ⇒ WAND`, `spell_mastery + dark_arts ⇒ CASTING`, all others `NONE`. `SkillSystemAPI.isTreeAvailable` now pure audience check; old `if (tree == WANDLORE) …` special case deleted and re-expressed as `meetsRequirement(WAND, …)` = identical predicate (`canUseWand && !no_wand`). `evaluateUnlock` gains requirement step ordered strictly **after** audience check (chain: module → maxed → affordability → adjacency → audience → requirements); everything before requirements byte-preserved. Crafted payloads for sealed regions rejected server-side (new reason `requirement_unmet`). New capability tag `no_casting` added to `squib` and both obscurial variants (`suppressed`, `unleashed`) via existing `HeritageVariant` tag set.
- **Client denial screen repurposed, not deleted.** `SkillScreenRouter` now routes purely by resolved audience: player with heritage always opens their chart (capability gating happens inside as sealed regions, keyed off same synced heritage state server enforces — no client re-derivation from `canUseWand`). `muggle_like` denial path removed; goblin/elf special-case branch collapses into uniform route. `SkillAccessDeniedScreen` retained solely for no-heritage case (`no_type`) and generic-error guard. Module-disabled gating stays server-authoritative (router does not re-check `Module.SKILL_TREES`; server returns `module_disabled`). Sealed regions render permanently-locked by **reusing** existing locked ember node style (seal cue in tooltip line, placeholder lang key `skilltree.region.sealed.tooltip` with terse `"Sealed"` fallback — no flavor text authored here).
- **Per-audience point-cap hook added, all `60`, zero behavior change.** `SkillSystemAPI` gains `EnumMap<Audience,Integer>` initialized to `MAX_SKILL_POINTS` for every audience (`// TUNE`), read via `pointCapFor(audience)` and branched at `awardPoints` seam (`addSkillPoints(amount, cap)` overload; old single-arg delegates with `MAX_SKILL_POINTS`). Because every cap equals 60 today, earning byte-identical — asserted by unit test over all audiences.
- **Empty-web tripwire (guard for the guarded case).** `SkillNodeLoader.apply` warns loudly if any *committable* heritage (`isAlphaAvailable` = wizardkind/werewolf/obscurial, all → `WIZARD`) resolves to audience with zero nodes. Coming-soon heritages mapping to node-less new audiences expected and stay unreachable (selection gated on `isAlphaAvailable`); tripwire silent for them and never crashes.

---

## 2026-07-10 — Skill Web Rework Phase 5: Star-Chart Skin

### Audit
- Grid-era `WizardsAndBeastsUiTokens.SkillTree` constants (NODE_X_SPACING, TAB_*, GRID_*, PAN_MIN/MAX, VIEWPORT_BG, NODE_*) and Phase 2 `SkillTreeGuiTextures` class + its node PNG set are now fully orphaned — flag for deletion pass.
- Server-side chat feedback (`/wandb skill unlock/info/list`) prints filler display names as raw lang keys — server can't resolve client lang; fix is switching ChatHelper messages to translatable Components (separate pass, touches command text conventions).
- `SkillAccessDeniedScreen` and `SkillScreenRouter` still use old plain look — cohesive but not chart-styled; cosmetic only.

### Resolution
- **Raw-lang-key rendering fixed** via translate-with-fallback: `SkillTreeRenderHelper.resolveDisplayName` (`I18n.exists ? I18n.get : literal`) applied at every client draw site — canvas tooltip title and SkillsTab unlocked-node chips. Phase 4 fillers and Polaris now show real names; legacy inline-English notables render byte-identically (the fallback IS the literal — the display-string debt stays punchlisted, untouched). Server chat messages (`/wandb skill` command feedback) still print raw keys for fillers — server-side, out of scope, punchlisted.
- **Cosmetic reskin (no behavior):** night starfield tile (procedural, seeded, tiled at 1:1 with ~0.3× pan parallax), three drawn survey rings at notable-band radii (120/200/280) centered on Polaris, nodes as tinted star sprites (core/ring/flare per size; state = brightness + shape: locked dim-ember dot, allocatable white core + region-tinted rim ring, allocated gold diffraction flare + hot core; distinct 8-point Polaris sprite), region tint map (locked palette incl. neutral tints for goblin/elf webs so they render with zero special-case code), ley-line edges (locked hairline / frontier region-tint lift / allocated gold 2px with slow global alpha shimmer), constellation labels at cached region centroids (italic, tinted, fading out as zoom passes ~0.9× toward build view), star pips, gold-on-night footer and tooltip.
- **Textures:** 11 PNGs committed under `textures/gui/skill_tree/chart/`, generated deterministically by `tools/skill_chart_textures.py` (repo's existing Python tooling convention; regenerable). Sprites are white/grayscale, tinted at draw via the color `blit` overload — vanilla `GuiGraphics` + `RenderPipelines.GUI_TEXTURED` only, no custom pipelines.
- **Performance:** off-viewport nodes and edges culled before draw (±48px pad); label centroids and translated components computed once per graph sync (list-identity check), not per frame; no per-frame allocation added to the render loop.

---

## 2026-07-10 — Skill Web Rework Phase 4: Wizard Web Content — Geometry, Fillers, Constellations

### Audit
- spell_mastery (Orion) full clear from Polaris = 59 SP of the 60 cap — technically legal but a knife-edge; one more notable or filler in Orion breaks the cap. Design review before Phase 5 content lands there.
- Filler/Polaris display names are lang keys rendered raw by the canvas until Phase 5 adds translation at draw time (single `Component.translatable` call in the tooltip/pips path).
- unlock_ability notables (e.g. basic_casting, green_thumb, creature_knowledge) have no scalable magnitude — fillers in those regions derive from region's other stat types instead; if a future region ships ONLY ability unlocks, the §3.3 derivation rule has no input (stop condition documented in Phase 4 prompt).
- Goblin/elf webs remain 5 nodes each vs the 60 cap (pre-existing gap, restated).

### Resolution
- **Travel cost is the intended behavior change.** Notables no longer sit edge-to-edge: Phase 2 prereq-derived edges rewired through filler chains (§3.4 contract — every legacy notable–notable/core edge still connected, interiors all fillers, asserted in `SkillNodeJsonTest.legacyConnectivityContractHolds`). Reaching and clearing regions costs more points than in Phase 3; see economy table in phase report (worst: spell_mastery full clear = 59 of the 60 cap — knife-edge, flagged for design review).
- **Three cross-region pathways add connectivity that never existed:** wandlore↔herbology (arcane_reserve–potion_potency), herbology↔magizoology (harvest_bounty–beast_handler), alchemy↔wandlore (transmute_focus–quick_cast), each a 4-filler chain crossing an open border. All other borders sealed — Dark Arts (Serpens) reachable only via its own spoke; geometric isolation in addition to `Module.DARK_ARTS` gating, never instead of it.
- **100 filler nodes added** (size "small", cost 1, maxLevel 1, exactly one effect each): 14 per region (3-filler spoke trunk from Polaris + 11 woven into rewired edges), 4 per open pathway, 4 in the Polaris cluster. Effects derived at ≈25% of the region's typical notable magnitude, never invented (per-region stat menu; magnitudes `// TUNE` in generator constants, now baked in JSON). Ids: `<tree>_minor_<stat>_<n>`.
- **wizard_core is now Polaris:** displayName is lang key `skill.wizards_and_beasts.node.polaris`, gains one universal effect (+0.5 max_health `// TUNE`), still root, cost 1.
- **New nodes use lang keys in `displayName`** (`skill.wizards_and_beasts.filler.*`, `.node.polaris`) while canvas still draws raw strings — keys render literally until Phase 5 translates them. Accepted interim state per "no rendering changes" constraint; legacy notables keep their inline-English strings (punchlisted debt, untouched).
- **Constellation identities ship as data:** `skilltree.region.<tree>.constellation` → Orion (spell_mastery), Serpens (dark_arts), Virgo (herbology), Monoceros (magizoology), Fornax (alchemy), Sagitta (wandlore). Nothing reads them yet (Phase 5).
- **Schema v3 migration:** `PlayerSkillData.CURRENT_VERSION` 2 → 3; `needsWebMigration()` now compares against the constant, so the existing v2 login machinery (clear + full refund + clamp to 60 + per-player log + resync) re-fires exactly once for v2 saves. No migrator structural change (NBT migrator still stamps only structural v1).
- **Node totals:** 161 (51 wizard notables incl. Polaris + 100 fillers + 5 goblin + 5 elf). Prompt estimated ~46 notables/~147 total; real Phase 1 inventory was 50 wizard notables, so totals land higher. Layout fully deterministic (id-hash jitter); generator deleted after use.

---

## 2026-07-10 — Skill Web Rework Phase 3: Vocation Reframe — Identity Only

### Audit
- `VocationDefinition.capstoneNodeId` is orphaned data — its only reader (capstone gate in deleted `unlockState`) gone. No shipped vocation JSON sets it. Keep for in-region-bonus prompt or delete then.
- `commitmentEffects` + `grantedAbilities` still confer gameplay on declaration (wandlore +0.1 wand_affinity; ability flags via `VocationAbilityHooks`) — at odds with "identity only, zero gameplay effect" but outside this prompt's deletion checklist. Needs explicit ruling.
- Shipped opposition data (now deleted from JSONs) contained exactly one pair: `dark_arts ↔ healer`, and `healer` was never a shipped vocation — the pair was always inert. Recorded as Phase 4 layout design input (dark arts vs. restoration should sit far apart on the web).

### Resolution
- **Vocation enforcement deleted.** The vocation step in `SkillSystemAPI.evaluateUnlock` gone (mastery-band gate, secondary-vocation first-tier rule, opposition lockout, capstone gate). Remaining chain — module gate → maxed → affordability → adjacency → audience (incl. wandlore wand check) — byte-preserved in order. Allocation outcomes differ from Phase 2 only where vocation step previously rejected (`vocation_locked:*` reasons no longer exist).
- **`tier` removed from `Skill` codec, builder, and all 61 node JSONs** (the deletion Phase 2 doc block scheduled). One-off generated transform, asserted zero `tier` keys remain; transform deleted after use. `VocationHelper`'s band logic (:75/:134) — tier's only reader — died with it.
- **`VocationDefinition` schema: `foundationMaxTier` and `oppositions` removed** (codec + all 5 vocation JSONs, same transform discipline). Opposition's readers were enforcement (`unlockState`, declare-time commit check) and command's "foreclosed" display — all deleted, so field died with them. `capstoneNodeId` now **orphaned data** (only reader was deleted capstone gate); kept in schema for future use, punchlisted.
- **Secondary declarations removed** (storage slot and flow). `PlayerVocationData` primary-only; codec still *reads* legacy `secondary` key into transient marker (never re-encoded), and login migration logs one info line per affected player, strips all vocation attribute modifiers (including old 0.5-scaled secondary profile) and re-applies primary, then persists — key gone on next save, never re-fires. No version int needed: key presence *is* the migration marker.
- **`VocationManager`:** `Slot` enum gone; `commit(player, id)` declares primary directly. Deleted with mechanics: `SAME_AS_OTHER_SLOT`/`OPPOSED`/`RESPEC_REQUIRED` results, mastery-progress overwrite guard, `clear()`'s mastery-node refund sweep (clear now just strips profile and declaration — no vocation-locked nodes to refund).
- **Command changes:** `/wandb skill vocation set secondary <id>` removed; `set primary` and `info`/`clear` remain. `info` prints only declaration (mastery/foreclosed lines gone). Dead lang keys removed; `info.primary`/`set.primary_ok`/`clear.ok` reworded only to stop referencing deleted two-slot/refund model.
- **`VocationHelper` not collapsed:** keeps real surface (declaration queries, `hasGrantedAbility`, `vocationOf` home-region hook) so it stays as query twin of `VocationManager`. `getSecondary` removed; `hasGrantedAbility` now reads primary only.
- **Unchanged on purpose (open question for future prompts):** `commitmentEffects` (attribute profile via `VocationEffectApplicator`) and `grantedAbilities` (`VocationAbilityHooks`) still apply on declaration — prompt's checklist did not delete them, but they are gameplay effects, so "vocation = zero gameplay effect" not fully true yet. Flagged in report for explicit ruling.
- **`VocationDataSyncS2CPayload`/`ClientVocationCache`:** secondary field dropped from wire format and cache.

---

## 2026-07-09 — Skill Web Rework Phase 2: Prerequisite DAG → Adjacency Web

### Audit
- Goblin (5 nodes, ~7 SP total sink) and elf (5 nodes, ~7 SP) webs have far fewer point sinks than the 60-point cap — goblin/elf players will cap out with nothing to spend on. Pre-existing content gap, now more visible under the cap; Phase 3 filler nodes are the intended fix.
- `/wandb skill points set` writes unspent points directly without touching `totalPointsEarned`, so an admin can set unspent above earned and spending beyond earned becomes possible. Pre-existing admin override; the earn path (`addSkillPoints`) enforces the cap.
- `SkillTreeGuiTextures` + the skill-node PNG set are no longer referenced by the canvas (plain shapes per Phase 2 spec). Kept for the Phase 3 star-chart pass; delete then if unused.
- `WizardsAndBeastsUiTokens.SkillTree` still carries grid-era constants (NODE_X_SPACING, TAB_*, GRID_*, PAN_MIN/MAX) now unused by the canvas. Harmless dead tokens; sweep in Phase 3.

### Resolution
- **Allocation semantics: ALL-prerequisites-MAXED → ANY-neighbor-level≥1.** Phase 1 audit recorded old rule precisely: every listed prerequisite had to be at max level (`SkillSystemAPI.evaluateUnlock`, old lines 92–94). New rule: a node's *first* level is allocatable iff it is a `root` node OR any edge-neighbor is at level ≥ 1 (`not_adjacent` rejection reason replaces `missing_prerequisite:<id>`); further levels of a started node need only affordability + `< maxLevel`. Level ≥ 1 opens edges — maxing is never a gate. All other `evaluateUnlock` rules preserved verbatim (module gate, maxed, affordability, heritage-audience `isTreeAvailable`, vocation mastery-cap/opposition line).
- **Codec/JSON: `prerequisites` and `column` removed; `x`/`y`/`edges`/`size`/`root` added.** `tier` **kept** as inert vocation band index (`VocationHelper`:75/:134 consume it; zero layout/adjacency meaning; scheduled for deletion in Vocation reframe prompt). Prompt §6(b) said to remove `tier` too — deviation adopted per agreed Option A after Phase 2 stop-and-report: removing it would have redefined Foundation/Mastery band mechanic (graph-depth substitution flips secondary-vocation rule on `expecto_patronum_unlock` and `keeper_vigor`).
- **Migration: unconditional refund + earned clamp to 60.** `PlayerSkillData` schema v1 → v2 at login (player identity available for required per-player log line): allocation map cleared, `earned = min(earned, 60)`, `unspent = earned`, version stamped on instance and persisted — idempotent across relogs. NBT-level migrator now stamps only structural versions (v1) so an intermediate save can't skip behavioral migration. Entries referencing unknown ids cleared with everything else (no per-node refund math — full refund by construction).
- **Point cap introduced:** `SkillSystemAPI.MAX_SKILL_POINTS = 60` (`// TUNE`). Enforced in `PlayerSkillData.addSkillPoints` (grant clamps to remaining headroom; no-op at cap), so XP level-ups, heritage bonus, proficiency milestones AND admin `points add` all respect it. Admin `points set` still bypasses (unspent only — punchlisted).
- **8 trees → 3 per-audience webs** (wizard = 6 trees as regions of one coordinate space; goblin and elf standalone). `SkillTreeId` unchanged as region label (Vocation/OWL/audience code untouched). Edges symmetrized at load; unknown/self/cross-audience edges log-and-drop; per-web unreachable-from-root nodes warn. Placeholder layout: wizard spokes at i×60° (SkillTreeId declaration order), radius 90 + 55·BFS-depth, ±25° sibling spread; goblin/elf rings at 70·depth.
- **Center node housing: `spell_mastery`** (§3.3 rule): `SkillTreeId` is a closed enum whose extension ripples beyond skill files (SkillsTab bars, OWLGradeCalculator, VocationRegistry tree mapping). One PLACEHOLDER node `wizard_core` (root:true, no effects, loud placeholder strings) with edges to all 6 former wizard tree roots. Former wizard tree roots lost implicit root status (reachable via center); goblin/elf former roots are their webs' roots.
- **Command changes:** `/wandb skill list [tree]` keeps optional tree argument as region filter (unchanged signature). `/wandb skill info <skill>` now prints "Connected:" neighbor list (green = allocated) + web-root marker instead of prerequisite list. Unlock rejection message for adjacency: "<name> is not connected to your allocated nodes."
- **Iteration-order delta:** registry per-tree lists now sort by id (was tier,column,id in Phase 1; column removed, tier no longer a layout key). Affects only text-list ordering (`/wandb skill list <tree>`, SkillsTab chips).
- **Compile-forced Vocation touches:** `VocationUnlockStateTest.node()` helper — `.position(tier, 0)` → `.tier(tier)` (builder signature change). No production vocation file touched; `VocationHelper` reads `getTier()` exactly as before.
- **GUI:** `SkillTreeScreen` grid + per-tree tabs replaced by single pan/zoom canvas over viewer's audience web (drag-pan, cursor-anchored zoom 0.25×–2.0×, circles by `size`, level pips, lit edges when both endpoints ≥ 1, allocated/allocatable/locked states, tooltip, server roundtrip on click, earned/spent/cap footer). `SkillScreenRouter` untouched. Per-node texture set (`SkillTreeGuiTextures`) no longer referenced — kept for Phase 3 skin pass.

---

## 2026-07-09 — Skill Definitions: Java → Datapack (Web Rework Phase 1)

### Audit
- Skill node `displayName`/`description` are inline English strings, not lang keys — ported verbatim into new `skill_nodes/` JSONs per fidelity spec. Skill names/descriptions remain non-localizable (pre-existing; `SkillTreeId` display names share the problem).
- `SkillsTab` class doc says "5 tree bars" but it iterates all 8 `SkillTreeId` values (doc drift, pre-existing). `src/main/java/at/koopro/wizardsandbeasts/client/gui/character/tab/SkillsTab.java:20`.
- `PlayerSkillData.resetAll`/`resetSkill` refund `pointCost × level` only for ids that resolve — an allocation whose definition is missing (now reachable: datapack removed the node) refunds 0 and stays in NBT. Points stranded until node returns or admin `points set`s. Pre-existing arithmetic, newly reachable.

### Resolution
- **Definitions moved Java → datapack.** The 8 static `*Skills.java` classes (60 `Skill.Builder` nodes) deleted; nodes now load from `data/wizards_and_beasts/skill_nodes/<tree>/<id>.json` via `SkillNodeLoader` (`SimpleJsonResourceReloadListener`, `BroomDefinitionLoader` pattern). JSONs were **generated, not hand-typed**: one-off extraction test serialized live Java objects through new `Skill.CODEC` and asserted encode→parse→encode idempotence plus field-by-field equality before Java classes deleted (extraction test deleted after use). Node id is JSON `id` field (plain string, byte-identical to old ids), not file path.
- **Prerequisite semantics (recorded for Phase 2, unchanged here):** ALL prerequisites in the list must be satisfied, and each prerequisite must be **maxed** (`PlayerSkillData.isMaxed`), not merely allocated (`SkillSystemAPI.evaluateUnlock`).
- **New capability: `/reload` hot-swaps skill definitions.** `SkillTrees` now a volatile-swapped reload-backed registry (server map + client cache). `SyncSkillDefinitionsPayload` pushes full definition list on player login and on `/reload` (`OnDatapackSyncEvent`, bestiary/handbook precedent), so changes appear client-side without relog.
- **Client definition source:** client GUI code (`SkillTreeScreen`, `SkillsTab`) now reads synced cache (`SkillTrees.clientById`/`clientGetTree`) instead of classpath statics; required for dedicated servers. Server logic keeps reading server side (`byId`/`getTree`).
- **Missing-definition tolerance:** if player attachment references node id absent from loaded datapack, every consumer already skipped unknown ids (`byId == null → continue`) — behavior preserved: allocation stays in NBT (inert, not deleted) and effects simply don't apply. Loader logs unknown prerequisites (node becomes unobtainable, load continues) and duplicate ids (first definition wins).
- **Validation timing:** old `SkillTrees.init()` threw at mod-init on unknown prerequisite; a datapack cannot hard-crash server, so loader **logs an error instead of throwing**. Unit test (`SkillNodeJsonTest`) still fails build if *shipped* JSONs have duplicate ids, unresolvable prerequisites, or parse errors.
- **Ordering delta (cosmetic):** per-tree node lists now sorted (tier, column, id) instead of Java class-registration order. GUI node positions are tier/column-driven and unchanged; only iteration order (e.g. `/wandb skill list <tree>` line order, SkillsTab chip order) can differ.
- **Vocation touch: none.** `VocationHelper`/`VocationManager` signatures consume `Skill` objects, which survived as runtime type; no vocation file modified.

---

## 2026-07-06 — Full audit sweep + fix pass

### Audit (read-only sweep — see FULL_AUDIT_REPORT.md for evidence)

#### BLOCKER
- **AUD-D-001** Wandmaking recipes never load: move 6 JSONs from `wandmaking_recipes/` → `recipe/` AND add `"type": "wizards_and_beasts:wandmaking"` to each. Wandmaker's Bench currently can never output a wand (`WandmakersBenchMenu.findRecipe` finds nothing).

#### BROKEN
- **AUD-E-001** Protego recast self-shatter: `Protego.executeCast` adds new PROTEGO_SHIELD effect (L57) *before* `shatterExistingIfPresent` (L58); old shield's `beginShatter` removes caster's effect (ProtegoShieldEntity:229-231) → new shield dies next tick. Reorder (shatter first, then add effect + spawn). Root cause of "shield not spawning".
- **AUD-D-003** Move `tags/items/` (plural, dead in 1.21) → `tags/item/`: `broom_repair_material_elite.json`, `broom_repair_material_racing.json`, `brooms.json`; delete stale `cannot_conjure/` duplicates. Broom repairMaterial tags currently empty.
- **AUD-F-001** Gate `WandBeamSpellHandlers.handleAvada` (and/or `WandBeamChannelLogic` BEAM_LETHAL branch) on `Module.DARK_ARTS` — Avada kills while module is DISABLED; Crucio/Imperio are gated.
- **AUD-D-002** Brew loaders scan MODID-prefixed dirs (`data/wizards_and_beasts/wizards_and_beasts/brews|brewing_recipes`) but JSONs are un-nested → never load. Fix DIRECTORY constants or move files.
- **AUD-C-001** `WandTipWorldCache.WAND_TIP_BONE="wand_tip"` — bone doesn't exist in master wand.geo (only elder skin). Beam origin always uses fallback math. Point at existing anchor (e.g. `fx_tip_anchor`) or add the bone.
- **AUD-D-004** `pocket_templates/` (3 JSONs) has no loader — decide: wire a loader or delete the data. (Open question #1.)
- **AUD-F-002** Spell learning has no DARK_ARTS gate — dark spells learnable while module disabled (Likely; verify UI path first).

#### POLISH
- **AUD-C-002** `WandTipWorldCache` never cleared → stale beam anchor after wand switch. Clear per-frame or on item change.
- **AUD-F-004** Vocation opposition lockout is unlock-time only; skills unlocked pre-commit are grandfathered. Needs design ruling ± commit-time revalidation.
- **AUD-G-004** `SpellCastC2SPayload.IGNORE_RELEASE_UNTIL_GAME_TICK` — add logout cleanup (same family as fixed Imperio leak).
- **AUD-E-002** `ProtegoWardManager.CASTER_TO_ENTITY` — add logout cleanup.
- **AUD-G-005** `ElfAbilityHandler.pullNearbyItems` — throttle (only unthrottled per-tick entity scan left).
- **AUD-F-005** `ModBlocks.java:245` stale TODO — STRUCTURES module exists; apply the gate.
- **AUD-B-007/008** Nullability: migrate ~30 jetbrains/javax annotation files to JSpecify; annotate the 19 `return null` files with zero `@Nullable` (top: BestiaryScreen, MirrorSessionManager, ColoredGlowRenderer, Brew*Definition).

#### NICE-TO-HAVE
- **AUD-C-005** `WandModel.getAnimationResource` returns null — latent NPE if controller ever added to WandItem.
- **AUD-G-006** Registry reload swap: adopt volatile immutable-map swap (pattern already in `BestiaryEntryRegistry.CLIENT_ENTRIES`) instead of clear+putAll.
- **AUD-A-003** TODO inventory: 16 sites; live wiring gaps = HappinessTickHandler effects hook, protego audio assets ×4.
- **AUD-C-007** WandRenderer "flat siblings" comment misdescribes geo (variants are children of container bones).

### Fix pass 2026-07-06 (same day)
All BLOCKER/BROKEN + most POLISH items above fixed and boot-verified (dedicated server: brews 1+1 loaded, 26 spells, 86 creatures, 0 tag failures, wandmaking recipes in RecipeManager — `isSpecial()` added to silence placement warning; MC source confirms recipes stay in RecipeMap).

Deliberately NOT fixed (need design ruling): AUD-D-004 pocket_templates loader-or-delete, AUD-F-004 vocation opposition grandfathering, AUD-G-001 carried_niffler copyOnDeath intent, AUD-B-007/008 nullability sweep (mechanical, large diff).

### Follow-up pass 2026-07-06 (evening)
- **AUD-D-004** pocket_templates: minimal loader shipped (PocketTemplate codec + registry + reload listener, maxRadius caps pocket creation; synthetic trunk_lock_N/trunk_decoy ids pass through). Boot-verified: 3 templates load.
- **AUD-B-007/008** nullability sweep: 52 javax imports → JSpecify; 22 null-returning methods across 18 files annotated.
- Niffler TODO(effects): happiness ≥80 → up to +10% spell damage via ModifierStack hook (HappinessSpellPower).
- Audio: protego_totalum_raise event-name-as-file-path fixed; 4 stale TODO(audio) markers retired (vanilla remaps ship).

Still open (design): AUD-F-004 vocation grandfathering, AUD-G-001 carried_niffler copyOnDeath.

---

## 2026-07-01 — Placed trunk entry mechanic

### Audit (pre-implementation audit for Expanded/Master's placed-block descent mechanic)
**STOP-AND-REPORT trigger hit (§5): trunks are wired as inventory-only held items; the block path would conflict.** No code written pending a migration decision.

- Pocket dimension + per-trunk allocation **exists**: `ModDimensions.EXTENSION_REALM`; `ExtensionCharmService.enterPocket/exitPocket/getOrCreatePocket`; per-pocket plot via `computePocketSpawn(caseId)` on 512-block grid; shell built by `PocketShellGenerator.ensurePocketShell`. Entry anchor = `TrunkRecord.spawnPos()`. Return point stored per-player in `TrunkRegistryData.saveReturnPosition/getReturnPosition/getReturnDimension`. Mechanic can proceed — nothing to build in dimension layer.
- **BLOCKER / conflict** `TrunkRecord` stores pocket identity/config only: `pocketId, owner, pocketName, accessMode, archetype, templateId, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, lockedExternally`. **No return point** (lives per-player in `TrunkRegistryData`) and **no active-lock index** (lives on item component `MOODYS_TRUNK_ACTIVE_LOCK`). For a block path, active-lock + return anchor belong on `TrunkBlockEntity`, so **TrunkRecord likely needs zero new fields** — confirm before adding (§5).
- **BLOCKER / conflict** All tiers are **held items that teleport on `use()`** — no placed-block entry exists:
  - `enchanted_trunk` = `EnchantedTrunkItem(TIER_1)` — 1 lock (Traveller's tier). **Currently full dimension-enter**, NOT storage-only as design intent assumes.
  - `expanded_trunk` = `EnchantedTrunkItem(TIER_2)` — 3 locks (Expanded).
  - `masters_trunk` = `EnchantedTrunkItem(TIER_3)` — 7 locks (Master's).
  - `moodys_trunk` = `MoodysTrunkItem` — 7 locks, per-lock sub-pockets, 7th = SAFEHOUSE "pit" (already a decoy-like compartment).
  - `newts_case_item` = `NewtsCaseItem` — Scamander sanctuary (trinket). All registered in `DarkArtefactItemRegistry` / `TrinketItemRegistry`.
  - Block path for Expanded/Master's would duplicate `EnchantedTrunkItem.use()`. Migration decision required (replace vs. add).
- BlockEntity pattern to mirror: `ExpansionFocusBlockEntity` + `PocketConfiguratorBlock` (`BaseEntityBlock`, `saveAdditional/loadAdditional` via ValueInput/Output, `setPlacedBy` binding pocket-at-pos). Registered in `ModBlockEntities.POCKET_CONFIGURATOR`.
- Block + BlockItem registration: `ModBlocks.registerBlock(...)` + `ModItems.ITEMS.registerSimpleBlockItem(...)` (see `POCKET_CONFIGURATOR` / `WARDING_STONE`). Directional facing not yet used by any mod block — would add `HorizontalDirectionalBlock` + `FACING` state.
- Packed DataComponent to mirror: `WandComponents.WAND_CONFIGURATION` (Codec-backed, item-attached). Existing pocket components (`POCKET_CASE_ID`, `POCKET_ID`, `POCKET_ARCHETYPE`, `MOODYS_TRUNK_ACTIVE_LOCK`, `MOODYS_TRUNK_BASE_ID`, etc.) in `ModDataComponents` already carry trunk reference on the item — a BlockItem can reuse `POCKET_CASE_ID`/`POCKET_ID` to survive pack-up with zero content loss (case→pocket binding persists in `TrunkRegistryData.caseBindings`).
- Gating constant confirmed: `Module.POCKET_DIMENSIONS` (state ENABLED). Pattern: `if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return PASS/FAIL;` at top of `use()`. Gate entry + lock-cycle; block placement itself can stay ungated.
- Traveller's storage-only path: **does not exist as storage-only**. `enchanted_trunk` (TIER_1) currently enters dimension like the others. Left untouched per §3; flagged here only.

### Resolution (2026-07-01)
STOP trigger addressed under Upgrade License decisions: Expanded/Master's/Moody's converted to placed `TrunkBlock` + `TrunkBlockEntity` (BlockItems keep ids); Traveller's held item untouched; `TrunkRecord` unchanged (no schema additions needed). `compileJava` green.

### Resolution follow-up (2026-07-01)
Travellers (enchanted_trunk) no longer a held reach-in item — converted to a placed TrunkBlock alongside Newts Case. No held trunk-likes remain. See MIGRATION_DELTAS addendum.

---

## 2026-06-29 — Heritage selector redesign

### Audit (pre-build audit for Ministry Handbook Part 1: Infrastructure)
No stop-and-report triggers hit.

- Current screen — `HeritageSelectionScreen extends Screen`. Three-phase wizard (`HERITAGE_LIST` arrow-cycle → `VARIANT_LIST` → `CONFIRMATION`), `Button`-driven, drawing delegated to `HeritageSelectionRenderHelper`. Display strings from `Heritage`/`HeritageVariant` enum accessors (`getDisplayName()`, `getDescription()`) — **hardcoded English in enums**, plus two translation keys. Scaling via `ScreenLayoutScaler` (not `GuiScaleHelper`).
- Gate-mode — no flag: screen is *always* the hard gate. `shouldCloseOnEsc()→false`, `isPauseScreen()→false`, `keyPressed` swallows ESC at root phase (steps back in sub-phases, blocks dismissal at `HERITAGE_LIST`). Open trigger is server-driven: `HeritageDataSyncS2CPayload.openSelector()==true` → `ClientPayloadHandlers:270` → reflection `openHeritageSelectionScreen` → `new HeritageSelectionScreen()`. Preserve exactly; new screen keeps same three overrides and parameterless ctor.
- Enums — `Heritage` (10 values) and `HeritageVariant` (33 values) both live in common pkg `at.koopro.wizardsandbeasts.heritage` → client-accessible. Expose `getDisplayName/getDescription/getColor/isAlphaAvailable/getSubtypes` (Heritage) and `getDisplayName/getDescription/getUiColor/getTags/getParentHeritage/getTotal{Health,Speed,Armor}` (HeritageVariant). **POLISH:** prompt says `WIZARDKIND` has 4 variants; enum actually has **5** (adds `adopted_magical`/"Wizard-Raised"). Build renders *all* variants uniformly via `getSubtypes()`, so non-blocking — just count discrepancy.
- Packets — C2S commit `HeritageSelectC2SPayload(String typeId, String subtypeId)`; S2C open `HeritageDataSyncS2CPayload` (open flag = `openSelector()`). **Server commit handler ALREADY validates availability**: `HeritageSelectC2SPayload.handle` rejects `!heritage.isAlphaAvailable()` (lines 71-75) with `message.wizards_and_beasts.type_selection.coming_soon`, plus locked-check, null-check, and parent-match check. → **Deliverable 5 is a no-op; no server change needed.** Signatures unchanged.
- Availability signal — authoritative source is enum flag `Heritage.isAlphaAvailable()`. Exactly 3 are true: `WIZARDKIND`, `WEREWOLF`, `OBSCURIAL`. Other 7 (`GOBLIN, HOUSE_ELF, VEELA, GIANT, CENTAUR, VAMPIRE, MERPEOPLE`) false → locked-but-browsable. No `ModuleManager` per-heritage state exists; do not invent one. Reuse `isAlphaAvailable()`.
- House style — `WizardsConfigScreen` (Ministry-memo: parchment fill via `McStylePanel.drawTiled`, letter-spaced shadow-free header, CLASSIFIED wax-style stamp, staggered ink reveal). Reuse: `InkRevealRenderer` (`client.gui.config`, public, widget-stagger + `isRevealed(idx,delayMs)` for manual draws), `McStylePanel` (`drawTexturedPanel/drawTiled/drawNineSlice/drawPanel/drawBorder`), `GuiScaleHelper` (`computeScale/clampedLeft/clampedTop`, downscale-only), parchment palette in `WizardsAndBeastsUiTokens.HeritageSelection.COLOR_*` and `ConfigWidgets.{PARCHMENT,INK,STAMP_RED}`.
- Mechanical descriptor — `PowerBandTable` is common pkg, pure static (`getBandMax(variant)`, `getGrowthCap(variant)`), no server-only deps → **client-accessible**. Identity card CAN show a power-band descriptor (band max per variant). Use it.

**Stop-and-report checklist:** none triggered. No BLOCKER → **Proceed with build.**

### Resolution (2026-06-29)
Halt resolved by user-approved Path 1 (adapt to real types; no touch to Skill/SkillNodeEffect/PlayerSkillData/nodes). Shipped + `./gradlew test` green (incl. new VocationUnlockStateTest, 8 cases). New `skill.vocation` package: VocationDefinition (Codec; `commitmentEffects` wrapped in `Codec.lazyInitialized` so class-load doesn't pull attribute registry — keeps it unit-testable without FML bootstrap), VocationRegistry (+symmetric areOpposed), VocationLoader (datapack `vocations/`, wired in WizardsAndBeastsMod), PlayerVocationData (separate Codec attachment), VocationHelper (Band/UnlockState + pure `unlockState(Optional,Optional,Skill)` core), VocationManager (commit/clear orchestration + audit block), VocationEffectApplicator (parallel `vocation/`-prefixed keyed apply). Gate = one line in SkillSystemAPI.evaluateUnlock. Sync = parallel VocationDataSyncS2CPayload + ClientVocationCache (login + commit/clear). Commands grafted at `/wandb skill vocation {info|set primary|secondary|clear}` (no `/skilltree` exists). 5 JSONs + en_us keys. Adaptations vs prompt: `/wandb skill` not `/skilltree`; parallel applicator not SkillTreeEffectApplicator; refund via resetSkill not spent-ledger; grant_ability flags live in `grantedAbilities` field (no new SkillNodeEffect variant); attributes limited to existing wand_affinity (others ship as TODO ability flags). Capstones left Optional.empty (no node authoring). Healer opposition handled absent.

---

## 2026-06-29 — Handbook infrastructure audit

### Audit
Pre-implementation audit for Ministry Handbook (Part 1: Infrastructure). No stop-and-report triggers hit.

- Bestiary pattern reference (codec, loader, registry, sync, screen, item, networking, module, recipe).
- Dispatch codec reference: `SkillNodeEffect.java` — sealed interface + `Type` enum + `Type.CODEC.dispatch(::type, Type::codec)` with per-variant `MapCodec`. `HandbookPage` mirrors this exactly.
- **Planned deviation from Bestiary:** handbook DOES sync full chapter list to client (`SyncHandbookPayload`, `HandbookChapterManager.CLIENT_CHAPTERS`) per spec §1c, whereas Bestiary entry defs never synced. Sync fires on `OnDatapackSyncEvent` (covers both join and `/reload`) rather than `PlayerLoggedInEvent`, so `/reload` re-pushes updated chapters.

---

## 2026-06-29 — Handbook content audit

### Audit
Pre-implementation audit for Ministry Handbook Part 2 (content JSON). No Java changes. No stop triggers hit (recipe gaps resolved via text fallback per §3).

- Codec field names match §1 assumptions exactly.
- Page type keys: `text`, `recipe`, `image`, `cross_ref`. Confirmed.
- Data path: `data/wizards_and_beasts/handbook/chapters/` confirmed.
- Recipe page behaviour: structural frame + captioned recipe id only (MIGRATION_DELTAS deviation 3). JSON supplies `recipe_id` only.
- Cross-ref: renders "See Bestiary →" banner; `target_type` accepted value is `"bestiary"`.
- Item identifiers found (icons): all chapter icons resolve to existing ids — **no icon fallbacks used**. (Ch09 uses `wizards_and_beasts:enchanted_trunk`, a real trunk id, instead of nonexistent `:trunk`/chest fallback.)
- Recipe IDs present under `data/wizards_and_beasts/recipes/`: `bestiary`, `cleansweep_seven`, `comet_260`, `firebolt`, `firebolt_supreme`, `marauders_map`, `ministry_handbook`, `nimbus_2000`, `nimbus_2001`, `pocket_case`, `spell_teacher`.
  - **MISSING `wizards_and_beasts:wand`** → Ch03 wand recipe page replaced with `text` fallback.
  - **MISSING `wizards_and_beasts:travellers_trunk`** → Ch09 trunk recipe page replaced with `text` fallback.

---

## 2026-06-24 — Full-roster creature abilities — common library + signatures

### Audit
- `ranged_hex` now launches real travelling `BeastHexProjectile` (`ThrowableItemProjectile`, registered `beast_hex_projectile`, vanilla `ThrownItemRenderer`) carrying damage + effect + trail — dodgeable, no longer hitscan. `web_snare`/`nundu_pestilence`/thunderbird-bolt remain intentional AoE/instant effects (not projectiles by design).
- `leap` now uses `GatedLeapGoal` (module-gated re-implementation of vanilla `LeapAtTargetGoal`) instead of vanilla goal, so it self-gates on `Module.CREATURES` like every other ability goal. Disabling module now stops leap too.
- `blink_away` and `camouflage` share entity's single `AbilityCooldown` counter, so a creature must not declare both (camouflage's reveal-window and blink's cooldown would interfere). Enforced by assignment: Demiguise/Moke use `camouflage` only (blink dropped). Add second counter if future creature needs both.
- `thunderbird_storm` sets *global* server weather (thunder/rain) when Thunderbird engages target — intended drama, but world-affecting. Tune `storm_duration_ticks` or scope to local effects if undesirable.
- Canon-gap signatures filled: graphorn `spell_resist`, erumpent `explosive_horn`, jobberknoll `death_cry` (new `onDeath` hook), ramora `anchor`.
- occamy choranaptyxic render-scale (`occamy_choranaptyxis` + synced `DATA_RENDER_SCALE` + `ScaledBeastRenderer`), Fire Crab `flame_burst` + Ashwinder `ember_trail` (fire emission), Kneazle `danger_sense`, and real ranged projectiles — all shipped.
- `ember_trail` places vanilla fire blocks in Ashwinder's wake (world-affecting, like dragon scorch). Gated by 6%/tick chance + air-on-solid only; tune `chance_per_tick` if too aggressive.
- Still deferred: render-time model tints (art-swap, no logic), Ashwinder igniting-egg ITEM (needs new item + loot pass), deeper bespoke uniques beyond signatures. New canon creatures already exist as dedicated `entity.beast` entities (not data roster). Niffler THIEF→ability-layer refactor left untouched (prior scope).
- `beast_hex_projectile` has no lang key (`entity.wizards_and_beasts.beast_hex_projectile`); harmless (projectiles aren't named in UI) and renders as flung magma cream placeholder until beast art lands.
- Live-client smoke test (summon each beast, observe auras/leaps/ranged/blink/storm + module toggle) not run in this environment; all ability logic server-authoritative and covered at data layer by `CreatureDefinitionCodecTest` (parses all 86 creature JSONs + asserts dispatch keys). No render-time entity access added.

### Resolution (2026-06-24)
All items above resolved and shipped as part of the Creature Ability framework build.

---

## 2026-06-24 — Creature ability canon-gap fills + onDeath hook

### Audit
Closed remaining canon signature gaps. New `onDeath(entity)` hook on `CreatureAbility` (default no-op), dispatched server-side from `GenericBeastEntity.die()` (module-gated). 4 new variants: `spell_resist` (Graphorn — heals back fraction of MAGIC/INDIRECT_MAGIC damage, matching mod's `damageSources().magic()` spells; reusable for any spell-resistant beast), `explosive_horn` (Erumpent — contained `ExplosionInteraction.NONE` burst on gored melee victim, distinct from on-death EXPLODE), `death_cry` (Jobberknoll — death burst: scream sound + Glowing on nearby living), `anchor` (Ramora — per-sec heavy Slowness on nearby in-water creatures, pinning them). Assigned: graphorn +spell_resist, erumpent +explosive_horn, jobberknoll +death_cry, ramora +anchor. CreatureAbility variant count now 24 (20 common-ish + 9 signatures across two passes). compileJava + full :test SUCCESSFUL.

### Resolution
All 4 new ability variants shipped and assigned.

---

## 2026-06-24 — Cleared the deferred creature-ability backlog

### Audit
Built every remaining deferred item. 4 new variants (28 total) + real projectile entity + render-state size-shift + new `onDeath`-style render path:

- **occamy_choranaptyxis** (Occamy size-shift, render-only): `GenericBeastEntity` gains synced `DATA_RENDER_SCALE` float (default 1.0, hitbox stays registry-frozen). New `ScaledBeastRenderer` (mirrors `DragonRenderer`'s render-state DataTicket → `root` bone scale, no live-entity access) now renders all non-dragon creatures (1.0 = identical to old `GeoRendererHelper.simple`). Ability eases scale toward max when roused + roomy, min when calm/confined (ceiling headroom proxy).
- **flame_burst** (Fire Crab, +`FlameBurstGoal`) and **ember_trail** (Ashwinder): the "fire emission" follow-up the fire pass deferred. Burst = AoE ignite + small fire damage on cooldown; trail = small per-tick chance to lay vanilla fire block in Ashwinder's wake (air-on-solid only). No new items.
- **Real ranged projectiles**: `ranged_hex` no longer hitscans. New `BeastHexProjectile` (`ThrowableItemProjectile`, registered `beast_hex_projectile`, rendered via vanilla `ThrownItemRenderer` as flung magma cream — the `WizardingThrownEntity` pattern) carries server-side damage + optional effect + particle trail; `RangedHexGoal` now launches it (dodgeable, travels, LOS) with throw sound.
- **danger_sense** (Kneazle): periodically outlines nearby `Enemy` mobs with Glowing (sixth sense for threats).

Assigned: occamy +occamy_choranaptyxis, fire_crab +flame_burst, ashwinder +ember_trail, kneazle +danger_sense. Codec test extended for 4 new dispatch keys; FireAffinity assertions now tolerate multi-ability creatures. compileJava + full :test SUCCESSFUL.

NOT done (clear rationale): new canon creatures — already exist as dedicated `entity.beast` entities (augurey/mooncalf/streeler/phoenix/bowtruckle/cornish_pixie/thestral), not data-driven roster, so nothing to add there. Niffler THIEF→ability-layer refactor left untouched (prior scope). Render-time model tints are art-swap work with no logic to write. Ashwinder igniting-egg item deferred to a loot/item pass (no new items this scope).

### Resolution
All items shipped.

---

## 2026-06-24 — Dragon Fire/Venom Kit — 10 breeds rebound

### Audit
The ten canonical dragon breeds (antipodean_opaleye, chinese_fireball, common_welsh_green, hebridean_black, hungarian_horntail, norwegian_ridgeback, peruvian_vipertooth, romanian_longhorn, swedish_short_snout, ukrainian_ironbelly) are now bound to a dedicated `DragonEntity` (extends `GenericFlyingBeastEntity`) instead of plain generic flyer — `ModCreatures.factory()` routes `DRAGON_IDS` to `DragonEntity::new`; `ClientSetup` routes them to `DragonRenderer`. `CreatureDefinition` gains optional nested `dragon` block (`DragonTraits`: fire_range, fire_color, flame_shape STREAM/JET/BURST, block_effect IGNITE/ASH, bite_venom, rideable [data-only], scale).

BEHAVIOURAL DELTA: dragons previously breathed via generic `BreatheFireGoal` (hardcoded range 9, no colour/shape/ASH). They now use `DragonBreathGoal` — server-authoritative cone (half-angle by flame_shape; IGNITE via vanilla fire, ASH = clean removal of `#wizards_and_beasts:dragon_ash_combustible` timber/bone blocks), tinted server-spawned particles, GeckoLib `triggerAnim` breath/bite (no packet). The `FIRE_BREATH` trait was REMOVED from the 10 dragon JSONs (would double-fire alongside new goal); FIRE_IMMUNE/FIRE_ATTACK/KNOCKBACK retained. Ridgeback + Vipertooth gain melee poison via `bite_venom`. Render scale per-breed (smallest 0.8 → largest 2.2) applied to `root` bone via render-state DataTicket — no hitbox change (hitbox stays registry-frozen in MANIFEST). compileJava + build -x test SUCCESSFUL.

### Resolution
Dragon Fire/Venom Kit fully implemented and tested.

---

### Dragon breath — Ice-and-Fire ground scorch (2026-06-24)

### Audit
`DragonBreathGoal` block effect reworked: IGNITE no longer gated on `BlockState.isFlammable` (that left natural terrain untouched — only victim caught fire). Breath now carpets vanilla fire onto ANY solid top surface across splash footprint at cone impact (`scorchArea`/`igniteColumn`), deduped per breath via shared `Set<BlockPos>`. Footprint radius by flame_shape (JET 1.0 / STREAM 1.5 / BURST 2.5); rays 12→18; ASH mirrors same footprint (`ashColumn`). Particle jet densified (per-half-block sampling, downrange spread, periodic LAVA). LIMITATION: placed fire is vanilla, so on non-flammable ground it self-extinguishes after a few seconds (no lingering char) — persistent dragon-fire block deferred (block registration, out of scope). compileJava SUCCESSFUL.

### Resolution
Ground scorch mechanic shipped.

---

## 2026-06-24 — CreatureAbility framework + FireAffinity (first beast ability)

### Audit
NEW reusable, datapack-driven ability layer mirroring `SkillNodeEffect`: `creature/ability/CreatureAbility` is a sealed interface with dispatch `Codec<CreatureAbility>` keyed by `Type` serialized-name (the `SkillNodeEffect` precedent). Server-side hooks: `tick(entity)`, default-no-op `onHurt(entity,source,amount)`, `onMeleeContact(entity,target)`, and `registerGoals(entity,goalSelector)`. Hooks typed against shared `GenericBeastEntity` base (every data-driven creature, dragons included). `CreatureDefinition` gains general top-level `List<CreatureAbility> abilities` (NOT inside dragon-specific `DragonTraits`), default empty — record now has 17 components, one past `RecordCodecBuilder.group`'s 16-arg ceiling. Codec assembled from two `MapCodec` halves (`StatBlock` + `AssetBlock`) merged with `Codec.mapPair`; both read SAME flat JSON object, so on-disk shape unchanged and all 60 existing creature JSONs parse untouched (proven by `CreatureDefinitionCodecTest`, 6 tests).

First concrete ability `creature/ability/FireAffinity` (record + MapCodec, all fields defaulted): `fire_immune`, `requires_fire`, `dry_grace_ticks`, `dry_damage`, `regen_in_fire`, `seek_fire_when_dry`, `ignite_melee_attackers`, `ember_particles`. Behaviour (server-side, gated on `Module.CREATURES` via `isEnabled` — the `DragonBreathGoal` precedent): in fire/lava resets dry timer + heals `regen_in_fire`/sec; out of fire past grace window applies `dry_damage`/sec through `DamageSources.dryOut()` (DRY_OUT not fire-typed, bypasses creature's own fire immunity); `ember_particles` emits FLAME+ASH at body anchor every 6t; `ignite_melee_attackers` ignites melee attacker via `onHurt`; `seek_fire_when_dry` registers `SeekFireGoal` (a `MoveToBlockGoal` that self-gates on module at `canUse` and only seeks once half-way through grace window). DELTA on three creatures: Salamander/Ashwinder/Fire Crab JSONs gain `abilities:[{type:fire_affinity,…}]` block (their pre-existing `FIRE_IMMUNE`/`FIRE_ATTACK` traits retained; `FireAffinity.fireImmune` OR-ed into `GenericBeastEntity.fireImmune()`). Per-entity dry-out counter lives on `GenericBeastEntity` (`FireDryTicks`) persisted via existing `addAdditionalSaveData`/`read` pattern (mirrors `CarriedLoot`) — record stays shared, stateless definition value. compileJava + targeted test SUCCESSFUL.

### Resolution
CreatureAbility framework + FireAffinity shipped.

---

## 2026-06-22 — Creature Carry-Theft + Real Lore

### Audit
THIEF trait upgraded to niffler-grade: `GenericBeastEntity` now pockets stolen stacks (up to 8) into carried list, persists it via ValueOutput/ValueInput (ItemStack.CODEC.listOf, key "CarriedLoot"), and drops the loot on death (jarvey/leprechaun carry then). Real bestiary lore written for all 33 new beasts (tools/creature_lore.py) replacing placeholder strings — 0 placeholder lore left. Build -x test SUCCESSFUL; gametest server loads 86 defs clean.

### Resolution
Carry-theft mechanic + real lore shipped.

---

## 2026-06-18 — Creature AI + Trait Foundation

### Audit
Data-driven behaviour layer for all 86 generic creatures. New `creature/Trait` enum (FEARFUL, PACK, FIRE_IMMUNE, FIRE_ATTACK, POISON_ATTACK, PETRIFY, CHARGE, KNOCKBACK, THIEF, AMPHIBIOUS, REGEN, EXPLODE_ON_DEATH). CreatureDefinition gains `temperament` (already), `attackDamage`, `traits[]`. GenericBeastEntity now wires goals by temperament (PASSIVE flee / NEUTRAL retaliate / HOSTILE hunt), applies ATTACK_DAMAGE on spawn, and interprets traits via fireImmune()/doHurtTarget()/tick()/die() (on-hit ignite, poison, petrify-lite slow+blind, knockback, charge, item-theft; passive regen; death explosion). Subclasses now supply only movement goals (addMovementGoals); base owns combat. ModCreatures baseline adds ATTACK_DAMAGE/ATTACK_KNOCKBACK. Behaviour assigned per creature by tools/creature_behavior.py (category defaults + per-id overrides). Build -x test SUCCESSFUL. Signature one-offs (true dragon fire-breath, basilisk death-gaze, niffler-grade theft) deferred.

### Resolution
AI + Trait foundation shipped.

---

## 2026-06-18 — Creature Build Batch 2 (33 NEW canonical beasts)

### Audit
33 NEW canonical beasts added as full bestiary entries + generic entities (knarl, griffin, fairy, puffskein, pygmy_puff, chizpurfle, horklump, red_cap, erkling, leprechaun, wampus_cat, salamander, murtlap, moke, jarvey, kappa, gnome, hippocampus, sea_serpent, fire_crab, imp, porlock, nogtail, dugbog, mackled_malaclaw, ramora, shrake, tebo, hodag, snallygaster, glumbumble, pogrebin, quintaped). ModCreatures roster 53→86; bestiary entries 62→95. Placeholder lore lang + bestiary icon/silhouette. Build SUCCESSFUL. First SESSILE-locomotion creature (horklump) now exercises `GenericSessileBeastEntity`.

### Resolution
Batch 2 creatures shipped.

---

## 2026-06-18 — Creature Build Pass (53 placeholder creatures)

### Audit
New data-driven creature system. 53 bestiary entries lacking an entity are now registered, summonable, GeckoLib-rendered placeholder mobs. New: `creature/CreatureDefinition` (codec record), `CreatureDefinitionRegistry`, `CreatureDefinitionLoader` (reload listener, dir `creatures`), `entity/creature/Generic{Ground,Flying,Aquatic,Sessile}BeastEntity` (+ `GenericBeastEntity` base), `registry/ModCreatures` (53-entry MANIFEST → EntityType + baseline attributes + spawn egg per id), `creature/command/CreatureCommands` (`/wandb creature summon|list`). `Module.CREATURES` flipped `DISABLED`→`PREVIEW`.

Behavioral note: existing entities/spells/brooms unaffected — purely additive. The 9 entries that already had entities (incl. in-progress Niffler) untouched. Per-creature runtime attributes load from `data/wizards_and_beasts/creatures/<id>.json` and applied over per-locomotion baseline on spawn (server-side); `EntityType` hitbox size registry-frozen from `ModCreatures.MANIFEST`.

Verification: `./gradlew compileJava` clean; `./gradlew build -x test` BUILD SUCCESSFUL (processResources/lang/jar ok). All 53 creature/geo/animation JSON validate; enum values match `BodyPlan`/`Locomotion`/`Temperament`. In-game (not run here — dev client session required): `/wandb creature summon <id>` then observe idle animation. Render path byte-identical to shipping `GeoRendererHelper.simple` pipeline.

### Resolution
Creature Build Pass shipped.

---

## 2026-06-13 — Spell F2 beam-channel runner wiring

### Audit
Per-entry `cadence` tag `{start, tick, end}`, default `tick`, added as wrapper (`SpellEffectEntry` = component + cadence, merged into same flat JSON object via dispatch's `MAP_CODEC`) — no component variant rewritten and pre-cadence JSON parses unchanged. `WandBeamChannelLogic` now runs channel spell's entries: `start` once on first channel tick, `tick` on existing `Config.beamChannelEffectIntervalTicks` interval (only while beam holds living target; subject = beam target), `end` once on release/interruption/spell-switch (cached-target fallback caster). Scaling multipliers re-resolved from live `ProficiencyScaler` profile each invocation. Cadence **inert** outside BEAM_CHANNEL — non-channel sites still run full list once via phase-less overload (BEAM_LETHAL/avada never reaches wiring), so no already-migrated spell's behavior changes. Nested `aoe_apply` children stay plain components.

### Resolution (F2)
- **crucio:** beam_channel 50.0, cooldown 220, baseDamage 2.0, req proficiency incendio:mastered, effects `[apply_effect cruciatus_pain 60 target darkArts cadence:tick]`. Residual tail: `handleCrucioChannel` minus pain apply: intent feedback payload, corruption accrual (5×intent per interval), WITHER/SLOWNESS cleanup, **ramp damage** (≥40 ticks, every 20: `min(1.5, 0.4+ticks/120)×intent` — time-ramp inexpressible), proficiency hits, pain-strip on end/target-switch (`clearSessionEffects` — pain still lapses immediately at beam stop). Shed: (1) pain refresh no longer intent-scaled: fixed channel interval (5 default, ≤22 LOW) vs `interval/max(0.5,intent)`; duration `60×durationMult` (≥36) vs `max(20, 60/intent)` — invisible in practice (refresh ≪ duration; immediate strip at end unchanged). (2) cast-time "That power is sealed away" message + fizzle sound when DARK_ARTS disabled (Java `executeCast` override) — shed; channel still no-ops (component `darkArts` gate + tail module gate). (3) props targetEffects weakness/nausea/slowness 40t were **dead** (no dispatch path applies targetEffects for BEAM_CHANNEL) — not carried into JSON; no observable change.
- **wingardium_leviosa:** beam_channel 16.0, cooldown 60, projectileSpeed 0.0, req knows lumos, **effects empty**. Residual tail: **entire lift behavior**: `WandBeamSpellIds.isLeviosa` → `handleLeviosaChannel` direct spring-motion (hold-distance scroll adjust, grace-miss ticks, no-gravity save/restore) — direct motion manipulation, no component expresses it (registration-only migration, like aguamenti).

Supporting changes:
- `ObscurusSurge`/`AvadaKedavra` requirements repointed `Spells.CRUCIO` → `"crucio"` (id-string).
- `JsonSpell.buildRequirement` no longer degrades to NONE when prerequisite isn't registered yet: resolves eagerly when present (unchanged) and otherwise falls back to mod-namespaced id string, enforced lazily at `isMet` time. Needed because JSON spells init one-by-one during reload sweep and crucio's prerequisite (incendio) is itself JSON — load order arbitrary.
- `Crucio.java` + `WingardiumLeviosa.java` deleted. `WandBeamChannelLogicTest`'s session tests still use `"crucio"` id string — unaffected.

State after F2: Java spell registrations = `RIDDIKULUS`, `PROTEGO`, `EXPECTO_PATRONUM`, `AVADA_KEDAVRA`, `IMPERIO`, `OBSCURUS_SURGE`, `OBSCURUS_GRASP` — bespoke set only. 20 spells ship as JSON. `SELF_UTILITY_RULES` unchanged.

---

## 2026-06-13 — Spell component vocabulary (see SPELL_EFFECT_COMPONENTS.md)

### Audit
The 12 SpellEffectComponent primitives (`apply_effect`, `damage`, `ignite`, `impulse`, `heal`, `dispel`, `clear_fire`, `light`, `repair`, `explosion`, `aoe_apply`, `swap_active_spell`) defined, wired, and tested. Scaling (F1) and cadence (F2) mechanisms implemented. Hybrid props (Step 5) documented.

### Resolution
Component vocabulary locked in as the canonical reference.

---

## 2026-06-11 — Spell migration foundation (see SPELLS.md)

### Audit
Step 4 migration blocked by three fundamental gaps: (A) components scaling-blind, (B) runner not wired to BEAM_CHANNEL, (C) multi-part/mechanism-mismatch spells. Foundation work F1–F5 prescribed: scaling-aware context, beam-channel runner, multi-part components (cleanse, conditional heal, clear_fire, aoe_apply, learn_spell), residual id-keyed tails policy, damage policy decision.

### Resolution
Foundation work F1, F3, F4, F5 completed (Step 3.5 "Component Parity Pass"). F2 (beam-channel) completed 2026-06-13. Revised Step 4 unblocked for everything except two beam spells.

---

## 2026-06-09 — Cast pipeline audit (see PIPELINE_AUDIT.md)

### Audit
Step 1 foundation verification of cast pipeline (input → C2S → server resolution → executor → effect/cooldown → HUD → module gate) with manual witness repro steps. Deep Q1/Q2 analysis (hardcoded-vs-datapack, Protego-specific-vs-generic). Named-bug findings later promoted to stable `AUD-*` IDs in `FULL_AUDIT_REPORT.md`.

### Resolution
Documented as PIPELINE_AUDIT.md; findings fed into later audits.

---

## 2026-06-06 — Full audit report (see FULL_AUDIT_REPORT.md for stable AUD-* IDs)

### Audit
Read-only diagnostic sweep (Passes A–G) with stable `AUD-<pass>-<nnn>` IDs. No production files modified. Findings logged for follow-up fix prompts.

### Resolution
Stable ID registry established; subsequent fix passes reference these IDs.

---

## 2026-05-28 — Pre-alpha audit punchlist (initial findings + coverage notes)

### Audit
95 findings (BLOCKER/POLISH/NICE-TO-HAVE) across all subsystems. Coverage notes documented. Established as working checklist for all subsequent build passes.

### Resolution
Punchlist served as the audit-first stop-and-report notes; resolutions tracked in subsequent delta logs and folded into this worklog.