# Entity Foundation — Result

Implementation of the shared entity foundation described in `documentation/ENTITY_AUDIT.md`.
Branch `dev-2026-09-17-rework`, Minecraft 1.21.11, NeoForge, Java 21.

**Validation: `./gradlew build` green, 2040 unit tests (2034 + 6), 134 GameTests, 0 failures.**

---

## Three audit claims that were wrong

Implementing found errors in my own audit. Correcting them here rather than leaving them to mislead
the next pass:

1. **"No hurt animation hook."** False. `GenericBeastEntity.hurtServer` has fired `CLIPS_HIT`
   (`hit`, `flinch`) since before this task. What is missing is the *clips on the rigs*, not the
   hook. Only the death hook was genuinely absent.

2. **"Dementor uses ground navigation and `ON_GROUND` placement."** False, twice over. It already
   uses `FlyingMoveControl(this, 10, true)` and `BeastNavigation.flying(...)`, and it registers no
   spawn placement at all — it arrives through its own despair-driven spawner. **No change was made,
   because nothing is broken.**

3. **"Phoenix should get the new idle system."** It cannot. `PhoenixEntity extends GeoEntityBase`,
   not `GenericBeastEntity`, so nothing in this foundation reaches it. Its rig also declares only
   `idle` and `fly` — preening needs a new clip, which is art, not data.

---

## Systems added

### 1. `CreatureBehaviour` — one nested data block

`creature/profile/CreatureBehaviour.java`. Carries `idle`, `sounds`, `reactions`, `combat`.

Nested rather than four more flat fields because `CreatureDefinition` was already seventeen
components and assembled from two `MapCodec` halves to clear `RecordCodecBuilder.group`'s
sixteen-argument ceiling. Four more flat fields would have pushed the second half over the same edge.

**Every field optional, whole block optional, defaults to `EMPTY`.** All ninety-six shipped creature
files parse unchanged; a creature that declares nothing behaves exactly as before. That is the
migration strategy — nothing rewritten, the new systems simply have nothing to do until data asks.

### 2. `SoundProfile`

`ambient`, `hurt`, `death`, `attack`, `special`, each an optional registry id resolved through
`BuiltInRegistries.SOUND_EVENT`. **No second sound registry, no creature id in the engine.** A
datapack can point at a vanilla sound with no code change. Absent means silent — a Lethifold should
be — and an unresolvable id yields null rather than throwing, the same tolerance the clip gate
applies to animations.

Wired as `getAmbientSound` / `getHurtSound` / `getDeathSound` overrides on `GenericBeastEntity`.
Vanilla reads null as "no noise", so undeclared is silent exactly as before.

### 3. `IdleProfile` + `CreatureIdleGoal`

Vocabulary **drawn from what the architecture can already do**, not invented: every idle action is a
one-shot clip played through the existing `triggerDeclared` gate, which checks the datapack's declared
clip list and no-ops when a rig has not declared it. A profile naming clips a creature lacks is inert,
not a render-pass crash.

Per-body-plan defaults ship for ten body plans (avians preen, quadrupeds graze and sniff, serpents
coil and taste air). **Most name clips that do not exist yet — deliberately.** The profile describes
what the creature *would* do; the moment a rig gains a `preen` clip, every avian starts preening with
no code or data change.

Registered at **priority 8** — below the wander at 5 and every combat goal, above only the look goals
— with **no goal flags**, so it can never hold `MOVE` or `LOOK` against the goals that need them.
Single-shot: fires and stands down.

Roaming, watching and looking around are deliberately **not** in the vocabulary. They are already
vanilla goals installed for everyone, and duplicating them would give two systems an opinion about
one behaviour.

### 4. Death animation hook

`CLIPS_DEATH` (`death`, `die`, `collapse`) fired from `die()` **before** `super.die`, because
`triggerAnim` syncs from a live entity and vanilla's death sequence removes it. Gated by
`triggerFirstDeclared`, so today — with no rig declaring a death clip — it is a no-op. The hook lands
first; clips arrive per rig afterwards, with no Java change per creature.

