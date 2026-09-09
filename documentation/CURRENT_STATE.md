Wizards & Beasts — Design Audit (Rev 2 · 2026-08-10)

Lead Game Designer / Minecraft + Wizarding World consultant review. Based on reading the actual repo (1,137 Java files, ~99.5k LOC, 682 hand-authored + 891 generated data files), not the docs — which I treated as stale where they disagreed with source. Re-verified from scratch; where this revision disagrees with Rev 1, the code moved.

What changed since Rev 1: two of the three criticals moved. The wand-wood system was rewired to read the datapack (mechanism fixed, data 40% done). Every creature now has a real multi-bone rig — the "colored box" finding is dead. The skill-tree filler finding is unchanged. *(Both have since been closed — see the addendum below. The skill web went from 168 nodes / 100 fillers to 107 / 30 on 2026-09-09.)* A new load-bearing system landed that Rev 1 never saw: innate player stats now feed the cast pipeline. And the wand's dual-system bug did not die — it migrated from woods to cores.

**Addendum, 2026-09-08 — Critical #1 is closed. Addendum, 2026-09-09 — Critical #2 is closed.** Each finding below is left as written, because it is the record of what was wrong and the reasoning still explains why it mattered. What has since landed is summarised under it. Critical #3 stands unchanged. The scores, the top-10 list and the roadmap below were written before either fix and are annotated where they are now out of date.

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

🟢 CRITICAL #1 (RESOLVED 2026-09-08) — The wand's identity is 40% wired and 0% visible

The wand is supposed to be a wizard's most important possession. The mechanism that makes it matter is now correct. The data behind it and the presentation in front of it are not.

Evidence:

	•	The woods were rewired. WandStatsResolver.applyWood() (WandStatsResolver.java:126) now takes an Identifier plus a HolderLookup.Provider and reads cast_modifiers off the datapack WandWoodDefinition — damage, cooldown, range, fizzle and per-category damage bonus. This is the right architecture and it replaces the old hardcoded switch. Rev 1's core complaint is architecturally resolved.
	•	But only 4 of 10 wood JSONs carry a cast_modifiers block — elder, holly, rowan, yew. Exactly the four the old enum had. Ash, blackthorn, hawthorn, vine, walnut and willow still contribute nothing to a cast; they now hit WandCastModifiers.NEUTRAL via a logged lookup miss instead of falling silently through a switch. Better diagnostics, identical player experience.
	•	spell_modifiers is now honest rather than dead. WandWoodDefinition documents the field as deliberately unread: those schools (healing, divination) have no SpellCategory counterpart, and choosing the mapping is an unmade balance decision. Kept as authored, ready for when that lands. That is a defensible call, correctly recorded — it is no longer a field pretending to be live.
	•	The dual-system bug migrated to cores. 8 core JSONs exist; none has a cast_modifiers block. Casting still reads a hardcoded 10-case WandCore enum switch. Two of those enum cores (ROUGAROU_HAIR, WHITE_RIVER_MONSTER_SPINE) have no JSON at all, and thestral_tail_hair.json does not match enum THESTRAL_TAIL. Same defect class as Rev 1's finding, one layer down.
	•	The numbers are still invisible. WandItem.appendHoverText (WandItem.java:137) prints wood name, core name, flexibility, master UUID, integrity, corruption, length and allegiance score — and zero derived cast stats. The only place a player can read what their wand does to a spell remains a permission-2 debug command (SpellCommands.java:223).

Why it matters: The wand-choosing-the-wizard ceremony, the woods, the cores, the personality affinity — the whole Ollivander fantasy — still resolves to numbers the player cannot see, and 60% of the wood roster plus the entire core roster still resolves to nothing. The plumbing got fixed; the faucet was never opened.

The one thing that works, still: WandCastingAllegianceSystem.java:43-45 — using someone else's wand is 0.6× damage / 1.5× cooldown. Real, felt, canon-authentic. It remains the model for the rest.

