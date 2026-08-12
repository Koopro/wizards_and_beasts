# Cast Pipeline Audit — Step 1 (Foundation Verification)

**Mod:** Wizards & Beasts (`wizards_and_beasts`, root `at.koopro.wizardsandbeasts`)
**Stack:** MC 1.21.11 / NeoForge 21.11.x / Java 21 / Mojang mappings / GeckoLib 5.4.5
**Branch inspected:** `azkaban-nbt-pipeline`
**Date:** 2026-06-09
**Scope:** Diagnosis + documentation only. No fixes, no content changes, no gating changes.

> **Relationship to other audits (added 2026-07-13):** This is the deep single-axis trace of the cast pipeline (input → C2S → server → executor → effect → HUD → module gate). Its named-bug findings were later promoted into the stable `AUD-*` ID registry tracked in `FULL_AUDIT_REPORT.md` (2026-07-06) — overlapping concepts live in Pass C (`AUD-C-001`/`AUD-C-002` wand-tip bone mismatch + cache-never-cleared) and Pass E (`AUD-E-001` Protego recast ordering). The unique content preserved here is the file:line trace depth and the §7 manual-witness repro + the §Q1/§Q2 hardcoded-vs-datapack and Protego-specific-vs-generic verdicts. For current resolution status of any named finding, consult `ALPHA_IMPROVEMENT_AUDIT.md` (2026-07-13).

> **Method note / honesty disclaimer.** This audit is a *static source trace*. The live
> end-to-end witness required by §6 of the task (cast from keybind → effect → HUD sweep →
> cooldown re-cast block → clear) and the empirical Q2 entity-spawn test **were not performed**:
> this environment is headless and cannot launch and hand-drive an interactive NeoForge client
> (keyboard/mouse + visual HUD observation). Those steps are **BLOCKED, not skipped silently** —
> exact manual repro steps are provided in §7 so a human (or a graphical session) can complete
> the witness. Every claim below is backed by a `file:line` reference so it can be verified
> without re-deriving it.

---

## Naming reconciliation (vs. expected names in the prompt)

| Expected (prompt) | Actual in source | Status |
|---|---|---|
| `SpellCastC2SPacket` | `SpellCastC2SPayload` | renamed (Packet→Payload); same role |
| `SpellCastService` | `SpellCastService` | **exact match** |
| `SpellExecutor` | `SpellExecutor` | **exact match** |
| `CastManager` (terminology doc) | *no such class* | does not exist; orchestration lives in `SpellCastService` |
| `CastContext` | `CastContext` | **exact match** |
| `Module.SPELLS` | *no such enum value* | does not exist; the real gate is `Module.WANDS_AND_SPELLS` |

**Verdict on §5 "names diverge materially":** No material divergence that blocks downstream work.
The only rename is `Packet`→`Payload` (cosmetic, NeoForge idiom), `CastManager` is just a doc
alias for `SpellCastService`, and there is no `Module.SPELLS` (use `WANDS_AND_SPELLS`). Downstream
prompts (steps 2/4–6) can safely refer to `SpellCastC2SPayload`, `SpellCastService`,
`SpellExecutor`, `CastContext`, and `Module.WANDS_AND_SPELLS`.

---

## 1. Input layer — how a cast is triggered client-side

There is **no dedicated "cast" keybind.** A cast is triggered by *using the wand item*
(right-click hold + release), not by a key in `SpellKeyBindings`.

