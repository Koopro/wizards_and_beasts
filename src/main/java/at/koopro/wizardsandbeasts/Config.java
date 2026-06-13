package at.koopro.wizardsandbeasts;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

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
    }
}
