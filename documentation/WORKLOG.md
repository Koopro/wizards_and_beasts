# Worklog

> **Reverse-chronological build log** — each build pass has an "Audit" subsection (findings) followed by "Resolution" subsection (deltas). Sources: `MIGRATION_DELTAS.md` (resolution deltas) + `AUDIT_PUNCHLIST.md` (audit findings + fix passes). Merged 2026-07-13.

## Template for a new pass

Copy this block, put it directly under this heading (newest first), and delete anything that has no
content — an empty "Audit" is worse than none, because it reads as "nothing was wrong".

Two rules the existing entries follow, and the reason for each:

1. **Audit before Resolution.** The findings are written down before the fix, so an entry records
   what was actually true, not a reconstruction from the diff.
2. **A resolution states the behaviour change, not the edit.** "Renamed X to Y" belongs in git.
   What belongs here is what a player or a caller can now do that they could not before, or what
   silently used to happen and no longer does.

Do not add an entry for work that has not happened. A worklog whose entries cannot be trusted is
worth less than no worklog.

```markdown
## YYYY-MM-DD — <short title: the system touched, and what changed about it>

### Audit
- <finding: what is wrong, where, and how it shows up>
- <deliberate non-fix: what was found and left alone, and why — these matter most later>

### Resolution
- **<the behaviour change, in bold>.** <What now happens. Name the classes/files, and say what the
  old behaviour was so the delta is readable without a checkout.>
- **<next change>.** <…>

### Deferred
- <what this pass did not do, and what it is blocked on>
```

---

---

## 2026-08-28 — Brooms: inventory appearance

### Audit
- **BLOCKER — seven broom sprites were never loaded.** Seven of the eight `models/item/*.json` were
  texture-less children of `wizards_and_beasts:item/broom`, so every broom item drew the generic
  broom's icon and the seven distinct PNGs beside them did nothing. The item side had been reported
  complete twice on the strength of the files existing, correctly named, next to correctly named
  sprites. Existence was the wrong thing to check.
- **All eight used `minecraft:item/generated`** or inherited it, so a broom was held flat in hand
  rather than angled like a shaft.
- **The icons varied only in hue.** One shape for all eight, and five of the eight are browns; at 16px
  a brown is a brown.

### Resolution
- **Every model declares its own `layer0`** and parents to `minecraft:item/handheld`.
  `ModModelProvider` uses `declareCustomModelItem` for all eight, which emits only the item-model
  definition and leaves `models/item/` hand-authored — so these files are the intended home and
  datagen will not overwrite them.
- **Three builds instead of one shape**: `_broom(shaft_width, bundle, collar_width)` backs `broom`,
  `broom_slim` and `broom_heavy`, and the bundle scales about its own root so a heavy broom reads as
  heavy in outline rather than only in colour.
- **The three materials became three things** — `TRIM` the handle, `BODY` the twig bundle, `MARK` the
  binding — which is what carries the brief's colour language: twine, brass, silver, chrome, red cord,
  gold and iron collars, each the metal that broom carries on its entity sheet.
- The collar is drawn **across** the handle and proud of it. Drawn along the shaft at one pixel wide,
  as first attempted, it vanished completely.
- **`BroomItemModelTest`** checks what existence cannot: each model names *its own* texture, each uses
  the handheld pose, and every sprite is **distinct by SHA-256**. Eight files with eight names can
  still be eight copies of one picture. The roster is scraped from `BroomItemRegistry`.
- **1313 tests pass** (three new).

---

## 2026-08-28 — Brooms: authoritative definition content

### Audit
- **`firebolt_supreme` was less steady than the Firebolt it upgrades.** `stabilityRating` 0.60 against
  0.65, where the brief asks for slightly higher. It also meant the Supreme took *more* of the racing
  profile's high-speed sink than the cheaper broom.
- **`nimbus_2001`'s turnSpeed already satisfied the brief** at 1.12 against the 2000's 1.10. Verified
  rather than changed.
- **The brief's `passengerOffset` values are positive.** `[0, 0.52, 0.02]` and `[0, 0.62, 0.0]` would
  reintroduce the floating-rider bug fixed on 2026-08-27: the offset positions the rider's feet, a
  rendered humanoid's hip sits 0.751 blocks above its own position, and the shafts are drawn with
  their top at 0.375 and 0.4375 — so +0.52 puts the hips 0.9 blocks clear of the handle.
- **`comet_260`'s "dust/gold mix" is not expressible.** `trailParticle` holds one id.

### Resolution
- **All seven brooms authored to spec** — profile, wobble, drift, momentum, crash multiplier, trail,
  boost cue and seat. Flight numbers untouched, as the brief asks.
- **`firebolt_supreme` stability 0.60 → 0.68**, so it is steadier than the Firebolt in fact as well as
  in tier.
- **Per-broom `model` files, generated not written.** `tools/broom_model.py` now emits one geometry per
  broom that names a model — the master rig with the unselected variants dropped, 20 bones against 45.
  Hand-authoring seven would be seven copies of the same shaft, and the renderer addresses variants by
  name, so a drifted copy hides bones that exist in one file and not the other.
  `everyPerBroomModelMatchesTheMasterRig` re-derives the expected bones and demands an exact match, and
  checks each declares its own `geometry.<name>` identifier. The generic broom names no model and keeps
  the master rig, so the fallback stays exercised.
- **`tools/broom_lineup.py` previews from the shipped file** when a broom names one, instead of
  re-filtering the rig — a preview that agrees with itself while disagreeing with what ships is worse
  than none. The lineup is pixel-identical before and after flattening, which is the proof the
  generator is right.
- **Seats derived, not transcribed**: -0.375 for the Cleansweep's `oak` shaft, -0.3125 for the
  Oakshaft's `heavy_oak`, -0.4375 for the thin shafts. The brief's *relative* intent — Oakshaft higher
  than Cleansweep — is honoured exactly; only the frame differs, and `BroomSeatParityTest` recomputes
  the values from the geometry.
- **1310 tests pass** (one new).

