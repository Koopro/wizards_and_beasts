# Item & system audit — 2026-10-21

Baseline: `dev` @ `d6925446`. Produced for the cohesive-RPG pass (master prompt 2026-10-21, §4.4) by four read-only
audit passes over the source, then spot-verified by hand where marked **[verified]**. Everything else is an
audit claim with a `path:line` attached — check the line before acting on it (lesson: *verify source, not the
stale audit*). Entries the passes did not inspect say **UNVERIFIED**.

Related, not duplicated here: `MAGICAL_ARTEFACT_STATUS.md` (artefact design notes), `DEAD_END_AUDIT.md`
(flag/dead-end ledger), `WAND_SYSTEM_AUDIT.md`, `CREATURE_QUALITY_MATRIX.md`, `SPELL_TRACING_DESIGN.md`.

## Verified headline findings

| # | Finding | Status |
|---|---|---|
| V1 | Mirror of Erised, Vanishing Cabinet, Sword of Gryffindor + 9 more canon artefacts are bare `Item::new` (`registry/CanonItemRegistry.java:34-63`) | **[verified]** |
| V2 | 30 skill nodes taught spells with no eligibility check (`SkillSystemAPI.teachSpell`) | **[verified]**, fixed on `feat/spell-tracing` (lessons + identity gates) |
| V3 | Episkey/Levicorpus books unreadable: mastery gate compared bare id to canonical hit ledger | **[verified]**, fixed on `feat/spell-tracing` |
| V4 | Remembrall and Sneakoscope share the identical shaped recipe (`G`/`GRG`/`G`, glass+redstone) | **[verified]** |
| V5 | `rougarou_hair` has loot + `wand_cores` entry but no wandmaking recipe | **[verified]** |
| V6 | `HorcruxDestruction.destroy` is the single soul-fragment release; `BasiliskFangItem` its only caller | **[verified]** |



---

## Artefact / Canon / Utility Item Audit — Wizards & Beasts (worktree `C:/tmp/wb-tracing`, branch `feat/spell-tracing`)

Read-only. No gradle run. Paths are relative to `src/main/java/at/koopro/wizardsandbeasts/` (Java) and
`src/main/resources/data/wizards_and_beasts/` or `src/generated/resources/data/wizards_and_beasts/` (data, written `data:` / `gen:`).

### Method

- Every item id from `registry/{Canon,DarkArtefact,Trinket,Misc,Lore,Currency}ItemRegistry.java`, `ModBlocks` block items and
  `travel/TravelItems.java` was indexed against **every JSON** under `src/main` + `src/generated` data (excluding tags/data_maps/curios/recipe-advancements)
  and against **every Java file** outside `registry/` and `datagen/` (constant name or quoted id). Script: scratchpad `acq.py`, output `acq.txt`.
- Shaped/shapeless recipes were canonicalised and compared for collisions (scratchpad `recipes.py`).
- Both structure NBTs (`structure/azkaban.nbt`, `structure/chamber_of_secrets/chamber.nbt`) were decompressed and scanned: **no chest, no loot table, no mod item** in either.
- Location/market JSON was read for `buys` (trader buys from player — NOT an acquisition route) vs `sells` / rank `rewards` (acquisition).
- Default module states (`module/ModuleDefaults.java`): DARK_ARTS=DISABLED (:28), CHAMBER_OF_SECRETS=DISABLED (:56), MINISTRY=DISABLED (:60), ARTEFACTS=ENABLED (:70), GRINGOTTS/POCKET_DIMENSIONS/BROOM_FLIGHT=ENABLED, OWLS=PREVIEW (reachable).
  A global gate (`event/module/ModuleContentGate.java:36-60`) cancels right-click use of any item whose module is off.

Status legend: **complete** = does its defining thing; **partial** = works but defining interaction missing/broken;
**decorative** = intentionally does nothing beyond vanilla; **placeholder** = behaviourless `Item::new` stub; **disabled** = gated off. 
"Default-off" = functional, but its module ships DISABLED. "Unreachable" = no recipe / loot / offer / reward / structure route.

### Doc claims vs source

| Doc claim | Verified? |
|---|---|
| MAGICAL_ARTEFACT_STATUS: Time-Turner, Portkey, Resurrection Stone rebuilt, "Creative" | TRUE (no route found for any of them in survival, except Portkey now: Ministry `sells` + `commended` rank reward — added after the doc, but MINISTRY ships DISABLED) |
| "Basilisk fang — Basilisk loot" | TRUE but basilisk spawns only via Chamber spawn override / breeding ritual, both gated by CHAMBER_OF_SECRETS (DISABLED); Knockturn sale gated by MINISTRY (DISABLED) → unreachable in default world |
| "Remembrall … Recipe" / "Sneakoscope … Recipe" | Both recipes exist but are **identical** (see P1-1) — one shadows the other |
| C-10 Remembrall honestly decorative | TRUE (`item/trinket/RemembrallItem.java:12`, no overrides) |
| §5.3 Gaunt ring `RING_STONE_PRESENT` never written | STILL TRUE (`item/darkartefact/GauntRingItem.java:25`, only reader; default `true`) |
| §5.2 Pensieve: own memories only | TRUE, plus tooltip counter is dead (P2) |
| §5.5 Foe-Glass counts every player | STILL TRUE (`item/trinket/FoeGlassItem.java:123`; javadoc says "not on holder's team", code ignores teams) |
| DEAD_END_AUDIT: `philosophers_stone` skill flag unread; Stone has no acquisition | STILL TRUE |
| CANON_AUDIT C-9 Stone = Elixir, C-7 Hand of Glory | TRUE (`item/consumable/ElixirOfLife.java`, `event/item/HandOfGloryTickHandler.java`) |
| CanonItemRegistry header: "behaviourless stubs" | TRUE for 73/79; 4 promoted (fang, servicing kit, Standard Book, telescope) and 2 used as trade materials |

### Main table

`Reg` = registration path:line. `Impl` = implementation class path:line (class decl / use method).

#### A. Legendary / Hallows / Dark artefacts

| Item (id) | Reg | Impl | Status | Acquisition (proved) | Behaviour (1 line) | Persistence / MP | Uses / deps | Bugs / missing feedback | Tests | Next action | Pri | Form |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Invisibility Cloak `invisibility_cloak` | registry/DarkArtefactItemRegistry.java:44 | item/cloak/CloakItem.java:26 (use :72) | complete | **NONE** (no recipe/loot/offer; only debug/gametest refs) — unreachable | Curios back-slot invisibility on Demiguise charges, reweavable | charges on stack; equip via EquipmentAPI | ARTEFACTS module; demiguise/CloakCharges | `interactLivingEntity` puts cloak in a mob's CHEST slot (:107), contradicting the registry comment "not the chest" | EquipmentTests cloakEffects/migratesChestCloak; src/test demiguise/CloakChargesTest | Add route: Demiguise-hair weaving recipe or Magizoology rank reward | P1 (group dead-end) | equipment |
| Cloak of Invisibility (Hallow) `deathly_hallow_cloak` | DarkArtefactItemRegistry.java:47 | CloakItem.java:26 | complete | **NONE** — unreachable | Never-fading invisibility that hides equipment | as above | as above | same CHEST-slot path | EquipmentTests | Quest/structure reward only (unique per world) | P2 | equipment / quest object |
| Resurrection Stone `resurrection_stone` | DarkArtefactItemRegistry.java:39 | item/hallow/ResurrectionStoneItem.java:26 (use :54); item/hallow/ShadesOfTheDead.java | complete, default-off (DARK_ARTS) | **NONE** — unreachable | 3 turns → 2 min shades; Dementors can't feed on holder | `SHADES_UNTIL` attachment (saved); turn counter on stack | DementorAura.canFeedOn; corruption +3 | Tooltip "Times used: n" (:48) actually shows the pending turn count; turn counter never decays (turn 1 today, turn 2 next week); hard-coded English literals | ArtefactTests.shadesKeepDementorsOff | Obtain by prising from Gaunt ring (writes RING_STONE_PRESENT=false) | P2 | item (quest object) |
| Philosopher's Stone `philosophers_stone` | DarkArtefactItemRegistry.java:34 | item/consumable/PhilosophersStoneItem.java:29 (use :60); ElixirOfLife.java | complete, default-off | **NONE** — unreachable | One Elixir draught/day spares next death | `ELIXIR_UNTIL`/`ELIXIR_NEXT_DRAUGHT` attachments | DARK_ARTS gate (:61) although alchemy is not Dark (Module tag `dark_arts`) | Gated by DARK_ARTS (disabled) → useless in default world even if obtained; `PHILOSOPHERS_STONE_DESTROYED` never written; `elixir_of_life` stub item exists but the Stone never yields it | CanonRulingTests.elixirSparesOneDeath | Move to ARTEFACTS; produce `elixir_of_life` flask item (1/day) instead of direct effect | P2 | item → produces consumable |
| Elixir of Life `elixir_of_life` | CanonItemRegistry.java:40 | Item::new | placeholder | NONE | nothing (tooltip promises "extends life") | — | — | Duplicate concept with Stone effect | CanonItemCatalogTest only | Make it the Stone's output | P2 | consumable |
| Tom Riddle's Diary `riddles_diary` | DarkArtefactItemRegistry.java:17 | item/darkartefact/RiddlesDiaryItem.java:26; diary/DiaryService.java | complete, default-off | **NONE** — unreachable (tooltip says "Source: Chamber of Secrets", Chamber has no loot) | Writes back, corruption per entry, possession at 85 | `DIARY_POSSESSING`, `SOUL_FRAGMENT_INTACT` components | DarkCorruptionService, HorcruxBearerTickHandler, Sneakoscope | literals not lang-keyed | CreatureWildlifeTests.basiliskEyesFangsAndVenom (destroy) | Chamber of Secrets reward | P1 (group) | item (quest object) |
| Marvolo Gaunt's Ring `marvolo_gaunts_ring` | DarkArtefactItemRegistry.java:20 | item/darkartefact/GauntRingItem.java | partial | NONE | Horcrux aura only; tooltip claims it bears the Stone | stack components | — | `RING_STONE_PRESENT` read-only (:25) → always "Bears the Resurrection Stone"; nothing links ring↔Stone | none specific | Implement "prise stone" interaction or drop the line | P2 | item |
| Slytherin's Locket `slytherins_locket` | DarkArtefactItemRegistry.java:23 | SlytherinsLocketItem.java; event/item/HorcruxBearerTickHandler.java | partial | NONE | Held: mental-stability drain + corruption | — | DARK_ARTS | `HorcruxBearerTickHandler` applies to ANY intact horcrux in hand but always says "The locket pulls cold" (:54); only hands checked (:60), not "carried" | none | Per-vessel whisper; check inventory | P3 | item |
| Hufflepuff's Cup `hufflepuffs_cup` | :26 | HufflepuffsCupItem.java | partial | NONE | Generic horcrux aura + tooltip | — | — | — | none | Give unique trait or keep as loot | P3 | item |
| Ravenclaw's Diadem `ravenclaws_diadem` | :29 | RavenclawsDiademItem.java | partial | NONE | Generic horcrux aura + tooltip | — | — | Tooltip "destroyed by Fiendfyre" — no Fiendfyre path | none | — | P3 | item / equipment |
| Basilisk Fang `basilisk_fang` | CanonItemRegistry.java:54 | item/darkartefact/BasiliskFangItem.java:34 (use :44); HorcruxDestruction.java | complete; default-unreachable | `loot_table/entities/basilisk.json`; Knockturn `sells` (`locations/knockturn_dealer.json:65`, trusted_contact). Basilisk only spawns via Chamber spawn_override / `basilisk/ritual/BasiliskBreedingRitual.java:49` (both CHAMBER_OF_SECRETS) and Knockturn is MINISTRY → **no route in default world** | 6 venom doses on hit; main-hand fang + off-hand horcrux destroys it | durability | Ministry/Knockturn contracts (`ministry.json:36`, `knockturn_dealer.json:36`) demand it | Horcrux destroy `consume(1)` (:66) eats the whole fang regardless of remaining doses; horcrux use FAILs when DARK_ARTS off (default) with no message | CreatureWildlifeTests basiliskEyesFangsAndVenom; EconomyLoopTests | Spend one dose instead of the stack; feedback when DARK_ARTS off | P2 | item (weapon) |
| Dark Mark `dark_mark_brand` | TrinketItemRegistry.java:40 | item/trinket/DarkMarkItem.java:35 (:72 brand, :143 take/call) | complete, default-off | **NONE** | Take the Mark; 2 s brand on another player; sneak-call all marked | `DARK_MARK` attachment, copyOnDeath | Sneakoscope reads it | **Non-consensual permanent brand** on any player in reach; only admin command removes it (`command/PlayerConditionCommands.java:138`); call broadcasts caster coordinates to all marked | none | Consent/PvP check, removal path | P2 (MP) | item |
| Blood-pact vial `blood_pact_vial` | TrinketItemRegistry.java:50 | item/bloodpact/BloodPactVialItem.java:37 (use :53) | complete, default-off | **NONE** — Knockturn only *buys* it (`knockturn_dealer.json:59`, inside `buys`), nobody sells it | Propose/accept binding pact | `ACTIVE_BLOOD_PACTS` attachment; static `PENDING_PROPOSALS` map (not saved, fine) | bloodpact system | dead market line (trader buys an unobtainable item) | none | Add recipe or remove the buy line | P2 | item |
| Hand of Glory `hand_of_glory` | TrinketItemRegistry.java:44 | item/trinket/HandOfGloryItem.java:19; HandOfGloryTickHandler.java | complete, default-off | `data:recipe/hand_of_glory.json` (DARK_ARTS condition) | Lit: holder sees through darkness | `HAND_OF_GLORY_CANDLE_LIT` component | Ministry contract `surrender_hand_of_glory` (`ministry.json:41`), Knockturn buys | literals | CanonRulingTests.handOfGloryBlindsNobody; ArtefactTests | — | P3 | item |
| Sword of Gryffindor `sword_of_gryffindor` | CanonItemRegistry.java:36 | Item::new | placeholder | NONE | nothing — not even a weapon | — | Horcrux tooltips cite it as destroyer (GauntRing/Locket) | Horcrux destruction only via fang | CanonItemCatalogTest | Sword item (SwordItem stats) that destroys horcruxes once it has "taken in" basilisk venom; Chamber reward / Sorting Hat pull | P2 | item (weapon) / quest object |
| Opal Necklace, Secrets of the Darkest Art, Moste Potente Potions, Shrunken Head | CanonItemRegistry.java:58, :60, :62, :66 | Item::new | placeholder | NONE | nothing | — | — | — | CanonItemCatalogTest | Opal Necklace → cursed TreasureArtefact; books → LoreTome/SpellSource | P3 | item / book |

#### B. Detection, communication, transport, utility artefacts

