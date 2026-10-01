# Wizards & Beasts — Canon Audit

`dev`, 2026-09-30. The audit (§0–§13) was written first, with no code changed. All twelve contradictions were then
resolved, on the owner's instruction to "do all" in an order of my choosing; see **§14** for the ruling applied to
each. The findings tables above §14 describe the code as it was audited. This compares the current implementation with the
established Wizarding World source material and with the project's own documented design. It covers spells, wand
mechanics, creatures, heritage, transformations, magical conditions, artefacts, Ministry and law, creature
interactions and the magical economy. Major locations (Azkaban, Chamber of Secrets, Diagon Alley and the rest) are
out of scope.

## 0. Rules this audit follows

**Source hierarchy.** Highest first:

1. The seven novels (cited as PS, CoS, PoA, GoF, OotP, HBP, DH, with chapter numbers).
2. Rowling's companion books (*Fantastic Beasts and Where to Find Them*, *Quidditch Through the Ages*) and her
   Wizarding World / Pottermore writing.
3. The screenplays (*Fantastic Beasts* films).
4. The films, where they add to the page.
5. Licensed games (*Hogwarts Legacy* and earlier). These are official but not Rowling's. The mod already tracks
   this split in `SpellCanonTier` (BOOKS / COMPANION / POTTERMORE / FILM / EXPANDED / ORIGINAL).

**Fanon is not canon.** Popular extrapolations are marked as such (the "five exceptions" to Gamp's Law, silver
against werewolves, vampire sunlight weakness).

**Citations marked *(to verify)*** are ones this audit is confident of in substance but did not check line by line.
They must be checked before anything is changed on their authority. Nothing here states a canon fact without
either a citation or that flag.

**Categories**

| Tag | Meaning |
|---|---|
| **CANON** | directly established |
| **CANON-INSPIRED** | a reasonable gameplay interpretation of canon |
| **MC ABSTRACTION** | an implementation compromise required by Minecraft |
| **ORIGINAL** | a deliberate Wizards & Beasts design |
| **CONTRADICTION** | the implementation conflicts with established lore (§11) |
| **UNCERTAIN** | the sources do not establish enough (§12) |

## 1. Summary

| Area | Canon-faithful | Contradictions | Uncertain |
|---|---|---|---|
| Spells | Avada Kedavra unblockable; Riddikulus; Patronus vs Dementors; Reparo; Episkey; Unforgivables need intent | **C-1** Patronus form by blood status | Accio on living things; Gamp's Law domains |
| Wands | Allegiance by defeat (disarm, stun, kill); Elder Wand won not made; foreign wand weaker; broken wand misbehaves; only the Elder Wand mends a snapped wand; wood and core lore | **C-3** ordinary wands need two defeats; **C-8** free, repeatable wand | Troll-whisker core |
| Creatures | Basilisk rules; Thestral sight; Hippogriff bow; Dementors; Occamy, Demiguise, Mooncalf, Bowtruckle, Niffler, Streeler; hides that resist spells | **C-12** dragons have no spell resistance; **C-11** Sphinx/Jarvey effects; **C-6** unicorn curse on kill | Troll hide strength |
| Heritage | Blood status confers no power; Squibs cannot cast; Code of Wand Use for goblins and house-elves; Veela allure; merpeople kinds | **C-5** half-giants cannot use wands | Vampire traits; which beings can conjure a Patronus |
| Transformations / conditions | Werewolf bite only in wolf form; no cure; Wolfsbane; Obscurus from suppressed magic | **C-4** one default Animagus form for everyone | Silver against werewolves |
| Artefacts | Time-Turner's hours; Portkey group travel; Resurrection Stone vs Dementors; Horcruxes and the fang; Sneakoscope; Foe-Glass; mirror; beaded bag | **C-7** Hand of Glory blinds others; **C-9** Philosopher's Stone buff stack; **C-10** Remembrall | Revelio against the Hallow cloak |
| Ministry / law | The Trace (under-17); Statute of Secrecy; Apparition test and splinching; Unforgivables as crimes | **C-2** Unforgivables are crimes whatever the target | Size of the unregistered-Animagus penalty; unlicensed Apparition |
| Economy | Knut / Sickle / Galleon ratios; goblin bank; Leprechaun gold vanishes | (**C-8** wand price) | Dragot; Firebolt Supreme |

## 2. Spells

