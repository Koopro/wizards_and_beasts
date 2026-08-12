# WAND_CAST_POSE_SCHEMA.md

Wand-use animation for **Wizards & Beasts**. Extends
`PLAYER_POSE_LAYER_SCHEMA.md` — that document's architecture, blend model, bands
and licence rules apply unchanged and are not restated here.

Status: **schema rev 2. D1–D8 resolved. G-1 and G-2 resolved (§8).**

---

## 1. Scope

Cast poses are authored performances, so per schema R-1 they are **keyframe
passes** (`KeyframePosePass`), not procedural. They occupy the **casting band,
200–299**.

Wave: the binding extensions, the phase read, the proficiency time-scale, the
ownership split, and the clip set that the resolved override roster (§8) names.
Nothing else.

---

## 2. Resolved decisions

| ID | Ruling |
|---|---|
| D1 | Category default clip + per-spell override slot in the binding. |
| D2 | The animation **reads** cast phases; it never defines them. Audit gate (§4). |
| D3 | Clip time-scales with proficiency, floored at **0.7×**. |
| D4 | Pose layer owns the **arm**; `GeoItem` owns the **wand's own bones and tip FX**. |
| D5 | `wandless` variant slot on the binding, falling back to the wand clip. |
| D6 | No distinct interrupt/recoil clip in wave 1. Interrupts blend out on the normal fade. |
| D7 | Broom-cast override authored up front, not discovered in verification. |
| D8 | Cast phase reaches the client on the **existing cast packet**, conditional on §4. |

---

## 3. Canon position on wand movements

**This section is a lore ruling, not a technical one, and it should be read
before the override roster is chosen.**

The books describe a wand movement exactly once with any specificity: Flitwick's
**"swish and flick"** for Wingardium Leviosa (*Philosopher's Stone*, ch. 10,
"Hallowe'en"). *Prisoner of Azkaban* ch. 7 has Lupin drilling the Riddikulus wand
movement without ever describing what it is.

Everything else in popular circulation — the Patronus sweep, the Expelliarmus
slash, the Unforgivables' gestures — is **film choreography or video-game
invention**: tier 4–5 in the project's canon hierarchy, below the books and
companion texts.

Consequences:

- Per-spell clips would make this project the author of ~30 fan-canon wand
  movements. D1's category-default structure exists specifically to avoid that.
- Any override clip beyond Wingardium Leviosa is **acknowledged extrapolation**.
  That is a legitimate choice for a game, but it is a choice, and it is recorded
  here rather than presented as canon.
- Where a film gesture is adopted, the binding entry names the film as its
  source. Where a gesture is invented, the entry says so. No entry claims book
  canon it does not have.

---

## 4. Phase source — audit gate

The pass reads cast phases via `PhaseTimer.between(progress, start, end)`. It
does **not** define its own timeline.

Audit and **stop-and-report**:

1. Does `CastManager` / `CastContext` expose distinct wind-up, release and
   recovery phases, or is casting instant?
2. Does the existing cast packet carry **timing**, or only a fire event?

D8 assumed the packet carries timing. If it carries only a fire event, D8 is
void and the choice returns to Christian — extending `PoseOverride` versus adding
timing to the cast packet is a design decision, not an implementation detail.
**Do not silently client-predict the timing.**

If casting is instant with no phases, report before building anything: a
three-phase clip against a single-tick cast is a spec error, not a tuning
problem.

---

## 5. Binding extensions

Extends the wave-1 binding registry (schema §5.4). Per cast entry:

| Field | Purpose |
|---|---|
| `category` | `SpellCategory` this entry defaults for. |
| `clip` | Third-person animation `Identifier`. |
| `first_person` | Explicit first-person clip id, or a declared fallback. Required, per schema §3.5. |
| `wandless` | Optional variant. Absent → falls back to `clip`. (D5) |
| `broom` | Optional variant used while mounted. Absent → falls back to `clip`. (D7) |
| `phases` | Wind-up / release / recovery windows as fractions, feeding `between()`. |
| `gesture_source` | `book` / `film` / `game` / `invented`. Required. (§3) |
| `overrides` | Per-spell `Identifier` → clip, replacing the category default. (D1) |

Resolution order for a cast: per-spell override → category default. Then variant
selection: broom → wandless → base.

**Category roster is not assumed.** The agent audits the live `SpellCategory`
values and reports the list before authoring entries. Prior phases of this
project have found assumed rosters wrong every time.

---

## 6. Proficiency time-scaling (D3)

```
clipSpeed = clamp(castSpeedMultiplier, 0.7, 1.0)
```

