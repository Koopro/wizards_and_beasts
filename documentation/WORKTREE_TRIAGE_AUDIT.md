# WORKTREE_TRIAGE_AUDIT.md

**Phase 0 — read-only audit. No `git add`/`commit`/`checkout`/`stash` performed.**
Branch: `fix/chamber-of-secrets-module-gate` (61 commits ahead of `main`, 0 behind).
Date: 2026-08-01.

---

## ⛔ STOP-AND-REPORT — the task premise does not match the worktree

The agent prompt is predicated on two claims. Both are **false** against the actual repository state:

1. **Claim:** "The working tree contains a large body of uncommitted work" comprising four units —
   petrify/basilisk, Chamber of Secrets, ~24 wandmaking recipes, new beasts.
   **Reality:** the uncommitted tree is **10 files, all Patronus/Protego rendering work.**
   None of the four described units appear in `git status`. They are **already committed** on this
   branch (see §1, §2).

2. **Claim:** "`./gradlew build` currently fails via `AssetModelParityTest`; no change can be verified."
   **Reality:** `./gradlew build` = **BUILD SUCCESSFUL in 18s** (run 2026-08-01, exit 0).
   `AssetModelParityTest` **passes** (last recorded run 2026-07-31, `failures="0"`; re-confirmed by
   the green build). The uncommitted work does not touch anything that test inspects (§3).

Stop-triggers fired (§6 of the prompt):
- *"`AssetModelParityTest` failure is caused by content already on `main` rather than uncommitted work"* —
  stronger: **there is no failure at all.**
- *"Total uncommitted scope materially exceeds the four expected units"* — the inverse: the scope is
  **entirely different** from and **much smaller** than the four units. Either way it is not the
  described worktree.

There is **no build to restore** and **no petrify/basilisk/chamber/wandmaking/beast worktree to
triage.** Proceeding into Phase 1/2 as written would mean fabricating a commit sequence for work that
does not exist. **Halting for re-scope. Nothing was staged, committed, or modified.**

The 10 uncommitted files ARE a coherent, green-building body of work (Patronus 3-use + Protego
visual, matching the `project_patronus_three_uses` memory). A corrected commit plan for *that actual
work* is offered in §5 — but it is outside the four units this prompt scoped, so it awaits approval.

---

## 1. Full inventory

### `git status --porcelain=v1 -uall` (verbatim)

```
 M src/main/java/at/koopro/wizardsandbeasts/client/entity/ProtegoShieldRenderer.java
 M src/main/java/at/koopro/wizardsandbeasts/client/model/PatronusStagModel.java
 M src/main/java/at/koopro/wizardsandbeasts/client/spell/PatronusRenderer.java
 M src/main/java/at/koopro/wizardsandbeasts/entity/spell/PatronusEntity.java
 M src/main/java/at/koopro/wizardsandbeasts/spell/command/SpellCommands.java
 M src/main/java/at/koopro/wizardsandbeasts/spell/impl/ExpectoPatronum.java
 M src/main/resources/assets/wizards_and_beasts/lang/en_us.json
 M src/main/resources/assets/wizards_and_beasts/textures/entity/protego_shield.png
 M tasks/todo.md
?? tools/protego_shield_skin.py
```

### `git diff --numstat` (verbatim, +/-/file)

```
11    0    ProtegoShieldRenderer.java
39    27   PatronusStagModel.java
212   9    PatronusRenderer.java
42    3    PatronusEntity.java
54    0    SpellCommands.java
53    17   ExpectoPatronum.java
3     0    en_us.json
-     -    protego_shield.png   (binary, 68 -> 5642 bytes)
63    0    tasks/todo.md
```
(untracked: `tools/protego_shield_skin.py`)

### Grouped by logical unit

**Unit A — Patronus: three canon uses** (corporeal form render + non-corporeal mist + form-reveal)
- `entity/spell/PatronusEntity.java` — synched `DATA_CORPOREAL` + `DATA_FORM_ID`, `CORPOREAL_POWER=40`,
  `trySpawn(..., formId)`, mist vs corporeal lifespan.
- `client/model/PatronusStagModel.java` — reshaped stag (slim body, branched antlers, ears, snout).
- `client/spell/PatronusRenderer.java` — `PatronusRenderState(corporeal, formId)`; form-aware model
  selection (cat/wolf/rabbit/parrot tinted vs stag; non-corporeal additive wisp).
- `spell/impl/ExpectoPatronum.java` — resolve form up front, reject-before-cooldown, mist actionbar.
- `spell/command/SpellCommands.java` — `/wandb wand spell patronus form reveal|clear`.
- `assets/.../lang/en_us.json` — 3 new keys: `expecto_patronum.form_revealed`, `.form_cleared`, `.mist`.
- `tasks/todo.md` — "ACTIVE PLAN 2026-07-31 — Patronus: three canon uses" section (+ review).

**Unit B — Protego shield visual pass**
- `client/entity/ProtegoShieldRenderer.java` — override `getRenderType` →
  `RenderTypes.entityTranslucentEmissive(texture)`.
