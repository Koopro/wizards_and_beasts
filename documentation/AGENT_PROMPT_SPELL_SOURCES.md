# AGENT PROMPT — Spell Sources (Books, Notes, Pages)

**Status:** ✅ **Implemented 2026-09-10**, alongside `AGENT_PROMPT_SPELL_LEARNING.md`.
**Depends on:** that prompt's Phase 0–1 decisions — binary `knowsSpell`, no payment path.
**Path:** `documentation/AGENT_PROMPT_SPELL_SOURCES.md`

---

## Goal

Data-driven items that, on use, attempt to teach a spell through `SpellLearningService` / eligibility,
with clear feedback and no vendor UI.

---

## Rules — all held

| Rule | How |
|---|---|
| Same hard stack rules | 1.21.11 / NeoForge 21.11 / Java 21, `Identifier`, `DeferredRegister`, JSpecify, no mixin. |
| Prefer **item component** or small datapack registry over one-off Java per book | One `spell_source` component and **one** item class. Two registered items exist, and they differ by one boolean — see "Two items, not 128" below. |
| Server validates everything | `SpellLearningEligibility` runs on the server only. The client's half of `SpellSourceItem.use` reads *blankness* off the component and nothing else. |
| Client only shows read UI / particles / message | Name, tooltip and the read animation. The learn, the sync and every refusal string come from the server. |
| Do not bypass `SpellLearningEligibility` | Every path — the item, the loot roller's sanity check, the lectern's scribing refusals — asks it or a service that asks it. It is unmodified. |
| Starter sources for Lumos and Stupefy obtainable without the teacher | `hidden_wizarding_cache` carries a guaranteed written book, Lumos weighted over Stupefy; village-house shelves roll from a pool containing both. |

---

## Deliverables

### 1. ✅ Component + codec

`ModDataComponents.SPELL_SOURCE` — id `spell_source`, a `String` spell id, `persistent(Codec.STRING)`
+ `networkSynchronized(ByteBufCodecs.STRING_UTF8)`. **Absent means blank**, which is a legal and
useful state: a blank is what a player buys, finds and scribes onto.

`SpellSource` (in `spell/learning/`) is the only thing that touches it: `spellIdOf`, `spellOf`,
`isBlank`, `write`, and `writtenName`.

`write` stores the **canonical** id, not whatever alias it was handed. Storing an alias would leave
two stacks that teach the same spell looking different to every comparison the game makes on
components, stacking included.

### 2. ✅ Use handler → learn attempt → sync

`SpellSourceItem.use` → eligibility → `startUsingItem` (60 ticks) → `finishUsingItem` →
`SpellLearningService.tryLearnSpell` → `SpellDataSyncS2CPayload` + `PlayerStatsSyncPayload`.

Eligibility runs **twice**, once to open the channel and once to teach. Sixty ticks is long enough
for a skill node to have granted the spell, a profession to have lapsed, or the book to have been
swapped out of hand. No new packet was needed: the sync that already existed is the sync.

The read is a channel rather than a click on purpose. A spell learned on a single tap in the middle
of a fight makes the source a combat item; three seconds of standing still makes it study.

### 3. ✅ Lang + item models (datagen)

`torn_spell_page` is `generateFlatItem` in `ModModelProvider`, with its sprite from
`tools/spell_source_sprites.py` — a small generator that imports `canon_sprites`' palette, canvas and
keyline rather than reimplementing them, but keeps its own table because `CanonItemCatalogTest` reads
`CanonItemRegistry.ALL` and a mod-invented item does not belong in the canon catalogue.

`standard_book_of_spells` keeps the sprite and 3-D model it already had; only its class changed.

