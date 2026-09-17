# AGENT PROMPT — Spell Learning Loop (Replace Spellteacher Vendor)

**Status:** ✅ **Implemented 2026-09-10.** Phases 0–5 complete. Supersedes any "spell teacher NPC sells unlocks" design.
**Path:** `documentation/AGENT_PROMPT_SPELL_LEARNING.md` (never root or `docs/` — gitignored).
**Authority:** `src/main/java` + `src/main/resources` + `src/generated/resources` win over this doc when they disagree.

---

## 0. Hard stack rules (non-negotiable)

- Minecraft **1.21.11**, NeoForge **21.11.x**, Java **21**, official Mojang mappings, GeckoLib **5.4.5**.
- `Identifier` everywhere — never `ResourceLocation`. `Identifier.fromNamespaceAndPath()` only.
- `Identifier`-keyed `AttributeModifier` — never UUID-keyed.
- `NeoForge.EVENT_BUS` only. `DeferredRegister` for all registration.
- `AttachmentType` + `Codec` for persistent state.
- `CustomPacketPayload` + `StreamCodec` + `RegisterPayloadHandlersEvent` for networking.
- JSpecify nullability on every public signature.
- **GeckoLib render safety:** no entity access at render time.
- **Mixin discipline:** prefer NeoForge event; if mixin required, stop and report with justification.
- **Repo wins.** Match existing patterns in `spell/learning/`, `spell/proficiency/`, `skill/`, `stats/`.

*Held. No mixin was needed; no new packet was needed either — the learning path is entirely
server-side plus one already-existing sync.*

---

## 1. Problem statement

`SpellLearningService.tryLearnSpell` was a paid unlock gate and `buildOffers` was a vendor catalogue.
Generic RPG trainer behaviour, undercutting skill-web keystones, proficiency, the existing gates, and
the lore.

---

## 2. Target design — **Primary (Book + Practice) shipped**

1. **Discovery** — a spell is unknown until the player obtains a *spell source*: a written Standard
   Book of Spells, found in world loot or scribed by another player.
2. **Theoretical knowledge** — reading the source calls into the learning service and teaches the
   spell. (See the Phase 0 decision below on why this is one step, not two.)
3. **Practical mastery** — proficiency and mastery tiers are untouched and remain the depth. Skill
   nodes still grant spells.

The **secondary** (named mentors) was not built; nothing in the mod currently has an NPC that would
carry it. The scribing path at the Study Lectern fills the same "somebody taught me" niche with a
player on the other end, and is strictly better for a server.

**Out of scope and confirmed absent:** paying Knuts to learn; any persistent screen listing eligible
spells for sale.

---

## 3. Existing code — final disposition

| Piece | Location | Outcome |
|---|---|---|
| Eligibility | `spell/learning/SpellLearningEligibility` | **Kept, byte-for-byte.** All gates stay. |
| Learn + sync | `SpellLearningService.tryLearnSpell` / `data.learnSpell` + `SpellDataSyncS2CPayload` | **Kept.** Knuts path deleted. |
| Tuition / Knowledge | `tuitionFor`, `StatEffects.tuitionCost`/`tuitionMultiplier`, `Config.spellTeacher*` | **Deleted.** KNOWLEDGE re-homed onto study rate — Phase 4. |
| Offers list | `buildOffers`, `SpellOffer` | **Deleted.** |
| Skill `learn_spell` | `SkillEffect.LearnSpell`, `SkillSystemAPI.teachSpell` | **Kept, untouched.** |
| Proficiency | `spell/proficiency/` | **Kept and now KNOWLEDGE's only consumer.** |
| Mastery tiers | `masterySpellId` + tier | **Kept.** |
| Teacher package | `spell/teacher/` + client screens + both payloads | **Deleted**, except the block — moved to `spell/study/SpellScriptoriumBlock` and re-purposed. |
| Config flags | `spellTeacherRequirePayment`, `spellTeacherLearnCostKnuts` | **Removed.** Delta recorded in `MIGRATION_DELTAS.md`. |

---

## 4. Phase plan — all complete

### ✅ Phase 0 — Inventory & decision lock

