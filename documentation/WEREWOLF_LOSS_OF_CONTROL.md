# Forced Lycanthropy & Loss of Control

How a werewolf stops being a player for a night, and why the server wins the argument.

Code: `at.koopro.wizardsandbeasts.heritage.werewolf` (rules, state, controller),
`event.heritage.WerewolfMoonHandler` (lifecycle), `event.heritage.WerewolfControlHandler` (constraints),
`client.heritage.WerewolfClientControlHandler` (client courtesy layer).

---

## 1. The one condition

Everything keys on a single stored boolean:
`PlayerHeritageData` custom flag **`werewolf_loss_of_control`**.

Not "is it a full moon and are they in wolf form and have they no Wolfsbane" — that is a derivation, and
three call sites deriving it will eventually disagree. One flag is set when control is taken and cleared
when it is given back. Every consumer asks that flag and nothing else.

It lives on the heritage attachment rather than in a new one because that attachment is already
`copyOnDeath`, already serialised to disk, and its flag map already rides `HeritageDataSyncS2CPayload` to
the owning client. Persistence across relog, survival across death, and client sync all come free, and
requirement 6 is satisfied without a new payload.

## 2. The sequence

| Phase | Where | What |
|---|---|---|
| Moon rises | `WerewolfMoonHandler.scan` (every 20 ticks) | full moon (`phase == 0`) + night + sky-lit, non-fixed-time dimension |
| Exposure | same | `+werewolfExposureGain` under open sky, `-werewolfExposureDecay` under a roof, clamped to `[0, threshold]` |
| Onset | `WerewolfTransformService.beginForcedTransform` | state → `TRANSITIONING`, **loss-of-control flag set**, particles + `werewolf_transform` sound, completion tick written to the flag map |
| Throes | `tickPending` (every tick) | frozen in place, `CRIT` particles every 4 ticks |
| Landing | `completeTransform` | `FormSystemAPI.setPlayerForm("werewolf_wolf")`, state → `TRANSFORMED`, equipment stripped, attributes applied, flag re-derived (cleared if medicated) |
| The night | `FeralController.tick` + `WerewolfControlHandler` | driven body, no hands |
| Dawn | `WerewolfMoonHandler.scan` | revert to `werewolf_human`, flags cleared, Weakness + Slowness + Hunger |

The deadline is written to persistent state rather than held in a static queue, so a player who logs out
mid-change comes back mid-change instead of stranded in `TRANSITIONING` forever.

Control is taken at the **onset**, not at the landing: the throes are the one window in which a player
could otherwise drink, cast or eat their way out of a change that has already started. The controller does
not drive during them — `tickPending` is already holding the body still — so `WerewolfControlHandler` skips
a player with a change pending while still applying every constraint.

`TransitionManager` is deliberately **not** used to run the change: it hard-caps duration at 30 ticks and
`werewolfTransformDelayTicks` is configurable well past that. It is still respected as a guard, so a
transition started by another system is never trampled.

## 3. How control is actually taken

The client layer is a **courtesy**, not the enforcement. Three server-side mechanisms do the work:

1. **Velocity overwrite.** `FeralController.applyDrive` writes `setDeltaMovement` every tick and sets
   `hurtMarked`, which is what makes the server emit a velocity packet the client must apply. Same
   mechanism `TransitionManager` uses to freeze a player mid-change.
2. **Absolute rotation packets.** The client sends its own yaw and pitch every tick and the server accepts
   them, so a server-side `setYRot` alone is overwritten before anyone sees it. `ClientboundPlayerRotationPacket`
   sets the client's rotation absolutely. Sent every 3 ticks, not every tick: every tick is a hard camera
   lock that fights the mouse into visible jitter.
3. **The drift guard.** Layers 1 and 2 assume a cooperating client. So the controller watches where the
   player *actually* ends up: 20 consecutive ticks of horizontal movement whose dot product with the drive
   direction is negative counts as defiance, and past `werewolfDriftCorrectionBlocks` from the last
   compliant position the server teleports them back. Both conditions are required — distance alone would
   punish knockback and falling, direction alone would fire on every glancing collision. The snap target is
   always a position the player genuinely occupied, so it can never teleport anyone into a wall.

