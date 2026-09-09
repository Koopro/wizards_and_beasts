# Known Issues — 0.1.0-alpha.1

Player-facing limitations of the current build. Derived from
[`ModuleDefaults`](../src/main/java/at/koopro/wizardsandbeasts/module/ModuleDefaults.java)
and the shipped data, not from design intent.

CI names this file in its RC-gate failure notice
([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)): when a gate fails, record the
reproducible failure here before retagging.

---

## 1. What the module states mean

| State | Meaning for a player |
|---|---|
| `ENABLED` | On, and presented as finished for the alpha. |
| `PREVIEW` | Reachable and playable, but **not finished** — content, art or balance is incomplete. |
| `DISABLED` | Off. Content stays registered; access is refused. An operator can turn it on with `/wandb admin module set`. |
| `COMING_SOON` | Off, and **out of an operator's reach**. Nothing ships in this state today. |

`ModuleManager` gates *access*, not *registration*. Blocks and items in an off module still
exist in the registry, which is why a `/give` can hand you something the module hides.
`ModuleContentIndex` **fails open**: content that carries no module tag stays accessible.

## 2. `PREVIEW` modules — reachable, unfinished

| Module | What is incomplete |
|---|---|
| `PROFICIENCY` | Per-spell proficiency accrues and scales casts. The stacking formula is now single-owned, clamped and test-locked (§4c, and the spell-power section of `DEVELOPER_REFERENCE.md`); the **numbers** in it are still unbalanced, which is why this stays `PREVIEW`. |
| `PLAYER_ABILITIES` | The ability framework (wheel, quick slots, Apparition/Legilimency/Animagus/Obscurial) works; the roster is small and several abilities have placeholder VFX. |
| `CREATURES` | 106 creatures are registered; **21 are alpha-ready and 82 are still on a placeholder box rig** (§4). Natural spawning defaults to the alpha slice only (`creatureNaturalSpawns`), and the creative tab hides placeholder spawn eggs (`showPlaceholderSpawnEggs`). Registration is never gated. |
| `BESTIARY` | **107 entries ship** with full lore, under `data/wizards_and_beasts/bestiary/entries/`. The screen works and entries unlock on sight (§4b). What is thin is the art: only the 32px procedural portraits exist, and the detail pane has no per-entry illustration. |
| `PLAYER_ANIMATION` | The pose layer runs, but wave 1 ships one proving clip plus a command-driven flight pass. Poses may read wrong in edge cases (swimming, riding, elytra). |
| `OWLS` | The O.W.L. **examination** system — grades, subjects, professions, the examination desk. It works; the grade curve is unbalanced and the profession roster is thin. The **owl post** (§5d) is new and shares this module's gate. |
| `PLAYER_STATS` | Stats are real and read by casts and training, but the derived values and the HUD are provisional. |
| `HERITAGE` | **3 of 10 heritages are alpha-available** — Wizardkind, Werewolf, Obscurial. The other seven (Goblin, House-Elf, Veela, Giant, Centaur, Vampire, Merpeople) are defined and shown but not selectable, and the transformation triggers for them do not exist. |

## 3. `DISABLED` modules — off by default

| Module | Why |
|---|---|
| `AZKABAN` | The fortress jigsaw start pool points at `data/wizards_and_beasts/structure/azkaban.nbt`, a **225-byte placeholder** with no reachable content. No Dementor spawn table, no loot. Turning it on generates an empty crag. |
| `CHAMBER_OF_SECRETS` | Same failure mode: `structure/chamber_of_secrets/chamber.nbt` is a **1.3 KB placeholder**. `ChamberOfSecretsStructure` gates generation on the module, so a default install places no chamber. |
| `MINISTRY` | Law enforcement — the Trace, notoriety, Auror dispatch, sentencing, licences, ranks. Never enabled in any shipped build; preserved off so existing worlds do not suddenly gain a police force. With it off, illegal magic is legal. |
| `DARK_ARTS` | Dark-arts gating layer. Off, so the content it would gate falls back to whatever other module owns it. |

Both structure modules are deliberately `DISABLED` rather than `COMING_SOON`: `COMING_SOON`
would put them out of an operator's reach, which is a behaviour change nobody asked for.

**All four are proven off, not assumed off.** `AzkabanStructureGateTest` and
`ChamberOfSecretsStructureGateTest` each drive their structure's `findGenerationPoint` through a
context made of nulls: a gated structure must return before it touches the world, so the empty result
is the proof. `DisabledModuleContentGateTest` covers the other two routes into a disabled module —
that no recipe produces an item owned by a module that ships off without carrying the
`wizards_and_beasts:module_enabled` condition for it (four do, and all four are gated), and that
Azkaban and the Chamber own no items at all, so no crafting loop can point at them however the
recipes are written. Creative tabs are filtered in code by `ModuleContentIndex`.

No hand-authored advancement references disabled-module content, and that is now a test too.

## 4. Creatures — alpha roster vs placeholder

`Module.CREATURES` registers **106 creatures**: 96 driven by `data/wizards_and_beasts/creatures/*.json`
through the four generic locomotion classes, plus 10 with their own entity class. They are not
equally finished, and the difference is deliberate rather than accidental.

### 4.1 Alpha-ready (21)

Named in [`creature/AlphaRoster.java`](../src/main/java/at/koopro/wizardsandbeasts/creature/AlphaRoster.java),
which is the single source of truth every gate reads. A creature is listed here only when it has a
hand-built rig, AI beyond wander-and-look, a loot table (or a stated reason it drops nothing), and a
bestiary entry naming its `entityType`. All four are checked against the files on disk by
`AlphaRosterAssetTest`, so the list cannot drift from what actually ships.

**Eight dragon breeds joined the roster.** They already had hand-built 23–32 cube rigs,
`DragonEntity`'s fire/venom kit and bestiary entries; the only one of the four tests they failed was
the loot table, so killing one was a no-op. They have one now.

**The Occamy joined it too**, on a 30-bone / 44-cube rig built by `tools/occamy_model.py`. It had
abilities, a loot table and a bestiary entry already; the rig was the one test it failed. It is now
also the only creature in the mod whose *hitbox* changes size during play — see 4.4.

