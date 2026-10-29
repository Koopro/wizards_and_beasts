# Alpha Smoke Checks — 0.1.0-alpha.1

The vertical slice, by hand. Everything here is a scenario the automated gate
(`test` → `runData` → `runGameTestServer` → `build`) does **not** cover, because it needs a
live world, a client, or both.

Run the whole list against a **fresh singleplayer world in Survival**, then again in
Creative for the steps a fresh survival world cannot reach yet. Record failures in
[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).

## Preconditions

```
/wandb admin module list
```

Confirm the states match [`ModuleDefaults`](../src/main/java/at/koopro/wizardsandbeasts/module/ModuleDefaults.java):

| Expect `ENABLED` | Expect `PREVIEW` | Expect `DISABLED` |
|---|---|---|
| `WANDS`, `WANDS_AND_SPELLS`, `SKILL_TREES`, `BROOM_FLIGHT`, `POCKET_DIMENSIONS`, `FLOO_NETWORK`, `CHARACTER_SHEET`, `STRUCTURES`, `HANDBOOK`, `GRINGOTTS`, `WANDWOOD`, `MAGIZOOLOGY`, `WIZARDING_FOOD`, `ARTEFACTS`, `FURNISHINGS`, `SCHOLARSHIP` | `PROFICIENCY`, `PLAYER_ABILITIES`, `CREATURES`, `BESTIARY`, `PLAYER_ANIMATION`, `OWLS`, `PLAYER_STATS`, `HERITAGE` | `DARK_ARTS`, `AZKABAN`, `CHAMBER_OF_SECRETS`, `MINISTRY` |

A mismatch means either the world was migrated (module state is stored per world) or a
config override is in play — resolve it before continuing, or the rest of the list proves nothing.

---

## S1 — Heritage first join

1. Join the fresh world.
2. **Expect:** the heritage selection screen opens and cannot be dismissed.
3. Cycle with `◀ ▶`. **Expect:** only Wizardkind is selectable; the other seven peoples show but
   refuse. The right-hand panel reads in words — Traits, Magical affinities, Special characteristics —
   with no Health/Speed/Armour numbers anywhere.
4. Pick Wizardkind, half-blood, and the condition "Lycanthropy: Bitten"; confirm.
5. **Expect:** the screen closes, the character sheet shows the lineage *and* the condition, and the
   body is the werewolf human form (visibly different from a plain Wizardkind pick).

**Fail modes seen before:** selection completing without assigning a form, which silently
skips all 22 size profiles.

## S2 — Wand obtain and bond

1. Craft a **wandmaker's bench** (`wizards_and_beasts:wandmakers_bench`), a **wand blank**
   (`wizards_and_beasts:wand_blank`), and one core material (e.g. `unicorn_hair`).
2. Use the bench, pick a wood and core, craft.
3. **Expect:** a `wizards_and_beasts:wand` with wood/core/length/flexibility in its tooltip.
4. Hold it. **Expect:** a bonding result — the wand either chooses you or does not, and says so.
5. If it refused, craft a different wood/core pair and repeat.

**Expect not to see:** every wand refusing every player. That was the shipped state before the
bond-math fix; a total refusal rate is a regression, not bad luck.

Creative shortcut: `/give @s wizards_and_beasts:wand`.

## S3 — Cast a spell

1. With the bonded wand in hand: `/wandb magic spell learn lumos`.
2. Press `X` and hold. **Expect:** the spell wheel opens showing the spells you know.
3. Aim at `lumos`, release. **Expect:** the wheel closes and the HUD diamond's active slot
   shows Lumos.
4. Right-click to cast. **Expect:** the cast plays, the slot's cooldown sweep runs down, and
   the GCD ring flashes.
5. Cast again immediately. **Expect:** refusal — the denied sound, an action-bar line, and
   the same reason on the HUD for about three seconds.
6. `/wandb magic spell forget lumos`, then try to cast. **Expect:** "You have not learned this
   spell yet." — a *different* line from the cooldown one.
7. Drop the wand and try to cast. **Expect:** "You need a wand in hand to cast."

**The point of steps 5–7:** three different refusals must produce three different messages.
A generic "can't cast" for all three is a failure.

## S4 — Skill tree

1. Press `K`.
2. **Expect:** the skill web opens and draws the star-chart background.
3. Pick an unlockable node. **Expect:** unlocking it produces a toast (not a chat line — chat
   draws *under* an open screen and would be invisible).
4. Pick a locked node. **Expect:** a refusal toast naming the missing requirement.

## S5 — Floo travel

