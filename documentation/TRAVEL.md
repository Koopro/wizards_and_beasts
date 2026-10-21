# Travel as progression

2026-10-14. Brooms, Apparition and the Floo already worked as transport; this pass makes them something a player
gets better at, and gives flying work to do. Plan and review: `tasks/todo.md` (archived after the pass).

## 1. Audit — what already shipped (unchanged by this pass)

| System | Already there |
|---|---|
| Brooms | 8 datapack definitions; separate authored speed / acceleration / braking / turning / banking / stability / boost (multiplier, length, cooldown) / drift / momentum / wobble / crash / wear; four handling profiles (school, balanced, racing, tank); broom shop with tier-gated stock; polish and servicing; Ministry broom licence for racing tiers; `BroomSpeedGuard` |
| Apparition | deterministic splinch ladder (no dice), licence test from practice, memorised points (only where you stood), wards, side-along, Trace offence, per-tier cooldowns, server-chosen destinations by index |
| Floo | powder → light → speak → window ritual, ownership, private hearths with non-confirming refusals, seal, cooldown, misfires with a cause |
| Portkey | one group journey to a fixed place |

## 2. What this pass added

### Brooms: fittings, wear, customization (`broom/fitting/`)
- **Fittings** — six items, one per canon idea: Cushioning Charm, Braking Charm, Streamlined Tail Twigs, Balanced
  Tail, Reinforced Binding, Anti-Jinx Varnish. Each moves its characteristic and **costs something else**
  (`BroomFittingTest.everyFittingIsATrade`). One per slot (charm, twigs, binding, varnish); room by tier: school 1,
  standard 2, racing 3, elite/legendary 4. Some need a better broom (racing varnish will not go on a school broom).
- **Fit** by using the item with a broom in the other hand or on a parked broom. **Strip** with the Broomstick
  Servicing Kit (16 uses; it was a picture with no behaviour). Replacing a fitting hands the old one back.
- **One read point.** `BroomEntity.resolveDefinition()` now returns the *flown* broom: the published definition ×
  fittings × wear (`BroomTune`). Movement, handling profiles, impacts, FX and `BroomSpeedGuard` all read it, so the
  rider's client and the server agree on a fitted broom by construction. Fittings are synced entity data.
- **Wear** — under half durability a broom is *worn* (wanders, leans); under a fifth *battered* (also slower). It never
  raises crash damage. Polish and servicing restore it.
- **Customization** — dye a parked broom: its slipstream is drawn in that colour (`dust_color_transition`). Slot
  swaps were not done: per-broom geo files carry only their own slot bones.
- **Where to get them** — crafted, sold at the broom shop by tier, and handed out as broom-shop and Quidditch ranks.

### Flying work (`travel/`)
- **Courier** (`courier_dispatch` service at the apothecary, Gringotts, Ollivanders, the Ministry and the broom shop):
  a sealed parcel for any keeper of another place ≥ 96 blocks away. A day to deliver, eight minutes for express;
  postage by distance (20–240 Knuts, ×1.5 express). Any way of getting there is legal.
- **Time trials** (`time_trial` service at the broom shop): eight hoops in the sky around the counter, flown in order
  and back through the first. Par is the loop at 0.62 blocks/tick — a Cleansweep misses it at cruise, a Comet makes
  it — so the broom is the progression. Hoops are points, not blocks: nothing is built, nothing is left behind.
- **Aerial survey**: each new 64-block square overflown ≥ 24 blocks up on a broom counts, with the biome as subject.
  **Treasure sighted**: ruins of a treasure site passing below (±32 blocks) are spotted once.
- **Creature encounters**: on a broom, a creature off the ground can be sighted and watched from twice as far
  (`EncounterRule.AERIAL_RANGE_SCALE`). The bestiary's watching rules are otherwise unchanged.
- All of these are `ActivityType`s on the one activity stream, so location standing and career contracts count them
  without a new contract kind. **Quidditch Player** got its career track (`career_tracks/quidditch_player.json`):
  reserve → squad flyer → first team → professional, rewards fittings and finally a Nimbus 2001.

