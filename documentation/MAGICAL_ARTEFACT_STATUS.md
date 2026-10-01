# Wizards & Beasts — Magical Artefact Status

An audit of every artefact in the repository on `dev` (2026-09-29): what each one should let a player do that nothing
else does, whether it does that, and what this pass changed. Only artefacts that exist in the code are listed. The 87
behaviourless canon stubs from the canon item backlog are out of scope; they are catalogue entries, not artefacts.

Companion documents: `SYSTEM_INTERACTION_MAP.md` (how systems connect), `PROGRESSION_MAP.md`.

## Classes

| Class | Meaning |
|---|---|
| **A** | Does its one thing, and nothing else does it |
| **B** | Works, but the defining interaction is missing or replaced by generic stat bonuses |
| **C** | Decorative: registered, textured, and it does nothing (or only what a vanilla item does) |
| **D** | Wrongly implemented: does something the artefact should not, or does not do what it claims |

How each artefact is obtained matters for rarity. "Creative" means it has no recipe and no loot entry, so it can only
be had in creative mode or through commands.

## Iconic artefacts

| Artefact | The one thing | Before this pass | Class now | Obtained |
|---|---|---|---|---|
| Marauder's Map | Information: who is where, on a charted atlas | Real (rebuilt 2026-08-21) | A | Recipe |
| Invisibility Cloak | Stealth: invisibility on a limited Demiguise weave | Real (charges, reweaving) | A | Creative |
| Cloak of Invisibility (Hallow) | Stealth that never runs out and hides everything you carry | Real (never runs out, hides equipment) | A | Creative |
| Deluminator | Light: take lights out and give them back | Real | A | Recipe |
| **Time-Turner** | Going back: "each turn is an hour back" | **D.** Moved the *whole server's* day clock *forward* 15–180 s | **A** (§1) | Creative |
| **Portkey** | A group journey, set in advance | **D.** Stored a destination and never travelled | **A** (§2) | Creative |
| **Resurrection Stone** | The dead walk with you, and the Dementors' cold cannot reach you (*Deathly Hallows* ch. 34) | **B.** Three turns gave Regeneration + Resistance | **A** (§3) | Creative |
| Philosopher's Stone | The Elixir of Life; turning metal to gold | **B, overpowered.** Regeneration II, Absorption III and Resistance for 5 min on a 5 min cooldown (so permanent), and it wiped every effect. The `destroyed` flag is never set. | B (§5.1) | Creative |
| Pensieve | Memory: see what someone else remembers | **B.** A read-only list of your own memories, filed under the Dark Arts | B (§4, §5.2) | Creative |
| Sneakoscope | Deceit detection | Real (rebuilt 2026-08-26) | A | Recipe |
| Two-Way Mirror | Talk to one person anywhere | Real (pairing, calls), but filed under the Dark Arts | A (§4) | Recipe |
| Foe-Glass | Who means you harm, and how near | Real (peer for 2 s, a reading), but filed under the Dark Arts; it counts every player as a foe | A (§4) | Recipe |
| Hermione's beaded bag | Carry more than fits | Real (a menu), but filed under the Dark Arts | A (§4) | Creative |
| Hand of Glory | Light for the holder only | Real (night vision for the holder, blindness for others) | A | Recipe |
| Dark Mark | Brand a follower and summon the marked | Real (2 s brand, summons) | A | Creative |
| Horcruxes (diary, ring, locket, cup, diadem) | A soul held in an object: dread while carried, destroyed only by basilisk venom and similar | Real (aura, corruption, `HorcruxDestruction`, the diary answers back) | A | Creative |
| Marvolo Gaunt's Ring | A Horcrux that is also the Resurrection Stone's setting | Horcrux behaviour real. "Bears the Resurrection Stone" is read from a component nothing ever writes, and the ring and the Stone are unrelated items | B (§5.3) | Creative |
| Basilisk fang | Destroys Horcruxes | Real | A | Basilisk loot |
| Blood-pact vial | A binding pact | Real (the blood-pact system) | A | Creative |
| Remembrall | "It turns red when you've forgotten something" | Flashes foil on a timer | C (§5.4) | Recipe |
| Extendable Ears | Eavesdropping | A plain `Item` | C (§5.4) | Recipe |
| Omnioculars | Replay and slow motion of a match | A vanilla spyglass | C (§5.4) | Recipe |
| Peruvian Instant Darkness Powder, Decoy Detonator | Darkness cloud; a distraction | Thrown entities (`WizardingThrownKind`) | A | Recipe |

