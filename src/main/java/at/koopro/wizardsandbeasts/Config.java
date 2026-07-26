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
    private static final ModConfigSpec.BooleanValue SPELL_TEACHER_REQUIRE_PAYMENT = BUILDER
            .comment("If true, learning from the spell teacher consumes vault funds.")
            .define("spellTeacherRequirePayment", false);
    private static final ModConfigSpec.IntValue SPELL_TEACHER_LEARN_COST_KNUTS = BUILDER
            .comment("Cost in knuts for learning one spell from the spell teacher.")
            .defineInRange("spellTeacherLearnCostKnuts", 0, 0, Integer.MAX_VALUE);
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
    private static final ModConfigSpec.BooleanValue ENABLE_WAND_ALLEGIANCE = BUILDER
            .comment("If true, wand allegiance compatibility, binding, and transfer mechanics are active.")
            .define("enableWandAllegiance", true);
    private static final ModConfigSpec.BooleanValue SHOW_SPELL_HUD_OVERLAY = BUILDER
            .comment("If true, the spell diamond HUD overlay is drawn.")
            .define("showSpellHudOverlay", true);
    private static final ModConfigSpec.BooleanValue REDUCE_SCREEN_EFFECTS = BUILDER
            .comment("If true, fullscreen effect overlays (mob-effect vignettes, Crucio, form transition,",
                    "Floo transit spin) are suppressed. Accessibility option; gameplay is unaffected.")
            .define("reduceScreenEffects", false);
    private static final ModConfigSpec.IntValue APPARITION_RANGE_BLOCKS = BUILDER
            .comment("Maximum Apparition distance in blocks; longer targets are clamped to this radius.")
            .defineInRange("apparitionRangeBlocks", 32, 4, 256);
    private static final ModConfigSpec.IntValue APPARITION_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown in ticks after a successful Apparition.")
            .defineInRange("apparitionCooldownTicks", 60, 0, 20 * 300);
    private static final ModConfigSpec.DoubleValue APPARITION_SPLINCH_BASE_CHANCE = BUILDER
            .comment("Base splinch chance at zero focus; a fully focused caster reduces it to zero.")
            .defineInRange("apparitionSplinchBaseChance", 0.35, 0.0, 1.0);

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
            .defineList("adminUuids", DEFAULT_ADMIN_UUIDS, () -> "", entry -> entry instanceof String);

    /** Configured admin UUIDs; empty before the config has loaded, which reads as "unconfigured". */
    public static List<? extends String> adminUuids() {
        try {
            return ADMIN_UUIDS.get();
        } catch (IllegalStateException ex) {
            return List.of();
        }
    }

    static {
        // Seed state per module, consulted only when a world is first created.
        at.koopro.wizardsandbeasts.module.ModuleConfig.define(BUILDER);
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enforceSpellRequirements;
    public static boolean debugLogSpellGateReasons;
    public static boolean enableDebugTools;
    public static boolean spellTeacherRequirePayment;
    public static int spellTeacherLearnCostKnuts;
    public static boolean debugLogCloakVisibility;
    public static boolean enableCloakSelfViewRestrictions;
    public static boolean cloakSelfViewRestrictionsDeathlyOnly;
    public static PerfProfile perfProfile;
    public static int beamTargetScanIntervalTicks;
    public static int beamChannelEffectIntervalTicks;
    public static boolean enableWandAllegiance;
    public static boolean showSpellHudOverlay;
    public static boolean reduceScreenEffects;
    public static int apparitionRangeBlocks;
    public static int apparitionCooldownTicks;
    public static float apparitionSplinchBaseChance;

    public enum PerfProfile {
        LOW,
        MEDIUM,
        HIGH
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enforceSpellRequirements = ENFORCE_SPELL_REQUIREMENTS.get();
        debugLogSpellGateReasons = DEBUG_LOG_SPELL_GATE_REASONS.get();
        enableDebugTools = ENABLE_DEBUG_TOOLS.get();
        spellTeacherRequirePayment = SPELL_TEACHER_REQUIRE_PAYMENT.get();
        spellTeacherLearnCostKnuts = SPELL_TEACHER_LEARN_COST_KNUTS.get();
        debugLogCloakVisibility = DEBUG_LOG_CLOAK_VISIBILITY.get();
        enableCloakSelfViewRestrictions = ENABLE_CLOAK_SELF_VIEW_RESTRICTIONS.get();
        cloakSelfViewRestrictionsDeathlyOnly = CLOAK_SELF_VIEW_RESTRICTIONS_DEATHLY_ONLY.get();
        perfProfile = PERF_PROFILE.get();
        beamTargetScanIntervalTicks = BEAM_TARGET_SCAN_INTERVAL_TICKS.get();
        beamChannelEffectIntervalTicks = BEAM_CHANNEL_EFFECT_INTERVAL_TICKS.get();
        enableWandAllegiance = ENABLE_WAND_ALLEGIANCE.get();
        showSpellHudOverlay = SHOW_SPELL_HUD_OVERLAY.get();
        reduceScreenEffects = REDUCE_SCREEN_EFFECTS.get();
        apparitionRangeBlocks = APPARITION_RANGE_BLOCKS.get();
        apparitionCooldownTicks = APPARITION_COOLDOWN_TICKS.get();
        apparitionSplinchBaseChance = APPARITION_SPLINCH_BASE_CHANCE.get().floatValue();
    }
}