1. Craft a `floo_fireplace`, a `floo_grate` and `floo_powder`.
2. Place two fireplaces far apart; register both.
3. Throw powder into one and pick the other as destination.
4. **Expect:** arrival at the second fireplace, with travel sickness applied.
5. Repeat several times. **Expect:** an occasional dark-weighted misfire — that is designed,
   not a bug. A *guaranteed* misfire is a bug.

## S6 — Broom flight

Do this on **two** brooms — the starter `broom` and a `firebolt_supreme` — because the impact and
camera maths are now per-broom, and the bug they replaced only showed as a difference between the two.

1. Craft or `/give @s wizards_and_beasts:broom`.
2. Use it on the ground. **Expect:** a `wizards_and_beasts:broom` entity spawns and you mount it.
3. Fly: W drives, S reverses, jump climbs, sneak descends, sprint boosts. **Expect:** pitch/yaw
   control, and the flight pose applied to the player model (`PLAYER_ANIMATION` is `PREVIEW` — the
   pose may read wrong at extreme pitch).
4. **Let go of everything mid-air.** Expect the broom to *settle downward*, slowly, and not to
   accelerate into a fall. Hovering forever is the old behaviour and a regression.
5. **Land on flat ground at a controlled descent.** Expect no damage, no durability loss, and a
   quiet touchdown. Check the broom's durability before and after: unchanged is the pass.
6. **Fly flat out into a cliff face.** Expect a real crash — dismount, damage, particles — on
   *both* brooms. The starter broom used to be unable to register any impact at all.
7. **Fly the Firebolt Supreme at about two-thirds throttle into a wall.** Expect to survive. It used
   to be fatal at that speed.
8. **Dismount while hovering over open ground.** Expect to be put down on the ground below, not left
   in the air, and not flung forward at flight speed.
9. **Dismount while parked inside a doorway.** Expect to end up beside the broom, not inside a block.
10. **Fly into water, then (in Creative) into lava.** Expect the flight to end, the rider to be set
    down at the nearest clear footing, the broom to drop as an item, and an action-bar line saying so.
11. Watch the wind and FOV at speed. Then set `broomFovEffect=0` and `broomWindVolume=0` and confirm
    both stop entirely — the accessibility path has to actually work.

## S6b — Floo travel

1. Build two fireplaces far apart. Register the first: look at it and run
   `/wandb world floo register "The Burrow"`. Register the second as `"Grimmauld Place"`.
2. Stand at the first with **no powder** and try to travel. **Expect:** "You have no Floo Powder" —
   and confirm no powder was taken, because there was none to take.
3. Stand somewhere with **no fireplace** and try to travel. **Expect:** "No lit, registered fireplace
   within reach." This used to fail silently, which is the thing being checked.
4. With powder, travel A → B. **Expect:** arrival at B, a toast (not a chat line), travel sickness,
   and green flame at the far end.
5. Travel back and forth several times. **Expect:** an occasional misfire to a different address,
   reported as a FAIL toast naming both where you meant to go and where you ended up. Designed —
   a *guaranteed* misfire is a bug.
6. **Rename a fireplace:** re-register "The Burrow" as "The Burrow Annexe". Open the destination
   list. **Expect exactly one entry**, under the new name. Two entries, or the old name still
   working, is the ghost-registration bug returning.
7. **Break a registered fireplace.** **Expect:** it disappears from the destination list.
8. Confirm nothing in steps 4–7 wrote to chat.

## S7 — Summon a creature

`CREATURES` is `PREVIEW`, so the summon command is the intended access path.

