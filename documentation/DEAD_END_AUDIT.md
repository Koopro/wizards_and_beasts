# DEAD_END_AUDIT.md — Phase 0 (read-only)

Audit for the Dead-End Elimination brief. No code written. `HEAD = e9b8c4b6`,
`./gradlew build` = **green** (exit 0) before audit. Working tree carried two pre-existing
untracked docs (`WAND_REWIRE_AUDIT.md`, `WORKTREE_TRIAGE_AUDIT.md`), left untouched.

## VERDICT: STOP AND REPORT — three stop-triggers fired

| # | Stop-trigger | Fired? | Evidence |
|---|---|---|---|
| 1 | Wiring `*_unlock` nodes consumes > ⅓ of the 60 SP budget | **YES (hard)** | Reaching all 16 nodes = **75 SP** (> 20 = ⅓, and > 60 = whole cap) |
| 2 | Any dead flag is bucket (a) — feature exists, wiring broken | **YES** | `apparition_training`: Apparition fully built; consumer `canApparate` reads `isApparitionUnlocked`, never the flag |
| 3 | Key-format mismatch count > ~10 (systemic) | No | Exactly **1** mismatch (`capacious_extremis`) |
| 4 | `COMING_SOON` cannot be applied at node granularity | **YES** | `Skill` has no module field; `evaluateUnlock` gates only the whole `SKILL_TREES` module |
| — | Fix requires creating content / canon ruling | Partial | `capacious_extremis` `arcane_mastery` prerequisite (§2.2) is ambiguous → stop-and-report per §3.5 |

Phases 3 (unlock gating) and 4 (COMING_SOON) are **blocked** by triggers 1, 2, 4. Phase 2
(`capacious_extremis` prerequisite) is **partially blocked** (ambiguous resolution). Only Phase 1
(key-format normalization, 1 site) is clean — but §2 mandates STOP AND REPORT after Phase 0
regardless, so no code was written. Recommendations at the end.

---

## (1) Key-format sweep — **1 mismatch total**

Reader convention is **bare** (no namespace). Every Java skill reader keys on a bare node id:
`hasSkill(id)` → `getSkillLevel(id)` (`skill/data/PlayerSkillData.java:124-126`); `hasAbility(id)`
call sites all pass bare strings (`creature_knowledge`, `elf_apparition`, `free_elf`,
`goblin_*`, `green_thumb`, `natural_remedy`, `niffler_friend`). All 161 node ids and all node
`edges` are bare (verified: 0 namespaced ids, 0 namespaced edges).

| Reference | Literal | Format | Reader expects | Matches? |
|---|---|---|---|---|
| `spells/capacious_extremis.json:15` `requiredSkillId` | `wizards_and_beasts:arcane_mastery` | **namespaced** | bare (`hasSkill` → `getSkillLevel`) | **NO** |
| `masterySpellId` (`levicorpus.json:20`) | `wingardium_leviosa` | bare spell id | bare spell id | yes |
| `masterySpellId` (`episkey.json:25`) | `reparo` | bare spell id | bare spell id | yes |
| all node `edges` (161 nodes) | bare | bare | bare | yes |

**Mismatch count = 1.** This is the `capacious_extremis` bug and it is isolated, **not** systemic
(trigger 3 does not fire). Normalization direction, if applied: **data → readers** (make the key
bare), because the readers are the established convention across the whole codebase and are not
themselves wrong. But see (2)/§3.5 — the bare form still points at a nonexistent node.

Note: `SpellDefinition` accepts `requiredSkillId` as a free `Codec.STRING`
(`spell/def/SpellDefinition.java:203`) with no format validation — nothing normalizes or checks it,
which is why the namespaced typo failed silently and closed.

## (2) Dangling reference sweep — **1 dangling**

| Reference site | Target | Exists? |
|---|---|---|
| `capacious_extremis.json:15` `requiredSkillId` → `arcane_mastery` | skill node `arcane_mastery` | **ABSENT** |
| `masterySpellId` → `wingardium_leviosa`, `reparo` | spells | present |
| 161 node `edges` | nodes | all present (0 dangling) |
| vocation `tree` (`spell_mastery`/`dark_arts`/`herbology`/`magizoology`/`wandlore`) | `SkillTreeId` | present |
| vocation `grantedAbilities` (`duelist_spell_power`, …) | vocation ability ids (own system) | resolve within vocation system |
| `unlock_ability` flag strings (15) | boolean flags, not references | n/a |

`capacious_extremis` is therefore **two independent defects on one node**: (a) namespaced key that
cannot match, and (b) the target node does not exist even in bare form. Fixing (a) alone leaves it
still unlearnable via the skill path.

