# Wizards & Beasts — Progression Map

The whole player progression loop as it exists on `dev` (2026-09-28): what each stage gives, what earns it, what the
player is told, and where the loop was broken. The last sections record what this pass fixed and what needs a design
decision. Companion documents: `SKILL_WEB.md` (the web itself), `HERITAGE_IDENTITY_STATUS.md`,
`CREATURE_IDENTITY_STATUS.md`, `KNOWN_ISSUES.md` §4.5a (spell sources), §4b (bestiary), §5m (heritage).

---

## 1. The loop

```mermaid
flowchart TD
    JOIN[Player joins] --> CEREMONY[Heritage ceremony<br/>heritage + lineage (+ condition)]
    CEREMONY -->|3 skill points<br/>3 profession points<br/>POWER roll| WEB0[Skill web: first nodes]
    CEREMONY --> WANDTRIAL[Ollivander's trial<br/>(wandmaker villager)]
    WANDTRIAL -->|resonant wand| WAND[Own wand: bond UNFAMILIAR]
    WEB0 -->|core → basic casting → lumos_unlock| FIRST[First spell: Lumos]
    FOUND[Spell sources found<br/>books in villages, libraries, ruins,<br/>hidden caches; copied at a Study Lectern] --> FIRST
    FIRST --> PRACTICE[Practice: landed hits / spells that did their work<br/>≤ 40 per spell per in-game day]
    PRACTICE -->|50| PROF[Proficient<br/>+1 skill point<br/>gates the next spells]
    PRACTICE -->|200| MAST[Mastered<br/>+2 skill points, +1 profession point]
    PRACTICE --> POWERCURVE[Power curve: damage, cooldown,<br/>duration, control, accuracy]
    PRACTICE --> PRECISION[PRECISION stat]
    PRACTICE --> WANDBOND[Wand bond → MASTERED<br/>(every use, not capped)]
    PROF --> CHAIN[Spell chains:<br/>Stupefy → Incendio/Diffindo → Confringo/Bombarda<br/>Flipendo → Glacius · Accio → Depulso · Incendio(M) → Crucio]
    MAST --> WEB[Skill web: 84 nodes, 60-point cap<br/>260 points to fill → a build, not a checklist]
    PROF --> WEB
    CREATURES[Creature interaction:<br/>sight → watch → study → signature] -->|KNOWN page<br/>+1 skill point| WEB
    CREATURES --> KNOWLEDGE[KNOWLEDGE stat → study rate]
    KNOWLEDGE --> POWERCURVE
    CREATURES --> BONDS[Bonds: feed, trust, ride, gifts, breeding]
    WEB -->|learn_spell nodes| FIRST
    WEB --> ADV[Advanced magic:<br/>Apparition (node + test + licence) · Patronus line ·<br/>Legilimency · Animagus ritual · Dark Arts]
    ADV --> SPEC[Specialisation]
    WEB --> SPEC
    SPEC --- VOC[Vocation (declared)]
    SPEC --- PROFN[Heritage profession nodes]
    SPEC --- OWL[OWL exam → career]
    DEFENCE[Resisting Imperius / Legilimency] --> WILLPOWER[WILLPOWER stat]
    PROTEGO[Protego deflections] --> REFLEXES[REFLEXES stat]
```

Text form, for a reader without Mermaid:

```
JOIN → HERITAGE (3 SP, 3 PP, POWER) → WAND (Ollivander trial, free) → FIRST SPELLS (web: Lumos with the 3 SP; found books)
     → PRACTICE (hits; ≤40/spell/day) → TIERS (Proficient 50: +1 SP; Mastered 200: +2 SP +1 PP) + power curve + PRECISION + wand bond
     → SKILL WEB (84 nodes, cap 60, spells via learn_spell) ← CREATURES (KNOWN page: +1 SP; KNOWLEDGE → study rate)
     → ADVANCED MAGIC (Apparition, Patronus, Legilimency, Animagus, Dark Arts) → SPECIALISATION (vocation / profession / OWL career)
```

## 2. Stage by stage

