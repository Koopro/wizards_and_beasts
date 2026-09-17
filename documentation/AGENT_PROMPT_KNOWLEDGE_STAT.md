# AGENT PROMPT — PlayerStat.KNOWLEDGE after Spellteacher removal

**Status:** ✅ **Implemented 2026-09-10**, as Phase 4 of `AGENT_PROMPT_SPELL_LEARNING.md`. Re-verified
against this brief 2026-09-11; one stale claim and two tooltip strings fixed on the second pass.
**Depends on:** payment path removed — it was, outright, not disabled.
**Path:** `documentation/AGENT_PROMPT_KNOWLEDGE_STAT.md`

---

## Problem

`KNOWLEDGE` existed mainly to discount `spellTeacherLearnCostKnuts` via `StatEffects.tuitionCost`.
With the vendor gone the stat lost its only gameplay consequence — it would have been derived, synced
and displayed, with nothing in the mod reading it, which is exactly the state it was in *before* the
tuition discount was invented for it.

---

## 1. Inventory

### Every read of `tuitionCost` / `tuitionMultiplier` — now zero

| Was | Now |
|---|---|
| `SpellLearningService.tuitionFor` (quote + charge) | deleted with the vendor |
| `StatReadout.effectValue` — KNOWLEDGE row | reads `StatEffects.studyRate` |
| `StatReadout.effectTone` — KNOWLEDGE row | reads `StatEffects.studyRate`, and moved out of the "lower is better" sign-flip group |
| `StatEffectsTest.tuitionMultiplierSpansItsDeclaredRange`, `.tuitionCostRoundsUpAndNeverReachesFree` | replaced by `studyRateSpansItsDeclaredRange`, `studyRateNeverPenalisesAnUnreadWizard` |
| `StatReadoutTest.everyEffectValueIsDerivedFromStatEffects`, `.theEndpointsReadTheWayTheDesignPromises` | assert `studyRate`; the endpoint reads `+50.0%` where it read `-40.0%` |

`grep -rn "tuition"` over `src/` now returns two hits, both prose: the word "intuition" in
`StatTraining`, and one deliberate historical note in `StatEffectsTest` explaining why the floor is
1.0.

### Every read of `PlayerStat.KNOWLEDGE`, by kind

**Derivation — unchanged, per the constraint.** `KnowledgeFormula.compute(spells, bestiary, nodes,
books)` with weights 2 / 1 / 3 / 2, clamped 0–100. `PlayerStatsAPI.computeKnowledge` gathers the four
counts. Server-authoritative and recomputed on demand — it is never stored, so no migration was
possible or needed.

**Sync fan-out — unchanged.** Five call sites push a stats sync because they moved one of the four
inputs: `BestiaryDataHelper` (×2), `LoreTomeItem`, `SkillSystemAPI` (×2), and
`SpellLearningService.tryLearnSpell`. `PlayerStatsSyncPayload` carries the derived snapshot;
`PlayerStatsData.knowledge()` is what the client reads.

**Write guards — unchanged.** `PlayerStatsAPI.setStat` refuses derived stats; `StatsDevKit` skips
KNOWLEDGE; `StatsFeatureDebug` deliberately shows the stored snapshot *and* the live computation so a
drift between them is visible.

**Effect — the one that changed.** Exactly one read, in
`SpellProficiencyTracker.studyRate(ServerPlayer)`.

**Display.** `StatReadout` (label, value, tone, tooltip) and `OwlFeatureDebug`'s row.

---

## 2. Proposal — one primary role: **study rate**

`StatEffects.studyRate(knowledge)` → **1.00 at 0, 1.50 at 100**, multiplying the base proficiency
increment in `SpellProficiencyTracker.recordSuccessfulHit`.

### Why this over the alternatives the brief named

**Source-learn reliability** ("higher chance to learn from a source on first read") was the closer
rival and is wrong for two reasons. It makes a *found* book sometimes worthless, which punishes
exploration with RNG on the one item the new learning loop is built around — and the loop is barely a
week old. And it collides with `SpellLearningEligibility`, which is deliberately a set of hard yes/no
gates with a stated reason for every refusal; a probabilistic failure would be the one refusal the
player cannot be told the cause of.

**Another damage number** was excluded by the brief and would have been wrong anyway: POWER already
owns damage, and a second multiplier on the same channel is a knob, not a role.

**Reduced fizzle** is PRECISION's job. Two stats pulling one lever means neither has an identity.

Study rate is the only candidate that describes what being well-read actually *does* — a wizard who
has read the books, walked the bestiary and worked the skill web recognises what they are doing wrong
sooner, so each landed cast teaches them more — and it lands on proficiency, which was already the
mastery curve. No new system: the hook is one multiplication inside a method that already existed.

