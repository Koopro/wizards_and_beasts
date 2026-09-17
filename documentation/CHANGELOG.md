# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Heritage says who you are, not what you rolled (2026-09-17)

Heritage was a selectable race with a stat block: pure-bloods rolled POWER on a higher band than Muggle-borns,
a bitten wizard stopped being a wizard of any family, and the selection screen advertised Health ±, Speed % and
Armour ± like an MMO character creator. Canon says the opposite in every case.

- **Lycanthropy and the Obscurus became conditions.** `MagicalCondition` and `ConditionOrigin` sit alongside a
  character's heritage and lineage instead of replacing them. Remus Lupin is a half-blood wizard with a wand,
  a teaching post and a curse; being bitten now keeps the POWER roll, the training and the family, adds the
  condition's traits, and changes the body. `HeritageAPI.afflict` gives one and `cure` takes it away.
- **Saves migrate.** A Werewolf or Obscurial record loads as Wizardkind plus the condition, with half-blood as
  the lineage the old model never recorded (data version 2).
- **Blood status stopped being a power level.** Every lineage with magic rolls POWER on one band; a Squib keeps
  0–10 and no growth. Nothing on the selection screen makes a character stronger.
- **Traits in words.** `HeritageTraits` gives each trait a name and a sentence under Traits / Magical
  affinities / Special characteristics, and that is what the selection dossier and the character sheet print.
  Dead tags (`vault_access`, `divination_sight`, `enhanced_bond`, `dark_resistance`, `nature_speech`,
  `water_breathing`) are gone; the ones that stayed are read by something.
- **A character can begin with a condition**, chosen at creation next to heritage and lineage, never rolled by
  the randomiser, and refused where it makes no sense (an Obscurus in a Squib).
- **Three gated peoples gained real mechanics**: goblins pay no Gringotts commission and are always caught
  passing forgeries; a house-elf Apparates past wards by trait, as Dobby did inside Hogwarts; a star-reading
  centaur reads the moon's calendar and the weather off the night sky.
- **Six profession nodes removed.** A curse is not a career.

### Creatures behave like wildlife (2026-09-17)

**The bestiary fills by watching, not killing.** Seeing a creature opens its page; watching it calmly for half a
minute teaches you how it lives; feeding it, handling it or finding what it sheds lets you study it; seeing the
thing it is known for completes the page. Killing a creature only ever tells you that you met it. Every page now
says which Ministry division the creature falls under, what it eats, how to approach it, what it yields and how,
and what wizards make of it — and how much of that is canon.

**Nothing lore protects comes from a body.** Unicorn hair is shed where unicorns graze, or combed loose by someone
the unicorn lets near. A phoenix is reborn from its ashes when it would die, and gives feathers to the person it is
loyal to. Demiguise hair is found where it rests, or given up when you reach one it did not see coming. Mooncalf
dung is left where the herd danced. None of them drops anything when killed — and a unicorn's killer is marked for
it.

**The creatures act like themselves.** A demiguise vanishes when you look straight at it and steps aside when you
walk straight at it. A unicorn keeps away unless you come quietly, empty-handed, and with a clear conscience.
Mooncalves stay burrowed until the full moon, then gather and dance. A bowtruckle defends its tree. A niffler goes
for gold first. A phoenix weeps healing tears for its friend.

**Werewolves are people most of the month.** A werewolf is only abroad under a full moon and is gone by dawn. Its
bite passes the curse to a human: you keep everything that makes you you, and the next full moon takes you.

### The Ministry has to find out (2026-09-17)

The Trace used to mean "cast a forbidden spell and it is on your record", and nothing else was law at all. The
Ministry now learns of magic the way it does in the books: the Trace feels magic around underage wizards,
Muggles who see something are reported to the Obliviators, and officials who watch report what they saw. An
adult casting alone in the wilderness is known to nobody.

**Children are under the Trace.** Characters can have an age, and until seventeen magic outside Hogwarts earns
a warning letter, then a summons. Magic at Hogwarts is fine, and so is magic in a life-threatening situation —
the Wizengamot will say so.

**Muggles notice.** A light in front of a villager is tidied up by the Obliviators. A spectacle in front of a
crowd opens an inquiry, and if enough of them saw, it finds you. Dark magic or a dangerous beast in front of any
Muggle goes to the Auror Office.

**Cases take time.** Record, warning, investigation, summons, Aurors: each rung is a letter that says what
happened and what comes next. A summons is answered with `/wandb ministry summons answer`. Aurors look where you
were last reported, not where you are.

**Hearings give reasons.** A ruling can dismiss the charges, warn, fine, hold your wand for a while, or refer you
to Azkaban, and the letter lists why. A hearing also examines your wand — an Unforgivable nobody saw can still
come out there.

**Not all illegal magic is equal.** Lumos, Accio and Reparo are everyday magic; Stupefy is restricted; Crucio,
Imperio and Avada Kedavra are crimes in themselves. What the law thinks of each spell is datapack data.

### A wand answers to whoever won it (2026-09-17)

Wands had three different ideas of who owned them — a stack master, a player attachment and a disarm log —
and they disagreed. A wand is now one record on the stack: its master, how far it has settled into that
master's hand, and who has been challenging for it. Casting with it reads the same record every time.

**Another wizard's wand works, poorly.** It casts at reduced power and a longer cooldown, and the tooltip
says whose it is. Hawthorn turns on strangers outright.

**You win a wand by beating its master.** Disarm, stun or kill them — how many times depends on the wand's
wood and core. Picking a dropped wand up wins nothing, and neither does looting a wizard the world killed.

**The Elder Wand has to be won.** It no longer belongs to whoever crafts or first holds it. Defeating its
master hands it over wherever it lies, and only its master gets its full power.

**Woods and cores have temperaments.** Their flat damage bonuses were narrowed; instead dragon heartstring
changes hands easily, phoenix feather is slow to trust, rowan dislikes dark magic, thestral hair only
fully answers someone who has seen death. Each wood and core carries notes separating canon from gameplay.

**Wands can break.** Blasts wear a held wand down, a broken wand backfires, and only the Elder Wand's Reparo
mends one.

### A wand hold is one cast, whatever the network does (2026-09-17)

An audit of the whole cast path — press, hold, channel, release, and every way a hold ends — against
duplicated, late, lost and forged packets, death, respawn, dimension changes, reconnects and a second
player. The release protocol itself held: the server reads a client's packets in wire order, so the
session's one release token already made duplicates and stragglers worthless. What broke was the
lifecycle around it.

**Dropping the wand mid-hold left the spell running.** Vanilla empties the wand stack before it stops
the use, and then skips the item's own teardown for an empty stack. A Leviosa target hung in the air,
the beam never ended for anyone watching, and the next hold picked up the stale channel. The server now
reconciles every player's hold with vanilla's item-use state once a tick, from state rather than from a
hook that can be skipped.

**Switching spell under a hold changed what the hold cast.** A long Lumos hold, switched to Protego just
before letting go, came out as a fully charged Protego — Bombarda and Flipendo too. A hold now belongs to
the spell it was pressed for: switching ends it (the wand starts a fresh hold if the button is still
down), and a release that races the switch casts nothing.

**A beam ran on after its own release would have been refused.** The channel checked three of the
release's rules. A silenced, drunk or wand-barred caster held Crucio for as long as they liked, and since
the refused release stamps no cooldown it cost nothing; Finite's interrupt was undone the next tick. The
channel now asks exactly what the release asks, bar the random rolls.

**A use packet sent into a running hold replaced its session**, forgetting that the hold was sustaining
a spell clash, so letting go of a won clash could cast after all.

**A dead player's late input no longer acts:** an ability press (Obscurus Grasp and Surge fired around the
corpse), an Imperius command, and a Patronus aura that kept pulsing around the body.

21 new game-test scenarios (84 total), each mutation-checked against the defect it guards: lifecycle
(respawn, dimension change, logout and reconnect, hotbar swap, dropped wand), races and latency (two full
presses in one tick; cast A, release A, cast B, duplicate A, release B; a release pair split across
ticks; a lost release; a re-sent use packet; spell switches), Avada Kedavra's server-driven release
against a real kill, two players whose releases and packets stay their own, an observer told of a beam
and of its end, and the after-death inputs. Found and not changed — interrupted channels cost nothing,
Imperio's mob tick is never registered, `/wandb magic spell learn` needs no permission — are in
`KNOWN_ISSUES.md` §8c.

### The Shield Charm has weight (2026-09-16)

**The charge is something you can see and hear climbing.** Under the four chimes there is a hum whose
pitch and volume rise with how far the hold has come towards the next shape, motes circle the wand in
that shape's colour, and — for the caster alone — the screen edges take a vignette in the same colour
that closes in as the charge climbs and flares on each threshold. The wand in your hand takes the
colour too. Sneak while a dome is ready and the footprint it would take is drawn on the ground. All of
it respects `reduceScreenEffects`: with it on, the sounds and the particles still carry the whole
picture.

**Thresholds** (novice, and 15%/30% faster at Proficient/Mastered): Totalum at 8 ticks, Maxima at 18,
Horribilis at 32 — a tap is 3-5 ticks once release latency is counted, so a flinch can never buy a
bubble, and the whole climb still fits inside one exchange of a duel.

**One spell, four shapes, chosen by how long you hold the wand.** A tap is *Protego*: a disc held out in
front of you that raises instantly, covers the arc you face and recovers faster than the spell's own
cooldown. Hold longer and it becomes *Totalum* (a bubble that walks with you and shelters whoever stands
close), then *Maxima* (a wide dome), then *Horribilis*. Practice raises the ceiling and shortens the
climb; Horribilis also needs three unlocked Dark Arts nodes, because warding a curse means understanding
it. The wand says which shape it has reached as it reaches it — a rising chime, motes in the tier's
colour, and the incantation on the action bar — and tells you which wall you hit when it can go no
further. Totalum, Maxima and Horribilis are **not** separate spells; the three `coming_soon` stubs that
claimed they were are gone.

**A shield is an absorb pool, not a hit counter.** Every spell it turns and every blow it takes for
someone spends integrity, which scales with the shape, your practice and the cast's own power. Damage
the pool cannot cover still lands. Over the last third of the pool the ward sings, reddens and thins, so
a shield about to go looks like one.

**Sneak as you release a dome to plant it.** A planted Maxima or Horribilis stays where you set it —
deeper, longer-lived, and standing for everyone inside it even after you walk out (up to 48 blocks).

**Running out of time and being broken are different endings.** Time runs out: it fades, quietly, and
costs nothing. Broken: glass, a shockwave that throws whatever was leaning on it, a stagger, and a recast
lockout stamped on the Protego cooldown that grows with the shape you lost. A broken Horribilis also
lashes its caster — Dark magic swallowed whole has to go somewhere.

**Horribilis now has Dark magic to actually ward against.** Its clause used to read "a Dark-family spell
projectile", which in this game is almost nothing: the Killing Curse is unblockable by canon, Crucio and
Imperio are beams, and the rest of the Dark list is self-cast. A spell now counts as Dark if its family
is Dark *or* its category is the Dark Arts, and — the half a player actually meets — so does a blow from
anything in the `dark_creatures` tag: dementors, the obscurus, the undead. Horribilis pays a third of the
integrity for those, every lesser ward pays a quarter more, so a Horribilis holds a graveyard roughly
three and a half times as long as a Maxima with the same pool.