| Creature | Rig | Behaviour | Drops |
|---|---|---|---|
| `niffler` | built | steals shiny items, pouch inventory, bonds to a player | gold nuggets, knuts |
| `bowtruckle` | hand-built | flees, tempted by sticks; **bonds, follows, breeds, gifts wandwood saplings** | sticks, sapling |
| `mooncalf` | built | dances under a full moon; **bonds, follows, breeds, gifts dung** | mooncalf dung |
| `thestral` | built | grazes, rideable, seen only by those who have seen death | thestral tail hair, bone |
| `phoenix` | built | fire-immune flyer | phoenix feather |
| `ghoul` | built | fearful; groans on its ambient beat | ghoul slime, rotten flesh |
| `hippogriff` | built | enrage at low health, dive-bomb, rakes on melee; **bonds, follows, gifts feathers** | feathers, leather |
| `obscurus` | built | dread aura, ranged hex, enrage, smoke tint | **nothing — see 4.3** |
| `werewolf` | built | pack tactics, leap, frenzy, howls | leather, bone, loose Knuts |
| `basilisk` | built | lethal gaze with a windup, constrict, petrify, cockcrow weakness | basilisk fang, leather |
| `occamy` | hand-built | constrict, enrage, **choranaptyxis: resizes hitbox and model to fit the space** | occamy eggshell |
| `common_welsh_green` | built | fire breath, bite venom, enrage | dragon heartstring, leather |
| `hungarian_horntail` | built | fire breath, bite venom, enrage, charge | dragon heartstring, leather |
| `antipodean_opaleye` | built | dragon kit | dragon heartstring, leather |
| `chinese_fireball` | built | dragon kit | dragon heartstring, leather |
| `hebridean_black` | built | dragon kit | dragon heartstring, leather |
| `norwegian_ridgeback` | built | dragon kit | dragon heartstring, leather |
| `peruvian_vipertooth` | built | dragon kit | dragon heartstring, leather |
| `romanian_longhorn` | built | dragon kit | dragon heartstring, leather |
| `swedish_short_snout` | built | dragon kit | dragon heartstring, leather |
| `ukrainian_ironbelly` | built | dragon kit | dragon heartstring, leather |

### 4.2 Placeholder rigs (82)

These carry `"_comment": "PLACEHOLDER box rig …"` in their definition, or (for `hidebehind` and
`runespoor`, which have a Java class and no JSON) a bulk-generated rig of the same grade. They are
data-complete — attributes, traits, abilities, bestiary lore and spawn placement all work — and they
are drawn as six to nine flat-shaded boxes.

**The markers are accurate, and are now machine-checked.** `CURRENT_STATE.md` previously claimed 93
of them were *stale* — left behind on creatures whose rigs had since been rebuilt. Checked against
every rig on disk, none were: every marked rig is a generated box rig, and every hand-built rig is
unmarked. `RigMarkerConsistencyTest` decides it from the files in both directions, so the question
does not have to be re-argued by hand and an unmarked box rig can no longer pass as finished art.

**This list is the art backlog.** These 82 are what still needs a modeller; nothing else does.

`abraxan`, `acromantula`, `aethonan`, `ashwinder`, `billywig`, `blast_ended_skrewt`
`boggart`, `bundimun`, `centaur`, `chimaera`, `chizpurfle`, `clabbert`
`crup`, `demiguise`, `diricawl`, `doxy`, `dugbog`, `erkling`
`erumpent`, `fairy`, `fire_crab`, `flobberworm`, `fwooper`, `giant`
`glumbumble`, `gnome`, `golden_snidget`, `granian`, `graphorn`, `griffin`
`grindylow`, `hidebehind`, `hippocampus`, `hodag`, `horklump`, `horned_serpent`
`imp`, `jarvey`, `jobberknoll`, `kappa`, `kelpie`, `knarl`
`kneazle`, `leprechaun`, `lethifold`, `lobalug`, `mackled_malaclaw`, `maledictus`
`manticore`, `matagot`, `merperson`, `moke`, `murtlap`, `nogtail`
`nundu`, `plimpy`, `pogrebin`, `porlock`, `puffskein`
`pukwudgie`, `pygmy_puff`, `qilin`, `quintaped`, `ramora`, `red_cap`
`reem`, `rougarou`, `runespoor`, `salamander`, `sea_serpent`, `shrake`
`snallygaster`, `sphinx`, `swooping_evil`, `tebo`, `thunderbird`, `toad`
`troll`, `unicorn`, `wampus_cat`, `yeti`, `zouwu`

**What this means in a default install:** they do not spawn naturally
(`creatureNaturalSpawns = ALPHA_ONLY`) and their spawn eggs are hidden from the creative tab
(`showPlaceholderSpawnEggs = false`). Nothing is unreachable: `/wandb beast creature summon <id>`
reaches every one of them, `creatureNaturalSpawns = ALL` restores wild spawning, and
`showPlaceholderSpawnEggs = true` restores the eggs.

The **Unicorn is exempt from the spawn gate** although it is on a placeholder rig: it is the only
wild source of unicorn hair, one of the three wand cores, and gating it would dead-end wandmaking.

### 4.3 Named limitations

- **The Obscurus drops nothing.** It is a parasitic magical force that disperses rather than dying,
  and no item exists for it to leave. Deliberate, not an oversight — but it does mean killing one is
  materially unrewarded.
- **The Basilisk stand-in is fixed; the Werewolf's cannot be, and is now a design instead.** The
  Basilisk's emerald was standing in for a venom fang — `basilisk_fang` is a registered item and now
  drops, with leather still standing in for the hide. The Werewolf has no material at all: canon
  names none, and inventing one would mean a new item in the middle of an uncommitted item-model
  migration. So its drop is deliberately not a material — a werewolf is a cursed person, and it
  leaves torn robes, bones and the coins in their pocket. A canon werewolf material remains open.
- **Eleven creature materials were registered and unobtainable in survival.** Fixed by giving their
  creature a loot table: `acromantula_venom`, `demiguise_hair`, `murtlap_essence`, `occamy_eggshell`
  and `basilisk_fang`. Four more (`erumpent_horn`, `thunderbird_tail_feather`, `troll_whisker`,
  `wampus_cat_hair`) *were* reachable through their bestiary harvest rules but their creatures had
  no loot table at all, so killing one dropped nothing; they now drop a common part while the rare
  material stays behind its study gate. The last two, `veela_hair` and `white_river_monster_spine`,
  have no creature in the mod at all and are now in the `hidden_wizarding_cache` chest table.
- **Four of the eight wand cores had no source.** `veela_hair`, `troll_whisker`, `wampus_cat_hair`
  and `thunderbird_tail_feather` are each consumed by ten wand recipes and could not be obtained, so
  half the wand catalogue advertised a core nobody could hold. All eight are now reachable, and
  `CreatureMaterialObtainabilityTest` fails the build if one stops being.
- **The Hippogriff does not bow.** Canon's greeting ritual — you bow, it bows back, and only then may
  it be ridden — is not implemented. It is approached and ridden by the generic path.
- **Three creatures are art-complete but behaviour-thin**: `augurey`, `cornish_pixie` and `streeler`
  have hand-drawn rigs and skins but wander-and-look AI only, so they are not on the alpha roster.
  The Streeler's colour-shift and venomous trail, and the Augurey's rain-cry, are both unwritten.
- **The eight extra dragon breeds are on the roster now.** They were held back for a loot table and
  for the argument that "an alpha slice of twenty is not a slice". They have loot, and the count
  argument does not survive contact with what they are: one family sharing one implementation, not
  eight unrelated creatures.
- **Bonding reaches four creatures, not ninety-six.** `niffler`, `bowtruckle`, `mooncalf` and
  `hippogriff` ship `creature_bonds/*.json`; every other creature has no profile and does not bond.
  Adding one is a datapack file and no Java (see 4d), so this is a content gap rather than a
  technical one.
- **A bonded creature is not protected from anyone but its owner.** The betrayal penalty only reads
  the owner's blows, so another player can kill a companion without the bond noticing. There is no
  ownership check on damage at all; that is a multiplayer question this pass did not open.