| Stage | What the player gains | Earned by | Told by | Verdict |
|---|---|---|---|---|
| Heritage | identity, traits, 3 SP, 3 PP, POWER roll | the ceremony (first join) | selection screen, toast | sound |
| Wand | a wand that chose you; resonance score | Ollivander's trial at a wandmaker villager | trial screen | works; free and repeatable (see §6) |
| First spells | Lumos from the web with the ceremony's points; anything found in a book | allocating `lumos_unlock`; reading a spell source | toasts, spell menu | sound |
| Practice | hits → tier; power curve; PRECISION; wand bond | landing spells / spells that did their work | HUD pips, spell menu tier | **was broken** (§4, fixed) |
| Proficiency tier | gates the next spells; skill points | 50 / 200 practised hits | milestone toast | sound, now agrees with power |
| Skill web | spells, abilities, bonuses that each have a consumer | skill points | web screen with lore, practice text, provenance | sound (84 nodes, audited 2026-09-17; not inflated here) |
| Creatures | bestiary tiers, KNOWLEDGE, bonds, materials, **+1 SP per KNOWN page** | watching, studying, witnessing signatures, feeding | bestiary, action bar | now feeds the web |
| Advanced magic | Apparition, Patronus, Legilimency, Animagus, the Dark Arts | web nodes + tests/rituals/licences | own screens and toasts | sound |
| Specialisation | vocation (declared), heritage professions, OWL career | declaring; profession points; OWL exam | own screens | **conflicting** (§5, decision needed) |
| Player stats | POWER (rolled), PRECISION, REFLEXES, WILLPOWER (trained), KNOWLEDGE (derived) | hits, Protego deflections, resisting mind magic; spells + bestiary + nodes + books | character sheet | sound; WILLPOWER has no solo path (§6) |

## 3. Training: "what did I actually improve by practising?"

| Practice | What improves | Where it shows |
|---|---|---|
| Landing a spell / a spell doing its work (≤ 40 per spell per day) | its **tier** (gates, skill points), its **power curve** (damage 0.65×→1.5×, cooldown 1.4×→0.7×, duration, control, accuracy), **PRECISION** | spell menu tier, HUD pips, power tooltip |
| Any use of your wand | the **wand bond** (UNFAMILIAR → MASTERED) — how the wand answers you | wand tooltip |
| Deflecting with Protego | **REFLEXES** | character sheet |
| Throwing off Imperius / Legilimency | **WILLPOWER** (resist chance, Resolve pool) | character sheet |
| Watching and studying creatures | bestiary tiers → **KNOWLEDGE** (study rate: practice counts for more on the power curve) and **skill points** | bestiary, character sheet |

### Known ≠ proficient ≠ mastered

The distinctions exist and matter, so nothing was added:

| State | Meaning in play |
|---|---|
| Unknown | not castable; a book or node teaches it |
| Known (Novice) | castable at 0.65× damage, 1.4× cooldown; the next spells in its chain stay locked |
| Proficient (50) | baseline strength (1.075× / 0.97×); unlocks spells gated on proficiency; +1 SP |
| Mastered (200) | full strength (1.5× / 0.7×); unlocks mastery gates (Crucio needs Incendio mastered); +2 SP, +1 PP |

## 4. Findings

| # | Finding | Kind | Status |
|---|---|---|---|
| 1 | **Vanilla XP levels awarded skill points** — 1 per level, and a level regained after spending it on an anvil counted again. Mining and mob farms bought the whole wizard web without casting | meaningless XP, passive progression | **fixed**: XP levels award nothing; a KNOWN bestiary page awards 1 SP |
| 2 | **Tier and power curve disagreed.** The tier counted hits; the power curve read a float growing 0.002/hit. At "Mastered" the curve stood at 0.4: a mastered spell cast at 0.95× damage and 1.03× cooldown — weaker than untrained. HUD pips (float) and menus (tier) showed different skill | conflicting systems, reward that did not matter | **fixed**: one curve tied to the tier (Proficient = 0.5, Mastered = 1.0); KNOWLEDGE scales practice on the curve; old saves lifted on read |
| 3 | **Practice could be spammed.** Every self-cast counted; Nox (1 s cooldown) reached Mastered in ~3.5 minutes of clicking, paying 3 SP and a profession point per spell | click → wait → XP | **fixed**: ≤ 40 practices per spell per in-game day (Mastered ≥ 5 days), with an action-bar note when a day's practice is used up; the wand still bonds |
| 4 | **Heritage professions are stat soup.** Every node gets the same formula — a combat cooldown cut and, for Wizardkind, utility damage and **+max health** ("Seer: reads omens" → +2 HP) — whatever its description says. Contradicts "no flat bonus", duplicates the web's category effects | rewards that don't match their purpose | **fixed 2026-10-06** (§9): one identity `GameplayStat` per trade, no attributes |
| 5 | **OWL careers are a dead end.** The OWL exam grades real progression (nodes, casts, brews, bestiary) and lets the player choose one of 14 careers; nothing outside the OWL package reads the choice | dead-end system | **fixed 2026-10-06** (§9): a career pays a daily wage for its work and carries its licences |
| 6 | **Four specialisation layers** (web trees, vocations, heritage professions, OWL careers) answer the same question differently | redundant progression | **narrowed 2026-10-06** (§9): each layer now answers a different question |
| 7 | WILLPOWER only trains when another player casts Imperius or Legilimency on you | dead end for solo play | **fixed 2026-10-06**: Riddikulus finishing a Boggart |
| 8 | Ollivander's trial is free and repeatable (canon: seven Galleons) | reward that doesn't cost | fixed 2026-09-30 (first wand free, then 7 G) |
| 9 | `PlayerSpellData.MasteryTier` duplicates `Proficiency`; `Proficiency`'s own damage/cooldown multipliers are read by nothing since `SpellPower` | dead code that misleads readers | open, minor |
| 10 | Skill web: every node has a consumer (both `effects` and `nodeEffects` lists), provenance is enforced, 260 points to fill against a 60 cap | — | healthy; left alone |

