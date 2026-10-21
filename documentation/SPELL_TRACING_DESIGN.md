# Spell tracing — design

Status: **implemented on `feat/spell-tracing`** (2026-10-21). Source of the requirement: the 2026-10-21 master
prompt §3.2 / §6 — *every newly learned spell passes a timed tracing challenge; no normal route bypasses it*.

## 1. What changes for the player

Learning still starts where it always did:

| Route | Before | After |
|---|---|---|
| Book / torn page / notes (`SpellSourceItem`) | hold right-click 3 s → spell known | hold right-click 3 s → **trace screen opens** → pass → spell known (page spent only now) |
| Skill-web node with `learn_spell` (30 nodes) | allocating the node wrote the spell straight in, **skipping every eligibility gate** | allocating the node opens the trace for its spell; the spell is a *pending lesson* until traced. Left-click the allocated node to trace again |
| Lumos → Nox (`swap_active_spell learn:true`) | casting Lumos taught Nox | one Lumos trace teaches both (Nox is Lumos's *companion*). The cast-time grant stays only for saves that knew Lumos before tracing existed |
| `/wandb spell learn`, admin player panel, `SpellDevKit` | direct write | **unchanged** — operator overrides, documented as such |

## 2. Data model

All in `spell/learning/tracing/` (common code, no client classes):

- **`TraceDifficulty`** — enum `BASIC, INTERMEDIATE, ADVANCED, DARK, MASTERY`. Each row holds the corridor
  half-width, the *pace* (canvas widths per second the clock is set at), the required accuracy, the
  path-length band, the template pool and the retry delay. The time limit is per path:
  `40 ticks + length ÷ pace`, so a long rune never gets less time per stroke than a short arc. Values are explicit constants in one place, tested for ordering (each row is at least as hard as the
  previous on width and accuracy).
- **Difficulty derivation** (`TraceDifficulty.forSpell`) — from data every spell already carries, no new JSON
  field:
  1. a mastery requirement (`requiredMasteryTier`) → `MASTERY`
  2. category `DARK_ARTS`, or Spell Law class `DARK`/`UNFORGIVABLE` → `DARK`
  3. a required skill node or profession, or a proficiency prerequisite → `ADVANCED`
  4. a plain "knows X" prerequisite → `INTERMEDIATE`
  5. otherwise → `BASIC`
- **`TracePath`** — an immutable polyline in a unit square (canvas space), plus its cumulative arc lengths.
  Start = first point, goal = last point.
- **`TraceChallenge`** — `id` (random long nonce), `spellId`, `difficulty`, `seed`, `path`, `issuedAt`
  (server game time), `source` (below). Server-only; the client receives the path points, never the seed.
- **`TraceSource`** — sealed: `Book(hand, item, spellId, spentOnRead)` or `Lesson(nodeId)`.

## 3. Path generation

`TracePathGenerator.generate(seed, difficulty)`:

1. Pick a template from the difficulty's pool. Templates: `ARC`, `SWISH` (swish-and-flick), `WAVE(n)`,
   `ZIGZAG(n)` (teeth rising 100–130 % of their run, corners 75–90°), `RUNE(n)` (self-avoiding orthogonal
   walk on a 4×4 grid), `HOOK` (line into a ¾ curl), `SPIRAL` (1.35–1.6 turns, mastery only).
   Pools: BASIC arc/swish/wave₁/hook · INTERMEDIATE wave₂/zigzag₂/rune₄/hook · ADVANCED wave₃/zigzag₃/rune₆ ·
   DARK zigzag₄/rune₆/rune₇ · MASTERY rune₇/rune₈/spiral.
2. Build control points with seeded jitter, apply one of 8 symmetries (4 rotations × mirror) and an optional
   direction reversal, then fit into the canvas with a margin.
3. Smooth curved templates (Catmull-Rom) and resample to an even spacing.
4. **Fairness check** (`TracePathValidator`), and retry with `seed + attempt` up to 32 times; if every
   attempt fails, fall back to a serpentine (rows four half-widths apart, sized to the length band) that the
   test suite proves valid for every difficulty. Measured fallback rate: under 1 % of seeds per row.

The fairness check enforces:

- every point is inside the canvas by at least half-width + margin;
- **no near-self-contact:** any two points more than 4 half-widths apart *along* the path are at least
  2 half-widths + a gap apart in the plane. This means the corridor never touches itself, so "which part of
  the path am I on" is never ambiguous and the path cannot be short-cut;
- start and goal are at least 3 half-widths apart;
- the length falls in the difficulty's band, so the required pointer speed (length ÷ time) stays below a
  fixed human-comfortable maximum.

Determinism: same `(seed, difficulty)` → same path (Java float maths is strict since 17). Tests cover
10 000 seeds per difficulty.

## 4. Judging (server-authoritative)

The client sends **the pointer samples, not a verdict**: up to 2 048 points, quantised to `u16` per axis, plus
an `assist` flag. `TraceJudge.judge(path, samples, difficulty, assist)` returns either a pass with an accuracy
score or a failure with a reason and the sample index where it happened:

| Reason | Rule |
|---|---|
| `NOT_STARTED` | first sample not inside the start marker (radius = 1.5 half-widths) |
| `LEFT_PATH` | any sample, *or any point on the line between consecutive samples* (densified to ¼ half-width steps), is further than the half-width from the path |
| `UNFINISHED` | the last sample is not inside the goal marker, or the furthest progress along the path is short of the goal |
| `INACCURATE` | mean deviation ÷ half-width above the difficulty's limit |
| `TOO_FEW_SAMPLES` / `TOO_MANY_SAMPLES` | malformed submission |

Timing is measured on the **server clock**, not taken from the client. The time allowed is a 10 s start
allowance plus the path's time limit plus 2 s of latency slack. A submission faster than the path's minimum
plausible trace time (length ÷ a maximum pointer speed) is rejected as `TOO_FAST`.

`assist` (an accessibility option, "Steady hand") widens the corridor and the time limit by 20 %. It changes
tolerance; it does not skip anything.

**Limit, stated honestly:** the path has to reach the client so it can be drawn, so a modified client could
automate the gesture. The server still guarantees everything else: the challenge belongs to the player, is
single-use and in time, the source is real and still present, and every learning gate applies.

## 5. Server lifecycle (`SpellTraceService`)

- One live challenge per player, held in `PlayerScopedState`, so logout clears it with no extra code. Death
  and dimension change also clear it, and the client screen is told the challenge was cancelled.
- **open**: re-run eligibility → check the retry cooldown → generate → store → send `SpellTraceOpenS2CPayload`.
  Opening a new challenge replaces an old one, and a late submission for the old one is answered as stale.
- **submit**: look up the session by player; the id must match; *remove the session before doing anything
  else* (single use — a replay or double-submit finds nothing). Then judge, then on a pass re-validate the
  source:
  - `Book`: the stack in that hand is still a `SpellSourceItem` with the same spell id;
  - `Lesson`: the node is still allocated.
  
  Then call `SpellLearningService.commit(player, spell, route)`, which re-runs every eligibility gate,
  writes the spell and its companions, applies the route's side effect (a book adds the dark-study stain; a
  lesson is recorded in the web ledger so a respec still takes it back), and syncs spells and stats. Only
  after a successful commit is a loose page spent (`shrink(1)`).