## 1. Time-Turner: the wearer goes back, the world does not

**Before.** Releasing the Time-Turner called `setDayTime(now + 15..180 s)` on the overworld. That moved the clock for
every player on the server, pushed it *forward*, and touched nothing about the wearer.

**Now** (`timeturner/TimeTurnerRules` pure, `TimeTurnerService`, `TimeTurnerItem`):
- Hold it to wind: one turn a second, up to 3, with a chime and the count on the action bar. Let go to go back.
- Each turn is one in-game hour (1000 ticks) back to where you stood then.
- The world keeps everything that happened since. The world clock is untouched.
- It only reaches back through hours it has spent with you. Its trail of your positions (one every second, on a
  non-saved `TIME_TURNER_TRAIL` attachment) is kept only while you carry it. It is forgotten when you set it down,
  log out or die.
- A trip spends the hours it passed through, so the next trip has to wait until you have lived them again. That is
  the whole cooldown: you cannot be in the same hour twice.
- It refuses:
  - while a mob is hunting you (it is not an escape hatch);
  - if that hour was spent in another dimension;
  - if something now stands where you stood.
- **Rarity.** It is creative-only, and it reaches back at most three hours of your own position. There is no world
  rollback, no undoing damage, and no duplicating anything.

## 2. Portkey: one journey, for everyone holding on

**Before.** Sneak-using on a block stored a position. Nothing ever read it.

**Now** (`portkey/PortkeyService`, `PortkeyItem`):
- Sneak-use on a block to set it. The dimension is recorded too (a new `PORTKEY_DIMENSION` component).
- Hold it for 3 seconds, the tug. You and every player within 1.5 blocks go to the top of that block together.
- The Portkey is then spent (consumed): one Portkey, one journey.
- It refuses, without being spent:
  - if it was set in another dimension;
  - if there is no room at the destination.

**What only it does.** It moves a *group* to a place chosen in advance. Apparition moves one wizard (and a passenger)
to wherever they can picture, and the Floo needs a grate at both ends.

## 3. Resurrection Stone: company through the cold

**Before.** Three turns gave Regeneration and Resistance for 2 min, plus mental stability +15 and corruption +3. The
buffs were a potion any brewer could make.

**Now** (`item/hallow/ShadesOfTheDead`):
- Three turns call the shades for 2 min (`SHADES_UNTIL`, saved).
- While they walk with you, `DementorAura.canFeedOn` is false for you. That is the one check behind the chill,
  sensing, pursuit and the Kiss, so no Dementor can feel you.
- They protect no one else, and they drive nothing off: that is still the Patronus.
- Let go of the Stone (drop it or put it in a chest) and they leave, as they did when Harry let it fall.
- The mental-stability comfort and the corruption cost are unchanged.

## 4. Non-Dark artefacts were filed under the Dark Arts

**Before.** The Pensieve, Two-Way Mirror, Hermione's beaded bag and Foe-Glass were in the `DARK_ARTS` module (item
tag, recipe conditions and item-use gates). Switching the Dark Arts off removed a memory basin, a mirror, a handbag and
an Auror's Dark Detector.

**Now.** They are in `ARTEFACTS`, which the `Module` enum defines as "portable magical gear that is not a wand, broom
or Dark artefact". The change covers:
- `ModItemTagsProvider` and the generated module tags;
- the Foe-Glass and mirror recipes;
- the four items' own gates.