- **No creature has a sound of its own.** Every beast reuses vanilla sound events.

### 4.4 Choranaptyxis — the one creature whose hitbox changes size

The Occamy grows or shrinks to fill the space it is in, and since this pass that is true of its
**box** as well as its picture.

**What it was.** `OccamyChoranaptyxis` wrote a render-only synced float that a bespoke
`ScaledBeastRenderer` applied to the rig's `root` bone. Its own javadoc said "the hitbox never
changes (registry-frozen)", which made a grown Occamy a large image around a small body: it blocked
nothing, reached no further, and fit everywhere it fit before. The trigger could barely fire either
— it needed an attack target *and* three air blocks straight above its head, so indoors, in a fight,
it never moved at all. Width was never measured, so it could neither squeeze through a gap nor be
boxed in.

**What it is.** One number, `Attributes.SCALE`, driven through `GenericBeastEntity.applySizeScale`:

- vanilla recomputes the bounding box from `getScale()` and calls `refreshDimensions()` itself when
  the attribute changes;
- GeckoLib's `GeoEntityRenderer.scaleModelForRender` already multiplies the model by the same value,
  so the renderer needs no support at all — `ScaledBeastRenderer`'s bone hack is deleted and the
  class is now `TintedBeastRenderer`, named for the one thing it still does;
- the modifier is `ADD_MULTIPLIED_BASE`, so it composes with the base scale `BondBreeding` writes
  for a juvenile rather than overwriting it.

It measures the space by probing candidate sizes against `Level.noCollision` and taking the largest
that is actually free, so it can never inflate into a wall. `applySizeScale` refreshes dimensions by hand:
vanilla's own refresh runs during the entity tick, so without it the creature carries the old box
for up to a tick after deciding it has a new one — found by `ChoranaptyxisTests`, which asserts the
box rather than the number. Shrinking runs 3x faster than growing:
being crushed is a need, swelling is a display. Range is `0.35`–`2.2` of a 1.7 x 1.9 body — 0.6 x
0.67 boxed in, through a one-block hole, and 3.7 x 4.2 roused in the open.

Open, and deliberately not done here:

- **`DragonRenderer` still carries the same defect.** It has its own render-state scale ticket doing
  model-only bone scaling for breed size, so a big breed's box is the same as a small one's. Ten
  creatures, the identical fix, and its own pass.
- **Growing back into tight geometry can still suffocate.** The probe stops the Occamy growing into
  a wall, but a player who builds a wall around a large one has boxed it in; it shrinks to its floor
  within four ticks and then suffocates like any other mob in a block. Vanilla has the same problem
  with elytra and does not guard it either.
- **Nothing tells the player the size is meaningful.** There is no cue that the creature in front of
  them is at its maximum, or that a corridor is what is keeping it small.

### 4.5 Placement facing — two bugs that looked like one

Reported together: the duelling dummy and the spell teacher both faced a fixed direction however
they were put down. They had nothing in common except the symptom.

**The spell teacher had no facing at all.** It extended `Block`, its blockstate had a single `""`
variant, and its model is a lectern with a *tilted reading surface* — so every one placed had its
desk tilted north. It is a `HorizontalDirectionalBlock` now, `FACING` is
`getHorizontalDirection().getOpposite()` (the reader's side, matching vanilla's lectern and
`OccamyEggshellBlock`), and `rotate`/`mirror` come free from the superclass, so a structure block or
a rotated `/clone` turns one correctly.

Its model tilt was also the wrong sign. A positive X rotation raises the *north* edge, which points
the desk away from a reader standing on the `FACING` side; vanilla's own lectern desk uses `-22.5`.
Invisible for as long as the block had no facing and all of them pointed one way.

**The dummy's placement code was correct and could not work.** `DuellingDummyItem` had always
computed `placerFacing.getOpposite().toYRot()` and handed it to `Entity.snapTo` — which writes
`yRot` and `xRot` only. A `LivingEntity` is *drawn* from `yBodyRot`, and the tracking that brings
`yBodyRot` toward `yRot` lives in `LivingEntity.aiStep`'s goal handling, which never runs on a mob
that registers no goals. So the dummy's `yRot` was right and its rendered body was south, always.
`DuellingDummyEntity.setFacing` now carries the angle into `yBodyRot`, `yHeadRot` and both
previous-tick values (without the latter the first frame renders a spin from south), and
`readAdditionalSaveData` re-applies it from `yRot`, because `yBodyRot` is not saved and a reload put
every dummy back to south.

`PlacementFacingTests` drives the real use path from a player looking along each of the four
cardinals and asserts the **rendered** rotation. An assertion on `yRot` would have passed against
the bug — which is the whole reason the bug survived to be reported.

## 4b. Bestiary

**The unlock rule is: seeing a creature opens its page, and the entry's own `encounterTrigger`
deepens it.** Sighting range is 12 blocks, scanned once a second per player. The rule itself is
[`bestiary/EncounterRule.java`](../src/main/java/at/koopro/wizardsandbeasts/bestiary/EncounterRule.java)
and is covered by `EncounterRuleTest`.

Tiers are `UNDISCOVERED → SIGHTED → ENCOUNTERED → STUDIED → MASTERED`; each rung reveals more of the
page (rating, then short lore and habitat, then the full account). Progress is per player, stored on
a data attachment and synced on every change.

Known limitations:

- **No per-entry illustration.** The detail pane draws the live entity where one exists and a shared
  placeholder otherwise; `textures/gui/bestiary/entries/<id>.png` is read if present but none ship.
- **Undiscovered entries are listed, not hidden.** An unopened entry shows as `???` with its
  silhouette, so the book reveals how many creatures exist before you have met any of them.
- **`EncounterTrigger.ITEM_USE` has no caller.** Entries may declare it; nothing fires it, so such an
  entry could only ever reach `SIGHTED`. No shipped entry uses it.
- **Ministry classification renders as repeated `X` in every language**, which is how canon writes it;
  category, size and tier labels are translated.
- Entries whose creature has no `entityType` can never be reached at all. `toad` was in that state
  and is fixed; nothing else ships without one.

## 4c. Skill web and spell power

`SKILL_TREES` ships `ENABLED`; `PROFICIENCY` stays `PREVIEW` until the curve is balanced.

**Refund is not admin-only and is not a stub.** `/wandb skill respec` carries no permission gate and
no cheat requirement: it clears every allocated node and refunds `pointCost × level` for each, strips
the ability grants those nodes sourced, and re-syncs. `/wandb skill reset <player> [node]` is the
admin form. A player cannot soft-lock themselves out of the web. The skill screen's footer now
advertises the respec command once any point has been spent, since the only real gap was that
nothing ever told a player it existed.

**The filler purge landed 2026-09-09.** The web went from **168 nodes, 100 of them carrying a
`skill.wizards_and_beasts.filler.minor_*` display key, to 107 nodes with 30 pathways.** What was
wrong was not the node count on its own: 41 of those 100 paid a raw attribute, so the cheapest thing
a player could do with a point was buy the fourteenth +0.5 max health. The design sheet
[`SKILL_WEB.md`](SKILL_WEB.md) carries the chain-by-chain account; the parts that matter here:

- **No small node may grant an attribute**, enforced by
  `SkillNodeJsonTest.noSmallNodeGrantsARawAttribute`. Pathways pay in their region's own currency —
  harvest luck, beast resistance, misfires, curse damage — because an attribute is the only effect
  type with no theme attached and therefore the only one that can be pasted onto a connector without
  anybody deciding what that connector is for.
- **Every `<spell>_unlock` node teaches its spell**, enforced by
  `SkillNodeJsonTest.everyUnlockNodeTeachesItsSpell`. The three DARK_ARTS nodes that shaved a
  cooldown while wearing an Unforgivable's name now teach it; knowing a spell and being allowed to
  cast it are separate data, so `AvadaKedavra`'s PROFICIENT-on-both-Imperio-and-Crucio requirement
  still holds and `Module.DARK_ARTS` still ships disabled.
- **`legilimency` was renamed `occlumency`** and grants the defence half of the mind-magic pair. The
  old node granted Dark Arts damage and had nothing to do with Legilimency, which every wizard
  already holds through `PlayerStatusAbilityGrantSource`.
- **Existing saves are refunded at login**, not stranded: `PlayerSkillData.CURRENT_VERSION` is 4.
  A saved allocation names deleted node ids, and `respec` can only refund what `SkillTrees.byId`
  still resolves, so without the bump those points would be neither spent nor recoverable.

Known limitations:

- **Node tooltips derive their effect lines from the `effects` data, not from the `description`
  prose.** That is the fix, not the limitation — the limitation is that one declarable effect type,
  `ability_refinement`, reaches no consumer: `AbilityModifiers` aggregates it correctly and no
  ability implementation reads the result, so a node using it would cost points and change nothing
  and render no line for it. `SkillNodeJsonTest` fails the build if a shipped node declares it, and
  none do. Wiring a consumer means choosing which ability an axis means something for, and inventing
  that mapping to close a checkbox is the same mistake as mapping wand `spell_modifiers` onto
  `SpellCategory`. (`learn_spell` was wired 2026-08-22 and `grant_ability` 2026-09-09; both ship.)
- **The tooltip shows what the *next* level buys on a partly-allocated node**, and the total on a
  maxed one. It does not show both at once.
- **`passive_attribute` only wires three attribute ids** (`max_health`, `movement_speed`, `armor`).
  Any other is inert; a test now enforces that none ship. 14 nodes use it, all notables where
  toughness is the fantasy — it was 55 before the purge.
- **Spell power is `PREVIEW`-grade tuning, not balance.** The formula and its bounds are locked by
  `SpellPowerTest` and documented in [`DEVELOPER_REFERENCE.md`](DEVELOPER_REFERENCE.md); the numbers
  in it are provisional. Proficiency at zero still lands a freshly-learned spell at 0.65×, which is a
  tuning decision inherited from `ProficiencyScaler` and not yet revisited.
- **The spell detail panel's power readout excludes the situational channel** — wand corruption,
  allegiance, dark corruption, vocation, Niffler happiness and player stats are resolved server-side
  at cast time and the client cannot know them. The panel says so on its last line, and under-reports
  rather than over-reports.
- **Two proficiency systems still coexist in the data.** The `Proficiency` enum tier (NOVICE /
  PROFICIENT / MASTERED at 0 / 50 / 200 successful hits) still drives display, gating and sound
  pitch; the float `spellProficiencies` curve (+0.002 per hit, so 500 hits to 1.0) is the only one
  that touches damage now. The two disagree about what "mastered" means, and unifying them is
  outstanding.

## 4d. Creature bonding

**Four creatures form a relationship with a player, and the layer they use is datapack-driven.**
Until this pass the Niffler was the only creature in the mod with one, and every part of it —
owner, bond level, feed table, milestone XP, follow goal — was hard-coded in `NifflerEntity`. It is
now `creature/bond/`, and the Niffler is one of its four users rather than its only implementation.

### How a species opts in

A creature bonds **if and only if** `data/wizards_and_beasts/creature_bonds/<id>.json` exists. There
is no `bondable` flag in Java. For the ninety-six data-driven creatures that means adding the
relationship layer costs one datapack file and no code at all: `GenericBeastEntity` implements the
interface for all of them, and a creature with no profile pays nothing but a null check.

Bespoke entity classes (`BowtruckleEntity`, `MooncalfEntity`, …) each supply three things — a
`BondState` field, a synched bond accessor, and calls from `tick`/`mobInteract`/`hurtServer`/save —
and inherit the rest as interface defaults.

| File | What it is |
|---|---|
| `BondProfile` | the species' numbers: feeds, thresholds, milestones, gift, breeding |
| `BondFeed` | one item it accepts, what it is worth, how long it is full afterwards |
| `BondGift` | what a bonded creature hands over while alive, and how often |
| `BondBreeding` | how it raises young |
| `BondState` | per-creature storage: owner, level, timers, juvenile growth |
| `BondableBeast` | the behaviour, as interface defaults |
| `FollowBondedOwnerGoal` | follows the owner past `followThreshold` |

### What bonding is *for*

Every creature material used to be reachable exactly one way — kill the creature — which made a
relationship strictly worse than no relationship. Two mechanics invert that:

- **Gifts.** A bonded creature produces its material on a cooldown, indefinitely. A Bowtruckle hands
  over wandwood saplings, a Mooncalf its dung, a Hippogriff feathers.
- **Breeding.** Two bonded Mooncalves or Bowtruckles, fed the breeding item, produce a juvenile that
  inherits part of its parents' bond and grows to full size on a timer.

Juveniles are the **same** `EntityType` shrunk through `Attributes.SCALE` rather than a registered
baby variant — an `EntityType` is frozen registry data, so a real baby species would be a
registration, a renderer, a spawn egg and a save-compat story each. `SCALE` drives the hitbox too,
so a calf is genuinely small rather than looking it.

### The Niffler migration

The Niffler's numbers moved into `creature_bonds/niffler.json` **unchanged** — diamond 20 / gold
ingot 15 / nugget 5 on 120/60/30-second cooldowns, milestones at 20/50/80/100, follow from 50,
bestiary `MASTERED` at 80 — and the NBT keys are byte-identical, so existing worlds keep their
Nifflers' owners and bonds. `BondProfileDataTest` asserts each of those numbers against the file, so
the migration cannot drift. Its pouch, pocket-carry, theft and peek are untouched.

Two things did change, both fixes:

- **`getBondLevel()` now reads the synched value.** It returned a server-only field, which is 0 on
  every client, so `NifflerPocketLayer`'s `>= 100` gate could never open. The value had been synced
  since the entity was written and nothing was reading it.
- **Crossing a milestone now says so**, through a toast. The four milestones previously fired an
  event and told the player nothing.

### Known limitations

- **`MagizoologyXPEvent` still has no listener.** Bond milestones post it, as they always did, and
  nothing consumes it — the XP is a hook, not a system.
- **Nothing shows the bond level.** It is synced to the client and no screen, tooltip or nameplate
  draws it, so a player learns where they are only from the behaviour changing.