**What Protego no longer does:** cancel every damage event while it is up. Falling, drowning, starving,
burning and poison are not somebody attacking you, and a charm that answered all of them was the best
survival tool in the mod. It answers attacks: an attacker, a projectile, a blast.

**Fixes underneath.** Protego never recorded a successful cast, so its proficiency was pinned at NOVICE
forever and Expecto Patronum (which needs Protego at Proficient) could not be learned. The old ward
marked players with a saved entity tag whose expiry lived only in memory, so a crash with a shield up
left a permanently invulnerable player — the tag is gone and old saves are swept at login. Bolt
interception is swept over each bolt's step instead of sampled, so fast spells no longer tunnel through a
ward, and a deflected bolt is no longer re-deflected until the shield's pool is gone. A spell stopped at
a body now has its stun, disarm and effects stopped with it, rather than only its damage.

Covered by `ProtegoRulesTest` (26 cases over the ladder, the pool and the swept geometry) and four game
tests: a dome spends integrity instead of health, the disc has a back, the ward ignores the weather, and
a breach locks the recast. Each was checked against a mutation that should break it.
### Spell clashes are held (2026-09-15)

**A lock is a duel now, not a firework.** When two bolts lock, a beam runs from each caster's wand tip to
the joint, and the lock lasts as long as both keep holding. A bolt fires when the button comes up, so both
casters have let go by the time their bolts meet: each has a second and a half to press and hold the wand
again, and is told so on the action bar. That hold feeds the lock and nothing else — no channel beam starts
under it, and letting go casts nothing (a new `CastReleaseGate.CLASH_HOLD` verdict spends the release).

**Whoever lets go loses.** Letting go, or never picking the lock up, fires the other caster's spell from the
joint at you as an ordinary bolt, so it lands with its full effect and can still be blocked. While both hold,
the stronger cast pushes the joint along the line between the wands, and pushing it onto a wand loses that
caster the lock the same way. Both letting go breaks the lock with nobody hit. There is no time limit: an
even duel lasts as long as the two of you do. The old timer (1.5 to 2.5 seconds) is gone.

**The joint follows the casters.** It sits on the line between their wands, so walking moves the lock with
you; casters who get closer than about three blocks or further than 48 break it.

**Only bolts flying at each other lock.** Clashes used to need nothing but two different casters, so two
allies firing at the same enemy could lock with each other on the way in. The bolts must now be heading more
than a right angle apart.

**`/wandb magic spell clash hold <players> <true|false>`** (admin) pins a player's side of any lock as held,
because one person cannot hold right-click in two game windows. Pin one side from the console before firing
and hold the other for real.

Covered by `SpellClashRulesTest` (grace, letting go, never holding, breaking, pushing onto a wand, head-on)
and three new game tests: the caster still holding wins, nobody holding breaks the lock, and releasing a lock
hold casts nothing while the releaser is hit. Disabling the gate, or making holds never count, fails them.

### Spells clash in mid-air (2026-09-12)

**Two bolts that meet now fight.** They used to fly through one another, with a single hard-coded
exception: Expelliarmus, and only as a dozen particles in one frame before both spells blinked out.
Any two projectiles from different casters whose paths come within four tenths of a block now lock, and what
they leave behind is an entity rather than a one-frame puff — `SpellClashEntity` holds for one and a
half to two and a half seconds, spitting sparks in both spells' colours, crackling, and creeping
towards whichever wizard is casting weaker, so the stronger cast visibly wins the push. Evenly matched
wizards hold the lock longest; a mismatch breaks sooner, because the point of the drift is that
somebody eventually loses it.

**The Killing Curse still meets only Expelliarmus** — the pairing that causes the wand-lock in the
first place — and two Killing Curses lock each other, which is Priori Incantatem itself. Nothing in a
clash deals damage: the two spells already cancelled when their bolts were destroyed.

**The lightning is the beam system's own.** `client.beam.Lightning` already draws a jagged bolt that
holds its shape for a couple of ticks and then snaps to a new one; a clash runs two to four of them at
once between the points the two spells came from, each in one spell's colour over a white core, with
the count following the same performance preset as the rest of the spell VFX. Nothing new goes on the
wire — the bolts are seeded from the entity id and the tick, and the camera kick comes free from the
impact burst the clash already sends.

**Bolts cannot slip through each other.** A bolt moves its whole step in one go, and two flying at each
other close about three blocks a tick, so a check on where they ended up missed nearly every head-on
pair. The clash is decided along both bolts' paths through the tick, at the moment they are closest, by
whichever of the two moves second.

**`/wandb magic spell cast <spell>`** (admin) fires a projectile spell from whoever runs it, through the
real cast past its gates: wand, allegiance, proficiency and the rest of the modifier pipeline apply, and
a wand can still misfire; knowing the spell, cooldowns, requirements and Gamp's Law are skipped. Nobody
alt-tabbing between two clients can line two casts up in one tick, and
`/execute as @a at @s run wandb magic spell cast stupefy` does. The game test
`spell_clash_opposed_bolts_lock` uses it to fire two casters at each other from 6.64 blocks, a range at
which the old position check never saw the bolts meet.

### Aguamenti fills brewing cauldrons, waters crops, and stops drowning its caster (2026-09-11)

**Holding it at nothing poured water on your head.** When the jet hit nothing, the source-water hold fell
back to the first open cell along your aim — one block from your eyes — so a long hold at open air put a
source over your own head. The aim also saw water, so every source it placed became the next thing the
jet hit, and the next source landed a block nearer, until one was in your face. Now a jet that hits
nothing places nothing, the aim looks through water, and no source is placed in or above the caster.

**Brewing cauldrons stayed dry.** A brewing pot keeps its water on the block entity, and the spell only
recognised vanilla cauldrons by blockstate, so the jet stacked a water block against the pot instead. It
fills an idle, empty pot now, the way a water bucket does.

**Crops stopped the jet above the soil.** The aim used block outlines, which a crop has and the drawn beam
ignores, so the farmland under a field was never reached and a held jet dropped a source on the crops.
The aim is now the beam's own collision ray, and it waters the farmland under them.

### Wingardium Leviosa throws only when you tell it to (2026-09-11)

**Letting go threw whatever you were holding.** Every end of a Leviosa hold ran through one teardown,
and that teardown flung the target along your aim at 1.6 blocks a tick. Releasing right-click shot it
forward, and so did switching spell, dying or logging out. Nothing lifted could simply be put down.

**Releasing drops it now, and the attack key throws it.** Left-click (or wherever attack is bound)
during the hold sends `SpellLeviosaThrowC2SPayload`. The server throws the held target and lifts
nothing more for the rest of that hold, because the target is still the cached one and the next
channel tick would lift it straight back out of the throw. One hold is still one cast. The click is
read in `ClientTickEvent.Pre` on purpose: while any item is in use, vanilla's `handleKeybinds` drains
attack clicks and discards them, and it runs before `Post`.

### /wandb magic spell learn taught you a spell you could not cast (2026-09-11)

**The command stored the wrong key.** It resolved the spell, then wrote player data under the raw
argument you typed instead of the spell's canonical id. `Spells.byId` accepts a bare path, a
namespaced id and a legacy namespace — three spellings of one spell — while the known-spell set is a
plain string set that normalises nothing. So `/wandb magic spell learn alohomora` stored `alohomora`,
every reader in the mod looked up `wizards_and_beasts:alohomora`, and the spell was learned and
uncastable. `forget` could not remove what `learn` had written, and `info` read cast counts off a key
nothing writes. All three now canonicalise after resolving, which is what the skill web already did.

**And the completions were unparseable by their own command.** The spell argument accepts a word, and
a word has no colon in it; the suggestions were namespaced ids, so accepting one put a red line under
it. They are bare paths now — what the parser takes and what the resolver expands — falling back to
the full id for a spell in another namespace.

Found by the smoke test written while retiring the spell-teacher package, not by the retirement.

### Nobody sells spells (2026-09-10)

**The spell teacher was a shop.** Walk to a lectern, open a catalogue of every spell you were
eligible for, pay 58 Knuts. It undercut four things the mod already had — skill-web keystones that
grant spells as earned progression, proficiency as the mastery curve, and the heritage, profession
and mastery-tier gates, all reduced to shelf labels on a price list. The screen, both packets, the
offer builder and the Knuts charge are gone.

**Spells are written down, and you have to find the writing.** A `spell_source` component holds one
spell id, and The Standard Book of Spells carries it — the first canon stub promoted out of the
behaviourless catalogue it was registered in. Holding right-click reads it for three seconds and
teaches what is written inside. Eligibility runs twice, once to open the read and once to finish it,
because three seconds is long enough for a skill node to have granted the spell already or for the
book in hand to have been swapped. Nothing about **who may learn what** changed: every gate is where
it was, and refuses through the reading path instead of by being left off a list.

**Books turn up in libraries.** Village houses (0.35), stronghold libraries and bastion treasure
(0.7), woodland mansions and pillager outposts (0.6), each with its own pool — a village shelf holds
Lumos or Alohomora, a stronghold holds Expelliarmus or Confringo. The hidden wizarding cache carries
a guaranteed one plus blanks and ink. Which spell is rolled, never chosen: a found book you pick the
contents of is a catalogue with extra steps.

**And a torn page turns up in ruins.** Mineshafts, dungeons, pyramids, temples, sunken ruins and
shipwrecks hold a single leaf out of somebody else’s spellbook, and it does not survive being
studied — spent on a successful read, and only on a successful one, because destroying a page a gate
has just refused would mean an unmet requirement costs you the only copy you had of the spell you
cannot learn yet. A textbook is lendable; a scrap is not.

**The lectern kept its job's other half.** Same block, same id, same model, now the **Study Lectern**:
a wizard who knows a spell can spend an ink bottle to write a blank book into a copy of it. That is
what makes the first found book matter to a whole server rather than to one player.

**Knowledge stopped being a discount.** Its only consequence was cheaper tuition, and there is no
tuition. It is now a study rate — 1.00 at Knowledge 0, 1.50 at 100 — multiplying the proficiency a
landed cast earns. The floor is 1.0 and never below: a stat nobody trains directly must not punish
the player who has not ground out the four systems it derives from.

**Known gap:** this removed the only coin sink that was on by default. Buying a wand and the skill
respec fee are what is left. A second sink is wanted, and it must buy an object or a service.


### Vampires stop eating (2026-09-07)

**Nutrition is a heritage question now, not a universal one.** Every body in the mod ran on the same
hunger bar, which is a claim about wizards that is false about half the heritage roster. A heritage
resolves to a `NutritionPolicy` — vanilla hunger, blood, or nothing — and the three sites that care
(the HUD, the food events, the tick) ask that one question instead of naming a heritage each.

**A vampire has ten blood drops where their drumsticks were.** Same 9x9 sockets on the same 8-pixel
pitch, filled and halved by the same loop vanilla runs for hunger, because a bar drawn a different way
from vanilla's bars reads as a different mod's HUD however good its colours are. They dry to a dark
withered red when the pool is nearly gone, and shake a pixel the way a starving player's drumsticks do.
The bar fills by right-clicking a living creature with an empty hand: a bite is worth more from a
larger animal and more again from something that fights back, the creature is left weakened and empty for
ten seconds afterwards so a penned herd is not a buffet, and no single feed can fill the pool, because a
thirst you can always top up is not a thirst. Crouching hands the click back to vanilla, so a vampire can
still mount a horse.

