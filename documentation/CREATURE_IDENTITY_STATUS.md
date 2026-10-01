# Wizards & Beasts — Creature Identity Status

Where every creature stands on one question: **with its name hidden, could a player tell from its behaviour what
makes this species special?** Written 2026-09-28 on `dev`, after the visual overhaul
(`VISUAL_STYLE_REPORT.md`) settled how creatures look. The Niffler is the benchmark for depth — it wants gold above
all, digs ore out of the ground for it, empties your pockets, can be bonded, carried and kept, breeds, and every one
of those has consequences — but not a template: a species gets the behaviour canon gives it, and nothing else.

**Classes**

| Class | Meaning |
|---|---|
| **A** | Signature behaviour exists, is distinctive, and is covered by a game test. |
| **B** | Partial: a signature ability exists, but it is generic in shape, untested, or the defining canon behaviour is missing. |
| **C** | Mostly generic: stats, temperament and shared combat abilities (enrage, leap, status on hit). |
| **D** | Intentionally simple: canon gives it little to do, and giving it more would be invention. |
| **—** | Not a wild creature in this mod (a player heritage or an NPC). |

How behaviour is added (unchanged by this pass): data-driven creatures take `CreatureAbility` entries in
`data/wizards_and_beasts/creatures/<id>.json` (one record per behaviour, dispatch by `type`); bespoke creatures have
their own entity class where the generic model cannot express them. Pure decisions live in
`creature/wildlife/WildlifeRules` and `SignatureRules` (unit-tested); world reads in `WildlifeWorld`. Witnessing a
signature fills the bestiary page to KNOWN (`BestiaryDiscoveryHandler.witnessedSignature`).

---

## 1. What this pass added

