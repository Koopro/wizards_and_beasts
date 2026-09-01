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
3. Cycle with `◀ ▶`. **Expect:** exactly three heritages are selectable — Wizardkind,
   Werewolf, Obscurial. The other seven show but refuse.
4. Pick Wizardkind and confirm.
5. **Expect:** the screen closes, and the model viewport's size profile is applied to the
   player (a Werewolf pick should look visibly different from Wizardkind).

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