Two groups, and the whole point of this step is that they behave differently — see
[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §4 for which creatures are in which, and
`creature/AlphaRoster.java` for the four conditions an id has to meet to be called alpha-ready.

```
/wandb beast creature list
```

1. **Expect:** the report distinguishes the alpha-ready roster from the placeholder ones.
2. Summon one from the **alpha** roster: `/wandb beast creature summon hippogriff`.
   **Expect:** a hand-built rig, AI beyond wander-and-look, and a loot table on death.
3. Summon one **placeholder**: `/wandb beast creature summon kneazle`.
   **Expect:** a six-to-nine-cube untextured box that idles and wanders, and drops nothing.
   That is the documented state, not a failure.
4. Summon `hungarian_horntail`. **Expect:** a real rig and the breed's fire/venom kit.
5. Wander a fresh world without summoning anything. **Expect:** only alpha-roster creatures
   spawn naturally (`creatureNaturalSpawns`), plus the few kept on for progression reasons.
6. Open the creative tab. **Expect:** no spawn eggs for placeholder creatures
   (`showPlaceholderSpawnEggs`, off by default).

## S8 — Bestiary

1. Craft the bestiary (`wizards_and_beasts:bestiary`) and open it.
2. **Expect:** the full article roster exists, most of it locked (see `KNOWN_ISSUES.md` §4b).
3. Encounter a creature you have not met. **Expect:** its entry unlocks on sight. Unlocking is
   `PREVIEW`-grade — an entry unlocking at the wrong tier is a known limitation, not a new bug.
4. **Expect the art to be thin:** procedural 32px portraits, and no per-entry illustration in the
   detail pane.
5. `/wandb beast bestiary unlock <id>` then reopen. **Expect:** the unlock is reflected.

## S9 — Disabled structures stay disabled

1. `/wandb admin module list` — confirm `AZKABAN` and `CHAMBER_OF_SECRETS` are `DISABLED`.
2. Explore or `/locate` for either structure.
3. **Expect:** nothing generates. Both start pools point at placeholder NBT
   (225 bytes and 1.3 KB); a default install must place neither.

## S10 — Handbook

1. `/give @s wizards_and_beasts:ministry_handbook`, open it.
2. **Expect:** twelve chapters, drawn procedurally, all navigable.

---

## S11 — Character sheet

1. Open the inventory. **Expect:** the Character Sheet tab button, with an icon — never a magenta
   square.
2. Open the sheet. **Expect:** no missing-texture checkerboard anywhere, at GUI scale 2 **and** 3.
3. **Expect an amber corner mark** on any tab whose data comes from a `PREVIEW` module — Attributes
   while `PLAYER_STATS` is `PREVIEW`. Flip it with `/wandb admin module set player_stats enabled` and
   confirm the mark goes away.
4. Click every tab at scale 2 and at scale 3. **Expect:** each one selects; hit areas track the
   drawn tabs at both scales.

## What the automated gate covers instead

| Gate | Proves |
|---|---|
| `./gradlew test` | Pure logic under `src/test/java` — module state resolution, content indexing, spell learning eligibility, requirement evaluation, reject-code mapping, cooldown display maths, spell-wheel selection. Runs under FML, so registry-adjacent code is loadable. |
| `./gradlew runData` | Datagen runs clean and `src/generated/resources` is reproducible. **Warning:** runData stubs placeholder textures into `src/main/resources` — never `git add -A` after it. |
| `./gradlew runGameTestServer` | The dedicated server boots, all registries load, and every datapack file parses. It does **not** run any scenario: the repository has no `@GameTest` classes yet (`KNOWN_ISSUES.md` §8). |
| `./gradlew build` | Compiles and jars. |

Adding `@GameTest` coverage for S3, S7 and S9 is the obvious next step; each needs a structure
template under `data/wizards_and_beasts/gametest/structures/` first. A flaky game test is worse
than the manual step it replaced, so unit-test anything that can be made pure instead.

## Spell tracing (2026-10-21, `feat/spell-tracing`)

Manual — the screen has no automated client test. Fresh survival world, `/wandb magic spell forget lumos` if needed.

1. Hold a Standard Book of Spells written with Lumos, hold right-click 3 s → **Trace Lumos** opens; chip says
   *Basic*; green start mark pulses (static with `reduceScreenEffects=true`); status counts down "Begin within".
2. Press on the green mark, trace to the blue ring → gold progress follows; on arrival "Learned Lumos",
   accuracy shown; Close returns to the game; Lumos **and** Nox are in the spell list; the book is still held.
3. Repeat with a torn page of Alohomora but leave the lines → red X at the slip, "Your wand strayed outside
   the lines.", "Nothing was used up"; page count unchanged; **Try again** opens a fresh path (different shape).
4. Release the mouse halfway → "The gesture was not finished"; let the timer run out → "Too slow".
5. Toggle **Steady hand** before starting → the corridor visibly widens; the toggle is disabled once tracing.
6. Skill web: allocate `frigora_unlock` → the trace opens over the web; press Esc → back on the web, node card
   reads "Lesson not yet traced. Click to trace it."; click the node → trace reopens; pass → Frigora known.
7. Resize the window / change GUI scale mid-challenge → corridor re-rasterises, still aligned with the markers.
8. Dark Arts spell (with `dark_knowledge` held): edges are red; after a fail, Try again is disabled with
   "You can try again in 10s" until the delay passes.

## Vanishing Cabinet (2026-10-22, `feat/vanishing-cabinet`)

Manual. You need a survival world, two cabinets (from the recipe or `/give`) and a wand.

1. Place both cabinets. Each is two blocks tall, faces you, and shows a gold latch at mid-height.
2. Sneak and click cabinet A with the wand: "The cabinet hums…". The wand does **not** cast. Sneak and click
   cabinet B: "The cabinets are linked…".
3. Click A (no sneak, any item in hand): you come out in front of B, facing away from it. Click B straight away:
   nothing happens (cooldown). Click B again: you come out at A.
4. As a second player, click A: "The door will not open for you." As the owner, sneak and click with an empty
   hand: "Anyone may now step through…". The second player can now travel.
5. Put a block in front of B's doors and click A: "Something blocks the way out…". You do not move.
6. Sneak and click A with the wand: "Touch your wand to it again…". Within 5 s do it again: "The link is broken."
7. Re-link, then break B: you get one cabinet back, and A reads "Just a cabinet…".
8. Link a cabinet in the Nether to one in the Overworld and travel both ways. Then try a cabinet inside a trunk
   (pocket realm): it refuses to link.

## Mirror of Erised (2026-10-22, `feat/mirror-of-erised`)

Manual. You need a survival world and a mirror (`/give @s wizards_and_beasts:mirror_of_erised`).

1. Place it: a gilded mirror two blocks tall on clawed feet, facing you.
2. Click it: the mirror screen opens. The vision fades in from the dark. In a fresh world an italic clue line
   should appear under the vision (a creature and a biome, a spell and a kind of place, a fireplace, or a shop).
   Hover the engraved top rail to see the inscription and its reading.
3. Click it again: a different vision, no clue, "It is hard to look away.", and Slowness plus Hunger.
4. Click a third time: the effects are stronger. A fourth time: "Only your own face looks back at you, tired."
   and "Enough for today."
5. Sleep through to the next day: the mirror shows visions again.
6. As a giant (or centaur, house-elf, vampire…), the visions are that heritage's own. A player who lost a
   companion sees it named.
7. Break it: you get one mirror back, whichever half you broke.

`ArtefactShowcaseCapture` (`WB_ARTEFACT_SHOWCASE=1`, world `wb_cabinet_capture`) photographs it in
`run/screenshots/artefact_showcase/`.

## Sword of Gryffindor and the Sorting Hat (2026-10-22, `feat/sword-of-gryffindor`)

Manual. You need a survival world and `/give @s wizards_and_beasts:sorting_hat`.

1. Wear the Hat (head slot or the Curios head slot): a patched, slumped brown hat on your head in third person.
2. Without the Hat, let a Warden (or a basilisk, or a Dementor) beat you below a third of your health while you
   fight back: nothing happens.
3. With the Hat, do the same: "Something heavy and gleaming falls out of the Sorting Hat…", and the sword is in
   your inventory. It has an epic name and never loses durability.
4. Repeat the fight: no second sword. Throw the sword into the void (or blow it up), then fight again: it comes
   back.
5. Kill a basilisk with the sword: "The blade drinks the basilisk's venom…". It now glints, and its tooltip says
   it is impregnated.
6. Hold Riddle's diary in the off hand and use the sword: the diary is destroyed and the sword stays (needs the
   Dark Arts module).