**Food does nothing.** The animation still plays — a vampire who cannot lift bread to their mouth reads
as a bug — and then the bar is put back exactly where it was, and they are told it tasted of ash.

**Running dry costs, in stages.** Sated, thirsty, parched, starving: weakness in the third band, weakness
and slowness in the fourth, and attrition damage in the last few points of the pool. Two of the penalties
are not applied by the mod at all. The hidden vanilla food level is driven from the blood pool, so
vanilla's own rules take the sprint away below a third and stop natural regeneration below ninety
percent, and there is only one system deciding either. It never reaches zero, so vanilla never starts
starving them on a second clock of its own.

Sprinting costs blood on top of the passive drain. Dying settles the pool to forty percent rather than
refilling it, so a graveyard is not a meal. Vampires who predate all this arrive with a full pool rather
than in a coma. `/wandb player heritage blood get|set|fill|drain` for testing, and the whole economy —
pool size, drain rate, band floors, feed yield, cooldowns, whether players are on the menu — is config.


### The Chocolate Frog gets a frog, and the cards get faces (2026-09-03)

**An escaped frog is a frog now.** It was a dropped item with a mind of its own — the right call for the
mechanic, because an `ItemEntity` already syncs, already despawns and is already catchable by walking
into it, and none of that had to be written twice. It just read wrong: a spinning flat sprite is the
universal signal for loot on the floor, and the joke is that this one is running away from you. It has
its own moulded-chocolate model, points where it is going, stretches into a hop and squashes on landing,
and pulses its throat while it sits there waiting for you.

**Twenty-four wizards, twenty-four cards.** There were eight names sharing one picture, which is the one
thing a collectible cannot be. Every card is now its own portrait — Dumbledore's half-moons over the
silver beard, Lockhart's teeth, Morgana under a black hood, Flamel among stars — with a foil frame and a
pip count for its tier, and a set number on the back of the tooltip. They are not equally likely: a
Dumbledore is twelve times as easy to pull as an Andros, and the two legendary cards together turn up
about once in eighty, because a collection nobody has to chase is just a list. The stack takes the
wizard's name and the tier's colour, so a chest of cards reads as a collection instead of as twenty-four
identical stacks that have to be hovered one at a time.

**And a card is held like a map.** Vanilla reaches that two-handed pose through an
`instanceof MapItem` check no mod item can satisfy without becoming a map, so the pose is reproduced and
the default hold cancelled: both hands, flat in front of you, tipping toward horizontal as you look down.
Same rule as vanilla's — only when the other hand is free, because a card spread across both hands would
hide the wand in the off-hand.


### A debug panel beside the thing, and a debug command per feature (2026-09-02)

The cauldron dump was thirty lines of chat. Reading it meant losing the conversation, and by the time you
had scrolled through it you were no longer looking at the pot it described. **It is a small box beside
the cauldron now.** Turn debug on, look at a pot, and the phase, the heat, the timer, the contents and
what each gesture would do are drawn next to it, updating four times a second while you watch.

It is not only cauldrons. Anything you look at answers: the wandmaker's bench with its counted enhancers
and its tier score, a Floo grate with both its own record and the network's — side by side, because the
two disagreeing is the shape of every "my address does not work" report — a broom's live speed against
its own ceiling while somebody is flying it, a beast's traits and *your* bestiary tier for it, and a
player summarised by every feature at once. Anything with no inspector of its own still answers with its
blockstate and its saved tag, because a panel that says nothing over an unclaimed block is
indistinguishable from a panel that is broken.

**The client never names what it is asking about.** It asks "what am I looking at"; the server does its
own pick from the player's own rotation and replies about whatever it finds. The obvious design — the
client raycasts and names the block — would have handed anyone with a packet editor the ability to read
any block entity in the world by position, from any distance, through walls.

**And thirty features had debug output for seven of them.** Not the unimportant ones — the ones nobody
had recently had to debug. There is now a section per subsystem, living beside the subsystem it reports
on: spells, wands, skills, abilities, heritage, transformations, stats, ministry, standing, Gringotts,
O.W.L.s, Floo, pockets, brooms, armour, bestiary, brewing and the module switches over all of it.
`/wandb debug feature <name>` prints one, `feature all` prints every one, and each takes an optional
player because "what does the server think is going on with *them*" is the question that actually gets
asked. A section that throws is reported as a failed section rather than taking the dump down with it —
this is diagnostic code run against broken state by definition.

The reports are built once as data and rendered twice, so the panel and the command cannot drift apart.

**Reading the state was only half of it.** Nearly every subsystem already had setters — learn a spell,
unlock a node, register a hearth — and none of them answered "I want to test brewing", which took eight
commands you had to already know. `/wandb debug dev` answers it in one: `open` unlocks whatever is gating
a feature, `kit` hands over the items it needs, `reset` puts it back, and `dev setup` does open-then-kit
for all fourteen. Every action reports what it changed *and what it skipped*, because a command that
silently writes fourteen fields is one you cannot trust.

The kits read the game rather than a hard-coded list: brewing hands you the ingredients the currently
loaded recipes actually name, brooms give one of every defined variant, abilities grant from the DEBUG
source so `reset` can take back exactly what it gave and leave a heritage grant alone. Some of what they
do is a deliberate refusal — the skill kit hands over the point budget rather than unlocking every node,
because unlocking them all would make prerequisites, costs and tree gates untestable, which is most of
what there is to test about a skill web.

**And three things had no way in at all.** The vault could be read and never written, so "does this cost
the right amount" could be observed but never arranged; it now has `/wandb player vault`. Dark corruption,
the Dark Mark, mental stability, resolve, happiness, love protection and Cruciatus exposure were written
by gameplay and settable by nothing — every one of them a threshold, and the only way to reach one was
the intended way, which for Cruciatus exposure meant being tortured for several real minutes.
`/wandb player condition` sets them. The Floo arrival cooldown can now be cleared, because waiting it out
between attempts was most of the time cost of testing travel.

### The moon takes werewolves now (2026-09-02)

The mod shipped ten heritages, three werewolf variants all tagged `moon_sensitive`, a `werewolf_wolf`
form with its own rig and animations, and a transition config for entering and leaving it — and no
moon-phase code anywhere. `getMoonPhase` had zero hits across the whole source tree. The signature
mechanic of the mod's signature heritage had no implementation, and the form was reachable only through
an admin command.

**A werewolf under a full moon is taken by it.** Moonlight has to actually reach them: standing under
open sky banks exposure, standing under a roof loses it faster than it was gained, so a cellar is a real
defence and a slow walk home is not. Once enough has soaked in the change begins — bones moving, the body
held still, and then the wolf, with a howl. Gear a wolf cannot wear comes off into their own inventory.

**And then it is not their character any more.** An unmedicated werewolf is a passenger: the wolf hunts
on its own, going for people first, then villagers and the golems standing in front of them, then
animals, and only then whatever hostile happens to be nearby. It sticks to what it is chasing until that
thing dies or escapes, and only a genuinely more interesting kind of prey pulls it off. Being hurt winds
it up; a wound-up wolf is faster, notices more and bites more often. The player cannot open a bag, use an
item, break a block, cast, or change back. Dawn gives it all back, along with the exhaustion of a night
spent as something else.

**Wolfsbane does not cure it.** Canon is emphatic that Lupin still becomes a wolf every month, so the
potion is honest about what it changes: the shape is still the wolf's, the mind behind it is yours again.
Drink it mid-night and the wolf is handed back to its player within the second; let it run out mid-night
and it is taken away again just as fast, after five seconds of warning that it is failing. A medicated
werewolf is *offered* the shape at dawn rather than stripped of it, and keeps it until they give it back.
Aconite steam hangs on anyone who has drunk it, visible to everyone around — on a shared server, whether
the werewolf beside you took their potion is something you want to be able to see.

**The no-hands rule is one rule now, and it always was supposed to be.** Animagus, Obscurial and the new
werewolf each carried a near-identical wall of event cancels, and they had already drifted: the Animagus
wall blocked right-clicking an item but never touched the spell-cast packet, so a wizard in a cat's body
could still cast from the spell wheel — the one thing "a beast holds no wand" was supposed to mean. The
prohibitions are now named once, enforced once, and declared by each system as a set. **Animagus stays
voluntary**: it takes the beast's hands, never the door out.

A transformed body also gets its senses back. Night vision no longer strobes — it was being re-applied on
a 40-tick timer, permanently under vanilla's flash threshold, which is why cat form was unpleasant to
play in the dark — and beasts that hunt by nose now smell what they cannot see: living things nearby
outlined through walls, for that player alone.

Four lifecycle bugs went with it. Logging out during any transformation left the player **permanently
invulnerable**, because the transition borrowed invulnerability and only the completion path gave it
back; the cleanup method for that case had been written and never called. Dying as a cat, or as a wolf,
respawned you still shaped like one with the transformed flag already cleared underneath — a body no
toggle could get you out of.


### Potions look like what they are, and cauldrons look like what is in them (2026-09-02)

Every potion in the game was called "Brew" and rendered as the same purple vial. Each of the fourteen
brews had carried its own name and its own colour since the brewing pillar was written; the bottle item
read neither. A bottle now takes the brew's name, wears the brew's colour, and carries its flavour line
as a tooltip — including brews a datapack adds, because the colour comes from the definition rather
than from a texture. The flavour text also became translatable on the way past: it used to be raw
English baked into a datapack file that no resource pack could reach.

**A cauldron can be empty now.** It never could before. The pot was a solid cube with a texture stretched
across its opening, so there was no inside for anything to be in or absent from, and the one tinted face
was its lid — invisible unless you stood directly over the block and looked down. Placed cauldrons are
drawn by a rig instead: the pot is hollow, and the liquid in it is a real surface that appears when you
fill it, thickens and flecks as ingredients steep, boils and steams while a brew works, settles pale when
it is ready, and sinks into grey sludge if it curdles off the heat. All three metals share the rig and
differ only in their sheet.

The idle particles that used to be the only way to tell a full pot from an empty one are back to being a
flourish at about a third of their old rate. They were tuned loud to carry a job the geometry can now do.

**The wandmaker's bench works while there is work on it.** A blank or a core on the bench sets the lathe
turning, the treadle pumping and the tools rattling on their rail; an empty bench settles to a slow idle
with a wood shaving swaying off the front edge. Everyone nearby sees it, not only the player with the
screen open.

### Apparition you can see and hear (2026-08-29)

Apparating was silent theatre. A wizard winding up stood perfectly still, and the crack at either end
was the ender pearl's teleport sound played at a fixed volume — the same noise a thrown pearl makes.

A wizard gathering themselves to Apparate now visibly does it: motes are drawn in toward them, tighter
as the moment approaches, and they turn on the spot — slowly at first, a full turn by the time letting
go is clean. Everyone nearby can see it, which is the point. An anchored jump takes three and a half
seconds and a single hit ends it, so the wind-up was always meant to be something bystanders could
watch coming.

