# Creature Quality Matrix

Revision 2026-10-06. Twelve dimensions per creature. The grades come from
`CREATURE_QUALITY_MATRIX_DATA.md`, which `python tools/creature_quality_matrix.py` measures from data, assets and
code for all 103 creatures — re-run it rather than editing grades by hand. This page adds judgement for the
high-priority archetypes, what this pass changed, and what each one needs next.

**Legend:** ● rich · ◐ basic / default · ○ missing · – not applicable (a creature that never fights has no combat).

## 1. Priority archetypes

Columns: **Vis**ual identity · **Mov**ement · **Idle** · **Com**bat · **Non**-combat behaviour · **Hab**itat ·
**Int**eraction · **Bon**ding · **Har**vest · **Bes**tiary · **Snd** sound · **VFX**.

| Creature | Vis | Mov | Idle | Com | Non | Hab | Int | Bon | Har | Bes | Snd | VFX | Ecology (live?) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Niffler | ● | ● | ● | – | ◐ | ● | ● | ● | ○ | ● | ● | ● | treasure-seeker, curious, burrowing, cowardly (live) |
| Bowtruckle | ◐ | ● | ◐ | ● | ● | ● | ● | ● | ● | ● | ● | ○ | tree-dwelling, territorial, social, climbing (live) |
| Phoenix | ◐ | ● | ◐ | – | ● | ● | ● | ● | ● | ● | ● | ● | flying, magical-sense, curious, corruption-sensitive (live) |
| Unicorn | ◐ | ◐ | ● | – | ● | ● | ● | ○ | ● | ● | ○ | ○ | prey, herd, human-avoidant, corruption-sensitive, magical-sense (live) |
| Demiguise | ◐ | ◐ | ◐ | – | ● | ● | ● | ○ | ● | ● | ○ | ● | human-avoidant, illusion-aware, cowardly, climbing (live) |
| Mooncalf | ● | ◐ | ◐ | – | ● | ● | ● | ● | ● | ● | ○ | ○ | prey, herd, nocturnal, burrowing, cowardly, human-avoidant (live) |
| Hippogriff | ◐ | ● | ● | ● | ◐ | ● | ● | ● | ○ | ● | ○ | ○ | herd, predator, flying, social (live) |
| Thestral | ◐ | ● | ● | ● | ◐ | ● | ● | ● | ● | ● | ○ | ○ | herd, scavenger, flying, illusion-aware (live) |
| Dementor | ◐ | ◐ | ◐ | – | ◐ | ◐ | ◐ | ○ | ○ | ● | ● | ● | flying, magical-sense, illusion-aware (data only) |
| Basilisk | ● | ◐ | ● | ◐ | ◐ | ○ | ◐ | ○ | ● | ● | ○ | ● | predator, territorial, aggressive (live; Chamber only by design) |
| Acromantula | ◐ | ● | ◐ | ● | ◐ | ● | ◐ | ○ | ● | ● | ● | ○ | predator, territorial, aggressive, nocturnal, social, climbing (live) |
| Centaur | ◐ | ◐ | ● | ◐ | ◐ | ○ | ◐ | ○ | ○ | ● | ○ | ○ | herd, magical-sense (live) |
| Merperson (mermaid) | ◐ | ◐ | ● | ◐ | ◐ | ○ | ◐ | ○ | ○ | ● | ● | ○ | aquatic, territorial, social, magical-sense (live) |
| Grindylow | ◐ | ◐ | ● | ◐ | ◐ | ○ | ◐ | ○ | ○ | ● | ● | ○ | aquatic, predator, territorial, aggressive, social (live) |
| Cornish Pixie | ◐ | ◐ | ◐ | – | ◐ | ● | ◐ | ○ | ○ | ● | ◐ | ○ | flying, curious, social (live) |
| Zouwu | ◐ | ◐ | ● | ● | ◐ | ○ | ◐ | ○ | ○ | ● | ○ | ○ | curious, magical-sense (live) |
| Occamy | ● | ◐ | ● | ● | ◐ | ● | ◐ | ○ | ● | ● | ○ | ● | territorial, aggressive, predator (live) |
| Dragon family (×10) | ●/◐ | ◐ | ● | ● | ◐ | ◐¹ | ◐ | ○ | ● | ● | ○ | ●² | predator + flying; territorial/aggressive by breed; Welsh Green and Short-Snout avoid people; Opaleye warns (live) |

¹ Only the Common Welsh Green spawns naturally. ² The breath kit draws particles; the matrix script credits only
creatures whose own code names particles, so most breeds read ○ there.

**Non-combat** is ● only when the ecology is live *and* the bestiary carries a signature for the creature; a live
ecology with no recorded signature is ◐.

## 2. What this pass changed (first wave)

| Creature | Before | Now |
|---|---|---|
| All 27 profiled creatures | no ecology; reactions declared and never raised | capabilities in data, decided every second, acted on by one goal; spells, deaths and wounds raise reactions |
| Unicorn | wary, groomable, sheds | also a herd that regroups, prey that bolts from an acromantula, scatters when one of its herd falls, flees spellcasting |
| Mooncalf | burrow, dance, dung | also herd, prey and avoidance through the shared layer (its burrow stays its own) |
| Hippogriff | bow etiquette, rideable; **never spawned** | herds on meadows and windswept hills; hunts chickens and rabbits, then is fed and leaves them |
| Acromantula | colony, webs, venom | hunts unicorns and mooncalves at night, rests by day, defends its ground, the colony answers a wound |
| Bowtruckle | tree, camouflage, locks | warns a stranger before it attacks; the tree's other Bowtruckles answer one being hurt |
| Grindylow | C — constrict only | ambush predator of fish, defends its stretch of water, answers its kin |
| Dragons | breath, breed scale | hunt livestock; breed temper decides warn vs attack vs avoid; fed after a kill |
| Occamy | choranaptyxis; **never spawned** | rare in jungle and bamboo; guards its ground and hunts |
| Zouwu, Phoenix, Pixie | — | curious: come to look at spells and quiet visitors (pixies keep their own pestering) |
| Every profiled creature | — | what a player watches it do is written into its bestiary page as a field note |

## 3. Next, per creature (before expanding the roster)

| Creature | Next step | Column it moves |
|---|---|---|
| Niffler | a bestiary signature and harvest rule (shed coin? none in canon — leave ○) | Non |
| Unicorn | foals (golden → silver), canon's missing half | Non, Vis |
| Hippogriff | its own sound set; the bow recorded as a signature | Snd, Non |
| Centaur | arrows instead of the shared hex; a forest habitat | Com, Hab |
| Merperson, Grindylow | lake habitats (a spawn rule that requires deep fresh water, like the Kelpie's) | Hab |
| Zouwu | Bamboo habitat; canon's tame-with-a-toy interaction | Hab, Int |
| Occamy | nest guarding and laid eggs | Non, Har |
| Dragons | sanctuaries rather than wild spawns for the nine aggressive breeds | Hab |
| Dementor | `EcologyGoal` on its `Monster` class, or a recorded reason not to | Non |
| Roster-wide | sound sets: 89 of 103 creatures have no own sounds | Snd |

## 4. Roster totals (all 103, measured)

See `CREATURE_QUALITY_MATRIX_DATA.md` §Totals. The two weakest columns roster-wide are **Habitat** (78 creatures
have no natural spawn) and **Sound** (89 have no own sounds); **Non-combat** behaviour moved from 0 live ecologies
to 27 this pass, and is the column the next waves should fill.
