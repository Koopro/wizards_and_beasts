# The Bestiary as a Magical Naturalist's Field Journal

Written 2026-10-07 from the code. Companions: `BESTIARY_ILLUSTRATION_SPEC.md` (plates), `CREATURE_ECOLOGY.md` (field
notes), `KNOWN_ISSUES.md` §4b (the tier rule, unchanged).

## 1. What stayed

Discovery is still the server's: `EncounterRule` decides a player's depth on each page (UNKNOWN → ENCOUNTERED →
OBSERVED → STUDIED → KNOWN) by sighting, calm watching, hands-on study and signatures — never by killing. The
client only draws what it is told.

## 2. Progressive reveal

`JournalLayout` (datapack: `data/<ns>/bestiary_journal/layout.json`, synced) lists every section, the page it sits on
(plate or notes), the depth it opens at, and an optional earlier *partial* depth. `JournalReveal` — pure, the single
gate — turns layout + depth into LOCKED / PARTIAL / OPEN per section.

| Section | Page | Partial | Opens |
|---|---|---|---|
| Ministry classification · Size | plate | — | Encountered |
| Habitat | plate | Encountered: *where you first met it* | Observed |
| Temperament · Danger | plate | — | Observed |
| Research · My history | plate | — | Encountered |
| Behaviour (seen with my own eyes) | notes | — | Encountered — the field notes |
| Behaviour (from the books) · Diet | notes | — | Observed |
| Feeding and trust | notes | Observed: *whether it takes food from a hand* | Studied |
| Magic · Weaknesses · Harvesting · Materials · Wizarding-world knowledge | notes | — | Studied |
| Signature behaviour | notes | Studied: *something remains unseen* | Known |

Rules that keep it honest:
- An undiscovered creature has **no page and no silhouette**, and cannot be searched for by name.
- Every discovered page shows the **same headings**; a locked one is a struck pencil line plus how to earn it, so a
  locked "Harvesting" says nothing about whether the creature yields anything. An open section with nothing to say
  reads "None recorded."
- The whole entry is synced (its texts are lang keys every client ships), so the gate is the screen's — a modified
  client could read the book, as with vanilla advancements. What it cannot do is raise its own depth, write notes or
  history, or claim a reward: all of that is server-side.

## 3. What a page draws on

| Source | Where it comes from | Synced as |
|---|---|---|
| Entry (lore, habitat, size, magic, weaknesses, threats, profile, signature) | `bestiary/entries/*.json` | `SyncBestiaryEntriesPayload.entries` |
| Optional `journal` block (art overrides, signature caption) | the entry | same |
| Temperament, ecology, what it eats from a hand, gift, harvest rules | derived on the server from creature definitions, `creature_ecology`, `creature_bonds`, `bestiary/harvest` (`JournalFacts`) | `SyncBestiaryEntriesPayload.facts` |
| Layout | `bestiary_journal/layout.json` | `SyncBestiaryEntriesPayload.layout` |
| Depth, field notes, watching time, harvest lockouts, **history** | `PlayerBestiaryData` (saved) | `BestiaryDataSyncPayload` (the whole record, via its save codec) |

`EntryHistory` (per player, per creature): first-met day, biome and dimension; the day each depth came; the study acts
performed (feed, handle, harvest = took what it shed, bond = first trust milestone); research completion.

## 4. The screen

`client/bestiary/gui/BestiaryScreen` in the `field_notebook` material: an **index** (categories, depth pips that turn
green on completed research, the journal's front matter on the facing page) and, per creature, a **two-page spread**.
The left page carries the plate (the earned stage, a *Signature* switch once known, a *Live* model switch once
studied) and the plate sections. The right page carries the notes, written by `JournalPageWriter`. Arrows leaf
through the pages in index order. No creature has a hand-made layout.

## 5. Research and its reward

`ResearchProgress` (pure) — a checklist the page prints:
1. Met it, watched it, studied it, knew it (the four depths);
2. Every hands-on act its profile names (feed, handle, take what it sheds, earn its trust);
3. At least half of the behaviours its ecology lets a player watch (`driven` capabilities, from field notes).

Completion is granted once (`ResearchRewards`) and lands in systems that already exist — no points:

| Reward | System | Effect |
|---|---|---|
| It knows you | `WildlifeRules.letsNear` | a wary creature lets you near without creeping up empty-handed; a slayer or darkened soul is still refused |
| Harvest | `BestiaryHarvestLootModifier` / `HarvestGate` | its rare material comes back in half the time |
| O.W.L. | `OWLGradeRules` Care of Magical Creatures | +1 per completed study |
| Career | `CareerWork` | a Magizoologist is paid for the day |

Existing ties are kept: KNOWN still pays one skill point (`SkillEvents`); the Magizoology web's category bonuses still
speed watching and its gentle-harvest line still raises harvest chance; ecology behaviour seen still writes field notes.

## 6. Tests

| Test | Pins |
|---|---|
| `JournalRevealTest` | nothing opens before its depth; same headings on every page; reveal is progressive; partial stages; unknown = no page |
| `ResearchAndFactsTest` | completion rule; descriptive ecology does not count; researched handling vs purity; temperament derivation |
| `JournalSyncTest` | player record and the book (entries + layout + facts) survive their real stream codecs; old saves load |
| `JournalDataTest` | shipped layout = default; broken layouts refused; art overrides and captions resolve; showcase plates complete, 96 × 96 RGBA |
| `JournalTests` (game) | history by depth with first meeting; study act recorded **and sent to the client**; research completes once and its handling, O.W.L. and harvest rewards land |

Mutation-checked: removing the history write, the act write, the completion mark, the handling reward or the harvest
halving each fails its scenario.

## 7. Not done

- Plates for the other ~98 creatures (the fallback chain covers them).
- No in-client pass of the screen.
- Pages scroll by wheel only; the old screen's unused textures (`textures/gui/bestiary/screen.png` etc.) are left in
  place for `gui_chrome.py`.
