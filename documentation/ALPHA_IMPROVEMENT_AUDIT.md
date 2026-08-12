# Wizards & Beasts — Architecture & Alpha Improvement Audit

**Date:** 2026-07-13 · **Mode:** read-only, evidence-based deep audit (7 parallel domain sweeps + main-thread synthesis) · **Stack:** MC 1.21.11 / NeoForge 21.11.42 / Java 21 / GeckoLib 5.4.5
**Scope:** 930 production Java files, 1,334 datapack files, 1,064 generated + 717 hand-authored assets. No production files were modified during this audit.
**Relationship to prior audits:** `FULL_AUDIT_REPORT.md` (2026-07-06) is a code-correctness sweep (Passes A–G, stable `AUD-*` IDs). This audit is a broader design/gameplay/architecture pass and treats `FULL_AUDIT_REPORT.md`'s **still-open** findings as carryover blockers rather than re-deriving them — they are cited by ID where relevant. Its previously-flagged brew-loader bug (AUD-D-002) is confirmed **fixed** in current source (see §8).

---

## Executive Summary

Wizards & Beasts has a genuinely strong **engineering foundation**: server-authoritative networking with consistently well-guarded packet handlers, a clean data-driven loader architecture (codecs, reload listeners, volatile-swap registries) used across most systems, a completed Java→JSON spell-component migration, and GeckoLib render code that correctly separates render state from live entity state. This is not a hobby-project codebase — the infrastructure is close to production quality in several areas.

The gap between "engine is solid" and "game feels finished" is **content depth and system wiring**, not architecture. This audit found **7 new Critical-severity findings** (on top of the still-open Critical/Blocker items from `FULL_AUDIT_REPORT.md`), and the pattern repeats across nearly every domain: a real system was built, and then either (a) a small wiring bug makes it fire zero times in practice, or (b) it was scaffolded but never filled with content:

- ~~The Wandmaker's Bench — the mod's headline crafting station — still cannot produce a wand (`AUD-D-001`, open)~~ — **fixed 2026-07-13** during this audit's follow-up: recipe directory/type-field corrected, coverage expanded from 6 to the full 30-combo wood×core matrix. Left as a record of the finding; see §2.1.
- ~~Wand corruption (dark magic tracking) computes a substring match that can never succeed~~ — **fixed 2026-07-13**: replaced with a direct `SpellCategory` check; the mechanic (accrual, power multiplier, threshold messages) is now reachable. See §4.1.
- ~~Bestiary discovery is `MANUAL`-only for all 86 manifest creatures~~ — **fixed 2026-07-13**: every entry now has a real `encounterTrigger` (KILL for hostile/neutral creatures, PROXIMITY for passive/lore-shy ones), derived from each creature's actual temperament/behavior, not guessed. See §5.1.
- Azkaban, the mod's one custom structure, generated but contained an essentially empty 225-byte NBT template, no monsters reachable outside admin commands, and no loot — plus an orphaned, ready-made loot table that was clearly meant for it and never got connected. **Stopgapped 2026-07-13**: generation disabled at the module flag until real content lands, so players can no longer stumble onto the empty stub. The real fortress/spawns/loot build remains open (L effort). See §7.1.
- ~80% of the 86-creature roster ships with explicitly-marked placeholder box-rig models (`"PLACEHOLDER box rig"` comment in 78/98 animation files).
- The `gameTestServer` Gradle run config was presented in the docs as an integration/GameTest gate but contains **zero** `@GameTest`s (or their modern data-driven equivalent) anywhere in the codebase. **Doc-corrected 2026-07-13** so this isn't overclaimed; the real test suite remains open. See §12.1.
- ~~`WandRenderer` reallocates the entire wand-module registry into fresh maps/lists every rendered frame~~ — **fixed 2026-07-13**: `WandModuleRegistry.getAllForSlot` now reads a cache invalidated only on actual registry writes, not on every render. See §10.1.

None of these are architecturally hard to fix — most are S/M effort. The theme is **finish what's already built** rather than **design something new**, which matches the brief's instruction to favor completing existing systems.

**Findings by severity (this audit only, excluding carryover):** 7 Critical (4 fully resolved 2026-07-13, 2 partially addressed — Azkaban stopgapped, GameTest docs corrected — pending real content/tests, 1 open) · 20 High · 24 Medium · ~30 Low/informational, across 15 domains and ~88 total findings.

---

## 1. Multiplayer Audit

**Overall verdict: strong.** No Critical-class bugs (crash, dupe, authority bypass, save corruption) were found. Every C2S handler checked re-derives the acting player from the packet context rather than trusting client-sent identity, amounts are bounds-checked before mutation, and broom flight is genuinely server-authoritative (position/velocity are never client-trusted). The issues that do exist cluster around **cross-player interaction on a shared target** and a handful of session managers that predate the mod's now-standard `PlayerLoggedOutEvent` cleanup pattern.

> **Update 2026-07-21:** All six High/Medium findings in this section (1.1–1.6) are fixed — resolution note on each. Commits `0897b4d` (1.1), `5357fbc` (1.2), `ac72d9e` (1.3), `78b4323` (1.4), `5070a9e` (1.5), `b72fdc9` (1.6). The Low items (1.7 and §Low) remain open.

### High

**1.1 — Concurrent beam-channel casters can hijack/desync each other's target**
Evidence: `spell/beam/WandBeamChannelLogic.java:42,103` keys `SESSIONS` by *caster* UUID only, never by target. `spell/beam/WandBeamSpellHandlers.java` applies velocity (`Leviosa`) and strips curse effects (`Crucio`) directly on the shared target with no "claimed by" check. Two players channeling Leviosa on the same entity fight over its velocity every tick (last-tick-wins); if caster A releases Crucio on a target B is also currently cursing, A's release wipes B's active curse effects off the target.
Why it matters: exploitable griefing — steal or cancel another player's channel on a shared target, with no feedback to the victimized caster.
Fix: add a per-target "claimed by" marker so a second caster on an already-claimed target is rejected/contested; scope end-effect cleanup to fire only when no other channel still claims the target.
Effort: M · Risk: Low · Files: `spell/beam/WandBeamChannelLogic.java`, `spell/beam/WandBeamSpellHandlers.java`
**✅ FIXED 2026-07-21 (`0897b4d`).** Added `spell/beam/BeamTargetClaims` (target→caster, `putIfAbsent`). The first caster to affect a target claims it; a second is contested and applies nothing. Cleanup releases only via the holder, so no channel can strip another's effects; release wired on target change, target loss and `clearSessionEffects`.

**1.2 — Beam channel never ends on caster death — victim stuck under the effect**
Evidence: the channel loop is driven only from `WandItem.onUseTick`; `WandBeamChannelLogic.endChannel` is invoked from respawn/logout/dimension-change handlers in `command/WizardsAndBeastsCommands.java:52-77`, but never from `LivingDeathEvent`. Vanilla's `stopUsingItem()` on death silently halts `onUseTick` without routing through any hook this mod listens to.
Why it matters: an Imperio victim stays "controlled" (or a Crucio target keeps taking mid-channel effects) for however long the caster sits on the death screen.
Fix: add a `LivingDeathEvent` handler calling `WandBeamChannelLogic.endChannel(...)`.
Effort: S · Risk: Low · Files: `item/wand/WandItem.java`, `spell/beam/WandBeamChannelLogic.java`
**✅ FIXED 2026-07-21 (`5357fbc`).** Added a `LivingDeathEvent` handler in `WizardsAndBeastsCommands` calling `WandBeamChannelLogic.endChannel`, alongside the existing respawn/logout/dimension-change cleanups.

