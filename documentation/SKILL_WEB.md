# Skill Web — Design Sheet

**Written 2026-08-22, from the live datapack and source.** Counts are measured, not carried over
from a design draft. Re-derive rather than trust this file if the web has been re-authored since.

---

## 1. Shape

One graph per **audience**, not per tree. All six wizard trees are `Audience.WIZARD`, so a wizard
sees one connected web spanning them; `SkillNodeLoader` drops cross-**audience** edges only. This is
why five of the eight trees carry no `root` node and are still reachable: they hang off
`wizard_core` through legal cross-tree edges.

| Audience | Root | Nodes |
|---|---|---|
| WIZARD | `wizard_core` | 158 |
| GOBLIN | `goblin_appraisal` | 5 |
| HOUSE_ELF | `elf_silent_step` | 5 |

Server log on boot, which is the authority: `Loaded 168 skill nodes across 3 webs (0 dropped edges,
0 unreachable nodes)`.

Budget is `MAX_SKILL_POINTS = 60`, per audience via `SkillSystemAPI.pointCapFor` (all audiences share
60 today). Routing under that budget is the web's core decision — it is why fillers exist at all.

## 2. Earn loop

Three live sources, all server-side, all clamped at the cap by `PlayerSkillData.addSkillPoints`:

| Source | Award | Site |
|---|---|---|
| XP level gained | 1 per level | `SkillEvents.onLevelUp` |
| Heritage locked in | 3, once | `SkillEvents.onHeritageSelected` |
| Spell proficiency milestone | 1 at PROFICIENT, 2 at MASTERED | `SkillEvents.checkProficiencyMilestone` |

Earning stops cleanly at 60: `addSkillPoints` grants `min(amount, cap - totalPointsEarned)` and
no-ops at zero. The screen footer shows unspent / earned / cap / spent.

## 3. Per-tree identity

The **path** column is the cheapest total point spend from a blank sheet to owning that keystone —
Dijkstra over the live web, counting `wizard_core` and the keystone itself. Measured, not estimated.

Against the 60-point budget: one keystone costs 18–31, so **two are comfortable and three is the
ceiling** (the two cheapest, `transmuters_insight` + `beast_speaker`, share `wizard_core` and land
near 40, leaving little for depth). Four is not reachable. That trade is the game.

| Tree | Fantasy | Branches | Keystone | Path | Signature effect |
|---|---|---|---|---|---|
| **SPELL_MASTERY** | Casting excellence — the duellist's craft | Focus (cooldown), Force (damage), per-spell technique | `archmage_finality` | 26 | Teaches **Finite Incantatem**; −15% combat cooldown |
| **WANDLORE** | The wand answers you, and Apparition follows | Precision, Arcane reserve, Apparition forks | `wand_true_master` | 31 | Teaches **Arresto Momentum**; −15% utility cooldown |
| **MAGIZOOLOGY** | Beasts as companions, not loot | Hide (resistance), Keeper, Animagus study | `beast_speaker` | 23 | Teaches **Riddikulus**; +15% beast damage resistance |
| **HERBOLOGY** | Green hands, full stores | Green touch (harvest), Vitality | `verdant_heart` | 24 | Teaches **Episkey**; +20% harvest bonus chance |
| **ALCHEMY** | Efficiency and toughness from the cauldron | Efficiency, Iron skin | `transmuters_insight` | 18 | Teaches **Capacious Extremis**; −10% utility cooldown |
| **DARK_ARTS** | *Not expanded.* | — | none | Module ships disabled; no keystone authored — see §6 |
| **GOBLIN_CRAFT** | Appraisal, ledgers, steel | flat 5-node web | none | Audience-specific, unchanged |
| **ELF_BOND** | Silent service and its release | flat 5-node web | none | Audience-specific, unchanged |

Every keystone costs **6 points**, is `size: keystone` (renders at 26px with its own flare/core/ring
art), and sits one edge beyond its tree's outermost node so the branch reads as terminating in it.

**Every keystone teaches a spell**, because that is the most observable thing a node can do: you
gain something castable, not a number on a tooltip. All five teach an `IMPLEMENTED` spell — teaching
a `COMING_SOON` one would hand over something the cast gate refuses.

## 4. What each effect type actually does

Measured against the source, because the enum is larger than the set of types that reach a system.

| Type | Uses | Reaches |
|---|---|---|
| `passive_attribute` | 55 | `Skill.deriveNodeEffects` → `SkillAttributeApplicator`. Only `max_health`, `movement_speed`, `armor` are wired; a test pins that set |
| `category_cooldown_reduction` | 45 | `PlayerSkillBonusData` → cast `ModifierStack` |
| `category_damage_bonus` | 27 | same |
| `gameplay_bonus` | 20 | `SkillEffectCache` → `HerbologyAbilityHandler`, `MagizoologyAbilityHandler` |
| `unlock_ability` | 17 | `SkillNodeAbilityGrantSource` → `AbilityGrantService`. All 17 flags have readers |
| `learn_spell` | 18 | **Wired 2026-08-22.** `SkillSystemAPI.applyImmediateEffects` → `PlayerSpellData` |
| `spell_cooldown_reduction` | 11 | `SkillEffectCache` per-spell maps |
| `spell_damage_bonus` | 6 | same |
| `grant_ability` | 0 | Reaches `SkillNodeAbilityGrantSource`, but has **no tooltip line** — see §6 |
| `ability_refinement` | 0 | Reaches `AbilityModifiers`, same caveat |

## 5. Spell knowledge is pushed, not derived

Abilities are derived: `AbilityGrantService` recomputes from allocated nodes on every query, so a
refund revokes them for free. Spell knowledge cannot work that way — it lives in `PlayerSpellData`
as one flat set shared with the teacher, with no source column.

So `learn_spell` **pushes**, and `PlayerSkillData.webTaughtSpells` is the ledger that makes the push
reversible:

- On allocation, the spell is taught and recorded **only if the player did not already know it**.
  That is what stops a respec confiscating a lesson bought from a teacher for Knuts.
- `SkillSystemAPI.reconcileDerivedEffects` — called by every refund path and by the login migration —
  forgets any ledger entry no longer granted by an allocated node.
- Pre-existing saves load the ledger empty, so nothing is recorded and nothing is revoked. No
  migration, no existing player loses a spell.

## 6. Deliberate omissions

- **Dark Arts has no keystone.** `Module.DARK_ARTS` ships disabled, and the brief for this pass
  forbids expanding it into enabled gameplay. The three `*_unlock` nodes there (`avada_kedavra`,
  `crucio`, `imperio`) were also **left without `learn_spell`** — teaching Unforgivables from a
  skill node is a gameplay expansion, not a wiring fix.
- **`grant_ability` / `ability_refinement` stay flagged unimplemented** in
  `SkillEffectSummary.isImplemented`, even though both reach a system. That flag gates *"safe to
  ship in a datapack"*, and neither can describe itself in a tooltip yet, so a node using one would
  allocate with a blank effect list. No node uses either.
- **No new `GameplayStat` constants.** The brief allows expansion only where a reader lands with it;
  the content authored here needed none, and a dead enum value is exactly what the rule forbids.
- **`passive_attribute` monoculture is unresolved.** 55 of 199 effects are `max_health` (32),
  `armor` (20), `movement_speed` (3) — real, wired, and flavourless in a wizarding mod. Converting
  them needs stats that do not exist yet, which is the `GameplayStat` work above. Logged, not fixed.
