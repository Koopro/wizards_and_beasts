# Wizards & Beasts — Heritage Identity Status

What each identity a player can carry actually does in play, what is missing, and what this pass added. Written
2026-09-28 on `dev`. The architecture is unchanged and is the frame for everything below:

| Layer | Answers | Code |
|---|---|---|
| **Heritage** | Which people you were born among | `heritage/Heritage` (8 peoples) |
| **Lineage** | Family, culture, blood — who you are within that people | `heritage/HeritageVariant` + trait tags |
| **Condition** | Something that *happened* to you, carried on top | `heritage/MagicalCondition` + `ConditionOrigin` (lycanthropy, the Obscurus) |

Rules this pass keeps (from the 2026-09-17 rework, `KNOWN_ISSUES.md` §5m): **no heritage grants a flat bonus for
being itself**; identity is **named traits** (`HeritageTraits`) with sentences, and mechanics that read those traits
through `PlayerHeritageData.hasTrait` (lineage ∪ condition); **blood status buys nothing**; a condition keeps the
character's heritage, lineage, POWER roll and training.

Werewolf and Obscurial in the brief are conditions in this mod, not heritages, and stay that way. Only Wizardkind
is selectable in the alpha (`Heritage.isAlphaAvailable`); Veela and Vampire are playable only through
`/wandb player heritage set` until they are opened.

**One condition slot, one door (2026-10-29).** A character carries at most one condition. Every writer asks
`heritage/ConditionRules` first: `toAcquire` (no heritage / cannot carry / already carries / another condition) and
`toReassign` (a heritage change may not strand a condition the new heritage cannot carry). The door is
`HeritageAssignment.afflict`; `HeritageAssignment.assign` refuses before touching anything. The bite, the condition
command, the heritage command, the Control Center and the gate all go through it, and every refusal is a translatable
sentence. `HeritageAPI.afflict` stays the raw write for tests and the gate's already-validated selection.
`HeritageAPI.cure` now also releases an Obscurus host (`ObscurialHeritageHandler.releaseHost`: dark-form flight,
`obscurial_*` meters). Werewolf flags are left alone on a cure (`werewolf_equipment_stripped` guards stashed gear).

**Vampirism is a condition (2026-10-29).** `MagicalCondition.VAMPIRISM`, origin `ConditionOrigin.SIRED` (traits
`sunlight_weakness`, `blood_hunger`, `transformation`), carried by Wizardkind and Veela only. The Vampire people
(Born, Dhampir, admin-only Turned) is unchanged. Everything that used to ask for the Vampire heritage now asks the
character's traits: `NutritionPolicyResolver.resolve(data)` includes the condition, `VampireHeritageHandler` burns
anyone with `sunlight_weakness`, and `HeritageTransformService.formHeritage` makes a carrier's toggle the bat (a turned
Veela loses the harpy toggle and the fury change; her allure stays). `HeritageFormBridge` keeps the carrier's own
body by day. Turning is `heritage/vampire/VampireSiring`: crouching empty-hand click = offer (30 s, `PlayerScopedState`),
the same back = accept; both halves ask `ConditionRules` and the sire's blood (35) before anything is written.
`HeritageAPI.cure` resets the blood pool.

---

## 0. 2026-10-14 — heritage as experience: the reference architecture

The brief: a player should be able to say *my character is different because of their heritage* — through passive
identity, active mechanics, social identity, restrictions and progression, never a stat spread. The House-Elf was
built end to end as the reference (§0b); the shared layer below now carries every people and both conditions. No
heritage was opened for selection: Wizardkind is still the only one.

### 0a. The shared layer (`heritage/identity/`)

| Piece | What it is | Why |
|---|---|---|
| **Trait polarity** (`HeritageTraits.Polarity`) | Every trait is an ADVANTAGE, DISADVANTAGE or MIXED (listed under both) | The sheet can say what helps and what costs without inventing numbers |
| **`HeritageProfile`** + `HeritageProfiles.build` | Six sections — Advantages, Disadvantages, What you can do, How the world treats you, Progression, Right now — worded on the server from the rules that already hold (traits, body modifiers, the wand law, status abilities, professions, practice, live state) | One place answers "what does my heritage mean"; no second store |
| **`HeritageProfileC2SPayload` / `S2CPayload`** | The tab asks; the server answers about the sender only, every 2 s while open (10-tick server throttle) | A condition stays private: nobody can ask for anyone else's |
| **Heritage tab** (Character Sheet, 7th tab) | Draws the profile: polarity-coloured lines with their sentence | The identity column showed trait *names* only |
| **Practice** (`HeritagePractice`, `PracticeKind`) | A people's own arts count towards profession points: 20 progress = 1 point, at most 8 a day | No-wand peoples earned points only by mastering spells, so they stopped at 3 |
| **Code of Wand Use** (`ministry/law/CodeOfWandUse`) | Clause Three for every people that bonds no wand: Ollivanders refuses the trial and commissions; a wand carried in a Ministry official's sight files `NON_HUMAN_WAND` (2 G fine), once a day | Canon (GoF ch.9, Winky); the first Ministry/NPC/location rule that reads heritage |
| **Lineage shift** (`HeritageAPI.shiftLineage`) | Moves a character to another lineage of their people because something happened in play; nothing re-rolled | Freedom must not re-roll POWER |
| **Node gates** (`HeritageNodeGates`) | A skill node a lineage must reach in play (`elf_free` only for a free elf) | Freedom is given, not bought with skill points |