### Deferred
- A blended trail would need a second field or a weighted-list codec. The Comet takes dust, which suits
  its straw-and-birch palette and keeps gold as the Nimbus family's tell.

---

## 2026-08-28 — Brooms: sound, trails, polish and crash flair

### Audit
- **`broom_polish` was an item with a tooltip and nothing else.** A `SimpleTooltipItem` whose entire
  behaviour was a line of hover text describing what it would do if it did anything.
- **A spent broom was rideable.** The spawn path did `setCurrentDurability(Math.max(1, remaining))`,
  which handed a fully damaged broom one point of durability and let it fly — then broke it again on
  the first knock. The acceptance case, and it had been open since brooms had durability.
- **`crashDamageMultiplier` applied to the rider but not the broom.** A Firebolt's 1.35 made the crash
  hurt the player more and cost the broom nothing, so the broom was the safe half of the pair on the
  broom most likely to hit a wall.
- **The slipstream came out of the rider's back.** It seeded behind the *player*, offset along the
  direction of travel, so on anything flying nose-up it drew inside them. `fx_tail` had been in the
  rig and referenced by no Java since the rig landed.
- **Found, not fixed: `PROTEGO_SHATTER` and `AK_BYPASS_FLASH` have no particle provider.**
  `ModParticleProviders` registers sprite sets only for the tinted spell types; `ProtegoShieldEntity`
  sends both of these. They render nothing. Pre-existing, different subsystem, and fixing it needs a
  decision about what they should look like.
- **Reversal, stated plainly:** the previous pass argued three bespoke trail particle types would be
  "seven registry entries to say what `minecraft:flame` already says". The brief named them again, so
  they were built — and they cost less than that objection assumed, because one class and one sprite
  serve all three.

### Resolution
- **Five more sound events and still not one audio file** — wood breaks for the two crash grades, a
  step and a wingbeat for mounting, honeycomb wax for the tin, all re-pitched vanilla with subtitles.
- **Three registered trail types** sharing one `BroomTrailParticle` and one sprite; a `Style` enum
  carries colour, size, lifetime, friction and gravity, so Firebolt embers rise and die in seven ticks
  where Cleansweep dust falls and lingers for fourteen. Registered with `overrideLimiter = false`: a
  trail is ambience and should thin out with the player's particle setting.
- **The trail leaves the twigs.** `BroomEntity.tailPosition()` reads the rig's `fx_tail` anchor and
  rotates it into the broom's frame. Threshold 0.55 → 0.4, boosting always draws, and *rate* carries
  the speed while colour stays the broom's identity.
- **Polish is a real item.** Repairs 25% of maximum durability — a fraction, because a flat figure is
  most of a Cleansweep and a rounding error on an Oakshaft — and leaves the handle slick for twenty
  minutes, halving the heading wander. Works with the broom in the other hand or on a broom standing
  in the world, and refuses one that needs nothing rather than eating a tin.
- **`POLISHED_UNTIL_TICK` is an absolute tick**, stripped by `BroomItem.inventoryTick` once it lapses,
  so everything downstream can read "present" as "polished". The entity carries a *synced* flag, since
  the movement step runs on both sides and the stack does not.
- **Crash flair**: severe impacts scale durability by `crashDamageMultiplier`, throw twigs from the
  tail and crit particles at the contact point, play the graded sound, and tell the rider on the
  action bar.
- **Mount cue plus 3 ticks of invulnerability**, because a rider seated below the broom's origin is
  briefly inside whatever it was parked on.
- **A spent broom refuses to fly** and says so; a tin is enough to get it airborne again.
- **1309 tests pass** (8 new). `BroomPolish` takes a bare `long gameTime` alongside the `Level`
  overload so the window can actually be tested — an absolute expiry nothing checks is a permanent
  buff wearing a timer's clothes.

### Deferred
- The polish buff is the wobble half of the brief's "+2% maxSpeed OR reduced wobble". Speed was left
  alone deliberately: it is the axis every broom is already balanced on.

---

## 2026-08-28 — Brooms: handling profiles as behaviour, not just numbers

### Audit
- **The scalars were not enough.** `handlingProfile` chose a bundle of numbers and `BroomMovement` read
  them inline, so brooms differed only in ways a number can express. All eight still accelerated,
  turned, drifted and stopped by identical arithmetic — the "same flying stick with a different
  maxSpeed" the brief names.
- **`SCHOOL`'s crash multiplier was backwards.** Set to 1.15 on the reasoning that a cheap broom is a
  flimsy one, which is wrong for the broom students are handed: a school broom is built to survive
  being flown badly. `cleansweep_seven.json` also carried a redundant 0.9 override of it.
- **`TANK`'s crash multiplier softened real crashes.** 0.75, where the brief softens only glancing
  knocks and says severe ones still hurt.
- **`onBoostStart` was about to ship inert.** No profile implemented it, because the FOV punch it was
  meant to trigger is derived from boost ticks instead. A hook nothing implements is a tuning constant
  nothing reads: it looks like a working seam, so the next person wires into it and nothing happens.
  Caught by a test written for the purpose, not by review.
- **Deliberate non-fix: the FOV punch stays in `BroomFlightFx`.** The brief names `BroomCameraHandler`;
  that class handles camera angles and third-person distance. FOV was already implemented there,
  already derived from boost ticks (which the brief prefers over a new flag) and already smoothed.
- **Deliberate non-fix: no 40-tick session window on the school boost cap.** It would make the same
  broom behave differently a moment later with no visible cause, and needs per-flight state to do it.

### Resolution
- **`entity.broom.handling`**: `BroomHandlingProfile` with eleven hooks, every one defaulted, so a
  profile class is exactly its deviations — `BalancedHandling` overrides nothing, `TankHandling` four.
  `HandlingProfileRegistry` maps the data enum onto the behaviour, keeping the dependency pointing
  from behaviour to data so `BroomDefinition` never gains a `BroomEntity` reference.
