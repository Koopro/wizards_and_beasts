# Player Animation Library — Adoption Spike Report

**Date:** 2026-08-13
**Branch:** `spike/pal-eval` (throwaway, never merged)
**Subject:** `com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.9+mc.1.21.11`
**Scope:** whether `PlayerPoseLayer` continues to exist.

**Recommendation: adopt, conditionally.** Reasoning in §2. What would change it, in §2.3.

---

## 0. What was actually run

Everything below marked **TESTED** was executed on this machine. Everything marked
**READ** was taken from PAL's own published sources (`-sources.jar` for the Neo,
Common and Core modules, 141 files) or from its jar's metadata. Nothing is from
PAL's documentation site, which was consulted only to find the Maven coordinate.

| Evidence | How |
| --- | --- |
| Dependency resolution | `./gradlew dependencies --configuration runtimeClasspath` |
| Classpath contents | `unzip -Z1` over PAL's jar, its two JiJ'd jars, and GeckoLib 5.4.5's jar |
| Mixin application order | `-Dmixin.debug.export=true` on `runClient`, then `javap -c` over the exported `PlayerModel` and `LivingEntityRenderer` |
| Compositing semantics, blending, clip loading, Molang | 8 JUnit tests in `src/test/java/at/koopro/wizardsandbeasts/spike/pal/PalSpikeCompositingTest.java`, all green |
| Regression check | Full suite: **575 tests, 0 failures** (567 pre-existing + 8 spike) |

Spike artefacts, all on the throwaway branch: two Java classes under
`at.koopro.wizardsandbeasts.spike.pal`, one test class, **one** clip
(`assets/wizards_and_beasts/player_animations/spike_pal_probe.json`), and the
build-file additions. No shipped file was modified. No schema was touched.

---

## 1. The questions

### Q1 — Does it resolve and build? **YES. TESTED.**

Exact coordinate:

```gradle
repositories {
    maven { name = 'RedlanceMinecraft'; url = 'https://repo.redlance.org/public' }
}
dependencies {
    implementation "com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.9+mc.1.21.11"
}
```

Note the Maven version string is `1.1.9+mc.1.21.11`, not the CurseForge display
name `1.1.9+1.21.11-NeoForge`. `1.1.9+mc.1.21.11` is `<latest>` and `<release>`
in the artifact's `maven-metadata.xml` for this Minecraft version; the 1.2.x line
has already moved to `mc.26.1` / `mc.26.2` and is not for this stack.

Resolved tree:

```
+--- software.bernie.geckolib:geckolib-neoforge-1.21.11:5.4.5
+--- com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.9+mc.1.21.11
|    +--- com.zigythebird.playeranim:PlayerAnimationLibCore:1.1.9+mc.1.21.11
|    |    +--- com.zigythebird:mochafloats:4.1.5
|    |    +--- org.javassist:javassist:3.30.2-GA
|    +--- com.zigythebird:mochafloats:4.1.5
|    \--- org.javassist:javassist:3.30.2-GA
```

`compileJava`, `compileTestJava`, `test` and `runClient` all succeed with it
present. The client reaches the main menu; FML reports
`Found valid mod file PlayerAnimationLibNeo-1.1.9+mc.1.21.11.jar with
{player_animation_library} mods`.

The repository had to be added as an `exclusiveContent` block covering both
`com.zigythebird.playeranim` and `com.zigythebird`, matching how GeckoLib and JEI
are already fenced in this build.

### Q2 — Classpath conflict with GeckoLib's Molang. **NONE. TESTED.**

This was checked first, as instructed. There is no conflict, and the reason is
that the two do not share an implementation at all:

- **GeckoLib 5.4.5** carries its own Molang engine, written in-house, under
  `software.bernie.geckolib.loading.math.*` — `MathParser`, `MathValue`,
  `MolangQueries`, `Operator`, and ~25 `MathFunction` implementations. The jar
  contains exactly one top-level package, `software/bernie/geckolib`, and JiJs
  nothing. Its POM declares no dependencies.
