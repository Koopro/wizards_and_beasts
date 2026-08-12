# PLAYER_POSE_LAYER_SCHEMA.md

Schema for the Wizards & Beasts player pose system. Wave 1 target: a
priority-stacked pose layer carrying **two pass types** — procedural and
keyframe — plus a creative-flight test harness driven by a command.

Status: **schema rev 4, corrected against Phase 1 implementation findings.**

Rev 4 changes: the layer **captures** rather than resets (§3.1) — resetting to
the initial pose at `TAIL` of `setupAnim` would destroy vanilla's own animation;
`RESET` redefined to blend toward the captured vanilla value (§3.3); overlay
copy removed as structurally unnecessary (§3.4); `BODY` application split from
`BODY` computation (§3.9).
Decisions D1–D6 resolved, D4 revised (see §11). All rulings R-1…R-5 resolved.

Rev 3 changes, all from the Phase 0 audit: `PlayerRenderState` corrected to
`AvatarRenderState`; partial-tick source ruled (§3.6); the render hook identified
as an **existing** delegate chain rather than a new one (§3.7); first-person path
moved to NeoForge events, no mixin (§3.8); R-5 resolved to *evaluator not
reachable* with a minimal-reader path and a proof gate (§5.5).

Rev 2 changes: keyframe passes added as a first-class pass type sourced from
GeckoLib-format `.animation.json`; R-1 split by pass type; authoring rig and
binding format added as deliverables.

---

## 1. Purpose and scope

The mod needs to pose the vanilla player model from live gameplay state — broom
flight attitude, spell cast phases, Apparition wind-up, Animagus transition.

Two different things are being asked for, and they need different mechanisms:

- **Continuous, input-driven attitude.** Flight body-pitch is a function of head
  pitch, movement direction and propulsion. Keyframes cannot express this.
  → **procedural passes, authored in Java.**
- **Discrete, authored performance.** A wand flick, an Apparition wind-up, a
  broom mount. These are choreography, not math, and should be retunable without
  a rebuild. → **keyframe passes, authored in Blockbench, shipped in a resource
  pack.**

Both are `PosePass` implementations feeding one blend stack. Wave 1 delivers the
layer, both pass types, and exactly one consumer of each: flight (procedural,
driven by creative flight through a command) and one throwaway keyframe clip to
prove the sampling path end to end. The broom entity is not touched.

Prior art: ThreeTAG's Palladium uses a priority-stacked procedural system of this
shape. The architecture is the reference; **no Palladium code or pose values are
copied.**

Licence note (corrected): Palladium is **GPL-3.0**, not All Rights Reserved as
earlier revisions of this document stated. GPL-3.0 is copyleft and requires
derivative works to be GPL-3.0; Wizards & Beasts is CC BY-NC-SA 4.0, which is not
GPL-compatible — the NC clause alone conflicts. Copying Palladium source or the
pose constants embedded in it is therefore a licence violation, not merely
impolite. Architectural ideas are not copyrightable and remain fair reference.
All pose numbers in this mod are authored from scratch.

---

## 2. Hard stack rules

- Minecraft 1.21.11 / NeoForge 21.11.x, Java 21, official Mojang mappings,
  GeckoLib 5.4.5.
- `Identifier` everywhere; `Identifier.fromNamespaceAndPath()`; never
  `ResourceLocation`, never `Identifier.tryParse()`.
- `NeoForge.EVENT_BUS` only. All registration via `DeferredRegister`.
- Persistent/synced player state via `AttachmentType` + `Codec`.
- Networking via `CustomPacketPayload` + `StreamCodec` +
  `RegisterPayloadHandlersEvent`.
- JSpecify nullability on all public signatures.
- Mixin bodies contain **no logic** — pure delegation to a handler class. Every
  new mixin requires a written justification naming the NeoForge event evaluated
  and why it was insufficient. `defaultRequire: 1`.
- Gate, never delete. Access via `ModuleManager`; registration is unconditional.
- **No new animation library dependency.** GeckoLib 5.4.5 is already shipped and
  is the only animation dependency permitted.

---

## 3. Architecture

### 3.1 `PlayerPoseLayer`

Client-side singleton. Holds an ordered list of `PosePass` instances sorted by
integer priority (ascending; later passes blend on top of earlier ones).