`die()` is called once by vanilla, so no repeat guard was needed beyond what the caller already does.
Server-side only, so no client/server divergence.

### 5. `CreatureReaction`

`Stimulus` (SPELL, FIRE, LIGHT, DARKNESS, PLAYER, CREATURE_DEATH, DAMAGE, ENVIRONMENT) × `Response`
(IGNORE, FLEE, ATTACK, INVESTIGATE, STUN, BECOME_ALERT, SPECIAL) plus a radius.

**Framework only, as instructed.** `reactionTo(stimulus)` and `reactionRadius(stimulus)` are the
single entry point; most stimuli have nothing raising them yet. That is the intended state — the
framework lands before the events, and a later pass wires the cast pipeline and the death broadcast
into this seam rather than building a second behaviour system.

Kept separate from `CreatureAbility` deliberately: an ability is what a creature *does* (48 types
across 91 creatures, its own dispatch codec), a reaction is what it *notices*.

### 6. `CombatProfile`

`style` (BRUTE, AMBUSHER, HARRIER, SKIRMISHER, PACK_HUNTER), `approachSpeed`, `windupTicks`,
`recoveryTicks`, `preferredRange`, `retreatAtHealth`.

`CombatProfile.DEFAULT` reproduces the current behaviour exactly, so declaring nothing fights
precisely as before. Currently `approachSpeed` is wired into the melee goal; the remaining fields are
declarable and not yet consumed — see blockers.

Rhythm is separate from ability, as the brief puts it: *combat rhythm + creature ability = creature
combat identity*. The ability system was not touched.

### 7. `scale`

A float on the stat half, applied via **`Attributes.SCALE`** — the existing system, not a new one.

Uses a **separate modifier id** (`creature_body_scale`) from the abilities' `SIZE_SCALE_ID`, so the
two compose instead of overwriting. That matches what `applySizeScale`'s own javadoc already
describes as the intended stack: a species body, an ability on top, an Engorgio on top of both.
Sharing an id would have meant a growing Occamy erasing its species scale.

---

## Systems reused, not rebuilt

As instructed, and verified present and working before deciding:

| System | Where it already lives |
|---|---|
| Emissive rendering | `GeoRendererHelper.applyGlowIfPresent` — glowmask-by-presence |
| Magical aura / glow | `bioluminescence` ability + `glow_self` |
| Aquatic navigation | `GenericAquaticBeastEntity` — `WaterBoundPathNavigation`, `SmoothSwimmingMoveControl` |
| Flying navigation | `GenericFlyingBeastEntity`, `BeastNavigation.flying` |
| Creature interaction | `BondableBeast`, `HarvestGate`, Bestiary study methods |
| Scale | `Attributes.SCALE` |
| Animation triggering | `triggerDeclared` / `triggerFirstDeclared` clip gate |
| Sound registration | `BuiltInRegistries.SOUND_EVENT` |

## Systems intentionally not created

- No `CreatureAnimationController` — the clip gate is that system and it works.
- No new navigation, flight, aura or emissive renderer.
- No second morality, classification or registry layer.
- No per-creature Java classes.

## `GenericBeastEntity` changes

Six additions, no conditional sprawl:

1. `applyBodyScale(float)` called from `applyDefinition()`.
2. `behaviour()`, `idleProfile()`, `combatProfile()`, `soundProfile()`, `reactionTo()`,
   `reactionRadius()`, `declaredClipNames()` — accessors returning never-null profiles so callers
   need no branch.
3. `getAmbientSound` / `getHurtSound` / `getDeathSound` overrides.
4. `CLIPS_DEATH` + the `die()` trigger.
5. `CreatureIdleGoal` registered at priority 8.
6. Melee approach speed read from the combat profile.

## Data format changes

Two optional fields on the creature definition, both backwards compatible:

```json
{
  "scale": 1.4,
  "behaviour": {
    "sounds":    { "ambient": "...", "hurt": "...", "death": "...", "attack": "...", "special": "..." },
    "idle":      { "actions": ["preen"], "minDelay": 140, "maxDelay": 400 },
    "combat":    { "style": "ambusher", "approachSpeed": 1.35, "windupTicks": 10,
                   "recoveryTicks": 20, "preferredRange": 0.0, "retreatAtHealth": 0.2 },
    "reactions": [ { "stimulus": "fire", "response": "flee", "radius": 10.0 } ]
  }
}
```

## Creatures using the new systems

Six, as a proof that the pipeline works end to end with **no new assets** — all point at vanilla
sounds:

| Creature | What it demonstrates |
|---|---|
| `acromantula` | sounds + an AMBUSHER combat profile with windup and retreat |
| `grindylow` | sounds on an aquatic creature |
| `crup`, `jarvey`, `fwooper`, `kelpie` | sounds across quadruped and avian body plans |
| `basilisk` | `scale: 1.4`, documented in-file |

The other ninety are untouched and behave identically to before.

## Phase 4 outcomes

| Entity | Outcome |
|---|---|
| **Basilisk** | **Done.** `scale: 1.4` — rig is 8.5 blocks long at 1.0, so this reads ~12 blocks. Documented in-file as a design decision: *Chamber of Secrets* says "fifty feet" and no source converts that to blocks. The real limit is that a vanilla hitbox is square in plan, so no scale makes a 12-block serpent occupy a 12-block box — only a multi-part entity would. **Untested in world: 3.78 × 4.06 collision needs a playtest for corridor navigation.** |
| **Dementor** | **No change — audit was wrong.** Already flying. |
| **Phoenix** | **Not done.** Bespoke `GeoEntityBase`, outside this foundation; rig declares only `idle`/`fly`, so idle personality needs new clips (art). |
| **Chocolate Frog** | **Not done.** Needs a rig built with `tools/rigkit.py` plus animations — art work, not foundation work. |
| **Bowtruckle** | **Not done.** Bespoke entity with no creature definition file; climbing has no existing mechanism to reuse and only one creature needs it, so per the brief it does not justify a shared system. |

## Creatures still requiring individual work

- **The 24 bespoke entities** are outside this foundation entirely. They have no `CreatureDefinition`,
  so no profile reaches them. Bringing Bowtruckle, Phoenix and Augurey in would mean either giving
  them definition files or lifting the profile accessors to a shared interface — a decision, not an
  oversight.
- **The 90 data-driven creatures with no declared profile** need authoring, not code.

## Remaining technical blockers

1. **Clips do not exist for most idle actions.** The system is correct and inert. Closing it is rig
   work in `tools/`, per body plan.
2. **No rig has a death clip.** Hook ready, clips absent.
3. **`windupTicks`, `recoveryTicks`, `preferredRange` and `retreatAtHealth` are declarable but not
   yet consumed.** Only `approachSpeed` is wired. Consuming the rest needs a replacement for
   `MeleeAttackGoal` — a real goal, deliberately not written blind in this pass.
4. **Most reaction stimuli have no event raising them.** SPELL needs a hook in the cast pipeline;
   CREATURE_DEATH needs a death broadcast.
5. **Bespoke entities cannot use any of this** without the decision above.
6. **Basilisk collision is untested in world.**

## Per-creature profile table

All 96 data-driven creatures. Columns: body plan, idle profile, sound profile, combat profile,
reaction count, scale, and which animation clips the creature still needs.