**1.3 — `FlooCallService` has no logout cleanup — soft-locks the caller on reconnect**
Evidence: `event/floo/FlooCallEvents.java` registers `PlayerTickEvent.Post`, `ServerChatEvent`, `ServerStoppingEvent` — **no `PlayerLoggedOutEvent`**. `floo/call/FlooCallService.java:42` `SESSIONS` is only drained by the per-tick countdown, which simply stops firing once the player disconnects. `floo/FlooTravelHandler.java:51` then blocks that player from any future Floo travel ("You are mid-Floo-call...") until a *new* call happens to replace the stuck entry.
Why it matters: reproducible soft-lock of an entire travel system after any disconnect mid-call.
Fix: add a `PlayerLoggedOutEvent` handler calling `FlooCallService.endCall(...)`.
Effort: S · Risk: None · Files: `event/floo/FlooCallEvents.java`, `floo/call/FlooCallService.java`
**✅ FIXED 2026-07-21 (`ac72d9e`).** Added a `PlayerLoggedOutEvent` handler in `FlooCallEvents` calling `FlooCallService.endCall`, mirroring the `ServerStopping` cleanup already present.

### Medium

**1.4 — Broom `lastInputSequence` never resets per rider — one crafted packet permanently jams a shared broom.** `entity/broom/BroomEntity.java:71-72,140-146` — sequence guard lives on the shared entity, never reset on passenger change; a single malicious `sequence=MAX_VALUE` packet then dismount permanently blocks all future legitimate riders. Fix: key acceptance on `(riderUUID, sequence)`. Effort S. **✅ FIXED 2026-07-21 (`78b4323`).** Reset the sequence baseline in `BroomEntity.removePassenger`; the input handler already validates `player.getVehicle() == broom`, so the monotonic guard only needs to hold within one rider's session.

**1.5 — Imperio control silently stealable by re-cast.** `spell/imperio/ImperioServerLogic.java:48` overwrites the victim's control state unconditionally on a second cast; the displaced original caster's commands now silently no-op with zero feedback. Not an authority bypass (the new controller's command-authority check is correctly guarded) — a UX/griefing gap. Fix: reject re-cast on an already-controlled target or push a "control lost" notice. Effort S. **✅ FIXED 2026-07-21 (`5070a9e`).** `beginControl` now detaches the displaced caster's `CASTER_TO_VICTIM` mapping and pushes an unbind + "Your hold on the Imperius Curse is broken." notice; same-caster refresh is unaffected.

**1.6 — "Dark Corruption" character-sheet stat is permanently dead — never wired to the real mechanic.** `registry/ModAttributes.java` registers a real, syncable vanilla `Attribute` that `AttributesTab.java:121` reads to draw the bar — but every actual corruption mutation (`WandBeamSpellHandlers`, `ImperioServerLogic`, `DiaryService`, `HorcruxBearerTickHandler`, `ResurrectionStoneItem`, `SpellExecutor`) writes to a completely separate `ModAttachments` float. The Attribute's `setBaseValue` is never called anywhere in the tree. The character sheet's Dark Corruption bar always reads 0 regardless of the player's real tracked value. Fix: mirror attachment writes into the Attribute, or delete the dead Attribute and read the attachment directly. Effort S. **✅ FIXED 2026-07-21 (`b72fdc9`).** Root cause was broader — no `EntityAttributeModificationEvent` attached the Attribute to the player at all (`getAttribute` returned null). Now attached, and `DarkCorruptionService` mirrors the attachment onto the syncable Attribute at its single write chokepoint, re-synced on login/respawn (copyOnDeath-safe). `WAND_AFFINITY`/`BEAST_RESISTANCE` share the gap but are left off until a real writer exists.

**1.7 — No client/server reconciliation for broom flight.** Physics run identically both sides with only a 10-tick keepalive sync; server-side collision detection can resolve up to latency+10 ticks behind where the client already flew, with no corrective snap-back ever sent. Not exploitable (bounded, symmetric physics) but a real fairness/visual-desync gap for remote observers. Effort M.

### Low
Cooldown-bypass via mid-cast exception (`SpellCastService.java:225-232` catches before cooldown/cast-count writes — narrow, needs a spell that both applies a visible effect and then throws); no rate limit on `SpellLeviosaAdjustC2SPayload` (harmless, output is clamped); `SetFlexibilityPayload.java:29` missing the `instanceof ServerPlayer` guard every sibling handler uses (not currently exploitable); `PocketDimensionEvents.java:53` leaks one `HashMap` entry per player who disconnects mid-countdown (cosmetic).

### Verified solid (no action needed)
Payload authority across `VaultActionC2SPayload`, `ApparitionRequestPayload`, `FlooTravelRequestC2SPayload`, `PocketConfigC2SPayload`, `HeritageSelectC2SPayload`, `SkillUnlockC2SPayload`, `MirrorConnectC2SPayload`, `DiaryWriteC2SPayload`, `LegilimencyRequestPayload` all re-validate ownership/range/prerequisites server-side. Broom `BroomInputC2SPayload` carries only booleans + yaw/pitch + a sequence number, never position/velocity. Cooldown check-before-mutate has no TOCTOU race (single-threaded packet handling). Login/respawn/dimension-change resync is centralized in `PlayerStateSyncService.syncFullLoginState` and covers spells, skills, vocations, abilities, heritage, vault, Azkaban trespass flag, carried-niffler, and form state. No dangerous static "current caster" singletons exist anywhere — all cross-player state uses UUID-keyed maps. Most session managers (Imperio, Protego, Mirror, Animagus, Marauders Map, Cloak, Bestiary, Niffler, Bloodpact) already clean up correctly on logout/death.

---

## 2. Gameplay Loop Audit

Tracing a fresh player through heritage → wand → first spell → skill tree → first creature → first transport, grounded in the actual trigger/unlock code (not documentation).

### Critical — RESOLVED 2026-07-13

**2.1 — ~~Wandmaker's Bench is a dead end the UI never explains.~~ FIXED.**
This finding described `AUD-D-001` (wandmaking recipes never load), which was believed still-open per `FULL_AUDIT_REPORT.md`'s 2026-07-06 snapshot. Re-verified 2026-07-13: it was already fixed by the time of this audit — `data/wizards_and_beasts/recipe/wandmaking_*.json` sit in the correctly-loaded directory with `"type": "wizards_and_beasts:wandmaking"` present, and `WandmakersBenchMenu.findRecipe` resolves matches correctly. In the same pass, recipe coverage was expanded from the original 6 files to the full 30-combo matrix (10 wood types recognized by `WandBlankItem.wandWoodFromLogBlock` × the 3 bench-craftable cores gated by `WandCoreMaterialItem.isBenchCore`) — every wood/core combination a player can physically place in the bench now has a matching recipe. `./gradlew compileJava` clean.
**Residual, much lower-severity item:** `WandmakersBenchScreen.renderBg` still has no explicit "no matching recipe" message for the (now much rarer) case of an unrecognized wood/core combo — worth a small polish pass, but no longer a dead-end since the matrix is complete. Downgraded to Low.
Effort: S (residual message only) · Risk: Low · Files: `wand/gui/WandmakersBenchMenu.java`, `client/wand/gui/WandmakersBenchScreen.java`, `data/wizards_and_beasts/recipe/wandmaking_*.json`

### High

**2.2 — First-wand acquisition path is undiscoverable by design.** The only wand source is interacting with a Wandmaker villager (`wand/ollivander/OllivanderInteractHandler.java:29-51`). The handbook's own `wands.json` chapter explicitly refuses to say how: *"No standard crafting procedure... obtained through the established channels of acquisition."* The Handbook itself must be hand-crafted with no in-game hint that doing so is useful. No chat line, toast, or advancement ever points a fresh player at "find a Wandmaker villager." Combined with 2.1, the entire wand pillar is invisible without wiki knowledge. Fix: send a chat/action-bar hint on heritage-lock; consider auto-granting the handbook. Effort S.

**2.3 — Dark spells learnable/castable with zero warning while Dark Arts is off.** Citing still-open `AUD-F-001`/`AUD-F-002`: neither the Avada Kedavra beam handler nor spell learning checks `Module.DARK_ARTS`. A curious new player can unlock and one-shot-kill with an Unforgivable in a build that ships Dark Arts disabled by default, with no in-fiction or UI warning this is unintended. Fix: already scoped in the prior audit. Effort S.

### Medium

**2.4 — Bestiary discovery is completely silent.** `event/bestiary/BestiaryDiscoveryHandler.java` sets discovery tiers on kill/proximity/loot with zero chat, toast, sound, or advancement calls anywhere in the file. A player has no way to know a new entry was logged unless they proactively open the screen and notice a diff. Fix: add a lightweight toast on tier transition. Effort S–M.

**2.5 — No unified signposting for "how do I learn my first spell."** Three disjoint paths exist (skill-node effects, `/wandb spell learn`, a craftable Spell Teacher block) with nothing tying them together; the handbook never mentions the Spell Teacher block exists. A player who never opens the skill tree (compounded by 9.1, the unbound Character Sheet key) has no other discoverable route. Fix: mention the block explicitly in the handbook. Effort S.

**2.6 — Obscurial ability onboarding gap — self-flagged, still unresolved.** `ALPHA_STATUS.md` already documents that dark-form-only ability input needs onboarding guidance; the handbook's `abilities.json` chapter still doesn't explain the restriction. Effort S.

### Low
Protego's known recast bug (`AUD-E-001`, still open) will read as "shield spells are broken" to a new player, since Protego is typically an early defensive spell — cited for onboarding impact, not re-derived.

### What's already well-onboarded
Heritage selection (`HeritageSelectC2SPayload.handle`) auto-opens on first login, shows per-variant tooltips, gives explicit chat confirmation, and grants skill points with its own message — this is the model the rest of onboarding should be brought up to. The spell HUD (`SpellDiamondOverlay`) is self-explanatory without external docs. Azkaban's ocean-pinned, 1024–2560-block-out placement means a fresh player cannot casually stumble into what is effectively end-game content (once that content exists — see §7.1).

---

## 3. Spell System Audit

### High

**3.1 — Cast-rejection feedback is silent for the most common failure modes.** `spell/cast/SpellDeniedS2CPayload` is a zero-field record; its only client handler just plays a generic denied sound. Server-side, rejects for "not holding wand," "no active spell," "unknown spell," "not known yet," and "on cooldown" call only a server-only debug counter that's never synced — no chat message anywhere. `client/spell/ui/SpellRejectReasonFormatter.java` already defines human-readable labels for exactly these reasons but has **zero call sites** anywhere in the codebase. A player who mashes cast on cooldown or without a wand gets an identical unexplained "fizzle."
Fix: add a `reason` field to `SpellDeniedS2CPayload`, populate it from existing reject-code constants, wire the already-built formatter to render it.
Effort: M · Risk: Low · Files: `network/spell/SpellDeniedS2CPayload.java`, `spell/cast/SpellCastService.java`, `client/spell/ui/SpellRejectReasonFormatter.java`

### Medium

**3.2 — Highest-damage combat spells are gated more loosely than mid-tier ones.** Bombarda (dmg 7.0, AOE explosion) and Confringo (dmg 6.0, ignite+explosion) require only "knows prerequisite," while strictly weaker Depulso/Glacius/Incendio require "proficient in prerequisite." The gate strength is inverted relative to power. Fix: raise the two outliers' requirement tier. Effort S, pure data change.

**3.3 — No resource/mana dimension exists; cooldown is the only cast cost.** `SpellDefinition` has no cost field at all; balance across all 32 spells rides entirely on cooldown ticks plus the shared proficiency-scaling curve. This flattens design space (no "expensive but fast-cycling" vs. "cheap but rare" spell archetypes). Not necessarily wrong for this game's design, but worth an explicit decision — and the flat spell-teacher learn-cost (currently disabled by default) would be a no-op across all tiers if ever enabled. Effort L if adding a resource pool, S if just fixing flat pricing.

### Low — cheap JSON-migration wins
The Java→JSON spell-component migration (documented in `SPELL_EFFECT_COMPONENTS.md`) left 7 bespoke Java spells. Three are now blocked by a single missing piece each, not genuine complexity:
- **Imperio** (3.4): fully data-expressible except one missing `controlDurationTicks` codec field. Effort S.
- **Avada Kedavra** (3.5): blocked only by the requirement schema's single-clause limit (needs `SpellRequirement.allOf` support in JSON, which the schema doesn't have yet). Effort M.
- **Riddikulus** (3.6): the smallest bespoke class in the codebase, blocked only by a missing generic "particle burst" effect component. Effort S — and adding that component also unblocks any future JSON spell wanting a cosmetic burst.