### Apparition
- Memorise / forget / sit the test from the **Travel tab** as well as by command (`ApparitionPointService` is the one
  door for both). Memorising is still only where you stand: no payload carries a position.
- **The Ministry examines in person** when its location is open: the test is the Ministry's `apparition_test` counter
  service for 12 Galleons. With the Ministry off (it ships off) the test is sat anywhere, free — fail-open, so a
  licence is never out of reach.
- **Risk readout** — while holding, the action bar shows what is working against the jump (moving, a full pack,
  hunger, water, a passenger, no licence) and how many ticks of slack the release has left, computed on the server
  from the same `Destabilization` the release is judged on. Failure was already deterministic; now it is visible.

### Floo
- **Discovery** — the picker lists the player's **address book**: hearths they have reached, stood at (opened the
  picker at), own, or that are **landmarks** (public and owned by nobody — the way an admin or the world registers a
  civic hearth). Speaking any public address still works: knowing the name is what the Floo needs, and arriving adds
  it to the book. A misfire can still land you in a grate you never found.
- **Existing worlds** — on first login after the update, a player who had played before gets every public address
  that existed then; a new player starts with an empty book (`TravelRecord.flooSeeded`).
- **Tolls** — a keeper may ask 0–3 Sickles of travellers to their own public hearth (Travel tab ▸ Toll). Paid at the
  commit, before the powder, to the keeper's vault — or held on the network and paid at their next login. Keepers,
  admins, misfired travellers and everyone while Gringotts is off travel free. Shown in the picker and before the
  fire roars.
- **Wards** close hearths: an Apparition ward refuses Floo arrivals too unless saved `"blocksFloo": false`.

### Travel tab
Character Sheet ▸ **Travel**: brooms (licence, condition, fitted stats), Apparition (licence progress, test, memorised
places with Forget, Memorise here), Floo (address book, tolls, toll button on your hearths), flying work (parcel and
deadline, trial state, best times, tallies). Server-built, owner-only, throttled; buttons are offers the server
re-checks.

## 3. Multiplayer and exploit rules

- Every destination is server-side: Apparition by index into the player's own points; Floo by address, re-checked at
  commit (seal, ward, toll, powder, arrival); time-trial hoops are the server's, judged on the server's view of the
  broom.
- Time trials: hoops checked on the swept segment (no skipping between ticks), a jump longer than twice the broom's
  boosted top speed + 1 disqualifies, leaving the broom or the world disqualifies, 60 s between hoops at most.
  `BroomSpeedGuard` already sets down over-speed brooms, at the *fitted* ceiling.
- Courier: one parcel in flight per player, its id on the player's record; only that id delivers, only by its
  courier; a stashed or copied parcel crumbles. Delivery is checked against the counter's distance from the origin.
- Fittings: the item is taken in the same call that writes the component; a refusal keeps it; a replace returns the old one.
- Tolls: charged before powder and refunded if the hop then fails; never charged on a misfire; capped at 3 Sickles.
- Travel tab: one C→S payload (action kind + ≤ 64-char argument), 10-tick throttle, answers only about the sender.

## 4. Not done / known limits

- No Quidditch: no teams, balls, pitches or referee exist to build it on.
- No Floo directory item: speaking an address is the word-of-mouth path.
- Time-trial courses are generated around wherever the counter is; in tight valleys a hoop can sit near a cliff face.
- A keeper can change a toll between a traveller's start and commit (bounded by the 3-Sickle cap).
- Not verified in a running client: the hoop particles, the Travel tab layout at small GUI scales, the dyed trail.

## 5. Tests

Unit: `BroomFittingTest`, `TimeTrialTest`, `CourierAndSurveyTest`, `FlooDiscoveryAndTollTest`,
`ApparitionRiskReadoutTest`, `TravelDataTest`. GameTests (`gametest/TravelTests`): fitted speed is what the guard
polices, the workshop never duplicates, wear wanders, parcels only for their courier and paid once, a trial
finishes and a teleport disqualifies, discovery and seeding, tolls move money once (online and offline keeper),
wards close hearths, tab actions touch only your own.