| Creature | Before | After | Where |
|---|---|---|---|
| Augurey | C — generic flyer | **A** — cries only when rain is coming (a true forecast off the server's weather clock); grounded and near its roost in fine weather, flies in rain; shy of anyone upright | `AugureyEntity`, `WildlifeWorld.rainComing` |
| Streeler | C — slow stroller | **A** — colour changes on the hour (day clock + UUID offset, nothing synced); trail kills plants and burns grass to dirt (mobGriefing respected); venom kills Horklumps | `StreelerEntity`, `StreelerRenderer` |
| Cornish Pixie | C — generic flyer | **A** — snatches what you hold, a wand first, and drops it wherever it has flown; three or more hoist a person and let them down slowly; struck, it lets go | `CornishPixieEntity` |
| Blast-Ended Skrewt | C — flung flame "hexes" | **A** — the blast shoves it forward and burns whatever is behind it; crowded Skrewts kill each other; shell turns spells (`spell_resist`). The non-canon ranged hex is gone | `blast_propulsion`, `infighting` |
| Griffin | C — enrage + dive | **A** — claims gold (`#wizards_and_beasts:griffin_hoard`) as its home, warns a stranger once then attacks, tolerates the one person who feeds it raw meat, gives up gold that has gone | `hoard_guard`, `creature_bonds/griffin.json` |
| Centaur | C — ranged hex, pack | **A** — on a clear night stops, looks up and says what the sky shows (always true: danger abroad, full moon, coming rain, or nothing for humans); warns, then turns on, whoever fells trees near it; will not carry a rider | `star_reading`, `forest_keeper`, `CentaurForestWatch` |
| Merpeople | C — water + ranged hex | **A** — song understood only with your head under water, a screech above it; each listener hears their own version | `merfolk_song` |
| Boggart | B — fear aura | **A** — becomes the most frightening creature the person facing it has met (everyone sees that shape), a shadow if they have met none; before two people it flickers and frightens nobody; seeks dark roofed places and stays | `boggart_dread` (rewritten), `Guise`, `GuiseBeastRenderer` |

Shared additions, all small: `SignatureRules` (pure rules + `SignatureRulesTest`), a synced `Guise` on
`GenericBeastEntity` (any shapeshifter; not saved), `GenericBeastEntity.hasAbility/abilityOf/isNaturallyHostile`,
six sound events pointing at vanilla audio through `sounds.json` (swappable for recordings without code), lang.
Nothing was added to the creature framework's shape: six new ability records, one new event listener.

---

## 2. Priority species

Each card: identity · signature behaviour · signature interaction · danger / use · environment · canon constraints ·
status. "Constraints" are what must **not** be invented.

### Hippogriff — A
- **Identity:** proud; respect before everything.
- **Behaviour:** watches, flies on its own schedule; answers a bow with a bow.
- **Interaction:** bow → it bows back → feed → trust → ride (bond 25).
- **Danger / use:** an unbowed stranger who crowds it is warned, then attacked; a trusted one is a mount.
- **Environment:** flies between waypoints, lands; coats vary.
- **Constraints:** never tame without the bow; insulting it is the one thing canon is explicit about.
- **Status:** `HippogriffEntity`, `creature_hippogriff_answers_a_bow`.

### Basilisk — A
- **Identity:** the King of Serpents; its eyes kill.
- **Behaviour:** gaze resolved per player with fresh line of sight; bite venom.
- **Interaction:** counterplay — water or a reflection petrifies instead of kills; blindness, blindfold, averted eyes stop it; phoenix tears cure the venom; a fang destroys a Horcrux; the cockcrow weakens it.
- **Environment:** the Chamber, one per structure; slain record kept.
- **Constraints:** no random surface spawns; the gaze needs eye contact.
- **Status:** `BasiliskEntity`, `creature_basilisk_eyes_fangs_and_venom`.

### Phoenix — A
- **Identity:** immortal by fire; loyal to the one who earns it.
- **Behaviour:** bursts into flame at death, rises from its ashes; travels by flame.
- **Interaction:** tears heal and cure; song steadies its person; defends whoever defends it (bond).
- **Danger / use:** never known to kill — strikes capped, blinds instead.
- **Environment:** high stony peaks and savanna plateaus.
- **Constraints:** no kill drops (feathers are given, not taken); cannot be caged into obedience.
- **Status:** `PhoenixEntity` + rebirth/tears/song classes, `creature_phoenix_rises_from_its_ashes`.

### Thestral — A
- **Identity:** seen only by those who have seen death.
- **Behaviour:** invisible per viewer until that viewer has witnessed a person's death.
- **Interaction:** anyone may feed (raw meat) and ride it, seen or not; tail hair at bond 60.
- **Danger / use:** a flying mount; a wand core.
- **Environment:** forests; drawn by blood.
- **Constraints:** not an omen of death; not evil; physical to everyone.
- **Status:** `ThestralEntity`, `creature_thestral_is_real_to_everyone`.

### Dementor — A
- **Identity:** despair made flesh.
- **Behaviour:** chill by distance band, swarm deepens it once; drains, then the Kiss.
- **Interaction:** Patronus repels by strength; blows do nothing; chocolate eases the after-effect.
- **Danger / use:** the Kiss cannot be cured.
- **Environment:** Azkaban, despair-driven arrivals.
- **Constraints:** cannot be killed by ordinary means; invisible to Muggles.
- **Status:** `DementorEntity`, `creature_dementor_chills_by_distance_and_flees_the_light`.

### Boggart — A (this pass)
- **Identity:** a shapeshifter with no shape of its own.
- **Behaviour:** unseen until faced; becomes the worst thing the person facing it has met; confused by a crowd; keeps to dark, roofed places.
- **Interaction:** face it with a friend; Riddikulus finishes it.
- **Danger / use:** fear (darkness, slowness, weakness, nausea) — never a physical kill.
- **Environment:** light drives it to find a cupboard-like spot and stay there.
- **Constraints:** nobody knows its true form (it is a shadow when it has nothing to become); it does not read minds beyond what you have met — the shape comes from your bestiary.
- **Status:** `boggart_dread`, `creature_boggart_becomes_your_fear`. **Gaps:** shapes are limited to creatures with a rig (no Snape, no full moon); fears that are not creatures cannot be shown.

### Unicorn — A
- **Identity:** purity; lets only the right person near.
- **Behaviour:** wary of strangers, shuns the corrupted and unicorn-killers from further off; sheds hair.
- **Interaction:** a quiet watcher may groom it (hair); killing one curses the killer.
- **Danger / use:** hair and horn from the living; blood is a curse.
- **Environment:** forests.
- **Constraints:** nothing from a kill; no riding.
- **Status:** `wary`, `groomable`, `shed`, `slayer_curse`; `creature_unicorn_lets_only_the_right_person_near`. **Gap:** foals (golden → silver) not modelled.

### Centaur — A (this pass)
- **Identity:** proud star-readers of the forest.
- **Behaviour:** on clear nights, reads the sky aloud — truthfully.
- **Interaction:** listen (the reading is a warning worth having); ask to ride and be refused.
- **Danger / use:** felling their forest earns one warning, then the herd (bow = ranged attack).
- **Environment:** forest; the reading needs open sky and no rain.
- **Constraints:** not tame, not mounts, not servants of wizards; they do not explain prophecy to humans (readings are terse).
- **Status:** `star_reading`, `forest_keeper`; `creature_centaur_reads_the_sky_and_keeps_the_forest`. **Gap:** their attack is still the shared `ranged_hex` projectile, not arrows.

### Merpeople — A (this pass)
- **Identity:** lake folk whose song only water carries.
- **Behaviour:** sing periodically.
- **Interaction:** put your head under (Gillyweed, the heritage's breath) to understand it; above water it is a screech.
- **Danger / use:** neutral; ranged attack when provoked.
- **Environment:** water-bound, dry out on land.
- **Constraints:** no invented Mermish vocabulary; verses are written for the mod, not quoted.
- **Status:** `merfolk_song`; `creature_merfolk_song_heard_only_under_water`. **Gap:** no villages or territory behaviour yet.

### Werewolf — A
- **Identity:** a human under a curse.
- **Behaviour:** abroad only at the full moon, leaves at dawn; pack; howls.
- **Interaction:** its bite under a full moon curses a human (config), keeping who they are.
- **Danger / use:** the most dangerous night in the mod.
- **Environment:** moon-bound.
- **Constraints:** no werewolf material exists in canon — no loot invented for it.
- **Status:** `moon_bound`, `lycanthropic_bite`; `creature_werewolf_bite_carries_the_curse`.

### Vampire — —
A player heritage (`heritage/`, blood meter, bat form), not a wild creature; its identity lives in
`VampireBloodTests`. No vampire mob is added by this pass: canon vampires are people.

### Veela — —
A player heritage (veela-harpy form). No mob: canon veela are people.

### Goblin — —
`GoblinTellerEntity`, Gringotts staff in four roles; the vault screen is its interaction. An NPC, not wildlife.

### Occamy — B
- **Identity:** choranaptyxic — fills the space it has.
- **Behaviour:** grows to fill room, shrinks when confined (`occamy_choranaptyxis`, physical: hitbox and model).
- **Interaction / use:** eggshells of pure silver (currently a kill drop).
- **Constraints:** eggshells should come from nests, not kills.
- **Gap:** nest guarding and laid eggs — canon's other half of the Occamy — not built.

### Bowtruckle — A
- Tree guardian; camouflages against bark; defends its home tree; bonded, picks iron locks for its person (never a Colloportus seal); wandwood. `creature_bowtruckle_*`.

### Cornish Pixie — A (this pass)
- **Identity:** electric-blue mischief.
- **Behaviour:** pesters anyone holding something.
- **Interaction:** it snatches (a wand first) and drops it wherever it has flown; strike it to make it let go.
- **Danger / use:** a lost wand up a tree; a swarm of three hoists you and lets you down slowly — no damage.
- **Environment:** forest swarms (2–4).
- **Constraints:** a pest, not a monster; Peskipiksi Pesternomi is a joke spell in canon and is left unimplemented.
- **Gap:** Immobulus (canon counter) is a `coming_soon` spell.

### Augurey — A (this pass)
- **Identity:** the "Irish phoenix" whose cry means rain.
- **Behaviour:** cries while rain is coming; flies only in rain, otherwise grounded near its roost.
- **Interaction:** a living barometer; watch it quietly (it flees the upright).
- **Constraints:** the cry is not an omen of death and does no harm; no drops invented.

### Mooncalf — A
- Burrowed until the full moon; the herd dances; dung at dawn; can be kept. `creature_mooncalf_dances_under_the_full_moon`.

### Streeler — A (this pass)
- **Identity:** the snail that changes colour hourly and poisons the ground.
- **Behaviour:** colour on the hour; trail withers plants, burns grass; kills Horklumps.
- **Danger / use:** keep it out of the garden; set it on a Horklump patch.
- **Constraints:** never attacks people; no taming.

### Blast-Ended Skrewt — A (this pass)
- **Identity:** Hagrid's illegal hybrid.
- **Behaviour:** blasts forward from its tail; burns what is behind; crowded, they kill each other.
- **Interaction:** do not stand behind it; spells mostly glance off.
- **Constraints:** refuses food — no bond profile, no taming, by design.
- **Gap:** stingers vs suckers (male/female) not modelled.

### Griffin — A (this pass)
- **Identity:** a guardian of gold.
- **Behaviour:** claims nearby gold as home and stays by it.
- **Interaction:** feed it raw meat to be tolerated at its gold; anyone else is warned once, then attacked.
- **Constraints:** no riding (canon never has one ridden); no griffin materials.

---

## 3. Full roster

Data-driven creatures are listed by id (`data/wizards_and_beasts/creatures/<id>.json`); bespoke ones are marked (b).

| Creature | Class | Signature today | What is missing (canon) |
|---|---|---|---|
| abraxan | C | enrage | whisky-drinking giant palomino; carriage-pulling |
| acromantula | A | colony home, wall-climb, silk, web snare, venom, speech | — |
| aethonan | C | enrage | — (canon gives little beyond size/colour) |
| antipodean_opaleye | B | dragon kit: breath, breed scale | breed behaviour (eats sheep, avoids killing humans) |
| ashwinder | B | fire affinity, ember trail | eggs that ignite (deferred item) |
| augurey (b) | A | rain forecast cry, flies only in rain | — |
| baby_niffler (b) | A | part of the Niffler litter/pouch system | — |
| basilisk | A | gaze, venom, counterplay | — |
| billywig | B | sting status, glow | the sting's giddy levitation as the defining effect |
| blast_ended_skrewt | A | blast propulsion, infighting, spell resist | stinger/sucker sexes |
| boggart | A | becomes your fear, crowd confusion, dark places | non-creature fears |
| bowtruckle (b) | A | tree, camouflage, locks | — |
| bundimun | B | block decay | house-infestation spread |
| centaur | A | star reading, forest keeping, no rider | arrows instead of hexes |
| chimaera | C | fire breath, charge | — |
| chinese_fireball | B | dragon kit | — |
| chizpurfle | C | life leech | drawn to magic (potions, wands) |
| clabbert | B | glow + danger sense | the pustule flashing as an alarm others can read |
| common_welsh_green | B | dragon kit | — |
| cornish_pixie (b) | A | snatching, hoisting | Immobulus counter |
| crup | C | leap | hostility to Muggles only |
| demiguise | A | invisible when watched, foresees straight approaches, sheds | — |
| dementor (b) | A | aura, drain, Kiss, Patronus | — |
| diricawl | B | blink away | — |
| doxy | C | poison bite, blink | doxycide counter |
| dugbog | B | camouflage in water, leap | eats Mandrakes |
| erkling | C | dread aura | lures children with its cackle |
| erumpent | B | explosive horn, charge | — |
| fairy | D | glow | — (decorative, vain) |
| fire_crab | B | flame burst | — |
| flobberworm | D | — | — (the dullest creature in canon, deliberately) |
| fwooper | B | maddening song | silencing charm counter |
| ghoul | D | — | — (a harmless attic nuisance) |
| giant | C | ranged hex, damage reduction | boulders; tribes |
| glumbumble | B | melancholy spore cloud, dread | melancholy treacle |
| gnome | C | blink away | de-gnoming (swing and throw) |
| goblin_teller (b) | — | Gringotts NPC | — |
| golden_snidget | B | evasion | — |
| granian | C | — | speed |
| graphorn | B | spell resist, thorns | — |
| griffin | A | hoard guard | — |
| grindylow | C | constrict | — |
| hebridean_black | B | dragon kit | — |
| hidebehind (b) | A | invisible to whoever faces it, hurt from its blind spot | — |
| hippocampus | D | water affinity | — |
| hippogriff | A | respect, bond, riding | — |
| hodag | C | leap, life leech | — |
| horklump | D | — (now the Streeler's prey) | — |
| horned_serpent | C | glow | the jewel |
| hungarian_horntail | B | dragon kit | egg guarding |
| imp | C | status on hit, blink | — |
| jarvey | B | insulting jinx, theft | — |
| jobberknoll | B | death cry | its feathers (truth/memory potions) |
| kappa | C | water, leech | bowing spills the water from its head |
| kelpie | A | horse disguise, grip, drowning, bridle | — |
| kneazle | B | danger sense | detects untrustworthy people |
| knarl | C | thorns | offended by gifts of food |
| lethifold | B | smother | Patronus as the only defence |
| leprechaun | B | thief, glow | leprechaun gold that vanishes |
| lobalug | D | thorns | — |
| mackled_malaclaw | C | status on hit | its bite's bad luck |
| maledictus | C | constrict | the curse's transformation |
| manticore | C | frenzy, poison | — |
| matagot | B | duplication when attacked | — |
| merperson | A | song under water | villages, territory |
| moke | B | camouflage | shrinking |
| mooncalf (b) | A | burrowed, moon dance, dung, keeping | — |
| murtlap | B | heal aura | essence as a cure (item exists) |
| niffler (b) | A | gold, digging, pockets, pouch, bond, litter | benchmark |
| nogtail | C | blink | blights farms; only a white dog finds it |
| norwegian_ridgeback | B | dragon kit | venomous fangs |
| nundu | B | pestilence breath | — |
| obscurus | B | dread aura, hex | its host |
| occamy | B | choranaptyxis | nests and silver eggs |
| peruvian_vipertooth | B | dragon kit | — |
| phoenix (b) | A | rebirth, tears, song, flame travel | — |
| plimpy | D | water | — |
| pogrebin | B | dread, camouflage | follows a person until despair |
| porlock | D | blink, heal | guards horses |
| puffskein | D | glow | eats bogeys (not modelled, deliberately) |
| pukwudgie | C | ranged hex, blink | — |
| pygmy_puff | D | glow | — |
| qilin | C | heal aura | bows to the worthy |
| quintaped | C | leap, poison, pack tactics | the cursed McClivert clan of the Isle of Drear |
| ramora | B | anchor | — |
| red_cap | C | status on hit | — |
| reem | C | charge, heal aura | — |
| romanian_longhorn | B | dragon kit | — |
| rougarou | C | camouflage | — |
| runespoor (b) | B | three heads, shared health | each head's role |
| salamander | B | lives in fire (fire affinity) | — |
| sea_serpent | C | constrict | — |
| shrake | D | thorns | — |
| snallygaster | C | ranged hex, leap | — |
| sphinx | B | riddle | — |
| streeler (b) | A | hourly colour, withering trail, kills Horklumps | — |
| swedish_short_snout | B | dragon kit | — |
| swooping_evil | C | leech, evasion | venom that erases memory |
| tebo | C | camouflage | — |
| thestral (b) | A | seen only by witnesses of death | — |
| thunderbird | B | storm, danger sense | — |
| toad | D | — | — |
| troll | C | enrage, damage reduction | — |
| ukrainian_ironbelly | B | dragon kit | — |
| unicorn | A | wary, grooming, shedding, slayer curse | foals |
| vampire | — | player heritage | — |
| veela | — | player heritage | — |
| wampus_cat | C | frenzy | — |
| werewolf | A | moon-bound, cursed bite, pack | — |
| yeti | C | camouflage, spore cloud | — |
| zouwu | C | leap, blink | tamed with a toy |

**Totals (111 rows):** A 23 · B 39 · C 34 · D 12 · — 3 (vampire, veela, goblin teller).

---

## 4. Validation

| Check | Result |
|---|---|
| `./gradlew compileJava` | green |
| `./gradlew build` (all unit tests incl. `SignatureRulesTest`) | green |
| `./gradlew runGameTestServer` | **150 / 150 required tests passed**, including the 8 `CreatureIdentityTests` scenarios |
| Mutation check | `Infighting.rival` forced to `null` → `creature_skrewt_blasts_and_turns_on_its_kind` fails with "three skrewts together did not fight"; restored |
| In-client capture (`EntityShowcaseCapture`, front/side/far, 8 creatures) | 24 shots, no model/texture/animation errors. Streeler shows its hour's colour with the shell bands still legible. The Boggart is blank (unwatched = unseen, as the report already lists it); a forced-guise run showed it drawn as a full-size Dementor with the Dementor's `float` clip, then the forcing was removed |

Server authority: every behaviour decides on the server; clients receive synced state only (the Boggart's guise, the
Pixie's movement, sounds and particles). Per-listener output (merfolk song, warnings, readings) is sent to that player
alone. Persistence: carried items and cooldowns (Pixie), roosts and hoards (vanilla `home`), warnings and grudges
(keyed ability cooldowns) all survive a save — each asserted with a save round-trip in the scenarios; the Boggart's
shape deliberately does not.

**Not verified:** live movement (the showcase and the game tests run creatures with AI off), a real two-client
multiplayer session (server authority is exercised with server-side mock players), and the Augurey's flight in real
rain (the forecast is tested with the weather handed in, as the full moon is elsewhere).

## 5. Open items

- **Occamy:** nests and laid silver eggs (the shells are still a kill drop).
- **Centaur:** arrows instead of the shared hex projectile.
- **Merpeople:** villages and territory.
- **Cornish Pixie:** Immobulus is a `coming_soon` spell; implementing it would give the canon counter.
- **Boggart:** fears that are not creatures (the full moon, a teacher) have no rig to become.
- **B-class species** in §3 each list the one canon behaviour that would make them A; the next candidates by payoff are
  Kneazle (sees through untrustworthy people), Leprechaun (gold that vanishes), Fwooper (silencing charm), Kappa (the
  bow), Jobberknoll (feathers) and the dragons' breed behaviour.