The crack is its own sound now, and there are three of them. A practised wizard's is quieter and
carries less far — down to eight blocks from thirty-two at the top of the curve — and changes character
near mastery. A house-elf's is higher and snappier. All of this was already being worked out on the
server and sent to everyone in earshot; nothing was playing it. Both ends of a jump twist as they go,
and a splinch tears audibly where it happened.

**Apparating off a broom works.** It used to put you at the destination for a fraction of a second
before the broom yanked you back, having already charged you the cooldown. You now let go of whatever
you are riding on the way out, and anything riding you is put down first. Apparating out of a fall no
longer lands the fall on you at the other end.

**You can Apparate onto water.** Previously a lake was not somewhere you could arrive, because nothing
solid was under it. Lava is now refused outright rather than being treated as a perfectly good landing.

**A long journey turns the stomach** — a couple of seconds of it, past sixty-four blocks. House-elves
are unbothered.

The whole thing is soot and deep violet now rather than ender purple, so it cannot be mistaken for a
pearl or for the Floo's emerald column at a glance. There is a gathering whoosh as a wizard begins and
a second, higher one the moment letting go would be clean — the timing is now audible as well as
visible, which matters if the wizard is facing away from you. Ash hangs where they left and where they
arrived for a second afterwards, so walking in late still tells you what happened. A wizard compresses
very slightly as they go, and your own view widens for a fraction of a second when you Apparate
yourself. That last one respects the reduced screen effects setting, like everything else that moves
the camera.

**Being hit while you Apparate.** It used to be a light tax: one hit during an anchored hold cost four
hit points and an item, and you arrived anyway. Apparition is still usable under fire — that is
deliberate, and escaping by it is meant to be an option — but it can no longer be done cleanly. Any hit
while you are gathering guarantees a Splinch. Holding an anchored jump makes it at least a bad one, and
so does a second hit. Taking somebody with you while being hit loses the journey outright, for both of
you.

**A splinch no longer strips your armour.** It was reaching into the four armour slots and the off-hand
alongside the pack, so a bad jump could tear the robes off your back or the wand out of your hand. It
takes what you were carrying now, never what you were wearing or holding. Packs can also mark
individual items as things a splinch will never take, with the item tag
`wizards_and_beasts:splinch_immune` — meant for quest items and one-of-a-kind rewards on servers where
losing one cannot be undone.

**Apparating hungry is harder.** Below six hunger points it costs about as much composure as being
careless on your feet. And a jump that ran its whole course only to find nowhere to arrive now costs a
second and a half before you can try again — refusals at the gate still cost nothing, because being
told no is not something to be punished for.

**Apparition is its own module now.** It used to ride on Player Abilities, so switching it off meant
switching off Legilimency and the Animagus form with it. It ships in the same state it was already in,
so nothing changes for an existing world — an operator simply has a switch that was not there before.

There is an Apparition chapter in the Ministry Handbook: the Three Ds, the moment to let go, what the
crack tells the people around you, where you may not go and why, and a page on splinching. Two
advancements go with it — one for arriving somewhere, one for leaving part of yourself behind.

The two Ministry wards that ship with the mod — Hogwarts and the Gringotts vaults — were the last raw
English sentences in Apparition; they are proper translation keys now.

Splinched belongings are also left where the wizard actually started rather than wherever they had
wandered to while holding the charge, and beginning a jump while facing open sky now holds patiently
instead of quietly refusing.

### Brooms you can tell apart in the inventory (2026-08-28)

Every broom had its own icon drawn and sitting in the mod — and none of them were being used. All
eight items were showing the plain broom's sprite. They now show their own.

A Firebolt is a dark needle with a red cord; a Nimbus is slim with a bright collar; the Oakshaft 79 is
a thick handle under a bundle half again the size of anything else. Brooms are also held in the hand
properly now, at an angle, instead of flat like a book.


### Every broom tuned to its own spec (2026-08-28)

The seven brooms now each fly to their own numbers rather than to a tier's. A Cleansweep wanders and
shudders under boost and forgives a crash; a Comet is unremarkable and steady; the Nimbus pair hold a
line, the 2001 a little harder than the 2000. A Firebolt is twitchy at speed and punishing into a
wall, and the Supreme is the same broom with the shake taken out of it.

The Oakshaft 79 keeps its momentum better than anything else in the game and is the gentlest thing to
crash after a school broom — it is a log, and logs do not mind.

Nothing about how fast any broom goes has changed.


### Brooms with a bit of noise about them (2026-08-28)

Brooms now sound like themselves and leave something behind. A Cleansweep kicks up dust; a Nimbus
throws gold sparks off its collar; a Firebolt trails embers that rise and wink out. The trail comes off
the twigs now rather than out of the rider's back, it starts earlier, and it thickens the faster you go
— a boost always shows, even from a standstill.

Mounting, dismounting, scraping a wall and properly crashing all have their own sound. A crash throws
broken twigs, and tells you what you have done to the thing you are sitting on.

**Broom polish finally does something.** It was an item with a tooltip describing behaviour it did not
have. A tin now mends a quarter of a broom and leaves the handle slick for twenty minutes, and a slick
broom holds a line noticeably better. Use it with the broom in your other hand, or just right-click a
broom lying on the ground.

**And a broom worn down to nothing will not take off.** It used to fly on a single point of durability
and break again immediately. Now it tells you it is in no state to fly — and a tin of polish is enough
to change its mind.


### Four kinds of broom, not eight speeds (2026-08-28)

They had different numbers. They still flew like the same stick.

**School brooms look after you.** A Cleansweep will not let its boost run away with you — it lurches,
complains, and gives you a fraction of what a racing broom would. It stops when you let go, it banks
gently, and it is the most forgiving thing in the game to crash. What it will not do is hold a line.

**Racing brooms lock on.** Take your hands off the stick on a Nimbus and it holds the heading a
Cleansweep would have wandered off. Past about three-quarters speed it starts to bite: the turn
tightens and the nose gets heavy, so the thing that makes a Firebolt fast is the same thing that makes
it dangerous — and a Firebolt into a wall costs more than any other broom in the game.

**The Oakshaft 79 is a log with twigs on it.** Slow to start, slow to turn at any speed, and it hangs
in the air forever once you stop pushing. Scrapes barely mark it. A real crash still does.


### Brooms look like themselves on a server too (2026-08-27)

Everything the last two updates gave the brooms — their own wood, their own tail, their own sound and
handling — only ever reached you in single-player. On a real server the client was never sent the
table those things live in, so every broom fell back to the plain one. Fixed; the table is sent on
join and on every reload.

**And you sit on the handle rather than in it.** The seat was measured to the middle of the shaft, so
riders sank halfway into the wood — most visibly on the Oakshaft 79, whose handle is three times the
thickness of a Firebolt's. Each broom now seats you on top of its own shaft.


### Brooms that fly like themselves (2026-08-27)

They looked different; they all still *flew* the same. Now a broom's handling is part of what it is.

**A school broom wanders.** Not much — a degree or so at speed — but you notice you are holding a
heading rather than being carried along one, and you notice it most on a Cleansweep and least on a
Firebolt. Push a training broom into its boost and it gets harder to aim, which racing brooms
conspicuously do not.

**Momentum is per broom.** An Oakshaft 79 takes an age to get moving and then keeps going; a Comet
sheds speed the moment you stop pushing. Crashes weigh differently too: the antique shrugs off a knock
that costs a Firebolt eight points of its handle.

**They sound different.** Each broom carries its own flight note and its own boost cue, and each
leaves its own trail — flame off a Firebolt, soul-fire off the Supreme, white sparks off the Nimbus
pair, ash off the Oakshaft.

**And a datapack can now retexture or remodel a broom without touching the mod.** `model`, `texture`
and `animation` are fields; so is where the rider sits and which way they face.


### Brooms you can tell apart (2026-08-27)

Every broom in the game was grey. The handles are painted in greyscale on purpose, to be coloured in
when they are drawn — and nothing ever supplied a colour. On top of that all seven shared one texture,
so past the range where you can make out an outline, a Cleansweep and a Firebolt were the same object.

**Now they are seven different brooms.** The Cleansweep is thick scuffed oak with a messy uneven
bundle and an iron foot peg. The Comet is pale birch and clean straw under a thin brass band. The
Nimbus 2000 is polished walnut with a silver collar and a tight swept tail; the 2001 is darker,
thinner, and flatter-tailed, with brighter rings. The Firebolt is an ebony needle bound in red cord,
and the Supreme carries gold runes on the handle and embers at the tips of its tail. The Oakshaft 79
is the thickest handle and the biggest, darkest bundle in the game, bound in iron.

**And you now sit on your broom.** The rider's hips were nearly a full block above the handle, which
is to say you were flying alongside your broom rather than on it. The seat is measured off the model
now, so you straddle the shaft.

Inventory icons follow the same wood, in the same order — the Comet used to be the *darkest* broom
icon in the game and is the palest broom in the world.


### Dittany is for wounds, not for hit points (2026-08-27)

It was six hearts, free, on a five-second cooldown — better than a golden apple and simpler than one.

**Now it rewards being used on somebody who is actually hurt.** Two hearts ordinarily; three when
there is a real injury to close — a Sectumsempra bleed, Cruciatus pain, a sundering — and the injury
goes with it. Nine-second cooldown, stacks to sixteen.

**And you can pour it on someone else.** Right-click an injured player or animal and they get the
dose. Refused on a patient who needs nothing, and the bottle is not spent, so a mis-click on a healthy
sheep costs nothing.

Self-application is still held for a moment rather than instant, which is deliberate: that is the
event Dittany's splinch cure listens for, and making it a click would have silently deleted the one
thing Dittany is actually famous for.

Still the Wiggenweld ingredient — nothing here touches brewing.

### Peppermint Toads keep hopping (2026-08-27)

Twelve seconds of Speed, and the toad does not stop once you have eaten it — a small green hop out of
your stomach every three seconds, audible to anyone standing near you. Four hops, on the beat.

The mint settles your stomach by exactly one level: Nausea I goes, Nausea II drops to I with its
remaining time intact. Not a cure — a bad case takes several toads, which is a better joke than one
toad fixing everything, and it stops the cheapest sweet in the shop quietly being the best antidote.

Novelty candy, priced as such. It lasts longer than a Fizzing Whizzbee and does far less, which is
the trade.

### Fizzing Whizzbees float and pop (2026-08-27)

They gave Jump Boost. Jump Boost on its own is a bigger hop and then the same fall, which is a potion
effect rather than a joke.

**Eight seconds of float.** You go up further, you come down slowly, and every jump while you are
fizzing gets an extra kick on the way up — added on top of the boost rather than replacing it, so the
two compound. Sherbet sputters round your feet the whole time, where everyone can see it.

**Then it pops.** A soft fizz-out and a burst when it runs out, because an effect that simply stopped
would leave you wondering when it had. The float is the payload; the pop is the punchline.

### Treacle tart and pumpkin pasties stay food (2026-08-27)

Both are still the plain meals, and that is the point — if every food carried a bonus, the magical
sweets would stop being special and the sensible thing to pack would always be whatever had the best
numbers.

**Treacle tart is Harry's favourite**, and now says so. Ninety seconds of Home Comfort: one heart
trickled out across the whole duration — slower than eating almost anything else, deliberately — and
fear passing thirty percent faster while it holds. A Dementor's chill, a soul-drain, a Confundus.
That is comfort food rather than first aid: it does not stop anything happening, it makes it pass
sooner. A Chocolate Frog still clears the despair outright, so both are worth carrying.