- **A juvenile is a scaled adult in every other respect.** It has adult health and adult AI, and its
  only juvenile behaviours are being small and not producing gifts.
- **Breeding has no pathing toward a partner.** Both parents must already be within
  `partnerRange`; they will not walk to each other the way vanilla animals do.

## 5. Spellcasting

- **33 spells ship** — 27 datapack JSON under `data/wizards_and_beasts/spells/` plus 6
  bespoke Java in `spell/impl/` (`avada_kedavra`, `expecto_patronum`, `imperio`,
  `obscurus_grasp`, `obscurus_surge`, `protego`). The canon roster in
  [`SPELL_CORPUS_ROSTER.md`](SPELL_CORPUS_ROSTER.md) lists 154; the remaining 121 are unwritten.
- **The loadout is four slots.** Arrow keys pick a slot, `G` opens the full assignment menu.
  A wizard who knows thirty spells still casts from four.
- **The spell wheel arms the active slot, it does not bypass it.** Hold `X`, sweep to a spell,
  release: that spell is assigned into whichever of the four slots is currently active. This is
  a fast way to change what the slot in your hand contains, not a fifth slot — releasing in the
  dead centre cancels and keeps what you had. It shows only spells you have learned that your
  heritage may equip; the server re-checks on arrival and wins any disagreement. Obscurial
  abilities are absent on purpose: they ride the ability wheel (`V`), not the spell loadout.
- **Reject feedback is one short line, not voice or animation.** A refused cast plays the denied
  sound and shows the reason for ~3 seconds — on the spell HUD when it is visible, on the action
  bar when it is not, never both. There is no failed-cast animation.
- **Two refusals still speak for themselves and are not on the HUD.** An unmet spell requirement
  and a Gamp's Law violation compose their own text from live state, so they go to the action bar
  with a specific sentence instead of a generic HUD line. That is deliberate: "Requires Stupefy at
  Adept" beats "requirement not met".
- **Cooldown display is client-predicted.** The sweep interpolates against the span the client
  observed when the cooldown started; a server stall can make it hold rather than spring back.
  The server is authoritative — a sweep that looks finished can still refuse.
- **Per-spell VFX is authored per spell.** Cast sound is data-driven (`sound` block) and so is the
  particle look (`vfx` block: `trail`, `impact`, `trailDensity`, `impactCount`). All 27 shipped spells
  declare one. Before this, `ModParticles.typeFor` picked a particle from the spell's *family*, so
  every spell in a family drew the same shape and differed only in hue — Stupefy and Expelliarmus were
  the same red bolt twice. An absent block still falls back to the family look, so a datapack spell
  that authors nothing is unchanged. See §8 of [`SPELLS.md`](SPELLS.md) for the plug points.
- **Particle counts are capped in the codec**, not trusted: 48 per impact burst, 6 per trail tick.
  Trails are client-side; impact bursts go through `sendParticles` and cost the server too.
- Beam styles are still family-generic — only trail and impact are per-spell.
- Every spell sound is a vanilla event. `stupefy` and `expelliarmus` shipped with no `sound` block at
  all and fell back to the generic family cast sound; both now have their own.

## 5b. Broom flight

- **The rider's client flies the broom; the server keeps the consequences.** This is vanilla's own
  contract for a ridden vehicle (`Player.isClientAuthoritative()` is true, and the server's position
  and rotation are overwritten from the client every tick), and the mod now follows it instead of
  simulating the flight on both sides at once. The old arrangement did not make the server
  authoritative — its result was snapped away regardless — it only gave it a second, wrong opinion
  built from input that arrives on change or every ten ticks, and that opinion still charged
  durability, dealt crash damage and played crash sounds for walls the rider never touched. Impacts
  now travel as a report from the side that actually saw them, checked and rate-limited on arrival.
- **Boost is counted once, on the server.** Its two counters are synched entity data, so both sides
  counting their own copy meant the server's stale count was broadcast over the client's prediction
  and a firing boost visibly stuttered.
- **Other players' brooms are interpolated, not re-simulated.** Onlookers used to run the full flight
  model against input they did not have.

- **Brooms sink when you let go.** Every definition authors a `weakGravity` and it is now applied:
  release the controls and the broom settles rather than hovering, capped well short of free-fall.
  Holding ascend or descend overrides it — that is the rider taking charge of altitude.
- **Landing gently is free.** Coming down at a walking descent, not still travelling forward at
  speed, costs no durability and deals no damage. Anything harder is scored as an impact, and so is
  any wall. `broomGentleLanding=false` restores the older behaviour where every touchdown counted.
- **Crash severity is measured per broom.** It used to divide by one fixed number that described no
  broom in the game, which meant the starter broom could not register an impact at any speed into
  any wall, and the Firebolt Supreme took fatal damage at 64% of its own top speed. Both are fixed;
  expect crashes to feel *different* from previous builds in both directions.
- **Water and lava end the flight.** A broom that meets either sets the rider down at the nearest
  clear footing and returns itself as an item. The rider is not protected from the fluid — this is a
  refusal to keep flying, not a rescue.
- **Dismount looks for somewhere to stand** — downward first, then outward, up only as a last
  resort — and the rider keeps none of the broom's momentum. Boxed in on every side, it still falls
  back to the broom's own position.
- **Alpha-limited:** speed is tuned by feel, not by a Quidditch pitch. `broomSpeedMultiplier` scales
  every broom at once and preserves their ranking. The flight FX (`broomWindVolume`,
  `broomFovEffect`, `broomSpeedParticles`) are all independently switchable, and **`broomFovEffect=0`
  is the supported setting for motion sickness** — a field of view that moves on its own is the
  commonest cause of it.

## 5b-2. Apparition

- **A charge nobody releases no longer splinches you.** Holding past the hard cap used to resolve as
  `CATASTROPHIC` -- the harshest rung in the ability, awarded for doing nothing. It now collapses: no
  arrival, no wound, and the sputter cooldown plus the exhaustion for the effort spent. Splinching is
  what a botched *jump* does, and a jump has to have been attempted to be botched.
- **Destination Apparition is press-to-commit.** Picking a point in the selector starts a seventy-tick
  anchored charge at a moment when you are holding nothing, because you were clicking in a GUI. The
  input driver now adopts that charge from the server's own phase broadcast and commits it on the next
  press of the ability key -- watch the destination ring, press when it closes. Before this it could
  not be released or withdrawn at all, so picking a destination and then changing your mind was a
  guaranteed catastrophic splinch about seven seconds later.

## 5c. Floo Network

- **Every refusal now names its cause.** Two used to fail in complete silence — no lit registered
  fireplace in reach, and a destination whose dimension the server no longer loads. Pressing Travel
  and having nothing happen at all was the single most confusing thing about the system.
- **Powder is charged last.** Every other check passes before a pinch is spent, so a refused hop
  never costs powder, and "You have no Floo Powder" means powder is the only thing missing.
- **Arrival is a toast, not chat.** Success and misfire both land as a toast plus, on a stumble, one
  action-bar line. Nothing about ordinary travel writes to chat any more.
- **Renaming a fireplace retires its old address.** It previously did not: the block accepted the
  new name while the network kept the old row pointing at the same block, so the hearth answered to
  both names and appeared twice in every destination list. One block now holds exactly one address.