## 5. Specialisation: the decision to make

Four things currently mean "what kind of wizard am I":

| Layer | Chosen by | Gives | Problem |
|---|---|---|---|
| Skill-web trees | spending points | spells, abilities, real mechanics | none — this is the working one |
| Vocation | declaring one (primary) | a few flags (+15% Dark damage, duelist spell power) | flat bonuses; overlaps the web |
| Heritage profession | profession points | the same formula for every node (combat cooldown, +HP) | stat soup; descriptions promise divination, wandmaking |
| OWL career | the exam | nothing | dead end |

Recommended direction (not implemented — it removes or re-purposes player-facing content): keep the **web** as the
place builds live; make the **OWL exam** the capstone that *certifies* a build and turn the career into the thing
vocations and professions try to be (a title, and at most one real unlock per career tied to an existing system —
Auror: the Unforgivables' Ministry exemption checks; Healer: dittany/episkey potency; Magizoologist: a bestiary tier
step); retire heritage professions' stat formula. Each of those is a separate, reviewable change.

## 6. Other open items

- **WILLPOWER solo path:** a Dementor's chill or a Boggart's fear resisted could train it; not done (no stat
  redesign in this pass).
- **Wand price:** Ollivander could charge — currency work, not progression.
- **Dead enums:** delete `Proficiency`'s multipliers and fold `MasteryTier` into `Proficiency` when next touching
  spell data.

## 7. What this pass changed

| Change | Files |
|---|---|
| XP levels no longer award skill points; a bestiary page reaching KNOWN awards 1 (by any earned path, including a mastered bond — `SYSTEM_INTERACTION_MAP.md` §3.3) | `event/skill/SkillEvents`, `bestiary/BestiaryDataHelper.setTier` |
| Practice limited to 40 per spell per in-game day; the day's count persists (`PracticeDay`, `PracticeCount`, additive NBT keys) | `spell/proficiency/SpellPractice` (new, pure), `SpellProficiencyTracker`, `spell/data/PlayerSpellData` |
| One proficiency measure: power curve tied to the tier, KNOWLEDGE scales practice, old saves lifted on read (server and client) | `SpellPractice`, `ProficiencyScaler`, `client/spell/hud/SpellDiamondOverlay`, `client/spell/ui/SpellPowerTooltip`, `spell/core/Spell` (cast pitch), `spell/command/ProficiencyCommands` |
| Player told when a day's practice is used up | lang `spell.wizards_and_beasts.practice.rested` |

Balance consequence: skill-point income now comes from the heritage ceremony (3), spell tiers (3 per spell over its
life, 28 implemented spells) and completed bestiary pages (1 each, 100+ species) — well above the 60-point cap, so the
web still fills, but only through magic and creatures. Existing players keep every point they have.

## 8. Validation

- `./gradlew build` green, including unit test `SpellPracticeTest` (daily limit, mastery ≥ 5 days, tier and curve
  agree, old save lifted, study rate never penalises).
