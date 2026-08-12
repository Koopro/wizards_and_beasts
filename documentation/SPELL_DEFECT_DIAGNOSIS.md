# Spell Defect Diagnosis — Patronus rotation & Imperio control

**Date:** 2026-08-02
**Branch:** `fix/chamber-of-secrets-module-gate` @ `d0313d96`
**Mode:** read-only diagnosis. No production code, data, or test file was modified.

## Preflight

| Check | Result |
| --- | --- |
| Working tree clean at start | **No** — 45 entries dirty (House Banner rework: `HouseBannerBlock`, `ModModelProvider`, `ModBlockLootTableProvider`, `WizardingWorldBlockRegistry`, generated banner models/blockstates/loot tables, untracked banner PNGs + `tools/banner_textures.py`, plus `WAND_REWIRE_AUDIT.md` / `WORKTREE_TRIAGE_AUDIT.md` / `tasks/todo.md`). Reported, not cleaned. None of it touches the Patronus or Imperio paths. |
| `./gradlew compileJava` | **Green** (exit 0) |
| `./gradlew build` | **Green** (exit 0) |

Both defects were isolated by static trace. Neither required a file edit.

---

## A — Patronus rotation

### A.1 Root cause

**The render is wrong. The entity is fine.**

Two lines apply a monotonically increasing yaw derived from entity age:

- [PatronusRenderer.java:100](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L100) — `poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5f));` (stag path)
- [PatronusRenderer.java:117](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L117) — `poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5f));` (borrowed vanilla model path)

`age` is `state.ageInTicks` ([PatronusRenderer.java:80](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L80)), which vanilla sets to `entity.tickCount + partialTick` — unbounded and ever-increasing:

```java
// EntityRenderer.java:173 (decompiled 1.21.11)
p_361028_.ageInTicks = p_362104_.tickCount + p_362204_;
```

So yaw = `1.5°` per tick = **30°/second = one full revolution every 12 seconds, forever**. That is the observed spin, exactly.

This is the same idiom vanilla uses to render a *deliberate* spin — `LivingEntityRenderer.java:181` spins the riptide/spin-attack pose with `Axis.YP.rotationDegrees(ageInTicks * -75.0F)`. The Patronus is doing a slower version of the riptide spin.

**Compounding cause:** the entity's yaw is never set and could not reach the renderer even if it were.

- `PatronusEntity` never calls `setYRot`, `setYBodyRot`, `setYHeadRot`, or `lookAt` anywhere. Grep over [PatronusEntity.java](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java) returns zero hits. Motion is written as raw position/velocity only — [PatronusEntity.java:135-136](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L135-L136) (hunt) and [PatronusEntity.java:149-152](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L149-L152) (orbit, `setPos` + `setDeltaMovement(Vec3.ZERO)`). Entity yaw stays `0` for its whole life.
- `PatronusRenderer` extends raw `EntityRenderer`, not `LivingEntityRenderer` ([PatronusRenderer.java:42](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L42)). Body rotation in vanilla is applied by `LivingEntityRenderer.setupRotations(..., state.bodyRot, ...)` (`LivingEntityRenderer.java:87`) using `LivingEntityRenderState.bodyRot/yRot/xRot` (`LivingEntityRenderState.java:14-16`). The base `EntityRenderState` **has no rotation fields at all** and `EntityRenderer` applies none.
- Consequently `PatronusRenderState` ([PatronusRenderer.java:264-267](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L264-L267)) carries only `corporeal` and `formId`. There is no rotation channel to fill.

So: the Patronus has no facing, and the renderer substitutes a clock-driven spin for one.

### A.2 Confidence

**High.**

`ageInTicks` semantics are confirmed against the decompiled 1.21.11 source, not assumed. The spin math is arithmetic. The absence of any yaw write in the entity is a whole-file grep. Nothing further would raise confidence short of an in-client A/B, which is not needed — the code cannot produce any other behaviour.

### A.3 Evidence chain