Call sites found and dealt with: `SpellTeacherScreen`, `ClientSpellTeacherState`,
`SpellClientPayloadHandlers.handleSpellTeacherOpen`, `ClientScreenHooks.openSpellTeacherScreen`,
`SpellTeacherOpenS2CPayload`, `SpellTeacherLearnC2SPayload`, `ModNetworkTeacher` (+ its line in
`ModNetwork`), `WizardsConfigScreen`'s two category entries, `StatEffects`/`StatReadout`'s KNOWLEDGE
rows, `SpellLearningServiceTest`, `StatEffectsTest`, `StatReadoutTest`, `PlacementFacingTests`, and
the block's registry/lang/advancement/recipe/loot-table/bench-enhancer files.

**Decision: knowledge stays binary.** Two-stage (*known* → *trained*) is the obvious shape for a book
fantasy and was rejected. The mod already has the second axis — proficiency, with mastery tiers on
top — so a third state would duplicate it, and it would cost a new `PlayerSpellData` field, a
`CURRENT_VERSION` bump, a sync-format change and a new failure mode in every gate that asks
`knowsSpell`. Reading teaches; casting masters.

**Decision: the block survives.** Deleting a registered block turns every placed copy into air and
orphans a sprite, a recipe, an advancement, a loot table and a bench-enhancer entry. It keeps the id
`spell_teacher` — the class name `SpellScriptoriumBlock` deliberately disagrees with it, the same way
`wand_wood_legacy` disagrees with `WandWood`.

### ✅ Phase 1 — Decouple payment

The charge path is deleted outright rather than defaulted off: a config flag that nobody should turn
on is a config flag that has to be explained forever. `tryLearnSpell` still serves the source item
and the skill effect. `/wandb spell learn` deliberately still writes `PlayerSpellData` directly and
bypasses the gates — that is what an operator override is for.

### ✅ Phase 2 — Spell source items (data-driven)

`ModDataComponents.SPELL_SOURCE` (`spell_source`, a `String` spell id; absent = blank). `SpellSource`
owns read/write and the registry-free name lookup. `SpellSourceItem` owns the 60-tick read channel.
`CanonItemRegistry.STANDARD_BOOK_OF_SPELLS` was promoted from `Item::new` — the first stub to take
behaviour, exactly as that registry's own note predicted — and stays in `CanonItemRegistry.ALL` so its
sprite, lang keys and catalogue test are unchanged.

A second source item, `torn_spell_page` (`MiscItemRegistry`), is the mid-game form: same component,
same read, but **spent** on a successful read. See `AGENT_PROMPT_SPELL_SOURCES.md` for why the
bound/loose distinction lives on the item rather than in the component.

Starter path: `SpellbookLootModifier` (`wizards_and_beasts:spellbook`) rolls one spell from a
per-modifier pool into village houses (0.35), stronghold libraries and bastion treasure (0.7),
woodland mansions and pillager outposts (0.6), and — as pages — ruins and mineshafts (0.4).
`hidden_wizarding_cache` carries a guaranteed written book — Lumos weighted over Stupefy — plus
blanks and ink bottles.

### ✅ Phase 3 — Remove vendor UI

Screen, client state, both payloads and the network registration are deleted. Eligibility and the
learn service are intact. Docs updated: `DEVELOPER_REFERENCE`, `CURRENT_STATE`, `KNOWN_ISSUES` §4.5a,
`MIGRATION_DELTAS`, `AUDIT_PUNCHLIST`, `CHANGELOG`.

### ✅ Phase 4 — Knowledge stat identity

**Chosen: faster proficiency gain.** `StatEffects.studyRate(knowledge)` → 1.00 at 0, 1.50 at 100,
multiplying the base increment in `SpellProficiencyTracker`.

Justification over the two alternatives: "higher chance to learn from a source on first read" makes a
found book sometimes worthless, which punishes exploration with RNG; "reduced fizzle" is PRECISION's
job and would give two stats one lever. Study rate is the only option that describes what being
well-read actually does, and it lands on the system that was already the mastery curve.

Three constraints, each with a test:
- the floor is **1.0, never below** — a discount ported carelessly onto a rate becomes a penalty on a
  stat nobody can train directly;
- **PLAYER_STATS off returns 1.0**, not a computed value from an unread stat;
- it multiplies an increment that **already tapers** above 0.8 proficiency.