---

## 4. Wand System Audit

### Critical — RESOLVED 2026-07-13

**4.1 — ~~Wand corruption system is functionally dead.~~ FIXED.** `wand/corruption/WandCorruptionSystem.java:54-61` gated corruption accrual on `spellKey.getPath().contains("dark_")`/`"light_"`. The only call site (`SpellExecutor.java:76`) passed real spell IDs like `avada_kedavra`, `crucio`, `imperio` — **none of which contained those substrings** — so `onSpellCast` always computed a zero increment and the corruption-based power multiplier / threshold-warning messages were unreachable.
**Fix applied:** `isDarkSpell`/`isLightSpell` (Identifier-substring heuristics) removed entirely; `onSpellCast` and `getEffectivePowerMultiplier` now take a `SpellCategory` directly (`DARK_ARTS` for the dark-magic accrual/bonus path, `DEFENSE` — Protego/Expecto Patronum's category, the closest existing equivalent to "light magic" — for the wood-affinity healing-penalty path) instead of re-deriving intent from an Identifier string. The call site in `SpellExecutor.executeGeneric` already had the live `Spell` object in scope (`ctx.spell()`), so this also deleted a dead `Identifier.parse`/fallback try-catch block plus its now-unused `LOGGER` field and three now-unused imports. `./gradlew compileJava` and `./gradlew test` both clean (33/33 test classes pass).
Effort: S · Risk: Low · Files: `wand/corruption/WandCorruptionSystem.java`, `spell/cast/SpellExecutor.java`

### High

**4.2 — Cosmetic wand-customization "Core" slot reuses the exact names of the real gameplay Core stat.** `WandModuleRegistry.bootstrap()` registers a purely visual `CORE` customization slot with variants named `phoenix_feather`, `dragon_heartstring`, `unicorn_hair` — 5 of the exact 10 enum names from the *real*, stat-bearing `WandCore` used at crafting time (`WandStatsResolver.applyCore`, e.g. Phoenix Feather = +15% combat damage). A player picking "Phoenix Feather" in the cosmetic UI will reasonably believe they changed their wand's power; they changed nothing. This is a design collision, not a coincidence — the class's own doc comment even flags it as pending a "future WandModuleProperties system."
Fix: rename the cosmetic variant IDs to avoid lore-material overlap, or wire the slot to actually reflect the real crafted core.
Effort: S (rename) / M (unify) · Risk: Low · Files: `wand/customization/WandModuleRegistry.java`, `wand/stat/WandCore.java`

### Medium

**4.3 — Two independently-formulated "wand suits wizard" systems, one hiding an opaque hash-based bonus.** The Ollivander bonding trial (`WandResonanceSystem`) and the per-cast allegiance layer (`Compatibility.score`) each re-derive wood/core/flex/length affinity from Heritage tags with *different* math, and `Compatibility` additionally folds in a `fate()` term — a deterministic hash of the wand's ordinal properties, dressed as mystical flavor but fixed and reproducible, worth up to 10% with zero player-facing explanation anywhere. Fix: consolidate onto one formula; surface or remove `fate`. Effort M, Risk Medium (touches live bonding math).

**4.4 — Ollivander trial's core/length scoring collapses to a function of player level alone for most players.** Two of the trial's four scoring axes derive their "ideal" target from vanilla reach attributes that only change during Animagus size transformations — for any player not currently transformed, the formula reduces to pure level-gating, undermining the "wand chooses the wizard" narrative the trial is built to sell. Effort M.

### Low
Wand integrity repair has an undocumented hard asymmetry: a normal repair always adds a flat +0.5 capped at 0.9 regardless of starting integrity, with no in-game explanation of why 0.9 is an unreachable ceiling without a master repair (`wand/integrity/WandIntegritySystem.java`). Effort S.

**Also carrying over from `FULL_AUDIT_REPORT.md` (still open, directly relevant to this system):** `AUD-C-001`/`AUD-C-002` (`WandTipWorldCache` bone-name mismatch means the beam origin never matches the modelled tip for standard wands, and the cache is never cleared). `AUD-D-001` (wandmaking recipes never load — see §2.1) is **resolved** as of 2026-07-13.

---

## 5. Creature Audit

Sampled all 86 `data/wizards_and_beasts/creatures/*.json` definitions plus the full `CreatureAbility` vocabulary.

### Critical — RESOLVED 2026-07-13

**5.1 — ~~Bestiary discovery is inert for the entire manifest roster.~~ FIXED.** Every one of the 95 bestiary entries had `"encounterTrigger": "MANUAL"`. The discovery system (`BestiaryDiscoveryHandler`) has fully working KILL, PROXIMITY (with a skill-node XP-multiplier hook), and LOOT triggers — but none ever fired because no entry opted in. Only the hand-wired Niffler got organic discovery, silently nullifying the `BESTIARY_XP_MULTIPLIER` skill node for every other creature.
**Fix applied:** set a real `encounterTrigger` on all 94 non-Niffler entries, derived from each creature's actual temperament/behavior rather than guessed — 86 entries cross-referenced against their `data/wizards_and_beasts/creatures/*.json` `temperament` field (HOSTILE/NEUTRAL → `KILL`, PASSIVE → `PROXIMITY`); the 9 bespoke entities without a creature-definition file (augurey, bowtruckle, cornish_pixie, dementor, mooncalf, phoenix, streeler, thestral) individually verified against their entity classes and set to `PROXIMITY` (e.g. `StreelerEntity`/`DementorEntity` have no attack goal or are functionally unkillable, confirming sighting-based discovery is correct, not a guess). Niffler's entry stays `MANUAL` — it already has its own bespoke `NifflerEventHandler` wiring that calls `BestiaryDataHelper.setTier` directly, bypassing this system entirely; changing its trigger would just add redundant, potentially-conflicting proximity ticks on top of the working hand-wired path. Result: 68 `KILL` + 26 `PROXIMITY` + 1 `MANUAL` (Niffler). `./gradlew compileJava`/`test` clean.
Effort: S–M · Risk: Low · Files: `data/wizards_and_beasts/bestiary/entries/*.json` (94 files), `event/bestiary/BestiaryDiscoveryHandler.java`

### High

**5.2 — Loot tables are mostly vanilla-equivalent clusters.** Creatures group into shared generic drops: 8 creatures share rabbit-hide drops, 6 share string, 11 "big hide" creatures share leather. Only ~10 of 86 creatures drop a distinctive mod item. Fix: author one signature item per named-lore creature. Effort M.

**5.3 — Abilities are heavily reused — most creatures are stat-reskins.** Across all 86 JSONs, `enrage` appears on 33 creatures, `status_on_hit` on 17, `leap`/`blink_away` on 12 each; only **15 abilities are unique to a single creature** (web_snare, boggart_dread, explosive_horn, flame_burst, fwooper_song, sphinx_riddle, thunderbird_storm, etc.). Two creatures (flobberworm, horklump) have zero abilities at all. The architecture supports deep per-creature variety — content simply hasn't filled it in yet. Effort L (content).

### Medium

**5.4 — Custom AI is a thin layer over one shared generic goal set.** Only 10 bespoke `Goal` classes exist to serve the entire 86+ roster; everything else routes through one shared `GenericBeastEntity.registerGoals()` path.

**5.5 — Two parallel on-hit-effect vocabularies coexist.** The older `Trait` enum (FIRE_ATTACK/POISON_ATTACK/PETRIFY, used by 15+11+1 creatures) and the newer JSON-configurable `StatusOnHit` ability (17 creatures) both apply on-hit effects with no clear signal to a new contributor which one to extend. Fix: deprecate the overlapping `Trait` flags in favor of `StatusOnHit`. Effort M, Risk Medium (balance drift if migrated amounts differ).

### Flagship creature roster (evidence-ranked)
1. **Hungarian Horntail** (representative of all 10 dragons) — dedicated `DragonEntity` + `DragonTraits` codec, the only family with true per-instance bespoke tuning.
2. **Basilisk** — bespoke death-gaze petrify mechanic + constrict.
3. **Acromantula** — unique web-snare AI + pack tactics.
4. **Thunderbird** — unique storm ability + dive-bomb AI.
5. **Occamy** — unique synced size-shift render mechanic.
6. **Erumpent** — unique explosive-horn ability + thematic drop.
7. **Fire Crab** — unique flame-burst ability + AI + thematic drop.
8. **Sphinx** — richest ability stack of any non-dragon creature (riddle + heal aura + dread aura + status-on-hit).

---

## 6. RPG Progression Audit

### High

**6.1 — Three skill nodes grant an ability flag nothing reads.** Cross-referencing every `abilityId` in the 161 skill nodes against actual Java consumers: `dark_knowledge`, `basic_casting`, and `wand_study` are never consumed anywhere. `basic_casting` (root of the entire Spell Mastery tree) and `wand_study` (root of Wandlore) have **only** this dead effect — spending a skill point on either currently grants zero mechanical benefit. This extends the memory-noted "wired dead skills" pattern that was previously fixed for five other nodes; these three were missed.
Fix: wire a small perk to each, or drop the dead effect.
Effort: S · Risk: Low · Files: `data/wizards_and_beasts/skill_nodes/spell_mastery/basic_casting.json`, `.../wandlore/wand_study.json`, `.../dark_arts/dark_knowledge.json`

**6.2 — Dark Arts module disabled by default (carryover, still open).** The entire Dark Arts skill tree, its vocation, and dark-artefact content ship but are inert out of the box (`ModuleManager.java:24`). Cited for completeness since it directly bears on whether Dark Arts is a complete gameplay loop today (it isn't, by default).

### Medium

**6.3 — Vocations specialize shallowly and non-exclusively.** Since the Phase 3 web rework, `VocationDefinition`'s own javadoc states it "gates nothing" — the gating fields were deliberately removed. All 5 vocations now grant only 1–2 flat percentage bonuses, and any player can freely allocate points in every tree regardless of declared vocation. This appears to be a deliberate design reversal (worth confirming intent before re-gating rather than treating as a bug). Effort L if revisited.

**6.4 — Heritage differentiates skill-tree access for only 2 of 11 heritages.** Only Goblin and House-Elf get their own skill-tree audience; the other nine (Werewolf, Obscurial, Veela, Giant, Centaur, Vampire, Merpeople, Wizardkind) all resolve to the generic Wizard tree. Heritage does have ongoing mechanical relevance via persistent flat stat modifiers — it's not a pure one-time flavor pick — but it barely reshapes *what* a player can build beyond the two exceptions. Effort L.

### Low
Legilimency is functional but structurally isolated — no skill node gates or enhances it, unlike the comparable Elf-Apparition/Goblin-Appraisal pattern (Effort M). Animagus stag form remains a placeholder per prior project notes (carryover, not re-derived).

---

## 7. World Audit

### Critical — STOPGAP APPLIED 2026-07-13

**7.1 — Azkaban has zero reachable content.** The structure NBT template is **225 bytes** (a real fortress export would be tens of KB+); `AzkabanStructure.java` skips generation entirely when the template resolves empty, and the template-piece data-marker handler explicitly no-ops. `spawn_overrides` in the structure JSON is empty, and Dementor is only ever constructed from an operator-only debug command — there is no spawn placement wired anywhere. No loot table is tied to the structure. A player who does the real work to locate this one-per-world, ocean-pinned structure finds a bare rock crag with an essentially empty box on top.
**Stopgap applied:** `ModuleManager.java:37` flips `Module.AZKABAN` from `PREVIEW` to `DISABLED` — the codebase already had this exact toggle built for this exact purpose (the pre-existing code comment literally said "set DISABLED to stop generation entirely"). `AzkabanStructure.findGenerationPoint` returns `Optional.empty()` when the module isn't enabled, so the structure no longer generates at all; registration (type/biome/structure-set) is unaffected per the existing design, so re-enabling later needs only flipping the flag back. Verified this also correctly neuters `DementorEntity`'s AI/aura tick and `DementorPursueGoal` (both independently gate on the same module check) — an admin-summoned Dementor goes fully inert too, so there's no half-disabled state where worldgen is off but a stray Dementor is still live. `./gradlew compileJava`/`test` clean.
**Still open — the real fix:** author an actual fortress NBT export, wire Dementor spawn placement, and attach loot (see §7.4, the orphaned `hidden_wizarding_cache.json`) before flipping this back to `PREVIEW`/`ENABLED`. That remains L effort and is unchanged by this stopgap.
Effort: S (stopgap, done) / L (real content, open) · Risk: Low · Files: `module/ModuleManager.java`, `azkaban/structure/AzkabanStructure.java`, `azkaban/structure/AzkabanTemplatePiece.java`, `data/wizards_and_beasts/structure/azkaban.nbt`

### Medium

**7.2 — Devil's Snare is a plain vanilla `Block::new` with no unique mechanic.** One of the most recognizable plants in the source material specifically because of its behavior ships as inert decoration, in contrast to Mandrake (custom scream-on-harvest) and Mallowsweet (custom regeneration cloud), which both have real bespoke behavior classes. Effort M.

**7.3 — No distinct magical biome; vegetation is bolted onto vanilla biomes at low density.** No custom `Biome` registration exists anywhere; all magical flora rides `neoforge:add_features` modifiers on stock biomes at 3–4% chance per attempt. This may be an intentional, cheaper design choice (avoiding the TerraBlender complexity already removed per project history) — worth an explicit decision rather than an assumption. Effort S (messaging) to L (real biome).

**7.4 — Orphaned loot table `hidden_wizarding_cache.json` — a ready-made exploration reward never wired to anything.** A well-tuned chest table (broom, currency, wand blank) exists with zero references anywhere else in the tree — almost certainly meant for Azkaban and abandoned when that work stalled. Currently, exploration yields **zero** structure-tied loot tables. Fix: wire it via the already-existing (currently no-op) data-marker hook once Azkaban's template is real. Effort S once §7.1 lands.

### Low / informational
Mandrake's loot table doesn't age-gate the harvest (breaking at age 0 yields the same as fully mature — no risk/reward curve). Azkaban is literally the mod's only custom structure, which correctly frames prioritization. **Positive finding:** magical tree generation (`ElderTreeFeature` and siblings) is genuinely bespoke procedural code, not copy-pasted vanilla generators — a real strength worth preserving as the template for future worldgen work.

---

## 8. Brewing Audit

### High

**8.1 — Only one brew exists in the entire mod.** `data/wizards_and_beasts/brews/` contains exactly one file (Wiggenweld Potion); `brew/Brews.java` has zero Java built-in registrations to fall back on. The pipeline (codec → loader → cauldron interaction → potency scaling) is well-built and proven correct by the one shipped brew — the gap is purely content volume. Fix: author 5–8 more brews using existing plants and vanilla ingredients; no new code required. Effort M.

**8.2 — Active cauldron brews are held in a non-persistent static in-memory map.** `brew/CauldronBrewing.java:52` — `ACTIVE_BREWS` is a plain `HashMap`, never written to `SavedData`/NBT. Ingredients are consumed immediately on brew start, before the timer completes. A server restart, `/reload`, or chunk unload mid-brew silently discards the entry — the player's already-consumed ingredients are gone with no compensation or error message.
Fix: persist via `SavedData` keyed by dimension+position, or force-complete/refund on level unload.
Effort: M · Risk: Low-Medium · Files: `brew/CauldronBrewing.java:46-52,99-123`

### Medium

**8.3 — Brewing has zero in-game discoverability.** None of the 12 handbook chapters cover brewing/potions/cauldrons; the custom inventory-scan recipe matching never surfaces in the vanilla recipe book either. Low-cost today (only one brew exists) but compounds as content is added. Effort S.

### Low
`AUD-D-002` (brew reload-listener directory bug) is **confirmed fixed** in current source — correcting an assumption in the original prompt; Wiggenweld Potion does load and is reachable today. No crafting recipe exists for any of the three cauldron tiers, meaning a from-scratch survival playthrough currently has no vanilla-visible way to obtain a cauldron at all (Effort S — ship the recipes now, defer automation).

---

## 9. UI/UX Audit

### High

**9.1 — Character Sheet has no reachable entry point out of the box.** `SpellKeyBindings.CHARACTER_SHEET` ships unbound (`InputConstants.UNKNOWN`), with a code comment noting the natural key (`K`) is already claimed by the Skill Menu. There is no item, command, or menu link to it anywhere else. The Character Sheet is pillar #2 of the mod's stated design and is completely unreachable unless a player manually finds a free key in Controls — something nothing in-game tells them to do.
Fix: give it a real default binding, or add a button to an always-open surface (an orphaned `ICON_TAB` texture already exists for exactly this purpose).
Effort: S · Risk: Low · Files: `client/spell/SpellKeyBindings.java`, `client/spell/CharacterSheetKeyHandler.java`

**9.2 — Vault UI: failed withdraw/exchange actions are silent no-ops.** Every reject branch in `VaultActionC2SPayload.handle` is an `if (amount > 0)` guard with no `else` and no error message — clicking "Withdraw" with insufficient balance does precisely nothing visible. Fix: mirror the rejection-message pattern already used correctly in `SkillUnlockC2SPayload`. Effort S.

### Medium

**9.3 — Gringotts vault screen is 100% hardcoded English, no i18n.** Every label uses `Component.literal` instead of `Component.translatable`, breaking localization and contrasting with the rest of the mod's screens. Effort S.

**9.4 — Skill-tree screen family has a visible art/consistency seam.** The recently-reworked `SkillTreeScreen` renders a full star-chart (procedural starfield, ley-lines), but `SkillAccessDeniedScreen` still uses the old flat panel look — a denied-access bounce drops the player from polished art back to a plain dialog mid-flow. Self-documented in `AUDIT_PUNCHLIST.md`'s addendum; confirmed still true. Effort S.

### Low
Server-side skill-unlock chat messages print raw untranslated lang keys for the ~100 procedurally-named filler nodes (the client-side renderer resolves names correctly, but commands/packets don't route through the same resolution). Orphaned Phase-2 skill-tree grid assets remain in the tree post-rework (maintenance hazard, not a bug — safe to delete once verified zero references). Wand-tip beam origin is cosmetically off for standard wands (`AUD-C-001`, carryover). `CharacterSheetTextures.java`'s "no art yet" TODO is stale — all four referenced PNGs already exist (see also §13.3).

---

## 10. Performance Audit

### Critical — RESOLVED 2026-07-13

**10.1 — ~~`WandRenderer` rebuilds the wand-module registry into fresh maps/lists every rendered frame.~~ FIXED.** `applyBoneVisibility` (a GeckoLib per-frame callback) calls `WandModuleRegistry.getAllForSlot(slot)` for each of 5 slots; that method allocated a new `LinkedHashMap` and copied a fresh list every single call — at full display framerate, 60–240+ allocations/sec on the mod's single most central item.
**Fix applied:** `WandModuleRegistry` now maintains a `volatile Map<WandSlot, List<WandModule>>` cache built once and invalidated (set to `null`) on every registry write (`register` during `bootstrap()`, `clearDatapack()`, `addDatapackModule()`) rather than on every read. `getAllForSlot` is now an O(1) cache hit on the hot path; rebuild only happens after an actual bootstrap or datapack reload, not per frame. Confirmed `WandSlot.renderOrder()` (the other per-slot iteration in `applyBoneVisibility`) already returned a static list — `getAllForSlot` was the only real per-frame allocator in this path. `./gradlew compileJava`/`test` clean.
Effort: S · Risk: Low · Files: `wand/customization/WandModuleRegistry.java`

### High

**10.2 — `WandBeamRenderer` re-runs the full ray+entity-scan every frame instead of once per tick.** The server-side twin explicitly throttles the identical scan to a tuned tick interval; the client renderer doesn't, duplicating a block raycast + AABB entity scan at display framerate purely for cosmetics while any beam channel is held. Fix: cache the resolved beam ray per game tick, interpolate the drawn endpoint with `partialTick`. Effort S.

**10.3 — Two loaders bypass the reload-listener/codec architecture entirely.** `WandResonanceConfigLoader` and `OllivanderPoolLoader` hand-parse raw `JsonObject` fields inside a single generic `try/catch`, invoked once at server start rather than wired into the reload-listener event. `/reload` silently does nothing for wand resonance tuning or the Ollivander pool, and a malformed entry throws an unchecked NPE reported only by file name, with far weaker diagnostics than every codec-based loader in the mod. Effort M.

**10.4 — Two registries use non-atomic mutable-map reload swaps.** `VocationRegistry` and `WandModuleRegistry` mutate a plain (non-volatile) map in place during reload, unlike every other registry in the codebase (`CreatureDefinitionRegistry`, `BestiaryEntryRegistry`, `PocketTemplateRegistry`, `SkillTrees`), which correctly swap a `volatile` field to a freshly built immutable map. This is the same anti-pattern `FULL_AUDIT_REPORT.md`'s `AUD-G-006` already flagged and fixed in one registry — it independently exists in two more the prior audit didn't touch. A concurrent reader during `/reload` can observe a transiently empty or half-populated map. Effort S.

### Medium

**10.5 — `BestiaryDiscoveryHandler` rebuilds a full registry index every 20-tick scan** (rebuild cost is reload-invariant dead weight at current scale, 95 entries — still open, Effort S to cache). ~~Its LOOT branch has a live correctness bug: `onDrops` marks every loot-triggered bestiary entry as discovered on any player kill of anything, without checking the killed entity's type.~~ **Fixed 2026-07-13** as part of wiring §5.1 (real `KILL`/`PROXIMITY`/`LOOT` triggers were about to go live, so the pre-existing `onDrops` bug was closed in the same pass — it now compares `entry.entityType()` against the dying entity, mirroring `onKill`'s already-correct pattern).

**10.6 — `ObscurialHeritageHandler` string-parses numeric flags every tick for every Obscurial player**, purely to check whether a throttle window elapsed — the only per-tick string-parsing pattern found in the audit. Effort M, Risk Low (touches save format — needs a migration path).

### Low
Two per-tick handlers (`CloakEffectsHandler`, `ObscurialHeritageHandler`) scan the full online player list unconditionally — cheap today, flagged as the first thing to profile if the server ever targets large concurrent counts.

**Profile-first order:** ~~WandRenderer/WandModuleRegistry~~ (fixed 2026-07-13) → WandBeamRenderer/BeamRayResolver → BestiaryDiscoveryHandler (correctness bug fixed 2026-07-13; the per-scan rebuild itself is still open) → ObscurialHeritageHandler.

---

## 11. Data-Driven Architecture Audit

### Medium

**11.1 — Loader validation quality is wildly inconsistent.** `SkillNodeLoader` is a clear gold standard: duplicate-ID detection with file provenance, per-edge validation with actionable warnings, and a full BFS reachability pass warning about unallocatable nodes. By contrast, `CreatureDefinitionLoader`, `BestiaryEntryLoader`, `BroomDefinitionLoader`, and `VocationLoader` are each a 3-line decode-and-replace with **zero** business-rule validation. A malformed-but-codec-valid creature or vocation JSON loads silently with no diagnostic, while an equally malformed skill node gets a precise warning — datapack authors get a wildly inconsistent debugging experience depending on which system they extend.
Fix: extract `SkillNodeLoader`'s duplicate-detection idiom into a shared helper other loaders can opt into.
Effort: M · Risk: Low

### Low — duplicated loader logic (explicit, as requested)
Four loaders (`CreatureDefinitionLoader`, `BestiaryEntryLoader`, `BroomDefinitionLoader`, `VocationLoader`) share a byte-identical 3-line `apply()` that isn't factored into a common base class — any future improvement has to be applied four times by hand. Three more (`SpellReloadListener`, `BrewReloadListener`, `BrewingRecipeReloadListener`) independently duplicate a "loaded/failed counter + per-entry try/catch" boilerplate. Both are cheap to consolidate into one or two shared helpers. Effort S each.

**Also carrying over (still open, directly relevant here):** `AUD-D-004` (`pocket_templates/` — confirmed by this audit's world/brewing pass to now have a real wired loader, contrary to the prior report's finding; this appears to have been fixed since 07-06 and should be marked resolved in `FULL_AUDIT_REPORT.md`).

---

## 12. Testing Audit

### Critical — DOC HALF FIXED 2026-07-13, REAL FIX OPEN

**12.1 — The advertised "GameTest gate" gates nothing.** Zero `@GameTest` annotations exist anywhere in the codebase (note: the modern NeoForge 1.21.x GameTest system is fully data-driven — no more `@GameTest` annotation at all; it's a `Consumer<GameTestHelper>` registered against `BuiltInRegistries.TEST_FUNCTION` + a `data/<ns>/test_instance/*.json` tying it to a structure template and `"environment": "minecraft:default"`). `build.gradle`'s own comment on the `gameTestServer` run config admits *"the server will crash when no gametests are provided."* `DEVELOPER_REFERENCE.md` and `ALPHA_STATUS.md` both described this as an integration/GameTest gate; it is, at best, a boot smoke test. No structure placement, block-entity interaction, or in-world behavior (Wandmaker's Bench, Floo fireplace, trunks, Azkaban) has any automated in-world coverage.
**Doc correction applied 2026-07-13** (the audit's own listed fallback option): `DEVELOPER_REFERENCE.md`'s task table and `ALPHA_STATUS.md`'s known-issues note both corrected to call `runGameTestServer` a boot-only smoke test, not a coverage gate, so nobody is misled by the docs while real coverage is pending.
**Real fix still open:** author an actual `@Consumer<GameTestHelper>`-based test + `test_instance` JSON + structure `.nbt` template (structure spawn, bench craft flow, or trunk enter/exit are the best first candidates). Blocked mid-session on local shell-tool availability before a structure NBT could be authored and verified end-to-end — picking this back up is the next step, not abandoned.
Effort: S (doc correction, done) / L (real GameTest suite, open) · Risk: Low

### High

**12.2 — The central spell cast-resolution path has zero tests.** No test exists for `SpellExecutor` or `SpellCastService` — the mod's single most-exercised runtime path. A test on cast-order/effect-lifecycle invariants would have statically caught the still-open Protego recast bug (`AUD-E-001`). Effort M.

**12.3 — No reload-safety tests, despite known reload bugs.** All existing codec tests are one-shot schema round-trips; none simulate an actual reload cycle. The exact bug class this would catch (silent stale data after `/reload`) is documented in both `AUD-D-002` (fixed) and the non-atomic-swap findings in §10.4. Effort M.

### Medium
No multiplayer/two-player simulation tests exist anywhere — cross-player systems (Imperio, blood pacts, Niffler theft, vault) rely entirely on the manual two-client smoke pass described in `ALPHA_STATUS.md`. No test exercises `Module` gating, despite two real gating bugs (`AUD-F-001`/`002`) having been found only by manual audit. No automated cross-registry validation exists between the creature manifest, creature JSON, bestiary entries, and assets — `DEVELOPER_REFERENCE.md` itself already names this as a needed refactor.

---

## 13. Asset Audit

### Critical

**13.1 — ~80% of the creature roster ships with explicitly-marked placeholder models.** 78 of 98 entity animation files contain the literal comment `"PLACEHOLDER box rig — swap with a Blockbench model..."`. Matching geometry files are generic box rigs from the `tools/creature_gen.py` scaffolding tool; textures for these creatures run 200–300 bytes versus 2,000+ bytes for genuinely hand-built entities (dragons, Niffler, Dementor, Mooncalf, etc.). This is the single highest-impact, best-evidenced art gap in the mod — larger than the handbook or character sheet, both of which turn out to already have real art (see below).
Fix: prioritize Blockbench passes creature-by-creature, starting with the highest-visibility/most-frequently-spawned beasts; the generation tool is documented as idempotent, so incremental swaps are safe.
Effort: XL · Risk: Low (isolated to assets) · Files: `assets/wizards_and_beasts/geckolib/{models,animations}/entity/*`, `tools/creature_gen.py`

### Low / corrections to prior assumptions
Two art gaps assumed elsewhere in project history turn out to already be resolved: the handbook's cover art (`book.png`, `emblem.png`) is real authored art, not procedural (only body text/chapter icons remain procedural); and `CharacterSheetTextures.java`'s "needs PNG art" TODO is stale — the four referenced textures already exist on disk. Both are worth a one-line doc/comment correction so future contributors don't duplicate finished work.

---

## 14. Documentation Audit

### High

**14.1 — `ASSET_STATUS.md` is stale by ~2 months and materially wrong about current coverage.** Dated May 19, unmodified since; claims most item texture PNGs are missing, when 717 distinct textures now exist with zero content-duplicates. Anyone using this doc to prioritize art work would target already-finished areas and miss the real gap (§13.1). Fix: regenerate or fold into `DEVELOPER_REFERENCE.md` and mark superseded. Effort S.

### Medium

**14.2 — `AUDIT_PUNCHLIST.md` confirmed stale/unreliable.** Four of its ten "BLOCKER" claims were spot-checked against current source and all four are refuted (bestiary textures exist, KNOWLEDGE stat is properly derived, currency lang keys are present, `ModNetworkAzkaban` compiles fine). This confirms the project-memory note flagging this doc as unreliable. Effort S — mark superseded.

**14.3 — `ARCHITECTURE.md` is the stalest doc in the repo and self-admits partial resync.** Oldest by a wide margin (May 7, 766 lines); states a NeoForge patch version two behind current and describes a 27-spell composition against the current 32. It's also the largest doc, making it the most likely to be consulted by mistake instead of the authoritative `DEVELOPER_REFERENCE.md`. Effort S — add a banner pointing to the authoritative doc.

**14.4 — Root-level documentation sprawl (15 overlapping audit/status files) is itself a maintenance risk.** Modification dates span May 7 → Jul 13; several self-describe as partial or historical. §14.1–14.3 above are direct symptoms. Fix: keep `DEVELOPER_REFERENCE.md` as living reference and `FULL_AUDIT_REPORT.md`/`MIGRATION_DELTAS.md` (plus this document) as the current punch-list; add "superseded" banners to the stale docs; archive one-shot historical audits under a `docs/history/` folder instead of root. Effort S.

### Low
`CREATURES.md` (merged from `CREATURE_BUILD_AUDIT.md`/`CREATURE_BUILD_PROGRESS.md`/`CREATURE_ABILITIES_PLAN.md`) consolidates the creature build log, roster, and abilities — the placeholder-asset methodology and bone-name contract from the original docs are preserved with current roster counts (95 bestiary entries, 86 generic creatures).

**Note:** all spot-checked claims in `DEVELOPER_REFERENCE.md` itself (file/skill-node/creature/bestiary/spell/vocation counts) matched current source exactly — it remains trustworthy as the authoritative reference.

---

## 15. Final Roadmap

### Critical — Must Fix Before Beta

~~1. §2.1 — Wandmaker's Bench dead end (root-caused by `AUD-D-001`).~~ **RESOLVED 2026-07-13** — recipe directory/type-field fixed, coverage expanded to the full 30-combo wood×core matrix.
~~2. §4.1 — Wand corruption system dead (substring-match bug).~~ **RESOLVED 2026-07-13** — `isDarkSpell`/`isLightSpell` replaced with direct `SpellCategory` checks; mechanic now reachable.
~~3. §5.1 — Bestiary discovery inert for 86/95 creatures.~~ **RESOLVED 2026-07-13** — `encounterTrigger` set per entry (68 KILL / 26 PROXIMITY / 1 MANUAL-by-design), plus the `onDrops` LOOT correctness bug it exposed (§10.5) fixed in the same pass.
~~4. §10.1 — `WandRenderer` per-frame reallocation on the mod's most-viewed item.~~ **RESOLVED 2026-07-13** — `WandModuleRegistry.getAllForSlot` now cached, invalidated only on registry writes.
5. **§7.1** — Azkaban has zero reachable content. **Stopgap applied 2026-07-13** (`Module.AZKABAN` → `DISABLED`, so players can no longer reach the empty stub); the real fortress/spawns/loot build remains open, L effort.

6. **§12.1** — GameTest gate gates nothing. **Doc correction applied 2026-07-13** (docs no longer overclaim coverage); the real `@GameTest`-equivalent suite remains open, L effort — de-risks everything else on this list once done.

Remaining new findings from this audit:
1. **§13.1** — ~80% of creatures are placeholder models (XL effort — the dominant cost driver for the whole roadmap; stage this rather than block on it).

Carried over from `FULL_AUDIT_REPORT.md`:
- Still open: `AUD-F-001`/`AUD-F-002` (Avada/dark-spell-learning missing Dark Arts gate), `AUD-E-001` (Protego recast self-shatters), `AUD-C-001`/`AUD-C-002` (wand-tip cache).
- **Resolved 2026-07-13:** `AUD-D-001` (wandmaking recipes — see above).

### High Priority
§1.1–1.3 (beam-channel target hijack, death cleanup, Floo logout soft-lock) · §2.2–2.3 (wand acquisition undiscoverable, dark spells unwarned) · §3.1 (silent cast rejection) · §4.2 (Core slot naming collision) · §5.2–5.3 (loot/ability reuse) · §6.1–6.2 (dead skill nodes, Dark Arts disabled) · §8.1–8.2 (single brew, non-persistent active brews) · §9.1–9.2 (Character Sheet unreachable, Vault silent failure) · §10.2–10.4 (beam renderer scan, bypassed loaders, non-atomic registries) · §12.2–12.3 (untested cast path, no reload-safety tests) · §14.1 (stale asset doc).

### Medium Priority
§1.4–1.7 · §2.4–2.6 · §3.2–3.3 · §4.3–4.4 · §5.4–5.5 · §6.3–6.4 · §7.2–7.4 · §8.3 · §9.3–9.4 · §10.5–10.6 · §11.1 · §12 (remaining) · §14.2–14.4.

### Low Priority
All remaining items in §1–§14 marked Low, plus informational/corrective findings (stale TODOs, doc-drift corrections, already-fixed items worth marking resolved).

---

## Top 10 Improvements by Impact
1. ~~Fix `AUD-D-001` (wandmaking recipe loading)~~ — **done 2026-07-13.** Recipe directory/type-field fixed and coverage expanded to the full 30-combo matrix; see §2.1.
2. ~~Wire `encounterTrigger` per bestiary entry (§5.1)~~ — **done 2026-07-13.** 68 KILL / 26 PROXIMITY / 1 MANUAL (Niffler, bespoke-wired); the `onDrops` LOOT correctness bug it exposed (§10.5) fixed in the same pass.
3. ~~Fix `WandCorruptionSystem`'s dark/light substring match (§4.1)~~ — **done 2026-07-13.** Replaced with direct `SpellCategory` checks; mechanic now reachable.
4. ~~Fix `WandRenderer`/`WandModuleRegistry` per-frame allocation (§10.1)~~ — **done 2026-07-13.** Cached, invalidated only on registry writes.
5. Wire `SpellRejectReasonFormatter` into cast-denial feedback (§3.1) — fixes silent-failure UX across the entire spell system in one change.
6. Close the Dark Arts gating gap on Avada Kedavra and spell learning (`AUD-F-001`/`002`) — both a balance and an onboarding-safety issue.
7. ~~Azkaban: disable generation as a stopgap~~ — **done 2026-07-13** (§7.1). Real fix still open: wire the orphaned `hidden_wizarding_cache` loot table + real spawns + fortress NBT (§7.1, §7.4) before re-enabling.
8. Fix Protego's recast ordering (`AUD-E-001`) — the first defensive spell most players learn currently self-breaks.
9. Persist active cauldron brews (§8.2) — closes a real data-loss bug in the only brewing flow that exists.
10. Stand up a staged creature-art production pass (§13.1) — the single largest content gap in the mod; even partial progress against the flagship-first order in §5 materially changes perceived polish.

## Top 10 Easiest Wins
1. ~~Fix `WandCorruptionSystem` substring match → `SpellCategory.DARK_ARTS` check (S).~~ **Done 2026-07-13.**
2. ~~Cache `WandModuleRegistry.getAllForSlot` (S).~~ **Done 2026-07-13.**
3. Add `PlayerLoggedOutEvent` cleanup to `FlooCallService` (S).
4. Add `LivingDeathEvent` hook to end wand beam channels (S).
5. Bind the Character Sheet keybind by default (S).
6. Add rejection messages to `VaultActionC2SPayload` (S).
7. ~~Fix `BestiaryDiscoveryHandler.onDrops`'s missing entity-type check (S).~~ **Done 2026-07-13** (alongside §5.1).
8. Ship crafting recipes for the three cauldron tiers (S).
9. Rename the cosmetic wand Core slot's variant IDs to stop colliding with `WandCore` (S).
10. Add "superseded" banners to `ARCHITECTURE.md`, `ASSET_STATUS.md`, `AUDIT_PUNCHLIST.md` (S).

## Technical Debt Summary
- **Duplicated loader logic**: 4 loaders share an identical 3-line `apply()`; 3 more share a "loaded/failed counter" pattern (§11).
- **Inconsistent loader validation**: one gold-standard loader (`SkillNodeLoader`) vs. several that do zero business-rule validation (§11.1).
- **Two registries missing the volatile-swap reload pattern** used correctly everywhere else (§10.4) — a second instance of a bug class `FULL_AUDIT_REPORT.md` already flagged once.
- **Two independently-formulated wand-compatibility formulas** with an unexplained hash-based bonus buried in one of them (§4.3).
- **Two parallel creature on-hit-effect vocabularies** (`Trait` enum vs. `StatusOnHit` ability) with no migration path (§5.5).
- **JSpecify/annotation-dialect split** (carryover `AUD-B-007`/`008`, still open per `FULL_AUDIT_REPORT.md`).
- **Documentation sprawl**: 15 overlapping root-level docs, several confirmed stale or self-contradicting the authoritative reference (§14).

## Gameplay Polish Summary
Onboarding has genuinely strong bones — heritage selection and the spell HUD are model examples — but they're undermined by several completely silent or undiscoverable flows downstream: wand acquisition, the Character Sheet, the Vault, and bestiary discovery all fail with zero player-facing feedback. The wand system has real mechanical depth (resonance, corruption, integrity) but loses legibility to a naming collision and two redundant formulas doing the same job differently. The creature roster has genuine flagship-quality examples (dragons, Basilisk, Thunderbird, Sphinx) but the bulk of the 86-creature roster is a stat-reskin of shared infrastructure, both mechanically and visually. World exploration currently has no reward loop at all — its one structure is an empty shell with an orphaned loot table sitting unconnected nearby. Brewing is a proven pipeline with exactly one proof-of-concept brew, not yet a system.

## Beta Readiness Score: 47 / 100
The engineering substrate — networking authority, data-driven loader architecture, the completed spell-component migration, GeckoLib render-state safety — is close to beta-grade quality on its own. The score is pulled down hard by the fact that several **headline gameplay loops are non-functional today**: the wand-crafting bench cannot produce a wand, wand corruption never fires, bestiary discovery never fires for 90% of creatures, and the mod's one custom structure is an empty shell. Add to that zero automated coverage for the core spell-cast path or any in-world behavior, and a from-scratch player today will hit multiple dead ends in their first few hours specifically in the systems the mod advertises as its pillars. None of this reflects a fragile codebase — it reflects an alpha where infrastructure outpaced content wiring, which is a fundamentally healthier problem to have than the reverse.

## Estimated Work Remaining to a Polished 1.0
Excluding the creature-art backlog (tracked separately below, since it dominates the timeline and can be staged incrementally without blocking other work):
- Critical/blocker fixes (new + carryover from `FULL_AUDIT_REPORT.md`): **~2–3 engineer-weeks**.
- High-priority UX/feedback wiring (cast-rejection messages, onboarding hints, vault/bench error states): **~2 engineer-weeks**.
- Medium content/balance passes (creature ability variety, brewing content, vocation/heritage depth): **~4–6 engineer-weeks**.
- Testing infrastructure (minimal GameTest suite, reload-safety harness, multiplayer simulation fixtures): **~2–3 engineer-weeks**.
- Documentation consolidation: **~2–3 days**.

**Subtotal: roughly 10–14 engineer-weeks (2.5–3.5 months) of focused solo work** to close every Critical/High finding in this report and reach a coherent, fully-wired beta.

**Creature-art backlog** (§13.1, 78 placeholder models): a full ship-quality Blockbench pass on all of them is realistically **2–4 additional months** of dedicated art time at a sustainable pace. This can and should be decoupled from the rest of the roadmap — ship the flagship-first order in §5 as a rolling content update rather than gating beta on 100% model completion, the same way the dragon roster and bespoke entities were clearly prioritized first already.
