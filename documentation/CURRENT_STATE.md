Wizards & Beasts — Design Audit (Rev 2 · 2026-08-10)

Lead Game Designer / Minecraft + Wizarding World consultant review. Based on reading the actual repo (1,137 Java files, ~99.5k LOC, 682 hand-authored + 891 generated data files), not the docs — which I treated as stale where they disagreed with source. Re-verified from scratch; where this revision disagrees with Rev 1, the code moved.

What changed since Rev 1: two of the three criticals moved. The wand-wood system was rewired to read the datapack (mechanism fixed, data 40% done). Every creature now has a real multi-bone rig — the "colored box" finding is dead. The skill-tree filler finding is unchanged. A new load-bearing system landed that Rev 1 never saw: innate player stats now feed the cast pipeline. And the wand's dual-system bug did not die — it migrated from woods to cores.

1. What the mod actually is

Core fantasy (as-built): "Be sorted into a magical heritage, earn a wand that chooses you, learn a spellbook of canon spells, and grow into a specialized wizard whose bloodline changes how magic leaves the wand." The lore layer is enormous and genuinely faithful — 96 canon creature definitions plus 107 bestiary entries, 10 wand woods / 8 cores drawn straight from Pottermore, the three Unforgivables gated correctly, Azkaban, the Chamber of Secrets, Gringotts, Ollivander, 10 heritages across 31 variants with paragraph-long canon descriptions.

The actual play loops:

	•	First 10 minutes: Forced heritage-selection ceremony on first join (HeritageOnboarding.java:31). Rev 1 called this a blind choice — it no longer is. HeritageDossierRenderer now previews health, speed, armour, size, magic power, whether that heritage can hold a wand at all, and the signature trait, against a live player-model viewport. You pick from the alpha heritages knowing what they do. Then you need to find/trade an Ollivander for a wand, because no wand bonds without a heritage.
	•	First hour: Acquire wand → learn starter spells (Lumos, Stupefy) from a teacher by paying Knuts from a Gringotts vault (SpellLearningService.java:63) → cast spells to build proficiency.
	•	10 hours: Grind spell proficiency, spend skill points across 8 trees, pick one of 5 vocations, hunt creatures, maybe reach Azkaban or the Chamber.
	•	Long-term loop: …is still thin. This remains the central problem (see §3).