- **PAL** uses Mocha, in `team.unnamed.mocha.*`, shipped as a JiJ'd
  `META-INF/jars/mochafloats-4.1.5.jar` (a floats-typed fork of Unnamed Team's
  Mocha), plus `javassist-3.30.2-GA.jar` for Mocha's bytecode-compiled
  expressions.

Package overlap between the two: **zero**. Duplicate class names: **zero**. No
shading collision is possible because neither relocates into the other's
namespace, and neither declares the other.

The only shared-artifact risk is `org.javassist:javassist`, which PAL JiJs with
range `[3.30.2-GA,)`. That is ordinary NeoForge JiJ resolution — if another mod
ships a higher version, the higher one is selected. No mod in this project's
runtime currently ships javassist.

Runtime confirmation: the dev client booted with both libraries loaded, and the
mixin log contains no error, no conflict, and no failed injector.

**This does not end the evaluation.** Proceed.

### Q3 — Application order relative to our `@At("TAIL")` delegate. **PAL RUNS AFTER US, ON BOTH HALVES. TESTED, FROM BYTECODE.**

Determined by exporting mixin-transformed classes from a real client run and
disassembling them, not from documentation and not from priority arithmetic.

**Limb half —** `PlayerModel.setupAnim(AvatarRenderState)`, after transformation:

```
127: invokespecial HumanoidModel.setupAnim            // vanilla finishes
133: invokespecial handler$zzp000$wizards_and_beasts$WizardsAndBeastsMod$applyPartTransforms
139: invokespecial handler$zzh000$player_animation_library$setupPlayerAnimation
142: return
```

Ours at 133, PAL's at 139. PAL declares `@Mixin(value = PlayerModel.class,
priority = 2001)` with the comment `//Apply after NotEnoughAnimation's inject`,
and injects at `@At("RETURN")`; ours is default priority at `@At("TAIL")`. Both
resolve to the same single `return`, and the higher-priority mixin, applied
later, lands after.

**Pose-stack half —** `LivingEntityRenderer.submit`, after transformation:

```
149: invokevirtual setupRotations(...)   // our body transform runs at its TAIL
158: invokevirtual PoseStack.scale(F,F,F)   // vanilla's scale(-1, -1, 1)
171: invokespecial handler$zzf000$player_animation_library$doTranslations
174: invokevirtual LivingEntityRenderer.scale(...)
291: invokeinterface SubmitNodeCollector.submitModel(...)
```

Ours completes at 149; PAL's body transform is at 171. Both multiply into the
same `PoseStack` before `submitModel`, ours first.

Two facts worth carrying forward regardless of the ruling:

1. **The frames differ in handedness.** Ours applies at the tail of
   `setupRotations`, i.e. *before* vanilla's `scale(-1, -1, 1)` at 158. PAL's
   applies *after* it, and compensates by wrapping its own transform in a second
   `scale(-1, -1, 1)` and negating `rotX` and `rotY`. Two correct
   implementations, two different sign conventions.
2. **`setupAnim` is called more than once per player per frame** in 1.21.11 —
   once at `LivingEntityRenderer.submit:321` for the render layers, and again from
   `ModelFeatureRenderer` when the submitted node is actually drawn. The pose
   layer's purity requirement (schema §3.7) is not a nicety here; it is what makes
   repeated evaluation safe. PAL has the same property for the same reason.

### Q4 — Compositing with the existing procedural pass. **IT BLENDS BY SEEDING, THEN PAL WINS PER-AXIS. STABLE. TESTED.**

This is the answer the spike existed for, so the mechanism, not just the outcome:

PAL's `PlayerModelMixin` calls, per part,
`RenderUtil.copyVanillaPart(part, bone)` and then
`AvatarAnimManager.updatePart(part, bone)`. The first copies whatever is *sitting
on the `ModelPart` right now* into a `PlayerAnimBone`; since PAL runs second
(Q3), that is our pass's output. The second runs the bone down PAL's layer stack
and writes the result back with `RenderUtil.translatePartToBone`, an absolute
write.

So our pose is not a competitor to PAL's stack — it is the stack's **input**.
What happens to it is decided one axis at a time by
`PlayerAnimBone.copyOtherBoneIfNotDisabled`, which copies only the channels a
layer has actually enabled.