- **School brooms cap their boost at 1.35× whatever the JSON asks**, brake 15% harder, bank 20% less
  and lurch when the boost catches. The cap is a no-op for every shipped school broom, asserted, so it
  guards datapacks rather than silently retuning the Cleansweep.
- **Racing brooms lock the heading** when the rider is not steering, and past 0.7 speed ratio tighten
  the turn and go nose-heavy. The sink runs through `afterVelocityComputed` rather than
  `modifyWeakGravity`, because gravity is skipped while a vertical key is held — routing it there
  would cancel the effect exactly when a rider is pulling out of a dive.
- **Tank brooms** lose 15% acceleration, turn at 0.75 always (mass, not momentum, so it bites at rest
  too), sink 15% less, and halve glancing durability loss — floored at 1, never zero.
- **Two scalar corrections**: `SCHOOL` crash 1.15 → 0.85, `TANK` 0.75 → 1.0.
- **The momentum-to-deceleration mapping is normalised**, anchored on `BroomHandling.NEUTRAL_MOMENTUM`,
  so balanced is exactly a no-op. Raw `1 / momentumRetention` would have made every broom in the game
  stop 11% faster than it used to.
- **Response curves are public statics** and `HandlingMath` takes a tick count rather than an entity,
  so every threshold is unit-testable. `BroomHandlingProfileTest` asserts the orderings a blind flight
  test would reveal — school brakes harder than balanced, tank turns slower than school, a Nimbus holds
  a line better than a Cleansweep, a Firebolt punishes a wall harder than either.
- **One narrow accessor pair** (`get/setVerticalVelocity`) plus `isSteering()`, because a sub-package
  cannot see package-private fields. Everything else a profile needs arrives as an argument.
- **1302 tests pass** (17 new). Input timeout and sequence guards untouched.

### Deferred
- The blind flight test itself is the acceptance and remains unrun; what is automated is every
  ordering it would expose.

---

## 2026-08-27 — Brooms: the client never had the definitions

### Audit
- **BLOCKER — the definition table was never sent to clients.** `BroomDefinitionLoader` is registered
  on `AddServerReloadListenersEvent`, so `BroomDefinitionRegistry` is populated server-side only.
  `BroomEntity` syncs its `DEFINITION_ID`, which is necessary and was never sufficient: an id is a key
  into a table, and on a dedicated server the client's table was empty. Every `resolveDefinition()` on
  the client fell through to `CODE_DEFAULT`, so every broom drew the generic sheet, sat at the generic
  seat and shed the generic trail whatever its JSON said. It worked in single-player only because the
  integrated server shares a JVM and the registry is a static field. This silently invalidated the
  whole per-broom pass and both of this brief's acceptance criteria.
- **The definition cache went stale on `/reload`.** `BroomEntity` cached its resolved definition and
  cleared it only when the synced id changed. A reload that retunes a broom in place changes no id, so
  brooms already in the world kept their pre-reload values until they unloaded.
- **The seat was measured to the shaft's centre line,** not its top surface, so every rider sank half a
  shaft into the handle. Worse, shafts are 2 to 6 model units thick at the point the rider sits, so one
  seat height could not be right for both the Firebolt's needle and the Oakshaft's log — which is
  exactly the case the acceptance criterion names.
- **Deliberate non-fix: pitch is not applied to the seat offset.** The brief asked for yaw *and* pitch.
  The drawn broom does not pitch with `getXRot()` — its nose angle is `updateTilt`'s roughly `-0.4×`,
  clamped to 35° — so rotating the seat by the full entity pitch swings the rider further than the mesh
  they sit on. Using the visual tilt instead is worse: those are client render values and this method
  positions the rider on the server too. The cost of omitting it is bounded by the largest authored
  `z`, five centimetres, which is under a pixel at any real flight angle.
- **Deliberate non-fix: `rider_attach` bone sampling.** GeckoLib bone transforms exist only inside a
  client render pass and `positionRider` needs an answer on the server. Not an API-convenience call.
- **Already in place, verified not rebuilt:** the custom `GeoModel` (`BroomVariantGeoModel`, branching
  model/texture/animation off the definition) and per-item models — all eight brooms already have
  `models/item/<id>.json`, `items/<id>.json` and a distinct sprite.

### Resolution
- **`BroomDefinitionsSyncS2CPayload` pushes the table on `OnDatapackSyncEvent`** — login and every
  `/reload` — the seam `AbilityFrameworkEvents` and the brew recipe sync already use. Brooms now render
  as themselves on a dedicated server, which is the acceptance criterion.
- **The stream codec is `BroomDefinition.CODEC` itself,** through
  `ByteBufCodecs.fromCodecWithRegistries`, not a hand-written field list. Twenty-five components across
  four nested records would otherwise need adding in three places, with the third failing silently as
  the field arriving at its default on the client — the very bug being fixed.
- **A round-trip test guards that.** Every shipped definition is encoded and re-decoded through
  `NbtOps` and must come back equal, so a field `encode` forgets to write fails the build.
- **`BroomDefinitionRegistry.generation()`** is bumped on every swap and the entity re-resolves when
  its cached generation goes out of date, so a `/reload` reaches brooms already in the world.
- **Riders sit on top of their own shaft.** Seat derived from the top surface of the shaft chain's
  `_mid` segment minus the 0.75-block humanoid hip pivot: `-0.4375` for the thin shafts, `-0.375` for
  `oak`, `-0.3125` for `heavy_oak`. `BroomSeatParityTest` recomputes it from the geometry for every
  broom and separately asserts the Oakshaft and the Firebolt genuinely differ.
- **1285 tests pass** (three new).

### Deferred
- `passengerYawOffset` is supported and every shipped broom leaves it at zero; it exists for datapacks
  wanting a side-saddle rider.

---

## 2026-08-27 — Brooms: models, audio and handling profiles from the datapack

### Audit
- **The codec had run out of room.** `BroomDefinition.CODEC` is a hand-written `Codec.of` whose
  decode was a nested `flatMap` chain 22 levels deep, because `RecordCodecBuilder.group` caps at 16
  fields. Sixteen more fields meant 38 levels and about 190 characters of leading whitespace on the
  deepest line. It also short-circuited: a datapack with four bad values reported one, so finding
  them all took four edit-and-reload cycles.