| Item (id) | Reg | Impl | Status | Acquisition | Behaviour | Persistence / MP | Uses / deps | Bugs / missing feedback | Tests | Next action | Pri | Form |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Marauder's Map `marauders_map` | MiscItemRegistry.java:60 | item/map/MaraudersMapItem.java:51 (use :98); map/* | complete | `data:recipe/marauders_map.json` (ARTEFACTS) | Unfold → charted atlas with tracked people | MaraudersMapAtlasStore, per-map id/trust | ARTEFACTS | **Recipe consumes a wand** (`"W": "#wizards_and_beasts:wands"`, no crafting remainder anywhere in code) — can eat a bonded/purchased wand | src/test map/MapGeometryTest, MaraudersMapTrackerScalingTest, ModuleTagDataTest | Replace W with a non-wand ingredient or add remainder | P1 | item |
| Sneakoscope `sneakoscope` | TrinketItemRegistry.java:28 | item/trinket/SneakoscopeItem.java:61 (:83 tick, :114 focus); sneakoscope/* | complete | `data:recipe/sneakoscope.json` — **collides with Remembrall** | Spins near deceit/threat; focus mode on cooldown | — | reads DARK_MARK, horcruxes, disguise | Recipe collision (P1-1) | src/test SneakoscopeTuningTest | Change one recipe | P1 | item |
| Remembrall `remembrall` | TrinketItemRegistry.java:22 | item/trinket/RemembrallItem.java:12 | decorative | `gen:recipe/remembrall.json` (from `datagen/ModRecipeProvider.java:179`) — **identical pattern/keys to sneakoscope** (` G `/`GRG`/` G `, glass+redstone) | nothing (animated model only) | — | — | recipe collision; lang desc "helps recall forgotten things" overpromises | CanonItemCatalogTest | Give a reminder function (Ministry hearing/contract deadline glow) or retire recipe | P1 (collision) | item |
| Two-Way Mirror `two_way_mirror` | TrinketItemRegistry.java:42 | item/trinket/TwoWayMirrorItem.java:21; mirror/MirrorPairing.java; mirror/MirrorSessionManager.java | complete (with bug) | `data:recipe/two_way_mirror.json` (ARTEFACTS) | Sneak-attune pair, speak name → live session (partner yaw/pitch presence) | pair UUID on stack; sessions in static maps (ephemeral) | MirrorConnectC2SPayload | **One global static pending slot** (`MirrorPairing.java:22,48`): concurrent pairings on a server overwrite each other, and a first mirror whose 5-min window expires keeps its pair id and is refused re-attuning forever (`:30`) → bricked crafted item. First mirror never gets `MIRROR_RECIPIENT_NAME` (only twin, :39) → tooltip shows a UUID. Any third player can steal a pending pair. | AuditRegressionTests.mirrorSessionWithTwoMirrors; ArtefactTests module tag | Pair both mirrors in one action (craft 2 linked, or sneak-use with twin in off-hand); pending keyed per attuner | P1 | item |
| Foe-Glass `foe_glass` | TrinketItemRegistry.java:38 | item/trinket/FoeGlassItem.java:26 (use :50) | complete | `data:recipe/foe_glass.json` | 2 s peer → foes in 32 blocks, nearest bearing | — | — | `isFoe` returns true for every player (:123), javadoc says team-aware; literals | ArtefactTests (module) | Use team/Trace hostility record | P3 | item (or block) |
| Portkey `portkey` | TrinketItemRegistry.java:30 | item/trinket/PortkeyItem.java:30; portkey/PortkeyService.java | complete; default-unreachable | `locations/ministry.json:72` `sells` (1479 Knuts, weekly 1) + `commended` rank reward (`ministry.json:23`). MINISTRY ships DISABLED → no route by default | Sneak-use sets block+dimension; 3 s tug moves holder + players within 1.5 → spent | target/dimension components | — | Arrival checks only `noCollision` (:76): no lava/void/support check (cf. ApparitionLanding), **no ward check** (ApparitionWardRegistry) — can land in Azkaban/warded areas; drags non-consenting players within 1.5 blocks | ArtefactTests.portkeyCarriesEveryone | Reuse ApparitionLanding.resolve + ward check; opt-in for others | P2 | item |
| Time-Turner `time_turner` | TrinketItemRegistry.java:26 | item/trinket/TimeTurnerItem.java:37; timeturner/* | complete | **NONE** — unreachable | Wind 1–3 turns → back to own position N hours ago | trail attachment non-saved (by design) | ARTEFACTS | only mob-target danger check (PvP escape allowed); arrival no lava check (`TimeTurnerService.java:102`) | ArtefactTests.timeTurnerTakesTheWearerBack; src/test TimeTurnerRulesTest; EquipmentTests | Ministry rank reward / N.E.W.T. capstone | P1 (group) | equipment (neck) |
| Pensieve `pensieve` | TrinketItemRegistry.java:46 | item/trinket/PensieveItem.java:20 (use :37); network/trinket/PensieveOpenS2CPayload | partial | **NONE** — unreachable | Opens read-only list of own memories | `PlayerMemoryData` | memory/MemoryService (HAPPY only, PAINFUL never written) | Tooltip "Memories stored: n" reads `PENSIEVE_MEMORIES_STORED` which nothing writes (always 0); tooltip promises "extract memories with a wand" — no such mechanic; `memory_vial` stub unused | ArtefactTests (module) | Pensieve **block** + `memory_vial` item to share memories | P2 | block |
| Hermione's beaded bag `hermiones_beaded_bag` | TrinketItemRegistry.java:48 | item/trinket/HermionesBagItem.java:21; HermionesBagMenu.java | complete | **NONE** — unreachable | 54-slot bag stored on stack | `HERMIONES_BAG_INVENTORY` component; saves on close | — | — | AuthorityTests (dupe guard) | Add Undetectable-Extension recipe / Pocket tier | P1 (group) | item |
| Deluminator `deluminator` | MiscItemRegistry.java:53 | item/deluminator/DeluminatorItem.java:38 (use :122); deluminator/DeluminatorBlockActions.java; DeluminatorReturnService | complete (with bug) | `gen:recipe/deluminator.json` | Open lid, absorb/restore lights in r=8, place light blocks, guide-return | stored lights on stack | ARTEFACTS (global gate) | **Flips `LIT` on ANY block in a 17³ cube** (`DeluminatorBlockActions.java:193,230`) — redstone torches/lamps, furnaces, campfires, candles, copper bulbs; restore lights idle furnaces/unpowered lamps; **no mayInteract/spawn-protection/claim check**; 4913-block scan per click | AnimatedItemAssetsTest only | Whitelist light blocks via tag; respect `mayInteract` | P1 | item |
| Omnioculars `omnioculars` | TrinketItemRegistry.java:24 | item/trinket/OmniocularsItem.java:6 (extends SpyglassItem) | decorative | `gen:recipe/omnioculars.json` | vanilla spyglass | — | — | — | OmniocularsScopeTextureTest, ItemUsePosePassTest | Replay/slow-mo tie-in with Quidditch track or leave | P3 | item |
| Extendable Ears `extendable_ears` | TrinketItemRegistry.java:36 | item/trinket/ExtendableEarsItem.java:5 | decorative | `data:recipe/extendable_ears.json` | nothing | — | — | crafted but inert; costs demiguise hair | none | Eavesdrop on nearby chat/NPC dialogue or remove recipe | P2 | item |
| Peruvian Instant Darkness Powder / Decoy Detonator | TrinketItemRegistry.java:53, :55 | item/projectile/WizardingProjectileItem.java:14; entity/spell/WizardingThrownKind | complete | `data:recipe/peruvian_instant_darkness_powder.json`, `data:recipe/decoy_detonator.json` | thrown darkness cloud / distraction | — | — | — | none found (UNVERIFIED) | — | P3 | item (throwable) |
| Ministry licence scroll `ministry_license_scroll` | TrinketItemRegistry.java:33 | item/trinket/MinistryLicenseScrollItem.java:44 | complete (UNVERIFIED detail) | `data:recipe/ministry_license_scroll.json`; LicenceOffice | licence paper | — | ministry/licence | — | EconomyTests, MinistryCaseworkTests | — | P3 | item |
| Telescope `telescope` | CanonItemRegistry.java:163 | education/TelescopeItem.java:28 | complete (UNVERIFIED detail) | `data:recipe/telescope.json` (OWLS) | Astronomy observation | — | education | — | AstronomyAndPaperTest | — | P3 | item |
| Broomstick Servicing Kit | CanonItemRegistry.java:101 | item/broom/BroomServicingKitItem.java:27 | complete | `data:recipe/broomstick_servicing_kit.json`; broom_shop `sells` | strips fittings/dye, 16 uses | durability | broom | — | TravelTests | — | P3 | item |
| Standard Book of Spells | CanonItemRegistry.java:151 | item/spell/SpellSourceItem.java:58 | complete | loot: `gen:loot_table/chests/hidden_wizarding_cache.json`, SpellbookLootModifier (villages/strongholds/mansions) | readable spell source | — | spell learning | — | SpellLearningTests | — | P3 | book |
| Torn spell page | MiscItemRegistry.java:49 | SpellSourceItem | complete | `data:loot_modifiers/spell_pages_in_ruins.json` | one-shot spell source | — | — | — | SpellLearningTests | — | P3 | book |
| Lore tomes ×3 (`a_history_of_magic`, `hogwarts_a_history`, `rise_and_fall_of_the_dark_arts`) | LoreItemRegistry.java:13-20 | item/lore/LoreTomeItem.java:30 | complete | recipes + `loot_table/chests/scholars_shelf.json` | one-time Knowledge/OWL credit | read-tracking | education/ExamQuestions | — | none specific (UNVERIFIED) | — | P3 | book |
| Earmuffs / Blindfold | MiscItemRegistry.java:24, :28 | item/wearable/EarmuffsItem.java:33, BlindfoldItem.java:18 | complete | recipes | Mandrake scream / basilisk gaze protection | — | — | — | MandrakeScreamTest, BasiliskGazeTest | — | P3 | equipment |
| Bestiary, Ministry Handbook, Duelling Dummy, Floo Powder | MiscItemRegistry.java:20, :31, :57, :63 | various | complete | recipes (+apothecary sells floo powder) | journal / guide / target / Floo fare | — | — | — | various | — | P3 | book / entity / item |
| Treasure artefacts ×4 (`ancient_goblet`, `warded_amulet`, `sealed_casket`, `tomb_medallion`) | CurrencyItemRegistry.java:29-40 | treasure/TreasureArtefactItem.java:21; TreasureLootModifier, CurseBreaking | complete | `data:treasure_sites/*.json` inject into vanilla structure chests (GRINGOTTS) | cursed loot to break and sell | value/curse components | market `kind: treasure` | — | LocationTests | — | P3 | item |
| Trunks: `enchanted_trunk`, `expanded_trunk`, `newts_case_item`, `masters_trunk`, `moodys_trunk` | ModBlocks.java:225, :233, :258, :241, :249 | TrunkBlock (UNVERIFIED — not inspected) | complete (UNVERIFIED) | enchanted: `data:recipe/pocket_case.json`; expanded: `recipe/expanded_trunk.json`; all three via `career_tracks/magizoologist.json`; **masters/moodys: NONE** | placed pocket-dimension entrance | TrunkRegistryData | POCKET_DIMENSIONS | masters/moodys unreachable | UNVERIFIED | add upgrade recipe | P2 | block |
| Courier parcel | travel/TravelItems.java:12 | travel/courier/CourierParcelItem.java | complete (UNVERIFIED) | issued by CourierService (contract) | delivery object | — | travel | — | TravelTests, CareerTests | — | P3 | quest object |

#### C. CanonItemRegistry stubs (all `Item::new` or `AnimatedItem.Plain::new`, acquisition NONE unless noted)

Every id below was grepped across all data JSON: **zero** recipe/loot/offer/structure hits unless stated. Tests: only
`src/test/.../registry/CanonItemCatalogTest.java` (sprite, name, canon citation, no collision). Tooltip = lang `.desc`
via `client/ItemDescriptionTooltipHandler.java:32`.

| Family | ids (CanonItemRegistry.java line) | Status | Acquisition | Notes / bugs | Next action | Pri | Recommended form |
|---|---|---|---|---|---|---|---|
| Legendary | `tales_of_beedle_the_bard` :34, `mirror_of_erised` :38 (animated), `goblet_of_fire` :42, `triwizard_cup` :44, `golden_egg` :46, `sorting_hat` :50 (also SheetIcons icon) | placeholder | NONE | Mirror desc is just the inscription; Triwizard Cup is a canon Portkey (could reuse PortkeyService) | Erised → block (shows desire = best item / missing bestiary entry); Sorting Hat → block/NPC for House choice + Sword pull; Goblet → block for tournament signup; Cup → pre-set Portkey reward; Beedle → LoreTome | P2 (Erised, Hat), P3 rest | block / quest object / book |
| Vanishing Cabinet | `vanishing_cabinet` :56 | placeholder | NONE | desc "What enters one leaves the other" promises a function | Linked-pair **block** (see pattern A below) — **done 2026-10-22** (`artefact/cabinet/`) | P2 | block (paired) |
| Dark curios | `opal_necklace` :58, `secrets_of_the_darkest_art` :60, `moste_potente_potions` :62, `shrunken_head` :66 | placeholder | NONE | — | necklace → cursed TreasureArtefact; books → LoreTome (DARK_ARTS) | P3 | item / book |
| Materials (used) | `acromantula_venom` :64, `murtlap_essence` :212 | partial (trade material only) | venom: `loot_table/entities/acromantula.json`, apothecary/knockturn sells; murtlap: `loot_table/entities/murtlap.json` | No brewing use found; both are contract objectives | Wire as brewing ingredients | P3 | ingredient |
| **Essence of Dittany** | `essence_of_dittany` :210 | placeholder | **sold** `locations/apothecary.json:212` (87 Knuts) | Player pays for a behaviourless item; the working `dittany` (`dittany/Dittany.java`) is a different item | Give it Dittany healing (stronger) or delist | P1 | consumable |
| Remedies | `draught_of_peace` :214, `blood_replenishing_potion` :216 | placeholder | NONE | conflicts with brew system (registry comment says potions became brews) | Convert to brews and delete item ids | P2 | brew |
| Detection | `secrecy_sensor` :70, `probity_probe` :72, `revealer` :74 | placeholder | NONE | — | Probity Probe → Gringotts/Ministry entry check vs disguise/Polyjuice | P3 | item |
| Communication | `howler` :78 (animated), `da_galleon` :80, `daily_prophet` :82, `the_quibbler` :84, `wizarding_wireless` :86, `quick_quotes_quill` :88, `auto_answer_quill` :90 | placeholder | NONE | — | Howler → owl-post message; DA Galleon → group ping (like Dark Mark call, consensual); Prophet → news of server events | P3 | item / block (wireless) |
| Transport | `knight_bus` :94, `flying_ford_anglia` :96, `thestral_carriage` :98 | placeholder | NONE | registry comment says these want entities | entity | P3 | entity |
| Weasleys' Wizard Wheezes | `skiving_snackbox` :106, `puking_pastilles` :108, `nosebleed_nougat` :110, `fainting_fancies` :112, `fever_fudge` :114, `headless_hat` :116, `shield_hat` :118, `shield_cloak` :120, `shield_gloves` :122, `portable_swamp` :124, `wildfire_whiz_bangs` :126, `ton_tongue_toffee` :128, `canary_cream` :130, `dungbomb` :132, `filibusters_fireworks` :134, `punching_telescope` :136, `u_no_poo` :138 | placeholder (17) | NONE | No joke-shop location exists | One WWW shop location (location framework) + food/thrown kinds; Shield hat/cloak/gloves → equipment with minor Protego ward | P3 | consumable / throwable / equipment |
| School books & kit | `dragon_hide_gloves` :153, `monster_book_of_monsters` :155 (animated), `advanced_potion_making` :157, `quill` :159, `brass_scales` :161, `magical_draughts_and_potions` :165, `fantastic_beasts_and_where_to_find_them` :167, `quidditch_through_the_ages` :169, `one_thousand_magical_herbs_and_fungi` :171, `beginners_guide_to_transfiguration` :173, `unfogging_the_future` :175 | placeholder | NONE | `quill` / monster book used only as SheetIcons icons | Textbooks → LoreTome (subject credit); gloves → equipment for handling creatures/plants | P3 | book / equipment |
| Games | `wizards_chess_set` :177, `gobstones` :179, `exploding_snap` :181 | placeholder | NONE | — | block (chess) / cosmetic | P3 | block / cosmetic |
| Quidditch | `golden_snitch` :185 (animated, creative-tab icon), `quaffle` :187, `bludger` :189, `beaters_bat` :191, `quidditch_robes` :193 | placeholder | NONE | Quidditch track exists in travel progression but uses none of these | entities for balls, equipment for robes/bat | P3 | entity / equipment |
| Other | `chocolate_bar` :220, `memory_vial` :222, `gubraithian_fire` :224, `spellotape` :226, `mrs_skowers_mess_remover` :228, `flesh_eating_slug_repellent` :230, `self_stirring_cauldron` :232, `collapsible_cauldron` :234 | placeholder | NONE | `chocolate_bar` inert while canon Dementor remedy exists only as Chocolate Frog (`chocolate/ChocolateWardHandler`); `memory_vial` is the missing Pensieve piece | chocolate_bar → food that clears Dementor chill; memory_vial → Pensieve share token; cauldrons → cauldron block variants | P2 (memory_vial, chocolate_bar), P3 rest | consumable / item / block |

### Status counts (126 items audited)

| Status | Count | Notes |
|---|---|---|
| complete | 43 | of which **default-off** (module DISABLED): 9 (diary, Philosopher's Stone, Resurrection Stone, Hand of Glory, Dark Mark, blood-pact vial, basilisk fang's Horcrux use, Portkey sale, + fang route); **unreachable in survival**: 13 (both cloaks, Resurrection Stone, Philosopher's Stone, diary, Time-Turner, Hermione's bag, Dark Mark, blood-pact vial, masters/moodys trunk, + Portkey & basilisk fang in default config); 7 rows marked UNVERIFIED detail (trunks ×5, courier, licence scroll/telescope internals) |
| partial | 7 | Gaunt ring, locket, cup, diadem, Pensieve, acromantula venom, murtlap essence |
| decorative | 3 | Remembrall, Omnioculars, Extendable Ears (all three craftable) |
| placeholder | 73 | CanonItemRegistry stubs; 72 have no route, 1 (`essence_of_dittany`) is **sold** |
| disabled | 0 | none hard-disabled; module gating counted above |

Unreachable in survival (default config) overall: 72 placeholders + 16 functional items (both cloaks, Resurrection Stone, Philosopher's Stone, 5 Horcruxes, Time-Turner, Pensieve, Hermione's bag, Dark Mark, blood-pact vial, masters_trunk, moodys_trunk) + 2 module-locked routes (Portkey via MINISTRY, basilisk fang via CHAMBER/MINISTRY) = **90**.

### P0 / P1 findings

No P0 (no crash/data-loss path found in the inspected code).

1. **P1 — Remembrall and Sneakoscope share one recipe.** `gen:recipe/remembrall.json` (datagen `datagen/ModRecipeProvider.java:179-186`) and `data:recipe/sneakoscope.json` are both ` G ` / `GRG` / ` G ` with glass + redstone, both ARTEFACTS (enabled by default). Only one result is craftable. (Same check also found `hogwarts_flagstone` ↔ `hogwarts_stone_bricks` collide — out of scope.)
2. **P1 — Deluminator griefs/breaks redstone.** `deluminator/DeluminatorBlockActions.java:193` sets `LIT=false` on any block with a LIT property in a 17³ cube, `:230` sets `LIT=true` on any unlit one; `:244-258` has no `mayInteract`/spawn-protection/claim check. Recipe-craftable, ARTEFACTS on by default.
3. **P1 — Marauder's Map recipe destroys a wand.** `data:recipe/marauders_map.json` key `W = #wizards_and_beasts:wands`; no crafting remainder anywhere in Java.
4. **P1 — Two-Way Mirror pairing is global and can brick a mirror.** `mirror/MirrorPairing.java:22` single static `pending`; `:48` overwrite; `:30` refuses re-attune after an expired window.
5. **P1 — Essence of Dittany sold but inert.** `locations/apothecary.json:212` sells it for 87 Knuts; `registry/CanonItemRegistry.java:210` is `Item::new`, no Java reference.
6. **P1 (group) — Survival dead ends.** Both cloaks, Resurrection Stone, Philosopher's Stone, 5 Horcruxes, Time-Turner, Pensieve, Hermione's bag, Dark Mark, blood-pact vial have no route (grep proof in scratchpad `acq.txt`); blood-pact vial is only *bought* (`knockturn_dealer.json:59`). Portkey (`ministry.json:23,72`) and basilisk fang (`knockturn_dealer.json:65`, Chamber) are reachable only with MINISTRY / CHAMBER_OF_SECRETS, both DISABLED (`module/ModuleDefaults.java:56,60`). The Chamber has no reward at all (NBT has no chest).

### P2 highlights (short)

- Portkey arrival lacks lava/support/ward checks and pulls non-consenting players (`portkey/PortkeyService.java:66-86`).
- Dark Mark brands any player without consent; removal only by admin (`item/trinket/DarkMarkItem.java:113-140`, `command/PlayerConditionCommands.java:138`).
- Philosopher's Stone gated by DARK_ARTS (`PhilosophersStoneItem.java:61`) although not Dark; `elixir_of_life` stub disconnected.
- Pensieve tooltip counter dead (`PensieveItem.java:31`), "extract with a wand" promised, `memory_vial` stub unused.
- Gaunt ring `RING_STONE_PRESENT` read-only (`GauntRingItem.java:25`).
- Basilisk fang Horcrux use eats whole fang (`BasiliskFangItem.java:66`) despite 6-dose durability.
- Cloak `interactLivingEntity` puts cloak in a mob's chest slot (`CloakItem.java:107`).
- Extendable Ears craftable and inert (costs Demiguise hair).
- `draught_of_peace` / `blood_replenishing_potion` stubs duplicate the brew system.

### P3 highlights

- `HorcruxBearerTickHandler.java:54` says "locket" for every Horcrux; only hands checked (`:60`).
- Foe-Glass `isFoe` counts every player (`FoeGlassItem.java:123`), javadoc says team-aware.
- Resurrection Stone "Times used" shows turn counter (`ResurrectionStoneItem.java:48`).
- Many artefacts use hard-coded English `Component.literal` (diary, mirror, horcruxes, Hand of Glory, Dark Mark, Pensieve, bag, Stone) instead of lang keys.
- Time-Turner arrival has no lava check (`TimeTurnerService.java:102`); danger check ignores players.

### Reusable patterns

#### (a) Persistent linked pair of world blocks with teleport (Vanishing Cabinet, Mirror of Erised-as-block)

| File | What to reuse |
|---|---|
| `floo/FlooNetworkManager.java:20,25,97` | Overworld `SavedData` + `SavedDataType` codec map of named endpoints (`address → FlooRegistryEntry`), `get(level)` always from overworld storage — the template for a `CabinetPairRegistry` (pairId → two GlobalPos) |
| `floo/FlooRegistryEntry.java` | Record + CODEC for an endpoint (dimension id + BlockPos + flags) |
| `floo/FlooRegistrationService.java:82,104` | `canOpenAt` / `submit` — register on placement with permission + fee; unregister path via `FlooNetworkManager.unregisterByPos:182` (call from block `onRemove`) |
| `block/floo/FlooFireplaceBlockEntity.java` | BE storing its own address — mirror of the registry entry on the block side |
| `floo/FlooTravelHandler.java:244,336,453,527` | Refuse **before** the departure window if the far end is blocked; `findSafeArrival` scans 1–3 above with build-height bounds |
| `floo/FlooDeparture.java`, `movement/MovementLock.java` | Departure window + movement lock + cancel without consuming the fare |
| `floo/FlooAccess.java:130,150` | `mayReach(isPublic, isOwner, hasVisited, isAdmin)` — pure access rule, reuse for "who may use my cabinet" |
| `floo/FlooTolls.java` | Optional per-use fee with offline-keeper escrow |
| `apparition/charge/ApparitionLanding.java` `resolve`/`fits` | Best safe-spot logic: loaded check, hitbox fit, **lava refusal**, water ok, needs support below |
| `apparition/ApparitionWardRegistry.java:51,73` | `findBlockingWard` / `findFlooBlockingWard` — ward check at the destination (persisted via `load/save:88,129`) |
| `mirror/MirrorPairing.java` | Pair-by-shared-UUID component — **anti-pattern for pending state** (global static slot); key pending by attuner or pair both at once |
| `mirror/MirrorSessionManager.java` `findHolder` | Locate the other half by pair id across online players |
| `portkey/PortkeyService.java` | Group teleport (1.5-block touch radius), dimension guard — missing lava/ward checks |
| `trunk/TrunkRegistryData.java`, `trunk/ExtensionCharmService.java` | Persistent block→pocket-space registry + teleport in/out (UNVERIFIED detail) |
| `chamber/ChamberBasilisk.java` `Data` | Small per-level `SavedData` set keyed by structure-start chunk long — good for "used once" flags |

#### (b) Chamber of Secrets dungeon reward

| File | What it gives |
|---|---|
| `chamber/structure/ChamberOfSecretsStructure.java` | Jigsaw wrapper; `findGenerationPoint` returns empty when CHAMBER_OF_SECRETS is off |
| `data:worldgen/structure/chamber_of_secrets.json`, `worldgen/structure_set/…`, `worldgen/template_pool/chamber_of_secrets/start_pool.json` | Start y=-40, size 3, spacing 40; `spawn_overrides` names the basilisk |
| `data:structure/chamber_of_secrets/chamber.nbt` | **No chest / loot table / mod item** inside — the reward has to be added (chest with loot table, or a pedestal block) |
| `chamber/ChamberBasilisk.java` (`mayWake`, `slainAt`, `Data`) | One basilisk per Chamber, slain flag persisted per structure start → reuse as "reward unlocked when slain" |
| `entity/creature/BasiliskEntity.java:225` | Death hook calling `ChamberBasilisk.slainAt` — the natural place to unlock/drop the reward (diary, Sword) |
| `data:loot_table/entities/basilisk.json` | Fang drop (1) + leather |
| `basilisk/ritual/BasiliskBreedingRitual.java:49` | Alternative basilisk source, same module gate |
| `treasure/TreasureSite.java`, `TreasureLootModifier.java`, `CurseBreaking.java`, `data:treasure_sites/*.json` | Structure-tag-keyed chest injection of cursed artefacts with curse layers — the best ready pattern for a guarded Chamber chest (add a `treasure_site/chamber_of_secrets` tag + loot table) |
| `loot/AddItemLootModifier.java`, `loot/ModuleGatedLootModifier.java` | Generic add-item and module-strip loot modifiers |
| `data:loot_table/chests/hidden_wizarding_cache.json`, `chests/scholars_shelf.json` | Example mod chest loot tables |
| `data:locations/ministry.json:36`, `knockturn_dealer.json:36` | Existing consumers that want a basilisk fang (contract hooks) |
| Tests: `src/test/.../chamber/structure/ChamberOfSecretsStructureGateTest.java`, `gametest/CreatureWildlifeTests.java:656` | Gate + fang/horcrux coverage to extend |

### Not inspected (UNVERIFIED)

TrunkBlock / pocket teleport internals; CourierParcelItem; MinistryLicenseScrollItem and TelescopeItem internals; WizardingThrownKind behaviour; SneakoscopeDetector and MaraudersMap tracker internals (only entry points read); SpellWorldGuard (not present on this branch); recipe-collision winner (load-order dependent, not determined).


---

## Audit: spell acquisition + heritage eligibility (worktree C:/tmp/wb-tracing, branch feat/spell-tracing)

Read-only. Paths relative to `src/main/java/at/koopro/wizardsandbeasts/` (J) and
`src/main/resources/data/wizards_and_beasts/` (D) unless absolute. Line numbers are from the worktree at audit time.

---

### A. Spell acquisition

#### A0. Registry size

- **158 registered spells**: 152 `D/spells/*.json` (registered namespaced, `wizards_and_beasts:<id>`,
  `J/spell/def/SpellReloadListener.java:64-66`) + 6 Java (`J/spell/core/Spells.java:71-79`: protego,
  expecto_patronum, avada_kedavra, imperio, obscurus_surge, obscurus_grasp; bare ids).
- **34 implemented**, **124 `coming_soon`** (all JSON). Coming-soon spells cannot be learned at all
  (`J/spell/learning/SpellLearningEligibility.java:43`) and are skipped by the loot roller
  (`J/loot/SpellbookLootModifier.java:111`).
- Stale doc: `J/spell/learning/SpellSource.java` javadoc says "There are 128 registered spells". It's 158.

#### A1. Fields usable to derive a learning difficulty

There is **no difficulty, tier or level field** on spells. These are the fields that exist:

| Field | Where | Values / range | Gates anything? |
|---|---|---|---|
| `category` | JSON required; Java ctor | `combat` / `utility` / `defense` / `dark_arts` (`J/spell/core/SpellCategory.java`) | yes: DARK_ARTS module gate, default legal class |
| `canonTier` | JSON optional (`SpellDefinition.java:394`) | `books`/`companion`/`film`/`pottermore`/`expanded`/`original`, absent = untriaged. Distribution: books 83, film 24, expanded 22, pottermore 21, original 2, absent 6 (all Java spells) | **no** (provenance only, `SpellCanonTier.java:11-13`) |
| `implementationState` | JSON, default `implemented` | `implemented` / `coming_soon` | yes (learn + cast) |
| `relation` + `relatedSpell` | JSON, default `base` | `base` 135 / `narrow_of` 11 / `rank_of` 7 / `counter_of` 5 | **no** (presentation only, `SpellRelation.java:10-18`) |
| `requirement` | JSON `{type: none/knows/proficiency, prerequisiteId, minProficiency: novice/proficient/mastered}`; Java `buildRequirement()` | Proficiency thresholds are successful-hit counts: NOVICE 0, PROFICIENT 50, MASTERED 200 (`J/spell/core/Proficiency.java:4-6`). Can be overridden per player by skill `spellGateOverrides` and `ScholarsMethod` (`SpellRequirement.java:102-108`). | yes: learning always; casting only when `enforceSpellRequirements` is true (default **false**, `J/Config.java:14-19`) |
| `learning{}` | JSON optional (`SpellDefinition.java:261-281`) | `requiredSkillId`, `requiredProfessionId`, `masterySpellId` + `minMasteryTier` (novice/proficient/mastered) | yes. Only 2 spells use it (episkey, levicorpus, both mastery). requiredSkill/profession are unused in data. |
| Legal class (`D/spell_law/*.json`, 29 files) | `legal_class` unrestricted/restricted/dark/unforgivable (weight 0/1/2/4, `ministry/trace/LegalClass.java:21-27`); `visibility` 0-3 | Missing entries default by category (`ministry/trace/SpellLaw.java:44-54`): utility→unrestricted/1, defense→unrestricted/2, combat→restricted/2, dark_arts→dark/2. Avada/Crucio/Imperio fall back to UNFORGIVABLE (`SpellLawRegistry.java:50-53`). | yes: law learning gate |
| `cooldownTicks`, `baseDamage`, `castType`, `range`, `effects`, `gampDomains` | JSON | numeric | no (power proxies only) |
| Skill node `pointCost` (for node-taught spells) | `D/skill_nodes/**` | 1-6 | gates the node, not the spell |

The most usable inputs for a derived difficulty are legal class (0/1/2/4), requirement depth (prereq chain plus
proficiency 0/50/200), category and canonTier. For node-taught spells, `pointCost` also works.

#### A2. Learning routes (source of truth)

1. **Spell source item** (`J/item/spell/SpellSourceItem.java:76-139`), via `SpellLearningService.tryLearnSpell`, which runs all of `SpellLearningEligibility`.
   - Items: `standard_book_of_spells` (reusable) and `torn_spell_page` (spent on read).
   - Loot pools come from `SpellbookLootModifier` (`D/loot_modifiers/`, registered in `data/neoforge/loot_modifiers/global_loot_modifiers.json`). The roll picks a random spell from the pool:
     - `spellbook_in_village_libraries` (chance 0.35): lumos, nox, stupefy, alohomora, wingardium_leviosa, reparo, colloportus, accio, depulso
     - `spellbook_in_stronghold_libraries` (0.7): stupefy, expelliarmus, incendio, glacius, arresto_momentum, episkey, finite_incantatem, flipendo, diffindo, aguamenti, confringo
     - `spellbook_in_woodland_mansions` (mansion + pillager outpost, 0.6): riddikulus, levicorpus, liberacorpus, bombarda, frigora, claustra_reverto, capacious_extremis, revelio
     - `spell_pages_in_ruins` (torn page, 0.4; mineshaft/dungeon/pyramid/...): episkey, arresto_momentum, finite_incantatem, colloportus, alohomora, aguamenti, glacius, diffindo, levicorpus, liberacorpus, riddikulus
     - `D/loot_table/chests/hidden_wizarding_cache.json` pool 3 (always one book): lumos 40%, stupefy 30%, **blank** 30% (count 1-3). The cache is injected at 12% into mansion/jungle temple/desert pyramid/ancient city/stronghold library (`D/loot_modifiers/hidden_cache_in_ruins.json`).
   - **Market**: no location (`D/locations/*.json`) sells a spell source. **No recipe** crafts a blank Standard Book of Spells, so the only survival source of blank books is the hidden cache above (about 3.6% per eligible chest).
   - **Spell Scriptorium** (`J/spell/study/SpellScriptoriumBlock.java:134-183`, block id `spell_teacher`, recipe `D/recipe/spell_teacher.json`, FURNISHINGS module). It writes the player's active, known spell into a blank source for 1 ink bottle. It does not check eligibility, implementation or Obscurial status (the reader is checked later). Any spell some player knows can be propagated this way, so it is a secondary route for every implemented spell, bounded by the scarcity of blank books.
2. **Skill node `learn_spell`**: 30 nodes carry 31 grants (`levicorpus_unlock` grants levicorpus + liberacorpus). The node is applied by `J/skill/SkillSystemAPI.java:404-417` → `teachSpell` `:427-439`. The node unlock gates (`evaluateUnlock` `:159-218`) are: SKILL_TREES module, points, adjacency (symmetric, `SkillTrees.neighbors`), tree audience, tree requirement (CASTING seals spell_mastery/dark_arts for `no_casting`), lineage, DARK_ARTS module for the dark tree, build/NEWT/standing. **`teachSpell` does NOT call `SpellLearningEligibility`**, so implementation state, law gate, authored `requirement`, Obscurial check and `learning{}` are all skipped. The ledger is `recordWebTaughtSpell` (revoked on respec).
3. **`swap_active_spell learn:true`**: only `D/spells/lumos.json:25-28` (casting Lumos teaches nox). It writes directly (`J/spell/effect/SpellEffectComponent.java:591-594`) with no eligibility check. `nox.json:27-29` swaps back without learn.
4. **Other**: none in survival. Admin/debug writers are listed in A5. Obscurus abilities are not learned at all: they are heritage abilities (`D/abilities/obscurus_*.json`, `J/ability/trigger/behavior/MigratedAbilityBehaviors.java:60-62`, executed by `J/heritage/obscurial/ObscurialServerLogic.java:119-189`).

#### A3. Eligibility gates (book path only), in order: `J/spell/learning/SpellLearningEligibility.java`

| line | gate | message |
|---|---|---|
| 33 | Obscurial ability (`obscurus_*` prefix) → never learnable | literal "This is an Obscurial ability, not a learnable spell." |
| 36-38 | category DARK_ARTS and module DARK_ARTS off | literal |
| 43 | not implemented | literal |
| 46 | `ObscurialRules.canHeritageUseSpell` (Obscurial-only spells; in practice the same `obscurus_*` set) | literal |
| 49 | already known | literal |
| 56-59 | **Law**: `SpellLawLearningGate.refusal` (`SpellLawLearningGate.java:90-104`): DARK needs node `dark_knowledge`, UNFORGIVABLE needs `curse_control`. Fails open if the node is absent from the datapack (`:135-141`). Learning stains corruption: dark 1.0, unforgivable 4.0 (`:116-126`, called from `SpellLearningService.java:68`). | lang `spell.wizards_and_beasts.learning.refused.dark/.unforgivable` (present in `src/main/resources/assets/.../en_us.json`) |
| 60-62 | authored/admin `requirement` (`SpellTuning.requirement` override) | translatable `req.*` |
| 64-70 | `requiredSkillId` (authored or `SpellTuning.requiredSkill` admin override) | literal |
| 72-78 | `requiredProfessionId` (heritage profession) | literal |
| 81-86 | mastery tier of `masterySpellId` | literal |

**No heritage capability gate**: `canCast()` / `no_wand` / `no_casting` are not checked. A Squib, Goblin, House-Elf
or Obscurial can read a book and learn the spell (the cast is refused later by `SpellNetworkGuards.canUseWand`,
`J/spell/cast/SpellCastService.java:100`). An admin-disabled spell (`SpellAvailability.castAllowed`) is also not
checked at learn time.

#### A4. Findings

- **No route**: 124 coming_soon spells (by design) plus obscurus_surge and obscurus_grasp (by design: heritage
  abilities, refused at `SpellLearningEligibility.java:33`). **Every implemented learnable spell (32) has at least one route.**
- Route counts (implemented): loot 27, node 31 spell grants (30 nodes, 31 distinct spells), swap 1 (nox). Both loot
  and node: 26. Loot only: **revelio**. Node only: **protego, expecto_patronum, imperio, crucio, avada_kedavra**
  (book copies of these exist only through the Scriptorium). Scriptorium: all 32, conditional on a blank book.
- **BUG: dead book route for episkey and levicorpus.** `learning.masterySpellId` is authored bare (`"reparo"`,
  `"wingardium_leviosa"`; `D/spells/episkey.json:36`, `D/spells/levicorpus.json:20`). `SpellLearningEligibility.java:81-86`
  passes it raw to `PlayerSpellData.hasReachedMasteryTier` (`J/spell/data/PlayerSpellData.java:188-193`) → `getSuccessfulHits` (`:173-175`)
  with no canonicalisation. Hits are keyed by `spell.getId()`, which is namespaced for JSON spells
  (`SpellExecutor.java:181`, `SpellReloadListener.java:64-66`). The lookup always returns 0, so the player is NOVICE and the book is refused forever.
  `SpellRequirement` canonicalises for exactly this reason (`J/spell/core/SpellRequirement.java:95-97`), but the mastery path
  does not. The unit test uses Java Protego (bare id), so it misses this (`src/test/.../SpellLearningServiceTest.java:136-150`). Both spells are
  in the stronghold/mansion/torn-page pools, so a player finds a book they can never read. Both still have a working node route
  (`herbology/verdant_heart`, `spell_mastery/levicorpus_unlock`).
- **Law gate bypassed by the skill web.** `SpellLawLearningGate.java:38-40` says the web puts every Unforgivable behind
  `curse_control`. It does not: edges are symmetric and `quiet_casting` links to `imperio_unlock`
  (`D/skill_nodes/dark_arts/quiet_casting.json:25-28`), so this path avoids `curse_control`:
  wizard_core → dark_knowledge → legilimency_study → occlumency → quiet_casting → imperio_unlock → avada_kedavra_unlock (→ crucio_unlock).
  Imperio and Avada Kedavra are learnable without curse_control, and `teachSpell` never consults the law gate.
- **Authored prerequisites of node-only spells are dead for learning.** Imperio needs stupefy@mastered, crucio needs incendio@mastered,
  AK needs imperio+crucio@proficient, expecto needs protego@proficient. Their only survival route is the node, which ignores them, and at cast
  they only apply when `enforceSpellRequirements=true` (default false). They do apply to someone reading a Scriptorium-written copy.
- **Doc/code mismatch**: `SpellLearningService.java:26-27` says "every gate in SpellLearningEligibility applies to every caller", but the
  node (`SkillSystemAPI.java:427-439`) and swap (`SpellEffectComponent.java:591-594`) writers bypass it. `SpellImplementationState.java:20-21`
  still talks about a "teacher's offer row ... payment" that no longer exists.
- **Obscurus abilities, conditional**: `ObscurialServerLogic.java:159-163` enforces surge's `crucio@proficient` and grasp's
  `imperio@proficient` when `enforceSpellRequirements=true`. An Obscurial carries `no_casting`/`no_wand`
  (`J/heritage/ConditionOrigin.java:29-31`) and can never build 50 wand hits on Crucio/Imperio, so with that config on, both abilities are
  unreachable. With the default (false) they work.
- Refusal reasons in `SpellLearningEligibility` are hard-coded English (`Result.deny("...")`), except the law and requirement
  messages. They reach the player through `Component.literal` (`SpellSourceItem.java:90,136`).

#### A5. Every `PlayerSpellData.learnSpell` writer

| Site | Kind | Gated by eligibility? |
|---|---|---|
| `J/spell/learning/SpellLearningService.java:64` | gameplay (book/page read) | yes |
| `J/skill/SkillSystemAPI.java:437` | gameplay (skill node `learn_spell`) | **no** (node gates only) |
| `J/spell/effect/SpellEffectComponent.java:593` | gameplay (`swap_active_spell learn:true`, lumos→nox) | **no** |
| `J/admin/player/PlayerAdminService.java:525` | admin `SPELL_UNLOCK` | yes (`validateLearnAttempt` at :521) |
| `J/admin/player/PlayerAdminService.java:535` | admin `SPELL_GRANT` ("past the learning rules") | no, intended |
| `J/spell/command/SpellCommands.java:155` | admin `/wandb spell learn` (`.requires(ADMIN)` :71) | no, intended |
| `J/spell/command/SpellCommands.java:403` | admin learn-all (`.requires(ADMIN)` :97) | no, intended |
| `J/spell/debug/SpellDevKit.java:49` | debug dev kit | no |
| `J/client/spell/state/ClientSpellDataState.java:100` | client mirror of S2C sync | n/a |
| `PlayerSpellData.java:332` (`load`) | persistence/migration | n/a |
| gametest: `AdminMagicTests:316`, `BuildTests:365`, `ProgressionTests:130`, `ProtegoWardBeamTests:262`, `SpellLearningTests:121`, `WandAllegianceTests:603`, `WandCastRaceTests:600`, `WizardTestSupport:305,309` | test | n/a |

#### A6. Full matrix (implemented spells, then coming_soon)

Legend: legal class with `*` = derived default (no `spell_law` file). Loot tags: village/stronghold/mansion(outpost)/
torn_page:ruins/hidden_cache with modifier chance. Node "(n pt)" = `pointCost`. The Scriptorium (any known spell) is not
repeated per row.

| id | src | category | canonTier | implState | relation | requirement (prereq) | learning{} | legal class | (1) loot / book pools | (2) skill node learn_spell | (3) swap | route |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| accio |json | utility | books | implemented | base | knows wingardium_leviosa | - | unrestricted | village(0.35) | accio_unlock(2 pt) | - | loot+node |
| aguamenti |json | utility | books | implemented | base | knows lumos | - | unrestricted | stronghold(0.7); torn_page:ruins(0.4) | aguamenti_unlock(2 pt) | - | loot+node |
| alohomora |json | utility | books | implemented | base | - | - | unrestricted | village(0.35); torn_page:ruins(0.4) | alohomora_unlock(2 pt) | - | loot+node |
| arresto_momentum |json | utility | film | implemented | base | knows wingardium_leviosa | - | unrestricted* | stronghold(0.7); torn_page:ruins(0.4) | arresto_momentum_unlock(3 pt) | - | loot+node |
| avada_kedavra |java | dark_arts | - | implemented | base | imperio@proficient + crucio@proficient | - | unforgivable | - | avada_kedavra_unlock(6 pt) | - | node |
| bombarda |json | combat | film | implemented | base | proficiency diffindo@proficient | - | restricted | mansion/outpost(0.6) | bombarda_unlock(3 pt) | - | loot+node |
| capacious_extremis |json | utility | pottermore | implemented | base | - | - | unrestricted* | mansion/outpost(0.6) | transmuters_insight(6 pt) | - | loot+node |
| claustra_reverto |json | utility | original | implemented | base | knows capacious_extremis | - | unrestricted* | mansion/outpost(0.6) | claustra_reverto_unlock(2 pt) | - | loot+node |
| colloportus |json | utility | books | implemented | base | knows alohomora | - | unrestricted* | village(0.35); torn_page:ruins(0.4) | colloportus_unlock(2 pt) | - | loot+node |
| confringo |json | combat | books | implemented | base | proficiency incendio@proficient | - | restricted | stronghold(0.7) | confringo_unlock(3 pt) | - | loot+node |
| crucio |json | dark_arts | books | implemented | base | proficiency incendio@mastered | - | unforgivable | - | crucio_unlock(4 pt) | - | node |
| depulso |json | combat | expanded | implemented | base | proficiency accio@proficient | - | restricted* | village(0.35) | depulso_unlock(2 pt) | - | loot+node |
| diffindo |json | combat | books | implemented | base | proficiency stupefy@proficient | - | restricted* | stronghold(0.7); torn_page:ruins(0.4) | diffindo_unlock(2 pt) | - | loot+node |
| episkey |json | defense | books | implemented | base | knows reparo | {"masterySpellId": "reparo", "minMasteryTier": "proficient"} | unrestricted* | stronghold(0.7); torn_page:ruins(0.4) | verdant_heart(6 pt) | - | loot+node |
| expecto_patronum |java | defense | - | implemented | base | protego@proficient | - | unrestricted* | - | expecto_patronum_unlock(5 pt) | - | node |
| expelliarmus |json | combat | books | implemented | base | knows stupefy | - | restricted | stronghold(0.7) | expelliarmus_unlock(2 pt) | - | loot+node |
| finite_incantatem |json | utility | books | implemented | base | - | - | unrestricted* | stronghold(0.7); torn_page:ruins(0.4) | counter_curse(3 pt) | - | loot+node |
| flipendo |json | combat | expanded | implemented | base | - | - | restricted* | stronghold(0.7) | flipendo_unlock(2 pt) | - | loot+node |
| frigora |json | utility | original | implemented | base | knows lumos | - | unrestricted* | mansion/outpost(0.6) | frigora_unlock(2 pt) | - | loot+node |
| glacius |json | combat | expanded | implemented | base | proficiency flipendo@proficient | - | restricted* | stronghold(0.7); torn_page:ruins(0.4) | glacius_unlock(3 pt) | - | loot+node |
| imperio |java | dark_arts | - | implemented | base | stupefy@mastered | - | unforgivable | - | imperio_unlock(4 pt) | - | node |
| incendio |json | combat | books | implemented | base | proficiency stupefy@proficient | - | restricted | stronghold(0.7) | incendio_unlock(3 pt) | - | loot+node |
| levicorpus |json | utility | books | implemented | base | knows wingardium_leviosa | {"masterySpellId": "wingardium_leviosa", "minMasteryTier": "proficient"} | unrestricted* | mansion/outpost(0.6); torn_page:ruins(0.4) | levicorpus_unlock(3 pt) | - | loot+node |
| liberacorpus |json | utility | books | implemented | base | knows wingardium_leviosa | - | unrestricted* | mansion/outpost(0.6); torn_page:ruins(0.4) | levicorpus_unlock(3 pt) | - | loot+node |
| lumos |json | utility | books | implemented | base | - | - | unrestricted | village(0.35); hidden_cache(w4) | lumos_unlock(1 pt) | - | loot+node |
| nox |json | utility | books | implemented | base | knows lumos | - | unrestricted | village(0.35) | nox_unlock(1 pt) | lumos swap learn:true | loot+node+swap |
| obscurus_grasp |java | dark_arts | - | implemented | base | imperio@proficient | - | dark* | - | - | - | NONE (heritage ability) |
| obscurus_surge |java | dark_arts | - | implemented | base | crucio@proficient | - | dark* | - | - | - | NONE (heritage ability) |
| protego |java | defense | - | implemented | base | knows expelliarmus | - | unrestricted* | - | protego_unlock(2 pt) | - | node |
| reparo |json | utility | books | implemented | base | knows lumos | - | unrestricted | village(0.35) | reparo_unlock(2 pt) | - | loot+node |
| revelio |json | utility | expanded | implemented | base | - | - | unrestricted* | mansion/outpost(0.6) | - | - | loot |
| riddikulus |json | defense | books | implemented | base | - | - | unrestricted* | mansion/outpost(0.6); torn_page:ruins(0.4) | beast_speaker(6 pt) | - | loot+node |
| stupefy |json | combat | books | implemented | base | - | - | restricted | village(0.35); stronghold(0.7); hidden_cache(w3) | stupefy_unlock(2 pt) | - | loot+node |
| wingardium_leviosa |json | utility | books | implemented | base | knows lumos | - | unrestricted | village(0.35) | wingardium_unlock(2 pt) | - | loot+node |

#### coming_soon spells (124) - refused by SpellLearningEligibility.java:43; no loot pool, node or swap names any of them

| id | category | canonTier | relation | requirement | legal class |
|---|---|---|---|---|---|
| aberto | utility | film | rank_of→alohomora | proficiency alohomora@proficient | unrestricted* |
| alarte_ascendare | combat | film | base | - | restricted* |
| anapneo | utility | books | base | - | unrestricted* |
| anteoculatia | combat | pottermore | base | - | restricted* |
| aparecium | utility | books | narrow_of→revelio | - | unrestricted* |
| appare_vestigium | utility | film | rank_of→revelio | proficiency revelio@proficient | unrestricted* |
| ascendio | utility | film | base | - | unrestricted* |
| avenseguim | utility | film | base | - | unrestricted* |
| avis | utility | books | base | - | unrestricted* |
| baubillious | combat | expanded | base | - | restricted* |
| bombarda_maxima | combat | film | rank_of→bombarda | proficiency bombarda@proficient | restricted |
| brachiabindo | combat | film | base | - | restricted* |
| brackium_emendo | utility | film | narrow_of→reparo | - | unrestricted* |
| calvorio | combat | pottermore | base | - | restricted* |
| cantis | combat | pottermore | base | - | restricted* |
| carpe_retractum | utility | expanded | base | - | unrestricted* |
| cave_inimicum | defense | books | base | - | unrestricted* |
| colloshoo | combat | pottermore | base | - | restricted* |
| colovaria | utility | pottermore | base | - | unrestricted* |
| confundo | combat | books | base | - | restricted |
| defodio | utility | books | base | - | unrestricted* |
| deletrius | utility | books | counter_of→prior_incantato | - | unrestricted* |
| densaugeo | combat | books | base | - | restricted* |
| deprimo | combat | books | base | - | restricted* |
| descendo | utility | books | base | - | unrestricted* |
| diminuendo | utility | expanded | rank_of→reducio | proficiency reducio@proficient | unrestricted* |
| dissendium | utility | books | base | - | unrestricted* |
| ducklifors | combat | expanded | base | - | restricted* |
| duro | utility | books | base | - | unrestricted* |
| ebublio | combat | expanded | base | - | restricted* |
| engorgio | utility | books | base | - | unrestricted* |
| engorgio_skullus | combat | pottermore | narrow_of→engorgio | - | restricted* |
| entomorphis | combat | pottermore | base | - | restricted* |
| epoximise | utility | pottermore | base | - | unrestricted* |
| erecto | utility | books | base | - | unrestricted* |
| evanesco | utility | books | base | - | unrestricted* |
| everte_statum | combat | film | base | - | restricted* |
| expulso | combat | books | base | - | restricted |
| ferula | utility | books | base | - | unrestricted* |
| fianto_duri | defense | film | base | - | unrestricted* |
| finestra | utility | film | base | - | unrestricted* |
| flagrate | utility | books | base | - | unrestricted* |
| flintifors | combat | pottermore | base | - | restricted* |
| fumos | utility | expanded | base | - | unrestricted* |
| furnunculus | combat | books | base | - | restricted* |
| geminio | utility | books | base | - | unrestricted* |
| glisseo | utility | books | base | - | unrestricted* |
| herbifors | combat | pottermore | base | - | restricted* |
| herbivicus | utility | expanded | base | - | unrestricted* |
| homenum_revelio | utility | books | narrow_of→revelio | - | unrestricted |
| immobulus | combat | film | base | - | restricted* |
| impedimenta | combat | books | base | - | restricted* |
| impervius | utility | books | base | - | unrestricted* |
| incarcerous | combat | books | base | - | restricted* |
| incarcifors | combat | pottermore | base | - | restricted* |
| langlock | combat | books | base | - | restricted* |
| lapifors | combat | expanded | base | - | restricted* |
| legilimens | utility | books | base | - | restricted |
| levioso | utility | expanded | narrow_of→wingardium_leviosa | - | unrestricted* |
| locomotor | utility | books | base | - | unrestricted* |
| locomotor_mortis | combat | books | base | - | restricted* |
| locomotor_wibbly | combat | pottermore | base | - | restricted* |
| lumos_maxima | utility | film | rank_of→lumos | proficiency lumos@proficient | unrestricted |
| melofors | combat | pottermore | base | - | restricted* |
| meteolojinx_recanto | utility | books | base | - | unrestricted* |
| mimblewimble | combat | books | base | - | restricted* |
| mobiliarbus | utility | books | narrow_of→locomotor | - | unrestricted* |
| mobilicorpus | utility | books | narrow_of→locomotor | - | unrestricted* |
| molliare | utility | pottermore | base | - | unrestricted* |
| morsmordre | dark_arts | books | base | - | dark |
| mucus_ad_nauseam | combat | pottermore | base | - | restricted* |
| muffliato | utility | books | base | - | unrestricted |
| mutatio_skullus | combat | pottermore | base | - | restricted* |
| nebulus | utility | expanded | base | - | unrestricted* |
| obliviate | utility | books | base | - | restricted |
| obscuro | combat | books | base | - | restricted* |
| oculus_reparo | utility | film | narrow_of→reparo | - | unrestricted* |
| oppugno | combat | books | base | - | restricted* |
| orchideous | utility | books | base | - | unrestricted* |
| pack | utility | books | base | - | unrestricted* |
| papyrus_reparo | utility | film | narrow_of→reparo | - | unrestricted* |
| partis_temporus | utility | film | base | - | unrestricted* |
| periculum | utility | expanded | base | - | unrestricted* |
| peskipiksi_pesternomi | utility | books | base | - | unrestricted* |
| petrificus_totalus | combat | books | base | - | restricted |
| piertotum_locomotor | combat | books | rank_of→locomotor | proficiency locomotor@proficient | restricted* |
| point_me | utility | books | base | - | unrestricted |
| portus | utility | books | base | - | unrestricted* |
| prior_incantato | utility | books | base | - | unrestricted* |
| protego_diabolica | dark_arts | film | base | - | dark* |
| pullus | combat | expanded | base | - | restricted* |
| quietus | utility | books | counter_of→sonorus | - | unrestricted* |
| redactum_skullus | combat | pottermore | narrow_of→reducio | - | restricted* |
| reducio | utility | books | counter_of→engorgio | - | unrestricted* |
| reducto | combat | books | base | - | restricted |
| relashio | utility | books | base | - | unrestricted* |
| rennervate | utility | books | counter_of→stupefy | - | unrestricted* |
| reparifarge | utility | expanded | base | - | unrestricted* |
| reparifors | utility | expanded | base | - | unrestricted* |
| repello_inimicum | defense | film | base | - | unrestricted* |
| repello_muggletum | utility | books | base | - | unrestricted* |
| reverte | utility | film | base | - | unrestricted* |
| rictusempra | combat | books | base | - | restricted* |
| salvio_hexia | defense | books | base | - | unrestricted* |
| scourgify | utility | books | base | - | unrestricted |
| sectumsempra | dark_arts | books | base | - | dark |
| serpensortia | combat | books | base | - | restricted* |
| silencio | combat | books | base | - | restricted* |
| slugulus_eructo | combat | pottermore | base | - | restricted* |
| sonorus | utility | books | base | - | unrestricted* |
| specialis_revelio | utility | books | narrow_of→revelio | - | unrestricted* |
| spongify | utility | expanded | base | - | unrestricted* |
| steleus | combat | pottermore | base | - | restricted* |
| surgito | utility | film | base | - | unrestricted* |
| tarantallegra | combat | books | base | - | restricted* |
| tentaclifors | combat | pottermore | base | - | restricted* |
| tergeo | utility | books | base | - | unrestricted* |
| titillando | combat | expanded | base | - | restricted* |
| ventus | combat | film | base | - | restricted* |
| verdimillious | utility | expanded | base | - | unrestricted* |
| vermillious | utility | expanded | base | - | unrestricted* |
| vipera_evanesca | utility | film | counter_of→serpensortia | - | unrestricted* |
| vulnera_sanentur | utility | books | rank_of→episkey | proficiency episkey@proficient | unrestricted* |
| waddiwasi | combat | books | base | - | restricted* |

---

### B. Heritage and conditions

#### B1. Inventory

**Heritages** (`J/heritage/Heritage.java:23-64`). Columns: canUseWand / magicSource / alphaAvailable (the default selectability).

| Heritage | wand | source | selectable by default | Variants (`HeritageVariant.java`, tags) |
|---|---|---|---|---|
| WIZARDKIND | yes | WAND | **yes** | pure_blood, half_blood, muggle_born, adopted_magical (wandwork, learned_arts); squib (no_wand, no_casting, creature_kinship) |
| GOBLIN | no | INNATE | no | common, warrior, rune |
| HOUSE_ELF | no | INNATE | no | bound, free, hall (innate_apparition, household_magic, bound_service except free) |
| VEELA | yes | HYBRID | no | full (transformation, no_wand, allure), half (transformation, allure), quarter (allure) |
| GIANT | yes | HYBRID | no | full_giant (no_wand), half_giant, clan_warden (no_wand); all spell_resistant_hide |
| CENTAUR | no | INNATE | no | forest, war, stargazer |
| VAMPIRE | yes | HYBRID | no | turned (sunlight_weakness, blood_hunger, transformation), born_vampire (sunlight_weakness, blood_hunger), dhampir (blood_hunger) |
| MERPEOPLE | no | INNATE | no | merrow, selkie, siren (water_dwelling, mermish) |

**Conditions** (`J/heritage/MagicalCondition.java:24-26`, origins in `ConditionOrigin.java:21-31`). There is a **single slot**:
`PlayerHeritageData.condition` with `setCondition` (`J/heritage/data/PlayerHeritageData.java:66-68`).

| Condition | Origins | Traits | Eligibility (`MagicalCondition.canBeCarriedBy` :58-63) |
|---|---|---|---|
| LYCANTHROPY | bitten, born (cradle), savage_bite | moon_sensitive, transformation | Wizardkind only (Squib allowed) |
| OBSCURUS | suppressed, unleashed | obscurus_form, no_casting, no_wand (+transformation if unleashed) | Wizardkind only, not `no_casting` variants (so not Squib) |

Data: `D/heritage_appearance/` has 10 files (presentation only). Vampire blood, thirst and nutrition: `J/heritage/vampire/*`,
`J/heritage/nutrition/NutritionPolicyResolver.java:35-45` (keyed on the `blood_hunger` tag).

#### B2. Acquisition and assignment paths (every writer of heritage or condition)

All writes go through `HeritageAPI.commit` (`J/heritage/HeritageAPI.java:101`), `shiftLineage` (:152), `afflict` (:171),
`cure` (:188) and `clear` (:216). Callers:

| Caller | What | Checks |
|---|---|---|
| `J/network/heritage/HeritageSelectC2SPayload.java:79-133` → `HeritageAssignment.select` | onboarding gate: player picks heritage + variant + optional condition | lock (:79), `HeritageRules.refusalKey` (:93, lang `message.wizards_and_beasts.type_selection.closed/coming_soon`), variant pairing (:100), `canBeCarriedBy` (:122, lang `message.wizards_and_beasts.heritage.condition_not_possible`, en_us.json:3275) |
| `J/heritage/command/HeritageCommands.java:402` and `J/admin/heritage/HeritageAdminService.java:184` → `HeritageAssignment.assign` | admin set heritage | pairing only. **Ignores selectability** (by design; `HeritageAdminService` returns `assigned_closed`) and **ignores an existing condition** |
| `HeritageCommands.java:334-363` | admin set condition | `hasHeritageSelected`, `canBeCarriedBy` (:357, literal English failure). **No check for an existing different condition**, which is silently overwritten |
| `HeritageCommands.java:378` | admin cure | — |
| `J/creature/wildlife/LycanthropyInfection.java:47-61` | **only in-world condition acquisition**: werewolf bite (creature ability `J/creature/ability/LycanthropicBite.java:27`, or a transformed werewolf player :66-76) | HERITAGE module, full moon, damage > 0, victim is Wizardkind, victim not already a werewolf (`J/creature/wildlife/WildlifeRules.java:252-256`) |
| `J/heritage/elf/HouseElfBond.java:215` | shiftLineage bound → free elf | same people (`HeritageAPI.java:153-156`) |
| `J/heritage/debug/HeritageDevKit.java:60,79`, `J/client/debug/*ShowcaseCapture.java` | debug | — |

#### B3. Verifications

1. **"Vampire acquisition limited to Wizardkind and Veela": DOES NOT EXIST.** No code turns anyone into a vampire. Vampire is a
   *heritage* (`Heritage.java:56-59`, `alphaAvailable=false`). The only ways in are (a) the onboarding gate, if an admin opens it through
   `HeritageRules` (`J/heritage/rules/HeritageRules.java:60-62`), which accepts any player who has no heritage locked yet, whatever they "were";
   or (b) admin `HeritageAssignment.assign`, which applies to **any** current heritage (Goblin, Centaur, Merpeople ...) with no
   Wizardkind/Veela restriction (`HeritageCommands.java:385-402`, `HeritageAdminService.java:165-184`). The vampire package (`J/heritage/vampire/`)
   has feeding (`VampireBloodEvents.java:57+`) but never calls `commit`/`afflict`, so there is no bite-to-turn path. Docs agree it is admin-only:
   `documentation/HERITAGE_IDENTITY_STATUS.md:18-19`.
2. **"Incompatible condition events rejected with clear feedback": PARTIAL.**
   - Onboarding: yes. `HeritageSelectC2SPayload.java:122-128` uses lang key `message.wizards_and_beasts.heritage.condition_not_possible`.
   - Admin condition command: rejects ineligible heritage, but with a literal English string (`HeritageCommands.java:357-361`), not a lang key.
   - **VIOLATION (in-world)**: `LycanthropyInfection.bitten` (`LycanthropyInfection.java:52-57`) only checks Wizardkind and not-already-werewolf. A
     Wizardkind **Obscurial** bitten at full moon has `OBSCURUS` **silently replaced** by `BITTEN` (single slot, `PlayerHeritageData.java:66-68`;
     `HeritageAPI.afflict` :171-180). No rejection; the only message is the generic "you carry the curse" (`heritage.wizards_and_beasts.werewolf.bitten`).
     `WildlifeRules.infects` has no parameter for an existing condition.
   - **VIOLATION (admin)**: `HeritageCommands.setCondition` overwrites an existing different condition without warning (:357-363).
     `HeritageAssignment.assign` (`J/heritage/HeritageAssignment.java:62-66`) reassigns heritage and keeps a condition that `canBeCarriedBy` now
     forbids (e.g. Wizardkind+Obscurus → Vampire keeps Obscurus).
3. **"Obscurus/Obscurial mastery hybrid model (safe control path vs risky high-output path)": NOT IMPLEMENTED as a mastery/progression model.**
   What exists is a stance toggle with resources, but no progression:
   - Resources: drain ("control"), charge ("pressure"), stress (`J/heritage/obscurial/ObscurialResourceManager.java:13-24`). Tiers: Stability/LoreControl
     STABLE>60 / UNSTABLE / CRITICAL≤25 and stress CALM/AGITATED≥40/VOLATILE≥70 (`ObscurialTierRules.java:40-73`).
   - Safe stance (human form): damage ×0.95, cooldown ×1.08, regenerates control (`ObscurialCombatRules.java:17-26`, `ObscurialRules.java:142-173`).
   - Risky stance (dark form): damage ×1.20 (+0.10 below 50% HP, +0.22 rage at CATASTROPHIC control), cooldown ×0.90, abilities −10% cooldown. Costs are
     drain/charge per tick, daylight damage, fizzle up to 90% with 3.0 backlash, surge/grasp control costs (`ObscurialCombatRules.java:17-56,60-80,156-165`;
     `ObscurialRules.java:264-293`). Entering needs control > 5 (`ObscurialServerLogic.java:79`). Forced dark form triggers on low HP or VOLATILE+CATASTROPHIC
     (`J/event/heritage/ObscurialHeritageHandler.java:227-239`). Stress vent is at `ObscurialServerLogic.java:95+`.
   - Missing: any persistent mastery/skill value, any choice of path, any skill nodes, profession or practice for the Obscurus. `PracticeKind`
     (`J/heritage/identity/PracticeKind.java`) covers elf/goblin/centaur only. A grep for mastery/progress in `heritage/obscurial` finds nothing. Obscurials
     can't use the spell_mastery or dark_arts webs (tree requirement CASTING vs `no_casting`, `SkillSystemAPI.java:177-179`).
4. **Items/potions that could bypass heritage eligibility**:
   - No item, potion or artifact writes heritage or condition. All writers are listed in B2; brew/polyjuice/disguise/animagus code does not call
     `HeritageAPI.commit/afflict` (grep). Polyjuice/disguise changes appearance only (UNVERIFIED in depth).
   - **Spell books bypass heritage casting capability**: `SpellLearningEligibility` never checks `canCast()`/`no_wand`, so Squib, Goblin, House-Elf,
     Centaur, Merpeople, full Veela/Giant and Obscurial characters can learn spells from books and Scriptorium copies. Skill nodes, by contrast, are sealed by the
     CASTING tree requirement. The cast is still refused later (`SpellCastService.java:100`).
   - The form-change packet (`J/network/form/FormChangeRequestC2SPayload.java:66-84`) can put anyone into any heritage form, including `obscurial_dark`, but it is admin-gated.

#### B4. Heritage rule violations (summary)

| # | Severity | Evidence |
|---|---|---|
| H1 | high (in-world) | Werewolf bite silently overwrites Obscurus: `J/creature/wildlife/LycanthropyInfection.java:52-57`, `WildlifeRules.java:252-256`, `PlayerHeritageData.java:66-68` |
| H2 | spec gap | No vampire acquisition at all, so no Wizardkind/Veela restriction: `Heritage.java:56-59`, admin `HeritageAssignment.java:62-66` accepts any source heritage |
| H3 | medium (admin) | Condition overwrite without warning: `HeritageCommands.java:357-363`. Literal non-lang message: `:358-360` |
| H4 | medium (admin) | Heritage reassign keeps an ineligible condition: `HeritageAssignment.java:62-66`, `HeritageAdminService.java:184` |
| H5 | spec gap | No Obscurial mastery/hybrid progression: `J/heritage/obscurial/*` (stance toggle only) |
| H6 | low | Book learning ignores `no_casting`/`no_wand`: `SpellLearningEligibility.java:30-89` (no `canCast`) |
| H7 | conditional | With `enforceSpellRequirements=true`, Obscurial surge/grasp need Crucio/Imperio proficiency an Obscurial cannot earn: `ObscurialServerLogic.java:159-163`, `ConditionOrigin.java:29-31` |

UNVERIFIED: polyjuice/disguise internals; client `HeritageSelectionScreen` behaviour beyond the `canBeCarriedBy` calls at :265/:281; skill-node
graph reachability per audience (BFS over symmetric edges found all 90 nodes reachable from the 3 roots).


---

## Audit — Wandmaking + Brewing (read-only)

Tree: `C:/tmp/wb-tracing` (branch `feat/spell-tracing`, HEAD `d6925446`). Nothing edited, no gradle run.
Paths below are relative to `src/main/java/at/koopro/wizardsandbeasts/` (Java, "J:") or
`src/main/resources/data/wizards_and_beasts/` (data, "D:") unless absolute-ish. `G:` = `src/generated/resources/data/wizards_and_beasts/`.
"UNVERIFIED" = not inspected / runtime-only claim.

---

### Severity summary

| # | Sev | Finding | Evidence |
|---|---|---|---|
| W1 | P1 | 3 wand cores unobtainable in survival: `thunderbird_tail_feather`, `troll_whisker`, `wampus_cat_hair`. Their only sources are entity loot/harvest of creatures with **no natural spawn** (no `spawn_thunderbird/troll/wampus_cat` biome modifier; not in BeastSpawnHandler), and Ollivanders/Apothecary only **buy** them. 30 of 80 wand recipes dead. | D:neoforge/biome_modifier/ (list has no thunderbird/troll/wampus_cat); D:locations/ollivanders.json:157-172 (`buys`), :175-179 (`sells` = wand_blank, holly_log, unicorn_hair, dragon_heartstring only); D:locations/apothecary.json:191 (troll_whisker under `buys`); D:bestiary/harvest/thunderbird_tail_feather.json (needs a live thunderbird) |
| W2 | P1 | `rougarou_hair` and `white_river_monster_spine` have core definitions but are plain `Item`s (not `WandCoreMaterialItem`) and have **zero** wandmaking recipes → bench slot refuses them, commission refuses them; loot drops are dead-end items. Cache comment claims "consumed by ten wand recipes each" — stale. | J:registry/ConsumableItemRegistry.java:136-139; J:registry/WandItemRegistry.java:31-62 (8 cores only); D:recipe/wandmaking_* = 80 files = 10 woods × 8 cores; D:loot_table/chests/hidden_wizarding_cache.json:123; J:wand/gui/WandmakersBenchMenu.java:315-318 (CoreSlot.mayPlace) |
| W3 | P1 | Wood/heritage **Compatibility** score is computed every cast and stored in `CastContext.compatibility` but nothing reads it. Also legacy `WandWood` enum has 4 values, so 6/10 woods always score neutral 0.5 anyway. | J:wand/cast/WandCastingAllegianceSystem.java:53; J:spell/cast/CastContext.java:29 (only field/copies; grep `compatibility()` = 0 hits); J:wand/stat/WandWood.java:13-16 |
| W4 | P2 | `WAND_CAST_COUNT` component has **no writer**; Wand tab and admin facets always show 0. | J:wand/WandComponents.java:76 (only definition); readers J:client/gui/character/tab/WandTab.java:239, J:admin/player/PlayerFacets.java:131 |
| W5 | P2 | Bench length re-rolls on every slot click / flexibility change → free reroll to LONG (≥14in: +10% range, +5% dmg). | J:wand/gui/WandmakersBenchMenu.java:160-165, 217; J:wand/cast/WandStatsResolver.java:181,240-243 |
| W6 | P2 | Bench tier = unbounded sum of enhancer blocks in an 11³ cube; max requirement 8.3 = 17 vanilla bookshelves. Tier labels ("master") are display only. | J:wand/bench/BenchMultiblockScanner.java (total += value, no cap); D:wizards_and_beasts/bench_enhancers/bookshelf.json (0.5); recipe max 8.3 (D:recipe/wandmaking_elder_thestral_tail_hair.json) |
| W7 | P2 | Negative fizzle values are partly decorative: `WandStats` clamps fizzle at 0 and `applyToStack` only adds when >0, so a wand's negative fizzle never offsets misfire from other sources; unicorn −0.05 is always fully wasted (no wood has positive fizzle, max flex +0.02). | J:wand/cast/WandStats.java:46; J:wand/cast/WandStatsResolver.java:83-85 |
| W8 | P2 | Wand corruption is a one-way ratchet (only writer increments on Dark casts, no cleanse) and above 0.5 it *raises* Dark Arts damage up to ×1.5. | J:wand/corruption/WandCorruptionSystem.java:34-43, 62-64 |
| W9 | P2 | Recipe `result_integrity` (0.84–1.0) multiplies all wand damage but Ollivanders WandCare resets to 1.0 for a fee → recipe quality is just a fee. | J:wand/corruption/WandCorruptionSystem.java:60-61; J:wand/ollivander/WandCare.java:144 |
| W10 | P2 | Legendary elder wood is carved from any crimson stem (Nether), and warped stems/bamboo → vine. | J:item/wand/WandBlankItem.java (`wandWoodFromLogBlock`, CRIMSON_STEMS → elder) |
| W11 | P2 | Rowan `refuse_threshold` 0.65 == global `match_threshold` 0.65 → a rowan wand never "mismatches": any non-match is a refusal that deals 4 magic damage per right-click. | D:wizards_and_beasts/wand_woods/rowan.json; D:wand_resonance_config.json; J:wand/resonance/WandResonanceSystem.java:234-238, 268-270 |
| B1 | P1 | **No in-game recipe discovery** for any brew. Potion books are `Item::new`, no handbook chapter on brewing, no unlock/known-recipe state; JEI is `compileOnly`/`runtimeOnly` dev dependency (not required). Cauldron says "make nothing you know of" though nothing is ever "known". | J:registry/CanonItemRegistry.java:62,157,165; D:handbook/chapters/ (no brewing chapter); build.gradle:223-225; lang `brew.wizards_and_beasts.cauldron.no_match` |
| B2 | P1 | Cauldron tier ladder inverted vs crafting chain: tiers are PEWTER < BRASS < COPPER, but crafting is vanilla cauldron → **brass** → copper → **pewter** (pewter consumes a copper cauldron + 4 iron blocks). Pewter is the most expensive pot and the lowest tier — a trap; crafting it downgrades you. | J:brew/CauldronTier.java (enum order, javadoc "ladder flipped"); D:recipe/brass_cauldron.json, wizarding_copper_cauldron.json, pewter_cauldron.json |
| B3 | P1 | Occamy-eggshell silver refining is unreachable by normal click: the cauldron's `useItemOn` handles **every** non-empty stack (`addIngredient` → `timedAdd` while brewing) and returns SUCCESS, so `OccamyEggshellItem.useOn` never runs unless sneaking. A normal click on a brewing Silver Solution counts as contamination (+0.25 failure) and **consumes** the shell (145+ Knuts). Tooltip tells the player to "drop it into a brewing silver solution". Only unit tests (`SilverRefiningTest`) cover it; no gametest. | J:block/brew/WizardingCauldronBlock.java:166-181, 235-238, 273-280; J:item/consumable/OccamyEggshellItem.java:50-70; J:block/brew/CauldronBlockEntity.java:601-626. Vanilla dispatch order (block before item unless secondary-use) from knowledge — runtime UNVERIFIED |
| B4 | P1 | Polyjuice has no survival sample route: the only writer of `POLYJUICE_TARGET` is the admin-gated `/wandb … polyjuice sample` command. Every survival-brewed bottle → "no sample". | J:polyjuice/command/PolyjuiceCommands.java:63; J:command/WorldCommands.java:28 (`requires ADMIN`); J:polyjuice/PolyjuiceService.java:71-75; J:registry/ModDataComponents.java:134 ("until the cauldron accepts a hair item") |
| B5 | P2 | Brew potency uses the **drinker's** `potion_potency` skill, not the brewer's; bought/sold brews scale to whoever drinks. Failure uses the brewer's skill (consistent with intent there). | J:item/brew/BrewItem.java:120-122; J:block/brew/CauldronBlockEntity.java:561-564 |
| B6 | P2 | Veritaserum, Amortentia, Draught of Living Death only apply to the **drinker** (BrewItem is drink-only, no splash/give route) — self-dosing is purely a debuff; SignatureBrewHandler javadoc assumes a victim. | J:brew/effect/BrewEffect.java:361-385; J:event/brew/SignatureBrewHandler.java:57-70; grep: VeritaserumService.dose only from BrewEffect:381 |
| B7 | P2 | Contamination: any player can drop items into anybody's brewing pot (+0.25 failure each); no brewer check. Documented as intentional "survivable", but 4 clicks ruin a batch. | J:block/brew/WizardingCauldronBlock.java:235-238; J:block/brew/CauldronBlockEntity.java:623 |
| B8 | P2 | Ingredient-obtainability unit test is a string search over loot/recipe JSON — passes for items whose source creature never spawns (same blind spot as W1). | src/test/java/at/koopro/wizardsandbeasts/brew/ShippedBrewDataTest.java:110-130 |
| B9 | P3 | Bezoar removes **all** effects (incl. Felix, Wolfsbane). | J:item/consumable/BezoarItem.java (`player.removeAllEffects()`) |

---

### A) Wandmaking

#### A.1 Pipeline

1. Wand blank: crafting `stick, stick, iron_nugget` (D:recipe/wand_blank.json); also sold by Ollivanders (D:locations/ollivanders.json:176) and in hidden cache.
2. Carve: use blank on a log 50 ticks → `WAND_WOOD` set from block (J:item/wand/WandBlankItem.java). Mod logs map to their wood; vanilla: oak/pale oak→rowan, spruce→holly, birch→hawthorn, jungle→walnut, acacia→ash, dark oak→yew, mangrove→willow, cherry→blackthorn, crimson→elder, warped/bamboo→vine.
3. Bench (J:wand/gui/WandmakersBenchMenu.java:167-220): blank + core item (`WandCoreMaterialItem`) → `WandRulesService.recipeFor(wood, core)` → `WandRules.mayMake` (admin withdrawal) → bench tier ≥ `minimum_bench_tier` → `recipe.createWand(flex, rollLength)`. Flexibility chosen by player (UI). Taking output records `CareerWork.WANDCRAFT` (:346). No skill gate on bench crafting.
4. Wand chooses wizard: first right-click of an unbonded wand → `WandResonanceSystem.computeResonance/applyResonance` (J:item/wand/WandItem.java:111-115).
5. Other routes: Ollivander trial (J:wand/gui/OllivanderTrialMenu.java), commission (J:location/ollivanders/WandCommission.java:126-221, requires recipe + non-legendary rarity), admin/`/wandb` give.

#### A.2 Bench tiers / enhancers

| Enhancer (D:wizards_and_beasts/bench_enhancers/) | value |
|---|---|
| minecraft:bookshelf | 0.5 |
| minecraft:chiseled_bookshelf | 1.0 |
| wizards_and_beasts:floating_candle | 1.0 |
| wizards_and_beasts:pewter_cauldron | 1.5 |
| wizards_and_beasts:spell_teacher | 2.0 |

Scan radius 5 (D:wand_resonance_config.json), plain sum, no cap, no per-block limit (J:wand/bench/BenchMultiblockScanner.java). Recipe tiers 2.0–8.3. Rescan on bench place / neighbour change (J:wand/bench/WandmakersBenchBlock.java:56,92) and onLoad.

#### A.3 Data components on the wand stack (J:wand/WandComponents.java, legacy in J:registry/ModDataComponents.java:48-80)

| Component | Writers | Gameplay readers |
|---|---|---|
| wand_wood | WandBlankItem finishUsingItem; WandmakingRecipe:61; WandItem.createWand | WandStatsResolver.resolveWoodId (cast stats), resonance, allegiance temperament (WandAllegianceService:118), corruption healing check |
| wand_core | WandmakingRecipe:62; trial; createWand | WandStatsResolver.resolveCoreId, resonance, temperament (WandAllegianceService:120) |
| wand_flexibility | WandmakingRecipe:64; trial:117,171; WandItem:303 | WandStatsResolver:187-196 (fizzle/dmg/cooldown), resonance:67,228 |
| wand_length | WandmakingRecipe:66; trial:118,173; WandItem:322 | WandStatsResolver:173-183,236-243; resonance:71 |
| wand_integrity | recipe:67; WandAllegianceService:392 (explosions), 416/429 (mend); WandCare:144; trial | WandCorruptionSystem:60 (damage ×integrity), WandAllegianceRules:204 (backfire ≤ BROKEN_AT) |
| wand_corruption | WandCorruptionSystem:43 only (+0.02 per Dark cast) | WandCorruptionSystem:61-70 (Dark dmg bonus / healing-wood defence malus); tooltip |
| wand_master / wand_allegiance_score / wand_bond_history | WandAllegianceService (183-373), resonance:247-248 | cast bond state → power/cooldown (WandCastingAllegianceSystem:46-49), Expelliarmus (J:spell/expelliarmus/ExpelliarmusDisarmHandler.java:42) |
| wand_cast_count | **NONE** | display only (WandTab:239, PlayerFacets:131) → **decorative/dead** |
| wand_configuration | admin/commands (WandConfigCommands:118-235, WandAdminService:277) | visuals only (WandLookResolver:68, WandAppearance:106) |
| *_legacy (wood/core/length/flex/allegiance) | none in production (read-only fallbacks) | WandStatsResolver, Compatibility, WandCastingAllegianceSystem:30 |

#### A.4 Declared stats — reader table

Readers grepped separately (`.<accessor>()` / `::accessor`, excluding codec `forGetter`).

**Core definition (J:wand/registry/WandCoreDefinition.java)**

| Field | Gameplay reader | Verdict |
|---|---|---|
| raw_power | J:wand/resonance/WandResonanceSystem.java:173 (fit vs XP level for resonance) | live — but NOT cast power; higher = harder to bond at low XP |
| consistency | admin only (J:admin/wand/WandAdminService.java:151) | **NO READER — decorative** (javadoc :13-15 admits) |
| loyalty | admin only (:152) | **NO READER — decorative** |
| dark_affinity | admin only (:153) | **NO READER — decorative** |
| initiative | admin only (:154) | **NO READER — decorative** |
| allegiance_transfer_resistance | admin only (:155) | **NO READER — decorative** |
| source_key | admin only (:149) | **decorative** |
| cast_modifiers.damage/cooldown/fizzle/category_damage_bonus | J:wand/cast/WandStatsResolver.java:126,134-139 → SpellExecutor:67 → ModifierStack | live (fizzle only when net >0, see W7) |
| cast_modifiers.range | WandStats.rangeFor → J:spell/cast/SpellCastTargetedHandler.java:67,216,312; SpellCastConeHandler:38,150; SpellExecutor:152; WandBeamChannelLogic:132,364 | live |
| temperament.* | J:wand/allegiance/WandAllegianceRules.java:112-204 (each field read) | live |
| appearance | client visuals | cosmetic by design |

**Wood definition (J:wand/registry/WandWoodDefinition.java)**

| Field | Gameplay reader | Verdict |
|---|---|---|
| affinity_tags | only `"healing"` read, J:wand/corruption/WandCorruptionSystem.java:71 | mostly decorative (combat/protective/dark/rare/divination unread) |
| spell_modifiers | admin only (WandAdminService:126-128) | **NO READER — decorative** (docs WAND_REWIRE_AUDIT §A.6 "pending ruling") |
| personality_affinity | WandResonanceSystem.java:133 | live (resonance) |
| rarity | market pricing J:market/MarketGoods.java:113; WandCommission:126,139 (legendary → 0 → not commissionable) | live (economy only) |
| refuse_threshold | WandResonanceSystem.java:238 | live |
| cast_modifiers / temperament | as for cores | live |

**Stack / derived stats**

| Stat | Reader | Verdict |
|---|---|---|
| flexibility | WandStatsResolver:187-196; resonance; Compatibility (unread) | live |
| length | WandStatsResolver:173-183; resonance | live |
| integrity | WandCorruptionSystem:60; WandAllegianceRules:204 | live |
| bond / allegiance score | WandAllegianceService:102 → state → power/cooldown | live |
| mastery (MASTERED state) | WandAllegianceRules.powerMultiplier/cooldownMultiplier | live |
| power | no separate "power" field; `raw_power` = resonance only | — |
| precision | no field; comment label for flexibility (WandStatsResolver:185) | — |
| corruption | WandCorruptionSystem:61-70 | live |
| cast_count | none | **decorative/dead (no writer)** |
| Compatibility score (heritage × wood/core/flex/length) | none (CastContext field never read) | **NO READER — decorative** |
| WAND_AFFINITY attribute | none; not attached to players (J:registry/ModAttributes.java:36-39) | **decorative** (shown in WandTab:162-164 only if present) |
| wand_configuration modules | visuals | cosmetic |

#### A.5 Cores — values, acquisition, dominance

Cast modifiers from D:wizards_and_beasts/wand_cores/*.json.

| Core | dmg | range | fizzle | other | Bench item? | Recipes | Survival acquisition (proof) |
|---|---|---|---|---|---|---|---|
| dragon_heartstring | 1.10 | 1.0 | +0.02 | bond ×1.5, wins −1 | yes | 10 | Welsh Green spawns (D:neoforge/biome_modifier/spawn_common_welsh_green.json) + loot (D:loot_table/entities/common_welsh_green.json) + harvest D:bestiary/harvest/dragon_heartstring_prime.json; **sold** Ollivanders (ollivanders.json:179, trusted_supplier, 1/wk) + Knockturn (knockturn_dealer.json:55, 64). Other 9 breeds: loot exists but no spawn. |
| phoenix_feather | 1.0 | 1.05 | 0 | bond ×0.5, wins +1, loyal cd ×0.9 | yes | 10 | phoenix spawns (spawn_phoenix.json) + G:loot_table/entities/phoenix.json. Not sold. |
| unicorn_hair | 0.95 | 1.0 | −0.05 (wasted, W7) | transfer −0.1, dark cost | yes | 10 | unicorn spawns + G:loot_table/entities/unicorn.json; sold Ollivanders :178, Apothecary :216, Knockturn :52 |
| thestral_tail_hair | 1.05 | 1.0 | 0 | mastery needs death witness | yes | 10 | thestral spawns (spawn_thestral.json) + D:loot_table/entities/thestral.json. Not sold (Ollivanders only buys, :169) |
| veela_hair | 1.05 | 1.0 | +0.04 | — | yes | 10 | only D:loot_table/chests/hidden_wizarding_cache.json:128 (weight 2) via `hidden_cache_in_ruins` GLM |
| wampus_cat_hair | 1.04 | 1.0 | +0.01 | — | yes | 10 | **NONE in survival** — no spawn; harvest D:bestiary/harvest/wampus_cat_hair.json + G loot need entity; Ollivanders buys only (:170) |
| troll_whisker | 1.04 | 0.95 | +0.05 | bond ×0.8 | yes | 10 | **NONE in survival** — no spawn; Ollivanders/Apothecary buy only (:167 / apothecary :191) |
| thunderbird_tail_feather | 1.08 | 1.0 | 0 | bond ×0.6 | yes | 10 | **NONE in survival** — no spawn; harvest D:bestiary/harvest/thunderbird_tail_feather.json needs entity; Ollivanders buys only (:172) |
| rougarou_hair | 1.0 | 1.0 | +0.03 | dark_arts +0.1 | **no (plain Item)** | **0** | rougarou spawns (BeastSpawnHandler:48 + spawn_rougarou.json), D:loot_table/entities/rougarou.json (1-2). Obtainable but **unusable** |
| white_river_monster_spine | 1.0 | 1.08 | −0.02 | — | **no (plain Item)** | **0** | hidden cache only (:133). **Unusable** |

Dominance (cast stats only):
- Thestral (1.05 dmg, 0 fizzle) strictly dominates Veela (1.05, +0.04) except for thestral's mastery gate.
- Wampus (1.04, range 1.0, +0.01) strictly dominates Troll (1.04, range 0.95, +0.05, slower bond).
- Dragon heartstring — the shop-bought "common" core — has the highest damage (1.10); thunderbird (1.08, rare, unobtainable) is worse on damage.
- Rare cores (rougarou, white river) are neither better nor worse: they cannot be made.
- `raw_power` does not change casts; higher power only makes resonance fail for low-XP players (WandResonanceSystem:173-185), e.g. dragon at XP 0 ≈ 0.51 < 0.65 match.

#### A.6 Woods (D:wizards_and_beasts/wand_woods/)

| Wood | rarity | dmg | cd | range | fizzle | category bonus | temperament | Source |
|---|---|---|---|---|---|---|---|---|
| ash | uncommon | 1.0 | 0.97 | 1.0 | −0.01 | — | wins +1, passed-on ×0.8, foreign ×0.8 | ash_tree worldgen, acacia, Ollivanders sells? no (buys :161) |
| blackthorn | rare | 1.04 | 1.0 | 1.0 | 0 | — | bond needs danger, wins +1 | tree worldgen, cherry |
| elder | legendary | 1.03 | 1.0 | 1.05 | 0 | — | wins −1 | elder_tree worldgen, **crimson stem** |
| hawthorn | uncommon | 1.0 | 1.0 | 1.0 | 0 | dark +0.05 | backfires in foreign hands | tree, birch |
| holly | uncommon | 1.0 | 1.0 | 1.0 | 0 | defense +0.08 | — | tree, spruce; Ollivanders sells holly_log (:177) |
| rowan | common | 1.0 | 1.0 | 1.0 | 0 | defense +0.1, dark −0.1 | dark cost 0.02; refuse 0.65 (W11) | tree, oak/pale oak |
| vine | uncommon | 1.0 | 1.0 | 1.03 | 0 | — | bond ×1.25 | warped stem/bamboo (no tree worldgen) |
| walnut | rare | 1.0 | 0.97 | 1.0 | 0 | — | loyal cd ×0.95 | tree, jungle |
| willow | common | 1.0 | 0.97 | 1.0 | 0 | — | — | tree, mangrove |
| yew | rare | 1.05 | 1.0 | 1.0 | 0 | dark +0.05 | — | tree, dark oak |

All 10 woods × 8 craftable cores have recipes (80). Length ranges per recipe; elder × thestral = 14–15in (always LONG).

#### A.7 Customization

`WandConfiguration` / `WandModule` (handle/shaft/tip/core/ornament bone variants, texture/tint overrides) — visual only; set by admin panel or `/wandb wand config` (J:wand/command/WandConfigCommands.java). No survival customization path found beyond the bench's flexibility choice. Rare constructions (wood `appearance.rare`) are cosmetic rolls.

---

### B) Brewing

#### B.1 Pipeline

- Cauldron blocks: brass/copper/pewter (J:registry/WizardingWorldBlockRegistry.java:92-100). Needs heat source below (J:brew/CauldronHeat.java), water bucket fill, up to 6 ingredient slots, empty-hand click starts (`StartResult`, J:block/brew/CauldronBlockEntity.java:316-385).
- Match: `BrewingRecipes.findMatch(contents, tier)`; tier gate `isAtLeast`.
- Qualification gate (N.E.W.T./O.W.L.) checked before consumption (CauldronBlockEntity:316-325; D:qualification_gates/advanced_study.json).
- Off heat 200 ticks → spoil (CauldronBlockEntity:90, 474-476).
- Timed adds while brewing: catalyst in window or contamination +0.25 each (:601-626).
- Completion: one failure roll = base + penalties (+ missed catalyst), reduced 15%/level of `potion_potency` (max 3), floor 0.02 (J:brew/BrewFailure.java); failure → spoiled; bottle a spoiled pot → Ruined Potion (J:block/brew/WizardingCauldronBlock.java:427-446).
- Collect: brew points by tier (pewter 1 / brass 2 / copper 3) to the brewer + CareerWork (CauldronBlockEntity:440-455).
- Drink: `BrewItem.onConsumed` → components with potency = drinker's skill ×(1+0.1·lvl) (J:item/brew/BrewItem.java:104-140).
- Discovery: **none** (B1).

#### B.2 Cauldron tiers

| Tier (ordinal) | Block | Crafting | Notes |
|---|---|---|---|
| PEWTER (0, lowest) | pewter_cauldron | 4 iron blocks + **copper cauldron** (D:recipe/pewter_cauldron.json) | most expensive, weakest → B2 |
| BRASS (1) | brass_cauldron | 4 copper ingots + vanilla cauldron | entry pot |
| COPPER (2, top) | wizarding_copper_cauldron | 4 copper blocks + brass cauldron | |

#### B.3 Per-brew table

| Brew | Tier | Ingredients | Discoverable? | Ingredient acquisition | Effect implemented | Gate | Tests |
|---|---|---|---|---|---|---|---|
| pepperup_potion | pewter | blaze_powder, sugar | No (JEI only) | vanilla | heal_and_cure + fire res + speed (J:brew/effect/BrewEffect.java:180) | — | gametest cauldron_brews_and_pays_the_brewer |
| wiggenweld_potion | pewter | glistering_melon_slice, sweet_berries×2, dittany | No | dittany: holly/rowan leaves loot (G:loot_table/blocks/holly_leaves.json:175, rowan_leaves.json), trees via D:neoforge/biome_modifier/holly_tree.json, rowan_tree.json; Apothecary sells (apothecary.json:210) | vanilla regen + absorption (legacy list) | — | unit + 1 gametest ref |
| skele_gro | brass | dittany×2, bone_meal×3, ghast_tear | No | as above + vanilla | heal_and_cure + regen/nausea | — | unit |
| mandrake_restoration_draught | copper | mandrake×2, sugar | No | mandrake_crop age 7 (G:loot_table/blocks/mandrake_crop.json), mandrake_patch worldgen, Apothecary sells seeds (:211) | MandrakeRestorationEffect → PetrifyServerLogic.cure (J:effect/MandrakeRestorationEffect.java) | — | unit |
| silver_solution | brass | iron_ingot, amethyst_shard×2, glow_ink_sac | No | vanilla | drink: glowing only; real use = refine to pure silver (B3) | — | unit |
| pure_silver_solution | (refined) | silver_solution brewing + occamy_eggshell sneak-use | No | eggshell: occamy spawns (spawn_occamy.json) + loot 35% (G:loot_table/entities/occamy.json); Apothecary sells at trusted_supplier (:217); Knockturn sells (:53) | silvering weapons (J:brew/silver/SilveringHandler.java), J:brew/SilverRefining.java | — | SilverRefiningTest (unit only) |
| amortentia | copper | mallowsweet×2, rose_bush×2, nautilus_shell | No | mallowsweet patch worldgen; vanilla | INFATUATION (J:event/brew/SignatureBrewHandler.java, VeelaAllure) — self only (B6) | N.E.W.T. Potions A | unit |
| veritaserum | copper | mallowsweet×3, phantom_membrane | No | as above | truth_serum → VeritaserumService.dose — self only (B6) | N.E.W.T. E | unit |
| wolfsbane_potion | brass | devils_snare×2, nether_wart, spider_eye | No | devils_snare patch worldgen | WOLFSBANE → J:heritage/werewolf/WolfsbaneHandler.java | N.E.W.T. E | unit |
| draught_of_living_death | copper | devils_snare×2, mandrake, fermented_spider_eye; catalyst glistering_melon_slice @45–65% (miss 0.4); base failure 0.35 | No | as above | LIVING_DEATH (SignatureBrewHandler) | N.E.W.T. A | unit |
| felix_felicis | copper | occamy_eggshell, gold_ingot×2, glowstone_dust×2; base failure 0.4 | No | eggshell as above | felix_felicis component → FelixFortune | N.E.W.T. A | gametests (2), unit |
| polyjuice_potion | copper | gillyweed×2, fermented_spider_eye, rabbit_hide | No | gillyweed: kelpie/lobalug loot (G:loot_table/entities/kelpie.json, lobalug.json; both spawn); Apothecary only **buys** gillyweed (:187) | PolyjuiceDisguise → **needs admin-set sample** (B4) | O.W.L. E | DisguiseTests, unit |
| shadow_form | copper | hidebehind_shadow_essence×2, demiguise_hair | No | hidebehind spawns (night) + D:loot_table/entities/hidebehind.json; demiguise spawns + G:loot_table/entities/demiguise.json; Apothecary only buys demiguise_hair (:194) | SHADOW_FORM → J:shadow/ShadowFormHandler.java | — | unit |
| ruined_potion | (failure) | — | n/a | bottle a spoiled pot | nausea/hunger/weakness | — | — |

Discoverability for all: no in-world teaching item, book, NPC lesson output or unlock state was found (grep of handbook chapters, CanonItemRegistry potion books `Item::new`, lang). Potions lesson at Apothecary counts toward O.W.L. (per apothecary.json `_comment`) — content of the lesson not inspected (UNVERIFIED whether it names recipes).

#### B.4 Specific ingredients requested

| Item | Source(s) | Used in brewing? | Other use |
|---|---|---|---|
| dittany | holly/rowan leaves; Apothecary sells (:210) | wiggenweld, skele_gro | contract `dittany_stock` |
| bezoar | goat drop 25% (D:loot_modifiers/bezoar_from_goat.json); Apothecary sells (:214, 3/wk) | no | consumable: removes all effects + partial petrify cure (B9) |
| mooncalf_dung | mooncalf loot (G:loot_table/entities/mooncalf.json; mooncalf spawns) | no | Bertie Botts recipe (D:recipe/bertie_botts_every_flavour_beans.json); Apothecary buys |
| occamy_eggshell | occamy loot 35%; Apothecary sells (trusted_supplier); Knockturn sells | felix_felicis; silver refining (sneak only, B3) | placeable block, fragile on fall (J:event/item/OccamyEggshellFragility.java) |
| demiguise_hair | demiguise loot | shadow_form | demiguise_weave_trim, extendable_ears recipes |
| golden_snidget_feather | golden_snidget loot (snidget spawns) | no | Firebolt recipes; broom offhand perk (J:broom/SnidgetFeather.java) |
| mandrake | mandrake_crop age 7; worldgen patch; seeds sold; baby_mandrake 25% GLM | mandrake draught, living death | — |
| silver | no silver ingot/ore; "silver" = silver_solution brew (vanilla inputs) → pure_silver via eggshell | silver_solution | silvering weapons |

#### B.5 Unreachable / circular

- Unreachable brewing ingredients: **none** for shipped recipes (every modded ingredient has a spawning source or seller). Weak spots: veela_hair-style single-source items are wand-side, not brewing.
- Practically unreachable *mechanics*: pure silver by normal click (B3), Polyjuice disguise in survival (B4), recipe knowledge without JEI (B1).
- Circular dependencies: none found. Closest: pewter cauldron requires the copper cauldron it ranks below (B2) — inverted, not circular.

---

### Unreachable / dead-end item list

Wand side:
- thunderbird_tail_feather, troll_whisker, wampus_cat_hair — no survival source (W1).
- rougarou_hair, white_river_monster_spine — obtainable, no use (W2).
- Dragon heartstring from the 9 non-spawning breeds' loot tables — only Welsh Green drops in practice.
- Grindylow/kappa gillyweed loot — those creatures never spawn (gillyweed still reachable via kelpie/lobalug).

Brewing side: none unreachable as items; see B3/B4 for unreachable mechanics.

### Docs drift noted
- D:loot_table/chests/hidden_wizarding_cache.json:123 says veela_hair / white_river_monster_spine are "consumed by ten wand recipes each" — white river has 0 recipes.
- documentation/WANDMAKER_AUDIT.md §2.2 (five cores refused) is fixed for 5 cores (WandItemRegistry comment :44-47) but rougarou/white river were left as plain items.


---

## Audit: Creatures, Famous Wizard Cards, Books/Lore/World

Read-only audit of `C:/tmp/wb-tracing` (branch feat/spell-tracing worktree), 2026-10-21. No gradle run, nothing edited.
Paths below are relative to the repo root. `J` = `src/main/java/at/koopro/wizardsandbeasts`, `D` = `src/main/resources/data/wizards_and_beasts`, `G` = `src/generated/resources/data/wizards_and_beasts`.

**Loot-table precedence note.** 43 entity loot tables exist in both `D/loot_table/entities` and `G/loot_table/entities`, and 39 of them differ. `build.gradle:172-175` adds `src/generated/resources` as a second resource dir with `DuplicatesStrategy.EXCLUDE`. That keeps the first copy, which is `src/main/resources`, so **main wins**. This follows from Gradle's srcDir order; it was not checked at runtime. The "effective drops" below use main where it exists and generated otherwise.

---

### Summary of findings

#### P0
None in this scope. Nothing crashes, and no card reward breaks the cosmetic-only rule.

#### P1
1. **Three wand cores cannot be obtained in survival, which strands 30 wandmaking recipes.** These are `thunderbird_tail_feather`, `troll_whisker` and `wampus_cat_hair`.
   - Their only source is a bestiary harvest rule that fires on a kill (`D/bestiary/harvest/{thunderbird_tail_feather,troll_whisker,wampus_cat_hair}.json`). The effective main loot tables do not drop them (`D/loot_table/entities/{thunderbird,troll,wampus_cat}.json`).
   - None of the three creatures has a natural spawn: there is no `spawn_*.json` in `D/neoforge/biome_modifier/` and no entry in `J/entity/beast/BeastSpawnHandler.java:41-102`.
   - Ollivanders only *buys* them (`D/locations/ollivanders.json` `/market/buys`), and no chest table contains them.
   - Each core has 10 `D/recipe/wandmaking_*_<core>.json` recipes, so 30 recipes have no reachable input.
2. **Two wand cores can be found but cannot be used: `rougarou_hair` and `white_river_monster_spine`.**
   - Both have core definitions (`D/wizards_and_beasts/wand_cores/rougarou_hair.json`, `.../white_river_monster_spine.json`) and enum entries (`J/wand/stat/WandCore.java:21-22`).
   - Neither has a wandmaking recipe (0 matches in `D/recipe` or `G/recipe`). The bench (`J/wand/gui/WandmakersBenchMenu.java:197-203,239-243`) and Ollivander commissions (`J/location/ollivanders/WandCommission.java:129-130`) both require a recipe.
   - Rougarou hair drops from a creature that spawns (`spawn_rougarou.json`). The spine rolls in the hidden wizarding cache (`D/loot_table/chests/hidden_wizarding_cache.json:133`).
   - The cache's own `_comment` at line 123 claims the spine is "consumed by ten wand recipes"; that is false.
3. **Blank spell sources are nearly unobtainable, so scribing at the Spell Scriptorium is gated on luck.**
   - The Scriptorium needs a *blank* `SpellSourceItem` (`J/spell/study/SpellScriptoriumBlock.java:133-152`).
   - Neither `standard_book_of_spells` nor `torn_spell_page` has a recipe or a shop entry. Every loot modifier writes a spell onto the stack.
   - The only blanks are 1-3 books in the hidden-cache entry at `hidden_wizarding_cache.json:97` (3/10 weight). That table rolls on just 12% of mansion, jungle, desert, ancient-city and stronghold-library chests (`D/loot_modifiers/hidden_cache_in_ruins.json`).
   - `J/spell/learning/SpellSource.java:26` says blanks are "purchasable"; nothing sells them.
4. **About 77 of roughly 105 creatures never appear in survival.** Only 28 roster creatures have a biome modifier. The basilisk (Chamber), dementor (Azkaban), toad (basilisk ritual) and baby niffler (litter) have their own routes. Every other creature exists only as a spawn egg or admin spawn. Consequences:
   - The griffin bond and companion data are unreachable (`D/creature_bonds/griffin.json`, `D/companions/griffin.json`).
   - The Horntail heartstring harvest rule is dead (`D/bestiary/harvest/dragon_heartstring.json`).
   - The ecology data for the other 9 dragons, centaur, merperson, grindylow and zouwu never runs in survival.
   - `documentation/CREATURE_QUALITY_MATRIX.md:49,54` says the Hippogriff and Occamy are "never spawned". That is stale: `spawn_hippogriff.json` and `spawn_occamy.json` now exist.

#### P2
- **Dead-end products** (no recipe, brew, wand, Java behaviour or shop):
  - `granian_hair`, `hidebehind_claw`, `horned_serpent_gem`, `matagot_essence` and `pukwudgie_venom_sac` are each `registerSimpleItem` (`J/registry/ConsumableItemRegistry.java:152,164,166,168,172`) and sit only in the `module/magizoology` tag.
  - `rougarou_hair` (see P1.2).
  - Sell-only products: `yeti_fur`, `murtlap_essence` and `acromantula_venom` (apothecary/Knockturn buy them, contracts); `murtlap_essence` is a stub `Item::new` at `CanonItemRegistry.java:213`.
  - `leprechaun_gold` has a real item class, but the leprechaun does not spawn, so it is unreachable.
  - Blank famous wizard cards: the recipe at `J/datagen/ModRecipeProvider.java:198-205` makes 2 blanks, and nothing prints a wizard onto a blank (`FamousWizardCardItem.java:30`).
- **Kill-only drops where a nonlethal route would fit the canon:**
  - `golden_snidget_feather`: kill-only, and killing a protected Snidget carries no legal consequence (no Ministry or tag hook).
  - `occamy_eggshell`: kill-only (0-1 per kill, `D/loot_table/entities/occamy.json`); in canon the shells come from nests.
  - `granian_hair`.
  - `horned_serpent_gem`: in canon the horn is the Ilvermorny wand core, so a wand use would fit.
  - `thestral_tail_hair` drops on kill but also has a bond gift (`creature_bonds/thestral.json:42`); the bestiary lists only "remains".
- **Datagen and hand-authored loot tables disagree on 20 creatures.** The generated tables give kill drops for unicorn hair, phoenix feather, mooncalf dung and demiguise hair. These are shadowed by empty main tables, so the nonlethal design depends on the resource-merge order.
- **Structures are placeholders.**
  - The Chamber of Secrets NBT is only `stone_bricks` and `air`, with no chests or loot. It repeats every 40 chunks in any overworld biome (`D/worldgen/structure_set/chamber_of_secrets.json:11`), so the "one monster" premise becomes many basilisks and fangs.
  - Azkaban's NBT is 1 KB with no loot.
  - Both are confirmed as future work in `documentation/LOCATIONS.md:173-174`.
- **Eight canon textbooks are plain stub `Item`s with no acquisition and no use.** They are `advanced_potion_making`, `magical_draughts_and_potions`, `fantastic_beasts_and_where_to_find_them`, `quidditch_through_the_ages`, `one_thousand_magical_herbs_and_fungi`, `beginners_guide_to_transfiguration`, `unfogging_the_future` and `monster_book_of_monsters` (`J/registry/CanonItemRegistry.java:155-176`). They are natural candidates for LoreTome or spell sources.
- **The hidden wizarding cache always gives an Oakshaft 79 broom.** Pool 1 at `hidden_wizarding_cache.json:5-15` has rolls 1 and a single entry, so every cache yields a full broom.

---

### A) Creatures

Column key:
- **Spawn**: Y means there is a `D/neoforge/biome_modifier/spawn_<id>.json`. Other routes are noted.
- **Loot src**: which loot table is in effect (main or gen).
- **Nonlethal**: groom/shed come from the creature JSON `abilities` (`J/creature/ability/Groomable.java`, `Shed.java`). Gift and breed come from `D/creature_bonds/<id>.json`.
- **Harvest**: a kill-time extra drop gated by bestiary tier (`D/bestiary/harvest/*.json`, applied by `J/loot/BestiaryHarvestLootModifier.java`).
- **Taming**: means a bond file exists. The bond system is feed → follow → gift → mastery; there is no vanilla `TamableAnimal`.
- **Breeding**: only `creature_bonds/{mooncalf,bowtruckle}.json` have a `breeding` block. The niffler brings a litter on first spawn (`J/event/bestiary/niffler/NifflerEventHandler.java:35-58`). The basilisk has a ritual (`J/basilisk/ritual/BasiliskBreedingRitual.java`). No creature uses vanilla `BreedGoal` or `getBreedOffspring`.

#### A.1 Creatures with a real survival presence

| Creature | Spawn | Loot src → drops | Nonlethal | Bond / breed | Harvest | Class |
|---|---|---|---|---|---|---|
| unicorn | Y (birch/dark/flower forest, w1) | main → **none** (slayer curse) | groom + shed `unicorn_hair` | bond, no gift | – | **complete** |
| demiguise | Y (jungle/dark forest, w1) | main → none | groom + shed `demiguise_hair` | bond | – | **complete** |
| mooncalf | Y (meadow/plains) | main → none | dance dung (`MooncalfEntity.java:210-221`) | bond gift dung; **breed (wheat)** | – | **complete** |
| bowtruckle | Y (forest) | main → oak_sapling, stick | – | bond gift holly_sapling; **breed (mallowsweet)** | – | **complete** |
| phoenix | Y (peaks/savanna, w1) | main → none | shed `phoenix_feather` (`PhoenixEntity.java:581-588`), tears | bond gift feather | – | **complete** |
| thestral | Y (taiga) | main → bone, thestral_tail_hair | – | bond gift tail hair | – | **complete** (also lethal route) |
| hippogriff | Y (meadow/windswept) | main → feather, leather | bow etiquette, ride | bond gift vanilla feather | – | **complete** |
| niffler | Y (badlands) | main → gold_nugget, knut | treasure digging | bond (betrayal unwired, `creature_bonds/niffler.json`) | – | **complete** |
| kelpie | Y (rivers) | gen → gillyweed, kelp | saddle/ride (`KelpieEntity.java:276-300`) | – | – | complete |
| acromantula | Y (dark forest) | main → acromantula_venom, string | – | – | – | partial (venom sell/contract only) |
| common_welsh_green | Y (windswept/peaks, w1) | main → dragon_heartstring, leather | – | – | – | complete (heartstring = wand core; also sold at Ollivanders/Knockturn) |
| occamy | Y (jungle, w1) | main → feather, occamy_eggshell (0-1) | – | – | – | partial (eggshell kill-only) |
| basilisk | Chamber `spawn_overrides` + `ChamberBasilisk.mayWake`; ritual | main → basilisk_fang, leather | – | – | – | complete (fang destroys Horcrux, `J/item/darkartefact/BasiliskFangItem.java`, `HorcruxDestruction.java`) |
| dementor | Azkaban self-spawn (`J/entity/azkaban/DementorEntity.java:425-441`) | none | – | – | – | complete for its role |
| werewolf | Y (forest/taiga) | main → none | moon_bound | – | – | complete for its role |
| hidebehind | Y | main → hidebehind_claw, hidebehind_shadow_essence | – | – | – | partial (claw is a dead end; essence goes to brew and throwable) |
| ghoul | Y (forest) | main → ghoul_slime, rotten_flesh | – | – | – | complete (slime → decoy_detonator) |
| golden_snidget | Y (flower forest/meadow) | main → golden_snidget_feather | – | – | – | partial (feather used in broom recipes; kill-only, no law) |
| granian | Y (plains/meadow) | main → granian_hair | – | – | – | **partial, dead-end product** |
| horned_serpent | Y (river/swamp) | main → horned_serpent_gem | – | – | – | **partial, dead-end product** |
| matagot | Y (overworld, own placement) | main → matagot_essence | – | – | – | **partial, dead-end product** |
| pukwudgie | Y (swamp/dark forest) | main → pukwudgie_venom_sac | – | – | – | **partial, dead-end product** |
| rougarou | Y (swamp) | main → rougarou_hair | – | – | – | **partial, core without recipe** |
| yeti | Y (snow peaks) | main → yeti_fur | – | – | – | partial (sell-only) |
| augurey | Y (forest) | main → feather | UNVERIFIED (no mobInteract) | – | – | partial (vanilla drop only) |
| cornish_pixie | Y (forest) | main → feather | – | – | – | partial |
| bundimun | Y (forest) | main → slime_ball | – | – | – | partial |
| runespoor | Y (savanna) | main → spider_eye | – | – | – | partial (canon eggs are a brew ingredient; none here) |
| streeler | Y (swamp) | main → lime_dye | – | – | – | partial |
| lobalug | Y (ocean) | main → ink_sac | – | – | – | partial |
| goblin_teller | Y (dripstone/lush caves) | – | Gringotts host | – | – | n/a (NPC) |
| toad | basilisk ritual only | gen → none | – | – | – | placeholder |

#### A.2 Creatures with systems but no survival spawn (unreachable)

| Creature | Loot → drops | What exists | Class |
|---|---|---|---|
| hungarian_horntail | main → dragon_heartstring, leather | harvest rule heartstring (KNOWN, 0.35) – dead | partial/unreachable |
| other 8 dragons (opaleye, fireball, hebridean, ridgeback, vipertooth, longhorn, short_snout, ironbelly) | main → dragon_heartstring, leather | `DragonEntity` breath kit, ecology files | partial/unreachable |
| erumpent | main → leather | harvest erumpent_horn (horn is a throwable explosive, `J/entity/spell/WizardingThrownEntity.java:37,65`) | partial/unreachable |
| thunderbird | main → feather | harvest tail feather (wand core) | partial/unreachable (**P1**) |
| troll | main → bone, rotten_flesh | harvest troll_whisker (wand core) | partial/unreachable (**P1**) |
| wampus_cat | main → leather | harvest wampus_cat_hair (wand core) | partial/unreachable (**P1**) |
| griffin | gen → feather, leather | hoard_guard ability, bond + companion data | partial/unreachable |
| centaur | gen → none | star_reading, forest_keeper, ecology | partial/unreachable |
| sphinx | gen → leather | sphinx_riddle | partial/unreachable |
| merperson, grindylow, zouwu | gen → none / gillyweed+kelp / leather | ecology files | partial/unreachable |
| murtlap | main → murtlap_essence | apothecary contract/buy | partial/unreachable |
| leprechaun | gen → leprechaun_gold | real item class (`LeprechaunGoldItem`) | partial/unreachable |

#### A.3 Placeholders (no spawn, vanilla-only or no drops, no bespoke ability)

abraxan, aethonan (feather, leather); ashwinder, salamander, fire_crab (blaze_powder; fire_crab also emerald); billywig, chizpurfle, doxy, glumbumble, puffskein, pygmy_puff (string); blast_ended_skrewt, boggart, erkling, giant, gnome, imp, maledictus, obscurus, pogrebin, porlock, red_cap (no drops); chimaera, graphorn, hodag, manticore, nundu, quintaped, reem, tebo (leather); clabbert, crup, jarvey, knarl, kneazle, mackled_malaclaw, moke, nogtail (rabbit_hide); diricawl, fwooper, jobberknoll (feather); dugbog, flobberworm (slime_ball); fairy (glowstone_dust); hippocampus, sea_serpent, shrake (prismarine_shard); horklump (brown_mushroom); kappa (gillyweed, kelp); lethifold, swooping_evil (phantom_membrane); plimpy, ramora (cod); qilin (gold_nugget); snallygaster (feather, leather).

Several of these have combat abilities in `D/creatures/<id>.json` (e.g. boggart_dread, lethifold_smother, nundu_pestilence, fwooper_song). They work only when spawned by egg or admin.

#### A.4 Product → use map (creature materials)

The scan covered `D/`, `G/` and `J/` minus registry, lang, models and client code.

| Product | Sources | Uses | Verdict |
|---|---|---|---|
| unicorn_hair | groom, shed (nonlethal); Ollivanders/apothecary/Knockturn sell | 10 wandmaking recipes, ollivander pool, contracts, market | complete |
| demiguise_hair | groom, shed | brew (polyjuice), demiguise_weave_trim, trim material, contract | complete |
| phoenix_feather | shed, bond gift | 10 wand recipes, ollivander pool, contract | complete |
| thestral_tail_hair | kill + bond gift | 10 wand recipes, Ollivanders buys | complete |
| dragon_heartstring | kill (Welsh Green only in survival); Ollivanders/Knockturn sell | 10 wand recipes | complete |
| mooncalf_dung | dance, bond gift | `MooncalfDungItem` fertiliser, recipe, apothecary | complete |
| occamy_eggshell | kill only; apothecary sells | felix_felicis brew, fragility event | complete use, kill-only source |
| hidebehind_shadow_essence | kill | shadow_form brew, recipe, throwable | complete |
| golden_snidget_feather | kill | firebolt / firebolt_supreme recipes, `J/broom/SnidgetFeather.java` | complete use, kill-only, no law |
| ghoul_slime | kill | decoy_detonator recipe, apothecary | complete |
| gillyweed | kelpie/kappa/grindylow kill; also a plant | brew, `GillyweedHandler` | complete |
| basilisk_fang | kill | Horcrux destruction, contraband trade | complete |
| erumpent_horn | studied kill (creature unreachable) | throwable explosive, sell | unreachable |
| thunderbird_tail_feather / troll_whisker / wampus_cat_hair | studied kill (creature unreachable) | 10 wand recipes each | **unreachable (P1)** |
| veela_hair | hidden cache (2/16 of a 12% roll) | 10 wand recipes | rare but reachable |
| white_river_monster_spine | hidden cache | **none** (no recipe) | **dead end (P1)** |
| rougarou_hair | kill | **none** (core def but no recipe) | **dead end (P1)** |
| acromantula_venom | kill | sell, contracts | sell-only |
| yeti_fur | kill | apothecary buy | sell-only |
| murtlap_essence | kill (unreachable) | apothecary buy/contract; stub `Item::new` | sell-only, unreachable |
| granian_hair, hidebehind_claw, horned_serpent_gem, matagot_essence, pukwudgie_venom_sac | kill | **none** | **dead end** |
| leprechaun_gold | kill (unreachable) | niffler-shiny tags, Gringotts module | unreachable |

#### A.5 Notes

- Harvest rules (`J/bestiary/harvest/HarvestRule.java`) only ever *add* drops on kill. They are not a nonlethal mechanic.
- Nonlethal mechanics in code:
  - `Groomable` (empty hand, trust check, 24000-tick cooldown) and `Shed` (calm creature leaves a `CreatureSign` item on a timer). These are used only by unicorn and demiguise.
  - Phoenix shedding, mooncalf dance dung, and bond `gift` blocks (bowtruckle, hippogriff, mooncalf, phoenix, thestral).
- The bestiary `profile.materials` in `D/bestiary/entries/*.json` declares each source as `remains`, `studied_kill`, `shed`, `grooming`, `gift` or `husbandry`. The thestral lists only `remains` even though a bond gift exists.
- `CreatureStimuli.mobInteract` was not inspected (UNVERIFIED).

---

### B) Famous Wizard Cards and Chocolate Frogs

#### Roster and weights
- `J/card/WizardCards.java:30-84` defines 24 cards: 8 common, 8 uncommon, 6 rare and 2 legendary (Flamel, Harry Potter).
- Weights are at `J/card/CardRarity.java:20-23`: COMMON 12, UNCOMMON 7, RARE 3, LEGENDARY 1, drawn per card. The total is 172.
- Odds per card: common 6.98%, uncommon 4.07%, rare 1.74%, legendary 0.58%.
- Odds per tier: common 55.8%, uncommon 32.6%, rare 10.5%, legendary 1.16% (about 1 in 86; the javadoc says about 1 in 80).
- Monte-Carlo estimate for a full set: mean about 281 frogs eaten, median 231, 90th percentile about 513.

#### Acquisition
- **Eating a chocolate frog** gives one random card via `InventoryHandover.giveOrDrop` (`J/item/consumable/ChocolateFrogItem.java:81-82`).
- **Escapes:** 15% of frogs hop away as a catchable `ChocolateFrogEntity` that lives 5-8 s and still carries its card (`ChocolateFrog.java:44`, `ChocolateFrogItem.java:59-63,95-113`).
- **Getting frogs:** the only source is a shapeless recipe (2 cocoa beans + slime ball, `D/recipe/chocolate_frog.json`, module WIZARDING_FOOD). No shop, trader or loot table carries frogs.
- **Card recipe:** paper ×2 + ink bottle + Knut makes **2 blank cards** (`J/datagen/ModRecipeProvider.java:198-205`). Nothing prints a wizard onto a blank, so this is a dead end.

#### Collection tracking and duplicates
- **There is no collection tracking.** The card is an item with a `wizard_card_id` data component (`J/item/trinket/FamousWizardCardItem.java:51-60`). No attachment, stat, advancement or saved data records which cards a player has owned. Grepping `WizardCards|WIZARD_CARD_ID|card_collection|album` finds only the item, registry and datagen.
- **Duplicates** are handled only by stacking: stacks of 16 per wizard id (`J/registry/ConsumableItemRegistry.java:61-62`). There is no trade-in, dedupe or upgrade.
- **Collection UI:** none. The only display is the tooltip, which shows the deed line and "N of 24" with the tier name (`FamousWizardCardItem.java:70-90`).
- **Milestone rewards:** none.

#### Design rule (cards must be cosmetic + lore only)
- **No violation.** Cards grant nothing.
- The chocolate frog itself does have gameplay effects as a consumable: food 3/0.45, clears every effect in `#chocolate_frog_cures`, and a 30 s `CHOCOLATE_WARD` that denies despair (`J/chocolate/ChocolateFrog.java`, `ChocolateWardHandler.java`). These belong to the food, not the card. The Bertie Bott's chocolate bean reuses the same code (`J/bertiebotts/BeanFlavour.java:141-146`).
- If a collection or milestone system is added later, it must stay cosmetic.

#### Gaps (design, not bugs)
No album or collection record, no completion reward (a cosmetic one would fit, such as a title or frame), no frog vendor (Honeydukes exists only as a block palette, `J/block/location/HogsmeadeBlocks.java:37-44`), and a dead blank-card recipe.

---

### C) Books, lore and world

#### Spell sources
- **Items:**
  - `standard_book_of_spells` is `SpellSourceItem.Animated`, bound (not used up on read) (`J/registry/CanonItemRegistry.java:151-152`).
  - `torn_spell_page` is loose, used up on a successful read (`J/registry/MiscItemRegistry.java:49-51`).
  - Reading is a 60-tick channel; eligibility is checked twice (`J/item/spell/SpellSourceItem.java`).
- **Scribing:** `spell_teacher` block = `SpellScriptoriumBlock`. It writes the player's *active* spell onto a held blank source and costs 1 ink bottle (`J/spell/study/SpellScriptoriumBlock.java:133-160,185`). Blank sources are scarce (see P1.3).
- **Writers of `spell_source`:** only `J/loot/SpellbookLootModifier.java:92` and `SpellScriptoriumBlock.java:152`. Spells are also granted by skill nodes (`J/skill/SkillSystemAPI.java:437`).
- The modifier skips spells that are unimplemented or Obscurial at roll time. All 27 pool ids resolve to `implemented` JSON spells. The other 124 spell JSONs are `coming_soon`.

| Loot modifier / table | Rolls on | Item | Chance | Spell pool |
|---|---|---|---|---|
| `spellbook_in_village_libraries` | village cartographer, temple, plains/savanna/snowy/taiga/desert houses | book | 0.35 | lumos, nox, stupefy, alohomora, wingardium_leviosa, reparo, colloportus, accio, depulso |
| `spellbook_in_stronghold_libraries` | stronghold library, crossing, corridor; bastion treasure | book | 0.7 | stupefy, expelliarmus, incendio, glacius, arresto_momentum, episkey, finite_incantatem, flipendo, diffindo, aguamenti, confringo |
| `spellbook_in_woodland_mansions` | woodland mansion, pillager outpost | book | 0.6 | riddikulus, levicorpus, liberacorpus, bombarda, frigora, claustra_reverto, capacious_extremis, revelio |
| `spell_pages_in_ruins` | mineshaft, dungeon, desert pyramid, jungle temple, underwater ruin big, shipwreck supply | torn_spell_page | 0.4 | episkey, arresto_momentum, finite_incantatem, colloportus, alohomora, aguamenti, glacius, diffindo, levicorpus, liberacorpus, riddikulus |
| `chests/hidden_wizarding_cache` | via `hidden_cache_in_ruins` (12% of mansion, jungle, desert, ancient city, stronghold library) | book | pool 4 always rolls | fixed lumos (w4), fixed stupefy (w3), **blank ×1-3 (w3)** |

Every implemented JSON spell has either a loot source or a skill grant. Crucio is skill-only.

#### Lore and research items
- **LoreTomes** grant one-time KNOWLEDGE and History of Magic OWL credit after a 60-tick read (`J/item/lore/LoreTomeItem.java`). The three tomes are `a_history_of_magic` (w3), `hogwarts_a_history` (w2) and `rise_and_fall_of_the_dark_arts` (w1). Their only source is `chests/scholars_shelf`, rolled 30% of the time on stronghold library, mansion, village cartographer and village temple chests (`D/loot_modifiers/lore_tomes_in_libraries.json`).
- **Stub textbooks (P2):** eight canon books have no use and no source, listed in P2 above (`CanonItemRegistry.java:155-176`).
- **Craftable research items:** `bestiary`, `ministry_handbook`, `marauders_map` and `ministry_license_scroll` each have a recipe. `riddles_diary` is a dark artefact with no acquisition; it is used only in a gametest (UNVERIFIED beyond grep).

#### Structures and their loot

| Structure | Placement | Contents / loot | Purpose |
|---|---|---|---|
| `azkaban_fortress` | one per world, deep ocean, 64-160 chunks (`D/worldgen/structure_set/azkaban_fortress.json`) | code-built crag plus a 1 KB template; no chests or loot tables in NBT; dementors self-spawn | distinct (prison / dementor zone), **no yield**; LOCATIONS.md:173 lists the real fortress as future work |
| `chamber_of_secrets` | random_spread spacing 40 / separation 18, any overworld biome, y=-40 (`structure_set/chamber_of_secrets.json:11-12`) | NBT is only stone_bricks + air; basilisk via `spawn_overrides`, one per chamber until slain (`J/chamber/ChamberBasilisk.java`) | **placeholder box**; yields only a basilisk fang; repeats across the world; LOCATIONS.md:174 lists the puzzle and real NBT as future work |
| Treasure sites (`D/treasure_sites/*.json`): ancient_city, desert_tomb, jungle_temple, sunken_ruins, woodland_mansion | vanilla structures | cursed artefacts (tomb_medallion, warded_amulet, sealed_casket, ancient_goblet) with curse layers and value ranges; expedition charts | **distinct purpose** (Gringotts curse-breaking loop) |
| Vanilla chests via GLMs | see `neoforge/loot_modifiers/global_loot_modifiers.json` | Knuts (village, mineshaft), Sickles (dungeon, stronghold, temples, mansion), Galleons (mansion, end city, buried treasure, bastion), Dragots (shipwreck, ruins, outpost), spell sources and lore tomes as above, hidden cache, bezoar from goats, baby mandrake | coin seeding is generic; spell, lore and treasure layers are purpose-built |
| `hidden_wizarding_cache` | 12% of 5 rare tables | **guaranteed Oakshaft 79 broom** (pool 1), 1-3 coin rolls, 3/8 wand_blank, spell book (fixed or blank), ink 6/10, rare core 4/16 (veela_hair or white_river_monster_spine) | "wandmaker's cache"; generous (a guaranteed broom) |
| `scholars_shelf` | 30% of 4 library-type tables | one lore tome | lore |

There are no other worldgen structures. Diagon Alley, Hogsmeade, the Ministry and Gringotts are block palettes plus NPC-hosted locations, not generated buildings (`documentation/LOCATIONS.md:3,18-20`).

---

### UNVERIFIED
- Main-over-generated loot precedence was checked from `build.gradle` only, not in a built jar.
- `CreatureStimuli.mobInteract`, and augurey, runespoor, streeler and hidebehind interactions beyond the grep.
- Whether any admin, Ministry or event flow spawns no-spawn creatures (only `CreatureCommands`, `CreatureTestSpawns` and the basilisk ritual were found).
- How Azkaban is built in code (`AzkabanCragPiece`) was not read; the "no loot" call comes from the NBT and a grep of `J/azkaban`.