**Pumpkin pasties are travel food and nothing else** — except on the one occasion the trolley
implies. Eaten in a minecart or on a broom, they are worth ten seconds of Speed. Not because pastry
is magic, but because that is when somebody eats one.

### Dirigible Plums show you the Wrackspurts (2026-08-27)

**Eat one and you leave the ground for a second**, which is the joke and stays the joke — a bob, not
a movement ability.

**Then forty-five seconds of noticing what other people have decided is not there.** Anything within
sixteen blocks that is hiding gets a violet outline: potion invisibility, an Invisibility Cloak, a
Demiguise's camouflage, a wizard in Shadow Form. Only you see it — the outline is a client render
field, so it is per-viewer by construction and costs nothing on the wire.

Deliberately concealment rather than proximity. An ordinary zombie three blocks away is not outlined;
a wizard under a Cloak is. A plum that revealed *everything* would trivialise every hiding mechanic in
the mod, and would not be Luna-ish at all — she does not see through walls, she notices things.

**And then five seconds of muddle.** A fruit that let you see the invisible for free would be a
scrying tool rather than a Luna Lovegood joke.

What counts as hiding is a datapack tag, so a pack's own concealment charm is caught without the plum
knowing it exists.

### Gillyweed is a transformation, not a potion (2026-08-27)

It was Water Breathing with a different name. Now it is forty-five seconds of being something else.

**You breathe water and you swim like you belong there** — a real swim-speed bonus, not just lungs
that work. Bubbles stream off the sides of your neck while you are under, and your skin takes the
colour of the lake. Everyone else sees both; the tint is the player model rendered again in
translucent green, because the render state has no colour field to write to.

**Three seconds of warning before it goes.** Bubbles tearing loose, a note climbing as the seconds
run out, and a line telling you to surface. After that, ordinary drowning — the warning buys
attention, not air. That gap is the whole reason this item is remembered from the Black Lake.

**It replaces Water Breathing rather than stacking.** Any potion already running is stripped when the
gills grow, and a new one cannot land while they are in. Drinking a potion and then chewing Gillyweed
wastes the potion, which is the honest outcome.

**Chewing another sprig mid-dive refreshes it** — surfacing to plan is not what the Black Lake is
about — at the cost of a ten-second wait once the gills finally lapse. Applied on expiry rather than
on eating, so topping up is a real choice rather than something you simply never do.

### Firewhisky charges for its courage (2026-08-27)

**Never pure Strength.** Every shot buys twelve seconds of Strength and a little fire resistance, and
every shot charges three seconds of burn for it — amber heat rolling across the screen and a bite out
of your hunger. A drink that granted Strength and nothing else would just be a worse Strength potion
with better flavour text.

**The second and third are the item.** Another within a minute and the room starts moving and goes
briefly dark. A third within two minutes puts you on the floor and takes your wand off you for
fifteen seconds — a real cast gate, sitting after every check that describes the spell and before the
ones that describe timing, so a drunk wizard is told they are drunk rather than told about a cooldown
they could not reach anyway.

Every shot says which round it was, because a wizard who suddenly cannot cast needs to know it was
the whisky and not a bug.

The bottle comes back — a plain glass one, since unlike a Butterbeer mug there is nowhere to refill
it.

### Droobles Best Blowing Gum actually blows a bubble (2026-08-27)

It gave you Slow Falling. Now it gets you across the ravine.

**Chew it and everybody watches the bubble grow on your face**, swelling as you go. Then twenty-five
seconds of drifting upward whenever you hold jump — gently, noticeably slower than a jump, so it
reads as being carried rather than flying.

**Three limits keep it a tool.** It only lifts while the key is held. It stops eight blocks above
where the bubble was blown — measured from there, not from the ground, so stepping off a cliff
mid-float buys no extra climb. And anything that hits you pops it on the spot, which is why Slow
Falling rides along for the whole thing: the ride can end anywhere, and it should not end in a
crater. The forty-second cooldown is the fourth limit; a bubble is a plan, not a way to travel.

Popping is loud whichever way it happens — running out, being hit, or being cleansed off — with a wet
snap and a burst of bubbles.

### Every-Flavour Beans finally mean every flavour (2026-08-27)

They were a fifty-fifty between Speed and Poison. After three beans you knew the whole item, and
"every flavour" turned out to mean two.

**Twelve flavours across five bands.** Two rolls, not one: how good or bad the bean is has fixed odds
— forty percent pleasant, thirty-five unpleasant, fifteen rare good, eight rare bad, two legendary —
and which flavour delivers that is a separate surprise. So what you cannot predict is *what*, only
roughly how bad, which is the difference between a joke and a coin.

Toffee, peppermint and honey. Earwax, vomit and dirt. Treacle tart, and a four-leaf clover that
really does bend your next loot roll. A rotten egg that blinds you, and a pepper that sets you alight
for two seconds. And one bean in fifty is either actual chocolate — which does exactly what a
Chocolate Frog does, by calling the same code — or a bogey, which makes you sneeze so hard you stop
being useful for half a second.

**Every bean says what it was.** Always, including the dull ones. A bean that quietly applied Nausea
is an unexplained debuff; a bean that says *Earwax…* first is a joke you are in on.

The table extends without rebalancing: a fourth unpleasant flavour makes each unpleasant flavour
rarer and leaves unpleasantness itself at thirty-five percent.

### Chocolate Frogs bite back, and hop away (2026-08-27)

Still three hunger, still a card. Two things are new.

**Chocolate is what you eat after a Dementor.** It is the one piece of first aid the books are
explicit about, so a frog now clears every despair you are carrying — the chill, the soul-drain, and
the Wither and mining fatigue the dark creatures inflict — and refuses them re-entry for thirty
seconds. Not resistance to Dementors; resistance to the feeling, which is what chocolate is actually
for. A datapack decides what counts.

**And fifteen percent of them get away.** Rolled before the eating starts, so the animation never
plays for a frog that was never eaten. What springs out of your hand is a real thing you can chase:
it hops for five to eight seconds, and walking into it gets the frog *and* its card back. Miss it and
you are out one snack. Low odds, recoverable loss — this is the joke the item is named after, not a
punishment.

The escapee is an item entity rather than a mob, which is why catching one is simply picking it up.

### Pumpkin juice is the other drink (2026-08-27)

Butterbeer is the pub. This is school, and the two are now worth telling apart.

**A clear head, for a minute.** Hogwarts Comfort: ten percent more experience from what you kill and
what you study. Combat scales the drop rather than the orb, so it never quietly covers furnaces and
ore farms. Study awards are ones and twos, where a flat ten percent rounds to nothing — so the
fraction is paid as a chance instead, and over a term of brewing it comes out at the ten percent on
the tin.

**It shakes off one small thing.** One harmful effect at amplifier 0, chosen at random, and it names
what went. Not poison, not Wither, not any of the Unforgivables, and nothing stronger than the first
tier — which is what keeps it from being milk with extra steps. A datapack decides what counts as
serious.

**And it leaves a trail.** Ten orange seconds behind you while you walk, derived client-side from the
effect's own remaining duration, so a decoration that lasts a sixth as long as the buff costs nothing
on the wire.

Fifteen-second cooldown — a real one this time, unlike Butterbeer's effect window, because the reason
to drink again quickly would be to reroll the cure.

### Butterbeer is worth stopping for (2026-08-27)

It restored two hunger and eight seconds of Regeneration, and that was all.

**Warmth, for ninety seconds.** Freezing simply stops — powder snow, a frozen river, a night in the
Snowy Slopes — and any frost already on you unwinds rather than being held where it was. Golden steam
comes off you while it lasts.

**Mellow, for forty-five.** Neutral things stop minding you: wolves, bees, endermen, golems, piglins.
Hit one and it minds you again immediately, and anything that already wanted you dead is unmoved. The
world goes about four percent wider at the edges and does nothing else — no wobble, no sway, no tint.
There is no drunkenness in this item, because it has to be usable on a server full of children.

**A second mug inside two minutes is just a drink.** Not a cooldown — you can always drink — it
simply stops stacking, and the tooltip says so. Servers that want a queasy reward for downing two
inside thirty seconds can switch `butterbeerGulpNausea` on; it ships off.

**The mug comes back.** Emptied into your hand, or your pack, or the floor — never nowhere. Refill it
on anything tagged `butterbeer_source`, which ships holding the brass cauldron and leaves the brewing
cauldrons alone so a potioneer's kit is not also a tap.

**And it sounds like a pint.** A fizz as the mug comes up, a warm swallow as it goes down, instead of
the generic potion glug.

### The Golden Snidget feather earns its rarity (2026-08-27)

It was a rare drop that no recipe wanted and nothing read.

**It is the tail of a Firebolt.** Both Firebolt-tier recipes now finish with a Snidget feather where
they used to take a handful of leaves — which is a real increase in what those brooms cost, and the
reason the fastest brooms in the game should be hard to own.

**Or hold it and fly faster.** In your off hand on a broom it is worth eight percent more top speed
and a noticeably steadier line: the broom converges harder on where it is pointed instead of carrying
old momentum through turns. A twitchy school broom gains more from it than a Firebolt does, because
it is meant to make a broom easier to fly rather than to make the best broom better.

**It wears out.** Sixty-four points, one per twenty seconds of powered flight, so about twenty minutes
in the air. Parking mid-flight costs nothing. That leaves a player with one feather an actual
decision: build the broom, or fly the one you have.

**Nifflers can smell it.** It is the shiniest thing in the mod and they will come for it.

### Shadow Essence, thrown and brewed (2026-08-27)

**Throw it and it opens a hole in the world.** Four blocks across, twelve seconds, and a short throw
on purpose — this is placed a few paces away, not lobbed across a valley. Anything standing in it goes
unseen: mobs lose their target and cannot pick a new one, and it is dark enough inside to feel like
somewhere you should not be. Hidebehinds notice from twenty-four blocks and come to it, which is the
one reliable way to pull one off a player it has been stalking.

**Brewed with a Demiguise hair it becomes Shadow Form.** Half-there and quicker than you should be,
for thirty seconds. The catch is written on the tin: light magic hits you twenty percent harder while
it lasts. It is deliberately only *semi*-invisibility — eyes lose you, and every hostile thing in the
world still knows exactly where you are. Borrowing a Hidebehind's shadow is not the same as being one;
a Demiguise hair is still what buys the stronger version.

**Spell damage now carries its family.** Every spell hurt with plain `magic`, so nothing downstream
could tell a light blast from a fire one and a vulnerability like this had nothing to attach to. Light
spells now deal a `light_magic` damage type, and what counts as light-based is a datapack tag — so a
pack can decide its own sunbeam qualifies without touching code.

### Mandrakes scream (2026-08-27)

Pulling one played a Ghast noise and did nothing.

**Now it screams eight blocks.** Nausea and Weakness to every living thing in earshot, the wizard
doing the pulling included — that is not a bug, it is the reason the greenhouse hands out earmuffs
before anyone touches the pots. Only a fully grown Mandrake does it; a seedling squeals harmlessly,
which is a warning about what a grown one will do rather than a tax on weeding.

