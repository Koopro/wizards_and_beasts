# FLIGHT_POSE_CONSTANTS.md

Authored starting values for `FlightPosePass`, filling schema §7.3.

Originally written from scratch for Wizards & Beasts. No values derived from any
other project.

**These are a tuned starting point, not final.** They exist so in-game
verification tests the blend rather than testing obviously-wrong placeholders.
Expect to adjust in Blockbench once you can see them.

---

## 1. Conventions

- All values in **degrees**. Convert at the call site.
- `xRot` positive pitches a limb **backwards / upwards at the front**; negative
  swings it **forward**. An arm at `xRot = -90` points straight ahead.
- `zRot` on arms: signs **mirror** between left and right. The table gives the
  right arm; negate `zRot` for the left. If your first in-game pass shows the
  arms crossing into the torso instead of spreading, flip the sign globally —
  that is the one convention I cannot verify without seeing it render.
- `HEAD` values are **additive on top of** the player's real look pitch, not
  replacements. Use `ADD`. Everything else is `SET_SHORTEST`.
- `BODY` is the virtual part (§3.9) — a `PoseStack` transform, not a `ModelPart`.

---

## 2. `BODY` — the pivot problem first

Rotating the body about its default origin pivots the player around their feet,
which looks like falling over rather than leaning. The lie-flat pose needs the
rotation centred near the chest.

That's what the `X2`/`Y2` targets exist for (§3.3) — translate applied **after**
rotation:

```
Y   = +0.75      // rise to chest height, before rotation
X_ROT = <state>  // pitch about that point
Y2  = -0.75      // return, after rotation
```

`0.75` blocks ≈ 12 model units ≈ mid-chest on a standard player. Tune this
first — it has more visual impact than any rotation value in the table below.

| State | `BODY` X_ROT | Y / Y2 | Z_ROT (bank) |
|---|---|---|---|
| `HOVER` | `-8` | `+0.35 / -0.35` | `0` |
| `GLIDE` | `-35` | `+0.75 / -0.75` | see §4 |
| `PROPELLED` | `-78` | `+0.75 / -0.75` | see §4 |

`PROPELLED` is deliberately not `-90`. A fully horizontal body reads as a rigid
plank; a few degrees short keeps the head leading and the silhouette alive.

---

## 3. Third person

### `HOVER` — upright, treading air

| Part | X_ROT | Y_ROT | Z_ROT |
|---|---|---|---|
| `HEAD` | `+2` | `0` | `0` |
| `CHEST` | `0` | `0` | `0` |
| `RIGHT_ARM` | `-12` | `0` | `+18` |
| `LEFT_ARM` | `-12` | `0` | `-18` |
| `RIGHT_LEG` | `-14` | `0` | `+4` |
| `LEFT_LEG` | `-4` | `0` | `-4` |

Asymmetric legs are intentional. A perfectly symmetrical idle reads as a
mannequin; the offset makes it read as a person holding position.

### `GLIDE` — leaning into travel, arms out

| Part | X_ROT | Y_ROT | Z_ROT |
|---|---|---|---|
| `HEAD` | `+22` | `0` | `0` |
| `CHEST` | `-6` | `0` | `0` |
| `RIGHT_ARM` | `-38` | `-12` | `+52` |
| `LEFT_ARM` | `-38` | `+12` | `-52` |
| `RIGHT_LEG` | `+12` | `0` | `+6` |
| `LEFT_LEG` | `+16` | `0` | `-6` |

`HEAD` positive compensates the body pitch so the player still looks where they
are going rather than at the ground. This is why `HEAD` is `ADD`.

### `PROPELLED` — swept back, committed

| Part | X_ROT | Y_ROT | Z_ROT |
|---|---|---|---|
| `HEAD` | `+58` | `0` | `0` |
| `CHEST` | `-10` | `0` | `0` |
| `RIGHT_ARM` | `+28` | `-8` | `+14` |
| `LEFT_ARM` | `+28` | `+8` | `-14` |
| `RIGHT_LEG` | `+6` | `0` | `+3` |
| `LEFT_LEG` | `+9` | `0` | `-3` |