- **The renderer could only ever draw one geometry.** Model and animation were fixed at construction;
  only the texture branched.
- **The brief's `passengerOffset` default would have re-broken the seat.** It specified
  `(0, 0.55, 0)`. The rig draws every shaft 0.25 blocks up and a humanoid's hip pivot is 0.75 blocks
  up, so an absolute +0.55 seats the rider 1.30 blocks above the handle — the same class of mistake
  as the `height * 0.55` fixed earlier the same day, one step further along.
- **The mod has no audio files at all.** Zero `.ogg` in the repo; all 47 existing sound events are
  vanilla samples re-pitched in `sounds.json`. Seven bespoke `broom_trail_*` particle types would
  likewise have been seven registry entries, seven providers and seven sprite sets to say what
  `minecraft:flame` already says.
- **Three places spelled out the same boost test by hand** — input held, charge remaining, cooldown
  clear — against a package-private field the FX layer could not reach.
- **Deliberate non-fix: `handlingProfile` was not left as a label.** An enum authored into every file
  and read by nothing is the `wood_tint` failure again, so the profile supplies every other handling
  default and explicit keys override it.

### Resolution
- **Sixteen optional fields, grouped four ways.** `BroomAssets` (model/texture/animation),
  `BroomHandling` (profile and seven scalars), `BroomAudio` (two sounds and a trail particle) and
  `BroomSeat` (offset and yaw). The JSON stays flat — `yawDrift` sits next to `maxSpeed` — so the
  record gained four components rather than sixteen.
- **The decode pyramid is gone.** `BroomFields` reads the flat keys and collects every fault, so one
  message now names all of them. Unknown keys are ignored, which is what makes a definition from a
  later version of the mod load in an earlier one.
- **A datapack can redirect geometry, texture and animation with no code edit,** and any of the three
  left unset falls back to the shared `broom` asset. `texture` takes a full `textures/...` path or a
  GeckoLib subpath; the one-day-old `entity_texture` key is still accepted.
- **`BALANCED` reproduces the constants it replaced, to the decimal** — momentum 0.90 maps to the old
  `COAST_DRAG` 0.990 and `INPUT_DRAG` 0.995, crash multiplier 1.0, durability loss 1/2/3. The single
  deliberate change is `yawDrift` 0.02: every broom now weaves about half a degree at top speed where
  it tracked a perfect line. Deterministic from `tickCount`, never random, because yaw is stepped on
  both sides of the connection and a random wander desyncs into a visible snap-back.
- **Ten of the sixteen fields change behaviour rather than being carried.** Drift, wobble and momentum
  in `BroomMovement`; crash damage and durability loss in `BroomImpacts`; FOV punch, boost cue, flight
  loop and trail particle in `BroomFlightFx`; the seat in `BroomEntity`; the yaw in
  `BroomRiderRenderHandler`. `/wandb world broom info` prints all of them.
- **Eight new sound events and not one new audio file.** Elytra loops for wind, firecharge and
  firework launches for boosts, dragon wingbeats for the heavy end — all re-pitched vanilla, with
  subtitles.
- **`BroomEntity.isBoostFiring()`** replaced the three hand-written copies of the same three-way test.
- **Nine new tests**, 1282 passing. The ones that matter: a definition authoring none of the new keys
  decodes to the old broom exactly; a profile's defaults lose to an explicit key and nothing else
  moves; every fault is reported together; every named sound exists in `sounds.json` and has a
  subtitle; every trail particle is an option-free vanilla type.

### Deferred
- `model` and `animation` are supported and unused — no shipped broom needs its own geometry, which
  is the point of the master rig. The fields exist for datapacks.
- A replacement geometry silently opts out of the slot system, because the renderer hides variants by
  name. Documented rather than guarded; a rig that carries the bones keeps working.

---

## 2026-08-27 — Brooms: seven identities over one rig

### Audit
- **Every handle in the game rendered grey.** `shaft_*` and `tail_cap_*` are painted greyscale
  precisely so `wood_tint` can colour them, and all eight broom definitions omitted the key. The
  codec, the renderer's `getRenderColor` multiply and the greyscale paint pass had all shipped; no
  value was ever authored.
- **One texture for eight brooms.** `BroomRenderer` built a `DefaultedEntityGeoModel("broom")`,
  which fixes the sheet at construction. The slot system gave the brooms different outlines, and
  outline is the first thing distance takes away.
- **Two shaft variants that were the same shaft.** `shaft_plain` and `shaft_swept` had byte-identical
  cubes differing by 5 deg and 9 deg of rotation. `oakshaft_79` — briefed as a very thick antique oak
  — selected `plain`, the school broom's shaft. `broom` and `cleansweep_seven` had identical
  `model_slots` rows.
- **The rider was flying next to the broom.** `getPassengerAttachmentPoint` returned
  `dimensions.height() * 0.55`. The rig draws every shaft 0.25 blocks above the broom's position and
  a rendered humanoid's hip sits 0.75 blocks above its own, so the seat wanted -0.50 and got +0.33:
  the hip was 0.83 blocks clear of the handle.
- **Inventory icons disagreed with the world.** All eight drew one shape in one hue, and the Comet
  was the darkest icon in the set while being the palest broom in the game.
- **Deliberate non-fix: the brief's seven per-broom `.geo.json` files were not built.** The master
  rig already holds every part of every broom and the renderer already hides the unselected ones;
  seven copies of 45 bones would be seven places for the hide-all-but-one loop to drift out of sync
  with. The identity gap was colour, and colour is a texture. Bone names `footrest`, `rider_attach`
  and `boost_fx` likewise stay as the shipped `footstrap`, `fx_mount` and `fx_tail` — renaming them
  breaks `BroomModelParityTest`, the animation clips and the docs for no visible gain.
- **Deliberate non-fix: `fx_tip`, `fx_tail` and `fx_mount` are still referenced by no Java.** They
  are anchors waiting on a particle pass; the parity test keeps them alive.