## (3) Dead-flag classification — one is bucket (a)

Buckets: **(a)** feature exists, a consumer exists, but it never reads the flag → feature-completion,
**out of scope** (stop-trigger 2). **(b)** nothing exists to unlock → `COMING_SOON` candidate.

| Flag | Feature exists? | Consumer of the flag? | Bucket | Evidence |
|---|---|---|---|---|
| `apparition_training` | **YES** — full Apparition system | none reads flag; `canApparate` gates on `isApparitionUnlocked` | **(a)** | `apparition/ApparitionServerLogic.java:46-51`; the only setter of `isApparitionUnlocked` is a **command** (`ApparitionCommands.java:117`) — no survival path. The node was evidently meant to be the wizard unlock and was never wired. |
| `wand_mastery` | **NO** — no wand-mastery gameplay anywhere | none | **(b)** | Zero refs outside the skill node + the unrelated profession id `ProfessionNode.java:23,26,33`. |
| `philosophers_stone` | **AMBIGUOUS** — `PhilosophersStoneItem` is fully built + functional (Elixir of Life, cooldown, destroy component) but has no acquisition (no recipe/loot) | none reads flag | **borderline** | `item/consumable/PhilosophersStoneItem.java` (working item, Module-gated, not flag-gated). The item exists, so "nothing exists to unlock" is not strictly true; but no consumer gates on the flag either. Not a clean (b). |

**`apparition_training` = bucket (a) fires stop-trigger 2.** Wiring it (node → `setApparitionUnlocked`)
is completing the Apparition feature's progression, not eliminating a dead end — explicitly out of
scope. `philosophers_stone` being borderline means `COMING_SOON`-gating it would hide a node that
points at a real (if unobtainable) item — also not a clean dead-end fix.

## (4) `*_unlock` node inventory — 16 nodes

None of these 16 is referenced by any spell's `requiredSkillId` today (verified across all spell
JSON + Java). Each only carries a cooldown/damage effect. `minSP` = cheapest SP to reach the node
from a tree root (Dijkstra over undirected adjacency, node weight = `pointCost`).

| Node | Tree | cost | minSP to reach | Target spell | Spell form |
|---|---|---|---|---|---|
| `lumos_unlock` | spell_mastery | 1 | 6 | `lumos` | JSON |
| `protego_unlock` | spell_mastery | 2 | 7 | `protego` | **JAVA** |
| `accio_unlock` | spell_mastery | 2 | 8 | `accio` | JSON |
| `nox_unlock` | spell_mastery | 1 | 8 | `nox` | JSON |
| `stupefy_unlock` | spell_mastery | 2 | 8 | `stupefy` | JSON |
| `crucio_unlock` | dark_arts | 4 | 11 | `crucio` | JSON |
| `expecto_patronum_unlock` | spell_mastery | 4 | 11 | `expecto_patronum` | **JAVA** |
| `expelliarmus_unlock` | spell_mastery | 2 | 11 | `expelliarmus` | JSON |
| `reparo_unlock` | spell_mastery | 2 | 11 | `reparo` | JSON |
| `incendio_unlock` | spell_mastery | 3 | 13 | `incendio` | JSON |
| `flipendo_unlock` | spell_mastery | 2 | 14 | `flipendo` | JSON |
| `imperio_unlock` | dark_arts | 4 | 17 | `imperio` | **JAVA** |
| `wingardium_unlock` | spell_mastery | 2 | 17 | `wingardium` → **`wingardium_leviosa`** | JSON (id ≠ node stem) |
| `avada_kedavra_unlock` | dark_arts | 6 | 19 | `avada_kedavra` | **JAVA** |
| `alohomora_unlock` | spell_mastery | 2 | 20 | `alohomora` | JSON |
| `bombarda_unlock` | spell_mastery | 3 | 20 | `bombarda` | JSON |

Two wiring complications beyond the SP budget:
- **4 target spells are bespoke Java** (`protego`, `expecto_patronum`, `imperio`, `avada_kedavra`).
  `Spell.getRequiredSkillId()` returns `null` and has no setter (`spell/core/Spell.java:271-276`).
  Gating these needs **Java changes**, not the pure-data wiring §3 assumes.
- **`wingardium_unlock`** would gate spell id `wingardium_leviosa`, not `wingardium` — the node stem
  does not equal the spell id, so a naive "strip `_unlock`" mapping is wrong for this node.

## (5) Spell-learning path trace

`tryLearnSpell` (`spell/learning/SpellLearningService.java:54-78`), in order:

1. `validateLearnAttempt` (`:58`) → `SpellLearningEligibility.evaluate` (`:92`) — gates in order
   (`spell/learning/SpellLearningEligibility.java`): unknown-spell (`:31`) → Obscurial ability
   (`:34`) → **DARK_ARTS module** (`:37`) → heritage-can-use (`:41`) → already-known (`:44`) →
   `spell.getRequirement().isMet` (`:47`) → **`requiredSkillId` via `hasSkill`** (`:51-57`) →
   `requiredProfessionId` (`:59-65`) → mastery tier (`:68-73`).
2. **Knuts payment** (`:63-72`): if `Config.spellTeacherRequirePayment && spellTeacherLearnCostKnuts
   > 0`, withdraw `spellTeacherLearnCostKnuts` from the vault; refund + fail if short.
3. `data.learnSpell` (`:74`) + sync.

**Insertion point named by §3 already exists.** The `requiredSkillId` eligibility gate is
`SpellLearningEligibility.java:51-57` and already runs before the Knuts charge (Knuts is in
`SpellLearningService`, downstream of `validateLearnAttempt`). So Phase 3 "add the eligibility gate
to `SpellLearningService`" is **redundant** — no new gate code is needed; wiring is (for JSON
spells) setting `requiredSkillId`, and (for the 4 Java spells) overriding `getRequiredSkillId()`.
Failure already returns a visible reason (`"Requires skill: " + requiredSkillId`,
`SpellLearningEligibility.java:55`) — a placeholder, adequate for §3.7.

## (6) SP-budget impact — decisive

- Cost to reach **all 16** `*_unlock` nodes (union of cheapest root→node paths, 45 distinct nodes) =
  **75 SP**.
- Cap = **60 SP** (`SkillSystemAPI.java:22`). One-third threshold = **20 SP**.
- **75 SP > 20 (3.75×), and 75 SP > 60 (the entire lifetime budget).**

A player literally cannot reach every gated spell within the cap, so gating all 16 forces spell
access to compete with every other skill purchase across the whole 60-point economy. That is a
progression rebalance, not a bug fix — **stop-trigger 1 fires decisively** (§6, §9: balance is never
in the implementation license).

## (7) Module-state / `COMING_SOON` granularity

- `ModuleState` = `disabled | enabled | preview | coming_soon` (`module/ModuleConfig.java:39`);
  `coming_soon` = module stays off, operators cannot switch it (`:40`).
- Applied at **module** granularity only: `SkillSystemAPI.evaluateUnlock` gates the whole
  `Module.SKILL_TREES` (`skill/SkillSystemAPI.java:109-110`).
- `Skill` nodes carry **no** module/availability field (`skill/Skill.java:71-89`).
  `ModuleContentIndex` maps only `EntityType`/`ItemLike` to modules via tags
  (`module/ModuleContentIndex.java:71-86`) — **skill nodes are not indexable by module**.

**`COMING_SOON` cannot be applied at node granularity** without new per-node availability infra
(a `Module`/availability field on `Skill` + a check in `evaluateUnlock`). That is new
infrastructure, not a data fix — **stop-trigger 4 fires**. Gating `wand_mastery` (bucket b) the way
§3.4 prescribes is not currently possible.

---

## Recommendation (for the human — no code written)

Given three fired stop-triggers plus one ambiguous canon-adjacent resolution, the brief as written
cannot proceed. Smallest safe subset, if separately greenlit:

- **Phase 1 (safe, 1 site):** normalize `capacious_extremis` `requiredSkillId` to bare **and** add a
  data-integrity regression test that every `requiredSkillId` resolves to an existing node. Value:
  makes the whole class fail loudly in future. Caveat: with the node absent, normalization alone
  does not make the spell learnable.
- **Phase 2 (`capacious_extremis` prerequisite): STOP per §3.5** — resolving the missing
  `arcane_mastery` is ambiguous between "drop the requirement (make it learnable)" and "repoint at an
  existing node," and both are design calls. Needs Christian.
- **Phase 3 (unlock gating): BLOCKED** by trigger 1 (75 > 60 SP) — this is a progression rebalance
  and belongs to Christian. Also note trigger it would need Java edits for 4 spells and a name-map
  fix for `wingardium_unlock`.
- **Phase 4 (COMING_SOON for bucket-b flags): BLOCKED** by trigger 4 (no node granularity).
  `apparition_training` is additionally bucket (a) → feature-completion, out of scope (trigger 2);
  `philosophers_stone` is borderline, not a clean (b).

Deferred findings are logged for `AUDIT_PUNCHLIST.md` should any phase later be greenlit.