The Hand of Glory, Horcruxes, Dark Mark, the Stones and the blood-pact vial stay Dark.

**This changes what a default world has.** `DARK_ARTS` ships DISABLED and `ARTEFACTS` ships ENABLED, so these four
are now usable in a default world, and the mirror and Foe-Glass recipes appear. They had been hidden only because they
were filed in the wrong module, and all four already work. A server that wants them gone can turn `ARTEFACTS` off, or
the owner can revert this classification.

## 5. Open — owner decisions

1. **Philosopher's Stone.** Today it is a permanent Regeneration II / Absorption III / Resistance aura that also wipes
   every effect. That is exactly the "overpowered universal tool" to avoid; it is safe only because it is
   creative-only. Canon gives it two things: the Elixir of Life and gold. Neither has a home yet:
   - there is no ageing or lifespan system for the Elixir to act on;
   - gold has no link to wizarding money (Galleons come from goblins).

   **Recommendation:** the Elixir as a slow draw (one flask per in-game day) whose one effect is life-sustaining, not
   a buff stack. Decide first whether survival players can ever get the Stone.
2. **Pensieve.** Memories matter only to the Patronus, and `MemoryType.PAINFUL` is declared and never written. Its
   canon use is letting *someone else* see a memory (Snape's to Harry). That needs memories as shareable objects:
   either a memory vial item or a Pensieve block. That is new content, so it is not done here.
3. **Gaunt's ring ↔ Resurrection Stone.** `RING_STONE_PRESENT` is read and never written. Nothing connects the ring to
   the Stone, so either prising the Stone out of the ring becomes how you obtain it, or the line is deleted.
4. **Remembrall, Extendable Ears, Omnioculars** are craftable and decorative or duplicate vanilla. Each has a real
   canon use (a reminder, eavesdropping, replay), but each needs a system the mod does not have. Either leave them
   honestly decorative (the tooltip should not promise more) or remove their recipes.
5. **Foe-Glass "foes"** are every hostile mob and every player. There is no notion of who actually means you harm (a
   Ministry case against you, a player who attacked you recently). This fits better with a future hostility record
   than with a guess now.
6. **Rarity.** Every Hallow, Stone, the Time-Turner, Portkey, Pensieve and the beaded bag are creative-only. That keeps
   them rare but out of survival play. Where they should turn up (the Ministry, loot, rituals) is a content decision.

## 6. Validation

- `./gradlew build` green, including the new `TimeTurnerRulesTest`: turns, an hour back to the latest place, never
  beyond the carried trail, and a trail long enough for the longest trip.
- `./gradlew runGameTestServer`: all 164 required tests pass. The four new ones are in `ArtefactTests`:
  - the Time-Turner goes back, the world clock stays put, the same hour cannot be relived, and the trail is forgotten
    when the Time-Turner is set down;
  - the Portkey carries the holder and the friend touching them but not a stranger 6 blocks off, and is spent;
  - the Stone's shades hide a survival player from Dementors and leave with the Stone;
  - the four non-Dark artefacts are `ARTEFACTS`, and the Hand of Glory is still Dark.
- Mutation check: three mutations were applied together and restored afterwards. Each broke exactly its own test:
  - the trail kept after a trip: "the same hour was relived twice";
  - the Portkey not spent: "the Portkey was not spent by its journey";
  - the shades clause removed from `DementorAura`: "a Dementor could still feel someone walking with the dead".

## Update 2026-09-30 (canon rulings)

- **Philosopher's Stone.** It is now the Elixir of Life: one draught per in-game day spares the drinker's next death
  that day. The buff stack and cleanse are gone. §5 item 1 is resolved (`CANON_AUDIT.md` C-9).
- **Hand of Glory.** The holder sees through darkness; nobody is blinded (C-7).
- **Remembrall.** No longer flashes; it is honestly decorative (C-10). §5 item 4 remains for the other two
  decorative items.
- **Wands from Ollivander.** The first is free, later ones cost seven Galleons (C-8).