`StatReadout` moved KNOWLEDGE out of its "lower is better" group; it reads `+50.0%` at 100 where it
read `-40.0%`. Lang: "Tuition Cost" → "Study Rate".

### ✅ Phase 5 — Verification

`./gradlew test` — green. `./gradlew runGameTestServer` — **26/26**, including seven new scenarios in
`SpellLearningTests` that drive the real click paths on a real `ServerPlayer` on a dedicated-server
harness:

- `spell_source_teaches_the_spell_written_in_it` — read opens, finishes, teaches, book survives;
- `spell_source_refuses_a_spell_already_known` — the gates still refuse through the reading path;
- `spell_source_blank_teaches_nothing`;
- `spell_source_respects_the_required_skill_gate` — the one gate no unit test can reach;
- `spell_source_survives_an_unresolvable_spell_id`;
- `spell_source_page_is_spent_only_on_success`;
- `spell_scriptorium_scribes_the_active_spell` — including that survival scribing spends exactly one
  ink bottle.

No teacher UI is reachable: the screen class does not exist and neither payload is registered.

---

## 5. Tests shipped

| Test | Covers |
|---|---|
| `SpellLearningServiceTest` | already-known, unmet requirement, unknown spell, unimplemented, Dark Arts with the module off, unreached mastery tier, Obscurial abilities, and that the refusal string is the eligibility layer's own verbatim |
| `SpellbookLootPoolTest` | every shipped pool id resolves, is implemented, and is neither a Dark Art nor an Obscurial ability — the modifier skips a bad entry *silently*, so this is the only warning there is |
| `SpellLearningTests` (game test) | the seven scenarios above |
| `StatEffectsTest` | study rate spans its range, is monotonic, and never drops below 1.0 |
| `StatReadoutTest` | the sheet's KNOWLEDGE number is derived from `StatEffects.studyRate` and reads `+50.0%` at 100 |
| `CanonItemCatalogTest` | unchanged and still green with the book promoted |

---

## 6. Reconciliation addendum — what the brief did not anticipate

**1. ~~`Spells` is nearly empty on a dedicated client.~~ — CORRECTED 2026-09-11.** This entry was
written against `AUDIT_PUNCHLIST`'s "no `SpellDefinition` sync payload exists" blocker, which was
**already stale**: `SpellDefinitionsSyncS2CPayload` mirrors the datapack table to every client on
login and on `/reload`, via `SpellDefinitionSyncEvents` on `OnDatapackSyncEvent`. `Spells.byId`
answers correctly on a dedicated client. The punchlist entry is now closed.

`SpellSource.writtenName` still builds a book's title from the stored id plus the
`spell.<namespace>.<path>.name` lang convention rather than from the registry — but the reason is
narrower than the one first given here. A title should not depend on the spell still existing: an id
whose definition a pack has since removed, or a legacy id out of an old save, resolves to `null`, and
a title built that way goes silently untitled. `SpellSourceItem.use` reads *blankness* off the
component for the same reason — a book that is written but unreadable is not the same refusal as a
blank one.

**2. `Spell.getDisplayName()` returns a raw `String` that is usually a lang key.** Passing it as a
`Component.translatable` argument renders the key literally. Every user-facing name here goes through
`writtenName` instead.

**3. Scribing is an addition, not in the brief.** Without it the lectern has no job and a server's
second player is left waiting on loot RNG for a spell the first one already knows. It costs one ink
bottle and takes the spell from the **active loadout slot** — the wheel is already the "which spell
am I thinking about" control, and a second picker would be a second thing to keep in step.

**4. The coin sink is now the open problem.** Removing the teacher fee removed the only sink that was
on by default. Ollivander's wands and the 493-Knut respec are what is left. Tracked in
`KNOWN_ISSUES` §5f; the replacement must buy an object or a service, never knowledge.

**5. `WizardTestSupport.placeMockPlayer` is creative.** Two of the four game tests were written
against survival assumptions and failed on it. The scribing scenario clears `instabuild` explicitly.
Separately: `Inventory.add` can land a stack in the selected hotbar slot, which `setItemInHand` then
overwrites — set the hand *first*.

---

## 7. Non-goals (held)

- No skill-web redesign.
- No proficiency-formula rework beyond the one KNOWLEDGE multiplier.
- No school simulation.
- No new spell content.