- **Breaking a registered fireplace removes it from the network.** Destroying your own destination
  loses it; there is no grace period.
- **A Floo address is not a chunk loader.** Nothing is pre-loaded and no ticket is held; the
  teleport loads the destination chunk on arrival the way vanilla does and the ordinary chunk
  lifecycle releases it. A network of a hundred hearths is a hundred addresses, not a hundred forced
  chunks.
- **Misfires are designed.** Roughly one hop in ten lands somewhere else, weighted toward dark
  addresses. An *occasional* wrong destination is the feature; a guaranteed one is a bug.
- **Alpha-limited:** the Floo *command* output (`/wandb ... floo register`, `unregister`) is still
  hardcoded English. Those are operator tools rather than player-facing play, so they were left for
  a later localisation pass.

## 5d. Owl post — new, PREVIEW

**`Module.OWLS` was never owl post.** It is the O.W.L. examination system; no owl entity, no
delivery and no bird existed anywhere in the mod. The row in §2 used to claim otherwise.

The delivery loop now exists: `/wandb player post send <player>` hands the stack in your main hand to
an owl, and it arrives two minutes later. `/wandb player post status` reports what is in the air.
Parcels are stored in `OwlPostData` (a `SavedData` on the overworld) with an **absolute** arrival
tick, so a flight survives a restart rather than pausing while the server is off.

Every failure state says something specific, and nothing is ever silently destroyed:

| Situation | What happens |
|---|---|
| module off | refused at send; the stack never leaves your hand |
| empty hand | refused at send |
| addressed to yourself | refused at send |
| recipient offline on arrival | the owl circles and retries; the parcel is not lost |
| recipient's pack full | the owl turns around and flies it back, taking the same two minutes |
| sender's pack also full on return | dropped at the sender's feet, and they are told why |

Limitations, all deliberate for the alpha:

- **There is no owl.** This is a scheduled hand-off, not an entity with a flight path. Nothing flies
  across the world and nothing can be intercepted. An owl entity, a rookery block and an owl-post item
  can all sit on top of this service later without changing it.
- **Command-only.** No item or block entry point yet.
- **Same-server only.** The recipient is a player on this server, resolved at delivery. Cross-dimension
  is fine because the queue lives on the overworld and addresses a player, not a position.
- **No cancellation.** Once an owl has your parcel you wait for it to land or come back.
- Flight time is a constant, not a distance: sending across the world and sending across the room
  both take two minutes.

## 5e. Wands

- **Wands are now tinted by their wood** (`WandAppearance`), which is what makes two of them
  distinguishable in an inventory at all — every wand in the mod rendered the same sprite before, so
  ten woods and three cores shared one look and the only way to tell two apart was to hover both.
  The Elder Wand keeps its own art and is never tinted.
- A datapack wood with no hand-picked colour gets a deterministic one derived from its id, held
  inside a warm timber band so it still reads as wood.
- **The first bond is announced once** — one toast at the moment a wand goes from unbound to bound,
  naming its wood, its core and how readily it took to its owner. Everything after that stays passive,
  as it was.
- Still missing: the tint is the only visual difference. There is one wand mesh, so core, length and
  flexibility are legible in the tooltip and nowhere else.
- The bond toast keys on the unbound-to-bound transition, so it fires once per wand per owner. A wand
  transferred by defeat re-announces for its new owner, which is intended.

### 5e.1 What a wand does is now readable — resolved 2026-09-08

`CURRENT_STATE.md` Critical #1 ("the wand's identity is 40% wired and 0% visible") is closed. Both
halves of it:

- **All 10 woods and all 10 cores carry an authored `cast_modifiers` block.** The last hardcoded
  switch in the wand — `WandStatsResolver`'s ten-case `WandCore` fallback table — is deleted, so wood
  and core now resolve by exactly the same datapack path. A datapack can retune or override any of
  the twenty, including downwards, which the fallback made impossible: it read a neutral result as
  "nobody authored this yet" and answered from the enum instead.
- **The tooltip states the resolved contribution.** Damage, cooldown, range, misfire chance and any
  non-zero category bonus, in the tooltip's existing green/dark-red vocabulary. Hovering a card at
  Ollivander's gives the same summary before the choice is spent.

Three things worth knowing about the numbers:

- They are the wand's **contribution**, not an outcome. A tooltip has no spell, no proficiency and no
  caster stats in scope, and the cast pipeline applies further modifiers on top. Reading `+20% damage`
  as "this wand deals 20% more damage than the one in your other hand" is right; reading it as a
  final figure is not.
- A row that rounds to zero is **omitted**, not printed as `+0%`. A wand with no rows is a wand whose
  four components cancel, not a bug.
- Ollivander's summary is computed at the **trial length**, 11". The gifted wand's length is rolled
  on acceptance, so the range and cooldown rows can move a few points either way once it is yours.
  The card says so.

Deliberately still unwired: `spell_modifiers` on a wood definition stays authored and unread. Its
keys are magical schools (`healing`, `divination`, `charms`) and `SpellCategory` has only `combat`,
`utility`, `defense` and `dark_arts`. Mapping one onto the other is an unmade balance decision, and
the same ruling is why unicorn's Healing, veela's Charms and thunderbird's Transfiguration bonuses
are absent from the authored tables rather than mapped onto a category that means something else.

## 5f. Currency

Rates are canon and correct: **29 Knuts = 1 Sickle, 17 Sickles = 1 Galleon, 493 Knuts = 1 Galleon**
(`CurrencyHelper`). Every coin's tooltip now states the whole scale, not just its own step — the three
coins previously had no tooltip at all, so a new player had no way to learn that the money is not
decimal. Balance is on the character sheet's Carried Coin panel and in the Gringotts screen at any
goblin teller.

- **The spell teacher is the coin sink, and it is now on by default** (58 Knuts — two Sickles — per
  lesson). It was implemented but switched off, which meant nothing in a default install ever consumed
  a coin and the whole economy was decoration. `spellTeacherRequirePayment=false` restores the old
  sandbox behaviour.
- **No exchange path with emeralds.** Wizarding coins and villager trade are separate economies with
  no conversion between them, deliberately for now; there is no Gringotts exchange desk yet.
- Coins are not accepted by any vanilla mechanic, so a player who only ever trades with villagers can
  ignore the currency entirely.

## 5g. Pocket dimensions

New pockets are not barren — `PocketShellGenerator` lays an archetype-specific floor and
`PocketSanctuaryFurnisher` furnishes the sanctuary — and there are **four independent ways out**:

1. the hatch or door in the shell;
2. the **Claustra Reverto** charm;
3. `/wandb world pocket exit`, which is ungated for exactly this reason;
4. a **void-fall safety net** below y=50 that fires even when the case is locked from outside.

The fail-safe was always there; nothing told the player about it, so a griefed hatch felt like being
trapped forever. Entering a pocket now names all four.

Limitations:

- **Creature escape is unmodelled.** Beasts inside a pocket do not path out, get released, or react to
  the case being opened; `ExtensionCharmService.releaseToWorld` exists and nothing calls it from AI.
