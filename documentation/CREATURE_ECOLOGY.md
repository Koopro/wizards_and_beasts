# Creature Ecology — the data-driven behaviour framework

Written 2026-10-06 from the code it describes; daily life (needs, rhythm, weather, personality, commitment, noise,
activity) added 2026-11-05/06 (`tasks/todo_creature_ai_2026_11_05.md`). Companion documents: `CREATURE_QUALITY_MATRIX.md` (where each
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

Only `capabilities` is required. Optional since 2026-11-05:

```json
{
  "extends": "grazer_herd",
  "without": ["diurnal"],
  "needs": { "hunger": 24000, "thirst": 12000, "social": 8000, "safety": 1200 },
  "personality": { "boldness": [0.1, 0.4] },
  "personal_space": 12,
  "hunt_style": "stalk",
  "forage_blocks": "#minecraft:leaves",
  "food_items": "#minecraft:meat"
}
```

- **`extends`** names an archetype in `creature_ecology/_archetypes/` (13: `grazer_herd`, `winged_grazer`,
  `solitary_predator`, `pack_predator`, `aquatic_ambusher`, `aquatic_drifter`, `small_flier`, `household_pest`,
  `pet_creature`, `dark_creature`, `great_beast`, `fire_creature`, `magical_being`). The file's own fields win;
  `capabilities` add to the archetype's (`without` removes some); `needs` and `personality` merge key by key.
  `EcologyProfileFiles` resolves this for the reload listener and the data test alike.
- **`needs`**: ticks from satisfied to desperate, per need (section 9). A creature without the block has no needs.
- **`personality`**: ranges per axis, overriding what the capabilities imply (section 10).

All 106 roster creatures have a profile (`EcologyProfileDataTest.everyCreatureOnTheRosterHasAnEcology`).
Contradictions are refused at load (`human-avoidant` with `human-friendly`, `aggressive` with `cowardly`,
`aggressive` without `territorial` or `predator`, two rhythms, `solitary` with `herd`/`social`, `cold-adapted` with
`warmth-loving`, thirst on an aquatic creature, hunger with nothing to eat, a diet without hunger, a social /
curiosity / magic need on a creature without `herd`/`social`, `curious`, `magical-sense`). Vanilla animals take part through
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
| `diurnal` | driven | sleeps at night (wherever it is; a kept creature keeps its person's hours) |
| `crepuscular` | driven | sleeps through midday (4000-9500) and the dead of night (16000-21000) |
| `solitary` | driven | moves off when its own kind comes within `personal_space` |
| `grazer`, `browser`, `insectivore` | driven | hungry, eats grass/ferns/flowers (standing on a grass block, or in short grass), leaves (standing beside), bark (beside a trunk); `forage_blocks` replaces the default |
| `weather-shy` | driven | under open sky in rain or thunder, finds cover and stays under it until it passes |
| `cold-adapted` | driven | in a hot biome by day (base temperature above 1), seeks shade |
| `warmth-loving` | driven | in a cold biome (below 0.3) or in rain, seeks cover |
| `grooming` | driven | calm, fed and watered, grooms for a few seconds; not again for two minutes |
| `aquatic`, `flying`, `burrowing`, `climbing` | descriptive | how the body moves; carried by the entity class. `EcologyProfileDataTest` holds `aquatic`/`flying` to the creature's locomotion |

## 4. The brain

`EcologyBrain.decide(profile, perception)` is pure. The order is the policy:

0. a hazard (burning, in lava, fire, on magma, in powder snow) for a creature not made for it;
1. a reaction still in force (section 5);
2. survival: a threat in range, badly hurt, a shunned or untrusted person too close;
3. ground: an intruder on its territory, warned then defended;
4. food: a hungry predator with prey in reach, outside its sleeping hours (a stalker creeps until 8 blocks);
5. weather: a weather-shy creature in rain or thunder, timid prey in a storm, a creature out of its climate;
6. the day: its sleeping hours by rhythm (a nocturnal creature seeks cover, then rests; diurnal and crepuscular
   sleep), or worn out;
7. needs by utility: the most pressing of thirst, hunger and loneliness it can answer now (water, food, kin seen);
8. kin (kept off if solitary, rejoined if herd), home, things on the ground, remains, then people it is curious about
   or fond of;
9. calm and sated, it grooms.

**Holding a decision** (`EcologyCommitment`, pure). The brain's choice is a candidate. Each intent has a band (IDLE,
ROUTINE, ALERT, DEFENCE, EMERGENCY), a minimum and maximum hold, an end cooldown and a fail cooldown (`EcologyIntent`).
A higher band interrupts at once; inside its minimum, an intent is kept; a same-band candidate must be more pressing
by 0.15; nothing is held past its maximum; a cooling candidate counts as nothing. `EcologyBrain.sustains` says whether
the current intent still makes sense, with looser thresholds than starting it (a predator is fled until it is out of
sense range, not just out of flee range; a meal goes on until hunger is nearly gone): the hysteresis.
`EcologyCommitmentTest.aThreatAtTheEdgeOfRangeDoesNotMakeTheCreatureFlicker`: the bare policy flips 40 times in 40
looks, the committed creature a handful.

**Failure recovery.** Every move toward something watches its progress: no ground gained for 100 ticks (a chase:
200) fails the intent. It is dropped, cooled down (doubling per repeat, eight times at most), and the place is
forgotten. A target that dies, leaves or is taken in by someone ends the intent at once. Ridden or carried, a
creature has no ecology.

`EcologyGoal` carries the decision out; one goal per creature, registered once and reading the profile live (so a
`/reload` reaches creatures already in the world). A bespoke creature passes the intents it already does better as
`skipped` — the Niffler's own treasure goals, the Mooncalf's burrow, the Bowtruckle's tree restriction and melee.

**Performance contract.** A creature looks around (`EcologySensing.perceive`) once every 20 ticks, staggered by
entity id, with **one** bounded entity query (`sense_radius`, at most 32) classifying kin, threats, prey, people and
items in a single pass, plus a few reads at its own position (sky, biome, the blocks at its feet). Water and food are
found by `EcologyResources` on the SLOW tier, only while a need is close to acting and nothing is known: a jittered
grid of 49 columns every 4 blocks out to 12, nearest first, 8 rows each (at most 392 block reads, never into an
unloaded chunk), remembered 2 minutes, backed off 5 s to 60 s on a miss; anything four blocks across is never missed.
Tree-dwellers still scan once per life for their trunk. Between looks the goal
only steers. Stimuli are raised per event with one bounded query each. `ecology_looks_around_only_every_second`
pins the cadence (three looks in sixty ticks).

## 5. Stimuli

| Stimulus | Raised from | Default answer when the creature declares none |
|---|---|---|
| `SPELL` | a successful cast (`SpellCastService`) | magical-sense: curious → investigate; prey/cowardly/avoidant → flee; else alert |
| `CREATURE_DEATH` | `LivingDeathEvent` | herd or prey → flee (kin, and fellow prey); social predator → alert |
| `DAMAGE` | `LivingDamageEvent.Post` | cowardly, or non-aggressive prey → flee; social kin answer (§3) |

| `NOISE` | `VanillaGameEvent`: explosion, lightning, horn (loud, 32); projectile landing, note block, fuse, a block broken by a person (medium, 10); a block placed by a person (soft, 6) | loud: prey/cowardly/avoidant flee, territorial bristle, curious investigate; medium: the timid flee, the curious investigate; soft: the curious investigate. A sleeper sleeps through soft noise and distant medium noise |
| `PLAYER` | a sprinting player's step (medium) | the avoidant and the timid flee, the territorial bristle; the curious ignore it (they only come to quiet people) |

An answer is held for 100 ticks; a flight is not overwritten by a weaker answer. Noise is rate-limited per chunk (one
of each loudness per 20 ticks) before its one query; block changes count only when a person makes them, so a grazing
herd does not spook itself. A death writes a slot in `CarrionLog` (a 64-entry ring per level, no query); scavengers
read it during their own look. `LIGHT`, `DARKNESS` and `ENVIRONMENT` stay unraised; fire is a hazard in the perception
instead of a stimulus.

## 6. Field notes — behaviour becomes bestiary knowledge

When a creature's intent changes, every player within 16 blocks with line of sight writes the capability behind it
into that creature's page (`PlayerBestiaryData.fieldNotes`, saved, synced, shown as "Seen: …" lines). The first time,
the action bar says so ("Field note — Mooncalf: Sleeps by day, out of the sun. Approach quietly and it may not wake.").
So the bestiary tells a player how to approach a creature because they watched it — come back at night, keep off a
Hippogriff's herd, don't cast near a unicorn.

## 7. Testing a behaviour on its own

- **Unit — `EcologyScenario`** (test tree): one decision, no world.
  `creature(PREY, HERD).sees(predatorAt(6)).decides(FLEE_PREDATOR)`. `EcologyBrainTest` is the policy table.
- **Data — `EcologyProfileDataTest`**: every file parses (archetypes resolved), names a real creature, agrees with
  its locomotion, a predator or aggressive creature has attack damage to act with, every roster creature has a
  profile, every archetype is used, and every hunger can be answered. `EcologyProfileFilesTest` covers `extends`.
- **Unit — `EcologyBrainLifeTest`, `EcologyCommitmentTest`, `EcologyNeedsTest`, `EcologyPersonalityTest`,
  `EcologyResourcesTest`** (fake terrain through a `Probe`), `CreatureNoiseTest`, `CreatureActivityTest`.
- **World — `CreatureLifeTests`**: drinks at a pond, grazes and shows EAT, gives up on unreachable water, sleeps by
  rhythm, shelters from real rain and stays under cover, flees fire, keeps distance, an explosion scatters and draws
  and is rate-limited, a running person is heard, a scavenger finds a kill, home and needs survive a save, and forty
  creatures stay within the look and search budget. Each scenario overrides its own species' profile
  (`EcologyProfileRegistry.override`, a layer) and undoes everything through `TestCleanup`, even when it fails.
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

## 9. Needs

`EcologyNeed`: hunger, thirst, energy (tiredness), social (loneliness), curiosity (boredom), safety (alarm), magic.
Each is a pressure from 0 to 1, only where the profile declares it, advanced by elapsed time at each look (no
per-tick cost; no time is charged across an unloaded span). Acted on from 0.55 (a kept creature from 0.8), carried on
to 0.1. Rest and company recover four times faster than tiredness and loneliness build. A fright (hurt, a threat in
flee range, a stimulus it flees) sets safety to 1, which widens the flee radius by a quarter and puts curiosity off
until it fades. A spell nearby feeds a magic need. A new creature starts each need somewhere in 0-0.4, from its UUID,
so a herd does not all get thirsty in the same minute. Needs never show as bars: the brain, the field notes and the
admin creature report read them.

## 10. Personality

Five axes (boldness, curiosity, sociability, aggression, activity), each drawn from the creature's UUID inside its
species' range: prey timid, herds sociable, solitary creatures not, aggressive creatures bold and quick to anger;
`personality` in the profile overrides. Same creature, same character, nothing saved. An axis only scales a threshold
of a behaviour the species already has, within 0.8-1.2: how far off a threat is fled, how close an intruder gets before
the charge, how fast loneliness and boredom build. A neutral creature follows the plain policy.
`EcologyPersonalityTest` holds the corners: no temperament stops prey fleeing a predator at its side, stops an
aggressive creature defending, or gives a species a behaviour it does not have.

## 11. Showing it: activity

`CreatureActivity` is one synced byte on `GenericBeastEntity` (`HasActivity` for bespoke classes). The goal writes it
from the intent: FLEE, RUN, SNEAK, THREATEN, REST, SLEEP, EAT and DRINK (once there), GROOM, ALERT. The movement
controller reads it (`AnimHelper.activityController`). Each activity names its best clips and a stand-in: with no
`sleep` clip the rig plays idle at a third of the speed, with no `run` the walk half again as fast, and eating loops
`graze`. The client asks GeckoLib's baked animations which clips the rig has (`RigClips`), because GeckoLib throws on
an unknown clip. So every existing rig shows the difference today, and a new clip lights up with no code change.

## 12. Not done

- The Dementor declares an ecology but runs no `EcologyGoal` (it is a `Monster` with its own dread systems).
- `burrowing` and `climbing` are descriptive: no generic dig-in or wall-climb navigation.
- Stimuli `LIGHT`/`DARKNESS`/`ENVIRONMENT` are not raised.
- Saved: home, needs, fed-until. Not saved: the decision and cooldowns (it looks again within a second of loading).
- Water is found near the surface only (rows from 3 above to 4 below the creature); food blocks are vanilla tags or a
  profile's `forage_blocks`; a meal changes the world only for short grass and ferns, and only where mobs may grief.
- Activity clips: none are authored yet; every activity runs on its stand-in until BR-5 adds `sleep`, `run`, `eat`.
- Not seen in a client yet: the activity loops (sleeping idle, grazing loop) on a dedicated server.
- No in-client pass of the field-note page lines.