### Resolution
- **Each shipped broom draws its own texture sheet.** New optional `entity_texture` on
  `BroomDefinition`; `BroomVariantGeoModel` overrides `getTextureResource` from render-state data and
  leaves geometry and animation on the base asset, so there is still one rig and one set of clips.
  `tools/broom_model.py` packs the UV islands once and paints them once per scheme, so the sheets are
  interchangeable and swapping one cannot move an island. A Cleansweep is scuffed oak, a Comet pale
  birch, a Nimbus polished walnut over silver, a Firebolt ebony bound in red cord, the Supreme the
  same with gold runes and ember-tipped bristles, an Oakshaft near-black under iron.
- **A broom is now either painted or tinted, never both.** `getRenderColor` refuses to tint a broom
  that ships its own sheet — its colour is already final, and GeckoLib's render colour applies to the
  whole pass, so a tint would drag the band and the bristles with it. The generic `broom` stays on the
  shared greyscale sheet with a `wood_tint`, so the no-art path a datapack broom takes is the path a
  shipped broom exercises.
- **Nine new slot variants, 14 to 23.** Thick `oak` and `heavy_oak` shafts, a `collar_ring` and
  `iron_rings` binding, `swept` and `heavy` bristles, an `iron_peg` footstrap, a `maker_mark` and a
  `runic_band` accent — each because a broom in the roster had nothing to select. `shaft_racing` was
  retuned from 4 units to 3 at the binding: it was briefed as the slimmest handle and was the
  second-thickest. Thickness now carries the difference and sweep only seasons it.
- **You sit on the broom.** `BroomEntity.SEAT_OFFSET_Y` is derived from the rig (shaft centre 0.25
  blocks up) and the humanoid hip (0.75 blocks up) rather than from a hitbox height that has nothing
  to do with either. `BroomItem` lifts a broom by the same amount on mount, both when spawning one and
  when climbing onto one lying on the ground, so the rider lands where they were standing instead of
  half a block into the floor.
- **Item icons follow the wood.** Hue and lightness order now track each broom's shaft colour, spread
  across the range a 16px sprite can actually resolve — below roughly `#402f20` the shading collapses
  four brooms into the same smudge.
- **Four new parity tests.** Every `model_slots` value names a variant `BroomSlot` knows; every
  `entity_texture` resolves to a PNG at the declared sheet size; no definition sets both
  `entity_texture` and `wood_tint`; every broom in `BroomItemRegistry` has a definition file. 1271
  tests pass.

### Deferred
- The bright binding band still takes `wood_tint` on any broom that uses one, which is only the
  generic broom now. Exempting it needs a second render layer — unchanged from the earlier pass.
- `animation.broom.brake` and `animation.broom.summon` remain authored and unwired.

---

## 2026-08-26 — Bestiary harvest: a mastered creature finally yields something

### Audit
- **Discovery tiers were read by two things, and neither was loot.** `KnowledgeFormula` derived a stat
  from the count of discoveries and `OWLGradeCalculator` checked a tier index for an exam grade. No loot
  table, recipe, wand core or bench gate had ever asked what tier a player held, so MASTERED bought a
  number on a screen.
- **There was no rare tier for it to unlock even if something had asked.** All 27 entity loot tables
  drop exactly one base material apiece — `unicorn` → `unicorn_hair`, `phoenix` → `phoenix_feather`.
- **Five registered materials had no source at all.** `erumpent_horn`, `thunderbird_tail_feather`,
  `troll_whisker`, `wampus_cat_hair` and `veela_hair` are registered items, four of them wand cores
  tagged into WANDS and filed in the creative menu, and nothing in the world dropped any of them.
- **`BestiaryEntry` is at the codec ceiling.** Sixteen fields exactly, which is
  `RecordCodecBuilder.group`'s limit — extending it was not an option, so the side table the brief
  allows was also the only thing that compiles.
- **Deliberate non-fix: `veela_hair` stays sourceless.** There is no veela bestiary entry and no
  registered veela entity; veela are a *heritage*, not a mob. Giving the item a source needs a creature
  that does not exist, which is out of this pass's scope.

### Resolution
- **A tier now decides whether the rarest part of a beast comes off it.**
  `data/<ns>/bestiary/harvest/*.json` names a bestiary entry, an item, a minimum `DiscoveryTier`, a
  chance and an optional lockout. Five rules ship, four of which give a previously sourceless wand core
  its first way into the world.
- **Basic drops cannot disappear, by construction.** `BestiaryHarvestLootModifier` only ever appends to
  the rolled loot; it has no code path that inspects, filters or removes an existing stack. An unstudied
  player killing a unicorn gets exactly the same unicorn hair they got before this existed.
- **A rule that could never fire is a load failure.** `minTier: UNDISCOVERED` (which would gate nothing),
  `chance: 0`, a chance outside 0–1, an unknown item and an unknown tier are each refused by the codec
  with a message saying why. A rule naming a non-existent entry is dropped at index time with a warning.
- **Farming is throttled where it matters.** The lockout is per player *and* per bestiary entry, stored
  on the existing bestiary attachment so a relog cannot clear it, checked *before* the chance roll so a
  farm cannot burn attempts against it, and not started by a failed roll. A beast with no killer — burned,
  or killed by another mob — yields nothing, which is what a mob crusher produces.
- **`DiscoveryTier.CODEC` no longer throws on an unknown name.** It was
  `Codec.STRING.xmap(DiscoveryTier::valueOf, …)`, so a typo in any datapack field carrying a tier took
  the whole reload down instead of reporting one bad file. Found because a test asserted the refusal and
  got an exception instead of an error result. Now a `comapFlatMap` returning a parse error; the wire and
  disk format are unchanged.

### Deferred
- No new items. The five shipped rules use materials that already existed; a rare drop that needed a new
  item would have been content authoring rather than closing the edge.
- No recipe or bench gating on tier. The loot edge is closed; whether the *wandmaker* should also ask
  what you have studied is a separate decision.
