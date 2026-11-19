# Player Animation

What the player's body does, when it does it, and how to add to it. Reviewed in a real client on 2026-11-18. The
contact sheets in this folder are the record (`p1_*` wand, `p2_*` interactions, `p3_*` movement).

## How it works

Everything is a pass on the existing pose layer (`client/pose/PlayerPoseLayer`). There is no animation library
and no second player renderer. Passes run in priority order, and each one blends over the passes before it.

| Pass | Priority | Drives |
|---|---|---|
| `ItemUsePosePass` | 50 | Item holds read from the render state: book, card, optic, carve, polish, wind, brand, **drink, eat, read** |
| `WandReadyPass` | 60 | The wand held ready at rest, and the raise when it is drawn |
| `GesturePosePass` | 70 | One-shot gestures the server starts (`PlayerGesture`) |
| `MovementPosePass` | 110 | **Falling and landing** |
| `FlightPosePass` | 120 | Flight attitude (creative and ability flight) |
| `CastPosePass` | 200 | Casting: the build-up, the strike, the settle |
| `HeritageProportionPass`, `ApparitionPosePass` | 300+ | Body proportions, the Apparition twirl |

Two older handlers run before the layer: `BroomRiderPoseHandler` (now eases in on mount and out on dismount) and
`PetrifyRenderHandler`. The layer skips a petrified player completely.

### Multiplayer sync

There are three ways a pose reaches other players, and only one of them adds network traffic.

- **Derived from vanilla's synced state.** No packets. This covers item use, the held stack, ground contact and
  position, so drinking, eating, reading, the ready hold, falling and landing all work this way.
- **Server moments for casts.** `CastAnimationSync` sends `SpellCastAnimationS2CPayload`. This existed before
  this work and is unchanged.
- **Server moments for everything else.** `PlayerGestures.play(player, gesture)` sends
  `PlayerGestureS2CPayload` to the player and to everyone tracking them. The client never invents the timing.

### No snapping

Every pose eases in and out:

- Casts crossfade from whatever was on screen (`ClientCastAnimationState.Blend`).
- Derived holds use `KindFade`, which remembers each player's current pose and eases between poses.
- `PoseBuilder.opWeight` lets one pass crossfade two of its own poses.

## What plays when

### Wand

| Moment | Pose | Source |
|---|---|---|
| Wand in the main hand, not in use | Ready hold: arm lifted, point level, slow breath. Part of the walk swing is kept; mostly given up when running | derived |
| Wand drawn | Raise that overshoots slightly, then settles (7 t) | derived |
| Cast held | Build-up for the spell's category; holding longer raises it further and adds a tremble | cast payload |
| Cast released | Strike, then settle | cast payload |
| Cast refused or interrupted | Arm lets go over 4 t | cast payload |
| Casting from a broom | Free hand stays on the broom handle, less twist | cast payload + ride data |
| Key `I` (Inspect Wand) | Wand raised before the eyes and turned along its length; free hand steadies the tip | gesture `wand_inspect` |

Category sets (`CastPoseConstants`):

| Set | Look | Source label |
|---|---|---|
| COMBAT | Over the shoulder, then thrown out | game |
| UTILITY | Lift, then an open point | invented |
| DEFENSE | Guard across the face, free hand up | invented |
| DARK_ARTS | Low draw, then a hunched whip forward | invented |

Per-spell overrides (schema G-1):

| Spell | Look | Source label |
|---|---|---|
| Wingardium Leviosa | Swish and flick | book |
| Expelliarmus | Slash | film |
| Expecto Patronum | Sweep up, free arm opens | film |
| Avada Kedavra | Cocked by the head, then a driven jab | film |
| Protego | Raised guard | invented |

### Interactions

