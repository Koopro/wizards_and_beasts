# Professions — career tracks, contracts and mastery

Status: implemented 2026-10-08. The Magizoologist is the reference track and is fully playable end to end.
Every other career keeps its flat daily wage until it gets a track file.

## 1. The path

```
identity (heritage) → education (O.W.L.s) → career choice (eligibility by grades)
  → career track: rank 1 … rank N
      each rank = career experience + milestones (what the rank stands for)
      work that pays experience = activity the mod already records
      contracts = commissioned work from a daily board
  → mastery: top rank, its reward, and a wage three times the trainee's
```

What already existed and is kept:
- `Profession` lists the careers.
- `ProfessionEligibility` decides which O.W.L. grades open which career.
- `CareerProfile` sets each career's daily work, base wage and licences.
- `CareerWork` pays one wage per working day.
- `CareerPapers` issues licences.

A track adds the progression that follows the choice.

## 2. Architecture

| Piece | Where | Role |
|---|---|---|
| Track data | `data/<ns>/career_tracks/<profession>.json` | Ranks, experience rules, contracts. The file name is the profession. |
| Model | `owl/career/track/CareerTrack`, `ContractDefinition`, `CareerGoal`, `ActivityFilter`, `ActivityType` | Codecs; `CareerTrack.validate()` refuses a bad track at load. |
| Loader | `CareerTracks.Loader` (server reload listener) | One track per offered profession. |
| Rules (pure) | `CareerRules` | Experience and daily caps, contract progress, the daily board, promotion. Unit-tested. |
| Server | `CareerProgression` | The only writer of player career state. Hooks, buttons, rewards, the view. |
| State | attachment `career_progress` → `PlayerCareerProgress` | One record per profession, so changing career keeps the old rank. Copied on death. |
| Network | `CareerViewS2CPayload` (server-built view), `CareerActionC2SPayload` (view, accept, abandon, hand in) | The client sends intents only. |
| UI | Character Sheet **Career** tab (`client/gui/character/tab/CareerTab`) | Rank, experience, milestones, contracts in hand, today's offers. |

### Activities (what counts)

Each activity is one existing hook site. No new player action was invented.

| Activity | Hook |
|---|---|
| `bestiary_tier` | `BestiaryDataHelper.setTier`, earned path only. An admin `forceSetTier` never counts. |
| `research_completed` | `ResearchRewards.check`, first completion only |
| `creature_fed` | `BondableBeast.offerBondFood` |
| `bond_milestone`, `bond_mastered` | `BondableBeast.increaseBond` |
| `creature_bred` | `BondableBeast.spawnJuvenile`, credited to the owner of the young |
| `creature_groomed` | `Groomable.onInteract`, on success |
| `companion_trained`, `companion_mastered` | `Companions.afterChange`, when a companion first reaches the stage (see `COMPANIONS.md`) |

Adding a career that counts something new takes two steps: add an `ActivityType` value, then add one
`CareerProgression.record` call at the place where that activity happens.

### Goals (rank milestones)

Every goal reads state that another system already keeps:

| Goal | Reads |
|---|---|
| `owl_grade` | the player's best grade in a subject |
| `bestiary_pages` | pages at a minimum tier, optionally limited to creatures with a minimum Ministry rating |
| `research_completed` | completed field research |
| `activity_total` | lifetime career activity, optionally counting only distinct creatures |
| `contracts_completed` | contracts finished |

### Contracts

There are two objective kinds:
- **Activity.** Counts matching activity done *after* the contract is accepted. Progress is stored on the
  contract itself, not in the lifetime counters. With `distinct`, each creature counts only once.
- **Delivery.** Items handed in. The server counts and removes the items itself, and takes nothing unless the
  full amount is there.

How the board works:
- **The day's draw.** Each in-game day the board draws `board_size` offers, weighted, from the contracts the
  player's rank allows. The draw is seeded by player, day and rank, so reopening the tab or accepting one
  offer does not reshuffle the others.
- **Removed from the board.** Contracts already held, contracts completed within their `cooldown_days`, and
  contracts abandoned today are not offered.
- **Holding limit.** At most `max_active` contracts can be held at once.
- **Expiry.** A contract expires after `duration_days`. The player gets a notice and loses the contract;
  there is no other penalty.

Rewards:
- Coin goes to the vault through `GringottsBilling` under `MoneyFlow.CAREER_CONTRACT`.
- Career experience is added to the track.
- If the Gringotts module is off, only the experience is paid.

### Promotion

A player is promoted when the next rank's experience **and** every one of its milestones are met. Promotion
checks run after any activity, contract or hand-in, and also when a career is chosen.

On promotion the player gets:
- the rank's item rewards (to the inventory, or dropped if it is full);
- a toast;
- a **server-wide announcement**, which is the public part;
- a wage multiplier on the career's daily wage, applied by `CareerWork`.

## 3. Magizoologist (reference track)

| Rank | XP | Milestones | Wage | Reward |
|---|---|---|---|---|
| Trainee Magizoologist | 0 | — | 1.0× (14 S) | — |
| Field Naturalist | 200 | 5 bestiary pages at Observed | 1.25× | — |
| Beast Keeper | 600 | 1 creature raised to full trust; 2 contracts | 1.5× | Enchanted trunk |
| Senior Magizoologist | 1400 | 3 field research completions; 1 creature bred; 6 contracts | 2.0× | Expanded trunk |
| Master Magizoologist | 2800 | 6 pages Known; 2 of them XXXX or above; 12 contracts; Care of Magical Creatures at O | 3.0× | **Newt's Case** (Scamander sanctuary pocket) |