**What landed (2026-09-08).** Every claim above is now false, in the order it was made:

	•	**All 10 woods carry a cast_modifiers block.** Ash, blackthorn, hawthorn, vine, walnut and willow were authored, and the four that existed — elder, holly, rowan, yew — were deliberately re-tuned rather than preserved. Pinned in WandWoodCastModifierTest, all ten, so a new wood cannot arrive unpinned.
	•	**All 10 cores carry one too, and the enum switch is gone.** troll_whisker was authored; rougarou_hair and white_river_monster_spine had no definition file at all and now have one. WandStatsResolver.applyCoreFallback and its ten-case WandCore table are deleted, and applyCore is now line-for-line the shape of applyWood. Pinned in the new WandCoreCastModifierTest.
	•	**The id mismatch is fixed at the source.** The enum persists Thestral as thestral_tail while every other system spells it thestral_tail_hair, so the datapack lookup missed and only the fallback answered — meaning that with the fallback deleted, every legacy Thestral wand would have gone to neutral. WandCore.getDefinitionPath() now carries the id the cast path keys on, separately from the frozen getSerializedName() that backs the persistent component. WandIdParityTest asserts both directions and that Thestral is the only core where the two disagree.
	•	**The numbers are visible.** WandCastLines renders the resolved WandStats as tooltip rows — damage, cooldown, range, misfire chance and any non-zero category bonus — in the tooltip's existing green/dark-red vocabulary, and WandItem.appendHoverText appends them. It states the wand's *contribution*, not an effective damage figure: a tooltip has no spell, no proficiency and no caster stats in scope, and a number claiming to be the real one would be wrong in a way nobody could check. Rows that round to zero are omitted rather than printed as +0%.
	•	**Wood and core read as names, not ids.** WandLoreNames resolves each definition's display_name, so the tooltip no longer prints `wizards_and_beasts:rowan`. ShippedWandDefinitionJsonTest asserts every display_name names a key en_us actually has — LangParityTest cannot reach these, because it scans Java and these keys live in JSON.
	•	**Ollivander's trial cards show it before the choice is spent.** The cards printed a raw id truncated at 12 characters (`thestral_ta…`) and a resonance bar, which answers "will this wand have me" and nothing else. They now print resolved names clipped to the pixels a card has, and hovering one gives the full cast summary — resolved from the same trial stack the resonance score was computed from, through the same WandStatsResolver.resolve call the cast path makes. The gifted wand's length is rolled on acceptance rather than fixed at trial, so the summary says so rather than letting the wizard find out afterwards.

**What is deliberately still unwired.** spell_modifiers stays authored and unread. Its keys are magical schools — healing, divination, charms — and SpellCategory has only COMBAT, UTILITY, DEFENSE and DARK_ARTS. Mapping one onto the other is a balance decision nobody has made, and inventing it to close a checkbox would be worse than the gap. The same ruling is why unicorn's Healing, veela's Charms and thunderbird's Transfiguration bonuses are absent from the authored tables; each omission is recorded at the line it affects.

**Remaining polish, not blocking.** There is still one wand mesh, so core, length and flexibility are legible in the tooltip and nowhere on the model — the wood tint is the only visual difference between two wands in a hotbar.

🟢 CRITICAL #2 (RESOLVED 2026-09-09) — The skill trees are still literal filler

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

**Two corrections to the finding above, found while fixing it.** The node count was 168, not 163, and `learn_spell` had already been wired on 2026-08-22 — 18 nodes carried it and every `<spell>_unlock` in SPELL_MASTERY did teach its spell. "A grep for any spell-granting effect returns 0" was true when Rev 1 wrote it and stale by Rev 2. The misnaming was real but narrower than reported: exactly **three** nodes, all in DARK_ARTS (`crucio_unlock`, `imperio_unlock`, `avada_kedavra_unlock`), plus a fourth nobody had listed — `legilimency`, which granted Dark Arts damage and had nothing whatever to do with Legilimency, an ability every wizard already holds through `PlayerStatusAbilityGrantSource`.