### 0b. House-Elf — the reference

| Pillar | Mechanic | Code | Canon |
|---|---|---|---|
| Passive | Small body; ward-ignoring Apparition with no licence and no Trace (existing) | `ApparitionServerLogic` | CoS ch.2; HBP ch.19 |
| Active | **Snap** — one target within 8 blocks thrown back and disarmed; **Kitchen charm** — up to 8 carried raw foods cooked, no fire. Wandless; on the wheel; a miss spends no cooldown | `heritage/elf/ElfMagic`, `abilities/elf_snap.json`, `elf_kitchen.json` | Dobby vs Lucius (CoS ch.18); the Hogwarts kitchens (GoF ch.21) |
| Social | **Service**: a bound elf pledges (sneak-use a player, empty hand), the player accepts (sneak-use back within 10 s). The master's wheel gains **Call House-Elf**: every bound elf in that world Apparates to their side | `HouseElfBond`, `HouseElfEvents`, `ElfBondData` (on the elf, survives death/relog) | Kreacher and Dobby answer summons |
| Restrictions | A bound elf's blow at its master never lands and the elf takes 2 damage instead; it cannot snap its master; the Code of Wand Use | `HouseElfEvents.onIncomingDamage`, `ElfMagic.snapAt` | Dobby's self-punishment (CoS ch.2) |
| Freedom | The master uses any garment (head/chest/legs/feet) on the elf: the garment passes over, lineage Bound/Hall → **Free** (POWER, Resolve, training kept), bond gone, permanently — a free elf cannot be bound again | `HouseElfBond.present`, `HeritageAPI.shiftLineage` | CoS ch.18 |
| Progression | Practice — snaps, kitchen charms, summons answered, Apparating — pays profession points (Steward → Ward Binder → Liberated Adept); the `elf_free` node opens only once free | `PracticeKind.ELF_*`, `HeritageNodeGates` | — |
| State on the sheet | Whom they serve, whether the master is in this world, summonses answered, practice towards the next point, the wand law | `HeritageProfiles.elf` | — |

Integration: **Ministry** (Code of Wand Use; no Trace on elf magic), **NPCs/locations** (Ollivanders refuses non-humans,
also the commission), **economy** (the fine through `GringottsBilling` via `MinistryFines`), **professions** (practice),
**skills** (node gate), **player stats** (lineage modifiers listed; POWER untouched by freedom), **education** (no
wandwork, so no O.W.L. casting evidence — listed under Disadvantages via `no_wand`).

### 0c. The pillars, per identity

| Identity | Passive | Active (wheel) | Social | Restrictions | Progression | State on sheet |
|---|---|---|---|---|---|---|
| Wizardkind | wandwork, learned arts, lineage culture | Apparition, Legilimency (learned) | Trace and licences | Squib: no wand | spells → profession points | Apparition licence |
| **House-Elf** | **as §0b** | **snap, kitchen; call (master)** | **service bond; no Trace** | **no harm to master; wand law** | **practice** | **bond, master online, summons** |
| Goblin | craft, property, runes; small body | — (appraisal is a skill node) | Gringotts commission 3.5 %, forgeries always seen; **immune to allure** (new) | wand law | **practice: appraisal** (new) | — |
| Veela | allure | allure; harpy (Full/Half) | allure on people and susceptible mobs | Full: no wand; fury at low health | spells (Half/Quarter) | human / harpy |
| Giant | spell-resistant hide; size; +health/+armour | — | beasts trust half-giants | Full/Clan: no wand | spells (Half) | — |
| Centaur | star reading; size; +speed | **Read the Stars** (new; was a command only) | — | wand law | **practice: star reading** (new) | sky readable now |
| Vampire | blood hunger, sunlight | bat (Turned) | feeding | burns in sun; food does nothing | spells | blood, thirst, sunlit |
| Merpeople | water breathing | — (Selkie change is automatic) | — | wand law | — | underwater / ashore |
| Lycanthropy | moon-sensitive | — (moon-forced) | silver bites the wolf | loss of control; no wand as wolf | — | nights to full moon, form, Wolfsbane |
| Obscurus | obscurus form | form, vent, surge, grasp | — | no wand, no casting | tiers | stress, held / loose |