Responsibilities:

1. **Capture** every model part's incoming transform at layer entry. The layer
   runs at `TAIL` of `PlayerModel.setupAnim`, so the incoming value *is* vanilla's
   authored output for this frame — the walk cycle, the swing, the crouch.
   **The layer never resets to the initial pose.** Doing so would discard
   vanilla's animation wholesale every frame the layer runs.
2. Run each pass in priority order, collecting per-part op lists.
3. Apply the collected ops to the target — either a `ModelPart` (third person and
   whole-model) or a `PoseStackResult` (first person, and the virtual `BODY`
   part).

The layer does not know or care which passes are procedural and which are
keyframe. Both emit the same op lists.

### 3.2 `PosePass`

```
PosePass {
    int priority();
    void pose(PoseBuilder builder, PoseContext context);
}
```

`PoseContext` carries: the client player, the humanoid model, a
`FirstPersonContext`, `partialTicks`, and the render-state-derived motion values
(§3.6). A pass that has nothing to contribute returns without touching the
builder — an empty op list costs nothing.

Two implementations in wave 1:

- `ProceduralPosePass` — abstract base; subclasses compute values in Java.
- `KeyframePosePass` — samples a GeckoLib-format animation at a time derived from
  a `PhaseTimer` and emits the resulting bone transforms as ops (§5).

### 3.3 `PoseBuilder` and `PartPoseData`

`PoseBuilder.get(PlayerModelPart)` returns a `PartPoseData`, lazily created.

`PartPoseData` is **a queue of operations, not direct mutation**. Each operation
is a `(target, type, value)` triple:

**Targets**

| Group | Targets | Notes |
|---|---|---|
| Translate A | `X`, `Y`, `Z` | applied before rotation |
| Translate B | `X2`, `Y2`, `Z2` | applied *after* rotation — pivot offsetting |
| Rotate | `X_ROT`, `Y_ROT`, `Z_ROT` | radians internally, degree helpers exposed |
| Scale | `X_SCALE`, `Y_SCALE`, `Z_SCALE` | initial value 1.0 |

`X2`/`Y2`/`Z2` exist only on the `PoseStackResult` backend. Requesting them on a
`ModelPart` target is a no-op and must log once at debug level.

**Operation types**

| Type | Semantics |
|---|---|
| `SET` | lerp current → value. Raw; wraps the long way on angles. |
| `SET_SHORTEST` | lerp current → value with radian wrapping. Use for anything derived from entity yaw/pitch. |
| `ADD` | additive offset. On scale targets, multiplicative around 1.0. |
| `RESET` | lerp current → **the vanilla value captured at layer entry** (§3.1). This is "hand this limb back to vanilla mid-stride", which is what a blend-out needs. `ModelPart.getInitialPose()` is the authored rest pose — near a T-pose — and is **not** used. A genuine rest-pose target, if ever needed, gets its own op type. |

**Blend weight.** Each `PartPoseData` carries one scalar `multiplier`, default
1.0. Every operation resolves as `current + (target − current) × multiplier`.
`animate(Easing, progress)` is sugar for `multiplier(easing.apply(progress))`.

This is the entire blending model. There are no channels, no timelines, no
controllers. Composition happens because passes run in priority order and each
weighted-lerps the accumulated state toward its own pose. A keyframe pass fading
out is the same operation as a procedural pass fading out.

### 3.4 `PlayerModelPart`

`HEAD`, `CHEST`, `RIGHT_ARM`, `LEFT_ARM`, `RIGHT_LEG`, `LEFT_LEG`, `BODY`.

`BODY` is **virtual** — it has no backing `ModelPart`. It resolves against the
`PoseStack` during the renderer's rotation setup, which is how whole-body pitch
(lying flat on a broom) is achieved without rotating each limb.

Overlay parts (`hat`, `jacket`, sleeves, pants) are never posed directly. In
1.21.11 they are **children** of their base part — `leftArm.getChild("left_sleeve")`,
`body.getChild("jacket")` — so they inherit the transform through the hierarchy
and no copy step is required. The earlier sibling-copy requirement described a
model that does not exist in this version. If a future version reverts to
siblings, the copy is reinstated here.

### 3.5 `FirstPersonContext`

`NONE`, `RIGHT_ARM`, `LEFT_ARM`.