**What landed (2026-09-09).** The web is **168 → 107 nodes, and 100 self-labelled fillers → 30 pathways.**

	•	**The +0.5-hearts stack is gone, and cannot be rebuilt.** 41 of the 100 fillers each paid a raw attribute — fourteen separate nodes granting +0.5 max health, ten granting +0.5 armour. Not one small node grants an attribute now, and `SkillNodeJsonTest.noSmallNodeGrantsARawAttribute` fails the build if one ever does again. An attribute is the only effect type with no theme attached to it, and therefore the only one that can be pasted onto a connector without anybody deciding what that connector is for. `passive_attribute` fell from 55 of 199 effects to 14 of 151, and all 14 now sit on notables where toughness *is* the fantasy — `herbal_vitality`, `goblin_steelheart`, `keeper_vigor`.
	•	**The fillers were load-bearing, so they were dissolved rather than deleted.** They were the web's connective tissue: 57 all-small chain components sitting between notables. The purge went chain by chain — 4 dead-end spurs off the hub deleted outright, 23 single fillers becoming 15 direct notable↔notable edges and 8 real spell nodes, and 30 chains of two-to-four fillers each collapsing to **one** pathway node at the chain's centroid. Every surviving node kept or averaged its coordinates, so the constellation still reads as the same six spokes around Polaris.
	•	**A pathway is what a filler should have been.** One point, one effect, and that effect is its region's own currency rather than a generic stat: Herbology pays harvest luck, Magizoology beast resistance, Wandlore fewer misfires, Dark Arts curse damage. Magnitudes are 2–4× what the fillers paid, on a third as many nodes.
	•	**The tree's most exciting-looking nodes are no longer lies.** The three Unforgivable nodes teach their spells. Teaching is safe because knowing a spell and being allowed to cast it are separate data — `AvadaKedavra`'s requirement still demands PROFICIENT on both Imperio and Crucio, and `Module.DARK_ARTS` still ships disabled. `SkillNodeJsonTest.everyUnlockNodeTeachesItsSpell` now holds every `<spell>_unlock` node in the web to its own name. `legilimency` became `occlumency` and grants the defence half of the mind-magic pair, read at the Legilimency resist roll.
	•	**Nine new nodes hand over a spell instead of a number.** Drawn from the twelve implemented spells no node reached: Confringo, Glacius, Diffindo, Depulso, Colloportus, Levicorpus (with Liberacorpus, because somebody has to let them down), Aguamenti, Claustra Reverto and Frigora. Eight of them sit at a deleted filler's coordinates *beside* a route that also got its direct edge, so each is a fork you may take rather than a toll you must pay. `learn_spell` went from the fifth-commonest effect on the web to **the commonest** — 31 uses against `passive_attribute`'s 14.
	•	**The budget tension survived and improved.** Buying the whole wizard web costs 272 against a 60-point cap. Counting route overlap, the cheapest two keystones now land at 29 and three at 47 — leaving 13 points of real depth — while four costs 65 and does not fit. Before the purge the same three cost 40+ with almost nothing left over. The difference is entirely the halved travel tax.
	•	**Two `GameplayStat` members were added, each with its reader landing beside it.** `SPELL_MISFIRE_REDUCTION` is subtracted from the cast's misfire total in `SkillSystemAPI.applySkillModifiers` — added negatively rather than set, because misfire accumulates from the wand's fizzle, allegiance and PRECISION, and a setter would silently discard whichever ran first. `OCCLUMENCY_SHIELD` is *added* to the target's trained Occlumency in `LegilimencyServerLogic`, not multiplied: multiplying would have left an untrained wizard who bought the node at exactly zero.
	•	**`grant_ability` is shippable now.** It reached `SkillNodeAbilityGrantSource` all along and was flagged unimplemented only because it had no tooltip line. It prints the same sentence `unlock_ability` does, because it is the same benefit. **`ability_refinement` stays flagged** — `AbilityModifiers` aggregates it and no ability implementation reads the result, so a node using one would cost points and change nothing. Inventing a consumer to close the checkbox would be the same mistake as mapping wand `spell_modifiers` onto `SpellCategory`, and is refused for the same reason.
	•	**Existing saves are refunded, not stranded.** A saved allocation names node ids that no longer exist, and `respec` can only give back what `SkillTrees.byId` still resolves — so those points would have been neither spent nor refundable. `PlayerSkillData.CURRENT_VERSION` is bumped to 4, which re-runs the login refund that already exists for exactly this case, strips the attribute modifiers and revokes the web-taught spells on the way through.