- The clip stretches or compresses to match the actual cast duration, so the
  release frame lands on the effect rather than drifting from it.
- The **0.7× floor** exists because unbounded scaling turns a master duellist
  into sped-up flailing. Past the floor the clip stops accelerating and the
  remaining speed shows as reduced cooldown only.
- `ProficiencyScaler` is the source of the multiplier. The pass reads it; it does
  not recompute the curve.

---

## 7. Ownership during a cast (D4)

| Owner | Responsible for |
|---|---|
| Pose layer (`KeyframePosePass`) | The casting arm — shoulder, elbow, hand orientation. Both person modes. |
| `GeoItem` (wand) | The wand's own bones, bone-toggle variants, tip FX anchors. |

**Both read the same synced `PhaseTimer`.** Neither drives the other. This is the
only thing preventing the arm and the wand's own flourish from drifting apart,
and it is load-bearing: two independent clocks on the same object will desync
under lag, and the desync will look like a wand model bug rather than a timing
bug.

The pose layer never touches wand bones. The `GeoItem` never touches the arm.

---

## 8. Open gates

**G-1 — Override roster. RESOLVED.** Four per-spell overrides; everything else
takes its category default.

| Spell | Justification | `gesture_source` |
|---|---|---|
| Wingardium Leviosa | The one book-described movement (PS ch. 10). | `book` |
| Expecto Patronum | Sustained conjuration, structurally unlike a strike. | `film` |
| Expelliarmus | Duel-defining; the allegiance mechanic hangs off it. | `film` |
| Avada Kedavra | Must not share a clip with ordinary damage spells. | `film` |

Riddikulus was considered and cut: PoA ch. 7 confirms a distinct movement exists
but describes nothing, so it would force an `invented` label for no gameplay
payoff. Revisable — this is binding JSON, not code.

**G-2 — Authoring rig. RESOLVED: agent-authored, with a mandatory parity test.**

Earlier revisions ruled `player.geo.json` hand-built. That was wrong. It is a
**mechanical transcription** of vanilla's `LayerDefinition` — bone pivots and cube
dimensions that already exist in source — not creative modelling, and an agent
reading the source of truth will do it more accurately than a human eyeballing it.

The hazard is coordinate space: Blockbench/GeckoLib geo space and vanilla
`ModelPart` pivot space do not share an origin convention and some axes invert.
An error there is **silent** — every exported rotation is wrong in a way that
reads as bad animation rather than a bad transform.

Therefore the rig ships with a **parity test**: for each bone, a known rotation
applied through the geo path and through the `ModelPart` path must produce the
same resulting transform. **No test, no rig.**

The rig is a separate concern from cast poses and is delivered by its own prompt.
Cast-pose work is gated on that prompt merging.

## 8b. Authoring-rig rulings (post-delivery)

**Slim arm — one rig.** The slim variant changes arm cube width 4→3 and shifts
the left arm cube's x offset, but both shoulder pivots stay at `(±5, 2, 0)`.
Clips read pivots and rotations and never cube dimensions, so a slim rig would
animate identically. A second file buys Blockbench preview fidelity at the cost
of two rigs that can drift — and a drifted rig is a silent source of wrong
exports, which is the exact failure the parity test exists to catch. **Single
rig. Not revisited without new evidence.**

**Comment keys — durable only.** `player.geo.json` introduced a `_comment` key
(no other geo asset in the project had any comment mechanism; GeckoLib's Gson
loader ignores unknown keys). Rule: a comment may describe **what a file is** —
"authoring-only, never rendered" — and must never describe **state**. This repo
already carries 93 creature JSONs whose `"_comment": "PLACEHOLDER box rig"`
markers stopped being true and corrupted an audit into reporting every creature
as a cube. State-describing comments become lies; identity-describing comments
do not.

**Coordinate convention — recorded, not rediscovered.** `y_model = 24 - y_geo`,
the reflection `diag(1,-1,1)`. Conjugating a rotation by it negates X and Z and
leaves Y unchanged. This is **not** vanilla's `scale(-1,-1,1)`, which is a 180°
rotation about Z and negates X and Y instead. Conflating the two is a known live
defect source in this project.

---

## 9. Out of scope

- Nonverbal casting, Occlumency, Legilimency poses.
- Duelling stances or any idle wand-at-ready pose (one-frame clips, later wave).
- Wand bone-toggle work, tip FX, particle timing.
- Interrupt / recoil clips (D6).
- Changing `CastManager`, `CastContext` or `ProficiencyScaler` in any way. The
  pass reads them; it does not modify them.
- Spell balance, cooldowns, damage.
- Migrating any wave-1 handler into a pass.