## Wandmaking (2026-10-22, `feat/wandmaking`)

Manual. You need a survival world, a bench and a few enhancers.

1. Ollivanders, at the wandmaker's friend tier: thunderbird tail feather, wampus cat hair, troll whisker and
   White River Monster spine are for sale. Knockturn (trusted contact) sells the first three and rougarou hair.
2. Make a holly + rougarou hair wand at a bench of tier 4.6 or more. Its tooltip shows a Dark Arts bonus and
   extra misfire. A holly + unicorn hair wand shows a Dark Arts penalty and −misfire in green.
3. Right-click the bench holding a finished wand: "Refining a Wand" opens (plain right-click still opens the
   bench). Polish with honeycomb: the output wand's integrity rises. Take it: the honeycomb and the old wand are
   spent.
4. Re-fit stiffer with a log of the wand's own wood (spruce for holly). Without Wandlore's "Grain of the Wood"
   and with no lectern by the bench it says "Needs Grain of the Wood".
5. "New handle" with any log: the wand's handle changes, and nothing else does.
6. Find a rougarou in a swamp (default spawn setting): it drops rougarou hair.

## Learning spells (2026-10-23, `feat/spell-acquisition`)

Manual. New survival world.

1. Choose a Wizardkind heritage: a "Your first light" toast. Lumos and Nox are in the spell list, and you still
   have 3 skill points.
