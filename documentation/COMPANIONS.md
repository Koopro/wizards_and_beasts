# Companions — bonding grown into a life with a creature

The companion layer sits on top of the creature bond layer (`creature/bond/`). It does not replace it. A
creature with a bond profile (`creature_bonds/<species>.json`) and a companion profile
(`companions/<species>.json`) is a companion. A creature with only a bond profile works exactly as before.

Code: `creature/companion/`. Data: `companions/`, `companion_traits/`. UI: the **Beasts** tab of the
Character Sheet (`client/gui/character/tab/BeastsTab.java`).

## 1. The path

| Stage | Reached by | Opens |
|---|---|---|
| Wild | no owner, or bond below `curious_at` | — |
| Curious | bond ≥ `curious_at` (default 10) | — |
| Trusting | bond ≥ `trusting_at` (default 30) | follow, stay, rest; bestiary page → Observed |
| Bonded | bond ≥ the bond profile's `followThreshold` | return, search, assist; bestiary page → Studied |
| Trained | training ≥ `trained_at` (default 200) | defend, attack, patrol; career activity `companion_trained` |
| Mastered | training ≥ `mastered_at` (default 500) **and** bond at the mastery mark | temperament no longer refuses orders; career activity `companion_mastered` |

The stage is never stored as truth. `CompanionRules.stage` derives it from bond, training and the profiles
every time it is needed. `lastStage` is stored only so a rise can be noticed once, then remembered,
announced and credited. A fall caused by a betrayal lowers the mark quietly.

## 2. Bond and training

Bond comes from meaningful interactions. Each kind of interaction has a daily cap, counted in base units
before personality: the cap limits how *often* an interaction counts in a day, and personality decides how
*much* each one is worth. A greedy niffler still gets one favourite treat a day, but values it twice as much.

| Interaction | Base | Daily cap | Where |
|---|---|---|---|
| feed | the bond profile's feed gain | none (the feed cooldown limits it) | `BondableBeast.offerBondFood` |
| care | 3 | 6 | sneak + empty hand (a pat), or feeding it while hurt (heals 2) |
| explore | 1 | 6 | a new chunk reached together while following, checked every 5 s |
| combat | 2 | 6 | its owner or the companion kills a hostile nearby (`CompanionEvents.onDeath`) |
| training | 1 | 5 | every order carried out |
| gift | 8 | 8 | the species' `favourite` item, from the owner |
| help | 2 | 6 | a successful search or assist |

**Training** is a separate measure. Each lesson is worth 10 points × the traits' `training_rate`, with a cap
of 50 a day. A lesson is any of these:
- a return;
- a successful search or assist;
- a hostile beaten together;
- a stay or rest held for a minute (`HOLD_TO_TRAIN`) while the owner is out of sight range.

Clicking does not train a creature. Only orders it carries out count.

**Who can raise it.** Only the owner and players the owner trusts can feed, ride or calm an owned creature.
A stranger's food is refused, with a message, and the item is kept. A trusted friend's kindness raises the
bond, but milestones, bestiary pages and career credit go to the owner.

## 3. Personality

`companion_traits/<id>.json`:

```json
{ "bond_multipliers": { "care": 1.5, "combat": 0.5 }, "refuses": ["attack", "defend"],
  "flee_below_health": 0.5, "follow_distance": 1.0, "training_rate": 1.0, "guards_owner": false }
```

When a creature is first claimed it rolls `traits_per_creature` (default 2) distinct traits from its
species' weighted `trait_pool`, seeded by its UUID. It keeps them for life, and they survive release. No
species can roll every trait; `CompanionDataTest` enforces this.

Shipped traits: brave, skittish, greedy, playful, loyal, aggressive, territorial, curious. What a trait
changes:
- **Bond multipliers** per interaction.
- **Refusals**: skittish refuses attack and defend until Mastered.
- **Fleeing**: below the health fraction it stops fighting and takes no new target.
- **Follow distance**: loyal keeps close, territorial ranges wide.
- **Training rate.**
- **guards_owner**: a Bonded creature that can fight and has this trait turns on whatever hurts its owner,
  even while following. Without it, only defend or patrol does.

## 4. Commands