In wave 1 this is a **required** branch, not an optional one: passes author
separate values for the first-person case. A third-person shoulder rotation
produces a wrong-looking held wand in first person, so a pass that ignores the
context is a defect, not a gap.

For keyframe passes this means either a separate first-person clip or an
explicit declared fallback in the binding (§5.4) — never silent reuse of the
third-person clip.

Convenience predicates: `firstPerson()`, `mainArm()`, `offArm()`.

### 3.6 Motion inputs

**There is no `PlayerRenderState` in 1.21.11.** The type is `AvatarRenderState`,
inheriting `HumanoidRenderState` → `ArmedEntityRenderState` →
`LivingEntityRenderState` → `EntityRenderState`. Members are **public fields**,
not accessors.

| Need | Field | Declared on |
|---|---|---|
| walk animation position / speed | `walkAnimationPos`, `walkAnimationSpeed` | `LivingEntityRenderState` |
| body yaw / head yaw / pitch | `bodyRot`, `yRot`, `xRot` | `LivingEntityRenderState` |
| time value | `ageInTicks` | `EntityRenderState` |
| crouch / swim / passenger | `isCrouching`, `swimAmount`, `isPassenger` | `HumanoidRenderState` |
| attack arm, item use | `attackArm`, `ticksUsingItem`, `isUsingItem` | `HumanoidRenderState` |
| elytra flight | `isFallFlying`, `elytraRotX/Y/Z` | `HumanoidRenderState` |
| avatar flight | `fallFlyingTimeInTicks`, `fallFlyingScale()`, `flyingYRot`, `shouldApplyFlyingYRot` | `AvatarRenderState` |

**Partial tick — ruling.** The render state carries no partial tick; `ageInTicks`
is a time value, not a frame fraction. `PhaseTimer` interpolation needs a true
frame fraction, so the layer sources it once per frame from the client delta
tracker and passes it down through `PoseContext`. It is **not** re-derived inside
individual passes, and `ageInTicks` is **not** substituted for it. A pass wanting
a continuous oscillator uses `ageInTicks`; a pass interpolating a `PhaseTimer`
uses the frame fraction. These are different quantities and the schema does not
conflate them.

**Do not** introduce global mutable state (a `SKIP_ANIMATIONS`-style flag, static
`CACHED_*` motion values). If a first-person pass needs to suppress a
third-person pass, that is expressed through `FirstPersonContext` inside the pass.

### 3.7 The render hook already exists

`mixin/client/PlayerModelMixin` injects at `@At("TAIL")` of
`PlayerModel.setupAnim(AvatarRenderState)`. Its body is pure delegation, its
javadoc already carries the NeoForge-event justification (`RenderLivingEvent.Pre`
fires before `setupAnim` and is overwritten; `RegisterRenderStateModifiersEvent`
never sees `ModelPart`s), and `defaultRequire: 1` is set.

**The pose layer becomes a fourth delegate on this hook. No second injection, no
new mixin.**

Three handlers already chain there in a documented order:
`BroomRiderPoseHandler` → `PetrifyRenderHandler` → `ModelDebugPartTransforms`.

**Coexistence ruling.** Wave 1 does **not** migrate these three into passes. The
layer runs as a fourth delegate, after them. Their effective ordering is recorded
in the band table (§10 R-2) as a legacy row so the bands do not lie about what is
actually posing the model, and a `AUDIT_PUNCHLIST.md` entry at POLISH tracks
migrating them. Migrating them inside wave 1 is scope creep and is rejected.

### 3.8 First person

`ItemInHandRenderer.renderPlayerArm` is private, but NeoForge covers the path:
`RenderArmEvent` (per-arm, cancellable) and `RenderHandEvent`, both carrying the
`PoseStack`. **First-person posing uses these events. No mixin.**

### 3.9 `BODY` — computation is split from application

`PlayerModel.setupAnim` has no `PoseStack` in scope, and `setupRotations` runs
**before** `setupAnim` in the render path — so a body transform computed in the
layer's delegate is already a frame late for the site that would apply it.

Ruling:

- **Pose computation must not depend on where the result is applied.** The
  computed body transform rides on the render state: one value, one owner.