| Implementation | Tag | Basis |
|---|---|---|
| Avada Kedavra ignores Protego (`AvadaKedavra.isUnblockable`, `ProtegoShieldEntity` lets it through) | **CANON** | "There's no countercurse. There's no blocking it" (GoF ch.14). The earlier open item F-2 in `SPELL_CANON_AUDIT.md` is settled: the code matches. |
| Avada Kedavra is a projectile, so walls and blocks stop it | **CANON** | Physical objects block it (DH ch.36, the Atrium duel, OotP ch.36 statues) *(to verify chapter)* |
| Avada Kedavra requires Proficient Imperio and Crucio | **ORIGINAL** | Canon only says it needs "a powerful bit of magic behind it" (GoF ch.14). The prerequisite chain is the mod's way of gating it. |
| Crucio's strength scales with intent (corruption, proficiency, Dark nodes) | **CANON-INSPIRED** | "You need to mean them" (OotP ch.36) |
| Imperio is resisted by willpower (sneak attempts, WILLPOWER) | **CANON-INSPIRED** | Harry throws it off (GoF ch.15) |
| Unforgivables stain the caster (corruption) | **CANON-INSPIRED** | Canon ties the worst magic to damage of the soul (Horcruxes, HBP ch.23). The toll amounts are ORIGINAL. |
| Patronus strength from happiness and memories, weakened by corruption | **CANON-INSPIRED** | Happy memory (PoA ch.12). Canon also shows a cruel person casting one (Umbridge's cat, DH ch.13), so corruption weakening it is interpretation, not rule. |
| **Patronus form chosen by heritage and blood status** (`PatronusFormDeterminer`) | **CONTRADICTION** | See **C-1** |
| Riddikulus banishes a Boggart | **CANON** | PoA ch.7 |
| Stupefy stuns; several at once for a hide (`MagicResistance`) | **CANON / CANON-INSPIRED** | Several Stunners for a dragon (GoF ch.19); Hagrid shrugging them off (OotP ch.31). The counts (2 / 3 / 4) are ORIGINAL. |
| Expelliarmus disarms and counts as a defeat of the wand's master | **CANON** | DH ch.24 and ch.36 (Draco disarming Dumbledore) |
| Levicorpus hoists; Liberacorpus releases | **CANON** | HBP ch.12 |
| Levicorpus slams the target down for fall damage when it ends | **ORIGINAL** | |
| Reparo cannot mend a snapped wand; only the Elder Wand can | **CANON** | DH ch.19, DH ch.36 |
| Episkey heals minor injuries | **CANON** | HBP ch.8 (Tonks and Harry's nose) |
| Diffindo applies a "sundered" wound to living targets | **CANON-INSPIRED** | Canon uses it on objects (GoF ch.20). Using it on bodies is interpretation, a mild stretch. |
| Accio, Depulso, Wingardium Leviosa and Flipendo move mobs | **UNCERTAIN / CANON-INSPIRED** | Canon shows Accio on objects. Leviosa lifts a troll's club (PS ch.10), not the troll. Summoning or lifting living beings is not established. |
| Glacius, Flipendo, Depulso, Revelio | **EXPANDED** (games) | Correctly tagged in `canonTier` |
| Frigora, Claustra Reverto | **ORIGINAL** | Correctly tagged |
| Arresto Momentum, Bombarda | FILM | Tagged `film`. Arresto Momentum's book status is worth a re-check *(to verify)*. |
| Practice budget, tiers, power curve, skill-web spells | **ORIGINAL** | Canon has practice and study but no mechanism |
| **Gamp's Law: four domains** (food, life, love, information) (`GampDomain`) | **UNCERTAIN** | See §12. Canon names food only, as "the first of the five Principal Exceptions" (DH ch.15). |

## 3. Wand mechanics

| Implementation | Tag | Basis |
|---|---|---|
| The wand chooses the wizard (resonance trial) | **CANON** | PS ch.5 |
| Allegiance moves on defeat: disarm, stun or kill (`WandAllegianceService`) | **CANON** | DH ch.24 (Ollivander) and ch.36; Grindelwald stunning Gregorovitch (DH ch.23) *(to verify chapter)* |
| Elder Wand goes with the first defeat and cannot be crafted (`ElderWandSavedData`) | **CANON** | DH ch.35–36 |
| **An ordinary wand needs two defeats** (`WandAllegianceRules.BASE_WINS_TO_TRANSFER = 2`) | **CONTRADICTION** | See **C-3** |
| Another wizard's wand works, but worse (0.70×) | **CANON / CANON-INSPIRED** | Harry with the blackthorn wand, Hermione with Bellatrix's (DH ch.19, ch.26). The number is ORIGINAL. |
| Broken wand always backfires | **CANON** | Ron's wand (CoS ch.6 and following) |
| Unicorn hair and rowan resent the Dark Arts; blackthorn bonds through danger; hawthorn backfires in careless hands; thestral hair needs someone who has faced death | **CANON** (POTTERMORE) | Wizarding World "Wand Woods" / "Wand Cores" *(to verify exact wording)* |
| Woods: ash, blackthorn, elder, hawthorn, holly, rowan, vine, walnut, willow, yew | **CANON** (POTTERMORE) | All ten appear in Ollivander's wood notes |
| Cores: phoenix, dragon, unicorn, Veela hair (Fleur, GoF ch.18), thestral tail hair (Elder Wand), Thunderbird, Wampus, rougarou, White River Monster spine (American wandlore) | **CANON** | Veela hair from the novels; the rest from Wizarding World writing |
| Troll-whisker core | **UNCERTAIN** | Not in the novels; source not identified |
| Bond growth, neglect decay, power per bond state | **ORIGINAL** | |
| **The Ollivander trial gives a wand free, repeatably** | **CONTRADICTION** | See **C-8** |

## 4. Creatures

| Implementation | Tag | Basis |
|---|---|---|
| Basilisk: direct gaze kills, indirect petrifies (water, spyglass); the rooster's crow is fatal; only phoenix tears cure the venom; fangs destroy Horcruxes | **CANON** / spyglass **CANON-INSPIRED** | CoS ch.16–18; DH ch.31. Canon's indirect views are a reflection, a camera, a ghost and water; the spyglass stands in for a mirror. |
| Thestrals seen only by those who have witnessed death (per-viewer rendering) | **CANON** | OotP ch.21 |
| Hippogriff must be bowed to first | **CANON** | PoA ch.6 |
| Dementors cannot be killed; the Patronus drives them off; the Kiss | **CANON** | PoA ch.12; that they cannot be killed is Wizarding World writing *(to verify)* |
| Graphorn, troll, giant, manticore, quintaped and skrewt hides resist spells | **CANON-INSPIRED** | Graphorn hide repels spells "more effectively than dragon hide" (*Fantastic Beasts*). A skrewt's shell deflects spells (GoF ch.31) *(to verify)*. The troll and manticore values are ORIGINAL. |
| **Dragons carry no spell resistance** | **CONTRADICTION** | See **C-12** |
| Dragons all rated XXXXX | **CANON** | *Fantastic Beasts* |
| Occamy: choranaptyxic, pure-silver eggshells | **CANON** | *Fantastic Beasts* |
| Demiguise: invisibility, foresight, hair woven into cloaks | **CANON** | *Fantastic Beasts* |
| Mooncalf full-moon dance and dung | **CANON** | *Fantastic Beasts* |
| Bowtruckle: guards wand-trees, eats woodlice, picks locks | **CANON** (lock-picking FILM) | *Fantastic Beasts* book and film |
| Niffler drawn to shiny things | **CANON** | *Fantastic Beasts* |
| Streeler venom kills Horklumps | **CANON** | *Fantastic Beasts* |
| Fwooper song drives listeners mad | **CANON** | *Fantastic Beasts* |
| Nundu breath spreads disease (poison, wither and hunger here) | **CANON-INSPIRED** | *Fantastic Beasts*: disease-laden breath, never subdued by fewer than a hundred wizards |
| Thunderbird brings storms | **CANON** (companion / film) | *Fantastic Beasts* |
| **Sphinx ability = Blindness + Nausea; Jarvey ability = Unluck + Weakness** | **CONTRADICTION** (mild) | See **C-11** |
| **Unicorn: killing one curses the killer** (`SlayerCurse`) | **CONTRADICTION** (mild) | See **C-6** |
| Kelpie is a shapeshifting water horse | **CANON** | *Fantastic Beasts*. The bridle that subdues it (same source) was not checked against the code in this pass. |
| Creature bonds, gifts, breeding, bestiary tiers from watching | **ORIGINAL** | |
| Werewolf creature infects only in wolf form at the full moon | **CANON** | See §6 |

## 5. Heritage

| Implementation | Tag | Basis |
|---|---|---|
| Blood status (pure-blood, half-blood, Muggle-born) changes nothing about power (`PowerBandTable`: everyone with magic vs the Squib) | **CANON** | Hermione (every book); blood purity as prejudice (CoS ch.7) |
| Squibs cannot cast (`no_casting`) | **CANON** | Filch, Mrs Figg (CoS ch.8, OotP ch.2) |
| Squib creature kinship (one extra trust tier) | **CANON-INSPIRED** | Mrs Figg's Kneazle crosses, Filch and Mrs Norris |
| Goblins and house-elves cannot use wands (`canUseWand = false`) | **CANON** | Code of Wand Use, clause 3 (GoF ch.9). Both do magic without wands (house-elves throughout; goblins, DH ch.24). |
| Centaurs and merpeople cannot use wands | **UNCERTAIN** | Neither is shown with one, but no rule is stated |
| Full giants cannot use wands | **UNCERTAIN** | Not established |
| **Half-giants cannot use wands** (`Heritage.GIANT.canUseWand = false` covers `GIANT_HALF`) | **CONTRADICTION** | See **C-5** |
| Giant hide resists spells (`spell_resistant_hide`) | **CANON** | Hagrid, "giant blood" (OotP ch.20 and ch.31) *(to verify exact chapter)* |
| Veela allure entrances | **CANON** | GoF ch.8 |
| Veela allure works on everyone, regardless of sex | **MC ABSTRACTION** | Canon shows it working on men (GoF ch.8). Minecraft has no sex for players or mobs. |
| Veela bird-like fury when angry | **CANON** | GoF ch.8. Tagged `transformation`; the implementation was not verified in this pass. |
| Merpeople kinds: merrow, selkie, siren; Mermish | **CANON** | *Fantastic Beasts*; GoF ch.26 |
| Vampire sunlight weakness | **UNCERTAIN** | Folklore, not established in the Wizarding World sources |
| Vampires drink blood | **CANON** | HBP ch.15 (Sanguini) *(to verify chapter)* |
| Dhampir lineage | **ORIGINAL** | Folklore import, not Wizarding World material |
| Only Wizardkind is playable (alpha) | ORIGINAL scope | Limits how much the heritage findings matter today |
| Heritage professions (`ProfessionNode`), POWER roll | **ORIGINAL** | |

## 6. Transformations and conditions

| Implementation | Tag | Basis |
|---|---|---|
| Lycanthropy passes by a bite in wolf form under the full moon (`LycanthropyInfection`) | **CANON** | PoA ch.18; Bill bitten by the human-form Greyback is not a full werewolf (HBP ch.29) |
| No cure for lycanthropy (only an admin command removes it) | **CANON** | PoA ch.18 |
| Wolfsbane keeps the mind during the change | **CANON** | PoA ch.18 |
| Werewolf a *condition*, not a people | **CANON** | Lupin is a wizard with a condition |
| Silver weapons hurt a transformed werewolf (`SilveredWeapons`) | **UNCERTAIN** | Folklore. Canon links silver to *treating* werewolf wounds (powdered silver and dittany *(to verify)*), not to wounding the wolf. |
| Obscurus from suppressed magic; resources separate from corruption | **CANON** | *Fantastic Beasts* screenplays |
| Adult Obscurials playable | **CANON-INSPIRED** | Canon says Obscurials rarely live past ten, and Credence is the exception (*Fantastic Beasts*) |
| Animagus ritual: a mandrake used in a thunderstorm, in one step | **MC ABSTRACTION** | Wizarding World writing describes a month-long process with a mandrake leaf, dew, a moth chrysalis and a lightning storm *(to verify)*. The mod keeps the ingredients and the storm and compresses the rest. |
| **Every new Animagus gets the same form (cat); the form can be changed later** | **CONTRADICTION** | See **C-4** |
| Unregistered Animagus is an offence | **CANON** | PoA ch.18, GoF ch.37 |
| Unregistered Animagus penalty is a 10-Galleon fine | **UNCERTAIN** | See §12 |
| Polyjuice: someone else's hair, human forms only | **CANON** | CoS ch.12 and ch.16 (Hermione and the cat hair) |
| Polyjuice lasts 300 real seconds, described as "an hour" | **MC ABSTRACTION** | One hour per dose (CoS ch.12; GoF ch.35). The length is scaled to play time. |

## 7. Artefacts

| Implementation | Tag | Basis |
|---|---|---|
| Time-Turner: a turn per hour, back to where you stood, and the world keeps its own time | **CANON** (hours) / **MC ABSTRACTION** (no second self) | PoA ch.21. The two Harrys cannot be modelled in one Minecraft world, so only the wearer's position goes back. |
| Portkey carries everyone touching it | **CANON** | GoF ch.6 |
| Portkey is consumed after one trip; the tug is manual | **MC ABSTRACTION** | Canon Portkeys leave at a set time and some make a return trip (the Triwizard Cup) |
| Resurrection Stone shades shield from Dementors | **CANON** | DH ch.34 |
| Horcruxes: dread and corruption; destroyed by basilisk venom | **CANON / CANON-INSPIRED** | DH; the dread aura is interpretation |
| Invisibility cloaks: the Hallow never fades, Demiguise cloaks do | **CANON** | DH ch.21 (Xenophilius), *Fantastic Beasts* |
| Revelio shows a wizard under any cloak, the Hallow included | **UNCERTAIN** | See §12 |
| Sneakoscope detects deceit | **CANON** | PoA ch.1, GoF |
| Foe-Glass shows enemies, clearer the closer they are | **CANON** | GoF ch.20 |
| Foe-Glass counts every player and every hostile mob as a foe | **MC ABSTRACTION** | |
| Two-Way Mirror: speak the name | **CANON** | OotP ch.38 (Sirius's gift) |
| Beaded bag (Undetectable Extension Charm) | **CANON** | DH ch.9 |
| Deluminator takes and returns light | **CANON** | PS ch.1, DH ch.7 and ch.19 |
| Marauder's Map shows people | **CANON** | PoA ch.10 |
| Marauder's Map charts the whole world rather than Hogwarts | **MC ABSTRACTION** | |
| Pensieve lists your own memories | **CANON-INSPIRED** (partial) | GoF ch.30. Canon's main use, viewing *another's* memories, is not implemented (a gap, not a contradiction). |
| Dark Mark: a branding item anyone can use; a marked wizard summons every other marked wizard | **ORIGINAL** | Canon: Voldemort brands and summons his followers (GoF ch.33), and they summon *him* by touching the Mark (DH ch.23). With no Voldemort in the game, the mod gives the Mark to players. |
| **Hand of Glory blinds other players** | **CONTRADICTION** (mild) | See **C-7** |
| **Philosopher's Stone = permanent Regeneration, Absorption and Resistance** | **CONTRADICTION** | See **C-9** |
| Philosopher's Stone "destroyed 1992" tooltip | **CANON** | PS ch.17 (destroyed after the first year) |
| **Remembrall flashes on a timer** | **CONTRADICTION** (cosmetic) | See **C-10** |
| Hermione's bag, Pensieve, Mirror and Foe-Glass filed as non-Dark | **CANON-INSPIRED** | None of them is Dark magic |

## 8. Ministry and law

| Implementation | Tag | Basis |
|---|---|---|
| The Trace: under-17 magic reported by place, not by who cast it | **CANON** | OotP ch.2; HBP ch.4; DH ch.4; the Hover Charm blamed on Harry (CoS ch.2) |
| Characters are adults by default | **ORIGINAL** | A deliberate setting (Ministry law notes) |
| Statute of Secrecy: Muggle witnesses bring fines | **CANON / CANON-INSPIRED** | International Statute of Secrecy (throughout). The fine sizes are ORIGINAL. |
| Unforgivables are crimes | **CANON** | GoF ch.14 |
| **The crime ignores the target: cursing a chicken is filed like cursing a person** | **CONTRADICTION** | See **C-2** |
| A "sentence" means wand confiscation and a record, not Azkaban | **MC ABSTRACTION** | Azkaban is a location and out of scope. Canon: life in Azkaban (GoF ch.14). |
| Apparition test at 17, splinching | **CANON** | HBP ch.18 and ch.22 |
| Unlicensed Apparition fined 2 Galleons | **UNCERTAIN** | Canon calls it illegal (the trio in DH); the penalty is not stated |
| Wand confiscation; wand snapped on expulsion | **CANON** | Hagrid's wand snapped when he was expelled (PS ch.4 *(to verify chapter)*); the Ministry threatens to snap Harry's wand (OotP ch.2) |
| Wizengamot hearings | **CANON-INSPIRED** | OotP ch.8. The verdict rules are ORIGINAL. |
| Broom, dangerous-creature, restricted-substance and Ministry-access licences | **ORIGINAL** | Canon has registrations (Animagi, the Floo Network) but not these papers |
| Floo registration and fee | **CANON-INSPIRED** | Floo Network Authority / Floo Regulation Panel (GoF ch.4, OotP) *(to verify naming)* |

## 9. Creature interactions

| Implementation | Tag | Basis |
|---|---|---|
| Unicorns and other purity-sensitive creatures shun the corrupted | **CANON-INSPIRED** | Unicorns prefer the innocent and shy from men (*Fantastic Beasts*: they prefer a witch's touch *(to verify)*) |
| Dementors cannot feel someone walking with the Stone's shades | **CANON** | DH ch.34 |
| Hides resisting spells, overcome by several at once | **CANON-INSPIRED** | GoF ch.19 |
| Feeding and grooming as study; bestiary tiers | **ORIGINAL** | |
| Veela allure on `allure_susceptible` mobs | **ORIGINAL** extension | Canon shows it on humans only |

## 10. Magical economy

| Implementation | Tag | Basis |
|---|---|---|
| 29 Knuts = 1 Sickle; 17 Sickles = 1 Galleon | **CANON** | PS ch.5 |
| Gringotts run by goblins | **CANON** | PS ch.5 |
| Leprechaun gold vanishes | **CANON** | GoF ch.8 and ch.28 |
| Fines billed to the vault, skill respec fee, Floo fee | **ORIGINAL** | |
| Dragot, with a moving rate against the Galleon | **UNCERTAIN** | See §12 |
| **Wands cost nothing** | **CONTRADICTION** | See **C-8** |
| Brooms: Cleansweep Seven, Comet 260, Nimbus 2000/2001, Firebolt, Oakshaft 79 | **CANON** | PS, CoS, PoA, OotP; *Quidditch Through the Ages* |
| Firebolt Supreme | **UNCERTAIN** | Source not identified |

## 11. Potential contradictions

Each gives the exact implementation, the lore issue, whether it touches gameplay, and possible resolutions. **None
is implemented here.** Ordered by how much they matter.

### C-1 · The Patronus form is decided by blood status and heritage — **high**

- **Implementation.** `spell/patronus/PatronusFormDeterminer.determine`:
  - pure-blood → wolf, half-blood → fox, Muggle-born → rabbit, any other Wizardkind → goat;
  - vampire → bat or phantom; Veela → parrot;
  - at 90+ happiness a "rare" form per heritage (horse, cat, allay);
  - every other heritage gets `null`, which means no Patronus at all.
- **Lore issue.** A Patronus reflects the individual. It is not inherited, and it can change after deep emotional
  upheaval: Tonks's changes (HBP ch.8 *(to verify chapter)*), Snape's doe matches Lily's (DH ch.33). Nothing ties its form to blood
  status. Tying an animal to blood status also echoes the pure-blood ideology canon condemns: the soul sorted by
  ancestry.
- **Gameplay.** Yes, for identity and presentation: every Muggle-born gets the same rabbit. It also decides who can
  cast a Patronus at all.
- **Resolutions.**
  1. Derive the form from the character, not the lineage: a stable hash of the player's UUID over the form pool, so
     each player's Patronus is personal, fixed and not chosen.
  2. The same, plus a single "Patronus changed" event tied to a documented trigger, mirroring Tonks.
  3. Keep it datapack-driven but key it on something personal (the wand, a first successful cast) rather than on
     lineage.
  4. Separately, decide whether non-Wizardkind heritages may cast (see §12). The `null` should be a deliberate
     rule, not a missing switch case.

### C-2 · An Unforgivable is a crime whatever the target — **high**

- **Implementation.** `ministry/trace/MinistryTrace.onSuccessfulCast(caster, spellId, category)` receives no target.
  Every Unforgivable cast is filed as an Unforgivable incident (`LegalClass.UNFORGIVABLE`), so Crucio on a zombie or
  a chicken is a crime.
- **Lore issue.** "Use of any one of them on **a fellow human being** is enough to earn a life sentence in Azkaban"
  (GoF ch.14). In the same scene the curses are shown on spiders in a Hogwarts classroom (GoF ch.14) — though that
  "Moody" is an impostor, so the scene shows only that the law is about people, not that animal use is approved.
- **Gameplay.** Yes: players are prosecuted for hunting with the Killing Curse.
- **Resolutions.**
  1. Pass the target's kind into the trace: a human target (a player, villager or other "person") is the
     Unforgivable crime; a creature is a `DARK` act (it still leaves wand residue and still corrupts the caster).
  2. Keep casting at a creature legal but have witnesses report it as "Dark magic" (a weight in any case, not a
     charge).
  3. Leave it as a deliberate stricter law and document it as ORIGINAL.

### C-3 · An ordinary wand needs two defeats to change allegiance — **medium**

- **Implementation.** `WandAllegianceRules.BASE_WINS_TO_TRANSFER = 2` (plus temperament extras). Only the Elder
  Wand goes on the first defeat.
- **Lore issue.** In canon, one victory wins a wand. Harry overpowering Draco once at Malfoy Manor wins Draco's
  hawthorn wand and, through it, the Elder Wand (DH ch.24, ch.36). Ollivander speaks of a wand "won" with no count.
- **Gameplay.** Yes: duels and wand theft.
- **Resolutions.**
  1. Set the base to 1, and keep temperament extras as the one rule that makes a stubborn wood hold longer
     (canon-inspired, from Ollivander's notes on loyal woods).
  2. Keep 2 as an ORIGINAL anti-griefing measure, and say so in-game (the tooltip already names states).
  3. Count one decisive defeat (a disarm, or a stun from full health) as enough, and lesser hits as partial.

### C-4 · Every Animagus gets the same form, and it can be changed — **medium**

- **Implementation.** `AnimagusEvents.completeRitual` sets `AnimagusForms.defaultFormId()` (the first entry,
  `animagus_cat`) for everyone. `AnimagusTransformService.setForm` allows a different form while human (through a
  command, described as "register a different form").
- **Lore issue.** An Animagus takes one form, not chosen, and it reflects who they are: McGonagall's cat, Sirius's
  dog, Pettigrew's rat, Skeeter's beetle (PoA, GoF). Wizarding World writing says the witch or wizard has no choice
  of animal *(to verify wording)*.
- **Gameplay.** Yes: identity, and which form's movement a player gets.
- **Resolutions.**
  1. Assign the form at the ritual from a stable per-player seed over `AnimagusForms.IDS` (not chosen, not
     changeable); keep `setForm` as an admin tool.
  2. Make the result deterministic from something the player built (heritage traits and skill trees), with no
     choice presented.
  3. Keep the choice as an ORIGINAL accommodation and document it.

### C-5 · Half-giants cannot use wands — **medium (dormant)**

- **Implementation.** `Heritage.GIANT` has `canUseWand = false`, which covers `GIANT_FULL`, `GIANT_HALF` and
  `GIANT_CLAN`.
- **Lore issue.** Hagrid, a half-giant, keeps and uses his broken wand in the pink umbrella (PS ch.4, ch.5). Madame
  Maxime, a half-giant, is a witch and heads Beauxbatons (GoF).
- **Gameplay.** Not today: Giant is not alpha-available. It would matter the day it is.
- **Resolutions.**
  1. Put a `no_wand` trait on `GIANT_FULL` (and `GIANT_CLAN`) and set the Giant heritage's `canUseWand` to true,
     so the half-giant can use one.
  2. Split the half-giant out as Wizardkind + a lineage (as werewolf became a condition), since a half-giant is
     raised and trained as a wizard.

### C-6 · Killing a unicorn curses the killer — **low / medium**

- **Implementation.** `creature/ability/SlayerCurse.onDeath` (unicorn): the killer gets the `unicorn_slayer` flag,
  15 corruption, and Weakness and Unluck for 10–20 minutes.
- **Lore issue.** Canon calls slaying a unicorn "a monstrous thing", but the *cursed life* is laid on whoever drinks
  its blood: "from the moment the blood touches your lips" (PS ch.15).
- **Gameplay.** Yes: the penalty lands on the killing, and there is no blood-drinking act.
- **Resolutions.**
  1. Keep the social cost of killing (creatures shun the slayer, corruption); move "cursed" effects to a future
     unicorn-blood item (`unicorn_blood` is already tagged as a restricted ingredient).
  2. Keep it as is and rename the message so it describes monstrousness, not a curse.

### C-7 · The Hand of Glory blinds other players — **low / medium (PvP)**

- **Implementation.** `event/item/HandOfGloryTickHandler`: while it is lit, the holder gets Night Vision and every
  other player within 16 blocks gets Blindness.
- **Lore issue.** "Insert a candle and it gives light only to the holder" (CoS ch.4). Others do not see its
  light; nothing says they are blinded. In HBP (ch.27) it lights the Death Eaters' way through Peruvian Darkness
  Powder *(to verify pairing)*.
- **Gameplay.** Yes: a 16-block Blindness aura is an offensive tool.
- **Resolutions.**
  1. The holder sees in darkness, including Peruvian Instant Darkness Powder clouds, and others get nothing.
  2. Limit any effect on others to an existing darkness source (the powder).

### C-8 · Wands cost nothing, and the trial can be repeated — **medium (economy-dependent)**

- **Implementation.** `network/wand/ChooseTrialWandPayload` hands the chosen wand over with no price; the trial can
  be reopened.
- **Lore issue.** Harry pays seven Galleons (PS ch.5).
- **Gameplay.** Yes: losing a wand costs nothing, which weakens allegiance.
- **Resolutions.** Already listed in `PROGRESSION_MAP.md` §6 and `SYSTEM_INTERACTION_MAP.md` §4: the first wand free
  (the wand chose you), replacements priced — but only once there is a money income loop.

### C-9 · The Philosopher's Stone is a permanent buff stack — **medium (currently creative-only)**

- **Implementation.** `item/consumable/PhilosophersStoneItem.use`: removes every effect, then gives Regeneration II,
  Absorption III and Resistance for 5 minutes on a 5-minute cooldown.
- **Lore issue.** The Stone makes the Elixir of Life, which keeps its drinker alive but must be drunk regularly, and
  it turns metal to gold (PS ch.13). Neither is a combat buff.
- **Gameplay.** Only in creative. In survival it would be the strongest item in the mod.
- **Resolutions.** See `MAGICAL_ARTEFACT_STATUS.md` §5.1: one draught a day with a single life-sustaining effect, or
  hold it until there is a lifespan or gold economy to act on.

### C-10 · The Remembrall flashes on a timer — **cosmetic**

- **Implementation.** `RemembrallItem.isFoil` alternates every 400 ms, always.
- **Lore issue.** It turns red when its holder has forgotten something (PS ch.9).
- **Gameplay.** No.
- **Resolutions.** Make it static (honestly decorative), or glow for one real, cheap condition (for example, a
  spell-slot or skill point left unspent).

### C-11 · The Sphinx and the Jarvey do things canon never gives them — **low**

- **Implementation.** `SphinxRiddle.tick` gives nearby players Blindness and Nausea when the Sphinx has a target.
  `JarveyJinx.tick` gives Unluck and Weakness plus a fox screech.
- **Lore issue.** A Sphinx poses riddles and attacks when answered wrongly (GoF ch.31; *Fantastic Beasts*). A
  Jarvey hurls rude insults and hunts gnomes (*Fantastic Beasts*). Neither curses its target's senses.
- **Gameplay.** Yes, mildly (debuffs in combat).
- **Resolutions.**
  1. Sphinx: a riddle exchange as a signature behaviour (answer from the item in hand, or a chat response), with an
     attack on failure.
  2. Jarvey: insults (action-bar lines) and hunting gnomes; drop the debuffs.
  3. Or retag the abilities as ORIGINAL and rename them so they stop claiming a canon source.

### C-12 · Dragons have no spell resistance — **medium**

- **Implementation.** The ten dragon definitions carry no `spell_resist`, so one Stupefy takes hold on a dragon,
  while a troll needs two and a Graphorn three (`MagicResistance`).
- **Lore issue.** Dragons are "too strong and too magical to be knocked out by a single Stunner… about half a dozen
  wizards" (GoF ch.19). A Graphorn's hide is *more* resistant than a dragon's (*Fantastic Beasts*), so dragons
  should sit just below the Graphorn, not at zero.
- **Gameplay.** Yes.
- **Resolutions.**
  1. Add `spell_resist` to the dragons at a value that asks for about six stuns within a hide the Graphorn exceeds.
     This also gives dragons the damage heal-back, which is a balance decision (already open in
     `SYSTEM_INTERACTION_MAP.md` §4).
  2. Give `spell_resist` a separate field for enchantments (stun count) and damage (heal-back), so dragons can
     resist stuns without soaking damage. The conjunctivitis weakness in the eyes (GoF ch.20) could follow.

## 12. Uncertain — sources do not settle it

| Question | What the sources give | What the mod does |
|---|---|---|
| Gamp's Law exceptions beyond food | DH ch.15 names food as "the first of the five Principal Exceptions"; the other four are never named. Separately, canon says no spell raises the dead (GoF ch.36) and love cannot be manufactured (HBP ch.9) — but not *as* Gamp exceptions. | Four domains: food, life, love, information. **Information is fanon.** The lore messages should not claim the list is canon. |
| Unregistered Animagus penalty | Illegal (PoA, GoF). Wizarding World writing is widely quoted as naming Azkaban *(to verify)*. | A 10-Galleon fine |
| Unlicensed Apparition penalty | Illegal; no penalty stated | A 2-Galleon fine |
| Silver against werewolves | Folklore; canon uses silver in wound treatment *(to verify)* | Silvered weapons do extra damage to a transformed werewolf |
| Vampire sunlight weakness, turned vs born | Folklore; canon has blood-drinking and little else | `sunlight_weakness` on turned and born vampires |
| Which beings can conjure a Patronus | Not stated for goblins, elves, centaurs and others | Only Wizardkind, vampires and Veela |
| Centaur, merpeople and full-giant wand use | Not stated | None can use a wand |
| Revelio against the Hallow cloak | Xenophilius calls the Hallow's concealment impenetrable (DH ch.21), yet Moody's eye sees Harry under it (GoF ch.? *(to verify)*). Plain "Revelio" is a game spell. | Reveals anyone under any cloak |
| Accio, Leviosa and Depulso on living beings | Canon shows objects | They move mobs |
| Troll-whisker wand core | Source not identified | Shipped as a core |
| Firebolt Supreme | Source not identified | Shipped as a broom |
| Dragot | Not found in the sources checked; possibly screenplay or supplementary material | A foreign currency with a moving rate and forgeries |
| The merpeople description ("a song that almost drowned a champion") | GoF's lake task has a song and hostages, not a drowning song | Description text only |

## 13. What this audit recommends looking at first (no changes made)

1. **C-1 Patronus form.** It touches tone as well as lore, and a per-player form is cheap and safe.
2. **C-2 Unforgivable target.** It is a law players will run into.
3. **C-3 wins to transfer** and **C-4 Animagus form.** Each is a one-rule decision.
4. **C-12 dragon hides.** Pair it with the open balance decision.
5. Settle the *(to verify)* citations above before acting on them. For Gamp's Law, trim the claims in the lore
   messages rather than the mechanics.

Heritage-only items (C-5, vampire traits) can wait until those heritages become playable.


## 14. Rulings applied (2026-09-30)

The owner asked for every contradiction to be resolved, in an order of my choosing. Each resolution follows one of
the options in §11. Ordered as implemented:

| # | Resolution chosen | Where |
|---|---|---|
| **C-3** | Base wins to transfer = **1**. Hard-won woods and cores keep `extra_wins` (ash, blackthorn, phoenix +1; elder −1). Unicorn hair's faithfulness moved from an extra win to `transfer_bond_bonus -0.1`: it serves its winner reluctantly, so Draco's hawthorn/unicorn wand is won in one struggle, as in DH ch. 24. | `WandAllegianceRules.BASE_WINS_TO_TRANSFER`, `wand_cores/unicorn_hair.json` |
| **C-5** | The Giant heritage allows wands. `GIANT_FULL` and `GIANT_CLAN` carry `no_wand`; `GIANT_HALF` can use one (Hagrid, Madame Maxime). The magic source shows Hybrid. | `Heritage.GIANT`, `HeritageVariant` |
| **C-10** | The Remembrall no longer flashes on a timer; it is honestly decorative. | `RemembrallItem` |
| **C-6** | Killing a unicorn keeps its social cost (the slayer is marked and shunned, and takes 15 corruption) and loses the Weakness/Unluck "curse". The cursed life belongs to the blood, which is not implemented. | `creatures/unicorn.json` |
| **C-7** | A lit Hand of Glory gives its holder night vision and lifts Blindness and Darkness (so the holder sees through Peruvian Instant Darkness Powder). Nobody else is affected. | `HandOfGloryTickHandler`, `HandOfGloryItem` |
| **C-12** | `spell_resist` split in two: `resist_fraction` (how many spells at once an enchantment needs) and a new optional `damage_fraction` (heal-back, defaulting to the old value, so existing creatures are unchanged). Dragons: 0.83, which is 6 spells at once, with no heal-back. Graphorn: 0.86, which is 8 spells at once, above the dragons as FB has it, with damage heal-back unchanged at 0.6. | `SpellResist`, ten dragon JSONs, `graphorn.json` |
| **C-11** | The Sphinx speaks one of three riddles to nearby players while it has a target; its attack stays its ordinary melee. The Jarvey shouts insults. Neither lays debuffs any more. `duration_ticks` is still parsed but inert. | `SphinxRiddle`, `JarveyJinx`, lang |
| **C-1** | The Patronus form is drawn once from the character's own UUID over eight animals: the same for that character every time, and blind to heritage and blood status. Happiness no longer swaps the form. Every caster can conjure one. Forms already stored on players are kept. | `PatronusFormDeterminer` (+ `PatronusFormDeterminerTest`) |
| **C-4** | The Animagus form is drawn at the ritual from the character's UUID, among forms that have a datapack definition. Choosing a form (`/wandb animagus form`) is now operator-only. Existing Animagi keep their forms. | `AnimagusForms.innateFormId`, `AnimagusEvents`, `AnimagusCommands` |
| **C-2** | Unforgivables are filed where they land, not at release. On a person (a player, villager or trader, or an illager) the charge is the Unforgivable; on a creature, the Dark law (wand residue, witnesses, no life-sentence charge). A curse that hits nobody is no incident. Corruption is unchanged. | `MinistryTrace.onUnforgivableUse` and `isPerson`; `SpellCastService`, `SpellProjectileEntity`, `WandBeamSpellHandlers` (Crucio), `ImperioServerLogic` |
| **C-8** | The first wand from the trial is free: a new character has no money and cannot cast without one, and that is the one Minecraft compromise. Every later wand costs `ollivanderWandPriceKnuts` (default 3451 Knuts = seven Galleons) from the vault, all or nothing, while Gringotts is on. | `OllivanderPrice`, `ChooseTrialWandPayload`, `Config` |
| **C-9** | The Philosopher's Stone gives one draught of the Elixir of Life per in-game day. For that day the drinker's next death is refused (they are left on 2 health) and the draught is spent. No buffs, no cleanse. A Dementor's Kiss is not death. | `ElixirOfLife`, `PhilosophersStoneItem`, attachments `ELIXIR_UNTIL` and `ELIXIR_NEXT_DRAUGHT` |

**Not changed (§12 items).** The uncertain items stay as they are and remain owner decisions: Gamp's Law domains,
the unregistered-Animagus penalty, silver, vampire traits, the troll-whisker core, the Firebolt Supreme and the
Dragot.

**Tests.** `gametest/CanonRulingTests` has eight scenarios:

| Ruling | What the scenario checks |
|---|---|
| C-2 | a curse on a cow is Dark; on a villager, Unforgivable |
| C-3 | one defeat wins Draco's wand |
| C-4 | the innate form is stable and has a body |
| C-5 | half-giant yes, full giant no |
| C-7 | the holder sees and a bystander is unharmed |
| C-8 | first wand free, second seven Galleons, all or nothing |
| C-9 | one death spared, not two |
| C-12 | a dragon needs six Stunners |

Tests that pinned the old rules were updated to the new ones:
- `WandAllegianceRulesTest` and `WandTemperamentLoreTest`;
- `WandAllegianceTests` (the stun sequence now uses a phoenix wand; the Elder Wand scenario now expects the
  rival's wand to be won);
- `CreatureWildlifeTests` (the unicorn slayer is not cursed);
- `MagicResistanceRulesTest`.

**Mutation check.** The C-2, C-7, C-8 and C-9 rulings were each reverted and each broke its own scenario, then were
restored. Full suite: 177 required tests pass.
