# AGENT PROMPT — Retire spell/teacher package

**Status:** ✅ **Done 2026-09-10** as Phase 3 of `AGENT_PROMPT_SPELL_LEARNING.md`; re-audited against
this brief **2026-09-11**, which turned up four things the first pass missed.
**Depends on:** learning loop no longer requires catalogue UI — it does not.
**Path:** `documentation/AGENT_PROMPT_RETIRE_SPELL_TEACHER.md`

---

## 1. Map — everything that was under the vendor

| Thing | Kind | Outcome |
|---|---|---|
| `spell/teacher/SpellTeacherBlock` | block | **kept, moved** → `spell/study/SpellScriptoriumBlock`. Registry id still `spell_teacher` |
| `spell/teacher/ModNetworkTeacher` | network registration | deleted (+ its line in `ModNetwork`) |
| `network/spell/teacher/SpellTeacherOpenS2CPayload` | packet | deleted |
| `network/spell/teacher/SpellTeacherLearnC2SPayload` | packet | deleted |
| `client/spell/gui/SpellTeacherScreen` | screen | deleted |
| `client/spell/state/ClientSpellTeacherState` | client state | deleted |
| `SpellClientPayloadHandlers.handleSpellTeacherOpen` + `openClientScreenSafe` | handler | deleted |
| `ClientScreenHooks.openSpellTeacherScreen` | hook | deleted |
| `SpellLearningService.buildOffers` / `SpellOffer` / `tuitionFor` | service | deleted |
| `Config.spellTeacherRequirePayment` / `spellTeacherLearnCostKnuts` | config | deleted (+ 2 rows in `WizardsConfigScreen`) |
| `StatEffects.tuitionCost` / `tuitionMultiplier` | stat effect | deleted — see `AGENT_PROMPT_KNOWLEDGE_STAT.md` |
| `block.wizards_and_beasts.spell_teacher` | lang | **kept**, retitled "Study Lectern"; six new `.usage`/`.scribed`/refusal keys added under the same prefix |
| `advancement/progression/spell_teacher.json` | advancement | **kept**, retargeted: icon and criterion are now the spellbook, description rewritten. Id frozen — an advancement id change re-fires the toast for everyone who had it |
| `recipe/spell_teacher.json`, `loot_table/blocks/spell_teacher.json`, `bench_enhancers/spell_teacher.json`, blockstate + models | data/assets | **kept**, all still valid — the block still exists |

No triggers, no menu types, no block entity: the vendor screen was opened by a packet, not a
`MenuType`, so there was nothing on the container side to unpick.

**Why the block was kept rather than deleted.** Deleting a registered block turns every placed copy
into air on world load and orphans a sprite, a recipe, an advancement, a loot table and a
bench-enhancer entry. The class name and the id deliberately disagree now, the same way
`wand_wood_legacy` disagrees with `WandWood`.

---

## 2. Shared widgets — swept, nothing orphaned

The deleted screen used `McStylePanel` (149 references across the mod) and `GuiScaleHelper.Layout`
(37). Both stay. `PacketCodecUtils.MAX_KNOWN_SPELLS`, which the deleted open-payload bounded its
list with, is still used by `SpellDataSyncS2CPayload`. No GUI skin or material was registered for the
teacher screen, so nothing was orphaned in the skin system either.

**One real leftover, found on the re-audit:** `SpellClientPayloadHandlers` still imported
`ClientScreenHooksInvoker` after `openClientScreenSafe` was deleted. Removed. An unused-import scan
over every file the retirement touched found no others.

---

## 3. Commands and skill `learn_spell` — verified, and two defects found

Both paths are covered by new game tests rather than by inspection, and **the command path did not
work**:

### `skill_node_still_teaches_its_spell`

Allocates `frigora_unlock` (a real node carrying a `learn_spell` effect) via
`SkillSystemAPI.forceUnlock` and asserts both that the spell is known **and** that the grant landed
in the web-taught ledger — the ledger being what a respec revokes, so a grant that skipped it would
look correct and quietly survive a respec forever. Passed first time; the skill path never touched
the vendor.

### `spell_learn_command_still_works` — found two pre-existing bugs