**Experience** (first matching rule wins; daily caps in brackets):

| Activity | XP |
|---|---|
| Bestiary page to Known | 40 [120] |
| Bestiary page to Studied | 20 [80] |
| Bestiary page to Observed | 10 [60] |
| Bestiary page to Encountered | 5 [30] |
| Research completed | 60 [180] |
| Bond mastered | 80 [160] |
| Bond milestone | 15 [60] |
| Creature bred | 40 [80] |
| Creature groomed | 10 [20] |
| Creature fed | 3 [15] |

**Contracts:** 18 in total, with every rank opening some.
- Surveys and first sightings.
- Feeding rounds.
- Specimen deliveries: groomed unicorn hair, shed demiguise hair, mooncalf dung, a holly sapling given by a
  bowtruckle, and a phoenix feather given by a phoenix.
- Trust programmes.
- Dangerous-creature surveys.
- Breeding programmes.
- Companion work: train a field partner (Beast Keeper), master a companion (Senior).
- Research papers.
- A Welsh Green survey.
- Rare-creature research.

`CareerTrackDataTest` checks the track against the rest of the shipped data:
- every creature it names spawns in a default world (alpha roster plus a spawn modifier);
- every delivery is something a creature gives without being killed;
- breeding work names only creatures that breed;
- every rank milestone can be reached with the creatures that exist.

**What the player does:**
1. Sit the O.W.L.s.
2. Choose Magizoologist.
3. Open **C → Career**.
4. Take a survey.
5. Go and find creatures.
6. Bond and feed them.
7. Groom a unicorn for St Mungo's.
8. Breed mooncalves.
9. Complete research on dangerous beasts.
10. Rise through five ranks to Master Magizoologist and receive Newt's Case: a sanctuary to keep creatures in.

## 4. Multiplayer, authority, persistence

- **Server-authoritative.** All state is written by `CareerProgression` on the server, from server-side hooks.
  The client receives a ready-made view and sends intents.
  - Accepting a contract re-derives today's board on the server.
  - Handing in a delivery counts the items on the server.
- **Rate limiting.** Actions are limited to one every 4 ticks per player, and contract ids are capped at 64
  bytes when the packet is decoded.
- **Persistence.** The attachment is copied on death. The GameTest `career_progress_survives_relog` checks a
  real save and reload.
- **Public information.** Promotions are broadcast to everyone on the server. Experience and contracts are
  private.
- **Module gating.** The `OWLS` module gates everything; with it off, nothing records and the tab says
  careers are off. Contract coin also needs the `GRINGOTTS` module. Career choice now checks `OWLS` on the
  server.

## 5. Adding a profession

1. Write `career_tracks/<profession>.json`. The schema is the Magizoologist file.
2. Add lang keys:
   - `career.wizards_and_beasts.<profession>.rank.<id>`
   - `career.wizards_and_beasts.<profession>.contract.<id>`
   - `career.wizards_and_beasts.<profession>.contract.<id>.brief`
3. If the career's work is not yet an `ActivityType`, add the value and one `record` call at its hook site.
   Likely candidates:
   - Healer or Potioneer: brew collected (`CauldronBlockEntity`).
   - Wandmaker: wand finished (`WandmakersBenchMenu`).
   - Auror: an incident or case closed (`MinistryTrace`). This needs the Ministry module and the law fixes
     listed in `WIZARD_LIFE_AUDIT.md`, Phase 6.
4. Add a data-join test like `CareerTrackDataTest` for the new file.

## 6. Tests

- **`CareerRulesTest` (12 tests, pure).**
  - Experience: first-match rule, daily caps, cap remainder, day reset.
  - Contracts: distinct progress, ignoring unrelated activity, expiry.
  - Board: stability, rank gating, removing held, recent and abandoned contracts.
  - Promotion: experience and milestones are both required, and there is no rank past the top.
  - Lifetime totals: rating and distinct filters.
  - The saved record round-trips through its codec.
- **`CareerTrackDataTest` (7 tests).**
  - The track is sound.
  - Creatures it names are reachable.
  - Deliveries can be obtained without a kill.
  - Breeding work is consistent.
  - Milestones are reachable.
  - Rank rewards are real items.
  - Lang keys exist.
- **`CareerTests` (7 GameTests on a live server).**
  - Choosing the career opens the track.
  - A survey contract pays through the real `setTier`.
  - A delivery is refused when short and takes exactly the items when complete.
  - Promotion needs both experience and milestones, and the wage rises with it.
  - The board refuses unoffered contracts and a full hand.
  - Progress survives a relog.
  - A career without a track is unchanged.
- **Visual check.** `client/debug/CareerShowcaseCapture` (`WB_CAREER_SHOWCASE=1 ./gradlew runClient`)
  captures the Career tab at trainee and master rank.

## 7. Not done (intentionally)

- **Other careers have no track yet**, by decision for this pass.
  - Auror depends on the Ministry module (disabled by default) and on law fixes.
  - Healer, Potioneer and Wandmaker need their hook sites added.
- **Contracts come by owl only in name.** They are on the board in the tab; no letter item or owl entity
  delivers them.
- **No physical department or office.** The audit's Phase 4 (places) would give contracts a location.
- **The dangerous-creatures licence is still not read by keeping or breeding.** Magizoologists hold it, but
  enforcing it would change balance for everyone. That is a separate decision.
- **Career experience does not feed anything outside the career.** That is deliberate: per
  `SYSTEM_INTERACTION_MAP.md` §5, a career never multiplies spell power.