- No GameTest. The gating is pure and fully unit-tested; the one seam a unit test cannot reach is the
  loot modifier's use of `LootContext`, and a GameTest for it would need a mob, a kill and a player —
  which is the manual check, not a cheap automated one.

---

## 2026-08-25 — Living heritage: three axes, two of them derived

### Audit
- **`Heritage` is species, not blood status.** PURE_BLOOD / HALF_BLOOD / MUGGLE_BORN / SQUIB /
  ADOPTED_MAGICAL are `HeritageVariant` values under `Heritage.WIZARDKIND`. The brief's terms map one
  level down from where they read as if they should.
- **Half of the Light/Dark axis already existed and had no light pole.** `DARK_CORRUPTION` is a 0–100
  attachment with a single write seam (`DarkCorruptionService`, which applies vocation scaling) and four
  live writers: Unforgivables, a worn Horcrux, the Resurrection Stone, Riddle's diary. There was no way
  to move it the other way and nothing that counted as being *light*.
- **Nothing read blood status after character creation.** Heritage set stats, size, form and ability
  grants at selection and was then inert as an identity.
- **Three numbers, no model.** Notoriety, corruption and blood status never met.
- **Deliberate non-fix: no fourth stored meter.** The brief's "Ministry standing" is the criminal
  record, which already exists and is already banded. Mirroring it into a standing record would have
  created a second source of truth that drifts the first time `TraceService` writes the original.
- **Deliberate non-fix: the mod ships no standing gates.** The gate mechanism is live, loaded and
  tested, but authoring one changes what existing players can reach in a save they have already spent
  points in. That is a content decision, and a datapack file turns it on with no code change.

### Resolution
- **Standing is three bipolar axes and exactly two new stored floats.** `TRADITION` (reformist ↔
  traditionalist) is stored. `ALIGNMENT` is `light − DARK_CORRUPTION`, so the existing corruption meter
  becomes the dark pole of a bipolar axis and keeps every one of its four writers rather than being
  replaced. `MINISTRY` is `rankCredit − notoriety`, a view over `PlayerMinistryRecord` — a pardon or a
  fine paid now moves an axis with no standing code running at all.
- **Conduct moves it, through datapack rules.** `data/<ns>/magical_deeds/*.json` maps an event to axis
  deltas. The three triggers are seams that already existed and were already used by other systems, so
  the whole system added no new event plumbing and no new tick work: the successful-cast line in
  `SpellCastService`, `TraceService.report`, and the earned path in `BestiaryDataHelper.setTier`.
  Five deeds ship.
- **A deed that could never fire is a load failure, not a silent no-op.** Empty effects, a zero or NaN
  delta, a write to the derived `ministry` axis, a negative `alignment` delta (that is corruption, and
  routing it here would skip vocation scaling), a `minTier` on a non-bestiary trigger — each is refused
  by the codec with a message saying why.
- **Bands, not floats, reach anything downstream.** Five steps per axis, thresholds as a percent of the
  bound, so gates and notices cannot flicker on a fractional drift and a server retuning the bound does
  not move where the bands sit.
- **The skill web can be gated on who you have become.** `SkillSystemAPI.evaluateUnlock` gained one
  branch, last in the order and skipped entirely when nothing is authored, returning `standing_unmet` —
  which the existing per-node refusal toast already knows how to translate.
- **The Character Sheet's Record tab now opens with the three meters**, each filled from the centre
  toward whichever pole the wizard has moved to, with the pole names under it. Drawn outside the Trace
  check: tradition and alignment survive the Ministry module being switched off.
- **One float bug found and fixed by its own test.** `100f * (60 / 100.0f)` is `60.000004`, so a wizard
  sitting exactly on the documented 60% threshold banded one step below what their sheet said. The
  percent arithmetic is in double now.

### Deferred
- No standing gate content. The mechanism ships inert by design; see the audit note.
- No decay. Standing is currently monotonic per deed — nothing pulls an axis back toward neutral over
  time. Whether it should is a design question, not an oversight.
- Heritage variant does not seed `tradition`. Everyone starts at 0 deliberately: heritage decides where
  you begin the game, conduct decides where you end it. A seeding pass would need a migration story for
  existing saves.

---

## 2026-08-25 — Ministry fines: notoriety stops being a closed loop

### Audit
- **Notoriety was a closed loop.** `TraceService` filed offences and `MinistryEvents` decayed them,
  and nothing else in the mod read the result. `WantedLevel.dispatchesAurors()` and
  `aurorsPerDispatch()` had exactly one caller between them — an announcement string. Committing a
  crime moved a number nobody could see toward a consequence that did not exist.
- **The paperwork half of the law model had no penalty at all.** `MagicalOffence` splits crimes into
  `arrestable` (Azkaban) and everything else, and the everything-else side is documented in the enum
  itself as "a fine, not Azkaban" — but there was no fine anywhere in the mod. Casting an Unforgivable
  at least raised heat; Apparating unlicensed or transforming unregistered filed a line on a record
  nobody could read and cost nothing.
- **`ApparitionServerLogic:317` already said so**: *"The whole enforcement half of this is deliberately
  absent: no fine, no summons, no patrol, no way to…"*.
- **The Ministry record was never synced to any client.** There is no `network/ministry` package;
  `PlayerStateSyncService.syncFullLoginState` did not mention it. The only way to see your own criminal
  record was a chat command, and the Character Sheet had three tabs, none of them about the Ministry.
- **Deliberate non-fix: Aurors and Azkaban sentences stay unbuilt.** They are the other half of the same
  loop and the natural next pass, but they need a new GeckoLib entity, combat AI, dispatch scheduling and
  a teleport into the existing structure — none of which can be verified without a running world. This
  pass took the half that is pure server arithmetic against data that already exists.
- **Deliberate non-fix: `gui.wizards_and_beasts.character_sheet.effects{,.none,.more}` are referenced by
  `CharacterSheetScreen` and absent from `en_us.json`.** They belong to another pass's uncommitted work on
  the same screen and slip past `LangParityTest` because its regex only matches single-line
  `translatable("…")` calls; these are wrapped across two lines. Reported rather than invented, because
  the copy is that pass's to choose.