What genuinely differentiates it: The wand-allegiance mechanic (casting with a wand that isn't yours is penalized — Expelliarmus can win you a better wand), the proficiency mastery curve, the heritage system's real divergence (an Obscurial can't use a wand at all), the new innate-stat cast layer, and the Patronus. These are the signature ideas.

Verdict on shape: Rev 1 called this an engineered skeleton whose flesh was entirely placeholder. That is no longer true — 111 creature rigs and 162 entity textures shipped, and the cast pipeline gained two real player-influenced input layers. What remains hollow is content density inside engines that already work, and progression payoff. It is closer to a mod you would enjoy for 10 hours, and still not there.

2. The three findings that matter most

🔴 CRITICAL #1 — The wand's identity is 40% wired and 0% visible

The wand is supposed to be a wizard's most important possession. The mechanism that makes it matter is now correct. The data behind it and the presentation in front of it are not.

Evidence:

	•	The woods were rewired. WandStatsResolver.applyWood() (WandStatsResolver.java:126) now takes an Identifier plus a HolderLookup.Provider and reads cast_modifiers off the datapack WandWoodDefinition — damage, cooldown, range, fizzle and per-category damage bonus. This is the right architecture and it replaces the old hardcoded switch. Rev 1's core complaint is architecturally resolved.
	•	But only 4 of 10 wood JSONs carry a cast_modifiers block — elder, holly, rowan, yew. Exactly the four the old enum had. Ash, blackthorn, hawthorn, vine, walnut and willow still contribute nothing to a cast; they now hit WandCastModifiers.NEUTRAL via a logged lookup miss instead of falling silently through a switch. Better diagnostics, identical player experience.
	•	spell_modifiers is now honest rather than dead. WandWoodDefinition documents the field as deliberately unread: those schools (healing, divination) have no SpellCategory counterpart, and choosing the mapping is an unmade balance decision. Kept as authored, ready for when that lands. That is a defensible call, correctly recorded — it is no longer a field pretending to be live.
	•	The dual-system bug migrated to cores. 8 core JSONs exist; none has a cast_modifiers block. Casting still reads a hardcoded 10-case WandCore enum switch. Two of those enum cores (ROUGAROU_HAIR, WHITE_RIVER_MONSTER_SPINE) have no JSON at all, and thestral_tail_hair.json does not match enum THESTRAL_TAIL. Same defect class as Rev 1's finding, one layer down.
	•	The numbers are still invisible. WandItem.appendHoverText (WandItem.java:137) prints wood name, core name, flexibility, master UUID, integrity, corruption, length and allegiance score — and zero derived cast stats. The only place a player can read what their wand does to a spell remains a permission-2 debug command (SpellCommands.java:223).

Why it matters: The wand-choosing-the-wizard ceremony, the woods, the cores, the personality affinity — the whole Ollivander fantasy — still resolves to numbers the player cannot see, and 60% of the wood roster plus the entire core roster still resolves to nothing. The plumbing got fixed; the faucet was never opened.

The one thing that works, still: WandCastingAllegianceSystem.java:43-45 — using someone else's wand is 0.6× damage / 1.5× cooldown. Real, felt, canon-authentic. It remains the model for the rest.

🔴 CRITICAL #2 — The skill trees are still literal filler

8 skill trees, now 163 nodes (was 161). I re-tallied every node's effect type:

Effect	Count	What it is
passive_attribute	55	+0.5 health, +armor, +0.01 speed
category_cooldown_reduction	42	−2.5% cooldown
category_damage_bonus	27	+% damage
gameplay_bonus	18	real mechanics (harvest, beast resist)
unlock_ability	17	real choices (Animagus, Legilimency…)
spell_cooldown_reduction	11	buffs to spells learned elsewhere
spell_damage_bonus	6	same

The developer named the filler nodes filler in the shipped data, and 100 of 163 nodes now carry a skill.wizards_and_beasts.filler.minor_* display key — minor_vitality ×14, minor_focus ×13, minor_precision ×11, minor_resilience ×10, minor_hide ×10, and a long tail. Only ~17 of 163 nodes (the unlock_ability ones) represent a meaningful "what kind of wizard am I" decision.

Worse, and unchanged: the nodes named incendio_unlock, accio_unlock, avada_kedavra_unlock etc. still don't unlock the spell — they only shave cooldown or add damage. A grep across all 163 nodes for any spell-granting effect returns 0. Spells are learned from teachers for coin, not from the tree. The tree's most exciting-looking nodes are misnamed passives.

Why it matters: A player spends an hour earning points to buy +0.5 hearts fourteen times. That is the "huge skill tree where most nodes are +5%" anti-pattern. This is the one Rev-1 critical that has not moved at all.

🔴 CRITICAL #3 — The beasts have bodies now, but only one of them will look at you

Rev 1's headline finding here is dead, and what's left underneath is a sharper, smaller problem.

	•	The "colored box" claim is false now. 111 entity .geo.json rigs ship, averaging 9.7 bones, with zero single-bone rigs and 162 entity textures. Creatures are jointed, animatable figures you can tell apart.
	•	Fidelity is box-per-bone. 102 of 111 rigs average ≤1.4 cubes per bone — a torso cube, a head cube, four leg cubes, two wing cubes. Hippogriff is 10 bones / 10 cubes. Only 9 rigs are genuinely sculpted: Hungarian Horntail (36 bones / 144 cubes), Basilisk (23/111), Thestral (35/110), Obscurus (26/65), Peruvian Vipertooth, Chinese Fireball, Niffler, Streeler and the broom. The gap between the hand-built Horntail and a generated Hippogriff is immediately visible and sets the quality bar.
	•	93 of 96 creature JSONs still carry "_comment": "PLACEHOLDER box rig — swap with a Blockbench model…". That marker is now stale metadata that actively misrepresents shipped work — it is what produced Rev 1's false "every creature is a cube" finding. Clear it per-creature or it will keep generating bad audits.
	•	Zero taming, zero breeding across all 96 data-driven creatures, unchanged. GenericBeastEntity.mobInteract only handles the disguise-form gate. No feeding, no bonding, no trust.
	•	The pattern is proven exactly once. NifflerEntity is a bespoke Java entity with a real 0–100 bondLevel, milestone events posted to MagizoologyXPEvent, a pouch inventory and GUI, a carry attachment, FollowBondedPlayerGoal, SeekShinyBlockGoal, PickupCoinGoal, FleeWhenPouchStolen, and a baby variant that inherits bond data. It is the best-realised creature in the mod and the only one you can have a relationship with. Nothing in the 96-entry datapack roster is reachable that way.

The tragedy, restated: mechanically they were never reskins — 95/96 have real ability kits (web_snare, death_gaze, petrify, pack_tactics, fire breath) and a temperament spread (45 neutral / 29 hostile / 22 passive). They now have bodies too. What they still lack is a reason for the player to do anything but fight or flee.

3. System-by-system

Spells — the strongest pillar (7/10 magic design, up from 6). 27 JSON spells + 6 bespoke Java (Avada Kedavra, Imperio, Expecto Patronum, Protego, 2 Obscurus). These are good:

	•	Real Minecraft interaction — Lumos applies a light field, blinds and marks nearby monsters, and swaps your active spell to Nox (spells/lumos.json). Incendio is a cone that ignites. Not just "damage number."
	•	Real progression gating — Incendio needs Stupefy at proficient; Avada Kedavra requires PROFICIENT on both Imperio and Crucio (AvadaKedavra.buildRequirement). Note the canon reasoning is recorded in-source: the three Unforgivables are independent, so the Killing Curse is gated behind command of the other two rather than mastery of a parent.
	•	Real mastery — ProficiencyScaler.java is a well-tuned S-curve across five axes: damage 0.65× → 1.50×, cooldown 1.40× → 0.70×, duration 0.75× → 1.40×, control 0.82× → 1.30×, accuracy improving 20%. Cast-to-improve is exactly right for Minecraft.
	•	Avada Kedavra is a dodgeable projectile (speed 2.2, "jet of green light") on a long cooldown behind a deep dark-path gate — a defensibly balanced instant-kill, though isUnblockable() returning true in multiplayer still deserves a second look.
	•	New: the cast pipeline now carries three input layers a player can influence — proficiency, wand (partially), and innate stats.

Player stats — new since Rev 1, and load-bearing. Rev 1 never saw this system because it was write-only. It now reads: StatCastModifiers is called from SpellExecutor.executeGeneric — POWER → damage multiplier, PRECISION → misfire delta (applied before the roll, so it's in the diced total), REFLEXES → cooldown multiplier. WILLPOWER is read at the Imperio and Legilimency resist rolls; KNOWLEDGE is derived and informational. StatTraining and StatMilestones give the stats a growth path. POWER is rolled once at the heritage ceremony inside a species band, so this is the "how strong were you born" axis. This is the layer that makes two wizards of the same heritage cast differently, and it is a bigger source of felt divergence than the entire skill tree. Uncommitted at time of writing; no in-game pass.

Potions — built then abandoned, and now known to be blocked (unchanged, worse understood). The brewing infrastructure is real: cauldron tiers (pewter/brass), ingredient lists, heat-time ticks, colored output brews. There are still exactly 2 brews (Wiggenweld, Mandrake Restoration) and 2 recipes. The reason it stalled is now clear: BrewDefinition only supports lists of vanilla MobEffects, which structurally blocks every signature potion — Polyjuice, Felix Felicis, Veritaserum and Wolfsbane all need effects the codec cannot express. This is not a content gap you can author your way out of; it needs a codec extension first. Meanwhile Herbology (24 skill nodes) still grows crops for potions that don't exist. Still the single biggest content-empty engine in the mod.

Heritage — genuinely excellent, and now well presented (best RPG idea in the mod). Rev 1 undercounted this at 5 heritages; it is 10 heritages across 31 variants, with real mechanical divergence: base health/speed/armor deltas, wand-usability (Obscurial canUseWand = false → a whole no-wand playstyle), heritage-gated abilities and wand compatibility. This is where "two players feel like different wizards" actually happens — more than the skill trees deliver. Rev 1's only complaint, the blind first-login choice, is fixed: the dossier previews the mechanical deltas before you commit. Not yet verified in-game.

Worldgen / exploration — still nearly absent (3/10). Two structures total: azkaban_fortress and chamber_of_secrets, both one-per-world set pieces. There are 7 configured and 7 placed features — four wandwood trees plus devil's snare, mallowsweet and mandrake patches — so the overworld has magical flora, but no magical places. No wizard hamlet, no roadside shrine, no ruin with a spellbook, no reason to wander. One custom dimension (extension_realm) serves the trunk/pocket system. Creatures spawn naturally (BeastSpawnHandler) and now look like creatures, which helps. Minecraft's core verb is explore and discover; this mod still barely participates. Unchanged and now the clearest single weakest pillar.

Currency — a legitimate sink, but grind-risk (unchanged). Gringotts vault (Knuts/Sickles/Galleons) gates spell-learning and wand-buying (SpellLearningService.java:63, withdrawing Config.spellTeacherLearnCostKnuts). Real sinks, not fake currency. The risk is still the source: if earning Knuts is tedious, the spell wall becomes a grind gate rather than a choice. Still unmeasured.

Vocations — thin (unchanged). 5 vocations, each granting 1–2 small abilities tied to a tree (Duelist = spell power + cast speed; Wandlore = +0.1 wand affinity). They point at different activities but the mechanical grant is minor; playstyle divergence comes from which abilities you unlock, not the vocation itself.

Brooms — still a hidden gem. 7 tiered brooms with a full flight-physics vector (maxSpeed, acceleration, handling, stability, boost, durability, repair material), and a broom.geo.json rig now exists. The most quietly complete secondary system in the mod and the best Minecraft-exploration fit besides spells.

Audio — thin but honest. 41 sound events defined, 0 .ogg files — every event maps to a vanilla sound. Functional, never distinctive.

Technical quality — high (7/10, held). Data-driven everywhere, codec-based, module-gated across 26 modules (ModuleManager), server-authoritative casting with a bounded ModifierStack (hard cap 3.0 / floor 0.25), synced entities, FML unit tests. 2,494 lang keys (up from 2,161) — content is thoroughly named and described. Docked for: the core dual-system split, the 93 stale PLACEHOLDER markers, SOUL_FRAGMENT_INTACT being read at six sites and never written false anywhere (every Horcrux vessel is permanently indestructible), ModifierStack.finalPower() remaining a dead knob with no consumers, and a working tree carrying three feature-sized uncommitted changes at once. The engineering is still not the problem.

4. Scores

Category	Rev 1	Now	One-line justification
Minecraft Integration	5	5/10	Spells & brooms integrate beautifully; worldgen and 2 potions still barely touch the sandbox.
Wizarding World Authenticity	8	8/10	Lore density is outstanding — 10 heritages/31 variants, 96 canon beasts, Unforgivables, Gringotts. Box-per-bone rig fidelity still costs it the feel points.
Fun	4	5/10	Casting improved — innate stats and datapack woods now reach the pipeline; the loops around it are still filler grind and non-interactive beasts.
Magic Design	6	7/10	Spells, proficiency and gating are strong, and the cast now has genuine player-influenced inputs; the wand half is still half-dead.
Wand Design	3	5/10	Wood mechanism correctly rewired to the datapack, but 6/10 woods are inert, all 8 cores are un-migrated, and the derived stats are invisible in the tooltip.
RPG Progression	4	5/10	Player stats give a real, trainable growth axis; 100 of 163 tree nodes are still self-labelled filler.
Exploration	3	3/10	Two set-piece structures. Magical flora is not magical geography.
Creature Design	4	5/10	111 rigs and 162 textures land; box-per-bone fidelity, stale placeholder markers and zero taming/breeding hold it down.
Worldbuilding	5	5/10	The text (naming, heritage prose, handbook) is magical; the world you walk through still is not.
Atmosphere	5	6/10	Real rigs and textures shipped alongside the beam/particle/Patronus VFX; procedural GUIs and all-vanilla audio still pull down.
UX	4	5/10	Heritage choice is now informed. Wand math is still invisible and the trees still lie in their filenames.
Multiplayer Design	6	6/10	Real sync discipline (server-authoritative casts, synced Patronus/stats). Unblockable instakill still needs review.
Balance	5	5/10	Proficiency curve and Avada gating are well-tuned; inert woods and filler progression still muddy the decisions.
Replayability	4	5/10	10 heritages / 31 variants (Rev 1 undercounted at 5) is a real replay hook; filler trees and no procedural world undercut it.
Technical Quality	7	7/10	Genuinely solid data-driven engineering; docked for the core split, stale markers, a dead knob, and three uncommitted feature branches in one tree.
Overall	5	6/10	Two of three Rev-1 criticals moved. The mod crossed from "engineered skeleton" to "engineered skeleton with skin."

5. The requested reports

A. Executive summary

Doing well: Lore authenticity, spell design (interaction + mastery + gating), heritage-driven identity, the new innate-stat cast layer, and the underlying engineering. Held back by: the wand's choice is still invisible and 60% unwired, the skill trees are still self-admitted filler, the beasts have bodies but no relationships, and the brewing engine is empty by codec limitation rather than by neglect. Strongest identity: canon-faithful spellcasting where your wand, your bloodline and your practice all measurably change the spell. Path to exceptional: stop adding systems and finish the five that are already 40–80% built — surface the wand's numbers, author the remaining wood/core data, replace filler nodes with real forks, extend the brew codec then fill it, and generalise the Niffler's bond layer.

B. Top 10 problems (by player impact)

	1.	🔴 The wand's derived cast stats are invisible to the player — the Ollivander fantasy still resolves to unreadable numbers. (§Crit-1)
	2.	🔴 100 of 163 skill nodes are named filler, and the *_unlock nodes unlock nothing. (§Crit-2)
	3.	🔴 96 creatures, zero taming/breeding — the Niffler proves the pattern and is alone in it. (§Crit-3)
	4.	🟠 6 of 10 wand woods and all 8 wand cores contribute nothing to a cast; the dual-system bug migrated from woods to cores.
	5.	🟠 Brewing is blocked at the codec — BrewDefinition cannot express any signature potion. Still 2 brews.
	6.	🟠 Exploration is 2 set-piece structures; the overworld has magical flora but no magical places.
	7.	🟠 102 of 111 rigs are box-per-bone; Hippogriff and Horntail look like they're from different mods.
	8.	🟠 93 creature JSONs carry stale "PLACEHOLDER box rig" markers that misrepresent shipped work and corrupt audits.
	9.	🟡 SOUL_FRAGMENT_INTACT is read at six sites and never written false — every Horcrux vessel is permanently indestructible.
	10.	🟡 Vocations are too thin to create distinct playstyles; currency risks becoming a grind-gate on spell learning.

C. Top 10 strengths

	1.	Spell proficiency mastery curve (ProficiencyScaler) — cast-to-improve, S-curve tuned across five axes.
	2.	Spells interact with the world, not just deal damage (Lumos, Incendio).
	3.	Wand allegiance penalty — the one wand mechanic that's felt and canon.
	4.	Innate player stats reaching the cast pipeline (POWER / PRECISION / REFLEXES / WILLPOWER).
	5.	Heritage divergence across 10 heritages and 31 variants, especially the no-wand Obscurial playstyle.
	6.	Correct Unforgivable gating, with the canon reasoning recorded in-source.
	7.	The Niffler — bond levels, pouch, carry, babies. The single best-realised creature in the mod.
	8.	The Patronus (happiness-driven, wards Dementors, corporeal forms, synced).
	9.	Broom flight physics — deep, tiered, exploration-ready.
	10.	Server-authoritative, bounded, synced technical foundation across 26 gated modules, with 2,494 lang keys.

D. Most "Wizarding World" features

Heritage system & prose · wand allegiance / winning wands via Expelliarmus · the Patronus · Unforgivables gating · the Niffler's pouch and bond · Gringotts + Ollivander + Azkaban + Chamber naming.

E. Most "Minecraft" features

Spells that alter terrain/mobs · brooms as a flight/exploration tool · potions brewed from vanilla + magical ingredients in cauldrons · cast-to-improve proficiency · natural creature spawns · wandwood trees and herb patches in worldgen.

F. Biggest missed opportunities (small effort, big payoff)

	•	Print the wand's resolved cast stats in the tooltip. The values already compute on the cast path and already render in a debug command. This is a display change, and it repairs the mod's central fantasy.
	•	Author cast_modifiers for the 6 remaining woods and migrate the 8 cores off the enum switch. The reader exists; this is JSON authoring plus one deletion.
	•	Extend BrewDefinition past vanilla MobEffect lists. One codec change unblocks Polyjuice, Felix Felicis, Veritaserum and Wolfsbane, and finally gives Herbology's 24 nodes a reason to exist.
	•	Generalise the Niffler's bond layer onto GenericBeastEntity. The hard part is written and shipping; a dozen signature beasts could inherit it.
	•	Clear the 93 stale _comment markers. Trivial, and it stops the data lying about itself.
	•	Upgrade rig fidelity on the signature dozen. The Horntail is the quality bar and it was hand-built — the recipe exists.

G. Remove / Rework / Simplify / Keep / Expand

	•	KEEP: Spell casting + proficiency, the player-stats cast layer, heritage system, wand allegiance, broom physics, module/networking foundation, the Niffler.
	•	REWORK: Wand cores (migrate to datapack cast_modifiers, delete the enum switch); the whole skill-tree node set (replace filler with forks; make spell-unlock nodes actually unlock or rename them honestly).
	•	SIMPLIFY: 8 skill trees → fewer, denser trees; collapse the 5-node stub trees into their heritages or cut.
	•	REMOVE: The 93 stale PLACEHOLDER box rig comments; either wire spell_modifiers to a real SpellCategory mapping or drop the field.
	•	EXPAND: Potions (after the codec fix), creature bonding, everyday exploration structures, rig fidelity on the signature dozen.

H. Recommended development order

	1.	Surface the wand. Tooltip the resolved cast stats. Highest leverage, smallest change, fixes the core fantasy — Crit-1.
	2.	Finish the wand data. 6 woods + 8 cores onto cast_modifiers; delete the core enum switch. Kills the dual-system bug for good.
	3.	De-filler the skill trees (fixes fake depth — Crit-2). Convert minor_* clusters into meaningful forks; make spell-unlock nodes actually unlock, or rename them.
	4.	Unblock brewing at the codec, then fill it (10–20 canon potions) and tie Herbology to it.
	5.	Generalise Niffler bonding to a signature dozen beasts (fixes the "Beasts" half — Crit-3).
	6.	Upgrade rig fidelity on those same dozen, using the Horntail as the bar.
	7.	Seed everyday magical exploration (a wizard hamlet, a hedge-witch cottage, a ruin with a spellbook).
	8.	Commit and verify. Three feature-sized changes are sitting uncommitted, and neither the heritage restyle nor the player-stats wiring has had an in-game pass. Balance/polish after that; only then consider new content.

6. Final design question

If Wizards & Beasts shipped tomorrow, after 10 hours players would remember: "A gigantic, lore-accurate wizard mod where I picked a heritage that actually changed how I cast, learned some genuinely cool canon spells, raised a Niffler… and then the skill tree gave me +0.5 hearts fourteen times, my wand was a name with no numbers, every other beast was a jointed lump that ignored me, and there were no potions." They'd respect the ambition and the spell feel, and bounce off the emptiness behind it.

What they should remember instead: "The wand that chose me and fought better in my hand than a stolen one — and I could see exactly why. The Niffler I raised, and the Hippogriff that finally let me near it. The Wolfsbane I brewed the night before the full moon. The Patronus I conjured when the Dementors came. The duel I won by disarming someone and claiming their wand's allegiance."

Rev 1 called the gap between those two paragraphs a depth-and-presentation gap. Rev 2 narrows it further: it is a finishing gap. Every pillar in that second paragraph is 40–80% built and stalled one deliberate step short of being felt — the wand reader is wired but silent, the rigs shipped but the data still calls them placeholders, the bond system works on exactly one animal, the brewing engine runs but its codec cannot express a real potion. Nothing here needs a new system. It needs the last mile on five existing ones. Close that and this becomes the mod where players think: "This is what it feels like to actually be a wizard in Minecraft."

—

Verification notes: every number above was read out of the working tree on 2026-08-10, not from prior audit docs. Uncommitted at time of writing: GuiScaleHelper, HeritageDossierRenderer, HeritageSelectionScreen, plus an untracked HeritageNameContrastTest. The heritage restyle and the player-stats wiring have not had an in-game pass. Rig fidelity was measured as cubes-per-bone across all 111 entity geometries; "sculpted" means >1.4.