- **No cross-hook handler field**, and specifically no cleared-on-read accessor.
  That is the global mutable state §3.6 prohibits, and it silently couples two
  hooks whose ordering is not guaranteed.
- The **application site** is audited and justified like any other hook. A second
  mixin with a written justification is acceptable. A `PoseStack` push in one
  event and a pop in another is not — it breaks under cancellation and under any
  exception between the two.

Until an application site exists, whole-body pitch does not render. Leaving it
visibly incomplete is correct; faking it is not.

---

## 4. `PhaseTimer`

The shared driver for every time-varying pose input, procedural or keyframe.

An int that counts up toward `maxValue` while its condition holds and back down
toward `startValue` when it releases, plus a `prevValue` retained for partial-tick
interpolation.

```
float value(partialTicks)  →  lerp(prev, current, partialTicks) / max      // 0..1
float raw(partialTicks)    →  lerp(prev, current, partialTicks)            // ticks
```

Plus a sub-range helper:

```
float between(float progress, float start, float end)
```

which remaps a raw timer value onto 0..1 across an arbitrary window, so one timer
can sequence several phases (wind-up 0–8, hold 8–20, release 20–26) without
allocating three timers.

`PhaseTimer` state is server-authoritative and synced. For a keyframe pass it
supplies both the playhead (`raw`) and the fade weight (`value`), so a clip's
progress is server-owned and identical on every client watching.

---

## 5. Keyframe passes

### 5.1 Why the format and not the renderer

GeckoLib animates a `GeoBone` hierarchy through a `GeoRenderer`. Animating the
player *that* way means replacing `PlayerRenderer` with a Geo renderer, which
forfeits vanilla skin handling, armour, elytra, capes and every other mod's
render layers. That is not acceptable for a mod whose players wear robes.

What is reusable is the **format**: a `.animation.json` is bone name → keyframes
of rotation / position / scale with easings. Sample it at time `t`, map bone
names to `PlayerModelPart`, emit ops. The vanilla player model keeps rendering
normally.

### 5.2 Authoring rig

`player.geo.json` — an authoring-only rig whose bones mirror the vanilla player
model's names, pivots and dimensions exactly.

- **Never rendered.** It exists so Blockbench exports transforms that map 1:1
  onto vanilla parts.
- Same role the master `wand.geo.json` plays for the wand system.
- Bone names are the canonical mapping keys: `head`, `body`, `right_arm`,
  `left_arm`, `right_leg`, `left_leg`, plus a root bone for whole-body motion
  that maps to the virtual `BODY` part.
- Pivot conventions must match vanilla exactly or every exported rotation is
  wrong. The agent verifies pivots against the vanilla model in Phase 0 and
  reports the table it derived.

### 5.3 Asset location

Animation clips are **client visuals**, so they ship in `assets/`, not `data/` —
a resource pack, not a datapack. This is consistent with the mod's
MobEffect-is-state / entity-is-visual-front-end rule: the server owns *when*, the
client owns *what it looks like*.

Path convention is derived from the existing GeckoLib asset layout the agent
audits; it is not prescribed here.

### 5.4 Binding format

A client-side JSON registry mapping a trigger to a clip. Per entry:

- animation `Identifier`
- priority (must fall in a band, §10 R-2)
- blend-in and blend-out durations
- an explicit first-person clip id, or a declared fallback behaviour
- loop / hold-last-frame behaviour

Reload-listener driven, mirroring the existing `BestiaryEntry` pattern.

### 5.5 Sampler — RESOLVED: evaluator not reachable

Audited classes: `AnimationProcessor`, `AnimationController`, `Animation`,
`BoneAnimation`, `KeyframeStack`, `Keyframe`, `MathValue`, `EasingType`,
`ControllerState`, `BakedAnimationCache`.

**Reachable — the data and the loader.** All records, all public, no renderer
types:

```
Animation(name, length, loopType, BoneAnimation[])
  → BoneAnimation(boneName, rotationKeyFrames, positionKeyFrames, scaleKeyFrames)
    → KeyframeStack(xKeyframes, yKeyframes, zKeyframes)
      → Keyframe(startTime, length, startValue, endValue, EasingType)
```

`BakedAnimationCache.getAnimation(Identifier, Identifier[], String)` returns an
`Animation` with no `GeoModel` and no `GeoAnimatable`.