**Earmuffs.** Fluffy, pink, non-negotiable, crafted from wool and leather. Worn on the head, they keep
the cry out. Who is spared is a datapack tag, not a list in code — the Blindfold is in it too because
the brief asked, and a pack that thinks a blindfold has no business stopping sound can take it out
without touching a class.

**The root replants.** A Mandrake used on soil goes back in the ground and starts growing again, so a
wizard with one Mandrake never needs seeds again.

**Baby Mandrakes are throwable.** A mature bed yields one a quarter of the time. Thrown, it shrieks
where it lands — half the reach, a third of the duration, so it stays a way to ruin somebody's
afternoon rather than a better harvest.

### Demiguise hair, and three different ways to not be seen (2026-08-27)

The hair was a rare drop used in exactly one recipe.

**Crouch and hold it, and it fades out of your hand.** Not a message saying it faded — the item model
walks through five progressively thinner sprites over the twenty-four ticks you hold it, and then you
are gone for five seconds. Camouflage comes with it: nothing picks you as a target, and anything
already coming for you forgets. Swing at something and both end instantly. Ninety seconds before you
can spend another.

**Demiguise Weave is a real armour trim.** Applied at a smithing table with any trim template, it
renders like every other trim and costs you the trim slot — which is the price of the ability. Wear
two pieces and a crouch turns you invisible for eight seconds, once a minute. Plain invisibility, no
camouflage: the hair you spend is what buys the stronger version.

**Invisibility Cloaks now wear out — except the one that shouldn't.** Every cloak in the books fades
except one, and being that one is what makes it a Hallow. Ordinary cloaks now carry seconds of
concealment, spent only while you are actually hidden, and shown on the tooltip. Hold a Demiguise hair
and use it to weave more in. The Deathly Hallow prints "It does not wear out" and never carries a
charge at all. Cloaks that already exist read as fully charged rather than as spent.

### The Occamy eggshell is worth carrying home (2026-08-27)

It was a rare drop with no use, tagged as an alchemical material that no recipe wanted.

**Set it down, or throw it in the pot.** Clicked on a floor it places as a small silver ornament.
Clicked on a cauldron already brewing something silver-based, the whole batch refines into its pure
silver form. Timing is the mechanic: the cauldron keeps nothing once a brew finishes, so the shell has
to go in while it heats. It is a catalyst, not an ingredient. A mistimed click costs nothing — the
shell is only spent when it actually refines something.

**What silver is for.** Pure Silver Solution silvers a weapon — bottle in one hand, blade in the
other — and a silvered weapon bites four extra half-hearts into anything the pack calls a dark
creature. Flat, not a multiplier, so it is worth pouring over a starter sword rather than saving for
the best one you will ever own. It also finally makes the Expanded Trunk craftable; every trunk above
the first was uncraftable before this.

**Which brews are silver is data, not code.** A brew declares the brew it refines into and that is
what makes it silver-based; a brew is pure silver because something else names it. Two derived facts,
one field, no list in Java — a datapack adds a whole silver chain by writing one line.

**They break.** Sixteen to a stack, and a fall that hurts you has a 30% chance of taking a held stack
with a soft crack. Only what is in your hands: shells in a pack are padded by everything around them.

### French money behaves like foreign money (2026-08-27)

The Dragot was a fourth coin that stacked to sixty-four and did nothing a Knut did not.

**It has a rate, and the rate moves.** A Dragot is worth about 0.8 Galleons, and that number is a
server config rather than a constant. What a goblin actually quotes you moves within three percent of
it, and Gringotts takes five percent on the way through. The quote is pinned when you walk up to the
counter and stands for the whole visit — so the number on the screen is the number you get, and it
has moved by the time you come back. Buying Dragots and selling them straight back is always a loss;
there is a test that says so at every point in the spread.

**Not everyone takes it.** A British shopkeeper offered a Dragot says so and will not deal. A
wandering trader — the one vanilla NPC who is plainly from somewhere else — takes them happily.
Datapacks decide the rest through three entity tags, including vendors who take *nothing* but Dragots
and vendors who take them grudgingly at a quarter over the odds.

**One in a hundred is bad.** Dragots found in traveller's caches and shipwreck treasure are stamped
devalued at one percent, rolled per coin rather than per stack. Nothing tells you which ones. Spend
one and there is a one-in-five chance the vendor weighs it, kills the sale, files it with the Ministry
and remembers your face — a goblin simply keeps the coin. The only tell before that is that a bad
stack will not merge with a good one, which is exactly how you would spot a shaved coin in a real
purse.

The old `DRAGOTS_PER_10_GALLEONS` constant implied a rate about a third of this one. It was never read
by anything and is now deprecated in place rather than quietly deleted.

### Papers, please (2026-08-27)

The Ministry License Scroll stacked to sixteen and did nothing. It is now the document the whole mod
asks you for.

**One licence, seven kinds.** Apparition, broom flight, Animagus registration, Auror trainee,
dangerous creatures, Ministry access and restricted substances — each made out to one wizard, with an
endorsement rank from 0 to 3, an expiry and a seal. It never stacks: two scrolls are two different
documents.

**Five gates read it, and they all read the same one.** Apparating, mounting anything from a racing
broom upwards, walking into the Ministry, being dealt with by a goblin at the counter, and buying from
a merchant with controlled ingredients in stock. Every one of them asks a single class the same
question, so a revoked licence means the same thing everywhere and adding a sixth gate costs one call.

**Forgeries work, until they don't.** A forged licence is indistinguishable from a real one and passes
every check — but each dealing with an official is a fifteen-percent chance the ink is looked at too
closely. When it is, the scroll is struck off on the spot, the crime goes on your file as the only
arrestable paperwork offence on the books, and every official within twenty-four blocks turns on you.
Nothing un-revokes a licence.

**Endorsements are written, not bought.** Ink in one hand and the scroll in the other, parchment in
your pack, and an O.W.L. in the subject the Ministry examines that licence on: a pass buys rank 1, an
E buys rank 2, an O buys rank 3. Refusals cost nothing — the Ministry does not keep your stationery
for telling you no.

**Right-click to read it.** A parchment card with the Ministry's seal turning above it, drawn rather
than textured — which is why a revoked licence's seal goes black and keeps turning.

Everything here is inert while the Ministry module is off. That module is what *cares* that you hold
papers; nothing about earning them depends on it.

### The Sneakoscope stops being an ornament (2026-08-26)

It was an item you could hold. It did nothing.

**Now it spins at deceit, not at hiding.** Held in either hand it sweeps twelve blocks every eight
ticks and reacts to people who are not what they are presenting themselves as: anyone invisible —
Cloak included, since the Cloak sets the flag and casts no charm — a creative or spectating player
actually moving through the room, someone holding a wand that has already sworn itself to a different
wizard, anyone bearing the Dark Mark, and anyone carrying a Horcrux whose soul is still whole. It
never names them and never reveals them. It tells you how bad it is and leaves the looking to you.

**Three bands you can feel.** One or two suspects and it shivers with a soft whirr. Three to five and
it winds up, louder, with silver light orbiting the glass. Six or more and it shrieks continuously
and the holder's screen — nobody else's — pulses red at the edges.

**Right-click to focus.** Half the reach, twice the reading, and it points: an arrow of motes along
the nearest suspect's bearing, rounded to one of sixteen sectors because the item is not that precise
and should not pretend to be. Focus costs nothing to hold and two seconds to release, so it cannot be
flicked on for a free peek.

The reading is kept on the item as `last_threat_count`, which drives the tooltip and the spinning
model. Nothing about it is on the wire: the whole presentation is derived client-side from components
a held stack already replicates.

### Studying a creature is finally worth something (2026-08-26)

The Bestiary tracked how well you knew each beast across five tiers, and mastering one bought you a
number on a screen. Nothing you could hold ever depended on it.

**Now the rarest materials answer to study.** An Erumpent's horn, a Thunderbird's tail feather, a
troll's whisker, a Wampus cat's hair — four wand cores that existed in the game and dropped from
absolutely nothing — now come off creatures you have actually learned about. A dragon you have mastered
gives up a second heartstring.

**Nothing you already got is affected.** The ordinary drop from every creature is exactly what it was:
this only ever adds. Kill a unicorn knowing nothing about unicorns and you get your unicorn hair, the
same as always.

**It resists farming.** Each rare material has its own cooldown, per player and per creature, that
survives logging out. A beast killed by fire or by another mob was studied by nobody and yields nothing
rare, so a mob crusher produces the same loot it always did.

Packs decide all of it: which creature yields what, at which tier, how often, and how long the wait.

### Your magic starts saying something about you (2026-08-25)

Heritage decided who you were born as, and then stopped mattering. Dark corruption only ever went up.
Notoriety and blood status never met. Three numbers, no story.

**Now conduct builds a character on top of birth.** Three meters sit at the top of the Record tab:
where you stand between Reformist and Traditionalist, between Dark and Light, and how the Ministry
regards you. Each is filled from the centre toward whichever way you have actually moved.

**Nothing was replaced to get there.** Dark corruption is the same meter it always was — every Horcrux,
every Unforgivable still stains you exactly as before — it is simply the dark half of an axis that now
has a light half too, earned by protective and restorative magic. Ministry standing is read straight
off your criminal record, so paying a fine or being pardoned moves it the moment it happens.

**What counts is written in the datapack.** A pack can say what casting a Patronus means, what studying
a creature says about you, or what an unregistered transformation makes you look like — and can gate
parts of the skill web behind who you have become. The mod ships the rules for the first three and none
of the gates: your existing skill trees are exactly as reachable as they were.

Servers can retune the whole thing: `standingAxisBound` for how far the axes run, and two threshold
keys for where "leaning" and "strongly" begin.

### The Ministry started collecting (2026-08-25)

Notoriety used to be a number that went up and then quietly went back down. Nothing read it, nothing
answered it, and the offences the mod calls paperwork rather than crimes — Apparating without a licence,
transforming while unregistered — cost you a line on a file you could not see.

**Now the Ministry sends a bill.** Every paperwork offence carries a fine: two Galleons for unlicensed
Apparition, ten for an unregistered Animagus, and more if you have priors for the same thing. It is
levied against your Gringotts vault, not your pockets, and Gringotts pays it the moment there is
anything to pay it with — including a partial payment against a vault that cannot cover the lot.

**Ignoring it costs more than money.** While a fine stands, your notoriety stops cooling and starts
creeping upward instead. It cannot creep far: an unpaid ticket can make the Ministry want a word, and
can never make you Undesirable No. 1. That is reserved for the things you would go to Azkaban for.

**You can finally see your own record.** The Character Sheet has a Record tab: your standing, a
notoriety meter, what you owe, and the file itself — which never decays, because the Ministry keeps
files. `/wandb ministry fine` shows the bill and `/wandb ministry fine pay` settles it early. Magical
Law Enforcement can remit one with `/wandb ministry fine waive`.

Server owners can scale the whole tariff, or switch fines off entirely, with `ministryFineScalePercent`;
the criminal record is unaffected either way. With the Gringotts module off, no fine is ever levied —
a debt against a vault that does not exist would be a debt nobody could clear.

### The Marauder's Map became a map (2026-08-21)

It was an entity radar wearing a map's name: coloured squares on a stock background, bound to
whatever spot you first unfolded it at and blank everywhere else forever. It now charts the world.