A client that ignores all three still cannot leave.

## 4. What the player cannot do

Enforced in `WerewolfControlHandler` unless noted:

- use an item, or start using one — `RightClickItem`, `LivingEntityUseItemEvent.Start`
- interact with a block or entity, or break a block — `RightClickBlock`, `EntityInteract`,
  `EntityInteractSpecific`, `LeftClickBlock`
- open or hold open a container — the client refuses to open one (`ScreenEvent.Opening`), and the server
  calls `closeContainer()` every tick because `PlayerContainerEvent.Open` is not cancellable
- throw items away — `ItemTossEvent`, **with the stack put back first**: cancelling that event alone
  destroys the item (its own javadoc says so), so the cancel is conditional on the inventory having room
- cast — `SpellNetworkGuards.canUseWand`, which is the one guard every wand packet passes, so one check
  covers casting, assigning, selecting and the Leviosa adjustment
- cancel the transformation — `requestVoluntaryRevert` refuses while the flag is set, and it is the only
  voluntary route out

**Melee is deliberately allowed.** A wolf's answer to everything is its teeth, and `AnimagusEvents` already
establishes the convention that a transformed player keeps their claws while losing their hands. The player
cannot aim it in any case — the controller owns their facing. (There is also a mechanical reason:
`AttackEntityEvent` fires *inside* `Player.attack`, so cancelling it would break the controller's own bite.)

## 5. Wolfsbane

Canon is emphatic: Lupin still becomes a wolf. The potion preserves his **mind**, not his shape. So by
default `werewolfWolfsbaneSuppressesTransform = false` and Wolfsbane decides one thing only — whether the
loss-of-control flag is set when the change lands.

`WerewolfMoonHandler.refreshControl` re-derives that flag every scan, so both directions work mid-night:
a dose drunk at midnight hands the wolf back to its player, and a dose running out at 3am takes them away
again. Deriving it once at transformation time would have made Wolfsbane a thing you must drink *before*
the change, which is not what the potion is.

A medicated werewolf is not stripped of the shape at dawn — they are *offered* it
(`markVoluntaryStay`), and keep it until they give it back
(`/wandb player heritage werewolf revert`) or the potion expires.

Two sources are honoured as "medicated": the `wizards_and_beasts:wolfsbane` effect (what a brewed dose
grants, and what expires) and the older `PlayerAbilityData.wolfsbaneActive` boolean, so neither path
silently stops working.

**Acquisition.** Brewed — `brewing_recipes/wolfsbane_potion.json`, brass cauldron, 2 fern + spider eye +
nether wart, 600 ticks of heat. Also `/wandb player heritage werewolf wolfsbane <player> [amplifier]`
(admin), which is the path `werewolfWolfsbaneDurationTicks` and
`werewolfWolfsbaneAmplifierExtendsDuration` govern; the brewed bottle carries its own duration in its
datapack file, because a brew's strength is a datapack's business in this mod.

**Drunk in the throes.** `beginForcedTransform` refuses to start a change while the potion is up (when
suppression is enabled at all), so `tickPending` answers the same way: a dose landing during the
transformation window aborts it, clears the exposure counter and hands the body straight back. Without
that, the potion's one job would depend on beating a 50-tick window rather than on being drunk.

**What it looks like** (`WolfsbaneHandler`). Aconite steam — slow `SPORE_BLOSSOM_AIR` motes at chest
height, denser once the drinker is actually in wolf shape. Spawned server-side so *other players see it*,
which is the point: on a shared server, whether the werewolf next to you took their potion is information
you want. The last 5 seconds are called out on the action bar with an escalating brewing-stand chime,
because of what happens at zero — a werewolf under a full moon whose Wolfsbane runs out loses control of
their character inside one scan, and that must never arrive unannounced.

No screen tint, deliberately: every tint layer in the mod is gated on `Config.reduceScreenEffects`, so a
tint would mean the accessibility setting switches off the only warning that the thing keeping you in
control is about to stop.

## 6. Targeting

Three files, split so the interesting part is testable without a game:

| File | Minecraft types? | Job |
|---|---|---|
| `FeralTargetPriority` | no | the bands and their weights |
| `FeralTargetSelection` | no | pick-best, and should-switch |
| `FeralTargeting` | yes | classify an entity, eligibility, run the query |

**Bands**, highest first:

| Band | Weight | What lands in it |
|---|---|---|
| `PLAYER` | 1000 | any player who is not pack |
| `VILLAGER_OR_GOLEM` | 750 | `Npc` (villagers, wandering traders) and `AbstractGolem` |
| `ANIMAL` | 500 | `Animal`, `WaterAnimal` |
| `HOSTILE` | 250 | `Enemy` |
| `OTHER` | 0 | anything else living |

Score is **band weight − distance in blocks**, so distance orders *within* a band and can never cross
one. That is an invariant, not a coincidence: `FeralTargetPriority.MAX_SCORED_DISTANCE` (216 = the 96
radius cap × 1.5 rage bonus × 1.5 leash) must be smaller than every gap, and
`FeralTargetSelectionTest.bands_areSpacedFurtherApartThanAnyReachableDistance` asserts it. It caught a
real bug during development — the original spacing of 100 let a point-blank animal outscore a distant
villager.

Classification order matters twice, both non-obvious:
- golems before `Animal`, because they live in `entity.animal.golem` and read as livestock;
- `Enemy` before `Animal`, because hoglins and friends extend `Animal` — a hoglin is a fight, not a meal.

**Switching** (`FeralTargetSelection.shouldSwitch`) — the wolf drops what it is chasing only when:
1. the target dies, is removed, or leaves the leash (`radius × 1.5`), **or**
2. something in a *strictly higher band* comes into range.

A nearer target in the same band never steals focus. Validity is expressed by passing `null` for the
current candidate, so "it died" and "there was never one" are one branch rather than two.

Line of sight is required at acquisition — without it a wolf pins itself to a cow behind a hill and
grinds against terrain forever. The entity query runs at most once per `RETARGET_INTERVAL_TICKS` (10);
between scans a still-valid target is returned without touching the world.

## 7. Movement, and why there is no pathfinding

There is no vanilla navigation to borrow. `PathNavigation`, `PathFinder` and every `NodeEvaluator` are
built around a `Mob` — the navigation constructor takes one, the evaluator asks it for its path types and
its `FOLLOW_RANGE` — and a `ServerPlayer` is not a `Mob`. A real path would mean spawning and maintaining
a proxy mob per transformed werewolf purely to ask it questions: an entity that can be hit, targeted,
persisted and desynced, for a hunt that happens a few nights a month.

So: **direct movement with local obstacle avoidance.**

- `probe` asks `level.noCollision(player, box.move(dir))` — the same question vanilla movement asks — so
  fences, carpets, open trapdoors and modded geometry answer correctly with no special case each.
  Blocked at foot height but clear one block up ⇒ `STEP_UP` (a hop).
- `wouldFall` refuses a heading whose ground is more than 4 blocks down, *unless the prey is down there*.
  Water counts as ground: a wolf will jump in after you.
- `steer` fans out through `{0°, ±35°, ±70°, ±105°}` and takes the first heading that passes. Straight
  ahead is tried first, so on open ground this costs one probe.

This handles doorways, fences, one-block steps and ledges — the whole of what a charge across open
terrain actually meets. It will not solve a maze. A werewolf is not supposed to.

Attacks go through `Player.attack(target)`, not a hand-rolled `hurt`, which is what makes the bite read
from `Attributes.ATTACK_DAMAGE` — the attribute `WerewolfAttributes` raises for the form. Enchantments,
knockback, the damage tick and the attack-strength curve all come with it.

## 8. Rage

0–100, on the controller (not persisted — a night's fury does not survive a relog).

| Event | Change |
|---|---|
| target acquired ("seeing prey") | +8 |
| bite lands | +5 |
| damage taken | +3 per half-heart, capped at +25 per blow |
| every 40 ticks, hunting | −1 |
| every 40 ticks, idle | −3 |

Damage is read from `LivingDamageEvent.Post`, i.e. **after** armour and resistances — a wolf in enchanted
plate is not enraged by a punch that did nothing.

At full rage: charge speed ×1.35, aggro radius ×1.5, bite cooldown −4 ticks (floored at 4).
`getRage()`/`addRage()` are public so a frenzy tier or a HUD can hang off them.

## 9. Extending it

`FeralController` is a plain class with `protected` seams, held per-player in a `PlayerScopedState`:

- pack focus fire → override `selectTarget`
- frenzy tiers → read `getRage()` from `onBite`
- stalking, circling, leaping → override `drive`
- a howl that summons → override `onTargetAcquired`
- a different gait around obstacles → override `steer` or `probe`

None of those need to touch the constraint wall or the transform sequence. Target *rules* are changed in
`FeralTargetPriority` / `FeralTargetSelection` instead, where they stay unit-testable.

## 10. Testing it

Two admin commands, both shortcuts through conditions the system checks for itself rather than back
doors around them:

| Command | What it does |
|---|---|
| `/wandb player heritage werewolf moon` | winds the world clock to the next full-moon night. **Only the clock** — no transform, no exposure change, no sky bypass. A werewolf in a cellar still will not turn. |
| `/wandb player heritage werewolf transform [player]` | starts a forced change now, skipping only the moonlight wait. Still refuses for a non-werewolf, an existing wolf, a change already in flight, and Wolfsbane suppression. |
| `/wandb player heritage werewolf wolfsbane <player> [amp]` | doses from code, using the config duration |
| `/wandb player heritage werewolf status [player]` | moon, exposure meter, state, form, Wolfsbane, loss of control, staying-by-choice |
| `/wandb player heritage werewolf revert` | the medicated player's voluntary exit |

### Manual smoke tests

Run as a werewolf (`/wandb player heritage set <you> werewolf bitten`) unless noted.

1. **Forced transform under open sky.** `moon`, stand outside. Within ~15s of scans: TRANSITIONING,
   particles + change sound, then the wolf form lands with a howl. `status` shows loss of control true.
   → Inventory will not open. Right-click does nothing. Blocks will not break. Wand casts are refused.
   Movement is not yours; the wolf runs at the nearest valid target and bites.
2. **Shelter blocks it.** `moon`, then stand under a full roof from the start. Exposure on `status`
   should fall, not rise, and no change should begin. Step outside → it fills and the change comes.
3. **Wolfsbane restores control.** While feral, `wolfsbane <you>`. Within one scan (≤1s) loss of control
   goes false: your keys answer again, the wolf form and its attributes stay. Aconite haze on you,
   visible to other players. `revert` now works.
4. **Wolfsbane suppresses (opt-in).** Set `werewolfWolfsbaneSuppressesTransform = true`, dose first, then
   `transform` → refused. Dose *during* the TRANSITIONING window → the change aborts and hands the body
   back.
5. **Wolfsbane expiry.** Dose with a short duration during a full moon. Last 5 seconds warn on the action
   bar with a rising chime; at zero, control is taken again within one scan.
6. **Sunrise reverts.** `/time set day` while transformed → back to `werewolf_human`, wolf attributes
   removed, night vision and scent gone, Weakness + Slowness + Hunger applied. Unmedicated only —
   a medicated werewolf is *offered* the shape and keeps it until `revert` or the potion runs out.
7. **Dimension with no sky.** Transform, then enter the Nether → reverts. `moon` there does nothing.
8. **Death and relog.** Die as a wolf → respawn human, not a wolf with human stats. Log out mid-change
   and back in → the change resumes; you are **not** invulnerable (the old bug).
9. **Animagus still works and is still voluntary.** As an Animagus in beast form: no inventory, no item
   use, no block break, **no casting** (this was the gap — the wall never covered the cast packet), but
   the ability key still toggles you back at will. Cat form's night vision no longer strobes.
10. **Other heritages untouched.** As Wizardkind/Goblin/Veela: `moon` changes nothing about you, no
    constraints apply, casting and inventory work normally.

## 11. Known limitation

The wolf's combat profile (health / speed / armour / attack / knockback) is config-driven rather than
datapack-driven, because the form system has no per-form stat layer — `SizeProfile` carries geometry and
reach, not combat. When a `form_stats` datapack type lands, `WerewolfAttributes` becomes its applier and the
five config keys become that file's defaults. Nothing else changes.