| Hop | Anchor | Finding |
| --- | --- | --- |
| 1 | [ExpectoPatronum.java:112](src/main/java/at/koopro/wizardsandbeasts/spell/impl/ExpectoPatronum.java#L112) | Sole spawn call → `PatronusEntity.trySpawn(...)`. No rotation argument. |
| 2 | [PatronusEntity.java:97](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L97) | `setPos` only. Yaw left at the `Mob` default of `0`. |
| 3 | [PatronusEntity.java:106-166](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L106-L166) | Entire server tick. Position and velocity written; **no rotation written, ever**. |
| 4 | [ClientSetup.java:25](src/main/java/at/koopro/wizardsandbeasts/client/ClientSetup.java#L25) | `event.registerEntityRenderer(ModEntities.PATRONUS.get(), PatronusRenderer::new)`. |
| 5 | [PatronusRenderer.java:42](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L42) | `extends EntityRenderer<…>` — the non-living base. No `setupRotations` exists on this path. |
| 6 | [PatronusRenderer.java:69-73](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L69-L73) | `extractRenderState` pulls `corporeal` + `formId`. Correct, but no rotation is extracted. |
| 7 | [PatronusRenderer.java:80](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L80) | `float age = state.ageInTicks;` |
| 8 | `EntityRenderer.java:173` (vanilla) | `ageInTicks = tickCount + partialTick` — unbounded. |
| 9 | [PatronusRenderer.java:100](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L100), [:117](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L117) | **`Axis.YP.rotationDegrees(age * 1.5f)` → 30°/s continuous yaw. The defect.** |

### A.4 Corporeal vs non-corporeal

**The defect is specific to corporeal Patronuses.** `renderMist` ([PatronusRenderer.java:134-152](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L134-L152)) applies no `mulPose` rotation at all — it only pulses and wobbles. Non-corporeal wisps do not spin.

Within corporeal, **both** sub-paths spin: the borrowed vanilla model (cat/wolf/rabbit/parrot) at line 117 and the `PatronusStagModel` fallback at line 100. Identical constant, identical symptom.

### A.5 Was this the GeckoLib render-state rule?

**No.** `PatronusRenderer` is not a GeckoLib renderer — no `GeoRenderState`, no `DataTicket`, no `buildRenderTask`, no `GeoEntityRenderer`. It is a vanilla `EntityRenderer` using the 1.21.5+ `submit` / `SubmitNodeCollector` API. That suspect is cleared.

The render-state extraction discipline is in fact **clean here**: every field `submit()` reads (`ageInTicks`, `corporeal`, `formId`) comes from the render state. There is **no live entity access in the render path**. Stack rule satisfied.

*(Adjacent observation, not the reported defect, not fixed:* [PatronusRenderer.java:171-201](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L171-L201) *poses a **shared cached model instance** during `submit()`, but geometry is emitted later inside the deferred `submitCustomGeometry` lambda. With two Patronuses of different forms visible at once, the last-posed model wins for both. Single-Patronus-per-caster makes this rare; flagging only.)*

### A.6 Comparison — an entity in this mod that renders correctly

`PetrifyServerLogic` is the server-side comparison for §B; for rendering the informative contrast is **any vanilla-derived living renderer**, and the specific thing the Patronus lacks is the `LivingEntityRenderer` base:

| | Working living renderer | `PatronusRenderer` |
| --- | --- | --- |
| Base class | `LivingEntityRenderer` | `EntityRenderer` ([:42](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L42)) |
| Render state | `LivingEntityRenderState` (has `bodyRot`/`yRot`/`xRot`) | `EntityRenderState` (has none) |
| Rotation applied | `setupRotations(state, pose, state.bodyRot, …)` | Nothing — replaced by a clock |
| Entity sets yaw | yes, via AI/navigation | **never** |

A grep for `ageInTicks *` across the whole mod returns **only** these two Patronus lines. No other renderer in the codebase drives rotation off entity age. The Patronus is the sole outlier.

### A.7 Origin

`git log -S` shows the spin was introduced in **`6598a1b0` "feat: placeholder Patronus stag geometry (item 12 scaffolding)"** — i.e. it originated in explicitly-labelled *placeholder scaffolding*, where a slow turntable spin is a reasonable way to show off a new model. It was then copied verbatim onto the vanilla-form path in **`7d670ec9` "feat(spell): render the Patronus by form, and add mist + reveal"**. It is scaffolding that outlived its scaffold — not a regression, and consistent with the bisect finding that it predates `8ce80b13`.

### A.8 Proposed fix — ranked (not implemented)

**Option 1 — give the entity a facing, and render it (recommended).**
Server-side: in `PatronusEntity.tick()`, set yaw from the movement vector — toward the hunted dark mob in the hunt branch ([:130-145](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L130-L145)), and tangent to the orbit in the guard branch ([:146-161](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L146-L161)), keeping `yBodyRot`/`yHeadRot` in sync. Client-side: add a `bodyRot` field to `PatronusRenderState`, populate it in `extractRenderState` (lerping `yRotO`→`yRot` by `partialTick`), apply `Axis.YP.rotationDegrees(180.0f - bodyRot)` in place of the age term, and delete both `age * 1.5f` lines.
*Pro:* the guardian faces what it guards against — correct on both canon and feel; the orbit reads as a circling patrol instead of a spinning prop. *Con:* touches the entity as well as the renderer; needs the extract-side lerp done right or it will judder.

**Option 2 — delete the spin only.**
Remove lines 100 and 117 and nothing else. The Patronus then always faces world-north.
*Pro:* two-line change, zero blast radius, immediately stops the reported symptom. *Con:* a stag orbiting sideways while permanently facing north looks broken in a different way. Fine as a stopgap, not as the answer.

**Option 3 — retarget the spin at the caster.**
Keep a rotation but derive it from the orbit angle so the Patronus always faces outward from (or inward to) the caster.
*Pro:* no entity change; renderer-local. *Con:* the renderer would have to re-derive `orbitAngle`, which is server-only private state ([PatronusEntity.java:53](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L53)) — it would need syncing anyway, at which point Option 1 is cleaner and more general.

**Recommendation: Option 1**, with Option 2 acceptable if a fast stopgap is wanted before the design ruling in §A.10 lands.

### A.9 Blast radius

- `PatronusRenderer` — no other caller; only construction site is [ClientSetup.java:25](src/main/java/at/koopro/wizardsandbeasts/client/ClientSetup.java#L25).
- `PatronusStagModel` — **shared with the Animagus stag form.** Option 1 does not touch it; any fix that does would hit Animagus.
- `PatronusEntity` — read by [FleeFromPatronusGoal.java](src/main/java/at/koopro/wizardsandbeasts/entity/azkaban/goal/FleeFromPatronusGoal.java) and [PatronusDetection.java](src/main/java/at/koopro/wizardsandbeasts/spell/patronus/PatronusDetection.java). Both read position/presence, not rotation. Adding a yaw write is additive and cannot affect them.
- Option 1 adds one field to the render state — a `PatronusRenderState`-local change with no external consumer.

### A.10 Open questions for Christian

1. **What should the Patronus face?** Options: (a) its direction of travel, (b) the nearest dark creature whenever one is in range and travel direction otherwise, (c) always outward from the caster like a sentry. Canon supports (b) — the Patronus is a guardian that squares up to the threat — but this is a feel call. Option 1 above assumes (b).
2. **Keep any idle motion?** The vertical bob at [:101](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L101)/[:118](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L118) (`sin(age * 0.12) * 0.04`) is subtle and reads as a hover, not a defect. Confirm it stays.

---

## B — Imperio control layer

### B.1 Root cause

**Two independent causes, one per victim type. Neither is a networking or module-gating problem.**

**Cause B-1 — mob victims: the driver is never registered on the event bus.**

[ImperioServerLogic.java:96-119](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L96-L119) declares `@SubscribeEvent public static void onServerTick(ServerTickEvent.Post)` — the only thing that ever calls `executeMobCommand`. But the enclosing class carries **no** `@EventBusSubscriber`:

```java
// ImperioServerLogic.java:35 — no annotation on this line or above it
public final class ImperioServerLogic {
```

The only annotation in the file is on the **nested** `LogoutCleanup` class at [:271](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L271). NeoForge's `@EventBusSubscriber` registers exactly the annotated class — a nested-class annotation does **not** register the enclosing class's handlers. And `ImperioServerLogic` is never registered manually either: the sole `NeoForge.EVENT_BUS.register` call in the mod is [WizardsAndBeastsMod.java:171](src/main/java/at/koopro/wizardsandbeasts/WizardsAndBeastsMod.java#L171), for `AbilityFrameworkEvents`.

**`onServerTick` has never fired. `executeMobCommand` is dead code.**

**Cause B-2 — player victims: the command execution path does not exist.**

[ImperioServerLogic.java:110-112](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L110-L112) skips non-mobs with an explicit hand-off comment:

```java
if (!(victim instanceof Mob mob)) {
    continue; // player victims are handled by tickVictim
}
```

`tickVictim` ([:178-227](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L178-L227)) does **not** handle commands. It does exactly three things: the sneak-resistance roll, willpower/effect bookkeeping, and duration countdown. It never reads `st.imperioCommandOrdinal()` and never touches the victim's position, velocity, look, or input. The comment describes a hand-off that was never written.

A whole-repo grep for `IMPERIO_CONTROL_STATE` returns 15 hits: 13 inside `ImperioServerLogic`, one registration in `ModAttachments`, and one read at [SignatureSpellPlayerTickHandler.java:28](src/main/java/at/koopro/wizardsandbeasts/event/spell/SignatureSpellPlayerTickHandler.java#L28) — used only to gate willpower regen. **Nothing anywhere converts a stored command into player movement.**

### B.2 Confidence

**High for B-1.** The missing annotation is a single verifiable fact, corroborated three ways: the class declaration, the exhaustive `EVENT_BUS.register` grep, and the sibling comparison in §B.7.

**High for B-2.** "No code path exists" is established by an exhaustive grep of the only state key any such path could read.

Confidence is already sufficient to act. If further proof is wanted, a breakpoint or a log line in `onServerTick` on a live server would confirm B-1 in one cast — but nothing in the trace admits another reading.

### B.3 Evidence chain — full control chain, hop by hop

| # | Hop | Anchor | Status |
| --- | --- | --- | --- |
| 1 | Cast reaches the targeted handler | [SpellCastTargetedHandler.java:119-122](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastTargetedHandler.java#L119-L122) | ✅ works (entity hit) |
| 1b | …or the block-raycast fallback grabs the nearest mob | [SpellCastTargetedHandler.java:182-195](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastTargetedHandler.java#L182-L195) | ✅ works |
| 2 | `beginControl` writes control state | [ImperioServerLogic.java:53-55](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L53-L55) | ✅ works |
| 3 | Euphoria effect + `CASTER_TO_VICTIM` link + caster notified | [:56](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L56), [:69-70](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L69-L70) | ✅ works |
| 4 | Caster client stores bound victim | [SpellClientPayloadHandlers.java:97](src/main/java/at/koopro/wizardsandbeasts/client/spell/network/SpellClientPayloadHandlers.java#L97) → [ClientSignatureSpellState.java:44-49](src/main/java/at/koopro/wizardsandbeasts/client/spell/state/ClientSignatureSpellState.java#L44-L49) | ✅ works |
| 5 | Next wand release opens the command menu | [WandItem.java:126](src/main/java/at/koopro/wizardsandbeasts/item/wand/WandItem.java#L126) → [WandCastClient.java:38-53](src/main/java/at/koopro/wizardsandbeasts/client/wand/WandCastClient.java#L38-L53) | ✅ works |
| 6 | Button sends `ImperioCommandC2SPayload` | [ImperioCommandScreen.java:31](src/main/java/at/koopro/wizardsandbeasts/client/spell/gui/ImperioCommandScreen.java#L31) | ✅ works |
| 7 | Payload registered C2S | [ModNetworkSpells.java:114-116](src/main/java/at/koopro/wizardsandbeasts/network/spell/ModNetworkSpells.java#L114-L116) | ✅ works |
| 8 | Server handler enqueues on main thread | [ImperioCommandC2SPayload.java:30-34](src/main/java/at/koopro/wizardsandbeasts/network/spell/ImperioCommandC2SPayload.java#L30-L34) | ✅ works |
| 9 | `applyCommand` persists the ordinal onto the victim | [ImperioServerLogic.java:86-88](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L86-L88) | ✅ works |
| **10** | **Something reads the ordinal and drives the victim** | **mobs:** [:96-119](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L96-L119) never registered · **players:** no such code | ❌ **chain terminates here** |

**The chain is intact for nine hops and dies at the tenth.** The command is correctly stored on the victim and then read by nobody. This is exactly consistent with the reported symptom: cast fires, effect applies, control does nothing.

Distinguishing the two failure modes the prompt asks about: for **mobs** this is *never fires* (the handler is not on the bus at all), not *fires with no effect*. For **players** it is neither — there is no handler.

### B.4 State storage

`AttachmentType<ImperioControlState>` — a 6-field record ([ImperioControlState.java:12-18](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioControlState.java#L12-L18)) registered at [ModAttachments.java:154-155](src/main/java/at/koopro/wizardsandbeasts/registry/ModAttachments.java#L154-L155) with a `Codec` ([:23-30](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioControlState.java#L23-L30)), so it persists across save/load. Storage is sound and is **not** implicated.

Caster↔victim linkage is a separate in-memory `ConcurrentHashMap<UUID,UUID> CASTER_TO_VICTIM` ([:38](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L38)) — deliberately transient, cleaned on logout ([:260-264](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L260-L264)) and on control end ([:236](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L236)).

The caster's end **does** know the link exists — `ImperioVictimBoundS2CPayload` at [:70](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L70), cleared at [:239](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L239) and [:252](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L252). This is why the command GUI opens at all, and it is why the defect presents as "the menu works but nothing happens" rather than "nothing happens".

### B.5 Movement authority

Nothing applies control input to any victim's movement.

- `ImperioEuphoriaEffect` ([ImperioEuphoriaEffect.java](src/main/java/at/koopro/wizardsandbeasts/effect/ImperioEuphoriaEffect.java)) is **cosmetic only** — `sendParticles` and nothing else. It is not, and was never, the movement authority.
- `executeMobCommand` ([:121-159](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L121-L159)) *is* server-side and *would be* correct authority for mobs (`setTarget`, `getNavigation().moveTo`, item drop) — it is simply unreachable.
- For players there is no equivalent at all.

### B.6 Player vs mob victims

Imperio targets **both**. `beginControl` takes a `LivingEntity` ([:42](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L42)) and has explicit player branches ([:57-59](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L57-L59)) and mob branches ([:60-62](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L60-L62)).

**The defect differs between them, and the distinction is the single most useful thing here for scoping the fix:**

| | Mob victim | Player victim |
| --- | --- | --- |
| Control state applied | ✅ | ✅ |
| Euphoria effect + particles | ✅ | ✅ |
| Command stored from GUI | ✅ | ✅ |
| Resistance / sneak-to-throw-off | n/a | ✅ works ([:161-176](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L161-L176), [:186-215](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L186-L215)) |
| Duration countdown / auto-release | via `tickDown` on player tick only | ✅ ([:221-226](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L221-L226)) |
| **Command execution** | ❌ **code exists, never runs** (one-line fix) | ❌ **code does not exist** (feature work) |

Note a knock-on of B-1: a **mob** victim's `tickDown` also never runs, because `tickVictim` is only invoked from `PlayerTickEvent` ([SignatureSpellPlayerTickHandler.java:26](src/main/java/at/koopro/wizardsandbeasts/event/spell/SignatureSpellPlayerTickHandler.java#L26)). A controlled mob's `remainingTicks` therefore never decrements; the control state clears only because the Euphoria `MobEffectInstance` expires on its own timer, leaving a stale `isControlled=true` attachment and a stale `CASTER_TO_VICTIM` entry on the mob indefinitely. Registering the bus does not by itself fix this — the tick handler would also need to call `tickDown` for mobs.

### B.7 Comparison — the mod's other behaviour-override systems

**`PetrifyServerLogic` is the near-exact sibling and it works.** Same package convention, same `ServerTickEvent.Post` pattern, same attachment-backed state:

```java
// PetrifyServerLogic.java:32-33
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PetrifyServerLogic {
```

```java
// ImperioServerLogic.java:35  ← the annotation is simply absent
public final class ImperioServerLogic {
```

That one line is the entire structural difference between the working system and the broken one.

`PetrifyServerLogic.onServerTick` (`:100-119`) is also the direct template for the missing player path in B-2 — it enforces state on a `ServerPlayer` **server-side every tick** (`teleportTo`, `setYRot`, `setXRot`, `setDeltaMovement(Vec3.ZERO)`, `stopUsingItem()`). Whatever Imperio does to player victims should follow that shape.

Every other `ServerTickEvent` user in the mod carries the class-level annotation — `TransitionManager` (`:24-25`), `CauldronBrewing`, `BloodPactEnforcer`, `CloakEffectsHandler`, `ObscurialHeritageHandler`, `ExpectoPatronumAuraHandler`, `ProtegoShieldHandler`, `SpellCombatControlHandler`, `MaraudersMapTracker`, `MirrorSessionManager`, `DeluminatorReturnService`, `ShareEntranceService`. **`ImperioServerLogic` is the only one that does not.**

### B.8 Packet registration — all clear

Every Imperio payload is registered at [ModNetworkSpells.java:97-120](src/main/java/at/koopro/wizardsandbeasts/network/spell/ModNetworkSpells.java#L97-L120) with matching direction:

| Payload | Direction | Registered | `StreamCodec` |
| --- | --- | --- | --- |
| `ImperioControlS2CPayload` | S2C | `:97-99` | `BOOL` + `UUIDUtil` — round-trips |
| `ImperioResistS2CPayload` | S2C | `:101-103` | ✅ |
| `ImperioVictimBoundS2CPayload` | S2C | `:105-107` | `UUIDUtil` — round-trips |
| `ImperioCommandC2SPayload` | C2S | `:114-116` | `VAR_INT` — round-trips |
| `ImperioResistC2SPayload` | C2S | `:118-120` | ✅ |

Handlers run on the intended side: `ImperioCommandC2SPayload.handle` guards `ctx.player() instanceof ServerPlayer` and wraps in `ctx.enqueueWork` ([:30-34](src/main/java/at/koopro/wizardsandbeasts/network/spell/ImperioCommandC2SPayload.java#L30-L34)). Ordinal decoding is bounds-safe ([ImperioCommand.java:11-17](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioCommand.java#L11-L17)). **Networking is not implicated.**

### B.9 Origin

Commit **`637c95d7` "feat(spells): Imperio-controlled mobs now execute their puppet commands"** (2026-07-24) added `onServerTick` + `executeMobCommand` as `1 file changed, 70 insertions(+)`. Diffing that commit for annotation lines yields only `+    @SubscribeEvent` — **the class-level `@EventBusSubscriber` was never added.** The commit message describes precisely the behaviour that has never once executed.

The earlier commit `07d33cc7` had introduced the `EventBusSubscriber` import and applied the annotation to the nested `LogoutCleanup` class only. So the file has *looked* wired since then — the import is present and an annotation is visible on screen — which is very likely why the omission survived review.

### B.10 Proposed fix — ranked (not implemented)

**Mob victims (B-1) — one option, no real alternative:**
Add `@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)` to the `ImperioServerLogic` class declaration at [:35](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L35). The import is already present at [:26](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L26). The nested `LogoutCleanup` annotation can stay — the two register independently and `onLogout` will not double-fire.
While in there, `tickDown` should also be driven for mob victims (see the stale-state knock-on in §B.6); the natural home is the same `onServerTick` loop.

**Player victims (B-2) — three options:**

1. **Server-authoritative movement enforcement, `PetrifyServerLogic`-style (recommended).** In `tickVictim`, branch on `st.imperioCommandOrdinal()` and enforce the command every tick: `FOLLOW_CASTER` sets `setDeltaMovement` toward the controller, `STAND_STILL` zeroes movement and calls `stopUsingItem`, `DROP_HELD_ITEM` calls `drop(…)`, `RETREAT` moves away, `ATTACK_NEAREST` forces look-at plus a swing. Directly mirrors the proven petrify pattern; needs no new packet and no mixin.
   *Con:* server-forced movement fights client prediction — expect rubber-banding unless deltas are modest. Petrify sidesteps this by pinning to a fixed point; Imperio needs continuous motion, which is harder.
2. **Client-side input override on the victim.** Send the active command to the victim client via the existing `ImperioControlS2CPayload` (extend it with the ordinal) and have the victim's client synthesise movement input, so prediction stays coherent.
   *Con:* an unmodded/hostile client can ignore it. Acceptable only if paired with server-side clamping, at which point it is Option 1 plus extra moving parts.
3. **Do not control players; make Imperio a mob-only curse and repurpose the player case.** Fix B-1 only and reframe player Imperio as the disorientation/euphoria + resistance minigame that already works today, with the command GUI suppressed for player victims.
   *Con:* diverges from canon, where the Imperius Curse is defined by control over *people*.

**Recommendation:** ship B-1 immediately (one line, unblocks the whole mob path, low risk), then take B-2 Option 1 as separate scoped work — and treat B-2 as a **feature**, not a bug fix. The prompt's framing ("the control does not work") is accurate for mobs but understates players, where it was never built.

**Secondary risk to expect after B-1 lands, worth naming before the fix cycle:** `executeMobCommand` runs every 5 ticks ([:101-103](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L101-L103)) and steers the mob via `setTarget` / `getNavigation().moveTo`. The victim's own vanilla AI goals run **every** tick and will contend — a zombie's `NearestAttackableTargetGoal` will re-acquire a target between Imperio passes, and stroll goals will re-path over `moveTo`. B-1 will make control *start working*, but it may look intermittent rather than absolute. If it does, the follow-up is goal suppression on the controlled mob (remove/disable conflicting goals for the duration) rather than a shorter tick interval. **This is a prediction from static reading, not an observed behaviour — flagging so it is not mistaken for a fresh regression.**

### B.11 Blast radius

- Adding the annotation **activates ~40 lines of code that have never executed on any world**. Everything downstream of `executeMobCommand` is effectively untested at runtime: `ATTACK_NEAREST` will make controlled mobs attack anything nearby except the controller ([:126](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L126) excludes only `controller` and `mob` itself — **passive animals, villagers and other players are all valid targets**). This is presumably intentional per the commit message ("its own former allies included"), but it goes live the moment the annotation lands. Worth a deliberate confirmation rather than a surprise.
- `DROP_HELD_ITEM` ([:148-157](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L148-L157)) permanently strips a mob's mainhand into a world `ItemEntity`. Also never executed before. Griefing/dupe surface worth one look before it is enabled.
- `ImperioServerLogic` is called from four sites — [SpellCastTargetedHandler.java:121](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastTargetedHandler.java#L121) and [:191](src/main/java/at/koopro/wizardsandbeasts/spell/cast/SpellCastTargetedHandler.java#L191), [SignatureSpellPlayerTickHandler.java:26](src/main/java/at/koopro/wizardsandbeasts/event/spell/SignatureSpellPlayerTickHandler.java#L26), and the two C2S payload handlers. None are affected by the annotation itself.
- B-2 Option 1 would edit `tickVictim`, which also owns the resistance roll and duration countdown — both currently working. Regression risk is real; those two behaviours need explicit re-verification after any edit there.

### B.12 Open questions for Christian

1. **Should Imperio control other players at all?** This is a design/canon ruling and it decides whether B-2 is built (Option 1) or dropped (Option 3). Canon says yes; forcing another player's movement in multiplayer is a heavier call than the code implies.
2. **Is `ATTACK_NEAREST` targeting passive mobs, villagers and bystanding players intended?** The code says yes; it has just never actually happened in a world. Confirm before B-1 makes it live.
3. **Should controlled mobs have their own AI goals suppressed for the duration** (see §B.10 secondary risk), or is contested/intermittent control the intended feel?

---

## C — Shared cause, console output, migration suspects

### C.1 Is there one upstream cause behind both?

**No. Plainly: no.** These are two unrelated defects that happen to have been noticed in the same play session.

The only thing `ExpectoPatronum` and `Imperio` share is `extends Spell` ([ExpectoPatronum.java:22](src/main/java/at/koopro/wizardsandbeasts/spell/impl/ExpectoPatronum.java#L22), [Imperio.java:9](src/main/java/at/koopro/wizardsandbeasts/spell/impl/Imperio.java#L9)) — the base every spell in the mod uses, including `AvadaKedavra`, which the prompt confirms works. There is no bespoke-spell base class, no shared entity or effect superclass, and no shared registration path between them. `Spell` is not implicated in either defect.

They sit in different layers entirely: **A is client render, B is server event-bus registration.** They fail for different reasons, at different times, on different sides of the wire. Any theory unifying them would be manufactured, so I am not offering one.

The one honest common thread is process, not code: **both are features whose implementation commit was never verified in a live client.** A's spin came from scaffolding (`6598a1b0`) that was never revisited; B's driver came from a feature commit (`637c95d7`) whose headline behaviour has never run. Neither would survive one minute of in-game testing. That is a testing-coverage observation, not a shared root cause.

### C.2 Console output

No exception or warning is reachable on either path, which is itself the diagnostic point:

- **A** produces no log output. A wrong rotation is silently valid — the geometry submits and draws successfully.
- **B** produces no log output. An unregistered `@SubscribeEvent` method is not an error condition in NeoForge; nothing scans for orphaned handlers, and the annotation processor has nothing to complain about. The code compiles, loads, and sits inert. **This silence is why the defect survived from 2026-07-24 to now.**
- No silent `catch` blocks on either path. Both fail by omission, not by swallowed exception.
- `./gradlew build` is green (exit 0) with no warnings touching either file. Neither defect is detectable from the build.

### C.3 Version-migration suspects

**A — yes, migration-shaped.** `PatronusRenderer` extends `EntityRenderer` while its entity is a `Mob` ([PatronusEntity.java:31](src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java#L31)). In the 1.21.5+ render-state migration, rotation handling moved into `LivingEntityRenderer.setupRotations` consuming `LivingEntityRenderState.bodyRot`. Extending the non-living base is *mechanically* legal and compiles clean, but it **silently drops all rotation handling** — a default that effectively flipped. This is exactly the "completed mechanically, not semantically" shape the brief asks about. The age-driven spin then papered over the absence.

A second, milder instance: `submitCustomGeometry` is deferred-execution by design in the new pipeline, but the model-posing at [:171-201](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L171-L201) is written as though rendering were immediate (see §A.5). That is a 4→5-era assumption carried into a 5-era API.

**B — no.** Nothing migration-shaped. `@EventBusSubscriber` / `@SubscribeEvent` / `ServerTickEvent.Post` are all current-idiom and correct; the annotation was simply never typed. Git history confirms it was absent from the moment the handler was written, not lost in a version bump.

**GeckoLib 4→5 — not implicated in either.** Neither defect path touches GeckoLib. `PatronusRenderer` is a vanilla renderer; Imperio has no renderer.

### C.4 Scope note (stop-and-report)

Per the brief's stop condition on causes lying outside the two spells: **neither cause is outside its own spell's code.** A is contained in `PatronusRenderer` (+ optionally `PatronusEntity`). B is contained in `ImperioServerLogic`. Neither the casting pipeline, the entity framework, nor any shared base class needs to change. No canon or design ruling is required to fix **A option 2** or **B-1**; rulings are required for **A option 1** (facing behaviour) and **B-2** (player control), listed in §A.10 and §B.12.

### C.5 Investigation additions (read-only, as licensed)

The trace reached two adjacent systems. Both were read only, nothing was changed:

- **`PetrifyServerLogic`** — read as the working comparison for §B.7 and as the template for §B.10 Option 1. Healthy; no defect found.
- **Decompiled vanilla 1.21.11 sources** (`EntityRenderer`, `LivingEntityRenderer`, `EntityRenderState`, `LivingEntityRenderState`), extracted read-only from the neoformruntime jar into the session scratchpad to confirm `ageInTicks` semantics rather than assume them.

---

## Summary

| | Defect A — Patronus spin | Defect B — Imperio control |
| --- | --- | --- |
| **Layer** | Client render | Server event bus + missing feature |
| **Root cause** | `Axis.YP.rotationDegrees(age * 1.5f)` at [PatronusRenderer.java:100](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L100) & [:117](src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java#L117); entity yaw never set and unreachable from a non-`LivingEntityRenderer` base | **B-1:** no `@EventBusSubscriber` on [ImperioServerLogic.java:35](src/main/java/at/koopro/wizardsandbeasts/spell/imperio/ImperioServerLogic.java#L35) → `onServerTick` never fires. **B-2:** no player command-execution code exists |
| **Entity or render?** | **Render.** Entity is fine | Server-side; not a render issue |
| **Confidence** | High | High |
| **Fix size** | 2 lines (stopgap) / ~20 lines (proper) | **1 line** for mobs; feature work for players |
| **Needs a ruling?** | Only for the proper fix (facing behaviour) | Yes, for player control |