| Moment | Gesture | Hook |
|---|---|---|
| Drinking (any `ItemUseAnimation.DRINK`, vanilla potions included) | Cup to the lips, head tipping back | derived |
| Eating (any `EAT`) | Hand to mouth, chewing bob | derived |
| Reading a spell page or recipe page | Two-hand book with a page turn every 20 t; one hand if the other is full | derived |
| Bestiary, Ministry Handbook | `open_book` | item `use` |
| Marauder's Map | `unfold` | item `use` |
| Two-Way Mirror, Sneakoscope (focus on) | `hold_up` | item `use` |
| Deluminator (any click) | `click_up` | item `use` |
| Pensieve, Mirror of Erised | `peer` | item / block use |
| Sword of Gryffindor on a Horcrux | `stab` | item `use` |
| Cauldron: starting a brew, clicking a brew in progress | `stir` | block use |
| Cauldron: adding an ingredient | `toss` | block use |
| Wand bench, Vanishing Cabinet, examination desk, Floo hearth | `reach` | `GestureInteractHooks` |
| Floo departure | `floo_throw`, lasting the departure wind-up; stopped if the departure is cancelled | `FlooDeparture` |
| Pulling a Mandrake | `pull` | `MandrakeCropBlock` |
| Grooming a creature | `stroke` | `Groomable` |
| Picking up a creature's sign | `stoop` | `CreatureSign` |
| Werewolf change begins | `hunch`, lasting the configured delay; stopped on abort | `WerewolfTransformService` |

### Movement

| Moment | Pose |
|---|---|
| Falling faster than 0.55 blocks per tick | Arms up and out with a flail, legs apart, head down |
| Landing after a fall | Knees and waist bend; deeper the faster the fall (9 t) |
| Broom mount and dismount | Riding crouch eases in (6 t) and out (5 t) |
| Running with a wand | Ready hold mostly gives way to the running swing |

## Adding a gesture

1. Add an entry to `PlayerGesture` with a wire id and a default length.
2. Add a `Clip` to `GesturePoseConstants.clip`. Keys hold the main arm, the free arm (written as the left arm), each
   arm's share, the head pitch, the lean, and an optional circle or tremor.
3. Call `PlayerGestures.play(player, gesture)` at the server moment. Use `stop` if that moment can be cancelled.
4. `InteractionPoseTest` checks that the new clip starts at 0, ends at 1 and does not jump at any key.
5. Shoot it with `WB_EQUIPMENT_SHOWCASE=anim2`, which plays every gesture.

## Sign conventions

Angles are in degrees and written for a right-handed player:

- Negative `xRot` swings a limb forward.
- On the main arm, negative `yRot` turns it inward.
- The free arm is written as the left arm: inward is `+yRot`, outward is `-zRot`.

The passes mirror all of this for a left-handed player.

## Not supported, and known limits

- **First person shows only the held item.** The body poses (drinking tilt, stoop, falling, gestures) are
  third-person only, as in vanilla. In first person, vanilla handles drinking, eating and drawing; the cast and the
  wand inspect move the held wand. The per-category gestures do not have first-person versions yet: first person
  uses the one generic wand movement for every category.
- **Gestures always use the main arm**, even when an item was used from the off hand. The wire carries no hand.
  Drinking and eating are the exception and follow the real hand.
- **Animagus transformation has no gesture.** The form's model replaces the player on the same tick, so a gesture
  would never be seen.
- **No heritage-specific gait.** Veela and house-elves have no walk of their own. A werewolf has the change hunch
  only, not a human-form gait.
- **No separate broom mount or dismount gesture.** The riding crouch blending in and out covers both. There is
  also no lean-back pose for landing a broom.
- **Remote creative fliers** can briefly read as falling if they dive fast, because only the local player's flying
  flag is synced. Elytra flight, riding, water and ladders are excluded.
- **Screens that open on the client** (Bestiary, Handbook, Two-Way Mirror) cover the player's own view. The
  gesture is for people watching and for the F5 view.
- **The read pose was not photographed.** Spell pages and recipe pages only channel when they carry content. The
  pose itself is the book hold plus a page turn.
- **The cape** still uses vanilla's cloak physics, which already respond to falling. It does not read the posed
  legs.
- **Armour-slot trousers** worn under a posed body were checked in the riding, falling and landing frames. They
  follow the legs.