- `./gradlew runGameTestServer`: all 156 required tests pass, including `ProgressionTests`:
  `progression_practice_is_spread_over_days`, `progression_mastered_casts_at_full_strength`,
  `progression_skill_points_come_from_magic_not_xp`.
- Mutation check: with `SpellPractice.counts` forced to `true`, exactly `progression_practice_is_spread_over_days`
  fails ("a day's practice counted 50 times, not 40"); restored afterwards.
- Every reader of the power-curve value goes through `SpellPractice.effective`: `ProficiencyScaler`, HUD pips,
  power tooltip, cast-sound pitch, `/wandb` proficiency `get`/`preview`. The debug `set`/`reset` commands still write
  the stored value only, so a reset spell with practised hits reads back at what its hits earned.

## Update 2026-09-30

- The wand price (§6) is now implemented: the first wand from the trial is free, every later one costs seven
  Galleons from the vault (`CANON_AUDIT.md` C-8, `ollivanderWandPriceKnuts`).
- One defeat now wins an ordinary wand (C-3). The "Wandlore grip" skill node still adds defeats a challenger needs.

---

## 9. Revision 2026-10-06 — closing the loop

Started 2026-10-01 (`tasks/todo_progression_loop_2026_10_01.md`), finished and verified 2026-10-06. No new
system: every link below joins two that already existed. Breaks were verified in source, not taken from
this document's earlier sections.

### 9.1 Revised graph

```
HERITAGE  identity traits · 3 SP · 3 PP · POWER roll
   │        └─ PP → heritage trade: one identity GameplayStat per node (Wandmaker: wand kept through a lost duel)
   ▼
WAND      Ollivander's trial (first free, then 7 G) · bond grows by practice
   ▼
DISCOVERY web (Lumos) · spellbooks/pages in villages, libraries, ruins · lore tomes + hidden caches now in loot
   ▼
PRACTICE  ≤40/spell/day → tiers (+SP/+PP) · power curve · PRECISION · wand bond
   ▼
SKILLS    84-node web, 60-point cap (the build)  ◄── bestiary KNOWN pages (+1 SP)
   ▼
VOCATION  declared; every vocation now has a live reader (Wandlore: −5 pts misfire)
   ▼
ADVANCED  Animagus · Patronus · Apparition · creatures/bonds · brewing · Mandrakes · lore
   ▼
O.W.L.    certifies the build: 8 subjects graded from live evidence · resit weekly, best paper kept
   ▼
CAREER    daily wage into the vault for the career's own work · Ministry licences (pass + career)
   ▼
WORLD     licences open Gringotts / racing brooms / restricted ingredients · standing gates nodes · Trace, fines
   ▼
ENDGAME   resit for O grades → O-gated careers (Hit Wizard, Obliviator, Unspeakable, Potioneer, Herbologist)
          · mastered spells · web keystones · coin spent on wands, Floo registration, respec, fines
```

### 9.2 Systems that now feed one another

| From | To | Through | Status |
|---|---|---|---|
| Practised spell tiers (Utility) | O.W.L. Charms | `OWLGradeRules` tier points (Proficient 1, Mastered 2) | done |
| Practised spell tiers (Combat, Defence; never Dark) | O.W.L. Defence | same | done |
| Brews collected | O.W.L. Potions; Healer/Potioneer wage | `CauldronBlockEntity` | done |
| Mandrakes + Herbology nodes; mature crops | O.W.L. Herbology; Herbologist wage | `MandrakeCropBlock`, `HerbologyAbilityHandler` | done |
| Bestiary STUDIED/KNOWN pages; feeding bonded creatures | O.W.L. Care of Magical Creatures; Magizoologist wage | `BestiaryDataHelper.setTier`, `BondableBeast` | done |
| Wandlore nodes; wands finished at the bench | O.W.L. Ancient Runes; Wandmaker wage | `WandmakersBenchMenu` | done |
| Lore tomes (now in library/temple/cartographer loot) | O.W.L. History of Magic; KNOWLEDGE | `scholars_shelf` table | done |
| Completed / registered Animagus | O.W.L. Transfiguration | ability data | done |
| O.W.L. passes; chosen career | Ministry licences (Ministry module on) | `CareerPapers` | done |
| Career work, once per in-game day | Gringotts vault | `CareerWork` | done |
| Riddikulus finishing a Boggart | WILLPOWER | `StatTraining.BOGGART_BANISHED` | done |
| Heritage trades (PP) | existing `GameplayStat` readers | `ProfessionNode.bonus` → `SkillSystemAPI.getGameplayBonus` | done |
| Wandlore vocation | cast misfire | `VocationAbilityHooks` `wand_steadiness` | done |
| Ruins, mansions, temples, ancient cities | the hidden wizarding cache (only source of Veela hair, White River Monster spine) | `hidden_cache_in_ruins` modifier | done |

