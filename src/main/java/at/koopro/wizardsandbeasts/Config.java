package at.koopro.wizardsandbeasts;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENFORCE_SPELL_REQUIREMENTS = BUILDER
            .comment(
                    "If true, the server rejects spell casts whose SpellRequirement (prerequisite spell + minimum",
                    "proficiency) is not met by the caster. Sandbox-friendly default is false; enable for a",
                    "school-sim style progression where prerequisites are mandatory.")
            .define("enforceSpellRequirements", false);
    private static final ModConfigSpec.BooleanValue DEBUG_LOG_SPELL_GATE_REASONS = BUILDER
            .comment("If true, logs server-side reasons when spell casts are rejected.")
            .define("debugLogSpellGateReasons", false);
    private static final ModConfigSpec.BooleanValue ENABLE_DEBUG_TOOLS = BUILDER
            .comment("If true, enables client debug overlays and debug keybindings.")
            .define("enableDebugTools", false);
    private static final ModConfigSpec.IntValue SKILL_RESPEC_COST_KNUTS = BUILDER
            .comment("Cost in knuts to respec the skill web. Default 493 = one Galleon.",
                    "A fee rather than a point penalty on purpose: a percentage loss compounds across",
                    "respecs and can strand a player permanently under the 60-point cap, which is the one",
                    "failure this web must not have. Coins can be earned back; earned points cannot.",
                    "Charged only while the Gringotts module is on; set 0 to make respec free.")
            .defineInRange("skillRespecCostKnuts", 493, 0, Integer.MAX_VALUE);
    private static final ModConfigSpec.IntValue OLLIVANDER_WAND_PRICE_KNUTS = BUILDER
            .comment("Price in knuts of every wand after a player's first from Ollivander's trial. Default 3451 =",
                    "seven Galleons (Philosopher's Stone ch. 5). The first wand is always free: a new character has",
                    "no money and cannot cast without one. Charged only while the Gringotts module is on; 0 = free.")
            .defineInRange("ollivanderWandPriceKnuts", 3451, 0, Integer.MAX_VALUE);
    private static final ModConfigSpec.IntValue MINISTRY_FINE_SCALE_PERCENT = BUILDER
            .comment("Percentage applied to every Ministry fine. 100 ships the tariff as designed",
                    "(2 Galleons for unlicensed Apparition, 10 for an unregistered Animagus), 200 doubles it,",
                    "0 turns fines off entirely while leaving the criminal record intact.",
                    "Only paperwork offences carry a fine at all - the Unforgivables are answered with a",
                    "sentence, so scaling this never touches them. Fines are charged only while the",
                    "Gringotts module is on; with no vault to bill there is nothing to collect.")
            .defineInRange("ministryFineScalePercent", 100, 0, 10000);
    private static final ModConfigSpec.IntValue MINISTRY_DAYS_PER_YEAR = BUILDER
            .comment("In-game days one year of a character's life takes. An underage wizard comes of age - and",
                    "the Trace lifts - after (17 - age) of these years. World time, not play time: it passes for",
                    "everyone at once, in every dimension.")
            .defineInRange("ministryDaysPerYear", 8, 1, 3650);
    private static final ModConfigSpec.IntValue MINISTRY_NEW_CHARACTER_AGE = BUILDER
            .comment("The age a brand-new character starts at. 0 keeps every character of age (the default), so",
                    "no one is placed under the Trace unless an admin sets an age. 11 to 16 starts new players",
                    "underage. Existing characters are never changed by this setting.")
            .defineInRange("ministryNewCharacterAge", 0, 0, 16);
    private static final ModConfigSpec.DoubleValue MINISTRY_NOTORIETY_DECAY_PER_SECOND = BUILDER
            .comment("Notoriety a wizard sheds per second while lying low (no unpaid fine, not a fugitive).",
                    "0.05 ships the designed pace: a spree takes real time to cool. 0 keeps notoriety forever.")
            .defineInRange("ministryNotorietyDecayPerSecond", 0.05, 0.0, 1.0);
    private static final ModConfigSpec.IntValue STANDING_AXIS_BOUND = BUILDER
            .comment("Magnitude of every magical-standing axis. Each axis runs -bound to +bound with 0",
                    "as true neutrality. Raising it makes standing slower to move, not deeper: the deed",
                    "values in data/<ns>/magical_deeds/ are absolute, so a 200 bound halves their effect.")
            .defineInRange("standingAxisBound", 100, 1, 10000);
    private static final ModConfigSpec.IntValue STANDING_LEAN_THRESHOLD_PERCENT = BUILDER
            .comment("Percent of the axis bound at which a wizard is described as leaning one way.",
                    "Below this the axis reads as neutral and gates keyed to a lean do not open.")
            .defineInRange("standingLeanThresholdPercent", 25, 1, 99);
    private static final ModConfigSpec.IntValue STANDING_STRONG_THRESHOLD_PERCENT = BUILDER
            .comment("Percent of the axis bound at which the axis becomes a fact about the character.",
                    "Must exceed standingLeanThresholdPercent; if it does not, the two are ordered at",
                    "read time rather than leaving the leaning band unreachable.")
            .defineInRange("standingStrongThresholdPercent", 60, 1, 100);
    private static final ModConfigSpec.IntValue STANDING_MINISTRY_RANK_CREDIT = BUILDER
            .comment("Ministry standing conferred per rank step: Obliviator 1x, Auror 2x, Magical Law",
                    "Enforcement 3x, Minister 4x. Notoriety is subtracted from it, so at the default a",
                    "clean Auror sits well into the trusted band and a wanted one does not.",
                    "Set 0 to make Ministry standing purely a measure of how wanted you are.")
            .defineInRange("standingMinistryRankCredit", 20, 0, 10000);
    private static final ModConfigSpec.BooleanValue DEBUG_LOG_CLOAK_VISIBILITY = BUILDER
            .comment("If true, logs cloak visibility/equipment masking behavior on the server.")
            .define("debugLogCloakVisibility", false);
    private static final ModConfigSpec.BooleanValue ENABLE_CLOAK_SELF_VIEW_RESTRICTIONS = BUILDER
            .comment("If true, cloak wearer is forced to first-person and container screens are closed.")
            .define("enableCloakSelfViewRestrictions", false);
    private static final ModConfigSpec.BooleanValue CLOAK_SELF_VIEW_RESTRICTIONS_DEATHLY_ONLY = BUILDER
            .comment("If true, self-view restrictions apply only to Deathly Hallow cloak.")
            .define("cloakSelfViewRestrictionsDeathlyOnly", true);
    private static final ModConfigSpec.EnumValue<PerfProfile> PERF_PROFILE = BUILDER
            .comment("Server safety preset for high-frequency systems: beam scans/effects and sync cadence.")
            .defineEnum("perfProfile", PerfProfile.MEDIUM);
    private static final ModConfigSpec.IntValue BEAM_TARGET_SCAN_INTERVAL_TICKS = BUILDER
            .comment("Ticks between held-beam target scans. Higher = less CPU, slower retargeting.")
            .defineInRange("beamTargetScanIntervalTicks", 2, 1, 20);
    private static final ModConfigSpec.IntValue BEAM_CHANNEL_EFFECT_INTERVAL_TICKS = BUILDER
            .comment("Ticks between held-beam effect applications. Higher = less CPU, lower DPS cadence.")
            .defineInRange("beamChannelEffectIntervalTicks", 5, 1, 20);
    private static final ModConfigSpec.DoubleValue DRAGOT_GALLEON_RATE = BUILDER
            .comment("Galleons a single Dragot is worth at Gringotts, before the 5% fee and the +/-3%",
                    "spread. The rate a teller actually quotes moves within that spread and is pinned",
                    "per player for 20 seconds; see DragotRates. Server-side: the rate belongs to the",
                    "world's economy, not to any one coin.")
            .defineInRange("dragotGalleonRate", 0.8, 0.01, 100.0);
    private static final ModConfigSpec.BooleanValue BUTTERBEER_GULP_NAUSEA = BUILDER
            .comment("If true, downing a second Butterbeer within 30 seconds gives brief Nausea.",
                    "Off by default: Butterbeer barely affects humans in the books, and a drink that",
                    "debuffs a child's character is not something a family server should get by",
                    "accident. The anti-spam rule works without this either way.")
            .define("butterbeerGulpNausea", false);
    private static final ModConfigSpec.BooleanValue ENABLE_WAND_ALLEGIANCE = BUILDER
            .comment("If true, wand allegiance compatibility, binding, and transfer mechanics are active.")
            .define("enableWandAllegiance", true);
    // ── Wands: numbers the stat resolver and the allegiance rules already use in one place each ──
    private static final ModConfigSpec.DoubleValue WAND_AFFINITY_STRENGTH = BUILDER
            .comment("How much a wand's wood and core cast modifiers count. 1 as authored; 0 makes every wood and",
                    "core cast alike; 2 doubles each one's difference from neutral.")
            .defineInRange("wandAffinityStrength", 1.0, 0.0, 2.0);
    private static final ModConfigSpec.DoubleValue WAND_BOND_GROWTH = BUILDER
            .comment("Multiplies how fast a wand's bond with its wizard deepens with use.")
            .defineInRange("wandBondGrowthMultiplier", 1.0, 0.0, 5.0);
    private static final ModConfigSpec.IntValue WAND_DEFEATS_TO_WIN = BUILDER
            .comment("Defeats of its master that win an ordinary wand, before its wood's temperament adjusts it.",
                    "The Elder Wand is always won with one.")
            .defineInRange("wandDefeatsToWin", 1, 1, 5);
    private static final ModConfigSpec.DoubleValue WAND_NEGLECT_LOSS = BUILDER
            .comment("Multiplies how fast an unused wand's bond cools once its grace period is over. 0 turns",
                    "neglect off. Neglect alone never takes a bond below reluctance.")
            .defineInRange("wandNeglectLossMultiplier", 1.0, 0.0, 5.0);
    private static final ModConfigSpec.BooleanValue WAND_FOREIGN_BACKFIRE = BUILDER
            .comment("If true (as shipped), a wand whose wood turns on a stranger (hawthorn) backfires in a hand",
                    "it does not know. A broken wand always backfires.")
            .define("wandForeignBackfire", true);
    private static final ModConfigSpec.BooleanValue SHOW_SPELL_HUD_OVERLAY = BUILDER
            .comment("If true, the spell diamond HUD overlay is drawn.")
            .define("showSpellHudOverlay", true);
    private static final ModConfigSpec.BooleanValue REDUCE_SCREEN_EFFECTS = BUILDER
            .comment("If true, fullscreen effect overlays (mob-effect vignettes, Crucio, form transition,",
                    "Floo transit spin) are suppressed. Accessibility option; gameplay is unaffected.")
            .define("reduceScreenEffects", false);
    private static final ModConfigSpec.DoubleValue SPELL_POWER_SOFT_CAP_KNEE = BUILDER
            .comment("Composed spell damage multiplier below which nothing is compressed. Above it, each",
                    "further point of multiplier buys less, approaching spellPowerMaxMultiplier without",
                    "reaching it. See SpellPower.java and DEVELOPER_REFERENCE.md for the formula.")
            .defineInRange("spellPowerSoftCapKnee", 1.5, 0.1, 10.0);
    private static final ModConfigSpec.DoubleValue SPELL_POWER_MAX_MULTIPLIER = BUILDER
            .comment("Asymptotic ceiling for the composed spell damage multiplier. Must exceed",
                    "spellPowerSoftCapKnee.")
            .defineInRange("spellPowerMaxMultiplier", 3.0, 0.2, 20.0);
    private static final ModConfigSpec.DoubleValue SPELL_POWER_MIN_MULTIPLIER = BUILDER
            .comment("Hard floor for the composed spell damage multiplier, so a stack of penalties cannot",
                    "reduce a cast to nothing. Must not exceed spellPowerSoftCapKnee.")
            .defineInRange("spellPowerMinMultiplier", 0.25, 0.01, 1.0);
    private static final ModConfigSpec.DoubleValue SPELL_COOLDOWN_MIN_MULTIPLIER = BUILDER
            .comment("Hard floor for the composed cooldown multiplier (lower = faster). The anti-spam guard:",
                    "0.25 means a fully-invested caster still waits a quarter of the base cooldown.")
            .defineInRange("spellCooldownMinMultiplier", 0.25, 0.01, 1.0);
    private static final ModConfigSpec.DoubleValue SPELL_COOLDOWN_MAX_MULTIPLIER = BUILDER
            .comment("Hard ceiling for the composed cooldown multiplier, so a stack of penalties cannot",
                    "lock a spell away for minutes.")
            .defineInRange("spellCooldownMaxMultiplier", 2.0, 1.0, 10.0);
    // ── Server-wide spell rules (administered from the Control Center's Magic and Dark Arts sections) ──
    private static final ModConfigSpec.DoubleValue SPELL_DAMAGE_MULTIPLIER = BUILDER
            .comment("Scales every spell's base damage before any caster bonus. 1.0 plays each spell as",
                    "authored; 0 makes spells harmless. Applied to the base, so the spell power soft cap",
                    "and ceiling still bound what bonuses add on top.")
            .defineInRange("spellDamageMultiplier", 1.0, 0.0, 10.0);
    private static final ModConfigSpec.DoubleValue SPELL_COOLDOWN_MULTIPLIER = BUILDER
            .comment("Scales every spell's base cooldown. 1.0 is as authored, 0.5 halves every cooldown.",
                    "The cooldown floor (spellCooldownMinMultiplier) still applies to caster bonuses.")
            .defineInRange("spellCooldownMultiplier", 1.0, 0.1, 10.0);
    private static final ModConfigSpec.DoubleValue SPELL_RANGE_MULTIPLIER = BUILDER
            .comment("Scales the range of every targeted, cone and beam spell. Projectiles are unaffected:",
                    "they fly until they hit something.")
            .defineInRange("spellRangeMultiplier", 1.0, 0.25, 4.0);
    private static final ModConfigSpec.BooleanValue ALLOW_UNFORGIVABLE_CURSES = BUILDER
            .comment("If false, every spell the spell law classes as Unforgivable (Avada Kedavra, Crucio,",
                    "Imperio as shipped) is refused at the cast, for everyone. They can still be learned.")
            .define("allowUnforgivableCurses", true);
    private static final ModConfigSpec.BooleanValue SPELL_BLOCK_DAMAGE = BUILDER
            .comment("If false, spell explosions do not break blocks and spells do not set blocks alight.",
                    "Entities are still hurt and ignited. Use on servers where spells must not grief builds.")
            .define("spellBlockDamage", true);
    // ── Brewing: server-wide rules the cauldron already has a single decision point for ──
    private static final ModConfigSpec.BooleanValue BREWING_ENABLED = BUILDER
            .comment("If false, no cauldron starts a new brew. Brews already on the heat finish, and bottles",
                    "already made can still be drunk.")
            .define("brewingEnabled", true);
    private static final ModConfigSpec.DoubleValue BREW_SPEED_MULTIPLIER = BUILDER
            .comment("Divides every recipe's heat time when a brew starts: 2.0 brews twice as fast. Brews",
                    "already on the heat keep the time they started with.")
            .defineInRange("brewSpeedMultiplier", 1.0, 0.1, 10.0);
    private static final ModConfigSpec.DoubleValue BREW_FAILURE_MULTIPLIER = BUILDER
            .comment("Scales every recipe's authored failure chance at the completion roll. 0 removes the",
                    "recipes' own risk (mistakes in the pot still count); brewing skill still reduces it.")
            .defineInRange("brewFailureMultiplier", 1.0, 0.0, 3.0);
    private static final ModConfigSpec.BooleanValue BREW_REQUIRE_HEAT_SOURCE = BUILDER
            .comment("If true (as shipped), a cauldron needs a heat source under it to start and keep brewing,",
                    "and spoils when left cold. False lets any cauldron brew without one.")
            .define("brewRequireHeatSource", true);
    private static final ModConfigSpec.DoubleValue BREW_CONTAMINATION_PENALTY = BUILDER
            .comment("Failure chance added for each item dropped into a working pot that does not belong in",
                    "the brew (as shipped 0.25: two mistakes are recoverable, four are not).")
            .defineInRange("brewContaminationPenalty", 0.25, 0.0, 1.0);
    private static final ModConfigSpec.BooleanValue SHOW_SPELL_POWER_IN_TOOLTIP = BUILDER
            .comment("If true, spell tooltips show the caster's effective damage multiplier and where it",
                    "comes from. The number is computed by the same class the server casts with, so it",
                    "cannot drift from what a cast actually does.")
            .define("showSpellPowerInTooltip", true);
    private static final ModConfigSpec.EnumValue<CreatureSpawns> CREATURE_NATURAL_SPAWNS = BUILDER
            .comment("Which wizarding creatures may spawn naturally.",
                    "  ALPHA_ONLY - only the creatures on the finished alpha roster, plus the ones the mod's",
                    "               own progression needs (the Unicorn, sole wild source of unicorn hair).",
                    "               This is the default: the rest of the roster still wears placeholder rigs.",
                    "  ALL        - every creature with a spawn placement, placeholder rigs included.",
                    "  NONE       - no wizarding creature spawns; summon them with a spawn egg or",
                    "               /wandb beast creature summon.",
                    "Registration and the Bestiary are unaffected either way.")
            .defineEnum("creatureNaturalSpawns", CreatureSpawns.ALPHA_ONLY);
    // ── Broom flight ────────────────────────────────────────────────────────────────────────
    // Speed is the one broom value a server may genuinely want to retune without editing eight
    // datapack files, so it gets a multiplier rather than an override: it scales whatever each
    // definition authored, and the relative ranking of the eight brooms survives the change.
    private static final ModConfigSpec.DoubleValue BROOM_SPEED_MULTIPLIER = BUILDER
            .comment("Scales every broom's authored maxSpeed. 1.0 flies them exactly as their",
                    "definition specifies. Applied on top of the datapack value, so the ranking",
                    "between brooms is preserved at any setting.")
            .defineInRange("broomSpeedMultiplier", 1.0D, 0.25D, 2.0D);
    private static final ModConfigSpec.DoubleValue BROOM_SERVER_SPEED_SCALE = BUILDER
            .comment("Server rule: scales every broom's top speed for everyone, in the definitions the server",
                    "sends. Unlike broomSpeedMultiplier (each player's own game), this one a client cannot change.")
            .defineInRange("broomServerSpeedScale", 1.0D, 0.25D, 2.0D);
    private static final ModConfigSpec.BooleanValue BROOM_SPEED_GUARD = BUILDER
            .comment("Server rule: set down a rider whose broom keeps travelling faster than the server's",
                    "definition allows (flight is simulated on the rider's client; this is the server's check).")
            .define("broomSpeedGuard", true);
    private static final ModConfigSpec.BooleanValue BROOM_GENTLE_LANDING = BUILDER
            .comment("If true, setting a broom down at a controlled descent costs no durability and",
                    "deals no damage. Turn off for the older behaviour, where every touchdown was",
                    "scored as an impact.")
            .define("broomGentleLanding", true);
    private static final ModConfigSpec.DoubleValue BROOM_WIND_VOLUME = BUILDER
            .comment("Volume of the speed-scaled flight wind, 0.0 to silence it. Client-side.")
            .defineInRange("broomWindVolume", 0.6D, 0.0D, 1.0D);
    private static final ModConfigSpec.DoubleValue BROOM_FOV_EFFECT = BUILDER
            .comment("How much the field of view widens at full broom speed, as a fraction.",
                    "0.0 disables it entirely — motion-sickness sufferers should set this to 0.",
                    "Client-side and purely cosmetic; it changes nothing the server sees.")
            .defineInRange("broomFovEffect", 0.12D, 0.0D, 0.5D);
    private static final ModConfigSpec.BooleanValue BROOM_SPEED_PARTICLES = BUILDER
            .comment("If true, faint slipstream particles trail a broom at speed. Client-side.")
            .define("broomSpeedParticles", true);

    private static final ModConfigSpec.IntValue FLOO_LIT_TIMEOUT_TICKS = BUILDER
            .comment("How long a Floo hearth stays green after a pinch of powder, in ticks.",
                    "Default 1800 = 90 seconds: long enough to open the destination list, read it, type an",
                    "address and commit, without a hearth lit and walked away from lighting a room all night.",
                    "Floo Flames in front of the hearth hold their own charge clock on top of this; whichever",
                    "runs out first takes the fire.")
            .defineInRange("flooLitTimeoutTicks", 1800, 20, 72000);
    private static final ModConfigSpec.IntValue FLOO_DEPARTURE_WINDUP_TICKS = BUILDER
            .comment("Ticks between naming a destination and actually leaving the grate.",
                    "This is the ritual: the fire roars, the traveller is held, and the hop only commits at",
                    "the end of it. Set 0 to teleport the instant the destination is confirmed, which is the",
                    "old behaviour and reads as a warp menu.")
            .defineInRange("flooDepartureWindupTicks", 40, 0, 200);
    private static final ModConfigSpec.IntValue FLOO_MISFIRE_CHANCE_PERCENT = BUILDER
            .comment("Chance in percent that a hop from the destination LIST comes out at the wrong grate.",
                    "Typing an address in SPEAK mode ignores this: a spoken address misfires when it is",
                    "mumbled - see flooSpeakTypoTolerance - so the risk is the player's own aim, not a die.",
                    "Set 0 to make list travel perfectly reliable.")
            .defineInRange("flooMisfireChancePercent", 10, 0, 100);
    private static final ModConfigSpec.IntValue FLOO_SPEAK_TYPO_TOLERANCE = BUILDER
            .comment("How many characters a spoken address may be wrong by and still connect somewhere.",
                    "0 refuses anything but an exact address. At the default of 2, \"Diagon Alle\" reaches",
                    "Diagon Alley - and \"Diagonally\" is close enough to be understood as SOMETHING, which is",
                    "what lands you in Knockturn Alley instead. Beyond this distance nothing answers at all.")
            .defineInRange("flooSpeakTypoTolerance", 2, 0, 10);
    private static final ModConfigSpec.IntValue FLOO_TRAVEL_COOLDOWN_TICKS = BUILDER
            .comment("Ticks a wizard must wait after arriving before the Network will take them again.",
                    "Default 60 = three seconds, which is roughly how long the arrival stagger lasts:",
                    "the cooldown is over at about the moment a player has their feet back under them,",
                    "so it is felt as the stagger rather than as a separate restriction.",
                    "This is what stops a hearth being a place you chain hops from as fast as you can",
                    "click. Set 0 to allow back-to-back travel.")
            .defineInRange("flooTravelCooldownTicks", 60, 0, 12000);
    private static final ModConfigSpec.BooleanValue FLOO_FUZZY_MATCH = BUILDER
            .comment("If true, a spoken address that is nearly right still connects - to somewhere.",
                    "This is what makes mumbling a destination interesting rather than merely wrong:",
                    "within flooSpeakTypoTolerance the Network understands you as meaning SOMETHING, and",
                    "sends you to the misfire table for it. Set false and only an exact address is heard;",
                    "anything else is refused outright with no journey and no soot.")
            .define("flooFuzzyMatch", true);
    private static final ModConfigSpec.IntValue FLOO_REGISTRATION_FEE_KNUTS = BUILDER
            .comment("Cost in knuts to register a hearth on the Floo Network with the Ministry Handbook.",
                    "Default 493 = one Galleon: a real expense early on, trivial later, which is the right",
                    "shape for a one-off piece of paperwork. Free in creative and for this mod's admins.",
                    "Charged only while the Gringotts module is on; with no vault to bill there is nothing",
                    "to collect, and a fee nobody could pay would make registration unreachable instead of",
                    "costly. The name-tag path is unaffected - it costs a name tag, and always did.")
            .defineInRange("flooRegistrationFeeKnuts", 493, 0, Integer.MAX_VALUE);
    private static final ModConfigSpec.IntValue FLOO_TICKS_PER_CHARGE = BUILDER
            .comment("How long one unused Floo charge lasts before it burns down on its own, in ticks.",
                    "Default 1200 = one minute per hop.")
            .defineInRange("flooTicksPerCharge", 1200, 20, 24000);

    private static final ModConfigSpec.BooleanValue SHOW_PLACEHOLDER_SPAWN_EGGS = BUILDER
            .comment("If true, the creative tab lists spawn eggs for creatures still on a placeholder rig.",
                    "Off by default so the tab shows the alpha roster; /wandb beast creature summon reaches",
                    "every creature regardless of this setting.")
            .define("showPlaceholderSpawnEggs", false);

    // --- Duelling dummy ------------------------------------------------------------
    //
    // A training target is only worth having if the numbers it reports are the numbers a
    // real fight would produce, so these keys change what the dummy *reports*, never what
    // a spell does. The one exception is dummyProtectEquipment, which is about the cost of
    // practising rather than about the reading.

    private static final ModConfigSpec.EnumValue<DummyNumberMode> DUMMY_DAMAGE_NUMBERS = BUILDER
            .comment("Who sees the floating damage numbers a duelling dummy throws off.",
                    "SELF shows them only to the wizard who landed the hit; ALL shows every player's",
                    "hits to everyone in range, which is what a practice hall wants. NONE leaves the",
                    "dummy working and silent.")
            .defineEnum("dummyDamageNumbers", DummyNumberMode.SELF);
    private static final ModConfigSpec.BooleanValue DUMMY_SHOW_HEARTS = BUILDER
            .comment("If true, damage numbers are shown in hearts rather than half-heart points.",
                    "Off by default because every other number the mod prints - spell power, brew",
                    "potency, armour - is in points, and one display in a different unit is worse",
                    "than a slightly less friendly one.")
            .define("dummyShowHearts", false);
    private static final ModConfigSpec.EnumValue<DummyDpsMode> DUMMY_DPS_MODE = BUILDER
            .comment("How the dummy reports damage per second on the action bar.",
                    "DYNAMIC updates the running average on every hit; ON_RESET prints one summary",
                    "when the bout ends; OFF never reports.")
            .defineEnum("dummyDpsMode", DummyDpsMode.DYNAMIC);
    private static final ModConfigSpec.IntValue DUMMY_BOUT_TIMEOUT_TICKS = BUILDER
            .comment("Ticks without a hit before a bout is considered over: the dummy heals back to",
                    "full and the next hit starts a fresh DPS window. Too short and a spell with a",
                    "long cast time reads as two bouts; too long and an abandoned dummy never resets.")
            .defineInRange("dummyBoutTimeoutTicks", 60, 20, 6000);
    private static final ModConfigSpec.IntValue DUMMY_HEALTH = BUILDER
            .comment("Maximum health of a duelling dummy in training mode. It cannot be killed at any",
                    "value - reaching zero resets it - so this only decides how many hits fit in a",
                    "bout before the reset interrupts the reading.")
            .defineInRange("dummyHealth", 200, 1, 1024);
    private static final ModConfigSpec.IntValue DUMMY_BOSS_HEALTH = BUILDER
            .comment("Maximum health while in boss mode, which a dummy enters when given a banner.",
                    "Boss mode also raises a boss bar, so this is the number that bar measures.")
            .defineInRange("dummyBossHealth", 1000, 1, 100000);
    private static final ModConfigSpec.IntValue DUMMY_ARMOR = BUILDER
            .comment("Armour points a bare dummy has before anything is equipped on it. 0 makes the",
                    "dummy a plain damage readout; raise it to practise against a target that soaks.",
                    "Armour from robes actually hung on the dummy is added on top of this.")
            .defineInRange("dummyArmor", 0, 0, 30);
    private static final ModConfigSpec.BooleanValue DUMMY_PROTECT_EQUIPMENT = BUILDER
            .comment("If true, wands and weapons take no durability damage from hitting a dummy.",
                    "On by default: an hour of practice should not cost a wand.")
            .define("dummyProtectEquipment", true);
    private static final ModConfigSpec.DoubleValue DUMMY_XP_PER_DAMAGE = BUILDER
            .comment("Experience orbs dropped per point of damage dealt to a dummy. 0 disables it.",
                    "Deliberately off by default - a dummy that pays out turns practice into a grind.")
            .defineInRange("dummyXpPerDamage", 0.0, 0.0, 10.0);
    private static final ModConfigSpec.BooleanValue DUMMY_SCARECROW = BUILDER
            .comment("If true, a dummy wearing a carved pumpkin keeps animals away and suppresses",
                    "hostile spawns around itself.")
            .define("dummyScarecrow", true);
    private static final ModConfigSpec.IntValue DUMMY_SCARE_RADIUS = BUILDER
            .comment("Radius in blocks of the scarecrow effect. Ignored when dummyScarecrow is false.")
            .defineInRange("dummyScareRadius", 12, 1, 64);
    private static final ModConfigSpec.BooleanValue DUMMY_DECOY = BUILDER
            .comment("If true, a dummy wearing a player head draws hostile mobs onto itself.",
                    "Off by default: a decoy that works is a wall against every raid the server has,",
                    "and that is a decision an operator should make on purpose.")
            .define("dummyDecoy", false);

    /**
     * Accounts permitted to use the mod's administrative commands and the module admin surface.
     *
     * <p>Ships with the authors' personal and dev accounts. Because a populated list is a closed
     * allow-list, on any other server these are the only accounts that may administer the mod until an
     * operator edits this from the console. Replace them with your own, or clear the list to fall back to
     * ordinary operator permission.
     *
     * <p>The third entry is the offline-mode UUID of the {@code Dev} player the Gradle {@code runClient}
     * task logs in as, so the mod's own admin commands work in the development workspace without editing
     * config after every wipe of {@code run/}. It is derived, not secret — {@code UUID.nameUUIDFromBytes(
     * "OfflinePlayer:Dev")} — and can never match an authenticated account, since Mojang only issues
     * version-4 UUIDs and this is version 3.
     */
    private static final List<String> DEFAULT_ADMIN_UUIDS = List.of(
            "d3c9ca09-8f15-4e59-96dd-1c0fab339fcd",
            "c9e6dcee-95f6-4d14-a980-5008ff72676e",
            "380df991-f603-344c-a090-369bad2a924a");

    private static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_UUIDS = BUILDER
            .comment("Player UUIDs permitted to use this mod's admin commands and change module state.",
                    "Ships with the mod authors' UUIDs plus the offline 'Dev' account used by the",
                    "development workspace. Replace them with your own, or clear the list to",
                    "fall back to ordinary operator permission.",
                    "When ANY valid UUID is listed this is a closed allow-list: operators not on it are",
                    "refused. The server console and command blocks always qualify, so a bad entry here",
                    "can always be corrected from the console.")
            // defineListAllowEmpty because the comment above tells the operator to clear this list, and
            // plain defineList pins the spec to ListValueSpec.NON_EMPTY — an operator who followed that
            // instruction would have hit a config that rewrote itself once a second forever rather than
            // one that fell back to operator permission. Same defect as werewolfEquipmentWhitelist.
            .defineListAllowEmpty("adminUuids", DEFAULT_ADMIN_UUIDS, () -> "",
                    entry -> entry instanceof String);

    /** Configured admin UUIDs; empty before the config has loaded, which reads as "unconfigured". */
    public static List<? extends String> adminUuids() {
        try {
            return ADMIN_UUIDS.get();
        } catch (IllegalStateException ex) {
            return List.of();
        }
    }

    static {
        // Forced lycanthropy: the moon, the loss of control, Wolfsbane and the wolf's body.
        // Declared flat rather than in a section so the keys reach the in-game config screen; see
        // WerewolfConfig's class javadoc.
        at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfConfig.define(BUILDER);
        // The blood economy: the pool, the drain, the thirst bands and what a bite is worth. Flat for
        // the same reason as the werewolf block above.
        at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodConfig.define(BUILDER);
        // Seed state per module, consulted only when a world is first created.
        at.koopro.wizardsandbeasts.module.ModuleConfig.define(BUILDER);
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enforceSpellRequirements;
    public static boolean debugLogSpellGateReasons;
    public static boolean enableDebugTools;
    public static int skillRespecCostKnuts;
    public static int ollivanderWandPriceKnuts = 3451;
    /**
     * Seeded with the shipped default rather than left at 0: an uninitialised scale reads as "fines are
     * switched off", so a fine assessed before the config load event would silently cost nothing.
     */
    public static int ministryFineScalePercent = 100;
    public static int ministryDaysPerYear = 8;
    public static int ministryNewCharacterAge = 0;
    public static double ministryNotorietyDecayPerSecond = 0.05;
    /**
     * Seeded with the shipped defaults for the same reason the fine scale is: standing is read from the
     * character sheet and from the skill-gate path, and a bound of 0 before the config load event would
     * band every wizard as neutral and silently open every gate keyed to a strong band.
     */
    public static int standingAxisBound = 100;
    public static int standingLeanThresholdPercent = 25;
    public static int standingStrongThresholdPercent = 60;
    public static int standingMinistryRankCredit = 20;
    public static boolean debugLogCloakVisibility;
    public static boolean enableCloakSelfViewRestrictions;
    public static boolean cloakSelfViewRestrictionsDeathlyOnly;
    public static PerfProfile perfProfile;
    public static int beamTargetScanIntervalTicks;
    public static int beamChannelEffectIntervalTicks;
    public static boolean enableWandAllegiance;
    public static float wandAffinityStrength = 1.0f;
    public static float wandBondGrowthMultiplier = 1.0f;
    public static int wandDefeatsToWin = 1;
    public static float wandNeglectLossMultiplier = 1.0f;
    public static boolean wandForeignBackfire = true;
    public static double dragotGalleonRate = 0.8;
    public static boolean butterbeerGulpNausea = false;
    public static boolean showSpellHudOverlay;
    public static boolean reduceScreenEffects;
    /** Seeded with the shipped default so a spawn check before config load still answers sanely. */
    public static CreatureSpawns creatureNaturalSpawns = CreatureSpawns.ALPHA_ONLY;
    public static boolean showPlaceholderSpawnEggs;
    public static DummyNumberMode dummyDamageNumbers = DummyNumberMode.SELF;
    public static boolean dummyShowHearts = false;
    public static DummyDpsMode dummyDpsMode = DummyDpsMode.DYNAMIC;
    public static int dummyBoutTimeoutTicks = 60;
    public static int dummyHealth = 200;
    public static int dummyBossHealth = 1000;
    public static int dummyArmor = 0;
    public static boolean dummyProtectEquipment = true;
    public static double dummyXpPerDamage = 0.0;
    public static boolean dummyScarecrow = true;
    public static int dummyScareRadius = 12;
    public static boolean dummyDecoy = false;
    public static float broomSpeedMultiplier = 1.0f;
    public static float broomServerSpeedScale = 1.0f;
    public static boolean broomSpeedGuard = true;
    public static boolean broomGentleLanding = true;
    public static float broomWindVolume = 0.6f;
    public static float broomFovEffect = 0.12f;
    public static boolean broomSpeedParticles = true;
    public static float spellPowerSoftCapKnee = 1.5f;
    public static float spellPowerMaxMultiplier = 3.0f;
    public static float spellPowerMinMultiplier = 0.25f;
    public static float spellCooldownMinMultiplier = 0.25f;
    public static float spellCooldownMaxMultiplier = 2.0f;
    public static boolean showSpellPowerInTooltip = true;
    public static float spellDamageMultiplier = 1.0f;
    public static float spellCooldownMultiplier = 1.0f;
    public static float spellRangeMultiplier = 1.0f;
    public static boolean allowUnforgivableCurses = true;
    public static boolean spellBlockDamage = true;
    public static boolean brewingEnabled = true;
    public static float brewSpeedMultiplier = 1.0f;
    public static float brewFailureMultiplier = 1.0f;
    public static boolean brewRequireHeatSource = true;
    public static float brewContaminationPenalty = 0.25f;
    /**
     * Floo timings and risk, all seeded with their shipped defaults.
     *
     * <p>Seeded rather than left at zero for the reason the fine scale and the standing bounds are: a
     * hearth can be lit, and a hop can be asked for, before {@link #onLoad} has ever run on a client
     * joining a world. A zero timeout would put every hearth out on the tick it was lit, and a zero
     * charge count would make a pinch of powder buy nothing.
     */
    public static int flooLitTimeoutTicks = 1800;
    public static int flooDepartureWindupTicks = 40;
    public static int flooMisfireChancePercent = 10;
    public static int flooSpeakTypoTolerance = 2;
    public static int flooTicksPerCharge = 1200;
    public static int flooTravelCooldownTicks = 60;
    public static boolean flooFuzzyMatch = true;
    public static int flooRegistrationFeeKnuts = 493;

    /** Who a dummy's floating damage numbers are drawn for. */
    public enum DummyNumberMode {
        NONE,
        SELF,
        ALL
    }

    /** How a dummy reports damage per second. */
    public enum DummyDpsMode {
        DYNAMIC,
        ON_RESET,
        OFF
    }

    public enum PerfProfile {
        LOW,
        MEDIUM,
        HIGH
    }

    /** How much of the creature roster is allowed to spawn on its own. See the config comment. */
    public enum CreatureSpawns {
        NONE,
        ALPHA_ONLY,
        ALL
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enforceSpellRequirements = ENFORCE_SPELL_REQUIREMENTS.get();
        debugLogSpellGateReasons = DEBUG_LOG_SPELL_GATE_REASONS.get();
        enableDebugTools = ENABLE_DEBUG_TOOLS.get();
        skillRespecCostKnuts = SKILL_RESPEC_COST_KNUTS.get();
        ollivanderWandPriceKnuts = OLLIVANDER_WAND_PRICE_KNUTS.get();
        ministryFineScalePercent = MINISTRY_FINE_SCALE_PERCENT.get();
        ministryDaysPerYear = MINISTRY_DAYS_PER_YEAR.get();
        ministryNewCharacterAge = MINISTRY_NEW_CHARACTER_AGE.get();
        ministryNotorietyDecayPerSecond = MINISTRY_NOTORIETY_DECAY_PER_SECOND.get();
        standingAxisBound = STANDING_AXIS_BOUND.get();
        standingLeanThresholdPercent = STANDING_LEAN_THRESHOLD_PERCENT.get();
        standingStrongThresholdPercent = STANDING_STRONG_THRESHOLD_PERCENT.get();
        standingMinistryRankCredit = STANDING_MINISTRY_RANK_CREDIT.get();
        debugLogCloakVisibility = DEBUG_LOG_CLOAK_VISIBILITY.get();
        enableCloakSelfViewRestrictions = ENABLE_CLOAK_SELF_VIEW_RESTRICTIONS.get();
        cloakSelfViewRestrictionsDeathlyOnly = CLOAK_SELF_VIEW_RESTRICTIONS_DEATHLY_ONLY.get();
        perfProfile = PERF_PROFILE.get();
        beamTargetScanIntervalTicks = BEAM_TARGET_SCAN_INTERVAL_TICKS.get();
        beamChannelEffectIntervalTicks = BEAM_CHANNEL_EFFECT_INTERVAL_TICKS.get();
        enableWandAllegiance = ENABLE_WAND_ALLEGIANCE.get();
        wandAffinityStrength = WAND_AFFINITY_STRENGTH.get().floatValue();
        wandBondGrowthMultiplier = WAND_BOND_GROWTH.get().floatValue();
        wandDefeatsToWin = WAND_DEFEATS_TO_WIN.get();
        wandNeglectLossMultiplier = WAND_NEGLECT_LOSS.get().floatValue();
        wandForeignBackfire = WAND_FOREIGN_BACKFIRE.get();
        // The stat resolver and allegiance rules read WandGlobals, never Config: that is also where a remote
        // server's values arrive, so tooltips on a client show the server's numbers.
        at.koopro.wizardsandbeasts.wand.rules.WandRulesService.onGlobalsChanged(
                new at.koopro.wizardsandbeasts.wand.rules.WandGlobals.Values(wandAffinityStrength,
                        wandBondGrowthMultiplier, wandDefeatsToWin, wandNeglectLossMultiplier, wandForeignBackfire));
        dragotGalleonRate = DRAGOT_GALLEON_RATE.get();
        butterbeerGulpNausea = BUTTERBEER_GULP_NAUSEA.get();
        showSpellHudOverlay = SHOW_SPELL_HUD_OVERLAY.get();
        reduceScreenEffects = REDUCE_SCREEN_EFFECTS.get();
        creatureNaturalSpawns = CREATURE_NATURAL_SPAWNS.get();
        showPlaceholderSpawnEggs = SHOW_PLACEHOLDER_SPAWN_EGGS.get();
        dummyDamageNumbers = DUMMY_DAMAGE_NUMBERS.get();
        dummyShowHearts = DUMMY_SHOW_HEARTS.get();
        dummyDpsMode = DUMMY_DPS_MODE.get();
        dummyBoutTimeoutTicks = DUMMY_BOUT_TIMEOUT_TICKS.get();
        dummyHealth = DUMMY_HEALTH.get();
        dummyBossHealth = DUMMY_BOSS_HEALTH.get();
        dummyArmor = DUMMY_ARMOR.get();
        dummyProtectEquipment = DUMMY_PROTECT_EQUIPMENT.get();
        dummyXpPerDamage = DUMMY_XP_PER_DAMAGE.get();
        dummyScarecrow = DUMMY_SCARECROW.get();
        dummyScareRadius = DUMMY_SCARE_RADIUS.get();
        dummyDecoy = DUMMY_DECOY.get();
        broomSpeedMultiplier = BROOM_SPEED_MULTIPLIER.get().floatValue();
        float previousScale = broomServerSpeedScale;
        broomServerSpeedScale = BROOM_SERVER_SPEED_SCALE.get().floatValue();
        broomSpeedGuard = BROOM_SPEED_GUARD.get();
        if (previousScale != broomServerSpeedScale) {
            at.koopro.wizardsandbeasts.broom.rules.BroomRulesService.onSpeedScaleChanged();
        }
        broomGentleLanding = BROOM_GENTLE_LANDING.get();
        broomWindVolume = BROOM_WIND_VOLUME.get().floatValue();
        broomFovEffect = BROOM_FOV_EFFECT.get().floatValue();
        broomSpeedParticles = BROOM_SPEED_PARTICLES.get();
        spellPowerSoftCapKnee = SPELL_POWER_SOFT_CAP_KNEE.get().floatValue();
        spellPowerMaxMultiplier = SPELL_POWER_MAX_MULTIPLIER.get().floatValue();
        spellPowerMinMultiplier = SPELL_POWER_MIN_MULTIPLIER.get().floatValue();
        spellCooldownMinMultiplier = SPELL_COOLDOWN_MIN_MULTIPLIER.get().floatValue();
        spellCooldownMaxMultiplier = SPELL_COOLDOWN_MAX_MULTIPLIER.get().floatValue();
        showSpellPowerInTooltip = SHOW_SPELL_POWER_IN_TOOLTIP.get();
        flooLitTimeoutTicks = FLOO_LIT_TIMEOUT_TICKS.get();
        flooDepartureWindupTicks = FLOO_DEPARTURE_WINDUP_TICKS.get();
        flooMisfireChancePercent = FLOO_MISFIRE_CHANCE_PERCENT.get();
        flooSpeakTypoTolerance = FLOO_SPEAK_TYPO_TOLERANCE.get();
        flooTicksPerCharge = FLOO_TICKS_PER_CHARGE.get();
        flooTravelCooldownTicks = FLOO_TRAVEL_COOLDOWN_TICKS.get();
        flooFuzzyMatch = FLOO_FUZZY_MATCH.get();
        flooRegistrationFeeKnuts = FLOO_REGISTRATION_FEE_KNUTS.get();
        at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfConfig.refresh();
        at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodConfig.refresh();
        // Pushed rather than pulled: SpellPower must not depend on this class, so that the cast
        // formula stays unit-testable without a mod environment.
        at.koopro.wizardsandbeasts.spell.cast.SpellPower.applyBounds(
                spellPowerSoftCapKnee, spellPowerMaxMultiplier, spellPowerMinMultiplier,
                spellCooldownMinMultiplier, spellCooldownMaxMultiplier);
        spellDamageMultiplier = SPELL_DAMAGE_MULTIPLIER.get().floatValue();
        spellCooldownMultiplier = SPELL_COOLDOWN_MULTIPLIER.get().floatValue();
        spellRangeMultiplier = SPELL_RANGE_MULTIPLIER.get().floatValue();
        allowUnforgivableCurses = ALLOW_UNFORGIVABLE_CURSES.get();
        spellBlockDamage = SPELL_BLOCK_DAMAGE.get();
        brewingEnabled = BREWING_ENABLED.get();
        brewSpeedMultiplier = BREW_SPEED_MULTIPLIER.get().floatValue();
        brewFailureMultiplier = BREW_FAILURE_MULTIPLIER.get().floatValue();
        brewRequireHeatSource = BREW_REQUIRE_HEAT_SOURCE.get();
        brewContaminationPenalty = BREW_CONTAMINATION_PENALTY.get().floatValue();
        // Pushed for the same reason: Spell reads SpellTuning, never Config, and SpellTuning is also what a
        // remote server's values arrive in. This publishes the local layer and tells connected clients.
        at.koopro.wizardsandbeasts.spell.tuning.SpellTuningService.onGlobalsChanged(
                new at.koopro.wizardsandbeasts.spell.tuning.SpellTuningSnapshot.Globals(
                        spellDamageMultiplier, spellCooldownMultiplier, spellRangeMultiplier,
                        allowUnforgivableCurses, spellBlockDamage));
    }
}