Measured (`palReplacesTheAxesItAddresses`, `palPreservesAxesNoLayerAddresses`):

| Case | Result |
| --- | --- |
| Axis a PAL layer writes | PAL's value replaces ours outright |
| Axis on the same bone that no PAL layer writes | our value survives verbatim |
| Bone no PAL layer addresses at all | our value survives verbatim |

**Nothing silently wins the whole model, and nothing flickers.** When no PAL
animation is active, `setupPlayerAnimation` takes the `else` branch to
`pal$resetAll`, which is an explicit no-op — PAL touches nothing at all. Frame-to-
frame stability follows from the ordering being baked into bytecode at class-load
and from both systems being pure functions of render state; there is no
last-writer race to be non-deterministic about.

**The one genuinely bad case is the pose-stack half**, and it is bad in the
*non*-adoption direction. Our body transform and PAL's body bone are separate
matrix multiplications into the same stack (149 and 171). They **compose**, they
do not arbitrate. A player whom our `FlightPosePass` has pitched 60° forward,
who then plays any PAL animation with a `body` channel — an Emotecraft emote, for
instance — gets **both** pitches. That is a visible defect with no seam to fix it
at, and it is exactly the "two systems compositing across a seam" that the ruling
identified.

### Q5 — The `BODY` gate. **YES, AND NOT VIA CUSTOM PIVOTS. TESTED.**

PAL renders whole-body pitch, and the documented custom-pivot mechanism is not
how. It reserves a bone named `body` (distinct from `torso`, which is the chest
`ModelPart`) and consumes it in `LivingEntityRendererMixin.doTranslations` as a
direct `PoseStack` transform:

```java
PlayerAnimBone body = animationPlayer.get3DTransform(new PlayerAnimBone("body"));
poseStack.translate(-body.getPosX()/16, body.getPosY()/16 + 0.75, body.getPosZ()/16);
body.rotX *= -1;
body.rotY *= -1;
RenderUtil.rotateMatrixAroundBone(poseStack, body);
poseStack.scale(body.getScaleX(), body.getScaleY(), body.getScaleZ());
poseStack.translate(0, -0.75, 0);
```

This is the same idea as `PlayerModelPart.BODY` / `PoseStackResult`, arrived at
independently, and it closes the §3.9 gap for the same reason ours does: the
injection site is inside `submit`, after `setupRotations` has yawed the stack to
the player's facing. Test `wholeBodyPitchIsAnOrdinaryBone` confirms a procedural
layer can drive it.

It is also a **capability regression** in one specific respect, which matters
because §3.9 was written around it:

- The pivot is **hard-coded at 0.75 blocks** (`+0.75` … `-0.75`), i.e. hip height.
  Our `PoseStackResult` carries an arbitrary pre-rotation and post-rotation
  translate pair (`X/Y/Z` and `X2/Y2/Z2`), which is what lets a pose pitch about
  the *chest* rather than the hips or the feet. Under PAL that choice is gone —
  a chest pivot has to be faked by feeding compensating `posX/posY/posZ` on the
  body bone, which is expressible but is arithmetic the author now owns.
- `body.getPosX()` is negated on application while Y and Z are not (they are
  inside the mirrored frame). Another sign trap of exactly the class the parity
  test exists to catch.

**This is the strongest argument for adoption**, as anticipated — but it is worth
being precise that PAL's advantage here is not capability, since we already
solved §3.9. It is that PAL owns *both* halves through one stack, so the two
halves cannot disagree.

### Q6 — Format and convention. **LOADS OUR FORMAT DIRECTLY; APPLIES ITS OWN Y CONVERSION ON POSITION ONLY. TESTED.**

PAL's `AnimationFormat` enum has exactly two members, `GECKOLIB` and
`PLAYER_ANIMATOR`, and `UniversalAnimLoader` sniffs which by structure: an object
with a top-level `"animations"` key is read as GeckoLib format. Test
`geckoLibClipLoadsAndKeepsItsSign` loads a hand-written GeckoLib-format clip and
gets a usable `Animation` out.

On the numbers — this is the part that could have reproduced the parity-test
defect class, so it was measured rather than assumed:

- **Rotations: no sign change, degrees → radians only.** A keyframe of `-90`
  degrees comes out of `get3DTransform` as `-1.5708` rad, asserted to 1e-4. There
  is no negation anywhere in `AnimationLoader`.
- **Rotations onto a `ModelPart`: identity.** `RenderUtil.translatePartToBone`
  assigns `part.xRot = bone.getRotX()` with no flip.
- **Positions: Y is negated, X and Z are not.**
  `part.y = -bone.getPosY() + initialPose.y()`, and the inverse on the way in.
  PAL's bone space is Y-up; `ModelPart` is Y-down.
- **Positions are deltas from `getInitialPose()`, not absolutes.** PAL adds the
  initial pose back on `x`, `y`, `z` and `yRot`, and does *not* for `xRot`/`zRot`.

Consequence for the project's mapping, stated plainly:

- The `diag(1, -1, 1)` derivation **still holds** — PAL negates exactly Y on
  position and nothing on rotation, which is the same relationship.
- `y_model = 24 - y_geo` is a **geometry**-space mapping, used to check
  `player.geo.json`'s pivots against the vanilla rig. PAL never reads
  `player.geo.json` (it animates vanilla `ModelPart`s by name), so that mapping
  neither breaks nor applies. The parity test keeps its value as a guard on the
  authoring rig; it simply stops being on the runtime path.
- The trap that *does* survive is the pose-stack body bone: negated `posX`,
  negated `rotX`/`rotY`, un-negated `posY`/`posZ`, fixed hip pivot. Any
  hand-migration of `FlightPoseConstants` into body-bone values must be checked
  against a rendered frame, not reasoned about.

### Q7 — First person. **PAL EXPECTS TO OWN THAT PATH ENTIRELY. READ.**

PAL does not cooperate with `RenderArmEvent` / `RenderHandEvent`; it replaces the
first-person pipeline whenever an active animation asks for it:

- `firstPerson.ItemInHandRendererMixin` — `@Inject(method = "renderHandsWithItems", at = HEAD, cancellable = true)`
- `firstPerson.ItemInHandLayerMixin` — cancels `submitArmWithItem` at HEAD
- `firstPerson.LevelRendererMixin` — `@ModifyExpressionValue` on `Camera.isDetached()` inside `extractVisibleEntities`, so the player entity renders while the camera is attached
- `firstPerson.HumanoidArmorLayerMixin`, `firstPerson.LivingEntityRendererMixin` — suppress layers on the first-person pass
- `PlayerModelMixin` — hides head/body/legs and shows arms per `FirstPersonConfiguration`

The gate is per-animation: `FirstPersonMode.NONE` (the default) means "transparent
in first person" and PAL leaves vanilla alone. `THIRD_PERSON_MODEL` means PAL
takes over and renders the real model from the camera.

For this project the collision is smaller than it looks, and that is itself a
finding: **the pose layer's first-person branch is declared but not wired.**
`FirstPersonContext` has `RIGHT_ARM` / `LEFT_ARM` members and a `part()`
accessor, but the only two call sites in the codebase
(`PlayerPoseHandler:44`, `PlayerPoseLayer:130`) both pass `FirstPersonContext.NONE`.
The two `RenderHandEvent` listeners that do exist
(`AnimagusClientViewHandler`, `ObscurialClientViewHandler`) hide the hand for a
transformed player; they do not pose it. So adopting PAL here fills a gap rather
than replacing working code — but `FirstPersonConfiguration` (five booleans:
show left/right arm, left/right item, armor) is a narrower vocabulary than
`FirstPersonContext` was reaching for, and a wand-tip pose that needs a specific
shoulder angle in first person still has to be authored as a clip or a layer, not
configured.

### Q8 — Distribution. **BOTH-SIDES JAR, CLIENT-ONLY BEHAVIOUR; A SOFT DEPENDENCY IS POSSIBLE BUT POINTLESS UNDER THE RULING. READ.**

From `META-INF/neoforge.mods.toml` and the entrypoint:

- `modId = "player_animation_library"`, `license = "MIT License"` — matches the
  brief. `LICENSE` in the upstream repo is MIT. **No licence discrepancy; no
  reason to halt.**