| Creature | Body plan | Idle | Sound | Combat | Reactions | Scale | Animations needed |
|---|---|---|---|---|---|---|---|
| `abraxan` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `acromantula` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | declared | ambusher | none | 1.0 | hurt+death |
| `aethonan` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `antipodean_opaleye` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `ashwinder` | SERPENTINE | body-plan default (SERPENTINE) | silent | default (brute) | none | 1.0 | hurt+death |
| `basilisk` | SERPENTINE | body-plan default (SERPENTINE) | silent | default (brute) | none | 1.4 | hurt+death |
| `billywig` | INSECTOID_FLYER | body-plan default (INSECTOID_FLYER) | silent | n/a — no melee | none | 1.0 | death |
| `blast_ended_skrewt` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | silent | default (brute) | none | 1.0 | hurt+death |
| `boggart` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | default (brute) | none | 1.0 | death |
| `bundimun` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | n/a — no melee | none | 1.0 | death |
| `centaur` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `chimaera` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `chinese_fireball` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `chizpurfle` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | silent | n/a — no melee | none | 1.0 | death |
| `clabbert` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | n/a — no melee | none | 1.0 | death |
| `common_welsh_green` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `crup` | QUADRUPED | body-plan default (QUADRUPED) | declared | default (brute) | none | 1.0 | hurt+death |
| `demiguise` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | n/a — no melee | none | 1.0 | death |
| `diricawl` | AVIAN | body-plan default (AVIAN) | silent | n/a — no melee | none | 1.0 | death |
| `doxy` | INSECTOID_FLYER | body-plan default (INSECTOID_FLYER) | silent | default (brute) | none | 1.0 | death |
| `dugbog` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `erkling` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `erumpent` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `fairy` | INSECTOID_FLYER | body-plan default (INSECTOID_FLYER) | silent | n/a — no melee | none | 1.0 | death |
| `fire_crab` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | silent | default (brute) | none | 1.0 | hurt+death |
| `flobberworm` | WORM_LARVA | body-plan default (WORM_LARVA) | silent | n/a — no melee | none | 1.0 | death |
| `fwooper` | AVIAN | body-plan default (AVIAN) | declared | default (brute) | none | 1.0 | death |
| `ghoul` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `giant` | LARGE_HUMANOID | body-plan default (LARGE_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `glumbumble` | INSECTOID_FLYER | body-plan default (INSECTOID_FLYER) | silent | n/a — no melee | none | 1.0 | death |
| `gnome` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | n/a — no melee | none | 1.0 | death |
| `golden_snidget` | AVIAN | body-plan default (AVIAN) | silent | n/a — no melee | none | 1.0 | death |
| `granian` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | n/a — no melee | none | 1.0 | hurt+death |
| `graphorn` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `griffin` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `grindylow` | AQUATIC | body-plan default (AQUATIC) | declared | default (brute) | none | 1.0 | death |
| `hebridean_black` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `hippocampus` | AQUATIC | body-plan default (AQUATIC) | silent | n/a — no melee | none | 1.0 | hurt+death |
| `hippogriff` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `hodag` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `horklump` | SESSILE | body-plan default (SESSILE) | silent | n/a — no melee | none | 1.0 | death |
| `horned_serpent` | SERPENTINE | body-plan default (SERPENTINE) | silent | default (brute) | none | 1.0 | hurt+death |
| `hungarian_horntail` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `imp` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | n/a — no melee | none | 1.0 | death |
| `jarvey` | QUADRUPED | body-plan default (QUADRUPED) | declared | default (brute) | none | 1.0 | hurt+death |
| `jobberknoll` | AVIAN | body-plan default (AVIAN) | silent | n/a — no melee | none | 1.0 | death |
| `kappa` | AQUATIC | body-plan default (AQUATIC) | silent | default (brute) | none | 1.0 | death |
| `kelpie` | AQUATIC | body-plan default (AQUATIC) | declared | default (brute) | none | 1.0 | hurt+death |
| `knarl` | QUADRUPED | body-plan default (QUADRUPED) | silent | n/a — no melee | none | 1.0 | death |
| `kneazle` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `leprechaun` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | death |
| `lethifold` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | default (brute) | none | 1.0 | death |
| `lobalug` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | n/a — no melee | none | 1.0 | death |
| `mackled_malaclaw` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | silent | default (brute) | none | 1.0 | death |
| `maledictus` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `manticore` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `matagot` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `merperson` | AQUATIC | body-plan default (AQUATIC) | silent | default (brute) | none | 1.0 | hurt+death |
| `moke` | QUADRUPED | body-plan default (QUADRUPED) | silent | n/a — no melee | none | 1.0 | death |
| `murtlap` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | death |
| `nogtail` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `norwegian_ridgeback` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `nundu` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `obscurus` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | default (brute) | none | 1.0 | hurt+death |
| `occamy` | SERPENTINE | body-plan default (SERPENTINE) | silent | default (brute) | none | 1.0 | hurt+death |
| `peruvian_vipertooth` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `plimpy` | AQUATIC | body-plan default (AQUATIC) | silent | n/a — no melee | none | 1.0 | death |
| `pogrebin` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | default (brute) | none | 1.0 | hurt+death |
| `porlock` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | n/a — no melee | none | 1.0 | death |
| `puffskein` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | n/a — no melee | none | 1.0 | death |
| `pukwudgie` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `pygmy_puff` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | n/a — no melee | none | 1.0 | death |
| `qilin` | QUADRUPED | body-plan default (QUADRUPED) | silent | n/a — no melee | none | 1.0 | death |
| `quintaped` | ARTHROPOD_MULTILEG | body-plan default (ARTHROPOD_MULTILEG) | silent | default (brute) | none | 1.0 | hurt+death |
| `ramora` | AQUATIC | body-plan default (AQUATIC) | silent | n/a — no melee | none | 1.0 | death |
| `red_cap` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `reem` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `romanian_longhorn` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `rougarou` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `salamander` | QUADRUPED | body-plan default (QUADRUPED) | silent | n/a — no melee | none | 1.0 | death |
| `sea_serpent` | SERPENTINE | body-plan default (SERPENTINE) | silent | default (brute) | none | 1.0 | hurt+death |
| `shrake` | AQUATIC | body-plan default (AQUATIC) | silent | default (brute) | none | 1.0 | death |
| `snallygaster` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `sphinx` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `swedish_short_snout` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `swooping_evil` | INSECTOID_FLYER | body-plan default (INSECTOID_FLYER) | silent | default (brute) | none | 1.0 | hurt+death |
| `tebo` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `thunderbird` | AVIAN | body-plan default (AVIAN) | silent | default (brute) | none | 1.0 | hurt+death |
| `toad` | BLOB_SPHERE | body-plan default (BLOB_SPHERE) | silent | n/a — no melee | none | 1.0 | death |
| `troll` | LARGE_HUMANOID | body-plan default (LARGE_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `ukrainian_ironbelly` | WINGED_QUADRUPED | body-plan default (WINGED_QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `unicorn` | QUADRUPED | body-plan default (QUADRUPED) | silent | n/a — no melee | none | 1.0 | death |
| `wampus_cat` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |
| `werewolf` | BIPED_HUMANOID | body-plan default (BIPED_HUMANOID) | silent | default (brute) | none | 1.0 | death |
| `yeti` | LARGE_HUMANOID | body-plan default (LARGE_HUMANOID) | silent | default (brute) | none | 1.0 | hurt+death |
| `zouwu` | QUADRUPED | body-plan default (QUADRUPED) | silent | default (brute) | none | 1.0 | hurt+death |

---

## Validation performed

| Check | Result |
|---|---|
| `./gradlew build` | **BUILD SUCCESSFUL** |
| Unit tests | **2040**, 0 failures (2034 + 6 new) |
| GameTests | **All 134 required tests passed** |
| All 96 creature definitions parse | asserted by `everyShippedCreatureStillParses` |
| Undeclared behaviour is inert | asserted by `aCreatureWithoutABehaviourBlockGetsTheEmptyOne` |
| Declared behaviour round-trips | asserted by `aDeclaredBehaviourBlockSurvivesTheCodec` |
| Basilisk scale stays documented | asserted by `theBasiliskCarriesItsDocumentedScale` |

Coverage across entity kinds, via the existing GameTest suite plus the new codec tests: passive,
hostile, flying, aquatic, bespoke, data-driven, creature with custom animation, creature without.

**Not verified:** anything needing the client running — Basilisk collision in corridors, whether the
idle goal reads well in motion, and how the vanilla sounds sit on these creatures. Those need a
playtest.