| Command | Kind | Needs | What it does |
|---|---|---|---|
| follow | mode | Trusting | the bond layer's follow goal (the default; what every bonded creature did before) |
| stay | mode | Trusting | holds at the spot where it was told; drops its target |
| rest | mode | Trusting | as stay, and heals 1 health every 5 s |
| return | order | Bonded | walks to its owner, or appears beside them from more than 16 blocks away; back to follow |
| search | order | Bonded | uses its SEARCH abilities |
| assist | order | Bonded | uses its ASSIST abilities on the owner (within 16 blocks) |
| defend | mode | Trained | follows the owner and targets whatever hurts them or whatever they strike |
| attack | order | Trained | sets it on the creature the owner is looking at (24 blocks) |
| patrol | mode | Trained | wanders around its anchor, engaging hostiles |

`CompanionRules.check` decides each order in this sequence:
1. The species understands it (the profile's `commands`).
2. It is not a combat order to a creature that cannot fight (`can_fight` and an attack attribute).
3. The stage allows it.
4. No trait refuses it. This check is skipped once the creature is Mastered.

Only the owner gives orders, within 64 blocks and in the same dimension. `return` is the exception and works
from any distance. Companions never target their owner, a trusted player, or another of the owner's
creatures. They target players only when the protection rules would let a player hurt them.

How commands are given:
- **Beasts tab.** Click a command button. The tab sends `CompanionActionC2SPayload`, which is an intent
  only: the server re-checks everything, and the payload is rate-limited to one every 4 ticks.
- **Sneak + empty hand on the creature.** Toggles follow and stay, and counts as a pat.

## 5. Species abilities

Abilities are a sealed `CompanionAbility` with a data dispatch on `"kind"`:

| kind | Command | Fields |
|---|---|---|
| `heal_owner` | assist | `amount`, `cooldown_seconds` |
| `cleanse_owner` | assist | `cooldown_seconds` — removes harmful effects |
| `reveal` | search | `target` (`valuables`, `wandwood`, `threats`, `dark`), `radius`, `cooldown_seconds`. Outlines blocks or entities for the owner through `SpellOutlines` |
| `retrieve_items` | search | `radius`, `max_stacks`, `cooldown_seconds` — brings loose items to the owner |
| `scout` | search | `radius`, `seconds`, `cooldown_seconds` — turns invisible and reveals threats |
| `innate` | — | `id` — a trait the species already has elsewhere (mount, rebirth, pouch…), listed for the player |

Shipped:

| Species | Commands | Abilities | Fights |
|---|---|---|---|
| Niffler | follow stay rest return search | retrieve items; reveal valuables; pouch | no |
| Bowtruckle | + defend patrol | reveal wandwood; lock picking | yes |
| Hippogriff | follow stay rest return defend attack patrol | mount; aerial travel | yes |
| Thestral | + search | reveal threats; mount; unseen | yes |
| Phoenix | follow stay rest return assist defend | heal; cleanse; rebirth; flame travel | yes |
| Demiguise | follow stay rest return search | scout; foresight | no |
| Unicorn | follow stay rest return assist search | heal; reveal dark; purity | no |
| Griffin | follow stay rest return defend attack patrol | hoard guard | yes |
| Mooncalf | follow stay rest return | moon dance; fertile dung | no |

Unicorn and Demiguise became bondable for this work: they have new `creature_bonds/` files.

## 6. Ownership and protection

The owner has one list of trusted players and one list of hostile players. A player cannot be on both lists.
The lists live on the owner's roster, and each creature copies them every 20 seconds, so a creature still
knows them while the owner is offline. `/wandb player companion trust|untrust|hostile|unhostile <player>`
and `… list` manage the lists.

| Who | Hurt it | Feed / ride / calm | Command |
|---|---|---|---|
| Owner | yes, and pays the betrayal penalty | yes | yes |
| Trusted | no | yes | no |
| Hostile | yes | no | no |
| Stranger | only under `companionProtection = PVP_FLAG` with PvP on | no | no |

`companionProtection` is a server config value: `OWNER_ONLY` (the default) or `PVP_FLAG`. A blow from
another player's creature counts as that player's blow. Refused damage is cancelled at
`LivingIncomingDamageEvent`, so it never lands and cannot be worn down.

Owned creatures never despawn (`MobDespawnEvent` → DENY). Companions are `PathfinderMob`s, not `Animal`s,
so before this an owned niffler left 128 blocks behind was simply deleted.

**Release** (Beasts tab): the creature forgets its owner. Its bond goes to 0 and it loses its mode,
training and trust lists. Its personality, lineage and story stay with it.

## 7. Breeding

Breeding uses the bond layer's existing system (`BondBreeding`: item, min bond, love, cooldown, growth,
inherited bond). The companion profile's optional `breeding` adds three things:
- `habitat`: `needs_sky`, `biomes` (tag), and `blocks` (tag) with `count` within `radius`. Outside its
  habitat the pair loses the mood, and the owner is told why. Bowtruckles need 6 logs within 4 blocks;
  mooncalves need open sky.