- Both declared dependencies (`neoforge`, `minecraft`) carry `side = "BOTH"`.
  There is no `displayTest` key, so FML's default applies.
- Every mixin in `player_animation_library.mixins.json` is in the `client` array;
  the `mixins` array is empty.
- `PlayerAnimLibModNeo` registers only `AddClientReloadListenersEvent`,
  `FMLClientSetupEvent` and (in dev only) `RegisterClientCommandsEvent`.

So it loads on a dedicated server and does nothing there. It is not marked
client-only, and it is not architected to be omitted from one side.

**Absent entirely:** our mod would fail to load if PAL were a hard dependency and
missing; if declared `type = "optional"`, every PAL touchpoint would need a
`ModList.isLoaded` guard plus classloading isolation behind an indirection, and
the fallback would have to be… `PlayerPoseLayer`. Under the ruling that
`PlayerPoseLayer` is deleted, there is no fallback, so **adoption means a hard
dependency in practice.** Keeping it soft means keeping both systems, which is
the outcome the ruling was written to avoid.

### Q9 — Custom animations, not just clips. **YES, GENUINELY. TESTED.**

`com.zigythebird.playeranimcore.animation.layered.IAnimation` is a public
interface with default methods, and `AnimationStack.addAnimLayer(int, IAnimation)`
accepts any implementation. The surface a procedural pass needs is all there:

```java
void    tick(AnimationData state);          // client tick, called even when inactive
void    setupAnim(AnimationData state);     // per render frame, active only
boolean isActive();
PlayerAnimBone get3DTransform(PlayerAnimBone bone);   // the pose itself
boolean canRemove();
```

Test `proceduralLayerIsDrivenByTheStack` registers a pure-Java layer with no
clip behind it and confirms the stack ticks it, calls `setupAnim` on it with the
real partial tick, and pulls transforms from it. Test
`wholeBodyPitchIsAnOrdinaryBone` confirms the same layer can drive the whole-avatar
channel. `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id,
priority, factory)` is the per-player registration path, and
`PlayerAnimationRegisterEvent` on the NeoForge bus is the alternative.

`FlightPosePass` becomes an `IAnimation`. That is a rewrite, not a port — the op
vocabulary (`PoseTarget` × `PoseOpType`) is replaced by direct mutation of a
`PlayerAnimBone` — but nothing in it is inexpressible.

**One real loss.** `PlayerPoseLayer` captures vanilla's per-part output at layer
entry and gives `RESET` a destination, so a fade-out hands the limb back to
vanilla *mid-stride* rather than snapping to the rest pose. PAL has the same
seeding at the `updatePart` boundary, but its own `AbstractFadeModifier` fades
between *layers*, not back toward the captured vanilla value, and its alpha is a
function of elapsed ticks over a fixed length. Reproducing `FadeTimer`'s
behaviour means a custom modifier that overrides `calculateProgress`. Expressible;
not free.

### Q10 — Weighted blending of static pose clips. **BUILDABLE, BUT NOT A BUILT-IN. TESTED.**

The plain answer first: **`AnimationStack` exposes no per-layer weight at all.**
Its `int priority` is an ordering key, nothing more; `get3DTransform` chains
`bone = layer.get3DTransform(bone)` down the list and each layer does what it
likes with what it is handed. The only weight PAL ships is
`AbstractFadeModifier`, whose alpha comes from `time / length`.

That is not a dead end, because the arithmetic is public.
`AbstractFadeModifier` itself blends with `bone.scale(1 - a).add(copy2.scale(a))`,
and `PlayerAnimBone.scale` / `.add` are ordinary public methods. Test
`threeWayWeightedBlendIsContinuous` implements a three-source weighted blend in
~40 lines (`PalSpikeWeightedBlend`), sweeps a weight across 11 steps, and asserts
the output is linear in the weight to 1e-5 and identical across two evaluations
of the same state.

So: HOVER / GLIDE / PROPELLED as three clips blended continuously from
`propulsion` and pitch **is achievable**, with a mod-owned `IAnimation` doing the
mixing and the clips as its sources. Two caveats worth pinning:

- `PlayerAnimBone.scale` multiplies the **scale** channels too, so weights must
  sum to 1 or the avatar's size drifts. PAL's own fade has the same property.
- Blending is per-channel Euler-linear, not quaternion. At the pitch magnitudes
  flight uses this is fine; near-180° blends would gimbal.

Alternatively `AbstractFadeModifier` is subclassable — `calculateProgress` and
`getAlpha(boneName, progress)` are both `protected` — so a modifier whose alpha
reads live gameplay state is also legal. `getAlpha` taking the bone name means
per-bone weighting comes for free either way.

### Q11 — Custom Molang queries. **YES. TESTED, INCLUDING PER-FRAME RE-EVALUATION.**

This one is easy to assume exists, so it was executed rather than read.

`MolangLoader.createNewEngine(controller)` builds the engine, registers PAL's own
`anim_time` and `controller_speed`, then fires
`MolangEvent.MOLANG_EVENT` and only afterwards calls `queryBinding.block()` to
freeze it. On NeoForge that event is re-posted on the mod bus as
`com.zigythebird.playeranim.neoforge.event.MolangEvent`, which exposes:

```java
boolean setDoubleQuery(String name, ToDoubleFunction<AnimationController> value);
boolean setBoolQuery(String name, Function<AnimationController, Boolean> value);
```

Test `modRegisteredMolangQueryIsReadFromAClip` registers `spike_propulsion`,
loads a clip whose `left_arm` rotation keyframe is the expression
`query.spike_propulsion * 90`, and samples it twice with the backing value
changed in between. Results: 90° → 1.5708 rad, then 45° → 0.7854 rad. The query
is read **every frame**, not folded at load.

PAL also ships ~70 built-in queries already covering much of what flight needs —
`ground_speed`, `is_on_ground`, `body_x_rotation`, `head_x_rotation`,
`is_first_person`, `limb_swing_amount`, `is_sprinting`, `is_swimming`,
`frame_alpha` and so on.

**How much of Q9 does this replace?** A useful amount, but not the hard part.
Molang gets *inputs* into a clip cheaply, so "arm angle follows head pitch" or
"lean scales with speed" becomes clip authoring rather than Java. What it does
not replace is state that has to be *computed and remembered* across ticks —
`FlightStateDeriver`'s EMA over `getKnownMovement()`, `PhaseTimer`'s phase
machine, hysteresis between flight states. Those stay Java. Realistically both
are used: an `IAnimation` owning the state machine, exposing its outputs as
Molang queries, with clips reading them.

---

## 2. Recommendation

### 2.1 Adopt, conditionally

Nothing disqualifying was found. Q2 — the predicted failure mode — is a clean
pass with zero package overlap. Q3, Q4, Q5, Q9, Q10 and Q11 all came back
positive with the mechanism understood rather than guessed. The library is MIT,
matches the stated licence, resolves for this exact stack, and the full suite
stays green with it on the classpath.

The argument that actually decides it is Q4's last paragraph. Today the two
whole-body transforms — ours at `setupRotations`'s tail, PAL's at
`submit:171` — **compose rather than arbitrate**. Any user with Emotecraft, or
with any of the ~20 modpacks bundling PAL, who emotes while flying, gets both
pitches multiplied together. There is no mixin priority that fixes this, because
neither transform is wrong; there are simply two of them. Owning one stack is
the only structural answer, and PAL is the stack that other mods already write
into.

Set against that, the honest counterweight: **PAL's third-party mod adoption is
thinner than its download count suggests.** CurseForge's dependents list filtered
to required-dependency mods shows two entries, with 433 and 959 downloads. The
1.3M figure comes from Emotecraft bundling and modpack inclusion. So the
compatibility case rests on Emotecraft and on packs, not on a broad ecosystem of
mods we would otherwise fight.

### 2.2 The conditions

1. **A rendered in-game pass before any migration is scheduled.** Every finding
   here is bytecode, source or headless arithmetic. Q4's blending was proved at
   the `ModelPart` boundary, not observed on a flying player. The body-bone sign
   traps in Q5 and Q6 are precisely the class of defect that only a frame shows.