**Not reachable — the evaluation.** Everything turning a keyframe into a number
wants a `ControllerState`, whose constructor requires a `GeoRenderState`:
`MathValue.get(ControllerState)`, `EasingType.apply(EasingState, ControllerState)`,
`AnimationProcessor.findAnimationPointValue(...)`. `AnimationProcessor` also wants
a `GeoAnimatable` and a `GeoModel`.

**Ruling: minimal reader.** Load through `BakedAnimationCache`, walk the records,
find the bracketing keyframe pair by `startTime`/`length`, interpolate.
`EasingType.easeIn` / `easeOut` / `easeInOut` are static, take no
`ControllerState`, and return `Double2DoubleFunction` — easing is reused, not
reimplemented.

**Proof gate — required deliverable.** `MathValue.get` still demands a
`ControllerState` even for a literal. A constant node very likely ignores the
argument, but that is an implementation-detail bet. Before any reader code is
built on it, ship an FML unit test proving the behaviour of a literal
`MathValue` under the argument the reader will actually pass. If the test does
not pass cleanly, the reader extracts the literal from the record directly and
never calls `MathValue.get`. **Do not build on the assumption without the test.**

### 5.6 Wave 1 keyframe scope

One throwaway clip, sufficient to prove: authoring rig → Blockbench export →
binding → sampler → ops → blended pose, in both person modes. Content clips
(wand flick, Apparition, Animagus, broom mount) are all follow-up work.

---

## 6. `PoseOverride` state (D1)

Server-authoritative, synced to all tracking clients — not client-local.
Third-person verification and multiplayer verification both require that other
players see the pose.

- `AttachmentType<PoseOverride>` with a `Codec`, attached to the player.
- Fields: an active `FlightPoseState` (nullable — absent means no override) and a
  `manual` boolean distinguishing a command-forced state from a derived one.
- Sync on change, on `PlayerEvent.StartTracking`, and on respawn/dimension change.
- One `CustomPacketPayload` with a `StreamCodec`, S→C only. No C→S packet: the
  command is the only writer and it runs server-side.

This attachment is the field the broom system will later write. The harness is
not throwaway scaffolding.

---

## 7. `FlightPosePass` (procedural)

Priority: locomotion band, see §10 R-2.

### 7.1 States

| State | Meaning |
|---|---|
| `HOVER` | airborne, negligible horizontal input |
| `GLIDE` | steady directional movement, body tilted into travel |
| `PROPELLED` | high-speed forward attitude, body near-horizontal |

Roll / barrel-roll states are **out of scope for wave 1**.

Transitions blend: the pass holds a from-state, a to-state and a blend progress,
and poses the weighted mix. Blend duration is a constant in wave 1, tunable
later.

### 7.2 Inputs

Derived per frame from the render state and the player:

- pitch and yaw, and the yaw delta across the last tick (for lean-into-turn)
- forward and sideways movement fraction, −1..1
- a `propulsion` 0..1 from a `PhaseTimer` gated on the sprint key while flying
- a `fade` 0..1 from a `PhaseTimer` gated on "override active", so the pose eases
  in and out rather than snapping

### 7.3 Pose values

**Not specified here.** Every rotation and offset is authored against the vanilla
player model and exported into the pass as named constants. The agent does **not**
invent pose numbers; if the values are not yet authored when the prompt runs, the
agent stops and reports.

Each state needs a value set for: `BODY` (3 rotations, plus Y and Y2 offset for
the lie-flat pivot), `HEAD`, both arms and both legs (3 rotations each), in both
third-person and first-person variants.

### 7.4 Attack interaction

While the player is swinging, the swing must remain readable. The pass applies
the swinging arm's ops at a reduced multiplier rather than suppressing the flight
pose outright. Exact factor is a tuning constant.

---

## 8. Command (D2)

```
/wizardsandbeasts pose flight <off|auto|hover|glide|propelled> [player]
```

Server-side, registered through the existing dispatcher pattern using the shared
`WizardsAndBeastsCommandPermissions.ADMIN` predicate — **not** a raw
`hasPermission(2)` call.

- `off` — clears the override.
- `auto` — derives the state each tick from creative-flight movement, exercising
  the real state machine.
- `hover` / `glide` / `propelled` — forces one state and holds it, for
  deterministic screenshots and blend inspection.