**What this does not fix.** The web is still the *only* place these decisions live, and DARK_ARTS still has no keystone because its module ships disabled — authoring a branch payoff nobody can reach is content for its own sake. Verified by 35 passing skill tests; no in-game pass.

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

Potions — RESOLVED since this audit; see the addendum below. The paragraph as written was: "built then abandoned, and now known to be blocked. The brewing infrastructure is real: cauldron tiers (pewter/brass), ingredient lists, heat-time ticks, colored output brews. There are still exactly 2 brews (Wiggenweld, Mandrake Restoration) and 2 recipes. The reason it stalled is now clear: BrewDefinition only supports lists of vanilla MobEffects, which structurally blocks every signature potion — Polyjuice, Felix Felicis, Veritaserum and Wolfsbane all need effects the codec cannot express. This is not a content gap you can author your way out of; it needs a codec extension first. Meanwhile Herbology (24 skill nodes) still grows crops for potions that don't exist. Still the single biggest content-empty engine in the mod."
**Addendum, 2026-09-09 — the codec blocker is gone and the roster is filled.** Two passes closed
this, and the finding above is left as written because the reasoning is still why it mattered.

*The codec (2026-08-28).* `BrewEffect` is a sealed interface with a `Type`-dispatch codec —
the same shape `SpellEffectComponent` uses — plus a `BrewEffectEntry` carrying an optional
`on_drink` / `on_brew_complete` phase. `BrewDefinition.toBrew` wraps a legacy `effects` list into an
`apply_effects` component at load, so migration is opt-in per brew and the two original potions are
still authored the old way and still work. Brews went 2 -> 14, recipes 2 -> 12.

*The content (2026-09-09).* Three of those fourteen were still vanilla effect lists wearing canon
names, because the systems behind them did not exist. All three are now real:

- **Veritaserum** gets a `truth_serum` component and a `veritaserum` package. Truth here is
  mechanical and narrow: you cannot hold a false face. Polyjuice is reverted and refused, invisibility
  is stripped and re-stripped, you are outlined, and Occlumency reads zero so Legilimency lands. It
  touches nothing you type and reveals no private data — it opens an ability the interrogator already
  had, which is the right boundary for something you can put in a drink.
- **Draught of Living Death** gets a `living_death` mob effect: hostiles stop targeting you, because
  you read as a corpse. Blind, rooted and harmless for the duration.
- **Amortentia** gets an `infatuation` mob effect: you cannot bring yourself to strike another
  player. Indiscriminate rather than aimed at one person, because a bottle does not record its
  brewer — documented as an alpha limitation in `KNOWN_ISSUES` §5i.1 rather than faked.