Arms **positive** here — swept back along the body, not out front. Legs nearly
straight and trailing.

---

## 4. Bank into turns

Applied on top of the state pose, driven by the yaw delta input (§7.2):

```
bankDegrees = clamp(yawDelta * 2.6, -30, +30)
```

- `BODY` `Z_ROT` `SET_SHORTEST` ← `bankDegrees`
- outer arm `Z_ROT` `ADD` ← `bankDegrees * 0.35`
- inner arm `Z_ROT` `ADD` ← `-bankDegrees * 0.20`

Smooth `yawDelta` over ~4 ticks before using it, or the bank strobes on
mouse jitter. `HOVER` gets no bank.

---

## 5. First person

Only `RIGHT_ARM` / `LEFT_ARM` matter, selected by `FirstPersonContext`. These are
**not** the third-person values — a third-person shoulder rotation puts the hand
outside the viewport.

| State | X_ROT | Y_ROT | Z_ROT | Y offset |
|---|---|---|---|---|
| `HOVER` | `-4` | `0` | `+6` | `0` |
| `GLIDE` | `-15` | `-6` | `+22` | `-0.06` |
| `PROPELLED` | `+10` | `-4` | `+9` | `-0.14` |

The `Y` offset drops the arm as speed rises so it clears the crosshair. Mirror
`Y_ROT` and `Z_ROT` for the off arm.

Wand in hand is the case to check — an arm angle that looks fine empty-handed
can put the wand tip through the camera near plane.

---

## 6. Blending

- State-to-state blend: **6 ticks**, ease-in-out.
- Fade in on override activate: **8 ticks**. Fade out: **5 ticks** — releasing
  should feel quicker than committing.
- `propulsion` drives `GLIDE` → `PROPELLED` continuously rather than switching at
  a threshold. Treat `PROPELLED` as the far end of that blend, not a third
  discrete pose, and the transition stops popping.

---

## 7. Tune in this order

1. `BODY` Y / Y2 pivot height. Everything else is judged against it.
2. `PROPELLED` `BODY` X_ROT. Sets the ceiling the other states read against.
3. Arm `Z_ROT` spread on `GLIDE`. The most visible single value.
4. `HEAD` compensation. Wrong here reads as "the player isn't looking where
   they're flying," which people notice without being able to name it.
5. Bank multiplier and clamp.
6. First person, with a wand equipped.

Legs last. Nobody looks at the legs.

---

## Implementation notes (added on transcription, 2026-08-12)

The values above are the authored source. Two were changed on the way into
`client/pose/FlightPoseConstants.java`, both logged in `AUDIT_PUNCHLIST.md`:

**`HEAD` is negated.** §3 gives `+2 / +22 / +58` and states the purpose — "so the
player still looks where they are going rather than at the ground". In Minecraft
that requires a *negative* number: vanilla sets `head.xRot = state.xRot *
DEG_TO_RAD`, and entity pitch is positive looking **down**, so a positive head
rotation tips the gaze further into the ground on a body already pitched forward.
That was the reported symptom from the first in-game pass. The magnitudes here
are unchanged; only the sign moved, and
`FlightPoseConstantsTest.headCompensationLiftsTheGazeRatherThanDroppingIt` pins
it because it is the value most likely to be "corrected" back.

The underlying trap: `BODY` is a `PoseStack` transform and the limbs are
`ModelPart`s on the far side of vanilla's `scale(-1, -1, 1)`, which negates the X
rotation axis. A head compensation therefore carries the **same** sign as the
body pitch it cancels.

**§5's `Y offset` is read as blocks.** `-0.06` and `-0.14` are invisible as
`ModelPart` units (sixteenths of a block); blocks is the only reading under which
the field does what §5 says it does. Converted at the call site.

**§6's third bullet is not implemented.** Continuous propulsion-driven
`GLIDE` → `PROPELLED` needs a continuous value on the wire, and `PoseOverride`
carries a discrete `FlightPoseState` — a schema change rather than an
implementation detail, so it was logged for a ruling instead of made
unilaterally. The eased six-tick cross-fade ships in its place: no pop, but not
speed-proportional.
