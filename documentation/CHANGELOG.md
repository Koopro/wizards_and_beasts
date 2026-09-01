# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

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