- `[player]` optional target, defaulting to the sender, so a second player can be
  posed for third-person verification without switching clients.

The override only takes effect while the player is actually flying
(`abilities.flying`). Forcing a state on a grounded player is accepted, stored,
and simply does not render — no error, no warning.

---

## 9. Module gating (D5)

New module: **`Module.PLAYER_ANIMATION`**, initial state `PREVIEW`.

- `DISABLED` and `COMING_SOON` suppress the pose layer entirely — passes stay
  registered, the layer runs zero passes, and the command reports the module is
  disabled rather than failing.
- `PREVIEW` counts as enabled: the pose renders.
- Registration is never conditional. Nothing in a world is destroyed when the
  module is disabled; `PoseOverride` attachments persist and simply do not render.

---

## 10. Rulings (resolved)

**R-1 — Where each pass type lives. RESOLVED.** Split by pass type:

- *Procedural passes are Java.* A flight pose is a function of four live inputs,
  not a table of values. No JSON format can express `headPitch + 80° × propulsion`
  without becoming an expression language.
- *Keyframe passes are resource-driven.* Clips in `assets/`, bindings in a
  reload-listener registry, retunable without a rebuild.

Static input-independent poses (duelling stance, bow, wand-at-ready) are
authored as **one-frame keyframe clips**, not a separate `PoseDefinition` format.
No third mechanism exists.

**R-2 — Priority bands. RESOLVED.** Load-bearing, because keyframe and procedural
passes stack against each other and compete for the same parts.

| Band | Use |
|---|---|
| 0–99 | posture / idle |
| 100–199 | locomotion — flight, broom attitude |
| 200–299 | casting — spell phases, wand flicks |
| 300+ | transforms — Animagus, Metamorphmagus, Obscurus |
| *legacy* | `BroomRiderPoseHandler` → `PetrifyRenderHandler` → `ModelDebugPartTransforms`, running as delegates ahead of the layer (§3.7) |

A pass declaring a priority outside every band is a hard error at registration,
not a warning. Bands are not subdivided further without a ruling. The legacy row
is descriptive, not a band — it exists so the table does not misrepresent what is
actually posing the model.

**R-3 — Module name. RESOLVED.** `Module.PLAYER_ANIMATION`. Half the system is
genuinely keyframe animation, and it is the name a player scanning a module list
will expect.

**R-4 — Seam with broom rider posing. NEEDS CONFIRMATION.**
`AGENT_PROMPT_BROOM_MODELS.md` deferred rider posing to a follow-up, but the
audit found `BroomRiderPoseHandler` already exists and already poses the rider.
Wave 1 still must not touch `BroomDefinition` or the broom entity, and does not
migrate that handler (§3.7). The intended end state — broom writes
`PoseOverride`, handler retires into a locomotion-band pass — is unchanged, but
it is now a migration rather than a greenfield addition. Christian to confirm.

**R-5 — GeckoLib sampler reachability. RESOLVED.** Evaluator not reachable;
minimal reader with a mandatory proof gate (§5.5).

---

## 11. Resolved decisions

| ID | Decision | Ruling |
|---|---|---|
| D1 | Command scope | Server command writing a synced `AttachmentType`; all players see the pose. |
| D2 | State source | Both — manual forced states and an `auto` derived mode. |
| D3 | First person | Included in wave 1 as a required branch, for both pass types. |
| D4 | Keyframe backend | **Revised.** Keyframe passes included, sourced from GeckoLib-format `.animation.json`. No new library dependency — GeckoLib 5.4.5 only. |
| D5 | Module gating | New module. |
| D6 | Mixin surface | Agent audits and justifies the 1.21.11 hook; not prescribed here. |

---

## 12. Out of scope for wave 1

- Broom entity, `BroomDefinition`, rider seating, broom physics.
- Spell cast poses, Apparition, Animagus, Metamorphmagus, Polyjuice — including
  their keyframe clips. Wave 1 ships one throwaway clip only.
- Roll / barrel-roll flight states.
- Replacing `PlayerRenderer` with a Geo renderer, under any framing.
- Any animation dependency other than GeckoLib 5.4.5.
- Camera angle modification.
- Held-item transform correction while posed.
- Armour and render-layer follow-through beyond the vanilla overlay copy.