- `assets/.../textures/entity/protego_shield.png` — 1×1 stub → 64×64 hex/rune barrier texture.
- `tools/protego_shield_skin.py` — new deterministic generator for that texture (`Generator` marker).
- `tasks/todo.md` — "Protego visual pass (2026-07-31)" section (+ review).

**None of the prompt's four expected units are present in the uncommitted tree.**
Expected-unit reconciliation:
| Prompt "unit" | Uncommitted? | Actual location |
|---|---|---|
| Petrify / basilisk | **No** | committed: `spell/petrify/*`, `basilisk/*`, `effect/PetrificusTotalusEffect`, etc. |
| Chamber of Secrets | **No** | committed: `chamber/structure/ChamberOfSecretsStructure(s).java`, gate test |
| Wandmaking recipes (~24) | **No** | not in worktree at all; actual uncommitted recipe count = **0** |
| New beast definitions | **No** | not in worktree at all |

---

## 2. Cross-unit coupling map

| File | Belongs to | Depends on |
|---|---|---|
| `PatronusEntity.java` | A | self-contained (uses `ModEntities`, existing) |
| `PatronusStagModel.java` | A | client-only, used by `PatronusRenderer` |
| `PatronusRenderer.java` | A | `PatronusStagModel`, `PatronusRenderState` |
| `ExpectoPatronum.java` | A | `PatronusEntity.trySpawn`, `PatronusFormDeterminer` (existing) |
| `SpellCommands.java` | A | `PatronusFormDeterminer`, `ModAttachments.PATRONUS_FORM`, `PatronusFormSetS2CPayload` (all existing/committed) |
| `en_us.json` | A | additive keys only |
| `ProtegoShieldRenderer.java` | B | `ProtegoShieldEntity` (existing), `RenderTypes` |
| `protego_shield.png` | B | produced by `protego_shield_skin.py` |
| `protego_shield_skin.py` | B | none |
| **`tasks/todo.md`** | **A AND B** | ⚠️ **shared** — contains both the Patronus plan and the Protego pass sections |

**Coupling flag:** `tasks/todo.md` is the only file that belongs to two units. Its Patronus and Protego
sections are separate, non-overlapping hunks. Splitting it across two commits by **pathspec alone is
impossible** (pathspec stages whole files). Options for a clean 2-commit split: (a) accept `todo.md`
into one commit only (e.g. B) and note it; (b) patch-stage its hunks — but the prompt forbids
`git add -p`-style flows for this task. **Simplest clean path: A and B are so tightly a single work
session that committing them as ONE `feat` is defensible; the two-commit split is what forces the
`todo.md` coupling problem.** See §5.

No Java file belongs to two units. Units A and B are otherwise independent and each builds alone.

---

## 3. `AssetModelParityTest` root cause

**Test:** `src/test/java/at/koopro/wizardsandbeasts/resources/AssetModelParityTest.java`.

**Invariant asserted:** every `"model": "<id>"` reference inside each JSON under
`src/generated/resources/assets/wizards_and_beasts/items/` resolves to an existing model file at
`src/main/resources/.../models/<path>.json` **or** `src/generated/resources/.../models/<path>.json`.
Failing assertion (line 40–41):

```java
assertTrue(missing.isEmpty(),
        "Generated item definitions reference missing models:\n" + String.join("\n", missing));
```

**Current status: PASSING.** Evidence:
- Last recorded run `build/test-results/.../TEST-...AssetModelParityTest.xml` (2026-07-31T20:19:50Z):
  `tests="1" skipped="0" failures="0" errors="0"`.
- Full `./gradlew build` on 2026-08-01: **BUILD SUCCESSFUL**, `:test UP-TO-DATE`, `:check`, `:build` all green.

**Violating entries: none.** No registered entry currently violates the invariant.

**Which uncommitted unit introduced a violation: none.** The test reads only `generated/.../items/*.json`
against `models/*.json`. The uncommitted work touches **no** generated item definition and **no** model
JSON — it touches entity renderers, an entity model class (Java, not a resource model), a spell impl, a
command, `en_us.json`, and one entity texture PNG. It is structurally incapable of causing this test
to fail. **The prompt's stated blocker does not exist.**

---

## 4. Stack-rule violation sweep (uncommitted files)

| Rule | Result |
|---|---|
| `ResourceLocation` usage | **none** — all use `Identifier` |
| `Identifier.tryParse()` | **none** — `SpellCommands` uses `Identifier.fromNamespaceAndPath` path via existing APIs |
| UUID-keyed `AttributeModifier` | **none** — `PatronusEntity` uses `UUID ownerUuid` for **owner tracking** (caster identity), not attribute keying; allowed |
| `MinecraftForge.EVENT_BUS` | **none** |
| Missing JSpecify on public sigs | new **private** helpers (`revealPatronusForm`, `clearPatronusForm`, `formLabel`) need none; `getRenderType` is an `@Override` matching GeckoLib's signature; new public members on `PatronusEntity` (`isCorporeal`, `getFormId`, `trySpawn` overload) follow the file's existing (un-annotated) convention. No regression vs surrounding code. **No fix required for build; note only.** |
| Entity access in render path | `PatronusRenderer` reads entity state into `PatronusRenderState` in `addRenderData` (build phase), not at render time — consistent with the GeckoLib 5.4.5 `GeoRenderState` pattern. **Clean.** |