### Resolution
- **A paperwork offence now costs money.** `MagicalOffence` carries a `fineKnuts` tariff — two Galleons
  for unlicensed Apparition, ten for an unregistered Animagus — and the split is exclusive by
  construction: an arrestable offence carries no fine because Azkaban is its penalty, and a fineable one
  is never arrestable. `MinistryFineTest` pins that as an invariant over `values()`, so a new offence
  cannot be added that is punished twice or not at all.
- **The Ministry bills Gringotts, not the pocket.** `MinistryFines` debits `PlayerVaultData`, taking
  what is there and leaving the remainder on the books. A standing debt is swept every five seconds by
  `MinistryEvents`, so a player who deposits at Gringotts walks out having paid. Previously the vault was
  reachable only by the player choosing to spend; the world can now reach into it.
- **An unpaid fine freezes notoriety decay and slowly heats instead.** `TraceService.decay` used to shed
  heat for anyone not fugitive and not serving; a debtor is now a third case. The heat is capped at
  `WantedLevel.WANTED`'s threshold by `FineSchedule.DEBT_HEAT_CEILING`, so ignoring a two-Galleon ticket
  can make you sought for questioning and can never make you Undesirable No. 1.
- **A pardon now settles the debt.** It did not exist to settle before; leaving it standing would have
  left a pardoned wizard permanently unable to cool, which is the opposite of a pardon.
- **The record reaches the client for the first time.** `MinistryRecordSyncS2CPayload` pushes the player's
  own record — and only their own — on login and on every mutation that changes it, through the existing
  `MinistryRecords.mutate` seam. `ClientMinistryRecordState` caches it and drops it on disconnect.
- **The Character Sheet has a Record tab.** Wanted band, a notoriety meter, the outstanding fine and the
  permanent file. It distinguishes a clean record from a world where the Trace is switched off, which the
  record alone cannot express.
- **`/wandb ministry fine`** shows what you owe (no rank needed — it is your bill), `fine pay [knuts]`
  settles it now, and `fine waive <player>` remits it at Magical Law Enforcement rank or operator.
- **`ministryFineScalePercent`** scales or disables the whole tariff without touching the criminal record.

### Deferred
- Aurors, arrest and sentence ticking — Ministry plan Phase 2, unchanged.
- Wand registration and the registry service — Phase 3, unchanged.
- Fines for arrestable offences: deliberately none. They are answered with time, and the sentence system
  that will answer them does not exist yet.

---

## 2026-08-21 — Marauder's Map: from entity radar to a charted world

### Audit
- **The map had no terrain at all.** `MaraudersMapScreen` drew `demo_background.png` and coloured
  `fill()` squares. There was no biome, no relief, no discovery, no persistence — 869 lines total,
  all of it a live entity sweep.
- **The map bound to the holder's position on first use and never moved again.** `initializeIfNeeded`
  wrote `BoundX/BoundZ` once; `use()` re-read them forever. Walk 200 blocks from where you first
  unfolded it and the map was blank, permanently.
- **A dimension change silently killed the sweep and left the screen open.** `onPlayerChangedDimension`
  called `removePlayer`; the client was never told, so the screen sat showing frozen Overworld dots
  while the player stood in the Nether.
- `MaraudersMapScreen.dimension` was stored and never read — the screen could not tell which
  dimension it was drawing.
- `renderEntities` bounds-checked dots against the whole panel rect rather than the content rect, so
  dots drew over the frame and the header.
- `MapSyncS2CPayload.encode` wrote an unbounded count while `decode` enforced `MAX_MAP_ENTRIES`. A
  sweep over the cap desynchronises the connection mid-stream rather than dropping one packet.
- The entity sweep ran `minY..maxY`: a player hiding in a cave, or a base sixty blocks down, was
  fully revealed.
- Tooltips were hand-drawn with `fill()` and ignored screen edges. Almost every string was hardcoded
  English. `MapClientHandler` duplicated what `ClientPayloadHandlers` already forwarded.
- **Deliberate non-fix:** the mod has no custom biomes (`PocketBiomes.SELECTABLE` is fifteen vanilla
  ids), so "custom mod biomes have appropriate map representations" is satisfied by covering vanilla
  and falling back gracefully. `MapStyles.DEFAULT_STYLE` is plain ground rather than a magenta error
  tile, because an unstyled biome from another mod is the normal state of a modded world.

### Resolution
- **The map charts what it is carried across, and remembers it.** `MapSurveyor` samples five points
  per chunk from `WORLD_SURFACE`/`OCEAN_FLOOR` on *already-loaded* chunks only — it never calls
  `getChunk`, so a map cannot chart a continent from a chair, and cannot generate terrain on the
  server thread. A tile is one biome plus one of seven `MapRelief` bands; caves, ravines, ore and
  buried bases are not representable in that format at all, which is a stronger guarantee than a
  filter. Steady state is free: a surveyed tile is never revisited.
- **Exploration is persistent, shared and server-authoritative.** `MaraudersMapAtlasStore` is a
  `SavedData` keyed by a `MapId` on the stack, so the atlas survives logout, restart, death and
  dimension change for the same reason the world does. Hand the map to someone on its trusted list
  and they open the same parchment, already charted. The alternative — the atlas in `CUSTOM_DATA` —
  would re-serialise and re-ship a continent every time the holder picked up a cobblestone.
- **The view follows the holder.** `MapSession` splits the two scopes the old code had conflated:
  terrain and markers are the unbounded atlas, and the moving dots are a live sweep with a real
  radius centred on the holder *right now*. A dimension change re-points the session and tells the
  client instead of abandoning it.