Fixed on the way: the Clan Warden (a full-blood giant) was given the half-giant body; goblins were not immune to
the allure although this document always said they were; stale "not yet mechanical" lines in `KNOWN_ISSUES.md`,
`ALPHA_RELEASE_GATE.md` and `AUDIT_PUNCHLIST.md`; `water_breathing` comments that named a tag that does not exist.

Still open, deliberately: `goblin_craft`, `forest_law` and `mermish` describe and do nothing; the VEELA, GIANT,
CENTAUR and MERPEOPLE skill webs are empty; merpeople have no practice (no art of theirs has a hook yet); the
vampire bat cannot fly and has no rig; every heritage ability icon is the placeholder.

### 0d. Validation

| Check | Result |
|---|---|
| Unit tests | green, incl. `HeritageIdentityArchitectureTest` (polarity and lang coverage, practice math, profile sections) and `ProfessionNodeTest` |
| `runGameTestServer` | **340 / 340**, including the seven `HouseElfTests` scenarios (pledge/accept, harm to master, the call, freedom by garment, snap and kitchen, Code of Wand Use, profile) |
| Mutation | Removing the cancel in `HouseElfEvents.onIncomingDamage` fails `heritage_elf_cannot_harm_master` ("the elf's blow landed on its master"); restored |

That scenario first passed its "master unhurt" check vacuously — its players were creative, and a creative player
is invulnerable before the damage event fires. It now uses survival players.

**Not verified:** the Heritage tab in a running client; the bond and the call across two real clients.

---

## 1. Summary

| Identity | Kind | Before this pass | After |
|---|---|---|---|
| Wizardkind | heritage | Real mechanics (only people who bond wands, Apparate, practise Legilimency, open every skill tree, carry conditions) — but the character sheet described none of it; lineages carried one flavour line each | Two named traits make that breadth visible (`wandwork`, `learned_arts`); the Squib's `creature_kinship` does what it says |
| Werewolf | condition | Complete moon cycle, forced change, loss of control, Wolfsbane, cursed bite | Silver now bites a werewolf **in wolf shape** (and never the wizard) |
| Obscurial | condition | Complete: stress, tiers, surge/grasp, dark form, daylight, sealed wand | Audited, unchanged — already separate from stats and corruption |
| Veela | heritage (gated) | Harpy fury at low health + voluntary harpy; **allure had no gameplay** (read only by wand scoring) | Allure is an ability: people near cannot bring themselves to fight; limits and counterplay below |
| Vampire | heritage (gated) | Blood economy with thirst stages, feeding on the living, food does nothing, sunlight burns, voluntary bat | Audited, unchanged — the most complete heritage; gaps listed |

---

## 2. Wizardkind

**1. Mechanically different:** the only people who bond a wand (`PlayerHeritageData.canUseWand`), whose skill web is
open end to end, who can learn Apparition (`ApparitionServerLogic.isAllowedHeritage`) and Legilimency
(`LegilimencyServerLogic.canLegilimise`), and who can carry a condition and remain themselves
(`MagicalCondition.canBeCarriedBy`). Identity is **breadth**, not power.

**2. Normal play:** everything the mod's magic offers is open to them: casting, brewing, beasts, wandlore, law.

**3. Advantages:** flexibility. No other people learns every art.

**4. Limitations:** no innate magic — every capability is learned, licensed and practised (Apparition needs the test
and a licence; spells are found and read). A Squib (lineage) casts nothing and bonds no wand.

**5. Visual identity:** the baseline human body; heritage appearance entries can adjust it.

**6. World interactions:** the Ministry's Trace and law apply to them; wands choose them (`Compatibility`); goblins
charge them Gringotts' commission.

**7. Progression:** POWER roll on the one band everyone with magic shares (20–85); the skill web; spells learned
from sources; professions (Apprentice → Wandmaker → Seer → Archmage).

**8. Unchanged on purpose:** blood status gives no power. Pure-blood, half-blood, Muggle-born and wizard-raised
roll the same band and learn the same way.