No stack-rule violations that affect the build. Confirmed by green `./gradlew build`.

---

## 5. Proposed commit sequence (for the ACTUAL worktree — awaiting approval, out of prompt scope)

> ⚠️ This work is **not** any of the four units this prompt scoped. Per the Upgrade License, this is a
> scope observation, not a silent expansion — presenting for a decision, committing nothing.

The tree already builds green **with all 10 files present**, so every candidate SHA below builds.

**Option 1 — two commits (preferred if `todo.md` split is acceptable):**

1. `feat(spell): render the Patronus by form, and add mist + reveal`
   Pathspec: `PatronusEntity.java PatronusStagModel.java PatronusRenderer.java ExpectoPatronum.java SpellCommands.java en_us.json`
   Builds: pre-existing `PatronusFormDeterminer` / `PATRONUS_FORM` attachment / `PatronusFormSetS2CPayload` are already committed; nothing here depends on Unit B. ✔ green.

2. `feat(client): give the Protego shield a glowing barrier skin`
   Pathspec: `ProtegoShieldRenderer.java textures/entity/protego_shield.png tools/protego_shield_skin.py tasks/todo.md`
   Builds: renderer override + texture are independent of Unit A. ✔ green.
   (`tasks/todo.md` carried whole into commit 2; its Patronus section rides along — the coupling cost.)

**Option 2 — one commit (avoids the `todo.md` coupling entirely):**

1. `feat(spell): Patronus form rendering + mist, and a Protego barrier skin`
   Pathspec: all 10 files.
   One work session, one green tree. Only downside: two features in one commit (violates the prompt's
   "one commit per logical unit" — but that rule was written for the *four-unit* worktree that isn't here).

**Recommendation:** Option 1. The `todo.md` coupling is cosmetic (a docs file), and two `feat` commits
match the two distinct features. Neither option involves any parity fix, any test change, any deletion
of registered content, or any canon-value edit.

---

## Canon / scope safety check (all clear)

- No creature classification, `mmRating`, `canonTier`, wandwood, or spell-name change.
- 3 new `lang` keys are **feature-feedback UI strings** the work itself introduces (not edits to
  existing lore values) — additive, not a canon-value modification.
- No module registration added or modified. No mixin added or modified.
- No wandmaking recipe touched (count of recipe changes = 0).

---

## Verification log

| Check | Result |
|---|---|
| `git status --porcelain=v1 -uall` | 10 entries (9 tracked mods + 1 untracked), all Patronus/Protego |
| `git rev-list --left-right --count main...HEAD` | `0  61` (branch 61 ahead, 0 behind) |
| petrify/basilisk/chamber in `src/main/java` | present & committed (37 files match) — not in worktree |
| `AssetModelParityTest` last run | PASS (`failures=0`, 2026-07-31) |
| `./gradlew build` (2026-08-01) | **BUILD SUCCESSFUL in 18s**, exit 0 |
| stack-rule sweep (uncommitted) | clean |

**Phase 0 complete. Halting before Phase 1 per stop-triggers.**

---

## Phase 2 — sequenced commits (executed 2026-08-01, Option 1 approved)

No Phase 1 fixes were needed: the tree already built green, so `MIGRATION_DELTAS.md` is empty (no
behavioural deltas introduced). Repo hook `.git/hooks/commit-msg` forbids a Claude co-author trailer;
none was added.

| # | SHA | Message | Pathspec (explicit) | Build @ SHA |
|---|---|---|---|---|
| 1 | `7d670ec9` | `feat(spell): render the Patronus by form, and add mist + reveal` | `PatronusEntity.java`, `PatronusStagModel.java`, `PatronusRenderer.java`, `ExpectoPatronum.java`, `SpellCommands.java`, `en_us.json` | ✅ BUILD SUCCESSFUL |
| 2 | `29d84d0a` | `feat(client): give the Protego shield a glowing barrier skin` | `ProtegoShieldRenderer.java`, `protego_shield.png`, `protego_shield_skin.py`, `tasks/todo.md` | ✅ BUILD SUCCESSFUL |

Pre/post-flight diffs matched intent exactly at each step (only the intended files left the
uncommitted set; nothing changed state unexpectedly). `tasks/todo.md` was carried whole into commit 2
as flagged (its Patronus section rode along — the accepted `todo.md` coupling cost of Option 1).

**Left intentionally uncommitted:** `WORKTREE_TRIAGE_AUDIT.md` (this report — a deliverable, not
feature work; commit if desired).

**Not done (needs a live client, per prompt §7):** in-game visual pass — Patronus form/mist render,
Protego disc/dome glow + shatter. Compile + full test suite verified only.