- **Terrain is an interpretation, not a screenshot.** `MapTerrainRenderer` draws one hand-drawn
  sprite per chunk — trees for a wood, wave lines for water, hachures for a ridge — from a single
  greyscale sheet tinted per biome. Four variants per terrain type, picked by a hash of the tile's
  own world coordinates, so a forest is not the same six trees stamped in a perfect grid.
  `MapView.lodStep` collapses tiles into power-of-two blocks as the player zooms out, so the blit
  count stays roughly constant and the far view is *cheaper* than the near one.
- **Places have to be earned.** A generated structure whose highest piece is at or above sea level is
  marked when the holder walks over it; anything wholly below it has to be entered. That one rule
  puts Azkaban and villages on the page and keeps strongholds, mineshafts and the Chamber of Secrets
  off it — no per-structure flag to maintain, and `reveal: entered` is available when a pack wants
  to be explicit.
- **Hogwarts is found by being built.** The mod does not generate a castle; it ships the stone. New
  `#wizards_and_beasts:landmark/*` tags (generated by filtering `LocationBlockHelper.allBlocks()` on
  registry-name prefix, so a new marble variant joins its landmark for free) let
  `MapDiscoveryRule.FromBlocks` count palette blocks per chunk. Lay 220 blocks of Hogwarts stone in
  one chunk and a castle appears on the map, labelled, drawn larger than anything else on the page.
  Hogsmeade, Diagon Alley, Gringotts and the Ministry work the same way at their own thresholds.
- **Waypoints, with the authorization written once.** `MapMarkerService` is the single gate: a
  discovery is not a player's to rewrite, another player's pins never reach the wire, and a refused
  edit says why rather than doing nothing. Right-click the parchment to plant a pin where you are
  pointing; eight icons, rename, hide, erase, capped at 64 per player.
- **Footprints.** The positions were already arriving for the dots, so a trail is a short client-side
  history of packets the screen already had: no extra sweep, no extra packet, no server state. It is
  the one thing on this map no other map mod has.
- **Server decides what is there; the resource pack decides what it looks like.** Biome tiles and
  marker symbols are client resources under `assets/.../map_biome_style/` and
  `map_marker_style/`, on the *resource* reload cycle. Discovery rules are datapack files under
  `data/.../map_discovery/`. Nothing about appearance is ever on the wire.
- **Art:** `tools/map_textures.py` generates nine sheets — 32 terrain types x 4 variants, 24 marker
  symbols, the holder's arrowhead, footprints, a compass rose, paper grain, fold creases and the
  control icons. `tools/gui_chrome.py` gains a sixth skin, `marauders_map`: aged parchment, scuffed
  leather, pocket brass, with a quill-nib seal.
- **The held item was rebuilt from scratch, because none of it worked.** The geometry declared a
  64x64 texture and addressed UVs out to (40, 37) while `textures/item/marauders_map.png` was a
  **16x16 flat inventory icon** — the model was sampling almost entirely outside its own skin. The
  item model was `{"parent": "builtin/entity"}` with **no display block at all**, so an 8x1x12 model
  rendered at raw model scale in the GUI, in hand, on the ground and in a frame. The two flaps
  hinged on their *outer* edges, so they swung away from the centre rather than closing over it — a
  tri-fold folded inside out — and were half-thickness slabs sitting on the body at the same height,
  z-fighting it wherever they overlapped.
- **The animation set could not have worked either.** `folded_idle` was a static hold with no motion
  in it; `unfolding` was 0.5 s of linear interpolation; and because a triggered clip returns control
  to the controller's default when it ends, and the default *was* `folded_idle`, the map snapped
  shut in the same breath it finished opening. `open_idle` existed in the file and nothing
  registered it.
- **`tools/map_item_model.py` now generates all four artefacts from one panel table.** The sheet is
  authored flat in **XY with its thickness along Z**, so the face normal is +Z — the direction every
  display context already treats as toward the viewer — and the nine transforms became poses rather
  than 90-degree corrections. The fold pivots are *solved*, not typed: a half turn about `p` maps a
  coordinate to `2p - x`, so the hinge that lands a flap on the centre panel is fully determined,
  and each flap is staggered one thickness proud so the closed map is three distinct sheets. Both
  axes of that solve are asserted at generation time, with a note about the crossed pairing — a half
  turn reverses an interval, so asserting the uncrossed one fails on correct geometry.
- **Four clips and a state to be in:** closed, opening, open, closing. Opening chains into
  `open_idle` so it stays open; closing eases *in* rather than out, because closing is a deliberate
  act. The fold plays on `MapCloseC2SPayload` and nowhere else, since that is the only moment the
  hand is visible again — the logout, respawn and dimension paths close the session silently.
- **`tools/map_item_preview.py`** renders the geo with its real texture at its real display
  transforms, which is what caught the remaining problems: an inverted depth test in the first draft
  of the preview itself, and the fact that the folded stack, not the open sheet, is what the GUI
  slot ever shows. `tools/audit/s13_map_resources.py` now cross-checks geometry against skin,
  animation against bones, and clip names against the controller — it fails on the missing display
  block, which is the exact bug that shipped.
- **Two bugs the mockup caught that a compile could not.** `McStylePanel.drawNineSlice` cuts an 8px
  border out of a 32px sprite, so a widget under 18px has no interior left and renders as four
  disconnected corners around a hole — and `layout.s()` drops below 1.0 on a 1920x1080 window at GUI
  scale 4, pushing an 18px design size back under the floor. Every skinned widget on the screen now
  goes through `skinned()`, which holds the absolute floor the sprite's non-scaling border requires.

### Deferred
- Floo hearths and Apparition points have marker styles, translation keys and legend entries, but
  nothing plants them yet: both live in systems the surveyor does not read
  (`FlooNetworkManager`, `PlayerApparitionPoints`). The discovery side is one rule variant away.
- Multiplayer player-tracking policy is deliberately "everyone within 128 blocks, invisibility
  included, only for someone on the map's own trusted list" — that is the artefact, and a Marauder's
  Map fooled by a Disillusionment Charm is not one. Revisit only if server operators ask.
- `LangParityTest` and `StatReadoutTest` fail on this branch for 58 keys owned by the in-flight Floo
  call service and character-sheet stats work. Not this pass's, and not touched.

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