- **failure**: nothing is written or spent. The result payload names the reason and states that the source
  is kept. The retry delay is 0 for BASIC/INTERMEDIATE (re-reading the book is pacing enough), 5 s for
  ADVANCED and 10 s for DARK/MASTERY, shown in the refusal.
- **retry / lesson start**: `SpellTraceRequestC2SPayload(spellId)`. The server re-issues only if a valid source
  exists right now: a pending web lesson for that spell, or a matching source item in either hand. The client
  is never trusted to name a source.

### Edge cases

| Case | Outcome |
|---|---|
| already knows the spell | refused at open and at commit (eligibility) |
| spell disabled / unimplemented / removed by `/reload` | refused at open; at commit, `Spells.byId` → null → "no longer exists", source kept |
| loses eligibility mid-trace (heritage, profession, module, Spell Law) | commit re-runs the gates → refused with that reason, source kept |
| book moved, dropped or swapped mid-trace | source re-check fails → "the book is no longer in your hand", nothing taught |
| node refunded mid-trace | source re-check fails, nothing taught |
| disconnect / death / dimension change | session cleared; nothing taught or spent |
| two submits | the second finds no session → ignored as stale |
| server restart | sessions are transient by design; the source was never spent |
| another player | sessions are per-player and the source is in the player's own hand; there is no shared source today |