| | |
|---|---|
| **Completed (this pass)** | `wandwork` and `learned_arts` traits on the four casting lineages — they describe rules that already hold, so the character sheet finally says what a wizard *is* rather than nothing. Squib `creature_kinship` now makes wary beasts (the unicorn, anything with `wary`/`groomable`) treat them as one tier better known (`WildlifeWorld.trusted`) — it was printed on the sheet and read only by wand scoring |
| **Missing** | Lineage-specific *social* reactions (pure-blood households, Muggle-born prejudice) — none have a consumer in the world yet; they stay descriptive rather than becoming power |
| **Dependencies** | Apparition licence system, skill web, spell learning, `WildlifeWorld` |
| **Known limitations** | Obscurial wizards show `learned_arts` from their lineage beside `no_casting` from their condition; the condition wins in every check, the sheet shows both |

## 3. Werewolf (lycanthropy — a condition)

**Identity vs state:** the *person* is a witch or wizard with a lineage (the heritage layer); *lycanthropy* is the
condition (`MagicalCondition.LYCANTHROPY`, origins bitten / bitten as a child / savage bite); the *wolf* is a transformation state the
moon forces (`WerewolfTransformService`, `TransformationState.TRANSFORMED` + form `werewolf_wolf`). Veela/Vampire
shape changes deliberately do **not** route through the werewolf service, and the werewolf does not route through
`HeritageTransformService` — two transform services, no duplicated condition logic.

**1–2.** Full moon + night + moonlight exposure force the change (a cellar is a real defence); the wolf's body takes
the player's hands away (no items, interaction or casting; `FormConstraintEvents`); a feral controller drives it.
**3.** The wolf is strong and fast (`WerewolfAttributes`); its bite under a full moon curses a human.
**4.** Loss of control, no wand in wolf shape, and now **silver**. **5.** `werewolf_wolf` rig. **6.** Wolfsbane
(potion) keeps the mind their own through the change; the werewolf creature shares the moon rules; silver.
**7.** None as a career — conditions are not professions (the six condition profession nodes were removed).
**8.** Unchanged: the heritage and lineage underneath, the moon cycle, Wolfsbane.

| | |
|---|---|
| **Completed (this pass)** | Silvered weapons bite a werewolf player **only in wolf shape** (`SilveredWeapons.isDarkCreature`): the condition is what silver answers to, the wizard in human shape is untouched |
| **Missing** | Nothing structural. Canon extras not built: dittany and powdered silver on a werewolf wound (Bill Weasley) |
| **Dependencies** | Silvering (brew/silver), Wolfsbane brew, form constraints |
| **Known limitations** | Silver's bonus is the flat silvering bonus used against every dark creature |

## 4. Obscurial (the Obscurus — a condition)

**1–2.** Suppressed magic: no wand and no casting in human form (`no_casting`, `no_wand`); stress builds, can be
vented; an Unleashed Obscurial takes the dark form, which *can* cast its own abilities (surge, grasp).
**3.** Dark-form power (surge, grasp, flight-like form). **4.** Stress lockout, daylight vulnerability in dark form,
sealed wand, form constraints on hands. **5.** `obscurial_dark` player form (`ObscurialDarkModel`), shares the
Obscurus palette. **6.** Wand scoring leans to thestral/dragon and yew/elder; the Obscurus creature exists apart.
**7.** Tiers (`ObscurialTierRules`) inside the condition.
**8.** Unchanged, and verified separate: Obscurial resources live in `ObscurialResourceManager` over the heritage
flag map; nothing reads `PlayerStat` or `DarkCorruptionService` from the Obscurial code, and neither system reads
Obscurial resources. Corruption and the Obscurus are two systems, as intended.

| | |
|---|---|
| **Completed** | Audit only |
| **Missing** | A way back (canon offers none for Credence; left absent deliberately) |
| **Known limitations** | `learned_arts` shows on the sheet from the lineage; every gate honours `no_casting` |

## 5. Veela

**1. Mechanically different:** allure and fury. **2.** Allure on demand; wounded badly (≤35% health) the harpy comes
out whether they meant it or not (`VeelaHeritageHandler`); Full/Half can choose the harpy (`veela_form`).
**3.** Allure takes the fight out of people; the harpy's fury. **4.** Allure works only on people, only in human
shape, can be resisted, breaks when they strike; Full Veela bond no wand (`no_wand`). **5.** Veela-harpy form rig.
**6.** Wand scoring favours veela-hair and unicorn cores (Fleur's grandmother's hair). **7.** Professions
(Charmer → Songbird → Flame Dancer). **8.** Unchanged: fury, harpy form, no stat bonus for beauty.