Lang keys: `item.wizards_and_beasts.spell_source.named` (`"%s: %s"` — the item's own name and the
spell's, so a shelf is readable without hovering), `.tooltip`, `.tooltip.spent`, `.blank`,
`.blank.tooltip`, plus `torn_spell_page` and its `.desc`.

### 4. ✅ Two starter sources and a mid-game example

| Source | Item | Read | Where |
|---|---|---|---|
| **The Standard Book of Spells — Lumos** | `standard_book_of_spells` | survives | `hidden_wizarding_cache` (guaranteed, weight 4); village houses |
| **The Standard Book of Spells — Stupefy** | `standard_book_of_spells` | survives | `hidden_wizarding_cache` (weight 3); village houses; stronghold libraries |
| **Torn Spell Page** (mid-game) | `torn_spell_page` | **spent** | abandoned mineshafts, dungeons, desert pyramids, jungle temples, underwater ruins, shipwreck supply — 0.4 |

Plus `spellbook_in_stronghold_libraries` (0.7) and `spellbook_in_woodland_mansions` (0.6) for the
later tiers.

### 5. ✅ Tests

| Case | Where |
|---|---|
| success | `SpellLearningTests.spell_source_teaches_the_spell_written_in_it` |
| already-known | unit `validateLearnAttempt_rejectsAlreadyKnownSpell` + game test `spell_source_refuses_a_spell_already_known` |
| gated — **skill** | game test `spell_source_respects_the_required_skill_gate` (needs a real player; the eligibility layer skips that branch when it has none, which is what every unit test gives it) |
| gated — **mastery** | unit `validateLearnAttempt_rejectsUnreachedMasteryTier`, which also asserts it *opens* once the tier is genuinely reached, so it cannot pass on an unrelated refusal |
| gated — **dark** | unit `validateLearnAttempt_rejectsDarkArtsWhenTheModuleIsOff` |
| gated — **unimplemented** | unit `validateLearnAttempt_rejectsUnimplementedSpell` |
| gated — **Obscurial** | unit `validateLearnAttempt_rejectsObscurialAbilities` |
| **no crash on bad id** | game test `spell_source_survives_an_unresolvable_spell_id` — drives *both* halves of the read, because a client that already began the animation will call the second one |
| loot pools are sane | `SpellbookLootPoolTest` — every id resolves, is implemented, is not a Dark Art, is not an Obscurial ability |
| consumption | game test `spell_source_page_is_spent_only_on_success` |

---

## Explicitly avoided

- **The old teacher screen.** It does not exist; neither payload is registered.
- **Charging Knuts in the source handler.** Phase 0 kept no paid exception. Nothing in the learning
  path touches the vault.

---

## Design notes the brief did not specify

### Two items, not 128 — and not one either

128 registered spells would be 128 ids, sprites and lang keys for one object with one variable field,
so the spell lives in a component. But there are **two** items, because there are two bargains:

- **bound** (`standard_book_of_spells`) survives being read — a textbook someone has read is still a
  textbook, and on a server the copy that taught one wizard Lumos has to be lendable to the next;
- **loose** (`torn_spell_page`) is spent — a single leaf studied to pieces is the only thing a found
  scrap plausibly is, and it is what keeps mid-game sources worth finding again.

Which of the two an item is belongs to the **item**, not the component. The component answers "which
spell"; putting "is this reusable" there too would make two visually identical pages behave
differently with nothing on the tooltip to say so.

### Spent on success only

A page refused by a gate has not been studied to pieces — it has been picked up and put down again.
Destroying it would mean an unmet requirement costs the player the only copy they had of the spell
they cannot learn yet. Pinned by a game test, because it is the kind of thing a later refactor
"tidies" into the wrong branch.

### The loot modifier rolls the spell; nobody picks it

`SpellbookLootModifier` takes a `spells` pool and an optional `item`, and rolls one entry. A found
book whose contents you choose is a catalogue with extra steps, and a catalogue is what this system
exists to not be.

The optional `item` is validated at parse time against `instanceof SpellSourceItem`, because the
failure it prevents is silent: a plain item carrying a `spell_source` component looks like a normal
drop, teaches nothing when used, and gives no clue why.

Unresolvable pool entries are skipped at **roll** time, not load time — the spell registry is
populated long after a datapack parses, so refusing to load would take the working entries down with
the typo. That makes the skip completely silent, which is why `SpellbookLootPoolTest` exists.

### Names are built without the registry

Not because the registry is unavailable. `SpellDefinitionsSyncS2CPayload` mirrors the datapack table
to every client on login and on `/reload`, so `Spells.byId` answers on a dedicated client. (An earlier
draft of this section claimed otherwise, citing an `AUDIT_PUNCHLIST` blocker that had already been
fixed; both are corrected.)

`SpellSource.writtenName` builds the title from the stored id plus the
`spell.<namespace>.<path>.name` lang convention that both halves of the corpus keep, with a
prettified fallback, because a book's title should not depend on the spell still existing. An id
whose definition a pack has since removed, or a legacy id out of an old save, resolves to `null` —
and a title built through the registry would go silently untitled on exactly the book that most needs
to say what it was for.

### Where the second copy comes from

Not a source item at all: `SpellScriptoriumBlock` (the Study Lectern, id still `spell_teacher`) lets
a wizard who knows a spell spend an ink bottle to write a blank book into a copy of it. That is what
makes the first found book matter to a whole server rather than to one player. See
`AGENT_PROMPT_SPELL_LEARNING.md` §6.3.