**It draws the country you have walked through.** Every chunk the map is carried near is sampled — one
biome, one of seven relief bands — and drawn as a hand-inked tile: trees for a wood, wave lines for
water, hachures for a ridge, a snow-capped triangle for a peak. Four variants of every terrain type,
picked by a hash of the tile's own coordinates, so a forest is not one sprite stamped in a grid.
Somewhere you have not been is bare parchment, and stays bare.

**It cannot be used to cheat.** The surveyor never loads a chunk — it reads only what the holder's
own client already has — and a tile is a biome and a height band, so caves, ore and the base someone
dug under a hill are not representable in the format at all. Generated structures follow one rule:
if the highest piece is at or above sea level it is marked from above, and if it is not, you have to
go inside. Azkaban and villages appear; strongholds, mineshafts and the Chamber of Secrets do not,
until you find them.

**Hogwarts appears because you built it.** The mod ships castle stone rather than a castle, so the
map finds one by noticing that somebody has laid a great deal of it in one place. Hogsmeade, Diagon
Alley, Gringotts and the Ministry work the same way, each with its own symbol, each labelled on the
page rather than only on hover.

**It remembers, and it is shared.** The atlas lives in the save keyed to the map itself, not to a
player and not on the stack: it survives logout, restart, death and the Nether, and someone on the
map's trusted list opens the same parchment already charted.

**And the object itself is a map now.** It was a slab skinned with a flat inventory icon it was too
big to sample, rendered at raw model scale because it had no display transforms at all, with two
flaps that hinged the wrong way and an unfold animation that snapped shut the instant it finished.
It is now a tri-fold sheet that closes into three visible layers of paper, with a worn cover, a wax
seal and a brass corner facing you in the inventory; it swings open with the spring stiff paper has,
holds open while you read it, and folds away with a page-turn when you close the screen.

**Pan, zoom, waypoints, a legend, coordinates.** Drag the sheet, scroll to zoom where you are
pointing, right-click to pin a place and name it, press C to come back to yourself. Zooming out
collapses tiles into blocks rather than drawing more of them, so the far view stays readable and
costs less than the near one.

**And the ink watches.** Everyone alive within 128 blocks still shows as a named mark that turns to
face the way they are walking — invisibility included, because that is the artefact — now trailing
footprints that fade behind them.

### Stats made a progression system rather than five numbers (2026-08-21)

The stat block, its training hooks and `StatEffects` were all real and all reaching gameplay. What
was missing was every part a player can perceive: **earning a point was completely silent**, the
sheet showed five values with no consequence attached to any of them, nothing said what raised a
stat or what stopped it, and `KNOWLEDGE` — derived, synced and displayed — was read by nothing in
the mod at all.

**Earning a point is now an event.** `StatProgression` sends one `StatLevelUpS2CPayload` carrying the
stat and the two numbers — never a sentence, the same split the cast-rejection channel uses — and
the client spends it on a toast, an amethyst chime pitched by how high the stat now is, a dozen
enchant glyphs, and a 1.2 s flash on that row of the character sheet. Ordinary training progress
stays silent, which is the whole point: a spell hit happens hundreds of times, and the one that
finishes a point is the one that has earned a noise.

**The accumulator moved out of `PlayerStatsAPI` into `StatTrainingScaler.apply`.** The loop there had
a dead ceiling guard — it re-read the *player attachment* for a value it had not written yet — and
banked progress at 100 that could never be spent. `StatTrainingReachabilityTest` could not have
caught either: it carried its own transcription of the loop rather than driving it. It drives the
real function now, and the pinned grind lengths are unchanged.

**`StatReadout`** is the one place a stat value becomes words, and every figure in it comes back out
of `StatEffects`. The sheet asks for a sentence and never sees a coefficient, so it cannot drift from
the cast pipeline — `StatReadoutTest` asserts the two agree at every endpoint.

**Character sheet.** Each stat row is now a 16px glyph, the value, a meter with a brass tick at the
heritage ceiling, the training hairline (eased between syncs, so a hairline that moves 0.3% per cast
is visibly moving), and what the stat is doing right now: *Spell Damage +2.6%*, *Misfire Chance
−2.96%*. Hovering gives the full card — what it is, what trains it, how many of each event the next
point is away, its ceiling and why. The event count is simulated against the real accumulator rather
than solved, because the closed form is off by one deep in the curve, and it is suppressed entirely
past 999 where it stops being a target anyone can hold. `CharacterTab` now receives the mouse and
hands its tooltip back to the screen, which draws it outside the sheet's scale transform.

**`KNOWLEDGE` has a consequence.** One, not a system: a wizard who has read the books, walked the
bestiary and worked the skill web is quoted less for a lesson — `StatEffects.tuitionCost`, 1.00 →
0.60 across the range, applied by `SpellLearningService` to both the price on the offer card and the
amount that leaves the vault, from one method so they cannot disagree.

**Art.** `tools/stat_icons.py` draws `character_sheet/stat_icons.png`, a 112×16 strip of seven 16px
glyphs — the five stats in `PlayerStat` declaration order, then the heritage padlock and the prodigy
starburst. Authored at 1:1 in the `WizardsPalette` brass family; a missing strip draws nothing rather
than the checkerboard.

**Also.** `ClientStatsState.clear()` had no callers, so one server's stat block survived into the next
session; it is wired to `LoggingOut` now. `grantPowerGrowth` is non-decreasing, so a milestone can no
longer drag an admin-set Power back down to its band. A milestone that lands on a capped stat says so
instead of silently spending its one shot. Every player-facing string on the tab — "Stats",
"Attributes", "Max Health", "Wand", "Carried Coin", every HUD row — is a lang key, and the mod's three
own attributes finally have names. `getPowerCeiling` (no callers, and it returned the current value,
not a ceiling) is gone; `getPowerCap` and `isPowerCapped` replace it and are reported by
`/wandb player stats get`.

### Cast feedback, spell wheel, and the missing alpha docs (2026-08-20)

**Refusals now say which refusal.** Eight reject sites in `SpellCastService`, four in
`SpellAssignC2SPayload` and five in `ObscurialServerLogic` were writing hardcoded English through
`Component.literal("§5...")`. Every one is a lang key now, and the whole vocabulary lives in one
map on `SpellRejectCodes` — 23 codes, one distinct key each, asserted collision-free and
lang-resolvable by `CastRejectMessageKeyTest`. Flavour colour moved into the lang *values*, so no
Java class holds a colour for a sentence any more.

**The server stopped writing the sentence.** `SpellDeniedS2CPayload` was an empty record; it now
carries the reject code, and `ClientSpellRejectFeedback` decides what the player reads and where —
the spell HUD while it is up, the action bar when it is not, never both. The server remains the only
authority on whether a cast is legal. Two codes deliberately stay silent on the client
(`REQUIREMENTS_UNMET`, `GAMP_HARD_REJECT`) because their sites compose something better from live
state: "Requires Stupefy at Adept" beats "requirement not met".

**Spell HUD** gained the short-lived reject line (3s, with a fade tail) above the active spell name.
The cooldown arithmetic moved out of the renderer into `SpellCooldownDisplay` and is now tested —
including the three ways it has actually gone wrong here: dividing by the base cooldown instead of
the applied span, an unclamped fraction on the boundary frame, and a seconds readout that rounded
down and showed "0" to a player who still could not cast.

**Spell wheel** (`X`, hold). Sweep to a spell, release to arm it into the currently active loadout
slot; release in the dead centre to cancel. Shows only spells you have learned that your heritage may
equip, with the same cooldown shading as the HUD diamond. It changes what the slot in your hand
contains — the loadout is still four slots and casting is unchanged. Confirming sends an ordinary
server-validated `SpellAssignC2SPayload`. Selection logic is pure and tested (`SpellWheelModel`):
empty roster, deadzone, the wrap seam at due north, and every direction at every wheel size from 1
to 24 entries.

**Docs.** `KNOWN_ISSUES.md` — named by CI's failure notice since the history purge and absent ever
since — now exists, derived from `ModuleDefaults` rather than from intent: 8 `PREVIEW` modules, 4
`DISABLED`, 83 of 96 creatures on the placeholder box rig, 3 of 10 heritages selectable, both
structure modules pointing at placeholder NBT. `ALPHA_SMOKE.md` records the ten manual vertical-slice
checks with what each should show, and states plainly what the automated gate does and does not
prove — `runGameTestServer` currently proves the server boots and the registries load, because the
repository has no `@GameTest` classes. README and `.github/workflows/ci.yml` point at real paths.

**Also:** `SpellRejectReasonFormatter` had grown a second English vocabulary paraphrasing the first;
it now resolves through the same key map. `SpellVfxClient` carries the map of where per-spell sound,
trail, impact and beam plug in (all four were already data-driven) — mirrored in `SPELLS.md` §8.


### Type system data expansion (heritage / subtype / profession)

**WizType (10)** — Stats, `MagicSource`, `SizeCategory`, `alphaAvailable`, colours, and two-sentence encyclopaedia descriptions aligned to spec: Wizardkind baseline; Werewolf hybrid +1 armour; Obscurial innate, no wand, +2 HP / +0.02 speed; Goblin small innate −2 HP / −0.005 speed / +2 armour; House-Elf small innate −4 HP / +0.015 speed; Veela hybrid speed; Giant large none-magic tank (20 / −0.02 / 6); Centaur large innate; Vampire hybrid (4 / 0.025 / 2); Merpeople innate land-speed penalty (−0.01) with +1 armour. Alpha flag true only for Wizardkind, Werewolf, Obscurial. Giant size category moved from Huge to Large per design.

**WizSubtype (31)** — Lore descriptions, stat tweaks within caps, tag vocabulary (`no_wand`, `transformation`, `sunlight_weakness`, `enhanced_bond`, `rune_affinity`, `vault_access`, `water_breathing`, `nature_speech`, `divination_sight`, `dark_resistance`, `innate_apparition`, `blood_hunger`, `moon_sensitive`, `obscurus_form`), and per-subtype `uiColor` (ARGB) for the heritage confirmation UI. **31 entries:** core 27 from the heritage spec plus `adopted_magical` (Wizardkind), `savage_bite` (Werewolf), `hall` (House-Elf), `clan_warden` (Giant).

**ProfessionNode** — Full trees for all ten types with tier costs (1 / 2 / 3) and prerequisite chains: Wizardkind branching Apprentice → Wandmaker / Seer → Archmage; Werewolf Tracker → Pack Master → Alpha; Obscurial Channeler → Conduit → Harbinger; Goblin Vault Clerk → Metalsmith / Bank Steward → Director; House-Elf Steward → Ward Binder → Liberated Adept; Veela Charmer → Songbird → Flame Dancer; Giant Bruiser → Ravager → Warlord; Centaur Ranger → Star Reader (`centaur_stargazer_path`) → Oracle; Vampire Stalker → Wraith → Nightlord; Merpeople Tidewarden → Deep Caller → Songweaver. Profession tags updated to the requested set (`wand_mastery`, `divination`, `vault`, `smithing`, `healing`, `stealth`, `transformation`, `combat`, `beast_mastery`, `economy`, `astronomy`, `dark_arts`, `song_magic`, `aquatic`, `bond_magic`).