**Allure, as built (`heritage/veela/VeelaAllure`, ability `veela_allure`, 60 s cooldown):**
- For its duration, people near the Veela carry `INFATUATION` — the existing Amortentia state: an infatuated
  **player cannot strike another player**, and is told why. People-like **mobs** (`#wizards_and_beasts:allure_susceptible`:
  villagers, wandering traders, illagers, witches, piglins) drop their target and cannot take up another
  (`VeelaAllureEvents`).
- **Reach/hold by lineage:** Full 12 blocks / 10 s, Half 9 / 7 s, Quarter 6 / 5 s.
- **Resist:** a player throws it off with probability ½ × `StatResistModifiers.resistScalar` (0.2 with no will,
  0.5 with full will) — the same will that resists the Imperius and Legilimency.
- **Immune:** beasts, the undead, constructs, goblins, other Veela, spectators.
- **Counterplay/consequence:** the Veela striking anyone breaks it on everyone they entranced (only theirs — an
  Amortentia someone else brewed stays). The harpy has no allure: fury replaces it.
- Why not a charisma stat: it changes what people *do* (they stop fighting) for a moment, with a cost (the
  Veela cannot attack without ending it), rather than shifting a number.

| | |
|---|---|
| **Completed (this pass)** | Allure ability, susceptible-people tag, anger break, lineage scaling, will resist |
| **Missing** | The social half — trade and NPC reactions — deliberately not added as a discount or reputation number |
| **Dependencies** | Ability wheel, `INFATUATION` (Amortentia), player stats resist |
| **Known limitations** | Gated heritage: reachable only by command until opened. Canon's allure acts mostly on men; the mod has no gender, so will is the only defence |

## 6. Vampire

**1. Mechanically different:** a blood economy instead of hunger. **2.** Food does nothing; a blood pool drains with
activity and time and is refilled by feeding on the living (an empty hand on a living thing; the
`#wizards_and_beasts:bloodless` deny-list names what yields nothing); thirst bands (Sated → Thirsty
→ Parched → Starving) bring penalties (`VampireBloodHandler`). **3.** Night, the bat shape (`vampire_form`,
voluntary). **4.** Turned and Born burn in direct sun (not in rain; `VampireHeritageHandler`), Dhampirs do not;
starving is dangerous. **5.** Bat form; blood bar in place of the hunger bar. **6.** Feeding on mobs and players,
bloodless things. **7.** Professions (Stalker → Wraith → Nightlord).
**8.** Unchanged, and deliberately so: the mod's vampire lore is restrained (canon gives little beyond Sanguini and
the garlic in Quirrell's turban) — no invented weaknesses were added.

| | |
|---|---|
| **Completed** | Audit only |
| **Missing** | Nothing high-value that canon supports. Garlic aversion is folklore canon only hints at (Quirrell); left out |
| **Dependencies** | Nutrition policy, form system |
| **Known limitations** | Gated heritage. Professions carry the skill-effect bonuses of their nodes, which is the one place stat-shaped rewards remain for any heritage |

---

## 7. Validation

| Check | Result |
|---|---|
| `./gradlew runData` | wrote one file: the new `entity_type/allure_susceptible` tag |
| `./gradlew build` (unit tests incl. `HeritageIdentityRulesTest`, `HeritageTraitCatalogTest`) | green |
| `./gradlew runGameTestServer` | **153 / 153 required tests passed**, including three new scenarios in `HeritageIdentityTests`: `heritage_veela_allure_takes_the_fight_out_of_people`, `heritage_squib_kinship_is_trusted_by_beasts`, `heritage_silver_bites_the_wolf_not_the_wizard` |
| Mutation check | Removing the kinship step failed `heritage_squib_kinship_is_trusted_by_beasts` ("a unicorn would not let a quiet Squib near after one meeting"); restored |

Server authority: every new rule runs on the server (ability activation, target cancelling, damage events, trust
reads); clients see vanilla-synced effects and toasts. Persistence: `INFATUATION` is a vanilla effect and saves as
one; the record of who a Veela entranced is deliberately transient (it only needs to live as long as the effect).

**Not verified:** the allure on a real second client (the resist roll against a live player is covered by the pure
rule test, not by a scenario, because it is random by design); the new traits on the character-sheet screen in a
running client.

## 8. Next, in order of value

1. Open the House-Elf for play once its art and onboarding are signed off — it is now the complete reference.
   Then Veela and Vampire, which already have real mechanics.
2. Lineage social reactions for Wizardkind, only once the world has people who would react (NPCs, portraits).
3. Werewolf wound care: dittany with powdered silver (canon), on the existing Dittany rework.