- Pocket interiors are procedural shells, not authored rooms. `pocket_templates` ships three.
- The exit hint is a chat line on entry; it is not repeated, so a player who misses it has to find it
  again in this file.

## 5h. Floo Network

`FLOO_NETWORK` ships `ENABLED`. The travel half was already solid — every refusal carries a
translation key and powder is consumed last, after every other check, so a refused hop never costs
one. What was missing was the half that creates something to travel *to*.

**Naming was admin-only.** `/wandb world floo register` sits under `/wandb world`, which requires
ADMIN, so a survival player could craft a Floo Fireplace, place it and light it — and never give it
an address. Every hearth they built was unreachable and the destination list stayed empty forever.
A **name tag on a cold hearth** now registers it: a vanilla item, no new registry entry, no art, and
the tag is consumed so an address costs something.

Limitations:

- **Addresses are first-come, first-served and global.** Any player who can reach a cold hearth can
  claim any unused name, and there is no ownership, no protection and no way to take a name back
  except to break the fireplace. Fine for single-player and for a friendly server; not a permissions
  model.
- **Registration is public by default** when done with a name tag. The private/public distinction
  exists in the data (`FlooRegistryEntry.isPublic`) and only the command can set it.
- **A Floo address is not a chunk loader**, on purpose. The destination chunk loads on arrival the way
  vanilla teleports do; a network of a hundred hearths must not become a hundred forced chunks.
- **Misfires are a 10% chance** and weighted toward "dark" addresses by keyword, which is canon but
  means a player can be moved somewhere they did not choose. The arrival toast says so.
- **Cross-dimension travel is allowed** and the destination list labels anything outside the
  overworld. A destination whose dimension no longer loads is refused with a reason rather than
  silently, because the address outlives the dimension in saved data.
- Destination previewing ("peek before you step") is not implemented and is not in the data model.

## 5i. Brewing and potions

**14 brews and 12 recipes ship.** The cauldron ladder is pewter -> brass -> copper, and the tier a
recipe asks for is what gates it.

A brew's behaviour is a list of **components** (`brew/effect/BrewEffect`), dispatched by a `type`
discriminator, with an optional `phase` of `on_drink` (the default) or `on_brew_complete`. A brew
that declares no `components` has its legacy `effects` list wrapped in an `apply_effects` component
at load, so the two original brews — Wiggenweld and Mandrake Restoration Draught — are still
authored the old way and still work. That is deliberate and tested: an effect list *is* the right
description of most potions.

There are three ways to author a potion, in increasing order of cost, and the cheapest one that
fits is the right one:

| What the potion does | How to author it | Example |
|---|---|---|
| Vanilla effects | `apply_effects` | Pepperup |
| Something that needs a rule of its own | a modded effect + one event handler | Wolfsbane, Draught of Living Death, Amortentia |
| Something that needs state of its own | a component type handing off to a system | Felix Felicis, Polyjuice, Veritaserum |

### 5i.1 What the signature potions actually do

- **Veritaserum** — you cannot hold a false face for three minutes. Any Polyjuice disguise ends,
  another cannot be started, invisibility is stripped and re-stripped, you are outlined, and your
  Occlumency reads as zero so Legilimency goes straight through. It touches **nothing** you type,
  and reveals no location, inventory or vault: it opens an ability the interrogator already had
  rather than leaking anything by itself. A dose can be slipped into a drink, which is why that
  boundary is where it is.
- **Draught of Living Death** — hostiles stop targeting you, because you read as a body. You are
  also blind, rooted and unable to mine for the duration; being overlooked is the compensation for
  being helpless, not a stealth tool. It is a *harmful* effect, so milk and any healing draught with
  an empty `cure` list will end it.
- **Amortentia** — you cannot bring yourself to strike another player. Mobs are unaffected, so a
  dose is a social disaster rather than a death sentence in a cave.
- **Felix Felicis** — a real run of luck (near-death saves, extra ore, chest re-rolls) with an
  internal cooldown and a punishing second bottle. Not Luck II.
- **Polyjuice** — a real disguise, keyed to the hair in *that* bottle.
- **Wolfsbane** — the werewolf keeps their mind, never their shape.

**Known alpha limitation — Amortentia has no object of affection.** In canon you are besotted with
a particular person; here the effect is indiscriminate, because a brewed bottle does not record who
made it. Narrowing it needs the cauldron to stamp its brewer onto the output stack, which is not
built. The effect is deliberately broad rather than faking a target by picking the nearest player.

### 5i.2 Herbology now has a consumer

Nine of the twelve recipes used to be built entirely from vanilla items while the mod's own flora —
mandrake, mallowsweet, devil's snare, gillyweed, dittany — was consumed by no brew at all. Ten of
twelve now use at least one mod ingredient, and `ShippedBrewDataTest` holds that at a floor of half.
Pepperup (the pewter tutorial potion) and Silver Solution (metallurgy, not herbology) are vanilla on
purpose.

Two related fixes worth knowing about:

- **`minecraft:crops` did not contain `wizards_and_beasts:mandrake_crop`.** `MandrakeCropBlock`
  extends `CropBlock`, but that tag is *data*, not a superclass check — so Herbology's Harvest
  Bounty, Bountiful Harvest and the Herbologist vocation's `crop_yield` all silently did nothing
  when you harvested the mod's only crop. Fixed by
  `data/minecraft/tags/block/crops.json`.
- **`essence_of_dittany` is unobtainable** — a registered canon item with no loot table, no recipe
  and no other source. It is one of the 87 behaviourless canon stubs. Skele-Gro asks for `dittany`
  (which drops from holly and rowan leaves) instead. `ShippedBrewDataTest` now fails any recipe that
  names an unobtainable mod ingredient, because the symptom is a cauldron that simply never matches.

### 5i.3 Still missing

- **`BrewDefinition` has no failure component.** `failureChance` and the catalyst window are recipe
  properties; a brew cannot describe what its own botched version does beyond the shared
  `ruined_potion`.
- **`on_brew_complete` has no shipped user.** The phase works and is tested, but no brew uses it.
- **Component `apply()` bodies are unit-tested only for parsing.** Executing one needs a level and a
  living entity, so heal amounts, cure behaviour and the new handlers are compile-checked and
  reasoned about rather than run — see §8.

## 6. Structures and worldgen

- Azkaban and the Chamber of Secrets place nothing (§3).
- `pocket_templates` ship, but the pocket dimension's template roster is thin.

## 7. Art and assets

- Character sheet backgrounds and tab icons are drawn procedurally; the PNG assets named in
  `client/gui/character/CharacterSheetTextures.java` do not exist yet.
- ~~`floo_fireplace.png` is missing~~ — it is not. The texture, its lit variant, the side and top faces, the blockstate and both models all ship and agree; the `TODO` in `ModBlocks` that this line was derived from was stale and has been removed.
- Dementor voice lines reuse vanilla sounds.
- The Character Sheet's own art (background, tab icon, panels) **does exist** — the `TODO: create`
  comments that said otherwise were stale and are gone. The screen now probes for it on open and
  falls back to the shared themed panel if a resource pack removes it, so no required panel can
  show the missing-texture checkerboard.