**Integration** — Wand compatibility and resonance heuristics updated for the new subtype tags; spell/HUD/skill routing treats `no_wand` like Squib; type confirmation header draws subtype line using `getUiColor()`.

### Canon correctness pass (2026-07-25)

**Bestiary** — Corrected 56 `mmRating` values to the five-grade Ministry of Magic scale from *Fantastic Beasts and Where to Find Them*. Six were outside the scale entirely (`sea_serpent` 9; `griffin`/`hippocampus`/`snallygaster`/`tebo`/`wampus_cat` 6) and fifty disagreed with Scamander's grade. Most visible: `phoenix` 1 → 4 (difficulty of domestication) and `golden_snidget` 3 → 4 (endangered protected species) — at their old values the scale read both as harmless birds. `BestiaryScreen` now renders the grade as repeated `X` glyphs rather than a bare integer. Dragon breeds untouched pending a design ruling; entries with no canon Ministry grade untouched. `mmRating` has no gameplay reader, so this is display and datapack metadata only. Full before/after table in `MIGRATION_DELTAS.md`.

**Heritage** — `Heritage` and `HeritageVariant` descriptions now resolve through lang keys (`type.WizardsAndBeastsMod.<id>.desc`, `subtype.WizardsAndBeastsMod.<parent>.<id>.desc`), mirroring the existing `ProfessionNode` pattern; 41 keys emitted through `ModLanguageProvider`, generated `en_us.json` 599 → 640. `/wandb heritage list` now follows the client locale. Corrected the Werewolf description: lycanthropy is transmitted by bite and is not heritable, so the "or born with" clause is gone. No heritage gameplay field changed.

**Spells** — Added `SpellCanonTier` (`BOOKS`/`COMPANION`/`FILM`/`POTTERMORE`/`EXPANDED`/`ORIGINAL`) and an optional `canonTier` field on `SpellDefinition`, defaulting to `EXPANDED` so all existing JSON loads unchanged. Backfilled `flipendo`/`glacius`/`depulso` as `expanded` (video-game origin) and `frigora`/`claustra_reverto` as `original` (no canon attestation). Provenance metadata only — nothing gates on it.

**Wandlore** — `elder.json` no longer carries the `dark` affinity tag. Elder is the rarest and unluckiest wandwood, not an inherently dark one; that association belongs to the Elder Wand as an artefact. Descriptive metadata with no Java reader, so no behaviour change.

**Deferred** — `BestiaryEntry.CODEC` still binds `mmRating` as unbounded `Codec.INT`. Tightening it to `intRange(1, 5)`, plus its regression test, is blocked on `toad.json` shipping `"mmRating": 0` with no canon grade to replace it. See `AUDIT_PUNCHLIST.md`. *(Resolved by the follow-up pass below.)*

### Canon correctness pass, follow-up (2026-07-25)

**Bestiary — `mmRating` is optional.** `BestiaryEntry.mmRating` is now `Optional<Integer>` bound as `Codec.intRange(1, 5).optionalFieldOf("mmRating")`. Absence means *unclassified*, which is the correct state for an ordinary animal with magical uses: Scamander's A–Z lists only creatures that exist exclusively in the magical world, so a toad holds no Ministry grade at all rather than the real grade `X`. `toad.json` drops the field outright (owl, cat and rat will ship the same way). `BestiaryScreen` renders the new `bestiary.wizards_and_beasts.rating.unclassified` placeholder when a grade is absent. New `BestiaryClassificationRangeTest` guards the domain. **Breaking for third-party datapacks:** an `mmRating` outside 1–5 now fails datapack load with a range error.

**Bestiary — dragons are uniformly XXXXX.** All ten breeds are now `5`; seven moved up (`antipodean_opaleye`, `common_welsh_green` from 3; `chinese_fireball`, `hebridean_black`, `norwegian_ridgeback`, `romanian_longhorn`, `swedish_short_snout` from 4). Scamander grades "Dragon" as a single XXXXX species with the breeds described inside it and assigns no per-breed grades; with `mmRating` rendering as X glyphs, a lower per-breed value was a false in-fiction statement. The difficulty gradient is canon-supported at the level of temperament rather than classification and moves to a future `threatTier` field.

**Bestiary — `sortOrder`.** `demiguise` 33 → 94, resolving the only collision in the set (with `cornish_pixie`).

**Spells — `canonTier` is genuinely optional.** The implicit `EXPANDED` default is gone; the field is `Optional<SpellCanonTier>`, so absence now means *untriaged* rather than *expanded*. Backfilled 19 spells as `BOOKS` and 2 as `FILM` (`arresto_momentum`, `bombarda` — both originate in the *Prisoner of Azkaban* film, neither appears in any book). `capacious_extremis` is deliberately left unassigned pending sourcing. **Breaking for third-party datapacks:** a spell without `canonTier` no longer receives an implicit `EXPANDED`.

**Docs** — `DEVELOPER_REFERENCE.md` §2 corrected (107 bestiary entries, 27 JSON spells, 33 total; the 6 Java spells enumerated, since `riddikulus`/`capacious_extremis`/`claustra_reverto` are JSON), and §6 records the dragon ruling. The stale "`AssetModelParityTest` fails at HEAD" punchlist item is marked resolved — it passes, and the uncommitted work tangle does not block the build.

### Added
- Typed skill/type sync packet payloads with bounded decode helpers.
- Packet bounds and codec safety tests for network payload decoding.
- Beam performance presets (`low`, `medium`, `high`) with debug UI and command hooks.
- Timed brewing cauldron BlockEntity scaffold (heat-gated tick progression).
- Legacy namespace migration helper and migration compatibility matrix (`MIGRATION_MATRIX.md`).
- Additional packet bounds regression coverage for skill/type sync payload limits.
- Alpha release candidate gate checklist in `ALPHA_TESTING.md` (tests + fresh server boot + two-client smoke).
- Additional packet bounds regression tests for SpellAssign/SkillUnlock/TypeSelect/FormChange request payloads.
- Pre-beta confidence gate matrix in `ALPHA_TESTING.md` (runData + two-client smoke + soak pass).
- New `time_turner` item with hold-channel-release flow that advances world time on server-side release with cooldown gating.
- Dedicated Obscurial ability-use packet path (`obscurial_ability_use`) and dark-form HUD quickbar/readiness surfacing (`N` Surge, `M` Grasp).
- Configurable server perf profile knobs (`LOW`/`MEDIUM`/`HIGH`) for held-beam scan/effect cadence safety tuning.
- Packet bounds regression coverage for Obscurial ability request payload sizing.
- Lore regression tests for canonical coin selection/ranking and world-placement lore guardrails (tree biome placement + plant biome families).
- Datapack lore regression test coverage for `episkey`, `frigora`, and `levicorpus` spell definitions.

### Changed
- Lumos now stays self-focused (no nearby-player reveal glow), and Expecto Patronum now relies on repel/aura behavior instead of a generic target glow marker.
- Canonical coin checks now key off item ids with explicit slot-priority helper logic, keeping Niffler coin theft deterministic toward highest-value canonical denominations.
- Repo hygiene guardrails now keep local plan artifacts out of git and CI/docs now consistently document the release gate order (`test` -> `runData` -> `runGameTestServer` -> `build`).
- Spell assignment now reports explicit reject reasons to players.
- Form model renderer now prefers per-form texture paths instead of a forced placeholder texture.
- Alpha docs switched command examples from `/WizardsAndBeastsMod` to `/wandb`.
- Spell slot selection and form/type request rejections now provide immediate action-bar feedback.
- Respawn and dimension-change now trigger full spell/skill/type/form re-sync to reduce stale client state.
- Marauder's Map sync now scales interval/entity cap with active viewers to avoid multiplayer tick spikes.
- Held beam channel logic now throttles target scan/effect cadence to reduce per-tick load.
- Timed cauldron brewing now hardens invalid-state cleanup and active-brew queue guardrails.
- Floo Powder now supports bind-and-travel flow on Floo Grates (sneak bind, use travel).
- Obscurial and spell execution hotspots are split into compatibility-safe helpers (`ObscurialValueCodec`, `ObscurialDamageRules`, `SpellCastHandlers`).
- Incendio-style ignition now supports world block ignition on cone and igniting projectile impacts.
- Aguamenti channel now hydrates farmland, fills vanilla cauldrons while held, and places a source-water block after sustained hold.
- Glacius can now interact with world blocks by extinguishing fire and placing snow layers on valid adjacent faces.
- Diffindo projectiles now cut a strict whitelist of soft/crop blocks on block impact.
- Depulso/Flipendo/Expelliarmus block impacts now push nearby lightweight entities (items/projectiles).
- Reparo now falls back to targeted block repairs for safe vanilla subsets (anvil damage tiers and cracked stone/deepslate variants).
- Channel progression now ramps Crucio hold pressure over time and improves Wingardium Leviosa hold stability/strength scaling.
- Targeted block-hit fallback now gives Imperio a short-range mob-control application around the impacted block when no direct entity target is acquired.
- Stupefy projectiles now apply a short impact pulse debuff around block hits.
- Confringo block impacts now apply fire-first surface interaction before explosion resolution.
- Accio now pulls select non-living physical targets (projectiles/vehicles) with LOS checks.
- Expecto Patronum cone casts now include an immediate repel pulse for nearby hostile mobs.
- Obscurus Grasp now uses fluid-aware LOS and smoother pull motion for collision-safe handling.
- Self-cast utility polish adds Protego cast pulses, Arresto Momentum local stabilization, Lumos nearby fire cleanup, and Nox nearby glow cleanup.
- Avada Kedavra channel execution now enforces a direct line-of-sight check before terminal damage application.
- Global knockback handling now caps extreme force values to reduce cross-spell movement spikes.
- Time Turner now has dimension and threat-gating safeguards to prevent unstable time shifts.
- Expecto Patronum aura duration now scales with caster proficiency.
- Protego now grants a short mastery bonus layer on cast for improved defensive uptime.
- Bombarda targeted impacts now emit deterministic impact feedback and lightweight push utility.
- Obscurus Surge now reduces boost force when a close frontal collision is detected.
- Avada Kedavra now requires a short beam charge window before lethal execution.
- Obscurial-only actions (`obscurus_*`) are treated as abilities instead of spell-slot learn/assign flows.
- CI gate now runs `test`, `runData`, and `runGameTestServer` as pre-beta release checks.
- Datapack spell tuning pass improves `episkey`, `frigora`, and `levicorpus` toward more lore-aligned behavior and reduced placeholder effects.
- Expecto Patronum immediate cone pulse now repels only dark-aligned targets (matching aura intent) and uses a more thematic cast audio profile.
- Obscurial dark-form upkeep now escalates with stress tier and surfaces volatile collapse warnings more clearly during active dark form.
- Expecto Patronum cone casts now skip non-dark targets before generic cone damage/effect application, keeping anti-dark identity consistent.
- `wizards_and_beasts:levicorpus` datapack cast sound now uses a neutral magical cue instead of evoker-coded audio.

## [0.1.0-alpha.1] - 2026-04-19

### Added
- Initial external alpha release process docs (`ALPHA_TESTING.md`, `KNOWN_ISSUES.md`).
- CI workflow for `test` and `build` with uploaded jar artifacts.

### Changed
- Release version moved from snapshot to semantic alpha versioning.
- Mod metadata description updated from development wording to alpha wording.