2. **`FadeTimer`'s blend-back-to-vanilla is reproduced first** (Q9), as a custom
   modifier, and proved before `PlayerPoseLayer` is deleted. It is the one
   behaviour PAL does not have an equivalent for.
3. **The hip-pivot constraint is accepted or compensated** (Q5). If any shipped
   pose depends on a chest pivot, the compensating offsets are derived and
   parity-checked before migration, not during.
4. **The hard dependency is accepted** (Q8). Under the ruling there is no
   fallback; PAL becomes required.

### 2.3 What would change this

- **A rendered pass showing instability** — jitter, or a pose that differs
  between the `submit` and `ModelFeatureRenderer` invocations of `setupAnim`.
  Q3 showed `setupAnim` runs more than once per frame; the headless tests cannot
  see a disagreement between those invocations.
- **PAL dropping 1.21.11.** The 1.2.x line has already moved to `mc.26.x`.
  `1.1.9+mc.1.21.11` is current, but if 1.21.11 stops receiving fixes while this
  project is still on it, a hard dependency on an unmaintained branch is worse
  than a maintained in-house layer.
- **The hip pivot proving insufficient** for a pose already shipped or specified —
  that turns a clean adoption into a fork-or-work-around.
- **Deciding first-person poses are near-term.** `FirstPersonConfiguration`'s five
  booleans are a much narrower instrument than `FirstPersonContext` was reaching
  for, and PAL takes the whole path or none of it (Q7).

Not disqualifying, and explicitly not weighed: Q10 needing ~40 lines of
mod-owned blending, and Q9 being a rewrite rather than a port. Both are ordinary
work.

---

## 3. Migration cost if adopting

Counted from the working tree, not estimated. **22 files** in `client/pose`,
**7** in the other pose packages, **3** client mixins, **9** test files carrying
**81 test methods**.

### 3.1 Discarded — 14 files, 972 lines

| File | Lines | Why |
| --- | --- | --- |
| `client/pose/PlayerPoseLayer.java` | 280 | replaced by `AnimationStack` |
| `client/pose/PoseStackResult.java` | 77 | replaced by PAL's `body` bone |
| `client/pose/PoseTarget.java` | 66 | replaced by `PlayerAnimBone`'s fields |
| `client/pose/PoseBand.java` | 61 | replaced by `addAnimLayer`'s `int` |
| `client/pose/PlayerModelPart.java` | 60 | replaced by PAL's bone-name strings |
| `client/pose/PoseOpType.java` | 60 | replaced by direct bone mutation |
| `client/pose/PoseContext.java` | 51 | replaced by `AnimationData` |
| `client/pose/FirstPersonContext.java` | 41 | replaced by `FirstPersonConfiguration` |
| `client/pose/ProceduralPosePass.java` | 35 | replaced by `IAnimation` |
| `client/pose/PoseBuilder.java` | 29 | no equivalent needed |
| `client/pose/PosePass.java` | 27 | replaced by `IAnimation` |
| `client/pose/PartPoseData.java` | 86 | replaced by `PlayerAnimBone` |
| `client/pose/PlayerPoseHandler.java` | 94 | replaced by `PlayerAnimationFactory` |
| `mixin/client/AvatarRendererMixin.java` | 35 | PAL owns the body transform |

`client/pose/FadeTimer.java` (64) is a **conditional** fourteenth-and-a-half: it
dies only once condition 2.2(2) is met, otherwise it is reimplemented as a PAL
modifier.

### 3.2 Rewritten — 4 files, 632 lines

`FlightPosePass` (299) and `CastPosePass` (129) become `IAnimation`
implementations. `PoseClientEvents` (75) becomes factory registration.
`PlayerModelMixin` (58) survives but loses its `PlayerPoseHandler.applyPose`
line — the broom-rider, petrification and debug-editor delegates on that hook are
untouched and still need it.

### 3.3 Surviving unchanged — 8 files, 692 lines

