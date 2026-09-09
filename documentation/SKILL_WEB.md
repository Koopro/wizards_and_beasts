# Skill Web — Design Sheet

**Written 2026-08-22, from the live datapack and source. Re-measured 2026-09-09 after the filler
purge.** Counts are measured, not carried over from a design draft. Re-derive rather than trust this
file if the web has been re-authored since.

---

## 1. Shape

One graph per **audience**, not per tree. All six wizard trees are `Audience.WIZARD`, so a wizard
sees one connected web spanning them; `SkillNodeLoader` drops cross-**audience** edges only. This is
why five of the eight trees carry no `root` node and are still reachable: they hang off
`wizard_core` through legal cross-tree edges.

| Audience | Root | Nodes |
|---|---|---|
| WIZARD | `wizard_core` | 97 |
| GOBLIN | `goblin_appraisal` | 5 |
| HOUSE_ELF | `elf_silent_step` | 5 |

Server log on boot, which is the authority: `Loaded 107 skill nodes across 3 webs (0 dropped edges,
0 unreachable nodes)`.

Budget is `MAX_SKILL_POINTS = 60`, per audience via `SkillSystemAPI.pointCapFor` (all audiences share
60 today). Buying the whole wizard web costs 272, so a run spends about a fifth of it. Routing under
that budget is the web's core decision — it is why the pathway nodes exist at all.

### The filler purge (2026-09-09)

The web shipped 168 nodes of which 100 were named `filler.minor_*` in the data itself, and 41 of
those paid a raw attribute: fourteen separate nodes each granting +0.5 max health. The cheapest use
of a point was the fourteenth half-heart, which is the "huge tree where every node is +5%"
anti-pattern in its purest form.

What the fillers were structurally, though, was the web's connective tissue — 57 all-small chain
components sitting between notables. Deleting them wholesale would have disconnected the graph, so
the purge went chain by chain:

| Chain shape | Count | Became |
|---|---|---|
| dead-end spur off the hub (`polaris_minor_vitality_*`) | 4 | deleted; they led nowhere |
| single filler between two notables | 23 | 15 direct notable↔notable edges, 8 real spell nodes |
| two, three or four fillers in a row | 30 | **one pathway node** each, at the chain's centroid |

Net: **168 → 107 nodes, 100 small → 30**, and the travel tax across a two-filler link halved.
Geometry is preserved — every surviving node kept or averaged its coordinates, so the constellation
still reads as the same six spokes around Polaris.

A **pathway** is what a filler should have been: one point, one effect, and that effect is the
region's own currency rather than a generic stat. Herbology pathways pay harvest luck, Magizoology
pays beast resistance, Wandlore pays fewer misfires. `SkillNodeJsonTest.noSmallNodeGrantsARawAttribute`
is the rule that keeps this from being undone one node at a time: an attribute is the only effect
with no theme attached, and therefore the only one that can be pasted onto a connector without
anybody deciding what that connector is for.

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

Against the 60-point budget: one keystone costs 12–26. Counting the overlap between routes, the
cheapest **two** land at 29, **three** at 47 — leaving 13 points of depth — and **four at 65, which
does not fit**. So three keystones is a real all-in choice with nothing left over, two is the
comfortable shape, and four is off the table. That trade is the game. (Before the purge the same
three cost 40+ with almost nothing left, and the difference is entirely the halved travel tax.)

| Tree | Fantasy | Branches | Keystone | Path | Signature effect |
|---|---|---|---|---|---|
| **SPELL_MASTERY** | Casting excellence — the duellist's craft | Focus (cooldown), Force (damage), per-spell technique | `archmage_finality` | 19 | Teaches **Finite Incantatem**; −15% combat cooldown |
| **WANDLORE** | The wand answers you, and Apparition follows | Wandwork (misfires), Arcane reserve, Apparition forks | `wand_true_master` | 26 | Teaches **Arresto Momentum**; −15% utility cooldown |
| **MAGIZOOLOGY** | Beasts as companions, not loot | Field handling (resistance), Keeper, Animagus study | `beast_speaker` | 18 | Teaches **Riddikulus**; +15% beast damage resistance |
| **HERBOLOGY** | Green hands, full stores | Greenhouse rotation (harvest), Vitality, Aguamenti | `verdant_heart` | 19 | Teaches **Episkey**; +20% harvest bonus chance |
| **ALCHEMY** | Efficiency and toughness from the cauldron | Bench discipline, Iron skin, Frigora | `transmuters_insight` | 12 | Teaches **Capacious Extremis**; −10% utility cooldown |
| **DARK_ARTS** | Curses, and the mind on both sides of them | Dark practice, Occlumency, the three Unforgivables | none | Module ships disabled; no keystone authored — see §6 |
| **GOBLIN_CRAFT** | Appraisal, ledgers, steel | flat 5-node web | none | Audience-specific, unchanged |
| **ELF_BOND** | Silent service and its release | flat 5-node web | none | Audience-specific, unchanged |