2. Open the skill web: the Lumos node costs 0.
3. Open village chests until a spellbook drops. Hold right-click to read it. It always opens a trace, because
   every village book can be read from scratch.
4. Close the trace screen of an advanced spell (any book a skill gates), log out and back in, then try again: it
   is still waiting.
5. Craft a book + ink bottle + feather: a blank spellbook. At a study lectern, with a known spell active and ink
   in your pack, use the blank on the lectern: a written copy.
6. Learn Petrificus Totalus from a book and cast it at a zombie: it stops dead.
7. As a goblin, try to read any spellbook: "Only someone who can use a wand can learn spells."

## Creature harvesting (2026-10-23, `feat/creature-harvest`)

Manual. Survival world, Bestiary and Magizoology on (default).

1. Kill a thestral without having studied thestrals: no tail hair. Study them to STUDIED (watch them, read the
   journal page) and kill another: sometimes a hair. A second kill straight after gives none.
2. Bond a thestral to 60 and stand near it: its tail hair is handed to you rather than dropped.
3. Find an occamy and wait near it for a few in-game days without hurting it: eggshells appear at its feet.
4. Apothecary, rank regular: murtlap essence, yeti fur, pukwudgie venom sac and matagot essence are for sale.
   Deliver four murtlap essence to a murtlap order.
5. Craft eight arrows around a pukwudgie venom sac: eight Arrows of Poison (strong).
6. Brew matagot essence, 2 quartz and 2 sweet berries in a brass cauldron: Draught of Peace. Drink it while
   nauseous: the nausea goes.
7. Run `/wandb admin module set ministry enabled` and stand near a goblin teller. Kill a unicorn: a Poaching toast,
   and the offence is on your record. Then bond a thestral to 80: an "Atoned" toast, and unicorns let you near.

## Heritage 2.0 (2026-10-29, `feat/heritage-2`)

Manual. You need a survival world and two players: A (an operator) and B.

1. Run `/wandb player heritage condition set B suppressed`, then `/wandb player heritage condition set B bitten`.
   The second command is refused with a sentence, and B still carries the Obscurus.
2. Run `/wandb player heritage condition clear B`, then `/wandb player heritage set A vampire born_vampire`.
3. A crouches and right-clicks B with an empty hand. B reads "... offers you their blood". B crouches and
   right-clicks A within 30 seconds. B is told the blood takes them, and A's blood bar drops.
4. B eats bread: it does nothing. B stands in the sun at noon: B burns. B's ability wheel shows the bat form.
5. Run `/wandb player heritage condition clear B`, then `/wandb player heritage set B goblin common`. Repeat step 3:
   both players are told a Goblin cannot be turned, and nothing changes.
6. Run `/wandb player heritage set B wizardkind half_blood` and `/wandb player heritage condition set B unleashed`.
   The character sheet shows "Unleashed path - mastery 0 / 100". In dark form, Grasp is refused with "needs 20
   mastery".

## Brewing (2026-10-23, `feat/brewing`)

Manual. You need a survival world, a pewter and a brass cauldron on a heat source, and two players for steps 7-8.

1. New player: brew Pepperup (blaze powder + sugar, pewter). It starts normally ("Ready in ...").
2. Brew Skele-Gro (2 dittany, 3 bone meal, 1 ghast tear, brass) without having read anything. The message says
   "An experiment begins". If it works, a toast says you learned it.
3. Hover Magical Drafts and Potions (apothecary): it lists Skele-Gro, Silver Solution and the Mandrake draught.
   Hold right-click for three seconds: a toast names what you learned, and the book stays in your hand.
   Hover again: the known recipes now show their ingredients and cauldron.
4. Open a stronghold library or village temple chest in a new world: a Potion Recipe Page sometimes appears.
   Reading it teaches the one recipe on it.
5. Bottle a brew with no skill and no O.W.L.: the tooltip says "Thinly brewed: lasts 85%". Then run
   `/wandb player skill force_unlock @s potion_potency` and `/wandb player owls set_grade @s POTIONS O`; the next
   bottle says "Finely brewed: lasts 130%".
6. Pull a Devil's Snare by hand: it drops. Find a wild Mandrake on grass in a new world (rare, plains and
   forests); it does not pop off.
7. Hold a Polyjuice bottle and right-click the other player: "You drop one of <name>'s hairs into the Polyjuice".
   The other player sees "Something tugs at your hair". Drinking the bottle disguises you as them.
8. Petrify the other player (`/wandb player condition set petrified true <them>`). Right-click them with a Mandrake Restoration
   Draught: they can move again, and you get the glass bottle back.