- **Keybinds** are registered in
  [SpellKeyBindings.java](src/main/java/at/koopro/wizardsandbeasts/client/spell/SpellKeyBindings.java#L68-L83)
  via `RegisterKeyMappingsEvent`. The spell-relevant ones select/route, they do **not** cast:
  - `SPELL_UP/RIGHT/DOWN/LEFT` (arrow keys) — choose the active loadout slot.
  - `SPELL_MENU` (`G`) — open the radial spell menu.
  - `OBSCURIAL_ABILITY_PRIMARY/SECONDARY` (`N`/`M`) — Obscurial abilities (separate path).
  - Slot selection / menu input is handled in
    [SpellClientInputHandler.onClientTick](src/main/java/at/koopro/wizardsandbeasts/client/spell/SpellClientInputHandler.java#L27-L42)
    → `SpellInputController.handleGameplayBindings(...)`.
- **The actual cast trigger** is the wand item's use cycle in
  [WandItem.java](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java):
  - `use(...)` ([:77-97](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L77-L97))
    gates on `WandModuleHooks.isWandsEnabled()` (i.e. `Module.WANDS`), then `startUsingItem(hand)`.
  - `onUseTick(...)` ([:110-115](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L110-L115))
    drives beam channels via `WandBeamChannelLogic.tick`.
  - `releaseUsing(...)` ([:118-132](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L118-L132))
    is the cast moment: on the **client** side it sends the C2S payload —
    `ClientPacketDistributor.sendToServer(new SpellCastC2SPayload())`
    ([:128](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L128)) — unless
    `WandCastClient.tryOpenImperioCommandMenu()` intercepts (Imperio special-case). The server
    side records hold ticks (`WandCastTiming.recordRelease`) and ends any beam channel.

**Cast = "press-and-release right-click while holding a bonded wand."** Hold duration is captured
(`WandCastTiming`) and consumed by spells that care (e.g. Protego tier).

---

## 2. C2S packet — the cast payload

[SpellCastC2SPayload.java](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java)

- **`CustomPacketPayload`:** `record SpellCastC2SPayload()` — zero fields
  ([:24](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java#L24)).
  The active spell is **not** in the packet; the server reads it from the player's attachment
  (`PlayerSpellData.getActiveSpellId()`), so the packet is purely a "release happened" signal.
- **Type / id:** `wizards_and_beasts:spell_cast`
  ([:32-33](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java#L32-L33)).
- **`StreamCodec`:** `PacketCodecUtils.noPayloadCodec(SpellCastC2SPayload::new)`
  ([:35-36](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java#L35-L36)) — empty codec.
- **Handler:** `handle(pkt, ctx)`
  ([:43-48](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java#L43-L48))
  → `ctx.enqueueWork` → `completeWandCastRelease(player)`
  ([:62-78](src/main/java/at/koopro/wizardsandbeasts/network/spell/SpellCastC2SPayload.java#L62-L78)),
  which applies a **duplicate-release guard** (`IGNORE_RELEASE_UNTIL_GAME_TICK`, used after
  server-driven releases like Avada) and then delegates to `SpellCastService.completeWandCastRelease`.
- **Registration:** `RegisterPayloadHandlersEvent` → `PayloadRegistrar` in
  [ModNetworkSpells.register](src/main/java/at/koopro/wizardsandbeasts/network/spell/ModNetworkSpells.java#L30-L34):
  `registrar.playToServer(TYPE, STREAM_CODEC, SpellCastC2SPayload::handle)`.

---

## 3. Server resolution — validation before dispatch

All resolution lives in
[SpellCastService.completeWandCastRelease](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L44-L246)
(there is no separate `CastManager`). The validation chain, in order:

1. Server-level check ([:45-48](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L45-L48)).
2. Holding a wand ([:50-53](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L50-L53)).
3. `LANGLOCK` effect ([:54-57](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L54-L57)).
4. Mental-stability misfire (Obscurial) ([:58-63](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L58-L63)).
5. **Wand bond / master** check ([:65-75](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L65-L75)).
6. `SpellNetworkGuards.canUseWand` ([:78-81](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L78-L81)).
7. Active spell present ([:82-86](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L82-L86)).
8. Spell exists in registry (`Spells.byId`) ([:88-92](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L88-L92)).
9. **Spell known** (`data.knowsSpell`) ([:94-97](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L94-L97)) — this is the per-player "knows the spell" gate, not a module gate.
10. Obscurial-ability routing guard ([:98-102](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L98-L102)).
11. **Requirement gate** (`spell.getRequirement().isMet`, behind `Config.enforceSpellRequirements`) ([:104-110](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L104-L110)).
12. Obscurial dark-form restrictions ([:112-127](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L112-L127)).
13. **Cooldown** check (`data.isOnCooldown`) ([:135-141](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L135-L141)).
14. **Global cooldown** (`data.isGlobalCooldownActive`) ([:142-148](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L142-L148)).
15. Build `CastContext` (wand stats, allegiance, compatibility, scaling profile) ([:150-167](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L150-L167)).
16. Obscurial collapse-instability fizzle ([:169-184](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L169-L184)) and instability fizzle ([:186-199](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L186-L199)).
17. **Gamp's Law** validation ([:201-219](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L201-L219)) — hard reject or penalty.
18. **Dispatch** → `SpellExecutor.executeGeneric(castContext, serverLevel)` ([:222](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L222)).
19. Cooldown write + sync ([:230-244](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L230-L244)) — see §5.

Rejections return a `CastResult` and increment per-reason counters via `debugReject` /
`rejectWithHumanStress` ([:248-266](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L248-L266)).
Reject codes are centralised in
[SpellRejectCodes.java](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellRejectCodes.java).

---

## 4. Executor — how a resolved spell's effect is applied

[SpellExecutor.java](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java)

- Entry: `executeGeneric(CastContext, ServerLevel)`
  ([:61-108](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L61-L108)).
  Runs the **modifier pipeline**: wand corruption / foreign-master ([:66-83](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L66-L83)),
  skill-system damage/cooldown mods, wand-stat application, Obscurial cast mods ([:84-87](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L84-L87)),
  dark-corruption-vs-light-magic penalty ([:89-94](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L89-L94)),
  and a **misfire roll** ([:96-103](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L96-L103))
  that can early-return (fizzle) before any effect.
- Dispatch hook: `spell.executeCast(ctx, level)`
  ([:107](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L107)). The base
  [Spell.executeCast](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spell.java#L76-L78)
  delegates to `SpellExecutor.dispatchGeneric`; subclasses (Protego, Lumos, …) override it for
  custom behaviour (entity spawns, etc.).
- `dispatchGeneric(ctx, level)`
  ([:115-176](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L115-L176))
  switches on `SpellProperties.getCastType()`:
  - `PROJECTILE` → `spell.spawnProjectile(...)` → spawns a registered `SpellProjectileEntity`
    ([Spell.java:236-246](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spell.java#L236-L246)).
  - `SELF` → repair / self-utility rules / self-effects ([:134-161](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L134-L161)).
    The self-utility table (`SELF_UTILITY_RULES`, [:178-211](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L178-L211))
    is **hardcoded by spell id** (lumos, nox, protego, episkey, frigora, capacious_extremis, …) —
    a step-2 migration concern (see §6).
  - `CONE` / `TARGETED` → `SpellCastHandlers.handleCone/handleTargeted`.
  - `BEAM_LETHAL` / `BEAM_CHANNEL` → per-tick logic in `WandBeamChannelLogic` (no-op here).

**Entity-spawning spells bypass `dispatchGeneric`'s default arm** by overriding `executeCast`:
e.g. [Protego.executeCast:40-76](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Protego.java#L40-L76)
spawns a `ProtegoShieldEntity` directly via `level.addFreshEntity(shield)`. Projectile spells
(e.g. Stupefy) keep the default arm and spawn via `spawnProjectile`.

---

## 5. Effect + cooldown — write, track, and HUD read

**Write (server, authoritative).** After a successful `SpellExecutor.executeGeneric`,
`SpellCastService` ([:230-244](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L230-L244)):
- Computes `cooldown = clamp(baseCooldown × modifierMult × scalingMult, floor=50%base, ≥1)`.
- `data.setCooldown(spellId, expiryTick)` where `expiryTick = currentTick + cooldown` and
  `currentTick = serverLevel.getGameTime()`.
- `data.setGlobalCooldownEndTick(currentTick + GLOBAL_COOLDOWN_TICKS)` (GCD = **5 ticks**,
  [:40](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L40)).
- Pushes a delta to the client: `SpellDataDeltaS2CPayload.sendTo(player, spellId, expiryTick, …, gcdEndTick)`.

**Cooldown clock invariant** is documented in-code
([:129-133](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L129-L133)):
always `getGameTime()` (monotonic, persisted as absolute expiry ticks). Never `getDayTime()`/wall clock.

**Track + HUD read (client mirror of server state).**
- The client mirror is
  [ClientSpellDataState](src/main/java/at/koopro/wizardsandbeasts/client/spell/state/ClientSpellDataState.java):
  `applyDelta(...)` ([:125-130](src/main/java/at/koopro/wizardsandbeasts/client/spell/state/ClientSpellDataState.java#L125-L130))
  and `applyFullSync(...)` ([:86-123](src/main/java/at/koopro/wizardsandbeasts/client/spell/state/ClientSpellDataState.java#L86-L123))
  apply server-sent expiry/GCD ticks. It also captures `cooldownSpanTicks` (the true applied
  duration, since the server sends only the expiry) for correct sweep denominators.
- The HUD is
  [SpellDiamondOverlay.render](src/main/java/at/koopro/wizardsandbeasts/client/spell/hud/SpellDiamondOverlay.java#L39-L206):
  - Per-slot **radial diamond sweep** ([:89-112](src/main/java/at/koopro/wizardsandbeasts/client/spell/hud/SpellDiamondOverlay.java#L89-L112)),
    reading `data.getCooldownExpiry(spellId)` and `ClientSpellDataState.getCooldownSpanTicks`.
  - **GCD ring** ([:145-160](src/main/java/at/koopro/wizardsandbeasts/client/spell/hud/SpellDiamondOverlay.java#L145-L160)),
    reading `data.getGlobalCooldownEndTick()` and `SpellCastService.GLOBAL_COOLDOWN_TICKS`.
  - **Seconds readout** ([:164-185](src/main/java/at/koopro/wizardsandbeasts/client/spell/hud/SpellDiamondOverlay.java#L164-L185)).
  - Uses `ClientSpellDataState.monotonicTick(...)` to avoid the sweep springing backward on server lag.

**HUD source — server-authoritative vs client-predicted (explicit answer):**
The HUD reads a **client-side mirror of server-authoritative state**. The cooldown value is
*written only on the server* (`SpellCastService.setCooldown`) and *pushed* to the client via
`SpellDataDeltaS2CPayload`; the client does **not** predict or locally stamp cooldowns on cast.
There is therefore a one-round-trip latency between the cast packet and the sweep starting, but
the displayed value is authoritative (not a client guess). **Flag for step 5 (proficiency):** the
sweep denominator (`cooldownSpanTicks`) is reconstructed client-side from the observed expiry delta,
not sent explicitly — any future proficiency/scaling change to cooldown duration must keep that
reconstruction correct or the sweep fraction will be wrong.

---

## 6. Module gate — which `Module`, current state, where evaluated

- **The gate is `Module.WANDS_AND_SPELLS`** (defined in
  [Module.java:8](src/main/java/at/koopro/wizardsandbeasts/module/Module.java#L8)). **There is no
  `Module.SPELLS`.** A separate `Module.WANDS` gates whether the wand item can be *used* at all.
- **Current `ModuleManager` state**
  ([ModuleManager.java:19-38](src/main/java/at/koopro/wizardsandbeasts/module/ModuleManager.java#L19-L38)):
  - `WANDS` = **ENABLED**
  - `WANDS_AND_SPELLS` = **ENABLED**
  - `PROFICIENCY` = PREVIEW (counts as enabled; `isEnabled` returns true for ENABLED *or* PREVIEW)
  - `DARK_ARTS` = **DISABLED** (Imperio/Crucio/Avada effects stay gated off)
  - `CREATURES` = DISABLED, `BESTIARY`/`OWLS`/`PLAYER_STATS`/`PLAYER_ABILITIES`/`AZKABAN` = PREVIEW
  - `SKILL_TREES`/`BROOM_FLIGHT`/`POCKET_DIMENSIONS`/`CHARACTER_SHEET` = ENABLED, `FLOO_NETWORK` = DISABLED
- **Where the gate is evaluated — important nuance:** `WANDS_AND_SPELLS` is **not** a single hard
  guard at the top of the cast path. `SpellCastService` does **not** early-reject when it is
  disabled; it only consults it to decide whether to send the cosmetic `SpellDeniedS2CPayload`
  (e.g. [:137](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L137),
  [:144](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L144),
  [:179](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L179),
  [:195](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L195),
  [:208](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastService.java#L208)).
  The effective gating happens at the **effect-application** layer instead:
  - [Spell.canApplyEffect:217-227](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spell.java#L217-L227)
    — self/target effects only apply if `WANDS_AND_SPELLS` is enabled (and `DARK_ARTS`/`CREATURES`
    for those specific effects).
  - [Lumos.buildProperties:25-27](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Lumos.java#L25-L27)
    — the Lumos self-effect supplier returns `null` when `WANDS_AND_SPELLS` is disabled.
  - The HUD itself is gated by `WANDS_AND_SPELLS`
    ([SpellDiamondOverlay:89/145/164](src/main/java/at/koopro/wizardsandbeasts/client/spell/hud/SpellDiamondOverlay.java#L89)).
  - `SpellCastTargetedHandler:185` and `ModCreativeTabs:117` also branch on it.
  - The hard wand-use gate is `Module.WANDS` at
    [WandItem.use:78](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L78).

  **Net:** with the default config (`WANDS` + `WANDS_AND_SPELLS` both enabled) the canary needs
  **no temporary module toggle** (§4 not exercised). **Downstream caution:** because the gate is
  spread across effect sites rather than a single guard, disabling `WANDS_AND_SPELLS` would *still
  run* the cast pipeline (cooldown stamped, projectile entity potentially spawned) while silently
  no-op-ing effects — a latent inconsistency worth noting for any future module-toggle work.

---

## 7. Registered spells & canary selection

**Registered Java spells** (static fields in
[Spells.java:46-78](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spells.java#L46-L78)) — 25:

| Category | Spells |
|---|---|
| Combat | stupefy, expelliarmus, incendio, diffindo, bombarda, confringo, flipendo, glacius, depulso |
| Utility | lumos, nox, accio, reparo, wingardium_leviosa, alohomora, colloportus, liberacorpus, riddikulus, arresto_momentum, aguamenti |
| Defense | protego, expecto_patronum |
| Dark Arts | avada_kedavra, crucio, imperio, obscurus_surge, obscurus_grasp |

**Registered JSON / datapack spells** (loaded by `SpellReloadListener`) — 6, in
`src/main/resources/data/wizards_and_beasts/spells/`: `capacious_extremis`, `claustra_reverto`,
`episkey`, `finite_incantatem`, `frigora`, `levicorpus`.

**Total: 31 registered spell ids.**

**Which cast successfully today:** *Not empirically determined* (headless — see disclaimer). From
static analysis, every Java spell whose `executeCast`/`dispatchGeneric` arm is wired (all of the
non-Dark-Arts set) should cast, gated only by `knowsSpell` + requirement + cooldown. Dark-Arts
spells (avada/crucio/imperio + obscurus_*) have their *effects* gated off because `DARK_ARTS` is
DISABLED, though the pipeline still runs.

**Canary chosen: `lumos`** — a pure-effect `SELF` spell with no entity spawn
([Lumos.java](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Lumos.java)). It isolates
input → effect → cooldown → HUD. It is `UTILITY` (gated under `WANDS_AND_SPELLS`, which is ENABLED),
has `SpellRequirement.none()`, and base cooldown 200 ticks (10 s) — long enough to clearly observe
the HUD sweep and the re-cast block.

### Manual witness repro (for a graphical session — BLOCKED in this env)

1. `./gradlew runClient`, create/enter a world in creative.
2. Obtain a wand (creative tab) and bond it: right-click once to resonate, it becomes your master
   wand (`WandItem.use` resonance path).
3. Learn + slot Lumos: open spell menu (`G`) or use commands
   ([SpellCommands](src/main/java/at/koopro/wizardsandbeasts/spell/command/SpellCommands.java)) to
   learn `lumos` and set it as the active loadout slot (arrow keys select the slot).
4. **Cast:** right-click-hold the wand and release. Expect: `LUMOS_FIELD` effect applied, a particle
   burst, the cast sound.
5. **HUD:** the bottom-right diamond HUD slot for Lumos should start a dark radial sweep
   (10 s) plus a brief GCD ring; a seconds readout should count down.
6. **Re-cast block:** immediately right-click-release again → rejected (`COOLDOWN_ACTIVE`), a
   `SpellDeniedS2CPayload` feedback; the sweep should not reset. After 10 s the sweep clears and a
   re-cast succeeds.

---

## Q1 — Hardcoded or datapack-driven? **HYBRID (both paths live).**

**Evidence:** The registry supports three paths
([Spells.java class doc:21-33](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spells.java#L21-L33)):
1. **Hardcoded Java spells** — 25 concrete subclasses registered as static fields
   ([Spells.java:46-78](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spells.java#L46-L78)),
   finalized by `Spells.init()`.
2. **Datapack JSON spells** — a real, working reload pipeline:
   [SpellReloadListener](src/main/java/at/koopro/wizardsandbeasts/spell/def/SpellReloadListener.java)
   extends `SimpleJsonResourceReloadListener<SpellDefinition>`, reads
   `data/<ns>/wizards_and_beasts/spells/*.json` via `SpellDefinition.CODEC`, and registers each as a
   [JsonSpell](src/main/java/at/koopro/wizardsandbeasts/spell/core/JsonSpell.java) through
   `Spells.registerJson` ([:109-119](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spells.java#L109-L119)),
   clearing prior JSON spells each reload (`clearJsonSpells`, idempotent). **6 JSON spell files
   already ship** (capacious_extremis, claustra_reverto, episkey, finite_incantatem, frigora,
   levicorpus). This mirrors the `BestiaryEntry` reload pattern referenced in the prompt.
3. Addon Java spells via `RegisterSpellsEvent`.

**Conclusion for step 2:** The datapack mechanism is **already built and proven** (6 live JSON
spells). The migration is therefore *not* greenfield — but it is also *not* mostly done, because:
- 25 of 31 spells are still Java classes.
- Several JSON spells' **effects are still hardcoded in Java by id** — the `SELF_UTILITY_RULES`
  table in [SpellExecutor:178-211](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L178-L211)
  branches on `episkey`, `frigora`, `capacious_extremis`, `claustra_reverto` (all JSON spells). So a
  JSON `SpellDefinition` currently supplies *metadata* (cast type, projectile params, etc.) but
  bespoke behaviour is still in Java. Step 2 must decide how far the JSON schema absorbs behaviour.

---

## Q2 — Generic vs Protego-specific entity-spawn failure? **CANNOT EMPIRICALLY CONFIRM (headless); static evidence points AWAY from "generic".**

**Empirical status:** The task asks to cast ≥1 entity-spawning spell other than Protego and record
spawn/no-spawn. **This requires a running client and was not performed** (headless env). Honest
verdict: **UNRESOLVED pending the manual witness in §7.**

**Static evidence (the strongest available here):**
- All three relevant entity types are **registered**:
  [ModEntities:29-37](src/main/java/at/koopro/wizardsandbeasts/registry/ModEntities.java#L29-L37)
  — `spell_projectile` (`SpellProjectileEntity`), `patronus`, `protego_shield`.
- The cast path reaches `spell.executeCast(ctx, level)` **uniformly for every spell**
  ([SpellExecutor:107](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L107));
  there is no per-spell branch that could silently skip Protego specifically.
- **Protego** spawns its entity directly and unconditionally in its override
  ([Protego.executeCast:60-62](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Protego.java#L60-L62)):
  `new ProtegoShieldEntity(level, caster, tier)` then `level.addFreshEntity(shield)`. There is no
  module gate, no early return, and no conditional guarding that spawn.
- **Stupefy** (the suggested non-Protego entity spawner) is a `PROJECTILE`
  ([Stupefy.java:18](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Stupefy.java#L18)) that
  spawns via the default `dispatchGeneric` → `spawnProjectile` → `addFreshEntity`
  ([Spell.java:236-246](src/main/java/at/koopro/wizardsandbeasts/spell/core/Spell.java#L236-L246)).

Because the spawn call sites are present, registered, and reached by the same code path, **a
*generic* pipeline-level spawn failure is not supported by the source** — nothing in
`SpellCastService` → `SpellExecutor` would suppress entity spawning across the board. If the Protego
"cast-to-spawn" symptom is real, the most likely loci (to investigate in the step-4 prompt, *not
here*) are Protego-specific: the `ProtegoShieldEntity` constructor/spawn-validity (e.g. bounding box
/ `noPhysics` / position), client-side `ProtegoSpawnS2CPayload` handling/renderer, or
`ProtegoWardManager` interactions — none of which touch the shared pipeline.

**Provisional verdict (to be confirmed by the §7 witness):** **likely Protego-specific, not
generic** — the foundation pipeline appears sound. The manual test (cast Stupefy and one more
projectile; confirm the projectile entity appears; then cast Protego and confirm shield
spawn/no-spawn) will convert this from "static inference" to a definitive verdict.

---

## Downstream risk list (steps 2 & 4–6)

1. **[Step 2 — migration surface] Spells are 25 Java classes + 6 JSON.** Migrating Java spells to
   datapack JSON touches all of `spell/impl/*` plus the `SpellDefinition` codec. Not greenfield, but
   substantial.
2. **[Step 2 — behaviour leak] JSON spells still rely on hardcoded Java behaviour.**
   `SpellExecutor.SELF_UTILITY_RULES` ([:178-211](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellExecutor.java#L178-L211))
   and `SpellCastHandlers` branch on spell id. The JSON schema currently carries metadata, not full
   behaviour — step 2 must define how much behaviour the schema absorbs (effects, self-utility,
   cone/targeted handlers) vs. what stays in Java handlers keyed by id.
3. **[Step 5 — proficiency/HUD] HUD cooldown is server-authoritative but the sweep denominator is
   client-reconstructed.** `cooldownSpanTicks` is inferred from the observed expiry delta
   ([ClientSpellDataState:53-63](src/main/java/at/koopro/wizardsandbeasts/client/spell/state/ClientSpellDataState.java#L53-L63)),
   not sent. Any proficiency-driven change to cooldown duration must keep that reconstruction valid
   or the radial fraction will render wrong. (Visual code itself is out of scope to touch.)
4. **[Cross-step — gating inconsistency] `WANDS_AND_SPELLS` is not a single hard cast guard.** It is
   checked at scattered effect/HUD/feedback sites, not as an early reject in `SpellCastService`.
   Disabling it would still run the cast (cooldown stamped, projectile possibly spawned) with no-op
   effects. Any future module-toggle or "spells off" behaviour must account for this.
5. **[Step 4 — Protego] Q2 unresolved empirically.** Static analysis says the pipeline is sound and
   any Protego spawn bug is Protego-local, but this **must be confirmed with the §7 manual witness**
   before the step-4 prompt is finalized. Likely investigation targets are `ProtegoShieldEntity`
   spawn validity, `ProtegoSpawnS2CPayload`/renderer, and `ProtegoWardManager` — not the pipeline.
6. **[Method — no live verification ran] The end-to-end witness and Q2 cast test are BLOCKED in this
   headless environment.** They need a graphical client session (§7). This is the single biggest gap
   in this audit and should be closed before relying on the "foundation is sound" conclusion.

---

## Source-modification statement (§6)

**No source files were modified during this audit.** The §4 temporary module toggle was **not
used** (the canary `lumos` is enabled by default under `WANDS_AND_SPELLS`). The only file written is
this `PIPELINE_AUDIT.md` artifact.