Note what did *not* need a new component type. Two of the three are a mob effect plus one event
handler, which is the cheap half of the seam: a component type is for a potion that must hand off to
a system with state (Felix's cooldown, Polyjuice's identity, Veritaserum's compulsion). A potion whose
whole behaviour is "while this is on you, X does not happen" is better as an effect, because then
`/effect`, splash bottles, milk and a healing draught's `cure` list all work on it for free.

*Herbology.* Nine of twelve recipes were built entirely from vanilla items while the mod's own flora
was consumed by nothing. Ten of twelve now use mod ingredients. Two silent defects turned up
underneath: `wizards_and_beasts:mandrake_crop` was **not in the `minecraft:crops` block tag**, so
every Herbology harvest bonus did nothing on the mod's only crop (`CropBlock` is a superclass, that
tag is data); and `essence_of_dittany` is a registered canon item with **no source anywhere**, so a
recipe asking for it would never have matched. Both fixed, both now covered by
`ShippedBrewDataTest`.

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
RPG Progression	4	5/10 → 7/10	Player stats give a real, trainable growth axis; 100 of 163 tree nodes are still self-labelled filler. *(Re-scored 2026-09-09: 30 pathways of 107 nodes, `learn_spell` is the commonest effect on the web, and three keystones is a real all-in choice against the 60-point cap.)*
Exploration	3	3/10	Two set-piece structures. Magical flora is not magical geography.
Creature Design	4	5/10	111 rigs and 162 textures land; box-per-bone fidelity, stale placeholder markers and zero taming/breeding hold it down.
Worldbuilding	5	5/10	The text (naming, heritage prose, handbook) is magical; the world you walk through still is not.
Atmosphere	5	6/10	Real rigs and textures shipped alongside the beam/particle/Patronus VFX; procedural GUIs and all-vanilla audio still pull down.
UX	4	5/10	Heritage choice is now informed. Wand math is still invisible and the trees still lie in their filenames.
Multiplayer Design	6	6/10	Real sync discipline (server-authoritative casts, synced Patronus/stats). Unblockable instakill still needs review.
Balance	5	5/10 → 6/10	Proficiency curve and Avada gating are well-tuned; inert woods and filler progression still muddy the decisions. *(Re-scored 2026-09-09: the filler half is fixed; the wand half was fixed on 09-08.)*
Replayability	4	5/10	10 heritages / 31 variants (Rev 1 undercounted at 5) is a real replay hook; filler trees and no procedural world undercut it.
Technical Quality	7	7/10	Genuinely solid data-driven engineering; docked for the core split, stale markers, a dead knob, and three uncommitted feature branches in one tree.
Overall	5	6/10	Two of three Rev-1 criticals moved. The mod crossed from "engineered skeleton" to "engineered skeleton with skin."

5. The requested reports

A. Executive summary

Doing well: Lore authenticity, spell design (interaction + mastery + gating), heritage-driven identity, the new innate-stat cast layer, and the underlying engineering. Held back by: the wand's choice is still invisible and 60% unwired, the skill trees are still self-admitted filler, and the beasts have bodies but no relationships. Strongest identity: canon-faithful spellcasting where your wand, your bloodline and your practice all measurably change the spell. Path to exceptional: stop adding systems and finish the five that are already 40–80% built — surface the wand's numbers, author the remaining wood/core data, replace filler nodes with real forks, and generalise the Niffler's bond layer. (The wand and the brew codec are both done as of September; see their addenda.)

B. Top 10 problems (by player impact)

	1.	🔴 The wand's derived cast stats are invisible to the player — the Ollivander fantasy still resolves to unreadable numbers. (§Crit-1)
	2.	🟢 *Closed 2026-09-09.* 100 of 163 skill nodes are named filler, and the *_unlock nodes unlock nothing. (§Crit-2) — now 30 pathways of 107, no small node grants an attribute, and every `<spell>_unlock` teaches its spell under a test.
	3.	🔴 96 creatures, zero taming/breeding — the Niffler proves the pattern and is alone in it. (§Crit-3)
	4.	🟠 6 of 10 wand woods and all 8 wand cores contribute nothing to a cast; the dual-system bug migrated from woods to cores.
	5.	🟢 Brewing — RESOLVED. The codec seam landed 2026-08-28 and the signature roster was filled 2026-09-09: 14 brews, 12 recipes, and Veritaserum / Living Death / Amortentia are real rather than vanilla effect lists.
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
	•	~~Extend BrewDefinition past vanilla MobEffect lists.~~ Done 2026-08-28, filled 2026-09-09. What is left in brewing is a failure component and an `on_brew_complete` user — see `KNOWN_ISSUES` §5i.3.
	•	Generalise the Niffler's bond layer onto GenericBeastEntity. The hard part is written and shipping; a dozen signature beasts could inherit it.
	•	Clear the 93 stale _comment markers. Trivial, and it stops the data lying about itself.
	•	Upgrade rig fidelity on the signature dozen. The Horntail is the quality bar and it was hand-built — the recipe exists.