### 9.3 Systems that must NOT be connected

| Link refused | Why |
|---|---|
| O.W.L. grade or career → spell damage/cooldown | a career is a job; the build lives in the web (`SYSTEM_INTERACTION_MAP.md` §5) |
| Coin → spell knowledge | spells are found and read, not bought (`KNOWN_ISSUES.md` §4.5a) |
| Vanilla XP → skill points | removed 2026-09-28; mob farms bought the web |
| Blood status → power | prejudice, not magic (`KNOWN_ISSUES.md` §5m) |
| Wage per act | farmable; a working day pays once, like the practice budget |
| Dark Arts practice → any wage | no lawful career is paid for curses |
| Standing → O.W.L. grades | the exam marks skill, not character |
| Killing creatures → Care of Magical Creatures | study counts; the harvest path already exists separately |
| O.W.L. pass → Apparition or Animagus licence | each has its own test or register |
| Arithmancy, Divination, Astronomy, Muggle Studies → any career | no content feeds them; they are "not examined" until it does |

### 9.4 Changes (this revision)

| Change | Files |
|---|---|
| O.W.L. grades from live evidence; 4 unfed subjects not sat; thresholds fitted to the 84-node web | `owl/OWLGradeRules` (new, pure), `OWLGradeCalculator`, `OWLSubject` |
| Career requirements name examined subjects only; one rule for server and client; Arithmancer not offered | `ProfessionEligibility`, `Profession`, `client/owl/screen/ProfessionSelectionScreen` |
| Resits: 7 in-game days apart, best paper per subject kept, career choice reopens after each sitting | `OWLExaminationHandler`, `owl/data/PlayerOWLData`, `RequestOWLExamPacket`, `OWLDataSyncPayload`, client O.W.L. screens and cache |
| Examination desk craftable (OWLS module) | `recipe/examination_desk.json` |
| Careers pay one wage per in-game day for the career's own work; licences issued with passes and careers | `owl/career/CareerProfile`, `CareerWork`, `CareerPapers` (new); hooks in `SpellProficiencyTracker`, `CauldronBlockEntity`, `HerbologyAbilityHandler`, `BondableBeast`, `BestiaryDataHelper`, `WandmakersBenchMenu`, `SpellScriptoriumBlock`, `BroomEntity`, `OwlPostService`; `heritage/data/PlayerProfessionData` |
| Licence scroll recipe yields 1 (the item never stacks) | `recipe/ministry_license_scroll.json` |
| Lore tomes and the hidden cache placed in loot | `loot_modifiers/lore_tomes_in_libraries.json`, `hidden_cache_in_ruins.json`, `loot_table/chests/scholars_shelf.json`, `global_loot_modifiers.json` |
| WILLPOWER solo path | `stats/StatTraining`, `spell/effect/SpellEffectComponent` (Riddikulus) |
| Heritage trades pay identity stats, never attributes | `heritage/profession/ProfessionNode`, `skill/SkillAttributeApplicator`, `skill/SkillSystemAPI`, `heritage/command/ProfessionCommands` (list prints what a trade pays) |
| Wandlore vocation does something | `vocations/wandlore.json`, `skill/vocation/VocationAbilityHooks`, lang |

Two leftovers from the 2026-10-01 session were found and fixed on 2026-10-06: the practice hook that pays
duelling and charmwork careers was disabled (`if (false)`), and `CareerWork.pays` compared
`today >= lastPaidDay`, which paid every act. Both were caught by the tests written for them.

### 9.5 Save migration

All fields are additive with defaults; no data version bump.