`FlightPoseConstants` (205) and `CastPoseConstants` (123) are pure data.
`PhaseTimer` (98), `ClientCastAnimationState` (125) and `ClientPoseState` (50)
are timing and state, independent of how the pose is applied. `PoseOverride` (46),
`PoseOverrideService` (71) and `FlightPoseState` (36) with their sync path
(`PoseSyncHandler`, `ModNetworkPose`, `PoseOverrideSyncS2CPayload`,
`FlightStateDeriver` — 274 further lines) are server-side authority and are not
touched by any of this.

`player.geo.json` survives, as does the `diag(1, -1, 1)` derivation (Q6).

### 3.4 Tests — 81 methods, of which 13 discarded and 19 rewritten

| Test file | Methods | Fate |
| --- | --- | --- |
| `PoseBandTest` | 13 | **discarded** — bands become plain ints |
| `FlightPosePassTest` | 9 | rewritten against `IAnimation` |
| `CastPoseTest` | 10 | rewritten against `IAnimation` |
| `GeckoLibSamplingProofTest` | 7 | **obsolete** — PAL owns sampling; keep only if GeckoLib still samples elsewhere |
| `PlayerGeoParityTest` | 8 | survives |
| `PlayerGeoRigParityTest` | 5 | survives |
| `FlightPoseConstantsTest` | 12 | survives |
| `PhaseTimerTest` | 10 | survives |
| `FlightStateDeriverTest` | 7 | survives |

**Totals: 20 test methods discarded, 19 rewritten, 42 survive untouched.**

One ceiling to note, measured rather than assumed
(`palMixinsDoNotApplyInTheUnitTestHarness`): **PAL's mixins do not apply in this
project's FML unit-test harness.** Its classes are on the classpath and compile,
but FML loads only the tested mod, so `PlayerModel` comes out untransformed and
`PlayerModel instanceof IBoneUpdater` is false. PAL's *blend core* is fully
testable headless — all eight spike tests are — but anything asserting on
composited `ModelPart` output after both mixins have run cannot be a unit test.
That is the same ceiling the current layer has, so it is not a regression; it is
a constraint on how the rewritten tests in §3.4 can be written.

---

## 4. Compatibility cost if not adopting

Concretely, what contends over the same model parts:

- **Emotecraft** (same author, `docs.zigythebird.com` documents both) requires
  PAL and is its largest single driver of installs. An emote is precisely a
  full-body clip with a `body` channel, so it hits the composing-body-transform
  defect in Q4 head-on.
- **Not Enough Animations** — named in PAL's own source. PAL's `PlayerModelMixin`
  carries `priority = 2001` with the comment `//Apply after NotEnoughAnimation's
  inject`, which is direct evidence that this injection point is already
  contested by at least three parties (NEA, PAL, us), and that PAL has explicitly
  positioned itself last.
- **~20 modpacks** currently list PAL as a dependency on CurseForge, several in
  the hundreds-to-thousands of downloads (ReCreate Factory 6.0K, EnchantVenture
  3.7K). A pack is how most users would encounter both this mod and PAL together.
- **Mods** requiring PAL directly are, today, few: CurseForge's dependents list
  filtered to required-dependency mods returns two, at 433 and 959 downloads. The
  ecosystem argument for adoption is weaker than the raw download figure implies,
  and this should be weighed honestly.

If we do not adopt, the failure mode is not a crash and not a hard conflict —
Q4 established that PAL degrades gracefully into our layer on the limb half. It
is specifically the **whole-body transform double-applying**, which is silent,
looks like a physics bug, and will be reported against this mod rather than
against PAL.

---

## 5. Spike hygiene

- Branch `spike/pal-eval`. Never merged, never rebased onto main.
- No schema modified. No shipped pass, `PlayerPoseLayer`, or parity test
  modified. Nothing deleted.
- One clip, as budgeted: `spike_pal_probe.json`, three bone channels, used only
  by the tests.
- The concurrent GUI/skill-tree working-tree changes were never staged.
- `build.gradle` and `gradle.properties` carry PAL only on the spike branch, each
  edit tagged `SPIKE ONLY -- ... Do not merge.` The `mixin.debug.export` flag on
  the client run config is likewise spike-only.
- No unrelated defect was fixed. One observation that is not a defect and not in
  scope to change: `FirstPersonContext`'s non-`NONE` members currently have no
  producer (Q7).