G. Remove / Rework / Simplify / Keep / Expand

	•	KEEP: Spell casting + proficiency, the player-stats cast layer, heritage system, wand allegiance, broom physics, module/networking foundation, the Niffler.
	•	REWORK: Wand cores (migrate to datapack cast_modifiers, delete the enum switch) — *done 2026-09-08*; the whole skill-tree node set (replace filler with forks; make spell-unlock nodes actually unlock or rename them honestly) — *done 2026-09-09*.
	•	SIMPLIFY: 8 skill trees → fewer, denser trees; collapse the 5-node stub trees into their heritages or cut.
	•	REMOVE: The 93 stale PLACEHOLDER box rig comments; either wire spell_modifiers to a real SpellCategory mapping or drop the field.
	•	EXPAND: creature bonding, everyday exploration structures, rig fidelity on the signature dozen. (Potions are no longer on this list.)

H. Recommended development order

	1.	Surface the wand. Tooltip the resolved cast stats. Highest leverage, smallest change, fixes the core fantasy — Crit-1.
	2.	Finish the wand data. 6 woods + 8 cores onto cast_modifiers; delete the core enum switch. Kills the dual-system bug for good.
	3.	~~De-filler the skill trees (fixes fake depth — Crit-2). Convert minor_* clusters into meaningful forks; make spell-unlock nodes actually unlock, or rename them.~~ **Done 2026-09-09.**
	4.	~~Unblock brewing at the codec, then fill it and tie Herbology to it.~~ Done — 14 brews, and ten of twelve recipes are built on the mod's own flora.
	5.	Generalise Niffler bonding to a signature dozen beasts (fixes the "Beasts" half — Crit-3).
	6.	Upgrade rig fidelity on those same dozen, using the Horntail as the bar.
	7.	Seed everyday magical exploration (a wizard hamlet, a hedge-witch cottage, a ruin with a spellbook).
	8.	Commit and verify. Three feature-sized changes are sitting uncommitted, and neither the heritage restyle nor the player-stats wiring has had an in-game pass. Balance/polish after that; only then consider new content.

6. Final design question

If Wizards & Beasts shipped tomorrow, after 10 hours players would remember: "A gigantic, lore-accurate wizard mod where I picked a heritage that actually changed how I cast, learned some genuinely cool canon spells, raised a Niffler… and then the skill tree gave me +0.5 hearts fourteen times, my wand was a name with no numbers, every other beast was a jointed lump that ignored me, and there were no potions." They'd respect the ambition and the spell feel, and bounce off the emptiness behind it.

What they should remember instead: "The wand that chose me and fought better in my hand than a stolen one — and I could see exactly why. The Niffler I raised, and the Hippogriff that finally let me near it. The Wolfsbane I brewed the night before the full moon. The Patronus I conjured when the Dementors came. The duel I won by disarming someone and claiming their wand's allegiance."

Rev 1 called the gap between those two paragraphs a depth-and-presentation gap. Rev 2 narrows it further: it is a finishing gap. Every pillar in that second paragraph is 40–80% built and stalled one deliberate step short of being felt — the wand reader is wired but silent, the rigs shipped but the data still calls them placeholders, the bond system works on exactly one animal. Nothing here needs a new system. It needs the last mile on five existing ones. Close that and this becomes the mod where players think: "This is what it feels like to actually be a wizard in Minecraft."

—

Verification notes: every number above was read out of the working tree on 2026-08-10, not from prior audit docs. Uncommitted at time of writing: GuiScaleHelper, HeritageDossierRenderer, HeritageSelectionScreen, plus an untracked HeritageNameContrastTest. The heritage restyle and the player-stats wiring have not had an in-game pass. Rig fidelity was measured as cubes-per-bone across all 111 entity geometries; "sculpted" means >1.4.