- `compatible`: other species it can pair with. The default is its own species only.
- `mutation_chance` (0.15).

**The juvenile** (`CompanionBreeding.onBorn`):
- Traits: one from each parent. With `mutation_chance`, the second is replaced by a fresh roll from the pool.
- Generation: the higher parent's generation + 1.
- Records its parents' names, its birth day, and a "born" history line. The parents remember "raised young".

There is no genetic simulator, and coat variants are not inherited (see §10).

## 8. Roster, persistence and sync

- **On the creature** (`BondState` → `CompanionMemory`, one `Companion` key): traits, mode, anchor,
  training, trust lists, history (16 entries), lineage, daily caps, cooldowns and last stage. A creature
  saved before companions loads as `EMPTY`, and nothing else in its save changes.
- **On the owner** (`COMPANION_ROSTER` attachment, kept on death): one entry per companion, so the tab works
  while creatures are unloaded or far away. An entry holds species, name, bond, stage, traits, training,
  health, mode, last position, history, lineage, the commands offered and usable, and its abilities. A dead
  companion's entry becomes a memorial ("Remembered"). The roster keeps 32 entries and evicts the oldest
  memorial first.
- **Sync**: any change marks the owner dirty. At the end of the server tick each dirty roster is sent once,
  as `CompanionRosterS2CPayload`, so many changes become one packet. It is also sent on login. The client
  only displays it.

## 9. Integrations

- **Bestiary**: reaching Trusting writes the page at Observed; reaching Bonded writes it at Studied. Mastery
  still lands Known through the bond layer.
- **Magizoologist**: activities `companion_trained` (100 xp, 200 a day) and `companion_mastered` (200 xp,
  400 a day). Contracts: `field_partner` (Beast Keeper: train one companion, 1479 Knuts + 180 xp) and
  `master_companion` (Senior Magizoologist: master one, 4930 Knuts + 400 xp). The existing magizoologist
  ×1.5 bond bonus applies.
- **Module gating**: all bond gain checks `Module.CREATURES`.

## 10. Adding a species

1. Give it a bond profile (`creature_bonds/<id>.json`) if it has none, and make the entity a `BondableBeast`.
   Generic creatures already are.
2. Add `companions/<id>.json` with `trait_pool`, `commands`, `abilities`, and optionally `can_fight`,
   `favourite`, `breeding` and the stage thresholds.
3. Call `CompanionGoals.register(this, goalSelector, targetSelector)` after the follow goal, if the entity
   class registers its own goals. `GenericBeastEntity` already does.
4. Add lang keys for any new trait or innate ability (`companion.wizards_and_beasts.trait.<id>`,
   `…ability.innate.<id>`).

`CompanionDataTest` fails on:
- a missing bond profile;
- an unknown trait;
- a SEARCH or ASSIST command with no ability to use, or an ability with no command;
- combat orders on a creature that cannot fight;
- a missing lang key.

A new ability kind is a new `CompanionAbility` record plus a `Kind` value, and one case in
`Companions.search` / `assist`. The switch is exhaustive, so the compiler points to each case that is missing.

## 11. Tests

- `CompanionRulesTest` (14): stages, refusals including temperament and Mastered, bond gain under caps and
  personality, training cap, trait rolls and inheritance, protection matrix, codec round trips.
- `CompanionDataTest` (6): the shipped data joined against itself, the bond layer and the lang file.
- `CompanionTests` (GameTests, 10), on live creatures:
  - claim through feeding, and a stranger's food refused;
  - protection by relation;
  - despawn denial;
  - commands and refusals by owner, species, stage and temperament;
  - niffler search fetching items and training;
  - unicorn assist healing, then cooldown;
  - breeding in habitat (lineage, inherited traits) and refused outside it;
  - entity save round trip;
  - roster across relog;
  - one sync packet for three orders in a tick.
- `CreatureWildlifeTests.thestral`: now also checks that a stranger cannot strike an owned thestral, and that
  the owner marking them hostile lets the blow land.

## 12. Not done

- Coat variant inheritance in breeding: juveniles get the species' normal variant roll.
- Real multi-client play. Multiplayer is covered by GameTests with mock players: two to three players,
  relog, packet capture.
- A command wheel or keybind for orders. Orders come from the Beasts tab and the sneak-pat toggle only.