- Tabs on the Character Sheet fed by a `PREVIEW` module carry an amber corner mark, read live from
  `ModuleManager` — so half-finished numbers are labelled as half-finished rather than wrong.

## 8. Testing gaps

- `runGameTestServer` runs **four real in-world scenarios** as of 2026-09-03
  ([`WandCastLifecycleTests`](../src/main/java/at/koopro/wizardsandbeasts/gametest/WandCastLifecycleTests.java)),
  covering the wand cast/release lifecycle. Note that 1.21.11 has no `@GameTest` annotation — tests are
  registry entries added through NeoForge's `RegisterGameTestsEvent`; earlier notes in this repository
  describing an annotation-based system were written against an older Minecraft version.
- Everything else in the mod still has **no in-world coverage**: no block entity, structure, GUI,
  brewing, Floo or creature behaviour is exercised by a game test.
- The vertical slice is verified by hand — see [`ALPHA_SMOKE.md`](ALPHA_SMOKE.md).
- Pure logic is covered by JUnit under `src/test/java`; that is the preferred home for anything
  that does not need a live world.

## 8a. Open defects found in the 2026-09-03 alpha-hardening audit

Three defects found by that audit are **not fixed**. They are recorded here rather than in a
worklog because each is reproducible and each has a player-visible consequence.

### 8a.1 The common config file rewrites itself once a second, forever

**Reproduce:** `./gradlew runGameTestServer`, then
`grep -c "is not correct. Correcting" <log>` — 9 to 11 occurrences in a ~20-second run, all from
`FileWatcher-1-thread-1`, every one naming `werewolfEquipmentWhitelist`.

**What happens:** `run/config/wizards_and_beasts-common.toml` oscillates between two different
contents — one of **66** top-level keys (19280 bytes) and one of **44** (13473 bytes) — flipping
about once a second for as long as the process lives. Each flip logs a WARN and rotates a
`-N.toml.bak`, so the five backup slots are consumed within seconds and further rotations fail
with `FileAlreadyExistsException`. The werewolf tunables are in the 22 keys that come and go, so
**they may not persist across a restart**.

**Partly addressed:** `werewolfEquipmentWhitelist` and `adminUuids` both used
`ModConfigSpec.Builder.defineList`, which pins the spec to `ListValueSpec.NON_EMPTY`; an empty
default therefore failed its own size check on every load. Both now use `defineListAllowEmpty`,
which removed the correction that used to fire during mod loading. `adminUuids` was the worse of
the two — its own comment tells an operator to *clear the list* to fall back to operator
permission, and plain `defineList` made that instruction impossible to follow.

**It can also crash mod loading.** On 2026-09-03 a `runGameTestServer` run failed during mod loading
with `WritingException: Failed to atomically write (REPLACE_ATOMIC) the config` /
`NoSuchFileException: wizards_and_beasts-common.new.tmp.toml` — the rewrite loop lost its own temp file
mid-write. Deleting `run/config/wizards_and_beasts-common*.toml*` and re-running cleared it. So this is
not only log noise: it intermittently takes the server down at startup.

**Still open:** the `FileWatcher` correction loop survives that fix, and the logged reason is the
`value == null` branch of `ListValueSpec.correct` — the key is absent when the watcher re-reads,
even though it is present in the file the correction just wrote. Something writes a 22-key-shorter
variant of the file; that writer has not been identified. **Root cause unknown.**

### 8a.4 A game-test player cannot be killed

Both `Entity.kill(ServerLevel)` and `hurt(damageSources().genericKill(), Float.MAX_VALUE)` leave a
`ServerPlayer` created inside a game test at **full health**, with `isRemoved() == false`. Clearing
`invulnerableTime` first made no difference. On one earlier variant the player's health did reach zero
and it was back to `20.0` one tick later.

Something in the mod's `LivingIncomingDamageEvent` chain — nineteen handlers subscribe to it — or in the
mock player's own setup is absorbing the blow. It has not been traced.

**Consequence for coverage:** `lifeless_caster_cannot_release` empties the caster's health directly
instead, which does exercise `CastReleaseGate.CASTER_NOT_ALIVE` through the real release path. The
death-driven `WandCastSessions.abort` in `WizardsAndBeastsCommands.onLivingDeath` is therefore **not
covered by any automated test**, and neither is anything else that depends on a player dying.

### 8a.2 Five of the twelve O.W.L. subjects can only ever grade Troll

`OWLGradeCalculator` reads seven counters that **nothing in the mod ever increments**:
`combatSpellCasts` (on `PlayerSpellData`), and `metamorphFormsUsed`, `arithmancyInteractions`,
`runicInteractions`, `divinationEvents`, `astronomyEvents`, `muggleItems` (on `PlayerSkillData`).
`getLoreItemsRead()` is backed by `loreEntriesRead`, whose only mutator `recordLoreEntry` has zero
call sites. All are saved to and loaded from NBT, so they look wired.

Four subjects have a live second input and are unaffected in practice — Charms, Defence Against
the Dark Arts, Transfiguration and Ancient Runes all reach `O` through skill-tree nodes or the
Animagus stage. Potions (`potionBrewPoints`, awarded by `CauldronBlockEntity`) and Herbology
(`plantsHarvested`, from `MandrakeCropBlock`) are fully live.

**Permanently `T`:** Arithmancy, Divination, History of Magic, Astronomy, Muggle Studies. Each
reads a dead counter as its *only* input. Not fixed here because the mod has no arithmancy,
divination, astronomy or muggle-studies content to hang an increment on — wiring them would mean
inventing those systems, not connecting existing ones. History of Magic is the closest to
reachable: the Handbook exists, but it is a client-only screen with no server-side "chapter read"
signal, so `recordLoreEntry` has nothing to call it.

### 8a.3 Four selectable Animagus forms have no datapack definition

The server says so itself on every boot:

```
[Animagus] 4 selectable form(s) have no datapack definition and will get no capabilities,
attributes or senses: [animagus_stag, animagus_hawk, animagus_hare, animagus_beetle]
[Animagus] 2 loaded definition(s) are not selectable by any player and are unreachable in game:
[animagus_falcon, animagus_rat]
```

`AnimagusForms.IDS` offers six forms; `data/wizards_and_beasts/animagus_forms/` holds four
definitions, and only `cat` and `dog` appear in both. A player who picks Stag — the iconic one —
gets a form with no capabilities, no attribute changes and no senses. `AnimagusAbilityService`
*does* have active-ability and passive-effect cases for all four missing forms, and
`FormModelRenderer` has render cases, so the gap is the data row alone.

Related: every `animagus_forms` definition names a GeckoLib model, texture and animation file
under `geckolib/{models,animations}/entity/form/`, and **none of those directories contains a
single file**; the only form texture that ships is `animagus_stag.png`, for the form that has no
definition. Forms render through `FormModelRenderer` on vanilla models, so nothing is visibly
broken today, but those three fields describe assets that do not exist.

## 9. Reporting a new issue

Include: the module states in effect (`/wandb admin module list`), whether the world was
freshly created or migrated, and the exact reject code if a cast was refused
(`/wandb debug spell rejects summary`).
