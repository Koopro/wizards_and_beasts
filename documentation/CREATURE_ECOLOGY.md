# Creature Ecology — the data-driven behaviour framework

Written 2026-10-06 from the code it describes. Companion documents: `CREATURE_QUALITY_MATRIX.md` (where each
creature stands), `ENTITY_DESIGN_BIBLE.md` (the per-creature "Ecology" lines this framework makes real),
`CREATURE_IDENTITY_STATUS.md` (each species' signature behaviour).

## 1. What it adds, and what it reuses

The roster already had a per-species layer: traits, temperament, datapack abilities (`creature/ability`), idle,
sound and combat profiles, bond profiles, bestiary tiers and signatures, wildlife trust (`WildlifeWorld`). What it did
not have was **ecology** — creatures relating to each other and to the world: a unicorn did not know an acromantula
was a threat, a herd did not keep together, a nocturnal creature was awake at noon, and the `CreatureReaction`
vocabulary was declared on creatures but **raised by nothing** (`GenericBeastEntity.reactionTo` had no caller).

This framework is that layer. It reuses rather than replaces:

| Reused | How |
|---|---|
| `CreatureReaction` stimuli/responses | now raised (§5); a creature's declared reaction wins over its ecology's implied one |
| `WildlifeWorld.letsNear` / `shuns` | the "trusted" and "shunned" person in every perception — one rule for wary, corruption-sensitive and curious creatures |
| `BondableBeast` | a bonded creature keeps no territory or herd and does not avoid people |
| Bestiary entries and sync | behaviour seen becomes a **field note** on the page (§6) |
| Each creature's navigation | a flier flies away, a swimmer swims away |
| `creature_bonds/` precedent | one data file per creature id, so bespoke Java creatures and datapack ones share one source |

## 2. Data

`data/wizards_and_beasts/creature_ecology/<creature id>.json`:

```json
{
  "capabilities": ["prey", "herd", "human-avoidant", "magical-corruption-sensitive", "magical-sense"],
  "sense_radius": 24,
  "herd_radius": 12,
  "territory_radius": 16,
  "fed_ticks": 6000
}
```

Only `capabilities` is required. Contradictions are refused at load (`human-avoidant` with `human-friendly`,
`aggressive` with `cowardly`, `aggressive` without `territorial` or `predator`). Vanilla animals take part through
two entity tags: `wizards_and_beasts:ecology_prey` (sheep, cows, chickens, fish…) and
`wizards_and_beasts:ecology_predators` (wolves, foxes, cats…); `wizards_and_beasts:dementors` is what a
fear-sensitive creature dreads.

## 3. Capabilities

| Capability | Kind | In play |
|---|---|---|
| `predator` | driven | hunts declared prey and the prey tag, never larger than itself, never a bonded or tamed animal; fed for `fed_ticks` after **any** kill of its prey |
| `prey` | driven | flees predators (and the predator tag) inside ¾ of its sense radius |
| `herd` | driven | regroups when the centre of its kin is beyond `herd_radius` |
| `territorial` | driven | warns a stranger within `territory_radius` (stops, glares, calls); defends inside half of it |
| `nocturnal` | driven | by day finds cover, then rests; asleep, it notices only a person who walks right up — never a sneaking one |
| `tree-dwelling` | driven | home is the nearest log where it was found; returns when it strays |
| `cowardly` | driven | below 35% health with something near, it breaks off and runs |
| `aggressive` | driven | answers an intruder or prey with force, no warning |
| `curious` | driven | approaches a quiet (sneaking or trusted) person; investigates spells it notices |
| `social` | driven | when one is hurt, its kin nearby answer (attack if territorial/aggressive, flee if prey/cowardly, else alert) |
| `scavenger` | driven | goes to meat and fish on the ground and eats one |
| `treasure-seeker` | driven | goes to Niffler-tagged treasure on the ground |
| `human-avoidant` | driven | keeps untrusted people beyond 10 blocks (`WildlifeRules.letsNear` decides trust) |
| `human-friendly` | driven | walks up to people, unasked |
| `magical-sense` | perceptual | notices spells at 24 blocks instead of 12 |
| `illusion-aware` | perceptual | perceives invisible players |
| `fear-sensitive` | perceptual | treats Dementors as threats |
| `magical-corruption-sensitive` | perceptual | flees the dark-corrupted and slayers from its whole sense radius |
| `aquatic`, `flying`, `burrowing`, `climbing` | descriptive | how the body moves; carried by the entity class. `EcologyProfileDataTest` holds `aquatic`/`flying` to the creature's locomotion |

## 4. The brain

`EcologyBrain.decide(profile, perception)` is pure. The order is the policy:

1. a reaction still in force (§5);
2. survival — a threat in range, badly hurt, a shunned or untrusted person too close;
3. ground — an intruder on its territory, warned then defended;
4. food — a hungry predator with prey in reach (a nocturnal one only at night);
5. the day — a nocturnal creature seeks cover, then rests;
6. kin, home, things on the ground, then people it is curious about or fond of.

`EcologyGoal` carries the decision out; one goal per creature, registered once and reading the profile live (so a
`/reload` reaches creatures already in the world). A bespoke creature passes the intents it already does better as
`skipped` — the Niffler's own treasure goals, the Mooncalf's burrow, the Bowtruckle's tree restriction and melee.

**Performance contract.** A creature looks around (`EcologySensing.perceive`) once every 20 ticks, staggered by
entity id, with **one** bounded entity query (`sense_radius`, ≤ 32) classifying kin, threats, prey, people and items
in a single pass. No block scans, except once per creature life for a tree-dweller's home trunk. Between looks the goal
only steers. Stimuli are raised per event with one bounded query each. `ecology_looks_around_only_every_second`
pins the cadence (three looks in sixty ticks).

## 5. Stimuli

| Stimulus | Raised from | Default answer when the creature declares none |
|---|---|---|
| `SPELL` | a successful cast (`SpellCastService`) | magical-sense: curious → investigate; prey/cowardly/avoidant → flee; else alert |
| `CREATURE_DEATH` | `LivingDeathEvent` | herd or prey → flee (kin, and fellow prey); social predator → alert |
| `DAMAGE` | `LivingDamageEvent.Post` | cowardly, or non-aggressive prey → flee; social kin answer (§3) |

An answer is held for 100 ticks; a flight is not overwritten by a weaker answer. `PLAYER`, `LIGHT`, `DARKNESS`,
`FIRE` and `ENVIRONMENT` stay unraised: the perception already covers people and daylight, and the others have no
rationale on a shipped creature yet (`ENTITY_DESIGN_BIBLE.md`, Reaction vocabulary).

## 6. Field notes — behaviour becomes bestiary knowledge

When a creature's intent changes, every player within 16 blocks with line of sight writes the capability behind it
into that creature's page (`PlayerBestiaryData.fieldNotes`, saved, synced, shown as "Seen: …" lines). The first time,
the action bar says so ("Field note — Mooncalf: Sleeps by day, out of the sun. Approach quietly and it may not wake.").
So the bestiary tells a player how to approach a creature because they watched it — come back at night, keep off a
Hippogriff's herd, don't cast near a unicorn.

## 7. Testing a behaviour on its own

- **Unit — `EcologyScenario`** (test tree): one decision, no world.
  `creature(PREY, HERD).sees(predatorAt(6)).decides(FLEE_PREDATOR)`. `EcologyBrainTest` is the policy table.
- **Data — `EcologyProfileDataTest`**: every file parses, names a real creature, agrees with its locomotion, and a
  predator or aggressive creature has attack damage to act with.
- **World — `CreatureArena`** (game tests): spawns creatures frozen on their own lane, takes one look on demand
  (`arena.think(mob)`) and asserts (`arena.expect(mob, intent)`). `EcologyTests` runs each scenario alone, because a
  neighbouring scenario's acromantula is a predator. Each scenario was mutation-checked: disabling the mechanism makes
  it fail.

## 8. Adding a creature

1. Write `creature_ecology/<id>.json`.
2. Datapack creature: nothing else — `GenericBeastEntity` runs `EcologyGoal`.
   Bespoke class: add `new EcologyGoal(this, Set.of(<intents it does itself>))` to `registerGoals`.
3. Add a scenario to `EcologyTests` for anything the creature does that others do not.
4. Re-run `python tools/creature_quality_matrix.py`.

## 9. Not done

- The Dementor declares an ecology but runs no `EcologyGoal` (it is a `Monster` with its own dread systems).
- `burrowing` and `climbing` are descriptive: no generic dig-in or wall-climb navigation.
- Stimuli `PLAYER`/`LIGHT`/`DARKNESS`/`FIRE`/`ENVIRONMENT` are not raised.
- Ecology state is not saved: after a reload, home is where the creature stands and hunger is forgotten.
- No in-client pass of the field-note page lines.
