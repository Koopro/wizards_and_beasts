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

1. Open Veela and Vampire for play once their art and onboarding are ready — both now have real mechanics.
2. Lineage social reactions for Wizardkind, only once the world has people who would react (NPCs, portraits).
3. Werewolf wound care: dittany with powdered silver (canon), on the existing Dittany rework.