Runs `/wandb magic spell learn alohomora` through the real dispatcher as the player.

**Defect 1 — the command stored a non-canonical id.** `SpellCommands.learnSpell` resolved the `Spell`
and then keyed `PlayerSpellData` by the **raw argument**: `data.learnSpell(spellId)`, not
`spell.getId()`. `PlayerSpellData.knowsSpell` is a plain set lookup with no normalisation, while
`Spells.byId` accepts a bare path, a namespaced id and a legacy namespace — three spellings of one
spell. So `/wandb magic spell learn alohomora` stored `alohomora` while every reader in the mod looks
up `wizards_and_beasts:alohomora`: **the spell was learned and uncastable.** `forget` and `info` had
the same shape — `forget` could not remove what `learn` had written, and `info` read cast counts off
a key nothing writes.

Fixed by canonicalising to `spell.getId()` after resolution in all three, matching
`SkillSystemAPI.teachSpell`, which already did this and says why.

Proven rather than assumed: reverting the one-line fix turns the test red with
`known set holds [alohomora]` against a lookup for `wizards_and_beasts:alohomora`.

**Defect 2 — the completions were unparseable by their own command.** All three subcommands suggested
`Spells.all().map(Spell::getId)`, which is namespaced, into a `StringArgumentType.word()` argument,
whose charset has no colon. Every suggestion the command offered put a red line under itself on
acceptance. Suggestions now emit the bare path — which `word()` parses and `Spells.byId` resolves by
prefixing — falling back to the full id for a spell in someone else's namespace, where the bare path
would resolve to the wrong spell or to nothing.

Neither defect was caused by retiring the vendor. Both were found by the smoke test this brief asked
for, in the same package the retirement touched.

---

## 4. Package tree updated

`DEVELOPER_REFERENCE.md`: `spell` no longer lists `teacher` and now lists `study`; `network`'s entry
dropped its `spell (teacher)` parenthetical, since `network/spell` has no subpackages left.

The same line had **pre-existing drift** unrelated to this work — it omitted `debug` and `petrify` and
listed a `tag` package that does not exist. Corrected against the filesystem rather than just
patching out the word "teacher".

The class/API tables gained `SpellSource`, `SpellSourceItem`, `SpellScriptoriumBlock` and
`SpellbookLootModifier`, and the "Add a spell" extension guide lost its "teacher data" step.

---

## 5. Smoke

- **No reachable teacher screen in survival.** `SpellTeacherScreen` does not exist; neither payload is
  registered in `ModNetwork`; nothing calls `openSpellTeacherScreen`. Right-clicking the lectern
  empty-handed shows an action-bar line telling you what it wants, which is the affordance a player
  who knew the old block will reach for first.
- **Learning still works by all three routes**, each with a game test: sources
  (`spell_source_teaches_the_spell_written_in_it`), skills (`skill_node_still_teaches_its_spell`),
  commands (`spell_learn_command_still_works`).
- `./gradlew test` green; `./gradlew runGameTestServer` **28/28**; server boots clean.

---

## Rule: delete, don't disable — held

Nothing was left behind a flag. There is no removal ticket in `AUDIT_PUNCHLIST.md` because there is
nothing left to remove.

Two `AUDIT_PUNCHLIST` entries were *edited* during this work, both about the teacher:

- the untranslated-refusal POLISH item, which survived the rewrite unchanged and now describes the
  action-bar path instead of the screen;
- the **"a remote client's spell registry holds 6 spells"** BLOCKER, which turned out to be **stale** —
  `SpellDefinitionsSyncS2CPayload` fixed it via `OnDatapackSyncEvent` at some earlier point and the
  entry was never closed. Closing it also required correcting `AGENT_PROMPT_SPELL_LEARNING.md` §6.1
  and `AGENT_PROMPT_SPELL_SOURCES.md`, which had both cited the stale entry as live when justifying
  `SpellSource.writtenName`. That method's behaviour is unchanged and still correct; its stated reason
  was wrong and is now narrower — a title must not depend on the spell still existing, since an id
  whose definition a pack has dropped resolves to `null` and would go silently untitled.