## 6. Networking (follows `network/spell` conventions, registered in `ModNetworkSpells`)

| Payload | Direction | Fields |
|---|---|---|
| `SpellTraceOpenS2CPayload` | S→C | id, spellId, difficulty, half-width, timeLimitTicks, startAllowanceTicks, spendsSource, points (u16 pairs) |
| `SpellTraceSubmitC2SPayload` | C→S | id, assist, samples (u16 pairs, ≤ 2 048) |
| `SpellTraceResultS2CPayload` | S→C | id, outcome, failIndex, accuracy, retryTicks, sourceKept |
| `SpellTraceRequestC2SPayload` | C→S | spellId (retry, or start a pending lesson) |

## 7. Client (`client/spell/trace/SpellTraceScreen`)

- A non-pause screen (lesson L814: a screen that round-trips packets must not pause) on the `NIGHT` surface
  of the UI kit, with a square canvas.
- The corridor is rasterised once per resize into row spans: a dark fill with a 2 px high-contrast border
  (parchment-gold; crimson for DARK). The start is a filled ring and the goal a ring with a cross-hair. A
  faint dotted centre-line runs up to the next 15 % of the path.
- **States:** Ready ("press on the start mark") → Tracing (progress band, time bar) → Judging → Passed or
  Failed. On a fail, the failing point is shown as an X with the reason underneath, plus **Try again** (sends
  a request) and **Close**.
- The same `TraceJudge` runs locally, so leaving the path is shown at once and the attempt is submitted
  immediately; the server's verdict is the one displayed.
- Header: spell name, difficulty chip, "Page will be used up on success" for loose sources. A legend explains
  the markers.
- Accessibility: a *Steady hand* toggle on the screen itself (assist; remembered for the session, so no new
  config key), reduced motion via the existing `reduceScreenEffects` setting (the start-mark pulse is the
  only animation and it switches off), and every state change gets a distinct colour plus a text label
  (never colour alone). There is no skip option.
- After a pass or Close, the previous screen is restored, e.g. the skill web.

## 8. Tests

- Unit (`src/test/.../spell/learning/tracing/`): generator determinism; validity for 10 000 seeds × 5
  difficulties; template variety; fallback validity; difficulty derivation table; judge — perfect trace
  passes, boundary cross, a cut corner between two in-corridor samples, start missed, unfinished, inaccurate,
  sample-count limits, assist widening; payload codec round-trips.
- Architecture: a source scan proving that no gameplay class outside the allowlist (`SpellLearningService`,
  admin, command, debug) calls `PlayerSpellData.learnSpell`.
- Game tests (`gametest/SpellTraceTests`): book kept on fail; page spent exactly once on pass; double submit;
  stale id; eligibility lost mid-trace; book removed mid-trace; node allocation → pending lesson → trace →
  ledger → respec revokes; Lumos teaches Nox; retry cooldown.
- Client: manual procedure in `documentation/ALPHA_SMOKE.md` (screen states, resize, reduced motion, assist).

## 9. Related fix shipped with this slice

`SpellLearningEligibility` compared a spell's `learning.masterySpellId` ("reparo") against hit counts stored
under the canonical id ("wizards_and_beasts:reparo"), so the mastery gate on Episkey and Levicorpus could never
open and their books were unreadable. The id is now resolved first, as `SpellRequirement` already does for
prerequisites (game test `spell_learning_mastery_gate_reads_canonical_hits`).

## 10. Known limits / follow-ups

- A modified client can automate the gesture (§4). Server guarantees are unaffected.
- **Decision taken, open for review:** skill-node lessons now run the gates on whether a spell may exist for
  the player at all (module, implemented, heritage, already known, Spell Law), which they skipped entirely
  before. They do *not* run the spell's own progression gates (prerequisite spell, required skill,
  profession, mastery): the allocated node stands in for those, because the web's layout is what the player
  paid points for and does not always match the authored prerequisite (`expelliarmus_unlock` is reachable
  without `stupefy_unlock`; Frigora's spell requires Lumos). Applying them would strand points.
- Consequence of the Spell Law gate: `quiet_casting` (which grants Imperio/Avada via `learn_spell` without
  `curse_control`, an audit finding) now owes a lesson that is refused until `curse_control` is held.