### Three constraints, each with a test

- **Floor is 1.0, never below.** A discount's natural shape is "everyone pays full price, the
  well-read pay less". Ported carelessly onto a rate that becomes "everyone learns slowly, the
  well-read learn normally" — a penalty on a stat nobody can train directly, applied to every player
  who has not ground out the other four systems it derives from. Pinned by
  `studyRateNeverPenalisesAnUnreadWizard`.
- **`PLAYER_STATS` off returns 1.0**, not a computed value from an unread stat. A module being
  disabled must never make the game harder than a module enabled at zero — the same rule
  `StatResistModifiers` exists to enforce for WILLPOWER.
- **It multiplies an increment that already tapers** above 0.8 proficiency, so it shortens the grind
  without flattening the curve.

+50% at 100 is deliberately modest. KNOWLEDGE is a reward for work already done elsewhere, and must
not become the reason anyone farms one of its four inputs.

---

## 3. Implementation

| File | Change |
|---|---|
| `StatEffects` | `TUITION_AT_ZERO/MAX` → `STUDY_RATE_AT_ZERO/MAX` (1.00 / 1.50); `tuitionMultiplier` + `tuitionCost` → `studyRate`; class docs rewritten to say why the floor is 1.0 |
| `SpellProficiencyTracker` | `baseIncrement = 0.002f * studyRate(player)`; private `studyRate(ServerPlayer)` gates on `Module.PLAYER_STATS` |
| `StatReadout` | KNOWLEDGE row reads `studyRate`; **moved out of the "lower is better" group** in `effectTone` — it is a gain now, not a saving, so flipping its sign would have coloured a maxed stat red |
| `StatCastModifiers` | its javadoc claimed KNOWLEDGE was "purely informational" — true when written, false now. Corrected, with a note that the stat is deliberately *outside* the cast pipeline because it governs what a cast teaches, not what it does |
| lang | `stat.effect.knowledge`: "Tuition Cost" → **"Study Rate"**. `stat.desc.knowledge` and `stat.tooltip.knowledge_sources` rewritten — both still described sitting lessons and paying for them |

The tooltip needed no structural change: `StatReadout.tooltip` already renders
`effectLabel` / `effectValue` / `effectTone` for every stat, and the derived-stat branch already
appends the "not trained directly" note and the sources line.

---

## 4. Tests

| Test | Asserts |
|---|---|
| `StatEffectsTest.studyRateSpansItsDeclaredRange` | 1.00 / 1.25 / 1.50 at 0 / 50 / 100, and monotonic across all 101 values |
| `StatEffectsTest.studyRateNeverPenalisesAnUnreadWizard` | ≥ 1.0 at every value |
| `StatReadoutTest.everyEffectValueIsDerivedFromStatEffects` | the sheet's number is `studyRate`, not a second formula |
| `StatReadoutTest.theEndpointsReadTheWayTheDesignPromises` | `0.0%` at 0, `+50.0%` at 100 |
| `StatReadoutTest.aStatSittingOnNeutralNeverReadsAsMinusZero` | KNOWLEDGE 0 is `0.0%`, not `-0.0%` |
| `StatReadoutTest` tone cases | GRAY at 0, **GREEN** at 100 — this is the assertion that would have caught the missed sign flip |
| `KnowledgeFormulaTest` | derivation and the derived/untrainable flags, unchanged and still green |

`./gradlew test` green; `./gradlew runGameTestServer` 26/26.

---

## 5. Delta recorded

`MIGRATION_DELTAS.md` → *"Spellteacher vendor removed"* → **"KNOWLEDGE re-homed: tuition discount →
study rate"**, carrying the three constraints and the `StatReadout` sign-flip note. Also in
`KNOWN_ISSUES` §4.5a and the `CHANGELOG` entry for 2026-09-10.

---

## 6. Constraints held

- **No new systems.** The hook is one multiplication inside `SpellProficiencyTracker`, which already
  owned the whole proficiency curve. No new class, no new packet, no new attachment field.
- **Derivation untouched.** `KnowledgeFormula` and its four inputs are exactly as they were; only the
  *effect* changed, which is what the brief asked for.

---

## 7. Still open

KNOWLEDGE now has one consequence, which is one more than it had before the tuition discount and the
same count as after it. If a second is ever wanted, the honest place is **not** a bigger multiplier
here — it is somewhere KNOWLEDGE means "recognises things", such as what a Bestiary entry reveals at a
glance, or whether a brew's ingredients are legible before you commit them.