Every keystone costs **6 points**, is `size: keystone` (renders at 26px with its own flare/core/ring
art), and sits one edge beyond its tree's outermost node so the branch reads as terminating in it.

**Every keystone teaches a spell**, because that is the most observable thing a node can do: you
gain something castable, not a number on a tooltip. All five teach an `IMPLEMENTED` spell — teaching
a `COMING_SOON` one would hand over something the cast gate refuses.

## 4. What each effect type actually does

Measured against the source, because the enum is larger than the set of types that reach a system.

| Type | Uses | Was | Reaches |
|---|---|---|---|
| `learn_spell` | 31 | 18 | `SkillSystemAPI.applyImmediateEffects` → `PlayerSpellData`, ledgered so a refund takes it back |
| `gameplay_bonus` | 25 | 20 | `SkillEffectCache` → `HerbologyAbilityHandler`, `MagizoologyAbilityHandler`, the cast `ModifierStack`, `LegilimencyServerLogic` |
| `category_cooldown_reduction` | 24 | 45 | `PlayerSkillBonusData` → cast `ModifierStack` |
| `unlock_ability` | 17 | 17 | `SkillNodeAbilityGrantSource` → `AbilityGrantService`. All 17 flags have readers |
| `spell_cooldown_reduction` | 16 | 11 | `SkillEffectCache` per-spell maps |
| `category_damage_bonus` | 14 | 27 | `PlayerSkillBonusData` → cast `ModifierStack` |
| `passive_attribute` | 14 | 55 | `Skill.deriveNodeEffects` → `SkillAttributeApplicator`. Only `max_health`, `movement_speed`, `armor` are wired; a test pins that set |
| `spell_damage_bonus` | 10 | 6 | `SkillEffectCache` per-spell maps |
| `grant_ability` | 0 | 0 | `SkillNodeAbilityGrantSource`, same list as `unlock_ability`. Shippable since 2026-09-09 — it has a tooltip line now |
| `ability_refinement` | 0 | 0 | `AbilityModifiers` aggregates it and no ability reads the result — see §6 |

The shape of that table is the whole point of the purge. `learn_spell` went from the fifth-commonest
effect to the commonest, and `passive_attribute` from the commonest to seventh, because the thing a
node most often does is now *hand you a spell* rather than move a number you cannot see.

`GameplayStat` gained two members, each with a reader landing beside it:

| Stat | Read at | What it is |
|---|---|---|
| `SPELL_MISFIRE_REDUCTION` | `SkillSystemAPI.applySkillModifiers` → `ModifierStack.addMisfireChance` | Wandlore's currency. Added negatively rather than set, because misfire accumulates from the wand's fizzle, allegiance and PRECISION; the reader clamps to `[0, 1]` so a large bonus floors at zero instead of becoming a hit bonus |
| `OCCLUMENCY_SHIELD` | `LegilimencyServerLogic` | Added to the target's *trained* Occlumency before the Willpower scalar. Added, not multiplied — multiplying would leave an untrained wizard who bought the node at exactly zero |

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

- **Dark Arts still has no keystone.** `Module.DARK_ARTS` ships disabled, and a keystone is a
  branch's payoff — authoring one for a region nobody can reach is content for its own sake.
  *(Reversed 2026-09-09: the three `*_unlock` nodes there do now carry `learn_spell`. The earlier
  ruling called teaching Unforgivables a gameplay expansion; the counter-argument that won is that a
  node called "Killing Curse Mastery" which grants an 8% cooldown shave is a lie told to the player
  at a cost of six points, and the honest fixes were "teach it" or "rename it". Teaching is safe
  because knowing a spell and being allowed to cast it are separate data: `AvadaKedavra`'s
  requirement still demands PROFICIENT on both Imperio and Crucio, and the module gate still holds
  the whole region shut.* `SkillNodeJsonTest.everyUnlockNodeTeachesItsSpell` now enforces the rule
  for every `<spell>_unlock` node in the web.)
- **`ability_refinement` stays flagged unimplemented** in `SkillEffectSummary.isImplemented`.
  `AbilityModifiers` aggregates it correctly and *no ability implementation reads the result*, so a
  node declaring one would cost points and change nothing. The flag gates *"safe to ship in a
  datapack"*, not *"the mechanism exists"*. Inventing a consumer — mapping `RANGE` onto Apparition's
  anchor capacity, say — to close the checkbox would be the same mistake as mapping wand
  `spell_modifiers` onto `SpellCategory`, and is refused for the same reason.
  *(`grant_ability` left this list on 2026-09-09: it reached `SkillNodeAbilityGrantSource` all along
  and was blocked only by having no tooltip line. It has one now — the same sentence
  `unlock_ability` prints, because it is the same benefit — so a node may ship it.)*
- **`passive_attribute` monoculture is resolved, not by conversion but by deletion.** It was 55 of
  199 effects; it is now 14 of 151, and every one sits on a notable where toughness *is* the fantasy
  (`herbal_vitality`, `goblin_steelheart`, `keeper_vigor`). The 41 that went were all half-hearts and
  half-points-of-armour on connectors.