| Record | Old save loads as | Consequence |
|---|---|---|
| `PlayerOWLData` | `sittings` = 1 if `examTaken`, `lastSatTick` = 0 | may resit at once under the new marking; stale Trolls in unexamined subjects are dropped at that sitting and shown "not examined" before it |
| `PlayerProfessionData` | `chosenAtSitting` = 0, `lastPaidDay` = −1 | a career chosen under the old rules may be chosen again once; the first working day pays |
| Heritage trades | unlocked ids unchanged | `SkillAttributeApplicator.removeAll` strips every `skill/profession/*` modifier at the next `applyAll` (login). A Wizardkind player who owned all four trades loses up to +8 max health — intended |
| Wandlore vocation | unchanged id | its old `wand_affinity` boost never applied (no player carries the attribute), so there is nothing to strip |
| `OWLDataSyncPayload` | wire format changed | client and server must run the same build (always true for a mod) |

### 9.6 Tests

| Kind | Test | Pins |
|---|---|---|
| unit | `OWLGradeRulesTest` (10) | every examined subject reaches O from shipped data; unexamined never graded; a resit keeps the best paper |
| unit | `CareerProfileTest` (5) | every offered career is paid, every kind of work is paid by some career; once a day; licences; old-save codecs |
| unit | `ProfessionNodeTest` (3) | every trade pays a positive, bounded stat; a heritage line pays in ≤ 4 currencies; sums ignore unknown ids |
| unit | `StatTrainingReachabilityTest`, `AbilityGrantsTest` | Boggart WILLPOWER arithmetic; the Wandlore grant |
| game | `progression_owl_grades_what_was_practised` | five mastered charms → E; Arithmancy ungraded; no immediate resit |
| game | `progression_career_is_paid_for_its_work` | wage through the real practice seam, once a day, only for the career's own work |
| game | `progression_heritage_trade_pays_its_own_currency` | Wandmaker grip reaches `getGameplayBonus`; max health unchanged |
| game | `progression_wandlore_vocation_steadies_the_wand` | Wandlore takes exactly 5 points of misfire off a cast |

Not covered by automation: the client screens (no in-game pass yet), loot injection into real chests (boot
log only) and the Boggart path end to end (unit arithmetic only).

### 9.7 Phased plan

| Phase | Scope | State |
|---|---|---|
| 1 | O.W.L. regrade and resits, careers (wage, licences), loot placement, WILLPOWER solo path, heritage trades, Wandlore vocation | **done** (this revision) |
| 2 | Coin sinks that buy an object or a service (`KNOWN_ISSUES.md` §5f): licence endorsement fee, Apparition test fee, an ingredient apothecary; one real duty per career where a system already exists (Auror warrant read by the Ministry's case ladder, Healer potency) | proposed |
| 3 | World discovery feeding education: History of Magic sources in structures, the examination desk in a generated structure, the dangerous-creature licence gating breeding | proposed |
| 4 | Endgame: career standing from paid working days (promotion), a Dark Arts keystone once the module ships, a client screen for heritage trades | proposed — only after Phase 2 shows the wage loop is played |

### 9.8 Remaining gaps

- Coin sinks are still thin: wages are 10 Sickles to 1 Galleon a day against a 7-Galleon wand, the Floo fee,
  fines and a 1-Galleon respec.
- A career has no progression after it is chosen — the wage is flat.
- Heritage trades are command-only (`/wandb … profession`); seven of eight heritages are not selectable.
- Standing has exactly one gameplay reader (skill-node gating in `SkillSystemAPI`); the 2026-10-01 note that it
  had none was stale.
- Four O.W.L. subjects stay unexamined until arithmancy, divination, astronomy or Muggle-studies play exists.
- A goblin sees Curse-Breaker greyed on the client although the server would accept it (heritage is not known
  client-side).
- No in-game pass of the O.W.L. and career screens.

## Update 2026-10-08 — career tracks

The §9.8 gap "a career has no progression after it is chosen" is closed for the Magizoologist: ranks earned
with career experience and milestones, a daily contract board, rank-scaled wage and mastery rewards
(`documentation/PROFESSIONS.md`). Other careers keep the flat wage until they get a track. Career progress
never feeds spell power (§9.3 stands).

## Update 2026-10-14 — education

The O.W.L. is now a sitting (coursework + a timed practical/written paper), a N.E.W.T. level sits between the
O.W.L. and the elite careers, Astronomy is examined (telescope), keepers teach lessons up to an Acceptable, and
grades gate advanced brews and the web's capstones. The graph above gains one rung (O.W.L. → N.E.W.T. → elite
career / capstone). Design and tests: `documentation/EDUCATION.md`. §9.3 stands: no grade feeds spell power.
